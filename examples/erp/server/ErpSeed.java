/**
 * @author 张晓达
 */
class ErpSeed {
    /**
     * Boot seed policy:
     * - ERP_DEMO_MODE=1: ensure DEMO book JNAT with versioned practice data.
     * - ERP_DEMO_MODE=0: never insert demo business into empty DB; formal books stay empty
     *   until setup wizard. Existing DEMO books may get non-destructive seed upgrade only.
     */
    static void ensure() {
        Object c = null;
        try {
            c = ErpDb.queryOne(
                    "SELECT id FROM companies WHERE code='JNAT'", new ArrayList<Object>(), new LongMapper());
        } catch (Exception ex) {
            ErpDb.reconnect();
            c = ErpDb.queryOne(
                    "SELECT id FROM companies WHERE code='JNAT'", new ArrayList<Object>(), new LongMapper());
        }
        if (ErpProductConfig.demoMode != 0) {
            long companyId = 0L;
            if (c == null) {
                long now = AsyncClockNative.wallMillis();
                ErpDb.exec(
                        "INSERT INTO companies(code, name, book_kind, timezone, currency_code, amount_scale, created_at) VALUES ('JNAT','JNative Demo Co','"
                                + BookKind.DEMO + "','UTC','CNY',0,"
                                + CryptoParse.longToString(now) + ")",
                        new ArrayList<Object>());
                Object cidObj = ErpDb.queryOne(
                        "SELECT id FROM companies WHERE code='JNAT'", new ArrayList<Object>(), new LongMapper());
                companyId = ErpNum.asLong(cidObj);
                seedRoles(companyId, now);
                seedUsers(companyId, now);
                seedDemoBusiness(companyId, now);
                seedSequences(companyId);
                ErpFinanceCore.ensureSeed(companyId);
                ErpBooks.recordDemoSeed(companyId, 0);
                ErpDemoScenarioSeed.applyIfNeeded(companyId);
            } else {
                companyId = ErpNum.asLong(c);
                ErpBooks.setBookKind(companyId, BookKind.DEMO);
                ErpBooks.upgradeDemoSeedIfNeeded(companyId);
                Object uc = ErpDb.queryOne(
                        "SELECT COUNT(*) FROM users WHERE company_id="
                                + CryptoParse.longToString(companyId),
                        new ArrayList<Object>(), new LongMapper());
                if (ErpNum.asLong(uc) == 0L) {
                    seedUsers(companyId, AsyncClockNative.wallMillis());
                    System.out.println("DEMO_RESEED_USERS companyId="
                            + CryptoParse.longToString(companyId));
                }
            }
            ensurePlatform(companyId);
            return;
        }
        /* Formal / production boot: do not auto-create demo company. */
        if (c != null) {
            long companyId = ErpNum.asLong(c);
            String kind = ErpBooks.bookKind(companyId);
            if (kind.equals(BookKind.DEMO)) {
                ErpBooks.upgradeDemoSeedIfNeeded(companyId);
            }
            ensurePlatform(companyId);
        }
        /* Also ensure platform for any existing formal company (first by id). */
        Object any = ErpDb.queryOne(
                "SELECT id FROM companies ORDER BY id LIMIT 1", new ArrayList<Object>(), new LongMapper());
        if (any != null) {
            ensurePlatform(ErpNum.asLong(any));
        } else {
            /* Empty formal DB: bootstrap one FORMAL book + admin so login works.
               No demo masters/partners — business data stays empty until wizard. */
            ensureFormalBootstrap();
        }
    }

    /** First-boot admin for empty formal databases (no DEMO practice data). */
    static void ensureFormalBootstrap() {
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO companies(code, name, book_kind, timezone, currency_code, amount_scale, created_at) VALUES ('CO1','Formal Co','"
                        + BookKind.FORMAL + "','Asia/Shanghai','CNY',0,"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        Object cidObj = ErpDb.queryOne(
                "SELECT id FROM companies WHERE code='CO1'", new ArrayList<Object>(), new LongMapper());
        long companyId = ErpNum.asLong(cidObj);
        seedRoles(companyId, now);
        seedUsers(companyId, now);
        seedSequences(companyId);
        ErpFinanceCore.ensureSeed(companyId);
        ensurePlatform(companyId);
        System.out.println("FORMAL_BOOTSTRAP companyId=" + CryptoParse.longToString(companyId)
                + " admin=user1@jnative.dev");
    }

    static void ensurePlatform(long companyId) {
        ErpPlatformAuth.ensureSeed(companyId);
        Object adminId = ErpDb.queryOne(
                "SELECT id FROM users WHERE company_id=" + CryptoParse.longToString(companyId)
                        + " AND email='user1@jnative.dev'",
                new ArrayList<Object>(), new LongMapper());
        if (adminId != null) {
            long adminRole = ErpPlatformAuth.ensureRole(companyId, "admin", "Administrator", DataScope.COMPANY);
            ErpPlatformAuth.assignRole(ErpNum.asLong(adminId), adminRole);
            ErpPlatformConfig.setSetting(companyId, ErpNum.asLong(adminId), "locale", SettingType.STRING, "zh-CN", 0);
        }
        ErpPlatformReports.ensureReport(companyId, "INV_BAL", "Inventory Balance", "InventoryBalanceDataset");
        ErpPlatformReports.ensureReport(companyId, "SO_LIST", "Sales Orders", "SalesOrderDataset");
    }

    /** Practice masters for DEMO books only (customers/suppliers/items/warehouses). */
    static void seedDemoBusiness(long companyId, long now) {
        seedPartners(companyId, now);
        seedItems(companyId, now);
        seedWarehouses(companyId, now);
        /* Independent practice materials for tutorials */
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("FG-PRACTICE-A");
        Object item = ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper());
        if (item == null) {
            ErpDb.exec(
                    "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES ("
                            + CryptoParse.longToString(companyId)
                            + ",'FG-PRACTICE-A','Practice Item A',8000,5000,"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        }
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH-PRACTICE");
        Object wh = ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper());
        if (wh == null) {
            ErpDb.exec(
                    "INSERT INTO warehouses(company_id, code, name, created_at) VALUES ("
                            + CryptoParse.longToString(companyId)
                            + ",'WH-PRACTICE','Practice WH',"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        }
    }

    static void seedRoles(long companyId, long now) {
        ArrayList<Object> r = new ArrayList<Object>();
        r.add(CryptoParse.longToString(companyId));
        r.add("admin");
        r.add("Administrator");
        ErpDb.exec("INSERT INTO roles(company_id, code, name) VALUES (?,?,?)", r);
        if (Erp.dialect.equals("sqlite")) {
            ArrayList<Object> perm = new ArrayList<Object>();
            perm.add("erp.all");
            perm.add("Full ERP access");
            ErpDb.exec("INSERT OR IGNORE INTO permissions(code, name) VALUES (?,?)", perm);
        } else {
            ErpDb.exec(
                    "INSERT INTO permissions(code, name) VALUES ('erp.all','Full ERP access') ON CONFLICT DO NOTHING",
                    new ArrayList<Object>());
        }
    }

    static void seedUsers(long companyId, long now) {
        String hash = Password.hashBlocking("erp1234");
        int u = 1;
        while (u <= 5) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("user" + String.valueOf(u) + "@jnative.dev");
            row.add("ERP User " + String.valueOf(u));
            row.add(hash);
            if (u == 1) {
                row.add("admin");
            } else {
                row.add("user");
            }
            row.add(CryptoParse.longToString(now));
            row.add(CryptoParse.longToString(now));
            ErpDb.exec(
                    "INSERT INTO users(company_id, email, display_name, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    row);
            u = u + 1;
        }
    }

    static void seedPartners(long companyId, long now) {
        int c = 1;
        while (c <= 20) {
            String kind = PartnerKind.CUSTOMER;
            if (c <= 10) {
                kind = PartnerKind.CUSTOMER;
            } else {
                kind = PartnerKind.SUPPLIER;
            }
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("P" + String.valueOf(c));
            row.add("Partner " + String.valueOf(c));
            row.add(kind);
            row.add(CryptoParse.longToString(now));
            ErpDb.exec("INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)",
                    row);
            c = c + 1;
        }
    }

    static void seedItems(long companyId, long now) {
        int i = 1;
        while (i <= 100) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("SKU-" + String.valueOf(i));
            row.add("Item " + String.valueOf(i));
            row.add(String.valueOf(100 + i));
            row.add(CryptoParse.longToString(now));
            ErpDb.exec("INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES (?,?,?,?,?)",
                    row);
            i = i + 1;
        }
    }

    static void seedWarehouses(long companyId, long now) {
        int w = 1;
        while (w <= 3) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add("WH" + String.valueOf(w));
            row.add("Warehouse " + String.valueOf(w));
            row.add(CryptoParse.longToString(now));
            ErpDb.exec("INSERT INTO warehouses(company_id, code, name, created_at) VALUES (?,?,?,?)", row);
            w = w + 1;
        }
    }

    static void seedSequences(long companyId) {
        String[] names = new String[9];
        names[0] = "PO";
        names[1] = "SO";
        names[2] = "GR";
        names[3] = "DL";
        names[4] = "OS";
        names[5] = "OA";
        names[6] = "OAP";
        names[7] = "RV";
        names[8] = "PR";
        int i = 0;
        while (i < names.length) {
            ArrayList<Object> chk = new ArrayList<Object>();
            chk.add(CryptoParse.longToString(companyId));
            chk.add(names[i]);
            Object existing = ErpDb.queryOne(
                    "SELECT next_val FROM number_sequences WHERE company_id=? AND seq_name=?", chk, new LongMapper());
            if (existing == null) {
                ArrayList<Object> row = new ArrayList<Object>();
                row.add(CryptoParse.longToString(companyId));
                row.add(names[i]);
                row.add("1");
                ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", row);
            }
            i = i + 1;
        }
    }
}
