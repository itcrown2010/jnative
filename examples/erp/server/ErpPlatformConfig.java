/**
 * Settings + audit events. Sensitive values never returned in clear text lists.
 */
class ErpPlatformConfig {
    static void setSetting(long companyId, long userId, String key, String type, String value, int sensitive) {
        if (userId != 0L && ErpPlatformAuth.hasPermission(userId, companyId, "ADMIN.SETTING.MANAGE") == 0) {
            throw new TransactionException("FORBIDDEN:ADMIN.SETTING.MANAGE");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        Object old = ErpDb.queryOne(
                "SELECT setting_value FROM system_settings WHERE company_id=? AND setting_key=?", p,
                new StringMapper());
        String oldVal = "";
        if (old != null) {
            oldVal = (String) old;
        }
        long now = AsyncClockNative.wallMillis();
        if (old == null) {
            ErpDb.exec(
                    "INSERT INTO system_settings(company_id, setting_key, setting_type, setting_value, sensitive, updated_by, updated_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ",'" + key + "','" + type + "','"
                            + ErpPlatformMeta.escapeLite(value) + "',"
                            + CryptoParse.longToString(sensitive) + ","
                            + CryptoParse.longToString(userId) + ","
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        } else {
            ErpDb.exec(
                    "UPDATE system_settings SET setting_value='" + ErpPlatformMeta.escapeLite(value)
                            + "', setting_type='" + type + "', sensitive="
                            + CryptoParse.longToString(sensitive) + ", updated_by="
                            + CryptoParse.longToString(userId) + ", updated_at="
                            + CryptoParse.longToString(now) + " WHERE company_id="
                            + CryptoParse.longToString(companyId) + " AND setting_key='" + key + "'",
                    new ArrayList<Object>());
        }
        String histOld = oldVal;
        String histNew = value;
        if (sensitive != 0) {
            histOld = "***";
            histNew = "***";
        }
        ErpDb.exec(
                "INSERT INTO setting_history(company_id, setting_key, old_value, new_value, changed_by, changed_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'" + key + "','"
                        + ErpPlatformMeta.escapeLite(histOld) + "','"
                        + ErpPlatformMeta.escapeLite(histNew) + "',"
                        + CryptoParse.longToString(userId) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
    }

    static String getSetting(long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        Object v = ErpDb.queryOne(
                "SELECT setting_value FROM system_settings WHERE company_id=? AND setting_key=?", p,
                new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }

    static String getSettingForDisplay(long companyId, String key) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(key);
        DbQueryResult r = ErpDb.query(
                "SELECT setting_value, sensitive FROM system_settings WHERE company_id=? AND setting_key=?", p);
        if (r.rows.size() == 0) {
            return "";
        }
        if (r.rows.get(0).getInt(1) != 0) {
            return "***";
        }
        return r.rows.get(0).getString(0);
    }
}

class ErpPlatformAudit {
    static void write(long companyId, long actorUserId, String resourceType, long resourceId, String action,
            String beforeJson, String afterJson, String requestId) {
        long now = AsyncClockNative.wallMillis();
        if (beforeJson == null) {
            beforeJson = "";
        }
        if (afterJson == null) {
            afterJson = "";
        }
        if (requestId == null) {
            requestId = "";
        }
        ErpDb.exec(
                "INSERT INTO audit_events(company_id, actor_user_id, resource_type, resource_id, action, before_json, after_json, request_id, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(actorUserId) + ",'"
                        + resourceType + "',"
                        + CryptoParse.longToString(resourceId) + ",'"
                        + action + "','"
                        + ErpPlatformMeta.escapeLite(maskSecrets(beforeJson)) + "','"
                        + ErpPlatformMeta.escapeLite(maskSecrets(afterJson)) + "','"
                        + ErpPlatformMeta.escapeLite(requestId) + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
    }

    static String maskSecrets(String json) {
        if (json == null) {
            return "";
        }
        /* lightweight redaction markers */
        if (json.indexOf("password") >= 0 || json.indexOf("secret") >= 0 || json.indexOf("token") >= 0) {
            return "{\"redacted\":true}";
        }
        return json;
    }

    static int countEvents(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT COUNT(*) FROM audit_events WHERE company_id=?", p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static int tryDeleteAsUser(long companyId, long eventId) {
        /* business users must not delete audit — always refuse */
        return 0;
    }
}
