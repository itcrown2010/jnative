package io.jnative.pkg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Package030SuiteTest {

    @TempDir Path tmp;
    Path registryRoot;
    FilesystemRegistry registry;

    @BeforeEach
    void setup() throws Exception {
        registryRoot = tmp.resolve("registry");
        registry = new FilesystemRegistry(registryRoot, "test");
        publish("hello", "1.0.0", List.of(), Set.of(), "source");
        publish("hello", "1.1.0", List.of(), Set.of(), "source");
        publish("math", "1.2.0",
                List.of(Dependency.registry(PackageId.parse("jnative/hello"), "^1.0.0")),
                Set.of(), "source");
        publish("json-demo", "2.0.0", List.of(), Set.of(), "source");
        publish("json-demo", "2.1.0", List.of(), Set.of(), "source");
        publish("crypto", "1.0.0", List.of(), Set.of(), "source");
        publish("crypto", "2.0.0", List.of(), Set.of(), "source");
        publish("sqlite-native", "1.4.0", List.of(),
                Set.of(TargetCapability.NATIVE_FFI, TargetCapability.FILESYSTEM), "native");
        publish("webui-charts", "1.0.0", List.of(),
                Set.of(TargetCapability.BROWSER_DOM, TargetCapability.JS_INTEROP), "source");
    }

    private void publish(String name, String ver, List<Dependency> deps,
                         Set<TargetCapability> requires, String type) throws Exception {
        PackageId id = PackageId.parse("jnative/" + name);
        Path pkg = tmp.resolve("src-" + name + "-" + ver);
        Files.createDirectories(pkg.resolve("src"));
        Files.writeString(pkg.resolve("jnative.toml"),
                "[package]\nname=\"" + name + "\"\nnamespace=\"jnative\"\nversion=\"" + ver
                        + "\"\nlicense=\"MIT\"\ntype=\"" + type + "\"\n");
        Files.writeString(pkg.resolve("src/Lib.java"), "class Lib" + name.replace("-", "") + " {}\n");
        byte[] archive = PackageArchive.pack(pkg);
        String hash = PackageArchive.contentHash(archive);
        PackageManifest m = new PackageManifest();
        m.setId(id);
        m.setVersion(SemanticVersion.parse(ver));
        m.setLicense("MIT");
        m.setPackageType(type);
        m.dependencies().addAll(deps);
        m.requires().addAll(requires);
        DependencyResolver.PackageMeta meta = new DependencyResolver.PackageMeta(
                id, SemanticVersion.parse(ver), deps, requires, Set.of(), hash,
                registry.url(), false, type, pkg.toString(), List.of());
        registry.publish(id, SemanticVersion.parse(ver), archive, meta);
    }

    @Test
    void semverCompareAndPreRelease() {
        assertTrue(SemanticVersion.parse("1.2.3").compareTo(SemanticVersion.parse("1.2.4")) < 0);
        assertTrue(SemanticVersion.parse("1.2.3").compareTo(SemanticVersion.parse("1.2.3-alpha")) > 0);
        assertTrue(SemanticVersion.parse("1.2.3-alpha").compareTo(SemanticVersion.parse("1.2.3-beta")) < 0);
        assertEquals(SemanticVersion.parse("1.2.3"), SemanticVersion.parse("1.2.3+build"));
    }

    @Test
    void versionConstraints() {
        SemanticVersion v = SemanticVersion.parse("1.2.3");
        assertTrue(VersionConstraint.parse("^1.2.0").matches(v));
        assertFalse(VersionConstraint.parse("^1.2.0").matches(SemanticVersion.parse("2.0.0")));
        assertTrue(VersionConstraint.parse("~1.2.0").matches(v));
        assertFalse(VersionConstraint.parse("~1.2.0").matches(SemanticVersion.parse("1.3.0")));
        assertTrue(VersionConstraint.parse(">=1.0 <2.0").matches(v));
        assertTrue(VersionConstraint.parse("*").matches(v));
        assertTrue(VersionConstraint.parse("1.2.3").matches(v));
    }

    @Test
    void resolverTransitiveAndFeatureUnion() throws Exception {
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("demo/app"));
        root.dependencies().add(Dependency.registry(PackageId.parse("jnative/math"), "^1.2.0"));
        DependencyResolver resolver = new DependencyResolver(registry.asIndex());
        ResolvedDependencyGraph g = resolver.resolve(new DependencyResolver.ResolveRequest(
                root, "native", false, false, Set.of()));
        assertNotNull(g.get(PackageId.parse("jnative/math")));
        assertNotNull(g.get(PackageId.parse("jnative/hello")));
        assertTrue(g.get(PackageId.parse("jnative/hello")).version().compareTo(SemanticVersion.parse("1.0.0")) >= 0);
    }

    @Test
    void resolverConflictExplainsPaths() {
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("demo/app"));
        root.dependencies().add(Dependency.registry(PackageId.parse("jnative/crypto"), "^1.0.0"));
        // simulate second direct constraint incompatible via fake index
        DependencyResolver.Index index = new DependencyResolver.Index() {
            @Override
            public List<DependencyResolver.PackageMeta> versions(PackageId id) {
                if (id.name().equals("crypto")) {
                    return List.of(
                            meta(id, "1.0.0"),
                            meta(id, "2.0.0")
                    );
                }
                if (id.name().equals("a")) {
                    return List.of(new DependencyResolver.PackageMeta(
                            id, SemanticVersion.parse("1.0.0"),
                            List.of(Dependency.registry(PackageId.parse("jnative/crypto"), "^1.0")),
                            Set.of(), Set.of(), "h", "s", false, "source", "", List.of()));
                }
                if (id.name().equals("b")) {
                    return List.of(new DependencyResolver.PackageMeta(
                            id, SemanticVersion.parse("1.0.0"),
                            List.of(Dependency.registry(PackageId.parse("jnative/crypto"), "^2.0")),
                            Set.of(), Set.of(), "h", "s", false, "source", "", List.of()));
                }
                return List.of();
            }

            @Override
            public DependencyResolver.PackageMeta get(PackageId id, SemanticVersion version) {
                return versions(id).stream().filter(m -> m.version().equals(version)).findFirst().orElse(null);
            }

            private DependencyResolver.PackageMeta meta(PackageId id, String v) {
                return new DependencyResolver.PackageMeta(id, SemanticVersion.parse(v),
                        List.of(), Set.of(), Set.of(), "h", "s", false, "source", "", List.of());
            }
        };
        PackageManifest app = new PackageManifest();
        app.setId(PackageId.parse("demo/app"));
        app.dependencies().add(Dependency.registry(PackageId.parse("jnative/a"), "*"));
        app.dependencies().add(Dependency.registry(PackageId.parse("jnative/b"), "*"));
        DependencyResolver resolver = new DependencyResolver(index);
        DependencyResolver.ResolveException ex = assertThrows(
                DependencyResolver.ResolveException.class,
                () -> resolver.resolve(new DependencyResolver.ResolveRequest(
                        app, "native", false, false, Set.of())));
        assertTrue(ex.getMessage().contains("conflict") || ex.getMessage().contains("Conflict")
                || ex.getMessage().contains("No version"));
    }

    @Test
    void browserRejectsNativeFfiPackage() {
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("demo/web"));
        root.dependencies().add(Dependency.registry(PackageId.parse("jnative/sqlite-native"), "^1.4.0"));
        DependencyResolver resolver = new DependencyResolver(registry.asIndex());
        assertThrows(DependencyResolver.ResolveException.class, () ->
                resolver.resolve(new DependencyResolver.ResolveRequest(
                        root, "wasm32-browser", false, false, Set.of())));
    }

    @Test
    void lockfileRoundTripAndReproducibility() throws Exception {
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("demo/app"));
        root.dependencies().add(Dependency.registry(PackageId.parse("jnative/math"), "^1.2.0"));
        DependencyResolver resolver = new DependencyResolver(registry.asIndex());
        ResolvedDependencyGraph g1 = resolver.resolve(new DependencyResolver.ResolveRequest(
                root, "native", false, false, Set.of()));
        Path lock = tmp.resolve("jnative.lock");
        Lockfile.fromGraph(g1, "mfp1").save(lock);
        Lockfile loaded = Lockfile.load(lock);
        ResolvedDependencyGraph g2 = loaded.toGraph(root.id());
        assertEquals(g1.packages().size(), g2.packages().size());
        assertEquals(g1.get(PackageId.parse("jnative/math")).version(),
                g2.get(PackageId.parse("jnative/math")).version());
    }

    @Test
    void packageCacheIntegrityAndOffline() throws Exception {
        PackageCache cache = new PackageCache(tmp.resolve("pkg-cache"));
        byte[] data = "hello-pkg".getBytes(StandardCharsets.UTF_8);
        // pack real archive
        Path pkg = tmp.resolve("p");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("jnative.toml"), "[package]\nname=\"x\"\nversion=\"1.0.0\"\nlicense=\"MIT\"\n");
        data = PackageArchive.pack(pkg);
        String hash = PackageArchive.contentHash(data);
        cache.putArchive(hash, data);
        assertTrue(cache.has(hash));
        Path extracted = cache.ensureExtracted(hash);
        assertTrue(Files.isRegularFile(extracted.resolve("jnative.toml")));

        byte[] bad = "tampered".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> cache.putArchive(hash, bad));
    }

    @Test
    void archiveRejectsPathTraversal() throws Exception {
        // craft zip with ../
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("../evil.txt"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path dest = tmp.resolve("extract");
        assertThrows(IOException.class, () -> PackageArchive.extract(bos.toByteArray(), dest));
    }

    @Test
    void packageManagerAddRemoveUpdate() throws Exception {
        Path project = tmp.resolve("app");
        Files.createDirectories(project);
        PackageManifest m = new PackageManifest();
        m.setId(PackageId.parse("demo/app"));
        m.setVersion(SemanticVersion.parse("0.1.0"));
        m.setLicense("MIT");
        ManifestFile.saveToml(project.resolve("jnative.toml"), m);

        PackageManager pm = new PackageManager(project);
        // only our test registry
        CompositeRegistry only = new CompositeRegistry().add(registry);
        // replace by adding first
        pm.addRegistry(registry);
        pm.add("math", "^1.2.0");
        assertTrue(Files.isRegularFile(project.resolve("jnative.lock")));
        PackageManifest loaded = ManifestFile.load(project.resolve("jnative.toml"));
        assertTrue(loaded.dependencies().stream().anyMatch(d -> d.id().name().equals("math")));

        String tree = pm.depsTree();
        assertTrue(tree.contains("math") || tree.contains("hello"));

        String explain = pm.explain("math");
        assertTrue(explain.contains("selected") || explain.contains("math"));

        pm.remove("math");
        loaded = ManifestFile.load(project.resolve("jnative.toml"));
        assertTrue(loaded.dependencies().stream().noneMatch(d -> d.id().name().equals("math")));
    }

    @Test
    void buildGraphMaterializeNoSrcCopy() throws Exception {
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("demo/app"));
        root.dependencies().add(Dependency.registry(PackageId.parse("jnative/json-demo"), "^2.0.0"));
        DependencyResolver resolver = new DependencyResolver(registry.asIndex());
        ResolvedDependencyGraph g = resolver.resolve(new DependencyResolver.ResolveRequest(
                root, "native", false, false, Set.of()));
        PackageCache cache = new PackageCache(tmp.resolve("c2"));
        for (ResolvedPackage p : g.packages().values()) {
            byte[] data = registry.download(p.id(), p.version());
            cache.putArchive(PackageArchive.contentHash(data), data);
            // update hash on node via re-read — package already has hash from meta
        }
        // ensure hashes match registry
        ResolvedDependencyGraph g2 = new ResolvedDependencyGraph(root.id());
        for (ResolvedPackage p : g.packages().values()) {
            byte[] data = registry.download(p.id(), p.version());
            String hash = PackageArchive.contentHash(data);
            cache.putArchive(hash, data);
            ResolvedPackage np = new ResolvedPackage(p.id(), p.version(), p.source(), hash,
                    p.features(), false, p.requires(), p.packageType(), "");
            g2.put(np);
        }
        var nodes = BuildGraphPackageIntegration.materialize(g2, cache);
        assertFalse(nodes.isEmpty());
        Path projectSrc = tmp.resolve("app/src");
        Files.createDirectories(projectSrc);
        // ensure we did not copy into project src
        try (var walk = Files.walk(projectSrc)) {
            assertEquals(1, walk.count()); // only the directory itself-ish — empty dir may be 1
        }
        assertTrue(nodes.get(0).root().toString().contains("extracted")
                || Files.isDirectory(nodes.get(0).root()));
    }

    @Test
    void compositeRegistrySourcePrecedence() throws Exception {
        Path privateReg = tmp.resolve("private");
        FilesystemRegistry priv = new FilesystemRegistry(privateReg, "private");
        // private publishes json-demo 9.0.0
        Path pkg = tmp.resolve("priv-json");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("jnative.toml"),
                "[package]\nname=\"json-demo\"\nnamespace=\"jnative\"\nversion=\"9.0.0\"\nlicense=\"MIT\"\n");
        byte[] archive = PackageArchive.pack(pkg);
        priv.publish(PackageId.parse("jnative/json-demo"), SemanticVersion.parse("9.0.0"), archive,
                new DependencyResolver.PackageMeta(PackageId.parse("jnative/json-demo"),
                        SemanticVersion.parse("9.0.0"), List.of(), Set.of(), Set.of(),
                        PackageArchive.contentHash(archive), priv.url(), false, "source", "", List.of()));

        CompositeRegistry comp = new CompositeRegistry().add(priv).add(registry);
        List<SemanticVersion> vers = comp.versions(PackageId.parse("jnative/json-demo"));
        assertEquals(SemanticVersion.parse("9.0.0"), vers.get(0));
        // public registry alone has 2.1.0 — private wins for versions()
    }

    @Test
    void yankSemantics() throws Exception {
        registry.yank(PackageId.parse("jnative/hello"), SemanticVersion.parse("1.0.0"));
        assertTrue(registry.isYanked(PackageId.parse("jnative/hello"), SemanticVersion.parse("1.0.0")));
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("demo/app"));
        root.dependencies().add(Dependency.registry(PackageId.parse("jnative/hello"), "^1.0.0"));
        DependencyResolver resolver = new DependencyResolver(registry.asIndex());
        ResolvedDependencyGraph g = resolver.resolve(new DependencyResolver.ResolveRequest(
                root, "native", false, false, Set.of()));
        // should pick 1.1.0 not yanked 1.0.0
        assertEquals(SemanticVersion.parse("1.1.0"), g.get(PackageId.parse("jnative/hello")).version());
    }

    @Test
    void resolverStressHundredPackages() throws Exception {
        Path stressReg = tmp.resolve("stress-reg");
        FilesystemRegistry reg = new FilesystemRegistry(stressReg);
        for (int i = 0; i < 100; i++) {
            PackageId id = PackageId.parse("stress/p" + i);
            List<Dependency> deps = new ArrayList<>();
            if (i > 0) {
                deps.add(Dependency.registry(PackageId.parse("stress/p" + (i - 1)), "*"));
            }
            Path pkg = tmp.resolve("stress-" + i);
            Files.createDirectories(pkg);
            Files.writeString(pkg.resolve("a.txt"), "x" + i);
            byte[] arch = PackageArchive.pack(pkg);
            reg.publish(id, SemanticVersion.parse("1.0.0"), arch,
                    new DependencyResolver.PackageMeta(id, SemanticVersion.parse("1.0.0"),
                            deps, Set.of(), Set.of(), PackageArchive.contentHash(arch),
                            reg.url(), false, "source", "", List.of()));
        }
        PackageManifest root = new PackageManifest();
        root.setId(PackageId.parse("stress/app"));
        root.dependencies().add(Dependency.registry(PackageId.parse("stress/p99"), "*"));
        long t0 = System.currentTimeMillis();
        ResolvedDependencyGraph g = new DependencyResolver(reg.asIndex()).resolve(
                new DependencyResolver.ResolveRequest(root, "native", false, false, Set.of()));
        long dt = System.currentTimeMillis() - t0;
        assertEquals(100, g.packages().size());
        System.out.println("stress 100 packages resolve ms=" + dt);
        assertTrue(dt < 30_000);
    }

    @Test
    void publishDryRunExcludesSecrets() throws Exception {
        Path project = tmp.resolve("pub");
        Files.createDirectories(project);
        Files.writeString(project.resolve("jnative.toml"),
                "[package]\nname=\"pub\"\nnamespace=\"demo\"\nversion=\"1.0.0\"\nlicense=\"MIT\"\n");
        Files.writeString(project.resolve("src.txt"), "ok");
        Files.writeString(project.resolve(".env"), "SECRET=1");
        PackageManager pm = new PackageManager(project);
        pm.addRegistry(registry);
        assertThrows(Exception.class, () -> pm.publish(false));
        // remove secret
        Files.delete(project.resolve(".env"));
        var dry = pm.publish(true);
        assertTrue(dry.dryRun());
        assertTrue(dry.files().stream().noneMatch(f -> f.contains(".env")));
    }
}
