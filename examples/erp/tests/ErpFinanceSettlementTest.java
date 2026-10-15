/**
 * Settlement acceptance (integer cents):
 * AR 1000+600; receipt 1200 alloc 1000+200; open 400; unalloc 0;
 * receipt 500 alloc 400; unalloc 100; idempotent; reverse 200; concurrent reject.
 * Mirror AP path. Integrity + ageing.
 * @author 张晓达
 */
class ErpFinanceSettlementTest {
    static long companyId = 0L;
    static long customerId = 0L;
    static long supplierId = 0L;
    static long bankAccountId = 0L;
    static String companyCode = "F";

    static int runAll() {
        companyCode = "F" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        if (seed() == 0) {
            System.out.println("FAIL seed");
            return 0;
        }
        int pass = customerScenario();
        if (pass != 0) {
            pass = supplierScenario();
        }
        if (pass != 0) {
            try {
                pass = ErpFinanceSettlement.runIntegrityChecks(companyId);
            } catch (Exception ix) {
                System.out.println("FAIL integrity throw");
                if (ix.getMessage() != null) {
                    System.out.println(ix.getMessage());
                }
                pass = 0;
            }
        }
        if (pass != 0) {
            try {
                ArrayList<Object> ageing = ErpFinanceReports.arAgeing(companyId, AsyncClockNative.wallMillis());
                System.out.println("OK ageing_buckets partners=" + String.valueOf(ageing.size()));
            } catch (Exception ax) {
                System.out.println("FAIL ageing throw");
                if (ax.getMessage() != null) {
                    System.out.println(ax.getMessage());
                }
                pass = 0;
            }
        }
        if (pass != 0) {
            System.out.println("PASS settlement");
        } else {
            System.out.println("FAIL settlement");
        }
        return pass;
    }

    static int seed() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Finance Settlement Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));
        ArrayList<Object> cu = new ArrayList<Object>();
        cu.add(CryptoParse.longToString(companyId));
        cu.add("CUS1");
        cu.add("Customer One");
        cu.add(PartnerKind.CUSTOMER);
        cu.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", cu);
        ArrayList<Object> su = new ArrayList<Object>();
        su.add(CryptoParse.longToString(companyId));
        su.add("SUP1");
        su.add("Supplier One");
        su.add(PartnerKind.SUPPLIER);
        su.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", su);
        ErpFinanceCore.ensureSeed(companyId);
        ErpFinanceSettlement.seedBankBalance(companyId, 10000000L);
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add("CUS1");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cp, new LongMapper()));
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add("SUP1");
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

    static int customerScenario() {
        long now = AsyncClockNative.wallMillis();
        long ar1 = ErpFinanceSettlement.createOpenAr(companyId, customerId, 1000L, now, now + 86400000L, "CNY");
        long ar2 = ErpFinanceSettlement.createOpenAr(companyId, customerId, 600L, now, now + 86400000L, "CNY");
        if (ar1 == 0L || ar2 == 0L) {
            System.out.println("FAIL create AR 1000/600");
            return 0;
        }
        System.out.println("OK ar_created 1000+600");

        IncomingPaymentRequest ip1 = new IncomingPaymentRequest();
        ip1.customerId = customerId;
        ip1.paymentDate = now;
        ip1.amount = 1200L;
        ip1.bankAccountId = bankAccountId;
        ip1.idempotencyKey = "set-ip-1-" + companyCode;
        ip1.paymentMethod = "TRANSFER";
        ip1.externalRef = "BANK-001";
        ip1.postNow = 1;
        ip1.allocations = new ArrayList<Object>();
        PaymentAllocationInput a1 = new PaymentAllocationInput();
        a1.invoiceId = ar1;
        a1.allocatedAmount = 1000L;
        ip1.allocations.add(a1);
        PaymentAllocationInput a2 = new PaymentAllocationInput();
        a2.invoiceId = ar2;
        a2.allocatedAmount = 200L;
        ip1.allocations.add(a2);
        PostResultDto r1 = ErpFinanceSettlement.createReceipt(companyId, 0L, ip1);
        if (okPost(r1) == 0) {
            System.out.println("FAIL IP1 1200");
            if (r1 != null) {
                System.out.println(r1.message);
            }
            return 0;
        }
        long pay1 = ErpFinanceSettlement.parseDocId(r1.documentJson);
        if (ErpFinanceSettlement.arOpen(companyId, ar2) != 400L) {
            System.out.println("FAIL ar2 open expected 400 got "
                    + CryptoParse.longToString(ErpFinanceSettlement.arOpen(companyId, ar2)));
            return 0;
        }
        if (ErpFinanceSettlement.receiptUnallocated(companyId, pay1) != 0L) {
            System.out.println("FAIL IP1 unallocated expected 0");
            return 0;
        }
        System.out.println("OK ip1200 ar2_open=400 unalloc=0");

        IncomingPaymentRequest ip2 = new IncomingPaymentRequest();
        ip2.customerId = customerId;
        ip2.paymentDate = now;
        ip2.amount = 500L;
        ip2.bankAccountId = bankAccountId;
        ip2.idempotencyKey = "set-ip-2-" + companyCode;
        ip2.postNow = 1;
        ip2.allocations = new ArrayList<Object>();
        PaymentAllocationInput a3 = new PaymentAllocationInput();
        a3.invoiceId = ar2;
        a3.allocatedAmount = 400L;
        ip2.allocations.add(a3);
        PostResultDto r2 = ErpFinanceSettlement.createReceipt(companyId, 0L, ip2);
        if (okPost(r2) == 0) {
            System.out.println("FAIL IP2 500");
            return 0;
        }
        long pay2 = ErpFinanceSettlement.parseDocId(r2.documentJson);
        if (ErpFinanceSettlement.arOpen(companyId, ar2) != 0L) {
            System.out.println("FAIL ar2 should be settled");
            return 0;
        }
        if (ErpFinanceSettlement.receiptUnallocated(companyId, pay2) != 100L) {
            System.out.println("FAIL IP2 unalloc expected 100 got "
                    + CryptoParse.longToString(ErpFinanceSettlement.receiptUnallocated(companyId, pay2)));
            return 0;
        }
        System.out.println("OK ip500 ar2_settled unalloc=100");

        /* Idempotent replay */
        PostResultDto r1b = ErpFinanceSettlement.createReceipt(companyId, 0L, ip1);
        PostResultDto r2b = ErpFinanceSettlement.createReceipt(companyId, 0L, ip2);
        if (okPost(r1b) == 0 || okPost(r2b) == 0) {
            System.out.println("FAIL idempotent replay");
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, ar1) != 0L
                || ErpFinanceSettlement.arOpen(companyId, ar2) != 0L
                || ErpFinanceSettlement.receiptUnallocated(companyId, pay2) != 100L) {
            System.out.println("FAIL balances changed after idempotent replay");
            return 0;
        }
        System.out.println("OK idempotent_replay");

        /* Reverse the 200 allocation on ar2 from pay1 */
        long alloc200 = ErpFinanceSettlement.findActiveAllocId(companyId, pay1, ar2, "AR");
        if (alloc200 == 0L) {
            System.out.println("FAIL find alloc 200");
            return 0;
        }
        SettlementReverseRequest rev = new SettlementReverseRequest();
        rev.allocationId = alloc200;
        rev.idempotencyKey = "set-rev-200-" + companyCode;
        rev.reason = "correct partial";
        PostResultDto rr = ErpFinanceSettlement.reverseReceiptAlloc(companyId, 0L, rev);
        if (okPost(rr) == 0) {
            System.out.println("FAIL reverse 200");
            if (rr != null) {
                System.out.println(rr.message);
            }
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, ar2) != 200L) {
            System.out.println("FAIL after reverse ar2 open expected 200 got "
                    + CryptoParse.longToString(ErpFinanceSettlement.arOpen(companyId, ar2)));
            return 0;
        }
        if (ErpFinanceSettlement.receiptUnallocated(companyId, pay1) != 200L) {
            System.out.println("FAIL after reverse IP1 unalloc expected 200 got "
                    + CryptoParse.longToString(ErpFinanceSettlement.receiptUnallocated(companyId, pay1)));
            return 0;
        }
        /* Double reverse rejected */
        PostResultDto rr2 = ErpFinanceSettlement.reverseReceiptAlloc(companyId, 0L, rev);
        if (rr2 != null && rr2.ok != 0 && rr2.code != null && rr2.code.equals("OK")) {
            /* idempotent key returns same — OK */
            System.out.println("OK reverse_idempotent");
        } else {
            SettlementReverseRequest revDup = new SettlementReverseRequest();
            revDup.allocationId = alloc200;
            revDup.idempotencyKey = "set-rev-200-dup-" + companyCode;
            revDup.reason = "dup";
            PostResultDto rr3 = ErpFinanceSettlement.reverseReceiptAlloc(companyId, 0L, revDup);
            if (rr3 != null && rr3.ok != 0) {
                System.out.println("FAIL expected double-reverse reject");
                return 0;
            }
            System.out.println("OK reverse_reject_dup");
        }

        /* Concurrent-style: try over-allocate remaining open with two requests exceeding open */
        long ar3 = ErpFinanceSettlement.createOpenAr(companyId, customerId, 300L, now, now, "CNY");
        IncomingPaymentRequest ip3 = new IncomingPaymentRequest();
        ip3.customerId = customerId;
        ip3.paymentDate = now;
        ip3.amount = 300L;
        ip3.bankAccountId = bankAccountId;
        ip3.idempotencyKey = "set-ip-race-a-" + companyCode;
        ip3.postNow = 1;
        ip3.allocations = new ArrayList<Object>();
        PaymentAllocationInput ra = new PaymentAllocationInput();
        ra.invoiceId = ar3;
        ra.allocatedAmount = 300L;
        ip3.allocations.add(ra);
        PostResultDto raceA = ErpFinanceSettlement.createReceipt(companyId, 0L, ip3);
        IncomingPaymentRequest ip4 = new IncomingPaymentRequest();
        ip4.customerId = customerId;
        ip4.paymentDate = now;
        ip4.amount = 300L;
        ip4.bankAccountId = bankAccountId;
        ip4.idempotencyKey = "set-ip-race-b-" + companyCode;
        ip4.postNow = 1;
        ip4.allocations = new ArrayList<Object>();
        PaymentAllocationInput rb = new PaymentAllocationInput();
        rb.invoiceId = ar3;
        rb.allocatedAmount = 300L;
        ip4.allocations.add(rb);
        PostResultDto raceB = ErpFinanceSettlement.createReceipt(companyId, 0L, ip4);
        if (okPost(raceA) == 0) {
            System.out.println("FAIL race first alloc");
            return 0;
        }
        if (raceB != null && raceB.ok != 0) {
            System.out.println("FAIL expected over-alloc reject on second");
            return 0;
        }
        if (ErpFinanceSettlement.arOpen(companyId, ar3) != 0L) {
            System.out.println("FAIL ar3 should remain settled once");
            return 0;
        }
        System.out.println("OK concurrent_over_alloc_rejected");

        /* Cross-party reject */
        IncomingPaymentRequest bad = new IncomingPaymentRequest();
        bad.customerId = supplierId;
        bad.paymentDate = now;
        bad.amount = 50L;
        bad.bankAccountId = bankAccountId;
        bad.idempotencyKey = "set-ip-cross-" + companyCode;
        bad.postNow = 1;
        bad.allocations = new ArrayList<Object>();
        PaymentAllocationInput cx = new PaymentAllocationInput();
        cx.invoiceId = ar1;
        cx.allocatedAmount = 50L;
        bad.allocations.add(cx);
        PostResultDto cross = ErpFinanceSettlement.createReceipt(companyId, 0L, bad);
        if (cross != null && cross.ok != 0) {
            System.out.println("FAIL expected cross-party reject");
            return 0;
        }
        System.out.println("OK cross_party_reject");

        long bankAfter = ErpFinanceSettlement.bankBalance(companyId, bankAccountId);
        System.out.println("OK bank_balance=" + CryptoParse.longToString(bankAfter));
        return 1;
    }

    static int supplierScenario() {
        long now = AsyncClockNative.wallMillis();
        long ap1 = ErpFinanceSettlement.createOpenAp(companyId, supplierId, 1000L, now, now, "CNY");
        long ap2 = ErpFinanceSettlement.createOpenAp(companyId, supplierId, 600L, now, now, "CNY");
        if (ap1 == 0L || ap2 == 0L) {
            System.out.println("FAIL create AP");
            return 0;
        }
        OutgoingPaymentRequest op1 = new OutgoingPaymentRequest();
        op1.supplierId = supplierId;
        op1.paymentDate = now;
        op1.amount = 1200L;
        op1.bankAccountId = bankAccountId;
        op1.idempotencyKey = "set-op-1-" + companyCode;
        op1.postNow = 1;
        op1.allocations = new ArrayList<Object>();
        PaymentAllocationInput a1 = new PaymentAllocationInput();
        a1.invoiceId = arOrAp(ap1);
        a1.allocatedAmount = 1000L;
        op1.allocations.add(a1);
        PaymentAllocationInput a2 = new PaymentAllocationInput();
        a2.invoiceId = ap2;
        a2.allocatedAmount = 200L;
        op1.allocations.add(a2);
        PostResultDto r1 = ErpFinanceSettlement.createPayment(companyId, 0L, op1);
        if (okPost(r1) == 0) {
            System.out.println("FAIL OP1");
            if (r1 != null) {
                System.out.println(r1.message);
            }
            return 0;
        }
        long pay1 = ErpFinanceSettlement.parseDocId(r1.documentJson);
        if (ErpFinanceSettlement.apOpen(companyId, ap2) != 400L) {
            System.out.println("FAIL ap2 open expected 400");
            return 0;
        }
        OutgoingPaymentRequest op2 = new OutgoingPaymentRequest();
        op2.supplierId = supplierId;
        op2.paymentDate = now;
        op2.amount = 500L;
        op2.bankAccountId = bankAccountId;
        op2.idempotencyKey = "set-op-2-" + companyCode;
        op2.postNow = 1;
        op2.allocations = new ArrayList<Object>();
        PaymentAllocationInput a3 = new PaymentAllocationInput();
        a3.invoiceId = ap2;
        a3.allocatedAmount = 400L;
        op2.allocations.add(a3);
        if (okPost(ErpFinanceSettlement.createPayment(companyId, 0L, op2)) == 0) {
            System.out.println("FAIL OP2");
            return 0;
        }
        long alloc200 = ErpFinanceSettlement.findActiveAllocId(companyId, pay1, ap2, "AP");
        SettlementReverseRequest rev = new SettlementReverseRequest();
        rev.allocationId = alloc200;
        rev.idempotencyKey = "set-op-rev-" + companyCode;
        rev.reason = "ap reverse";
        if (okPost(ErpFinanceSettlement.reversePaymentAlloc(companyId, 0L, rev)) == 0) {
            System.out.println("FAIL AP reverse");
            return 0;
        }
        if (ErpFinanceSettlement.apOpen(companyId, ap2) != 200L) {
            System.out.println("FAIL ap2 open after reverse expected 200");
            return 0;
        }
        if (ErpFinanceSettlement.paymentUnallocated(companyId, pay1) != 200L) {
            System.out.println("FAIL OP1 unalloc expected 200");
            return 0;
        }
        System.out.println("OK supplier_partial_alloc_reverse");
        return 1;
    }

    static long arOrAp(long id) {
        return id;
    }
}
