package io.jnative.pkg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolved dependency graph (exact versions). */
public final class ResolvedDependencyGraph {
    private final Map<PackageId, ResolvedPackage> packages = new LinkedHashMap<>();
    private final List<String> explain = new ArrayList<>();
    private final PackageId root;

    public ResolvedDependencyGraph(PackageId root) {
        this.root = root;
    }

    public PackageId root() {
        return root;
    }

    public Map<PackageId, ResolvedPackage> packages() {
        return packages;
    }

    public void put(ResolvedPackage pkg) {
        packages.put(pkg.id(), pkg);
    }

    public ResolvedPackage get(PackageId id) {
        return packages.get(id);
    }

    public List<String> explain() {
        return explain;
    }

    public String tree() {
        StringBuilder sb = new StringBuilder();
        sb.append(root.coordinate()).append('\n');
        List<ResolvedPackage> roots = new ArrayList<>();
        for (ResolvedPackage p : packages.values()) {
            roots.add(p);
        }
        roots.sort((a, b) -> a.id().compareTo(b.id()));
        int i = 0;
        for (ResolvedPackage p : roots) {
            boolean last = ++i == roots.size();
            appendNode(sb, p, "", last, 0);
        }
        return sb.toString();
    }

    private void appendNode(StringBuilder sb, ResolvedPackage p, String prefix, boolean last, int depth) {
        if (depth > 8) {
            return;
        }
        sb.append(prefix).append(last ? "└── " : "├── ")
                .append(p.id().coordinate()).append(' ').append(p.version()).append('\n');
        String childPrefix = prefix + (last ? "    " : "│   ");
        List<PackageId> deps = p.dependencies();
        for (int i = 0; i < deps.size(); i++) {
            ResolvedPackage child = packages.get(deps.get(i));
            if (child == null) {
                continue;
            }
            appendNode(sb, child, childPrefix, i == deps.size() - 1, depth + 1);
        }
    }

    public String why(PackageId id) {
        ResolvedPackage pkg = packages.get(id);
        if (pkg == null) {
            return id + " is not in the resolved graph";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(id.coordinate()).append(' ').append(pkg.version()).append(" is present because:\n");
        boolean found = false;
        for (ResolvedPackage p : packages.values()) {
            if (p.dependencies().contains(id)) {
                sb.append("  ").append(p.id().coordinate()).append(' ').append(p.version()).append('\n');
                found = true;
            }
        }
        if (!found) {
            sb.append("  direct dependency of ").append(root.coordinate()).append('\n');
        }
        return sb.toString();
    }

    public String toDot() {
        StringBuilder sb = new StringBuilder("digraph deps {\n");
        for (ResolvedPackage p : packages.values()) {
            String from = "\"" + p.id().coordinate() + "@" + p.version() + "\"";
            for (PackageId d : p.dependencies()) {
                ResolvedPackage c = packages.get(d);
                if (c != null) {
                    sb.append("  ").append(from).append(" -> \"")
                            .append(c.id().coordinate()).append('@').append(c.version()).append("\";\n");
                }
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\n  \"root\": \"").append(root).append("\",\n  \"packages\": [\n");
        boolean first = true;
        for (ResolvedPackage p : packages.values()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("    {\"id\":\"").append(p.id()).append("\",\"version\":\"")
                    .append(p.version()).append("\",\"hash\":\"").append(p.contentHash())
                    .append("\",\"source\":\"").append(escape(p.source())).append("\"}");
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
