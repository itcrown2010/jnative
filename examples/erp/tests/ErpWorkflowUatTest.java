/** Regression: business approval permissions cannot satisfy an assigned workflow role. */
class ErpWorkflowUatTest {
    static int runAll() {
        ErpEnterpriseLogicTest.companyCode = "WU" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        ErpEnterpriseLogicTest.seed();
        long company = ErpEnterpriseLogicTest.companyA;
        long admin = ErpEnterpriseLogicTest.adminUser;
        long sales = ErpEnterpriseLogicTest.salesUser;
        if (ErpPlatformAuth.hasPermission(sales, company, "SALES.ORDER.APPROVE") == 0) {
            System.out.println("FAIL workflow fixture requires sales approval permission");
            return 0;
        }
        if (ErpPlatformAuth.hasRole(sales, company, "admin") != 0
                || ErpPlatformAuth.hasRole(admin, ErpEnterpriseLogicTest.companyB, "admin") != 0) {
            System.out.println("FAIL workflow role/company boundary");
            return 0;
        }
        int pass = ErpEnterpriseLogicTest.runWorkflow(1);
        if (pass == 0) {
            return 0;
        }
        long instance = ErpPlatformWorkflow.startInstance(company, "SO_APPROVE", "SALES_ORDER", 9901L,
                100L, sales, 1, "admin");
        int denied = 0;
        try {
            ErpPlatformWorkflow.act(company, instance, admin, "INVALID", "invalid", 100L);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0 || ErpPlatformWorkflow.instanceStatus(company, instance).equals(WorkflowStatus.IN_APPROVAL) == false) {
            System.out.println("FAIL invalid action changed workflow");
            return 0;
        }
        denied = 0;
        try {
            ErpPlatformWorkflow.act(company, instance, ErpEnterpriseLogicTest.adminB, "APPROVE", "cross company", 100L);
        } catch (Exception ex) {
            denied = 1;
        }
        if (denied == 0) {
            System.out.println("FAIL cross-company workflow approval");
            return 0;
        }
        if (ErpPlatformWorkflow.act(company, instance, admin, "APPROVE", "authorized", 100L) != 2) {
            System.out.println("FAIL authorized role approval");
            return 0;
        }
        System.out.println("WORKFLOW_UAT_PASS");
        return 1;
    }
}
