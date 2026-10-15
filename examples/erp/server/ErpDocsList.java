/**
 * List helpers for goods receipts, deliveries, inventory transactions, payments.
 * @author 张晓达
 */
class ErpDocsList {
    static String goodsReceiptsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT g.id, g.doc_no, g.purchase_order_id, g.warehouse_id, g.status, g.created_at, "
                        + "(SELECT COALESCE(SUM(l.quantity),0) FROM goods_receipt_lines l WHERE l.goods_receipt_id=g.id), "
                        + "(SELECT o.doc_no FROM purchase_orders o WHERE o.id=g.purchase_order_id), "
                        + "(SELECT bp.name FROM purchase_orders o JOIN business_partners bp ON bp.id=o.supplier_id WHERE o.id=g.purchase_order_id) "
                        + "FROM goods_receipts g WHERE g.company_id=? ORDER BY g.id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"docNo\":" + ErpJson.str(row.getString(1))
                    + ",\"purchaseOrderId\":" + ErpJson.num(row.getLong(2))
                    + ",\"warehouseId\":" + ErpJson.num(row.getLong(3))
                    + ",\"status\":" + ErpJson.str(row.getString(4))
                    + ",\"date\":" + ErpJson.num(row.getLong(5))
                    + ",\"qty\":" + String.valueOf(row.getInt(6))
                    + ",\"totalQty\":" + String.valueOf(row.getInt(6))
                    + ",\"poDocNo\":" + ErpJson.str(row.getString(7))
                    + ",\"supplierName\":" + ErpJson.str(row.getString(8)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String deliveriesJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT d.id, d.doc_no, d.sales_order_id, d.warehouse_id, d.status, d.created_at, "
                        + "(SELECT COALESCE(SUM(l.quantity),0) FROM delivery_lines l WHERE l.delivery_id=d.id), "
                        + "(SELECT o.doc_no FROM sales_orders o WHERE o.id=d.sales_order_id), "
                        + "(SELECT bp.name FROM sales_orders o JOIN business_partners bp ON bp.id=o.customer_id WHERE o.id=d.sales_order_id) "
                        + "FROM deliveries d WHERE d.company_id=? ORDER BY d.id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"docNo\":" + ErpJson.str(row.getString(1))
                    + ",\"salesOrderId\":" + ErpJson.num(row.getLong(2))
                    + ",\"warehouseId\":" + ErpJson.num(row.getLong(3))
                    + ",\"status\":" + ErpJson.str(row.getString(4))
                    + ",\"date\":" + ErpJson.num(row.getLong(5))
                    + ",\"qty\":" + String.valueOf(row.getInt(6))
                    + ",\"totalQty\":" + String.valueOf(row.getInt(6))
                    + ",\"soDocNo\":" + ErpJson.str(row.getString(7))
                    + ",\"customerName\":" + ErpJson.str(row.getString(8)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String inventoryTxnsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT t.id, t.warehouse_id, t.item_id, t.txn_type, t.quantity_delta, t.ref_doc_type, t.ref_doc_id, t.created_at, "
                        + "(SELECT w.code FROM warehouses w WHERE w.id=t.warehouse_id), "
                        + "(SELECT it.sku FROM items it WHERE it.id=t.item_id) "
                        + "FROM inventory_transactions t WHERE t.company_id=? ORDER BY t.id DESC LIMIT 500",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            String refType = row.getString(5);
            if (refType == null) {
                refType = "";
            }
            String refNo = refType + "-" + CryptoParse.longToString(row.getLong(6));
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"warehouseId\":" + ErpJson.num(row.getLong(1))
                    + ",\"itemId\":" + ErpJson.num(row.getLong(2))
                    + ",\"txnType\":" + ErpJson.str(row.getString(3))
                    + ",\"type\":" + ErpJson.str(row.getString(3))
                    + ",\"quantity\":" + String.valueOf(row.getInt(4))
                    + ",\"refDocType\":" + ErpJson.str(refType)
                    + ",\"refDocNo\":" + ErpJson.str(refNo)
                    + ",\"createdAt\":" + ErpJson.num(row.getLong(7))
                    + ",\"warehouseCode\":" + ErpJson.str(row.getString(8))
                    + ",\"itemSku\":" + ErpJson.str(row.getString(9)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String incomingPaymentsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT ip.id, ip.customer_id, ip.payment_date, ip.amount, ip.status, "
                        + "(SELECT bp.name FROM business_partners bp WHERE bp.id=ip.customer_id), "
                        + "ip.unallocated_amount, ip.doc_no "
                        + "FROM incoming_payments ip WHERE ip.company_id=? ORDER BY ip.id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            long unalloc = 0L;
            String docNo = "IP-" + CryptoParse.longToString(row.getLong(0));
            if (row.columnCount() > 6) {
                unalloc = row.getLong(6);
                if (row.getString(7) != null && row.getString(7).length() > 0) {
                    docNo = row.getString(7);
                }
            }
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"customerId\":" + ErpJson.num(row.getLong(1))
                    + ",\"paymentDate\":" + ErpJson.num(row.getLong(2))
                    + ",\"date\":" + ErpJson.num(row.getLong(2))
                    + ",\"amount\":" + ErpJson.num(row.getLong(3))
                    + ",\"status\":" + ErpJson.str(row.getString(4))
                    + ",\"unallocatedAmount\":" + ErpJson.num(unalloc)
                    + ",\"docNo\":" + ErpJson.str(docNo)
                    + ",\"customerName\":" + ErpJson.str(row.getString(5)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String outgoingPaymentsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT op.id, op.supplier_id, op.payment_date, op.amount, op.status, "
                        + "(SELECT bp.name FROM business_partners bp WHERE bp.id=op.supplier_id), "
                        + "op.unallocated_amount, op.doc_no "
                        + "FROM outgoing_payments op WHERE op.company_id=? ORDER BY op.id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            long unalloc = 0L;
            String docNo = "OP-" + CryptoParse.longToString(row.getLong(0));
            if (row.columnCount() > 6) {
                unalloc = row.getLong(6);
                if (row.getString(7) != null && row.getString(7).length() > 0) {
                    docNo = row.getString(7);
                }
            }
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"supplierId\":" + ErpJson.num(row.getLong(1))
                    + ",\"paymentDate\":" + ErpJson.num(row.getLong(2))
                    + ",\"date\":" + ErpJson.num(row.getLong(2))
                    + ",\"amount\":" + ErpJson.num(row.getLong(3))
                    + ",\"status\":" + ErpJson.str(row.getString(4))
                    + ",\"unallocatedAmount\":" + ErpJson.num(unalloc)
                    + ",\"docNo\":" + ErpJson.str(docNo)
                    + ",\"supplierName\":" + ErpJson.str(row.getString(5)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String periodsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, fiscal_year, period_number, status FROM fiscal_periods WHERE company_id=? ORDER BY fiscal_year, period_number",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            int fy = row.getInt(1);
            int pn = row.getInt(2);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"fiscalYear\":" + String.valueOf(fy)
                    + ",\"periodNumber\":" + String.valueOf(pn)
                    + ",\"code\":" + ErpJson.str(String.valueOf(fy) + "-" + String.valueOf(pn))
                    + ",\"name\":" + ErpJson.str("FY" + String.valueOf(fy) + " P" + String.valueOf(pn))
                    + ",\"status\":" + ErpJson.str(row.getString(3)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static long firstBankAccountId(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT id FROM bank_accounts WHERE company_id=? AND active=1 ORDER BY id LIMIT 1", p,
                new LongMapper());
        return ErpNum.asLong(v);
    }
}
