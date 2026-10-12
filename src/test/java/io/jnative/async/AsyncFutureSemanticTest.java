/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.async;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.EffectAnalyzer;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class AsyncFutureSemanticTest {

    private DiagnosticReporter analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        if (!reporter.hasErrors()) {
            TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
            if (!reporter.hasErrors()) {
                new EffectAnalyzer(reporter).analyze(typed, unit);
            }
        }
        return reporter;
    }

    @Test
    void asyncMethodMustHaveBody() {
        DiagnosticReporter r = analyze("""
                class Box {
                    Box() {}
                }
                class S {
                    async Box load();
                }
                """);
        assertTrue(r.hasErrors());
    }

    @Test
    void awaitInsideNonAsyncMethodStillRejected() {
        DiagnosticReporter r = analyze("""
                class Box { Box() {} }
                class S {
                    async Box loadAsync() { return new Box(); }
                    static Box syncLoad() {
                        Box b = await S.loadAsync();
                        return b;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("await is only allowed")));
    }

    @Test
    void nestedAwaitInAsyncAccepted() {
        DiagnosticReporter r = analyze("""
                class Box { Box() {} }
                class S {
                    async Box inner() { return new Box(); }
                    async Box outer() {
                        Box a = await inner();
                        Box b = await inner();
                        return a;
                    }
                }
                """);
        assertFalse(r.hasErrors(), () -> r.diagnostics().toString());
    }
}
