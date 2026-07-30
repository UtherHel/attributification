package dev.utherhel.attributification.attribute;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the raw mappings parsed from datapacks into the concrete, conflict-free
 * set the runtime uses.
 * <p>
 * Four things happen here, in order:
 * <ol>
 *   <li><b>Validation</b> — malformed entries are dropped with a reason.</li>
 *   <li><b>Wildcard expansion</b> — {@code spell_power:*} becomes one mapping per
 *       registered attribute, with each {@code *} captured from the source and
 *       substituted into the target.</li>
 *   <li><b>Cycle removal</b> — if A converts to B while B converts back to A,
 *       both mappings are dropped so each attribute keeps its own value.</li>
 *   <li><b>Chain flattening</b> — A→B→C collapses to A→C, but only where that is
 *       provably equivalent (see {@link #isPureAlias}).</li>
 * </ol>
 */
public final class MappingCompiler {

    /** Guards against a pathological chain even after cycles are removed. */
    private static final int MAX_CHAIN_DEPTH = 16;

    private MappingCompiler() {}

    /**
     * @param raw         mappings as parsed from datapack JSON
     * @param knownIds    every registered attribute ID, used to expand wildcards
     * @param log         receives one line per diagnostic
     * @return concrete mappings, free of wildcards, self-references and cycles
     */
    public static List<CompiledMapping> compile(
        List<CompiledMapping> raw,
        Collection<String> knownIds,
        Consumer<String> log
    ) {
        List<CompiledMapping> expanded = expandAll(raw, knownIds, log);
        Map<String, CompiledMapping> bySource = indexBySource(expanded, log);
        removeCycles(bySource, log);
        flattenChains(bySource, log);
        warnUnknownTargets(bySource, knownIds, log);
        return List.copyOf(bySource.values());
    }

    // ── 1–2. Validation and wildcard expansion ────────────────────────────

    private static List<CompiledMapping> expandAll(
        List<CompiledMapping> raw,
        Collection<String> knownIds,
        Consumer<String> log
    ) {
        List<CompiledMapping> out = new ArrayList<>();
        for (CompiledMapping mapping : raw) {
            for (String source : mapping.sources()) {
                if (!isValidId(source, "source", log)) continue;
                if (wildcardCount(source) == 0) {
                    List<String> targets = concreteTargets(mapping, source, log);
                    if (!targets.isEmpty()) {
                        out.add(withSourceAndTargets(mapping, source, targets));
                    }
                    continue;
                }
                out.addAll(expandWildcard(mapping, source, knownIds, log));
            }
        }
        return out;
    }

    /**
     * Expands one wildcard source pattern against the registry.
     * <p>
     * Every {@code *} in the source captures a run of characters, and the n-th
     * capture replaces the n-th {@code *} in each target — so
     * {@code spell_power:* → irons_spellbooks:*_spell_power} maps {@code fire}
     * onto {@code fire_spell_power}.
     */
    private static List<CompiledMapping> expandWildcard(
        CompiledMapping mapping,
        String source,
        Collection<String> knownIds,
        Consumer<String> log
    ) {
        int sourceWildcards = wildcardCount(source);

        // A target without wildcards is a legitimate many-to-one merge; a target
        // with a different count cannot be filled in and is a config error.
        for (String target : mapping.targets()) {
            int targetWildcards = wildcardCount(target);
            if (targetWildcards != 0 && targetWildcards != sourceWildcards) {
                log.accept(
                    "Skipping mapping: source '" +
                        source +
                        "' has " +
                        sourceWildcards +
                        " wildcard(s) but target '" +
                        target +
                        "' has " +
                        targetWildcards +
                        " — they must match, or the target must have none"
                );
                return List.of();
            }
        }

        Pattern pattern = compileGlob(source);
        List<Pattern> excludes = new ArrayList<>();
        for (String exclude : mapping.exclude()) {
            excludes.add(compileGlob(exclude));
        }

        List<CompiledMapping> out = new ArrayList<>();
        int excluded = 0;
        for (String id : knownIds) {
            Matcher matcher = pattern.matcher(id);
            if (!matcher.matches()) continue;
            if (matchesAny(excludes, id)) {
                excluded++;
                continue;
            }

            List<String> captures = new ArrayList<>(matcher.groupCount());
            for (int g = 1; g <= matcher.groupCount(); g++) {
                captures.add(matcher.group(g));
            }

            List<String> targets = new ArrayList<>();
            for (String target : mapping.targets()) {
                String concrete = substitute(target, captures);
                if (!isValidId(concrete, "target", log)) continue;
                // A pattern that maps an attribute onto itself is a no-op, not
                // an error — it just means this ID is outside the conversion.
                if (!concrete.equals(id)) targets.add(concrete);
            }
            if (!targets.isEmpty()) {
                out.add(withSourceAndTargets(mapping, id, targets));
            }
        }

        if (out.isEmpty()) {
            log.accept(
                "Wildcard source '" +
                    source +
                    "' matched no registered attributes" +
                    (excluded > 0 ? " (" + excluded + " excluded)" : "")
            );
        }
        return out;
    }

    /** Drops targets that point back at the source; those convert nothing. */
    private static List<String> concreteTargets(
        CompiledMapping mapping,
        String source,
        Consumer<String> log
    ) {
        List<String> targets = new ArrayList<>();
        for (String target : mapping.targets()) {
            if (wildcardCount(target) > 0) {
                log.accept(
                    "Skipping target '" +
                        target +
                        "': it uses a wildcard but source '" +
                        source +
                        "' does not, so there is nothing to substitute"
                );
                continue;
            }
            if (!isValidId(target, "target", log)) continue;
            if (target.equals(source)) {
                log.accept(
                    "Ignoring self-mapping '" +
                        source +
                        "' → itself; the attribute is left unchanged"
                );
                continue;
            }
            targets.add(target);
        }
        return targets;
    }

    // ── 3. Cycles ─────────────────────────────────────────────────────────

    /**
     * Removes every mapping that takes part in a conversion cycle.
     * <p>
     * Two datapacks can disagree — one converting A into B while the other
     * converts B back into A. Applying either direction would be arbitrary, and
     * applying both would feed each attribute into the other, so both mappings
     * are dropped and the two attributes are left to work independently.
     */
    private static void removeCycles(
        Map<String, CompiledMapping> bySource,
        Consumer<String> log
    ) {
        Set<String> visited = new HashSet<>();
        Set<String> inCycle = new HashSet<>();

        for (String start : new ArrayList<>(bySource.keySet())) {
            if (visited.contains(start)) continue;

            // Iterative walk so a deep chain cannot overflow the stack.
            Set<String> path = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.push(start);

            while (!pending.isEmpty()) {
                String current = pending.pop();
                if (!visited.add(current)) continue;
                path.add(current);

                CompiledMapping mapping = bySource.get(current);
                if (mapping == null) continue;
                for (String target : mapping.targets()) {
                    if (path.contains(target)) {
                        inCycle.addAll(cycleFrom(path, target));
                        inCycle.add(current);
                    } else if (bySource.containsKey(target)) {
                        pending.push(target);
                    }
                }
            }
        }

        if (inCycle.isEmpty()) return;

        for (String source : inCycle) {
            bySource.remove(source);
        }
        log.accept(
            "Dropped " +
                inCycle.size() +
                " mapping(s) forming a conversion cycle: " +
                String.join(", ", inCycle) +
                " — these attributes are left unconverted so neither overwrites the other"
        );
    }

    /** The tail of {@code path} starting at {@code entry} — the cycle itself. */
    private static Set<String> cycleFrom(Set<String> path, String entry) {
        Set<String> cycle = new LinkedHashSet<>();
        boolean started = false;
        for (String node : path) {
            if (node.equals(entry)) started = true;
            if (started) cycle.add(node);
        }
        return cycle;
    }

    // ── 4. Chain flattening ───────────────────────────────────────────────

    /**
     * Rewrites A→B to A→C when B→C is a pure alias.
     * <p>
     * Only alias hops are collapsed because the conversion is not composable in
     * general: {@code subtractValue} does not distribute across two hops, and an
     * operation conversion only fires on {@code ADD_VALUE}, so a second one
     * would never apply. Chains that carry real conversion are reported instead
     * of being silently mis-composed.
     */
    private static void flattenChains(
        Map<String, CompiledMapping> bySource,
        Consumer<String> log
    ) {
        Set<String> reported = new HashSet<>();

        for (Map.Entry<String, CompiledMapping> entry : bySource.entrySet()) {
            CompiledMapping mapping = entry.getValue();
            List<String> resolved = new ArrayList<>(mapping.targets().size());
            boolean changed = false;

            for (String target : mapping.targets()) {
                String terminal = target;
                int depth = 0;

                while (depth++ < MAX_CHAIN_DEPTH) {
                    CompiledMapping next = bySource.get(terminal);
                    if (next == null || next.targets().isEmpty()) break;

                    if (!isPureAlias(next)) {
                        String key = entry.getKey() + "->" + terminal;
                        if (reported.add(key)) {
                            log.accept(
                                "'" +
                                    entry.getKey() +
                                    "' converts into '" +
                                    terminal +
                                    "', which itself converts into '" +
                                    next.primaryTarget() +
                                    "' with its own coefficients — only the first" +
                                    " step is applied; map it directly if you want both"
                            );
                        }
                        break;
                    }

                    String hop = next.primaryTarget();
                    if (hop.equals(terminal)) break;
                    terminal = hop;
                    changed = true;
                }

                if (!resolved.contains(terminal)) resolved.add(terminal);
            }

            if (changed) {
                entry.setValue(withSourceAndTargets(mapping, entry.getKey(), resolved));
            }
        }
    }

    /**
     * True when a mapping only renames an attribute — no scaling, no offset, no
     * operation change — so substituting its target changes nothing else.
     */
    private static boolean isPureAlias(CompiledMapping mapping) {
        // conversionEquivalence 0 means "fall back to equivalence" (see
        // AttributeMappingEntry#getConversionEquivalence), so both read as 1.
        double conversion = mapping.conversionEquivalence() == 0.0
            ? mapping.equivalence()
            : mapping.conversionEquivalence();
        return (
            mapping.equivalence() == 1.0 &&
            conversion == 1.0 &&
            mapping.subtractValue() == 0.0 &&
            "bidirectional".equals(mapping.mode()) &&
            "DEFAULT".equals(mapping.operationConversion())
        );
    }

    // ── Diagnostics ───────────────────────────────────────────────────────

    /**
     * Warns about targets that are not registered, without dropping them.
     * <p>
     * A modpack normally ships mappings for mods it does not all have installed,
     * so a missing target is expected rather than broken — but it is also what a
     * typo looks like, and silence makes the two indistinguishable.
     */
    private static void warnUnknownTargets(
        Map<String, CompiledMapping> bySource,
        Collection<String> knownIds,
        Consumer<String> log
    ) {
        if (knownIds.isEmpty()) return;
        Set<String> known = new HashSet<>(knownIds);
        int missing = 0;
        for (CompiledMapping mapping : bySource.values()) {
            for (String target : mapping.targets()) {
                if (!known.contains(target)) missing++;
            }
        }
        if (missing > 0) {
            log.accept(
                missing +
                    " mapping target(s) are not registered attributes — expected if" +
                    " the owning mod is absent, otherwise check for typos"
            );
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Map<String, CompiledMapping> indexBySource(
        List<CompiledMapping> mappings,
        Consumer<String> log
    ) {
        Map<String, CompiledMapping> bySource = new LinkedHashMap<>();
        for (CompiledMapping mapping : mappings) {
            String source = mapping.primarySource();
            CompiledMapping previous = bySource.put(source, mapping);
            if (previous != null) {
                log.accept(
                    "Duplicate mapping for '" +
                        source +
                        "': '" +
                        previous.primaryTarget() +
                        "' replaced by '" +
                        mapping.primaryTarget() +
                        "' (later datapack wins)"
                );
            }
        }
        return bySource;
    }

    private static CompiledMapping withSourceAndTargets(
        CompiledMapping mapping,
        String source,
        List<String> targets
    ) {
        return new CompiledMapping(
            List.of(source),
            targets,
            mapping.exclude(),
            mapping.equivalence(),
            mapping.conversionEquivalence(),
            mapping.mode(),
            mapping.subtractValue(),
            mapping.operationConversion()
        );
    }

    private static boolean isValidId(
        String id,
        String role,
        Consumer<String> log
    ) {
        if (id != null && id.indexOf(':') > 0) return true;
        log.accept(
            "Skipping " + role + " '" + id + "': missing a 'namespace:path' form"
        );
        return false;
    }

    private static int wildcardCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '*') count++;
        }
        return count;
    }

    /** Replaces the n-th {@code *} with the n-th captured group. */
    private static String substitute(String target, List<String> captures) {
        if (captures.isEmpty() || target.indexOf('*') < 0) return target;
        StringBuilder sb = new StringBuilder(target.length());
        int index = 0;
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c == '*' && index < captures.size()) {
                sb.append(captures.get(index++));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean matchesAny(List<Pattern> patterns, String id) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(id).matches()) return true;
        }
        return false;
    }

    /** Compiles a glob into a regex where each {@code *} is a capturing group. */
    static Pattern compileGlob(String glob) {
        StringBuilder sb = new StringBuilder("^");
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (literal.length() > 0) {
                    sb.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                sb.append("(.+)");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) sb.append(Pattern.quote(literal.toString()));
        sb.append('$');
        return Pattern.compile(sb.toString());
    }
}
