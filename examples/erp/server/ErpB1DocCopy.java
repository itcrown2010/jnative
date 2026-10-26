/**
 * Copy-from chain (SAP B1 "Copy To / Copy From"): builds a draft target document from a base document,
 * defaulting to the open quantity of every base line. Quotations copy into the legacy SO / PO engines.
 * @author 张晓达
 */
class ErpB1DocCopy {
    static long taxCodeIdForRate(long companyId, long bps) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(ErpB1Util.lng(bps));
        return ErpNum.asLong(ErpDb.queryOne("SELECT id FROM tax_codes WHERE company_id=? AND rate_bps=? AND active=1 ORDER BY id LIMIT 1", p, new LongMapper()));
    }

    /** selected: baseLineId -> quantity (from body "lines"); null/empty means every line with open quantity. */
    static long selectedQty(JsonObject body, long baseLineId, long openQty, int hasSelection) {
        if (hasSelection == 0) {
            return openQty;
        }
        JsonValue lv = body.get("lines");
        JsonArray arr = lv.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject o = arr.get(i).asObject();
            if (ErpNum.parseLong(ErpB1Util.text(o, "baseLineId", "0")) == baseLineId) {
                long q = ErpNum.parseLong(ErpB1Util.text(o, "quantity", ErpB1Util.text(o, "qty", "0")));
                if (q > openQty) {
                    throw new RuntimeException("quantity exceeds open quantity for baseLineId " + ErpB1Util.lng(baseLineId));
                }
                return q;
            }
            i = i + 1;
        }
        return 0L;
    }

    static String copy(long companyId, long userId, String toType, String fromType, long fromId, JsonObject body) {
        if (toType.equals("SO") && fromType.equals("SQ")) {
            return quoteToOrder(companyId, userId, fromId, 1);
        }
        if (toType.equals("PO") && fromType.equals("PQ")) {
            return quoteToOrder(companyId, userId, fromId, 0);
        }
        if (ErpB1Docs.validType(toType) == 0 || ErpB1Docs.isPosting(toType) == 0
                || ErpB1Docs.baseAllowed(toType, fromType) == 0 || fromType.length() == 0) {
            throw new RuntimeException("copy " + fromType + " -> " + toType + " is not supported");
        }
        long partnerId = ErpB1Docs.basePartner(companyId, fromType, fromId);
        if (partnerId <= 0L) {
            throw new RuntimeException("base document not found or not posted");
        }
        int hasSel = 0;
        if (body != null) {
            JsonValue lv = body.get("lines");
            if (lv != null && lv.kind == JsonValue.ARRAY_KIND && lv.asArray().size() > 0) {
                hasSel = 1;
            }
        }
        B1Doc d = new B1Doc();
        d.docType = toType;
        d.partnerId = partnerId;
        d.docDate = AsyncClockNative.wallMillis();
        d.currency = ErpB1Master.localCurrency(companyId);
        d.rateMicro = 1000000L;
        d.baseType = fromType;
        d.baseId = fromId;
        d.shipTo = "";
        d.billTo = "";
        d.refNo = "";
        d.notes = "";
        d.idempotencyKey = "";
        d.lines = new ArrayList<Object>();
        String sql = "";
        int mk = 0;
        if (fromType.equals("DL")) {
            sql = "SELECT dl.id, dl.item_id, dl.quantity, sl.unit_price_cents, sl.discount_bps, sl.tax_bps FROM delivery_lines dl JOIN sales_order_lines sl ON sl.id=dl.sales_order_line_id WHERE dl.delivery_id=? AND dl.company_id=? ORDER BY dl.id";
            ArrayList<Object> hp = ErpB1Util.params();
            hp.add(ErpB1Util.lng(fromId));
            hp.add(ErpB1Util.lng(companyId));
            DbQueryResult hr = ErpDb.query("SELECT d.warehouse_id, so.doc_no, so.notes FROM deliveries d JOIN sales_orders so ON so.id=d.sales_order_id WHERE d.id=? AND d.company_id=?", hp);
            if (hr.rows.size() > 0) {
                d.warehouseId = hr.rows.get(0).getLong(0);
                d.refNo = hr.rows.get(0).getString(1);
            }
        } else if (fromType.equals("SO")) {
            sql = "SELECT id, item_id, quantity, unit_price_cents, discount_bps, tax_bps FROM sales_order_lines WHERE sales_order_id=? AND company_id=? ORDER BY id";
            ArrayList<Object> hp = ErpB1Util.params();
            hp.add(ErpB1Util.lng(fromId));
            hp.add(ErpB1Util.lng(companyId));
            DbQueryResult hr = ErpDb.query("SELECT warehouse_id, doc_no FROM sales_orders WHERE id=? AND company_id=?", hp);
            if (hr.rows.size() > 0) {
                d.warehouseId = hr.rows.get(0).getLong(0);
                d.refNo = hr.rows.get(0).getString(1);
            }
        } else if (fromType.equals("GR")) {
            sql = "SELECT g.id, g.item_id, g.quantity, pl.unit_price_cents, pl.discount_bps, pl.tax_bps FROM goods_receipt_lines g JOIN purchase_order_lines pl ON pl.id=g.purchase_order_line_id WHERE g.goods_receipt_id=? AND g.company_id=? ORDER BY g.id";
            ArrayList<Object> hp = ErpB1Util.params();
            hp.add(ErpB1Util.lng(fromId));
            hp.add(ErpB1Util.lng(companyId));
            DbQueryResult hr = ErpDb.query("SELECT warehouse_id, doc_no FROM goods_receipts WHERE id=? AND company_id=?", hp);
            if (hr.rows.size() > 0) {
                d.warehouseId = hr.rows.get(0).getLong(0);
                d.refNo = hr.rows.get(0).getString(1);
            }
        } else if (fromType.equals("PO")) {
            sql = "SELECT id, item_id, quantity, unit_price_cents, discount_bps, tax_bps FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=? ORDER BY id";
            ArrayList<Object> hp = ErpB1Util.params();
            hp.add(ErpB1Util.lng(fromId));
            hp.add(ErpB1Util.lng(companyId));
            DbQueryResult hr = ErpDb.query("SELECT warehouse_id, doc_no FROM purchase_orders WHERE id=? AND company_id=?", hp);
            if (hr.rows.size() > 0) {
                d.warehouseId = hr.rows.get(0).getLong(0);
                d.refNo = hr.rows.get(0).getString(1);
            }
        } else {
            mk = 1;
            sql = "SELECT id, item_id, quantity, unit_price_cents, discount_bps, tax_bps, tax_code_id, description, uom_code, warehouse_id FROM mk_doc_lines WHERE doc_id=? AND company_id=? ORDER BY id";
            ArrayList<Object> hp = ErpB1Util.params();
            hp.add(ErpB1Util.lng(fromId));
            hp.add(ErpB1Util.lng(companyId));
            DbQueryResult hr = ErpDb.query("SELECT currency, rate_micro, payment_terms_id, warehouse_id, doc_no, discount_bps FROM mk_docs WHERE id=? AND company_id=?", hp);
            if (hr.rows.size() > 0) {
                d.currency = hr.rows.get(0).getString(0);
                d.rateMicro = hr.rows.get(0).getLong(1);
                d.paymentTermsId = hr.rows.get(0).getLong(2);
                d.warehouseId = hr.rows.get(0).getLong(3);
                d.refNo = hr.rows.get(0).getString(4);
                d.discountBps = hr.rows.get(0).getLong(5);
            }
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(fromId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.query(sql, p);
        int i = 0;
        while (i < r.rows.size()) {
            Row x = r.rows.get(i);
            long lineId = x.getLong(0);
            long qty = x.getLong(2);
            long open = qty - ErpB1Docs.consumedQty(companyId, fromType, lineId, 0L);
            long take = selectedQty(body, lineId, open, hasSel);
            if (take > 0L) {
                B1Line l = new B1Line();
                l.itemId = x.getLong(1);
                l.quantity = take;
                l.unitPriceCents = x.getLong(3);
                l.discountBps = x.getLong(4);
                l.taxBps = x.getLong(5);
                l.taxCodeId = 0L;
                l.description = "";
                l.uomCode = "";
                l.warehouseId = d.warehouseId;
                if (mk != 0) {
                    l.taxCodeId = x.getLong(6);
                    l.description = x.getString(7);
                    l.uomCode = x.getString(8);
                    l.warehouseId = x.getLong(9);
                } else if (l.taxBps > 0L) {
                    l.taxCodeId = taxCodeIdForRate(companyId, l.taxBps);
                }
                l.baseType = fromType;
                l.baseId = fromId;
                l.baseLineId = lineId;
                d.lines.add(l);
            }
            i = i + 1;
        }
        if (d.lines.size() == 0) {
            throw new RuntimeException("nothing left to copy: base document is fully consumed");
        }
        ErpB1Docs.resolve(companyId, d);
        String saved = ErpB1Docs.saveJson(companyId, userId, d, 0L);
        return ErpB1Docs.get(companyId, toType, ErpB1Docs.idOf(saved));
    }

    static long firstWarehouse(long companyId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        return ErpNum.asLong(ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=? ORDER BY id LIMIT 1", p, new LongMapper()));
    }

    /** Sales/purchase quotation -> legacy order. Prices are converted to local currency. */
    static String quoteToOrder(long companyId, long userId, long quoteId, int sales) {
        String qtype = sales != 0 ? ErpB1Docs.SQ : ErpB1Docs.PQ;
        B1Doc q = ErpB1DocPost.load(companyId, qtype, quoteId);
        if (q.status.equals("OPEN") == false) {
            throw new RuntimeException("quotation must be OPEN to copy (status=" + q.status + ")");
        }
        if (sales != 0) {
            try {
                ErpB1Master.assertCreditOk(companyId, q.partnerId, q.totalLocalCents);
            } catch (Exception ex) {
                throw new RuntimeException(ErpB1Util.exMsg(ex, "CREDIT_BLOCKED"));
            }
        }
        long wh = q.warehouseId;
        if (wh <= 0L) {
            wh = firstWarehouse(companyId);
        }
        ArrayList<Object> lines = new ArrayList<Object>();
        int i = 0;
        while (i < q.lines.size()) {
            B1Line l = (B1Line) q.lines.get(i);
            if (l.itemId <= 0L) {
                throw new RuntimeException("order lines require item master records (text line " + String.valueOf(i + 1) + ")");
            }
            long price = ErpB1Master.toLocal(l.unitPriceCents, q.rateMicro);
            long disc = l.discountBps;
            if (q.discountBps > 0L) {
                disc = 10000L - ErpDocMoney.divHalfUp((10000L - disc) * (10000L - q.discountBps), 10000L);
            }
            if (sales != 0) {
                SoLineInput in = new SoLineInput();
                in.itemId = l.itemId;
                in.quantity = (int) l.quantity;
                in.unitPriceCents = price;
                in.discountBps = (int) disc;
                in.taxBps = (int) l.taxBps;
                lines.add(in);
            } else {
                PoLineInput in2 = new PoLineInput();
                in2.itemId = l.itemId;
                in2.quantity = (int) l.quantity;
                in2.unitPriceCents = price;
                in2.discountBps = (int) disc;
                in2.taxBps = (int) l.taxBps;
                lines.add(in2);
            }
            i = i + 1;
        }
        String local = ErpB1Master.localCurrency(companyId);
        String note = "From " + q.docNo;
        if (q.currency.equals(local) == false) {
            note = note + " (" + q.currency + " @ " + ErpB1Util.lng(q.rateMicro) + "e-6, prices converted)";
        }
        long now = AsyncClockNative.wallMillis();
        String targetNo = "";
        long targetId = 0L;
        String idem = "mk-" + qtype + "-" + ErpB1Util.lng(quoteId);
        if (sales != 0) {
            SalesOrderDto so = ErpSales.createDraftFull(companyId, q.partnerId, wh, now, 0L, note, local, lines, "", idem);
            targetId = so.id;
            targetNo = so.docNo;
        } else {
            PurchaseOrderDto po = ErpPurchasing.createDraftFull(companyId, q.partnerId, wh, now, 0L, note, local, lines, "", idem);
            targetId = po.id;
            targetNo = po.docNo;
        }
        ArrayList<Object> up = ErpB1Util.params();
        up.add(sales != 0 ? "SO" : "PO");
        up.add(ErpB1Util.lng(targetId));
        up.add(ErpB1Util.lng(now));
        up.add(ErpB1Util.lng(quoteId));
        up.add(ErpB1Util.lng(companyId));
        ErpDb.exec("UPDATE mk_docs SET status='CLOSED', target_type=?, target_id=?, updated_at=? WHERE id=? AND company_id=? AND status='OPEN'", up);
        try {
            ArrayList<Object> ref = ErpB1Util.params();
            ref.add(ErpB1Util.lng(companyId));
            ref.add("B1_COPY");
            ref.add(qtype);
            ref.add(ErpB1Util.lng(quoteId));
            ref.add(sales != 0 ? "SO" : "PO");
            ref.add(ErpB1Util.lng(targetId));
            ref.add(ErpB1Util.lng(now));
            ErpDb.exec("INSERT INTO document_references(company_id, ref_kind, from_doc_type, from_doc_id, to_doc_type, to_doc_id, created_at) VALUES (?,?,?,?,?,?,?)", ref);
        } catch (Exception ignored) {
        }
        return "{\"targetType\":" + ErpJson.str(sales != 0 ? "SO" : "PO") + ",\"targetId\":" + ErpB1Util.lng(targetId)
                + ",\"targetDocNo\":" + ErpJson.str(targetNo) + ",\"sourceType\":" + ErpJson.str(qtype)
                + ",\"sourceId\":" + ErpB1Util.lng(quoteId) + "}";
    }
}
