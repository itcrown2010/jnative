/**
 * Admin back-office: students, guardians, invites, courses, classes, rooms, staff, products, settings, enrollment.
 * @author 张晓达
 */
class WcBizAdmin {
    static String OPS = "ADMIN,DIRECTOR";
    static long codeSeq = 0L;

    /* ---------- students ---------- */
    static String studentsList(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER");
        if (g.length() > 0) {
            return g;
        }
        String sql = "SELECT s.id, s.name, s.campus_id, s.status, s.gender, s.birthday, s.age_band, "
                + "COALESCE((SELECT name FROM wc_campuses WHERE id=s.campus_id),''), "
                + "COALESCE((SELECT g.name FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=s.id ORDER BY sg.id LIMIT 1),''), "
                + "COALESCE((SELECT g.phone FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=s.id ORDER BY sg.id LIMIT 1),''), "
                + "COALESCE((SELECT SUM(b.paid_remaining_milli+b.bonus_remaining_milli) FROM wc_pack_balances b JOIN wc_contracts ct ON ct.id=b.contract_id WHERE ct.student_id=s.id AND ct.status='ACTIVE' AND ct.pack_expire_at>"
                + WcApi.l(AsyncClockNative.wallMillis()) + "),0) "
                + "FROM wc_students s WHERE s.org_id=?";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        String kw = c.qs("q");
        if (kw.length() > 0) {
            sql = sql + " AND (s.name LIKE ? OR s.id IN (SELECT sg.student_id FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE g.phone LIKE ? OR g.name LIKE ?))";
            p.add(WcApi.like(kw));
            p.add(WcApi.like(kw));
            p.add(WcApi.like(kw));
        }
        long campus = c.ql("campusId");
        if (campus > 0L) {
            sql = sql + " AND s.campus_id=?";
            p.add(WcApi.l(campus));
        }
        String st = c.qs("status");
        if (st.length() > 0) {
            sql = sql + " AND s.status=?";
            p.add(st);
        }
        if (c.es.identity.equals(WcIdentity.TEACHER)) {
            sql = sql + " AND s.id IN (SELECT student_id FROM wc_lesson_sessions WHERE teacher_user_id=? UNION SELECT ss.student_id FROM wc_session_students ss JOIN wc_lesson_sessions ls ON ls.id=ss.session_id WHERE ls.teacher_user_id=?)";
            p.add(WcApi.l(c.es.userId));
            p.add(WcApi.l(c.es.userId));
        }
        sql = sql + " ORDER BY s.id DESC LIMIT 200";
        DbQueryResult r = WcDb.query(sql, p);
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,name:s,campusId:l,status:s,gender:s,birthday:s,ageBand:s,campusName:s,guardianName:s,guardianPhone:s,balanceMilli:l"));
    }

    static String studentCreate(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String name = c.bs("name");
        if (name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写学员姓名");
        }
        long campus = c.bl("campusId");
        if (campus == 0L) {
            campus = WcApi.one("SELECT id FROM wc_campuses WHERE org_id=? ORDER BY id LIMIT 1", WcApi.P1(c.es.orgId));
        }
        String band = c.body.getString("ageBand", WcAgeBand.CHILD);
        long sid = WcServices.createStudent(c.es.orgId, campus, name, band);
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(c.body.getString("gender", ""));
        up.add(c.bs("birthday"));
        up.add(c.bs("school"));
        up.add(c.bs("note"));
        up.add(WcApi.l(sid));
        WcDb.exec("UPDATE wc_students SET gender=?, birthday=?, school=?, note=? WHERE id=?", up);
        String gname = c.bs("guardianName");
        String gphone = c.bs("guardianPhone");
        long gid = 0L;
        if (gname.length() > 0) {
            ArrayList<Object> gp = new ArrayList<Object>();
            gp.add(WcApi.l(c.es.orgId));
            gp.add(gphone);
            if (gphone.length() > 0) {
                gid = WcApi.one("SELECT id FROM wc_guardians WHERE org_id=? AND phone=? ORDER BY id LIMIT 1", gp);
            }
            if (gid == 0L) {
                gid = WcServices.createGuardian(c.es.orgId, 0L, gname, gphone);
            }
            WcServices.linkGuardian(sid, gid);
        }
        WcServices.audit(c.es.orgId, c.es.userId, "STUDENT_CREATE", "student", sid, name);
        return c.ok("\"studentId\":" + WcApi.l(sid) + ",\"guardianId\":" + WcApi.l(gid));
    }

    static String studentUpdate(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.pid();
        ArrayList<Object> ck = WcApi.P2(sid, c.es.orgId);
        if (WcApi.one("SELECT id FROM wc_students WHERE id=? AND org_id=?", ck) == 0L) {
            return c.fail(404, "NOT_FOUND", "学员不存在");
        }
        String name = c.bs("name");
        if (name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "姓名不能为空");
        }
        String status = c.body.getString("status", "ACTIVE");
        if (status.equals("ACTIVE") == false && status.equals("INACTIVE") == false
                && status.equals("GRADUATED") == false) {
            return c.fail(400, "INVALID_INPUT", "状态不合法");
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(name);
        up.add(c.bs("gender"));
        up.add(c.bs("birthday"));
        up.add(c.bs("school"));
        up.add(c.bs("note"));
        up.add(status);
        up.add(WcApi.l(sid));
        WcDb.exec("UPDATE wc_students SET name=?, gender=?, birthday=?, school=?, note=?, status=? WHERE id=?", up);
        WcServices.audit(c.es.orgId, c.es.userId, "STUDENT_UPDATE", "student", sid, status);
        return c.ok("");
    }

    static String studentDetail(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER,PARENT");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.pid();
        if (c.es.identity.equals(WcIdentity.PARENT) && WcServices.isGuardianOf(c.es.userId, sid) == 0) {
            return c.fail(403, "FORBIDDEN", "无权查看该孩子");
        }
        DbQueryResult s = WcDb.query(
                "SELECT name, campus_id, status, gender, birthday, school, note, age_band, created_at FROM wc_students WHERE id=? AND org_id=?",
                WcApi.P2(sid, c.es.orgId));
        if (s.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "学员不存在");
        }
        String note = s.rows.get(0).getString(6);
        if (c.es.identity.equals(WcIdentity.PARENT)) {
            note = "";
        }
        String head = "\"id\":" + WcApi.l(sid) + ",\"name\":" + WcApi.q(s.rows.get(0).getString(0))
                + ",\"campusId\":" + WcApi.l(s.rows.get(0).getLong(1))
                + ",\"status\":" + WcApi.q(s.rows.get(0).getString(2))
                + ",\"gender\":" + WcApi.q(s.rows.get(0).getString(3))
                + ",\"birthday\":" + WcApi.q(s.rows.get(0).getString(4))
                + ",\"school\":" + WcApi.q(s.rows.get(0).getString(5))
                + ",\"note\":" + WcApi.q(note)
                + ",\"ageBand\":" + WcApi.q(s.rows.get(0).getString(7))
                + ",\"createdAt\":" + WcApi.l(s.rows.get(0).getLong(8));
        DbQueryResult gs = WcDb.query(
                "SELECT g.id, g.name, g.phone, CASE WHEN g.user_id>0 THEN 1 ELSE 0 END FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=? ORDER BY sg.id",
                WcApi.P1(sid));
        String guardians = WcApi.rows(gs, "id:l,name:s,phone:s,registered:l");
        DbQueryResult cs = WcDb.query(
                "SELECT ct.id, ct.billing_mode, COALESCE(co.name,''), ct.receivable_cents, ct.received_cents, ct.refunded_cents, ct.status, ct.pack_expire_at, COALESCE(b.paid_remaining_milli+b.bonus_remaining_milli,0), ct.term_end, ct.term_promised_sessions FROM wc_contracts ct LEFT JOIN wc_courses co ON co.id=ct.course_id LEFT JOIN wc_pack_balances b ON b.contract_id=ct.id WHERE ct.student_id=? ORDER BY ct.id DESC",
                WcApi.P1(sid));
        String contracts = WcApi.rows(cs,
                "id:l,mode:s,courseName:s,receivableCents:l,receivedCents:l,refundedCents:l,status:s,expireAt:l,remainingMilli:l,termEnd:l,promised:l");
        long present = WcApi.one("SELECT COUNT(*) FROM wc_attendance WHERE student_id=? AND status IN ('PRESENT','LATE')",
                WcApi.P1(sid));
        long absent = WcApi.one("SELECT COUNT(*) FROM wc_attendance WHERE student_id=? AND status='ABSENT'",
                WcApi.P1(sid));
        long leaves = WcApi.one("SELECT COUNT(*) FROM wc_leave_requests WHERE student_id=? AND status!='CANCELLED'",
                WcApi.P1(sid));
        long fb = WcApi.one("SELECT COUNT(*) FROM wc_feedback WHERE student_id=? AND status='PUBLISHED'",
                WcApi.P1(sid));
        return c.ok(head + ",\"guardians\":" + guardians + ",\"contracts\":" + contracts
                + ",\"stats\":{\"present\":" + WcApi.l(present) + ",\"absent\":" + WcApi.l(absent)
                + ",\"leaves\":" + WcApi.l(leaves) + ",\"feedbacks\":" + WcApi.l(fb) + "}");
    }

    static String guardianAdd(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.pid();
        if (WcApi.one("SELECT id FROM wc_students WHERE id=? AND org_id=?", WcApi.P2(sid, c.es.orgId)) == 0L) {
            return c.fail(404, "NOT_FOUND", "学员不存在");
        }
        String name = c.bs("name");
        String phone = c.bs("phone");
        if (name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写家长姓名");
        }
        long gid = WcServices.createGuardian(c.es.orgId, 0L, name, phone);
        WcServices.linkGuardian(sid, gid);
        return c.ok("\"guardianId\":" + WcApi.l(gid));
    }

    /* ---------- invites ---------- */
    static String newCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        codeSeq = codeSeq + 1L;
        long seed = (AsyncClockNative.wallMillis() + codeSeq * 7919L) % 2147483647L;
        String code = "";
        int i = 0;
        while (i < 8) {
            seed = (seed * 48271L) % 2147483647L;
            long idx = (seed / 7L) % 32L;
            int ix = (int) idx;
            code = code + chars.substring(ix, ix + 1);
            i = i + 1;
        }
        return code;
    }

    static String inviteCreate(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String kind = c.body.getString("identity", WcIdentity.PARENT);
        if (kind.equals(WcIdentity.PARENT) == false && kind.equals(WcIdentity.TEACHER) == false
                && kind.equals(WcIdentity.ADMIN) == false) {
            return c.fail(400, "INVALID_INPUT", "邀请身份不合法");
        }
        long sid = c.bl("studentId");
        if (kind.equals(WcIdentity.PARENT) && sid == 0L) {
            return c.fail(400, "INVALID_INPUT", "家长邀请需选择学员");
        }
        long campus = c.bl("campusId");
        if (campus == 0L) {
            campus = WcApi.one("SELECT id FROM wc_campuses WHERE org_id=? ORDER BY id LIMIT 1", WcApi.P1(c.es.orgId));
        }
        String code = newCode();
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        p.add(code);
        p.add(kind);
        p.add(WcApi.l(sid));
        p.add(WcApi.l(campus));
        p.add(c.bs("note"));
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(now));
        p.add(WcApi.l(now + 72L * 3600000L));
        WcDb.exec(
                "INSERT INTO wc_invites(org_id, code, identity_kind, student_id, campus_id, note, created_by, created_at, expires_at) VALUES (?,?,?,?,?,?,?,?,?)",
                p);
        WcServices.audit(c.es.orgId, c.es.userId, "INVITE_CREATE", "invite", 0L, kind);
        return c.ok("\"code\":" + WcApi.q(code) + ",\"expiresAt\":" + WcApi.l(now + 72L * 3600000L));
    }

    static String inviteList(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT i.id, i.code, i.identity_kind, i.student_id, COALESCE(s.name,''), i.note, i.created_at, i.expires_at, i.used_by FROM wc_invites i LEFT JOIN wc_students s ON s.id=i.student_id WHERE i.org_id=? ORDER BY i.id DESC LIMIT 100",
                WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,code:s,identity:s,studentId:l,studentName:s,note:s,createdAt:l,expiresAt:l,usedBy:l"));
    }

    /* ---------- courses / classes / rooms ---------- */
    static String coursesList(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT id, code, name, subject, status, description FROM wc_courses WHERE org_id=? ORDER BY id",
                WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r, "id:l,code:s,name:s,subject:s,status:s,description:s"));
    }

    static String courseSave(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long id = c.bl("id");
        String name = c.bs("name");
        if (name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写课程名称");
        }
        String status = c.body.getString("status", "ACTIVE");
        if (id > 0L) {
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(name);
            up.add(c.bs("subject"));
            up.add(c.bs("description"));
            up.add(status);
            up.add(WcApi.l(id));
            up.add(WcApi.l(c.es.orgId));
            WcDb.exec("UPDATE wc_courses SET name=?, subject=?, description=?, status=? WHERE id=? AND org_id=?", up);
            return c.ok("\"courseId\":" + WcApi.l(id));
        }
        String code = c.bs("code");
        if (code.length() == 0) {
            code = "C" + WcApi.l(AsyncClockNative.wallMillis() % 1000000L);
        }
        long cid = WcServices.createCourse(c.es.orgId, code, name, c.bs("subject"));
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(c.bs("description"));
        up.add(WcApi.l(cid));
        WcDb.exec("UPDATE wc_courses SET description=? WHERE id=?", up);
        WcServices.audit(c.es.orgId, c.es.userId, "COURSE_CREATE", "course", cid, name);
        return c.ok("\"courseId\":" + WcApi.l(cid));
    }

    static String classesList(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER");
        if (g.length() > 0) {
            return g;
        }
        String sql = "SELECT k.id, k.name, k.campus_id, COALESCE(ca.name,''), k.course_id, COALESCE(co.name,''), k.teacher_user_id, COALESCE(u.display_name,''), k.status, k.capacity, (SELECT COUNT(*) FROM wc_class_students cs WHERE cs.class_id=k.id) FROM wc_classes k LEFT JOIN wc_campuses ca ON ca.id=k.campus_id LEFT JOIN wc_courses co ON co.id=k.course_id LEFT JOIN wc_users u ON u.id=k.teacher_user_id WHERE k.org_id=?";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        if (c.es.identity.equals(WcIdentity.TEACHER)) {
            sql = sql + " AND k.teacher_user_id=?";
            p.add(WcApi.l(c.es.userId));
        }
        sql = sql + " ORDER BY k.id";
        DbQueryResult r = WcDb.query(sql, p);
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,name:s,campusId:l,campusName:s,courseId:l,courseName:s,teacherUserId:l,teacherName:s,status:s,capacity:l,memberCount:l"));
    }

    static String classSave(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long id = c.bl("id");
        String name = c.bs("name");
        if (name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写班级名称");
        }
        long cap = c.bl("capacity");
        if (cap <= 0L) {
            cap = 8L;
        }
        if (id > 0L) {
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(name);
            up.add(WcApi.l(c.bl("teacherUserId")));
            up.add(WcApi.l(cap));
            up.add(c.body.getString("status", "ACTIVE"));
            up.add(WcApi.l(id));
            up.add(WcApi.l(c.es.orgId));
            WcDb.exec("UPDATE wc_classes SET name=?, teacher_user_id=?, capacity=?, status=? WHERE id=? AND org_id=?",
                    up);
            return c.ok("\"classId\":" + WcApi.l(id));
        }
        long campus = c.bl("campusId");
        long course = c.bl("courseId");
        if (campus == 0L || course == 0L) {
            return c.fail(400, "INVALID_INPUT", "请选择校区与课程");
        }
        long kid = WcServices.createClass(c.es.orgId, campus, course, name, c.bl("teacherUserId"));
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(WcApi.l(cap));
        up.add(WcApi.l(kid));
        WcDb.exec("UPDATE wc_classes SET capacity=? WHERE id=?", up);
        WcServices.audit(c.es.orgId, c.es.userId, "CLASS_CREATE", "class", kid, name);
        return c.ok("\"classId\":" + WcApi.l(kid));
    }

    static String classMembers(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER");
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT s.id, s.name, cs.joined_at FROM wc_class_students cs JOIN wc_students s ON s.id=cs.student_id WHERE cs.class_id=? ORDER BY cs.id",
                WcApi.P1(c.pid()));
        return c.ok("\"items\":" + WcApi.rows(r, "id:l,name:s,joinedAt:l"));
    }

    static String classMemberAdd(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long kid = c.pid();
        long sid = c.bl("studentId");
        long cap = WcApi.one("SELECT capacity FROM wc_classes WHERE id=? AND org_id=?", WcApi.P2(kid, c.es.orgId));
        if (cap == 0L) {
            return c.fail(404, "NOT_FOUND", "班级不存在");
        }
        long cur = WcApi.one("SELECT COUNT(*) FROM wc_class_students WHERE class_id=?", WcApi.P1(kid));
        if (cur >= cap) {
            return c.fail(409, "CLASS_FULL", "班级已满员（" + WcApi.l(cap) + "人）");
        }
        if (WcApi.one("SELECT id FROM wc_students WHERE id=? AND org_id=?", WcApi.P2(sid, c.es.orgId)) == 0L) {
            return c.fail(404, "NOT_FOUND", "学员不存在");
        }
        if (WcApi.one("SELECT id FROM wc_class_students WHERE class_id=? AND student_id=?", WcApi.P2(kid, sid)) > 0L) {
            return c.fail(409, "ALREADY_MEMBER", "学员已在班级中");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(kid));
        p.add(WcApi.l(sid));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        WcDb.exec("INSERT INTO wc_class_students(class_id, student_id, joined_at) VALUES (?,?,?)", p);
        return c.ok("");
    }

    static String classMemberRemove(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        WcDb.exec("DELETE FROM wc_class_students WHERE class_id=? AND student_id=?", WcApi.P2(c.pid(), c.bl("studentId")));
        return c.ok("");
    }

    static String roomsList(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER");
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT r.id, r.campus_id, COALESCE(ca.name,''), r.code, r.name, r.capacity FROM wc_rooms r JOIN wc_campuses ca ON ca.id=r.campus_id WHERE ca.org_id=? ORDER BY r.id",
                WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r, "id:l,campusId:l,campusName:s,code:s,name:s,capacity:l"));
    }

    static String roomSave(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String name = c.bs("name");
        long campus = c.bl("campusId");
        if (name.length() == 0 || campus == 0L) {
            return c.fail(400, "INVALID_INPUT", "请填写教室名称并选择校区");
        }
        long id = c.bl("id");
        long cap = c.bl("capacity");
        if (cap <= 0L) {
            cap = 8L;
        }
        if (id > 0L) {
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(name);
            up.add(WcApi.l(cap));
            up.add(WcApi.l(id));
            WcDb.exec("UPDATE wc_rooms SET name=?, capacity=? WHERE id=?", up);
            return c.ok("\"roomId\":" + WcApi.l(id));
        }
        String code = c.bs("code");
        if (code.length() == 0) {
            code = "R" + WcApi.l(AsyncClockNative.wallMillis() % 100000L);
        }
        long rid = WcServices.createRoom(campus, code, name);
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(WcApi.l(cap));
        up.add(WcApi.l(rid));
        WcDb.exec("UPDATE wc_rooms SET capacity=? WHERE id=?", up);
        return c.ok("\"roomId\":" + WcApi.l(rid));
    }

    /* ---------- staff ---------- */
    static String staffList(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT u.id, u.login, u.display_name, u.phone, u.status, COALESCE((SELECT GROUP_CONCAT(DISTINCT identity_kind) FROM wc_user_identities WHERE user_id=u.id),''), COALESCE((SELECT per_session_cents FROM wc_teacher_rates WHERE teacher_user_id=u.id),0), u.last_login_at, CASE WHEN u.password_hash='' THEN 0 ELSE 1 END FROM wc_users u WHERE u.org_id=? AND u.id IN (SELECT user_id FROM wc_user_identities WHERE identity_kind!='PARENT') ORDER BY u.id",
                WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,login:s,displayName:s,phone:s,status:s,identities:s,perSessionCents:l,lastLoginAt:l,hasPassword:l"));
    }

    static String staffCreate(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String login = c.bs("login");
        String name = c.bs("displayName");
        String pw = c.bs("password");
        if (login.length() < 3 || name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写账号与姓名");
        }
        String weak = WcBizAuth.validatePassword(c, login, pw);
        if (weak.length() > 0) {
            return weak;
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(WcApi.l(c.es.orgId));
        lp.add(login);
        if (WcApi.one("SELECT id FROM wc_users WHERE org_id=? AND login=?", lp) > 0L) {
            return c.fail(409, "LOGIN_TAKEN", "账号已存在");
        }
        String kind = c.body.getString("identity", WcIdentity.TEACHER);
        if (kind.equals(WcIdentity.TEACHER) == false && kind.equals(WcIdentity.ADMIN) == false
                && kind.equals(WcIdentity.DIRECTOR) == false) {
            return c.fail(400, "INVALID_INPUT", "身份不合法");
        }
        long campus = c.bl("campusId");
        if (campus == 0L) {
            campus = WcApi.one("SELECT id FROM wc_campuses WHERE org_id=? ORDER BY id LIMIT 1", WcApi.P1(c.es.orgId));
        }
        long uid = WcServices.createUser(c.es.orgId, login, name);
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(Password.hashBlocking(pw));
        up.add(c.bs("phone"));
        up.add(WcApi.l(uid));
        WcDb.exec("UPDATE wc_users SET password_hash=?, phone=? WHERE id=?", up);
        WcServices.addIdentity(uid, c.es.orgId, kind, campus);
        WcServices.grantStaffCampus(uid, campus, kind);
        if (kind.equals(WcIdentity.TEACHER)) {
            setRate(c.es.orgId, uid, c.bl("perSessionCents"));
        }
        WcServices.audit(c.es.orgId, c.es.userId, "STAFF_CREATE", "user", uid, kind);
        return c.ok("\"userId\":" + WcApi.l(uid));
    }

    static void setRate(long orgId, long teacherId, long cents) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(WcApi.l(teacherId));
        p.add(WcApi.l(cents));
        WcDb.exec(
                "INSERT INTO wc_teacher_rates(org_id, teacher_user_id, per_session_cents) VALUES (?,?,?) ON CONFLICT(teacher_user_id) DO UPDATE SET per_session_cents=excluded.per_session_cents",
                p);
    }

    static String staffUpdate(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long uid = c.pid();
        if (WcApi.one("SELECT id FROM wc_users WHERE id=? AND org_id=?", WcApi.P2(uid, c.es.orgId)) == 0L) {
            return c.fail(404, "NOT_FOUND", "员工不存在");
        }
        String status = c.body.getString("status", "ACTIVE");
        if (uid == c.es.userId && status.equals("DISABLED")) {
            return c.fail(400, "INVALID_INPUT", "不能停用自己");
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(c.bs("displayName"));
        up.add(c.bs("phone"));
        up.add(status);
        up.add(WcApi.l(uid));
        WcDb.exec("UPDATE wc_users SET display_name=?, phone=?, status=? WHERE id=?", up);
        if (c.body.has("perSessionCents")) {
            setRate(c.es.orgId, uid, c.bl("perSessionCents"));
        }
        String pw = c.bs("newPassword");
        if (pw.length() > 0) {
            String login = WcApi.oneStr("SELECT login FROM wc_users WHERE id=?", WcApi.P1(uid));
            String weak = WcBizAuth.validatePassword(c, login, pw);
            if (weak.length() > 0) {
                return weak;
            }
            ArrayList<Object> pp = new ArrayList<Object>();
            pp.add(Password.hashBlocking(pw));
            pp.add(WcApi.l(uid));
            WcDb.exec("UPDATE wc_users SET password_hash=?, fail_count=0, locked_until=0 WHERE id=?", pp);
        }
        WcServices.audit(c.es.orgId, c.es.userId, "STAFF_UPDATE", "user", uid, status);
        return c.ok("");
    }

    static String teachersLite(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT DISTINCT u.id, u.display_name FROM wc_users u JOIN wc_user_identities i ON i.user_id=u.id WHERE u.org_id=? AND i.identity_kind='TEACHER' AND u.status!='DISABLED' ORDER BY u.id",
                WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r, "id:l,name:s"));
    }

    /* ---------- products ---------- */
    static String productsList(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        String sql = "SELECT p.id, p.course_id, COALESCE(co.name,''), p.name, p.kind, p.paid_milli, p.bonus_milli, p.valid_days, p.term_sessions, p.term_days, p.price_cents, p.status FROM wc_products p LEFT JOIN wc_courses co ON co.id=p.course_id WHERE p.org_id=?";
        if (c.es.identity.equals(WcIdentity.PARENT) || c.es.identity.equals(WcIdentity.TEACHER)) {
            sql = sql + " AND p.status='ACTIVE'";
        }
        sql = sql + " ORDER BY p.id";
        DbQueryResult r = WcDb.query(sql, WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,courseId:l,courseName:s,name:s,kind:s,paidMilli:l,bonusMilli:l,validDays:l,termSessions:l,termDays:l,priceCents:l,status:s"));
    }

    static String productSave(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        String name = c.bs("name");
        String kind = c.body.getString("kind", "PACK");
        if (name.length() == 0 || c.bl("courseId") == 0L) {
            return c.fail(400, "INVALID_INPUT", "请填写名称并选择课程");
        }
        if (kind.equals("PACK") == false && kind.equals("TERM") == false) {
            return c.fail(400, "INVALID_INPUT", "类型须为 PACK 或 TERM");
        }
        if (c.bl("priceCents") < 0L) {
            return c.fail(400, "INVALID_INPUT", "价格不能为负");
        }
        if (kind.equals("PACK") && c.bl("paidMilli") <= 0L) {
            return c.fail(400, "INVALID_INPUT", "课时包须填写购买课时");
        }
        if (kind.equals("TERM") && c.bl("termSessions") <= 0L) {
            return c.fail(400, "INVALID_INPUT", "学期制须填写承诺课次");
        }
        long id = c.bl("id");
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.bl("courseId")));
        p.add(name);
        p.add(kind);
        p.add(WcApi.l(c.bl("paidMilli")));
        p.add(WcApi.l(c.bl("bonusMilli")));
        long vd = c.bl("validDays");
        if (vd <= 0L) {
            vd = 180L;
        }
        p.add(WcApi.l(vd));
        p.add(WcApi.l(c.bl("termSessions")));
        long td = c.bl("termDays");
        if (td <= 0L) {
            td = 90L;
        }
        p.add(WcApi.l(td));
        p.add(WcApi.l(c.bl("priceCents")));
        p.add(c.body.getString("status", "ACTIVE"));
        if (id > 0L) {
            p.add(WcApi.l(id));
            p.add(WcApi.l(c.es.orgId));
            WcDb.exec(
                    "UPDATE wc_products SET course_id=?, name=?, kind=?, paid_milli=?, bonus_milli=?, valid_days=?, term_sessions=?, term_days=?, price_cents=?, status=? WHERE id=? AND org_id=?",
                    p);
            return c.ok("\"productId\":" + WcApi.l(id));
        }
        p.add(WcApi.l(c.es.orgId));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        long pid = WcDb.insertReturningId(
                "INSERT INTO wc_products(course_id, name, kind, paid_milli, bonus_milli, valid_days, term_sessions, term_days, price_cents, status, org_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
        WcServices.audit(c.es.orgId, c.es.userId, "PRODUCT_CREATE", "product", pid, name);
        return c.ok("\"productId\":" + WcApi.l(pid));
    }

    /* ---------- settings ---------- */
    static String settingGet(long orgId, String key, String def) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(key);
        String v = WcApi.oneStr("SELECT svalue FROM wc_settings WHERE org_id=? AND skey=?", p);
        if (v.length() == 0) {
            return def;
        }
        return v;
    }

    static WcRuleSnapshot rulesFor(long orgId) {
        WcRuleSnapshot s = WcRuleSnapshot.defaults();
        s.leaveDeadlineHours = WcNum.parseInt(settingGet(orgId, "leaveDeadlineHours", "12"));
        s.leaveConsumes = WcNum.parseInt(settingGet(orgId, "leaveConsumes", "0"));
        s.absentConsumes = WcNum.parseInt(settingGet(orgId, "absentConsumes", "0"));
        s.allowMakeup = WcNum.parseInt(settingGet(orgId, "allowMakeup", "1"));
        s.makeupValidDays = WcNum.parseInt(settingGet(orgId, "makeupValidDays", "30"));
        if (settingGet(orgId, "deductOrder", "PAID_FIRST").equals("BONUS_FIRST")) {
            s.deductOrder = "BONUS_FIRST";
        }
        return s;
    }

    static String settingsGet(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR");
        if (g.length() > 0) {
            return g;
        }
        long o = c.es.orgId;
        return c.ok("\"orgName\":" + WcApi.q(WcApi.oneStr("SELECT name FROM wc_orgs WHERE id=?", WcApi.P1(o)))
                + ",\"contactPhone\":" + WcApi.q(settingGet(o, "contactPhone", ""))
                + ",\"leaveDeadlineHours\":" + WcApi.q(settingGet(o, "leaveDeadlineHours", "12"))
                + ",\"leaveConsumes\":" + WcApi.q(settingGet(o, "leaveConsumes", "0"))
                + ",\"absentConsumes\":" + WcApi.q(settingGet(o, "absentConsumes", "0"))
                + ",\"allowMakeup\":" + WcApi.q(settingGet(o, "allowMakeup", "1"))
                + ",\"makeupValidDays\":" + WcApi.q(settingGet(o, "makeupValidDays", "30"))
                + ",\"deductOrder\":" + WcApi.q(settingGet(o, "deductOrder", "PAID_FIRST"))
                + ",\"renewMilliThreshold\":" + WcApi.q(settingGet(o, "renewMilliThreshold", "2000"))
                + ",\"renewDays\":" + WcApi.q(settingGet(o, "renewDays", "30")));
    }

    static void settingPut(long orgId, String key, String value) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(orgId));
        p.add(key);
        p.add(value);
        WcDb.exec(
                "INSERT INTO wc_settings(org_id, skey, svalue) VALUES (?,?,?) ON CONFLICT(org_id, skey) DO UPDATE SET svalue=excluded.svalue",
                p);
    }

    static String settingsSave(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String[] keys = new String[9];
        keys[0] = "contactPhone";
        keys[1] = "leaveDeadlineHours";
        keys[2] = "leaveConsumes";
        keys[3] = "absentConsumes";
        keys[4] = "allowMakeup";
        keys[5] = "makeupValidDays";
        keys[6] = "deductOrder";
        keys[7] = "renewMilliThreshold";
        keys[8] = "renewDays";
        int i = 0;
        while (i < 9) {
            if (c.body.has(keys[i])) {
                settingPut(c.es.orgId, keys[i], c.body.getString(keys[i], ""));
            }
            i = i + 1;
        }
        WcServices.audit(c.es.orgId, c.es.userId, "SETTINGS_SAVE", "org", c.es.orgId, "");
        return c.ok("");
    }

    /* ---------- enroll by product ---------- */
    static String enrollProduct(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.bl("studentId");
        long pid = c.bl("productId");
        String idem = c.bs("idempotencyKey");
        DbQueryResult pr = WcDb.query(
                "SELECT course_id, kind, paid_milli, bonus_milli, valid_days, term_sessions, term_days, price_cents, status FROM wc_products WHERE id=? AND org_id=?",
                WcApi.P2(pid, c.es.orgId));
        if (pr.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "商品不存在");
        }
        if (pr.rows.get(0).getString(8).equals("ACTIVE") == false) {
            return c.fail(409, "PRODUCT_OFF", "商品已下架");
        }
        DbQueryResult st = WcDb.query("SELECT campus_id FROM wc_students WHERE id=? AND org_id=?",
                WcApi.P2(sid, c.es.orgId));
        if (st.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "学员不存在");
        }
        long campus = c.bl("campusId");
        if (campus == 0L) {
            campus = st.rows.get(0).getLong(0);
        }
        long discount = c.bl("discountCents");
        long price = pr.rows.get(0).getLong(7);
        if (discount < 0L || discount > price) {
            return c.fail(400, "INVALID_INPUT", "优惠金额不合法");
        }
        WcRuleSnapshot rules = rulesFor(c.es.orgId);
        long now = AsyncClockNative.wallMillis();
        WcResult r = null;
        if (pr.rows.get(0).getString(1).equals("PACK")) {
            long expire = now + pr.rows.get(0).getLong(4) * 86400000L;
            r = WcServices.enrollPack(c.es.orgId, campus, sid, pr.rows.get(0).getLong(0), c.bl("classId"),
                    pr.rows.get(0).getLong(2), pr.rows.get(0).getLong(3), expire, price, discount, c.es.userId, idem,
                    rules);
        } else {
            r = WcServices.enrollTerm(c.es.orgId, campus, sid, pr.rows.get(0).getLong(0), c.bl("classId"), now,
                    now + pr.rows.get(0).getLong(6) * 86400000L, (int) pr.rows.get(0).getLong(5), price, discount,
                    c.es.userId, idem, rules);
        }
        if (r.ok == 0) {
            return c.result(r);
        }
        long cid = WcServices.parseDocId(r.documentJson, "contractId");
        long pay = c.bl("payCents");
        long paymentId = 0L;
        if (pay > 0L) {
            WcResult pr2 = WcServices.registerPayment(c.es.orgId, cid, pay, c.body.getString("payMethod", "CASH"),
                    c.bs("receiptNo"), c.es.userId, idem + "-pay");
            if (pr2.ok == 0) {
                return c.fail(400, pr2.code, "合同已创建(#" + WcApi.l(cid) + ")，但收款失败：" + pr2.message);
            }
            paymentId = WcServices.parseDocId(pr2.documentJson, "paymentId");
        }
        long leadId = c.bl("leadId");
        if (leadId > 0L) {
            WcBizCrm.markWon(c.es.orgId, leadId, sid, c.es.userId);
        }
        return c.ok("\"contractId\":" + WcApi.l(cid) + ",\"paymentId\":" + WcApi.l(paymentId));
    }

    /* ---------- recurring schedule ---------- */
    static String scheduleRecurring(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long first = c.bl("firstStartAt");
        long durMin = c.bl("durationMin");
        long weeks = c.bl("weeks");
        if (first <= 0L || durMin <= 0L || weeks <= 0L || weeks > 52L) {
            return c.fail(400, "INVALID_INPUT", "请填写首课时间、时长与周数（1-52）");
        }
        long created = 0L;
        long skipped = 0L;
        String problems = "[";
        long w = 0L;
        while (w < weeks) {
            long start = first + w * 7L * 86400000L;
            WcResult r = WcServices.createSession(c.es.orgId, c.bl("campusId"), c.bl("classId"),
                    c.bl("teacherUserId"), c.bl("roomId"), start, start + durMin * 60000L, c.bl("studentId"),
                    c.bl("contractId"), 0L);
            if (r.ok == 0) {
                if (skipped > 0L) {
                    problems = problems + ",";
                }
                problems = problems + "{\"startAt\":" + WcApi.l(start) + ",\"reason\":" + WcApi.q(r.message) + "}";
                skipped = skipped + 1L;
            } else {
                created = created + 1L;
            }
            w = w + 1L;
        }
        WcServices.audit(c.es.orgId, c.es.userId, "SCHEDULE_RECURRING", "class", c.bl("classId"),
                "created=" + WcApi.l(created) + " skipped=" + WcApi.l(skipped));
        return c.ok("\"created\":" + WcApi.l(created) + ",\"skipped\":" + WcApi.l(skipped) + ",\"problems\":"
                + problems + "]");
    }
}
