/**
 * First-run setup wizard for empty FORMAL books.
 * Steps: COMPANY -> CURRENCY -> WAREHOUSE -> NUMBERING -> USER -> MASTER -> OPENING -> VALIDATE -> DONE
 */
class WizardStep {
    static String COMPANY = "COMPANY";
    static String CURRENCY = "CURRENCY";
    static String WAREHOUSE = "WAREHOUSE";
    static String NUMBERING = "NUMBERING";
    static String USER = "USER";
    static String MASTER = "MASTER";
    static String OPENING = "OPENING";
    static String VALIDATE = "VALIDATE";
    static String DONE = "DONE";
}

class ErpSetupWizard {
    static String getStateJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT step_code, payload_json, completed, updated_at FROM setup_wizard_state WHERE company_id=?",
                p);
        String book = ErpBooks.bookKind(companyId);
        if (r.rows.size() == 0) {
            return "{\"companyId\":" + ErpJson.num(companyId)
                    + ",\"bookKind\":" + ErpJson.str(book)
                    + ",\"step\":" + ErpJson.str(WizardStep.COMPANY)
                    + ",\"completed\":0,\"payload\":{}}";
        }
        Row row = r.rows.get(0);
        return "{\"companyId\":" + ErpJson.num(companyId)
                + ",\"bookKind\":" + ErpJson.str(book)
                + ",\"step\":" + ErpJson.str(row.getString(0))
                + ",\"completed\":" + CryptoParse.longToString(row.getInt(2))
                + ",\"payload\":" + row.getString(1)
                + ",\"updatedAt\":" + ErpJson.num(row.getLong(3)) + "}";
    }

    /** Create a brand-new FORMAL company (empty business). Idempotent by code. */
    static long createFormalCompany(String code, String name, String timezone) {
        if (code == null || code.length() == 0) {
            throw new TransactionException("company code required");
        }
        if (name == null || name.length() == 0) {
            throw new TransactionException("company name required");
        }
        if (timezone == null || timezone.length() == 0) {
            timezone = "UTC";
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(code);
        Object existing = ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper());
        if (existing != null) {
            long id = ErpNum.asLong(existing);
            if (ErpBooks.isFormalBook(id) == 0) {
                throw new TransactionException("code belongs to DEMO book");
            }
            return id;
        }
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO companies(code, name, created_at) VALUES ('"
                        + ErpPlatformMeta.escapeLite(code) + "','"
                        + ErpPlatformMeta.escapeLite(name) + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        Object cid = ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper());
        long companyId = ErpNum.asLong(cid);
        ErpDb.exec(
                "UPDATE companies SET book_kind='" + BookKind.FORMAL
                        + "', timezone='" + ErpPlatformMeta.escapeLite(timezone)
                        + "', currency_code='CNY', amount_scale=0, setup_completed=0 WHERE id="
                        + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpFinanceCore.ensureSeed(companyId);
        ErpSeed.seedSequences(companyId);
        ErpPlatformAuth.ensureSeed(companyId);
        saveProgress(companyId, 0L, WizardStep.CURRENCY, "{}");
        return companyId;
    }

    static void saveProgress(long companyId, long userId, String step, String payloadJson) {
        if (payloadJson == null || payloadJson.length() == 0) {
            payloadJson = "{}";
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object existing = ErpDb.queryOne("SELECT company_id FROM setup_wizard_state WHERE company_id=?", p,
                new LongMapper());
        if (existing == null) {
            ErpDb.exec(
                    "INSERT INTO setup_wizard_state(company_id, step_code, payload_json, completed, updated_at, updated_by) VALUES ("
                            + CryptoParse.longToString(companyId) + ",'"
                            + step + "','"
                            + ErpPlatformMeta.escapeLite(payloadJson) + "',0,"
                            + CryptoParse.longToString(now) + ","
                            + CryptoParse.longToString(userId) + ")",
                    new ArrayList<Object>());
        } else {
            ErpDb.exec(
                    "UPDATE setup_wizard_state SET step_code='" + step
                            + "', payload_json='" + ErpPlatformMeta.escapeLite(payloadJson)
                            + "', updated_at=" + CryptoParse.longToString(now)
                            + ", updated_by=" + CryptoParse.longToString(userId)
                            + " WHERE company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
        }
    }

    static void applyCompanyStep(long companyId, long userId, String name, String timezone) {
        assertFormalEditable(companyId);
        if (name != null && name.length() > 0) {
            ErpDb.exec(
                    "UPDATE companies SET name='" + ErpPlatformMeta.escapeLite(name)
                            + "' WHERE id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
        }
        if (timezone != null && timezone.length() > 0) {
            ErpDb.exec(
                    "UPDATE companies SET timezone='" + ErpPlatformMeta.escapeLite(timezone)
                            + "' WHERE id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
        }
        saveProgress(companyId, userId, WizardStep.CURRENCY,
                "{\"name\":" + ErpJson.str(name) + ",\"timezone\":" + ErpJson.str(timezone) + "}");
    }

    static void applyCurrencyStep(long companyId, long userId, String currency, int amountScale) {
        assertFormalEditable(companyId);
        if (currency == null || currency.length() == 0) {
            currency = "CNY";
        }
        if (amountScale < 0) {
            amountScale = 0;
        }
        ErpDb.exec(
                "UPDATE companies SET currency_code='" + ErpPlatformMeta.escapeLite(currency)
                        + "', amount_scale=" + CryptoParse.longToString(amountScale)
                        + " WHERE id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        saveProgress(companyId, userId, WizardStep.WAREHOUSE,
                "{\"currency\":" + ErpJson.str(currency) + ",\"amountScale\":"
                        + CryptoParse.longToString(amountScale) + "}");
    }

    static long applyWarehouseStep(long companyId, long userId, String code, String name) {
        assertFormalEditable(companyId);
        if (code == null || code.length() == 0) {
            throw new TransactionException("warehouse code required");
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add(code);
        Object existing = ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=? AND code=?", chk,
                new LongMapper());
        long whId = 0L;
        long now = AsyncClockNative.wallMillis();
        if (existing != null) {
            whId = ErpNum.asLong(existing);
        } else {
            ErpDb.exec(
                    "INSERT INTO warehouses(company_id, code, name, created_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ",'"
                            + ErpPlatformMeta.escapeLite(code) + "','"
                            + ErpPlatformMeta.escapeLite(name) + "',"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
            whId = ErpNum.asLong(ErpDb.queryOne(
                    "SELECT id FROM warehouses WHERE company_id=? AND code=?", chk, new LongMapper()));
        }
        saveProgress(companyId, userId, WizardStep.NUMBERING,
                "{\"warehouseId\":" + ErpJson.num(whId) + "}");
        return whId;
    }

    static void applyNumberingStep(long companyId, long userId) {
        assertFormalEditable(companyId);
        ErpSeed.seedSequences(companyId);
        saveProgress(companyId, userId, WizardStep.USER, "{\"numbering\":\"ok\"}");
    }

    static long applyUserStep(long companyId, long actorUserId, String email, String displayName,
            String password, String roleCode) {
        assertFormalEditable(companyId);
        if (email == null || email.length() == 0) {
            throw new TransactionException("email required");
        }
        if (password == null || password.length() < 6) {
            throw new TransactionException("password too short");
        }
        if (roleCode == null || roleCode.length() == 0) {
            roleCode = "admin";
        }
        if (displayName == null) {
            displayName = email;
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add(email);
        Object existing = ErpDb.queryOne("SELECT id FROM users WHERE company_id=? AND email=?", chk, new LongMapper());
        long userId = 0L;
        long now = AsyncClockNative.wallMillis();
        if (existing != null) {
            userId = ErpNum.asLong(existing);
        } else {
            String hash = Password.hashBlocking(password);
            ArrayList<Object> row = new ArrayList<Object>();
            row.add(CryptoParse.longToString(companyId));
            row.add(email);
            row.add(displayName);
            row.add(hash);
            row.add(roleCode);
            row.add(CryptoParse.longToString(now));
            row.add(CryptoParse.longToString(now));
            ErpDb.exec(
                    "INSERT INTO users(company_id, email, display_name, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    row);
            userId = ErpNum.asLong(ErpDb.queryOne(
                    "SELECT id FROM users WHERE company_id=? AND email=?", chk, new LongMapper()));
        }
        long roleId = ErpPlatformAuth.ensureRole(companyId, roleCode, roleCode, DataScope.COMPANY);
        ErpPlatformAuth.assignRole(userId, roleId);
        if (roleCode.equals("admin")) {
            ErpPlatformAuth.grantAllBusiness(roleId);
        }
        saveProgress(companyId, actorUserId, WizardStep.MASTER,
                "{\"userId\":" + ErpJson.num(userId) + "}");
        return userId;
    }

    static void advanceToOpening(long companyId, long userId) {
        assertFormalEditable(companyId);
        saveProgress(companyId, userId, WizardStep.OPENING, "{\"master\":\"ready\"}");
    }

    static void advanceToValidate(long companyId, long userId) {
        assertFormalEditable(companyId);
        saveProgress(companyId, userId, WizardStep.VALIDATE, "{\"opening\":\"ready\"}");
    }

    static String validateAndComplete(long companyId, long userId) {
        assertFormalEditable(companyId);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object wh = ErpDb.queryOne("SELECT COUNT(*) FROM warehouses WHERE company_id=?", p, new LongMapper());
        Object users = ErpDb.queryOne("SELECT COUNT(*) FROM users WHERE company_id=?", p, new LongMapper());
        Object items = ErpDb.queryOne("SELECT COUNT(*) FROM items WHERE company_id=?", p, new LongMapper());
        if (ErpNum.asLong(wh) == 0L) {
            throw new TransactionException("validate failed: need warehouse");
        }
        if (ErpNum.asLong(users) == 0L) {
            throw new TransactionException("validate failed: need user");
        }
        /* items optional if openings not yet - warn in message */
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "UPDATE companies SET setup_completed=1 WHERE id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpDb.exec(
                "UPDATE setup_wizard_state SET step_code='" + WizardStep.DONE
                        + "', completed=1, updated_at=" + CryptoParse.longToString(now)
                        + ", updated_by=" + CryptoParse.longToString(userId)
                        + " WHERE company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        ErpProductConfig.setupCompleted = 1;
        return "{\"ok\":true,\"step\":\"DONE\",\"warehouses\":" + ErpJson.num(ErpNum.asLong(wh))
                + ",\"users\":" + ErpJson.num(ErpNum.asLong(users))
                + ",\"items\":" + ErpJson.num(ErpNum.asLong(items)) + "}";
    }

    static void assertFormalEditable(long companyId) {
        if (ErpBooks.isFormalBook(companyId) == 0) {
            throw new TransactionException("wizard is for FORMAL books only");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object setup = ErpDb.queryOne("SELECT setup_completed FROM companies WHERE id=?", p, new LongMapper());
        if (ErpNum.asLong(setup) != 0L) {
            Object po = ErpDb.queryOne("SELECT COUNT(*) FROM purchase_orders WHERE company_id=?", p, new LongMapper());
            Object so = ErpDb.queryOne("SELECT COUNT(*) FROM sales_orders WHERE company_id=?", p, new LongMapper());
            if (ErpNum.asLong(po) + ErpNum.asLong(so) > 0L) {
                throw new TransactionException("FOUNDATION_LOCKED:business documents exist");
            }
        }
    }
}
