/* 登录 / 邀请码注册 / 修改密码。真实账号登录优先；演示快捷入口仅在测试环境显示。 */
(function () {
  var WC = window.WC = window.WC || {};
  var K = WC.kit;
  var mode = "login";

  function afterLogin() {
    var core = WC.core;
    core.clearRoleCache();
    return core.refreshMe().then(function () {
      core.nativeSaveHint(core.state.me && core.state.me.identity);
      try { localStorage.setItem("wc_last_login", K.val("lgUser") || K.val("rgUser") || ""); } catch (e) { /* ignore */ }
      core.state.page = "home";
      core.state.stack = [];
      core.render();
    });
  }

  function demoBlock() {
    if (!WC.core.cfg.allowDemoLogin) return "";
    return '<details class="demo"><summary>演示账号（仅测试环境）</summary>' +
      '<p class="muted">测试数据，密码统一 Wonder2026，也可点下方一键进入。</p>' +
      '<div class="actions">' +
      '<button type="button" class="secondary" data-demo="admin|ADMIN">教务</button>' +
      '<button type="button" class="secondary" data-demo="admin|DIRECTOR">负责人</button>' +
      '<button type="button" class="secondary" data-demo="teacher1|TEACHER">老师</button>' +
      '<button type="button" class="secondary" data-demo="parent1|PARENT">家长</button>' +
      "</div></details>";
  }

  function render() {
    var last = "";
    try { last = localStorage.getItem("wc_last_login") || ""; } catch (e) { /* ignore */ }
    var body;
    if (mode === "login") {
      body = K.field("账号（手机号 / 工号）", "lgUser", { value: last, attrs: 'autocomplete="username" autocapitalize="off"' }) +
        K.field("密码", "lgPass", { type: "password", attrs: 'autocomplete="current-password"' }) +
        '<button type="button" id="btnLogin">登录</button>' +
        '<p class="muted">忘记密码请联系机构教务重置。</p>';
    } else {
      body = '<p class="muted">家长请向机构索取邀请码，注册后自动关联孩子。</p>' +
        K.field("邀请码", "rgCode", { attrs: 'autocapitalize="characters" maxlength="8"' }) +
        K.field("您的姓名", "rgName") +
        K.field("账号（建议手机号）", "rgUser", { attrs: 'autocomplete="username"' }) +
        K.field("手机号", "rgPhone", { type: "tel" }) +
        K.field("设置密码（≥8 位，含字母和数字）", "rgPass", { type: "password", attrs: 'autocomplete="new-password"' }) +
        K.field("确认密码", "rgPass2", { type: "password" }) +
        '<button type="button" id="btnReg">注册并进入</button>';
    }
    WC.core.shell('<section class="panel login"><h1>欢迎使用奇想校园</h1>' +
      K.tabs([{ k: "login", t: "账号登录" }, { k: "register", t: "邀请码注册" }], mode) + body +
      '<p id="authErr" class="state error"></p>' + demoBlock() + "</section>");
    K.bind(null, { tab: function (k) { mode = k; render(); } });
    var err = document.getElementById("authErr");
    function showErr(e) { if (err) err.textContent = String((e && e.message) || e); }
    var bl = document.getElementById("btnLogin");
    if (bl) bl.onclick = function () {
      if (WC.core.state.busy) return;
      err.textContent = "";
      var u = K.val("lgUser"), p = document.getElementById("lgPass").value;
      if (!u || !p) { showErr("请输入账号和密码"); return; }
      WC.core.setBusy(true);
      WC.core.api("/api/wc/auth/login", { method: "POST", body: { login: u, password: p }, raw: true })
        .then(function (j) { if (!j.ok) throw new Error(j.message || "登录失败"); return afterLogin(); })
        .catch(showErr).then(function () { WC.core.setBusy(false); });
    };
    var br = document.getElementById("btnReg");
    if (br) br.onclick = function () {
      if (WC.core.state.busy) return;
      err.textContent = "";
      var p1 = document.getElementById("rgPass").value, p2 = document.getElementById("rgPass2").value;
      if (p1 !== p2) { showErr("两次密码不一致"); return; }
      WC.core.setBusy(true);
      WC.core.api("/api/wc/auth/register", {
        method: "POST", raw: true,
        body: { inviteCode: K.val("rgCode").toUpperCase(), displayName: K.val("rgName"), login: K.val("rgUser"), phone: K.val("rgPhone"), password: p1 }
      }).then(function (j) { if (!j.ok) throw new Error(j.message || "注册失败"); return afterLogin(); })
        .catch(showErr).then(function () { WC.core.setBusy(false); });
    };
    var demos = document.querySelectorAll("[data-demo]");
    for (var i = 0; i < demos.length; i++) {
      demos[i].onclick = function (ev) {
        if (WC.core.state.busy) return;
        var parts = ev.currentTarget.getAttribute("data-demo").split("|");
        WC.core.setBusy(true);
        WC.core.api("/api/wc/auth/demo", { method: "POST", body: { login: parts[0], identity: parts[1] }, raw: true })
          .then(function (j) { if (!j.ok) throw new Error(j.message || "无法进入"); return afterLogin(); })
          .catch(showErr).then(function () { WC.core.setBusy(false); });
      };
    }
  }

  WC.pages.login = render;

  /* 修改密码 */
  WC.pages.password = function () {
    WC.core.shell(K.page("修改密码",
      K.field("原密码", "pwOld", { type: "password", attrs: 'autocomplete="current-password"' }) +
      K.field("新密码（≥8 位，含字母和数字）", "pwNew", { type: "password", attrs: 'autocomplete="new-password"' }) +
      K.field("确认新密码", "pwNew2", { type: "password" }) +
      '<button type="button" data-act="save">保存</button>'));
    K.bind(null, {
      save: function () {
        var n1 = document.getElementById("pwNew").value, n2 = document.getElementById("pwNew2").value;
        if (n1 !== n2) { WC.core.toast("两次新密码不一致"); return; }
        K.submit(function () {
          return K.api("/api/wc/auth/password", { method: "POST", body: { oldPassword: document.getElementById("pwOld").value, newPassword: n1 } })
            .then(function () { WC.core.toast("密码已修改"); WC.core.back(); });
        });
      }
    });
  };
})();
