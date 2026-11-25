"""Runtime mods a test needs besides the mod itself (Fabric API, config libraries, integrations).

dev/modtest.toml lists them per target:

    [runtime]
    fabric = ["maven:net.fabricmc.fabric-api:fabric-api:${deps.fabric_api}"]
    "1.20.1-forge" = ["curse:curios-309927:6418456", "modrinth:kotlin-for-forge:4.11.0"]

Keys are a loader ("fabric"), a target name ("1.20.1-forge") or a glob ("1.21.*-neoforge"); every matching key adds its
list. ${...} takes values from stonecutter.properties.toml for that target. Kinds:

    maven:<group>:<artifact>:<version>[:<classifier>]   Gradle cache first, then the repositories below
    curse:<slug>-<projectId>:<fileId>                    the CurseForge maven (www.cursemaven.com)
    modrinth:<project>:<version number or id>            the Modrinth API (primary file of that version)
    modrinth:<project>:*                                 its newest version for the target's loader and Minecraft
    file:<path>                                          a jar on disk (relative to the repo root)

The jars are the published (production) artifacts, so they are what a player installs.
"""
from __future__ import annotations

import fnmatch
import urllib.parse
from pathlib import Path

from .common import HOME, fetch, fetch_json, log
from .project import Project, Target

GRADLE_CACHE = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
REPOS = [
    "https://repo.maven.apache.org/maven2",
    "https://maven.fabricmc.net",
    "https://maven.neoforged.net/releases",
    "https://maven.minecraftforge.net",
    "https://api.modrinth.com/maven",
    "https://www.cursemaven.com",
    "https://maven.architectury.dev",
    "https://maven.fzzyhmstrs.me",
    "https://maven.terraformersmc.com/releases",
    "https://maven.shedaniel.me",
    "https://thedarkcolour.github.io/KotlinForForge",
    "https://maven.ladysnake.org/releases",
]


def _from_gradle(group: str, artifact: str, version: str, classifier: str) -> Path | None:
    d = GRADLE_CACHE / group / artifact / version
    name = f"{artifact}-{version}{'-' + classifier if classifier else ''}.jar"
    hits = sorted(d.glob(f"*/{name}")) if d.is_dir() else []
    return hits[0] if hits else None


def maven(coord: str, repos: list[str] | None = None) -> Path:
    parts = coord.split(":")
    group, artifact, version = parts[:3]
    classifier = parts[3] if len(parts) > 3 else ""
    hit = _from_gradle(group, artifact, version, classifier)
    if hit:
        return hit
    name = f"{artifact}-{version}{'-' + classifier if classifier else ''}.jar"
    dest = HOME / "mods" / group / name
    if dest.is_file():
        return dest
    rel = f"{group.replace('.', '/')}/{artifact}/{urllib.parse.quote(version)}/{urllib.parse.quote(name)}"
    errors = []
    for repo in repos or REPOS:
        try:
            return fetch(f"{repo}/{rel}", dest, tries=1)
        except OSError as e:
            errors.append(str(e))
    raise OSError(f"{coord}: not in the Gradle cache and not found in {len(errors)} repositories")


def modrinth(project: str, version: str, t: Target | None = None) -> Path:
    vs = fetch_json(f"https://api.modrinth.com/v2/project/{project}/version", HOME / "meta" / f"modrinth-{project}.json")
    if version == "*" and t is not None:
        fits = [v for v in vs if t.loader in v["loaders"] and t.mc in v["game_versions"]]
        vs, version = sorted(fits, key=lambda v: v["date_published"], reverse=True)[:1], "*"
    if version != "*" and t is not None:
        # a version number is often shared by the Fabric and Forge files of one release: prefer the target's loader
        vs = sorted(vs, key=lambda v: t.loader not in v["loaders"])
    for v in vs:
        if version in ("*", v["id"], v["version_number"]):
            f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
            return fetch(f["url"], HOME / "mods" / "modrinth" / project / f["filename"], f["hashes"].get("sha1"))
    raise OSError(f"modrinth {project}: no version {version}" + (f" for {t.loader} {t.mc}" if t else ""))


def resolve(spec: str, root: Path, t: Target | None = None) -> Path:
    kind, _, rest = spec.partition(":")
    if kind == "maven":
        return maven(rest)
    if kind == "curse":
        slug, file_id = rest.rsplit(":", 1)
        return maven(f"curse.maven:{slug}:{file_id}", ["https://www.cursemaven.com"])
    if kind == "modrinth":
        project, version = rest.split(":", 1)
        return modrinth(project, version, t)
    if kind == "file":
        p = (root / rest).resolve()
        if not p.is_file():
            raise OSError(f"{spec}: no such file")
        return p
    raise ValueError(f"unknown runtime mod kind in {spec!r}")


def specs(proj: Project, t: Target, section: str = "runtime") -> list[str]:
    table = proj.cfg.get(section, {})
    out: list[str] = []
    for key, items in table.items():
        if key in (t.loader, t.name, f"{t.mc}-{t.loader}") or fnmatch.fnmatch(t.name, key) \
                or fnmatch.fnmatch(f"{t.mc}-{t.loader}", key):
            out += [proj.expand(s, t) for s in items]
    return list(dict.fromkeys(out))


def runtime_mods(proj: Project, t: Target, side: str) -> list[Path]:
    """The mod jar (+ the core jar for an addon) and every runtime mod for `side` ("server" or "client")."""
    jars = [proj.jar(t)]
    if t.branch:
        core = proj.core_of(t)
        if core:
            jars.append(proj.jar(core))
    for s in specs(proj, t) + specs(proj, t, f"runtime_{side}"):
        try:
            jars.append(resolve(s, proj.root, t))
        except OSError as e:
            log(f"     {t.name}: runtime mod {s}: {e}")
            raise
    return list(dict.fromkeys(jars))
