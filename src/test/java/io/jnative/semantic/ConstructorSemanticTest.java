/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class ConstructorSemanticTest {

    private DiagnosticReporter analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        new SemanticAnalyzer(reporter).analyze(unit);
        return reporter;
    }

    @Test
    void constructorCycleIsError() {
        DiagnosticReporter r = analyze("""
                class Person {
                    Person() { this(1); }
                    Person(int x) { this(); }
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream()
                .anyMatch(d -> d.message().contains("cycle")));
    }

    @Test
    void thisMustBeFirst() {
        DiagnosticReporter r = analyze("""
                class Person {
                    int age;
                    Person() {
                        age = 1;
                        this(2);
                    }
                    Person(int x) { this.age = x; }
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(r.hasErrors());
    }
}
