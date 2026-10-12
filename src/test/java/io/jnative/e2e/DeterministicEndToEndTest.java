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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class DeterministicEndToEndTest {

    @TempDir
    Path tempDir;

    private static Path libDir;
    private static boolean fixtureReady;

    @BeforeAll
    static void buildCFixtures() throws Exception {
        var toolchain = Toolchain.detect().orElse(null);
        Assumptions.assumeTrue(toolchain != null, "clang not available");

        libDir = Files.createTempDirectory("jnative-det-lib");
        String os = System.getProperty("os.name", "").toLowerCase();
        Path lib = os.contains("mac") || os.contains("darwin")
                ? libDir.resolve("libjnative_cbtest.dylib")
                : libDir.resolve("libjnative_cbtest.so");

        Path src = Path.of("src/test/resources/native_fixtures/callback_invoke.c").toAbsolutePath();
        Assumptions.assumeTrue(Files.isRegularFile(src));
        Path obj = libDir.resolve("callback_invoke.o");
        ProcessBuilder compile = new ProcessBuilder(
                toolchain.clang().toString(), "-c", src.toString(), "-o", obj.toString());
        compile.redirectErrorStream(true);
        Process cp = compile.start();
        String cout = new String(cp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assumptions.assumeTrue(cp.waitFor(30, TimeUnit.SECONDS) && cp.exitValue() == 0, cout);

        ProcessBuilder link = new ProcessBuilder(
                toolchain.clang().toString(), "-shared", obj.toString(), "-o", lib.toString(), "-lpthread");
        link.redirectErrorStream(true);
        Process lp = link.start();
        String lout = new String(lp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        fixtureReady = lp.waitFor(30, TimeUnit.SECONDS) && lp.exitValue() == 0;
        Assumptions.assumeTrue(fixtureReady, lout);
    }

    @Test
    void deterministicSumLoop() throws Exception {
        assertStdout("""
                public class Main {
                    @Deterministic
                    static int sum() {
                        int s = 0;
                        for (int i = 0; i < 10; i = i + 1) {
                            s = s + i;
                        }
                        return s;
                    }
                    public static void main(String[] args) {
                        System.out.println(sum());
                    }
                }
                """, "45\n");
    }

    @Test
    void deterministicCallback() throws Exception {
        assertStdout("""
                class CLib {
                    @Native(symbol = "invoke_callback", library = "jnative_cbtest")
                    static native int invokeCallback(NativePtr cb, int value);
                }
                public class Main {
                    @Deterministic
                    static int inc(int x) {
                        return x + 1;
                    }
                    public static void main(String[] args) {
                        NativeCallbackHandle h = Native.callback(x -> inc(x));
                        int out = CLib.invokeCallback(h.pointer(), 41);
                        System.out.println(out);
                        h.close();
                    }
                }
                """, "42\n");
    }

    private void assertStdout(String source, String expected) throws Exception {
        Path src = tempDir.resolve("Main.java");
        Files.writeString(src, source);
        Path out = tempDir.resolve("main_out");

        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setOutputFile(out);
        options.addLibraryPath(libDir);
        options.addLinkLibrary("jnative_cbtest");

        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder run = new ProcessBuilder(out.toString());
        run.redirectErrorStream(true);
        Process proc = run.start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, proc.exitValue(), output);
        assertEquals(expected, output);
    }
}
