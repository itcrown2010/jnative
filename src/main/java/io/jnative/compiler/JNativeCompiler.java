package io.jnative.compiler;

import io.jnative.ast.AstPrinter;
import io.jnative.ast.CompilationUnit;
import io.jnative.backend.NativeLinker;
import io.jnative.backend.Platform;
import io.jnative.backend.Toolchain;
import io.jnative.backend.llvm.LlvmIrGenerator;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.ir.AsyncLoweringPass;
import io.jnative.ir.AsyncReport;
import io.jnative.ir.IrGenerator;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrPrinter;
import io.jnative.opt.OptimizationPipeline;
import io.jnative.opt.OptimizationReport;
import io.jnative.lexer.Lexer;
import io.jnative.lexer.Token;
import io.jnative.lexer.TokenType;
import io.jnative.parser.Parser;
import io.jnative.generic.GenericLowering;
import io.jnative.semantic.EffectAnalyzer;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Top-level compilation pipeline orchestration.
 */
public final class JNativeCompiler {

    /**
     * Frontend-only analysis for IDE (lex → parse → semantic). No IR/LLVM/link.
     * Version is caller-owned; stale results should be discarded when sources advance.
     */
    public Compilation analyze(SourceUnit unit, long version) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        if (unit == null) {
            return new Compilation(version, null, null, null, reporter.diagnostics(), false, 0, 0);
        }
        String sourceText;
        try {
            sourceText = io.jnative.stdlib.StdlibIncluder.prependStdlibs(unit.sourceText());
        } catch (IOException e) {
            reporter.error(SourceLocation.UNKNOWN, "failed to load stdlib: " + e.getMessage());
            return new Compilation(version, null, null, null, reporter.diagnostics(), false, 0, 0);
        }
        String fileName = unit.path() == null ? "Memory.java" : unit.path();
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < fileName.length()) {
            fileName = fileName.substring(slash + 1);
        }
        long t0 = System.nanoTime();
        Lexer lexer = new Lexer(sourceText, fileName);
        List<Token> tokens = lexer.tokenize();
        for (Token token : tokens) {
            if (token.type() == TokenType.ERROR) {
                reporter.error(token.location(), token.lexeme());
            }
        }
        Parser parser = new Parser(tokens, reporter);
        CompilationUnit ast = parser.parse(fileName);
        long parseMs = (System.nanoTime() - t0) / 1_000_000L;
        SyntaxTree tree = new SyntaxTree(fileName, unit.sourceText(), ast, reporter.diagnostics(), version);
        if (reporter.hasErrors()) {
            return new Compilation(version, tree, null, null, reporter.diagnostics(), false, parseMs, 0);
        }
        long t1 = System.nanoTime();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        TypedCompilationUnit typed = analyzer.analyze(ast);
        long semMs = (System.nanoTime() - t1) / 1_000_000L;
        SemanticModel model = typed == null ? null : new SemanticModel(typed, version);
        boolean ok = !reporter.hasErrors();
        return new Compilation(version, tree, typed, model, reporter.diagnostics(), ok, parseMs, semMs);
    }

    /** Analyze a path on disk (frontend only). */
    public Compilation analyze(Path source, long version) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        try {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            return analyze(new SourceUnit(source.getFileName().toString(), text), version);
        } catch (IOException e) {
            reporter.error(SourceLocation.UNKNOWN, "failed to read: " + e.getMessage());
            return new Compilation(version, null, null, null, reporter.diagnostics(), false, 0, 0);
        }
    }

    /**
     * Compile an in-memory {@link SourceUnit} (IDE unsaved buffers).
     * Writes a temporary file when a real path is needed for diagnostics/output naming.
     */
    public CompileResult compile(SourceUnit unit, CompilerOptions options) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        if (unit == null) {
            reporter.error(SourceLocation.UNKNOWN, "no source unit");
            return fail(reporter);
        }
        try {
            Path tmp = Files.createTempFile("jnative-src-", ".java");
            Files.writeString(tmp, unit.sourceText(), StandardCharsets.UTF_8);
            Path previous = options.sourceFile();
            options.setSourceFile(tmp);
            CompileResult result = compile(options);
            options.setSourceFile(previous);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort
            }
            return result;
        } catch (IOException e) {
            reporter.error(SourceLocation.UNKNOWN, "failed to materialize source unit: " + e.getMessage());
            return fail(reporter);
        }
    }

    public CompileResult compile(CompilerOptions options) {
        DiagnosticReporter reporter = new DiagnosticReporter();

        if (options.sourceFile() == null) {
            reporter.error(SourceLocation.UNKNOWN, "no input file");
            return fail(reporter);
        }

        Path source = options.sourceFile().toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            reporter.error(new SourceLocation(source.toString(), 0, 0),
                    "source file not found: " + source);
            return fail(reporter);
        }

        String userSource;
        String sourceText;
        try {
            userSource = Files.readString(source, StandardCharsets.UTF_8);
            sourceText = io.jnative.stdlib.StdlibIncluder.prependStdlibs(userSource);
        } catch (IOException e) {
            reporter.error(new SourceLocation(source.toString(), 0, 0),
                    "failed to read source: " + e.getMessage());
            return fail(reporter);
        }

        String fileName = source.getFileName().toString();

        // Lexer
        Lexer lexer = new Lexer(sourceText, fileName);
        List<Token> tokens = lexer.tokenize();
        for (Token token : tokens) {
            if (token.type() == TokenType.ERROR) {
                reporter.error(token.location(), token.lexeme());
            }
        }
        if (reporter.hasErrors()) {
            return fail(reporter);
        }

        // Parser
        Parser parser = new Parser(tokens, reporter);
        CompilationUnit ast = parser.parse(fileName);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
        }

        // ---- Meta / Annotation Processing (0.32) ----
        Path generatedDir = options.generatedDir();
        if (generatedDir == null) {
            Path parent = source.getParent();
            generatedDir = (parent == null ? Path.of(".") : parent).resolve(".jnative/generated");
        }
        boolean deterministic = options.deterministic();
        io.jnative.processor.ProcessorScheduler metaScheduler = new io.jnative.processor.ProcessorScheduler(
                io.jnative.processor.ProcessorRegistry.builtin(),
                generatedDir,
                deterministic,
                io.jnative.processor.ProcessorScheduler.DEFAULT_TIMEOUT_MS,
                java.util.Map.of());
        io.jnative.processor.ProcessorScheduler.Result metaResult =
                metaScheduler.run(ast, options.target() == null ? "native" : options.target(), reporter);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
        }
        if (options.explainMeta() && !metaResult.explain().isEmpty()) {
            for (String line : metaResult.explain()) {
                System.out.println(line);
            }
        }
        // Merge into user source (not stdlib-expanded text), then re-select stdlibs for generated refs.
        if (!metaResult.artifacts().isEmpty() && !userSource.contains("BEGIN GENERATED META")) {
            String mergedUser = io.jnative.processor.ProcessorScheduler.mergeGeneratedSources(
                    userSource, metaResult.artifacts());
            try {
                sourceText = io.jnative.stdlib.StdlibIncluder.prependStdlibs(mergedUser);
            } catch (IOException e) {
                reporter.error(SourceLocation.UNKNOWN,
                        "failed to reload stdlib after metaprogramming: " + e.getMessage());
                return fail(reporter);
            }
            reporter = new DiagnosticReporter();
            lexer = new Lexer(sourceText, fileName);
            tokens = lexer.tokenize();
            for (Token token : tokens) {
                if (token.type() == TokenType.ERROR) {
                    reporter.error(token.location(), token.lexeme());
                }
            }
            if (reporter.hasErrors()) {
                return fail(reporter);
            }
            parser = new Parser(tokens, reporter);
            ast = parser.parse(fileName);
            if (reporter.hasErrors()) {
                return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
            }
        }

        // ---- RPC (0.28): analyze @Rpc interfaces → generate dispatcher/client source ----
        io.jnative.rpc.RpcAnalyzer rpcAnalyzer = new io.jnative.rpc.RpcAnalyzer(reporter);
        java.util.List<io.jnative.rpc.RpcServiceModel> rpcServices = rpcAnalyzer.analyze(ast);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
        }
        if (!rpcServices.isEmpty() && !sourceText.contains("BEGIN GENERATED RPC")) {
            String generated = io.jnative.rpc.RpcCodeGenerator.generate(rpcServices);
            if (options.emitRpc()) {
                System.out.print(io.jnative.rpc.RpcSchemaGenerator.toReport(rpcServices));
            }
            if (options.emitRpcSchema() || options.emitRpc()) {
                try {
                    Path schemaOut = options.outputFile() != null
                            ? options.outputFile().getParent()
                            : source.getParent();
                    if (schemaOut == null) {
                        schemaOut = Path.of(".");
                    }
                    Files.createDirectories(schemaOut);
                    Files.writeString(schemaOut.resolve("rpc-schema.json"),
                            io.jnative.rpc.RpcSchemaGenerator.toJsonSchema(rpcServices),
                            StandardCharsets.UTF_8);
                } catch (IOException e) {
                    reporter.error(SourceLocation.UNKNOWN, "failed to write rpc-schema.json: " + e.getMessage());
                }
            }
            sourceText = sourceText + "\n\n" + generated;
            reporter = new DiagnosticReporter();
            lexer = new Lexer(sourceText, fileName);
            tokens = lexer.tokenize();
            for (Token token : tokens) {
                if (token.type() == TokenType.ERROR) {
                    reporter.error(token.location(), token.lexeme());
                }
            }
            if (reporter.hasErrors()) {
                return fail(reporter);
            }
            parser = new Parser(tokens, reporter);
            ast = parser.parse(fileName);
            if (reporter.hasErrors()) {
                return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
            }
            // Re-analyze after merge (generated has no @Rpc)
            rpcServices = new io.jnative.rpc.RpcAnalyzer(reporter).analyze(ast);
        } else if (options.emitRpc() && !rpcServices.isEmpty()) {
            System.out.print(io.jnative.rpc.RpcSchemaGenerator.toReport(rpcServices));
        }

        if (options.emitAst()) {
            System.out.print(AstPrinter.print(ast));
        }

        // Semantic
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        TypedCompilationUnit typed = analyzer.analyze(ast);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
        }

        EffectAnalyzer effectAnalyzer = new EffectAnalyzer(reporter);
        effectAnalyzer.analyze(typed, ast);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
        }

        if (options.emitEffects()) {
            System.out.print(effectAnalyzer.emitEffectsReport(typed));
            if (options.emitOnly()) {
                return new CompileResult(true, ast, null, null, null, reporter.diagnostics());
            }
        }

        GenericLowering genericLowering = new GenericLowering();
        typed = genericLowering.lower(typed, reporter);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
        }

        if (options.emitGenerics() || options.explainGenerics()) {
            System.out.print(genericLowering.emitReport());
            if (options.emitOnly()) {
                return new CompileResult(true, ast, null, null, null, reporter.diagnostics());
            }
        }

        if (options.emitLayout()) {
            for (var ns : typed.nativeStructs().values()) {
                if (ns.layout() != null) {
                    System.out.print(ns.layout().describe());
                    System.out.println();
                }
            }
            return new CompileResult(true, ast, null, null, null, reporter.diagnostics());
        }

        // IR
        IrModule ir = new IrGenerator().generate(typed);

        if (options.emitAsync()) {
            System.out.print(AsyncReport.emit(ir, typed));
        }

        new AsyncLoweringPass().run(ir, options);

        if (options.emitAsync() && options.emitOnly()) {
            return new CompileResult(true, ast, ir, null, null, reporter.diagnostics());
        }

        if (options.emitIrBeforeOpt()) {
            System.out.print(IrPrinter.print(ir));
        }

        OptimizationReport optReport = null;
        if (options.optLevel() > 0 || options.emitIrBeforeOpt() || options.optReport()
                || options.emitIrAfterOpt() || options.emitEscapeAnalysis()
                || options.explainOpt() || options.verifyIr()) {
            OptimizationPipeline pipeline = OptimizationPipeline.forLevel(options.optLevel());
            optReport = pipeline.run(ir, typed, options);
        }

        if (options.emitIrAfterOpt() || (options.emitIr() && options.optLevel() > 0)) {
            System.out.print(IrPrinter.print(ir));
        } else if (options.emitIr()) {
            System.out.print(IrPrinter.print(ir));
        }

        if (options.optReport() && optReport != null) {
            System.out.print(optReport);
        }

        // ---- wasm32-browser: skip host LLVM, emit .wasm + JS ----
        if (options.isWasmBrowserTarget()) {
            try {
                io.jnative.layout.LayoutConfig.configureWasm32();
                Path outDir = options.outputFile() != null
                        ? options.outputFile()
                        : source.toAbsolutePath().getParent().resolve("dist");
                if (outDir != null && outDir.getFileName() != null
                        && outDir.getFileName().toString().endsWith(".wasm")) {
                    outDir = outDir.getParent();
                }
                var artifact = new io.jnative.backend.wasm.WasmBrowserBackend().compile(
                        new io.jnative.backend.BackendRequest(
                                source, sourceText, typed, ir, options, outDir));
                System.out.println("wasm32-browser: " + artifact.primaryOutput());
                for (Path p : artifact.secondaryOutputs()) {
                    System.out.println("  + " + p);
                }
                if (options.runAfterBuild()) {
                    startWasmDevServer(outDir);
                }
                return new CompileResult(true, ast, ir, null, artifact.primaryOutput(), reporter.diagnostics());
            } catch (Exception e) {
                reporter.error(SourceLocation.UNKNOWN, "wasm backend failed: " + e.getMessage());
                return new CompileResult(false, ast, ir, null, null, reporter.diagnostics());
            } finally {
                io.jnative.layout.LayoutConfig.configureNative64();
            }
        }

        // LLVM
        String llvmIr = new LlvmIrGenerator().generate(ir);
        if (options.emitLlvm()) {
            System.out.print(llvmIr);
        }

        if (options.emitOnly()) {
            return new CompileResult(true, ast, ir, llvmIr, null, reporter.diagnostics());
        }

        // Native link
        Optional<Toolchain> toolchain = Toolchain.detect();
        if (toolchain.isEmpty()) {
            reporter.error(SourceLocation.UNKNOWN,
                    "LLVM/clang toolchain not found.\n"
                            + "Install LLVM and ensure clang is available in PATH.");
            return new CompileResult(false, ast, ir, llvmIr, null, reporter.diagnostics());
        }

        Path output = resolveOutput(options, source, toolchain.get().platform());
        Path llFile = null;
        Path nativeDir = null;
        try {
            llFile = Files.createTempFile("jnative-", ".ll");
            Files.writeString(llFile, llvmIr, StandardCharsets.UTF_8);

            nativeDir = Files.createTempDirectory("jnative-native-");
            String irText = IrPrinter.print(ir);
            boolean includeUi = irText.contains("jnative_ui_");
            boolean includeGraphics = irText.contains("jnative_gfx_");
            boolean includePoll = irText.contains("jnative_poll_")
                    || irText.contains("jnative_plat_set_nonblocking");
            boolean includeCrypto = irText.contains("jnative_secure_random")
                    || irText.contains("jnative_bit_and")
                    || irText.contains("jnative_crypto");
            boolean includeTls = irText.contains("jnative_tls_")
                    || irText.contains("TlsConnection")
                    || irText.contains("TlsContext");
            boolean headless = options.headless()
                    || "headless".equalsIgnoreCase(options.uiBackend())
                    || "1".equals(System.getenv("JNATIVE_HEADLESS"));
            java.util.List<Path> runtimeSources = io.jnative.backend.NativeRuntimeSources
                    .materializeAll(
                            nativeDir,
                            toolchain.get().platform(),
                            includeUi,
                            includeGraphics,
                            headless,
                            options.uiBackend(),
                            includePoll,
                            includeTls);
            java.util.List<Path> extraSources = new java.util.ArrayList<>(runtimeSources);
            extraSources.addAll(options.extraNativeSources());

            NativeLinker linker = new NativeLinker(toolchain.get());
            java.util.LinkedHashSet<String> libs = new java.util.LinkedHashSet<>(ir.nativeLibraries());
            libs.addAll(options.linkLibraries());
            /* @Native(library="jnative_crypto") symbols are in the linked object, not a .dylib */
            libs.remove("jnative_crypto");
            libs.remove("jnative_tls");
            java.util.List<String> linkFlags = new java.util.ArrayList<>(options.extraLinkFlags());
            if (includeTls) {
                addOpenSslLinkFlags(linkFlags, libs, toolchain.get().platform());
            }
            if (includeGraphics || includeUi) {
                libs.add("z");
                linkFlags.add("-lm");
            }
            /* libm for Math stdlib (Linux); Darwin provides these in libSystem. */
            boolean needsLibm = irText.contains("declare double @sqrt(")
                    || irText.contains("declare double @pow(")
                    || irText.contains("declare double @sin(")
                    || irText.contains("declare double @cos(")
                    || irText.contains("declare double @log(")
                    || irText.contains("declare double @hypot(")
                    || irText.contains("declare double @cbrt(");
            if (needsLibm
                    && toolchain.get().platform() != Platform.MACOS_ARM64
                    && toolchain.get().platform() != Platform.MACOS_X86_64) {
                linkFlags.add("-lm");
            }
            boolean linkCocoa = includeUi && !headless
                    && (toolchain.get().platform() == Platform.MACOS_ARM64
                    || toolchain.get().platform() == Platform.MACOS_X86_64)
                    && !"gtk".equalsIgnoreCase(options.uiBackend())
                    && !"headless".equalsIgnoreCase(options.uiBackend());
            if (linkCocoa) {
                linkFlags.add("-framework");
                linkFlags.add("Cocoa");
                linkFlags.add("-framework");
                linkFlags.add("AppKit");
            }
            boolean linkSecurity =
                    (toolchain.get().platform() == Platform.MACOS_ARM64
                    || toolchain.get().platform() == Platform.MACOS_X86_64);
            if (linkSecurity) {
                linkFlags.add("-framework");
                linkFlags.add("Security");
            }
            if ("gui".equalsIgnoreCase(options.appType())
                    && toolchain.get().platform() == Platform.WINDOWS_X86_64) {
                linkFlags.add("-Wl,--subsystem,windows");
            }
            NativeLinker.LinkResult linkResult = linker.link(
                    llFile, output, libs, options.libraryPaths(), extraSources,
                    options.optLevel(), linkFlags);
            if (!linkResult.success()) {
                reporter.error(SourceLocation.UNKNOWN,
                        "clang failed with exit code " + linkResult.exitCode()
                                + (linkResult.output().isBlank() ? "" : "\n" + linkResult.output()));
                return new CompileResult(false, ast, ir, llvmIr, null, reporter.diagnostics());
            }
            return new CompileResult(true, ast, ir, llvmIr, output, reporter.diagnostics());
        } catch (IOException e) {
            reporter.error(SourceLocation.UNKNOWN, "I/O error: " + e.getMessage());
            return new CompileResult(false, ast, ir, llvmIr, null, reporter.diagnostics());
        } finally {
            if (nativeDir != null && !options.keepTemps()) {
                try (var walk = Files.walk(nativeDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort
                        }
                    });
                } catch (IOException ignored) {
                    // best-effort
                }
            }
            if (llFile != null && !options.keepTemps()) {
                try {
                    Files.deleteIfExists(llFile);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private static void addOpenSslLinkFlags(
            java.util.List<String> linkFlags,
            java.util.Set<String> libs,
            Platform platform
    ) {
        String root = System.getenv("OPENSSL_ROOT_DIR");
        if (root == null || root.isBlank()) {
            root = System.getenv("OPENSSL_DIR");
        }
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (root != null && !root.isBlank()) {
            candidates.add(root);
        }
        candidates.add("/opt/homebrew/opt/openssl@3");
        candidates.add("/opt/homebrew/opt/openssl");
        candidates.add("/usr/local/opt/openssl@3");
        candidates.add("/usr/local/opt/openssl");
        candidates.add("/usr");
        String chosen = null;
        for (String c : candidates) {
            java.nio.file.Path inc = java.nio.file.Path.of(c, "include", "openssl", "ssl.h");
            if (java.nio.file.Files.isRegularFile(inc)
                    || java.nio.file.Files.isRegularFile(java.nio.file.Path.of(c, "include", "ssl.h"))) {
                chosen = c;
                break;
            }
        }
        if (chosen != null) {
            java.nio.file.Path include = java.nio.file.Path.of(chosen, "include");
            java.nio.file.Path libDir = java.nio.file.Path.of(chosen, "lib");
            if (java.nio.file.Files.isDirectory(include)) {
                linkFlags.add("-I" + include.toAbsolutePath().normalize());
            }
            if (java.nio.file.Files.isDirectory(libDir)) {
                linkFlags.add("-L" + libDir.toAbsolutePath().normalize());
            }
        }
        libs.add("ssl");
        libs.add("crypto");
        if (platform == Platform.MACOS_ARM64 || platform == Platform.MACOS_X86_64) {
            // rpath for Homebrew dylibs when not using system openssl
            if (chosen != null && chosen.contains("homebrew")) {
                linkFlags.add("-Wl,-rpath," + java.nio.file.Path.of(chosen, "lib").toAbsolutePath());
            }
        }
    }

    private static Path resolveOutput(CompilerOptions options, Path source, Platform platform) {
        if (options.outputFile() != null) {
            return options.outputFile().toAbsolutePath().normalize();
        }
        String base = source.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return source.getParent().resolve(platform.executableName(base));
    }

    /** Serve dist/ for `jnative run --target wasm32-browser`. */
    private static void startWasmDevServer(Path outDir) throws IOException {
        int port = 3000;
        System.out.println("Serving " + outDir.toAbsolutePath() + " at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
        // Minimal static server via Python (always available on macOS/Linux CI agents).
        try {
            new ProcessBuilder(
                    "python3", "-c",
                    "from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer\n"
                            + "import os\n"
                            + "os.chdir(r'''" + outDir.toAbsolutePath() + "''')\n"
                            + "class H(SimpleHTTPRequestHandler):\n"
                            + "  extensions_map={**SimpleHTTPRequestHandler.extensions_map,'': 'application/octet-stream','.wasm':'application/wasm'}\n"
                            + "ThreadingHTTPServer(('127.0.0.1'," + port + "),H).serve_forever()\n")
                    .inheritIO()
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static CompileResult fail(DiagnosticReporter reporter) {
        return new CompileResult(false, null, null, null, null, reporter.diagnostics());
    }
}
