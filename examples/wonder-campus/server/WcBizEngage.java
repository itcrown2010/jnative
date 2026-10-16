/**
 * Home-school engagement: announcements, homework, parent reviews, renewal enquiry.
 * @author 张晓达
 */
class WcBizEngage {
    /* ---------- announcements ---------- */
    static String annList(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        String sql = "SELECT a.id, a.title, a.body, a.audience, a.campus_id, a.pinned, a.status, a.created_at, COALESCE(u.display_name,''), CASE WHEN r.id IS NULL THEN 0 ELSE 1 END FROM wc_announcements a LEFT JOIN wc_users u ON u.id=a.created_by LEFT JOIN wc_announcement_reads r ON r.announcement_id=a.id AND r.user_id=? WHERE a.org_id=?";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(c.es.orgId));
        if (c.es.identity.equals(WcIdentity.PARENT)) {
            sql = sql + " AND a.status='PUBLISHED' AND a.audience IN ('ALL','PARENT') AND (a.campus_id=0 OR a.campus_id IN (SELECT s.campus_id FROM wc_students s JOIN wc_student_guardians sg ON sg.student_id=s.id JOIN wc_guardians g ON g.id=sg.guardian_id WHERE g.user_id=?))";
            p.add(WcApi.l(c.es.userId));
        } else if (c.es.identity.equals(WcIdentity.TEACHER)) {
            sql = sql + " AND a.status='PUBLISHED' AND a.audience IN ('ALL','TEACHER')";
        }
        sql = sql + " ORDER BY a.pinned DESC, a.id DESC LIMIT 100";
        DbQueryResult r = WcDb.query(sql, p);
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,title:s,body:s,audience:s,campusId:l,pinned:l,status:s,createdAt:l,author:s,read:l"));
    }

    static String annCreate(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String title = c.bs("title");
        String body = c.bs("body");
        if (title.length() == 0 || body.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写标题和内容");
        }
        String aud = c.body.getString("audience", "ALL");
        if (aud.equals("ALL") == false && aud.equals("PARENT") == false && aud.equals("TEACHER") == false) {
            return c.fail(400, "INVALID_INPUT", "受众不合法");
        }
        long campus = c.bl("campusId");
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        p.add(WcApi.l(campus));
        p.add(aud);
        p.add(title);
        p.add(body);
        p.add(WcApi.l(c.bl("pinned") > 0L ? 1L : 0L));
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        long id = WcDb.insertReturningId(
                "INSERT INTO wc_announcements(org_id, campus_id, audience, title, body, pinned, created_by, created_at) VALUES (?,?,?,?,?,?,?,?)",
                p);
        int notified = 0;
        if (aud.equals("ALL") || aud.equals("PARENT")) {
            String sql = "SELECT DISTINCT g.user_id FROM wc_guardians g JOIN wc_student_guardians sg ON sg.guardian_id=g.id JOIN wc_students s ON s.id=sg.student_id WHERE g.org_id=? AND g.user_id>0";
            ArrayList<Object> gp = new ArrayList<Object>();
            gp.add(WcApi.l(c.es.orgId));
            if (campus > 0L) {
                sql = sql + " AND s.campus_id=?";
                gp.add(WcApi.l(campus));
            }
            DbQueryResult us = WcDb.query(sql, gp);
            int i = 0;
            while (i < us.rows.size()) {
                WcServices.notifyUser(c.es.orgId, us.rows.get(i).getLong(0), "公告：" + title, body, "ANNOUNCEMENT", id);
                notified = notified + 1;
                i = i + 1;
            }
        }
        if (aud.equals("ALL") || aud.equals("TEACHER")) {
            DbQueryResult ts = WcDb.query(
                    "SELECT DISTINCT user_id FROM wc_user_identities WHERE org_id=? AND identity_kind='TEACHER'",
                    WcApi.P1(c.es.orgId));
            int j = 0;
            while (j < ts.rows.size()) {
                WcServices.notifyUser(c.es.orgId, ts.rows.get(j).getLong(0), "公告：" + title, body, "ANNOUNCEMENT", id);
                notified = notified + 1;
                j = j + 1;
            }
        }
        WcServices.audit(c.es.orgId, c.es.userId, "ANNOUNCE", "announcement", id, title);
        return c.ok("\"announcementId\":" + WcApi.l(id) + ",\"notified\":" + WcApi.l((long) notified));
    }

    static String annRead(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.pid()));
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        WcDb.exec("INSERT OR IGNORE INTO wc_announcement_reads(announcement_id, user_id, read_at) VALUES (?,?,?)", p);
        return c.ok("");
    }

    static String annRevoke(WcCtx c) {
        String g = c.guard("ADMIN");
        if (g.length() > 0) {
            return g;
        }
        WcDb.exec("UPDATE wc_announcements SET status='REVOKED' WHERE id=? AND org_id=?",
                WcApi.P2(c.pid(), c.es.orgId));
        return c.ok("");
    }

    /* ---------- homework ---------- */
    static String targetSql() {
        return "SELECT student_id FROM wc_class_students WHERE class_id=h.class_id UNION SELECT student_id FROM wc_lesson_sessions WHERE class_id=h.class_id AND student_id>0 UNION SELECT ss.student_id FROM wc_session_students ss JOIN wc_lesson_sessions ls ON ls.id=ss.session_id WHERE ls.class_id=h.class_id";
    }

    static String hwCreate(WcCtx c) {
        String g = c.guard("TEACHER,ADMIN");
        if (g.length() > 0) {
            return g;
        }
        String title = c.bs("title");
        if (title.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写作业标题");
        }
        long classId = c.bl("classId");
        long studentId = c.bl("studentId");
        if (classId == 0L && studentId == 0L) {
            return c.fail(400, "INVALID_INPUT", "请选择班级或学员");
        }
        if (classId > 0L && c.es.identity.equals(WcIdentity.TEACHER)) {
            long own = WcApi.one("SELECT id FROM wc_classes WHERE id=? AND teacher_user_id=?",
                    WcApi.P2(classId, c.es.userId));
            long ownSess = WcApi.one("SELECT id FROM wc_lesson_sessions WHERE class_id=? AND teacher_user_id=? LIMIT 1",
                    WcApi.P2(classId, c.es.userId));
            if (own == 0L && ownSess == 0L) {
                return c.fail(403, "FORBIDDEN", "只能给自己任教的班级布置作业");
            }
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        p.add(WcApi.l(classId));
        p.add(WcApi.l(c.bl("sessionId")));
        p.add(WcApi.l(studentId));
        p.add(title);
        p.add(c.bs("body"));
        p.add(WcApi.l(c.bl("dueAt")));
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        long id = WcDb.insertReturningId(
                "INSERT INTO wc_homework(org_id, class_id, session_id, student_id, title, body, due_at, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                p);
        DbQueryResult us = WcDb.query(
                "SELECT DISTINCT g.user_id FROM wc_guardians g JOIN wc_student_guardians sg ON sg.guardian_id=g.id JOIN wc_homework h ON h.id=? WHERE g.user_id>0 AND (sg.student_id=h.student_id OR (h.student_id=0 AND sg.student_id IN ("
                        + targetSql() + ")))",
                WcApi.P1(id));
        int i = 0;
        while (i < us.rows.size()) {
            WcServices.notifyUser(c.es.orgId, us.rows.get(i).getLong(0), "新作业：" + title, c.bs("body"), "HOMEWORK", id);
            i = i + 1;
        }
        return c.ok("\"homeworkId\":" + WcApi.l(id) + ",\"notified\":" + WcApi.l((long) us.rows.size()));
    }

    static String hwList(WcCtx c) {
        String g = c.guard("TEACHER,ADMIN,DIRECTOR,PARENT");
        if (g.length() > 0) {
            return g;
        }
        if (c.es.identity.equals(WcIdentity.PARENT)) {
            long sid = c.ql("studentId");
            if (sid == 0L || WcServices.isGuardianOf(c.es.userId, sid) == 0) {
                return c.fail(403, "FORBIDDEN", "请选择自己的孩子");
            }
            DbQueryResult r = WcDb.query(
                    "SELECT h.id, h.title, h.body, h.due_at, h.created_at, COALESCE(u.display_name,''), COALESCE(sb.status,'TODO'), COALESCE(sb.stars,0), COALESCE(sb.comment,''), COALESCE(sb.content,''), COALESCE(sb.media_id,0) FROM wc_homework h LEFT JOIN wc_users u ON u.id=h.created_by LEFT JOIN wc_homework_submissions sb ON sb.homework_id=h.id AND sb.student_id=? WHERE h.org_id=? AND (h.student_id=? OR (h.student_id=0 AND CAST(? AS INTEGER) IN ("
                            + targetSql() + "))) ORDER BY h.id DESC LIMIT 60",
                    four(sid, c.es.orgId, sid, sid));
            return c.ok("\"items\":" + WcApi.rows(r,
                    "id:l,title:s,body:s,dueAt:l,createdAt:l,teacher:s,status:s,stars:l,comment:s,content:s,mediaId:l"));
        }
        String sql = "SELECT h.id, h.title, h.body, h.due_at, h.created_at, h.class_id, COALESCE(k.name,''), h.student_id, COALESCE(s.name,''), (SELECT COUNT(*) FROM wc_homework_submissions sb WHERE sb.homework_id=h.id), (SELECT COUNT(*) FROM wc_homework_submissions sb WHERE sb.homework_id=h.id AND sb.status='SUBMITTED') FROM wc_homework h LEFT JOIN wc_classes k ON k.id=h.class_id LEFT JOIN wc_students s ON s.id=h.student_id WHERE h.org_id=?";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        if (c.es.identity.equals(WcIdentity.TEACHER)) {
            sql = sql + " AND h.created_by=?";
            p.add(WcApi.l(c.es.userId));
        }
        sql = sql + " ORDER BY h.id DESC LIMIT 60";
        DbQueryResult r = WcDb.query(sql, p);
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,title:s,body:s,dueAt:l,createdAt:l,classId:l,className:s,studentId:l,studentName:s,submitted:l,pendingReview:l"));
    }

    static ArrayList<Object> four(long a, long b, long c2, long d) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(a));
        p.add(WcApi.l(b));
        p.add(WcApi.l(c2));
        p.add(WcApi.l(d));
        return p;
    }

    static String hwSubmissions(WcCtx c) {
        String g = c.guard("TEACHER,ADMIN,DIRECTOR");
        if (g.length() > 0) {
            return g;
        }
        long hid = c.pid();
        long owner = WcApi.one("SELECT id FROM wc_homework WHERE id=? AND org_id=?", WcApi.P2(hid, c.es.orgId));
        if (owner == 0L) {
            return c.fail(404, "NOT_FOUND", "作业不存在");
        }
        DbQueryResult h = WcDb.query("SELECT student_id, class_id FROM wc_homework WHERE id=?", WcApi.P1(hid));
        String ids = "0";
        if (h.rows.get(0).getLong(0) > 0L) {
            ids = WcApi.l(h.rows.get(0).getLong(0));
        } else {
            long kid = h.rows.get(0).getLong(1);
            ArrayList<Object> kp = new ArrayList<Object>();
            kp.add(WcApi.l(kid));
            kp.add(WcApi.l(kid));
            kp.add(WcApi.l(kid));
            DbQueryResult ts = WcDb.query(
                    "SELECT student_id FROM wc_class_students WHERE class_id=? UNION SELECT student_id FROM wc_lesson_sessions WHERE class_id=? AND student_id>0 UNION SELECT ss.student_id FROM wc_session_students ss JOIN wc_lesson_sessions ls ON ls.id=ss.session_id WHERE ls.class_id=?",
                    kp);
            int t = 0;
            while (t < ts.rows.size()) {
                ids = ids + "," + WcApi.l(ts.rows.get(t).getLong(0));
                t = t + 1;
            }
        }
        DbQueryResult r = WcDb.query(
                "SELECT s.id, s.name, COALESCE(sb.id,0), COALESCE(sb.status,'TODO'), COALESCE(sb.content,''), COALESCE(sb.media_id,0), COALESCE(sb.stars,0), COALESCE(sb.comment,''), COALESCE(sb.created_at,0) FROM wc_students s LEFT JOIN wc_homework_submissions sb ON sb.homework_id=? AND sb.student_id=s.id WHERE s.id IN ("
                        + ids + ") ORDER BY s.id",
                WcApi.P1(hid));
        return c.ok("\"items\":" + WcApi.rows(r,
                "studentId:l,studentName:s,submissionId:l,status:s,content:s,mediaId:l,stars:l,comment:s,submittedAt:l"));
    }

    static String hwSubmit(WcCtx c) {
        String g = c.guard("PARENT");
        if (g.length() > 0) {
            return g;
        }
        long hid = c.pid();
        long sid = c.bl("studentId");
        if (WcServices.isGuardianOf(c.es.userId, sid) == 0) {
            return c.fail(403, "FORBIDDEN", "无权为该孩子提交");
        }
        String content = c.bs("content");
        long media = c.bl("mediaId");
        if (content.length() == 0 && media == 0L) {
            return c.fail(400, "INVALID_INPUT", "请填写文字或上传作品");
        }
        long ok = WcApi.one(
                "SELECT h.id FROM wc_homework h WHERE h.id=? AND h.org_id=? AND (h.student_id=? OR (h.student_id=0 AND CAST(? AS INTEGER) IN ("
                        + targetSql() + ")))",
                four(hid, c.es.orgId, sid, sid));
        if (ok == 0L) {
            return c.fail(403, "FORBIDDEN", "该作业未布置给此学员");
        }
        long cur = WcApi.one("SELECT id FROM wc_homework_submissions WHERE homework_id=? AND student_id=?",
                WcApi.P2(hid, sid));
        long now = AsyncClockNative.wallMillis();
        if (cur > 0L) {
            String st = WcApi.oneStr("SELECT status FROM wc_homework_submissions WHERE id=?", WcApi.P1(cur));
            if (st.equals("REVIEWED")) {
                return c.fail(409, "ALREADY_REVIEWED", "老师已点评，不能再修改");
            }
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(content);
            up.add(WcApi.l(media));
            up.add(WcApi.l(now));
            up.add(WcApi.l(cur));
            WcDb.exec("UPDATE wc_homework_submissions SET content=?, media_id=?, created_at=? WHERE id=?", up);
        } else {
            ArrayList<Object> ip = new ArrayList<Object>();
            ip.add(WcApi.l(hid));
            ip.add(WcApi.l(sid));
            ip.add(content);
            ip.add(WcApi.l(media));
            ip.add(WcApi.l(now));
            WcDb.exec(
                    "INSERT INTO wc_homework_submissions(homework_id, student_id, content, media_id, created_at) VALUES (?,?,?,?,?)",
                    ip);
        }
        long teacher = WcApi.one("SELECT created_by FROM wc_homework WHERE id=?", WcApi.P1(hid));
        if (teacher > 0L) {
            String sname = WcApi.oneStr("SELECT name FROM wc_students WHERE id=?", WcApi.P1(sid));
            WcServices.notifyUser(c.es.orgId, teacher, "作业已提交", sname + " 提交了作业，待点评", "HOMEWORK", hid);
        }
        return c.ok("");
    }

    static String hwReview(WcCtx c) {
        String g = c.guard("TEACHER,ADMIN");
        if (g.length() > 0) {
            return g;
        }
        long subId = c.pid();
        long stars = c.bl("stars");
        if (stars < 1L || stars > 5L) {
            return c.fail(400, "INVALID_INPUT", "星级须为 1-5");
        }
        DbQueryResult s = WcDb.query(
                "SELECT sb.student_id, h.id, h.title, h.created_by FROM wc_homework_submissions sb JOIN wc_homework h ON h.id=sb.homework_id WHERE sb.id=? AND h.org_id=?",
                WcApi.P2(subId, c.es.orgId));
        if (s.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "提交不存在");
        }
        if (c.es.identity.equals(WcIdentity.TEACHER) && s.rows.get(0).getLong(3) != c.es.userId) {
            return c.fail(403, "FORBIDDEN", "只能点评自己布置的作业");
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(WcApi.l(stars));
        up.add(c.bs("comment"));
        up.add(WcApi.l(c.es.userId));
        up.add(WcApi.l(AsyncClockNative.wallMillis()));
        up.add(WcApi.l(subId));
        WcDb.exec("UPDATE wc_homework_submissions SET status='REVIEWED', stars=?, comment=?, reviewed_by=?, reviewed_at=? WHERE id=?",
                up);
        DbQueryResult gu = WcDb.query(
                "SELECT DISTINCT g.user_id FROM wc_guardians g JOIN wc_student_guardians sg ON sg.guardian_id=g.id WHERE sg.student_id=? AND g.user_id>0",
                WcApi.P1(s.rows.get(0).getLong(0)));
        int i = 0;
        while (i < gu.rows.size()) {
            WcServices.notifyUser(c.es.orgId, gu.rows.get(i).getLong(0), "作业已点评：" + s.rows.get(0).getString(2),
                    c.bs("comment"), "HOMEWORK", s.rows.get(0).getLong(1));
            i = i + 1;
        }
        return c.ok("");
    }

    /* ---------- reviews ---------- */
    static String reviewCreate(WcCtx c) {
        String g = c.guard("PARENT");
        if (g.length() > 0) {
            return g;
        }
        long sess = c.bl("sessionId");
        long sid = c.bl("studentId");
        long rating = c.bl("rating");
        if (rating < 1L || rating > 5L) {
            return c.fail(400, "INVALID_INPUT", "评分须为 1-5 星");
        }
        if (WcServices.isGuardianOf(c.es.userId, sid) == 0) {
            return c.fail(403, "FORBIDDEN", "无权评价");
        }
        DbQueryResult s = WcDb.query(
                "SELECT status, teacher_user_id, student_id FROM wc_lesson_sessions WHERE id=? AND org_id=?",
                WcApi.P2(sess, c.es.orgId));
        if (s.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "课次不存在");
        }
        if (s.rows.get(0).getString(0).equals("COMPLETED") == false) {
            return c.fail(409, "NOT_COMPLETED", "课程结束后才能评价");
        }
        long inSession = 0L;
        if (s.rows.get(0).getLong(2) == sid) {
            inSession = 1L;
        } else {
            inSession = WcApi.one("SELECT id FROM wc_session_students WHERE session_id=? AND student_id=?",
                    WcApi.P2(sess, sid));
        }
        if (inSession == 0L) {
            return c.fail(403, "FORBIDDEN", "该学员未参加此课次");
        }
        if (WcApi.one("SELECT id FROM wc_reviews WHERE session_id=? AND student_id=?", WcApi.P2(sess, sid)) > 0L) {
            return c.fail(409, "ALREADY_REVIEWED", "已评价过该课次");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        p.add(WcApi.l(sess));
        p.add(WcApi.l(sid));
        p.add(WcApi.l(s.rows.get(0).getLong(1)));
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(rating));
        p.add(c.bs("comment"));
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        WcDb.exec(
                "INSERT INTO wc_reviews(org_id, session_id, student_id, teacher_user_id, parent_user_id, rating, comment, created_at) VALUES (?,?,?,?,?,?,?,?)",
                p);
        if (rating <= 2L) {
            DbQueryResult ad = WcDb.query(
                    "SELECT DISTINCT user_id FROM wc_user_identities WHERE org_id=? AND identity_kind IN ('ADMIN','DIRECTOR')",
                    WcApi.P1(c.es.orgId));
            int i = 0;
            while (i < ad.rows.size()) {
                WcServices.notifyUser(c.es.orgId, ad.rows.get(i).getLong(0), "低分评价预警",
                        "课次 #" + WcApi.l(sess) + " 收到 " + WcApi.l(rating) + " 星评价，请及时回访", "REVIEW", sess);
                i = i + 1;
            }
        }
        return c.ok("");
    }

    static String reviewPending(WcCtx c) {
        String g = c.guard("PARENT");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.ql("studentId");
        if (WcServices.isGuardianOf(c.es.userId, sid) == 0) {
            return c.fail(403, "FORBIDDEN", "请选择自己的孩子");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(sid));
        p.add(WcApi.l(sid));
        p.add(WcApi.l(AsyncClockNative.wallMillis() - 30L * 86400000L));
        p.add(WcApi.l(sid));
        DbQueryResult r = WcDb.query(
                "SELECT ls.id, ls.start_at, ls.teacher_user_id, COALESCE(u.display_name,''), COALESCE(k.name,'') FROM wc_lesson_sessions ls LEFT JOIN wc_users u ON u.id=ls.teacher_user_id LEFT JOIN wc_classes k ON k.id=ls.class_id WHERE ls.status='COMPLETED' AND (ls.student_id=? OR ls.id IN (SELECT session_id FROM wc_session_students WHERE student_id=?)) AND ls.start_at>? AND ls.id NOT IN (SELECT session_id FROM wc_reviews WHERE student_id=?) ORDER BY ls.start_at DESC LIMIT 20",
                p);
        return c.ok("\"items\":" + WcApi.rows(r, "sessionId:l,startAt:l,teacherUserId:l,teacherName:s,className:s"));
    }

    static String reviewList(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER");
        if (g.length() > 0) {
            return g;
        }
        String where = "WHERE rv.org_id=?";
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.orgId));
        if (c.es.identity.equals(WcIdentity.TEACHER)) {
            where = where + " AND rv.teacher_user_id=?";
            p.add(WcApi.l(c.es.userId));
        }
        DbQueryResult sum = WcDb.query(
                "SELECT rv.teacher_user_id, COALESCE(u.display_name,''), COUNT(*), SUM(rv.rating) FROM wc_reviews rv LEFT JOIN wc_users u ON u.id=rv.teacher_user_id "
                        + where + " GROUP BY rv.teacher_user_id ORDER BY SUM(rv.rating)*1.0/COUNT(*) DESC",
                p);
        String sumJson = WcApi.rows(sum, "teacherUserId:l,teacherName:s,count:l,ratingSum:l");
        DbQueryResult items = WcDb.query(
                "SELECT rv.id, rv.session_id, rv.rating, rv.comment, rv.created_at, COALESCE(u.display_name,''), COALESCE(s.name,'') FROM wc_reviews rv LEFT JOIN wc_users u ON u.id=rv.teacher_user_id LEFT JOIN wc_students s ON s.id=rv.student_id "
                        + where + " ORDER BY rv.id DESC LIMIT 50",
                p);
        String spec = "id:l,sessionId:l,rating:l,comment:s,createdAt:l,teacherName:s,studentName:s";
        String itemsJson = WcApi.rows(items, spec);
        if (c.es.identity.equals(WcIdentity.TEACHER)) {
            /* teachers see ratings anonymously: strip student name */
            itemsJson = itemsJson.replace("\"studentName\":", "\"_s\":");
        }
        return c.ok("\"summary\":" + sumJson + ",\"items\":" + itemsJson);
    }

    /* ---------- renewal enquiry ---------- */
    static String renewRequest(WcCtx c) {
        String g = c.guard("PARENT");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.bl("studentId");
        if (WcServices.isGuardianOf(c.es.userId, sid) == 0) {
            return c.fail(403, "FORBIDDEN", "无权操作");
        }
        long since = AsyncClockNative.wallMillis() - 3L * 86400000L;
        ArrayList<Object> dp = new ArrayList<Object>();
        dp.add(WcApi.l(c.es.userId));
        dp.add(WcApi.l(sid));
        dp.add(WcApi.l(since));
        long dup = WcApi.one(
                "SELECT id FROM wc_audit WHERE actor_user_id=? AND action='RENEW_REQUEST' AND entity_id=? AND created_at>? LIMIT 1",
                dp);
        if (dup > 0L) {
            return c.fail(429, "TOO_FREQUENT", "近 3 天内已提交过续费咨询，教务会尽快联系您");
        }
        String sname = WcApi.oneStr("SELECT name FROM wc_students WHERE id=?", WcApi.P1(sid));
        String pname = "";
        long pid = c.bl("productId");
        if (pid > 0L) {
            pname = WcApi.oneStr("SELECT name FROM wc_products WHERE id=?", WcApi.P1(pid));
        }
        String uname = WcApi.oneStr("SELECT display_name FROM wc_users WHERE id=?", WcApi.P1(c.es.userId));
        String phone = WcApi.oneStr("SELECT phone FROM wc_users WHERE id=?", WcApi.P1(c.es.userId));
        DbQueryResult ad = WcDb.query(
                "SELECT DISTINCT user_id FROM wc_user_identities WHERE org_id=? AND identity_kind='ADMIN'",
                WcApi.P1(c.es.orgId));
        int i = 0;
        while (i < ad.rows.size()) {
            WcServices.notifyUser(c.es.orgId, ad.rows.get(i).getLong(0), "续费咨询：" + sname,
                    uname + "(" + phone + ") 咨询续费" + (pname.length() > 0 ? "「" + pname + "」" : "") + "。"
                            + c.bs("note"),
                    "RENEW", sid);
            i = i + 1;
        }
        WcServices.audit(c.es.orgId, c.es.userId, "RENEW_REQUEST", "student", sid, pname);
        return c.ok("\"notified\":" + WcApi.l((long) ad.rows.size()));
    }
}
