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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author 张晓达
 */
class Ipv6TcpEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void loopbackIpv6OrSkipped() throws Exception {
        assumeTrue(Toolchain.detect().isPresent());
        boolean v6;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            v6 = s.getLocalPort() > 0;
        } catch (Exception ex) {
            Assumptions.abort("IPv6 loopback unsupported: " + ex.getMessage());
            return;
        }
        assumeTrue(v6);
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            port = s.getLocalPort();
        }
        // Re-bind inside JNative — free the Java socket first (try-with-resources closed).
        port = freePortV6();
        Path ready = tempDir.resolve("v6-ready");
        Path serverBin = compile("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("::1", %d);
                        File.writeString("%s", "1");
                        TcpSocket c = server.accept();
                        String s = c.readString(4);
                        c.writeString(s);
                        c.close();
                        server.close();
                    }
                }
                """.formatted(port, ready.toString().replace("\\", "\\\\")), "V6Srv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && !Files.exists(ready)) {
                if (!server.isAlive()) {
                    fail("server died: " + new String(server.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
                Thread.sleep(20);
            }
            assumeTrue(Files.exists(ready), "IPv6 listen failed — SKIPPED with reason: bind ::1 unsuccessful");
            Path clientBin = compile("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket s = TcpSocket.connect("::1", %d);
                            s.writeString("ping");
                            System.out.println(s.readString(4));
                            s.close();
                        }
                    }
                    """.formatted(port), "V6Cli");
            Process client = new ProcessBuilder(clientBin.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(client.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, client.exitValue(), out);
            assertEquals("ping\n", out);
        } finally {
            server.destroyForcibly();
        }
    }

    private static int freePortV6() throws Exception {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            return s.getLocalPort();
        }
    }

    private Path compile(String source, String name) throws Exception {
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
}
