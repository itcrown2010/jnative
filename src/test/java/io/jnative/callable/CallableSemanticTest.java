/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.callable;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class CallableSemanticTest {

    private TypedCompilationUnit analyzeOk(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        return typed;
    }

    private DiagnosticReporter analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(reporter).analyze(unit);
        }
        return reporter;
    }

    @Test
    void lambdaIntUnaryFunction() {
        analyzeOk("""
                public class C {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> x + 1;
                        System.out.println(f.apply(41));
                    }
                }
                """);
    }

    @Test
    void capturingLambda() {
        analyzeOk("""
                public class C {
                    public static void main(String[] args) {
                        int base = 100;
                        IntUnaryFunction f = x -> x + base;
                        System.out.println(f.apply(23));
                    }
                }
                """);
    }

    @Test
    void rejectsNonEffectivelyFinalCapture() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    public static void main(String[] args) {
                        int base = 100;
                        IntUnaryFunction f = x -> x + base;
                        base = 200;
                        System.out.println(f.apply(23));
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("effectively final")));
    }

    @Test
    void staticMethodReference() {
        analyzeOk("""
                public class C {
                    static int doubleIt(int x) { return x * 2; }
                    public static void main(String[] args) {
                        IntUnaryFunction f = C::doubleIt;
                        System.out.println(f.apply(21));
                    }
                }
                """);
    }

    @Test
    void nativeCallbackRejectsCapture() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    public static void main(String[] args) {
                        int scale = 2;
                        Native.callback(x -> x * scale);
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("Native.callback does not support capturing")));
    }
}
