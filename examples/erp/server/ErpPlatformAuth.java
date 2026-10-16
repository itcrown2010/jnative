/**
 * Enterprise authorization: MODULE.RESOURCE.ACTION + data scope OWN/DEPARTMENT/COMPANY.
 * Server-side only — UI hide is not authorization.
 * @author 张晓达
 */
class ErpPlatformAuth {
    static void ensureSeed(long companyId) {
        seedPermission("SALES.ORDER.READ", "Read sales orders");
        seedPermission("SALES.ORDER.CREATE", "Create sales orders");
        seedPermission("SALES.ORDER.APPROVE", "Approve sales orders");
        seedPermission("SALES.ORDER.POST", "Post sales/delivery");
        seedPermission("SALES.DELIVERY.REVERSE", "Reverse deliveries");
        seedPermission("SALES.CUSTOMER.READ", "Read customers");
        seedPermission("FINANCE.JOURNAL.READ", "Read journals");
        seedPermission("FINANCE.JOURNAL.POST", "Post journals");
        seedPermission("FINANCE.OPENING.POST", "Post opening AR/AP");
        seedPermission("FINANCE.PAYMENT.CREATE", "Create receipts/payments");
        seedPermission("FINANCE.PAYMENT.POST", "Post receipts/payments");
        seedPermission("FINANCE.PAYMENT.ALLOCATE", "Allocate receipts/payments");
        seedPermission("FINANCE.PAYMENT.REVERSE", "Reverse allocations / refunds");
        seedPermission("FINANCE.PAYMENT.VOID", "Void entire receipts/payments");
        seedPermission("PURCHASE.ORDER.READ", "Read POs");
        seedPermission("PURCHASE.ORDER.CREATE", "Create POs");
        seedPermission("PURCHASE.ORDER.APPROVE", "Approve POs");
        seedPermission("PURCHASE.ORDER.POST", "Post goods receipts");
        seedPermission("PURCHASE.GR.REVERSE", "Reverse goods receipts");
        seedPermission("INVENTORY.AVAILABLE.READ", "Read available qty (no valuation)");
        seedPermission("INVENTORY.BALANCE.READ", "Read inventory balances and valuation");
        seedPermission("INVENTORY.OPENING.POST", "Post opening stock");
        seedPermission("ADMIN.USER.READ", "Read users");
        seedPermission("ADMIN.ROLE.MANAGE", "Manage roles");
        seedPermission("ADMIN.CUSTOMFIELD.MANAGE", "Manage custom fields");
        seedPermission("ADMIN.FORM.MANAGE", "Manage forms");
        seedPermission("ADMIN.WORKFLOW.MANAGE", "Manage workflows");
        seedPermission("ADMIN.REPORT.RUN", "Run reports");
        seedPermission("ADMIN.IMPORT.RUN", "Run imports");
        seedPermission("ADMIN.SETTING.MANAGE", "Manage settings");
        seedPermission("ADMIN.AUDIT.READ", "Read audit");
        seedPermission("ADMIN.SETUP.MANAGE", "Run setup wizard");
        seedPermission("ADMIN.DEMO.RESET", "Reset demo book");

        long salesRole = ensureRole(companyId, "sales", "Sales", DataScope.OWN);
        long financeRole = ensureRole(companyId, "finance", "Finance", DataScope.COMPANY);
        long purchaseRole = ensureRole(companyId, "purchase", "Purchase", DataScope.DEPARTMENT);
        long adminRole = ensureRole(companyId, "admin", "Administrator", DataScope.COMPANY);

        grant(salesRole, "SALES.ORDER.READ");
        grant(salesRole, "SALES.ORDER.CREATE");
        grant(salesRole, "SALES.ORDER.APPROVE");
        grant(salesRole, "SALES.ORDER.POST");
        grant(salesRole, "SALES.CUSTOMER.READ");
        /* Sales needs available qty for SO lines; valuation stays finance/warehouse. */
        grant(salesRole, "INVENTORY.AVAILABLE.READ");
        grant(financeRole, "FINANCE.JOURNAL.READ");
        grant(financeRole, "FINANCE.JOURNAL.POST");
        grant(financeRole, "FINANCE.OPENING.POST");
        grant(financeRole, "FINANCE.PAYMENT.CREATE");
        grant(financeRole, "FINANCE.PAYMENT.POST");
        grant(financeRole, "FINANCE.PAYMENT.ALLOCATE");
        grant(financeRole, "FINANCE.PAYMENT.REVERSE");
        grant(financeRole, "FINANCE.PAYMENT.VOID");
        grant(financeRole, "ADMIN.REPORT.RUN");
        grant(purchaseRole, "PURCHASE.ORDER.READ");
        grant(purchaseRole, "PURCHASE.ORDER.CREATE");
        grant(purchaseRole, "PURCHASE.ORDER.APPROVE");
        grant(purchaseRole, "PURCHASE.ORDER.POST");
        grant(purchaseRole, "INVENTORY.AVAILABLE.READ");
        grant(purchaseRole, "INVENTORY.BALANCE.READ");
        grantAllBusiness(adminRole);
    }

    /** Full business + admin surface for Administrator role. */
    static void grantAllBusiness(long roleId) {
        grant(roleId, "SALES.ORDER.READ");
        grant(roleId, "SALES.ORDER.CREATE");
        grant(roleId, "SALES.ORDER.APPROVE");
        grant(roleId, "SALES.ORDER.POST");
        grant(roleId, "SALES.DELIVERY.REVERSE");
        grant(roleId, "SALES.CUSTOMER.READ");
        grant(roleId, "FINANCE.JOURNAL.READ");
        grant(roleId, "FINANCE.JOURNAL.POST");
        grant(roleId, "FINANCE.OPENING.POST");
        grant(roleId, "FINANCE.PAYMENT.CREATE");
        grant(roleId, "FINANCE.PAYMENT.POST");
        grant(roleId, "FINANCE.PAYMENT.ALLOCATE");
        grant(roleId, "FINANCE.PAYMENT.REVERSE");
        grant(roleId, "FINANCE.PAYMENT.VOID");
        grant(roleId, "PURCHASE.ORDER.READ");
        grant(roleId, "PURCHASE.ORDER.CREATE");
        grant(roleId, "PURCHASE.ORDER.APPROVE");
        grant(roleId, "PURCHASE.ORDER.POST");
        grant(roleId, "PURCHASE.GR.REVERSE");
        grant(roleId, "INVENTORY.AVAILABLE.READ");
        grant(roleId, "INVENTORY.BALANCE.READ");
        grant(roleId, "INVENTORY.OPENING.POST");
        grant(roleId, "ADMIN.USER.READ");
        grant(roleId, "ADMIN.ROLE.MANAGE");
        grant(roleId, "ADMIN.CUSTOMFIELD.MANAGE");
        grant(roleId, "ADMIN.FORM.MANAGE");
        grant(roleId, "ADMIN.WORKFLOW.MANAGE");
        grant(roleId, "ADMIN.REPORT.RUN");
        grant(roleId, "ADMIN.IMPORT.RUN");
        grant(roleId, "ADMIN.SETTING.MANAGE");
        grant(roleId, "ADMIN.AUDIT.READ");
        grant(roleId, "ADMIN.SETUP.MANAGE");
        grant(roleId, "ADMIN.DEMO.RESET");
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

    /** Workflow assignments name roles, not interchangeable business permissions. */
    static int hasRole(long userId, long companyId, String roleCode) {
        if (userId <= 0L || companyId <= 0L || roleCode == null || roleCode.length() == 0) {
            return 0;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(companyId));
        p.add(roleCode);
        p.add(CryptoParse.longToString(companyId));
        p.add(roleCode);
        Object count = ErpDb.queryOne(
                "SELECT COUNT(*) FROM users u LEFT JOIN platform_user_roles ur ON ur.user_id=u.id LEFT JOIN platform_roles r ON r.id=ur.role_id WHERE u.id=? AND u.company_id=? AND (u.role=? OR (r.company_id=? AND r.code=?))",
                p, new LongMapper());
        if (ErpNum.asLong(count) > 0L) {
            return 1;
        }
        return 0;
    }

    static int hasPermission(long userId, long companyId, String permCode) {
        /* Legacy users.role=admin retains full access for pilot continuity */
        ArrayList<Object> ur = new ArrayList<Object>();
        ur.add(CryptoParse.longToString(userId));
        ur.add(CryptoParse.longToString(companyId));
        Object role = ErpDb.queryOne("SELECT role FROM users WHERE id=? AND company_id=?", ur, new StringMapper());
        if (role != null && ((String) role).equals("admin")) {
            return 1;
        }
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
