/**
 * Real account auth: password login, invite registration, password change, profile.
 * @author 张晓达
 */
class WcBizAuth {
    static int MAX_FAILS = 5;
    static long LOCK_MS = 600000L;

    static String identitiesJson(long userId) {
        DbQueryResult ids = WcDb.query("SELECT identity_kind FROM wc_user_identities WHERE user_id=?",
                WcApi.P1(userId));
        String arr = "[";
        ArrayList<Object> seen = new ArrayList<Object>();
        int i = 0;
        while (i < ids.rows.size()) {
            String k = ids.rows.get(i).getString(0);
            int dup = 0;
            int j = 0;
            while (j < seen.size()) {
                if (((String) seen.get(j)).equals(k)) {
                    dup = 1;
                }
                j = j + 1;
            }
            if (dup == 0) {
                if (seen.size() > 0) {
                    arr = arr + ",";
                }
                arr = arr + "\"" + k + "\"";
                seen.add(k);
            }
            i = i + 1;
        }
        return arr + "]";
    }

    static String defaultIdentity(long userId) {
        String[] order = new String[4];
        order[0] = WcIdentity.ADMIN;
        order[1] = WcIdentity.DIRECTOR;
        order[2] = WcIdentity.TEACHER;
        order[3] = WcIdentity.PARENT;
        int i = 0;
        while (i < 4) {
            if (WcServices.hasIdentity(userId, order[i]) != 0) {
                return order[i];
            }
            i = i + 1;
        }
        return "";
    }

    static void startSession(WcCtx c, long userId, long orgId, String identity) {
        Session session = WcAuth.load(c.ctx);
        session.rotateId();
        session.setString("userId", WcApi.l(userId));
        session.setString("orgId", WcApi.l(orgId));
        session.setString("identity", identity);
        WcAuth.save(c.ctx, session);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(WcApi.l(AsyncClockNative.wallMillis()));
        p.add(WcApi.l(userId));
        WcDb.exec("UPDATE wc_users SET last_login_at=?, fail_count=0, locked_until=0 WHERE id=?", p);
    }

    static String loginOk(WcCtx c, long userId, long orgId, String identity) {
        String name = WcApi.oneStr("SELECT display_name FROM wc_users WHERE id=?", WcApi.P1(userId));
        return c.ok("\"userId\":" + WcApi.l(userId) + ",\"identity\":" + WcApi.q(identity)
                + ",\"identities\":" + identitiesJson(userId) + ",\"displayName\":" + WcApi.q(name));
    }

    static String login(WcCtx c) {
        String login = c.bs("login");
        String password = c.bs("password");
        if (login.length() == 0 || password.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请输入账号和密码");
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(login);
        DbQueryResult r = WcDb.query(
                "SELECT id, org_id, password_hash, status, fail_count, locked_until FROM wc_users WHERE login=? ORDER BY id LIMIT 1",
                p);
        if (r.rows.size() == 0) {
            return c.fail(401, "BAD_CREDENTIALS", "账号或密码错误");
        }
        long uid = r.rows.get(0).getLong(0);
        long oid = r.rows.get(0).getLong(1);
        String hash = r.rows.get(0).getString(2);
        String st = r.rows.get(0).getString(3);
        long fails = r.rows.get(0).getLong(4);
        long lockedUntil = r.rows.get(0).getLong(5);
        long now = AsyncClockNative.wallMillis();
        if (st != null && st.equals("DISABLED")) {
            return c.fail(403, "ACCOUNT_DISABLED", "账号已停用，请联系机构管理员");
        }
        if (lockedUntil > now) {
            return c.fail(429, "ACCOUNT_LOCKED", "密码错误次数过多，请 10 分钟后再试");
        }
        if (hash == null || hash.length() == 0) {
            return c.fail(401, "NO_PASSWORD", "账号尚未设置密码，请联系教务重置");
        }
        if (Password.verifyBlocking(password, hash) == 0) {
            long nf = fails + 1L;
            long lock = 0L;
            if (nf >= (long) MAX_FAILS) {
                lock = now + LOCK_MS;
                nf = 0L;
            }
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(WcApi.l(nf));
            up.add(WcApi.l(lock));
            up.add(WcApi.l(uid));
            WcDb.exec("UPDATE wc_users SET fail_count=?, locked_until=? WHERE id=?", up);
            return c.fail(401, "BAD_CREDENTIALS", "账号或密码错误");
        }
        String want = c.bs("identity");
        String identity = want;
        if (identity.length() == 0 || WcServices.hasIdentity(uid, identity) == 0) {
            identity = defaultIdentity(uid);
        }
        if (identity.length() == 0) {
            return c.fail(403, "NO_IDENTITY", "账号尚未分配身份");
        }
        startSession(c, uid, oid, identity);
        WcServices.audit(oid, uid, "LOGIN", "user", uid, identity);
        return loginOk(c, uid, oid, identity);
    }

    static String validatePassword(WcCtx c, String login, String pw) {
        if (pw.length() < 8) {
            return c.fail(400, "WEAK_PASSWORD", "密码至少 8 位");
        }
        if (pw.equals(login)) {
            return c.fail(400, "WEAK_PASSWORD", "密码不能与账号相同");
        }
        int digit = 0;
        int alpha = 0;
        int i = 0;
        while (i < pw.length()) {
            int ch = pw.charAt(i);
            if (ch >= 48 && ch <= 57) {
                digit = 1;
            }
            if ((ch >= 65 && ch <= 90) || (ch >= 97 && ch <= 122)) {
                alpha = 1;
            }
            i = i + 1;
        }
        if (digit == 0 || alpha == 0) {
            return c.fail(400, "WEAK_PASSWORD", "密码需同时包含字母和数字");
        }
        return "";
    }

    static String register(WcCtx c) {
        String code = c.bs("inviteCode");
        String login = c.bs("login");
        String password = c.bs("password");
        String name = c.bs("displayName");
        if (code.length() == 0 || login.length() < 3 || name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "请填写邀请码、账号和姓名");
        }
        String weak = validatePassword(c, login, password);
        if (weak.length() > 0) {
            return weak;
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(code);
        DbQueryResult inv = WcDb.query(
                "SELECT id, org_id, identity_kind, student_id, campus_id, expires_at, used_by FROM wc_invites WHERE code=?",
                p);
        if (inv.rows.size() == 0) {
            return c.fail(404, "INVITE_NOT_FOUND", "邀请码无效");
        }
        long invId = inv.rows.get(0).getLong(0);
        long orgId = inv.rows.get(0).getLong(1);
        String kind = inv.rows.get(0).getString(2);
        long studentId = inv.rows.get(0).getLong(3);
        long campusId = inv.rows.get(0).getLong(4);
        long expires = inv.rows.get(0).getLong(5);
        long usedBy = inv.rows.get(0).getLong(6);
        long now = AsyncClockNative.wallMillis();
        if (usedBy > 0L) {
            return c.fail(409, "INVITE_USED", "邀请码已被使用");
        }
        if (expires < now) {
            return c.fail(410, "INVITE_EXPIRED", "邀请码已过期，请联系教务重新生成");
        }
        ArrayList<Object> lp = new ArrayList<Object>();
        lp.add(WcApi.l(orgId));
        lp.add(login);
        if (WcApi.one("SELECT id FROM wc_users WHERE org_id=? AND login=?", lp) > 0L) {
            return c.fail(409, "LOGIN_TAKEN", "该账号已被注册");
        }
        long uid = WcServices.createUser(orgId, login, name);
        String hash = Password.hashBlocking(password);
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(hash);
        up.add(c.bs("phone"));
        up.add(WcApi.l(uid));
        WcDb.exec("UPDATE wc_users SET password_hash=?, phone=? WHERE id=?", up);
        WcServices.addIdentity(uid, orgId, kind, campusId);
        if (kind.equals(WcIdentity.PARENT)) {
            long gid = WcServices.createGuardian(orgId, uid, name, c.bs("phone"));
            if (studentId > 0L) {
                WcServices.linkGuardian(studentId, gid);
            }
        } else {
            WcServices.grantStaffCampus(uid, campusId, kind);
        }
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(WcApi.l(uid));
        ip.add(WcApi.l(now));
        ip.add(WcApi.l(invId));
        WcDb.exec("UPDATE wc_invites SET used_by=?, used_at=? WHERE id=?", ip);
        WcServices.audit(orgId, uid, "REGISTER", "user", uid, kind);
        startSession(c, uid, orgId, kind);
        return loginOk(c, uid, orgId, kind);
    }

    static String changePassword(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        String oldPw = c.bs("oldPassword");
        String newPw = c.bs("newPassword");
        String hash = WcApi.oneStr("SELECT password_hash FROM wc_users WHERE id=?", WcApi.P1(c.es.userId));
        if (hash.length() > 0 && Password.verifyBlocking(oldPw, hash) == 0) {
            return c.fail(401, "BAD_CREDENTIALS", "原密码错误");
        }
        String login = WcApi.oneStr("SELECT login FROM wc_users WHERE id=?", WcApi.P1(c.es.userId));
        String weak = validatePassword(c, login, newPw);
        if (weak.length() > 0) {
            return weak;
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(Password.hashBlocking(newPw));
        up.add(WcApi.l(c.es.userId));
        WcDb.exec("UPDATE wc_users SET password_hash=? WHERE id=?", up);
        WcServices.audit(c.es.orgId, c.es.userId, "PASSWORD_CHANGE", "user", c.es.userId, "");
        return c.ok("");
    }

    static String profile(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        DbQueryResult r = WcDb.query("SELECT login, display_name, phone FROM wc_users WHERE id=?",
                WcApi.P1(c.es.userId));
        if (r.rows.size() == 0) {
            return c.fail(404, "NOT_FOUND", "账号不存在");
        }
        long unread = WcApi.one("SELECT COUNT(*) FROM wc_notifications WHERE user_id=? AND read_at=0",
                WcApi.P1(c.es.userId));
        return c.ok("\"userId\":" + WcApi.l(c.es.userId) + ",\"login\":" + WcApi.q(r.rows.get(0).getString(0))
                + ",\"displayName\":" + WcApi.q(r.rows.get(0).getString(1))
                + ",\"phone\":" + WcApi.q(r.rows.get(0).getString(2)) + ",\"identity\":" + WcApi.q(c.es.identity)
                + ",\"identities\":" + identitiesJson(c.es.userId) + ",\"unread\":" + WcApi.l(unread));
    }

    static String updateProfile(WcCtx c) {
        String g = c.guard("");
        if (g.length() > 0) {
            return g;
        }
        String name = c.bs("displayName");
        if (name.length() == 0) {
            return c.fail(400, "INVALID_INPUT", "姓名不能为空");
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(name);
        up.add(c.bs("phone"));
        up.add(WcApi.l(c.es.userId));
        WcDb.exec("UPDATE wc_users SET display_name=?, phone=? WHERE id=?", up);
        return c.ok("");
    }

    /** Demo/dev only: give every password-less account a known password so real login can be tried locally. */
    static void seedDemoPasswords() {
        String allow = Env.get("WC_ALLOW_DEMO_LOGIN");
        if (allow == null || allow.equals("1") == false) {
            return;
        }
        DbQueryResult r = WcDb.query("SELECT id FROM wc_users WHERE password_hash=''", WcApi.P());
        if (r.rows.size() == 0) {
            return;
        }
        String hash = Password.hashBlocking("Wonder2026");
        int i = 0;
        while (i < r.rows.size()) {
            ArrayList<Object> up = new ArrayList<Object>();
            up.add(hash);
            up.add(WcApi.l(r.rows.get(i).getLong(0)));
            WcDb.exec("UPDATE wc_users SET password_hash=? WHERE id=?", up);
            i = i + 1;
        }
    }
}
