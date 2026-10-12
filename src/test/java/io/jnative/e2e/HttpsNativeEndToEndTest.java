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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative HttpClient ↔ JNative HTTPS server (Round 4).
 * @author 张晓达
 */
class HttpsNativeEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void httpsGetRoundtrip() throws Exception {
        Path certDir = Path.of("examples/https/certs").toAbsolutePath();
        Assumptions.assumeTrue(Files.exists(certDir.resolve("server.crt")));
        Assumptions.assumeTrue(Files.exists(certDir.resolve("server.key")));
        int port = freePort();
        Path ready = tempDir.resolve("https-ready");
        Path serverBin = compile("""
                class RootHandler implements WebHandler {
                    public void handle(WebContext ctx) {
                        ctx.text("hello-https");
                    }
                }
                public class Server {
                    public static void main(String[] args) {
                        WebApp app = new WebApp();
                        app.get("/", new RootHandler());
                        TlsConfig tls = TlsConfig.builder()
                                .certificate("%s")
                                .privateKey("%s")
                                .alpn("http/1.1")
                                .build();
                        File.writeString("%s", "1");
                        app.listenTls(%d, tls);
                    }
                }
                """.formatted(
                esc(certDir.resolve("server.crt").toString()),
                esc(certDir.resolve("server.key").toString()),
                esc(ready.toString()),
                port), "HttpsSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            String stdout = run("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpRequest req = HttpRequest.create("GET", "https://127.0.0.1:%d/");
                            req.insecure(1);
                            HttpResponse resp = HttpClient.send(req);
                            System.out.println(resp.statusCode());
                            System.out.println(resp.bodyString());
                        }
                    }
                    """.formatted(port));
            assertTrue(stdout.contains("200"), stdout);
            assertTrue(stdout.contains("hello-https"), stdout);
        } finally {
            server.destroyForcibly();
        }
    }

    private void waitReadyFile(Path ready, Process server) throws Exception {
        long deadline = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(ready)) {
                Thread.sleep(100);
                return;
            }
            if (!server.isAlive()) {
                String out = new String(server.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                fail("server died: " + out);
            }
            Thread.sleep(20);
        }
        fail("ready missing");
    }

    private String run(String source) throws Exception {
        Path bin = compile(source, "HttpsCli");
        Process process = new ProcessBuilder(bin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), stdout);
        return stdout;
    }

    private Path compile(String source, String name) throws Exception {
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
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
