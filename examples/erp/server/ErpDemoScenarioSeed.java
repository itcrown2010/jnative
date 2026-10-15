/**
 * Versioned demo trading scenarios using real posting services.
 * SEED_EPOCH = 2025-07-01 UTC (1751328000000). Document dates are offsets from this.
 * Idempotent via demo_seed_log seed_key + DEMO_SEED_VERSION gate.
 * Practice SKU FG-PRACTICE-A / WH-PRACTICE stay at qty 0.
 * @author 张晓达
 */
class ErpDemoScenarioSeed {
    /** 2025-07-01T00:00:00Z */
    static long SEED_EPOCH = 1751328000000L;
    static long DAY = 86400000L;

    static void applyIfNeeded(long companyId) {
        if (ErpBooks.isDemoBook(companyId) == 0) {
            return;
        }
        int cur = ErpBooks.demoSeedVersion(companyId);
        if (cur >= ErpBooks.DEMO_SEED_VERSION) {
            return;
        }
        System.out.println("DEMO_SCENARIO_SEED start companyId=" + CryptoParse.longToString(companyId)
                + " from=" + CryptoParse.longToString(cur) + " to="
                + CryptoParse.longToString(ErpBooks.DEMO_SEED_VERSION));
        try {
            runScenarios(companyId);
            ErpBooks.recordDemoSeed(companyId, ErpBooks.DEMO_SEED_VERSION);
            log(companyId, "v2-complete", "OK", "scenario seed applied");
            System.out.println("DEMO_SCENARIO_SEED ok");
        } catch (Exception ex) {
            String msg = "";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            log(companyId, "v2-failed", "FAILED", msg);
            System.out.println("DEMO_SCENARIO_SEED failed: " + msg);
            throw new TransactionException("DEMO_SCENARIO_SEED:" + msg);
        }
    }

    static void forceReseed(long companyId) {
        ErpBooks.requireDemoBookOrThrow(companyId, "RESET_DEMO");
        System.out.println("DEMO_FORCE_RESEED companyId=" + CryptoParse.longToString(companyId));
        try {
            /* Masters already re-seeded by ErpBooks.resetDemoBook; only run doc scenarios. */
            runScenarioDocuments(companyId);
            ErpBooks.recordDemoSeed(companyId, ErpBooks.DEMO_SEED_VERSION);
            log(companyId, "v2-reset", "OK", "scenario reseed after reset");
            System.out.println("DEMO_FORCE_RESEED ok");
        } catch (Exception ex) {
            String msg = "";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            if (msg.length() == 0) {
                msg = "unknown";
            }
            System.out.println("DEMO_FORCE_RESEED_EX " + msg);
            log(companyId, "v2-reset", "FAILED", msg);
            throw new TransactionException("FORCE_RESEED:" + msg);
        }
    }

    static void log(long companyId, String key, String status, String detail) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        Object existing = null;
        try {
            existing = ErpDb.queryOne("SELECT id FROM demo_seed_log WHERE company_id=? AND seed_key=?", p,
                    new LongMapper());
        } catch (Exception ignored) {
            return;
        }
        if (existing != null) {
            ErpDb.exec(
                    "UPDATE demo_seed_log SET status='" + status + "', detail='" + detail.replace("'", " ")
                            + "', applied_at=" + CryptoParse.longToString(now)
                            + ", seed_version=" + CryptoParse.longToString(ErpBooks.DEMO_SEED_VERSION)
                            + " WHERE company_id=" + CryptoParse.longToString(companyId)
                            + " AND seed_key='" + key + "'",
                    new ArrayList<Object>());
        } else {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add(String.valueOf(ErpBooks.DEMO_SEED_VERSION));
            row.add(key);
            row.add(status);
            row.add(detail);
            row.add(CryptoParse.longToString(now));
            try {
                ErpDb.exec(
                        "INSERT INTO demo_seed_log(company_id, seed_version, seed_key, status, detail, applied_at) VALUES (?,?,?,?,?,?)",
                        row);
            } catch (Exception ignored) {
            }
        }
    }

    static int already(long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        try {
            Object v = ErpDb.queryOne(
                    "SELECT status FROM demo_seed_log WHERE company_id=? AND seed_key=?", p, new StringMapper());
            if (v != null && ((String) v).equals("OK")) {
                return 1;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    static void runScenarios(long companyId) {
        long now = AsyncClockNative.wallMillis();
        ErpSeed.seedSequences(companyId);
        ErpFinanceCore.ensureSeed(companyId);
        ErpSeed.seedDemoBusiness(companyId, now);
        ErpMastersExtra.ensureSeed(companyId, now);
        ensureTradingMasters(companyId, now);
        runScenarioDocuments(companyId);
    }

    /** Opening + purchase/sales chains (masters must already exist). */
    static void runScenarioDocuments(long companyId) {
        long adminId = adminUserId(companyId);
        long whMain = warehouseId(companyId, "WH1");
        if (whMain == 0L) {
            whMain = warehouseId(companyId, "WH-MAIN");
        }
        if (whMain == 0L) {
            throw new TransactionException("no main warehouse");
        }
        long supplierId = firstPartner(companyId, PartnerKind.SUPPLIER);
        long customerId = firstPartner(companyId, PartnerKind.CUSTOMER);
        if (supplierId == 0L || customerId == 0L) {
            throw new TransactionException("missing trading partners");
        }
        long itemA = itemId(companyId, "SKU-1");
        if (itemA == 0L) {
            itemA = firstItem(companyId);
        }
        long itemB = itemId(companyId, "SKU-2");
        if (itemB == 0L) {
            itemB = itemA;
        }
        if (itemA == 0L) {
            throw new TransactionException("missing seed items");
        }
        long bankId = ErpDocsList.firstBankAccountId(companyId);
        if (bankId == 0L) {
            throw new TransactionException("no bank account");
        }
        log(companyId, "phase", "RUN", "ids wh=" + CryptoParse.longToString(whMain)
                + " sup=" + CryptoParse.longToString(supplierId)
                + " cust=" + CryptoParse.longToString(customerId)
                + " bank=" + CryptoParse.longToString(bankId));

        if (already(companyId, "opening-stock") == 0) {
            try {
                log(companyId, "phase", "RUN", "opening");
                seedOpening(companyId, adminId, whMain, itemA, itemB);
                log(companyId, "opening-stock", "OK", "opening stock posted");
            } catch (Exception openEx) {
                String om = "";
                if (openEx.getMessage() != null) {
                    om = openEx.getMessage();
                }
                if (om.length() == 0) {
                    om = "opening failed";
                }
                log(companyId, "opening-stock", "SKIP", om);
                System.out.println("DEMO_SCENARIO opening skipped: " + om);
            }
        }
        if (already(companyId, "purchase-chain") == 0) {
            try {
                log(companyId, "phase", "RUN", "purchase");
                seedPurchaseChain(companyId, adminId, supplierId, whMain, itemA, itemB, bankId);
                log(companyId, "purchase-chain", "OK", "PR/PO/GR/AP/payment");
            } catch (Exception pex) {
                String pm = "";
                if (pex.getMessage() != null) {
                    pm = pex.getMessage();
                }
                if (pm.length() == 0) {
                    pm = "purchase-chain failed";
                }
                log(companyId, "purchase-chain", "FAILED", pm);
                throw new TransactionException("purchase-chain:" + pm);
            }
        }
        if (already(companyId, "sales-chain") == 0) {
            try {
                log(companyId, "phase", "RUN", "sales");
                seedSalesChain(companyId, adminId, customerId, whMain, itemA, bankId);
                log(companyId, "sales-chain", "OK", "SO/DL/AR/payment");
            } catch (Exception sex) {
                String sm = "";
                if (sex.getMessage() != null) {
                    sm = sex.getMessage();
                }
                if (sm.length() == 0) {
                    sm = "sales-chain failed";
                }
                log(companyId, "sales-chain", "FAILED", sm);
                throw new TransactionException("sales-chain:" + sm);
            }
        }
        assertPracticeZero(companyId);
        log(companyId, "phase", "OK", "complete");
    }

    static void ensureTradingMasters(long companyId, long now) {
        /* Top up to at least 10 customers / 8 suppliers / 30 items / 3 warehouses if sparse. */
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add(PartnerKind.CUSTOMER);
        long custN = ErpNum.asLong(ErpDb.queryOne(
                "SELECT COUNT(*) FROM business_partners WHERE company_id=? AND kind=?", cp, new LongMapper()));
        int c = (int) custN + 1;
        while (c <= 10) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("C" + String.valueOf(c));
            row.add("Customer " + String.valueOf(c));
            row.add(PartnerKind.CUSTOMER);
            row.add(CryptoParse.longToString(now));
            ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                    row);
            c = c + 1;
        }
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add(PartnerKind.SUPPLIER);
        long supN = ErpNum.asLong(ErpDb.queryOne(
                "SELECT COUNT(*) FROM business_partners WHERE company_id=? AND kind=?", sp, new LongMapper()));
        int s = (int) supN + 1;
        while (s <= 8) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("S" + String.valueOf(s));
            row.add("Supplier " + String.valueOf(s));
            row.add(PartnerKind.SUPPLIER);
            row.add(CryptoParse.longToString(now));
            ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                    row);
            s = s + 1;
        }
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        long itemN = ErpNum.asLong(ErpDb.queryOne(
                "SELECT COUNT(*) FROM items WHERE company_id=?", ip, new LongMapper()));
        int i = (int) itemN + 1;
        while (i <= 30) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("SKU-" + String.valueOf(i));
            row.add("Item " + String.valueOf(i));
            row.add(String.valueOf(1000 + i * 10));
            row.add(CryptoParse.longToString(now));
            ErpDb.exec("INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES (?,?,?,?,?)",
                    row);
            i = i + 1;
        }
    }

    static void seedOpening(long companyId, long userId, long warehouseId, long itemA, long itemB) {
        ArrayList<Object> lines = new ArrayList<Object>();
        OpeningLineInput a = new OpeningLineInput();
        a.itemId = itemA;
        a.quantity = 100;
        a.unitCostAmount = 5000L;
        lines.add(a);
        OpeningLineInput b = new OpeningLineInput();
        b.itemId = itemB;
        b.quantity = 50;
        b.unitCostAmount = 3000L;
        lines.add(b);
        ErpDb.reconnect();
        PostResultDto draft = ErpOpening.createStockDraft(companyId, userId, warehouseId, SEED_EPOCH + DAY * 5,
                "Demo opening", "seed-open-draft", lines);
        if (draft.ok == 0) {
            throw new TransactionException("opening draft:" + draft.message);
        }
        long docId = parseIdFromDoc(draft.documentJson);
        if (docId == 0L) {
            throw new TransactionException("opening draft: missing doc id");
        }
        PostResultDto posted = null;
        int attempt = 0;
        while (attempt < 3) {
            ErpDb.reconnect();
            posted = ErpOpening.postStock(companyId, userId, docId, "seed-open-post");
            if (posted != null && posted.ok != 0) {
                return;
            }
            attempt = attempt + 1;
        }
        String pm = "";
        if (posted != null && posted.message != null) {
            pm = posted.message;
        }
        throw new TransactionException("opening post:" + pm);
    }

    static void seedPurchaseChain(long companyId, long userId, long supplierId, long warehouseId, long itemA,
            long itemB, long bankId) {
        /* Draft PR */
        ArrayList<Object> prDraftLines = new ArrayList<Object>();
        PrLineInput pl0 = new PrLineInput();
        pl0.itemId = itemA;
        pl0.quantity = 20;
        pl0.unitPriceCents = 4500L;
        prDraftLines.add(pl0);
        ErpPurchaseRequests.createWithStatus(companyId, "Chen Fang", supplierId, "draft PR", "SEED-PR-DRAFT",
                DocStatus.DRAFT, prDraftLines);

        /* Submitted PR */
        ArrayList<Object> prSubLines = new ArrayList<Object>();
        PrLineInput pl1 = new PrLineInput();
        pl1.itemId = itemB;
        pl1.quantity = 15;
        pl1.unitPriceCents = 2800L;
        prSubLines.add(pl1);
        ErpPurchaseRequests.createWithStatus(companyId, "Chen Fang", supplierId, "submitted PR", "SEED-PR-SUB",
                DocStatus.SUBMITTED, prSubLines);

        /* Approved PR that feeds PO */
        ArrayList<Object> prOkLines = new ArrayList<Object>();
        PrLineInput pl2 = new PrLineInput();
        pl2.itemId = itemA;
        pl2.quantity = 30;
        pl2.unitPriceCents = 4500L;
        prOkLines.add(pl2);
        PrLineInput pl3 = new PrLineInput();
        pl3.itemId = itemB;
        pl3.quantity = 10;
        pl3.unitPriceCents = 2800L;
        prOkLines.add(pl3);
        ErpPurchaseRequests.createWithStatus(companyId, "Chen Fang", supplierId, "approved PR", "SEED-PR-OK",
                DocStatus.APPROVED, prOkLines);

        ArrayList<Object> poLines = new ArrayList<Object>();
        PoLineInput pol1 = new PoLineInput();
        pol1.itemId = itemA;
        pol1.quantity = 30;
        pol1.unitPriceCents = 4500L;
        poLines.add(pol1);
        PoLineInput pol2 = new PoLineInput();
        pol2.itemId = itemB;
        pol2.quantity = 10;
        pol2.unitPriceCents = 2800L;
        poLines.add(pol2);
        PurchaseOrderDto po = ErpPurchasing.createDraft(companyId, supplierId, poLines);
        po = ErpPurchasing.approve(companyId, po.id);
        po = ErpPurchasing.get(companyId, po.id);

        /* Split GR: 12 then 18 of itemA; 10 of itemB on second */
        OrderLineDto lineA = (OrderLineDto) po.lines.get(0);
        OrderLineDto lineB = (OrderLineDto) po.lines.get(1);

        PostGoodsReceiptRequest gr1 = new PostGoodsReceiptRequest();
        gr1.purchaseOrderId = po.id;
        gr1.warehouseId = warehouseId;
        gr1.idempotencyKey = "seed-gr-1";
        gr1.lines = new ArrayList<Object>();
        PostLineRequest gl1 = new PostLineRequest();
        gl1.orderLineId = lineA.id;
        gl1.quantity = 12;
        gr1.lines.add(gl1);
        PostResultDto gr1r = ErpPosting.postGoodsReceipt(companyId, gr1);
        if (gr1r.ok == 0) {
            throw new TransactionException("GR1:" + gr1r.message);
        }

        PostGoodsReceiptRequest gr2 = new PostGoodsReceiptRequest();
        gr2.purchaseOrderId = po.id;
        gr2.warehouseId = warehouseId;
        gr2.idempotencyKey = "seed-gr-2";
        gr2.lines = new ArrayList<Object>();
        PostLineRequest gl2a = new PostLineRequest();
        gl2a.orderLineId = lineA.id;
        gl2a.quantity = 18;
        gr2.lines.add(gl2a);
        PostLineRequest gl2b = new PostLineRequest();
        gl2b.orderLineId = lineB.id;
        gl2b.quantity = 10;
        gr2.lines.add(gl2b);
        PostResultDto gr2r = ErpPosting.postGoodsReceipt(companyId, gr2);
        if (gr2r.ok == 0) {
            throw new TransactionException("GR2:" + gr2r.message);
        }
        ErpDb.reconnect();
        long grId = latestGoodsReceiptId(companyId);
        if (grId == 0L) {
            grId = parseIdFromDoc(gr2r.documentJson);
        }
        /* GR already wrote PROVISIONAL AP per receipt (source_gr_id). Formal vendor invoice is separate (source_gr_id=0). */
        long apTotal = 30L * 4500L + 10L * 2800L;
        ApInvoiceDto ap = null;
        int apCreateAttempt = 0;
        while (apCreateAttempt < 3 && ap == null) {
            ErpDb.reconnect();
            ap = ErpFinanceArAp.createApInvoice(companyId, supplierId, apTotal,
                    SEED_EPOCH + DAY * 40, SEED_EPOCH + DAY * 70, 0L);
            apCreateAttempt = apCreateAttempt + 1;
        }
        if (ap == null) {
            throw new TransactionException("AP create failed");
        }
        PostResultDto apPost = null;
        int apPostAttempt = 0;
        while (apPostAttempt < 4) {
            ErpDb.reconnect();
            apPost = ErpFinanceArAp.postApInvoice(companyId, userId, ap.id);
            if (apPost != null && apPost.ok != 0) {
                break;
            }
            apPostAttempt = apPostAttempt + 1;
        }
        if (apPost == null || apPost.ok == 0) {
            String am = "";
            if (apPost != null && apPost.message != null) {
                am = apPost.message;
            }
            throw new TransactionException("AP post:" + am);
        }
        OutgoingPaymentRequest pay = new OutgoingPaymentRequest();
        pay.supplierId = supplierId;
        pay.paymentDate = SEED_EPOCH + DAY * 55;
        pay.amount = apTotal / 2;
        pay.bankAccountId = bankId;
        pay.idempotencyKey = "seed-op-1";
        pay.allocations = new ArrayList<Object>();
        PaymentAllocationInput alloc = new PaymentAllocationInput();
        alloc.invoiceId = ap.id;
        alloc.allocatedAmount = apTotal / 2;
        pay.allocations.add(alloc);
        PostResultDto payR = null;
        int opAttempt = 0;
        while (opAttempt < 4) {
            ErpDb.reconnect();
            payR = ErpFinanceArAp.postOutgoingPayment(companyId, userId, pay);
            if (payR != null && payR.ok != 0) {
                break;
            }
            opAttempt = opAttempt + 1;
        }
        if (payR == null || payR.ok == 0) {
            String om = "";
            if (payR != null && payR.message != null) {
                om = payR.message;
            }
            throw new TransactionException("OP:" + om);
        }
    }

    static void seedSalesChain(long companyId, long userId, long customerId, long warehouseId, long itemA,
            long bankId) {
        ArrayList<Object> soLines = new ArrayList<Object>();
        SoLineInput sl = new SoLineInput();
        sl.itemId = itemA;
        sl.quantity = 20;
        sl.unitPriceCents = 8000L;
        soLines.add(sl);
        SalesOrderDto so = ErpSales.createDraftFull(companyId, customerId, warehouseId, SEED_EPOCH + DAY * 45,
                SEED_EPOCH + DAY * 50, "demo sales", "CNY", soLines);
        so = ErpSales.approve(companyId, so.id);
        so = ErpSales.get(companyId, so.id);
        OrderLineDto line = (OrderLineDto) so.lines.get(0);

        PostDeliveryRequest d1 = new PostDeliveryRequest();
        d1.salesOrderId = so.id;
        d1.warehouseId = warehouseId;
        d1.idempotencyKey = "seed-dl-1";
        d1.lines = new ArrayList<Object>();
        PostLineRequest dl1 = new PostLineRequest();
        dl1.orderLineId = line.id;
        dl1.quantity = 8;
        d1.lines.add(dl1);
        PostResultDto r1 = ErpPosting.postDelivery(companyId, d1);
        if (r1.ok == 0) {
            throw new TransactionException("DL1:" + r1.message);
        }

        PostDeliveryRequest d2 = new PostDeliveryRequest();
        d2.salesOrderId = so.id;
        d2.warehouseId = warehouseId;
        d2.idempotencyKey = "seed-dl-2";
        d2.lines = new ArrayList<Object>();
        PostLineRequest dl2 = new PostLineRequest();
        dl2.orderLineId = line.id;
        dl2.quantity = 12;
        d2.lines.add(dl2);
        PostResultDto r2 = ErpPosting.postDelivery(companyId, d2);
        if (r2.ok == 0) {
            throw new TransactionException("DL2:" + r2.message);
        }
        ErpDb.reconnect();
        long dlId = latestDeliveryId(companyId);
        if (dlId == 0L) {
            dlId = parseIdFromDoc(r2.documentJson);
        }
        /* DL already wrote PROVISIONAL AR per delivery; formal invoice is separate (source_delivery_id=0). */
        long arTotal = 20L * 8000L;
        ArInvoiceDto ar = ErpFinanceArAp.createArInvoice(companyId, customerId, arTotal,
                SEED_EPOCH + DAY * 50, SEED_EPOCH + DAY * 80, 0L);
        if (ar == null) {
            ErpDb.reconnect();
            ar = ErpFinanceArAp.createArInvoice(companyId, customerId, arTotal,
                    SEED_EPOCH + DAY * 50, SEED_EPOCH + DAY * 80, 0L);
        }
        if (ar == null) {
            throw new TransactionException("AR create failed");
        }
        PostResultDto arPost = ErpFinanceArAp.postArInvoice(companyId, userId, ar.id);
        if (arPost.ok == 0) {
            throw new TransactionException("AR post:" + arPost.message);
        }
        IncomingPaymentRequest pay = new IncomingPaymentRequest();
        pay.customerId = customerId;
        pay.paymentDate = SEED_EPOCH + DAY * 65;
        pay.amount = arTotal;
        pay.bankAccountId = bankId;
        pay.idempotencyKey = "seed-ip-1";
        pay.allocations = new ArrayList<Object>();
        PaymentAllocationInput alloc = new PaymentAllocationInput();
        alloc.invoiceId = ar.id;
        alloc.allocatedAmount = arTotal;
        pay.allocations.add(alloc);
        PostResultDto payR = null;
        int ipAttempt = 0;
        while (ipAttempt < 4) {
            ErpDb.reconnect();
            payR = ErpFinanceArAp.postIncomingPayment(companyId, userId, pay);
            if (payR != null && payR.ok != 0) {
                break;
            }
            ipAttempt = ipAttempt + 1;
        }
        if (payR == null || payR.ok == 0) {
            String im = "";
            if (payR != null && payR.message != null) {
                im = payR.message;
            }
            throw new TransactionException("IP:" + im);
        }
    }

    static void assertPracticeZero(long companyId) {
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("FG-PRACTICE-A");
        Object item = ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper());
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH-PRACTICE");
        Object wh = ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper());
        if (item == null || wh == null) {
            return;
        }
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(companyId));
        bp.add(CryptoParse.longToString(ErpNum.asLong(wh)));
        bp.add(CryptoParse.longToString(ErpNum.asLong(item)));
        Object qty = ErpDb.queryOne(
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                bp, new LongMapper());
        if (qty != null && ErpNum.asLong(qty) != 0L) {
            throw new TransactionException("practice inventory must be 0");
        }
    }

    static long parseIdFromDoc(String json) {
        if (json == null) {
            return 0L;
        }
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            return 0L;
        }
        int i = idx + 5;
        while (i < json.length()) {
            int c = json.charAt(i);
            if (c == 32 || c == 34) {
                i = i + 1;
            } else {
                break;
            }
        }
        int j = i;
        while (j < json.length()) {
            int c = json.charAt(j);
            if (c >= 48 && c <= 57) {
                j = j + 1;
            } else {
                break;
            }
        }
        if (j <= i) {
            return 0L;
        }
        return ErpNum.parseLong(json.substring(i, j));
    }

    static long adminUserId(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add("user1@jnative.dev");
        Object v = ErpDb.queryOne("SELECT id FROM users WHERE company_id=? AND email=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long warehouseId(long companyId, String code) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object v = ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=? AND code=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long itemId(long companyId, String sku) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sku);
        Object v = ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long firstItem(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku NOT LIKE 'FG-PRACTICE%' ORDER BY id LIMIT 1", p,
                new LongMapper());
        return ErpNum.asLong(v);
    }

    static long firstPartner(long companyId, String kind) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(kind);
        Object v = ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND kind=? ORDER BY id LIMIT 1", p,
                new LongMapper());
        return ErpNum.asLong(v);
    }

    static long latestGoodsReceiptId(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT id FROM goods_receipts WHERE company_id=? ORDER BY id DESC LIMIT 1", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long latestDeliveryId(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT id FROM deliveries WHERE company_id=? ORDER BY id DESC LIMIT 1", p, new LongMapper());
        return ErpNum.asLong(v);
    }
}
