"""A production dedicated server in its own work dir: console input, log waits, a clean stop."""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import threading
import time
from pathlib import Path

from . import runtime
from .common import kill_tree, rmtree
from .project import Target

# spawn-animals / spawn-npcs stay true: when false, a server discards every animal / villager on its next tick, even
# one a scene summons (ServerLevel.shouldDiscardEntity). A flat world spawns no animals by itself.
PROPS = {
    "online-mode": "false", "server-ip": "127.0.0.1", "level-type": "minecraft\\:flat", "generate-structures": "false",
    "view-distance": "4", "simulation-distance": "4", "spawn-protection": "0", "difficulty": "easy",
    "sync-chunk-writes": "false", "enable-query": "false", "enable-rcon": "false", "pause-when-empty-seconds": "0",
    "white-list": "false", "spawn-monsters": "false", "spawn-npcs": "true", "spawn-animals": "true", "motd": "modtest",
}


class Server:
    def __init__(self, t: Target, work: Path, port: int, mods: list[Path], jvm: list[str] | None = None,
                 props: dict | None = None, ops: list[tuple[str, str]] = (), files: dict[str, str] | None = None,
                 xmx: str = "2G"):
        self.t, self.work, self.port = t, work, port
        rmtree(work)
        (work / "mods").mkdir(parents=True)
        (work / "tmp").mkdir()
        (work / "eula.txt").write_text("eula=true\n")
        p = dict(PROPS, **{"server-port": str(port)}, **(props or {}))
        (work / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in p.items()))
        if ops:
            (work / "ops.json").write_text(json.dumps([{"uuid": u, "name": n, "level": 4, "bypassesPlayerLimit": False}
                                                       for n, u in ops]))
        for rel, text in (files or {}).items():
            f = work / rel
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(text, encoding="utf-8")
        for m in mods:
            shutil.copy2(m, work / "mods" / m.name)
        self.cmd = runtime.server_command(t, work, xmx, jvm)
        (work / "launch.txt").write_text(subprocess.list2cmdline(self.cmd) + "\n")
        self.log = work / "server.log"
        self.out = open(self.log, "w", encoding="utf-8", errors="replace")
        self.proc = subprocess.Popen(self.cmd, cwd=work, stdin=subprocess.PIPE, stdout=self.out, stderr=subprocess.STDOUT,
                                     text=True, encoding="utf-8", errors="replace",
                                     creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        self.lock = threading.Lock()
        self.started = time.time()

    def send(self, *lines: str) -> None:
        with self.lock:
            try:
                for line in lines:
                    self.proc.stdin.write(line + "\n")
                self.proc.stdin.flush()
            except (OSError, ValueError):
                pass

    def text(self) -> str:
        try:
            return self.log.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return ""

    def wait(self, pattern: str, timeout: float, after: int = 0) -> re.Match | None:
        end = time.time() + timeout
        while time.time() < end:
            m = re.search(pattern, self.text()[after:])
            if m:
                return m
            if self.proc.poll() is not None:
                return re.search(pattern, self.text()[after:])
            time.sleep(0.25)
        return None

    def wait_exit(self, timeout: float) -> int | None:
        try:
            return self.proc.wait(timeout)
        except subprocess.TimeoutExpired:
            return None

    def wait_exit_after(self, results: Path, timeout: float, grace: float = 60) -> tuple[int | None, bool]:
        """Waits for the process to end; once `results` exists it gets `grace` seconds to do so. Returns (exit code or
        None, whether it hung after the self-test had finished)."""
        end = time.time() + timeout
        while time.time() < end:
            code = self.proc.poll()
            if code is not None:
                return code, False
            if results.is_file() and time.time() > results.stat().st_mtime + grace:
                return None, True
            time.sleep(0.5)
        return None, False

    def live_threads(self) -> list[str]:
        """Names of the non-daemon Java threads of the running server (jstack of the same JDK), the ones that keep a
        stopped server's JVM alive. Empty if the process is gone or jstack failed."""
        if self.proc.poll() is not None:
            return []
        jstack = Path(self.cmd[0]).with_name("jstack.exe" if self.cmd[0].endswith(".exe") else "jstack")
        try:
            dump = subprocess.run([str(jstack), str(self.proc.pid)], capture_output=True, text=True, timeout=60,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)).stdout
        except (OSError, subprocess.TimeoutExpired):
            return []
        (self.work / "threads.txt").write_text(dump, encoding="utf-8")
        names = re.findall(r'^"([^"]+)" #\d+ (?!daemon)', dump, flags=re.M)
        return [n for n in names if n not in ("DestroyJavaVM",)]

    def stop(self, timeout: float = 90) -> None:
        if self.proc.poll() is None:
            self.send("stop")
            if self.wait_exit(timeout) is None:
                kill_tree(self.proc)
        try:
            self.proc.stdin.close()
        except OSError:
            pass
        self.out.close()
