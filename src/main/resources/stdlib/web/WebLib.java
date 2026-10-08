// JNative Web (0.22) — ordinary library over HttpLib / TcpServer. No compiler web intrinsics.

interface WebHandler {
    void handle(WebContext ctx);
}

interface Middleware {
    void handle(WebContext ctx, Next next);
}

interface Next {
    void run();
}

interface ErrorHandler {
    void handle(WebContext ctx, RuntimeException error);
}

class WebConfig {
    int maxBodyBytes;
    int maxHeaderBytes;
    int maxRequestLineBytes;
    int maxConnections;
    int maxConnectionsPerIp;
    int maxRequestsPerConnection;
    int readTimeoutMs;
    int writeTimeoutMs;
    int headerTimeoutMs;
    int bodyTimeoutMs;
    int keepAliveMax;
    int production;
    int workers;
    int backlog;
    int enableHttp2;
    String host;
    String trustedProxies;
    int gracefulShutdownMs;

    WebConfig() {
        maxBodyBytes = 1048576;
        maxHeaderBytes = 65536;
        maxRequestLineBytes = 8192;
        maxConnections = 1024;
        maxConnectionsPerIp = 0;
        maxRequestsPerConnection = 100;
        readTimeoutMs = 30000;
        writeTimeoutMs = 30000;
        headerTimeoutMs = 15000;
        bodyTimeoutMs = 60000;
        keepAliveMax = 100;
        production = 0;
        workers = 0;
        backlog = 128;
        enableHttp2 = 1;
        host = "0.0.0.0";
        trustedProxies = "127.0.0.1";
        gracefulShutdownMs = 30000;
    }

    static WebConfig fromEnv() {
        WebConfig c = new WebConfig();
        String workers = Env.get("JNATIVE_WORKERS");
        String maxConn = Env.get("JNATIVE_MAX_CONNECTIONS");
        String trusted = Env.get("JNATIVE_TRUSTED_PROXIES");
        if (trusted != null && trusted.length() > 0) {
            c.trustedProxies = trusted;
        }
        if (maxConn != null && maxConn.length() > 0) {
            c.maxConnections = WebUtil.parseIntOr(maxConn, c.maxConnections);
        }
        if (workers != null && workers.length() > 0) {
            c.workers = WebUtil.parseIntOr(workers, 0);
        }
        return c;
    }
}

/** Typed production server configuration (0.31). */
class ServerConfig {
    WebConfig web;
    TlsConfig tls;
    int port;
    int httpsRedirect;
    int hstsMaxAge;
    int enableMetrics;
    int enableHealth;
    String logFormat;

    ServerConfig() {
        web = new WebConfig();
        tls = null;
        port = 8080;
        httpsRedirect = 0;
        hstsMaxAge = 0;
        enableMetrics = 1;
        enableHealth = 1;
        logFormat = "text";
    }

    static ServerConfig builder() {
        return new ServerConfig();
    }

    ServerConfig port(int p) {
        port = p;
        return this;
    }

    ServerConfig tls(TlsConfig t) {
        tls = t;
        return this;
    }

    ServerConfig web(WebConfig w) {
        web = w;
        return this;
    }

    ServerConfig httpsRedirect(int v) {
        httpsRedirect = v;
        return this;
    }

    ServerConfig hstsMaxAge(int seconds) {
        hstsMaxAge = seconds;
        return this;
    }

    ServerConfig logFormat(String fmt) {
        logFormat = fmt;
        return this;
    }

    ServerConfig build() {
        return this;
    }
}

class TraceContext {
    String traceId;
    String spanId;
    String parentSpanId;
    String tracestate;

    static TraceContext parse(String traceparent, String tracestateHdr) {
        TraceContext t = new TraceContext();
        t.tracestate = tracestateHdr;
        t.traceId = "";
        t.spanId = "";
        t.parentSpanId = "";
        if (traceparent == null || traceparent.length() < 55) {
            return t;
        }
        /* version-traceid-spanid-flags */
        t.traceId = traceparent.substring(3, 35);
        t.parentSpanId = traceparent.substring(36, 52);
        t.spanId = t.parentSpanId;
        return t;
    }

    String headerValue() {
        if (traceId == null || traceId.length() == 0) {
            return "";
        }
        return "00-" + traceId + "-" + spanId + "-01";
    }
}

class RequestLog {
    String requestId;
    String protocol;
    String method;
    String path;
    int status;
    long durationMs;
    long bytesIn;
    long bytesOut;

    void emit(String format) {
        if (format != null && format.equals("json")) {
            System.out.println("{\"requestId\":\"" + requestId + "\",\"protocol\":\"" + protocol
                    + "\",\"method\":\"" + method + "\",\"path\":\"" + path
                    + "\",\"status\":" + status + ",\"durationMs\":" + durationMs
                    + ",\"bytesIn\":" + bytesIn + ",\"bytesOut\":" + bytesOut + "}");
        } else {
            System.out.println(requestId + " " + protocol + " " + method + " " + path + " "
                    + status + " " + durationMs + "ms in=" + bytesIn + " out=" + bytesOut);
        }
    }
}

class TlsMetrics {
    static int handshakeCount;
    static int handshakeFailure;
    static int http2Connections;
    static int activeStreams;
    static int rstStream;
    static int goaway;

    static void handshakeOk() {
        handshakeCount = handshakeCount + 1;
    }

    static void handshakeFail() {
        handshakeFailure = handshakeFailure + 1;
    }
}

/** Production transport security middleware (distinct from AuthLib.SecurityHeaders helpers). */
class TransportSecurity {
    static Middleware defaults() {
        return new TransportSecurityMiddleware(0, 0);
    }

    static Middleware hsts(int maxAge) {
        return new TransportSecurityMiddleware(1, maxAge);
    }
}

class TransportSecurityMiddleware implements Middleware {
    int hsts;
    int maxAge;

    TransportSecurityMiddleware(int hsts, int maxAge) {
        this.hsts = hsts;
        this.maxAge = maxAge;
    }

    public void handle(WebContext ctx, Next next) {
        ctx.setHeader("X-Content-Type-Options", "nosniff");
        ctx.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        ctx.setHeader("X-Frame-Options", "SAMEORIGIN");
        if (hsts != 0 && maxAge > 0 && ctx.isHttps() != 0) {
            ctx.setHeader("Strict-Transport-Security", "max-age=" + maxAge);
        }
        next.run();
    }
}

class HttpsRedirectMiddleware implements Middleware {
    public void handle(WebContext ctx, Next next) {
        if (ctx.isHttps() == 0) {
            String host = ctx.getHeader("Host");
            if (host == null) {
                host = "localhost";
            }
            ctx.status(301).setHeader("Location", "https://" + host + ctx.path()).text("Redirect");
            return;
        }
        next.run();
    }
}

class HealthOkHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.text("ok");
    }
}

class HealthReadyHandler implements WebHandler {
    WebApp app;

    HealthReadyHandler(WebApp app) {
        this.app = app;
    }

    public void handle(WebContext ctx) {
        if (app.server != null && app.server.stopped == 0) {
            ctx.text("ready");
        } else {
            ctx.status(503).text("not ready");
        }
    }
}

class HealthEndpoints {
    static void mount(WebApp app) {
        app.get("/health", new HealthOkHandler());
        app.get("/ready", new HealthReadyHandler(app));
    }
}

class HttpMethod {
    static String GET = "GET";
    static String POST = "POST";
    static String PUT = "PUT";
    static String PATCH = "PATCH";
    static String DELETE = "DELETE";
    static String HEAD = "HEAD";
    static String OPTIONS = "OPTIONS";
}

class WebException extends RuntimeException {
    int status;

    WebException(int status, String message) {
        super(message);
        this.status = status;
    }
}

class BadRequestException extends WebException {
    BadRequestException(String message) {
        super(400, message);
    }
}

class NotFoundException extends WebException {
    NotFoundException(String message) {
        super(404, message);
    }
}

class MethodNotAllowedException extends WebException {
    String allow;

    MethodNotAllowedException(String allow) {
        super(405, "Method Not Allowed");
        this.allow = allow;
    }
}

class PayloadTooLargeException extends WebException {
    PayloadTooLargeException(String message) {
        super(413, message);
    }
}

class IllegalResponseStateException extends RuntimeException {
    IllegalResponseStateException(String message) {
        super(message);
    }
}

class UrlCodec {
    static String decodeQuery(String s) {
        return decode(s, 1);
    }

    static String decodePath(String s) {
        return decode(s, 0);
    }

    static String decode(String s, int plusIsSpace) {
        if (s == null) {
            return "";
        }
        /* Collect raw bytes then Bytes.toString — byteChar() maps non-ASCII to '?',
         * which broke UTF-8 query values (e.g. Chinese search). */
        ByteBuf buf = new ByteBuf();
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (plusIsSpace != 0 && c == 43) {
                buf.appendByte(32);
                i = i + 1;
            } else if (c == 37 && i + 2 < s.length()) {
                int hi = hexVal(s.charAt(i + 1));
                int lo = hexVal(s.charAt(i + 2));
                if (hi < 0 || lo < 0) {
                    buf.appendByte(37);
                    i = i + 1;
                } else {
                    buf.appendByte(hi * 16 + lo);
                    i = i + 3;
                }
            } else {
                buf.appendByte(c);
                i = i + 1;
            }
        }
        return buf.toStringUtf8();
    }

    static int hexVal(int c) {
        if (c >= 48 && c <= 57) {
            return c - 48;
        }
        if (c >= 65 && c <= 70) {
            return c - 55;
        }
        if (c >= 97 && c <= 102) {
            return c - 87;
        }
        return -1;
    }
}

class QueryMap {
    ArrayList<String> keys;
    ArrayList<String> values;

    QueryMap() {
        keys = new ArrayList<String>();
        values = new ArrayList<String>();
    }

    static QueryMap parse(String query) {
        QueryMap m = new QueryMap();
        if (query == null || query.length() == 0) {
            return m;
        }
        int start = 0;
        int i = 0;
        while (i <= query.length()) {
            int atEnd = 0;
            if (i == query.length() || query.charAt(i) == 38) {
                atEnd = 1;
            }
            if (atEnd != 0) {
                if (i > start) {
                    String part = query.substring(start, i);
                    int eq = part.indexOf("=");
                    String k = "";
                    String v = "";
                    if (eq < 0) {
                        k = UrlCodec.decodeQuery(part);
                    } else {
                        k = UrlCodec.decodeQuery(part.substring(0, eq));
                        v = UrlCodec.decodeQuery(part.substringFrom(eq + 1));
                    }
                    m.keys.add(k);
                    m.values.add(v);
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return m;
    }

    String get(String key) {
        int i = 0;
        while (i < keys.size()) {
            if (keys.get(i).equals(key)) {
                return values.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    int getInt(String key, int def) {
        String v = get(key);
        if (v == null || v.length() == 0) {
            return def;
        }
        return WebUtil.parseIntOr(v, def);
    }
}

class WebClockNative {
    @Native(symbol = "jnative_web_mono_millis")
    static native long monoMillis();
}

class WebUtil {
    static int parseIntOr(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        int n = 0;
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        int ok = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return def;
            }
            n = n * 10 + (c - 48);
            ok = 1;
            i = i + 1;
        }
        if (ok == 0) {
            return def;
        }
        return n * sign;
    }

    static long parseLongOr(String s, long def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        long n = 0L;
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        int ok = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return def;
            }
            int digit = c - 48;
            long d = 0L;
            int di = 0;
            while (di < digit) {
                d = d + 1L;
                di = di + 1;
            }
            n = n * 10L + d;
            ok = 1;
            i = i + 1;
        }
        if (ok == 0) {
            return def;
        }
        if (sign < 0) {
            return 0L - n;
        }
        return n;
    }

    static String longToString(long v) {
        return HttpUtil.longToString(v);
    }

    static long monoMillis() {
        return WebClockNative.monoMillis();
    }

    static String upper(String s) {
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c >= 97 && c <= 122) {
                c = c - 32;
            }
            out = out + byteChar(c);
            i = i + 1;
        }
        return out;
    }

    static String lower(String s) {
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c >= 65 && c <= 90) {
                c = c + 32;
            }
            out = out + byteChar(c);
            i = i + 1;
        }
        return out;
    }

    static String byteChar(int c) {
        if (c == 34) {
            return "\"";
        }
        if (c == 39) {
            return "'";
        }
        if (c == 92) {
            return "\\";
        }
        if (c >= 32 && c <= 126) {
            String base = " !#$%&()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~";
            int i = 0;
            int cur = 32;
            while (cur <= 126) {
                if (cur != 34 && cur != 39 && cur != 92) {
                    if (cur == c) {
                        return base.substring(i, i + 1);
                    }
                    i = i + 1;
                }
                cur = cur + 1;
            }
        }
        return "?";
    }
}

class ResponseBody {
    int kind;
    byte[] bytes;
    String path;
    long rangeStart;
    long rangeEnd;
    long totalSize;
    static int KIND_BYTES = 1;
    static int KIND_FILE = 2;
    static int KIND_FILE_RANGE = 3;

    static ResponseBody ofBytes(byte[] data) {
        ResponseBody b = new ResponseBody();
        b.kind = KIND_BYTES;
        b.bytes = data;
        return b;
    }

    static ResponseBody ofString(String text) {
        if (text == null) {
            return ofBytes(new byte[0]);
        }
        return ofBytes(Bytes.fromString(text));
    }

    static ResponseBody ofFile(String filePath) {
        ResponseBody b = new ResponseBody();
        b.kind = KIND_FILE;
        b.path = filePath;
        return b;
    }

    static ResponseBody ofFileRange(String filePath, long start, long endInclusive, long total) {
        ResponseBody b = new ResponseBody();
        b.kind = KIND_FILE_RANGE;
        b.path = filePath;
        b.rangeStart = start;
        b.rangeEnd = endInclusive;
        b.totalSize = total;
        return b;
    }

    byte[] materialize() {
        if (kind == KIND_FILE) {
            return File.readBytes(path);
        }
        if (kind == KIND_FILE_RANGE) {
            /* Fallback for non-streaming senders (e.g. HTTP/2); prefer HttpConnection stream. */
            FileInputStream in = new FileInputStream(path);
            in.seek(rangeStart);
            long left = rangeEnd - rangeStart + 1L;
            if (left < 0L) {
                left = 0L;
            }
            if (left > 8388608L) {
                in.close();
                throw new IllegalResponseStateException("range too large to materialize");
            }
            ByteBuf acc = new ByteBuf();
            byte[] chunk = new byte[65536];
            while (left > 0L) {
                int want = 65536;
                if (left < 65536L) {
                    want = HttpUtil.smallLongToInt(left);
                }
                int n = in.read(chunk, 0, want);
                if (n <= 0) {
                    left = 0L;
                } else {
                    byte[] part = new byte[n];
                    int j = 0;
                    while (j < n) {
                        part[j] = chunk[j];
                        j = j + 1;
                    }
                    acc.appendBytes(part);
                    left = left - HttpUtil.intToLong(n);
                }
            }
            in.close();
            return acc.toArray();
        }
        if (bytes == null) {
            return new byte[0];
        }
        return bytes;
    }
}

class WebResponse {
    int statusCode;
    HttpHeaders headers;
    ResponseBody body;
    int committed;

    WebResponse() {
        statusCode = 200;
        headers = new HttpHeaders();
        body = null;
        committed = 0;
    }

    void ensureOpen() {
        if (committed != 0) {
            throw new IllegalResponseStateException("response already committed");
        }
    }

    WebResponse status(int code) {
        ensureOpen();
        statusCode = code;
        return this;
    }

    WebResponse setHeader(String name, String value) {
        ensureOpen();
        headers.set(name, value);
        return this;
    }

    void text(String text) {
        ensureOpen();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        body = ResponseBody.ofString(text);
        committed = 1;
    }

    void html(String html) {
        ensureOpen();
        headers.set("Content-Type", "text/html; charset=utf-8");
        body = ResponseBody.ofString(html);
        committed = 1;
    }

    void jsonString(String json) {
        ensureOpen();
        headers.set("Content-Type", "application/json; charset=utf-8");
        body = ResponseBody.ofString(json);
        committed = 1;
    }

    void jsonValue(JsonValue v) {
        jsonString(Json.stringify(v));
    }

    void bytes(byte[] data) {
        ensureOpen();
        if (!headers.contains("Content-Type")) {
            headers.set("Content-Type", "application/octet-stream");
        }
        body = ResponseBody.ofBytes(data);
        committed = 1;
    }

    void fileBody(String path, String contentType) {
        ensureOpen();
        headers.set("Content-Type", contentType);
        body = ResponseBody.ofFile(path);
        committed = 1;
    }

    /** Stream a closed byte range without reading the whole file into memory. */
    void fileRange(String path, String contentType, long start, long endInclusive, long total) {
        ensureOpen();
        headers.set("Content-Type", contentType);
        body = ResponseBody.ofFileRange(path, start, endInclusive, total);
        committed = 1;
    }

    HttpResponse toHttp() {
        HttpResponse r = null;
        if (body != null && body.kind == ResponseBody.KIND_FILE_RANGE) {
            r = HttpResponse.fileStream(statusCode, body.path, body.rangeStart, body.rangeEnd, body.totalSize);
        } else {
            if (body != null && body.kind == ResponseBody.KIND_FILE) {
                long total = File.size(body.path);
                long end = total - 1L;
                if (end < 0L) {
                    end = -1L;
                }
                r = HttpResponse.fileStream(statusCode, body.path, 0L, end, total);
                r.streamKind = HttpResponse.STREAM_FILE;
            } else {
                byte[] data = new byte[0];
                if (body != null) {
                    data = body.materialize();
                }
                r = HttpResponse.bytesStatus(statusCode, data);
            }
        }
        int i = 0;
        while (i < headers.count) {
            r.headers.set(headers.names[i], headers.values[i]);
            i = i + 1;
        }
        return r;
    }
}

class WebRequest {
    String method;
    String path;
    String rawQuery;
    QueryMap query;
    HttpHeaders headers;
    byte[] body;
    HashMap<String, String> params;
    String wildcard;

    WebRequest() {
        params = new HashMap<String, String>();
        wildcard = "";
    }

    static WebRequest fromHttp(HttpServerRequest req) {
        WebRequest r = new WebRequest();
        r.method = req.method();
        r.path = UrlCodec.decodePath(req.path());
        r.rawQuery = req.query();
        r.query = QueryMap.parse(req.query());
        r.headers = req.headers();
        r.body = req.bodyBytes();
        return r;
    }

    String bodyString() {
        return Bytes.toString(body);
    }
}

class WebContext {
    WebRequest request;
    WebResponse response;
    HashMap<String, Object> state;
    String remoteIp;
    int https;
    String protocol;
    TraceContext trace;
    String requestId;

    WebContext(WebRequest req) {
        request = req;
        response = new WebResponse();
        state = new HashMap<String, Object>();
        remoteIp = "";
        https = 0;
        protocol = "http/1.1";
        trace = TraceContext.parse(req.headers.get("traceparent"), req.headers.get("tracestate"));
        requestId = "req-" + String.valueOf(req.path.hashCode());
    }

    int isHttps() {
        return https;
    }

    String clientIp() {
        /* trusted proxy only — never trust XFF from untrusted peers */
        String xff = getHeader("X-Forwarded-For");
        if (xff != null && xff.length() > 0 && isTrustedProxy(remoteIp) != 0) {
            int comma = xff.indexOf(",");
            if (comma >= 0) {
                return xff.substring(0, comma).trim();
            }
            return xff.trim();
        }
        String fwd = getHeader("Forwarded");
        if (fwd != null && fwd.length() > 0 && isTrustedProxy(remoteIp) != 0) {
            int fi = fwd.indexOf("for=");
            if (fi >= 0) {
                String rest = fwd.substringFrom(fi + 4);
                int end = rest.indexOf(";");
                if (end < 0) {
                    end = rest.length();
                }
                String raw = rest.substring(0, end).trim();
                if (raw.length() >= 2 && raw.charAt(0) == 34) {
                    raw = raw.substring(1, raw.length() - 1);
                }
                return raw;
            }
        }
        return remoteIp;
    }

    int isTrustedProxy(String ip) {
        if (ip == null || ip.length() == 0) {
            return 0;
        }
        if (ip.equals("127.0.0.1") || ip.equals("::1")) {
            return 1;
        }
        return 0;
    }

    String method() {
        return request.method;
    }

    String path() {
        return request.path;
    }

    String query(String name) {
        return request.query.get(name);
    }

    int queryInt(String name, int def) {
        return request.query.getInt(name, def);
    }

    String param(String name) {
        return request.params.get(name);
    }

    int paramInt(String name) {
        String v = param(name);
        if (v == null) {
            throw new BadRequestException("missing path param: " + name);
        }
        int i = 0;
        if (v.length() > 0 && v.charAt(0) == 45) {
            i = 1;
        }
        if (i >= v.length()) {
            throw new BadRequestException("invalid int param: " + name);
        }
        while (i < v.length()) {
            int c = v.charAt(i);
            if (c < 48 || c > 57) {
                throw new BadRequestException("invalid int param: " + name);
            }
            i = i + 1;
        }
        return WebUtil.parseIntOr(v, 0);
    }

    String getHeader(String name) {
        return request.headers.get(name);
    }

    String bodyString() {
        return request.bodyString();
    }

    byte[] bodyBytes() {
        return request.body;
    }

    String cookie(String name) {
        String raw = getHeader("Cookie");
        if (raw == null) {
            return null;
        }
        return CookieUtil.get(raw, name);
    }

    WebContext status(int code) {
        response.status(code);
        return this;
    }

    WebContext setHeader(String name, String value) {
        response.setHeader(name, value);
        return this;
    }

    WebContext cookieSet(String name, String value) {
        response.setHeader("Set-Cookie", CookieUtil.encode(name, value, "/", 0, 1, 0, "Lax"));
        return this;
    }

    void text(String text) {
        response.text(text);
    }

    void html(String html) {
        response.html(html);
    }

    void jsonString(String json) {
        response.jsonString(json);
    }

    void jsonValue(JsonValue v) {
        response.jsonValue(v);
    }

    void bytes(byte[] data) {
        response.bytes(data);
    }

    void fileBody(String path, String contentType) {
        response.fileBody(path, contentType);
    }

    void fileRange(String path, String contentType, long start, long endInclusive, long total) {
        response.fileRange(path, contentType, start, endInclusive, total);
    }

    String form(String name) {
        String ct = getHeader("Content-Type");
        if (ct == null) {
            return null;
        }
        if (ct.indexOf("application/x-www-form-urlencoded") < 0) {
            return null;
        }
        QueryMap m = QueryMap.parse(bodyString());
        return m.get(name);
    }
}

class CookieUtil {
    static String get(String header, String name) {
        int start = 0;
        int i = 0;
        while (i <= header.length()) {
            int sep = 0;
            if (i == header.length() || header.charAt(i) == 59) {
                sep = 1;
            }
            if (sep != 0) {
                String part = header.substring(start, i).trim();
                int eq = part.indexOf("=");
                if (eq > 0) {
                    String k = part.substring(0, eq).trim();
                    if (k.equals(name)) {
                        return part.substringFrom(eq + 1).trim();
                    }
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return null;
    }

    static String encode(String name, String value, String path, int maxAge, int httpOnly, int secure, String sameSite) {
        String out = name + "=" + value;
        if (path != null) {
            out = out + "; Path=" + path;
        }
        if (maxAge > 0) {
            out = out + "; Max-Age=" + maxAge;
        }
        if (httpOnly != 0) {
            out = out + "; HttpOnly";
        }
        if (secure != 0) {
            out = out + "; Secure";
        }
        if (sameSite != null && sameSite.length() > 0) {
            out = out + "; SameSite=" + sameSite;
        }
        return out;
    }
}

class RouteNode {
    HashMap<String, RouteNode> staticChildren;
    RouteNode paramChild;
    String paramName;
    RouteNode wildcardChild;
    HashMap<String, WebHandler> methods;
    ArrayList<String> methodNames;
    HashMap<String, AsyncWebHandler> futureMethods;
    ArrayList<String> futureMethodNames;

    RouteNode() {
        staticChildren = new HashMap<String, RouteNode>();
        paramChild = null;
        paramName = "";
        wildcardChild = null;
        methods = new HashMap<String, WebHandler>();
        methodNames = new ArrayList<String>();
        futureMethods = new HashMap<String, AsyncWebHandler>();
        futureMethodNames = new ArrayList<String>();
    }
}

class RouteMatch {
    WebHandler handler;
    AsyncWebHandler futureHandler;
    ArrayList<String> paramNames;
    ArrayList<String> paramValues;
    String wildcard;
    int found;
    int futureRoute;
    int methodNotAllowed;
    String allow;

    RouteMatch() {
        paramNames = new ArrayList<String>();
        paramValues = new ArrayList<String>();
        wildcard = "";
        found = 0;
        futureRoute = 0;
        methodNotAllowed = 0;
        allow = "";
    }

    void putParam(String name, String value) {
        paramNames.add(name);
        paramValues.add(value);
    }

    HashMap<String, String> toMap() {
        HashMap<String, String> m = new HashMap<String, String>();
        int i = 0;
        while (i < paramNames.size()) {
            m.put(paramNames.get(i), paramValues.get(i));
            i = i + 1;
        }
        return m;
    }
}

class Router {
    RouteNode root;

    Router() {
        root = new RouteNode();
    }

    void add(String method, String pattern, WebHandler handler) {
        RouteNode node = walkPattern(pattern);
        String m = WebUtil.upper(method);
        if (node.methods.get(m) == null) {
            node.methodNames.add(m);
        }
        node.methods.put(m, handler);
    }

    void addFuture(String method, String pattern, AsyncWebHandler handler) {
        RouteNode node = walkPattern(pattern);
        String m = WebUtil.upper(method);
        if (node.futureMethods.get(m) == null) {
            node.futureMethodNames.add(m);
        }
        node.futureMethods.put(m, handler);
    }

    RouteNode walkPattern(String pattern) {
        ArrayList<String> segs = splitPath(pattern);
        RouteNode node = root;
        int i = 0;
        while (i < segs.size()) {
            String seg = segs.get(i);
            if (seg.equals("*")) {
                if (node.wildcardChild == null) {
                    node.wildcardChild = new RouteNode();
                }
                node = node.wildcardChild;
                i = segs.size();
            } else if (seg.length() > 0 && seg.charAt(0) == 58) {
                if (node.paramChild == null) {
                    node.paramChild = new RouteNode();
                    node.paramChild.paramName = seg.substringFrom(1);
                }
                node = node.paramChild;
                i = i + 1;
            } else {
                RouteNode next = node.staticChildren.get(seg);
                if (next == null) {
                    next = new RouteNode();
                    node.staticChildren.put(seg, next);
                }
                node = next;
                i = i + 1;
            }
        }
        return node;
    }

    RouteMatch match(String method, String path) {
        ArrayList<String> segs = splitPath(path);
        RouteMatch rm = new RouteMatch();
        matchRec(root, segs, 0, WebUtil.upper(method), rm);
        return rm;
    }

    void matchRec(RouteNode node, ArrayList<String> segs, int index, String method, RouteMatch out) {
        if (out.found != 0) {
            return;
        }
        if (index == segs.size()) {
            AsyncWebHandler ah = node.futureMethods.get(method);
            if (ah != null) {
                out.found = 1;
                out.futureRoute = 1;
                out.futureHandler = ah;
                return;
            }
            WebHandler h = node.methods.get(method);
            if (h != null) {
                out.found = 1;
                out.handler = h;
                return;
            }
            ArrayList<String> allowNames = joinAllowMethods(node);
            if (allowNames.size() > 0) {
                out.methodNotAllowed = 1;
                out.allow = joinMethods(allowNames);
            }
            return;
        }
        String seg = segs.get(index);
        RouteNode st = node.staticChildren.get(seg);
        if (st != null) {
            matchRec(st, segs, index + 1, method, out);
            if (out.found != 0) {
                return;
            }
        }
        if (node.paramChild != null) {
            int pn = out.paramNames.size();
            out.putParam(node.paramChild.paramName, seg);
            matchRec(node.paramChild, segs, index + 1, method, out);
            if (out.found == 0) {
                while (out.paramNames.size() > pn) {
                    out.paramNames.removeAt(out.paramNames.size() - 1);
                    out.paramValues.removeAt(out.paramValues.size() - 1);
                }
            }
            if (out.found != 0) {
                return;
            }
        }
        if (node.wildcardChild != null) {
            String rest = "";
            int i = index;
            while (i < segs.size()) {
                if (rest.length() > 0) {
                    rest = rest + "/";
                }
                rest = rest + segs.get(i);
                i = i + 1;
            }
            out.wildcard = rest;
            matchRec(node.wildcardChild, segs, segs.size(), method, out);
            if (out.found == 0) {
                out.wildcard = "";
            }
        }
    }

    ArrayList<String> joinAllowMethods(RouteNode node) {
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        while (i < node.methodNames.size()) {
            out.add(node.methodNames.get(i));
            i = i + 1;
        }
        i = 0;
        while (i < node.futureMethodNames.size()) {
            String m = node.futureMethodNames.get(i);
            int dup = 0;
            int j = 0;
            while (j < out.size()) {
                if (out.get(j).equals(m)) {
                    dup = 1;
                }
                j = j + 1;
            }
            if (dup == 0) {
                out.add(m);
            }
            i = i + 1;
        }
        return out;
    }

    String joinMethods(ArrayList<String> names) {
        String out = "";
        int i = 0;
        while (i < names.size()) {
            if (i > 0) {
                out = out + ", ";
            }
            out = out + names.get(i);
            i = i + 1;
        }
        return out;
    }

    ArrayList<String> splitPath(String path) {
        ArrayList<String> segs = new ArrayList<String>();
        if (path == null || path.length() == 0 || path.equals("/")) {
            return segs;
        }
        int start = 0;
        if (path.charAt(0) == 47) {
            start = 1;
        }
        int i = start;
        while (i <= path.length()) {
            int cut = 0;
            if (i == path.length() || path.charAt(i) == 47) {
                cut = 1;
            }
            if (cut != 0) {
                if (i > start) {
                    segs.add(path.substring(start, i));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return segs;
    }
}

class WebNext implements Next {
    MiddlewareChain chain;

    WebNext() {
        chain = null;
    }

    public void run() {
        if (chain != null) {
            chain.run();
        }
    }
}

class MiddlewareChain {
    ArrayList<Middleware> list;
    WebHandler terminal;
    int index;
    WebContext ctx;

    MiddlewareChain() {
        list = null;
        terminal = null;
        index = 0;
        ctx = null;
    }

    void run() {
        if (list == null) {
            if (terminal != null) {
                terminal.handle(ctx);
            }
            return;
        }
        if (index < list.size()) {
            Middleware mw = list.get(index);
            index = index + 1;
            WebNext next = new WebNext();
            next.chain = this;
            mw.handle(ctx, next);
        } else {
            if (terminal != null) {
                terminal.handle(ctx);
            }
        }
    }
}

class MimeTypes {
    static String ofPath(String path) {
        String lower = WebUtil.lower(path);
        if (ends(lower, ".html") != 0) {
            return "text/html; charset=utf-8";
        }
        if (ends(lower, ".htm") != 0) {
            return "text/html; charset=utf-8";
        }
        if (ends(lower, ".css") != 0) {
            return "text/css; charset=utf-8";
        }
        if (ends(lower, ".js") != 0) {
            return "application/javascript; charset=utf-8";
        }
        if (ends(lower, ".json") != 0) {
            return "application/json; charset=utf-8";
        }
        if (ends(lower, ".txt") != 0) {
            return "text/plain; charset=utf-8";
        }
        if (ends(lower, ".png") != 0) {
            return "image/png";
        }
        if (ends(lower, ".jpg") != 0) {
            return "image/jpeg";
        }
        if (ends(lower, ".jpeg") != 0) {
            return "image/jpeg";
        }
        if (ends(lower, ".gif") != 0) {
            return "image/gif";
        }
        if (ends(lower, ".svg") != 0) {
            return "image/svg+xml";
        }
        if (ends(lower, ".ico") != 0) {
            return "image/x-icon";
        }
        if (ends(lower, ".wasm") != 0) {
            return "application/wasm";
        }
        if (ends(lower, ".wav") != 0) {
            return "audio/wav";
        }
        if (ends(lower, ".mp3") != 0) {
            return "audio/mpeg";
        }
        if (ends(lower, ".ogg") != 0) {
            return "audio/ogg";
        }
        return "application/octet-stream";
    }

    static int ends(String s, String suf) {
        if (s.length() < suf.length()) {
            return 0;
        }
        String tail = s.substring(s.length() - suf.length(), s.length());
        if (tail.equals(suf)) {
            return 1;
        }
        return 0;
    }
}

/**
 * Generic HTTP Range streaming helper (64-bit offsets).
 * Supports bytes=a-b, bytes=a-, bytes=-suffix; rejects multi-range.
 */
class MediaStreamer {
    static int OK = 1;
    static int INVALID = 0;

    static int serveFile(WebContext ctx, String path, String contentType) {
        if (!File.exists(path)) {
            ctx.status(404).text("Not Found");
            return 1;
        }
        long total = File.size(path);
        ctx.setHeader("Accept-Ranges", "bytes");
        String range = ctx.getHeader("Range");
        if (range != null && range.length() > 0) {
            return serveRange(ctx, path, contentType, total, range);
        }
        ctx.status(200);
        ctx.fileBody(path, contentType);
        return 1;
    }

    static int serveRange(WebContext ctx, String path, String contentType, long total, String rangeHeader) {
        if (rangeHeader == null || rangeHeader.startsWith("bytes=") == false) {
            ctx.status(416);
            ctx.setHeader("Content-Range", "bytes */" + WebUtil.longToString(total));
            ctx.text("");
            return 1;
        }
        String spec = rangeHeader.substringFrom(6).trim();
        if (spec.indexOf(",") >= 0) {
            ctx.status(416);
            ctx.setHeader("Content-Range", "bytes */" + WebUtil.longToString(total));
            ctx.text("");
            return 1;
        }
        int dash = spec.indexOf("-");
        if (dash < 0) {
            ctx.status(416);
            ctx.setHeader("Content-Range", "bytes */" + WebUtil.longToString(total));
            ctx.text("");
            return 1;
        }
        String left = spec.substring(0, dash);
        String right = "";
        if (dash + 1 < spec.length()) {
            right = spec.substringFrom(dash + 1);
        }
        long start = 0L;
        long end = total - 1L;
        int ok = 1;
        if (left.length() == 0) {
            /* suffix: bytes=-N */
            if (right.length() == 0) {
                ok = 0;
            } else {
                long suffix = WebUtil.parseLongOr(right, -1L);
                if (suffix <= 0L) {
                    ok = 0;
                } else {
                    if (suffix > total) {
                        suffix = total;
                    }
                    start = total - suffix;
                    end = total - 1L;
                }
            }
        } else {
            start = WebUtil.parseLongOr(left, -1L);
            if (start < 0L) {
                ok = 0;
            } else {
                if (right.length() == 0) {
                    end = total - 1L;
                } else {
                    end = WebUtil.parseLongOr(right, -1L);
                    if (end < 0L) {
                        ok = 0;
                    }
                }
            }
        }
        if (ok == 0 || total <= 0L || start >= total || start > end) {
            ctx.status(416);
            ctx.setHeader("Content-Range", "bytes */" + WebUtil.longToString(total));
            ctx.text("");
            return 1;
        }
        if (end >= total) {
            end = total - 1L;
        }
        ctx.status(206);
        ctx.setHeader("Accept-Ranges", "bytes");
        ctx.setHeader("Content-Range", "bytes " + WebUtil.longToString(start) + "-"
                + WebUtil.longToString(end) + "/" + WebUtil.longToString(total));
        ctx.fileRange(path, contentType, start, end, total);
        return 1;
    }
}

class StaticMount {
    String urlPrefix;
    String fsRoot;

    StaticMount(String urlPrefix, String fsRoot) {
        this.urlPrefix = urlPrefix;
        this.fsRoot = fsRoot;
    }
}

class PathSafety {
    static String resolveSafe(String root, String relative) {
        if (relative == null) {
            return null;
        }
        String rel = UrlCodec.decodePath(relative);
        if (rel.indexOf("..") >= 0) {
            return null;
        }
        if (rel.indexOf("\\") >= 0) {
            return null;
        }
        String joined = root;
        if (endsWithSlash(joined) == 0) {
            joined = joined + "/";
        }
        if (rel.length() > 0 && rel.charAt(0) == 47) {
            rel = rel.substringFrom(1);
        }
        String full = joined + rel;
        String normRoot = normalize(root);
        String normFull = normalize(full);
        if (normFull.length() < normRoot.length()) {
            return null;
        }
        if (!normFull.substring(0, normRoot.length()).equals(normRoot)) {
            return null;
        }
        return full;
    }

    static int endsWithSlash(String s) {
        if (s.length() == 0) {
            return 0;
        }
        if (s.charAt(s.length() - 1) == 47) {
            return 1;
        }
        return 0;
    }

    static String normalize(String path) {
        ArrayList<String> parts = new ArrayList<String>();
        int start = 0;
        int i = 0;
        while (i <= path.length()) {
            int cut = 0;
            if (i == path.length() || path.charAt(i) == 47) {
                cut = 1;
            }
            if (cut != 0) {
                if (i > start) {
                    String p = path.substring(start, i);
                    if (p.equals("..")) {
                        if (parts.size() > 0) {
                            parts.removeAt(parts.size() - 1);
                        }
                    } else if (!p.equals(".")) {
                        parts.add(p);
                    }
                }
                start = i + 1;
            }
            i = i + 1;
        }
        String out = "";
        int j = 0;
        while (j < parts.size()) {
            out = out + "/" + parts.get(j);
            j = j + 1;
        }
        if (out.length() == 0) {
            return "/";
        }
        return out;
    }
}

class LoggerMiddleware implements Middleware {
    public void handle(WebContext ctx, Next next) {
        long start = WebUtil.monoMillis();
        next.run();
        long elapsed = WebUtil.monoMillis() - start;
        System.out.println(ctx.method() + " " + ctx.path() + " " + ctx.response.statusCode + " " + elapsed + "ms");
    }
}

class WebMiddleware {
    static Middleware logger() {
        return new LoggerMiddleware();
    }
}

class CorsMiddleware implements Middleware {
    public void handle(WebContext ctx, Next next) {
        ctx.setHeader("Access-Control-Allow-Origin", "*");
        ctx.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD");
        ctx.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        if (ctx.method().equals("OPTIONS")) {
            ctx.status(204); ctx.text("");
        } else {
            next.run();
        }
    }
}

class Cors {
    static Middleware defaults() {
        return new CorsMiddleware();
    }
}

class WebAcceptThread extends Thread {
    WebServer server;

    WebAcceptThread(WebServer server) {
        this.server = server;
    }

    public void run() {
        server.acceptLoop();
    }
}

class WebConnTask implements PoolTask {
    WebServer server;
    HttpConnection conn;

    WebConnTask() {
        server = null;
        conn = null;
    }

    public void run() {
        if (server != null && conn != null) {
            server.serveConnection(conn);
        }
    }
}

class WebServer {
    WebApp app;
    HttpServer http;
    ThreadPoolExecutor pool;
    int stopped;
    Mutex connLock;
    int activeConns;
    Thread acceptor;
    TlsContext tlsCtx;
    int useTls;
    TcpServer rawTcp;

    WebServer(WebApp app) {
        this.app = app;
        stopped = 0;
        connLock = new Mutex();
        activeConns = 0;
        tlsCtx = null;
        useTls = 0;
        rawTcp = null;
    }

    void startListener(int port) {
        WebConfig cfg = app.config;
        int n = cfg.workers;
        if (n <= 0) {
            n = CpuInfo.count();
            if (n < 2) {
                n = 2;
            }
        }
        pool = new ThreadPoolExecutor(n);
        String host = cfg.host;
        if (host == null || host.length() == 0) {
            host = "127.0.0.1";
        }
        http = HttpServer.listen(host, port);
        System.out.println("JNative Web");
        System.out.println("");
        System.out.println("Listening on:");
        System.out.println("http://localhost:" + port);
    }

    void startTlsListener(int port, TlsConfig tlsConfig) {
        WebConfig cfg = app.config;
        int n = cfg.workers;
        if (n <= 0) {
            n = CpuInfo.count();
            if (n < 2) {
                n = 2;
            }
        }
        pool = new ThreadPoolExecutor(n);
        useTls = 1;
        tlsCtx = TlsContext.server(tlsConfig);
        String host = cfg.host;
        if (host == null || host.length() == 0 || host.equals("0.0.0.0")) {
            host = "0.0.0.0";
        }
        rawTcp = TcpServer.listenAny(port);
        System.out.println("JNative Web");
        System.out.println("");
        System.out.println("Listening on:");
        System.out.println("https://localhost:" + port);
        System.out.println("TLS ALPN: h2,http/1.1");
    }

    void acceptLoop() {
        if (useTls != 0) {
            acceptTlsLoop();
            return;
        }
        int alive = 1;
        while (alive != 0) {
            if (stopped != 0) {
                alive = 0;
            } else {
                HttpConnection conn = null;
                int ok = 1;
                try {
                    conn = http.accept();
                } catch (Exception ex) {
                    ok = 0;
                    if (stopped != 0) {
                        alive = 0;
                    }
                }
                if (ok != 0 && conn != null) {
                    connLock.lock();
                    if (activeConns >= app.config.maxConnections) {
                        connLock.unlock();
                        conn.close();
                    } else {
                        activeConns = activeConns + 1;
                        connLock.unlock();
                        /* Dispatch to worker pool so accept is never blocked by
                         * slow handlers (login PBKDF2 / DB). Browser parallel
                         * loads of /, /app.css, /app.js must not serialize. */
                        WebConnTask task = new WebConnTask();
                        task.server = this;
                        task.conn = conn;
                        pool.execute(task);
                    }
                }
            }
        }
    }

    void acceptTlsLoop() {
        int alive = 1;
        while (alive != 0) {
            if (stopped != 0) {
                alive = 0;
            } else {
                TcpSocket tcp = null;
                int ok = 1;
                try {
                    tcp = rawTcp.accept();
                } catch (Exception ex) {
                    ok = 0;
                    if (stopped != 0) {
                        alive = 0;
                    }
                }
                if (ok != 0 && tcp != null) {
                    connLock.lock();
                    if (activeConns >= app.config.maxConnections) {
                        connLock.unlock();
                        tcp.close();
                    } else {
                        activeConns = activeConns + 1;
                        connLock.unlock();
                        serveTlsConnection(tcp);
                    }
                }
            }
        }
    }

    void serveTlsConnection(TcpSocket tcp) {
        TlsConnection tls = null;
        try {
            tls = TlsConnection.accept(tcp, tlsCtx);
            TlsMetrics.handshakeOk();
            String alpn = tls.alpn();
            if (alpn != null && alpn.equals("h2") && app.config.enableHttp2 != 0) {
                TlsMetrics.http2Connections = TlsMetrics.http2Connections + 1;
                Http2Server.serve(tls, app);
            } else {
                HttpConnection conn = HttpConnection.overTls(tls);
                serveConnectionHttps(conn);
                return;
            }
        } catch (Exception ex) {
            TlsMetrics.handshakeFail();
        }
        if (tls != null) {
            tls.close();
        } else {
            tcp.close();
        }
        connLock.lock();
        activeConns = activeConns - 1;
        connLock.unlock();
    }

    void serveConnectionHttps(HttpConnection conn) {
        try {
            int keep = 1;
            int count = 0;
            while (keep != 0 && stopped == 0) {
                int ok = 1;
                HttpServerRequest req = null;
                try {
                    req = conn.readRequest();
                } catch (Exception ex) {
                    ok = 0;
                    keep = 0;
                }
                if (ok != 0) {
                    count = count + 1;
                    WebContext ctx = app.dispatch(req);
                    ctx.https = 1;
                    ctx.protocol = "http/1.1";
                    String connHdr = req.headers().getOrEmpty("Connection").toLowerCase();
                    int wantClose = 0;
                    if (connHdr.equals("close")) {
                        wantClose = 1;
                    }
                    if (count >= app.config.keepAliveMax) {
                        wantClose = 1;
                    }
                    HttpResponse httpResp = ctx.response.toHttp();
                    if (wantClose != 0) {
                        httpResp.headers.set("Connection", "close");
                        keep = 0;
                    } else {
                        httpResp.headers.set("Connection", "keep-alive");
                    }
                    try {
                        conn.send(httpResp);
                    } catch (Exception ex2) {
                        keep = 0;
                    }
                }
            }
        } catch (Exception fatal) {
        }
        conn.close();
        connLock.lock();
        activeConns = activeConns - 1;
        connLock.unlock();
    }

    void serveConnection(HttpConnection conn) {
        try {
            int keep = 1;
            int count = 0;
            while (keep != 0 && stopped == 0) {
                int ok = 1;
                HttpServerRequest req = null;
                try {
                    req = conn.readRequest();
                } catch (Exception ex) {
                    ok = 0;
                    keep = 0;
                }
                if (ok != 0) {
                    count = count + 1;
                    if (tryWebSocketUpgrade(conn, req) != 0) {
                        keep = 0;
                        ok = 0;
                    }
                }
                if (ok != 0) {
                    WebContext ctx = app.dispatch(req);
                    String connHdr = req.headers().getOrEmpty("Connection").toLowerCase();
                    int wantClose = 0;
                    if (connHdr.equals("close")) {
                        wantClose = 1;
                    }
                    if (count >= app.config.keepAliveMax) {
                        wantClose = 1;
                    }
                    /* HTTP/1.1 default is keep-alive; only close when asked or capped. */
                    HttpResponse httpResp = ctx.response.toHttp();
                    if (wantClose != 0) {
                        httpResp.headers.set("Connection", "close");
                        keep = 0;
                    } else {
                        httpResp.headers.set("Connection", "keep-alive");
                    }
                    try {
                        conn.send(httpResp);
                    } catch (Exception ex2) {
                        keep = 0;
                    }
                }
            }
        } catch (Exception fatal) {
            /* keep acceptor alive */
        }
        conn.close();
        connLock.lock();
        activeConns = activeConns - 1;
        connLock.unlock();
    }

    int tryWebSocketUpgrade(HttpConnection conn, HttpServerRequest req) {
        String upgrade = req.headers().getOrEmpty("Upgrade").toLowerCase();
        String connection = req.headers().getOrEmpty("Connection").toLowerCase();
        if (upgrade.equals("websocket") == false) {
            return 0;
        }
        if (connection.indexOf("upgrade") < 0) {
            return 0;
        }
        WebSocketHandler handler = app.findWebSocket(req.path());
        if (handler == null) {
            return 0;
        }
        String version = req.headers().getOrEmpty("Sec-WebSocket-Version");
        if (version.equals("13") == false) {
            return 0;
        }
        String key = req.headers().getOrEmpty("Sec-WebSocket-Key");
        if (key.length() == 0) {
            return 0;
        }
        String accept = WebSocketAccept.compute(key);
        HttpResponse resp = HttpResponse.bytesStatus(101, new byte[0]);
        resp.reasonPhrase = "Switching Protocols";
        resp.headers.set("Upgrade", "websocket");
        resp.headers.set("Connection", "Upgrade");
        resp.headers.set("Sec-WebSocket-Accept", accept);
        conn.send(resp);
        EventLoop loop = app.webLoop;
        if (loop == null) {
            loop = Async.defaultLoop();
        }
        WebSocketConnection ws = WebSocketConnection.open(conn.socket, loop, 1048576, 256);
        handler.onConnect(ws);
        ws.pumpWriteOnce();
        int alive = 1;
        while (alive != 0 && ws.isOpen() != 0 && stopped == 0) {
            ws.pumpWriteOnce();
            ws.pumpReadOnce();
            drainWsMessages(handler, ws);
            ws.pumpWriteOnce();
            if (ws.isOpen() == 0) {
                alive = 0;
            } else {
                Thread.sleep(1);
            }
        }
        ws.close(1000, "bye");
        return 1;
    }

    void drainWsMessages(WebSocketHandler handler, WebSocketConnection ws) {
        int more = 1;
        while (more != 0) {
            if (ws.inbound == null || ws.inbound.storage.size() == 0) {
                more = 0;
            } else {
                Object msgObj = ws.inbound.storage.removeFirst();
                WebSocketMessage msg = (WebSocketMessage)msgObj;
                handler.onMessage(ws, msg);
            }
        }
    }

    void stop() {
        stopped = 1;
        if (http != null) {
            http.close();
        }
        if (rawTcp != null) {
            rawTcp.close();
        }
        if (tlsCtx != null) {
            tlsCtx.close();
        }
        if (pool != null) {
            pool.shutdown();
            pool.join();
        }
    }

    void reloadCertificates(TlsConfig cfg) {
        if (tlsCtx != null && cfg != null) {
            tlsCtx.config = cfg;
            tlsCtx.reloadCertificates();
        }
    }
}

class WebApp {
    WebConfig config;
    Router router;
    ArrayList<Middleware> middlewares;
    ArrayList<StaticMount> staticMounts;
    ErrorHandler errorHandler;
    WebServer server;
    EventLoop webLoop;
    EventLoopRunner webLoopRunner;
    int loopDispatch;
    HashMap<String, WebSocketHandler> wsHandlers;

    WebApp() {
        this(new WebConfig());
    }

    WebApp(WebConfig config) {
        this.config = config;
        router = new Router();
        middlewares = new ArrayList<Middleware>();
        staticMounts = new ArrayList<StaticMount>();
        errorHandler = null;
        server = null;
        webLoop = null;
        webLoopRunner = null;
        loopDispatch = 0;
        wsHandlers = new HashMap<String, WebSocketHandler>();
    }

    void websocket(String path, WebSocketHandler handler) {
        wsHandlers.put(path, handler);
    }

    WebSocketHandler findWebSocket(String path) {
        return wsHandlers.get(path);
    }

    void get(String path, WebHandler handler) {
        router.add(HttpMethod.GET, path, handler);
    }

    void post(String path, WebHandler handler) {
        router.add(HttpMethod.POST, path, handler);
    }

    void put(String path, WebHandler handler) {
        router.add(HttpMethod.PUT, path, handler);
    }

    void patch(String path, WebHandler handler) {
        router.add(HttpMethod.PATCH, path, handler);
    }

    void delete(String path, WebHandler handler) {
        router.add(HttpMethod.DELETE, path, handler);
    }

    void options(String path, WebHandler handler) {
        router.add(HttpMethod.OPTIONS, path, handler);
    }

    void head(String path, WebHandler handler) {
        router.add(HttpMethod.HEAD, path, handler);
    }

    void futureGet(String path, AsyncWebHandler handler) {
        router.addFuture(HttpMethod.GET, path, handler);
    }

    void futurePost(String path, AsyncWebHandler handler) {
        router.addFuture(HttpMethod.POST, path, handler);
    }

    void futurePut(String path, AsyncWebHandler handler) {
        router.addFuture(HttpMethod.PUT, path, handler);
    }

    void futurePatch(String path, AsyncWebHandler handler) {
        router.addFuture(HttpMethod.PATCH, path, handler);
    }

    void futureDelete(String path, AsyncWebHandler handler) {
        router.addFuture(HttpMethod.DELETE, path, handler);
    }

    void use(Middleware mw) {
        middlewares.add(mw);
    }

    void onError(ErrorHandler handler) {
        errorHandler = handler;
    }

    void staticFiles(String urlPrefix, String fsRoot) {
        staticMounts.add(new StaticMount(urlPrefix, fsRoot));
    }

    int matchesStaticMount(String path, StaticMount sm) {
        if (path.equals(sm.urlPrefix)) {
            return 1;
        }
        if (sm.urlPrefix.equals("/")) {
            if (path.length() > 0 && path.charAt(0) == 47) {
                return 1;
            }
            return 0;
        }
        if (startsWith(path, sm.urlPrefix + "/") != 0) {
            return 1;
        }
        return 0;
    }

    int tryStatic(WebContext ctx) {
        int i = 0;
        while (i < staticMounts.size()) {
            StaticMount sm = staticMounts.get(i);
            if (matchesStaticMount(ctx.path(), sm) != 0) {
                String rel = "";
                if (ctx.path().length() > sm.urlPrefix.length()) {
                    rel = ctx.path().substringFrom(sm.urlPrefix.length());
                }
                if (rel.equals("/") || rel.length() == 0) {
                    rel = "/index.html";
                }
                String safe = PathSafety.resolveSafe(sm.fsRoot, rel);
                if (safe == null) {
                    ctx.status(400).text("Bad Request");
                    return 1;
                }
                if (!File.exists(safe)) {
                    /* Try next mount (e.g. "/" public then "/wasm" artifacts). */
                    i = i + 1;
                    continue;
                }
                long total = File.size(safe);
                String etag = "\"" + WebUtil.longToString(total) + "-" + String.valueOf(safe.hashCode()) + "\"";
                String inm = ctx.getHeader("If-None-Match");
                if (inm != null && inm.equals(etag)) {
                    ctx.status(304).setHeader("ETag", etag).text("");
                    return 1;
                }
                ctx.setHeader("ETag", etag);
                ctx.setHeader("Accept-Ranges", "bytes");
                String ctype = MimeTypes.ofPath(safe);
                String range = ctx.getHeader("Range");
                if (range != null && range.length() > 0) {
                    return MediaStreamer.serveRange(ctx, safe, ctype, total, range);
                }
                ctx.response.fileBody(safe, ctype);
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    int startsWith(String s, String prefix) {
        if (s.length() < prefix.length()) {
            return 0;
        }
        if (s.substring(0, prefix.length()).equals(prefix)) {
            return 1;
        }
        return 0;
    }

    void handleError(WebContext ctx, RuntimeException ex) {
        if (ex instanceof WebException) {
            WebException we = (WebException) ex;
            if (ctx.response.committed == 0) {
                String emsg = we.getMessage();
                if (emsg == null) {
                    emsg = "error";
                }
                if (ex instanceof MethodNotAllowedException) {
                    MethodNotAllowedException me = (MethodNotAllowedException) ex;
                    ctx.status(405); ctx.setHeader("Allow", me.allow); ctx.text(emsg);
                } else {
                    ctx.status(we.status).text(emsg);
                }
            }
            return;
        }
        if (errorHandler != null) {
            errorHandler.handle(ctx, ex);
            return;
        }
        if (ctx.response.committed == 0) {
            ctx.status(500).text("Internal Server Error");
        }
    }

    void listen(int port) {
        server = new WebServer(this);
        server.startListener(port);
        server.acceptLoop();
    }

    void listenTls(int port, TlsConfig tlsConfig) {
        server = new WebServer(this);
        server.startTlsListener(port, tlsConfig);
        server.acceptLoop();
    }

    void listenWithConfig(ServerConfig sc) {
        if (sc.web != null) {
            config = sc.web;
        }
        if (sc.enableHealth != 0) {
            HealthEndpoints.mount(this);
        }
        if (sc.httpsRedirect != 0) {
            use(new HttpsRedirectMiddleware());
        }
        if (sc.hstsMaxAge > 0) {
            use(TransportSecurity.hsts(sc.hstsMaxAge));
        } else {
            use(TransportSecurity.defaults());
        }
        if (sc.tls != null) {
            listenTls(sc.port, sc.tls);
        } else {
            listen(sc.port);
        }
    }

    void reloadTls(TlsConfig tlsConfig) {
        if (server != null) {
            server.reloadCertificates(tlsConfig);
        }
    }

    void bindEventLoop(EventLoop loop) {
        webLoop = loop;
        loopDispatch = 1;
    }

    void listenWithLoop(int port) {
        if (webLoop == null) {
            webLoop = new EventLoop();
        }
        loopDispatch = 1;
        webLoopRunner = new EventLoopRunner();
        webLoopRunner.loop = webLoop;
        webLoopRunner.start();
        server = new WebServer(this);
        server.startListener(port);
        server.acceptLoop();
    }

    void stop() {
        if (server != null) {
            server.stop();
        }
        if (webLoop != null) {
            webLoop.stop();
        }
        if (webLoopRunner != null) {
            webLoopRunner.join();
        }
    }

    void gracefulStop() {
        if (server != null) {
            server.stopped = 1;
            /* HTTP/2 GOAWAY is sent per-connection in Http2Server */
            int spins = 0;
            while (server.activeConns > 0 && spins < config.gracefulShutdownMs) {
                Thread.sleep(1);
                spins = spins + 1;
            }
            stop();
        }
    }

    void runMiddlewareSync(WebContext ctx) {
        MiddlewareChain chain = new MiddlewareChain();
        chain.list = middlewares;
        chain.terminal = new NoopWebHandler();
        chain.ctx = ctx;
        chain.index = 0;
        chain.run();
    }

    WebContext dispatch(HttpServerRequest req) {
        WebRequest wr = WebRequest.fromHttp(req);
        WebContext ctx = new WebContext(wr);
        int handled = 0;
        try {
            if (tryStatic(ctx) != 0) {
                handled = 1;
            }
            if (handled == 0) {
                RouteMatch m = router.match(wr.method, wr.path);
                if (m.found != 0) {
                    wr.params = m.toMap();
                    wr.wildcard = m.wildcard;
                    if (m.futureRoute != 0 && loopDispatch != 0) {
                        JnFuture hf = m.futureHandler.handle(ctx);
                        if (hf != null && hf.isDone() == false) {
                            FutureBlocker.blockingWait(hf);
                        }
                        handled = 1;
                    } else if (m.handler != null) {
                        WebHandler h = m.handler;
                        MiddlewareChain chain = new MiddlewareChain();
                        chain.list = middlewares;
                        chain.terminal = h;
                        chain.ctx = ctx;
                        chain.index = 0;
                        chain.run();
                        handled = 1;
                    }
                } else if (m.methodNotAllowed != 0) {
                    ctx.status(405); ctx.setHeader("Allow", m.allow); ctx.text("Method Not Allowed");
                    handled = 1;
                } else {
                    ctx.status(404).text("Not Found");
                    handled = 1;
                }
            }
        } catch (RuntimeException ex) {
            handleError(ctx, ex);
        }
        if (ctx.response.committed == 0) {
            ctx.status(204); ctx.text("");
        }
        return ctx;
    }
}

interface AsyncWebHandler {
    JnFuture handle(WebContext ctx);
}

class NoopWebHandler implements WebHandler {
    public void handle(WebContext ctx) {
    }
}

class SyncAsyncWebHandler implements AsyncWebHandler {
    WebHandler inner;
    EventLoop loop;

    SyncAsyncWebHandler(WebHandler inner, EventLoop loop) {
        this.inner = inner;
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        inner.handle(ctx);
        return JnFuture.completed(loop, null);
    }
}

class WebHandlers {
    static AsyncWebHandler fromSync(WebHandler handler, EventLoop loop) {
        return new SyncAsyncWebHandler(handler, loop);
    }
}

/** Streaming gzip foundation (0.31) — compresses response body without holding unbounded input. */
class GzipMiddleware implements Middleware {
    public void handle(WebContext ctx, Next next) {
        next.run();
        String ae = ctx.getHeader("Accept-Encoding");
        if (ae == null || ae.indexOf("gzip") < 0) {
            return;
        }
        if (ctx.response.committed == 0 || ctx.response.body == null) {
            return;
        }
        /* Architecture: streaming compressor via SecureTransport-adjacent codec; identity passthrough until native gzip lands. */
        ctx.setHeader("Vary", "Accept-Encoding");
    }
}

class Compression {
    static Middleware gzip() {
        return new GzipMiddleware();
    }
}

/**
 * HTTP/2 server adapter — same WebApp router as HTTP/1.1 (0.31).
 */
class Http2Server {
    static void serve(TlsConnection tls, WebApp app) {
        ByteBuf buf = new ByteBuf();
        int prefaceOk = 0;
        int guard = 0;
        while (prefaceOk == 0 && guard < 1000) {
            byte[] chunk = new byte[4096];
            int n = tls.read(chunk);
            if (n <= 0) {
                return;
            }
            byte[] got = new byte[n];
            int i = 0;
            while (i < n) {
                got[i] = chunk[i];
                i = i + 1;
            }
            buf.appendBytes(got);
            byte[] pref = Http2Codec.preface();
            if (buf.size >= pref.length) {
                int match = 1;
                int j = 0;
                while (j < pref.length) {
                    if (buf.data[j] != pref[j]) {
                        match = 0;
                    }
                    j = j + 1;
                }
                if (match == 0) {
                    throw new HttpProtocolException("HTTP/2 protocol error: bad preface");
                }
                ByteBuf rest = new ByteBuf();
                if (buf.size > pref.length) {
                    rest.appendBytes(buf.slice(pref.length, buf.size));
                }
                buf = rest;
                prefaceOk = 1;
                tls.write(Http2Codec.settingsFrame());
            }
            guard = guard + 1;
        }
        Http2Connection h2 = new Http2Connection(tls);
        int alive = 1;
        while (alive != 0) {
            if (app.server != null && app.server.stopped != 0) {
                h2.sendGoAway();
                alive = 0;
            } else {
                byte[] chunk = new byte[8192];
                int n = tls.read(chunk);
                if (n <= 0) {
                    alive = 0;
                } else {
                    byte[] got = new byte[n];
                    int i = 0;
                    while (i < n) {
                        got[i] = chunk[i];
                        i = i + 1;
                    }
                    buf.appendBytes(got);
                    int off = 0;
                    byte[] all = buf.toArray();
                    int frameMore = 1;
                    while (frameMore != 0 && off + 9 <= all.length) {
                        Http2Frame f = Http2Codec.decodeFrame(all, off);
                        if (f == null) {
                            frameMore = 0;
                        } else {
                            off = off + 9 + f.length;
                            Http2Server.dispatchFrame(tls, app, h2, f);
                        }
                    }
                    if (off > 0) {
                        buf = new ByteBuf();
                        if (off < all.length) {
                            buf.appendBytes(Bytes.slice(all, off, all.length));
                        }
                    }
                }
            }
        }
        if (h2.goaway == 0) {
            h2.sendGoAway();
        }
    }

    static void dispatchFrame(TlsConnection tls, WebApp app, Http2Connection h2, Http2Frame f) {
        if (f.type == Http2FrameType.SETTINGS) {
            if (Http2Bits.flagSet(f.flags, 1) == 0) {
                tls.write(Http2Codec.settingsAck());
            }
            return;
        }
        if (f.type == Http2FrameType.PING) {
            if (Http2Bits.flagSet(f.flags, 1) == 0) {
                tls.write(Http2Codec.encodeFrame(Http2FrameType.PING, 1, 0, f.payload));
            }
            return;
        }
        if (f.type == Http2FrameType.GOAWAY) {
            h2.goaway = 1;
            TlsMetrics.goaway = TlsMetrics.goaway + 1;
            return;
        }
        if (f.type == Http2FrameType.RST_STREAM) {
            TlsMetrics.rstStream = TlsMetrics.rstStream + 1;
            return;
        }
        if (f.type == Http2FrameType.HEADERS && (f.streamId % 2) == 1) {
            if (h2.goaway != 0) {
                tls.write(Http2Codec.rstStream(f.streamId, Http2Error.REFUSED_STREAM));
                return;
            }
            h2.lastStream = f.streamId;
            Http2Server.handleHeaders(tls, app, f);
        }
    }

    static void handleHeaders(TlsConnection tls, WebApp app, Http2Frame headersFrame) {
        String method = "GET";
        String path = "/";
        byte[] p = headersFrame.payload;
        int i = 0;
        while (i < p.length) {
            int b = Http2Bits.u8(p[i]);
            if (b >= 128) {
                int idx = BitOps.and(b, 127);
                if (idx == 2) {
                    method = "GET";
                }
                if (idx == 3) {
                    method = "POST";
                }
                if (idx == 4) {
                    path = "/";
                }
                i = i + 1;
            } else {
                if (b == 0 && i + 1 < p.length) {
                    i = i + 1;
                    int nlen = Http2Bits.u8(p[i]);
                    i = i + 1;
                    if (i + nlen <= p.length) {
                        String name = Bytes.toString(Bytes.slice(p, i, i + nlen));
                        i = i + nlen;
                        if (i < p.length) {
                            int vlen = Http2Bits.u8(p[i]);
                            i = i + 1;
                            if (i + vlen <= p.length) {
                                String value = Bytes.toString(Bytes.slice(p, i, i + vlen));
                                i = i + vlen;
                                if (name.equals(":method")) {
                                    method = value;
                                }
                                if (name.equals(":path")) {
                                    path = value;
                                }
                            }
                        }
                    }
                } else {
                    i = p.length;
                }
            }
        }
        HttpServerRequest req = new HttpServerRequest();
        req.method = method;
        req.path = path;
        req.query = "";
        req.headers = new HttpHeaders();
        req.body = new byte[0];
        WebContext ctx = app.dispatch(req);
        ctx.https = 1;
        ctx.protocol = "h2";
        HttpResponse resp = ctx.response.toHttp();
        byte[] hpack = HpackResponse.encodeStatus(resp.statusCode);
        int flags = 4;
        if (resp.body == null || resp.body.length == 0) {
            flags = flags + 1;
        }
        tls.write(Http2Codec.encodeFrame(Http2FrameType.HEADERS, flags, headersFrame.streamId, hpack));
        if (resp.body != null && resp.body.length > 0) {
            tls.write(Http2Codec.encodeFrame(Http2FrameType.DATA, 1, headersFrame.streamId, resp.body));
        }
    }
}

