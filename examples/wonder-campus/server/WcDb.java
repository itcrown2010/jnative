/**
 * Wonder Campus DB bootstrap (SQLite only for v1).
 * @author 张晓达
 */
class Wc {
    static EventLoop loop;
    static Database db;
    static String dialect;
    static int schemaReady = 0;
    static String schemaError = "";
    static InMemorySessionStore sessions;
}

class WcDb {
    static void setup(EventLoop loop) {
        Wc.loop = loop;
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "sqlite:./build/wonder-campus/wonder.db";
        }
        Wc.dialect = DatabaseUrl.parse(url).scheme;
        if (Wc.dialect.equals("sqlite") == false) {
            throw new ConnectionException("wonder-campus v1 requires sqlite DATABASE_URL");
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;
        JnFuture dbF = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbF);
        if (dbF.getError() != null) {
            throw new ConnectionException("db connect failed");
        }
        Wc.db = (Database) dbF.getValue();
        exec("PRAGMA foreign_keys=ON", new ArrayList<Object>());
        exec("PRAGMA journal_mode=WAL", new ArrayList<Object>());
        migrate();
        Wc.schemaReady = 1;
    }

    static void migrate() {
        applyMigrationFile("001", "examples/wonder-campus/migrations/001_init_sqlite.sql");
        applyMigrationFile("002", "examples/wonder-campus/migrations/002_mobile_loop_sqlite.sql");
        applyMigrationFile("003", "examples/wonder-campus/migrations/003_commercial_sqlite.sql");
        tryAlter("ALTER TABLE wc_contracts ADD COLUMN billing_entity_id INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_payments ADD COLUMN billing_entity_id INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_lesson_sessions ADD COLUMN completed_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_feedback ADD COLUMN status TEXT NOT NULL DEFAULT 'PUBLISHED'");
        tryAlter("ALTER TABLE wc_feedback ADD COLUMN explored TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_feedback ADD COLUMN attempt TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_feedback ADD COLUMN encouragement TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_feedback ADD COLUMN published_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_leave_requests ADD COLUMN cancelled_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_attendance ADD COLUMN note TEXT NOT NULL DEFAULT ''");
        /* 003 commercial columns (idempotent: duplicate-column errors ignored) */
        tryAlter("ALTER TABLE wc_users ADD COLUMN phone TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_users ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
        tryAlter("ALTER TABLE wc_users ADD COLUMN last_login_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_users ADD COLUMN fail_count INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_users ADD COLUMN locked_until INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE wc_students ADD COLUMN gender TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_students ADD COLUMN birthday TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_students ADD COLUMN school TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_students ADD COLUMN note TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_students ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
        tryAlter("ALTER TABLE wc_courses ADD COLUMN description TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_courses ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
        tryAlter("ALTER TABLE wc_classes ADD COLUMN capacity INTEGER NOT NULL DEFAULT 8");
        tryAlter("ALTER TABLE wc_classes ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
        tryAlter("ALTER TABLE wc_rooms ADD COLUMN capacity INTEGER NOT NULL DEFAULT 8");
        tryAlter("ALTER TABLE wc_campuses ADD COLUMN address TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE wc_campuses ADD COLUMN phone TEXT NOT NULL DEFAULT ''");
    }

    static void applyMigrationFile(String version, String path) {
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(version);
        Object existing = null;
        try {
            existing = queryOne("SELECT version FROM schema_migrations WHERE version=?", chk,
                    new StringMapper());
        } catch (Exception ex) {
            existing = null;
        }
        String sql = File.readString(path);
        if (sql == null || sql.length() == 0) {
            Wc.schemaError = "missing migration " + path;
            throw new ConnectionException(Wc.schemaError);
        }
        if (existing == null) {
            runScript(sql);
            long now = AsyncClockNative.wallMillis();
            exec("INSERT INTO schema_migrations(version, applied_at) VALUES ('" + version + "',"
                    + CryptoParse.longToString(now) + ")", new ArrayList<Object>());
        } else {
            /* re-apply IF NOT EXISTS DDL for upgraded DBs that recorded version early */
            runScript(sql);
        }
    }

    static void tryAlter(String sql) {
        try {
            exec(sql, new ArrayList<Object>());
        } catch (Exception ignored) {
        }
    }

    /** Split on ';' and strip -- line comments (same approach as ErpDb.runScript). */
    static void runScript(String sql) {
        ArrayList<Object> empty = new ArrayList<Object>();
        int start = 0;
        int i = 0;
        int lineStart = 1;
        while (i < sql.length()) {
            int c = sql.charAt(i);
            if (lineStart != 0 && c == 45 && i + 1 < sql.length() && sql.charAt(i + 1) == 45) {
                while (i < sql.length() && sql.charAt(i) != 10) {
                    i = i + 1;
                }
                continue;
            }
            if (c == 59) {
                String stmt = trim(sql.substring(start, i));
                if (stmt.length() > 0) {
                    exec(stmt, empty);
                }
                start = i + 1;
                lineStart = 1;
            } else {
                if (c == 10) {
                    lineStart = 1;
                } else {
                    if (c == 13) {
                        /* skip CR */
                    } else {
                        if (c == 32 || c == 9) {
                            /* whitespace */
                        } else {
                            lineStart = 0;
                        }
                    }
                }
            }
            i = i + 1;
        }
        String last = trim(sql.substring(start, sql.length()));
        if (last.length() > 0) {
            exec(last, empty);
        }
    }

    static String trim(String s) {
        if (s == null) {
            return "";
        }
        int a = 0;
        int b = s.length();
        while (a < b) {
            int c = s.charAt(a);
            if (c == 32 || c == 10 || c == 13 || c == 9) {
                a = a + 1;
            } else {
                break;
            }
        }
        while (b > a) {
            int c = s.charAt(b - 1);
            if (c == 32 || c == 10 || c == 13 || c == 9) {
                b = b - 1;
            } else {
                break;
            }
        }
        if (a >= b) {
            return "";
        }
        return s.substring(a, b);
    }

    static void exec(String sql, ArrayList<Object> params) {
        JnFuture f = Wc.db.execute(sql, params);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
    }

    static Object queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        JnFuture f = Wc.db.query(sql, params);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
        DbQueryResult r = (DbQueryResult) f.getValue();
        if (r == null || r.rows.size() == 0) {
            return null;
        }
        return mapper.map(r.rows.get(0));
    }

    static DbQueryResult query(String sql, ArrayList<Object> params) {
        JnFuture f = Wc.db.query(sql, params);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
        return (DbQueryResult) f.getValue();
    }

    static long insertReturningId(String sql, ArrayList<Object> params) {
        exec(sql, params);
        Object id = queryOne("SELECT last_insert_rowid()", new ArrayList<Object>(), new LongMapper());
        return WcNum.asLong(id);
    }

    static Object runInTxn(DbTransactionWork work) {
        JnFuture f = Wc.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
        return f.getValue();
    }

    static void execConn(DbConnection conn, String sql, ArrayList<Object> params) {
        JnFuture f = conn.execute(sql, params);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
    }

    static Object queryOneConn(DbConnection conn, String sql, ArrayList<Object> params, RowMapper mapper) {
        JnFuture f = conn.query(sql, params);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
        DbQueryResult r = (DbQueryResult) f.getValue();
        if (r == null || r.rows.size() == 0) {
            return null;
        }
        return mapper.map(r.rows.get(0));
    }

    static DbQueryResult queryConn(DbConnection conn, String sql, ArrayList<Object> params) {
        JnFuture f = conn.query(sql, params);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw f.getError();
        }
        return (DbQueryResult) f.getValue();
    }

    static long insertReturningIdConn(DbConnection conn, String sql, ArrayList<Object> params) {
        execConn(conn, sql, params);
        Object id = queryOneConn(conn, "SELECT last_insert_rowid()", new ArrayList<Object>(), new LongMapper());
        return WcNum.asLong(id);
    }
}

class WcNum {
    static long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0L;
        }
        return WebUtil.parseLongOr(s, 0L);
    }

    static long asLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof String) {
            return parseLong((String) v);
        }
        return 0L;
    }

    static int parseInt(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        return WebUtil.parseIntOr(s, 0);
    }
}

class LongMapper implements RowMapper {
    public Object map(Row row) {
        return CryptoParse.longToString(row.getLong(0));
    }
}

class StringMapper implements RowMapper {
    public Object map(Row row) {
        return row.getString(0);
    }
}
