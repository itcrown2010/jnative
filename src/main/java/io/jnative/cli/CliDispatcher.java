package io.jnative.cli;

import io.jnative.Main;
import io.jnative.build.JNativeProject;
import io.jnative.build.ProjectFile;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.dev.DevCoordinator;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.incremental.BuildResult;
import io.jnative.incremental.CompilerSession;
import io.jnative.incremental.LocalBuildCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Top-level CLI dispatcher for build / dev / cache / clean / single-file compile.
 */
public final class CliDispatcher {

    private CliDispatcher() {
    }

    public static int dispatch(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            System.out.print(helpText());
            return 0;
        }
        String cmd = args[0];
        return switch (cmd) {
            case "dev" -> runDev(args);
            case "build" -> runBuild(args);
            case "clean" -> runClean(args);
            case "cache" -> runCache(args);
            case "explain-build" -> runExplainBuild(args);
            case "cert" -> CertCli.dispatch(args);
            case "serve" -> ServeCli.dispatch(args);
            case "processors" -> ProcessorsCli.dispatch(args);
            case "generated" -> ProcessorsCli.generated(args);
            case "add", "remove", "update", "deps", "search", "info",
                 "publish", "audit", "licenses" -> PackageCli.dispatch(args);
            case "-h", "--help", "help" -> {
                System.out.print(helpText());
                yield 0;
            }
            case "-v", "--version", "version" -> {
                System.out.println("JNative " + Main.VERSION);
                yield 0;
            }
            default -> runSingleFile(args);
        };
    }

    private static int runDev(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        int browserPort = 3000;
        int serverPort = 8080;
        for (int i = 1; i < args.length; i++) {
            if ("--browser-port".equals(args[i]) && i + 1 < args.length) {
                browserPort = Integer.parseInt(args[++i]);
            } else if ("--server-port".equals(args[i]) && i + 1 < args.length) {
                serverPort = Integer.parseInt(args[++i]);
            } else if ("--project".equals(args[i]) && i + 1 < args.length) {
                root = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if ("-Odev".equals(args[i])) {
                // default
            }
        }
        try (DevCoordinator coord = new DevCoordinator(root, browserPort, serverPort)) {
            coord.start();
        }
        return 0;
    }

    private static int runBuild(String[] args) throws Exception {
        // Hybrid: jnative build --target wasm32-browser App.java
        boolean hasTarget = false;
        boolean hasJava = false;
        for (String a : args) {
            if ("--target".equals(a) || a.startsWith("--target=")) {
                hasTarget = true;
            }
            if (a.endsWith(".java")) {
                hasJava = true;
            }
        }
        if (hasTarget || hasJava) {
            return runSingleFile(args);
        }
        Path root = Path.of(".").toAbsolutePath().normalize();
        boolean explain = false;
        boolean release = false;
        boolean offline = false;
        int jobs = 0;
        int opt = 1;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--explain" -> explain = true;
                case "--offline" -> offline = true;
                case "--release" -> {
                    release = true;
                    opt = 3;
                }
                case "-Odev" -> opt = 1;
                case "-O0" -> opt = 0;
                case "-O1" -> opt = 1;
                case "-O2" -> opt = 2;
                case "-O3" -> opt = 3;
                case "--jobs", "-j" -> {
                    if (i + 1 < args.length) {
                        jobs = Integer.parseInt(args[++i]);
                    }
                }
                case "--project" -> {
                    if (i + 1 < args.length) {
                        root = Path.of(args[++i]).toAbsolutePath().normalize();
                    }
                }
                default -> {
                }
            }
        }
        // Package resolve before compile (compiler never hits network)
        try {
            if (java.nio.file.Files.isRegularFile(root.resolve("jnative.toml"))
                    || java.nio.file.Files.isRegularFile(root.resolve("jnative.lock"))) {
                io.jnative.pkg.PackageManager pm = new io.jnative.pkg.PackageManager(root);
                pm.setOffline(offline);
                var graph = pm.resolve("native", false, false);
                var nodes = io.jnative.pkg.BuildGraphPackageIntegration.materialize(graph, pm.cache());
                io.jnative.pkg.BuildGraphPackageIntegration.validateCapabilities(nodes, "native");
                System.out.println("[pkg] resolved " + graph.packages().size()
                        + " packages, build nodes=" + nodes.size());
            }
        } catch (Exception e) {
            System.err.println("[pkg] " + e.getMessage());
            if (offline || java.nio.file.Files.isRegularFile(root.resolve("jnative.toml"))) {
                return 1;
            }
        }
        CompilerSession session = new CompilerSession(root);
        session.setExplain(explain);
        session.setOptLevel(release ? 3 : opt);
        if (jobs > 0) {
            session.setJobs(jobs);
        }
        BuildResult result = session.build();
        System.out.print(result.summary());
        if (explain) {
            for (String line : result.explain()) {
                System.out.println(line);
                System.out.println();
            }
        }
        for (String d : result.diagnostics()) {
            System.err.println(d);
        }
        return result.success() ? 0 : 1;
    }

    private static int runClean(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        for (int i = 1; i < args.length; i++) {
            if ("--project".equals(args[i]) && i + 1 < args.length) {
                root = Path.of(args[++i]).toAbsolutePath().normalize();
            }
        }
        JNativeProject project = ProjectFile.discover(root);
        Path build = root.resolve(project.buildDir());
        Path generated = root.resolve(project.generatedDir());
        deleteTree(build);
        deleteTree(generated);
        // also common build/
        deleteTree(root.resolve("build"));
        System.out.println("Cleaned build outputs under " + project.buildDir()
                + " (cache retained; use `jnative cache clean` to wipe cache)");
        return 0;
    }

    private static int runCache(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: jnative cache stats|clean");
            return 1;
        }
        Path root = Path.of(".").toAbsolutePath().normalize();
        for (int i = 2; i < args.length; i++) {
            if ("--project".equals(args[i]) && i + 1 < args.length) {
                root = Path.of(args[++i]).toAbsolutePath().normalize();
            }
        }
        JNativeProject project = ProjectFile.discover(root);
        LocalBuildCache cache = new LocalBuildCache(root.resolve(project.cacheDir()));
        cache.ensureLayout();
        return switch (args[1]) {
            case "stats" -> {
                Map<String, Object> st = cache.stats();
                System.out.println("Cache root:     " + st.get("root"));
                System.out.println("Disk entries:   " + st.get("diskEntries"));
                System.out.println("Disk bytes:     " + st.get("diskBytes"));
                System.out.println("Memory entries: " + st.get("memoryEntries"));
                System.out.println("Hits:           " + st.get("hits"));
                System.out.println("Misses:         " + st.get("misses"));
                System.out.println("Hit rate:       " + st.get("hitRatePercent") + "%");
                yield 0;
            }
            case "clean" -> {
                cache.clean();
                System.out.println("Cache cleaned: " + cache.root());
                yield 0;
            }
            default -> {
                System.err.println("unknown cache subcommand: " + args[1]);
                yield 1;
            }
        };
    }

    private static int runExplainBuild(String[] args) throws Exception {
        List<String> next = new ArrayList<>();
        next.add("build");
        next.add("--explain");
        for (int i = 1; i < args.length; i++) {
            next.add(args[i]);
        }
        return runBuild(next.toArray(new String[0]));
    }

    private static int runSingleFile(String[] args) {
        CompilerOptions options;
        try {
            options = CliArguments.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(helpText());
            return 1;
        }
        if (options.help()) {
            System.out.print(helpText());
            return 0;
        }
        if (options.version()) {
            System.out.println("JNative " + Main.VERSION);
            return 0;
        }
        if (options.sourceFile() == null) {
            System.err.println("error: no input file");
            System.err.println(helpText());
            return 1;
        }
        CompileResult result = new JNativeCompiler().compile(options);
        for (Diagnostic d : result.diagnostics()) {
            System.err.println(d);
        }
        return result.success() ? 0 : 1;
    }

    private static String[] slice(String[] args, int from) {
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    public static String helpText() {
        return """
                JNative 0.32 — Compile-Time Metaprogramming / Annotation Processing

                Usage:
                  jnative processors [list|explain <Type>]
                  jnative generated <SymbolOrPath>
                  jnative build [--project DIR] [--explain] [--deterministic] [--offline] [--release]
                  jnative add|remove|update|deps|search|info|publish|audit|licenses
                  jnative cert|serve|dev|clean|cache
                  jnative <source.java> [-o out] [-O0..-O3] [--explain-meta] [--deterministic]
                  jnative --version | --help

                Metaprogramming (0.32):
                  Stable Meta API · AnnotationProcessor · .jnative/generated · ZERO runtime reflection
                  Metaprogramming LLVM intrinsics: NONE
                """;
    }
}
