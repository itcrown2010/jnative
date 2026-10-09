import jnative.web.*;
import jnative.json.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Stores.todos = new TodoStore();
        Stores.loop = new EventLoop();

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

        app.listenWithLoop(18183);
    }
}

class Stores {
    static TodoStore todos;
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

class TodoStore {
    ArrayList<Todo> list;
    HashMap<String, Todo> byId;
    int nextId;

    TodoStore() {
        list = new ArrayList<Todo>();
        byId = new HashMap<String, Todo>();
        nextId = 1;
    }

    Todo create(String title) {
        String id = "" + nextId;
        nextId = nextId + 1;
        Todo todo = new Todo(id, title, 0);
        list.add(todo);
        byId.put(id, todo);
        return todo;
    }

    Todo get(String id) {
        return byId.get(id);
    }

    int remove(String id) {
        Todo todo = byId.get(id);
        if (todo == null) {
            return 0;
        }
        byId.remove(id);
        int i = 0;
        while (i < list.size()) {
            if (list.get(i) == todo) {
                list.removeAt(i);
                return 1;
            }
            i = i + 1;
        }
        return 0;
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

class DbSim {
    static void wait(EventLoop loop) {
        FutureBlocker.blockingWait(Async.sleepOnLoop(loop, 10));
    }
}

class HealthHandler implements AsyncWebHandler {
    EventLoop loop;

    HealthHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ctx.jsonString("{\"status\":\"ok\"}");
        return JnFuture.completed(loop, null);
    }
}

class ListTodosHandler implements AsyncWebHandler {
    EventLoop loop;

    ListTodosHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        DbSim.wait(loop);
        TodoCodec codec = new TodoCodec();
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < Stores.todos.list.size()) {
            arr.add(codec.encode(Stores.todos.list.get(i)));
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
        DbSim.wait(loop);
        TodoCodec codec = new TodoCodec();
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String title = body.getString("title", "");
        if (title.length() == 0) {
            throw new BadRequestException("title required");
        }
        Todo todo = Stores.todos.create(title);
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
        DbSim.wait(loop);
        TodoCodec codec = new TodoCodec();
        String id = ctx.param("id");
        Todo todo = Stores.todos.get(id);
        if (todo == null) {
            throw new NotFoundException("todo not found");
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String title = body.getString("title", "");
        if (title.length() > 0) {
            todo.title = title;
        }
        JsonValue doneVal = body.get("done");
        if (doneVal.isNull() == false && doneVal.kind == JsonValue.BOOL_KIND) {
            if (doneVal.asBoolean()) {
                todo.done = 1;
            } else {
                todo.done = 0;
            }
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
        DbSim.wait(loop);
        String id = ctx.param("id");
        int ok = Stores.todos.remove(id);
        if (ok == 0) {
            throw new NotFoundException("todo not found");
        }
        ctx.status(204);
        ctx.text("");
        return JnFuture.completed(loop, null);
    }
}
