/* 奇想校园触屏壳 — WebView HTML/CSS/JS，非原生 UIKit/Compose */
(function () {
  var cfg = window.WC_CONFIG || { apiBase: "", allowDemoLogin: false, env: "unknown" };
  var state = {
    me: null,
    page: "home",
    busy: false,
    dirtyForm: false,
    campusId: 0,
    childId: 0,
    sessionId: 0,
    draftId: 0,
    form: {},
    cache: {}
  };

  var view = document.getElementById("view");
  var toastEl = document.getElementById("toast");

  function apiBase() {
    var b = cfg.apiBase || "";
    if (b && b.charAt(b.length - 1) === "/") b = b.slice(0, -1);
    return b;
  }

  function toast(msg) {
    toastEl.textContent = msg;
    toastEl.classList.remove("hidden");
    setTimeout(function () { toastEl.classList.add("hidden"); }, 2600);
  }

  function api(path, opts) {
    opts = opts || {};
    return fetch(apiBase() + path, {
      method: opts.method || "GET",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: opts.body ? JSON.stringify(opts.body) : undefined
    }).then(function (r) {
      return r.text().then(function (t) {
        var j = {};
        try { j = JSON.parse(t || "{}"); } catch (e) { j = { ok: 0, message: t || "无效响应" }; }
        j._status = r.status;
        if (r.status === 401 || j.code === "AUTH_REQUIRED") {
          state.me = null;
          state.cache = {};
          if (!opts.softAuth) {
            toast("登录已失效，请重新登录");
            render();
          }
          throw new Error("AUTH");
        }
        if (r.status === 403) {
          throw new Error(j.message || "无权限");
        }
        return j;
      });
    });
  }

  function idem(p) { return p + "-" + Date.now() + "-" + Math.floor(Math.random() * 9999); }

  function setBusy(v) {
    state.busy = !!v;
    var buttons = view.querySelectorAll("button");
    for (var i = 0; i < buttons.length; i++) buttons[i].disabled = !!v;
  }

  function clearRoleCache() {
    state.cache = {};
    state.sessionId = 0;
    state.draftId = 0;
    state.form = {};
    state.dirtyForm = false;
  }

  function nativeClearSession() {
    try {
      if (window.wcNativeBridge && window.wcNativeBridge.clearSession) {
        window.wcNativeBridge.clearSession();
      }
    } catch (e) { /* ignore */ }
  }

  function nativeSaveHint(identity) {
    try {
      if (window.wcNativeBridge && window.wcNativeBridge.saveHint) {
        window.wcNativeBridge.saveHint(identity);
      }
    } catch (e) { /* ignore */ }
  }

  function fmtTime(ms) {
    if (!ms) return "—";
    try { return new Date(ms).toLocaleString("zh-CN", { hour12: false }); } catch (e) { return String(ms); }
  }

  function fmtDate(ms) {
    if (!ms) return "—";
    try { return new Date(ms).toLocaleDateString("zh-CN"); } catch (e) { return String(ms); }
  }

  function fmtLessons(milli) {
    var n = (Number(milli) || 0) / 1000;
    if (Math.abs(n - Math.round(n)) < 0.001) return String(Math.round(n));
    return n.toFixed(2);
  }

  function fmtMoney(cents) {
    return ((Number(cents) || 0) / 100).toFixed(2) + " 元";
  }

  function badge(text, kind) {
    return '<span class="badge ' + (kind || "") + '">' + text + "</span>";
  }

  function retryBox(msg, id) {
    return stateBox("error", msg) + '<button type="button" id="' + id + '" class="secondary">重试</button>';
  }

  function ledgerTypeLabel(t) {
    if (t === "CONSUME" || t === "CHECKIN") return "上课扣课";
    if (t === "REVERSE" || t === "RESTORE") return "冲正恢复";
    if (t === "GRANT" || t === "PURCHASE") return "购课入账";
    if (t === "BONUS") return "赠送课时";
    if (t === "REFUND") return "退费冲减";
    return t || "变动";
  }

  function contractStatusLabel(c) {
    if (!c) return "未知";
    if (c.status === "EXPIRED" || (c.packExpireAt && c.packExpireAt > 0 && c.packExpireAt < Date.now())) {
      return "已过期";
    }
    if (c.status === "CANCELLED" || c.status === "CLOSED") return "已关闭";
    if (c.billingMode === "PACK" && (c.remainingMilli || 0) <= 0) return "余额不足";
    if (c.status === "ACTIVE") return "有效";
    if (c.status === "DRAFT" || c.status === "UNPAID") return "未购齐/待生效";
    return c.status || "未知";
  }

  function shell(html) {
    var nav = "";
    if (state.me) {
      var tab = navTab();
      nav = '<nav class="nav" role="navigation" aria-label="主导航">';
      nav += btnNav("home", "首页", tab === "home");
      nav += btnNav("schedule", "课表", tab === "schedule");
      nav += btnNav("notes", "消息", tab === "notes");
      nav += btnNav("me", "我的", tab === "me");
      nav += "</nav>";
    }
    view.innerHTML = '<div class="page">' + html + "</div>" + nav;
    bindNav();
    focusScrollSetup();
  }

  function navTab() {
    var p = state.page;
    if (p === "home") return "home";
    if (p === "notes") return "notes";
    if (p === "me" || p === "contracts") return "me";
    if (p === "schedule" || p === "today" || p === "leaves" || p === "teach" || p === "feedback" ||
        p === "kids" || p === "parentLeave" || p === "progress" || p === "parentFb" || p === "session") {
      return "schedule";
    }
    return "home";
  }

  function btnNav(page, label, on) {
    return '<button type="button" data-nav="' + page + '" class="' + (on ? "on" : "") +
      '" aria-label="' + label + '"' + (on ? ' aria-current="page"' : "") + ">" + label + "</button>";
  }

  function bindNav() {
    var nodes = view.querySelectorAll("[data-nav]");
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].onclick = function (ev) {
        var next = ev.currentTarget.getAttribute("data-nav");
        if (state.dirtyForm && !window.confirm("有未保存内容，确定离开？")) return;
        state.dirtyForm = false;
        if (next === "schedule") {
          if (state.me.identity === "ADMIN") state.page = "schedule";
          else if (state.me.identity === "TEACHER") state.page = "today";
          else state.page = "kids";
        } else {
          state.page = next;
        }
        render();
      };
    }
  }

  function focusScrollSetup() {
    var inputs = view.querySelectorAll("input, textarea, select");
    for (var i = 0; i < inputs.length; i++) {
      inputs[i].addEventListener("focus", function (ev) {
        var el = ev.target;
        setTimeout(function () {
          try { el.scrollIntoView({ block: "center", behavior: "smooth" }); } catch (e) { el.scrollIntoView(true); }
        }, 300);
      });
      if (inputs[i].tagName === "TEXTAREA" || inputs[i].type === "text" || inputs[i].type === "number") {
        inputs[i].addEventListener("input", function () { state.dirtyForm = true; });
      }
    }
  }

  function stateBox(kind, text) {
    return '<div class="state ' + (kind || "") + '">' + text + "</div>";
  }

  function renderLogin() {
    if (!cfg.allowDemoLogin) {
      shell('<section class="panel"><h1>奇想校园</h1>' +
        stateBox("error", "正式环境未开放测试登录。请使用正式账号入口。") +
        '<p class="muted">环境：' + (cfg.env || "prod") + " · 服务 " + (apiBase() || "同源") + "</p></section>");
      return;
    }
    shell(
      '<section class="panel"><h1>欢迎回来</h1>' +
      badge("测试环境账号", "warn") +
      '<p class="muted">以下为虚构测试账号，正式配置不会出现此入口</p>' +
      '<label>测试账号<select id="loginSel" aria-label="测试账号">' +
      '<option value="admin">教务账号（admin）</option>' +
      '<option value="teacher1">专职老师（teacher1）</option>' +
      '<option value="teacher_x">跨校区老师（teacher_x）</option>' +
      '<option value="parent1">家长·两孩（parent1）</option>' +
      '</select></label>' +
      '<label>身份<select id="idSel" aria-label="身份">' +
      '<option value="ADMIN">教务</option><option value="TEACHER">老师</option><option value="PARENT">家长</option>' +
      '</select></label>' +
      '<button id="btnLogin" type="button" aria-label="进入工作台">进入工作台</button>' +
      '<p id="loginErr" class="state error" role="alert"></p></section>'
    );
    document.getElementById("btnLogin").onclick = function () {
      if (state.busy) return;
      setBusy(true);
      document.getElementById("loginErr").textContent = "";
      api("/api/wc/auth/demo", {
        method: "POST",
        body: {
          login: document.getElementById("loginSel").value,
          identity: document.getElementById("idSel").value
        }
      }).then(function (j) {
        if (!j.ok) {
          document.getElementById("loginErr").textContent = j.message || "无法进入";
          return;
        }
        clearRoleCache();
        return refreshMe().then(function () {
          nativeSaveHint(state.me && state.me.identity);
          state.page = "home";
          render();
        });
      }).catch(function (e) {
        if (String(e.message) !== "AUTH") {
          var msg = String(e.message || e);
          if (msg === "Failed to fetch" || msg.indexOf("NetworkError") >= 0) {
            msg = "无法连接服务（" + (apiBase() || "同源") + "）。请确认测试服务已启动，不会使用假数据。";
          }
          document.getElementById("loginErr").textContent = msg;
        }
      }).finally(function () { setBusy(false); });
    };
  }

  function refreshMe() {
    return api("/api/wc/me").then(function (me) {
      if (!me.ok) throw new Error(me.message || "无法读取账号");
      state.me = me;
      return me;
    });
  }

  function identityBar() {
    var ids = (state.me && state.me.identities) || [];
    var html = '<div class="chips">';
    for (var i = 0; i < ids.length; i++) {
      var on = ids[i] === state.me.identity ? " on" : "";
      html += '<button type="button" class="chip' + on + '" data-switch="' + ids[i] + '">' + labelId(ids[i]) + "</button>";
    }
    html += '<button type="button" class="ghost" id="btnOut">退出</button></div>';
    return html;
  }

  function labelId(id) {
    if (id === "ADMIN") return "教务";
    if (id === "TEACHER") return "老师";
    if (id === "PARENT") return "家长";
    return id;
  }

  function bindIdentity() {
    var chips = view.querySelectorAll("[data-switch]");
    for (var i = 0; i < chips.length; i++) {
      chips[i].onclick = function (ev) {
        var id = ev.currentTarget.getAttribute("data-switch");
        if (state.busy || id === state.me.identity) return;
        setBusy(true);
        api("/api/wc/auth/switch-identity", { method: "POST", body: { identity: id } })
          .then(function () {
            clearRoleCache();
            return refreshMe();
          })
          .then(function () {
            state.page = "home";
            render();
            toast("已切换到" + labelId(id));
          })
          .catch(function (e) { if (String(e.message) !== "AUTH") toast(String(e.message || e)); })
          .finally(function () { setBusy(false); });
      };
    }
    var out = document.getElementById("btnOut");
    if (out) out.onclick = function () {
      if (state.busy) return;
      setBusy(true);
      api("/api/wc/auth/logout", { method: "POST", body: {}, softAuth: true })
        .catch(function () { /* still clear local */ })
        .then(function () {
          nativeClearSession();
          state.me = null;
          clearRoleCache();
          render();
          toast("已退出");
        })
        .finally(function () { setBusy(false); });
    };
  }

  function renderHome() {
    shell('<section class="panel"><div class="row"><h1>' + labelId(state.me.identity) + "工作台</h1></div>" +
      identityBar() + '<div id="homeBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    var path = state.me.identity === "PARENT" ? "/api/wc/home/parent"
      : (state.me.identity === "TEACHER" ? "/api/wc/home/teacher" : "/api/wc/admin/today");
    api(path).then(function (h) {
      var el = document.getElementById("homeBody");
      if (!el) return;
      if (!h.ok) {
        el.innerHTML = stateBox("error", h.message || "加载失败");
        return;
      }
      if (state.me.identity === "ADMIN") {
        var items = h.todaySessions || [];
        var head = '<p class="muted">教务今日：待处理请假 ' + (h.pendingLeaves || 0) +
          " · 待安排补课 " + (h.openMakeupCredits || 0) + "</p>" +
          '<div class="actions"><button type="button" id="goAdminLeave">请假审批</button>' +
          '<button type="button" class="secondary" id="goAdminSched">今日课表</button></div>';
        if (!items.length) {
          el.innerHTML = head + stateBox("", "今天还没有课程");
        } else {
          el.innerHTML = head + '<ul class="list" id="sessList"></ul>';
          fillSessions(document.getElementById("sessList"), items);
        }
        var gal = document.getElementById("goAdminLeave");
        if (gal) gal.onclick = function () { state.page = "leaves"; render(); };
        var gas = document.getElementById("goAdminSched");
        if (gas) gas.onclick = function () { state.page = "schedule"; render(); };
      } else if (state.me.identity === "TEACHER") {
        el.innerHTML = '<p class="muted">下一节课：' + (h.nextSessionId ? ("#" + h.nextSessionId) : "暂无") + "</p>" +
          '<div class="actions"><button type="button" class="secondary" id="goToday">今日课程</button>' +
          '<button type="button" id="goFbHome">写反馈</button></div>';
        document.getElementById("goToday").onclick = function () {
          state.sessionId = h.nextSessionId || 0;
          state.page = "today";
          render();
        };
        document.getElementById("goFbHome").onclick = function () {
          state.sessionId = h.nextSessionId || 0;
          state.page = "feedback";
          render();
        };
      } else {
        el.innerHTML = '<p class="child muted">已关联孩子 ' + (h.linkedChildren || 0) +
          " · 未读消息 " + (h.unread || 0) + "</p>" +
          '<div class="actions"><button type="button" id="goKids">近期课表</button>' +
          '<button type="button" class="secondary" id="goProg">课时流水</button></div>' +
          '<button type="button" id="goPfb">课堂反馈</button>';
        document.getElementById("goKids").onclick = function () { state.page = "kids"; render(); };
        document.getElementById("goProg").onclick = function () { state.page = "progress"; render(); };
        document.getElementById("goPfb").onclick = function () { state.page = "parentFb"; render(); };
      }
    }).catch(function (e) {
      var el = document.getElementById("homeBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
  }

  function sessionCardHtml(s) {
    return '<div class="title">' + fmtTime(s.startAt) + " " + badge(statusLabel(s.status), "status") + "</div>" +
      '<div class="meta">课程/班级 #' + (s.classId || "—") +
      " · 校区 " + (s.campusId || "—") +
      " · 教室 " + (s.roomId || "—") +
      " · 老师 #" + (s.teacherUserId || "—") +
      (s.studentId ? (" · 学员 #" + s.studentId) : "") + "</div>";
  }

  function fillSessions(ul, items) {
    if (!ul) return;
    ul.innerHTML = "";
    for (var i = 0; i < items.length; i++) {
      var s = items[i];
      var li = document.createElement("li");
      li.innerHTML = sessionCardHtml(s) +
        '<div class="actions" style="margin-top:8px">' +
        '<button type="button" data-sid="' + s.id + '" class="secondary openS">课次详情</button>' +
        (state.me.identity === "TEACHER" || state.me.identity === "ADMIN"
          ? '<button type="button" data-sid="' + s.id + '" class="openTeach">签到</button>' : "") +
        (state.me.identity === "ADMIN" && (s.status === "SCHEDULED" || s.status === "RESCHEDULED")
          ? '<button type="button" data-sid="' + s.id + '" class="cancelS">取消</button>' : "") +
        "</div>";
      ul.appendChild(li);
    }
    var opens = ul.querySelectorAll(".openS");
    for (var j = 0; j < opens.length; j++) {
      opens[j].onclick = function (ev) {
        state.sessionId = parseInt(ev.currentTarget.getAttribute("data-sid"), 10);
        state.page = "session";
        render();
      };
    }
    var teaches = ul.querySelectorAll(".openTeach");
    for (var t = 0; t < teaches.length; t++) {
      teaches[t].onclick = function (ev) {
        state.sessionId = parseInt(ev.currentTarget.getAttribute("data-sid"), 10);
        state.page = "teach";
        render();
      };
    }
    var cancels = ul.querySelectorAll(".cancelS");
    for (var k = 0; k < cancels.length; k++) {
      cancels[k].onclick = function (ev) {
        var sid = ev.currentTarget.getAttribute("data-sid");
        if (state.busy) return;
        setBusy(true);
        api("/api/wc/sessions/" + sid + "/cancel", { method: "POST", body: { reason: "教务取消" } })
          .then(function (j) {
            toast(j.ok ? "已取消，权益将按规则冲正" : (j.message || "取消失败"));
            render();
          })
          .catch(function (e) { if (String(e.message) !== "AUTH") toast(String(e.message || e)); })
          .finally(function () { setBusy(false); });
      };
    }
  }

  function renderToday() {
    shell('<section class="panel"><h1>今日课程</h1>' + identityBar() +
      '<div id="todayBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    var el = document.getElementById("todayBody");
    api("/api/wc/sessions/today").then(function (j) {
      if (!j.ok) {
        el.innerHTML = retryBox(j.message || "加载失败", "retryToday");
        document.getElementById("retryToday").onclick = function () { render(); };
        return;
      }
      var items = j.items || [];
      if (state.me.identity === "TEACHER") {
        items = items.filter(function (s) { return s.teacherUserId === state.me.userId; });
      }
      if (!items.length) {
        el.innerHTML = stateBox("", "近期没有课程");
        return;
      }
      el.innerHTML = '<ul class="list" id="sessList"></ul>';
      fillSessions(document.getElementById("sessList"), items);
    }).catch(function (e) {
      if (String(e.message) === "AUTH") return;
      el.innerHTML = retryBox(String(e.message || e), "retryToday");
      document.getElementById("retryToday").onclick = function () { render(); };
    });
  }

  function renderSession() {
    shell('<section class="panel"><h1>课次详情</h1>' + identityBar() +
      '<div id="sessBody">' + stateBox("", "加载中…") + "</div>" +
      '<div class="actions"><button type="button" class="secondary" id="goCheckin">去签到</button>' +
      '<button type="button" id="goFbFromSess">写反馈</button></div></section>');
    bindIdentity();
    api("/api/wc/sessions/" + state.sessionId + "/roster").then(function (j) {
      var el = document.getElementById("sessBody");
      if (!j.ok) {
        el.innerHTML = retryBox(j.message || "无法读取课次", "retrySess");
        document.getElementById("retrySess").onclick = function () { render(); };
        return;
      }
      el.innerHTML = sessionCardHtml({
        startAt: j.startAt, status: j.status, classId: j.classId,
        campusId: j.campusId, roomId: j.roomId, teacherUserId: j.teacherUserId
      }) + '<p class="muted">课次 #' + j.sessionId + " · 名单 " + ((j.roster || []).length) + " 人</p>" +
        '<ul class="list">' + (j.roster || []).map(function (r) {
          return "<li><div class=\"title\">" + r.name + "</div><div class=\"meta\">" +
            attLabel(r.attendance) + (r.billingMode === "TERM" ? " · 学期制" : " · 课时包") + "</div></li>";
        }).join("") + "</ul>";
    }).catch(function (e) {
      var el = document.getElementById("sessBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
    document.getElementById("goCheckin").onclick = function () { state.page = "teach"; render(); };
    document.getElementById("goFbFromSess").onclick = function () { state.page = "feedback"; render(); };
  }

  function statusLabel(s) {
    if (s === "SCHEDULED") return "计划中";
    if (s === "RESCHEDULED") return "已改期";
    if (s === "COMPLETED") return "已完成";
    if (s === "CANCELLED_BY_SCHOOL") return "已取消";
    return s;
  }

  function renderSchedule() {
    shell('<section class="panel"><h1>今日课表</h1>' + identityBar() +
      '<label>校区<select id="campusSel"><option value="0">全部校区</option></select></label>' +
      '<div id="schBody">' + stateBox("", "加载中…") + "</div>" +
      '<h2>快速排课</h2>' +
      '<label>学员编号<input id="nsStudent" type="number" /></label>' +
      '<label>合同编号<input id="nsContract" type="number" /></label>' +
      '<label>班级编号<input id="nsClass" type="number" value="1" /></label>' +
      '<label>老师编号<input id="nsTeacher" type="number" value="2" /></label>' +
      '<label>教室编号<input id="nsRoom" type="number" value="1" /></label>' +
      '<label>校区编号<input id="nsCampus" type="number" value="1" /></label>' +
      '<button type="button" id="btnCreate">创建课程</button>' +
      '<p id="schErr" class="state error"></p></section>');
    bindIdentity();
    api("/api/wc/campuses").then(function (c) {
      var sel = document.getElementById("campusSel");
      (c.items || []).forEach(function (x) {
        var o = document.createElement("option");
        o.value = x.id; o.textContent = x.name;
        if (state.campusId === x.id) o.selected = true;
        sel.appendChild(o);
      });
      sel.onchange = function () { state.campusId = parseInt(sel.value, 10) || 0; loadToday(); };
      loadToday();
    });
    document.getElementById("btnCreate").onclick = function () {
      if (state.busy) return;
      /* 默认排到 13 小时后：满足请假截止窗口，且仍便于近期课表验收；整组改期另入口 */
      var start = Date.now() + 13 * 3600 * 1000;
      setBusy(true);
      document.getElementById("schErr").textContent = "";
      api("/api/wc/sessions", {
        method: "POST",
        body: {
          campusId: parseInt(document.getElementById("nsCampus").value, 10) || 0,
          classId: parseInt(document.getElementById("nsClass").value, 10) || 0,
          teacherUserId: parseInt(document.getElementById("nsTeacher").value, 10) || 0,
          roomId: parseInt(document.getElementById("nsRoom").value, 10) || 0,
          startAt: start,
          endAt: start + 3600000,
          studentId: parseInt(document.getElementById("nsStudent").value, 10) || 0,
          contractId: parseInt(document.getElementById("nsContract").value, 10) || 0,
          singleOccurrence: 1
        }
      }).then(function (j) {
        if (!j.ok) {
          document.getElementById("schErr").textContent = j.message || "排课失败（含冲突详情）";
          return;
        }
        toast("课程已创建");
        loadToday();
      }).catch(function (e) {
        if (String(e.message) !== "AUTH") document.getElementById("schErr").textContent = String(e.message || e);
      }).finally(function () { setBusy(false); });
    };
  }

  function loadToday() {
    var q = state.campusId ? ("?campusId=" + state.campusId) : "";
    api("/api/wc/admin/today" + q).then(function (h) {
      var el = document.getElementById("schBody");
      if (!el) return;
      var items = h.todaySessions || [];
      if (!items.length) { el.innerHTML = stateBox("", "暂无课程"); return; }
      el.innerHTML = '<ul class="list" id="sessList"></ul>';
      fillSessions(document.getElementById("sessList"), items);
    }).catch(function (e) {
      var el = document.getElementById("schBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
  }

  function renderLeaves() {
    shell('<section class="panel"><h1>请假审批</h1>' + identityBar() +
      '<div id="leaveBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/leaves/pending").then(function (j) {
      var el = document.getElementById("leaveBody");
      if (!j.ok) { el.innerHTML = stateBox("error", j.message || "无权限"); return; }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "没有待处理请假"); return; }
      var html = '<ul class="list">';
      for (var i = 0; i < items.length; i++) {
        var L = items[i];
        html += '<li><div class="title">学员 ' + L.studentId + " · 课次 " + L.sessionId + "</div>" +
          '<div class="meta">' + (L.reason || "") + "</div>" +
          '<button type="button" data-lid="' + L.id + '" class="approve">批准并安排补课</button></li>';
      }
      html += "</ul>";
      el.innerHTML = html;
      var btns = el.querySelectorAll(".approve");
      for (var b = 0; b < btns.length; b++) {
        btns[b].onclick = function (ev) {
          var lid = ev.currentTarget.getAttribute("data-lid");
          var start = Date.now() + 86400000 * 2;
          if (state.busy) return;
          setBusy(true);
          api("/api/wc/leave/" + lid + "/approve-makeup", {
            method: "POST",
            body: {
              makeupStart: start,
              makeupEnd: start + 3600000,
              teacherUserId: 2,
              roomId: 1
            }
          }).then(function (r) {
            toast(r.ok ? "已安排补课" : (r.message || "失败"));
            render();
          }).catch(function (e) { if (String(e.message) !== "AUTH") toast(String(e.message || e)); })
            .finally(function () { setBusy(false); });
        };
      }
    }).catch(function (e) {
      var el = document.getElementById("leaveBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
  }

  function renderContracts() {
    shell('<section class="panel"><h1>合同与收款</h1>' + identityBar() +
      '<div id="cBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/contracts").then(function (j) {
      var el = document.getElementById("cBody");
      if (!j.ok) { el.innerHTML = stateBox("error", j.message || "无权限"); return; }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "暂无合同"); return; }
      var html = '<ul class="list">';
      for (var i = 0; i < items.length; i++) {
        var c = items[i];
        html += '<li class="finance"><div class="title">' + (c.billingMode === "PACK" ? "课时包" : "学期制") +
          ' · 合同 ' + c.id + '</div><div class="meta">学员 ' + c.studentId +
          ' · 应收 ' + (c.receivableCents / 100).toFixed(2) + ' 元 · 实收 ' + (c.receivedCents / 100).toFixed(2) +
          ' 元</div>';
        if (c.billingMode === "PACK") {
          html += '<div class="meta">剩余课时 ' + (c.remainingMilli / 1000) + "</div>";
        } else {
          html += '<div class="meta">承诺课次 ' + c.promisedSessions + "</div>";
        }
        html += '<button type="button" data-cid="' + c.id + '" class="ledger">查看流水</button></li>';
      }
      html += '</ul><pre class="preview" id="ledOut"></pre>';
      el.innerHTML = html;
      var bs = el.querySelectorAll(".ledger");
      for (var x = 0; x < bs.length; x++) {
        bs[x].onclick = function (ev) {
          var cid = ev.currentTarget.getAttribute("data-cid");
          api("/api/wc/contracts/" + cid + "/ledger").then(function (L) {
            document.getElementById("ledOut").textContent = JSON.stringify(L, null, 2);
          });
        };
      }
    }).catch(function (e) {
      var el = document.getElementById("cBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
  }

  function renderTeach() {
    shell('<section class="panel"><h1>签到</h1>' + identityBar() +
      '<div id="sessMeta" class="muted">课次 #' + (state.sessionId || "—") + "</div>" +
      '<div id="roster">' + stateBox("", "加载中…") + "</div>" +
      '<div class="preview" id="batchPrev" role="status"></div>' +
      '<div class="actions"><button type="button" class="secondary" id="previewBatch">确认签到</button>' +
      '<button type="button" id="retryBtn">重试加载</button></div></section>');
    bindIdentity();
    var lastKey = "";
    function load() {
      if (!state.sessionId) {
        document.getElementById("roster").innerHTML = stateBox("", "请先从「今日课程」选择课次");
        return;
      }
      document.getElementById("roster").innerHTML = stateBox("", "加载中…");
      api("/api/wc/sessions/" + state.sessionId + "/roster").then(function (j) {
        if (!j.ok) {
          document.getElementById("roster").innerHTML = retryBox(j.message || "无权限", "retryBtn2");
          document.getElementById("retryBtn2").onclick = load;
          return;
        }
        document.getElementById("sessMeta").innerHTML = sessionCardHtml({
          startAt: j.startAt, status: j.status, classId: j.classId,
          campusId: j.campusId, roomId: j.roomId, teacherUserId: j.teacherUserId
        });
        var rows = j.roster || [];
        state.cache.roster = rows;
        if (!rows.length) {
          document.getElementById("roster").innerHTML = stateBox("", "本课暂无学员");
          return;
        }
        var html = '<ul class="list">';
        for (var i = 0; i < rows.length; i++) {
          var r = rows[i];
          var done = r.attendance === "PRESENT" || r.attendance === "LATE";
          html += '<li><label class="row-check"><input type="checkbox" class="stu" value="' + r.studentId + '"' +
            (done ? "" : " checked") + ' data-est="' + (r.estimateMilli || 0) + '" data-mode="' + (r.billingMode || "") + '" /> ' +
            '<span><strong>' + r.name + "</strong> " + badge(attLabel(r.attendance), done ? "ok" : "") +
            "<br/><span class=\"meta\">" + (r.billingMode === "TERM" ? "学期制·计次" : ("课时包·预计扣 " + fmtLessons(r.estimateMilli || 1000) + " 课时")) +
            (r.consumedMilli ? (" · 已扣 " + fmtLessons(r.consumedMilli)) : "") +
            "</span></span></label>" +
            (r.attendanceId ? ('<button type="button" class="ghost rev" data-aid="' + r.attendanceId +
              '" data-name="' + r.name + '">考勤纠错</button>') : "") +
            "</li>";
        }
        html += "</ul>";
        document.getElementById("roster").innerHTML = html;
        var revs = view.querySelectorAll(".rev");
        for (var x = 0; x < revs.length; x++) {
          revs[x].onclick = function (ev) {
            var aid = ev.currentTarget.getAttribute("data-aid");
            var name = ev.currentTarget.getAttribute("data-name");
            if (state.busy || !window.confirm("确认冲正「" + name + "」的签到？将恢复对应权益。")) return;
            setBusy(true);
            api("/api/wc/attendance/" + aid + "/reverse", {
              method: "POST",
              body: { idempotencyKey: idem("rev") }
            }).then(function (rj) {
              toast(rj.ok ? ("已冲正：" + name) : (rj.message || "冲正失败"));
              load();
            }).catch(function (e) {
              if (String(e.message) !== "AUTH") toast(String(e.message || e));
            }).finally(function () { setBusy(false); });
          };
        }
        updatePreview();
      }).catch(function (e) {
        if (String(e.message) !== "AUTH") {
          document.getElementById("roster").innerHTML = retryBox(String(e.message || e), "retryBtn2");
          document.getElementById("retryBtn2").onclick = load;
        }
      });
    }
    function updatePreview() {
      var boxes = view.querySelectorAll(".stu:checked");
      var n = boxes.length;
      var est = 0;
      var packN = 0;
      for (var i = 0; i < boxes.length; i++) {
        var m = parseInt(boxes[i].getAttribute("data-est"), 10) || 0;
        if (boxes[i].getAttribute("data-mode") === "PACK") { est += m || 1000; packN++; }
      }
      document.getElementById("batchPrev").textContent =
        "本次将为 " + n + " 名学员签到。" +
        (packN ? ("课时包预计合计扣 " + fmtLessons(est) + " 课时；") : "学期制按次记录；") +
        "重复提交不会重复扣课。";
    }
    document.getElementById("retryBtn").onclick = load;
    if (state.sessionId) load();
    document.getElementById("previewBatch").onclick = function () {
      var boxes = view.querySelectorAll(".stu:checked");
      var ids = [];
      for (var i = 0; i < boxes.length; i++) ids.push(boxes[i].value);
      updatePreview();
      if (!ids.length) { toast("请选择学员"); return; }
      if (!window.confirm(document.getElementById("batchPrev").textContent + "\n确认提交？")) return;
      if (state.busy) return;
      setBusy(true);
      lastKey = idem("ui-batch");
      api("/api/wc/sessions/" + state.sessionId + "/batch-checkin", {
        method: "POST",
        body: { studentIds: ids.join(","), idempotencyKey: lastKey }
      }).then(function (j) {
        var lines = [j.message || ""];
        var items = (j.data && j.data.items) || [];
        for (var k = 0; k < items.length; k++) {
          var it = items[k];
          lines.push("学员#" + it.studentId + "：" + (it.ok ? ("成功，扣 " + fmtLessons(it.consumedMilli || 0) + " 课时") : (it.message || it.code || "失败")));
        }
        document.getElementById("batchPrev").textContent = lines.join("\n");
        if (!j.ok) {
          toast(j.message || "签到失败");
          return;
        }
        toast("签到已提交，正在回读结果");
        return load();
      }).catch(function (e) {
        if (String(e.message) !== "AUTH") {
          toast("网络或服务异常，未确认保存成功：" + String(e.message || e));
          document.getElementById("batchPrev").textContent = "提交失败，请检查网络后重试（不会显示假成功）。";
        }
      }).finally(function () { setBusy(false); });
    };
  }

  function attLabel(a) {
    if (a === "NOT_RECORDED") return "未登记";
    if (a === "PRESENT") return "出勤";
    if (a === "LATE") return "迟到";
    if (a === "LEAVE") return "请假";
    if (a === "ABSENT") return "缺勤";
    return a;
  }

  function renderFeedback() {
    shell('<section class="panel"><h1>课堂反馈（老师）</h1>' + identityBar() +
      '<p class="muted">先稳定文字闭环；图片按现有上传能力可选</p>' +
      '<label>课次编号<input id="fbSid" type="number" inputmode="numeric" value="' + (state.sessionId || "") + '" /></label>' +
      '<label>学员编号<input id="fbStu" type="number" inputmode="numeric" value="' + (state.form.studentId || "") + '" /></label>' +
      '<label>今天探索了什么<textarea id="fbE">' + (state.form.explored || "") + "</textarea></label>" +
      '<label>孩子的具体尝试<textarea id="fbA">' + (state.form.attempt || "") + "</textarea></label>" +
      '<label>下一步鼓励<textarea id="fbC">' + (state.form.encouragement || "") + "</textarea></label>" +
      '<label>作品图片（可选）<input id="fbFile" type="file" accept="image/jpeg,image/png" /></label>' +
      '<div class="actions"><button type="button" id="saveDraft">保存草稿</button>' +
      '<button type="button" class="secondary" id="pub">预览并发布</button></div>' +
      '<div class="preview" id="fbPrev" role="status"></div></section>');
    bindIdentity();
    if (state.sessionId && !state.form.studentId) {
      api("/api/wc/sessions/" + state.sessionId + "/roster").then(function (j) {
        var r = (j.roster || [])[0];
        if (r && document.getElementById("fbStu") && !document.getElementById("fbStu").value) {
          document.getElementById("fbStu").value = String(r.studentId);
        }
      }).catch(function () {});
    }
    document.getElementById("saveDraft").onclick = function () {
      state.form.explored = document.getElementById("fbE").value;
      state.form.attempt = document.getElementById("fbA").value;
      state.form.encouragement = document.getElementById("fbC").value;
      state.form.studentId = parseInt(document.getElementById("fbStu").value, 10) || 0;
      if (state.busy) return;
      setBusy(true);
      var sid = parseInt(document.getElementById("fbSid").value, 10) || 0;
      var stu = state.form.studentId;
      state.sessionId = sid;
      api("/api/wc/feedback/draft", {
        method: "POST",
        body: {
          sessionId: sid,
          studentId: stu,
          explored: state.form.explored,
          attempt: state.form.attempt,
          encouragement: state.form.encouragement,
          tags: "",
          idempotencyKey: idem("draft")
        }
      }).then(function (j) {
        document.getElementById("fbE").value = state.form.explored;
        document.getElementById("fbA").value = state.form.attempt;
        document.getElementById("fbC").value = state.form.encouragement;
        if (!j.ok) {
          toast(j.message || "保存失败，已保留输入");
          return;
        }
        state.draftId = (j.data && j.data.feedbackId) || state.draftId;
        state.dirtyForm = false;
        document.getElementById("fbPrev").textContent = "草稿已保存（家长不可见）\n探索：" +
          state.form.explored + "\n尝试：" + state.form.attempt + "\n鼓励：" + state.form.encouragement;
        toast("草稿已保存");
        var file = document.getElementById("fbFile").files[0];
        if (file && state.draftId) return uploadFile(file, sid, stu, state.draftId);
      }).catch(function (e) {
        document.getElementById("fbE").value = state.form.explored || "";
        document.getElementById("fbA").value = state.form.attempt || "";
        document.getElementById("fbC").value = state.form.encouragement || "";
        if (String(e.message) !== "AUTH") {
          toast("网络异常，未确认保存成功：" + String(e.message || e));
        }
      }).finally(function () { setBusy(false); });
    };
    document.getElementById("pub").onclick = function () {
      if (!state.draftId) { toast("请先保存草稿"); return; }
      var preview = "家长将看到：\n探索：" + document.getElementById("fbE").value +
        "\n尝试：" + document.getElementById("fbA").value +
        "\n鼓励：" + document.getElementById("fbC").value;
      document.getElementById("fbPrev").textContent = preview;
      if (!window.confirm(preview + "\n\n确认发布？重复发布不会重复通知。")) return;
      if (state.busy) return;
      setBusy(true);
      api("/api/wc/feedback/" + state.draftId + "/publish", { method: "POST", body: {} })
        .then(function (j) {
          toast(j.ok ? "已发布给授权家长" : (j.message || "发布失败"));
          if (j.ok) {
            state.dirtyForm = false;
            document.getElementById("fbPrev").textContent = preview + "\n\n（已发布）";
          }
        })
        .catch(function (e) {
          if (String(e.message) !== "AUTH") toast("发布失败（未确认成功）：" + String(e.message || e));
        })
        .finally(function () { setBusy(false); });
    };
  }

  function uploadFile(file, sessionId, studentId, feedbackId) {
    return new Promise(function (resolve, reject) {
      if (file.size > 2097152) {
        toast("图片超过 2MB");
        resolve();
        return;
      }
      var reader = new FileReader();
      reader.onload = function () {
        var dataUrl = reader.result;
        api("/api/wc/media/upload", {
          method: "POST",
          body: {
            sessionId: sessionId,
            studentId: studentId,
            feedbackId: feedbackId,
            mime: file.type,
            dataBase64: dataUrl,
            idempotencyKey: idem("media")
          }
        }).then(function (j) {
          toast(j.ok ? "作品已上传" : (j.message || "上传失败，可重试"));
          resolve(j);
        }).catch(reject);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  function renderKids() {
    shell('<section class="panel"><h1>孩子与课程</h1>' + identityBar() +
      '<label>孩子<select id="childSel"></select></label>' +
      '<div id="kidBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/students").then(function (j) {
      if (!j.ok) {
        document.getElementById("kidBody").innerHTML = stateBox("error", j.message || "无权限");
        return;
      }
      var sel = document.getElementById("childSel");
      var items = j.items || [];
      if (!items.length) {
        document.getElementById("kidBody").innerHTML = stateBox("", "尚未关联孩子");
        return;
      }
      items.forEach(function (c) {
        var o = document.createElement("option");
        o.value = c.id; o.textContent = c.name;
        if (state.childId === c.id) o.selected = true;
        sel.appendChild(o);
      });
      if (!state.childId) state.childId = items[0].id;
      sel.value = String(state.childId);
      sel.onchange = function () { state.childId = parseInt(sel.value, 10); loadKidSessions(); };
      loadKidSessions();
    });
  }

  function loadKidSessions() {
    api("/api/wc/sessions/today").then(function (j) {
      var el = document.getElementById("kidBody");
      var mine = (j.items || []).filter(function (s) { return s.studentId === state.childId; });
      if (!mine.length) { el.innerHTML = stateBox("", "近期没有该孩子的课程"); return; }
      el.innerHTML = '<ul class="list" id="sessList"></ul>';
      fillSessions(document.getElementById("sessList"), mine);
    }).catch(function (e) {
      var el = document.getElementById("kidBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
  }

  function renderParentLeave() {
    shell('<section class="panel"><h1>请假</h1>' + identityBar() +
      '<label>孩子<select id="childSel"></select></label>' +
      '<label>原因<textarea id="reason">' + (state.form.reason || "") + "</textarea></label>" +
      '<button type="button" id="doLeave">提交请假</button>' +
      '<div id="mk">' + stateBox("", "") + "</div></section>");
    bindIdentity();
    api("/api/wc/students").then(function (j) {
      var sel = document.getElementById("childSel");
      (j.items || []).forEach(function (c) {
        var o = document.createElement("option");
        o.value = c.id; o.textContent = c.name;
        sel.appendChild(o);
      });
      if (state.childId) sel.value = String(state.childId);
    });
    document.getElementById("doLeave").onclick = function () {
      state.form.reason = document.getElementById("reason").value;
      state.childId = parseInt(document.getElementById("childSel").value, 10) || 0;
      if (state.busy) return;
      setBusy(true);
      api("/api/wc/sessions/today").then(function (j) {
        var mine = (j.items || []).filter(function (s) {
          return s.studentId === state.childId && (s.status === "SCHEDULED" || s.status === "RESCHEDULED");
        });
        if (!mine.length) {
          toast("当前没有可请假的课程");
          document.getElementById("reason").value = state.form.reason;
          return null;
        }
        return api("/api/wc/leave", {
          method: "POST",
          body: {
            sessionId: mine[0].id,
            studentId: state.childId,
            reason: state.form.reason,
            idempotencyKey: idem("leave")
          }
        });
      }).then(function (r) {
        if (!r) return;
        if (!r.ok) {
          document.getElementById("reason").value = state.form.reason;
          toast(r.message || "提交失败，已保留内容");
          return;
        }
        toast("请假已提交");
        return api("/api/wc/students/" + state.childId + "/makeup");
      }).then(function (m) {
        if (m) document.getElementById("mk").innerHTML = "<pre class=\"preview\">" + JSON.stringify(m, null, 2) + "</pre>";
      }).catch(function (e) {
        document.getElementById("reason").value = state.form.reason || "";
        if (String(e.message) !== "AUTH") toast(String(e.message || e));
      }).finally(function () { setBusy(false); });
    };
  }

  function renderProgress() {
    shell('<section class="panel"><h1>课时流水</h1>' + identityBar() +
      '<label>孩子<select id="childSel" aria-label="选择孩子"></select></label>' +
      '<div id="pBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/students").then(function (j) {
      var sel = document.getElementById("childSel");
      sel.innerHTML = "";
      (j.items || []).forEach(function (c) {
        var o = document.createElement("option");
        o.value = c.id; o.textContent = c.name;
        sel.appendChild(o);
      });
      if (state.childId) sel.value = String(state.childId);
      sel.onchange = function () {
        state.childId = parseInt(sel.value, 10);
        document.getElementById("pBody").innerHTML = stateBox("", "加载中…");
        loadProgress();
      };
      state.childId = parseInt(sel.value, 10) || state.childId;
      loadProgress();
    });
  }

  function loadProgress() {
    var el = document.getElementById("pBody");
    if (!state.childId) { el.innerHTML = stateBox("", "请选择孩子"); return; }
    api("/api/wc/contracts?studentId=" + state.childId).then(function (j) {
      if (!j.ok) { el.innerHTML = retryBox(j.message || "无权限", "retryP"); document.getElementById("retryP").onclick = loadProgress; return; }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "尚未购买课程（无合同）"); return; }
      var packs = items.filter(function (c) { return c.billingMode === "PACK"; });
      var terms = items.filter(function (c) { return c.billingMode === "TERM"; });
      var html = "";
      if (packs.length) {
        html += "<h2>课时包</h2>";
        packs.forEach(function (c) {
          var st = contractStatusLabel(c);
          html += '<div class="finance" data-cid="' + c.id + '"><div class="row"><strong>合同 #' + c.id +
            "</strong>" + badge(st, st === "有效" ? "ok" : "warn") + "</div>" +
            "<p>剩余 <strong>" + fmtLessons(c.remainingMilli) + "</strong> 课时" +
            (c.packExpireAt ? (" · 到期 " + fmtDate(c.packExpireAt)) : "") + "</p>" +
            "<p class=\"muted\">实收 " + fmtMoney(c.receivedCents) + "</p>" +
            '<button type="button" class="openL secondary" data-cid="' + c.id + '">查看流水</button>' +
            '<div class="ledger-list" id="L' + c.id + '"></div></div>';
        });
      }
      if (terms.length) {
        html += "<h2>学期制</h2>";
        terms.forEach(function (c) {
          var st = contractStatusLabel(c);
          html += '<div class="finance" data-cid="' + c.id + '"><div class="row"><strong>合同 #' + c.id +
            "</strong>" + badge(st, st === "有效" ? "ok" : "warn") + "</div>" +
            "<p>承诺 <strong>" + (c.promisedSessions || 0) + "</strong> 次课（按次考勤，不与课时包余额混算）</p>" +
            "<p class=\"muted\">实收 " + fmtMoney(c.receivedCents) + "</p>" +
            '<button type="button" class="openL secondary" data-cid="' + c.id + '">查看流水</button>' +
            '<div class="ledger-list" id="L' + c.id + '"></div></div>';
        });
      }
      el.innerHTML = html || stateBox("", "暂无合同");
      var bs = el.querySelectorAll(".openL");
      for (var x = 0; x < bs.length; x++) {
        bs[x].onclick = function (ev) {
          var cid = ev.currentTarget.getAttribute("data-cid");
          var box = document.getElementById("L" + cid);
          box.innerHTML = stateBox("", "加载流水…");
          api("/api/wc/contracts/" + cid + "/ledger").then(function (L) {
            if (!L.ok) { box.innerHTML = stateBox("error", L.message || "无法读取"); return; }
            var rows = L.items || [];
            if (!rows.length) { box.innerHTML = stateBox("", "还没有流水"); return; }
            var h = '<ul class="list">';
            for (var i = 0; i < rows.length; i++) {
              var e = rows[i];
              var delta = Number(e.milliDelta) || 0;
              var why = ledgerTypeLabel(e.type);
              h += "<li><div class=\"title\">" + why + " · " + (delta >= 0 ? "+" : "") + fmtLessons(delta) +
                " 课时</div><div class=\"meta\">课次 #" + (e.sessionId || "—") + " · " + fmtTime(e.createdAt) +
                (e.note ? (" · " + e.note) : "") + "</div></li>";
            }
            h += "</ul><p class=\"muted\">当前剩余 " + fmtLessons(L.remainingMilli) + " 课时（课时包口径）</p>";
            box.innerHTML = h;
          }).catch(function (err) {
            if (String(err.message) !== "AUTH") box.innerHTML = stateBox("error", String(err.message || err));
          });
        };
      }
    }).catch(function (e) {
      if (String(e.message) !== "AUTH") {
        el.innerHTML = retryBox(String(e.message || e), "retryP");
        document.getElementById("retryP").onclick = loadProgress;
      }
    });
  }

  function renderParentFb() {
    shell('<section class="panel"><h1>课堂反馈</h1>' + identityBar() +
      '<label>孩子<select id="childSel" aria-label="选择孩子"></select></label>' +
      '<div id="fbBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/students").then(function (j) {
      var sel = document.getElementById("childSel");
      sel.innerHTML = "";
      (j.items || []).forEach(function (c) {
        var o = document.createElement("option");
        o.value = c.id; o.textContent = c.name;
        sel.appendChild(o);
      });
      if (state.childId) sel.value = String(state.childId);
      sel.onchange = function () {
        state.childId = parseInt(sel.value, 10);
        document.getElementById("fbBody").innerHTML = stateBox("", "加载中…");
        loadFb();
      };
      state.childId = parseInt(sel.value, 10) || 0;
      loadFb();
    });
  }

  function loadFb() {
    var el = document.getElementById("fbBody");
    if (!state.childId) { el.innerHTML = stateBox("", "请选择孩子"); return; }
    var reqChild = state.childId;
    api("/api/wc/parent/feedback?studentId=" + reqChild).then(function (j) {
      if (reqChild !== state.childId) return;
      if (!j.ok) { el.innerHTML = retryBox(j.message || "无权限", "retryFb"); document.getElementById("retryFb").onclick = loadFb; return; }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "还没有已发布的反馈"); return; }
      var html = '<ul class="list child">';
      for (var i = 0; i < items.length; i++) {
        var f = items[i];
        html += "<li><div class=\"title\">上课 " + fmtDate(f.sessionStartAt) + " · 老师 #" + (f.teacherUserId || "—") +
          "</div><div class=\"meta\">课次 #" + f.sessionId + " · 校区 " + (f.campusId || "—") +
          " · 发布 " + fmtTime(f.publishedAt) + "</div>" +
          "<p><strong>探索</strong> " + (f.explored || "") + "</p>" +
          "<p><strong>尝试</strong> " + (f.attempt || "") + "</p>" +
          "<p><strong>鼓励</strong> " + (f.encouragement || "") + "</p></li>";
      }
      html += "</ul>";
      el.innerHTML = html;
    }).catch(function (e) {
      if (reqChild !== state.childId) return;
      if (String(e.message) !== "AUTH") {
        el.innerHTML = retryBox(String(e.message || e), "retryFb");
        document.getElementById("retryFb").onclick = loadFb;
      }
    });
  }

  function renderNotes() {
    shell('<section class="panel"><h1>消息</h1>' + identityBar() +
      '<div id="nBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/notifications").then(function (j) {
      var el = document.getElementById("nBody");
      if (!j.ok) { el.innerHTML = stateBox("error", j.message || "无权限") +
        '<button type="button" id="retryN">重试</button>'; 
        var r = document.getElementById("retryN");
        if (r) r.onclick = function () { render(); };
        return;
      }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "暂无消息"); return; }
      var html = '<ul class="list">';
      for (var i = 0; i < items.length; i++) {
        var n = items[i];
        html += '<li><div class="title">' + n.title + '</div><div class="meta">' + n.body +
          '</div><button type="button" data-nid="' + n.id + '" class="ghost mark" aria-label="标为已读">标为已读</button></li>';
      }
      html += "</ul>";
      el.innerHTML = html;
      var ms = el.querySelectorAll(".mark");
      for (var m = 0; m < ms.length; m++) {
        ms[m].onclick = function (ev) {
          var nid = ev.currentTarget.getAttribute("data-nid");
          api("/api/wc/notifications/" + nid + "/read", { method: "POST", body: {} }).then(function () {
            toast("已读");
            render();
          });
        };
      }
    }).catch(function (e) {
      var el = document.getElementById("nBody");
      if (el && String(e.message) !== "AUTH") {
        el.innerHTML = stateBox("error", String(e.message || e)) +
          '<button type="button" id="retryN">重试</button>';
        document.getElementById("retryN").onclick = function () { render(); };
      }
    });
  }

  function renderMe() {
    var id = state.me.identity;
    var extra = "";
    if (id === "ADMIN") {
      extra = '<button type="button" id="goLeaves">请假审批 / 补课</button>' +
        '<button type="button" class="secondary" id="goContracts">合同与流水</button>';
    } else if (id === "TEACHER") {
      extra = '<button type="button" id="goFb">反馈草稿与发布</button>' +
        '<p class="muted">老师工作台强调快速记录；签到在「课表」。</p>';
    } else {
      extra = '<button type="button" id="goLeave">为孩子请假</button>' +
        '<button type="button" class="secondary" id="goProg">课时 / 学期进度</button>' +
        '<button type="button" class="secondary" id="goPfb">课堂反馈</button>' +
        '<p class="muted">家长端强调成长与清楚的课时账，不展示公开成绩排名。</p>';
    }
    shell('<section class="panel"><h1>我的</h1>' + identityBar() +
      '<p class="muted">环境 ' + (cfg.env || "") + ' · 触屏壳（非原生 SwiftUI）</p>' +
      '<p class="muted">服务 ' + (apiBase() || "同源") + "</p>" +
      extra +
      '<button type="button" class="ghost" id="btnOut2" aria-label="退出登录">退出登录</button></section>');
    bindIdentity();
    var map = {
      goLeaves: "leaves", goContracts: "contracts", goFb: "feedback",
      goLeave: "parentLeave", goProg: "progress", goPfb: "parentFb"
    };
    Object.keys(map).forEach(function (k) {
      var el = document.getElementById(k);
      if (el) el.onclick = function () { state.page = map[k]; render(); };
    });
    var out2 = document.getElementById("btnOut2");
    if (out2) out2.onclick = function () {
      var btn = document.getElementById("btnOut");
      if (btn) btn.click();
      else {
        api("/api/wc/auth/logout", { method: "POST", body: {}, softAuth: true })
          .catch(function () {})
          .then(function () {
            nativeClearSession();
            state.me = null;
            clearRoleCache();
            render();
          });
      }
    };
  }

  function render() {
    if (!state.me) {
      renderLogin();
      return;
    }
    if (state.page === "home") return renderHome();
    if (state.page === "schedule") return renderSchedule();
    if (state.page === "today") return renderToday();
    if (state.page === "session") return renderSession();
    if (state.page === "leaves") return renderLeaves();
    if (state.page === "contracts") return renderContracts();
    if (state.page === "teach") return renderTeach();
    if (state.page === "feedback") return renderFeedback();
    if (state.page === "kids") return renderKids();
    if (state.page === "parentLeave") return renderParentLeave();
    if (state.page === "progress") return renderProgress();
    if (state.page === "parentFb") return renderParentFb();
    if (state.page === "notes") return renderNotes();
    if (state.page === "me") return renderMe();
    renderHome();
  }

  function queryParam(name) {
    try {
      var q = new URLSearchParams(window.location.search || "");
      return q.get(name) || "";
    } catch (e) { return ""; }
  }

  function tryAcceptLogin() {
    if (!cfg.allowDemoLogin) return Promise.resolve();
    var login = queryParam("wc_accept");
    var identity = queryParam("wc_identity") || "ADMIN";
    if (!login) return Promise.resolve();
    return api("/api/wc/auth/demo", {
      method: "POST",
      body: { login: login, identity: identity }
    }).then(function (j) {
      if (!j.ok) throw new Error(j.message || "验收登录失败");
      clearRoleCache();
      return refreshMe().then(function () {
        nativeSaveHint(state.me && state.me.identity);
        state.page = "home";
      });
    });
  }

  /* Boot: paint login immediately, then merge session/config (avoid blank first frame). */
  render();
  api("/api/wc/client-config", { softAuth: true }).then(function (c) {
    if (c && c.ok) {
      if (typeof c.allowDemoLogin === "number") cfg.allowDemoLogin = c.allowDemoLogin === 1;
      if (c.env) cfg.env = c.env;
    }
  }).catch(function () { /* offline packaging uses local config.js */ })
    .then(function () {
      return api("/api/wc/me", { softAuth: true }).then(function (me) {
        if (me && me.ok) state.me = me;
      }).catch(function () { /* stay logged out */ });
    })
    .then(function () { return tryAcceptLogin(); })
    .then(function () { render(); })
    .catch(function () { render(); });
})();
