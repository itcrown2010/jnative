/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Reliable async/await state-machine e2e (O0 + default opt).
 * @author 张晓达
 */
class AsyncAwaitEndToEndTest {

    @TempDir
    Path tmp;

    @Test
    void awaitTenPlusFiveIsFifteenNotTen() throws Exception {
        String src = """
                class Box {
                    int n;
                    Box(int n) { this.n = n; }
                }
                public class Main {
                    static int side;
                    async Box ten() { return new Box(10); }
                    async Box addFive() {
                        side = side + 1;
                        Box a = await ten();
                        side = side + 1;
                        return new Box(a.n + 5);
                    }
                    public static void main(String[] args) {
                        Main m = new Main();
                        Future<Box> f = m.addFive();
                        int spins = 0;
                        while (!Future.isComplete(f) && spins < 100000) { spins = spins + 1; }
                        System.out.println(((Box) Future.getCompleted(f)).n);
                        System.out.println(side);
                    }
                }
                """;
        assertO0AndOpt(src, "15\n2\n");
    }

    @Test
    void delayedResumeAndConcurrentCalls() throws Exception {
        String src = """
                class Box {
                    int n;
                    Box(int n) { this.n = n; }
                }
                class Completer extends Thread {
                    Future<Box> f;
                    int value;
                    public void run() {
                        Future.tryComplete(f, new Box(value));
                    }
                }
                public class Main {
                    async Box add(Future<Box> d, int k) {
                        Box a = await d;
                        return new Box(a.n + k);
                    }
                    public static void main(String[] args) {
                        Main m = new Main();
                        Future<Box> d1 = Future.pending();
                        Future<Box> d2 = Future.pending();
                        Future<Box> r1 = m.add(d1, 5);
                        Future<Box> r2 = m.add(d2, 7);
                        Completer c1 = new Completer();
                        c1.f = d1;
                        c1.value = 10;
                        Completer c2 = new Completer();
                        c2.f = d2;
                        c2.value = 20;
                        c1.start();
                        c2.start();
                        c1.join();
                        c2.join();
                        int spins = 0;
                        while ((!Future.isComplete(r1) || !Future.isComplete(r2)) && spins < 1000000) {
                            spins = spins + 1;
                        }
                        System.out.println(((Box) Future.getCompleted(r1)).n);
                        System.out.println(((Box) Future.getCompleted(r2)).n);
                    }
                }
                """;
        assertO0AndOpt(src, "15\n27\n");
    }

    @Test
    void twoSequentialAwaits() throws Exception {
        String src = """
                class Box {
                    int n;
                    Box(int n) { this.n = n; }
                }
                public class Main {
                    async Box one() { return new Box(1); }
                    async Box two() { return new Box(2); }
                    async Box sum() {
                        Box a = await one();
                        Box b = await two();
                        return new Box(a.n + b.n);
                    }
                    public static void main(String[] args) {
                        Future<Box> f = new Main().sum();
                        int spins = 0;
                        while (!Future.isComplete(f) && spins < 100000) { spins = spins + 1; }
                        System.out.println(((Box) Future.getCompleted(f)).n);
                    }
                }
                """;
        assertO0AndOpt(src, "3\n");
    }

    @Test
    void awaitFailureCompletesMethodFuture() throws Exception {
        String src = """
                class Box { int n; Box(int n) { this.n = n; } }
                public class Main {
                    async Box boom() {
                        Future<Box> bad = Future.failed(new RuntimeException("x"));
                        Box a = await bad;
                        return new Box(1);
                    }
                    public static void main(String[] args) {
                        Future<Box> f = new Main().boom();
                        int spins = 0;
                        while (!Future.isComplete(f) && spins < 100000) { spins = spins + 1; }
                        System.out.println(Future.getState(f));
                        System.out.println(Future.getCompleted(f) == null ? 1 : 0);
                    }
                }
                """;
        // state FAILED = 2
        assertO0AndOpt(src, "2\n1\n");
    }

    @Test
    void wasmRejectsOrUnsupportedAsync() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path file = tmp.resolve("W.java");
        Files.writeString(file, """
                class Box { int n; Box(int n) { this.n = n; } }
                public class W {
                    async Box m() { return new Box(1); }
                    public static void main(String[] args) {
                        Future<Box> f = new W().m();
                        System.out.println(Future.isComplete(f) ? 1 : 0);
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setOutputFile(tmp.resolve("wout"));
        options.setTarget("wasm32-browser");
        CompileResult cr = new JNativeCompiler().compile(options);
        // Either rejects async/callable machinery or fails for missing wasm async runtime.
        assertFalse(cr.success());
    }

    private void assertO0AndOpt(String src, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        assertEquals(expected, compileRun(tmp.resolve("o0"), src, 0), "O0");
        assertEquals(expected, compileRun(tmp.resolve("o1"), src, 1), "O1");
    }

    private String compileRun(Path out, String src, int opt) throws Exception {
        Path file = out.getParent().resolve(out.getFileName() + ".java");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setProgramSources(ProgramSources.of(SourceUnit.ofPath(file, src)));
        options.setOutputFile(out);
        options.setOptLevel(opt);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (a, b) -> a + "\n" + b));
        ProcessBuilder pb = new ProcessBuilder(cr.executable().toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(90, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            fail("timeout");
        }
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }
}
