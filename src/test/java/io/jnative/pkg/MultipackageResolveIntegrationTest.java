package io.jnative.pkg;

import io.jnative.backend.Toolchain;
import io.jnative.cli.ProjectCli;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.diagnostics.Diagnostic;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-package third-party source lib: language packages + imports + access diagnostics.
 * @author 张晓达
 */
class MultipackageResolveIntegrationTest {

    private static final String EXPECTED = "400\n3\n8\n";

    @TempDir
    Path tmp;

    @Test
    void multipackageLibBuildsAndRuns() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path registryRoot = tmp.resolve("registry");
        Path lib = writeLibrary(tmp.resolve("library"));
        Path app = writeApp(tmp.resolve("app"), true);
        FilesystemRegistry registry = new FilesystemRegistry(registryRoot, "test");
        publish(lib, registry);

        PackageManager pm = new PackageManager(app);
        pm.addRegistry(registry);
        ResolvedDependencyGraph graph = pm.resolve("native", false, true);
        assertNotNull(graph.get(PackageId.parse("demo/greetkit")));

        assertFalse(Files.exists(app.resolve("src/Texts.jn")));
        assertFalse(Files.exists(app.resolve("src/Greeter.jn")));

        ProjectCli.BuildOutcome outcome = ProjectCli.buildQuiet(
                app, false, 1, false, false, List.of(registryRoot));
        assertTrue(outcome.success(), String.join("\n", outcome.diagnosticTexts()));
        assertEquals(EXPECTED, runNative(outcome.primaryArtifact()));
    }

    @Test
    void badAccessNegativeHasLocation() throws Exception {
        Path libSrc = writeLibrary(tmp.resolve("lib2")).resolve("src");
        Path bad = tmp.resolve("BadAccess.jn");
        String merged = Files.readString(libSrc.resolve("Greeter.jn"), StandardCharsets.UTF_8)
                + "\n\n"
                + Files.readString(libSrc.resolve("Texts.jn"), StandardCharsets.UTF_8)
                + "\n\n"
                + """
                package com.demo.app;

                import com.demo.greet.Texts;

                public class BadAccess {
                    public static void main(String[] args) {
                        System.out.println(Texts.hidden(1));
                    }
                }
                """;
        Files.writeString(bad, merged, StandardCharsets.UTF_8);

        CompilerOptions opt = new CompilerOptions();
        opt.setSourceFile(bad);
        opt.setOutputFile(tmp.resolve("bad-access-out"));
        CompileResult cr = new JNativeCompiler().compile(opt);
        assertFalse(cr.success());
        String all = cr.diagnostics().stream().map(Diagnostic::toString).reduce("", (a, b) -> a + b);
        assertTrue(all.toLowerCase().contains("not accessible")
                || all.toLowerCase().contains("package"), all);
        assertTrue(cr.diagnostics().stream().anyMatch(d ->
                d.location() != null && d.location().line() > 0), all);
    }

    @Test
    void ambiguousCallNegative() throws Exception {
        Path src = tmp.resolve("AmbiguousCall.jn");
        Files.writeString(src, """
                package com.demo.app;

                public class AmbiguousCall {
                    static int f(int a, long b) { return 1; }
                    static int f(long a, int b) { return 2; }
                    public static void main(String[] args) {
                        System.out.println(f(1, 1));
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions opt = new CompilerOptions();
        opt.setSourceFile(src);
        opt.setOutputFile(tmp.resolve("ambig-out"));
        CompileResult cr = new JNativeCompiler().compile(opt);
        assertFalse(cr.success());
        String all = cr.diagnostics().stream().map(Diagnostic::message)
                .reduce("", (a, b) -> a + "\n" + b).toLowerCase();
        assertTrue(all.contains("ambiguous"), all);
    }

    private static Path writeLibrary(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("jnative.toml"), """
                [package]
                name = "greetkit"
                namespace = "demo"
                version = "1.0.0"
                license = "MIT"
                type = "source"
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("src/Greeter.jn"), """
                package com.demo.greet;

                public interface Greeter {
                    int greet(int n);
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("src/Texts.jn"), """
                package com.demo.greet;

                public class Texts {
                    public static int bump(int x) {
                        return x + 1;
                    }

                    static int hidden(int x) {
                        return x * 10;
                    }
                }
                """, StandardCharsets.UTF_8);
        return dir;
    }

    private static Path writeApp(Path dir, boolean withDep) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("jnative.project"), """
                {
                  "name": "greet-app",
                  "source": ["src"],
                  "main": "Main",
                  "output": "build/greet-app"
                }
                """, StandardCharsets.UTF_8);
        String deps = withDep ? """

                [dependencies]
                "demo/greetkit" = "1.0.0"
                """ : "";
        Files.writeString(dir.resolve("jnative.toml"), """
                [package]
                name = "greet-app"
                namespace = "demo"
                version = "0.1.0"
                license = "MIT"
                type = "source"
                """ + deps, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("src/Main.jn"), """
                package com.demo.app;

                import com.demo.greet.Greeter;
                import com.demo.greet.Texts;

                public class Main {
                    public static void main(String[] args) {
                        Greeter loud = new LoudGreeter();
                        Greeter soft = new SoftGreeter();
                        System.out.println(loud.greet(3));
                        System.out.println(soft.greet(3));
                        System.out.println(Texts.bump(7));
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("src/LoudGreeter.jn"), """
                package com.demo.app;

                import com.demo.greet.Greeter;
                import com.demo.greet.Texts;

                public class LoudGreeter implements Greeter {
                    public int greet(int n) {
                        return Texts.bump(n) * 100;
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("src/SoftGreeter.jn"), """
                package com.demo.app;

                import com.demo.greet.Greeter;

                public class SoftGreeter implements Greeter {
                    public int greet(int n) {
                        return n;
                    }
                }
                """, StandardCharsets.UTF_8);
        return dir;
    }

    private static void publish(Path lib, FilesystemRegistry registry) throws Exception {
        PackageManifest m = ManifestFile.load(lib.resolve("jnative.toml"));
        byte[] archive = PackageArchive.pack(lib);
        String hash = PackageArchive.contentHash(archive);
        DependencyResolver.PackageMeta meta = new DependencyResolver.PackageMeta(
                m.id(), m.version(), m.dependencies(), m.requires(), m.features(),
                hash, registry.url(), false, m.packageType(), lib.toString(),
                m.nativeLibraries());
        registry.publish(m.id(), m.version(), archive, meta);
    }

    private static String runNative(Path binary) throws Exception {
        assertNotNull(binary);
        Path outFile = binary.resolveSibling(binary.getFileName() + ".stdout");
        ProcessBuilder pb = new ProcessBuilder(binary.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(outFile.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("native process timed out");
        }
        String output = Files.readString(outFile, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
