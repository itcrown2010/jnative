package io.jnative.pkg;

import io.jnative.incremental.ContentHasher;
import io.jnative.incremental.UnitFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Bridges resolved packages into the 0.29 build graph as first-class nodes.
 * Compiler receives paths only — never contacts the registry.
 * @author 张晓达
 */
public final class BuildGraphPackageIntegration {
    public record PackageBuildNode(
            PackageId id,
            SemanticVersion version,
            Path root,
            String contentHash,
            String publicApiFingerprint,
            String implementationFingerprint,
            List<Path> sources,
            List<PackageManifest.NativeLibrarySpec> nativeLibraries,
            java.util.Set<TargetCapability> requires
    ) {
    }

    private BuildGraphPackageIntegration() {
    }

    public static List<PackageBuildNode> materialize(
            ResolvedDependencyGraph graph,
            PackageCache cache
    ) throws IOException {
        List<PackageBuildNode> nodes = new ArrayList<>();
        for (ResolvedPackage p : graph.packages().values()) {
            Path root;
            // Prefer content-addressed cache extraction. Registry metadata may set
            // path to the version directory (which holds package.jnpkg, not sources).
            if (!p.contentHash().isEmpty()) {
                root = cache.ensureExtracted(p.contentHash());
            } else if (!p.path().isEmpty() && Files.isDirectory(Path.of(p.path()))) {
                root = Path.of(p.path());
            } else {
                continue;
            }
            List<Path> sources = listJava(root);
            StringBuilder api = new StringBuilder();
            StringBuilder impl = new StringBuilder();
            for (Path src : sources) {
                String text = Files.readString(src);
                UnitFingerprint fp = UnitFingerprint.fromSource(
                        root.relativize(src).toString(), text);
                api.append(fp.publicApiHash()).append(';');
                impl.append(fp.implementationHash()).append(';');
            }
            nodes.add(new PackageBuildNode(
                    p.id(),
                    p.version(),
                    root,
                    p.contentHash(),
                    ContentHasher.sha256(api.toString()),
                    ContentHasher.sha256(impl.toString()),
                    sources,
                    new ArrayList<>(p.nativeLibraries()),
                    p.requires()
            ));
        }
        return nodes;
    }

    /** Source roots to pass into compilation — never copied into project/src. */
    public static List<Path> sourceRoots(List<PackageBuildNode> nodes) {
        List<Path> out = new ArrayList<>();
        for (PackageBuildNode n : nodes) {
            Path src = n.root().resolve("src");
            if (Files.isDirectory(src)) {
                out.add(src);
            } else {
                out.add(n.root());
            }
        }
        return out;
    }

    public static Map<String, String> fingerprints(List<PackageBuildNode> nodes) {
        Map<String, String> m = new LinkedHashMap<>();
        for (PackageBuildNode n : nodes) {
            m.put(n.id().coordinate(), n.contentHash() + ":" + n.publicApiFingerprint());
        }
        return m;
    }

    public static void validateCapabilities(List<PackageBuildNode> nodes, String triple) {
        var provided = TargetCapability.forTriple(triple);
        for (PackageBuildNode n : nodes) {
            String missing = TargetCapability.describeMissing(n.requires(), provided);
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "error:\npackage " + n.id()
                                + "\nrequires " + missing
                                + "\n\ntarget " + triple
                                + "\ndoes not provide " + missing);
            }
        }
    }

    private static List<Path> listJava(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(io.jnative.source.SourceFiles::isSourcePath)
                    .filter(Files::isRegularFile)
                    .forEach(out::add);
        }
        return out;
    }
}
