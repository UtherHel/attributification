package dev.utherhel.attributification.config;

import java.util.ArrayList;
import java.util.List;

public class StaticAttributeMappingEntry {

    private final String sourceAttribute;
    private final String targetAttribute;
    private final List<String> targetAttributes;

    public StaticAttributeMappingEntry(
        String sourceAttribute,
        String targetAttribute
    ) {
        this.sourceAttribute = sourceAttribute;
        this.targetAttribute = targetAttribute;
        this.targetAttributes = parseTargetAttributes(targetAttribute);
    }

    private static List<String> parseTargetAttributes(String targetValue) {
        List<String> result = new ArrayList<>();
        if (targetValue != null && !targetValue.isEmpty()) {
            String[] parts = targetValue.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    public String getSourceAttribute() {
        return sourceAttribute;
    }

    public String getTargetAttribute() {
        return targetAttribute;
    }

    public List<String> getTargetAttributes() {
        return targetAttributes;
    }

    @Override
    public String toString() {
        return String.format("%s -> %s", sourceAttribute, targetAttribute);
    }
}
