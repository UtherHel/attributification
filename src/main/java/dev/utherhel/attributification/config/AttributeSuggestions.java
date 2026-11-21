package dev.utherhel.attributification.config;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Provides utility methods for attribute ID lookups and suggestions.
 */
public final class AttributeSuggestions {

    private AttributeSuggestions() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks if a string is a valid registered attribute ID
     */
    public static boolean isValidAttribute(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        // Allow comma-separated list for fallback attributes
        if (id.contains(",")) {
            String[] parts = id.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!isValidSingleAttribute(trimmed)) {
                    return false;
                }
            }
            return true;
        }

        return isValidSingleAttribute(id);
    }

    /**
     * Checks if a single attribute ID is valid
     */
    public static boolean isValidSingleAttribute(String id) {
        try {
            ResourceLocation identifier = ResourceLocation.tryParse(id);
            if (identifier == null) {
                return false;
            }
            return BuiltInRegistries.ATTRIBUTE.containsKey(identifier);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets all registered attribute IDs sorted alphabetically
     */
    public static List<String> getAllAttributeIds() {
        List<String> attributeIds = new ArrayList<>();
        Map<String, List<String>> attributesByName = new LinkedHashMap<>();

        // Collect all attributes grouped by name
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
            if (id != null) {
                String fullId = id.toString();
                String name = id.getPath();

                attributesByName
                    .computeIfAbsent(name, k -> new ArrayList<>())
                    .add(fullId);
            }
        }

        // Add all IDs sorted
        List<String> allIds = new ArrayList<>();
        for (List<String> ids : attributesByName.values()) {
            allIds.addAll(ids);
        }
        allIds.sort(String::compareTo);
        attributeIds.addAll(allIds);

        return attributeIds;
    }

    /**
     * Gets attribute IDs grouped by namespace
     */
    public static Map<String, List<String>> getAttributesByNamespace() {
        Map<String, List<String>> byNamespace = new LinkedHashMap<>();

        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
            if (id != null) {
                String namespace = id.getNamespace();
                byNamespace
                    .computeIfAbsent(namespace, k -> new ArrayList<>())
                    .add(id.toString());
            }
        }

        // Sort each namespace's list
        for (List<String> ids : byNamespace.values()) {
            ids.sort(String::compareTo);
        }

        return byNamespace;
    }
}
