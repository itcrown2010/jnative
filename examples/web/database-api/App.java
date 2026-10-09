import jnative.web.*;
import jnative.json.*;

/**
 * JNative 0.24 database-api demo.
 * Default: sqlite:./build/web/database-api.db (deterministic local).
 * Override: DATABASE_URL=postgresql://user:pass@localhost:5432/jnative
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Stores.loop = new EventLoop();
        DbInit.setup(Stores.loop);

        WebApp app = new WebApp();
        app.bindEventLoop(Stores.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());

        EventLoop loop = Stores.loop;
        app.futureGet("/api/health", new HealthHandler(loop));
        app.futureGet("/users", new ListUsersHandler(loop));
        app.futureGet("/users/:id", new GetUserHandler(loop));
        app.futurePost("/users", new CreateUserHandler(loop));
        app.futurePut("/users/:id", new UpdateUserHandler(loop));
        app.futureDelete("/users/:id", new DeleteUserHandler(loop));

        System.out.println("database-api listening on 18185");
        app.listenWithLoop(18185);
    }
}

class Stores {
    static Database db;
    static EventLoop loop;
    static String dialect;
}

class User {
    int id;
    String name;
    String email;

    User(int id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }
}

class UserMapper implements RowMapper {
    public Object map(Row row) {
        return new User(row.getInt(0), row.getString(1), row.getString(2));
    }
}

class UserCodec implements JsonCodec<User> {
    public JsonValue encode(User value) {
        JsonObject o = new JsonObject();
        o.putNumber("id", value.id);
        o.putString("name", value.name);
        o.putString("email", value.email);
        return o;
    }

    public User decode(JsonValue value) {
        JsonObject obj = value.asObject();
        return new User(
                0,
                obj.getString("name", ""),
                obj.getString("email", ""));
    }
}

class DbInit {
    static void setup(EventLoop loop) {
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "sqlite:./build/web/database-api.db";
            Stores.dialect = "sqlite";
        } else {
            DatabaseUrl parsed = DatabaseUrl.parse(url);
            Stores.dialect = parsed.scheme;
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;
        cfg.maxPool = 4;
        JnFuture dbFuture = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbFuture);
        Stores.db = (Database)dbFuture.getValue();

        ArrayList<Object> empty = new ArrayList<Object>();
        if (Stores.dialect.equals("sqlite")) {
            FutureBlocker.blockingWait(Stores.db.execute(
                    "CREATE TABLE IF NOT EXISTS users ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "name TEXT NOT NULL, "
                            + "email TEXT NOT NULL)",
                    empty));
        } else {
            FutureBlocker.blockingWait(Stores.db.execute(
                    "CREATE TABLE IF NOT EXISTS users ("
                            + "id SERIAL PRIMARY KEY, "
                            + "name TEXT NOT NULL, "
                            + "email TEXT NOT NULL)",
                    empty));
        }
    }
}

class HealthHandler implements AsyncWebHandler {
    EventLoop loop;

    HealthHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ctx.jsonString("{\"status\":\"ok\",\"db\":\"" + Stores.dialect + "\"}");
        return JnFuture.completed(loop, null);
    }
}

class ListUsersHandler implements AsyncWebHandler {
    EventLoop loop;

    ListUsersHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ArrayList<Object> empty = new ArrayList<Object>();
        JnFuture q = Stores.db.query("SELECT id, name, email FROM users ORDER BY id", empty);
        FutureBlocker.blockingWait(q);
        DbQueryResult result = (DbQueryResult)q.getValue();
        UserMapper mapper = new UserMapper();
        UserCodec codec = new UserCodec();
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < result.rows.size()) {
            User user = (User)mapper.map(result.rows.get(i));
            arr.add(codec.encode(user));
            i = i + 1;
        }
        ctx.jsonValue(arr);
        return JnFuture.completed(loop, null);
    }
}

class GetUserHandler implements AsyncWebHandler {
    EventLoop loop;

    GetUserHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        int id = ctx.paramInt("id");
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(new DbBindInt(id));
        UserMapper mapper = new UserMapper();
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        if (Stores.dialect.equals("postgresql")) {
            sql = "SELECT id, name, email FROM users WHERE id = $1";
        }
        JnFuture found = Stores.db.queryOne(sql, params, mapper);
        FutureBlocker.blockingWait(found);
        User user = (User)found.getValue();
        if (user == null) {
            ctx.status(404);
            ctx.jsonString("{\"error\":\"User not found\"}");
            return JnFuture.completed(loop, null);
        }
        UserCodec codec = new UserCodec();
        ctx.jsonValue(codec.encode(user));
        return JnFuture.completed(loop, null);
    }
}

class CreateUserHandler implements AsyncWebHandler {
    EventLoop loop;

    CreateUserHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String name = body.getString("name", "");
        String email = body.getString("email", "");
        if (name.length() == 0 || email.length() == 0) {
            ctx.status(400);
            ctx.jsonString("{\"error\":\"name and email required\"}");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(name);
        params.add(email);
        UserMapper mapper = new UserMapper();
        Object created;
        if (Stores.dialect.equals("postgresql")) {
            JnFuture inserted = Stores.db.queryOne(
                    "INSERT INTO users(name,email) VALUES($1,$2) RETURNING id,name,email",
                    params,
                    mapper);
            FutureBlocker.blockingWait(inserted);
            created = inserted.getValue();
        } else {
            FutureBlocker.blockingWait(Stores.db.execute(
                    "INSERT INTO users(name,email) VALUES(?,?)",
                    params));
            ArrayList<Object> q = new ArrayList<Object>();
            q.add(email);
            JnFuture selected = Stores.db.queryOne(
                    "SELECT id, name, email FROM users WHERE email = ? ORDER BY id DESC LIMIT 1",
                    q,
                    mapper);
            FutureBlocker.blockingWait(selected);
            created = selected.getValue();
        }
        UserCodec codec = new UserCodec();
        ctx.status(201);
        ctx.jsonValue(codec.encode((User)created));
        return JnFuture.completed(loop, null);
    }
}

class UpdateUserHandler implements AsyncWebHandler {
    EventLoop loop;

    UpdateUserHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        int id = ctx.paramInt("id");
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String name = body.getString("name", "");
        String email = body.getString("email", "");
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(name);
        params.add(email);
        params.add(new DbBindInt(id));
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        if (Stores.dialect.equals("postgresql")) {
            sql = "UPDATE users SET name = $1, email = $2 WHERE id = $3";
        }
        FutureBlocker.blockingWait(Stores.db.execute(sql, params));
        ArrayList<Object> q = new ArrayList<Object>();
        q.add(new DbBindInt(id));
        UserMapper mapper = new UserMapper();
        String sel = "SELECT id, name, email FROM users WHERE id = ?";
        if (Stores.dialect.equals("postgresql")) {
            sel = "SELECT id, name, email FROM users WHERE id = $1";
        }
        JnFuture selected = Stores.db.queryOne(sel, q, mapper);
        FutureBlocker.blockingWait(selected);
        User user = (User)selected.getValue();
        if (user == null) {
            ctx.status(404);
            ctx.jsonString("{\"error\":\"User not found\"}");
            return JnFuture.completed(loop, null);
        }
        UserCodec codec = new UserCodec();
        ctx.jsonValue(codec.encode(user));
        return JnFuture.completed(loop, null);
    }
}

class DeleteUserHandler implements AsyncWebHandler {
    EventLoop loop;

    DeleteUserHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        int id = ctx.paramInt("id");
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(new DbBindInt(id));
        String sql = "DELETE FROM users WHERE id = ?";
        if (Stores.dialect.equals("postgresql")) {
            sql = "DELETE FROM users WHERE id = $1";
        }
        FutureBlocker.blockingWait(Stores.db.execute(sql, params));
        ctx.status(204);
        ctx.text("");
        return JnFuture.completed(loop, null);
    }
}
