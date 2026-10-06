package io.jnative.compiler;

import io.jnative.ast.AstPrinter;
import io.jnative.ast.CompilationUnit;
import io.jnative.backend.NativeLinker;
import io.jnative.backend.Platform;
import io.jnative.backend.Toolchain;
import io.jnative.backend.llvm.LlvmIrGenerator;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.ir.IrGenerator;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrPrinter;
import io.jnative.lexer.Lexer;
import io.jnative.lexer.Token;
import io.jnative.lexer.TokenType;
import io.jnative.parser.Parser;
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

        String sourceText;
        try {
            sourceText = Files.readString(source, StandardCharsets.UTF_8);
            if (io.jnative.stdlib.StdlibIncluder.needsHttp(sourceText)) {
                sourceText = io.jnative.stdlib.StdlibIncluder.prependHttpStdlib(sourceText);
            }
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

        if (options.emitAst()) {
            System.out.print(AstPrinter.print(ast));
        }

        // Semantic
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        TypedCompilationUnit typed = analyzer.analyze(ast);
        if (reporter.hasErrors()) {
            return new CompileResult(false, ast, null, null, null, reporter.diagnostics());
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
        if (options.emitIr()) {
            System.out.print(IrPrinter.print(ir));
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
            java.util.List<Path> socketSources = io.jnative.backend.NativeRuntimeSources
                    .materializeSocketSources(nativeDir, toolchain.get().platform());

            NativeLinker linker = new NativeLinker(toolchain.get());
            java.util.LinkedHashSet<String> libs = new java.util.LinkedHashSet<>(ir.nativeLibraries());
            libs.addAll(options.linkLibraries());
            NativeLinker.LinkResult linkResult = linker.link(
                    llFile, output, libs, options.libraryPaths(), socketSources);
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

    private static CompileResult fail(DiagnosticReporter reporter) {
        return new CompileResult(false, null, null, null, null, reporter.diagnostics());
    }
}
