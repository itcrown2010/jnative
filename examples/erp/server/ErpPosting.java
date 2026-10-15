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
        String s = (String) v;
        if (s == null) {
            return "";
        }
        return s;
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
        Object cur = ErpDb.queryOneConn(conn, "SELECT status FROM purchase_orders WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (cur != null) {
            String cs = (String) cur;
            if (cs.equals(DocStatus.CLOSED) || cs.equals(DocStatus.CANCELLED)) {
                return;
            }
        }
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
        Object appr = ErpDb.queryOneConn(conn,
                "SELECT approval_status FROM sales_orders WHERE id=? AND company_id=?", p, new StringMapper());
        String approval = ApprovalStatus.APPROVED;
        if (appr != null) {
            approval = (String) appr;
        }
        if (approval.equals(ApprovalStatus.CANCELLED)) {
            return;
        }
        Object fulObj = ErpDb.queryOneConn(conn,
                "SELECT fulfillment_status FROM sales_orders WHERE id=? AND company_id=?", p, new StringMapper());
        if (fulObj != null && ((String) fulObj).equals(FulfillmentStatus.CLOSED)) {
            return;
        }
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
        String fulfillment = FulfillmentStatus.NONE;
        if (anyDelivered != 0) {
            fulfillment = FulfillmentStatus.PARTIAL;
        }
        if (allDelivered != 0 && lines.rows.size() > 0) {
            fulfillment = FulfillmentStatus.FULFILLED;
        }
        String compat = ErpSales.compositeStatus(approval, fulfillment);
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(fulfillment);
        up.add(compat);
        up.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        up.add(CryptoParse.longToString(soId));
        up.add(CryptoParse.longToString(companyId));
        ErpDb.execConn(conn,
                "UPDATE sales_orders SET fulfillment_status=?, status=?, updated_at=? WHERE id=? AND company_id=?",
                up);
    }
}

class GrPostWork implements DbTransactionWork {
    long companyId;
    PostGoodsReceiptRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(req.purchaseOrderId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult poHdr = ErpDb.queryConn(conn,
                "SELECT status, supplier_id, warehouse_id FROM purchase_orders WHERE id=? AND company_id=?", pp);
        if (poHdr == null || poHdr.rows == null || poHdr.rows.size() == 0) {
            throw new TransactionException("PO not found");
        }
        Row poRow = poHdr.rows.get(0);
        String poStatus = poRow.getString(0);
        if (poStatus == null) {
            poStatus = "";
        }
        long supplierId = poRow.getLong(1);
        long poWarehouseId = poRow.getLong(2);
        if (poStatus.equals(DocStatus.APPROVED) == false && poStatus.equals(DocStatus.PARTIALLY_RECEIVED) == false) {
            throw new TransactionException("PO not receivable status=" + poStatus);
        }
        long warehouseId = req.warehouseId;
        if (warehouseId <= 0L) {
            warehouseId = poWarehouseId;
        }
        if (warehouseId <= 0L) {
            throw new TransactionException("warehouse required");
        }
        if (req.lines == null || req.lines.size() == 0) {
            throw new TransactionException("GR lines required");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "GR");
        String docNo = "GR-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(req.purchaseOrderId));
        hdr.add(CryptoParse.longToString(warehouseId));
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
            /* CAS: concurrent GR cannot over-receive the same PO line */
            ArrayList<Object> cup = new ArrayList<Object>();
            cup.add(String.valueOf(newReceived));
            cup.add(CryptoParse.longToString(line.orderLineId));
            cup.add(CryptoParse.longToString(companyId));
            cup.add(String.valueOf(received));
            ErpDb.execConn(conn,
                    "UPDATE purchase_order_lines SET received_qty=? WHERE id=? AND company_id=? AND received_qty=?",
                    cup);
            ArrayList<Object> chk = new ArrayList<Object>();
            chk.add(CryptoParse.longToString(line.orderLineId));
            chk.add(CryptoParse.longToString(companyId));
            Object rcvNow = ErpDb.queryOneConn(conn,
                    "SELECT received_qty FROM purchase_order_lines WHERE id=? AND company_id=?", chk,
                    new IntMapper());
            if (rcvNow == null || ErpNum.parseInt((String) rcvNow) != newReceived) {
                throw new TransactionException("CAS_CONFLICT:PO line receive race");
            }
            ErpInventory.setItemUnitCostConn(conn, companyId, itemId, unitCost);
            long lineValue = ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId,
                    line.quantity, unitCost, InvTxnType.GOODS_RECEIPT, "GR", grId, now);
            try {
                ErpDb.execConn(conn,
                        "INSERT INTO goods_receipt_lines(company_id, goods_receipt_id, purchase_order_line_id, item_id, quantity, unit_cost_amount, line_value_amount) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(grId) + ","
                                + CryptoParse.longToString(line.orderLineId) + ","
                                + CryptoParse.longToString(itemId) + ","
                                + CryptoParse.longToString(line.quantity) + ","
                                + CryptoParse.longToString(unitCost) + ","
                                + CryptoParse.longToString(lineValue) + ")",
                        new ArrayList<Object>());
            } catch (Exception grLineEx) {
                ErpDb.execConn(conn,
                        "INSERT INTO goods_receipt_lines(company_id, goods_receipt_id, purchase_order_line_id, item_id, quantity) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(grId) + ","
                                + CryptoParse.longToString(line.orderLineId) + ","
                                + CryptoParse.longToString(itemId) + ","
                                + CryptoParse.longToString(line.quantity) + ")",
                        new ArrayList<Object>());
            }
            grValue = grValue + lineValue;
            i = i + 1;
        }
        if (grValue > 0L) {
            PostResultDto je = GoodsReceiptPosting.postConn(conn, companyId, 0L, grId, grValue);
            if (je.ok == 0) {
                throw new TransactionException(je.message);
            }
        }
        /* Provisional AP (sub-ledger only — not vendor invoice confirmation) */
        long apId = 0L;
        if (grValue > 0L) {
            apId = ErpFinanceArAp.createProvisionalApConn(conn, companyId, supplierId, grValue, grId, now);
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
        String docJson = "{\"id\":" + ErpJson.num(grId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"provisionalApId\":" + ErpJson.num(apId)
                + ",\"provisionalApCents\":" + ErpJson.num(grValue)
                + ",\"apKind\":\"PROVISIONAL\"}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted (provisional AP)";
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
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(req.salesOrderId));
        sp.add(CryptoParse.longToString(companyId));
        DbQueryResult soHdr = ErpDb.queryConn(conn,
                "SELECT approval_status, fulfillment_status, warehouse_id, customer_id FROM sales_orders WHERE id=? AND company_id=?",
                sp);
        if (soHdr == null || soHdr.rows == null || soHdr.rows.size() == 0) {
            throw new TransactionException("SO not found");
        }
        Row soRow = soHdr.rows.get(0);
        String approval = soRow.getString(0);
        String fulfillment = soRow.getString(1);
        long soWarehouseId = soRow.getLong(2);
        long customerId = soRow.getLong(3);
        if (approval == null) {
            approval = "";
        }
        if (fulfillment == null) {
            fulfillment = "";
        }
        if (approval.equals(ApprovalStatus.APPROVED) == false) {
            throw new TransactionException("SO not approved");
        }
        if (fulfillment.equals(FulfillmentStatus.CLOSED) || fulfillment.equals(FulfillmentStatus.FULFILLED)) {
            throw new TransactionException("SO not deliverable");
        }
        long warehouseId = req.warehouseId;
        if (warehouseId <= 0L) {
            warehouseId = soWarehouseId;
        }
        if (warehouseId <= 0L) {
            throw new TransactionException("warehouse required");
        }
        if (soWarehouseId > 0L && warehouseId != soWarehouseId) {
            throw new TransactionException("warehouse mismatch — reservation is warehouse-scoped");
        }
        if (req.lines == null || req.lines.size() == 0) {
            throw new TransactionException("DL lines required");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "DL");
        String docNo = "DL-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(req.salesOrderId));
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add("POSTED");
        hdr.add(CryptoParse.longToString(now));
        long dlId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO deliveries(company_id, doc_no, sales_order_id, warehouse_id, status, created_at) VALUES (?,?,?,?,?,?)",
                hdr);
        long cogsValue = 0L;
        long arValue = 0L;
        int i = 0;
        while (i < req.lines.size()) {
            PostLineRequest line = (PostLineRequest) req.lines.get(i);
            ArrayList<Object> lp = new ArrayList<Object>();
            lp.add(CryptoParse.longToString(line.orderLineId));
            lp.add(CryptoParse.longToString(companyId));
            lp.add(CryptoParse.longToString(req.salesOrderId));
            DbQueryResult lr = ErpDb.queryConn(conn,
                    "SELECT item_id, quantity, delivered_qty, unit_price_cents FROM sales_order_lines WHERE id=? AND company_id=? AND sales_order_id=?",
                    lp);
            if (lr.rows.size() == 0) {
                throw new TransactionException("SO line not found");
            }
            Row lrow = lr.rows.get(0);
            long itemId = lrow.getLong(0);
            int ordered = lrow.getInt(1);
            int delivered = lrow.getInt(2);
            long unitPrice = lrow.getLong(3);
            if (line.quantity <= 0) {
                throw new TransactionException("invalid qty");
            }
            if (delivered + line.quantity > ordered) {
                throw new TransactionException("over-delivery");
            }
            int onHand = ErpInventory.qtyOnHandConn(conn, companyId, warehouseId, itemId);
            if (onHand < line.quantity) {
                throw new TransactionException("insufficient stock");
            }
            /* Consume this order's reservation first; remainder must fit unreserved available */
            int lineRes = ErpReservation.lineActiveReservedConn(conn, companyId, line.orderLineId);
            int fromRes = lineRes;
            if (fromRes > line.quantity) {
                fromRes = line.quantity;
            }
            int unreservedNeed = line.quantity - fromRes;
            if (unreservedNeed > 0) {
                int avail = ErpReservation.availableConn(conn, companyId, warehouseId, itemId);
                if (unreservedNeed > avail) {
                    throw new TransactionException("insufficient available (other orders reserved)");
                }
            }
            ErpReservation.consumeLineConn(conn, companyId, line.orderLineId, warehouseId, fromRes, now);
            int newDelivered = delivered + line.quantity;
            ArrayList<Object> cup = new ArrayList<Object>();
            cup.add(String.valueOf(newDelivered));
            cup.add(CryptoParse.longToString(line.orderLineId));
            cup.add(CryptoParse.longToString(companyId));
            cup.add(String.valueOf(delivered));
            ErpDb.execConn(conn,
                    "UPDATE sales_order_lines SET delivered_qty=? WHERE id=? AND company_id=? AND delivered_qty=?",
                    cup);
            ArrayList<Object> dchk = new ArrayList<Object>();
            dchk.add(CryptoParse.longToString(line.orderLineId));
            dchk.add(CryptoParse.longToString(companyId));
            Object delNow = ErpDb.queryOneConn(conn,
                    "SELECT delivered_qty FROM sales_order_lines WHERE id=? AND company_id=?", dchk, new IntMapper());
            if (delNow == null || ErpNum.parseInt((String) delNow) != newDelivered) {
                throw new TransactionException("CAS_CONFLICT:SO line deliver race");
            }
            long moved = ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId,
                    0 - line.quantity, 0L, InvTxnType.DELIVERY, "DL", dlId, now);
            long unitCogs = 0L;
            if (line.quantity > 0) {
                unitCogs = moved / (0L + line.quantity);
            }
            ErpDb.execConn(conn,
                    "INSERT INTO delivery_lines(company_id, delivery_id, sales_order_line_id, item_id, quantity, unit_cost_amount, cogs_amount, cogs_returned_amount) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(dlId) + ","
                            + CryptoParse.longToString(line.orderLineId) + ","
                            + CryptoParse.longToString(itemId) + ","
                            + CryptoParse.longToString(line.quantity) + ","
                            + CryptoParse.longToString(unitCogs) + ","
                            + CryptoParse.longToString(moved) + ",0)",
                    new ArrayList<Object>());
            cogsValue = cogsValue + moved;
            arValue = arValue + (unitPrice * (long) line.quantity);
            i = i + 1;
        }
        if (cogsValue > 0L) {
            PostResultDto je = DeliveryCostPosting.postConn(conn, companyId, 0L, dlId, cogsValue);
            if (je.ok == 0) {
                throw new TransactionException(je.message);
            }
        }
        long arId = 0L;
        if (arValue > 0L) {
            arId = ErpFinanceArAp.createProvisionalArConn(conn, companyId, customerId, arValue, dlId, now);
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
        String docJson = "{\"id\":" + ErpJson.num(dlId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"provisionalArId\":" + ErpJson.num(arId)
                + ",\"provisionalArCents\":" + ErpJson.num(arValue)
                + ",\"arKind\":\"PROVISIONAL\"}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted (provisional AR)";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "DL", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
