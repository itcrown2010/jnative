/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative SQLite driver (0.24) — sqlite3 via @Native FFI, all I/O on worker pool.

/**
 * @author 张晓达
 */
class Sqlite3Native {
    @Native(library = "sqlite3", symbol = "sqlite3_open_v2")
    static native int openV2(String filename, NativePtr ppDb, int flags, String vfs);

    @Native(library = "sqlite3", symbol = "sqlite3_close")
    static native int close(NativePtr db);

    @Native(library = "sqlite3", symbol = "sqlite3_prepare_v2")
    static native int prepareV2(NativePtr db, String sql, int nByte, NativePtr ppStmt, NativePtr pzTail);

    @Native(library = "sqlite3", symbol = "sqlite3_bind_text")
    static native int bindText(NativePtr stmt, int index, String value, int n, long destructor);

    @Native(library = "sqlite3", symbol = "sqlite3_bind_null")
    static native int bindNull(NativePtr stmt, int index);

    @Native(library = "sqlite3", symbol = "sqlite3_step")
    static native int step(NativePtr stmt);

    @Native(library = "sqlite3", symbol = "sqlite3_column_count")
    static native int columnCount(NativePtr stmt);

    @Native(library = "sqlite3", symbol = "sqlite3_column_name")
    static native NativePtr columnName(NativePtr stmt, int col);

    @Native(library = "sqlite3", symbol = "sqlite3_column_text")
    static native NativePtr columnText(NativePtr stmt, int col);

    @Native(library = "sqlite3", symbol = "sqlite3_column_blob")
    static native NativePtr columnBlob(NativePtr stmt, int col);

    @Native(library = "sqlite3", symbol = "sqlite3_column_bytes")
    static native int columnBytes(NativePtr stmt, int col);

    @Native(library = "sqlite3", symbol = "sqlite3_column_type")
    static native int columnType(NativePtr stmt, int col);

    @Native(library = "sqlite3", symbol = "sqlite3_finalize")
    static native int finalize(NativePtr stmt);

    @Native(library = "sqlite3", symbol = "sqlite3_errcode")
    static native int errcode(NativePtr db);

    @Native(library = "sqlite3", symbol = "sqlite3_errmsg")
    static native NativePtr errmsg(NativePtr db);

}

class SqliteConst {
    static int OK = 0;
    static int ROW = 100;
    static int DONE = 101;
    static int OPEN_READWRITE = 2;
    static int OPEN_CREATE = 4;
    static int OPEN_URI = 64;
    static int INTEGER = 1;
    static int FLOAT = 2;
    static int TEXT = 3;
    static int BLOB = 4;
    static int NULL = 5;
}

class DbSqliteBootstrap {
    static void register() {
        DriverRegistry.register("sqlite", new SQLiteDriver());
    }
}

class SQLiteDriver implements DatabaseDriver {
    public JnFuture connect(DatabaseConfig config) {
        EventLoop loop = config.loop;
        if (loop == null) {
            loop = Async.defaultLoop();
        }
        SqliteConnectSupplier sup = new SqliteConnectSupplier();
        sup.loop = loop;
        sup.path = DatabaseUrl.parse(config.url).sqlitePath;
        return Async.blockingOnLoop(loop, sup);
    }
}

class SqliteConnectSupplier implements BlockingSupplier {
    EventLoop loop;
    String path;

    public Object get() {
        DbLoopGuard.checkNotOnLoop(loop, "sqlite connect");
        NativePtr slot = NativeMemory.allocate(8L);
        int flags = SqliteConst.OPEN_READWRITE + SqliteConst.OPEN_CREATE;
        if (path.indexOf("?") >= 0 || path.startsWith("file:")) {
            flags = flags + SqliteConst.OPEN_URI;
        }
        int rc = Sqlite3Native.openV2(path, slot, flags, null);
        NativePtr db = NativeMemory.getPtr(slot, 0L);
        NativeMemory.free(slot);
        if (rc != SqliteConst.OK) {
            throw new ConnectionException("sqlite3_open_v2 failed: " + String.valueOf(rc));
        }
        if (db == NativePtr.NULL) {
            throw new ConnectionException("sqlite3_open_v2 returned null db");
        }
        SQLiteConnection inner = new SQLiteConnection();
        inner.loop = loop;
        inner.db = db;
        DbConnection conn = new DbConnection();
        conn.sqliteBackend = inner;
        return conn;
    }
}

class SQLiteConnection implements DatabaseBackend {
    EventLoop loop;
    NativePtr db;
    int closed;

    SQLiteConnection() {
        closed = 0;
    }

    public JnFuture query(String sql, ArrayList<Object> params) {
        SqliteQuerySupplier sup = new SqliteQuerySupplier();
        sup.conn = this;
        sup.sql = sql;
        sup.params = params;
        sup.oneRow = 0;
        sup.mapper = null;
        return Async.blockingOnLoop(loop, sup);
    }

    public JnFuture queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        SqliteQuerySupplier sup = new SqliteQuerySupplier();
        sup.conn = this;
        sup.sql = sql;
        sup.params = params;
        sup.oneRow = 1;
        sup.mapper = mapper;
        return Async.blockingOnLoop(loop, sup);
    }

    public JnFuture execute(String sql, ArrayList<Object> params) {
        SqliteExecuteSupplier sup = new SqliteExecuteSupplier();
        sup.conn = this;
        sup.sql = sql;
        sup.params = params;
        return Async.blockingOnLoop(loop, sup);
    }

    public JnFuture beginTransaction() {
        return execute("BEGIN", null);
    }

    public JnFuture commit() {
        return execute("COMMIT", null);
    }

    public JnFuture rollback() {
        return execute("ROLLBACK", null);
    }

    public void release() {
        if (closed != 0) {
            return;
        }
        closed = 1;
        if (db != NativePtr.NULL) {
            Sqlite3Native.close(db);
            db = NativePtr.NULL;
        }
    }

    public int isBroken() {
        return closed;
    }

    String errorMessage() {
        NativePtr msg = Sqlite3Native.errmsg(db);
        if (msg == NativePtr.NULL) {
            return "sqlite error";
        }
        return NativeMemory.fromCString(msg);
    }

    void checkRc(int rc, String op) {
        if (rc == SqliteConst.OK || rc == SqliteConst.ROW || rc == SqliteConst.DONE) {
            return;
        }
        String msg = errorMessage();
        throw new QueryException(op + " failed (" + String.valueOf(rc) + "): " + msg);
    }

    NativePtr prepareBound(String sql, ArrayList<Object> params) {
        String converted = DbParseUtil.dollarToQuestion(sql);
        NativePtr stmtSlot = NativeMemory.allocate(8L);
        int rc = Sqlite3Native.prepareV2(db, converted, -1, stmtSlot, NativePtr.NULL);
        NativePtr stmt = NativeMemory.getPtr(stmtSlot, 0L);
        NativeMemory.free(stmtSlot);
        checkRc(rc, "prepare");
        if (stmt == NativePtr.NULL) {
            throw new QueryException("prepare returned null stmt: " + errorMessage());
        }
        bindAll(stmt, params);
        return stmt;
    }

    void bindAll(NativePtr stmt, ArrayList<Object> params) {
        if (params == null) {
            return;
        }
        int i = 0;
        while (i < params.size()) {
            bindOne(stmt, i + 1, params.get(i));
            i = i + 1;
        }
    }

    void bindOne(NativePtr stmt, int index, Object value) {
        if (value == null) {
            Sqlite3Native.bindNull(stmt, index);
            return;
        }
        String text = SqliteParam.asText(value);
        // SQLITE_TRANSIENT == -1: copy value before temporary FFI CString is freed.
        int rc = Sqlite3Native.bindText(stmt, index, text, -1, -1L);
        checkRc(rc, "bind_text");
    }

    Row readRow(NativePtr stmt) {
        int cols = Sqlite3Native.columnCount(stmt);
        Row row = new Row(cols);
        row.count = cols;
        int c = 0;
        while (c < cols) {
            NativePtr namePtr = Sqlite3Native.columnName(stmt, c);
            String name = NativeMemory.fromCString(namePtr);
            int stype = Sqlite3Native.columnType(stmt, c);
            if (stype == SqliteConst.NULL) {
                row.setColumn(c, name, DbColumnType.NULL, 1);
            } else if (stype == SqliteConst.INTEGER) {
                row.setColumn(c, name, DbColumnType.INTEGER, 0);
                NativePtr textPtrInt = Sqlite3Native.columnText(stmt, c);
                row.stringValues[c] = NativeMemory.fromCString(textPtrInt);
                row.longValues[c] = DbParseUtil.parseLong(row.stringValues[c]);
            } else if (stype == SqliteConst.FLOAT) {
                row.setColumn(c, name, DbColumnType.FLOAT, 0);
                NativePtr textPtrFl = Sqlite3Native.columnText(stmt, c);
                row.stringValues[c] = NativeMemory.fromCString(textPtrFl);
                row.doubleValues[c] = DbParseUtil.parseDouble(row.stringValues[c]);
            } else if (stype == SqliteConst.BLOB) {
                row.setColumn(c, name, DbColumnType.BLOB, 0);
                NativePtr blobPtr = Sqlite3Native.columnBlob(stmt, c);
                int blen = Sqlite3Native.columnBytes(stmt, c);
                DbBlobHolder blob = new DbBlobHolder(SqliteNativeUtil.readBytes(blobPtr, blen));
                row.blobValues.set(c, blob);
                row.stringValues[c] = "";
            } else {
                row.setColumn(c, name, DbColumnType.TEXT, 0);
                NativePtr textPtr = Sqlite3Native.columnText(stmt, c);
                if (textPtr == NativePtr.NULL) {
                    row.nullFlags[c] = 1;
                } else {
                    row.stringValues[c] = NativeMemory.fromCString(textPtr);
                }
            }
            c = c + 1;
        }
        return row;
    }
}

class DbBindInt {
    int value;

    DbBindInt(int value) {
        this.value = value;
    }
}

class SqliteParam {
    static String asText(Object value) {
        if (value instanceof String) {
            String s = (String)value;
            return s;
        }
        if (value instanceof DbBindInt) {
            DbBindInt bi = (DbBindInt)value;
            return DbParseUtil.intToString(bi.value);
        }
        return "";
    }
}

class SqliteNativeUtil {
    static byte[] readBytes(NativePtr ptr, int len) {
        if (ptr == NativePtr.NULL || len <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[len];
        int i = 0;
        long off = 0L;
        while (i < len) {
            int word = NativeMemory.getInt(ptr, off);
            int b0 = DbByteUtil.byteAt(word, 0);
            int b1 = DbByteUtil.byteAt(word, 1);
            int b2 = DbByteUtil.byteAt(word, 2);
            int b3 = DbByteUtil.byteAt(word, 3);
            if (i < len) {
                out[i] = DbByteUtil.toByte(b0);
                i = i + 1;
            }
            if (i < len) {
                out[i] = DbByteUtil.toByte(b1);
                i = i + 1;
            }
            if (i < len) {
                out[i] = DbByteUtil.toByte(b2);
                i = i + 1;
            }
            if (i < len) {
                out[i] = DbByteUtil.toByte(b3);
                i = i + 1;
            }
            off = off + 4L;
        }
        return out;
    }
}

class SqliteQuerySupplier implements BlockingSupplier {
    SQLiteConnection conn;
    String sql;
    ArrayList<Object> params;
    int oneRow;
    RowMapper mapper;

    public Object get() {
        DbLoopGuard.checkNotOnLoop(conn.loop, "sqlite query");
        NativePtr stmt = conn.prepareBound(sql, params);
        DbQueryResult result = new DbQueryResult();
        int rc = Sqlite3Native.step(stmt);
        while (rc == SqliteConst.ROW) {
            Row row = conn.readRow(stmt);
            if (oneRow != 0 && mapper != null) {
                Object mapped = mapper.map(row);
                Sqlite3Native.finalize(stmt);
                return mapped;
            }
            result.rows.add(row);
            rc = Sqlite3Native.step(stmt);
        }
        conn.checkRc(rc, "step");
        Sqlite3Native.finalize(stmt);
        if (oneRow != 0) {
            return null;
        }
        return result;
    }
}

class SqliteExecuteSupplier implements BlockingSupplier {
    SQLiteConnection conn;
    String sql;
    ArrayList<Object> params;

    public Object get() {
        DbLoopGuard.checkNotOnLoop(conn.loop, "sqlite execute");
        NativePtr stmt = conn.prepareBound(sql, params);
        int rc = Sqlite3Native.step(stmt);
        conn.checkRc(rc, "execute");
        Sqlite3Native.finalize(stmt);
        DbQueryResult result = new DbQueryResult();
        result.affectedRows = 1;
        return result;
    }
}
