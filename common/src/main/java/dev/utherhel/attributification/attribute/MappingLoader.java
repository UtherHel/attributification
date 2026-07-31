package dev.utherhel.attributification.attribute;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MappingLoader implements SynchronousResourceReloader {
    private static final Gson GSON = new Gson();
    private static final String MAPPINGS_DIR = "attributification/mappings";
    private static final Set<String> VALID_MODES = Set.of(
        "bidirectional", "unidirectional", "onlyVisualChange", "fullReplacement"
    );
    private static final Set<String> VALID_OP_CONVERSIONS = Set.of(
        "DEFAULT", "ADDITION_TO_MULTIPLY_BASE", "ADDITION_TO_MULTIPLY_TOTAL"
    );

    public static List<AttributeMappingEntry> load(ResourceManager resourceManager) {
        List<CompiledMapping> rawMappings = new ArrayList<>();
        Set<String> seenFiles = new HashSet<>();

        for (Identifier id : resourceManager.findAllResources(MAPPINGS_DIR, path -> path.getPath().endsWith(".json")).keySet()) {
            if (seenFiles.contains(id.toString())) continue;
            seenFiles.add(id.toString());

            for (Resource resource : resourceManager.getAllResources(id)) {
                try (Reader reader = resource.getReader()) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root != null && root.has("mappings")) {
                        JsonArray arr = root.getAsJsonArray("mappings");
                        for (JsonElement elem : arr) {
                            JsonObject obj = elem.getAsJsonObject();
                            CompiledMapping cm = parseMapping(obj);
                            if (cm != null) {
                                rawMappings.add(cm);
                            }
                        }
                    }
                } catch (Exception e) {
                    AttributificationLogger.warn("Failed to load mapping file " + id + ": " + e.getMessage());
                }
            }
        }

        AttributificationLogger.info("Loaded " + rawMappings.size() + " raw mappings from datapacks");
        List<AttributeMappingEntry> compiled = MappingCompiler.compile(rawMappings);
        AttributificationLogger.info("Compiled into " + compiled.size() + " attribute mappings");
        return compiled;
    }

    private static CompiledMapping parseMapping(JsonObject obj) {
        List<String> sources = parseStringList(obj, "source", "sources");
        List<String> targets = parseStringList(obj, "target", "targets");
        List<String> exclude = parseStringArray(obj, "exclude");
        double equivalence = obj.has("equivalence") ? obj.get("equivalence").getAsDouble() : 1.0;
        double conversionEquivalence = obj.has("conversionEquivalence") ? obj.get("conversionEquivalence").getAsDouble() : 0.0;
        String mode = obj.has("mode") ? obj.get("mode").getAsString() : "bidirectional";
        double subtractValue = obj.has("subtractValue") ? obj.get("subtractValue").getAsDouble() : 0.0;
        String operationConversion = obj.has("operationConversion") ? obj.get("operationConversion").getAsString() : "DEFAULT";

        if (sources.isEmpty() || targets.isEmpty()) {
            AttributificationLogger.warn("Skipping mapping with empty sources or targets");
            return null;
        }

        if (!VALID_MODES.contains(mode)) {
            AttributificationLogger.warn("Invalid mode '" + mode + "', using bidirectional");
            mode = "bidirectional";
        }

        if (!VALID_OP_CONVERSIONS.contains(operationConversion)) {
            AttributificationLogger.warn("Invalid operationConversion '" + operationConversion + "', using DEFAULT");
            operationConversion = "DEFAULT";
        }

        return new CompiledMapping(sources, targets, exclude, equivalence, conversionEquivalence, mode, subtractValue, operationConversion);
    }

    private static List<String> parseStringList(JsonObject obj, String singular, String plural) {
        List<String> result = new ArrayList<>();
        if (obj.has(plural)) {
            JsonElement arr = obj.get(plural);
            if (arr.isJsonArray()) {
                for (JsonElement e : arr.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) {
                        result.add(e.getAsString());
                    }
                }
            }
        } else if (obj.has(singular)) {
            result.add(obj.get(singular).getAsString());
        }
        return result;
    }

    private static List<String> parseStringArray(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key)) {
            JsonElement arr = obj.get(key);
            if (arr.isJsonArray()) {
                for (JsonElement e : arr.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) {
                        result.add(e.getAsString());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void reload(ResourceManager manager) {
        List<AttributeMappingEntry> mappings = load(manager);
        Attributifaction.getConfig().getMappings().clear();
        Attributifaction.getConfig().getMappings().addAll(mappings);
        Attributifaction.reloadAttributeUnifier();
    }
}
