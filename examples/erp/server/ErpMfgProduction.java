/**
 * Production orders: release (lock BOM), issue, return, receipt, WIP, scrap.
 * Inventory changes only via ErpInventory.applyDeltaWithCostConn.
 * @author 张晓达
 */
class ErpMfgProduction {
    static ProductionOrderDto createPlanned(long companyId, long itemId, long warehouseId, int plannedQty,
            long bomId) {
        if (plannedQty <= 0) {
            return null;
        }
        BomDto bom = ErpMfgBom.getBom(companyId, bomId);
        if (bom == null) {
            return null;
        }
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNo(companyId, "MO");
        String no = "MO-" + CryptoParse.longToString(seq);
        String ins = "INSERT INTO production_orders(company_id, order_number, item_id, bom_id, bom_version, planned_quantity, completed_quantity, scrapped_quantity, status, warehouse_id, planned_start_date, planned_end_date, actual_start_date, actual_end_date, over_issue_tolerance_bp, created_at, updated_at) VALUES ("
                + CryptoParse.longToString(companyId) + ",'"
                + no + "',"
                + CryptoParse.longToString(itemId) + ","
                + CryptoParse.longToString(bomId) + ","
                + CryptoParse.longToString(bom.version) + ","
                + CryptoParse.longToString(plannedQty) + ",0,0,'"
                + ProductionOrderStatus.PLANNED + "',"
                + CryptoParse.longToString(warehouseId) + ","
                + CryptoParse.longToString(now) + ","
                + CryptoParse.longToString(now) + ",0,0,0,"
                + CryptoParse.longToString(now) + ","
                + CryptoParse.longToString(now) + ")";
        ErpDb.exec(ins, new ArrayList<Object>());
        ArrayList<Object> qp = new ArrayList<Object>();
        qp.add(CryptoParse.longToString(companyId));
        qp.add(no);
        Object idObj = ErpDb.queryOne(
                "SELECT id FROM production_orders WHERE company_id=? AND order_number=?", qp, new LongMapper());
        return getOrder(companyId, ErpNum.asLong(idObj));
    }

    static ProductionOrderDto release(long companyId, long orderId) {
        ProductionOrderDto po = getOrder(companyId, orderId);
        if (po == null) {
            return null;
        }
        if (po.status.equals(ProductionOrderStatus.PLANNED) == false) {
            throw new TransactionException("only PLANNED can release");
        }
        BomDto bom = ErpMfgBom.getBom(companyId, po.bomId);
        if (bom == null || bom.status.equals(BomStatus.ACTIVE) == false) {
            throw new TransactionException("BOM version not active");
        }
        long now = AsyncClockNative.wallMillis();
        /* Lock version already stored on order; materialize requirements from locked BOM lines (single-level for components; multi-level explode for leaves). */
        ArrayList<Object> leaves = ErpMfgBom.explode(companyId, po.itemId, po.plannedQuantity, now);
        int i = 0;
        while (i < leaves.size()) {
            BomExplosionLine ln = (BomExplosionLine) leaves.get(i);
            String cins = "INSERT INTO production_order_components(company_id, production_order_id, component_item_id, required_quantity, issued_quantity, returned_quantity, warehouse_id, unit_cost_amount) VALUES ("
                    + CryptoParse.longToString(companyId) + ","
                    + CryptoParse.longToString(orderId) + ","
                    + CryptoParse.longToString(ln.componentItemId) + ","
                    + CryptoParse.longToString(ln.requiredQuantity) + ",0,0,"
                    + CryptoParse.longToString(po.warehouseId) + ",0)";
            ErpDb.exec(cins, new ArrayList<Object>());
            i = i + 1;
        }
        ErpDb.exec(
                "INSERT INTO production_wip(production_order_id, company_id, material_cost, labor_cost, overhead_cost, transferred_cost, remaining_cost) VALUES ("
                        + CryptoParse.longToString(orderId) + ","
                        + CryptoParse.longToString(companyId) + ",0,0,0,0,0)",
                new ArrayList<Object>());
        ErpDb.exec(
                "UPDATE production_orders SET status='" + ProductionOrderStatus.RELEASED + "', updated_at="
                        + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(orderId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpDb.exec(
                "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",0,'RELEASE','PRODUCTION_ORDER',"
                        + CryptoParse.longToString(orderId) + ",'release locked BOM v"
                        + CryptoParse.longToString(po.bomVersion) + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        return getOrder(companyId, orderId);
    }

    static ProductionOrderDto getOrder(long companyId, long orderId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orderId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, order_number, item_id, bom_id, bom_version, planned_quantity, completed_quantity, scrapped_quantity, status, warehouse_id FROM production_orders WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            return null;
        }
        Row row = r.rows.get(0);
        ProductionOrderDto d = new ProductionOrderDto();
        d.id = row.getLong(0);
        d.orderNumber = row.getString(1);
        d.itemId = row.getLong(2);
        d.bomId = row.getLong(3);
        d.bomVersion = row.getInt(4);
        d.plannedQuantity = row.getInt(5);
        d.completedQuantity = row.getInt(6);
        d.scrappedQuantity = row.getInt(7);
        d.status = row.getString(8);
        d.warehouseId = row.getLong(9);
        d.components = loadComponents(companyId, orderId);
        return d;
    }

    static ArrayList<Object> loadComponents(long companyId, long orderId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(orderId));
        DbQueryResult r = ErpDb.query(
                "SELECT component_item_id, required_quantity, issued_quantity, returned_quantity, warehouse_id FROM production_order_components WHERE company_id=? AND production_order_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            ProductionComponentDto c = new ProductionComponentDto();
            c.componentItemId = row.getLong(0);
            c.requiredQuantity = row.getInt(1);
            c.issuedQuantity = row.getInt(2);
            c.returnedQuantity = row.getInt(3);
            c.warehouseId = row.getLong(4);
            out.add(c);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> listOrders(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id FROM production_orders WHERE company_id=? ORDER BY id", p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            out.add(getOrder(companyId, r.rows.get(i).getLong(0)));
            i = i + 1;
        }
        return out;
    }

    static PostResultDto postIssue(long companyId, long userId, ProductionIssueRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        MfgIssueWork work = new MfgIssueWork();
        work.companyId = companyId;
        work.userId = userId;
        work.req = req;
        return ErpFinanceArAp.runPostWork(work);
    }

    static PostResultDto postReceipt(long companyId, long userId, ProductionReceiptRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        MfgReceiptWork work = new MfgReceiptWork();
        work.companyId = companyId;
        work.userId = userId;
        work.req = req;
        return ErpFinanceArAp.runPostWork(work);
    }

    static WipDto getWip(long companyId, long orderId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orderId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT production_order_id, material_cost, labor_cost, overhead_cost, transferred_cost, remaining_cost FROM production_wip WHERE production_order_id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            return null;
        }
        Row row = r.rows.get(0);
        WipDto w = new WipDto();
        w.productionOrderId = row.getLong(0);
        w.materialCost = row.getLong(1);
        w.laborCost = row.getLong(2);
        w.overheadCost = row.getLong(3);
        w.transferredCost = row.getLong(4);
        w.remainingCost = row.getLong(5);
        return w;
    }

    static void refreshWipRemainingConn(DbConnection conn, long orderId, long companyId) {
        ErpDb.execConn(conn,
                "UPDATE production_wip SET remaining_cost = material_cost + labor_cost + overhead_cost - transferred_cost WHERE production_order_id="
                        + CryptoParse.longToString(orderId) + " AND company_id="
                        + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
    }

    static void refreshOrderStatusConn(DbConnection conn, long companyId, long orderId, long now) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orderId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT planned_quantity, completed_quantity, status FROM production_orders WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            return;
        }
        Row row = r.rows.get(0);
        int planned = row.getInt(0);
        int completed = row.getInt(1);
        String st = row.getString(2);
        String next = st;
        if (completed <= 0) {
            if (st.equals(ProductionOrderStatus.RELEASED) == false
                    && st.equals(ProductionOrderStatus.IN_PROGRESS) == false) {
                next = st;
            } else {
                next = ProductionOrderStatus.IN_PROGRESS;
            }
        } else {
            if (completed < planned) {
                next = ProductionOrderStatus.PARTIALLY_COMPLETED;
            } else {
                next = ProductionOrderStatus.COMPLETED;
            }
        }
        ErpDb.execConn(conn,
                "UPDATE production_orders SET status='" + next + "', updated_at="
                        + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(orderId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
    }
}

class MfgIssueWork implements DbTransactionWork {
    long companyId;
    long userId;
    ProductionIssueRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> op = new ArrayList<Object>();
        op.add(CryptoParse.longToString(req.productionOrderId));
        op.add(CryptoParse.longToString(companyId));
        DbQueryResult or = ErpDb.queryConn(conn,
                "SELECT status, warehouse_id, over_issue_tolerance_bp, planned_quantity FROM production_orders WHERE id=? AND company_id=?",
                op);
        if (or.rows.size() == 0) {
            throw new TransactionException("production order not found");
        }
        Row orow = or.rows.get(0);
        String status = orow.getString(0);
        int tolBp = orow.getInt(2);
        if (status.equals(ProductionOrderStatus.RELEASED) == false
                && status.equals(ProductionOrderStatus.IN_PROGRESS) == false
                && status.equals(ProductionOrderStatus.PARTIALLY_COMPLETED) == false) {
            throw new TransactionException("order not issuable");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "PI");
        String docNo = "PI-" + CryptoParse.longToString(seq);
        long postingDate = req.postingDate;
        if (postingDate == 0L) {
            postingDate = now;
        }
        long issueId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO production_issues(company_id, doc_no, production_order_id, warehouse_id, posting_date, status, idempotency_key, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'"
                        + docNo + "',"
                        + CryptoParse.longToString(req.productionOrderId) + ","
                        + CryptoParse.longToString(req.warehouseId) + ","
                        + CryptoParse.longToString(postingDate) + ",'"
                        + MfgDocStatus.POSTED + "','"
                        + req.idempotencyKey + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        long totalAmount = 0L;
        int li = 0;
        while (li < req.lines.size()) {
            ProductionIssueLineInput line = (ProductionIssueLineInput) req.lines.get(li);
            if (line.quantity <= 0) {
                throw new TransactionException("issue qty invalid");
            }
            ArrayList<Object> cp = new ArrayList<Object>();
            cp.add(CryptoParse.longToString(req.productionOrderId));
            cp.add(CryptoParse.longToString(companyId));
            cp.add(CryptoParse.longToString(line.componentItemId));
            String lockSql = "SELECT required_quantity, issued_quantity, returned_quantity FROM production_order_components WHERE production_order_id=? AND company_id=? AND component_item_id=?";
            if (Erp.dialect.equals("postgresql")) {
                lockSql = lockSql + " FOR UPDATE";
            }
            DbQueryResult cr = ErpDb.queryConn(conn, lockSql, cp);
            if (cr.rows.size() == 0) {
                throw new TransactionException("component not on order");
            }
            Row crow = cr.rows.get(0);
            int required = crow.getInt(0);
            int issued = crow.getInt(1);
            int returned = crow.getInt(2);
            int netIssued = issued - returned;
            int allowed = required - netIssued;
            long reqL = 0L + required;
            long tolBpL = 0L + tolBp;
            long tolQty = (reqL * tolBpL) / 10000L;
            int maxAllowed = allowed + ErpNum.parseInt(CryptoParse.longToString(tolQty));
            if (line.quantity > maxAllowed) {
                throw new TransactionException("over-issue");
            }
            long moved = ErpInventory.applyDeltaWithCostConn(conn, companyId, req.warehouseId, line.componentItemId,
                    0 - line.quantity, 0L, InvTxnType.PRODUCTION_ISSUE, "PI", issueId, now);
            long unitCost = 0L;
            if (line.quantity > 0) {
                long qL = 0L + line.quantity;
                unitCost = moved / qL;
            }
            totalAmount = totalAmount + moved;
            ErpDb.execConn(conn,
                    "INSERT INTO production_issue_lines(company_id, issue_id, component_item_id, quantity, unit_cost_amount, amount) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(issueId) + ","
                            + CryptoParse.longToString(line.componentItemId) + ","
                            + CryptoParse.longToString(line.quantity) + ","
                            + CryptoParse.longToString(unitCost) + ","
                            + CryptoParse.longToString(moved) + ")",
                    new ArrayList<Object>());
            ErpDb.execConn(conn,
                    "UPDATE production_order_components SET issued_quantity="
                            + CryptoParse.longToString(issued + line.quantity)
                            + " WHERE production_order_id=" + CryptoParse.longToString(req.productionOrderId)
                            + " AND company_id=" + CryptoParse.longToString(companyId)
                            + " AND component_item_id=" + CryptoParse.longToString(line.componentItemId),
                    new ArrayList<Object>());
            li = li + 1;
        }
        ErpDb.execConn(conn,
                "UPDATE production_wip SET material_cost = material_cost + "
                        + CryptoParse.longToString(totalAmount)
                        + " WHERE production_order_id=" + CryptoParse.longToString(req.productionOrderId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpMfgProduction.refreshWipRemainingConn(conn, req.productionOrderId, companyId);
        long invAcc = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.INVENTORY);
        long wipAcc = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.WIP);
        PostingRequest je = new PostingRequest();
        je.sourceType = JournalSource.PRODUCTION_ISSUE;
        je.sourceId = issueId;
        je.postingDate = postingDate;
        je.documentDate = postingDate;
        je.description = "Production issue " + docNo;
        je.idempotencyKey = "je-pi-" + CryptoParse.longToString(issueId);
        je.lines = new ArrayList<Object>();
        je.lines.add(ErpFinanceCore.line(wipAcc, totalAmount, 0L, 0L, "WIP material"));
        je.lines.add(ErpFinanceCore.line(invAcc, 0L, totalAmount, 0L, "Raw inventory"));
        PostResultDto posted = ErpFinanceCore.postJournalConn(conn, companyId, userId, je);
        if (posted.ok == 0) {
            throw new TransactionException(posted.message);
        }
        if (status.equals(ProductionOrderStatus.RELEASED)) {
            ErpDb.execConn(conn,
                    "UPDATE production_orders SET status='" + ProductionOrderStatus.IN_PROGRESS
                            + "', actual_start_date=" + CryptoParse.longToString(now)
                            + ", updated_at=" + CryptoParse.longToString(now)
                            + " WHERE id=" + CryptoParse.longToString(req.productionOrderId)
                            + " AND company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
        }
        String docJson = "{\"id\":" + ErpJson.num(issueId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"amount\":" + ErpJson.num(totalAmount) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "issued";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "PI", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class MfgReceiptWork implements DbTransactionWork {
    long companyId;
    long userId;
    ProductionReceiptRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (req.quantity <= 0) {
            throw new TransactionException("receipt qty invalid");
        }
        ArrayList<Object> op = new ArrayList<Object>();
        op.add(CryptoParse.longToString(req.productionOrderId));
        op.add(CryptoParse.longToString(companyId));
        String lockSql = "SELECT item_id, planned_quantity, completed_quantity, status, warehouse_id FROM production_orders WHERE id=? AND company_id=?";
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult or = ErpDb.queryConn(conn, lockSql, op);
        if (or.rows.size() == 0) {
            throw new TransactionException("production order not found");
        }
        Row orow = or.rows.get(0);
        long itemId = orow.getLong(0);
        int planned = orow.getInt(1);
        int completed = orow.getInt(2);
        String status = orow.getString(3);
        if (status.equals(ProductionOrderStatus.CANCELLED) || status.equals(ProductionOrderStatus.CLOSED)) {
            throw new TransactionException("order not receivable");
        }
        if (completed + req.quantity > planned) {
            throw new TransactionException("over-receipt");
        }
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(req.productionOrderId));
        wp.add(CryptoParse.longToString(companyId));
        DbQueryResult wr = ErpDb.queryConn(conn,
                "SELECT material_cost, labor_cost, overhead_cost, transferred_cost, remaining_cost FROM production_wip WHERE production_order_id=? AND company_id=?",
                wp);
        if (wr.rows.size() == 0) {
            throw new TransactionException("WIP missing");
        }
        Row wrow = wr.rows.get(0);
        long remaining = wrow.getLong(4);
        int openQty = planned - completed;
        long transfer = 0L;
        if (openQty > 0) {
            long remL = remaining;
            long qtyL = 0L + req.quantity;
            long openL = 0L + openQty;
            transfer = (remL * qtyL) / openL;
        }
        if (completed + req.quantity == planned) {
            transfer = remaining;
        }
        long unitCost = 0L;
        if (req.quantity > 0) {
            long qtyL2 = 0L + req.quantity;
            unitCost = transfer / qtyL2;
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "PR");
        String docNo = "PR-" + CryptoParse.longToString(seq);
        long postingDate = req.postingDate;
        if (postingDate == 0L) {
            postingDate = now;
        }
        long receiptId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO production_receipts(company_id, doc_no, production_order_id, warehouse_id, quantity, unit_cost_amount, total_cost, posting_date, status, idempotency_key, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'"
                        + docNo + "',"
                        + CryptoParse.longToString(req.productionOrderId) + ","
                        + CryptoParse.longToString(req.warehouseId) + ","
                        + CryptoParse.longToString(req.quantity) + ","
                        + CryptoParse.longToString(unitCost) + ","
                        + CryptoParse.longToString(transfer) + ","
                        + CryptoParse.longToString(postingDate) + ",'"
                        + MfgDocStatus.POSTED + "','"
                        + req.idempotencyKey + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ErpInventory.setItemUnitCostConn(conn, companyId, itemId, unitCost);
        ErpInventory.applyDeltaWithCostConn(conn, companyId, req.warehouseId, itemId, req.quantity, unitCost,
                InvTxnType.PRODUCTION_RECEIPT, "PR", receiptId, now);
        ErpDb.execConn(conn,
                "UPDATE production_wip SET transferred_cost = transferred_cost + "
                        + CryptoParse.longToString(transfer)
                        + " WHERE production_order_id=" + CryptoParse.longToString(req.productionOrderId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpMfgProduction.refreshWipRemainingConn(conn, req.productionOrderId, companyId);
        ErpDb.execConn(conn,
                "UPDATE production_orders SET completed_quantity="
                        + CryptoParse.longToString(completed + req.quantity)
                        + ", updated_at=" + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(req.productionOrderId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpMfgProduction.refreshOrderStatusConn(conn, companyId, req.productionOrderId, now);
        long wipAcc = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.WIP);
        long fgAcc = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.FINISHED_GOODS);
        PostingRequest je = new PostingRequest();
        je.sourceType = JournalSource.PRODUCTION_RECEIPT;
        je.sourceId = receiptId;
        je.postingDate = postingDate;
        je.documentDate = postingDate;
        je.description = "Production receipt " + docNo;
        je.idempotencyKey = "je-pr-" + CryptoParse.longToString(receiptId);
        je.lines = new ArrayList<Object>();
        je.lines.add(ErpFinanceCore.line(fgAcc, transfer, 0L, 0L, "Finished goods"));
        je.lines.add(ErpFinanceCore.line(wipAcc, 0L, transfer, 0L, "WIP relief"));
        PostResultDto posted = ErpFinanceCore.postJournalConn(conn, companyId, userId, je);
        if (posted.ok == 0) {
            throw new TransactionException(posted.message);
        }
        String docJson = "{\"id\":" + ErpJson.num(receiptId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"qty\":" + ErpJson.num(req.quantity) + ",\"cost\":" + ErpJson.num(transfer) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "received";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "PR", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
