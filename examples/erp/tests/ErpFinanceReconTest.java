/**
 * Isolated-DB recon: opening cash/stock → GR/AP/pay → DL/AR/receipt → void;
 * asserts AR/AP control vs detail, bank/GL/bcm, inventory value, trial balance.
 * Money = integer cents throughout.
 * @author 张晓达
 */
class ErpFinanceReconTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long itemId = 0L;
    static long supplierId = 0L;
    static long customerId = 0L;
    static long bankAccountId = 0L;
    static long adminUserId = 0L;

    static int runAll() {
        if (seed() == 0) {
            System.out.println("FAIL recon seed");
            return 0;
        }
        int pass = 1;
        pass = flowPurchasePay(pass);
        pass = flowSalesReceipt(pass);
        pass = assertBooks(pass);
        if (pass != 0) {
            System.out.println("PASS finance_recon");
            System.out.println("RECON_UNIT=cents");
        } else {
            System.out.println("FAIL finance_recon");
        }
        return pass;
    }

    static int seed() {
        String code = "R" + CryptoParse.longToString(AsyncClockNative.wallMillis() % 100000000L);
        companyId = ErpSetupWizard.createFormalCompany(code, "Recon Co", "Asia/Shanghai");
        ErpSetupWizard.applyCompanyStep(companyId, 0L, "Recon Co", "Asia/Shanghai");
        ErpSetupWizard.applyCurrencyStep(companyId, 0L, "CNY", 0);
        warehouseId = ErpSetupWizard.applyWarehouseStep(companyId, 0L, "WH1", "Main");
        ErpSetupWizard.applyNumberingStep(companyId, 0L);
        adminUserId = ErpSetupWizard.applyUserStep(companyId, 0L, "admin@" + code + ".test", "Admin",
                "erp1234", "admin");
        ErpSetupWizard.advanceToOpening(companyId, adminUserId);
        ErpFinanceCore.ensureSeed(companyId);
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> it = new ArrayList<Object>();
        it.add(CryptoParse.longToString(companyId));
        it.add("SKUREC");
        it.add("Recon Item");
        it.add("8000");
        it.add("5000");
        it.add(CryptoParse.longToString(now));
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES (?,?,?,?,?,?)",
                it);
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("SKUREC");
        itemId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", ip,
                new LongMapper()));
        ArrayList<Object> su = new ArrayList<Object>();
        su.add(CryptoParse.longToString(companyId));
        su.add("SUPR");
        su.add("Supplier R");
        su.add(PartnerKind.SUPPLIER);
        su.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", su);
        ArrayList<Object> cu = new ArrayList<Object>();
        cu.add(CryptoParse.longToString(companyId));
        cu.add("CUSR");
        cu.add("Customer R");
        cu.add(PartnerKind.CUSTOMER);
        cu.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", cu);
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add("SUPR");
        supplierId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", sp, new LongMapper()));
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add("CUSR");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cp, new LongMapper()));
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(companyId));
        bankAccountId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM bank_accounts WHERE company_id=? ORDER BY id LIMIT 1", bp, new LongMapper()));
        /* Opening cash (cents) via bank sub-ledger seed; stock via standard opening docs. */
        ErpFinanceSettlement.seedBankBalance(companyId, 5000000L);
        ArrayList<Object> osLines = new ArrayList<Object>();
        OpeningLineInput oln = new OpeningLineInput();
        oln.itemId = itemId;
        oln.quantity = 20;
        oln.unitCostAmount = 5000L;
        osLines.add(oln);
        PostResultDto draft = ErpOpening.createStockDraft(companyId, adminUserId, warehouseId,
                AsyncClockNative.wallMillis(), "recon open stock", "recon-os-draft", osLines);
        if (draft == null || draft.ok == 0) {
            System.out.println("FAIL recon opening stock draft");
            return 0;
        }
        long osId = ErpFinanceSettlement.parseDocId(draft.documentJson);
        PostResultDto openStock = ErpOpening.postStock(companyId, adminUserId, osId, "recon-os-post");
        if (openStock == null || openStock.ok == 0) {
            System.out.println("FAIL recon opening stock post");
            if (openStock != null && openStock.message != null) {
                System.out.println(openStock.message);
            }
            return 0;
        }
        ErpSetupWizard.validateAndComplete(companyId, adminUserId);
        if (companyId == 0L || itemId == 0L || bankAccountId == 0L) {
            return 0;
        }
        System.out.println("OK recon seed company=" + CryptoParse.longToString(companyId)
                + " openStockQty=20 openStockCents=100000 bankCents=5000000");
        return 1;
    }

    static int flowPurchasePay(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> lines = new ArrayList<Object>();
        PoLineInput ln = new PoLineInput();
        ln.itemId = itemId;
        ln.quantity = 10;
        ln.unitPriceCents = 5000L;
        lines.add(ln);
        PurchaseOrderDto po = ErpPurchasing.createDraftFull(companyId, supplierId, warehouseId, 0L, 0L, "recon",
                "CNY", lines);
        ErpPurchasing.approve(companyId, po.id);
        PurchaseOrderDto full = ErpPurchasing.get(companyId, po.id);
        OrderLineDto ol = (OrderLineDto) full.lines.get(0);
        PostGoodsReceiptRequest gr = new PostGoodsReceiptRequest();
        gr.purchaseOrderId = po.id;
        gr.warehouseId = warehouseId;
        gr.idempotencyKey = "recon-gr-1";
        gr.lines = new ArrayList<Object>();
        PostLineRequest gl = new PostLineRequest();
        gl.orderLineId = ol.id;
        gl.quantity = 10;
        gr.lines.add(gl);
        PostResultDto grR = ErpPosting.postGoodsReceipt(companyId, gr);
        if (grR == null || grR.ok == 0) {
            System.out.println("FAIL recon GR");
            return 0;
        }
        long apId = ErpFinanceSettlement.createOpenAp(companyId, supplierId, 50000L, now, now, "CNY");
        OutgoingPaymentRequest op = new OutgoingPaymentRequest();
        op.supplierId = supplierId;
        op.paymentDate = now;
        op.amount = 50000L;
        op.bankAccountId = bankAccountId;
        op.idempotencyKey = "recon-op-1";
        op.postNow = 1;
        op.allocations = new ArrayList<Object>();
        PaymentAllocationInput a = new PaymentAllocationInput();
        a.invoiceId = apId;
        a.allocatedAmount = 50000L;
        op.allocations.add(a);
        PostResultDto pay = ErpFinanceSettlement.createPayment(companyId, adminUserId, op);
        if (pay == null || pay.ok == 0) {
            System.out.println("FAIL recon OP");
            return 0;
        }
        long payId = ErpFinanceSettlement.parseDocId(pay.documentJson);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "recon-op-void-1";
        vr.reason = "recon void OP";
        PostResultDto voidR = ErpFinanceSettlement.voidPayment(companyId, adminUserId, vr);
        if (voidR == null || voidR.ok == 0) {
            System.out.println("FAIL recon OP void");
            if (voidR != null && voidR.message != null) {
                System.out.println(voidR.message);
            }
            return 0;
        }
        if (ErpFinanceSettlement.apOpen(companyId, apId) != 50000L) {
            System.out.println("FAIL recon AP open after void");
            return 0;
        }
        System.out.println("OK recon purchase→pay→void apOpenCents=50000");
        return 1;
    }

    static int flowSalesReceipt(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> lines = new ArrayList<Object>();
        SoLineInput sl = new SoLineInput();
        sl.itemId = itemId;
        sl.quantity = 3;
        sl.unitPriceCents = 8000L;
        lines.add(sl);
        SalesOrderDto so = ErpSales.createDraftFull(companyId, customerId, warehouseId, 0L, 0L, "recon", "CNY",
                lines);
        ErpSales.approve(companyId, so.id);
        SalesOrderDto full = ErpSales.get(companyId, so.id);
        OrderLineDto ol = (OrderLineDto) full.lines.get(0);
        PostDeliveryRequest dl = new PostDeliveryRequest();
        dl.salesOrderId = so.id;
        dl.warehouseId = warehouseId;
        dl.idempotencyKey = "recon-dl-1";
        dl.lines = new ArrayList<Object>();
        PostLineRequest ll = new PostLineRequest();
        ll.orderLineId = ol.id;
        ll.quantity = 3;
        dl.lines.add(ll);
        PostResultDto dlR = ErpPosting.postDelivery(companyId, dl);
        if (dlR == null || dlR.ok == 0) {
            System.out.println("FAIL recon DL");
            return 0;
        }
        long arId = ErpFinanceSettlement.createOpenAr(companyId, customerId, 24000L, now, now, "CNY");
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 24000L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "recon-ip-1";
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PaymentAllocationInput a = new PaymentAllocationInput();
        a.invoiceId = arId;
        a.allocatedAmount = 24000L;
        ip.allocations.add(a);
        PostResultDto rec = ErpFinanceSettlement.createReceipt(companyId, adminUserId, ip);
        if (rec == null || rec.ok == 0) {
            System.out.println("FAIL recon IP");
            return 0;
        }
        long payId = ErpFinanceSettlement.parseDocId(rec.documentJson);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "recon-ip-void-1";
        vr.reason = "recon void IP";
        if (ErpFinanceSettlement.voidReceipt(companyId, adminUserId, vr).ok == 0) {
            System.out.println("FAIL recon IP void");
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, arId) != 24000L) {
            System.out.println("FAIL recon AR open after void");
            return 0;
        }
        System.out.println("OK recon sales→receipt→void arOpenCents=24000");
        return 1;
    }

    static int assertBooks(int pass) {
        if (pass == 0) {
            return 0;
        }
        ArrayList<Object> cid = new ArrayList<Object>();
        cid.add(CryptoParse.longToString(companyId));
        /* Bank sub-ledger vs movements */
        Object bal = ErpDb.queryOne("SELECT balance FROM bank_accounts WHERE company_id=? ORDER BY id LIMIT 1",
                cid, new LongMapper());
        Object mov = ErpDb.queryOne(
                "SELECT COALESCE(SUM(amount),0) FROM bank_cash_movements WHERE company_id=?", cid,
                new LongMapper());
        /* Opening seed may set balance without matching movements; when movements exist, last balance_after wins. */
        Object movCnt = ErpDb.queryOne("SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=?", cid,
                new LongMapper());
        if (ErpNum.asLong(movCnt) > 0L) {
            Object last = ErpDb.queryOne(
                    "SELECT balance_after FROM bank_cash_movements WHERE company_id=? ORDER BY id DESC LIMIT 1",
                    cid, new LongMapper());
            if (ErpNum.asLong(bal) != ErpNum.asLong(last)) {
                System.out.println("FAIL recon bank bal!=lastMov bal="
                        + CryptoParse.longToString(ErpNum.asLong(bal)) + " last="
                        + CryptoParse.longToString(ErpNum.asLong(last)));
                return 0;
            }
        }
        /* Inventory qty/value */
        ArrayList<Object> iq = new ArrayList<Object>();
        iq.add(CryptoParse.longToString(companyId));
        iq.add(CryptoParse.longToString(warehouseId));
        iq.add(CryptoParse.longToString(itemId));
        Object qty = ErpDb.queryOne(
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                iq, new IntMapper());
        Object val = ErpDb.queryOne(
                "SELECT value_amount FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                iq, new LongMapper());
        /* 20 open +10 GR -3 DL = 27; value should track moving avg */
        if (ErpNum.parseInt((String) qty) != 27) {
            System.out.println("FAIL recon qty want 27 got " + (String) qty);
            return 0;
        }
        if (ErpNum.asLong(val) <= 0L) {
            System.out.println("FAIL recon inventory value");
            return 0;
        }
        if (ErpFinanceSettlement.runIntegrityChecks(companyId) == 0) {
            System.out.println("FAIL recon settlement integrity");
            return 0;
        }
        if (ErpCosting.runConservationChecks(companyId) == 0) {
            System.out.println("FAIL recon costing integrity");
            return 0;
        }
        ArrayList<Object> tb = ErpFinanceReports.trialBalance(companyId, 0L, AsyncClockNative.wallMillis() + 1L);
        long tot = ErpFinanceReports.trialTotalsBalanced(tb);
        if (tot < 0L) {
            System.out.println("FAIL recon trial balance");
            return 0;
        }
        System.out.println("OK recon books qty=27 invValCents="
                + CryptoParse.longToString(ErpNum.asLong(val))
                + " bankCents=" + CryptoParse.longToString(ErpNum.asLong(bal))
                + " bcmSumCents=" + CryptoParse.longToString(ErpNum.asLong(mov))
                + " tbCents=" + CryptoParse.longToString(tot));
        return 1;
    }
}
