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
     * Apply qty delta with valuation. Positive delta increases stock (GR);
     * negative decreases (DL). unitCost is per-unit for inbound; outbound uses
     * moving average from current balance value/qty.
     * Returns absolute value amount moved (positive).
     */
    static long applyDeltaWithCostConn(DbConnection conn, long companyId, long warehouseId, long itemId, int delta,
            long unitCostIn, String txnType, String refDocType, long refDocId, long now) {
        int onHand = qtyOnHandConn(conn, companyId, warehouseId, itemId);
        long value = valueOnHandConn(conn, companyId, warehouseId, itemId);
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
        if (onHand == 0 && delta > 0) {
            ErpDb.execConn(conn,
                    "INSERT INTO inventory_balances(company_id, warehouse_id, item_id, quantity_on_hand, value_amount) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(warehouseId) + ","
                            + CryptoParse.longToString(itemId) + ","
                            + CryptoParse.longToString(delta) + ","
                            + CryptoParse.longToString(nextValue) + ")",
                    empty);
        } else {
            ErpDb.execConn(conn,
                    "UPDATE inventory_balances SET quantity_on_hand=" + CryptoParse.longToString(nextQty)
                            + ", value_amount=" + CryptoParse.longToString(nextValue)
                            + " WHERE company_id=" + CryptoParse.longToString(companyId)
                            + " AND warehouse_id=" + CryptoParse.longToString(warehouseId)
                            + " AND item_id=" + CryptoParse.longToString(itemId),
                    empty);
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
