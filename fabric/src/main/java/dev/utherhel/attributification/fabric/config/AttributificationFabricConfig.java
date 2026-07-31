package dev.utherhel.attributification.fabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.LogLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AttributificationFabricConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve("attributification.json");

    private AttributificationFabricConfig() {}

    public static LogLevel loadLogLevel() {
        if (!Files.exists(CONFIG_PATH)) {
            saveDefault();
            return LogLevel.ERRORS;
        }
        try {
            String content = Files.readString(CONFIG_PATH);
            JsonObject root = GSON.fromJson(content, JsonObject.class);
            if (root != null && root.has("debug")) {
                JsonObject debug = root.getAsJsonObject("debug");
                if (debug != null && debug.has("logLevel")) {
                    try {
                        return LogLevel.valueOf(debug.get("logLevel").getAsString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        AttributificationLogger.warn("Invalid logLevel in config, using ERRORS");
                    }
                }
            }
        } catch (Exception e) {
            AttributificationLogger.warn("Failed to read config: " + e.getMessage());
        }
        return LogLevel.ERRORS;
    }

    private static void saveDefault() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            JsonObject debug = new JsonObject();
            debug.addProperty("logLevel", "ERRORS");
            root.add("debug", debug);
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
        } catch (IOException e) {
            AttributificationLogger.warn("Failed to save default config: " + e.getMessage());
        }
    }
}
