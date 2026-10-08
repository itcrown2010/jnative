/**
 * Enterprise authorization: MODULE.RESOURCE.ACTION + data scope OWN/DEPARTMENT/COMPANY.
 * Server-side only — UI hide is not authorization.
 */
class ErpPlatformAuth {
    static void ensureSeed(long companyId) {
        seedPermission("SALES.ORDER.READ", "Read sales orders");
        seedPermission("SALES.ORDER.CREATE", "Create sales orders");
        seedPermission("SALES.ORDER.APPROVE", "Approve sales orders");
        seedPermission("SALES.ORDER.POST", "Post sales/delivery");
        seedPermission("SALES.CUSTOMER.READ", "Read customers");
        seedPermission("FINANCE.JOURNAL.READ", "Read journals");
        seedPermission("FINANCE.JOURNAL.POST", "Post journals");
        seedPermission("PURCHASE.ORDER.READ", "Read POs");
        seedPermission("PURCHASE.ORDER.APPROVE", "Approve POs");
        seedPermission("INVENTORY.BALANCE.READ", "Read inventory");
        seedPermission("ADMIN.USER.READ", "Read users");
        seedPermission("ADMIN.ROLE.MANAGE", "Manage roles");
        seedPermission("ADMIN.CUSTOMFIELD.MANAGE", "Manage custom fields");
        seedPermission("ADMIN.FORM.MANAGE", "Manage forms");
        seedPermission("ADMIN.WORKFLOW.MANAGE", "Manage workflows");
        seedPermission("ADMIN.REPORT.RUN", "Run reports");
        seedPermission("ADMIN.IMPORT.RUN", "Run imports");
        seedPermission("ADMIN.SETTING.MANAGE", "Manage settings");
        seedPermission("ADMIN.AUDIT.READ", "Read audit");

        long salesRole = ensureRole(companyId, "sales", "Sales", DataScope.OWN);
        long financeRole = ensureRole(companyId, "finance", "Finance", DataScope.COMPANY);
        long purchaseRole = ensureRole(companyId, "purchase", "Purchase", DataScope.DEPARTMENT);
        long adminRole = ensureRole(companyId, "admin", "Administrator", DataScope.COMPANY);

        grant(salesRole, "SALES.ORDER.READ");
        grant(salesRole, "SALES.ORDER.CREATE");
        grant(salesRole, "SALES.CUSTOMER.READ");
        grant(financeRole, "FINANCE.JOURNAL.READ");
        grant(financeRole, "FINANCE.JOURNAL.POST");
        grant(financeRole, "ADMIN.REPORT.RUN");
        grant(purchaseRole, "PURCHASE.ORDER.READ");
        grant(purchaseRole, "PURCHASE.ORDER.APPROVE");
        grant(adminRole, "ADMIN.USER.READ");
        grant(adminRole, "ADMIN.ROLE.MANAGE");
        grant(adminRole, "ADMIN.CUSTOMFIELD.MANAGE");
        grant(adminRole, "ADMIN.FORM.MANAGE");
        grant(adminRole, "ADMIN.WORKFLOW.MANAGE");
        grant(adminRole, "ADMIN.REPORT.RUN");
        grant(adminRole, "ADMIN.IMPORT.RUN");
        grant(adminRole, "ADMIN.SETTING.MANAGE");
        grant(adminRole, "ADMIN.AUDIT.READ");
        grant(adminRole, "SALES.ORDER.READ");
        grant(adminRole, "SALES.ORDER.APPROVE");
        grant(adminRole, "FINANCE.JOURNAL.READ");
        grant(adminRole, "INVENTORY.BALANCE.READ");
    }

    static void seedPermission(String code, String name) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(code);
        Object existing = ErpDb.queryOne("SELECT id FROM platform_permissions WHERE code=?", p, new LongMapper());
        if (existing != null) {
            return;
        }
        ErpDb.exec(
                "INSERT INTO platform_permissions(code, name) VALUES ('" + code + "','" + name + "')",
                new ArrayList<Object>());
    }

    static long ensureRole(long companyId, String code, String name, String scope) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object existing = ErpDb.queryOne(
                "SELECT id FROM platform_roles WHERE company_id=? AND code=?", p, new LongMapper());
        if (existing != null) {
            return ErpNum.asLong(existing);
        }
        ErpDb.exec(
                "INSERT INTO platform_roles(company_id, code, name, data_scope) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'" + code + "','" + name + "','" + scope + "')",
                new ArrayList<Object>());
        Object id = ErpDb.queryOne(
                "SELECT id FROM platform_roles WHERE company_id=? AND code=?", p, new LongMapper());
        return ErpNum.asLong(id);
    }

    static void grant(long roleId, String permCode) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(permCode);
        Object pid = ErpDb.queryOne("SELECT id FROM platform_permissions WHERE code=?", p, new LongMapper());
        if (pid == null) {
            return;
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(roleId));
        chk.add(CryptoParse.longToString(ErpNum.asLong(pid)));
        Object existing = ErpDb.queryOne(
                "SELECT role_id FROM platform_role_permissions WHERE role_id=? AND permission_id=?",
                chk, new LongMapper());
        if (existing != null) {
            return;
        }
        ErpDb.exec(
                "INSERT INTO platform_role_permissions(role_id, permission_id) VALUES ("
                        + CryptoParse.longToString(roleId) + ","
                        + CryptoParse.longToString(ErpNum.asLong(pid)) + ")",
                new ArrayList<Object>());
    }

    static void assignRole(long userId, long roleId) {
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(userId));
        chk.add(CryptoParse.longToString(roleId));
        Object existing = ErpDb.queryOne(
                "SELECT user_id FROM platform_user_roles WHERE user_id=? AND role_id=?", chk, new LongMapper());
        if (existing != null) {
            return;
        }
        ErpDb.exec(
                "INSERT INTO platform_user_roles(user_id, role_id) VALUES ("
                        + CryptoParse.longToString(userId) + ","
                        + CryptoParse.longToString(roleId) + ")",
                new ArrayList<Object>());
    }

    static int hasPermission(long userId, long companyId, String permCode) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(companyId));
        p.add(permCode);
        Object v = ErpDb.queryOne(
                "SELECT COUNT(*) FROM platform_user_roles ur JOIN platform_roles r ON r.id=ur.role_id JOIN platform_role_permissions rp ON rp.role_id=r.id JOIN platform_permissions p ON p.id=rp.permission_id WHERE ur.user_id=? AND r.company_id=? AND p.code=?",
                p, new LongMapper());
        if (ErpNum.asLong(v) > 0L) {
            return 1;
        }
        return 0;
    }

    static String maxDataScope(long userId, long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT r.data_scope FROM platform_user_roles ur JOIN platform_roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.company_id=?",
                p);
        String best = DataScope.OWN;
        int i = 0;
        while (i < r.rows.size()) {
            String s = r.rows.get(i).getString(0);
            if (s.equals(DataScope.COMPANY)) {
                return DataScope.COMPANY;
            }
            if (s.equals(DataScope.DEPARTMENT)) {
                best = DataScope.DEPARTMENT;
            }
            i = i + 1;
        }
        return best;
    }

    static int assertSameCompany(long sessionCompanyId, long resourceCompanyId) {
        if (sessionCompanyId == 0L || resourceCompanyId == 0L) {
            return 0;
        }
        if (sessionCompanyId == resourceCompanyId) {
            return 1;
        }
        return 0;
    }

    static int canAccessOwner(long userId, long companyId, long ownerUserId, long ownerDeptId) {
        String scope = maxDataScope(userId, companyId);
        if (scope.equals(DataScope.COMPANY)) {
            return 1;
        }
        if (scope.equals(DataScope.OWN)) {
            if (ownerUserId == userId) {
                return 1;
            }
            return 0;
        }
        if (scope.equals(DataScope.DEPARTMENT)) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(userId));
            p.add(CryptoParse.longToString(companyId));
            Object dept = ErpDb.queryOne(
                    "SELECT department_id FROM users WHERE id=? AND company_id=?", p, new LongMapper());
            if (ErpNum.asLong(dept) == 0L) {
                return 0;
            }
            if (ErpNum.asLong(dept) == ownerDeptId) {
                return 1;
            }
            return 0;
        }
        return 0;
    }

    static void requirePermissionOrThrow(long userId, long companyId, String perm) {
        if (hasPermission(userId, companyId, perm) == 0) {
            throw new TransactionException("FORBIDDEN:" + perm);
        }
    }
}
