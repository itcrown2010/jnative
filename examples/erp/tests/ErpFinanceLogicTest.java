/**
 * Finance 1.2 acceptance:
 * Purchase 100 @50 = 5000; sell 30 @80 = 2400; stock 70 / value 3500; COGS 1500; GP 900.
 * AR 2400 → collect 1000+1400 → open 0.
 * AP 5000 → pay 2000+3000 → open 0.
 * Trial balance must balance; journals debit==credit.
 * @author 张晓达
 */
class ErpFinanceLogicTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long itemId = 0L;
    static long supplierId = 0L;
    static long customerId = 0L;
    static long bankAccountId = 0L;
    static String companyCode = "F";

    static int runAll() {
        companyCode = "F" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        seedMinimal();
        if (companyId == 0L || itemId == 0L || warehouseId == 0L || bankAccountId == 0L) {
            System.out.println("FAIL finance seed");
            return 0;
        }
        int pass = 1;
        pass = runCoreScenario(pass);
        pass = runExceptionChecks(pass);
        if (pass != 0) {
            System.out.println("PASS finance");
        } else {
            System.out.println("FAIL finance");
        }
        return pass;
    }

    static void seedMinimal() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Finance Co");
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
        it.add("80");
        it.add(CryptoParse.longToString(now));
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES (?,?,?,?,50,?)",
                it);

        ArrayList<Object> sup = new ArrayList<Object>();
        sup.add(CryptoParse.longToString(companyId));
        sup.add("SUP1");
        sup.add("Supplier");
        sup.add(PartnerKind.SUPPLIER);
        sup.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                sup);

        ArrayList<Object> cust = new ArrayList<Object>();
        cust.add(CryptoParse.longToString(companyId));
        cust.add("CUS1");
        cust.add("Customer");
        cust.add(PartnerKind.CUSTOMER);
        cust.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                cust);

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
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(companyId));
        bankAccountId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM bank_accounts WHERE company_id=? ORDER BY id LIMIT 1", bp, new LongMapper()));
    }

    static int okPost(PostResultDto r) {
        if (r == null) {
            return 0;
        }
        return r.ok;
    }

    static int runCoreScenario(int pass) {
        long now = AsyncClockNative.wallMillis();

        ArrayList<Object> poLines = new ArrayList<Object>();
        PoLineInput pl = new PoLineInput();
        pl.itemId = itemId;
        pl.quantity = 100;
        pl.unitPriceCents = 50L;
        poLines.add(pl);
        PurchaseOrderDto po = ErpPurchasing.createDraft(companyId, supplierId, poLines);
        if (po == null) {
            System.out.println("FAIL PO create");
            return 0;
        }
        po = ErpPurchasing.approve(companyId, po.id);
        OrderLineDto poLine = (OrderLineDto) po.lines.get(0);

        PostGoodsReceiptRequest gr = new PostGoodsReceiptRequest();
        gr.purchaseOrderId = po.id;
        gr.warehouseId = warehouseId;
        gr.idempotencyKey = "fin-gr-1";
        gr.lines = new ArrayList<Object>();
        PostLineRequest gl = new PostLineRequest();
        gl.orderLineId = poLine.id;
        gl.quantity = 100;
        gr.lines.add(gl);
        PostResultDto grR = ErpPosting.postGoodsReceipt(companyId, gr);
        if (okPost(grR) == 0) {
            System.out.println("FAIL GR");
            if (grR != null) {
                System.out.println(grR.code);
                System.out.println(grR.message);
            }
            return 0;
        }
        
        ArrayList<Object> soLines = new ArrayList<Object>();
        SoLineInput sl = new SoLineInput();
        sl.itemId = itemId;
        sl.quantity = 30;
        sl.unitPriceCents = 80L;
        soLines.add(sl);
        SalesOrderDto so = ErpSales.createDraft(companyId, customerId, soLines);
        if (so == null) {
            System.out.println("FAIL SO create");
            return 0;
        }
                so = ErpSales.approve(companyId, so.id);
                OrderLineDto soLine = (OrderLineDto) so.lines.get(0);

        PostDeliveryRequest dl = new PostDeliveryRequest();
        dl.salesOrderId = so.id;
        dl.warehouseId = warehouseId;
        dl.idempotencyKey = "fin-dl-1";
        dl.lines = new ArrayList<Object>();
        PostLineRequest dln = new PostLineRequest();
        dln.orderLineId = soLine.id;
        dln.quantity = 30;
        dl.lines.add(dln);
        PostResultDto dlR = ErpPosting.postDelivery(companyId, dl);
        if (okPost(dlR) == 0) {
            System.out.println("FAIL DL");
            if (dlR != null) {
                System.out.println(dlR.code);
                System.out.println(dlR.message);
            }
            return 0;
        }
        
        ArrayList<Object> balP = new ArrayList<Object>();
        balP.add(CryptoParse.longToString(companyId));
        balP.add(CryptoParse.longToString(warehouseId));
        balP.add(CryptoParse.longToString(itemId));
                Object invQtyObj = null;
        Object invValObj = null;
        try {
            invQtyObj = ErpDb.queryOne(
                    "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    balP, new LongMapper());
            invValObj = ErpDb.queryOne(
                    "SELECT value_amount FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    balP, new LongMapper());
        } catch (Exception invEx) {
            System.out.println("FAIL inv query");
            if (invEx.getMessage() != null) {
                System.out.println(invEx.getMessage());
            }
            return 0;
        }
        if (invQtyObj == null) {
            System.out.println("FAIL inv balance missing");
            return 0;
        }
                long invQty = ErpNum.asLong(invQtyObj);
        long invVal = ErpNum.asLong(invValObj);
        if (invQty != 70L || invVal != 3500L) {
            System.out.println("FAIL inv qty/value expected 70/3500 got "
                    + CryptoParse.longToString(invQty) + "/" + CryptoParse.longToString(invVal));
            pass = 0;
        }

        long cogs = debitNet("5000");
        if (cogs != 1500L) {
            System.out.println("FAIL COGS expected 1500 got " + CryptoParse.longToString(cogs));
            pass = 0;
        }

        ErpDb.reconnect();
        ApInvoiceDto ap = ErpFinanceArAp.createApInvoice(companyId, supplierId, 5000L, now, now, 0L);
        if (ap == null) {
            System.out.println("FAIL AP create");
            return 0;
        }
                PostResultDto apPost = null;
        try {
            apPost = ErpFinanceArAp.postApInvoice(companyId, 0L, ap.id);
        } catch (Exception postEx) {
            System.out.println("FAIL AP post throw");
            if (postEx.getMessage() != null) {
                System.out.println(postEx.getMessage());
            }
            return 0;
        }
        if (okPost(apPost) == 0) {
            System.out.println("FAIL AP post");
            if (apPost != null) {
                System.out.println(apPost.code);
                System.out.println(apPost.message);
            }
            return 0;
        }
        
        ErpDb.reconnect();
        ArInvoiceDto ar = ErpFinanceArAp.createArInvoice(companyId, customerId, 2400L, now, now, 0L);
        if (ar == null) {
            System.out.println("FAIL AR create");
            return 0;
        }
        PostResultDto arPost = ErpFinanceArAp.postArInvoice(companyId, 0L, ar.id);
        if (okPost(arPost) == 0) {
            System.out.println("FAIL AR post");
            if (arPost != null) {
                if (arPost.code != null) {
                    System.out.println(arPost.code);
                }
                if (arPost.message != null) {
                    System.out.println(arPost.message);
                }
            }
            return 0;
        }
        long rev = creditNet("4000");
        if (rev != 2400L) {
            System.out.println("FAIL revenue expected 2400 got " + CryptoParse.longToString(rev));
            pass = 0;
        }
        long gp = rev - debitNet("5000");
        if (gp != 900L) {
            System.out.println("FAIL GP expected 900 got " + CryptoParse.longToString(gp));
            pass = 0;
        }

        IncomingPaymentRequest ip1 = new IncomingPaymentRequest();
        ip1.customerId = customerId;
        ip1.paymentDate = now;
        ip1.amount = 1000L;
        ip1.bankAccountId = bankAccountId;
        ip1.idempotencyKey = "fin-ip-1";
        ip1.allocations = new ArrayList<Object>();
        PaymentAllocationInput a1 = new PaymentAllocationInput();
        a1.invoiceId = ar.id;
        a1.allocatedAmount = 1000L;
        ip1.allocations.add(a1);
        if (okPost(ErpFinanceArAp.postIncomingPayment(companyId, 0L, ip1)) == 0) {
            System.out.println("FAIL IP1");
            return 0;
        }

        IncomingPaymentRequest ip2 = new IncomingPaymentRequest();
        ip2.customerId = customerId;
        ip2.paymentDate = now;
        ip2.amount = 1400L;
        ip2.bankAccountId = bankAccountId;
        ip2.idempotencyKey = "fin-ip-2";
        ip2.allocations = new ArrayList<Object>();
        PaymentAllocationInput a2 = new PaymentAllocationInput();
        a2.invoiceId = ar.id;
        a2.allocatedAmount = 1400L;
        ip2.allocations.add(a2);
        if (okPost(ErpFinanceArAp.postIncomingPayment(companyId, 0L, ip2)) == 0) {
            System.out.println("FAIL IP2");
            return 0;
        }

        OutgoingPaymentRequest op1 = new OutgoingPaymentRequest();
        op1.supplierId = supplierId;
        op1.paymentDate = now;
        op1.amount = 2000L;
        op1.bankAccountId = bankAccountId;
        op1.idempotencyKey = "fin-op-1";
        op1.allocations = new ArrayList<Object>();
        PaymentAllocationInput pa1 = new PaymentAllocationInput();
        pa1.invoiceId = ap.id;
        pa1.allocatedAmount = 2000L;
        op1.allocations.add(pa1);
        PostResultDto op1r = ErpFinanceArAp.postOutgoingPayment(companyId, 0L, op1);
        if (okPost(op1r) == 0) {
            System.out.println("FAIL OP1");
            if (op1r != null) {
                if (op1r.code != null) {
                    System.out.println(op1r.code);
                }
                if (op1r.message != null) {
                    System.out.println(op1r.message);
                }
            }
            return 0;
        }

        OutgoingPaymentRequest op2 = new OutgoingPaymentRequest();
        op2.supplierId = supplierId;
        op2.paymentDate = now;
        op2.amount = 3000L;
        op2.bankAccountId = bankAccountId;
        op2.idempotencyKey = "fin-op-2";
        op2.allocations = new ArrayList<Object>();
        PaymentAllocationInput pa2 = new PaymentAllocationInput();
        pa2.invoiceId = ap.id;
        pa2.allocatedAmount = 3000L;
        op2.allocations.add(pa2);
        if (okPost(ErpFinanceArAp.postOutgoingPayment(companyId, 0L, op2)) == 0) {
            System.out.println("FAIL OP2");
            return 0;
        }

        ArrayList<Object> arP = new ArrayList<Object>();
        arP.add(CryptoParse.longToString(ar.id));
        arP.add(CryptoParse.longToString(companyId));
        Object arOpen = ErpDb.queryOne("SELECT open_amount FROM ar_invoices WHERE id=? AND company_id=?", arP,
                new LongMapper());
        ArrayList<Object> apP = new ArrayList<Object>();
        apP.add(CryptoParse.longToString(ap.id));
        apP.add(CryptoParse.longToString(companyId));
        Object apOpen = ErpDb.queryOne("SELECT open_amount FROM ap_invoices WHERE id=? AND company_id=?", apP,
                new LongMapper());
        if (ErpNum.asLong(arOpen) != 0L || ErpNum.asLong(apOpen) != 0L) {
            System.out.println("FAIL open AR/AP not zero");
            pass = 0;
        }

        if (journalsBalanced() == 0) {
            System.out.println("FAIL journals unbalanced");
            pass = 0;
        }

        ArrayList<Object> tb = ErpFinanceReports.trialBalance(companyId, 0L, now + 1L);
        long tot = ErpFinanceReports.trialTotalsBalanced(tb);
        if (tot < 0L) {
            System.out.println("FAIL trial balance");
            pass = 0;
        }

        System.out.println("FINANCE_CORE invQty=" + CryptoParse.longToString(invQty)
                + " invVal=" + CryptoParse.longToString(invVal)
                + " cogs=" + CryptoParse.longToString(debitNet("5000"))
                + " rev=" + CryptoParse.longToString(creditNet("4000"))
                + " gp=" + CryptoParse.longToString(gp)
                + " tb=" + CryptoParse.longToString(tot));
        return pass;
    }

    static long debitNet(String code) {
        Object aid = ErpFinanceCore.accountIdByCode(companyId, code);
        long id = ErpNum.asLong(aid);
        long dr = ErpFinanceReports.sumSide(companyId, id, 0L, 4102444800000L, 1);
        long cr = ErpFinanceReports.sumSide(companyId, id, 0L, 4102444800000L, 0);
        return dr - cr;
    }

    static long creditNet(String code) {
        return 0L - debitNet(code);
    }

    static int journalsBalanced() {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object bad = ErpDb.queryOne(
                "SELECT COUNT(*) FROM (SELECT e.id FROM journal_entries e JOIN journal_entry_lines l ON l.journal_entry_id=e.id WHERE e.company_id=? AND e.status='POSTED' GROUP BY e.id HAVING SUM(l.debit_amount) <> SUM(l.credit_amount)) t",
                p, new LongMapper());
        if (ErpNum.asLong(bad) != 0L) {
            return 0;
        }
        return 1;
    }

    static int runExceptionChecks(int pass) {
        long now = AsyncClockNative.wallMillis();
        /* Duplicate IP idempotency */
        IncomingPaymentRequest dup = new IncomingPaymentRequest();
        dup.customerId = customerId;
        dup.paymentDate = now;
        dup.amount = 1000L;
        dup.bankAccountId = bankAccountId;
        dup.idempotencyKey = "fin-ip-1";
        dup.allocations = new ArrayList<Object>();
        PostResultDto d1 = ErpFinanceArAp.postIncomingPayment(companyId, 0L, dup);
        if (d1 == null || d1.ok == 0) {
            System.out.println("FAIL idempotent replay");
            pass = 0;
        }

        /* Over-allocation against closed AR */
        ArInvoiceDto ar2 = ErpFinanceArAp.createArInvoice(companyId, customerId, 100L, now, now, 0L);
        if (ar2 == null) {
            System.out.println("FAIL AR2 create");
            return 0;
        }
        ErpFinanceArAp.postArInvoice(companyId, 0L, ar2.id);
        IncomingPaymentRequest over = new IncomingPaymentRequest();
        over.customerId = customerId;
        over.paymentDate = now;
        over.amount = 200L;
        over.bankAccountId = bankAccountId;
        over.idempotencyKey = "fin-ip-over";
        over.allocations = new ArrayList<Object>();
        PaymentAllocationInput oa = new PaymentAllocationInput();
        oa.invoiceId = ar2.id;
        oa.allocatedAmount = 200L;
        over.allocations.add(oa);
        PostResultDto overR = ErpFinanceArAp.postIncomingPayment(companyId, 0L, over);
        if (overR != null && overR.ok != 0) {
            System.out.println("FAIL expected over-allocation reject");
            pass = 0;
        }

        /* Closed period reject */
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        ErpDb.exec("UPDATE fiscal_periods SET status='CLOSED' WHERE company_id=?", cp);
        PostingRequest bad = new PostingRequest();
        bad.sourceType = JournalSource.MANUAL;
        bad.sourceId = 1L;
        bad.postingDate = now;
        bad.documentDate = now;
        bad.description = "should fail";
        bad.idempotencyKey = "fin-closed";
        bad.lines = new ArrayList<Object>();
        long bank = ErpFinanceCore.mappedAccountId(companyId, AccountMapKey.BANK);
        long arAcc = ErpFinanceCore.mappedAccountId(companyId, AccountMapKey.AR);
        bad.lines.add(ErpFinanceCore.line(bank, 10L, 0L, 0L, "x"));
        bad.lines.add(ErpFinanceCore.line(arAcc, 0L, 10L, 0L, "y"));
        ClosedPeriodWork w = new ClosedPeriodWork();
        w.companyId = companyId;
        w.req = bad;
        PostResultDto closedR = ErpFinanceArAp.runPostWork(w);
        if (closedR != null && closedR.ok != 0) {
            System.out.println("FAIL expected closed period reject");
            pass = 0;
        }
        ErpDb.exec("UPDATE fiscal_periods SET status='OPEN' WHERE company_id=?", cp);

        return pass;
    }
}

class ClosedPeriodWork implements DbTransactionWork {
    long companyId;
    PostingRequest req;

    public JnFuture run(DbConnection conn) {
        return JnFuture.completed(Erp.loop, ErpFinanceCore.postJournalConn(conn, companyId, 0L, req));
    }
}
