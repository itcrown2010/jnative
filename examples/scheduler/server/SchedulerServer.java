import jnative.web.*;
import jnative.json.*;

/**
 * JNative Scheduler — native server (no JVM).
 *
 * Default DB: sqlite:./build/scheduler/scheduler.db
 * Override:   DATABASE_URL=postgresql://...
 *
 * REST mirrors shared @Rpc contracts for browser fetch.
 */
public class SchedulerServer {
    public static void main(String[] args) {
        Sch.loop = new EventLoop();
        SchDb.setup(Sch.loop);
        Sch.sessions = new InMemorySessionStore();
        Sch.sessions.idleTimeoutMs = 86400000L;
        Sch.loginLimiter = new RateLimiter();
        Sch.loginLimiter.capacity = 30;
        Sch.loginLimiter.refillPerSec = 5;
        Sch.hubSeq = 0;
        Sch.hubPayload = "";
        SchSeed.ensureDemo();

        WebApp app = new WebApp();
        app.bindEventLoop(Sch.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", "examples/scheduler/public");

        app.get("/health", new HealthHandler());
        app.get("/ready", new ReadyHandler());

        app.futurePost("/api/auth/login", new LoginHandler(Sch.loop));
        app.futurePost("/api/auth/logout", new LogoutHandler(Sch.loop));
        app.futureGet("/api/auth/currentUser", new MeHandler(Sch.loop));

        app.futureGet("/api/calendar/listCalendars", new ListCalendarsHandler(Sch.loop));
        app.futureGet("/api/calendar/eventsBetween", new EventsBetweenHandler(Sch.loop));
        app.futurePost("/api/calendar/createEvent", new CreateEventHandler(Sch.loop));
        app.futurePost("/api/calendar/updateEvent", new UpdateEventHandler(Sch.loop));
        app.futurePost("/api/calendar/deleteEvent", new DeleteEventHandler(Sch.loop));

        app.futureGet("/api/tasks/listTasks", new ListTasksHandler(Sch.loop));
        app.futurePost("/api/tasks/createTask", new CreateTaskHandler(Sch.loop));
        app.futurePost("/api/tasks/updateTask", new UpdateTaskHandler(Sch.loop));
        app.futurePost("/api/tasks/deleteTask", new DeleteTaskHandler(Sch.loop));

        app.futureGet("/api/search/search", new SearchHandler(Sch.loop));
        app.get("/api/stream", new StreamHandler());

        ReminderScheduler.start(Sch.loop);

        System.out.println("JNative Scheduler on http://127.0.0.1:18400");
        System.out.println("Demo: demo@jnative.dev / demo1234");
        app.listenWithLoop(18400);
    }
}

class Sch {
    static EventLoop loop;
    static Database db;
    static String dialect;
    static InMemorySessionStore sessions;
    static RateLimiter loginLimiter;
    static long hubSeq;
    static String hubPayload;
    static long requestSeq;
}

class SchDb {
    static void setup(EventLoop loop) {
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "sqlite:./build/scheduler/scheduler.db";
            Sch.dialect = "sqlite";
        } else {
            Sch.dialect = DatabaseUrl.parse(url).scheme;
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;
        JnFuture dbF = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbF);
        Sch.db = (Database) dbF.getValue();
        ArrayList<Object> empty = new ArrayList<Object>();
        exec("CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "email TEXT NOT NULL UNIQUE,"
                + "display_name TEXT NOT NULL,"
                + "password_hash TEXT NOT NULL,"
                + "timezone TEXT NOT NULL DEFAULT 'Asia/Shanghai',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "token_hash TEXT NOT NULL UNIQUE,"
                + "csrf_token TEXT NOT NULL,"
                + "expires_at INTEGER NOT NULL,"
                + "created_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS calendars ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "owner_id INTEGER NOT NULL,"
                + "name TEXT NOT NULL,"
                + "description TEXT NOT NULL DEFAULT '',"
                + "color TEXT NOT NULL DEFAULT '#3B82F6',"
                + "is_default INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "calendar_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "description TEXT NOT NULL DEFAULT '',"
                + "location TEXT NOT NULL DEFAULT '',"
                + "start_time INTEGER NOT NULL,"
                + "end_time INTEGER NOT NULL,"
                + "all_day INTEGER NOT NULL DEFAULT 0,"
                + "timezone TEXT NOT NULL DEFAULT 'UTC',"
                + "recurrence_rule TEXT NOT NULL DEFAULT '',"
                + "status TEXT NOT NULL DEFAULT 'CONFIRMED',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)", empty);
        exec("CREATE TABLE IF NOT EXISTS reminders ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "event_id INTEGER NOT NULL,"
                + "type TEXT NOT NULL DEFAULT 'IN_APP',"
                + "minutes_before INTEGER NOT NULL DEFAULT 10)", empty);
        exec("CREATE TABLE IF NOT EXISTS tasks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "description TEXT NOT NULL DEFAULT '',"
                + "due_at INTEGER,"
                + "priority TEXT NOT NULL DEFAULT 'NORMAL',"
                + "status TEXT NOT NULL DEFAULT 'OPEN',"
                + "created_at INTEGER NOT NULL,"
                + "completed_at INTEGER)", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_events_calendar_start ON events(calendar_id, start_time)", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_events_range ON events(start_time, end_time)", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_tasks_user_due ON tasks(user_id, due_at)", empty);
        exec("CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token_hash)", empty);
    }

    static void exec(String sql, ArrayList<Object> params) {
        FutureBlocker.blockingWait(Sch.db.execute(sql, params));
    }
}

class SchSeed {
    static void ensureDemo() {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add("demo@jnative.dev");
        JnFuture q = Sch.db.queryOne("SELECT id FROM users WHERE email=?", p, new IdMapper());
        FutureBlocker.blockingWait(q);
        Object existing = q.getValue();
        if (existing != null) {
            return;
        }
        long now = AsyncClockNative.wallMillis();
        String hash = Password.hashBlocking("demo1234");
        ArrayList<Object> u = new ArrayList<Object>();
        u.add("demo@jnative.dev");
        u.add("Demo User");
        u.add(hash);
        u.add("Asia/Shanghai");
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(now));
        SchDb.exec("INSERT INTO users(email, display_name, password_hash, timezone, created_at, updated_at) VALUES (?,?,?,?,?,?)", u);

        JnFuture uq = Sch.db.queryOne("SELECT id FROM users WHERE email=?", p, new IdMapper());
        FutureBlocker.blockingWait(uq);
        long userId = SchNum.parseLong((String) uq.getValue());

        insertCalendar(userId, "Personal", "#3B82F6", 1, now);
        insertCalendar(userId, "Work", "#10B981", 0, now);
        insertCalendar(userId, "Family", "#F59E0B", 0, now);

        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(userId));
        JnFuture cq = Sch.db.queryOne("SELECT id FROM calendars WHERE owner_id=? AND is_default=1", cp, new IdMapper());
        FutureBlocker.blockingWait(cq);
        long calId = SchNum.parseLong((String) cq.getValue());

        long day = 86400000L;
        long tomorrow10 = startOfTodayUtc() + day + 10L * 3600000L;
        insertEvent(calId, "客户会议", "准备报价与演示", "会议室 A", tomorrow10, tomorrow10 + 3600000L, "", 10, now);
        insertEvent(calId, "Weekly Sync", "", "", tomorrow10 + day, tomorrow10 + day + 1800000L, "WEEKLY", 15, now);

        ArrayList<Object> t = new ArrayList<Object>();
        t.add(CryptoParse.longToString(userId));
        t.add("准备报价");
        t.add("客户会议材料");
        t.add(CryptoParse.longToString(tomorrow10 - 3600000L));
        t.add(TaskPriority.HIGH);
        t.add(TaskStatus.OPEN);
        t.add(CryptoParse.longToString(now));
        SchDb.exec("INSERT INTO tasks(user_id, title, description, due_at, priority, status, created_at) VALUES (?,?,?,?,?,?,?)", t);
    }

    static long startOfTodayUtc() {
        long now = AsyncClockNative.wallMillis();
        return now - (now % 86400000L);
    }

    static void insertCalendar(long userId, String name, String color, int isDefault, long now) {
        ArrayList<Object> c = new ArrayList<Object>();
        c.add(CryptoParse.longToString(userId));
        c.add(name);
        c.add("");
        c.add(color);
        c.add(String.valueOf(isDefault));
        c.add(CryptoParse.longToString(now));
        SchDb.exec("INSERT INTO calendars(owner_id, name, description, color, is_default, created_at) VALUES (?,?,?,?,?,?)", c);
    }

    static void insertEvent(long calId, String title, String desc, String loc, long start, long end, String rrule, int mins, long now) {
        ArrayList<Object> e = new ArrayList<Object>();
        e.add(CryptoParse.longToString(calId));
        e.add(title);
        e.add(desc);
        e.add(loc);
        e.add(CryptoParse.longToString(start));
        e.add(CryptoParse.longToString(end));
        e.add(String.valueOf(0));
        e.add("Asia/Shanghai");
        e.add(rrule);
        e.add(EventStatus.CONFIRMED);
        e.add(CryptoParse.longToString(now));
        e.add(CryptoParse.longToString(now));
        SchDb.exec("INSERT INTO events(calendar_id, title, description, location, start_time, end_time, all_day, timezone, recurrence_rule, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", e);
        ArrayList<Object> q = new ArrayList<Object>();
        q.add(CryptoParse.longToString(calId));
        q.add(title);
        q.add(CryptoParse.longToString(start));
        JnFuture f = Sch.db.queryOne("SELECT id FROM events WHERE calendar_id=? AND title=? AND start_time=?", q, new IdMapper());
        FutureBlocker.blockingWait(f);
        if (f.getValue() != null) {
            long eid = SchNum.parseLong((String) f.getValue());
            ArrayList<Object> r = new ArrayList<Object>();
            r.add(CryptoParse.longToString(eid));
            r.add(ReminderType.IN_APP);
            r.add(String.valueOf(mins));
            SchDb.exec("INSERT INTO reminders(event_id, type, minutes_before) VALUES (?,?,?)", r);
        }
    }
}


class SchQuery {
    static ArrayList<Object> mapAll(JnFuture q, RowMapper mapper) {
        FutureBlocker.blockingWait(q);
        DbQueryResult result = (DbQueryResult) q.getValue();
        ArrayList<Object> out = new ArrayList<Object>();
        if (result == null || result.rows == null) {
            return out;
        }
        int i = 0;
        while (i < result.rows.size()) {
            out.add(mapper.map(result.rows.get(i)));
            i = i + 1;
        }
        return out;
    }
}

class IdMapper implements RowMapper {
    public Object map(Row row) {
        return CryptoParse.longToString(row.getLong(0));
    }
}

class SchAuth {
    static String nextRequestId() {
        Sch.requestSeq = Sch.requestSeq + 1;
        return "req-" + CryptoParse.longToString(Sch.requestSeq);
    }

    static Session load(WebContext ctx) {
        SessionManager mgr = new SessionManager();
        mgr.store = Sch.sessions;
        return mgr.loadOrCreate(ctx);
    }

    static void save(WebContext ctx, Session session) {
        SessionManager mgr = new SessionManager();
        mgr.store = Sch.sessions;
        mgr.save(ctx, session);
    }

    static long requireUserId(WebContext ctx) {
        Session s = load(ctx);
        String uid = s.getString("userId");
        if (uid == null || uid.length() == 0) {
            return 0;
        }
        return SchNum.parseLong(uid);
    }

    static void publish(String type, String json) {
        Sch.hubSeq = Sch.hubSeq + 1;
        Sch.hubPayload = "{\"seq\":" + Sch.hubSeq + ",\"type\":\"" + type + "\",\"data\":" + json + "}";
    }

    static void err(WebContext ctx, int status, String code, String message, String requestId) {
        JsonObject o = new JsonObject();
        o.putString("code", code);
        o.putString("message", message);
        o.putString("requestId", requestId);
        ctx.status(status).jsonString(Json.stringify(o));
    }
}

class HealthHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.jsonString("{\"ok\":true,\"service\":\"scheduler\"}");
    }
}

class ReadyHandler implements WebHandler {
    public void handle(WebContext ctx) {
        if (Sch.db == null) {
            ctx.status(503).jsonString("{\"ready\":false}");
            return;
        }
        ctx.jsonString("{\"ready\":true,\"db\":\"" + Sch.dialect + "\"}");
    }
}

class StreamHandler implements WebHandler {
    public void handle(WebContext ctx) {
        String since = ctx.query("since");
        long s = 0;
        if (since != null && since.length() > 0) {
            s = SchNum.parseLong(since);
        }
        if (Sch.hubSeq > s && Sch.hubPayload.length() > 0) {
            ctx.jsonString(Sch.hubPayload);
        } else {
            ctx.jsonString("{\"seq\":" + Sch.hubSeq + ",\"type\":\"HEARTBEAT\",\"data\":{}}");
        }
    }
}

class LoginHandler implements AsyncWebHandler {
    EventLoop loop;

    LoginHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        if (Sch.loginLimiter.allow("login") == 0) {
            SchAuth.err(ctx, 429, "RATE_LIMITED", "too many login attempts", rid);
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        LoginRequest req = new LoginRequest();
        req.email = body.getString("email", "");
        req.password = body.getString("password", "");
        String verr = LoginValidation.validate(req);
        if (verr.length() > 0) {
            SchAuth.err(ctx, 400, "VALIDATION_ERROR", verr, rid);
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(req.email);
        JnFuture q = Sch.db.queryOne(
                "SELECT id, email, display_name, password_hash, timezone FROM users WHERE email=?",
                p, new UserRowMapper());
        FutureBlocker.blockingWait(q);
        UserRow user = (UserRow) q.getValue();
        if (user == null || Password.verifyBlocking(req.password, user.passwordHash) == 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "invalid email or password", rid);
            return JnFuture.completed(loop, null);
        }
        Session session = SchAuth.load(ctx);
        session.rotateId();
        session.setString("userId", CryptoParse.longToString(user.id));
        String csrf = "csrf-" + CryptoParse.longToString(user.id) + "-" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        session.setString("csrf", csrf);
        SchAuth.save(ctx, session);
        AuthSessionDto dto = new AuthSessionDto();
        dto.user.id = user.id;
        dto.user.email = user.email;
        dto.user.displayName = user.displayName;
        dto.user.timezone = user.timezone;
        dto.csrfToken = csrf;
        ctx.jsonString(SchJson.authSession(dto));
        return JnFuture.completed(loop, null);
    }
}

class LogoutHandler implements AsyncWebHandler {
    EventLoop loop;

    LogoutHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        Session session = SchAuth.load(ctx);
        session.setString("userId", "");
        session.setString("csrf", "");
        SchAuth.save(ctx, session);
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
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        JnFuture q = Sch.db.queryOne(
                "SELECT id, email, display_name, password_hash, timezone FROM users WHERE id=?",
                p, new UserRowMapper());
        FutureBlocker.blockingWait(q);
        UserRow user = (UserRow) q.getValue();
        if (user == null) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        UserDto dto = new UserDto();
        dto.id = user.id;
        dto.email = user.email;
        dto.displayName = user.displayName;
        dto.timezone = user.timezone;
        ctx.jsonString(SchJson.user(dto));
        return JnFuture.completed(loop, null);
    }
}

class UserRow {
    long id;
    String email;
    String displayName;
    String passwordHash;
    String timezone;
}

class UserRowMapper implements RowMapper {
    public Object map(Row row) {
        UserRow u = new UserRow();
        u.id = row.getLong(0);
        u.email = row.getString(1);
        u.displayName = row.getString(2);
        u.passwordHash = row.getString(3);
        u.timezone = row.getString(4);
        return u;
    }
}

class ListCalendarsHandler implements AsyncWebHandler {
    EventLoop loop;

    ListCalendarsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        ArrayList<Object> rows = SchQuery.mapAll(Sch.db.query("SELECT id, name, description, color, is_default FROM calendars WHERE owner_id=? ORDER BY is_default DESC, id",
                p), new CalendarRowMapper());
        ctx.jsonString(SchJson.calendars(rows));
        return JnFuture.completed(loop, null);
    }
}

class CalendarRowMapper implements RowMapper {
    public Object map(Row row) {
        CalendarDto c = new CalendarDto();
        c.id = row.getLong(0);
        c.name = row.getString(1);
        c.description = row.getString(2);
        c.color = row.getString(3);
        c.isDefault = row.getInt(4);
        return c;
    }
}

class EventsBetweenHandler implements AsyncWebHandler {
    EventLoop loop;

    EventsBetweenHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        long fromMs = SchNum.parseLong(ctx.query("from"));
        long toMs = SchNum.parseLong(ctx.query("to"));
        if (fromMs <= 0 || toMs <= 0) {
            SchAuth.err(ctx, 400, "VALIDATION_ERROR", "from and to required", rid);
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        p.add(CryptoParse.longToString(toMs));
        p.add(CryptoParse.longToString(fromMs));
        ArrayList<Object> rows = SchQuery.mapAll(Sch.db.query("SELECT e.id, e.calendar_id, e.title, e.description, e.location, e.start_time, e.end_time,"
                        + " e.all_day, e.timezone, e.recurrence_rule, e.status, c.color,"
                        + " COALESCE((SELECT minutes_before FROM reminders r WHERE r.event_id=e.id LIMIT 1), 10)"
                        + " FROM events e JOIN calendars c ON c.id=e.calendar_id"
                        + " WHERE c.owner_id=? AND e.start_time<=? AND e.end_time>=?"
                        + " AND e.status<>'CANCELLED'",
                p), new EventRowMapper());
        ArrayList<Object> expanded = new ArrayList<Object>();
        int i = 0;
        while (i < rows.size()) {
            EventDto base = (EventDto) rows.get(i);
            ArrayList<Object> part = RecurrenceIterator.expand(base, fromMs, toMs);
            int j = 0;
            while (j < part.size()) {
                expanded.add(part.get(j));
                j = j + 1;
            }
            i = i + 1;
        }
        /* also expand recurring whose base start is before range */
        ArrayList<Object> p2 = new ArrayList<Object>();
        p2.add(CryptoParse.longToString(uid));
        p2.add(CryptoParse.longToString(fromMs));
        ArrayList<Object> rows2 = SchQuery.mapAll(Sch.db.query("SELECT e.id, e.calendar_id, e.title, e.description, e.location, e.start_time, e.end_time,"
                        + " e.all_day, e.timezone, e.recurrence_rule, e.status, c.color,"
                        + " COALESCE((SELECT minutes_before FROM reminders r WHERE r.event_id=e.id LIMIT 1), 10)"
                        + " FROM events e JOIN calendars c ON c.id=e.calendar_id"
                        + " WHERE c.owner_id=? AND e.recurrence_rule<>'' AND e.start_time<?"
                        + " AND e.status<>'CANCELLED'",
                p2), new EventRowMapper());
        i = 0;
        while (i < rows2.size()) {
            EventDto base = (EventDto) rows2.get(i);
            ArrayList<Object> part = RecurrenceIterator.expand(base, fromMs, toMs);
            int j = 0;
            while (j < part.size()) {
                expanded.add(part.get(j));
                j = j + 1;
            }
            i = i + 1;
        }
        ctx.jsonString(SchJson.events(expanded));
        return JnFuture.completed(loop, null);
    }
}

class EventRowMapper implements RowMapper {
    public Object map(Row row) {
        EventDto e = new EventDto();
        e.id = row.getLong(0);
        e.calendarId = row.getLong(1);
        e.title = row.getString(2);
        e.description = row.getString(3);
        e.location = row.getString(4);
        e.startTime = row.getLong(5);
        e.endTime = row.getLong(6);
        e.allDay = row.getInt(7);
        e.timezone = row.getString(8);
        e.recurrenceRule = row.getString(9);
        e.status = row.getString(10);
        e.color = row.getString(11);
        e.minutesBefore = row.getInt(12);
        return e;
    }
}

class CreateEventHandler implements AsyncWebHandler {
    EventLoop loop;

    CreateEventHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        if (SchCsrf.ok(ctx) == 0) {
            SchAuth.err(ctx, 403, "FORBIDDEN", "csrf", rid);
            return JnFuture.completed(loop, null);
        }
        CreateEventRequest req = SchJson.parseCreateEvent(ctx.bodyString());
        String verr = EventValidation.validateCreate(req);
        if (verr.length() > 0) {
            SchAuth.err(ctx, 400, "VALIDATION_ERROR", verr, rid);
            return JnFuture.completed(loop, null);
        }
        if (CalendarRepo.owns(uid, req.calendarId) == 0) {
            SchAuth.err(ctx, 403, "FORBIDDEN", "calendar access denied", rid);
            return JnFuture.completed(loop, null);
        }
        EventDto created = EventRepo.create(req);
        SchAuth.publish("EVENT_CREATED", SchJson.event(created));
        ctx.status(201).jsonString(SchJson.event(created));
        return JnFuture.completed(loop, null);
    }
}

class UpdateEventHandler implements AsyncWebHandler {
    EventLoop loop;

    UpdateEventHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        if (SchCsrf.ok(ctx) == 0) {
            SchAuth.err(ctx, 403, "FORBIDDEN", "csrf", rid);
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long id = body.getLong("id", 0);
        UpdateEventRequest req = SchJson.parseUpdateEvent(body);
        String verr = EventValidation.validateUpdate(req);
        if (verr.length() > 0) {
            SchAuth.err(ctx, 400, "VALIDATION_ERROR", verr, rid);
            return JnFuture.completed(loop, null);
        }
        if (EventRepo.owns(uid, id) == 0) {
            SchAuth.err(ctx, 404, "NOT_FOUND", "event not found", rid);
            return JnFuture.completed(loop, null);
        }
        EventDto updated = EventRepo.update(id, req);
        SchAuth.publish("EVENT_UPDATED", SchJson.event(updated));
        ctx.jsonString(SchJson.event(updated));
        return JnFuture.completed(loop, null);
    }
}

class DeleteEventHandler implements AsyncWebHandler {
    EventLoop loop;

    DeleteEventHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        if (SchCsrf.ok(ctx) == 0) {
            SchAuth.err(ctx, 403, "FORBIDDEN", "csrf", rid);
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long id = body.getLong("id", 0);
        if (EventRepo.owns(uid, id) == 0) {
            SchAuth.err(ctx, 404, "NOT_FOUND", "event not found", rid);
            return JnFuture.completed(loop, null);
        }
        EventRepo.delete(id);
        SchAuth.publish("EVENT_DELETED", "{\"id\":" + id + "}");
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class SchCsrf {
    static int ok(WebContext ctx) {
        Session s = SchAuth.load(ctx);
        String expect = s.getString("csrf");
        String got = ctx.getHeader("X-CSRF-Token");
        if (got == null || got.length() == 0) {
            got = ctx.query("csrf");
        }
        if (expect == null || expect.length() == 0) {
            return 1;
        }
        if (got != null && got.equals(expect)) {
            return 1;
        }
        return 0;
    }
}

class CalendarRepo {
    static int owns(long userId, long calendarId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(calendarId));
        p.add(CryptoParse.longToString(userId));
        JnFuture q = Sch.db.queryOne("SELECT id FROM calendars WHERE id=? AND owner_id=?", p, new IdMapper());
        FutureBlocker.blockingWait(q);
        if (q.getValue() == null) {
            return 0;
        }
        return 1;
    }
}

class EventRepo {
    static int owns(long userId, long eventId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(eventId));
        p.add(CryptoParse.longToString(userId));
        JnFuture q = Sch.db.queryOne(
                "SELECT e.id FROM events e JOIN calendars c ON c.id=e.calendar_id WHERE e.id=? AND c.owner_id=?",
                p, new IdMapper());
        FutureBlocker.blockingWait(q);
        if (q.getValue() == null) {
            return 0;
        }
        return 1;
    }

    static EventDto create(CreateEventRequest req) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> e = new ArrayList<Object>();
        e.add(CryptoParse.longToString(req.calendarId));
        e.add(req.title);
        e.add(req.description);
        e.add(req.location);
        e.add(CryptoParse.longToString(req.startTime));
        e.add(CryptoParse.longToString(req.endTime));
        e.add(String.valueOf(req.allDay));
        e.add(req.timezone);
        e.add(req.recurrenceRule);
        e.add(EventStatus.CONFIRMED);
        e.add(CryptoParse.longToString(now));
        e.add(CryptoParse.longToString(now));
        SchDb.exec("INSERT INTO events(calendar_id, title, description, location, start_time, end_time, all_day, timezone, recurrence_rule, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", e);
        ArrayList<Object> q = new ArrayList<Object>();
        q.add(CryptoParse.longToString(req.calendarId));
        q.add(req.title);
        q.add(CryptoParse.longToString(req.startTime));
        JnFuture f = Sch.db.queryOne("SELECT id FROM events WHERE calendar_id=? AND title=? AND start_time=? ORDER BY id DESC LIMIT 1", q, new IdMapper());
        FutureBlocker.blockingWait(f);
        long id = SchNum.parseLong((String) f.getValue());
        ArrayList<Object> r = new ArrayList<Object>();
        r.add(CryptoParse.longToString(id));
        r.add(ReminderType.IN_APP);
        r.add(String.valueOf(req.minutesBefore));
        SchDb.exec("INSERT INTO reminders(event_id, type, minutes_before) VALUES (?,?,?)", r);
        return get(id);
    }

    static EventDto update(long id, UpdateEventRequest req) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> e = new ArrayList<Object>();
        e.add(req.title);
        e.add(req.description);
        e.add(req.location);
        e.add(CryptoParse.longToString(req.startTime));
        e.add(CryptoParse.longToString(req.endTime));
        e.add(String.valueOf(req.allDay));
        e.add(req.timezone);
        e.add(req.recurrenceRule);
        e.add(req.status);
        e.add(CryptoParse.longToString(now));
        e.add(CryptoParse.longToString(id));
        SchDb.exec("UPDATE events SET title=?, description=?, location=?, start_time=?, end_time=?, all_day=?, timezone=?, recurrence_rule=?, status=?, updated_at=? WHERE id=?", e);
        ArrayList<Object> d = new ArrayList<Object>();
        d.add(CryptoParse.longToString(id));
        SchDb.exec("DELETE FROM reminders WHERE event_id=?", d);
        ArrayList<Object> r = new ArrayList<Object>();
        r.add(CryptoParse.longToString(id));
        r.add(ReminderType.IN_APP);
        r.add(String.valueOf(req.minutesBefore));
        SchDb.exec("INSERT INTO reminders(event_id, type, minutes_before) VALUES (?,?,?)", r);
        return get(id);
    }

    static void delete(long id) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        SchDb.exec("DELETE FROM reminders WHERE event_id=?", p);
        SchDb.exec("DELETE FROM events WHERE id=?", p);
    }

    static EventDto get(long id) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture q = Sch.db.queryOne(
                "SELECT e.id, e.calendar_id, e.title, e.description, e.location, e.start_time, e.end_time,"
                        + " e.all_day, e.timezone, e.recurrence_rule, e.status, c.color,"
                        + " COALESCE((SELECT minutes_before FROM reminders r WHERE r.event_id=e.id LIMIT 1), 10)"
                        + " FROM events e JOIN calendars c ON c.id=e.calendar_id WHERE e.id=?",
                p, new EventRowMapper());
        FutureBlocker.blockingWait(q);
        return (EventDto) q.getValue();
    }
}

class ListTasksHandler implements AsyncWebHandler {
    EventLoop loop;

    ListTasksHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        ArrayList<Object> rows = SchQuery.mapAll(Sch.db.query("SELECT id, title, description, due_at, priority, status, created_at, completed_at FROM tasks WHERE user_id=? ORDER BY status, due_at",
                p), new TaskRowMapper());
        ctx.jsonString(SchJson.tasks(rows));
        return JnFuture.completed(loop, null);
    }
}

class TaskRowMapper implements RowMapper {
    public Object map(Row row) {
        TaskDto t = new TaskDto();
        t.id = row.getLong(0);
        t.title = row.getString(1);
        t.description = row.getString(2);
        t.dueAt = row.getLong(3);
        t.priority = row.getString(4);
        t.status = row.getString(5);
        t.createdAt = row.getLong(6);
        t.completedAt = row.getLong(7);
        return t;
    }
}

class CreateTaskHandler implements AsyncWebHandler {
    EventLoop loop;

    CreateTaskHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        CreateTaskRequest req = SchJson.parseCreateTask(ctx.bodyString());
        String verr = TaskValidation.validateCreate(req);
        if (verr.length() > 0) {
            SchAuth.err(ctx, 400, "VALIDATION_ERROR", verr, rid);
            return JnFuture.completed(loop, null);
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> t = new ArrayList<Object>();
        t.add(CryptoParse.longToString(uid));
        t.add(req.title);
        t.add(req.description);
        t.add(CryptoParse.longToString(req.dueAt));
        t.add(req.priority);
        t.add(TaskStatus.OPEN);
        t.add(CryptoParse.longToString(now));
        SchDb.exec("INSERT INTO tasks(user_id, title, description, due_at, priority, status, created_at) VALUES (?,?,?,?,?,?,?)", t);
        ArrayList<Object> q = new ArrayList<Object>();
        q.add(CryptoParse.longToString(uid));
        q.add(req.title);
        JnFuture f = Sch.db.queryOne("SELECT id, title, description, due_at, priority, status, created_at, completed_at FROM tasks WHERE user_id=? AND title=? ORDER BY id DESC LIMIT 1", q, new TaskRowMapper());
        FutureBlocker.blockingWait(f);
        TaskDto dto = (TaskDto) f.getValue();
        SchAuth.publish("TASK_UPDATED", SchJson.task(dto));
        ctx.status(201).jsonString(SchJson.task(dto));
        return JnFuture.completed(loop, null);
    }
}

class UpdateTaskHandler implements AsyncWebHandler {
    EventLoop loop;

    UpdateTaskHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long id = body.getLong("id", 0);
        UpdateTaskRequest req = SchJson.parseUpdateTask(body);
        if (TaskRepo.owns(uid, id) == 0) {
            SchAuth.err(ctx, 404, "NOT_FOUND", "task not found", rid);
            return JnFuture.completed(loop, null);
        }
        long completed = 0;
        if (req.status.equals(TaskStatus.DONE)) {
            completed = AsyncClockNative.wallMillis();
        }
        ArrayList<Object> t = new ArrayList<Object>();
        t.add(req.title);
        t.add(req.description);
        t.add(CryptoParse.longToString(req.dueAt));
        t.add(req.priority);
        t.add(req.status);
        t.add(CryptoParse.longToString(completed));
        t.add(CryptoParse.longToString(id));
        SchDb.exec("UPDATE tasks SET title=?, description=?, due_at=?, priority=?, status=?, completed_at=? WHERE id=?", t);
        TaskDto dto = TaskRepo.get(id);
        SchAuth.publish("TASK_UPDATED", SchJson.task(dto));
        ctx.jsonString(SchJson.task(dto));
        return JnFuture.completed(loop, null);
    }
}

class DeleteTaskHandler implements AsyncWebHandler {
    EventLoop loop;

    DeleteTaskHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long id = body.getLong("id", 0);
        if (TaskRepo.owns(uid, id) == 0) {
            SchAuth.err(ctx, 404, "NOT_FOUND", "task not found", rid);
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        SchDb.exec("DELETE FROM tasks WHERE id=?", p);
        SchAuth.publish("TASK_UPDATED", "{\"id\":" + id + ",\"deleted\":true}");
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class TaskRepo {
    static int owns(long userId, long taskId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(taskId));
        p.add(CryptoParse.longToString(userId));
        JnFuture q = Sch.db.queryOne("SELECT id FROM tasks WHERE id=? AND user_id=?", p, new IdMapper());
        FutureBlocker.blockingWait(q);
        if (q.getValue() == null) {
            return 0;
        }
        return 1;
    }

    static TaskDto get(long id) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        JnFuture q = Sch.db.queryOne(
                "SELECT id, title, description, due_at, priority, status, created_at, completed_at FROM tasks WHERE id=?",
                p, new TaskRowMapper());
        FutureBlocker.blockingWait(q);
        return (TaskDto) q.getValue();
    }
}

class SearchHandler implements AsyncWebHandler {
    EventLoop loop;

    SearchHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String rid = SchAuth.nextRequestId();
        long uid = SchAuth.requireUserId(ctx);
        if (uid <= 0) {
            SchAuth.err(ctx, 401, "AUTH_REQUIRED", "login required", rid);
            return JnFuture.completed(loop, null);
        }
        String query = ctx.query("q");
        if (query == null) {
            query = "";
        }
        ArrayList<Object> out = new ArrayList<Object>();
        if (query.length() == 0) {
            ctx.jsonString("[]");
            return JnFuture.completed(loop, null);
        }
        String like = "%" + query + "%";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(uid));
        p.add(like);
        p.add(like);
        p.add(like);
        ArrayList<Object> rows = SchQuery.mapAll(Sch.db.query("SELECT e.id, e.title, e.description, e.start_time FROM events e"
                        + " JOIN calendars c ON c.id=e.calendar_id"
                        + " WHERE c.owner_id=? AND (e.title LIKE ? OR e.description LIKE ? OR e.location LIKE ?)"
                        + " LIMIT 50",
                p), new SearchEventMapper());
        int i = 0;
        while (i < rows.size()) {
            out.add((SearchResultDto) rows.get(i));
            i = i + 1;
        }
        ArrayList<Object> tp = new ArrayList<Object>();
        tp.add(CryptoParse.longToString(uid));
        tp.add(like);
        tp.add(like);
        ArrayList<Object> trows = SchQuery.mapAll(Sch.db.query("SELECT id, title, description, due_at FROM tasks WHERE user_id=? AND (title LIKE ? OR description LIKE ?) LIMIT 50",
                tp), new SearchTaskMapper());
        i = 0;
        while (i < trows.size()) {
            out.add((SearchResultDto) trows.get(i));
            i = i + 1;
        }
        ctx.jsonString(SchJson.searchResults(out));
        return JnFuture.completed(loop, null);
    }
}

class SearchEventMapper implements RowMapper {
    public Object map(Row row) {
        SearchResultDto s = new SearchResultDto();
        s.kind = "event";
        s.id = row.getLong(0);
        s.title = row.getString(1);
        s.snippet = row.getString(2);
        s.whenMs = row.getLong(3);
        return s;
    }
}

class SearchTaskMapper implements RowMapper {
    public Object map(Row row) {
        SearchResultDto s = new SearchResultDto();
        s.kind = "task";
        s.id = row.getLong(0);
        s.title = row.getString(1);
        s.snippet = row.getString(2);
        s.whenMs = row.getLong(3);
        return s;
    }
}

class ReminderScheduler {
    static void start(EventLoop loop) {
        loop.scheduleTimer(30000, new ReminderTick());
    }
}

class ReminderTick implements LoopTask {
    public void run() {
        long now = AsyncClockNative.wallMillis();
        long windowEnd = now + 60000L;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(windowEnd));
        ArrayList<Object> rows = SchQuery.mapAll(Sch.db.query("SELECT e.id, e.title, r.minutes_before, e.start_time FROM reminders r"
                        + " JOIN events e ON e.id=r.event_id"
                        + " WHERE e.start_time - (r.minutes_before * 60000) >= ? AND e.start_time - (r.minutes_before * 60000) < ?"
                        + " AND e.status='CONFIRMED'",
                p), new ReminderRowMapper());
        int i = 0;
        while (i < rows.size()) {
            String json = (String) rows.get(i);
            SchAuth.publish("REMINDER", json);
            i = i + 1;
        }
        Sch.loop.scheduleTimer(30000, new ReminderTick());
    }
}

class ReminderRowMapper implements RowMapper {
    public Object map(Row row) {
        long id = row.getLong(0);
        String title = row.getString(1);
        int mins = row.getInt(2);
        return "{\"eventId\":" + id + ",\"title\":\"" + SchJson.esc(title) + "\",\"minutesBefore\":" + mins + "}";
    }
}
