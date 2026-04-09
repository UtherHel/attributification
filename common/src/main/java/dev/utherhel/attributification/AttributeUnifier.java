package dev.utherhel.attributification;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.AttributificationConfig;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * OPTIMIZED: Maps source → target, tracks Identifiers for cleanup
 * Implements bidirectional caching for fast lookups
 */
public class AttributeUnifier {
    private final AttributificationConfig config;
    
    // Forward cache: source → mapping
    private final Map<String, AttributeMappingEntry> sourceToMapping;
    
    // Reverse cache: target → mapping (for EntityAttributeInstanceMixin)
    private final Map<String, AttributeMappingEntry> targetToMapping;
    
    // Tracks modifier Identifiers: target → Set<Identifier>
    private final Map<String, Set<Identifier>> redirectedModifiers = new HashMap<>();
    
    // Tracks source for each Identifier: Identifier → source
    private final Map<Identifier, String> modifierSourceMap = new HashMap<>();

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
    public synchronized void trackRedirectedModifier(String sourceAttributeId, String targetAttributeId, Identifier modifierId) {
        redirectedModifiers.computeIfAbsent(targetAttributeId, k -> new HashSet<>()).add(modifierId);
        if (sourceAttributeId != null) {
            modifierSourceMap.put(modifierId, sourceAttributeId);
        }
    }

    /**
     * Untrack modifier
     */
    public synchronized void untrackRedirectedModifier(String targetAttributeId, Identifier modifierId) {
        Set<Identifier> ids = redirectedModifiers.get(targetAttributeId);
        if (ids != null) {
            ids.remove(modifierId);
            if (ids.isEmpty()) {
                redirectedModifiers.remove(targetAttributeId);
            }
        }
        modifierSourceMap.remove(modifierId);
    }

    /**
     * Get all Identifiers for target
     */
    public synchronized Set<Identifier> getRedirectedModifiers(String targetAttributeId) {
        return new HashSet<>(redirectedModifiers.getOrDefault(targetAttributeId, Collections.emptySet()));
    }

    /**
     * Get all Identifiers created by source
     */
    public synchronized Set<Identifier> getModifiersBySource(String sourceAttributeId) {
        Set<Identifier> result = new HashSet<>();
        for (Map.Entry<Identifier, String> entry : modifierSourceMap.entrySet()) {
            if (entry.getValue().equals(sourceAttributeId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get source for Identifier
     */
    public synchronized String getModifierSource(Identifier modifierId) {
        return modifierSourceMap.get(modifierId);
    }

    /**
     * Get config
     */
    public AttributificationConfig getConfig() {
        return config;
    }
}
