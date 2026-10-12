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
 * Regression: HTTP Range must stream via seek+chunked read (not File.readBytes whole file).
 * @author 张晓达
 */
class HttpRangeStreamingEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void rangePartialAndSuffixAnd416AndMultiReject() throws Exception {
        int port = freePort();
        Path root = tempDir.resolve("www");
        Files.createDirectories(root);
        // ~256 KiB patterned payload (large enough that whole-file buffering would be noticeable)
        byte[] payload = new byte[262144];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        Path big = root.resolve("big.bin");
        Files.write(big, payload);
        Path ready = tempDir.resolve("range-ready");
        Path serverBin = compileJn("""
                class PingHandler implements WebHandler {
                    public void handle(WebContext ctx) {
                        ctx.text("ok");
                    }
                }
                public class Server {
                    public static void main(String[] args) {
                        WebApp app = new WebApp();
                        app.get("/ping", new PingHandler());
                        app.staticFiles("/media", "%s");
                        File.writeString("%s", "1");
                        app.listen(%d);
                    }
                }
                """.formatted(esc(root.toString()), esc(ready.toString()), port), "RangeSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int port = %d;
                            int ok = 1;
                            HttpRequest r1 = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/media/big.bin");
                            r1.header("Range", "bytes=0-3");
                            HttpResponse a = HttpClient.send(r1);
                            if (a.statusCode() != 206) { ok = 0; }
                            byte[] ab = a.bodyBytes();
                            if (ab.length != 4) { ok = 0; }
                            if (ab[0] != 0) { ok = 0; }
                            if (ab[1] != 1) { ok = 0; }
                            if (ab[2] != 2) { ok = 0; }
                            if (ab[3] != 3) { ok = 0; }
                            String cr = a.headers().getOrEmpty("Content-Range");
                            if (cr.indexOf("bytes 0-3/262144") < 0) { ok = 0; }

                            HttpRequest r2 = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/media/big.bin");
                            r2.header("Range", "bytes=100-102");
                            HttpResponse b = HttpClient.send(r2);
                            if (b.statusCode() != 206) { ok = 0; }
                            byte[] bb = b.bodyBytes();
                            if (bb.length != 3) { ok = 0; }
                            if (bb[0] != 100) { ok = 0; }
                            if (bb[1] != 101) { ok = 0; }
                            if (bb[2] != 102) { ok = 0; }

                            HttpRequest r3 = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/media/big.bin");
                            r3.header("Range", "bytes=-5");
                            HttpResponse c = HttpClient.send(r3);
                            if (c.statusCode() != 206) { ok = 0; }
                            byte[] cb = c.bodyBytes();
                            if (cb.length != 5) { ok = 0; }
                            if (cb[0] != 95) { ok = 0; }

                            HttpRequest r4 = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/media/big.bin");
                            r4.header("Range", "bytes=999999-1000000");
                            HttpResponse d = HttpClient.send(r4);
                            if (d.statusCode() != 416) { ok = 0; }
                            String cr4 = d.headers().getOrEmpty("Content-Range");
                            if (cr4.indexOf("bytes */262144") < 0) { ok = 0; }

                            HttpRequest r5 = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/media/big.bin");
                            r5.header("Range", "bytes=0-1,2-3");
                            HttpResponse e = HttpClient.send(r5);
                            if (e.statusCode() != 416) { ok = 0; }

                            HttpRequest r6 = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/media/big.bin");
                            r6.header("Range", "bytes=262140-");
                            HttpResponse f = HttpClient.send(r6);
                            if (f.statusCode() != 206) { ok = 0; }
                            if (f.bodyBytes().length != 4) { ok = 0; }

                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void concurrentRanges() throws Exception {
        int port = freePort();
        Path root = tempDir.resolve("www2");
        Files.createDirectories(root);
        byte[] payload = new byte[65536];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xff);
        }
        Files.write(root.resolve("c.bin"), payload);
        Path ready = tempDir.resolve("range-ready2");
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        WebApp app = new WebApp();
                        app.staticFiles("/m", "%s");
                        File.writeString("%s", "1");
                        app.listen(%d);
                    }
                }
                """.formatted(esc(root.toString()), esc(ready.toString()), port), "RangeConc");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int port = %d;
                            int ok = 1;
                            int i = 0;
                            while (i < 8) {
                                int start = i * 1000;
                                int end = start + 99;
                                HttpRequest r = HttpRequest.create("GET", "http://127.0.0.1:" + port + "/m/c.bin");
                                r.header("Range", "bytes=" + String.valueOf(start) + "-" + String.valueOf(end));
                                HttpResponse resp = HttpClient.send(r);
                                if (resp.statusCode() != 206) { ok = 0; }
                                if (resp.bodyBytes().length != 100) { ok = 0; }
                                i = i + 1;
                            }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    private void waitReadyFile(Path ready, Process server) throws Exception {
        long deadline = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(ready)) {
                return;
            }
            if (!server.isAlive()) {
                fail("server died");
            }
            Thread.sleep(20);
        }
        fail("ready flag missing");
    }

    private void assertStdout(String source, String expected) throws Exception {
        Path bin = compileJn(source, "RangeClient");
        ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(180, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }

    private Path compileJn(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name + "Bin");
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
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
