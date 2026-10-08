package io.jnative.incremental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source-level dependency graph with typed edges.
 * Heuristic extraction from source text (imports / type names / @Rpc / codecs).
 */
public final class SourceDependencyGraph {
    public record Edge(String from, String to, DependencyKind kind) {
    }

    private final Map<String, Set<Edge>> outgoing = new LinkedHashMap<>();
    private final Map<String, Set<Edge>> incoming = new LinkedHashMap<>();

    public void clear() {
        outgoing.clear();
        incoming.clear();
    }

    public void addEdge(String from, String to, DependencyKind kind) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        Edge e = new Edge(from, to, kind);
        outgoing.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(e);
        incoming.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(e);
    }

    public Set<Edge> outgoing(String unit) {
        return outgoing.getOrDefault(unit, Set.of());
    }

    public Set<Edge> incoming(String unit) {
        return incoming.getOrDefault(unit, Set.of());
    }

    public Set<String> reverseDependents(String unit) {
        Set<String> out = new LinkedHashSet<>();
        for (Edge e : incoming.getOrDefault(unit, Set.of())) {
            out.add(e.from());
        }
        return out;
    }

    /** Scan source and register dependency edges from {@code relativePath}. */
    public void indexUnit(String relativePath, String source, Set<String> knownUnits) {
        if (source == null) {
            return;
        }
        // Type references: ClassName identifiers that match other units
        for (String other : knownUnits) {
            if (other.equals(relativePath)) {
                continue;
            }
            String simple = simpleName(other);
            if (simple.isEmpty()) {
                continue;
            }
            if (source.contains(simple)) {
                DependencyKind kind = DependencyKind.TYPE_REFERENCE;
                if (source.contains("extends " + simple) || source.contains("implements " + simple)) {
                    kind = source.contains("extends " + simple)
                            ? DependencyKind.INHERITANCE
                            : DependencyKind.INTERFACE_IMPLEMENTATION;
                }
                if (simple.endsWith("Codec") || source.contains("new " + simple + "(")) {
                    if (simple.endsWith("Codec")) {
                        kind = DependencyKind.JSON_CODEC;
                    }
                }
                if (simple.endsWith("Api") || source.contains("@Rpc")) {
                    if (source.contains(simple) && (simple.endsWith("Api") || other.contains("Api"))) {
                        kind = DependencyKind.RPC_CONTRACT;
                    }
                }
                if (simple.endsWith("Page") || simple.endsWith("Component")
                        || source.contains("WebUiComponent") || source.contains("extends Component")) {
                    if (other.contains("browser") || other.contains("webui")) {
                        kind = DependencyKind.UI_COMPONENT;
                    }
                }
                addEdge(relativePath, other, kind);
            }
        }
        // Explicit import lines (flattened packages still appear as comments/imports)
        Matcher m = Pattern.compile("import\\s+[\\w.]+\\.(\\w+)\\s*;").matcher(source);
        while (m.find()) {
            String name = m.group(1);
            for (String other : knownUnits) {
                if (simpleName(other).equals(name)) {
                    addEdge(relativePath, other, DependencyKind.TYPE_REFERENCE);
                }
            }
        }
    }

    public List<String> invalidateClosure(
            String changed,
            ChangeClassification classification
    ) {
        Set<String> out = new LinkedHashSet<>();
        out.add(changed);
        if (classification == ChangeClassification.IMPLEMENTATION_ONLY
                || classification == ChangeClassification.UNCHANGED
                || classification == ChangeClassification.RESOURCE_ONLY) {
            // Still include INLINE_DEPENDENCY reverse edges
            for (Edge e : incoming.getOrDefault(changed, Set.of())) {
                if (e.kind() == DependencyKind.INLINE_DEPENDENCY
                        || e.kind() == DependencyKind.OPTIMIZATION_DEPENDENCY) {
                    out.add(e.from());
                }
            }
            return new ArrayList<>(out);
        }
        // BFS reverse dependents filtered by classification
        ArrayList<String> queue = new ArrayList<>();
        queue.add(changed);
        int i = 0;
        while (i < queue.size()) {
            String cur = queue.get(i);
            i++;
            for (Edge e : incoming.getOrDefault(cur, Set.of())) {
                if (!shouldPropagate(classification, e.kind())) {
                    continue;
                }
                if (out.add(e.from())) {
                    queue.add(e.from());
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean shouldPropagate(ChangeClassification c, DependencyKind k) {
        return switch (c) {
            case RPC_CONTRACT_CHANGE -> k == DependencyKind.RPC_CONTRACT
                    || k == DependencyKind.TYPE_REFERENCE
                    || k == DependencyKind.JSON_CODEC;
            case JSON_SHAPE_CHANGE -> k == DependencyKind.JSON_CODEC
                    || k == DependencyKind.TYPE_REFERENCE
                    || k == DependencyKind.RPC_CONTRACT;
            case PUBLIC_API_CHANGE, ABI_CHANGE -> true;
            case IMPLEMENTATION_ONLY -> k == DependencyKind.INLINE_DEPENDENCY
                    || k == DependencyKind.OPTIMIZATION_DEPENDENCY;
            case RESOURCE_ONLY, BUILD_CONFIG_CHANGE, SOURCE_CHANGED, UNCHANGED ->
                    k != DependencyKind.RESOURCE || c == ChangeClassification.RESOURCE_ONLY;
        };
    }

    private static String simpleName(String path) {
        String name = path.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return io.jnative.source.SourceFiles.stripExtension(name);
    }
}
