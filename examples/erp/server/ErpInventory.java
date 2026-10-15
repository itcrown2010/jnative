/**
 * Inventory balances / transactions with perpetual moving-average costing (item × warehouse).
 * Money = integer minor units. Outbound uses pre-issue avg; clear-to-zero clears residual value.
 * @author 张晓达
 */
class ErpInventory {
    static ArrayList<Object> balances(long companyId, long warehouseId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = null;
        if (warehouseId > 0L) {
            p.add(CryptoParse.longToString(warehouseId));
            r = ErpDb.query(
                    "SELECT b.warehouse_id, b.item_id, b.quantity_on_hand, b.value_amount, "
                            + "(SELECT w.code FROM warehouses w WHERE w.id=b.warehouse_id), "
                            + "(SELECT w.name FROM warehouses w WHERE w.id=b.warehouse_id), "
                            + "(SELECT it.sku FROM items it WHERE it.id=b.item_id), "
                            + "(SELECT it.name FROM items it WHERE it.id=b.item_id), "
                            + "(SELECT it.min_qty FROM items it WHERE it.id=b.item_id) "
                            + "FROM inventory_balances b WHERE b.company_id=? AND b.warehouse_id=? ORDER BY b.warehouse_id, b.item_id",
                    p);
        } else {
            r = ErpDb.query(
                    "SELECT b.warehouse_id, b.item_id, b.quantity_on_hand, b.value_amount, "
                            + "(SELECT w.code FROM warehouses w WHERE w.id=b.warehouse_id), "
                            + "(SELECT w.name FROM warehouses w WHERE w.id=b.warehouse_id), "
                            + "(SELECT it.sku FROM items it WHERE it.id=b.item_id), "
                            + "(SELECT it.name FROM items it WHERE it.id=b.item_id), "
                            + "(SELECT it.min_qty FROM items it WHERE it.id=b.item_id) "
                            + "FROM inventory_balances b WHERE b.company_id=? ORDER BY b.warehouse_id, b.item_id",
                    p);
        }
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            InventoryBalanceDto b = new InventoryBalanceDto();
            b.warehouseId = row.getLong(0);
            b.itemId = row.getLong(1);
            b.quantityOnHand = row.getInt(2);
            b.valueAmount = row.getLong(3);
            b.warehouseCode = row.getString(4);
            b.warehouseName = row.getString(5);
            b.itemSku = row.getString(6);
            b.itemName = row.getString(7);
            b.minQty = row.getInt(8);
            b.quantityReserved = reservedQty(companyId, b.warehouseId, b.itemId);
            b.reservedSupported = 1;
            b.quantityAvailable = b.quantityOnHand - b.quantityReserved;
            if (b.quantityAvailable < 0) {
                b.quantityAvailable = 0;
            }
            if (b.quantityOnHand > 0 && b.valueAmount == 0L) {
                b.costState = "PENDING_COST";
            } else if (b.quantityOnHand == 0 && b.valueAmount != 0L) {
                b.costState = "VALUE_WITHOUT_QTY";
            } else if (b.quantityOnHand > 0 && b.valueAmount > 0L) {
                b.costState = "OK";
            } else {
                b.costState = "ZERO";
            }
            out.add(b);
            i = i + 1;
        }
        return out;
    }

    static int reservedQty(long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT COALESCE(SUM(quantity - quantity_consumed - quantity_released),0) FROM inventory_reservations "
                        + "WHERE company_id=? AND warehouse_id=? AND item_id=? AND status='"
                        + ReservationStatus.ACTIVE + "'",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    /** Alerts where min_qty > 0 and on-hand <= min_qty. */
    static String alertsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT b.item_id, b.warehouse_id, b.quantity_on_hand, i.sku, i.name, i.min_qty, w.code "
                        + "FROM inventory_balances b "
                        + "JOIN items i ON i.id=b.item_id AND i.company_id=b.company_id "
                        + "JOIN warehouses w ON w.id=b.warehouse_id "
                        + "WHERE b.company_id=? AND i.min_qty > 0 AND b.quantity_on_hand <= i.min_qty "
                        + "ORDER BY i.sku",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            int onHand = row.getInt(2);
            int minQ = row.getInt(5);
            String sev = "WARN";
            if (onHand <= 0) {
                sev = "CRIT";
            }
            out = out + "{\"id\":" + ErpJson.str(CryptoParse.longToString(row.getLong(1)) + "-"
                    + CryptoParse.longToString(row.getLong(0)))
                    + ",\"itemId\":" + ErpJson.num(row.getLong(0))
                    + ",\"warehouseId\":" + ErpJson.num(row.getLong(1))
                    + ",\"qty\":" + String.valueOf(onHand)
                    + ",\"sku\":" + ErpJson.str(row.getString(3))
                    + ",\"name\":" + ErpJson.str(row.getString(4))
                    + ",\"minQty\":" + String.valueOf(minQ)
                    + ",\"warehouse\":" + ErpJson.str(row.getString(6))
                    + ",\"message\":" + ErpJson.str("On-hand " + String.valueOf(onHand) + " <= min " + String.valueOf(minQ))
                    + ",\"severity\":" + ErpJson.str(sev) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static PostResultDto adjust(long companyId, long userId, long warehouseId, long itemId, int qtyDelta,
            String reason, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        if (qtyDelta == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "INVALID_QTY";
            bad.message = "quantity delta required";
            return bad;
        }
        if (reason == null || reason.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "REASON_REQUIRED";
            bad.message = "reason required";
            return bad;
        }
        InvAdjustWork work = new InvAdjustWork();
        work.companyId = companyId;
        work.userId = userId;
        work.warehouseId = warehouseId;
        work.itemId = itemId;
        work.qtyDelta = qtyDelta;
        work.reason = reason;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }

    static PostResultDto postCount(long companyId, long userId, long warehouseId, ArrayList<Object> lines,
            String notes, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        InvCountWork work = new InvCountWork();
        work.companyId = companyId;
        work.userId = userId;
        work.warehouseId = warehouseId;
        work.lines = lines;
        work.notes = notes == null ? "" : notes;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }

    static int qtyOnHandConn(DbConnection conn, long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    static long valueOnHandConn(DbConnection conn, long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT value_amount FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    /** Lock balance row (PG FOR UPDATE) and return qty/value/version. Missing row -> zeros. */
    static long[] lockBalanceConn(DbConnection conn, long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        String sql = "SELECT quantity_on_hand, value_amount, row_version FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?";
        if (Erp.dialect.equals("postgresql")) {
            sql = sql + " FOR UPDATE";
        }
        DbQueryResult r = ErpDb.queryConn(conn, sql, p);
        long[] out = new long[3];
        out[0] = 0L;
        out[1] = 0L;
        out[2] = 0L;
        if (r.rows.size() == 0) {
            return out;
        }
        Row row = r.rows.get(0);
        out[0] = 0L + row.getInt(0);
        out[1] = row.getLong(1);
        out[2] = row.getLong(2);
        return out;
    }

    static long itemUnitCostConn(DbConnection conn, long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(itemId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT unit_cost_amount FROM items WHERE id=? AND company_id=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static void setItemUnitCostConn(DbConnection conn, long companyId, long itemId, long unitCost) {
        ErpDb.execConn(conn,
                "UPDATE items SET unit_cost_amount=" + CryptoParse.longToString(unitCost)
                        + " WHERE id=" + CryptoParse.longToString(itemId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
    }

    /**
     * Apply qty delta with moving-average valuation + CAS on row_version.
     * Inbound: value += unitCostIn * qty (confirmed receipt cost, not tax-inclusive by default).
     * Outbound: cost = floor(value/qty) * issueQty; if qty clears to 0, residual value is cleared.
     * Returns absolute cost/value moved (positive).
     */
    static long applyDeltaWithCostConn(DbConnection conn, long companyId, long warehouseId, long itemId, int delta,
            long unitCostIn, String txnType, String refDocType, long refDocId, long now) {
        return applyDeltaCostConn(conn, companyId, warehouseId, itemId, delta, unitCostIn, 0L, 0, txnType, refDocType,
                refDocId, 0L, now, 0L, "");
    }

    /** Inbound with exact value add (sales return restore / rounding remainder). */
    static long applyInboundExactValueConn(DbConnection conn, long companyId, long warehouseId, long itemId, int qty,
            long valueAdd, String txnType, String refDocType, long refDocId, long sourceLineId, long now,
            long userId, String reason) {
        if (qty <= 0) {
            throw new TransactionException("inbound qty must be > 0");
        }
        if (valueAdd < 0L) {
            throw new TransactionException("inbound value must be >= 0");
        }
        return applyDeltaCostConn(conn, companyId, warehouseId, itemId, qty, 0L, valueAdd, 1, txnType, refDocType,
                refDocId, sourceLineId, now, userId, reason);
    }

    static long applyDeltaCostConn(DbConnection conn, long companyId, long warehouseId, long itemId, int delta,
            long unitCostIn, long exactValueDelta, int useExactValue, String txnType, String refDocType, long refDocId,
            long sourceLineId, long now, long userId, String reason) {
        long[] bal = lockBalanceConn(conn, companyId, warehouseId, itemId);
        int onHand = ErpNum.parseInt(CryptoParse.longToString(bal[0]));
        long value = bal[1];
        long version = bal[2];
        int nextQty = onHand + delta;
        if (nextQty < 0) {
            throw new TransactionException("insufficient stock");
        }
        if (onHand == 0 && value != 0L && delta >= 0) {
            /* Zero qty must not retain unexplained value — clear before inbound. */
            value = 0L;
        }
        long valueDelta = 0L;
        long unitCost = unitCostIn;
        long deltaL = 0L + delta;
        long onHandL = 0L + onHand;
        if (delta > 0) {
            if (useExactValue != 0) {
                valueDelta = exactValueDelta;
                unitCost = exactValueDelta / deltaL;
            } else {
                if (unitCostIn < 0L) {
                    throw new TransactionException("unit cost must be >= 0");
                }
                valueDelta = unitCostIn * deltaL;
                unitCost = unitCostIn;
            }
        } else {
            if (delta < 0) {
                long absQty = 0L - deltaL;
                if (onHand <= 0) {
                    throw new TransactionException("insufficient stock");
                }
                if (nextQty == 0) {
                    /* Clear residual rounding — whole remaining value exits with last units. */
                    valueDelta = 0L - value;
                    unitCost = absQty > 0L ? (value / absQty) : 0L;
                } else {
                    unitCost = value / onHandL;
                    valueDelta = 0L - (unitCost * absQty);
                }
            }
        }
        long nextValue = value + valueDelta;
        if (nextQty == 0) {
            nextValue = 0L;
            valueDelta = 0L - value;
        }
        if (nextValue < 0L) {
            throw new TransactionException("inventory value would go negative");
        }
        long nextVersion = version + 1L;
        long avgAfter = 0L;
        if (nextQty > 0) {
            avgAfter = nextValue / (0L + nextQty);
        }
        ArrayList<Object> empty = new ArrayList<Object>();
        ErpDb.execConn(conn,
                "INSERT INTO inventory_transactions(company_id, warehouse_id, item_id, txn_type, quantity_delta, unit_cost_amount, value_delta_amount, ref_doc_type, ref_doc_id, created_at, biz_date, posted_by, notes) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(warehouseId) + ","
                        + CryptoParse.longToString(itemId) + ",'"
                        + txnType + "',"
                        + CryptoParse.longToString(delta) + ","
                        + CryptoParse.longToString(unitCost) + ","
                        + CryptoParse.longToString(valueDelta) + ",'"
                        + refDocType + "',"
                        + CryptoParse.longToString(refDocId) + ","
                        + CryptoParse.longToString(now) + ","
                        + CryptoParse.longToString(now) + ","
                        + CryptoParse.longToString(userId) + ",'"
                        + (reason == null ? "" : reason) + "')",
                empty);
        String ik = "ice-" + refDocType + "-" + CryptoParse.longToString(refDocId) + "-"
                + CryptoParse.longToString(itemId) + "-" + CryptoParse.longToString(sourceLineId) + "-"
                + CryptoParse.longToString(now) + "-" + CryptoParse.longToString(delta);
        ArrayList<Object> ce = new ArrayList<Object>();
        ce.add(CryptoParse.longToString(companyId));
        ce.add(CryptoParse.longToString(warehouseId));
        ce.add(CryptoParse.longToString(itemId));
        ce.add(txnType);
        ce.add(String.valueOf(delta));
        ce.add(CryptoParse.longToString(unitCost));
        ce.add(CryptoParse.longToString(valueDelta));
        ce.add(String.valueOf(onHand));
        ce.add(CryptoParse.longToString(value));
        ce.add(String.valueOf(nextQty));
        ce.add(CryptoParse.longToString(nextValue));
        ce.add(CryptoParse.longToString(avgAfter));
        ce.add(refDocType);
        ce.add(CryptoParse.longToString(refDocId));
        ce.add(CryptoParse.longToString(sourceLineId));
        ce.add(CryptoParse.longToString(now));
        ce.add(CryptoParse.longToString(now));
        ce.add(CryptoParse.longToString(userId));
        ce.add(reason == null ? "" : reason);
        ce.add(ik);
        try {
            ErpDb.execConn(conn,
                    "INSERT INTO inventory_cost_events(company_id, warehouse_id, item_id, event_type, quantity_delta, unit_cost_amount, value_delta_amount, qty_before, value_before, qty_after, value_after, avg_cost_after, source_type, source_id, source_line_id, biz_date, posted_at, posted_by, reason, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    ce);
        } catch (Exception ignored) {
            /* Older DBs without table: txn row still written. */
        }
        if (onHand == 0 && delta > 0 && version == 0L) {
            try {
                ErpDb.execConn(conn,
                        "INSERT INTO inventory_balances(company_id, warehouse_id, item_id, quantity_on_hand, value_amount, row_version) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(warehouseId) + ","
                                + CryptoParse.longToString(itemId) + ","
                                + CryptoParse.longToString(delta) + ","
                                + CryptoParse.longToString(nextValue) + ",1)",
                        empty);
            } catch (Exception ex) {
                throw new TransactionException("CAS_CONFLICT:inventory insert race");
            }
        } else {
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(CryptoParse.longToString(nextQty));
            up.add(CryptoParse.longToString(nextValue));
            up.add(CryptoParse.longToString(nextVersion));
            up.add(CryptoParse.longToString(companyId));
            up.add(CryptoParse.longToString(warehouseId));
            up.add(CryptoParse.longToString(itemId));
            up.add(CryptoParse.longToString(version));
            ErpDb.execConn(conn,
                    "UPDATE inventory_balances SET quantity_on_hand=?, value_amount=?, row_version=? WHERE company_id=? AND warehouse_id=? AND item_id=? AND row_version=?",
                    up);
            int checkQty = qtyOnHandConn(conn, companyId, warehouseId, itemId);
            ArrayList<Object> vp = new ArrayList<Object>();
            vp.add(CryptoParse.longToString(companyId));
            vp.add(CryptoParse.longToString(warehouseId));
            vp.add(CryptoParse.longToString(itemId));
            Object vv = ErpDb.queryOneConn(conn,
                    "SELECT row_version FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    vp, new LongMapper());
            long checkVer = ErpNum.asLong(vv);
            if (checkQty != nextQty || checkVer != nextVersion) {
                throw new TransactionException("CAS_CONFLICT:inventory version mismatch");
            }
        }
        if (valueDelta < 0L) {
            return 0L - valueDelta;
        }
        return valueDelta;
    }

    static void applyDeltaConn(DbConnection conn, long companyId, long warehouseId, long itemId, int delta,
            String txnType, String refDocType, long refDocId, long now) {
        long unitCost = itemUnitCostConn(conn, companyId, itemId);
        applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, delta, unitCost, txnType, refDocType, refDocId,
                now);
    }

    static long avgUnitCost(long valueAmount, int qty) {
        if (qty <= 0) {
            return 0L;
        }
        return valueAmount / (0L + qty);
    }
}

class InvAdjustWork implements DbTransactionWork {
    long companyId;
    long userId;
    long warehouseId;
    long itemId;
    int qtyDelta;
    String reason;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "IA");
        String docNo = "IA-" + CryptoParse.longToString(seq);
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(companyId));
        ins.add(docNo);
        ins.add(CryptoParse.longToString(warehouseId));
        ins.add(CryptoParse.longToString(itemId));
        ins.add(String.valueOf(qtyDelta));
        ins.add(reason);
        ins.add("POSTED");
        ins.add(idempotencyKey);
        ins.add(CryptoParse.longToString(userId));
        ins.add(CryptoParse.longToString(now));
        long adjId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO inventory_adjustments(company_id, doc_no, warehouse_id, item_id, quantity_delta, reason, status, idempotency_key, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                ins);
        long unitCost = ErpInventory.itemUnitCostConn(conn, companyId, itemId);
        ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, qtyDelta, unitCost,
                InvTxnType.ADJUSTMENT, "IA", adjId, now);
        String docJson = "{\"id\":" + ErpJson.num(adjId) + ",\"docNo\":" + ErpJson.str(docNo) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "adjusted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "IA", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class InvCountLineInput {
    long itemId;
    int countedQty;
}

class InvCountWork implements DbTransactionWork {
    long companyId;
    long userId;
    long warehouseId;
    ArrayList<Object> lines;
    String notes;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (lines == null || lines.size() == 0) {
            throw new TransactionException("count lines required");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "IC");
        String docNo = "IC-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add("POSTED");
        hdr.add(idempotencyKey);
        hdr.add(notes);
        hdr.add(CryptoParse.longToString(userId));
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        long countId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO inventory_counts(company_id, doc_no, warehouse_id, status, idempotency_key, notes, created_by, created_at, posted_at) VALUES (?,?,?,?,?,?,?,?,?)",
                hdr);
        int i = 0;
        while (i < lines.size()) {
            InvCountLineInput ln = (InvCountLineInput) lines.get(i);
            if (ln.itemId <= 0L) {
                throw new TransactionException("item required");
            }
            if (ln.countedQty < 0) {
                throw new TransactionException("counted qty invalid");
            }
            int systemQty = ErpInventory.qtyOnHandConn(conn, companyId, warehouseId, ln.itemId);
            int variance = ln.countedQty - systemQty;
            ArrayList<Object> cl = new ArrayList<Object>();
            cl.add(CryptoParse.longToString(companyId));
            cl.add(CryptoParse.longToString(countId));
            cl.add(CryptoParse.longToString(ln.itemId));
            cl.add(String.valueOf(systemQty));
            cl.add(String.valueOf(ln.countedQty));
            cl.add(String.valueOf(variance));
            ErpDb.execConn(conn,
                    "INSERT INTO inventory_count_lines(company_id, inventory_count_id, item_id, system_qty, counted_qty, variance_qty) VALUES (?,?,?,?,?,?)",
                    cl);
            if (variance != 0) {
                long unitCost = ErpInventory.itemUnitCostConn(conn, companyId, ln.itemId);
                ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, ln.itemId, variance, unitCost,
                        InvTxnType.ADJUSTMENT, "IC", countId, now);
            }
            i = i + 1;
        }
        String docJson = "{\"id\":" + ErpJson.num(countId) + ",\"docNo\":" + ErpJson.str(docNo) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "count posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "IC", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
