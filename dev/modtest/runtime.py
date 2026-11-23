"""Production loader installs for dedicated servers, cached once per (Minecraft, loader, loader version).

fabric    the official Fabric installer (`server -downloadMinecraft`), the same files a server owner gets
forge     the official Forge installer (`--installServer`)
neoforge  the official NeoForge installer (`--install-server`)

A run never writes into the install: the server starts in its own work dir and the loader's argument file is
rewritten to absolute library paths. MODTEST_INSTALLERS may point at a folder of already downloaded installer jars.
"""
from __future__ import annotations

import os
import re
import subprocess
import time
from pathlib import Path

from . import slots
from .common import HOME, fetch, fetch_json, find_jdk, java_major, log
from .project import Target

LOCK_NOTE = "installed.ok"


def loader_version(t: Target) -> str:
    key = {"fabric": "deps.fabric_loader", "quilt": "deps.fabric_loader", "neoforge": "deps.neo_loader",
           "forge": "deps.forge_loader"}[t.loader]
    v = t.props.get(key) or t.props.get("modtest." + t.loader)
    if not v:
        raise SystemExit(f"{t.name}: {key} is not set in stonecutter.properties.toml (or modtest.toml [props])")
    return str(v)


def _installer(url: str, name: str) -> Path:
    local = os.environ.get("MODTEST_INSTALLERS")
    if local and (Path(local) / name).is_file():
        return Path(local) / name
    return fetch(url, HOME / "installers" / name)


def fabric_installer() -> Path:
    data = fetch_json("https://meta.fabricmc.net/v2/versions/installer", HOME / "meta" / "fabric-installers.json")
    v = next(i for i in data if i.get("stable"))
    return _installer(v["url"], f"fabric-installer-{v['version']}.jar")


def forge_installer(full: str) -> Path:  # full = "1.20.1-47.4.29"
    name = f"forge-{full}-installer.jar"
    return _installer(f"https://maven.minecraftforge.net/net/minecraftforge/forge/{full}/{name}", name)


def neoforge_installer(v: str) -> Path:
    name = f"neoforge-{v}-installer.jar"
    return _installer(f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{v}/{name}", name)


def _run(cmd: list[str], cwd: Path, what: str, attempts: int = 3) -> None:
    """Runs an installer; a failed download makes the Forge/NeoForge installers skip their processors and still exit 0,
    so their log is checked too. Network hiccups are retried."""
    cwd.mkdir(parents=True, exist_ok=True)
    logf = cwd / "install.log"
    for i in range(attempts):
        with open(logf, "w", encoding="utf-8", errors="replace") as f:
            r = subprocess.run(cmd, cwd=cwd, stdout=f, stderr=subprocess.STDOUT)
        failed = "There was an error during installation" in logf.read_text(encoding="utf-8", errors="replace")
        if r.returncode == 0 and not failed:
            return
        if i + 1 < attempts:
            log(f"     {what} failed (exit {r.returncode}), retrying")
            time.sleep(5)
    raise SystemExit(f"{what} failed (exit {r.returncode}), see {logf}")


def server_install(t: Target) -> Path:
    lv = loader_version(t)
    d = HOME / "servers" / f"{t.mc}-{t.loader}-{lv}"
    if (d / LOCK_NOTE).is_file():
        return d
    with slots.exclusive(f"server-install-{d.name}"):
        if not (d / LOCK_NOTE).is_file():  # another run may have installed it while this one waited
            _install(t, lv, d)
    return d


def _install(t: Target, lv: str, d: Path) -> None:
    java = str(find_jdk(java_major(t.mc)))
    log(f"     installing {t.loader} {lv} server for {t.mc} into {d}")
    if t.loader in ("fabric", "quilt"):
        _run([java, "-jar", str(fabric_installer()), "server", "-dir", str(d), "-mcversion", t.mc, "-loader", lv,
              "-downloadMinecraft"], d, "Fabric server install")
    elif t.loader == "forge":
        _run([java, "-jar", str(forge_installer(lv)), "--installServer", str(d)], d, "Forge server install")
    else:
        _run([java, "-jar", str(neoforge_installer(lv)), "--install-server", str(d)], d, "NeoForge server install")
    (d / LOCK_NOTE).write_text(lv)


def _abs_args(args_file: Path, install: Path) -> list[str]:
    lib = (install / "libraries").as_posix()
    text = args_file.read_text(encoding="utf-8")
    text = re.sub(r"(^|[;= \n])libraries(?=[/\s;]|$)", lambda m: m.group(1) + lib, text, flags=re.M)
    out = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("-") and " " in line and not line.startswith("-D"):
            opt, val = line.split(" ", 1)
            out += [opt, val]
        else:
            out += line.split(" ") if line.startswith("--fml") or line.startswith("--launchTarget") else [line]
    return out


def server_command(t: Target, work: Path, xmx: str = "2G", jvm: list[str] | None = None) -> list[str]:
    """The java command line of a production server for `t`, started with `work` as its directory."""
    inst = server_install(t)
    java = str(find_jdk(java_major(t.mc)))
    head = [java, f"-Xmx{xmx}", "-Dfile.encoding=UTF-8", f"-Djava.io.tmpdir={work / 'tmp'}", *(jvm or [])]
    if t.loader in ("fabric", "quilt"):
        jar = (inst / "server.jar").as_posix().replace(":", "\\:")
        (work / "fabric-server-launcher.properties").write_text(f"serverJar={jar}\n")
        return head + ["-jar", str(inst / "fabric-server-launch.jar"), "nogui"]
    group = "net/minecraftforge/forge" if t.loader == "forge" else "net/neoforged/neoforge"
    v = loader_version(t)
    args = inst / "libraries" / group / v / "win_args.txt"
    if not args.is_file():
        hits = list((inst / "libraries" / group).glob("*/win_args.txt"))
        if not hits:
            raise SystemExit(f"{t.name}: no win_args.txt in {inst}")
        args = hits[0]
    return head + _abs_args(args, inst) + ["nogui"]
