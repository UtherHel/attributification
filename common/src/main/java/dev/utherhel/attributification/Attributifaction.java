package dev.utherhel.attributification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import dev.utherhel.attributification.config.AttributificationConfig;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Attributifaction {
    public static final String MOD_ID = "attributification";
    private static AttributeUnifier attributeUnifier;
    private static AttributificationConfig config;
    private static boolean isFabric = false;
    private static long attributeUnifierVersion = 0; // Incremented on each reload
    
    // List of cache clear callbacks to call when config reloads
    private static final List<Runnable> cacheClearCallbacks = new ArrayList<>();
    
    private Attributifaction() {
        // Private constructor to hide implicit public one
    }
    
    public static void init() {
        // Detect platform
        isFabric = Platform.isFabric();
        AttributificationLogger.info("Running on platform: " + (isFabric ? "Fabric" : "Forge"));
        
        // Migrate from old JSON config to new TOML config if needed
        AttributificationConfig migratedConfig = migrateFromOldConfig();
        
        // Register and load main config with fconfig
        // RegisterType.BOTH = GUI on client + server sync (all players see same unification)
        if (migratedConfig != null) {
            // Use migrated config
            config = migratedConfig;
        } else {
            // Load normally (no migration needed)
            Supplier<AttributificationConfig> configSupplier = AttributificationConfig::new;
            config = ConfigApi.registerAndLoadConfig(configSupplier, RegisterType.BOTH);
        }
        
        attributeUnifier = new AttributeUnifier(config);
        attributeUnifierVersion++; // Increment version to invalidate all caches
        
        // Register cache clear callback
        registerCacheClearCallback(() -> {
            AttributificationLogger.info("AttributeUnifier version incremented, caches will be invalidated");
        });
        
        AttributificationLogger.info("Loaded " + config.getMappings().size() + " attribute mappings");
    }
    
    /**
     * Migrates from old JSON config (attribute_mappings.json) to new TOML config (attributification.toml).
     * This method is called before loading the new config to ensure user settings are preserved.
     * 
     * OPTIMIZED: Parses JSON directly without using deprecated classes!
     * 
     * @return Migrated config if migration was performed, null otherwise
     */
    private static AttributificationConfig migrateFromOldConfig() {
        Path oldConfigPath = Platform.getConfigFolder().resolve("attributification/attribute_mappings.json");
        Path newConfigPath = Platform.getConfigFolder().resolve("attributification/attributification.toml");
        
        // Only migrate if old config exists and new config doesn't exist yet
        if (Files.exists(oldConfigPath) && !Files.exists(newConfigPath)) {
            AttributificationLogger.info("Found old JSON config, migrating to new TOML format...");
            
            try {
                // Load old JSON config manually (without deprecated classes)
                String json = Files.readString(oldConfigPath);
                Gson gson = new GsonBuilder().create();
                JsonObject root = gson.fromJson(json, JsonObject.class);
                
                // Create new config with migrated mappings
                AttributificationConfig newConfig = new AttributificationConfig();
                newConfig.getMappings().clear();
                
                // Parse mappings array
                JsonArray mappingsArray = null;
                
                // Try to parse as JsonObject first (new format with "mappings" field)
                if (root != null && root.isJsonObject() && root.has("mappings")) {
                    JsonElement mappingsElement = root.get("mappings");
                    if (mappingsElement.isJsonArray()) {
                        mappingsArray = mappingsElement.getAsJsonArray();
                    }
                } else {
                    // Fallback: try to parse entire JSON as array (very old format)
                    try {
                        JsonElement rootElement = gson.fromJson(json, JsonElement.class);
                        if (rootElement.isJsonArray()) {
                            mappingsArray = rootElement.getAsJsonArray();
                        }
                    } catch (Exception e) {
                        AttributificationLogger.warn("Failed to parse old config as array: " + e.getMessage());
                    }
                }
                
                if (mappingsArray != null) {
                    for (JsonElement element : mappingsArray) {
                        JsonObject mapping = element.getAsJsonObject();
                        
                        String sourceAttribute = mapping.has("sourceAttribute") ? mapping.get("sourceAttribute").getAsString() : "";
                        String targetAttribute = mapping.has("targetAttribute") ? mapping.get("targetAttribute").getAsString() : "";
                        double equivalence = mapping.has("equivalence") ? mapping.get("equivalence").getAsDouble() : 1.0;
                        double conversionEquivalence = mapping.has("conversionEquivalence") ? mapping.get("conversionEquivalence").getAsDouble() : 0.0;
                        
                        // Convert bidirectional to unidirectional (inverted logic)
                        // bidirectional=true → unidirectional=false
                        // bidirectional=false → unidirectional=true
                        boolean bidirectional = mapping.has("bidirectional") && mapping.get("bidirectional").getAsBoolean();
                        boolean unidirectional = !bidirectional;
                        
                        boolean onlyVisualChange = mapping.has("onlyVisualChange") && mapping.get("onlyVisualChange").getAsBoolean();
                        boolean fullReplacement = mapping.has("fullReplacement") && mapping.get("fullReplacement").getAsBoolean();
                        double subtractValue = mapping.has("subtractValue") ? mapping.get("subtractValue").getAsDouble() : 0.0;
                        String operationConversion = mapping.has("operationConversion") ? mapping.get("operationConversion").getAsString() : "DEFAULT";
                        
                        AttributeMappingEntry newMapping = new AttributeMappingEntry(
                            sourceAttribute,
                            targetAttribute,
                            equivalence,
                            conversionEquivalence,
                            unidirectional,
                            onlyVisualChange,
                            fullReplacement,
                            subtractValue,
                            operationConversion
                        );
                        
                        newConfig.getMappings().add(newMapping);
                    }
                }
                
                // Register new config (this will save it to TOML)
                Supplier<AttributificationConfig> supplier = () -> newConfig;
                AttributificationConfig registeredConfig = ConfigApi.registerAndLoadConfig(supplier, RegisterType.BOTH);
                
                // Rename old config to .old for backup
                Path backupPath = oldConfigPath.resolveSibling("attribute_mappings.json.old");
                Files.move(oldConfigPath, backupPath);
                
                AttributificationLogger.info("Successfully migrated " + newConfig.getMappings().size() + " mappings to new TOML format");
                AttributificationLogger.info("Old config backed up to: " + backupPath);
                
                return registeredConfig;
            } catch (IOException e) {
                AttributificationLogger.error("Failed to migrate old config: " + e.getMessage(), e);
                AttributificationLogger.error("Will use default configuration instead");
            }
        }
        
        return null;
    }
    
    /**
     * Registers a callback to clear cache when config is reloaded.
     * Called from mixins to register their cache clearing methods.
     * 
     * This allows centralized cache management without tight coupling.
     */
    public static void registerCacheClearCallback(Runnable callback) {
        cacheClearCallbacks.add(callback);
    }
    
    /**
     * Reloads the AttributeUnifier with current config.
     * Called automatically from AttributificationConfig when config is saved.
     */
    public static void reloadAttributeUnifier() {
        // Clear all registered caches
        for (Runnable callback : cacheClearCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                AttributificationLogger.warn("Failed to clear cache: " + e.getMessage());
            }
        }
        
        // Clear AttributeUtils caches
        dev.utherhel.attributification.util.AttributeUtils.clearCaches();
        
        // Recreate unifier with new config
        attributeUnifier = new AttributeUnifier(config);
        attributeUnifierVersion++; // Increment version to invalidate all caches
        
        AttributificationLogger.info("Reloaded " + config.getMappings().size() + " attribute mappings");
        AttributificationLogger.warn("NOTE: Static mappings require restart to take effect");
        AttributificationLogger.info("Runtime updates: dynamic mappings, equivalence, operation conversions");
    }
    
    public static long getAttributeUnifierVersion() {
        return attributeUnifierVersion;
    }
    
    public static AttributeUnifier getAttributeUnifier() {
        return attributeUnifier;
    }
    
    public static AttributificationConfig getConfig() {
        return config;
    }
    
    public static boolean isFabric() {
        return isFabric;
    }
}
