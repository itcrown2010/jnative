package io.jnative.cli;

import io.jnative.Main;
import io.jnative.backend.NativeRuntimeSources;
import io.jnative.backend.Toolchain;
import io.jnative.build.JNativeProject;
import io.jnative.build.ProjectFile;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.incremental.BuildResult;
import io.jnative.incremental.CompilerSession;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Project-oriented UX: {@code doctor}, {@code new}, {@code run}, {@code test}.
 */
public final class ProjectCli {

    /** Product channel banner for the polished CLI surface. */
    public static final String CHANNEL = "1.0-dev";

    private ProjectCli() {
    }

    public static int doctor(String[] args) {
        System.out.println("JNative " + CHANNEL);
        System.out.println();

        List<Check> checks = new ArrayList<>();
        checks.add(checkCompiler());
        checks.add(checkLlvm());
        checks.add(checkClang());
        checks.add(checkLinker());
        checks.add(checkRuntime());
        checks.add(checkPackageCache());

        boolean healthy = true;
        for (Check c : checks) {
            String mark = c.ok() ? "✓" : "✗";
            System.out.println(mark + " " + c.name());
            if (!c.ok()) {
                healthy = false;
                if (c.detail() != null && !c.detail().isBlank()) {
                    System.out.println("  " + c.detail());
                }
            }
        }
        System.out.println();
        if (healthy) {
            System.out.println("Environment healthy.");
            return 0;
        }
        System.out.println("Environment has issues. Fix the items above and re-run `jnative doctor`.");
        return 1;
    }

    public static int newProject(String[] args) throws Exception {
        if (args.length < 2 || args[1].startsWith("-")) {
            System.err.println("usage: jnative new <name>");
            return 1;
        }
        String name = args[1].trim();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            System.err.println("error: invalid project name: " + name);
            return 1;
        }
        Path dir = Path.of(name).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            if (!Files.isDirectory(dir)) {
                System.err.println("error: path exists and is not a directory: " + name);
                return 1;
            }
            try (Stream<Path> listing = Files.list(dir)) {
                if (listing.findAny().isPresent()) {
                    System.err.println("error: directory already exists and is not empty: " + name);
                    return 1;
                }
            }
        } else {
            Files.createDirectories(dir);
        }

        Files.createDirectories(dir.resolve("src"));
        Files.createDirectories(dir.resolve("tests"));

        String projectJson = """
                {
                  "name": "%s",
                  "source": ["src"],
                  "main": "Main",
                  "output": "build/%s"
                }
                """.formatted(name, name);
        Files.writeString(dir.resolve("jnative.project"), projectJson, StandardCharsets.UTF_8);

        String mainSrc = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello, JNative!");
                    }
                }
                """;
        Files.writeString(dir.resolve("src/Main.java"), mainSrc, StandardCharsets.UTF_8);

        writeTest(dir, "GreetingTest", """
                public class GreetingTest {
                    public static void main(String[] args) {
                        String s = "Hello, JNative!";
                        int ok = 0;
                        if (s.length() >= 5) {
                            ok = 1;
                        }
                        int keep = 1 / ok;
                    }
                }
                """);
        writeTest(dir, "MathTest", """
                public class MathTest {
                    public static void main(String[] args) {
                        int n = 1 + 1;
                        int ok = 0;
                        if (n == 2) {
                            ok = 1;
                        }
                        int keep = 1 / ok;
                    }
                }
                """);
        writeTest(dir, "StringTest", """
                public class StringTest {
                    public static void main(String[] args) {
                        String a = "JNative";
                        String b = "J" + "Native";
                        int ok = 0;
                        if (a.compareTo(b) == 0) {
                            ok = 1;
                        }
                        int keep = 1 / ok;
                    }
                }
                """);
        writeTest(dir, "IdentityTest", """
                public class IdentityTest {
                    public static void main(String[] args) {
                        int x = 42;
                        int ok = 0;
                        if (x == 42) {
                            ok = 1;
                        }
                        int keep = 1 / ok;
                    }
                }
                """);

        System.out.println();
        System.out.println("Created project `" + name + "`.");
        System.out.println();
        return 0;
    }

    public static int run(String[] args) throws Exception {
        Path root = projectRoot(args, 1);
        if (!Files.isRegularFile(root.resolve("jnative.project"))) {
            System.err.println("error: no jnative.project in " + root);
            System.err.println("Run `jnative new <name>` or `jnative <source.java> --run`.");
            return 1;
        }
        JNativeProject project = ProjectFile.discover(root);
        System.out.println("Compiling " + project.name() + "...");
        BuildOutcome outcome = buildQuiet(root, false, 1, false);
        if (!outcome.success()) {
            return 1;
        }
        System.out.println();
        Path binary = outcome.primaryArtifact();
        if (binary == null || !Files.isRegularFile(binary)) {
            System.err.println("error: build produced no runnable artifact");
            return 1;
        }
        ProcessBuilder pb = new ProcessBuilder(binary.toAbsolutePath().toString());
        pb.directory(root.toFile());
        pb.inheritIO();
        return pb.start().waitFor();
    }

    public static int test(String[] args) throws Exception {
        Path root = projectRoot(args, 1);
        Path testsDir = root.resolve("tests");
        if (!Files.isDirectory(testsDir)) {
            System.err.println("error: no tests/ directory in " + root);
            return 1;
        }
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(testsDir)) {
            sources = walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
        int total = sources.size();
        int passed = 0;
        int failed = 0;
        Path outDir = root.resolve("build/tests");
        Files.createDirectories(outDir);
        JNativeCompiler compiler = new JNativeCompiler();
        for (Path src : sources) {
            String base = src.getFileName().toString();
            int dot = base.lastIndexOf('.');
            String name = dot > 0 ? base.substring(0, dot) : base;
            Path out = outDir.resolve(name);
            CompilerOptions options = new CompilerOptions();
            options.setSourceFile(src);
            options.setOutputFile(out);
            options.setOptLevel(1);
            CompileResult cr = compiler.compile(options);
            if (!cr.success()) {
                failed++;
                System.err.println("FAIL " + name + " (compile)");
                for (Diagnostic d : cr.diagnostics()) {
                    System.err.println(d);
                }
                continue;
            }
            ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString();
            }
            int code = p.waitFor();
            if (code == 0) {
                passed++;
            } else {
                failed++;
                System.err.println("FAIL " + name + " (exit " + code + ")");
                if (!output.isBlank()) {
                    System.err.print(output);
                }
            }
        }
        System.out.println(total + " tests");
        System.out.println(passed + " passed");
        System.out.println(failed + " failed");
        System.out.println();
        return failed == 0 ? 0 : 1;
    }

    /** Shared quiet build used by {@code run} and {@code build}. */
    public static BuildOutcome buildQuiet(Path root, boolean release, int optLevel) throws Exception {
        return buildQuiet(root, release, optLevel, true);
    }

    public static BuildOutcome buildQuiet(Path root, boolean release, int optLevel, boolean printOutput)
            throws Exception {
        CompilerSession session = new CompilerSession(root);
        session.setOptLevel(release ? 3 : optLevel);
        session.setReleaseMode(release);
        long t0 = System.currentTimeMillis();
        BuildResult result = session.build();
        long ms = result.wallTimeMs() > 0 ? result.wallTimeMs() : (System.currentTimeMillis() - t0);
        if (!result.success()) {
            System.out.printf(Locale.US, "Build FAILED in %.2fs%n", ms / 1000.0);
            for (String d : result.diagnostics()) {
                System.err.println(d);
            }
            return new BuildOutcome(false, null, ms, session);
        }
        Path artifact = session.primaryArtifact();
        System.out.printf(Locale.US, "Build succeeded in %.2fs%n", ms / 1000.0);
        if (printOutput && artifact != null) {
            Path rel;
            try {
                rel = root.relativize(artifact.toAbsolutePath().normalize());
            } catch (Exception e) {
                rel = artifact;
            }
            System.out.println("Output: " + rel.toString().replace('\\', '/'));
        }
        for (String d : result.diagnostics()) {
            System.err.println(d);
        }
        return new BuildOutcome(true, artifact, ms, session);
    }

    public record BuildOutcome(boolean success, Path primaryArtifact, long wallTimeMs, CompilerSession session) {
    }

    private static void writeTest(Path root, String className, String source) throws Exception {
        Files.writeString(root.resolve("tests").resolve(className + ".java"), source, StandardCharsets.UTF_8);
    }

    private static Path projectRoot(String[] args, int start) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        for (int i = start; i < args.length; i++) {
            if ("--project".equals(args[i]) && i + 1 < args.length) {
                root = Path.of(args[++i]).toAbsolutePath().normalize();
            }
        }
        return root;
    }

    private record Check(String name, boolean ok, String detail) {
    }

    private static Check checkCompiler() {
        try {
            if (Main.VERSION == null || Main.VERSION.isBlank()) {
                return new Check("Compiler", false, "version missing");
            }
            new JNativeCompiler();
            return new Check("Compiler", true, null);
        } catch (Exception e) {
            return new Check("Compiler", false, e.getMessage());
        }
    }

    private static Check checkLlvm() {
        if (findOnPath("llvm-config").isPresent()
                || findOnPath("llc").isPresent()
                || findOnPath("llvm-as").isPresent()) {
            return new Check("LLVM", true, null);
        }
        // Apple clang / most clang toolchains embed LLVM and link .ll directly.
        Optional<Toolchain> tc = Toolchain.detect();
        if (tc.isPresent() && clangMentionsLlvm(tc.get().clang().toString())) {
            return new Check("LLVM", true, null);
        }
        return new Check("LLVM", false, "llvm-config/llc not found; install LLVM or a clang with LLVM IR support");
    }

    private static Check checkClang() {
        Optional<Toolchain> tc = Toolchain.detect();
        if (tc.isPresent()) {
            return new Check("clang", true, null);
        }
        return new Check("clang", false, "clang not found on PATH");
    }

    private static Check checkLinker() {
        if (findOnPath("ld").isPresent()
                || findOnPath("ld64").isPresent()
                || findOnPath("lld").isPresent()) {
            return new Check("Linker", true, null);
        }
        // clang drives the system linker on macOS/Linux
        if (Toolchain.detect().isPresent()) {
            return new Check("Linker", true, null);
        }
        return new Check("Linker", false, "no ld/lld and no clang linker driver");
    }

    private static Check checkRuntime() {
        String[] required = {
                "native/posix/jnative_gc2.c",
                "native/posix/jnative_gc2.h",
                "native/posix/jnative_mt.c",
        };
        ClassLoader cl = NativeRuntimeSources.class.getClassLoader();
        for (String r : required) {
            try (InputStream in = cl.getResourceAsStream(r)) {
                if (in == null) {
                    return new Check("Runtime", false, "missing bundled resource: " + r);
                }
            } catch (Exception e) {
                return new Check("Runtime", false, e.getMessage());
            }
        }
        return new Check("Runtime", true, null);
    }

    private static Check checkPackageCache() {
        try {
            Path cache = Path.of(System.getProperty("user.home"), ".jnative", "pkg-cache");
            Files.createDirectories(cache);
            Path probe = cache.resolve(".doctor-write");
            Files.writeString(probe, "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe);
            return new Check("Package cache", true, null);
        } catch (Exception e) {
            return new Check("Package cache", false, e.getMessage());
        }
    }

    private static boolean clangMentionsLlvm(String clang) {
        try {
            ProcessBuilder pb = new ProcessBuilder(clang, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                out = sb.toString().toLowerCase(Locale.ROOT);
            }
            p.waitFor();
            return out.contains("clang") || out.contains("llvm");
        } catch (Exception e) {
            return false;
        }
    }

    private static Optional<Path> findOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        String sep = System.getProperty("path.separator", ":");
        for (String dir : pathEnv.split(sep)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(name, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader ignored = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                while (ignored.readLine() != null) {
                    // drain
                }
            }
            if (p.waitFor() == 0) {
                return Optional.of(Path.of(name));
            }
        } catch (Exception ignored) {
            // not found
        }
        return Optional.empty();
    }
}
