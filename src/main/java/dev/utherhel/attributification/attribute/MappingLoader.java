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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

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

    /** Identifies the last load in this game, so a client can tell its own integrated server's mappings apart. */
    private static volatile String token = "";

    /** The fields a mapping can have; anything else is most likely a typo and is reported. */
    private static final Set<String> FIELDS = Set.of(
        "source", "sources", "target", "targets", "exclude", "mode", "equivalence",
        "conversionEquivalence", "subtractValue", "operationConversion", "comment", "_comment"
    );
    private static final List<String> MODES = List.of(
        "bidirectional", "unidirectional", "onlyVisualChange", "fullReplacement"
    );
    private static final List<String> OPERATIONS = List.of(
        "DEFAULT", "ADDITION_TO_MULTIPLY_BASE", "ADDITION_TO_MULTIPLY_TOTAL"
    );

    private MappingLoader() {}

    /**
     * Returns the currently loaded mappings, or an empty list if none loaded.
     */
    public static List<CompiledMapping> getMappings() {
        List<CompiledMapping> m = loadedMappings;
        return m != null ? m : List.of();
    }

    static String token() {
        return token;
    }

    /** Client side: replaces the mappings with the ones the server sent (see {@link MappingSync}). */
    static void useSynced(List<CompiledMapping> mappings) {
        loadedMappings = List.copyOf(mappings);
        Attributifaction.reloadAttributeUnifier();
    }

    /**
     * Reloads all mappings from datapack JSON files.
     * Called on server start and /reload.
     */
    public static void load(ResourceManager resourceManager) {
        // Also load datapack config first
        DatapackConfigLoader.load(resourceManager);

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
            DIRECTORY,
            id -> id.getPath().endsWith(".json")
        );

        List<CompiledMapping> result = new ArrayList<>();
        int problems = 0;
        AttributificationLogger.resetWarnOnce();

        // Sorted so that when two datapacks define the same source, which one
        // wins is deterministic instead of depending on map iteration order.
        List<ResourceLocation> fileIds = new ArrayList<>(resources.keySet());
        fileIds.sort(ResourceLocation::compareTo);

        for (ResourceLocation fileId : fileIds) {
            try (
                Reader reader = new InputStreamReader(
                    resources.get(fileId).open(),
                    StandardCharsets.UTF_8
                )
            ) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed == null || !parsed.isJsonObject()) {
                    AttributificationLogger.warn(fileId + " is not a JSON object like {\"mappings\": [...]}: skipped");
                    problems++;
                    continue;
                }
                JsonObject root = parsed.getAsJsonObject();
                if (!root.has("mappings") || !root.get("mappings").isJsonArray()) {
                    AttributificationLogger.warn(fileId + " has no \"mappings\" list: skipped");
                    problems++;
                    continue;
                }

                int index = 0;
                for (JsonElement elem : root.getAsJsonArray("mappings")) {
                    index++;
                    String where = fileId + ", mapping " + index;
                    if (!elem.isJsonObject()) {
                        AttributificationLogger.warn(where + " is not a JSON object: skipped");
                        problems++;
                        continue;
                    }
                    CompiledMapping mapping = parseMapping(elem.getAsJsonObject(), where);
                    if (mapping != null) {
                        result.add(mapping);
                    } else {
                        problems++;
                    }
                }
            } catch (Exception e) {
                AttributificationLogger.warn(
                    "Could not read " +
                        fileId +
                        " (is it valid JSON?): " +
                        e.getMessage()
                );
                problems++;
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
            List<String> knownIds = registeredAttributeIds();
            int[] compileProblems = {0};
            List<CompiledMapping> compiled = MappingCompiler.compile(
                result,
                knownIds,
                message -> {
                    compileProblems[0]++;
                    AttributificationLogger.warn(message);
                },
                AttributificationLogger::info
            );
            loadedMappings = compiled;
            problems += compileProblems[0];
            AttributificationLogger.info(
                "Loaded " +
                    result.size() +
                    " attribute mappings from datapacks (" +
                    compiled.size() +
                    " after expansion)"
            );
            summarize(result.size(), compiled, knownIds, problems);
        }

        token = java.util.UUID.randomUUID().toString();

        // Notify the system to rebuild caches
        Attributifaction.reloadAttributeUnifier();
        MappingSync.broadcast();
    }

    /** One line: how many mappings work, wait for a mod, or have a problem. */
    private static void summarize(int entries, List<CompiledMapping> compiled, List<String> knownIds, int problems) {
        Set<String> known = new java.util.HashSet<>(knownIds);
        int active = 0;
        for (CompiledMapping mapping : compiled) {
            if (!known.contains(mapping.primarySource())) continue;
            for (String target : mapping.targets()) {
                if (known.contains(target)) {
                    active++;
                    break;
                }
            }
        }
        AttributificationLogger.summary(
            entries + " attribute mapping entries (" + compiled.size() + " mappings after wildcards): " + active +
                " active, " + (compiled.size() - active) +
                " waiting for mods that are not installed" +
                (problems > 0 ? ", " + problems + " problem(s) (see the warnings above)" : "") + "."
        );
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
            for (ResourceLocation id : BuiltInRegistries.ATTRIBUTE.keySet()) {
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
        String fileId
    ) {
        try {
            for (String key : obj.keySet()) {
                if (!FIELDS.contains(key)) {
                    AttributificationLogger.warn(
                        "Unknown field '" + key + "' in " + fileId + " (ignored)" + suggest(key, FIELDS)
                    );
                }
            }

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
            double subtractValue = getDouble(obj, "subtractValue", 0.0);
            String where = fileId + " (" + sources.get(0) + ")";
            if (!Double.isFinite(equivalence) || !Double.isFinite(conversionEquivalence) ||
                !Double.isFinite(subtractValue)) {
                AttributificationLogger.warn("Skipping mapping in " + where + ": equivalence, conversionEquivalence" +
                    " and subtractValue must be numbers");
                return null;
            }
            double divisor = conversionEquivalence != 0.0 ? conversionEquivalence : equivalence;
            if (divisor == 0.0) {
                AttributificationLogger.warn("Skipping mapping in " + where + ": item bonuses are divided by" +
                    " conversionEquivalence (or equivalence when it is left out), which is 0 here");
                return null;
            }

            String mode = canonical(getString(obj, "mode", "bidirectional"), MODES);
            if (mode == null) {
                AttributificationLogger.warn("Skipping mapping in " + where + ": unknown mode '" +
                    getString(obj, "mode", "") + "'; use one of " + String.join(", ", MODES));
                return null;
            }
            String operationConversion = canonical(
                getString(obj, "operationConversion", "DEFAULT"), OPERATIONS);
            if (operationConversion == null) {
                AttributificationLogger.warn("Skipping mapping in " + where + ": unknown operationConversion '" +
                    getString(obj, "operationConversion", "") + "'; use one of " + String.join(", ", OPERATIONS));
                return null;
            }

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

    /**
     * The canonical spelling of {@code value} among {@code allowed}, ignoring case, spaces, '-' and '_'
     * ("full_replacement" and "FullReplacement" are "fullReplacement"); null when it is none of them.
     */
    private static String canonical(String value, List<String> allowed) {
        String key = squash(value);
        for (String option : allowed) {
            if (squash(option).equals(key)) return option;
        }
        return null;
    }

    private static String squash(String value) {
        return value.replace("_", "").replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /** ", did you mean 'x'?" for a field name one or two letters away from a known one. */
    private static String suggest(String key, Set<String> known) {
        String best = null;
        int bestDistance = 3;
        for (String option : known) {
            int d = distance(key.toLowerCase(Locale.ROOT), option.toLowerCase(Locale.ROOT));
            if (d < bestDistance) {
                bestDistance = d;
                best = option;
            }
        }
        return best == null ? "" : ", did you mean '" + best + "'?";
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
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
