package dev.utherhel.attributification.attribute;

import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;

import java.util.*;
import java.util.regex.Pattern;

public final class MappingCompiler {
    private MappingCompiler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<AttributeMappingEntry> compile(List<CompiledMapping> rawMappings) {
        List<CompiledMapping> expanded = expandWildcards(rawMappings);
        expanded = removeCycles(expanded);
        expanded = collapseChains(expanded);
        return expanded.stream().map(CompiledMapping::toEntry).toList();
    }

    static List<CompiledMapping> expandWildcards(List<CompiledMapping> mappings) {
        List<CompiledMapping> result = new ArrayList<>();
        for (CompiledMapping mapping : mappings) {
            for (int i = 0; i < mapping.sources().size(); i++) {
                String source = mapping.sources().get(i);
                String target = i < mapping.targets().size() ? mapping.targets().get(i) : mapping.targets().get(0);
                int sourceWildcards = countWildcards(source);
                int targetWildcards = countWildcards(target);
                if (sourceWildcards != targetWildcards) {
                    AttributificationLogger.warn("Wildcard count mismatch: " + source + " (" + sourceWildcards + ") -> " + target + " (" + targetWildcards + ")");
                    continue;
                }
                if (sourceWildcards == 0) {
                    mapping.addResolved(source, target);
                }
            }
            if (mapping.resolvedSources().isEmpty()) {
                result.add(mapping);
            } else {
                for (int i = 0; i < mapping.resolvedSources().size(); i++) {
                    String src = mapping.resolvedSources().get(i);
                    String tgt = mapping.resolvedTargets().get(i);
                    List<String> singleSource = List.of(src);
                    List<String> singleTarget = List.of(tgt);
                    result.add(new CompiledMapping(singleSource, singleTarget, mapping.exclude(),
                        mapping.equivalence(), mapping.conversionEquivalence(), mapping.mode(),
                        mapping.subtractValue(), mapping.operationConversion()));
                }
            }
        }
        return result;
    }

    private static int countWildcards(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '*') count++;
        }
        return count;
    }

    static List<CompiledMapping> removeCycles(List<CompiledMapping> mappings) {
        Map<String, CompiledMapping> sourceToMapping = new LinkedHashMap<>();
        for (CompiledMapping m : mappings) {
            for (String src : m.sources()) {
                if (src != null && !src.isEmpty()) {
                    sourceToMapping.put(src, m);
                }
            }
        }

        Set<String> inCycle = new HashSet<>();
        for (CompiledMapping m : mappings) {
            for (String src : m.sources()) {
                if (src == null || src.isEmpty()) continue;
                for (String tgt : m.targets()) {
                    if (src.equals(tgt)) {
                        inCycle.add(src);
                    } else if (sourceToMapping.containsKey(tgt)) {
                        CompiledMapping next = sourceToMapping.get(tgt);
                        for (String nextSrc : next.sources()) {
                            if (tgt.equals(nextSrc)) {
                                for (String nextTgt : next.targets()) {
                                    if (nextTgt.equals(src)) {
                                        inCycle.add(src);
                                        inCycle.add(tgt);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!inCycle.isEmpty()) {
            AttributificationLogger.warn("Removing " + inCycle.size() + " cyclic mappings: " + inCycle);
        }

        List<CompiledMapping> result = new ArrayList<>();
        for (CompiledMapping m : mappings) {
            boolean hasCycle = false;
            for (String src : m.sources()) {
                if (inCycle.contains(src)) {
                    hasCycle = true;
                    break;
                }
            }
            if (!hasCycle) {
                result.add(m);
            }
        }
        return result;
    }

    static List<CompiledMapping> collapseChains(List<CompiledMapping> mappings) {
        Map<String, CompiledMapping> sourceToMapping = new LinkedHashMap<>();
        for (CompiledMapping m : mappings) {
            for (String src : m.sources()) {
                sourceToMapping.putIfAbsent(src, m);
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            List<CompiledMapping> newMappings = new ArrayList<>();
            for (CompiledMapping m : mappings) {
                if (isPureAlias(m)) {
                    String source = m.sources().get(0);
                    String target = m.targets().get(0);
                    CompiledMapping next = sourceToMapping.get(target);
                    if (next != null && next != m && isPureAlias(next)) {
                        String finalTarget = next.targets().get(0);
                        AttributificationLogger.info("Collapsing chain: " + source + " -> " + target + " -> " + finalTarget);
                        newMappings.add(new CompiledMapping(
                            List.of(source), List.of(finalTarget), m.exclude(),
                            m.equivalence(), m.conversionEquivalence(), m.mode(),
                            m.subtractValue(), m.operationConversion()));
                        changed = true;
                        continue;
                    }
                }
                newMappings.add(m);
            }
            mappings = newMappings;
            sourceToMapping.clear();
            for (CompiledMapping m : mappings) {
                for (String src : m.sources()) {
                    sourceToMapping.putIfAbsent(src, m);
                }
            }
        }
        return mappings;
    }

    static boolean isPureAlias(CompiledMapping m) {
        return m.equivalence() == 1.0
            && m.conversionEquivalence() == 0.0
            && m.subtractValue() == 0.0
            && "bidirectional".equals(m.mode())
            && "DEFAULT".equals(m.operationConversion());
    }
}
