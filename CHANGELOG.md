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
