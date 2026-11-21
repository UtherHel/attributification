package dev.utherhel.attributification;

import dev.utherhel.attributification.config.AttributificationConfig;
import dev.utherhel.attributification.config.DefaultAttributificationConfig;
import java.util.ArrayList;
import java.util.List;

public final class Attributifaction {

    public static final String MOD_ID = "attributification";
    private static AttributeUnifier attributeUnifier;
    private static AttributificationConfig config;
    private static boolean isFabric = false;
    private static long attributeUnifierVersion = 0; // Incremented on each reload

    // List of cache clear callbacks to call when config reloads
    private static final List<Runnable> cacheClearCallbacks = new ArrayList<>();

    private Attributifaction() {
        // Private constructor to hide implicit one
    }

    /**
     * Initializes the mod with the platform-specific config.
     * Called by platform entry points.
     *
     * @param platformConfig the platform-specific config implementation
     */
    public static void init(AttributificationConfig platformConfig) {
        // Detect platform
        //? if fabric {
        isFabric = true;
        //?} else {
        /*isFabric = false;
        *///?}
        AttributificationLogger.info(
            "Running on platform: " + (isFabric ? "Fabric" : "NeoForge")
        );

        // Use the provided platform config
        config = platformConfig;

        attributeUnifier = new AttributeUnifier(config);
        attributeUnifierVersion++; // Increment version to invalidate all caches

        // Register cache clear callback
        registerCacheClearCallback(() -> {
            AttributificationLogger.info(
                "AttributeUnifier version incremented, caches will be invalidated"
            );
        });

        AttributificationLogger.info(
            "Loaded " + config.getMappings().size() + " attribute mappings"
        );
    }

    /**
     * Initializes the mod with default config (fallback).
     * Platform entry points should prefer {@link #init(AttributificationConfig)}.
     */
    public static void init() {
        init(new DefaultAttributificationConfig());
    }

    /**
     * Registers a callback to clear cache when config is reloaded.
     * Called from mixins to register their cache clearing methods.
     */
    public static void registerCacheClearCallback(Runnable callback) {
        cacheClearCallbacks.add(callback);
    }

    /**
     * Reloads the AttributeUnifier with current config.
     * Called when datapacks or config are reloaded.
     */
    public static void reloadAttributeUnifier() {
        // Clear all registered caches
        for (Runnable callback : cacheClearCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                AttributificationLogger.warn(
                    "Failed to clear cache: " + e.getMessage()
                );
            }
        }

        // Clear AttributeUtils caches
        dev.utherhel.attributification.util.AttributeUtils.clearCaches();

        // Recreate unifier with new config
        attributeUnifier = new AttributeUnifier(config);
        attributeUnifierVersion++; // Increment version to invalidate all caches

        AttributificationLogger.info(
            "Reloaded " + config.getMappings().size() + " attribute mappings"
        );
        AttributificationLogger.warn(
            "NOTE: Static mappings require restart to take effect"
        );
        AttributificationLogger.info(
            "Runtime updates: dynamic mappings, equivalence, operation conversions"
        );
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
