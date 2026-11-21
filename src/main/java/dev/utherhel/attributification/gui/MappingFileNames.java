package dev.utherhel.attributification.gui;

import java.util.Locale;

/**
 * Naming rules for mapping datapack files, shared by the GUI and both loader
 * payload handlers.
 * <p>
 * A mapping file is addressed by an <em>id</em> of the form
 * {@code "<namespace>/<name>.json"} — for example
 * {@code "attributification/defaults.json"}. The id maps directly onto the
 * datapack resource path {@code data/<namespace>/attributification/mappings/<name>.json},
 * which is what makes an override work: a file saved into the world datapack
 * under the same id shadows the built-in one.
 * <p>
 * Names are restricted to {@code [a-z0-9_-]} because Minecraft only accepts
 * {@code [a-z0-9/._-]} in resource paths — an uppercase or non-Latin character
 * would make the whole datapack fail to load.
 */
public final class MappingFileNames {

    /** Namespace used for files the player creates in the GUI. */
    public static final String DEFAULT_NAMESPACE = "attributification";

    /** Resource sub-directory holding mapping files inside a namespace. */
    public static final String MAPPINGS_DIR = "attributification/mappings";

    public static final String EXTENSION = ".json";

    /** Longest accepted bare name, so the resulting path stays sane. */
    public static final int MAX_NAME_LENGTH = 48;

    private MappingFileNames() {}

    // ── Character rules ───────────────────────────────────────────────────

    /** Characters allowed in a bare file name (no namespace, no extension). */
    public static boolean isNameCharValid(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    /**
     * Characters the name field accepts while typing. Uppercase Latin is
     * allowed here and folded to lowercase on submit, so typing {@code MyFile}
     * feels natural while the stored name stays datapack-legal.
     */
    public static boolean isTypedCharValid(char c) {
        return isNameCharValid(c) || (c >= 'A' && c <= 'Z');
    }

    /** True when every character of {@code typed} may be entered in the name field. */
    public static boolean isTypeable(String typed) {
        for (int i = 0; i < typed.length(); i++) {
            if (!isTypedCharValid(typed.charAt(i))) return false;
        }
        return true;
    }

    // ── Names ─────────────────────────────────────────────────────────────

    /** Validates a bare name such as {@code "my_mappings"} (no extension). */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!isNameCharValid(name.charAt(i))) return false;
        }
        return true;
    }

    /** Validates a file name including its {@code .json} extension. */
    public static boolean isValidFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(EXTENSION)) return false;
        return isValidName(
            fileName.substring(0, fileName.length() - EXTENSION.length())
        );
    }

    public static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) return false;
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!isNameCharValid(c) && c != '.') return false;
        }
        return true;
    }

    /**
     * Normalizes text typed into the name field into a valid file name.
     *
     * @return {@code "<name>.json"}, or {@code null} if the input cannot be
     *     made valid (empty, too long, or containing rejected characters)
     */
    public static String normalizeTypedName(String typed) {
        if (typed == null) return null;
        String name = typed.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        return isValidName(name) ? name + EXTENSION : null;
    }

    // ── Ids ───────────────────────────────────────────────────────────────

    /** Builds {@code "<namespace>/<fileName>"}. */
    public static String id(String namespace, String fileName) {
        return namespace + "/" + fileName;
    }

    /** Builds an id in the GUI's own namespace. */
    public static String defaultId(String fileName) {
        return id(DEFAULT_NAMESPACE, fileName);
    }

    /**
     * Validates a full id. Rejects path traversal, nested directories, and any
     * character Minecraft would refuse in a resource path.
     */
    public static boolean isValidId(String id) {
        if (id == null) return false;
        int slash = id.indexOf('/');
        if (slash <= 0 || slash != id.lastIndexOf('/')) return false;
        return (
            isValidNamespace(id.substring(0, slash)) &&
            isValidFileName(id.substring(slash + 1))
        );
    }

    /** Namespace part of an id, or the default namespace if it has none. */
    public static String namespaceOf(String id) {
        int slash = id == null ? -1 : id.indexOf('/');
        return slash <= 0 ? DEFAULT_NAMESPACE : id.substring(0, slash);
    }

    /** File name part of an id, extension included. */
    public static String fileNameOf(String id) {
        if (id == null) return "";
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(slash + 1);
    }

    /**
     * Accepts a possibly unqualified name and returns a valid id, or
     * {@code null} if it cannot be made valid. Bare names are placed in the
     * default namespace, which is how files created before namespaces were
     * shown in the GUI keep resolving.
     */
    public static String toValidId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        String candidate = value.indexOf('/') < 0
            ? defaultId(value.endsWith(EXTENSION) ? value : value + EXTENSION)
            : value;
        return isValidId(candidate) ? candidate : null;
    }
}
