/**
 * Central management GL: policy, event→voucher posting, lifecycle helpers,
 * books reconciliation, period lock checks.
 * Amounts = integer currency units (exact equality; no float tolerance).
 * Scope: internal management accrual — not statutory/tax reporting.
 * @author 张晓达
 */
class ErpGl {
    static String glPolicy(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT gl_policy FROM companies WHERE id=?", p, new StringMapper());
        if (v == null) {
            return GlPolicy.PROVISIONAL_CLEARING;
        }
        String s = (String) v;
        if (s == null || s.length() == 0) {
            return GlPolicy.PROVISIONAL_CLEARING;
        }
        return s;
    }

    static void setGlPolicy(long companyId, String policy) {
        if (policy == null || policy.length() == 0) {
            policy = GlPolicy.PROVISIONAL_CLEARING;
        }
        ErpDb.exec(
                "UPDATE companies SET gl_policy='" + policy + "' WHERE id="
                        + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
    }

    static void ensureMgmtBook(long companyId) {
        ErpFinanceCore.ensureSeed(companyId);
        ErpFinanceCore.ensureEquityMapping(companyId);
        ErpFinanceCore.applyAccountMeta(companyId);
        setGlPolicy(companyId, GlPolicy.MGMT_DIRECT_ACCRUAL);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        try {
            ErpDb.exec("UPDATE companies SET functional_currency='CNY' WHERE id=?", p);
        } catch (Exception ignored) {
        }
    }

    /**
     * Enqueue + consume in one DB transaction (same-DB atomic).
     * Idempotent on (company, event_type, source_type, source_id, source_line_id).
     */
    static PostResultDto postEvent(long companyId, long userId, String eventType, String sourceType,
            long sourceId, long sourceLineId, long amount, long partnerId, long warehouseId, long bizDate,
            String payloadJson) {
        GlEventWork w = new GlEventWork();
        w.companyId = companyId;
        w.userId = userId;
        w.eventType = eventType;
        w.sourceType = sourceType;
        w.sourceId = sourceId;
        w.sourceLineId = sourceLineId;
        w.amount = amount;
        w.partnerId = partnerId;
        w.warehouseId = warehouseId;
        w.bizDate = bizDate;
        if (payloadJson == null) {
            w.payloadJson = "";
        } else {
            w.payloadJson = payloadJson;
        }
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto approveJournal(long companyId, long userId, long journalId) {
        GlApproveWork w = new GlApproveWork();
        w.companyId = companyId;
        w.userId = userId;
        w.journalId = journalId;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto postApprovedJournal(long companyId, long userId, long journalId) {
        GlPostApprovedWork w = new GlPostApprovedWork();
        w.companyId = companyId;
        w.userId = userId;
        w.journalId = journalId;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto rejectJournal(long companyId, long userId, long journalId, String reason) {
        GlRejectWork w = new GlRejectWork();
        w.companyId = companyId;
        w.userId = userId;
        w.journalId = journalId;
        w.reason = reason;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto reverseJournal(long companyId, long userId, long journalId, String reason,
            String idempotencyKey) {
        GlReverseWork w = new GlReverseWork();
        w.companyId = companyId;
        w.userId = userId;
        w.journalId = journalId;
        w.reason = reason;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto closePeriod(long companyId, long userId, long periodId) {
        GlClosePeriodWork w = new GlClosePeriodWork();
        w.companyId = companyId;
        w.userId = userId;
        w.periodId = periodId;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto reopenPeriod(long companyId, long userId, long periodId, String reason) {
        GlReopenPeriodWork w = new GlReopenPeriodWork();
        w.companyId = companyId;
        w.userId = userId;
        w.periodId = periodId;
        w.reason = reason;
        return ErpPosting.runPostingWork(w);
    }

    /** Account net balance from POSTED lines (debit − credit). */
    static long accountNet(long companyId, long accountId, long asOfDate) {
        String sql = "SELECT COALESCE(SUM(l.debit_amount),0)-COALESCE(SUM(l.credit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND l.account_id=" + CryptoParse.longToString(accountId)
                + " AND e.status='" + JournalStatus.POSTED + "'"
                + " AND e.posting_date<=" + CryptoParse.longToString(asOfDate);
        Object v = ErpDb.queryOne(sql, new ArrayList<Object>(), new LongMapper());
        return ErpNum.asLong(v);
    }

    static long mappedNet(long companyId, String mapKey, long asOfDate) {
        long aid = ErpFinanceCore.mappedAccountId(companyId, mapKey);
        if (aid == 0L) {
            return 0L;
        }
        return accountNet(companyId, aid, asOfDate);
    }

    static long partnerSubledger(long companyId, String mapKey, long partnerId, long asOfDate) {
        long aid = ErpFinanceCore.mappedAccountId(companyId, mapKey);
        if (aid == 0L || partnerId == 0L) {
            return 0L;
        }
        String sql = "SELECT COALESCE(SUM(l.debit_amount),0)-COALESCE(SUM(l.credit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND l.account_id=" + CryptoParse.longToString(aid)
                + " AND l.business_partner_id=" + CryptoParse.longToString(partnerId)
                + " AND e.status='" + JournalStatus.POSTED + "'"
                + " AND e.posting_date<=" + CryptoParse.longToString(asOfDate);
        Object v = ErpDb.queryOne(sql, new ArrayList<Object>(), new LongMapper());
        return ErpNum.asLong(v);
    }

    static ArrayList<Object> listEvents(long companyId, String statusFilter) {
        String sql = "SELECT id, event_type, source_type, source_id, source_line_id, status, journal_entry_id, amount, partner_id, warehouse_id, biz_date, error_code, error_message FROM gl_posting_events WHERE company_id="
                + CryptoParse.longToString(companyId);
        if (statusFilter != null && statusFilter.length() > 0) {
            sql = sql + " AND status='" + statusFilter + "'";
        }
        sql = sql + " ORDER BY id DESC LIMIT 500";
        DbQueryResult r = ErpDb.query(sql, new ArrayList<Object>());
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            GlPostingEventDto d = new GlPostingEventDto();
            d.id = row.getLong(0);
            d.eventType = row.getString(1);
            d.sourceType = row.getString(2);
            d.sourceId = row.getLong(3);
            d.sourceLineId = row.getLong(4);
            d.status = row.getString(5);
            d.journalEntryId = row.getLong(6);
            d.amount = row.getLong(7);
            d.partnerId = row.getLong(8);
            d.warehouseId = row.getLong(9);
            d.bizDate = row.getLong(10);
            d.errorCode = row.getString(11);
            d.errorMessage = row.getString(12);
            out.add(d);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> reconcile(long companyId, long asOfDate, long warehouseId) {
        ArrayList<Object> out = new ArrayList<Object>();
        out.add(checkTrial(companyId, asOfDate));
        out.add(checkArControl(companyId, asOfDate));
        out.add(checkApControl(companyId, asOfDate));
        out.add(checkInventory(companyId, asOfDate, warehouseId));
        out.add(checkBank(companyId, asOfDate));
        out.add(checkPendingEvents(companyId));
        return out;
    }

    static GlReconCheckDto checkTrial(long companyId, long asOfDate) {
        GlReconCheckDto c = new GlReconCheckDto();
        c.checkName = "TRIAL_BALANCE";
        String sql = "SELECT COALESCE(SUM(l.debit_amount),0), COALESCE(SUM(l.credit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND e.status='" + JournalStatus.POSTED + "'"
                + " AND e.posting_date<=" + CryptoParse.longToString(asOfDate);
        DbQueryResult r = ErpDb.query(sql, new ArrayList<Object>());
        long dr = 0L;
        long cr = 0L;
        if (r.rows.size() > 0) {
            dr = r.rows.get(0).getLong(0);
            cr = r.rows.get(0).getLong(1);
        }
        c.expectedAmount = dr;
        c.actualAmount = cr;
        c.diffAmount = dr - cr;
        if (dr == cr) {
            c.ok = 1;
            c.detail = "balanced debit=credit=" + CryptoParse.longToString(dr);
        } else {
            c.ok = 0;
            c.detail = "unbalanced debit=" + CryptoParse.longToString(dr) + " credit="
                    + CryptoParse.longToString(cr);
        }
        return c;
    }

    static GlReconCheckDto checkArControl(long companyId, long asOfDate) {
        GlReconCheckDto c = new GlReconCheckDto();
        c.checkName = "AR_CONTROL_VS_SUBLEDGER";
        long control = mappedNet(companyId, AccountMapKey.AR, asOfDate);
        long aid = ErpFinanceCore.mappedAccountId(companyId, AccountMapKey.AR);
        String sql = "SELECT COALESCE(SUM(l.debit_amount),0)-COALESCE(SUM(l.credit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND l.account_id=" + CryptoParse.longToString(aid)
                + " AND COALESCE(l.business_partner_id,0)>0"
                + " AND e.status='" + JournalStatus.POSTED + "'"
                + " AND e.posting_date<=" + CryptoParse.longToString(asOfDate);
        long sub = ErpNum.asLong(ErpDb.queryOne(sql, new ArrayList<Object>(), new LongMapper()));
        c.expectedAmount = control;
        c.actualAmount = sub;
        c.diffAmount = control - sub;
        if (control == sub) {
            c.ok = 1;
            c.detail = "AR control=subledger=" + CryptoParse.longToString(control);
        } else {
            c.ok = 0;
            c.detail = "AR control " + CryptoParse.longToString(control) + " != partner lines "
                    + CryptoParse.longToString(sub);
        }
        return c;
    }

    static GlReconCheckDto checkApControl(long companyId, long asOfDate) {
        GlReconCheckDto c = new GlReconCheckDto();
        c.checkName = "AP_CONTROL_VS_SUBLEDGER";
        /* AP credit-normal: liability balance = credit − debit = −net */
        long controlNet = mappedNet(companyId, AccountMapKey.AP, asOfDate);
        long control = 0L - controlNet;
        long aid = ErpFinanceCore.mappedAccountId(companyId, AccountMapKey.AP);
        String sql = "SELECT COALESCE(SUM(l.credit_amount),0)-COALESCE(SUM(l.debit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND l.account_id=" + CryptoParse.longToString(aid)
                + " AND COALESCE(l.business_partner_id,0)>0"
                + " AND e.status='" + JournalStatus.POSTED + "'"
                + " AND e.posting_date<=" + CryptoParse.longToString(asOfDate);
        long sub = ErpNum.asLong(ErpDb.queryOne(sql, new ArrayList<Object>(), new LongMapper()));
        c.expectedAmount = control;
        c.actualAmount = sub;
        c.diffAmount = control - sub;
        if (control == sub) {
            c.ok = 1;
            c.detail = "AP control=subledger=" + CryptoParse.longToString(control);
        } else {
            c.ok = 0;
            c.detail = "AP control " + CryptoParse.longToString(control) + " != partner lines "
                    + CryptoParse.longToString(sub);
        }
        return c;
    }

    static GlReconCheckDto checkInventory(long companyId, long asOfDate, long warehouseId) {
        GlReconCheckDto c = new GlReconCheckDto();
        c.checkName = "INVENTORY_VS_COSTED_STOCK";
        long glInv = mappedNet(companyId, AccountMapKey.INVENTORY, asOfDate);
        long stock = 0L;
        if (warehouseId > 0L) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            p.add(CryptoParse.longToString(warehouseId));
            Object v = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(value_amount),0) FROM inventory_balances WHERE company_id=? AND warehouse_id=?",
                    p, new LongMapper());
            stock = ErpNum.asLong(v);
        } else {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            Object v = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(value_amount),0) FROM inventory_balances WHERE company_id=?",
                    p, new LongMapper());
            stock = ErpNum.asLong(v);
        }
        c.expectedAmount = glInv;
        c.actualAmount = stock;
        c.diffAmount = glInv - stock;
        if (glInv == stock) {
            c.ok = 1;
            c.detail = "inventory GL=stock value=" + CryptoParse.longToString(glInv);
        } else {
            c.ok = 0;
            c.detail = "inventory GL " + CryptoParse.longToString(glInv) + " != stock "
                    + CryptoParse.longToString(stock);
        }
        return c;
    }

    static GlReconCheckDto checkBank(long companyId, long asOfDate) {
        GlReconCheckDto c = new GlReconCheckDto();
        c.checkName = "BANK_VS_CASH_MOVEMENTS";
        long glBank = mappedNet(companyId, AccountMapKey.BANK, asOfDate);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object bal = ErpDb.queryOne(
                "SELECT COALESCE(SUM(balance),0) FROM bank_accounts WHERE company_id=? AND active=1", p,
                new LongMapper());
        long bankBal = ErpNum.asLong(bal);
        /* Prefer cash movements sum when table present */
        long mov = bankBal;
        try {
            Object m = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(CASE WHEN movement_type IN ('RECEIPT','REFUND_IN') THEN amount ELSE 0-amount END),0) FROM bank_cash_movements WHERE company_id="
                            + CryptoParse.longToString(companyId)
                            + " AND biz_date<=" + CryptoParse.longToString(asOfDate),
                    new ArrayList<Object>(), new LongMapper());
            /* If movements exist and bank balance is maintained, compare GL to bank_accounts.balance */
            mov = bankBal;
            if (m != null) {
                /* keep bankBal as actual for books that sync balance column */
            }
        } catch (Exception ignored) {
        }
        c.expectedAmount = glBank;
        c.actualAmount = mov;
        c.diffAmount = glBank - mov;
        if (glBank == mov) {
            c.ok = 1;
            c.detail = "bank GL=bank_accounts.balance=" + CryptoParse.longToString(glBank);
        } else {
            c.ok = 0;
            c.detail = "bank GL " + CryptoParse.longToString(glBank) + " != bank balance "
                    + CryptoParse.longToString(mov)
                    + " (sync bank_accounts.balance or post cash via ErpGl)";
        }
        return c;
    }

    static GlReconCheckDto checkPendingEvents(long companyId) {
        GlReconCheckDto c = new GlReconCheckDto();
        c.checkName = "PENDING_OR_FAILED_EVENTS";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object n = ErpDb.queryOne(
                "SELECT COUNT(*) FROM gl_posting_events WHERE company_id=? AND status IN ('PENDING','FAILED')",
                p, new LongMapper());
        long cnt = ErpNum.asLong(n);
        c.expectedAmount = 0L;
        c.actualAmount = cnt;
        c.diffAmount = cnt;
        if (cnt == 0L) {
            c.ok = 1;
            c.detail = "no pending/failed GL events";
        } else {
            c.ok = 0;
            c.detail = "pending_or_failed=" + CryptoParse.longToString(cnt);
        }
        return c;
    }

    static ArrayList<Object> closeBlockers(long companyId, long periodId) {
        ArrayList<Object> out = new ArrayList<Object>();
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(periodId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult pr = ErpDb.query(
                "SELECT start_date, end_date, status FROM fiscal_periods WHERE id=? AND company_id=?", pp);
        if (pr.rows.size() == 0) {
            out.add("PERIOD_NOT_FOUND");
            return out;
        }
        long start = pr.rows.get(0).getLong(0);
        long end = pr.rows.get(0).getLong(1);
        ArrayList<Object> ep = new ArrayList<Object>();
        ep.add(CryptoParse.longToString(companyId));
        Object pending = ErpDb.queryOne(
                "SELECT COUNT(*) FROM gl_posting_events WHERE company_id=? AND status IN ('PENDING','FAILED')",
                ep, new LongMapper());
        if (ErpNum.asLong(pending) > 0L) {
            out.add("PENDING_EVENTS:" + CryptoParse.longToString(ErpNum.asLong(pending)));
        }
        String unposted = "SELECT COUNT(*) FROM journal_entries WHERE company_id="
                + CryptoParse.longToString(companyId)
                + " AND status IN ('DRAFT','APPROVED')"
                + " AND posting_date>=" + CryptoParse.longToString(start)
                + " AND posting_date<=" + CryptoParse.longToString(end);
        Object up = ErpDb.queryOne(unposted, new ArrayList<Object>(), new LongMapper());
        if (ErpNum.asLong(up) > 0L) {
            out.add("UNPOSTED_JOURNALS:" + CryptoParse.longToString(ErpNum.asLong(up)));
        }
        GlReconCheckDto trial = checkTrial(companyId, end);
        if (trial.ok == 0) {
            out.add("TRIAL_UNBALANCED");
        }
        GlReconCheckDto ar = checkArControl(companyId, end);
        if (ar.ok == 0) {
            out.add("AR_MISMATCH");
        }
        GlReconCheckDto ap = checkApControl(companyId, end);
        if (ap.ok == 0) {
            out.add("AP_MISMATCH");
        }
        return out;
    }

    /** Build MGMT_DIRECT_ACCRUAL lines for a known event type. */
    static PostResultDto buildAndPostMappedConn(DbConnection conn, long companyId, long userId,
            String eventType, String sourceType, long sourceId, long amount, long partnerId,
            long warehouseId, long bizDate, long eventRowId) {
        PostResultDto bad = new PostResultDto();
        bad.ok = 0;
        if (amount <= 0L) {
            bad.code = "BAD_AMOUNT";
            bad.message = "amount must be > 0";
            return bad;
        }
        String policy = "";
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        Object pol = ErpDb.queryOneConn(conn, "SELECT gl_policy FROM companies WHERE id=?", cp,
                new StringMapper());
        if (pol != null) {
            policy = (String) pol;
        }
        if (policy.equals(GlPolicy.MGMT_DIRECT_ACCRUAL) == false) {
            bad.code = "POLICY";
            bad.message = "event mapper requires MGMT_DIRECT_ACCRUAL; current=" + policy
                    + " (PROVISIONAL_CLEARING uses GR_CLEARING path; formal confirm PENDING_CONFIG)";
            return bad;
        }
        long bank = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.BANK);
        long ar = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AR);
        long inv = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.INVENTORY);
        long ap = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AP);
        long equity = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.EQUITY);
        long rev = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.REVENUE);
        long cogs = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.COGS);
        PostingRequest req = new PostingRequest();
        req.sourceType = JournalSource.GL_EVENT;
        req.sourceId = eventRowId;
        req.postingDate = bizDate;
        req.documentDate = bizDate;
        req.eventType = eventType;
        req.initialStatus = JournalStatus.POSTED;
        req.idempotencyKey = "gl-evt-" + CryptoParse.longToString(companyId) + "-" + eventType + "-"
                + sourceType + "-" + CryptoParse.longToString(sourceId) + "-"
                + CryptoParse.longToString(0L);
        req.lines = new ArrayList<Object>();
        if (eventType.equals(GlEventType.OPENING_CASH)) {
            req.description = "Opening cash vs equity";
            req.sourceType = JournalSource.OPENING;
            req.lines.add(ErpFinanceCore.line(bank, amount, 0L, 0L, "Opening bank"));
            req.lines.add(ErpFinanceCore.line(equity, 0L, amount, 0L, "Opening equity"));
        } else if (eventType.equals(GlEventType.PURCHASE_STOCK)) {
            if (partnerId <= 0L) {
                bad.code = "PARTNER_REQUIRED";
                bad.message = "supplier required for PURCHASE_STOCK";
                return bad;
            }
            req.description = "Purchase stock (mgmt direct AP)";
            req.lines.add(ErpFinanceCore.lineWh(inv, amount, 0L, 0L, warehouseId, "Inventory"));
            req.lines.add(ErpFinanceCore.line(ap, 0L, amount, partnerId, "AP"));
        } else if (eventType.equals(GlEventType.SALE_REVENUE)) {
            if (partnerId <= 0L) {
                bad.code = "PARTNER_REQUIRED";
                bad.message = "customer required for SALE_REVENUE";
                return bad;
            }
            req.description = "Sales revenue (mgmt direct AR)";
            req.lines.add(ErpFinanceCore.line(ar, amount, 0L, partnerId, "AR"));
            req.lines.add(ErpFinanceCore.line(rev, 0L, amount, 0L, "Revenue"));
        } else if (eventType.equals(GlEventType.SALE_COGS)) {
            req.description = "Sales COGS";
            req.lines.add(ErpFinanceCore.line(cogs, amount, 0L, 0L, "COGS"));
            req.lines.add(ErpFinanceCore.lineWh(inv, 0L, amount, 0L, warehouseId, "Inventory relief"));
        } else if (eventType.equals(GlEventType.CUSTOMER_RECEIPT)) {
            if (partnerId <= 0L) {
                bad.code = "PARTNER_REQUIRED";
                bad.message = "customer required for CUSTOMER_RECEIPT";
                return bad;
            }
            req.description = "Customer receipt";
            req.lines.add(ErpFinanceCore.line(bank, amount, 0L, 0L, "Bank"));
            req.lines.add(ErpFinanceCore.line(ar, 0L, amount, partnerId, "AR relieve"));
        } else if (eventType.equals(GlEventType.VENDOR_PAYMENT)) {
            if (partnerId <= 0L) {
                bad.code = "PARTNER_REQUIRED";
                bad.message = "supplier required for VENDOR_PAYMENT";
                return bad;
            }
            req.description = "Vendor payment";
            req.lines.add(ErpFinanceCore.line(ap, amount, 0L, partnerId, "AP relieve"));
            req.lines.add(ErpFinanceCore.line(bank, 0L, amount, 0L, "Bank"));
        } else {
            bad.code = "UNKNOWN_EVENT";
            bad.message = "no mapping for event " + eventType;
            return bad;
        }
        return ErpFinanceCore.postJournalConn(conn, companyId, userId, req);
    }

    static void syncBankBalanceConn(DbConnection conn, long companyId, long delta) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object bankId = ErpDb.queryOneConn(conn,
                "SELECT id FROM bank_accounts WHERE company_id=? ORDER BY id LIMIT 1", p, new LongMapper());
        if (ErpNum.asLong(bankId) == 0L) {
            return;
        }
        ErpDb.execConn(conn,
                "UPDATE bank_accounts SET balance=balance+(" + CryptoParse.longToString(delta)
                        + ") WHERE id=" + CryptoParse.longToString(ErpNum.asLong(bankId)),
                new ArrayList<Object>());
    }

    static long parseJeId(String documentJson) {
        if (documentJson == null) {
            return 0L;
        }
        int idx = documentJson.indexOf("\"id\":");
        if (idx < 0) {
            return 0L;
        }
        int j = idx + 5;
        String n = "";
        while (j < documentJson.length()) {
            int c = documentJson.charAt(j);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            j = j + 1;
        }
        if (n.length() == 0) {
            return 0L;
        }
        return ErpNum.parseLong(n);
    }
}

class GlEventWork implements DbTransactionWork {
    long companyId;
    long userId;
    String eventType;
    String sourceType;
    long sourceId;
    long sourceLineId;
    long amount;
    long partnerId;
    long warehouseId;
    long bizDate;
    String payloadJson;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        if (bizDate <= 0L) {
            bizDate = now;
        }
        ArrayList<Object> find = new ArrayList<Object>();
        find.add(CryptoParse.longToString(companyId));
        find.add(eventType);
        find.add(sourceType);
        find.add(CryptoParse.longToString(sourceId));
        find.add(CryptoParse.longToString(sourceLineId));
        DbQueryResult existing = ErpDb.queryConn(conn,
                "SELECT id, status, journal_entry_id, error_code, error_message FROM gl_posting_events WHERE company_id=? AND event_type=? AND source_type=? AND source_id=? AND source_line_id=?",
                find);
        if (existing.rows.size() > 0) {
            Row er = existing.rows.get(0);
            String st = er.getString(1);
            if (st.equals(GlEventStatus.POSTED)) {
                PostResultDto ok = new PostResultDto();
                ok.ok = 1;
                ok.code = "OK";
                ok.message = "idempotent";
                ok.documentJson = "{\"eventId\":" + ErpJson.num(er.getLong(0))
                        + ",\"journalEntryId\":" + ErpJson.num(er.getLong(2))
                        + ",\"status\":" + ErpJson.str(st) + ",\"idempotent\":1}";
                return JnFuture.completed(Erp.loop, ok);
            }
            if (st.equals(GlEventStatus.FAILED)) {
                /* Allow retry of failed event in-place */
                long eventId = er.getLong(0);
                PostResultDto posted = ErpGl.buildAndPostMappedConn(conn, companyId, userId, eventType,
                        sourceType, sourceId, amount, partnerId, warehouseId, bizDate, eventId);
                if (posted.ok == 0) {
                    ErpDb.execConn(conn,
                            "UPDATE gl_posting_events SET status='FAILED', error_code='" + posted.code
                                    + "', error_message='" + posted.message
                                    + "', retry_count=retry_count+1, updated_at="
                                    + CryptoParse.longToString(now)
                                    + " WHERE id=" + CryptoParse.longToString(eventId),
                            new ArrayList<Object>());
                    return JnFuture.completed(Erp.loop, posted);
                }
                long jeId = ErpGl.parseJeId(posted.documentJson);
                ErpDb.execConn(conn,
                        "UPDATE gl_posting_events SET status='POSTED', journal_entry_id="
                                + CryptoParse.longToString(jeId)
                                + ", error_code='', error_message='', posted_at="
                                + CryptoParse.longToString(now)
                                + ", updated_at=" + CryptoParse.longToString(now)
                                + " WHERE id=" + CryptoParse.longToString(eventId),
                        new ArrayList<Object>());
                applyCashSideEffect(conn, eventType, amount);
                posted.documentJson = "{\"eventId\":" + ErpJson.num(eventId)
                        + ",\"journalEntryId\":" + ErpJson.num(jeId)
                        + ",\"status\":\"POSTED\"}";
                return JnFuture.completed(Erp.loop, posted);
            }
        }
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(companyId));
        ins.add(eventType);
        ins.add(sourceType);
        ins.add(CryptoParse.longToString(sourceId));
        ins.add(CryptoParse.longToString(sourceLineId));
        ins.add(GlEventStatus.PENDING);
        ins.add(CryptoParse.longToString(amount));
        ins.add(CryptoParse.longToString(partnerId));
        ins.add(CryptoParse.longToString(warehouseId));
        ins.add(CryptoParse.longToString(bizDate));
        ins.add(payloadJson);
        ins.add(CryptoParse.longToString(now));
        ins.add(CryptoParse.longToString(now));
        long eventId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO gl_posting_events(company_id, event_type, source_type, source_id, source_line_id, status, amount, partner_id, warehouse_id, biz_date, payload_json, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ins);
        PostResultDto posted = ErpGl.buildAndPostMappedConn(conn, companyId, userId, eventType, sourceType,
                sourceId, amount, partnerId, warehouseId, bizDate, eventId);
        if (posted.ok == 0) {
            ErpDb.execConn(conn,
                    "UPDATE gl_posting_events SET status='FAILED', error_code='" + posted.code
                            + "', error_message='" + posted.message + "', updated_at="
                            + CryptoParse.longToString(now)
                            + " WHERE id=" + CryptoParse.longToString(eventId),
                    new ArrayList<Object>());
            /* Event row remains FAILED — never pretend posted */
            return JnFuture.completed(Erp.loop, posted);
        }
        long jeId = ErpGl.parseJeId(posted.documentJson);
        ErpDb.execConn(conn,
                "UPDATE gl_posting_events SET status='POSTED', journal_entry_id="
                        + CryptoParse.longToString(jeId)
                        + ", posted_at=" + CryptoParse.longToString(now)
                        + ", updated_at=" + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(eventId),
                new ArrayList<Object>());
        applyCashSideEffect(conn, eventType, amount);
        posted.documentJson = "{\"eventId\":" + ErpJson.num(eventId)
                + ",\"journalEntryId\":" + ErpJson.num(jeId)
                + ",\"status\":\"POSTED\"}";
        return JnFuture.completed(Erp.loop, posted);
    }

    void applyCashSideEffect(DbConnection conn, String eventType, long amount) {
        if (eventType.equals(GlEventType.OPENING_CASH) || eventType.equals(GlEventType.CUSTOMER_RECEIPT)) {
            ErpGl.syncBankBalanceConn(conn, companyId, amount);
        } else if (eventType.equals(GlEventType.VENDOR_PAYMENT)) {
            ErpGl.syncBankBalanceConn(conn, companyId, 0L - amount);
        }
    }
}

class GlApproveWork implements DbTransactionWork {
    long companyId;
    long userId;
    long journalId;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(journalId));
        p.add(CryptoParse.longToString(companyId));
        Object st = ErpDb.queryOneConn(conn,
                "SELECT status FROM journal_entries WHERE id=? AND company_id=?", p, new StringMapper());
        if (st == null) {
            throw new TransactionException("journal not found");
        }
        if (((String) st).equals(JournalStatus.DRAFT) == false) {
            throw new TransactionException("journal not DRAFT; status=" + ((String) st));
        }
        ErpDb.execConn(conn,
                "UPDATE journal_entries SET status='" + JournalStatus.APPROVED
                        + "', approved_by=" + CryptoParse.longToString(userId)
                        + ", approved_at=" + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(journalId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + JournalStatus.DRAFT + "'",
                new ArrayList<Object>());
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = JournalStatus.APPROVED;
        ok.documentJson = "{\"id\":" + ErpJson.num(journalId) + ",\"status\":\"APPROVED\"}";
        return JnFuture.completed(Erp.loop, ok);
    }
}

class GlPostApprovedWork implements DbTransactionWork {
    long companyId;
    long userId;
    long journalId;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(journalId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hr = ErpDb.queryConn(conn,
                "SELECT status, posting_date FROM journal_entries WHERE id=? AND company_id=?", p);
        if (hr.rows.size() == 0) {
            throw new TransactionException("journal not found");
        }
        String st = hr.rows.get(0).getString(0);
        long postingDate = hr.rows.get(0).getLong(1);
        if (st.equals(JournalStatus.APPROVED) == false) {
            throw new TransactionException("journal not APPROVED; status=" + st);
        }
        if (ErpFinanceCore.assertOpenPeriodConn(conn, companyId, postingDate) == 0) {
            throw new TransactionException("PERIOD_CLOSED:cannot post into closed period");
        }
        /* CAS: only APPROVED → POSTED */
        ErpDb.execConn(conn,
                "UPDATE journal_entries SET status='" + JournalStatus.POSTED
                        + "', posted_by=" + CryptoParse.longToString(userId)
                        + ", posted_at=" + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(journalId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + JournalStatus.APPROVED + "'",
                new ArrayList<Object>());
        Object after = ErpDb.queryOneConn(conn,
                "SELECT status FROM journal_entries WHERE id=? AND company_id=?", p, new StringMapper());
        if (after == null || ((String) after).equals(JournalStatus.POSTED) == false) {
            throw new TransactionException("post CAS failed; concurrent change");
        }
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = JournalStatus.POSTED;
        ok.documentJson = "{\"id\":" + ErpJson.num(journalId) + ",\"status\":\"POSTED\"}";
        return JnFuture.completed(Erp.loop, ok);
    }
}

class GlRejectWork implements DbTransactionWork {
    long companyId;
    long userId;
    long journalId;
    String reason;

    public JnFuture run(DbConnection conn) {
        if (reason == null) {
            reason = "";
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(journalId));
        p.add(CryptoParse.longToString(companyId));
        Object st = ErpDb.queryOneConn(conn,
                "SELECT status FROM journal_entries WHERE id=? AND company_id=?", p, new StringMapper());
        if (st == null) {
            throw new TransactionException("journal not found");
        }
        String s = (String) st;
        if (s.equals(JournalStatus.DRAFT) == false && s.equals(JournalStatus.APPROVED) == false) {
            throw new TransactionException("cannot reject status=" + s);
        }
        ErpDb.execConn(conn,
                "UPDATE journal_entries SET status='" + JournalStatus.REJECTED
                        + "', rejected_reason='" + reason + "' WHERE id="
                        + CryptoParse.longToString(journalId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = JournalStatus.REJECTED;
        ok.documentJson = "{\"id\":" + ErpJson.num(journalId) + ",\"status\":\"REJECTED\"}";
        return JnFuture.completed(Erp.loop, ok);
    }
}

class GlReverseWork implements DbTransactionWork {
    long companyId;
    long userId;
    long journalId;
    String reason;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(journalId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hr = ErpDb.queryConn(conn,
                "SELECT COALESCE(event_type,''), status FROM journal_entries WHERE id=? AND company_id=?", p);
        String eventType = "";
        if (hr.rows.size() > 0) {
            eventType = hr.rows.get(0).getString(0);
        }
        /* Amount from lines for cash sync undo */
        long cashAmt = 0L;
        Object amt = ErpDb.queryOneConn(conn,
                "SELECT COALESCE(SUM(debit_amount+credit_amount),0)/2 FROM journal_entry_lines WHERE journal_entry_id="
                        + CryptoParse.longToString(journalId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>(), new LongMapper());
        cashAmt = ErpNum.asLong(amt);
        PostResultDto r = ErpFinanceCore.reverseJournalConn(conn, companyId, userId, journalId, reason,
                idempotencyKey);
        if (r.ok != 0) {
            if (eventType.equals(GlEventType.OPENING_CASH) || eventType.equals(GlEventType.CUSTOMER_RECEIPT)) {
                ErpGl.syncBankBalanceConn(conn, companyId, 0L - cashAmt);
            } else if (eventType.equals(GlEventType.VENDOR_PAYMENT)) {
                ErpGl.syncBankBalanceConn(conn, companyId, cashAmt);
            }
        }
        return JnFuture.completed(Erp.loop, r);
    }
}

class GlClosePeriodWork implements DbTransactionWork {
    long companyId;
    long userId;
    long periodId;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(periodId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult pr = ErpDb.queryConn(conn,
                "SELECT status, start_date, end_date FROM fiscal_periods WHERE id=? AND company_id=?", pp);
        if (pr.rows.size() == 0) {
            throw new TransactionException("period not found");
        }
        if (pr.rows.get(0).getString(0).equals(FiscalPeriodStatus.OPEN) == false) {
            throw new TransactionException("period not OPEN");
        }
        long start = pr.rows.get(0).getLong(1);
        long end = pr.rows.get(0).getLong(2);
        /* Claim CLOSING first so concurrent posts see non-OPEN and fail period check */
        ErpDb.execConn(conn,
                "UPDATE fiscal_periods SET status='" + FiscalPeriodStatus.CLOSING
                        + "' WHERE id=" + CryptoParse.longToString(periodId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + FiscalPeriodStatus.OPEN + "'",
                new ArrayList<Object>());
        Object claimed = ErpDb.queryOneConn(conn,
                "SELECT status FROM fiscal_periods WHERE id=? AND company_id=?", pp, new StringMapper());
        if (claimed == null || ((String) claimed).equals(FiscalPeriodStatus.CLOSING) == false) {
            throw new TransactionException("period close CAS failed");
        }
        String blockMsg = closeBlockersConn(conn, companyId, start, end);
        if (blockMsg.length() > 0) {
            ErpDb.execConn(conn,
                    "UPDATE fiscal_periods SET status='" + FiscalPeriodStatus.OPEN
                            + "' WHERE id=" + CryptoParse.longToString(periodId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
            throw new TransactionException("close blocked: " + blockMsg);
        }
        ErpDb.execConn(conn,
                "UPDATE fiscal_periods SET status='" + FiscalPeriodStatus.CLOSED
                        + "' WHERE id=" + CryptoParse.longToString(periodId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + FiscalPeriodStatus.CLOSING + "'",
                new ArrayList<Object>());
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "PERIOD_LOCKED";
        ok.documentJson = "{\"periodId\":" + ErpJson.num(periodId)
                + ",\"status\":\"CLOSED\",\"note\":\"period lock only; P-L close / year-end NOT implemented\"}";
        return JnFuture.completed(Erp.loop, ok);
    }

    static String closeBlockersConn(DbConnection conn, long companyId, long start, long end) {
        String msg = "";
        ArrayList<Object> ep = new ArrayList<Object>();
        ep.add(CryptoParse.longToString(companyId));
        Object pending = ErpDb.queryOneConn(conn,
                "SELECT COUNT(*) FROM gl_posting_events WHERE company_id=? AND status IN ('PENDING','FAILED')",
                ep, new LongMapper());
        if (ErpNum.asLong(pending) > 0L) {
            msg = msg + " PENDING_EVENTS:" + CryptoParse.longToString(ErpNum.asLong(pending));
        }
        Object up = ErpDb.queryOneConn(conn,
                "SELECT COUNT(*) FROM journal_entries WHERE company_id="
                        + CryptoParse.longToString(companyId)
                        + " AND status IN ('DRAFT','APPROVED')"
                        + " AND posting_date>=" + CryptoParse.longToString(start)
                        + " AND posting_date<=" + CryptoParse.longToString(end),
                new ArrayList<Object>(), new LongMapper());
        if (ErpNum.asLong(up) > 0L) {
            msg = msg + " UNPOSTED_JOURNALS:" + CryptoParse.longToString(ErpNum.asLong(up));
        }
        Object trial = ErpDb.queryOneConn(conn,
                "SELECT COALESCE(SUM(l.debit_amount),0)-COALESCE(SUM(l.credit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id="
                        + CryptoParse.longToString(companyId)
                        + " AND e.status='POSTED' AND e.posting_date<=" + CryptoParse.longToString(end),
                new ArrayList<Object>(), new LongMapper());
        if (ErpNum.asLong(trial) != 0L) {
            msg = msg + " TRIAL_UNBALANCED";
        }
        return msg;
    }
}

class GlReopenPeriodWork implements DbTransactionWork {
    long companyId;
    long userId;
    long periodId;
    String reason;

    public JnFuture run(DbConnection conn) {
        if (reason == null || reason.length() == 0) {
            throw new TransactionException("reopen reason required");
        }
        long now = AsyncClockNative.wallMillis();
        ErpDb.execConn(conn,
                "UPDATE fiscal_periods SET status='" + FiscalPeriodStatus.OPEN
                        + "' WHERE id=" + CryptoParse.longToString(periodId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + FiscalPeriodStatus.CLOSED + "'",
                new ArrayList<Object>());
        try {
            ErpDb.execConn(conn,
                    "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(userId)
                            + ",'PERIOD_REOPEN','FISCAL_PERIOD',"
                            + CryptoParse.longToString(periodId) + ",'" + reason + "',"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        } catch (Exception ignored) {
        }
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "REOPENED";
        ok.documentJson = "{\"periodId\":" + ErpJson.num(periodId) + ",\"status\":\"OPEN\"}";
        return JnFuture.completed(Erp.loop, ok);
    }
}
