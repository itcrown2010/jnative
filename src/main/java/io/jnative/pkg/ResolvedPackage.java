package io.jnative.pkg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One resolved package node in the dependency graph.
 * @author 张晓达
 */
public final class ResolvedPackage {
    private final PackageId id;
    private final SemanticVersion version;
    private final String source;
    private final String contentHash;
    private final Set<String> features = new LinkedHashSet<>();
    private final List<PackageId> dependencies = new ArrayList<>();
    private final String path; // local extracted / path dep
    private final boolean yanked;
    private final Set<TargetCapability> requires = new LinkedHashSet<>();
    private final String packageType;
    private final List<PackageManifest.NativeLibrarySpec> nativeLibraries = new ArrayList<>();

    public ResolvedPackage(
            PackageId id,
            SemanticVersion version,
            String source,
            String contentHash,
            Set<String> features,
            boolean yanked,
            Set<TargetCapability> requires,
            String packageType,
            String path
    ) {
        this.id = Objects.requireNonNull(id);
        this.version = Objects.requireNonNull(version);
        this.source = source == null ? "" : source;
        this.contentHash = contentHash == null ? "" : contentHash;
        if (features != null) {
            this.features.addAll(features);
        }
        this.yanked = yanked;
        if (requires != null) {
            this.requires.addAll(requires);
        }
        this.packageType = packageType == null ? "source" : packageType;
        this.path = path == null ? "" : path;
    }

    public PackageId id() {
        return id;
    }

    public SemanticVersion version() {
        return version;
    }

    public String source() {
        return source;
    }

    public String contentHash() {
        return contentHash;
    }

    public Set<String> features() {
        return features;
    }

    public List<PackageId> dependencies() {
        return dependencies;
    }

    public String path() {
        return path;
    }

    public boolean yanked() {
        return yanked;
    }

    public Set<TargetCapability> requires() {
        return requires;
    }

    public String packageType() {
        return packageType;
    }

    public List<PackageManifest.NativeLibrarySpec> nativeLibraries() {
        return nativeLibraries;
    }

    public String coordinateVersion() {
        return id.coordinate() + "@" + version;
    }
}
