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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class FfiEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void puts() throws Exception {
        assertStdout("""
                class LibC {
                    @Native(symbol = "puts")
                    static native int puts(String text);
                }
                public class Main {
                    public static void main(String[] args) {
                        LibC.puts("Hello Native World");
                    }
                }
                """, "Hello Native World\n");
    }

    @Test
    void abs() throws Exception {
        assertStdout("""
                class LibC {
                    @Native(symbol = "abs")
                    static native int abs(int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(LibC.abs(-123));
                    }
                }
                """, "123\n");
    }

    @Test
    void strlen() throws Exception {
        assertStdout("""
                class LibC {
                    @Native(symbol = "strlen")
                    static native long strlen(String text);
                }
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(LibC.strlen("JNative"));
                    }
                }
                """, "7\n");
    }

    @Test
    void nativeMemory() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        NativePtr p = NativeMemory.allocate(16L);
                        NativeMemory.putInt(p, 0L, 12345);
                        int x = NativeMemory.getInt(p, 0L);
                        System.out.println(x);
                        NativeMemory.free(p);
                    }
                }
                """, "12345\n");
    }

    @Test
    void coreAcceptancePutsAbs() throws Exception {
        assertStdout("""
                class LibC {
                    @Native(symbol = "puts")
                    static native int puts(String text);

                    @Native(symbol = "abs")
                    static native int abs(int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        LibC.puts("Hello from JNative");
                        int n = LibC.abs(-42);
                        System.out.println(n);
                    }
                }
                """, "Hello from JNative\n42\n");
    }

    @Test
    void gcPlusFfi() throws Exception {
        assertStdout("""
                class Person { int x; }
                class LibC {
                    @Native(symbol = "puts")
                    static native int puts(String text);
                }
                public class Main {
                    public static void main(String[] args) {
                        String s = "Hello";
                        for (int i = 0; i < 10000; i = i + 1) {
                            Person p = new Person();
                        }
                        System.gc();
                        LibC.puts(s);
                    }
                }
                """, "Hello\n", Map.of("JNATIVE_GC_STRESS", "1"));
    }

    @Test
    void emitIrShowsNativeCall() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("Emit.java");
        Files.writeString(javaFile, """
                class LibC {
                    @Native(symbol = "puts")
                    static native int puts(String text);
                }
                public class Main {
                    public static void main(String[] args) {
                        LibC.puts("x");
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setEmitIr(true);
        CompileResult irResult = new JNativeCompiler().compile(options);
        assertTrue(irResult.success(), () -> irResult.diagnostics().toString());
        // Capture via --emit-ir goes to stdout during compile; also check LLVM
        options.setEmitIr(false);
        options.setEmitLlvm(true);
        CompileResult llvmResult = new JNativeCompiler().compile(options);
        assertTrue(llvmResult.success());
        assertNotNull(llvmResult.llvmIr());
        assertTrue(llvmResult.llvmIr().contains("declare i32 @puts("), llvmResult.llvmIr());
        assertTrue(llvmResult.llvmIr().contains("call i32 @puts("), llvmResult.llvmIr());
    }

    @Test
    void primitiveNativeCallBaseline() throws Exception {
        // 1,000,000 primitive native calls — baseline only, no performance gate.
        assertStdout("""
                class LibC {
                    @Native(symbol = "abs")
                    static native int abs(int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        int n = 0;
                        for (int i = 0; i < 1000000; i = i + 1) {
                            n = LibC.abs(-1);
                        }
                        System.out.println(n);
                    }
                }
                """, "1\n");
    }

    @Test
    void sqliteOptional() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path probe = tempDir.resolve("sqlite_probe.c");
        Files.writeString(probe,
                "int sqlite3_libversion_number(void); int main(){return sqlite3_libversion_number()>0?0:1;}\n");
        Path probeOut = tempDir.resolve("sqlite_probe");
        ProcessBuilder pb = new ProcessBuilder(
                Toolchain.detect().get().clang().toString(),
                probe.toString(), "-o", probeOut.toString(), "-lsqlite3");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().readAllBytes();
        boolean ok = p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        Assumptions.assumeTrue(ok, "sqlite3 not available");

        assertStdout("""
                class Sqlite {
                    @Native(library = "sqlite3", symbol = "sqlite3_libversion_number")
                    static native int libversionNumber();
                }
                public class Main {
                    public static void main(String[] args) {
                        int v = Sqlite.libversionNumber();
                        if (v > 0) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "1\n");
    }

    private void assertStdout(String source, String expected) throws Exception {
        assertStdout(source, expected, Map.of());
    }

    private void assertStdout(String source, String expected, Map<String, String> env) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("Ffi.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("Ffi");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }
}
