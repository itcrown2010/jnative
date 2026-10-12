/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.effects;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrPrinter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.EffectAnalyzer;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.ir.IrGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class EffectIrTest {

    private String generateIr(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        new EffectAnalyzer(reporter).analyze(typed, unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        IrModule module = new IrGenerator().generate(typed);
        return IrPrinter.print(module);
    }

    @Test
    void noGcLoopHasNoSafepointPoll() {
        String ir = generateIr("""
                public class C {
                    @NoGC
                    static int sum() {
                        int s = 0;
                        for (int i = 0; i < 100; i = i + 1) {
                            s = s + i;
                        }
                        return s;
                    }
                    public static void main(String[] args) {
                        System.out.println(sum());
                    }
                }
                """);
        int pollCount = countOccurrences(ir, "safepoint.poll");
        assertEquals(0, pollCount, () -> "expected no safepoint polls in @NoGC loop IR:\n" + ir);
    }

    @Test
    void ordinaryLoopHasSafepointPoll() {
        String ir = generateIr("""
                public class C {
                    static int sum() {
                        int s = 0;
                        for (int i = 0; i < 100; i = i + 1) {
                            s = s + i;
                        }
                        return s;
                    }
                    public static void main(String[] args) {
                        System.out.println(sum());
                    }
                }
                """);
        assertTrue(ir.contains("safepoint.poll"), () -> "expected loop backedge safepoint:\n" + ir);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
