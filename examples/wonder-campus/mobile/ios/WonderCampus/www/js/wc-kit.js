/* 奇想校园 UI 工具集：转义、格式化、表单、页面骨架。纯函数，不依赖 app.js 内部变量。 */
(function () {
  var WC = window.WC = window.WC || {};
  WC.pages = WC.pages || {};
  var K = WC.kit = {};

  K.esc = function (s) {
    if (s === null || s === undefined) return "";
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  };

  /* 金额：服务端一律为「分」整数；仅在展示层换算，避免浮点参与计算 */
  K.money = function (cents) {
    cents = Math.trunc(Number(cents) || 0);
    var sign = cents < 0 ? "-" : "";
    var a = Math.abs(cents);
    var y = Math.floor(a / 100);
    var f = a % 100;
    return sign + "¥" + String(y).replace(/\B(?=(\d{3})+(?!\d))/g, ",") + "." + (f < 10 ? "0" : "") + f;
  };

  K.toCents = function (yuanStr) {
    var t = String(yuanStr || "").trim();
    if (!/^\d+(\.\d{1,2})?$/.test(t)) return NaN;
    var parts = t.split(".");
    var frac = (parts[1] || "") + "00";
    return parseInt(parts[0], 10) * 100 + parseInt(frac.slice(0, 2), 10);
  };

  /* 课时：lesson_milli(1000=1课时) */
  K.lessons = function (milli) {
    var v = (Number(milli) || 0) / 1000;
    return (Math.round(v * 10) / 10).toString();
  };

  var TZ = { timeZone: "Asia/Shanghai", hour12: false };
  K.date = function (ms) {
    if (!ms) return "—";
    return new Date(ms).toLocaleDateString("zh-CN", { timeZone: TZ.timeZone, month: "numeric", day: "numeric" });
  };
  K.dateFull = function (ms) {
    if (!ms) return "—";
    return new Date(ms).toLocaleDateString("zh-CN", { timeZone: TZ.timeZone, year: "numeric", month: "numeric", day: "numeric" });
  };
  K.hm = function (ms) {
    if (!ms) return "";
    return new Date(ms).toLocaleTimeString("zh-CN", { timeZone: TZ.timeZone, hour: "2-digit", minute: "2-digit", hour12: false });
  };
  K.dt = function (ms) {
    if (!ms) return "—";
    return K.date(ms) + " " + K.hm(ms);
  };
  K.weekday = function (ms) {
    var w = new Date(ms).toLocaleDateString("zh-CN", { timeZone: TZ.timeZone, weekday: "short" });
    return w;
  };
  K.dayStart = function (ms) {
    var off = 8 * 3600000;
    var local = ms + off;
    return local - (local % 86400000) - off;
  };
  K.dayKey = function (ms) { return K.dayStart(ms); };
  K.dayLabel = function (ms) {
    var today = K.dayStart(Date.now());
    var d = K.dayStart(ms);
    var diff = Math.round((d - today) / 86400000);
    var tag = diff === 0 ? "今天" : (diff === 1 ? "明天" : (diff === -1 ? "昨天" : ""));
    return K.date(ms) + " " + K.weekday(ms) + (tag ? " · " + tag : "");
  };

  /* 输入框 datetime-local ↔ 毫秒（按东八区解释） */
  K.toLocalInput = function (ms) {
    var d = new Date(ms + 8 * 3600000);
    return d.toISOString().slice(0, 16);
  };
  K.fromLocalInput = function (v) {
    if (!v) return 0;
    var t = Date.parse(v + ":00Z");
    if (isNaN(t)) return 0;
    return t - 8 * 3600000;
  };

  K.pill = function (text, kind) {
    return '<span class="pill ' + (kind || "") + '">' + K.esc(text) + "</span>";
  };

  K.empty = function (text) { return '<div class="state">' + K.esc(text) + "</div>"; };

  K.field = function (label, id, o) {
    o = o || {};
    var type = o.type || "text";
    var v = o.value === undefined || o.value === null ? "" : o.value;
    var ph = o.placeholder ? ' placeholder="' + K.esc(o.placeholder) + '"' : "";
    var extra = o.attrs ? " " + o.attrs : "";
    if (type === "textarea") {
      return "<label>" + K.esc(label) + '<textarea id="' + id + '"' + ph + extra + ">" + K.esc(v) + "</textarea></label>";
    }
    return "<label>" + K.esc(label) + '<input id="' + id + '" type="' + type + '" value="' + K.esc(v) + '"' + ph + extra + " /></label>";
  };

  /* options: [{v, t}] ; selected: v */
  K.select = function (label, id, options, selected, attrs) {
    var h = "<label>" + K.esc(label) + '<select id="' + id + '"' + (attrs ? " " + attrs : "") + ">";
    for (var i = 0; i < options.length; i++) {
      var o = options[i];
      h += '<option value="' + K.esc(o.v) + '"' + (String(o.v) === String(selected) ? " selected" : "") + ">" + K.esc(o.t) + "</option>";
    }
    return h + "</select></label>";
  };

  K.val = function (id) {
    var el = document.getElementById(id);
    return el ? el.value.trim() : "";
  };
  K.num = function (id) {
    var n = parseInt(K.val(id), 10);
    return isNaN(n) ? 0 : n;
  };

  /* 页面骨架：标题 + 可选返回 */
  K.page = function (title, body, o) {
    o = o || {};
    var head = '<div class="row pagehead">';
    if (o.back !== false) head += '<button type="button" class="ghost" data-act="back">‹ 返回</button>';
    head += "<h1>" + K.esc(title) + "</h1>";
    head += o.right || '<span></span>';
    head += "</div>";
    return '<section class="panel">' + head + body + "</section>";
  };

  K.tabs = function (items, active) {
    var h = '<div class="tabs" role="tablist">';
    for (var i = 0; i < items.length; i++) {
      h += '<button type="button" class="tab' + (items[i].k === active ? " on" : "") + '" data-tab="' + items[i].k + '">' + K.esc(items[i].t) + "</button>";
    }
    return h + "</div>";
  };

  K.kpi = function (value, label, o) {
    o = o || {};
    var attrs = o.go ? ' data-go="' + o.go + '"' + (o.p ? ' data-p="' + o.p + '"' : "") : "";
    return '<div class="kpi' + (o.warn ? " warn" : "") + (o.go ? " tap" : "") + '"' + attrs + '><div class="v">' + K.esc(value) +
      '</div><div class="l">' + K.esc(label) + "</div></div>";
  };

  K.tile = function (icon, label, go, badge, p) {
    return '<button type="button" class="tile" data-go="' + go + '"' + (p ? ' data-p="' + p + '"' : "") + '><span class="ic">' + icon + "</span><span>" + K.esc(label) + "</span>" +
      (badge ? '<i class="bd">' + K.esc(badge) + "</i>" : "") + "</button>";
  };

  K.stars = function (n) {
    var s = "";
    for (var i = 1; i <= 5; i++) s += i <= n ? "★" : "☆";
    return '<span class="stars">' + s + "</span>";
  };

  /* 统一事件绑定：data-act / data-go / data-tab / data-id */
  K.bind = function (root, acts) {
    var core = WC.core;
    var scope = root || document.getElementById("view");
    var nodes = scope.querySelectorAll("[data-act]");
    for (var i = 0; i < nodes.length; i++) {
      var nm = nodes[i].getAttribute("data-act");
      if (nm === "back") { nodes[i].onclick = function () { core.back(); }; continue; }
      if (!acts || !acts[nm]) continue; /* 保留此前已绑定的处理器 */
      nodes[i].onclick = (function (fn) { return function (ev) { fn(ev.currentTarget, ev); }; })(acts[nm]);
    }
    var gos = scope.querySelectorAll("[data-go]");
    for (var j = 0; j < gos.length; j++) {
      gos[j].onclick = function (ev) {
        var el = ev.currentTarget;
        var p = {};
        var id = el.getAttribute("data-id");
        if (id) p.id = parseInt(id, 10);
        var extra = el.getAttribute("data-p");
        if (extra) {
          extra.split("&").forEach(function (kv) {
            var a = kv.split("=");
            p[a[0]] = decodeURIComponent(a[1] || "");
          });
        }
        core.go(el.getAttribute("data-go"), p);
      };
    }
    var tabs = scope.querySelectorAll("[data-tab]");
    for (var t = 0; t < tabs.length; t++) {
      if (!acts || !acts.tab) continue;
      tabs[t].onclick = function (ev) { acts.tab(ev.currentTarget.getAttribute("data-tab")); };
    }
  };

  /* API：失败抛错（已在 app.js 中处理 401），统一 toast */
  K.api = function (path, opts) {
    return WC.core.api(path, opts).then(function (j) {
      if (!j || j.ok === 0 || j.ok === false) {
        var e = new Error((j && j.message) || "操作失败");
        e.code = j && j.code;
        throw e;
      }
      return j;
    });
  };
  K.fail = function (e) {
    if (e && String(e.message) === "AUTH") return;
    WC.core.toast(String((e && e.message) || e));
  };

  K.body = function (id, html) {
    var el = document.getElementById(id);
    if (el) el.innerHTML = html;
  };
  K.loading = function () { return '<div class="state">加载中…</div>'; };
  K.error = function (e) { return '<div class="state error">' + K.esc((e && e.message) || e) + "</div>"; };

  /* 带 busy 防抖的提交 */
  K.submit = function (fn) {
    var core = WC.core;
    if (core.state.busy) return;
    core.setBusy(true);
    var done = function () { core.setBusy(false); };
    var p;
    try { p = fn(); } catch (e) { done(); K.fail(e); return; }
    Promise.resolve(p).catch(K.fail).then(done, done);
  };

  K.download = function (name, text) {
    try {
      var blob = new Blob([text], { type: "text/csv;charset=utf-8" });
      var a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      setTimeout(function () { document.body.removeChild(a); URL.revokeObjectURL(a.href); }, 500);
      return true;
    } catch (e) { return false; }
  };

  K.copy = function (text) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        return navigator.clipboard.writeText(text).then(function () { return true; }, function () { return false; });
      }
    } catch (e) { /* fall through */ }
    return Promise.resolve(false);
  };

  K.sessionStatus = function (s) {
    var m = { SCHEDULED: "待上课", RESCHEDULED: "已改期", COMPLETED: "已完成", CANCELLED_BY_SCHOOL: "学校停课" };
    return m[s] || s;
  };
  K.sessionKind = function (s) {
    return s === "COMPLETED" ? "ok" : (s === "CANCELLED_BY_SCHOOL" ? "bad" : "");
  };

  /* 学员选择：加载 /admin/students 到 <select> */
  K.loadStudentOptions = function (selId, selected, q) {
    return K.api("/api/wc/admin/students" + (q ? "?q=" + encodeURIComponent(q) : "")).then(function (j) {
      var sel = document.getElementById(selId);
      if (!sel) return j;
      sel.innerHTML = (j.items || []).map(function (s) {
        return '<option value="' + s.id + '"' + (String(selected) === String(s.id) ? " selected" : "") + ">" +
          K.esc(s.name) + (s.guardianPhone ? " · " + K.esc(s.guardianPhone) : "") + "</option>";
      }).join("");
      return j;
    });
  };
})();
