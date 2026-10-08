/**
 * Finance 1.2 — chart of accounts, fiscal periods, journal posting engine.
 * Money amounts are integer currency units (no float/double).
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
        mapAccount(companyId, AccountMapKey.REVENUE, "4000");
        mapAccount(companyId, AccountMapKey.COGS, "5000");

        ArrayList<Object> empty = new ArrayList<Object>();
        Object bankGl = accountIdByCode(companyId, "1000");
        ErpDb.exec(
                "INSERT INTO bank_accounts(company_id, account_name, currency, ledger_account_id, active) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'Operating Bank','USD',"
                        + CryptoParse.longToString(ErpNum.asLong(bankGl)) + ",1)",
                empty);

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
            return 1;
        }
        if (((String) st).equals(FiscalPeriodStatus.OPEN)) {
            return 1;
        }
        return 0;
    }

    static PostResultDto postJournalConn(DbConnection conn, long companyId, long userId, PostingRequest req) {
        PostResultDto bad = new PostResultDto();
        bad.ok = 0;
        if (req == null || req.lines == null || req.lines.size() == 0) {
            bad.code = "EMPTY_JE";
            bad.message = "empty journal";
            return bad;
        }
        if (assertOpenPeriodConn(conn, companyId, req.postingDate) == 0) {
            bad.code = "PERIOD_CLOSED";
            bad.message = "fiscal period closed";
            return bad;
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
            Object acc = ErpDb.queryOneConn(conn,
                    "SELECT posting_allowed FROM gl_accounts WHERE id=? AND company_id=? AND active=1", ap,
                    new IntMapper());
            if (acc == null || ErpNum.parseInt((String) acc) == 0) {
                bad.code = "BAD_ACCOUNT";
                bad.message = "invalid account";
                return bad;
            }
            debit = debit + ln.debitAmount;
            credit = credit + ln.creditAmount;
            i = i + 1;
        }
        if (debit != credit) {
            bad.code = "UNBALANCED";
            bad.message = "debit != credit";
            return bad;
        }
        if (req.idempotencyKey != null && req.idempotencyKey.length() > 0) {
            String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
            if (cached.length() > 0) {
                return ErpPosting.resultFromJson(cached);
            }
        }
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "JE");
        String jn = "JE-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(jn);
        hdr.add(CryptoParse.longToString(req.postingDate));
        hdr.add(CryptoParse.longToString(req.documentDate));
        hdr.add(req.sourceType);
        hdr.add(CryptoParse.longToString(req.sourceId));
        hdr.add(JournalStatus.POSTED);
        if (req.description == null) {
            hdr.add("");
        } else {
            hdr.add(req.description);
        }
        hdr.add(CryptoParse.longToString(userId));
        hdr.add(CryptoParse.longToString(now));
        long jeId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO journal_entries(company_id, journal_number, posting_date, document_date, source_type, source_id, status, description, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                hdr);
        int lineNo = 1;
        int li = 0;
        while (li < req.lines.size()) {
            PostingLineInput ln = (PostingLineInput) req.lines.get(li);
            String desc = "";
            if (ln.description != null) {
                desc = ln.description;
            }
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
            lineNo = lineNo + 1;
            li = li + 1;
        }
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted";
        ok.documentJson = "{\"id\":" + ErpJson.num(jeId) + ",\"journalNumber\":" + ErpJson.str(jn)
                + ",\"debit\":" + ErpJson.num(debit) + ",\"credit\":" + ErpJson.num(credit) + "}";
        if (req.idempotencyKey != null && req.idempotencyKey.length() > 0) {
            ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "JE", ok.documentJson, now);
        }
        return ok;
    }

    static PostingLineInput line(long accountId, long debit, long credit, long bpId, String desc) {
        PostingLineInput ln = new PostingLineInput();
        ln.accountId = accountId;
        ln.debitAmount = debit;
        ln.creditAmount = credit;
        ln.businessPartnerId = bpId;
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
