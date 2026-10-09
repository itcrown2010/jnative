// JNative RPC Runtime (0.28) — envelopes, HTTP client helper, errors.
// NOT compiler intrinsics. Generated stubs call into this library.
// No generic methods — avoid GenericLowering cycles.

/**
 * @author 张晓达
 */
class RpcException extends RuntimeException {
    String code;
    int status;

    RpcException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() {
        return code;
    }

    int httpStatus() {
        return status;
    }
}

class RpcUnauthorizedException extends RpcException {
    RpcUnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}

class RpcForbiddenException extends RpcException {
    RpcForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}

class RpcRemoteException extends RpcException {
    RpcRemoteException(String code, String message) {
        super(code, message, 400);
    }
}

class RpcTransportException extends RpcException {
    RpcTransportException(String message) {
        super("TRANSPORT_ERROR", message, 502);
    }
}

class RpcProtocolException extends RpcException {
    RpcProtocolException(String message) {
        super("PROTOCOL_ERROR", message, 400);
    }
}

class RpcTimeoutException extends RpcException {
    RpcTimeoutException(String message) {
        super("TIMEOUT", message, 504);
    }
}

class RpcContext {
    String requestId;
    String principal;
    WebContext web;

    RpcContext(WebContext web, String requestId) {
        this.web = web;
        this.requestId = requestId;
        this.principal = "";
    }
}

class RpcRuntime {
    static int requestCounter;

    static String nextRequestId() {
        requestCounter = requestCounter + 1;
        return "rpc-" + String.valueOf(requestCounter);
    }

    static boolean isAuthenticated(WebContext ctx) {
        if (ctx == null) {
            return false;
        }
        String auth = ctx.getHeader("Authorization");
        if (auth != null && auth.length() > 0) {
            return true;
        }
        String cookie = ctx.getHeader("Cookie");
        if (cookie != null && cookie.indexOf("JNSESSION") >= 0) {
            return true;
        }
        return false;
    }

    static boolean hasRole(WebContext ctx, String role) {
        if (!isAuthenticated(ctx)) {
            return false;
        }
        String r = ctx.getHeader("X-JNative-Role");
        if (r == null) {
            return false;
        }
        return r.equals(role);
    }
}

class RpcEnvelope {
    static JsonObject argsObject(JsonValue body) {
        if (body == null || !body.isObject()) {
            return new JsonObject();
        }
        JsonObject o = body.asObject();
        JsonValue args = o.get("args");
        if (args != null && args.isObject()) {
            return args.asObject();
        }
        return o;
    }

    static int getInt(JsonObject args, String key) {
        JsonValue v = args.get(key);
        if (v == null) {
            return 0;
        }
        return jsonToInt(v);
    }

    static int jsonToInt(JsonValue v) {
        if (v == null) {
            return 0;
        }
        String s = Json.stringify(v);
        return WebUtil.parseIntOr(s, 0);
    }

    static String getString(JsonObject args, String key) {
        JsonValue v = args.get(key);
        if (v == null || !v.isString()) {
            return "";
        }
        return v.asString();
    }

    static boolean getBool(JsonObject args, String key) {
        JsonValue v = args.get(key);
        if (v == null) {
            return false;
        }
        return v.asBoolean();
    }

    static JsonValue requireArg(JsonObject args, String key) {
        JsonValue v = args.get(key);
        if (v == null) {
            throw new RpcProtocolException("missing argument: " + key);
        }
        return v;
    }

    static JsonValue okNull() {
        JsonObject o = new JsonObject();
        o.put("ok", new JsonBoolean(1));
        o.put("result", JsonNull.get());
        return o;
    }

    static JsonValue okInt(int v) {
        JsonObject o = new JsonObject();
        o.put("ok", new JsonBoolean(1));
        o.put("result", new JsonNumber(v));
        return o;
    }

    static JsonValue okString(String v) {
        JsonObject o = new JsonObject();
        o.put("ok", new JsonBoolean(1));
        o.put("result", new JsonString(v));
        return o;
    }

    static JsonValue okBool(boolean v) {
        JsonObject o = new JsonObject();
        o.put("ok", new JsonBoolean(1));
        if (v) {
            o.put("result", new JsonBoolean(1));
        } else {
            o.put("result", new JsonBoolean(0));
        }
        return o;
    }

    static JsonValue okValue(JsonValue result) {
        JsonObject o = new JsonObject();
        o.put("ok", new JsonBoolean(1));
        o.put("result", result);
        return o;
    }

    static JsonValue error(String code, String message, String requestId) {
        JsonObject err = new JsonObject();
        err.putString("code", code);
        err.putString("message", message);
        if (requestId != null) {
            err.putString("requestId", requestId);
        }
        JsonObject o = new JsonObject();
        o.put("ok", new JsonBoolean(0));
        o.put("error", err);
        return o;
    }

    static void throwIfError(JsonValue parsed) {
        if (parsed == null || !parsed.isObject()) {
            throw new RpcProtocolException("invalid RPC response");
        }
        JsonObject o = parsed.asObject();
        JsonValue ok = o.get("ok");
        if (ok != null && ok.asBoolean()) {
            return;
        }
        JsonValue err = o.get("error");
        if (err != null && err.isObject()) {
            JsonObject e = err.asObject();
            String code = e.getString("code", "REMOTE_ERROR");
            String msg = e.getString("message", "remote error");
            throw new RpcRemoteException(code, msg);
        }
        throw new RpcProtocolException("RPC call failed");
    }

    static JsonValue resultValue(JsonValue parsed) {
        JsonObject o = parsed.asObject();
        return o.get("result");
    }

    static int resultInt(JsonValue parsed) {
        JsonValue r = resultValue(parsed);
        if (r == null) {
            return 0;
        }
        return jsonToInt(r);
    }

    static String resultString(JsonValue parsed) {
        JsonValue r = resultValue(parsed);
        if (r == null || !r.isString()) {
            return "";
        }
        return r.asString();
    }

    static boolean resultBool(JsonValue parsed) {
        JsonValue r = resultValue(parsed);
        if (r == null) {
            return false;
        }
        return r.asBoolean();
    }
}

class RpcHttp {
    static HttpResponse postJson(String url, JsonObject body) {
        HttpRequest req = HttpRequest.create("POST", url);
        req.header("Content-Type", "application/json; charset=utf-8");
        req.header("Accept", "application/json");
        req.header("X-JNative-Rpc", "0.28");
        req.header("Authorization", "Bearer demo");
        req.header("X-JNative-Role", "ADMIN");
        String json = Json.stringify(body);
        req.bodyString(json);
        HttpResponse resp = HttpClient.send(req);
        if (resp == null) {
            throw new RpcTransportException("null HTTP response");
        }
        return resp;
    }
}
