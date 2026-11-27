### Unreleased
- Minecraft 26.1, 26.2 and 26.3 support (Fabric and NeoForge)
- Minecraft 1.20.2 - 1.21.11 support (Fabric: every release from 1.20; NeoForge: 1.20.3 - 1.20.6,
  1.21.1 - 1.21.5 and 1.21.7 - 1.21.11)
- One codebase for all Minecraft versions and loaders (Stonecutter); 1.20.1 now uses the same
  mapping engine as 1.21.1 (wildcards, excludes)
- Architectury API is no longer required
- Fabric API 0.116.18 (1.21.1) / 0.92.12 (1.20.1), Fabric Loader 0.20.1
- Mapping editor: the server accepts editor requests only from operators (permission level 2)
- Clients use the server's mappings: the server sends them when a player joins and after every reload, so the HUD, the inventory and item tooltips show the same values as the server (before, a client of a dedicated server computed the unmapped values)
- Mistakes in mapping files are reported with the file and the mapping: an attribute ID with a typo, an unknown field (with a suggestion), an unknown `mode` or `operationConversion` (case and `_` no longer matter), a conversion that would divide by zero; such a mapping is skipped instead of half-working. Mappings for mods that are not installed are only counted, and one summary line tells how many mappings are active
- The default log level is WARNINGS, so these problems show; messages from attribute lookups that run every tick are logged once
- Large mapping files open and save in the editor (they travel compressed; before, a file of more than 32767 characters disconnected the editor's player); one too large even then is refused with a message instead
- Fix: rare crashes or wrong values in single player from a lookup cache shared by the server and the client thread
- Fix: Full Replacement removed the source attribute's item modifiers instead of moving them to the
  target, so neither attribute changed (#2)
- Fix (Fabric): equipment used the unconverted item modifiers; only tooltips were converted
- Fix: item modifiers were converted twice (value divided by the conversion equivalence two times)
- Fix: the target attribute's own modifiers were divided by the conversion equivalence as well; only
  modifiers that reach the target through the source attribute are converted now
- Fix: `debug.logLevel` was ignored, so every redirected attribute lookup and read was logged at
  INFO; info lines are written again only with `logLevel = ALL`
- Removed unused modifier tracking that grew with every modifier added to a target attribute

### 2.1.0
- Move attribute mappings to datapacks — reload with /reload, ship or override like any datapack
- Replace Fzzy Config with platform-native config (Fabric: JSON, NeoForge: TOML)
- Add /attributification gui for browsing and editing mapping files in-game

### 2.0.5.1
- Fix tooltip conversion for unidirectional mappings
- Add smart autocomplete for attribute IDs in config GUI
- Add Russian translations for the config GUI

### 2.0.5
- Add `subtractValue` field for mappings
- Add base value conversion defaults for several mods
- Fix config deserialization during migration

### 2.0.4
- Add Lodestone and Obscure API → Iron's Spellbooks default mappings
- Fix full replacement mode to properly hide modifiers in tooltips

### 2.0.3
- Add Ars Nouveau and Goety → Iron's Spellbooks default mappings
- Fix `conversionEquivalence` fallback to use `equivalence` when set to 0
