// Author: 张晓达
/**
 * JNative One - AppShell (Rpc + DB)
 * Browser WASM SPA remains BLOCKED.
 * i18n: JnI18n (zh-CN default, en); preference in localStorage jn_erp_locale.
 */
(function () {
  if (window.JnTheme) JnTheme.apply();
  if (window.JnI18n) JnI18n.applyHtmlLang();
  var tabs = [];
  var activePageId = null;
  var collapsed = false;
  var notifOpen = false;
  var pageScroll = {};
  var sessionUser = null;
  var bookInfo = null;
  var authed = sessionStorage.getItem("jn_auth") === "1" || sessionStorage.getItem("jn_rpc_auth") === "1";
  if (window.JnData) JnData.useRpc("");
  if (window.JnGridData) JnGridData.useRpc("");

  function $(sel, root) { return (root || document).querySelector(sel); }
  function t(key, params, fallback) {
    return window.JnI18n ? JnI18n.t(key, params, fallback) : (fallback != null ? fallback : key);
  }

  function userDisplay() {
    var u = sessionUser || (JnData.active && JnData.active.user) || null;
    if (!u) {
      try { u = JSON.parse(sessionStorage.getItem("jn_user") || "null"); } catch (e) { u = null; }
    }
    return u;
  }

  function userLabel(u) {
    if (!u) return t("common.signedIn");
    return u.displayName || u.email || ("#" + u.id);
  }

  function userInitials(u) {
    var name = userLabel(u);
    if (!name) return "?";
    var parts = String(name).replace(/@.*/, "").split(/[\s._-]+/).filter(Boolean);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return String(name).slice(0, 2).toUpperCase();
  }

  function bookLabel() {
    var b = bookInfo || (JnData.active && JnData.active.book) || null;
    if (!b) return "";
    return b.name || b.code || "";
  }

  function isDemoBook() {
    var b = bookInfo || (JnData.active && JnData.active.book) || null;
    return !!(b && (b.bookKind === "DEMO" || b.demo === true || b.demoSeedVersion != null));
  }

  function sanitizeTabs(list) {
    var removed = { "demo.center": 1 };
    return (list || []).filter(function (id) {
      if (removed[id]) return false;
      return !!JnRegistry.getRaw(id);
    });
  }

  function afterLogin(data) {
    sessionUser = (data && data.user) || JnData.active.user;
    authed = true;
    return JnData.active.loadBook ? JnData.active.loadBook() : null;
  }

  function renderLogin() {
    document.body.innerHTML =
      '<div class="jn-login"><div class="jn-card jn-login-card">' +
      (window.JnI18n ? JnI18n.langToggleHtml() : "") +
      "<h1>" + JnUI.esc(t("app.brand", null, "JNative One")) + "</h1>" +
      '<div class="sub">' + JnUI.esc(t("login.subtitle")) + "</div>" +
      '<div class="jn-field"><label>' + JnUI.esc(t("login.email")) + '</label><input class="jn-input" id="email" value="user1@jnative.dev" /></div>' +
      '<div class="jn-field"><label>' + JnUI.esc(t("login.password")) + '</label><input class="jn-input" id="password" type="password" value="erp1234" /></div>' +
      '<button type="button" class="jn-btn primary" id="btn-login" style="width:100%">' + JnUI.esc(t("login.submit")) + "</button>" +
      '<button type="button" class="jn-btn" id="btn-demo-enter" style="width:100%;margin-top:8px;display:none">' +
      JnUI.esc(t("login.demoEnter", null, "体验示例公司")) + "</button>" +
      '<p class="hint" style="margin-top:12px;color:var(--jn-color-text-muted);font-size:12px">' + JnUI.esc(t("login.hint")) + "</p>" +
      '<p id="login-err" style="color:#b91c1c;font-size:12px;margin-top:8px"></p>' +
      "</div></div>";
    if (window.JnI18n) {
      JnI18n.bindLangToggle(document.body, function () { renderLogin(); });
    }
    var errEl = $("#login-err");
    var demoBtn = $("#btn-demo-enter");
    if (JnData.active && JnData.active.demoAvailable) {
      JnData.active.demoAvailable().then(function (ok) {
        if (ok && demoBtn) demoBtn.style.display = "";
      });
    }
    $("#btn-login").onclick = function () {
      var email = $("#email").value.trim();
      var password = $("#password").value;
      var btn = $("#btn-login");
      errEl.textContent = "";
      if (btn) {
        btn.disabled = true;
        btn.textContent = t("login.submitting", null, "登录中…");
      }
      JnData.useRpc("");
      JnData.active.login(email, password).then(afterLogin).then(function (book) {
        bookInfo = book || null;
        bootShell();
      }).catch(function (err) {
        errEl.textContent = (err && err.message) ? err.message : t("login.failed");
        if (btn) {
          btn.disabled = false;
          btn.textContent = t("login.submit");
        }
      });
    };
    if (demoBtn) {
      demoBtn.onclick = function () {
        errEl.textContent = "";
        demoBtn.disabled = true;
        demoBtn.textContent = t("login.submitting", null, "登录中…");
        JnData.useRpc("");
        JnData.active.enterDemo().then(afterLogin).then(function (book) {
          bookInfo = book || null;
          bootShell();
        }).catch(function (err) {
          errEl.textContent = (err && err.message) ? err.message : t("login.failed");
          demoBtn.disabled = false;
          demoBtn.textContent = t("login.demoEnter", null, "体验示例公司");
        });
      };
    }
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
      if (!items) return "";
      return '<div class="jn-nav-group"><div class="jn-nav-group-title">' + JnUI.esc(g.title) + "</div>" + items + "</div>";
    }).join("");
  }

  function tabsHtml() {
    if (!tabs.length) return "";
    return tabs.map(function (tid) {
      var p = JnRegistry.get(tid);
      return '<button type="button" class="jn-tab' + (tid === activePageId ? " active" : "") + '" data-tab="' + tid + '">' +
        JnUI.esc(p ? p.title : tid) +
        '<span class="x" data-close-tab="' + tid + '">x</span></button>';
    }).join("");
  }

  function breadcrumbHtml() {
    var p = JnRegistry.get(activePageId);
    if (!p) return "";
    return JnUI.esc(t("app.brand", null, "JNative One")) + ' <span>/</span> ' +
      JnUI.esc(p.module) + " <span>/</span> <strong>" + JnUI.esc(p.title) + "</strong>";
  }

  function paintUserChrome() {
    var u = userDisplay();
    var label = userLabel(u);
    var initials = userInitials(u);
    var avatar = $("#user-avatar");
    var nameEl = $("#user-name");
    if (avatar) avatar.textContent = initials;
    if (nameEl) nameEl.textContent = label;
    var bookEl = $("#jn-book-label");
    if (bookEl) {
      if (isDemoBook()) {
        bookEl.textContent = t("common.demoBookChip", null, "示例公司 · 演示账套");
        bookEl.style.display = "";
        bookEl.title = bookLabel() || "DEMO";
      } else {
        var bl = bookLabel();
        bookEl.textContent = bl;
        bookEl.style.display = bl ? "" : "none";
        if (bl) bookEl.title = bl;
      }
    }
  }

  function paintShellChrome() {
    var app = $("#jn-app");
    if (!app) return;
    app.classList.toggle("collapsed", collapsed);
    $(".jn-nav").innerHTML = menuHtml();
    $(".jn-tabbar").innerHTML = tabsHtml();
    $(".jn-breadcrumb").innerHTML = breadcrumbHtml();
    var langHost = $("#jn-lang-host");
    if (langHost && window.JnI18n) {
      langHost.innerHTML = JnI18n.langToggleHtml();
      JnI18n.bindLangToggle(langHost, function () { onLocaleChange(); });
    }
    var dens = $("#btn-density");
    if (dens && window.JnTheme) {
      dens.textContent = (JnTheme.get().density === "compact") ? t("common.compact") : t("common.comfortable");
    }
    var search = $("#global-search");
    if (search) {
      search.placeholder = t("common.searchUnavailable");
      search.disabled = true;
      search.title = t("common.searchUnavailable");
    }
    paintUserChrome();
    bindChrome();
  }

  function onLocaleChange() {
    paintShellChrome();
    renderActive();
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
    tabs = tabs.filter(function (tid) { return tid !== id; });
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
      var leave = window.confirm(t("common.unsavedLeave"));
      if (!leave) return;
      var bd = document.querySelector(".jn-doc-editor-backdrop");
      if (bd) bd.remove();
    }
    if (pageId === "demo.center") {
      JnUI.toast(t("common.pageRemoved"), "err");
      return;
    }
    var page = JnRegistry.get(pageId);
    if (!page) {
      JnUI.toast(t("common.unknownPage", { id: pageId }), "err");
      return;
    }
    var host = $("#jn-workspace");
    if (host && activePageId) pageScroll[activePageId] = host.scrollTop;
    if (tabs.indexOf(pageId) < 0) tabs.push(pageId);
    activePageId = pageId;
    location.hash = "#" + page.route;
    try {
      sessionStorage.setItem("jn_ui_tabs", JSON.stringify(sanitizeTabs(tabs)));
      sessionStorage.setItem("jn_ui_active", activePageId);
    } catch (e) {}
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
    var u = userDisplay();
    document.body.innerHTML =
      '<div class="jn-app" id="jn-app">' +
      '<aside class="jn-sidebar">' +
      '<div class="jn-sidebar-brand"><span class="mark">JN</span><span class="label">' +
      JnUI.esc(t("app.brand", null, "JNative One")) + '</span></div>' +
      '<nav class="jn-nav"></nav></aside>' +
      '<div class="jn-main">' +
      '<header class="jn-topbar">' +
      '<button type="button" class="jn-icon-btn" id="btn-collapse" title="' + JnUI.esc(t("common.toggleSidebar")) + '">=</button>' +
      '<input class="jn-search" id="global-search" disabled title="' + JnUI.esc(t("common.searchUnavailable")) +
      '" placeholder="' + JnUI.esc(t("common.searchUnavailable")) + '" />' +
      '<div class="jn-topbar-right" style="position:relative">' +
      '<span class="jn-book-chip" id="jn-book-label" style="display:none;margin-right:8px;font-size:12px;color:var(--jn-color-text-muted)"></span>' +
      '<span id="jn-lang-host"></span>' +
      '<button type="button" class="jn-density-toggle" id="btn-density" title="' + JnUI.esc(t("common.density")) + '">' +
      JnUI.esc(t("common.density")) + "</button>" +
      '<button type="button" class="jn-icon-btn" id="btn-notif" title="' + JnUI.esc(t("common.notifications")) +
      '" aria-label="' + JnUI.esc(t("common.notifications")) + '">?</button>' +
      '<div class="jn-user-menu" id="user-menu" tabindex="0" aria-label="' + JnUI.esc(t("common.userMenu")) +
      '"><span class="jn-avatar" id="user-avatar">' + JnUI.esc(userInitials(u)) +
      '</span><span id="user-name">' + JnUI.esc(userLabel(u)) + "</span></div>" +
      '<div class="jn-notif-panel" id="notif-panel" style="display:none" role="menu"></div>' +
      "</div></header>" +
      '<div class="jn-tabbar"></div>' +
      '<div class="jn-breadcrumb"></div>' +
      '<main class="jn-workspace" id="jn-workspace"></main>' +
      "</div></div>" +
      '<div class="jn-toast-host" id="jn-toast-host"></div>';

    $("#btn-collapse").onclick = function () {
      collapsed = !collapsed;
      paintShellChrome();
    };
    var dens = $("#btn-density");
    if (dens) {
      dens.onclick = function () {
        var d = JnTheme.toggleDensity();
        dens.textContent = d.density === "compact" ? t("common.compact") : t("common.comfortable");
        JnUI.toast(t("common.densityToast", { density: d.density }), "ok");
        renderActive();
      };
    }
    $("#btn-notif").onclick = function () {
      notifOpen = !notifOpen;
      var panel = $("#notif-panel");
      panel.style.display = notifOpen ? "block" : "none";
      if (notifOpen) {
        panel.innerHTML = '<div class="jn-notif-item"><div>' + JnUI.esc(t("common.notificationsEmpty")) + "</div></div>";
      }
    };
    $("#user-menu").onclick = function () {
      var cu = userDisplay();
      var bl = bookLabel();
      var role = (cu && cu.role) ? String(cu.role) : "";
      var demoReset = isDemoBook()
        ? '<p style="margin-top:12px"><button type="button" class="jn-btn" id="btn-demo-reset">' +
          JnUI.esc(t("common.demoReset", null, "重置演示账套")) + "</button></p>"
        : "";
      JnUI.modal({
        title: t("common.userMenu"),
        bodyHtml: "<p><strong>" + JnUI.esc(userLabel(cu)) + "</strong></p>" +
          (cu && cu.email ? "<p>" + JnUI.esc(cu.email) + "</p>" : "") +
          (role ? "<p>" + JnUI.esc(t("common.roleLine", { role: role })) + "</p>" : "") +
          (bl ? "<p>" + JnUI.esc(t("common.bookLine", { name: bl })) + "</p>" : "") +
          demoReset,
        primaryLabel: t("common.signOut"),
        onOk: function () {
          var done = function () {
            sessionStorage.removeItem("jn_auth");
            sessionStorage.removeItem("jn_rpc_auth");
            sessionStorage.removeItem("jn_demo_auth");
            sessionStorage.removeItem("jn_csrf");
            sessionStorage.removeItem("jn_user");
            location.reload();
          };
          if (JnData.active && JnData.active.logout) {
            JnData.active.logout().then(done).catch(done);
          } else done();
        }
      });
      setTimeout(function () {
        var rb = document.getElementById("btn-demo-reset");
        if (!rb) return;
        rb.onclick = function (e) {
          e.preventDefault();
          e.stopPropagation();
          var ok = window.confirm(t("common.demoResetConfirm", null,
            "确认重置演示账套？将删除演示业务数据并重新初始化示例场景。正式账套不可执行。"));
          if (!ok) return;
          rb.disabled = true;
          JnData.active.resetDemo().then(function () {
            JnUI.toast(t("common.demoResetOk", null, "演示账套已重置"), "ok");
            if (JnData.active.invalidate) JnData.active.invalidate();
            location.reload();
          }).catch(function (err) {
            JnUI.toast((err && err.message) || "reset failed", "err");
            rb.disabled = false;
          });
        };
      }, 0);
    };

    var hashRoute = (location.hash || "").replace(/^#/, "");
    var initial = JnRegistry.getByRoute(hashRoute);
    try {
      var savedTabs = JSON.parse(sessionStorage.getItem("jn_ui_tabs") || "[]");
      var savedActive = sessionStorage.getItem("jn_ui_active");
      tabs = sanitizeTabs(savedTabs);
      if (savedActive === "demo.center" || !JnRegistry.getRaw(savedActive)) {
        activePageId = tabs[0] || null;
      } else {
        activePageId = savedActive;
      }
      sessionStorage.setItem("jn_ui_tabs", JSON.stringify(tabs));
      if (activePageId) sessionStorage.setItem("jn_ui_active", activePageId);
      else sessionStorage.removeItem("jn_ui_active");
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

  function resumeSession() {
    JnData.useRpc("");
    return JnData.active.loadSession().then(function (user) {
      sessionUser = user;
      authed = true;
      return JnData.active.loadBook ? JnData.active.loadBook() : null;
    }).then(function (book) {
      bookInfo = book || null;
      bootShell();
    }).catch(function () {
      sessionStorage.removeItem("jn_auth");
      sessionStorage.removeItem("jn_rpc_auth");
      authed = false;
      renderLogin();
    });
  }

  window.JnApp = {
    navigate: navigate,
    pageCount: function () { return JnRegistry.count; },
    refreshLocale: onLocaleChange,
    currentUser: userDisplay,
    bookInfo: function () { return bookInfo; }
  };

  document.addEventListener("DOMContentLoaded", function () {
    if (window.JnI18n) {
      if (JnI18n.ensureReady && JnI18n.ensureReady() === 0) {
        console.error("[JnI18n] catalogs missing; UI labels may show raw keys");
      }
      if (JnI18n.catalogSize("zh-CN") > 0 && JnI18n.getLocale() !== "en" && JnI18n.getLocale() !== "zh-CN") {
        JnI18n.setLocale("zh-CN");
      }
      JnI18n.applyHtmlLang();
    }
    JnData.useRpc("");
    if (window.JnGridData) JnGridData.useRpc("");
    if (!authed) renderLogin();
    else resumeSession();
  });
})();
