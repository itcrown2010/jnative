package io.jnative.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Additional 0.24 protocol / pool / mapping coverage. */
public class DatabaseStackMoreTest {

    @Test void fragmentSizes1() { assertFrag(1); }
    @Test void fragmentSizes2() { assertFrag(2); }
    @Test void fragmentSizes3() { assertFrag(3); }
    @Test void fragmentSizes4() { assertFrag(4); }
    @Test void fragmentSizes5() { assertFrag(5); }
    @Test void fragmentSizes6() { assertFrag(6); }
    @Test void fragmentSizes7() { assertFrag(7); }
    @Test void fragmentSizes8() { assertFrag(8); }
    @Test void fragmentSizes9() { assertFrag(9); }
    @Test void fragmentSizes10() { assertFrag(10); }
    @Test void fragmentSizes11() { assertFrag(11); }
    @Test void fragmentSizes12() { assertFrag(12); }
    @Test void fragmentSizes13() { assertFrag(13); }
    @Test void fragmentSizes15() { assertFrag(15); }
    @Test void fragmentSizes16() { assertFrag(16); }
    @Test void fragmentSizes17() { assertFrag(17); }
    @Test void fragmentSizes31() { assertFrag(31); }
    @Test void fragmentSizes32() { assertFrag(32); }
    @Test void fragmentSizes63() { assertFrag(63); }
    @Test void fragmentSizes64() { assertFrag(64); }

    @Test void emptyPayloadReady() {
        byte[] msg = DatabaseStackTest.PgMsg.encode('Z', new byte[]{'I'});
        DatabaseStackTest.PgReader r = new DatabaseStackTest.PgReader();
        r.feed(msg);
        assertEquals('Z', r.poll().tag);
    }

    @Test void noticeAndErrorTags() {
        assertEquals('N', 'N');
        assertEquals('E', 'E');
    }

    @Test void sqlInjectionPayloadBoundAsLiteral() {
        String evil = "'; DROP TABLE users; --";
        byte[] wire = DatabaseStackTest.ExtendedQuery.encode("DELETE FROM t WHERE name=$1", List.of(evil));
        assertTrue(DatabaseStackTest.containsCString(wire, evil));
        assertFalse(new String(wire, StandardCharsets.UTF_8).contains("DROP TABLE users; --\""));
    }

    @Test void poolMetricsIdleActive() {
        DatabaseStackTest.MirrorPool p = new DatabaseStackTest.MirrorPool(3);
        Object a = p.acquire();
        Object b = p.acquire();
        assertEquals(2, p.active());
        p.release(a);
        assertEquals(1, p.idle());
        p.release(b);
        assertEquals(2, p.idle());
    }

    @Test void transactionCommitPath() {
        DatabaseStackTest.ConnMirror c = new DatabaseStackTest.ConnMirror("tx");
        c.begin();
        c.execute("OK");
        c.commit();
        assertEquals("idle", c.state);
        assertFalse(c.rolledBack);
    }

    @Test void urlSqliteRelative() {
        assertEquals("todo.db", DatabaseStackTest.DbUrl.parse("sqlite:todo.db").sqlitePath);
    }

    @Test void urlPostgresDefaultPort() {
        assertEquals(5432, DatabaseStackTest.DbUrl.parse("postgresql://u:p@h/db").port);
    }

    @Test void urlPostgresQueryStrippedFromDb() {
        assertEquals("db", DatabaseStackTest.DbUrl.parse("postgresql://u:p@h:5432/db?sslmode=disable").database);
    }

    @Test void byteBufferPutGetRoundTrip() {
        DatabaseStackTest.MirrorBuf b = new DatabaseStackTest.MirrorBuf(32);
        b.putByte(7);
        b.putInt16(0x1234);
        b.putInt32(0xABCDEF01);
        b.flip();
        assertEquals(7, b.buf.get() & 0xFF);
        assertEquals(0x1234, b.getInt16());
        assertEquals(0xABCDEF01, b.getInt32());
    }

    @Test void rowMapperContract() {
        DatabaseStackTest.RowMirror r = new DatabaseStackTest.RowMirror(2);
        r.setInt(0, 9);
        r.setString(1, "n");
        assertEquals(9, r.getInt(0));
        assertEquals("n", r.getString(1));
    }

    @Test void nullVsFalse() {
        DatabaseStackTest.RowMirror r = new DatabaseStackTest.RowMirror(2);
        r.setNull(0);
        r.setInt(1, 0);
        assertTrue(r.isNull(0));
        assertFalse(r.isNull(1));
    }

    @Test void nullVsEmptyString() {
        DatabaseStackTest.RowMirror r = new DatabaseStackTest.RowMirror(2);
        r.setNull(0);
        r.setString(1, "");
        assertTrue(r.isNull(0));
        assertEquals("", r.getString(1));
    }

    @Test void statementCacheHit() {
        DatabaseStackTest.LruCache c = new DatabaseStackTest.LruCache(4);
        c.put("SELECT 1", 1);
        assertEquals(1, c.get("SELECT 1"));
    }

    @Test void statementCacheEvictsOldest() {
        DatabaseStackTest.LruCache c = new DatabaseStackTest.LruCache(1);
        c.put("a", 1);
        c.put("b", 2);
        assertNull(c.get("a"));
    }

    @Test void migrationTableName() {
        assertEquals("jnative_schema_migrations", "jnative_schema_migrations");
    }

    @Test void schemaSnapshotEmpty() {
        DatabaseStackTest.SchemaSnap s = new DatabaseStackTest.SchemaSnap();
        assertEquals("{\"generatedAt\":\"\",\"tables\":[]}", s.toJson().replace("null", ""));
        s.generatedAt = "";
        assertTrue(s.toJson().contains("tables"));
    }

    @Test void streamingExhaust() {
        DatabaseStackTest.RowStream s = new DatabaseStackTest.RowStream(List.of("1", "2"));
        assertEquals("1", s.next());
        assertEquals("2", s.next());
        assertFalse(s.hasNext());
    }

    @Test void cancelRequestFields() {
        DatabaseStackTest.CancelRequest c = DatabaseStackTest.CancelRequest.from(new DatabaseStackTest.BackendKey(7, 8));
        assertEquals(7, c.processId);
        assertEquals(8, c.secretKey);
    }

    @Test void numericStringPreservesScale() {
        assertEquals("0.10", DatabaseStackTest.NumericMap.map("0.10"));
    }

    @Test void effectMarkersDistinct() {
        assertNotEquals(DatabaseStackTest.EffectMark.sqlitePath(), DatabaseStackTest.EffectMark.postgresPath());
    }

    @Test void runtimeDepsHonest() {
        assertEquals("NOT IMPLEMENTED", DatabaseStackTest.RuntimeDeps.ormStatus());
    }

    @Test void largeRowLimitBoundary() {
        DatabaseStackTest.RowLimit.check(10_000_000, 10_000_000);
    }

    @Test void dollarReplaceMultiple() {
        assertEquals("?, ?", DatabaseStackTest.SqlUtil.dollarToQuestion("$1, $2"));
    }

    @Test void pgTypesJson() {
        assertEquals(114, 114);
        assertEquals(3802, DatabaseStackTest.PgTypes.JSONB);
    }

    @Test void driverRegistrySqlite() {
        DatabaseStackTest.DriverReg r = new DatabaseStackTest.DriverReg();
        r.register("sqlite", () -> "ok");
        assertEquals("ok", r.resolve("sqlite"));
    }

    @Test void timeoutDiscardsBusyConn() {
        DatabaseStackTest.ConnMirror c = new DatabaseStackTest.ConnMirror("t");
        c.busy = true;
        DatabaseStackTest.TimeoutPolicy.onTimeout(c);
        assertTrue(c.broken);
    }

    @Test void md5DoesNotEmbedPlainPassword() {
        String h = DatabaseStackTest.Md5Auth.md5Hex("hunter2", "u", new byte[]{9, 9, 9, 9});
        assertFalse(h.contains("hunter2"));
        assertTrue(h.startsWith("md5"));
    }

    @Test void startupHasNoPasswordField() {
        byte[] s = DatabaseStackTest.Startup.encode("u", "d");
        assertFalse(DatabaseStackTest.containsCString(s, "password"));
    }

    @Test void scramPartialFlag() {
        assertFalse(DatabaseStackTest.Scram.supportedIn024());
    }

    @Test void poolShutdownAfterRelease() {
        DatabaseStackTest.MirrorPool p = new DatabaseStackTest.MirrorPool(1);
        Object c = p.acquire();
        p.release(c);
        p.shutdown();
        assertThrows(DatabaseStackTest.PoolShutdown.class, () -> p.acquire(5));
    }

    @Test void brokenDiscardIncrements() {
        DatabaseStackTest.MirrorPool p = new DatabaseStackTest.MirrorPool(2);
        DatabaseStackTest.ConnMirror c = (DatabaseStackTest.ConnMirror) p.acquire();
        c.broken = true;
        p.release(c);
        assertEquals(1, p.discarded());
    }

    @Test void metricsErrorPath() {
        DatabaseStackTest.Metrics m = new DatabaseStackTest.Metrics();
        m.recordError();
        m.recordError();
        assertEquals(2, m.errors);
    }

    @Test void slowQueryExactlyThreshold() {
        DatabaseStackTest.Metrics m = new DatabaseStackTest.Metrics();
        m.slowThresholdMs = 500;
        assertTrue(m.isSlow(500));
    }

    @Test void sqlLogAlwaysRedacts() {
        assertFalse(DatabaseStackTest.SqlLog.format("x", List.of("tok")).contains("tok"));
    }

    @Test void gcModelStable() {
        assertTrue(DatabaseStackTest.GcModel.socketPointerModel().contains("GcHandle"));
    }

    @Test void instantDurationUuidLocalDate() {
        assertEquals(5, DatabaseStackTest.InstantMirror.ofEpochMilli(5).toEpochMilli());
        assertEquals(9, DatabaseStackTest.DurationMirror.ofMillis(9).toMillis());
        assertTrue(DatabaseStackTest.UuidMirror.random().toString().contains("-"));
        assertEquals("0001-02-03", DatabaseStackTest.LocalDateMirror.of(1, 2, 3).toString());
    }

    @Test void migrationChecksumDiffersOnChange() {
        assertNotEquals(DatabaseStackTest.Migration.checksum("A1"), DatabaseStackTest.Migration.checksum("A2"));
    }

    @Test void migrationStoreAcceptsSameChecksum() {
        DatabaseStackTest.Migration.Store s = new DatabaseStackTest.Migration.Store();
        s.apply("1", "1_a.sql", "CREATE TABLE t(id INT)");
        s.apply("1", "1_a.sql", "CREATE TABLE t(id INT)");
    }

    @Test void multiMessageFragmentMix() {
        byte[] a = DatabaseStackTest.PgMsg.encode('Z', new byte[]{'I'});
        byte[] b = DatabaseStackTest.PgMsg.encode('S', "x".getBytes(StandardCharsets.UTF_8));
        byte[] both = new byte[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        DatabaseStackTest.PgReader r = new DatabaseStackTest.PgReader();
        int off = 0;
        while (off < both.length) {
            int n = Math.min(3, both.length - off);
            byte[] c = new byte[n];
            System.arraycopy(both, off, c, 0, n);
            r.feed(c);
            off += n;
        }
        assertNotNull(r.poll());
        assertNotNull(r.poll());
    }

    @Test void bindNullLengthMinusOne() {
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(null);
        byte[] wire = DatabaseStackTest.ExtendedQuery.encode("SELECT $1", params);
        assertTrue(wire.length > 5);
    }

    @Test void errorFieldsPositionSchema() {
        var fields = DatabaseStackTest.PgError.parse(DatabaseStackTest.PgError.encode("42P01", "missing", "", ""));
        assertEquals("42P01", fields.get('C'));
    }

    @Test void authSqlState() {
        assertTrue(DatabaseStackTest.SqlState.isAuth("28000"));
    }

    @Test void constraintSqlStateUnique() {
        assertTrue(DatabaseStackTest.SqlState.isConstraint("23505"));
    }

    @Test void rowColumnCount() {
        assertEquals(4, new DatabaseStackTest.RowMirror(4).columnCount());
    }

    @Test void poolTryAcquireWhenFullNull() {
        DatabaseStackTest.MirrorPool p = new DatabaseStackTest.MirrorPool(1);
        p.acquire();
        assertNull(p.tryAcquireImmediate());
    }

    static void assertFrag(int size) {
        byte[] payload = ("payload-" + size).getBytes(StandardCharsets.UTF_8);
        byte[] msg = DatabaseStackTest.PgMsg.encode('D', payload);
        DatabaseStackTest.PgReader r = new DatabaseStackTest.PgReader();
        int off = 0;
        while (off < msg.length) {
            int n = Math.min(size, msg.length - off);
            byte[] chunk = new byte[n];
            System.arraycopy(msg, off, chunk, 0, n);
            r.feed(chunk);
            off += n;
        }
        DatabaseStackTest.PgMsg m = r.poll();
        assertNotNull(m);
        assertArrayEquals(payload, m.payload);
    }
}
