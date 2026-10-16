/**
 * Mobile-loop E2E: schedule → leave → check-in → entitlement → feedback → parent view.
 * @author 张晓达
 */
class WonderMobileLoopTest {
    static int runAll() {
        WonderDemoSeed.seedAll();
        int pass = 1;
        pass = flow(pass);
        if (pass == 0) {
            return 0;
        }
        pass = negatives(pass);
        if (pass == 0) {
            return 0;
        }
        System.out.println("PASS wonder_campus_mobile_loop");
        return 1;
    }

    static int flow(int pass) {
        System.out.println("STEP mobile-loop flow");
        long orgId = WonderDemoSeed.orgId;
        long t0 = AsyncClockNative.wallMillis() + 86400000L * 2L;
        WcResult s1 = WcServices.createSession(orgId, WonderDemoSeed.campusSun, WonderDemoSeed.engClass,
                WonderDemoSeed.teacherHome, WonderDemoSeed.roomSun1, t0, t0 + 3600000L, WonderDemoSeed.studentA,
                WonderDemoSeed.packContract, 0L);
        if (s1.ok == 0) {
            System.out.println("FAIL schedule " + s1.message);
            return 0;
        }
        long sid = WcServices.parseDocId(s1.documentJson, "sessionId");
        WcResult conflict = WcServices.createSession(orgId, WonderDemoSeed.campusSun, WonderDemoSeed.engClass,
                WonderDemoSeed.teacherHome, WonderDemoSeed.roomStar1, t0, t0 + 3600000L, WonderDemoSeed.studentA,
                WonderDemoSeed.packContract, 0L);
        if (conflict.ok != 0 || conflict.message.indexOf("冲突") < 0) {
            System.out.println("FAIL conflict detail missing: " + conflict.message);
            return 0;
        }
        System.out.println("OK conflict detail=" + conflict.message);
        /* cross-campus without grant */
        WcResult x = WcServices.createSession(orgId, WonderDemoSeed.campusStar, WonderDemoSeed.engClass,
                WonderDemoSeed.teacherCross, WonderDemoSeed.roomStar1, t0 + 86400000L, t0 + 86400000L + 3600000L,
                WonderDemoSeed.studentA, WonderDemoSeed.packContract, 0L);
        if (x.ok != 0) {
            System.out.println("FAIL expected campus deny");
            return 0;
        }
        WcServices.grantCampusEntitlement(orgId, WonderDemoSeed.packContract, WonderDemoSeed.campusStar,
                WonderDemoSeed.adminId);
        WcResult x2 = WcServices.createSession(orgId, WonderDemoSeed.campusStar, WonderDemoSeed.engClass,
                WonderDemoSeed.teacherCross, WonderDemoSeed.roomStar1, t0 + 86400000L, t0 + 86400000L + 3600000L,
                WonderDemoSeed.studentA, WonderDemoSeed.packContract, 0L);
        if (x2.ok == 0) {
            System.out.println("FAIL after grant " + x2.message);
            return 0;
        }
        long leaveStart = t0 + 86400000L * 3L;
        WcResult sLeave = WcServices.createSession(orgId, WonderDemoSeed.campusSun, WonderDemoSeed.engClass,
                WonderDemoSeed.teacherHome, WonderDemoSeed.roomSun1, leaveStart, leaveStart + 3600000L,
                WonderDemoSeed.studentA, WonderDemoSeed.packContract, 0L);
        long leaveSid = WcServices.parseDocId(sLeave.documentJson, "sessionId");
        WcResult leave = WcServices.requestLeave(orgId, leaveSid, WonderDemoSeed.studentA, WonderDemoSeed.parentId,
                "TEST外出", "loop-leave-1");
        if (leave.ok == 0) {
            System.out.println("FAIL leave " + leave.message);
            return 0;
        }
        long leaveId = WcServices.parseDocId(leave.documentJson, "leaveId");
        WcResult mk = WcServices.approveLeaveAndMakeup(orgId, leaveId, leaveStart + 86400000L * 2L,
                leaveStart + 86400000L * 2L + 3600000L, WonderDemoSeed.teacherHome, WonderDemoSeed.roomSun1,
                WonderDemoSeed.adminId);
        if (mk.ok == 0) {
            System.out.println("FAIL makeup " + mk.message);
            return 0;
        }
        long before = WcServices.packRemaining(WonderDemoSeed.packContract);
        WcResult batch = WcLoopServices.batchCheckIn(orgId, sid, CryptoParse.longToString(WonderDemoSeed.studentA),
                WonderDemoSeed.teacherHome, "loop-batch-1");
        if (batch.ok == 0) {
            System.out.println("FAIL batch " + batch.message);
            return 0;
        }
        long after = WcServices.packRemaining(WonderDemoSeed.packContract);
        if (before - after != 1000L) {
            System.out.println("FAIL consume");
            return 0;
        }
        WcResult batch2 = WcLoopServices.batchCheckIn(orgId, sid, CryptoParse.longToString(WonderDemoSeed.studentA),
                WonderDemoSeed.teacherHome, "loop-batch-1");
        if (WcServices.packRemaining(WonderDemoSeed.packContract) != after) {
            System.out.println("FAIL duplicate batch consume");
            return 0;
        }
        System.out.println("OK batch " + batch.message);
        WcResult draft = WcLoopServices.saveFeedbackDraft(orgId, sid, WonderDemoSeed.studentA,
                WonderDemoSeed.teacherHome, "TEST探索故事", "TEST尝试造句", "TEST继续加油", "英语", "loop-fb-1");
        if (draft.ok == 0) {
            System.out.println("FAIL draft");
            return 0;
        }
        long fid = WcServices.parseDocId(draft.documentJson, "feedbackId");
        String parentBefore = WcLoopServices.parentFeedbackJson(WonderDemoSeed.parentId, WonderDemoSeed.studentA);
        if (parentBefore.indexOf("TEST探索故事") >= 0) {
            System.out.println("FAIL draft leaked to parent");
            return 0;
        }
        WcResult pub = WcLoopServices.publishFeedbackDraft(orgId, fid, WonderDemoSeed.teacherHome);
        if (pub.ok == 0) {
            System.out.println("FAIL publish");
            return 0;
        }
        String parentAfter = WcLoopServices.parentFeedbackJson(WonderDemoSeed.parentId, WonderDemoSeed.studentA);
        if (parentAfter.indexOf("TEST探索故事") < 0) {
            System.out.println("FAIL parent cannot see published");
            return 0;
        }
        System.out.println("OK feedback draft/publish");
        WcResult cancel = WcServices.schoolCancelSession(orgId, sid, "TEST停课", WonderDemoSeed.adminId);
        if (cancel.ok == 0) {
            System.out.println("FAIL cancel");
            return 0;
        }
        if (WcServices.packRemaining(WonderDemoSeed.packContract) != before) {
            System.out.println("FAIL cancel did not restore entitlement got="
                    + CryptoParse.longToString(WcServices.packRemaining(WonderDemoSeed.packContract))
                    + " want=" + CryptoParse.longToString(before));
            return 0;
        }
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(sid));
        Object activeAtt = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_attendance WHERE session_id=? AND reversed_at=0 AND status IN ('PRESENT','LATE') AND consumed_milli>0",
                ap, new LongMapper());
        if (WcNum.asLong(activeAtt) != 0L) {
            System.out.println("FAIL cancel left active consumed attendance");
            return 0;
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(WonderDemoSeed.packContract));
        Object revLed = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_lesson_ledger WHERE contract_id=? AND entry_type='REVERSE_CONSUME'",
                lp, new LongMapper());
        if (WcNum.asLong(revLed) < 1L) {
            System.out.println("FAIL missing reverse ledger after cancel");
            return 0;
        }
        ArrayList<Object> mc = new ArrayList<Object>();
        mc.add(CryptoParse.longToString(leaveId));
        Object mst = WcDb.queryOne("SELECT status FROM wc_makeup_credits WHERE leave_id=?", mc, new StringMapper());
        if (mst == null || ((String) mst).equals(WcMakeupStatus.USED) == false) {
            System.out.println("FAIL makeup credit not USED");
            return 0;
        }
        System.out.println("OK cancel restores entitlement + reverse ledger + makeup USED");
        return pass;
    }

    static int negatives(int pass) {
        System.out.println("STEP mobile-loop negatives");
        long orgId = WonderDemoSeed.orgId;
        long t = AsyncClockNative.wallMillis() + 86400000L * 20L;
        WcResult s = WcServices.createSession(orgId, WonderDemoSeed.campusSun, WonderDemoSeed.engClass,
                WonderDemoSeed.teacherHome, WonderDemoSeed.roomSun1, t, t + 3600000L, WonderDemoSeed.studentA,
                WonderDemoSeed.expiredContract, 0L);
        long sid = WcServices.parseDocId(s.documentJson, "sessionId");
        WcResult cin = WcServices.checkIn(orgId, sid, WonderDemoSeed.studentA, WonderDemoSeed.teacherHome,
                "loop-exp");
        if (cin.ok != 0) {
            System.out.println("FAIL expired should reject");
            return 0;
        }
        WcResult bad = WcServices.requestLeave(orgId, sid, WonderDemoSeed.studentA, WonderDemoSeed.adminId, "x",
                "loop-bad-parent");
        if (bad.ok != 0) {
            System.out.println("FAIL non-parent leave");
            return 0;
        }
        String leak = WcLoopServices.parentFeedbackJson(WonderDemoSeed.adminId, WonderDemoSeed.studentA);
        if (leak.indexOf("\"ok\":1") >= 0) {
            System.out.println("FAIL non-guardian feedback list");
            return 0;
        }
        System.out.println("OK negatives");
        return pass;
    }
}
