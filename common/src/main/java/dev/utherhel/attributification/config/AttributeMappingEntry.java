package dev.utherhel.attributification.config;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.util.AllowableStrings;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;

import java.util.ArrayList;
import java.util.List;

/**
 * Attribute mapping entry for config GUI
 */
@Translation(prefix = "attributification.config.mapping")
public class AttributeMappingEntry extends ConfigSection {
    
    private static final String DEFAULT_SOURCE = "modid:attribute_name";
    private static final String DEFAULT_TARGET = "modid:target_attribute";
    
    // AllowableStrings for attribute suggestions (accepts any string, but provides suggestions)
    private static final AllowableStrings ATTRIBUTE_SUGGESTIONS = AttributeSuggestions.create(true);
    
    // AllowableStrings for operation conversion type
    private static final AllowableStrings OPERATION_CONVERSION_SUGGESTIONS = new AllowableStrings(
        s -> s.equals("DEFAULT") || s.equals("ADDITION_TO_MULTIPLY_BASE") || s.equals("ADDITION_TO_MULTIPLY_TOTAL"),
        () -> List.of("DEFAULT", "ADDITION_TO_MULTIPLY_BASE", "ADDITION_TO_MULTIPLY_TOTAL")
    );
    
    @Comment("Source attribute")
    public ValidatedString sourceAttribute = new ValidatedString("", ATTRIBUTE_SUGGESTIONS);
    
    @Comment("Target attribute (comma-separated for fallback)")
    public ValidatedString targetAttribute = new ValidatedString("", ATTRIBUTE_SUGGESTIONS);
    
    @Comment("Virtual display multiplier")
    public ValidatedDouble equivalence = new ValidatedDouble(1.0, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
    
    @Comment("Modifier conversion divisor (0 = use equivalence)")
    public ValidatedDouble conversionEquivalence = new ValidatedDouble(0.0, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
    
    @Comment("Direct redirection (no virtual value)")
    public ValidatedBoolean unidirectional = new ValidatedBoolean(false);
    
    @Comment("Visual rename only")
    public ValidatedBoolean onlyVisualChange = new ValidatedBoolean(false);
    
    @Comment("Freeze source, only target works")
    public ValidatedBoolean fullReplacement = new ValidatedBoolean(false);
    
    @Comment("Subtract from target for display")
    public ValidatedDouble subtractValue = new ValidatedDouble(0.0, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
    
    @Comment("Operation conversion")
    public ValidatedString operationConversion = new ValidatedString("DEFAULT", OPERATION_CONVERSION_SUGGESTIONS);
    
    /**
     * Default constructor
     */
    public AttributeMappingEntry() {
        this.sourceAttribute = new ValidatedString(DEFAULT_SOURCE, ATTRIBUTE_SUGGESTIONS);
        this.targetAttribute = new ValidatedString(DEFAULT_TARGET, ATTRIBUTE_SUGGESTIONS);
        this.equivalence = new ValidatedDouble(1.0, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
        this.conversionEquivalence = new ValidatedDouble(0.0, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
        this.unidirectional = new ValidatedBoolean(false);
        this.onlyVisualChange = new ValidatedBoolean(false);
        this.fullReplacement = new ValidatedBoolean(false);
        this.subtractValue = new ValidatedDouble(0.0, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
        this.operationConversion = new ValidatedString("DEFAULT", OPERATION_CONVERSION_SUGGESTIONS);
    }
    
    /**
     * Translation key for config GUI
     */
    @Override
    public String translationKey() {
        String source = getSourceAttribute();
        String target = getTargetAttribute();
        
        if (isDefaultMapping(source, target)) {
            return "attributification.config.mapping.new";
        }
        
        return buildMappingSummary(source, target);
    }
    
    private boolean isDefaultMapping(String source, String target) {
        return (source == null || source.isEmpty() || source.equals(DEFAULT_SOURCE)) &&
               (target == null || target.isEmpty() || target.equals(DEFAULT_TARGET));
    }
    
    private String buildMappingSummary(String source, String target) {
        StringBuilder summary = new StringBuilder();
        
        summary.append(formatSource(source));
        summary.append(" → ");
        summary.append(formatTarget(target));
        appendModifiers(summary);
        
        return summary.toString();
    }
    
    private String formatSource(String source) {
        return (source != null && !source.isEmpty() && !source.equals(DEFAULT_SOURCE)) ? source : "???";
    }
    
    private String formatTarget(String target) {
        if (target == null || target.isEmpty() || target.equals(DEFAULT_TARGET)) {
            return "???";
        }
        return target.length() > 30 ? target.substring(0, 27) + "..." : target;
    }
    
    private void appendModifiers(StringBuilder summary) {
        if (getEquivalence() != 1.0) {
            summary.append(" [×").append(String.format("%.1f", getEquivalence())).append("]");
        }
        if (isUnidirectional()) {
            summary.append(" [→]");
        }
        if (isFullReplacement()) {
            summary.append(" [R]");
        }
        if (isOnlyVisualChange()) {
            summary.append(" [V]");
        }
    }
    
    /**
     * Full constructor
     */
    public AttributeMappingEntry(String sourceAttribute, String targetAttribute, double equivalence,
                                 double conversionEquivalence, boolean unidirectional, boolean onlyVisualChange,
                                 boolean fullReplacement, double subtractValue, String operationConversion) {
        this.sourceAttribute = new ValidatedString(sourceAttribute, ATTRIBUTE_SUGGESTIONS);
        this.targetAttribute = new ValidatedString(targetAttribute, ATTRIBUTE_SUGGESTIONS);
        this.equivalence = new ValidatedDouble(equivalence, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
        this.conversionEquivalence = new ValidatedDouble(conversionEquivalence, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
        this.unidirectional = new ValidatedBoolean(unidirectional);
        this.onlyVisualChange = new ValidatedBoolean(onlyVisualChange);
        this.fullReplacement = new ValidatedBoolean(fullReplacement);
        this.subtractValue = new ValidatedDouble(subtractValue, Double.MAX_VALUE, 0.0, ValidatedNumber.WidgetType.TEXTBOX);
        this.operationConversion = new ValidatedString(operationConversion != null ? operationConversion : "DEFAULT", OPERATION_CONVERSION_SUGGESTIONS);
    }
    
    // Getters
    
    public String getSourceAttribute() {
        return sourceAttribute.get();
    }
    
    public String getTargetAttribute() {
        return targetAttribute.get();
    }
    
    // Cached parsed target attributes
    private String cachedTargetAttributeValue;
    private List<String> cachedTargetAttributesList;
    
    public List<String> getTargetAttributes() {
        String targetValue = targetAttribute.get();
        
        // Return cached value if unchanged
        if (cachedTargetAttributeValue != null && cachedTargetAttributeValue.equals(targetValue)) {
            return cachedTargetAttributesList;
        }
        
        // Parse and cache
        cachedTargetAttributeValue = targetValue;
        cachedTargetAttributesList = new ArrayList<>();
        
        if (targetValue != null && !targetValue.isEmpty()) {
            String[] parts = targetValue.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    cachedTargetAttributesList.add(trimmed);
                }
            }
        }
        
        return cachedTargetAttributesList;
    }
    
    public double getEquivalence() {
        return equivalence.get();
    }
    
    public double getConversionEquivalence() {
        double conv = conversionEquivalence.get();
        return conv != 0.0 ? conv : equivalence.get();
    }
    
    public boolean isUnidirectional() {
        return unidirectional.get();
    }
    
    public boolean isOnlyVisualChange() {
        return onlyVisualChange.get();
    }
    
    public boolean isFullReplacement() {
        return fullReplacement.get();
    }
    
    public double getSubtractValue() {
        return subtractValue.get();
    }
    
    public String getOperationConversion() {
        return operationConversion.get();
    }
    
    public OperationConversionType getOperationConversionType() {
        String opConv = operationConversion.get();
        if (opConv == null || opConv.isEmpty()) {
            return OperationConversionType.DEFAULT;
        }
        try {
            return OperationConversionType.valueOf(opConv);
        } catch (IllegalArgumentException e) {
            return OperationConversionType.DEFAULT;
        }
    }
    
    @Override
    public String toString() {
        String flags = "";
        if (isUnidirectional()) flags += " [→]";
        if (isOnlyVisualChange()) flags += " [Visual]";
        if (isFullReplacement()) flags += " [Replace]";
        if (getSubtractValue() != 0.0) flags += String.format(" [Subtract:%.1f]", getSubtractValue());
        if (!getOperationConversion().equals("DEFAULT")) {
            flags += " [" + getOperationConversion() + "]";
        }
        return String.format("%s -> %s (x%.2f)%s", getSourceAttribute(), getTargetAttribute(), getEquivalence(), flags);
    }
    
    /**
     * Checks if mapping changed
     */
    @Override
    public boolean deserializedChanged(Object old, Object newValue) {
        if (!isValidComparison(old, newValue)) {
            return true;
        }
        
        AttributeMappingEntry oldEntry = (AttributeMappingEntry) old;
        AttributeMappingEntry newEntry = (AttributeMappingEntry) newValue;
        
        return hasFieldChanges(oldEntry, newEntry);
    }
    
    private boolean isValidComparison(Object old, Object newValue) {
        if (old == null || newValue == null) {
            return false;
        }
        return old instanceof AttributeMappingEntry && newValue instanceof AttributeMappingEntry;
    }
    
    private boolean hasFieldChanges(AttributeMappingEntry oldEntry, AttributeMappingEntry newEntry) {
        return !java.util.Objects.equals(oldEntry.sourceAttribute.get(), newEntry.sourceAttribute.get()) ||
               !java.util.Objects.equals(oldEntry.targetAttribute.get(), newEntry.targetAttribute.get()) ||
               !java.util.Objects.equals(oldEntry.equivalence.get(), newEntry.equivalence.get()) ||
               !java.util.Objects.equals(oldEntry.conversionEquivalence.get(), newEntry.conversionEquivalence.get()) ||
               !java.util.Objects.equals(oldEntry.unidirectional.get(), newEntry.unidirectional.get()) ||
               !java.util.Objects.equals(oldEntry.onlyVisualChange.get(), newEntry.onlyVisualChange.get()) ||
               !java.util.Objects.equals(oldEntry.fullReplacement.get(), newEntry.fullReplacement.get()) ||
               !java.util.Objects.equals(oldEntry.subtractValue.get(), newEntry.subtractValue.get()) ||
               !java.util.Objects.equals(oldEntry.operationConversion.get(), newEntry.operationConversion.get());
    }
}
