public class App {
    static void log(String msg) {
        String prev = "";
        if (File.exists("build/db/sqlite_step.log")) {
            prev = File.readString("build/db/sqlite_step.log");
        }
        File.writeString("build/db/sqlite_step.log", prev + msg + "\n");
    }

    public static void main(String[] args) {
        File.writeString("build/db/sqlite_step.log", "step1\n");
        EventLoop loop = new EventLoop();
        log("step2");
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = "sqlite::memory:";
        cfg.loop = loop;
        cfg.maxPool = 1;
        DbSqliteBootstrap.register();
        log("step3");
        Database db = (Database)FutureBlocker.blockingGet(Database.connectWithConfig(cfg));
        log("connected");

        DbConnection conn = (DbConnection)FutureBlocker.blockingGet(db.acquire());
        log("acquired");

        ArrayList<Object> empty = new ArrayList<Object>();
        FutureBlocker.blockingGet(conn.execute(
                "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)",
                empty));
        log("created");

        ArrayList<Object> insertParams = new ArrayList<Object>();
        insertParams.add("Alice");
        FutureBlocker.blockingGet(conn.execute("INSERT INTO users (name) VALUES (?)", insertParams));
        log("inserted");

        UserMapper mapper = new UserMapper();
        Object userObj = FutureBlocker.blockingGet(
                conn.queryOne("SELECT id, name FROM users", empty, mapper));
        if (userObj == null) {
            log("user-null");
        } else {
            User user = (User)userObj;
            log("user:" + String.valueOf(user.id) + ":" + user.name);
        }
        db.pool.release(conn);
        log("done");
    }
}

class User {
    int id;
    String name;

    User(int id, String name) {
        this.id = id;
        this.name = name;
    }
}

class UserMapper implements RowMapper {
    public Object map(Row row) {
        return new User(row.getInt(0), row.getString(1));
    }
}
