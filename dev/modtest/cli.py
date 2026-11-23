"""python dev/mt.py <command> [targets] [options]

  targets              the Stonecutter targets and whether a jar of the current version is built
  build   [targets]    gradlew build for the targets (Gradle on JDK 25, tests skipped)
  smoke   [targets]    production dedicated server: boots with the mod and its runtime mods, the in-mod self-test
                       checks that the mod is active and every mixin applies (mixin audit), the log stays clean
  test    [targets]    the same server running the mod's server scenes (dev/... Scenarios, mode "full")
  client  [targets]    production client: reaches the title screen, mixin audit, log clean (headless: no window;
                       --visible shows it and takes screenshots)
  mp      [targets]    server + client over the network; --case both,client_only,server_only (default both):
                       the server runs the player scenes once the client joined, the client its client scenes
  all     [targets]    smoke, test, client and mp both, in that order (stops a target at its first failure)

Targets: comma list of names or globs (`1.21.1-fabric`, `1.21.1-*`, `addon-parcool:*`); default: dev/modtest.toml
default_targets, else every core target. Options: --build (build first), -j N (parallel servers), --keep (keep work
dirs of passing runs), --scene a,b (only these scenes), --timeout S.
Exit code 0 only if every run passed. Work dirs: dev/.work/<command>/<target>, a summary in dev/.work/report.md.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

from . import client, deps, logscan, slots
from .common import free_port, log, offline_uuid, rmtree, tail
from .project import Project, Target
from .server import Server

PLAYER = "ModTester"


@dataclass
class Result:
    command: str
    target: str
    ok: bool
    seconds: float
    checks: list[tuple[bool, str, str]] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    work: Path | None = None


def read_results(path: Path) -> tuple[dict | None, list[tuple[bool, str, str]]]:
    if not path.is_file():
        return None, []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return None, [(False, "results.json", f"unreadable: {e}")]
    return data, [(bool(r["pass"]), r["name"], r.get("detail", "")) for r in data.get("results", [])]


def selftest_props(proj: Project, t: Target, mode: str, out: Path, scene: str | None, side: str,
                   extra: dict | None = None) -> dict:
    p = {f"{proj.mod_id}.selftest": str(out), f"{proj.mod_id}.selftest.mode": mode, "modtest.audit": "1",
         f"{proj.mod_id}.selftest.side": side}
    if scene:
        p[f"{proj.mod_id}.selftest.scene"] = scene
    expect = list(proj.cfg.get("expect_loaded", {}).get(t.loader, []))
    if t.branch:
        expect += [proj.cfg.get("branch_mod_ids", {}).get(t.branch, t.branch.replace("-", "_"))]
    if expect:
        p[f"{proj.mod_id}.selftest.expect"] = ",".join(expect)
    p.update(extra or {})
    return p


def jvm_props(props: dict) -> list[str]:
    return [f"-D{k}={v}" for k, v in props.items()]


def check_log(res: Result, text: str, proj: Project, who: str) -> None:
    fatal, errors, warns = logscan.scan(text, proj.mod_id, proj.cfg.get("log_noise", []))
    for line in fatal[:8]:
        res.checks.append((False, f"{who}/log", line))
    for line in errors[:8]:
        res.checks.append((False, f"{who}/log", line))
    if not fatal and not errors:
        res.checks.append((True, f"{who}/log", f"no errors ({len(warns)} mod warnings)"))
    for line in warns[:5]:
        res.notes.append(f"{who} warning: {line}")


def known_issues(proj: Project, t: Target, res: Result) -> None:
    """modtest.toml `known_failures`: regexes of check names (a list, or a table keyed like [runtime]) that fail
    because of a known, not yet fixed bug. Such a failure counts as passed and is reported as a warning; a known
    failure that passes is reported too, so the entry gets removed once the bug is fixed."""
    table = proj.cfg.get("known_failures", [])
    pats = [re.compile(p) for p in (table if isinstance(table, list) else deps.specs(proj, t, "known_failures"))]
    if not pats:
        return
    checks = []
    for ok, name, detail in res.checks:
        known = any(p.search(name) for p in pats)
        if known and not ok:
            checks.append((True, name, f"KNOWN ISSUE: {detail}"))
            res.notes.append(f"warning: known issue {name}: {detail}")
        else:
            if known:
                res.notes.append(f"warning: known issue {name} passes now: remove it from known_failures")
            checks.append((ok, name, detail))
    res.checks = checks


def finish(proj: Project, t: Target, res: Result, keep: bool) -> Result:
    known_issues(proj, t, res)
    res.ok = res.ok and all(c[0] for c in res.checks) and bool(res.checks)
    if res.ok and not keep and res.work:
        rmtree(res.work)
        res.work = None
    return res


# ---------------------------------------------------------------- server runs
def server_run(proj: Project, t: Target, command: str, mode: str, args, port: int) -> Result:
    t0 = time.time()
    work = proj.root / "dev" / ".work" / command / t.slug
    res = Result(command, t.name, True, 0, work=work)
    out = work / "out"
    rmtree(out)
    try:
        mods = deps.runtime_mods(proj, t, "server")
    except (OSError, SystemExit) as e:
        res.ok = False
        res.notes.append(f"runtime mods: {e}")
        return res
    props = selftest_props(proj, t, mode, out, args.scene, "server")
    timeout = args.timeout or (900 if mode == "full" else 600)
    with slots.slot("server", f"{proj.mod_id} {command} {t.name}"):
        try:
            srv = Server(t, work / "server", port, mods, jvm_props(props), files=extra_files(proj, t, "server"))
        except (OSError, SystemExit) as e:  # server install failed
            res.ok = False
            res.notes.append(f"server: {e}")
            return res
        try:
            if not srv.wait(r"Done \(\d", 420):
                res.ok = False
                res.notes.append("server did not finish starting")
                res.notes += tail(srv.log, 15)
            else:
                res.notes.append(f"started in {time.time() - srv.started:.0f} s")
                for line in proj.cfg.get("server_commands", []):
                    srv.send(proj.expand(line, t))
                code, hung = srv.wait_exit_after(out / "server.json", timeout)
                if hung:
                    exit_check(res, proj, srv.live_threads())
                elif code is None:
                    res.ok = False
                    res.notes.append(f"self-test did not finish within {timeout} s")
                else:
                    res.checks.append((True, "server/exit", "the JVM exited after the server stopped"))
        finally:
            srv.stop()
    data, checks = read_results(out / "server.json")
    if data is None:
        res.ok = False
        res.notes.append("no server results (the self-test never ran: mod not loaded, or its server hook missing)")
        res.notes += tail(srv.log, 12)
    res.checks += checks
    check_log(res, srv.text(), proj, "server")
    res.seconds = time.time() - t0
    return finish(proj, t, res, args.keep)


def exit_check(res: Result, proj: Project, threads: list[str]) -> None:
    """The server stopped (its self-test halted it) but the JVM stayed up: some mod left a non-daemon thread running,
    so a real server hangs after /stop too. Threads matching modtest.toml `known_exit_hangs` (regexes) are reported
    but do not fail the run, e.g. a library's worker the mod cannot shut down."""
    known = [re.compile(p) for p in proj.cfg.get("known_exit_hangs", [])]
    unknown = [n for n in threads if not any(k.search(n) for k in known)]
    names = ", ".join(threads) or "unknown (no thread dump)"
    if threads and not unknown:
        res.checks.append((True, "server/exit", f"JVM kept alive after stop by known thread(s): {names} (killed)"))
        res.notes.append(f"warning: the server JVM does not exit after stop: {names}")
    else:
        res.checks.append((False, "server/exit", f"the JVM did not exit 60 s after the server stopped; non-daemon "
                                                 f"threads: {names} (dump: server/threads.txt)"))


def extra_files(proj: Project, t: Target, side: str) -> dict[str, str]:
    files = {}
    for key, table in proj.cfg.get(f"files_{side}", {}).items():
        import fnmatch
        if key in (t.loader, t.name) or fnmatch.fnmatch(t.name, key):
            files.update({rel: proj.expand(text, t) for rel, text in table.items()})
    return files


# ---------------------------------------------------------------- client runs
def client_run(proj: Project, t: Target, args) -> Result:
    t0 = time.time()
    work = proj.root / "dev" / ".work" / "client" / t.slug
    res = Result("client", t.name, True, 0, work=work)
    out = work / "out"
    rmtree(out)
    try:
        mods = deps.runtime_mods(proj, t, "client")
    except (OSError, SystemExit) as e:
        res.ok = False
        res.notes.append(f"runtime mods: {e}")
        return res
    props = selftest_props(proj, t, "boot", out, args.scene, "client")
    with slots.slot("client", f"{proj.mod_id} client {t.name}"):
        try:
            code, logf = client.run(t, work / "game", mods, props, PLAYER, None, args.timeout or 600,
                                    out / "client.json", extra_files(proj, t, "client"))
        except (OSError, SystemExit) as e:  # client install failed
            res.ok = False
            res.notes.append(f"client: {e}")
            return res
    data, checks = read_results(out / "client.json")
    if data is None:
        res.ok = False
        res.notes.append(f"no client results (exit {code})")
        res.notes += tail(logf, 12)
    res.checks += checks
    check_log(res, logf.read_text(encoding="utf-8", errors="replace") if logf.is_file() else "", proj, "client")
    res.seconds = time.time() - t0
    return finish(proj, t, res, args.keep)


def mp_run(proj: Project, t: Target, case: str, args, port: int) -> Result:
    t0 = time.time()
    work = proj.root / "dev" / ".work" / f"mp-{case}" / t.slug
    res = Result(f"mp {case}", t.name, True, 0, work=work)
    out = work / "out"
    rmtree(out)
    modded_server, modded_client = case != "client_only", case != "server_only"
    try:
        smods = deps.runtime_mods(proj, t, "server") if modded_server else []
        cmods = deps.runtime_mods(proj, t, "client") if modded_client else []
    except (OSError, SystemExit) as e:
        res.ok = False
        res.notes.append(f"runtime mods: {e}")
        return res
    vt = Target(t.mc, "vanilla", project=t.project, props=t.props)
    sprops = selftest_props(proj, t, "mp", out, args.scene, "server",
                            {f"{proj.mod_id}.selftest.players": "1",
                             f"{proj.mod_id}.selftest.waitClient": "true" if modded_client else "false"})
    cprops = selftest_props(proj, t, "mp", out, args.scene, "client",
                            {f"{proj.mod_id}.selftest.serverMod": "false"} if not modded_server else None)
    if not modded_client:
        cprops = {}
    timeout = args.timeout or 900
    with slots.slot("server", f"{proj.mod_id} mp {case} {t.name}"), slots.slot("client", f"{proj.mod_id} mp {case} {t.name}"):
        try:
            if modded_server:
                srv = Server(t, work / "server", port, smods, jvm_props(sprops), ops=[(PLAYER, offline_uuid(PLAYER))],
                             files=extra_files(proj, t, "server"))
            else:
                # the same loader server without the mod: what a player meets on a server that doesn't have it
                srv = Server(t, work / "server", port, [], ops=[(PLAYER, offline_uuid(PLAYER))])
        except (OSError, SystemExit) as e:  # server install failed
            res.ok = False
            res.notes.append(f"server: {e}")
            return res
        cres: dict = {}
        try:
            if not srv.wait(r"Done \(\d", 420):
                res.ok = False
                res.notes.append("server did not finish starting")
                res.notes += tail(srv.log, 15)
                return finish(proj, t, res, True)
            ct = t if modded_client else vt

            # both: the server director kicks the player when its scenes are done, the client then quits;
            # server_only: a vanilla client has no director, it is closed once the server wrote its results;
            # client_only: the client director leaves the server and quits by itself
            done = out / "server.json" if not modded_client else None

            def go():
                try:
                    cres["code"], cres["log"] = client.run(ct, work / "game", cmods, cprops, PLAYER,
                                                           f"127.0.0.1:{port}", timeout, done,
                                                           extra_files(proj, t, "client") if modded_client else None)
                except (OSError, SystemExit) as e:  # client install failed
                    cres["error"] = str(e)
            th = threading.Thread(target=go)
            th.start()
            pattern = rf"{PLAYER}(\[[^\]]*\])? (joined the game|logged in)"
            joined, end = None, time.time() + 300
            while not joined and time.time() < end and th.is_alive():  # a client that crashed will never join
                joined = srv.wait(pattern, 5)
            joined = joined or re.search(pattern, srv.text())
            if cres.get("error"):
                res.notes.append(f"client: {cres['error']}")
            res.checks.append((bool(joined), "server/join", "the client joined" if joined else "the client never joined"))
            if not joined and srv.live_threads():  # where the server was while the client could not get in
                res.notes.append("server thread dump: server/threads.txt")
            th.join(timeout + 60)
            if modded_server and srv.wait_exit(120) is None:
                res.notes.append("server self-test did not finish after the session")
        finally:
            srv.stop()
    if modded_server:
        data, checks = read_results(out / "server.json")
        if data is None:
            res.ok = False
            res.notes.append("no server results")
        res.checks += [(ok, "server/" + n, d) for ok, n, d in checks]
    if modded_client:
        data, checks = read_results(out / "client.json")
        if data is None:
            res.ok = False
            res.notes.append(f"no client results (exit {cres.get('code')})")
        res.checks += [(ok, "client/" + n, d) for ok, n, d in checks]
    check_log(res, srv.text(), proj, "server")
    logf = cres.get("log")
    if logf and Path(logf).is_file():
        check_log(res, Path(logf).read_text(encoding="utf-8", errors="replace"), proj, "client")
    left = [l for l in srv.text().splitlines() if f"{PLAYER} lost connection" in l and "modtest done" not in l
            and "Disconnected" not in l and "Server closed" not in l]
    for l in left:
        res.checks.append((False, "server/disconnect", l.strip()[:200]))
    res.seconds = time.time() - t0
    return finish(proj, t, res, args.keep)


def flaky_join(r: Result) -> str:
    """Why the client of a failed mp run never got into the world, when that looks like the machine (a connect that
    hangs, a timeout under load) rather than the mod; "" otherwise. A disconnect with a reason (registry mismatch,
    missing mod, restart prompt) is the mod's result and is not retried."""
    if r.ok or not r.work or not r.work.is_dir() or not any(n == "server/join" and not ok for ok, n, _ in r.checks):
        return ""
    detail = next((d for ok, n, d in r.checks if n == "client/join" and not ok), "")
    if detail.startswith("stuck connecting") or (detail.startswith("disconnected while joining") and re.search(
            r"(?i)timed? ?out|connection (refused|reset)", detail)):
        return detail.split(";")[0][:120]
    return ""


# ---------------------------------------------------------------- report
def print_result(r: Result) -> None:
    log(f"{'PASS' if r.ok else 'FAIL'} {r.command:16} {r.target:28} {r.seconds:5.0f} s  "
        f"{sum(c[0] for c in r.checks)}/{len(r.checks)} checks" + (f"  work: {r.work}" if r.work else ""))
    for ok, name, detail in (r.checks if not r.ok else []):
        log(f"     {'PASS' if ok else 'FAIL'} {name}  {detail}")
    for n in r.notes if not r.ok else [n for n in r.notes if "warning" in n]:
        log(f"     {n}")


def write_report(proj: Project, results: list[Result]) -> Path:
    p = proj.root / "dev" / ".work" / "report.md"
    p.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"# {proj.mod_id} {proj.version}: {sum(r.ok for r in results)}/{len(results)} runs passed", "",
             "| Run | Target | Result | Checks | Time |", "|---|---|---|---|---|"]
    for r in results:
        lines.append(f"| {r.command} | {r.target} | {'PASS' if r.ok else 'FAIL'} | "
                     f"{sum(c[0] for c in r.checks)}/{len(r.checks)} | {r.seconds:.0f} s |")
    for r in results:
        lines += ["", f"## {r.command} {r.target}: {'PASS' if r.ok else 'FAIL'}", ""]
        lines += [f"- {'PASS' if ok else 'FAIL'} `{n}` {d}" for ok, n, d in r.checks]
        lines += [f"- note: {n}" for n in r.notes]
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return p


def main(root: Path) -> None:
    ap = argparse.ArgumentParser(prog="mt.py", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["targets", "build", "smoke", "test", "client", "mp", "all"])
    ap.add_argument("targets", nargs="?")
    ap.add_argument("--build", action="store_true")
    ap.add_argument("-j", type=int, default=2)
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--scene")
    ap.add_argument("--case", default="both")
    ap.add_argument("--timeout", type=float)
    ap.add_argument("--visible", action="store_true", help="show the client window (default: headless)")
    a = ap.parse_intermixed_args()  # options may come before the targets: `all --build 1.21.1-*`
    if a.visible:
        os.environ["MODTEST_VISIBLE"] = "1"
    proj = Project(root)
    sel = proj.select(a.targets)
    if a.command == "targets":
        for t in proj.targets:
            try:
                j = proj.jar(t).name
            except SystemExit:
                j = "(not built)"
            log(f"{t.name:32} {t.gradle_path:36} {j}")
        return
    if a.command == "build" or a.build:
        if not proj.build(sel):
            sys.exit("build failed")
        if a.command == "build":
            return
    results: list[Result] = []
    stages = {"smoke": ["smoke"], "test": ["test"], "client": ["client"], "mp": ["mp"],
              "all": ["smoke", "test", "client", "mp"]}[a.command]
    failed: set[str] = set()
    for stage in stages:
        todo = [t for t in sel if t.name not in failed]
        if stage in ("smoke", "test"):
            def one(it):
                i, t = it
                return server_run(proj, t, stage, "boot" if stage == "smoke" else "full", a, free_port())
            with ThreadPoolExecutor(max(1, a.j)) as ex:
                for r in ex.map(one, enumerate(todo)):
                    print_result(r)
                    results.append(r)
        elif stage == "client":
            for t in todo:
                r = client_run(proj, t, a)
                print_result(r)
                results.append(r)
        else:
            for t in todo:
                for case in a.case.split(","):
                    r = mp_run(proj, t, case, a, free_port())
                    why = flaky_join(r)
                    if why:  # a client that never got in (hung connect, timeout under load): try once more
                        kept = r.work.with_name(r.work.name + "-try1")
                        rmtree(kept)
                        try:
                            r.work.rename(kept)
                        except OSError:  # a file still held open: the retry reuses the work dir
                            kept = r.work
                        log(f"     retrying mp {case} {t.name}: {why} (first attempt: {kept})")
                        r = mp_run(proj, t, case, a, free_port())
                        r.notes.append(f"warning: the first attempt did not get in ({why}), work dir: {kept}")
                    print_result(r)
                    results.append(r)
        failed |= {r.target for r in results if not r.ok}
    rep = write_report(proj, results)
    ok = all(r.ok for r in results)
    log(f"{proj.mod_id}: {sum(r.ok for r in results)}/{len(results)} runs passed -> {rep}")
    sys.exit(0 if ok else 1)
