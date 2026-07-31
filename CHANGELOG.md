### 2.1.0
- Move attribute mappings to datapacks — reload with /reload, ship or override like any datapack
- Replace Fzzy Config with platform-native config (Fabric: JSON, Forge: ModConfigSpec)
- Add /attributification command with gui and reload subcommands
- Add default mappings as datapack (defaults.json) instead of Java code
- Add wildcard support, cycle detection, and chain collapsing in mapping compiler
- Add legacy Fzzy Config migration to datapack format
- Add Forge ItemAttributeModifierEvent handler for gameplay conversion

### 2.0.5.1
- Remove developer mode debug stick recipe
- Add fabric entrypoints to fabric.mod.json
- Update fabric.mod.json version

### 2.0.5
- Fix: NeoForge Server crashed at startup due to runtime-dist Cleanroom installer invoking client-only class ReferenceBeans
  - Solution: moved EntityAttributeModifierCallback into mixin-only Adapter, deleting separate interface
- Added gradle.properties with versioning

### 2.0.4
- Removed EntryPointMixinReference; replaced with simple @Mod EventBusSubscriber
- Added onEntityAttributeModifier callback calls
- EntityAttributeModifierCallback proxy for multi-loader compatibility
- Refactored `event/` folder structure to match 1.21.1 branch layout

### 2.0.3
- Fully removed obsolete code from 1.19.2 era
- Implemented persistent config via Fzzy Config (NeoForge + Fabric)
- Switched from Fzzy Config annotation model to `ConfigValue<T>` API
- Created typed config holder wrapper
- Expanded default mapping count from 5 to 38
- Updated `fabric.mod.json` and `mods.toml`

### 2.0.2
- YACL → Fzzy Config migration started
- Removed legacy ConfigScreenWrapper
- Removed obsolete networking classes
- Cleaned up mods.toml formatting

### 2.0.1
- Removed unused localization keys
- Deleted legacy GUI/network classes
- Cleaned up build.gradle
- Split Mixin configs into `attributification.mixins.json` and `attributification.neoforge.mixins.json`
- Removed `attributification-common.mixins.json`
- Added `fabric.mod.json` keys
