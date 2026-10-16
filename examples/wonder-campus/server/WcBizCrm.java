/**
 * Lead CRM: enquiry → follow-up → trial → convert to student → enroll.
 * Status machine: NEW → CONTACTED → TRIAL_BOOKED → TRIAL_DONE → WON | LOST.
 * @author 张晓达
 */
class WcBizCrm {
    static String OPS = "ADMIN,DIRECTOR";

    static int validOpenStatus(String s) {
        if (s.equals("NEW") || s.equals("CONTACTED") || s.equals("TRIAL_BOOKED") || s.equals("TRIAL_DONE")) {
            return 1;
        }
        return 0;
    }

    static String leadsList(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        String sql = "SELECT l.id, l.child_name, l.child_age, l.guardian_name, l.phone, l.source, l.status, l.next_follow_at, l.updated_at, l.owner_user_id, COALESCE(u.display_name,''), COALESCE(co.name,''), l.created_at FROM wc_leads l LEFT JOIN wc_users u ON u.id=l.owner_user_id LEFT JOIN wc_courses co ON co.id=l.intent_course_id WHERE l.org_id=?";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        String st = c.qs("status");
        if (st.length() > 0) {
            if (st.equals("OPEN")) {
                sql = sql + " AND l.status IN ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE')";
            } else {
                sql = sql + " AND l.status=?";
                p.add(st);
            }
        }
        String kw = c.qs("q");
        if (kw.length() > 0) {
            sql = sql + " AND (l.child_name LIKE ? OR l.phone LIKE ? OR l.guardian_name LIKE ?)";
            p.add(WcApi.like(kw));
            p.add(WcApi.like(kw));
            p.add(WcApi.like(kw));
        }
        if (c.qs("due").equals("1")) {
            sql = sql + " AND l.next_follow_at>0 AND l.next_follow_at<=? AND l.status IN ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE')";
            p.add(WcApi.l(AsyncClockNative.wallMillis() + 86400000L));
        }
        sql = sql + " ORDER BY CASE WHEN l.next_follow_at>0 THEN 0 ELSE 1 END, l.next_follow_at, l.id DESC LIMIT 200";
        DbQueryResult r = WcDb.query(sql, p);
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,childName:s,childAge:l,guardianName:s,phone:s,source:s,status:s,nextFollowAt:l,updatedAt:l,ownerUserId:l,ownerName:s,intentCourse:s,createdAt:l"));
    }

    static String leadCreate(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        String child = c.bs("childName");
        String phone = c.bs("phone");
        if (child.length() == 0 || phone.length() < 6) {
            return c.fail(400, "INVALID_INPUT", "请填写孩子姓名和联系电话");
        }
        ArrayList<Object> dp = new ArrayList<Object>();
        dp.add(WcApi.l(c.es.orgId));
        dp.add(phone);
        long dup = WcApi.one(
                "SELECT id FROM wc_leads WHERE org_id=? AND phone=? AND status IN ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE') ORDER BY id LIMIT 1",
                dp);
        if (dup > 0L) {
            return c.fail(409, "DUPLICATE_LEAD", "该电话已有跟进中线索 #" + WcApi.l(dup));
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        p.add(WcApi.l(c.bl("campusId")));
        p.add(child);
        p.add(WcApi.l(c.bl("childAge")));
        p.add(c.bs("guardianName"));
        p.add(phone);
        p.add(c.body.getString("source", "WALK_IN"));
        p.add(WcApi.l(c.bl("intentCourseId")));
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(c.bl("nextFollowAt")));
        p.add(c.bs("note"));
        p.add(WcApi.l(now));
        p.add(WcApi.l(now));
        long id = WcDb.insertReturningId(
                "INSERT INTO wc_leads(org_id, campus_id, child_name, child_age, guardian_name, phone, source, intent_course_id, owner_user_id, next_follow_at, note, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
        WcServices.audit(c.es.orgId, c.es.userId, "LEAD_CREATE", "lead", id, child);
        return c.ok("\"leadId\":" + WcApi.l(id));
    }

    static String leadDetail(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long id = c.pid();
        DbQueryResult l = WcDb.query(
                "SELECT child_name, child_age, guardian_name, phone, source, intent_course_id, status, owner_user_id, next_follow_at, lost_reason, converted_student_id, note, campus_id, created_at FROM wc_leads WHERE id=? AND org_id=?",
                WcApi.P2(id, c.es.orgId));
        if (l.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "线索不存在");
        }
        Row w = l.rows.get(0);
        String head = "\"id\":" + WcApi.l(id) + ",\"childName\":" + WcApi.q(w.getString(0)) + ",\"childAge\":"
                + WcApi.l(w.getLong(1)) + ",\"guardianName\":" + WcApi.q(w.getString(2)) + ",\"phone\":"
                + WcApi.q(w.getString(3)) + ",\"source\":" + WcApi.q(w.getString(4)) + ",\"intentCourseId\":"
                + WcApi.l(w.getLong(5)) + ",\"status\":" + WcApi.q(w.getString(6)) + ",\"ownerUserId\":"
                + WcApi.l(w.getLong(7)) + ",\"nextFollowAt\":" + WcApi.l(w.getLong(8)) + ",\"lostReason\":"
                + WcApi.q(w.getString(9)) + ",\"convertedStudentId\":" + WcApi.l(w.getLong(10)) + ",\"note\":"
                + WcApi.q(w.getString(11)) + ",\"campusId\":" + WcApi.l(w.getLong(12)) + ",\"createdAt\":"
                + WcApi.l(w.getLong(13));
        DbQueryResult f = WcDb.query(
                "SELECT f.id, f.kind, f.content, f.created_at, COALESCE(u.display_name,'') FROM wc_lead_follows f LEFT JOIN wc_users u ON u.id=f.user_id WHERE f.lead_id=? ORDER BY f.id DESC",
                WcApi.P1(id));
        DbQueryResult t = WcDb.query(
                "SELECT t.id, t.start_at, t.status, t.result_note, COALESCE(co.name,''), COALESCE(u.display_name,'') FROM wc_trials t LEFT JOIN wc_courses co ON co.id=t.course_id LEFT JOIN wc_users u ON u.id=t.teacher_user_id WHERE t.lead_id=? ORDER BY t.id DESC",
                WcApi.P1(id));
        return c.ok(head + ",\"follows\":" + WcApi.rows(f, "id:l,kind:s,content:s,createdAt:l,userName:s")
                + ",\"trials\":" + WcApi.rows(t, "id:l,startAt:l,status:s,resultNote:s,courseName:s,teacherName:s"));
    }

    static void touch(long id, String status, long nextFollow) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(status);
        p.add(WcApi.l(nextFollow));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        p.add(WcApi.l(id));
        WcDb.exec("UPDATE wc_leads SET status=?, next_follow_at=?, updated_at=? WHERE id=?", p);
    }

    static String leadFollow(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long id = c.pid();
        DbQueryResult l = WcDb.query("SELECT status FROM wc_leads WHERE id=? AND org_id=?", WcApi.P2(id, c.es.orgId));
        if (l.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "线索不存在");
        }
        String cur = l.rows.get(0).getString(0);
        if (validOpenStatus(cur) == 0) {
            return c.fail(409, "LEAD_CLOSED", "线索已结束，不能再跟进");
        }
        String content = c.bs("content");
        if (content.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写跟进内容");
        }
        String next = c.body.getString("status", cur);
        if (validOpenStatus(next) == 0) {
            return c.fail(400, "INVALID_INPUT", "状态不合法");
        }
        if (cur.equals("NEW") && next.equals("NEW")) {
            next = "CONTACTED";
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(id));
        p.add(WcApi.l(c.es.userId));
        p.add(c.body.getString("kind", "NOTE"));
        p.add(content);
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        WcDb.exec("INSERT INTO wc_lead_follows(lead_id, user_id, kind, content, created_at) VALUES (?,?,?,?,?)", p);
        touch(id, next, c.bl("nextFollowAt"));
        return c.ok("\"status\":" + WcApi.q(next));
    }

    static String leadTrial(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long id = c.pid();
        DbQueryResult l = WcDb.query("SELECT status, campus_id FROM wc_leads WHERE id=? AND org_id=?",
                WcApi.P2(id, c.es.orgId));
        if (l.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "线索不存在");
        }
        if (validOpenStatus(l.rows.get(0).getString(0)) == 0) {
            return c.fail(409, "LEAD_CLOSED", "线索已结束");
        }
        long startAt = c.bl("startAt");
        if (startAt < AsyncClockNative.wallMillis() - 3600000L) {
            return c.fail(400, "INVALID_TIME", "试听时间不能早于当前");
        }
        long teacher = c.bl("teacherUserId");
        if (teacher > 0L) {
            ArrayList<Object> cp = new ArrayList<Object>();
            cp.add(WcApi.l(teacher));
            cp.add(WcApi.l(startAt));
            cp.add(WcApi.l(startAt + 3600000L));
            long clash = WcApi.one(
                    "SELECT id FROM wc_lesson_sessions WHERE teacher_user_id=? AND status IN ('SCHEDULED','RESCHEDULED') AND start_at<? AND end_at>? LIMIT 1",
                    swap(cp));
            if (clash > 0L) {
                return c.fail(409, "TEACHER_BUSY", "该老师此时段已有课程");
            }
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        p.add(WcApi.l(id));
        p.add(WcApi.l(l.rows.get(0).getLong(1)));
        p.add(WcApi.l(c.bl("courseId")));
        p.add(WcApi.l(teacher));
        p.add(WcApi.l(startAt));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        long tid = WcDb.insertReturningId(
                "INSERT INTO wc_trials(org_id, lead_id, campus_id, course_id, teacher_user_id, start_at, created_at) VALUES (?,?,?,?,?,?,?)",
                p);
        touch(id, "TRIAL_BOOKED", startAt);
        return c.ok("\"trialId\":" + WcApi.l(tid));
    }

    /** params order (teacher, start, end) → sql expects (teacher, end, start): start_at<end AND end_at>start */
    static ArrayList<Object> swap(ArrayList<Object> cp) {
        ArrayList<Object> o = new ArrayList<Object>();
        o.add(cp.get(0));
        o.add(cp.get(2));
        o.add(cp.get(1));
        return o;
    }

    static String trialResult(WcCtx c) {
        String g = c.guard(OPS + ",TEACHER");
        if (g.length() > 0) {
            return g;
        }
        long tid = c.pid();
        DbQueryResult t = WcDb.query("SELECT lead_id, status FROM wc_trials WHERE id=? AND org_id=?",
                WcApi.P2(tid, c.es.orgId));
        if (t.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "试听不存在");
        }
        if (t.rows.get(0).getString(1).equals("BOOKED") == false) {
            return c.fail(409, "TRIAL_CLOSED", "试听已处理");
        }
        String res = c.bs("result");
        if (res.equals("ATTENDED") == false && res.equals("NOSHOW") == false && res.equals("CANCELLED") == false) {
            return c.fail(400, "INVALID_INPUT", "结果须为 ATTENDED / NOSHOW / CANCELLED");
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(res);
        up.add(c.bs("note"));
        up.add(WcApi.l(tid));
        WcDb.exec("UPDATE wc_trials SET status=?, result_note=? WHERE id=?", up);
        long lead = t.rows.get(0).getLong(0);
        if (res.equals("ATTENDED")) {
            touch(lead, "TRIAL_DONE", AsyncClockNative.wallMillis() + 86400000L);
        } else {
            touch(lead, "CONTACTED", AsyncClockNative.wallMillis() + 86400000L);
        }
        return c.ok("");
    }

    static String leadLose(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long id = c.pid();
        String reason = c.bs("reason");
        if (reason.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "流失必须填写原因");
        }
        DbQueryResult l = WcDb.query("SELECT status FROM wc_leads WHERE id=? AND org_id=?", WcApi.P2(id, c.es.orgId));
        if (l.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "线索不存在");
        }
        if (validOpenStatus(l.rows.get(0).getString(0)) == 0) {
            return c.fail(409, "LEAD_CLOSED", "线索已结束");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(reason);
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        p.add(WcApi.l(id));
        WcDb.exec("UPDATE wc_leads SET status='LOST', lost_reason=?, next_follow_at=0, updated_at=? WHERE id=?", p);
        WcServices.audit(c.es.orgId, c.es.userId, "LEAD_LOST", "lead", id, reason);
        return c.ok("");
    }

    static String leadConvert(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long id = c.pid();
        DbQueryResult l = WcDb.query(
                "SELECT status, child_name, guardian_name, phone, campus_id, converted_student_id FROM wc_leads WHERE id=? AND org_id=?",
                WcApi.P2(id, c.es.orgId));
        if (l.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "线索不存在");
        }
        Row w = l.rows.get(0);
        if (w.getLong(5) > 0L) {
            return c.ok("\"studentId\":" + WcApi.l(w.getLong(5)) + ",\"existing\":1");
        }
        if (validOpenStatus(w.getString(0)) == 0) {
            return c.fail(409, "LEAD_CLOSED", "线索已流失，不能转化");
        }
        long campus = w.getLong(4);
        if (campus == 0L) {
            campus = WcApi.one("SELECT id FROM wc_campuses WHERE org_id=? ORDER BY id LIMIT 1", WcApi.P1(c.es.orgId));
        }
        long sid = WcServices.createStudent(c.es.orgId, campus, w.getString(1), WcAgeBand.CHILD);
        String gname = w.getString(2);
        if (gname.length() == 0) {
            gname = w.getString(1) + "家长";
        }
        long gid = WcServices.createGuardian(c.es.orgId, 0L, gname, w.getString(3));
        WcServices.linkGuardian(sid, gid);
        markWon(c.es.orgId, id, sid, c.es.userId);
        return c.ok("\"studentId\":" + WcApi.l(sid) + ",\"guardianId\":" + WcApi.l(gid));
    }

    static void markWon(long orgId, long leadId, long studentId, long actor) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(studentId));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        p.add(WcApi.l(leadId));
        p.add(WcApi.l(orgId));
        WcDb.exec(
                "UPDATE wc_leads SET status='WON', converted_student_id=?, next_follow_at=0, updated_at=? WHERE id=? AND org_id=?",
                p);
        WcServices.audit(orgId, actor, "LEAD_WON", "lead", leadId, "student=" + WcApi.l(studentId));
    }
}
