/**
 * Manufacturing 1.3: SmartDevice-A BOM explode + issue + partial receipts 60+40.
 * @author 张晓达
 */
class ErpManufacturingLogicTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long fgId = 0L;
    static long motorId = 0L;
    static long sensorId = 0L;
    static long controllerId = 0L;
    static long caseId = 0L;
    static String companyCode = "M";

    static int runAll() {
        companyCode = "M" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        seedMinimal();
        if (companyId == 0L || fgId == 0L) {
            System.out.println("FAIL mfg seed");
            return 0;
        }
        int pass = 1;
        pass = runCore(pass);
        pass = runExceptions(pass);
        if (pass != 0) {
            System.out.println("PASS manufacturing");
        } else {
            System.out.println("FAIL manufacturing");
        }
        return pass;
    }

    static void seedMinimal() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Mfg Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));

        ErpDb.exec(
                "INSERT INTO warehouses(company_id, code, name, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'WH1','Main',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH1");
        warehouseId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper()));

        fgId = insertItem("SD-A", "SmartDevice-A", 0L);
        motorId = insertItem("MOTOR", "Motor", 10L);
        sensorId = insertItem("SENSOR", "Sensor", 5L);
        controllerId = insertItem("CTRL", "Controller", 20L);
        caseId = insertItem("CASE", "Case", 8L);

        String[] seq = new String[6];
        seq[0] = "PO";
        seq[1] = "SO";
        seq[2] = "GR";
        seq[3] = "DL";
        seq[4] = "MO";
        seq[5] = "PI";
        int si = 0;
        while (si < 6) {
            ErpDb.exec(
                    "INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES ("
                            + CryptoParse.longToString(companyId) + ",'" + seq[si] + "',1)",
                    new ArrayList<Object>());
            si = si + 1;
        }
        ErpDb.exec(
                "INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'PR',1)",
                new ArrayList<Object>());
        ErpFinanceCore.ensureSeed(companyId);

        /* Stock components enough for 100 devices */
        stock(motorId, 200);
        stock(sensorId, 400);
        stock(controllerId, 100);
        stock(caseId, 100);
    }

    static long insertItem(String sku, String name, long unitCost) {
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'" + sku + "','" + name + "',0,"
                        + CryptoParse.longToString(unitCost) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sku);
        return ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku=?", p, new LongMapper()));
    }

    static void stock(long itemId, int qty) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> empty = new ArrayList<Object>();
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(itemId));
        cp.add(CryptoParse.longToString(companyId));
        Object c = ErpDb.queryOne("SELECT unit_cost_amount FROM items WHERE id=? AND company_id=?", cp,
                new LongMapper());
        long cost = ErpNum.asLong(c);
        long qtyL = 0L + qty;
        long value = cost * qtyL;
        ErpDb.exec(
                "INSERT INTO inventory_balances(company_id, warehouse_id, item_id, quantity_on_hand, value_amount) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(warehouseId) + ","
                        + CryptoParse.longToString(itemId) + ","
                        + CryptoParse.longToString(qty) + ","
                        + CryptoParse.longToString(value) + ")",
                empty);
        ErpDb.exec(
                "INSERT INTO inventory_transactions(company_id, warehouse_id, item_id, txn_type, quantity_delta, unit_cost_amount, value_delta_amount, ref_doc_type, ref_doc_id, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(warehouseId) + ","
                        + CryptoParse.longToString(itemId) + ",'ADJUSTMENT',"
                        + CryptoParse.longToString(qty) + ","
                        + CryptoParse.longToString(cost) + ","
                        + CryptoParse.longToString(value) + ",'SEED',0,"
                        + CryptoParse.longToString(now) + ")",
                empty);
    }

    static int qtyOf(long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int runCore(int pass) {
        System.out.println("MFG_DIALECT " + Erp.dialect);
        BomDto bom = ErpMfgBom.createBom(companyId, fgId, "BOM-SDA", 1);
        System.out.println("BOM_CREATED");
        try {
            ErpDb.reconnect();
            ErpMfgBom.addLine(companyId, bom.id, motorId, 2, 0, warehouseId, 10);
            ErpDb.reconnect();
            ErpMfgBom.addLine(companyId, bom.id, sensorId, 4, 0, warehouseId, 20);
            ErpDb.reconnect();
            ErpMfgBom.addLine(companyId, bom.id, controllerId, 1, 0, warehouseId, 30);
            ErpDb.reconnect();
            ErpMfgBom.addLine(companyId, bom.id, caseId, 1, 0, warehouseId, 40);
        } catch (Exception lineEx) {
            System.out.println("FAIL addLine");
            if (lineEx.getMessage() != null) {
                System.out.println(lineEx.getMessage());
            }
            return 0;
        }
        System.out.println("BOM_LINES");
        ErpMfgBom.activate(companyId, bom.id);
        System.out.println("BOM_ACTIVE");
        ErpDb.reconnect();

        ArrayList<Object> exp = ErpMfgBom.explode(companyId, fgId, 100, AsyncClockNative.wallMillis());
        System.out.println("BOM_EXPLODED");
        int motorReq = 0;
        int sensorReq = 0;
        int controllerReq = 0;
        int caseReq = 0;
        int ei = 0;
        while (ei < exp.size()) {
            BomExplosionLine ln = (BomExplosionLine) exp.get(ei);
            if (ln.componentItemId == motorId) {
                motorReq = ln.requiredQuantity;
            }
            if (ln.componentItemId == sensorId) {
                sensorReq = ln.requiredQuantity;
            }
            if (ln.componentItemId == controllerId) {
                controllerReq = ln.requiredQuantity;
            }
            if (ln.componentItemId == caseId) {
                caseReq = ln.requiredQuantity;
            }
            ei = ei + 1;
        }
        if (motorReq != 200 || sensorReq != 400 || controllerReq != 100 || caseReq != 100) {
            System.out.println("FAIL BOM explode reqs");
            System.out.println("motor=" + CryptoParse.longToString(motorReq)
                    + " sensor=" + CryptoParse.longToString(sensorReq)
                    + " ctrl=" + CryptoParse.longToString(controllerReq)
                    + " case=" + CryptoParse.longToString(caseReq));
            return 0;
        }
        System.out.println("BOM_EXPLODE_OK");

        ProductionOrderDto mo = ErpMfgProduction.createPlanned(companyId, fgId, warehouseId, 100, bom.id);
        if (mo == null) {
            System.out.println("FAIL MO create");
            return 0;
        }
        mo = ErpMfgProduction.release(companyId, mo.id);
        if (mo.components == null || mo.components.size() != 4) {
            System.out.println("FAIL MO components");
            return 0;
        }

        ProductionIssueRequest issue = new ProductionIssueRequest();
        issue.productionOrderId = mo.id;
        issue.warehouseId = warehouseId;
        issue.postingDate = AsyncClockNative.wallMillis();
        issue.idempotencyKey = "mfg-issue-1";
        issue.lines = new ArrayList<Object>();
        addIssueLine(issue, motorId, 200);
        addIssueLine(issue, sensorId, 400);
        addIssueLine(issue, controllerId, 100);
        addIssueLine(issue, caseId, 100);
        PostResultDto ir = ErpMfgProduction.postIssue(companyId, 0L, issue);
        if (ir == null || ir.ok == 0) {
            System.out.println("FAIL issue");
            if (ir != null) {
                System.out.println(ir.message);
            }
            return 0;
        }
        PostResultDto ir2 = ErpMfgProduction.postIssue(companyId, 0L, issue);
        if (ir2 == null || ir2.ok == 0) {
            System.out.println("FAIL issue idempotent");
            return 0;
        }
        if (qtyOf(motorId) != 0 || qtyOf(sensorId) != 0 || qtyOf(controllerId) != 0 || qtyOf(caseId) != 0) {
            System.out.println("FAIL component stock not zero");
            return 0;
        }
        WipDto wip1 = ErpMfgProduction.getWip(companyId, mo.id);
        if (wip1 == null || wip1.materialCost <= 0L) {
            System.out.println("FAIL WIP material");
            return 0;
        }
        System.out.println("ISSUE_OK wipMat=" + CryptoParse.longToString(wip1.materialCost));

        ProductionReceiptRequest r1 = new ProductionReceiptRequest();
        r1.productionOrderId = mo.id;
        r1.warehouseId = warehouseId;
        r1.quantity = 60;
        r1.postingDate = AsyncClockNative.wallMillis();
        r1.idempotencyKey = "mfg-rcpt-1";
        ErpDb.reconnect();
        PostResultDto rr1 = ErpMfgProduction.postReceipt(companyId, 0L, r1);
        if (rr1 == null || rr1.ok == 0) {
            System.out.println("FAIL receipt 60");
            if (rr1 != null) {
                System.out.println(rr1.code);
                System.out.println(rr1.message);
            }
            return 0;
        }
        System.out.println("RCPT60_OK");
        ProductionReceiptRequest r2 = new ProductionReceiptRequest();
        r2.productionOrderId = mo.id;
        r2.warehouseId = warehouseId;
        r2.quantity = 40;
        r2.postingDate = AsyncClockNative.wallMillis();
        r2.idempotencyKey = "mfg-rcpt-2";
        ErpDb.reconnect();
        PostResultDto rr2 = ErpMfgProduction.postReceipt(companyId, 0L, r2);
        if (rr2 == null || rr2.ok == 0) {
            System.out.println("FAIL receipt 40");
            if (rr2 != null) {
                System.out.println(rr2.code);
                System.out.println(rr2.message);
            }
            return 0;
        }
        /* over-receipt reject */
        ProductionReceiptRequest r3 = new ProductionReceiptRequest();
        r3.productionOrderId = mo.id;
        r3.warehouseId = warehouseId;
        r3.quantity = 1;
        r3.postingDate = AsyncClockNative.wallMillis();
        r3.idempotencyKey = "mfg-rcpt-over";
        PostResultDto over = ErpMfgProduction.postReceipt(companyId, 0L, r3);
        if (over != null && over.ok != 0) {
            System.out.println("FAIL expected over-receipt reject");
            pass = 0;
        }

        if (qtyOf(fgId) != 100) {
            System.out.println("FAIL FG qty expected 100 got " + CryptoParse.longToString(qtyOf(fgId)));
            pass = 0;
        }
        ProductionOrderDto done = ErpMfgProduction.getOrder(companyId, mo.id);
        if (done.completedQuantity != 100
                || done.status.equals(ProductionOrderStatus.COMPLETED) == false) {
            System.out.println("FAIL MO not completed");
            pass = 0;
        }
        WipDto wip2 = ErpMfgProduction.getWip(companyId, mo.id);
        if (wip2 == null || wip2.remainingCost != 0L) {
            System.out.println("FAIL WIP remaining expected 0");
            if (wip2 != null) {
                System.out.println(CryptoParse.longToString(wip2.remainingCost));
            }
            pass = 0;
        }

        MrpRunRequest mrp = new MrpRunRequest();
        mrp.runCode = "MRP-" + companyCode;
        mrp.planningStartDate = AsyncClockNative.wallMillis();
        mrp.planningEndDate = mrp.planningStartDate + 86400000L * 2L;
        long runId = ErpMfgMrp.runMrp(companyId, mrp);
        long runId2 = ErpMfgMrp.runMrp(companyId, mrp);
        if (runId == 0L || runId != runId2) {
            System.out.println("FAIL MRP idempotent run");
            pass = 0;
        }
        System.out.println("MFG_CORE fg=" + CryptoParse.longToString(qtyOf(fgId))
                + " wipRem=" + CryptoParse.longToString(wip2.remainingCost)
                + " mrp=" + CryptoParse.longToString(runId));
        return pass;
    }

    static void addIssueLine(ProductionIssueRequest issue, long itemId, int qty) {
        ProductionIssueLineInput ln = new ProductionIssueLineInput();
        ln.componentItemId = itemId;
        ln.quantity = qty;
        issue.lines.add(ln);
    }

    static int runExceptions(int pass) {
        try {
            BomDto bom = ErpMfgBom.createBom(companyId, motorId, "BOM-CYCLE", 1);
            ErpMfgBom.addLine(companyId, bom.id, fgId, 1, 0, warehouseId, 10);
            /* activate FG bom already points to motor; adding motor->fg creates cycle on explode */
            ErpMfgBom.activate(companyId, bom.id);
            try {
                ErpMfgBom.explode(companyId, fgId, 1, AsyncClockNative.wallMillis());
                System.out.println("FAIL expected cycle explode");
                pass = 0;
            } catch (Exception ex) {
                System.out.println("CYCLE_OK");
            }
        } catch (Exception ex2) {
            System.out.println("CYCLE_CREATE_OK");
        }
        return pass;
    }
}
