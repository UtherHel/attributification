package dev.utherhel.attributification.attribute;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.OperationConversionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable record of a compiled attribute mapping from a datapack JSON file.
 * <p>
 * Supports multiple source patterns (with {@code *} wildcards), multiple target
 * attributes (fallback order), and exclusion patterns.
 *
 * @param sources              Source patterns (e.g. ["spell_power:*"]). Supports {@code *} as wildcard.
 * @param targets              Target attributes in fallback order (e.g. ["irons_spellbooks:fire_spell_power"])
 * @param exclude              Exclusion patterns — IDs matching these will NOT be unified
 * @param equivalence          Virtual display multiplier (default 1.0)
 * @param conversionEquivalence Modifier conversion divisor (0 = use equivalence)
 * @param mode                 Mapping mode: bidirectional, unidirectional, onlyVisualChange, fullReplacement
 * @param subtractValue        Value subtracted from target before equivalence (default 0.0)
 * @param operationConversion  Operation conversion type (DEFAULT, ADDITION_TO_MULTIPLY_BASE, ADDITION_TO_MULTIPLY_TOTAL)
 */
public record CompiledMapping(
    List<String> sources,
    List<String> targets,
    List<String> exclude,
    double equivalence,
    double conversionEquivalence,
    String mode,
    double subtractValue,
    String operationConversion
) {
    public CompiledMapping {
        if (sources == null || sources.isEmpty()) sources = List.of();
        if (targets == null || targets.isEmpty()) targets = List.of();
        if (exclude == null) exclude = List.of();
        if (mode == null || mode.isBlank()) mode = "bidirectional";
        if (
            operationConversion == null || operationConversion.isBlank()
        ) operationConversion = "DEFAULT";
        // Defensive copy
        sources = List.copyOf(sources);
        targets = List.copyOf(targets);
        exclude = List.copyOf(exclude);
    }

    /**
     * Returns the primary source (first in list, or empty string).
     */
    public String primarySource() {
        return sources.isEmpty() ? "" : sources.get(0);
    }

    /**
     * Returns the primary target (first in list, or empty string).
     */
    public String primaryTarget() {
        return targets.isEmpty() ? "" : targets.get(0);
    }

    /**
     * Converts this compiled mapping to an {@link AttributeMappingEntry} for use
     * with the existing runtime system.
     */
    public AttributeMappingEntry toEntry() {
        boolean unidirectional = "unidirectional".equals(mode);
        boolean onlyVisualChange = "onlyVisualChange".equals(mode);
        boolean fullReplacement = "fullReplacement".equals(mode);

        // Join multiple targets as comma-separated for backward compat
        String targetStr = String.join(", ", targets);

        return new AttributeMappingEntry(
            primarySource(),
            targetStr,
            equivalence,
            conversionEquivalence,
            unidirectional,
            onlyVisualChange,
            fullReplacement,
            subtractValue,
            operationConversion,
            new ArrayList<>(sources),
            new ArrayList<>(exclude)
        );
    }
}
