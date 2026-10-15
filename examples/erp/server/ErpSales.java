/**
 * Sales order lifecycle with separate approval_status and fulfillment_status.
 * Approve reserves available stock (best-effort); does not change on-hand.
 * @author 张晓达
 */
class ErpSales {
    static SalesOrderDto createDraft(long companyId, long customerId, ArrayList<Object> lines) {
        return createDraftFull(companyId, customerId, 0L, 0L, 0L, "", "CNY", lines, "", "");
    }

    static SalesOrderDto createDraftFull(long companyId, long customerId, long warehouseId, long orderDate,
            long deliveryDate, String notes, String currency, ArrayList<Object> lines) {
        return createDraftFull(companyId, customerId, warehouseId, orderDate, deliveryDate, notes, currency, lines,
                "", "");
    }

    static SalesOrderDto createDraftFull(long companyId, long customerId, long warehouseId, long orderDate,
            long deliveryDate, String notes, String currency, ArrayList<Object> lines, String contactName,
            String idempotencyKey) {
        validateLines(lines);
        if (customerId <= 0L) {
            throw new TransactionException("customer required");
        }
        SoCreateWork work = new SoCreateWork();
        work.companyId = companyId;
        work.customerId = customerId;
        work.warehouseId = warehouseId;
        work.orderDate = orderDate;
        work.deliveryDate = deliveryDate;
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
                String msg = "SO create failed";
                if (em.length() > 0) {
                    msg = msg + ": " + em;
                } else {
                    msg = msg + ": (no message)";
                }
                throw new TransactionException(msg);
            }
            Object val = f.getValue();
            /* Idempotent replay returns JSON string starting with '{' (ASCII 123) */
            if (val instanceof String) {
                String s = (String) val;
                if (s.length() > 0 && s.charAt(0) == 123) {
                    ErpDb.reconnect();
                    long replayId = 0L;
                    try {
                        JsonObject jo = Json.parse(s).asObject();
                        replayId = jo.getLong("id", 0L);
                    } catch (Exception ignored) {
                    }
                    if (replayId > 0L) {
                        SalesOrderDto replayed = loadSo(companyId, replayId);
                        if (replayed == null) {
                            throw new TransactionException("SO create failed: replay reload null");
                        }
                        return replayed;
                    }
                }
            }
            long id = ErpNum.asLong(val);
            ErpDb.reconnect();
            SalesOrderDto created = loadSo(companyId, id);
            if (created == null) {
                throw new TransactionException("SO create failed: reload null id=" + CryptoParse.longToString(id));
            }
            return created;
        }
        throw new TransactionException("SO create failed: busy");
    }

    static SalesOrderDto updateDraft(long companyId, long soId, long customerId, long warehouseId, long orderDate,
            long deliveryDate, String notes, String currency, ArrayList<Object> lines) {
        return updateDraft(companyId, soId, customerId, warehouseId, orderDate, deliveryDate, notes, currency, lines,
                "");
    }

    static SalesOrderDto updateDraft(long companyId, long soId, long customerId, long warehouseId, long orderDate,
            long deliveryDate, String notes, String currency, ArrayList<Object> lines, String contactName) {
        validateLines(lines);
        SoUpdateWork work = new SoUpdateWork();
        work.companyId = companyId;
        work.soId = soId;
        work.customerId = customerId;
        work.warehouseId = warehouseId;
        work.orderDate = orderDate;
        work.deliveryDate = deliveryDate;
        work.notes = notes == null ? "" : notes;
        work.currency = (currency == null || currency.length() == 0) ? "CNY" : currency;
        work.contactName = contactName == null ? "" : contactName;
        work.lines = lines;
        JnFuture f = Erp.db.transaction(work);
        FutureBlocker.blockingWait(f);
        if (f.getError() != null) {
            String msg = "SO update failed";
            if (f.getError().getMessage() != null) {
                msg = msg + ": " + f.getError().getMessage();
            }
            throw new TransactionException(msg);
        }
        ErpDb.reconnect();
        return loadSo(companyId, soId);
    }

    static SalesOrderDto submit(long companyId, long soId) {
        return transition(companyId, soId, "submit");
    }

    static SalesOrderDto approve(long companyId, long soId) {
        return transition(companyId, soId, "approve");
    }

    static SalesOrderDto cancel(long companyId, long soId) {
        return transition(companyId, soId, "cancel");
    }

    static SalesOrderDto closeRemaining(long companyId, long soId) {
        return transition(companyId, soId, "close");
    }

    static SalesOrderDto transition(long companyId, long soId, String action) {
        SoStatusWork work = new SoStatusWork();
        work.companyId = companyId;
        work.soId = soId;
        work.action = action;
        int attempt = 0;
        while (attempt < 4) {
            try {
                JnFuture f = Erp.db.transaction(work);
                FutureBlocker.blockingWait(f);
                if (f.getError() != null) {
                    Exception err = f.getError();
                    String em = "";
                    if (err.getMessage() != null) {
                        em = err.getMessage();
                    }
                    if (attempt < 3 && (ErpPosting.isPgWireError(err) != 0 || em.length() == 0)) {
                        System.out.println("SO_RETRY action=" + action + " attempt="
                                + CryptoParse.longToString(attempt));
                        ErpDb.reconnect();
                        attempt = attempt + 1;
                        continue;
                    }
                    String msg = "SO " + action + " failed";
                    if (em.length() > 0) {
                        msg = msg + ": " + em;
                    }
                    throw new TransactionException(msg);
                }
                ErpDb.reconnect();
                return loadSo(companyId, soId);
            } catch (TransactionException te) {
                throw te;
            } catch (Exception ex) {
                String em = "";
                if (ex.getMessage() != null) {
                    em = ex.getMessage();
                }
                if (attempt < 3 && (ErpPosting.isPgWireError(ex) != 0 || em.length() == 0)) {
                    System.out.println("SO_RETRY action=" + action + " attempt="
                            + CryptoParse.longToString(attempt) + " catch");
                    ErpDb.reconnect();
                    attempt = attempt + 1;
                    continue;
                }
                throw new TransactionException("SO " + action + " failed: " + em);
            }
        }
        throw new TransactionException("SO " + action + " failed after retries");
    }

    static void validateLines(ArrayList<Object> lines) {
        if (lines == null || lines.size() == 0) {
            throw new TransactionException("SO lines required");
        }
        int i = 0;
        while (i < lines.size()) {
            SoLineInput in = (SoLineInput) lines.get(i);
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

    static SalesOrderDto get(long companyId, long soId) {
        return loadSo(companyId, soId);
    }

    static ArrayList<Object> list(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id FROM sales_orders WHERE company_id=? ORDER BY id DESC", p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            SalesOrderDto so = loadSo(companyId, r.rows.get(i).getLong(0));
            if (so != null) {
                out.add(so);
            }
            i = i + 1;
        }
        return out;
    }

    static long soTotalCents(SalesOrderDto so) {
        long t = 0L;
        int i = 0;
        while (so.lines != null && i < so.lines.size()) {
            OrderLineDto ln = (OrderLineDto) so.lines.get(i);
            long amt = ln.lineAmountCents;
            if (amt <= 0L) {
                amt = ErpDocMoney.lineAmountCents(ln.quantity, ln.unitPriceCents, ln.discountBps, ln.taxBps);
            }
            t = t + amt;
            i = i + 1;
        }
        return t;
    }

    static String compositeStatus(String approval, String fulfillment) {
        if (approval != null && approval.equals(ApprovalStatus.CANCELLED)) {
            return DocStatus.CANCELLED;
        }
        if (approval != null && approval.equals(ApprovalStatus.DRAFT)) {
            return DocStatus.DRAFT;
        }
        if (approval != null && approval.equals(ApprovalStatus.SUBMITTED)) {
            return DocStatus.SUBMITTED;
        }
        if (fulfillment != null && fulfillment.equals(FulfillmentStatus.PARTIAL)) {
            return DocStatus.PARTIALLY_DELIVERED;
        }
        if (fulfillment != null && fulfillment.equals(FulfillmentStatus.FULFILLED)) {
            return DocStatus.DELIVERED;
        }
        if (fulfillment != null && fulfillment.equals(FulfillmentStatus.CLOSED)) {
            return DocStatus.CLOSED;
        }
        return DocStatus.APPROVED;
    }

    static String soJson(SalesOrderDto so) {
        if (so == null) {
            return "null";
        }
        String linesJson = "[";
        int i = 0;
        while (so.lines != null && i < so.lines.size()) {
            if (i > 0) {
                linesJson = linesJson + ",";
            }
            OrderLineDto ln = (OrderLineDto) so.lines.get(i);
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
                    + ",\"deliveredQty\":" + String.valueOf(ln.fulfilledQty)
                    + ",\"fulfilledQty\":" + String.valueOf(ln.fulfilledQty)
                    + ",\"returnedQty\":" + String.valueOf(ln.returnedQty)
                    + ",\"reservedQty\":" + String.valueOf(ln.reservedQty)
                    + ",\"openQty\":" + String.valueOf(ln.openQty)
                    + ",\"shortfallQty\":" + String.valueOf(ln.shortfallQty)
                    + ",\"unitPriceCents\":" + ErpJson.num(ln.unitPriceCents)
                    + ",\"discountBps\":" + String.valueOf(ln.discountBps)
                    + ",\"taxBps\":" + String.valueOf(ln.taxBps)
                    + ",\"lineAmountCents\":" + ErpJson.num(lineAmt) + "}";
            i = i + 1;
        }
        linesJson = linesJson + "]";
        String contact = so.contactName == null ? "" : so.contactName;
        return "{\"id\":" + ErpJson.num(so.id)
                + ",\"docNo\":" + ErpJson.str(so.docNo)
                + ",\"customerId\":" + ErpJson.num(so.customerId)
                + ",\"partnerId\":" + ErpJson.num(so.customerId)
                + ",\"customerName\":" + ErpJson.str(so.customerName)
                + ",\"partnerName\":" + ErpJson.str(so.customerName)
                + ",\"warehouseId\":" + ErpJson.num(so.warehouseId)
                + ",\"orderDate\":" + ErpJson.num(so.orderDate)
                + ",\"date\":" + ErpJson.num(so.orderDate)
                + ",\"deliveryDate\":" + ErpJson.num(so.deliveryDate)
                + ",\"notes\":" + ErpJson.str(so.notes)
                + ",\"currency\":" + ErpJson.str(so.currency)
                + ",\"contactName\":" + ErpJson.str(contact)
                + ",\"approvalStatus\":" + ErpJson.str(so.approvalStatus)
                + ",\"fulfillmentStatus\":" + ErpJson.str(so.fulfillmentStatus)
                + ",\"status\":" + ErpJson.str(so.status)
                + ",\"createdAt\":" + ErpJson.num(so.createdAt)
                + ",\"totalCents\":" + ErpJson.num(soTotalCents(so))
                + ",\"total\":" + ErpJson.num(soTotalCents(so))
                + ",\"lines\":" + linesJson + "}";
    }

    static String soListJson(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + soJson((SalesOrderDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static SalesOrderDto loadSo(long companyId, long soId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hdr = ErpDb.query(
                "SELECT id, doc_no, customer_id, status, warehouse_id, order_date, delivery_date, notes, currency, approval_status, fulfillment_status, created_at, contact_name FROM sales_orders WHERE id=? AND company_id=?",
                p);
        if (hdr.rows.size() == 0) {
            /* Fallback without contact_name column */
            hdr = ErpDb.query(
                    "SELECT id, doc_no, customer_id, status, warehouse_id, order_date, delivery_date, notes, currency, approval_status, fulfillment_status, created_at FROM sales_orders WHERE id=? AND company_id=?",
                    p);
            if (hdr.rows.size() == 0) {
                return null;
            }
        }
        Row h = hdr.rows.get(0);
        SalesOrderDto dto = new SalesOrderDto();
        dto.id = h.getLong(0);
        dto.docNo = h.getString(1);
        dto.customerId = h.getLong(2);
        dto.status = h.getString(3);
        dto.warehouseId = h.getLong(4);
        dto.orderDate = h.getLong(5);
        dto.deliveryDate = h.getLong(6);
        dto.notes = h.getString(7);
        dto.currency = h.getString(8);
        dto.approvalStatus = h.getString(9);
        dto.fulfillmentStatus = h.getString(10);
        dto.createdAt = h.getLong(11);
        dto.contactName = "";
        if (h.columnCount() > 12) {
            dto.contactName = h.getString(12);
        }
        if (dto.contactName == null) {
            dto.contactName = "";
        }
        if (dto.orderDate <= 0L && dto.createdAt > 0L) {
            dto.orderDate = dto.createdAt;
        }
        if (dto.approvalStatus == null || dto.approvalStatus.length() == 0) {
            dto.approvalStatus = ApprovalStatus.DRAFT;
        }
        if (dto.fulfillmentStatus == null || dto.fulfillmentStatus.length() == 0) {
            dto.fulfillmentStatus = FulfillmentStatus.NONE;
        }
        if (dto.currency == null || dto.currency.length() == 0) {
            dto.currency = "CNY";
        }
        if (dto.notes == null) {
            dto.notes = "";
        }
        dto.status = compositeStatus(dto.approvalStatus, dto.fulfillmentStatus);
        dto.lines = loadSoLines(companyId, soId);
        dto.customerName = ErpPurchasing.partnerName(companyId, dto.customerId);
        return dto;
    }

    static ArrayList<Object> loadSoLines(long companyId, long soId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = null;
        try {
            r = ErpDb.query(
                    "SELECT id, item_id, quantity, delivered_qty, unit_price_cents, returned_qty, discount_bps, tax_bps FROM sales_order_lines WHERE sales_order_id=? AND company_id=? ORDER BY line_no",
                    p);
        } catch (Exception ex) {
            r = ErpDb.query(
                    "SELECT id, item_id, quantity, delivered_qty, unit_price_cents, returned_qty FROM sales_order_lines WHERE sales_order_id=? AND company_id=? ORDER BY line_no",
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
            line.returnedQty = row.getInt(5);
            line.discountBps = 0;
            line.taxBps = 0;
            if (row.columnCount() > 7) {
                line.discountBps = row.getInt(6);
                line.taxBps = row.getInt(7);
            }
            line.openQty = line.quantity - line.fulfilledQty;
            if (line.openQty < 0) {
                line.openQty = 0;
            }
            line.reservedQty = lineReserved(companyId, line.id);
            line.shortfallQty = line.openQty - line.reservedQty;
            if (line.shortfallQty < 0) {
                line.shortfallQty = 0;
            }
            line.lineAmountCents = ErpDocMoney.lineAmountCents(line.quantity, line.unitPriceCents, line.discountBps,
                    line.taxBps);
            line.uom = "EA";
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

    static int lineReserved(long companyId, long soLineId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(soLineId));
        Object v = ErpDb.queryOne(
                "SELECT COALESCE(SUM(quantity - quantity_consumed - quantity_released),0) FROM inventory_reservations "
                        + "WHERE company_id=? AND sales_order_line_id=? AND status='" + ReservationStatus.ACTIVE + "'",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }
}

class SoCreateWork implements DbTransactionWork {
    long companyId;
    long customerId;
    long warehouseId;
    long orderDate;
    long deliveryDate;
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
        if (notes == null) {
            notes = "";
        }
        if (currency == null || currency.length() == 0) {
            currency = "CNY";
        }
        if (contactName == null) {
            contactName = "";
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "SO");
        String docNo = "SO-" + CryptoParse.longToString(seq);
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(customerId));
        hdr.add(DocStatus.DRAFT);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(now));
        hdr.add(CryptoParse.longToString(warehouseId));
        hdr.add(CryptoParse.longToString(od));
        hdr.add(CryptoParse.longToString(deliveryDate));
        hdr.add(notes);
        hdr.add(currency);
        hdr.add(ApprovalStatus.DRAFT);
        hdr.add(FulfillmentStatus.NONE);
        hdr.add(contactName);
        long soId = 0L;
        try {
            soId = ErpDb.insertReturningIdConn(conn,
                    "INSERT INTO sales_orders(company_id, doc_no, customer_id, status, created_at, updated_at, warehouse_id, order_date, delivery_date, notes, currency, approval_status, fulfillment_status, contact_name) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    hdr);
        } catch (Exception ex) {
            ArrayList<Object> basic = new ArrayList<Object>();
            basic.add(CryptoParse.longToString(companyId));
            basic.add(docNo);
            basic.add(CryptoParse.longToString(customerId));
            basic.add(DocStatus.DRAFT);
            basic.add(CryptoParse.longToString(now));
            basic.add(CryptoParse.longToString(now));
            basic.add(CryptoParse.longToString(warehouseId));
            basic.add(CryptoParse.longToString(od));
            basic.add(CryptoParse.longToString(deliveryDate));
            basic.add(notes);
            basic.add(currency);
            basic.add(ApprovalStatus.DRAFT);
            basic.add(FulfillmentStatus.NONE);
            soId = ErpDb.insertReturningIdConn(conn,
                    "INSERT INTO sales_orders(company_id, doc_no, customer_id, status, created_at, updated_at, warehouse_id, order_date, delivery_date, notes, currency, approval_status, fulfillment_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    basic);
            try {
                ArrayList<Object> cup = new ArrayList<Object>();
                cup.add(contactName);
                cup.add(CryptoParse.longToString(soId));
                cup.add(CryptoParse.longToString(companyId));
                ErpDb.execConn(conn, "UPDATE sales_orders SET contact_name=? WHERE id=? AND company_id=?", cup);
            } catch (Exception ignored) {
            }
        }
        if (soId <= 0L) {
            throw new TransactionException("SO insert returned 0");
        }
        insertLines(conn, companyId, soId, lines);
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            /* Minimal cached response; caller reloads by id */
            String json = "{\"id\":" + CryptoParse.longToString(soId) + ",\"docNo\":" + ErpJson.str(docNo) + "}";
            ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "SO_CREATE", json, now);
        }
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(soId));
    }

    static void insertLines(DbConnection conn, long companyId, long soId, ArrayList<Object> lines) {
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
            ln.add("0");
            ln.add(String.valueOf(in.discountBps));
            ln.add(String.valueOf(in.taxBps));
            try {
                ErpDb.execConn(conn,
                        "INSERT INTO sales_order_lines(company_id, sales_order_id, line_no, item_id, quantity, delivered_qty, unit_price_cents, returned_qty, discount_bps, tax_bps) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        ln);
            } catch (Exception ex) {
                ArrayList<Object> basic = new ArrayList<Object>();
                basic.add(CryptoParse.longToString(companyId));
                basic.add(CryptoParse.longToString(soId));
                basic.add(String.valueOf(lineNo));
                basic.add(CryptoParse.longToString(in.itemId));
                basic.add(String.valueOf(in.quantity));
                basic.add("0");
                basic.add(CryptoParse.longToString(in.unitPriceCents));
                basic.add("0");
                ErpDb.execConn(conn,
                        "INSERT INTO sales_order_lines(company_id, sales_order_id, line_no, item_id, quantity, delivered_qty, unit_price_cents, returned_qty) VALUES (?,?,?,?,?,?,?,?)",
                        basic);
            }
            lineNo = lineNo + 1;
            i = i + 1;
        }
    }
}

class SoUpdateWork implements DbTransactionWork {
    long companyId;
    long soId;
    long customerId;
    long warehouseId;
    long orderDate;
    long deliveryDate;
    String notes;
    String currency;
    String contactName;
    ArrayList<Object> lines;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        Object st = ErpDb.queryOneConn(conn,
                "SELECT approval_status FROM sales_orders WHERE id=? AND company_id=?", p, new StringMapper());
        if (st == null) {
            throw new TransactionException("SO not found");
        }
        if (((String) st).equals(ApprovalStatus.DRAFT) == false) {
            throw new TransactionException("SO not draft — approved docs cannot be edited");
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
        up.add(CryptoParse.longToString(customerId));
        up.add(CryptoParse.longToString(warehouseId));
        up.add(CryptoParse.longToString(od));
        up.add(CryptoParse.longToString(deliveryDate));
        up.add(notes);
        up.add(currency);
        up.add(contactName);
        up.add(CryptoParse.longToString(now));
        up.add(CryptoParse.longToString(soId));
        up.add(CryptoParse.longToString(companyId));
        try {
            ErpDb.execConn(conn,
                    "UPDATE sales_orders SET customer_id=?, warehouse_id=?, order_date=?, delivery_date=?, notes=?, currency=?, contact_name=?, updated_at=? WHERE id=? AND company_id=?",
                    up);
        } catch (Exception ex) {
            ArrayList<Object> up2 = new ArrayList<Object>();
            up2.add(CryptoParse.longToString(customerId));
            up2.add(CryptoParse.longToString(warehouseId));
            up2.add(CryptoParse.longToString(od));
            up2.add(CryptoParse.longToString(deliveryDate));
            up2.add(notes);
            up2.add(currency);
            up2.add(CryptoParse.longToString(now));
            up2.add(CryptoParse.longToString(soId));
            up2.add(CryptoParse.longToString(companyId));
            ErpDb.execConn(conn,
                    "UPDATE sales_orders SET customer_id=?, warehouse_id=?, order_date=?, delivery_date=?, notes=?, currency=?, updated_at=? WHERE id=? AND company_id=?",
                    up2);
        }
        ErpDb.execConn(conn,
                "DELETE FROM sales_order_lines WHERE sales_order_id=" + CryptoParse.longToString(soId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        SoCreateWork.insertLines(conn, companyId, soId, lines);
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(soId));
    }
}

class SoStatusWork implements DbTransactionWork {
    long companyId;
    long soId;
    String action;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(soId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult hdr = ErpDb.queryConn(conn,
                "SELECT approval_status, fulfillment_status, warehouse_id FROM sales_orders WHERE id=? AND company_id=?",
                p);
        if (hdr.rows.size() == 0) {
            throw new TransactionException("SO not found");
        }
        Row h = hdr.rows.get(0);
        String approval = h.getString(0);
        String fulfillment = h.getString(1);
        long warehouseId = h.getLong(2);
        long now = AsyncClockNative.wallMillis();
        String nextApproval = approval;
        String nextFulfillment = fulfillment;
        if (action.equals("submit")) {
            if (approval.equals(ApprovalStatus.DRAFT) == false) {
                throw new TransactionException("SO not draft");
            }
            nextApproval = ApprovalStatus.SUBMITTED;
        } else {
            if (action.equals("approve")) {
                if (approval.equals(ApprovalStatus.DRAFT) == false
                        && approval.equals(ApprovalStatus.SUBMITTED) == false) {
                    throw new TransactionException("SO not approvable");
                }
                if (warehouseId <= 0L) {
                    throw new TransactionException("warehouse required to approve/reserve");
                }
                nextApproval = ApprovalStatus.APPROVED;
                /* Best-effort reserve open qty per line — does not change on-hand */
                DbQueryResult lines = ErpDb.queryConn(conn,
                        "SELECT id, item_id, quantity, delivered_qty FROM sales_order_lines WHERE sales_order_id=? AND company_id=? ORDER BY line_no",
                        p);
                int li = 0;
                while (li < lines.rows.size()) {
                    Row lr = lines.rows.get(li);
                    long lineId = lr.getLong(0);
                    long itemId = lr.getLong(1);
                    int qty = lr.getInt(2);
                    int delivered = lr.getInt(3);
                    int open = qty - delivered;
                    if (open > 0) {
                        ErpReservation.reserveLineConn(conn, companyId, soId, lineId, warehouseId, itemId, open,
                                now);
                    }
                    li = li + 1;
                }
            } else {
                if (action.equals("cancel")) {
                    if (approval.equals(ApprovalStatus.CANCELLED)) {
                        throw new TransactionException("SO already cancelled");
                    }
                    DbQueryResult rq = ErpDb.queryConn(conn,
                            "SELECT COALESCE(SUM(delivered_qty),0) FROM sales_order_lines WHERE sales_order_id=? AND company_id=?",
                            p);
                    int del = 0;
                    if (rq.rows.size() > 0) {
                        del = rq.rows.get(0).getInt(0);
                    }
                    if (del > 0) {
                        throw new TransactionException("SO has deliveries — cancel blocked");
                    }
                    ErpReservation.releaseSoConn(conn, companyId, soId, now);
                    nextApproval = ApprovalStatus.CANCELLED;
                } else {
                    if (action.equals("close")) {
                        if (approval.equals(ApprovalStatus.APPROVED) == false) {
                            throw new TransactionException("SO not closable");
                        }
                        ErpReservation.releaseSoConn(conn, companyId, soId, now);
                        nextFulfillment = FulfillmentStatus.CLOSED;
                    } else {
                        throw new TransactionException("unknown action");
                    }
                }
            }
        }
        String compat = ErpSales.compositeStatus(nextApproval, nextFulfillment);
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(nextApproval);
        up.add(nextFulfillment);
        up.add(compat);
        up.add(CryptoParse.longToString(now));
        up.add(CryptoParse.longToString(soId));
        up.add(CryptoParse.longToString(companyId));
        ErpDb.execConn(conn,
                "UPDATE sales_orders SET approval_status=?, fulfillment_status=?, status=?, updated_at=? WHERE id=? AND company_id=?",
                up);
        return JnFuture.completed(Erp.loop, CryptoParse.longToString(soId));
    }
}

class SoLineInput {
    long itemId;
    int quantity;
    long unitPriceCents;
    int discountBps;
    int taxBps;
}
