/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Database stdlib (0.24) — jnative.db core: URL parsing, pool, metrics, driver SPI.

/**
 * @author 张晓达
 */
class DatabaseException extends RuntimeException {
    DatabaseException(String message) {
        super(message);
    }
}

class ConnectionException extends DatabaseException {
    ConnectionException(String message) {
        super(message);
    }
}

class AuthenticationException extends DatabaseException {
    AuthenticationException(String message) {
        super(message);
    }
}

class QueryException extends DatabaseException {
    QueryException(String message) {
        super(message);
    }
}

class ConstraintViolationException extends DatabaseException {
    ConstraintViolationException(String message) {
        super(message);
    }
}

class TransactionException extends DatabaseException {
    TransactionException(String message) {
        super(message);
    }
}

class QueryTimeoutException extends DatabaseException {
    QueryTimeoutException(String message) {
        super(message);
    }
}

class ConnectionAcquireTimeoutException extends DatabaseException {
    ConnectionAcquireTimeoutException(String message) {
        super(message);
    }
}

class DbLoopViolationException extends DatabaseException {
    DbLoopViolationException(String message) {
        super(message);
    }
}

class DbByteUtil {
    static int unsignedByte(byte b) {
        int v = b;
        if (v < 0) {
            return v + 256;
        }
        return v;
    }

    static byte toByte(int v) {
        if (v > 127) {
            return v - 256;
        }
        return v;
    }

    static int byteAt(int v, int byteIndex) {
        int divisor = 1;
        int i = 0;
        while (i < byteIndex) {
            divisor = divisor * 256;
            i = i + 1;
        }
        int q = v / divisor;
        if (q < 0) {
            q = -q;
        }
        return q % 256;
    }

    static int byteAtLong(long v, int byteIndex) {
        long divisor = 1L;
        int i = 0;
        while (i < byteIndex) {
            divisor = divisor * 256L;
            i = i + 1;
        }
        long q = v / divisor;
        if (q < 0L) {
            q = -q;
        }
        while (q >= 256L) {
            q = q - 256L;
        }
        return DbParseUtil.longToIntMod256(q);
    }
}

class DbByteBuffer {
    byte[] data;
    int position;
    int limit;
    int capacity;

    DbByteBuffer() {
        capacity = 256;
        data = new byte[capacity];
        position = 0;
        limit = 0;
    }

    DbByteBuffer(int cap) {
        if (cap < 16) {
            cap = 16;
        }
        capacity = cap;
        data = new byte[capacity];
        position = 0;
        limit = 0;
    }

    void ensureCapacity(int need) {
        if (need <= capacity) {
            return;
        }
        int ncap = capacity * 2;
        while (ncap < need) {
            ncap = ncap * 2;
        }
        byte[] nd = new byte[ncap];
        Bytes.copy(data, 0, nd, 0, limit);
        data = nd;
        capacity = ncap;
    }

    int capacity() {
        return capacity;
    }

    int getPosition() {
        return position;
    }

    void setPosition(int pos) {
        position = pos;
    }

    int getLimit() {
        return limit;
    }

    void setLimit(int lim) {
        limit = lim;
    }

    int remaining() {
        return limit - position;
    }

    void flip() {
        /* Write cursor is `limit`; prepare for reading from the start. */
        position = 0;
    }

    void clear() {
        position = 0;
        limit = capacity;
    }

    void putByte(int b) {
        ensureCapacity(limit + 1);
        data[limit] = b;
        limit = limit + 1;
    }

    void putInt16(int v) {
        ensureCapacity(limit + 2);
        data[limit] = DbByteUtil.byteAt(v, 1);
        data[limit + 1] = DbByteUtil.byteAt(v, 0);
        limit = limit + 2;
    }

    void putInt32(int v) {
        ensureCapacity(limit + 4);
        data[limit] = DbByteUtil.byteAt(v, 3);
        data[limit + 1] = DbByteUtil.byteAt(v, 2);
        data[limit + 2] = DbByteUtil.byteAt(v, 1);
        data[limit + 3] = DbByteUtil.byteAt(v, 0);
        limit = limit + 4;
    }

    void putInt64(long v) {
        ensureCapacity(limit + 8);
        data[limit] = DbByteUtil.byteAtLong(v, 7);
        data[limit + 1] = DbByteUtil.byteAtLong(v, 6);
        data[limit + 2] = DbByteUtil.byteAtLong(v, 5);
        data[limit + 3] = DbByteUtil.byteAtLong(v, 4);
        data[limit + 4] = DbByteUtil.byteAtLong(v, 3);
        data[limit + 5] = DbByteUtil.byteAtLong(v, 2);
        data[limit + 6] = DbByteUtil.byteAtLong(v, 1);
        data[limit + 7] = DbByteUtil.byteAtLong(v, 0);
        limit = limit + 8;
    }

    void putBytes(byte[] src) {
        if (src == null || src.length == 0) {
            return;
        }
        ensureCapacity(limit + src.length);
        Bytes.copy(src, 0, data, limit, src.length);
        limit = limit + src.length;
    }

    int getByte() {
        if (position >= limit) {
            return -1;
        }
        int v = DbByteUtil.unsignedByte(data[position]);
        position = position + 1;
        return v;
    }

    int getInt16() {
        int b0 = getByte();
        int b1 = getByte();
        if (b0 < 0 || b1 < 0) {
            return 0;
        }
        int v = b0 * 256 + b1;
        if (v >= 32768) {
            v = v - 65536;
        }
        return v;
    }

    int getInt32() {
        int b0 = getByte();
        int b1 = getByte();
        int b2 = getByte();
        int b3 = getByte();
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            return 0;
        }
        return b0 * 16777216 + b1 * 65536 + b2 * 256 + b3;
    }

    long getInt64() {
        long v = 0L;
        int i = 0;
        while (i < 8) {
            int b = getByte();
            if (b < 0) {
                return 0L;
            }
            v = v * 256L + b;
            i = i + 1;
        }
        return v;
    }

    byte[] slice(int from, int to) {
        return Bytes.slice(data, from, to);
    }

    byte[] toArray() {
        return Bytes.slice(data, 0, limit);
    }

    void putCString(String s) {
        if (s == null) {
            putByte(0);
            return;
        }
        putBytes(Bytes.fromString(s));
        putByte(0);
    }

    String getCString() {
        int start = position;
        while (position < limit) {
            int b = DbByteUtil.unsignedByte(data[position]);
            if (b == 0) {
                byte[] slice = Bytes.slice(data, start, position);
                position = position + 1;
                return Bytes.toString(slice);
            }
            position = position + 1;
        }
        return "";
    }

    byte[] getBytes(int len) {
        if (len <= 0) {
            return new byte[0];
        }
        if (position + len > limit) {
            return new byte[0];
        }
        byte[] out = Bytes.slice(data, position, position + len);
        position = position + len;
        return out;
    }

    int peekByteAt(int idx) {
        if (idx < 0 || idx >= limit) {
            return -1;
        }
        return DbByteUtil.unsignedByte(data[idx]);
    }

    int peekInt32At(int idx) {
        int b0 = peekByteAt(idx);
        int b1 = peekByteAt(idx + 1);
        int b2 = peekByteAt(idx + 2);
        int b3 = peekByteAt(idx + 3);
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            return 0;
        }
        return b0 * 16777216 + b1 * 65536 + b2 * 256 + b3;
    }
}

class DatabaseUrl {
    String scheme;
    String host;
    int port;
    String database;
    String user;
    String password;
    String sqlitePath;

    DatabaseUrl() {
        scheme = "";
        host = "";
        port = 0;
        database = "";
        user = "";
        password = "";
        sqlitePath = "";
    }

    static DatabaseUrl parse(String url) {
        if (url == null || url.length() == 0) {
            throw new ConnectionException("empty database url");
        }
        DatabaseUrl u = new DatabaseUrl();
        int schemeEnd = url.indexOf(":");
        if (schemeEnd < 0) {
            throw new ConnectionException("missing scheme in url: " + url);
        }
        u.scheme = url.substring(0, schemeEnd).toLowerCase();
        if (u.scheme.equals("sqlite")) {
            u.sqlitePath = url.substringFrom(schemeEnd + 1);
            if (u.sqlitePath.length() == 0) {
                throw new ConnectionException("sqlite url missing path");
            }
            u.sqlitePath = normalizeSqlitePath(u.sqlitePath);
            return u;
        }
        if (u.scheme.equals("postgres")) {
            u.scheme = "postgresql";
        }
        if (!u.scheme.equals("postgresql")) {
            throw new ConnectionException("unsupported scheme: " + u.scheme);
        }
        u.port = 5432;
        String rest = url.substringFrom(schemeEnd + 3);
        int at = rest.indexOf("@");
        if (at >= 0) {
            String auth = rest.substring(0, at);
            rest = rest.substringFrom(at + 1);
            int colon = auth.indexOf(":");
            if (colon >= 0) {
                u.user = auth.substring(0, colon);
                u.password = auth.substringFrom(colon + 1);
            } else {
                u.user = auth;
                u.password = "";
            }
        }
        int slash = rest.indexOf("/");
        String hostPort = rest;
        if (slash >= 0) {
            hostPort = rest.substring(0, slash);
            u.database = rest.substringFrom(slash + 1);
            int q = u.database.indexOf("?");
            if (q >= 0) {
                u.database = u.database.substring(0, q);
            }
        } else {
            u.database = "";
        }
        int hpColon = hostPort.indexOf(":");
        if (hpColon >= 0) {
            u.host = hostPort.substring(0, hpColon);
            u.port = DbParseUtil.parsePort(hostPort.substringFrom(hpColon + 1));
        } else {
            u.host = hostPort;
        }
        return u;
    }

    static String normalizeSqlitePath(String path) {
        if (path.equals(":memory:")) {
            return "file:jnative_shared_mem?mode=memory&cache=shared";
        }
        return path;
    }
}

class DbParseUtil {
    static int longToIntMod256(long q) {
        while (q >= 256L) {
            q = q - 256L;
        }
        while (q < 0L) {
            q = q + 256L;
        }
        int out = 0;
        while (q > 0L) {
            out = out + 1;
            q = q - 1L;
        }
        return out;
    }

    static int parsePort(String s) {
        if (s == null || s.length() == 0) {
            return 5432;
        }
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                throw new ConnectionException("invalid port: " + s);
            }
            n = n * 10 + (c - 48);
            i = i + 1;
        }
        return n;
    }

    static int parseInt(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int n = 0;
        int neg = 0;
        int i = 0;
        if (s.charAt(0) == 45) {
            neg = 1;
            i = 1;
        }
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return 0;
            }
            n = n * 10 + (c - 48);
            i = i + 1;
        }
        if (neg != 0) {
            n = -n;
        }
        return n;
    }

    static String digitString(int digit) {
        if (digit == 0) {
            return "0";
        }
        if (digit == 1) {
            return "1";
        }
        if (digit == 2) {
            return "2";
        }
        if (digit == 3) {
            return "3";
        }
        if (digit == 4) {
            return "4";
        }
        if (digit == 5) {
            return "5";
        }
        if (digit == 6) {
            return "6";
        }
        if (digit == 7) {
            return "7";
        }
        if (digit == 8) {
            return "8";
        }
        return "9";
    }

    static String intToString(int v) {
        if (v == 0) {
            return "0";
        }
        String out = "";
        int neg = 0;
        if (v < 0) {
            neg = 1;
            v = -v;
        }
        while (v > 0) {
            int digit = v - (v / 10) * 10;
            out = digitString(digit) + out;
            v = v / 10;
        }
        if (neg != 0) {
            out = "-" + out;
        }
        return out;
    }

    static String longToString(long v) {
        if (v == 0L) {
            return "0";
        }
        String out = "";
        int neg = 0;
        if (v < 0L) {
            neg = 1;
            v = -v;
        }
        while (v > 0L) {
            long rem = v - (v / 10L) * 10L;
            int digit = 0;
            int i = 0;
            while (i < rem) {
                digit = digit + 1;
                i = i + 1;
            }
            out = digitString(digit) + out;
            v = v / 10L;
        }
        if (neg != 0) {
            out = "-" + out;
        }
        return out;
    }

    static long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0L;
        }
        long n = 0L;
        int neg = 0;
        int i = 0;
        if (s.charAt(0) == 45) {
            neg = 1;
            i = 1;
        }
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return 0L;
            }
            n = n * 10L + (c - 48);
            i = i + 1;
        }
        if (neg != 0) {
            n = -n;
        }
        return n;
    }

    static double parseDouble(String s) {
        if (s == null || s.length() == 0) {
            return 0.0;
        }
        int dot = s.indexOf(".");
        if (dot < 0) {
            return parseInt(s);
        }
        String whole = s.substring(0, dot);
        String frac = s.substringFrom(dot + 1);
        double w = parseInt(whole);
        double f = 0.0;
        double place = 0.1;
        int i = 0;
        while (i < frac.length()) {
            int c = frac.charAt(i);
            if (c >= 48 && c <= 57) {
                f = f + (c - 48) * place;
                place = place * 0.1;
            }
            i = i + 1;
        }
        if (whole.startsWith("-")) {
            return w - f;
        }
        return w + f;
    }

    static String dollarToQuestion(String sql) {
        if (sql == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < sql.length()) {
            int c = sql.charAt(i);
            if (c == 36) {
                int j = i + 1;
                int hasDigit = 0;
                while (j < sql.length()) {
                    int d = sql.charAt(j);
                    if (d >= 48 && d <= 57) {
                        hasDigit = 1;
                        j = j + 1;
                    } else {
                        j = sql.length();
                    }
                }
                if (hasDigit != 0) {
                    out = out + "?";
                    i = j;
                } else {
                    out = out + "$";
                    i = i + 1;
                }
            } else {
                out = out + sql.substring(i, i + 1);
                i = i + 1;
            }
        }
        return out;
    }

    static String questionToDollar(String sql) {
        if (sql == null) {
            return "";
        }
        String out = "";
        int i = 0;
        int n = 0;
        while (i < sql.length()) {
            int c = sql.charAt(i);
            if (c == 63) {
                n = n + 1;
                out = out + "$" + intToString(n);
                i = i + 1;
            } else {
                out = out + sql.substring(i, i + 1);
                i = i + 1;
            }
        }
        return out;
    }
}

class Row {
    String[] columnNames;
    int[] columnTypes;
    String[] stringValues;
    long[] longValues;
    double[] doubleValues;
    ArrayList<Object> blobValues;
    int[] nullFlags;
    int count;

    Row(int cap) {
        columnNames = new String[cap];
        columnTypes = new int[cap];
        stringValues = new String[cap];
        longValues = new long[cap];
        doubleValues = new double[cap];
        blobValues = new ArrayList<Object>();
        int j = 0;
        while (j < cap) {
            blobValues.add(null);
            j = j + 1;
        }
        nullFlags = new int[cap];
        count = 0;
    }

    int columnCount() {
        return count;
    }

    String columnName(int index) {
        if (index < 0 || index >= count) {
            return "";
        }
        return columnNames[index];
    }

    int isNull(int index) {
        if (index < 0 || index >= count) {
            return 1;
        }
        return nullFlags[index];
    }

    String getString(int index) {
        if (isNull(index) != 0) {
            return null;
        }
        return stringValues[index];
    }

    int getInt(int index) {
        if (isNull(index) != 0) {
            return 0;
        }
        if (columnTypes[index] == DbColumnType.INTEGER) {
            return DbParseUtil.parseInt(DbParseUtil.longToString(longValues[index]));
        }
        return DbParseUtil.parseInt(stringValues[index]);
    }

    long getLong(int index) {
        if (isNull(index) != 0) {
            return 0L;
        }
        if (columnTypes[index] == DbColumnType.INTEGER) {
            return longValues[index];
        }
        return DbParseUtil.parseLong(stringValues[index]);
    }

    double getDouble(int index) {
        if (isNull(index) != 0) {
            return 0.0;
        }
        if (columnTypes[index] == DbColumnType.FLOAT) {
            return doubleValues[index];
        }
        if (columnTypes[index] == DbColumnType.INTEGER) {
            return DbParseUtil.parseDouble(DbParseUtil.longToString(longValues[index]));
        }
        return DbParseUtil.parseDouble(stringValues[index]);
    }

    int getBoolean(int index) {
        if (isNull(index) != 0) {
            return 0;
        }
        if (columnTypes[index] == DbColumnType.INTEGER) {
            if (longValues[index] != 0L) {
                return 1;
            }
            return 0;
        }
        String s = stringValues[index];
        if (s == null) {
            return 0;
        }
        if (s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t")) {
            return 1;
        }
        return 0;
    }

    byte[] getBytes(int index) {
        if (isNull(index) != 0) {
            return null;
        }
        if (columnTypes[index] == DbColumnType.BLOB) {
            Object blob = blobValues.get(index);
            if (blob instanceof DbBlobHolder) {
                DbBlobHolder holder = (DbBlobHolder)blob;
                return holder.data;
            }
            return null;
        }
        if (stringValues[index] != null) {
            return Bytes.fromString(stringValues[index]);
        }
        return null;
    }

    void setColumn(int index, String name, int type, int isNullVal) {
        columnNames[index] = name;
        columnTypes[index] = type;
        nullFlags[index] = isNullVal;
    }
}

class DbBlobHolder {
    byte[] data;

    DbBlobHolder(byte[] data) {
        this.data = data;
    }
}

class DbColumnType {
    static int NULL = 0;
    static int INTEGER = 1;
    static int FLOAT = 2;
    static int TEXT = 3;
    static int BLOB = 4;
}

interface RowMapper {
    Object map(Row row);
}

interface TypeCodec {
    Row encode(Object value);

    Object decode(Row row);
}

interface DatabaseDriver {
    JnFuture connect(DatabaseConfig config);
}

class DbConnection {
    Object sqliteBackend;
    int broken;
    int inTransaction;

    DbConnection() {
        sqliteBackend = null;
        broken = 0;
        inTransaction = 0;
    }

    JnFuture query(String sql, ArrayList<Object> params) {
        if (sqliteBackend == null) {
            throw new QueryException("DbConnection.query: no backend");
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            return pg.query(sql, params);
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        return sc.query(sql, params);
    }

    JnFuture queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        if (sqliteBackend == null) {
            throw new QueryException("DbConnection.queryOne: no backend");
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            return pg.queryOne(sql, params, mapper);
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        return sc.queryOne(sql, params, mapper);
    }

    JnFuture execute(String sql, ArrayList<Object> params) {
        if (sqliteBackend == null) {
            throw new QueryException("DbConnection.execute: no backend");
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            return pg.execute(sql, params);
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        return sc.execute(sql, params);
    }

    JnFuture beginTransaction() {
        if (sqliteBackend == null) {
            throw new QueryException("DbConnection.beginTransaction: no backend");
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            return pg.beginTransaction();
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        return sc.beginTransaction();
    }

    JnFuture commit() {
        if (sqliteBackend == null) {
            throw new QueryException("DbConnection.commit: no backend");
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            return pg.commit();
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        return sc.commit();
    }

    JnFuture rollback() {
        if (sqliteBackend == null) {
            throw new QueryException("DbConnection.rollback: no backend");
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            return pg.rollback();
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        return sc.rollback();
    }

    int isBroken() {
        if (sqliteBackend == null) {
            return broken;
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            if (pg.isBroken() != 0) {
                return 1;
            }
            return broken;
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        if (sc.isBroken() != 0) {
            return 1;
        }
        return broken;
    }

    void release() {
        if (sqliteBackend == null) {
            return;
        }
        if (sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)sqliteBackend;
            pg.release();
            return;
        }
        SQLiteConnection sc = (SQLiteConnection)sqliteBackend;
        sc.release();
    }
}

interface DatabaseBackend {
    JnFuture query(String sql, ArrayList<Object> params);

    JnFuture queryOne(String sql, ArrayList<Object> params, RowMapper mapper);

    JnFuture execute(String sql, ArrayList<Object> params);

    JnFuture beginTransaction();

    JnFuture commit();

    JnFuture rollback();

    void release();

    int isBroken();
}

interface DbTransactionWork {
    JnFuture run(DbConnection conn);
}

class DatabaseConfig {
    String url;
    EventLoop loop;
    int minPool;
    int maxPool;
    int idleTimeoutMs;
    int acquireTimeoutMs;

    DatabaseConfig() {
        url = "";
        loop = null;
        minPool = 1;
        maxPool = 4;
        idleTimeoutMs = 60000;
        acquireTimeoutMs = 5000;
    }
}

class DbMetrics {
    int connectionsCreated;
    int connectionsActive;
    int connectionsIdle;
    int queriesExecuted;
    int queryErrors;
    int poolAcquireTimeouts;
    long totalQueryMs;

    DbMetrics() {
        connectionsCreated = 0;
        connectionsActive = 0;
        connectionsIdle = 0;
        queriesExecuted = 0;
        queryErrors = 0;
        poolAcquireTimeouts = 0;
        totalQueryMs = 0L;
    }
}

class DriverRegistry {
    static HashMap<String, DatabaseDriver> drivers;
    static int initDone;

    static void ensureInit() {
        if (initDone != 0) {
            return;
        }
        drivers = new HashMap<String, DatabaseDriver>();
        initDone = 1;
    }

    static void register(String scheme, DatabaseDriver driver) {
        ensureInit();
        drivers.put(scheme, driver);
    }

    static DatabaseDriver resolve(String scheme) {
        ensureInit();
        DatabaseDriver d = drivers.get(scheme);
        if (d == null) {
            throw new ConnectionException("no driver registered for scheme: " + scheme);
        }
        return d;
    }
}

class DbLoopGuard {
    static void checkNotOnLoop(EventLoop loop, String operation) {
        if (loop == null) {
            return;
        }
        if (loop.isLoopThread()) {
            throw new DbLoopViolationException(
                    "blocking database call on event loop thread: " + operation);
        }
    }
}

class FutureBridge {
    static Future<Object> toAwaitable(JnFuture jf) {
        FutureBlocker.blockingWait(jf);
        if (jf.getState() == JnFuture.FAILED) {
            throw jf.getError();
        }
        if (jf.getState() == JnFuture.CANCELLED) {
            throw new RuntimeException("future cancelled");
        }
        return Future.completed(jf.getValue());
    }

    static JnFuture fromCompletedFuture(Future<Object> cf) {
        EventLoop loop = Async.defaultLoop();
        if (Future.isComplete(cf)) {
            Object v = Future.getCompleted(cf);
            return JnFuture.completed(loop, v);
        }
        return JnFuture.failed(loop, new RuntimeException("compiler Future not complete"));
    }
}

class ConnectionPool {
    EventLoop loop;
    DatabaseDriver driver;
    DatabaseConfig config;
    DatabaseUrl parsedUrl;
    AsyncSemaphore semaphore;
    ArrayList<DbConnection> idle;
    DbMetrics metrics;
    Mutex lock;

    ConnectionPool(DatabaseConfig config, DatabaseUrl parsedUrl, DatabaseDriver driver) {
        this.config = config;
        this.parsedUrl = parsedUrl;
        this.driver = driver;
        if (config.loop != null) {
            loop = config.loop;
        } else {
            loop = Async.defaultLoop();
        }
        semaphore = new AsyncSemaphore(loop, config.maxPool);
        idle = new ArrayList<DbConnection>();
        metrics = new DbMetrics();
        lock = new Mutex();
    }

    DbMetrics getMetrics() {
        return metrics;
    }

    JnFuture acquire() {
        JnFuture permit = semaphore.acquire();
        PoolAcquireStep step = new PoolAcquireStep();
        step.pool = this;
        return permit.flatMap(step);
    }

    void release(DbConnection conn) {
        if (conn == null) {
            return;
        }
        if (conn.isBroken() != 0) {
            discard(conn);
            return;
        }
        lock.lock();
        metrics.connectionsActive = metrics.connectionsActive - 1;
        metrics.connectionsIdle = metrics.connectionsIdle + 1;
        idle.add(conn);
        lock.unlock();
        semaphore.release();
    }

    void discard(DbConnection conn) {
        lock.lock();
        metrics.connectionsActive = metrics.connectionsActive - 1;
        lock.unlock();
        conn.release();
        semaphore.release();
    }

    JnFuture takeIdleOrConnect() {
        lock.lock();
        if (idle.size() > 0) {
            DbConnection c = idle.removeAt(idle.size() - 1);
            metrics.connectionsIdle = metrics.connectionsIdle - 1;
            metrics.connectionsActive = metrics.connectionsActive + 1;
            lock.unlock();
            return JnFuture.completed(loop, c);
        }
        lock.unlock();
        JnFuture created = driver.connect(config);
        PoolConnectDone done = new PoolConnectDone();
        done.pool = this;
        return created.then(done);
    }
}

class PoolAcquireStep implements FlatMapFunction {
    ConnectionPool pool;

    public JnFuture apply(Object ignored) {
        return pool.takeIdleOrConnect();
    }
}

class PoolConnectDone implements Function<Object, Object> {
    ConnectionPool pool;

    public Object apply(Object v) {
        pool.lock.lock();
        pool.metrics.connectionsCreated = pool.metrics.connectionsCreated + 1;
        pool.metrics.connectionsActive = pool.metrics.connectionsActive + 1;
        pool.lock.unlock();
        return v;
    }
}

class Database {
    ConnectionPool pool;
    EventLoop loop;
    DbMetrics metrics;

    Database(ConnectionPool pool) {
        this.pool = pool;
        this.loop = pool.loop;
        this.metrics = pool.metrics;
    }

    static JnFuture connect(String url) {
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        return connectWithConfig(cfg);
    }

    static JnFuture connectWithConfig(DatabaseConfig config) {
        if (config == null) {
            throw new ConnectionException("null DatabaseConfig");
        }
        if (config.url == null || config.url.length() == 0) {
            throw new ConnectionException("empty url");
        }
        DbBootstrap.ensureDrivers();
        DatabaseUrl parsed = DatabaseUrl.parse(config.url);
        DatabaseDriver driver = DriverRegistry.resolve(parsed.scheme);
        ConnectionPool pool = new ConnectionPool(config, parsed, driver);
        Database db = new Database(pool);
        return JnFuture.completed(pool.loop, db);
    }

    JnFuture acquire() {
        return pool.acquire();
    }

    JnFuture query(String sql, ArrayList<Object> params) {
        PoolQueryAllWork work = new PoolQueryAllWork();
        work.db = this;
        work.sql = sql;
        work.params = params;
        return withConnection(work);
    }

    JnFuture queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        PoolQueryOneWork work = new PoolQueryOneWork();
        work.db = this;
        work.sql = sql;
        work.params = params;
        work.mapper = mapper;
        return withConnection(work);
    }

    JnFuture execute(String sql, ArrayList<Object> params) {
        PoolExecuteWork work = new PoolExecuteWork();
        work.db = this;
        work.sql = sql;
        work.params = params;
        return withConnection(work);
    }

    JnFuture transaction(DbTransactionWork work) {
        PoolTransactionWork tw = new PoolTransactionWork();
        tw.db = this;
        tw.work = work;
        return withConnection(tw);
    }

    JnFuture withConnection(DbPoolWork work) {
        JnFuture connF = pool.acquire();
        WithConnectionFlatMap fm = new WithConnectionFlatMap();
        fm.db = this;
        fm.work = work;
        return connF.flatMap(fm);
    }

    DbMetrics getMetrics() {
        return metrics;
    }
}

interface DbPoolWork {
    JnFuture run(DbConnection conn);
}

class WithConnectionFlatMap implements FlatMapFunction {
    Database db;
    DbPoolWork work;

    public JnFuture apply(Object v) {
        DbConnection conn = (DbConnection)v;
        JnFuture result = work.run(conn);
        ReleaseAfterWork release = new ReleaseAfterWork();
        release.db = db;
        release.conn = conn;
        return result.finallyDo(release);
    }
}

class ReleaseAfterWork implements Consumer<Object> {
    Database db;
    DbConnection conn;

    public void accept(Object ignored) {
        db.pool.release(conn);
    }
}

class PoolQueryAllWork implements DbPoolWork {
    Database db;
    String sql;
    ArrayList<Object> params;

    public JnFuture run(DbConnection conn) {
        long start = AsyncClockNative.monoMillis();
        JnFuture q = conn.query(sql, params);
        QueryMetricsCallback cb = new QueryMetricsCallback();
        cb.db = db;
        cb.startMs = start;
        return q.then(cb);
    }
}

class QueryMetricsCallback implements Function<Object, Object> {
    Database db;
    long startMs;

    public Object apply(Object v) {
        long end = AsyncClockNative.monoMillis();
        db.metrics.queriesExecuted = db.metrics.queriesExecuted + 1;
        db.metrics.totalQueryMs = db.metrics.totalQueryMs + (end - startMs);
        return v;
    }
}

class PoolQueryOneWork implements DbPoolWork {
    Database db;
    String sql;
    ArrayList<Object> params;
    RowMapper mapper;

    public JnFuture run(DbConnection conn) {
        long start = AsyncClockNative.monoMillis();
        JnFuture q = conn.queryOne(sql, params, mapper);
        QueryMetricsCallback cb = new QueryMetricsCallback();
        cb.db = db;
        cb.startMs = start;
        return q.then(cb);
    }
}

class PoolExecuteWork implements DbPoolWork {
    Database db;
    String sql;
    ArrayList<Object> params;

    public JnFuture run(DbConnection conn) {
        long start = AsyncClockNative.monoMillis();
        JnFuture q = conn.execute(sql, params);
        QueryMetricsCallback cb = new QueryMetricsCallback();
        cb.db = db;
        cb.startMs = start;
        return q.then(cb);
    }
}

class PoolTransactionWork implements DbPoolWork {
    Database db;
    DbTransactionWork work;

    public JnFuture run(DbConnection conn) {
        JnFuture begun = conn.beginTransaction();
        TransactionFlatMap fm = new TransactionFlatMap();
        fm.conn = conn;
        fm.work = work;
        return begun.flatMap(fm);
    }
}

class TransactionFlatMap implements FlatMapFunction {
    DbConnection conn;
    DbTransactionWork work;

    public JnFuture apply(Object ignored) {
        JnFuture ran;
        try {
            ran = work.run(conn);
        } catch (Exception ex) {
            FutureBlocker.blockingWait(conn.rollback());
            return JnFuture.failed(TransactionLoopUtil.loopOf(conn), ex);
        }
        TransactionCommitMap cm = new TransactionCommitMap();
        cm.conn = conn;
        JnFuture committed = ran.flatMap(cm);
        TransactionRollbackRecover recover = new TransactionRollbackRecover();
        recover.conn = conn;
        return committed.recover(recover);
    }
}

class TransactionLoopUtil {
    static EventLoop loopOf(DbConnection conn) {
        if (conn.sqliteBackend instanceof PgConnection) {
            PgConnection pg = (PgConnection)conn.sqliteBackend;
            return pg.loop;
        }
        SQLiteConnection sc = (SQLiteConnection)conn.sqliteBackend;
        return sc.loop;
    }
}

class TransactionRollbackRecover implements RecoverFunction {
    DbConnection conn;

    public Object apply(Exception ex) {
        FutureBlocker.blockingWait(conn.rollback());
        conn.broken = 1;
        String msg = "transaction rolled back";
        if (ex != null && ex.getMessage() != null) {
            msg = msg + ": " + ex.getMessage();
        }
        throw new TransactionException(msg);
    }
}

class TransactionCommitMap implements FlatMapFunction {
    DbConnection conn;

    public JnFuture apply(Object v) {
        JnFuture committed = conn.commit();
        TransactionReturnValue rv = new TransactionReturnValue();
        rv.value = v;
        rv.conn = conn;
        return committed.then(rv);
    }
}

class TransactionReturnValue implements Function<Object, Object> {
    Object value;
    DbConnection conn;

    public Object apply(Object ignored) {
        if (conn != null) {
            conn.broken = 1;
        }
        return value;
    }
}

class DbQueryResult {
    ArrayList<Row> rows;
    int affectedRows;

    DbQueryResult() {
        rows = new ArrayList<Row>();
        affectedRows = 0;
    }
}

class DbBootstrap {
    static int done;

    static void ensureDrivers() {
        if (done != 0) {
            return;
        }
        DbSqliteBootstrap.register();
        /* PostgreSQL wire driver (md5/trust; SCRAM still incomplete — B-P0-05). */
        DbPgBootstrap.register();
        done = 1;
    }
}
