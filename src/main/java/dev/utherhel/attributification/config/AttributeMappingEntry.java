package dev.utherhel.attributification.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Attribute mapping entry — maps source → target with conversion parameters.
 * Immutable after construction.
 * <p>
 * Supports multiple source patterns (with {@code *} wildcards), multiple target
 * attributes (comma-separated fallback), and exclusion patterns.
 */
public class AttributeMappingEntry {

    private final String sourceAttribute;
    private final String targetAttribute;
    private final List<String> targetAttributes;
    private final double equivalence;
    private final double conversionEquivalence;
    private final boolean unidirectional;
    private final boolean onlyVisualChange;
    private final boolean fullReplacement;
    private final double subtractValue;
    private final String operationConversion;
    private final OperationConversionType operationConversionType;

    // New multi-source / exclude support
    private final List<String> sources;
    private final List<String> exclude;

    /**
     * Full constructor with sources and exclude lists.
     */
    public AttributeMappingEntry(
        String sourceAttribute,
        String targetAttribute,
        double equivalence,
        double conversionEquivalence,
        boolean unidirectional,
        boolean onlyVisualChange,
        boolean fullReplacement,
        double subtractValue,
        String operationConversion,
        List<String> sources,
        List<String> exclude
    ) {
        this.sourceAttribute = sourceAttribute;
        this.targetAttribute = targetAttribute;
        this.equivalence = equivalence;
        this.conversionEquivalence = conversionEquivalence;
        this.unidirectional = unidirectional;
        this.onlyVisualChange = onlyVisualChange;
        this.fullReplacement = fullReplacement;
        this.subtractValue = subtractValue;
        this.operationConversion =
            operationConversion != null ? operationConversion : "DEFAULT";

        // Parse target attributes eagerly
        this.targetAttributes = parseTargetAttributes(targetAttribute);

        // Parse operation conversion type eagerly
        OperationConversionType parsedType;
        try {
            parsedType = OperationConversionType.valueOf(
                this.operationConversion
            );
        } catch (IllegalArgumentException e) {
            parsedType = OperationConversionType.DEFAULT;
        }
        this.operationConversionType = parsedType;

        // Store sources / exclude
        this.sources = sources != null ? List.copyOf(sources) : List.of();
        this.exclude = exclude != null ? List.copyOf(exclude) : List.of();
    }

    /**
     * Full constructor (backward compat — uses sourceAttribute as single source).
     */
    public AttributeMappingEntry(
        String sourceAttribute,
        String targetAttribute,
        double equivalence,
        double conversionEquivalence,
        boolean unidirectional,
        boolean onlyVisualChange,
        boolean fullReplacement,
        double subtractValue,
        String operationConversion
    ) {
        this(
            sourceAttribute,
            targetAttribute,
            equivalence,
            conversionEquivalence,
            unidirectional,
            onlyVisualChange,
            fullReplacement,
            subtractValue,
            operationConversion,
            List.of(sourceAttribute),
            List.of()
        );
    }

    /**
     * Parses comma-separated target attributes into a list.
     */
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

    // --- Getters ---

    public String getSourceAttribute() {
        return sourceAttribute;
    }

    public String getTargetAttribute() {
        return targetAttribute;
    }

    public List<String> getTargetAttributes() {
        return targetAttributes;
    }

    public List<String> getSources() {
        return sources;
    }

    public List<String> getExclude() {
        return exclude;
    }

    /**
     * Returns source patterns for wildcard matching.
     * Falls back to {@link #getSourceAttribute()} wrapped in a list.
     */
    public List<String> getSourcePatterns() {
        if (!sources.isEmpty()) return sources;
        if (
            sourceAttribute != null && !sourceAttribute.isEmpty()
        ) return List.of(sourceAttribute);
        return List.of();
    }

    public double getEquivalence() {
        return equivalence;
    }

    public double getConversionEquivalence() {
        return conversionEquivalence != 0.0
            ? conversionEquivalence
            : equivalence;
    }

    public boolean isUnidirectional() {
        return unidirectional;
    }

    public boolean isOnlyVisualChange() {
        return onlyVisualChange;
    }

    public boolean isFullReplacement() {
        return fullReplacement;
    }

    public double getSubtractValue() {
        return subtractValue;
    }

    public String getOperationConversion() {
        return operationConversion;
    }

    public OperationConversionType getOperationConversionType() {
        return operationConversionType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sources.isEmpty() ? sourceAttribute : sources)
            .append(" -> ")
            .append(targetAttribute);
        sb.append(" (x").append(String.format("%.2f", equivalence)).append(")");
        if (unidirectional) sb.append(" [→]");
        if (onlyVisualChange) sb.append(" [Visual]");
        if (fullReplacement) sb.append(" [Replace]");
        if (subtractValue != 0.0) sb.append(
            String.format(" [Subtract:%.1f]", subtractValue)
        );
        if (!"DEFAULT".equals(operationConversion)) sb.append(" [")
            .append(operationConversion)
            .append("]");
        if (!exclude.isEmpty()) sb.append(" [excl: ")
            .append(exclude.size())
            .append("]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeMappingEntry that)) return false;
        return (
            Double.compare(equivalence, that.equivalence) == 0 &&
            Double.compare(conversionEquivalence, that.conversionEquivalence) ==
                0 &&
            unidirectional == that.unidirectional &&
            onlyVisualChange == that.onlyVisualChange &&
            fullReplacement == that.fullReplacement &&
            Double.compare(subtractValue, that.subtractValue) == 0 &&
            Objects.equals(sourceAttribute, that.sourceAttribute) &&
            Objects.equals(targetAttribute, that.targetAttribute) &&
            Objects.equals(operationConversion, that.operationConversion) &&
            Objects.equals(sources, that.sources) &&
            Objects.equals(exclude, that.exclude)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            sourceAttribute,
            targetAttribute,
            equivalence,
            conversionEquivalence,
            unidirectional,
            onlyVisualChange,
            fullReplacement,
            subtractValue,
            operationConversion,
            sources,
            exclude
        );
    }
}
