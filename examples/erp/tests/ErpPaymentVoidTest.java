/**
 * Whole receipt/payment void acceptance (integer cents).
 * Covers unalloc / partial / full / prior alloc-reverse / duplicate / period /
 * overdraft / cross-company; asserts doc+invoice+alloc+bank+GL.
 * @author 张晓达
 */
class ErpPaymentVoidTest {
    static long companyId = 0L;
    static long otherCompanyId = 0L;
    static long customerId = 0L;
    static long supplierId = 0L;
    static long bankAccountId = 0L;
    static String companyCode = "V";

    static int runAll() {
        companyCode = "V" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        if (seed() == 0) {
            System.out.println("FAIL void seed");
            return 0;
        }
        int pass = 1;
        pass = caseReceiptUnallocated(pass);
        pass = caseReceiptFullAlloc(pass);
        pass = caseReceiptPartialThenPriorReverse(pass);
        pass = casePaymentFullAlloc(pass);
        pass = caseDuplicateVoid(pass);
        pass = casePeriodClosed(pass);
        pass = caseOverdraftReject(pass);
        pass = caseCrossCompany(pass);
        if (pass != 0) {
            pass = ErpFinanceSettlement.runIntegrityChecks(companyId);
        }
        if (pass != 0) {
            System.out.println("PASS payment_void");
        } else {
            System.out.println("FAIL payment_void");
        }
        return pass;
    }

    static int seed() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Void Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> co2 = new ArrayList<Object>();
        co2.add(companyCode + "X");
        co2.add("Other Co");
        co2.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co2);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));
        ArrayList<Object> chk2 = new ArrayList<Object>();
        chk2.add(companyCode + "X");
        otherCompanyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk2, new LongMapper()));
        ArrayList<Object> cu = new ArrayList<Object>();
        cu.add(CryptoParse.longToString(companyId));
        cu.add("CUSV");
        cu.add("Cust V");
        cu.add(PartnerKind.CUSTOMER);
        cu.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", cu);
        ArrayList<Object> su = new ArrayList<Object>();
        su.add(CryptoParse.longToString(companyId));
        su.add("SUPV");
        su.add("Sup V");
        su.add(PartnerKind.SUPPLIER);
        su.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", su);
        ErpFinanceCore.ensureSeed(companyId);
        ErpFinanceSettlement.seedBankBalance(companyId, 10000000L);
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add("CUSV");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cp, new LongMapper()));
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add("SUPV");
        supplierId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", sp, new LongMapper()));
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(companyId));
        bankAccountId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM bank_accounts WHERE company_id=? ORDER BY id LIMIT 1", bp, new LongMapper()));
        if (companyId == 0L || customerId == 0L || supplierId == 0L || bankAccountId == 0L) {
            return 0;
        }
        return 1;
    }

    static int okPost(PostResultDto r) {
        if (r == null || r.ok == 0) {
            return 0;
        }
        return 1;
    }

    static long bankBal() {
        return ErpFinanceSettlement.bankBalance(companyId, bankAccountId);
    }

    static long bcmCount(String sourceType) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sourceType);
        return ErpNum.asLong(ErpDb.queryOne(
                "SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=? AND source_type=?", p,
                new LongMapper()));
    }

    static long jeCount(String sourceType) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sourceType);
        return ErpNum.asLong(ErpDb.queryOne(
                "SELECT COUNT(*) FROM journal_entries WHERE company_id=? AND source_type=? AND status='POSTED'",
                p, new LongMapper()));
    }

    static long activeArAlloc(long payId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(payId));
        return ErpNum.asLong(ErpDb.queryOne(
                "SELECT COUNT(*) FROM ar_settlement_allocs WHERE company_id=? AND payment_id=? AND status='ACTIVE' AND reverse_of_id=0",
                p, new LongMapper()));
    }

    static String payStatus(String table, long id) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT status FROM " + table + " WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }

    static int caseReceiptUnallocated(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        long before = bankBal();
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 500L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-unalloc-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        if (okPost(r) == 0) {
            System.out.println("FAIL void unalloc create");
            return 0;
        }
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        if (bankBal() != before + 500L) {
            System.out.println("FAIL void unalloc bank after post");
            return 0;
        }
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-unalloc-v-" + companyCode;
        vr.reason = "unalloc void";
        PostResultDto vrR = ErpFinanceSettlement.voidReceipt(companyId, 0L, vr);
        if (okPost(vrR) == 0) {
            System.out.println("FAIL void unalloc void " + (vrR == null ? "" : vrR.message));
            return 0;
        }
        if (payStatus("incoming_payments", payId).equals(PaymentStatus.CANCELLED) == false) {
            System.out.println("FAIL void unalloc status");
            return 0;
        }
        if (bankBal() != before) {
            System.out.println("FAIL void unalloc bank not restored");
            return 0;
        }
        if (bcmCount("INCOMING_PAYMENT_VOID") < 1L) {
            System.out.println("FAIL void unalloc reverse bcm");
            return 0;
        }
        if (jeCount(JournalSource.REVERSAL) < 1L) {
            System.out.println("FAIL void unalloc reverse je");
            return 0;
        }
        System.out.println("OK void_receipt_unallocated");
        return 1;
    }

    static int caseReceiptFullAlloc(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        long arId = ErpFinanceSettlement.createOpenAr(companyId, customerId, 800L, now, now, "CNY");
        long before = bankBal();
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 800L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-full-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PaymentAllocationInput a = new PaymentAllocationInput();
        a.invoiceId = arId;
        a.allocatedAmount = 800L;
        ip.allocations.add(a);
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        if (okPost(r) == 0) {
            System.out.println("FAIL void full create");
            return 0;
        }
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        if (ErpFinanceSettlement.arOpen(companyId, arId) != 0L) {
            System.out.println("FAIL void full ar open before");
            return 0;
        }
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-full-v-" + companyCode;
        vr.reason = "full void";
        if (okPost(ErpFinanceSettlement.voidReceipt(companyId, 0L, vr)) == 0) {
            System.out.println("FAIL void full");
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, arId) != 800L) {
            System.out.println("FAIL void full ar not restored");
            return 0;
        }
        if (activeArAlloc(payId) != 0L) {
            System.out.println("FAIL void full active alloc remains");
            return 0;
        }
        if (bankBal() != before) {
            System.out.println("FAIL void full bank");
            return 0;
        }
        System.out.println("OK void_receipt_full_alloc");
        return 1;
    }

    static int caseReceiptPartialThenPriorReverse(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        long arId = ErpFinanceSettlement.createOpenAr(companyId, customerId, 1000L, now, now, "CNY");
        long before = bankBal();
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 1000L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-part-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PaymentAllocationInput a = new PaymentAllocationInput();
        a.invoiceId = arId;
        a.allocatedAmount = 600L;
        ip.allocations.add(a);
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        if (okPost(r) == 0) {
            System.out.println("FAIL void part create");
            return 0;
        }
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        long allocId = ErpFinanceSettlement.findActiveAllocId(companyId, payId, arId, "AR");
        SettlementReverseRequest rev = new SettlementReverseRequest();
        rev.allocationId = allocId;
        rev.idempotencyKey = "void-ip-part-rev-" + companyCode;
        rev.reason = "unalloc first";
        if (okPost(ErpFinanceSettlement.reverseReceiptAlloc(companyId, 0L, rev)) == 0) {
            System.out.println("FAIL void part prior reverse");
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, arId) != 1000L) {
            System.out.println("FAIL void part ar after alloc reverse");
            return 0;
        }
        long arOpenBeforeVoid = ErpFinanceSettlement.arOpen(companyId, arId);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-part-v-" + companyCode;
        vr.reason = "void after unalloc";
        if (okPost(ErpFinanceSettlement.voidReceipt(companyId, 0L, vr)) == 0) {
            System.out.println("FAIL void part void");
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, arId) != arOpenBeforeVoid) {
            System.out.println("FAIL void part double-restored AR");
            return 0;
        }
        if (bankBal() != before) {
            System.out.println("FAIL void part bank");
            return 0;
        }
        System.out.println("OK void_receipt_prior_alloc_reverse");
        return 1;
    }

    static int casePaymentFullAlloc(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        long apId = ErpFinanceSettlement.createOpenAp(companyId, supplierId, 700L, now, now, "CNY");
        long before = bankBal();
        OutgoingPaymentRequest op = new OutgoingPaymentRequest();
        op.supplierId = supplierId;
        op.paymentDate = now;
        op.amount = 700L;
        op.bankAccountId = bankAccountId;
        op.idempotencyKey = "void-op-full-" + companyCode;
        op.postNow = 1;
        op.allocations = new ArrayList<Object>();
        PaymentAllocationInput a = new PaymentAllocationInput();
        a.invoiceId = apId;
        a.allocatedAmount = 700L;
        op.allocations.add(a);
        PostResultDto r = ErpFinanceSettlement.createPayment(companyId, 0L, op);
        if (okPost(r) == 0) {
            System.out.println("FAIL void op create");
            return 0;
        }
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-op-full-v-" + companyCode;
        vr.reason = "op void";
        if (okPost(ErpFinanceSettlement.voidPayment(companyId, 0L, vr)) == 0) {
            System.out.println("FAIL void op");
            return 0;
        }
        if (ErpFinanceSettlement.apOpen(companyId, apId) != 700L) {
            System.out.println("FAIL void op ap not restored");
            return 0;
        }
        if (payStatus("outgoing_payments", payId).equals(PaymentStatus.CANCELLED) == false) {
            System.out.println("FAIL void op status");
            return 0;
        }
        if (bankBal() != before) {
            System.out.println("FAIL void op bank");
            return 0;
        }
        System.out.println("OK void_payment_full_alloc");
        return 1;
    }

    static int caseDuplicateVoid(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 100L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-dup-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-dup-v-" + companyCode;
        vr.reason = "dup";
        if (okPost(ErpFinanceSettlement.voidReceipt(companyId, 0L, vr)) == 0) {
            System.out.println("FAIL void dup first");
            return 0;
        }
        PostResultDto again = ErpFinanceSettlement.voidReceipt(companyId, 0L, vr);
        if (okPost(again) == 0) {
            System.out.println("FAIL void dup idempotent replay");
            return 0;
        }
        PaymentVoidRequest vr2 = new PaymentVoidRequest();
        vr2.paymentId = payId;
        vr2.idempotencyKey = "void-ip-dup-v2-" + companyCode;
        vr2.reason = "again";
        PostResultDto rej = ErpFinanceSettlement.voidReceipt(companyId, 0L, vr2);
        if (rej != null && rej.ok != 0) {
            System.out.println("FAIL void dup expected reject");
            return 0;
        }
        System.out.println("OK void_duplicate");
        return 1;
    }

    static int casePeriodClosed(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 50L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-closed-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        long beforeBal = bankBal();
        long beforeBcm = bcmCount("INCOMING_PAYMENT_VOID");
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        ErpDb.exec("UPDATE fiscal_periods SET status='CLOSED' WHERE company_id=?", cp);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-closed-v-" + companyCode;
        vr.reason = "closed";
        PostResultDto rej = ErpFinanceSettlement.voidReceipt(companyId, 0L, vr);
        ErpDb.exec("UPDATE fiscal_periods SET status='OPEN' WHERE company_id=?", cp);
        if (rej != null && rej.ok != 0) {
            System.out.println("FAIL void period expected reject");
            return 0;
        }
        if (rej == null || rej.message == null || rej.message.indexOf("PERIOD_CLOSED") < 0) {
            System.out.println("FAIL void period message " + (rej == null ? "" : rej.message));
            return 0;
        }
        if (bankBal() != beforeBal || bcmCount("INCOMING_PAYMENT_VOID") != beforeBcm) {
            System.out.println("FAIL void period partial write");
            return 0;
        }
        if (payStatus("incoming_payments", payId).equals(PaymentStatus.POSTED) == false) {
            System.out.println("FAIL void period status changed");
            return 0;
        }
        System.out.println("OK void_period_closed");
        return 1;
    }

    static int caseOverdraftReject(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        /* Drain bank then void a receipt → would go negative without overdraft. */
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 200L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-od-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(bankAccountId));
        ErpDb.exec("UPDATE bank_accounts SET balance=50, allow_overdraft=0 WHERE id=?", bp);
        long beforeBcm = bcmCount("INCOMING_PAYMENT_VOID");
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-od-v-" + companyCode;
        vr.reason = "od";
        PostResultDto rej = ErpFinanceSettlement.voidReceipt(companyId, 0L, vr);
        /* Restore balance to last movement (do not invent cash without bcm row). */
        ArrayList<Object> cid = new ArrayList<Object>();
        cid.add(CryptoParse.longToString(companyId));
        Object lastBal = ErpDb.queryOne(
                "SELECT balance_after FROM bank_cash_movements WHERE company_id=? ORDER BY id DESC LIMIT 1",
                cid, new LongMapper());
        if (ErpNum.asLong(lastBal) > 0L) {
            ArrayList<Object> rb = new ArrayList<Object>();
            rb.add(CryptoParse.longToString(ErpNum.asLong(lastBal)));
            rb.add(CryptoParse.longToString(bankAccountId));
            ErpDb.exec("UPDATE bank_accounts SET balance=?, allow_overdraft=0 WHERE id=?", rb);
        }
        if (rej != null && rej.ok != 0) {
            System.out.println("FAIL void overdraft expected reject");
            return 0;
        }
        if (rej == null || rej.message == null || rej.message.indexOf("overdraft") < 0) {
            System.out.println("FAIL void overdraft message " + (rej == null ? "" : rej.message));
            return 0;
        }
        if (bcmCount("INCOMING_PAYMENT_VOID") != beforeBcm) {
            System.out.println("FAIL void overdraft partial bcm");
            return 0;
        }
        if (payStatus("incoming_payments", payId).equals(PaymentStatus.POSTED) == false) {
            System.out.println("FAIL void overdraft status");
            return 0;
        }
        System.out.println("OK void_overdraft_rejected");
        return 1;
    }

    static int caseCrossCompany(int pass) {
        if (pass == 0) {
            return 0;
        }
        long now = AsyncClockNative.wallMillis();
        IncomingPaymentRequest ip = new IncomingPaymentRequest();
        ip.customerId = customerId;
        ip.paymentDate = now;
        ip.amount = 30L;
        ip.bankAccountId = bankAccountId;
        ip.idempotencyKey = "void-ip-xco-" + companyCode;
        ip.postNow = 1;
        ip.allocations = new ArrayList<Object>();
        PostResultDto r = ErpFinanceSettlement.createReceipt(companyId, 0L, ip);
        long payId = ErpFinanceSettlement.parseDocId(r.documentJson);
        PaymentVoidRequest vr = new PaymentVoidRequest();
        vr.paymentId = payId;
        vr.idempotencyKey = "void-ip-xco-v-" + companyCode;
        vr.reason = "xco";
        PostResultDto rej = ErpFinanceSettlement.voidReceipt(otherCompanyId, 0L, vr);
        if (rej != null && rej.ok != 0) {
            System.out.println("FAIL void cross-company expected reject");
            return 0;
        }
        if (payStatus("incoming_payments", payId).equals(PaymentStatus.POSTED) == false) {
            System.out.println("FAIL void cross-company mutated");
            return 0;
        }
        System.out.println("OK void_cross_company");
        return 1;
    }
}
