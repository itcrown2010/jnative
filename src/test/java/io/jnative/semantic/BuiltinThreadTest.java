/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author 张晓达
 */
class BuiltinThreadTest {
    @Test
    void mutexIsBuiltin() {
        String src = """
                public class Main {
                    static Mutex lock = new Mutex();
                    public static void main(String[] args) {}
                }
                """;
        DiagnosticReporter r = new DiagnosticReporter();
        var tokens = new Lexer(src, "Main.java").tokenize();
        var unit = new Parser(tokens, r).parse("Main.java");
        assertFalse(r.hasErrors(), r.diagnostics().toString());
        var typed = new SemanticAnalyzer(r).analyze(unit);
        assertTrue(typed.classes().containsKey("Thread"), "keys=" + typed.classes().keySet());
        assertTrue(typed.classes().containsKey("Mutex"), "keys=" + typed.classes().keySet());
        assertTrue(typed.classes().containsKey("AtomicInt"), "keys=" + typed.classes().keySet());
        assertFalse(r.hasErrors(), r.diagnostics().toString());
    }
}
