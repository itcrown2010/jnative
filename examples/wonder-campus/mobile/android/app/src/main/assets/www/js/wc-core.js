/* Author: 张晓达 — wonder-campus modular www (plain scripts, no bundler) */

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
  pendingMedia: 0,
  form: {},
  cache: {},
  loadSeq: 0,
  childSessionMap: {}
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

function fmtClock(ms) {
  if (!ms) return "—";
  try {
    return new Date(ms).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  } catch (e) { return String(ms); }
}

function fmtDate(ms) {
  if (!ms) return "—";
  try { return new Date(ms).toLocaleDateString("zh-CN"); } catch (e) { return String(ms); }
}

function sessionTheme(s) {
  if (!s) return "课程";
  return s.theme || s.courseName || s.className || s.subject || ("班级 #" + (s.classId || "—"));
}

function sessionPlaceLine(s) {
  var campus = s.campusName || (s.campusId ? ("校区 " + s.campusId) : "校区 —");
  var room = s.roomName || (s.roomId ? ("教室 " + s.roomId) : "教室 —");
  var teacher = s.teacherName || (s.teacherUserId ? ("老师 #" + s.teacherUserId) : "老师 —");
  return campus + " · " + room + " · " + teacher;
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

function stateBox(kind, text) {
  return '<div class="state ' + (kind || "") + '">' + text + "</div>";
}

function escapeHtml(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function statusLabel(s) {
  if (s === "SCHEDULED") return "计划中";
  if (s === "RESCHEDULED") return "已改期";
  if (s === "COMPLETED") return "已完成";
  if (s === "CANCELLED_BY_SCHOOL") return "已取消";
  return s;
}

function attLabel(a) {
  if (a === "NOT_RECORDED") return "未登记";
  if (a === "PRESENT") return "出勤";
  if (a === "LATE") return "迟到";
  if (a === "LEAVE") return "请假";
  if (a === "ABSENT") return "缺勤";
  return a;
}

function queryParam(name) {
  try {
    var q = new URLSearchParams(window.location.search || "");
    return q.get(name) || "";
  } catch (e) { return ""; }
}

