/* Author: 张晓达 — wonder-campus modular www (plain scripts, no bundler) */
function renderHomeTeacher() {
  shell('<div id="homeBody">' + stateBox("", "加载中…") + "</div>");
  var el = document.getElementById("homeBody");
  api("/api/wc/home/teacher").then(function (h) {
    if (!h.ok) { el.innerHTML = retryBox(h.message || "加载失败", "rh"); document.getElementById("rh").onclick = render; return; }
    state.sessionId = h.nextSessionId || 0;
    var html = '<p class="brand-kicker">' + weekdayLabel() + "</p>" +
      "<h1>今天</h1><p class=\"greeting\">把课堂留给好奇，把琐事交给系统。</p>" +
      '<div class="stats">' +
      '<div class="stat"><span class="n">' + (h.todaySessionCount || 0) + '</span><span class="l">今日课程</span></div>' +
      '<div class="stat"><span class="n">' + (h.todayStudentCount || 0) + '</span><span class="l">上课学员</span></div>' +
      '<div class="stat"><span class="n">' + (h.pendingFeedbackCount || 0) + '</span><span class="l">待写反馈</span></div></div>' +
      '<div id="nextCard">' + stateBox("", "加载下一课…") + "</div>" +
      '<div class="quick-grid">' +
      '<button type="button" id="goTodayList">我的课表</button>' +
      '<button type="button" id="goFbList">课堂反馈</button>' +
      '<button type="button" id="goTeachHome">签到</button>' +
      '<button type="button" id="goNotesT">消息</button></div>';
    el.innerHTML = html;
    document.getElementById("goTodayList").onclick = function () { state.page = "today"; render(); };
    document.getElementById("goFbList").onclick = function () { state.page = "feedback"; render(); };
    document.getElementById("goTeachHome").onclick = function () {
      if (!state.sessionId) { toast("暂无下一节课，请从课表进入"); return; }
      state.page = "teach"; render();
    };
    document.getElementById("goNotesT").onclick = function () { state.page = "notes"; render(); };
    if (!h.nextSessionId) {
      document.getElementById("nextCard").innerHTML = '<section class="panel">' +
        stateBox("", "今天没有待上课程") +
        '<p class="muted">可查看未来安排，或从课表进入已结束课次补记反馈。</p>' +
        '<button type="button" class="secondary" id="goFuture">查看课表</button></section>';
      document.getElementById("goFuture").onclick = function () { state.page = "today"; render(); };
      return;
    }
    api("/api/wc/sessions/" + h.nextSessionId + "/roster").then(function (j) {
      var card = document.getElementById("nextCard");
      if (!card) return;
      if (!j.ok) {
        card.innerHTML = retryBox(j.message || "无法加载下一课", "rn");
        document.getElementById("rn").onclick = render;
        return;
      }
      var n = (j.roster || []).length;
      var pending = (j.roster || []).filter(function (r) {
        return r.attendance === "NOT_RECORDED";
      }).length;
      card.innerHTML = '<section class="hero-card"><p class="eyebrow">' + fmtDate(j.startAt) + " " +
        fmtClock(j.startAt) + " · " + statusLabel(j.status) + "</p><h2>" + escapeHtml(sessionTheme(j)) +
        "</h2>" +
        '<p class="muted">' + escapeHtml(sessionPlaceLine(j)) + " · " + n + " 名学员</p>" +
        '<p class="muted">待签到 ' + pending + " 人</p>" +
        '<div class="actions"><button type="button" id="enterClass">进入课堂</button>' +
        '<button type="button" class="secondary" id="enterFb">写反馈</button></div></section>';
      document.getElementById("enterClass").onclick = function () {
        state.sessionId = h.nextSessionId; state.page = "teach"; render();
      };
      document.getElementById("enterFb").onclick = function () {
        state.sessionId = h.nextSessionId; state.page = "feedback"; render();
      };
    }).catch(function (e) {
      var card = document.getElementById("nextCard");
      if (card && String(e.message) !== "AUTH") {
        card.innerHTML = retryBox(String(e.message || e), "rn");
        document.getElementById("rn").onclick = render;
      }
    });
  }).catch(function (e) {
    if (String(e.message) !== "AUTH") {
      el.innerHTML = retryBox(String(e.message || e), "rh");
      document.getElementById("rh").onclick = render;
    }
  });
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
  var canTeach = state.me.identity === "TEACHER" || state.me.identity === "ADMIN";
  var actions = canTeach
    ? '<div class="actions"><button type="button" class="secondary" id="goCheckin">去签到</button>' +
      '<button type="button" id="goFbFromSess">写反馈</button></div>'
    : (state.me.identity === "PARENT"
      ? '<div class="actions"><button type="button" class="secondary" id="goLeaveSess">请假</button></div>'
      : "");
  shell('<section class="panel"><h1>课次详情</h1>' +
    '<div id="sessBody">' + stateBox("", "加载中…") + "</div>" + actions + "</section>");
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
  var gc = document.getElementById("goCheckin");
  if (gc) gc.onclick = function () { state.page = "teach"; render(); };
  var gf = document.getElementById("goFbFromSess");
  if (gf) gf.onclick = function () { state.page = "feedback"; render(); };
  var gl = document.getElementById("goLeaveSess");
  if (gl) gl.onclick = function () { state.page = "parentLeave"; render(); };
}

function renderTeach() {
  shell('<section class="panel"><h1>签到</h1>' +
    '<div id="sessMeta">' + stateBox("", "加载课次…") + "</div>" +
    '<div id="roster">' + stateBox("", "加载中…") + "</div>" +
    '<div class="preview" id="batchPrev" role="status"></div>' +
    '<div class="actions"><button type="button" id="previewBatch">确认签到</button>' +
    '<button type="button" class="secondary" id="retryBtn">重试加载</button>' +
    '<button type="button" class="ghost" id="goFbAfter">写反馈</button></div></section>');
  var lastKey = "";
  var submitting = false;
  function load() {
    if (!state.sessionId) {
      document.getElementById("roster").innerHTML = stateBox("", "请先从「今天」或课表进入课次");
      return;
    }
    document.getElementById("roster").innerHTML = stateBox("", "加载中…");
    api("/api/wc/sessions/" + state.sessionId + "/roster").then(function (j) {
      if (!j.ok) {
        document.getElementById("roster").innerHTML = retryBox(j.message || "无权限", "retryBtn2");
        document.getElementById("retryBtn2").onclick = load;
        return;
      }
      document.getElementById("sessMeta").innerHTML = sessionCardHtml(j);
      var rows = j.roster || [];
      state.cache.roster = rows;
      if (!rows.length) {
        document.getElementById("roster").innerHTML = stateBox("", "本课暂无学员") +
          '<p class="muted">报名关系未就绪时名单为空；请联系教务确认。</p>';
        document.getElementById("previewBatch").textContent = "确认签到（0人）";
        return;
      }
      var html = '<ul class="list">';
      for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var done = r.attendance === "PRESENT" || r.attendance === "LATE";
        html += '<li><label class="row-check"><input type="checkbox" class="stu" value="' + r.studentId + '"' +
          (done ? "" : " checked") +
          ' data-est="' + (r.estimateMilli || 0) +
          '" data-mode="' + (r.billingMode || "") +
          '" data-name="' + escapeHtml(r.name) +
          '" data-att="' + (r.attendance || "") + '" /> ' +
          '<span><strong>' + escapeHtml(r.name) + "</strong> " + badge(attLabel(r.attendance), done ? "ok" : "") +
          "<br/><span class=\"meta\">" +
          (r.billingMode === "TERM" ? "学期制·出勤计次" : ("课时包·预计扣 " + fmtLessons(r.estimateMilli || 1000) + " 课时")) +
          (r.consumedMilli ? (" · 已扣 " + fmtLessons(r.consumedMilli)) : "") +
          "</span></span></label>" +
          (r.attendanceId ? ('<button type="button" class="ghost rev" data-aid="' + r.attendanceId +
            '" data-name="' + escapeHtml(r.name) + '">考勤纠错（冲正）</button>') : "") +
          "</li>";
      }
      html += "</ul>";
      document.getElementById("roster").innerHTML = html;
      var boxes = view.querySelectorAll(".stu");
      for (var b = 0; b < boxes.length; b++) boxes[b].onchange = updatePreview;
      var revs = view.querySelectorAll(".rev");
      for (var x = 0; x < revs.length; x++) {
        revs[x].onclick = function (ev) {
          var aid = ev.currentTarget.getAttribute("data-aid");
          var name = ev.currentTarget.getAttribute("data-name");
          if (state.busy || !window.confirm("确认冲正「" + name + "」的签到？将恢复对应权益，原记录保留审计。")) return;
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
    var packN = 0, termN = 0, est = 0;
    var already = 0;
    for (var i = 0; i < boxes.length; i++) {
      var mode = boxes[i].getAttribute("data-mode");
      var att = boxes[i].getAttribute("data-att");
      if (att === "PRESENT" || att === "LATE") already++;
      if (mode === "TERM") termN++;
      else { packN++; est += parseInt(boxes[i].getAttribute("data-est"), 10) || 1000; }
    }
    var unchecked = view.querySelectorAll(".stu:not(:checked)").length;
    var btn = document.getElementById("previewBatch");
    if (btn) btn.textContent = "确认签到（" + n + "人）";
    document.getElementById("batchPrev").textContent =
      "将提交出勤 " + n + " 人（未勾选 " + unchecked + " 人本次不登记）。" +
      (packN ? ("课时包 " + packN + " 人预计合计扣 " + fmtLessons(est) + " 课时；") : "") +
      (termN ? ("学期制 " + termN + " 人按次记录；") : "") +
      (already ? ("其中 " + already + " 人已有出勤记录，重复提交不会重复扣课。") : "幂等键保证网络重试不重复扣课。") +
      " 请假/缺勤请使用纠错或教务流程，不在此批量扣课。";
  }
  document.getElementById("retryBtn").onclick = load;
  document.getElementById("goFbAfter").onclick = function () { state.page = "feedback"; render(); };
  if (state.sessionId) load();
  document.getElementById("previewBatch").onclick = function () {
    var boxes = view.querySelectorAll(".stu:checked");
    var ids = [];
    for (var i = 0; i < boxes.length; i++) ids.push(boxes[i].value);
    updatePreview();
    if (!ids.length) { toast("请选择要签到的学员"); return; }
    if (!window.confirm(document.getElementById("batchPrev").textContent + "\n\n确认提交？")) return;
    if (state.busy || submitting) return;
    submitting = true;
    setBusy(true);
    if (!lastKey) lastKey = idem("ui-batch");
    api("/api/wc/sessions/" + state.sessionId + "/batch-checkin", {
      method: "POST",
      body: { studentIds: ids.join(","), idempotencyKey: lastKey }
    }).then(function (j) {
      var lines = [j.message || ""];
      var items = (j.data && j.data.items) || [];
      for (var k = 0; k < items.length; k++) {
        var it = items[k];
        lines.push((it.ok ? "✓ " : "✗ ") + "学员#" + it.studentId + "：" +
          (it.ok ? ("成功，扣 " + fmtLessons(it.consumedMilli || 0) + " 课时") : (it.message || it.code || "失败")));
      }
      document.getElementById("batchPrev").textContent = lines.join("\n");
      if (!j.ok && !(j.data && j.data.okCount)) {
        toast(j.message || "签到失败，内容未当作成功");
        return;
      }
      toast("已提交 " + ((j.data && j.data.okCount) || 0) + " 人出勤，正在回读");
      lastKey = "";
      return load();
    }).catch(function (e) {
      if (String(e.message) !== "AUTH") {
        toast("网络或服务异常，未确认保存成功：" + String(e.message || e));
        document.getElementById("batchPrev").textContent =
          "提交失败。请检查网络后使用同一幂等键重试（不会显示假成功，也不会重复扣课）。";
      }
    }).finally(function () { submitting = false; setBusy(false); });
  };
}

function renderFeedback() {
  shell('<section class="panel"><h1>课堂反馈</h1>' +
    '<p class="muted">草稿仅老师可见；发布前请确认孩子与接收范围。未完成上传的作品不能作为有效附件。</p>' +
    '<label>课次<input id="fbSid" type="number" inputmode="numeric" value="' + (state.sessionId || "") + '" /></label>' +
    '<label>学员<select id="fbStuSel" aria-label="选择学员"><option value="">加载名单…</option></select></label>' +
    '<label>今天探索了什么<textarea id="fbE">' + escapeHtml(state.form.explored || "") + "</textarea></label>" +
    '<label>孩子的具体尝试<textarea id="fbA">' + escapeHtml(state.form.attempt || "") + "</textarea></label>" +
    '<label>下一步鼓励<textarea id="fbC">' + escapeHtml(state.form.encouragement || "") + "</textarea></label>" +
    '<label>作品图片（可选）<input id="fbFile" type="file" accept="image/jpeg,image/png" /></label>' +
    '<p id="mediaHint" class="muted"></p>' +
    '<div class="actions"><button type="button" id="saveDraft">保存草稿</button>' +
    '<button type="button" class="secondary" id="pub">预览并发布</button></div>' +
    '<div class="preview" id="fbPrev" role="status"></div></section>');
  state.pendingMedia = 0;
  var rosterNames = {};
  function fillRoster() {
    var sid = parseInt(document.getElementById("fbSid").value, 10) || state.sessionId || 0;
    if (!sid) return;
    api("/api/wc/sessions/" + sid + "/roster").then(function (j) {
      var sel = document.getElementById("fbStuSel");
      if (!sel) return;
      sel.innerHTML = "";
      var rows = j.roster || [];
      if (!rows.length) {
        sel.innerHTML = '<option value="">本课暂无学员</option>';
        return;
      }
      rows.forEach(function (r) {
        rosterNames[r.studentId] = r.name;
        var o = document.createElement("option");
        o.value = r.studentId;
        o.textContent = r.name;
        if (state.form.studentId && state.form.studentId === r.studentId) o.selected = true;
        sel.appendChild(o);
      });
      if (!state.form.studentId) state.form.studentId = rows[0].studentId;
      sel.value = String(state.form.studentId);
    }).catch(function () {
      var sel = document.getElementById("fbStuSel");
      if (sel) sel.innerHTML = '<option value="">名单加载失败</option>';
    });
  }
  fillRoster();
  document.getElementById("fbSid").onchange = fillRoster;
  document.getElementById("fbFile").onchange = function () {
    state.dirtyForm = true;
    var f = this.files && this.files[0];
    document.getElementById("mediaHint").textContent = f
      ? ("已选择 " + f.name + "（" + Math.round(f.size / 1024) + " KB），保存草稿时上传")
      : "";
  };
  document.getElementById("saveDraft").onclick = function () {
    state.form.explored = document.getElementById("fbE").value;
    state.form.attempt = document.getElementById("fbA").value;
    state.form.encouragement = document.getElementById("fbC").value;
    state.form.studentId = parseInt(document.getElementById("fbStuSel").value, 10) || 0;
    if (state.busy) return;
    if (!state.form.studentId) { toast("请选择学员"); return; }
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
      var childName = rosterNames[stu] || ("学员#" + stu);
      document.getElementById("fbPrev").textContent = "草稿已保存（家长不可见）\n孩子：" + childName +
        "\n探索：" + state.form.explored + "\n尝试：" + state.form.attempt +
        "\n鼓励：" + state.form.encouragement;
      toast("草稿已保存");
      var file = document.getElementById("fbFile").files[0];
      if (file && state.draftId) {
        state.pendingMedia = 1;
        document.getElementById("mediaHint").textContent = "作品上传中…";
        return uploadFile(file, sid, stu, state.draftId).then(function (uj) {
          state.pendingMedia = 0;
          if (uj && uj.ok) {
            document.getElementById("mediaHint").textContent = "作品已上传，可随反馈发布";
            document.getElementById("fbFile").value = "";
          } else {
            document.getElementById("mediaHint").textContent = "作品上传失败，发布时不会附带无效附件";
          }
        });
      }
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
    if (state.pendingMedia) { toast("作品仍在上传，请等待完成后再发布"); return; }
    var stu = parseInt(document.getElementById("fbStuSel").value, 10) || 0;
    var childName = rosterNames[stu] || ("学员#" + stu);
    var preview = "发布预览\n孩子：" + childName +
      "\n接收范围：该孩子已授权监护人（其他孩子看不到）" +
      "\n作品：" + (document.getElementById("mediaHint").textContent.indexOf("已上传") >= 0 ? "已附带已完成上传的作品" : "无 / 未附带未完成上传") +
      "\n探索：" + document.getElementById("fbE").value +
      "\n尝试：" + document.getElementById("fbA").value +
      "\n鼓励：" + document.getElementById("fbC").value;
    document.getElementById("fbPrev").textContent = preview;
    if (!window.confirm(preview + "\n\n确认发布？内容修改不会重复轰炸通知。")) return;
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

