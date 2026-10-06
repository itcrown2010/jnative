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
        if (!u.scheme.equals("http")) {
            if (u.scheme.equals("https")) {
                throw new UnsupportedSchemeException("https not supported yet");
            }
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
                u.port = 80;
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
                u.port = 80;
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

    void add(String name, String value) {
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
        if (more == null || more.length == 0) {
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
}

class HttpRequest {
    String method;
    String url;
    HttpHeaders headers;
    byte[] body;

    static HttpRequest create(String method, String url) {
        HttpRequest r = new HttpRequest();
        r.method = method;
        r.url = url;
        r.headers = new HttpHeaders();
        r.body = new byte[0];
        return r;
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

    static HttpResponse text(String body) {
        return textStatus(200, body);
    }

    static HttpResponse textStatus(int status, String body) {
        HttpResponse r = new HttpResponse();
        r.statusCode = status;
        r.reasonPhrase = HttpResponse.reasonFor(status);
        r.headers = new HttpHeaders();
        r.headers.set("Content-Type", "text/plain; charset=utf-8");
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
        if (body == null) {
            r.body = new byte[0];
        } else {
            r.body = body;
        }
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

class HttpClient {
    static int maxHeaderSize() {
        return 65536;
    }

    static int maxBodySize() {
        return 33554432;
    }

    static int maxRedirects() {
        return 5;
    }

    static HttpResponse get(String url) {
        HttpRequest req = HttpRequest.create("GET", url);
        return HttpClient.send(req);
    }

    static HttpResponse send(HttpRequest request) {
        return HttpClient.sendRedirects(request, 0);
    }

    static HttpResponse sendRedirects(HttpRequest request, int redirectCount) {
        HttpUrl url = HttpUrl.parse(request.url);
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
                int i = 0;
                while (i < request.headers.count) {
                    if (!request.headers.names[i].equalsIgnoreCase("Host")
                            && !request.headers.names[i].equalsIgnoreCase("Content-Length")) {
                        next.headers.add(request.headers.names[i], request.headers.values[i]);
                    }
                    i = i + 1;
                }
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

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
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
            String out = "http://" + base.hostHeader() + path;
            if (q.length() > 0) {
                out = out + "?" + q;
            }
            return out;
        }
        return "http://" + base.hostHeader() + "/" + location;
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
            if (next < 0 || next == pos) {
                hdrDone = 1;
            } else {
                String line = headerText.substring(pos, next);
                int colon = line.indexOf(":");
                if (colon <= 0) {
                    throw new HttpProtocolException("malformed header: " + line);
                }
                String name = line.substring(0, colon).trim();
                String value = line.substringFrom(colon + 1).trim();
                response.headers.add(name, value);
                pos = next + 2;
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

    HttpConnection(TcpSocket socket) {
        this.socket = socket;
    }

    HttpServerRequest readRequest() {
        ByteBuf buf = new ByteBuf();
        int headerEnd = -1;
        while (headerEnd < 0) {
            byte[] chunk = socket.readBytes(4096);
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
            req.body = HttpClient.readExact(socket, remainder, len);
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
            if (next < 0 || next == pos) {
                reqHdrDone = 1;
            } else {
                String line = headerText.substring(pos, next);
                int colon = line.indexOf(":");
                if (colon <= 0) {
                    throw new HttpProtocolException("malformed header");
                }
                req.headers.add(line.substring(0, colon).trim(), line.substringFrom(colon + 1).trim());
                pos = next + 2;
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
            response.headers.set("Content-Length", String.valueOf(response.body.length));
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
        buf.appendBytes(response.body);
        socket.write(buf.toArray());
    }

    void close() {
        socket.close();
    }
}

class HttpServer {
    TcpServer server;

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

    HttpConnection accept() {
        TcpSocket sock = server.accept();
        return new HttpConnection(sock);
    }

    void close() {
        server.close();
    }
}
