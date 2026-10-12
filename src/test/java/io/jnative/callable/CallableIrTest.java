/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.callable;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.ir.IrGenerator;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrPrinter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class CallableIrTest {

    private IrModule generate(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        return new IrGenerator().generate(typed);
    }

    @Test
    void emitIrContainsMakeAndInvokeCallable() {
        IrModule module = generate("""
                public class C {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> x + 1;
                        System.out.println(f.apply(41));
                    }
                }
                """);
        String text = IrPrinter.print(module);
        assertTrue(text.contains("make_callable"), text);
        assertTrue(text.contains("invoke_callable"), text);
    }
}
