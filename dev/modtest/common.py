"""Shared paths, downloads, JDK lookup and small helpers of the UtherHel mod test harness.

The harness lives in every mod repository as dev/modtest (a synced copy; the master is C:\\MCProjects\\tools\\modtest,
run its sync.py after an edit). Machine-wide caches (loader installs, client libraries, assets, downloaded mods) go to
MODTEST_HOME, default %LOCALAPPDATA%\\utherhel-modtest; per-run work dirs go to <repo>/dev/.work and are git-ignored.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

HOME = Path(os.environ.get("MODTEST_HOME") or Path(os.environ.get("LOCALAPPDATA", Path.home())) / "utherhel-modtest")
WINDOWS = os.name == "nt"
CP_SEP = ";" if WINDOWS else ":"
PRINT = threading.Lock()
UA = {"User-Agent": "utherhel-modtest/1 (+https://github.com/UtherHel)"}


def log(*parts) -> None:
    with PRINT:
        print(*parts, flush=True)


def fetch(url: str, dest: Path, sha1: str | None = None, tries: int = 4) -> Path:
    """Downloads url to dest once (atomic rename); verifies sha1 when given."""
    if dest.is_file() and (sha1 is None or file_sha1(dest) == sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_name(dest.name + ".part")
    err: Exception | None = None
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r, open(tmp, "wb") as f:
                shutil.copyfileobj(r, f, 1 << 20)
            if sha1 and file_sha1(tmp) != sha1:
                raise OSError(f"sha1 mismatch for {url}")
            os.replace(tmp, dest)
            return dest
        except urllib.error.HTTPError as e:
            err = e
            if e.code == 404:
                break
        except OSError as e:
            err = e
        time.sleep(2 ** i)
    tmp.unlink(missing_ok=True)
    raise OSError(f"download failed: {url}: {err}")


def fetch_json(url: str, cache: Path | None = None, max_age: float = 86400):
    if cache and cache.is_file() and time.time() - cache.stat().st_mtime < max_age:
        return json.loads(cache.read_text(encoding="utf-8"))
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        data = r.read()
    if cache:
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_bytes(data)
    return json.loads(data)


def file_sha1(p: Path) -> str:
    h = hashlib.sha1()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def version_tuple(v: str) -> tuple[int, ...]:
    return tuple(int(x) for x in re.findall(r"\d+", v)[:3])


def java_major(mc: str) -> int:
    """The Java a Minecraft release ships with: 17 up to 1.20.4, 21 up to 1.21.11, 25 from 26.1."""
    t = version_tuple(mc)
    if t >= (26,):
        return 25
    if t >= (1, 20, 5):
        return 21
    return 17


def find_jdk(major: int) -> Path:
    """java.exe of a JDK `major`: MODTEST_JDK_<major>, then common install roots and Gradle toolchains."""
    env = os.environ.get(f"MODTEST_JDK_{major}")
    if env:
        return Path(env) / "bin" / ("java.exe" if WINDOWS else "java")
    roots = [Path(r"C:\Program Files\Eclipse Adoptium"), Path(r"C:\Program Files\Java"), Path(r"C:\Program Files\Zulu"),
             Path(r"C:\Program Files\Microsoft"), Path.home() / ".gradle" / "jdks", Path.home() / ".jdks"]
    for root in roots:
        if not root.is_dir():
            continue
        for d in sorted(root.iterdir(), reverse=True):
            m = re.search(r"(?:jdk|temurin|zulu|openjdk)[-_]?(\d+)", d.name, re.I)
            exe = d / "bin" / ("java.exe" if WINDOWS else "java")
            if m and int(m.group(1)) == major and exe.is_file():
                return exe
    sys.exit(f"no JDK {major} found: install one or set MODTEST_JDK_{major} to its home")


def kill_tree(proc: subprocess.Popen) -> None:
    if proc.poll() is not None:
        return
    if WINDOWS:
        subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)], capture_output=True)
    else:
        proc.kill()
    try:
        proc.wait(30)
    except subprocess.TimeoutExpired:
        pass


def free_port() -> int:
    import socket
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def rmtree(p: Path) -> None:
    """Removes a work dir; retries because Windows keeps files of a just-killed JVM locked for a moment."""
    for i in range(10):
        if not p.exists():
            return
        try:
            shutil.rmtree(p)
            return
        except OSError:
            time.sleep(1 + i)
    shutil.rmtree(p, ignore_errors=True)


def tail(path: Path, n: int = 25) -> list[str]:
    try:
        return path.read_text(encoding="utf-8", errors="replace").splitlines()[-n:]
    except OSError:
        return []


def offline_uuid(name: str) -> str:
    """The UUID an offline-mode server gives `name` (UUID v3 of "OfflinePlayer:<name>")."""
    import uuid
    h = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    h[6] = h[6] & 0x0F | 0x30
    h[8] = h[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(h)))
