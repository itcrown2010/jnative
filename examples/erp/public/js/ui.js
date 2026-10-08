/** Shared UI helpers / components */
(function (global) {
  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function money(n) {
    if (global.JnI18n) return global.JnI18n.formatMoney(n, "USD");
    var v = Number(n) || 0;
    return "USD " + v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  function qty(n) {
    if (global.JnI18n) return global.JnI18n.formatNumber(n);
    return String(Number(n) || 0);
  }
  function statusHtml(s) {
    var v = s || "OPEN";
    var label = global.JnI18n ? global.JnI18n.statusLabel(v) : v;
    return '<span class="jn-status ' + esc(v) + '">' + esc(label) + "</span>";
  }
  function cell(col, row) {
    var v = row[col.key];
    if (col.type === "money") return '<td class="jn-money">' + esc(money(v)) + "</td>";
    if (col.type === "qty") return '<td class="jn-qty">' + esc(qty(v)) + "</td>";
    if (col.type === "status") return "<td>" + statusHtml(v) + "</td>";
    return "<td>" + esc(v) + "</td>";
  }
  function toast(msg, kind) {
    var host = document.getElementById("jn-toast-host");
    if (!host) return;
    var el = document.createElement("div");
    el.className = "jn-toast " + (kind || "");
    el.textContent = msg;
    host.appendChild(el);
    setTimeout(function () { el.remove(); }, 2800);
  }
  function modal(opts) {
    var backdrop = document.createElement("div");
    backdrop.className = "jn-modal-backdrop";
    backdrop.innerHTML =
      '<div class="jn-modal" role="dialog">' +
      '<div class="jn-modal-header"><strong>' + esc(opts.title || "Dialog") + '</strong>' +
      '<span class="spacer" style="flex:1"></span><button type="button" class="jn-icon-btn" data-close>x</button></div>' +
      '<div class="jn-modal-body">' + (opts.bodyHtml || "") + "</div>" +
      '<div class="jn-modal-footer">' +
      '<button type="button" class="jn-btn" data-close>' +
      esc((global.JnI18n && global.JnI18n.t("common.cancel")) || "Cancel") + "</button>" +
      (opts.primaryLabel ? '<button type="button" class="jn-btn primary" data-ok>' + esc(opts.primaryLabel) + "</button>" : "") +
      "</div></div>";
    function close() { backdrop.remove(); }
    backdrop.addEventListener("click", function (e) {
      if (e.target === backdrop || (e.target.getAttribute && e.target.getAttribute("data-close") != null)) close();
    });
    backdrop.addEventListener("keydown", function (e) {
      if (e.key === "Escape") close();
    });
    var ok = backdrop.querySelector("[data-ok]");
    if (ok) ok.addEventListener("click", function () {
      var allowClose = true;
      if (opts.onOk) {
        var result = opts.onOk(backdrop);
        if (result === false) allowClose = false;
      }
      if (allowClose) close();
    });
    document.body.appendChild(backdrop);
    var focusable = backdrop.querySelector("input,button,select,textarea,[tabindex]");
    if (focusable) try { focusable.focus(); } catch (e) {}
    return backdrop;
  }

  function drawer(opts) {
    var backdrop = document.createElement("div");
    backdrop.className = "jn-drawer-backdrop";
    var panel = document.createElement("div");
    panel.className = "jn-drawer";
    panel.innerHTML =
      '<div class="jn-modal-header"><strong>' + esc(opts.title || "Details") + '</strong>' +
      '<span class="spacer" style="flex:1"></span><button type="button" class="jn-icon-btn" data-close>x</button></div>' +
      '<div class="jn-modal-body" style="flex:1;overflow:auto">' + (opts.bodyHtml || "") + "</div>";
    function close() { backdrop.remove(); panel.remove(); }
    backdrop.addEventListener("click", close);
    panel.querySelector("[data-close]").addEventListener("click", close);
    document.body.appendChild(backdrop);
    document.body.appendChild(panel);
    return panel;
  }

  function confirmDialog(opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      modal({
        title: opts.title || "Confirm",
        bodyHtml: "<p>" + esc(opts.message || "Are you sure?") + "</p>" +
          (opts.demoNote ? '<div class="jn-demo-save-note">' + esc(opts.demoNote) + "</div>" : ""),
        primaryLabel: opts.okLabel || "Confirm",
        onOk: function () { resolve(true); }
      });
      /* Cancel path: backdrop close => treat as false after tick if still pending */
      setTimeout(function () {
        var bd = document.querySelector(".jn-modal-backdrop:last-child");
        if (!bd) return;
        bd.addEventListener("click", function (e) {
          if (e.target === bd || e.target.getAttribute("data-close") != null) resolve(false);
        });
      }, 0);
    });
  }

  global.JnUI = {
    esc: esc,
    money: money,
    qty: qty,
    statusHtml: statusHtml,
    cell: cell,
    toast: toast,
    modal: modal,
    drawer: drawer,
    confirmDialog: confirmDialog
  };
})(window);
