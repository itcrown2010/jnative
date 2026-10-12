/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.BuildProfile;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SanitizeMode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JNative 0.8 — RuntimeStabilitySuite.
 * Short deterministic stress runs always execute.
 * Long soaks (10m/30m/2h): set {@code JNATIVE_STABILITY_SOAK}; otherwise NOT_RUN (never fake PASS).
 * @author 张晓达
 */
@Tag("stability")
class RuntimeStabilitySuiteTest {

    @TempDir
    Path tempDir;

    @Test
    void gcStressDebugAndRelease() throws Exception {
        String src = """
                class Box {
                    int v;
                    Box next;
                    Box(int v) { this.v = v; }
                }
                public class Main {
                    @Native(symbol = "jn_gc2_minor")
                    static native void minorGc();
                    @Native(symbol = "jn_gc_user_verify_heap")
                    static native int verifyHeap();
                    static Box cycle(int n) {
                        Box head = new Box(n);
                        Box cur = head;
                        int i = 0;
                        while (i < 8) {
                            cur.next = new Box(i);
                            cur = cur.next;
                            i = i + 1;
                        }
                        cur.next = head;
                        return head;
                    }
                    public static void main(String[] args) {
                        Box keep = cycle(1);
                        int i = 0;
                        while (i < 5000) {
                            Box t = new Box(i);
                            t.next = keep;
                            String s = "x" + i;
                            int[] a = new int[16];
                            a[0] = i;
                            if ((i / 500) * 500 == i) {
                                minorGc();
                            }
                            i = i + 1;
                        }
                        System.gc();
                        if (keep.v != 1) {
                            System.out.println("GC_FAIL");
                            return;
                        }
                        if (verifyHeap() != 1) {
                            System.out.println("VERIFY_FAIL");
                            return;
                        }
                        System.out.println("GC_STRESS_PASS");
                    }
                }
                """;
        /* Soft GC stress (nursery half-full trigger). Every-alloc stress=1 is tracked separately. */
        assertPass(src, "GcStress", "GC_STRESS_PASS", 0, SanitizeMode.NONE, Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "soft",
                "JNATIVE_GC_VERIFY", "1"
        ));
        assertPass(src, "GcStressRel", "GC_STRESS_PASS", 2, SanitizeMode.NONE, Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "soft",
                "JNATIVE_GC_VERIFY", "1"
        ));
    }

    @Test
    void threadStress2_4_8_16() throws Exception {
        assertThreadWorkers(2);
        assertThreadWorkers(4);
        assertThreadWorkers(8);
        assertThreadWorkers(16);
    }

    private void assertThreadWorkers(int n) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        StringBuilder workers = new StringBuilder();
        StringBuilder starts = new StringBuilder();
        StringBuilder joins = new StringBuilder();
        for (int i = 0; i < n; i++) {
            workers.append("Worker w").append(i).append(" = new Worker();\n");
            starts.append("w").append(i).append(".start();\n");
            joins.append("w").append(i).append(".join();\n");
        }
        int expected = n * 2000;
        String src = """
                class Worker extends Thread {
                    public void run() {
                        int i = 0;
                        while (i < 2000) {
                            Main.lock.lock();
                            Main.counter = Main.counter + 1;
                            Main.lock.unlock();
                            String tmp = "t" + i;
                            i = i + 1;
                        }
                    }
                }
                public class Main {
                    static Mutex lock = new Mutex();
                    static int counter;
                    public static void main(String[] args) {
                        %s
                        %s
                        %s
                        System.gc();
                        System.out.println(counter);
                        if (counter != %d) {
                            System.out.println("THREAD_FAIL");
                            return;
                        }
                        System.out.println("THREAD_STRESS_PASS %d");
                    }
                }
                """.formatted(workers, starts, joins, expected, n);
        String out = compileRun(src, "Th" + n, 2, SanitizeMode.NONE, Map.of(), 180);
        assertTrue(out.contains("THREAD_STRESS_PASS " + n), out);
        assertFalse(out.contains("THREAD_FAIL"), out);
    }

    @Test
    void ffiExceptionStringArrayNetworkStress() throws Exception {
        assertPass("""
                class LibC {
                    @Native(symbol = "strlen")
                    static native long strlen(String text);
                    @Native(symbol = "abs")
                    static native int abs(int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        long sum = 0;
                        while (i < 3000) {
                            String s = "hello-" + i;
                            sum = sum + LibC.strlen(s);
                            int a = LibC.abs(0 - (i - (i / 100) * 100));
                            if (a < 0) {
                                System.out.println("FFI_FAIL");
                                return;
                            }
                            NativePtr p = NativeMemory.allocate(32L);
                            NativeMemory.putInt(p, 0L, i);
                            int x = NativeMemory.getInt(p, 0L);
                            NativeMemory.free(p);
                            if (x != i) {
                                System.out.println("FFI_MEM_FAIL");
                                return;
                            }
                            i = i + 1;
                        }
                        if (sum <= 0) {
                            System.out.println("FFI_SUM_FAIL");
                            return;
                        }
                        System.out.println("FFI_STRESS_PASS");
                    }
                }
                """, "FfiSt", "FFI_STRESS_PASS", 2, SanitizeMode.NONE, Map.of());

        assertPass("""
                class MyException extends RuntimeException {
                    MyException(String m) { super(m); }
                }
                public class Main {
                    static void deep() { throw new MyException("boom"); }
                    static void mid() { deep(); }
                    static void outer() {
                        try { mid(); } catch (MyException e) { System.out.println("caught"); }
                    }
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 200) {
                            try {
                                throw new MyException("x");
                            } catch (MyException e) {
                                /* ok */
                            } finally {
                                i = i + 1;
                            }
                        }
                        outer();
                        System.out.println("EX_STRESS_PASS");
                    }
                }
                """, "ExSt", "EX_STRESS_PASS", 2, SanitizeMode.NONE, Map.of());

        String strArr = """
                public class Main {
                    public static void main(String[] args) {
                        String empty = "";
                        String cn = "你好世界";
                        String longish = cn + cn + cn + "abc";
                        if (empty.length() != 0) { System.out.println("S_EMPTY_FAIL"); return; }
                        if (cn.length() < 4) { System.out.println("S_CN_FAIL"); return; }
                        int[] emptyA = new int[0];
                        int[] big = new int[1024];
                        big[0] = 1;
                        big[1023] = 9;
                        Object[] objs = new Object[8];
                        objs[0] = cn;
                        if (emptyA.length != 0) { System.out.println("A_EMPTY_FAIL"); return; }
                        if (big[1023] != 9) { System.out.println("A_BIG_FAIL"); return; }
                        System.out.println("STRING_ARRAY_PASS");
                    }
                }
                """;
        assertPass(strArr, "StrArr", "STRING_ARRAY_PASS", 0, SanitizeMode.NONE, Map.of());
        assertPass(strArr, "StrArrO2", "STRING_ARRAY_PASS", 2, SanitizeMode.NONE, Map.of());

        int port = freePort();
        String net = """
                class EchoServer extends Thread {
                    public void run() {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        TcpSocket client = server.accept();
                        String req = client.readString(64);
                        client.writeString(req);
                        client.close();
                        server.close();
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        EchoServer s = new EchoServer();
                        s.start();
                        TcpSocket c = TcpSocket.connect("127.0.0.1", %d);
                        c.writeString("ping");
                        String r = c.readString(64);
                        c.close();
                        s.join();
                        if (r == null) { System.out.println("NET_FAIL"); return; }
                        System.out.println("NET_STRESS_PASS");
                    }
                }
                """.formatted(port, port);
        assertPass(net, "NetSt", "NET_STRESS_PASS", 2, SanitizeMode.NONE, Map.of());
    }

    @Test
    void asanGcStressIfSupported() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Assumptions.assumeTrue(sanitizerWorks(SanitizeMode.ASAN), "ASan not supported on this clang");
        String src = """
                class Box { int v; Box(int v) { this.v = v; } }
                public class Main {
                    @Native(symbol = "jn_gc2_minor")
                    static native void minorGc();
                    @Native(symbol = "jn_gc_user_verify_heap")
                    static native int verifyHeap();
                    public static void main(String[] args) {
                        Box keep = new Box(7);
                        int i = 0;
                        while (i < 2000) {
                            Box t = new Box(i);
                            if ((i / 200) * 200 == i) minorGc();
                            i = i + 1;
                        }
                        if (keep.v != 7 || verifyHeap() != 1) {
                            System.out.println("ASAN_GC_FAIL");
                            return;
                        }
                        System.out.println("GC_STRESS_PASS");
                    }
                }
                """;
        assertPass(src, "GcAsan", "GC_STRESS_PASS", 1, SanitizeMode.ASAN, Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_VERIFY", "1",
                "ASAN_OPTIONS", "detect_leaks=0:halt_on_error=1"
        ));
    }

    @Test
    void ubsanSmokeIfSupported() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Assumptions.assumeTrue(sanitizerWorks(SanitizeMode.UBSAN), "UBSan not supported");
        assertPass("""
                public class Main {
                    public static void main(String[] args) {
                        int x = 1 + 2;
                        System.out.println(x);
                        System.out.println("STRING_ARRAY_PASS");
                    }
                }
                """, "UbSmoke", "STRING_ARRAY_PASS", 1, SanitizeMode.UBSAN, Map.of(
                "UBSAN_OPTIONS", "halt_on_error=1"
        ));
    }

    @Test
    void tsanThreadStressIfSupported() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Assumptions.assumeTrue(sanitizerWorks(SanitizeMode.TSAN), "TSan not supported");
        String src = """
                class Worker extends Thread {
                    public void run() {
                        int i = 0;
                        while (i < 1000) {
                            Main.lock.lock();
                            Main.counter = Main.counter + 1;
                            Main.lock.unlock();
                            i = i + 1;
                        }
                    }
                }
                public class Main {
                    static Mutex lock = new Mutex();
                    static int counter;
                    public static void main(String[] args) {
                        Worker a = new Worker();
                        Worker b = new Worker();
                        Worker c = new Worker();
                        Worker d = new Worker();
                        a.start(); b.start(); c.start(); d.start();
                        a.join(); b.join(); c.join(); d.join();
                        if (counter != 4000) { System.out.println("THREAD_FAIL"); return; }
                        System.out.println("THREAD_STRESS_PASS 4");
                    }
                }
                """;
        String out = compileRun(src, "ThTsan", 1, SanitizeMode.TSAN, Map.of(
                "TSAN_OPTIONS", "halt_on_error=1"
        ), 180);
        assertTrue(out.contains("THREAD_STRESS_PASS 4"), out);
    }

    @Test
    void llvmIrVerifyOnCompile() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Path javaFile = tempDir.resolve("VerifyOnly.java");
        Files.writeString(javaFile,
                "public class Main { public static void main(String[] a) { System.out.println(1); } }",
                StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("VerifyOnly");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(0);
        options.setVerifyLlvmIr(true);
        options.setBuildProfile(BuildProfile.DEBUG);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
    }

    @Test
    void soakMarkerDocumentsNotRunByDefault() {
        String soak = System.getenv("JNATIVE_STABILITY_SOAK");
        if (soak == null || soak.isBlank()) {
            System.out.println("SOAK_STATUS=NOT_RUN (set JNATIVE_STABILITY_SOAK=10m|30m|2h + run scripts/runtime-stability-0.8.sh)");
            return;
        }
        fail("Long soak requested (" + soak + ") — use scripts/runtime-stability-0.8.sh; do not fake PASS in JUnit");
    }

    private void assertPass(String src, String name, String token, int opt, SanitizeMode san, Map<String, String> env)
            throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        String out = compileRun(src, name + "_O" + opt, opt, san, env, 120);
        assertTrue(out.contains(token), out);
        assertFalse(out.toUpperCase(Locale.ROOT).contains("_FAIL"), out);
    }

    private String compileRun(String src, String name, int opt, SanitizeMode san, Map<String, String> env, int timeoutSec)
            throws Exception {
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(opt);
        options.setSanitizeMode(san);
        options.setDebugSymbols(true);
        options.setVerifyLlvmIr(true);
        options.setBuildProfile(opt == 0 ? BuildProfile.DEBUG : BuildProfile.RELEASE);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> name + " compile failed: " + cr.diagnostics());
        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        Map<String, String> e = new HashMap<>(pb.environment());
        e.putAll(env);
        e.put("JNATIVE_BUILD_ID", "stability-" + name);
        pb.environment().putAll(e);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(timeoutSec, TimeUnit.SECONDS), () -> "timeout " + name + "\n" + stdout);
        assertEquals(0, p.exitValue(), () -> "nonzero exit " + name + " code=" + p.exitValue() + "\n" + stdout);
        return stdout;
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static boolean sanitizerWorks(SanitizeMode mode) {
        try {
            Toolchain tc = Toolchain.detect().orElse(null);
            if (tc == null) return false;
            Path dir = Files.createTempDirectory("jn-san-probe");
            Path src = dir.resolve("p.c");
            Files.writeString(src, "int main(void){return 0;}\n");
            Path out = dir.resolve("p");
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(tc.clang().toString());
            cmd.add("-O0");
            cmd.add("-g");
            cmd.add(src.toString());
            cmd.add("-o");
            cmd.add(out.toString());
            if (mode == SanitizeMode.ASAN) cmd.add("-fsanitize=address");
            if (mode == SanitizeMode.UBSAN) cmd.add("-fsanitize=undefined");
            if (mode == SanitizeMode.TSAN) cmd.add("-fsanitize=thread");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
