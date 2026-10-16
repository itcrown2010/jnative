/**
 * Wonder Campus HTTP server. Default port 18880. Never binds 18700.
 * @author 张晓达
 */
public class WonderServer {
    @Native(symbol = "exit")
    static native void exitProcess(int code);

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--e2e-test") || args[0].equals("--mobile-loop-test"))) {
            EventLoop loop = new EventLoop();
            try {
                WcDb.setup(loop);
                int pass = 1;
                if (args[0].equals("--e2e-test")) {
                    pass = WonderE2eTest.runAll();
                }
                if (pass != 0) {
                    pass = WonderMobileLoopTest.runAll();
                }
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL e2e exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }

        String portEnv = Env.get("WC_LISTEN_PORT");
        int port = 18880;
        if (portEnv != null && portEnv.length() > 0) {
            port = WebUtil.parseIntOr(portEnv, 18880);
        }
        if (port == 18700) {
            System.out.println("REFUSED: port 18700 reserved for ERP");
            exitProcess(3);
        }
        EventLoop loop = new EventLoop();
        Wc.sessions = new InMemorySessionStore();
        Wc.sessions.idleTimeoutMs = 86400000L;
        WcDb.setup(loop);
        ensureDemoSeed();

        WebApp app = new WebApp();
        app.config.keepAliveMax = 1;
        app.config.readTimeoutMs = 0;
        app.config.workers = 2;
        app.bindEventLoop(loop);
        app.use(WebMiddleware.logger());
        app.use(Cors.defaults());
        app.staticFiles("/", "examples/wonder-campus/mobile/www");

        app.futureGet("/api/wc/health", new WcHealthHandler(loop));
        app.futureGet("/api/wc/client-config", new WcClientConfigHandler(loop));
        app.futurePost("/api/wc/auth/demo", new WcDemoLoginHandler(loop));
        app.futurePost("/api/wc/auth/logout", new WcLogoutHandler(loop));
        app.futurePost("/api/wc/auth/switch-identity", new WcSwitchIdentityHandler(loop));
        app.futureGet("/api/wc/me", new WcMeHandler(loop));
        app.futureGet("/api/wc/home/admin", new WcHomeAdminHandler(loop));
        app.futureGet("/api/wc/home/teacher", new WcHomeTeacherHandler(loop));
        app.futureGet("/api/wc/home/parent", new WcHomeParentHandler(loop));
        app.futureGet("/api/wc/home/director", new WcHomeDirectorHandler(loop));
        app.futureGet("/api/wc/notifications", new WcNotifyHandler(loop));
        app.futureGet("/api/wc/contracts/:id/ledger", new WcLedgerHandler(loop));
        app.futureGet("/api/wc/sessions/today", new WcTodaySessionsHandler(loop));
        app.futureGet("/api/wc/students", new WcStudentsHandler(loop));

        app.futurePost("/api/wc/enroll/pack", new WcEnrollPackHandler(loop));
        app.futurePost("/api/wc/enroll/term", new WcEnrollTermHandler(loop));
        app.futurePost("/api/wc/payments", new WcPaymentHandler(loop));
        app.futurePost("/api/wc/payments/:id/refund", new WcRefundHandler(loop));
        app.futurePost("/api/wc/sessions", new WcCreateSessionHandler(loop));
        app.futurePost("/api/wc/sessions/:id/cancel", new WcCancelSessionHandler(loop));
        app.futurePost("/api/wc/sessions/:id/checkin", new WcCheckInHandler(loop));
        app.futurePost("/api/wc/attendance/:id/reverse", new WcReverseCheckInHandler(loop));
        app.futurePost("/api/wc/sessions/:id/feedback", new WcFeedbackHandler(loop));
        app.futurePost("/api/wc/leave", new WcLeaveHandler(loop));
        app.futurePost("/api/wc/leave/:id/approve-makeup", new WcApproveLeaveHandler(loop));
        app.futurePost("/api/wc/leave/:id/cancel", new WcCancelLeaveHandler(loop));
        app.futureGet("/api/wc/admin/today", new WcAdminTodayHandler(loop));
        app.futureGet("/api/wc/sessions/:id/roster", new WcRosterHandler(loop));
        app.futurePost("/api/wc/sessions/:id/batch-checkin", new WcBatchCheckInHandler(loop));
        app.futurePost("/api/wc/sessions/:id/complete", new WcCompleteSessionHandler(loop));
        app.futurePost("/api/wc/feedback/draft", new WcFeedbackDraftHandler(loop));
        app.futurePost("/api/wc/feedback/:id/publish", new WcFeedbackPublishHandler(loop));
        app.futureGet("/api/wc/parent/feedback", new WcParentFeedbackHandler(loop));
        app.futureGet("/api/wc/parent/children", new WcStudentsHandler(loop));
        app.futurePost("/api/wc/media", new WcMediaRegisterHandler(loop));
        app.futureGet("/api/wc/media/:id/meta", new WcMediaMetaHandler(loop));
        app.futureGet("/api/wc/feedback/templates", new WcTemplatesHandler(loop));
        app.futureGet("/api/wc/campuses", new WcCampusesHandler(loop));
        app.futureGet("/api/wc/leaves/pending", new WcPendingLeavesHandler(loop));
        app.futureGet("/api/wc/contracts", new WcContractsHandler(loop));
        app.futureGet("/api/wc/contracts/:id/payments", new WcPaymentsHandler(loop));
        app.futureGet("/api/wc/students/:id/makeup", new WcMakeupHandler(loop));
        app.futurePost("/api/wc/notifications/:id/read", new WcNotifyReadHandler(loop));
        app.futurePost("/api/wc/media/upload", new WcMediaUploadHandler(loop));
        app.futureGet("/api/wc/media/:id/content", new WcMediaContentHandler(loop));
        app.futurePost("/api/wc/media/:id/delete", new WcMediaDeleteHandler(loop));
        WcRouter.register(app, loop);

        String host = Env.get("WC_LISTEN_HOST");
        if (host == null || host.length() == 0) {
            host = "127.0.0.1";
        }
        String dbUrl = Env.get("DATABASE_URL");
        if (dbUrl == null || dbUrl.length() == 0) {
            dbUrl = "sqlite:./build/wonder-campus/wonder.db";
        }
        System.out.println("WONDER_CAMPUS listen http://" + host + ":" + String.valueOf(port));
        System.out.println("WONDER_CAMPUS db=" + dbUrl);
        /* WebApp binds port only; host printed for operators (default loopback via OS). */
        app.listenWithLoop(port);
    }

    static void ensureDemoSeed() {
        WonderDemoSeed.seedAll();
        WonderDemoSeed.seedCommercial();
        WcBizAuth.seedDemoPasswords();
        System.out.println("WONDER_CAMPUS demo seed OK");
    }
}

class WcSession {
    long userId;
    long orgId;
    String identity;
}

class WcAuth {
    static Session load(WebContext ctx) {
        SessionManager mgr = new SessionManager();
        mgr.store = Wc.sessions;
        return mgr.loadOrCreate(ctx);
    }

    static void save(WebContext ctx, Session session) {
        SessionManager mgr = new SessionManager();
        mgr.store = Wc.sessions;
        mgr.save(ctx, session);
    }

    static WcSession require(WebContext ctx) {
        WcSession out = new WcSession();
        out.userId = 0L;
        out.orgId = 0L;
        out.identity = "";
        Session s = load(ctx);
        String uid = s.getString("userId");
        String oid = s.getString("orgId");
        String idn = s.getString("identity");
        if (uid != null && uid.length() > 0) {
            out.userId = WcNum.parseLong(uid);
        }
        if (oid != null && oid.length() > 0) {
            out.orgId = WcNum.parseLong(oid);
        }
        if (idn != null) {
            out.identity = idn;
        }
        return out;
    }

    static int requireIdentity(WebContext ctx, WcSession es, String kind) {
        if (es == null || es.userId == 0L) {
            err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return 0;
        }
        if (es.identity.equals(kind) == false) {
            err(ctx, 403, "FORBIDDEN", "需要身份 " + kind);
            return 0;
        }
        return 1;
    }

    static void err(WebContext ctx, int status, String code, String message) {
        ctx.status(status).jsonString("{\"ok\":0,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    static void result(WebContext ctx, WcResult r) {
        if (r.ok == 0) {
            err(ctx, 400, r.code, r.message);
            return;
        }
        String doc = r.documentJson;
        if (doc == null || doc.length() == 0) {
            doc = "{}";
        }
        ctx.jsonString("{\"ok\":1,\"code\":\"" + r.code + "\",\"message\":\"" + r.message
                + "\",\"data\":" + doc + "}");
    }
}

class WcHealthHandler implements AsyncWebHandler {
    EventLoop loop;
    WcHealthHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ctx.jsonString("{\"ok\":1,\"service\":\"wonder-campus\",\"schemaReady\":"
                + String.valueOf(Wc.schemaReady) + ",\"portDefault\":18880}");
        return JnFuture.completed(loop, null);
    }
}

class WcClientConfigHandler implements AsyncWebHandler {
    EventLoop loop;
    WcClientConfigHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ctx.jsonString(WcQueryServices.clientConfigJson());
        return JnFuture.completed(loop, null);
    }
}

class WcDemoLoginHandler implements AsyncWebHandler {
    EventLoop loop;
    WcDemoLoginHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        String allow = Env.get("WC_ALLOW_DEMO_LOGIN");
        if (allow == null || allow.equals("1") == false) {
            WcAuth.err(ctx, 403, "DEMO_DISABLED", "演示登录未启用");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String login = body.getString("login", "admin");
        String identity = body.getString("identity", WcIdentity.ADMIN);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(login);
        Object uid = WcDb.queryOne("SELECT id FROM wc_users WHERE login=? ORDER BY id LIMIT 1", p,
                new LongMapper());
        if (uid == null) {
            WcAuth.err(ctx, 404, "NOT_FOUND", "用户不存在");
            return JnFuture.completed(loop, null);
        }
        long userId = WcNum.asLong(uid);
        ArrayList<Object> op = new ArrayList<Object>();
        op.add(CryptoParse.longToString(userId));
        Object org = WcDb.queryOne("SELECT org_id FROM wc_users WHERE id=?", op, new LongMapper());
        if (WcServices.hasIdentity(userId, identity) == 0) {
            WcAuth.err(ctx, 403, "NO_IDENTITY", "账号无此身份");
            return JnFuture.completed(loop, null);
        }
        Session session = WcAuth.load(ctx);
        session.rotateId();
        session.setString("userId", CryptoParse.longToString(userId));
        session.setString("orgId", CryptoParse.longToString(WcNum.asLong(org)));
        session.setString("identity", identity);
        WcAuth.save(ctx, session);
        ctx.jsonString("{\"ok\":1,\"userId\":" + CryptoParse.longToString(userId)
                + ",\"identity\":\"" + identity + "\",\"displayName\":\"" + login + "\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcLogoutHandler implements AsyncWebHandler {
    EventLoop loop;
    WcLogoutHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        Session session = WcAuth.load(ctx);
        session.invalidate();
        SessionManager mgr = new SessionManager();
        mgr.store = Wc.sessions;
        mgr.clearCookie(ctx);
        ctx.jsonString("{\"ok\":1}");
        return JnFuture.completed(loop, null);
    }
}

class WcSwitchIdentityHandler implements AsyncWebHandler {
    EventLoop loop;
    WcSwitchIdentityHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String identity = body.getString("identity", "");
        if (WcServices.hasIdentity(es.userId, identity) == 0) {
            WcAuth.err(ctx, 403, "NO_IDENTITY", "账号无此身份");
            return JnFuture.completed(loop, null);
        }
        Session session = WcAuth.load(ctx);
        session.setString("identity", identity);
        WcAuth.save(ctx, session);
        ctx.jsonString("{\"ok\":1,\"identity\":\"" + identity + "\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcMeHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMeHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.userId));
        DbQueryResult ids = WcDb.query(
                "SELECT identity_kind FROM wc_user_identities WHERE user_id=?", p);
        String arr = "[";
        int i = 0;
        while (i < ids.rows.size()) {
            if (i > 0) {
                arr = arr + ",";
            }
            arr = arr + "\"" + ids.rows.get(i).getString(0) + "\"";
            i = i + 1;
        }
        arr = arr + "]";
        String dname = WcApi.oneStr("SELECT display_name FROM wc_users WHERE id=?", WcApi.P1(es.userId));
        ctx.jsonString("{\"ok\":1,\"userId\":" + CryptoParse.longToString(es.userId)
                + ",\"displayName\":" + WcApi.q(dname)
                + ",\"orgId\":" + CryptoParse.longToString(es.orgId)
                + ",\"identity\":\"" + es.identity + "\",\"identities\":" + arr + "}");
        return JnFuture.completed(loop, null);
    }
}

class WcHomeAdminHandler implements AsyncWebHandler {
    EventLoop loop;
    WcHomeAdminHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        long now = AsyncClockNative.wallMillis();
        long dayEnd = now + 86400000L;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.orgId));
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(dayEnd));
        Object today = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND start_at>=? AND start_at<? AND status IN ('SCHEDULED','RESCHEDULED')",
                p, new LongMapper());
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(es.orgId));
        Object pending = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_leave_requests WHERE org_id=? AND status='PENDING'", lp,
                new LongMapper());
        ctx.jsonString("{\"ok\":1,\"role\":\"教务\",\"todaySessions\":"
                + CryptoParse.longToString(WcNum.asLong(today))
                + ",\"pendingLeaves\":" + CryptoParse.longToString(WcNum.asLong(pending))
                + ",\"conflicts\":0,\"hint\":\"今日课表与待处理请假\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcHomeTeacherHandler implements AsyncWebHandler {
    EventLoop loop;
    WcHomeTeacherHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.TEACHER) == false
                && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师身份");
            return JnFuture.completed(loop, null);
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.userId));
        p.add(CryptoParse.longToString(now));
        Object next = WcDb.queryOne(
                "SELECT id FROM wc_lesson_sessions WHERE teacher_user_id=? AND start_at>=? AND status IN ('SCHEDULED','RESCHEDULED') ORDER BY start_at LIMIT 1",
                p, new LongMapper());
        long dayStart = now - (now % 86400000L);
        long dayEnd = dayStart + 86400000L;
        ArrayList<Object> dp = new ArrayList<Object>();
        dp.add(CryptoParse.longToString(es.userId));
        dp.add(CryptoParse.longToString(dayStart));
        dp.add(CryptoParse.longToString(dayEnd));
        Object todayCnt = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE teacher_user_id=? AND start_at>=? AND start_at<? AND status IN ('SCHEDULED','RESCHEDULED','COMPLETED')",
                dp, new LongMapper());
        Object stuCnt = WcDb.queryOne(
                "SELECT COUNT(DISTINCT ss.student_id) FROM wc_session_students ss JOIN wc_lesson_sessions ls ON ls.id=ss.session_id WHERE ls.teacher_user_id=? AND ls.start_at>=? AND ls.start_at<?",
                dp, new LongMapper());
        ArrayList<Object> fp = new ArrayList<Object>();
        fp.add(CryptoParse.longToString(es.orgId));
        fp.add(CryptoParse.longToString(es.userId));
        Object draftCnt = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_feedback f JOIN wc_lesson_sessions ls ON ls.id=f.session_id WHERE f.org_id=? AND ls.teacher_user_id=? AND f.status='DRAFT'",
                fp, new LongMapper());
        ctx.jsonString("{\"ok\":1,\"role\":\"老师\",\"nextSessionId\":"
                + CryptoParse.longToString(WcNum.asLong(next))
                + ",\"todaySessionCount\":" + CryptoParse.longToString(WcNum.asLong(todayCnt))
                + ",\"todayStudentCount\":" + CryptoParse.longToString(WcNum.asLong(stuCnt))
                + ",\"pendingFeedbackCount\":" + CryptoParse.longToString(WcNum.asLong(draftCnt))
                + ",\"hint\":\"下一节课、签到与待反馈\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcHomeDirectorHandler implements AsyncWebHandler {
    EventLoop loop;
    WcHomeDirectorHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.DIRECTOR) == 0) {
            return JnFuture.completed(loop, null);
        }
        long now = AsyncClockNative.wallMillis();
        long dayEnd = now + 86400000L;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.orgId));
        p.add(CryptoParse.longToString(now - 3600000L));
        p.add(CryptoParse.longToString(dayEnd));
        Object today = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND start_at>=? AND start_at<?",
                p, new LongMapper());
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(es.orgId));
        Object pending = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_leave_requests WHERE org_id=? AND status='PENDING' AND cancelled_at=0",
                lp, new LongMapper());
        Object openMakeup = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_makeup_credits WHERE org_id=? AND status='OPEN'",
                lp, new LongMapper());
        Object campuses = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_campuses WHERE org_id=?", lp, new LongMapper());
        /* 收款/退款/课消等经营金额未提供可靠汇总接口，明确返回 unavailable，禁止前端填演示数 */
        ctx.jsonString("{\"ok\":1,\"role\":\"负责人\""
                + ",\"campusCount\":" + CryptoParse.longToString(WcNum.asLong(campuses))
                + ",\"todaySessionCount\":" + CryptoParse.longToString(WcNum.asLong(today))
                + ",\"pendingLeaves\":" + CryptoParse.longToString(WcNum.asLong(pending))
                + ",\"openMakeupCredits\":" + CryptoParse.longToString(WcNum.asLong(openMakeup))
                + ",\"receivedCents\":null,\"refundCents\":null,\"consumeRevenueCents\":null"
                + ",\"unavailableMetrics\":[\"receivedCents\",\"refundCents\",\"consumeRevenueCents\"]"
                + ",\"asOf\":" + CryptoParse.longToString(now)
                + ",\"hint\":\"仅展示可追溯运营计数；金额指标接口未提供\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcHomeParentHandler implements AsyncWebHandler {
    EventLoop loop;
    WcHomeParentHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.PARENT) == 0) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.userId));
        Object kids = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE g.user_id=?",
                p, new LongMapper());
        ArrayList<Object> np = new ArrayList<Object>();
        np.add(CryptoParse.longToString(es.userId));
        Object notes = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_notifications WHERE user_id=? AND read_at=0", np, new LongMapper());
        ctx.jsonString("{\"ok\":1,\"role\":\"家长\",\"linkedChildren\":"
                + CryptoParse.longToString(WcNum.asLong(kids))
                + ",\"unread\":" + CryptoParse.longToString(WcNum.asLong(notes))
                + ",\"hint\":\"下次上课、请假与成长动态\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcNotifyHandler implements AsyncWebHandler {
    EventLoop loop;
    WcNotifyHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.userId));
        DbQueryResult r = WcDb.query(
                "SELECT id, title, body, created_at FROM wc_notifications WHERE user_id=? ORDER BY id DESC LIMIT 20",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0))
                    + ",\"title\":\"" + r.rows.get(i).getString(1) + "\""
                    + ",\"body\":\"" + r.rows.get(i).getString(2) + "\"}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString("{\"ok\":1,\"items\":" + out + "}");
        return JnFuture.completed(loop, null);
    }
}

class WcLedgerHandler implements AsyncWebHandler {
    EventLoop loop;
    WcLedgerHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        long cid = WcNum.parseLong(ctx.param("id"));
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(cid));
        Object studentId = WcDb.queryOne("SELECT student_id FROM wc_contracts WHERE id=?", cp, new LongMapper());
        if (studentId == null) {
            WcAuth.err(ctx, 404, "NOT_FOUND", "合同不存在");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.PARENT)
                && WcServices.isGuardianOf(es.userId, WcNum.asLong(studentId)) == 0) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "无权查看该孩子课时");
            return JnFuture.completed(loop, null);
        }
        DbQueryResult r = WcDb.query(
                "SELECT entry_type, bucket, milli_delta, note, created_at, session_id FROM wc_lesson_ledger WHERE contract_id=? ORDER BY id",
                cp);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"type\":\"" + r.rows.get(i).getString(0) + "\""
                    + ",\"bucket\":\"" + r.rows.get(i).getString(1) + "\""
                    + ",\"milliDelta\":" + CryptoParse.longToString(r.rows.get(i).getLong(2))
                    + ",\"note\":\"" + r.rows.get(i).getString(3) + "\""
                    + ",\"createdAt\":" + CryptoParse.longToString(r.rows.get(i).getLong(4))
                    + ",\"sessionId\":" + CryptoParse.longToString(r.rows.get(i).getLong(5)) + "}";
            i = i + 1;
        }
        out = out + "]";
        long rem = WcServices.packRemaining(cid);
        ctx.jsonString("{\"ok\":1,\"remainingMilli\":" + CryptoParse.longToString(rem)
                + ",\"unit\":\"lesson_milli(1000=1课时)\",\"items\":" + out + "}");
        return JnFuture.completed(loop, null);
    }
}

class WcTodaySessionsHandler implements AsyncWebHandler {
    EventLoop loop;
    WcTodaySessionsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        long now = AsyncClockNative.wallMillis();
        long dayEnd = now + 86400000L * 7L;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.orgId));
        p.add(CryptoParse.longToString(now - 86400000L));
        p.add(CryptoParse.longToString(dayEnd));
        DbQueryResult r = WcDb.query(
                "SELECT id, start_at, end_at, status, student_id, teacher_user_id, contract_id, campus_id, room_id, class_id FROM wc_lesson_sessions WHERE org_id=? AND start_at>=? AND start_at<? ORDER BY start_at LIMIT 50",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            long classId = r.rows.get(i).getLong(9);
            long teacherId = r.rows.get(i).getLong(5);
            long campusId = r.rows.get(i).getLong(7);
            long roomId = r.rows.get(i).getLong(8);
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0))
                    + ",\"startAt\":" + CryptoParse.longToString(r.rows.get(i).getLong(1))
                    + ",\"endAt\":" + CryptoParse.longToString(r.rows.get(i).getLong(2))
                    + ",\"status\":\"" + r.rows.get(i).getString(3) + "\""
                    + ",\"studentId\":" + CryptoParse.longToString(r.rows.get(i).getLong(4))
                    + ",\"teacherUserId\":" + CryptoParse.longToString(teacherId)
                    + ",\"contractId\":" + CryptoParse.longToString(r.rows.get(i).getLong(6))
                    + ",\"campusId\":" + CryptoParse.longToString(campusId)
                    + ",\"roomId\":" + CryptoParse.longToString(roomId)
                    + ",\"classId\":" + CryptoParse.longToString(classId)
                    + WcLoopServices.sessionLabelsJson(classId, teacherId, campusId, roomId) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString("{\"ok\":1,\"items\":" + out + "}");
        return JnFuture.completed(loop, null);
    }
}

class WcStudentsHandler implements AsyncWebHandler {
    EventLoop loop;
    WcStudentsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        DbQueryResult r = null;
        if (es.identity.equals(WcIdentity.PARENT)) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(es.userId));
            r = WcDb.query(
                    "SELECT s.id, s.name, s.campus_id FROM wc_students s JOIN wc_student_guardians sg ON sg.student_id=s.id JOIN wc_guardians g ON g.id=sg.guardian_id WHERE g.user_id=?",
                    p);
        } else {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(es.orgId));
            r = WcDb.query("SELECT id, name, campus_id FROM wc_students WHERE org_id=? ORDER BY id", p);
        }
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0))
                    + ",\"name\":\"" + r.rows.get(i).getString(1) + "\""
                    + ",\"campusId\":" + CryptoParse.longToString(r.rows.get(i).getLong(2)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString("{\"ok\":1,\"items\":" + out + "}");
        return JnFuture.completed(loop, null);
    }
}

class WcEnrollPackHandler implements AsyncWebHandler {
    EventLoop loop;
    WcEnrollPackHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcRuleSnapshot rules = WcRuleSnapshot.defaults();
        long expire = body.getLong("expireAt", AsyncClockNative.wallMillis() + 86400000L * 180L);
        WcResult r = WcServices.enrollPack(es.orgId, body.getLong("campusId", 0L),
                body.getLong("studentId", 0L), body.getLong("courseId", 0L), body.getLong("classId", 0L),
                body.getLong("paidMilli", 0L), body.getLong("bonusMilli", 0L), expire,
                body.getLong("listPriceCents", 0L), body.getLong("discountCents", 0L), es.userId,
                body.getString("idempotencyKey", ""), rules);
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcEnrollTermHandler implements AsyncWebHandler {
    EventLoop loop;
    WcEnrollTermHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcRuleSnapshot rules = WcRuleSnapshot.defaults();
        WcResult r = WcServices.enrollTerm(es.orgId, body.getLong("campusId", 0L),
                body.getLong("studentId", 0L), body.getLong("courseId", 0L), body.getLong("classId", 0L),
                body.getLong("termStart", 0L), body.getLong("termEnd", 0L),
                body.getInt("promisedSessions", 0), body.getLong("listPriceCents", 0L),
                body.getLong("discountCents", 0L), es.userId, body.getString("idempotencyKey", ""), rules);
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcPaymentHandler implements AsyncWebHandler {
    EventLoop loop;
    WcPaymentHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcResult r = WcServices.registerPayment(es.orgId, body.getLong("contractId", 0L),
                body.getLong("amountCents", 0L), body.getString("method", WcPayMethod.CASH),
                body.getString("receiptNo", ""), es.userId, body.getString("idempotencyKey", ""));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcRefundHandler implements AsyncWebHandler {
    EventLoop loop;
    WcRefundHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long payId = WcNum.parseLong(ctx.param("id"));
        WcResult r = WcServices.refundPayment(es.orgId, payId, body.getLong("amountCents", 0L),
                body.getString("reason", ""), es.userId, body.getString("idempotencyKey", ""));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcCreateSessionHandler implements AsyncWebHandler {
    EventLoop loop;
    WcCreateSessionHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcResult r = WcServices.createSession(es.orgId, body.getLong("campusId", 0L),
                body.getLong("classId", 0L), body.getLong("teacherUserId", 0L), body.getLong("roomId", 0L),
                body.getLong("startAt", 0L), body.getLong("endAt", 0L), body.getLong("studentId", 0L),
                body.getLong("contractId", 0L), body.getLong("seriesId", 0L));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcCancelSessionHandler implements AsyncWebHandler {
    EventLoop loop;
    WcCancelSessionHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long sid = WcNum.parseLong(ctx.param("id"));
        WcResult r = WcServices.schoolCancelSession(es.orgId, sid, body.getString("reason", ""), es.userId);
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcCheckInHandler implements AsyncWebHandler {
    EventLoop loop;
    WcCheckInHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.TEACHER) == false
                && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师或教务身份");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long sid = WcNum.parseLong(ctx.param("id"));
        WcResult r = WcServices.checkIn(es.orgId, sid, body.getLong("studentId", 0L), es.userId,
                body.getString("idempotencyKey", ""));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcReverseCheckInHandler implements AsyncWebHandler {
    EventLoop loop;
    WcReverseCheckInHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.ADMIN) == false
                && es.identity.equals(WcIdentity.TEACHER) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师或教务身份");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long attId = WcNum.parseLong(ctx.param("id"));
        /* 老师仅可纠错本人可操作课次上的考勤 */
        if (es.identity.equals(WcIdentity.TEACHER)) {
            ArrayList<Object> ap = new ArrayList<Object>();
            ap.add(CryptoParse.longToString(attId));
            Object sid = WcDb.queryOne("SELECT session_id FROM wc_attendance WHERE id=?", ap, new LongMapper());
            if (sid == null || WcLoopServices.teacherCanOperateSession(es.userId, WcNum.asLong(sid)) == 0) {
                WcAuth.err(ctx, 403, "FORBIDDEN", "无权纠错该考勤");
                return JnFuture.completed(loop, null);
            }
        }
        WcResult r = WcServices.reverseCheckIn(es.orgId, attId, es.userId,
                body.getString("idempotencyKey", ""));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcFeedbackHandler implements AsyncWebHandler {
    EventLoop loop;
    WcFeedbackHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.TEACHER) == false
                && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师身份");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long sid = WcNum.parseLong(ctx.param("id"));
        WcResult r = WcServices.publishFeedback(es.orgId, sid, body.getLong("studentId", 0L), es.userId,
                body.getString("tags", ""), body.getString("body", ""),
                body.getString("idempotencyKey", ""));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcLeaveHandler implements AsyncWebHandler {
    EventLoop loop;
    WcLeaveHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.PARENT) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcResult r = WcServices.requestLeave(es.orgId, body.getLong("sessionId", 0L),
                body.getLong("studentId", 0L), es.userId, body.getString("reason", ""),
                body.getString("idempotencyKey", ""));
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcApproveLeaveHandler implements AsyncWebHandler {
    EventLoop loop;
    WcApproveLeaveHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long leaveId = WcNum.parseLong(ctx.param("id"));
        WcResult r = WcServices.approveLeaveAndMakeup(es.orgId, leaveId, body.getLong("makeupStart", 0L),
                body.getLong("makeupEnd", 0L), body.getLong("teacherUserId", 0L),
                body.getLong("roomId", 0L), es.userId);
        WcAuth.result(ctx, r);
        return JnFuture.completed(loop, null);
    }
}

class WcCancelLeaveHandler implements AsyncWebHandler {
    EventLoop loop;
    WcCancelLeaveHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.PARENT) == 0) {
            return JnFuture.completed(loop, null);
        }
        long leaveId = WcNum.parseLong(ctx.param("id"));
        WcAuth.result(ctx, WcLoopServices.cancelLeave(es.orgId, leaveId, es.userId));
        return JnFuture.completed(loop, null);
    }
}

class WcAdminTodayHandler implements AsyncWebHandler {
    EventLoop loop;
    WcAdminTodayHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        long campusId = WcNum.parseLong(ctx.query("campusId"));
        long teacherId = WcNum.parseLong(ctx.query("teacherId"));
        long roomId = WcNum.parseLong(ctx.query("roomId"));
        ctx.jsonString(WcLoopServices.adminTodayJson(es.orgId, campusId, teacherId, roomId));
        return JnFuture.completed(loop, null);
    }
}

class WcRosterHandler implements AsyncWebHandler {
    EventLoop loop;
    WcRosterHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        long sid = WcNum.parseLong(ctx.param("id"));
        if (es.identity.equals(WcIdentity.TEACHER) && WcLoopServices.teacherCanOperateSession(es.userId, sid) == 0) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "无权查看该课名单");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(WcLoopServices.sessionRosterJson(sid));
        return JnFuture.completed(loop, null);
    }
}

class WcBatchCheckInHandler implements AsyncWebHandler {
    EventLoop loop;
    WcBatchCheckInHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.TEACHER) == false && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师或教务");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long sid = WcNum.parseLong(ctx.param("id"));
        WcAuth.result(ctx, WcLoopServices.batchCheckIn(es.orgId, sid, body.getString("studentIds", ""),
                es.userId, body.getString("idempotencyKey", "")));
        return JnFuture.completed(loop, null);
    }
}

class WcCompleteSessionHandler implements AsyncWebHandler {
    EventLoop loop;
    WcCompleteSessionHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.identity.equals(WcIdentity.TEACHER) == false && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师或教务");
            return JnFuture.completed(loop, null);
        }
        long sid = WcNum.parseLong(ctx.param("id"));
        WcAuth.result(ctx, WcLoopServices.completeSession(es.orgId, sid, es.userId));
        return JnFuture.completed(loop, null);
    }
}

class WcFeedbackDraftHandler implements AsyncWebHandler {
    EventLoop loop;
    WcFeedbackDraftHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.identity.equals(WcIdentity.TEACHER) == false && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcAuth.result(ctx, WcLoopServices.saveFeedbackDraft(es.orgId, body.getLong("sessionId", 0L),
                body.getLong("studentId", 0L), es.userId, body.getString("explored", ""),
                body.getString("attempt", ""), body.getString("encouragement", ""),
                body.getString("tags", ""), body.getString("idempotencyKey", "")));
        return JnFuture.completed(loop, null);
    }
}

class WcFeedbackPublishHandler implements AsyncWebHandler {
    EventLoop loop;
    WcFeedbackPublishHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.identity.equals(WcIdentity.TEACHER) == false && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "需要老师");
            return JnFuture.completed(loop, null);
        }
        long fid = WcNum.parseLong(ctx.param("id"));
        WcAuth.result(ctx, WcLoopServices.publishFeedbackDraft(es.orgId, fid, es.userId));
        return JnFuture.completed(loop, null);
    }
}

class WcParentFeedbackHandler implements AsyncWebHandler {
    EventLoop loop;
    WcParentFeedbackHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.PARENT) == 0) {
            return JnFuture.completed(loop, null);
        }
        long studentId = WcNum.parseLong(ctx.query("studentId"));
        ctx.jsonString(WcLoopServices.parentFeedbackJson(es.userId, studentId));
        return JnFuture.completed(loop, null);
    }
}

class WcMediaRegisterHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMediaRegisterHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcAuth.result(ctx, WcLoopServices.registerMedia(es.orgId, es.userId, body.getLong("studentId", 0L),
                body.getLong("sessionId", 0L), body.getLong("feedbackId", 0L),
                body.getString("storagePath", ""), body.getString("mime", "image/jpeg"),
                body.getLong("sizeBytes", 0L)));
        return JnFuture.completed(loop, null);
    }
}

class WcMediaMetaHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMediaMetaHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        long mid = WcNum.parseLong(ctx.param("id"));
        if (WcLoopServices.canReadMedia(es.userId, es.identity, mid) == 0) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "无权读取该媒体");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(mid));
        DbQueryResult r = WcDb.query(
                "SELECT id, mime, size_bytes, storage_path, student_id FROM wc_media_assets WHERE id=? AND deleted_at=0",
                p);
        if (r.rows.size() == 0) {
            WcAuth.err(ctx, 404, "NOT_FOUND", "媒体不存在");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"ok\":1,\"mediaId\":" + CryptoParse.longToString(r.rows.get(0).getLong(0))
                + ",\"mime\":\"" + r.rows.get(0).getString(1) + "\",\"sizeBytes\":"
                + CryptoParse.longToString(r.rows.get(0).getLong(2))
                + ",\"note\":\"内容需鉴权读取，非公开链接\"}");
        return JnFuture.completed(loop, null);
    }
}

class WcTemplatesHandler implements AsyncWebHandler {
    EventLoop loop;
    WcTemplatesHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请登录");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.orgId));
        DbQueryResult r = WcDb.query(
                "SELECT subject, title, body_hint FROM wc_feedback_templates WHERE org_id=?", p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"subject\":\"" + r.rows.get(i).getString(0) + "\",\"title\":\""
                    + r.rows.get(i).getString(1) + "\",\"hint\":\"" + r.rows.get(i).getString(2) + "\"}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString("{\"ok\":1,\"items\":" + out + "}");
        return JnFuture.completed(loop, null);
    }
}

class WcCampusesHandler implements AsyncWebHandler {
    EventLoop loop;
    WcCampusesHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(WcQueryServices.campusesJson(es.orgId));
        return JnFuture.completed(loop, null);
    }
}

class WcPendingLeavesHandler implements AsyncWebHandler {
    EventLoop loop;
    WcPendingLeavesHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (WcAuth.requireIdentity(ctx, es, WcIdentity.ADMIN) == 0) {
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(WcQueryServices.pendingLeavesJson(es.orgId));
        return JnFuture.completed(loop, null);
    }
}

class WcContractsHandler implements AsyncWebHandler {
    EventLoop loop;
    WcContractsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        long studentId = WcNum.parseLong(ctx.query("studentId"));
        if (es.identity.equals(WcIdentity.PARENT) && studentId > 0L
                && WcServices.isGuardianOf(es.userId, studentId) == 0) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "无权查看该孩子合同");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(WcQueryServices.contractsJson(es.orgId, studentId));
        return JnFuture.completed(loop, null);
    }
}

class WcPaymentsHandler implements AsyncWebHandler {
    EventLoop loop;
    WcPaymentsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        long cid = WcNum.parseLong(ctx.param("id"));
        ctx.jsonString(WcQueryServices.paymentsJson(es.orgId, cid));
        return JnFuture.completed(loop, null);
    }
}

class WcMakeupHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMakeupHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        long studentId = WcNum.parseLong(ctx.param("id"));
        if (es.identity.equals(WcIdentity.PARENT) && WcServices.isGuardianOf(es.userId, studentId) == 0) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "无权查看");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(WcQueryServices.makeupCreditsJson(es.orgId, studentId));
        return JnFuture.completed(loop, null);
    }
}

class WcNotifyReadHandler implements AsyncWebHandler {
    EventLoop loop;
    WcNotifyReadHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        long nid = WcNum.parseLong(ctx.param("id"));
        WcQueryServices.markNotifyRead(es.userId, nid);
        ctx.jsonString("{\"ok\":1}");
        return JnFuture.completed(loop, null);
    }
}

class WcMediaUploadHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMediaUploadHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        if (es.identity.equals(WcIdentity.TEACHER) == false && es.identity.equals(WcIdentity.ADMIN) == false) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "仅老师可上传课堂作品");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        WcAuth.result(ctx, WcMediaService.uploadBase64(es.orgId, es.userId, body.getLong("studentId", 0L),
                body.getLong("sessionId", 0L), body.getLong("feedbackId", 0L), body.getString("mime", ""),
                body.getString("dataBase64", ""), body.getString("idempotencyKey", "")));
        return JnFuture.completed(loop, null);
    }
}

class WcMediaContentHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMediaContentHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        long mid = WcNum.parseLong(ctx.param("id"));
        if (WcLoopServices.canReadMedia(es.userId, es.identity, mid) == 0) {
            WcAuth.err(ctx, 403, "FORBIDDEN", "无权读取该作品");
            return JnFuture.completed(loop, null);
        }
        String path = WcMediaService.filePathOf(mid);
        if (path.length() == 0) {
            WcAuth.err(ctx, 404, "NOT_FOUND", "作品不存在");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> mp = new ArrayList<Object>();
        mp.add(CryptoParse.longToString(mid));
        Object mime = WcDb.queryOne("SELECT mime FROM wc_media_assets WHERE id=?", mp, new StringMapper());
        byte[] data = File.readBytes(path);
        if (data == null) {
            WcAuth.err(ctx, 404, "NOT_FOUND", "文件缺失");
            return JnFuture.completed(loop, null);
        }
        String ct = mime == null ? "application/octet-stream" : (String) mime;
        ctx.setHeader("Content-Type", ct);
        ctx.setHeader("Cache-Control", "private, no-store");
        ctx.status(200).bytes(data);
        return JnFuture.completed(loop, null);
    }
}

class WcMediaDeleteHandler implements AsyncWebHandler {
    EventLoop loop;
    WcMediaDeleteHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        WcSession es = WcAuth.require(ctx);
        if (es.userId == 0L) {
            WcAuth.err(ctx, 401, "AUTH_REQUIRED", "请重新登录");
            return JnFuture.completed(loop, null);
        }
        long mid = WcNum.parseLong(ctx.param("id"));
        WcAuth.result(ctx, WcMediaService.deleteMedia(es.orgId, mid, es.userId));
        return JnFuture.completed(loop, null);
    }
}
