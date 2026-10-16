import jnative.web.*;
import jnative.json.*;

/**
 * JNative ERP Phase-1 — purchase/sales/inventory posting loop.
 *
 * Default DB: postgresql://jnative_erp@127.0.0.1:5432/jnative_erp
 * Fallback:    DATABASE_URL=sqlite:./build/erp/erp.db
 * @author 张晓达
 */
public class ErpServer {
    @Native(symbol = "exit")
    static native void exitProcess(int code);

    static void printEx(Exception ex) {
        if (ex == null) {
            System.out.println("EX_NULL");
            return;
        }
        String msg = ex.getMessage();
        if (msg != null && msg.length() > 0) {
            System.out.println(msg);
        } else {
            System.out.println("EX_EMPTY_MESSAGE");
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--workflow-uat-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpWorkflowUatTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                printEx(ex);
                exitProcess(2);
            }
        }
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
        if (args.length > 0 && args[0].equals("--sales-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpSalesLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL sales exception");
                printEx(ex);
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
                printEx(ex);
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--settlement-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpFinanceSettlementTest.runAll();
                if (pass != 0) {
                    pass = ErpPaymentVoidTest.runAll();
                }
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL settlement exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--void-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpPaymentVoidTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL void exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--recon-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpFinanceReconTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL recon exception");
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--costing-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpCostingLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL costing exception");
                if (ex.getMessage() != null && ex.getMessage().length() > 0) {
                    System.out.println(ex.getMessage());
                } else {
                    System.out.println("EX_EMPTY_MESSAGE");
                }
                exitProcess(2);
            }
        }
        if (args.length > 0 && args[0].equals("--gl-test")) {
            Erp.loop = new EventLoop();
            try {
                ErpDb.setup(Erp.loop);
                int pass = ErpGlLogicTest.runAll();
                if (pass != 0) {
                    exitProcess(0);
                }
                exitProcess(1);
            } catch (Exception ex) {
                System.out.println("FAIL gl exception");
                printEx(ex);
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
                ErpDemoScenarioSeed.forceReseed(companyId);
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
        /* Socket read timeouts are not wired on TCP yet — disable keep-alive so workers
           are not stuck in blocking recv after Safari parallel asset loads. */
        app.config.keepAliveMax = 1;
        app.config.readTimeoutMs = 0;
        app.config.workers = 4;
        app.bindEventLoop(Erp.loop);
        app.use(Cors.defaults());
        app.use(WebMiddleware.logger());
        app.staticFiles("/", ErpProductConfig.publicPath);
        app.staticFiles("/wasm", ErpProductConfig.wasmPath);
        app.onError(new ErpErrorHandler());

        app.get("/health", new ErpHealthHandler());
        app.get("/ready", new ErpReadyHandler());
        app.get("/version", new ErpVersionHandler());

        app.futurePost("/api/auth/login", new ErpLoginHandler(Erp.loop));
        app.futurePost("/api/auth/logout", new ErpLogoutHandler(Erp.loop));
        app.futureGet("/api/auth/currentUser", new ErpMeHandler(Erp.loop));
        app.futureGet("/api/auth/demo-available", new ErpDemoAvailableHandler(Erp.loop));
        app.futurePost("/api/auth/demo-enter", new ErpDemoEnterHandler(Erp.loop));

        app.futureGet("/api/erp/dashboard", new ErpDashboardHandler(Erp.loop));
        app.futureGet("/api/erp/dashboard/summary", new ErpDashSummaryHandler(Erp.loop));
        app.futureGet("/api/erp/finance/summary", new ErpFinanceSummaryHandler(Erp.loop));
        app.futureGet("/api/erp/partners/:id/contacts", new ErpPartnerContactsHandler(Erp.loop));
        app.futureGet("/api/erp/partners", new ErpPartnersHandler(Erp.loop));
        app.futureGet("/api/erp/items", new ErpItemsHandler(Erp.loop));
        app.futurePost("/api/erp/items", new ErpCreateItemHandler(Erp.loop));
        app.futureGet("/api/erp/items/:id", new ErpGetItemHandler(Erp.loop));
        app.futurePost("/api/erp/items/:id", new ErpUpdateItemHandler(Erp.loop));
        app.futurePut("/api/erp/items/:id", new ErpUpdateItemHandler(Erp.loop));
        app.futureGet("/api/erp/warehouses", new ErpWarehousesHandler(Erp.loop));
        app.futureGet("/api/erp/inventory", new ErpInventoryHandler(Erp.loop));
        app.futureGet("/api/erp/inventory/transactions", new ErpInventoryTxnsHandler(Erp.loop));
        app.futureGet("/api/erp/inventory/costing", new ErpCostingBalancesHandler(Erp.loop));
        app.futureGet("/api/erp/inventory/cost-events", new ErpCostingEventsHandler(Erp.loop));
        app.futureGet("/api/erp/inventory/margin", new ErpCostingMarginHandler(Erp.loop));
        app.futureGet("/api/erp/inventory/cost-integrity", new ErpCostingIntegrityHandler(Erp.loop));
        app.futureGet("/api/erp/categories", new ErpCategoriesHandler(Erp.loop));
        app.futureGet("/api/erp/units", new ErpUnitsHandler(Erp.loop));
        app.futureGet("/api/erp/employees", new ErpEmployeesHandler(Erp.loop));
        app.futureGet("/api/erp/departments", new ErpDepartmentsHandler(Erp.loop));

        app.futureGet("/api/erp/purchaseRequests", new ErpListPrHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseRequests", new ErpCreatePrHandler(Erp.loop));
        app.futureGet("/api/erp/purchaseRequests/:id", new ErpGetPrHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseRequests/:id/approve", new ErpApprovePrHandler(Erp.loop));

        app.futureGet("/api/erp/purchaseOrders", new ErpListPoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders", new ErpCreatePoHandler(Erp.loop));
        app.futureGet("/api/erp/purchaseOrders/:id", new ErpGetPoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders/:id", new ErpUpdatePoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders/:id/submit", new ErpSubmitPoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders/:id/approve", new ErpApprovePoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders/:id/close", new ErpClosePoHandler(Erp.loop));
        app.futurePost("/api/erp/purchaseOrders/:id/cancel", new ErpCancelPoHandler(Erp.loop));
        app.futureGet("/api/erp/goodsReceipts", new ErpListGrHandler(Erp.loop));
        app.futureGet("/api/erp/goodsReceipts/:id", new ErpGetGrHandler(Erp.loop));
        app.futurePost("/api/erp/goodsReceipts", new ErpGrHandler(Erp.loop));
        app.futurePost("/api/erp/goodsReceipts/:id/reverse", new ErpReverseGrHandler(Erp.loop));
        app.futurePost("/api/erp/inventory/adjust", new ErpInvAdjustHandler(Erp.loop));
        app.futurePost("/api/erp/inventory/count", new ErpInvCountHandler(Erp.loop));
        app.futureGet("/api/erp/inventory/alerts", new ErpInvAlertsHandler(Erp.loop));

        app.futureGet("/api/erp/salesOrders", new ErpListSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders", new ErpCreateSoHandler(Erp.loop));
        app.futureGet("/api/erp/salesOrders/:id", new ErpGetSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders/:id", new ErpUpdateSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders/:id/submit", new ErpSubmitSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders/:id/approve", new ErpApproveSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders/:id/cancel", new ErpCancelSoHandler(Erp.loop));
        app.futurePost("/api/erp/salesOrders/:id/close", new ErpCloseSoHandler(Erp.loop));
        app.futureGet("/api/erp/deliveries", new ErpListDlHandler(Erp.loop));
        app.futureGet("/api/erp/deliveries/:id", new ErpGetDlHandler(Erp.loop));
        app.futurePost("/api/erp/deliveries", new ErpDlHandler(Erp.loop));
        app.futurePost("/api/erp/deliveries/:id/reverse", new ErpReverseDlHandler(Erp.loop));
        app.futurePost("/api/erp/salesReturns", new ErpSalesReturnHandler(Erp.loop));

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
        app.futurePost("/api/erp/finance/ar", new ErpFinanceArCreateHandler(Erp.loop));
        app.futurePost("/api/erp/finance/ar/:id/post", new ErpFinanceArPostHandler(Erp.loop));
        app.futureGet("/api/erp/finance/ap", new ErpFinanceApListHandler(Erp.loop));
        app.futurePost("/api/erp/finance/ap", new ErpFinanceApCreateHandler(Erp.loop));
        app.futurePost("/api/erp/finance/ap/:id/post", new ErpFinanceApPostHandler(Erp.loop));
        app.futureGet("/api/erp/finance/banks", new ErpFinanceBanksHandler(Erp.loop));
        app.futureGet("/api/erp/finance/periods", new ErpFinancePeriodsHandler(Erp.loop));
        app.futureGet("/api/erp/finance/incoming-payments", new ErpFinanceIncomingPayHandler(Erp.loop));
        app.futurePost("/api/erp/finance/incoming-payments", new ErpFinanceIncomingPayPostHandler(Erp.loop));
        app.futureGet("/api/erp/finance/outgoing-payments", new ErpFinanceOutgoingPayHandler(Erp.loop));
        app.futurePost("/api/erp/finance/outgoing-payments", new ErpFinanceOutgoingPayPostHandler(Erp.loop));
        app.futureGet("/api/erp/finance/trialBalance", new ErpFinanceTrialHandler(Erp.loop));
        app.futureGet("/api/erp/finance/gl", new ErpFinanceGlHandler(Erp.loop));
        app.futureGet("/api/erp/finance/arAgeing", new ErpFinanceArAgeingHandler(Erp.loop));
        app.futureGet("/api/erp/finance/apAgeing", new ErpFinanceApAgeingHandler(Erp.loop));
        app.futurePost("/api/erp/finance/receipts", new ErpSettlementReceiptCreateHandler(Erp.loop));
        app.futurePost("/api/erp/finance/receipts/:id/post", new ErpSettlementReceiptPostHandler(Erp.loop));
        app.futurePost("/api/erp/finance/receipts/allocate", new ErpSettlementReceiptAllocHandler(Erp.loop));
        app.futurePost("/api/erp/finance/receipts/reverse-alloc", new ErpSettlementReceiptRevHandler(Erp.loop));
        app.futurePost("/api/erp/finance/receipts/:id/void", new ErpSettlementReceiptVoidHandler(Erp.loop));
        app.futurePost("/api/erp/finance/payments", new ErpSettlementPaymentCreateHandler(Erp.loop));
        app.futurePost("/api/erp/finance/payments/:id/post", new ErpSettlementPaymentPostHandler(Erp.loop));
        app.futurePost("/api/erp/finance/payments/allocate", new ErpSettlementPaymentAllocHandler(Erp.loop));
        app.futurePost("/api/erp/finance/payments/reverse-alloc", new ErpSettlementPaymentRevHandler(Erp.loop));
        app.futurePost("/api/erp/finance/payments/:id/void", new ErpSettlementPaymentVoidHandler(Erp.loop));
        app.futurePost("/api/erp/finance/ar/:id/cancel-provisional", new ErpFinanceArCancelProvHandler(Erp.loop));
        app.futureGet("/api/erp/finance/party-statement", new ErpSettlementPartyHandler(Erp.loop));
        app.futureGet("/api/erp/finance/settlement-integrity", new ErpSettlementIntegrityHandler(Erp.loop));
        app.futureGet("/api/erp/finance/cash-movements", new ErpSettlementCashHandler(Erp.loop));
        app.futureGet("/api/erp/finance/gl-events", new ErpGlEventsHandler(Erp.loop));
        app.futurePost("/api/erp/finance/gl-events", new ErpGlEventPostHandler(Erp.loop));
        app.futureGet("/api/erp/finance/gl-recon", new ErpGlReconHandler(Erp.loop));
        app.futurePost("/api/erp/finance/journals/:id/approve", new ErpGlApproveHandler(Erp.loop));
        app.futurePost("/api/erp/finance/journals/:id/post", new ErpGlPostApprovedHandler(Erp.loop));
        app.futurePost("/api/erp/finance/journals/:id/reject", new ErpGlRejectHandler(Erp.loop));
        app.futurePost("/api/erp/finance/journals/:id/reverse", new ErpGlReverseHandler(Erp.loop));
        app.futurePost("/api/erp/finance/periods/:id/close", new ErpGlClosePeriodHandler(Erp.loop));
        app.futurePost("/api/erp/finance/periods/:id/reopen", new ErpGlReopenPeriodHandler(Erp.loop));
        app.futureGet("/api/erp/finance/periods/:id/close-blockers", new ErpGlCloseBlockersHandler(Erp.loop));

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

class ErpErrorHandler implements ErrorHandler {
    public void handle(WebContext ctx, RuntimeException error) {
        String msg = "error";
        if (error != null && error.getMessage() != null) {
            msg = error.getMessage();
        }
        System.out.println("HTTP_500 " + ctx.method() + " " + ctx.path() + " " + msg);
        if (ctx.response.committed == 0) {
            ErpAuth.err(ctx, 500, "INTERNAL", msg);
        }
    }
}

class ErpLoginHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpLoginHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        try {
            if (Erp.loginLimiter.allow("login") == 0) {
                ErpAuth.err(ctx, 429, "RATE_LIMITED", "too many login attempts");
                return JnFuture.completed(loop, null);
            }
            String raw = ctx.bodyString();
            if (raw == null || raw.length() == 0) {
                ErpAuth.err(ctx, 400, "BAD_REQUEST", "missing login body");
                return JnFuture.completed(loop, null);
            }
            JsonValue parsed = Json.parse(raw);
            if (parsed == null || parsed.isObject() == false) {
                ErpAuth.err(ctx, 400, "BAD_REQUEST", "invalid login JSON");
                return JnFuture.completed(loop, null);
            }
            JsonObject body = parsed.asObject();
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
        } catch (RuntimeException ex) {
            String msg = "login failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            System.out.println("LOGIN_ERROR " + msg);
            ErpAuth.err(ctx, 500, "INTERNAL", msg);
            return JnFuture.completed(loop, null);
        }
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        UserRow user = ErpAuth.loadUser(es.userId, es.companyId);
        if (user == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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

/**
 * Kind-specific dashboard aggregates. Query: ?kind=sales|purchasing|inventory|finance|executive
 * Amounts are integer cents; currency from company.functional/currency_code.
 */
class ErpDashSummaryHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDashSummaryHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        String kind = ctx.query("kind");
        if (kind == null || kind.length() == 0) {
            kind = "executive";
        }
        long companyId = es.companyId;
        String cid = CryptoParse.longToString(companyId);
        ArrayList<Object> empty = new ArrayList<Object>();
        long now = AsyncClockNative.wallMillis();
        long periodStart = 0L;
        long periodEnd = now;
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(cid);
        pp.add(CryptoParse.longToString(now));
        pp.add(CryptoParse.longToString(now));
        try {
            DbQueryResult pr = ErpDb.query(
                    "SELECT start_date, end_date FROM fiscal_periods WHERE company_id=? AND start_date<=? AND end_date>=? ORDER BY period_number LIMIT 1",
                    pp);
            if (pr.rows.size() > 0) {
                periodStart = pr.rows.get(0).getLong(0);
                periodEnd = pr.rows.get(0).getLong(1);
            }
        } catch (Exception ignored) {
        }
        String currency = "CNY";
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(cid);
        try {
            Object cur = ErpDb.queryOne("SELECT currency_code FROM companies WHERE id=?", cp, new StringMapper());
            if (cur != null && ((String) cur).length() > 0) {
                currency = (String) cur;
            }
        } catch (Exception ignored) {
        }
        String json = "";
        if (kind.equals("sales")) {
            Object orderAmt = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(l.quantity * l.unit_price_cents),0) FROM sales_order_lines l JOIN sales_orders o ON o.id=l.sales_order_id WHERE o.company_id="
                            + cid + " AND o.approval_status NOT IN ('CANCELLED') AND o.order_date>="
                            + CryptoParse.longToString(periodStart) + " AND o.order_date<="
                            + CryptoParse.longToString(periodEnd),
                    empty, new LongMapper());
            Object openQty = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(l.quantity - l.delivered_qty),0) FROM sales_order_lines l JOIN sales_orders o ON o.id=l.sales_order_id WHERE o.company_id="
                            + cid + " AND o.approval_status='APPROVED' AND (l.quantity - l.delivered_qty) > 0",
                    empty, new LongMapper());
            Object openAmt = ErpDb.queryOne(
                    "SELECT COALESCE(SUM((l.quantity - l.delivered_qty) * l.unit_price_cents),0) FROM sales_order_lines l JOIN sales_orders o ON o.id=l.sales_order_id WHERE o.company_id="
                            + cid + " AND o.approval_status='APPROVED' AND (l.quantity - l.delivered_qty) > 0",
                    empty, new LongMapper());
            Object overdue = ErpDb.queryOne(
                    "SELECT COUNT(*) FROM sales_orders WHERE company_id=" + cid
                            + " AND approval_status='APPROVED' AND delivery_date>0 AND delivery_date<"
                            + CryptoParse.longToString(now) + " AND fulfillment_status IN ('NONE','PARTIAL')",
                    empty, new LongMapper());
            json = "{\"kind\":\"sales\",\"currency\":" + ErpJson.str(currency)
                    + ",\"asOf\":" + ErpJson.num(now)
                    + ",\"periodStart\":" + ErpJson.num(periodStart)
                    + ",\"periodEnd\":" + ErpJson.num(periodEnd)
                    + ",\"basis\":\"period SO line qty*unit_price_cents (excl. cancelled); open=APPROVED undelivered\""
                    + ",\"periodOrderAmount\":" + ErpJson.num(ErpNum.asLong(orderAmt))
                    + ",\"openDeliveryQty\":" + ErpJson.num(ErpNum.asLong(openQty))
                    + ",\"openDeliveryAmount\":" + ErpJson.num(ErpNum.asLong(openAmt))
                    + ",\"overdueDeliveryCount\":" + ErpJson.num(ErpNum.asLong(overdue))
                    + ",\"drill\":{\"periodOrderAmount\":\"sales.orders\",\"openDeliveryQty\":\"sales.orders\",\"overdueDeliveryCount\":\"sales.orders\"}}";
        } else {
            if (kind.equals("purchasing")) {
                Object orderAmt = ErpDb.queryOne(
                        "SELECT COALESCE(SUM(l.quantity * l.unit_price_cents),0) FROM purchase_order_lines l JOIN purchase_orders o ON o.id=l.purchase_order_id WHERE o.company_id="
                                + cid + " AND o.status NOT IN ('CANCELLED') AND o.order_date>="
                                + CryptoParse.longToString(periodStart) + " AND o.order_date<="
                                + CryptoParse.longToString(periodEnd),
                        empty, new LongMapper());
                Object openQty = ErpDb.queryOne(
                        "SELECT COALESCE(SUM(l.quantity - l.received_qty),0) FROM purchase_order_lines l JOIN purchase_orders o ON o.id=l.purchase_order_id WHERE o.company_id="
                                + cid + " AND o.status IN ('APPROVED','PARTIALLY_RECEIVED') AND (l.quantity - l.received_qty) > 0",
                        empty, new LongMapper());
                Object openAmt = ErpDb.queryOne(
                        "SELECT COALESCE(SUM((l.quantity - l.received_qty) * l.unit_price_cents),0) FROM purchase_order_lines l JOIN purchase_orders o ON o.id=l.purchase_order_id WHERE o.company_id="
                                + cid + " AND o.status IN ('APPROVED','PARTIALLY_RECEIVED') AND (l.quantity - l.received_qty) > 0",
                        empty, new LongMapper());
                Object overdue = ErpDb.queryOne(
                        "SELECT COUNT(*) FROM purchase_orders WHERE company_id=" + cid
                                + " AND status IN ('APPROVED','PARTIALLY_RECEIVED') AND expected_date>0 AND expected_date<"
                                + CryptoParse.longToString(now),
                        empty, new LongMapper());
                json = "{\"kind\":\"purchasing\",\"currency\":" + ErpJson.str(currency)
                        + ",\"asOf\":" + ErpJson.num(now)
                        + ",\"periodStart\":" + ErpJson.num(periodStart)
                        + ",\"periodEnd\":" + ErpJson.num(periodEnd)
                        + ",\"basis\":\"period PO line qty*unit_price_cents (excl. cancelled); open=APPROVED/PARTIAL unreceived\""
                        + ",\"periodOrderAmount\":" + ErpJson.num(ErpNum.asLong(orderAmt))
                        + ",\"openReceiptQty\":" + ErpJson.num(ErpNum.asLong(openQty))
                        + ",\"openReceiptAmount\":" + ErpJson.num(ErpNum.asLong(openAmt))
                        + ",\"overdueReceiptCount\":" + ErpJson.num(ErpNum.asLong(overdue))
                        + ",\"drill\":{\"periodOrderAmount\":\"pur.orders\",\"openReceiptQty\":\"pur.orders\",\"overdueReceiptCount\":\"pur.orders\"}}";
            } else {
                if (kind.equals("inventory")) {
                    Object qty = ErpDb.queryOne(
                            "SELECT COALESCE(SUM(quantity_on_hand),0) FROM inventory_balances WHERE company_id=" + cid,
                            empty, new LongMapper());
                    Object valued = ErpDb.queryOne(
                            "SELECT COALESCE(SUM(value_amount),0) FROM inventory_balances WHERE company_id=" + cid,
                            empty, new LongMapper());
                    Object low = ErpDb.queryOne(
                            "SELECT COUNT(*) FROM inventory_balances WHERE company_id=" + cid
                                    + " AND quantity_on_hand<=0",
                            empty, new LongMapper());
                    Object pendingCost = ErpDb.queryOne(
                            "SELECT COUNT(*) FROM inventory_balances WHERE company_id=" + cid
                                    + " AND quantity_on_hand>0 AND COALESCE(value_amount,0)=0",
                            empty, new LongMapper());
                    json = "{\"kind\":\"inventory\",\"currency\":" + ErpJson.str(currency)
                            + ",\"asOf\":" + ErpJson.num(now)
                            + ",\"periodStart\":" + ErpJson.num(periodStart)
                            + ",\"periodEnd\":" + ErpJson.num(periodEnd)
                            + ",\"basis\":\"on-hand qty; valuedAmount=SUM(inventory_balances.value_amount); pendingCost=qty>0 and value=0\""
                            + ",\"onHandQty\":" + ErpJson.num(ErpNum.asLong(qty))
                            + ",\"valuedAmount\":" + ErpJson.num(ErpNum.asLong(valued))
                            + ",\"zeroOrNegStockCount\":" + ErpJson.num(ErpNum.asLong(low))
                            + ",\"pendingCostCount\":" + ErpJson.num(ErpNum.asLong(pendingCost))
                            + ",\"drill\":{\"onHandQty\":\"inv.balance\",\"valuedAmount\":\"inv.valuation\",\"pendingCostCount\":\"inv.balance\"}}";
                } else {
                    if (kind.equals("finance")) {
                        /* Reuse finance summary shape via redirect fields */
                        Object arOpen = ErpDb.queryOne(
                                "SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices WHERE company_id=" + cid
                                        + " AND status NOT IN ('CANCELLED','DRAFT')",
                                empty, new LongMapper());
                        Object apOpen = ErpDb.queryOne(
                                "SELECT COALESCE(SUM(open_amount),0) FROM ap_invoices WHERE company_id=" + cid
                                        + " AND status NOT IN ('CANCELLED','DRAFT')",
                                empty, new LongMapper());
                        Object receipts = ErpDb.queryOne(
                                "SELECT COALESCE(SUM(amount),0) FROM incoming_payments WHERE company_id=" + cid
                                        + " AND status='POSTED' AND payment_date>=" + CryptoParse.longToString(periodStart)
                                        + " AND payment_date<=" + CryptoParse.longToString(periodEnd),
                                empty, new LongMapper());
                        Object payments = ErpDb.queryOne(
                                "SELECT COALESCE(SUM(amount),0) FROM outgoing_payments WHERE company_id=" + cid
                                        + " AND status='POSTED' AND payment_date>=" + CryptoParse.longToString(periodStart)
                                        + " AND payment_date<=" + CryptoParse.longToString(periodEnd),
                                empty, new LongMapper());
                        Object pending = ErpDb.queryOne(
                                "SELECT COUNT(*) FROM gl_posting_events WHERE company_id=" + cid
                                        + " AND status IN ('PENDING','FAILED')",
                                empty, new LongMapper());
                        json = "{\"kind\":\"finance\",\"currency\":" + ErpJson.str(currency)
                                + ",\"asOf\":" + ErpJson.num(now)
                                + ",\"periodStart\":" + ErpJson.num(periodStart)
                                + ",\"periodEnd\":" + ErpJson.num(periodEnd)
                                + ",\"basis\":\"open_amount AR/AP; POSTED receipts/payments in fiscal period; gl_posting_events PENDING/FAILED\""
                                + ",\"arOpenAmount\":" + ErpJson.num(ErpNum.asLong(arOpen))
                                + ",\"apOpenAmount\":" + ErpJson.num(ErpNum.asLong(apOpen))
                                + ",\"periodReceipts\":" + ErpJson.num(ErpNum.asLong(receipts))
                                + ",\"periodPayments\":" + ErpJson.num(ErpNum.asLong(payments))
                                + ",\"pendingGlEvents\":" + ErpJson.num(ErpNum.asLong(pending))
                                + ",\"drill\":{\"arOpenAmount\":\"fin.ar\",\"apOpenAmount\":\"fin.ap\",\"pendingGlEvents\":\"fin.glEvents\"}}";
                    } else {
                        /* executive — core todos, not master-data counts */
                        Object openSo = ErpDb.queryOne(
                                "SELECT COUNT(*) FROM sales_orders WHERE company_id=" + cid
                                        + " AND approval_status IN ('DRAFT','SUBMITTED')",
                                empty, new LongMapper());
                        Object openPo = ErpDb.queryOne(
                                "SELECT COUNT(*) FROM purchase_orders WHERE company_id=" + cid
                                        + " AND status IN ('DRAFT','SUBMITTED')",
                                empty, new LongMapper());
                        Object arOpen = ErpDb.queryOne(
                                "SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices WHERE company_id=" + cid
                                        + " AND status NOT IN ('CANCELLED','DRAFT')",
                                empty, new LongMapper());
                        Object apOpen = ErpDb.queryOne(
                                "SELECT COALESCE(SUM(open_amount),0) FROM ap_invoices WHERE company_id=" + cid
                                        + " AND status NOT IN ('CANCELLED','DRAFT')",
                                empty, new LongMapper());
                        Object pendingGl = ErpDb.queryOne(
                                "SELECT COUNT(*) FROM gl_posting_events WHERE company_id=" + cid
                                        + " AND status IN ('PENDING','FAILED')",
                                empty, new LongMapper());
                        json = "{\"kind\":\"executive\",\"currency\":" + ErpJson.str(currency)
                                + ",\"asOf\":" + ErpJson.num(now)
                                + ",\"periodStart\":" + ErpJson.num(periodStart)
                                + ",\"periodEnd\":" + ErpJson.num(periodEnd)
                                + ",\"basis\":\"draft/submitted docs + open AR/AP + pending GL events\""
                                + ",\"draftSoCount\":" + ErpJson.num(ErpNum.asLong(openSo))
                                + ",\"draftPoCount\":" + ErpJson.num(ErpNum.asLong(openPo))
                                + ",\"arOpenAmount\":" + ErpJson.num(ErpNum.asLong(arOpen))
                                + ",\"apOpenAmount\":" + ErpJson.num(ErpNum.asLong(apOpen))
                                + ",\"pendingGlEvents\":" + ErpJson.num(ErpNum.asLong(pendingGl))
                                + ",\"drill\":{\"draftSoCount\":\"sales.orders\",\"draftPoCount\":\"pur.orders\",\"arOpenAmount\":\"fin.ar\",\"apOpenAmount\":\"fin.ap\",\"pendingGlEvents\":\"fin.glEvents\"}}";
                    }
                }
            }
        }
        ctx.jsonString(json);
        return JnFuture.completed(loop, null);
    }
}

class ErpPartnerContactsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpPartnerContactsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long partnerId = ErpNum.parseLong(ctx.param("id"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(partnerId));
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name FROM business_partners WHERE id=? AND company_id=?", p);
        if (r.rows.size() == 0) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "partner not found");
            return JnFuture.completed(loop, null);
        }
        Row row = r.rows.get(0);
        String name = row.getString(2);
        if (name == null) {
            name = "";
        }
        /* Synthetic primary contact from partner master — no separate contacts table yet. */
        ctx.jsonString("[{\"id\":\"primary-" + CryptoParse.longToString(partnerId)
                + "\",\"name\":" + ErpJson.str(name)
                + ",\"title\":\"primary\",\"partnerId\":" + ErpJson.num(partnerId) + "}]");
        return JnFuture.completed(loop, null);
    }
}

/** Finance workbench KPIs from real AR/AP/cash/GL-event sources (management book). */
class ErpFinanceSummaryHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceSummaryHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long companyId = es.companyId;
        String cid = CryptoParse.longToString(companyId);
        ArrayList<Object> empty = new ArrayList<Object>();
        Object arOpen = ErpDb.queryOne(
                "SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices WHERE company_id=" + cid
                        + " AND status NOT IN ('CANCELLED','DRAFT')",
                empty, new LongMapper());
        Object apOpen = ErpDb.queryOne(
                "SELECT COALESCE(SUM(open_amount),0) FROM ap_invoices WHERE company_id=" + cid
                        + " AND status NOT IN ('CANCELLED','DRAFT')",
                empty, new LongMapper());
        long now = AsyncClockNative.wallMillis();
        long periodStart = 0L;
        long periodEnd = now;
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(cid);
        pp.add(CryptoParse.longToString(now));
        pp.add(CryptoParse.longToString(now));
        DbQueryResult pr = ErpDb.query(
                "SELECT start_date, end_date FROM fiscal_periods WHERE company_id=? AND start_date<=? AND end_date>=? ORDER BY period_number LIMIT 1",
                pp);
        if (pr.rows.size() > 0) {
            periodStart = pr.rows.get(0).getLong(0);
            periodEnd = pr.rows.get(0).getLong(1);
        }
        long receipts = 0L;
        long payments = 0L;
        try {
            Object rcv = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(amount),0) FROM incoming_payments WHERE company_id=" + cid
                            + " AND status='POSTED' AND payment_date>=" + CryptoParse.longToString(periodStart)
                            + " AND payment_date<=" + CryptoParse.longToString(periodEnd),
                    empty, new LongMapper());
            receipts = ErpNum.asLong(rcv);
            Object pay = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(amount),0) FROM outgoing_payments WHERE company_id=" + cid
                            + " AND status='POSTED' AND payment_date>=" + CryptoParse.longToString(periodStart)
                            + " AND payment_date<=" + CryptoParse.longToString(periodEnd),
                    empty, new LongMapper());
            payments = ErpNum.asLong(pay);
        } catch (Exception ignored) {
        }
        long pendingEvents = 0L;
        try {
            Object pe = ErpDb.queryOne(
                    "SELECT COUNT(*) FROM gl_posting_events WHERE company_id=" + cid
                            + " AND status IN ('PENDING','FAILED')",
                    empty, new LongMapper());
            pendingEvents = ErpNum.asLong(pe);
        } catch (Exception ignored) {
        }
        String currency = "CNY";
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(cid);
        try {
            Object cur = ErpDb.queryOne("SELECT currency_code FROM companies WHERE id=?", cp, new StringMapper());
            if (cur != null && ((String) cur).length() > 0) {
                currency = (String) cur;
            }
        } catch (Exception ignored) {
        }
        ctx.jsonString("{\"currency\":" + ErpJson.str(currency)
                + ",\"asOf\":" + ErpJson.num(now)
                + ",\"periodStart\":" + ErpJson.num(periodStart)
                + ",\"periodEnd\":" + ErpJson.num(periodEnd)
                + ",\"arOpenAmount\":" + ErpJson.num(ErpNum.asLong(arOpen))
                + ",\"apOpenAmount\":" + ErpJson.num(ErpNum.asLong(apOpen))
                + ",\"periodReceipts\":" + ErpJson.num(receipts)
                + ",\"periodPayments\":" + ErpJson.num(payments)
                + ",\"pendingGlEvents\":" + ErpJson.num(pendingEvents)
                + ",\"basis\":" + ErpJson.str("open_amount AR/AP; POSTED receipts/payments in open fiscal period; gl_posting_events PENDING/FAILED")
                + "}");
        return JnFuture.completed(loop, null);
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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

class ErpGetItemHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGetItemHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        ItemDto item = ErpItems.get(es.companyId, id);
        if (item == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "item not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpJson.item(item));
        return JnFuture.completed(loop, null);
    }
}

class ErpUpdateItemHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpUpdateItemHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        try {
            ErpSession es = ErpRequire.auth(ctx);
            if (es == null) {
                ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
                return JnFuture.completed(loop, null);
            }
            if (ErpAuth.checkCsrf(ctx) == 0) {
                ErpAuth.err(ctx, 403, "CSRF", "invalid csrf token");
                return JnFuture.completed(loop, null);
            }
            long id = ErpNum.parseLong(ctx.param("id"));
            String raw = ctx.bodyString();
            if (raw == null || raw.length() == 0) {
                ErpAuth.err(ctx, 400, "EMPTY_BODY", "JSON body required");
                return JnFuture.completed(loop, null);
            }
            JsonObject body = Json.parse(raw).asObject();
            String name = body.getString("name", "");
            long price = body.getLong("unitPriceCents", -1L);
            if (price < 0L) {
                long major = body.getLong("price", -1L);
                if (major >= 0L && major < 100000000L) {
                    price = major * 100L;
                } else {
                    ItemDto cur = ErpItems.get(es.companyId, id);
                    if (cur == null) {
                        ErpAuth.err(ctx, 404, "NOT_FOUND", "item not found");
                        return JnFuture.completed(loop, null);
                    }
                    price = cur.unitPriceCents;
                }
            }
            ItemDto updated = ErpItems.update(es.companyId, id, name, price);
            if (updated == null) {
                ErpAuth.err(ctx, 400, "ITEM_UPDATE_FAILED", "invalid item or name");
                return JnFuture.completed(loop, null);
            }
            ctx.jsonString(ErpJson.item(updated));
        } catch (Exception ex) {
            String msg = "item update failed";
            if (ex.getMessage() != null && ex.getMessage().length() > 0) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 500, "ITEM_UPDATE_EX", msg);
        }
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        int canBal = ErpPlatformAuth.hasPermission(es.userId, es.companyId, "INVENTORY.BALANCE.READ");
        int canAvail = ErpPlatformAuth.hasPermission(es.userId, es.companyId, "INVENTORY.AVAILABLE.READ");
        if (canBal == 0 && canAvail == 0) {
            ErpAuth.err(ctx, 403, "FORBIDDEN", "INVENTORY.AVAILABLE.READ");
            return JnFuture.completed(loop, null);
        }
        long wh = ErpNum.parseLong(ctx.query("warehouseId"));
        ArrayList<Object> list = ErpInventory.balances(es.companyId, wh);
        if (canBal == 0) {
            /* Available-only: strip valuation / cost state for sales SO qty checks. */
            int i = 0;
            while (i < list.size()) {
                InventoryBalanceDto b = (InventoryBalanceDto) list.get(i);
                b.valueAmount = 0L;
                b.costState = "REDACTED";
                i = i + 1;
            }
        }
        ctx.jsonString(ErpJson.balanceList(list));
        return JnFuture.completed(loop, null);
    }
}

class ErpCostingBalancesHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCostingBalancesHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "INVENTORY.BALANCE.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long wh = ErpNum.parseLong(ctx.query("warehouseId"));
        ctx.jsonString(ErpCosting.balancesJson(es.companyId, wh));
        return JnFuture.completed(loop, null);
    }
}

class ErpCostingEventsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCostingEventsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "INVENTORY.BALANCE.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long wh = ErpNum.parseLong(ctx.query("warehouseId"));
        long itemId = ErpNum.parseLong(ctx.query("itemId"));
        ctx.jsonString(ErpCosting.costEventsJson(es.companyId, wh, itemId, 200));
        return JnFuture.completed(loop, null);
    }
}

class ErpCostingMarginHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCostingMarginHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "INVENTORY.BALANCE.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpCosting.marginJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpCostingIntegrityHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCostingIntegrityHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "INVENTORY.BALANCE.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        int pass = ErpCosting.runConservationChecks(es.companyId);
        ctx.jsonString("{\"ok\":" + String.valueOf(pass) + "}");
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
        try {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            long supplierId = body.getLong("supplierId", 0L);
            if (supplierId <= 0L) {
                supplierId = body.getLong("partnerId", 0L);
            }
            long warehouseId = body.getLong("warehouseId", 0L);
            long orderDate = ErpDocMoney.parseBizDate(body, "orderDate", "date");
            long expectedDate = ErpDocMoney.parseBizDate(body, "expectedDate", "");
            String notes = body.getString("notes", "");
            String currency = body.getString("currency", "CNY");
            String contactName = body.getString("contactName", "");
            String idem = ctx.getHeader("Idempotency-Key");
            if (idem == null || idem.length() == 0) {
                idem = body.getString("idempotencyKey", "");
            }
            ArrayList<Object> lines = ErpBodyParse.parsePoLines(body);
            PurchaseOrderDto po = ErpPurchasing.createDraftFull(es.companyId, supplierId, warehouseId, orderDate,
                    expectedDate, notes, currency, lines, contactName, idem);
            ctx.jsonString(ErpPurchasing.poJson(po));
        } catch (Exception ex) {
            String msg = "PO create failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "PO_CREATE_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpUpdatePoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpUpdatePoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            long id = ErpNum.parseLong(ctx.param("id"));
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            long supplierId = body.getLong("supplierId", 0L);
            if (supplierId <= 0L) {
                supplierId = body.getLong("partnerId", 0L);
            }
            long warehouseId = body.getLong("warehouseId", 0L);
            long orderDate = ErpDocMoney.parseBizDate(body, "orderDate", "date");
            long expectedDate = ErpDocMoney.parseBizDate(body, "expectedDate", "");
            String notes = body.getString("notes", "");
            String currency = body.getString("currency", "CNY");
            String contactName = body.getString("contactName", "");
            ArrayList<Object> lines = ErpBodyParse.parsePoLines(body);
            PurchaseOrderDto po = ErpPurchasing.updateDraft(es.companyId, id, supplierId, warehouseId, orderDate,
                    expectedDate, notes, currency, lines, contactName);
            ctx.jsonString(ErpPurchasing.poJson(po));
        } catch (Exception ex) {
            String msg = "PO update failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "PO_UPDATE_FAILED", msg);
        }
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PurchaseOrderDto po = ErpPurchasing.get(es.companyId, id);
        if (po == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "PO not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpPurchasing.poJson(po));
        return JnFuture.completed(loop, null);
    }
}

class ErpSubmitPoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSubmitPoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            long id = ErpNum.parseLong(ctx.param("id"));
            PurchaseOrderDto po = ErpPurchasing.submit(es.companyId, id);
            ctx.jsonString(ErpPurchasing.poJson(po));
        } catch (Exception ex) {
            String msg = "PO submit failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "PO_SUBMIT_FAILED", msg);
        }
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
        try {
            long id = ErpNum.parseLong(ctx.param("id"));
            PurchaseOrderDto po = ErpPurchasing.approve(es.companyId, id);
            ctx.jsonString(ErpPurchasing.poJson(po));
        } catch (Exception ex) {
            String msg = "PO approve failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "PO_APPROVE_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpClosePoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpClosePoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.APPROVE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            long id = ErpNum.parseLong(ctx.param("id"));
            PurchaseOrderDto po = ErpPurchasing.close(es.companyId, id);
            ctx.jsonString(ErpPurchasing.poJson(po));
        } catch (Exception ex) {
            String msg = "PO close failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "PO_CLOSE_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpCancelPoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCancelPoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            long id = ErpNum.parseLong(ctx.param("id"));
            PurchaseOrderDto po = ErpPurchasing.cancel(es.companyId, id);
            ctx.jsonString(ErpPurchasing.poJson(po));
        } catch (Exception ex) {
            String msg = "PO cancel failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "PO_CANCEL_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpInvAdjustHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpInvAdjustHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String key = ctx.getHeader("Idempotency-Key");
        if (key == null || key.length() == 0) {
            key = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpInventory.adjust(es.companyId, es.userId, body.getLong("warehouseId", 0L),
                body.getLong("itemId", 0L), body.getInt("quantityDelta", 0), body.getString("reason", ""), key);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpInvCountHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpInvCountHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "PURCHASE.ORDER.POST");
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
                InvCountLineInput ln = new InvCountLineInput();
                ln.itemId = o.getLong("itemId", 0L);
                ln.countedQty = o.getInt("countedQty", 0);
                lines.add(ln);
                i = i + 1;
            }
        }
        PostResultDto r = ErpInventory.postCount(es.companyId, es.userId, body.getLong("warehouseId", 0L), lines,
                body.getString("notes", ""), key);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpInvAlertsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpInvAlertsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpInventory.alertsJson(es.companyId));
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
        try {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            long customerId = body.getLong("customerId", 0L);
            if (customerId <= 0L) {
                customerId = body.getLong("partnerId", 0L);
            }
            long warehouseId = body.getLong("warehouseId", 0L);
            long orderDate = ErpDocMoney.parseBizDate(body, "orderDate", "date");
            long deliveryDate = ErpDocMoney.parseBizDate(body, "deliveryDate", "");
            String notes = body.getString("notes", "");
            String currency = body.getString("currency", "CNY");
            String contactName = body.getString("contactName", "");
            String idem = ctx.getHeader("Idempotency-Key");
            if (idem == null || idem.length() == 0) {
                idem = body.getString("idempotencyKey", "");
            }
            ArrayList<Object> lines = ErpBodyParse.parseSoLines(body);
            SalesOrderDto so = ErpSales.createDraftFull(es.companyId, customerId, warehouseId, orderDate,
                    deliveryDate, notes, currency, lines, contactName, idem);
            ctx.jsonString(ErpSales.soJson(so));
        } catch (Exception ex) {
            String msg = "SO create failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "SO_CREATE_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpUpdateSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpUpdateSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            long id = ErpNum.parseLong(ctx.param("id"));
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            long customerId = body.getLong("customerId", 0L);
            if (customerId <= 0L) {
                customerId = body.getLong("partnerId", 0L);
            }
            SalesOrderDto so = ErpSales.updateDraft(es.companyId, id, customerId, body.getLong("warehouseId", 0L),
                    ErpDocMoney.parseBizDate(body, "orderDate", "date"),
                    ErpDocMoney.parseBizDate(body, "deliveryDate", ""),
                    body.getString("notes", ""), body.getString("currency", "CNY"),
                    ErpBodyParse.parseSoLines(body), body.getString("contactName", ""));
            ctx.jsonString(ErpSales.soJson(so));
        } catch (Exception ex) {
            String msg = "SO update failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "SO_UPDATE_FAILED", msg);
        }
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        SalesOrderDto so = ErpSales.get(es.companyId, id);
        if (so == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "SO not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpSales.soJson(so));
        return JnFuture.completed(loop, null);
    }
}

class ErpSubmitSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSubmitSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            SalesOrderDto so = ErpSales.submit(es.companyId, ErpNum.parseLong(ctx.param("id")));
            ctx.jsonString(ErpSales.soJson(so));
        } catch (Exception ex) {
            String msg = "SO submit failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "SO_SUBMIT_FAILED", msg);
        }
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
        try {
            SalesOrderDto so = ErpSales.approve(es.companyId, ErpNum.parseLong(ctx.param("id")));
            ctx.jsonString(ErpSales.soJson(so));
        } catch (Exception ex) {
            String msg = "SO approve failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "SO_APPROVE_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpCancelSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCancelSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            ctx.jsonString(ErpSales.soJson(ErpSales.cancel(es.companyId, ErpNum.parseLong(ctx.param("id")))));
        } catch (Exception ex) {
            String msg = "SO cancel failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "SO_CANCEL_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpCloseSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCloseSoHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.APPROVE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        try {
            ctx.jsonString(ErpSales.soJson(ErpSales.closeRemaining(es.companyId, ErpNum.parseLong(ctx.param("id")))));
        } catch (Exception ex) {
            String msg = "SO close failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            ErpAuth.err(ctx, 400, "SO_CLOSE_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpSalesReturnHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSalesReturnHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "SALES.ORDER.POST");
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
                SalesReturnLineInput ln = new SalesReturnLineInput();
                ln.deliveryLineId = o.getLong("deliveryLineId", 0L);
                ln.quantity = o.getInt("quantity", 0);
                lines.add(ln);
                i = i + 1;
            }
        }
        PostResultDto r = ErpSalesReturn.post(es.companyId, body.getLong("salesOrderId", 0L),
                body.getLong("deliveryId", 0L), body.getLong("warehouseId", 0L), body.getInt("resellable", 1),
                body.getString("notes", ""), lines, key);
        ctx.status(200).jsonString(ErpJson.postResult(r));
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
            if (in.quantity <= 0) {
                in.quantity = o.getInt("qty", 0);
            }
            in.unitPriceCents = o.getLong("unitPriceCents", 0L);
            in.discountBps = o.getInt("discountBps", 0);
            in.taxBps = o.getInt("taxBps", 0);
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
            if (in.quantity <= 0) {
                in.quantity = o.getInt("qty", 0);
            }
            in.unitPriceCents = o.getLong("unitPriceCents", 0L);
            in.discountBps = o.getInt("discountBps", 0);
            in.taxBps = o.getInt("taxBps", 0);
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

    static ArrayList<Object> parsePaymentAllocations(JsonObject body) {
        ArrayList<Object> out = new ArrayList<Object>();
        JsonValue lv = body.get("allocations");
        if (lv == null || lv.kind != JsonValue.ARRAY_KIND) {
            return out;
        }
        JsonArray arr = lv.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject o = arr.get(i).asObject();
            PaymentAllocationInput a = new PaymentAllocationInput();
            a.invoiceId = o.getLong("invoiceId", 0L);
            a.allocatedAmount = o.getLong("allocatedAmount", 0L);
            out.add(a);
            i = i + 1;
        }
        return out;
    }

    static IncomingPaymentRequest parseIncomingPayment(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        IncomingPaymentRequest req = new IncomingPaymentRequest();
        req.customerId = body.getLong("customerId", 0L);
        req.paymentDate = body.getLong("paymentDate", AsyncClockNative.wallMillis());
        req.amount = body.getLong("amount", 0L);
        req.bankAccountId = body.getLong("bankAccountId", 0L);
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.paymentMethod = body.getString("paymentMethod", "");
        req.externalRef = body.getString("externalRef", "");
        req.notes = body.getString("notes", "");
        req.postNow = body.getInt("postNow", 1);
        req.allocations = parsePaymentAllocations(body);
        return req;
    }

    static OutgoingPaymentRequest parseOutgoingPayment(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        OutgoingPaymentRequest req = new OutgoingPaymentRequest();
        req.supplierId = body.getLong("supplierId", 0L);
        req.paymentDate = body.getLong("paymentDate", AsyncClockNative.wallMillis());
        req.amount = body.getLong("amount", 0L);
        req.bankAccountId = body.getLong("bankAccountId", 0L);
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.paymentMethod = body.getString("paymentMethod", "");
        req.externalRef = body.getString("externalRef", "");
        req.notes = body.getString("notes", "");
        req.postNow = body.getInt("postNow", 1);
        req.allocations = parsePaymentAllocations(body);
        return req;
    }

    static SettlementAllocateRequest parseSettlementAllocate(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        SettlementAllocateRequest req = new SettlementAllocateRequest();
        req.paymentId = body.getLong("paymentId", 0L);
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.reason = body.getString("reason", "");
        req.allocations = parsePaymentAllocations(body);
        return req;
    }

    static SettlementReverseRequest parseSettlementReverse(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        SettlementReverseRequest req = new SettlementReverseRequest();
        req.allocationId = body.getLong("allocationId", 0L);
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.reason = body.getString("reason", "");
        return req;
    }

    static PaymentVoidRequest parsePaymentVoid(WebContext ctx, long pathPaymentId) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        PaymentVoidRequest req = new PaymentVoidRequest();
        req.paymentId = pathPaymentId;
        if (req.paymentId <= 0L) {
            req.paymentId = body.getLong("paymentId", 0L);
        }
        req.idempotencyKey = ctx.getHeader("Idempotency-Key");
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            req.idempotencyKey = body.getString("idempotencyKey", "");
        }
        req.reason = body.getString("reason", "");
        return req;
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        try {
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
                String desc = j.description == null ? "" : j.description;
                out = out + "{\"id\":" + ErpJson.num(j.id)
                        + ",\"journalNumber\":" + ErpJson.str(j.journalNumber)
                        + ",\"postingDate\":" + ErpJson.num(j.postingDate)
                        + ",\"sourceType\":" + ErpJson.str(j.sourceType)
                        + ",\"status\":" + ErpJson.str(j.status)
                        + ",\"description\":" + ErpJson.str(desc)
                        + ",\"totalDebit\":" + ErpJson.num(j.totalDebit)
                        + ",\"totalCredit\":" + ErpJson.num(j.totalCredit) + "}";
                i = i + 1;
            }
            out = out + "]";
            ctx.jsonString(out);
        } catch (Exception ex) {
            String msg = "加载凭证失败";
            if (ex.getMessage() != null && ex.getMessage().length() > 0) {
                msg = msg + ": " + ex.getMessage();
            }
            System.out.println("ERR journals requestId=" + ErpAuth.nextRequestId() + " " + msg);
            ErpAuth.err(ctx, 500, "JOURNALS_FAILED", msg);
        }
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
                    + ",\"customerName\":" + ErpJson.str(ErpPurchasing.partnerName(es.companyId, d.customerId))
                    + ",\"invoiceNumber\":" + ErpJson.str(d.invoiceNumber)
                    + ",\"invoiceDate\":" + ErpJson.num(d.invoiceDate)
                    + ",\"dueDate\":" + ErpJson.num(d.dueDate)
                    + ",\"totalAmount\":" + ErpJson.num(d.totalAmount)
                    + ",\"paidAmount\":" + ErpJson.num(d.paidAmount)
                    + ",\"openAmount\":" + ErpJson.num(d.openAmount)
                    + ",\"adjustedAmount\":" + ErpJson.num(d.adjustedAmount)
                    + ",\"settlementStatus\":" + ErpJson.str(d.settlementStatus)
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
                    + ",\"supplierName\":" + ErpJson.str(ErpPurchasing.partnerName(es.companyId, d.supplierId))
                    + ",\"invoiceNumber\":" + ErpJson.str(d.invoiceNumber)
                    + ",\"invoiceDate\":" + ErpJson.num(d.invoiceDate)
                    + ",\"dueDate\":" + ErpJson.num(d.dueDate)
                    + ",\"totalAmount\":" + ErpJson.num(d.totalAmount)
                    + ",\"paidAmount\":" + ErpJson.num(d.paidAmount)
                    + ",\"openAmount\":" + ErpJson.num(d.openAmount)
                    + ",\"adjustedAmount\":" + ErpJson.num(d.adjustedAmount)
                    + ",\"settlementStatus\":" + ErpJson.str(d.settlementStatus)
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        try {
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
        } catch (Exception ex) {
            String msg = "试算平衡表加载失败";
            if (ex.getMessage() != null && ex.getMessage().length() > 0) {
                msg = msg + ": " + ex.getMessage();
            }
            System.out.println("ERR trialBalance " + msg);
            ErpAuth.err(ctx, 500, "TRIAL_BALANCE_FAILED", msg);
        }
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
                    + ",\"postingDate\":" + ErpJson.num(ln.postingDate)
                    + ",\"journalNumber\":" + ErpJson.str(ln.journalNumber)
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

class ErpGlEventsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlEventsHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        String st = ctx.query("status");
        if (st == null) {
            st = "";
        }
        ArrayList<Object> list = ErpGl.listEvents(es.companyId, st);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            GlPostingEventDto d = (GlPostingEventDto) list.get(i);
            out = out + "{\"id\":" + ErpJson.num(d.id)
                    + ",\"eventType\":" + ErpJson.str(d.eventType)
                    + ",\"sourceType\":" + ErpJson.str(d.sourceType)
                    + ",\"sourceId\":" + ErpJson.num(d.sourceId)
                    + ",\"status\":" + ErpJson.str(d.status)
                    + ",\"journalEntryId\":" + ErpJson.num(d.journalEntryId)
                    + ",\"amount\":" + ErpJson.num(d.amount)
                    + ",\"partnerId\":" + ErpJson.num(d.partnerId)
                    + ",\"errorCode\":" + ErpJson.str(d.errorCode)
                    + ",\"errorMessage\":" + ErpJson.str(d.errorMessage) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpGlEventPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlEventPostHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        if (body == null) {
            ErpAuth.err(ctx, 400, "BAD_JSON", "body required");
            return JnFuture.completed(loop, null);
        }
        PostResultDto r = ErpGl.postEvent(es.companyId, es.userId,
                body.getString("eventType", ""),
                body.getString("sourceType", "MANUAL"),
                body.getLong("sourceId", 0L),
                body.getLong("sourceLineId", 0L),
                body.getLong("amount", 0L),
                body.getLong("partnerId", 0L),
                body.getLong("warehouseId", 0L),
                body.getLong("bizDate", 0L),
                body.getString("payloadJson", ""));
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlReconHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlReconHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long asOf = WebUtil.parseLongOr(ctx.query("asOf"), AsyncClockNative.wallMillis());
        long wh = WebUtil.parseLongOr(ctx.query("warehouseId"), 0L);
        ArrayList<Object> list = ErpGl.reconcile(es.companyId, asOf, wh);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            GlReconCheckDto c = (GlReconCheckDto) list.get(i);
            out = out + "{\"checkName\":" + ErpJson.str(c.checkName)
                    + ",\"ok\":" + String.valueOf(c.ok)
                    + ",\"expectedAmount\":" + ErpJson.num(c.expectedAmount)
                    + ",\"actualAmount\":" + ErpJson.num(c.actualAmount)
                    + ",\"diffAmount\":" + ErpJson.num(c.diffAmount)
                    + ",\"detail\":" + ErpJson.str(c.detail) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

class ErpGlApproveHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlApproveHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PostResultDto r = ErpGl.approveJournal(es.companyId, es.userId, id);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlPostApprovedHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlPostApprovedHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PostResultDto r = ErpGl.postApprovedJournal(es.companyId, es.userId, id);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlRejectHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlRejectHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String reason = "";
        if (body != null) {
            reason = body.getString("reason", "");
        }
        PostResultDto r = ErpGl.rejectJournal(es.companyId, es.userId, id, reason);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlReverseHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlReverseHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String reason = "";
        String key = ctx.getHeader("Idempotency-Key");
        if (body != null) {
            reason = body.getString("reason", "");
            if (key == null || key.length() == 0) {
                key = body.getString("idempotencyKey", "");
            }
        }
        PostResultDto r = ErpGl.reverseJournal(es.companyId, es.userId, id, reason, key);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlClosePeriodHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlClosePeriodHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PostResultDto r = ErpGl.closePeriod(es.companyId, es.userId, id);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlReopenPeriodHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlReopenPeriodHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String reason = "";
        if (body != null) {
            reason = body.getString("reason", "");
        }
        PostResultDto r = ErpGl.reopenPeriod(es.companyId, es.userId, id, reason);
        ctx.jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpGlCloseBlockersHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGlCloseBlockersHandler(EventLoop loop) {
        this.loop = loop;
    }

    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        ArrayList<Object> blockers = ErpGl.closeBlockers(es.companyId, id);
        String out = "[";
        int i = 0;
        while (i < blockers.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + ErpJson.str((String) blockers.get(i));
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
            /* auth() does not write the body — without this, clients see empty 204 */
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return null;
        }
        if (ErpPlatformAuth.hasPermission(es.userId, es.companyId, perm) == 0) {
            ErpAuth.err(ctx, 403, "FORBIDDEN", "无权限: " + perm);
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

class ErpDemoResetWork implements BlockingSupplier {
    long companyId;
    String errorMsg;

    public Object get() {
        errorMsg = "";
        try {
            ErpBooks.resetDemoBook(companyId);
            return "ok";
        } catch (Exception ex) {
            String msg = "";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            if (msg.length() == 0) {
                msg = "reset failed";
            }
            errorMsg = msg;
            throw new RuntimeException(msg);
        }
    }
}

class ErpDemoResetExitTask implements LoopTask {
    public void run() {
        System.out.println("DEMO_RESET exiting for scenario reseed");
        ErpServer.exitProcess(0);
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        if (ErpPlatformAuth.hasPermission(es.userId, es.companyId, "ADMIN.DEMO.RESET") == 0) {
            ErpAuth.err(ctx, 403, "FORBIDDEN", "ADMIN.DEMO.RESET");
            return JnFuture.completed(loop, null);
        }
        ErpDemoResetWork work = new ErpDemoResetWork();
        work.companyId = es.companyId;
        JnFuture f = Async.submit(loop, work);
        JnFuture out = JnFuture.createPending(loop);
        ErpDemoResetDone cb = new ErpDemoResetDone();
        cb.ctx = ctx;
        cb.out = out;
        cb.work = work;
        cb.loop = loop;
        f.onComplete(cb);
        return out;
    }
}

class ErpDemoResetDone implements JnFutureCallback {
    WebContext ctx;
    JnFuture out;
    ErpDemoResetWork work;
    EventLoop loop;

    public void invoke(JnFuture f) {
        if (f.getState() == JnFuture.SUCCESS) {
            ctx.jsonString("{\"ok\":true,\"bookKind\":\"DEMO\",\"reseed\":\"restart\"}");
            out.tryComplete(null);
            /* KeepAlive / process supervisor restarts; boot applyIfNeeded restores v2 scenarios. */
            if (loop != null) {
                loop.scheduleTimer(400, new ErpDemoResetExitTask());
            }
            return;
        }
        String msg = "";
        if (work != null && work.errorMsg != null && work.errorMsg.length() > 0) {
            msg = work.errorMsg;
        } else if (f.getError() != null && f.getError().getMessage() != null) {
            msg = f.getError().getMessage();
        }
        if (msg.length() == 0) {
            msg = "reset failed";
        }
        if (msg.indexOf("FORMAL_BOOK") >= 0 || msg.indexOf("DEMO_MODE_REQUIRED") >= 0) {
            ErpAuth.err(ctx, 403, "FORBIDDEN", msg);
        } else {
            ErpAuth.err(ctx, 400, "RESET_FAIL", msg);
        }
        out.tryComplete(null);
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
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
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
        int defaultTaxBps = ErpDocMoney.companyDefaultTaxBps(es.companyId);
        ctx.jsonString("{\"code\":" + ErpJson.str(row.getString(0))
                + ",\"name\":" + ErpJson.str(row.getString(1))
                + ",\"bookKind\":" + ErpJson.str(row.getString(2))
                + ",\"timezone\":" + ErpJson.str(row.getString(3))
                + ",\"currency\":" + ErpJson.str(row.getString(4))
                + ",\"amountScale\":" + ErpJson.num(row.getLong(5))
                + ",\"setupCompleted\":" + CryptoParse.longToString(row.getInt(6))
                + ",\"defaultTaxBps\":" + String.valueOf(defaultTaxBps)
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

class ErpDemoAvailableHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDemoAvailableHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        int available = 0;
        if (ErpProductConfig.demoMode != 0) {
            Object c = ErpDb.queryOne(
                    "SELECT id FROM companies WHERE code='JNAT' AND book_kind='" + BookKind.DEMO + "'",
                    new ArrayList<Object>(), new LongMapper());
            if (c != null) {
                available = 1;
            }
        }
        ctx.jsonString("{\"available\":" + (available != 0 ? "true" : "false") + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpDemoEnterHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDemoEnterHandler(EventLoop loop) {
        this.loop = loop;
    }
    public JnFuture handle(WebContext ctx) {
        try {
            if (ErpProductConfig.demoMode == 0) {
                ErpAuth.err(ctx, 403, "FORBIDDEN", "demo entry disabled");
                return JnFuture.completed(loop, null);
            }
            Object cidObj = ErpDb.queryOne(
                    "SELECT id FROM companies WHERE code='JNAT' AND book_kind='" + BookKind.DEMO + "'",
                    new ArrayList<Object>(), new LongMapper());
            if (cidObj == null) {
                ErpAuth.err(ctx, 403, "FORBIDDEN", "demo book not available");
                return JnFuture.completed(loop, null);
            }
            long companyId = ErpNum.asLong(cidObj);
            UserRow user = ErpAuth.loadUserByEmail("user1@jnative.dev");
            if (user == null || user.companyId != companyId) {
                ErpAuth.err(ctx, 403, "FORBIDDEN", "demo user not available");
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
            ctx.jsonString("{\"user\":" + ErpJson.user(dto) + ",\"csrfToken\":" + ErpJson.str(csrf)
                    + ",\"demo\":true}");
            return JnFuture.completed(loop, null);
        } catch (RuntimeException ex) {
            String msg = "demo enter failed";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            System.out.println("DEMO_ENTER_ERROR " + msg);
            ErpAuth.err(ctx, 500, "INTERNAL", msg);
            return JnFuture.completed(loop, null);
        }
    }
}

class ErpCategoriesHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCategoriesHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpMastersExtra.categoriesJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpUnitsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpUnitsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpMastersExtra.unitsJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpEmployeesHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpEmployeesHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpMastersExtra.employeesJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpDepartmentsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpDepartmentsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpMastersExtra.departmentsJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpInventoryTxnsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpInventoryTxnsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        try {
            ctx.jsonString(ErpDocsList.inventoryTxnsJson(es.companyId));
        } catch (Exception ex) {
            String msg = "库存流水加载失败";
            if (ex.getMessage() != null && ex.getMessage().length() > 0) {
                msg = msg + ": " + ex.getMessage();
            }
            System.out.println("ERR inventory/transactions " + msg);
            ErpAuth.err(ctx, 500, "INV_TXNS_FAILED", msg);
        }
        return JnFuture.completed(loop, null);
    }
}

class ErpListPrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpListPrHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpPurchaseRequests.listJson(ErpPurchaseRequests.list(es.companyId)));
        return JnFuture.completed(loop, null);
    }
}

class ErpCreatePrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpCreatePrHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String requester = body.getString("requester", body.getString("requesterName", "Buyer"));
        long supplierId = body.getLong("supplierId", 0L);
        String notes = body.getString("notes", "");
        ArrayList<Object> lines = new ArrayList<Object>();
        JsonValue lv = body.get("lines");
        if (lv != null && lv.kind == JsonValue.ARRAY_KIND) {
            JsonArray arr = lv.asArray();
            int i = 0;
            while (i < arr.size()) {
                JsonObject o = arr.get(i).asObject();
                PrLineInput in = new PrLineInput();
                in.itemId = o.getLong("itemId", 0L);
                in.quantity = o.getInt("quantity", 0);
                in.unitPriceCents = o.getLong("unitPriceCents", 0L);
                lines.add(in);
                i = i + 1;
            }
        }
        PurchaseRequestDto pr = ErpPurchaseRequests.createDraft(es.companyId, requester, supplierId, notes, "", lines);
        ctx.jsonString(ErpPurchaseRequests.toJson(pr));
        return JnFuture.completed(loop, null);
    }
}

class ErpGetPrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGetPrHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PurchaseRequestDto pr = ErpPurchaseRequests.get(es.companyId, id);
        if (pr == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "PR not found");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpPurchaseRequests.toJson(pr));
        return JnFuture.completed(loop, null);
    }
}

class ErpApprovePrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpApprovePrHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PurchaseRequestDto pr = ErpPurchaseRequests.approve(es.companyId, id);
        ctx.jsonString(ErpPurchaseRequests.toJson(pr));
        return JnFuture.completed(loop, null);
    }
}

class ErpListPoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpListPoHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        String status = ctx.query("status");
        if (status == null) {
            status = "";
        }
        String docNo = ctx.query("docNo");
        if (docNo == null) {
            docNo = "";
        }
        String q = ctx.query("q");
        if (q == null) {
            q = "";
        }
        long supplierId = ErpNum.parseLong(ctx.query("supplierId"));
        ctx.jsonString(ErpPurchasing.poListJson(
                ErpPurchasing.listFiltered(es.companyId, status, docNo, supplierId, q)));
        return JnFuture.completed(loop, null);
    }
}

class ErpListSoHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpListSoHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpSales.soListJson(ErpSales.list(es.companyId)));
        return JnFuture.completed(loop, null);
    }
}

class ErpListGrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpListGrHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpDocsList.goodsReceiptsJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpListDlHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpListDlHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpDocsList.deliveriesJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpGetDlHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGetDlHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        String json = ErpDocsList.deliveryJson(es.companyId, ErpNum.parseLong(ctx.param("id")));
        if (json == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "出库单不存在");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(json);
        return JnFuture.completed(loop, null);
    }
}

class ErpGetGrHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpGetGrHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        String json = ErpDocsList.goodsReceiptJson(es.companyId, ErpNum.parseLong(ctx.param("id")));
        if (json == null) {
            ErpAuth.err(ctx, 404, "NOT_FOUND", "入库单不存在");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(json);
        return JnFuture.completed(loop, null);
    }
}

class ErpFinancePeriodsHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinancePeriodsHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpDocsList.periodsJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceIncomingPayHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceIncomingPayHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpDocsList.incomingPaymentsJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceOutgoingPayHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceOutgoingPayHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.auth(ctx);
        if (es == null) {
            ErpAuth.err(ctx, 401, "AUTH_REQUIRED", "登录已失效，请重新登录");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString(ErpDocsList.outgoingPaymentsJson(es.companyId));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceArCreateHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceArCreateHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long customerId = body.getLong("customerId", 0L);
        long total = body.getLong("totalAmount", 0L);
        long invDate = body.getLong("invoiceDate", AsyncClockNative.wallMillis());
        long due = body.getLong("dueDate", invDate);
        long src = body.getLong("sourceDeliveryId", 0L);
        ArInvoiceDto d = ErpFinanceArAp.createArInvoice(es.companyId, customerId, total, invDate, due, src);
        if (d == null) {
            ErpAuth.err(ctx, 400, "AR_CREATE_FAIL", "create AR failed");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"id\":" + ErpJson.num(d.id) + ",\"invoiceNumber\":" + ErpJson.str(d.invoiceNumber)
                + ",\"status\":" + ErpJson.str(d.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceArPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceArPostHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PostResultDto r = ErpFinanceArAp.postArInvoice(es.companyId, es.userId, id);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceApCreateHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceApCreateHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        long supplierId = body.getLong("supplierId", 0L);
        long total = body.getLong("totalAmount", 0L);
        long invDate = body.getLong("invoiceDate", AsyncClockNative.wallMillis());
        long due = body.getLong("dueDate", invDate);
        long src = body.getLong("sourceGrId", 0L);
        ApInvoiceDto d = ErpFinanceArAp.createApInvoice(es.companyId, supplierId, total, invDate, due, src);
        if (d == null) {
            ErpAuth.err(ctx, 400, "AP_CREATE_FAIL", "create AP failed");
            return JnFuture.completed(loop, null);
        }
        ctx.jsonString("{\"id\":" + ErpJson.num(d.id) + ",\"invoiceNumber\":" + ErpJson.str(d.invoiceNumber)
                + ",\"status\":" + ErpJson.str(d.status) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceApPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceApPostHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PostResultDto r = ErpFinanceArAp.postApInvoice(es.companyId, es.userId, id);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceIncomingPayPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceIncomingPayPostHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        IncomingPaymentRequest req = ErpBodyParse.parseIncomingPayment(ctx);
        PostResultDto r = ErpFinanceArAp.postIncomingPayment(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceOutgoingPayPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceOutgoingPayPostHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        OutgoingPaymentRequest req = ErpBodyParse.parseOutgoingPayment(ctx);
        PostResultDto r = ErpFinanceArAp.postOutgoingPayment(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementReceiptCreateHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementReceiptCreateHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        IncomingPaymentRequest req = ErpBodyParse.parseIncomingPayment(ctx);
        PostResultDto r = ErpFinanceSettlement.createReceipt(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementReceiptPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementReceiptPostHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        String ik = ctx.getHeader("Idempotency-Key");
        if (ik == null || ik.length() == 0) {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            ik = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpFinanceSettlement.postReceipt(es.companyId, es.userId, id, ik);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementReceiptAllocHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementReceiptAllocHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.ALLOCATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        SettlementAllocateRequest req = ErpBodyParse.parseSettlementAllocate(ctx);
        PostResultDto r = ErpFinanceSettlement.allocateReceipt(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementReceiptRevHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementReceiptRevHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.REVERSE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        SettlementReverseRequest req = ErpBodyParse.parseSettlementReverse(ctx);
        PostResultDto r = ErpFinanceSettlement.reverseReceiptAlloc(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementPaymentCreateHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementPaymentCreateHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.CREATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        OutgoingPaymentRequest req = ErpBodyParse.parseOutgoingPayment(ctx);
        PostResultDto r = ErpFinanceSettlement.createPayment(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementPaymentPostHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementPaymentPostHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.POST");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        String ik = ctx.getHeader("Idempotency-Key");
        if (ik == null || ik.length() == 0) {
            JsonObject body = Json.parse(ctx.bodyString()).asObject();
            ik = body.getString("idempotencyKey", "");
        }
        PostResultDto r = ErpFinanceSettlement.postPayment(es.companyId, es.userId, id, ik);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementPaymentAllocHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementPaymentAllocHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.ALLOCATE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        SettlementAllocateRequest req = ErpBodyParse.parseSettlementAllocate(ctx);
        PostResultDto r = ErpFinanceSettlement.allocatePayment(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementPaymentRevHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementPaymentRevHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.REVERSE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        SettlementReverseRequest req = ErpBodyParse.parseSettlementReverse(ctx);
        PostResultDto r = ErpFinanceSettlement.reversePaymentAlloc(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementReceiptVoidHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementReceiptVoidHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.VOID");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PaymentVoidRequest req = ErpBodyParse.parsePaymentVoid(ctx, id);
        PostResultDto r = ErpFinanceSettlement.voidReceipt(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementPaymentVoidHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementPaymentVoidHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.VOID");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        PaymentVoidRequest req = ErpBodyParse.parsePaymentVoid(ctx, id);
        PostResultDto r = ErpFinanceSettlement.voidPayment(es.companyId, es.userId, req);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpFinanceArCancelProvHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpFinanceArCancelProvHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.PAYMENT.REVERSE");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long id = ErpNum.parseLong(ctx.param("id"));
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String ik = ctx.getHeader("Idempotency-Key");
        if (ik == null || ik.length() == 0) {
            ik = body.getString("idempotencyKey", "");
        }
        String reason = body.getString("reason", "");
        PostResultDto r = ErpFinanceArAp.cancelProvisionalAr(es.companyId, es.userId, id, reason, ik);
        ctx.status(200).jsonString(ErpJson.postResult(r));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementPartyHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementPartyHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        long partnerId = ErpNum.parseLong(ctx.query("partnerId"));
        String side = ctx.query("side");
        if (side == null || side.length() == 0) {
            side = "AR";
        }
        ctx.jsonString(ErpFinanceSettlement.partyStatementJson(es.companyId, partnerId, side, 0L, 0L));
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementIntegrityHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementIntegrityHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        int pass = ErpFinanceSettlement.runIntegrityChecks(es.companyId);
        ctx.jsonString("{\"ok\":" + String.valueOf(pass) + "}");
        return JnFuture.completed(loop, null);
    }
}

class ErpSettlementCashHandler implements AsyncWebHandler {
    EventLoop loop;
    ErpSettlementCashHandler(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        ErpSession es = ErpRequire.authPerm(ctx, "FINANCE.JOURNAL.READ");
        if (es == null) {
            return JnFuture.completed(loop, null);
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(es.companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, bank_account_id, movement_type, amount, balance_after, biz_date, source_type, source_id FROM bank_cash_movements WHERE company_id=? ORDER BY id DESC LIMIT 200",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"bankAccountId\":" + ErpJson.num(row.getLong(1))
                    + ",\"movementType\":" + ErpJson.str(row.getString(2))
                    + ",\"amount\":" + ErpJson.num(row.getLong(3))
                    + ",\"balanceAfter\":" + ErpJson.num(row.getLong(4))
                    + ",\"bizDate\":" + ErpJson.num(row.getLong(5))
                    + ",\"sourceType\":" + ErpJson.str(row.getString(6))
                    + ",\"sourceId\":" + ErpJson.num(row.getLong(7)) + "}";
            i = i + 1;
        }
        out = out + "]";
        ctx.jsonString(out);
        return JnFuture.completed(loop, null);
    }
}

