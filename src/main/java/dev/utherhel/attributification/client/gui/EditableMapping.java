package dev.utherhel.attributification.client.gui;

//? if >=1.21 {
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Mutable client-side model of a single mapping entry, plus (de)serialization
 * of the whole {@code {"mappings": [...]}} datapack file.
 * <p>
 * Mirrors the fields understood by
 * {@link dev.utherhel.attributification.attribute.MappingLoader}, including
 * {@code exclude}, so editing a file in-game never silently drops data.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
final class EditableMapping {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    // ── Enums ─────────────────────────────────────────────────────────────

    enum Mode {
        BIDIRECTIONAL("bidirectional"),
        UNIDIRECTIONAL("unidirectional"),
        ONLY_VISUAL_CHANGE("onlyVisualChange"),
        FULL_REPLACEMENT("fullReplacement");

        final String id;

        Mode(String id) {
            this.id = id;
        }

        Component label() {
            return Component.translatable(
                "gui.attributification.mappings.mode." + id
            );
        }

        Component description() {
            return Component.translatable(
                "gui.attributification.mappings.mode." + id + ".desc"
            );
        }

        static Mode byId(String id) {
            for (Mode m : values()) {
                if (m.id.equalsIgnoreCase(id)) return m;
            }
            return BIDIRECTIONAL;
        }
    }

    enum OpConversion {
        DEFAULT,
        ADDITION_TO_MULTIPLY_BASE,
        ADDITION_TO_MULTIPLY_TOTAL;

        Component label() {
            return Component.translatable(
                "gui.attributification.mappings.op." + name()
            );
        }

        static OpConversion byId(String id) {
            for (OpConversion o : values()) {
                if (o.name().equalsIgnoreCase(id)) return o;
            }
            return DEFAULT;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────

    final List<String> sources = new ArrayList<>();
    final List<String> targets = new ArrayList<>();
    final List<String> exclude = new ArrayList<>();

    double equivalence = 1.0;
    double conversionEquivalence = 0.0;
    double subtractValue = 0.0;
    Mode mode = Mode.BIDIRECTIONAL;
    OpConversion operationConversion = OpConversion.DEFAULT;

    EditableMapping() {
        sources.add("");
        targets.add("");
    }

    // ── Display ───────────────────────────────────────────────────────────

    /** {@code source → target}, with placeholders for not-yet-filled slots. */
    Component summary() {
        Component src = describe(sources);
        Component dst = describe(targets);
        return Component.empty()
            .append(src)
            .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
            .append(dst);
    }

    private static Component describe(List<String> values) {
        List<String> filled = clean(values);
        if (filled.isEmpty()) {
            return Component.translatable(
                "gui.attributification.mappings.unset"
            ).withStyle(ChatFormatting.RED);
        }
        Component first = Component.literal(shortId(filled.get(0)));
        if (filled.size() == 1) return first;
        return first.copy()
            .append(
                Component.literal(" +" + (filled.size() - 1)).withStyle(
                    ChatFormatting.GRAY
                )
            );
    }

    /** Drops the {@code minecraft:} namespace for compactness. */
    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    boolean isComplete() {
        return !clean(sources).isEmpty() && !clean(targets).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    static List<String> clean(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v == null) continue;
            String trimmed = v.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** Human-friendly double rendering: {@code 1.0}, {@code 0.25}, {@code 12.5}. */
    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E7) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }
        return Double.toString(value);
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    /**
     * Parses a mappings file. Returns at least one (blank) entry so the editor
     * always has something to show.
     *
     * @throws com.google.gson.JsonParseException on malformed JSON
     */
    static List<EditableMapping> parseFile(String json) {
        List<EditableMapping> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root != null && root.get("mappings") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonObject obj) {
                    result.add(parseEntry(obj));
                }
            }
        }
        if (result.isEmpty()) result.add(new EditableMapping());
        return result;
    }

    private static EditableMapping parseEntry(JsonObject obj) {
        EditableMapping m = new EditableMapping();
        m.sources.clear();
        m.targets.clear();

        readIds(obj, "sources", "source", m.sources);
        readIds(obj, "targets", "target", m.targets);
        readArray(obj, "exclude", m.exclude);

        if (m.sources.isEmpty()) m.sources.add("");
        if (m.targets.isEmpty()) m.targets.add("");

        m.equivalence = readDouble(obj, "equivalence", 1.0);
        m.conversionEquivalence = readDouble(obj, "conversionEquivalence", 0.0);
        m.subtractValue = readDouble(obj, "subtractValue", 0.0);
        m.mode = Mode.byId(readString(obj, "mode", "bidirectional"));
        m.operationConversion = OpConversion.byId(
            readString(obj, "operationConversion", "DEFAULT")
        );
        return m;
    }

    /**
     * Reads both the array form ({@code sources}) and the single-string form
     * ({@code source}), splitting the latter on commas like the loader does.
     */
    private static void readIds(
        JsonObject obj,
        String arrayKey,
        String singleKey,
        List<String> out
    ) {
        readArray(obj, arrayKey, out);
        String single = readString(obj, singleKey, "");
        if (!single.isEmpty()) {
            for (String part : single.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
        }
    }

    private static void readArray(
        JsonObject obj,
        String key,
        List<String> out
    ) {
        if (obj.get(key) instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    String value = element.getAsString().trim();
                    if (!value.isEmpty()) out.add(value);
                }
            }
        }
    }

    private static String readString(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static double readDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────

    /** Serializes entries, skipping incomplete ones the loader would reject. */
    static String serializeFile(List<EditableMapping> entries) {
        JsonArray array = new JsonArray();
        for (EditableMapping m : entries) {
            if (m.isComplete()) array.add(m.toJson());
        }
        JsonObject root = new JsonObject();
        root.add("mappings", array);
        return GSON.toJson(root);
    }

    private JsonObject toJson() {
        JsonObject obj = new JsonObject();
        writeIds(obj, "source", "sources", clean(sources));
        writeIds(obj, "target", "targets", clean(targets));

        List<String> cleanExclude = clean(exclude);
        if (!cleanExclude.isEmpty()) {
            JsonArray array = new JsonArray();
            cleanExclude.forEach(array::add);
            obj.add("exclude", array);
        }

        obj.addProperty("equivalence", equivalence);
        obj.addProperty("conversionEquivalence", conversionEquivalence);
        obj.addProperty("subtractValue", subtractValue);
        obj.addProperty("mode", mode.id);
        obj.addProperty("operationConversion", operationConversion.name());
        return obj;
    }

    /** Writes a single value as a string, multiple values as an array. */
    private static void writeIds(
        JsonObject obj,
        String singleKey,
        String arrayKey,
        List<String> values
    ) {
        if (values.size() == 1) {
            obj.addProperty(singleKey, values.get(0));
        } else {
            JsonArray array = new JsonArray();
            values.forEach(array::add);
            obj.add(arrayKey, array);
        }
    }
}
//?} else {
/*import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

final class EditableMapping {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    enum Mode {
        BIDIRECTIONAL("bidirectional"),
        UNIDIRECTIONAL("unidirectional"),
        ONLY_VISUAL_CHANGE("onlyVisualChange"),
        FULL_REPLACEMENT("fullReplacement");

        final String id;

        Mode(String id) {
            this.id = id;
        }

        Component label() {
            return Component.translatable(
                "gui.attributification.mappings.mode." + id
            );
        }

        Component description() {
            return Component.translatable(
                "gui.attributification.mappings.mode." + id + ".desc"
            );
        }

        static Mode byId(String id) {
            for (Mode m : values()) {
                if (m.id.equalsIgnoreCase(id)) return m;
            }
            return BIDIRECTIONAL;
        }
    }

    enum OpConversion {
        DEFAULT,
        ADDITION_TO_MULTIPLY_BASE,
        ADDITION_TO_MULTIPLY_TOTAL;

        Component label() {
            return Component.translatable(
                "gui.attributification.mappings.op." + name()
            );
        }

        static OpConversion byId(String id) {
            for (OpConversion o : values()) {
                if (o.name().equalsIgnoreCase(id)) return o;
            }
            return DEFAULT;
        }
    }

    final List<String> sources = new ArrayList<>();
    final List<String> targets = new ArrayList<>();
    final List<String> exclude = new ArrayList<>();

    double equivalence = 1.0;
    double conversionEquivalence = 0.0;
    double subtractValue = 0.0;
    Mode mode = Mode.BIDIRECTIONAL;
    OpConversion operationConversion = OpConversion.DEFAULT;

    EditableMapping() {
        sources.add("");
        targets.add("");
    }

    Component summary() {
        Component src = describe(sources);
        Component dst = describe(targets);
        return Component.empty()
            .append(src)
            .append(Component.literal(" \u2192 ").withStyle(ChatFormatting.DARK_GRAY))
            .append(dst);
    }

    private static Component describe(List<String> values) {
        List<String> filled = clean(values);
        if (filled.isEmpty()) {
            return Component.translatable(
                "gui.attributification.mappings.unset"
            ).withStyle(ChatFormatting.RED);
        }
        Component first = Component.literal(shortId(filled.get(0)));
        if (filled.size() == 1) return first;
        return first.copy()
            .append(
                Component.literal(" +" + (filled.size() - 1)).withStyle(
                    ChatFormatting.GRAY
                )
            );
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    boolean isComplete() {
        return !clean(sources).isEmpty() && !clean(targets).isEmpty();
    }

    static List<String> clean(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v == null) continue;
            String trimmed = v.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E7) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }
        return Double.toString(value);
    }

    static List<EditableMapping> parseFile(String json) {
        List<EditableMapping> result = new ArrayList<>();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root != null && root.get("mappings") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonObject obj) {
                    result.add(parseEntry(obj));
                }
            }
        }
        if (result.isEmpty()) result.add(new EditableMapping());
        return result;
    }

    private static EditableMapping parseEntry(JsonObject obj) {
        EditableMapping m = new EditableMapping();
        m.sources.clear();
        m.targets.clear();

        readIds(obj, "sources", "source", m.sources);
        readIds(obj, "targets", "target", m.targets);
        readArray(obj, "exclude", m.exclude);

        if (m.sources.isEmpty()) m.sources.add("");
        if (m.targets.isEmpty()) m.targets.add("");

        m.equivalence = readDouble(obj, "equivalence", 1.0);
        m.conversionEquivalence = readDouble(obj, "conversionEquivalence", 0.0);
        m.subtractValue = readDouble(obj, "subtractValue", 0.0);
        m.mode = Mode.byId(readString(obj, "mode", "bidirectional"));
        m.operationConversion = OpConversion.byId(
            readString(obj, "operationConversion", "DEFAULT")
        );
        return m;
    }

    private static void readIds(
        JsonObject obj,
        String arrayKey,
        String singleKey,
        List<String> out
    ) {
        readArray(obj, arrayKey, out);
        String single = readString(obj, singleKey, "");
        if (!single.isEmpty()) {
            for (String part : single.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
        }
    }

    private static void readArray(
        JsonObject obj,
        String key,
        List<String> out
    ) {
        if (obj.get(key) instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    String value = element.getAsString().trim();
                    if (!value.isEmpty()) out.add(value);
                }
            }
        }
    }

    private static String readString(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static double readDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    static String serializeFile(List<EditableMapping> entries) {
        JsonArray array = new JsonArray();
        for (EditableMapping m : entries) {
            if (m.isComplete()) array.add(m.toJson());
        }
        JsonObject root = new JsonObject();
        root.add("mappings", array);
        return GSON.toJson(root);
    }

    private JsonObject toJson() {
        JsonObject obj = new JsonObject();
        writeIds(obj, "source", "sources", clean(sources));
        writeIds(obj, "target", "targets", clean(targets));

        List<String> cleanExclude = clean(exclude);
        if (!cleanExclude.isEmpty()) {
            JsonArray array = new JsonArray();
            cleanExclude.forEach(array::add);
            obj.add("exclude", array);
        }

        obj.addProperty("equivalence", equivalence);
        obj.addProperty("conversionEquivalence", conversionEquivalence);
        obj.addProperty("subtractValue", subtractValue);
        obj.addProperty("mode", mode.id);
        obj.addProperty("operationConversion", operationConversion.name());
        return obj;
    }

    private static void writeIds(
        JsonObject obj,
        String singleKey,
        String arrayKey,
        List<String> values
    ) {
        if (values.size() == 1) {
            obj.addProperty(singleKey, values.get(0));
        } else {
            JsonArray array = new JsonArray();
            values.forEach(array::add);
            obj.add(arrayKey, array);
        }
    }
}
*///?}
