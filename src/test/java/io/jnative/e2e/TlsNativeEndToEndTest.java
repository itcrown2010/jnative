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

/** Live OpenSSL TLS handshake E2E (Round 4).
 * @author 张晓达
 */
class TlsNativeEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void localhostTlsEchoWithInsecureDevMode() throws Exception {
        Path certDir = Path.of("examples/https/certs").toAbsolutePath();
        Assumptions.assumeTrue(Files.exists(certDir.resolve("server.crt")), "test cert missing");
        Assumptions.assumeTrue(Files.exists(certDir.resolve("server.key")), "test key missing");
        int port = freePort();
        String crt = certDir.resolve("server.crt").toString();
        String key = certDir.resolve("server.key").toString();
        Path ready = tempDir.resolve("tls-ready");
        Path serverBin = compile("""
                public class Server {
                    public static void main(String[] args) {
                        TlsConfig cfg = TlsConfig.builder()
                                .certificate("%s")
                                .privateKey("%s")
                                .alpn("http/1.1")
                                .build();
                        TlsContext ctx = TlsContext.server(cfg);
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        File.writeString("%s", "1");
                        TcpSocket tcp = server.accept();
                        TlsConnection tls = TlsConnection.accept(tcp, ctx);
                        byte[] buf = new byte[16];
                        tls.read(buf);
                        tls.write(Bytes.fromString("PONG"));
                        tls.close();
                        server.close();
                        ctx.close();
                    }
                }
                """.formatted(esc(crt), esc(key), port, esc(ready.toString())), "TlsSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            String stdout = run("""
                    public class Main {
                        public static void main(String[] args) {
                            TlsConfig cfg = TlsConfig.builder()
                                    .insecureSkipVerify(1)
                                    .serverName("localhost")
                                    .alpn("http/1.1")
                                    .build();
                            TlsConnection tls = TlsConnection.connect("127.0.0.1", %d, cfg);
                            tls.write(Bytes.fromString("PING"));
                            byte[] buf = new byte[16];
                            int n = tls.read(buf);
                            byte[] got = new byte[n];
                            Bytes.copy(buf, 0, got, 0, n);
                            System.out.println(Bytes.toString(got));
                            tls.close();
                        }
                    }
                    """.formatted(port));
            assertTrue(stdout.contains("PONG"), stdout);
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void defaultVerifyRejectsSelfSigned() throws Exception {
        Path certDir = Path.of("examples/https/certs").toAbsolutePath();
        Assumptions.assumeTrue(Files.exists(certDir.resolve("server.crt")));
        int port = freePort();
        String crt = certDir.resolve("server.crt").toString();
        String key = certDir.resolve("server.key").toString();
        Path ready = tempDir.resolve("tls-ready2");
        Path serverBin = compile("""
                public class Server {
                    public static void main(String[] args) {
                        TlsConfig cfg = TlsConfig.builder()
                                .certificate("%s")
                                .privateKey("%s")
                                .build();
                        TlsContext ctx = TlsContext.server(cfg);
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        File.writeString("%s", "1");
                        TcpSocket tcp = server.accept();
                        try {
                            TlsConnection.accept(tcp, ctx);
                        } catch (TlsException e) {
                        }
                        server.close();
                        ctx.close();
                    }
                }
                """.formatted(esc(crt), esc(key), port, esc(ready.toString())), "TlsRejSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            String stdout = run("""
                    public class Main {
                        public static void main(String[] args) {
                            int ok = 0;
                            try {
                                TlsConfig cfg = TlsConfig.builder()
                                        .serverName("localhost")
                                        .build();
                                TlsConnection.connect("127.0.0.1", %d, cfg);
                            } catch (TlsException e) {
                                ok = 1;
                            }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port));
            assertTrue(stdout.trim().endsWith("1"), stdout);
        } finally {
            server.destroyForcibly();
        }
    }

    private static String esc(String path) {
        return path.replace("\\", "\\\\");
    }

    private void waitReadyFile(Path ready, Process server) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(ready)) {
                return;
            }
            if (!server.isAlive()) {
                String out = new String(server.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                fail("server died before ready: " + out);
            }
            Thread.sleep(20);
        }
        fail("server not ready (flag missing)");
    }

    private String run(String source) throws Exception {
        Path bin = compile(source, "TlsCli");
        ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        return stdout;
    }

    private Path compile(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
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

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
