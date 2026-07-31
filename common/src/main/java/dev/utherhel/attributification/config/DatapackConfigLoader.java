package dev.utherhel.attributification.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.utherhel.attributification.AttributificationLogger;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;

public final class DatapackConfigLoader {
    private static final Gson GSON = new Gson();

    private DatapackConfigLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static LogLevel loadLogLevelOverride(ResourceManager resourceManager) {
        Identifier configId = new Identifier("attributification", "attributification/config.json");
        for (Resource resource : resourceManager.getAllResources(configId)) {
            try (Reader reader = resource.getReader()) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null && root.has("debug")) {
                    JsonObject debug = root.getAsJsonObject("debug");
                    if (debug != null && debug.has("logLevel")) {
                        String level = debug.get("logLevel").getAsString();
                        try {
                            LogLevel parsed = LogLevel.valueOf(level.toUpperCase());
                            AttributificationLogger.info("Datapack config overrides logLevel to: " + parsed);
                            return parsed;
                        } catch (IllegalArgumentException e) {
                            AttributificationLogger.warn("Invalid logLevel in datapack config: " + level);
                        }
                    }
                }
            } catch (Exception e) {
                AttributificationLogger.warn("Failed to read datapack config: " + e.getMessage());
            }
        }
        return null;
    }
}
