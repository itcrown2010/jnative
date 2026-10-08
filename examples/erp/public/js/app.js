/**
 * JNative One - AppShell (UI 1.5 polish)
 * Interactive DEMO SPA (DemoStateStore). Not React/Vue.
 * Browser WASM SPA remains BLOCKED.
 */
(function () {
  if (window.JnTheme) JnTheme.apply();
  var tabs = [];
  var activePageId = null;
  var collapsed = false;
  var notifOpen = false;
  var pageScroll = {};
  var authed = sessionStorage.getItem("jn_demo_auth") === "1";

  function $(sel, root) { return (root || document).querySelector(sel); }

  function renderLogin() {
    document.body.innerHTML =
      '<div class="jn-login"><div class="jn-card jn-login-card">' +
      "<h1>JNative One</h1>" +
      '<div class="sub">Modern Enterprise ERP | UI 1.5 | DEMO MODE</div>' +
      '<div class="jn-field"><label>Email</label><input class="jn-input" id="email" value="user1@jnative.dev" /></div>' +
      '<div class="jn-field"><label>Password</label><input class="jn-input" id="password" type="password" value="demo" /></div>' +
      '<button type="button" class="jn-btn primary" id="btn-login" style="width:100%">Enter Demo Workspace</button>' +
      '<p class="hint" style="margin-top:12px;color:var(--jn-color-text-muted);font-size:12px">Demo mode does not connect to the formal business database. WASM WebUI runtime is not used for this shell (see foundation report).</p>' +
      "</div></div>";
    $("#btn-login").onclick = function () {
      authed = true;
      sessionStorage.setItem("jn_demo_auth", "1");
      bootShell();
    };
  }

  function menuHtml() {
    return JnRegistry.menuGroups.map(function (g) {
      var items = g.ids.map(function (id) {
        var p = JnRegistry.get(id);
        if (!p) return "";
        return '<button type="button" class="jn-nav-item' + (activePageId === id ? " active" : "") +
          '" data-nav="' + p.pageId + '" title="' + JnUI.esc(p.title) + '">' +
          '<span class="ico">' + JnUI.esc(p.icon || "*") + '</span><span class="lbl">' + JnUI.esc(p.title) + "</span></button>";
      }).join("");
      return '<div class="jn-nav-group"><div class="jn-nav-group-title">' + JnUI.esc(g.title) + "</div>" + items + "</div>";
    }).join("");
  }

  function tabsHtml() {
    if (!tabs.length) return "";
    return tabs.map(function (t) {
      return '<button type="button" class="jn-tab' + (t === activePageId ? " active" : "") + '" data-tab="' + t + '">' +
        JnUI.esc(JnRegistry.get(t).title) +
        '<span class="x" data-close-tab="' + t + '">x</span></button>';
    }).join("");
  }

  function breadcrumbHtml() {
    var p = JnRegistry.get(activePageId);
    if (!p) return "";
    return 'JNative One <span>/</span> ' + JnUI.esc(p.module) + " <span>/</span> <strong>" + JnUI.esc(p.title) + "</strong>";
  }

  function paintShellChrome() {
    var app = $("#jn-app");
    if (!app) return;
    app.classList.toggle("collapsed", collapsed);
    $(".jn-nav").innerHTML = menuHtml();
    $(".jn-tabbar").innerHTML = tabsHtml();
    $(".jn-breadcrumb").innerHTML = breadcrumbHtml();
    bindChrome();
  }

  function bindChrome() {
    document.querySelectorAll("[data-nav]").forEach(function (btn) {
      btn.onclick = function () { navigate(btn.getAttribute("data-nav")); };
    });
    document.querySelectorAll("[data-tab]").forEach(function (btn) {
      btn.onclick = function (e) {
        if (e.target.getAttribute("data-close-tab")) return;
        navigate(btn.getAttribute("data-tab"));
      };
    });
    document.querySelectorAll("[data-close-tab]").forEach(function (x) {
      x.onclick = function (e) {
        e.stopPropagation();
        closeTab(x.getAttribute("data-close-tab"));
      };
    });
  }

  function closeTab(id) {
    tabs = tabs.filter(function (t) { return t !== id; });
    if (activePageId === id) {
      activePageId = tabs[tabs.length - 1] || "dash.executive";
    }
    if (!tabs.length) tabs = ["dash.executive"];
    paintShellChrome();
    renderActive();
  }

  function navigate(pageId) {
    var openDoc = document.querySelector(".jn-doc-editor-backdrop .jn-doc-editor");
    if (openDoc && openDoc.querySelector("[data-dirty]") && openDoc.querySelector("[data-dirty]").style.display !== "none") {
      var leave = window.confirm("Unsaved changes will be lost. Leave document editor?");
      if (!leave) return;
      var bd = document.querySelector(".jn-doc-editor-backdrop");
      if (bd) bd.remove();
    }
    var page = JnRegistry.get(pageId);
    if (!page) {
      JnUI.toast("Unknown page: " + pageId, "err");
      return;
    }
    var host = $("#jn-workspace");
    if (host && activePageId) pageScroll[activePageId] = host.scrollTop;
    if (tabs.indexOf(pageId) < 0) tabs.push(pageId);
    activePageId = pageId;
    location.hash = "#" + page.route;
    try { sessionStorage.setItem("jn_ui_tabs", JSON.stringify(tabs)); sessionStorage.setItem("jn_ui_active", activePageId); } catch (e) {}
    paintShellChrome();
    renderActive();
  }

  function renderActive() {
    var page = JnRegistry.get(activePageId);
    var host = $("#jn-workspace");
    if (!page || !host) return;
    JnTemplates.render(host, page);
    var y = pageScroll[activePageId] || 0;
    setTimeout(function () { host.scrollTop = y; }, 0);
  }

  function bootShell() {
    document.body.innerHTML =
      '<div class="jn-app" id="jn-app">' +
      '<aside class="jn-sidebar">' +
      '<div class="jn-sidebar-brand"><span class="mark">JN</span><span class="label">JNative One</span></div>' +
      '<nav class="jn-nav"></nav></aside>' +
      '<div class="jn-main">' +
      '<header class="jn-topbar">' +
      '<button type="button" class="jn-icon-btn" id="btn-collapse" title="Toggle sidebar">=</button>' +
      '<input class="jn-search" id="global-search" placeholder="Global search (DEMO) - customers, orders, items..." />' +
      '<div class="jn-topbar-right" style="position:relative">' +
      '<button type="button" class="jn-density-toggle" id="btn-density" title="Toggle density">Density</button>' +
      '<button type="button" class="jn-btn" id="btn-demo-center" style="height:28px;padding:0 10px;font-size:12px">Demo Center</button>' +
      '<button type="button" class="jn-icon-btn" id="btn-notif" title="Notifications" aria-label="Notifications">?</button>' +
      '<div class="jn-user-menu" id="user-menu" tabindex="0" aria-label="User menu"><span class="jn-avatar">DU</span><span>Demo User</span></div>' +
      '<div class="jn-notif-panel" id="notif-panel" style="display:none" role="menu"></div>' +
      "</div></header>" +
      '<div class="jn-demo-banner" role="status"><span class="badge">DEMO MODE</span> DemoStateStore + ApplicationService - isolated from formal business DB. ' +
      'Persist: localStorage (survives refresh; cleared by Reset / clearing site data). Pages: ' +
      JnRegistry.count +
      ' <button type="button" class="jn-btn" id="btn-reset-demo" style="margin-left:8px;height:24px;padding:0 8px;font-size:11px">Reset Demo Data</button></div>' +
      '<div class="jn-tabbar"></div>' +
      '<div class="jn-breadcrumb"></div>' +
      '<main class="jn-workspace" id="jn-workspace"></main>' +
      "</div></div>" +
      '<div class="jn-toast-host" id="jn-toast-host"></div>';

    $("#btn-collapse").onclick = function () {
      collapsed = !collapsed;
      paintShellChrome();
    };
    var resetBtn = $("#btn-reset-demo");
    if (resetBtn) {
      resetBtn.onclick = function () {
        if (!window.confirm("Reset all DEMO data to seed state? This cannot be undone.")) return;
        JnAppService.resetDemo();
        JnUI.toast("DEMO data reset to seed (localStorage rewritten)", "ok");
        renderActive();
      };
    }
    var dens = $("#btn-density");
    if (dens) {
      dens.onclick = function () {
        var d = JnTheme.toggleDensity();
        dens.textContent = d.density === "compact" ? "Compact" : "Comfortable";
        JnUI.toast("Density: " + d.density, "ok");
        renderActive();
      };
      dens.textContent = (JnTheme.get().density === "compact") ? "Compact" : "Comfortable";
    }
    var demoBtn = $("#btn-demo-center");
    if (demoBtn) demoBtn.onclick = function () { navigate("demo.center"); };
    $("#btn-notif").onclick = function () {
      notifOpen = !notifOpen;
      var panel = $("#notif-panel");
      panel.style.display = notifOpen ? "block" : "none";
      if (notifOpen) {
        panel.innerHTML = JnMockData.notifications().map(function (n) {
          return '<div class="jn-notif-item"><strong>' + JnUI.esc(n.title) + "</strong><div>" + JnUI.esc(n.body) +
            '</div><div style="color:var(--jn-color-text-muted)">' + JnUI.esc(n.time) + "</div></div>";
        }).join("");
      }
    };
    $("#user-menu").onclick = function () {
      JnUI.modal({
        title: "User menu",
        bodyHtml: "<p>Demo User | Demo Co</p><p>Role: Admin</p><p>Data mode: DEMO</p>",
        primaryLabel: "Sign out",
        onOk: function () {
          sessionStorage.removeItem("jn_demo_auth");
          location.reload();
        }
      });
    };
    if (window.JnGlobalSearch) JnGlobalSearch.bind($("#global-search"));
    else {
      $("#global-search").addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
          var q = e.target.value.trim();
          if (!q) return;
          navigate("md.customers");
          JnUI.toast("Global search fallback (DEMO)", "ok");
        }
      });
    }

    var hashRoute = (location.hash || "").replace(/^#/, "");
    var initial = JnRegistry.getByRoute(hashRoute);
    try {
      var savedTabs = JSON.parse(sessionStorage.getItem("jn_ui_tabs") || "[]");
      var savedActive = sessionStorage.getItem("jn_ui_active");
      if (savedTabs && savedTabs.length) {
        tabs = savedTabs.filter(function (id) { return !!JnRegistry.get(id); });
        activePageId = JnRegistry.get(savedActive) ? savedActive : tabs[0];
      }
    } catch (e) {}
    if (!tabs.length) {
      var start = initial || JnRegistry.get("dash.executive");
      tabs = [start.pageId];
      activePageId = start.pageId;
    } else if (initial && tabs.indexOf(initial.pageId) < 0) {
      tabs.push(initial.pageId);
      activePageId = initial.pageId;
    } else if (initial) {
      activePageId = initial.pageId;
    }
    paintShellChrome();
    renderActive();
  }

  window.JnApp = {
    navigate: navigate,
    pageCount: function () { return JnRegistry.count; }
  };

  document.addEventListener("DOMContentLoaded", function () {
    JnData.useMock();
    if (!authed) renderLogin();
    else bootShell();
  });
})();
