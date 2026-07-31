package dev.utherhel.attributification.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utherhel.attributification.AttributificationLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LegacyMappingConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LEGACY_FILE_NAME = "attributification.json5";
    private static final String CONVERTED_SUFFIX = ".converted";

    private LegacyMappingConverter() {}

    public static int countLegacyMappings(Path configDir) {
        Path legacyFile = configDir.resolve(LEGACY_FILE_NAME);
        if (!Files.isRegularFile(legacyFile)) return 0;
        try {
            String content = Files.readString(legacyFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonArray dynMappings = root.getAsJsonArray("dynamicMappings");
            return dynMappings != null ? dynMappings.size() : 0;
        } catch (Exception e) {
            AttributificationLogger.warn("Failed to parse legacy config: " + e.getMessage());
            return 0;
        }
    }

    public static int convertAndSave(Path configDir, Path outputPath) {
        Path legacyFile = configDir.resolve(LEGACY_FILE_NAME);
        if (!Files.isRegularFile(legacyFile)) return 0;

        try {
            String content = Files.readString(legacyFile, StandardCharsets.UTF_8);
            content = stripJson5Comments(content);

            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            List<JsonObject> convertedEntries = new ArrayList<>();

            JsonArray dynMappings = root.getAsJsonArray("dynamicMappings");
            if (dynMappings != null) {
                for (JsonElement elem : dynMappings) {
                    JsonObject old = elem.getAsJsonObject();
                    JsonObject entry = convertEntry(old);
                    if (entry != null) convertedEntries.add(entry);
                }
            }

            JsonArray staticMappings = root.getAsJsonArray("staticMappings");
            if (staticMappings != null) {
                for (JsonElement elem : staticMappings) {
                    JsonObject old = elem.getAsJsonObject();
                    JsonObject entry = new JsonObject();
                    entry.addProperty("source", getStr(old, "sourceAttribute"));
                    entry.addProperty("target", getStr(old, "targetAttribute"));
                    entry.addProperty("equivalence", 1.0);
                    entry.addProperty("conversionEquivalence", 0.0);
                    entry.addProperty("mode", "bidirectional");
                    entry.addProperty("subtractValue", 0.0);
                    entry.addProperty("operationConversion", "DEFAULT");
                    convertedEntries.add(entry);
                }
            }

            if (convertedEntries.isEmpty()) return 0;

            JsonObject rootOut = new JsonObject();
            JsonArray arr = new JsonArray();
            for (JsonObject entry : convertedEntries) {
                arr.add(entry);
            }
            rootOut.add("mappings", arr);

            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, GSON.toJson(rootOut), StandardCharsets.UTF_8);
            AttributificationLogger.info("Converted " + convertedEntries.size() + " legacy mappings to " + outputPath.getFileName());

            Path renamed = legacyFile.resolveSibling(legacyFile.getFileName() + CONVERTED_SUFFIX);
            Files.move(legacyFile, renamed);

            return convertedEntries.size();
        } catch (IOException e) {
            AttributificationLogger.error("Failed to convert legacy mappings: " + e.getMessage());
            return 0;
        }
    }

    private static JsonObject convertEntry(JsonObject old) {
        JsonObject entry = new JsonObject();
        entry.addProperty("source", getStr(old, "sourceAttribute"));
        entry.addProperty("target", getStr(old, "targetAttribute"));
        entry.addProperty("equivalence", getDouble(old, "equivalence", 1.0));
        entry.addProperty("conversionEquivalence", getDouble(old, "conversionEquivalence", 0.0));

        if (getBool(old, "fullReplacement")) {
            entry.addProperty("mode", "fullReplacement");
        } else if (getBool(old, "onlyVisualChange")) {
            entry.addProperty("mode", "onlyVisualChange");
        } else if (getBool(old, "unidirectional")) {
            entry.addProperty("mode", "unidirectional");
        } else {
            entry.addProperty("mode", "bidirectional");
        }

        entry.addProperty("subtractValue", getDouble(old, "subtractValue", 0.0));
        entry.addProperty("operationConversion", getStr(old, "operationConversion", "DEFAULT"));
        return entry;
    }

    static String stripJson5Comments(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; if (!inLineComment && !inBlockComment) sb.append(c); continue; }
            if (c == '\\' && inString) { escape = true; if (!inLineComment && !inBlockComment) sb.append(c); continue; }
            if (inLineComment) { if (c == '\n') inLineComment = false; continue; }
            if (inBlockComment) { if (c == '*' && i + 1 < json.length() && json.charAt(i + 1) == '/') { inBlockComment = false; i++; } continue; }
            if (c == '"') { inString = !inString; sb.append(c); continue; }
            if (!inString) {
                if (c == '/' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    if (next == '/') { inLineComment = true; i++; continue; }
                    if (next == '*') { inBlockComment = true; i++; continue; }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String getStr(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : "";
    }

    private static String getStr(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsDouble() : def;
    }

    private static boolean getBool(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }
}
