import jnative.web.*;
import jnative.json.*;

public class App {
    public static void main(String[] args) {
        Stores.loop = new EventLoop();
        DbInit.setup(Stores.loop);

        WebApp app = new WebApp();
        app.bindEventLoop(Stores.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", "examples/web/todo/public");

        EventLoop loop = Stores.loop;
        app.futureGet("/api/health", new HealthHandler(loop));
        app.futureGet("/api/todos", new ListTodosHandler(loop));
        app.futurePost("/api/todos", new CreateTodoHandler(loop));
        app.futurePut("/api/todos/:id", new UpdateTodoHandler(loop));
        app.futureDelete("/api/todos/:id", new DeleteTodoHandler(loop));

        app.listenWithLoop(18184);
    }
}

class Stores {
    static Database db;
    static EventLoop loop;
}

class Todo {
    String id;
    String title;
    int done;

    Todo(String id, String title, int done) {
        this.id = id;
        this.title = title;
        this.done = done;
    }
}

class DbInit {
    static void setup(EventLoop loop) {
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = "sqlite:todo.db";
        cfg.loop = loop;
        JnFuture dbFuture = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbFuture);
        Stores.db = (Database)dbFuture.getValue();
        ArrayList<Object> empty = new ArrayList<Object>();
        FutureBlocker.blockingWait(Stores.db.execute(
                "CREATE TABLE IF NOT EXISTS todos ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "title TEXT NOT NULL, "
                        + "done INTEGER NOT NULL DEFAULT 0)",
                empty));
    }
}

class TodoCodec implements JsonCodec<Todo> {
    public JsonValue encode(Todo value) {
        JsonObject o = new JsonObject();
        o.putString("id", value.id);
        o.putString("title", value.title);
        if (value.done != 0) {
            o.put("done", new JsonBoolean(1));
        } else {
            o.put("done", new JsonBoolean(0));
        }
        return o;
    }

    public Todo decode(JsonValue value) {
        JsonObject obj = value.asObject();
        int done = 0;
        JsonValue d = obj.get("done");
        if (d.isNull() == false && d.kind == JsonValue.BOOL_KIND) {
            if (d.asBoolean()) {
                done = 1;
            }
        }
        return new Todo(obj.getString("id", ""), obj.getString("title", ""), done);
    }
}

class TodoRowMapper implements RowMapper {
    public Object map(Row row) {
        String id = String.valueOf(row.getInt(0));
        String title = row.getString(1);
        int done = row.getInt(2);
        return new Todo(id, title, done);
    }
}

class HealthHandler implements AsyncWebHandler {
    EventLoop loop;

    HealthHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ctx.jsonString("{\"status\":\"ok\",\"db\":\"sqlite\"}");
        return JnFuture.completed(loop, null);
    }
}

class ListTodosHandler implements AsyncWebHandler {
    EventLoop loop;

    ListTodosHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ArrayList<Object> empty = new ArrayList<Object>();
        JnFuture q = Stores.db.query("SELECT id, title, done FROM todos ORDER BY id", empty);
        FutureBlocker.blockingWait(q);
        DbQueryResult result = (DbQueryResult)q.getValue();
        TodoCodec codec = new TodoCodec();
        TodoRowMapper mapper = new TodoRowMapper();
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < result.rows.size()) {
            Row row = (Row)result.rows.get(i);
            Todo todo = (Todo)mapper.map(row);
            arr.add(codec.encode(todo));
            i = i + 1;
        }
        ctx.jsonValue(arr);
        return JnFuture.completed(loop, null);
    }
}

class CreateTodoHandler implements AsyncWebHandler {
    EventLoop loop;

    CreateTodoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        TodoCodec codec = new TodoCodec();
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String title = body.getString("title", "");
        if (title.length() == 0) {
            throw new BadRequestException("title required");
        }
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(title);
        JnFuture ins = Stores.db.execute("INSERT INTO todos (title, done) VALUES (?, 0)", params);
        FutureBlocker.blockingWait(ins);
        TodoRowMapper mapper = new TodoRowMapper();
        ArrayList<Object> qParams = new ArrayList<Object>();
        qParams.add(title);
        JnFuture one = Stores.db.queryOne(
                "SELECT id, title, done FROM todos WHERE title = ? ORDER BY id DESC LIMIT 1",
                qParams,
                mapper);
        FutureBlocker.blockingWait(one);
        Todo todo = (Todo)one.getValue();
        ctx.status(201);
        ctx.jsonValue(codec.encode(todo));
        return JnFuture.completed(loop, null);
    }
}

class UpdateTodoHandler implements AsyncWebHandler {
    EventLoop loop;

    UpdateTodoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        TodoCodec codec = new TodoCodec();
        String id = ctx.param("id");
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String title = body.getString("title", "");
        JsonValue doneVal = body.get("done");
        int done = -1;
        if (doneVal.isNull() == false && doneVal.kind == JsonValue.BOOL_KIND) {
            if (doneVal.asBoolean()) {
                done = 1;
            } else {
                done = 0;
            }
        }
        if (title.length() > 0 && done >= 0) {
            ArrayList<Object> paramsBoth = new ArrayList<Object>();
            paramsBoth.add(title);
            paramsBoth.add(new DbBindInt(done));
            paramsBoth.add(id);
            JnFuture updateBoth = Stores.db.execute(
                    "UPDATE todos SET title = ?, done = ? WHERE id = ?",
                    paramsBoth);
            FutureBlocker.blockingWait(updateBoth);
        } else if (title.length() > 0) {
            ArrayList<Object> paramsTitle = new ArrayList<Object>();
            paramsTitle.add(title);
            paramsTitle.add(id);
            JnFuture updateTitle = Stores.db.execute("UPDATE todos SET title = ? WHERE id = ?", paramsTitle);
            FutureBlocker.blockingWait(updateTitle);
        } else if (done >= 0) {
            ArrayList<Object> paramsDone = new ArrayList<Object>();
            paramsDone.add(new DbBindInt(done));
            paramsDone.add(id);
            JnFuture updateDone = Stores.db.execute("UPDATE todos SET done = ? WHERE id = ?", paramsDone);
            FutureBlocker.blockingWait(updateDone);
        }
        TodoRowMapper mapper = new TodoRowMapper();
        ArrayList<Object> qParams = new ArrayList<Object>();
        qParams.add(id);
        JnFuture one = Stores.db.queryOne(
                "SELECT id, title, done FROM todos WHERE id = ?",
                qParams,
                mapper);
        FutureBlocker.blockingWait(one);
        Todo todo = (Todo)one.getValue();
        if (todo == null) {
            throw new NotFoundException("todo not found");
        }
        ctx.jsonValue(codec.encode(todo));
        return JnFuture.completed(loop, null);
    }
}

class DeleteTodoHandler implements AsyncWebHandler {
    EventLoop loop;

    DeleteTodoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String id = ctx.param("id");
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(id);
        TodoRowMapper mapper = new TodoRowMapper();
        JnFuture found = Stores.db.queryOne("SELECT id, title, done FROM todos WHERE id = ?", params, mapper);
        FutureBlocker.blockingWait(found);
        Todo existing = (Todo)found.getValue();
        if (existing == null) {
            throw new NotFoundException("todo not found");
        }
        JnFuture d = Stores.db.execute("DELETE FROM todos WHERE id = ?", params);
        FutureBlocker.blockingWait(d);
        ctx.status(204);
        ctx.text("");
        return JnFuture.completed(loop, null);
    }
}
