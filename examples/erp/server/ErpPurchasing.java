/**
 * @author 张晓达
 */
class ErpPurchasing {
    static PurchaseOrderDto createDraft(long companyId, long supplierId, ArrayList<Object> lines) {
        PoCreateWork work = new PoCreateWork();
        work.companyId = companyId;
        work.supplierId = supplierId;
        work.lines = lines;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            String msg = "PO create failed";
            if (f.getError().getMessage() != null) {
                msg = msg + ": " + f.getError().getMessage();
            }
            throw new TransactionException(msg);
        }
        long id = ErpNum.asLong(f.getValue());
        ErpDb.reconnect();
        return loadPo(companyId, id);
    }

    static PurchaseOrderDto approve(long companyId, long poId) {
        PoApproveWork work = new PoApproveWork();
        work.companyId = companyId;
        work.poId = poId;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw new TransactionException("PO approve failed");
        }
        long id = ErpNum.asLong(f.getValue());
        ErpDb.reconnect();
        return loadPo(companyId, id);
    }

    static PurchaseOrderDto get(long companyId, long poId) {
        return loadPo(companyId, poId);
    }

    static PurchaseOrderDto loadPo(long companyId, long poId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hdr = ErpDb.query(
                "SELECT id, doc_no, supplier_id, status FROM purchase_orders WHERE id=? AND company_id=?",
                p);
        if (hdr.rows.size() == 0) {
            return null;
        }
        Row h = hdr.rows.get(0);
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.id = h.getLong(0);
        dto.docNo = h.getString(1);
        dto.supplierId = h.getLong(2);
        dto.status = h.getString(3);
        dto.lines = loadPoLines(companyId, poId);
        dto.supplierName = partnerName(companyId, dto.supplierId);
        return dto;
    }

    static ArrayList<Object> loadPoLines(long companyId, long poId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, item_id, quantity, received_qty, unit_price_cents FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=? ORDER BY line_no",
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

    static String partnerName(long companyId, long partnerId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(partnerId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT name FROM business_partners WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }
}

class PoCreateWork implements DbTransactionWork {
    long companyId;
    long supplierId;
    ArrayList<Object> lines;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "PO");
        String docNo = "PO-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(supplierId));
        hdr.add(DocStatus.DRAFT);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        long poId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO purchase_orders(company_id, doc_no, supplier_id, status, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                hdr);
        int lineNo = 1;
        int i = 0;
        while (i < lines.size()) {
            PoLineInput in = (PoLineInput) lines.get(i);
            ArrayList<Object> ln = new ArrayList<Object>();
            ln.add(CryptoParse.longToString(companyId));
            ln.add(CryptoParse.longToString(poId));
            ln.add(String.valueOf(lineNo));
            ln.add(CryptoParse.longToString(in.itemId));
            ln.add(String.valueOf(in.quantity));
            ln.add("0");
            ln.add(CryptoParse.longToString(in.unitPriceCents));
            ErpDb.execConn(conn,
                    "INSERT INTO purchase_order_lines(company_id, purchase_order_id, line_no, item_id, quantity, received_qty, unit_price_cents) VALUES (?,?,?,?,?,?,?)",
                    ln);
            lineNo = lineNo + 1;
            i = i + 1;
        }
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(poId));
    }
}

class PoApproveWork implements DbTransactionWork {
    long companyId;
    long poId;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        Object st = ErpDb.queryOneConn(conn, "SELECT status FROM purchase_orders WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (st == null) {
            throw new TransactionException("PO not found");
        }
        if (((String) st).equals(DocStatus.DRAFT) == false) {
            throw new TransactionException("PO not draft");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(DocStatus.APPROVED);
        up.add(CryptoParse.longToString(now));
        up.add(CryptoParse.longToString(poId));
        up.add(CryptoParse.longToString(companyId));
        ErpDb.execConn(conn,
                "UPDATE purchase_orders SET status=?, updated_at=? WHERE id=? AND company_id=?",
                up);
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(poId));
    }
}

class PoLineInput {
    long itemId;
    int quantity;
    long unitPriceCents;
}
