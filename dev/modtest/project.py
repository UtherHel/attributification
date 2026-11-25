"""The mod repository being tested: its Stonecutter targets, per-target properties, built jars and dev/modtest.toml."""
from __future__ import annotations

import fnmatch
import glob
import os
import re
import subprocess
import sys
import tomllib
from dataclasses import dataclass, field
from pathlib import Path

from .common import find_jdk, log, version_tuple

FAMILY = {"fabric": "fabric", "quilt": "fabric", "forge": "forge", "neoforge": "neoforge"}


@dataclass
class Target:
    mc: str
    loader: str
    branch: str = ""          # "" = the core mod, else a Stonecutter branch (an addon with its own jar)
    project: str = ""         # Stonecutter project name, e.g. "1.21.1-fabric"
    props: dict = field(default_factory=dict)

    @property
    def name(self) -> str:
        return f"{self.branch + ':' if self.branch else ''}{self.mc}-{self.loader}"

    @property
    def gradle_path(self) -> str:
        return f":{self.branch + ':' if self.branch else ''}{self.project}"

    @property
    def slug(self) -> str:
        return self.name.replace(":", "_")


def _flatten(d: dict, prefix: str = "") -> dict:
    out = {}
    for k, v in d.items():
        if isinstance(v, dict) and not re.match(r"^\d", k) and k not in ("fabric", "forge", "neoforge", "quilt"):
            out.update(_flatten(v, f"{prefix}{k}."))
        elif not isinstance(v, dict):
            out[f"{prefix}{k}"] = v
    return out


def _gradle_properties(path: Path) -> dict:
    out = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith(("#", "!")) and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


class Project:
    def __init__(self, root: Path):
        self.root = root
        self.cfg = tomllib.loads((root / "dev" / "modtest.toml").read_text(encoding="utf-8"))
        props_file = root / "stonecutter.properties.toml"
        self.raw = tomllib.loads(props_file.read_text(encoding="utf-8")) if props_file.is_file() else {}
        self.base = _flatten({k: v for k, v in self.raw.items() if not isinstance(v, dict) or not re.match(r"^\d", k)
                              and k not in ("fabric", "forge", "neoforge", "quilt")})
        if not props_file.is_file():  # non-Stonecutter layouts (Architectury): gradle.properties keys as they are
            self.base.update(_gradle_properties(root / "gradle.properties"))
        self.mod_id = self.cfg.get("mod_id") or self.base.get("mod.id")
        self.version = self.cfg.get("version") or self.base.get("mod.version") or self.base.get("mod_version", "")
        self.targets = self._targets()

    # ---------------------------------------------------------------- targets
    def _props(self, mc: str, loader: str) -> dict:
        p = dict(self.base)
        p.update(_flatten(self.raw.get(mc, {})))
        p.update(_flatten(self.raw.get(loader, {}).get(mc, {})))
        extra = self.cfg.get("props", {})  # modtest.toml [props]: flat keys, [props.<loader>] per loader
        p.update({k: v for k, v in extra.items() if not isinstance(v, dict)})
        p.update(extra.get(loader, {}))
        p["mc"], p["loader"] = mc, loader
        return p

    def _targets(self) -> list[Target]:
        if "targets" in self.cfg:  # explicit list (non-Stonecutter layouts such as Architectury)
            return [Target(t["mc"], t["loader"], t.get("branch", ""), t.get("project", ""), self._props(t["mc"], t["loader"]))
                    for t in self.cfg["targets"]]
        text = (self.root / "settings.gradle.kts").read_text(encoding="utf-8")
        text = re.sub(r"//[^\n]*", "", text)
        out, branch, depth, branch_depth = [], "", 0, -1
        for line in text.splitlines():
            m = re.search(r'branch\("([^"]+)"\)\s*\{', line)
            if m:
                branch, branch_depth = m.group(1), depth
            for m in re.finditer(r'\bmatch\("([^"]+)"((?:\s*,\s*"[a-z]+")+)', line):
                for loader in re.findall(r'"([a-z]+)"', m.group(2)):
                    out.append(Target(m.group(1), loader, branch, f"{m.group(1)}-{loader}"))
            for m in re.finditer(r'\bvariant\("([^"]+)",\s*"([a-z]+)",\s*"([^"]+)"\)', line):
                out.append(Target(m.group(3), m.group(2), branch, f"{m.group(1)}-{m.group(2)}"))
            depth += line.count("{") - line.count("}")
            if branch and depth <= branch_depth:
                branch, branch_depth = "", -1
        for t in out:
            t.props = self._props(t.mc, t.loader)
        return out

    def select(self, spec: str | None) -> list[Target]:
        """`spec`: comma list of target names or globs ("1.21.1-*", "*:1.20.1-forge"); default = dev/modtest.toml
        `default_targets`, else every core target."""
        spec = spec or ",".join(self.cfg.get("default_targets", [])) or None
        if not spec:
            return [t for t in self.targets if not t.branch]
        pats = [s.strip() for s in spec.split(",") if s.strip()]
        sel = [t for t in self.targets if any(fnmatch.fnmatch(t.name, p) for p in pats)]
        if not sel:
            sys.exit(f"no target matches {spec!r}; known: {', '.join(t.name for t in self.targets)}")
        return sel

    def core_of(self, t: Target) -> Target | None:
        return next((c for c in self.targets if not c.branch and c.mc == t.mc and c.loader == t.loader), None)

    # ---------------------------------------------------------------- jars
    def jar(self, t: Target) -> Path:
        """The jar of the current mod version for `t` (build dirs keep older builds, which must never be tested)."""
        pattern = self.cfg.get("jar", {}).get(t.name) or self.cfg.get("jar_pattern")
        if pattern:
            pattern = self.expand(pattern, t)
            hits = sorted(Path(p) for p in glob.glob(str(self.root / pattern)))
        else:
            libs = self.root / (t.branch or "") / "versions" / t.project / "build" / "libs"
            hits = sorted(libs.glob("*.jar"))
        version = t.props.get("mod.version", self.version)
        hits = [h for h in hits if not re.search(r"-(sources|javadoc|dev|dev-shadow|all|slim)\.jar$", h.name)
                and (not version or version in h.name)]
        if not hits:
            sys.exit(f"{t.name}: no built jar for version {version}; run `python dev/mt.py build {t.name}` first")
        return max(hits, key=lambda p: p.stat().st_mtime)

    def expand(self, s: str, t: Target) -> str:
        """`${prop}` -> the target's property; `${prop:-default}` when the property may be missing."""
        def sub(m):
            key, default = m.group(1), m.group(2)
            if key not in t.props:
                if default is not None:
                    return default
                raise KeyError(f"{t.name}: property {key} is not set (stonecutter.properties.toml)")
            return str(t.props[key])
        return re.sub(r"\$\{([^}:]+)(?::-([^}]*))?\}", sub, s)

    def build(self, targets: list[Target]) -> bool:
        tasks = []
        for t in targets:
            tasks.append(f"{t.gradle_path}:build" if t.project else self.cfg["build_task"][t.name])
            core = self.core_of(t) if t.branch else None
            if core:
                tasks.append(f"{core.gradle_path}:build")
        java = find_jdk(int(self.cfg.get("gradle_jdk", 25)))
        env = dict(os.environ, JAVA_HOME=str(java.parent.parent))
        cmd = [str(self.root / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--console=plain", "--no-daemon",
               *dict.fromkeys(tasks), "-x", "test", *self.cfg.get("gradle_args", [])]
        log("build:", " ".join(cmd[1:]))
        r = subprocess.run(cmd, cwd=self.root, env=env)
        return r.returncode == 0
