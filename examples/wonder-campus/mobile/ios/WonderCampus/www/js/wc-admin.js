/* 教务：学员档案、招生线索 CRM、售卖报名收款。 */
(function () {
  var WC = window.WC = window.WC || {};
  var K = WC.kit;
  function core() { return WC.core; }
  function params() { return core().state.params || {}; }
  var canWrite = function () { return core().state.me.identity === "ADMIN"; };

  var SOURCES = [{ v: "WALK_IN", t: "到店" }, { v: "REFERRAL", t: "转介绍" }, { v: "ONLINE", t: "线上" },
    { v: "PHONE", t: "电话" }, { v: "EVENT", t: "活动" }, { v: "OTHER", t: "其他" }];
  function sourceLabel(v) { var r = SOURCES.filter(function (s) { return s.v === v; })[0]; return r ? r.t : v; }
  var LEAD_STATUS = { NEW: "新线索", CONTACTED: "已联系", TRIAL_BOOKED: "已约试听", TRIAL_DONE: "试听完成", WON: "已成交", LOST: "已流失" };
  var METHODS = [{ v: "WECHAT", t: "微信" }, { v: "ALIPAY", t: "支付宝" }, { v: "CASH", t: "现金" }, { v: "BANK", t: "银行卡" }];
  var STUDENT_STATUS = { ACTIVE: "在读", INACTIVE: "休学", GRADUATED: "结业" };

  /* ================= 学员列表 ================= */
  var stuFilter = { q: "", status: "ACTIVE" };
  WC.pages.students = function () {
    var chips = [["ACTIVE", "在读"], ["INACTIVE", "休学"], ["GRADUATED", "结业"], ["", "全部"]];
    core().shell(K.page("学员档案",
      '<input id="stuQ" type="search" placeholder="搜索姓名 / 家长 / 手机号" value="' + K.esc(stuFilter.q) + '" />' +
      '<div class="chips">' + chips.map(function (c) {
        return '<button type="button" class="chip' + (stuFilter.status === c[0] ? " on" : "") + '" data-st="' + c[0] + '">' + c[1] + "</button>";
      }).join("") + "</div>" +
      '<div id="stuList">' + K.loading() + "</div>",
      { right: canWrite() ? '<button type="button" class="ghost" data-go="studentForm">＋ 新增</button>' : "" }));
    K.bind(null, {});
    var timer = null;
    document.getElementById("stuQ").oninput = function (e) {
      stuFilter.q = e.target.value.trim();
      clearTimeout(timer);
      timer = setTimeout(load, 300);
    };
    var cs = document.querySelectorAll("[data-st]");
    for (var i = 0; i < cs.length; i++) {
      cs[i].onclick = function (ev) { stuFilter.status = ev.currentTarget.getAttribute("data-st"); WC.pages.students(); };
    }
    function load() {
      var qs = "?q=" + encodeURIComponent(stuFilter.q) + (stuFilter.status ? "&status=" + stuFilter.status : "");
      K.api("/api/wc/admin/students" + qs).then(function (j) {
        var items = j.items || [];
        if (!items.length) { K.body("stuList", K.empty(stuFilter.q ? "没有匹配的学员" : "还没有学员，点右上角新增")); return; }
        K.body("stuList", '<ul class="list">' + items.map(function (s) {
          return '<li class="tap" data-go="studentDetail" data-id="' + s.id + '"><div class="row"><div class="title">' + K.esc(s.name) +
            (s.status !== "ACTIVE" ? " " + K.pill(STUDENT_STATUS[s.status] || s.status) : "") + "</div>" +
            '<div class="muted">剩余 ' + K.lessons(s.balanceMilli) + " 课时</div></div>" +
            '<div class="meta">' + K.esc(s.guardianName) + " " + K.esc(s.guardianPhone) + " · " + K.esc(s.campusName) + "</div></li>";
        }).join("") + "</ul>");
        K.bind(null, {});
      }).catch(function (e) { K.body("stuList", K.error(e)); });
    }
    load();
  };

  /* ================= 学员表单 ================= */
  function campusOptions() {
    return K.api("/api/wc/campuses").then(function (j) {
      return (j.items || []).map(function (c) { return { v: c.id, t: c.name }; });
    });
  }

  WC.pages.studentForm = function () {
    var editId = params().id || 0;
    core().shell(K.page(editId ? "编辑学员" : "新增学员", '<div id="sf">' + K.loading() + "</div>"));
    K.bind(null, {});
    var pre = editId ? K.api("/api/wc/students/" + editId + "/detail") : Promise.resolve({});
    Promise.all([pre, campusOptions()]).then(function (res) {
      var d = res[0], camps = res[1];
      var h = K.field("学员姓名 *", "sfName", { value: d.name || "" }) +
        K.select("性别", "sfGender", [{ v: "", t: "未填" }, { v: "F", t: "女" }, { v: "M", t: "男" }], d.gender || "") +
        K.field("生日", "sfBirth", { type: "date", value: d.birthday || "" }) +
        K.field("就读学校", "sfSchool", { value: d.school || "" });
      if (!editId) {
        h += K.select("校区", "sfCampus", camps, "") +
          K.field("家长姓名", "sfGName") + K.field("家长手机", "sfGPhone", { type: "tel" });
      } else {
        h += K.select("状态", "sfStatus", [{ v: "ACTIVE", t: "在读" }, { v: "INACTIVE", t: "休学" }, { v: "GRADUATED", t: "结业" }], d.status);
      }
      h += K.field("备注（家长不可见）", "sfNote", { type: "textarea", value: d.note || "" }) +
        '<button type="button" data-act="save">保存</button>';
      K.body("sf", h);
      K.bind(null, {
        save: function () {
          var name = K.val("sfName");
          if (!name) { core().toast("请填写学员姓名"); return; }
          var body = { name: name, gender: K.val("sfGender"), birthday: K.val("sfBirth"), school: K.val("sfSchool"), note: K.val("sfNote") };
          var url;
          if (editId) { body.status = K.val("sfStatus"); url = "/api/wc/students/" + editId + "/update"; }
          else {
            body.campusId = K.num("sfCampus"); body.guardianName = K.val("sfGName"); body.guardianPhone = K.val("sfGPhone");
            url = "/api/wc/admin/students";
          }
          K.submit(function () {
            return K.api(url, { method: "POST", body: body }).then(function (j) {
              core().toast("已保存");
              core().state.stack.pop();
              core().go("studentDetail", { id: editId || j.studentId });
            });
          });
        }
      });
    }).catch(function (e) { K.body("sf", K.error(e)); });
  };

  /* ================= 学员详情 ================= */
  WC.pages.studentDetail = function () {
    var id = params().id;
    core().shell(K.page("学员详情", '<div id="sd">' + K.loading() + "</div>"));
    K.bind(null, {});
    function load() {
      K.api("/api/wc/students/" + id + "/detail").then(function (d) {
        var h = '<div class="card"><div class="big">' + K.esc(d.name) + " " + K.pill(STUDENT_STATUS[d.status] || d.status) + "</div>" +
          '<div class="muted">' + (d.gender === "F" ? "女" : (d.gender === "M" ? "男" : "")) + (d.birthday ? " · " + K.esc(d.birthday) : "") +
          (d.school ? " · " + K.esc(d.school) : "") + "</div>" + (d.note ? '<div class="note">备注：' + K.esc(d.note) + "</div>" : "") + "</div>";
        h += '<div class="kpis">' + K.kpi(d.stats.present, "出勤") + K.kpi(d.stats.absent, "缺勤", { warn: d.stats.absent > 0 }) +
          K.kpi(d.stats.leaves, "请假") + K.kpi(d.stats.feedbacks, "反馈") + "</div>";
        h += "<h2>家长</h2>";
        h += d.guardians.length ? '<ul class="list">' + d.guardians.map(function (g) {
          return "<li><div class=\"row\"><div class=\"title\">" + K.esc(g.name) + " " + K.esc(g.phone) + "</div>" +
            K.pill(g.registered ? "已注册" : "未注册", g.registered ? "ok" : "") + "</div></li>";
        }).join("") + "</ul>" : K.empty("暂无家长信息");
        if (canWrite()) {
          h += '<div class="actions"><button type="button" class="secondary" data-act="invite">生成家长邀请码</button>' +
            '<button type="button" class="secondary" data-act="addG">＋ 家长</button></div><div id="inviteBox"></div><div id="gForm"></div>';
        }
        h += "<h2>合同与课时</h2>";
        h += d.contracts.length ? '<ul class="list">' + d.contracts.map(function (c) {
          var owed = c.receivableCents - c.receivedCents + c.refundedCents;
          var bal = c.mode === "PACK" ? "剩余 " + K.lessons(c.remainingMilli) + " 课时 · 到期 " + K.dateFull(c.expireAt)
            : "学期制 " + c.promised + " 次 · 截止 " + K.dateFull(c.termEnd);
          return '<li><div class="row"><div class="title">' + K.esc(c.courseName) + " " + K.pill(c.mode === "PACK" ? "课时包" : "学期", "") + "</div>" +
            (owed > 0 ? K.pill("欠 " + K.money(owed), "bad") : K.pill("已结清", "ok")) + "</div>" +
            '<div class="meta">' + bal + "</div><div class=\"meta\">应收 " + K.money(c.receivableCents) + " · 实收 " + K.money(c.receivedCents) +
            (c.refundedCents ? " · 退款 " + K.money(c.refundedCents) : "") + "</div>" +
            (canWrite() && owed > 0 ? '<div class="inline-actions"><button type="button" class="mini" data-act="pay" data-id="' + c.id + '" data-owed="' + owed + '">登记收款</button></div>' : "") +
            '<div id="pay' + c.id + '"></div></li>';
        }).join("") + "</ul>" : K.empty("还没有购买课程");
        if (canWrite()) {
          h += '<div class="actions"><button type="button" data-go="sell" data-id="' + id + '">报名 / 续费</button>' +
            '<button type="button" class="secondary" data-go="studentForm" data-id="' + id + '">编辑资料</button></div>';
        }
        K.body("sd", h);
        K.bind(null, {
          invite: function () {
            K.submit(function () {
              return K.api("/api/wc/invites", { method: "POST", body: { identity: "PARENT", studentId: id } }).then(function (j) {
                K.body("inviteBox", '<div class="card invite"><div class="muted">邀请码（72 小时内有效，仅可使用一次）</div><div class="code">' +
                  K.esc(j.code) + '</div><div class="muted">让家长打开 App →「邀请码注册」输入</div><button type="button" class="mini" data-act="copy" data-code="' + K.esc(j.code) + '">复制</button></div>');
                K.bind(null, {
                  copy: function (el) { K.copy("奇想校园邀请码：" + el.getAttribute("data-code")).then(function (ok) { core().toast(ok ? "已复制" : "请长按选择复制"); }); }
                });
              });
            });
          },
          addG: function () {
            K.body("gForm", '<div class="card">' + K.field("家长姓名", "agName") + K.field("手机号", "agPhone", { type: "tel" }) +
              '<button type="button" data-act="saveG">保存</button></div>');
            K.bind(null, {
              saveG: function () {
                K.submit(function () {
                  return K.api("/api/wc/students/" + id + "/guardians", { method: "POST", body: { name: K.val("agName"), phone: K.val("agPhone") } })
                    .then(function () { core().toast("已添加"); load(); });
                });
              }
            });
          },
          pay: function (el) {
            var cid = el.getAttribute("data-id"), owed = parseInt(el.getAttribute("data-owed"), 10);
            K.body("pay" + cid, '<div class="card">' + K.field("收款金额（元）", "pyAmt", { value: (owed / 100).toFixed(2), attrs: 'inputmode="decimal"' }) +
              K.select("方式", "pyMethod", METHODS, "WECHAT") + K.field("收据号（可空）", "pyRcpt") +
              '<button type="button" data-act="doPay" data-id="' + cid + '">确认收款</button></div>');
            var key = core().idem("pay");
            K.bind(null, {
              doPay: function () {
                var cents = K.toCents(K.val("pyAmt"));
                if (!(cents > 0)) { core().toast("请输入正确金额"); return; }
                if (cents > owed) { core().toast("收款不能超过欠款 " + K.money(owed)); return; }
                K.submit(function () {
                  return K.api("/api/wc/payments", { method: "POST", body: { contractId: parseInt(cid, 10), amountCents: cents, method: K.val("pyMethod"), receiptNo: K.val("pyRcpt") || ("R" + Date.now()), idempotencyKey: key } })
                    .then(function () { core().toast("已登记收款"); load(); });
                });
              }
            });
          }
        });
      }).catch(function (e) { K.body("sd", K.error(e)); });
    }
    load();
  };

  /* ================= 线索列表 ================= */
  var leadFilter = { status: "OPEN", q: "", due: 0 };
  WC.pages.leads = function () {
    if (params().due) { leadFilter.due = 1; leadFilter.status = "OPEN"; }
    var tabs = [{ k: "OPEN", t: "跟进中" }, { k: "WON", t: "已成交" }, { k: "LOST", t: "已流失" }];
    core().shell(K.page("招生线索",
      K.tabs(tabs, leadFilter.status) +
      '<input id="ldQ" type="search" placeholder="搜索孩子 / 家长 / 手机" value="' + K.esc(leadFilter.q) + '" />' +
      (leadFilter.due ? '<p class="alert">仅显示今日需跟进 <button type="button" class="mini" data-act="clrDue">显示全部</button></p>' : "") +
      '<div id="ldList">' + K.loading() + "</div>",
      { right: canWrite() ? '<button type="button" class="ghost" data-go="leadForm">＋ 新增</button>' : "" }));
    K.bind(null, {
      tab: function (k) { leadFilter.status = k; leadFilter.due = 0; WC.pages.leads(); },
      clrDue: function () { leadFilter.due = 0; core().state.params = {}; WC.pages.leads(); }
    });
    var timer = null;
    document.getElementById("ldQ").oninput = function (e) { leadFilter.q = e.target.value.trim(); clearTimeout(timer); timer = setTimeout(load, 300); };
    function load() {
      var qs = "?status=" + leadFilter.status + "&q=" + encodeURIComponent(leadFilter.q) + (leadFilter.due ? "&due=1" : "");
      K.api("/api/wc/leads" + qs).then(function (j) {
        var items = j.items || [];
        if (!items.length) { K.body("ldList", K.empty("没有线索")); return; }
        var now = Date.now();
        K.body("ldList", '<ul class="list">' + items.map(function (l) {
          var overdue = l.nextFollowAt && l.nextFollowAt < now && l.status !== "WON" && l.status !== "LOST";
          return '<li class="tap" data-go="leadDetail" data-id="' + l.id + '"><div class="row"><div class="title">' + K.esc(l.childName) +
            (l.childAge ? " · " + l.childAge + "岁" : "") + "</div>" + K.pill(LEAD_STATUS[l.status] || l.status, l.status === "WON" ? "ok" : (l.status === "LOST" ? "bad" : "")) + "</div>" +
            '<div class="meta">' + K.esc(l.guardianName) + " " + K.esc(l.phone) + " · " + sourceLabel(l.source) +
            (l.intentCourse ? " · 意向 " + K.esc(l.intentCourse) : "") + "</div>" +
            (l.nextFollowAt ? '<div class="meta' + (overdue ? " late" : "") + '">下次跟进 ' + K.dt(l.nextFollowAt) + (overdue ? "（已逾期）" : "") + "</div>" : "") + "</li>";
        }).join("") + "</ul>");
        K.bind(null, {});
      }).catch(function (e) { K.body("ldList", K.error(e)); });
    }
    load();
  };

  function courseOptions(withBlank) {
    return K.api("/api/wc/courses").then(function (j) {
      var o = (j.items || []).filter(function (c) { return c.status === "ACTIVE"; }).map(function (c) { return { v: c.id, t: c.name }; });
      return withBlank ? [{ v: 0, t: "未定" }].concat(o) : o;
    });
  }

  WC.pages.leadForm = function () {
    core().shell(K.page("新增线索", '<div id="lf">' + K.loading() + "</div>"));
    K.bind(null, {});
    Promise.all([courseOptions(true), campusOptions()]).then(function (res) {
      K.body("lf", K.field("孩子姓名 *", "lfChild") + K.field("年龄", "lfAge", { type: "number", attrs: 'min="1" max="18"' }) +
        K.field("家长称呼", "lfG") + K.field("联系电话 *", "lfPhone", { type: "tel" }) +
        K.select("来源", "lfSrc", SOURCES, "WALK_IN") + K.select("意向课程", "lfCourse", res[0], 0) +
        K.select("意向校区", "lfCampus", res[1], "") +
        K.field("下次跟进时间", "lfNext", { type: "datetime-local", value: K.toLocalInput(Date.now() + 86400000) }) +
        K.field("备注", "lfNote", { type: "textarea" }) + '<button type="button" data-act="save">保存线索</button>');
      K.bind(null, {
        save: function () {
          if (!K.val("lfChild") || K.val("lfPhone").length < 6) { core().toast("请填写孩子姓名和有效电话"); return; }
          K.submit(function () {
            return K.api("/api/wc/leads", {
              method: "POST", body: {
                childName: K.val("lfChild"), childAge: K.num("lfAge"), guardianName: K.val("lfG"), phone: K.val("lfPhone"), source: K.val("lfSrc"),
                intentCourseId: K.num("lfCourse"), campusId: K.num("lfCampus"), nextFollowAt: K.fromLocalInput(K.val("lfNext")), note: K.val("lfNote")
              }
            }).then(function (j) { core().toast("线索已创建"); core().state.stack.pop(); core().go("leadDetail", { id: j.leadId }); });
          });
        }
      });
    }).catch(function (e) { K.body("lf", K.error(e)); });
  };

  /* ================= 线索详情 ================= */
  WC.pages.leadDetail = function () {
    var id = params().id;
    core().shell(K.page("线索详情", '<div id="ld">' + K.loading() + "</div>"));
    K.bind(null, {});
    function load() {
      Promise.all([K.api("/api/wc/leads/" + id), K.api("/api/wc/teachers"), courseOptions(true)]).then(function (res) {
        var l = res[0], teachers = res[1].items || [], courses = res[2];
        var open = ["NEW", "CONTACTED", "TRIAL_BOOKED", "TRIAL_DONE"].indexOf(l.status) >= 0;
        var h = '<div class="card"><div class="row"><div class="big">' + K.esc(l.childName) + (l.childAge ? " · " + l.childAge + "岁" : "") + "</div>" +
          K.pill(LEAD_STATUS[l.status] || l.status, l.status === "WON" ? "ok" : (l.status === "LOST" ? "bad" : "")) + "</div>" +
          "<div>" + K.esc(l.guardianName) + ' <a href="tel:' + K.esc(l.phone) + '">' + K.esc(l.phone) + "</a></div>" +
          '<div class="muted">来源 ' + sourceLabel(l.source) + " · 创建于 " + K.dateFull(l.createdAt) + "</div>" +
          (l.note ? '<div class="note">' + K.esc(l.note) + "</div>" : "") +
          (l.status === "LOST" ? '<div class="alert">流失原因：' + K.esc(l.lostReason) + "</div>" : "") +
          (l.nextFollowAt && open ? '<div class="muted">下次跟进 ' + K.dt(l.nextFollowAt) + "</div>" : "") + "</div>";
        if (open && canWrite()) {
          h += '<div class="actions"><button type="button" data-act="showFollow">记录跟进</button><button type="button" class="secondary" data-act="showTrial">预约试听</button>' +
            '<button type="button" class="secondary" data-act="convert">转为学员</button><button type="button" class="ghost block" data-act="showLose">标记流失</button></div>' +
            '<div id="lfBox"></div>';
        }
        if (l.status === "WON" && l.convertedStudentId) {
          h += '<div class="actions"><button type="button" data-go="studentDetail" data-id="' + l.convertedStudentId + '">查看学员档案</button>' +
            '<button type="button" class="secondary" data-go="sell" data-id="' + l.convertedStudentId + '" data-p="leadId=' + id + '">去报名收款</button></div>';
        }
        if (l.trials.length) {
          h += "<h2>试听</h2><ul class=\"list\">" + l.trials.map(function (t) {
            var st = { BOOKED: "已预约", ATTENDED: "已到课", NOSHOW: "未到", CANCELLED: "已取消" }[t.status] || t.status;
            return "<li><div class=\"row\"><div class=\"title\">" + K.dt(t.startAt) + " " + K.esc(t.courseName) + " " + K.esc(t.teacherName) + "</div>" + K.pill(st, t.status === "ATTENDED" ? "ok" : "") + "</div>" +
              (t.resultNote ? '<div class="meta">' + K.esc(t.resultNote) + "</div>" : "") +
              (t.status === "BOOKED" && canWrite() ? '<div class="inline-actions"><button type="button" class="mini" data-act="tr" data-id="' + t.id + '" data-r="ATTENDED">已到课</button>' +
                '<button type="button" class="mini alt" data-act="tr" data-id="' + t.id + '" data-r="NOSHOW">未到</button></div>' : "") + "</li>";
          }).join("") + "</ul>";
        }
        h += "<h2>跟进记录</h2>" + (l.follows.length ? '<ul class="list timeline">' + l.follows.map(function (f) {
          return "<li><div class=\"meta\">" + K.dt(f.createdAt) + " · " + K.esc(f.userName) + " · " + ({ CALL: "电话", WECHAT: "微信", VISIT: "到访", NOTE: "备注" }[f.kind] || f.kind) + "</div><div>" + K.esc(f.content) + "</div></li>";
        }).join("") + "</ul>" : K.empty("暂无跟进"));
        K.body("ld", h);
        K.bind(null, {
          showFollow: function () {
            K.body("lfBox", '<div class="card">' + K.select("方式", "fwKind", [{ v: "CALL", t: "电话" }, { v: "WECHAT", t: "微信" }, { v: "VISIT", t: "到访" }, { v: "NOTE", t: "备注" }], "CALL") +
              K.field("沟通内容 *", "fwText", { type: "textarea" }) +
              K.field("下次跟进时间", "fwNext", { type: "datetime-local", value: K.toLocalInput(Date.now() + 86400000 * 2) }) +
              '<button type="button" data-act="saveFollow">保存</button></div>');
            K.bind(null, {
              saveFollow: function () {
                if (!K.val("fwText")) { core().toast("请填写沟通内容"); return; }
                K.submit(function () {
                  return K.api("/api/wc/leads/" + id + "/follow", { method: "POST", body: { kind: K.val("fwKind"), content: K.val("fwText"), nextFollowAt: K.fromLocalInput(K.val("fwNext")) } })
                    .then(function () { core().toast("已记录"); load(); });
                });
              }
            });
          },
          showTrial: function () {
            K.body("lfBox", '<div class="card">' + K.field("试听时间 *", "trAt", { type: "datetime-local", value: K.toLocalInput(Date.now() + 86400000 * 2) }) +
              K.select("课程", "trCourse", courses, l.intentCourseId) +
              K.select("试听老师", "trTeacher", [{ v: 0, t: "待定" }].concat(teachers.map(function (t) { return { v: t.id, t: t.name }; })), 0) +
              '<button type="button" data-act="saveTrial">确认预约</button></div>');
            K.bind(null, {
              saveTrial: function () {
                var at = K.fromLocalInput(K.val("trAt"));
                if (!at) { core().toast("请选择试听时间"); return; }
                K.submit(function () {
                  return K.api("/api/wc/leads/" + id + "/trial", { method: "POST", body: { startAt: at, courseId: K.num("trCourse"), teacherUserId: K.num("trTeacher") } })
                    .then(function () { core().toast("已预约试听"); load(); });
                });
              }
            });
          },
          tr: function (el) {
            var tid = el.getAttribute("data-id"), r = el.getAttribute("data-r");
            var note = r === "ATTENDED" ? (window.prompt("试听反馈（可空）", "") || "") : "";
            K.submit(function () {
              return K.api("/api/wc/trials/" + tid + "/result", { method: "POST", body: { result: r, note: note } }).then(function () { core().toast("已更新"); load(); });
            });
          },
          convert: function () {
            if (!window.confirm("将为「" + l.childName + "」创建学员档案和家长信息，确认？")) return;
            K.submit(function () {
              return K.api("/api/wc/leads/" + id + "/convert", { method: "POST", body: {} }).then(function (j) {
                core().toast("已转为学员");
                core().go("sell", { id: j.studentId, leadId: id });
              });
            });
          },
          showLose: function () {
            K.body("lfBox", '<div class="card">' + K.select("流失原因 *", "lsReason", [{ v: "价格太高", t: "价格太高" }, { v: "距离太远", t: "距离太远" }, { v: "时间不合适", t: "时间不合适" },
              { v: "选择了其他机构", t: "选择了其他机构" }, { v: "孩子不感兴趣", t: "孩子不感兴趣" }, { v: "联系不上", t: "联系不上" }, { v: "其他", t: "其他" }], "价格太高") +
              '<button type="button" class="danger" data-act="doLose">确认流失</button></div>');
            K.bind(null, {
              doLose: function () {
                K.submit(function () {
                  return K.api("/api/wc/leads/" + id + "/lose", { method: "POST", body: { reason: K.val("lsReason") } }).then(function () { core().toast("已标记流失"); load(); });
                });
              }
            });
          }
        });
      }).catch(function (e) { K.body("ld", K.error(e)); });
    }
    load();
  };

  /* ================= 售卖 / 报名收款 ================= */
  WC.pages.sell = function () {
    var p = params();
    var key = core().idem("sell");
    core().shell(K.page("售卖 · 报名收款", '<div id="sl">' + K.loading() + "</div>"));
    K.bind(null, {});
    Promise.all([K.api("/api/wc/admin/students"), K.api("/api/wc/products"), K.api("/api/wc/classes")]).then(function (res) {
      var students = res[0].items || [], products = (res[1].items || []).filter(function (x) { return x.status === "ACTIVE"; }), classes = res[2].items || [];
      if (!products.length) { K.body("sl", K.empty("还没有在售商品，请先到「课程班级 → 商品」创建")); return; }
      var h = K.select("学员 *", "slStu", students.map(function (s) { return { v: s.id, t: s.name + (s.guardianPhone ? " · " + s.guardianPhone : "") }; }), p.id || "") +
        K.select("商品 *", "slProd", products.map(function (x) { return { v: x.id, t: x.name + "  " + K.money(x.priceCents) }; }), "") +
        '<div id="slInfo" class="muted"></div>' +
        K.select("分班（可选）", "slClass", [{ v: 0, t: "暂不分班" }].concat(classes.map(function (c) { return { v: c.id, t: c.name + "（" + c.campusName + "）" }; })), 0) +
        K.field("优惠金额（元）", "slDisc", { value: "0", attrs: 'inputmode="decimal"' }) +
        K.field("本次收款（元，可分期）", "slPay", { value: "", attrs: 'inputmode="decimal"' }) +
        K.select("收款方式", "slMethod", METHODS, "WECHAT") + K.field("收据号（可空）", "slRcpt") +
        '<div class="card sum" id="slSum"></div><button type="button" data-act="submit">确认报名并收款</button>';
      K.body("sl", h);
      function prod() { var id = K.num("slProd"); return products.filter(function (x) { return x.id === id; })[0]; }
      var payTouched = false;
      function recalc() {
        var x = prod(); if (!x) return;
        var disc = K.toCents(K.val("slDisc") || "0"); if (isNaN(disc)) disc = 0;
        var recv = Math.max(0, x.priceCents - disc);
        if (!payTouched) document.getElementById("slPay").value = (recv / 100).toFixed(2);
        var pay = K.toCents(K.val("slPay") || "0"); if (isNaN(pay)) pay = 0;
        var info = x.kind === "PACK" ? "课时包：" + K.lessons(x.paidMilli) + " 课时 + 赠送 " + K.lessons(x.bonusMilli) + "，有效 " + x.validDays + " 天"
          : "学期制：" + x.termSessions + " 次，" + x.termDays + " 天";
        K.body("slInfo", K.esc(info));
        K.body("slSum", "应收 <b>" + K.money(recv) + "</b> · 本次收款 <b>" + K.money(pay) + "</b> · 欠款 <b>" + K.money(Math.max(0, recv - pay)) + "</b>");
      }
      ["slProd", "slDisc", "slPay"].forEach(function (id) {
        document.getElementById(id).oninput = function () { if (id === "slPay") payTouched = true; if (id !== "slPay") payTouched = false; recalc(); };
      });
      recalc();
      K.bind(null, {
        submit: function () {
          var x = prod();
          var disc = K.toCents(K.val("slDisc") || "0"), pay = K.toCents(K.val("slPay") || "0");
          if (!K.num("slStu")) { core().toast("请选择学员"); return; }
          if (isNaN(disc) || isNaN(pay)) { core().toast("金额格式不正确"); return; }
          if (disc > x.priceCents) { core().toast("优惠不能超过商品价格"); return; }
          if (pay > x.priceCents - disc) { core().toast("收款不能超过应收"); return; }
          K.submit(function () {
            return K.api("/api/wc/enroll/product", {
              method: "POST", body: {
                studentId: K.num("slStu"), productId: x.id, classId: K.num("slClass"), discountCents: disc, payCents: pay,
                payMethod: K.val("slMethod"), receiptNo: K.val("slRcpt") || ("R" + Date.now()), leadId: p.leadId ? parseInt(p.leadId, 10) : 0, idempotencyKey: key
              }
            }).then(function () {
              core().toast("报名成功");
              core().state.stack.pop();
              core().go("studentDetail", { id: K.num("slStu") });
            });
          });
        }
      });
    }).catch(function (e) { K.body("sl", K.error(e)); });
  };
})();
