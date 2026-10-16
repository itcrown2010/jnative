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
    cache: {},
    params: {},
    stack: []
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
        if (opts.raw) return j;
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
    try { return new Date(ms).toLocaleString(); } catch (e) { return String(ms); }
  }

  function shell(html) {
    var nav = "";
    if (state.me) {
      var tab = navTab();
      nav = '<nav class="nav" role="navigation" aria-label="主导航">';
      nav += btnNav("home", "首页", tab === "home");
      nav += btnNav("work", "工作", tab === "work");
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
    if (p === "me" || p === "profile" || p === "password") return "me";
    return "work";
  }

  function btnNav(page, label, on) {
    return '<button type="button" data-nav="' + page + '" class="' + (on ? "on" : "") +
      '" aria-label="' + label + '" aria-current="' + (on ? "page" : "false") + '">' + label + "</button>";
  }

  function bindNav() {
    var nodes = view.querySelectorAll("[data-nav]");
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].onclick = function (ev) {
        var next = ev.currentTarget.getAttribute("data-nav");
        if (state.dirtyForm && !window.confirm("有未保存内容，确定离开？")) return;
        state.dirtyForm = false;
        state.stack = [];
        state.params = {};
        state.page = next;
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
        stateBox("error", "当前环境未开放演示登录。请使用正式账号入口（本包为触屏壳，需配置 apiBase）。") +
        '<p class="muted">环境：' + (cfg.env || "") + '</p></section>');
      return;
    }
    shell(
      '<section class="panel"><h1>欢迎回来</h1>' +
      '<p class="muted">请选择身份进入对应工作台</p>' +
      '<label>账号<select id="loginSel">' +
      '<option value="admin">教务账号</option>' +
      '<option value="teacher1">专职老师</option>' +
      '<option value="teacher_x">跨校区老师</option>' +
      '<option value="parent1">家长（两个孩子）</option>' +
      '</select></label>' +
      '<label>身份<select id="idSel">' +
      '<option value="ADMIN">教务</option><option value="TEACHER">老师</option><option value="PARENT">家长</option>' +
      '</select></label>' +
      '<button id="btnLogin" type="button">进入</button>' +
      '<p id="loginErr" class="state error"></p></section>'
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
        el.innerHTML = '<p class="muted">下一节课编号：' + (h.nextSessionId || "暂无") + "</p>" +
          '<div class="actions"><button type="button" class="secondary" id="goTeach">学员签到</button>' +
          '<button type="button" id="goFbHome">写反馈</button></div>';
        document.getElementById("goTeach").onclick = function () {
          state.sessionId = h.nextSessionId || 0;
          state.page = "teach";
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
          '<button type="button" class="secondary" id="goPLeave">请假</button></div>';
        document.getElementById("goKids").onclick = function () { state.page = "kids"; render(); };
        document.getElementById("goPLeave").onclick = function () { state.page = "parentLeave"; render(); };
      }
    }).catch(function (e) {
      var el = document.getElementById("homeBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
  }

  function fillSessions(ul, items) {
    if (!ul) return;
    ul.innerHTML = "";
    for (var i = 0; i < items.length; i++) {
      var s = items[i];
      var li = document.createElement("li");
      li.innerHTML = '<div class="title">' + fmtTime(s.startAt) + " · " + statusLabel(s.status) + "</div>" +
        '<div class="meta">校区 ' + s.campusId + " · 老师 " + s.teacherUserId + " · 教室 " + s.roomId +
        (s.studentId ? " · 学员 " + s.studentId : "") + "</div>" +
        '<div class="actions" style="margin-top:8px">' +
        '<button type="button" data-sid="' + s.id + '" class="secondary openS">详情</button>' +
        (state.me.identity === "ADMIN" && (s.status === "SCHEDULED" || s.status === "RESCHEDULED")
          ? '<button type="button" data-sid="' + s.id + '" class="cancelS">取消</button>' : "") +
        "</div>";
      ul.appendChild(li);
    }
    var opens = ul.querySelectorAll(".openS");
    for (var j = 0; j < opens.length; j++) {
      opens[j].onclick = function (ev) {
        state.sessionId = parseInt(ev.currentTarget.getAttribute("data-sid"), 10);
        state.page = state.me.identity === "PARENT" ? "kids" : "teach";
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
      var start = Date.now() + 86400000;
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
          contractId: parseInt(document.getElementById("nsContract").value, 10) || 0
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
      '<label>课次编号<input id="sid" type="number" value="' + (state.sessionId || "") + '" /></label>' +
      '<div class="actions"><button type="button" id="loadRoster">加载名单</button>' +
      '<button type="button" class="secondary" id="previewBatch">预览并签到</button></div>' +
      '<div id="roster">' + stateBox("", "请加载名单") + "</div>" +
      '<div class="preview" id="batchPrev"></div>' +
      '<button type="button" id="complete">完成课次（不自动扣课）</button>' +
      '<button type="button" class="ghost" id="retryBtn">重试加载</button></section>');
    bindIdentity();
    function load() {
      var sid = parseInt(document.getElementById("sid").value, 10) || 0;
      state.sessionId = sid;
      document.getElementById("roster").innerHTML = stateBox("", "加载中…");
      api("/api/wc/sessions/" + sid + "/roster").then(function (j) {
        if (!j.ok) {
          document.getElementById("roster").innerHTML = stateBox("error", j.message || "无权限");
          return;
        }
        var rows = j.roster || [];
        if (!rows.length) {
          document.getElementById("roster").innerHTML = stateBox("", "本课暂无学员");
          return;
        }
        var html = '<ul class="list">';
        for (var i = 0; i < rows.length; i++) {
          var r = rows[i];
          html += '<li><label><input type="checkbox" class="stu" value="' + r.studentId + '" checked /> ' +
            r.name + " · " + attLabel(r.attendance) + "</label></li>";
        }
        html += "</ul>";
        document.getElementById("roster").innerHTML = html;
      }).catch(function (e) {
        if (String(e.message) !== "AUTH") {
          document.getElementById("roster").innerHTML = stateBox("error", String(e.message || e));
        }
      });
    }
    document.getElementById("loadRoster").onclick = load;
    document.getElementById("retryBtn").onclick = load;
    if (state.sessionId) load();
    document.getElementById("previewBatch").onclick = function () {
      var boxes = view.querySelectorAll(".stu:checked");
      var ids = [];
      for (var i = 0; i < boxes.length; i++) ids.push(boxes[i].value);
      document.getElementById("batchPrev").textContent =
        "将提交 " + ids.length + " 人签到。课时包学员预计按合同快照扣除权益；重复提交不会重复扣。";
      if (!ids.length || state.busy) return;
      setBusy(true);
      var key = idem("ui-batch");
      api("/api/wc/sessions/" + state.sessionId + "/batch-checkin", {
        method: "POST",
        body: { studentIds: ids.join(","), idempotencyKey: key }
      }).then(function (j) {
        document.getElementById("batchPrev").textContent = (j.message || "") + "\n" + JSON.stringify(j.data || j, null, 2);
        toast(j.ok ? "已提交" : (j.message || "失败"));
        return api("/api/wc/sessions/" + state.sessionId + "/roster");
      }).then(function (r2) {
        if (r2 && r2.roster) {
          /* re-read truth */
          load();
        }
      }).catch(function (e) {
        if (String(e.message) !== "AUTH") toast(String(e.message || e));
      }).finally(function () { setBusy(false); });
    };
    document.getElementById("complete").onclick = function () {
      if (state.busy) return;
      setBusy(true);
      api("/api/wc/sessions/" + state.sessionId + "/complete", { method: "POST", body: {} })
        .then(function (j) { toast(j.ok ? "课次已标记完成" : (j.message || "失败")); })
        .catch(function (e) { if (String(e.message) !== "AUTH") toast(String(e.message || e)); })
        .finally(function () { setBusy(false); });
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
    shell('<section class="panel"><h1>课堂反馈</h1>' + identityBar() +
      '<label>课次<input id="fbSid" type="number" value="' + (state.sessionId || "") + '" /></label>' +
      '<label>学员<input id="fbStu" type="number" /></label>' +
      '<label>今天探索了什么<textarea id="fbE">' + (state.form.explored || "") + "</textarea></label>" +
      '<label>孩子的具体尝试<textarea id="fbA">' + (state.form.attempt || "") + "</textarea></label>" +
      '<label>下一步鼓励<textarea id="fbC">' + (state.form.encouragement || "") + "</textarea></label>" +
      '<label>作品图片（可选）<input id="fbFile" type="file" accept="image/jpeg,image/png" /></label>' +
      '<div class="actions"><button type="button" id="saveDraft">保存草稿</button>' +
      '<button type="button" class="secondary" id="pub">预览并发布</button></div>' +
      '<div class="preview" id="fbPrev"></div></section>');
    bindIdentity();
    document.getElementById("saveDraft").onclick = function () {
      state.form.explored = document.getElementById("fbE").value;
      state.form.attempt = document.getElementById("fbA").value;
      state.form.encouragement = document.getElementById("fbC").value;
      if (state.busy) return;
      setBusy(true);
      var sid = parseInt(document.getElementById("fbSid").value, 10) || 0;
      var stu = parseInt(document.getElementById("fbStu").value, 10) || 0;
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
        if (!j.ok) {
          document.getElementById("fbE").value = state.form.explored;
          document.getElementById("fbA").value = state.form.attempt;
          document.getElementById("fbC").value = state.form.encouragement;
          toast(j.message || "保存失败，已保留输入");
          return;
        }
        state.draftId = (j.data && j.data.feedbackId) || state.draftId;
        document.getElementById("fbPrev").textContent = "草稿已保存（家长尚不可见）\n" +
          state.form.explored + "\n" + state.form.attempt + "\n" + state.form.encouragement;
        toast("草稿已保存");
        var file = document.getElementById("fbFile").files[0];
        if (file && state.draftId) return uploadFile(file, sid, stu, state.draftId);
      }).catch(function (e) {
        document.getElementById("fbE").value = state.form.explored || "";
        if (String(e.message) !== "AUTH") toast(String(e.message || e));
      }).finally(function () { setBusy(false); });
    };
    document.getElementById("pub").onclick = function () {
      if (!state.draftId) { toast("请先保存草稿"); return; }
      document.getElementById("fbPrev").textContent =
        "家长将看到：\n探索：" + document.getElementById("fbE").value +
        "\n尝试：" + document.getElementById("fbA").value +
        "\n鼓励：" + document.getElementById("fbC").value;
      if (state.busy) return;
      setBusy(true);
      api("/api/wc/feedback/" + state.draftId + "/publish", { method: "POST", body: {} })
        .then(function (j) {
          toast(j.ok ? "已发布给授权家长" : (j.message || "发布失败"));
          if (j.ok) document.getElementById("fbPrev").textContent += "\n\n（已发布）";
        })
        .catch(function (e) { if (String(e.message) !== "AUTH") toast(String(e.message || e)); })
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
    shell('<section class="panel"><h1>课时与进度</h1>' + identityBar() +
      '<label>孩子<select id="childSel"></select></label>' +
      '<div id="pBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/students").then(function (j) {
      var sel = document.getElementById("childSel");
      (j.items || []).forEach(function (c) {
        var o = document.createElement("option");
        o.value = c.id; o.textContent = c.name;
        sel.appendChild(o);
      });
      if (state.childId) sel.value = String(state.childId);
      sel.onchange = function () { state.childId = parseInt(sel.value, 10); loadProgress(); };
      state.childId = parseInt(sel.value, 10) || state.childId;
      loadProgress();
    });
  }

  function loadProgress() {
    api("/api/wc/contracts?studentId=" + state.childId).then(function (j) {
      var el = document.getElementById("pBody");
      if (!j.ok) { el.innerHTML = stateBox("error", j.message || "无权限"); return; }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "暂无报名"); return; }
      var html = "";
      for (var i = 0; i < items.length; i++) {
        var c = items[i];
        html += '<div class="finance panel" style="margin:8px 0"><h2>' +
          (c.billingMode === "PACK" ? "课时包余额" : "学期进度") + "</h2>";
        if (c.billingMode === "PACK") {
          html += "<p>剩余 " + (c.remainingMilli / 1000) + " 课时</p>";
        } else {
          html += "<p>承诺 " + c.promisedSessions + " 次课</p>";
        }
        html += '<button type="button" data-cid="' + c.id + '" class="openL">展开流水</button>' +
          '<pre class="preview" id="L' + c.id + '"></pre></div>';
      }
      el.innerHTML = html;
      var bs = el.querySelectorAll(".openL");
      for (var x = 0; x < bs.length; x++) {
        bs[x].onclick = function (ev) {
          var cid = ev.currentTarget.getAttribute("data-cid");
          api("/api/wc/contracts/" + cid + "/ledger").then(function (L) {
            document.getElementById("L" + cid).textContent = JSON.stringify(L, null, 2);
          });
        };
      }
    });
  }

  function renderParentFb() {
    shell('<section class="panel"><h1>成长反馈</h1>' + identityBar() +
      '<label>孩子<select id="childSel"></select></label>' +
      '<div id="fbBody">' + stateBox("", "加载中…") + "</div></section>");
    bindIdentity();
    api("/api/wc/students").then(function (j) {
      var sel = document.getElementById("childSel");
      (j.items || []).forEach(function (c) {
        var o = document.createElement("option");
        o.value = c.id; o.textContent = c.name;
        sel.appendChild(o);
      });
      if (state.childId) sel.value = String(state.childId);
      sel.onchange = function () {
        state.childId = parseInt(sel.value, 10);
        loadFb();
      };
      state.childId = parseInt(sel.value, 10) || 0;
      loadFb();
    });
  }

  function loadFb() {
    api("/api/wc/parent/feedback?studentId=" + state.childId).then(function (j) {
      var el = document.getElementById("fbBody");
      if (!j.ok) { el.innerHTML = stateBox("error", j.message || "无权限"); return; }
      var items = j.items || [];
      if (!items.length) { el.innerHTML = stateBox("", "还没有已发布的反馈"); return; }
      var html = '<ul class="list child">';
      for (var i = 0; i < items.length; i++) {
        var f = items[i];
        html += "<li><div class=\"title\">" + (f.explored || "") + "</div><div class=\"meta\">" +
          (f.attempt || "") + "<br/>" + (f.encouragement || "") + "</div></li>";
      }
      html += "</ul>";
      el.innerHTML = html;
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

  function go(page, params) {
    if (state.dirtyForm && !window.confirm("有未保存内容，确定离开？")) return;
    state.dirtyForm = false;
    state.stack.push({ page: state.page, params: state.params });
    if (state.stack.length > 20) state.stack.shift();
    state.page = page;
    state.params = params || {};
    window.scrollTo(0, 0);
    render();
  }

  function back() {
    state.dirtyForm = false;
    var prev = state.stack.pop();
    if (prev) { state.page = prev.page; state.params = prev.params; }
    else { state.page = "work"; state.params = {}; }
    render();
  }

  function logoutNow() {
    return api("/api/wc/auth/logout", { method: "POST", body: {}, softAuth: true })
      .catch(function () { /* still clear local */ })
      .then(function () {
        nativeClearSession();
        state.me = null;
        state.stack = [];
        state.page = "home";
        clearRoleCache();
        render();
      });
  }

  var WC = window.WC = window.WC || {};
  WC.pages = WC.pages || {};
  WC.core = {
    state: state, cfg: cfg, api: api, toast: toast, shell: shell, render: render, go: go, back: back,
    refreshMe: refreshMe, labelId: labelId, idem: idem, setBusy: setBusy, clearRoleCache: clearRoleCache,
    apiBase: apiBase, stateBox: stateBox, uploadFile: uploadFile, logout: logoutNow, nativeSaveHint: nativeSaveHint,
    fillSessions: fillSessions
  };

  function render() {
    if (!state.me) {
      if (WC.pages.login) return WC.pages.login();
      renderLogin();
      return;
    }
    if (WC.pages[state.page]) {
      try { return WC.pages[state.page](); } catch (e) {
        shell('<section class="panel"><h1>页面出错</h1>' + stateBox("error", String(e && e.message || e)) +
          '<button type="button" id="errHome">回到首页</button></section>');
        var eh = document.getElementById("errHome");
        if (eh) eh.onclick = function () { state.page = "home"; state.stack = []; render(); };
        return;
      }
    }
    if (state.page === "home") return renderHome();
    if (state.page === "schedule") return renderSchedule();
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

  /* Boot: merge server client-config when same-origin */
  api("/api/wc/client-config", { softAuth: true }).then(function (c) {
    if (c && c.ok) {
      if (typeof c.allowDemoLogin === "number") cfg.allowDemoLogin = c.allowDemoLogin === 1;
      if (c.env) cfg.env = c.env;
    }
  }).catch(function () { /* offline packaging uses local config.js */ })
    .then(function () {
      return api("/api/wc/me", { softAuth: true }).then(function (me) {
        if (me && me.ok) state.me = me;
      }).catch(function () { state.me = null; });
    })
    .then(function () { render(); })
    .catch(function () { render(); });
})();
