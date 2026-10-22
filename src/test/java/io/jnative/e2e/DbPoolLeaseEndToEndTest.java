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
 * Connection-pool lease: failed connects must return permits so later acquires succeed.
 *
 * @author 张晓达
 */
class DbPoolLeaseEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void connectFailuresReturnPermitUnderMaxPoolOne() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Assumptions.assumeTrue(sqliteAvailable(), "sqlite3 not available");

        String src = """
                class FailDriver implements DatabaseDriver {
                    int calls;
                    public JnFuture connect(DatabaseConfig config) {
                        calls = calls + 1;
                        EventLoop loop = config.loop;
                        if (loop == null) {
                            loop = Async.defaultLoop();
                        }
                        if (calls <= 3) {
                            return JnFuture.failed(loop, new ConnectionException("boom" + String.valueOf(calls)));
                        }
                        return new SQLiteDriver().connect(config);
                    }
                }

                public class Main {
                    @Native(symbol = "exit")
                    static native void exitProcess(int code);

                    public static void main(String[] args) {
                        EventLoop loop = new EventLoop();
                        DatabaseConfig cfg = new DatabaseConfig();
                        cfg.url = "sqlite::memory:";
                        cfg.loop = loop;
                        cfg.maxPool = 1;
                        cfg.acquireTimeoutMs = 2000;
                        FailDriver driver = new FailDriver();
                        DriverRegistry.register("sqlite", driver);
                        DatabaseUrl parsed = DatabaseUrl.parse(cfg.url);
                        ConnectionPool pool = new ConnectionPool(cfg, parsed, driver);
                        int i = 0;
                        while (i < 3) {
                            JnFuture f = pool.acquire();
                            FutureBlocker.blockingWait(f);
                            if (f.getState() != JnFuture.FAILED) {
                                System.out.println("FAIL_EXPECTED_FAIL");
                                exitProcess(1);
                            }
                            i = i + 1;
                        }
                        DbConnection conn = (DbConnection)FutureBlocker.blockingGet(pool.acquire());
                        if (conn == null) {
                            System.out.println("FAIL_NULL");
                            exitProcess(1);
                        }
                        pool.release(conn);
                        DbMetrics m = pool.getMetrics();
                        System.out.println(m.connectionsCreated);
                        System.out.println(m.connectionsActive);
                        System.out.println(m.connectionsIdle);
                        System.out.println("JNATIVE_POOL_LEASE_PASS");
                        exitProcess(0);
                    }
                }
                """;

        Path javaFile = tempDir.resolve("PoolLease.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("PoolLease");
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
        assertTrue(stdout.contains("JNATIVE_POOL_LEASE_PASS"), stdout);
        assertTrue(stdout.contains("1\n0\n1\n") || stdout.contains("1\r\n0\r\n1\r\n"), stdout);
    }

    private boolean sqliteAvailable() throws Exception {
        Path probe = tempDir.resolve("sqlite_probe.c");
        Files.writeString(probe,
                "int sqlite3_libversion_number(void); int main(){return sqlite3_libversion_number()>0?0:1;}\n",
                StandardCharsets.UTF_8);
        Path probeOut = tempDir.resolve("sqlite_probe");
        ProcessBuilder pb = new ProcessBuilder("clang", probe.toString(), "-o", probeOut.toString(), "-lsqlite3");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor(30, TimeUnit.SECONDS);
        return p.exitValue() == 0;
    }
}
