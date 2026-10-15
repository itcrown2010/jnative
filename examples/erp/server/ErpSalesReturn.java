/**
 * Sales return linked to original delivery lines.
 * Does not reopen SO open-to-deliver qty.
 * @author 张晓达
 */
class ErpSalesReturn {
    static PostResultDto post(long companyId, long salesOrderId, long deliveryId, long warehouseId,
            int resellable, String notes, ArrayList<Object> lines, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        SrPostWork work = new SrPostWork();
        work.companyId = companyId;
        work.salesOrderId = salesOrderId;
        work.deliveryId = deliveryId;
        work.warehouseId = warehouseId;
        work.resellable = resellable;
        work.notes = notes == null ? "" : notes;
        work.lines = lines;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }
}

class SalesReturnLineInput {
    long deliveryLineId;
    int quantity;
}

class SrPostWork implements DbTransactionWork {
    long companyId;
    long salesOrderId;
    long deliveryId;
    long warehouseId;
    int resellable;
    String notes;
    ArrayList<Object> lines;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (lines == null || lines.size() == 0) {
            throw new TransactionException("return lines required");
        }
        ArrayList<Object> dp = new ArrayList<Object>();
        dp.add(CryptoParse.longToString(deliveryId));
        dp.add(CryptoParse.longToString(companyId));
        DbQueryResult dh = ErpDb.queryConn(conn,
                "SELECT sales_order_id, warehouse_id, status FROM deliveries WHERE id=? AND company_id=?", dp);
        if (dh.rows.size() == 0) {
            throw new TransactionException("delivery not found");
        }
        long soId = dh.rows.get(0).getLong(0);
        long dlWh = dh.rows.get(0).getLong(1);
        String dlSt = dh.rows.get(0).getString(2);
        if (dlSt.equals("POSTED") == false) {
            throw new TransactionException("delivery not posted");
        }
        if (salesOrderId > 0L && salesOrderId != soId) {
            throw new TransactionException("SO/delivery mismatch");
        }
        salesOrderId = soId;
        if (warehouseId <= 0L) {
            warehouseId = dlWh;
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "SR");
        String docNo = "SR-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(salesOrderId));
        hdr.add(CryptoParse.longToString(deliveryId));
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add("POSTED");
        hdr.add(String.valueOf(resellable));
        hdr.add(idempotencyKey);
        hdr.add(notes);
        hdr.add(CryptoParse.longToString(now));
        long srId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO sales_returns(company_id, doc_no, sales_order_id, delivery_id, warehouse_id, status, resellable, idempotency_key, notes, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                hdr);
        long creditCents = 0L;
        int i = 0;
        while (i < lines.size()) {
            SalesReturnLineInput in = (SalesReturnLineInput) lines.get(i);
            if (in.quantity <= 0) {
                throw new TransactionException("invalid return qty");
            }
            ArrayList<Object> lp = new ArrayList<Object>();
            lp.add(CryptoParse.longToString(in.deliveryLineId));
            lp.add(CryptoParse.longToString(companyId));
            lp.add(CryptoParse.longToString(deliveryId));
            DbQueryResult lr = ErpDb.queryConn(conn,
                    "SELECT id, sales_order_line_id, item_id, quantity, returned_qty, cogs_amount, cogs_returned_amount, unit_cost_amount FROM delivery_lines WHERE id=? AND company_id=? AND delivery_id=?",
                    lp);
            if (lr.rows.size() == 0) {
                lr = ErpDb.queryConn(conn,
                        "SELECT id, sales_order_line_id, item_id, quantity, returned_qty, 0, 0, 0 FROM delivery_lines WHERE id=? AND company_id=? AND delivery_id=?",
                        lp);
            }
            if (lr.rows.size() == 0) {
                throw new TransactionException("delivery line not found");
            }
            Row lrow = lr.rows.get(0);
            long soLineId = lrow.getLong(1);
            long itemId = lrow.getLong(2);
            int shipped = lrow.getInt(3);
            int alreadyReturned = lrow.getInt(4);
            long cogsAmt = 0L;
            long cogsReturned = 0L;
            long unitCogs = 0L;
            if (lrow.columnCount() > 5) {
                cogsAmt = lrow.getLong(5);
                cogsReturned = lrow.getLong(6);
                unitCogs = lrow.getLong(7);
            }
            if (alreadyReturned + in.quantity > shipped) {
                throw new TransactionException("over-return");
            }
            int remainQty = shipped - alreadyReturned;
            long remainCogs = cogsAmt - cogsReturned;
            if (remainCogs < 0L) {
                remainCogs = 0L;
            }
            /* Restore original issue cost — not today's moving average. Last partial takes remainder. */
            long restoreValue = 0L;
            if (cogsAmt > 0L) {
                if (in.quantity >= remainQty) {
                    restoreValue = remainCogs;
                } else {
                    restoreValue = (remainCogs * (0L + in.quantity)) / (0L + remainQty);
                }
            } else {
                if (unitCogs > 0L) {
                    restoreValue = unitCogs * (0L + in.quantity);
                } else {
                    /* Genuine zero-cost issue (e.g. opening at 0) — restore 0, never today's purchase price. */
                    restoreValue = 0L;
                }
            }
            int newRet = alreadyReturned + in.quantity;
            long newCogsRet = cogsReturned + restoreValue;
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(String.valueOf(newRet));
            up.add(CryptoParse.longToString(newCogsRet));
            up.add(CryptoParse.longToString(in.deliveryLineId));
            up.add(CryptoParse.longToString(companyId));
            up.add(String.valueOf(alreadyReturned));
            ErpDb.execConn(conn,
                    "UPDATE delivery_lines SET returned_qty=?, cogs_returned_amount=? WHERE id=? AND company_id=? AND returned_qty=?",
                    up);
            ArrayList<Object> solp = new ArrayList<Object>();
            solp.add(CryptoParse.longToString(soLineId));
            solp.add(CryptoParse.longToString(companyId));
            DbQueryResult sol = ErpDb.queryConn(conn,
                    "SELECT returned_qty, unit_price_cents FROM sales_order_lines WHERE id=? AND company_id=?",
                    solp);
            int soRet = 0;
            long unitPrice = 0L;
            if (sol.rows.size() > 0) {
                soRet = sol.rows.get(0).getInt(0);
                unitPrice = sol.rows.get(0).getLong(1);
            }
            int newSoRet = soRet + in.quantity;
            ErpDb.execConn(conn,
                    "UPDATE sales_order_lines SET returned_qty=" + CryptoParse.longToString(newSoRet)
                            + " WHERE id=" + CryptoParse.longToString(soLineId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
            ArrayList<Object> srl = new ArrayList<Object>();
            srl.add(CryptoParse.longToString(companyId));
            srl.add(CryptoParse.longToString(srId));
            srl.add(CryptoParse.longToString(in.deliveryLineId));
            srl.add(CryptoParse.longToString(soLineId));
            srl.add(CryptoParse.longToString(itemId));
            srl.add(String.valueOf(in.quantity));
            ErpDb.execConn(conn,
                    "INSERT INTO sales_return_lines(company_id, sales_return_id, delivery_line_id, sales_order_line_id, item_id, quantity) VALUES (?,?,?,?,?,?)",
                    srl);
            if (resellable != 0) {
                ErpInventory.applyInboundExactValueConn(conn, companyId, warehouseId, itemId, in.quantity,
                        restoreValue, InvTxnType.SALES_RETURN, "SR", srId, in.deliveryLineId, now, 0L,
                        "restore original DL COGS");
            }
            creditCents = creditCents + (unitPrice * (long) in.quantity);
            i = i + 1;
        }
        if (creditCents > 0L) {
            ErpFinanceArAp.adjustArOpenConn(conn, companyId, deliveryId, creditCents, srId, now);
        }
        ArrayList<Object> ref = new ArrayList<Object>();
        ref.add(CryptoParse.longToString(companyId));
        ref.add(DocRefKind.SR_TO_DL);
        ref.add("SR");
        ref.add(CryptoParse.longToString(srId));
        ref.add("DL");
        ref.add(CryptoParse.longToString(deliveryId));
        ref.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)",
                ref);
        String docJson = "{\"id\":" + ErpJson.num(srId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"creditCents\":" + ErpJson.num(creditCents) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "sales return posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "SR", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
