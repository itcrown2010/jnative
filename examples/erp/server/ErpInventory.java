class ErpInventory {
    static ArrayList<Object> balances(long companyId, long warehouseId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        DbQueryResult r = ErpDb.query(
                "SELECT warehouse_id, item_id, quantity_on_hand, value_amount FROM inventory_balances WHERE company_id=? AND warehouse_id=?",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            InventoryBalanceDto b = new InventoryBalanceDto();
            b.warehouseId = row.getLong(0);
            b.itemId = row.getLong(1);
            b.quantityOnHand = row.getInt(2);
            out.add(b);
            i = i + 1;
        }
        return out;
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
     * Apply qty delta with valuation + optimistic CAS on row_version.
     * Concurrent conflicting updates raise CAS_CONFLICT (caller may retry in new txn).
     */
    static long applyDeltaWithCostConn(DbConnection conn, long companyId, long warehouseId, long itemId, int delta,
            long unitCostIn, String txnType, String refDocType, long refDocId, long now) {
        long[] bal = lockBalanceConn(conn, companyId, warehouseId, itemId);
        int onHand = ErpNum.parseInt(CryptoParse.longToString(bal[0]));
        long value = bal[1];
        long version = bal[2];
        int nextQty = onHand + delta;
        if (nextQty < 0) {
            throw new TransactionException("insufficient stock");
        }
        long valueDelta = 0L;
        long unitCost = unitCostIn;
        long deltaL = 0L + delta;
        long onHandL = 0L + onHand;
        if (delta > 0) {
            valueDelta = unitCostIn * deltaL;
        } else {
            if (delta < 0) {
                long absQty = 0L - deltaL;
                if (onHand == 0) {
                    throw new TransactionException("insufficient stock");
                }
                unitCost = value / onHandL;
                valueDelta = 0L - (unitCost * absQty);
            }
        }
        long nextValue = value + valueDelta;
        if (nextValue < 0L) {
            nextValue = 0L;
        }
        long nextVersion = version + 1L;
        ArrayList<Object> empty = new ArrayList<Object>();
        ErpDb.execConn(conn,
                "INSERT INTO inventory_transactions(company_id, warehouse_id, item_id, txn_type, quantity_delta, unit_cost_amount, value_delta_amount, ref_doc_type, ref_doc_id, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(warehouseId) + ","
                        + CryptoParse.longToString(itemId) + ",'"
                        + txnType + "',"
                        + CryptoParse.longToString(delta) + ","
                        + CryptoParse.longToString(unitCost) + ","
                        + CryptoParse.longToString(valueDelta) + ",'"
                        + refDocType + "',"
                        + CryptoParse.longToString(refDocId) + ","
                        + CryptoParse.longToString(now) + ")",
                empty);
        if (onHand == 0 && delta > 0 && version == 0L) {
            /* Insert path: unique PK prevents double-insert races. */
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
            /* CAS: only succeed if row_version unchanged */
            ErpDb.execConn(conn,
                    "UPDATE inventory_balances SET quantity_on_hand=?, value_amount=?, row_version=? WHERE company_id=? AND warehouse_id=? AND item_id=? AND row_version=?",
                    up);
            int checkQty = qtyOnHandConn(conn, companyId, warehouseId, itemId);
            long checkVer = 0L;
            ArrayList<Object> vp = new ArrayList<Object>();
            vp.add(CryptoParse.longToString(companyId));
            vp.add(CryptoParse.longToString(warehouseId));
            vp.add(CryptoParse.longToString(itemId));
            Object vv = ErpDb.queryOneConn(conn,
                    "SELECT row_version FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    vp, new LongMapper());
            checkVer = ErpNum.asLong(vv);
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
}
