/**
 * EnterpriseDataGrid - unified list grid (UI 1.3).
 * Labels via JnI18n; data via JnGridData (server-backed).
 */
(function (global) {
  function tt(key, params, fb) {
    return global.JnI18n ? JnI18n.t(key, params, fb) : (fb != null ? fb : key);
  }

  function escCsv(v) {
    var s = v == null ? "" : String(v);
    /* neutralize formula injection */
    if (/^[=+\-@]/.test(s)) s = "'" + s;
    if (/[",\n\r]/.test(s)) s = '"' + s.replace(/"/g, '""') + '"';
    return s;
  }

  function formatCell(col, row) {
    var v = row[col.field];
    if (col.formatter === "status" || (col.dataType === "ENUM" && (col.field === "status" || col.title === "Status"))) {
      return "<td>" + JnUI.statusHtml(v) + "</td>";
    }
    if (col.dataType === "MONEY") return '<td class="jn-money" style="text-align:right">' + JnUI.esc(JnUI.money(v)) + "</td>";
    if (col.dataType === "QUANTITY" || col.dataType === "INTEGER" || col.dataType === "DECIMAL") {
      return '<td class="jn-qty" style="text-align:right">' + JnUI.esc(v) + "</td>";
    }
    return "<td style=\"text-align:" + (col.alignment || "left") + "\">" + JnUI.esc(v == null ? "" : v) + "</td>";
  }

  function mount(host, opts) {
    var page = opts.page;
    var columns = (opts.columns || JnGridModel.fromPageColumns(page.columns)).map(function (c) {
      return Object.assign({}, c);
    });
    var defaultColumns = columns.map(function (c) { return Object.assign({}, c); });
    var state = JnGridModel.queryState(opts.state);
    var selected = {}; /* id -> true, page scope by default */
    var selectAllPage = false;
    var selectAllResults = false;
    var loading = false;
    var error = null;
    var lastResult = { rows: [], total: 0, page: 1, pageSize: state.pageSize, timings: {} };
    var focusRow = -1;
    var resizeState = null;
    var renderT0 = performance.now();
    var lastRenderMs = 0;
    var aggText = "";

    /* apply default saved view once */
    var defView = JnSavedViews.getDefault(page.pageId);
    if (defView) applyView(defView, true);

    function applyView(v, silent) {
      if (!v) return;
      state.filters = v.filters || state.filters;
      state.sorting = v.sorting || [];
      state.pageSize = v.pageSize || state.pageSize;
      state.page = 1;
      if (v.columnOrder && v.columnOrder.length) {
        var map = {};
        columns.forEach(function (c) { map[c.field] = c; });
        var ordered = [];
        v.columnOrder.forEach(function (f) { if (map[f]) { ordered.push(map[f]); delete map[f]; } });
        Object.keys(map).forEach(function (f) { ordered.push(map[f]); });
        columns = ordered;
      }
      if (v.visibleColumns && v.visibleColumns.length) {
        var set = {};
        v.visibleColumns.forEach(function (f) { set[f] = true; });
        columns.forEach(function (c) { c.visible = !!set[c.field]; });
      }
      if (v.columnWidths) {
        columns.forEach(function (c) {
          if (v.columnWidths[c.field]) c.width = v.columnWidths[c.field];
        });
      }
      if (!silent) JnUI.toast(tt("grid.viewApplied", { name: v.name }), "ok");
    }

    function visibleCols() {
      return columns.filter(function (c) { return c.visible !== false; });
    }

    function sortDir(field) {
      for (var i = 0; i < state.sorting.length; i++) {
        if (state.sorting[i].field === field) return state.sorting[i].dir;
      }
      return null;
    }

    function toggleSort(field) {
      var col = columns.filter(function (c) { return c.field === field; })[0];
      if (!col || col.sortable === false) return;
      var idx = -1;
      for (var i = 0; i < state.sorting.length; i++) if (state.sorting[i].field === field) idx = i;
      if (idx < 0) state.sorting.push({ field: field, dir: "asc" });
      else if (state.sorting[idx].dir === "asc") state.sorting[idx].dir = "desc";
      else state.sorting.splice(idx, 1);
      state.page = 1;
      reload();
    }

    function reload() {
      loading = true;
      error = null;
      paint();
      var t0 = performance.now();
      if (global.JnData && JnData.active && JnData.active.invalidate) {
        JnData.active.invalidate(page.collection);
      }
      JnGridData.active.query(page.collection, state, columns).then(function (res) {
        lastResult = res;
        loading = false;
        lastRenderMs = Math.round(performance.now() - t0);
        selectAllPage = false;
        /* clear page selection markers that are not in selectAllResults mode for missing ids - keep selected map */
        if (!selectAllResults) {
          /* keep existing selected ids */
        }
        return JnGridData.active.aggregate(page.collection, state, columns, buildAggSpecs());
      }).then(function (agg) {
        if (agg && agg.aggregates) {
          aggText = Object.keys(agg.aggregates).map(function (k) {
            var a = agg.aggregates[k];
            return k + "=" + (a.error || a.value);
          }).join(" | ");
        }
        paint();
      }).catch(function (e) {
        loading = false;
        error = e.message || String(e);
        paint();
      });
    }

    function buildAggSpecs() {
      var specs = [{ field: columns[0].field, fn: "COUNT" }];
      columns.forEach(function (c) {
        if (c.dataType === "MONEY" || c.dataType === "QUANTITY") {
          specs.push({ field: c.field, fn: "SUM" });
        }
      });
      return specs.slice(0, 4);
    }

    function toolbarHtml() {
      var nSel = Object.keys(selected).length;
      var scopeNote = selectAllPage
        ? tt("grid.selectedPage", { count: (lastResult.rows || []).length })
        : tt("grid.selectedCount", { count: nSel });
      return '<div class="jn-grid-toolbar jn-toolbar">' +
        "<h2 class=\"jn-card-title\" style=\"margin:0\">" + JnUI.esc(page.title) + "</h2>" +
        '<span class="spacer"></span>' +
        (page.readOnly ? "" : '<button type="button" class="jn-btn primary" data-g="new">' + JnUI.esc(tt("common.new")) + "</button>") +
        '<button type="button" class="jn-btn" data-g="filter">' + JnUI.esc(tt("grid.advancedFilter")) + "</button>" +
        '<button type="button" class="jn-btn" data-g="views">' + JnUI.esc(tt("grid.savedViews")) + "</button>" +
        '<button type="button" class="jn-btn" data-g="columns">' + JnUI.esc(tt("grid.columns")) + "</button>" +
        '<button type="button" class="jn-btn" data-g="refresh">' + JnUI.esc(tt("common.refresh")) + "</button>" +
        "</div>" +
        '<div class="jn-batch-toolbar jn-toolbar">' +
        "<span>" + JnUI.esc(scopeNote) + "</span>" +
        '<button type="button" class="jn-btn" data-g="sel-page">' + JnUI.esc(tt("grid.selectPage")) + "</button>" +
        '<button type="button" class="jn-btn" data-g="sel-clear">' + JnUI.esc(tt("grid.clearSelection")) + "</button>" +
        '<button type="button" class="jn-btn" data-g="export-page">' + JnUI.esc(tt("grid.export")) + "</button>" +
        "</div>";
    }

    function filterBarHtml() {
      var sortLabel = (state.sorting || []).map(function (s) { return s.field + " " + s.dir; }).join(", ") || tt("common.none");
      var filtCount = JnGridFilter.countConditions(state.filters);
      return '<div class="jn-filter-bar">' +
        '<div class="jn-field"><label>' + JnUI.esc(tt("grid.quickSearch")) + '</label><input class="jn-input" data-role="quick-q" placeholder="' + JnUI.esc(tt("grid.containsPlaceholder")) + '" /></div>' +
        '<div class="jn-field"><label>' + JnUI.esc(tt("grid.groupBy")) + '</label><select class="jn-select" data-role="group">' +
        '<option value="">' + JnUI.esc(tt("common.none")) + "</option>" +
        columns.map(function (c) {
          return '<option value="' + JnUI.esc(c.field) + '"' + (state.grouping && state.grouping.field === c.field ? " selected" : "") + ">" + JnUI.esc(c.title) + "</option>";
        }).join("") + "</select></div>" +
        '<div class="jn-field"><label>' + JnUI.esc(tt("grid.sort")) + '</label><div class="jn-muted" style="line-height:32px;font-size:12px">' + JnUI.esc(sortLabel) + "</div></div>" +
        '<div class="jn-field"><label>' + JnUI.esc(tt("grid.filters")) + '</label><div style="line-height:32px">' + JnUI.esc(tt("grid.filterCount", { count: filtCount })) + "</div></div>" +
        '<button type="button" class="jn-btn primary" data-g="apply-quick">' + JnUI.esc(tt("common.apply")) + "</button>" +
        "</div>";
    }

    function headerHtml() {
      var cols = visibleCols();
      var left = cols.filter(function (c) { return c.pinned === "left"; });
      var mid = cols.filter(function (c) { return c.pinned !== "left" && c.pinned !== "right"; });
      var right = cols.filter(function (c) { return c.pinned === "right"; });
      var ordered = left.concat(mid).concat(right);
      var th = '<th class="jn-grid-check"><input type="checkbox" data-g="check-page" title="Select current page only" ' + (selectAllPage ? "checked" : "") + " /></th>";
      ordered.forEach(function (c) {
        var dir = sortDir(c.field);
        var pin = c.pinned === "left" ? " jn-pin-left" : (c.pinned === "right" ? " jn-pin-right" : "");
        th += '<th class="jn-grid-th' + pin + '" data-field="' + JnUI.esc(c.field) + '" style="width:' + c.width + "px;min-width:" + c.minWidth + "px;max-width:" + c.maxWidth + 'px">' +
          '<span class="jn-th-label" data-sort="' + JnUI.esc(c.field) + '">' + JnUI.esc(c.title) +
          (dir === "asc" ? " ^" : dir === "desc" ? " v" : "") + "</span>" +
          '<span class="jn-col-resizer" data-resize="' + JnUI.esc(c.field) + '"></span></th>';
      });
      th += '<th class="jn-pin-right jn-grid-actions">' + JnUI.esc(tt("grid.actions")) + "</th>";
      return th;
    }

    function bodyHtml() {
      if (loading) return '<tr><td colspan="99">' + JnComponents.loading() + "</td></tr>";
      if (error) return '<tr><td colspan="99">' + JnComponents.error(error) + "</td></tr>";
      var cols = visibleCols();
      var left = cols.filter(function (c) { return c.pinned === "left"; });
      var mid = cols.filter(function (c) { return c.pinned !== "left" && c.pinned !== "right"; });
      var right = cols.filter(function (c) { return c.pinned === "right"; });
      var ordered = left.concat(mid).concat(right);
      var rows = lastResult.rows || [];
      if (!rows.length) return '<tr><td colspan="99"><div class="jn-empty">' + JnUI.esc(tt("common.empty")) + "</div></td></tr>";
      return rows.map(function (row, ri) {
        var checked = !!selected[row.id];
        var cells = ordered.map(function (c) {
          var pin = c.pinned === "left" ? " jn-pin-left" : (c.pinned === "right" ? " jn-pin-right" : "");
          var html = formatCell(c, row);
          return html.replace(/^<td/, '<td class="' + pin.trim() + '" data-field="' + JnUI.esc(c.field) + '"');
        }).join("");
        return '<tr tabindex="0" data-id="' + JnUI.esc(row.id) + '" data-ri="' + ri + '" class="' + (focusRow === ri ? "jn-row-focus" : "") + (checked ? " jn-row-selected" : "") + '">' +
          '<td class="jn-grid-check"><input type="checkbox" data-sel="' + JnUI.esc(row.id) + '" ' + (checked ? "checked" : "") + " /></td>" +
          cells +
          '<td class="jn-pin-right"><button type="button" class="jn-btn" data-open="' + JnUI.esc(row.id) + '">' + JnUI.esc(tt("common.open")) + "</button></td></tr>";
      }).join("");
    }

    function groupsHtml() {
      if (!lastResult.groups || !lastResult.groups.length) return "";
      return '<div class="jn-grid-groups"><strong>' + JnUI.esc(tt("grid.groups")) + ':</strong> ' +
        lastResult.groups.map(function (g) {
          return '<span class="jn-status OPEN">' + JnUI.esc(g.key) + ": " + g.count + "</span>";
        }).join(" ") + "</div>";
    }

    function pagerHtml() {
      var total = lastResult.total || 0;
      var page = lastResult.page || 1;
      var ps = lastResult.pageSize || state.pageSize;
      var pages = Math.max(1, Math.ceil(total / ps));
      var from = total === 0 ? 0 : (page - 1) * ps + 1;
      var to = Math.min(page * ps, total);
      return '<div class="jn-pagination jn-grid-pager">' +
        "<span>" + JnUI.esc(tt("common.of", { from: from, to: to, total: total })) +
        " | " + JnUI.esc(tt("grid.pageOf", { page: page, pages: pages })) + "</span>" +
        '<span class="spacer" style="flex:1"></span>' +
        '<label>' + JnUI.esc(tt("grid.pageSize")) + ' <select data-role="page-size">' +
        [10, 20, 50, 100].map(function (n) {
          return '<option value="' + n + '"' + (ps === n ? " selected" : "") + ">" + n + "</option>";
        }).join("") + "</select></label>" +
        '<button type="button" class="jn-btn" data-g="prev" ' + (page <= 1 ? "disabled" : "") + ">" + JnUI.esc(tt("common.prev")) + "</button>" +
        '<button type="button" class="jn-btn" data-g="next" ' + (page >= pages ? "disabled" : "") + ">" + JnUI.esc(tt("common.next")) + "</button>" +
        "</div>";
    }

    function paint() {
      var t0 = performance.now();
      host.innerHTML =
        '<div class="jn-card jn-enterprise-grid" data-page="' + JnUI.esc(page.pageId) + '">' +
        toolbarHtml() + filterBarHtml() + groupsHtml() +
        '<div class="jn-grid-scroll jn-table-wrap" data-grid-scroll>' +
        '<table class="jn-table jn-grid-table"><thead><tr>' + headerHtml() + "</tr></thead><tbody>" + bodyHtml() +
        "</tbody></table></div>" + pagerHtml() +
        "</div>";
      bind();
      lastRenderMs = Math.round(performance.now() - t0);
      host.setAttribute("data-grid-render-ms", String(lastRenderMs));
    }

    function openRow(id) {
      if (opts.onOpen) opts.onOpen(id);
      else if (global.JnBusinessNav) JnBusinessNav.openRecord(page, id);
    }

    function exportCsv(scope) {
      var cols = visibleCols();
      function toCsv(rows) {
        var lines = ["\uFEFF" + cols.map(function (c) { return escCsv(c.title); }).join(",")];
        rows.forEach(function (r) {
          lines.push(cols.map(function (c) { return escCsv(r[c.field]); }).join(","));
        });
        return lines.join("\r\n");
      }
      function download(name, text) {
        var blob = new Blob([text], { type: "text/csv;charset=utf-8" });
        var a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = name;
        a.click();
        URL.revokeObjectURL(a.href);
      }
      if (scope === "page") {
        download(page.pageId + "-page.csv", toCsv(lastResult.rows || []));
        JnUI.toast(tt("grid.exportDone"), "ok");
        return;
      }
      var st = Object.assign({}, state, { page: 1, pageSize: Math.min(10000, Math.max(lastResult.total, 1)) });
      JnGridData.active.query(page.collection, st, columns).then(function (res) {
        download(page.pageId + "-filtered.csv", toCsv(res.rows || []));
        JnUI.toast(tt("grid.exportDone"), "ok");
      });
    }

    function openColumnManager() {
      var backdrop = document.createElement("div");
      backdrop.className = "jn-modal-backdrop";
      function paintCm() {
        var rows = columns.map(function (c, i) {
          return '<tr data-ci="' + i + '"><td><input type="checkbox" data-vis="' + i + '" ' + (c.visible !== false ? "checked" : "") + " /></td>" +
            "<td>" + JnUI.esc(c.title) + "</td>" +
            "<td>" + JnUI.esc(c.dataType) + "</td>" +
            '<td><button type="button" class="jn-btn" data-up="' + i + '">^</button> ' +
            '<button type="button" class="jn-btn" data-dn="' + i + '">v</button></td></tr>';
        }).join("");
        backdrop.innerHTML =
          '<div class="jn-modal" style="width:min(520px,96vw)">' +
          '<div class="jn-modal-header"><strong>Column Manager</strong><span class="spacer" style="flex:1"></span>' +
          '<button type="button" class="jn-icon-btn" data-close>x</button></div>' +
          '<div class="jn-modal-body"><div class="jn-table-wrap"><table class="jn-table"><thead><tr><th>Show</th><th>Title</th><th>Type</th><th>Order</th></tr></thead><tbody>' +
          rows + "</tbody></table></div></div>" +
          '<div class="jn-modal-footer"><button type="button" class="jn-btn" data-reset>Restore defaults</button>' +
          '<button type="button" class="jn-btn primary" data-close>Done</button></div></div>';
        backdrop.querySelectorAll("[data-close]").forEach(function (b) {
          b.onclick = function () { backdrop.remove(); paint(); };
        });
        backdrop.onclick = function (e) { if (e.target === backdrop) { backdrop.remove(); paint(); } };
        backdrop.querySelectorAll("[data-vis]").forEach(function (cb) {
          cb.onchange = function () {
            columns[Number(cb.getAttribute("data-vis"))].visible = cb.checked;
          };
        });
        backdrop.querySelectorAll("[data-up]").forEach(function (b) {
          b.onclick = function () {
            var i = Number(b.getAttribute("data-up"));
            if (i <= 0) return;
            var t = columns[i - 1]; columns[i - 1] = columns[i]; columns[i] = t;
            paintCm();
          };
        });
        backdrop.querySelectorAll("[data-dn]").forEach(function (b) {
          b.onclick = function () {
            var i = Number(b.getAttribute("data-dn"));
            if (i >= columns.length - 1) return;
            var t = columns[i + 1]; columns[i + 1] = columns[i]; columns[i] = t;
            paintCm();
          };
        });
        backdrop.querySelector("[data-reset]").onclick = function () {
          columns = defaultColumns.map(function (c) { return Object.assign({}, c); });
          paintCm();
        };
      }
      document.body.appendChild(backdrop);
      paintCm();
    }

    function bind() {
      host.querySelectorAll("[data-g]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var a = btn.getAttribute("data-g");
          if (a === "refresh") reload();
          else if (a === "new" && opts.onNew) opts.onNew();
          else if (a === "filter") {
            JnFilterBuilder.open({
              columns: columns,
              filters: state.filters,
              onApply: function (f) { state.filters = f; state.page = 1; reload(); }
            });
          } else if (a === "views") {
            JnSavedViews.openManager(page.pageId, state, columns, function (msg) {
              if (msg.type === "apply") { applyView(msg.view); reload(); }
              if (msg.type === "resetDefaults") {
                columns = defaultColumns.map(function (c) { return Object.assign({}, c); });
                state = JnGridModel.queryState({ pageSize: 20 });
                reload();
              }
            });
          } else if (a === "columns") openColumnManager();
          else if (a === "apply-quick") {
            var q = host.querySelector('[data-role="quick-q"]');
            var g = host.querySelector('[data-role="group"]');
            var text = q ? q.value.trim() : "";
            state.grouping = g && g.value ? { field: g.value } : null;
            if (text) {
              /* quick contains across first string-like columns via OR group */
              var conds = columns.filter(function (c) {
                return c.dataType === "STRING" || c.dataType === "ENUM";
              }).slice(0, 4).map(function (c) {
                return { field: c.field, op: "CONTAINS", value: text };
              });
              state.filters = { logic: "OR", conditions: conds, groups: [] };
            }
            state.page = 1;
            reload();
          } else if (a === "prev") { state.page = Math.max(1, state.page - 1); reload(); }
          else if (a === "next") { state.page = state.page + 1; reload(); }
          else if (a === "sel-page") {
            selectAllResults = false;
            selectAllPage = true;
            (lastResult.rows || []).forEach(function (r) { selected[r.id] = true; });
            paint();
            JnUI.toast("Selected current page only (not all results)", "ok");
          } else if (a === "sel-clear") {
            selected = {}; selectAllPage = false; selectAllResults = false; paint();
          } else if (a === "export-page") exportCsv("page");
          else if (a === "check-page") {
            /* handled below */
          }
        });
      });
      var checkPage = host.querySelector('[data-g="check-page"]');
      if (checkPage) {
        checkPage.addEventListener("change", function () {
          selectAllResults = false;
          selectAllPage = checkPage.checked;
          (lastResult.rows || []).forEach(function (r) {
            if (checkPage.checked) selected[r.id] = true;
            else delete selected[r.id];
          });
          paint();
        });
      }
      host.querySelectorAll("[data-sel]").forEach(function (cb) {
        cb.addEventListener("change", function () {
          selectAllResults = false;
          var id = cb.getAttribute("data-sel");
          if (cb.checked) selected[id] = true;
          else delete selected[id];
          paint();
        });
      });
      host.querySelectorAll("[data-open]").forEach(function (b) {
        b.addEventListener("click", function () { openRow(b.getAttribute("data-open")); });
      });
      host.querySelectorAll("tr[data-id]").forEach(function (tr) {
        tr.addEventListener("dblclick", function () { openRow(tr.getAttribute("data-id")); });
        tr.addEventListener("click", function (e) {
          if (e.target && typeof e.target.closest === "function" && e.target.closest("button,input")) return;
          focusRow = Number(tr.getAttribute("data-ri"));
          host.querySelectorAll("tr[data-ri]").forEach(function (r) { r.classList.toggle("jn-row-focus", Number(r.getAttribute("data-ri")) === focusRow); });
        });
        tr.addEventListener("keydown", function (e) {
          if (e.isComposing || e.keyCode === 229) return;
          if (e.target.tagName === "INPUT") return;
          var ri = Number(tr.getAttribute("data-ri"));
          if (e.key === "ArrowDown") {
            e.preventDefault();
            var next = host.querySelector('tr[data-ri="' + (ri + 1) + '"]');
            if (next) next.focus();
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            var prev = host.querySelector('tr[data-ri="' + (ri - 1) + '"]');
            if (prev) prev.focus();
          } else if (e.key === "Enter") {
            e.preventDefault();
            openRow(tr.getAttribute("data-id"));
          } else if (e.key === "Escape") {
            tr.blur();
          } else if (e.key === " " || e.key === "Spacebar") {
            e.preventDefault();
            var id = tr.getAttribute("data-id");
            if (selected[id]) delete selected[id]; else selected[id] = true;
            selectAllResults = false;
            paint();
          }
        });
      });
      host.querySelectorAll("[data-sort]").forEach(function (el) {
        el.addEventListener("click", function () { toggleSort(el.getAttribute("data-sort")); });
      });
      host.querySelectorAll("[data-resize]").forEach(function (h) {
        h.addEventListener("mousedown", function (e) {
          e.preventDefault();
          var field = h.getAttribute("data-resize");
          var col = columns.filter(function (c) { return c.field === field; })[0];
          if (!col) return;
          resizeState = { field: field, startX: e.clientX, startW: col.width };
          function onMove(ev) {
            if (!resizeState) return;
            var dx = ev.clientX - resizeState.startX;
            var w = resizeState.startW + dx;
            w = Math.max(col.minWidth, Math.min(col.maxWidth, w));
            col.width = w;
            var th = host.querySelector('th[data-field="' + field + '"]');
            if (th) th.style.width = w + "px";
          }
          function onUp() {
            resizeState = null;
            document.removeEventListener("mousemove", onMove);
            document.removeEventListener("mouseup", onUp);
          }
          document.addEventListener("mousemove", onMove);
          document.addEventListener("mouseup", onUp);
        });
      });
      var ps = host.querySelector('[data-role="page-size"]');
      if (ps) ps.addEventListener("change", function () {
        state.pageSize = Number(ps.value) || 20;
        state.page = 1;
        reload();
      });
      /* context menu */
      var scroll = host.querySelector("[data-grid-scroll]");
      if (scroll) {
        scroll.addEventListener("contextmenu", function (e) {
          var tr = (e.target && typeof e.target.closest === "function") ? e.target.closest("tr[data-id]") : null;
          if (!tr) return;
          e.preventDefault();
          var id = tr.getAttribute("data-id");
          var menu = document.createElement("div");
          menu.className = "jn-ctx-menu";
          menu.style.left = e.clientX + "px";
          menu.style.top = e.clientY + "px";
          menu.innerHTML = '<button type="button" data-a="open">Open</button><button type="button" data-a="drill">Business drill-down</button><button type="button" data-a="sel">Toggle select</button>';
          document.body.appendChild(menu);
          function close() { menu.remove(); document.removeEventListener("click", close); }
          menu.querySelector('[data-a="open"]').onclick = function () { openRow(id); close(); };
          menu.querySelector('[data-a="drill"]').onclick = function () {
            if (JnBusinessNav) JnBusinessNav.drillFromRow(page, id);
            close();
          };
          menu.querySelector('[data-a="sel"]').onclick = function () {
            if (selected[id]) delete selected[id]; else selected[id] = true;
            paint(); close();
          };
          setTimeout(function () { document.addEventListener("click", close); }, 0);
        });
      }
    }

    reload();
    return {
      reload: reload,
      getState: function () { return state; },
      getColumns: function () { return columns; },
      initialRenderMs: function () { return Math.round(performance.now() - renderT0); }
    };
  }

  global.JnEnterpriseGrid = { mount: mount };
})(window);
