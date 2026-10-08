class ErpSeed {
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
        long companyId = 0L;
        if (c == null) {
            long now = AsyncClockNative.wallMillis();
            ErpDb.exec(
                    "INSERT INTO companies(code, name, created_at) VALUES ('JNAT','JNative Demo Co',"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
            Object cidObj = ErpDb.queryOne(
                    "SELECT id FROM companies WHERE code='JNAT'", new ArrayList<Object>(), new LongMapper());
            companyId = ErpNum.asLong(cidObj);
            seedRoles(companyId, now);
            seedUsers(companyId, now);
            seedPartners(companyId, now);
            seedItems(companyId, now);
            seedWarehouses(companyId, now);
            seedSequences(companyId);
            ErpFinanceCore.ensureSeed(companyId);
        } else {
            companyId = ErpNum.asLong(c);
        }
        ensurePlatform(companyId);
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
            ErpPlatformConfig.setSetting(companyId, ErpNum.asLong(adminId), "locale", SettingType.STRING, "en-US", 0);
        }
        ErpPlatformReports.ensureReport(companyId, "INV_BAL", "Inventory Balance", "InventoryBalanceDataset");
        ErpPlatformReports.ensureReport(companyId, "SO_LIST", "Sales Orders", "SalesOrderDataset");
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
        String[] names = new String[4];
        names[0] = "PO";
        names[1] = "SO";
        names[2] = "GR";
        names[3] = "DL";
        int i = 0;
        while (i < names.length) {
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add(names[i]);
            row.add("1");
            ErpDb.exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", row);
            i = i + 1;
        }
    }
}
