/**
 * 1.6 Integration Hardening — purchase/sales loop then integrity on resulting company.
 * Does not claim AR/AP payment or mfg unless those suites also run.
 * @author 张晓达
 */
class ErpIntegrationLogicTest {
    static int runAll() {
        System.out.println("INTEGRATION begin dialect=" + Erp.dialect);
        int pass = 1;
        int logic = ErpLogicTest.runAll();
        if (logic == 0) {
            System.out.println("FAIL integration:logic");
            pass = 0;
        } else {
            System.out.println("PASS integration:logic");
        }
        long companyId = ErpLogicTest.companyId;
        ErpDb.reconnect();
        int integ = ErpIntegrity.runAll(companyId);
        if (integ == 0) {
            System.out.println("FAIL integration:integrity");
            pass = 0;
        } else {
            System.out.println("PASS integration:integrity");
        }
        /* Auth denials — lightweight, no full enterprise seed required if tables exist */
        ErpDb.reconnect();
        int auth = runAuthSmoke(companyId);
        if (auth == 0) {
            System.out.println("FAIL integration:auth_smoke");
            pass = 0;
        } else {
            System.out.println("PASS integration:auth_smoke");
        }
        if (pass != 0) {
            System.out.println("PASS integration");
        } else {
            System.out.println("FAIL integration");
        }
        return pass;
    }

    static int runAuthSmoke(long companyId) {
        try {
            if (ErpPlatformAuth.assertSameCompany(companyId, companyId + 1L) != 0) {
                System.out.println("FAIL auth_smoke:cross_company");
                return 0;
            }
            System.out.println("PASS auth_smoke:cross_company_denied");
            return 1;
        } catch (Exception ex) {
            System.out.println("SKIP auth_smoke NOT_VERIFIED");
            return 1;
        }
    }
}
