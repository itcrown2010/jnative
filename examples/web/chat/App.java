import jnative.web.*;
import jnative.json.*;

/**
 * JNative Chat (0.25) — Register/Login/Session + WebSocket rooms.
 * Default persistence: sqlite:./build/web/chat.db
 * Override: DATABASE_URL=postgresql://...
 */
public class App {
    public static void main(String[] args) {
        Stores.loop = new EventLoop();
        ChatDb.setup(Stores.loop);
        Stores.sessions = new InMemorySessionStore();
        Stores.sessions.idleTimeoutMs = 3600000L;
        Stores.hub = new WebSocketHub();
        Stores.loginLimiter = new RateLimiter();
        Stores.loginLimiter.capacity = 20;
        Stores.loginLimiter.refillPerSec = 5;

        WebApp app = new WebApp();
        app.bindEventLoop(Stores.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", "examples/web/chat/public");

        app.futurePost("/api/register", new RegisterHandler(Stores.loop));
        app.futurePost("/api/login", new LoginHandler(Stores.loop));
        app.futurePost("/api/logout", new LogoutHandler(Stores.loop));
        app.futureGet("/api/me", new MeHandler(Stores.loop));
        app.futureGet("/api/rooms", new RoomsHandler(Stores.loop));
        app.futureGet("/api/rooms/:id/messages", new MessagesHandler(Stores.loop));
        app.websocket("/ws/chat", new ChatWsHandler());

        System.out.println("JNative Chat listening on 18251");
        app.listenWithLoop(18251);
    }
}

class Stores {
    static EventLoop loop;
    static Database db;
    static String dialect;
    static InMemorySessionStore sessions;
    static WebSocketHub hub;
    static RateLimiter loginLimiter;
}

class ChatUser {
    int id;
    String email;
    String displayName;
    String passwordHash;

    ChatUser(int id, String email, String displayName, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }
}

class ChatUserMapper implements RowMapper {
    public Object map(Row row) {
        return new ChatUser(row.getInt(0), row.getString(1), row.getString(2), row.getString(3));
    }
}

class ChatMessage {
    int id;
    int roomId;
    int userId;
    String displayName;
    String content;

    ChatMessage(int id, int roomId, int userId, String displayName, String content) {
        this.id = id;
        this.roomId = roomId;
        this.userId = userId;
        this.displayName = displayName;
        this.content = content;
    }
}

class ChatMessageMapper implements RowMapper {
    public Object map(Row row) {
        return new ChatMessage(row.getInt(0), row.getInt(1), row.getInt(2), row.getString(3), row.getString(4));
    }
}

class ChatDb {
    static void setup(EventLoop loop) {
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "sqlite:./build/web/chat.db";
            Stores.dialect = "sqlite";
        } else {
            Stores.dialect = DatabaseUrl.parse(url).scheme;
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;
        JnFuture dbF = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbF);
        Stores.db = (Database)dbF.getValue();
        ArrayList<Object> empty = new ArrayList<Object>();
        FutureBlocker.blockingWait(Stores.db.execute(
                "CREATE TABLE IF NOT EXISTS users ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "email TEXT NOT NULL UNIQUE, "
                        + "display_name TEXT NOT NULL, "
                        + "password_hash TEXT NOT NULL, "
                        + "created_at INTEGER NOT NULL)",
                empty));
        FutureBlocker.blockingWait(Stores.db.execute(
                "CREATE TABLE IF NOT EXISTS rooms ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "name TEXT NOT NULL UNIQUE)",
                empty));
        FutureBlocker.blockingWait(Stores.db.execute(
                "CREATE TABLE IF NOT EXISTS messages ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "room_id INTEGER NOT NULL, "
                        + "user_id INTEGER NOT NULL, "
                        + "content TEXT NOT NULL, "
                        + "created_at INTEGER NOT NULL)",
                empty));
        FutureBlocker.blockingWait(Stores.db.execute(
                "INSERT OR IGNORE INTO rooms(id, name) VALUES (1, 'lobby')",
                empty));
    }
}

class SessionUtil {
    static Session load(WebContext ctx) {
        SessionManager mgr = new SessionManager();
        mgr.store = Stores.sessions;
        return mgr.loadOrCreate(ctx);
    }

    static void save(WebContext ctx, Session session) {
        SessionManager mgr = new SessionManager();
        mgr.store = Stores.sessions;
        mgr.save(ctx, session);
    }
}

class RegisterHandler implements AsyncWebHandler {
    EventLoop loop;

    RegisterHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String email = body.getString("email", "");
        String name = body.getString("displayName", "");
        String password = body.getString("password", "");
        if (email.length() == 0 || password.length() == 0) {
            ctx.status(400).jsonString("{\"error\":\"email and password required\"}");
            return JnFuture.completed(loop, null);
        }
        if (name.length() == 0) {
            name = email;
        }
        String hash = Password.hashBlocking(password);
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(email);
        params.add(name);
        params.add(hash);
        params.add(CryptoParse.longToString(WebUtil.monoMillis()));
        FutureBlocker.blockingWait(Stores.db.execute(
                "INSERT INTO users(email, display_name, password_hash, created_at) VALUES (?,?,?,?)",
                params));
        ctx.status(201).jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class LoginHandler implements AsyncWebHandler {
    EventLoop loop;

    LoginHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (Stores.loginLimiter.allow("login") == 0) {
            ctx.status(429).jsonString("{\"error\":\"too many requests\"}");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String email = body.getString("email", "");
        String password = body.getString("password", "");
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(email);
        ChatUserMapper mapper = new ChatUserMapper();
        JnFuture one = Stores.db.queryOne(
                "SELECT id, email, display_name, password_hash FROM users WHERE email = ?",
                params,
                mapper);
        FutureBlocker.blockingWait(one);
        ChatUser user = (ChatUser)one.getValue();
        if (user == null || Password.verifyBlocking(password, user.passwordHash) == 0) {
            throw new UnauthorizedException("invalid credentials");
        }
        Session session = SessionUtil.load(ctx);
        session.rotateId();
        session.setString("userId", String.valueOf(user.id));
        session.setString("email", user.email);
        session.setString("displayName", user.displayName);
        session.setString("role", "USER");
        SessionUtil.save(ctx, session);
        SecurityHeaders.applyDefaults(ctx.response);
        ctx.jsonString("{\"ok\":true,\"displayName\":\"" + user.displayName + "\"}");
        return JnFuture.completed(loop, null);
    }
}

class LogoutHandler implements AsyncWebHandler {
    EventLoop loop;

    LogoutHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        Session session = SessionUtil.load(ctx);
        session.invalidate();
        SessionUtil.save(ctx, session);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class MeHandler implements AsyncWebHandler {
    EventLoop loop;

    MeHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        Session session = SessionUtil.load(ctx);
        String userId = session.getString("userId");
        if (userId == null || userId.length() == 0) {
            throw new UnauthorizedException("login required");
        }
        ctx.jsonString("{\"userId\":\"" + userId + "\",\"email\":\""
                + session.getString("email") + "\",\"displayName\":\""
                + session.getString("displayName") + "\"}");
        return JnFuture.completed(loop, null);
    }
}

class RoomsHandler implements AsyncWebHandler {
    EventLoop loop;

    RoomsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ctx.jsonString("[{\"id\":1,\"name\":\"lobby\"}]");
        return JnFuture.completed(loop, null);
    }
}

class MessagesHandler implements AsyncWebHandler {
    EventLoop loop;

    MessagesHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String roomId = ctx.param("id");
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(roomId);
        JnFuture q = Stores.db.query(
                "SELECT m.id, m.room_id, m.user_id, u.display_name, m.content "
                        + "FROM messages m JOIN users u ON u.id = m.user_id "
                        + "WHERE m.room_id = ? ORDER BY m.id ASC LIMIT 100",
                params);
        FutureBlocker.blockingWait(q);
        DbQueryResult result = (DbQueryResult)q.getValue();
        ChatMessageMapper mapper = new ChatMessageMapper();
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < result.rows.size()) {
            ChatMessage msg = (ChatMessage)mapper.map(result.rows.get(i));
            JsonObject o = new JsonObject();
            o.putNumber("id", msg.id);
            o.putNumber("roomId", msg.roomId);
            o.putNumber("userId", msg.userId);
            o.putString("displayName", msg.displayName);
            o.putString("content", msg.content);
            arr.add(o);
            i = i + 1;
        }
        ctx.jsonValue(arr);
        return JnFuture.completed(loop, null);
    }
}

class ChatWsHandler implements WebSocketHandler {
    public void onConnect(WebSocketConnection socket) {
        Stores.hub.join("1", socket);
        socket.userData = "1";
        socket.enqueueOutbound(WebSocketFrameCodec.encodeServer(
                WebSocketOpcode.TEXT,
                Bytes.fromString("{\"type\":\"USER_JOINED\",\"roomId\":\"1\"}")));
    }

    public void onMessage(WebSocketConnection socket, WebSocketMessage message) {
        if (message.isText() == 0) {
            return;
        }
        JsonObject obj = Json.parse(message.text).asObject();
        String type = obj.getString("type", "");
        if (type.equals("CHAT_MESSAGE") == false) {
            return;
        }
        String content = obj.getString("content", "");
        if (content.length() == 0) {
            return;
        }
        String displayName = obj.getString("displayName", "anon");
        String userIdStr = obj.getString("userId", "0");
        int userId = CryptoParse.intFromString(userIdStr);
        if (userId <= 0) {
            userId = 1;
        }
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(new DbBindInt(1));
        params.add(new DbBindInt(userId));
        params.add(content);
        params.add(CryptoParse.longToString(WebUtil.monoMillis()));
        FutureBlocker.blockingWait(Stores.db.execute(
                "INSERT INTO messages(room_id, user_id, content, created_at) VALUES (?,?,?,?)",
                params));
        String wire = "{\"type\":\"CHAT_MESSAGE\",\"displayName\":\"" + displayName
                + "\",\"content\":\"" + content + "\"}";
        Stores.hub.broadcastText("1", wire);
    }
}
