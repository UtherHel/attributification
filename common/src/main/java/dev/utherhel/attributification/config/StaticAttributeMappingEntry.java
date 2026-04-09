package dev.utherhel.attributification.config;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.util.AllowableStrings;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;

import java.util.ArrayList;
import java.util.List;

/**
 * Static mapping (requires restart)
 */
@Translation(prefix = "attributification.config.staticMapping")
public class StaticAttributeMappingEntry extends ConfigSection {
    
    private static final String DEFAULT_SOURCE = "modid:attribute_name";
    private static final String DEFAULT_TARGET = "modid:target_attribute";
    
    private static final AllowableStrings ATTRIBUTE_SUGGESTIONS = AttributeSuggestions.create(true);
    
    @Comment("Source attribute")
    public ValidatedString sourceAttribute = new ValidatedString("", ATTRIBUTE_SUGGESTIONS);
    
    @Comment("Target attribute")
    public ValidatedString targetAttribute = new ValidatedString("", ATTRIBUTE_SUGGESTIONS);
    
    // Cached parsed target attributes
    private String cachedTargetAttributeValue;
    private List<String> cachedTargetAttributesList;
    
    public StaticAttributeMappingEntry() {
        this.sourceAttribute = new ValidatedString(DEFAULT_SOURCE, ATTRIBUTE_SUGGESTIONS);
        this.targetAttribute = new ValidatedString(DEFAULT_TARGET, ATTRIBUTE_SUGGESTIONS);
    }
    
    public StaticAttributeMappingEntry(String sourceAttribute, String targetAttribute) {
        this.sourceAttribute = new ValidatedString(sourceAttribute, ATTRIBUTE_SUGGESTIONS);
        this.targetAttribute = new ValidatedString(targetAttribute, ATTRIBUTE_SUGGESTIONS);
    }
    
    @Override
    public String translationKey() {
        String source = getSourceAttribute();
        String target = getTargetAttribute();
        
        if (isDefaultMapping(source, target)) {
            return "attributification.config.staticMapping.new";
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
    
    public String getSourceAttribute() {
        return sourceAttribute.get();
    }
    
    public String getTargetAttribute() {
        return targetAttribute.get();
    }
    
    public List<String> getTargetAttributes() {
        String targetValue = targetAttribute.get();
        
        if (cachedTargetAttributeValue != null && cachedTargetAttributeValue.equals(targetValue)) {
            return cachedTargetAttributesList;
        }
        
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
    
    @Override
    public String toString() {
        return String.format("%s -> %s", getSourceAttribute(), getTargetAttribute());
    }
    
    @Override
    public boolean deserializedChanged(Object old, Object newValue) {
        if (!isValidComparison(old, newValue)) {
            return true;
        }
        
        StaticAttributeMappingEntry oldEntry = (StaticAttributeMappingEntry) old;
        StaticAttributeMappingEntry newEntry = (StaticAttributeMappingEntry) newValue;
        
        return hasFieldChanges(oldEntry, newEntry);
    }
    
    private boolean isValidComparison(Object old, Object newValue) {
        if (old == null || newValue == null) {
            return false;
        }
        return old instanceof StaticAttributeMappingEntry && newValue instanceof StaticAttributeMappingEntry;
    }
    
    private boolean hasFieldChanges(StaticAttributeMappingEntry oldEntry, StaticAttributeMappingEntry newEntry) {
        return !java.util.Objects.equals(oldEntry.sourceAttribute.get(), newEntry.sourceAttribute.get()) ||
               !java.util.Objects.equals(oldEntry.targetAttribute.get(), newEntry.targetAttribute.get());
    }
}
