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
 * RH-1: same physical SQLite connection across awaits (TEMP table visibility).
 *
 * Note: nested {@code FutureBlocker} inside {@code Database.transaction} callbacks
 * can deadlock the shared pool (callbacks run on pool workers). This test drives
 * BEGIN/await/query/COMMIT from the main thread while holding one acquired
 * {@code DbConnection} — the same ownership model {@code TransactionFlatMap} uses.
 *
 * PostgreSQL {@code pg_backend_pid()} live e2e remains blocked (B-P0-05).
 * @author 张晓达
 */
class SqliteTxAwaitAffinityEndToEndTest {

    @TempDir
    Path tempDir;

    private static final String SOURCE = """
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
                    Thread.sleep(30);
                    return null;
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
                    DbConnection conn = (DbConnection)FutureBlocker.blockingGet(db.acquire());
                    ArrayList<Object> empty = new ArrayList<Object>();
                    FutureBlocker.blockingGet(conn.beginTransaction());
                    FutureBlocker.blockingGet(conn.execute(
                            "CREATE TEMP TABLE rh1_aff (v INTEGER NOT NULL)", empty));
                    ArrayList<Object> ins = new ArrayList<Object>();
                    ins.add(new DbBindInt(42));
                    FutureBlocker.blockingGet(conn.execute(
                            "INSERT INTO rh1_aff(v) VALUES (?)", ins));
                    FutureBlocker.blockingGet(Async.blockingOnLoop(loop, new PoolSleepSupplier()));
                    IntMapper mapper = new IntMapper();
                    IntBox a = (IntBox)FutureBlocker.blockingGet(
                            conn.queryOne("SELECT v FROM rh1_aff", empty, mapper));
                    FutureBlocker.blockingGet(Async.blockingOnLoop(loop, new PoolSleepSupplier()));
                    IntBox b = (IntBox)FutureBlocker.blockingGet(
                            conn.queryOne("SELECT v FROM rh1_aff", empty, mapper));
                    FutureBlocker.blockingGet(Async.blockingOnLoop(loop, new PoolSleepSupplier()));
                    IntBox c = (IntBox)FutureBlocker.blockingGet(
                            conn.queryOne("SELECT count(*) FROM rh1_aff", empty, mapper));
                    FutureBlocker.blockingGet(conn.commit());
                    db.pool.release(conn);
                    if (a.value != 42) {
                        System.out.println("FAIL_A");
                        exitProcess(1);
                    }
                    if (b.value != 42) {
                        System.out.println("FAIL_B");
                        exitProcess(1);
                    }
                    if (c.value != 1) {
                        System.out.println("FAIL_C");
                        exitProcess(1);
                    }
                    System.out.println(a.value + b.value + c.value);
                    System.out.println("JNATIVE_TX_AWAIT_AFFINITY_PASS");
                    // Pool workers are non-daemon; exit before jn_thread_join_remaining hangs.
                    exitProcess(0);
                }
            }
            """;

    @Test
    void tempTableVisibleAcrossAwaitsSameConnection() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Assumptions.assumeTrue(sqliteAvailable(), "sqlite3 not available");

        Path javaFile = tempDir.resolve("TxAff.java");
        Files.writeString(javaFile, SOURCE, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("TxAff");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(1);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "timeout: " + stdout);
        assertEquals(0, p.exitValue(), stdout);
        assertTrue(stdout.contains("85"), stdout);
        assertTrue(stdout.contains("JNATIVE_TX_AWAIT_AFFINITY_PASS"), stdout);
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
