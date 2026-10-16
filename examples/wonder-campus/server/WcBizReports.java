/**
 * Management reports, renewal alerts, payroll, dashboards, CSV export.
 * @author 张晓达
 */
class WcBizReports {
    static String OPS = "ADMIN,DIRECTOR";

    static long monthStart(long now) {
        long day = WcApi.dayStart(now);
        /* civil-from-days (UTC+8) to find day-of-month */
        long local = (day + 8L * 3600000L) / 86400000L;
        long z = local + 719468L;
        long era = z / 146097L;
        long doe = z - era * 146097L;
        long yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L;
        long doy = doe - (365L * yoe + yoe / 4L - yoe / 100L);
        long mp = (5L * doy + 2L) / 153L;
        long dom = doy - (153L * mp + 2L) / 5L + 1L;
        return day - (dom - 1L) * 86400000L;
    }

    static long fromParam(WcCtx c) {
        long f = c.ql("from");
        if (f <= 0L) {
            f = monthStart(AsyncClockNative.wallMillis());
        }
        return f;
    }

    static long toParam(WcCtx c) {
        long t = c.ql("to");
        if (t <= 0L) {
            t = AsyncClockNative.wallMillis() + 1L;
        }
        return t;
    }

    static ArrayList<Object> range3(long org, long from, long to) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(org));
        p.add(WcApi.l(from));
        p.add(WcApi.l(to));
        return p;
    }

    static long renewalCount(long org) {
        long now = AsyncClockNative.wallMillis();
        long thr = WcNum.parseLong(WcBizAdmin.settingGet(org, "renewMilliThreshold", "2000"));
        long days = WcNum.parseLong(WcBizAdmin.settingGet(org, "renewDays", "30"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(org));
        p.add(WcApi.l(now));
        p.add(WcApi.l(thr));
        p.add(WcApi.l(now + days * 86400000L));
        long packs = WcApi.one(
                "SELECT COUNT(*) FROM wc_contracts ct JOIN wc_pack_balances b ON b.contract_id=ct.id WHERE ct.org_id=? AND ct.billing_mode='PACK' AND ct.status='ACTIVE' AND ct.pack_expire_at>? AND (b.paid_remaining_milli+b.bonus_remaining_milli<=CAST(? AS INTEGER) OR ct.pack_expire_at<=?)",
                p);
        ArrayList<Object> tp = new ArrayList<Object>();
        tp.add(WcApi.l(org));
        tp.add(WcApi.l(now));
        tp.add(WcApi.l(now + 14L * 86400000L));
        long terms = WcApi.one(
                "SELECT COUNT(*) FROM wc_contracts WHERE org_id=? AND billing_mode='TERM' AND status='ACTIVE' AND term_end>? AND term_end<=?",
                tp);
        return packs + terms;
    }

    static String overview(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long org = c.es.orgId;
        long from = fromParam(c);
        long to = toParam(c);
        ArrayList<Object> r3 = range3(org, from, to);
        long received = WcApi.one(
                "SELECT COALESCE(SUM(amount_cents),0) FROM wc_payments WHERE org_id=? AND created_at>=? AND created_at<?",
                r3);
        long refunded = WcApi.one(
                "SELECT COALESCE(SUM(amount_cents),0) FROM wc_refunds WHERE org_id=? AND created_at>=? AND created_at<?",
                r3);
        long consume = WcApi.one(
                "SELECT COALESCE(SUM(amount_cents),0) FROM wc_consume_revenue WHERE org_id=? AND created_at>=? AND created_at<?",
                r3);
        long newStudents = WcApi.one(
                "SELECT COUNT(*) FROM wc_students WHERE org_id=? AND created_at>=? AND created_at<?", r3);
        long newContracts = WcApi.one(
                "SELECT COUNT(*) FROM wc_contracts WHERE org_id=? AND created_at>=? AND created_at<?", r3);
        long activeStudents = WcApi.one("SELECT COUNT(*) FROM wc_students WHERE org_id=? AND status='ACTIVE'",
                WcApi.P1(org));
        long completed = WcApi.one(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND status='COMPLETED' AND start_at>=? AND start_at<?",
                r3);
        long present = WcApi.one(
                "SELECT COUNT(*) FROM wc_attendance WHERE org_id=? AND created_at>=? AND created_at<? AND status IN ('PRESENT','LATE')",
                r3);
        long absent = WcApi.one(
                "SELECT COUNT(*) FROM wc_attendance WHERE org_id=? AND created_at>=? AND created_at<? AND status='ABSENT'",
                r3);
        long leaves = WcApi.one(
                "SELECT COUNT(*) FROM wc_leave_requests WHERE org_id=? AND created_at>=? AND created_at<? AND status!='CANCELLED'",
                r3);
        long leadsNew = WcApi.one("SELECT COUNT(*) FROM wc_leads WHERE org_id=? AND created_at>=? AND created_at<?", r3);
        long leadsWon = WcApi.one(
                "SELECT COUNT(*) FROM wc_leads WHERE org_id=? AND status='WON' AND updated_at>=? AND updated_at<?", r3);
        long leadsOpen = WcApi.one(
                "SELECT COUNT(*) FROM wc_leads WHERE org_id=? AND status IN ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE')",
                WcApi.P1(org));
        long arrears = WcApi.one(
                "SELECT COALESCE(SUM(CASE WHEN receivable_cents-received_cents+refunded_cents>0 THEN receivable_cents-received_cents+refunded_cents ELSE 0 END),0) FROM wc_contracts WHERE org_id=? AND status='ACTIVE'",
                WcApi.P1(org));
        long marks = present + absent;
        long rate = 0L;
        if (marks > 0L) {
            rate = present * 1000L / marks;
        }
        long conv = 0L;
        if (leadsNew > 0L) {
            conv = leadsWon * 1000L / leadsNew;
        }
        return c.ok("\"from\":" + WcApi.l(from) + ",\"to\":" + WcApi.l(to) + ",\"receivedCents\":" + WcApi.l(received)
                + ",\"refundedCents\":" + WcApi.l(refunded) + ",\"netCents\":" + WcApi.l(received - refunded)
                + ",\"consumeRevenueCents\":" + WcApi.l(consume) + ",\"newStudents\":" + WcApi.l(newStudents)
                + ",\"activeStudents\":" + WcApi.l(activeStudents) + ",\"newContracts\":" + WcApi.l(newContracts)
                + ",\"sessionsCompleted\":" + WcApi.l(completed) + ",\"present\":" + WcApi.l(present)
                + ",\"absent\":" + WcApi.l(absent) + ",\"leaves\":" + WcApi.l(leaves)
                + ",\"attendanceRatePermille\":" + WcApi.l(rate) + ",\"leadsNew\":" + WcApi.l(leadsNew)
                + ",\"leadsWon\":" + WcApi.l(leadsWon) + ",\"leadsOpen\":" + WcApi.l(leadsOpen)
                + ",\"conversionPermille\":" + WcApi.l(conv) + ",\"arrearsCents\":" + WcApi.l(arrears)
                + ",\"renewalDue\":" + WcApi.l(renewalCount(org)));
    }

    static String daily(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long days = c.ql("days");
        if (days <= 0L || days > 90L) {
            days = 14L;
        }
        long today = WcApi.dayStart(AsyncClockNative.wallMillis());
        String out = "[";
        long i = days - 1L;
        long idx = 0L;
        while (i >= 0L) {
            long ds = today - i * 86400000L;
            ArrayList<Object> p = range3(c.es.orgId, ds, ds + 86400000L);
            long rec = WcApi.one(
                    "SELECT COALESCE(SUM(amount_cents),0) FROM wc_payments WHERE org_id=? AND created_at>=? AND created_at<?",
                    p);
            long ref = WcApi.one(
                    "SELECT COALESCE(SUM(amount_cents),0) FROM wc_refunds WHERE org_id=? AND created_at>=? AND created_at<?",
                    p);
            long cons = WcApi.one(
                    "SELECT COALESCE(SUM(amount_cents),0) FROM wc_consume_revenue WHERE org_id=? AND created_at>=? AND created_at<?",
                    p);
            long cls = WcApi.one(
                    "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND status='COMPLETED' AND start_at>=? AND start_at<?",
                    p);
            if (idx > 0L) {
                out = out + ",";
            }
            out = out + "{\"day\":" + WcApi.l(ds) + ",\"receivedCents\":" + WcApi.l(rec) + ",\"refundedCents\":"
                    + WcApi.l(ref) + ",\"consumeCents\":" + WcApi.l(cons) + ",\"sessions\":" + WcApi.l(cls) + "}";
            idx = idx + 1L;
            i = i - 1L;
        }
        return c.ok("\"items\":" + out + "]");
    }

    static String arrears(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query(
                "SELECT ct.id, ct.student_id, s.name, COALESCE(co.name,''), ct.receivable_cents, ct.received_cents, ct.refunded_cents, ct.receivable_cents-ct.received_cents+ct.refunded_cents, COALESCE((SELECT g.phone FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=ct.student_id ORDER BY sg.id LIMIT 1),''), ct.created_at FROM wc_contracts ct JOIN wc_students s ON s.id=ct.student_id LEFT JOIN wc_courses co ON co.id=ct.course_id WHERE ct.org_id=? AND ct.status='ACTIVE' AND ct.receivable_cents-ct.received_cents+ct.refunded_cents>0 ORDER BY ct.receivable_cents-ct.received_cents+ct.refunded_cents DESC LIMIT 200",
                WcApi.P1(c.es.orgId));
        return c.ok("\"items\":" + WcApi.rows(r,
                "contractId:l,studentId:l,studentName:s,courseName:s,receivableCents:l,receivedCents:l,refundedCents:l,owedCents:l,phone:s,createdAt:l"));
    }

    static String renewal(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long org = c.es.orgId;
        long now = AsyncClockNative.wallMillis();
        long thr = WcNum.parseLong(WcBizAdmin.settingGet(org, "renewMilliThreshold", "2000"));
        long days = WcNum.parseLong(WcBizAdmin.settingGet(org, "renewDays", "30"));
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(org));
        p.add(WcApi.l(now));
        p.add(WcApi.l(thr));
        p.add(WcApi.l(now + days * 86400000L));
        DbQueryResult packs = WcDb.query(
                "SELECT ct.id, ct.student_id, s.name, COALESCE(co.name,''), b.paid_remaining_milli+b.bonus_remaining_milli, ct.pack_expire_at, COALESCE((SELECT g.phone FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=ct.student_id ORDER BY sg.id LIMIT 1),'') FROM wc_contracts ct JOIN wc_pack_balances b ON b.contract_id=ct.id JOIN wc_students s ON s.id=ct.student_id LEFT JOIN wc_courses co ON co.id=ct.course_id WHERE ct.org_id=? AND ct.billing_mode='PACK' AND ct.status='ACTIVE' AND ct.pack_expire_at>? AND (b.paid_remaining_milli+b.bonus_remaining_milli<=CAST(? AS INTEGER) OR ct.pack_expire_at<=?) ORDER BY b.paid_remaining_milli+b.bonus_remaining_milli LIMIT 200",
                p);
        ArrayList<Object> tp = new ArrayList<Object>();
        tp.add(WcApi.l(org));
        tp.add(WcApi.l(now));
        tp.add(WcApi.l(now + 14L * 86400000L));
        DbQueryResult terms = WcDb.query(
                "SELECT ct.id, ct.student_id, s.name, COALESCE(co.name,''), ct.term_end, ct.term_promised_sessions, COALESCE((SELECT g.phone FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=ct.student_id ORDER BY sg.id LIMIT 1),'') FROM wc_contracts ct JOIN wc_students s ON s.id=ct.student_id LEFT JOIN wc_courses co ON co.id=ct.course_id WHERE ct.org_id=? AND ct.billing_mode='TERM' AND ct.status='ACTIVE' AND ct.term_end>? AND ct.term_end<=? ORDER BY ct.term_end LIMIT 200",
                tp);
        return c.ok("\"packs\":" + WcApi.rows(packs,
                "contractId:l,studentId:l,studentName:s,courseName:s,remainingMilli:l,expireAt:l,phone:s")
                + ",\"terms\":" + WcApi.rows(terms,
                        "contractId:l,studentId:l,studentName:s,courseName:s,termEnd:l,promised:l,phone:s"));
    }

    static String funnel(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult st = WcDb.query("SELECT status, COUNT(*) FROM wc_leads WHERE org_id=? GROUP BY status",
                WcApi.P1(c.es.orgId));
        DbQueryResult src = WcDb.query(
                "SELECT source, COUNT(*), SUM(CASE WHEN status='WON' THEN 1 ELSE 0 END) FROM wc_leads WHERE org_id=? GROUP BY source ORDER BY COUNT(*) DESC",
                WcApi.P1(c.es.orgId));
        DbQueryResult lost = WcDb.query(
                "SELECT lost_reason, COUNT(*) FROM wc_leads WHERE org_id=? AND status='LOST' GROUP BY lost_reason ORDER BY COUNT(*) DESC LIMIT 10",
                WcApi.P1(c.es.orgId));
        return c.ok("\"byStatus\":" + WcApi.rows(st, "status:s,count:l") + ",\"bySource\":"
                + WcApi.rows(src, "source:s,count:l,won:l") + ",\"lostReasons\":" + WcApi.rows(lost, "reason:s,count:l"));
    }

    static DbQueryResult payrollQuery(long org, long teacher, long from, long to) {
        String sql = "SELECT u.id, u.display_name, COUNT(ls.id), COALESCE((SELECT per_session_cents FROM wc_teacher_rates WHERE teacher_user_id=u.id),0), SUM(ls.end_at-ls.start_at) FROM wc_lesson_sessions ls JOIN wc_users u ON u.id=ls.teacher_user_id WHERE ls.org_id=? AND ls.status='COMPLETED' AND ls.start_at>=? AND ls.start_at<?";
        ArrayList<Object> p = range3(org, from, to);
        if (teacher > 0L) {
            sql = sql + " AND ls.teacher_user_id=?";
            p.add(WcApi.l(teacher));
        }
        sql = sql + " GROUP BY u.id ORDER BY COUNT(ls.id) DESC";
        return WcDb.query(sql, p);
    }

    static String payrollJson(DbQueryResult q) {
        String out = "[";
        int i = 0;
        while (i < q.rows.size()) {
            Row w = q.rows.get(i);
            long cnt = w.getLong(2);
            long rate = w.getLong(3);
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"teacherUserId\":" + WcApi.l(w.getLong(0)) + ",\"teacherName\":" + WcApi.q(w.getString(1))
                    + ",\"sessions\":" + WcApi.l(cnt) + ",\"minutes\":" + WcApi.l(w.getLong(4) / 60000L)
                    + ",\"perSessionCents\":" + WcApi.l(rate) + ",\"payCents\":" + WcApi.l(cnt * rate) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static String payroll(WcCtx c) {
        String g = c.guard("ADMIN,DIRECTOR,TEACHER");
        if (g.length() > 0) {
            return g;
        }
        long teacher = 0L;
        if (c.es.identity.equals(WcIdentity.TEACHER)) {
            teacher = c.es.userId;
        } else {
            teacher = c.ql("teacherId");
        }
        long from = fromParam(c);
        long to = toParam(c);
        String rows = payrollJson(payrollQuery(c.es.orgId, teacher, from, to));
        return c.ok("\"from\":" + WcApi.l(from) + ",\"to\":" + WcApi.l(to) + ",\"items\":" + rows);
    }

    /* ---------- dashboards ---------- */
    static String adminDashboard(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        long org = c.es.orgId;
        long now = AsyncClockNative.wallMillis();
        long ds = WcApi.dayStart(now);
        ArrayList<Object> r3 = range3(org, ds, ds + 86400000L);
        long today = WcApi.one(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND start_at>=? AND start_at<? AND status IN ('SCHEDULED','RESCHEDULED','COMPLETED')",
                r3);
        long todayDone = WcApi.one(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND start_at>=? AND start_at<? AND status='COMPLETED'",
                r3);
        long pendingLeaves = WcApi.one(
                "SELECT COUNT(*) FROM wc_leave_requests WHERE org_id=? AND status='PENDING' AND cancelled_at=0",
                WcApi.P1(org));
        long openMakeup = WcApi.one("SELECT COUNT(*) FROM wc_makeup_credits WHERE org_id=? AND status='OPEN'",
                WcApi.P1(org));
        ArrayList<Object> lp = WcApi.P2(org, now + 86400000L);
        long followDue = WcApi.one(
                "SELECT COUNT(*) FROM wc_leads WHERE org_id=? AND next_follow_at>0 AND next_follow_at<=? AND status IN ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE')",
                lp);
        long newLeads = WcApi.one("SELECT COUNT(*) FROM wc_leads WHERE org_id=? AND status='NEW'", WcApi.P1(org));
        long arrears = WcApi.one(
                "SELECT COALESCE(SUM(CASE WHEN receivable_cents-received_cents+refunded_cents>0 THEN receivable_cents-received_cents+refunded_cents ELSE 0 END),0) FROM wc_contracts WHERE org_id=? AND status='ACTIVE'",
                WcApi.P1(org));
        long todayIn = WcApi.one(
                "SELECT COALESCE(SUM(amount_cents),0) FROM wc_payments WHERE org_id=? AND created_at>=? AND created_at<?",
                r3);
        long unreadNotes = WcApi.one("SELECT COUNT(*) FROM wc_notifications WHERE user_id=? AND read_at=0",
                WcApi.P1(c.es.userId));
        long unsettled = WcApi.one(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE org_id=? AND status IN ('SCHEDULED','RESCHEDULED') AND end_at<? AND start_at>?",
                WcApi.P3(org, now, now - 7L * 86400000L));
        return c.ok("\"todaySessions\":" + WcApi.l(today) + ",\"todayDone\":" + WcApi.l(todayDone)
                + ",\"pendingLeaves\":" + WcApi.l(pendingLeaves) + ",\"openMakeup\":" + WcApi.l(openMakeup)
                + ",\"followDue\":" + WcApi.l(followDue) + ",\"newLeads\":" + WcApi.l(newLeads)
                + ",\"arrearsCents\":" + WcApi.l(arrears) + ",\"todayReceivedCents\":" + WcApi.l(todayIn)
                + ",\"renewalDue\":" + WcApi.l(renewalCount(org)) + ",\"unreadNotes\":" + WcApi.l(unreadNotes)
                + ",\"unsettledSessions\":" + WcApi.l(unsettled));
    }

    static String parentOverview(WcCtx c) {
        String g = c.guard("PARENT");
        if (g.length() > 0) {
            return g;
        }
        long sid = c.ql("studentId");
        if (WcServices.isGuardianOf(c.es.userId, sid) == 0) {
            return c.fail(403, "FORBIDDEN", "请选择自己的孩子");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> np = new ArrayList<Object>();
        np.add(WcApi.l(sid));
        np.add(WcApi.l(sid));
        np.add(WcApi.l(now));
        DbQueryResult nx = WcDb.query(
                "SELECT ls.id, ls.start_at, ls.end_at, ls.status, COALESCE(k.name,''), COALESCE(u.display_name,''), COALESCE(r.name,''), COALESCE(ca.name,'') FROM wc_lesson_sessions ls LEFT JOIN wc_classes k ON k.id=ls.class_id LEFT JOIN wc_users u ON u.id=ls.teacher_user_id LEFT JOIN wc_rooms r ON r.id=ls.room_id LEFT JOIN wc_campuses ca ON ca.id=ls.campus_id WHERE (ls.student_id=? OR ls.id IN (SELECT session_id FROM wc_session_students WHERE student_id=?)) AND ls.start_at>=? AND ls.status IN ('SCHEDULED','RESCHEDULED') ORDER BY ls.start_at LIMIT 1",
                np);
        String next = "null";
        if (nx.rows.size() > 0) {
            Row w = nx.rows.get(0);
            next = "{\"sessionId\":" + WcApi.l(w.getLong(0)) + ",\"startAt\":" + WcApi.l(w.getLong(1)) + ",\"endAt\":"
                    + WcApi.l(w.getLong(2)) + ",\"status\":" + WcApi.q(w.getString(3)) + ",\"className\":"
                    + WcApi.q(w.getString(4)) + ",\"teacherName\":" + WcApi.q(w.getString(5)) + ",\"roomName\":"
                    + WcApi.q(w.getString(6)) + ",\"campusName\":" + WcApi.q(w.getString(7)) + "}";
        }
        DbQueryResult ct = WcDb.query(
                "SELECT ct.id, ct.billing_mode, COALESCE(co.name,''), COALESCE(b.paid_remaining_milli+b.bonus_remaining_milli,0), ct.pack_expire_at, ct.term_end, ct.term_promised_sessions, ct.receivable_cents-ct.received_cents+ct.refunded_cents FROM wc_contracts ct LEFT JOIN wc_courses co ON co.id=ct.course_id LEFT JOIN wc_pack_balances b ON b.contract_id=ct.id WHERE ct.student_id=? AND ct.status='ACTIVE' ORDER BY ct.id DESC",
                WcApi.P1(sid));
        String contracts = WcApi.rows(ct,
                "id:l,mode:s,courseName:s,remainingMilli:l,expireAt:l,termEnd:l,promised:l,owedCents:l");
        long remainTotal = WcApi.one(
                "SELECT COALESCE(SUM(b.paid_remaining_milli+b.bonus_remaining_milli),0) FROM wc_pack_balances b JOIN wc_contracts ct ON ct.id=b.contract_id WHERE ct.student_id=? AND ct.status='ACTIVE' AND ct.pack_expire_at>?",
                WcApi.P2(sid, now));
        long thr = WcNum.parseLong(WcBizAdmin.settingGet(c.es.orgId, "renewMilliThreshold", "2000"));
        long pendingHw = WcApi.one(
                "SELECT COUNT(*) FROM wc_homework h WHERE h.org_id=? AND (h.student_id=? OR (h.student_id=0 AND CAST(? AS INTEGER) IN ("
                        + WcBizEngage.targetSql()
                        + "))) AND h.id NOT IN (SELECT homework_id FROM wc_homework_submissions WHERE student_id=?)",
                WcBizEngage.four(c.es.orgId, sid, sid, sid));
        long pendingReview = WcApi.one(
                "SELECT COUNT(*) FROM wc_lesson_sessions ls WHERE ls.status='COMPLETED' AND (ls.student_id=? OR ls.id IN (SELECT session_id FROM wc_session_students WHERE student_id=?)) AND ls.start_at>? AND ls.id NOT IN (SELECT session_id FROM wc_reviews WHERE student_id=?)",
                WcBizEngage.four(sid, sid, now - 30L * 86400000L, sid));
        long unreadNotes = WcApi.one("SELECT COUNT(*) FROM wc_notifications WHERE user_id=? AND read_at=0",
                WcApi.P1(c.es.userId));
        long newFb = WcApi.one(
                "SELECT COUNT(*) FROM wc_feedback WHERE student_id=? AND status='PUBLISHED' AND published_at>?",
                WcApi.P2(sid, now - 7L * 86400000L));
        DbQueryResult fb = WcDb.query(
                "SELECT f.id, f.body, f.published_at, COALESCE(u.display_name,'') FROM wc_feedback f LEFT JOIN wc_users u ON u.id=f.teacher_user_id WHERE f.student_id=? AND f.status='PUBLISHED' ORDER BY f.id DESC LIMIT 1",
                WcApi.P1(sid));
        long present = WcApi.one("SELECT COUNT(*) FROM wc_attendance WHERE student_id=? AND status IN ('PRESENT','LATE')",
                WcApi.P1(sid));
        return c.ok("\"next\":" + next + ",\"contracts\":" + contracts + ",\"remainingMilli\":" + WcApi.l(remainTotal)
                + ",\"renewHint\":" + (remainTotal <= thr && contractsExist(contracts) ? "1" : "0")
                + ",\"pendingHomework\":" + WcApi.l(pendingHw) + ",\"pendingReviews\":" + WcApi.l(pendingReview)
                + ",\"unreadNotes\":" + WcApi.l(unreadNotes) + ",\"newFeedback\":" + WcApi.l(newFb)
                + ",\"attended\":" + WcApi.l(present) + ",\"latestFeedback\":"
                + WcApi.rows(fb, "id:l,body:s,publishedAt:l,teacherName:s"));
    }

    static boolean contractsExist(String json) {
        return json.length() > 2;
    }

    static String scheduleRange(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        long from = c.ql("from");
        long to = c.ql("to");
        if (from <= 0L) {
            from = WcApi.dayStart(AsyncClockNative.wallMillis());
        }
        if (to <= 0L) {
            to = from + 7L * 86400000L;
        }
        String sql = "SELECT ls.id, ls.start_at, ls.end_at, ls.status, ls.student_id, COALESCE(s.name,''), COALESCE(k.name,''), COALESCE(co.name,''), ls.teacher_user_id, COALESCE(u.display_name,''), COALESCE(r.name,''), COALESCE(ca.name,''), ls.campus_id FROM wc_lesson_sessions ls LEFT JOIN wc_students s ON s.id=ls.student_id LEFT JOIN wc_classes k ON k.id=ls.class_id LEFT JOIN wc_courses co ON co.id=k.course_id LEFT JOIN wc_users u ON u.id=ls.teacher_user_id LEFT JOIN wc_rooms r ON r.id=ls.room_id LEFT JOIN wc_campuses ca ON ca.id=ls.campus_id WHERE ls.org_id=? AND ls.start_at>=? AND ls.start_at<?";
        ArrayList<Object> p = range3(c.es.orgId, from, to);
        if (c.es.identity.equals(WcIdentity.PARENT)) {
            long sid = c.ql("studentId");
            if (sid == 0L || WcServices.isGuardianOf(c.es.userId, sid) == 0) {
                return c.fail(403, "FORBIDDEN", "请选择自己的孩子");
            }
            sql = sql + " AND (ls.student_id=? OR ls.id IN (SELECT session_id FROM wc_session_students WHERE student_id=?))";
            p.add(WcApi.l(sid));
            p.add(WcApi.l(sid));
        } else if (c.es.identity.equals(WcIdentity.TEACHER)) {
            sql = sql + " AND ls.teacher_user_id=?";
            p.add(WcApi.l(c.es.userId));
        } else {
            long t = c.ql("teacherId");
            if (t > 0L) {
                sql = sql + " AND ls.teacher_user_id=?";
                p.add(WcApi.l(t));
            }
            long camp = c.ql("campusId");
            if (camp > 0L) {
                sql = sql + " AND ls.campus_id=?";
                p.add(WcApi.l(camp));
            }
        }
        sql = sql + " ORDER BY ls.start_at LIMIT 300";
        DbQueryResult r = WcDb.query(sql, p);
        return c.ok("\"from\":" + WcApi.l(from) + ",\"to\":" + WcApi.l(to) + ",\"items\":" + WcApi.rows(r,
                "id:l,startAt:l,endAt:l,status:s,studentId:l,studentName:s,className:s,courseName:s,teacherUserId:l,teacherName:s,roomName:s,campusName:s,campusId:l"));
    }

    static String teacherStudents(WcCtx c) {
        String g = c.guard("TEACHER");
        if (g.length() > 0) {
            return g;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(c.es.userId));
        p.add(WcApi.l(c.es.userId));
        DbQueryResult r = WcDb.query(
                "SELECT s.id, s.name, s.age_band, (SELECT COUNT(*) FROM wc_attendance a WHERE a.student_id=s.id AND a.status IN ('PRESENT','LATE')), (SELECT COUNT(*) FROM wc_attendance a WHERE a.student_id=s.id AND a.status='ABSENT'), (SELECT COUNT(*) FROM wc_feedback f WHERE f.student_id=s.id AND f.teacher_user_id="
                        + WcApi.l(c.es.userId)
                        + " AND f.status='PUBLISHED'), COALESCE((SELECT MAX(start_at) FROM wc_lesson_sessions WHERE student_id=s.id AND teacher_user_id="
                        + WcApi.l(c.es.userId)
                        + " AND status='COMPLETED'),0), s.note FROM wc_students s WHERE s.org_id=? AND s.id IN (SELECT student_id FROM wc_lesson_sessions WHERE teacher_user_id=? AND student_id>0 UNION SELECT ss.student_id FROM wc_session_students ss JOIN wc_lesson_sessions ls ON ls.id=ss.session_id WHERE ls.teacher_user_id="
                        + WcApi.l(c.es.userId) + ") ORDER BY s.id",
                WcApi.P2(c.es.orgId, c.es.userId));
        return c.ok("\"items\":" + WcApi.rows(r,
                "id:l,name:s,ageBand:s,present:l,absent:l,feedbacks:l,lastLessonAt:l,note:s"));
    }

    /* ---------- CSV export ---------- */
    static String export(WcCtx c) {
        String g = c.guard(OPS);
        if (g.length() > 0) {
            return g;
        }
        String type = c.qs("type");
        long org = c.es.orgId;
        String csv = "";
        if (type.equals("students")) {
            DbQueryResult r = WcDb.query(
                    "SELECT s.id, s.name, s.gender, s.birthday, s.status, COALESCE(ca.name,''), COALESCE((SELECT g.name FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=s.id ORDER BY sg.id LIMIT 1),''), COALESCE((SELECT g.phone FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=s.id ORDER BY sg.id LIMIT 1),'') FROM wc_students s LEFT JOIN wc_campuses ca ON ca.id=s.campus_id WHERE s.org_id=? ORDER BY s.id",
                    WcApi.P1(org));
            csv = "学员ID,姓名,性别,生日,状态,校区,家长,电话\n";
            int i = 0;
            while (i < r.rows.size()) {
                Row w = r.rows.get(i);
                csv = csv + WcApi.l(w.getLong(0)) + "," + WcApi.csvCell(w.getString(1)) + ","
                        + WcApi.csvCell(w.getString(2)) + "," + WcApi.csvCell(w.getString(3)) + ","
                        + WcApi.csvCell(w.getString(4)) + "," + WcApi.csvCell(w.getString(5)) + ","
                        + WcApi.csvCell(w.getString(6)) + "," + WcApi.csvCell(w.getString(7)) + "\n";
                i = i + 1;
            }
        } else if (type.equals("payments")) {
            long from = fromParam(c);
            long to = toParam(c);
            DbQueryResult r = WcDb.query(
                    "SELECT p.id, p.created_at, s.name, p.amount_cents, p.method, p.receipt_no, p.contract_id FROM wc_payments p JOIN wc_contracts ct ON ct.id=p.contract_id JOIN wc_students s ON s.id=ct.student_id WHERE p.org_id=? AND p.created_at>=? AND p.created_at<? ORDER BY p.id",
                    range3(org, from, to));
            csv = "收款ID,时间戳,学员,金额(元),方式,收据号,合同ID\n";
            int i = 0;
            while (i < r.rows.size()) {
                Row w = r.rows.get(i);
                csv = csv + WcApi.l(w.getLong(0)) + "," + WcApi.l(w.getLong(1)) + "," + WcApi.csvCell(w.getString(2))
                        + "," + WcApi.yuan(w.getLong(3)) + "," + WcApi.csvCell(w.getString(4)) + ","
                        + WcApi.csvCell(w.getString(5)) + "," + WcApi.l(w.getLong(6)) + "\n";
                i = i + 1;
            }
        } else if (type.equals("arrears")) {
            DbQueryResult r = WcDb.query(
                    "SELECT ct.id, s.name, ct.receivable_cents, ct.received_cents, ct.refunded_cents, ct.receivable_cents-ct.received_cents+ct.refunded_cents FROM wc_contracts ct JOIN wc_students s ON s.id=ct.student_id WHERE ct.org_id=? AND ct.status='ACTIVE' AND ct.receivable_cents-ct.received_cents+ct.refunded_cents>0 ORDER BY 6 DESC",
                    WcApi.P1(org));
            csv = "合同ID,学员,应收(元),实收(元),退款(元),欠款(元)\n";
            int i = 0;
            while (i < r.rows.size()) {
                Row w = r.rows.get(i);
                csv = csv + WcApi.l(w.getLong(0)) + "," + WcApi.csvCell(w.getString(1)) + ","
                        + WcApi.yuan(w.getLong(2)) + "," + WcApi.yuan(w.getLong(3)) + "," + WcApi.yuan(w.getLong(4))
                        + "," + WcApi.yuan(w.getLong(5)) + "\n";
                i = i + 1;
            }
        } else if (type.equals("payroll")) {
            long from = fromParam(c);
            long to = toParam(c);
            DbQueryResult pq = payrollQuery(org, 0L, from, to);
            csv = "老师ID,老师,完成课次,总分钟,单价(元),课酬(元)\n";
            int i = 0;
            while (i < pq.rows.size()) {
                Row w = pq.rows.get(i);
                long cnt = w.getLong(2);
                long rate = w.getLong(3);
                csv = csv + WcApi.l(w.getLong(0)) + "," + WcApi.csvCell(w.getString(1)) + "," + WcApi.l(cnt) + ","
                        + WcApi.l(w.getLong(4) / 60000L) + "," + WcApi.yuan(rate) + "," + WcApi.yuan(cnt * rate) + "\n";
                i = i + 1;
            }
        } else if (type.equals("leads")) {
            DbQueryResult r = WcDb.query(
                    "SELECT id, child_name, child_age, guardian_name, phone, source, status, lost_reason FROM wc_leads WHERE org_id=? ORDER BY id",
                    WcApi.P1(org));
            csv = "线索ID,孩子,年龄,家长,电话,来源,状态,流失原因\n";
            int i = 0;
            while (i < r.rows.size()) {
                Row w = r.rows.get(i);
                csv = csv + WcApi.l(w.getLong(0)) + "," + WcApi.csvCell(w.getString(1)) + "," + WcApi.l(w.getLong(2))
                        + "," + WcApi.csvCell(w.getString(3)) + "," + WcApi.csvCell(w.getString(4)) + ","
                        + WcApi.csvCell(w.getString(5)) + "," + WcApi.csvCell(w.getString(6)) + ","
                        + WcApi.csvCell(w.getString(7)) + "\n";
                i = i + 1;
            }
        } else {
            return c.fail(400, "INVALID_INPUT", "type 须为 students/payments/arrears/payroll/leads");
        }
        WcServices.audit(org, c.es.userId, "EXPORT", "report", 0L, type);
        c.contentType = "text/csv; charset=utf-8";
        c.fileName = type + ".csv";
        /* UTF-8 BOM so Excel opens Chinese correctly */
        return "\uFEFF" + csv;
    }
}
