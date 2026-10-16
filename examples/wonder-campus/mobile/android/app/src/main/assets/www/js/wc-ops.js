/* Author: 张晓达 — wonder-campus modular www (plain scripts, no bundler) */
function renderHomeAdmin() {
  shell('<div id="homeBody">' + stateBox("", "加载中…") + "</div>");
  var el = document.getElementById("homeBody");
  Promise.all([api("/api/wc/admin/today"), api("/api/wc/campuses")]).then(function (arr) {
    var h = arr[0], camps = arr[1];
    if (!h.ok) { el.innerHTML = retryBox(h.message || "加载失败", "rh"); document.getElementById("rh").onclick = render; return; }
    var campName = "本校区";
    var clist = camps.items || [];
    if (state.campusId) {
      var found = clist.filter(function (c) { return c.id === state.campusId; })[0];
      if (found) campName = found.name;
    } else if (clist[0]) campName = clist[0].name;
    var items = h.todaySessions || [];
    var pending = h.pendingLeaves || 0;
    var makeup = h.openMakeupCredits || 0;
    var html = '<p class="brand-kicker">奇想校园 · 教务工作台</p>' +
      '<div class="row"><h1>' + campName + '</h1>' +
      '<label style="margin:0;width:auto">校区<select id="campHome" style="margin:0;min-width:120px"><option value="0">全部</option>';
    clist.forEach(function (c) {
      html += '<option value="' + c.id + '"' + (state.campusId === c.id ? " selected" : "") + ">" + c.name + "</option>";
    });
    html += "</select></label></div>";
    html += '<div class="stats">' +
      '<div class="stat"><span class="n">' + items.length + '</span><span class="l">今日课程</span></div>' +
      '<div class="stat"><span class="n">' + pending + '</span><span class="l">待审批请假</span></div>' +
      '<div class="stat"><span class="n">' + makeup + '</span><span class="l">待安排补课</span></div></div>';
    if (pending || makeup) {
      html += '<div class="alert-banner">' + pending + " 项请假待审批 · " + makeup +
        ' 项补课待安排<button type="button" id="goTodos">查看待办</button></div>';
    }
    html += '<div class="quick-grid">' +
      '<button type="button" id="qaSched">新增排课</button>' +
      '<button type="button" id="qaStu">学员合同</button>' +
      '<button type="button" id="qaMk">补课安排</button>' +
      '<button type="button" id="qaPay">收款记录</button></div>' +
      '<div class="section-head"><h2>今天的课</h2><button type="button" class="link" id="goFullSched">完整课表</button></div>' +
      '<div id="adminSess"></div>';
    el.innerHTML = html;
    document.getElementById("campHome").onchange = function () {
      state.campusId = parseInt(this.value, 10) || 0;
      renderHomeAdmin();
    };
    var gt = document.getElementById("goTodos");
    if (gt) gt.onclick = function () { state.page = "leaves"; render(); };
    document.getElementById("qaSched").onclick = function () { state.page = "schedule"; render(); };
    document.getElementById("qaStu").onclick = function () { state.page = "contracts"; render(); };
    document.getElementById("qaMk").onclick = function () { state.page = "leaves"; render(); };
    document.getElementById("qaPay").onclick = function () { state.page = "contracts"; render(); };
    document.getElementById("goFullSched").onclick = function () { state.page = "schedule"; render(); };
    var box = document.getElementById("adminSess");
    var filtered = state.campusId ? items.filter(function (s) { return s.campusId === state.campusId; }) : items;
    if (!filtered.length) {
      box.innerHTML = stateBox("", "今天还没有课程") +
        '<p class="muted">可新增排课；若权限不足将无法保存。</p>';
    } else {
      box.innerHTML = '<ul class="list" id="sessList"></ul>';
      fillSessions(document.getElementById("sessList"), filtered.slice(0, 8));
    }
  }).catch(function (e) {
    if (String(e.message) !== "AUTH") {
      el.innerHTML = retryBox(String(e.message || e), "rh");
      document.getElementById("rh").onclick = render;
    }
  });
}

function renderHomeDirector() {
  shell('<div id="homeBody">' + stateBox("", "加载中…") + "</div>");
  var el = document.getElementById("homeBody");
  api("/api/wc/home/director").then(function (h) {
    if (!h.ok) { el.innerHTML = retryBox(h.message || "无权限", "rh"); document.getElementById("rh").onclick = render; return; }
    el.innerHTML = '<p class="brand-kicker">奇想校园 · 负责人概览</p><h1>运营概览</h1>' +
      '<p class="muted">更新于 ' + fmtTime(h.asOf) + " · 仅展示可追溯计数</p>" +
      '<div class="stats">' +
      '<div class="stat"><span class="n">' + (h.campusCount || 0) + '</span><span class="l">校区</span></div>' +
      '<div class="stat"><span class="n">' + (h.todaySessionCount || 0) + '</span><span class="l">今日课次</span></div>' +
      '<div class="stat"><span class="n">' + (h.pendingLeaves || 0) + '</span><span class="l">待审请假</span></div></div>' +
      '<section class="panel"><h2>需要关注</h2>' +
      '<p>待安排补课资格 <strong>' + (h.openMakeupCredits || 0) + "</strong></p>" +
      '<button type="button" class="secondary" id="dirLeaves">进入教务待办</button></section>' +
      '<div class="metric-na"><strong>金额类指标未提供</strong><br/>' +
      "实际收款 / 退款 / 课消收入接口尚未就绪，此处不填演示数字。" +
      "<br/>口径与期间确认后将接入可追溯明细。</div>";
    document.getElementById("dirLeaves").onclick = function () {
      /* 负责人可持有教务身份；此处引导切换 */
      toast("请在「我的」切换到教务身份处理审批");
      state.page = "me";
      render();
    };
  }).catch(function (e) {
    if (String(e.message) !== "AUTH") {
      el.innerHTML = retryBox(String(e.message || e), "rh");
      document.getElementById("rh").onclick = render;
    }
  });
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

function renderNotes() {
  if (state.me.identity === "DIRECTOR") {
    shell('<section class="panel"><h1>经营</h1>' +
      '<p class="muted">仅展示已接入且可追溯的运营计数；金额类指标接口未就绪。</p>' +
      '<div id="nBody">' + stateBox("", "加载中…") + "</div></section>");
    api("/api/wc/home/director").then(function (h) {
      var el = document.getElementById("nBody");
      if (!h.ok) { el.innerHTML = stateBox("error", h.message || "无权限"); return; }
      el.innerHTML = '<div class="stats">' +
        '<div class="stat"><span class="n">' + (h.campusCount || 0) + '</span><span class="l">校区</span></div>' +
        '<div class="stat"><span class="n">' + (h.todaySessionCount || 0) + '</span><span class="l">今日课次</span></div>' +
        '<div class="stat"><span class="n">' + (h.openMakeupCredits || 0) + '</span><span class="l">待补课</span></div></div>' +
        '<div class="metric-na"><strong>收款 / 退款 / 课消收入</strong><br/>接口未提供，不展示演示金额。</div>';
    }).catch(function (e) {
      var el = document.getElementById("nBody");
      if (el && String(e.message) !== "AUTH") el.innerHTML = stateBox("error", String(e.message || e));
    });
    return;
  }
  shell('<section class="panel"><h1>消息</h1>' +
    '<div id="nBody">' + stateBox("", "加载中…") + "</div></section>");
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

function renderStudents() {
  shell('<section class="panel"><h1>学员</h1><div id="stuBody">' + stateBox("", "加载中…") + "</div></section>");
  api("/api/wc/students").then(function (j) {
    var el = document.getElementById("stuBody");
    if (!j.ok) { el.innerHTML = retryBox(j.message || "无权限", "rs"); document.getElementById("rs").onclick = render; return; }
    var items = j.items || [];
    if (!items.length) { el.innerHTML = stateBox("", "暂无学员") + '<p class="muted">报名完成后会出现在此列表。</p>'; return; }
    var html = '<ul class="list">';
    items.forEach(function (s) {
      html += '<li><div class="title">' + s.name + '</div><div class="meta">校区 ' + (s.campusId || "—") +
        '</div></li>';
    });
    html += '</ul><button type="button" class="secondary" id="goContractsFromStu">查看合同</button>';
    el.innerHTML = html;
    document.getElementById("goContractsFromStu").onclick = function () { state.page = "contracts"; render(); };
  }).catch(function (e) {
    var el = document.getElementById("stuBody");
    if (el && String(e.message) !== "AUTH") {
      el.innerHTML = retryBox(String(e.message || e), "rs");
      document.getElementById("rs").onclick = render;
    }
  });
}

