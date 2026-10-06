package io.jnative.effects;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.EffectAnalyzer;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectSemanticTest {

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
    void noGcRejectsAllocation() {
        DiagnosticReporter r = analyze("""
                class Person {}
                public class C {
                    @NoGC
                    static void foo() {
                        Person p = new Person();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("@NoGC")));
    }

    @Test
    void noGcTransitiveWithPath() {
        DiagnosticReporter r = analyze("""
                class Person {}
                public class C {
                    @NoGC
                    static void foo() {
                        bar();
                    }
                    static void bar() {
                        Person p = new Person();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("effect path")));
    }

    @Test
    void noThrowRejectsThrow() {
        DiagnosticReporter r = analyze("""
                class E extends RuntimeException {
                    E() { super("x"); }
                }
                public class C {
                    @NoThrow
                    static void foo() {
                        throw new E();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("@NoThrow")));
    }

    @Test
    void noBlockRejectsSleep() {
        DiagnosticReporter r = analyze("""
                public class C {
                    @NoBlock
                    static void foo() {
                        Thread.sleep(1);
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("@NoBlock")));
    }

    @Test
    void deterministicRejectsBoth() {
        DiagnosticReporter r = analyze("""
                class Person {}
                public class C {
                    @Deterministic
                    static void foo() {
                        Person p = new Person();
                        Thread.sleep(1);
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("@Deterministic")
                || d.message().contains("@NoGC")
                || d.message().contains("@NoBlock")));
    }

    @Test
    void pureNativeAbsAllowed() {
        DiagnosticReporter r = analyze("""
                class Lib {
                    @Native(effects = "PURE")
                    static native int abs(int x);
                }
                public class C {
                    @Deterministic
                    static int compute() {
                        return Lib.abs(-5);
                    }
                }
                """);
        assertFalse(r.hasErrors(), () -> r.diagnostics().toString());
    }

    @Test
    void overrideContractViolation() {
        DiagnosticReporter r = analyze("""
                class Person {}
                class Base {
                    @NoGC
                    void m() {}
                }
                class Sub extends Base {
                    void m() {
                        Person p = new Person();
                    }
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream()
                .anyMatch(d -> d.message().contains("override effect contract violation")));
    }

    @Test
    void lambdaAllocationCreation() {
        DiagnosticReporter r = analyze("""
                public class C {
                    @NoGC
                    static void foo() {
                        int base = 1;
                        IntUnaryFunction f = x -> x + base;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("@NoGC")));
    }

    @Test
    void ordinaryFunctionAllowsAllocation() {
        DiagnosticReporter r = analyze("""
                class Person {}
                public class C {
                    static void foo() {
                        Person p = new Person();
                    }
                    public static void main(String[] args) {}
                }
                """);
        assertFalse(r.hasErrors(), () -> r.diagnostics().toString());
    }
}
