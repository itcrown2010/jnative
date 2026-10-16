/* Author: 张晓达 — wonder-campus modular www (plain scripts, no bundler) */
function shell(html) {
  var appEl = document.getElementById("app");
  if (appEl) {
    if (state.me) appEl.classList.add("logged-in");
    else appEl.classList.remove("logged-in");
  }
  var nav = "";
  if (state.me) {
    var items = navItems();
    var tab = navTab();
    nav = '<nav class="nav" role="navigation" aria-label="主导航">';
    for (var i = 0; i < items.length; i++) {
      nav += btnNav(items[i][0], items[i][1], tab === items[i][0]);
    }
    nav += "</nav>";
  }
  view.innerHTML = '<div class="page">' + html + "</div>" + nav;
  bindNav();
  focusScrollSetup();
}

function navItems() {
  var id = state.me.identity;
  if (id === "TEACHER") {
    return [["home", "今天"], ["schedule", "课表"], ["feedback", "反馈"], ["me", "我的"]];
  }
  if (id === "ADMIN") {
    return [["home", "工作台"], ["schedule", "课表"], ["students", "学员"], ["me", "我的"]];
  }
  if (id === "DIRECTOR") {
    return [["home", "概览"], ["schedule", "校区"], ["notes", "经营"], ["me", "我的"]];
  }
  return [["home", "首页"], ["schedule", "课表"], ["notes", "消息"], ["me", "我的"]];
}

function navTab() {
  var p = state.page;
  if (p === "home") return "home";
  if (p === "notes") return "notes";
  if (p === "me" || p === "contracts") return "me";
  if (p === "feedback") return state.me.identity === "TEACHER" ? "feedback" : "schedule";
  if (p === "students") return "students";
  if (p === "schedule" || p === "today" || p === "leaves" || p === "teach" ||
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
        if (state.me.identity === "ADMIN" || state.me.identity === "DIRECTOR") state.page = "schedule";
        else if (state.me.identity === "TEACHER") state.page = "today";
        else state.page = "kids";
      } else if (next === "students") {
        state.page = "students";
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

function renderLogin() {
  if (!cfg.allowDemoLogin) {
    shell('<section class="panel"><h1>欢迎回来</h1>' +
      stateBox("error", "正式环境未开放测试登录。请使用正式账号入口。") +
      '<p class="muted">环境：' + (cfg.env || "prod") + "</p></section>");
    return;
  }
  shell(
    '<section class="panel"><h1>欢迎回来</h1>' +
    badge("测试环境", "warn") +
    '<p class="muted">虚构测试账号；正式构建不会出现此入口</p>' +
    '<label>账号<select id="loginSel" aria-label="测试账号">' +
    '<option value="admin">教务/负责人（admin）</option>' +
    '<option value="teacher1">专职老师（teacher1）</option>' +
    '<option value="teacher_x">跨校区老师（teacher_x）</option>' +
    '<option value="parent1">家长·两孩（parent1）</option>' +
    '</select></label>' +
    '<label>身份<select id="idSel" aria-label="身份">' +
    '<option value="ADMIN">教务</option>' +
    '<option value="DIRECTOR">负责人</option>' +
    '<option value="TEACHER">老师</option>' +
    '<option value="PARENT">家长</option>' +
    '</select></label>' +
    '<button id="btnLogin" type="button" aria-label="进入">进入</button>' +
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

function identityBar() { return ""; }

function labelId(id) {
  if (id === "ADMIN") return "教务";
  if (id === "TEACHER") return "老师";
  if (id === "PARENT") return "家长";
  if (id === "DIRECTOR") return "负责人";
  return id;
}

function weekdayLabel() {
  try {
    return new Date().toLocaleDateString("zh-CN", { weekday: "long", month: "long", day: "numeric" });
  } catch (e) { return fmtDate(Date.now()); }
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

function sessionCardHtml(s) {
  return '<div class="title">' + sessionTheme(s) + " " + badge(statusLabel(s.status), "status") + "</div>" +
    '<div class="meta">' + fmtDate(s.startAt) + " " + fmtClock(s.startAt) +
    (s.endAt ? ("–" + fmtClock(s.endAt)) : "") +
    "<br/>" + sessionPlaceLine(s) +
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

function renderHome() {
  if (state.me.identity === "PARENT") return renderHomeParent();
  if (state.me.identity === "TEACHER") return renderHomeTeacher();
  if (state.me.identity === "DIRECTOR") return renderHomeDirector();
  return renderHomeAdmin();
}

function renderMe() {
  var ids = state.me.identities || [];
  var chips = '<p class="muted">当前身份：' + labelId(state.me.identity) + "</p><div class=\"chips\">";
  for (var i = 0; i < ids.length; i++) {
    chips += '<button type="button" class="chip' + (ids[i] === state.me.identity ? " on" : "") +
      '" data-switch="' + ids[i] + '">' + labelId(ids[i]) + "</button>";
  }
  chips += "</div>";
  if (!ids.length) chips += stateBox("", "未授权任何业务身份");
  var extra = "";
  if (state.me.identity === "ADMIN") {
    extra = '<button type="button" id="goLeaves">请假审批 / 补课</button>' +
      '<button type="button" class="secondary" id="goContracts">合同与收款</button>';
  } else if (state.me.identity === "TEACHER") {
    extra = '<button type="button" id="goFb">反馈草稿与发布</button>';
  } else if (state.me.identity === "PARENT") {
    extra = '<button type="button" id="goLeave">为孩子请假</button>' +
      '<button type="button" class="secondary" id="goProg">课程权益</button>' +
      '<button type="button" class="secondary" id="goPfb">成长档案</button>';
  } else if (state.me.identity === "DIRECTOR") {
    extra = '<p class="muted">经营金额指标待接口就绪后展示；请勿使用演示数字。</p>';
  }
  shell('<section class="panel"><h1>我的</h1>' +
    '<h2>身份与授权</h2>' + chips +
    '<p class="muted">仅显示本账号已授权身份；切换后清除上一身份缓存。</p>' +
    extra +
    '<button type="button" id="btnOut" aria-label="退出登录">退出登录</button></section>');
  bindIdentity();
  var map = {
    goLeaves: "leaves", goContracts: "contracts", goFb: "feedback",
    goLeave: "parentLeave", goProg: "progress", goPfb: "parentFb"
  };
  Object.keys(map).forEach(function (k) {
    var el = document.getElementById(k);
    if (el) el.onclick = function () { state.page = map[k]; render(); };
  });
}

