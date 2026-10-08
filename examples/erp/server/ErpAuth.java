import jnative.web.*;

class ErpAuth {
    static String nextRequestId() {
        Erp.requestSeq = Erp.requestSeq + 1L;
        return "req-" + CryptoParse.longToString(Erp.requestSeq);
    }

    static Session load(WebContext ctx) {
        SessionManager mgr = new SessionManager();
        mgr.store = Erp.sessions;
        return mgr.loadOrCreate(ctx);
    }

    static void save(WebContext ctx, Session session) {
        SessionManager mgr = new SessionManager();
        mgr.store = Erp.sessions;
        mgr.save(ctx, session);
    }

    static ErpSession requireSession(WebContext ctx) {
        ErpSession out = new ErpSession();
        out.userId = 0L;
        out.companyId = 0L;
        Session s = load(ctx);
        String uid = s.getString("userId");
        String cid = s.getString("companyId");
        if (uid != null && uid.length() > 0) {
            out.userId = ErpNum.parseLong(uid);
        }
        if (cid != null && cid.length() > 0) {
            out.companyId = ErpNum.parseLong(cid);
        }
        return out;
    }

    static int checkCsrf(WebContext ctx) {
        Session s = load(ctx);
        String expect = s.getString("csrf");
        String got = ctx.getHeader("X-CSRF-Token");
        if (got == null || got.length() == 0) {
            got = ctx.query("csrf");
        }
        if (expect == null || expect.length() == 0 || got == null || expect.equals(got) == false) {
            return 0;
        }
        return 1;
    }

    static void err(WebContext ctx, int status, String code, String message) {
        JsonObject o = new JsonObject();
        o.putString("code", code);
        o.putString("message", message);
        o.putString("requestId", nextRequestId());
        ctx.status(status).jsonString(Json.stringify(o));
    }

    static UserRow loadUserByEmail(String email) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(email);
        Object v = ErpDb.queryOne(
                "SELECT id, company_id, email, display_name, password_hash, role FROM users WHERE email=?",
                p, new ErpUserRowMapper());
        if (v == null) {
            return null;
        }
        return (UserRow) v;
    }

    static UserRow loadUser(long userId, long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT id, company_id, email, display_name, password_hash, role FROM users WHERE id=? AND company_id=?",
                p, new ErpUserRowMapper());
        if (v == null) {
            return null;
        }
        return (UserRow) v;
    }
}

class ErpSession {
    long userId;
    long companyId;
}

class UserRow {
    long id;
    long companyId;
    String email;
    String displayName;
    String passwordHash;
    String role;
}

class ErpUserRowMapper implements RowMapper {
    public Object map(Row row) {
        UserRow u = new UserRow();
        u.id = row.getLong(0);
        u.companyId = row.getLong(1);
        u.email = row.getString(2);
        u.displayName = row.getString(3);
        u.passwordHash = row.getString(4);
        u.role = row.getString(5);
        return u;
    }
}
