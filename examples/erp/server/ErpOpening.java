/**
 * Independent opening documents: stock / AR / AP.
 * Draft → validate → post (idempotent). Never mutate balances outside posting.
 * @author 张晓达
 */
class ErpOpening {
    static PostResultDto createStockDraft(long companyId, long userId, long warehouseId, long businessDate,
            String notes, String idempotencyKey, ArrayList<Object> lines) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        OpeningStockDraftWork work = new OpeningStockDraftWork();
        work.companyId = companyId;
        work.userId = userId;
        work.warehouseId = warehouseId;
        work.businessDate = businessDate;
        work.notes = notes;
        work.idempotencyKey = idempotencyKey;
        work.lines = lines;
        return ErpPosting.runPostingWork(work);
    }

    static PostResultDto postStock(long companyId, long userId, long docId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        OpeningStockPostWork work = new OpeningStockPostWork();
        work.companyId = companyId;
        work.userId = userId;
        work.docId = docId;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }

    static PostResultDto createAndPostAr(long companyId, long userId, long customerId, long businessDate,
            long amount, String notes, String idempotencyKey) {
        OpeningArWork work = new OpeningArWork();
        work.companyId = companyId;
        work.userId = userId;
        work.customerId = customerId;
        work.businessDate = businessDate;
        work.amount = amount;
        work.notes = notes;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }

    static PostResultDto createAndPostAp(long companyId, long userId, long supplierId, long businessDate,
            long amount, String notes, String idempotencyKey) {
        OpeningApWork work = new OpeningApWork();
        work.companyId = companyId;
        work.userId = userId;
        work.supplierId = supplierId;
        work.businessDate = businessDate;
        work.amount = amount;
        work.notes = notes;
        work.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(work);
    }
}

class OpeningStockDraftWork implements DbTransactionWork {
    long companyId;
    long userId;
    long warehouseId;
    long businessDate;
    String notes;
    String idempotencyKey;
    ArrayList<Object> lines;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (notes == null) {
            notes = "";
        }
        if (lines == null || lines.size() == 0) {
            throw new TransactionException("opening stock lines required");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "OS");
        String docNo = "OS-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add(CryptoParse.longToString(businessDate));
        hdr.add(DocStatus.DRAFT);
        hdr.add(idempotencyKey);
        hdr.add(notes);
        hdr.add(CryptoParse.longToString(userId));
        hdr.add(CryptoParse.longToString(now));
        long docId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO opening_stock_docs(company_id, doc_no, warehouse_id, business_date, status, idempotency_key, notes, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                hdr);
        int i = 0;
        while (i < lines.size()) {
            OpeningLineInput line = (OpeningLineInput) lines.get(i);
            if (line.quantity <= 0) {
                throw new TransactionException("invalid opening qty");
            }
            ErpDb.execConn(conn,
                    "INSERT INTO opening_stock_lines(company_id, doc_id, item_id, quantity, unit_cost_amount) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(docId) + ","
                            + CryptoParse.longToString(line.itemId) + ","
                            + CryptoParse.longToString(line.quantity) + ","
                            + CryptoParse.longToString(line.unitCostAmount) + ")",
                    new ArrayList<Object>());
            i = i + 1;
        }
        String docJson = "{\"id\":" + ErpJson.num(docId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"status\":" + ErpJson.str(DocStatus.DRAFT) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "draft";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "OS_DRAFT", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class OpeningStockPostWork implements DbTransactionWork {
    long companyId;
    long userId;
    long docId;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> hp = new ArrayList<Object>();
        hp.add(CryptoParse.longToString(docId));
        hp.add(CryptoParse.longToString(companyId));
        DbQueryResult hr = ErpDb.queryConn(conn,
                "SELECT doc_no, warehouse_id, status FROM opening_stock_docs WHERE id=? AND company_id=?", hp);
        if (hr.rows.size() == 0) {
            throw new TransactionException("opening stock doc not found");
        }
        Row hrow = hr.rows.get(0);
        String docNo = hrow.getString(0);
        long warehouseId = hrow.getLong(1);
        String status = hrow.getString(2);
        if (status.equals("POSTED")) {
            String replay = "{\"id\":" + ErpJson.num(docId) + ",\"docNo\":" + ErpJson.str(docNo)
                    + ",\"status\":\"POSTED\"}";
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(replay));
        }
        if (status.equals(DocStatus.DRAFT) == false) {
            throw new TransactionException("opening stock not draft");
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(docId));
        lp.add(CryptoParse.longToString(companyId));
        DbQueryResult lines = ErpDb.queryConn(conn,
                "SELECT item_id, quantity, unit_cost_amount FROM opening_stock_lines WHERE doc_id=? AND company_id=?",
                lp);
        if (lines.rows.size() == 0) {
            throw new TransactionException("opening stock has no lines");
        }
        int i = 0;
        while (i < lines.rows.size()) {
            Row lrow = lines.rows.get(i);
            long itemId = lrow.getLong(0);
            int qty = lrow.getInt(1);
            long unitCost = lrow.getLong(2);
            ErpInventory.setItemUnitCostConn(conn, companyId, itemId, unitCost);
            ErpInventory.applyDeltaWithCostConn(conn, companyId, warehouseId, itemId, qty, unitCost,
                    InvTxnType.ADJUSTMENT, "OS", docId, now);
            i = i + 1;
        }
        ErpDb.execConn(conn,
                "UPDATE opening_stock_docs SET status='POSTED', posted_at=" + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(docId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        try {
            ErpDb.execConn(conn,
                    "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(userId)
                            + ",'POST','OPENING_STOCK',"
                            + CryptoParse.longToString(docId) + ",'" + docNo + "',"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        } catch (Exception auditEx) {
            /* Audit must not block opening stock posting. */
        }
        String docJson = "{\"id\":" + ErpJson.num(docId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"status\":\"POSTED\"}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "OS_POST", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class OpeningArWork implements DbTransactionWork {
    long companyId;
    long userId;
    long customerId;
    long businessDate;
    long amount;
    String notes;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            throw new TransactionException("Idempotency-Key required");
        }
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (amount <= 0L) {
            throw new TransactionException("opening AR amount must be positive");
        }
        if (notes == null) {
            notes = "";
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "OA");
        String docNo = "OA-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(customerId));
        hdr.add(CryptoParse.longToString(businessDate));
        hdr.add(CryptoParse.longToString(amount));
        hdr.add("POSTED");
        hdr.add(idempotencyKey);
        hdr.add(notes);
        hdr.add(CryptoParse.longToString(userId));
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        long docId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO opening_ar_docs(company_id, doc_no, customer_id, business_date, amount, status, idempotency_key, notes, created_by, created_at, posted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                hdr);
        /* Record as posted AR invoice open amount for subsequent settlement */
        String invNo = "AR-OPEN-" + docNo;
        ErpDb.execConn(conn,
                "INSERT INTO ar_invoices(company_id, customer_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(customerId) + ",'"
                        + invNo + "',"
                        + CryptoParse.longToString(businessDate) + ","
                        + CryptoParse.longToString(businessDate) + ",'CNY',"
                        + CryptoParse.longToString(amount) + ",0,"
                        + CryptoParse.longToString(amount) + ",'"
                        + InvoiceStatus.POSTED + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        String docJson = "{\"id\":" + ErpJson.num(docId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"status\":\"POSTED\",\"amount\":" + ErpJson.num(amount) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "OA_POST", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class OpeningApWork implements DbTransactionWork {
    long companyId;
    long userId;
    long supplierId;
    long businessDate;
    long amount;
    String notes;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        if (idempotencyKey == null || idempotencyKey.length() == 0) {
            throw new TransactionException("Idempotency-Key required");
        }
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (amount <= 0L) {
            throw new TransactionException("opening AP amount must be positive");
        }
        if (notes == null) {
            notes = "";
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "OAP");
        String docNo = "OAP-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(supplierId));
        hdr.add(CryptoParse.longToString(businessDate));
        hdr.add(CryptoParse.longToString(amount));
        hdr.add("POSTED");
        hdr.add(idempotencyKey);
        hdr.add(notes);
        hdr.add(CryptoParse.longToString(userId));
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        long docId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO opening_ap_docs(company_id, doc_no, supplier_id, business_date, amount, status, idempotency_key, notes, created_by, created_at, posted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                hdr);
        String invNo = "AP-OPEN-" + docNo;
        ErpDb.execConn(conn,
                "INSERT INTO ap_invoices(company_id, supplier_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(supplierId) + ",'"
                        + invNo + "',"
                        + CryptoParse.longToString(businessDate) + ","
                        + CryptoParse.longToString(businessDate) + ",'CNY',"
                        + CryptoParse.longToString(amount) + ",0,"
                        + CryptoParse.longToString(amount) + ",'"
                        + InvoiceStatus.POSTED + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        String docJson = "{\"id\":" + ErpJson.num(docId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"status\":\"POSTED\",\"amount\":" + ErpJson.num(amount) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "OP_POST", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class OpeningLineInput {
    long itemId;
    int quantity;
    long unitCostAmount;
}
