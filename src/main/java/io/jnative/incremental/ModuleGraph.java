package io.jnative.incremental;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Module dependency DAG with cycle detection. */
public final class ModuleGraph {
    private final Map<String, Set<String>> edges = new LinkedHashMap<>();

    public void clear() {
        edges.clear();
    }

    public void addModule(String name) {
        edges.computeIfAbsent(name, k -> new LinkedHashSet<>());
    }

    public void addEdge(String from, String to) {
        addModule(from);
        addModule(to);
        edges.get(from).add(to);
    }

    public Set<String> dependents(String module) {
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> e : edges.entrySet()) {
            if (e.getValue().contains(module)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public Set<String> dependencies(String module) {
        return edges.getOrDefault(module, Set.of());
    }

    /** Returns cycle path if present, else empty. */
    public List<String> findCycle() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> stack = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        for (String n : edges.keySet()) {
            if (dfs(n, visited, stack, path)) {
                return path;
            }
        }
        return List.of();
    }

    private boolean dfs(String n, Set<String> visited, Set<String> stack, List<String> path) {
        if (stack.contains(n)) {
            path.add(n);
            return true;
        }
        if (visited.contains(n)) {
            return false;
        }
        visited.add(n);
        stack.add(n);
        for (String d : edges.getOrDefault(n, Set.of())) {
            if (dfs(d, visited, stack, path)) {
                path.add(0, n);
                return true;
            }
        }
        stack.remove(n);
        return false;
    }

    public List<String> topologicalOrder() {
        List<String> cycle = findCycle();
        if (!cycle.isEmpty()) {
            throw new IllegalStateException("module cycle: " + String.join(" -> ", cycle));
        }
        Map<String, Integer> indeg = new LinkedHashMap<>();
        for (String n : edges.keySet()) {
            indeg.putIfAbsent(n, 0);
            for (String d : edges.get(n)) {
                indeg.put(d, indeg.getOrDefault(d, 0) + 1);
            }
        }
        // edges are from → dependsOn (from depends on to), so reverse for topo of build order
        Map<String, Integer> reverseIndeg = new LinkedHashMap<>();
        Map<String, Set<String>> reverse = new LinkedHashMap<>();
        for (String n : edges.keySet()) {
            reverse.putIfAbsent(n, new LinkedHashSet<>());
            reverseIndeg.putIfAbsent(n, 0);
        }
        for (Map.Entry<String, Set<String>> e : edges.entrySet()) {
            for (String dep : e.getValue()) {
                reverse.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(e.getKey());
                reverseIndeg.put(e.getKey(), reverseIndeg.getOrDefault(e.getKey(), 0) + 1);
                reverseIndeg.putIfAbsent(dep, reverseIndeg.getOrDefault(dep, 0));
            }
        }
        Queue<String> q = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : reverseIndeg.entrySet()) {
            if (e.getValue() == 0) {
                q.add(e.getKey());
            }
        }
        List<String> order = new ArrayList<>();
        while (!q.isEmpty()) {
            String n = q.poll();
            order.add(n);
            for (String child : reverse.getOrDefault(n, Set.of())) {
                int v = reverseIndeg.get(child) - 1;
                reverseIndeg.put(child, v);
                if (v == 0) {
                    q.add(child);
                }
            }
        }
        return order;
    }
}
