package dev.utherhel.attributification.util;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for common attribute operations used across mixins.
 * Provides shared caching to avoid repeated registry lookups.
 * 
 * This class centralizes attribute ID lookups that were previously duplicated
 * across multiple mixins, improving performance and reducing code duplication.
 */
public class AttributeUtils {
    
    // Cache for Identifier objects to avoid repeated creation
    private static final ConcurrentHashMap<String, Identifier> identifierCache = new ConcurrentHashMap<>();
    
    // Shared cache for EntityAttribute -> String ID mapping (used by all mixins)
    // This avoids repeated registry lookups in hot paths
    private static final ConcurrentHashMap<EntityAttribute, String> attributeIdCache = new ConcurrentHashMap<>();
    
    // Private constructor to prevent instantiation
    private AttributeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Gets the string ID for an EntityAttribute (e.g., "minecraft:generic.attack_damage").
     * Uses shared cache to avoid repeated registry lookups.
     * Returns null if attribute is not registered.
     * 
     * This method is called frequently in mixins, so caching is critical for performance.
     * 
     * @param attribute The attribute to get the ID for
     * @return The string ID of the attribute, or null if not registered
     */
    public static String getAttributeId(EntityAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        
        return attributeIdCache.computeIfAbsent(attribute, attr -> {
            Identifier id = Registries.ATTRIBUTE.getId(attr);
            return id != null ? id.toString() : null;
        });
    }
    
    /**
     * Gets EntityAttribute by its string ID (e.g., "minecraft:generic.attack_damage").
     * Returns null if attribute doesn't exist or ID is invalid.
     * 
     * @param attributeId The string ID of the attribute
     * @return The EntityAttribute, or null if not found
     */
    public static EntityAttribute getAttributeByName(String attributeId) {
        if (attributeId == null || attributeId.isEmpty()) {
            return null;
        }
        
        try {
            // Use cached Identifier to avoid repeated object creation
            Identifier location = identifierCache.computeIfAbsent(attributeId, Identifier::new);
            return Registries.ATTRIBUTE.get(location);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets first available attribute from list (fallback logic).
     * Used for target attributes with comma-separated fallbacks.
     * 
     * Example: "primary:attribute, fallback:attribute"
     * Returns first attribute that exists in the registry.
     * 
     * NOTE: This method is called frequently, so callers should cache the result.
     * 
     * @param attributeIds List of attribute IDs to try
     * @return First available attribute, or null if none found
     */
    public static EntityAttribute getFirstAvailableAttribute(List<String> attributeIds) {
        if (attributeIds == null || attributeIds.isEmpty()) {
            return null;
        }
        
        for (String attributeId : attributeIds) {
            EntityAttribute attribute = getAttributeByName(attributeId);
            if (attribute != null) {
                return attribute;
            }
        }
        
        return null;
    }
    
    /**
     * Clears all caches. Called when config is reloaded.
     * This ensures that any changes to attribute registrations are picked up.
     */
    public static void clearCaches() {
        int idCacheSize = attributeIdCache.size();
        int identifierCacheSize = identifierCache.size();
        
        attributeIdCache.clear();
        identifierCache.clear();
        
        dev.utherhel.attributification.AttributificationLogger.info(
            "Cleared AttributeUtils caches (attributeId: " + idCacheSize + 
            " entries, identifier: " + identifierCacheSize + " entries)"
        );
    }
}
