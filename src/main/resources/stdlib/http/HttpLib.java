/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative HTTP/1.1 stdlib (0.8.3) — pure JNative above TcpSocket. NOT a compiler intrinsic.

class HttpException extends IOException {
    HttpException(String message) {
        super(message);
    }
}

class HttpProtocolException extends HttpException {
    HttpProtocolException(String message) {
        super(message);
    }
}

class InvalidUrlException extends HttpException {
    InvalidUrlException(String message) {
        super(message);
    }
}

class UnsupportedSchemeException extends HttpException {
    UnsupportedSchemeException(String message) {
        super(message);
    }
}

class HttpUrl {
    String scheme;
    String host;
    int port;
    String path;
    String query;

    static HttpUrl parse(String url) {
        if (url == null) {
            throw new InvalidUrlException("null url");
        }
        HttpUrl u = new HttpUrl();
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            throw new InvalidUrlException("missing scheme: " + url);
        }
        u.scheme = url.substring(0, schemeEnd).toLowerCase();
        if (!u.scheme.equals("http") && !u.scheme.equals("https")) {
            throw new UnsupportedSchemeException("unsupported scheme: " + u.scheme);
        }
        int authStart = schemeEnd + 3;
        int pathStart = -1;
        int i = authStart;
        // IPv6 literal: http://[::1]:8080/
        if (i < url.length() && url.charAt(i) == 91) {
            int rb = url.indexOfFrom("]", i);
            if (rb < 0) {
                throw new InvalidUrlException("bad IPv6 host: " + url);
            }
            u.host = url.substring(i + 1, rb);
            i = rb + 1;
            if (i < url.length() && url.charAt(i) == 58) {
                i = i + 1;
                int portEnd = i;
                int portDone = 0;
                while (portDone == 0 && portEnd < url.length()) {
                    char c = url.charAt(portEnd);
                    if (c == 47 || c == 63) {
                        portDone = 1;
                    } else {
                        portEnd = portEnd + 1;
                    }
                }
                u.port = HttpUtil.parsePositiveInt(url.substring(i, portEnd));
                i = portEnd;
            } else {
                if (u.scheme.equals("https")) {
                    u.port = 443;
                } else {
                    u.port = 80;
                }
            }
            pathStart = i;
        } else {
            int slash = url.indexOfFrom("/", authStart);
            int q = url.indexOfFrom("?", authStart);
            int hostEnd = url.length();
            if (slash >= 0 && slash < hostEnd) {
                hostEnd = slash;
            }
            if (q >= 0 && q < hostEnd) {
                hostEnd = q;
            }
            String hostPort = url.substring(authStart, hostEnd);
            int colon = hostPort.indexOf(":");
            if (colon >= 0) {
                u.host = hostPort.substring(0, colon);
                u.port = HttpUtil.parsePositiveInt(hostPort.substringFrom(colon + 1));
            } else {
                u.host = hostPort;
                if (u.scheme.equals("https")) {
                    u.port = 443;
                } else {
                    u.port = 80;
                }
            }
            if (u.host.length() == 0) {
                throw new InvalidUrlException("empty host: " + url);
            }
            pathStart = hostEnd;
        }
        if (pathStart >= url.length()) {
            u.path = "/";
            u.query = "";
        } else {
            int qmark = url.indexOfFrom("?", pathStart);
            if (qmark < 0) {
                u.path = url.substringFrom(pathStart);
                if (u.path.length() == 0) {
                    u.path = "/";
                }
                u.query = "";
            } else {
                u.path = url.substring(pathStart, qmark);
                if (u.path.length() == 0) {
                    u.path = "/";
                }
                u.query = url.substringFrom(qmark + 1);
            }
        }
        return u;
    }

    String hostHeader() {
        if (port == 80) {
            return host;
        }
        return host + ":" + String.valueOf(port);
    }

    String requestTarget() {
        if (query.length() == 0) {
            return path;
        }
        return path + "?" + query;
    }
}

class HttpHeaders {
    String[] names;
    String[] values;
    int count;

    HttpHeaders() {
        names = new String[16];
        values = new String[16];
        count = 0;
    }

    void ensure(int need) {
        if (need <= names.length) {
            return;
        }
        int nlen = names.length * 2;
        while (nlen < need) {
            nlen = nlen * 2;
        }
        String[] nn = new String[nlen];
        String[] nv = new String[nlen];
        int i = 0;
        while (i < count) {
            nn[i] = names[i];
            nv[i] = values[i];
            i = i + 1;
        }
        names = nn;
        values = nv;
    }

    static void rejectCrLf(String s) {
        if (s == null) {
            return;
        }
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 13 || c == 10) {
                throw new HttpProtocolException("CRLF not allowed in header name/value");
            }
            i = i + 1;
        }
    }

    void add(String name, String value) {
        rejectCrLf(name);
        rejectCrLf(value);
        ensure(count + 1);
        names[count] = name;
        values[count] = value;
        count = count + 1;
    }

    void set(String name, String value) {
        remove(name);
        add(name, value);
    }

    void remove(String name) {
        int w = 0;
        int i = 0;
        while (i < count) {
            if (!names[i].equalsIgnoreCase(name)) {
                names[w] = names[i];
                values[w] = values[i];
                w = w + 1;
            }
            i = i + 1;
        }
        count = w;
    }

    boolean contains(String name) {
        int i = 0;
        while (i < count) {
            if (names[i].equalsIgnoreCase(name)) {
                return true;
            }
            i = i + 1;
        }
        return false;
    }

    String get(String name) {
        int i = 0;
        while (i < count) {
            if (names[i].equalsIgnoreCase(name)) {
                return values[i];
            }
            i = i + 1;
        }
        return null;
    }

    String getOrEmpty(String name) {
        String v = get(name);
        if (v == null) {
            return "";
        }
        return v;
    }
}

class ByteBuf {
    byte[] data;
    int size;

    ByteBuf() {
        data = new byte[256];
        size = 0;
    }

    void ensure(int need) {
        if (need <= data.length) {
            return;
        }
        int nlen = data.length * 2;
        while (nlen < need) {
            nlen = nlen * 2;
        }
        byte[] nd = new byte[nlen];
        Bytes.copy(data, 0, nd, 0, size);
        data = nd;
    }

    void appendBytes(byte[] more) {
        /* Split null/empty checks — avoid short-circuit codegen that NPEs then deref null. */
        if (more == null) {
            return;
        }
        if (more.length == 0) {
            return;
        }
        ensure(size + more.length);
        Bytes.copy(more, 0, data, size, more.length);
        size = size + more.length;
    }

    void appendByte(int b) {
        ensure(size + 1);
        data[size] = b;
        size = size + 1;
    }

    int indexOfCrlfCrlf() {
        int i = 0;
        while (i + 3 < size) {
            if (data[i] == 13 && data[i + 1] == 10 && data[i + 2] == 13 && data[i + 3] == 10) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    int indexOfCrlf(int from) {
        return Bytes.indexOf2(data, from, 13, 10);
    }

    byte[] toArray() {
        return Bytes.slice(data, 0, size);
    }

    byte[] slice(int from, int to) {
        return Bytes.slice(data, from, to);
    }

    String toStringUtf8() {
        return Bytes.toString(toArray());
    }
}

class HttpUtil {
    static int parsePositiveInt(String s) {
        if (s == null || s.length() == 0) {
            throw new HttpProtocolException("invalid integer");
        }
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c < 48 || c > 57) {
                throw new HttpProtocolException("invalid integer: " + s);
            }
            n = n * 10 + (c - 48);
            i = i + 1;
        }
        return n;
    }

    static int parseHexInt(String s) {
        if (s == null || s.length() == 0) {
            throw new HttpProtocolException("invalid hex");
        }
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int d = -1;
            if (c >= 48 && c <= 57) {
                d = c - 48;
            } else {
                if (c >= 65 && c <= 70) {
                    d = c - 55;
                } else {
                    if (c >= 97 && c <= 102) {
                        d = c - 87;
                    }
                }
            }
            if (d < 0) {
                throw new HttpProtocolException("invalid hex: " + s);
            }
            n = n * 16 + d;
            i = i + 1;
        }
        return n;
    }

    static boolean isNoBodyStatus(int code) {
        if (code >= 100 && code < 200) {
            return true;
        }
        if (code == 204 || code == 304) {
            return true;
        }
        return false;
    }

    static byte[] crlf() {
        byte[] b = new byte[2];
        b[0] = 13;
        b[1] = 10;
        return b;
    }

    // note: byte array stores accept int literals (narrowing at store)

    static byte[] ascii(String s) {
        return Bytes.fromString(s);
    }

    static String longToString(long v) {
        if (v == 0L) {
            return "0";
        }
        int neg = 0;
        long n = v;
        if (n < 0L) {
            neg = 1;
            n = 0L - n;
        }
        String out = "";
        while (n > 0L) {
            long rem = n - (n / 10L) * 10L;
            int digit = 0;
            while (rem > 0L) {
                digit = digit + 1;
                rem = rem - 1L;
            }
            out = String.valueOf(digit) + out;
            n = n / 10L;
        }
        if (neg != 0) {
            out = "-" + out;
        }
        return out;
    }

    /** Convert a small non-negative long to int (loop; for chunk sizes). */
    static int smallLongToInt(long v) {
        if (v <= 0L) {
            return 0;
        }
        int out = 0;
        while (v > 0L && out < 2147483647) {
            out = out + 1;
            v = v - 1L;
        }
        return out;
    }

    static long intToLong(int v) {
        long out = 0L;
        int n = v;
        if (n < 0) {
            n = 0 - n;
            while (n > 0) {
                out = out + 1L;
                n = n - 1;
            }
            return 0L - out;
        }
        while (n > 0) {
            out = out + 1L;
            n = n - 1;
        }
        return out;
    }
}

class HttpRequest {
    String method;
    String url;
    HttpHeaders headers;
    byte[] body;
    /** Development-only: skip TLS certificate verify when non-zero. */
    int insecureSkipVerify;

    static HttpRequest create(String method, String url) {
        HttpRequest r = new HttpRequest();
        r.method = method;
        r.url = url;
        r.headers = new HttpHeaders();
        r.body = new byte[0];
        r.insecureSkipVerify = 0;
        return r;
    }

    HttpRequest insecure(int v) {
        insecureSkipVerify = v;
        if (v != 0) {
            System.out.println("warning: HttpRequest.insecure(true) — TLS certificate verification DISABLED");
        }
        return this;
    }

    void header(String name, String value) {
        headers.set(name, value);
    }

    void bodyBytes(byte[] data) {
        if (data == null) {
            body = new byte[0];
        } else {
            body = data;
        }
    }

    void bodyString(String text) {
        if (text == null) {
            body = new byte[0];
        } else {
            body = Bytes.fromString(text);
        }
    }
}

class HttpResponse {
    int statusCode;
    String reasonPhrase;
    HttpHeaders headers;
    byte[] body;
    /** 0 = in-memory body; 1 = stream whole file; 2 = stream byte range [fileStart,fileEnd]. */
    int streamKind;
    String streamPath;
    long fileStart;
    long fileEnd;
    long fileTotal;
    static int STREAM_NONE = 0;
    static int STREAM_FILE = 1;
    static int STREAM_RANGE = 2;

    static HttpResponse text(String body) {
        return textStatus(200, body);
    }

    static HttpResponse textStatus(int status, String body) {
        HttpResponse r = new HttpResponse();
        r.statusCode = status;
        r.reasonPhrase = HttpResponse.reasonFor(status);
        r.headers = new HttpHeaders();
        r.headers.set("Content-Type", "text/plain; charset=utf-8");
        r.streamKind = STREAM_NONE;
        if (body == null) {
            r.body = new byte[0];
        } else {
            r.body = Bytes.fromString(body);
        }
        return r;
    }

    static HttpResponse bytesStatus(int status, byte[] body) {
        HttpResponse r = new HttpResponse();
        r.statusCode = status;
        r.reasonPhrase = HttpResponse.reasonFor(status);
        r.headers = new HttpHeaders();
        r.headers.set("Content-Type", "application/octet-stream");
        r.streamKind = STREAM_NONE;
        if (body == null) {
            r.body = new byte[0];
        } else {
            r.body = body;
        }
        return r;
    }

    static HttpResponse fileStream(int status, String path, long start, long endInclusive, long total) {
        HttpResponse r = new HttpResponse();
        r.statusCode = status;
        r.reasonPhrase = HttpResponse.reasonFor(status);
        r.headers = new HttpHeaders();
        r.headers.set("Content-Type", "application/octet-stream");
        r.body = new byte[0];
        r.streamKind = STREAM_RANGE;
        r.streamPath = path;
        r.fileStart = start;
        r.fileEnd = endInclusive;
        r.fileTotal = total;
        long len = endInclusive - start + 1L;
        if (len < 0L) {
            len = 0L;
        }
        r.headers.set("Content-Length", HttpUtil.longToString(len));
        return r;
    }

    static HttpResponse json(String body) {
        HttpResponse r = textStatus(200, body);
        r.headers.set("Content-Type", "application/json");
        return r;
    }

    static String reasonFor(int status) {
        if (status == 200) {
            return "OK";
        }
        if (status == 201) {
            return "Created";
        }
        if (status == 204) {
            return "No Content";
        }
        if (status == 301) {
            return "Moved Permanently";
        }
        if (status == 302) {
            return "Found";
        }
        if (status == 303) {
            return "See Other";
        }
        if (status == 206) {
            return "Partial Content";
        }
        if (status == 304) {
            return "Not Modified";
        }
        if (status == 307) {
            return "Temporary Redirect";
        }
        if (status == 308) {
            return "Permanent Redirect";
        }
        if (status == 400) {
            return "Bad Request";
        }
        if (status == 404) {
            return "Not Found";
        }
        if (status == 416) {
            return "Range Not Satisfiable";
        }
        if (status == 501) {
            return "Not Implemented";
        }
        return "HTTP";
    }

    int statusCode() {
        return statusCode;
    }

    String reasonPhrase() {
        return reasonPhrase;
    }

    HttpHeaders headers() {
        return headers;
    }

    byte[] bodyBytes() {
        return body;
    }

    String bodyString() {
        return Bytes.toString(body);
    }
}

/** Connection pool key: scheme|host|port|alpn|insecure
 * @author 张晓达
 */
class HttpPoolKey {
    String scheme;
    String host;
    int port;
    String alpn;
    int insecure;

    static HttpPoolKey of(HttpUrl url, TlsConfig cfg) {
        HttpPoolKey k = new HttpPoolKey();
        k.scheme = url.scheme;
        k.host = url.host;
        k.port = url.port;
        k.alpn = "";
        k.insecure = 0;
        if (cfg != null) {
            k.alpn = cfg.alpn;
            k.insecure = cfg.insecureSkipVerify;
        }
        return k;
    }

    String id() {
        return scheme + "|" + host + "|" + port + "|" + alpn + "|" + insecure;
    }
}

class HttpPooledConn {
    String key;
    TcpSocket tcp;
    TlsConnection tls;
    long lastUsedMs;
    int http2;
}

class HttpConnectionPool {
    static HttpConnectionPool SHARED;
    HttpPooledConn[] slots;
    int count;
    int maxPerHost;
    int maxTotal;
    int idleTimeoutMs;

    static HttpConnectionPool shared() {
        if (SHARED == null) {
            SHARED = new HttpConnectionPool();
            SHARED.slots = new HttpPooledConn[64];
            SHARED.count = 0;
            SHARED.maxPerHost = 8;
            SHARED.maxTotal = 64;
            SHARED.idleTimeoutMs = 60000;
        }
        return SHARED;
    }

    HttpPooledConn take(String key) {
        int i = 0;
        while (i < count) {
            HttpPooledConn c = slots[i];
            if (c != null && c.key.equals(key)) {
                slots[i] = slots[count - 1];
                slots[count - 1] = null;
                count = count - 1;
                return c;
            }
            i = i + 1;
        }
        return null;
    }

    void release(HttpPooledConn c) {
        if (c == null) {
            return;
        }
        if (count >= maxTotal) {
            if (c.tls != null) {
                c.tls.close();
            } else {
                if (c.tcp != null) {
                    c.tcp.close();
                }
            }
            return;
        }
        c.lastUsedMs = 0;
        slots[count] = c;
        count = count + 1;
    }
}

class HttpClient {
    /**
     * HTTP/1.1 pipelining is NOT supported.
     * Keep-alive reuses connections one request at a time (HTTPS pool).
     */
    static int PIPELINING_SUPPORTED = 0;

    static int maxHeaderSize() {
        return 65536;
    }

    static int maxBodySize() {
        return 1048576;
    }

    static int maxRedirects() {
        return 5;
    }

    static int connectTimeoutMs = 10000;
    static int requestTimeoutMs = 30000;
    static int tlsHandshakeTimeoutMs = 10000;

    static HttpResponse get(String url) {
        HttpRequest req = HttpRequest.create("GET", url);
        return HttpClient.send(req);
    }

    static HttpResponse send(HttpRequest request) {
        return HttpClient.sendRedirects(request, 0);
    }

    static HttpResponse sendRedirects(HttpRequest request, int redirectCount) {
        HttpUrl url = HttpUrl.parse(request.url);
        if (url.scheme.equals("https")) {
            return HttpClient.sendHttpsRedirects(request, redirectCount);
        }
        TcpSocket socket = TcpSocket.connect(url.host, url.port);
        try {
            byte[] wire = HttpClient.serializeRequest(request, url);
            socket.write(wire);
            HttpResponse response = HttpClient.readResponse(socket, request.method);
            socket.close();
            if (HttpClient.isRedirect(response.statusCode) && redirectCount < HttpClient.maxRedirects()) {
                String loc = response.headers.get("Location");
                if (loc == null || loc.length() == 0) {
                    throw new HttpProtocolException("redirect without Location");
                }
                String nextUrl = HttpClient.resolveRedirect(request.url, loc);
                String method = request.method;
                byte[] body = request.body;
                if (response.statusCode == 303) {
                    method = "GET";
                    body = new byte[0];
                } else {
                    if (response.statusCode == 301 || response.statusCode == 302) {
                        if (method.equals("POST")) {
                            method = "GET";
                            body = new byte[0];
                        }
                    }
                }
                HttpRequest next = HttpRequest.create(method, nextUrl);
                next.insecureSkipVerify = request.insecureSkipVerify;
                HttpClient.copyRedirectHeaders(request, next, request.url, nextUrl);
                next.bodyBytes(body);
                return HttpClient.sendRedirects(next, redirectCount + 1);
            }
            if (HttpClient.isRedirect(response.statusCode) && redirectCount >= HttpClient.maxRedirects()) {
                throw new HttpException("too many redirects");
            }
            return response;
        } catch (HttpException he) {
            socket.close();
            throw he;
        } catch (IOException ioe) {
            socket.close();
            throw ioe;
        }
    }

    static HttpResponse sendHttpsRedirects(HttpRequest request, int redirectCount) {
        HttpUrl url = HttpUrl.parse(request.url);
        String alpnList = "h2,http/1.1";
        if (request.insecureSkipVerify != 0) {
            /* Dev/test path: prefer HTTP/1.1 to avoid h2 preface edge cases with skip-verify. */
            alpnList = "http/1.1";
        }
        TlsConfig tlsCfg = TlsConfig.builder().alpn(alpnList).serverName(url.host).build();
        if (request.insecureSkipVerify != 0) {
            tlsCfg.insecureSkipVerify(1);
        }
        HttpPoolKey pk = HttpPoolKey.of(url, tlsCfg);
        HttpConnectionPool pool = HttpConnectionPool.shared();
        HttpPooledConn pooled = pool.take(pk.id());
        TlsConnection tls = null;
        int fresh = 0;
        if (pooled != null && pooled.tls != null) {
            tls = pooled.tls;
        } else {
            tls = TlsConnection.connect(url.host, url.port, tlsCfg);
            fresh = 1;
        }
        try {
            String alpn = tls.alpn();
            if (alpn != null && alpn.equals("h2")) {
                /* Fresh TLS required for preface; multiplex reuse is Http2Connection's job. */
                if (fresh == 0) {
                    tls.close();
                    tls = TlsConnection.connect(url.host, url.port, tlsCfg);
                    fresh = 1;
                }
                HttpResponse h2 = Http2Client.request(tls, request, url);
                tls.close();
                if (HttpClient.isRedirect(h2.statusCode) && redirectCount < HttpClient.maxRedirects()) {
                    String loc = h2.headers.get("Location");
                    if (loc != null && loc.length() > 0) {
                        String nextUrl = HttpClient.resolveRedirect(request.url, loc);
                        HttpRequest next = HttpRequest.create(request.method, nextUrl);
                        next.insecureSkipVerify = request.insecureSkipVerify;
                        HttpClient.copyRedirectHeaders(request, next, request.url, nextUrl);
                        next.bodyBytes(request.body);
                        return HttpClient.sendRedirects(next, redirectCount + 1);
                    }
                }
                return h2;
            }
            if (!request.headers.contains("Connection")) {
                request.headers.set("Connection", "keep-alive");
            }
            byte[] wire = HttpClient.serializeRequest(request, url);
            tls.write(wire);
            HttpResponse response = HttpClient.readResponseTls(tls, request.method);
            String connHdr = response.headers.getOrEmpty("Connection").toLowerCase();
            if (connHdr.equals("close")) {
                tls.close();
            } else {
                HttpPooledConn back = new HttpPooledConn();
                back.key = pk.id();
                back.tls = tls;
                back.http2 = 0;
                pool.release(back);
            }
            if (HttpClient.isRedirect(response.statusCode) && redirectCount < HttpClient.maxRedirects()) {
                String loc = response.headers.get("Location");
                if (loc == null || loc.length() == 0) {
                    throw new HttpProtocolException("redirect without Location");
                }
                String nextUrl = HttpClient.resolveRedirect(request.url, loc);
                HttpRequest next = HttpRequest.create(request.method, nextUrl);
                next.insecureSkipVerify = request.insecureSkipVerify;
                HttpClient.copyRedirectHeaders(request, next, request.url, nextUrl);
                next.bodyBytes(request.body);
                return HttpClient.sendRedirects(next, redirectCount + 1);
            }
            return response;
        } catch (HttpException he) {
            tls.close();
            throw he;
        } catch (IOException ioe) {
            tls.close();
            throw ioe;
        }
    }

    static HttpResponse readResponseTls(TlsConnection tls, String method) {
        ByteBuf buf = new ByteBuf();
        int headerEnd = -1;
        while (headerEnd < 0) {
            byte[] chunk = new byte[4096];
            int n = tls.read(chunk);
            if (n <= 0) {
                throw new HttpProtocolException("connection closed before headers");
            }
            byte[] got = new byte[n];
            int i = 0;
            while (i < n) {
                got[i] = chunk[i];
                i = i + 1;
            }
            buf.appendBytes(got);
            if (buf.size > HttpClient.maxHeaderSize()) {
                throw new HttpProtocolException("headers too large");
            }
            headerEnd = buf.indexOfCrlfCrlf();
        }
        String headerText = Bytes.toString(buf.slice(0, headerEnd));
        byte[] remainder = buf.slice(headerEnd + 4, buf.size);
        HttpResponse response = HttpClient.parseStatusAndHeaders(headerText);
        if (method.equals("HEAD") || HttpUtil.isNoBodyStatus(response.statusCode)) {
            response.body = new byte[0];
            return response;
        }
        response.body = HttpClient.readBodyTls(tls, response.headers, remainder);
        return response;
    }

    static byte[] readBodyTls(TlsConnection tls, HttpHeaders headers, byte[] prelude) {
        String cl = headers.get("Content-Length");
        if (cl != null) {
            int len = HttpUtil.parsePositiveInt(cl);
            ByteBuf out = new ByteBuf();
            out.appendBytes(prelude);
            int reading = 1;
            while (reading != 0 && out.size < len) {
                byte[] chunk = new byte[4096];
                int n = tls.read(chunk);
                if (n <= 0) {
                    reading = 0;
                } else {
                    byte[] got = new byte[n];
                    int i = 0;
                    while (i < n) {
                        got[i] = chunk[i];
                        i = i + 1;
                    }
                    out.appendBytes(got);
                }
            }
            return out.toArray();
        }
        ByteBuf out = new ByteBuf();
        out.appendBytes(prelude);
        int guard = 0;
        int reading2 = 1;
        while (reading2 != 0 && guard < 100000) {
            byte[] chunk = new byte[4096];
            int n = tls.read(chunk);
            if (n <= 0) {
                reading2 = 0;
            } else {
                byte[] got = new byte[n];
                int i = 0;
                while (i < n) {
                    got[i] = chunk[i];
                    i = i + 1;
                }
                out.appendBytes(got);
            }
            guard = guard + 1;
        }
        return out.toArray();
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    static int isSensitiveRedirectHeader(String name) {
        if (name.equalsIgnoreCase("Authorization")) {
            return 1;
        }
        if (name.equalsIgnoreCase("Proxy-Authorization")) {
            return 1;
        }
        if (name.equalsIgnoreCase("Cookie")) {
            return 1;
        }
        return 0;
    }

    static int isCrossOriginRedirect(String fromUrl, String toUrl) {
        HttpUrl a = HttpUrl.parse(fromUrl);
        HttpUrl b = HttpUrl.parse(toUrl);
        if (!a.scheme.equalsIgnoreCase(b.scheme)) {
            return 1;
        }
        if (!a.host.equalsIgnoreCase(b.host)) {
            return 1;
        }
        if (a.port != b.port) {
            return 1;
        }
        return 0;
    }

    /** Copy headers for redirect; strip credentials on cross-origin. Never forward Host/Content-Length. */
    static void copyRedirectHeaders(HttpRequest from, HttpRequest to, String fromUrl, String toUrl) {
        int cross = HttpClient.isCrossOriginRedirect(fromUrl, toUrl);
        int i = 0;
        while (i < from.headers.count) {
            String n = from.headers.names[i];
            if (!n.equalsIgnoreCase("Host") && !n.equalsIgnoreCase("Content-Length")) {
                int skip = 0;
                if (cross != 0 && HttpClient.isSensitiveRedirectHeader(n) != 0) {
                    skip = 1;
                }
                if (skip == 0) {
                    to.headers.add(n, from.headers.values[i]);
                }
            }
            i = i + 1;
        }
    }

    static String resolveRedirect(String baseUrl, String location) {
        if (location.indexOf("://") >= 0) {
            return location;
        }
        HttpUrl base = HttpUrl.parse(baseUrl);
        if (location.startsWith("/")) {
            String q = "";
            int qi = location.indexOf("?");
            String path = location;
            if (qi >= 0) {
                path = location.substring(0, qi);
                q = location.substringFrom(qi + 1);
            }
            String out = base.scheme + "://" + base.hostHeader() + path;
            if (q.length() > 0) {
                out = out + "?" + q;
            }
            return out;
        }
        return base.scheme + "://" + base.hostHeader() + "/" + location;
    }

    static byte[] serializeRequest(HttpRequest request, HttpUrl url) {
        ByteBuf buf = new ByteBuf();
        buf.appendBytes(HttpUtil.ascii(request.method));
        buf.appendByte(32);
        buf.appendBytes(HttpUtil.ascii(url.requestTarget()));
        buf.appendBytes(HttpUtil.ascii(" HTTP/1.1"));
        buf.appendBytes(HttpUtil.crlf());
        if (!request.headers.contains("Host")) {
            buf.appendBytes(HttpUtil.ascii("Host: "));
            buf.appendBytes(HttpUtil.ascii(url.hostHeader()));
            buf.appendBytes(HttpUtil.crlf());
        }
        if (!request.headers.contains("Connection")) {
            buf.appendBytes(HttpUtil.ascii("Connection: close"));
            buf.appendBytes(HttpUtil.crlf());
        }
        if (request.body.length > 0 && !request.headers.contains("Content-Length")) {
            buf.appendBytes(HttpUtil.ascii("Content-Length: "));
            buf.appendBytes(HttpUtil.ascii(String.valueOf(request.body.length)));
            buf.appendBytes(HttpUtil.crlf());
        }
        int i = 0;
        while (i < request.headers.count) {
            buf.appendBytes(HttpUtil.ascii(request.headers.names[i]));
            buf.appendBytes(HttpUtil.ascii(": "));
            buf.appendBytes(HttpUtil.ascii(request.headers.values[i]));
            buf.appendBytes(HttpUtil.crlf());
            i = i + 1;
        }
        buf.appendBytes(HttpUtil.crlf());
        if (request.body.length > 0) {
            buf.appendBytes(request.body);
        }
        return buf.toArray();
    }

    static HttpResponse readResponse(TcpSocket socket, String method) {
        ByteBuf buf = new ByteBuf();
        int headerEnd = -1;
        while (headerEnd < 0) {
            byte[] chunk = socket.readBytes(4096);
            if (chunk.length == 0) {
                throw new HttpProtocolException("connection closed before headers");
            }
            buf.appendBytes(chunk);
            if (buf.size > HttpClient.maxHeaderSize()) {
                throw new HttpProtocolException("headers too large");
            }
            headerEnd = buf.indexOfCrlfCrlf();
        }
        String headerText = Bytes.toString(buf.slice(0, headerEnd));
        byte[] remainder = buf.slice(headerEnd + 4, buf.size);
        HttpResponse response = HttpClient.parseStatusAndHeaders(headerText);
        if (method.equals("HEAD") || HttpUtil.isNoBodyStatus(response.statusCode)) {
            response.body = new byte[0];
            return response;
        }
        response.body = HttpClient.readBody(socket, response.headers, remainder);
        return response;
    }

    static HttpResponse parseStatusAndHeaders(String headerText) {
        int lineEnd = headerText.indexOf("\r\n");
        if (lineEnd < 0) {
            throw new HttpProtocolException("malformed status line");
        }
        String statusLine = headerText.substring(0, lineEnd);
        if (!statusLine.startsWith("HTTP/")) {
            throw new HttpProtocolException("malformed response: " + statusLine);
        }
        int sp1 = statusLine.indexOf(" ");
        if (sp1 < 0) {
            throw new HttpProtocolException("malformed status line");
        }
        int sp2 = statusLine.indexOfFrom(" ", sp1 + 1);
        String codeStr = "";
        String reason = "";
        if (sp2 < 0) {
            codeStr = statusLine.substringFrom(sp1 + 1);
            reason = "";
        } else {
            codeStr = statusLine.substring(sp1 + 1, sp2);
            reason = statusLine.substringFrom(sp2 + 1);
        }
        HttpResponse response = new HttpResponse();
        response.statusCode = HttpUtil.parsePositiveInt(codeStr);
        response.reasonPhrase = reason;
        response.headers = new HttpHeaders();
        response.body = new byte[0];
        int pos = lineEnd + 2;
        int hdrDone = 0;
        while (hdrDone == 0 && pos < headerText.length()) {
            int next = headerText.indexOfFrom("\r\n", pos);
            if (next == pos) {
                hdrDone = 1;
            } else {
                String line = "";
                if (next < 0) {
                    line = headerText.substringFrom(pos);
                    hdrDone = 1;
                } else {
                    line = headerText.substring(pos, next);
                    pos = next + 2;
                }
                if (line.length() > 0) {
                    int colon = line.indexOf(":");
                    if (colon <= 0) {
                        throw new HttpProtocolException("malformed header: " + line);
                    }
                    String name = line.substring(0, colon).trim();
                    String value = line.substringFrom(colon + 1).trim();
                    response.headers.add(name, value);
                }
            }
        }
        return response;
    }

    static byte[] readBody(TcpSocket socket, HttpHeaders headers, byte[] prelude) {
        String te = headers.getOrEmpty("Transfer-Encoding").toLowerCase();
        if (te.indexOf("chunked") >= 0) {
            return HttpClient.readChunked(socket, prelude);
        }
        String cl = headers.get("Content-Length");
        if (cl != null) {
            int len = HttpUtil.parsePositiveInt(cl.trim());
            if (len > HttpClient.maxBodySize()) {
                throw new HttpProtocolException("body too large");
            }
            return HttpClient.readExact(socket, prelude, len);
        }
        // Connection: close framing
        ByteBuf buf = new ByteBuf();
        buf.appendBytes(prelude);
        int eof = 0;
        while (eof == 0) {
            byte[] chunk = socket.readBytes(8192);
            if (chunk.length == 0) {
                eof = 1;
            } else {
                buf.appendBytes(chunk);
                if (buf.size > HttpClient.maxBodySize()) {
                    throw new HttpProtocolException("body too large");
                }
            }
        }
        return buf.toArray();
    }

    static byte[] readExact(TcpSocket socket, byte[] prelude, int len) {
        byte[] out = new byte[len];
        int got = 0;
        if (prelude.length > 0) {
            int n = prelude.length;
            if (n > len) {
                n = len;
            }
            Bytes.copy(prelude, 0, out, 0, n);
            got = n;
        }
        while (got < len) {
            int want = len - got;
            if (want > 8192) {
                want = 8192;
            }
            byte[] chunk = socket.readBytes(want);
            if (chunk.length == 0) {
                throw new HttpProtocolException("truncated body");
            }
            Bytes.copy(chunk, 0, out, got, chunk.length);
            got = got + chunk.length;
        }
        return out;
    }

    static byte[] readExactTls(TlsConnection tls, byte[] prelude, int len) {
        byte[] out = new byte[len];
        int got = 0;
        if (prelude.length > 0) {
            int n = prelude.length;
            if (n > len) {
                n = len;
            }
            Bytes.copy(prelude, 0, out, 0, n);
            got = n;
        }
        while (got < len) {
            byte[] chunk = new byte[8192];
            int n = tls.read(chunk);
            if (n <= 0) {
                throw new HttpProtocolException("truncated body");
            }
            int copy = n;
            if (got + copy > len) {
                copy = len - got;
            }
            Bytes.copy(chunk, 0, out, got, copy);
            got = got + copy;
        }
        return out;
    }

    static byte[] readChunked(TcpSocket socket, byte[] prelude) {
        ByteBuf src = new ByteBuf();
        src.appendBytes(prelude);
        ByteBuf out = new ByteBuf();
        int chunksDone = 0;
        while (chunksDone == 0) {
            String sizeLine = HttpClient.readLine(socket, src);
            int semi = sizeLine.indexOf(";");
            String hex = sizeLine;
            if (semi >= 0) {
                hex = sizeLine.substring(0, semi);
            }
            hex = hex.trim();
            int size = HttpUtil.parseHexInt(hex);
            if (size == 0) {
                int trailDone = 0;
                while (trailDone == 0) {
                    String t = HttpClient.readLine(socket, src);
                    if (t.length() == 0) {
                        trailDone = 1;
                    }
                }
                chunksDone = 1;
            } else {
                if (out.size + size > HttpClient.maxBodySize()) {
                    throw new HttpProtocolException("body too large");
                }
                byte[] chunk = HttpClient.takeBytes(socket, src, size);
                out.appendBytes(chunk);
                String after = HttpClient.readLine(socket, src);
                if (after.length() != 0) {
                    throw new HttpProtocolException("bad chunk framing");
                }
            }
        }
        return out.toArray();
    }

    static String readLine(TcpSocket socket, ByteBuf src) {
        int found = 0;
        String line = "";
        while (found == 0) {
            int idx = src.indexOfCrlf(0);
            if (idx >= 0) {
                line = Bytes.toString(src.slice(0, idx));
                byte[] rest = src.slice(idx + 2, src.size);
                src.data = rest;
                src.size = rest.length;
                found = 1;
            } else {
                if (src.size > HttpClient.maxHeaderSize()) {
                    throw new HttpProtocolException("line too long");
                }
                byte[] chunk = socket.readBytes(1024);
                if (chunk.length == 0) {
                    throw new HttpProtocolException("eof in chunked body");
                }
                src.appendBytes(chunk);
            }
        }
        return line;
    }

    static byte[] takeBytes(TcpSocket socket, ByteBuf src, int n) {
        while (src.size < n) {
            byte[] chunk = socket.readBytes(4096);
            if (chunk.length == 0) {
                throw new HttpProtocolException("eof in chunk");
            }
            src.appendBytes(chunk);
        }
        byte[] out = src.slice(0, n);
        byte[] rest = src.slice(n, src.size);
        src.data = rest;
        src.size = rest.length;
        return out;
    }
}

class HttpServerRequest {
    String method;
    String path;
    String query;
    HttpHeaders headers;
    byte[] body;

    String method() {
        return method;
    }

    String path() {
        return path;
    }

    String query() {
        return query;
    }

    HttpHeaders headers() {
        return headers;
    }

    byte[] bodyBytes() {
        return body;
    }

    String bodyString() {
        return Bytes.toString(body);
    }
}

class HttpConnection {
    TcpSocket socket;
    TlsConnection tls;
    int useTls;

    HttpConnection(TcpSocket socket) {
        this.socket = socket;
        this.tls = null;
        this.useTls = 0;
    }

    static HttpConnection overTls(TlsConnection tlsConn) {
        HttpConnection c = new HttpConnection(tlsConn.tcp);
        c.tls = tlsConn;
        c.useTls = 1;
        return c;
    }

    byte[] readChunk(int max) {
        if (useTls != 0) {
            byte[] chunk = new byte[max];
            int n = tls.read(chunk);
            if (n <= 0) {
                return new byte[0];
            }
            byte[] got = new byte[n];
            int i = 0;
            while (i < n) {
                got[i] = chunk[i];
                i = i + 1;
            }
            return got;
        }
        return socket.readBytes(max);
    }

    void writeBytes(byte[] data) {
        if (useTls != 0) {
            tls.write(data);
        } else {
            socket.write(data);
        }
    }

    HttpServerRequest readRequest() {
        ByteBuf buf = new ByteBuf();
        int headerEnd = -1;
        while (headerEnd < 0) {
            byte[] chunk = readChunk(4096);
            if (chunk.length == 0) {
                throw new HttpProtocolException("connection closed before request headers");
            }
            buf.appendBytes(chunk);
            if (buf.size > HttpClient.maxHeaderSize()) {
                throw new HttpProtocolException("request headers too large");
            }
            headerEnd = buf.indexOfCrlfCrlf();
        }
        String headerText = Bytes.toString(buf.slice(0, headerEnd));
        byte[] remainder = buf.slice(headerEnd + 4, buf.size);
        HttpServerRequest req = HttpConnection.parseRequest(headerText);
        String te = req.headers.getOrEmpty("Transfer-Encoding").toLowerCase();
        if (te.indexOf("chunked") >= 0) {
            throw new HttpProtocolException("chunked request bodies not supported");
        }
        String cl = req.headers.get("Content-Length");
        if (cl != null) {
            int len = HttpUtil.parsePositiveInt(cl.trim());
            if (len > HttpClient.maxBodySize()) {
                throw new HttpProtocolException("request body too large");
            }
            if (useTls != 0) {
                req.body = HttpClient.readExactTls(tls, remainder, len);
            } else {
                req.body = HttpClient.readExact(socket, remainder, len);
            }
        } else {
            req.body = new byte[0];
        }
        return req;
    }

    static HttpServerRequest parseRequest(String headerText) {
        int lineEnd = headerText.indexOf("\r\n");
        if (lineEnd < 0) {
            throw new HttpProtocolException("malformed request line");
        }
        String reqLine = headerText.substring(0, lineEnd);
        int sp1 = reqLine.indexOf(" ");
        int sp2 = reqLine.indexOfFrom(" ", sp1 + 1);
        if (sp1 < 0 || sp2 < 0) {
            throw new HttpProtocolException("malformed request line");
        }
        HttpServerRequest req = new HttpServerRequest();
        req.method = reqLine.substring(0, sp1);
        String target = reqLine.substring(sp1 + 1, sp2);
        int q = target.indexOf("?");
        if (q < 0) {
            req.path = target;
            req.query = "";
        } else {
            req.path = target.substring(0, q);
            req.query = target.substringFrom(q + 1);
        }
        req.headers = new HttpHeaders();
        req.body = new byte[0];
        int pos = lineEnd + 2;
        int reqHdrDone = 0;
        while (reqHdrDone == 0 && pos < headerText.length()) {
            int next = headerText.indexOfFrom("\r\n", pos);
            if (next == pos) {
                reqHdrDone = 1;
            } else {
                String line = "";
                if (next < 0) {
                    /* Last header line: headerText excludes the terminating CRLFCRLF. */
                    line = headerText.substringFrom(pos);
                    reqHdrDone = 1;
                } else {
                    line = headerText.substring(pos, next);
                    pos = next + 2;
                }
                if (line.length() > 0) {
                    int colon = line.indexOf(":");
                    if (colon <= 0) {
                        throw new HttpProtocolException("malformed header");
                    }
                    req.headers.add(line.substring(0, colon).trim(), line.substringFrom(colon + 1).trim());
                }
            }
        }
        return req;
    }

    void send(HttpResponse response) {
        ByteBuf buf = new ByteBuf();
        buf.appendBytes(HttpUtil.ascii("HTTP/1.1 "));
        buf.appendBytes(HttpUtil.ascii(String.valueOf(response.statusCode)));
        buf.appendByte(32);
        buf.appendBytes(HttpUtil.ascii(response.reasonPhrase));
        buf.appendBytes(HttpUtil.crlf());
        if (!response.headers.contains("Content-Length")) {
            if (response.streamKind == HttpResponse.STREAM_RANGE
                    || response.streamKind == HttpResponse.STREAM_FILE) {
                long len = response.fileEnd - response.fileStart + 1L;
                if (len < 0L) {
                    len = 0L;
                }
                response.headers.set("Content-Length", HttpUtil.longToString(len));
            } else {
                int blen = 0;
                if (response.body != null) {
                    blen = response.body.length;
                }
                response.headers.set("Content-Length", String.valueOf(blen));
            }
        }
        if (!response.headers.contains("Connection")) {
            response.headers.set("Connection", "close");
        }
        int i = 0;
        while (i < response.headers.count) {
            buf.appendBytes(HttpUtil.ascii(response.headers.names[i]));
            buf.appendBytes(HttpUtil.ascii(": "));
            buf.appendBytes(HttpUtil.ascii(response.headers.values[i]));
            buf.appendBytes(HttpUtil.crlf());
            i = i + 1;
        }
        buf.appendBytes(HttpUtil.crlf());
        writeBytes(buf.toArray());
        if (response.streamKind == HttpResponse.STREAM_RANGE
                || response.streamKind == HttpResponse.STREAM_FILE) {
            sendFileRange(response.streamPath, response.fileStart, response.fileEnd);
        } else {
            if (response.body != null && response.body.length > 0) {
                writeBytes(response.body);
            }
        }
    }

    /** Stream file bytes via seek + bounded reads; never materializes the whole file. */
    void sendFileRange(String path, long start, long endInclusive) {
        if (path == null) {
            return;
        }
        long remaining = endInclusive - start + 1L;
        if (remaining <= 0L) {
            return;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(path);
            in.seek(start);
            int chunkCap = 65536;
            byte[] chunk = new byte[chunkCap];
            while (remaining > 0L) {
                int want = chunkCap;
                if (remaining < 65536L) {
                    want = HttpUtil.smallLongToInt(remaining);
                }
                int n = in.read(chunk, 0, want);
                if (n <= 0) {
                    remaining = 0L;
                } else {
                    byte[] part = new byte[n];
                    int j = 0;
                    while (j < n) {
                        part[j] = chunk[j];
                        j = j + 1;
                    }
                    writeBytes(part);
                    remaining = remaining - HttpUtil.intToLong(n);
                }
            }
        } catch (Exception ex) {
            /* peer disconnect / IO error: stop streaming and close FD in finally */
        }
        if (in != null) {
            try {
                in.close();
            } catch (Exception closeEx) {
            }
        }
    }

    void close() {
        if (useTls != 0 && tls != null) {
            tls.close();
        } else {
            if (socket != null) {
                socket.close();
            }
        }
    }
}

class HttpServer {
    TcpServer server;
    String rootPath;
    HttpHandler rootHandler;

    static HttpServer listen(String host, int port) {
        HttpServer s = new HttpServer();
        s.server = TcpServer.listen(host, port);
        return s;
    }

    static HttpServer listenAny(int port) {
        HttpServer s = new HttpServer();
        s.server = TcpServer.listenAny(port);
        return s;
    }

    void get(String path, HttpHandler handler) {
        rootPath = path;
        rootHandler = handler;
    }

    void serveOne() {
        HttpConnection conn = accept();
        HttpServerRequest req = conn.readRequest();
        if (rootHandler != null && req.path().equals(rootPath)) {
            Object result = rootHandler.handle(req);
            HttpResponse resp = (HttpResponse) result;
            conn.send(resp);
        } else {
            conn.send(HttpResponse.textStatus(404, "Not Found"));
        }
    }

    HttpConnection accept() {
        TcpSocket sock = server.accept();
        return new HttpConnection(sock);
    }

    void close() {
        server.close();
    }
}
