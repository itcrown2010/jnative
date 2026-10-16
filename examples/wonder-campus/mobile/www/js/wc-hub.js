/* 首页（四角色）、工作台 Hub、我的、消息（通知+公告）。 */
(function () {
  var WC = window.WC = window.WC || {};
  var K = WC.kit;
  function core() { return WC.core; }
  function me() { return core().state.me; }

  /* ---------- 身份切换条 ---------- */
  function identityChips() {
    var ids = (me() && me().identities) || [];
    if (ids.length < 2) return "";
    var h = '<div class="chips">';
    for (var i = 0; i < ids.length; i++) {
      h += '<button type="button" class="chip' + (ids[i] === me().identity ? " on" : "") + '" data-switch="' + ids[i] + '">' +
        K.esc(labelId(ids[i])) + "</button>";
    }
    return h + "</div>";
  }
  function labelId(id) {
    return { ADMIN: "教务", TEACHER: "老师", PARENT: "家长", DIRECTOR: "负责人" }[id] || id;
  }
  function bindSwitch() {
    var chips = document.querySelectorAll("[data-switch]");
    for (var i = 0; i < chips.length; i++) {
      chips[i].onclick = function (ev) {
        var id = ev.currentTarget.getAttribute("data-switch");
        if (id === me().identity) return;
        K.submit(function () {
          return K.api("/api/wc/auth/switch-identity", { method: "POST", body: { identity: id } }).then(function () {
            core().clearRoleCache();
            core().state.stack = [];
            return core().refreshMe();
          }).then(function () {
            core().state.page = "home";
            core().render();
            core().toast("已切换到" + labelId(id));
          });
        });
      };
    }
  }

  function greeting() {
    var m = me();
    var h = new Date().getHours();
    var t = h < 11 ? "早上好" : (h < 18 ? "下午好" : "晚上好");
    return t + "，" + K.esc(m.displayName || "") + '<span class="role-tag">' + K.esc(labelId(m.identity)) + "</span>";
  }

  /* ---------- 课次卡片 ---------- */
  function sessionCard(s, o) {
    o = o || {};
    var who = s.studentName || s.className || "";
    var h = '<li class="sess"><div class="row"><div><div class="title">' + K.hm(s.startAt) + "–" + K.hm(s.endAt) + " · " +
      K.esc(s.courseName || s.className || "课程") + "</div><div class=\"meta\">" +
      K.esc(who) + (s.teacherName && o.showTeacher ? " · " + K.esc(s.teacherName) : "") +
      (s.roomName ? " · " + K.esc(s.roomName) : "") + "</div></div>" +
      K.pill(K.sessionStatus(s.status), K.sessionKind(s.status)) + "</div>";
    if (o.actions) h += '<div class="inline-actions">' + o.actions(s) + "</div>";
    return h + "</li>";
  }
  WC.sessionCard = sessionCard;

  function todayRange() {
    var d = K.dayStart(Date.now());
    return "from=" + d + "&to=" + (d + 86400000);
  }

  /* ---------- 首页 ---------- */
  function homeAdmin() {
    var isDir = me().identity === "DIRECTOR";
    core().shell('<section class="panel"><h1 class="hello">' + greeting() + "</h1>" + identityChips() +
      '<div id="dash">' + K.loading() + "</div></section>" +
      '<section class="panel"><h2>今日课程</h2><div id="todayList">' + K.loading() + "</div></section>");
    bindSwitch();
    if (isDir) {
      K.api("/api/wc/reports/overview").then(function (o) {
        K.body("dash", '<div class="kpis">' +
          K.kpi(K.money(o.netCents), "本月净收款", { go: "reports", p: "tab=overview" }) +
          K.kpi(K.money(o.consumeRevenueCents), "本月课消收入", { go: "reports" }) +
          K.kpi(o.newStudents, "本月新增学员", { go: "students" }) +
          K.kpi((o.attendanceRatePermille / 10).toFixed(1) + "%", "出勤率", { go: "reports" }) +
          K.kpi(K.money(o.arrearsCents), "欠费合计", { go: "reports", p: "tab=arrears", warn: o.arrearsCents > 0 }) +
          K.kpi(o.renewalDue, "续费预警", { go: "reports", p: "tab=renewal", warn: o.renewalDue > 0 }) +
          "</div>" +
          '<div class="actions"><button type="button" data-go="reports">查看经营报表</button><button type="button" class="secondary" data-go="payroll">课酬统计</button></div>');
        K.bind(null, {});
      }).catch(function (e) { K.body("dash", K.error(e)); });
    } else {
      K.api("/api/wc/admin/dashboard").then(function (d) {
        K.body("dash", '<div class="kpis">' +
          K.kpi(d.todayDone + "/" + d.todaySessions, "今日课程 完成/总数", { go: "calendar" }) +
          K.kpi(d.pendingLeaves, "待审批请假", { go: "leaves", warn: d.pendingLeaves > 0 }) +
          K.kpi(d.followDue, "待跟进线索", { go: "leads", p: "due=1", warn: d.followDue > 0 }) +
          K.kpi(K.money(d.arrearsCents), "欠费合计", { go: "reports", p: "tab=arrears", warn: d.arrearsCents > 0 }) +
          K.kpi(d.renewalDue, "续费预警", { go: "reports", p: "tab=renewal", warn: d.renewalDue > 0 }) +
          K.kpi(K.money(d.todayReceivedCents), "今日收款", { go: "contracts" }) +
          "</div>" +
          (d.unsettledSessions > 0 ? '<p class="alert">有 ' + d.unsettledSessions + " 节已结束的课程尚未标记完成</p>" : "") +
          '<div class="actions"><button type="button" data-go="leadForm">＋ 新增线索</button>' +
          '<button type="button" class="secondary" data-go="sell">售卖 / 报名收款</button>' +
          '<button type="button" class="secondary" data-go="studentForm">＋ 新增学员</button>' +
          '<button type="button" class="secondary" data-go="announce">发布公告</button></div>');
        K.bind(null, {});
      }).catch(function (e) { K.body("dash", K.error(e)); });
    }
    K.api("/api/wc/schedule/range?" + todayRange()).then(function (j) {
      var items = j.items || [];
      if (!items.length) { K.body("todayList", K.empty("今天没有排课")); return; }
      K.body("todayList", '<ul class="list">' + items.map(function (s) { return sessionCard(s, { showTeacher: true }); }).join("") + "</ul>");
    }).catch(function (e) { K.body("todayList", K.error(e)); });
  }

  function homeTeacher() {
    core().shell('<section class="panel"><h1 class="hello">' + greeting() + "</h1>" + identityChips() +
      '<div id="dash">' + K.loading() + "</div></section>" +
      '<section class="panel"><h2>今天的课</h2><div id="todayList">' + K.loading() + "</div></section>");
    bindSwitch();
    K.api("/api/wc/home/teacher").then(function (h) {
      K.body("dash", '<div class="kpis">' +
        K.kpi(h.todaySessionCount, "今日课次", { go: "tWeek" }) +
        K.kpi(h.todayStudentCount, "今日学员", { go: "tStudents" }) +
        K.kpi(h.pendingFeedbackCount, "待发布反馈", { go: "feedback", warn: h.pendingFeedbackCount > 0 }) +
        "</div>");
      K.bind(null, {});
    }).catch(function (e) { K.body("dash", K.error(e)); });
    K.api("/api/wc/schedule/range?" + todayRange()).then(function (j) {
      var items = j.items || [];
      if (!items.length) { K.body("todayList", K.empty("今天没有课，好好休息")); return; }
      K.body("todayList", '<ul class="list">' + items.map(function (s) {
        return sessionCard(s, {
          actions: function (x) {
            return '<button type="button" class="mini" data-act="checkin" data-id="' + x.id + '">签到</button>' +
              '<button type="button" class="mini alt" data-act="fb" data-id="' + x.id + '">写反馈</button>';
          }
        });
      }).join("") + "</ul>");
      K.bind(null, {
        checkin: function (el) { core().state.sessionId = parseInt(el.getAttribute("data-id"), 10); core().go("teach"); },
        fb: function (el) { core().state.sessionId = parseInt(el.getAttribute("data-id"), 10); core().go("feedback"); }
      });
    }).catch(function (e) { K.body("todayList", K.error(e)); });
  }

  function pickKid(kids) {
    var st = core().state;
    var ok = kids.some(function (k) { return k.id === st.childId; });
    if (!ok) st.childId = kids.length ? kids[0].id : 0;
    return st.childId;
  }
  WC.loadKids = function () {
    return K.api("/api/wc/students").then(function (j) {
      var kids = j.items || [];
      pickKid(kids);
      core().state.cache.kids = kids;
      return kids;
    });
  };
  WC.kidSwitcher = function (kids, onChange) {
    if (kids.length < 2) return kids.length ? '<p class="kidname">' + K.esc(kids[0].name) + "</p>" : "";
    var h = '<div class="chips">';
    kids.forEach(function (k) {
      h += '<button type="button" class="chip' + (k.id === core().state.childId ? " on" : "") + '" data-kid="' + k.id + '">' + K.esc(k.name) + "</button>";
    });
    return h + "</div>";
  };
  WC.bindKids = function (onChange) {
    var chips = document.querySelectorAll("[data-kid]");
    for (var i = 0; i < chips.length; i++) {
      chips[i].onclick = function (ev) {
        core().state.childId = parseInt(ev.currentTarget.getAttribute("data-kid"), 10);
        onChange();
      };
    }
  };

  function homeParent() {
    core().shell('<section class="panel"><h1 class="hello">' + greeting() + "</h1>" + identityChips() +
      '<div id="kidBar"></div><div id="dash">' + K.loading() + "</div></section>");
    bindSwitch();
    WC.loadKids().then(function (kids) {
      if (!kids.length) { K.body("dash", K.empty("尚未关联孩子，请联系机构教务")); return; }
      K.body("kidBar", WC.kidSwitcher(kids));
      WC.bindKids(homeParent);
      var sid = core().state.childId;
      return K.api("/api/wc/parent/overview?studentId=" + sid).then(function (o) {
        var h = "";
        if (o.next) {
          var n = o.next;
          h += '<div class="card next"><div class="muted">下一节课</div><div class="big">' + K.dayLabel(n.startAt) + " " + K.hm(n.startAt) +
            "</div><div>" + K.esc(n.className || "课程") + " · " + K.esc(n.teacherName) + "</div><div class=\"muted\">" +
            K.esc(n.campusName) + " " + K.esc(n.roomName) + '</div><button type="button" class="mini" data-go="parentLeave">请假</button></div>';
        } else {
          h += '<div class="card"><div class="muted">下一节课</div><div>暂无排课，可联系教务安排</div></div>';
        }
        h += '<div class="kpis">' +
          K.kpi(K.lessons(o.remainingMilli), "剩余课时", { go: "progress", warn: !!o.renewHint }) +
          K.kpi(o.attended, "已上课次", { go: "progress" }) +
          K.kpi(o.pendingHomework, "待交作业", { go: "pHomework", warn: o.pendingHomework > 0 }) +
          K.kpi(o.pendingReviews, "待评价课程", { go: "pReview" }) +
          K.kpi(o.newFeedback, "本周新反馈", { go: "parentFb" }) +
          K.kpi(o.unreadNotes, "未读消息", { go: "notes" }) +
          "</div>";
        if (o.renewHint) h += '<p class="alert">课时即将用完，可点击「续费咨询」了解课程套餐。</p><button type="button" data-go="pRenew">续费咨询</button>';
        var fb = (o.latestFeedback || [])[0];
        if (fb) {
          h += '<div class="card"><div class="muted">最新课堂反馈 · ' + K.esc(fb.teacherName) + " · " + K.date(fb.publishedAt) + "</div><div>" +
            K.esc(fb.body || "").slice(0, 120) + '</div><button type="button" class="ghost" data-go="parentFb">查看全部</button></div>';
        }
        K.body("dash", h);
        K.bind(null, {});
      });
    }).catch(function (e) { K.body("dash", K.error(e)); });
  }

  WC.pages.home = function () {
    var id = me().identity;
    if (id === "PARENT") return homeParent();
    if (id === "TEACHER") return homeTeacher();
    return homeAdmin();
  };

  /* ---------- 工作台 Hub ---------- */
  WC.pages.work = function () {
    var id = me().identity;
    var tiles = "";
    if (id === "ADMIN") {
      tiles = K.tile("👤", "学员档案", "students") + K.tile("📞", "招生线索", "leads") + K.tile("🛒", "售卖报名", "sell") +
        K.tile("📅", "排课日历", "calendar") + K.tile("📝", "请假审批", "leaves") + K.tile("💰", "合同收款", "contracts") +
        K.tile("🏫", "课程班级", "org") + K.tile("📢", "公告", "announce") + K.tile("📊", "经营报表", "reports") +
        K.tile("⏰", "续费预警", "reports", "", "tab=renewal") + K.tile("✋", "签到", "teach") + K.tile("⭐", "课后评价", "reviews");
    } else if (id === "DIRECTOR") {
      tiles = K.tile("📊", "经营报表", "reports") + K.tile("💴", "课酬统计", "payroll") + K.tile("⭐", "课后评价", "reviews") +
        K.tile("⏰", "续费预警", "reports", "", "tab=renewal") + K.tile("🎯", "线索漏斗", "reports", "", "tab=funnel") +
        K.tile("👤", "学员档案", "students") + K.tile("📞", "招生线索", "leads") + K.tile("📅", "排课日历", "calendar") +
        K.tile("🏫", "课程班级", "org") + K.tile("📢", "公告", "announce");
    } else if (id === "TEACHER") {
      tiles = K.tile("📅", "我的课表", "tWeek") + K.tile("✋", "签到", "teach") + K.tile("💬", "课堂反馈", "feedback") +
        K.tile("📚", "布置作业", "tHomework") + K.tile("👦", "我的学员", "tStudents") + K.tile("💴", "我的课酬", "payroll") +
        K.tile("⭐", "家长评价", "reviews");
    } else {
      tiles = K.tile("📅", "课程表", "pWeek") + K.tile("🙋", "请假", "parentLeave") + K.tile("📈", "课时进度", "progress") +
        K.tile("💬", "成长反馈", "parentFb") + K.tile("📚", "作业", "pHomework") + K.tile("⭐", "课后评价", "pReview") +
        K.tile("🔄", "续费咨询", "pRenew") + K.tile("👨‍👩‍👧", "我的孩子", "kids");
    }
    core().shell('<section class="panel"><h1>工作台</h1><div class="hub">' + tiles + "</div></section>");
    K.bind(null, {});
  };

  /* ---------- 我的 ---------- */
  WC.pages.me = function () {
    var m = me();
    core().shell('<section class="panel"><h1>我的</h1><div class="card profile"><div class="avatar">' +
      K.esc((m.displayName || "?").slice(0, 1)) + '</div><div><div class="big">' + K.esc(m.displayName || "") +
      '</div><div class="muted" id="meSub">' + K.esc(labelId(m.identity)) + "</div></div></div>" + identityChips() +
      '<div class="menu">' +
      '<button type="button" class="row-btn" data-go="profile">编辑资料 <span>›</span></button>' +
      '<button type="button" class="row-btn" data-go="password">修改密码 <span>›</span></button>' +
      (m.identity === "ADMIN" ? '<button type="button" class="row-btn" data-go="settings">机构规则设置 <span>›</span></button>' : "") +
      '</div><p class="muted">环境 ' + K.esc(core().cfg.env || "") + " · 服务 " + K.esc(core().apiBase() || "同源") +
      '</p><button type="button" class="ghost block" data-act="logout">退出登录</button></section>');
    bindSwitch();
    K.bind(null, { logout: function () { if (!core().state.busy) core().logout().then(function () { core().toast("已退出"); }); } });
    K.api("/api/wc/me/profile").then(function (p) {
      K.body("meSub", K.esc(labelId(m.identity)) + " · " + K.esc(p.login) + (p.phone ? " · " + K.esc(p.phone) : ""));
    }).catch(function () { /* optional */ });
  };

  WC.pages.profile = function () {
    core().shell(K.page("编辑资料", '<div id="pf">' + K.loading() + "</div>"));
    K.bind(null, {});
    K.api("/api/wc/me/profile").then(function (p) {
      K.body("pf", K.field("姓名", "pfName", { value: p.displayName }) + K.field("手机号", "pfPhone", { value: p.phone, type: "tel" }) +
        '<p class="muted">账号：' + K.esc(p.login) + '（不可修改）</p><button type="button" data-act="save">保存</button>');
      K.bind(null, {
        save: function () {
          K.submit(function () {
            return K.api("/api/wc/me/profile", { method: "POST", body: { displayName: K.val("pfName"), phone: K.val("pfPhone") } })
              .then(function () { return core().refreshMe(); }).then(function () { core().toast("已保存"); core().back(); });
          });
        }
      });
    }).catch(function (e) { K.body("pf", K.error(e)); });
  };

  /* ---------- 消息：通知 + 公告 ---------- */
  var noteTab = "notice";
  WC.pages.notes = function () {
    core().shell('<section class="panel"><h1>消息</h1>' + K.tabs([{ k: "notice", t: "通知" }, { k: "ann", t: "公告" }], noteTab) +
      '<div id="nBody">' + K.loading() + "</div></section>");
    K.bind(null, { tab: function (k) { noteTab = k; WC.pages.notes(); } });
    if (noteTab === "notice") {
      K.api("/api/wc/notifications").then(function (j) {
        var items = j.items || [];
        if (!items.length) { K.body("nBody", K.empty("暂无通知")); return; }
        K.body("nBody", '<ul class="list">' + items.map(function (n) {
          return '<li data-nid="' + n.id + '"><div class="title">' + K.esc(n.title) + '</div><div class="meta">' + K.esc(n.body) + "</div></li>";
        }).join("") + "</ul>");
        var lis = document.querySelectorAll("[data-nid]");
        for (var i = 0; i < lis.length; i++) {
          lis[i].onclick = function (ev) {
            var id = ev.currentTarget.getAttribute("data-nid");
            core().api("/api/wc/notifications/" + id + "/read", { method: "POST", body: {} });
            ev.currentTarget.classList.add("seen");
          };
        }
      }).catch(function (e) { K.body("nBody", K.error(e)); });
    } else {
      K.api("/api/wc/announcements").then(function (j) {
        var items = (j.items || []).filter(function (a) { return a.status === "PUBLISHED"; });
        if (!items.length) { K.body("nBody", K.empty("暂无公告")); return; }
        K.body("nBody", '<ul class="list">' + items.map(function (a) {
          return '<li class="ann' + (a.read ? "" : " unread") + '" data-aid="' + a.id + '"><div class="title">' + (a.pinned ? "📌 " : "") +
            K.esc(a.title) + '</div><div class="meta">' + K.dateFull(a.createdAt) + " · " + K.esc(a.author) + '</div><div class="abody">' +
            K.esc(a.body) + "</div></li>";
        }).join("") + "</ul>");
        var lis = document.querySelectorAll("[data-aid]");
        for (var i = 0; i < lis.length; i++) {
          lis[i].onclick = function (ev) {
            var el = ev.currentTarget;
            el.classList.toggle("open");
            if (el.classList.contains("unread")) {
              el.classList.remove("unread");
              core().api("/api/wc/announcements/" + el.getAttribute("data-aid") + "/read", { method: "POST", body: {} });
            }
          };
        }
      }).catch(function (e) { K.body("nBody", K.error(e)); });
    }
  };
})();
