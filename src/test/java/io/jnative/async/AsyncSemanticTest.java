package io.jnative.async;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.EffectAnalyzer;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsyncSemanticTest {

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
    void awaitOutsideAsyncRejected() {
        DiagnosticReporter r = analyze("""
                class User {}
                class S {
                    static User load() { return new User(); }
                    static void main(String[] args) {
                        User u = await S.load();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("await is only allowed")));
    }

    @Test
    void awaitNonFutureRejected() {
        DiagnosticReporter r = analyze("""
                class User {}
                class S {
                    async User load() { return new User(); }
                    static void main(String[] args) {
                    }
                }
                """);
        // parse async method ok; test await of non-future inside async
        r = analyze("""
                class User {}
                class S {
                    static User sync() { return new User(); }
                    async User load() {
                        User u = await S.sync();
                        return u;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("Future")));
    }

    @Test
    void asyncFutureReturnTypeRejected() {
        DiagnosticReporter r = analyze("""
                class User {}
                class S {
                    async Future<User> load() { return null; }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("must not be Future")));
    }

    @Test
    void validAsyncAwaitAccepted() {
        DiagnosticReporter r = analyze("""
                class User {
                    User() {}
                }
                class S {
                    async User loadAsync() { return new User(); }
                    async User load() {
                        User u = await loadAsync();
                        return u;
                    }
                }
                """);
        assertFalse(r.hasErrors(), () -> r.diagnostics().toString());
    }

    @Test
    void noGcWithAwaitRejected() {
        DiagnosticReporter r = analyze("""
                class User { User() {} }
                class S {
                    async User loadAsync() { return new User(); }
                    @NoGC
                    async User load() {
                        User u = await loadAsync();
                        return u;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("@NoGC")));
    }
}
