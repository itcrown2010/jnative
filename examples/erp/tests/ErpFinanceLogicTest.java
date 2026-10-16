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
        if (pass != 0) {
            pass = runExceptionChecks(pass);
        }
        if (pass != 0) {
            System.out.println("PASS finance");
        } else {
            System.out.println("FAIL finance");
        }
        return pass;
    }

    static void seedMinimal() {
        System.out.println("STEP finance seed");
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
        /* Opening cash so AP payouts (2000+3000) after AR collections stay within overdraft rules. */
        if (bankAccountId > 0L) {
            FinOpenCashWork open = new FinOpenCashWork();
            open.companyId = companyId;
            open.bankAccountId = bankAccountId;
            open.amount = 10000L;
            open.bizDate = now;
            PostResultDto openR = ErpPosting.runPostingWork(open);
            if (openR == null || openR.ok == 0) {
                System.out.println("WARN finance opening cash failed");
                if (openR != null && openR.message != null) {
                    System.out.println(openR.message);
                }
            }
        }
    }

    static int okPost(PostResultDto r) {
        if (r == null) {
            return 0;
        }
        return r.ok;
    }

    static int runCoreScenario(int pass) {
        long now = AsyncClockNative.wallMillis();
        System.out.println("STEP finance core start");

        ArrayList<Object> poLines = new ArrayList<Object>();
        PoLineInput pl = new PoLineInput();
        pl.itemId = itemId;
        pl.quantity = 100;
        pl.unitPriceCents = 50L;
        poLines.add(pl);
        PurchaseOrderDto po = null;
        try {
            System.out.println("STEP PO create");
            po = ErpPurchasing.createDraft(companyId, supplierId, poLines);
        } catch (Exception ex) {
            System.out.println("FAIL PO create ex");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (po == null) {
            System.out.println("FAIL PO create");
            return 0;
        }
        try {
            System.out.println("STEP PO approve id=" + CryptoParse.longToString(po.id));
            po = ErpPurchasing.approve(companyId, po.id);
        } catch (Exception ex) {
            System.out.println("FAIL PO approve ex");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (po == null || po.lines == null || po.lines.size() == 0) {
            System.out.println("FAIL PO approve");
            return 0;
        }
        System.out.println("STEP PO approved");
        OrderLineDto poLine = (OrderLineDto) po.lines.get(0);
        System.out.println("STEP poLine id=" + CryptoParse.longToString(poLine.id));

        PostGoodsReceiptRequest gr = new PostGoodsReceiptRequest();
        gr.purchaseOrderId = po.id;
        gr.warehouseId = warehouseId;
        gr.idempotencyKey = "fin-gr-1";
        gr.lines = new ArrayList<Object>();
        PostLineRequest gl = new PostLineRequest();
        gl.orderLineId = poLine.id;
        gl.quantity = 100;
        gr.lines.add(gl);
        PostResultDto grR = null;
        try {
            System.out.println("STEP GR post");
            ErpDb.reconnect();
            grR = ErpPosting.postGoodsReceipt(companyId, gr);
        } catch (Exception ex) {
            System.out.println("FAIL GR ex");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            } else {
                System.out.println("EX_EMPTY GR");
            }
            return 0;
        }
        if (okPost(grR) == 0) {
            System.out.println("FAIL GR");
            if (grR != null) {
                System.out.println(grR.code);
                System.out.println(grR.message);
            }
            return 0;
        }
        System.out.println("STEP GR ok");
        
        ArrayList<Object> soLines = new ArrayList<Object>();
        SoLineInput sl = new SoLineInput();
        sl.itemId = itemId;
        sl.quantity = 30;
        sl.unitPriceCents = 80L;
        soLines.add(sl);
        ErpDb.reconnect();
        SalesOrderDto so = null;
        int soAttempt = 0;
        while (soAttempt < 3 && so == null) {
            try {
                so = ErpSales.createDraftFull(companyId, customerId, warehouseId, 0L, 0L, "fin", "CNY",
                        soLines);
                if (so != null) {
                    so = ErpSales.approve(companyId, so.id);
                }
            } catch (Exception soEx) {
                System.out.println("SO_ATTEMPT fail");
                if (soEx.getMessage() != null) {
                    System.out.println(soEx.getMessage());
                }
                so = null;
                ErpDb.reconnect();
            }
            soAttempt = soAttempt + 1;
        }
        if (so == null || so.lines == null || so.lines.size() == 0) {
            System.out.println("FAIL SO create/approve");
            return 0;
        }
        OrderLineDto soLine = (OrderLineDto) so.lines.get(0);
        if (soLine.reservedQty < 30) {
            System.out.println("FAIL SO reserve qty=" + String.valueOf(soLine.reservedQty));
            return 0;
        }

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
        PostResultDto op2r = ErpFinanceArAp.postOutgoingPayment(companyId, 0L, op2);
        if (okPost(op2r) == 0) {
            System.out.println("FAIL OP2");
            if (op2r != null) {
                if (op2r.code != null) {
                    System.out.println(op2r.code);
                }
                if (op2r.message != null) {
                    System.out.println(op2r.message);
                }
            }
            ArrayList<Object> balP = new ArrayList<Object>();
            balP.add(CryptoParse.longToString(bankAccountId));
            Object bal = ErpDb.queryOne("SELECT balance FROM bank_accounts WHERE id=?", balP, new LongMapper());
            System.out.println("bank_balance=" + CryptoParse.longToString(ErpNum.asLong(bal)));
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

        /* Regression: invoice open changed must co-exist with bank cash movements + GL
         * (legacy ArAp path historically updated paid/open without bank_cash_movements). */
        ArrayList<Object> cidP = new ArrayList<Object>();
        cidP.add(CryptoParse.longToString(companyId));
        Object bcmIp = ErpDb.queryOne(
                "SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=? AND source_type='INCOMING_PAYMENT'",
                cidP, new LongMapper());
        Object bcmOp = ErpDb.queryOne(
                "SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=? AND source_type='OUTGOING_PAYMENT'",
                cidP, new LongMapper());
        Object jeIp = ErpDb.queryOne(
                "SELECT COUNT(*) FROM journal_entries WHERE company_id=? AND source_type='INCOMING_PAYMENT' AND status='POSTED'",
                cidP, new LongMapper());
        Object jeOp = ErpDb.queryOne(
                "SELECT COUNT(*) FROM journal_entries WHERE company_id=? AND source_type='OUTGOING_PAYMENT' AND status='POSTED'",
                cidP, new LongMapper());
        Object arAlloc = ErpDb.queryOne(
                "SELECT COUNT(*) FROM ar_settlement_allocs WHERE company_id=? AND status='ACTIVE'",
                cidP, new LongMapper());
        Object apAlloc = ErpDb.queryOne(
                "SELECT COUNT(*) FROM ap_settlement_allocs WHERE company_id=? AND status='ACTIVE'",
                cidP, new LongMapper());
        if (ErpNum.asLong(bcmIp) < 2L || ErpNum.asLong(bcmOp) < 2L) {
            System.out.println("FAIL bank_cash_movements missing for IP/OP bcmIp="
                    + CryptoParse.longToString(ErpNum.asLong(bcmIp)) + " bcmOp="
                    + CryptoParse.longToString(ErpNum.asLong(bcmOp)));
            pass = 0;
        }
        if (ErpNum.asLong(jeIp) < 2L || ErpNum.asLong(jeOp) < 2L) {
            System.out.println("FAIL payment GL missing jeIp="
                    + CryptoParse.longToString(ErpNum.asLong(jeIp)) + " jeOp="
                    + CryptoParse.longToString(ErpNum.asLong(jeOp)));
            pass = 0;
        }
        if (ErpNum.asLong(arAlloc) < 2L || ErpNum.asLong(apAlloc) < 2L) {
            System.out.println("FAIL settlement alloc rows missing ar="
                    + CryptoParse.longToString(ErpNum.asLong(arAlloc)) + " ap="
                    + CryptoParse.longToString(ErpNum.asLong(apAlloc)));
            pass = 0;
        }
        ArrayList<Object> bankP = new ArrayList<Object>();
        bankP.add(CryptoParse.longToString(bankAccountId));
        Object bankBal = ErpDb.queryOne("SELECT balance FROM bank_accounts WHERE id=?", bankP, new LongMapper());
        Object bcmSum = ErpDb.queryOne(
                "SELECT COALESCE(SUM(amount),0) FROM bank_cash_movements WHERE bank_account_id=?",
                bankP, new LongMapper());
        if (ErpNum.asLong(bankBal) != ErpNum.asLong(bcmSum)) {
            System.out.println("FAIL bank balance != cash movement sum bal="
                    + CryptoParse.longToString(ErpNum.asLong(bankBal)) + " sum="
                    + CryptoParse.longToString(ErpNum.asLong(bcmSum)));
            pass = 0;
        } else {
            System.out.println("OK payment_bank_gl_settlement_aligned");
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
        ErpDb.reconnect();
        System.out.println("STEP finance exception checks");
        try {
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
            } else {
                System.out.println("OK idempotent_replay");
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
            } else {
                System.out.println("OK over_alloc_rejected");
            }

            /* Closed period reject */
            ArrayList<Object> cp = new ArrayList<Object>();
            cp.add(CryptoParse.longToString(companyId));
            System.out.println("STEP close period");
            ErpDb.reconnect();
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
            System.out.println("STEP close maps bank=" + CryptoParse.longToString(bank)
                    + " ar=" + CryptoParse.longToString(arAcc));
            if (bank <= 0L || arAcc <= 0L) {
                System.out.println("FAIL mapped accounts missing for closed-period check");
                pass = 0;
            } else {
                bad.lines.add(ErpFinanceCore.line(bank, 10L, 0L, 0L, "x"));
                bad.lines.add(ErpFinanceCore.line(arAcc, 0L, 10L, 0L, "y"));
                ClosedPeriodWork w = new ClosedPeriodWork();
                w.companyId = companyId;
                w.req = bad;
                PostResultDto closedR = null;
                int cAttempt = 0;
                while (cAttempt < 3 && closedR == null) {
                    try {
                        closedR = ErpFinanceArAp.runPostWork(w);
                    } catch (Exception cEx) {
                        System.out.println("STEP closed post retry");
                        ErpDb.reconnect();
                        closedR = null;
                    }
                    cAttempt = cAttempt + 1;
                }
                if (closedR != null && closedR.ok != 0) {
                    System.out.println("FAIL expected closed period reject code=" + closedR.code);
                    pass = 0;
                } else {
                    System.out.println("OK closed_period_rejected");
                }
            }
            ErpDb.exec("UPDATE fiscal_periods SET status='OPEN' WHERE company_id=?", cp);
        } catch (Exception ex) {
            System.out.println("FAIL finance exceptionChecks");
            if (ex.getMessage() != null && ex.getMessage().length() > 0) {
                System.out.println(ex.getMessage());
            } else {
                System.out.println("EX_EMPTY exceptionChecks");
            }
            return 0;
        }
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

/** Seed opening cash: GL Bank/Equity + bank balance + cash movement (aligned). */
class FinOpenCashWork implements DbTransactionWork {
    long companyId;
    long bankAccountId;
    long amount;
    long bizDate;

    public JnFuture run(DbConnection conn) {
        long bankGl = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.BANK);
        long equity = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.EQUITY);
        if (bankGl == 0L || equity == 0L) {
            throw new TransactionException("opening cash: bank/equity unmapped");
        }
        PostingRequest openJe = new PostingRequest();
        openJe.sourceType = JournalSource.OPENING;
        openJe.sourceId = bankAccountId;
        openJe.postingDate = bizDate;
        openJe.documentDate = bizDate;
        openJe.description = "Opening bank cash";
        openJe.idempotencyKey = "fin-open-cash-" + CryptoParse.longToString(bankAccountId);
        openJe.lines = new ArrayList<Object>();
        openJe.lines.add(ErpFinanceCore.line(bankGl, amount, 0L, 0L, "Bank opening"));
        openJe.lines.add(ErpFinanceCore.line(equity, 0L, amount, 0L, "Equity opening"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, 0L, openJe);
        if (je == null || je.ok == 0) {
            String msg = "opening JE failed";
            if (je != null && je.message != null) {
                msg = je.message;
            }
            throw new TransactionException(msg);
        }
        ErpDb.execConn(conn,
                "UPDATE bank_accounts SET balance=" + CryptoParse.longToString(amount)
                        + " WHERE id=" + CryptoParse.longToString(bankAccountId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ArrayList<Object> mp = new ArrayList<Object>();
        mp.add(CryptoParse.longToString(companyId));
        mp.add(CryptoParse.longToString(bankAccountId));
        mp.add(CashMovementType.RECEIPT);
        mp.add(CryptoParse.longToString(amount));
        mp.add(CryptoParse.longToString(amount));
        mp.add(CryptoParse.longToString(bizDate));
        mp.add("OPENING");
        mp.add(CryptoParse.longToString(bankAccountId));
        mp.add("0");
        mp.add("opening cash");
        mp.add("0");
        mp.add(CryptoParse.longToString(bizDate));
        mp.add("fin-bcm-open-" + CryptoParse.longToString(bankAccountId));
        ErpDb.execConn(conn,
                "INSERT INTO bank_cash_movements(company_id, bank_account_id, movement_type, amount, balance_after, biz_date, source_type, source_id, party_id, notes, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                mp);
        return JnFuture.completed(Erp.loop, ErpFinanceSettlement.okDoc("OK", "opening cash",
                "{\"bankAccountId\":" + ErpJson.num(bankAccountId) + ",\"amount\":" + ErpJson.num(amount) + "}"));
    }
}
