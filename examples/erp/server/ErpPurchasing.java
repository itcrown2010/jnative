/**
 * Purchase order lifecycle: draft / submit / approve / close.
 * Approve does not change inventory. Receipt qty tracked per line.
 * @author 张晓达
 */
class ErpPurchasing {
    static PurchaseOrderDto createDraft(long companyId, long supplierId, ArrayList<Object> lines) {
        return createDraftFull(companyId, supplierId, 0L, 0L, 0L, "", "CNY", lines, "", "");
    }

    static PurchaseOrderDto createDraftFull(long companyId, long supplierId, long warehouseId, long orderDate,
            long expectedDate, String notes, String currency, ArrayList<Object> lines) {
        return createDraftFull(companyId, supplierId, warehouseId, orderDate, expectedDate, notes, currency, lines,
                "", "");
    }

    static PurchaseOrderDto createDraftFull(long companyId, long supplierId, long warehouseId, long orderDate,
            long expectedDate, String notes, String currency, ArrayList<Object> lines, String contactName,
            String idempotencyKey) {
        validateLines(lines);
        if (supplierId <= 0L) {
            throw new TransactionException("supplier required");
        }
        PoCreateWork work = new PoCreateWork();
        work.companyId = companyId;
        work.supplierId = supplierId;
        work.warehouseId = warehouseId;
        work.orderDate = orderDate;
        work.expectedDate = expectedDate;
        work.notes = notes == null ? "" : notes;
        work.currency = (currency == null || currency.length() == 0) ? "CNY" : currency;
        work.contactName = contactName == null ? "" : contactName;
        work.idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        work.lines = lines;
        int attempt = 0;
        while (attempt < 4) {
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            String em = "";
            if (f.getError().getMessage() != null) {
                em = f.getError().getMessage();
            }
            if (attempt < 3 && (em.length() == 0 || em.indexOf("database is locked") >= 0
                    || em.indexOf("SQLITE_BUSY") >= 0 || em.indexOf("statements in progress") >= 0
                    || em.indexOf("prepare returned null") >= 0 || em.indexOf("not an error") >= 0)) {
                ErpDb.reconnect();
                attempt = attempt + 1;
                continue;
            }
            String msg = "PO create failed";
            if (em.length() > 0) {
                msg = msg + ": " + em;
            } else {
                msg = msg + ": (no message)";
            }
            throw new TransactionException(msg);
        }
        Object val = f.getValue();
        if (val instanceof String) {
            String s = (String) val;
            if (s.length() > 0 && s.charAt(0) == 123) {
                ErpDb.reconnect();
                long replayId = 0L;
                try {
                    replayId = Json.parse(s).asObject().getLong("id", 0L);
                } catch (Exception ignored) {
                }
                if (replayId > 0L) {
                    return loadPo(companyId, replayId);
                }
            }
        }
        long id = ErpNum.asLong(val);
        ErpDb.reconnect();
        PurchaseOrderDto created = loadPo(companyId, id);
        if (created == null) {
            throw new TransactionException("PO create failed: reload null id=" + CryptoParse.longToString(id));
        }
        return created;
        }
        throw new TransactionException("PO create failed: busy");
    }

    static PurchaseOrderDto updateDraft(long companyId, long poId, long supplierId, long warehouseId, long orderDate,
            long expectedDate, String notes, String currency, ArrayList<Object> lines) {
        return updateDraft(companyId, poId, supplierId, warehouseId, orderDate, expectedDate, notes, currency, lines,
                "");
    }

    static PurchaseOrderDto updateDraft(long companyId, long poId, long supplierId, long warehouseId, long orderDate,
            long expectedDate, String notes, String currency, ArrayList<Object> lines, String contactName) {
        validateLines(lines);
        PoUpdateWork work = new PoUpdateWork();
        work.companyId = companyId;
        work.poId = poId;
        work.supplierId = supplierId;
        work.warehouseId = warehouseId;
        work.orderDate = orderDate;
        work.expectedDate = expectedDate;
        work.notes = notes == null ? "" : notes;
        work.currency = (currency == null || currency.length() == 0) ? "CNY" : currency;
        work.contactName = contactName == null ? "" : contactName;
        work.lines = lines;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            String msg = "PO update failed";
            if (f.getError().getMessage() != null) {
                msg = msg + ": " + f.getError().getMessage();
            }
            throw new TransactionException(msg);
        }
        ErpDb.reconnect();
        return loadPo(companyId, poId);
    }

    static PurchaseOrderDto submit(long companyId, long poId) {
        return transition(companyId, poId, "submit");
    }

    static PurchaseOrderDto approve(long companyId, long poId) {
        return transition(companyId, poId, "approve");
    }

    static PurchaseOrderDto close(long companyId, long poId) {
        return transition(companyId, poId, "close");
    }

    static PurchaseOrderDto cancel(long companyId, long poId) {
        return transition(companyId, poId, "cancel");
    }

    static PurchaseOrderDto transition(long companyId, long poId, String action) {
        PoStatusWork work = new PoStatusWork();
        work.companyId = companyId;
        work.poId = poId;
        work.action = action;
        int attempt = 0;
        while (attempt < 4) {
            JnFuture f = Erp.db.transaction(work);
            FutureBlocker.blockingWait(f);
            if (f.getError() != null) {
                String em = "";
                if (f.getError().getMessage() != null) {
                    em = f.getError().getMessage();
                }
                if (attempt < 3 && (em.length() == 0 || em.indexOf("database is locked") >= 0
                        || em.indexOf("SQLITE_BUSY") >= 0 || em.indexOf("statements in progress") >= 0
                        || em.indexOf("prepare returned null") >= 0 || em.indexOf("not an error") >= 0)) {
                    ErpDb.reconnect();
                    attempt = attempt + 1;
                    continue;
                }
                String msg = "PO " + action + " failed";
                if (em.length() > 0) {
                    msg = msg + ": " + em;
                }
                throw new TransactionException(msg);
            }
            ErpDb.reconnect();
            PurchaseOrderDto loaded = loadPo(companyId, poId);
            if (loaded == null) {
                throw new TransactionException("PO " + action + " failed: reload null");
            }
            return loaded;
        }
        throw new TransactionException("PO " + action + " failed: busy");
    }

    static void validateLines(ArrayList<Object> lines) {
        if (lines == null || lines.size() == 0) {
            throw new TransactionException("PO lines required");
        }
        int i = 0;
        while (i < lines.size()) {
            PoLineInput in = (PoLineInput) lines.get(i);
            if (in.itemId <= 0L) {
                throw new TransactionException("item required");
            }
            if (in.quantity <= 0) {
                throw new TransactionException("qty must be > 0");
            }
            if (in.unitPriceCents < 0L) {
                throw new TransactionException("unit price invalid");
            }
            i = i + 1;
        }
    }

    static PurchaseOrderDto get(long companyId, long poId) {
        return loadPo(companyId, poId);
    }

    static ArrayList<Object> list(long companyId) {
        return listFiltered(companyId, "", "", 0L, "");
    }

    static ArrayList<Object> listFiltered(long companyId, String status, String docNo, long supplierId, String q) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        String sql = "SELECT id FROM purchase_orders WHERE company_id=?";
        if (status != null && status.length() > 0) {
            sql = sql + " AND status=?";
            p.add(status);
        }
        if (supplierId > 0L) {
            sql = sql + " AND supplier_id=?";
            p.add(CryptoParse.longToString(supplierId));
        }
        if (docNo != null && docNo.length() > 0) {
            sql = sql + " AND doc_no=?";
            p.add(docNo);
        }
        sql = sql + " ORDER BY id DESC";
        DbQueryResult r = ErpDb.query(sql, p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            long id = r.rows.get(i).getLong(0);
            PurchaseOrderDto po = loadPo(companyId, id);
            if (po != null) {
                if (q == null || q.length() == 0
                        || (po.docNo != null && po.docNo.toLowerCase().indexOf(q.toLowerCase()) >= 0)
                        || (po.supplierName != null && po.supplierName.toLowerCase().indexOf(q.toLowerCase()) >= 0)
                        || (po.status != null && po.status.toLowerCase().indexOf(q.toLowerCase()) >= 0)) {
                    out.add(po);
                }
            }
            i = i + 1;
        }
        return out;
    }

    static long poTotalCents(PurchaseOrderDto po) {
        long t = 0L;
        int i = 0;
        while (po.lines != null && i < po.lines.size()) {
            OrderLineDto ln = (OrderLineDto) po.lines.get(i);
            long amt = ln.lineAmountCents;
            if (amt <= 0L) {
                amt = ErpDocMoney.lineAmountCents(ln.quantity, ln.unitPriceCents, ln.discountBps, ln.taxBps);
            }
            t = t + amt;
            i = i + 1;
        }
        return t;
    }

    static String poJson(PurchaseOrderDto po) {
        if (po == null) {
            return "null";
        }
        String linesJson = "[";
        int i = 0;
        while (po.lines != null && i < po.lines.size()) {
            if (i > 0) {
                linesJson = linesJson + ",";
            }
            OrderLineDto ln = (OrderLineDto) po.lines.get(i);
            int openQty = ln.quantity - ln.fulfilledQty;
            if (openQty < 0) {
                openQty = 0;
            }
            long lineAmt = ln.lineAmountCents;
            if (lineAmt <= 0L) {
                lineAmt = ErpDocMoney.lineAmountCents(ln.quantity, ln.unitPriceCents, ln.discountBps, ln.taxBps);
            }
            String name = ln.itemName == null ? "" : ln.itemName;
            String uom = ln.uom == null || ln.uom.length() == 0 ? "EA" : ln.uom;
            linesJson = linesJson + "{\"id\":" + ErpJson.num(ln.id)
                    + ",\"itemId\":" + ErpJson.num(ln.itemId)
                    + ",\"sku\":" + ErpJson.str(ln.itemSku)
                    + ",\"itemSku\":" + ErpJson.str(ln.itemSku)
                    + ",\"name\":" + ErpJson.str(name)
                    + ",\"itemName\":" + ErpJson.str(name)
                    + ",\"uom\":" + ErpJson.str(uom)
                    + ",\"qty\":" + String.valueOf(ln.quantity)
                    + ",\"quantity\":" + String.valueOf(ln.quantity)
                    + ",\"receivedQty\":" + String.valueOf(ln.fulfilledQty)
                    + ",\"fulfilledQty\":" + String.valueOf(ln.fulfilledQty)
                    + ",\"openQty\":" + String.valueOf(openQty)
                    + ",\"unitPriceCents\":" + ErpJson.num(ln.unitPriceCents)
                    + ",\"discountBps\":" + String.valueOf(ln.discountBps)
                    + ",\"taxBps\":" + String.valueOf(ln.taxBps)
                    + ",\"lineAmountCents\":" + ErpJson.num(lineAmt) + "}";
            i = i + 1;
        }
        linesJson = linesJson + "]";
        String contact = po.contactName == null ? "" : po.contactName;
        return "{\"id\":" + ErpJson.num(po.id)
                + ",\"docNo\":" + ErpJson.str(po.docNo)
                + ",\"supplierId\":" + ErpJson.num(po.supplierId)
                + ",\"partnerId\":" + ErpJson.num(po.supplierId)
                + ",\"supplierName\":" + ErpJson.str(po.supplierName)
                + ",\"partnerName\":" + ErpJson.str(po.supplierName)
                + ",\"warehouseId\":" + ErpJson.num(po.warehouseId)
                + ",\"orderDate\":" + ErpJson.num(po.orderDate)
                + ",\"date\":" + ErpJson.num(po.orderDate)
                + ",\"expectedDate\":" + ErpJson.num(po.expectedDate)
                + ",\"notes\":" + ErpJson.str(po.notes)
                + ",\"currency\":" + ErpJson.str(po.currency)
                + ",\"contactName\":" + ErpJson.str(contact)
                + ",\"status\":" + ErpJson.str(po.status)
                + ",\"createdAt\":" + ErpJson.num(po.createdAt)
                + ",\"totalCents\":" + ErpJson.num(poTotalCents(po))
                + ",\"total\":" + ErpJson.num(poTotalCents(po))
                + ",\"lines\":" + linesJson + "}";
    }

    static String poListJson(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + poJson((PurchaseOrderDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static PurchaseOrderDto loadPo(long companyId, long poId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hdr = ErpDb.query(
                "SELECT id, doc_no, supplier_id, status, warehouse_id, order_date, expected_date, notes, currency, created_at, contact_name FROM purchase_orders WHERE id=? AND company_id=?",
                p);
        if (hdr.rows.size() == 0) {
            hdr = ErpDb.query(
                    "SELECT id, doc_no, supplier_id, status, warehouse_id, order_date, expected_date, notes, currency, created_at FROM purchase_orders WHERE id=? AND company_id=?",
                    p);
            if (hdr.rows.size() == 0) {
                return null;
            }
        }
        Row h = hdr.rows.get(0);
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.id = h.getLong(0);
        dto.docNo = h.getString(1);
        dto.supplierId = h.getLong(2);
        dto.status = h.getString(3);
        dto.warehouseId = h.getLong(4);
        dto.orderDate = h.getLong(5);
        dto.expectedDate = h.getLong(6);
        dto.notes = h.getString(7);
        dto.currency = h.getString(8);
        dto.createdAt = h.getLong(9);
        dto.contactName = "";
        if (h.columnCount() > 10) {
            dto.contactName = h.getString(10);
        }
        if (dto.contactName == null) {
            dto.contactName = "";
        }
        if (dto.orderDate <= 0L && dto.createdAt > 0L) {
            dto.orderDate = dto.createdAt;
        }
        if (dto.currency == null || dto.currency.length() == 0) {
            dto.currency = "CNY";
        }
        if (dto.notes == null) {
            dto.notes = "";
        }
        dto.lines = loadPoLines(companyId, poId);
        dto.supplierName = partnerName(companyId, dto.supplierId);
        return dto;
    }

    static ArrayList<Object> loadPoLines(long companyId, long poId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r;
        try {
            r = ErpDb.query(
                    "SELECT id, item_id, quantity, received_qty, unit_price_cents, discount_bps, tax_bps FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=? ORDER BY line_no",
                    p);
        } catch (Exception ex) {
            r = ErpDb.query(
                    "SELECT id, item_id, quantity, received_qty, unit_price_cents FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=? ORDER BY line_no",
                    p);
        }
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
            line.discountBps = 0;
            line.taxBps = 0;
            if (row.columnCount() > 6) {
                line.discountBps = row.getInt(5);
                line.taxBps = row.getInt(6);
            }
            line.uom = "EA";
            line.lineAmountCents = ErpDocMoney.lineAmountCents(line.quantity, line.unitPriceCents, line.discountBps,
                    line.taxBps);
            ItemDto it = ErpItems.get(companyId, line.itemId);
            if (it != null) {
                line.itemSku = it.sku;
                line.itemName = it.name;
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
    long warehouseId;
    long orderDate;
    long expectedDate;
    String notes;
    String currency;
    String contactName;
    String idempotencyKey;
    ArrayList<Object> lines;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
            if (cached != null && cached.length() > 0) {
                return JnFuture.completed(Erp.loop, cached);
            }
        }
        long od = orderDate;
        if (od <= 0L) {
            od = now;
        }
        if (contactName == null) {
            contactName = "";
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "PO");
        String docNo = "PO-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(supplierId));
        hdr.add(DocStatus.DRAFT);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add(CryptoParse.longToString(od));
        hdr.add(CryptoParse.longToString(expectedDate));
        hdr.add(notes);
        hdr.add(currency);
        hdr.add(contactName);
        long poId = 0L;
        try {
            poId = ErpDb.insertReturningIdConn(conn,
                    "INSERT INTO purchase_orders(company_id, doc_no, supplier_id, status, created_at, updated_at, warehouse_id, order_date, expected_date, notes, currency, contact_name) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    hdr);
        } catch (Exception ex) {
            ArrayList<Object> basic = new ArrayList<Object>();
            basic.add(CryptoParse.longToString(companyId));
            basic.add(docNo);
            basic.add(CryptoParse.longToString(supplierId));
            basic.add(DocStatus.DRAFT);
            basic.add(CryptoParse.longToString(now));
            basic.add(CryptoParse.longToString(now));
            basic.add(CryptoParse.longToString(warehouseId));
            basic.add(CryptoParse.longToString(od));
            basic.add(CryptoParse.longToString(expectedDate));
            basic.add(notes);
            basic.add(currency);
            poId = ErpDb.insertReturningIdConn(conn,
                    "INSERT INTO purchase_orders(company_id, doc_no, supplier_id, status, created_at, updated_at, warehouse_id, order_date, expected_date, notes, currency) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    basic);
        }
        insertLines(conn, companyId, poId, lines);
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            String json = "{\"id\":" + CryptoParse.longToString(poId) + ",\"docNo\":" + ErpJson.str(docNo) + "}";
            ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "PO_CREATE", json, now);
        }
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(poId));
    }

    static void insertLines(DbConnection conn, long companyId, long poId, ArrayList<Object> lines) {
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
            ln.add(String.valueOf(in.discountBps));
            ln.add(String.valueOf(in.taxBps));
            try {
                ErpDb.execConn(conn,
                        "INSERT INTO purchase_order_lines(company_id, purchase_order_id, line_no, item_id, quantity, received_qty, unit_price_cents, discount_bps, tax_bps) VALUES (?,?,?,?,?,?,?,?,?)",
                        ln);
            } catch (Exception ex) {
                ArrayList<Object> basic = new ArrayList<Object>();
                basic.add(CryptoParse.longToString(companyId));
                basic.add(CryptoParse.longToString(poId));
                basic.add(String.valueOf(lineNo));
                basic.add(CryptoParse.longToString(in.itemId));
                basic.add(String.valueOf(in.quantity));
                basic.add("0");
                basic.add(CryptoParse.longToString(in.unitPriceCents));
                ErpDb.execConn(conn,
                        "INSERT INTO purchase_order_lines(company_id, purchase_order_id, line_no, item_id, quantity, received_qty, unit_price_cents) VALUES (?,?,?,?,?,?,?)",
                        basic);
            }
            lineNo = lineNo + 1;
            i = i + 1;
        }
    }
}

class PoUpdateWork implements DbTransactionWork {
    long companyId;
    long poId;
    long supplierId;
    long warehouseId;
    long orderDate;
    long expectedDate;
    String notes;
    String currency;
    String contactName;
    ArrayList<Object> lines;

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
            throw new TransactionException("PO not draft — approved docs cannot be edited");
        }
        long now = AsyncClockNative.wallMillis();
        long od = orderDate;
        if (od <= 0L) {
            od = now;
        }
        if (contactName == null) {
            contactName = "";
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(CryptoParse.longToString(supplierId));
        up.add(CryptoParse.longToString(warehouseId));
        up.add(CryptoParse.longToString(od));
        up.add(CryptoParse.longToString(expectedDate));
        up.add(notes);
        up.add(currency);
        up.add(contactName);
        up.add(CryptoParse.longToString(now));
        up.add(CryptoParse.longToString(poId));
        up.add(CryptoParse.longToString(companyId));
        try {
            ErpDb.execConn(conn,
                    "UPDATE purchase_orders SET supplier_id=?, warehouse_id=?, order_date=?, expected_date=?, notes=?, currency=?, contact_name=?, updated_at=? WHERE id=? AND company_id=?",
                    up);
        } catch (Exception ex) {
            ArrayList<Object> up2 = new ArrayList<Object>();
            up2.add(CryptoParse.longToString(supplierId));
            up2.add(CryptoParse.longToString(warehouseId));
            up2.add(CryptoParse.longToString(od));
            up2.add(CryptoParse.longToString(expectedDate));
            up2.add(notes);
            up2.add(currency);
            up2.add(CryptoParse.longToString(now));
            up2.add(CryptoParse.longToString(poId));
            up2.add(CryptoParse.longToString(companyId));
            ErpDb.execConn(conn,
                    "UPDATE purchase_orders SET supplier_id=?, warehouse_id=?, order_date=?, expected_date=?, notes=?, currency=?, updated_at=? WHERE id=? AND company_id=?",
                    up2);
        }
        ErpDb.execConn(conn,
                "DELETE FROM purchase_order_lines WHERE purchase_order_id=" + CryptoParse.longToString(poId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        PoCreateWork.insertLines(conn, companyId, poId, lines);
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(poId));
    }
}

class PoStatusWork implements DbTransactionWork {
    long companyId;
    long poId;
    String action;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(poId));
        p.add(CryptoParse.longToString(companyId));
        Object stObj = ErpDb.queryOneConn(conn, "SELECT status FROM purchase_orders WHERE id=? AND company_id=?", p,
                new StringMapper());
        if (stObj == null) {
            throw new TransactionException("PO not found");
        }
        String st = (String) stObj;
        String next = st;
        if (action.equals("submit")) {
            if (st.equals(DocStatus.DRAFT) == false) {
                throw new TransactionException("PO not draft");
            }
            next = DocStatus.SUBMITTED;
        } else {
            if (action.equals("approve")) {
                if (st.equals(DocStatus.DRAFT) == false && st.equals(DocStatus.SUBMITTED) == false) {
                    throw new TransactionException("PO not approvable");
                }
                next = DocStatus.APPROVED;
            } else {
                if (action.equals("close")) {
                    if (st.equals(DocStatus.APPROVED) == false && st.equals(DocStatus.PARTIALLY_RECEIVED) == false
                            && st.equals(DocStatus.RECEIVED) == false) {
                        throw new TransactionException("PO not closable");
                    }
                    next = DocStatus.CLOSED;
                } else {
                    if (action.equals("cancel")) {
                        if (st.equals(DocStatus.DRAFT) == false && st.equals(DocStatus.SUBMITTED) == false
                                && st.equals(DocStatus.APPROVED) == false) {
                            throw new TransactionException("PO not cancellable");
                        }
                        DbQueryResult rq = ErpDb.queryConn(conn,
                                "SELECT COALESCE(SUM(received_qty),0) FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=?",
                                p);
                        int rcv = 0;
                        if (rq.rows.size() > 0) {
                            rcv = rq.rows.get(0).getInt(0);
                        }
                        if (rcv > 0) {
                            throw new TransactionException("PO has receipts — cancel blocked");
                        }
                        next = DocStatus.CANCELLED;
                    } else {
                        throw new TransactionException("unknown action");
                    }
                }
            }
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(next);
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
    int discountBps;
    int taxBps;
}
