package dev.utherhel.attributification.attribute;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.DatapackConfigLoader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Loads attribute mappings from datapack JSON files.
 * <p>
 * Files are loaded from {@code data/<namespace>/attributification/mappings/*.json}
 * on every resource reload (server start, /reload).
 * <p>
 * The mod ships its own built-in datapack at
 * {@code data/attributification/attributification/mappings/defaults.json},
 * which users can override or disable like any other datapack.
 */
public final class MappingLoader {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String DIRECTORY = "attributification/mappings";

    // Currently loaded mappings (volatile for thread-safe read)
    private static volatile List<CompiledMapping> loadedMappings = null;

    private MappingLoader() {}

    /**
     * Returns the currently loaded mappings, or an empty list if none loaded.
     */
    public static List<CompiledMapping> getMappings() {
        List<CompiledMapping> m = loadedMappings;
        return m != null ? m : List.of();
    }

    /**
     * Reloads all mappings from datapack JSON files.
     * Called on server start and /reload.
     */
    public static void load(ResourceManager resourceManager) {
        // Also load datapack config first
        DatapackConfigLoader.load(resourceManager);

        Map<Identifier, Resource> resources = resourceManager.findResources(
            DIRECTORY,
            id -> id.getPath().endsWith(".json")
        );

        List<CompiledMapping> result = new ArrayList<>();

        // Sorted so that when two datapacks define the same source, which one
        // wins is deterministic instead of depending on map iteration order.
        List<Identifier> fileIds = new ArrayList<>(resources.keySet());
        fileIds.sort(Identifier::compareTo);

        for (Identifier fileId : fileIds) {
            try (
                Reader reader = new InputStreamReader(
                    resources.get(fileId).getInputStream(),
                    StandardCharsets.UTF_8
                )
            ) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null || !root.has("mappings")) continue;

                for (JsonElement elem : root.getAsJsonArray("mappings")) {
                    JsonObject obj = elem.getAsJsonObject();
                    CompiledMapping mapping = parseMapping(obj, fileId);
                    if (mapping != null) {
                        result.add(mapping);
                    }
                }
            } catch (Exception e) {
                AttributificationLogger.warn(
                    "Failed to load mappings from " +
                        fileId +
                        ": " +
                        e.getMessage()
                );
            }
        }

        if (result.isEmpty()) {
            AttributificationLogger.info(
                "No datapack mappings found — attribute unification is inactive"
            );
            loadedMappings = List.of();
        } else {
            // Expand wildcards, drop cycles and collapse alias chains before the
            // runtime ever sees a mapping.
            List<CompiledMapping> compiled = MappingCompiler.compile(
                result,
                registeredAttributeIds(),
                AttributificationLogger::warn
            );
            loadedMappings = compiled;
            AttributificationLogger.info(
                "Loaded " +
                    result.size() +
                    " attribute mappings from datapacks (" +
                    compiled.size() +
                    " after expansion)"
            );
        }

        // Notify the system to rebuild caches
        Attributifaction.reloadAttributeUnifier();
    }

    /**
     * Every registered attribute ID, used to expand wildcard sources.
     * <p>
     * Empty if the registry is not populated yet, in which case wildcard
     * patterns simply match nothing and concrete mappings are unaffected.
     */
    private static List<String> registeredAttributeIds() {
        List<String> ids = new ArrayList<>();
        try {
            for (Identifier id : Registries.ATTRIBUTE.getIds()) {
                ids.add(id.toString());
            }
        } catch (Exception e) {
            AttributificationLogger.warn(
                "Could not read the attribute registry, wildcard sources will" +
                    " not expand: " +
                    e.getMessage()
            );
        }
        return ids;
    }

    /**
     * Parses a single mapping JSON object into a CompiledMapping.
     */
    private static CompiledMapping parseMapping(
        JsonObject obj,
        Identifier fileId
    ) {
        try {
            // ── Sources ──────────────────────────────────────────────────
            List<String> sources = new ArrayList<>();
            // Support both "source" (string) and "sources" (array)
            if (obj.has("sources") && obj.get("sources").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("sources")) {
                    String s = e.getAsString();
                    if (!s.isEmpty()) sources.add(s);
                }
            }
            String singleSource = getString(obj, "source", "");
            if (!singleSource.isEmpty()) {
                sources.add(singleSource);
            }
            if (sources.isEmpty()) {
                AttributificationLogger.warn(
                    "Skipping mapping with empty sources in " + fileId
                );
                return null;
            }

            // ── Targets ──────────────────────────────────────────────────
            List<String> targets = new ArrayList<>();
            // Support both "target" (string, possibly comma-separated) and "targets" (array)
            if (obj.has("targets") && obj.get("targets").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("targets")) {
                    String t = e.getAsString();
                    if (!t.isEmpty()) targets.add(t);
                }
            }
            String singleTarget = getString(obj, "target", "");
            if (!singleTarget.isEmpty()) {
                // Comma-separated fallback targets
                String[] parts = singleTarget.split(",");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) targets.add(trimmed);
                }
            }
            if (targets.isEmpty()) {
                AttributificationLogger.warn(
                    "Skipping mapping with empty targets in " + fileId
                );
                return null;
            }

            // ── Exclusions ───────────────────────────────────────────────
            List<String> exclude = new ArrayList<>();
            if (obj.has("exclude") && obj.get("exclude").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("exclude")) {
                    String x = e.getAsString();
                    if (!x.isEmpty()) exclude.add(x);
                }
            }

            double equivalence = getDouble(obj, "equivalence", 1.0);
            double conversionEquivalence = getDouble(
                obj,
                "conversionEquivalence",
                0.0
            );
            String mode = getString(obj, "mode", "bidirectional");
            double subtractValue = getDouble(obj, "subtractValue", 0.0);
            String operationConversion = getString(
                obj,
                "operationConversion",
                "DEFAULT"
            );

            return new CompiledMapping(
                sources,
                targets,
                exclude,
                equivalence,
                conversionEquivalence,
                mode,
                subtractValue,
                operationConversion
            );
        } catch (Exception e) {
            AttributificationLogger.warn(
                "Failed to parse mapping in " + fileId + ": " + e.getMessage()
            );
            return null;
        }
    }

    private static String getString(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsDouble() : def;
    }
}
