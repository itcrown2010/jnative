/**
 * Headless acceptance: PO 100 → GR 60+40 → stock 100 → SO 30 → DL 10+20 → stock 70;
 * duplicate idempotency replay; over-receive / over-delivery rejected.
 * @author 张晓达
 */
class ErpLogicTest {
    static long companyId = 1L;
    static long warehouseId = 1L;
    static long itemId = 1L;
    static long supplierId = 1L;
    static long customerId = 1L;

    static String companyCode = "TST";

    static int runAll() {
        int pass = 1;
        companyCode = "T" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        seedMinimal();
        if (companyId == 0L || itemId == 0L || warehouseId == 0L) {
            System.out.println("FAIL seed");
            return 0;
        }
        pass = runScenario(pass);
        if (pass != 0) {
            System.out.println("PASS");
        } else {
            System.out.println("FAIL");
        }
        return pass;
    }

    static void seedMinimal() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Test Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));

        ArrayList<Object> wh = new ArrayList<Object>();
        wh.add(CryptoParse.longToString(companyId));
        wh.add("WH1");
        wh.add("Main WH");
        wh.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO warehouses(company_id, code, name, created_at) VALUES (?,?,?,?)", wh);

        ArrayList<Object> it = new ArrayList<Object>();
        it.add(CryptoParse.longToString(companyId));
        it.add("SKU-1");
        it.add("Widget");
        it.add("100");
        it.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES (?,?,?,?,?)", it);

        ArrayList<Object> sup = new ArrayList<Object>();
        sup.add(CryptoParse.longToString(companyId));
        sup.add("SUP1");
        sup.add("Supplier");
        sup.add(PartnerKind.SUPPLIER);
        sup.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", sup);

        ArrayList<Object> cust = new ArrayList<Object>();
        cust.add(CryptoParse.longToString(companyId));
        cust.add("CUS1");
        cust.add("Customer");
        cust.add(PartnerKind.CUSTOMER);
        cust.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", cust);

        String[] seq = new String[4];
        seq[0] = "PO";
        seq[1] = "SO";
        seq[2] = "GR";
        seq[3] = "DL";
        int si = 0;
        while (si < 4) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add(seq[si]);
            row.add("1");
            ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", row);
            si = si + 1;
        }
        ErpFinanceCore.ensureSeed(companyId);
        resolveFixtureIds();
    }

    static void resolveFixtureIds() {
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH1");
        warehouseId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper()));
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("SKU-1");
        itemId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper()));
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add("SUP1");
        supplierId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", sp, new LongMapper()));
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add("CUS1");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cp, new LongMapper()));
    }

    static int okPost(PostResultDto r) {
        if (r == null) {
            return 0;
        }
        return r.ok;
    }

    static void failPost(String label, PostResultDto r) {
        System.out.println("FAIL " + label);
        if (r == null) {
            System.out.println("null result");
            return;
        }
        if (r.code != null) {
            System.out.println(r.code);
        }
        if (r.message != null) {
            System.out.println(r.message);
        }
    }

    static int runScenario(int pass) {
        ErpDb.reconnect();
        ArrayList<Object> poLines = new ArrayList<Object>();
        PoLineInput pl = new PoLineInput();
        pl.itemId = itemId;
        pl.quantity = 100;
        pl.unitPriceCents = 100L;
        poLines.add(pl);
        PurchaseOrderDto po = null;
        try {
            po = ErpPurchasing.createDraft(companyId, supplierId, poLines);
        } catch (Exception ex) {
            System.out.println("FAIL po_create");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (po == null) {
            System.out.println("FAIL po_create null");
            return 0;
        }
        try {
            po = ErpPurchasing.approve(companyId, po.id);
        } catch (Exception ex) {
            System.out.println("FAIL po_approve");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (po == null || po.lines == null || po.lines.size() == 0) {
            System.out.println("FAIL po_approve lines");
            return 0;
        }
        OrderLineDto poLine = (OrderLineDto) po.lines.get(0);

        PostGoodsReceiptRequest gr1 = new PostGoodsReceiptRequest();
        gr1.purchaseOrderId = po.id;
        gr1.warehouseId = warehouseId;
        gr1.idempotencyKey = "gr-1-" + companyCode;
        gr1.lines = new ArrayList<Object>();
        PostLineRequest gl1 = new PostLineRequest();
        gl1.orderLineId = poLine.id;
        gl1.quantity = 60;
        gr1.lines.add(gl1);
        if (okPost(ErpPosting.postGoodsReceipt(companyId, gr1)) == 0) {
            System.out.println("FAIL gr1");
            pass = 0;
        }

        PostGoodsReceiptRequest gr2 = new PostGoodsReceiptRequest();
        gr2.purchaseOrderId = po.id;
        gr2.warehouseId = warehouseId;
        gr2.idempotencyKey = "gr-2-" + companyCode;
        gr2.lines = new ArrayList<Object>();
        PostLineRequest gl2 = new PostLineRequest();
        gl2.orderLineId = poLine.id;
        gl2.quantity = 40;
        gr2.lines.add(gl2);
        if (okPost(ErpPosting.postGoodsReceipt(companyId, gr2)) == 0) {
            System.out.println("FAIL gr2");
            pass = 0;
        }

        int stockAfterGr = stockQty();
        if (stockAfterGr != 100) {
            System.out.println("FAIL stock_after_gr=" + String.valueOf(stockAfterGr));
            pass = 0;
        }

        if (okPost(ErpPosting.postGoodsReceipt(companyId, gr1)) == 0) {
            System.out.println("FAIL gr1_idempotent_replay");
            pass = 0;
        }
        if (stockQty() != 100) {
            System.out.println("FAIL stock_after_idempotent");
            pass = 0;
        }

        PostGoodsReceiptRequest overGr = new PostGoodsReceiptRequest();
        overGr.purchaseOrderId = po.id;
        overGr.warehouseId = warehouseId;
        overGr.idempotencyKey = "gr-over-" + companyCode;
        overGr.lines = new ArrayList<Object>();
        PostLineRequest gol = new PostLineRequest();
        gol.orderLineId = poLine.id;
        gol.quantity = 1;
        overGr.lines.add(gol);
        if (okPost(ErpPosting.postGoodsReceipt(companyId, overGr)) != 0) {
            System.out.println("FAIL over_gr_should_reject");
            pass = 0;
        }

        ArrayList<Object> soLines = new ArrayList<Object>();
        SoLineInput sl = new SoLineInput();
        sl.itemId = itemId;
        sl.quantity = 30;
        sl.unitPriceCents = 200L;
        soLines.add(sl);
        SalesOrderDto so = null;
        try {
            so = ErpSales.createDraft(companyId, customerId, soLines);
        } catch (Exception ex) {
            System.out.println("FAIL so_create");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (so == null) {
            System.out.println("FAIL so_create null");
            return 0;
        }
        try {
            so = ErpSales.approve(companyId, so.id);
        } catch (Exception ex) {
            System.out.println("FAIL so_approve");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (so == null || so.lines == null || so.lines.size() == 0) {
            System.out.println("FAIL so_approve lines");
            return 0;
        }
        OrderLineDto soLine = (OrderLineDto) so.lines.get(0);

        PostDeliveryRequest dl1 = new PostDeliveryRequest();
        dl1.salesOrderId = so.id;
        dl1.warehouseId = warehouseId;
        dl1.idempotencyKey = "dl-1-" + companyCode;
        dl1.lines = new ArrayList<Object>();
        PostLineRequest d1 = new PostLineRequest();
        d1.orderLineId = soLine.id;
        d1.quantity = 10;
        dl1.lines.add(d1);
        PostResultDto dl1r = ErpPosting.postDelivery(companyId, dl1);
        if (okPost(dl1r) == 0) {
            failPost("dl1", dl1r);
            pass = 0;
        }

        PostDeliveryRequest dl2 = new PostDeliveryRequest();
        dl2.salesOrderId = so.id;
        dl2.warehouseId = warehouseId;
        dl2.idempotencyKey = "dl-2-" + companyCode;
        dl2.lines = new ArrayList<Object>();
        PostLineRequest d2 = new PostLineRequest();
        d2.orderLineId = soLine.id;
        d2.quantity = 20;
        dl2.lines.add(d2);
        if (okPost(ErpPosting.postDelivery(companyId, dl2)) == 0) {
            System.out.println("FAIL dl2");
            pass = 0;
        }

        int stockFinal = stockQty();
        if (stockFinal != 70) {
            System.out.println("FAIL stock_final=" + String.valueOf(stockFinal));
            pass = 0;
        }

        PostDeliveryRequest overDl = new PostDeliveryRequest();
        overDl.salesOrderId = so.id;
        overDl.warehouseId = warehouseId;
        overDl.idempotencyKey = "dl-over-" + companyCode;
        overDl.lines = new ArrayList<Object>();
        PostLineRequest dol = new PostLineRequest();
        dol.orderLineId = soLine.id;
        dol.quantity = 1;
        overDl.lines.add(dol);
        if (okPost(ErpPosting.postDelivery(companyId, overDl)) != 0) {
            System.out.println("FAIL over_dl_should_reject");
            pass = 0;
        }

        return pass;
    }

    static int stockQty() {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }
}
