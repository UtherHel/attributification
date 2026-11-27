# dev/ — testing the mod in real Minecraft

Development tools modelled on ProfetGit's `dev/`, rewritten for Windows and these projects. The mod is not tested in
Gradle's dev environment but the way a player installs it: the built jar goes onto an official Fabric, Forge or
NeoForge server or client of the right version, together with the dependencies from `dev/modtest.toml`. Inside the
mod a self-test (`selftest/`) checks the mechanics and records PASS/FAIL. The script reads the results and the logs
and prints a summary.

## Commands

```
python dev/mt.py targets                 Stonecutter targets and whether the current version's jar is built
python dev/mt.py build   [targets]       gradlew build (Gradle on JDK 25, tests skipped)
python dev/mt.py smoke   [targets]       server: the mod loaded, every mixin applied, no errors in the log
python dev/mt.py test    [targets]       server + the mod's server scenes (no players)
python dev/mt.py client  [targets]       client: title screen, client mixins, scenes without a world
python dev/mt.py mp      [targets]       server + client over the network: scenes with a player on both sides
python dev/mt.py all     [targets]       everything in turn; a target that fails a stage goes no further
```

Targets are names or patterns, comma separated: `1.21.1-fabric`, `1.21.1-*`, `addon-parcool:*`. Without an argument
the `default_targets` of `dev/modtest.toml` are used, otherwise every target of the main mod. Options: `--build`
(build first), `-j N` (parallel servers), `--keep` (keep the work folders of passed runs), `--scene a,b` (only these
scenes), `--case both,client_only,server_only` (for `mp`), `--timeout S`, `--visible` (show the client window).
The exit code is 0 only if everything passed.

The result of every run is printed to the console and written to `dev/.work/report.md`. The work folders
`dev/.work/<command>/<target>` are deleted after a passed run and kept after a failure: they hold `server/server.log`,
`game/client.log`, `out/server.json`, `out/client.json` (and `out/shots/*.png` with `--visible`).

By default the client runs without a window: LWJGL is replaced with a copy patched by the
[HeadlessMc](https://github.com/headlesshq/headlessmc) transformer (MIT; the same as mc-runtime-test does in CI), and
every GLFW/OpenGL/OpenAL call becomes a no-op. The game runs fully — ticks, screens, network, mixins — but draws
nothing and takes no focus. The patched jars are cached in `%LOCALAPPDATA%\utherhel-modtest\hmc`. `--visible` (or
`MODTEST_VISIBLE=1`) starts the normal client with a window, for screenshots (`ClientSelfTest.shot`) and a look by eye.

## Always checked

| Check | Where | Meaning |
|---|---|---|
| `boot/mod_loaded` | server, client | the loader sees the mod; the detail names the loader, the Minecraft and the mod version |
| `boot/mixin_audit` | server, client | `MixinEnvironment.audit()`: every mixin of this side applied right away, not on the first use of its class |
| `boot/loaded_<id>` | server, client | an addon or the expected integrations are loaded (`expect_loaded`) |
| `server/log`, `client/log` | both | no crashes, mixin or mod loading errors, no ERROR lines; environment noise is filtered (`log_noise`) |
| `server/join` | `mp` | the client joined the server |
| `server/disconnect` | `mp` | the player was not kicked before the end of the session |
| `server/exit` | server | the JVM exited by itself after `stop`; otherwise FAIL with the names of the live non-daemon threads (`server/threads.txt`) |

## The mod's scenes

The scenes live in the mod: `src/main/java/<package>/selftest/Scenarios.java` (server) and `ClientScenarios.java` (client).

```java
static void server(Script<MinecraftServer> s) {
    if (s.scene("rules")) {
        s.run(srv -> TestCompat.command(srv, "gamerule doDaylightCycle false"))
            .waitTicks(20)
            .check("rules_loaded", srv -> rules().isEmpty() ? "no rules" : null)       // null = PASS
            .eventually("applied", 100, srv -> applied() ? null : "not applied yet");  // waits up to 100 ticks
    }
}
```

`Scenarios.server` — the server without players (`test`), `Scenarios.player` — once a client joined (`mp`, the player
is `SelfTest.player(srv)`), `ClientScenarios.title` — the client from the title screen without a world (`client`),
`ClientScenarios.client` — the client in the server's world (`mp`). `ClientSelfTest.shot(mc, "name")` saves a
screenshot (only with `--visible`; without a window nothing is drawn and the call does nothing).
A step that throws fails its scene; the other scenes go on.

The self-test sleeps unless `-D<modid>.selftest=<folder>` is set: in normal play it costs one boolean check per tick.
Two mixins hook it into the game: `SelfTestServerMixin` (server tick) and `client/SelfTestClientMixin` (client tick).

## dev/modtest.toml

| Key | Value |
|---|---|
| `[runtime]` | mods the mod does not start without: `maven:group:artifact:version`, `curse:slug-id:fileId`, `modrinth:project:version` (`modrinth:project:*` — the newest version for the target's loader and Minecraft version), `file:path`. The key is a loader, a target or a pattern; `${deps.x}` comes from `stonecutter.properties.toml`, `${x:-default}` with a value for when the key is missing |
| `[runtime_server]`, `[runtime_client]` | the same for one side only |
| `[files_server."<target>"]` | files put into the server folder before the first start, e.g. a data pack in `world/datapacks/` or a config in `config/`; `${...}` are substituted the same way |
| `[files_client."<target>"]` | the same for the client folder (`client` and `mp`), e.g. a config that must match the server's |
| `known_exit_hangs` | regexes of thread names: if the server did not exit after `stop` only because of them, `server/exit` is a PASS with a warning (a known, documented problem) |
| `server_commands` | console commands right after the start |
| `default_targets` | the default targets |
| `expect_loaded` | per loader: ids of mods that must load together with the mod |
| `branch_mod_ids` | the mod id of every addon (Stonecutter branch) |
| `log_noise` | regexes of harmless log lines |
| `known_failures` | regexes of names of checks that fail because of a known, not yet fixed bug (a list, or a table keyed like `[runtime]`): such a failure counts as passed and prints a `known issue` warning; if the check passes after all, a "passes now" warning says the entry can go |

## Where things are

- Server and client installs, libraries, assets, downloaded mods: `%LOCALAPPDATA%\utherhel-modtest`
  (`MODTEST_HOME`). Assets are copied from `%APPDATA%\.minecraft` when they are already there.
- JDK 17/21/25 are looked up in `C:\Program Files\Eclipse Adoptium` and the folders next to it; a path can be set with
  `MODTEST_JDK_<version>`.
- At most 2 servers and 1 client run at the same time on the whole machine (`MODTEST_SERVER_SLOTS`,
  `MODTEST_CLIENT_SLOTS`), even from different repositories. The waiting ones queue in order of arrival.
- mp: if the client hangs while connecting (90 s on the Connecting screen) or times out, the run is repeated once
  (the first attempt stays in `<work>-try1`, the client's thread dump in `out/client-threads.txt`). A refusal with a
  reason (wrong mod, registry mismatch, restart screen) is a result of the mod and is not repeated.
- `dev/modtest/` is a shared copy of the harness. The master is edited in `C:\MCProjects\tools\modtest`, then
  `python sync.py`. The self-test engine (`Report`, `Script`, `SelfTest`, `ClientSelfTest`, `ModTestHook`) and both
  mixins are synced the same way. `TestCompat`, `ClientCompat`, `Scenarios`, `ClientScenarios` belong to the mod and
  are not overwritten.
