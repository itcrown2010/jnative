/**
 * Posting of B1 marketing documents (A/R & A/P invoices and credit memos): GL journal, sub-ledger
 * (ar_invoices / ap_invoices), conversion of provisional DL/GR sub-ledger rows, reversal.
 * All writes of one document happen in one DB transaction; the journal idempotency key is per document.
 * @author 张晓达
 */
class ErpB1DocPost {
    static B1Doc load(long companyId, String type, long id) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        p.add(type);
        DbQueryResult r = ErpDb.query("SELECT partner_id, partner_name, status, doc_date, due_date, currency, rate_micro, warehouse_id, discount_bps, subtotal_cents, tax_cents, total_cents, total_local_cents, base_type, base_id, doc_no, subledger_id, journal_id, notes FROM mk_docs WHERE id=? AND company_id=? AND doc_type=?", p);
        if (r.rows.size() == 0) {
            throw new RuntimeException("not found");
        }
        Row h = r.rows.get(0);
        B1Doc d = new B1Doc();
        d.id = id;
        d.docType = type;
        d.partnerId = h.getLong(0);
        d.partnerName = h.getString(1);
        d.status = h.getString(2);
        d.docDate = h.getLong(3);
        d.dueDate = h.getLong(4);
        d.currency = h.getString(5);
        d.rateMicro = h.getLong(6);
        d.warehouseId = h.getLong(7);
        d.discountBps = h.getLong(8);
        d.subtotalCents = h.getLong(9);
        d.taxCents = h.getLong(10);
        d.totalCents = h.getLong(11);
        d.totalLocalCents = h.getLong(12);
        d.baseType = h.getString(13);
        d.baseId = h.getLong(14);
        d.docNo = h.getString(15);
        d.notes = h.getString(18);
        d.lines = new ArrayList<Object>();
        ArrayList<Object> lp = ErpB1Util.params();
        lp.add(ErpB1Util.lng(id));
        lp.add(ErpB1Util.lng(companyId));
        DbQueryResult lr = ErpDb.query("SELECT id, item_id, description, warehouse_id, quantity, uom_code, unit_price_cents, discount_bps, tax_code_id, tax_bps, net_cents, tax_cents, total_cents, is_inventory, base_type, base_id, base_line_id, clearing_cents FROM mk_doc_lines WHERE doc_id=? AND company_id=? ORDER BY line_no", lp);
        int i = 0;
        while (i < lr.rows.size()) {
            Row x = lr.rows.get(i);
            B1Line l = new B1Line();
            l.id = x.getLong(0);
            l.itemId = x.getLong(1);
            l.description = x.getString(2);
            l.warehouseId = x.getLong(3);
            l.quantity = x.getLong(4);
            l.uomCode = x.getString(5);
            l.unitPriceCents = x.getLong(6);
            l.discountBps = x.getLong(7);
            l.taxCodeId = x.getLong(8);
            l.taxBps = x.getLong(9);
            l.netCents = x.getLong(10);
            l.taxCents = x.getLong(11);
            l.totalCents = x.getLong(12);
            l.isInventory = x.getLong(13);
            l.baseType = x.getString(14);
            l.baseId = x.getLong(15);
            l.baseLineId = x.getLong(16);
            l.clearingCents = x.getLong(17);
            d.lines.add(l);
            i = i + 1;
        }
        return d;
    }

    static void ensureAccounts(long companyId) {
        if (ErpFinanceCore.mappedAccountId(companyId, "OUTPUT_TAX") == 0L
                || ErpFinanceCore.mappedAccountId(companyId, "INPUT_TAX") == 0L
                || ErpFinanceCore.mappedAccountId(companyId, "EXPENSE") == 0L) {
            ErpB1Master.ensureB1Accounts(companyId);
        }
    }

    /** Non-transactional validation; throws RuntimeException with a business error. */
    static B1Doc precheck(long companyId, String type, long id) {
        if (ErpB1Docs.isPosting(type) == 0) {
            throw new RuntimeException(type + " is not a posting document");
        }
        B1Doc d = load(companyId, type, id);
        if (d.status.equals("POSTED")) {
            return d;
        }
        if (d.status.equals("DRAFT") == false) {
            throw new RuntimeException("cannot post status " + d.status);
        }
        if (d.lines.size() == 0) {
            throw new RuntimeException("lines required");
        }
        ensureAccounts(companyId);
        int sales = ErpB1Docs.isSales(type);
        ArrayList<Object> pp = ErpB1Util.params();
        pp.add(ErpB1Util.lng(d.partnerId));
        pp.add(ErpB1Util.lng(companyId));
        Object ps = ErpDb.queryOne("SELECT status FROM business_partners WHERE id=? AND company_id=?", pp, new StringMapper());
        if (ps == null) {
            throw new RuntimeException("partner not found");
        }
        if (((String) ps).equals("ACTIVE") == false) {
            throw new RuntimeException("PARTNER_" + (String) ps);
        }
        if (d.rateMicro <= 0L) {
            throw new RuntimeException("NO_EXCHANGE_RATE " + d.currency);
        }
        if (d.totalLocalCents <= 0L) {
            throw new RuntimeException("ZERO_TOTAL");
        }
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            String where = "line " + String.valueOf(i + 1) + ": ";
            if (l.baseType.length() > 0) {
                ErpB1Docs.checkBase(companyId, d, l, id, where);
            }
            if (type.equals(ErpB1Docs.ARINV) && l.isInventory != 0L && l.baseType.equals("DL") == false) {
                throw new RuntimeException(where + "INVENTORY_REQUIRES_DELIVERY: invoice stock items from a delivery");
            }
            if (type.equals(ErpB1Docs.APINV) && l.isInventory != 0L && l.baseType.equals("GR") == false) {
                throw new RuntimeException(where + "INVENTORY_REQUIRES_RECEIPT: invoice stock items from a goods receipt");
            }
            i = i + 1;
        }
        if (type.equals(ErpB1Docs.ARINV) && d.baseType.equals("DL")) {
            requireFullCoverage(companyId, d, "delivery_lines", "delivery_id");
        }
        if (type.equals(ErpB1Docs.APINV) && d.baseType.equals("GR")) {
            requireFullCoverage(companyId, d, "goods_receipt_lines", "goods_receipt_id");
        }
        if (type.equals(ErpB1Docs.ARCM) || type.equals(ErpB1Docs.APCM)) {
            ArrayList<Object> cp = ErpB1Util.params();
            cp.add(ErpB1Util.lng(companyId));
            cp.add(d.baseType);
            cp.add(ErpB1Util.lng(d.baseId));
            cp.add(ErpB1Util.lng(id));
            long others = ErpNum.asLong(ErpDb.queryOne("SELECT COALESCE(SUM(total_local_cents),0) FROM mk_docs WHERE company_id=? AND base_type=? AND base_id=? AND status<>'CANCELLED' AND id<>?", cp, new LongMapper()));
            ArrayList<Object> bp = ErpB1Util.params();
            bp.add(ErpB1Util.lng(d.baseId));
            bp.add(ErpB1Util.lng(companyId));
            long baseTotal = ErpNum.asLong(ErpDb.queryOne("SELECT total_local_cents FROM mk_docs WHERE id=? AND company_id=?", bp, new LongMapper()));
            if (others + d.totalLocalCents > baseTotal) {
                throw new RuntimeException("CREDIT_EXCEEDS_INVOICE: credit memos exceed invoice total");
            }
        }
        if (type.equals(ErpB1Docs.ARINV)) {
            long extra = d.totalLocalCents;
            if (d.baseType.equals("DL")) {
                extra = 0L;
            }
            try {
                ErpB1Master.assertCreditOk(companyId, d.partnerId, extra);
            } catch (Exception ex) {
                throw new RuntimeException(ErpB1Util.exMsg(ex, "CREDIT_BLOCKED"));
            }
        }
        return d;
    }

    static void requireFullCoverage(long companyId, B1Doc d, String table, String hdrCol) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(d.baseId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.query("SELECT id, quantity FROM " + table + " WHERE " + hdrCol + "=? AND company_id=?", p);
        if (r.rows.size() != d.lines.size()) {
            throw new RuntimeException("INVOICE_MUST_COVER_FULL_BASE: invoice every line of the base document");
        }
        int i = 0;
        while (i < r.rows.size()) {
            long lineId = r.rows.get(i).getLong(0);
            long qty = r.rows.get(i).getLong(1);
            int found = 0;
            int k = 0;
            while (k < d.lines.size()) {
                B1Line l = (B1Line) d.lines.get(k);
                if (l.baseLineId == lineId && l.quantity == qty) {
                    found = 1;
                }
                k = k + 1;
            }
            if (found == 0) {
                throw new RuntimeException("INVOICE_MUST_COVER_FULL_BASE: partial invoicing of a delivery/receipt is not supported");
            }
            i = i + 1;
        }
    }

    static String post(long companyId, long userId, String type, long id) {
        B1Doc d = precheck(companyId, type, id);
        if (d.status.equals("POSTED")) {
            return ErpB1Docs.get(companyId, type, id);
        }
        B1PostWork w = new B1PostWork();
        w.companyId = companyId;
        w.userId = userId;
        w.type = type;
        w.docId = id;
        w.reverse = 0;
        PostResultDto r = ErpPosting.runPostingWork(w);
        ErpDb.reconnect();
        if (r.ok == 0) {
            throw new RuntimeException(r.message);
        }
        return ErpB1Docs.get(companyId, type, id);
    }

    static String reverse(long companyId, long userId, String type, long id) {
        if (ErpB1Docs.isPosting(type) == 0) {
            throw new RuntimeException(type + " cannot be reversed");
        }
        B1PostWork w = new B1PostWork();
        w.companyId = companyId;
        w.userId = userId;
        w.type = type;
        w.docId = id;
        w.reverse = 1;
        PostResultDto r = ErpPosting.runPostingWork(w);
        ErpDb.reconnect();
        if (r.ok == 0) {
            throw new RuntimeException(r.message);
        }
        return ErpB1Docs.get(companyId, type, id);
    }
}

class B1PostWork implements DbTransactionWork {
    long companyId;
    long userId;
    String type;
    long docId;
    int reverse;

    PostResultDto ok(String msg) {
        PostResultDto r = new PostResultDto();
        r.ok = 1;
        r.code = "OK";
        r.message = msg;
        r.documentJson = "{\"id\":" + ErpB1Util.lng(docId) + "}";
        return r;
    }

    String fail(String m) {
        return m;
    }

    public JnFuture run(DbConnection conn) {
        if (reverse != 0) {
            doReverse(conn);
            return JnFuture.completed(Erp.loop, ok("reversed"));
        }
        doPost(conn);
        return JnFuture.completed(Erp.loop, ok("posted"));
    }

    long acct(DbConnection conn, String key) {
        long a = ErpFinanceCore.mappedAccountIdConn(conn, companyId, key);
        if (a == 0L) {
            throw new TransactionException("account mapping missing: " + key);
        }
        return a;
    }

    /** Local-currency amounts per line (rounded per line so header = sum of lines). */
    long netLocal(B1Doc d, B1Line l) {
        return ErpB1Master.toLocal(l.netCents, d.rateMicro);
    }

    long taxLocal(B1Doc d, B1Line l) {
        return ErpB1Master.toLocal(l.taxCents, d.rateMicro);
    }

    void doPost(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        /* Row lock first (no-op update), then read the state. */
        ArrayList<Object> lk = ErpB1Util.params();
        lk.add(ErpB1Util.lng(docId));
        lk.add(ErpB1Util.lng(companyId));
        ErpDb.execConn(conn, "UPDATE mk_docs SET status=status WHERE id=? AND company_id=?", lk);
        ArrayList<Object> sp = ErpB1Util.params();
        sp.add(ErpB1Util.lng(docId));
        sp.add(ErpB1Util.lng(companyId));
        Object st = ErpDb.queryOneConn(conn, "SELECT status FROM mk_docs WHERE id=? AND company_id=?", sp, new StringMapper());
        if (st == null) {
            throw new TransactionException("not found");
        }
        if (((String) st).equals("POSTED")) {
            return;
        }
        if (((String) st).equals("DRAFT") == false) {
            throw new TransactionException("cannot post status " + (String) st);
        }
        B1Doc d = ErpB1DocPost.load(companyId, type, docId);
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            if (l.baseType.length() > 0) {
                ArrayList<Object> cp = ErpB1Util.params();
                cp.add(ErpB1Util.lng(companyId));
                cp.add(l.baseType);
                cp.add(ErpB1Util.lng(l.baseLineId));
                cp.add(ErpB1Util.lng(docId));
                long used = ErpNum.asLong(ErpDb.queryOneConn(conn, "SELECT COALESCE(SUM(l.quantity),0) FROM mk_doc_lines l JOIN mk_docs d ON d.id=l.doc_id WHERE l.company_id=? AND l.base_type=? AND l.base_line_id=? AND d.status<>'CANCELLED' AND d.id<>?", cp, new LongMapper()));
                long bq = ErpB1Docs.baseLineQty(companyId, l.baseType, l.baseId, l.baseLineId, l.itemId);
                if (l.quantity > bq - used) {
                    throw new TransactionException("CAS_CONFLICT: base quantity already consumed");
                }
            }
            i = i + 1;
        }
        long localNet = 0L;
        long localTax = 0L;
        i = 0;
        while (i < d.lines.size()) {
            B1Line l2 = (B1Line) d.lines.get(i);
            localNet = localNet + netLocal(d, l2);
            localTax = localTax + taxLocal(d, l2);
            i = i + 1;
        }
        long localTotal = localNet + localTax;
        if (localTotal != d.totalLocalCents) {
            throw new TransactionException("total mismatch (stale totals)");
        }
        long journalId = 0L;
        long subId = 0L;
        if (type.equals(ErpB1Docs.ARINV)) {
            journalId = postArInvoice(conn, d, localNet, localTax, localTotal, now);
            subId = upsertAr(conn, d, localTotal, journalId, now);
        } else if (type.equals(ErpB1Docs.ARCM)) {
            journalId = postArCreditMemo(conn, d, localNet, localTax, localTotal);
            subId = applyArCredit(conn, d, localTotal, now);
        } else if (type.equals(ErpB1Docs.APINV)) {
            journalId = postApInvoice(conn, d, localNet, localTax, localTotal);
            subId = upsertAp(conn, d, localTotal, journalId, now);
        } else {
            journalId = postApCreditMemo(conn, d, localNet, localTax, localTotal);
            subId = applyApCredit(conn, d, localTotal, now);
        }
        ArrayList<Object> up = ErpB1Util.params();
        up.add(ErpB1Util.lng(journalId));
        up.add(ErpB1Util.lng(subId));
        up.add(ErpB1Util.lng(now));
        up.add(ErpB1Util.lng(now));
        up.add(ErpB1Util.lng(docId));
        up.add(ErpB1Util.lng(companyId));
        ErpDb.execConn(conn, "UPDATE mk_docs SET status='POSTED', journal_id=?, subledger_id=?, posted_at=?, updated_at=? WHERE id=? AND company_id=? AND status='DRAFT'", up);
        if (d.baseType.length() > 0) {
            ArrayList<Object> ref = ErpB1Util.params();
            ref.add(ErpB1Util.lng(companyId));
            ref.add("B1_COPY");
            ref.add(d.baseType);
            ref.add(ErpB1Util.lng(d.baseId));
            ref.add(type);
            ref.add(ErpB1Util.lng(docId));
            ref.add(ErpB1Util.lng(now));
            ErpDb.execConn(conn, "INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)", ref);
        }
    }

    long journal(DbConnection conn, B1Doc d, String key, String desc, ArrayList<Object> lines) {
        PostingRequest req = new PostingRequest();
        req.sourceType = ErpB1Docs.isSales(type) != 0 ? JournalSource.AR_INVOICE : JournalSource.AP_INVOICE;
        req.sourceId = docId;
        req.postingDate = d.docDate;
        req.documentDate = d.docDate;
        req.description = desc + " " + d.docNo;
        req.idempotencyKey = key + ErpB1Util.lng(docId);
        req.lines = lines;
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, req);
        if (je.ok == 0) {
            throw new TransactionException(je.code + ": " + je.message);
        }
        return ErpGl.parseJeId(je.documentJson);
    }

    void addLine(ArrayList<Object> lines, long account, long debit, long credit, long bp, String desc) {
        if (debit == 0L && credit == 0L) {
            return;
        }
        lines.add(ErpFinanceCore.line(account, debit, credit, bp, desc));
    }

    long postArInvoice(DbConnection conn, B1Doc d, long net, long tax, long total, long now) {
        ArrayList<Object> lines = new ArrayList<Object>();
        addLine(lines, acct(conn, AccountMapKey.AR), total, 0L, d.partnerId, "A/R invoice");
        addLine(lines, acct(conn, AccountMapKey.REVENUE), 0L, net, 0L, "Revenue");
        if (tax > 0L) {
            addLine(lines, acct(conn, "OUTPUT_TAX"), 0L, tax, 0L, "Output VAT");
        }
        return journal(conn, d, "je-b1-arinv-", "A/R invoice", lines);
    }

    long postArCreditMemo(DbConnection conn, B1Doc d, long net, long tax, long total) {
        ArrayList<Object> lines = new ArrayList<Object>();
        addLine(lines, acct(conn, "SALES_RETURNS"), net, 0L, 0L, "Sales returns / allowances");
        if (tax > 0L) {
            addLine(lines, acct(conn, "OUTPUT_TAX"), tax, 0L, 0L, "Output VAT reversal");
        }
        addLine(lines, acct(conn, AccountMapKey.AR), 0L, total, d.partnerId, "A/R credit memo");
        return journal(conn, d, "je-b1-arcm-", "A/R credit memo", lines);
    }

    /** GR clearing value (local) of an invoiced goods-receipt line. */
    long grLineValue(DbConnection conn, B1Line l) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(l.baseLineId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.queryConn(conn, "SELECT COALESCE(NULLIF(g.unit_cost_amount,0), pl.unit_price_cents) FROM goods_receipt_lines g JOIN purchase_order_lines pl ON pl.id=g.purchase_order_line_id WHERE g.id=? AND g.company_id=?", p);
        if (r.rows.size() == 0) {
            throw new TransactionException("GR line not found");
        }
        return r.rows.get(0).getLong(0) * l.quantity;
    }

    long postApInvoice(DbConnection conn, B1Doc d, long net, long tax, long total) {
        long clearing = 0L;
        long variance = 0L;
        long expense = 0L;
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            long nl = netLocal(d, l);
            if (l.baseType.equals("GR") && l.isInventory != 0L) {
                long cv = grLineValue(conn, l);
                clearing = clearing + cv;
                variance = variance + (nl - cv);
                ArrayList<Object> cu = ErpB1Util.params();
                cu.add(ErpB1Util.lng(cv));
                cu.add(ErpB1Util.lng(l.id));
                ErpDb.execConn(conn, "UPDATE mk_doc_lines SET clearing_cents=? WHERE id=?", cu);
            } else {
                expense = expense + nl;
            }
            i = i + 1;
        }
        ArrayList<Object> lines = new ArrayList<Object>();
        addLine(lines, acct(conn, AccountMapKey.GR_CLEARING), clearing, 0L, 0L, "GR/IR clearing");
        if (variance > 0L) {
            addLine(lines, acct(conn, "PURCHASE_VARIANCE"), variance, 0L, 0L, "Purchase price variance");
        } else if (variance < 0L) {
            addLine(lines, acct(conn, "PURCHASE_VARIANCE"), 0L, 0L - variance, 0L, "Purchase price variance");
        }
        addLine(lines, acct(conn, "EXPENSE"), expense, 0L, 0L, "Purchase expense");
        if (tax > 0L) {
            addLine(lines, acct(conn, "INPUT_TAX"), tax, 0L, 0L, "Input VAT");
        }
        addLine(lines, acct(conn, AccountMapKey.AP), 0L, total, d.partnerId, "A/P invoice");
        return journal(conn, d, "je-b1-apinv-", "A/P invoice", lines);
    }

    long postApCreditMemo(DbConnection conn, B1Doc d, long net, long tax, long total) {
        long clearing = 0L;
        long variance = 0L;
        long expense = 0L;
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            long nl = netLocal(d, l);
            ArrayList<Object> bp = ErpB1Util.params();
            bp.add(ErpB1Util.lng(l.baseLineId));
            bp.add(ErpB1Util.lng(companyId));
            DbQueryResult br = ErpDb.queryConn(conn, "SELECT clearing_cents, quantity FROM mk_doc_lines WHERE id=? AND company_id=?", bp);
            long baseClearing = 0L;
            long baseQty = 1L;
            if (br.rows.size() > 0) {
                baseClearing = br.rows.get(0).getLong(0);
                baseQty = br.rows.get(0).getLong(1);
            }
            if (baseClearing > 0L && baseQty > 0L) {
                long share = ErpDocMoney.divHalfUp(baseClearing * l.quantity, baseQty);
                clearing = clearing + share;
                variance = variance + (nl - share);
            } else {
                expense = expense + nl;
            }
            i = i + 1;
        }
        ArrayList<Object> lines = new ArrayList<Object>();
        addLine(lines, acct(conn, AccountMapKey.AP), total, 0L, d.partnerId, "A/P credit memo");
        addLine(lines, acct(conn, AccountMapKey.GR_CLEARING), 0L, clearing, 0L, "GR/IR clearing");
        if (variance > 0L) {
            addLine(lines, acct(conn, "PURCHASE_VARIANCE"), 0L, variance, 0L, "Purchase price variance");
        } else if (variance < 0L) {
            addLine(lines, acct(conn, "PURCHASE_VARIANCE"), 0L - variance, 0L, 0L, "Purchase price variance");
        }
        addLine(lines, acct(conn, "EXPENSE"), 0L, expense, 0L, "Purchase expense");
        if (tax > 0L) {
            addLine(lines, acct(conn, "INPUT_TAX"), 0L, tax, 0L, "Input VAT reversal");
        }
        return journal(conn, d, "je-b1-apcm-", "A/P credit memo", lines);
    }

    /* ------------------------------------------------------------ sub-ledger */

    long upsertAr(DbConnection conn, B1Doc d, long total, long journalId, long now) {
        return upsertSub(conn, d, total, journalId, now, "ar_invoices", "customer_id", "source_delivery_id", "DL");
    }

    long upsertAp(DbConnection conn, B1Doc d, long total, long journalId, long now) {
        return upsertSub(conn, d, total, journalId, now, "ap_invoices", "supplier_id", "source_gr_id", "GR");
    }

    long upsertSub(DbConnection conn, B1Doc d, long total, long journalId, long now, String table, String partnerCol,
            String srcCol, String srcType) {
        long subId = 0L;
        if (d.baseType.equals(srcType)) {
            ArrayList<Object> p = ErpB1Util.params();
            p.add(ErpB1Util.lng(companyId));
            p.add(ErpB1Util.lng(d.baseId));
            DbQueryResult r = ErpDb.queryConn(conn, "SELECT id, paid_amount, adjusted_amount, is_provisional, invoice_number FROM " + table + " WHERE company_id=? AND " + srcCol + "=?", p);
            if (r.rows.size() > 0 && r.rows.get(0).getLong(3) != 0L) {
                subId = r.rows.get(0).getLong(0);
                long paid = r.rows.get(0).getLong(1);
                long adj = r.rows.get(0).getLong(2);
                String oldNo = r.rows.get(0).getString(4);
                long effective = total + adj;
                long open = effective - paid;
                if (open < 0L) {
                    throw new TransactionException("invoice total below amount already settled on provisional");
                }
                String settle = ErpFinanceSettlement.settlementOf(open, paid, effective);
                ArrayList<Object> up = ErpB1Util.params();
                up.add(d.docNo);
                up.add(ErpB1Util.lng(d.docDate));
                up.add(ErpB1Util.lng(d.dueDate));
                up.add(ErpB1Util.lng(total));
                up.add(ErpB1Util.lng(open));
                up.add(settle);
                up.add(ErpB1Util.lng(journalId));
                up.add(d.currency);
                up.add("B1 " + d.docNo + " PROV_NO=" + oldNo);
                up.add(ErpB1Util.lng(subId));
                up.add(ErpB1Util.lng(companyId));
                ErpDb.execConn(conn, "UPDATE " + table + " SET invoice_number=?, invoice_date=?, due_date=?, total_amount=?, open_amount=?, settlement_status=?, journal_entry_id=?, currency_code=?, notes=?, is_provisional=0, status='POSTED' WHERE id=? AND company_id=?", up);
                return subId;
            }
        }
        ArrayList<Object> ip = ErpB1Util.params();
        ip.add(ErpB1Util.lng(companyId));
        ip.add(ErpB1Util.lng(d.partnerId));
        ip.add(d.docNo);
        ip.add(ErpB1Util.lng(d.docDate));
        ip.add(ErpB1Util.lng(d.dueDate));
        ip.add(ErpB1Util.lng(total));
        ip.add(ErpB1Util.lng(total));
        ip.add(ErpB1Util.lng(now));
        ip.add(ErpB1Util.lng(journalId));
        ip.add(d.currency);
        ip.add("B1 " + d.docNo);
        return ErpDb.insertReturningIdConn(conn, "INSERT INTO " + table + "(company_id, " + partnerCol + ", invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, " + srcCol + ", created_at, is_provisional, journal_entry_id, settlement_status, adjusted_amount, currency_code, notes) VALUES (?,?,?,?,?,'CNY',?,0,?,'POSTED',0,?,0,?,'OPEN',0,?,?)", ip);
    }

    long baseSubledger(DbConnection conn, B1Doc d) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(d.baseId));
        p.add(ErpB1Util.lng(companyId));
        long s = ErpNum.asLong(ErpDb.queryOneConn(conn, "SELECT subledger_id FROM mk_docs WHERE id=? AND company_id=?", p, new LongMapper()));
        if (s <= 0L) {
            throw new TransactionException("base invoice has no sub-ledger row");
        }
        return s;
    }

    long applyArCredit(DbConnection conn, B1Doc d, long credit, long now) {
        return applyCredit(conn, d, credit, now, "AR", "ar_invoices", 1);
    }

    long applyApCredit(DbConnection conn, B1Doc d, long credit, long now) {
        return applyCredit(conn, d, credit, now, "AP", "ap_invoices", 1);
    }

    /** sign=1 applies the credit (lowers open); sign=-1 restores it (reversal). */
    long applyCredit(DbConnection conn, B1Doc d, long credit, long now, String side, String table, int sign) {
        long subId = baseSubledger(conn, d);
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(subId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.queryConn(conn, "SELECT open_amount, paid_amount, total_amount, adjusted_amount FROM " + table + " WHERE id=? AND company_id=?", p);
        if (r.rows.size() == 0) {
            throw new TransactionException("invoice sub-ledger row missing");
        }
        long open = r.rows.get(0).getLong(0);
        long paid = r.rows.get(0).getLong(1);
        long total = r.rows.get(0).getLong(2);
        long adj = r.rows.get(0).getLong(3);
        long delta = credit;
        if (sign < 0) {
            delta = 0L - credit;
        }
        long newOpen = open - delta;
        long newAdj = adj - delta;
        if (newOpen < 0L) {
            throw new TransactionException("CREDIT_EXCEEDS_OPEN: invoice already settled; refund or reverse the settlement first");
        }
        String settle = ErpFinanceSettlement.settlementOf(newOpen, paid, total + newAdj);
        ArrayList<Object> up = ErpB1Util.params();
        up.add(ErpB1Util.lng(newAdj));
        up.add(ErpB1Util.lng(newOpen));
        up.add(settle);
        up.add(ErpB1Util.lng(subId));
        up.add(ErpB1Util.lng(companyId));
        ErpDb.execConn(conn, "UPDATE " + table + " SET adjusted_amount=?, open_amount=?, settlement_status=? WHERE id=? AND company_id=?", up);
        ArrayList<Object> ap = ErpB1Util.params();
        ap.add(ErpB1Util.lng(companyId));
        ap.add(side);
        ap.add(ErpB1Util.lng(subId));
        ap.add(ErpB1Util.lng(0L - delta));
        ap.add(sign > 0 ? "credit memo " + d.docNo : "credit memo reversal " + d.docNo);
        ap.add("B1_CREDIT_MEMO");
        ap.add(ErpB1Util.lng(docId));
        ap.add(ErpB1Util.lng(userId));
        ap.add(ErpB1Util.lng(now));
        ap.add(ErpB1Util.lng(now));
        ap.add((sign > 0 ? "b1-cm-" : "b1-cmrev-") + ErpB1Util.lng(docId));
        ErpDb.execConn(conn, "INSERT INTO ar_ap_adjustments(company_id, side, invoice_id, adjust_amount, reason, source_type, source_id, created_by, biz_time, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?)", ap);
        return subId;
    }

    /* ------------------------------------------------------------ reversal */

    void doReverse(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> lk = ErpB1Util.params();
        lk.add(ErpB1Util.lng(docId));
        lk.add(ErpB1Util.lng(companyId));
        ErpDb.execConn(conn, "UPDATE mk_docs SET status=status WHERE id=? AND company_id=?", lk);
        B1Doc d = null;
        ArrayList<Object> hp = ErpB1Util.params();
        hp.add(ErpB1Util.lng(docId));
        hp.add(ErpB1Util.lng(companyId));
        hp.add(type);
        DbQueryResult hr = ErpDb.queryConn(conn, "SELECT status, journal_id, subledger_id, doc_no, partner_id, total_local_cents, base_id FROM mk_docs WHERE id=? AND company_id=? AND doc_type=?", hp);
        if (hr.rows.size() == 0) {
            throw new TransactionException("not found");
        }
        String status = hr.rows.get(0).getString(0);
        long journalId = hr.rows.get(0).getLong(1);
        long subId = hr.rows.get(0).getLong(2);
        String docNo = hr.rows.get(0).getString(3);
        long totalLocal = hr.rows.get(0).getLong(5);
        long baseId = hr.rows.get(0).getLong(6);
        if (status.equals("CANCELLED")) {
            return;
        }
        if (status.equals("POSTED") == false) {
            throw new TransactionException("only POSTED documents can be reversed (status=" + status + ")");
        }
        String table = "ar_invoices";
        String side = "AR";
        if (ErpB1Docs.isSales(type) == 0) {
            table = "ap_invoices";
            side = "AP";
        }
        if (type.equals(ErpB1Docs.ARINV) || type.equals(ErpB1Docs.APINV)) {
            ArrayList<Object> cp = ErpB1Util.params();
            cp.add(ErpB1Util.lng(companyId));
            cp.add(type);
            cp.add(ErpB1Util.lng(docId));
            long cms = ErpNum.asLong(ErpDb.queryOneConn(conn, "SELECT COUNT(*) FROM mk_docs WHERE company_id=? AND base_type=? AND base_id=? AND status<>'CANCELLED'", cp, new LongMapper()));
            if (cms > 0L) {
                throw new TransactionException("HAS_CREDIT_MEMO: reverse the credit memos first");
            }
            ArrayList<Object> sp = ErpB1Util.params();
            sp.add(ErpB1Util.lng(subId));
            sp.add(ErpB1Util.lng(companyId));
            DbQueryResult sr = ErpDb.queryConn(conn, "SELECT paid_amount, adjusted_amount, notes FROM " + table + " WHERE id=? AND company_id=?", sp);
            if (sr.rows.size() == 0) {
                throw new TransactionException("sub-ledger row missing");
            }
            if (sr.rows.get(0).getLong(0) != 0L || sr.rows.get(0).getLong(1) != 0L) {
                throw new TransactionException("HAS_SETTLEMENT: invoice has payments/adjustments; reverse them first");
            }
            String notes = sr.rows.get(0).getString(2);
            int pn = notes == null ? -1 : notes.indexOf("PROV_NO=");
            ArrayList<Object> up = ErpB1Util.params();
            if (pn >= 0) {
                /* Restore the provisional sub-ledger row created by the delivery / receipt. */
                up.add(notes.substring(pn + 8, notes.length()));
                up.add(ErpB1Util.lng(totalLocal));
                up.add(ErpB1Util.lng(subId));
                up.add(ErpB1Util.lng(companyId));
                ErpDb.execConn(conn, "UPDATE " + table + " SET invoice_number=?, is_provisional=1, status='PROVISIONAL', settlement_status='OPEN', open_amount=?, notes='PROVISIONAL restored after invoice reversal' WHERE id=? AND company_id=?", up);
            } else {
                up.add(ErpB1Util.lng(subId));
                up.add(ErpB1Util.lng(companyId));
                ErpDb.execConn(conn, "UPDATE " + table + " SET status='CANCELLED', open_amount=0, settlement_status='SETTLED' WHERE id=? AND company_id=?", up);
            }
        } else {
            /* credit memo: give the credit back to the base invoice */
            B1Doc cm = new B1Doc();
            cm.docNo = docNo;
            cm.baseId = baseId;
            applyCredit(conn, cm, totalLocal, now, side, table, -1);
        }
        if (journalId > 0L) {
            PostResultDto rv = ErpFinanceCore.reverseJournalConn(conn, companyId, userId, journalId,
                    "reverse " + docNo, "b1-rev-" + ErpB1Util.lng(docId));
            if (rv.ok == 0) {
                throw new TransactionException(rv.code + ": " + rv.message);
            }
        }
        ArrayList<Object> up2 = ErpB1Util.params();
        up2.add(ErpB1Util.lng(now));
        up2.add(ErpB1Util.lng(docId));
        up2.add(ErpB1Util.lng(companyId));
        ErpDb.execConn(conn, "UPDATE mk_docs SET status='CANCELLED', updated_at=? WHERE id=? AND company_id=? AND status='POSTED'", up2);
    }
}
