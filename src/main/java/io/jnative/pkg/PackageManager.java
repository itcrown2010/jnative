package io.jnative.pkg;

import io.jnative.incremental.ContentHasher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Programmatic package manager — used by CLI, Studio, and build system.
 * Compiler never talks to the registry; it receives a resolved graph only.
 * @author 张晓达
 */
public final class PackageManager {
    private final Path projectRoot;
    private final PackageCache cache;
    private final CompositeRegistry registries;
    private final Path manifestPath;
    private final Path lockPath;
    private boolean offline;

    public PackageManager(Path projectRoot) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        Path cacheRoot = this.projectRoot.resolve(".jnative/pkg-cache");
        // also allow shared user cache via env
        String shared = System.getenv("JNATIVE_PKG_CACHE");
        if (shared != null && !shared.isBlank()) {
            cacheRoot = Path.of(shared);
        }
        this.cache = new PackageCache(cacheRoot);
        this.registries = new CompositeRegistry();
        Path localReg = this.projectRoot.resolve(".jnative/registry");
        if (Files.isDirectory(localReg)) {
            registries.add(new FilesystemRegistry(localReg, "project-local"));
        }
        Path repoReg = this.projectRoot.resolve("registry");
        if (Files.isDirectory(repoReg)) {
            registries.add(new FilesystemRegistry(repoReg, "repo"));
        }
        // default demo registry under packages/registry if present
        Path demo = this.projectRoot.resolve("packages/registry");
        if (Files.isDirectory(demo)) {
            registries.add(new FilesystemRegistry(demo, "demo"));
        }
        this.manifestPath = Files.isRegularFile(this.projectRoot.resolve("jnative.toml"))
                ? this.projectRoot.resolve("jnative.toml")
                : this.projectRoot.resolve("jnative.toml");
        this.lockPath = this.projectRoot.resolve("jnative.lock");
    }

    public PackageManager addRegistry(PackageRegistry registry) {
        // prepend for precedence
        CompositeRegistry fresh = new CompositeRegistry();
        fresh.add(registry);
        for (PackageRegistry r : registries.registries()) {
            fresh.add(r);
        }
        registries.registries().clear();
        registries.registries().addAll(fresh.registries());
        return this;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public PackageCache cache() {
        return cache;
    }

    public CompositeRegistry registries() {
        return registries;
    }

    public PackageManifest loadManifest() throws IOException {
        if (!Files.isRegularFile(manifestPath)) {
            PackageManifest m = new PackageManifest();
            m.setId(PackageId.parse("jnative/" + projectRoot.getFileName()));
            m.setVersion(SemanticVersion.parse("0.1.0"));
            return m;
        }
        return ManifestFile.load(manifestPath);
    }

    public void saveManifest(PackageManifest m) throws IOException {
        ManifestFile.saveToml(manifestPath, m);
    }

    public String manifestFingerprint(PackageManifest m) throws IOException {
        if (Files.isRegularFile(manifestPath)) {
            return ContentHasher.sha256(Files.readAllBytes(manifestPath));
        }
        return ContentHasher.sha256(m.id() + "@" + m.version());
    }

    public ResolvedDependencyGraph resolve(String triple, boolean includeDev, boolean force)
            throws Exception {
        PackageManifest manifest = loadManifest();
        String mfp = manifestFingerprint(manifest);
        if (!force && Files.isRegularFile(lockPath)) {
            Lockfile lock = Lockfile.load(lockPath);
            if (mfp.equals(lock.manifestFingerprint()) && !lock.entries().isEmpty()) {
                ResolvedDependencyGraph g = lock.toGraph(manifest.id());
                ensureCached(g);
                return g;
            }
        }
        DependencyResolver resolver = new DependencyResolver(registries.asIndex());
        ResolvedDependencyGraph graph = resolver.resolve(new DependencyResolver.ResolveRequest(
                manifest, triple, includeDev, false, Set.of()));
        // download + verify
        for (ResolvedPackage p : new ArrayList<>(graph.packages().values())) {
            installPackage(p);
        }
        Lockfile lock = Lockfile.fromGraph(graph, mfp);
        lock.save(lockPath);
        return graph;
    }

    public void ensureCached(ResolvedDependencyGraph graph) throws IOException {
        for (ResolvedPackage p : graph.packages().values()) {
            if (p.contentHash().isEmpty()) {
                continue;
            }
            if (cache.has(p.contentHash())) {
                continue;
            }
            if (offline) {
                throw new IOException("package missing from local cache (offline): "
                        + p.id() + "@" + p.version() + " hash=" + p.contentHash());
            }
            installPackage(p);
        }
    }

    public Path installPackage(ResolvedPackage p) throws IOException {
        if (p.contentHash().isEmpty() && !p.path().isEmpty()) {
            // path dependency — already on disk
            return Path.of(p.path());
        }
        if (!p.contentHash().isEmpty() && cache.has(p.contentHash())) {
            return cache.ensureExtracted(p.contentHash());
        }
        if (offline) {
            throw new IOException("package missing from local cache (offline): " + p.id());
        }
        byte[] data = registries.download(p.id(), p.version());
        String hash = PackageArchive.contentHash(data);
        if (!p.contentHash().isEmpty() && !p.contentHash().equals(hash)) {
            throw new IOException("SECURITY ERROR: integrity failure for " + p.id()
                    + "@" + p.version() + " expected=" + p.contentHash() + " actual=" + hash);
        }
        cache.putArchive(hash, data);
        return cache.ensureExtracted(hash);
    }

    public void add(String nameOrCoord, String constraint) throws Exception {
        PackageManifest m = loadManifest();
        PackageId id = PackageId.parse(nameOrCoord.contains("/") ? nameOrCoord : "jnative/" + nameOrCoord);
        String c = constraint == null || constraint.isBlank() ? "^0.1.0" : constraint;
        // pick latest matching from registry to set a sensible default constraint
        List<SemanticVersion> vers = registries.versions(id);
        if (!vers.isEmpty() && (constraint == null || constraint.isBlank())) {
            c = "^" + vers.get(0).coreString();
        }
        m.dependencies().removeIf(d -> d.id().equals(id));
        m.dependencies().add(Dependency.registry(id, c));
        saveManifest(m);
        resolve("native", false, true);
    }

    public void remove(String nameOrCoord) throws Exception {
        PackageManifest m = loadManifest();
        PackageId id = PackageId.parse(nameOrCoord.contains("/") ? nameOrCoord : "jnative/" + nameOrCoord);
        m.dependencies().removeIf(d -> d.id().equals(id));
        m.devDependencies().removeIf(d -> d.id().equals(id));
        for (List<Dependency> list : m.targetDependencies().values()) {
            list.removeIf(d -> d.id().equals(id));
        }
        saveManifest(m);
        resolve("native", false, true);
    }

    public void update(String nameOrCoordOrNull) throws Exception {
        PackageManifest m = loadManifest();
        // Force re-resolve; if specific package, bump constraint upper by clearing lock entry conceptually
        if (nameOrCoordOrNull != null && !nameOrCoordOrNull.isBlank()) {
            PackageId id = PackageId.parse(nameOrCoordOrNull.contains("/")
                    ? nameOrCoordOrNull : "jnative/" + nameOrCoordOrNull);
            List<SemanticVersion> vers = registries.versions(id);
            if (!vers.isEmpty()) {
                for (int i = 0; i < m.dependencies().size(); i++) {
                    Dependency d = m.dependencies().get(i);
                    if (d.id().equals(id)) {
                        m.dependencies().set(i, Dependency.registry(id, "^" + vers.get(0).coreString()));
                    }
                }
                saveManifest(m);
            }
        }
        if (Files.exists(lockPath)) {
            Files.delete(lockPath);
        }
        resolve("native", false, true);
    }

    public String explain(String nameOrCoord) throws Exception {
        ResolvedDependencyGraph g = resolve("native", false, false);
        PackageId id = PackageId.parse(nameOrCoord.contains("/") ? nameOrCoord : "jnative/" + nameOrCoord);
        ResolvedPackage p = g.get(id);
        if (p == null) {
            return id + " is not resolved";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(id.coordinate()).append(' ').append(p.version()).append(" selected\n\nbecause:\n");
        PackageManifest m = loadManifest();
        for (Dependency d : m.dependencies()) {
            if (d.id().equals(id)) {
                sb.append(m.id().coordinate()).append(" requires ").append(d.constraint()).append('\n');
            }
        }
        sb.append(g.why(id));
        return sb.toString();
    }

    public String depsTree() throws Exception {
        return resolve("native", false, false).tree();
    }

    public String why(String nameOrCoord) throws Exception {
        PackageId id = PackageId.parse(nameOrCoord.contains("/") ? nameOrCoord : "jnative/" + nameOrCoord);
        return resolve("native", false, false).why(id);
    }

    public List<PackageId> search(String q) throws IOException {
        return registries.search(q);
    }

    public String info(String nameOrCoord) throws IOException {
        PackageId id = PackageId.parse(nameOrCoord.contains("/") ? nameOrCoord : "jnative/" + nameOrCoord);
        StringBuilder sb = new StringBuilder();
        sb.append(id.coordinate()).append('\n');
        List<SemanticVersion> vers = registries.versions(id);
        sb.append("versions: ").append(vers).append('\n');
        if (!vers.isEmpty()) {
            var meta = registries.metadata(id, vers.get(0));
            if (meta.isPresent()) {
                DependencyResolver.PackageMeta m = meta.get();
                sb.append("type: ").append(m.packageType()).append('\n');
                sb.append("source: ").append(m.source()).append('\n');
                sb.append("hash: sha256:").append(m.contentHash()).append('\n');
                sb.append("requires: ").append(m.requires()).append('\n');
                sb.append("features: ").append(m.features()).append('\n');
                sb.append("dependencies: ").append(m.dependencies()).append('\n');
                if (!m.nativeLibraries().isEmpty()) {
                    sb.append("NATIVE BINARIES:\n");
                    for (var n : m.nativeLibraries()) {
                        sb.append("  ").append(n.platform()).append(" ").append(n.name())
                                .append(" link=").append(n.link()).append('\n');
                    }
                }
                if (m.yanked()) {
                    sb.append("YANKED\n");
                }
            }
        }
        // typosquat warning for official-looking names
        if (!id.namespace().equals("jnative") && looksLikeOfficial(id.name())) {
            sb.append("warning: name resembles official jnative/* package — verify publisher\n");
        }
        return sb.toString();
    }

    private static boolean looksLikeOfficial(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("jnative") || n.equals("json") || n.equals("postgres")
                || n.equals("webui") || n.equals("http") || n.equals("rpc");
    }

    public String licenses() throws Exception {
        ResolvedDependencyGraph g = resolve("native", false, false);
        StringBuilder sb = new StringBuilder();
        for (ResolvedPackage p : g.packages().values()) {
            sb.append(p.id().coordinate()).append('@').append(p.version()).append("  license=UNKNOWN\n");
            Path extracted = p.contentHash().isEmpty() ? null : cache.extractedPath(p.contentHash());
            if (extracted != null && Files.isDirectory(extracted)) {
                Path toml = extracted.resolve("jnative.toml");
                if (Files.isRegularFile(toml)) {
                    PackageManifest m = ManifestFile.load(toml);
                    if (!m.license().isEmpty()) {
                        // rewrite last line
                    }
                }
            }
        }
        // Better pass
        sb.setLength(0);
        PackageManifest root = loadManifest();
        sb.append(root.id()).append('@').append(root.version())
                .append("  license=").append(root.license().isEmpty() ? "?" : root.license()).append('\n');
        for (ResolvedPackage p : g.packages().values()) {
            String lic = "?";
            try {
                Path dir = installPackage(p);
                Path toml = dir.resolve("jnative.toml");
                if (Files.isRegularFile(toml)) {
                    lic = ManifestFile.load(toml).license();
                    if (lic.isEmpty()) {
                        lic = "?";
                    }
                }
            } catch (IOException ignored) {
            }
            sb.append(p.id()).append('@').append(p.version()).append("  license=").append(lic).append('\n');
        }
        return sb.toString();
    }

    public List<String> audit() throws Exception {
        List<String> findings = new ArrayList<>();
        if (!Files.isRegularFile(lockPath)) {
            findings.add("no lockfile");
            return findings;
        }
        Lockfile lock = Lockfile.load(lockPath);
        Set<String> seen = new LinkedHashSet<>();
        for (Lockfile.LockEntry e : lock.entries().values()) {
            if (!seen.add(e.id())) {
                findings.add("duplicate package identity: " + e.id());
            }
            PackageId id = PackageId.parse(e.id());
            SemanticVersion ver = SemanticVersion.parse(e.version());
            if (registries.isYanked(id, ver)) {
                findings.add("known yanked version in lock: " + e.id() + "@" + e.version()
                        + " (still buildable; avoid for new resolves)");
            }
            if (e.contentHash().isEmpty()) {
                findings.add("missing content hash: " + e.id());
            } else if (cache.has(e.contentHash())) {
                try {
                    byte[] data = cache.getArchive(e.contentHash());
                    if (data != null && !ContentHasher.sha256(data).equals(e.contentHash())) {
                        findings.add("SECURITY ERROR: hash mismatch in cache for " + e.id());
                    }
                } catch (IOException ex) {
                    findings.add(ex.getMessage());
                }
            }
            if (e.source() == null || e.source().isBlank()) {
                findings.add("untrusted/unspecified source: " + e.id());
            }
            if ("native".equals(e.packageType())) {
                findings.add("native binary package present: " + e.id() + " (review FFI trust)");
            }
        }
        if (findings.isEmpty()) {
            findings.add("ok: no issues found");
        }
        return findings;
    }

    public PublishResult publish(boolean dryRun) throws Exception {
        PackageManifest m = loadManifest();
        validateForPublish(m);
        List<String> files = PackageArchive.listDryRun(projectRoot);
        if (dryRun) {
            return new PublishResult(true, m.id(), m.version(), "", files, true);
        }
        byte[] archive = PackageArchive.pack(projectRoot);
        String hash = PackageArchive.contentHash(archive);
        DependencyResolver.PackageMeta meta = new DependencyResolver.PackageMeta(
                m.id(), m.version(), m.dependencies(), m.requires(), m.features(),
                hash, registries.url(), false, m.packageType(), projectRoot.toString(),
                m.nativeLibraries());
        registries.publish(m.id(), m.version(), archive, meta);
        return new PublishResult(true, m.id(), m.version(), hash, files, false);
    }

    private void validateForPublish(PackageManifest m) throws IOException {
        if (m.id().name().isBlank()) {
            throw new IOException("package name required");
        }
        if (m.license().isBlank()) {
            throw new IOException("license required for publish");
        }
        if (Files.exists(projectRoot.resolve(".env"))) {
            throw new IOException("refusing to publish: .env present (secrets)");
        }
        // scan for obvious secrets in dry-list
        for (String f : PackageArchive.listDryRun(projectRoot)) {
            String lower = f.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pem") || lower.contains("id_rsa") || lower.endsWith(".p12")) {
                throw new IOException("refusing to publish secret-like file: " + f);
            }
        }
    }

    public record PublishResult(
            boolean ok,
            PackageId id,
            SemanticVersion version,
            String hash,
            List<String> files,
            boolean dryRun
    ) {
    }

    /** Path dependency roots for the 0.29 file watcher. */
    public List<Path> pathDependencyRoots() throws IOException {
        PackageManifest m = loadManifest();
        List<Path> out = new ArrayList<>();
        for (Dependency d : m.dependencies()) {
            if (d.kind() == Dependency.Kind.PATH && !d.path().isEmpty()) {
                out.add(projectRoot.resolve(d.path()).normalize());
            }
        }
        return out;
    }
}
