/**
 * @author 张晓达
 */
class ErpSales {
    static SalesOrderDto createDraft(long companyId, long customerId, ArrayList<Object> lines) {
        SoCreateWork work = new SoCreateWork();
        work.companyId = companyId;
        work.customerId = customerId;
        work.lines = lines;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw new TransactionException("SO create failed");
        }
        long id = ErpNum.asLong(f.getValue());
        ErpDb.reconnect();
        return loadSo(companyId, id);
    }

    static SalesOrderDto approve(long companyId, long soId) {
        SoApproveWork work = new SoApproveWork();
        work.companyId = companyId;
        work.soId = soId;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw new TransactionException("SO approve failed");
        }
        long id = ErpNum.asLong(f.getValue());
        ErpDb.reconnect();
        return loadSo(companyId, id);
    }

    static SalesOrderDto get(long companyId, long soId) {
        return loadSo(companyId, soId);
    }

    static SalesOrderDto loadSo(long companyId, long soId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hdr = ErpDb.query(
                "SELECT id, doc_no, customer_id, status FROM sales_orders WHERE id=? AND company_id=?",
                p);
        if (hdr.rows.size() == 0) {
            return null;
        }
        Row h = hdr.rows.get(0);
        SalesOrderDto dto = new SalesOrderDto();
        dto.id = h.getLong(0);
        dto.docNo = h.getString(1);
        dto.customerId = h.getLong(2);
        dto.status = h.getString(3);
        dto.lines = loadSoLines(companyId, soId);
        dto.customerName = ErpPurchasing.partnerName(companyId, dto.customerId);
        return dto;
    }

    static ArrayList<Object> loadSoLines(long companyId, long soId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, item_id, quantity, delivered_qty, unit_price_cents FROM sales_order_lines WHERE sales_order_id=? AND company_id=? ORDER BY line_no",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            OrderLineDto line = new OrderLineDto();
            line.id = row.getLong(0);
            line.itemId = row.getLong(1);
            line.quantity = row.getInt(2);
            line.fulfilledQty = row.getInt(3);
            line.unitPriceCents = row.getLong(4);
            ItemDto it = ErpItems.get(companyId, line.itemId);
            if (it != null) {
                line.itemSku = it.sku;
            }
            out.add(line);
            i = i + 1;
        }
        return out;
    }
}

class SoCreateWork implements DbTransactionWork {
    long companyId;
    long customerId;
    ArrayList<Object> lines;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "SO");
        String docNo = "SO-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(customerId));
        hdr.add(DocStatus.DRAFT);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        long soId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO sales_orders(company_id, doc_no, customer_id, status, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                hdr);
        int lineNo = 1;
        int i = 0;
        while (i < lines.size()) {
            SoLineInput in = (SoLineInput) lines.get(i);
            ArrayList<Object> ln = new ArrayList<Object>();
            ln.add(CryptoParse.longToString(companyId));
            ln.add(CryptoParse.longToString(soId));
            ln.add(String.valueOf(lineNo));
            ln.add(CryptoParse.longToString(in.itemId));
            ln.add(String.valueOf(in.quantity));
            ln.add("0");
            ln.add(CryptoParse.longToString(in.unitPriceCents));
            ErpDb.execConn(conn,
                    "INSERT INTO sales_order_lines(company_id, sales_order_id, line_no, item_id, quantity, delivered_qty, unit_price_cents) VALUES (?,?,?,?,?,?,?)",
                    ln);
            lineNo = lineNo + 1;
            i = i + 1;
        }
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(soId));
    }
}

class SoApproveWork implements DbTransactionWork {
    long companyId;
    long soId;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        Object st = ErpDb.queryOneConn(conn, "SELECT status FROM sales_orders WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (st == null) {
            throw new TransactionException("SO not found");
        }
        if (((String) st).equals(DocStatus.DRAFT) == false) {
            throw new TransactionException("SO not draft");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(DocStatus.APPROVED);
        up.add(CryptoParse.longToString(now));
        up.add(CryptoParse.longToString(soId));
        up.add(CryptoParse.longToString(companyId));
        ErpDb.execConn(conn,
                "UPDATE sales_orders SET status=?, updated_at=? WHERE id=? AND company_id=?",
                up);
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(soId));
    }
}

class SoLineInput {
    long itemId;
    int quantity;
    long unitPriceCents;
}
