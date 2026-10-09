/**
 * @author 张晓达
 */
public class App {
    static void log(String msg) {
        String prev = "";
        if (File.exists("build/db/sqlite_demo.log")) {
            prev = File.readString("build/db/sqlite_demo.log");
        }
        File.writeString("build/db/sqlite_demo.log", prev + msg + "\n");
    }

    public static void main(String[] args) {
        File.writeString("build/db/sqlite_demo.log", "start\n");
        EventLoop loop = new EventLoop();
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = "sqlite::memory:";
        cfg.loop = loop;
        cfg.maxPool = 1;

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
        User user = (User)FutureBlocker.blockingGet(
                conn.queryOne("SELECT id, name FROM users", empty, mapper));
        log("user:" + DbParseUtil.intToString(user.id) + ":" + user.name);
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
