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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native executables that must fail with non-zero exit and a clear runtime error.
 * @author 张晓达
 */
class RuntimeErrorTest {

    @TempDir
    Path tempDir;

    @Test
    void nullFieldAccess() throws Exception {
        assertRuntimeError("""
                class Person { int age; }
                public class Main {
                    public static void main(String[] args) {
                        Person p = null;
                        System.out.println(p.age);
                    }
                }
                """, "null");
    }

    @Test
    void nullVirtualCall() throws Exception {
        assertRuntimeError("""
                class Animal {
                    void speak() { System.out.println(1); }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = null;
                        a.speak();
                    }
                }
                """, "null");
    }

    @Test
    void arrayOutOfBounds() throws Exception {
        assertRuntimeError("""
                public class Main {
                    public static void main(String[] args) {
                        int[] a = new int[3];
                        a[5] = 10;
                    }
                }
                """, "array index out of bounds");
    }

    @Test
    void negativeArraySize() throws Exception {
        assertRuntimeError("""
                public class Main {
                    public static void main(String[] args) {
                        int[] a = new int[-1];
                    }
                }
                """, "negative array size");
    }

    @Test
    void invalidCast() throws Exception {
        assertRuntimeError("""
                class Animal {}
                class Dog extends Animal {}
                class Cat extends Animal {}
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Cat();
                        Dog d = (Dog) a;
                    }
                }
                """, "invalid cast");
    }

    private void assertRuntimeError(String source, String messageFragment) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path javaFile = tempDir.resolve("err.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("err");

        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);

        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        assertTrue(finished, "process timed out");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertNotEquals(0, process.exitValue(), "expected non-zero exit, got output: " + output);
        assertTrue(output.contains("Exception in thread \"main\"") || output.contains("JNative runtime error"),
                output);
        if (messageFragment.equals("null")) {
            assertTrue(output.contains("NullPointerException") || output.contains("null"), output);
        } else if (messageFragment.contains("array index")) {
            assertTrue(output.contains("ArrayIndexOutOfBoundsException")
                    || output.contains("array index"), output);
        } else if (messageFragment.contains("negative array")) {
            assertTrue(output.contains("NegativeArraySizeException")
                    || output.contains("negative array"), output);
        } else if (messageFragment.contains("invalid cast")) {
            assertTrue(output.contains("ClassCastException") || output.contains("invalid cast"), output);
        } else {
            assertTrue(output.contains(messageFragment), output);
        }
    }
}
