import jnative.web.*;
import jnative.json.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        Stores.todos = new TodoStore();

        WebApp app = new WebApp();
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", "examples/web/todo/public");

        app.get("/api/todos", new ListTodosHandler());
        app.post("/api/todos", new CreateTodoHandler());
        app.put("/api/todos/:id", new UpdateTodoHandler());
        app.delete("/api/todos/:id", new DeleteTodoHandler());

        app.listen(18182);
    }
}

class Stores {
    static TodoStore todos;
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

class ListTodosHandler implements WebHandler {
    public void handle(WebContext ctx) {
        TodoCodec codec = new TodoCodec();
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < Stores.todos.list.size()) {
            arr.add(codec.encode(Stores.todos.list.get(i)));
            i = i + 1;
        }
        ctx.jsonValue(arr);
    }
}

class CreateTodoHandler implements WebHandler {
    public void handle(WebContext ctx) {
        TodoCodec codec = new TodoCodec();
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String title = body.getString("title", "");
        if (title.length() == 0) {
            throw new BadRequestException("title required");
        }
        Todo todo = Stores.todos.create(title);
        ctx.status(201);
        ctx.jsonValue(codec.encode(todo));
    }
}

class UpdateTodoHandler implements WebHandler {
    public void handle(WebContext ctx) {
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
    }
}

class DeleteTodoHandler implements WebHandler {
    public void handle(WebContext ctx) {
        String id = ctx.param("id");
        int ok = Stores.todos.remove(id);
        if (ok == 0) {
            throw new NotFoundException("todo not found");
        }
        ctx.status(204);
        ctx.text("");
    }
}
