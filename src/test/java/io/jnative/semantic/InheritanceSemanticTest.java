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
class InheritanceSemanticTest {

    private DiagnosticReporter analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var unit = new Parser(new Lexer(src, "t.java").tokenize(), reporter).parse("t.java");
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(reporter).analyze(unit);
        }
        return reporter;
    }

    @Test
    void rejectsUnknownSuperclass() {
        DiagnosticReporter reporter = analyze("""
                class Dog extends Unknown {}
                public class Main { public static void main(String[] args) {} }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("undefined class 'Unknown'")));
    }

    @Test
    void rejectsSelfInheritance() {
        DiagnosticReporter reporter = analyze("""
                class A extends A {}
                public class Main { public static void main(String[] args) {} }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void rejectsCyclicInheritance() {
        DiagnosticReporter reporter = analyze("""
                class A extends B {}
                class B extends C {}
                class C extends A {}
                public class Main { public static void main(String[] args) {} }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("cyclic inheritance")));
    }

    @Test
    void allowsSubtypeAssignment() {
        DiagnosticReporter reporter = analyze("""
                class Animal {}
                class Dog extends Animal {}
                public class Main {
                    public static void main(String[] args) {
                        Dog d = new Dog();
                        Animal a = d;
                    }
                }
                """);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
    }

    @Test
    void rejectsDowncastWithoutCast() {
        DiagnosticReporter reporter = analyze("""
                class Animal {}
                class Dog extends Animal {}
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Animal();
                        Dog d = a;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void allowsNullToClass() {
        DiagnosticReporter reporter = analyze("""
                class Animal {}
                class Dog extends Animal {}
                public class Main {
                    public static void main(String[] args) {
                        Animal a = null;
                        Dog d = null;
                    }
                }
                """);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
    }

    @Test
    void rejectsIncompatibleOverride() {
        DiagnosticReporter reporter = analyze("""
                class Animal {
                    int foo(int x) { return x; }
                }
                class Dog extends Animal {
                    double foo(int x) { return 1.0; }
                }
                public class Main { public static void main(String[] args) {} }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("does not correctly override")));
    }
}
