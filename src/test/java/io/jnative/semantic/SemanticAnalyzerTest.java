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
class SemanticAnalyzerTest {

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
    void rejectsIncompatibleInitializer() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    public static void main(String[] args) {
                        int x = true;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void rejectsUndefinedVariable() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    public static void main(String[] args) {
                        x = 1;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.toString().contains("undefined variable 'x'")));
    }

    @Test
    void rejectsUndefinedMethod() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    public static void main(String[] args) {
                        foo();
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("undefined method 'foo'")));
    }

    @Test
    void rejectsWrongArgumentCount() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    static int add(int a) { return a; }
                    public static void main(String[] args) {
                        add(1, 2);
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("expects 1 argument")));
    }

    @Test
    void acceptsValidProgram() {
        DiagnosticReporter reporter = analyze("""
                public class C {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        int x = add(1, 2);
                        System.out.println(x);
                    }
                }
                """);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
    }

    @Test
    void rejectsUndefinedClass() {
        DiagnosticReporter reporter = analyze("""
                public class Main {
                    public static void main(String[] args) {
                        Unknown x = new Unknown();
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void rejectsUnknownField() {
        DiagnosticReporter reporter = analyze("""
                class Person { int age; }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.foo = 1;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("unknown field 'foo'")));
    }

    @Test
    void rejectsWrongFieldType() {
        DiagnosticReporter reporter = analyze("""
                class Person { int age; }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.age = true;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void rejectsPrimitiveNull() {
        DiagnosticReporter reporter = analyze("""
                public class Main {
                    public static void main(String[] args) {
                        int x = null;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void rejectsThisInStatic() {
        DiagnosticReporter reporter = analyze("""
                public class Main {
                    static void foo() {
                        System.out.println(this);
                    }
                    public static void main(String[] args) {
                        foo();
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("this")));
    }

    @Test
    void rejectsDuplicateField() {
        DiagnosticReporter reporter = analyze("""
                class Person {
                    int age;
                    int age;
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("duplicate field")));
    }
}
