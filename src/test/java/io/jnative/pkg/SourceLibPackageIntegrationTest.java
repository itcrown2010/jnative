package io.jnative.pkg;

import io.jnative.backend.Toolchain;
import io.jnative.cli.ProjectCli;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage for third-party pure source packages (.jnpkg):
 * local registry → lock → cache → compile → native run.
 */
class SourceLibPackageIntegrationTest {

    private static final String EXPECTED = "10\n0\n7\n6\n";

    private static final String MATH_UTILS = """
            class MathUtils {
                static int clamp(int value, int min, int max) {
                    if (value < min) {
                        return min;
                    }
                    if (value > max) {
                        return max;
                    }
                    return value;
                }

                static int gcd(int a, int b) {
                    while (b != 0) {
                        int remainder = a % b;
                        a = b;
                        b = remainder;
                    }
                    return a;
                }
            }
            """;

    private static final String MAIN_SRC = """
            class Main {
                public static void main(String[] args) {
                    System.out.println(MathUtils.clamp(15, 0, 10));
                    System.out.println(MathUtils.clamp(-3, 0, 10));
                    System.out.println(MathUtils.clamp(7, 0, 10));
                    System.out.println(MathUtils.gcd(48, 18));
                }
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void resolveLoadCompileAndRunFromTempRegistry() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path registryRoot = tmp.resolve("registry");
        Path lib = writeLibrary(tmp.resolve("library"), "1.0.0");
        Path app = writeApp(tmp.resolve("app"), true);
        FilesystemRegistry registry = new FilesystemRegistry(registryRoot, "test");
        publishLibrary(lib, registry);

        PackageManager pm = newPackageManager(app, registry);
        ResolvedDependencyGraph graph = pm.resolve("native", false, true);
        assertNotNull(graph.get(PackageId.parse("acme/math")));
        assertEquals(SemanticVersion.parse("1.0.0"), graph.get(PackageId.parse("acme/math")).version());
        assertTrue(Files.isRegularFile(app.resolve("jnative.lock")));

        var nodes = BuildGraphPackageIntegration.materialize(graph, pm.cache());
        List<Path> roots = BuildGraphPackageIntegration.sourceRoots(nodes);
        assertFalse(roots.isEmpty());
        assertTrue(Files.isRegularFile(roots.get(0).resolve("MathUtils.java"))
                || Files.walk(roots.get(0)).anyMatch(p -> p.getFileName().toString().equals("MathUtils.java")));

        // App must not contain a local copy of the library source.
        assertFalse(Files.exists(app.resolve("src/MathUtils.java")));

        ProjectCli.BuildOutcome outcome = ProjectCli.buildQuiet(
                app, false, 1, false, false, List.of(registryRoot));
        assertTrue(outcome.success(), "build should succeed with declared dependency");
        assertNotNull(outcome.primaryArtifact());
        assertEquals(EXPECTED, runNative(outcome.primaryArtifact()));
    }

    @Test
    void missingDependencyFailsCompileWithUnknownSymbol() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path app = writeApp(tmp.resolve("app-nodep"), false);
        // No jnative.toml dependencies — MathUtils is undefined.
        Files.writeString(app.resolve("jnative.toml"), """
                [package]
                name = "math-app"
                namespace = "acme"
                version = "0.1.0"
                license = "MIT"
                type = "source"
                """, StandardCharsets.UTF_8);

        io.jnative.incremental.CompilerSession session =
                new io.jnative.incremental.CompilerSession(app);
        var result = session.build();
        assertFalse(result.success());
        String all = String.join("\n", result.diagnostics()).toLowerCase();
        assertTrue(
                all.contains("mathutils")
                        || all.contains("unknown")
                        || all.contains("undeclared")
                        || all.contains("not found")
                        || all.contains("cannot find")
                        || all.contains("error"),
                () -> "expected missing-type diagnostic, got: " + all);
    }

    @Test
    void missingRegistryVersionGivesClearDependencyError() throws Exception {
        Path registryRoot = tmp.resolve("registry-empty");
        Files.createDirectories(registryRoot);
        Path app = writeApp(tmp.resolve("app-miss"), true);

        PackageManager pm = newPackageManager(app, new FilesystemRegistry(registryRoot, "empty"));
        Exception ex = assertThrows(Exception.class, () -> pm.resolve("native", false, true));
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        assertTrue(
                msg.contains("acme/math")
                        || msg.contains("No version")
                        || msg.contains("conflict")
                        || msg.contains("not found")
                        || msg.toLowerCase().contains("satisf"),
                () -> "expected clear dependency error, got: " + msg);
    }

    @Test
    void tamperedPackageHashIsRejected() throws Exception {
        Path registryRoot = tmp.resolve("registry-tamper");
        Path lib = writeLibrary(tmp.resolve("library-tamper"), "1.0.0");
        Path app = writeApp(tmp.resolve("app-tamper"), true);

        FilesystemRegistry registry = new FilesystemRegistry(registryRoot, "tamper");
        publishLibrary(lib, registry);

        Path versionDir = registryRoot.resolve("acme/math/1.0.0");
        Path pkg = versionDir.resolve("package.jnpkg");
        byte[] original = Files.readAllBytes(pkg);
        // Corrupt archive bytes but leave SHA256 pointing at the original hash.
        byte[] corrupt = original.clone();
        corrupt[corrupt.length / 2] ^= 0x5A;
        Files.write(pkg, corrupt);

        PackageManager pm = newPackageManager(app, registry);
        Exception ex = assertThrows(Exception.class, () -> pm.resolve("native", false, true));
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        assertTrue(
                msg.contains("SECURITY ERROR") || msg.contains("hash mismatch") || msg.contains("integrity"),
                () -> "expected hash rejection, got: " + msg);
    }

    @Test
    void lockPinsVersionWhenNewerPublished() throws Exception {
        Path registryRoot = tmp.resolve("registry-pin");
        Path lib100 = writeLibrary(tmp.resolve("library-1.0.0"), "1.0.0");
        Path app = writeApp(tmp.resolve("app-pin"), true);

        FilesystemRegistry registry = new FilesystemRegistry(registryRoot, "pin");
        publishLibrary(lib100, registry);

        PackageManager pm = newPackageManager(app, registry);
        ResolvedDependencyGraph first = pm.resolve("native", false, true);
        assertEquals(SemanticVersion.parse("1.0.0"), first.get(PackageId.parse("acme/math")).version());

        Path lib101 = writeLibrary(tmp.resolve("library-1.0.1"), "1.0.1");
        // Also publish a slightly different source so hash differs.
        Files.writeString(lib101.resolve("src/MathUtils.java"),
                MATH_UTILS + "\n// 1.0.1\n", StandardCharsets.UTF_8);
        publishLibrary(lib101, registry);

        // Ordinary resolve (no force) must keep the locked 1.0.0.
        PackageManager pm2 = newPackageManager(app, registry);
        ResolvedDependencyGraph second = pm2.resolve("native", false, false);
        assertEquals(SemanticVersion.parse("1.0.0"), second.get(PackageId.parse("acme/math")).version());
    }

    @Test
    void offlineRebuildUsesLockAndCache() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path registryRoot = tmp.resolve("registry-offline");
        Path lib = writeLibrary(tmp.resolve("library-offline"), "1.0.0");
        Path app = writeApp(tmp.resolve("app-offline"), true);

        FilesystemRegistry registry = new FilesystemRegistry(registryRoot, "offline");
        publishLibrary(lib, registry);

        PackageManager pm = newPackageManager(app, registry);
        pm.resolve("native", false, true);

        // Wipe registry contents — offline build must not need it.
        deleteRecursive(registryRoot);
        Files.createDirectories(registryRoot);

        ProjectCli.BuildOutcome outcome = ProjectCli.buildQuiet(
                app, false, 1, false, true, List.of(registryRoot));
        assertTrue(outcome.success(), "offline build should succeed from lock+cache");
        assertEquals(EXPECTED, runNative(outcome.primaryArtifact()));
    }

    private static PackageManager newPackageManager(Path project, FilesystemRegistry registry)
            throws IOException {
        // Fresh temp projects have no ./registry or packages/registry — only the
        // injected FilesystemRegistry is consulted. Cache stays under
        // project/.jnative/pkg-cache (isolated from the developer's shared cache).
        PackageManager pm = new PackageManager(project);
        pm.addRegistry(registry);
        return pm;
    }

    private static Path writeLibrary(Path root, String version) throws IOException {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("jnative.toml"), """
                [package]
                name = "math"
                namespace = "acme"
                version = "%s"
                description = "Third-party pure source math helpers"
                license = "MIT"
                type = "source"
                min_compiler = "0.30"
                """.formatted(version), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("src/MathUtils.java"), MATH_UTILS, StandardCharsets.UTF_8);
        return root;
    }

    private static Path writeApp(Path root, boolean withDep) throws IOException {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("jnative.project"), """
                {
                  "name": "math-app",
                  "source": ["src"],
                  "main": "Main",
                  "output": "build/math-app"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("src/Main.java"), MAIN_SRC, StandardCharsets.UTF_8);
        if (withDep) {
            Files.writeString(root.resolve("jnative.toml"), """
                    [package]
                    name = "math-app"
                    namespace = "acme"
                    version = "0.1.0"
                    license = "MIT"
                    type = "source"

                    [dependencies]
                    "acme/math" = "1.0.0"
                    """, StandardCharsets.UTF_8);
        }
        return root;
    }

    private static void publishLibrary(Path libRoot, FilesystemRegistry registry) throws Exception {
        PackageManifest m = ManifestFile.load(libRoot.resolve("jnative.toml"));
        byte[] archive = PackageArchive.pack(libRoot);
        String hash = PackageArchive.contentHash(archive);
        DependencyResolver.PackageMeta meta = new DependencyResolver.PackageMeta(
                m.id(), m.version(), m.dependencies(), m.requires(), m.features(),
                hash, registry.url(), false, m.packageType(), libRoot.toString(),
                m.nativeLibraries());
        registry.publish(m.id(), m.version(), archive, meta);
    }

    private static String runNative(Path binary) throws Exception {
        assertNotNull(binary);
        assertTrue(Files.isRegularFile(binary), "missing binary: " + binary);
        Path outFile = binary.resolveSibling(binary.getFileName() + ".stdout");
        ProcessBuilder pb = new ProcessBuilder(binary.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(outFile.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            fail("native process timed out: " + binary);
        }
        String output = Files.readString(outFile, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), () -> "exit=" + process.exitValue() + " output=\n" + output);
        return output;
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
