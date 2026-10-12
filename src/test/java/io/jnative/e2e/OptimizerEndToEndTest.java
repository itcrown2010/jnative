/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class OptimizerEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void pointCalculateLoop() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        String source = """
                class Point {
                    int x;
                    int y;
                    Point(int x, int y) { this.x = x; this.y = y; }
                }
                public class Main {
                    static int calculate(int value) {
                        Point p = new Point(value, value + 1);
                        return p.x + p.y;
                    }
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int i = 0; i < 5; i = i + 1) {
                            sum = sum + calculate(i);
                        }
                        System.out.println(sum);
                    }
                }
                """;

        Path javaFile = tempDir.resolve("PointLoop.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);

        CompilerOptions irOpts = new CompilerOptions();
        irOpts.setSourceFile(javaFile);
        irOpts.setOptLevel(2);
        irOpts.setEmitIrAfterOpt(true);
        irOpts.setOptReport(true);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream prev = System.out;
        System.setOut(new PrintStream(buf));
        try {
            CompileResult irResult = new JNativeCompiler().compile(irOpts);
            assertTrue(irResult.success(), () -> irResult.diagnostics().toString());
        } finally {
            System.setOut(prev);
        }
        String combined = buf.toString(StandardCharsets.UTF_8);
        assertFalse(combined.contains("new Point"), () -> combined);
        assertTrue(combined.contains("scalar-replacement") || !combined.contains("new Point"));

        Path out = tempDir.resolve("pointloop");
        CompilerOptions runOpts = new CompilerOptions();
        runOpts.setSourceFile(javaFile);
        runOpts.setOutputFile(out);
        runOpts.setOptLevel(2);
        CompileResult result = new JNativeCompiler().compile(runOpts);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        // sum i=0..4 of (2*i+1) = 1+3+5+7+9 = 25
        assertEquals("25\n", execute(result.executable()));
    }

    @Test
    void stackHolderKeepsHeapPersonAliveUnderGc() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String source = """
                class Person {
                    int age;
                    Person(int age) { this.age = age; }
                }
                class Holder {
                    Person person;
                    Holder() { this.person = null; }
                }
                public class Main {
                    static Person make() { return new Person(7); }
                    public static void main(String[] args) {
                        Holder h = new Holder();
                        h.person = make();
                        System.gc();
                        System.out.println(h.person.age);
                    }
                }
                """;
        Path javaFile = tempDir.resolve("StackRoot.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream prev = System.out;
        System.setOut(new PrintStream(buf));
        try {
            CompilerOptions irOpts = new CompilerOptions();
            irOpts.setSourceFile(javaFile);
            irOpts.setOptLevel(2);
            irOpts.setEmitIrAfterOpt(true);
            assertTrue(new JNativeCompiler().compile(irOpts).success());
        } finally {
            System.setOut(prev);
        }
        String ir = buf.toString(StandardCharsets.UTF_8);
        assertTrue(ir.contains("new Holder") && ir.contains(" stack"), () -> ir);
        assertTrue(ir.contains("new Person") && !ir.contains("new Person size=16 metadata=@meta.Person stack"),
                () -> ir);

        Path out = tempDir.resolve("stackroot");
        CompilerOptions runOpts = new CompilerOptions();
        runOpts.setSourceFile(javaFile);
        runOpts.setOutputFile(out);
        runOpts.setOptLevel(2);
        CompileResult result = new JNativeCompiler().compile(runOpts);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertEquals("7\n", execute(result.executable(), Map.of("JNATIVE_GC_STRESS", "1")));
    }

    private static String execute(Path executable) throws Exception {
        return execute(executable, Map.of());
    }

    private static String execute(Path executable, Map<String, String> env) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(executable.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(bytes, StandardCharsets.UTF_8));
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
