/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.MultiUnitFrontend;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static io.jnative.e2e.JavaReference.normalize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core language features (super calls, nested types, enums, varargs, multi-dimensional arrays, ...):
 * native output must equal the javac/java reference; misuse must be diagnosed.
 *
 * @author 张晓达
 */
class LanguageFeaturesEndToEndTest {

    @Test
    void superMethodCallsMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Super1.java.txt", "");
    }

    @Test
    void multiDimensionalArraysMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/MultiDim1.java.txt", "");
    }

    @Test
    void incrementDecrementValuesMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/IncDec1.java.txt", "");
    }

    @Test
    void nestedTypesMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Nested1.java.txt", "");
    }

    @Test
    void enumsWithMembersMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Enum1.java.txt", "");
    }

    @Test
    void varargsMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Varargs1.java.txt", "");
    }

    @Test
    void stringFormatAndPrintfMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Format1.java.txt", "");
    }

    @Test
    void lambdasWithWidePrimitiveSignaturesMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Lambda1.java.txt", "");
    }

    @Test
    void varLocalsMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Var1.java.txt", "");
    }

    @Test
    void labeledBreakContinueMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Labels1.java.txt", "");
    }

    @Test
    void switchExpressionsAndArrowsMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Switch1.java.txt", "");
    }

    @Test
    void miscSyntaxMatchesJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Syntax1.java.txt", "");
    }

    @Test
    void anonymousClassesMatchJava(@TempDir Path dir) throws Exception {
        compareWithJava(dir, "/e2e/stdlib/Anon1.java.txt", "");
    }

    @Test
    void anonymousClassMisuseIsDiagnosed() {
        String[][] cases = {
                {"abstract class B { B(int x) { } } class T { void f() { B b = new B(1) { }; } }",
                        "constructor arguments are not supported"},
                {"class T { void f() { Runnable r = new Runnable() { int c; public void run() { } }; } }",
                        "exactly one method"},
                {"interface I { void m(); } class T { void f() { I i = new I() { }; } }", "m"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze(c[0]);
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    @Test
    void newSyntaxMisuseIsDiagnosed() {
        String[][] cases = {
                {"class T { void f() { break nope; } }", "undefined label"},
                {"class T { void f() { a: { continue a; } } }", "not a loop label"},
                {"class T { void f() { var x; } }", "cannot infer"},
                {"class T { void f() { var x = null; } }", "cannot infer"},
                {"class T { int f(int x) { return switch (x) { case 1 -> 2; }; } }", "does not cover all possible"},
                {"class T { void f() { yield 1; } }", "error"},
                {"class T { int f(int x) { return switch (x) { case 1 -> 1; default -> \"s\"; }; } }", "incompatible"},
                {"class T { void f() { a: for (;;) { try { break a; } finally { } } } }",
                        "not supported"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze(c[0]);
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    @Test
    void varargsMisuseIsDiagnosed() {
        String[][] cases = {
                {"public class T { static void f(int... a, int b) { } }", "must be the last parameter"},
                {"public class T { static int f(int... a) { return 0; } void g() { f(\"x\"); } }",
                        "cannot be converted"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze(c[0]);
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    @Test
    void multiDimMisuseIsDiagnosed() {
        String[][] cases = {
                {"public class T { void f() { int[] a = {{1}, {2}}; } }", "nested array initializers"},
                {"public class T { void f() { int[][] a = new int[2][\"x\"]; } }", "array length must be int"},
                {"public class T { void f() { int[][] a = new int[2][3]; String s = a[0]; } }", "incompatible types"},
                {"public class T { int g() { return 1; } void f() { int[] a = new int[2]; a[g()]++; } }",
                        "evaluated twice"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze(c[0]);
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    @Test
    void superMisuseIsDiagnosed() {
        String[][] cases = {
                {"class T { static void f() { super.toString(); } }", "static context"},
                {"abstract class A { abstract void g(); } class T extends A { void g() { super.g(); } }",
                        "abstract method"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze(c[0]);
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    private static void compareWithJava(Path dir, String resource, String javaPrelude) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = JavaReference.resource(resource);
        String expected = normalize(JavaReference.run(dir.resolve("ref"), src, javaPrelude));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o0"), src, 0)));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o2"), src, 2)));
    }

    private static DiagnosticReporter analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        MultiUnitFrontend.analyzeProgram(ProgramSources.of(new SourceUnit("T.jn", src)), reporter);
        return reporter;
    }

    private static String messages(DiagnosticReporter r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics()) {
            sb.append(d).append('\n');
        }
        return sb.toString();
    }
}
