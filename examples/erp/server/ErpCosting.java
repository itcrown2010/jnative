/**
 * Moving-average inventory costing queries, margin, and conservation checks.
 * Business gross margin ≠ accounting net profit.
 * @author 张晓达
 */
class ErpCosting {

    static long balanceQty(long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long balanceValue(long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT value_amount FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long avgCost(long companyId, long warehouseId, long itemId) {
        long qty = balanceQty(companyId, warehouseId, itemId);
        long val = balanceValue(companyId, warehouseId, itemId);
        return ErpInventory.avgUnitCost(val, ErpNum.parseInt(CryptoParse.longToString(qty)));
    }

    /** Seed opening qty/value in one txn (for acceptance). */
    static PostResultDto seedOpening(long companyId, long warehouseId, long itemId, int qty, long valueAmount,
            String idempotencyKey) {
        CostSeedWork w = new CostSeedWork();
        w.companyId = companyId;
        w.warehouseId = warehouseId;
        w.itemId = itemId;
        w.qty = qty;
        w.valueAmount = valueAmount;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto receiveCost(long companyId, long warehouseId, long itemId, int qty, long unitCost,
            String idempotencyKey) {
        CostReceiveWork w = new CostReceiveWork();
        w.companyId = companyId;
        w.warehouseId = warehouseId;
        w.itemId = itemId;
        w.qty = qty;
        w.unitCost = unitCost;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto issueCost(long companyId, long warehouseId, long itemId, int qty, String idempotencyKey) {
        CostIssueWork w = new CostIssueWork();
        w.companyId = companyId;
        w.warehouseId = warehouseId;
        w.itemId = itemId;
        w.qty = qty;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    /**
     * Issue that records a synthetic delivery line so returns can restore original COGS.
     * Returns documentJson with deliveryLineId and cogsAmount.
     */
    static PostResultDto issueWithDeliveryLine(long companyId, long warehouseId, long itemId, int qty,
            String idempotencyKey) {
        CostIssueDlWork w = new CostIssueDlWork();
        w.companyId = companyId;
        w.warehouseId = warehouseId;
        w.itemId = itemId;
        w.qty = qty;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto returnToStock(long companyId, long warehouseId, long itemId, long deliveryLineId, int qty,
            String idempotencyKey) {
        CostReturnWork w = new CostReturnWork();
        w.companyId = companyId;
        w.warehouseId = warehouseId;
        w.itemId = itemId;
        w.deliveryLineId = deliveryLineId;
        w.qty = qty;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static String balancesJson(long companyId, long warehouseId) {
        ArrayList<Object> list = ErpInventory.balances(companyId, warehouseId);
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            InventoryBalanceDto b = (InventoryBalanceDto) list.get(i);
            long avg = ErpInventory.avgUnitCost(b.valueAmount, b.quantityOnHand);
            String costState = "OK";
            if (b.quantityOnHand > 0 && b.valueAmount == 0L) {
                costState = "PENDING_COST";
            }
            if (b.quantityOnHand == 0 && b.valueAmount != 0L) {
                costState = "VALUE_WITHOUT_QTY";
            }
            out = out + "{\"warehouseId\":" + ErpJson.num(b.warehouseId)
                    + ",\"itemId\":" + ErpJson.num(b.itemId)
                    + ",\"warehouseCode\":" + ErpJson.str(b.warehouseCode)
                    + ",\"itemSku\":" + ErpJson.str(b.itemSku)
                    + ",\"itemName\":" + ErpJson.str(b.itemName)
                    + ",\"quantityOnHand\":" + String.valueOf(b.quantityOnHand)
                    + ",\"valueAmount\":" + ErpJson.num(b.valueAmount)
                    + ",\"avgUnitCost\":" + ErpJson.num(avg)
                    + ",\"costState\":" + ErpJson.str(costState) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String costEventsJson(long companyId, long warehouseId, long itemId, int limit) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        String sql = "SELECT id, warehouse_id, item_id, event_type, quantity_delta, unit_cost_amount, value_delta_amount, qty_after, value_after, avg_cost_after, source_type, source_id, biz_date, posted_at FROM inventory_cost_events WHERE company_id=?";
        if (warehouseId > 0L) {
            sql = sql + " AND warehouse_id=" + CryptoParse.longToString(warehouseId);
        }
        if (itemId > 0L) {
            sql = sql + " AND item_id=" + CryptoParse.longToString(itemId);
        }
        sql = sql + " ORDER BY id DESC LIMIT " + String.valueOf(limit);
        DbQueryResult r = ErpDb.query(sql, p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"warehouseId\":" + ErpJson.num(row.getLong(1))
                    + ",\"itemId\":" + ErpJson.num(row.getLong(2))
                    + ",\"eventType\":" + ErpJson.str(row.getString(3))
                    + ",\"quantityDelta\":" + String.valueOf(row.getInt(4))
                    + ",\"unitCostAmount\":" + ErpJson.num(row.getLong(5))
                    + ",\"valueDeltaAmount\":" + ErpJson.num(row.getLong(6))
                    + ",\"qtyAfter\":" + String.valueOf(row.getInt(7))
                    + ",\"valueAfter\":" + ErpJson.num(row.getLong(8))
                    + ",\"avgCostAfter\":" + ErpJson.num(row.getLong(9))
                    + ",\"sourceType\":" + ErpJson.str(row.getString(10))
                    + ",\"sourceId\":" + ErpJson.num(row.getLong(11))
                    + ",\"bizDate\":" + ErpJson.num(row.getLong(12))
                    + ",\"postedAt\":" + ErpJson.num(row.getLong(13)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    /**
     * Business gross margin by delivery line: net sales (price*qty - returns price) − COGS net of returns.
     * Missing COGS → costState PENDING_COST (not zero).
     */
    static String marginJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT dl.id, dl.delivery_id, dl.item_id, dl.quantity, dl.returned_qty, dl.cogs_amount, dl.cogs_returned_amount, "
                        + "sol.unit_price_cents, d.doc_no, so.doc_no, "
                        + "(SELECT bp.name FROM business_partners bp WHERE bp.id=so.customer_id), "
                        + "(SELECT it.sku FROM items it WHERE it.id=dl.item_id) "
                        + "FROM delivery_lines dl "
                        + "JOIN deliveries d ON d.id=dl.delivery_id "
                        + "JOIN sales_order_lines sol ON sol.id=dl.sales_order_line_id "
                        + "JOIN sales_orders so ON so.id=d.sales_order_id "
                        + "WHERE dl.company_id=? ORDER BY dl.id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            int qty = row.getInt(3);
            int ret = row.getInt(4);
            long cogs = row.getLong(5);
            long cogsRet = row.getLong(6);
            long price = row.getLong(7);
            int netQty = qty - ret;
            if (netQty < 0) {
                netQty = 0;
            }
            long salesNet = price * (0L + netQty);
            long cogsNet = cogs - cogsRet;
            if (cogsNet < 0L) {
                cogsNet = 0L;
            }
            String costState = "OK";
            long margin = 0L;
            if (cogs == 0L && qty > 0) {
                costState = "PENDING_COST";
                margin = 0L;
            } else {
                margin = salesNet - cogsNet;
            }
            out = out + "{\"deliveryLineId\":" + ErpJson.num(row.getLong(0))
                    + ",\"deliveryId\":" + ErpJson.num(row.getLong(1))
                    + ",\"itemId\":" + ErpJson.num(row.getLong(2))
                    + ",\"deliveryDocNo\":" + ErpJson.str(row.getString(8))
                    + ",\"salesOrderDocNo\":" + ErpJson.str(row.getString(9))
                    + ",\"customerName\":" + ErpJson.str(row.getString(10))
                    + ",\"itemSku\":" + ErpJson.str(row.getString(11))
                    + ",\"shippedQty\":" + String.valueOf(qty)
                    + ",\"returnedQty\":" + String.valueOf(ret)
                    + ",\"salesNetAmount\":" + ErpJson.num(salesNet)
                    + ",\"cogsNetAmount\":" + ErpJson.num(cogsNet)
                    + ",\"grossMarginAmount\":" + ErpJson.num(margin)
                    + ",\"costState\":" + ErpJson.str(costState)
                    + ",\"note\":" + ErpJson.str("业务毛利≠会计净利润；税额未处理") + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static int runConservationChecks(long companyId) {
        int pass = 1;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT warehouse_id, item_id, quantity_on_hand, value_amount FROM inventory_balances WHERE company_id=?",
                p);
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            long wh = row.getLong(0);
            long item = row.getLong(1);
            int qty = row.getInt(2);
            long val = row.getLong(3);
            ArrayList<Object> tp = new ArrayList<Object>();
            tp.add(CryptoParse.longToString(companyId));
            tp.add(CryptoParse.longToString(wh));
            tp.add(CryptoParse.longToString(item));
            Object sq = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(quantity_delta),0) FROM inventory_transactions WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    tp, new LongMapper());
            Object sv = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(value_delta_amount),0) FROM inventory_transactions WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    tp, new LongMapper());
            long sumQty = ErpNum.asLong(sq);
            long sumVal = ErpNum.asLong(sv);
            if (sumQty != (0L + qty)) {
                System.out.println("FAIL cost:qty_balance wh=" + CryptoParse.longToString(wh)
                        + " item=" + CryptoParse.longToString(item)
                        + " bal=" + String.valueOf(qty) + " txnSum=" + CryptoParse.longToString(sumQty));
                pass = 0;
            }
            if (sumVal != val) {
                System.out.println("FAIL cost:value_balance wh=" + CryptoParse.longToString(wh)
                        + " item=" + CryptoParse.longToString(item)
                        + " bal=" + CryptoParse.longToString(val) + " txnSum=" + CryptoParse.longToString(sumVal));
                pass = 0;
            }
            if (qty == 0 && val != 0L) {
                System.out.println("FAIL cost:zero_qty_nonzero_value item=" + CryptoParse.longToString(item)
                        + " val=" + CryptoParse.longToString(val));
                pass = 0;
            }
            i = i + 1;
        }
        if (pass != 0) {
            System.out.println("PASS integrity:costing");
        }
        return pass;
    }
}

class CostSeedWork implements DbTransactionWork {
    long companyId;
    long warehouseId;
    long itemId;
    int qty;
    long valueAmount;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long unit = 0L;
        if (qty > 0) {
            unit = valueAmount / (0L + qty);
        }
        long moved = ErpInventory.applyInboundExactValueConn(conn, companyId, warehouseId, itemId, qty, valueAmount,
                InvTxnType.ADJUSTMENT, "COST_OPEN", 0L, 0L, now, 0L, "opening seed");
        String doc = "{\"qty\":" + String.valueOf(qty) + ",\"value\":" + ErpJson.num(moved)
                + ",\"avg\":" + ErpJson.num(unit) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "seeded";
        ok.documentJson = doc;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "COST_OPEN", doc, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class CostReceiveWork implements DbTransactionWork {
    long companyId;
    long warehouseId;
    long itemId;
    int qty;
    long unitCost;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long moved = ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, qty, unitCost,
                InvTxnType.GOODS_RECEIPT, "COST_IN", 0L, now);
        String doc = "{\"qty\":" + String.valueOf(qty) + ",\"value\":" + ErpJson.num(moved) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "received";
        ok.documentJson = doc;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "COST_IN", doc, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class CostIssueWork implements DbTransactionWork {
    long companyId;
    long warehouseId;
    long itemId;
    int qty;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long moved = ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, 0 - qty, 0L,
                InvTxnType.DELIVERY, "COST_OUT", 0L, now);
        String doc = "{\"qty\":" + String.valueOf(qty) + ",\"cogs\":" + ErpJson.num(moved) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "issued";
        ok.documentJson = doc;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "COST_OUT", doc, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class CostIssueDlWork implements DbTransactionWork {
    long companyId;
    long warehouseId;
    long itemId;
    int qty;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long moved = ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, 0 - qty, 0L,
                InvTxnType.DELIVERY, "COST_DL", 0L, now);
        long unit = 0L;
        if (qty > 0) {
            unit = moved / (0L + qty);
        }
        /* Minimal delivery_lines row for return cost restore (no SO required). */
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(companyId));
        ins.add("0");
        ins.add("0");
        ins.add(CryptoParse.longToString(itemId));
        ins.add(String.valueOf(qty));
        ins.add(CryptoParse.longToString(unit));
        ins.add(CryptoParse.longToString(moved));
        long lineId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO delivery_lines(company_id, delivery_id, sales_order_line_id, item_id, quantity, unit_cost_amount, cogs_amount, cogs_returned_amount, returned_qty) VALUES (?,?,?,?,?,?,?,0,0)",
                ins);
        String doc = "{\"deliveryLineId\":" + ErpJson.num(lineId) + ",\"cogsAmount\":" + ErpJson.num(moved)
                + ",\"unitCost\":" + ErpJson.num(unit) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "issued with DL line";
        ok.documentJson = doc;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "COST_DL", doc, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class CostReturnWork implements DbTransactionWork {
    long companyId;
    long warehouseId;
    long itemId;
    long deliveryLineId;
    int qty;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(deliveryLineId));
        lp.add(CryptoParse.longToString(companyId));
        DbQueryResult lr = ErpDb.queryConn(conn,
                "SELECT quantity, returned_qty, cogs_amount, cogs_returned_amount, item_id FROM delivery_lines WHERE id=? AND company_id=?",
                lp);
        if (lr.rows.size() == 0) {
            throw new TransactionException("delivery line not found");
        }
        int shipped = lr.rows.get(0).getInt(0);
        int already = lr.rows.get(0).getInt(1);
        long cogsAmt = lr.rows.get(0).getLong(2);
        long cogsRet = lr.rows.get(0).getLong(3);
        long lineItem = lr.rows.get(0).getLong(4);
        if (lineItem != itemId) {
            throw new TransactionException("item mismatch");
        }
        if (already + qty > shipped) {
            throw new TransactionException("over-return");
        }
        int remainQty = shipped - already;
        long remainCogs = cogsAmt - cogsRet;
        long restore = 0L;
        if (qty >= remainQty) {
            restore = remainCogs;
        } else {
            restore = (remainCogs * (0L + qty)) / (0L + remainQty);
        }
        long newRet = 0L + already + qty;
        long newCogsRet = cogsRet + restore;
        ErpDb.execConn(conn,
                "UPDATE delivery_lines SET returned_qty=" + CryptoParse.longToString(newRet)
                        + ", cogs_returned_amount=" + CryptoParse.longToString(newCogsRet)
                        + " WHERE id=" + CryptoParse.longToString(deliveryLineId)
                        + " AND returned_qty=" + CryptoParse.longToString(already),
                new ArrayList<Object>());
        ErpInventory.applyInboundExactValueConn(conn, companyId, warehouseId, itemId, qty, restore,
                InvTxnType.SALES_RETURN, "COST_SR", deliveryLineId, deliveryLineId, now, 0L,
                "restore original COGS");
        String doc = "{\"restoreValue\":" + ErpJson.num(restore) + ",\"deliveryLineId\":"
                + ErpJson.num(deliveryLineId) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "returned";
        ok.documentJson = doc;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "COST_SR", doc, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
