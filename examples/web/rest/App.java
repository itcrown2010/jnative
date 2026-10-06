import jnative.web.*;
import jnative.json.*;

public class App {
    public static void main(String[] args) {
        Stores.items = new ItemStore();
        Stores.items.seedDemo();

        WebApp app = new WebApp();
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());

        app.get("/api/health", new HealthHandler());
        app.get("/api/items", new ListItemsHandler());
        app.post("/api/items", new CreateItemHandler());
        app.get("/api/items/:id", new GetItemHandler());
        app.put("/api/items/:id", new UpdateItemHandler());
        app.delete("/api/items/:id", new DeleteItemHandler());

        app.listen(18180);
    }
}

class Stores {
    static ItemStore items;
}

class Item {
    String id;
    String name;

    Item(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class ItemStore {
    ArrayList<Item> list;
    HashMap<String, Item> byId;
    int nextId;

    ItemStore() {
        list = new ArrayList<Item>();
        byId = new HashMap<String, Item>();
        nextId = 1;
    }

    void seedDemo() {
        create("alpha");
        create("beta");
    }

    Item create(String name) {
        String id = "" + nextId;
        nextId = nextId + 1;
        Item item = new Item(id, name);
        list.add(item);
        byId.put(id, item);
        return item;
    }

    Item get(String id) {
        return byId.get(id);
    }

    int remove(String id) {
        Item item = byId.get(id);
        if (item == null) {
            return 0;
        }
        byId.remove(id);
        int i = 0;
        while (i < list.size()) {
            if (list.get(i) == item) {
                list.removeAt(i);
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }
}

class ItemCodec implements JsonCodec<Item> {
    public JsonValue encode(Item value) {
        JsonObject o = new JsonObject();
        o.putString("id", value.id);
        o.putString("name", value.name);
        return o;
    }

    public Item decode(JsonValue value) {
        JsonObject o = value.asObject();
        return new Item(o.getString("id", ""), o.getString("name", ""));
    }
}

class HealthHandler implements WebHandler {
    public void handle(WebContext ctx) {
        JsonObject o = new JsonObject();
        o.putString("status", "ok");
        ctx.jsonValue(o);
    }
}

class ListItemsHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ItemCodec codec = new ItemCodec();
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < Stores.items.list.size()) {
            arr.add(codec.encode(Stores.items.list.get(i)));
            i = i + 1;
        }
        ctx.jsonValue(arr);
    }
}

class CreateItemHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ItemCodec codec = new ItemCodec();
        JsonValue body = Json.parse(ctx.bodyString());
        JsonObject o = body.asObject();
        String name = o.getString("name", "");
        if (name.length() == 0) {
            throw new BadRequestException("name required");
        }
        Item item = Stores.items.create(name);
        ctx.status(201);
        ctx.jsonValue(codec.encode(item));
    }
}

class GetItemHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ItemCodec codec = new ItemCodec();
        String id = ctx.param("id");
        Item item = Stores.items.get(id);
        if (item == null) {
            throw new NotFoundException("item not found");
        }
        ctx.jsonValue(codec.encode(item));
    }
}

class UpdateItemHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ItemCodec codec = new ItemCodec();
        String id = ctx.param("id");
        Item item = Stores.items.get(id);
        if (item == null) {
            throw new NotFoundException("item not found");
        }
        JsonValue body = Json.parse(ctx.bodyString());
        JsonObject o = body.asObject();
        String name = o.getString("name", "");
        if (name.length() == 0) {
            throw new BadRequestException("name required");
        }
        item.name = name;
        ctx.jsonValue(codec.encode(item));
    }
}

class DeleteItemHandler implements WebHandler {
    public void handle(WebContext ctx) {
        String id = ctx.param("id");
        int ok = Stores.items.remove(id);
        if (ok == 0) {
            throw new NotFoundException("item not found");
        }
        ctx.status(204);
        ctx.text("");
    }
}
