package dev.utherhel.attributification.config;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttributeSuggestions {
    private AttributeSuggestions() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isValidAttribute(String id) {
        if (id == null || id.isEmpty()) return false;
        if (id.contains(",")) {
            String[] parts = id.split(",");
            for (String part : parts) {
                if (!isValidSingleAttribute(part.trim())) return false;
            }
            return true;
        }
        return isValidSingleAttribute(id);
    }

    private static boolean isValidSingleAttribute(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return false;
            return Registries.ATTRIBUTE.containsId(identifier);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> getAllAttributeIds() {
        List<String> result = new ArrayList<>();
        for (EntityAttribute attribute : Registries.ATTRIBUTE) {
            Identifier id = Registries.ATTRIBUTE.getId(attribute);
            if (id != null) {
                result.add(id.toString());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    public static Map<String, List<String>> getAttributesByNamespace() {
        Map<String, List<String>> byNamespace = new LinkedHashMap<>();
        for (EntityAttribute attribute : Registries.ATTRIBUTE) {
            Identifier id = Registries.ATTRIBUTE.getId(attribute);
            if (id != null) {
                byNamespace.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id.toString());
            }
        }
        for (List<String> ids : byNamespace.values()) {
            ids.sort(String::compareTo);
        }
        return byNamespace;
    }
}
