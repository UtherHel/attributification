package dev.utherhel.attributification.config;

import java.util.ArrayList;
import java.util.List;

public final class AttributeMappingEntry {
    private final String sourceAttribute;
    private final String targetAttribute;
    private final double equivalence;
    private final double conversionEquivalence;
    private final boolean unidirectional;
    private final boolean onlyVisualChange;
    private final boolean fullReplacement;
    private final double subtractValue;
    private final OperationConversionType operationConversionType;
    private final List<String> targetAttributes;

    public AttributeMappingEntry(String sourceAttribute, String targetAttribute,
                                  double equivalence, double conversionEquivalence,
                                  boolean unidirectional, boolean onlyVisualChange,
                                  boolean fullReplacement, double subtractValue,
                                  String operationConversion) {
        this.sourceAttribute = sourceAttribute;
        this.targetAttribute = targetAttribute;
        this.equivalence = equivalence;
        this.conversionEquivalence = conversionEquivalence;
        this.unidirectional = unidirectional;
        this.onlyVisualChange = onlyVisualChange;
        this.fullReplacement = fullReplacement;
        this.subtractValue = subtractValue;
        this.operationConversionType = parseOperationConversion(operationConversion);
        this.targetAttributes = parseTargets(targetAttribute);
    }

    private static OperationConversionType parseOperationConversion(String value) {
        if (value == null || value.isEmpty()) return OperationConversionType.DEFAULT;
        try {
            return OperationConversionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return OperationConversionType.DEFAULT;
        }
    }

    private static List<String> parseTargets(String targetValue) {
        List<String> result = new ArrayList<>();
        if (targetValue == null || targetValue.isEmpty()) return result;
        String[] parts = targetValue.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public String getSourceAttribute() { return sourceAttribute; }
    public String getTargetAttribute() { return targetAttribute; }
    public List<String> getTargetAttributes() { return targetAttributes; }
    public double getEquivalence() { return equivalence; }
    public double getConversionEquivalence() {
        return conversionEquivalence != 0.0 ? conversionEquivalence : equivalence;
    }
    public boolean isUnidirectional() { return unidirectional; }
    public boolean isOnlyVisualChange() { return onlyVisualChange; }
    public boolean isFullReplacement() { return fullReplacement; }
    public double getSubtractValue() { return subtractValue; }
    public String getOperationConversion() { return operationConversionType.name(); }
    public OperationConversionType getOperationConversionType() { return operationConversionType; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceAttribute).append(" -> ").append(targetAttribute);
        sb.append(" (x").append(String.format("%.2f", equivalence)).append(")");
        if (unidirectional) sb.append(" [→]");
        if (onlyVisualChange) sb.append(" [Visual]");
        if (fullReplacement) sb.append(" [Replace]");
        return sb.toString();
    }
}
