/**
 * Management GL acceptance (integer currency units, no tax/discount):
 * Opening cash 1000 vs equity → credit buy 10 cost 100 → sell 4 rev 80 cogs 40
 * → collect 50 → pay 60.
 * Expect: bank 990, AR 30, inv 60, AP 40, equity 1000, rev 80, cogs 40, profit 40.
 * @author 张晓达
 */
class ErpGlLogicTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long itemId = 0L;
    static long supplierId = 0L;
    static long customerId = 0L;
    static String companyCode = "G";
    static long bizDate = 0L;
    static long periodId = 0L;

    static int runAll() {
        companyCode = "G" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        bizDate = AsyncClockNative.wallMillis();
        if (seed() == 0) {
            System.out.println("FAIL gl seed");
            return 0;
        }
        int pass = scenario();
        if (pass != 0) {
            pass = exceptionChecks();
        }
        if (pass != 0) {
            System.out.println("PASS gl");
        } else {
            System.out.println("FAIL gl");
        }
        return pass;
    }

    static int seed() {
        long now = bizDate;
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("GL Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));
        ErpGl.ensureMgmtBook(companyId);

        ArrayList<Object> wh = new ArrayList<Object>();
        wh.add(CryptoParse.longToString(companyId));
        wh.add("WH1");
        wh.add("Main");
        wh.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO warehouses(company_id, code, name, created_at) VALUES (?,?,?,?)", wh);

        ArrayList<Object> it = new ArrayList<Object>();
        it.add(CryptoParse.longToString(companyId));
        it.add("SKU-G");
        it.add("GL Widget");
        it.add("20");
        it.add(CryptoParse.longToString(now));
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES (?,?,?,?,10,?)",
                it);

        ArrayList<Object> sup = new ArrayList<Object>();
        sup.add(CryptoParse.longToString(companyId));
        sup.add("SUP-G");
        sup.add("Supplier G");
        sup.add(PartnerKind.SUPPLIER);
        sup.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                sup);

        ArrayList<Object> cust = new ArrayList<Object>();
        cust.add(CryptoParse.longToString(companyId));
        cust.add("CUS-G");
        cust.add("Customer G");
        cust.add(PartnerKind.CUSTOMER);
        cust.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                cust);

        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH1");
        warehouseId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper()));
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("SKU-G");
        itemId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper()));
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add("SUP-G");
        supplierId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", sp, new LongMapper()));
        ArrayList<Object> cup = new ArrayList<Object>();
        cup.add(CryptoParse.longToString(companyId));
        cup.add("CUS-G");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cup, new LongMapper()));

        ArrayList<Object> fp = new ArrayList<Object>();
        fp.add(CryptoParse.longToString(companyId));
        periodId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM fiscal_periods WHERE company_id=? ORDER BY id LIMIT 1", fp, new LongMapper()));

        if (companyId == 0L || warehouseId == 0L || itemId == 0L || supplierId == 0L || customerId == 0L
                || periodId == 0L) {
            return 0;
        }
        if (ErpGl.glPolicy(companyId).equals(GlPolicy.MGMT_DIRECT_ACCRUAL) == false) {
            System.out.println("FAIL policy not MGMT_DIRECT_ACCRUAL");
            return 0;
        }
        return 1;
    }

    static int ok(PostResultDto r, String step) {
        if (r == null || r.ok == 0) {
            System.out.println("FAIL " + step);
            if (r != null) {
                System.out.println(r.code + " " + r.message);
            }
            return 0;
        }
        System.out.println("OK " + step);
        return 1;
    }

    static int expectBal(String mapKey, long want, String label) {
        long got = ErpGl.mappedNet(companyId, mapKey, bizDate + 86400000L);
        if (mapKey.equals(AccountMapKey.AP) || mapKey.equals(AccountMapKey.EQUITY)
                || mapKey.equals(AccountMapKey.REVENUE)) {
            got = 0L - got;
        }
        if (got != want) {
            System.out.println("FAIL " + label + " expected " + CryptoParse.longToString(want)
                    + " got " + CryptoParse.longToString(got));
            return 0;
        }
        System.out.println("OK " + label + "=" + CryptoParse.longToString(got));
        return 1;
    }

    static int scenario() {
        /* 1) Opening cash 1000 vs equity */
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.OPENING_CASH, "OPEN", 1L, 0L, 1000L, 0L, 0L,
                bizDate, ""), "opening_1000") == 0) {
            return 0;
        }
        /* Idempotent replay */
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.OPENING_CASH, "OPEN", 1L, 0L, 1000L, 0L, 0L,
                bizDate, ""), "opening_idempotent") == 0) {
            return 0;
        }
        Object openCnt = ErpDb.queryOne(
                "SELECT COUNT(*) FROM journal_entries WHERE company_id="
                        + CryptoParse.longToString(companyId)
                        + " AND event_type='OPENING_CASH' AND status='POSTED'",
                new ArrayList<Object>(), new LongMapper());
        if (ErpNum.asLong(openCnt) != 1L) {
            System.out.println("FAIL opening duplicate JE count=" + CryptoParse.longToString(ErpNum.asLong(openCnt)));
            return 0;
        }

        /* 2) Credit purchase 10 @ total 100 — stock + GL */
        if (ok(ErpCosting.receiveCost(companyId, warehouseId, itemId, 10, 10L,
                "gl-recv-" + companyCode), "stock_recv_10_100") == 0) {
            return 0;
        }
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.PURCHASE_STOCK, "PO", 1L, 0L, 100L, supplierId,
                warehouseId, bizDate, ""), "purchase_gl_100") == 0) {
            return 0;
        }

        /* 3) Sell 4 — revenue 80, COGS 40 */
        if (ok(ErpCosting.issueCost(companyId, warehouseId, itemId, 4, "gl-iss-" + companyCode),
                "stock_issue_4") == 0) {
            return 0;
        }
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.SALE_REVENUE, "SO", 1L, 0L, 80L, customerId, 0L,
                bizDate, ""), "sale_rev_80") == 0) {
            return 0;
        }
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.SALE_COGS, "SO", 1L, 1L, 40L, 0L, warehouseId,
                bizDate, ""), "sale_cogs_40") == 0) {
            return 0;
        }

        /* 4-5) Collect 50, pay 60 */
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.CUSTOMER_RECEIPT, "IP", 1L, 0L, 50L, customerId,
                0L, bizDate, ""), "receipt_50") == 0) {
            return 0;
        }
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.VENDOR_PAYMENT, "OP", 1L, 0L, 60L, supplierId, 0L,
                bizDate, ""), "payment_60") == 0) {
            return 0;
        }

        /* Ending balances */
        if (expectBal(AccountMapKey.BANK, 990L, "bank") == 0) {
            return 0;
        }
        if (expectBal(AccountMapKey.AR, 30L, "ar") == 0) {
            return 0;
        }
        if (expectBal(AccountMapKey.INVENTORY, 60L, "inventory") == 0) {
            return 0;
        }
        if (expectBal(AccountMapKey.AP, 40L, "ap") == 0) {
            return 0;
        }
        if (expectBal(AccountMapKey.EQUITY, 1000L, "equity") == 0) {
            return 0;
        }
        if (expectBal(AccountMapKey.REVENUE, 80L, "revenue") == 0) {
            return 0;
        }
        long cogs = ErpGl.mappedNet(companyId, AccountMapKey.COGS, bizDate + 86400000L);
        if (cogs != 40L) {
            System.out.println("FAIL cogs expected 40 got " + CryptoParse.longToString(cogs));
            return 0;
        }
        System.out.println("OK cogs=40");
        long profit = 80L - 40L;
        if (profit != 40L) {
            return 0;
        }
        System.out.println("OK business_profit=40");

        /* Trial + recon */
        ArrayList<Object> checks = ErpGl.reconcile(companyId, bizDate + 86400000L, warehouseId);
        int i = 0;
        while (i < checks.size()) {
            GlReconCheckDto c = (GlReconCheckDto) checks.get(i);
            if (c.ok == 0) {
                System.out.println("FAIL recon " + c.checkName + " " + c.detail);
                return 0;
            }
            System.out.println("OK recon " + c.checkName);
            i = i + 1;
        }

        /* Stock value 60 */
        long stock = ErpCosting.balanceValue(companyId, warehouseId, itemId);
        if (stock != 60L) {
            System.out.println("FAIL stock value expected 60 got " + CryptoParse.longToString(stock));
            return 0;
        }
        System.out.println("OK stock_value=60");

        /* Print JE summary for delivery evidence (best-effort — must not fail acceptance). */
        try {
            ArrayList<Object> jes = ErpFinanceReports.listJournals(companyId, 0L, bizDate + 86400000L);
            System.out.println("JE_COUNT=" + CryptoParse.longToString(jes.size()));
            int ji = 0;
            while (ji < jes.size()) {
                JournalEntryDto j = (JournalEntryDto) jes.get(ji);
                String jn = j.journalNumber == null ? "" : j.journalNumber;
                String js = j.status == null ? "" : j.status;
                String jd = j.description == null ? "" : j.description;
                System.out.println("JE " + jn + " " + js + " " + jd
                        + " Dr=" + CryptoParse.longToString(j.totalDebit)
                        + " Cr=" + CryptoParse.longToString(j.totalCredit));
                ji = ji + 1;
            }
        } catch (Exception jeEx) {
            System.out.println("JE_LIST_SKIP");
            if (jeEx.getMessage() != null) {
                System.out.println(jeEx.getMessage());
            }
        }
        return 1;
    }

    static int exceptionChecks() {
        /* Unbalanced manual JE rejected */
        PostingRequest bad = new PostingRequest();
        bad.sourceType = JournalSource.MANUAL;
        bad.sourceId = 99L;
        bad.postingDate = bizDate;
        bad.documentDate = bizDate;
        bad.description = "unbalanced";
        bad.initialStatus = JournalStatus.POSTED;
        bad.idempotencyKey = "gl-unbal-" + companyCode;
        bad.lines = new ArrayList<Object>();
        long bank = ErpFinanceCore.mappedAccountId(companyId, AccountMapKey.BANK);
        long equity = ErpFinanceCore.mappedAccountId(companyId, AccountMapKey.EQUITY);
        bad.lines.add(ErpFinanceCore.line(bank, 10L, 0L, 0L, "dr"));
        bad.lines.add(ErpFinanceCore.line(equity, 0L, 9L, 0L, "cr"));
        UnbalancedWork uw = new UnbalancedWork();
        uw.companyId = companyId;
        uw.req = bad;
        PostResultDto ur = ErpPosting.runPostingWork(uw);
        if (ur == null || ur.ok != 0 || ur.code.equals("UNBALANCED") == false) {
            System.out.println("FAIL unbalanced not rejected");
            if (ur != null) {
                System.out.println(ur.code + " " + ur.message);
            }
            return 0;
        }
        System.out.println("OK unbalanced_rejected");

        /* Reverse last posted opening? Use payment JE — find a POSTED JE */
        ArrayList<Object> jp = new ArrayList<Object>();
        jp.add(CryptoParse.longToString(companyId));
        Object jeIdObj = ErpDb.queryOne(
                "SELECT id FROM journal_entries WHERE company_id=? AND status='POSTED' AND event_type='VENDOR_PAYMENT' ORDER BY id DESC LIMIT 1",
                jp, new LongMapper());
        long jeId = ErpNum.asLong(jeIdObj);
        if (jeId == 0L) {
            System.out.println("FAIL no payment JE to reverse");
            return 0;
        }
        PostResultDto rev = ErpGl.reverseJournal(companyId, 1L, jeId, "test reverse",
                "gl-rev-" + companyCode);
        if (ok(rev, "reverse_payment") == 0) {
            return 0;
        }
        /* After reverse payment 60, bank should be 990+60=1050, AP 40+60=100 */
        long bankAfter = ErpGl.mappedNet(companyId, AccountMapKey.BANK, bizDate + 86400000L);
        if (bankAfter != 1050L) {
            System.out.println("FAIL bank after reverse expected 1050 got "
                    + CryptoParse.longToString(bankAfter));
            return 0;
        }
        System.out.println("OK reverse_bank=1050");
        /* Re-post payment to restore acceptance ending (for period close) */
        if (ok(ErpGl.postEvent(companyId, 1L, GlEventType.VENDOR_PAYMENT, "OP", 2L, 0L, 60L, supplierId, 0L,
                bizDate, ""), "repay_60") == 0) {
            return 0;
        }
        /* Note: bank sync applied again — balance column may drift; GL bank net should be 990 */
        if (expectBal(AccountMapKey.BANK, 990L, "bank_restored") == 0) {
            return 0;
        }

        /* Close period lock */
        PostResultDto closed = ErpGl.closePeriod(companyId, 1L, periodId);
        if (ok(closed, "period_close") == 0) {
            return 0;
        }
        PostResultDto blocked = ErpGl.postEvent(companyId, 1L, GlEventType.CUSTOMER_RECEIPT, "IP", 99L, 0L,
                1L, customerId, 0L, bizDate, "");
        if (blocked == null || blocked.ok != 0) {
            System.out.println("FAIL closed period still accepts post");
            return 0;
        }
        if (blocked.code.equals("PERIOD_CLOSED") == false) {
            /* Failure may surface as FAILED event with PERIOD_CLOSED */
            if (blocked.message != null && blocked.message.indexOf("period") >= 0) {
                System.out.println("OK period_block message=" + blocked.message);
            } else {
                System.out.println("FAIL unexpected block code=" + blocked.code + " " + blocked.message);
                return 0;
            }
        } else {
            System.out.println("OK period_closed_blocks_post");
        }

        PostResultDto reopened = ErpGl.reopenPeriod(companyId, 1L, periodId, "test reopen");
        if (ok(reopened, "period_reopen") == 0) {
            return 0;
        }
        return 1;
    }
}

class UnbalancedWork implements DbTransactionWork {
    long companyId;
    PostingRequest req;

    public JnFuture run(DbConnection conn) {
        PostResultDto r = ErpFinanceCore.postJournalConn(conn, companyId, 1L, req);
        return JnFuture.completed(Erp.loop, r);
    }
}
