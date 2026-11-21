package dev.utherhel.attributification.util;

//? if >=1.20.5 {
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Utility class for common attribute operations used across mixins.
 * Provides shared caching to avoid repeated registry lookups.
 * 
 * This class centralizes attribute ID lookups that were previously duplicated
 * across multiple mixins, improving performance and reducing code duplication.
 */
public class AttributeUtils {
    
    // Cache for parsed attribute ids to avoid repeated creation
    private static final ConcurrentHashMap<String, ResourceLocation> identifierCache = new ConcurrentHashMap<>();
    
    // Shared cache for RegistryEntry<EntityAttribute> -> String ID mapping (used by all mixins)
    // This avoids repeated registry lookups in hot paths
    private static final ConcurrentHashMap<Holder<Attribute>, String> attributeIdCache = new ConcurrentHashMap<>();
    
    // Private constructor to prevent instantiation
    private AttributeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Gets the string ID for a RegistryEntry<EntityAttribute> (e.g., "minecraft:generic.attack_damage").
     * Uses shared cache to avoid repeated registry lookups.
     * Returns null if attribute is not registered.
     * 
     * This method is called frequently in mixins, so caching is critical for performance.
     * 
     * @param attribute The attribute registry entry to get the ID for
     * @return The string ID of the attribute, or null if not registered
     */
    public static String getAttributeId(Holder<Attribute> attribute) {
        if (attribute == null) {
            return null;
        }
        
        return attributeIdCache.computeIfAbsent(attribute, attr -> 
            attr.unwrapKey().map(key -> key.
                //? if >=1.21.11 {
                /*identifier()
                *///?} else {
                location()
                //?}
                .toString()).orElse(null)
        );
    }
    
    /**
     * Gets the string ID for an EntityAttribute (e.g., "minecraft:generic.attack_damage").
     * This is a helper method for mixins that work with EntityAttribute directly (not RegistryEntry).
     * 
     * @param attribute The attribute to get the ID for
     * @return The string ID of the attribute, or null if not registered
     */
    public static String getAttributeIdDirect(Attribute attribute) {
        if (attribute == null) {
            return null;
        }
        
        // Get ID directly from registry
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        return id != null ? id.toString() : null;
    }
    
    /**
     * Gets RegistryEntry<EntityAttribute> by its string ID (e.g., "minecraft:generic.attack_damage").
     * Returns null if attribute doesn't exist or ID is invalid.
     * 
     * @param attributeId The string ID of the attribute
     * @return The RegistryEntry<EntityAttribute>, or null if not found
     */
    public static Holder<Attribute> getAttributeByName(String attributeId) {
        if (attributeId == null || attributeId.isEmpty()) {
            return null;
        }
        
        try {
            // Use the cached id to avoid repeated object creation
            //? if >=1.21 {
            ResourceLocation location = identifierCache.computeIfAbsent(attributeId, ResourceLocation::parse);
            //?} else {
            /*ResourceLocation location = identifierCache.computeIfAbsent(attributeId, ResourceLocation::new);
            *///?}
            //? if >=1.21.2 {
            /*return BuiltInRegistries.ATTRIBUTE.get(location).orElse(null);
            *///?} else {
            return BuiltInRegistries.ATTRIBUTE.getHolder(location).orElse(null);
            //?}
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
    public static Holder<Attribute> getFirstAvailableAttribute(List<String> attributeIds) {
        if (attributeIds == null || attributeIds.isEmpty()) {
            return null;
        }
        
        for (String attributeId : attributeIds) {
            Holder<Attribute> attribute = getAttributeByName(attributeId);
            if (attribute != null) {
                return attribute;
            }
        }
        
        return null;
    }
    
    /**
     * Gets EntityAttribute value from RegistryEntry.
     * 
     * @param entry The registry entry
     * @return The EntityAttribute value, or null if entry is null
     */
    public static Attribute getValue(Holder<Attribute> entry) {
        return entry != null ? entry.value() : null;
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
//?} else {
/*import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

/^*
 * Utility class for common attribute operations used across mixins.
 * Provides shared caching to avoid repeated registry lookups.
 * 
 * This class centralizes attribute ID lookups that were previously duplicated
 * across multiple mixins, improving performance and reducing code duplication.
 ^/
public class AttributeUtils {
    
    // Cache for parsed attribute ids to avoid repeated creation
    private static final ConcurrentHashMap<String, ResourceLocation> identifierCache = new ConcurrentHashMap<>();
    
    // Shared cache for EntityAttribute -> String ID mapping (used by all mixins)
    // This avoids repeated registry lookups in hot paths
    private static final ConcurrentHashMap<Attribute, String> attributeIdCache = new ConcurrentHashMap<>();
    
    // Private constructor to prevent instantiation
    private AttributeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /^*
     * Gets the string ID for an EntityAttribute (e.g., "minecraft:generic.attack_damage").
     * Uses shared cache to avoid repeated registry lookups.
     * Returns null if attribute is not registered.
     * 
     * This method is called frequently in mixins, so caching is critical for performance.
     * 
     * @param attribute The attribute to get the ID for
     * @return The string ID of the attribute, or null if not registered
     ^/
    public static String getAttributeId(Attribute attribute) {
        if (attribute == null) {
            return null;
        }
        
        return attributeIdCache.computeIfAbsent(attribute, attr -> {
            ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attr);
            return id != null ? id.toString() : null;
        });
    }
    
    /^*
     * Gets EntityAttribute by its string ID (e.g., "minecraft:generic.attack_damage").
     * Returns null if attribute doesn't exist or ID is invalid.
     * 
     * @param attributeId The string ID of the attribute
     * @return The EntityAttribute, or null if not found
     ^/
    public static Attribute getAttributeByName(String attributeId) {
        if (attributeId == null || attributeId.isEmpty()) {
            return null;
        }
        
        try {
            // Use the cached id to avoid repeated object creation
            ResourceLocation location = identifierCache.computeIfAbsent(attributeId, ResourceLocation::new);
            return BuiltInRegistries.ATTRIBUTE.get(location);
        } catch (Exception e) {
            return null;
        }
    }
    
    /^*
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
     ^/
    public static Attribute getFirstAvailableAttribute(List<String> attributeIds) {
        if (attributeIds == null || attributeIds.isEmpty()) {
            return null;
        }
        
        for (String attributeId : attributeIds) {
            Attribute attribute = getAttributeByName(attributeId);
            if (attribute != null) {
                return attribute;
            }
        }
        
        return null;
    }
    
    /^*
     * Clears all caches. Called when config is reloaded.
     * This ensures that any changes to attribute registrations are picked up.
     ^/
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
*///?}
