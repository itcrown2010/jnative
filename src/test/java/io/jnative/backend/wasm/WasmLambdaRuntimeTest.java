/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lambdas compile for wasm32-browser and must produce the same output as native (not just compile).
 *
 * @author 张晓达
 */
public class WasmLambdaRuntimeTest {

    @TempDir
    Path tmp;

    @Test
    void capturingLambdaRunsOnBrowserTarget() throws Exception {
        assertEquals(15, runExport("""
                public class WasmLam {
                    public static int run(int x) {
                        int k = 10;
                        IntUnaryFunction f = y -> y + k;
                        return f.apply(x);
                    }
                    public static void main(String[] args) { run(1); }
                }
                """, "WasmLam", "_run", 5));
    }

    @Test
    void collectionForEachLambdaRunsOnBrowserTarget() throws Exception {
        assertEquals(10, runExport("""
                public class WasmEach {
                    static int total;
                    public static int run(int x) {
                        ArrayList<String> list = new ArrayList<String>();
                        list.add("abc");
                        list.add("de");
                        total = x;
                        list.forEach(s -> total = total + s.length());
                        return total;
                    }
                    public static void main(String[] args) { run(1); }
                }
                """, "WasmEach", "WasmEach_run", 5));
    }

    @Test
    void printlnFormatsPrimitivesAndNullOnBrowserTarget() throws Exception {
        Assumptions.assumeTrue(nodeAvailable(), "node not available");
        Path dir = tmp.resolve("WasmPrint");
        Files.createDirectories(dir);
        Path file = dir.resolve("WasmPrint.java");
        Files.writeString(file, """
                public class WasmPrint {
                    public static void main(String[] args) {
                        System.out.println(2);
                        System.out.println(-12345);
                        System.out.println("hi");
                        System.out.println(true);
                        System.out.println('x');
                        long big = -9000000000L;
                        System.out.println(big);
                        String n = null;
                        System.out.println(n);
                        System.out.println("a" + 5);
                        double a = 0.1;
                        double b = 0.2;
                        System.out.println(a + b);
                        System.out.println(100.0);
                        System.out.println(1.5e10);
                        System.out.println(0.00001);
                        System.out.println(0.001);
                        System.out.println(9999999.0);
                        System.out.println(10000000.0);
                        System.out.println(-0.5);
                        double zero = 0.0;
                        System.out.println(zero / zero);
                        System.out.println(1.0 / zero);
                        float f = 0.1f;
                        System.out.println(f);
                        float g = 16777216.0f;
                        System.out.println(g);
                        System.out.println("v=" + 2.5);
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setOutputFile(dir.resolve("out"));
        options.setTarget("wasm32-browser");
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
        Path wasm = dir.resolve("out").resolve("app.wasm");
        if (!Files.exists(wasm)) {
            wasm = dir.resolve("app.wasm");
        }
        assertEquals("2\n-12345\nhi\ntrue\nx\n-9000000000\nnull\na5\n"
                + "0.30000000000000004\n100.0\n1.5E10\n1.0E-5\n0.001\n9999999.0\n1.0E7\n-0.5\nNaN\nInfinity\n"
                + "0.1\n1.6777216E7\nv=2.5", WasmNodeHarness.runMain(wasm, 30));
    }

    private int runExport(String source, String cls, String export, int arg) throws Exception {
        Assumptions.assumeTrue(nodeAvailable(), "node not available");
        Path dir = tmp.resolve(cls);
        Files.createDirectories(dir);
        Path file = dir.resolve(cls + ".java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setOutputFile(dir.resolve("out"));
        options.setTarget("wasm32-browser");
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
        Path wasm = dir.resolve("out").resolve("app.wasm");
        if (!Files.exists(wasm)) {
            wasm = dir.resolve("app.wasm");
        }
        assertTrue(Files.exists(wasm), "wasm output missing under " + dir);
        return WasmNodeHarness.callInt(wasm, export, arg, 30);
    }

    private static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
