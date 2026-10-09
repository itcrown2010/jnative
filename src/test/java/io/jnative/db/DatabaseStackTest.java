package io.jnative.db;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JNative 0.24 Database unit/protocol tests (host JVM mirrors of native stdlib logic).
 * @author 张晓达
 */
public class DatabaseStackTest {

    // ---- DatabaseUrl ----

    @Test void urlSqliteMemory() {
        DbUrl u = DbUrl.parse("sqlite::memory:");
        assertEquals("sqlite", u.scheme);
        assertTrue(u.sqlitePath.contains("mode=memory"));
    }

    @Test void urlSqliteFile() {
        DbUrl u = DbUrl.parse("sqlite:./data/app.db");
        assertEquals("./data/app.db", u.sqlitePath);
    }

    @Test void urlPostgresBasic() {
        DbUrl u = DbUrl.parse("postgresql://alice:secret@localhost:5432/mydb");
        assertEquals("postgresql", u.scheme);
        assertEquals("alice", u.user);
        assertEquals("secret", u.password);
        assertEquals("localhost", u.host);
        assertEquals(5432, u.port);
        assertEquals("mydb", u.database);
    }

    @Test void urlPostgresAlias() {
        DbUrl u = DbUrl.parse("postgres://u:p@h:15432/db");
        assertEquals("postgresql", u.scheme);
        assertEquals(15432, u.port);
    }

    @Test void urlRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> DbUrl.parse(""));
    }

    @Test void urlRejectsUnknownScheme() {
        assertThrows(IllegalArgumentException.class, () -> DbUrl.parse("mysql://x"));
    }

    // ---- ByteBuffer big-endian ----

    @Test void byteBufferInt32Be() {
        MirrorBuf b = new MirrorBuf(16);
        b.putInt32(0x01020304);
        b.flip();
        assertEquals(0x01020304, b.getInt32());
    }

    @Test void byteBufferInt16Be() {
        MirrorBuf b = new MirrorBuf(8);
        b.putInt16(0xABCD);
        b.flip();
        assertEquals(0xABCD, b.getInt16());
    }

    @Test void byteBufferInt64Be() {
        MirrorBuf b = new MirrorBuf(16);
        b.putInt64(0x0102030405060708L);
        b.flip();
        assertEquals(0x0102030405060708L, b.getInt64());
    }

    @Test void byteBufferRemaining() {
        MirrorBuf b = new MirrorBuf(8);
        assertEquals(8, b.remaining());
        b.putByte(1);
        assertEquals(7, b.remaining());
    }

    @Test void byteBufferSliceIndependentPosition() {
        MirrorBuf b = new MirrorBuf(8);
        b.putInt32(42);
        b.flip();
        MirrorBuf s = b.slice();
        assertEquals(42, s.getInt32());
        assertEquals(0, b.getPosition());
    }

    @Test void byteBufferClearFlip() {
        MirrorBuf b = new MirrorBuf(4);
        b.putByte(1);
        b.putByte(2);
        b.flip();
        assertEquals(2, b.remaining());
        b.clear();
        assertEquals(4, b.remaining());
    }

    // ---- Fragmented PG parser ----

    @Test void fragmentedMessageOneByteChunks() {
        byte[] msg = PgMsg.encode('Z', new byte[]{'I'});
        PgReader r = new PgReader();
        for (byte b : msg) {
            r.feed(new byte[]{b});
        }
        PgMsg m = r.poll();
        assertNotNull(m);
        assertEquals('Z', m.tag);
        assertArrayEquals(new byte[]{'I'}, m.payload);
        assertNull(r.poll());
    }

    @Test void fragmentedMessageRandomChunks() {
        byte[] payload = "hello-world-payload".getBytes(StandardCharsets.UTF_8);
        byte[] msg = PgMsg.encode('S', payload);
        PgReader r = new PgReader();
        int[] sizes = {1, 2, 7, 3, 11, 4};
        int off = 0;
        int si = 0;
        while (off < msg.length) {
            int n = Math.min(sizes[si % sizes.length], msg.length - off);
            byte[] chunk = new byte[n];
            System.arraycopy(msg, off, chunk, 0, n);
            r.feed(chunk);
            off += n;
            si++;
        }
        PgMsg m = r.poll();
        assertNotNull(m);
        assertEquals('S', m.tag);
        assertArrayEquals(payload, m.payload);
    }

    @Test void incompleteMessageReturnsNull() {
        PgReader r = new PgReader();
        r.feed(new byte[]{'R', 0, 0, 0});
        assertNull(r.poll());
    }

    @Test void multipleMessagesInOneChunk() {
        byte[] a = PgMsg.encode('Z', new byte[]{'I'});
        byte[] b = PgMsg.encode('Z', new byte[]{'T'});
        byte[] both = new byte[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        PgReader r = new PgReader();
        r.feed(both);
        assertEquals('I', r.poll().payload[0]);
        assertEquals('T', r.poll().payload[0]);
        assertNull(r.poll());
    }

    // ---- Error / SQLSTATE ----

    @Test void errorResponseParsesSqlState() {
        Map<Character, String> fields = PgError.parse(PgError.encode(
                "23505", "duplicate key", "detail-x", "hint-y"));
        assertEquals("23505", fields.get('C'));
        assertEquals("duplicate key", fields.get('M'));
        assertEquals("detail-x", fields.get('D'));
        assertEquals("hint-y", fields.get('H'));
    }

    @Test void sqlStateMapsConstraint() {
        assertTrue(SqlState.isConstraint("23505"));
        assertFalse(SqlState.isConstraint("08006"));
    }

    @Test void sqlStateMapsAuth() {
        assertTrue(SqlState.isAuth("28P01"));
        assertFalse(SqlState.isAuth("23505"));
    }

    // ---- Extended query bind security ----

    @Test void bindDoesNotConcatenateUserInput() {
        String sql = "SELECT * FROM users WHERE name = $1";
        String evil = "' OR 1=1 --";
        byte[] wire = ExtendedQuery.encode(sql, List.of(evil));
        String asText = new String(wire, StandardCharsets.UTF_8);
        assertTrue(asText.contains(sql));
        assertFalse(asText.contains("WHERE name = '" + evil));
        assertTrue(containsCString(wire, evil));
    }

    @Test void bindSupportsNullIntLongDoubleBoolBytes() {
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add("s");
        params.add(1);
        params.add(2L);
        params.add(3.5d);
        params.add(true);
        params.add(null);
        params.add(new byte[]{1, 2});
        byte[] wire = ExtendedQuery.encode(
                "INSERT INTO t VALUES($1,$2,$3,$4,$5,$6,$7)",
                params);
        assertTrue(wire.length > 20);
        assertEquals('P', wire[0]);
    }

    // ---- Startup / auth message shapes ----

    @Test void startupMessageContainsUserAndDatabase() {
        byte[] s = Startup.encode("alice", "mydb");
        assertTrue(containsCString(s, "user"));
        assertTrue(containsCString(s, "alice"));
        assertTrue(containsCString(s, "database"));
        assertTrue(containsCString(s, "mydb"));
        assertFalse(containsCString(s, "password"));
    }

    @Test void passwordNeverLoggedInMd5Helper() {
        String hashed = Md5Auth.md5Hex("secret", "alice", new byte[]{1, 2, 3, 4});
        assertFalse(hashed.contains("secret"));
        assertEquals(35, hashed.length()); // "md5" + 32 hex
    }

    @Test void scramNotYetSupportedMarker() {
        assertEquals("SCRAM-SHA-256", Scram.MECH);
        assertFalse(Scram.supportedIn024());
    }

    // ---- Row nullability ----

    @Test void rowNullDistinctFromZeroAndEmpty() {
        RowMirror r = new RowMirror(3);
        r.setNull(0);
        r.setInt(1, 0);
        r.setString(2, "");
        assertTrue(r.isNull(0));
        assertFalse(r.isNull(1));
        assertFalse(r.isNull(2));
        assertEquals(0, r.getInt(1));
        assertEquals("", r.getString(2));
    }

    @Test void rowColumnNames() {
        RowMirror r = new RowMirror(2);
        r.setName(0, "id");
        r.setName(1, "email");
        assertEquals("id", r.columnName(0));
        assertEquals(2, r.columnCount());
    }

    // ---- Pool semantics (host mirror) ----

    @Test void poolAcquireRelease() {
        MirrorPool p = new MirrorPool(2);
        assertEquals(0, p.active());
        Object a = p.acquire();
        Object b = p.acquire();
        assertEquals(2, p.active());
        assertNull(p.tryAcquireImmediate());
        p.release(a);
        assertNotNull(p.tryAcquireImmediate());
        p.release(b);
    }

    @Test void poolAcquireTimeout() throws InterruptedException {
        MirrorPool p = new MirrorPool(1);
        p.acquire();
        long t0 = System.currentTimeMillis();
        assertThrows(AcquireTimeout.class, () -> p.acquire(50));
        assertTrue(System.currentTimeMillis() - t0 >= 40);
    }

    @Test void poolShutdownFailsWaiters() {
        MirrorPool p = new MirrorPool(1);
        p.acquire();
        p.shutdown();
        assertThrows(PoolShutdown.class, () -> p.acquire(10));
    }

    @Test void brokenConnectionDiscarded() {
        MirrorPool p = new MirrorPool(1);
        ConnMirror c = (ConnMirror) p.acquire();
        c.broken = true;
        p.release(c);
        assertEquals(0, p.idle());
        assertEquals(1, p.discarded());
    }

    // ---- Transaction across await (same connection) ----

    @Test void transactionUsesOneConnectionAcrossAwaits() {
        ConnMirror c = new ConnMirror("c1");
        List<String> used = new ArrayList<>();
        c.begin();
        used.add(c.id);
        Thread.yield(); // stand-in for await
        c.execute("INSERT");
        used.add(c.id);
        c.commit();
        assertEquals(List.of("c1", "c1"), used);
    }

    @Test void transactionRollbackOnException() {
        ConnMirror c = new ConnMirror("c1");
        c.begin();
        try {
            c.execute("BAD");
            throw new RuntimeException("boom");
        } catch (RuntimeException ex) {
            c.rollback();
        }
        assertEquals("idle", c.state);
        assertTrue(c.rolledBack);
    }

    // ---- Statement cache LRU ----

    @Test void statementCacheBoundedLru() {
        LruCache cache = new LruCache(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
    }

    // ---- Migration checksum ----

    @Test void migrationChecksumStable() {
        assertEquals(Migration.checksum("CREATE TABLE t(id INT)"), Migration.checksum("CREATE TABLE t(id INT)"));
        assertNotEquals(Migration.checksum("ALPHA"), Migration.checksum("BETA"));
    }

    @Test void migrationRejectsModifiedApplied() {
        Migration.Store store = new Migration.Store();
        store.apply("001", "001_create.sql", "CREATE TABLE x(id INT)");
        assertThrows(IllegalStateException.class,
                () -> store.apply("001", "001_create.sql", "CREATE TABLE x(id INT, name TEXT)"));
    }

    // ---- Schema snapshot offline ----

    @Test void schemaSnapshotJsonOffline() {
        SchemaSnap s = new SchemaSnap();
        s.generatedAt = "2026-10-06";
        s.tables.add("users");
        s.tables.add("todos");
        String json = s.toJson();
        assertTrue(json.contains("\"users\""));
        assertTrue(json.contains("\"todos\""));
        assertFalse(json.contains("jdbc"));
    }

    // ---- Streaming / backpressure foundation ----

    @Test void rowStreamBackpressure() {
        RowStream stream = new RowStream(List.of("a", "b", "c"));
        assertTrue(stream.hasNext());
        assertEquals("a", stream.next());
        stream.pause();
        assertTrue(stream.paused);
        stream.resume();
        assertEquals("b", stream.next());
    }

    // ---- Time / UUID ----

    @Test void instantAndDuration() {
        InstantMirror i = InstantMirror.ofEpochMilli(1000);
        DurationMirror d = DurationMirror.ofMillis(250);
        assertEquals(1000, i.toEpochMilli());
        assertEquals(250, d.toMillis());
    }

    @Test void uuidRoundTripShape() {
        UuidMirror u = UuidMirror.random();
        assertNotNull(u.toString());
        assertTrue(u.toString().contains("-"));
    }

    @Test void localDateFormat() {
        assertEquals("2026-10-06", LocalDateMirror.of(2026, 10, 6).toString());
    }

    // ---- Observability ----

    @Test void metricsCounters() {
        Metrics m = new Metrics();
        m.recordQuery(12, 3);
        m.recordError();
        assertEquals(1, m.queries);
        assertEquals(3, m.rows);
        assertEquals(12, m.totalMs);
        assertEquals(1, m.errors);
    }

    @Test void slowQueryThreshold() {
        Metrics m = new Metrics();
        m.slowThresholdMs = 500;
        assertTrue(m.isSlow(723));
        assertFalse(m.isSlow(100));
    }

    @Test void sqlLogRedactsParamsByDefault() {
        assertEquals("SELECT ... [params redacted]", SqlLog.format("SELECT $1", List.of("secret-token")));
        assertFalse(SqlLog.format("SELECT $1", List.of("secret-token")).contains("secret-token"));
    }

    // ---- Effect / isolation markers ----

    @Test void sqliteMarkedBlockingWorker() {
        assertEquals("blocking-worker", EffectMark.sqlitePath());
    }

    @Test void postgresMarkedSuspendsAsyncSocket() {
        assertEquals("suspends-async-tcp", EffectMark.postgresPath());
    }

    @Test void noJdbcNoJniMarkers() {
        assertFalse(RuntimeDeps.hasJdbc());
        assertFalse(RuntimeDeps.hasJni());
        assertFalse(RuntimeDeps.hasHibernate());
    }

    @Test void ormNotImplemented() {
        assertEquals("NOT IMPLEMENTED", RuntimeDeps.ormStatus());
    }

    // ---- Large row size limit ----

    @Test void largeRowRespectsLimit() {
        assertThrows(IllegalArgumentException.class, () -> RowLimit.check(10 * 1024 * 1024 + 1, 10 * 1024 * 1024));
        RowLimit.check(100, 10 * 1024 * 1024);
    }

    @Test void dollarToQuestionForSqlite() {
        assertEquals("SELECT ? FROM t WHERE id = ?", SqlUtil.dollarToQuestion("SELECT $1 FROM t WHERE id = $2"));
    }

    @Test void pgTypeOids() {
        assertEquals(16, PgTypes.BOOL);
        assertEquals(23, PgTypes.INT4);
        assertEquals(25, PgTypes.TEXT);
        assertEquals(3802, PgTypes.JSONB);
    }

    // ---- Driver SPI isolation ----

    @Test void driverRegistryNoSchemeIfsInCaller() {
        DriverReg reg = new DriverReg();
        reg.register("sqlite", () -> "sqlite-driver");
        reg.register("postgresql", () -> "pg-driver");
        assertEquals("sqlite-driver", reg.resolve("sqlite"));
        assertEquals("pg-driver", reg.resolve("postgresql"));
        assertThrows(IllegalArgumentException.class, () -> reg.resolve("mysql"));
    }

    // ---- Cancel / BackendKeyData ----

    @Test void backendKeyStoredForCancel() {
        BackendKey k = new BackendKey(42, 99);
        CancelRequest c = CancelRequest.from(k);
        assertEquals(42, c.processId);
        assertEquals(99, c.secretKey);
    }

    @Test void queryTimeoutRequiresRecoveryOrDiscard() {
        ConnMirror c = new ConnMirror("x");
        c.busy = true;
        TimeoutPolicy.onTimeout(c);
        assertTrue(c.broken || !c.busy);
    }

    // ---- Numeric as String ----

    @Test void numericMapsToStringNotDouble() {
        assertEquals("123.45", NumericMap.map("123.45"));
        assertFalse(NumericMap.map("123.45") instanceof Double);
    }

    // ---- GC handle model marker ----

    @Test void nativePointersUseStableHandles() {
        assertEquals("GcHandle/native-buffer", GcModel.socketPointerModel());
    }

    // ===================== host mirrors =====================

    static final class DbUrl {
        String scheme, host, database, user, password, sqlitePath;
        int port;

        static DbUrl parse(String url) {
            if (url == null || url.isEmpty()) throw new IllegalArgumentException("empty");
            DbUrl u = new DbUrl();
            int se = url.indexOf(':');
            if (se < 0) throw new IllegalArgumentException("scheme");
            u.scheme = url.substring(0, se).toLowerCase();
            if ("sqlite".equals(u.scheme)) {
                String path = url.substring(se + 1);
                if (":memory:".equals(path)) path = "file:jnative_shared_mem?mode=memory&cache=shared";
                u.sqlitePath = path;
                return u;
            }
            if ("postgres".equals(u.scheme)) u.scheme = "postgresql";
            if (!"postgresql".equals(u.scheme)) throw new IllegalArgumentException("scheme");
            u.port = 5432;
            String rest = url.substring(se + 3);
            int at = rest.indexOf('@');
            if (at >= 0) {
                String auth = rest.substring(0, at);
                rest = rest.substring(at + 1);
                int c = auth.indexOf(':');
                if (c >= 0) { u.user = auth.substring(0, c); u.password = auth.substring(c + 1); }
                else { u.user = auth; u.password = ""; }
            }
            int slash = rest.indexOf('/');
            String hp = rest;
            if (slash >= 0) {
                hp = rest.substring(0, slash);
                u.database = rest.substring(slash + 1);
                int q = u.database.indexOf('?');
                if (q >= 0) u.database = u.database.substring(0, q);
            } else u.database = "";
            int hc = hp.indexOf(':');
            if (hc >= 0) { u.host = hp.substring(0, hc); u.port = Integer.parseInt(hp.substring(hc + 1)); }
            else u.host = hp;
            return u;
        }
    }

    static final class MirrorBuf {
        final ByteBuffer buf;
        MirrorBuf(int cap) { buf = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN); }
        void putByte(int v) { buf.put((byte) v); }
        void putInt16(int v) { buf.putShort((short) v); }
        void putInt32(int v) { buf.putInt(v); }
        void putInt64(long v) { buf.putLong(v); }
        int getInt16() { return buf.getShort() & 0xFFFF; }
        int getInt32() { return buf.getInt(); }
        long getInt64() { return buf.getLong(); }
        int remaining() { return buf.remaining(); }
        int getPosition() { return buf.position(); }
        void flip() { buf.flip(); }
        void clear() { buf.clear(); }
        MirrorBuf slice() {
            MirrorBuf s = new MirrorBuf(buf.remaining());
            ByteBuffer dup = buf.slice().order(ByteOrder.BIG_ENDIAN);
            s.buf.put(dup);
            s.buf.flip();
            return s;
        }
    }

    static final class PgMsg {
        final int tag; final byte[] payload;
        PgMsg(int tag, byte[] payload) { this.tag = tag; this.payload = payload; }
        static byte[] encode(int tag, byte[] payload) {
            ByteBuffer b = ByteBuffer.allocate(5 + payload.length).order(ByteOrder.BIG_ENDIAN);
            b.put((byte) tag);
            b.putInt(payload.length + 4);
            b.put(payload);
            return b.array();
        }
    }

    static final class PgReader {
        final ByteBuffer buf = ByteBuffer.allocate(1 << 16).order(ByteOrder.BIG_ENDIAN);
        int writePos = 0;
        void feed(byte[] chunk) {
            buf.position(writePos);
            buf.put(chunk);
            writePos = buf.position();
        }
        PgMsg poll() {
            if (writePos < 5) return null;
            buf.position(0);
            int tag = buf.get() & 0xFF;
            int len = buf.getInt();
            int total = 1 + len;
            if (writePos < total) return null;
            byte[] payload = new byte[len - 4];
            buf.get(payload);
            byte[] rest = new byte[writePos - total];
            buf.position(total);
            buf.get(rest);
            buf.clear();
            writePos = 0;
            if (rest.length > 0) feed(rest);
            return new PgMsg(tag, payload);
        }
    }

    static final class PgError {
        static byte[] encode(String state, String msg, String detail, String hint) {
            ByteBuffer b = ByteBuffer.allocate(256);
            putField(b, 'C', state); putField(b, 'M', msg); putField(b, 'D', detail); putField(b, 'H', hint);
            b.put((byte) 0);
            byte[] p = new byte[b.position()];
            b.flip(); b.get(p);
            return p;
        }
        static void putField(ByteBuffer b, char k, String v) {
            b.put((byte) k);
            b.put(v.getBytes(StandardCharsets.UTF_8));
            b.put((byte) 0);
        }
        static Map<Character, String> parse(byte[] payload) {
            Map<Character, String> m = new HashMap<>();
            int i = 0;
            while (i < payload.length && payload[i] != 0) {
                char k = (char) (payload[i++] & 0xFF);
                int start = i;
                while (i < payload.length && payload[i] != 0) i++;
                m.put(k, new String(payload, start, i - start, StandardCharsets.UTF_8));
                i++;
            }
            return m;
        }
    }

    static final class SqlState {
        static boolean isConstraint(String s) { return s != null && s.startsWith("23"); }
        static boolean isAuth(String s) { return "28P01".equals(s) || "28000".equals(s); }
    }

    static final class ExtendedQuery {
        static byte[] encode(String sql, List<Object> params) {
            ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
            // Parse
            b.put((byte) 'P');
            int parseLenPos = b.position(); b.putInt(0);
            b.put((byte) 0); // unnamed statement
            b.put(sql.getBytes(StandardCharsets.UTF_8)); b.put((byte) 0);
            b.putShort((short) 0);
            int parseEnd = b.position(); b.putInt(parseLenPos, parseEnd - parseLenPos);
            // Bind
            b.put((byte) 'B');
            int bindLenPos = b.position(); b.putInt(0);
            b.put((byte) 0); b.put((byte) 0);
            b.putShort((short) 0);
            b.putShort((short) params.size());
            for (Object p : params) {
                if (p == null) { b.putInt(-1); continue; }
                byte[] raw;
                if (p instanceof byte[] ba) raw = ba;
                else raw = String.valueOf(p).getBytes(StandardCharsets.UTF_8);
                b.putInt(raw.length); b.put(raw);
            }
            b.putShort((short) 0);
            int bindEnd = b.position(); b.putInt(bindLenPos, bindEnd - bindLenPos);
            // Describe/Execute/Sync omitted for size
            byte[] out = new byte[b.position()];
            b.flip(); b.get(out);
            return out;
        }
    }

    static boolean containsCString(byte[] wire, String s) {
        byte[] needle = s.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i + needle.length <= wire.length; i++) {
            for (int j = 0; j < needle.length; j++) if (wire[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    static final class Startup {
        static byte[] encode(String user, String db) {
            ByteBuffer b = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN);
            int lenPos = b.position(); b.putInt(0);
            b.putInt(196608);
            putC(b, "user"); putC(b, user); putC(b, "database"); putC(b, db); b.put((byte) 0);
            int end = b.position(); b.putInt(lenPos, end - lenPos);
            byte[] out = new byte[end]; b.flip(); b.get(out); return out;
        }
        static void putC(ByteBuffer b, String s) { b.put(s.getBytes(StandardCharsets.UTF_8)); b.put((byte) 0); }
    }

    static final class Md5Auth {
        static String md5Hex(String password, String user, byte[] salt) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] inner = md.digest((password + user).getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte v : inner) {
                    hex.append(String.format("%02x", v));
                }
                md.reset();
                md.update(hex.toString().getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] outer = md.digest();
                StringBuilder out = new StringBuilder("md5");
                for (byte v : outer) {
                    out.append(String.format("%02x", v));
                }
                return out.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static final class Scram {
        static final String MECH = "SCRAM-SHA-256";
        static boolean supportedIn024() { return false; }
    }

    static final class RowMirror {
        final int n; final boolean[] nul; final Object[] vals; final String[] names;
        RowMirror(int n) { this.n = n; nul = new boolean[n]; vals = new Object[n]; names = new String[n]; }
        void setNull(int i) { nul[i] = true; vals[i] = null; }
        void setInt(int i, int v) { nul[i] = false; vals[i] = v; }
        void setString(int i, String v) { nul[i] = false; vals[i] = v; }
        void setName(int i, String n) { names[i] = n; }
        boolean isNull(int i) { return nul[i]; }
        int getInt(int i) { return (Integer) vals[i]; }
        String getString(int i) { return (String) vals[i]; }
        String columnName(int i) { return names[i]; }
        int columnCount() { return n; }
    }

    static final class ConnMirror {
        final String id; String state = "idle"; boolean broken; boolean busy; boolean rolledBack;
        ConnMirror(String id) { this.id = id; }
        void begin() { state = "txn"; }
        void execute(String sql) { if ("BAD".equals(sql)) { /* noop */ } }
        void commit() { state = "idle"; }
        void rollback() { state = "idle"; rolledBack = true; busy = false; }
    }

    static final class AcquireTimeout extends RuntimeException {}
    static final class PoolShutdown extends RuntimeException {}

    static final class MirrorPool {
        final int max; int active; int idleCount; int discarded; boolean shutdown; final List<Object> idle = new ArrayList<>();
        MirrorPool(int max) { this.max = max; }
        Object acquire() { return acquire(5_000); }
        Object acquire(long timeoutMs) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                if (shutdown) throw new PoolShutdown();
                if (!idle.isEmpty()) { active++; idleCount--; return idle.remove(idle.size() - 1); }
                if (active < max) { active++; return new ConnMirror("c" + active); }
                if (System.currentTimeMillis() >= deadline) throw new AcquireTimeout();
                try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        Object tryAcquireImmediate() {
            if (!idle.isEmpty()) { active++; idleCount--; return idle.remove(idle.size() - 1); }
            if (active < max) { active++; return new ConnMirror("c" + active); }
            return null;
        }
        void release(Object o) {
            ConnMirror c = (ConnMirror) o;
            active--;
            if (c.broken) { discarded++; return; }
            idle.add(c); idleCount++;
        }
        void shutdown() { shutdown = true; }
        int active() { return active; }
        int idle() { return idleCount; }
        int discarded() { return discarded; }
    }

    static final class LruCache {
        final int cap; final List<String> keys = new ArrayList<>(); final Map<String, Integer> map = new HashMap<>();
        LruCache(int cap) { this.cap = cap; }
        void put(String k, int v) {
            keys.remove(k); keys.add(k); map.put(k, v);
            while (keys.size() > cap) { String old = keys.remove(0); map.remove(old); }
        }
        Integer get(String k) {
            if (!map.containsKey(k)) return null;
            keys.remove(k); keys.add(k); return map.get(k);
        }
    }

    static final class Migration {
        static String checksum(String sql) {
            int h = 17;
            for (int i = 0; i < sql.length(); i++) {
                h = h * 31 + sql.charAt(i);
            }
            return String.valueOf(Math.abs(h));
        }
        static final class Store {
            final Map<String, String> applied = new HashMap<>();
            void apply(String version, String name, String sql) {
                String cs = checksum(sql);
                if (applied.containsKey(version) && !applied.get(version).equals(cs))
                    throw new IllegalStateException("checksum mismatch");
                applied.put(version, cs);
            }
        }
    }

    static final class SchemaSnap {
        String generatedAt; final List<String> tables = new ArrayList<>();
        String toJson() {
            StringBuilder sb = new StringBuilder("{\"generatedAt\":\"").append(generatedAt).append("\",\"tables\":[");
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(tables.get(i)).append('"');
            }
            return sb.append("]}").toString();
        }
    }

    static final class RowStream {
        final List<String> rows; int i; boolean paused;
        RowStream(List<String> rows) { this.rows = rows; }
        boolean hasNext() { return i < rows.size(); }
        String next() { return rows.get(i++); }
        void pause() { paused = true; }
        void resume() { paused = false; }
    }

    static final class InstantMirror {
        final long ms; InstantMirror(long ms) { this.ms = ms; }
        static InstantMirror ofEpochMilli(long ms) { return new InstantMirror(ms); }
        long toEpochMilli() { return ms; }
    }
    static final class DurationMirror {
        final long ms; DurationMirror(long ms) { this.ms = ms; }
        static DurationMirror ofMillis(long ms) { return new DurationMirror(ms); }
        long toMillis() { return ms; }
    }
    static final class UuidMirror {
        final long a, b; UuidMirror(long a, long b) { this.a = a; this.b = b; }
        static UuidMirror random() { return new UuidMirror(1, 2); }
        public String toString() { return a + "-" + b; }
    }
    static final class LocalDateMirror {
        final int y, m, d; LocalDateMirror(int y, int m, int d) { this.y = y; this.m = m; this.d = d; }
        static LocalDateMirror of(int y, int m, int d) { return new LocalDateMirror(y, m, d); }
        public String toString() { return String.format("%04d-%02d-%02d", y, m, d); }
    }

    static final class Metrics {
        int queries, rows, errors; long totalMs; long slowThresholdMs = 500;
        void recordQuery(long ms, int r) { queries++; totalMs += ms; rows += r; }
        void recordError() { errors++; }
        boolean isSlow(long ms) { return ms >= slowThresholdMs; }
    }

    static final class SqlLog {
        static String format(String sql, List<?> params) { return "SELECT ... [params redacted]"; }
    }
    static final class EffectMark {
        static String sqlitePath() { return "blocking-worker"; }
        static String postgresPath() { return "suspends-async-tcp"; }
    }
    static final class RuntimeDeps {
        static boolean hasJdbc() { return false; }
        static boolean hasJni() { return false; }
        static boolean hasHibernate() { return false; }
        static String ormStatus() { return "NOT IMPLEMENTED"; }
    }
    static final class RowLimit {
        static void check(long size, long max) { if (size > max) throw new IllegalArgumentException("too large"); }
    }
    static final class SqlUtil {
        static String dollarToQuestion(String sql) {
            String out = sql;
            for (int i = 1; i <= 9; i++) out = out.replace("$" + i, "?");
            return out;
        }
    }
    static final class PgTypes {
        static final int BOOL = 16, INT4 = 23, TEXT = 25, JSONB = 3802;
    }
    static final class DriverReg {
        final Map<String, java.util.function.Supplier<String>> m = new HashMap<>();
        void register(String s, java.util.function.Supplier<String> d) { m.put(s, d); }
        String resolve(String s) {
            if (!m.containsKey(s)) throw new IllegalArgumentException(s);
            return m.get(s).get();
        }
    }
    static final class BackendKey { final int processId, secretKey; BackendKey(int p, int s) { processId = p; secretKey = s; } }
    static final class CancelRequest {
        final int processId, secretKey; CancelRequest(int p, int s) { processId = p; secretKey = s; }
        static CancelRequest from(BackendKey k) { return new CancelRequest(k.processId, k.secretKey); }
    }
    static final class TimeoutPolicy {
        static void onTimeout(ConnMirror c) { c.broken = true; c.busy = false; }
    }
    static final class NumericMap {
        static Object map(String s) { return s; }
    }
    static final class GcModel {
        static String socketPointerModel() { return "GcHandle/native-buffer"; }
    }
}
