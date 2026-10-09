/**
 * Safe report datasets — predefined queries only, company + permission gated.
 * @author 张晓达
 */
class ErpPlatformReports {
    static long ensureReport(long companyId, String code, String name, String dataset) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object existing = ErpDb.queryOne(
                "SELECT id FROM report_definitions WHERE company_id=? AND report_code=?", p, new LongMapper());
        if (existing != null) {
            return ErpNum.asLong(existing);
        }
        ErpDb.exec(
                "INSERT INTO report_definitions(company_id, report_code, name, dataset_code, active) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'" + code + "','"
                        + ErpPlatformMeta.escapeLite(name) + "','" + dataset + "',1)",
                new ArrayList<Object>());
        Object id = ErpDb.queryOne(
                "SELECT id FROM report_definitions WHERE company_id=? AND report_code=?", p, new LongMapper());
        return ErpNum.asLong(id);
    }

    static ArrayList<Object> runDataset(long companyId, long userId, String datasetCode, int limit) {
        if (ErpPlatformAuth.hasPermission(userId, companyId, "ADMIN.REPORT.RUN") == 0) {
            throw new TransactionException("FORBIDDEN:ADMIN.REPORT.RUN");
        }
        if (limit <= 0 || limit > 10000) {
            limit = 100;
        }
        ArrayList<Object> out = new ArrayList<Object>();
        if (datasetCode.equals("InventoryBalanceDataset")) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT warehouse_id, item_id, quantity_on_hand, value_amount FROM inventory_balances WHERE company_id=? ORDER BY warehouse_id, item_id LIMIT "
                            + CryptoParse.longToString(limit),
                    p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                out.add("{\"warehouseId\":" + ErpJson.num(row.getLong(0))
                        + ",\"itemId\":" + ErpJson.num(row.getLong(1))
                        + ",\"qty\":" + String.valueOf(row.getInt(2))
                        + ",\"value\":" + ErpJson.num(row.getLong(3)) + "}");
                i = i + 1;
            }
            return out;
        }
        if (datasetCode.equals("SalesOrderDataset")) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT id, doc_no, status, customer_id FROM sales_orders WHERE company_id=? ORDER BY id LIMIT "
                            + CryptoParse.longToString(limit),
                    p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                out.add("{\"id\":" + ErpJson.num(row.getLong(0))
                        + ",\"docNo\":" + ErpJson.str(row.getString(1))
                        + ",\"status\":" + ErpJson.str(row.getString(2))
                        + ",\"customerId\":" + ErpJson.num(row.getLong(3)) + "}");
                i = i + 1;
            }
            return out;
        }
        if (datasetCode.equals("PurchaseOrderDataset")) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT id, doc_no, status, supplier_id FROM purchase_orders WHERE company_id=? ORDER BY id LIMIT "
                            + CryptoParse.longToString(limit),
                    p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                out.add("{\"id\":" + ErpJson.num(row.getLong(0))
                        + ",\"docNo\":" + ErpJson.str(row.getString(1))
                        + ",\"status\":" + ErpJson.str(row.getString(2))
                        + ",\"supplierId\":" + ErpJson.num(row.getLong(3)) + "}");
                i = i + 1;
            }
            return out;
        }
        if (datasetCode.equals("CustomerBalanceDataset")) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT customer_id, SUM(open_amount) FROM ar_invoices WHERE company_id=? GROUP BY customer_id LIMIT "
                            + CryptoParse.longToString(limit),
                    p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                out.add("{\"customerId\":" + ErpJson.num(row.getLong(0))
                        + ",\"openAmount\":" + ErpJson.num(row.getLong(1)) + "}");
                i = i + 1;
            }
            return out;
        }
        throw new TransactionException("unknown dataset");
    }

    static void recordExecution(long companyId, long reportId, long userId, int rows) {
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO report_executions(company_id, report_id, executed_by, row_count, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(reportId) + ","
                        + CryptoParse.longToString(userId) + ","
                        + CryptoParse.longToString(rows) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
    }
}
