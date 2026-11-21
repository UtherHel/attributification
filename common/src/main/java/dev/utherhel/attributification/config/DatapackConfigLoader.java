package dev.utherhel.attributification.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utherhel.attributification.AttributificationLogger;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Loads optional base config from datapack resources at path
 * 'data/attributification/config.json' across all namespaces.
 * <p>
 * This provides a data-driven way to override platform config settings
 * on a per-world/server basis. Currently supports:
 * <ul>
 *   <li>{@code logLevel} - override the debug log level</li>
 * </ul>
 * <p>
 * If multiple datapacks define a config, the first one found is used
 * (ordered by datapack priority).
 */
public final class DatapackConfigLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String CONFIG_PATH = "attributification/config.json";

    // Loaded override values (null = no override)
    private static volatile LogLevel logLevelOverride = null;

    private DatapackConfigLoader() {}

    /**
     * Loads the base config from all datapacks.
     * Called during datapack reload ({@code /reload}).
     *
     * @param resourceManager the current resource manager
     */
    public static void load(ResourceManager resourceManager) {
        // Reset overrides
        logLevelOverride = null;

        // Find all config.json files in datapacks
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
            "attributification",
            id -> id.getPath().equals("attributification/config.json")
        );

        if (resources.isEmpty()) {
            AttributificationLogger.info(
                "No datapack config found, using platform config"
            );
            return;
        }

        // Use the first config found (highest priority datapack wins)
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try (
                Reader reader = new java.io.InputStreamReader(
                    entry.getValue().open(),
                    java.nio.charset.StandardCharsets.UTF_8
                )
            ) {
                JsonObject root = JsonParser.parseReader(
                    reader
                ).getAsJsonObject();

                if (root.has("logLevel")) {
                    String levelStr = root.get("logLevel").getAsString();
                    try {
                        LogLevel parsed = LogLevel.valueOf(
                            levelStr.toUpperCase()
                        );
                        logLevelOverride = parsed;
                        AttributificationLogger.info(
                            "Loaded logLevel '" +
                                parsed +
                                "' from datapack config: " +
                                id
                        );
                    } catch (IllegalArgumentException e) {
                        AttributificationLogger.warn(
                            "Unknown logLevel '" + levelStr + "' in " + id
                        );
                    }
                }

                // Only read the first config file
                break;
            } catch (Exception e) {
                AttributificationLogger.warn(
                    "Failed to parse datapack config from " +
                        id +
                        ": " +
                        e.getMessage()
                );
            }
        }
    }

    /**
     * Returns the log level override from datapacks, or {@code null} if
     * no override is present (meaning the platform config should be used).
     */
    public static LogLevel getLogLevelOverride() {
        return logLevelOverride;
    }

    /**
     * Checks if a datapack config override is active.
     */
    public static boolean hasOverride() {
        return logLevelOverride != null;
    }

    /**
     * Gets the effective log level: datapack override if present, else the
     * provided default.
     *
     * @param defaultLevel the fallback level (typically from platform config)
     * @return the effective log level
     */
    public static LogLevel getEffectiveLogLevel(LogLevel defaultLevel) {
        return logLevelOverride != null ? logLevelOverride : defaultLevel;
    }
}
