/**
 * Wonder Campus mobile-loop: notify tasks, feedback draft/publish, batch check-in,
 * leave cancel, makeup credits, media, roster queries.
 * @author 张晓达
 */
class WcLoopNotify {
    static void enqueue(long orgId, long userId, String title, String body, String refType, long refId,
            String dedupeKey) {
        if (userId <= 0L) {
            return;
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(userId));
        p.add(title == null ? "" : title);
        p.add(body == null ? "" : body);
        p.add(refType == null ? "" : refType);
        p.add(CryptoParse.longToString(refId));
        p.add(WcNotifyTaskStatus.PENDING);
        p.add("0");
        p.add(dedupeKey == null ? "" : dedupeKey);
        p.add(CryptoParse.longToString(now));
        p.add("0");
        try {
            WcDb.exec(
                    "INSERT INTO wc_notify_tasks(org_id, user_id, title, body, ref_type, ref_id, status, attempts, dedupe_key, created_at, done_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    p);
        } catch (Exception ex) {
            /* dedupe: do not fail business txn */
        }
        flushPending(orgId, 20);
    }

    static void enqueueGuardiansOfSession(long orgId, long sessionId, String title, String body, String refType,
            long refId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(sessionId));
        DbQueryResult r = WcDb.query(
                "SELECT DISTINCT g.user_id, ss.student_id FROM wc_session_students ss JOIN wc_student_guardians sg ON sg.student_id=ss.student_id JOIN wc_guardians g ON g.id=sg.guardian_id WHERE ss.session_id=? AND g.user_id>0",
                p);
        int i = 0;
        while (i < r.rows.size()) {
            long uid = r.rows.get(i).getLong(0);
            long sid = r.rows.get(i).getLong(1);
            /* body must not leak other children's names — keep generic + student id only for own child */
            enqueue(orgId, uid, title, body + "（学员#" + CryptoParse.longToString(sid) + "）", refType, refId,
                    refType + "-" + CryptoParse.longToString(refId) + "-u" + CryptoParse.longToString(uid));
            i = i + 1;
        }
    }

    static void flushPending(long orgId, int limit) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        DbQueryResult r = WcDb.query(
                "SELECT id, user_id, title, body, ref_type, ref_id, attempts FROM wc_notify_tasks WHERE org_id=? AND status='PENDING' ORDER BY id LIMIT "
                        + String.valueOf(limit),
                p);
        int i = 0;
        while (i < r.rows.size()) {
            long tid = r.rows.get(i).getLong(0);
            long uid = r.rows.get(i).getLong(1);
            String title = r.rows.get(i).getString(2);
            String body = r.rows.get(i).getString(3);
            String refType = r.rows.get(i).getString(4);
            long refId = r.rows.get(i).getLong(5);
            int attempts = WcNum.parseInt(CryptoParse.longToString(r.rows.get(i).getLong(6)));
            try {
                WcServices.notifyUser(orgId, uid, title, body, refType, refId);
                ArrayList<Object> u = new ArrayList<Object>();
                u.add(WcNotifyTaskStatus.DONE);
                u.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
                u.add(CryptoParse.longToString(tid));
                WcDb.exec("UPDATE wc_notify_tasks SET status=?, done_at=?, attempts=attempts+1 WHERE id=?", u);
            } catch (Exception ex) {
                ArrayList<Object> u = new ArrayList<Object>();
                if (attempts + 1 >= 5) {
                    u.add(WcNotifyTaskStatus.FAILED);
                } else {
                    u.add(WcNotifyTaskStatus.PENDING);
                }
                u.add(CryptoParse.longToString(tid));
                WcDb.exec("UPDATE wc_notify_tasks SET status=?, attempts=attempts+1 WHERE id=?", u);
            }
            i = i + 1;
        }
    }
}

class WcLoopServices {
    static WcResult completeSession(long orgId, long sessionId, long actor) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcSessionStatus.COMPLETED);
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(orgId));
        WcDb.exec(
                "UPDATE wc_lesson_sessions SET status=?, completed_at=? WHERE id=? AND org_id=? AND status IN ('SCHEDULED','RESCHEDULED')",
                p);
        WcServices.audit(orgId, actor, "COMPLETE_SESSION", "session", sessionId, "");
        return WcResult.success("OK", "session completed (independent of check-in)",
                "{\"sessionId\":" + CryptoParse.longToString(sessionId) + "}");
    }

    static WcResult batchCheckIn(long orgId, long sessionId, String studentIdsCsv, long actor, String idemPrefix) {
        if (idemPrefix == null || idemPrefix.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键前缀");
        }
        if (teacherCanOperateSession(actor, sessionId) == 0) {
            return WcResult.fail("FORBIDDEN", "老师无权操作该课次");
        }
        ArrayList<Object> parts = splitCsv(studentIdsCsv);
        int okN = 0;
        int failN = 0;
        long totalConsume = 0L;
        String details = "[";
        int i = 0;
        int emitted = 0;
        while (i < parts.size()) {
            long studentId = WcNum.parseLong((String) parts.get(i));
            if (studentId > 0L) {
                String idem = idemPrefix + "-" + CryptoParse.longToString(studentId);
                WcResult r = WcServices.checkIn(orgId, sessionId, studentId, actor, idem);
                if (emitted > 0) {
                    details = details + ",";
                }
                emitted = emitted + 1;
                details = details + "{\"studentId\":" + CryptoParse.longToString(studentId) + ",\"ok\":"
                        + String.valueOf(r.ok) + ",\"code\":\"" + r.code + "\",\"message\":\"" + r.message + "\"";
                if (r.ok != 0) {
                    okN = okN + 1;
                    long c = WcServices.parseDocId(r.documentJson, "consumedMilli");
                    totalConsume = totalConsume + c;
                    details = details + ",\"consumedMilli\":" + CryptoParse.longToString(c);
                } else {
                    failN = failN + 1;
                }
                details = details + "}";
            }
            i = i + 1;
        }
        details = details + "]";
        return WcResult.success("OK",
                "批量签到：成功" + String.valueOf(okN) + "人，失败" + String.valueOf(failN) + "人，合计权益变化 "
                        + CryptoParse.longToString(totalConsume) + " milli",
                "{\"okCount\":" + String.valueOf(okN) + ",\"failCount\":" + String.valueOf(failN)
                        + ",\"totalConsumedMilli\":" + CryptoParse.longToString(totalConsume) + ",\"items\":"
                        + details + "}");
    }

    static int teacherCanOperateSession(long teacherUserId, long sessionId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(sessionId));
        Object t = WcDb.queryOne("SELECT teacher_user_id FROM wc_lesson_sessions WHERE id=?", p, new LongMapper());
        if (t == null) {
            return 0;
        }
        if (WcNum.asLong(t) == teacherUserId) {
            return 1;
        }
        /* admin with TEACHER identity may also operate via ADMIN path — checked at HTTP layer */
        if (WcServices.hasIdentity(teacherUserId, WcIdentity.ADMIN) != 0) {
            return 1;
        }
        return 0;
    }

    static ArrayList<Object> splitCsv(String csv) {
        ArrayList<Object> list = new ArrayList<Object>();
        if (csv == null || csv.length() == 0) {
            return list;
        }
        String cur = "";
        int i = 0;
        while (i < csv.length()) {
            int c = csv.charAt(i);
            if (c == 44) {
                list.add(cur);
                cur = "";
            } else {
                if (c != 32) {
                    cur = cur + WebUtil.byteChar(c);
                }
            }
            i = i + 1;
        }
        list.add(cur);
        return list;
    }

    static WcResult cancelLeave(long orgId, long leaveId, long parentUserId) {
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(leaveId));
        lp.add(CryptoParse.longToString(orgId));
        DbQueryResult lr = WcDb.query(
                "SELECT student_id, status, makeup_session_id FROM wc_leave_requests WHERE id=? AND org_id=?",
                lp);
        if (lr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "请假不存在");
        }
        long studentId = lr.rows.get(0).getLong(0);
        if (WcServices.isGuardianOf(parentUserId, studentId) == 0) {
            return WcResult.fail("FORBIDDEN", "非授权家长");
        }
        String st = lr.rows.get(0).getString(1);
        long makeupSid = lr.rows.get(0).getLong(2);
        if (st.equals(WcLeaveStatus.REJECTED)) {
            return WcResult.fail("INVALID_STATE", "已拒绝的请假不可取消");
        }
        if (makeupSid > 0L) {
            ArrayList<Object> ms = new ArrayList<Object>();
            ms.add(CryptoParse.longToString(makeupSid));
            Object mst = WcDb.queryOne("SELECT status FROM wc_lesson_sessions WHERE id=?", ms, new StringMapper());
            if (mst != null && ((String) mst).equals(WcSessionStatus.COMPLETED)) {
                return WcResult.fail("MAKEUP_DONE", "补课已完成，不能取消请假");
            }
            ArrayList<Object> att = new ArrayList<Object>();
            att.add(CryptoParse.longToString(makeupSid));
            Object has = WcDb.queryOne(
                    "SELECT id FROM wc_attendance WHERE session_id=? AND reversed_at=0 AND status IN ('PRESENT','LATE')",
                    att, new LongMapper());
            if (has != null) {
                return WcResult.fail("MAKEUP_ATTENDED", "补课已签到，不能取消请假");
            }
            /* cancel unused makeup session */
            ArrayList<Object> cu = new ArrayList<Object>();
            cu.add(WcSessionStatus.CANCELLED_BY_SCHOOL);
            cu.add("leave cancelled");
            cu.add(CryptoParse.longToString(makeupSid));
            WcDb.exec("UPDATE wc_lesson_sessions SET status=?, cancel_reason=? WHERE id=?", cu);
        }
        ArrayList<Object> mc = new ArrayList<Object>();
        mc.add(WcMakeupStatus.CANCELLED);
        mc.add(CryptoParse.longToString(leaveId));
        WcDb.exec("UPDATE wc_makeup_credits SET status=? WHERE leave_id=? AND status='OPEN'", mc);
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(leaveId));
        WcDb.exec(
                "UPDATE wc_leave_requests SET cancelled_at=?, status='REJECTED', resolved_at=? WHERE id=?",
                u);
        return WcResult.success("OK", "leave cancelled",
                "{\"leaveId\":" + CryptoParse.longToString(leaveId) + "}");
    }

    static void openMakeupCredit(long orgId, long leaveId, long studentId, long contractId, long sourceSessionId,
            long expiresAt) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(leaveId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(contractId));
        p.add(CryptoParse.longToString(sourceSessionId));
        p.add(WcMakeupStatus.OPEN);
        p.add("0");
        p.add(CryptoParse.longToString(expiresAt));
        p.add(CryptoParse.longToString(now));
        try {
            WcDb.exec(
                    "INSERT INTO wc_makeup_credits(org_id, leave_id, student_id, contract_id, source_session_id, status, used_session_id, expires_at, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    p);
        } catch (Exception ex) {
            /* unique leave_id */
        }
    }

    static WcResult consumeMakeupCredit(long orgId, long leaveId, long makeupSessionId) {
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(WcMakeupStatus.USED);
        u.add(CryptoParse.longToString(makeupSessionId));
        u.add(CryptoParse.longToString(leaveId));
        u.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        WcDb.exec(
                "UPDATE wc_makeup_credits SET status=?, used_session_id=? WHERE leave_id=? AND status='OPEN' AND (expires_at=0 OR expires_at>=?)",
                u);
        ArrayList<Object> c = new ArrayList<Object>();
        c.add(CryptoParse.longToString(leaveId));
        Object st = WcDb.queryOne("SELECT status FROM wc_makeup_credits WHERE leave_id=?", c, new StringMapper());
        if (st == null || ((String) st).equals(WcMakeupStatus.USED) == false) {
            return WcResult.fail("MAKEUP_CREDIT_BUSY", "补课资格不可用或已被并发占用");
        }
        return WcResult.success("OK", "makeup credit used", "{}");
    }

    static WcResult saveFeedbackDraft(long orgId, long sessionId, long studentId, long teacherId, String explored,
            String attempt, String encouragement, String tags, String idem) {
        if (teacherCanOperateSession(teacherId, sessionId) == 0) {
            return WcResult.fail("FORBIDDEN", "老师无权操作该课次");
        }
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_feedback WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            String bodyUp = (explored == null ? "" : explored) + " " + (attempt == null ? "" : attempt) + " "
                    + (encouragement == null ? "" : encouragement);
            ArrayList<Object> u = new ArrayList<Object>();
            u.add(explored == null ? "" : explored);
            u.add(attempt == null ? "" : attempt);
            u.add(encouragement == null ? "" : encouragement);
            u.add(tags == null ? "" : tags);
            u.add(bodyUp);
            u.add(CryptoParse.longToString(WcNum.asLong(exist)));
            WcDb.exec(
                    "UPDATE wc_feedback SET explored=?, attempt=?, encouragement=?, tags=?, body=? WHERE id=? AND status='DRAFT'",
                    u);
            return WcResult.success("OK", "draft updated",
                    "{\"feedbackId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + ",\"status\":\"DRAFT\"}");
        }
        long now = AsyncClockNative.wallMillis();
        String body = (explored == null ? "" : explored) + " " + (attempt == null ? "" : attempt) + " "
                + (encouragement == null ? "" : encouragement);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(teacherId));
        p.add(tags == null ? "" : tags);
        p.add(body);
        p.add(CryptoParse.longToString(now));
        p.add(idem);
        p.add(WcFeedbackStatus.DRAFT);
        p.add(explored == null ? "" : explored);
        p.add(attempt == null ? "" : attempt);
        p.add(encouragement == null ? "" : encouragement);
        p.add("0");
        long fid = WcDb.insertReturningId(
                "INSERT INTO wc_feedback(org_id, session_id, student_id, teacher_user_id, tags, body, created_at, idempotency_key, status, explored, attempt, encouragement, published_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
        return WcResult.success("OK", "draft saved",
                "{\"feedbackId\":" + CryptoParse.longToString(fid) + ",\"status\":\"DRAFT\"}");
    }

    static WcResult publishFeedbackDraft(long orgId, long feedbackId, long teacherId) {
        ArrayList<Object> fp = new ArrayList<Object>();
        fp.add(CryptoParse.longToString(feedbackId));
        fp.add(CryptoParse.longToString(orgId));
        DbQueryResult fr = WcDb.query(
                "SELECT session_id, student_id, status, explored, attempt, encouragement FROM wc_feedback WHERE id=? AND org_id=?",
                fp);
        if (fr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "反馈不存在");
        }
        if (teacherCanOperateSession(teacherId, fr.rows.get(0).getLong(0)) == 0) {
            return WcResult.fail("FORBIDDEN", "老师无权发布");
        }
        if (fr.rows.get(0).getString(2).equals(WcFeedbackStatus.PUBLISHED)) {
            return WcResult.success("OK", "already published",
                    "{\"feedbackId\":" + CryptoParse.longToString(feedbackId) + "}");
        }
        ArrayList<Object> badMedia = new ArrayList<Object>();
        badMedia.add(CryptoParse.longToString(feedbackId));
        Object pending = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_media_assets WHERE feedback_id=? AND deleted_at=0 AND size_bytes<=0",
                badMedia, new LongMapper());
        if (WcNum.asLong(pending) > 0L) {
            return WcResult.fail("MEDIA_INCOMPLETE", "存在未完成上传的作品，请等待上传完成或删除后再发布");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(WcFeedbackStatus.PUBLISHED);
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(feedbackId));
        WcDb.exec("UPDATE wc_feedback SET status=?, published_at=? WHERE id=?", u);
        long studentId = fr.rows.get(0).getLong(1);
        ArrayList<Object> gp = new ArrayList<Object>();
        gp.add(CryptoParse.longToString(studentId));
        DbQueryResult gr = WcDb.query(
                "SELECT g.user_id FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=?",
                gp);
        int i = 0;
        while (i < gr.rows.size()) {
            long uid = gr.rows.get(i).getLong(0);
            WcLoopNotify.enqueue(orgId, uid, "课堂反馈已发布",
                    "学员#" + CryptoParse.longToString(studentId) + " 的反馈已可查看", "feedback", feedbackId,
                    "fb-pub-" + CryptoParse.longToString(feedbackId) + "-u" + CryptoParse.longToString(uid));
            i = i + 1;
        }
        return WcResult.success("OK", "feedback published",
                "{\"feedbackId\":" + CryptoParse.longToString(feedbackId) + ",\"status\":\"PUBLISHED\""
                        + ",\"parentPreview\":{\"explored\":\"" + fr.rows.get(0).getString(3) + "\",\"attempt\":\""
                        + fr.rows.get(0).getString(4) + "\",\"encouragement\":\"" + fr.rows.get(0).getString(5)
                        + "\"}}");
    }

    static String parentFeedbackJson(long parentUserId, long studentId) {
        if (WcServices.isGuardianOf(parentUserId, studentId) == 0) {
            return "{\"ok\":0,\"code\":\"FORBIDDEN\",\"message\":\"无权查看该孩子反馈\"}";
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(studentId));
        DbQueryResult r = WcDb.query(
                "SELECT f.id, f.session_id, f.explored, f.attempt, f.encouragement, f.published_at,"
                        + " COALESCE(ls.start_at,0), COALESCE(ls.teacher_user_id,0), COALESCE(ls.campus_id,0)"
                        + " FROM wc_feedback f LEFT JOIN wc_lesson_sessions ls ON ls.id=f.session_id"
                        + " WHERE f.student_id=? AND f.status='PUBLISHED' ORDER BY f.id DESC LIMIT 30",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            long fid = r.rows.get(i).getLong(0);
            out = out + "{\"id\":" + CryptoParse.longToString(fid) + ",\"sessionId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(1)) + ",\"explored\":\""
                    + r.rows.get(i).getString(2) + "\",\"attempt\":\"" + r.rows.get(i).getString(3)
                    + "\",\"encouragement\":\"" + r.rows.get(i).getString(4) + "\",\"publishedAt\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(5)) + ",\"sessionStartAt\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(6)) + ",\"teacherUserId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(7)) + ",\"campusId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(8))
                    + ",\"mediaIds\":" + mediaIdsForFeedback(fid) + "}";
            i = i + 1;
        }
        out = out + "]";
        return "{\"ok\":1,\"studentId\":" + CryptoParse.longToString(studentId) + ",\"items\":" + out + "}";
    }

    static WcResult registerMedia(long orgId, long ownerUserId, long studentId, long sessionId, long feedbackId,
            String storagePath, String mime, long sizeBytes) {
        if (sizeBytes > 2097152L) {
            return WcResult.fail("TOO_LARGE", "图片超过 2MB 限制");
        }
        if (storagePath == null || storagePath.indexOf("..") >= 0) {
            return WcResult.fail("BAD_PATH", "非法存储路径");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(ownerUserId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(feedbackId));
        p.add(storagePath);
        p.add(mime == null ? "image/jpeg" : mime);
        p.add(CryptoParse.longToString(sizeBytes));
        p.add(CryptoParse.longToString(now));
        p.add("0");
        long mid = WcDb.insertReturningId(
                "INSERT INTO wc_media_assets(org_id, owner_user_id, student_id, session_id, feedback_id, storage_path, mime, size_bytes, created_at, deleted_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                p);
        return WcResult.success("OK", "media registered (auth-gated read)",
                "{\"mediaId\":" + CryptoParse.longToString(mid) + "}");
    }

    static int canReadMedia(long userId, String identity, long mediaId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(mediaId));
        DbQueryResult r = WcDb.query(
                "SELECT owner_user_id, student_id, deleted_at, feedback_id, size_bytes FROM wc_media_assets WHERE id=?",
                p);
        if (r.rows.size() == 0 || r.rows.get(0).getLong(2) > 0L) {
            return 0;
        }
        if (r.rows.get(0).getLong(0) == userId) {
            return 1;
        }
        if (identity.equals(WcIdentity.ADMIN) || identity.equals(WcIdentity.TEACHER)) {
            return 1;
        }
        /* 家长仅可读：本人监护孩子 + 已发布反馈关联 + 已完成上传的作品 */
        if (identity.equals(WcIdentity.PARENT) == false) {
            return 0;
        }
        if (WcServices.isGuardianOf(userId, r.rows.get(0).getLong(1)) == 0) {
            return 0;
        }
        long feedbackId = r.rows.get(0).getLong(3);
        if (feedbackId <= 0L || r.rows.get(0).getLong(4) <= 0L) {
            return 0;
        }
        ArrayList<Object> fp = new ArrayList<Object>();
        fp.add(CryptoParse.longToString(feedbackId));
        Object st = WcDb.queryOne("SELECT status FROM wc_feedback WHERE id=?", fp, new StringMapper());
        if (st == null || st.equals(WcFeedbackStatus.PUBLISHED) == false) {
            return 0;
        }
        return 1;
    }

    /** Display names for session cards (course/class/teacher/campus/room). */
    static String sessionLabelsJson(long classId, long teacherId, long campusId, long roomId) {
        String className = "";
        String courseName = "";
        String subject = "";
        String teacherName = "";
        String campusName = "";
        String roomName = "";
        if (classId > 0L) {
            ArrayList<Object> cp = new ArrayList<Object>();
            cp.add(CryptoParse.longToString(classId));
            DbQueryResult cr = WcDb.query(
                    "SELECT cl.name, COALESCE(co.name,''), COALESCE(co.subject,'') FROM wc_classes cl"
                            + " LEFT JOIN wc_courses co ON co.id=cl.course_id WHERE cl.id=?",
                    cp);
            if (cr.rows.size() > 0) {
                className = cr.rows.get(0).getString(0);
                courseName = cr.rows.get(0).getString(1);
                subject = cr.rows.get(0).getString(2);
            }
        }
        if (teacherId > 0L) {
            ArrayList<Object> tp = new ArrayList<Object>();
            tp.add(CryptoParse.longToString(teacherId));
            Object tn = WcDb.queryOne("SELECT display_name FROM wc_users WHERE id=?", tp, new StringMapper());
            if (tn != null) {
                teacherName = (String) tn;
            }
        }
        if (campusId > 0L) {
            ArrayList<Object> mp = new ArrayList<Object>();
            mp.add(CryptoParse.longToString(campusId));
            Object cn = WcDb.queryOne("SELECT name FROM wc_campuses WHERE id=?", mp, new StringMapper());
            if (cn != null) {
                campusName = (String) cn;
            }
        }
        if (roomId > 0L) {
            ArrayList<Object> rp = new ArrayList<Object>();
            rp.add(CryptoParse.longToString(roomId));
            Object rn = WcDb.queryOne("SELECT name FROM wc_rooms WHERE id=?", rp, new StringMapper());
            if (rn != null) {
                roomName = (String) rn;
            }
        }
        String theme = courseName;
        if (theme.length() == 0) {
            theme = className;
        }
        if (theme.length() == 0) {
            theme = "课程";
        }
        return ",\"className\":\"" + themeEscape(className) + "\",\"courseName\":\"" + themeEscape(courseName)
                + "\",\"subject\":\"" + themeEscape(subject) + "\",\"theme\":\"" + themeEscape(theme)
                + "\",\"teacherName\":\"" + themeEscape(teacherName) + "\",\"campusName\":\""
                + themeEscape(campusName) + "\",\"roomName\":\"" + themeEscape(roomName) + "\"";
    }

    static String themeEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String mediaIdsForFeedback(long feedbackId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(feedbackId));
        DbQueryResult r = WcDb.query(
                "SELECT id FROM wc_media_assets WHERE feedback_id=? AND deleted_at=0 AND size_bytes>0 ORDER BY id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + CryptoParse.longToString(r.rows.get(i).getLong(0));
            i = i + 1;
        }
        return out + "]";
    }

    static String sessionRosterJson(long sessionId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(sessionId));
        DbQueryResult r = WcDb.query(
                "SELECT ss.student_id, s.name, ss.contract_id,"
                        + " COALESCE((SELECT a.status FROM wc_attendance a WHERE a.session_id=ss.session_id AND a.student_id=ss.student_id AND a.reversed_at=0 ORDER BY a.id DESC LIMIT 1),'NOT_RECORDED'),"
                        + " COALESCE((SELECT a.id FROM wc_attendance a WHERE a.session_id=ss.session_id AND a.student_id=ss.student_id AND a.reversed_at=0 ORDER BY a.id DESC LIMIT 1),0),"
                        + " COALESCE((SELECT c.billing_mode FROM wc_contracts c WHERE c.id=ss.contract_id),'PACK'),"
                        + " COALESCE((SELECT a.consumed_milli FROM wc_attendance a WHERE a.session_id=ss.session_id AND a.student_id=ss.student_id AND a.reversed_at=0 ORDER BY a.id DESC LIMIT 1),0)"
                        + " FROM wc_session_students ss JOIN wc_students s ON s.id=ss.student_id WHERE ss.session_id=?",
                p);
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(sessionId));
        DbQueryResult meta = WcDb.query(
                "SELECT start_at, end_at, status, campus_id, teacher_user_id, room_id, class_id FROM wc_lesson_sessions WHERE id=?",
                sp);
        long startAt = 0L;
        long endAt = 0L;
        String status = "";
        long campusId = 0L;
        long teacherId = 0L;
        long roomId = 0L;
        long classId = 0L;
        if (meta.rows.size() > 0) {
            startAt = meta.rows.get(0).getLong(0);
            endAt = meta.rows.get(0).getLong(1);
            status = meta.rows.get(0).getString(2);
            campusId = meta.rows.get(0).getLong(3);
            teacherId = meta.rows.get(0).getLong(4);
            roomId = meta.rows.get(0).getLong(5);
            classId = meta.rows.get(0).getLong(6);
        }
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            String mode = r.rows.get(i).getString(5);
            long est = mode.equals("TERM") ? 0L : 1000L;
            out = out + "{\"studentId\":" + CryptoParse.longToString(r.rows.get(i).getLong(0)) + ",\"name\":\""
                    + r.rows.get(i).getString(1) + "\",\"contractId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(2)) + ",\"attendance\":\""
                    + r.rows.get(i).getString(3) + "\",\"attendanceId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(4)) + ",\"billingMode\":\"" + mode
                    + "\",\"consumedMilli\":" + CryptoParse.longToString(r.rows.get(i).getLong(6))
                    + ",\"estimateMilli\":" + CryptoParse.longToString(est) + "}";
            i = i + 1;
        }
        out = out + "]";
        return "{\"ok\":1,\"sessionId\":" + CryptoParse.longToString(sessionId)
                + ",\"startAt\":" + CryptoParse.longToString(startAt)
                + ",\"endAt\":" + CryptoParse.longToString(endAt)
                + ",\"status\":\"" + status + "\""
                + ",\"campusId\":" + CryptoParse.longToString(campusId)
                + ",\"teacherUserId\":" + CryptoParse.longToString(teacherId)
                + ",\"roomId\":" + CryptoParse.longToString(roomId)
                + ",\"classId\":" + CryptoParse.longToString(classId)
                + sessionLabelsJson(classId, teacherId, campusId, roomId)
                + ",\"roster\":" + out + "}";
    }

    static String adminTodayJson(long orgId, long campusId, long teacherId, long roomId) {
        long now = AsyncClockNative.wallMillis();
        long dayEnd = now + 86400000L;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(now - 3600000L));
        p.add(CryptoParse.longToString(dayEnd));
        String sql = "SELECT id, campus_id, teacher_user_id, room_id, start_at, end_at, status, student_id FROM wc_lesson_sessions WHERE org_id=? AND start_at>=? AND start_at<?";
        if (campusId > 0L) {
            sql = sql + " AND campus_id=" + CryptoParse.longToString(campusId);
        }
        if (teacherId > 0L) {
            sql = sql + " AND teacher_user_id=" + CryptoParse.longToString(teacherId);
        }
        if (roomId > 0L) {
            sql = sql + " AND room_id=" + CryptoParse.longToString(roomId);
        }
        sql = sql + " ORDER BY start_at LIMIT 100";
        DbQueryResult r = WcDb.query(sql, p);
        String items = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                items = items + ",";
            }
            long campusId = r.rows.get(i).getLong(1);
            long teacherId = r.rows.get(i).getLong(2);
            long roomId = r.rows.get(i).getLong(3);
            ArrayList<Object> lp = new ArrayList<Object>();
            lp.add(CryptoParse.longToString(r.rows.get(i).getLong(0)));
            Object classIdObj = WcDb.queryOne("SELECT class_id FROM wc_lesson_sessions WHERE id=?", lp,
                    new LongMapper());
            long classId = WcNum.asLong(classIdObj);
            items = items + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0)) + ",\"campusId\":"
                    + CryptoParse.longToString(campusId) + ",\"teacherUserId\":"
                    + CryptoParse.longToString(teacherId) + ",\"roomId\":"
                    + CryptoParse.longToString(roomId) + ",\"startAt\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(4)) + ",\"endAt\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(5)) + ",\"status\":\""
                    + r.rows.get(i).getString(6) + "\",\"studentId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(7))
                    + ",\"classId\":" + CryptoParse.longToString(classId)
                    + sessionLabelsJson(classId, teacherId, campusId, roomId) + "}";
            i = i + 1;
        }
        items = items + "]";
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(orgId));
        Object pending = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_leave_requests WHERE org_id=? AND status='PENDING' AND cancelled_at=0",
                lp, new LongMapper());
        Object openMakeup = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_makeup_credits WHERE org_id=? AND status='OPEN'", lp, new LongMapper());
        return "{\"ok\":1,\"role\":\"教务\",\"todaySessions\":" + items + ",\"pendingLeaves\":"
                + CryptoParse.longToString(WcNum.asLong(pending)) + ",\"openMakeupCredits\":"
                + CryptoParse.longToString(WcNum.asLong(openMakeup)) + ",\"hint\":\"日程列表；周课表为补充视图\"}";
    }

    static void ensureFeedbackTemplates(long orgId) {
        upsertTemplate(orgId, "英语", "表达与故事", "今天探索了什么表达？孩子具体尝试了什么？下一步鼓励？");
        upsertTemplate(orgId, "画画", "创意与创作过程", "今天探索了什么主题？创作过程中的具体尝试？下一步鼓励？");
        upsertTemplate(orgId, "语文", "阅读讲述与写作", "今天读/讲了什么？孩子的具体表达？下一步鼓励？");
        upsertTemplate(orgId, "数学", "思路推理与生活应用", "今天探索了什么问题？推理尝试？生活应用鼓励？");
    }

    static void upsertTemplate(long orgId, String subject, String title, String hint) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(subject);
        p.add(title);
        p.add(hint);
        try {
            WcDb.exec(
                    "INSERT INTO wc_feedback_templates(org_id, subject, title, body_hint) VALUES (?,?,?,?)", p);
        } catch (Exception ex) {
        }
    }
}
