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
 * One-dimensional array initializers ({@code {1, 2}} / {@code new T[] {...}}) and instance field
 * initializers: native output must equal the javac/java reference; misuse must be diagnosed.
 *
 * @author 张晓达
 */
class ArrayInitEndToEndTest {

    @Test
    void arrayInitializersMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/ArrayInit1.java.txt", "");
    }

    @Test
    void charShortByteArrayIndicesMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/CharIndex1.java.txt", "");
    }

    @Test
    void staticInitializersUsingConstantsInArrayDimensions(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/StaticInitConst1.java.txt", "import java.util.ArrayList;\n");
    }

    @Test
    void instanceFieldInitializersMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/FieldInit1.java.txt", "import java.util.ArrayList;\n");
    }

    @Test
    void toStringAndConcatMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/ToStringConcat1.java.txt", "");
    }

    @Test
    void validInitializersAnalyzeClean() {
        DiagnosticReporter r = analyze("""
                public class T {
                    int[] f = {1, 2, 3,};
                    static String[] g = new String[] {"a"};
                    public void m() {
                        long[] l = {1, 2};
                        double[] d = {1, 2.5f};
                        String[] words = {"a", "b"};
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void misuseIsDiagnosed() {
        String[][] cases = {
                {"int[] a = {1, \"x\"};", "incompatible types"},
                {"int[] a = {1.5};", "incompatible types"},
                {"String[] a = {1};", "incompatible types"},
                {"int[] a = {{1}, {2}};", "nested array initializers are not supported"},
                {"int[] a = new int[] 5;", "array dimension missing"},
                {"int a = {1};", "expected expression"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze("public class T { public void f() { " + c[0] + " } }");
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    private static void compareWithJava(Path dir, String resource, String javaPrelude) throws Exception {
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