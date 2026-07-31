package dev.utherhel.attributification.gui;

import java.util.Locale;

public final class MappingFileNames {

    public static final String DEFAULT_NAMESPACE = "attributification";

    public static final String MAPPINGS_DIR = "attributification/mappings";

    public static final String EXTENSION = ".json";

    public static final int MAX_NAME_LENGTH = 48;

    private MappingFileNames() {}

    public static boolean isNameCharValid(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    public static boolean isTypedCharValid(char c) {
        return isNameCharValid(c) || (c >= 'A' && c <= 'Z');
    }

    public static boolean isTypeable(String typed) {
        for (int i = 0; i < typed.length(); i++) {
            if (!isTypedCharValid(typed.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!isNameCharValid(name.charAt(i))) return false;
        }
        return true;
    }

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

    public static String normalizeTypedName(String typed) {
        if (typed == null) return null;
        String name = typed.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        return isValidName(name) ? name + EXTENSION : null;
    }

    public static String id(String namespace, String fileName) {
        return namespace + "/" + fileName;
    }

    public static String defaultId(String fileName) {
        return id(DEFAULT_NAMESPACE, fileName);
    }

    public static boolean isValidId(String id) {
        if (id == null) return false;
        int slash = id.indexOf('/');
        if (slash <= 0 || slash != id.lastIndexOf('/')) return false;
        return (
            isValidNamespace(id.substring(0, slash)) &&
            isValidFileName(id.substring(slash + 1))
        );
    }

    public static String namespaceOf(String id) {
        int slash = id == null ? -1 : id.indexOf('/');
        return slash <= 0 ? DEFAULT_NAMESPACE : id.substring(0, slash);
    }

    public static String fileNameOf(String id) {
        if (id == null) return "";
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(slash + 1);
    }

    public static String toValidId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        String candidate = value.indexOf('/') < 0
            ? defaultId(value.endsWith(EXTENSION) ? value : value + EXTENSION)
            : value;
        return isValidId(candidate) ? candidate : null;
    }
}
