package io.jnative.opt;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.ir.IrGenerator;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrPrinter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.EffectAnalyzer;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.compiler.CompilerOptions;

/**
 * @author 张晓达
 */
final class OptTestSupport {

    private OptTestSupport() {
    }

    static IrModule compileIr(String source, int optLevel) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(source, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        if (reporter.hasErrors()) {
            throw new IllegalStateException(reporter.diagnostics().toString());
        }
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        if (reporter.hasErrors()) {
            throw new IllegalStateException(reporter.diagnostics().toString());
        }
        new EffectAnalyzer(reporter).analyze(typed, unit);
        IrModule ir = new IrGenerator().generate(typed);
        if (optLevel > 0) {
            CompilerOptions options = new CompilerOptions();
            options.setOptLevel(optLevel);
            OptimizationPipeline.forLevel(optLevel).run(ir, typed, options);
        }
        return ir;
    }

    static String irText(String source, int optLevel) {
        return IrPrinter.print(compileIr(source, optLevel));
    }
}
