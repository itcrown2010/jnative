/**
 * Daily-bucket MRP (gross / scheduled / projected / net / planned receipt & release).
 * Suggestions are advisory — not auto POs/MOs.
 */
class ErpMfgMrp {
    static long DAY_MS = 86400000L;

    static long runMrp(long companyId, MrpRunRequest req) {
        if (req == null || req.runCode == null || req.runCode.length() == 0) {
            throw new TransactionException("runCode required");
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add(req.runCode);
        Object existing = ErpDb.queryOne(
                "SELECT id FROM mrp_runs WHERE company_id=? AND run_code=?", chk, new LongMapper());
        if (existing != null) {
            return ErpNum.asLong(existing);
        }
        long now = AsyncClockNative.wallMillis();
        long start = req.planningStartDate;
        long end = req.planningEndDate;
        if (end < start) {
            throw new TransactionException("invalid planning window");
        }
        String ins = "INSERT INTO mrp_runs(company_id, run_code, planning_start_date, planning_end_date, status, created_at) VALUES ("
                + CryptoParse.longToString(companyId) + ",'"
                + req.runCode + "',"
                + CryptoParse.longToString(start) + ","
                + CryptoParse.longToString(end) + ",'DONE',"
                + CryptoParse.longToString(now) + ")";
        ErpDb.exec(ins, new ArrayList<Object>());
        Object runIdObj = ErpDb.queryOne(
                "SELECT id FROM mrp_runs WHERE company_id=? AND run_code=?", chk, new LongMapper());
        long runId = ErpNum.asLong(runIdObj);

        ArrayList<Object> items = loadPlanItems(companyId);
        int ii = 0;
        while (ii < items.size()) {
            long itemId = ErpNum.asLong(items.get(ii));
            planItem(companyId, runId, itemId, start, end);
            ii = ii + 1;
        }
        return runId;
    }

    static ArrayList<Object> loadPlanItems(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query("SELECT id FROM items WHERE company_id=? ORDER BY id", p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            out.add(CryptoParse.longToString(r.rows.get(i).getLong(0)));
            i = i + 1;
        }
        return out;
    }

    static int onHand(long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT SUM(quantity_on_hand) FROM inventory_balances WHERE company_id=? AND item_id=?",
                p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int openPoQty(long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT SUM(l.quantity - l.received_qty) FROM purchase_order_lines l JOIN purchase_orders o ON o.id=l.purchase_order_id WHERE l.company_id=? AND l.item_id=? AND o.status IN ('APPROVED','PARTIALLY_RECEIVED')",
                p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int openSoQty(long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT SUM(l.quantity - l.delivered_qty) FROM sales_order_lines l JOIN sales_orders o ON o.id=l.sales_order_id WHERE l.company_id=? AND l.item_id=? AND o.status IN ('APPROVED','PARTIALLY_DELIVERED')",
                p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int openMoDemand(long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT SUM(required_quantity - issued_quantity + returned_quantity) FROM production_order_components c JOIN production_orders o ON o.id=c.production_order_id WHERE c.company_id=? AND c.component_item_id=? AND o.status IN ('RELEASED','IN_PROGRESS','PARTIALLY_COMPLETED')",
                p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int openMoSupply(long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT SUM(planned_quantity - completed_quantity) FROM production_orders WHERE company_id=? AND item_id=? AND status IN ('RELEASED','IN_PROGRESS','PARTIALLY_COMPLETED','PLANNED')",
                p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int hasBom(long companyId, long itemId) {
        BomDto b = ErpMfgBom.findActiveBom(companyId, itemId, AsyncClockNative.wallMillis());
        if (b == null) {
            return 0;
        }
        return 1;
    }

    static void planItem(long companyId, long runId, long itemId, long start, long end) {
        int oh = onHand(companyId, itemId);
        int sched = openPoQty(companyId, itemId) + openMoSupply(companyId, itemId);
        int grossTotal = openSoQty(companyId, itemId) + openMoDemand(companyId, itemId);
        long day = start;
        int projected = oh;
        int remainingGross = grossTotal;
        int remainingSched = sched;
        while (day <= end) {
            int dayGross = 0;
            int daySched = 0;
            if (day == start) {
                dayGross = remainingGross;
                daySched = remainingSched;
                remainingGross = 0;
                remainingSched = 0;
            }
            int before = projected + daySched - dayGross;
            int net = 0;
            if (before < 0) {
                net = 0 - before;
            }
            int plannedReceipt = net;
            int plannedRelease = plannedReceipt;
            projected = before + plannedReceipt;
            String rins = "INSERT INTO mrp_requirements(company_id, mrp_run_id, item_id, bucket_date, gross_requirement, scheduled_receipts, projected_available, net_requirement, planned_order_receipt, planned_order_release) VALUES ("
                    + CryptoParse.longToString(companyId) + ","
                    + CryptoParse.longToString(runId) + ","
                    + CryptoParse.longToString(itemId) + ","
                    + CryptoParse.longToString(day) + ","
                    + CryptoParse.longToString(dayGross) + ","
                    + CryptoParse.longToString(daySched) + ","
                    + CryptoParse.longToString(projected) + ","
                    + CryptoParse.longToString(net) + ","
                    + CryptoParse.longToString(plannedReceipt) + ","
                    + CryptoParse.longToString(plannedRelease) + ")";
            ErpDb.exec(rins, new ArrayList<Object>());
            if (plannedReceipt > 0) {
                String kind = MrpSuggestionKind.PURCHASE;
                if (hasBom(companyId, itemId) != 0) {
                    kind = MrpSuggestionKind.PRODUCTION;
                }
                String sins = "INSERT INTO mrp_suggestions(company_id, mrp_run_id, suggestion_kind, item_id, required_quantity, required_date, suggested_order_date, supplier_id, status, converted_doc_id) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(runId) + ",'"
                        + kind + "',"
                        + CryptoParse.longToString(itemId) + ","
                        + CryptoParse.longToString(plannedReceipt) + ","
                        + CryptoParse.longToString(day) + ","
                        + CryptoParse.longToString(day) + ",0,'OPEN',0)";
                ErpDb.exec(sins, new ArrayList<Object>());
            }
            day = day + DAY_MS;
        }
    }

    static ArrayList<Object> listSuggestions(long companyId, long runId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(runId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, mrp_run_id, suggestion_kind, item_id, required_quantity, required_date, suggested_order_date, status FROM mrp_suggestions WHERE company_id=? AND mrp_run_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            MrpSuggestionDto d = new MrpSuggestionDto();
            d.id = row.getLong(0);
            d.mrpRunId = row.getLong(1);
            d.suggestionKind = row.getString(2);
            d.itemId = row.getLong(3);
            d.requiredQuantity = row.getInt(4);
            d.requiredDate = row.getLong(5);
            d.suggestedOrderDate = row.getLong(6);
            d.status = row.getString(7);
            out.add(d);
            i = i + 1;
        }
        return out;
    }
}
