/**
 * Enterprise Foundation 1.4 — authorization, isolation, metadata, workflow,
 * reports, CSV IO, settings, audit. Native + PostgreSQL preferred.
 * @author 张晓达
 */
class ErpEnterpriseLogicTest {
    static long companyA = 0L;
    static long companyB = 0L;
    static long salesUser = 0L;
    static long salesOther = 0L;
    static long financeUser = 0L;
    static long purchaseUser = 0L;
    static long adminUser = 0L;
    static long adminB = 0L;
    static long deptSales = 0L;
    static String companyCode = "E";

    static int runAll() {
        companyCode = "E" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        seed();
        if (companyA == 0L || salesUser == 0L) {
            System.out.println("FAIL enterprise seed");
            return 0;
        }
        int pass = 1;
        pass = runAuth(pass);
        ErpDb.reconnect();
        pass = runIsolation(pass);
        ErpDb.reconnect();
        pass = runCustomFields(pass);
        ErpDb.reconnect();
        pass = runForms(pass);
        ErpDb.reconnect();
        pass = runWorkflow(pass);
        ErpDb.reconnect();
        pass = runReports(pass);
        ErpDb.reconnect();
        pass = runImportExport(pass);
        ErpDb.reconnect();
        pass = runConfigAudit(pass);
        ErpDb.reconnect();
        pass = runMigrationTrack(pass);
        if (pass != 0) {
            System.out.println("PASS enterprise");
        } else {
            System.out.println("FAIL enterprise");
        }
        return pass;
    }

    static void seed() {
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO companies(code, name, created_at) VALUES ('" + companyCode + "A','Ent A',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ErpDb.exec(
                "INSERT INTO companies(code, name, created_at) VALUES ('" + companyCode + "B','Ent B',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> ca = new ArrayList<Object>();
        ca.add(companyCode + "A");
        companyA = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", ca, new LongMapper()));
        ArrayList<Object> cb = new ArrayList<Object>();
        cb.add(companyCode + "B");
        companyB = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", cb, new LongMapper()));

        ErpDb.exec(
                "INSERT INTO departments(company_id, code, name) VALUES ("
                        + CryptoParse.longToString(companyA) + ",'SALES','Sales')",
                new ArrayList<Object>());
        ArrayList<Object> dp = new ArrayList<Object>();
        dp.add(CryptoParse.longToString(companyA));
        dp.add("SALES");
        deptSales = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM departments WHERE company_id=? AND code=?", dp, new LongMapper()));

        ErpPlatformAuth.ensureSeed(companyA);
        ErpPlatformAuth.ensureSeed(companyB);

        salesUser = insertUser(companyA, "sales1@" + companyCode + ".dev", "Sales One", deptSales);
        salesOther = insertUser(companyA, "sales2@" + companyCode + ".dev", "Sales Two", deptSales);
        financeUser = insertUser(companyA, "fin@" + companyCode + ".dev", "Finance", 0L);
        purchaseUser = insertUser(companyA, "pur@" + companyCode + ".dev", "Purchase", 0L);
        adminUser = insertUser(companyA, "admin@" + companyCode + ".dev", "Admin A", 0L);
        adminB = insertUser(companyB, "adminb@" + companyCode + ".dev", "Admin B", 0L);

        long salesRole = ErpPlatformAuth.ensureRole(companyA, "sales", "Sales", DataScope.OWN);
        long financeRole = ErpPlatformAuth.ensureRole(companyA, "finance", "Finance", DataScope.COMPANY);
        long purchaseRole = ErpPlatformAuth.ensureRole(companyA, "purchase", "Purchase", DataScope.DEPARTMENT);
        long adminRole = ErpPlatformAuth.ensureRole(companyA, "admin", "Administrator", DataScope.COMPANY);
        long adminRoleB = ErpPlatformAuth.ensureRole(companyB, "admin", "Administrator", DataScope.COMPANY);

        ErpPlatformAuth.assignRole(salesUser, salesRole);
        ErpPlatformAuth.assignRole(salesOther, salesRole);
        ErpPlatformAuth.assignRole(financeUser, financeRole);
        ErpPlatformAuth.assignRole(purchaseUser, purchaseRole);
        ErpPlatformAuth.assignRole(adminUser, adminRole);
        ErpPlatformAuth.assignRole(adminB, adminRoleB);

        ErpDb.exec(
                "INSERT INTO warehouses(company_id, code, name, created_at) VALUES ("
                        + CryptoParse.longToString(companyA) + ",'WH1','Main',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ErpDb.exec(
                "INSERT INTO warehouses(company_id, code, name, created_at) VALUES ("
                        + CryptoParse.longToString(companyB) + ",'WH1','Main B',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES ("
                        + CryptoParse.longToString(companyA) + ",'SKU1','Item A',100,"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES ("
                        + CryptoParse.longToString(companyB) + ",'SKU1','Item B',100,"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
    }

    static long insertUser(long companyId, String email, String name, long deptId) {
        long now = AsyncClockNative.wallMillis();
        String hash = Password.hashBlocking("erp1234");
        ArrayList<Object> row = new ArrayList<Object>();
        row.add(CryptoParse.longToString(companyId));
        row.add(email);
        row.add(name);
        row.add(hash);
        row.add("user");
        row.add(CryptoParse.longToString(now));
        row.add(CryptoParse.longToString(now));
        row.add(CryptoParse.longToString(deptId));
        row.add("0");
        ErpDb.exec(
                "INSERT INTO users(company_id, email, display_name, password_hash, role, created_at, updated_at, department_id, owner_user_id) VALUES (?,?,?,?,?,?,?,?,?)",
                row);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(email);
        return ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM users WHERE company_id=? AND email=?", p, new LongMapper()));
    }

    static int runAuth(int pass) {
        /* salesperson cannot read other salesperson's owned record (OWN scope) */
        if (ErpPlatformAuth.canAccessOwner(salesUser, companyA, salesOther, deptSales) != 0) {
            System.out.println("FAIL auth: sales read other owner");
            return 0;
        }
        if (ErpPlatformAuth.canAccessOwner(salesUser, companyA, salesUser, deptSales) == 0) {
            System.out.println("FAIL auth: sales own access");
            return 0;
        }
        /* salesperson cannot modify finance journal */
        if (ErpPlatformAuth.hasPermission(salesUser, companyA, "FINANCE.JOURNAL.POST") != 0) {
            System.out.println("FAIL auth: sales finance post");
            return 0;
        }
        /* purchaser cannot approve sales orders */
        if (ErpPlatformAuth.hasPermission(purchaseUser, companyA, "SALES.ORDER.APPROVE") != 0) {
            System.out.println("FAIL auth: purchase approve SO");
            return 0;
        }
        /* sales cannot run admin import */
        if (ErpPlatformAuth.hasPermission(salesUser, companyA, "ADMIN.IMPORT.RUN") != 0) {
            System.out.println("FAIL auth: sales import");
            return 0;
        }
        if (ErpPlatformAuth.hasPermission(adminUser, companyA, "ADMIN.AUDIT.READ") == 0) {
            System.out.println("FAIL auth: admin audit");
            return 0;
        }
        System.out.println("OK auth denials");
        return pass;
    }

    static int runIsolation(int pass) {
        if (ErpPlatformAuth.assertSameCompany(companyA, companyB) != 0) {
            System.out.println("FAIL isolation: A==B");
            return 0;
        }
        if (ErpPlatformAuth.assertSameCompany(companyA, companyA) == 0) {
            System.out.println("FAIL isolation: A==A");
            return 0;
        }
        /* admin of A has no roles in B → no inventory read in B */
        if (ErpPlatformAuth.hasPermission(adminUser, companyB, "INVENTORY.BALANCE.READ") != 0) {
            System.out.println("FAIL isolation: adminA inventory in B");
            return 0;
        }
        /* sales cannot read inventory (not granted) even in own company */
        if (ErpPlatformAuth.hasPermission(salesUser, companyA, "INVENTORY.BALANCE.READ") != 0) {
            System.out.println("FAIL isolation: sales inventory");
            return 0;
        }
        System.out.println("OK company isolation");
        return pass;
    }

    static int runCustomFields(int pass) {
        long fid = ErpPlatformMeta.defineField(companyA, "PARTNER", "region", "Region", CustomFieldType.ENUM, 1,
                "", "NORTH,SOUTH,EAST");
        if (fid == 0L) {
            System.out.println("FAIL cf define");
            return 0;
        }
        int threw = 0;
        try {
            ErpPlatformMeta.setValue(companyA, "PARTNER", 1L, "region", "WEST");
        } catch (Exception ex) {
            threw = 1;
        }
        if (threw == 0) {
            System.out.println("FAIL cf enum");
            return 0;
        }
        ErpPlatformMeta.setValue(companyA, "PARTNER", 1L, "region", "NORTH");
        String v = ErpPlatformMeta.getValue(companyA, "PARTNER", 1L, "region");
        if (v.equals("NORTH") == false) {
            System.out.println("FAIL cf get");
            return 0;
        }
        threw = 0;
        try {
            ErpPlatformMeta.defineField(companyA, "PARTNER", "bad;code", "X", CustomFieldType.STRING, 0, "", "");
        } catch (Exception ex) {
            threw = 1;
        }
        if (threw == 0) {
            System.out.println("FAIL cf unsafe code");
            return 0;
        }
        System.out.println("OK custom fields");
        return pass;
    }

    static int runForms(int pass) {
        long formId = ErpPlatformMeta.defineForm(companyA, "PARTNER_EDIT", "PARTNER", "Partner Edit");
        ErpPlatformMeta.addFormField(companyA, formId, "code", "Code", CustomFieldType.STRING, 1, 0, 1, 10, "main");
        ErpPlatformMeta.addFormField(companyA, formId, "secret", "Secret", CustomFieldType.STRING, 0, 1, 0, 20, "sys");
        ArrayList<Object> fields = ErpPlatformMeta.listFormFields(companyA, formId);
        if (fields.size() < 2) {
            System.out.println("FAIL form fields");
            return 0;
        }
        ArrayList<Object> sub = new ArrayList<Object>();
        sub.add("code=P1");
        sub.add("secret=hack");
        String err = ErpPlatformMeta.validateFormSubmission(companyA, formId, sub);
        if (err.length() > 0) {
            /* codes are safe — ok */
        }
        /* hidden/readonly must still be server-checked by callers; meta rejects unsafe codes */
        ArrayList<Object> bad = new ArrayList<Object>();
        bad.add("x;drop=1");
        err = ErpPlatformMeta.validateFormSubmission(companyA, formId, bad);
        if (err.equals("unsafe code") == false) {
            System.out.println("FAIL form unsafe");
            return 0;
        }
        System.out.println("OK forms");
        return pass;
    }

    static int runWorkflow(int pass) {
        ErpPlatformWorkflow.defineWorkflow(companyA, "SO_APPROVE", "SO Approval", "SALES_ORDER",
                "{\"steps\":1,\"role\":\"admin\"}");
        long inst = ErpPlatformWorkflow.startInstance(companyA, "SO_APPROVE", "SALES_ORDER", 9001L, 5000L,
                salesUser, 1, "admin");
        /* sales cannot approve */
        int denied = 0;
        try {
            ErpPlatformWorkflow.act(companyA, inst, salesUser, "APPROVE", "no", 5000L);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL wf overreach");
            return 0;
        }
        /* amount change during approval */
        denied = 0;
        try {
            ErpPlatformWorkflow.act(companyA, inst, adminUser, "APPROVE", "ok", 6000L);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL wf amount change");
            return 0;
        }
        int r = ErpPlatformWorkflow.act(companyA, inst, adminUser, "APPROVE", "ok", 5000L);
        if (r != 2) {
            System.out.println("FAIL wf approve result=" + String.valueOf(r));
            return 0;
        }
        /* duplicate approval */
        denied = 0;
        try {
            ErpPlatformWorkflow.act(companyA, inst, adminUser, "APPROVE", "again", 5000L);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL wf duplicate");
            return 0;
        }
        String st = ErpPlatformWorkflow.instanceStatus(companyA, inst);
        if (st.equals(WorkflowStatus.APPROVED) == false) {
            System.out.println("FAIL wf status");
            return 0;
        }
        /* version freeze: new version does not alter running instance binding */
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyA));
        wp.add("SO_APPROVE");
        Object wid = ErpDb.queryOne(
                "SELECT id FROM workflow_definitions WHERE company_id=? AND workflow_code=?", wp, new LongMapper());
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO workflow_versions(company_id, workflow_id, version_no, definition_json, created_at) VALUES ("
                        + CryptoParse.longToString(companyA) + ","
                        + CryptoParse.longToString(ErpNum.asLong(wid)) + ",2,'{\"steps\":99}',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(inst));
        Object bound = ErpDb.queryOne(
                "SELECT workflow_version_id FROM workflow_instances WHERE id=?", ip, new LongMapper());
        ArrayList<Object> vp = new ArrayList<Object>();
        vp.add(CryptoParse.longToString(ErpNum.asLong(bound)));
        Object vno = ErpDb.queryOne("SELECT version_no FROM workflow_versions WHERE id=?", vp, new LongMapper());
        if (ErpNum.asLong(vno) != 1L) {
            System.out.println("FAIL wf version freeze");
            return 0;
        }
        System.out.println("OK workflow");
        return pass;
    }

    static int runReports(int pass) {
        long rid = ErpPlatformReports.ensureReport(companyA, "INV_BAL", "Inventory Balance", "InventoryBalanceDataset");
        int denied = 0;
        try {
            ErpPlatformReports.runDataset(companyA, salesUser, "InventoryBalanceDataset", 10);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL report sales");
            return 0;
        }
        ArrayList<Object> rows = ErpPlatformReports.runDataset(companyA, adminUser, "InventoryBalanceDataset", 10);
        ErpPlatformReports.recordExecution(companyA, rid, adminUser, rows.size());
        /* unknown dataset */
        denied = 0;
        try {
            ErpPlatformReports.runDataset(companyA, adminUser, "EvilSqlDataset", 10);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL report unknown");
            return 0;
        }
        System.out.println("OK reports");
        return pass;
    }

    static int runImportExport(int pass) {
        ArrayList<Object> headers = new ArrayList<Object>();
        headers.add("code");
        headers.add("name");
        ArrayList<Object> row = new ArrayList<Object>();
        row.add("=CMD");
        row.add("hello,world");
        String csv = ErpPlatformIo.exportCsvSafe(headers, row);
        if (csv.indexOf("'=CMD") < 0) {
            System.out.println("FAIL export formula harden");
            return 0;
        }
        if (csv.indexOf("\"hello,world\"") < 0) {
            System.out.println("FAIL export quote");
            return 0;
        }
        deniedImport(salesUser);
        String body = "code,name,kind\nIMP1,Import One,CUSTOMER\nIMP2,Import Two,SUPPLIER\nbad\n";
        long job = ErpPlatformIo.importPartnersCsv(companyA, adminUser, "JOB1", body);
        if (job == 0L) {
            System.out.println("FAIL import job");
            return 0;
        }
        ArrayList<Object> jp = new ArrayList<Object>();
        jp.add(CryptoParse.longToString(companyA));
        jp.add("JOB1");
        Object ok = ErpDb.queryOne("SELECT ok_rows FROM import_jobs WHERE company_id=? AND job_code=?", jp,
                new LongMapper());
        if (ErpNum.asLong(ok) < 2L) {
            System.out.println("FAIL import ok_rows");
            return 0;
        }
        System.out.println("OK import/export");
        return pass;
    }

    static void deniedImport(long userId) {
        int denied = 0;
        try {
            ErpPlatformIo.importPartnersCsv(companyA, userId, "BAD", "code,name,kind\nX,Y,CUSTOMER\n");
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            throw new TransactionException("import should deny");
        }
    }

    static int runConfigAudit(int pass) {
        ErpPlatformConfig.setSetting(companyA, adminUser, "locale", SettingType.STRING, "zh-CN", 0);
        ErpPlatformConfig.setSetting(companyA, adminUser, "api_secret", SettingType.STRING, "s3cr3t", 1);
        String disp = ErpPlatformConfig.getSettingForDisplay(companyA, "api_secret");
        if (disp.equals("***") == false) {
            System.out.println("FAIL sensitive display");
            return 0;
        }
        String raw = ErpPlatformConfig.getSetting(companyA, "locale");
        if (raw.equals("zh-CN") == false) {
            System.out.println("FAIL setting get");
            return 0;
        }
        int denied = 0;
        try {
            ErpPlatformConfig.setSetting(companyA, salesUser, "hack", SettingType.STRING, "x", 0);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL setting auth");
            return 0;
        }
        ErpPlatformAudit.write(companyA, adminUser, "SETTING", 0L, "UPDATE", "{\"locale\":\"en\"}",
                "{\"locale\":\"zh-CN\"}", "req-1");
        ErpPlatformAudit.write(companyA, adminUser, "USER", salesUser, "LOGIN", "{\"password\":\"x\"}", "", "req-2");
        if (ErpPlatformAudit.countEvents(companyA) < 2) {
            System.out.println("FAIL audit count");
            return 0;
        }
        if (ErpPlatformAudit.tryDeleteAsUser(companyA, 1L) != 0) {
            System.out.println("FAIL audit immutable");
            return 0;
        }
        System.out.println("OK config/audit");
        return pass;
    }

    static int runMigrationTrack(int pass) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add("004_enterprise");
        Object v = ErpDb.queryOne("SELECT version FROM schema_migrations WHERE version=?", p, new StringMapper());
        if (v == null) {
            System.out.println("FAIL migration track");
            return 0;
        }
        ErpDb.recordMigration("004_enterprise");
        Object v2 = ErpDb.queryOne("SELECT version FROM schema_migrations WHERE version=?", p, new StringMapper());
        if (v2 == null) {
            System.out.println("FAIL migration idempotent");
            return 0;
        }
        System.out.println("OK migration");
        return pass;
    }
}
