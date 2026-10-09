/**
 * Categories, UoM, employees, departments list helpers.
 * @author 张晓达
 */
class ErpMastersExtra {
    static void ensureSeed(long companyId, long now) {
        ensureCategory(companyId, "FG", "Finished Goods", now);
        ensureCategory(companyId, "RM", "Raw Material", now);
        ensureCategory(companyId, "PKG", "Packaging", now);
        ensureUnit(companyId, "EA", "Each", now);
        ensureUnit(companyId, "KG", "Kilogram", now);
        ensureUnit(companyId, "BOX", "Box", now);
        ensureEmployee(companyId, "EMP-001", "Li Wei", "Sales", "Account Manager", now);
        ensureEmployee(companyId, "EMP-002", "Chen Fang", "Purchasing", "Buyer", now);
        ensureEmployee(companyId, "EMP-003", "Wang Jun", "Finance", "Controller", now);
        ensureDepartment(companyId, "SALES", "Sales");
        ensureDepartment(companyId, "PURCH", "Purchasing");
        ensureDepartment(companyId, "FIN", "Finance");
        ensureDepartment(companyId, "MFG", "Manufacturing");
    }

    static void ensureCategory(long companyId, String code, String name, long now) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object id = ErpDb.queryOne("SELECT id FROM item_categories WHERE company_id=? AND code=?", p, new LongMapper());
        if (id != null) {
            return;
        }
        ArrayList<Object> row = new ArrayList<Object>();
        row.add(CryptoParse.longToString(companyId));
        row.add(code);
        row.add(name);
        row.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO item_categories(company_id, code, name, created_at) VALUES (?,?,?,?)", row);
    }

    static void ensureUnit(long companyId, String code, String name, long now) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object id = ErpDb.queryOne("SELECT id FROM units_of_measure WHERE company_id=? AND code=?", p, new LongMapper());
        if (id != null) {
            return;
        }
        ArrayList<Object> row = new ArrayList<Object>();
        row.add(CryptoParse.longToString(companyId));
        row.add(code);
        row.add(name);
        row.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO units_of_measure(company_id, code, name, created_at) VALUES (?,?,?,?)", row);
    }

    static void ensureEmployee(long companyId, String code, String name, String dept, String title, long now) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object id = ErpDb.queryOne("SELECT id FROM employees WHERE company_id=? AND code=?", p, new LongMapper());
        if (id != null) {
            return;
        }
        ArrayList<Object> row = new ArrayList<Object>();
        row.add(CryptoParse.longToString(companyId));
        row.add(code);
        row.add(name);
        row.add(dept);
        row.add(title);
        row.add("ACTIVE");
        row.add(CryptoParse.longToString(now));
        ErpDb.exec(
                "INSERT INTO employees(company_id, code, name, department, title, status, created_at) VALUES (?,?,?,?,?,?,?)",
                row);
    }

    static void ensureDepartment(long companyId, String code, String name) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object id = ErpDb.queryOne("SELECT id FROM departments WHERE company_id=? AND code=?", p, new LongMapper());
        if (id != null) {
            return;
        }
        ArrayList<Object> row = new ArrayList<Object>();
        row.add(CryptoParse.longToString(companyId));
        row.add(code);
        row.add(name);
        ErpDb.exec("INSERT INTO departments(company_id, code, name) VALUES (?,?,?)", row);
    }

    static String categoriesJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name FROM item_categories WHERE company_id=? ORDER BY id", p);
        return simpleCodeNameList(r);
    }

    static String unitsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name FROM units_of_measure WHERE company_id=? ORDER BY id", p);
        return simpleCodeNameList(r);
    }

    static String employeesJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name, department, title, status FROM employees WHERE company_id=? ORDER BY id", p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"code\":" + ErpJson.str(row.getString(1))
                    + ",\"name\":" + ErpJson.str(row.getString(2))
                    + ",\"department\":" + ErpJson.str(row.getString(3))
                    + ",\"title\":" + ErpJson.str(row.getString(4))
                    + ",\"status\":" + ErpJson.str(row.getString(5)) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String departmentsJson(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name FROM departments WHERE company_id=? ORDER BY id", p);
        return simpleCodeNameList(r);
    }

    static String simpleCodeNameList(DbQueryResult r) {
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row row = r.rows.get(i);
            out = out + "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"code\":" + ErpJson.str(row.getString(1))
                    + ",\"name\":" + ErpJson.str(row.getString(2)) + "}";
            i = i + 1;
        }
        return out + "]";
    }
}
