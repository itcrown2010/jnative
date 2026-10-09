import jnative.web.*;
import jnative.json.*;

/**
 * JNative 0.28 Full-Stack Typed RPC demo.
 *
 * Shared contract: UserApi (+ User / CreateUserRequest codecs).
 * Compiler generates UserApi_Rpc.mount / UserApi_Rpc.client / dispatchers.
 *
 * Mode:
 *   server  — listen :18280 and mount generated RPC routes
 *   client  — call generated client against base URL
 * @author 张晓达
 */

@Rpc("/api/users")
interface UserApi {
    ArrayList<User> listUsers();

    User getUser(int id);

    @Authenticated
    User createUser(CreateUserRequest request);

    @Role("ADMIN")
    void deleteUser(int id);
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

class CreateUserRequest {
    String name;
    String email;

    CreateUserRequest(String name, String email) {
        this.name = name;
        this.email = email;
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
        JsonObject o = value.asObject();
        return new User(RpcEnvelope.jsonToInt(o.get("id")),
                o.getString("name", ""),
                o.getString("email", ""));
    }
}

class CreateUserRequestCodec implements JsonCodec<CreateUserRequest> {
    public JsonValue encode(CreateUserRequest value) {
        JsonObject o = new JsonObject();
        o.putString("name", value.name);
        o.putString("email", value.email);
        return o;
    }

    public CreateUserRequest decode(JsonValue value) {
        JsonObject o = value.asObject();
        return new CreateUserRequest(o.getString("name", ""), o.getString("email", ""));
    }
}

class UserStore {
    ArrayList<User> users;
    int nextId;

    UserStore() {
        users = new ArrayList<User>();
        nextId = 1;
        users.add(new User(nextId, "Alice", "alice@example.com"));
        nextId = nextId + 1;
        users.add(new User(nextId, "Bob", "bob@example.com"));
        nextId = nextId + 1;
    }

    ArrayList<User> list() {
        return users;
    }

    User get(int id) {
        int i = 0;
        while (i < users.size()) {
            User u = users.get(i);
            if (u.id == id) {
                return u;
            }
            i = i + 1;
        }
        throw new RpcRemoteException("USER_NOT_FOUND", "user not found: " + String.valueOf(id));
    }

    User create(CreateUserRequest req) {
        User u = new User(nextId, req.name, req.email);
        nextId = nextId + 1;
        users.add(u);
        return u;
    }

    void delete(int id) {
        int i = 0;
        while (i < users.size()) {
            if (users.get(i).id == id) {
                users.removeAt(i);
                return;
            }
            i = i + 1;
        }
        throw new RpcRemoteException("USER_NOT_FOUND", "user not found: " + String.valueOf(id));
    }
}

class UserApiImpl implements UserApi {
    UserStore store;

    UserApiImpl(UserStore store) {
        this.store = store;
    }

    public ArrayList<User> listUsers() {
        return store.list();
    }

    public User getUser(int id) {
        return store.get(id);
    }

    public User createUser(CreateUserRequest request) {
        return store.create(request);
    }

    public void deleteUser(int id) {
        store.delete(id);
    }
}

public class App {
    public static void main(String[] args) {
        String mode = "server";
        if (args != null && args.length > 0) {
            mode = args[0];
        }
        if (mode.equals("client")) {
            runClient();
        } else {
            runServer();
        }
    }

    static void runServer() {
        UserStore store = new UserStore();
        WebApp app = new WebApp();
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.get("/api/health", new HealthHandler());
        UserApi_Rpc.mount(app, new UserApiImpl(store));
        System.out.println("JNative RPC UserApi on :18280");
        app.listen(18280);
    }

    static void runClient() {
        UserApi api = UserApi_Rpc.clientAt("http://127.0.0.1:18280");
        ArrayList<User> users = api.listUsers();
        System.out.println("listUsers size=" + String.valueOf(users.size()));
        User u = api.getUser(1);
        System.out.println("getUser id=1 name=" + u.name);
        User created = api.createUser(new CreateUserRequest("Carol", "carol@example.com"));
        System.out.println("createUser id=" + String.valueOf(created.id));
        ArrayList<User> after = api.listUsers();
        System.out.println("listUsers after create size=" + String.valueOf(after.size()));
        System.out.println("RPC_CLIENT_OK");
    }
}

class HealthHandler implements WebHandler {
    public void handle(WebContext ctx) {
        JsonObject o = new JsonObject();
        o.putString("status", "ok");
        o.putString("rpc", "0.28");
        ctx.jsonValue(o);
    }
}
