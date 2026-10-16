/**
 * Wonder Campus E2E: Scenario A (English pack) + Scenario B (Art term) + negatives.
 * @author 张晓达
 */
class WonderE2eTest {
    static long orgId;
    static long campusA;
    static long campusB;
    static long adminId;
    static long teacherId;
    static long parentId;
    static long strangerParentId;
    static long room1;
    static long room2;
    static long engCourse;
    static long artCourse;
    static long engClass;
    static long artClass;
    static long studentEng;
    static long studentArt;
    static long guardianId;

    static int runAll() {
        int pass = 1;
        pass = seed(pass);
        if (pass == 0) {
            System.out.println("FAIL seed");
            return 0;
        }
        pass = scenarioA(pass);
        if (pass == 0) {
            System.out.println("FAIL scenarioA");
            return 0;
        }
        pass = scenarioB(pass);
        if (pass == 0) {
            System.out.println("FAIL scenarioB");
            return 0;
        }
        pass = negatives(pass);
        if (pass == 0) {
            System.out.println("FAIL negatives");
            return 0;
        }
        System.out.println("PASS wonder_campus_e2e");
        return 1;
    }

    static int seed(int pass) {
        orgId = WcServices.createOrg("WC1", "奇想校园演示机构");
        campusA = WcServices.createCampus(orgId, "CA", "阳光校区");
        campusB = WcServices.createCampus(orgId, "CB", "星河校区");
        adminId = WcServices.createUser(orgId, "admin", "教务小奇");
        teacherId = WcServices.createUser(orgId, "teacher1", "画画老师");
        parentId = WcServices.createUser(orgId, "parent1", "家长甲");
        strangerParentId = WcServices.createUser(orgId, "parent2", "家长乙");
        WcServices.addIdentity(adminId, orgId, WcIdentity.ADMIN, campusA);
        WcServices.addIdentity(teacherId, orgId, WcIdentity.TEACHER, campusA);
        WcServices.addIdentity(parentId, orgId, WcIdentity.PARENT, 0L);
        WcServices.addIdentity(adminId, orgId, WcIdentity.TEACHER, campusA);
        WcServices.grantStaffCampus(adminId, campusA, "ADMIN");
        WcServices.grantStaffCampus(teacherId, campusA, "TEACHER");
        room1 = WcServices.createRoom(campusA, "R1", "彩虹教室");
        room2 = WcServices.createRoom(campusA, "R2", "星空教室");
        engCourse = WcServices.createCourse(orgId, "ENG", "趣味英语", "英语");
        artCourse = WcServices.createCourse(orgId, "ART", "奇想画画", "画画");
        engClass = WcServices.createClass(orgId, campusA, engCourse, "英语启蒙班", teacherId);
        artClass = WcServices.createClass(orgId, campusA, artCourse, "画画学期班", teacherId);
        studentEng = WcServices.createStudent(orgId, campusA, "小明", WcAgeBand.CHILD);
        studentArt = WcServices.createStudent(orgId, campusA, "小华", WcAgeBand.TEEN);
        guardianId = WcServices.createGuardian(orgId, parentId, "家长甲", "13800000000");
        WcServices.linkGuardian(studentEng, guardianId);
        WcServices.linkGuardian(studentArt, guardianId);
        System.out.println("OK seed org=" + CryptoParse.longToString(orgId));
        return pass;
    }

    static int scenarioA(int pass) {
        System.out.println("STEP A english pack");
        WcRuleSnapshot rules = WcRuleSnapshot.defaults();
        rules.sessionConsumeMilli = 1000L;
        rules.leaveConsumes = 0;
        long expire = AsyncClockNative.wallMillis() + 86400000L * 180L;
        WcResult en = WcServices.enrollPack(orgId, campusA, studentEng, engCourse, engClass, 40000L, 0L, expire,
                400000L, 0L, adminId, "enroll-eng-pack", rules);
        if (en.ok == 0) {
            System.out.println("FAIL A enroll " + en.message);
            return 0;
        }
        long contractId = WcServices.parseDocId(en.documentJson, "contractId");
        WcResult pay = WcServices.registerPayment(orgId, contractId, 400000L, WcPayMethod.WECHAT, "R-ENG-1",
                adminId, "pay-eng-1");
        if (pay.ok == 0) {
            System.out.println("FAIL A pay " + pay.message);
            return 0;
        }
        long t0 = AsyncClockNative.wallMillis() + 86400000L;
        WcResult s1 = WcServices.createSession(orgId, campusA, engClass, teacherId, room1, t0, t0 + 3600000L,
                studentEng, contractId, 0L);
        if (s1.ok == 0) {
            System.out.println("FAIL A schedule " + s1.message);
            return 0;
        }
        long sessionId = WcServices.parseDocId(s1.documentJson, "sessionId");
        WcResult conflict = WcServices.createSession(orgId, campusA, engClass, teacherId, room2, t0,
                t0 + 3600000L, studentEng, contractId, 0L);
        if (conflict.ok != 0) {
            System.out.println("FAIL A expected conflict");
            return 0;
        }
        System.out.println("OK A conflict rejected");
        long before = WcServices.packRemaining(contractId);
        WcResult cin = WcServices.checkIn(orgId, sessionId, studentEng, teacherId, "checkin-eng-1");
        if (cin.ok == 0) {
            System.out.println("FAIL A checkin " + cin.message);
            return 0;
        }
        long after = WcServices.packRemaining(contractId);
        if (before - after != 1000L) {
            System.out.println("FAIL A consume want 1000 got "
                    + CryptoParse.longToString(before - after));
            return 0;
        }
        WcResult dup = WcServices.checkIn(orgId, sessionId, studentEng, teacherId, "checkin-eng-2");
        if (dup.ok != 0) {
            System.out.println("FAIL A duplicate checkin should fail");
            return 0;
        }
        if (WcServices.packRemaining(contractId) != after) {
            System.out.println("FAIL A duplicate consumed again");
            return 0;
        }
        System.out.println("OK A checkin + idempotent reject");
        long leaveSessionStart = t0 + 86400000L * 3L;
        WcResult sLeave = WcServices.createSession(orgId, campusA, engClass, teacherId, room1, leaveSessionStart,
                leaveSessionStart + 3600000L, studentEng, contractId, 0L);
        long leaveSid = WcServices.parseDocId(sLeave.documentJson, "sessionId");
        WcResult leave = WcServices.requestLeave(orgId, leaveSid, studentEng, parentId, "外出", "leave-eng-1");
        if (leave.ok == 0) {
            System.out.println("FAIL A leave " + leave.message);
            return 0;
        }
        long leaveId = WcServices.parseDocId(leave.documentJson, "leaveId");
        long makeupStart = leaveSessionStart + 86400000L * 2L;
        WcResult mk = WcServices.approveLeaveAndMakeup(orgId, leaveId, makeupStart, makeupStart + 3600000L,
                teacherId, room2, adminId);
        if (mk.ok == 0) {
            System.out.println("FAIL A makeup " + mk.message);
            return 0;
        }
        long makeupId = WcServices.parseDocId(mk.documentJson, "makeupSessionId");
        long balBeforeRev = WcServices.packRemaining(contractId);
        long attId = WcServices.parseDocId(cin.documentJson, "attendanceId");
        WcResult rev = WcServices.reverseCheckIn(orgId, attId, adminId, "rev-eng-1");
        if (rev.ok == 0) {
            System.out.println("FAIL A reverse " + rev.message);
            return 0;
        }
        if (WcServices.packRemaining(contractId) != balBeforeRev + 1000L) {
            System.out.println("FAIL A reverse balance");
            return 0;
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(contractId));
        Object ledCnt = WcDb.queryOne("SELECT COUNT(*) FROM wc_lesson_ledger WHERE contract_id=?", lp,
                new LongMapper());
        if (WcNum.asLong(ledCnt) < 3L) {
            System.out.println("FAIL A ledger rows");
            return 0;
        }
        if (WcServices.isGuardianOf(parentId, studentEng) == 0) {
            System.out.println("FAIL A parent link");
            return 0;
        }
        System.out.println("OK A pack flow remainingMilli="
                + CryptoParse.longToString(WcServices.packRemaining(contractId))
                + " makeupSession=" + CryptoParse.longToString(makeupId));
        return pass;
    }

    static int scenarioB(int pass) {
        System.out.println("STEP B art term");
        WcRuleSnapshot rules = WcRuleSnapshot.defaults();
        long termStart = AsyncClockNative.wallMillis();
        long termEnd = termStart + 86400000L * 90L;
        WcResult en = WcServices.enrollTerm(orgId, campusA, studentArt, artCourse, artClass, termStart, termEnd,
                16, 320000L, 20000L, adminId, "enroll-art-term", rules);
        if (en.ok == 0) {
            System.out.println("FAIL B enroll " + en.message);
            return 0;
        }
        long contractId = WcServices.parseDocId(en.documentJson, "contractId");
        WcResult p1 = WcServices.registerPayment(orgId, contractId, 150000L, WcPayMethod.ALIPAY, "R-ART-1",
                adminId, "pay-art-1");
        WcResult p2 = WcServices.registerPayment(orgId, contractId, 150000L, WcPayMethod.CASH, "R-ART-2", adminId,
                "pay-art-2");
        if (p1.ok == 0 || p2.ok == 0) {
            System.out.println("FAIL B payments");
            return 0;
        }
        long t1 = termStart + 86400000L * 2L;
        WcResult s1 = WcServices.createSession(orgId, campusA, artClass, teacherId, room1, t1, t1 + 5400000L,
                studentArt, contractId, 0L);
        long sid = WcServices.parseDocId(s1.documentJson, "sessionId");
        WcResult cancel = WcServices.schoolCancelSession(orgId, sid, "校庆停课", adminId);
        if (cancel.ok == 0) {
            System.out.println("FAIL B school cancel");
            return 0;
        }
        long t2 = t1 + 86400000L * 7L;
        WcResult rs = WcServices.rescheduleSession(orgId, sid, t2, t2 + 5400000L, "改期补上", adminId);
        /* cancelled session may not reschedule — create new instead */
        if (rs.ok == 0) {
            WcResult s2 = WcServices.createSession(orgId, campusA, artClass, teacherId, room1, t2, t2 + 5400000L,
                    studentArt, contractId, 0L);
            if (s2.ok == 0) {
                System.out.println("FAIL B replacement session " + s2.message);
                return 0;
            }
            sid = WcServices.parseDocId(s2.documentJson, "sessionId");
        }
        long leaveStart = t2 + 86400000L * 3L;
        WcResult sLeave = WcServices.createSession(orgId, campusA, artClass, teacherId, room1, leaveStart,
                leaveStart + 5400000L, studentArt, contractId, 0L);
        long leaveSid = WcServices.parseDocId(sLeave.documentJson, "sessionId");
        WcResult leave = WcServices.requestLeave(orgId, leaveSid, studentArt, parentId, "身体不适", "leave-art-1");
        if (leave.ok == 0) {
            System.out.println("FAIL B leave " + leave.message);
            return 0;
        }
        long leaveId = WcServices.parseDocId(leave.documentJson, "leaveId");
        long makeupStart = leaveStart + 86400000L * 2L;
        WcResult mk = WcServices.approveLeaveAndMakeup(orgId, leaveId, makeupStart, makeupStart + 5400000L,
                teacherId, room2, adminId);
        if (mk.ok == 0) {
            System.out.println("FAIL B makeup " + mk.message);
            return 0;
        }
        WcResult cin = WcServices.checkIn(orgId, sid, studentArt, teacherId, "checkin-art-1");
        if (cin.ok == 0) {
            System.out.println("FAIL B checkin term " + cin.message);
            return 0;
        }
        WcResult fb = WcServices.publishFeedback(orgId, sid, studentArt, teacherId, "创造,表达",
                "今天大胆用了新的配色，继续保持！", "fb-art-1");
        if (fb.ok == 0) {
            System.out.println("FAIL B feedback");
            return 0;
        }
        long payId = WcServices.parseDocId(p2.documentJson, "paymentId");
        WcResult ref = WcServices.refundPayment(orgId, payId, 50000L, "部分退费", adminId, "refund-art-1");
        if (ref.ok == 0) {
            System.out.println("FAIL B refund " + ref.message);
            return 0;
        }
        WcResult ref2 = WcServices.refundPayment(orgId, payId, 200000L, "超额", adminId, "refund-art-2");
        if (ref2.ok != 0) {
            System.out.println("FAIL B expected refund exceed reject");
            return 0;
        }
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        Object promised = WcDb.queryOne("SELECT term_promised_sessions FROM wc_contracts WHERE id=?", cp,
                new LongMapper());
        Object attended = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_attendance WHERE contract_id=? AND status='PRESENT' AND reversed_at=0",
                cp, new LongMapper());
        Object left = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_attendance WHERE contract_id=? AND status='LEAVE'", cp,
                new LongMapper());
        System.out.println("OK B term promised=" + CryptoParse.longToString(WcNum.asLong(promised))
                + " present=" + CryptoParse.longToString(WcNum.asLong(attended))
                + " leave=" + CryptoParse.longToString(WcNum.asLong(left)));
        return pass;
    }

    static int negatives(int pass) {
        System.out.println("STEP negatives");
        if (WcServices.canAccessCampus(teacherId, campusB) != 0) {
            System.out.println("FAIL neg teacher campusB");
            return 0;
        }
        if (WcServices.isGuardianOf(strangerParentId, studentEng) != 0) {
            System.out.println("FAIL neg stranger parent");
            return 0;
        }
        WcResult badLeave = WcServices.requestLeave(orgId, 1L, studentEng, strangerParentId, "x", "bad-leave");
        if (badLeave.ok != 0) {
            System.out.println("FAIL neg leave should forbid");
            return 0;
        }
        /* expired pack */
        WcRuleSnapshot rules = WcRuleSnapshot.defaults();
        long past = AsyncClockNative.wallMillis() - 1000L;
        WcResult en = WcServices.enrollPack(orgId, campusA, studentEng, engCourse, engClass, 1000L, 0L, past,
                10000L, 0L, adminId, "enroll-expired", rules);
        long cid = WcServices.parseDocId(en.documentJson, "contractId");
        long t = AsyncClockNative.wallMillis() + 86400000L * 10L;
        WcResult s = WcServices.createSession(orgId, campusA, engClass, teacherId, room1, t, t + 3600000L,
                studentEng, cid, 0L);
        long sid = WcServices.parseDocId(s.documentJson, "sessionId");
        WcResult cin = WcServices.checkIn(orgId, sid, studentEng, teacherId, "checkin-expired");
        if (cin.ok != 0) {
            System.out.println("FAIL neg expired pack should reject");
            return 0;
        }
        /* duplicate payment idempotency */
        WcResult pay1 = WcServices.registerPayment(orgId, cid, 1000L, WcPayMethod.CASH, "R-X", adminId,
                "pay-dup");
        WcResult pay2 = WcServices.registerPayment(orgId, cid, 1000L, WcPayMethod.CASH, "R-X", adminId,
                "pay-dup");
        if (pay1.ok == 0 || pay2.ok == 0) {
            System.out.println("FAIL neg pay idem");
            return 0;
        }
        if (WcServices.parseDocId(pay1.documentJson, "paymentId")
                != WcServices.parseDocId(pay2.documentJson, "paymentId")) {
            System.out.println("FAIL neg pay idem different ids");
            return 0;
        }
        System.out.println("OK negatives");
        return pass;
    }
}
