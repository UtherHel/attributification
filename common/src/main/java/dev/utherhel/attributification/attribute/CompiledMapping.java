package dev.utherhel.attributification.attribute;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.OperationConversionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CompiledMapping {
    private final List<String> sources;
    private final List<String> targets;
    private final List<String> exclude;
    private final double equivalence;
    private final double conversionEquivalence;
    private final String mode;
    private final double subtractValue;
    private final String operationConversion;
    private final List<String> resolvedSources;
    private final List<String> resolvedTargets;

    public CompiledMapping(List<String> sources, List<String> targets, List<String> exclude,
                           double equivalence, double conversionEquivalence,
                           String mode, double subtractValue, String operationConversion) {
        this.sources = List.copyOf(sources);
        this.targets = List.copyOf(targets);
        this.exclude = exclude != null ? List.copyOf(exclude) : List.of();
        this.equivalence = equivalence;
        this.conversionEquivalence = conversionEquivalence;
        this.mode = mode;
        this.subtractValue = subtractValue;
        this.operationConversion = operationConversion;
        this.resolvedSources = new ArrayList<>();
        this.resolvedTargets = new ArrayList<>();
    }

    public List<String> sources() { return sources; }
    public List<String> targets() { return targets; }
    public List<String> exclude() { return exclude; }
    public double equivalence() { return equivalence; }
    public double conversionEquivalence() { return conversionEquivalence; }
    public String mode() { return mode; }
    public double subtractValue() { return subtractValue; }
    public String operationConversion() { return operationConversion; }
    public List<String> resolvedSources() { return Collections.unmodifiableList(resolvedSources); }
    public List<String> resolvedTargets() { return Collections.unmodifiableList(resolvedTargets); }

    public void addResolved(String source, String target) {
        resolvedSources.add(source);
        resolvedTargets.add(target);
    }

    public AttributeMappingEntry toEntry() {
        String targetStr = String.join(",", targets);
        boolean unidirectional = "unidirectional".equals(mode);
        boolean onlyVisualChange = "onlyVisualChange".equals(mode);
        boolean fullReplacement = "fullReplacement".equals(mode);

        return new AttributeMappingEntry(
            sources.isEmpty() ? "" : sources.get(0),
            targetStr,
            equivalence,
            conversionEquivalence,
            unidirectional,
            onlyVisualChange,
            fullReplacement,
            subtractValue,
            operationConversion
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompiledMapping that)) return false;
        return Double.compare(equivalence, that.equivalence) == 0
            && Double.compare(conversionEquivalence, that.conversionEquivalence) == 0
            && Double.compare(subtractValue, that.subtractValue) == 0
            && sources.equals(that.sources)
            && targets.equals(that.targets)
            && exclude.equals(that.exclude)
            && mode.equals(that.mode)
            && operationConversion.equals(that.operationConversion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sources, targets, exclude, equivalence, conversionEquivalence, mode, subtractValue, operationConversion);
    }

    @Override
    public String toString() {
        return "CompiledMapping{" + sources + " -> " + targets + " (x" + equivalence + ")}";
    }
}
