// Author: 张晓达
/**
 * ButtonActionRegistry - maps actionable UI controls to handlers (UI 1.6).
 * Used by acceptance scans to ensure business buttons are not dead.
 */
(function (global) {
  var ACTIONS = [
    { id: "shell.density", selector: "#btn-density", page: "shell", category: "shell", description: "Toggle density", required: true },
    { id: "shell.demoCenter", selector: "#btn-demo-center", page: "shell", category: "shell", description: "Open Demo Center", required: true },
    { id: "shell.resetDemo", selector: "#btn-reset-demo", page: "shell", category: "shell", description: "Reset Demo Data", required: true },
    { id: "shell.notif", selector: "#btn-notif", page: "shell", category: "shell", description: "Notifications", required: true },
    { id: "shell.collapse", selector: "#btn-collapse", page: "shell", category: "shell", description: "Collapse sidebar", required: true },
    { id: "grid.new", selector: '[data-g="new"]', page: "enterpriseGrid", category: "grid", description: "New record", required: true },
    { id: "grid.filter", selector: '[data-g="filter"]', page: "enterpriseGrid", category: "grid", description: "Advanced filter", required: true },
    { id: "grid.refresh", selector: '[data-g="refresh"]', page: "enterpriseGrid", category: "grid", description: "Refresh grid", required: true },
    { id: "grid.columns", selector: '[data-g="columns"]', page: "enterpriseGrid", category: "grid", description: "Column manager", required: true },
    { id: "grid.applyQuick", selector: '[data-g="apply-quick"]', page: "enterpriseGrid", category: "grid", description: "Apply quick filter", required: true },
    { id: "list.new", selector: '[data-action="new"]', page: "list", category: "list", description: "New", required: true },
    { id: "list.refresh", selector: '[data-action="refresh"]', page: "list", category: "list", description: "Refresh", required: true },
    { id: "list.applyFilter", selector: '[data-action="apply-filter"]', page: "list", category: "list", description: "Apply filter", required: true },
    { id: "doc.save", selector: '[data-action="save"]', page: "documentEditor", category: "document", description: "Save document", required: true },
    { id: "settings.save", selector: "[data-save]", page: "sys.settings", category: "settings", description: "Save settings", required: true },
    { id: "demo.startSales", selector: '[data-start="sales"]', page: "demo.center", category: "demo", description: "Start sales demo", required: true },
    { id: "mrp.run", selector: "[data-run]", page: "mfg.mrp", category: "mfg", description: "Run MRP", required: true },
    { id: "bom.save", selector: "[data-save]", page: "mfg.bomEditor", category: "mfg", description: "Save BOM", required: true }
  ];

  function scanVisible(root) {
    root = root || document;
    var found = [];
    var missing = [];
    ACTIONS.forEach(function (a) {
      var el = root.querySelector(a.selector);
      if (!el) {
        if (a.required && a.page === "shell") missing.push(a.id + ":not-in-dom");
        return;
      }
      var disabled = el.disabled || el.getAttribute("aria-disabled") === "true";
      var hasHandler = !!(el.onclick || el.getAttribute("data-g") || el.getAttribute("data-action") || el.getAttribute("data-start") || el.getAttribute("data-save") || el.getAttribute("data-run") || el.id);
      found.push({
        id: a.id,
        page: a.page,
        category: a.category,
        description: a.description,
        disabled: disabled,
        hasHandlerHint: hasHandler,
        text: (el.textContent || "").trim().slice(0, 40)
      });
      if (a.required && !hasHandler && !disabled) missing.push(a.id + ":no-handler-hint");
    });
    return { registry: ACTIONS.slice(), found: found, missing: missing };
  }

  /** Probe: click must produce modal/toast/navigation/DOM change within timeout. */
  function probeClick(el) {
    if (!el || el.disabled) return { ok: false, reason: "missing-or-disabled" };
    var before = document.body.innerHTML.length;
    var toastBefore = (document.querySelectorAll(".jn-toast").length);
    var modalBefore = document.querySelectorAll(".jn-modal-backdrop,.jn-doc-editor-backdrop,[data-filter-builder]").length;
    try { el.click(); } catch (e) { return { ok: false, reason: e.message }; }
    var toastAfter = document.querySelectorAll(".jn-toast").length;
    var modalAfter = document.querySelectorAll(".jn-modal-backdrop,.jn-doc-editor-backdrop,[data-filter-builder]").length;
    var after = document.body.innerHTML.length;
    var ok = toastAfter > toastBefore || modalAfter > modalBefore || after !== before;
    return { ok: ok, reason: ok ? "responded" : "no-visible-effect" };
  }

  global.JnButtonActions = {
    list: function () { return ACTIONS.slice(); },
    scanVisible: scanVisible,
    probeClick: probeClick
  };
})(window);
