import jnative.web.*;
import jnative.json.*;

/**
 * JNative ERP Phase-1 — purchase/sales/inventory posting loop.
 *
 * Default DB: postgresql://jnative_erp@127.0.0.1:5432/jnative_erp
 * Fallback:    DATABASE_URL=sqlite:./build/erp/erp.db
 */
public class ErpServer {
    @Native(symbol = "exit")
    static native void exitProcess(int code);

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--logic-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--finance-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpFinanceLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL finance exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--manufacturing-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpManufacturingLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL manufacturing exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--enterprise-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpEnterpriseLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL enterprise exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--version")) {
            ErpProductConfig.load();
            System.out.println(ErpProductConfig.publicVersionJson());
            exitProcess(0);
        }
        if (args.length > 0 && args[0].equals("--integration-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpIntegrationLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL integration exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--integrity-check")) {
            ErpProductConfig.load();
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                long companyId = 0L;
                Object c = ErpDb.queryOne(
                        "SELECT id FROM companies ORDER BY id LIMIT 1", new ArrayList<Object>(), new LongMapper());
                if (c != null) {
                    companyId = ErpNum.asLong(c);
                }
                int pass = ErpIntegrity.runAll(companyId);
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL integrity exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--pilot-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpPilotAcceptTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL pilot exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--reset-demo")) {
            ErpProductConfig.load();
            if (ErpProductConfig.demoMode == 0) {
                System.out.println("REFUSED: set ERP_DEMO_MODE=1 to reset demo data (production protected)");
                exitProcess(3);
            }
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                Object c = ErpDb.queryOne(
                        "SELECT id FROM companies WHERE code='JNAT'", new ArrayList<Object>(), new LongMapper());
                if (c == null) {
                    System.out.println("REFUSED: no JNAT demo company");
                    exitProcess(4);
                }
                long companyId = ErpNum.asLong(c);
                if (ErpBooks.isFormalBook(companyId) != 0) {
                    System.out.println("REFUSED: JNAT is FORMAL book — demo reset forbidden");
                    exitProcess(3);
                }
                ErpBooks.resetDemoBook(companyId);
                System.out.println("DEMO_RESET_OK companyId=" + CryptoParse.longToString(companyId));
                exitProcess(0);
            } catch (Exception ex) {
                System.out.println("FAIL reset-demo");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        ErpProductConfig.load();
        Erp.loop = new EventLoop();
        Erp.sessions = new InMemorySessionStore();
        Erp.sessions.idleTimeoutMs = 86400000L;
        Erp.loginLimiter = new RateLimiter();
        Erp.loginLimiter.capacity = 40;
        Erp.loginLimiter.refillPerSec = 8;
        Erp.requestSeq = 0L;
        ErpDb.setup(Erp.loop);
        ErpDb.reconnect();
        try {
            ErpSeed.ensure();
        } catch (Exception seedEx) {
            System.out.println("SEED_RETRY after error");
            if (seedEx.getMessage() != null) {
                System.out.println(seedEx.getMessage());
            }
            ErpDb.reconnect();
            try {
                ErpSeed.ensure();
            } catch (Exception seedEx2) {
                System.out.println("SEED_DEGRADED continuing without full seed");
                if (seedEx2.getMessage() != null) {
                    System.out.println(seedEx2.getMessage());
                }
                ErpDb.reconnect();
            }
        }

        WebApp app = new WebApp();
        app.bindEventLoop(Erp.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", ErpProductConfig.publicPath);
        app.staticFiles("/wasm", ErpProductConfig.wasmPath);

        app.get("/health", new ErpHealthHandler());
        app.get("/ready", new ErpReadyHandler());
        app.get("/version", new ErpVersionHandler());

        app.futurePost("/api/auth/login", new ErpLoginHandler(Erp.loop));
        app.futurePost("/api/auth/logout", new ErpLogoutHandler(Erp.loop));
        app.futureGet("/api/auth/currentUser", new ErpMeHandler(Erp.loop));

        app.futureGet("/api/erp/dashboard", new ErpDashboardHandler(Erp.loop));
        app.futureGet("/api/erp/partners", new ErpPartnersHandler(Erp.loop));
        app.futureGet("/api/erp/items", new ErpItemsHandler(Erp.loop));
        app.futurePost("/api/erp/items", new ErpCreateItemHandler(Erp.loop));
        app.futureGet("/api/erp/warehouses", new ErpWarehousesHandler(Erp.loop));
        app.futureGet("/api/erp/inventory", new ErpInventoryHandler(Erp.loop));

        app.futurePost("/api/erp/purchaseOrders", new ErpCreatePoHandler(Erp.loop));
        app.futureGet("/api/erp/purchaseOrders/:id", new ErpGetPoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders/:id/approve", new ErpApprovePoHandler(Erp.loop));
        app.futurePost("/api/erp/goodsReceipts", new ErpGrHandler(Erp.loop));
        app.futurePost("/api/erp/goodsReceipts/:id/reverse", new ErpReverseGrHandler(Erp.loop));

        app.futurePost("/api/erp/salesOrders", new ErpCreateSoHandler(Erp.loop));
        app.futureGet("/api/erp/salesOrders/:id", new ErpGetSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders/:id/approve", new ErpApproveSoHandler(Erp.loop));
        app.futurePost("/api/erp/deliveries", new ErpDlHandler(Erp.loop));
        app.futurePost("/api/erp/deliveries/:id/reverse", new ErpReverseDlHandler(Erp.loop));

        app.futureGet("/api/erp/setup/wizard", new ErpWizardGetHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/company", new ErpWizardCompanyHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/currency", new ErpWizardCurrencyHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/warehouse", new ErpWizardWarehouseHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/numbering", new ErpWizardNumberingHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/user", new ErpWizardUserHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/advance", new ErpWizardAdvanceHandler(Erp.loop));
        app.futurePost("/api/erp/setup/wizard/complete", new ErpWizardCompleteHandler(Erp.loop));
        app.futurePost("/api/erp/setup/formal-company", new ErpCreateFormalCompanyHandler(Erp.loop));

        app.futurePost("/api/erp/opening/stock", new ErpOpeningStockDraftHandler(Erp.loop));
        app.futurePost("/api/erp/opening/stock/:id/post", new ErpOpeningStockPostHandler(Erp.loop));
        app.futurePost("/api/erp/opening/ar", new ErpOpeningArHandler(Erp.loop));
        app.futurePost("/api/erp/opening/ap", new ErpOpeningApHandler(Erp.loop));

        app.futureGet("/api/erp/import/templates/partners", new ErpImportPartnerTemplateHandler(Erp.loop));
        app.futureGet("/api/erp/import/templates/items", new ErpImportItemTemplateHandler(Erp.loop));
        app.futurePost("/api/erp/import/partners/preview", new ErpImportPartnerPreviewHandler(Erp.loop));
        app.futurePost("/api/erp/import/items/preview", new ErpImportItemPreviewHandler(Erp.loop));
        app.futurePost("/api/erp/import/partners", new ErpImportPartnerHandler(Erp.loop));
        app.futurePost("/api/erp/import/items", new ErpImportItemHandler(Erp.loop));

        app.futurePost("/api/erp/admin/demo/reset", new ErpDemoResetHandler(Erp.loop));
        app.futureGet("/api/erp/help/tutorials", new ErpHelpTutorialsHandler(Erp.loop));
        app.futureGet("/api/erp/book", new ErpBookInfoHandler(Erp.loop));

        app.futureGet("/api/erp/finance/accounts", new ErpFinanceAccountsHandler(Erp.loop));
        app.futureGet("/api/erp/finance/journals", new ErpFinanceJournalsHandler(Erp.loop));
        app.futureGet("/api/erp/finance/ar", new ErpFinanceArListHandler(Erp.loop));
        app.futureGet("/api/erp/finance/ap", new ErpFinanceApListHandler(Erp.loop));
        app.futureGet("/api/erp/finance/banks", new ErpFinanceBanksHandler(Erp.loop));
        app.futureGet("/api/erp/finance/trialBalance", new ErpFinanceTrialHandler(Erp.loop));
        app.futureGet("/api/erp/finance/gl", new ErpFinanceGlHandler(Erp.loop));
        app.futureGet("/api/erp/finance/arAgeing", new ErpFinanceArAgeingHandler(Erp.loop));
        app.futureGet("/api/erp/finance/apAgeing", new ErpFinanceApAgeingHandler(Erp.loop));

        app.futureGet("/api/erp/mfg/boms", new ErpMfgBomListHandler(Erp.loop));
        app.futureGet("/api/erp/mfg/productionOrders", new ErpMfgPoListHandler(Erp.loop));
        app.futureGet("/api/erp/mfg/wip", new ErpMfgWipHandler(Erp.loop));
        app.futureGet("/api/erp/mfg/mrp/suggestions", new ErpMfgMrpSuggestionsHandler(Erp.loop));

        app.futureGet("/api/erp/admin/users", new ErpAdminUsersHandler(Erp.loop));
        app.futureGet("/api/erp/admin/roles", new ErpAdminRolesHandler(Erp.loop));
        app.futureGet("/api/erp/admin/permissions", new ErpAdminPermsHandler(Erp.loop));
        app.futureGet("/api/erp/admin/customFields", new ErpAdminCustomFieldsHandler(Erp.loop));
        app.futureGet("/api/erp/admin/forms", new ErpAdminFormsHandler(Erp.loop));
        app.futureGet("/api/erp/admin/workflows", new ErpAdminWorkflowsHandler(Erp.loop));
        app.futureGet("/api/erp/admin/reports", new ErpAdminReportsHandler(Erp.loop));
        app.futureGet("/api/erp/admin/importExport", new ErpAdminImportExportHandler(Erp.loop));
        app.futureGet("/api/erp/admin/settings", new ErpAdminSettingsHandler(Erp.loop));
        app.futureGet("/api/erp/admin/audit", new ErpAdminAuditHandler(Erp.loop));

        System.out.println("JNative One " + ErpProductConfig.version
                + " on http://" + ErpProductConfig.listenHost + ":"
                + String.valueOf(ErpProductConfig.listenPort));
        if (ErpProductConfig.demoMode != 0) {
            System.out.println("DEMO MODE enabled");
        }
        System.out.println("DB dialect: " + Erp.dialect);
        ErpProductConfig.logInfo("boot", "listen", "boot", 0L, 0L, "");
        app.listenWithLoop(ErpProductConfig.listenPort);
    }
}

class ErpHealthHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.jsonString("{\"ok\":true,\"service\":\"jnative-one\",\"version\":"
                + ErpJson.str(ErpProductConfig.version) + "}");
    }
}

class ErpVersionHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.jsonString(ErpProductConfig.publicVersionJson());
    }
}

class ErpReadyHandler implements WebHandler {
    public void handle(WebContext ctx) {
        int dbOk = 0;
        String mig = "";
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            JnFuture f = Erp.db.queryOne("SELECT 1 AS id", p, new LongMapper());
            FutureBlocker.blockingWait(f);
            if (f.getValue() != null) {
                dbOk = 1;
            }
            ArrayList<Object> mp = new ArrayList<Object>();
            mp.add("005_pilot");
            Object v = ErpDb.queryOne("SELECT version FROM schema_migrations WHERE version=?", mp,
                    new StringMapper());
            if (v != null) {
                mig = (String) v;
            } else {
                ArrayList<Object> mp4 = new ArrayList<Object>();
                mp4.add("004_enterprise");
                Object v4 = ErpDb.queryOne("SELECT version FROM schema_migrations WHERE version=?", mp4,
                        new StringMapper());
                if (v4 != null) {
                    mig = (String) v4;
                }
            }
        } catch (Exception ex) {
            dbOk = 0;
        }
        if (dbOk != 0 && Erp.schemaReady != 0) {
            ctx.jsonString("{\"ready\":true,\"db\":" + ErpJson.str(Erp.dialect)
                    + ",\"migration\":" + ErpJson.str(mig)
                    + ",\"demoMode\":" + String.valueOf(ErpProductConfig.demoMode) + "}");
        } else {
            ctx.status(503).jsonString("{\"ready\":false,\"schemaReady\":"
                    + String.valueOf(Erp.schemaReady)
                    + ",\"error\":" + ErpJson.str(Erp.schemaError) + "}");
        }
    }
}

class ErpLoginHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpLoginHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        if (Erp.loginLimiter.allow("login") == 0) {
            ErpAuth.err(ctx, 429, "RATE_LIMITED", "too many login attempts");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String email = body.getString("email", "");
        String password = body.getString("password", "");
        UserRow user = ErpAuth.loadUserByEmail(email);
        if (user == null || Password.verifyBlocking(password, user.passwordHash) == 0) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "invalid email or password");
            return JnFuture.completed(loop, null);
        }
        Session session = ErpAuth.load(ctx);
        session.rotateId();
        session.setString("userId", CryptoParse.longToString(user.id));
        session.setString("companyId", CryptoParse.longToString(user.companyId));
        String csrf = "csrf-" + CryptoParse.longToString(user.id) + "-"
                + CryptoParse.longToString(AsyncClockNative.wallMillis());
        session.setString("csrf", csrf);
        ErpAuth.save(ctx, session);
        UserDto dto = new UserDto();
        dto.id = user.id;
        dto.companyId = user.companyId;
        dto.email = user.email;
        dto.displayName = user.displayName;
        dto.role = user.role;
        ctx.jsonString("{\"user\":" + ErpJson.user(dto) + ",\"csrfToken\":" + ErpJson.str(csrf) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpLogoutHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpLogoutHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        Session session = ErpAuth.load(ctx);
        session.invalidate();
        ErpAuth.save(ctx, session);
        ctx.jsonString("{\"ok\":true}");
        return JnFuture.completed(loop, null);
    }
}

class ErpMeHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpMeHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAuth.requireSession(ctx);
        if (es.userId == 0L) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        UserRow user = ErpAuth.loadUser(es.userId, es.companyId);
        if (user == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        UserDto dto = new UserDto();
        dto.id = user.id;
        dto.companyId = user.companyId;
        dto.email = user.email;
        dto.displayName = user.displayName;
        dto.role = user.role;
        ctx.jsonString(ErpJson.user(dto));
        return JnFuture.completed(loop, null);
    }
}

class ErpRequire {
    static ErpSession auth(WebContext ctx) {
        ErpSession es = ErpAuth.requireSession(ctx);
        if (es.userId == 0L || es.companyId == 0L) {
            return null;
        }
        return es;
    }

    /** Auth + schema ready + permission. Sets 401/503/403 on failure and returns null. */
    static ErpSession authPerm(WebContext ctx, String perm) {
        if (Erp.schemaReady == 0) {
            ErpAuth.err(ctx, 503, "SCHEMA_NOT_READY", Erp.schemaError);
            return null;
        }
        ErpSession es = auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return null;
        }
        if (perm != null && perm.length() > 0) {
            if (ErpPlatformAuth.hasPermission(es.userId, es.companyId, perm) == 0) {
                ErpAuth.err(ctx, 403, "FORBIDDEN", perm);
                return null;
            }
        }
        return es;
    }
}

class ErpDashboardHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDashboardHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        DashboardDto d = new DashboardDto();
        d.customerCount = countPartners(es.companyId, PartnerKind.CUSTOMER);
        d.supplierCount = countPartners(es.companyId, PartnerKind.SUPPLIER);
        d.itemCount = countItems(es.companyId);
        d.openPoCount = countPo(es.companyId);
        d.openSoCount = countSo(es.companyId);
        ctx.jsonString("{\"customerCount\":" + String.valueOf(d.customerCount)
                + ",\"supplierCount\":" + String.valueOf(d.supplierCount)
                + ",\"itemCount\":" + String.valueOf(d.itemCount)
                + ",\"openPoCount\":" + String.valueOf(d.openPoCount)
                + ",\"openSoCount\":" + String.valueOf(d.openSoCount) + "}");
        return JnFuture.completed(loop, null);
    }

    static int countPartners(long companyId, String kind) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(kind);
        Object v = ErpDb.queryOne("SELECT COUNT(*) FROM business_partners WHERE company_id=? AND kind=?", p,
                new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    static int countItems(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT COUNT(*) FROM items WHERE company_id=?", p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    static int countPo(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT COUNT(*) FROM purchase_orders WHERE company_id=? AND status IN ('APPROVED','PARTIALLY_RECEIVED')",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    static int countSo(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT COUNT(*) FROM sales_orders WHERE company_id=? AND status IN ('APPROVED','PARTIALLY_DELIVERED')",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }
}

class ErpPartnersHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpPartnersHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        String kind = ctx.query("kind");
        ArrayList<Object> list = ErpPartners.list(es.companyId, kind);
        ctx.jsonString(ErpJson.partnerList(list));
        return JnFuture.completed(loop, null);
    }
}

class ErpItemsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpItemsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpJson.itemList(ErpItems.list(es.companyId)));
        return JnFuture.completed(loop, null);
    }
}

class ErpCreateItemHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCreateItemHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        if (ErpAuth.checkCsrf(ctx) == 0) {
            ErpAuth.err(ctx, 403, "CSRF", "invalid csrf token");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String sku = body.getString("sku", "");
        String name = body.getString("name", "");
        long price = body.getLong("unitPriceCents", 0L);
        if (price == 0L) {
            /* UI may send major units as price */
            long major = body.getLong("price", 0L);
            if (major > 0L && major < 100000000L) {
                price = major * 100L;
            }
        }
        ItemDto created = ErpItems.create(es.companyId, sku, name, price);
        if (created == null) {
            ErpAuth.err(ctx, 400, "ITEM_CREATE_FAILED", "invalid or duplicate sku");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpJson.item(created));
        return JnFuture.completed(loop, null);
    }
}

class ErpWarehousesHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWarehousesHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpJson.warehouseList(ErpWarehouses.list(es.companyId)));
        return JnFuture.completed(loop, null);
    }
}

class ErpInventoryHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpInventoryHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long wh = ErpNum.parseLong(ctx.query("warehouseId"));
        ctx.jsonString(ErpJson.balanceList(ErpInventory.balances(es.companyId, wh)));
        return JnFuture.completed(loop, null);
    }
}

class ErpCreatePoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCreatePoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long supplierId = body.getLong("supplierId", 0L);
        ArrayList<Object> lines = ErpBodyParse.parsePoLines(body);
        PurchaseOrderDto po = ErpPurchasing.createDraft(es.companyId, supplierId, lines);
        ctx.jsonString("{\"id\":" + ErpJson.num(po.id) + ",\"docNo\":" + ErpJson.str(po.docNo) + ",\"status\":"
                + ErpJson.str(po.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpGetPoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGetPoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PurchaseOrderDto po = ErpPurchasing.get(es.companyId, id);
        if (po == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "PO not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"id\":" + ErpJson.num(po.id) + ",\"docNo\":" + ErpJson.str(po.docNo) + ",\"status\":"
                + ErpJson.str(po.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpApprovePoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpApprovePoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.APPROVE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PurchaseOrderDto po = ErpPurchasing.approve(es.companyId, id);
        ctx.jsonString("{\"id\":" + ErpJson.num(po.id) + ",\"status\":" + ErpJson.str(po.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpGrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGrHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        PostGoodsReceiptRequest req = ErpBodyParse.parseGr(ctx);
        PostResultDto r = ErpPosting.postGoodsReceipt(es.companyId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpCreateSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCreateSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long customerId = body.getLong("customerId", 0L);
        ArrayList<Object> lines = ErpBodyParse.parseSoLines(body);
        SalesOrderDto so = ErpSales.createDraft(es.companyId, customerId, lines);
        ctx.jsonString("{\"id\":" + ErpJson.num(so.id) + ",\"docNo\":" + ErpJson.str(so.docNo) + ",\"status\":"
                + ErpJson.str(so.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpGetSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGetSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        SalesOrderDto so = ErpSales.get(es.companyId, id);
        if (so == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "SO not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"id\":" + ErpJson.num(so.id) + ",\"docNo\":" + ErpJson.str(so.docNo) + ",\"status\":"
                + ErpJson.str(so.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpApproveSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpApproveSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.APPROVE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        SalesOrderDto so = ErpSales.approve(es.companyId, id);
        ctx.jsonString("{\"id\":" + ErpJson.num(so.id) + ",\"status\":" + ErpJson.str(so.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpDlHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDlHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        PostDeliveryRequest req = ErpBodyParse.parseDl(ctx);
        PostResultDto r = ErpPosting.postDelivery(es.companyId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpBodyParse {
    static ArrayList<Object> parsePoLines(JsonObject body) {
        ArrayList<Object> lines = new ArrayList<Object>();
        JsonValue lv = body.get("lines");
        if (lv == null || lv.kind != JsonValue.ARRAY_KIND) {
            return lines;
        }
        JsonArray arr = lv.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject o = arr.get(i).asObject();
            PoLineInput in = new PoLineInput();
            in.itemId = o.getLong("itemId", 0L);
            in.quantity = o.getInt("quantity", 0);
            in.unitPriceCents = o.getLong("unitPriceCents", 0L);
            lines.add(in);
            i = i + 1;
        }
        return lines;
    }

    static ArrayList<Object> parseSoLines(JsonObject body) {
        ArrayList<Object> lines = new ArrayList<Object>();
        JsonValue lv = body.get("lines");
        if (lv == null || lv.kind != JsonValue.ARRAY_KIND) {
            return lines;
        }
        JsonArray arr = lv.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject o = arr.get(i).asObject();
            SoLineInput in = new SoLineInput();
            in.itemId = o.getLong("itemId", 0L);
            in.quantity = o.getInt("quantity", 0);
            in.unitPriceCents = o.getLong("unitPriceCents", 0L);
            lines.add(in);
            i = i + 1;
        }
        return lines;
    }

    static PostGoodsReceiptRequest parseGr(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        PostGoodsReceiptRequest req = new PostGoodsReceiptRequest();
        req.purchaseOrderId = body.getLong("purchaseOrderId", 0L);
        req.warehouseId = body.getLong("warehouseId", 0L);
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.lines = parsePostLines(body);
        return req;
    }

    static PostDeliveryRequest parseDl(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        PostDeliveryRequest req = new PostDeliveryRequest();
        req.salesOrderId = body.getLong("salesOrderId", 0L);
        req.warehouseId = body.getLong("warehouseId", 0L);
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.lines = parsePostLines(body);
        return req;
    }

    static ArrayList<Object> parsePostLines(JsonObject body) {
        ArrayList<Object> lines = new ArrayList<Object>();
        JsonValue lv = body.get("lines");
        if (lv == null || lv.kind != JsonValue.ARRAY_KIND) {
            return lines;
        }
        JsonArray arr = lv.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject o = arr.get(i).asObject();
            PostLineRequest line = new PostLineRequest();
            line.orderLineId = o.getLong("orderLineId", 0L);
            line.quantity = o.getInt("quantity", 0);
            lines.add(line);
            i = i + 1;
        }
        return lines;
    }
}

class ErpFinanceAccountsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceAccountsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> list = ErpFinanceReports.chartOfAccounts(es.companyId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            GlAccountDto a = (GlAccountDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(a.id)
                    + ",\"accountCode\":" + ErpJson.str(a.accountCode)
                    + ",\"accountName\":" + ErpJson.str(a.accountName)
                    + ",\"accountType\":" + ErpJson.str(a.accountType)
                    + ",\"postingAllowed\":" + String.valueOf(a.postingAllowed)
                    + ",\"active\":" + String.valueOf(a.active) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceJournalsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceJournalsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long from = WebUtil.parseLongOr(ctx.query("from"), 0L);
        long to = WebUtil.parseLongOr(ctx.query("to"), 4102444800000L);
        ArrayList<Object> list = ErpFinanceReports.listJournals(es.companyId, from, to);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            JournalEntryDto j = (JournalEntryDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(j.id)
                    + ",\"journalNumber\":" + ErpJson.str(j.journalNumber)
                    + ",\"postingDate\":" + ErpJson.num(j.postingDate)
                    + ",\"sourceType\":" + ErpJson.str(j.sourceType)
                    + ",\"status\":" + ErpJson.str(j.status)
                    + ",\"description\":" + ErpJson.str(j.description)
                    + ",\"totalDebit\":" + ErpJson.num(j.totalDebit)
                    + ",\"totalCredit\":" + ErpJson.num(j.totalCredit) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceArListHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceArListHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> list = ErpFinanceArAp.listAr(es.companyId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            ArInvoiceDto d = (ArInvoiceDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(d.id)
                    + ",\"customerId\":" + ErpJson.num(d.customerId)
                    + ",\"invoiceNumber\":" + ErpJson.str(d.invoiceNumber)
                    + ",\"totalAmount\":" + ErpJson.num(d.totalAmount)
                    + ",\"paidAmount\":" + ErpJson.num(d.paidAmount)
                    + ",\"openAmount\":" + ErpJson.num(d.openAmount)
                    + ",\"status\":" + ErpJson.str(d.status) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceApListHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceApListHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> list = ErpFinanceArAp.listAp(es.companyId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            ApInvoiceDto d = (ApInvoiceDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(d.id)
                    + ",\"supplierId\":" + ErpJson.num(d.supplierId)
                    + ",\"invoiceNumber\":" + ErpJson.str(d.invoiceNumber)
                    + ",\"totalAmount\":" + ErpJson.num(d.totalAmount)
                    + ",\"paidAmount\":" + ErpJson.num(d.paidAmount)
                    + ",\"openAmount\":" + ErpJson.num(d.openAmount)
                    + ",\"status\":" + ErpJson.str(d.status) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceBanksHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceBanksHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> list = ErpFinanceReports.listBankAccounts(es.companyId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + (String) list.get(i);
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceTrialHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceTrialHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long from = WebUtil.parseLongOr(ctx.query("from"), 0L);
        long to = WebUtil.parseLongOr(ctx.query("to"), 4102444800000L);
        ArrayList<Object> list = ErpFinanceReports.trialBalance(es.companyId, from, to);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            TrialBalanceRowDto r = (TrialBalanceRowDto) list.get(i);
            out = out + "{\"accountCode\":" + ErpJson.str(r.accountCode)
                    + ",\"accountName\":" + ErpJson.str(r.accountName)
                    + ",\"openingDebit\":" + ErpJson.num(r.openingDebit)
                    + ",\"openingCredit\":" + ErpJson.num(r.openingCredit)
                    + ",\"periodDebit\":" + ErpJson.num(r.periodDebit)
                    + ",\"periodCredit\":" + ErpJson.num(r.periodCredit)
                    + ",\"closingDebit\":" + ErpJson.num(r.closingDebit)
                    + ",\"closingCredit\":" + ErpJson.num(r.closingCredit) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceGlHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceGlHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long accountId = WebUtil.parseLongOr(ctx.query("accountId"), 0L);
        long partnerId = WebUtil.parseLongOr(ctx.query("partnerId"), 0L);
        long from = WebUtil.parseLongOr(ctx.query("from"), 0L);
        long to = WebUtil.parseLongOr(ctx.query("to"), 4102444800000L);
        ArrayList<Object> list = ErpFinanceReports.generalLedger(es.companyId, accountId, from, to, partnerId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            JournalLineDto ln = (JournalLineDto) list.get(i);
            out = out + "{\"accountCode\":" + ErpJson.str(ln.accountCode)
                    + ",\"debitAmount\":" + ErpJson.num(ln.debitAmount)
                    + ",\"creditAmount\":" + ErpJson.num(ln.creditAmount)
                    + ",\"businessPartnerId\":" + ErpJson.num(ln.businessPartnerId)
                    + ",\"description\":" + ErpJson.str(ln.description) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceArAgeingHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceArAgeingHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long asOf = WebUtil.parseLongOr(ctx.query("asOf"), AsyncClockNative.wallMillis());
        ctx.jsonString(ErpFinanceJson.ageing(ErpFinanceReports.arAgeing(es.companyId, asOf)));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceApAgeingHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceApAgeingHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long asOf = WebUtil.parseLongOr(ctx.query("asOf"), AsyncClockNative.wallMillis());
        ctx.jsonString(ErpFinanceJson.ageing(ErpFinanceReports.apAgeing(es.companyId, asOf)));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceJson {
    static String ageing(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            AgeingBucketDto b = (AgeingBucketDto) list.get(i);
            out = out + "{\"partnerId\":" + ErpJson.num(b.partnerId)
                    + ",\"partnerName\":" + ErpJson.str(b.partnerName)
                    + ",\"currentAmount\":" + ErpJson.num(b.currentAmount)
                    + ",\"d1to30\":" + ErpJson.num(b.d1to30)
                    + ",\"d31to60\":" + ErpJson.num(b.d31to60)
                    + ",\"d61to90\":" + ErpJson.num(b.d61to90)
                    + ",\"d90plus\":" + ErpJson.num(b.d90plus)
                    + ",\"totalOpen\":" + ErpJson.num(b.totalOpen) + "}";
            i = i + 1;
        }
        return out + "]";
    }
}

class ErpMfgBomListHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpMfgBomListHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> list = ErpMfgBom.listBoms(es.companyId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            BomDto b = (BomDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(b.id)
                    + ",\"bomCode\":" + ErpJson.str(b.bomCode)
                    + ",\"version\":" + String.valueOf(b.version)
                    + ",\"parentItemId\":" + ErpJson.num(b.parentItemId)
                    + ",\"status\":" + ErpJson.str(b.status)
                    + ",\"baseQuantity\":" + String.valueOf(b.baseQuantity) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpMfgPoListHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpMfgPoListHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> list = ErpMfgProduction.listOrders(es.companyId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            ProductionOrderDto d = (ProductionOrderDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(d.id)
                    + ",\"orderNumber\":" + ErpJson.str(d.orderNumber)
                    + ",\"itemId\":" + ErpJson.num(d.itemId)
                    + ",\"plannedQuantity\":" + String.valueOf(d.plannedQuantity)
                    + ",\"completedQuantity\":" + String.valueOf(d.completedQuantity)
                    + ",\"status\":" + ErpJson.str(d.status) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpMfgWipHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpMfgWipHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long orderId = WebUtil.parseLongOr(ctx.query("orderId"), 0L);
        if (orderId == 0L) {
            ctx.jsonString("[]");
            return JnFuture.completed(loop, null);
        }
        WipDto w = ErpMfgProduction.getWip(es.companyId, orderId);
        if (w == null) {
            ctx.jsonString("null");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"productionOrderId\":" + ErpJson.num(w.productionOrderId)
                + ",\"materialCost\":" + ErpJson.num(w.materialCost)
                + ",\"laborCost\":" + ErpJson.num(w.laborCost)
                + ",\"overheadCost\":" + ErpJson.num(w.overheadCost)
                + ",\"transferredCost\":" + ErpJson.num(w.transferredCost)
                + ",\"remainingCost\":" + ErpJson.num(w.remainingCost) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpMfgMrpSuggestionsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpMfgMrpSuggestionsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        long runId = WebUtil.parseLongOr(ctx.query("runId"), 0L);
        ArrayList<Object> list = ErpMfgMrp.listSuggestions(es.companyId, runId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            MrpSuggestionDto d = (MrpSuggestionDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(d.id)
                    + ",\"suggestionKind\":" + ErpJson.str(d.suggestionKind)
                    + ",\"itemId\":" + ErpJson.num(d.itemId)
                    + ",\"requiredQuantity\":" + String.valueOf(d.requiredQuantity)
                    + ",\"status\":" + ErpJson.str(d.status) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminGate {
    static ErpSession require(WebContext ctx, String perm) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            return null;
        }
        if (ErpPlatformAuth.hasPermission(es.userId, es.companyId, perm) == 0) {
            ErpAuth.err(ctx, 403, "FORBIDDEN", perm);
            return null;
        }
        return es;
    }
}

class ErpAdminUsersHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminUsersHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.USER.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, email, display_name, role FROM users WHERE company_id=? ORDER BY id LIMIT 200", p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"email\":" + ErpJson.str(row.getString(1))
                    + ",\"displayName\":" + ErpJson.str(row.getString(2))
                    + ",\"role\":" + ErpJson.str(row.getString(3)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminRolesHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminRolesHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.ROLE.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name, data_scope FROM platform_roles WHERE company_id=? ORDER BY id", p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"code\":" + ErpJson.str(row.getString(1))
                    + ",\"name\":" + ErpJson.str(row.getString(2))
                    + ",\"dataScope\":" + ErpJson.str(row.getString(3)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminPermsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminPermsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.ROLE.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        DbQueryResult r = ErpDb.query("SELECT code, name FROM platform_permissions ORDER BY code",
                new ArrayList<Object>());
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"code\":" + ErpJson.str(row.getString(0))
                    + ",\"name\":" + ErpJson.str(row.getString(1)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminCustomFieldsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminCustomFieldsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.CUSTOMFIELD.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT field_code, field_name, field_type, entity_type, required, active FROM custom_field_definitions WHERE company_id=? ORDER BY id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"fieldCode\":" + ErpJson.str(row.getString(0))
                    + ",\"fieldName\":" + ErpJson.str(row.getString(1))
                    + ",\"fieldType\":" + ErpJson.str(row.getString(2))
                    + ",\"entityType\":" + ErpJson.str(row.getString(3))
                    + ",\"required\":" + String.valueOf(row.getInt(4))
                    + ",\"active\":" + String.valueOf(row.getInt(5)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminFormsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminFormsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.FORM.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT form_code, title, entity_type, active FROM form_definitions WHERE company_id=? ORDER BY id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"formCode\":" + ErpJson.str(row.getString(0))
                    + ",\"title\":" + ErpJson.str(row.getString(1))
                    + ",\"entityType\":" + ErpJson.str(row.getString(2))
                    + ",\"active\":" + String.valueOf(row.getInt(3)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminWorkflowsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminWorkflowsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.WORKFLOW.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT workflow_code, name, entity_type, active FROM workflow_definitions WHERE company_id=? ORDER BY id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"workflowCode\":" + ErpJson.str(row.getString(0))
                    + ",\"name\":" + ErpJson.str(row.getString(1))
                    + ",\"entityType\":" + ErpJson.str(row.getString(2))
                    + ",\"active\":" + String.valueOf(row.getInt(3)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminReportsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminReportsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.REPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        String dataset = ctx.query("dataset");
        if (dataset == null || dataset.length() == 0) {
            dataset = "InventoryBalanceDataset";
        }
        ArrayList<Object> rows = ErpPlatformReports.runDataset(es.companyId, es.userId, dataset, 100);
        String out = "[";
        int i = 0;
        while (i < rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + (String) rows.get(i);
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminImportExportHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminImportExportHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT job_code, status, ok_rows, error_rows FROM import_jobs WHERE company_id=? ORDER BY id DESC LIMIT 50",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"jobCode\":" + ErpJson.str(row.getString(0))
                    + ",\"status\":" + ErpJson.str(row.getString(1))
                    + ",\"okRows\":" + String.valueOf(row.getInt(2))
                    + ",\"errorRows\":" + String.valueOf(row.getInt(3)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminSettingsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminSettingsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.SETTING.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT setting_key, setting_type, setting_value, sensitive FROM system_settings WHERE company_id=? ORDER BY setting_key",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            String val = row.getString(2);
            if (row.getInt(3) != 0) {
                val = "***";
            }
            out = out + "{\"key\":" + ErpJson.str(row.getString(0))
                    + ",\"type\":" + ErpJson.str(row.getString(1))
                    + ",\"value\":" + ErpJson.str(val)
                    + ",\"sensitive\":" + String.valueOf(row.getInt(3)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpAdminAuditHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpAdminAuditHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpAdminGate.require(ctx, "ADMIN.AUDIT.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT actor_user_id, resource_type, action, request_id, created_at FROM audit_events WHERE company_id=? ORDER BY id DESC LIMIT 100",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"actorUserId\":" + ErpJson.num(row.getLong(0))
                    + ",\"resourceType\":" + ErpJson.str(row.getString(1))
                    + ",\"action\":" + ErpJson.str(row.getString(2))
                    + ",\"requestId\":" + ErpJson.str(row.getString(3))
                    + ",\"createdAt\":" + ErpJson.num(row.getLong(4)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpReverseGrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpReverseGrHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.GR.REVERSE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String reason = body.getString("reason", "");
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpReverse.reverseGoodsReceipt(es.companyId, es.userId, id, reason, key);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpReverseDlHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpReverseDlHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.DELIVERY.REVERSE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String reason = body.getString("reason", "");
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpReverse.reverseDelivery(es.companyId, es.userId, id, reason, key);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardGetHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardGetHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpSetupWizard.getStateJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpCreateFormalCompanyHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCreateFormalCompanyHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        if (Erp.schemaReady == 0) {
            ErpAuth.err(ctx, 503, "SCHEMA_NOT_READY", Erp.schemaError);
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            long id = ErpSetupWizard.createFormalCompany(body.getString("code", ""),
                    body.getString("name", ""), body.getString("timezone", "UTC"));
            ctx.jsonString("{\"companyId\":" + ErpJson.num(id) + ",\"bookKind\":\"FORMAL\"}");
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardCompanyHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardCompanyHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            ErpSetupWizard.applyCompanyStep(es.companyId, es.userId, body.getString("name", ""),
                    body.getString("timezone", "UTC"));
            ctx.jsonString(ErpSetupWizard.getStateJson(es.companyId));
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardCurrencyHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardCurrencyHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            ErpSetupWizard.applyCurrencyStep(es.companyId, es.userId, body.getString("currency", "CNY"),
                    body.getInt("amountScale", 0));
            ctx.jsonString(ErpSetupWizard.getStateJson(es.companyId));
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardWarehouseHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardWarehouseHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            long wh = ErpSetupWizard.applyWarehouseStep(es.companyId, es.userId,
                    body.getString("code", "WH1"), body.getString("name", "Main WH"));
            ctx.jsonString("{\"warehouseId\":" + ErpJson.num(wh) + "}");
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardNumberingHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardNumberingHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            ErpSetupWizard.applyNumberingStep(es.companyId, es.userId);
            ctx.jsonString(ErpSetupWizard.getStateJson(es.companyId));
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardUserHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardUserHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            long uid = ErpSetupWizard.applyUserStep(es.companyId, es.userId,
                    body.getString("email", ""), body.getString("displayName", ""),
                    body.getString("password", ""), body.getString("role", "admin"));
            ctx.jsonString("{\"userId\":" + ErpJson.num(uid) + "}");
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardAdvanceHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardAdvanceHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String to = body.getString("to", WizardStep.OPENING);
        try {
            if (to.equals(WizardStep.OPENING)) {
                ErpSetupWizard.advanceToOpening(es.companyId, es.userId);
            } else {
                ErpSetupWizard.advanceToValidate(es.companyId, es.userId);
            }
            ctx.jsonString(ErpSetupWizard.getStateJson(es.companyId));
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpWizardCompleteHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpWizardCompleteHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.SETUP.MANAGE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            ctx.jsonString(ErpSetupWizard.validateAndComplete(es.companyId, es.userId));
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "WIZARD_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpOpeningStockDraftHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpOpeningStockDraftHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "INVENTORY.OPENING.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        ArrayList<Object> lines = new ArrayList<Object>();
        JsonValue lv = body.get("lines");
        if (lv != null && lv.kind == JsonValue.ARRAY_KIND) {
            JsonArray arr = lv.asArray();
            int i = 0;
            while (i < arr.size()) {
                JsonObject o = arr.get(i).asObject();
                OpeningLineInput in = new OpeningLineInput();
                in.itemId = o.getLong("itemId", 0L);
                in.quantity = o.getInt("quantity", 0);
                in.unitCostAmount = o.getLong("unitCostAmount", 0L);
                lines.add(in);
                i = i + 1;
            }
        }
        PostResultDto r = ErpOpening.createStockDraft(es.companyId, es.userId,
                body.getLong("warehouseId", 0L), body.getLong("businessDate", AsyncClockNative.wallMillis()),
                body.getString("notes", ""), key, lines);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpOpeningStockPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpOpeningStockPostHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "INVENTORY.OPENING.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpOpening.postStock(es.companyId, es.userId, id, key);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpOpeningArHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpOpeningArHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.OPENING.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpOpening.createAndPostAr(es.companyId, es.userId,
                body.getLong("customerId", 0L), body.getLong("businessDate", AsyncClockNative.wallMillis()),
                body.getLong("amount", 0L), body.getString("notes", ""), key);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpOpeningApHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpOpeningApHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.OPENING.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpOpening.createAndPostAp(es.companyId, es.userId,
                body.getLong("supplierId", 0L), body.getLong("businessDate", AsyncClockNative.wallMillis()),
                body.getLong("amount", 0L), body.getString("notes", ""), key);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpImportPartnerTemplateHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpImportPartnerTemplateHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"csv\":" + ErpJson.str(ErpPlatformIo.partnerTemplateZh()) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpImportItemTemplateHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpImportItemTemplateHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"csv\":" + ErpJson.str(ErpPlatformIo.itemTemplateZh()) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpImportPartnerPreviewHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpImportPartnerPreviewHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        ctx.jsonString(ErpPlatformIo.previewPartnersCsv(es.companyId, body.getString("csv", "")));
        return JnFuture.completed(loop, null);
    }
}

class ErpImportItemPreviewHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpImportItemPreviewHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        ctx.jsonString(ErpPlatformIo.previewItemsCsv(es.companyId, body.getString("csv", "")));
        return JnFuture.completed(loop, null);
    }
}

class ErpImportPartnerHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpImportPartnerHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            long id = ErpPlatformIo.importPartnersCsv(es.companyId, es.userId,
                    body.getString("jobCode", "IMP-" + CryptoParse.longToString(AsyncClockNative.wallMillis())),
                    body.getString("csv", ""));
            ctx.jsonString("{\"batchId\":" + ErpJson.num(id) + "}");
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "IMPORT_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpImportItemHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpImportItemHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "ADMIN.IMPORT.RUN");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        try {
            long id = ErpPlatformIo.importItemsCsv(es.companyId, es.userId,
                    body.getString("jobCode", "IMP-" + CryptoParse.longToString(AsyncClockNative.wallMillis())),
                    body.getString("csv", ""));
            ctx.jsonString("{\"batchId\":" + ErpJson.num(id) + "}");
        } catch (Exception ex) {
            ErpAuth.err(ctx, 400, "IMPORT_FAIL", ex.getMessage());
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpDemoResetHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDemoResetHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        try {
            if (ErpPlatformAuth.hasPermission(es.userId, es.companyId, "ADMIN.DEMO.RESET") == 0) {
                ErpAuth.err(ctx, 403, "FORBIDDEN", "ADMIN.DEMO.RESET");
                return JnFuture.completed(loop, null);
            }
            ErpBooks.resetDemoBook(es.companyId);
            ctx.jsonString("{\"ok\":true,\"bookKind\":\"DEMO\"}");
        } catch (Exception ex) {
            String msg = "";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            if (msg.indexOf("FORMAL_BOOK") >= 0 || msg.indexOf("DEMO_MODE_REQUIRED") >= 0) {
                ErpAuth.err(ctx, 403, "FORBIDDEN", msg);
            } else {
                ErpAuth.err(ctx, 400, "RESET_FAIL", msg);
            }
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpBookInfoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpBookInfoHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "login required");
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT code, name, book_kind, timezone, currency_code, amount_scale, setup_completed FROM companies WHERE id=?",
                p);
        if (r.rows.size() == 0) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "company");
            return JnFuture.completed(loop, null);
        }
        Row row = r.rows.get(0);
        ctx.jsonString("{\"code\":" + ErpJson.str(row.getString(0))
                + ",\"name\":" + ErpJson.str(row.getString(1))
                + ",\"bookKind\":" + ErpJson.str(row.getString(2))
                + ",\"timezone\":" + ErpJson.str(row.getString(3))
                + ",\"currency\":" + ErpJson.str(row.getString(4))
                + ",\"amountScale\":" + ErpJson.num(row.getLong(5))
                + ",\"setupCompleted\":" + CryptoParse.longToString(row.getInt(6))
                + ",\"demoSeedVersion\":" + CryptoParse.longToString(ErpBooks.demoSeedVersion(es.companyId))
                + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpHelpTutorialsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpHelpTutorialsHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        ctx.jsonString(ErpHelp.tutorialsJson());
        return JnFuture.completed(loop, null);
    }
}

