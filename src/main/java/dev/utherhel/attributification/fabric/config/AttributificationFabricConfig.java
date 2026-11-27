package dev.utherhel.attributification.fabric.config;

//? if fabric {
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributificationConfig;
import dev.utherhel.attributification.config.LogLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric-specific configuration that reads/writes {@code config/attributification.json}.
 * <p>
 * Only stores {@code debug.logLevel}. Attribute mappings are loaded from datapacks.
 */
public class AttributificationFabricConfig implements AttributificationConfig {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("attributification.json");

    private LogLevel logLevel = LogLevel.WARNINGS;

    public AttributificationFabricConfig() {}

    /**
     * Loads the config from the JSON file, or creates defaults if the file doesn't exist.
     */
    public void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String content = Files.readString(
                    CONFIG_PATH,
                    StandardCharsets.UTF_8
                );
                JsonObject root = JsonParser.parseString(
                    content
                ).getAsJsonObject();
                if (root.has("debug")) {
                    JsonObject debug = root.getAsJsonObject("debug");
                    if (debug.has("logLevel")) {
                        String levelStr = debug.get("logLevel").getAsString();
                        try {
                            this.logLevel = LogLevel.valueOf(levelStr.trim().toUpperCase(java.util.Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            AttributificationLogger.warn(
                                "Unknown logLevel '" +
                                    levelStr +
                                    "' (use NONE, ERRORS, WARNINGS or ALL), using WARNINGS"
                            );
                            this.logLevel = LogLevel.WARNINGS;
                        }
                    }
                }
                AttributificationLogger.info(
                    "Loaded config from " + CONFIG_PATH
                );
            } catch (Exception e) {
                AttributificationLogger.error(
                    "Failed to load config from " +
                        CONFIG_PATH +
                        ": " +
                        e.getMessage()
                );
                AttributificationLogger.info("Using default config values");
            }
        } else {
            AttributificationLogger.info(
                "No config file found at " + CONFIG_PATH + ", creating defaults"
            );
            save();
        }
    }

    /**
     * Saves the current config to the JSON file.
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            JsonObject debug = new JsonObject();
            debug.addProperty("logLevel", logLevel.name());
            root.add("debug", debug);
            String json = GSON.toJson(root);
            Files.writeString(CONFIG_PATH, json, StandardCharsets.UTF_8);
            AttributificationLogger.info("Saved config to " + CONFIG_PATH);
        } catch (IOException e) {
            AttributificationLogger.error(
                "Failed to save config to " +
                    CONFIG_PATH +
                    ": " +
                    e.getMessage()
            );
        }
    }

    @Override
    public LogLevel getPlatformLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
        save();
    }
}
//?}
