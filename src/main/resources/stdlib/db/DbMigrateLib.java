// JNative DB migrations foundation (0.24) — SQL-first, no ORM.

/**
 * @author 张晓达
 */
class MigrationRecord {
    String version;
    String name;
    String checksum;
    long appliedAt;

    MigrationRecord(String version, String name, String checksum, long appliedAt) {
        this.version = version;
        this.name = name;
        this.checksum = checksum;
        this.appliedAt = appliedAt;
    }
}

class MigrationFile {
    String version;
    String name;
    String sql;
    String checksum;

    MigrationFile(String version, String name, String sql) {
        this.version = version;
        this.name = name;
        this.sql = sql;
        this.checksum = MigrationUtil.checksum(sql);
    }
}

class MigrationUtil {
    static String checksum(String sql) {
        if (sql == null) {
            return "0";
        }
        int h = sql.length();
        int i = 0;
        while (i < sql.length()) {
            h = h * 31 + sql.length() - i;
            i = i + 1;
        }
        if (h < 0) {
            h = 0 - h;
        }
        return String.valueOf(h);
    }

    static ArrayList<MigrationFile> parseDirectoryListing(ArrayList<String> names, ArrayList<String> bodies) {
        ArrayList<MigrationFile> out = new ArrayList<MigrationFile>();
        int i = 0;
        while (i < names.size()) {
            String name = names.get(i);
            String body = bodies.get(i);
            String version = name;
            int us = name.indexOf("_");
            if (us > 0) {
                version = name.substring(0, us);
            }
            out.add(new MigrationFile(version, name, body));
            i = i + 1;
        }
        return out;
    }
}

class Migrator {
    Database db;
    String tableName;

    Migrator(Database db) {
        this.db = db;
        tableName = "jnative_schema_migrations";
    }

    JnFuture ensureTable() {
        ArrayList<Object> empty = new ArrayList<Object>();
        return db.execute(
                "CREATE TABLE IF NOT EXISTS jnative_schema_migrations ("
                        + "version TEXT PRIMARY KEY, "
                        + "name TEXT NOT NULL, "
                        + "checksum TEXT NOT NULL, "
                        + "applied_at INTEGER NOT NULL)",
                empty);
    }

    JnFuture applyOne(MigrationFile file) {
        ArrayList<Object> empty = new ArrayList<Object>();
        JnFuture exec = db.execute(file.sql, empty);
        MigrationInsertStep step = new MigrationInsertStep();
        step.db = db;
        step.file = file;
        return exec.flatMap(step);
    }
}

class MigrationInsertStep implements FlatMapFunction {
    Database db;
    MigrationFile file;

    public JnFuture apply(Object ignored) {
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(file.version);
        params.add(file.name);
        params.add(file.checksum);
        params.add(DbParseUtil.longToString(AsyncClockNative.monoMillis()));
        return db.execute(
                "INSERT INTO jnative_schema_migrations(version,name,checksum,applied_at) VALUES(?,?,?,?)",
                params);
    }
}

class SchemaSnapshot {
    String generatedAt;
    ArrayList<String> tables;

    SchemaSnapshot() {
        generatedAt = "";
        tables = new ArrayList<String>();
    }

    String toJson() {
        String out = "{\"generatedAt\":\"" + generatedAt + "\",\"tables\":[";
        int i = 0;
        while (i < tables.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "\"" + tables.get(i) + "\"";
            i = i + 1;
        }
        return out + "]}";
    }
}

class QueryAnnotation {
    String sql;

    QueryAnnotation(String sql) {
        this.sql = sql;
    }
}
