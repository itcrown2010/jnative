/**
 * List/query helpers for mobile app pages.
 * @author 张晓达
 */
class WcQueryServices {
    static String campusesJson(long orgId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        DbQueryResult r = WcDb.query("SELECT id, code, name FROM wc_campuses WHERE org_id=? ORDER BY id", p);
        return "{\"ok\":1,\"items\":" + rows3(r) + "}";
    }

    static String pendingLeavesJson(long orgId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        DbQueryResult r = WcDb.query(
                "SELECT id, session_id, student_id, reason, created_at, status FROM wc_leave_requests WHERE org_id=? AND status='PENDING' AND cancelled_at=0 ORDER BY id DESC LIMIT 50",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0)) + ",\"sessionId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(1)) + ",\"studentId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(2)) + ",\"reason\":\""
                    + r.rows.get(i).getString(3) + "\",\"status\":\"" + r.rows.get(i).getString(5) + "\"}";
            i = i + 1;
        }
        out = out + "]";
        return "{\"ok\":1,\"items\":" + out + "}";
    }

    static String contractsJson(long orgId, long studentId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        String sql = "SELECT id, student_id, campus_id, billing_mode, receivable_cents, received_cents, refunded_cents, status, pack_expire_at, term_promised_sessions, billing_entity_id FROM wc_contracts WHERE org_id=?";
        if (studentId > 0L) {
            sql = sql + " AND student_id=" + CryptoParse.longToString(studentId);
        }
        sql = sql + " ORDER BY id DESC LIMIT 50";
        DbQueryResult r = WcDb.query(sql, p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            long cid = r.rows.get(i).getLong(0);
            long rem = 0L;
            if (r.rows.get(i).getString(3).equals(WcBillingMode.PACK)) {
                rem = WcServices.packRemaining(cid);
            }
            out = out + "{\"id\":" + CryptoParse.longToString(cid) + ",\"studentId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(1)) + ",\"campusId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(2)) + ",\"billingMode\":\""
                    + r.rows.get(i).getString(3) + "\",\"receivableCents\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(4)) + ",\"receivedCents\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(5)) + ",\"refundedCents\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(6)) + ",\"status\":\""
                    + r.rows.get(i).getString(7) + "\",\"packExpireAt\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(8)) + ",\"remainingMilli\":"
                    + CryptoParse.longToString(rem) + ",\"promisedSessions\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(9)) + ",\"billingEntityId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(10)) + "}";
            i = i + 1;
        }
        out = out + "]";
        return "{\"ok\":1,\"items\":" + out + "}";
    }

    static String paymentsJson(long orgId, long contractId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(contractId));
        DbQueryResult r = WcDb.query(
                "SELECT id, amount_cents, method, receipt_no, created_at FROM wc_payments WHERE org_id=? AND contract_id=? ORDER BY id",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0)) + ",\"amountCents\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(1)) + ",\"method\":\""
                    + r.rows.get(i).getString(2) + "\",\"receiptNo\":\"" + r.rows.get(i).getString(3) + "\"}";
            i = i + 1;
        }
        out = out + "]";
        return "{\"ok\":1,\"items\":" + out + "}";
    }

    static String makeupCreditsJson(long orgId, long studentId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(orgId));
        p.add(CryptoParse.longToString(studentId));
        DbQueryResult r = WcDb.query(
                "SELECT id, leave_id, status, used_session_id, expires_at, source_session_id FROM wc_makeup_credits WHERE org_id=? AND student_id=? ORDER BY id DESC LIMIT 30",
                p);
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0)) + ",\"leaveId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(1)) + ",\"status\":\""
                    + r.rows.get(i).getString(2) + "\",\"usedSessionId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(3)) + ",\"sourceSessionId\":"
                    + CryptoParse.longToString(r.rows.get(i).getLong(5)) + "}";
            i = i + 1;
        }
        out = out + "]";
        return "{\"ok\":1,\"items\":" + out + "}";
    }

    static void markNotifyRead(long userId, long noteId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(AsyncClockNative.wallMillis()));
        p.add(CryptoParse.longToString(noteId));
        p.add(CryptoParse.longToString(userId));
        WcDb.exec("UPDATE wc_notifications SET read_at=? WHERE id=? AND user_id=?", p);
    }

    static String clientConfigJson() {
        String allow = Env.get("WC_ALLOW_DEMO_LOGIN");
        int demo = 0;
        if (allow != null && allow.equals("1")) {
            demo = 1;
        }
        String envName = Env.get("WC_ENV");
        if (envName == null || envName.length() == 0) {
            envName = "dev";
        }
        return "{\"ok\":1,\"env\":\"" + envName + "\",\"allowDemoLogin\":" + String.valueOf(demo)
                + ",\"ui\":\"webview-shell\",\"nativeUi\":\"not-supported\"}";
    }

    static String rows3(DbQueryResult r) {
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "{\"id\":" + CryptoParse.longToString(r.rows.get(i).getLong(0)) + ",\"code\":\""
                    + r.rows.get(i).getString(1) + "\",\"name\":\"" + r.rows.get(i).getString(2) + "\"}";
            i = i + 1;
        }
        return out + "]";
    }
}
