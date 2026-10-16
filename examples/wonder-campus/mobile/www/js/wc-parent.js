/* Author: 张晓达 — wonder-campus modular www (plain scripts, no bundler) */
function renderHomeParent() {
  shell('<div id="homeBody">' + stateBox("", "加载中…") + "</div>");
  var el = document.getElementById("homeBody");
  var seq = ++state.loadSeq;
  Promise.all([
    api("/api/wc/students"),
    api("/api/wc/sessions/today")
  ]).then(function (arr) {
    if (seq !== state.loadSeq || state.page !== "home") return;
    var kids = arr[0], sess = arr[1];
    if (!kids.ok) {
      el.innerHTML = retryBox(kids.message || "无法加载孩子列表", "rh");
      document.getElementById("rh").onclick = render;
      return;
    }
    if (!sess.ok) {
      el.innerHTML = retryBox(sess.message || "无法加载课表", "rh");
      document.getElementById("rh").onclick = render;
      return;
    }
    var items = kids.items || [];
    if (!items.length) {
      el.innerHTML = '<h1>孩子的校园</h1>' +
        stateBox("", "尚未关联孩子") +
        '<p class="muted">请联系教务完成监护授权后再查看课表与成长记录。</p>';
      return;
    }
    if (!state.childId || !items.some(function (c) { return c.id === state.childId; })) {
      state.childId = items[0].id;
    }
    var child = items.filter(function (c) { return c.id === state.childId; })[0] || items[0];
    state.childId = child.id;
    var mine = (sess.items || []).filter(function (s) {
      return s.studentId === state.childId && (s.status === "SCHEDULED" || s.status === "RESCHEDULED");
    }).sort(function (a, b) { return a.startAt - b.startAt; });
    var next = mine[0];
    var html = '<div class="row"><div><h1 class="child-title">' +
      escapeHtml(child.name) + "的校园</h1></div>" +
      '<label class="child-picker">孩子<select id="childHome" aria-label="切换孩子">';
    items.forEach(function (c) {
      html += '<option value="' + c.id + '"' + (c.id === state.childId ? " selected" : "") + ">" +
        escapeHtml(c.name) + "</option>";
    });
    html += "</select></label></div>";
    html += '<p class="greeting">今天，也陪孩子发现一点不一样。</p>';
    if (next) {
      state.sessionId = next.id;
      state.childSessionMap[state.childId] = next.id;
      html += '<section class="hero-card" aria-label="下一节课">' +
        '<p class="eyebrow">下一节课 · ' + fmtDate(next.startAt) + "</p>" +
        "<h2>" + escapeHtml(sessionTheme(next)) + "</h2>" +
        '<p class="time-line">' + fmtClock(next.startAt) +
        (next.endAt ? (" – " + fmtClock(next.endAt)) : "") + "</p>" +
        '<p class="muted">' + escapeHtml(sessionPlaceLine(next)) + "</p>" +
        '<div class="actions"><button type="button" id="goNextDetail">课程详情</button>' +
        '<button type="button" class="secondary" id="goLeaveFromHome">请假</button></div></section>';
    } else {
      state.sessionId = state.childSessionMap[state.childId] || 0;
      html += '<section class="panel">' + stateBox("", "近期没有已安排的课程") +
        '<p class="muted">下一次安排待确认时可联系教务；也可先查看课表与课程权益。</p>' +
        '<button type="button" class="secondary" data-go="kids">查看课表</button></section>';
    }
    html += '<div class="quick-grid" role="navigation" aria-label="常用入口">' +
      '<button type="button" data-go="kids">课表</button>' +
      '<button type="button" data-go="parentLeave">请假补课</button>' +
      '<button type="button" data-go="progress">课程权益</button>' +
      '<button type="button" data-go="parentFb">成长档案</button></div>' +
      '<div class="section-head"><h2>最近反馈</h2><button type="button" class="link" data-go="parentFb">全部</button></div>' +
      '<div id="homeFb">' + stateBox("", "加载中…") + "</div>";
    el.innerHTML = html;
    document.getElementById("childHome").onchange = function () {
      var nextChild = parseInt(this.value, 10);
      if (nextChild === state.childId) return;
      state.childId = nextChild;
      state.sessionId = state.childSessionMap[nextChild] || 0;
      renderHomeParent();
    };
    var goNext = document.getElementById("goNextDetail");
    if (goNext) goNext.onclick = function () {
      state.sessionId = state.childSessionMap[state.childId] || state.sessionId;
      state.page = "session";
      render();
    };
    var goLv = document.getElementById("goLeaveFromHome");
    if (goLv) goLv.onclick = function () {
      state.sessionId = state.childSessionMap[state.childId] || state.sessionId;
      state.page = "parentLeave";
      render();
    };
    var gos = el.querySelectorAll("[data-go]");
    for (var i = 0; i < gos.length; i++) {
      gos[i].onclick = function (ev) { state.page = ev.currentTarget.getAttribute("data-go"); render(); };
    }
    var fbSeq = seq;
    var fbChild = state.childId;
    api("/api/wc/parent/feedback?studentId=" + fbChild).then(function (fb) {
      if (fbSeq !== state.loadSeq || state.page !== "home" || state.childId !== fbChild) return;
      var box = document.getElementById("homeFb");
      if (!box) return;
      if (!fb.ok) {
        box.innerHTML = retryBox(fb.message || "反馈加载失败", "rfb");
        document.getElementById("rfb").onclick = function () { renderHomeParent(); };
        return;
      }
      var list = fb.items || [];
      if (!list.length) {
        box.innerHTML = stateBox("", "还没有已发布的成长反馈") +
          '<p class="muted">老师发布后会出现在这里；草稿不会展示给家长。</p>';
        return;
      }
      var out = '<ul class="list child">';
      for (var j = 0; j < Math.min(3, list.length); j++) {
        var f = list[j];
        var mediaN = (f.mediaIds || []).length;
        out += "<li><div class=\"title\">" + escapeHtml(f.explored || "课堂观察") + "</div><div class=\"meta\">" +
          fmtDate(f.sessionStartAt) + " · " + escapeHtml(f.attempt || "") +
          (mediaN ? (" · 作品 " + mediaN + " 张") : "") + "</div>";
        if (mediaN) {
          out += '<div class="media-row">';
          for (var m = 0; m < Math.min(3, mediaN); m++) {
            out += '<img class="media-thumb" alt="课堂作品" src="' + apiBase() + "/api/wc/media/" +
              f.mediaIds[m] + '/content" />';
          }
          out += "</div>";
        }
        out += "</li>";
      }
      out += "</ul>";
      box.innerHTML = out;
    }).catch(function (e) {
      if (fbSeq !== state.loadSeq || state.page !== "home") return;
      var box = document.getElementById("homeFb");
      if (box && String(e.message) !== "AUTH") {
        box.innerHTML = retryBox("反馈加载失败：" + String(e.message || e), "rfb");
        document.getElementById("rfb").onclick = function () { renderHomeParent(); };
      }
    });
  }).catch(function (e) {
    if (seq !== state.loadSeq || state.page !== "home") return;
    if (String(e.message) !== "AUTH") {
      el.innerHTML = retryBox(String(e.message || e), "rh");
      document.getElementById("rh").onclick = render;
    }
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
  var preferSid = state.sessionId || state.childSessionMap[state.childId] || 0;
  shell('<section class="panel"><h1>请假补课</h1>' +
    '<p class="muted">请选择与首页一致的孩子和课次；提交前会再次校验可请假状态。</p>' +
    '<label>孩子<select id="childSel" aria-label="孩子"></select></label>' +
    '<label>课次<select id="sessSel" aria-label="课次"><option value="0">加载中…</option></select></label>' +
    '<div id="leaveMeta" class="muted"></div>' +
    '<label>原因（可选说明）<textarea id="reason">' + escapeHtml(state.form.reason || "") + "</textarea></label>" +
    '<button type="button" id="doLeave">提交请假</button>' +
    '<div id="mk"></div></section>');
  function loadSessionsForChild(childId) {
    var sel = document.getElementById("sessSel");
    var meta = document.getElementById("leaveMeta");
    sel.innerHTML = '<option value="0">加载中…</option>';
    api("/api/wc/sessions/today").then(function (j) {
      if (!j.ok) {
        sel.innerHTML = '<option value="0">课表加载失败</option>';
        meta.textContent = j.message || "无法加载课次";
        return;
      }
      var mine = (j.items || []).filter(function (s) {
        return s.studentId === childId && (s.status === "SCHEDULED" || s.status === "RESCHEDULED");
      }).sort(function (a, b) { return a.startAt - b.startAt; });
      sel.innerHTML = "";
      if (!mine.length) {
        sel.innerHTML = '<option value="0">没有可请假的课次</option>';
        meta.textContent = "该孩子近期没有可请假的已安排课程。";
        return;
      }
      mine.forEach(function (s) {
        var o = document.createElement("option");
        o.value = s.id;
        o.textContent = sessionTheme(s) + " · " + fmtDate(s.startAt) + " " + fmtClock(s.startAt);
        sel.appendChild(o);
      });
      var want = preferSid && mine.some(function (s) { return s.id === preferSid; }) ? preferSid : mine[0].id;
      sel.value = String(want);
      state.sessionId = want;
      state.childSessionMap[childId] = want;
      var cur = mine.filter(function (s) { return s.id === want; })[0];
      meta.textContent = cur ? sessionPlaceLine(cur) : "";
      sel.onchange = function () {
        state.sessionId = parseInt(sel.value, 10) || 0;
        state.childSessionMap[childId] = state.sessionId;
        var c = mine.filter(function (s) { return s.id === state.sessionId; })[0];
        meta.textContent = c ? sessionPlaceLine(c) : "";
      };
    }).catch(function (e) {
      if (String(e.message) !== "AUTH") {
        sel.innerHTML = '<option value="0">加载失败</option>';
        meta.textContent = String(e.message || e);
      }
    });
  }
  api("/api/wc/students").then(function (j) {
    var sel = document.getElementById("childSel");
    (j.items || []).forEach(function (c) {
      var o = document.createElement("option");
      o.value = c.id; o.textContent = c.name;
      sel.appendChild(o);
    });
    if (state.childId) sel.value = String(state.childId);
    state.childId = parseInt(sel.value, 10) || state.childId;
    sel.onchange = function () {
      state.childId = parseInt(sel.value, 10) || 0;
      preferSid = state.childSessionMap[state.childId] || 0;
      loadSessionsForChild(state.childId);
    };
    loadSessionsForChild(state.childId);
  });
  document.getElementById("doLeave").onclick = function () {
    state.form.reason = document.getElementById("reason").value;
    state.childId = parseInt(document.getElementById("childSel").value, 10) || 0;
    state.sessionId = parseInt(document.getElementById("sessSel").value, 10) || 0;
    if (!state.sessionId) { toast("请选择可请假的课次"); return; }
    if (state.busy) return;
    setBusy(true);
    api("/api/wc/leave", {
      method: "POST",
      body: {
        sessionId: state.sessionId,
        studentId: state.childId,
        reason: state.form.reason,
        idempotencyKey: idem("leave")
      }
    }).then(function (r) {
      document.getElementById("reason").value = state.form.reason;
      if (!r.ok) {
        toast(r.message || "提交失败，已保留内容");
        return null;
      }
      toast("请假已提交");
      state.dirtyForm = false;
      return api("/api/wc/students/" + state.childId + "/makeup");
    }).then(function (m) {
      if (!m) return;
      var el = document.getElementById("mk");
      if (m.ok) {
        el.innerHTML = '<div class="preview">补课资格已按合同规则处理（详见返回）。</div>';
      } else {
        el.innerHTML = stateBox("error", m.message || "补课资格查询失败");
      }
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

