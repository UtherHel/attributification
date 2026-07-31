package dev.utherhel.attributification;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.AttributificationConfig;

import java.util.*;

public class AttributeUnifier {
    private final AttributificationConfig config;

    private final Map<String, AttributeMappingEntry> sourceToMapping = new HashMap<>();
    private final Map<String, AttributeMappingEntry> targetToMapping = new HashMap<>();
    private final Map<String, Set<UUID>> redirectedModifiers = new HashMap<>();
    private final Map<UUID, String> modifierSourceMap = new HashMap<>();

    public AttributeUnifier(AttributificationConfig config) {
        this.config = config;
        rebuildCache();
    }

    public void rebuildCache() {
        sourceToMapping.clear();
        targetToMapping.clear();

        for (AttributeMappingEntry mapping : config.getMappings()) {
            sourceToMapping.put(mapping.getSourceAttribute(), mapping);
            for (String targetId : mapping.getTargetAttributes()) {
                targetToMapping.put(targetId, mapping);
            }
        }
    }

    public boolean shouldUnify(String attributeId) {
        return sourceToMapping.containsKey(attributeId);
    }

    public AttributeMappingEntry getMapping(String sourceAttribute) {
        return sourceToMapping.get(sourceAttribute);
    }

    public AttributeMappingEntry getMappingByTarget(String targetAttribute) {
        return targetToMapping.get(targetAttribute);
    }

    public boolean isTargetAttribute(String attributeId) {
        return targetToMapping.containsKey(attributeId);
    }

    public synchronized void trackRedirectedModifier(String sourceAttributeId, String targetAttributeId, UUID modifierUuid) {
        redirectedModifiers.computeIfAbsent(targetAttributeId, k -> new HashSet<>()).add(modifierUuid);
        if (sourceAttributeId != null) {
            modifierSourceMap.put(modifierUuid, sourceAttributeId);
        }
    }

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

    public synchronized Set<UUID> getRedirectedModifiers(String targetAttributeId) {
        return new HashSet<>(redirectedModifiers.getOrDefault(targetAttributeId, Collections.emptySet()));
    }

    public synchronized Set<UUID> getModifiersBySource(String sourceAttributeId) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, String> entry : modifierSourceMap.entrySet()) {
            if (entry.getValue().equals(sourceAttributeId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public synchronized String getModifierSource(UUID modifierUuid) {
        return modifierSourceMap.get(modifierUuid);
    }

    public AttributificationConfig getConfig() {
        return config;
    }
}
