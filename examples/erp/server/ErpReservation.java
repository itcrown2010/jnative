/**
 * Inventory reservations: do not change on-hand; available = on-hand − active reserved.
 * @author 张晓达
 */
class ErpReservation {
    static int activeReservedConn(DbConnection conn, long companyId, long warehouseId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT COALESCE(SUM(quantity - quantity_consumed - quantity_released),0) FROM inventory_reservations "
                        + "WHERE company_id=? AND warehouse_id=? AND item_id=? AND status='"
                        + ReservationStatus.ACTIVE + "'",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    static int availableConn(DbConnection conn, long companyId, long warehouseId, long itemId) {
        int onHand = ErpInventory.qtyOnHandConn(conn, companyId, warehouseId, itemId);
        int reserved = activeReservedConn(conn, companyId, warehouseId, itemId);
        int avail = onHand - reserved;
        if (avail < 0) {
            return 0;
        }
        return avail;
    }

    static int lineActiveReservedConn(DbConnection conn, long companyId, long soLineId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(soLineId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT COALESCE(SUM(quantity - quantity_consumed - quantity_released),0) FROM inventory_reservations "
                        + "WHERE company_id=? AND sales_order_line_id=? AND status='"
                        + ReservationStatus.ACTIVE + "'",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    /**
     * Best-effort reserve for an SO line at a warehouse.
     * Returns qty actually reserved (may be less than requested — never fabricates success).
     */
    static int reserveLineConn(DbConnection conn, long companyId, long soId, long soLineId, long warehouseId,
            long itemId, int requestQty, long now) {
        if (requestQty <= 0 || warehouseId <= 0L) {
            return 0;
        }
        int avail = availableConn(conn, companyId, warehouseId, itemId);
        int toReserve = requestQty;
        if (toReserve > avail) {
            toReserve = avail;
        }
        if (toReserve <= 0) {
            return 0;
        }
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(companyId));
        ins.add(CryptoParse.longToString(soId));
        ins.add(CryptoParse.longToString(soLineId));
        ins.add(CryptoParse.longToString(warehouseId));
        ins.add(CryptoParse.longToString(itemId));
        ins.add(String.valueOf(toReserve));
        ins.add("0");
        ins.add("0");
        ins.add(ReservationStatus.ACTIVE);
        ins.add(CryptoParse.longToString(now));
        ins.add(CryptoParse.longToString(now));
        ErpDb.execConn(conn,
                "INSERT INTO inventory_reservations(company_id, sales_order_id, sales_order_line_id, warehouse_id, item_id, quantity, quantity_consumed, quantity_released, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                ins);
        return toReserve;
    }

    /** Release all active remaining qty for an SO (cancel / close remaining). */
    static void releaseSoConn(DbConnection conn, long companyId, long soId, long now) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT id, quantity, quantity_consumed, quantity_released FROM inventory_reservations "
                        + "WHERE sales_order_id=? AND company_id=? AND status='" + ReservationStatus.ACTIVE + "'",
                p);
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            long id = row.getLong(0);
            int qty = row.getInt(1);
            int consumed = row.getInt(2);
            int released = row.getInt(3);
            int remaining = qty - consumed - released;
            if (remaining > 0) {
                int newReleased = released + remaining;
                ArrayList<Object> up = new ArrayList<Object>();
                up.add(String.valueOf(newReleased));
                up.add(ReservationStatus.RELEASED);
                up.add(CryptoParse.longToString(now));
                up.add(CryptoParse.longToString(id));
                up.add(CryptoParse.longToString(companyId));
                ErpDb.execConn(conn,
                        "UPDATE inventory_reservations SET quantity_released=?, status=?, updated_at=? WHERE id=? AND company_id=?",
                        up);
            } else {
                ArrayList<Object> up2 = new ArrayList<Object>();
                up2.add(ReservationStatus.CONSUMED);
                up2.add(CryptoParse.longToString(now));
                up2.add(CryptoParse.longToString(id));
                up2.add(CryptoParse.longToString(companyId));
                ErpDb.execConn(conn,
                        "UPDATE inventory_reservations SET status=?, updated_at=? WHERE id=? AND company_id=?",
                        up2);
            }
            i = i + 1;
        }
    }

    /**
     * Consume reservation for SO line at warehouse. Returns qty consumed from reservation.
     * Does not touch other orders' reservations.
     */
    static int consumeLineConn(DbConnection conn, long companyId, long soLineId, long warehouseId, int need,
            long now) {
        if (need <= 0) {
            return 0;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(soLineId));
        p.add(CryptoParse.longToString(warehouseId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT id, quantity, quantity_consumed, quantity_released FROM inventory_reservations "
                        + "WHERE company_id=? AND sales_order_line_id=? AND warehouse_id=? AND status='"
                        + ReservationStatus.ACTIVE + "' ORDER BY id",
                p);
        int left = need;
        int consumedTotal = 0;
        int i = 0;
        while (i < r.rows.size() && left > 0) {
            Row row = r.rows.get(i);
            long id = row.getLong(0);
            int qty = row.getInt(1);
            int consumed = row.getInt(2);
            int released = row.getInt(3);
            int remaining = qty - consumed - released;
            if (remaining <= 0) {
                i = i + 1;
                continue;
            }
            int take = remaining;
            if (take > left) {
                take = left;
            }
            int newConsumed = consumed + take;
            String st = ReservationStatus.ACTIVE;
            if (newConsumed + released >= qty) {
                st = ReservationStatus.CONSUMED;
            }
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(String.valueOf(newConsumed));
            up.add(st);
            up.add(CryptoParse.longToString(now));
            up.add(CryptoParse.longToString(id));
            up.add(CryptoParse.longToString(companyId));
            up.add(String.valueOf(consumed));
            ErpDb.execConn(conn,
                    "UPDATE inventory_reservations SET quantity_consumed=?, status=?, updated_at=? WHERE id=? AND company_id=? AND quantity_consumed=?",
                    up);
            Object chk = ErpDb.queryOneConn(conn,
                    "SELECT quantity_consumed FROM inventory_reservations WHERE id="
                            + CryptoParse.longToString(id),
                    new ArrayList<Object>(), new IntMapper());
            if (chk == null || ErpNum.parseInt((String) chk) != newConsumed) {
                throw new TransactionException("CAS_CONFLICT:reservation consume race");
            }
            left = left - take;
            consumedTotal = consumedTotal + take;
            i = i + 1;
        }
        return consumedTotal;
    }
}
