/**
 * Wonder Campus generic API plumbing: request context, JSON helpers, route dispatch.
 * @author 张晓达
 */
class WcApi {
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        String o = s.replace("\\", "\\\\").replace("\"", "\\\"");
        o = o.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return o;
    }

    static String q(String s) {
        return "\"" + esc(s) + "\"";
    }

    static String l(long v) {
        return CryptoParse.longToString(v);
    }

    static ArrayList<Object> P() {
        return new ArrayList<Object>();
    }

    static ArrayList<Object> P1(long a) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(l(a));
        return p;
    }

    static ArrayList<Object> P2(long a, long b) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(l(a));
        p.add(l(b));
        return p;
    }

    static ArrayList<Object> P3(long a, long b, long c) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(l(a));
        p.add(l(b));
        p.add(l(c));
        return p;
    }

    static long one(String sql, ArrayList<Object> p) {
        Object v = WcDb.queryOne(sql, p, new LongMapper());
        return WcNum.asLong(v);
    }

    static String oneStr(String sql, ArrayList<Object> p) {
        Object v = WcDb.queryOne(sql, p, new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }

    /** spec: "id:l,name:s" — column i is read as long (l) or string (s). */
    static String rows(DbQueryResult r, String spec) {
        ArrayList<Object> keys = WcLoopServices.splitCsv(spec);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{";
            int k = 0;
            while (k < keys.size()) {
                String ks = (String) keys.get(k);
                int colon = ks.indexOf(":");
                String name = ks.substring(0, colon);
                String type = ks.substring(colon + 1, ks.length());
                if (k > 0) {
                    out = out + ",";
                }
                if (type.equals("l")) {
                    out = out + "\"" + name + "\":" + l(r.rows.get(i).getLong(k));
                } else {
                    String sv = r.rows.get(i).getString(k);
                    out = out + "\"" + name + "\":" + q(sv);
                }
                k = k + 1;
            }
            out = out + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String like(String kw) {
        return "%" + kw + "%";
    }

    static String csvCell(String s) {
        if (s == null) {
            return "";
        }
        String o = s.replace("\"", "\"\"");
        if (o.indexOf(",") >= 0 || o.indexOf("\n") >= 0 || o.indexOf("\"") >= 0) {
            return "\"" + o + "\"";
        }
        return o;
    }

    static long dayStart(long nowMs) {
        /* Asia/Shanghai calendar day start (UTC+8) */
        long off = 8L * 3600000L;
        long local = nowMs + off;
        return local - (local % 86400000L) - off;
    }

    static String yuan(long cents) {
        long a = cents;
        String sign = "";
        if (a < 0L) {
            sign = "-";
            a = 0L - a;
        }
        long y = a / 100L;
        long f = a % 100L;
        String fs = l(f);
        if (f < 10L) {
            fs = "0" + fs;
        }
        return sign + l(y) + "." + fs;
    }
}

class WcCtx {
    WebContext ctx;
    WcSession es;
    JsonObject body;
    String route;
    int status;
    String contentType;
    String fileName;

    WcCtx(WebContext ctx, String route) {
        this.ctx = ctx;
        this.route = route;
        this.status = 200;
        this.contentType = "";
        this.fileName = "";
        this.es = WcAuth.require(ctx);
        String raw = ctx.bodyString();
        if (raw == null || raw.length() == 0) {
            this.body = new JsonObject();
        } else {
            this.body = Json.parse(raw).asObject();
        }
    }

    String fail(int st, String code, String msg) {
        status = st;
        return "{\"ok\":0,\"code\":\"" + code + "\",\"message\":\"" + WcApi.esc(msg) + "\"}";
    }

    String ok(String inner) {
        if (inner == null || inner.length() == 0) {
            return "{\"ok\":1}";
        }
        return "{\"ok\":1," + inner + "}";
    }

    String result(WcResult r) {
        if (r.ok == 0) {
            return fail(400, r.code, r.message);
        }
        String doc = r.documentJson;
        if (doc == null || doc.length() == 0) {
            doc = "{}";
        }
        return "{\"ok\":1,\"code\":\"" + r.code + "\",\"message\":\"" + WcApi.esc(r.message) + "\",\"data\":"
                + doc + "}";
    }

    /** returns "" when identity is allowed, otherwise an error JSON. kinds: "ADMIN,DIRECTOR" */
    String guard(String kinds) {
        if (es.userId == 0L) {
            return fail(401, "AUTH_REQUIRED", "请登录");
        }
        if (kinds == null || kinds.length() == 0) {
            return "";
        }
        ArrayList<Object> ks = WcLoopServices.splitCsv(kinds);
        int i = 0;
        while (i < ks.size()) {
            if (es.identity.equals((String) ks.get(i))) {
                return "";
            }
            i = i + 1;
        }
        return fail(403, "FORBIDDEN", "当前身份无权限");
    }

    String qs(String name) {
        String v = ctx.query(name);
        if (v == null) {
            return "";
        }
        return v;
    }

    long ql(String name) {
        return WcNum.parseLong(qs(name));
    }

    long pid() {
        return WcNum.parseLong(ctx.param("id"));
    }

    String bs(String key) {
        return body.getString(key, "");
    }

    long bl(String key) {
        return body.getLong(key, 0L);
    }

    int bi(String key) {
        return body.getInt(key, 0);
    }
}

class WcApiHandler implements AsyncWebHandler {
    EventLoop loop;
    String route;
    int isPublic;

    WcApiHandler(EventLoop loop, String route, int isPublic) {
        this.loop = loop;
        this.route = route;
        this.isPublic = isPublic;
    }

    public JnFuture handle(WebContext ctx) {
        WcCtx c = new WcCtx(ctx, route);
        String out = "";
        if (isPublic == 0 && c.es.userId == 0L) {
            out = c.fail(401, "AUTH_REQUIRED", "请登录");
        } else {
            out = WcRouter.dispatch(c);
        }
        if (c.contentType.length() > 0) {
            ctx.setHeader("Content-Type", c.contentType);
            if (c.fileName.length() > 0) {
                ctx.setHeader("Content-Disposition", "attachment; filename=\"" + c.fileName + "\"");
            }
            ctx.status(c.status).text(out);
        } else {
            ctx.status(c.status).jsonString(out);
        }
        return JnFuture.completed(loop, null);
    }
}
