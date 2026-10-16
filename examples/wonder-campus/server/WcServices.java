/**
 * Wonder Campus domain services: org, enroll, schedule, attendance, leave, refund.
 * @author 张晓达
 */
class WcServices {

    static void audit(long orgId, long actor, String action, String entityType, long entityId, String detail) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(actor));
        p.add(action);
        p.add(entityType);
        p.add(CryptoParse.longToString(entityId));
        p.add(detail == null ? "" : detail);
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        WcDb.exec(
                "INSERT INTO wc_audit(org_id, actor_user_id, action, entity_type, entity_id, detail, created_at) VALUES (?,?,?,?,?,?,?)",
                p);
    }

    static void notifyUser(long orgId, long userId, String title, String body, String refType, long refId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(userId));
        p.add(title);
        p.add(body == null ? "" : body);
        p.add(refType == null ? "" : refType);
        p.add(CryptoParse.longToString(refId));
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        WcDb.exec(
                "INSERT INTO wc_notifications(org_id, user_id, title, body, ref_type, ref_id, created_at) VALUES (?,?,?,?,?,?,?)",
                p);
    }

    static long createOrg(String code, String name) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(code);
        p.add(name);
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId("INSERT INTO wc_orgs(code, name, created_at) VALUES (?,?,?)", p);
    }

    static long createCampus(long orgId, String code, String name) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(code);
        p.add(name);
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId(
                "INSERT INTO wc_campuses(org_id, code, name, created_at) VALUES (?,?,?,?)", p);
    }

    static long createUser(long orgId, String login, String displayName) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(login);
        p.add(displayName);
        p.add("");
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId(
                "INSERT INTO wc_users(org_id, login, display_name, password_hash, created_at) VALUES (?,?,?,?,?)",
                p);
    }

    static void addIdentity(long userId, long orgId, String kind, long campusId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(orgId));
        p.add(kind);
        p.add(CryptoParse.longToString(campusId));
        WcDb.exec("INSERT INTO wc_user_identities(user_id, org_id, identity_kind, campus_id) VALUES (?,?,?,?)",
                p);
    }

    static void grantStaffCampus(long userId, long campusId, String roleCode) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(campusId));
        p.add(roleCode);
        WcDb.exec("INSERT INTO wc_staff_campus(user_id, campus_id, role_code) VALUES (?,?,?)", p);
    }

    static int hasIdentity(long userId, String kind) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(kind);
        Object c = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_user_identities WHERE user_id=? AND identity_kind=?", p,
                new LongMapper());
        if (WcNum.asLong(c) > 0L) {
            return 1;
        }
        return 0;
    }

    static int canAccessCampus(long userId, long campusId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(campusId));
        Object c = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_staff_campus WHERE user_id=? AND campus_id=?", p, new LongMapper());
        if (WcNum.asLong(c) > 0L) {
            return 1;
        }
        return 0;
    }

    static int isGuardianOf(long userId, long studentId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(userId));
        p.add(CryptoParse.longToString(studentId));
        Object c = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE g.user_id=? AND sg.student_id=?",
                p, new LongMapper());
        if (WcNum.asLong(c) > 0L) {
            return 1;
        }
        return 0;
    }

    static long createCourse(long orgId, String code, String name, String subject) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(code);
        p.add(name);
        p.add(subject);
        return WcDb.insertReturningId(
                "INSERT INTO wc_courses(org_id, code, name, subject) VALUES (?,?,?,?)", p);
    }

    static long createRoom(long campusId, String code, String name) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(campusId));
        p.add(code);
        p.add(name);
        return WcDb.insertReturningId("INSERT INTO wc_rooms(campus_id, code, name) VALUES (?,?,?)", p);
    }

    static long createClass(long orgId, long campusId, long courseId, String name, long teacherUserId) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(campusId));
        p.add(CryptoParse.longToString(courseId));
        p.add(name);
        p.add(CryptoParse.longToString(teacherUserId));
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId(
                "INSERT INTO wc_classes(org_id, campus_id, course_id, name, teacher_user_id, created_at) VALUES (?,?,?,?,?,?)",
                p);
    }

    static long createStudent(long orgId, long campusId, String name, String ageBand) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(campusId));
        p.add(name);
        p.add(ageBand);
        p.add("0");
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId(
                "INSERT INTO wc_students(org_id, campus_id, name, age_band, birth_year, created_at) VALUES (?,?,?,?,?,?)",
                p);
    }

    static long createGuardian(long orgId, long userId, String name, String phone) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(userId));
        p.add(name);
        p.add(phone);
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId(
                "INSERT INTO wc_guardians(org_id, user_id, name, phone, created_at) VALUES (?,?,?,?,?)", p);
    }

    static void linkGuardian(long studentId, long guardianId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(guardianId));
        p.add("PARENT");
        WcDb.exec("INSERT INTO wc_student_guardians(student_id, guardian_id, relation) VALUES (?,?,?)", p);
    }

    static WcResult enrollPack(long orgId, long campusId, long studentId, long courseId, long classId,
            long paidMilli, long bonusMilli, long expireAt, long listPrice, long discount, long actor,
            String idem, WcRuleSnapshot rules) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_contracts WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent",
                    "{\"contractId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        long now = AsyncClockNative.wallMillis();
        long recv = listPrice - discount;
        if (recv < 0L) {
            recv = 0L;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(campusId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(courseId));
        p.add(CryptoParse.longToString(classId));
        p.add(WcBillingMode.PACK);
        p.add(CryptoParse.longToString(listPrice));
        p.add(CryptoParse.longToString(discount));
        p.add(CryptoParse.longToString(recv));
        p.add("0");
        p.add("0");
        p.add("ACTIVE");
        p.add(rules.toJson());
        p.add(CryptoParse.longToString(paidMilli));
        p.add(CryptoParse.longToString(bonusMilli));
        p.add(CryptoParse.longToString(expireAt));
        p.add("0");
        p.add("0");
        p.add("0");
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(actor));
        p.add(idem);
        long cid = WcDb.insertReturningId(
                "INSERT INTO wc_contracts(org_id, campus_id, student_id, course_id, class_id, billing_mode, list_price_cents, discount_cents, receivable_cents, received_cents, refunded_cents, status, rule_snapshot_json, pack_paid_milli, pack_bonus_milli, pack_expire_at, term_start, term_end, term_promised_sessions, created_at, created_by, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
        ArrayList<Object> b = new ArrayList<Object>();
        b.add(CryptoParse.longToString(cid));
        b.add(CryptoParse.longToString(paidMilli));
        b.add(CryptoParse.longToString(bonusMilli));
        WcDb.exec(
                "INSERT INTO wc_pack_balances(contract_id, paid_remaining_milli, bonus_remaining_milli) VALUES (?,?,?)",
                b);
        writeLedger(orgId, cid, studentId, WcLedgerType.PURCHASE, WcBucket.PAID, paidMilli, paidMilli, bonusMilli,
                0L, "pack purchase paid", actor, idem + "-lp");
        if (bonusMilli > 0L) {
            writeLedger(orgId, cid, studentId, WcLedgerType.PURCHASE, WcBucket.BONUS, bonusMilli, paidMilli,
                    bonusMilli, 0L, "pack purchase bonus", actor, idem + "-lb");
        }
        audit(orgId, actor, "ENROLL_PACK", "contract", cid, "paidMilli=" + CryptoParse.longToString(paidMilli));
        return WcResult.success("OK", "enrolled pack",
                "{\"contractId\":" + CryptoParse.longToString(cid) + "}");
    }

    static void setContractBillingEntity(long contractId, long billingEntityId) {
        if (billingEntityId <= 0L) {
            return;
        }
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(CryptoParse.longToString(billingEntityId));
        u.add(CryptoParse.longToString(contractId));
        WcDb.exec("UPDATE wc_contracts SET billing_entity_id=? WHERE id=?", u);
    }

    /** Home campus always allowed; other campuses need explicit grant. */
    static int contractAllowedOnCampus(long contractId, long campusId) {
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        Object home = WcDb.queryOne("SELECT campus_id FROM wc_contracts WHERE id=?", cp, new LongMapper());
        if (home == null) {
            return 0;
        }
        if (WcNum.asLong(home) == campusId) {
            return 1;
        }
        ArrayList<Object> g = new ArrayList<Object>();
        g.add(CryptoParse.longToString(contractId));
        g.add(CryptoParse.longToString(campusId));
        Object ok = WcDb.queryOne(
                "SELECT id FROM wc_campus_entitlement_grants WHERE contract_id=? AND campus_id=?", g,
                new LongMapper());
        if (ok != null) {
            return 1;
        }
        return 0;
    }

    static long createBillingEntity(long orgId, String code, String name) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(code);
        p.add(name);
        p.add("TEST billing entity — not legal advice");
        p.add(CryptoParse.longToString(now));
        return WcDb.insertReturningId(
                "INSERT INTO wc_billing_entities(org_id, code, name, note, created_at) VALUES (?,?,?,?,?)", p);
    }

    static void grantCampusEntitlement(long orgId, long contractId, long campusId, long actor) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(contractId));
        p.add(CryptoParse.longToString(campusId));
        p.add(CryptoParse.longToString(actor));
        p.add(CryptoParse.longToString(now));
        try {
            WcDb.exec(
                    "INSERT INTO wc_campus_entitlement_grants(org_id, contract_id, campus_id, granted_by, created_at) VALUES (?,?,?,?,?)",
                    p);
        } catch (Exception ex) {
            /* unique already granted */
        }
    }

    static WcResult enrollTerm(long orgId, long campusId, long studentId, long courseId, long classId,
            long termStart, long termEnd, int promised, long listPrice, long discount, long actor, String idem,
            WcRuleSnapshot rules) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_contracts WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent",
                    "{\"contractId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        long now = AsyncClockNative.wallMillis();
        long recv = listPrice - discount;
        if (recv < 0L) {
            recv = 0L;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(campusId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(courseId));
        p.add(CryptoParse.longToString(classId));
        p.add(WcBillingMode.TERM);
        p.add(CryptoParse.longToString(listPrice));
        p.add(CryptoParse.longToString(discount));
        p.add(CryptoParse.longToString(recv));
        p.add("0");
        p.add("0");
        p.add("ACTIVE");
        p.add(rules.toJson());
        p.add("0");
        p.add("0");
        p.add("0");
        p.add(CryptoParse.longToString(termStart));
        p.add(CryptoParse.longToString(termEnd));
        p.add(String.valueOf(promised));
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(actor));
        p.add(idem);
        long cid = WcDb.insertReturningId(
                "INSERT INTO wc_contracts(org_id, campus_id, student_id, course_id, class_id, billing_mode, list_price_cents, discount_cents, receivable_cents, received_cents, refunded_cents, status, rule_snapshot_json, pack_paid_milli, pack_bonus_milli, pack_expire_at, term_start, term_end, term_promised_sessions, created_at, created_by, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
        writeLedger(orgId, cid, studentId, WcLedgerType.TERM_GRANT, WcBucket.TERM, 0L + promised * 1000L, 0L, 0L,
                0L, "term grant sessions=" + String.valueOf(promised), actor, idem + "-tg");
        audit(orgId, actor, "ENROLL_TERM", "contract", cid, "promised=" + String.valueOf(promised));
        return WcResult.success("OK", "enrolled term",
                "{\"contractId\":" + CryptoParse.longToString(cid) + "}");
    }

    static void writeLedger(long orgId, long contractId, long studentId, String entryType, String bucket,
            long milliDelta, long paidAfter, long bonusAfter, long sessionId, String note, long actor,
            String idem) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(contractId));
        p.add(CryptoParse.longToString(studentId));
        p.add(entryType);
        p.add(bucket);
        p.add(CryptoParse.longToString(milliDelta));
        p.add(CryptoParse.longToString(paidAfter));
        p.add(CryptoParse.longToString(bonusAfter));
        p.add(CryptoParse.longToString(sessionId));
        p.add(note == null ? "" : note);
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        p.add(CryptoParse.longToString(actor));
        p.add(idem);
        WcDb.exec(
                "INSERT INTO wc_lesson_ledger(org_id, contract_id, student_id, entry_type, bucket, milli_delta, balance_paid_after, balance_bonus_after, session_id, note, created_at, created_by, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
    }

    static WcResult registerPayment(long orgId, long contractId, long amountCents, String method, String receiptNo,
            long actor, String idem) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        if (amountCents <= 0L) {
            return WcResult.fail("INVALID_AMOUNT", "收款金额必须为正整数分");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_payments WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent payment",
                    "{\"paymentId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        cp.add(CryptoParse.longToString(orgId));
        DbQueryResult cr = WcDb.query(
                "SELECT receivable_cents, received_cents, refunded_cents FROM wc_contracts WHERE id=? AND org_id=?",
                cp);
        if (cr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "合同不存在");
        }
        long receivable = cr.rows.get(0).getLong(0);
        long received = cr.rows.get(0).getLong(1);
        long due = receivable - received;
        if (amountCents > due) {
            return WcResult.fail("OVERPAY", "收款超过欠款");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(contractId));
        p.add(CryptoParse.longToString(amountCents));
        p.add(method);
        p.add(receiptNo);
        p.add("");
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(actor));
        p.add(idem);
        long pid = WcDb.insertReturningId(
                "INSERT INTO wc_payments(org_id, contract_id, amount_cents, method, receipt_no, note, created_at, created_by, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)",
                p);
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(CryptoParse.longToString(received + amountCents));
        u.add(CryptoParse.longToString(contractId));
        WcDb.exec("UPDATE wc_contracts SET received_cents=? WHERE id=?", u);
        audit(orgId, actor, "PAYMENT", "payment", pid, method + " " + CryptoParse.longToString(amountCents));
        return WcResult.success("OK", "payment recorded",
                "{\"paymentId\":" + CryptoParse.longToString(pid) + "}");
    }

    static WcResult refundPayment(long orgId, long paymentId, long amountCents, String reason, long actor,
            String idem) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_refunds WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent refund",
                    "{\"refundId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(paymentId));
        pp.add(CryptoParse.longToString(orgId));
        DbQueryResult pr = WcDb.query(
                "SELECT contract_id, amount_cents FROM wc_payments WHERE id=? AND org_id=?", pp);
        if (pr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "收款不存在");
        }
        long contractId = pr.rows.get(0).getLong(0);
        long payAmt = pr.rows.get(0).getLong(1);
        ArrayList<Object> rp = new ArrayList<Object>();
        rp.add(CryptoParse.longToString(paymentId));
        Object refunded = WcDb.queryOne(
                "SELECT COALESCE(SUM(amount_cents),0) FROM wc_refunds WHERE payment_id=?", rp, new LongMapper());
        long already = WcNum.asLong(refunded);
        if (amountCents <= 0L || already + amountCents > payAmt) {
            return WcResult.fail("REFUND_EXCEEDS", "退款超过可退金额");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(orgId));
        ins.add(CryptoParse.longToString(contractId));
        ins.add(CryptoParse.longToString(paymentId));
        ins.add(CryptoParse.longToString(amountCents));
        ins.add(reason == null ? "" : reason);
        ins.add(CryptoParse.longToString(now));
        ins.add(CryptoParse.longToString(actor));
        ins.add(idem);
        long rid = WcDb.insertReturningId(
                "INSERT INTO wc_refunds(org_id, contract_id, payment_id, amount_cents, reason, created_at, created_by, idempotency_key) VALUES (?,?,?,?,?,?,?,?)",
                ins);
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        Object curRef = WcDb.queryOne("SELECT refunded_cents FROM wc_contracts WHERE id=?", cp, new LongMapper());
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(CryptoParse.longToString(WcNum.asLong(curRef) + amountCents));
        u.add(CryptoParse.longToString(contractId));
        WcDb.exec("UPDATE wc_contracts SET refunded_cents=? WHERE id=?", u);
        audit(orgId, actor, "REFUND", "refund", rid, CryptoParse.longToString(amountCents));
        return WcResult.success("OK", "refund recorded",
                "{\"refundId\":" + CryptoParse.longToString(rid) + "}");
    }

    static int sessionOverlaps(long teacherId, long roomId, long studentId, long startAt, long endAt,
            long excludeSessionId) {
        /* Overlap: existing.start < newEnd AND existing.end > newStart */
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(teacherId));
        p.add(CryptoParse.longToString(endAt));
        p.add(CryptoParse.longToString(startAt));
        p.add(CryptoParse.longToString(excludeSessionId));
        Object t = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE teacher_user_id=? AND status IN ('SCHEDULED','RESCHEDULED') AND start_at<? AND end_at>? AND id<>?",
                p, new LongMapper());
        if (WcNum.asLong(t) > 0L) {
            return 1;
        }
        ArrayList<Object> r = new ArrayList<Object>();
        r.add(CryptoParse.longToString(roomId));
        r.add(CryptoParse.longToString(endAt));
        r.add(CryptoParse.longToString(startAt));
        r.add(CryptoParse.longToString(excludeSessionId));
        Object rm = WcDb.queryOne(
                "SELECT COUNT(*) FROM wc_lesson_sessions WHERE room_id=? AND status IN ('SCHEDULED','RESCHEDULED') AND start_at<? AND end_at>? AND id<>?",
                r, new LongMapper());
        if (WcNum.asLong(rm) > 0L) {
            return 1;
        }
        if (studentId > 0L) {
            ArrayList<Object> s = new ArrayList<Object>();
            s.add(CryptoParse.longToString(studentId));
            s.add(CryptoParse.longToString(endAt));
            s.add(CryptoParse.longToString(startAt));
            s.add(CryptoParse.longToString(excludeSessionId));
            Object st = WcDb.queryOne(
                    "SELECT COUNT(*) FROM wc_lesson_sessions ls JOIN wc_session_students ss ON ss.session_id=ls.id WHERE ss.student_id=? AND ls.status IN ('SCHEDULED','RESCHEDULED') AND ls.start_at<? AND ls.end_at>? AND ls.id<>?",
                    s, new LongMapper());
            if (WcNum.asLong(st) > 0L) {
                return 1;
            }
        }
        return 0;
    }

    static WcResult createSession(long orgId, long campusId, long classId, long teacherId, long roomId,
            long startAt, long endAt, long studentId, long contractId, long seriesId) {
        if (endAt <= startAt) {
            return WcResult.fail("INVALID_TIME", "结束时间必须晚于开始");
        }
        if (contractId > 0L && contractAllowedOnCampus(contractId, campusId) == 0) {
            return WcResult.fail("CAMPUS_NOT_AUTHORIZED", "合同未授权在此校区使用权益（需明确跨校区授权）");
        }
        String conflict = conflictDetail(teacherId, roomId, studentId, startAt, endAt, 0L);
        if (conflict.length() > 0) {
            return WcResult.fail("CONFLICT", conflict);
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(campusId));
        p.add(CryptoParse.longToString(classId));
        p.add(CryptoParse.longToString(seriesId));
        p.add(CryptoParse.longToString(teacherId));
        p.add(CryptoParse.longToString(roomId));
        p.add(CryptoParse.longToString(startAt));
        p.add(CryptoParse.longToString(endAt));
        p.add(WcSessionStatus.SCHEDULED);
        p.add("");
        p.add("0");
        p.add(CryptoParse.longToString(contractId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(startAt));
        p.add("");
        p.add(CryptoParse.longToString(now));
        long sid = WcDb.insertReturningId(
                "INSERT INTO wc_lesson_sessions(org_id, campus_id, class_id, series_id, teacher_user_id, room_id, start_at, end_at, status, cancel_reason, makeup_of_session_id, contract_id, student_id, original_start_at, change_note, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p);
        if (studentId > 0L) {
            ArrayList<Object> ss = new ArrayList<Object>();
            ss.add(CryptoParse.longToString(sid));
            ss.add(CryptoParse.longToString(studentId));
            ss.add(CryptoParse.longToString(contractId));
            WcDb.exec("INSERT INTO wc_session_students(session_id, student_id, contract_id) VALUES (?,?,?)", ss);
        }
        return WcResult.success("OK", "session created",
                "{\"sessionId\":" + CryptoParse.longToString(sid) + "}");
    }

    static String conflictDetail(long teacherId, long roomId, long studentId, long startAt, long endAt,
            long excludeSessionId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(teacherId));
        p.add(CryptoParse.longToString(endAt));
        p.add(CryptoParse.longToString(startAt));
        p.add(CryptoParse.longToString(excludeSessionId));
        DbQueryResult tr = WcDb.query(
                "SELECT id, start_at, end_at FROM wc_lesson_sessions WHERE teacher_user_id=? AND status IN ('SCHEDULED','RESCHEDULED') AND start_at<? AND end_at>? AND id<>? LIMIT 1",
                p);
        if (tr.rows.size() > 0) {
            return "老师时间冲突：课次#" + CryptoParse.longToString(tr.rows.get(0).getLong(0)) + " "
                    + CryptoParse.longToString(tr.rows.get(0).getLong(1)) + "-"
                    + CryptoParse.longToString(tr.rows.get(0).getLong(2));
        }
        ArrayList<Object> r = new ArrayList<Object>();
        r.add(CryptoParse.longToString(roomId));
        r.add(CryptoParse.longToString(endAt));
        r.add(CryptoParse.longToString(startAt));
        r.add(CryptoParse.longToString(excludeSessionId));
        DbQueryResult rr = WcDb.query(
                "SELECT id, start_at, end_at FROM wc_lesson_sessions WHERE room_id=? AND status IN ('SCHEDULED','RESCHEDULED') AND start_at<? AND end_at>? AND id<>? LIMIT 1",
                r);
        if (rr.rows.size() > 0) {
            return "教室时间冲突：课次#" + CryptoParse.longToString(rr.rows.get(0).getLong(0)) + " "
                    + CryptoParse.longToString(rr.rows.get(0).getLong(1)) + "-"
                    + CryptoParse.longToString(rr.rows.get(0).getLong(2));
        }
        if (studentId > 0L) {
            ArrayList<Object> s = new ArrayList<Object>();
            s.add(CryptoParse.longToString(studentId));
            s.add(CryptoParse.longToString(endAt));
            s.add(CryptoParse.longToString(startAt));
            s.add(CryptoParse.longToString(excludeSessionId));
            DbQueryResult sr = WcDb.query(
                    "SELECT ls.id, ls.start_at, ls.end_at FROM wc_lesson_sessions ls JOIN wc_session_students ss ON ss.session_id=ls.id WHERE ss.student_id=? AND ls.status IN ('SCHEDULED','RESCHEDULED') AND ls.start_at<? AND ls.end_at>? AND ls.id<>? LIMIT 1",
                    s);
            if (sr.rows.size() > 0) {
                return "学员时间冲突：课次#" + CryptoParse.longToString(sr.rows.get(0).getLong(0)) + " "
                        + CryptoParse.longToString(sr.rows.get(0).getLong(1)) + "-"
                        + CryptoParse.longToString(sr.rows.get(0).getLong(2));
            }
        }
        return "";
    }

    static WcResult schoolCancelSession(long orgId, long sessionId, String reason, long actor) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcSessionStatus.CANCELLED_BY_SCHOOL);
        p.add(reason == null ? "" : reason);
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(orgId));
        WcDb.exec(
                "UPDATE wc_lesson_sessions SET status=?, cancel_reason=? WHERE id=? AND org_id=? AND status IN ('SCHEDULED','RESCHEDULED')",
                p);
        /* Reverse any active consumed attendance — cancel must not leave valid consumes. */
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(sessionId));
        DbQueryResult ar = WcDb.query(
                "SELECT id FROM wc_attendance WHERE session_id=? AND reversed_at=0 AND consumed_milli>0 AND status IN ('PRESENT','LATE','ABSENT')",
                ap);
        int i = 0;
        while (i < ar.rows.size()) {
            long aid = ar.rows.get(i).getLong(0);
            reverseCheckIn(orgId, aid, actor, "cancel-rev-" + CryptoParse.longToString(sessionId) + "-"
                    + CryptoParse.longToString(aid));
            i = i + 1;
        }
        WcLoopNotify.enqueueGuardiansOfSession(orgId, sessionId, "排课变更",
                "学校取消课次#" + CryptoParse.longToString(sessionId), "session", sessionId);
        audit(orgId, actor, "SCHOOL_CANCEL", "session", sessionId, reason);
        return WcResult.success("OK", "cancelled by school",
                "{\"sessionId\":" + CryptoParse.longToString(sessionId) + "}");
    }

    static WcResult rescheduleSession(long orgId, long sessionId, long newStart, long newEnd, String note,
            long actor) {
        ArrayList<Object> q = new ArrayList<Object>();
        q.add(CryptoParse.longToString(sessionId));
        q.add(CryptoParse.longToString(orgId));
        DbQueryResult r = WcDb.query(
                "SELECT teacher_user_id, room_id, student_id, start_at, end_at FROM wc_lesson_sessions WHERE id=? AND org_id=?",
                q);
        if (r.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "课次不存在");
        }
        long teacherId = r.rows.get(0).getLong(0);
        long roomId = r.rows.get(0).getLong(1);
        long studentId = r.rows.get(0).getLong(2);
        long oldStart = r.rows.get(0).getLong(3);
        long oldEnd = r.rows.get(0).getLong(4);
        String conflict = conflictDetail(teacherId, roomId, studentId, newStart, newEnd, sessionId);
        if (conflict.length() > 0) {
            return WcResult.fail("CONFLICT", conflict);
        }
        ArrayList<Object> hist = new ArrayList<Object>();
        hist.add(CryptoParse.longToString(orgId));
        hist.add(CryptoParse.longToString(sessionId));
        hist.add(CryptoParse.longToString(oldStart));
        hist.add(CryptoParse.longToString(oldEnd));
        hist.add(CryptoParse.longToString(newStart));
        hist.add(CryptoParse.longToString(newEnd));
        hist.add(note == null ? "" : note);
        hist.add(CryptoParse.longToString(actor));
        hist.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        WcDb.exec(
                "INSERT INTO wc_session_changes(org_id, session_id, old_start_at, old_end_at, new_start_at, new_end_at, reason, actor_user_id, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                hist);
        if (oldStart > 0L) {
            ArrayList<Object> os = new ArrayList<Object>();
            os.add(CryptoParse.longToString(oldStart));
            os.add(CryptoParse.longToString(sessionId));
            WcDb.exec(
                    "UPDATE wc_lesson_sessions SET original_start_at=? WHERE id=? AND original_start_at=0", os);
        }
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(CryptoParse.longToString(newStart));
        u.add(CryptoParse.longToString(newEnd));
        u.add(WcSessionStatus.RESCHEDULED);
        u.add(note == null ? "" : note);
        u.add(CryptoParse.longToString(sessionId));
        WcDb.exec(
                "UPDATE wc_lesson_sessions SET start_at=?, end_at=?, status=?, change_note=? WHERE id=?", u);
        WcLoopNotify.enqueueGuardiansOfSession(orgId, sessionId, "排课变更",
                "课次改期至 " + CryptoParse.longToString(newStart), "session", sessionId);
        audit(orgId, actor, "RESCHEDULE", "session", sessionId, note);
        return WcResult.success("OK", "rescheduled",
                "{\"sessionId\":" + CryptoParse.longToString(sessionId) + "}");
    }

    static long parseSessionConsume(String snapJson) {
        if (snapJson == null) {
            return 1000L;
        }
        int idx = snapJson.indexOf("\"sessionConsumeMilli\":");
        if (idx < 0) {
            return 1000L;
        }
        int i = idx + 22;
        String n = "";
        while (i < snapJson.length()) {
            int c = snapJson.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            i = i + 1;
        }
        if (n.length() == 0) {
            return 1000L;
        }
        return WcNum.parseLong(n);
    }

    static int snapFlag(String snapJson, String key, int defaultVal) {
        if (snapJson == null) {
            return defaultVal;
        }
        String needle = "\"" + key + "\":";
        int idx = snapJson.indexOf(needle);
        if (idx < 0) {
            return defaultVal;
        }
        int i = idx + needle.length();
        String n = "";
        while (i < snapJson.length()) {
            int c = snapJson.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            i = i + 1;
        }
        if (n.length() == 0) {
            return defaultVal;
        }
        return WcNum.parseInt(n);
    }

    static WcResult checkIn(long orgId, long sessionId, long studentId, long actor, String idem) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_attendance WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent checkin",
                    "{\"attendanceId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        ArrayList<Object> dup = new ArrayList<Object>();
        dup.add(CryptoParse.longToString(sessionId));
        dup.add(CryptoParse.longToString(studentId));
        Object active = WcDb.queryOne(
                "SELECT id FROM wc_attendance WHERE session_id=? AND student_id=? AND status='PRESENT' AND reversed_at=0",
                dup, new LongMapper());
        if (active != null) {
            return WcResult.fail("ALREADY_CHECKED_IN", "已签到，不可重复扣课");
        }
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(sessionId));
        sp.add(CryptoParse.longToString(orgId));
        DbQueryResult sr = WcDb.query(
                "SELECT status, contract_id, campus_id FROM wc_lesson_sessions WHERE id=? AND org_id=?", sp);
        if (sr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "课次不存在");
        }
        String st = sr.rows.get(0).getString(0);
        if (st.equals(WcSessionStatus.CANCELLED_BY_SCHOOL)) {
            return WcResult.fail("SESSION_CANCELLED", "学校已停课，不能签到");
        }
        long contractId = sr.rows.get(0).getLong(1);
        long sessionCampus = sr.rows.get(0).getLong(2);
        if (contractId > 0L && contractAllowedOnCampus(contractId, sessionCampus) == 0) {
            return WcResult.fail("CAMPUS_NOT_AUTHORIZED", "合同未授权在此校区消课");
        }
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        DbQueryResult cr = WcDb.query(
                "SELECT billing_mode, rule_snapshot_json, pack_expire_at, org_id FROM wc_contracts WHERE id=?",
                cp);
        if (cr.rows.size() == 0) {
            return WcResult.fail("NO_CONTRACT", "无报名合同");
        }
        String mode = cr.rows.get(0).getString(0);
        String snap = cr.rows.get(0).getString(1);
        long expireAt = cr.rows.get(0).getLong(2);
        long now = AsyncClockNative.wallMillis();
        long consume = 0L;
        if (mode.equals(WcBillingMode.PACK)) {
            if (expireAt > 0L && now > expireAt) {
                return WcResult.fail("PACK_EXPIRED", "课时包已过期");
            }
            consume = parseSessionConsume(snap);
            ArrayList<Object> bp = new ArrayList<Object>();
            bp.add(CryptoParse.longToString(contractId));
            DbQueryResult br = WcDb.query(
                    "SELECT paid_remaining_milli, bonus_remaining_milli FROM wc_pack_balances WHERE contract_id=?",
                    bp);
            if (br.rows.size() == 0) {
                return WcResult.fail("NO_BALANCE", "无课时余额");
            }
            long paid = br.rows.get(0).getLong(0);
            long bonus = br.rows.get(0).getLong(1);
            if (paid + bonus < consume) {
                return WcResult.fail("INSUFFICIENT", "课时余额不足");
            }
            long takePaid = 0L;
            long takeBonus = 0L;
            String order = "PAID_FIRST";
            if (snap != null && snap.indexOf("BONUS_FIRST") >= 0) {
                order = "BONUS_FIRST";
            }
            if (order.equals("BONUS_FIRST")) {
                if (bonus >= consume) {
                    takeBonus = consume;
                } else {
                    takeBonus = bonus;
                    takePaid = consume - bonus;
                }
            } else {
                if (paid >= consume) {
                    takePaid = consume;
                } else {
                    takePaid = paid;
                    takeBonus = consume - paid;
                }
            }
            long newPaid = paid - takePaid;
            long newBonus = bonus - takeBonus;
            ArrayList<Object> ub = new ArrayList<Object>();
            ub.add(CryptoParse.longToString(newPaid));
            ub.add(CryptoParse.longToString(newBonus));
            ub.add(CryptoParse.longToString(contractId));
            ub.add(CryptoParse.longToString(paid));
            ub.add(CryptoParse.longToString(bonus));
            WcDb.exec(
                    "UPDATE wc_pack_balances SET paid_remaining_milli=?, bonus_remaining_milli=? WHERE contract_id=? AND paid_remaining_milli=? AND bonus_remaining_milli=?",
                    ub);
            if (takePaid > 0L) {
                writeLedger(orgId, contractId, studentId, WcLedgerType.CONSUME, WcBucket.PAID, 0L - takePaid,
                        newPaid, newBonus, sessionId, "checkin paid", actor, idem + "-cp");
            }
            if (takeBonus > 0L) {
                writeLedger(orgId, contractId, studentId, WcLedgerType.CONSUME, WcBucket.BONUS, 0L - takeBonus,
                        newPaid, newBonus, sessionId, "checkin bonus", actor, idem + "-cb");
            }
            /* 课消收入：按合同应收/购买 milli 粗分摊到单节（整数分） */
            ArrayList<Object> c2 = new ArrayList<Object>();
            c2.add(CryptoParse.longToString(contractId));
            Object recv = WcDb.queryOne("SELECT receivable_cents FROM wc_contracts WHERE id=?", c2,
                    new LongMapper());
            Object packPaid = WcDb.queryOne("SELECT pack_paid_milli FROM wc_contracts WHERE id=?", c2,
                    new LongMapper());
            long revenue = 0L;
            if (WcNum.asLong(packPaid) > 0L) {
                revenue = (WcNum.asLong(recv) * takePaid) / WcNum.asLong(packPaid);
            }
            ArrayList<Object> rev = new ArrayList<Object>();
            rev.add(CryptoParse.longToString(orgId));
            rev.add(CryptoParse.longToString(contractId));
            rev.add(CryptoParse.longToString(sessionId));
            rev.add(CryptoParse.longToString(revenue));
            rev.add(CryptoParse.longToString(now));
            rev.add(idem + "-rev");
            WcDb.exec(
                    "INSERT INTO wc_consume_revenue(org_id, contract_id, session_id, amount_cents, created_at, idempotency_key) VALUES (?,?,?,?,?,?)",
                    rev);
        }
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(orgId));
        ap.add(CryptoParse.longToString(sessionId));
        ap.add(CryptoParse.longToString(studentId));
        ap.add(CryptoParse.longToString(contractId));
        ap.add(WcAttendanceStatus.PRESENT);
        ap.add(CryptoParse.longToString(consume));
        ap.add("0");
        ap.add(CryptoParse.longToString(now));
        ap.add(CryptoParse.longToString(actor));
        ap.add("0");
        ap.add(idem);
        long aid = WcDb.insertReturningId(
                "INSERT INTO wc_attendance(org_id, session_id, student_id, contract_id, status, consumed_milli, ledger_id, created_at, created_by, reversed_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                ap);
        audit(orgId, actor, "CHECKIN", "attendance", aid, "consume=" + CryptoParse.longToString(consume));
        return WcResult.success("OK", "checked in",
                "{\"attendanceId\":" + CryptoParse.longToString(aid) + ",\"consumedMilli\":"
                        + CryptoParse.longToString(consume) + "}");
    }

    static WcResult reverseCheckIn(long orgId, long attendanceId, long actor, String idem) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(attendanceId));
        ap.add(CryptoParse.longToString(orgId));
        DbQueryResult ar = WcDb.query(
                "SELECT session_id, student_id, contract_id, status, consumed_milli, reversed_at FROM wc_attendance WHERE id=? AND org_id=?",
                ap);
        if (ar.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "签到不存在");
        }
        if (ar.rows.get(0).getLong(5) > 0L
                || ar.rows.get(0).getString(3).equals(WcAttendanceStatus.REVERSED)) {
            return WcResult.fail("ALREADY_REVERSED", "签到已撤销");
        }
        long sessionId = ar.rows.get(0).getLong(0);
        long studentId = ar.rows.get(0).getLong(1);
        long contractId = ar.rows.get(0).getLong(2);
        long consumed = ar.rows.get(0).getLong(4);
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        Object mode = WcDb.queryOne("SELECT billing_mode FROM wc_contracts WHERE id=?", cp, new StringMapper());
        if (mode != null && ((String) mode).equals(WcBillingMode.PACK) && consumed > 0L) {
            ArrayList<Object> bp = new ArrayList<Object>();
            bp.add(CryptoParse.longToString(contractId));
            DbQueryResult br = WcDb.query(
                    "SELECT paid_remaining_milli, bonus_remaining_milli FROM wc_pack_balances WHERE contract_id=?",
                    bp);
            long paid = br.rows.get(0).getLong(0);
            long bonus = br.rows.get(0).getLong(1);
            /* 撤销时优先退回付费桶（与 PAID_FIRST 消耗对称的简化） */
            long newPaid = paid + consumed;
            ArrayList<Object> ub = new ArrayList<Object>();
            ub.add(CryptoParse.longToString(newPaid));
            ub.add(CryptoParse.longToString(bonus));
            ub.add(CryptoParse.longToString(contractId));
            WcDb.exec(
                    "UPDATE wc_pack_balances SET paid_remaining_milli=?, bonus_remaining_milli=? WHERE contract_id=?",
                    ub);
            writeLedger(orgId, contractId, studentId, WcLedgerType.REVERSE_CONSUME, WcBucket.PAID, consumed, newPaid,
                    bonus, sessionId, "reverse checkin", actor, idem + "-rev");
        }
        ArrayList<Object> u = new ArrayList<Object>();
        u.add(WcAttendanceStatus.REVERSED);
        u.add(CryptoParse.longToString(now));
        u.add(CryptoParse.longToString(attendanceId));
        WcDb.exec("UPDATE wc_attendance SET status=?, reversed_at=? WHERE id=?", u);
        audit(orgId, actor, "REVERSE_CHECKIN", "attendance", attendanceId, idem);
        return WcResult.success("OK", "checkin reversed",
                "{\"attendanceId\":" + CryptoParse.longToString(attendanceId) + "}");
    }

    static WcResult requestLeave(long orgId, long sessionId, long studentId, long parentUserId, String reason,
            String idem) {
        if (isGuardianOf(parentUserId, studentId) == 0) {
            return WcResult.fail("FORBIDDEN", "非授权家长");
        }
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_leave_requests WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent leave",
                    "{\"leaveId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(sessionId));
        DbQueryResult sr = WcDb.query(
                "SELECT start_at, contract_id, status FROM wc_lesson_sessions WHERE id=?", sp);
        if (sr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "课次不存在");
        }
        if (sr.rows.get(0).getString(2).equals(WcSessionStatus.CANCELLED_BY_SCHOOL)) {
            return WcResult.fail("SESSION_CANCELLED", "学校已停课");
        }
        long startAt = sr.rows.get(0).getLong(0);
        long contractId = sr.rows.get(0).getLong(1);
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        Object snap = WcDb.queryOne("SELECT rule_snapshot_json FROM wc_contracts WHERE id=?", cp,
                new StringMapper());
        String snapJson = "";
        if (snap != null) {
            snapJson = (String) snap;
        }
        int deadlineH = snapFlag(snapJson, "leaveDeadlineHours", 12);
        long now = AsyncClockNative.wallMillis();
        if (startAt - now < (0L + deadlineH) * 3600000L) {
            return WcResult.fail("LEAVE_TOO_LATE", "已过请假截止时间");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(contractId));
        p.add(WcLeaveStatus.PENDING);
        p.add(reason == null ? "" : reason);
        p.add(CryptoParse.longToString(now));
        p.add(CryptoParse.longToString(parentUserId));
        p.add("0");
        p.add("0");
        p.add(idem);
        long lid = WcDb.insertReturningId(
                "INSERT INTO wc_leave_requests(org_id, session_id, student_id, contract_id, status, reason, created_at, created_by, resolved_at, makeup_session_id, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                p);
        notifyUser(orgId, parentUserId, "请假已提交", "等待教务安排补课", "leave", lid);
        return WcResult.success("OK", "leave submitted",
                "{\"leaveId\":" + CryptoParse.longToString(lid) + "}");
    }

    static WcResult approveLeaveAndMakeup(long orgId, long leaveId, long makeupStart, long makeupEnd,
            long teacherId, long roomId, long actor) {
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(CryptoParse.longToString(leaveId));
        lp.add(CryptoParse.longToString(orgId));
        DbQueryResult lr = WcDb.query(
                "SELECT session_id, student_id, contract_id, status FROM wc_leave_requests WHERE id=? AND org_id=?",
                lp);
        if (lr.rows.size() == 0) {
            return WcResult.fail("NOT_FOUND", "请假不存在");
        }
        if (lr.rows.get(0).getString(3).equals(WcLeaveStatus.APPROVED)) {
            return WcResult.fail("ALREADY_APPROVED", "请假已处理");
        }
        long origSession = lr.rows.get(0).getLong(0);
        long studentId = lr.rows.get(0).getLong(1);
        long contractId = lr.rows.get(0).getLong(2);
        ArrayList<Object> cp = new ArrayList<Object>();
        cp.add(CryptoParse.longToString(contractId));
        Object snap = WcDb.queryOne("SELECT rule_snapshot_json FROM wc_contracts WHERE id=?", cp,
                new StringMapper());
        String snapJson = snap == null ? "" : (String) snap;
        if (snapFlag(snapJson, "allowMakeup", 1) == 0) {
            return WcResult.fail("MAKEUP_DENIED", "合同不允许补课");
        }
        ArrayList<Object> sp = new ArrayList<Object>();
        sp.add(CryptoParse.longToString(origSession));
        DbQueryResult sr = WcDb.query(
                "SELECT campus_id, class_id FROM wc_lesson_sessions WHERE id=?", sp);
        long campusId = sr.rows.get(0).getLong(0);
        long classId = sr.rows.get(0).getLong(1);
        if (sessionOverlaps(teacherId, roomId, studentId, makeupStart, makeupEnd, 0L) != 0) {
            return WcResult.fail("CONFLICT", "补课时间冲突");
        }
        /* 原课记请假出席状态，不扣课时（leaveConsumes=0 默认） */
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> att = new ArrayList<Object>();
        att.add(CryptoParse.longToString(orgId));
        att.add(CryptoParse.longToString(origSession));
        att.add(CryptoParse.longToString(studentId));
        att.add(CryptoParse.longToString(contractId));
        att.add(WcAttendanceStatus.LEAVE);
        att.add("0");
        att.add("0");
        att.add(CryptoParse.longToString(now));
        att.add(CryptoParse.longToString(actor));
        att.add("0");
        att.add("leave-" + CryptoParse.longToString(leaveId));
        WcDb.exec(
                "INSERT INTO wc_attendance(org_id, session_id, student_id, contract_id, status, consumed_milli, ledger_id, created_at, created_by, reversed_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                att);
        ArrayList<Object> ms = new ArrayList<Object>();
        ms.add(CryptoParse.longToString(orgId));
        ms.add(CryptoParse.longToString(campusId));
        ms.add(CryptoParse.longToString(classId));
        ms.add("0");
        ms.add(CryptoParse.longToString(teacherId));
        ms.add(CryptoParse.longToString(roomId));
        ms.add(CryptoParse.longToString(makeupStart));
        ms.add(CryptoParse.longToString(makeupEnd));
        ms.add(WcSessionStatus.SCHEDULED);
        ms.add("");
        ms.add(CryptoParse.longToString(origSession));
        ms.add(CryptoParse.longToString(contractId));
        ms.add(CryptoParse.longToString(studentId));
        ms.add(CryptoParse.longToString(makeupStart));
        ms.add("makeup of " + CryptoParse.longToString(origSession));
        ms.add(CryptoParse.longToString(now));
        long makeupId = WcDb.insertReturningId(
                "INSERT INTO wc_lesson_sessions(org_id, campus_id, class_id, series_id, teacher_user_id, room_id, start_at, end_at, status, cancel_reason, makeup_of_session_id, contract_id, student_id, original_start_at, change_note, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ms);
        ArrayList<Object> ss = new ArrayList<Object>();
        ss.add(CryptoParse.longToString(makeupId));
        ss.add(CryptoParse.longToString(studentId));
        ss.add(CryptoParse.longToString(contractId));
        WcDb.exec("INSERT INTO wc_session_students(session_id, student_id, contract_id) VALUES (?,?,?)", ss);
        ArrayList<Object> ul = new ArrayList<Object>();
        ul.add(WcLeaveStatus.APPROVED);
        ul.add(CryptoParse.longToString(now));
        ul.add(CryptoParse.longToString(makeupId));
        ul.add(CryptoParse.longToString(leaveId));
        WcDb.exec(
                "UPDATE wc_leave_requests SET status=?, resolved_at=?, makeup_session_id=? WHERE id=?", ul);
        int makeupDays = snapFlag(snapJson, "makeupValidDays", 30);
        long expiresAt = now + 86400000L * makeupDays;
        WcLoopServices.openMakeupCredit(orgId, leaveId, studentId, contractId, origSession, expiresAt);
        WcResult used = WcLoopServices.consumeMakeupCredit(orgId, leaveId, makeupId);
        if (used.ok == 0) {
            return used;
        }
        ArrayList<Object> gp = new ArrayList<Object>();
        gp.add(CryptoParse.longToString(studentId));
        DbQueryResult gr = WcDb.query(
                "SELECT g.user_id FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=?",
                gp);
        int gi = 0;
        while (gi < gr.rows.size()) {
            long uid = gr.rows.get(gi).getLong(0);
            WcLoopNotify.enqueue(orgId, uid, "请假结果", "已批准并安排补课", "leave", leaveId,
                    "leave-ok-" + CryptoParse.longToString(leaveId) + "-u" + CryptoParse.longToString(uid));
            gi = gi + 1;
        }
        audit(orgId, actor, "LEAVE_MAKEUP", "leave", leaveId,
                "makeup=" + CryptoParse.longToString(makeupId));
        return WcResult.success("OK", "leave approved with makeup",
                "{\"leaveId\":" + CryptoParse.longToString(leaveId) + ",\"makeupSessionId\":"
                        + CryptoParse.longToString(makeupId) + "}");
    }

    static WcResult publishFeedback(long orgId, long sessionId, long studentId, long teacherId, String tags,
            String body, String idem) {
        if (idem == null || idem.length() == 0) {
            return WcResult.fail("IDEMPOTENCY_REQUIRED", "需要幂等键");
        }
        ArrayList<Object> ik = new ArrayList<Object>();
        ik.add(CryptoParse.longToString(orgId));
        ik.add(idem);
        Object exist = WcDb.queryOne("SELECT id FROM wc_feedback WHERE org_id=? AND idempotency_key=?", ik,
                new LongMapper());
        if (exist != null) {
            return WcResult.success("OK", "idempotent feedback",
                    "{\"feedbackId\":" + CryptoParse.longToString(WcNum.asLong(exist)) + "}");
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(sessionId));
        p.add(CryptoParse.longToString(studentId));
        p.add(CryptoParse.longToString(teacherId));
        p.add(tags == null ? "" : tags);
        p.add(body == null ? "" : body);
        p.add(CryptoParse.longToString(now));
        p.add(idem);
        long fid = WcDb.insertReturningId(
                "INSERT INTO wc_feedback(org_id, session_id, student_id, teacher_user_id, tags, body, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?)",
                p);
        ArrayList<Object> gp = new ArrayList<Object>();
        gp.add(CryptoParse.longToString(studentId));
        DbQueryResult gr = WcDb.query(
                "SELECT g.user_id FROM wc_student_guardians sg JOIN wc_guardians g ON g.id=sg.guardian_id WHERE sg.student_id=?",
                gp);
        int i = 0;
        while (i < gr.rows.size()) {
            long uid = gr.rows.get(i).getLong(0);
            if (uid > 0L) {
                notifyUser(orgId, uid, "课堂反馈", body == null ? "" : body, "feedback", fid);
            }
            i = i + 1;
        }
        return WcResult.success("OK", "feedback published",
                "{\"feedbackId\":" + CryptoParse.longToString(fid) + "}");
    }

    static long packRemaining(long contractId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(contractId));
        DbQueryResult r = WcDb.query(
                "SELECT paid_remaining_milli, bonus_remaining_milli FROM wc_pack_balances WHERE contract_id=?",
                p);
        if (r.rows.size() == 0) {
            return 0L;
        }
        return r.rows.get(0).getLong(0) + r.rows.get(0).getLong(1);
    }

    static long parseDocId(String json, String key) {
        if (json == null) {
            return 0L;
        }
        String needle = "\"" + key + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return 0L;
        }
        int i = idx + needle.length();
        String n = "";
        while (i < json.length()) {
            int c = json.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            i = i + 1;
        }
        return WcNum.parseLong(n);
    }
}
