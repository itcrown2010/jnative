#!/usr/bin/env python3
# Author: 张晓达
"""Commercial-module API smoke/regression test against a running wonder-server (demo seed, TEST data).

Usage: WC_BASE=http://127.0.0.1:18880 python3 scripts/smoke_commercial.py
Exit 0 only when every assertion passes.
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
import http.cookiejar

BASE = os.environ.get("WC_BASE", "http://127.0.0.1:18880") + "/api/wc"
PW = "Wonder2026"
fails = []
passed = 0


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None, raw=False):
        data = None
        headers = {}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode()
            headers["Content-Type"] = "application/json"
        elif method == "POST":
            data = b"{}"
            headers["Content-Type"] = "application/json"
        path = urllib.parse.quote(path, safe="/?&=%")
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.op.open(req, timeout=30) as r:
                txt = r.read().decode()
                return txt if raw else json.loads(txt)
        except urllib.error.HTTPError as e:
            txt = e.read().decode()
            try:
                return json.loads(txt)
            except Exception:
                return {"ok": 0, "code": "HTTP_%d" % e.code, "message": txt}

    def get(self, p, **k):
        return self.call("GET", p, **k)

    def post(self, p, b=None):
        return self.call("POST", p, b)


def check(name, cond, extra=""):
    global passed
    if cond:
        passed += 1
        print("PASS", name)
    else:
        fails.append(name)
        print("FAIL", name, extra)


def login(login_name, identity=None, pw=PW):
    c = Client()
    b = {"login": login_name, "password": pw}
    if identity:
        b["identity"] = identity
    r = c.post("/auth/login", b)
    return c, r


def main():
    tag = str(int(time.time()))[-6:]
    # ---- auth
    _, r = login("admin", pw="wrong")
    check("login rejects bad password", r.get("ok") == 0 and r.get("code") == "BAD_CREDENTIALS", r)
    admin, r = login("admin")
    check("admin password login", r.get("ok") == 1 and r.get("identity") == "ADMIN", r)
    anon = Client()
    check("unauthenticated is 401", anon.get("/admin/dashboard").get("code") == "AUTH_REQUIRED")

    # ---- dashboard / reports
    d = admin.get("/admin/dashboard")
    check("dashboard", d.get("ok") == 1 and "followDue" in d, d)
    o = admin.get("/reports/overview")
    check("overview has money+funnel", o.get("ok") == 1 and "receivedCents" in o and "conversionPermille" in o, o)
    check("daily report 14 days", len(admin.get("/reports/daily").get("items", [])) == 14)
    check("renewal report", admin.get("/reports/renewal").get("ok") == 1)
    check("arrears report", admin.get("/reports/arrears").get("ok") == 1)
    check("funnel report", admin.get("/reports/funnel").get("ok") == 1)

    # ---- students / guardians / invites / registration
    r = admin.post("/admin/students", {"name": "TEST新生" + tag, "gender": "F", "birthday": "2019-05-01",
                                        "guardianName": "TEST新家长" + tag, "guardianPhone": "139" + tag + "00"})
    check("create student", r.get("ok") == 1 and r.get("studentId", 0) > 0, r)
    sid = r.get("studentId", 0)
    check("create student requires name", admin.post("/admin/students", {"name": ""}).get("ok") == 0)
    lst = admin.get("/admin/students?q=TEST新生" + tag)
    check("student search", len(lst.get("items", [])) == 1, lst)
    det = admin.get("/students/%d/detail" % sid)
    check("student detail", det.get("ok") == 1 and len(det.get("guardians", [])) == 1, det)
    r = admin.post("/students/%d/update" % sid, {"name": "TEST新生" + tag, "status": "BAD"})
    check("student update rejects bad status", r.get("ok") == 0)
    inv = admin.post("/invites", {"identity": "PARENT", "studentId": sid})
    check("create invite", inv.get("ok") == 1 and len(inv.get("code", "")) == 8, inv)
    code = inv.get("code", "")
    reg = Client()
    r = reg.post("/auth/register", {"inviteCode": code, "login": "p" + tag, "password": "short", "displayName": "新家长"})
    check("register rejects weak password", r.get("code") == "WEAK_PASSWORD", r)
    r = reg.post("/auth/register", {"inviteCode": code, "login": "p" + tag, "password": "Parent" + tag,
                                     "displayName": "新家长" + tag, "phone": "139" + tag + "00"})
    check("register by invite logs in as parent", r.get("ok") == 1 and r.get("identity") == "PARENT", r)
    r2 = Client().post("/auth/register", {"inviteCode": code, "login": "q" + tag, "password": "Parent" + tag,
                                          "displayName": "x"})
    check("invite is single-use", r2.get("code") == "INVITE_USED", r2)
    kids = reg.get("/students")
    check("new parent sees only own child", [k["id"] for k in kids.get("items", [])] == [sid], kids)
    check("parent cannot see other child", reg.get("/students/1/detail").get("code") == "FORBIDDEN")
    check("parent cannot access admin api", reg.get("/admin/dashboard").get("code") == "FORBIDDEN")
    r = reg.post("/auth/password", {"oldPassword": "bad", "newPassword": "Newpass" + tag})
    check("change password verifies old", r.get("code") == "BAD_CREDENTIALS", r)
    r = reg.post("/auth/password", {"oldPassword": "Parent" + tag, "newPassword": "Newpass" + tag})
    check("change password", r.get("ok") == 1, r)
    _, r = login("p" + tag, pw="Newpass" + tag)
    check("login with new password", r.get("ok") == 1, r)

    # ---- courses / classes / rooms / staff / settings
    check("courses list", len(admin.get("/courses").get("items", [])) >= 4)
    r = admin.post("/courses", {"name": "TEST陶艺" + tag, "subject": "手工"})
    check("create course", r.get("ok") == 1, r)
    camps = admin.get("/campuses")
    campus = (camps.get("items") or camps.get("campuses") or [{}])[0].get("id", 1)
    r = admin.post("/rooms", {"name": "TEST教室" + tag, "campusId": campus, "capacity": 6})
    check("create room", r.get("ok") == 1, r)
    course_id = admin.get("/courses").get("items")[0]["id"]
    r = admin.post("/classes", {"name": "TEST陶艺班" + tag, "campusId": campus, "courseId": course_id, "capacity": 1})
    kid_class = r.get("classId", 0)
    check("create class (capacity 1)", r.get("ok") == 1, r)
    check("class add member", admin.post("/classes/%d/members" % kid_class, {"studentId": sid}).get("ok") == 1)
    r = admin.post("/classes/%d/members" % kid_class, {"studentId": 1})
    check("class rejects over capacity", r.get("code") == "CLASS_FULL", r)
    r = admin.post("/staff", {"login": "t" + tag, "displayName": "新老师", "password": "Teacher" + tag,
                              "identity": "TEACHER", "campusId": campus, "perSessionCents": 7000})
    check("create staff", r.get("ok") == 1, r)
    t_new = r.get("userId", 0)
    check("staff list", any(s["id"] == t_new for s in admin.get("/staff").get("items", [])))
    r = admin.post("/staff/%d" % t_new, {"displayName": "新老师", "status": "DISABLED"})
    check("disable staff", r.get("ok") == 1)
    _, r = login("t" + tag, pw="Teacher" + tag)
    check("disabled account cannot login", r.get("code") == "ACCOUNT_DISABLED", r)
    st = admin.get("/settings")
    check("settings get", st.get("ok") == 1 and "leaveDeadlineHours" in st, st)
    check("settings save", admin.post("/settings", {"renewDays": "30"}).get("ok") == 1)

    # ---- products + enroll by product + payment
    prods = admin.get("/products").get("items", [])
    check("products seeded", len(prods) >= 3)
    pack = [p for p in prods if p["kind"] == "PACK"][0]
    r = admin.post("/enroll/product", {"studentId": sid, "productId": pack["id"], "discountCents": 20000,
                                       "payCents": 100000, "payMethod": "WECHAT", "receiptNo": "R" + tag,
                                       "idempotencyKey": "smoke-enroll-" + tag})
    check("enroll by product with partial pay", r.get("ok") == 1 and r.get("contractId", 0) > 0, r)
    cid = r.get("contractId", 0)
    r = admin.post("/enroll/product", {"studentId": sid, "productId": pack["id"], "discountCents": 20000,
                                       "payCents": 100000, "idempotencyKey": "smoke-enroll-" + tag})
    check("enroll idempotent", r.get("ok") == 1 and r.get("contractId") == cid, r)
    r = admin.post("/enroll/product", {"studentId": sid, "productId": pack["id"], "discountCents": 99999999,
                                       "idempotencyKey": "smoke-bad-" + tag})
    check("enroll rejects discount > price", r.get("ok") == 0, r)
    det = admin.get("/students/%d/detail" % sid)
    c0 = det["contracts"][0]
    owed = c0["receivableCents"] - c0["receivedCents"]
    check("contract shows owed balance", owed == pack["priceCents"] - 20000 - 100000, c0)
    arrears = admin.get("/reports/arrears").get("items", [])
    check("arrears lists new contract", any(a["contractId"] == cid for a in arrears))
    csv = admin.get("/reports/export?type=arrears", raw=True)
    check("csv export arrears", "合同ID" in csv and str(cid) in csv, csv[:80])
    check("csv export rejects unknown type", admin.get("/reports/export?type=zzz").get("ok") == 0)

    # ---- CRM
    r = admin.post("/leads", {"childName": "TEST线索" + tag, "phone": "138" + tag + "0", "guardianName": "线索家长",
                              "source": "ONLINE", "childAge": 6})
    check("create lead", r.get("ok") == 1, r)
    lid = r.get("leadId", 0)
    check("duplicate phone blocked", admin.post("/leads", {"childName": "x", "phone": "138" + tag + "0"}).get("code") == "DUPLICATE_LEAD")
    r = admin.post("/leads/%d/follow" % lid, {"content": "电话沟通，家长有兴趣", "kind": "CALL"})
    check("follow moves NEW→CONTACTED", r.get("status") == "CONTACTED", r)
    r = admin.post("/leads/%d/trial" % lid, {"startAt": int(time.time() * 1000) + 86400000 * 3, "courseId": 1,
                                             "teacherUserId": 0})
    check("book trial", r.get("ok") == 1, r)
    tid = r.get("trialId", 0)
    check("lead is TRIAL_BOOKED", admin.get("/leads/%d" % lid).get("status") == "TRIAL_BOOKED")
    check("trial result validated", admin.post("/trials/%d/result" % tid, {"result": "??"}).get("ok") == 0)
    check("trial attended", admin.post("/trials/%d/result" % tid, {"result": "ATTENDED", "note": "表现好"}).get("ok") == 1)
    check("lead is TRIAL_DONE", admin.get("/leads/%d" % lid).get("status") == "TRIAL_DONE")
    r = admin.post("/leads/%d/convert" % lid, {})
    check("convert lead to student", r.get("ok") == 1 and r.get("studentId", 0) > 0, r)
    check("lead is WON", admin.get("/leads/%d" % lid).get("status") == "WON")
    check("won lead cannot be followed", admin.post("/leads/%d/follow" % lid, {"content": "x"}).get("code") == "LEAD_CLOSED")
    r = admin.post("/leads", {"childName": "TEST流失" + tag, "phone": "137" + tag + "0"})
    l2 = r.get("leadId", 0)
    check("lose requires reason", admin.post("/leads/%d/lose" % l2, {}).get("ok") == 0)
    check("lose with reason", admin.post("/leads/%d/lose" % l2, {"reason": "价格太高"}).get("ok") == 1)
    check("funnel counts lost reason", any(x["reason"] == "价格太高" for x in admin.get("/reports/funnel").get("lostReasons", [])))

    # ---- announcements
    r = admin.post("/announcements", {"title": "TEST公告" + tag, "body": "内容", "audience": "PARENT", "pinned": 1})
    check("publish announcement", r.get("ok") == 1 and r.get("notified", 0) >= 1, r)
    aid = r.get("announcementId", 0)
    parent, r = login("parent1")
    check("seed parent login", r.get("ok") == 1 and r.get("identity") == "PARENT", r)
    pa = parent.get("/announcements").get("items", [])
    check("parent sees announcement unread", any(a["id"] == aid and a["read"] == 0 for a in pa))
    parent.post("/announcements/%d/read" % aid)
    pa = parent.get("/announcements").get("items", [])
    check("announcement marked read", any(a["id"] == aid and a["read"] == 1 for a in pa))
    teacher, r = login("teacher1")
    check("teacher login", r.get("identity") == "TEACHER", r)
    check("teacher does not see PARENT-only announcement", all(a["id"] != aid for a in teacher.get("/announcements").get("items", [])))
    admin.post("/announcements/%d/revoke" % aid)
    check("revoked announcement hidden from parent", all(a["id"] != aid for a in parent.get("/announcements").get("items", [])))
    check("parent cannot publish", parent.post("/announcements", {"title": "x", "body": "y"}).get("code") == "FORBIDDEN")

    # ---- homework
    classes = teacher.get("/classes").get("items", [])
    check("teacher sees own classes only", len(classes) >= 1 and all(c["teacherUserId"] == classes[0]["teacherUserId"] for c in classes))
    eng = [c for c in classes if "英语" in c["name"]][0]
    r = teacher.post("/homework", {"classId": eng["id"], "title": "TEST作业" + tag, "body": "画一幅画",
                                   "dueAt": int(time.time() * 1000) + 86400000})
    check("teacher creates homework", r.get("ok") == 1 and r.get("notified", 0) >= 1, r)
    hid = r.get("homeworkId", 0)
    kids = parent.get("/students").get("items", [])
    kid_a = [k for k in kids if k["name"] == "TEST小明"][0]["id"]
    hl = parent.get("/homework?studentId=%d" % kid_a).get("items", [])
    check("parent sees homework as TODO", any(h["id"] == hid and h["status"] == "TODO" for h in hl), hl[:1])
    check("parent cannot see other family's homework", parent.get("/homework?studentId=%d" % sid).get("code") == "FORBIDDEN")
    check("submit needs content", parent.post("/homework/%d/submit" % hid, {"studentId": kid_a}).get("ok") == 0)
    r = parent.post("/homework/%d/submit" % hid, {"studentId": kid_a, "content": "我画好了"})
    check("parent submits homework", r.get("ok") == 1, r)
    subs = teacher.get("/homework/%d/submissions" % hid).get("items", [])
    mine = [s for s in subs if s["studentId"] == kid_a]
    check("teacher sees submission", len(mine) == 1 and mine[0]["status"] == "SUBMITTED", subs)
    check("review validates stars", teacher.post("/submissions/%d/review" % mine[0]["submissionId"], {"stars": 9}).get("ok") == 0)
    check("teacher reviews", teacher.post("/submissions/%d/review" % mine[0]["submissionId"], {"stars": 5, "comment": "很棒"}).get("ok") == 1)
    r = parent.post("/homework/%d/submit" % hid, {"studentId": kid_a, "content": "改一下"})
    check("reviewed homework locked", r.get("code") == "ALREADY_REVIEWED", r)
    check("parent cannot review", parent.post("/submissions/%d/review" % mine[0]["submissionId"], {"stars": 1}).get("code") == "FORBIDDEN")

    # ---- reviews
    pend = parent.get("/reviews/pending?studentId=%d" % kid_a).get("items", [])
    check("pending review exists for completed lesson", len(pend) >= 1, pend)
    sess = pend[0]["sessionId"]
    check("rating validated", parent.post("/reviews", {"sessionId": sess, "studentId": kid_a, "rating": 7}).get("ok") == 0)
    r = parent.post("/reviews", {"sessionId": sess, "studentId": kid_a, "rating": 5, "comment": "老师很耐心"})
    check("parent reviews lesson", r.get("ok") == 1, r)
    check("duplicate review blocked", parent.post("/reviews", {"sessionId": sess, "studentId": kid_a, "rating": 4}).get("code") == "ALREADY_REVIEWED")
    rv = admin.get("/reviews")
    check("admin sees review summary", len(rv.get("summary", [])) >= 1, rv)
    trv = teacher.get("/reviews")
    check("teacher review list hides student names", "studentName" not in json.dumps(trv, ensure_ascii=False))

    # ---- parent overview / schedule / renew
    po = parent.get("/parent/overview?studentId=%d" % kid_a)
    check("parent overview", po.get("ok") == 1 and "remainingMilli" in po and "pendingHomework" in po, po)
    check("parent overview forbids other child", parent.get("/parent/overview?studentId=%d" % sid).get("code") == "FORBIDDEN")
    now_ms = int(time.time() * 1000)
    sc = parent.get("/schedule/range?studentId=%d&from=%d&to=%d" % (kid_a, now_ms - 86400000 * 10, now_ms + 86400000 * 90))
    check("parent schedule range", sc.get("ok") == 1 and len(sc.get("items", [])) >= 1, sc)
    r = parent.post("/renew/request", {"studentId": kid_a, "note": "想续费"})
    check("renew request notifies admin", r.get("ok") == 1 and r.get("notified", 0) >= 1, r)
    check("renew request throttled", parent.post("/renew/request", {"studentId": kid_a}).get("code") == "TOO_FREQUENT")
    check("parent products visible", len(parent.get("/products").get("items", [])) >= 1)

    # ---- teacher
    ts = teacher.get("/teacher/students")
    check("teacher students", ts.get("ok") == 1 and len(ts.get("items", [])) >= 1, ts)
    pr = teacher.get("/reports/payroll")
    check("teacher payroll own only", pr.get("ok") == 1 and all(i["teacherName"] == "TEST专职老师" for i in pr.get("items", [])), pr)
    check("teacher cannot see reports overview", teacher.get("/reports/overview").get("code") == "FORBIDDEN")
    adm_pr = admin.get("/reports/payroll").get("items", [])
    check("admin payroll computes pay", any(i["payCents"] == i["sessions"] * i["perSessionCents"] and i["payCents"] > 0 for i in adm_pr), adm_pr)

    # ---- recurring schedule
    t0 = (int(time.time()) // 86400 + 10) * 86400 * 1000 + 3600000 * 2
    cl = admin.get("/classes").get("items", [])
    eng_c = [c for c in cl if "英语" in c["name"]][0]
    rooms = admin.get("/rooms").get("items", [])
    body = {"campusId": eng_c["campusId"], "classId": eng_c["id"], "teacherUserId": eng_c["teacherUserId"],
            "roomId": rooms[0]["id"], "studentId": 1, "contractId": 1, "firstStartAt": t0, "durationMin": 60, "weeks": 3}
    r = admin.post("/schedule/recurring", body)
    check("recurring schedule creates 3", r.get("ok") == 1 and r.get("created") == 3, r)
    r = admin.post("/schedule/recurring", body)
    check("recurring schedule detects conflicts", r.get("created") == 0 and r.get("skipped") == 3, r)

    print("\n%d passed, %d failed" % (passed, len(fails)))
    if fails:
        print("FAILED:", ", ".join(fails))
        sys.exit(1)
    print("OK wonder-campus commercial smoke")


if __name__ == "__main__":
    main()
