/**
 * @author 张晓达
 */
class ErpPosting {
    static int isPgWireError(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return 0;
        }
        String msg = ex.getMessage();
        if (msg.indexOf("08P01") >= 0) {
            return 1;
        }
        if (msg.indexOf("invalid message format") >= 0) {
            return 1;
        }
        /* SQLite native driver transient prepare/bind failures after long sessions */
        if (msg.indexOf("prepare returned null") >= 0) {
            return 1;
        }
        if (msg.indexOf("incomplete input") >= 0) {
            return 1;
        }
        if (msg.indexOf("bind_text failed") >= 0) {
            return 1;
        }
        if (msg.indexOf("not an error") >= 0) {
            return 1;
        }
        return 0;
    }

    static PostResultDto runPostingWork(DbTransactionWork work) {
        int attempt = 0;
        while (attempt < 4) {
            try {
                JnFuture f = Erp.db.transaction(work);
                FutureBlocker.blockingWait(f);
                if (f.getError() != null) {
                    throw f.getError();
                }
                Object v = f.getValue();
                if (v == null) {
                    PostResultDto bad = new PostResultDto();
                    bad.ok = 0;
                    bad.code = "POST_NULL";
                    bad.message = "transaction returned null";
                    return bad;
                }
                return (PostResultDto) v;
            } catch (Exception ex) {
                String msg = "";
                if (ex.getMessage() != null) {
                    msg = ex.getMessage();
                }
                if (attempt < 3 && (isPgWireError(ex) != 0 || msg.length() == 0)) {
                    System.out.println("POST_RETRY attempt=" + CryptoParse.longToString(attempt)
                            + " msg=" + msg);
                    ErpDb.reconnect();
                    attempt = attempt + 1;
                    continue;
                }
                PostResultDto bad = new PostResultDto();
                bad.ok = 0;
                bad.code = "POST_FAILED";
                if (msg.length() > 0) {
                    bad.message = msg;
                } else {
                    bad.message = "post failed";
                }
                return bad;
            }
        }
        PostResultDto bad = new PostResultDto();
        bad.ok = 0;
        bad.code = "POST_FAILED";
        bad.message = "post failed";
        return bad;
    }

    static PostResultDto postGoodsReceipt(long companyId, PostGoodsReceiptRequest req) {
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        GrPostWork work = new GrPostWork();
        work.companyId = companyId;
        work.req = req;
        return runPostingWork(work);
    }

    static PostResultDto postDelivery(long companyId, PostDeliveryRequest req) {
        if (req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        DlPostWork work = new DlPostWork();
        work.companyId = companyId;
        work.req = req;
        return runPostingWork(work);
    }

    static String cachedIdempotencyConn(DbConnection conn, long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        Object v = ErpDb.queryOneConn(conn,
                "SELECT response_json FROM idempotency_keys WHERE company_id=? AND idempotency_key=?",
                p, new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }

    static void storeIdempotencyConn(DbConnection conn, long companyId, String key, String operation, String json,
            long now) {
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(companyId));
        ins.add(key);
        ins.add(operation);
        ins.add(json);
        ins.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO idempotency_keys(company_id, idempotency_key, operation, response_json, created_at) VALUES (?,?,?,?,?)",
                ins);
    }

    static PostResultDto resultFromJson(String json) {
        PostResultDto r = new PostResultDto();
        r.ok = 1;
        r.code = "OK";
        r.message = "idempotent replay";
        r.documentJson = json;
        return r;
    }

    static void refreshPoStatusConn(DbConnection conn, long companyId, long poId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult lines = ErpDb.queryConn(conn,
                "SELECT quantity, received_qty FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=?",
                p);
        int allReceived = 1;
        int anyReceived = 0;
        int i = 0;
        while (i < lines.rows.size()) {
            Row row = lines.rows.get(i);
            int qty = row.getInt(0);
            int rcv = row.getInt(1);
            if (rcv < qty) {
                allReceived = 0;
            }
            if (rcv > 0) {
                anyReceived = 1;
            }
            i = i + 1;
        }
        String st = DocStatus.APPROVED;
        if (anyReceived != 0) {
            st = DocStatus.PARTIALLY_RECEIVED;
        }
        if (allReceived != 0 && lines.rows.size() > 0) {
            st = DocStatus.RECEIVED;
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(st);
        up.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        up.add(CryptoParse.longToString(poId));
        up.add(CryptoParse.longToString(companyId));
        ErpDb.execConn(conn, "UPDATE purchase_orders SET status=?, updated_at=? WHERE id=? AND company_id=?", up);
    }

    static void refreshSoStatusConn(DbConnection conn, long companyId, long soId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult lines = ErpDb.queryConn(conn,
                "SELECT quantity, delivered_qty FROM sales_order_lines WHERE sales_order_id=? AND company_id=?",
                p);
        int allDelivered = 1;
        int anyDelivered = 0;
        int i = 0;
        while (i < lines.rows.size()) {
            Row row = lines.rows.get(i);
            int qty = row.getInt(0);
            int del = row.getInt(1);
            if (del < qty) {
                allDelivered = 0;
            }
            if (del > 0) {
                anyDelivered = 1;
            }
            i = i + 1;
        }
        String st = DocStatus.APPROVED;
        if (anyDelivered != 0) {
            st = DocStatus.PARTIALLY_DELIVERED;
        }
        if (allDelivered != 0 && lines.rows.size() > 0) {
            st = DocStatus.DELIVERED;
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(st);
        up.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        up.add(CryptoParse.longToString(soId));
        up.add(CryptoParse.longToString(companyId));
        ErpDb.execConn(conn, "UPDATE sales_orders SET status=?, updated_at=? WHERE id=? AND company_id=?", up);
    }
}

class GrPostWork implements DbTransactionWork {
    long companyId;
    PostGoodsReceiptRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(req.purchaseOrderId));
        pp.add(CryptoParse.longToString(companyId));
        Object pst = ErpDb.queryOneConn(conn, "SELECT status FROM purchase_orders WHERE id=? AND company_id=?", pp,
                new StringMapper());
        if (pst == null) {
            throw new TransactionException("PO not found");
        }
        String poStatus = (String) pst;
        if (poStatus.equals(DocStatus.APPROVED) == false && poStatus.equals(DocStatus.PARTIALLY_RECEIVED) == false) {
            throw new TransactionException("PO not receivable");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "GR");
        String docNo = "GR-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(req.purchaseOrderId));
        hdr.add(CryptoParse.longToString(req.warehouseId));
        hdr.add("POSTED");
        hdr.add(CryptoParse.longToString(now));
        long grId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO goods_receipts(company_id, doc_no, purchase_order_id, warehouse_id, status, created_at) VALUES (?,?,?,?,?,?)",
                hdr);
        long grValue = 0L;
        int i = 0;
        while (i < req.lines.size()) {
            PostLineRequest line = (PostLineRequest) req.lines.get(i);
            ArrayList<Object> lp = new ArrayList<Object>();
            lp.add(CryptoParse.longToString(line.orderLineId));
            lp.add(CryptoParse.longToString(companyId));
            lp.add(CryptoParse.longToString(req.purchaseOrderId));
            DbQueryResult lr = ErpDb.queryConn(conn,
                    "SELECT item_id, quantity, received_qty, unit_price_cents FROM purchase_order_lines WHERE id=? AND company_id=? AND purchase_order_id=?",
                    lp);
            if (lr.rows.size() == 0) {
                throw new TransactionException("PO line not found");
            }
            Row lrow = lr.rows.get(0);
            long itemId = lrow.getLong(0);
            int ordered = lrow.getInt(1);
            int received = lrow.getInt(2);
            long unitCost = lrow.getLong(3);
            if (line.quantity <= 0) {
                throw new TransactionException("invalid qty");
            }
            if (received + line.quantity > ordered) {
                throw new TransactionException("over-receive");
            }
            int newReceived = received + line.quantity;
            ErpDb.execConn(conn,
                    "UPDATE purchase_order_lines SET received_qty=" + CryptoParse.longToString(newReceived)
                            + " WHERE id=" + CryptoParse.longToString(line.orderLineId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
            ErpDb.execConn(conn,
                    "INSERT INTO goods_receipt_lines(company_id, goods_receipt_id, purchase_order_line_id, item_id, quantity) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(grId) + ","
                            + CryptoParse.longToString(line.orderLineId) + ","
                            + CryptoParse.longToString(itemId) + ","
                            + CryptoParse.longToString(line.quantity) + ")",
                    new ArrayList<Object>());
            ErpInventory.setItemUnitCostConn(conn, companyId, itemId, unitCost);
            long lineValue = ErpInventory.applyDeltaWithCostConn(conn, companyId, req.warehouseId, itemId,
                    line.quantity, unitCost, InvTxnType.GOODS_RECEIPT, "GR", grId, now);
            grValue = grValue + lineValue;
            i = i + 1;
        }
        if (grValue > 0L) {
            PostResultDto je = GoodsReceiptPosting.postConn(conn, companyId, 0L, grId, grValue);
            if (je.ok == 0) {
                throw new TransactionException(je.message);
            }
        }
        ErpPosting.refreshPoStatusConn(conn, companyId, req.purchaseOrderId);
        ArrayList<Object> ref = new ArrayList<Object>();
        ref.add(CryptoParse.longToString(companyId));
        ref.add(DocRefKind.GR_TO_PO);
        ref.add("GR");
        ref.add(CryptoParse.longToString(grId));
        ref.add("PO");
        ref.add(CryptoParse.longToString(req.purchaseOrderId));
        ref.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)",
                ref);
        String docJson = "{\"id\":" + ErpJson.num(grId) + ",\"docNo\":" + ErpJson.str(docNo) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "GR", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class DlPostWork implements DbTransactionWork {
    long companyId;
    PostDeliveryRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(req.salesOrderId));
        sp.add(CryptoParse.longToString(companyId));
        Object sst = ErpDb.queryOneConn(conn, "SELECT status FROM sales_orders WHERE id=? AND company_id=?", sp,
                new StringMapper());
        if (sst == null) {
            throw new TransactionException("SO not found");
        }
        String soStatus = (String) sst;
        if (soStatus.equals(DocStatus.APPROVED) == false
                && soStatus.equals(DocStatus.PARTIALLY_DELIVERED) == false) {
            throw new TransactionException("SO not deliverable");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "DL");
        String docNo = "DL-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(req.salesOrderId));
        hdr.add(CryptoParse.longToString(req.warehouseId));
        hdr.add("POSTED");
        hdr.add(CryptoParse.longToString(now));
        long dlId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO deliveries(company_id, doc_no, sales_order_id, warehouse_id, status, created_at) VALUES (?,?,?,?,?,?)",
                hdr);
        long cogsValue = 0L;
        int i = 0;
        while (i < req.lines.size()) {
            PostLineRequest line = (PostLineRequest) req.lines.get(i);
            ArrayList<Object> lp = new ArrayList<Object>();
            lp.add(CryptoParse.longToString(line.orderLineId));
            lp.add(CryptoParse.longToString(companyId));
            lp.add(CryptoParse.longToString(req.salesOrderId));
            DbQueryResult lr = ErpDb.queryConn(conn,
                    "SELECT item_id, quantity, delivered_qty FROM sales_order_lines WHERE id=? AND company_id=? AND sales_order_id=?",
                    lp);
            if (lr.rows.size() == 0) {
                throw new TransactionException("SO line not found");
            }
            Row lrow = lr.rows.get(0);
            long itemId = lrow.getLong(0);
            int ordered = lrow.getInt(1);
            int delivered = lrow.getInt(2);
            if (line.quantity <= 0) {
                throw new TransactionException("invalid qty");
            }
            if (delivered + line.quantity > ordered) {
                throw new TransactionException("over-delivery");
            }
            int onHand = ErpInventory.qtyOnHandConn(conn, companyId, req.warehouseId, itemId);
            if (onHand < line.quantity) {
                throw new TransactionException("insufficient stock");
            }
            int newDelivered = delivered + line.quantity;
            ErpDb.execConn(conn,
                    "UPDATE sales_order_lines SET delivered_qty=" + CryptoParse.longToString(newDelivered)
                            + " WHERE id=" + CryptoParse.longToString(line.orderLineId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
            ErpDb.execConn(conn,
                    "INSERT INTO delivery_lines(company_id, delivery_id, sales_order_line_id, item_id, quantity) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(dlId) + ","
                            + CryptoParse.longToString(line.orderLineId) + ","
                            + CryptoParse.longToString(itemId) + ","
                            + CryptoParse.longToString(line.quantity) + ")",
                    new ArrayList<Object>());
            long moved = ErpInventory.applyDeltaWithCostConn(conn, companyId, req.warehouseId, itemId,
                    0 - line.quantity, 0L, InvTxnType.DELIVERY, "DL", dlId, now);
            cogsValue = cogsValue + moved;
            i = i + 1;
        }
        if (cogsValue > 0L) {
            PostResultDto je = DeliveryCostPosting.postConn(conn, companyId, 0L, dlId, cogsValue);
            if (je.ok == 0) {
                throw new TransactionException(je.message);
            }
        }
        ErpPosting.refreshSoStatusConn(conn, companyId, req.salesOrderId);
        ArrayList<Object> ref = new ArrayList<Object>();
        ref.add(CryptoParse.longToString(companyId));
        ref.add(DocRefKind.DL_TO_SO);
        ref.add("DL");
        ref.add(CryptoParse.longToString(dlId));
        ref.add("SO");
        ref.add(CryptoParse.longToString(req.salesOrderId));
        ref.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)",
                ref);
        String docJson = "{\"id\":" + ErpJson.num(dlId) + ",\"docNo\":" + ErpJson.str(docNo) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "DL", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
