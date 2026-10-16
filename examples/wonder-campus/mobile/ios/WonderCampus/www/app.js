/* Author: 张晓达 — wonder-campus modular www (plain scripts, no bundler) */
/* boot: router + accept login */
function render() {
  if (!state.me) {
    renderLogin();
    return;
  }
  if (state.page === "home") return renderHome();
  if (state.page === "schedule") {
    if (state.me.identity === "DIRECTOR") {
      /* 负责人「校区」：真实校区列表，非演示指标 */
      shell('<section class="panel"><h1>校区</h1><div id="campBody">' + stateBox("", "加载中…") + "</div></section>");
      api("/api/wc/campuses").then(function (j) {
        var el = document.getElementById("campBody");
        var items = j.items || [];
        if (!items.length) { el.innerHTML = stateBox("", "暂无校区"); return; }
        var html = '<ul class="list">';
        items.forEach(function (c) {
          html += '<li><div class="title">' + c.name + '</div><div class="meta">校区 #' + c.id + "</div></li>";
        });
        el.innerHTML = html + "</ul>";
      });
      return;
    }
    return renderSchedule();
  }
  if (state.page === "today") return renderToday();
  if (state.page === "session") return renderSession();
  if (state.page === "leaves") return renderLeaves();
  if (state.page === "contracts") return renderContracts();
  if (state.page === "students") return renderStudents();
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
