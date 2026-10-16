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

    /** Commercial-module demo content (TEST data): products, leads, announcement, homework, past lesson. */
    static void seedCommercial() {
        if (orgId == 0L) {
            return;
        }
        resolveIds();
        if (WcApi.one("SELECT COUNT(*) FROM wc_products WHERE org_id=?", WcApi.P1(orgId)) > 0L) {
            return;
        }
        long now = AsyncClockNative.wallMillis();
        WcBizAdmin.settingPut(orgId, "contactPhone", "400-000-0000");
        WcBizAdmin.setRate(orgId, teacherHome, 8000L);
        WcBizAdmin.setRate(orgId, teacherCross, 9000L);
        insertProduct("TEST趣味英语 20课时包", engCourse, "PACK", 20000L, 2000L, 180L, 0L, 90L, 320000L);
        insertProduct("TEST趣味英语 10课时体验包", engCourse, "PACK", 10000L, 0L, 120L, 0L, 90L, 180000L);
        insertProduct("TEST奇想画画 学期班(16次)", artCourse, "TERM", 0L, 0L, 0L, 16L, 90L, 320000L);
        insertProduct("TEST数学思维 20课时包", mathCourse, "PACK", 20000L, 1000L, 180L, 0L, 90L, 360000L);
        ArrayList<Object> cm = new ArrayList<Object>();
        cm.add(WcApi.l(engClass));
        cm.add(WcApi.l(studentA));
        cm.add(WcApi.l(now));
        WcDb.exec("INSERT OR IGNORE INTO wc_class_students(class_id, student_id, joined_at) VALUES (?,?,?)", cm);
        ArrayList<Object> cm2 = new ArrayList<Object>();
        cm2.add(WcApi.l(artClass));
        cm2.add(WcApi.l(studentB));
        cm2.add(WcApi.l(now));
        WcDb.exec("INSERT OR IGNORE INTO wc_class_students(class_id, student_id, joined_at) VALUES (?,?,?)", cm2);
        insertLead("TEST小宇", 6L, "TEST王女士", "13900000001", "REFERRAL", "NEW", engCourse, now + 3600000L);
        insertLead("TEST朵朵", 5L, "TEST李先生", "13900000002", "WALK_IN", "CONTACTED", artCourse, now + 86400000L);
        insertLead("TEST乐乐", 8L, "TEST张女士", "13900000003", "ONLINE", "TRIAL_BOOKED", mathCourse,
                now + 2L * 86400000L);
        ArrayList<Object> an = new ArrayList<Object>();
        an.add(WcApi.l(orgId));
        an.add("ALL");
        an.add("TEST 周末亲子开放日");
        an.add("本周六 10:00 阳光校区举办亲子开放日，欢迎带孩子体验画画与英语游戏课，请提前向教务报名。");
        an.add("1");
        an.add(WcApi.l(adminId));
        an.add(WcApi.l(now));
        WcDb.exec(
                "INSERT INTO wc_announcements(org_id, audience, title, body, pinned, created_by, created_at) VALUES (?,?,?,?,?,?,?)",
                an);
        ArrayList<Object> hw = new ArrayList<Object>();
        hw.add(WcApi.l(orgId));
        hw.add(WcApi.l(engClass));
        hw.add("TEST 本周单词跟读");
        hw.add("跟读 5 个动物单词并录一段 30 秒音频或写下你最喜欢的一个。");
        hw.add(WcApi.l(now + 3L * 86400000L));
        hw.add(WcApi.l(teacherHome));
        hw.add(WcApi.l(now));
        WcDb.exec(
                "INSERT INTO wc_homework(org_id, class_id, title, body, due_at, created_by, created_at) VALUES (?,?,?,?,?,?,?)",
                hw);
        /* a completed past lesson so reviews / payroll / attendance have real rows */
        long t0 = now - 2L * 86400000L;
        WcResult s = WcServices.createSession(orgId, campusSun, engClass, teacherHome, roomSun1, t0, t0 + 3600000L,
                studentA, packContract, 0L);
        if (s.ok != 0) {
            long sid = WcServices.parseDocId(s.documentJson, "sessionId");
            WcServices.checkIn(orgId, sid, studentA, adminId, "seed-past-checkin");
            WcLoopServices.completeSession(orgId, sid, adminId);
        }
        System.out.println("WONDER_DEMO commercial seed OK");
    }

    static long byLogin(String login) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(login);
        return WcApi.one("SELECT id FROM wc_users WHERE org_id=? AND login=?", p);
    }

    static long byCode(String table, String code) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(code);
        return WcApi.one("SELECT id FROM " + table + " WHERE org_id=? AND code=?", p);
    }

    static long byName(String table, String name) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(name);
        return WcApi.one("SELECT id FROM " + table + " WHERE org_id=? AND name=?", p);
    }

    /** seedAll returns early on an existing DB, leaving static ids at 0; re-resolve them by stable keys. */
    static void resolveIds() {
        if (adminId > 0L) {
            return;
        }
        adminId = byLogin("admin");
        teacherHome = byLogin("teacher1");
        teacherCross = byLogin("teacher_x");
        parentId = byLogin("parent1");
        campusSun = byCode("wc_campuses", "SUN");
        campusStar = byCode("wc_campuses", "STAR");
        engCourse = byCode("wc_courses", "ENG");
        artCourse = byCode("wc_courses", "ART");
        cnCourse = byCode("wc_courses", "CN");
        mathCourse = byCode("wc_courses", "MATH");
        engClass = byName("wc_classes", "TEST英语启蒙班");
        artClass = byName("wc_classes", "TEST画画学期班");
        studentA = byName("wc_students", "TEST小明");
        studentB = byName("wc_students", "TEST小华");
        roomSun1 = WcApi.one("SELECT id FROM wc_rooms WHERE campus_id=? ORDER BY id LIMIT 1", WcApi.P1(campusSun));
        packContract = WcApi.one(
                "SELECT id FROM wc_contracts WHERE student_id=? AND billing_mode='PACK' ORDER BY id LIMIT 1",
                WcApi.P1(studentA));
    }

    static void insertProduct(String name, long courseId, String kind, long paid, long bonus, long validDays,
            long termSessions, long termDays, long price) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(WcApi.l(courseId));
        p.add(name);
        p.add(kind);
        p.add(WcApi.l(paid));
        p.add(WcApi.l(bonus));
        p.add(WcApi.l(validDays));
        p.add(WcApi.l(termSessions));
        p.add(WcApi.l(termDays));
        p.add(WcApi.l(price));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        WcDb.exec(
                "INSERT INTO wc_products(org_id, course_id, name, kind, paid_milli, bonus_milli, valid_days, term_sessions, term_days, price_cents, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                p);
    }

    static void insertLead(String child, long age, String guardian, String phone, String source, String status,
            long courseId, long nextFollow) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(WcApi.l(campusSun));
        p.add(child);
        p.add(WcApi.l(age));
        p.add(guardian);
        p.add(phone);
        p.add(source);
        p.add(status);
        p.add(WcApi.l(courseId));
        p.add(WcApi.l(adminId));
        p.add(WcApi.l(nextFollow));
        p.add(WcApi.l(now));
        p.add(WcApi.l(now));
        WcDb.exec(
                "INSERT INTO wc_leads(org_id, campus_id, child_name, child_age, guardian_name, phone, source, status, intent_course_id, owner_user_id, next_follow_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
    }

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
