package dev.utherhel.attributification;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.AttributificationConfig;

import java.util.*;

/**
 * OPTIMIZED: Maps source → target, tracks UUIDs for cleanup
 * Implements bidirectional caching for fast lookups
 */
public class AttributeUnifier {
    private final AttributificationConfig config;
    
    // Forward cache: source → mapping
    private final Map<String, AttributeMappingEntry> sourceToMapping;
    
    // Reverse cache: target → mapping (for EntityAttributeInstanceMixin)
    private final Map<String, AttributeMappingEntry> targetToMapping;
    
    // Tracks modifier UUIDs: target → Set<UUID>
    private final Map<String, Set<UUID>> redirectedModifiers = new HashMap<>();
    
    // Tracks source for each UUID: UUID → source
    private final Map<UUID, String> modifierSourceMap = new HashMap<>();

    public AttributeUnifier(AttributificationConfig config) {
        this.config = config;
        this.sourceToMapping = new HashMap<>();
        this.targetToMapping = new HashMap<>();
        rebuildCache();
    }

    private void rebuildCache() {
        sourceToMapping.clear();
        targetToMapping.clear();
        
        for (AttributeMappingEntry mapping : config.getMappings()) {
            // Forward: source → mapping
            sourceToMapping.put(mapping.getSourceAttribute(), mapping);
            
            // Reverse: each target → mapping
            for (String targetId : mapping.getTargetAttributes()) {
                targetToMapping.put(targetId, mapping);
            }
        }
    }

    /**
     * Is this a source attribute?
     */
    public boolean shouldUnify(String attributeId) {
        return sourceToMapping.containsKey(attributeId);
    }

    /**
     * Get mapping for source attribute
     */
    public AttributeMappingEntry getMapping(String sourceAttribute) {
        return sourceToMapping.get(sourceAttribute);
    }
    
    /**
     * Get mapping for target attribute (used by EntityAttributeInstanceMixin)
     */
    public AttributeMappingEntry getMappingByTarget(String targetAttribute) {
        return targetToMapping.get(targetAttribute);
    }

    /**
     * Is this a target attribute?
     */
    public boolean isTargetAttribute(String attributeId) {
        return targetToMapping.containsKey(attributeId);
    }

    /**
     * Track modifier: source → target
     */
    public synchronized void trackRedirectedModifier(String sourceAttributeId, String targetAttributeId, UUID modifierUuid) {
        redirectedModifiers.computeIfAbsent(targetAttributeId, k -> new HashSet<>()).add(modifierUuid);
        if (sourceAttributeId != null) {
            modifierSourceMap.put(modifierUuid, sourceAttributeId);
        }
    }

    /**
     * Untrack modifier
     */
    public synchronized void untrackRedirectedModifier(String targetAttributeId, UUID modifierUuid) {
        Set<UUID> uuids = redirectedModifiers.get(targetAttributeId);
        if (uuids != null) {
            uuids.remove(modifierUuid);
            if (uuids.isEmpty()) {
                redirectedModifiers.remove(targetAttributeId);
            }
        }
        modifierSourceMap.remove(modifierUuid);
    }

    /**
     * Get all UUIDs for target
     */
    public synchronized Set<UUID> getRedirectedModifiers(String targetAttributeId) {
        return new HashSet<>(redirectedModifiers.getOrDefault(targetAttributeId, Collections.emptySet()));
    }

    /**
     * Get all UUIDs created by source
     */
    public synchronized Set<UUID> getModifiersBySource(String sourceAttributeId) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, String> entry : modifierSourceMap.entrySet()) {
            if (entry.getValue().equals(sourceAttributeId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get source for UUID
     */
    public synchronized String getModifierSource(UUID modifierUuid) {
        return modifierSourceMap.get(modifierUuid);
    }

    /**
     * Get config
     */
    public AttributificationConfig getConfig() {
        return config;
    }
}
