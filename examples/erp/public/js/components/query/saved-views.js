/**
 * SavedViewManager - localStorage DEMO personal views (not formal user config DB).
 */
(function (global) {
  var KEY = "jn_saved_views_v13";

  function loadAll() {
    try { return JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { return {}; }
  }
  function saveAll(map) {
    try { localStorage.setItem(KEY, JSON.stringify(map)); } catch (e) {}
  }

  function list(pageId) {
    var map = loadAll();
    return (map[pageId] || []).slice();
  }

  function create(pageId, view) {
    var map = loadAll();
    if (!map[pageId]) map[pageId] = [];
    var v = {
      id: "SV-" + Date.now(),
      pageId: pageId,
      name: view.name || "Untitled view",
      filters: view.filters || { logic: "AND", conditions: [], groups: [] },
      sorting: view.sorting || [],
      visibleColumns: view.visibleColumns || [],
      columnOrder: view.columnOrder || [],
      columnWidths: view.columnWidths || {},
      pageSize: view.pageSize || 20,
      isDefault: !!view.isDefault,
      demo: true
    };
    if (v.isDefault) map[pageId].forEach(function (x) { x.isDefault = false; });
    map[pageId].push(v);
    saveAll(map);
    return v;
  }

  function update(pageId, id, patch) {
    var map = loadAll();
    var arr = map[pageId] || [];
    for (var i = 0; i < arr.length; i++) {
      if (arr[i].id === id) {
        Object.keys(patch || {}).forEach(function (k) { arr[i][k] = patch[k]; });
        if (patch && patch.isDefault) arr.forEach(function (x) { if (x.id !== id) x.isDefault = false; });
        saveAll(map);
        return arr[i];
      }
    }
    return null;
  }

  function remove(pageId, id) {
    var map = loadAll();
    map[pageId] = (map[pageId] || []).filter(function (v) { return v.id !== id; });
    saveAll(map);
  }

  function getDefault(pageId) {
    var arr = list(pageId);
    for (var i = 0; i < arr.length; i++) if (arr[i].isDefault) return arr[i];
    return null;
  }

  function openManager(pageId, currentState, columns, onApply) {
    var backdrop = document.createElement("div");
    backdrop.className = "jn-modal-backdrop";
    function paint() {
      var views = list(pageId);
      var rows = views.map(function (v) {
        return '<tr data-id="' + JnUI.esc(v.id) + '"><td>' + JnUI.esc(v.name) +
          (v.isDefault ? ' <span class="jn-status OPEN">DEFAULT</span>' : "") +
          ' <em style="color:var(--jn-color-text-muted)">(DEMO local)</em></td>' +
          '<td><button type="button" class="jn-btn" data-apply="' + JnUI.esc(v.id) + '">Apply</button> ' +
          '<button type="button" class="jn-btn" data-rename="' + JnUI.esc(v.id) + '">Rename</button> ' +
          '<button type="button" class="jn-btn" data-def="' + JnUI.esc(v.id) + '">Set default</button> ' +
          '<button type="button" class="jn-btn danger" data-del="' + JnUI.esc(v.id) + '">Delete</button></td></tr>';
      }).join("") || '<tr><td colspan="2"><div class="jn-empty">No saved views</div></td></tr>';
      backdrop.innerHTML =
        '<div class="jn-modal" style="width:min(640px,96vw)">' +
        '<div class="jn-modal-header"><strong>Saved Views</strong><span class="spacer" style="flex:1"></span>' +
        '<button type="button" class="jn-icon-btn" data-close>x</button></div>' +
        '<div class="jn-modal-body">' +
        '<div class="jn-demo-save-note">Views persist in browser localStorage only - NOT formal user preference DB.</div>' +
        '<div class="jn-toolbar"><button type="button" class="jn-btn primary" data-save-current>Save current as view</button>' +
        '<button type="button" class="jn-btn" data-reset-default>Restore layout defaults</button></div>' +
        '<div class="jn-table-wrap"><table class="jn-table"><thead><tr><th>Name</th><th>Actions</th></tr></thead><tbody>' +
        rows + "</tbody></table></div></div>" +
        '<div class="jn-modal-footer"><button type="button" class="jn-btn" data-close>Close</button></div></div>';
      backdrop.querySelectorAll("[data-close]").forEach(function (b) { b.onclick = function () { backdrop.remove(); }; });
      backdrop.onclick = function (e) { if (e.target === backdrop) backdrop.remove(); };
      var saveBtn = backdrop.querySelector("[data-save-current]");
      if (saveBtn) saveBtn.onclick = function () {
        var name = window.prompt("View name (DEMO localStorage)");
        if (!name) return;
        var vis = columns.filter(function (c) { return c.visible !== false; }).map(function (c) { return c.field; });
        var order = columns.map(function (c) { return c.field; });
        var widths = {};
        columns.forEach(function (c) { widths[c.field] = c.width; });
        create(pageId, {
          name: name,
          filters: currentState.filters,
          sorting: currentState.sorting,
          visibleColumns: vis,
          columnOrder: order,
          columnWidths: widths,
          pageSize: currentState.pageSize
        });
        JnUI.toast("DEMO view saved to localStorage", "ok");
        paint();
      };
      var resetBtn = backdrop.querySelector("[data-reset-default]");
      if (resetBtn) resetBtn.onclick = function () {
        if (onApply) onApply({ type: "resetDefaults" });
        backdrop.remove();
      };
      backdrop.querySelectorAll("[data-apply]").forEach(function (b) {
        b.onclick = function () {
          var id = b.getAttribute("data-apply");
          var v = list(pageId).filter(function (x) { return x.id === id; })[0];
          if (v && onApply) onApply({ type: "apply", view: v });
          backdrop.remove();
        };
      });
      backdrop.querySelectorAll("[data-rename]").forEach(function (b) {
        b.onclick = function () {
          var id = b.getAttribute("data-rename");
          var name = window.prompt("Rename view");
          if (!name) return;
          update(pageId, id, { name: name });
          paint();
        };
      });
      backdrop.querySelectorAll("[data-def]").forEach(function (b) {
        b.onclick = function () {
          update(pageId, b.getAttribute("data-def"), { isDefault: true });
          paint();
        };
      });
      backdrop.querySelectorAll("[data-del]").forEach(function (b) {
        b.onclick = function () {
          if (!window.confirm("Delete DEMO view?")) return;
          remove(pageId, b.getAttribute("data-del"));
          paint();
        };
      });
    }
    document.body.appendChild(backdrop);
    paint();
  }

  global.JnSavedViews = {
    list: list,
    create: create,
    update: update,
    remove: remove,
    getDefault: getDefault,
    openManager: openManager,
    STORAGE_KEY: KEY
  };
})(window);
