/**
 * Finance 1.2 — chart of accounts, fiscal periods, journal posting engine.
 * Money amounts are integer currency units (no float/double).
 * @author 张晓达
 */
class ErpFinanceCore {
    static void ensureSeed(long companyId) {
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add("1000");
        Object existing = ErpDb.queryOne(
                "SELECT id FROM gl_accounts WHERE company_id=? AND account_code=?", chk, new LongMapper());
        if (existing != null) {
            ensureMfgAccounts(companyId);
            ensureEquityMapping(companyId);
            applyAccountMeta(companyId);
            ensureBankIfMissing(companyId);
            return;
        }
        long now = AsyncClockNative.wallMillis();
        ensureAccount(companyId, "1000", "Cash / Bank", AccountType.ASSET, 0L);
        ensureAccount(companyId, "1100", "Accounts Receivable", AccountType.ASSET, 0L);
        ensureAccount(companyId, "1200", "Inventory", AccountType.ASSET, 0L);
        ensureAccount(companyId, "1300", "WIP", AccountType.ASSET, 0L);
        ensureAccount(companyId, "1400", "Finished Goods", AccountType.ASSET, 0L);
        ensureAccount(companyId, "2000", "Accounts Payable", AccountType.LIABILITY, 0L);
        ensureAccount(companyId, "2100", "GR/IR Clearing", AccountType.LIABILITY, 0L);
        ensureAccount(companyId, "3000", "Equity", AccountType.EQUITY, 0L);
        ensureAccount(companyId, "4000", "Revenue", AccountType.REVENUE, 0L);
        ensureAccount(companyId, "5000", "Cost of Goods Sold", AccountType.EXPENSE, 0L);

        mapAccount(companyId, AccountMapKey.BANK, "1000");
        mapAccount(companyId, AccountMapKey.AR, "1100");
        mapAccount(companyId, AccountMapKey.INVENTORY, "1200");
        mapAccount(companyId, AccountMapKey.WIP, "1300");
        mapAccount(companyId, AccountMapKey.FINISHED_GOODS, "1400");
        mapAccount(companyId, AccountMapKey.AP, "2000");
        mapAccount(companyId, AccountMapKey.GR_CLEARING, "2100");
        mapAccount(companyId, AccountMapKey.EQUITY, "3000");
        mapAccount(companyId, AccountMapKey.REVENUE, "4000");
        mapAccount(companyId, AccountMapKey.COGS, "5000");

        applyAccountMeta(companyId);
        ensureBankIfMissing(companyId);

        /* Wide open period covering demo horizon (epoch±years). */
        ArrayList<Object> fp = new ArrayList<Object>();
        fp.add(CryptoParse.longToString(companyId));
        fp.add("2026");
        fp.add("1");
        fp.add("0");
        fp.add("4102444800000");
        fp.add(FiscalPeriodStatus.OPEN);
        ErpDb.exec(
                "INSERT INTO fiscal_periods(company_id, fiscal_year, period_number, start_date, end_date, status) VALUES (?,?,?,?,?,?)",
                fp);

        ArrayList<Object> seq = new ArrayList<Object>();
        seq.add(CryptoParse.longToString(companyId));
        seq.add("JE");
        seq.add("1");
        ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", seq);
        ArrayList<Object> seqAr = new ArrayList<Object>();
        seqAr.add(CryptoParse.longToString(companyId));
        seqAr.add("AR");
        seqAr.add("1");
        ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", seqAr);
        ArrayList<Object> seqAp = new ArrayList<Object>();
        seqAp.add(CryptoParse.longToString(companyId));
        seqAp.add("AP");
        seqAp.add("1");
        ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", seqAp);
        ArrayList<Object> seqIp = new ArrayList<Object>();
        seqIp.add(CryptoParse.longToString(companyId));
        seqIp.add("IP");
        seqIp.add("1");
        ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", seqIp);
        ArrayList<Object> seqOp = new ArrayList<Object>();
        seqOp.add(CryptoParse.longToString(companyId));
        seqOp.add("OP");
        seqOp.add("1");
        ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", seqOp);
    }

    static void ensureBankIfMissing(long companyId) {
        ArrayList<Object> empty = new ArrayList<Object>();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object existing = ErpDb.queryOne(
                "SELECT id FROM bank_accounts WHERE company_id=? ORDER BY id LIMIT 1", p, new LongMapper());
        if (existing != null) {
            return;
        }
        Object bankGl = accountIdByCode(companyId, "1000");
        if (ErpNum.asLong(bankGl) == 0L) {
            return;
        }
        ErpDb.exec(
                "INSERT INTO bank_accounts(company_id, account_name, currency, ledger_account_id, active) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'Operating Bank','USD',"
                        + CryptoParse.longToString(ErpNum.asLong(bankGl)) + ",1)",
                empty);
    }

    static void ensureEquityMapping(long companyId) {
        Object eq = accountIdByCode(companyId, "3000");
        if (ErpNum.asLong(eq) == 0L) {
            ensureAccount(companyId, "3000", "Equity", AccountType.EQUITY, 0L);
        }
        Object mapEq = ErpDb.queryOne(
                "SELECT account_id FROM account_mappings WHERE company_id=? AND mapping_key=?",
                mapParams(companyId, AccountMapKey.EQUITY), new LongMapper());
        if (ErpNum.asLong(mapEq) == 0L) {
            mapAccount(companyId, AccountMapKey.EQUITY, "3000");
        }
    }

    /** Normal balance + control/partner flags for seeded COA (idempotent). */
    static void applyAccountMeta(long companyId) {
        String cid = CryptoParse.longToString(companyId);
        ArrayList<Object> empty = new ArrayList<Object>();
        try {
            ErpDb.exec("UPDATE gl_accounts SET normal_balance='" + NormalBalance.DEBIT
                    + "', is_control=0, require_partner=0 WHERE company_id=" + cid
                    + " AND account_code IN ('1000','1200','1300','1400','5000')", empty);
            ErpDb.exec("UPDATE gl_accounts SET normal_balance='" + NormalBalance.DEBIT
                    + "', is_control=1, require_partner=1 WHERE company_id=" + cid
                    + " AND account_code='1100'", empty);
            ErpDb.exec("UPDATE gl_accounts SET normal_balance='" + NormalBalance.CREDIT
                    + "', is_control=1, require_partner=1 WHERE company_id=" + cid
                    + " AND account_code='2000'", empty);
            ErpDb.exec("UPDATE gl_accounts SET normal_balance='" + NormalBalance.CREDIT
                    + "', is_control=0, require_partner=0 WHERE company_id=" + cid
                    + " AND account_code IN ('2100','3000','4000')", empty);
        } catch (Exception ignored) {
        }
    }

    static void ensureMfgAccounts(long companyId) {
        Object wip = accountIdByCode(companyId, "1300");
        if (ErpNum.asLong(wip) == 0L) {
            ensureAccount(companyId, "1300", "WIP", AccountType.ASSET, 0L);
            mapAccount(companyId, AccountMapKey.WIP, "1300");
        }
        Object fg = accountIdByCode(companyId, "1400");
        if (ErpNum.asLong(fg) == 0L) {
            ensureAccount(companyId, "1400", "Finished Goods", AccountType.ASSET, 0L);
            mapAccount(companyId, AccountMapKey.FINISHED_GOODS, "1400");
        }
        Object mapWip = ErpDb.queryOne(
                "SELECT account_id FROM account_mappings WHERE company_id=? AND mapping_key=?",
                mapParams(companyId, AccountMapKey.WIP), new LongMapper());
        if (ErpNum.asLong(mapWip) == 0L) {
            mapAccount(companyId, AccountMapKey.WIP, "1300");
        }
        Object mapFg = ErpDb.queryOne(
                "SELECT account_id FROM account_mappings WHERE company_id=? AND mapping_key=?",
                mapParams(companyId, AccountMapKey.FINISHED_GOODS), new LongMapper());
        if (ErpNum.asLong(mapFg) == 0L) {
            mapAccount(companyId, AccountMapKey.FINISHED_GOODS, "1400");
        }
    }

    static ArrayList<Object> mapParams(long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        return p;
    }

    static void ensureAccount(long companyId, String code, String name, String type, long parentId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        p.add(name);
        p.add(type);
        if (parentId == 0L) {
            ErpDb.exec(
                    "INSERT INTO gl_accounts(company_id, account_code, account_name, account_type, posting_allowed, active) VALUES (?,?,?,?,1,1)",
                    p);
        } else {
            p.add(CryptoParse.longToString(parentId));
            ErpDb.exec(
                    "INSERT INTO gl_accounts(company_id, account_code, account_name, account_type, parent_account_id, posting_allowed, active) VALUES (?,?,?,?,?,1,1)",
                    p);
        }
    }

    static void mapAccount(long companyId, String key, String code) {
        Object aid = accountIdByCode(companyId, code);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        p.add(CryptoParse.longToString(ErpNum.asLong(aid)));
        ErpDb.exec("INSERT INTO account_mappings(company_id, mapping_key, account_id) VALUES (?,?,?)", p);
    }

    static Object accountIdByCode(long companyId, String code) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        return ErpDb.queryOne("SELECT id FROM gl_accounts WHERE company_id=? AND account_code=?", p, new LongMapper());
    }

    static long mappedAccountId(long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        Object v = ErpDb.queryOne(
                "SELECT account_id FROM account_mappings WHERE company_id=? AND mapping_key=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long mappedAccountIdConn(DbConnection conn, long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        Object v = ErpDb.queryOneConn(conn,
                "SELECT account_id FROM account_mappings WHERE company_id=? AND mapping_key=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static int assertOpenPeriodConn(DbConnection conn, long companyId, long postingDate) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(postingDate));
        p.add(CryptoParse.longToString(postingDate));
        Object st = ErpDb.queryOneConn(conn,
                "SELECT status FROM fiscal_periods WHERE company_id=? AND start_date<=? AND end_date>=? ORDER BY period_number LIMIT 1",
                p, new StringMapper());
        if (st == null) {
            /* No overlapping period row — keep legacy allow (seed creates wide OPEN period). */
            return 1;
        }
        String status = (String) st;
        if (status == null || status.length() == 0) {
            return 1;
        }
        if (status.equals(FiscalPeriodStatus.OPEN)) {
            return 1;
        }
        return 0;
    }

    static long periodIdForDateConn(DbConnection conn, long companyId, long postingDate) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(postingDate));
        p.add(CryptoParse.longToString(postingDate));
        Object id = ErpDb.queryOneConn(conn,
                "SELECT id FROM fiscal_periods WHERE company_id=? AND start_date<=? AND end_date>=? ORDER BY period_number LIMIT 1",
                p, new LongMapper());
        return ErpNum.asLong(id);
    }

    static PostResultDto postJournalConn(DbConnection conn, long companyId, long userId, PostingRequest req) {
        PostResultDto bad = new PostResultDto();
        bad.ok = 0;
        if (req == null || req.lines == null || req.lines.size() == 0) {
            bad.code = "EMPTY_JE";
            bad.message = "empty journal";
            return bad;
        }
        String status = JournalStatus.POSTED;
        if (req.initialStatus != null && req.initialStatus.length() > 0) {
            status = req.initialStatus;
        }
        if (status.equals(JournalStatus.POSTED) || status.equals(JournalStatus.APPROVED)) {
            if (assertOpenPeriodConn(conn, companyId, req.postingDate) == 0) {
                bad.code = "PERIOD_CLOSED";
                bad.message = "fiscal period closed or missing for posting date";
                return bad;
            }
        }
        long debit = 0L;
        long credit = 0L;
        int i = 0;
        while (i < req.lines.size()) {
            PostingLineInput ln = (PostingLineInput) req.lines.get(i);
            if (ln.debitAmount < 0L || ln.creditAmount < 0L) {
                bad.code = "NEG_AMOUNT";
                bad.message = "negative amount";
                return bad;
            }
            if (ln.debitAmount > 0L && ln.creditAmount > 0L) {
                bad.code = "BOTH_SIDES";
                bad.message = "line has debit and credit";
                return bad;
            }
            if (ln.debitAmount == 0L && ln.creditAmount == 0L) {
                bad.code = "ZERO_LINE";
                bad.message = "zero line";
                return bad;
            }
            ArrayList<Object> ap = new ArrayList<Object>();
            ap.add(CryptoParse.longToString(ln.accountId));
            ap.add(CryptoParse.longToString(companyId));
            DbQueryResult ar = ErpDb.queryConn(conn,
                    "SELECT posting_allowed, COALESCE(require_partner,0), COALESCE(require_warehouse,0) FROM gl_accounts WHERE id=? AND company_id=? AND active=1",
                    ap);
            if (ar.rows.size() == 0) {
                bad.code = "BAD_ACCOUNT";
                bad.message = "account missing or inactive id=" + CryptoParse.longToString(ln.accountId);
                return bad;
            }
            Row arow = ar.rows.get(0);
            if (arow.getInt(0) == 0) {
                bad.code = "BAD_ACCOUNT";
                bad.message = "account not posting-allowed";
                return bad;
            }
            if (arow.getInt(1) != 0 && ln.businessPartnerId <= 0L) {
                bad.code = "PARTNER_REQUIRED";
                bad.message = "auxiliary partner required for account "
                        + CryptoParse.longToString(ln.accountId);
                return bad;
            }
            if (arow.getInt(2) != 0 && ln.warehouseId <= 0L) {
                bad.code = "WAREHOUSE_REQUIRED";
                bad.message = "auxiliary warehouse required for account "
                        + CryptoParse.longToString(ln.accountId);
                return bad;
            }
            debit = debit + ln.debitAmount;
            credit = credit + ln.creditAmount;
            i = i + 1;
        }
        if (debit != credit) {
            bad.code = "UNBALANCED";
            bad.message = "debit != credit; debit=" + CryptoParse.longToString(debit)
                    + " credit=" + CryptoParse.longToString(credit)
                    + " diff=" + CryptoParse.longToString(debit - credit);
            return bad;
        }
        if (req.idempotencyKey != null && req.idempotencyKey.length() > 0) {
            String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
            if (cached != null && cached.length() > 0) {
                return ErpPosting.resultFromJson(cached);
            }
        }
        long now = AsyncClockNative.wallMillis();
        long periodId = periodIdForDateConn(conn, companyId, req.postingDate);
        long seq = ErpDb.nextDocNoConn(conn, companyId, "JE");
        String jn = "JE-" + CryptoParse.longToString(seq);
        String eventType = "";
        if (req.eventType != null) {
            eventType = req.eventType;
        }
        String descHdr = "";
        if (req.description != null) {
            descHdr = req.description;
        }
        long postedBy = 0L;
        long postedAt = 0L;
        if (status.equals(JournalStatus.POSTED)) {
            postedBy = userId;
            postedAt = now;
        }
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(jn);
        hdr.add(CryptoParse.longToString(req.postingDate));
        hdr.add(CryptoParse.longToString(req.documentDate));
        hdr.add(req.sourceType);
        hdr.add(CryptoParse.longToString(req.sourceId));
        hdr.add(status);
        hdr.add(descHdr);
        hdr.add(CryptoParse.longToString(userId));
        hdr.add(CryptoParse.longToString(now));
        long jeId = 0L;
        try {
            ArrayList<Object> hdr2 = new ArrayList<Object>();
            int hi = 0;
            while (hi < hdr.size()) {
                hdr2.add(hdr.get(hi));
                hi = hi + 1;
            }
            hdr2.add(eventType);
            hdr2.add(CryptoParse.longToString(periodId));
            hdr2.add(CryptoParse.longToString(postedBy));
            hdr2.add(CryptoParse.longToString(postedAt));
            jeId = ErpDb.insertReturningIdConn(conn,
                    "INSERT INTO journal_entries(company_id, journal_number, posting_date, document_date, source_type, source_id, status, description, created_by, created_at, event_type, fiscal_period_id, posted_by, posted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    hdr2);
        } catch (Exception colEx) {
            jeId = ErpDb.insertReturningIdConn(conn,
                    "INSERT INTO journal_entries(company_id, journal_number, posting_date, document_date, source_type, source_id, status, description, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    hdr);
        }
        int lineNo = 1;
        int li = 0;
        while (li < req.lines.size()) {
            PostingLineInput ln = (PostingLineInput) req.lines.get(li);
            String desc = "";
            if (ln.description != null) {
                desc = ln.description;
            }
            try {
                ErpDb.execConn(conn,
                        "INSERT INTO journal_entry_lines(company_id, journal_entry_id, line_no, account_id, debit_amount, credit_amount, business_partner_id, warehouse_id, description) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(jeId) + ","
                                + CryptoParse.longToString(lineNo) + ","
                                + CryptoParse.longToString(ln.accountId) + ","
                                + CryptoParse.longToString(ln.debitAmount) + ","
                                + CryptoParse.longToString(ln.creditAmount) + ","
                                + CryptoParse.longToString(ln.businessPartnerId) + ","
                                + CryptoParse.longToString(ln.warehouseId) + ",'"
                                + desc + "')",
                        new ArrayList<Object>());
            } catch (Exception lineEx) {
                ErpDb.execConn(conn,
                        "INSERT INTO journal_entry_lines(company_id, journal_entry_id, line_no, account_id, debit_amount, credit_amount, business_partner_id, description) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(jeId) + ","
                                + CryptoParse.longToString(lineNo) + ","
                                + CryptoParse.longToString(ln.accountId) + ","
                                + CryptoParse.longToString(ln.debitAmount) + ","
                                + CryptoParse.longToString(ln.creditAmount) + ","
                                + CryptoParse.longToString(ln.businessPartnerId) + ",'"
                                + desc + "')",
                        new ArrayList<Object>());
            }
            lineNo = lineNo + 1;
            li = li + 1;
        }
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = status;
        ok.documentJson = "{\"id\":" + ErpJson.num(jeId) + ",\"journalNumber\":" + ErpJson.str(jn)
                + ",\"status\":" + ErpJson.str(status)
                + ",\"debit\":" + ErpJson.num(debit) + ",\"credit\":" + ErpJson.num(credit) + "}";
        if (req.idempotencyKey != null && req.idempotencyKey.length() > 0) {
            ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "JE", ok.documentJson, now);
        }
        return ok;
    }

    static PostResultDto reverseJournalConn(DbConnection conn, long companyId, long userId, long journalId,
            String reason, String idempotencyKey) {
        PostResultDto bad = new PostResultDto();
        bad.ok = 0;
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
            if (cached != null && cached.length() > 0) {
                return ErpPosting.resultFromJson(cached);
            }
        }
        ArrayList<Object> hp = new ArrayList<Object>();
        hp.add(CryptoParse.longToString(journalId));
        hp.add(CryptoParse.longToString(companyId));
        DbQueryResult hr = ErpDb.queryConn(conn,
                "SELECT journal_number, posting_date, document_date, status, COALESCE(reversed_journal_id,0), COALESCE(event_type,'') FROM journal_entries WHERE id=? AND company_id=?",
                hp);
        if (hr.rows.size() == 0) {
            bad.code = "NOT_FOUND";
            bad.message = "journal not found";
            return bad;
        }
        Row h = hr.rows.get(0);
        String status = h.getString(3);
        long already = h.getLong(4);
        if (status.equals(JournalStatus.POSTED) == false) {
            bad.code = "NOT_POSTED";
            bad.message = "only POSTED journals can be reversed; status=" + status;
            return bad;
        }
        if (already != 0L) {
            bad.code = "ALREADY_REVERSED";
            bad.message = "already reversed by " + CryptoParse.longToString(already);
            return bad;
        }
        if (assertOpenPeriodConn(conn, companyId, h.getLong(1)) == 0) {
            bad.code = "PERIOD_CLOSED";
            bad.message = "period closed; cannot reverse";
            return bad;
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(journalId));
        lp.add(CryptoParse.longToString(companyId));
        DbQueryResult lines = ErpDb.queryConn(conn,
                "SELECT account_id, debit_amount, credit_amount, COALESCE(business_partner_id,0), COALESCE(warehouse_id,0), description FROM journal_entry_lines WHERE journal_entry_id=? AND company_id=? ORDER BY line_no",
                lp);
        PostingRequest req = new PostingRequest();
        req.sourceType = JournalSource.REVERSAL;
        req.sourceId = journalId;
        req.postingDate = h.getLong(1);
        req.documentDate = h.getLong(2);
        req.eventType = GlEventType.REVERSAL;
        if (reason == null || reason.length() == 0) {
            req.description = "Reversal of " + h.getString(0);
        } else {
            req.description = "Reversal of " + h.getString(0) + ": " + reason;
        }
        req.idempotencyKey = idempotencyKey;
        req.initialStatus = JournalStatus.POSTED;
        req.lines = new ArrayList<Object>();
        int i = 0;
        while (i < lines.rows.size()) {
            Row ln = lines.rows.get(i);
            req.lines.add(lineWh(ln.getLong(0), ln.getLong(2), ln.getLong(1), ln.getLong(3), ln.getLong(4),
                    ln.getString(5)));
            i = i + 1;
        }
        PostResultDto rev = postJournalConn(conn, companyId, userId, req);
        if (rev.ok == 0) {
            return rev;
        }
        long revId = 0L;
        if (rev.documentJson != null) {
            int idx = rev.documentJson.indexOf("\"id\":");
            if (idx >= 0) {
                int j = idx + 5;
                String n = "";
                while (j < rev.documentJson.length()) {
                    int c = rev.documentJson.charAt(j);
                    if (c >= 48 && c <= 57) {
                        n = n + WebUtil.byteChar(c);
                    } else {
                        if (n.length() > 0) {
                            break;
                        }
                    }
                    j = j + 1;
                }
                if (n.length() > 0) {
                    revId = ErpNum.parseLong(n);
                }
            }
        }
        /* Keep original POSTED (still queryable in books); reversing JE nets out.
           Mark link via reversed_journal_id; do not drop original from TB. */
        ErpDb.execConn(conn,
                "UPDATE journal_entries SET reversed_journal_id=" + CryptoParse.longToString(revId)
                        + " WHERE id=" + CryptoParse.longToString(journalId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + JournalStatus.POSTED + "'"
                        + " AND COALESCE(reversed_journal_id,0)=0",
                new ArrayList<Object>());
        return rev;
    }

    static PostingLineInput line(long accountId, long debit, long credit, long bpId, String desc) {
        return lineWh(accountId, debit, credit, bpId, 0L, desc);
    }

    static PostingLineInput lineWh(long accountId, long debit, long credit, long bpId, long whId, String desc) {
        PostingLineInput ln = new PostingLineInput();
        ln.accountId = accountId;
        ln.debitAmount = debit;
        ln.creditAmount = credit;
        ln.businessPartnerId = bpId;
        ln.warehouseId = whId;
        ln.description = desc;
        return ln;
    }
}

class GoodsReceiptPosting {
    static PostResultDto postConn(DbConnection conn, long companyId, long userId, long grId, long valueAmount) {
        long inv = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.INVENTORY);
        long clr = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.GR_CLEARING);
        PostingRequest req = new PostingRequest();
        req.sourceType = JournalSource.GOODS_RECEIPT;
        req.sourceId = grId;
        req.postingDate = AsyncClockNative.wallMillis();
        req.documentDate = req.postingDate;
        req.description = "Goods receipt valuation";
        req.idempotencyKey = "je-gr-" + CryptoParse.longToString(grId);
        req.lines = new ArrayList<Object>();
        req.lines.add(ErpFinanceCore.line(inv, valueAmount, 0L, 0L, "Inventory"));
        req.lines.add(ErpFinanceCore.line(clr, 0L, valueAmount, 0L, "GR clearing"));
        return ErpFinanceCore.postJournalConn(conn, companyId, userId, req);
    }
}

class DeliveryCostPosting {
    static PostResultDto postConn(DbConnection conn, long companyId, long userId, long dlId, long cogsAmount) {
        long cogs = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.COGS);
        long inv = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.INVENTORY);
        PostingRequest req = new PostingRequest();
        req.sourceType = JournalSource.DELIVERY;
        req.sourceId = dlId;
        req.postingDate = AsyncClockNative.wallMillis();
        req.documentDate = req.postingDate;
        req.description = "Delivery COGS";
        req.idempotencyKey = "je-dl-" + CryptoParse.longToString(dlId);
        req.lines = new ArrayList<Object>();
        req.lines.add(ErpFinanceCore.line(cogs, cogsAmount, 0L, 0L, "COGS"));
        req.lines.add(ErpFinanceCore.line(inv, 0L, cogsAmount, 0L, "Inventory relief"));
        return ErpFinanceCore.postJournalConn(conn, companyId, userId, req);
    }
}
