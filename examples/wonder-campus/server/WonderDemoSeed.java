/**
 * TEST-ONLY demo seed for mobile loop. All names marked TEST — no real children.
 * Six campuses / four subjects; demo focus on two campuses + two kids.
 * @author 张晓达
 */
class WonderDemoSeed {
    static long orgId;
    static long billingA;
    static long billingB;
    static long campusSun;
    static long campusStar;
    static long adminId;
    static long teacherHome;
    static long teacherCross;
    static long parentId;
    static long studentA;
    static long studentB;
    static long engCourse;
    static long artCourse;
    static long cnCourse;
    static long mathCourse;
    static long engClass;
    static long artClass;
    static long roomSun1;
    static long roomStar1;
    static long packContract;
    static long termContract;
    static long expiredContract;
    static long lowBalContract;

    static void seedAll() {
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add("WC-DEMO");
        Object exist = WcDb.queryOne("SELECT id FROM wc_orgs WHERE code=?", chk, new LongMapper());
        if (exist != null) {
            orgId = WcNum.asLong(exist);
            return;
        }
        orgId = WcServices.createOrg("WC-DEMO", "TEST奇想运营组织（非收款主体）");
        billingA = WcServices.createBillingEntity(orgId, "BE-A", "TEST收款主体-阳光");
        billingB = WcServices.createBillingEntity(orgId, "BE-B", "TEST收款主体-星河");
        /* 六校区骨架；演示聚焦阳光/星河 */
        campusSun = WcServices.createCampus(orgId, "SUN", "TEST阳光校区");
        campusStar = WcServices.createCampus(orgId, "STAR", "TEST星河校区");
        WcServices.createCampus(orgId, "MOON", "TEST月华校区");
        WcServices.createCampus(orgId, "CLOUD", "TEST云起校区");
        WcServices.createCampus(orgId, "RIVER", "TEST江畔校区");
        WcServices.createCampus(orgId, "HILL", "TEST山丘校区");
        adminId = WcServices.createUser(orgId, "admin", "TEST教务");
        teacherHome = WcServices.createUser(orgId, "teacher1", "TEST专职老师");
        teacherCross = WcServices.createUser(orgId, "teacher_x", "TEST跨校区老师");
        parentId = WcServices.createUser(orgId, "parent1", "TEST家长甲");
        WcServices.addIdentity(adminId, orgId, WcIdentity.ADMIN, campusSun);
        WcServices.addIdentity(adminId, orgId, WcIdentity.DIRECTOR, campusSun);
        WcServices.addIdentity(adminId, orgId, WcIdentity.TEACHER, campusSun);
        WcServices.addIdentity(teacherHome, orgId, WcIdentity.TEACHER, campusSun);
        WcServices.addIdentity(teacherCross, orgId, WcIdentity.TEACHER, campusSun);
        WcServices.addIdentity(teacherCross, orgId, WcIdentity.TEACHER, campusStar);
        WcServices.addIdentity(parentId, orgId, WcIdentity.PARENT, 0L);
        WcServices.grantStaffCampus(adminId, campusSun, "ADMIN");
        WcServices.grantStaffCampus(adminId, campusStar, "ADMIN");
        WcServices.grantStaffCampus(teacherHome, campusSun, "TEACHER");
        WcServices.grantStaffCampus(teacherCross, campusSun, "TEACHER");
        WcServices.grantStaffCampus(teacherCross, campusStar, "TEACHER");
        roomSun1 = WcServices.createRoom(campusSun, "S1", "TEST彩虹教室");
        roomStar1 = WcServices.createRoom(campusStar, "K1", "TEST星空教室");
        engCourse = WcServices.createCourse(orgId, "ENG", "TEST趣味英语", "英语");
        artCourse = WcServices.createCourse(orgId, "ART", "TEST奇想画画", "画画");
        cnCourse = WcServices.createCourse(orgId, "CN", "TEST语文表达", "语文");
        mathCourse = WcServices.createCourse(orgId, "MATH", "TEST数学思维", "数学");
        engClass = WcServices.createClass(orgId, campusSun, engCourse, "TEST英语启蒙班", teacherHome);
        artClass = WcServices.createClass(orgId, campusSun, artCourse, "TEST画画学期班", teacherHome);
        studentA = WcServices.createStudent(orgId, campusSun, "TEST小明", WcAgeBand.CHILD);
        studentB = WcServices.createStudent(orgId, campusSun, "TEST小华", WcAgeBand.TEEN);
        long g = WcServices.createGuardian(orgId, parentId, "TEST家长甲", "13800000000");
        WcServices.linkGuardian(studentA, g);
        WcServices.linkGuardian(studentB, g);
        WcLoopServices.ensureFeedbackTemplates(orgId);
        WcRuleSnapshot rules = WcRuleSnapshot.defaults();
        long expire = AsyncClockNative.wallMillis() + 86400000L * 180L;
        WcResult en = WcServices.enrollPack(orgId, campusSun, studentA, engCourse, engClass, 40000L, 0L, expire,
                400000L, 0L, adminId, "demo-enroll-eng", rules);
        packContract = WcServices.parseDocId(en.documentJson, "contractId");
        WcServices.setContractBillingEntity(packContract, billingA);
        WcServices.registerPayment(orgId, packContract, 400000L, WcPayMethod.WECHAT, "TEST-R-ENG", adminId,
                "demo-pay-eng");
        long termStart = AsyncClockNative.wallMillis();
        WcResult et = WcServices.enrollTerm(orgId, campusSun, studentB, artCourse, artClass, termStart,
                termStart + 86400000L * 90L, 16, 320000L, 20000L, adminId, "demo-enroll-art", rules);
        termContract = WcServices.parseDocId(et.documentJson, "contractId");
        WcServices.setContractBillingEntity(termContract, billingB);
        WcServices.registerPayment(orgId, termContract, 150000L, WcPayMethod.ALIPAY, "TEST-R-ART1", adminId,
                "demo-pay-art1");
        /* expired + low balance packs for negative demos */
        WcResult ex = WcServices.enrollPack(orgId, campusSun, studentA, engCourse, engClass, 1000L, 0L,
                AsyncClockNative.wallMillis() - 1000L, 10000L, 0L, adminId, "demo-enroll-expired", rules);
        expiredContract = WcServices.parseDocId(ex.documentJson, "contractId");
        WcResult low = WcServices.enrollPack(orgId, campusSun, studentA, engCourse, engClass, 500L, 0L, expire, 5000L,
                0L, adminId, "demo-enroll-low", rules);
        lowBalContract = WcServices.parseDocId(low.documentJson, "contractId");
        System.out.println("WONDER_DEMO seed org=" + CryptoParse.longToString(orgId)
                + " campuses=6 subjects=4 TEST data only");
    }
}
