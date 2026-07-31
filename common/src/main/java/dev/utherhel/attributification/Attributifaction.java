package dev.utherhel.attributification;

import dev.architectury.platform.Platform;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.DefaultAttributificationConfig;
import dev.utherhel.attributification.config.LogLevel;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.resource.ResourceType;

import java.util.ArrayList;
import java.util.List;

public final class Attributifaction {
    public static final String MOD_ID = "attributification";
    private static DefaultAttributificationConfig config;
    private static AttributeUnifier attributeUnifier;
    private static boolean isFabric = false;
    private static long attributeUnifierVersion = 0;
    private static final List<Runnable> cacheClearCallbacks = new ArrayList<>();

    private Attributifaction() {}

    public static void init() {
        init(null);
    }

    public static void init(LogLevel logLevel) {
        isFabric = Platform.isFabric();
        AttributificationLogger.info("Running on: " + (isFabric ? "Fabric" : "Forge"));

        config = new DefaultAttributificationConfig();
        if (logLevel != null) {
            config.setLogLevel(logLevel);
        }

        ReloadListenerRegistry.register(ResourceType.SERVER_DATA, new MappingLoader());

        attributeUnifier = new AttributeUnifier(config);
        attributeUnifierVersion++;

        AttributificationLogger.info("Attributification initialized. Will load mappings on first datapack reload.");
    }

    public static void setMappings(List<AttributeMappingEntry> mappings) {
        config.setMappings(mappings);
        if (attributeUnifier != null) {
            attributeUnifier.rebuildCache();
        }
    }

    public static void registerCacheClearCallback(Runnable callback) {
        cacheClearCallbacks.add(callback);
    }

    public static void reloadAttributeUnifier() {
        for (Runnable callback : cacheClearCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                AttributificationLogger.warn("Failed to clear cache: " + e.getMessage());
            }
        }
        AttributeUtils.clearCaches();

        attributeUnifier = new AttributeUnifier(config);
        attributeUnifierVersion++;

        AttributificationLogger.info("Reloaded " + config.getMappings().size() + " attribute mappings");
    }

    public static long getAttributeUnifierVersion() {
        return attributeUnifierVersion;
    }

    public static AttributeUnifier getAttributeUnifier() {
        return attributeUnifier;
    }

    public static DefaultAttributificationConfig getConfig() {
        return config;
    }

    public static boolean isFabric() {
        return isFabric;
    }
}
