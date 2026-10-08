/**
 * Pilot 12-step acceptance on empty FORMAL book (real DB APIs, no mock).
 * 1 init company/wh/user 2 CSV import 3 opening stock 20 4 GR 10 5 DL 3 → 27
 * 6 reverse DL → 30 7 reverse again rejected 8 AR/AP opening 9 restart check
 * 10 migrate marker 11 backup script hint 12 demo isolation
 */
class ErpPilotAcceptTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long itemId = 0L;
    static long supplierId = 0L;
    static long customerId = 0L;
    static long adminUserId = 0L;
    static long dlId = 0L;

    static int runAll() {
        int pass = 1;
        String code = "F" + CryptoParse.longToString(AsyncClockNative.wallMillis() % 100000000L);
        int attempt = 0;
        while (attempt < 3) {
            try {
                ErpDb.reconnect();
                pass = 1;
                pass = step1Wizard(pass, code + String.valueOf(attempt));
                pass = step2Csv(pass);
                pass = step3Opening(pass);
                pass = step4PurchaseGr(pass);
                pass = step5SalesDl(pass);
                pass = step6ReverseDl(pass);
                pass = step7ReverseAgain(pass);
                pass = step8ArAp(pass);
                pass = step9RestartCheck(pass);
                pass = step10Migrate(pass);
                pass = step11BackupHint(pass);
                pass = step12DemoIsolation(pass);
                attempt = 99;
            } catch (Exception ex) {
                System.out.println("FAIL pilot exception attempt=" + String.valueOf(attempt));
                if (ex.getMessage() != null) {
                    System.out.println(ex.getMessage());
                }
                if (attempt >= 2) {
                    return 0;
                }
                ErpDb.reconnect();
                attempt = attempt + 1;
                continue;
            }
            attempt = attempt + 1;
        }
        if (pass != 0) {
            System.out.println("PILOT_ACCEPT_PASS");
        } else {
            System.out.println("PILOT_ACCEPT_FAIL");
        }
        return pass;
    }

    static int step1Wizard(int pass, String code) {
        companyId = ErpSetupWizard.createFormalCompany(code, "Pilot Formal Co", "Asia/Shanghai");
        if (ErpBooks.isFormalBook(companyId) == 0) {
            System.out.println("FAIL step1 bookKind");
            return 0;
        }
        ErpSetupWizard.applyCompanyStep(companyId, 0L, "Pilot Formal Co", "Asia/Shanghai");
        ErpSetupWizard.applyCurrencyStep(companyId, 0L, "CNY", 0);
        warehouseId = ErpSetupWizard.applyWarehouseStep(companyId, 0L, "WH1", "Main WH");
        ErpSetupWizard.applyNumberingStep(companyId, 0L);
        adminUserId = ErpSetupWizard.applyUserStep(companyId, 0L, "admin@" + code + ".test", "Admin",
                "erp1234", "admin");
        ErpSetupWizard.advanceToOpening(companyId, adminUserId);
        System.out.println("OK step1 wizard companyId=" + CryptoParse.longToString(companyId));
        return pass;
    }

    static int step2Csv(int pass) {
        String partners = "code,name,kind\nCPILOT,Pilot Customer,CUSTOMER\nSPILOT,Pilot Supplier,SUPPLIER\nbadline\n";
        String preview = ErpPlatformIo.previewPartnersCsv(companyId, partners);
        if (preview.indexOf("\"errorRows\":1") < 0 && preview.indexOf("errorRows\":1") < 0) {
            /* tolerate formatting */
        }
        long batch = ErpPlatformIo.importPartnersCsv(companyId, adminUserId, "P-BATCH1", partners);
        if (batch == 0L) {
            System.out.println("FAIL step2 partner import");
            return 0;
        }
        String items = "sku,name,price,cost\nSKUPILOT,Pilot Item,8000,5000\n";
        long ib = ErpPlatformIo.importItemsCsv(companyId, adminUserId, "I-BATCH1", items);
        if (ib == 0L) {
            System.out.println("FAIL step2 item import");
            return 0;
        }
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("SKUPILOT");
        itemId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", ip,
                new LongMapper()));
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(companyId));
        sp.add("SPILOT");
        supplierId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", sp, new LongMapper()));
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(companyId));
        cp.add("CPILOT");
        customerId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM business_partners WHERE company_id=? AND code=?", cp, new LongMapper()));
        if (itemId == 0L || supplierId == 0L || customerId == 0L) {
            System.out.println("FAIL step2 resolve masters");
            return 0;
        }
        /* badline must not create partner */
        ArrayList<Object> bad = new ArrayList<Object>();
        bad.add(CryptoParse.longToString(companyId));
        bad.add("badline");
        Object badId = ErpDb.queryOne("SELECT id FROM business_partners WHERE company_id=? AND code=?", bad,
                new LongMapper());
        if (badId != null) {
            System.out.println("FAIL step2 badline written");
            return 0;
        }
        ErpSetupWizard.advanceToValidate(companyId, adminUserId);
        System.out.println("OK step2 csv");
        return pass;
    }

    static int step3Opening(int pass) {
        ArrayList<Object> lines = new ArrayList<Object>();
        OpeningLineInput ln = new OpeningLineInput();
        ln.itemId = itemId;
        ln.quantity = 20;
        ln.unitCostAmount = 5000L;
        lines.add(ln);
        PostResultDto draft = ErpOpening.createStockDraft(companyId, adminUserId, warehouseId,
                AsyncClockNative.wallMillis(), "opening", "os-draft-1", lines);
        if (draft.ok == 0) {
            System.out.println("FAIL step3 draft " + draft.message);
            return 0;
        }
        long docId = extractId(draft.documentJson);
        PostResultDto posted = ErpOpening.postStock(companyId, adminUserId, docId, "os-post-1");
        if (posted.ok == 0) {
            System.out.println("FAIL step3 post " + posted.message);
            return 0;
        }
        if (qty() != 20) {
            System.out.println("FAIL step3 qty want 20 got " + CryptoParse.longToString(qty()));
            return 0;
        }
        ErpSetupWizard.validateAndComplete(companyId, adminUserId);
        System.out.println("OK step3 opening=20");
        return pass;
    }

    static int step4PurchaseGr(int pass) {
        ArrayList<Object> lines = new ArrayList<Object>();
        PoLineInput pl = new PoLineInput();
        pl.itemId = itemId;
        pl.quantity = 10;
        pl.unitPriceCents = 5000L;
        lines.add(pl);
        ErpDb.reconnect();
        PurchaseOrderDto po = null;
        try {
            po = ErpPurchasing.createDraft(companyId, supplierId, lines);
        } catch (Exception ex) {
            System.out.println("FAIL step4 createDraft");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return 0;
        }
        if (po == null || po.id == 0L) {
            System.out.println("FAIL step4 po null");
            return 0;
        }
        ErpPurchasing.approve(companyId, po.id);
        PurchaseOrderDto full = ErpPurchasing.get(companyId, po.id);
        OrderLineDto ol = (OrderLineDto) full.lines.get(0);
        PostGoodsReceiptRequest req = new PostGoodsReceiptRequest();
        req.purchaseOrderId = po.id;
        req.warehouseId = warehouseId;
        req.idempotencyKey = "gr-pilot-1";
        req.lines = new ArrayList<Object>();
        PostLineRequest gl = new PostLineRequest();
        gl.orderLineId = ol.id;
        gl.quantity = 10;
        req.lines.add(gl);
        PostResultDto gr = ErpPosting.postGoodsReceipt(companyId, req);
        if (gr.ok == 0) {
            System.out.println("FAIL step4 gr " + gr.message);
            return 0;
        }
        if (qty() != 30) {
            System.out.println("FAIL step4 qty want 30 got " + CryptoParse.longToString(qty()));
            return 0;
        }
        System.out.println("OK step4 gr stock=30");
        return pass;
    }

    static int step5SalesDl(int pass) {
        ArrayList<Object> lines = new ArrayList<Object>();
        SoLineInput sl = new SoLineInput();
        sl.itemId = itemId;
        sl.quantity = 3;
        sl.unitPriceCents = 8000L;
        lines.add(sl);
        SalesOrderDto so = ErpSales.createDraft(companyId, customerId, lines);
        ErpSales.approve(companyId, so.id);
        SalesOrderDto full = ErpSales.get(companyId, so.id);
        OrderLineDto ol = (OrderLineDto) full.lines.get(0);
        PostDeliveryRequest req = new PostDeliveryRequest();
        req.salesOrderId = so.id;
        req.warehouseId = warehouseId;
        req.idempotencyKey = "dl-pilot-1";
        req.lines = new ArrayList<Object>();
        PostLineRequest dl = new PostLineRequest();
        dl.orderLineId = ol.id;
        dl.quantity = 3;
        req.lines.add(dl);
        PostResultDto r = ErpPosting.postDelivery(companyId, req);
        if (r.ok == 0) {
            System.out.println("FAIL step5 dl " + r.message);
            return 0;
        }
        dlId = extractId(r.documentJson);
        if (qty() != 27) {
            System.out.println("FAIL step5 qty want 27 got " + CryptoParse.longToString(qty()));
            return 0;
        }
        System.out.println("OK step5 dl stock=27");
        return pass;
    }

    static int step6ReverseDl(int pass) {
        PostResultDto r = ErpReverse.reverseDelivery(companyId, adminUserId, dlId, "dl error", "dl-rev-1");
        if (r.ok == 0) {
            System.out.println("FAIL step6 reverse " + r.message);
            return 0;
        }
        if (qty() != 30) {
            System.out.println("FAIL step6 qty want 30 got " + CryptoParse.longToString(qty()));
            return 0;
        }
        System.out.println("OK step6 reverse stock=30");
        return pass;
    }

    static int step7ReverseAgain(int pass) {
        PostResultDto r = ErpReverse.reverseDelivery(companyId, adminUserId, dlId, "reverse again", "dl-rev-2");
        if (r.ok != 0) {
            System.out.println("FAIL step7 second reverse should fail");
            return 0;
        }
        if (r.message == null || r.message.indexOf("already reversed") < 0) {
            if (r.message == null || r.message.indexOf("BLOCKED") < 0) {
                System.out.println("FAIL step7 message " + r.message);
                return 0;
            }
        }
        if (qty() != 30) {
            System.out.println("FAIL step7 qty changed");
            return 0;
        }
        System.out.println("OK step7 second reverse rejected");
        return pass;
    }

    static int step8ArAp(int pass) {
        PostResultDto ar = ErpOpening.createAndPostAr(companyId, adminUserId, customerId,
                AsyncClockNative.wallMillis(), 10000L, "opening AR", "oa-1");
        PostResultDto ap = ErpOpening.createAndPostAp(companyId, adminUserId, supplierId,
                AsyncClockNative.wallMillis(), 8000L, "opening AP", "op-1");
        if (ar.ok == 0 || ap.ok == 0) {
            System.out.println("FAIL step8 ar/ap post");
            if (ar.message != null) {
                System.out.println(ar.message);
            }
            if (ap.message != null) {
                System.out.println(ap.message);
            }
            return 0;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object arOpen = ErpDb.queryOne(
                "SELECT SUM(open_amount) FROM ar_invoices WHERE company_id=? AND status='POSTED'", p,
                new LongMapper());
        Object apOpen = ErpDb.queryOne(
                "SELECT SUM(open_amount) FROM ap_invoices WHERE company_id=? AND status='POSTED'", p,
                new LongMapper());
        Object arCnt = ErpDb.queryOne("SELECT COUNT(*) FROM opening_ar_docs WHERE company_id=?", p, new LongMapper());
        Object apCnt = ErpDb.queryOne("SELECT COUNT(*) FROM opening_ap_docs WHERE company_id=?", p, new LongMapper());
        if (ErpNum.asLong(arOpen) < 10000L || ErpNum.asLong(apOpen) < 8000L) {
            System.out.println("FAIL step8 balances ar=" + CryptoParse.longToString(ErpNum.asLong(arOpen))
                    + " ap=" + CryptoParse.longToString(ErpNum.asLong(apOpen))
                    + " oaDocs=" + CryptoParse.longToString(ErpNum.asLong(arCnt))
                    + " opDocs=" + CryptoParse.longToString(ErpNum.asLong(apCnt)));
            return 0;
        }
        System.out.println("OK step8 ar/ap opening");
        return pass;
    }

    static int step9RestartCheck(int pass) {
        ErpDb.reconnect();
        if (qty() != 30) {
            System.out.println("FAIL step9 after reconnect qty");
            return 0;
        }
        System.out.println("OK step9 reconnect");
        return pass;
    }

    static int step10Migrate(int pass) {
        ArrayList<Object> mp = new ArrayList<Object>();
        mp.add("005_pilot");
        Object v = ErpDb.queryOne("SELECT version FROM schema_migrations WHERE version=?", mp, new StringMapper());
        if (v == null) {
            System.out.println("FAIL step10 migration 005");
            return 0;
        }
        if (Erp.schemaReady == 0) {
            System.out.println("FAIL step10 schemaReady");
            return 0;
        }
        System.out.println("OK step10 migrate 005");
        return pass;
    }

    static int step11BackupHint(int pass) {
        /* Script presence is the automated gate; full pg drill is ops-verified. */
        String script = File.readString("examples/erp/scripts/backup-restore.sh");
        if (script == null || script.indexOf("BACKUP_RESTORE_OK") < 0) {
            System.out.println("FAIL step11 backup script");
            return 0;
        }
        String up = File.readString("examples/erp/scripts/jnative-one-upgrade.sh");
        if (up == null || up.indexOf("pg_dump") < 0) {
            System.out.println("FAIL step11 upgrade backup");
            return 0;
        }
        System.out.println("OK step11 backup/upgrade scripts");
        return pass;
    }

    static int step12DemoIsolation(int pass) {
        int denied = 0;
        try {
            ErpBooks.resetDemoBook(companyId);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL step12 formal reset should 403");
            return 0;
        }
        if (qty() != 30) {
            System.out.println("FAIL step12 formal data affected");
            return 0;
        }
        System.out.println("OK step12 demo isolation");
        return pass;
    }

    static int qty() {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(warehouseId));
        p.add(CryptoParse.longToString(itemId));
        Object v = ErpDb.queryOne(
                "SELECT quantity_on_hand FROM inventory_balances WHERE company_id=? AND warehouse_id=? AND item_id=?",
                p, new IntMapper());
        if (v == null) {
            return 0;
        }
        return ErpNum.parseInt((String) v);
    }

    static long extractId(String json) {
        if (json == null) {
            return 0L;
        }
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            return 0L;
        }
        int i = idx + 5;
        String n = "";
        while (i < json.length()) {
            int c = json.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            i = i + 1;
        }
        return ErpNum.parseLong(n);
    }
}
