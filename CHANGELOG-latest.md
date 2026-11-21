- Minecraft 26.1, 26.2 and 26.3 support (Fabric and NeoForge)
- One codebase for all Minecraft versions and loaders (Stonecutter); 1.20.1 now uses the same
  mapping engine as 1.21.1 (wildcards, excludes)
- Architectury API is no longer required
- Fabric API 0.116.18 (1.21.1) / 0.92.12 (1.20.1), Fabric Loader 0.20.1
- Mapping editor: the server accepts editor requests only from operators (permission level 2)
- Fix: Full Replacement removed the source attribute's item modifiers instead of moving them to the
  target, so neither attribute changed (#2)
- Fix (Fabric): equipment used the unconverted item modifiers; only tooltips were converted
- Fix: item modifiers were converted twice (value divided by the conversion equivalence two times)
- Fix: the target attribute's own modifiers were divided by the conversion equivalence as well; only
  modifiers that reach the target through the source attribute are converted now
- Fix: `debug.logLevel` was ignored, so every redirected attribute lookup and read was logged at
  INFO; info lines are written again only with `logLevel = ALL`
- Removed unused modifier tracking that grew with every modifier added to a target attribute
