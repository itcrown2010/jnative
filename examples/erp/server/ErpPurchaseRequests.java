/**
 * Minimal purchase request documents (list / get / create / approve).
 * @author 张晓达
 */
class PurchaseRequestDto {
    long id;
    String docNo;
    String requesterName;
    long supplierId;
    String supplierName;
    String status;
    String notes;
    long createdAt;
    long totalCents;
    ArrayList<Object> lines;
}

class PrLineInput {
    long itemId;
    int quantity;
    long unitPriceCents;
}

class ErpPurchaseRequests {
    static ArrayList<Object> list(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, doc_no, requester_name, supplier_id, status, notes, created_at FROM purchase_requests WHERE company_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            out.add(mapHeader(companyId, r.rows.get(i)));
            i = i + 1;
        }
        return out;
    }

    static PurchaseRequestDto get(long companyId, long id) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(id));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, doc_no, requester_name, supplier_id, status, notes, created_at FROM purchase_requests WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            return null;
        }
        PurchaseRequestDto d = mapHeader(companyId, r.rows.get(0));
        d.lines = loadLines(companyId, id);
        return d;
    }

    static PurchaseRequestDto mapHeader(long companyId, Row row) {
        PurchaseRequestDto d = new PurchaseRequestDto();
        d.id = row.getLong(0);
        d.docNo = row.getString(1);
        d.requesterName = row.getString(2);
        d.supplierId = row.getLong(3);
        d.status = row.getString(4);
        d.notes = row.getString(5);
        d.createdAt = row.getLong(6);
        d.supplierName = ErpPurchasing.partnerName(companyId, d.supplierId);
        d.totalCents = sumLines(companyId, d.id);
        d.lines = new ArrayList<Object>();
        return d;
    }

    static long sumLines(long companyId, long prId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(prId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT COALESCE(SUM(quantity * unit_price_cents),0) FROM purchase_request_lines WHERE purchase_request_id=? AND company_id=?",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static ArrayList<Object> loadLines(long companyId, long prId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(prId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, item_id, quantity, unit_price_cents FROM purchase_request_lines WHERE purchase_request_id=? AND company_id=? ORDER BY line_no",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            OrderLineDto line = new OrderLineDto();
            line.id = row.getLong(0);
            line.itemId = row.getLong(1);
            line.quantity = row.getInt(2);
            line.unitPriceCents = row.getLong(3);
            line.fulfilledQty = 0;
            ItemDto it = ErpItems.get(companyId, line.itemId);
            if (it != null) {
                line.itemSku = it.sku;
            }
            out.add(line);
            i = i + 1;
        }
        return out;
    }

    static PurchaseRequestDto createDraft(long companyId, String requesterName, long supplierId, String notes,
            String seedKey, ArrayList<Object> lines) {
        PrCreateWork work = new PrCreateWork();
        work.companyId = companyId;
        work.requesterName = requesterName;
        work.supplierId = supplierId;
        work.notes = notes;
        work.seedKey = seedKey;
        work.status = DocStatus.DRAFT;
        work.lines = lines;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw new TransactionException("PR create failed");
        }
        long id = ErpNum.asLong(f.getValue());
        ErpDb.reconnect();
        return get(companyId, id);
    }

    static PurchaseRequestDto createWithStatus(long companyId, String requesterName, long supplierId, String notes,
            String seedKey, String status, ArrayList<Object> lines) {
        if (seedKey != null && seedKey.length() > 0) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            p.add(seedKey);
            Object existing = ErpDb.queryOne(
                    "SELECT id FROM purchase_requests WHERE company_id=? AND seed_key=?", p, new LongMapper());
            if (existing != null) {
                return get(companyId, ErpNum.asLong(existing));
            }
        }
        PrCreateWork work = new PrCreateWork();
        work.companyId = companyId;
        work.requesterName = requesterName;
        work.supplierId = supplierId;
        work.notes = notes;
        work.seedKey = seedKey;
        work.status = status;
        work.lines = lines;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            throw new TransactionException("PR create failed");
        }
        long id = ErpNum.asLong(f.getValue());
        ErpDb.reconnect();
        return get(companyId, id);
    }

    static PurchaseRequestDto approve(long companyId, long prId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(prId));
        p.add(CryptoParse.longToString(companyId));
        Object st = ErpDb.queryOne("SELECT status FROM purchase_requests WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (st == null) {
            throw new TransactionException("PR not found");
        }
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "UPDATE purchase_requests SET status='" + DocStatus.APPROVED + "', updated_at="
                        + CryptoParse.longToString(now) + " WHERE id=" + CryptoParse.longToString(prId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        return get(companyId, prId);
    }

    static String toJson(PurchaseRequestDto d) {
        String lines = "[";
        int i = 0;
        while (d.lines != null && i < d.lines.size()) {
            if (i > 0) {
                lines = lines + ",";
            }
            OrderLineDto ln = (OrderLineDto) d.lines.get(i);
            lines = lines + "{\"id\":" + ErpJson.num(ln.id)
                    + ",\"itemId\":" + ErpJson.num(ln.itemId)
                    + ",\"itemSku\":" + ErpJson.str(ln.itemSku)
                    + ",\"quantity\":" + String.valueOf(ln.quantity)
                    + ",\"unitPriceCents\":" + ErpJson.num(ln.unitPriceCents) + "}";
            i = i + 1;
        }
        lines = lines + "]";
        return "{\"id\":" + ErpJson.num(d.id)
                + ",\"docNo\":" + ErpJson.str(d.docNo)
                + ",\"requester\":" + ErpJson.str(d.requesterName)
                + ",\"requesterName\":" + ErpJson.str(d.requesterName)
                + ",\"supplierId\":" + ErpJson.num(d.supplierId)
                + ",\"supplierName\":" + ErpJson.str(d.supplierName)
                + ",\"status\":" + ErpJson.str(d.status)
                + ",\"notes\":" + ErpJson.str(d.notes)
                + ",\"date\":" + ErpJson.num(d.createdAt)
                + ",\"createdAt\":" + ErpJson.num(d.createdAt)
                + ",\"totalCents\":" + ErpJson.num(d.totalCents)
                + ",\"total\":" + ErpJson.num(d.totalCents)
                + ",\"lines\":" + lines + "}";
    }

    static String listJson(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + toJson((PurchaseRequestDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }
}

class PrCreateWork implements DbTransactionWork {
    long companyId;
    String requesterName;
    long supplierId;
    String notes;
    String seedKey;
    String status;
    ArrayList<Object> lines;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "PR");
        String docNo = "PR-" + CryptoParse.longToString(seq);
        if (seedKey != null && seedKey.length() > 0) {
            docNo = "PR-" + seedKey;
        }
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(requesterName);
        hdr.add(CryptoParse.longToString(supplierId));
        hdr.add(status);
        hdr.add(notes);
        hdr.add(seedKey);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        long prId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO purchase_requests(company_id, doc_no, requester_name, supplier_id, status, notes, seed_key, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                hdr);
        int lineNo = 1;
        int i = 0;
        while (i < lines.size()) {
            PrLineInput in = (PrLineInput) lines.get(i);
            ArrayList<Object> ln = new ArrayList<Object>();
            ln.add(CryptoParse.longToString(companyId));
            ln.add(CryptoParse.longToString(prId));
            ln.add(String.valueOf(lineNo));
            ln.add(CryptoParse.longToString(in.itemId));
            ln.add(String.valueOf(in.quantity));
            ln.add(CryptoParse.longToString(in.unitPriceCents));
            ErpDb.execConn(conn,
                    "INSERT INTO purchase_request_lines(company_id, purchase_request_id, line_no, item_id, quantity, unit_price_cents) VALUES (?,?,?,?,?,?)",
                    ln);
            lineNo = lineNo + 1;
            i = i + 1;
        }
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(prId));
    }
}
