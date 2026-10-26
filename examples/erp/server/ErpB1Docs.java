/**
 * Unified marketing documents (SAP B1 ORDR/OINV/ORIN/OPQT style) on mk_docs / mk_doc_lines:
 * sales quotation, AR invoice, AR credit memo, purchase quotation, AP invoice, AP credit memo.
 * Legacy tables keep SO / DL / PO / GR; copy-from links them into the chain.
 * @author 张晓达
 */
class B1Line {
    long id;
    long itemId;
    String description;
    long warehouseId;
    long quantity;
    String uomCode;
    long unitPriceCents;
    long discountBps;
    long taxCodeId;
    long taxBps;
    long netCents;
    long taxCents;
    long totalCents;
    long isInventory;
    String baseType;
    long baseId;
    long baseLineId;
    long clearingCents;
}

class B1Doc {
    long id;
    String docType;
    String docNo;
    long partnerId;
    String partnerName;
    String status;
    long docDate;
    long dueDate;
    long validUntil;
    String currency;
    long rateMicro;
    long paymentTermsId;
    long priceListId;
    long warehouseId;
    String shipTo;
    String billTo;
    String refNo;
    long discountBps;
    long subtotalCents;
    long taxCents;
    long totalCents;
    long totalLocalCents;
    String baseType;
    long baseId;
    String notes;
    String idempotencyKey;
    long createdBy;
    ArrayList<Object> lines;
}

class ErpB1Docs {
    static String SQ = "SQ";
    static String ARINV = "ARINV";
    static String ARCM = "ARCM";
    static String PQ = "PQ";
    static String APINV = "APINV";
    static String APCM = "APCM";

    static int validType(String t) {
        if (t == null) {
            return 0;
        }
        if (t.equals(SQ) || t.equals(ARINV) || t.equals(ARCM) || t.equals(PQ) || t.equals(APINV) || t.equals(APCM)) {
            return 1;
        }
        return 0;
    }

    static int isSales(String t) {
        if (t.equals(SQ) || t.equals(ARINV) || t.equals(ARCM)) {
            return 1;
        }
        return 0;
    }

    static int isPosting(String t) {
        if (t.equals(ARINV) || t.equals(ARCM) || t.equals(APINV) || t.equals(APCM)) {
            return 1;
        }
        return 0;
    }

    static String prefix(String t) {
        if (t.equals(SQ)) {
            return "SQ-";
        }
        if (t.equals(ARINV)) {
            return "ARI-";
        }
        if (t.equals(ARCM)) {
            return "ARC-";
        }
        if (t.equals(PQ)) {
            return "PQ-";
        }
        if (t.equals(APINV)) {
            return "API-";
        }
        return "APC-";
    }

    static String label(String t) {
        if (t.equals(SQ)) {
            return "Sales Quotation";
        }
        if (t.equals(ARINV)) {
            return "A/R Invoice";
        }
        if (t.equals(ARCM)) {
            return "A/R Credit Memo";
        }
        if (t.equals(PQ)) {
            return "Purchase Quotation";
        }
        if (t.equals(APINV)) {
            return "A/P Invoice";
        }
        return "A/P Credit Memo";
    }

    /** Base document types allowed for each target document type. */
    static int baseAllowed(String type, String baseType) {
        if (baseType == null || baseType.length() == 0) {
            return 1;
        }
        if (type.equals(ARINV)) {
            return baseType.equals("DL") || baseType.equals("SO") ? 1 : 0;
        }
        if (type.equals(ARCM)) {
            return baseType.equals(ARINV) ? 1 : 0;
        }
        if (type.equals(APINV)) {
            return baseType.equals("GR") || baseType.equals("PO") ? 1 : 0;
        }
        if (type.equals(APCM)) {
            return baseType.equals(APINV) ? 1 : 0;
        }
        return 0;
    }

    /* ------------------------------------------------------------------ calc */

    static void calcLine(B1Line l, long headerDiscBps) {
        long gross = l.quantity * l.unitPriceCents;
        long disc = ErpDocMoney.divHalfUp(gross * l.discountBps, 10000L);
        long net = gross - disc;
        if (net < 0L) {
            net = 0L;
        }
        if (headerDiscBps > 0L) {
            net = net - ErpDocMoney.divHalfUp(net * headerDiscBps, 10000L);
        }
        l.netCents = net;
        l.taxCents = ErpDocMoney.divHalfUp(net * l.taxBps, 10000L);
        l.totalCents = l.netCents + l.taxCents;
    }

    static void calcDoc(B1Doc d) {
        long sub = 0L;
        long tax = 0L;
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            calcLine(l, d.discountBps);
            sub = sub + l.netCents;
            tax = tax + l.taxCents;
            i = i + 1;
        }
        d.subtotalCents = sub;
        d.taxCents = tax;
        d.totalCents = sub + tax;
        long loc = 0L;
        int k = 0;
        while (k < d.lines.size()) {
            B1Line ll = (B1Line) d.lines.get(k);
            loc = loc + ErpB1Master.toLocal(ll.netCents, d.rateMicro) + ErpB1Master.toLocal(ll.taxCents, d.rateMicro);
            k = k + 1;
        }
        d.totalLocalCents = loc;
    }

    /* ------------------------------------------------------------------ parse */

    static B1Doc parseBody(long companyId, String type, JsonObject body) {
        B1Doc d = new B1Doc();
        d.docType = type;
        d.partnerId = ErpNum.parseLong(ErpB1Util.text(body, "partnerId", ErpB1Util.text(body, "customerId", ErpB1Util.text(body, "supplierId", "0"))));
        long now = AsyncClockNative.wallMillis();
        d.docDate = ErpDocMoney.parseBizDate(body, "docDate", "date");
        if (d.docDate <= 0L) {
            d.docDate = now;
        }
        d.dueDate = ErpDocMoney.parseBizDate(body, "dueDate", "");
        d.validUntil = ErpDocMoney.parseBizDate(body, "validUntil", "");
        d.currency = ErpB1Util.text(body, "currency", "");
        d.rateMicro = ErpNum.parseLong(ErpB1Util.text(body, "rateMicro", "0"));
        d.paymentTermsId = ErpNum.parseLong(ErpB1Util.text(body, "paymentTermsId", "0"));
        d.priceListId = ErpNum.parseLong(ErpB1Util.text(body, "priceListId", "0"));
        d.warehouseId = ErpNum.parseLong(ErpB1Util.text(body, "warehouseId", "0"));
        d.shipTo = ErpB1Util.text(body, "shipTo", "");
        d.billTo = ErpB1Util.text(body, "billTo", "");
        d.refNo = ErpB1Util.text(body, "refNo", "");
        d.discountBps = ErpNum.parseLong(ErpB1Util.text(body, "discountBps", "0"));
        d.notes = ErpB1Util.text(body, "notes", "");
        d.idempotencyKey = ErpB1Util.text(body, "idempotencyKey", "");
        d.partnerName = "";
        d.baseType = "";
        d.baseId = 0L;
        d.lines = new ArrayList<Object>();
        JsonValue lv = body.get("lines");
        if (lv != null && lv.kind == JsonValue.ARRAY_KIND) {
            JsonArray arr = lv.asArray();
            int i = 0;
            while (i < arr.size()) {
                JsonObject o = arr.get(i).asObject();
                B1Line l = new B1Line();
                l.itemId = ErpNum.parseLong(ErpB1Util.text(o, "itemId", "0"));
                l.description = ErpB1Util.text(o, "description", "");
                l.warehouseId = ErpNum.parseLong(ErpB1Util.text(o, "warehouseId", "0"));
                l.quantity = ErpNum.parseLong(ErpB1Util.text(o, "quantity", ErpB1Util.text(o, "qty", "0")));
                l.uomCode = ErpB1Util.text(o, "uomCode", "");
                l.unitPriceCents = -1L;
                if (ErpB1Util.has(o, "unitPriceCents") != 0) {
                    l.unitPriceCents = ErpNum.parseLong(ErpB1Util.text(o, "unitPriceCents", "0"));
                }
                l.discountBps = -1L;
                if (ErpB1Util.has(o, "discountBps") != 0) {
                    l.discountBps = ErpNum.parseLong(ErpB1Util.text(o, "discountBps", "0"));
                }
                l.taxCodeId = ErpNum.parseLong(ErpB1Util.text(o, "taxCodeId", "0"));
                l.taxBps = -1L;
                if (ErpB1Util.has(o, "taxBps") != 0) {
                    l.taxBps = ErpNum.parseLong(ErpB1Util.text(o, "taxBps", "0"));
                }
                l.baseType = ErpB1Util.text(o, "baseType", "");
                l.baseId = ErpNum.parseLong(ErpB1Util.text(o, "baseId", "0"));
                l.baseLineId = ErpNum.parseLong(ErpB1Util.text(o, "baseLineId", "0"));
                d.lines.add(l);
                i = i + 1;
            }
        }
        return d;
    }

    /* ------------------------------------------------------------------ resolve + validate */

    static void resolve(long companyId, B1Doc d) {
        resolveEx(companyId, d, 0L);
    }

    static void resolveForUpdate(long companyId, B1Doc d, long id) {
        resolveEx(companyId, d, id);
    }

    static void resolveEx(long companyId, B1Doc d, long excludeId) {
        String type = d.docType;
        int sales = isSales(type);
        ArrayList<Object> pp = ErpB1Util.params();
        pp.add(ErpB1Util.lng(d.partnerId));
        pp.add(ErpB1Util.lng(companyId));
        DbQueryResult pr = ErpDb.query("SELECT name, kind, status, payment_terms_id, price_list_id, currency FROM business_partners WHERE id=? AND company_id=?", pp);
        if (pr.rows.size() == 0) {
            throw new RuntimeException("partner not found");
        }
        Row prow = pr.rows.get(0);
        String kind = prow.getString(1);
        if (sales != 0 && kind.equals("SUPPLIER")) {
            throw new RuntimeException("partner is not a customer");
        }
        if (sales == 0 && kind.equals("CUSTOMER")) {
            throw new RuntimeException("partner is not a supplier");
        }
        String pstatus = prow.getString(2);
        if (pstatus != null && pstatus.equals("ACTIVE") == false) {
            throw new RuntimeException("PARTNER_" + pstatus);
        }
        d.partnerName = prow.getString(0);
        if (d.paymentTermsId <= 0L) {
            d.paymentTermsId = prow.getLong(3);
        }
        if (d.priceListId <= 0L && sales != 0) {
            d.priceListId = prow.getLong(4);
        }
        if (d.currency == null || d.currency.length() == 0) {
            d.currency = prow.getString(5);
            if (d.currency == null || d.currency.length() == 0) {
                d.currency = ErpB1Master.localCurrency(companyId);
            }
        }
        if (ErpB1Master.codeExists(companyId, "currencies", "code", d.currency) == 0) {
            throw new RuntimeException("currency not found: " + d.currency);
        }
        if (d.rateMicro <= 0L) {
            d.rateMicro = ErpB1Master.rateMicro(companyId, d.currency, d.docDate);
            if (d.rateMicro <= 0L) {
                throw new RuntimeException("NO_EXCHANGE_RATE " + d.currency);
            }
        }
        if (d.discountBps < 0L || d.discountBps > 10000L) {
            throw new RuntimeException("discountBps must be 0..10000");
        }
        if (d.dueDate <= 0L) {
            long days = 0L;
            if (d.paymentTermsId > 0L) {
                ArrayList<Object> tp = ErpB1Util.params();
                tp.add(ErpB1Util.lng(d.paymentTermsId));
                tp.add(ErpB1Util.lng(companyId));
                days = ErpNum.asLong(ErpDb.queryOne("SELECT net_days FROM payment_terms WHERE id=? AND company_id=?", tp, new LongMapper()));
            }
            d.dueDate = d.docDate + days * 86400000L;
        }
        if (d.lines.size() == 0) {
            throw new RuntimeException("lines required");
        }
        resolveLines(companyId, d, excludeId);
    }

    static void resolveLines(long companyId, B1Doc d, long excludeDocId) {
        int sales = isSales(d.docType);
        String local = ErpB1Master.localCurrency(companyId);
        String docBaseType = "";
        long docBaseId = 0L;
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            String where = "line " + String.valueOf(i + 1) + ": ";
            if (l.quantity <= 0L) {
                throw new RuntimeException(where + "quantity must be > 0");
            }
            if (l.baseType == null) {
                l.baseType = "";
            }
            if (l.description == null) {
                l.description = "";
            }
            if (l.uomCode == null) {
                l.uomCode = "";
            }
            l.isInventory = 0L;
            if (l.itemId > 0L) {
                ArrayList<Object> ip = ErpB1Util.params();
                ip.add(ErpB1Util.lng(l.itemId));
                ip.add(ErpB1Util.lng(companyId));
                DbQueryResult ir = ErpDb.query("SELECT sku, name, is_inventory, status, is_sales, is_purchase FROM items WHERE id=? AND company_id=?", ip);
                if (ir.rows.size() == 0) {
                    throw new RuntimeException(where + "item not found");
                }
                Row irow = ir.rows.get(0);
                if (irow.getString(3) != null && irow.getString(3).equals("ACTIVE") == false) {
                    throw new RuntimeException(where + "item inactive: " + irow.getString(0));
                }
                if (sales != 0 && irow.getLong(4) == 0L) {
                    throw new RuntimeException(where + "item not flagged for sales: " + irow.getString(0));
                }
                if (sales == 0 && irow.getLong(5) == 0L) {
                    throw new RuntimeException(where + "item not flagged for purchase: " + irow.getString(0));
                }
                l.isInventory = irow.getLong(2);
                if (l.description.length() == 0) {
                    l.description = irow.getString(1);
                }
                int needDefaults = 0;
                if (l.unitPriceCents < 0L || l.discountBps < 0L || (l.taxBps < 0L && l.taxCodeId <= 0L)
                        || l.uomCode.length() == 0) {
                    needDefaults = 1;
                }
                if (needDefaults != 0) {
                    B1Price pi = ErpB1Master.priceInfo(companyId, d.partnerId, l.itemId, sales);
                    if (l.unitPriceCents < 0L) {
                        long up = pi.priceCents;
                        if (d.currency.equals(local) == false && d.rateMicro > 0L) {
                            up = ErpDocMoney.divHalfUp(up * 1000000L, d.rateMicro);
                        }
                        l.unitPriceCents = up;
                    }
                    if (l.discountBps < 0L) {
                        l.discountBps = pi.discountBps;
                    }
                    if (l.taxBps < 0L && l.taxCodeId <= 0L) {
                        l.taxCodeId = pi.taxCodeId;
                        l.taxBps = pi.taxBps;
                    }
                    if (l.uomCode.length() == 0) {
                        l.uomCode = pi.uom;
                    }
                }
            } else {
                if (l.description.length() == 0) {
                    throw new RuntimeException(where + "item or description required");
                }
                if (l.unitPriceCents < 0L) {
                    throw new RuntimeException(where + "unitPriceCents required for non-item line");
                }
                if (l.discountBps < 0L) {
                    l.discountBps = 0L;
                }
            }
            if (l.discountBps < 0L) {
                l.discountBps = 0L;
            }
            if (l.discountBps > 10000L) {
                throw new RuntimeException(where + "discountBps must be 0..10000");
            }
            if (l.taxCodeId > 0L) {
                ArrayList<Object> tp = ErpB1Util.params();
                tp.add(ErpB1Util.lng(l.taxCodeId));
                tp.add(ErpB1Util.lng(companyId));
                DbQueryResult tr = ErpDb.query("SELECT rate_bps, direction, active FROM tax_codes WHERE id=? AND company_id=?", tp);
                if (tr.rows.size() == 0) {
                    throw new RuntimeException(where + "tax code not found");
                }
                if (tr.rows.get(0).getLong(2) == 0L) {
                    throw new RuntimeException(where + "tax code inactive");
                }
                String dir = tr.rows.get(0).getString(1);
                if (sales != 0 && dir.equals("IN")) {
                    throw new RuntimeException(where + "tax code is input-only");
                }
                if (sales == 0 && dir.equals("OUT")) {
                    throw new RuntimeException(where + "tax code is output-only");
                }
                l.taxBps = tr.rows.get(0).getLong(0);
            }
            if (l.taxBps < 0L) {
                l.taxBps = 0L;
            }
            if (l.taxBps > 10000L) {
                throw new RuntimeException(where + "taxBps must be 0..10000");
            }
            if (l.warehouseId <= 0L) {
                l.warehouseId = d.warehouseId;
            }
            if (l.warehouseId > 0L && ErpB1Master.rowExists(companyId, "warehouses", l.warehouseId) == 0) {
                throw new RuntimeException(where + "warehouse not found");
            }
            if (l.baseType.length() > 0) {
                if (docBaseType.length() == 0) {
                    docBaseType = l.baseType;
                    docBaseId = l.baseId;
                } else if (docBaseType.equals(l.baseType) == false || docBaseId != l.baseId) {
                    throw new RuntimeException(where + "MIXED_BASE documents are not supported");
                }
                checkBase(companyId, d, l, excludeDocId, where);
            } else if (docBaseType.length() > 0) {
                throw new RuntimeException(where + "MIXED_BASE: every line must reference the base document");
            }
            i = i + 1;
        }
        if (docBaseType.length() > 0) {
            int k = 0;
            while (k < d.lines.size()) {
                B1Line l2 = (B1Line) d.lines.get(k);
                if (l2.baseType.length() == 0) {
                    throw new RuntimeException("MIXED_BASE: every line must reference the base document");
                }
                k = k + 1;
            }
        }
        d.baseType = docBaseType;
        d.baseId = docBaseId;
        calcDoc(d);
    }

    /* ------------------------------------------------------------------ base chain checks */

    static long baseLineQty(long companyId, String baseType, long baseId, long baseLineId, long itemId) {
        String sql = "";
        if (baseType.equals("DL")) {
            sql = "SELECT quantity, item_id FROM delivery_lines WHERE id=? AND company_id=? AND delivery_id=?";
        } else if (baseType.equals("SO")) {
            sql = "SELECT quantity, item_id FROM sales_order_lines WHERE id=? AND company_id=? AND sales_order_id=?";
        } else if (baseType.equals("GR")) {
            sql = "SELECT quantity, item_id FROM goods_receipt_lines WHERE id=? AND company_id=? AND goods_receipt_id=?";
        } else if (baseType.equals("PO")) {
            sql = "SELECT quantity, item_id FROM purchase_order_lines WHERE id=? AND company_id=? AND purchase_order_id=?";
        } else if (baseType.equals(ARINV) || baseType.equals(APINV)) {
            sql = "SELECT quantity, item_id FROM mk_doc_lines WHERE id=? AND company_id=? AND doc_id=?";
        } else {
            return -1L;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(baseLineId));
        p.add(ErpB1Util.lng(companyId));
        p.add(ErpB1Util.lng(baseId));
        DbQueryResult r = ErpDb.query(sql, p);
        if (r.rows.size() == 0) {
            return -1L;
        }
        if (itemId > 0L && r.rows.get(0).getLong(1) != itemId) {
            return -2L;
        }
        return r.rows.get(0).getLong(0);
    }

    static long consumedQty(long companyId, String baseType, long baseLineId, long excludeDocId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(baseType);
        p.add(ErpB1Util.lng(baseLineId));
        p.add(ErpB1Util.lng(excludeDocId));
        return ErpNum.asLong(ErpDb.queryOne("SELECT COALESCE(SUM(l.quantity),0) FROM mk_doc_lines l JOIN mk_docs d ON d.id=l.doc_id WHERE l.company_id=? AND l.base_type=? AND l.base_line_id=? AND d.status<>'CANCELLED' AND d.id<>?", p, new LongMapper()));
    }

    static long basePartner(long companyId, String baseType, long baseId) {
        String sql = "";
        if (baseType.equals("DL")) {
            sql = "SELECT so.customer_id FROM deliveries d JOIN sales_orders so ON so.id=d.sales_order_id WHERE d.id=? AND d.company_id=?";
        } else if (baseType.equals("SO")) {
            sql = "SELECT customer_id FROM sales_orders WHERE id=? AND company_id=?";
        } else if (baseType.equals("GR")) {
            sql = "SELECT po.supplier_id FROM goods_receipts g JOIN purchase_orders po ON po.id=g.purchase_order_id WHERE g.id=? AND g.company_id=?";
        } else if (baseType.equals("PO")) {
            sql = "SELECT supplier_id FROM purchase_orders WHERE id=? AND company_id=?";
        } else if (baseType.equals(ARINV) || baseType.equals(APINV)) {
            sql = "SELECT partner_id FROM mk_docs WHERE id=? AND company_id=? AND status='POSTED'";
        } else {
            return 0L;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(baseId));
        p.add(ErpB1Util.lng(companyId));
        return ErpNum.asLong(ErpDb.queryOne(sql, p, new LongMapper()));
    }

    static void checkBase(long companyId, B1Doc d, B1Line l, long excludeDocId, String where) {
        if (baseAllowed(d.docType, l.baseType) == 0) {
            throw new RuntimeException(where + "base type " + l.baseType + " not allowed for " + d.docType);
        }
        if (l.baseId <= 0L || l.baseLineId <= 0L) {
            throw new RuntimeException(where + "baseId and baseLineId required");
        }
        long bp = basePartner(companyId, l.baseType, l.baseId);
        if (bp <= 0L) {
            throw new RuntimeException(where + "base document not found or not posted");
        }
        if (bp != d.partnerId) {
            throw new RuntimeException(where + "base document belongs to a different partner");
        }
        long bq = baseLineQty(companyId, l.baseType, l.baseId, l.baseLineId, l.itemId);
        if (bq == -1L) {
            throw new RuntimeException(where + "base line not found");
        }
        if (bq == -2L) {
            throw new RuntimeException(where + "base line item mismatch");
        }
        long used = consumedQty(companyId, l.baseType, l.baseLineId, excludeDocId);
        if (l.quantity > bq - used) {
            throw new RuntimeException(where + "quantity exceeds open base quantity (" + ErpB1Util.lng(bq - used) + ")");
        }
    }

    /* ------------------------------------------------------------------ persistence */

    static String saveJson(long companyId, long userId, B1Doc d, long updateId) {
        B1DocSaveWork w = new B1DocSaveWork();
        w.companyId = companyId;
        w.userId = userId;
        w.doc = d;
        w.updateId = updateId;
        PostResultDto r = ErpPosting.runPostingWork(w);
        ErpDb.reconnect();
        if (r.ok == 0) {
            throw new RuntimeException(r.message);
        }
        return r.documentJson;
    }

    static long idOf(String json) {
        int a = json.indexOf("\"id\":");
        if (a < 0) {
            return 0L;
        }
        int b = a + 5;
        int e = b;
        while (e < json.length()) {
            int c = json.charAt(e);
            if (c < 48 || c > 57) {
                break;
            }
            e = e + 1;
        }
        return ErpNum.parseLong(json.substring(b, e));
    }

    static String headerSpec() {
        return "id:i|doc_type:s|doc_no:s|partner_id:i|partner_name:s|status:s|doc_date:i|due_date:i|valid_until:i|currency:s|rate_micro:i|payment_terms_id:i|price_list_id:i|warehouse_id:i|ship_to:s|bill_to:s|ref_no:s|discount_bps:i|subtotal_cents:i|tax_cents:i|total_cents:i|total_local_cents:i|base_type:s|base_id:i|target_type:s|target_id:i|journal_id:i|subledger_id:i|notes:s|created_at:i|updated_at:i|posted_at:i";
    }

    static String lineSpec() {
        return "l.id:i|l.line_no:i|l.item_id:i|COALESCE(i.sku,'') AS item_sku:s|l.description:s|l.warehouse_id:i|l.quantity:i|l.uom_code:s|l.unit_price_cents:i|l.discount_bps:i|l.tax_code_id:i|l.tax_bps:i|l.net_cents:i|l.tax_cents:i|l.total_cents:i|l.is_inventory:i|l.base_type:s|l.base_id:i|l.base_line_id:i|l.clearing_cents:i";
    }

    static String headerSelect() {
        return "SELECT " + ErpB1Util.specSelect(headerSpec()) + " FROM mk_docs";
    }

    static String get(long companyId, String type, long id) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        p.add(type);
        DbQueryResult r = ErpDb.query(headerSelect() + " WHERE id=? AND company_id=? AND doc_type=?", p);
        if (r.rows.size() == 0) {
            return null;
        }
        String head = ErpB1Util.rowJsonSpec(r.rows.get(0), headerSpec());
        ArrayList<Object> lp = ErpB1Util.params();
        lp.add(ErpB1Util.lng(id));
        lp.add(ErpB1Util.lng(companyId));
        DbQueryResult lr = ErpDb.query("SELECT " + ErpB1Util.specSelect(lineSpec()) + " FROM mk_doc_lines l LEFT JOIN items i ON i.id=l.item_id WHERE l.doc_id=? AND l.company_id=? ORDER BY l.line_no", lp);
        String extra = "";
        long sub = r.rows.get(0).getLong(27);
        if (sub > 0L) {
            ArrayList<Object> sp = ErpB1Util.params();
            sp.add(ErpB1Util.lng(sub));
            sp.add(ErpB1Util.lng(companyId));
            String tbl = "ar_invoices";
            if (isSales(type) == 0) {
                tbl = "ap_invoices";
            }
            DbQueryResult sr = ErpDb.query("SELECT paid_amount, open_amount, settlement_status FROM " + tbl + " WHERE id=? AND company_id=?", sp);
            if (sr.rows.size() > 0) {
                extra = ",\"paidLocalCents\":" + ErpB1Util.lng(sr.rows.get(0).getLong(0))
                        + ",\"openLocalCents\":" + ErpB1Util.lng(sr.rows.get(0).getLong(1))
                        + ",\"settlementStatus\":" + ErpJson.str(sr.rows.get(0).getString(2));
            }
        }
        return head.substring(0, head.length() - 1) + extra + ",\"lines\":" + ErpB1Util.rowsJsonSpec(lr, lineSpec()) + "}";
    }

    static String list(long companyId, String type, WebContext ctx) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(type);
        String sql = headerSelect() + " WHERE company_id=? AND doc_type=?";
        String st = ctx.query("status");
        if (st != null && st.length() > 0) {
            sql = sql + " AND status=?";
            p.add(st);
        }
        long partner = ErpNum.parseLong(ctx.query("partnerId"));
        if (partner > 0L) {
            sql = sql + " AND partner_id=?";
            p.add(ErpB1Util.lng(partner));
        }
        String bt = ctx.query("baseType");
        if (bt != null && bt.length() > 0) {
            sql = sql + " AND base_type=?";
            p.add(bt);
            long bid = ErpNum.parseLong(ctx.query("baseId"));
            if (bid > 0L) {
                sql = sql + " AND base_id=?";
                p.add(ErpB1Util.lng(bid));
            }
        }
        String q = ctx.query("q");
        if (q != null && q.length() > 0) {
            String like = "%" + ErpB1Util.safeLike(q) + "%";
            sql = sql + " AND (doc_no LIKE ? OR partner_name LIKE ? OR ref_no LIKE ?)";
            p.add(like);
            p.add(like);
            p.add(like);
        }
        long limit = ErpNum.parseLong(ctx.query("limit"));
        if (limit <= 0L || limit > 2000L) {
            limit = 500L;
        }
        sql = sql + " ORDER BY id DESC LIMIT " + ErpB1Util.lng(limit);
        return ErpB1Util.rowsJsonSpec(ErpDb.query(sql, p), headerSpec());
    }

    static String statusOf(long companyId, String type, long id) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        p.add(type);
        Object v = ErpDb.queryOne("SELECT status FROM mk_docs WHERE id=? AND company_id=? AND doc_type=?", p, new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }

    static String submit(long companyId, String type, long id) {
        String st = statusOf(companyId, type, id);
        if (st.length() == 0) {
            throw new RuntimeException("not found");
        }
        if (isPosting(type) != 0) {
            throw new RuntimeException("use post for " + type);
        }
        if (st.equals("OPEN")) {
            return get(companyId, type, id);
        }
        if (st.equals("DRAFT") == false) {
            throw new RuntimeException("cannot submit status " + st);
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(AsyncClockNative.wallMillis()));
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        ErpDb.exec("UPDATE mk_docs SET status='OPEN', updated_at=? WHERE id=? AND company_id=? AND status='DRAFT'", p);
        return get(companyId, type, id);
    }

    /** Cancels a draft or an open quotation that has not been copied. Posted documents use reverse. */
    static String cancel(long companyId, String type, long id) {
        String st = statusOf(companyId, type, id);
        if (st.length() == 0) {
            throw new RuntimeException("not found");
        }
        if (st.equals("CANCELLED")) {
            return get(companyId, type, id);
        }
        if (st.equals("POSTED")) {
            throw new RuntimeException("posted document must be reversed");
        }
        if (st.equals("CLOSED")) {
            throw new RuntimeException("closed document cannot be cancelled");
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(AsyncClockNative.wallMillis()));
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        ErpDb.exec("UPDATE mk_docs SET status='CANCELLED', updated_at=? WHERE id=? AND company_id=? AND status IN ('DRAFT','OPEN')", p);
        return get(companyId, type, id);
    }

    /** Open (not yet copied) quantity per line of a base document, for the copy-from picker. */
    static String openLines(long companyId, String baseType, long baseId) {
        String sql = "";
        if (baseType.equals("DL")) {
            sql = "SELECT dl.id AS line_id, dl.item_id, dl.quantity FROM delivery_lines dl WHERE dl.delivery_id=? AND dl.company_id=? ORDER BY dl.id";
        } else if (baseType.equals("SO")) {
            sql = "SELECT id AS line_id, item_id, quantity FROM sales_order_lines WHERE sales_order_id=? AND company_id=? ORDER BY id";
        } else if (baseType.equals("GR")) {
            sql = "SELECT id AS line_id, item_id, quantity FROM goods_receipt_lines WHERE goods_receipt_id=? AND company_id=? ORDER BY id";
        } else if (baseType.equals("PO")) {
            sql = "SELECT id AS line_id, item_id, quantity FROM purchase_order_lines WHERE purchase_order_id=? AND company_id=? ORDER BY id";
        } else if (baseType.equals(ARINV) || baseType.equals(APINV)) {
            sql = "SELECT id AS line_id, item_id, quantity FROM mk_doc_lines WHERE doc_id=? AND company_id=? ORDER BY id";
        } else {
            throw new RuntimeException("unsupported base type");
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(baseId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.query(sql, p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            long lineId = r.rows.get(i).getLong(0);
            long qty = r.rows.get(i).getLong(2);
            String bt2 = baseType;
            long used = 0L;
            if (baseType.equals("DL") || baseType.equals("SO") || baseType.equals("GR") || baseType.equals("PO")
                    || baseType.equals(ARINV) || baseType.equals(APINV)) {
                used = consumedQty(companyId, bt2, lineId, 0L);
            }
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"baseType\":" + ErpJson.str(baseType) + ",\"baseId\":" + ErpB1Util.lng(baseId)
                    + ",\"baseLineId\":" + ErpB1Util.lng(lineId) + ",\"itemId\":" + ErpB1Util.lng(r.rows.get(i).getLong(1))
                    + ",\"quantity\":" + ErpB1Util.lng(qty) + ",\"usedQuantity\":" + ErpB1Util.lng(used)
                    + ",\"openQuantity\":" + ErpB1Util.lng(qty - used) + "}";
            i = i + 1;
        }
        return out + "]";
    }
}

class B1DocSaveWork implements DbTransactionWork {
    long companyId;
    long userId;
    B1Doc doc;
    long updateId;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        B1Doc d = doc;
        long id = updateId;
        if (id <= 0L && d.idempotencyKey.length() > 0) {
            ArrayList<Object> ip = ErpB1Util.params();
            ip.add(ErpB1Util.lng(companyId));
            ip.add(d.idempotencyKey);
            Object ex = ErpDb.queryOneConn(conn, "SELECT id FROM mk_docs WHERE company_id=? AND idempotency_key=?", ip, new LongMapper());
            if (ex != null) {
                return done(ErpNum.asLong(ex));
            }
        }
        if (id > 0L) {
            ArrayList<Object> sp = ErpB1Util.params();
            sp.add(ErpB1Util.lng(id));
            sp.add(ErpB1Util.lng(companyId));
            sp.add(d.docType);
            Object st = ErpDb.queryOneConn(conn, "SELECT status FROM mk_docs WHERE id=? AND company_id=? AND doc_type=?", sp, new StringMapper());
            if (st == null) {
                throw new TransactionException("not found");
            }
            if (((String) st).equals("DRAFT") == false) {
                throw new TransactionException("only DRAFT documents can be edited (status=" + (String) st + ")");
            }
            ArrayList<Object> up = ErpB1Util.params();
            up.add(ErpB1Util.lng(d.partnerId));
            up.add(d.partnerName);
            up.add(ErpB1Util.lng(d.docDate));
            up.add(ErpB1Util.lng(d.dueDate));
            up.add(ErpB1Util.lng(d.validUntil));
            up.add(d.currency);
            up.add(ErpB1Util.lng(d.rateMicro));
            up.add(ErpB1Util.lng(d.paymentTermsId));
            up.add(ErpB1Util.lng(d.priceListId));
            up.add(ErpB1Util.lng(d.warehouseId));
            up.add(d.shipTo);
            up.add(d.billTo);
            up.add(d.refNo);
            up.add(ErpB1Util.lng(d.discountBps));
            up.add(ErpB1Util.lng(d.subtotalCents));
            up.add(ErpB1Util.lng(d.taxCents));
            up.add(ErpB1Util.lng(d.totalCents));
            up.add(ErpB1Util.lng(d.totalLocalCents));
            up.add(d.baseType);
            up.add(ErpB1Util.lng(d.baseId));
            up.add(d.notes);
            up.add(ErpB1Util.lng(now));
            up.add(ErpB1Util.lng(id));
            up.add(ErpB1Util.lng(companyId));
            ErpDb.execConn(conn, "UPDATE mk_docs SET partner_id=?, partner_name=?, doc_date=?, due_date=?, valid_until=?, currency=?, rate_micro=?, payment_terms_id=?, price_list_id=?, warehouse_id=?, ship_to=?, bill_to=?, ref_no=?, discount_bps=?, subtotal_cents=?, tax_cents=?, total_cents=?, total_local_cents=?, base_type=?, base_id=?, notes=?, updated_at=? WHERE id=? AND company_id=?", up);
            ArrayList<Object> dp = ErpB1Util.params();
            dp.add(ErpB1Util.lng(id));
            dp.add(ErpB1Util.lng(companyId));
            ErpDb.execConn(conn, "DELETE FROM mk_doc_lines WHERE doc_id=? AND company_id=?", dp);
        } else {
            long seq = ErpDb.nextDocNoConn(conn, companyId, "MK_" + d.docType);
            d.docNo = ErpB1Docs.prefix(d.docType) + ErpB1Util.lng(seq);
            ArrayList<Object> hp = ErpB1Util.params();
            hp.add(ErpB1Util.lng(companyId));
            hp.add(d.docType);
            hp.add(d.docNo);
            hp.add(ErpB1Util.lng(d.partnerId));
            hp.add(d.partnerName);
            hp.add(ErpB1Util.lng(d.docDate));
            hp.add(ErpB1Util.lng(d.dueDate));
            hp.add(ErpB1Util.lng(d.validUntil));
            hp.add(d.currency);
            hp.add(ErpB1Util.lng(d.rateMicro));
            hp.add(ErpB1Util.lng(d.paymentTermsId));
            hp.add(ErpB1Util.lng(d.priceListId));
            hp.add(ErpB1Util.lng(d.warehouseId));
            hp.add(d.shipTo);
            hp.add(d.billTo);
            hp.add(d.refNo);
            hp.add(ErpB1Util.lng(d.discountBps));
            hp.add(ErpB1Util.lng(d.subtotalCents));
            hp.add(ErpB1Util.lng(d.taxCents));
            hp.add(ErpB1Util.lng(d.totalCents));
            hp.add(ErpB1Util.lng(d.totalLocalCents));
            hp.add(d.baseType);
            hp.add(ErpB1Util.lng(d.baseId));
            hp.add(d.notes);
            hp.add(d.idempotencyKey);
            hp.add(ErpB1Util.lng(userId));
            hp.add(ErpB1Util.lng(now));
            hp.add(ErpB1Util.lng(now));
            id = ErpDb.insertReturningIdConn(conn, "INSERT INTO mk_docs(company_id, doc_type, doc_no, partner_id, partner_name, status, doc_date, due_date, valid_until, currency, rate_micro, payment_terms_id, price_list_id, warehouse_id, ship_to, bill_to, ref_no, discount_bps, subtotal_cents, tax_cents, total_cents, total_local_cents, base_type, base_id, notes, idempotency_key, created_by, created_at, updated_at) VALUES (?,?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", hp);
        }
        int i = 0;
        while (i < d.lines.size()) {
            B1Line l = (B1Line) d.lines.get(i);
            ArrayList<Object> lp = ErpB1Util.params();
            lp.add(ErpB1Util.lng(companyId));
            lp.add(ErpB1Util.lng(id));
            lp.add(String.valueOf(i + 1));
            lp.add(ErpB1Util.lng(l.itemId));
            lp.add(l.description);
            lp.add(ErpB1Util.lng(l.warehouseId));
            lp.add(ErpB1Util.lng(l.quantity));
            lp.add(l.uomCode);
            lp.add(ErpB1Util.lng(l.unitPriceCents));
            lp.add(ErpB1Util.lng(l.discountBps));
            lp.add(ErpB1Util.lng(l.taxCodeId));
            lp.add(ErpB1Util.lng(l.taxBps));
            lp.add(ErpB1Util.lng(l.netCents));
            lp.add(ErpB1Util.lng(l.taxCents));
            lp.add(ErpB1Util.lng(l.totalCents));
            lp.add(ErpB1Util.lng(l.isInventory));
            lp.add(l.baseType);
            lp.add(ErpB1Util.lng(l.baseId));
            lp.add(ErpB1Util.lng(l.baseLineId));
            ErpDb.execConn(conn, "INSERT INTO mk_doc_lines(company_id, doc_id, line_no, item_id, description, warehouse_id, quantity, uom_code, unit_price_cents, discount_bps, tax_code_id, tax_bps, net_cents, tax_cents, total_cents, is_inventory, base_type, base_id, base_line_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", lp);
            i = i + 1;
        }
        return done(id);
    }

    JnFuture done(long id) {
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "saved";
        ok.documentJson = "{\"id\":" + ErpB1Util.lng(id) + "}";
        return JnFuture.completed(Erp.loop, ok);
    }
}
