package io.jnative.pkg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backtracking + constraint propagation dependency resolver.
 * Does NOT use naive "highest version wins".
 * @author 张晓达
 */
public final class DependencyResolver {
    public static final String RESOLVER_VERSION = "1";

    public record PackageMeta(
            PackageId id,
            SemanticVersion version,
            List<Dependency> dependencies,
            Set<TargetCapability> requires,
            Set<String> features,
            String contentHash,
            String source,
            boolean yanked,
            String packageType,
            String path,
            List<PackageManifest.NativeLibrarySpec> nativeLibraries
    ) {
        public PackageMeta {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            requires = requires == null ? Set.of() : Set.copyOf(requires);
            features = features == null ? Set.of() : Set.copyOf(features);
            nativeLibraries = nativeLibraries == null ? List.of() : List.copyOf(nativeLibraries);
        }
    }

    public interface Index {
        List<PackageMeta> versions(PackageId id);

        PackageMeta get(PackageId id, SemanticVersion version);
    }

    public record ResolveRequest(
            PackageManifest root,
            String triple,
            boolean includeDev,
            boolean allowYanked,
            Set<String> enabledFeatures
    ) {
    }

    public record ResolveFailure(String message, List<String> paths) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(message).append('\n');
            for (String p : paths) {
                sb.append(p).append('\n');
            }
            return sb.toString();
        }
    }

    private final Index index;
    private final List<String> rejectionLog = new ArrayList<>();

    public DependencyResolver(Index index) {
        this.index = index;
    }

    public List<String> rejectionLog() {
        return rejectionLog;
    }

    public ResolvedDependencyGraph resolve(ResolveRequest req) throws ResolveException {
        rejectionLog.clear();
        PackageId rootId = req.root().id();
        Set<TargetCapability> provided = TargetCapability.forTriple(req.triple());

        Map<PackageId, VersionConstraint> constraints = new LinkedHashMap<>();
        Map<PackageId, Set<String>> featureUnion = new LinkedHashMap<>();
        Map<PackageId, List<String>> requirePaths = new LinkedHashMap<>();

        List<Dependency> roots = req.root().effectiveDependencies(req.triple(), req.includeDev());
        for (Dependency d : roots) {
            if (d.optional() && (req.enabledFeatures() == null
                    || req.enabledFeatures().stream().noneMatch(f -> true))) {
                // optional without feature activation — skip unless default
                if (d.optional()) {
                    continue;
                }
            }
            // For 0.30: include optional only if not marked optional OR features requested
            if (d.optional() && (req.enabledFeatures() == null || req.enabledFeatures().isEmpty())) {
                continue;
            }
            mergeConstraint(constraints, d.id(), d.constraint(), requirePaths, rootId.coordinate());
            featureUnion.computeIfAbsent(d.id(), k -> new LinkedHashSet<>()).addAll(d.features());
        }

        Map<PackageId, PackageMeta> chosen = new LinkedHashMap<>();
        if (!search(new ArrayList<>(constraints.keySet()), 0, constraints, featureUnion,
                requirePaths, chosen, provided, req.allowYanked(), rootId.coordinate())) {
            throw new ResolveException(buildConflict(constraints, requirePaths, chosen));
        }

        ResolvedDependencyGraph graph = new ResolvedDependencyGraph(rootId);
        for (Map.Entry<PackageId, PackageMeta> e : chosen.entrySet()) {
            PackageMeta meta = e.getValue();
            // validate capabilities
            String missing = TargetCapability.describeMissing(meta.requires(), provided);
            if (!missing.isEmpty()) {
                throw new ResolveException(
                        "error:\npackage " + meta.id()
                                + "\nrequires " + missing
                                + "\n\ntarget " + req.triple()
                                + "\ndoes not provide " + missing);
            }
            Set<String> feats = new LinkedHashSet<>(featureUnion.getOrDefault(e.getKey(), Set.of()));
            feats.addAll(meta.features());
            ResolvedPackage rp = new ResolvedPackage(
                    meta.id(), meta.version(), meta.source(), meta.contentHash(),
                    feats, meta.yanked(), meta.requires(), meta.packageType(), meta.path());
            rp.nativeLibraries().addAll(meta.nativeLibraries());
            for (Dependency dep : meta.dependencies()) {
                if (chosen.containsKey(dep.id())) {
                    rp.dependencies().add(dep.id());
                }
            }
            graph.put(rp);
            graph.explain().add(meta.id().coordinate() + " " + meta.version() + " selected");
        }
        return graph;
    }

    private boolean search(
            List<PackageId> queue,
            int idx,
            Map<PackageId, VersionConstraint> constraints,
            Map<PackageId, Set<String>> featureUnion,
            Map<PackageId, List<String>> requirePaths,
            Map<PackageId, PackageMeta> chosen,
            Set<TargetCapability> provided,
            boolean allowYanked,
            String rootPath
    ) {
        if (idx >= queue.size()) {
            return true;
        }
        PackageId id = queue.get(idx);
        if (chosen.containsKey(id)) {
            PackageMeta cur = chosen.get(id);
            if (!constraints.get(id).matches(cur.version())) {
                rejectionLog.add(id + "@" + cur.version() + " rejected: violates " + constraints.get(id));
                return false;
            }
            return search(queue, idx + 1, constraints, featureUnion, requirePaths, chosen, provided, allowYanked, rootPath);
        }

        VersionConstraint c = constraints.get(id);
        List<PackageMeta> candidates = new ArrayList<>(index.versions(id));
        candidates.sort(Comparator.comparing(PackageMeta::version).reversed());

        for (PackageMeta cand : candidates) {
            if (!allowYanked && cand.yanked()) {
                rejectionLog.add(id + "@" + cand.version() + " rejected: yanked");
                continue;
            }
            if (!c.matches(cand.version())) {
                rejectionLog.add(id + "@" + cand.version() + " rejected: does not match " + c);
                continue;
            }
            String missing = TargetCapability.describeMissing(cand.requires(), provided);
            if (!missing.isEmpty()) {
                rejectionLog.add(id + "@" + cand.version() + " rejected: missing capabilities " + missing);
                continue;
            }

            // propagate transitive constraints
            Map<PackageId, VersionConstraint> nextConstraints = new LinkedHashMap<>(constraints);
            Map<PackageId, List<String>> nextPaths = copyPaths(requirePaths);
            Map<PackageId, Set<String>> nextFeatures = copyFeatures(featureUnion);
            boolean ok = true;
            List<PackageId> newlyQueued = new ArrayList<>();
            for (Dependency dep : cand.dependencies()) {
                if (dep.optional()) {
                    continue;
                }
                VersionConstraint existing = nextConstraints.get(dep.id());
                if (existing == null) {
                    nextConstraints.put(dep.id(), dep.constraint());
                    nextPaths.computeIfAbsent(dep.id(), k -> new ArrayList<>())
                            .add(rootPath + " → " + id.coordinate() + " " + cand.version()
                                    + " → " + dep.id().coordinate() + " " + dep.constraint());
                    newlyQueued.add(dep.id());
                } else {
                    // intersect by keeping both via AND in VersionConstraint — encode as sequential clauses
                    VersionConstraint merged = VersionConstraint.parse(
                            existing.raw() + " " + dep.constraint().raw());
                    // Check if any candidate version satisfies both
                    boolean any = false;
                    for (PackageMeta v : index.versions(dep.id())) {
                        if (existing.matches(v.version()) && dep.constraint().matches(v.version())) {
                            any = true;
                            break;
                        }
                    }
                    if (!any) {
                        rejectionLog.add("conflict on " + dep.id() + ": " + existing + " vs " + dep.constraint());
                        nextPaths.computeIfAbsent(dep.id(), k -> new ArrayList<>())
                                .add(rootPath + " → " + id + " " + cand.version()
                                        + " → " + dep.id() + " " + dep.constraint());
                        ok = false;
                        break;
                    }
                    nextConstraints.put(dep.id(), merged);
                    nextPaths.computeIfAbsent(dep.id(), k -> new ArrayList<>())
                            .add(rootPath + " → " + id.coordinate() + " " + cand.version()
                                    + " → " + dep.id().coordinate() + " " + dep.constraint());
                }
                nextFeatures.computeIfAbsent(dep.id(), k -> new LinkedHashSet<>()).addAll(dep.features());
            }
            if (!ok) {
                continue;
            }

            List<PackageId> nextQueue = new ArrayList<>(queue);
            for (PackageId nq : newlyQueued) {
                if (!nextQueue.contains(nq)) {
                    nextQueue.add(nq);
                }
            }

            chosen.put(id, cand);
            if (search(nextQueue, idx + 1, nextConstraints, nextFeatures, nextPaths, chosen, provided, allowYanked, rootPath)) {
                featureUnion.clear();
                featureUnion.putAll(nextFeatures);
                constraints.clear();
                constraints.putAll(nextConstraints);
                requirePaths.clear();
                requirePaths.putAll(nextPaths);
                return true;
            }
            chosen.remove(id);
            rejectionLog.add(id + "@" + cand.version() + " rejected: backtrack");
        }
        return false;
    }

    private static void mergeConstraint(
            Map<PackageId, VersionConstraint> constraints,
            PackageId id,
            VersionConstraint c,
            Map<PackageId, List<String>> paths,
            String from
    ) {
        VersionConstraint existing = constraints.get(id);
        if (existing == null) {
            constraints.put(id, c);
        } else {
            constraints.put(id, VersionConstraint.parse(existing.raw() + " " + c.raw()));
        }
        paths.computeIfAbsent(id, k -> new ArrayList<>())
                .add(from + " → " + id.coordinate() + " " + c);
    }

    private ResolveFailure buildConflict(
            Map<PackageId, VersionConstraint> constraints,
            Map<PackageId, List<String>> paths,
            Map<PackageId, PackageMeta> chosen
    ) {
        PackageId conflict = null;
        for (PackageId id : constraints.keySet()) {
            if (!chosen.containsKey(id)) {
                conflict = id;
                break;
            }
        }
        if (conflict == null && !constraints.isEmpty()) {
            conflict = constraints.keySet().iterator().next();
        }
        List<String> pathList = conflict == null ? List.of() : paths.getOrDefault(conflict, List.of());
        String msg = "Dependency conflict: " + (conflict == null ? "?" : conflict.coordinate())
                + "\n\nNo version satisfies all constraints.";
        List<String> pretty = new ArrayList<>();
        for (String p : pathList) {
            pretty.add(p.replace(" → ", "\n└── "));
        }
        if (pretty.isEmpty()) {
            pretty.addAll(rejectionLog.stream().limit(20).toList());
        }
        return new ResolveFailure(msg, pretty);
    }

    private static Map<PackageId, List<String>> copyPaths(Map<PackageId, List<String>> in) {
        Map<PackageId, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<PackageId, List<String>> e : in.entrySet()) {
            out.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return out;
    }

    private static Map<PackageId, Set<String>> copyFeatures(Map<PackageId, Set<String>> in) {
        Map<PackageId, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<PackageId, Set<String>> e : in.entrySet()) {
            out.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }
        return out;
    }

    public static final class ResolveException extends Exception {
        private final ResolveFailure failure;

        public ResolveException(ResolveFailure failure) {
            super(failure.toString());
            this.failure = failure;
        }

        public ResolveException(String message) {
            super(message);
            this.failure = new ResolveFailure(message, List.of());
        }

        public ResolveFailure failure() {
            return failure;
        }
    }
}
