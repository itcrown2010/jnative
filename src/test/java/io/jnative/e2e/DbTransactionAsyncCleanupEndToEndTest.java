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
 * Database.transaction failure path must async-ROLLBACK without blocking the event loop.
 *
 * @author 张晓达
 */
class DbTransactionAsyncCleanupEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void transactionWorkFailureRollsBackWithoutLoopDeadlock() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Assumptions.assumeTrue(sqliteAvailable(), "sqlite3 not available");

        String src = """
                class BoomWork implements DbTransactionWork {
                    public JnFuture run(DbConnection conn) {
                        ArrayList<Object> empty = new ArrayList<Object>();
                        FutureBlocker.blockingGet(conn.execute(
                                "CREATE TEMP TABLE tx_boom (v INTEGER)", empty));
                        ArrayList<Object> ins = new ArrayList<Object>();
                        ins.add(new DbBindInt(7));
                        FutureBlocker.blockingGet(conn.execute(
                                "INSERT INTO tx_boom(v) VALUES (?)", ins));
                        EventLoop loop = Async.defaultLoop();
                        return JnFuture.failed(loop, new RuntimeException("work boom"));
                    }
                }

                class OkWork implements DbTransactionWork {
                    public JnFuture run(DbConnection conn) {
                        ArrayList<Object> empty = new ArrayList<Object>();
                        return conn.execute("CREATE TEMP TABLE tx_ok (v INTEGER)", empty);
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
                        cfg.maxPool = 2;
                        Database db = (Database)FutureBlocker.blockingGet(Database.connectWithConfig(cfg));
                        JnFuture tx = db.transaction(new BoomWork());
                        FutureBlocker.blockingWait(tx);
                        if (tx.getState() != JnFuture.FAILED) {
                            System.out.println("FAIL_NOT_FAILED");
                            exitProcess(1);
                        }
                        JnFuture ok = db.transaction(new OkWork());
                        FutureBlocker.blockingWait(ok);
                        if (ok.getState() != JnFuture.SUCCESS) {
                            System.out.println("FAIL_OK_TX");
                            exitProcess(1);
                        }
                        DbMetrics m = db.getMetrics();
                        System.out.println(m.connectionsActive);
                        System.out.println(m.connectionsIdle);
                        System.out.println("JNATIVE_TX_ASYNC_CLEANUP_PASS");
                        exitProcess(0);
                    }
                }
                """;

        Path javaFile = tempDir.resolve("TxAsync.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("TxAsync");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(1);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        assertTrue(p.waitFor(90, TimeUnit.SECONDS), "timeout — likely event-loop deadlock");
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        assertTrue(stdout.contains("JNATIVE_TX_ASYNC_CLEANUP_PASS"), stdout);
    }

    @Test
    void databaseTransactionHoldsOneConnectionAcrossAwaits() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Assumptions.assumeTrue(sqliteAvailable(), "sqlite3 not available");

        String src = """
                class IntBox {
                    int value;
                    IntBox(int value) { this.value = value; }
                }
                class IntMapper implements RowMapper {
                    public Object map(Row row) {
                        return new IntBox(row.getInt(0));
                    }
                }
                class PoolSleepSupplier implements BlockingSupplier {
                    public Object get() {
                        Thread.sleep(20);
                        return null;
                    }
                }
                class AffinityWork implements DbTransactionWork {
                    public JnFuture run(DbConnection conn) {
                        ArrayList<Object> empty = new ArrayList<Object>();
                        FutureBlocker.blockingGet(conn.execute(
                                "CREATE TEMP TABLE rh1_aff (v INTEGER NOT NULL)", empty));
                        ArrayList<Object> ins = new ArrayList<Object>();
                        ins.add(new DbBindInt(42));
                        FutureBlocker.blockingGet(conn.execute(
                                "INSERT INTO rh1_aff(v) VALUES (?)", ins));
                        EventLoop loop = Async.defaultLoop();
                        FutureBlocker.blockingGet(Async.blockingOnLoop(loop, new PoolSleepSupplier()));
                        IntMapper mapper = new IntMapper();
                        IntBox a = (IntBox)FutureBlocker.blockingGet(
                                conn.queryOne("SELECT v FROM rh1_aff", empty, mapper));
                        FutureBlocker.blockingGet(Async.blockingOnLoop(loop, new PoolSleepSupplier()));
                        IntBox b = (IntBox)FutureBlocker.blockingGet(
                                conn.queryOne("SELECT v FROM rh1_aff", empty, mapper));
                        if (a.value != 42 || b.value != 42) {
                            return JnFuture.failed(loop, new RuntimeException("affinity"));
                        }
                        return JnFuture.completed(loop, new IntBox(a.value + b.value));
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
                        cfg.maxPool = 4;
                        Database db = (Database)FutureBlocker.blockingGet(Database.connectWithConfig(cfg));
                        IntBox sum = (IntBox)FutureBlocker.blockingGet(db.transaction(new AffinityWork()));
                        System.out.println(sum.value);
                        System.out.println("JNATIVE_DB_TX_AFFINITY_PASS");
                        exitProcess(0);
                    }
                }
                """;

        Path javaFile = tempDir.resolve("TxAffDb.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("TxAffDb");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(1);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        assertTrue(p.waitFor(90, TimeUnit.SECONDS), "timeout");
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        assertTrue(stdout.contains("84"), stdout);
        assertTrue(stdout.contains("JNATIVE_DB_TX_AFFINITY_PASS"), stdout);
    }

    private boolean sqliteAvailable() throws Exception {
        Path probe = tempDir.resolve("sqlite_probe2.c");
        Files.writeString(probe,
                "int sqlite3_libversion_number(void); int main(){return sqlite3_libversion_number()>0?0:1;}\n",
                StandardCharsets.UTF_8);
        Path probeOut = tempDir.resolve("sqlite_probe2");
        ProcessBuilder pb = new ProcessBuilder("clang", probe.toString(), "-o", probeOut.toString(), "-lsqlite3");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor(30, TimeUnit.SECONDS);
        return p.exitValue() == 0;
    }
}
