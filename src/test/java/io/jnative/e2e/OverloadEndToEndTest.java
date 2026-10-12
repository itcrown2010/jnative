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

/** Core Round 1 — method overload definition + call resolution.
 * @author 张晓达
 */
class OverloadEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void sameNameDifferentParamTypes() throws Exception {
        assertEquals("3\n3\n", run("""
                class M {
                    static int abs(int a) {
                        if (a < 0) {
                            return 0 - a;
                        }
                        return a;
                    }
                    static long abs(long a) {
                        if (a < 0L) {
                            return 0L - a;
                        }
                        return a;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(M.abs(0 - 3));
                        System.out.println(M.abs(0L - 3L));
                    }
                }
                """, "OvBasic"));
    }

    @Test
    void constructorOverloads() throws Exception {
        assertEquals("1\n2\n", run("""
                class Box {
                    int v;
                    Box() {
                        this.v = 1;
                    }
                    Box(int x) {
                        this.v = x;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Box a = new Box();
                        Box b = new Box(2);
                        System.out.println(a.v);
                        System.out.println(b.v);
                    }
                }
                """, "OvCtor"));
    }

    private String run(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(0);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
