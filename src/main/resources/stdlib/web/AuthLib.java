// JNative Auth (0.25) — Session, Auth, CSRF, RateLimit, SecurityHeaders.

/**
 * @author 张晓达
 */
class UnauthorizedException extends WebException {
    UnauthorizedException(String message) {
        super(401, message);
    }
}

class ForbiddenException extends WebException {
    ForbiddenException(String message) {
        super(403, message);
    }
}

class UserPrincipal {
    String id;
    String name;
    String email;
    String role;

    UserPrincipal() {
        id = "";
        name = "";
        email = "";
        role = "";
    }

    static UserPrincipal of(String id, String name, String email, String role) {
        UserPrincipal p = new UserPrincipal();
        p.id = id;
        p.name = name;
        p.email = email;
        p.role = role;
        return p;
    }
}

class SessionData {
    String id;
    HashMap<String, String> values;
    long createdAtMs;
    long lastAccessMs;
    String userId;

    SessionData() {
        id = "";
        values = new HashMap<String, String>();
        createdAtMs = 0L;
        lastAccessMs = 0L;
        userId = "";
    }

    static SessionData createNew() {
        SessionData d = new SessionData();
        d.id = SecureRandom.sessionId();
        long now = WebUtil.monoMillis();
        d.createdAtMs = now;
        d.lastAccessMs = now;
        d.userId = "";
        return d;
    }
}

interface SessionStore {
    JnFuture load(EventLoop loop, String id);
    JnFuture save(EventLoop loop, SessionData data);
    JnFuture delete(EventLoop loop, String id);
}

interface SessionStoreSync {
    SessionData loadSync(String id);
    void saveSync(SessionData data);
    void deleteSync(String id);
}

class InMemorySessionStore implements SessionStore, SessionStoreSync {
    HashMap<String, SessionData> sessions;
    ArrayList<String> ids;
    long idleTimeoutMs;
    long absoluteTimeoutMs;

    InMemorySessionStore() {
        sessions = new HashMap<String, SessionData>();
        ids = new ArrayList<String>();
        idleTimeoutMs = 1800000L;
        absoluteTimeoutMs = 86400000L;
    }

    static InMemorySessionStore withTimeouts(long idleTimeoutMs, long absoluteTimeoutMs) {
        InMemorySessionStore s = new InMemorySessionStore();
        s.idleTimeoutMs = idleTimeoutMs;
        s.absoluteTimeoutMs = absoluteTimeoutMs;
        return s;
    }

    public SessionData loadSync(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        SessionData d = sessions.get(id);
        if (d == null) {
            return null;
        }
        long now = WebUtil.monoMillis();
        if (absoluteTimeoutMs > 0L) {
            if (now - d.createdAtMs > absoluteTimeoutMs) {
                deleteSync(id);
                return null;
            }
        }
        if (idleTimeoutMs > 0L) {
            if (now - d.lastAccessMs > idleTimeoutMs) {
                deleteSync(id);
                return null;
            }
        }
        d.lastAccessMs = now;
        return d;
    }

    public void saveSync(SessionData data) {
        if (data == null || data.id == null || data.id.length() == 0) {
            return;
        }
        data.lastAccessMs = WebUtil.monoMillis();
        if (sessions.containsKey(data.id) == false) {
            ids.add(data.id);
        }
        sessions.put(data.id, data);
    }

    public void deleteSync(String id) {
        if (id == null) {
            return;
        }
        sessions.remove(id);
        int i = 0;
        int removed = 0;
        while (i < ids.size() && removed == 0) {
            if (ids.get(i).equals(id)) {
                ids.removeAt(i);
                removed = 1;
            } else {
                i = i + 1;
            }
        }
    }

    public JnFuture load(EventLoop loop, String id) {
        return JnFuture.completed(loop, loadSync(id));
    }

    public JnFuture save(EventLoop loop, SessionData data) {
        saveSync(data);
        return JnFuture.completed(loop, null);
    }

    public JnFuture delete(EventLoop loop, String id) {
        deleteSync(id);
        return JnFuture.completed(loop, null);
    }

    void cleanup() {
        long now = WebUtil.monoMillis();
        ArrayList<String> doomed = new ArrayList<String>();
        int i = 0;
        while (i < ids.size()) {
            String id = ids.get(i);
            SessionData d = sessions.get(id);
            int drop = 0;
            if (d == null) {
                drop = 1;
            } else {
                if (absoluteTimeoutMs > 0L && now - d.createdAtMs > absoluteTimeoutMs) {
                    drop = 1;
                }
                if (idleTimeoutMs > 0L && now - d.lastAccessMs > idleTimeoutMs) {
                    drop = 1;
                }
            }
            if (drop != 0) {
                doomed.add(id);
            }
            i = i + 1;
        }
        i = 0;
        while (i < doomed.size()) {
            deleteSync(doomed.get(i));
            i = i + 1;
        }
    }
}

class Session {
    SessionData data;
    SessionStoreSync store;

    Session() {
        data = null;
        store = null;
    }

    static Session wrap(SessionData data, SessionStoreSync store) {
        Session s = new Session();
        s.data = data;
        s.store = store;
        return s;
    }

    String getId() {
        if (data == null) {
            return "";
        }
        return data.id;
    }

    String getString(String key) {
        if (data == null || key == null) {
            return null;
        }
        return data.values.get(key);
    }

    void setString(String key, String value) {
        if (data == null || key == null) {
            return;
        }
        if (value == null) {
            data.values.remove(key);
        } else {
            data.values.put(key, value);
        }
    }

    int getInt(String key, int def) {
        String v = getString(key);
        return WebUtil.parseIntOr(v, def);
    }

    void setInt(String key, int value) {
        setString(key, String.valueOf(value));
    }

    String getUserId() {
        if (data == null) {
            return "";
        }
        return data.userId;
    }

    void setUserId(String userId) {
        if (data == null) {
            return;
        }
        if (userId == null) {
            data.userId = "";
        } else {
            data.userId = userId;
        }
    }

    void save() {
        if (store != null && data != null) {
            store.saveSync(data);
        }
    }

    void invalidate() {
        if (data == null) {
            return;
        }
        String id = data.id;
        data.values = new HashMap<String, String>();
        data.userId = "";
        if (store != null) {
            store.deleteSync(id);
        }
        data.id = "";
    }

    /** Call after successful login to rotate the session id (session fixation defense). */
    void rotateId() {
        if (data == null || store == null) {
            return;
        }
        String oldId = data.id;
        String neu = SecureRandom.sessionId();
        data.id = neu;
        data.lastAccessMs = WebUtil.monoMillis();
        if (oldId != null && oldId.length() > 0) {
            store.deleteSync(oldId);
        }
        store.saveSync(data);
    }
}

class SessionManager {
    static String COOKIE_NAME = "JNSESSION";
    SessionStoreSync store;
    String cookieName;

    SessionManager() {
        store = new InMemorySessionStore();
        cookieName = COOKIE_NAME;
    }

    static SessionManager withStore(SessionStoreSync store) {
        SessionManager m = new SessionManager();
        if (store != null) {
            m.store = store;
        }
        return m;
    }

    Session loadOrCreate(WebContext ctx) {
        String raw = null;
        if (ctx != null) {
            raw = ctx.getHeader("Cookie");
        }
        String sid = null;
        if (raw != null) {
            sid = CookieUtil.get(raw, cookieName);
        }
        SessionData data = null;
        if (sid != null && sid.length() > 0) {
            data = store.loadSync(sid);
        }
        if (data == null) {
            data = SessionData.createNew();
            store.saveSync(data);
        }
        return Session.wrap(data, store);
    }

    void save(WebContext ctx, Session session) {
        if (ctx == null || session == null || session.data == null) {
            return;
        }
        session.save();
        String wire = CookieUtil.encode(cookieName, session.data.id, "/", 0, 1, 0, "Lax");
        ctx.setHeader("Set-Cookie", wire);
    }

    void clearCookie(WebContext ctx) {
        if (ctx == null) {
            return;
        }
        String wire = CookieUtil.encode(cookieName, "", "/", 0, 1, 0, "Lax");
        ctx.setHeader("Set-Cookie", wire);
    }
}

class AuthContext {
    static String ATTR_PRINCIPAL = "auth.principal";
    static String SESSION_USER_ID = "userId";
    static String SESSION_USER_NAME = "userName";
    static String SESSION_USER_EMAIL = "userEmail";
    static String SESSION_USER_ROLE = "userRole";

    static void bindPrincipal(Session session, UserPrincipal principal) {
        if (session == null || principal == null) {
            return;
        }
        session.setUserId(principal.id);
        session.setString(SESSION_USER_ID, principal.id);
        session.setString(SESSION_USER_NAME, principal.name);
        session.setString(SESSION_USER_EMAIL, principal.email);
        session.setString(SESSION_USER_ROLE, principal.role);
    }

    static UserPrincipal principalFromSession(Session session) {
        if (session == null || session.data == null) {
            return null;
        }
        String id = session.getString(SESSION_USER_ID);
        if (id == null || id.length() == 0) {
            id = session.getUserId();
        }
        if (id == null || id.length() == 0) {
            return null;
        }
        String name = session.getString(SESSION_USER_NAME);
        String email = session.getString(SESSION_USER_EMAIL);
        String role = session.getString(SESSION_USER_ROLE);
        if (name == null) {
            name = "";
        }
        if (email == null) {
            email = "";
        }
        if (role == null) {
            role = "";
        }
        return UserPrincipal.of(id, name, email, role);
    }

    static void putOnContext(WebContext ctx, UserPrincipal principal) {
        if (ctx == null) {
            return;
        }
        ctx.state.put(ATTR_PRINCIPAL, principal);
    }

    static UserPrincipal fromContext(WebContext ctx) {
        if (ctx == null) {
            return null;
        }
        Object v = ctx.state.get(ATTR_PRINCIPAL);
        if (v == null) {
            return null;
        }
        return (UserPrincipal)v;
    }
}

class AuthRequired {
    static UserPrincipal require(Session session) {
        UserPrincipal p = AuthContext.principalFromSession(session);
        if (p == null) {
            throw new UnauthorizedException("authentication required");
        }
        return p;
    }

    static UserPrincipal requireOnContext(WebContext ctx, Session session) {
        UserPrincipal p = AuthContext.fromContext(ctx);
        if (p == null) {
            p = AuthContext.principalFromSession(session);
        }
        if (p == null) {
            throw new UnauthorizedException("authentication required");
        }
        AuthContext.putOnContext(ctx, p);
        return p;
    }
}

class AuthRequireRole {
    static String ADMIN = "ADMIN";
    static String USER = "USER";

    static UserPrincipal requireRole(Session session, String role) {
        UserPrincipal p = AuthRequired.require(session);
        if (role == null) {
            throw new ForbiddenException("role required");
        }
        if (p.role == null || p.role.equals(role) == false) {
            throw new ForbiddenException("forbidden");
        }
        return p;
    }

    static UserPrincipal requireAdmin(Session session) {
        return requireRole(session, ADMIN);
    }
}

class Csrf {
    static String SESSION_KEY = "csrfToken";

    static String generate() {
        return SecureRandom.tokenHex(16);
    }

    static String ensure(Session session) {
        if (session == null) {
            return generate();
        }
        String t = session.getString(SESSION_KEY);
        if (t == null || t.length() == 0) {
            t = generate();
            session.setString(SESSION_KEY, t);
        }
        return t;
    }

    static int validate(String expected, String actual) {
        return ConstantTime.equalsString(expected, actual);
    }

    static int validateSession(Session session, String actual) {
        if (session == null) {
            return 0;
        }
        String expected = session.getString(SESSION_KEY);
        return validate(expected, actual);
    }
}

class RateLimitBucket {
    String key;
    int tokens;
    long lastRefillMs;

    RateLimitBucket() {
        key = "";
        tokens = 0;
        lastRefillMs = 0L;
    }
}

class RateLimiter {
    HashMap<String, RateLimitBucket> buckets;
    int capacity;
    int refillPerSec;

    RateLimiter() {
        buckets = new HashMap<String, RateLimitBucket>();
        capacity = 10;
        refillPerSec = 1;
    }

    static RateLimiter create(int capacity, int refillPerSec) {
        RateLimiter r = new RateLimiter();
        if (capacity > 0) {
            r.capacity = capacity;
        }
        if (refillPerSec > 0) {
            r.refillPerSec = refillPerSec;
        }
        return r;
    }

    static RateLimiter forLogin() {
        return create(5, 1);
    }

    void refill(RateLimitBucket b, long now) {
        long elapsed = now - b.lastRefillMs;
        if (elapsed <= 0L) {
            return;
        }
        long add = (elapsed * refillPerSec) / 1000L;
        if (add <= 0L) {
            return;
        }
        long next = b.tokens + add;
        if (next > capacity) {
            next = capacity;
        }
        int tokenInt = 0;
        long tn = next;
        while (tn > 0L) {
            tokenInt = tokenInt + 1;
            tn = tn - 1L;
        }
        b.tokens = tokenInt;
        b.lastRefillMs = now;
    }

    int allow(String key) {
        if (key == null) {
            key = "";
        }
        long now = WebUtil.monoMillis();
        RateLimitBucket b = buckets.get(key);
        if (b == null) {
            b = new RateLimitBucket();
            b.key = key;
            b.tokens = capacity;
            b.lastRefillMs = now;
            buckets.put(key, b);
        }
        refill(b, now);
        if (b.tokens <= 0) {
            return 0;
        }
        b.tokens = b.tokens - 1;
        return 1;
    }
}

class SecurityHeaders {
    static void applyDefaults(WebResponse response) {
        if (response == null) {
            return;
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "default-src 'self'");
    }

    static void applyDefaultsOnContext(WebContext ctx) {
        if (ctx == null) {
            return;
        }
        applyDefaults(ctx.response);
    }
}

class TrustedProxies {
    ArrayList<String> peers;

    TrustedProxies() {
        peers = new ArrayList<String>();
    }

    void add(String peer) {
        if (peer == null || peer.length() == 0) {
            return;
        }
        peers.add(peer);
    }

    int isTrusted(String peer) {
        if (peer == null) {
            return 0;
        }
        int i = 0;
        while (i < peers.size()) {
            if (peers.get(i).equals(peer)) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    String clientIp(String peer, String xForwardedFor) {
        if (isTrusted(peer) == 0) {
            if (peer == null) {
                return "";
            }
            return peer;
        }
        if (xForwardedFor == null || xForwardedFor.length() == 0) {
            if (peer == null) {
                return "";
            }
            return peer;
        }
        int comma = xForwardedFor.indexOf(",");
        if (comma < 0) {
            return xForwardedFor.trim();
        }
        return xForwardedFor.substring(0, comma).trim();
    }
}

class ApiKeyAuth {
    HashMap<String, String> keyHashes;

    ApiKeyAuth() {
        keyHashes = new HashMap<String, String>();
    }

    static String hashKey(String rawKey) {
        if (rawKey == null) {
            rawKey = "";
        }
        byte[] digest = Sha256.hash(Bytes.fromString(rawKey));
        return CryptoHex.encode(digest);
    }

    void putKey(String keyId, String rawKey) {
        if (keyId == null) {
            return;
        }
        keyHashes.put(keyId, hashKey(rawKey));
    }

    void putHash(String keyId, String sha256Hex) {
        if (keyId == null || sha256Hex == null) {
            return;
        }
        keyHashes.put(keyId, sha256Hex);
    }

    int verify(String keyId, String rawKey) {
        if (keyId == null) {
            return 0;
        }
        String expected = keyHashes.get(keyId);
        if (expected == null) {
            return 0;
        }
        String actual = hashKey(rawKey);
        return ConstantTime.equalsString(expected, actual);
    }
}

class AuthLogin {
    /** Successful login helper: bind principal then rotate session id. */
    static void completeLogin(Session session, UserPrincipal principal) {
        if (session == null || principal == null) {
            return;
        }
        AuthContext.bindPrincipal(session, principal);
        session.rotateId();
        session.save();
    }
}
