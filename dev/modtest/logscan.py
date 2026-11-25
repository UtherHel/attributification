"""Log checks: hard failures (crash, mixin, mod loading), errors, and warnings that name the mod.

Known environment noise (offline mode, missing audio device, telemetry, realms) is filtered here; a mod adds its own
known-harmless lines with `log_noise = ["regex", ...]` in dev/modtest.toml.
"""
from __future__ import annotations

import re

FATAL = re.compile(r"Mixin apply (for mod \S+ )?failed|MixinApplyError|MixinTransformerError|InvalidInjectionException|"
                   r"InvalidMixinException|ModLoadingException|LoadingFailedException|Missing or unsupported mandatory|"
                   r"Crash report saved|---- Minecraft Crash Report ----|Failed to start the minecraft server|"
                   r"Incompatible mods? found|ModResolutionException|NoClassDefFoundError|ClassNotFoundException|"
                   r"NoSuchMethodError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError|"
                   r"Mod \S+ requires|Unsupported class file major version|"
                   r"Encountered an unexpected exception|Exception in server tick loop")
ERROR = re.compile(r"/(ERROR|FATAL)\]|\[(ERROR|FATAL)\]|^\S*Exception\b|^Caused by:")
WARN = re.compile(r"/WARN\]|\[WARN\]")
NOISE = [
    r"OFFLINE/INSECURE|online-mode|authenticate usernames|hackers to connect|Server is running in offline mode",
    r"Failed to fetch user properties|InvalidCredentials|YggdrasilUserApiService|authlib|Realms|realms",
    r"Failed to request yggdrasil public key|Yggdrasil Key Fetcher",
    r"Failed to request Minecraft Services public key|Minecraft Services Discovery",  # the 26.x wording of the above
    r"Narrator|libflite|OpenAL|Failed to open OpenAL|Error initializing sound|SoundEngine|[Ss]ound ?[Ss]ystem",
    r"telemetry|Telemetry|TelemetryEventInstance|Failed to send telemetry",
    r"Can't keep up! Is the server overloaded",
    r"Ambiguity between arguments",
    r"No key layers in MapLike",
    r"Unable to construct a game profile|Failed to verify username|Unable to retrieve profile key pair",
    r"Failed to verify authentication",  # the offline test account (1.20.1 client)
    r"\[Forge Version Check/|\[ne\.mi\.fm\.VersionChecker/\]",  # Forge's update check (network)
    r"Missing metadata in pack|Missing data pack|pack\.mcmeta",
    r"Tried to load unrecognized dimension",
    r"Skipping BlockEntity with id",
    r"Configured defaults file|default configs",
    r"Reference map '.*' for .* could not be read",
    r"Mixin config .* does not specify \"minVersion\"",
    r"Failed to retrieve profile key pair|Profile key pair",
    r"Received a Gl|GL debug|OpenGL debug",
    r"Unable to load model:|Missing textures in model|Missing texture references|Unable to resolve texture",
    r"Attempted to load class net/minecraft/client/.* for invalid dist DEDICATED_SERVER",
    r"\[ModTest\]",
    # Mixin's WARN for a target class that is not there: a mod's mixin into an optional mod that is not installed. A
    # required mixin of the tested mod that cannot apply fails boot/mixin_audit or logs at ERROR instead.
    r"/WARN\].*Error loading class: \S+ \(java\.lang\.ClassNotFoundException",
]


def scan(text: str, mod_id: str, extra_noise: list[str] = ()) -> tuple[list[str], list[str], list[str]]:
    """(fatal lines, error lines, mod warnings): each list holds trimmed log lines.

    Lines that do not start with "[" continue the previous log record (stack trace, "Caused by:"); they are dropped
    together with a noise record.
    """
    noise = [re.compile(n) for n in NOISE + list(extra_noise)]
    fatal, errors, warns = [], [], []
    mod = re.compile(re.escape(mod_id), re.I) if mod_id else None
    in_noise = False
    for line in text.splitlines():
        if not line.strip() or line.startswith("\tat ") or line.startswith("    at "):
            continue
        if not line.startswith("["):
            if in_noise:
                continue
        else:
            in_noise = False
        if any(n.search(line) for n in noise):
            in_noise = True
            continue
        short = line.strip()[:300]
        if FATAL.search(line):
            fatal.append(short)
        elif ERROR.search(line):
            errors.append(short)
        elif WARN.search(line) and mod and mod.search(line):
            warns.append(short)
    return list(dict.fromkeys(fatal)), list(dict.fromkeys(errors)), list(dict.fromkeys(warns))
