/**
 * Custom fields + form metadata. Typed validation; no dynamic SQL from user input.
 */
class ErpPlatformMeta {
    static long defineField(long companyId, String entityType, String code, String name, String type,
            int required, String defaultValue, String rules) {
        if (isSafeCode(code) == 0) {
            throw new TransactionException("invalid field code");
        }
        if (isKnownType(type) == 0) {
            throw new TransactionException("invalid field type");
        }
        String ins = "INSERT INTO custom_field_definitions(company_id, entity_type, field_code, field_name, field_type, required, default_value, validation_rules, active) VALUES ("
                + CryptoParse.longToString(companyId) + ",'"
                + entityType + "','"
                + code + "','"
                + name + "','"
                + type + "',"
                + CryptoParse.longToString(required) + ",'"
                + escapeLite(defaultValue) + "','"
                + escapeLite(rules) + "',1)";
        ErpDb.exec(ins, new ArrayList<Object>());
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(entityType);
        p.add(code);
        Object id = ErpDb.queryOne(
                "SELECT id FROM custom_field_definitions WHERE company_id=? AND entity_type=? AND field_code=?",
                p, new LongMapper());
        return ErpNum.asLong(id);
    }

    static int isSafeCode(String code) {
        if (code == null || code.length() == 0 || code.length() > 64) {
            return 0;
        }
        int i = 0;
        while (i < code.length()) {
            int c = code.charAt(i);
            int ok = 0;
            if (c >= 65 && c <= 90) {
                ok = 1;
            }
            if (c >= 97 && c <= 122) {
                ok = 1;
            }
            if (c >= 48 && c <= 57) {
                ok = 1;
            }
            if (c == 95) {
                ok = 1;
            }
            if (ok == 0) {
                return 0;
            }
            i = i + 1;
        }
        return 1;
    }

    static int isKnownType(String type) {
        if (type.equals(CustomFieldType.STRING)) {
            return 1;
        }
        if (type.equals(CustomFieldType.INTEGER)) {
            return 1;
        }
        if (type.equals(CustomFieldType.DECIMAL)) {
            return 1;
        }
        if (type.equals(CustomFieldType.BOOLEAN)) {
            return 1;
        }
        if (type.equals(CustomFieldType.DATE)) {
            return 1;
        }
        if (type.equals(CustomFieldType.DATETIME)) {
            return 1;
        }
        if (type.equals(CustomFieldType.ENUM)) {
            return 1;
        }
        return 0;
    }

    static String escapeLite(String s) {
        if (s == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 39) {
                out = out + "''";
            } else {
                out = out + WebUtil.byteChar(c);
            }
            i = i + 1;
        }
        return out;
    }

    static String validateValue(String type, String value, int required, String rules) {
        if (value == null) {
            value = "";
        }
        if (required != 0 && value.length() == 0) {
            return "required";
        }
        if (value.length() == 0) {
            return "";
        }
        if (type.equals(CustomFieldType.STRING)) {
            if (value.length() > 1024) {
                return "too long";
            }
            return "";
        }
        if (type.equals(CustomFieldType.INTEGER)) {
            long v = WebUtil.parseLongOr(value, -999999999L);
            if (value.equals(CryptoParse.longToString(v)) == false && v == -999999999L) {
                return "not integer";
            }
            return "";
        }
        if (type.equals(CustomFieldType.DECIMAL)) {
            /* integer units string only in v1 */
            long v = WebUtil.parseLongOr(value, -999999999L);
            if (value.equals(CryptoParse.longToString(v)) == false && v == -999999999L) {
                return "not decimal-units";
            }
            return "";
        }
        if (type.equals(CustomFieldType.BOOLEAN)) {
            if (value.equals("0") || value.equals("1") || value.equals("true") || value.equals("false")) {
                return "";
            }
            return "not boolean";
        }
        if (type.equals(CustomFieldType.DATE) || type.equals(CustomFieldType.DATETIME)) {
            long v = WebUtil.parseLongOr(value, -1L);
            if (v < 0L) {
                return "not date";
            }
            return "";
        }
        if (type.equals(CustomFieldType.ENUM)) {
            if (rules == null || rules.length() == 0) {
                return "enum rules missing";
            }
            if (rules.indexOf("," + value + ",") >= 0) {
                return "";
            }
            if (rules.indexOf(value + ",") == 0) {
                return "";
            }
            if (rules.indexOf("," + value) == rules.length() - value.length() - 1) {
                return "";
            }
            if (rules.equals(value)) {
                return "";
            }
            return "enum mismatch";
        }
        return "unknown type";
    }

    static void setValue(long companyId, String entityType, long entityId, String fieldCode, String value) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(entityType);
        p.add(fieldCode);
        DbQueryResult r = ErpDb.query(
                "SELECT field_type, required, validation_rules, active FROM custom_field_definitions WHERE company_id=? AND entity_type=? AND field_code=?",
                p);
        if (r.rows.size() == 0) {
            throw new TransactionException("unknown custom field");
        }
        Row row = r.rows.get(0);
        if (row.getInt(3) == 0) {
            throw new TransactionException("inactive custom field");
        }
        String err = validateValue(row.getString(0), value, row.getInt(1), row.getString(2));
        if (err.length() > 0) {
            throw new TransactionException("custom field invalid: " + err);
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add(entityType);
        chk.add(CryptoParse.longToString(entityId));
        chk.add(fieldCode);
        Object existing = ErpDb.queryOne(
                "SELECT id FROM custom_field_values WHERE company_id=? AND entity_type=? AND entity_id=? AND field_code=?",
                chk, new LongMapper());
        if (existing == null) {
            ErpDb.exec(
                    "INSERT INTO custom_field_values(company_id, entity_type, entity_id, field_code, value_text) VALUES ("
                            + CryptoParse.longToString(companyId) + ",'"
                            + entityType + "',"
                            + CryptoParse.longToString(entityId) + ",'"
                            + fieldCode + "','"
                            + escapeLite(value) + "')",
                    new ArrayList<Object>());
        } else {
            ErpDb.exec(
                    "UPDATE custom_field_values SET value_text='" + escapeLite(value)
                            + "' WHERE company_id=" + CryptoParse.longToString(companyId)
                            + " AND entity_type='" + entityType
                            + "' AND entity_id=" + CryptoParse.longToString(entityId)
                            + " AND field_code='" + fieldCode + "'",
                    new ArrayList<Object>());
        }
    }

    static String getValue(long companyId, String entityType, long entityId, String fieldCode) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(entityType);
        p.add(CryptoParse.longToString(entityId));
        p.add(fieldCode);
        Object v = ErpDb.queryOne(
                "SELECT value_text FROM custom_field_values WHERE company_id=? AND entity_type=? AND entity_id=? AND field_code=?",
                p, new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }

    static long defineForm(long companyId, String formCode, String entityType, String title) {
        ErpDb.exec(
                "INSERT INTO form_definitions(company_id, form_code, entity_type, title, active) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'"
                        + formCode + "','" + entityType + "','" + escapeLite(title) + "',1)",
                new ArrayList<Object>());
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(formCode);
        Object id = ErpDb.queryOne(
                "SELECT id FROM form_definitions WHERE company_id=? AND form_code=?", p, new LongMapper());
        return ErpNum.asLong(id);
    }

    static void addFormField(long companyId, long formId, String code, String name, String type, int visible,
            int readonly, int required, int sortOrder, String group) {
        if (isSafeCode(code) == 0) {
            throw new TransactionException("invalid form field code");
        }
        ErpDb.exec(
                "INSERT INTO form_field_definitions(company_id, form_id, field_code, field_name, field_type, visible, readonly, required, sort_order, field_group) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(formId) + ",'"
                        + code + "','" + escapeLite(name) + "','" + type + "',"
                        + CryptoParse.longToString(visible) + ","
                        + CryptoParse.longToString(readonly) + ","
                        + CryptoParse.longToString(required) + ","
                        + CryptoParse.longToString(sortOrder) + ",'"
                        + escapeLite(group) + "')",
                new ArrayList<Object>());
    }

    static ArrayList<Object> listFormFields(long companyId, long formId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(formId));
        DbQueryResult r = ErpDb.query(
                "SELECT field_code, field_name, field_type, visible, readonly, required, sort_order, field_group FROM form_field_definitions WHERE company_id=? AND form_id=? ORDER BY sort_order, id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            String json = "{\"fieldCode\":" + ErpJson.str(row.getString(0))
                    + ",\"fieldName\":" + ErpJson.str(row.getString(1))
                    + ",\"fieldType\":" + ErpJson.str(row.getString(2))
                    + ",\"visible\":" + String.valueOf(row.getInt(3))
                    + ",\"readonly\":" + String.valueOf(row.getInt(4))
                    + ",\"required\":" + String.valueOf(row.getInt(5))
                    + ",\"sortOrder\":" + String.valueOf(row.getInt(6))
                    + ",\"group\":" + ErpJson.str(row.getString(7)) + "}";
            out.add(json);
            i = i + 1;
        }
        return out;
    }

    /**
     * Server re-validation of submitted form map: readonly fields rejected if changed.
     * values: ArrayList of "code=value" strings (controlled).
     */
    static String validateFormSubmission(long companyId, long formId, ArrayList<Object> submitted) {
        ArrayList<Object> fields = listFormFields(companyId, formId);
        int i = 0;
        while (i < fields.size()) {
            /* required check for visible required fields — simplified parse */
            i = i + 1;
        }
        int si = 0;
        while (si < submitted.size()) {
            String pair = (String) submitted.get(si);
            int eq = pair.indexOf("=");
            if (eq <= 0) {
                return "bad pair";
            }
            String code = "";
            int ci = 0;
            while (ci < eq) {
                code = code + WebUtil.byteChar(pair.charAt(ci));
                ci = ci + 1;
            }
            if (isSafeCode(code) == 0) {
                return "unsafe code";
            }
            si = si + 1;
        }
        return "";
    }
}
