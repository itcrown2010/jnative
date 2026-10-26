/**
 * HTTP surface of the B1 layer: master data CRUD, pricing / credit / FX helpers and marketing documents.
 * One handler class, one instance per route (kind selects the behavior).
 * @author 张晓达
 */
class ErpB1Handler implements AsyncWebHandler {
    EventLoop loop;
    String kind;

    ErpB1Handler(EventLoop loop, String kind) {
        this.loop = loop;
        this.kind = kind;
    }

    static void register(WebApp app, EventLoop loop) {
        app.futureGet("/api/erp/b1/m/:entity", ErpLocked.wrap(new ErpB1Handler(loop, "mList")));
        app.futurePost("/api/erp/b1/m/:entity", ErpLocked.wrap(new ErpB1Handler(loop, "mCreate")));
        app.futureGet("/api/erp/b1/m/:entity/:id", ErpLocked.wrap(new ErpB1Handler(loop, "mGet")));
        app.futurePost("/api/erp/b1/m/:entity/:id", ErpLocked.wrap(new ErpB1Handler(loop, "mUpdate")));
        app.futurePost("/api/erp/b1/m/:entity/:id/delete", ErpLocked.wrap(new ErpB1Handler(loop, "mDelete")));
        app.futureGet("/api/erp/b1/pricing", ErpLocked.wrap(new ErpB1Handler(loop, "pricing")));
        app.futureGet("/api/erp/b1/credit/:id", ErpLocked.wrap(new ErpB1Handler(loop, "credit")));
        app.futureGet("/api/erp/b1/rate", ErpLocked.wrap(new ErpB1Handler(loop, "rate")));
        app.futureGet("/api/erp/b1/open-lines", ErpLocked.wrap(new ErpB1Handler(loop, "openLines")));
        app.futurePost("/api/erp/b1/copy/:type", ErpLocked.wrap(new ErpB1Handler(loop, "copy")));
        app.futureGet("/api/erp/b1/docs/:type", ErpLocked.wrap(new ErpB1Handler(loop, "dList")));
        app.futurePost("/api/erp/b1/docs/:type", ErpLocked.wrap(new ErpB1Handler(loop, "dCreate")));
        app.futureGet("/api/erp/b1/docs/:type/:id", ErpLocked.wrap(new ErpB1Handler(loop, "dGet")));
        app.futurePost("/api/erp/b1/docs/:type/:id", ErpLocked.wrap(new ErpB1Handler(loop, "dUpdate")));
        app.futurePost("/api/erp/b1/docs/:type/:id/submit", ErpLocked.wrap(new ErpB1Handler(loop, "dSubmit")));
        app.futurePost("/api/erp/b1/docs/:type/:id/post", ErpLocked.wrap(new ErpB1Handler(loop, "dPost")));
        app.futurePost("/api/erp/b1/docs/:type/:id/cancel", ErpLocked.wrap(new ErpB1Handler(loop, "dCancel")));
        app.futurePost("/api/erp/b1/docs/:type/:id/reverse", ErpLocked.wrap(new ErpB1Handler(loop, "dReverse")));
        ErpB1Reports.register(app, loop);
    }

    static String createPerm(String type) {
        if (ErpB1Docs.isSales(type) != 0) {
            return "SALES.ORDER.CREATE";
        }
        return "PURCHASE.ORDER.CREATE";
    }

    static String postPerm(String type) {
        if (ErpB1Docs.isSales(type) != 0) {
            return "SALES.ORDER.POST";
        }
        return "PURCHASE.ORDER.POST";
    }

    public JnFuture handle(WebContext ctx) {
        try {
            dispatch(ctx);
        } catch (Exception ex) {
            String m = ErpB1Util.exMsg(ex, "request failed");
            int status = 400;
            String code = "B1_ERROR";
            if (m.equals("not found")) {
                status = 404;
                code = "NOT_FOUND";
            } else if (m.equals("DUPLICATE_KEY")) {
                status = 409;
                code = "DUPLICATE_KEY";
            }
            ErpB1Util.err(ctx, status, code, m);
        }
        return JnFuture.completed(loop, null);
    }

    void dispatch(WebContext ctx) {
        if (kind.startsWith("m")) {
            masterRoute(ctx);
            return;
        }
        if (kind.equals("pricing") || kind.equals("credit") || kind.equals("rate") || kind.equals("openLines")) {
            helperRoute(ctx);
            return;
        }
        docRoute(ctx);
    }

    void masterRoute(WebContext ctx) {
        boolean write = kind.equals("mCreate") || kind.equals("mUpdate") || kind.equals("mDelete");
        ErpSession es = null;
        if (write) {
            es = ErpRequire.authPerm(ctx, "MASTER.DATA.MANAGE");
        } else {
            es = ErpRequire.authPerm(ctx, null);
        }
        if (es == null) {
            return;
        }
        B1Entity e = ErpB1Master.entity(ctx.param("entity"));
        if (e == null) {
            ErpB1Util.err(ctx, 404, "NOT_FOUND", "unknown master entity");
            return;
        }
        ErpB1Master.ensureDefaultsOnce(es.companyId);
        if (kind.equals("mList")) {
            ctx.jsonString(ErpB1Master.list(es.companyId, e, ctx));
            return;
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        if (kind.equals("mGet")) {
            String j = ErpB1Master.get(es.companyId, e, id);
            if (j == null) {
                throw new RuntimeException("not found");
            }
            ctx.jsonString(j);
            return;
        }
        if (kind.equals("mDelete")) {
            ErpB1Master.remove(es.companyId, e, id);
            ctx.jsonString("{\"ok\":true}");
            return;
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        if (kind.equals("mCreate")) {
            ctx.jsonString(ErpB1Master.save(es.companyId, e, 0L, body));
            return;
        }
        ctx.jsonString(ErpB1Master.save(es.companyId, e, id, body));
    }

    void helperRoute(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, null);
        if (es == null) {
            return;
        }
        ErpB1Master.ensureDefaultsOnce(es.companyId);
        if (kind.equals("pricing")) {
            long partnerId = ErpNum.parseLong(ctx.query("partnerId"));
            long itemId = ErpNum.parseLong(ctx.query("itemId"));
            String side = ctx.query("side");
            int sales = 1;
            if (side != null && side.equals("purchase")) {
                sales = 0;
            }
            ctx.jsonString(ErpB1Master.pricingJson(es.companyId, partnerId, itemId, sales));
            return;
        }
        if (kind.equals("credit")) {
            long pid = ErpNum.parseLong(ctx.param("id"));
            ctx.jsonString(ErpB1Master.creditJson(es.companyId, pid, ErpNum.parseLong(ctx.query("extra"))));
            return;
        }
        if (kind.equals("rate")) {
            String cur = ctx.query("currency");
            long date = ErpNum.parseLong(ctx.query("date"));
            if (date <= 0L) {
                date = AsyncClockNative.wallMillis();
            }
            long rate = ErpB1Master.rateMicro(es.companyId, cur, date);
            ctx.jsonString("{\"currency\":" + ErpJson.str(cur) + ",\"rateMicro\":" + ErpB1Util.lng(rate)
                    + ",\"local\":" + ErpJson.str(ErpB1Master.localCurrency(es.companyId)) + "}");
            return;
        }
        ctx.jsonString(ErpB1Docs.openLines(es.companyId, ctx.query("baseType"), ErpNum.parseLong(ctx.query("baseId"))));
    }

    void docRoute(WebContext ctx) {
        String type = ctx.param("type");
        if (kind.equals("copy")) {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            String fromType = ErpB1Util.text(body, "fromType", "");
            ErpSession ces = ErpRequire.authPerm(ctx, createPerm(type.equals("SO") ? ErpB1Docs.SQ : type.equals("PO") ? ErpB1Docs.PQ : type));
            if (ces == null) {
                return;
            }
            ErpB1Master.ensureDefaultsOnce(ces.companyId);
            long fromId = ErpNum.parseLong(ErpB1Util.text(body, "fromId", "0"));
            ctx.jsonString(ErpB1DocCopy.copy(ces.companyId, ces.userId, type, fromType, fromId, body));
            return;
        }
        if (ErpB1Docs.validType(type) == 0) {
            ErpB1Util.err(ctx, 404, "NOT_FOUND", "unknown document type");
            return;
        }
        boolean read = kind.equals("dList") || kind.equals("dGet");
        boolean post = kind.equals("dPost") || kind.equals("dReverse");
        String perm = null;
        if (read == false) {
            perm = createPerm(type);
            if (post) {
                perm = postPerm(type);
            }
        }
        ErpSession es = ErpRequire.authPerm(ctx, perm);
        if (es == null) {
            return;
        }
        ErpB1Master.ensureDefaultsOnce(es.companyId);
        if (kind.equals("dList")) {
            ctx.jsonString(ErpB1Docs.list(es.companyId, type, ctx));
            return;
        }
        if (kind.equals("dCreate")) {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            B1Doc d = ErpB1Docs.parseBody(es.companyId, type, body);
            String idem = ctx.getHeader("Idempotency-Key");
            if (idem != null && idem.length() > 0) {
                d.idempotencyKey = idem;
            }
            d.createdBy = es.userId;
            ErpB1Docs.resolve(es.companyId, d);
            String saved = ErpB1Docs.saveJson(es.companyId, es.userId, d, 0L);
            ErpDb.reconnect();
            ctx.jsonString(ErpB1Docs.get(es.companyId, type, ErpB1Docs.idOf(saved)));
            return;
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        if (kind.equals("dGet")) {
            String j = ErpB1Docs.get(es.companyId, type, id);
            if (j == null) {
                throw new RuntimeException("not found");
            }
            ctx.jsonString(j);
            return;
        }
        if (kind.equals("dUpdate")) {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            B1Doc d = ErpB1Docs.parseBody(es.companyId, type, body);
            d.idempotencyKey = "";
            String st = ErpB1Docs.statusOf(es.companyId, type, id);
            if (st.length() == 0) {
                throw new RuntimeException("not found");
            }
            if (st.equals("DRAFT") == false) {
                throw new RuntimeException("only DRAFT documents can be edited (status=" + st + ")");
            }
            ErpB1Docs.resolveForUpdate(es.companyId, d, id);
            ErpB1Docs.saveJson(es.companyId, es.userId, d, id);
            ErpDb.reconnect();
            ctx.jsonString(ErpB1Docs.get(es.companyId, type, id));
            return;
        }
        if (kind.equals("dSubmit")) {
            ctx.jsonString(ErpB1Docs.submit(es.companyId, type, id));
            return;
        }
        if (kind.equals("dCancel")) {
            ctx.jsonString(ErpB1Docs.cancel(es.companyId, type, id));
            return;
        }
        if (kind.equals("dPost")) {
            ctx.jsonString(ErpB1DocPost.post(es.companyId, es.userId, type, id));
            return;
        }
        ctx.jsonString(ErpB1DocPost.reverse(es.companyId, es.userId, type, id));
    }
}
