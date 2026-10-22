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
 * PgMd5 RFC vectors + sslmode=require must refuse silent plaintext.
 *
 * @author 张晓达
 */
class PgMd5AndSslModeEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void md5AbcVectorAndSslRequireRejected() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        String src = """
                public class Main {
                    @Native(symbol = "exit")
                    static native void exitProcess(int code);

                    public static void main(String[] args) {
                        String h = PgMd5.hashBytes(Bytes.fromString("abc"));
                        if (!h.equals("900150983cd24fb0d6963f7d28e17f72")) {
                            System.out.println("MD5_BAD " + h);
                            exitProcess(1);
                        }
                        String empty = PgMd5.hashBytes(new byte[0]);
                        if (!empty.equals("d41d8cd98f00b204e9800998ecf8427e")) {
                            System.out.println("MD5_EMPTY_BAD " + empty);
                            exitProcess(1);
                        }
                        int ph2 = PgWire.countPlaceholders("SELECT a FROM t WHERE x = ? AND y = ?");
                        if (ph2 != 2) {
                            System.out.println("PH_BAD2 " + String.valueOf(ph2));
                            exitProcess(1);
                        }
                        int ph0 = PgWire.countPlaceholders("SELECT '?' AS q FROM t /* ? */");
                        if (ph0 != 0) {
                            System.out.println("PH_BAD0 " + String.valueOf(ph0));
                            exitProcess(1);
                        }
                        DatabaseUrl u = DatabaseUrl.parse(
                                "postgresql://u:p@127.0.0.1:5432/db?sslmode=require");
                        if (u.requiresTls() == 0) {
                            System.out.println("SSL_FLAG_BAD");
                            exitProcess(1);
                        }
                        EventLoop loop = new EventLoop();
                        DatabaseConfig cfg = new DatabaseConfig();
                        cfg.url = "postgresql://u:p@127.0.0.1:1/db?sslmode=require";
                        cfg.loop = loop;
                        JnFuture f = new PostgreSQLDriver().connect(cfg);
                        FutureBlocker.blockingWait(f);
                        if (f.getState() != JnFuture.FAILED) {
                            System.out.println("SSL_CONNECT_SHOULD_FAIL");
                            exitProcess(1);
                        }
                        String msg = f.getError().getMessage();
                        if (msg.indexOf("sslmode") < 0 && msg.indexOf("TLS") < 0) {
                            System.out.println("SSL_MSG_BAD " + msg);
                            exitProcess(1);
                        }
                        System.out.println("JNATIVE_PG_MD5_SSL_PASS");
                        exitProcess(0);
                    }
                }
                """;

        Path javaFile = tempDir.resolve("PgMd5Ssl.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("PgMd5Ssl");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(1);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "timeout");
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        assertTrue(stdout.contains("JNATIVE_PG_MD5_SSL_PASS"), stdout);
    }
}
