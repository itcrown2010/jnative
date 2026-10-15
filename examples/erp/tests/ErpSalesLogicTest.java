/**
 * Sales acceptance: stock 20; A reserve 12; B cannot take A's reserve;
 * A deliver 5 then 7; idempotent; return 2; AR provisional totals.
 * @author 张晓达
 */
class ErpSalesLogicTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long itemId = 0L;
    static long customerId = 0L;
    static String companyCode = "S";

    static int runAll() {
        companyCode = "S" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        if (seed() == 0) {
            System.out.println("FAIL seed");
            return 0;
        }
        int pass = scenario();
        if (pass != 0) {
            System.out.println("PASS sales");
        } else {
            System.out.println("FAIL sales");
        }
        return pass;
    }

    static int seed() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Sales Test Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));
        ArrayList<Object> wh = new ArrayList<Object>();
        wh.add(CryptoParse.longToString(companyId));
        wh.add("WH1");
        wh.add("Main");
        wh.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO warehouses(company_id, code, name, created_at) VALUES (?,?,?,?)", wh);
        ArrayList<Object> it = new ArrayList<Object>();
        it.add(CryptoParse.longToString(companyId));
        it.add("SKU-S");
        it.add("Sales Widget");
        it.add("500");
        it.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES (?,?,?,?,?)", it);
        ArrayList<Object> cu = new ArrayList<Object>();
        cu.add(CryptoParse.longToString(companyId));
        cu.add("CUS1");
        cu.add("Customer");
        cu.add(PartnerKind.CUSTOMER);
        cu.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", cu);
        ErpFinanceCore.ensureSeed(companyId);
        /* Sequences created lazily by nextDocNoConn; ensureSeed may also seed JE/AR. */
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH1");
        warehouseId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper()));
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("SKU-S");
        itemId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper()));
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add("CUS1");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cp, new LongMapper()));
        /* Opening stock 20 */
        PostResultDto adj = ErpInventory.adjust(companyId, 0L, warehouseId, itemId, 20, "opening-test",
                "open-" + companyCode);
        if (adj == null || adj.ok == 0) {
            System.out.println("FAIL opening stock");
            if (adj != null && adj.message != null) {
                System.out.println(adj.message);
            }
            return 0;
        }
        ErpDb.reconnect();
        if (companyId <= 0L || warehouseId <= 0L || itemId <= 0L || customerId <= 0L) {
            System.out.println("FAIL fixture ids");
            return 0;
        }
        return 1;
    }

    static int onHand() {
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

    static int reserved() {
        return ErpInventory.reservedQty(companyId, warehouseId, itemId);
    }

    static int available() {
        int a = onHand() - reserved();
        if (a < 0) {
            return 0;
        }
        return a;
    }

    static long arOpenSum() {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices WHERE company_id=? AND status='PROVISIONAL'",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static int scenario() {
        ErpDb.reconnect();
        System.out.println("FIXTURE company=" + CryptoParse.longToString(companyId)
                + " wh=" + CryptoParse.longToString(warehouseId)
                + " item=" + CryptoParse.longToString(itemId)
                + " cust=" + CryptoParse.longToString(customerId));
        System.out.println("STEP stock check");
        ErpDb.reconnect();
        int oh = 0;
        int av = 0;
        try {
            oh = onHand();
            System.out.println("STEP onHand=" + String.valueOf(oh));
            av = available();
            System.out.println("STEP available=" + String.valueOf(av));
        } catch (Exception ex) {
            System.out.println("FAIL stock query ex");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            } else {
                System.out.println("EX_EMPTY stock");
            }
            return 0;
        }
        if (oh != 20 || av != 20) {
            System.out.println("FAIL stock20 onHand=" + String.valueOf(oh) + " avail=" + String.valueOf(av));
            return 0;
        }
        System.out.println("STEP stock ok");
        ArrayList<Object> aLines = new ArrayList<Object>();
        SoLineInput al = new SoLineInput();
        al.itemId = itemId;
        al.quantity = 12;
        al.unitPriceCents = 1000L;
        aLines.add(al);
        SalesOrderDto soA = null;
        try {
            System.out.println("STEP SO_A create");
            soA = ErpSales.createDraftFull(companyId, customerId, warehouseId, 0L, 0L, "A", "CNY", aLines);
        } catch (Exception ex) {
            System.out.println("FAIL SO_A create ex");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            } else {
                System.out.println("EX_EMPTY create");
            }
            return 0;
        }
        if (soA == null) {
            System.out.println("FAIL SO_A create");
            return 0;
        }
        try {
            System.out.println("STEP SO_A approve id=" + CryptoParse.longToString(soA.id));
            soA = ErpSales.approve(companyId, soA.id);
        } catch (Exception ex) {
            System.out.println("FAIL SO_A approve ex");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            } else {
                System.out.println("EX_EMPTY approve");
            }
            return 0;
        }
        if (soA == null || soA.lines == null || soA.lines.size() == 0) {
            System.out.println("FAIL SO_A approve");
            return 0;
        }
        OrderLineDto lineA = (OrderLineDto) soA.lines.get(0);
        if (onHand() != 20 || reserved() != 12 || available() != 8) {
            System.out.println("FAIL after_A_reserve oh=" + String.valueOf(onHand()) + " rsv="
                    + String.valueOf(reserved()) + " av=" + String.valueOf(available()));
            return 0;
        }
        if (lineA.reservedQty != 12) {
            System.out.println("FAIL A_line_reserved=" + String.valueOf(lineA.reservedQty));
            return 0;
        }
        ArrayList<Object> bLines = new ArrayList<Object>();
        SoLineInput bl = new SoLineInput();
        bl.itemId = itemId;
        bl.quantity = 10;
        bl.unitPriceCents = 1000L;
        bLines.add(bl);
        SalesOrderDto soB = ErpSales.createDraftFull(companyId, customerId, warehouseId, 0L, 0L, "B", "CNY", bLines);
        soB = ErpSales.approve(companyId, soB.id);
        OrderLineDto lineB = (OrderLineDto) soB.lines.get(0);
        /* B can only reserve available 8 — not fabricate 10, not steal A's 12 */
        if (lineB.reservedQty != 8 || lineB.shortfallQty != 2) {
            System.out.println("FAIL B_reserve rsv=" + String.valueOf(lineB.reservedQty) + " short="
                    + String.valueOf(lineB.shortfallQty));
            return 0;
        }
        if (reserved() != 20 || available() != 0 || onHand() != 20) {
            System.out.println("FAIL after_B oh=" + String.valueOf(onHand()) + " rsv=" + String.valueOf(reserved())
                    + " av=" + String.valueOf(available()));
            return 0;
        }
        System.out.println("OK reserve_A12_B8");

        PostDeliveryRequest d1 = new PostDeliveryRequest();
        d1.salesOrderId = soA.id;
        d1.warehouseId = warehouseId;
        d1.idempotencyKey = "sa-dl5-" + companyCode;
        d1.lines = new ArrayList<Object>();
        PostLineRequest p1 = new PostLineRequest();
        p1.orderLineId = lineA.id;
        p1.quantity = 5;
        d1.lines.add(p1);
        PostResultDto r1 = ErpPosting.postDelivery(companyId, d1);
        if (r1 == null || r1.ok == 0) {
            System.out.println("FAIL dl5");
            if (r1 != null && r1.message != null) {
                System.out.println(r1.message);
            }
            return 0;
        }
        soA = ErpSales.get(companyId, soA.id);
        lineA = (OrderLineDto) soA.lines.get(0);
        if (onHand() != 15 || lineA.reservedQty != 7 || available() != 0) {
            System.out.println("FAIL after_dl5 oh=" + String.valueOf(onHand()) + " A_rsv="
                    + String.valueOf(lineA.reservedQty) + " av=" + String.valueOf(available()));
            return 0;
        }
        if (arOpenSum() != 5000L) {
            System.out.println("FAIL ar_after_dl5=" + CryptoParse.longToString(arOpenSum()));
            return 0;
        }
        /* idempotent replay */
        if (ErpPosting.postDelivery(companyId, d1).ok == 0) {
            System.out.println("FAIL dl5_idempotent");
            return 0;
        }
        if (onHand() != 15 || arOpenSum() != 5000L) {
            System.out.println("FAIL dl5_idempotent_side_effect");
            return 0;
        }
        System.out.println("OK dl5");

        PostDeliveryRequest d2 = new PostDeliveryRequest();
        d2.salesOrderId = soA.id;
        d2.warehouseId = warehouseId;
        d2.idempotencyKey = "sa-dl7-" + companyCode;
        d2.lines = new ArrayList<Object>();
        PostLineRequest p2 = new PostLineRequest();
        p2.orderLineId = lineA.id;
        p2.quantity = 7;
        d2.lines.add(p2);
        if (ErpPosting.postDelivery(companyId, d2).ok == 0) {
            System.out.println("FAIL dl7");
            return 0;
        }
        soA = ErpSales.get(companyId, soA.id);
        lineA = (OrderLineDto) soA.lines.get(0);
        if (onHand() != 8 || lineA.reservedQty != 0 || lineA.fulfilledQty != 12) {
            System.out.println("FAIL after_dl7 oh=" + String.valueOf(onHand()) + " A_rsv="
                    + String.valueOf(lineA.reservedQty) + " del=" + String.valueOf(lineA.fulfilledQty));
            return 0;
        }
        if (soA.fulfillmentStatus.equals(FulfillmentStatus.FULFILLED) == false) {
            System.out.println("FAIL fulfillment=" + soA.fulfillmentStatus);
            return 0;
        }
        if (arOpenSum() != 12000L) {
            System.out.println("FAIL ar_total=" + CryptoParse.longToString(arOpenSum()));
            return 0;
        }
        System.out.println("OK dl7_fulfilled");

        /* Return 2 from first delivery — find delivery line */
        ArrayList<Object> gp = new ArrayList<Object>();
        gp.add(CryptoParse.longToString(companyId));
        gp.add(CryptoParse.longToString(soA.id));
        Object dlIdObj = ErpDb.queryOne(
                "SELECT id FROM deliveries WHERE company_id=? AND sales_order_id=? ORDER BY id LIMIT 1", gp,
                new LongMapper());
        long dlId = ErpNum.asLong(dlIdObj);
        ArrayList<Object> glp = new ArrayList<Object>();
        glp.add(CryptoParse.longToString(dlId));
        Object dllObj = ErpDb.queryOne("SELECT id FROM delivery_lines WHERE delivery_id=? ORDER BY id LIMIT 1", glp,
                new LongMapper());
        long dllId = ErpNum.asLong(dllObj);
        ArrayList<Object> srLines = new ArrayList<Object>();
        SalesReturnLineInput srl = new SalesReturnLineInput();
        srl.deliveryLineId = dllId;
        srl.quantity = 2;
        srLines.add(srl);
        PostResultDto srr = ErpSalesReturn.post(companyId, soA.id, dlId, warehouseId, 1, "ret2", srLines,
                "sa-sr2-" + companyCode);
        if (srr == null || srr.ok == 0) {
            System.out.println("FAIL return2");
            if (srr != null && srr.message != null) {
                System.out.println(srr.message);
            }
            return 0;
        }
        soA = ErpSales.get(companyId, soA.id);
        lineA = (OrderLineDto) soA.lines.get(0);
        if (onHand() != 10) {
            System.out.println("FAIL after_return oh=" + String.valueOf(onHand()));
            return 0;
        }
        if (lineA.openQty != 0) {
            System.out.println("FAIL return_reopened_open=" + String.valueOf(lineA.openQty));
            return 0;
        }
        if (arOpenSum() != 10000L) {
            System.out.println("FAIL ar_after_return=" + CryptoParse.longToString(arOpenSum()));
            return 0;
        }
        System.out.println("OK return2");
        return 1;
    }
}
