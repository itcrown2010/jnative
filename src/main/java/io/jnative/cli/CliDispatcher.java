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
 * @author 张晓达
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
            case "doctor" -> ProjectCli.doctor(args);
            case "new" -> ProjectCli.newProject(args);
            case "mobile" -> MobileCli.dispatch(args);
            case "run" -> ProjectCli.run(args);
            case "test" -> ProjectCli.test(args);
            case "analyze" -> AnalyzeCli.dispatch(args);
            case "lsp" -> {
                io.jnative.lsp.LspServer.main(slice(args, 1));
                yield 0;
            }
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
            if (io.jnative.source.SourceFiles.isSourceFileName(a)) {
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
        boolean jsonFormat = false;
        boolean debug = false;
        boolean optExplicit = false;
        int jobs = 0;
        int opt = 1;
        java.util.List<Path> registries = new java.util.ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--explain" -> explain = true;
                case "--offline" -> offline = true;
                case "--debug" -> debug = true;
                case "--format" -> {
                    if (i + 1 < args.length && "json".equalsIgnoreCase(args[++i])) {
                        jsonFormat = true;
                    }
                }
                case "--format=json" -> jsonFormat = true;
                case "--release" -> {
                    release = true;
                    opt = 3;
                }
                case "-Odev" -> {
                    opt = 1;
                    optExplicit = true;
                }
                case "-O0" -> {
                    opt = 0;
                    optExplicit = true;
                }
                case "-O1" -> {
                    opt = 1;
                    optExplicit = true;
                }
                case "-O2" -> {
                    opt = 2;
                    optExplicit = true;
                }
                case "-O3" -> {
                    opt = 3;
                    optExplicit = true;
                }
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
                case "--registry" -> {
                    if (i + 1 < args.length) {
                        registries.add(Path.of(args[++i]).toAbsolutePath().normalize());
                    }
                }
                default -> {
                }
            }
        }
        if (debug && !optExplicit) {
            opt = 0;
        }
        if (!explain) {
            try {
                ProjectCli.BuildOutcome outcome = ProjectCli.buildQuiet(
                        root, release, opt, !jsonFormat, offline, registries, debug);
                if (jsonFormat) {
                    String artifact = outcome.primaryArtifact() == null
                            ? null
                            : outcome.primaryArtifact().toAbsolutePath().normalize().toString();
                    System.out.println(io.jnative.diagnostics.DiagnosticJson.buildReportJson(
                            outcome.success(), artifact, parseDiagnosticTexts(outcome.diagnosticTexts())));
                }
                return outcome.success() ? 0 : 1;
            } catch (Exception e) {
                if (jsonFormat) {
                    System.out.println(io.jnative.diagnostics.DiagnosticJson.buildReportJson(
                            false, null, java.util.List.of(
                                    io.jnative.diagnostics.Diagnostic.error(
                                            io.jnative.diagnostics.SourceLocation.UNKNOWN,
                                            e.getMessage() == null ? "build failed" : e.getMessage()))));
                } else {
                    System.err.println("[pkg] " + e.getMessage());
                }
                return 1;
            }
        }
        CompilerSession session = new CompilerSession(root);
        session.setExplain(true);
        session.setOptLevel(release ? 3 : opt);
        session.setReleaseMode(release);
        session.setEmitSourceDebugInfo(debug);
        if (jobs > 0) {
            session.setJobs(jobs);
        }
        try {
            ProjectCli.attachPackageSources(session, root, offline, registries);
        } catch (Exception e) {
            System.err.println("[pkg] " + e.getMessage());
            return 1;
        }
        BuildResult result = session.build();
        System.out.print(result.summary());
        for (String line : result.explain()) {
            System.out.println(line);
            System.out.println();
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
        boolean jsonFormat = false;
        for (String a : args) {
            if ("--format=json".equals(a) || ("--format".equals(a))) {
                jsonFormat = true;
            }
            if ("json".equalsIgnoreCase(a)) {
                jsonFormat = true;
            }
        }
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
        if (jsonFormat) {
            String artifact = result.executable() == null
                    ? null
                    : result.executable().toAbsolutePath().normalize().toString();
            System.out.println(io.jnative.diagnostics.DiagnosticJson.buildReportJson(
                    result.success(), artifact, result.diagnostics()));
        } else {
            for (Diagnostic d : result.diagnostics()) {
                System.err.println(d);
            }
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
        return "JNative " + ProjectCli.CHANNEL + """
                 — Native Java toolchain

                Usage:
                  jnative doctor [--mobile|--with-mobile]
                  jnative new <name> [--minimal] [--template mobile]
                  jnative mobile build|devices|run|logs ...
                  jnative run [--project DIR]
                  jnative test [--project DIR]
                  jnative build [--project DIR] [--registry DIR] [--debug] [--release] [--explain] [--offline] [--format json]
                  jnative <source.jn|.java> --debug [-O0..-O3] [-o out]
                  jnative analyze <file.jn|.java> | --stdin [--path name] [--format json]
                  jnative lsp
                  jnative processors [list|explain <Type>]
                  jnative generated <SymbolOrPath>
                  jnative add|remove|update|deps|search|info|publish|audit|licenses [--registry DIR]
                  jnative cert|serve|dev|clean|cache
                  jnative <source.jn|.java> [-o out] [-O0..-O3] [--run]
                  jnative --version | --help
                """;
    }

    /**
     * Parse compiler diagnostic texts {@code file:line:column: severity: message}.
     * Unparseable lines become a single NOTE at UNKNOWN so stdout logs are not invented as errors.
     */
    static java.util.List<io.jnative.diagnostics.Diagnostic> parseDiagnosticTexts(
            java.util.List<String> texts
    ) {
        java.util.List<io.jnative.diagnostics.Diagnostic> out = new java.util.ArrayList<>();
        if (texts == null) {
            return out;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "^(.+):(\\d+):(\\d+):\\s*(error|warning|note):\\s*(.*)$",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            // Multi-line target-prefixed blobs: try each line
            for (String line : text.split("\\R")) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                // Strip optional "targetName: " prefix from CompilerSession
                int colonSpace = t.indexOf(": ");
                String candidate = t;
                java.util.regex.Matcher m = p.matcher(candidate);
                if (!m.matches() && colonSpace > 0) {
                    candidate = t.substring(colonSpace + 2).trim();
                    m = p.matcher(candidate);
                }
                if (m.matches()) {
                    io.jnative.diagnostics.Diagnostic.Severity sev = switch (m.group(4).toLowerCase()) {
                        case "error" -> io.jnative.diagnostics.Diagnostic.Severity.ERROR;
                        case "warning" -> io.jnative.diagnostics.Diagnostic.Severity.WARNING;
                        default -> io.jnative.diagnostics.Diagnostic.Severity.NOTE;
                    };
                    out.add(new io.jnative.diagnostics.Diagnostic(
                            sev,
                            new io.jnative.diagnostics.SourceLocation(
                                    m.group(1),
                                    Integer.parseInt(m.group(2)),
                                    Integer.parseInt(m.group(3))),
                            m.group(5)));
                }
            }
        }
        return out;
    }
}
