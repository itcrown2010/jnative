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

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 4 Async integrity / RH-1 truth.
 * AsyncTcp* is a BLOCKING-ADAPTER: Async.blockingOnLoop + shared ThreadPoolExecutor
 * over blocking TcpSocket. EventLoop PollNative/kqueue is NOT used for these fds
 * (EventLoop.registerFd has no production callers). Not event-driven async I/O.
 * Cancel-vs-completion is proven at JnFuture level (single terminal state).
 * @author 张晓达
 */
class AsyncIoRound4EndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void cancelVsCompletionNoDoubleComplete() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        JnFuture f = new JnFuture();
                        int cancelled = 0;
                        if (f.tryCancel()) {
                            cancelled = 1;
                        }
                        int completed = 0;
                        if (f.tryComplete("x")) {
                            completed = 1;
                        }
                        int failed = 0;
                        if (f.tryFail(new RuntimeException("x"))) {
                            failed = 1;
                        }
                        int stateOk = 0;
                        if (f.getState() == JnFuture.CANCELLED) {
                            stateOk = 1;
                        }
                        System.out.println(cancelled);
                        System.out.println(completed);
                        System.out.println(failed);
                        System.out.println(stateOk);
                    }
                }
                """, "1\n0\n0\n1\n");
    }

    @Test
    void manyIdleTcpConnectionsSingleProcess() throws Exception {
        int port = freePort();
        int n = 100;
        Path ready = tempDir.resolve("idle-ready");
        Path clientDone = tempDir.resolve("idle-client-done");
        Path serverBin = compile("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        File.writeString("%s", "1");
                        int i = 0;
                        while (i < %d) {
                            TcpSocket s = server.accept();
                            i = i + 1;
                        }
                        while (!File.exists("%s")) {
                            Thread.sleep(20);
                        }
                        server.close();
                        System.out.println(%d);
                    }
                }
                """.formatted(port, esc(ready.toString()), n, esc(clientDone.toString()), n), "IdleTcpSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            Path clientBin = compile("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket[] socks = new TcpSocket[%d];
                            int i = 0;
                            while (i < %d) {
                                socks[i] = TcpSocket.connect("127.0.0.1", %d);
                                i = i + 1;
                            }
                            File.writeString("%s", "1");
                            System.out.println(%d);
                            Thread.sleep(800);
                            i = 0;
                            while (i < %d) {
                                socks[i].close();
                                i = i + 1;
                            }
                            File.writeString("%s", "1");
                        }
                    }
                    """.formatted(n, n, port, esc(tempDir.resolve("client-ready").toString()), n, n,
                            esc(clientDone.toString())), "IdleTcpCli");
            Process client = new ProcessBuilder(clientBin.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            waitReadyFile(tempDir.resolve("client-ready"), client);
            int threads = countThreads(client.pid());
            // Idle TCP must not be thread-per-connection
            assertTrue(threads < 40, "expected <<100 OS threads, got " + threads);
            assertTrue(client.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, client.exitValue(), new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(server.waitFor(30, TimeUnit.SECONDS));
        } finally {
            try {
                Files.writeString(clientDone, "1");
            } catch (Exception ignored) {
            }
            server.destroyForcibly();
        }
    }

    @Test
    void tcpEchoWithGcStress() throws Exception {
        int port = freePort();
        Path ready = tempDir.resolve("gc-ready");
        Path serverBin = compile("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        File.writeString("%s", "1");
                        int i = 0;
                        while (i < 30) {
                            TcpSocket c = server.accept();
                            String s = c.readString(3);
                            c.writeString(s);
                            c.close();
                            i = i + 1;
                        }
                        server.close();
                    }
                }
                """.formatted(port, esc(ready.toString())), "GcTcpSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int i = 0;
                            int ok = 1;
                            while (i < 30) {
                                TcpSocket s = TcpSocket.connect("127.0.0.1", %d);
                                s.writeString("abc");
                                String got = s.readString(3);
                                if (!got.equals("abc")) {
                                    ok = 0;
                                }
                                s.close();
                                i = i + 1;
                            }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n", Map.of("JNATIVE_GC_STRESS", "1"));
        } finally {
            server.destroyForcibly();
        }
    }

    private static int countThreads(long pid) throws Exception {
        Process p = new ProcessBuilder("ps", "-M", String.valueOf(pid))
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(5, TimeUnit.SECONDS);
        int lines = 0;
        for (String line : out.split("\n")) {
            if (!line.isBlank()) {
                lines++;
            }
        }
        return Math.max(0, lines - 1);
    }

    private void waitReadyFile(Path ready, Process proc) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(ready)) {
                return;
            }
            if (!proc.isAlive()) {
                fail("process died before ready");
            }
            Thread.sleep(20);
        }
        fail("ready missing: " + ready);
    }

    private void assertStdout(String source, String expected) throws Exception {
        assertStdout(source, expected, Map.of());
    }

    private void assertStdout(String source, String expected, Map<String, String> env) throws Exception {
        Path bin = compile(source, "AsyncR4");
        ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }

    private Path compile(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Path javaFile = tempDir.resolve(name + System.nanoTime() + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name + System.nanoTime() + "Bin");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        return out;
    }

    private static String esc(String path) {
        return path.replace("\\", "\\\\");
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
