package dev.utherhel.attributification;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.config.AttributificationConfig;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Maps source → target with wildcard support and exclusion patterns.
 */
public class AttributeUnifier {

    private final AttributificationConfig config;

    // Forward cache: exact source → mapping
    private final Map<String, AttributeMappingEntry> sourceToMapping;

    // Wildcard sources: list of (compiled pattern, entry) for dynamic matching
    private final List<WildcardEntry> wildcardSources;

    public AttributeUnifier(AttributificationConfig config) {
        this.config = config;
        this.sourceToMapping = new HashMap<>();
        this.wildcardSources = new ArrayList<>();
        rebuildCache();
    }

    private void rebuildCache() {
        sourceToMapping.clear();
        wildcardSources.clear();

        for (AttributeMappingEntry mapping : config.getMappings()) {
            List<String> patterns = mapping.getSourcePatterns();

            for (String pattern : patterns) {
                if (pattern.contains("*")) {
                    // Wildcard pattern — compile and add to wildcard list
                    wildcardSources.add(new WildcardEntry(pattern, mapping));
                } else {
                    // Exact match — add to forward cache
                    sourceToMapping.put(pattern, mapping);
                }
            }
        }
    }

    /**
     * Checks if an attribute ID should be unified.
     * First checks exact cache, then wildcard patterns with exclusion support.
     */
    public boolean shouldUnify(String attributeId) {
        // Fast path: exact match
        if (sourceToMapping.containsKey(attributeId)) return true;

        // Slow path: check wildcards
        for (WildcardEntry wc : wildcardSources) {
            if (wc.matches(attributeId) && !wc.isExcluded(attributeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get mapping for a source attribute.
     * Supports wildcard matching.
     */
    public AttributeMappingEntry getMapping(String sourceAttribute) {
        // Fast path: exact match
        AttributeMappingEntry exact = sourceToMapping.get(sourceAttribute);
        if (exact != null) return exact;

        // Slow path: wildcard matching
        for (WildcardEntry wc : wildcardSources) {
            if (
                wc.matches(sourceAttribute) && !wc.isExcluded(sourceAttribute)
            ) {
                return wc.entry;
            }
        }
        return null;
    }

    public AttributificationConfig getConfig() {
        return config;
    }

    /**
     * Internal: a wildcard source pattern with compiled regex and exclusion list.
     */
    private static class WildcardEntry {

        final String rawPattern;
        final Pattern regex;
        final AttributeMappingEntry entry;
        final List<Pattern> excludePatterns;

        WildcardEntry(String pattern, AttributeMappingEntry entry) {
            this.rawPattern = pattern;
            this.regex = compileGlob(pattern);
            this.entry = entry;
            this.excludePatterns = entry
                .getExclude()
                .stream()
                .map(AttributeUnifier::compileGlob)
                .toList();
        }

        boolean matches(String id) {
            return regex.matcher(id).matches();
        }

        boolean isExcluded(String id) {
            for (Pattern ex : excludePatterns) {
                if (ex.matcher(id).matches()) return true;
            }
            return false;
        }
    }

    /**
     * Converts a glob pattern (with {@code *}) to a regex.
     * {@code *} matches any sequence of characters within a single namespace path segment,
     * or across colon if the pattern doesn't have one.
     */
    static Pattern compileGlob(String pattern) {
        // Escape everything except *
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                sb.append(".*"); // * matches anything
            } else if (
                c == '.' ||
                c == ':' ||
                c == '/' ||
                c == '\\' ||
                c == '(' ||
                c == ')' ||
                c == '[' ||
                c == ']' ||
                c == '{' ||
                c == '}' ||
                c == '+' ||
                c == '^' ||
                c == '$' ||
                c == '|'
            ) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }
}
