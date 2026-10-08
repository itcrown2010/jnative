package io.jnative.compiler;

import io.jnative.ast.CompilationUnit;
import io.jnative.ast.ProgramAst;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.lexer.Lexer;
import io.jnative.lexer.Token;
import io.jnative.lexer.TokenType;
import io.jnative.parser.Parser;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.stdlib.StdlibIncluder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lex/parse each source unit independently, then run project-level semantic analysis.
 */
public final class MultiUnitFrontend {
    private MultiUnitFrontend() {
    }

    public record Result(
            ProgramAst program,
            TypedCompilationUnit typed,
            CompilationUnit flattened
    ) {
    }

    /**
     * Lightweight scan: collect simple names of {@code class}/{@code interface} declarations
     * across all units so each independent parse can recognize cross-CU type names
     * (including stdlib) without concatenating source text.
     */
    public static Set<String> discoverTypeSimpleNames(ProgramSources sources) {
        Set<String> names = new LinkedHashSet<>();
        if (sources == null) {
            return names;
        }
        for (SourceUnit unit : sources.units()) {
            Lexer lexer = new Lexer(unit.sourceText(), unit.path() == null ? "<memory>" : unit.path());
            List<Token> tokens = lexer.tokenize();
            for (int i = 0; i < tokens.size(); i++) {
                TokenType t = tokens.get(i).type();
                if ((t == TokenType.CLASS || t == TokenType.INTERFACE)
                        && i + 1 < tokens.size()
                        && tokens.get(i + 1).type() == TokenType.IDENTIFIER) {
                    names.add(tokens.get(i + 1).lexeme());
                }
            }
        }
        return names;
    }

    public static CompilationUnit parseUnit(SourceUnit unit, DiagnosticReporter reporter) {
        return parseUnit(unit, reporter, Set.of());
    }

    public static CompilationUnit parseUnit(
            SourceUnit unit, DiagnosticReporter reporter, Set<String> programTypeNames) {
        String path = unit.path() == null ? "<memory>" : unit.path();
        Lexer lexer = new Lexer(unit.sourceText(), path);
        List<Token> tokens = lexer.tokenize();
        for (Token token : tokens) {
            if (token.type() == TokenType.ERROR) {
                reporter.error(token.location(), token.lexeme());
            }
        }
        Parser parser = new Parser(tokens, reporter, programTypeNames);
        return parser.parse(path);
    }

    /**
     * Build program sources: selected stdlib units + caller units (deduped by path).
     */
    public static ProgramSources withStdlib(ProgramSources userAndDeps) throws IOException {
        String probe = userAndDeps.probeText();
        List<SourceUnit> stdlib = StdlibIncluder.collectStdlibSourceUnits(probe);
        Set<String> seen = new LinkedHashSet<>();
        List<SourceUnit> merged = new ArrayList<>();
        for (SourceUnit u : stdlib) {
            if (seen.add(u.path())) {
                merged.add(u);
            }
        }
        for (SourceUnit u : userAndDeps.units()) {
            if (seen.add(u.path())) {
                merged.add(u);
            }
        }
        return ProgramSources.of(merged);
    }

    public static Result analyzeProgram(ProgramSources sources, DiagnosticReporter reporter) {
        if (sources == null || sources.isEmpty()) {
            reporter.error(SourceLocation.UNKNOWN, "no source units");
            return null;
        }
        Set<String> programTypes = discoverTypeSimpleNames(sources);
        List<CompilationUnit> units = new ArrayList<>();
        for (SourceUnit src : sources.units()) {
            CompilationUnit cu = parseUnit(src, reporter, programTypes);
            units.add(cu);
        }
        if (reporter.hasErrors()) {
            return new Result(new ProgramAst(units), null, new ProgramAst(units).flattened("<program>"));
        }
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        TypedCompilationUnit typed = analyzer.analyze(units);
        return new Result(new ProgramAst(units), typed, typed.unit());
    }
}
