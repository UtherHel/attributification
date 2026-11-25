"""A production Minecraft client, installed and launched the way a launcher does it, without any launcher.

Vanilla comes from Mojang's version manifest (assets are copied from %APPDATA%\\.minecraft when present), Fabric from the
Fabric meta profile, Forge and NeoForge from their official installers (`--installClient`). The game runs offline
(`--userType legacy`), in its own game dir, with the mod and its runtime mods in mods/. By default it is headless
(HeadlessMc's LWJGL patch: no window, nothing rendered, screenshots skipped); MODTEST_VISIBLE=1 shows the real window.
Options keep it small, muted and without onboarding screens.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from . import runtime
from .common import HOME, CP_SEP, fetch, fetch_json, find_jdk, java_major, kill_tree, log, offline_uuid, rmtree, \
    version_tuple
from .project import Target

ROOT = HOME / "client"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
# Headless clients: HeadlessMc's LWJGL transformer (github.com/headlesshq/headlessmc, MIT; what mc-runtime-test runs in
# CI) turns every LWJGL call into a no-op, so the client runs without a window, a GPU or a display. LwjglPatch.java
# applies it to copies of the LWJGL jars (cached per HeadlessMc version, jar and patch tool). MODTEST_VISIBLE=1 (or
# --visible) runs the unpatched game with its real window instead, e.g. for screenshots.
HMC_VERSION = "3.0.0-RC9"
HMC_ALL = f"https://github.com/headlesshq/headlessmc/releases/download/{HMC_VERSION}/headlessmc-lwjgl-{HMC_VERSION}-all.jar"
HMC_MODULE = "io.github.headlesshq.headlessmc.lwjgl"
LAUNCHER_ASSETS = Path(os.environ.get("APPDATA", Path.home())) / ".minecraft" / "assets"
OS_NAME, ARCH = "windows", "x86_64"


# ---------------------------------------------------------------- rules and libraries
def allowed(rules, features: frozenset = frozenset({"has_custom_resolution"})) -> bool:
    if not rules:
        return True
    ok = False
    for r in rules:
        o = r.get("os", {})
        if o.get("name") not in (None, OS_NAME):
            continue
        if o.get("arch") not in (None, ARCH, "x64", "amd64"):
            continue
        feats = r.get("features", {})
        if any(bool(v) != (k in features) for k, v in feats.items()):
            continue
        ok = r["action"] == "allow"
    return ok


def maven_path(name: str) -> str:
    parts = name.split(":")
    g, a, v = parts[:3]
    ext = "jar"
    if "@" in v:
        v, ext = v.split("@")
    cls = f"-{parts[3]}" if len(parts) > 3 else ""
    if cls and "@" in cls:
        cls, ext = cls.split("@")
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}{cls}.{ext}"


def lib_key(lib: dict) -> str:
    p = lib["name"].split(":")
    return f"{p[0]}:{p[1]}:{p[3] if len(p) > 3 else ''}"


def lib_file(lib: dict) -> tuple[Path, str | None, str | None]:
    art = lib.get("downloads", {}).get("artifact")
    if art and art.get("path"):
        return ROOT / "libraries" / art["path"], art.get("url") or None, art.get("sha1")
    rel = maven_path(lib["name"])
    base = lib.get("url")
    return ROOT / "libraries" / rel, (base.rstrip("/") + "/" + rel) if base else None, lib.get("sha1")


def download_libraries(libs: list[dict]) -> None:
    jobs = []
    for lib in libs:
        if not allowed(lib.get("rules")):
            continue
        path, url, sha1 = lib_file(lib)
        if not path.is_file() and url:
            jobs.append((url, path, sha1))
    with ThreadPoolExecutor(8) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))


# ---------------------------------------------------------------- vanilla
def vanilla(mc: str) -> dict:
    vj = ROOT / "versions" / mc / f"{mc}.json"
    if not vj.is_file():
        man = fetch_json(MANIFEST, HOME / "meta" / "version_manifest_v2.json")
        entry = next((v for v in man["versions"] if v["id"] == mc), None)
        if entry is None:
            man = fetch_json(MANIFEST, HOME / "meta" / "version_manifest_v2.json", max_age=0)
            entry = next(v for v in man["versions"] if v["id"] == mc)
        fetch(entry["url"], vj, entry.get("sha1"))
    data = json.loads(vj.read_text(encoding="utf-8"))
    cj = data["downloads"]["client"]
    fetch(cj["url"], ROOT / "versions" / mc / f"{mc}.jar", cj["sha1"])
    download_libraries(data["libraries"])
    assets(data)
    return data


def assets(data: dict) -> None:
    ai = data["assetIndex"]
    idx = ROOT / "assets" / "indexes" / f"{ai['id']}.json"
    done = ROOT / "assets" / f"{ai['id']}.complete"
    if done.is_file():
        return
    fetch(ai["url"], idx, ai["sha1"])
    objects = json.loads(idx.read_text(encoding="utf-8"))["objects"]

    def one(h: str) -> None:
        rel = Path(h[:2]) / h
        dest = ROOT / "assets" / "objects" / rel
        if dest.is_file():
            return
        local = LAUNCHER_ASSETS / "objects" / rel
        if local.is_file():
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(local, dest)
        else:
            fetch(f"https://resources.download.minecraft.net/{h[:2]}/{h}", dest, h)

    log(f"     assets {ai['id']}: {len(objects)} objects")
    with ThreadPoolExecutor(16) as ex:
        list(ex.map(one, {o["hash"] for o in objects.values()}))
    done.write_text("ok")


# ---------------------------------------------------------------- loaders
def install(t: Target) -> str:
    """Installs vanilla + the loader for `t`; returns the version id to launch."""
    vanilla(t.mc)
    if t.loader == "vanilla":
        return t.mc
    lv = runtime.loader_version(t)
    if t.loader in ("fabric", "quilt"):
        vid = f"fabric-loader-{lv}-{t.mc}"
        vj = ROOT / "versions" / vid / f"{vid}.json"
        if not vj.is_file():
            fetch(f"https://meta.fabricmc.net/v2/versions/loader/{t.mc}/{lv}/profile/json", vj)
    else:
        vid = f"{t.mc}-forge-{lv.split('-', 1)[1]}" if t.loader == "forge" else f"neoforge-{lv}"
        vj = ROOT / "versions" / vid / f"{vid}.json"
        ok = ROOT / "versions" / vid / "modtest-installed.ok"  # the json alone is written before the processors run
        if not ok.is_file():
            prof = ROOT / "launcher_profiles.json"
            if not prof.is_file():
                prof.write_text(json.dumps({"profiles": {}, "selectedProfile": "", "version": 3}))
            java = str(find_jdk(java_major(t.mc)))
            inst = runtime.forge_installer(lv) if t.loader == "forge" else runtime.neoforge_installer(lv)
            flag = "--installClient" if t.loader == "forge" else "--install-client"
            log(f"     installing {t.loader} {lv} client")
            runtime._run([java, "-jar", str(inst), flag, str(ROOT)], ROOT / "install-logs" / vid, f"{t.loader} client install")
            if not vj.is_file():
                raise SystemExit(f"{t.loader} installer did not create {vj}")
            ok.write_text(lv)
    data = json.loads(vj.read_text(encoding="utf-8"))
    download_libraries(data.get("libraries", []))
    jar = ROOT / "versions" / vid / f"{vid}.jar"
    if not jar.is_file():
        shutil.copy2(ROOT / "versions" / t.mc / f"{t.mc}.jar", jar)
    return vid


# ---------------------------------------------------------------- launch
def _expand(args, subs: dict, features: frozenset) -> list[str]:
    out = []
    for a in args:
        if isinstance(a, dict):
            if not allowed(a.get("rules"), features):
                continue
            vals = a["value"] if isinstance(a["value"], list) else [a["value"]]
        else:
            vals = [a]
        for v in vals:
            for k, s in subs.items():
                v = v.replace("${" + k + "}", s)
            if "${" not in v:
                out.append(v)
    return out


OPTIONS = {
    "pauseOnLostFocus": "false", "onboardAccessibility": "false", "tutorialStep": "none", "skipMultiplayerWarning": "true",
    "joinedFirstServer": "true", "narrator": "0", "fullscreen": "false", "renderDistance": "4", "simulationDistance": "5",
    "maxFps": "60", "soundCategory_master": "0.0", "guiScale": "2", "autoJump": "false", "realmsNotifications": "false",
    "hideServerAddress": "true", "telemetryOptInExtra": "false",
}


def headless() -> bool:
    return os.environ.get("MODTEST_VISIBLE", "") in ("", "0", "false")


def headless_classpath(cp: list[str]) -> list[str]:
    """`cp` with every LWJGL jar replaced by its headless copy, and HeadlessMc's redirection API in front."""
    tool = Path(__file__).with_name("LwjglPatch.java")
    base = HOME / "hmc" / HMC_VERSION / hashlib.sha1(tool.read_bytes().replace(b"\r\n", b"\n")).hexdigest()[:8]
    all_jar = fetch(HMC_ALL, HOME / "hmc" / Path(HMC_ALL).name)
    api = base / "headlessmc-lwjgl.jar"
    out, todo = [], []
    for p in cp:
        if "lwjgl" not in Path(p).name.lower():
            out.append(p)
            continue
        dest = base / "lwjgl" / hashlib.sha1(p.encode()).hexdigest()[:12] / Path(p).name
        out.append(str(dest))
        if not dest.is_file():
            todo += [p, str(dest)]
    if todo or not api.is_file():
        lwjgl = [p for p in cp if "lwjgl" in Path(p).name.lower()]
        java = str(find_jdk(21))
        args = [java, "-cp", CP_SEP.join([str(all_jar), *lwjgl]), str(tool), HMC_MODULE, "--api", str(all_jar), str(api),
                *todo]
        r = subprocess.run(args, capture_output=True, text=True, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if r.returncode != 0:
            raise SystemExit(f"headless LWJGL patch failed: {(r.stderr or r.stdout)[-2000:]}")
    return [str(api), *out]


def command(t: Target, vid: str, game: Path, user: str, props: dict[str, str], join: str | None,
            width: int = 960, height: int = 540, headless_lwjgl: bool = False) -> list[str]:
    child = json.loads((ROOT / "versions" / vid / f"{vid}.json").read_text(encoding="utf-8"))
    chain = [child]
    while chain[-1].get("inheritsFrom"):
        p = chain[-1]["inheritsFrom"]
        chain.append(json.loads((ROOT / "versions" / p / f"{p}.json").read_text(encoding="utf-8")))
    root_v = chain[-1]
    cp, seen = [], set()
    for v in chain:
        for lib in v.get("libraries", []):
            if not allowed(lib.get("rules")) or lib_key(lib) in seen:
                continue
            path, _, _ = lib_file(lib)
            if not path.is_file():
                continue
            seen.add(lib_key(lib))
            cp.append(str(path))
    cp.append(str(ROOT / "versions" / vid / f"{vid}.jar"))
    if headless_lwjgl:
        cp = headless_classpath(cp)
    natives = game / "natives"
    natives.mkdir(parents=True, exist_ok=True)
    uuid = offline_uuid(user)
    subs = {
        "auth_player_name": user, "version_name": vid, "game_directory": str(game), "assets_root": str(ROOT / "assets"),
        "assets_index_name": root_v["assetIndex"]["id"], "auth_uuid": uuid.replace("-", ""), "auth_access_token": "0",
        "clientid": "0", "auth_xuid": "0", "user_type": "legacy", "version_type": "release", "user_properties": "{}",
        "natives_directory": str(natives), "launcher_name": "utherhel-modtest", "launcher_version": "1",
        "classpath": CP_SEP.join(cp), "classpath_separator": CP_SEP, "library_directory": str(ROOT / "libraries"),
        "resolution_width": str(width), "resolution_height": str(height),
    }
    feats = frozenset({"has_custom_resolution"})
    jvm, game_args = [], []
    for v in reversed(chain):
        a = v.get("arguments", {})
        jvm += _expand(a.get("jvm", []), subs, feats)
        game_args += _expand(a.get("game", []), subs, feats)
    if not jvm:
        jvm = [f"-Djava.library.path={natives}", "-cp", subs["classpath"]]
    java = str(find_jdk(java_major(t.mc)))
    extra = [f"-D{k}={v}" for k, v in props.items()]
    cmd = [java, "-Xmx3G", "-Dfile.encoding=UTF-8", f"-Djava.io.tmpdir={game / 'tmp'}", *extra, *jvm, child["mainClass"],
           *game_args]
    if join and version_tuple(t.mc) >= (1, 20):
        cmd += ["--quickPlayMultiplayer", join]
    return cmd


def run(t: Target, game: Path, mods: list[Path], props: dict[str, str], user: str = "ModTester", join: str | None = None,
        timeout: float = 600, done: Path | None = None, files: dict[str, str] | None = None) -> tuple[int | None, Path]:
    """Starts the client and waits until it exits (or `done` appears and the game stays up for a grace period, or the
    timeout). `files`: game-dir relative path -> text, written before the start (modtest.toml files_client).
    Returns (exit code or None on timeout, log path). Headless unless MODTEST_VISIBLE is set."""
    vid = install(t)
    rmtree(game)
    (game / "mods").mkdir(parents=True)
    (game / "tmp").mkdir()
    for rel, text in (files or {}).items():
        p = game / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
    for m in mods:
        shutil.copy2(m, game / "mods" / m.name)
    (game / "options.txt").write_text("".join(f"{k}:{v}\n" for k, v in OPTIONS.items()))
    props = dict(props)
    if headless():
        # LWJGL hands out heap buffers then: JOML must not use Unsafe on them (what HeadlessMc sets as well)
        props = {"modtest.headless": "true", "joml.nounsafe": "true", **props}
        if t.loader in ("forge", "neoforge"):  # FML's early loading window is its own GLFW window
            (game / "config").mkdir(exist_ok=True)
            (game / "config" / "fml.toml").write_text("earlyWindowControl = false\n")
    cmd = command(t, vid, game, user, props, join, headless_lwjgl=headless())
    (game / "launch.txt").write_text(subprocess.list2cmdline(cmd) + "\n")
    logf = game / "client.log"
    with open(logf, "w", encoding="utf-8", errors="replace") as out:
        proc = subprocess.Popen(cmd, cwd=game, stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        end, code = time.time() + timeout, None
        while time.time() < end:
            code = proc.poll()
            if code is not None:
                break
            if done and done.is_file() and time.time() > done.stat().st_mtime + 45:
                break  # results written but the game did not quit by itself
            time.sleep(1)
        if proc.poll() is None:
            kill_tree(proc)
        code = proc.poll() if code is None else code
    return code, logf
