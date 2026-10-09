package io.jnative.pkg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** One declared dependency edge.
 * @author 张晓达
 */
public final class Dependency {
    public enum Kind {
        REGISTRY, PATH, GIT, LOCAL_ARCHIVE, WORKSPACE
    }

    private final PackageId id;
    private final VersionConstraint constraint;
    private final Kind kind;
    private final String path;
    private final String gitUrl;
    private final String gitRev;
    private final Set<String> features = new LinkedHashSet<>();
    private final boolean optional;
    private final boolean dev;
    private final String targetScope; // "" | native | browser | desktop

    public Dependency(
            PackageId id,
            VersionConstraint constraint,
            Kind kind,
            String path,
            String gitUrl,
            String gitRev,
            Set<String> features,
            boolean optional,
            boolean dev,
            String targetScope
    ) {
        this.id = Objects.requireNonNull(id);
        this.constraint = constraint == null ? VersionConstraint.parse("*") : constraint;
        this.kind = kind == null ? Kind.REGISTRY : kind;
        this.path = path == null ? "" : path;
        this.gitUrl = gitUrl == null ? "" : gitUrl;
        this.gitRev = gitRev == null ? "" : gitRev;
        if (features != null) {
            this.features.addAll(features);
        }
        this.optional = optional;
        this.dev = dev;
        this.targetScope = targetScope == null ? "" : targetScope;
    }

    public static Dependency registry(PackageId id, String constraint) {
        return new Dependency(id, VersionConstraint.parse(constraint), Kind.REGISTRY,
                "", "", "", Set.of(), false, false, "");
    }

    public static Dependency path(PackageId id, String path) {
        return new Dependency(id, VersionConstraint.parse("*"), Kind.PATH,
                path, "", "", Set.of(), false, false, "");
    }

    public PackageId id() {
        return id;
    }

    public VersionConstraint constraint() {
        return constraint;
    }

    public Kind kind() {
        return kind;
    }

    public String path() {
        return path;
    }

    public String gitUrl() {
        return gitUrl;
    }

    public String gitRev() {
        return gitRev;
    }

    public Set<String> features() {
        return features;
    }

    public boolean optional() {
        return optional;
    }

    public boolean dev() {
        return dev;
    }

    public String targetScope() {
        return targetScope;
    }

    public Dependency withFeatures(Set<String> more) {
        Set<String> f = new LinkedHashSet<>(features);
        f.addAll(more);
        return new Dependency(id, constraint, kind, path, gitUrl, gitRev, f, optional, dev, targetScope);
    }

    public Dependency withTargetScope(String scope) {
        return new Dependency(id, constraint, kind, path, gitUrl, gitRev, features, optional, dev, scope);
    }

    public Dependency asDev() {
        return new Dependency(id, constraint, kind, path, gitUrl, gitRev, features, optional, true, targetScope);
    }

    public Dependency asOptional() {
        return new Dependency(id, constraint, kind, path, gitUrl, gitRev, features, true, dev, targetScope);
    }

    @Override
    public String toString() {
        return id + " " + constraint + (path.isEmpty() ? "" : " path=" + path);
    }
}
