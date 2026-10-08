/**
 * Reverse posted GR / DL. Same transaction: status, inventory, order qty, audit.
 * Not the same as refund/return merchandise flows.
 */
class ErpReverse {
    static PostResultDto reverseGoodsReceipt(long companyId, long userId, long grId, String reason, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        ReverseGrWork work = new ReverseGrWork();
        work.companyId = companyId;
        work.userId = userId;
        work.grId = grId;
        work.reason = reason;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }

    static PostResultDto reverseDelivery(long companyId, long userId, long dlId, String reason, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        ReverseDlWork work = new ReverseDlWork();
        work.companyId = companyId;
        work.userId = userId;
        work.dlId = dlId;
        work.reason = reason;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }
}

class ReverseGrWork implements DbTransactionWork {
    long companyId;
    long userId;
    long grId;
    String reason;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (reason == null) {
            reason = "";
        }
        ArrayList<Object> hp = new ArrayList<Object>();
        hp.add(CryptoParse.longToString(grId));
        hp.add(CryptoParse.longToString(companyId));
        DbQueryResult hr = ErpDb.queryConn(conn,
                "SELECT doc_no, purchase_order_id, warehouse_id, status, reversed_by_id, reverses_id FROM goods_receipts WHERE id=? AND company_id=?",
                hp);
        if (hr.rows.size() == 0) {
            throw new TransactionException("GR not found");
        }
        Row hrow = hr.rows.get(0);
        String srcDocNo = hrow.getString(0);
        long poId = hrow.getLong(1);
        long warehouseId = hrow.getLong(2);
        String status = hrow.getString(3);
        long reversedBy = hrow.getLong(4);
        long reversesId = hrow.getLong(5);
        if (reversesId != 0L) {
            throw new TransactionException("BLOCKED:cannot reverse a reversal document");
        }
        if (reversedBy != 0L) {
            throw new TransactionException("BLOCKED:GR already reversed; reverseDocId="
                    + CryptoParse.longToString(reversedBy));
        }
        if (status.equals("POSTED") == false && status.equals(DocStatus.RECEIVED) == false) {
            if (status.equals("REVERSED")) {
                throw new TransactionException("BLOCKED:GR already reversed");
            }
            throw new TransactionException("GR not posted");
        }
        /* Downstream AP invoice linked to this GR blocks reverse */
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(companyId));
        ap.add(CryptoParse.longToString(grId));
        Object apCnt = ErpDb.queryOneConn(conn,
                "SELECT COUNT(*) FROM ap_invoices WHERE company_id=? AND source_gr_id=? AND status<>'CANCELLED'",
                ap, new LongMapper());
        if (ErpNum.asLong(apCnt) > 0L) {
            throw new TransactionException("BLOCKED:downstream AP invoice exists; cancel/settle AP first");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "RV");
        String docNo = "RV-GR-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(poId));
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add("POSTED");
        hdr.add(CryptoParse.longToString(now));
        long revId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO goods_receipts(company_id, doc_no, purchase_order_id, warehouse_id, status, created_at) VALUES (?,?,?,?,?,?)",
                hdr);
        ErpDb.execConn(conn,
                "UPDATE goods_receipts SET reverses_id=" + CryptoParse.longToString(grId)
                        + ", reverse_reason='" + ErpPlatformMeta.escapeLite(reason)
                        + "' WHERE id=" + CryptoParse.longToString(revId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(grId));
        lp.add(CryptoParse.longToString(companyId));
        DbQueryResult lines = ErpDb.queryConn(conn,
                "SELECT purchase_order_line_id, item_id, quantity FROM goods_receipt_lines WHERE goods_receipt_id=? AND company_id=?",
                lp);
        int i = 0;
        while (i < lines.rows.size()) {
            Row lrow = lines.rows.get(i);
            long poLineId = lrow.getLong(0);
            long itemId = lrow.getLong(1);
            int qty = lrow.getInt(2);
            ArrayList<Object> rcv = new ArrayList<Object>();
            rcv.add(CryptoParse.longToString(poLineId));
            rcv.add(CryptoParse.longToString(companyId));
            Object rcvObj = ErpDb.queryOneConn(conn,
                    "SELECT received_qty FROM purchase_order_lines WHERE id=? AND company_id=?", rcv, new IntMapper());
            int received = ErpNum.parseInt((String) rcvObj);
            if (received < qty) {
                throw new TransactionException("BLOCKED:PO received qty inconsistent");
            }
            int newRcv = received - qty;
            ErpDb.execConn(conn,
                    "UPDATE purchase_order_lines SET received_qty=" + CryptoParse.longToString(newRcv)
                            + " WHERE id=" + CryptoParse.longToString(poLineId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
            ErpDb.execConn(conn,
                    "INSERT INTO goods_receipt_lines(company_id, goods_receipt_id, purchase_order_line_id, item_id, quantity) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(revId) + ","
                            + CryptoParse.longToString(poLineId) + ","
                            + CryptoParse.longToString(itemId) + ","
                            + CryptoParse.longToString(0 - qty) + ")",
                    new ArrayList<Object>());
            ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, 0 - qty, 0L,
                    InvTxnType.ADJUSTMENT, "GR_REV", revId, now);
            i = i + 1;
        }
        ErpDb.execConn(conn,
                "UPDATE goods_receipts SET status='REVERSED', reversed_by_id="
                        + CryptoParse.longToString(revId)
                        + ", reverse_reason='" + ErpPlatformMeta.escapeLite(reason)
                        + "' WHERE id=" + CryptoParse.longToString(grId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpPosting.refreshPoStatusConn(conn, companyId, poId);
        ArrayList<Object> ref = new ArrayList<Object>();
        ref.add(CryptoParse.longToString(companyId));
        ref.add("GR_REVERSE");
        ref.add("GR");
        ref.add(CryptoParse.longToString(revId));
        ref.add("GR");
        ref.add(CryptoParse.longToString(grId));
        ref.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)",
                ref);
        ErpDb.execConn(conn,
                "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(userId)
                        + ",'REVERSE','GR',"
                        + CryptoParse.longToString(grId) + ",'"
                        + ErpPlatformMeta.escapeLite(srcDocNo + "=>" + docNo + ":" + reason) + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        String docJson = "{\"id\":" + ErpJson.num(revId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"reversesId\":" + ErpJson.num(grId) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "reversed";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "GR_REV", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ReverseDlWork implements DbTransactionWork {
    long companyId;
    long userId;
    long dlId;
    String reason;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (reason == null) {
            reason = "";
        }
        ArrayList<Object> hp = new ArrayList<Object>();
        hp.add(CryptoParse.longToString(dlId));
        hp.add(CryptoParse.longToString(companyId));
        DbQueryResult hr = ErpDb.queryConn(conn,
                "SELECT doc_no, sales_order_id, warehouse_id, status, reversed_by_id, reverses_id FROM deliveries WHERE id=? AND company_id=?",
                hp);
        if (hr.rows.size() == 0) {
            throw new TransactionException("DL not found");
        }
        Row hrow = hr.rows.get(0);
        String srcDocNo = hrow.getString(0);
        long soId = hrow.getLong(1);
        long warehouseId = hrow.getLong(2);
        String status = hrow.getString(3);
        long reversedBy = hrow.getLong(4);
        long reversesId = hrow.getLong(5);
        if (reversesId != 0L) {
            throw new TransactionException("BLOCKED:cannot reverse a reversal document");
        }
        if (reversedBy != 0L || status.equals("REVERSED")) {
            throw new TransactionException("BLOCKED:DL already reversed; reverseDocId="
                    + CryptoParse.longToString(reversedBy));
        }
        if (status.equals("POSTED") == false) {
            throw new TransactionException("DL not posted");
        }
        ArrayList<Object> ar = new ArrayList<Object>();
        ar.add(CryptoParse.longToString(companyId));
        ar.add(CryptoParse.longToString(dlId));
        Object arCnt = ErpDb.queryOneConn(conn,
                "SELECT COUNT(*) FROM ar_invoices WHERE company_id=? AND source_delivery_id=? AND status<>'CANCELLED'",
                ar, new LongMapper());
        if (ErpNum.asLong(arCnt) > 0L) {
            throw new TransactionException("BLOCKED:downstream AR invoice exists; cancel/settle AR first");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "RV");
        String docNo = "RV-DL-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(soId));
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add("POSTED");
        hdr.add(CryptoParse.longToString(now));
        long revId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO deliveries(company_id, doc_no, sales_order_id, warehouse_id, status, created_at) VALUES (?,?,?,?,?,?)",
                hdr);
        ErpDb.execConn(conn,
                "UPDATE deliveries SET reverses_id=" + CryptoParse.longToString(dlId)
                        + ", reverse_reason='" + ErpPlatformMeta.escapeLite(reason)
                        + "' WHERE id=" + CryptoParse.longToString(revId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(dlId));
        lp.add(CryptoParse.longToString(companyId));
        DbQueryResult lines = ErpDb.queryConn(conn,
                "SELECT sales_order_line_id, item_id, quantity FROM delivery_lines WHERE delivery_id=? AND company_id=?",
                lp);
        int i = 0;
        while (i < lines.rows.size()) {
            Row lrow = lines.rows.get(i);
            long soLineId = lrow.getLong(0);
            long itemId = lrow.getLong(1);
            int qty = lrow.getInt(2);
            ArrayList<Object> del = new ArrayList<Object>();
            del.add(CryptoParse.longToString(soLineId));
            del.add(CryptoParse.longToString(companyId));
            Object delObj = ErpDb.queryOneConn(conn,
                    "SELECT delivered_qty FROM sales_order_lines WHERE id=? AND company_id=?", del, new IntMapper());
            int delivered = ErpNum.parseInt((String) delObj);
            if (delivered < qty) {
                throw new TransactionException("BLOCKED:SO delivered qty inconsistent");
            }
            int newDel = delivered - qty;
            ErpDb.execConn(conn,
                    "UPDATE sales_order_lines SET delivered_qty=" + CryptoParse.longToString(newDel)
                            + " WHERE id=" + CryptoParse.longToString(soLineId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
            ErpDb.execConn(conn,
                    "INSERT INTO delivery_lines(company_id, delivery_id, sales_order_line_id, item_id, quantity) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(revId) + ","
                            + CryptoParse.longToString(soLineId) + ","
                            + CryptoParse.longToString(itemId) + ","
                            + CryptoParse.longToString(0 - qty) + ")",
                    new ArrayList<Object>());
            /* restore stock */
            long unitCost = ErpInventory.itemUnitCostConn(conn, companyId, itemId);
            ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, qty, unitCost,
                    InvTxnType.ADJUSTMENT, "DL_REV", revId, now);
            i = i + 1;
        }
        ErpDb.execConn(conn,
                "UPDATE deliveries SET status='REVERSED', reversed_by_id="
                        + CryptoParse.longToString(revId)
                        + ", reverse_reason='" + ErpPlatformMeta.escapeLite(reason)
                        + "' WHERE id=" + CryptoParse.longToString(dlId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpPosting.refreshSoStatusConn(conn, companyId, soId);
        ArrayList<Object> ref = new ArrayList<Object>();
        ref.add(CryptoParse.longToString(companyId));
        ref.add("DL_REVERSE");
        ref.add("DL");
        ref.add(CryptoParse.longToString(revId));
        ref.add("DL");
        ref.add(CryptoParse.longToString(dlId));
        ref.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)",
                ref);
        ErpDb.execConn(conn,
                "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(userId)
                        + ",'REVERSE','DL',"
                        + CryptoParse.longToString(dlId) + ",'"
                        + ErpPlatformMeta.escapeLite(srcDocNo + "=>" + docNo + ":" + reason) + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        String docJson = "{\"id\":" + ErpJson.num(revId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"reversesId\":" + ErpJson.num(dlId) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "reversed";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "DL_REV", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
