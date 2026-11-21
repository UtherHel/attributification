package dev.utherhel.attributification.config;

import dev.utherhel.attributification.attribute.CompiledMapping;
import dev.utherhel.attributification.attribute.MappingLoader;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration interface for Attributification.
 * <p>
 * Platform-specific implementations handle persistence:
 * <ul>
 *   <li><b>Fabric</b>: reads {@code config/attributification.json}</li>
 *   <li><b>NeoForge</b>: uses {@code ModConfigSpec} (TOML via NeoForge API)</li>
 * </ul>
 * Only debug settings (logLevel) are stored in config;
 * attribute mappings are loaded from datapacks via {@link MappingLoader}
 * (including the mod's own built-in defaults datapack).
 * <p>
 * Datapack config (data/attributification/config.json) can override
 * the platform config's log level.
 */
public interface AttributificationConfig {
    /**
     * Returns the current debug log level.
     * <p>
     * If a datapack config override is present, it takes precedence over
     * the platform config value.
     */
    default LogLevel getLogLevel() {
        // Check datapack config override first
        if (DatapackConfigLoader.hasOverride()) {
            return DatapackConfigLoader.getLogLevelOverride();
        }
        return getPlatformLogLevel();
    }

    /**
     * Returns the platform-specific log level (before datapack override).
     * Implemented by Fabric/NeoForge config classes.
     */
    LogLevel getPlatformLogLevel();

    /**
     * Checks if debug logging (ALL) is enabled.
     */
    default boolean isDebugLoggingEnabled() {
        return getLogLevel() == LogLevel.ALL;
    }

    /**
     * Gets all attribute mappings for the unifier.
     * <p>
     * Mappings come exclusively from datapacks via {@link MappingLoader};
     * the mod's built-in defaults ship as a datapack too. Returns an empty
     * list before the first resource reload completes.
     */
    default List<AttributeMappingEntry> getMappings() {
        return MappingLoader.getMappings()
            .stream()
            .map(CompiledMapping::toEntry)
            .collect(Collectors.toList());
    }
}
