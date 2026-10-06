import jnative.web.*;
import jnative.json.*;

/**
 * JWT API demo (0.25) — login returns HS256 JWT; Bearer protects /api/profile.
 * Secret from JWT_SECRET env (dev fallback only for local smoke).
 */
public class App {
    public static void main(String[] args) {
        JwtDemo.key = JwtDemo.loadKey();
        JwtDemo.users = new HashMap<String, String>();
        String hash = Password.hashBlocking("secret");
        JwtDemo.users.put("alice", hash);

        EventLoop loop = new EventLoop();
        WebApp app = new WebApp();
        app.bindEventLoop(loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.futurePost("/api/login", new JwtLoginHandler(loop));
        app.futureGet("/api/profile", new JwtProfileHandler(loop));
        System.out.println("jwt-api listening on 18252");
        System.out.println("Use HTTPS via trusted reverse proxy in production.");
        app.listenWithLoop(18252);
    }
}

class JwtDemo {
    static byte[] key;
    static HashMap<String, String> users;

    static byte[] loadKey() {
        String s = Env.get("JWT_SECRET");
        if (s == null || s.length() < 16) {
            System.out.println("warning: JWT_SECRET unset/short; using ephemeral CSPRNG key");
            return SecureRandom.bytes(32);
        }
        return Bytes.fromString(s);
    }
}

class JwtLoginHandler implements AsyncWebHandler {
    EventLoop loop;

    JwtLoginHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String email = body.getString("email", "");
        String password = body.getString("password", "");
        String hash = JwtDemo.users.get(email);
        if (hash == null || Password.verifyBlocking(password, hash) == 0) {
            throw new UnauthorizedException("invalid credentials");
        }
        JwtClaims claims = new JwtClaims();
        claims.sub = email;
        claims.iss = "jnative-jwt-api";
        claims.aud = "jnative-clients";
        long now = WebUtil.monoMillis() / 1000L;
        claims.iat = now;
        claims.exp = now + 3600L;
        String token = Jwt.signHs256(claims, JwtDemo.key);
        SecurityHeaders.applyDefaults(ctx.response);
        ctx.jsonString("{\"token\":\"" + token + "\",\"tokenType\":\"Bearer\"}");
        return JnFuture.completed(loop, null);
    }
}

class JwtProfileHandler implements AsyncWebHandler {
    EventLoop loop;

    JwtProfileHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        String auth = ctx.request.headers.getOrEmpty("Authorization");
        if (auth.indexOf("Bearer ") != 0) {
            throw new UnauthorizedException("bearer required");
        }
        String token = auth.substringFrom(7);
        JwtClaims claims = Jwt.verifyHs256(token, JwtDemo.key);
        if (claims.iss.equals("jnative-jwt-api") == false) {
            throw new UnauthorizedException("bad issuer");
        }
        if (claims.aud.equals("jnative-clients") == false) {
            throw new ForbiddenException("bad audience");
        }
        SecurityHeaders.applyDefaults(ctx.response);
        ctx.jsonString("{\"sub\":\"" + claims.sub + "\",\"iss\":\"" + claims.iss + "\"}");
        return JnFuture.completed(loop, null);
    }
}
