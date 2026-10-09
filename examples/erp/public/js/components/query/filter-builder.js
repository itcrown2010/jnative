// Author: 张晓达
/**
 * FilterBuilder modal - AND/OR nested groups with type-limited operators.
 */
(function (global) {
  var OPS = JnGridModel.OPS;
  var PRESETS = [
    { id: "today", label: "Today" },
    { id: "yesterday", label: "Yesterday" },
    { id: "thisWeek", label: "This week" },
    { id: "thisMonth", label: "This month" },
    { id: "lastMonth", label: "Last month" },
    { id: "thisQuarter", label: "This quarter" },
    { id: "thisYear", label: "This year" },
    { id: "custom", label: "Custom range" }
  ];

  function clone(v) { return JSON.parse(JSON.stringify(v)); }

  function open(opts) {
    var columns = (opts.columns || []).filter(function (c) { return c.filterable !== false; });
    var group = clone(opts.filters || { logic: "AND", conditions: [], groups: [] });
    var backdrop = document.createElement("div");
    backdrop.className = "jn-modal-backdrop";
    backdrop.setAttribute("data-filter-builder", "1");

    function fieldCol(field) {
      for (var i = 0; i < columns.length; i++) if (columns[i].field === field) return columns[i];
      return columns[0];
    }

    function opsFor(field) {
      var c = fieldCol(field);
      return JnGridModel.OPS_BY_TYPE[c.dataType] || [OPS.EQ];
    }

    function paint() {
      var depthNote = "Max depth " + JnGridModel.MAX_FILTER_DEPTH + ", max conditions " + JnGridModel.MAX_FILTER_CONDITIONS;
      backdrop.innerHTML =
        '<div class="jn-modal jn-filter-builder" style="width:min(780px,96vw);max-height:92vh">' +
        '<div class="jn-modal-header"><strong>Advanced Filter</strong><span class="spacer" style="flex:1"></span>' +
        '<button type="button" class="jn-icon-btn" data-close>x</button></div>' +
        '<div class="jn-modal-body">' +
        '<div class="jn-demo-save-note">FilterBuilder DEMO - timezone = local calendar day (' +
        JnUI.esc((JnGridFilter.datePresetRange("today") || {}).tz || "local") + "). " + depthNote + "</div>" +
        renderGroup(group, 0, "root") +
        "</div>" +
        '<div class="jn-modal-footer">' +
        '<button type="button" class="jn-btn" data-clear>Clear</button>' +
        '<button type="button" class="jn-btn" data-close>Cancel</button>' +
        '<button type="button" class="jn-btn primary" data-apply>Apply</button></div></div>';
      bind();
    }

    function renderCondition(cond, path) {
      var col = fieldCol(cond.field);
      var ops = opsFor(cond.field);
      var fieldOpts = columns.map(function (c) {
        return '<option value="' + JnUI.esc(c.field) + '"' + (c.field === cond.field ? " selected" : "") + ">" + JnUI.esc(c.title) + "</option>";
      }).join("");
      var opOpts = ops.map(function (o) {
        return '<option value="' + o + '"' + (cond.op === o ? " selected" : "") + ">" + o + "</option>";
      }).join("");
      var isDate = col.dataType === "DATE" || col.dataType === "DATETIME";
      var isMoney = col.dataType === "MONEY";
      var valueHtml = "";
      if (cond.op === OPS.IS_NULL || cond.op === OPS.IS_NOT_NULL) {
        valueHtml = "<span class=\"jn-muted\">(no value)</span>";
      } else if (isDate) {
        var presetOpts = PRESETS.map(function (p) {
          return '<option value="' + p.id + '"' + ((cond.datePreset || "custom") === p.id ? " selected" : "") + ">" + p.label + "</option>";
        }).join("");
        valueHtml = '<select class="jn-select" data-path="' + path + '" data-k="datePreset">' + presetOpts + "</select>";
        if (!cond.datePreset || cond.datePreset === "custom") {
          valueHtml += ' <input class="jn-input" type="date" data-path="' + path + '" data-k="value" value="' + JnUI.esc(cond.value || "") + '" />';
          if (cond.op === OPS.BETWEEN) {
            valueHtml += ' <input class="jn-input" type="date" data-path="' + path + '" data-k="value2" value="' + JnUI.esc(cond.value2 || "") + '" />';
          }
        }
      } else if (cond.op === OPS.BETWEEN) {
        valueHtml = '<input class="jn-input" data-path="' + path + '" data-k="value" value="' + JnUI.esc(cond.value == null ? "" : cond.value) + '" placeholder="' + (isMoney ? "amount" : "from") + '" />' +
          ' <input class="jn-input" data-path="' + path + '" data-k="value2" value="' + JnUI.esc(cond.value2 == null ? "" : cond.value2) + '" placeholder="to" />';
      } else {
        valueHtml = '<input class="jn-input" data-path="' + path + '" data-k="value" value="' + JnUI.esc(cond.value == null ? "" : cond.value) + '"' +
          (isMoney ? ' placeholder="e.g. 100.00 (cents compare)"' : "") + " />";
      }
      return '<div class="jn-filter-cond" data-path="' + path + '">' +
        '<select class="jn-select" data-path="' + path + '" data-k="field">' + fieldOpts + "</select>" +
        '<select class="jn-select" data-path="' + path + '" data-k="op">' + opOpts + "</select>" +
        '<div class="jn-filter-vals">' + valueHtml + "</div>" +
        '<button type="button" class="jn-btn danger" data-del-cond="' + path + '">x</button></div>';
    }

    function renderGroup(g, depth, path) {
      var conds = (g.conditions || []).map(function (c, i) {
        return renderCondition(c, path + ".c" + i);
      }).join("");
      var nested = (g.groups || []).map(function (ng, i) {
        return renderGroup(ng, depth + 1, path + ".g" + i);
      }).join("");
      return '<div class="jn-filter-group" data-path="' + path + '">' +
        '<div class="jn-toolbar"><strong>Group</strong>' +
        '<select class="jn-select" data-path="' + path + '" data-k="logic" style="width:90px">' +
        '<option value="AND"' + (g.logic === "AND" ? " selected" : "") + ">AND</option>" +
        '<option value="OR"' + (g.logic === "OR" ? " selected" : "") + ">OR</option></select>" +
        '<button type="button" class="jn-btn" data-add-cond="' + path + '">+ Condition</button>' +
        (depth < JnGridModel.MAX_FILTER_DEPTH - 1 ? '<button type="button" class="jn-btn" data-add-group="' + path + '">+ Nested</button>' : "") +
        "</div>" + conds + nested + "</div>";
    }

    function resolvePath(path) {
      if (path === "root") return { parent: null, node: group, kind: "group" };
      var parts = path.split(".").slice(1);
      var node = group;
      var parent = null;
      var kind = "group";
      var idx = -1;
      for (var i = 0; i < parts.length; i++) {
        var p = parts[i];
        parent = node;
        if (p.charAt(0) === "c") {
          idx = Number(p.slice(1));
          node = parent.conditions[idx];
          kind = "cond";
        } else if (p.charAt(0) === "g") {
          idx = Number(p.slice(1));
          node = parent.groups[idx];
          kind = "group";
        }
      }
      return { parent: parent, node: node, kind: kind, idx: idx };
    }

    function syncFromDom() {
      backdrop.querySelectorAll("[data-k]").forEach(function (el) {
        var path = el.getAttribute("data-path");
        var k = el.getAttribute("data-k");
        var ref = resolvePath(path);
        if (!ref.node) return;
        var v = el.value;
        if (k === "datePreset") {
          ref.node.datePreset = v === "custom" ? null : v;
          if (v !== "custom") {
            var r = JnGridFilter.datePresetRange(v);
            if (r) { ref.node.value = r.from; ref.node.value2 = r.to; ref.node.op = OPS.BETWEEN; }
          }
        } else {
          ref.node[k] = v;
          if (k === "field") {
            var ops = opsFor(v);
            if (ops.indexOf(ref.node.op) < 0) ref.node.op = ops[0];
          }
        }
      });
    }

    function bind() {
      backdrop.querySelectorAll("[data-close]").forEach(function (b) {
        b.onclick = function () { backdrop.remove(); };
      });
      backdrop.onclick = function (e) { if (e.target === backdrop) backdrop.remove(); };
      backdrop.querySelector("[data-clear]").onclick = function () {
        group = { logic: "AND", conditions: [], groups: [] };
        paint();
      };
      backdrop.querySelector("[data-apply]").onclick = function () {
        syncFromDom();
        if (JnGridFilter.countConditions(group) > JnGridModel.MAX_FILTER_CONDITIONS) {
          JnUI.toast("Too many filter conditions (max " + JnGridModel.MAX_FILTER_CONDITIONS + ")", "err");
          return;
        }
        if (opts.onApply) opts.onApply(clone(group));
        backdrop.remove();
      };
      backdrop.querySelectorAll("[data-add-cond]").forEach(function (b) {
        b.onclick = function () {
          syncFromDom();
          if (JnGridFilter.countConditions(group) >= JnGridModel.MAX_FILTER_CONDITIONS) {
            JnUI.toast("Max conditions reached", "err");
            return;
          }
          var ref = resolvePath(b.getAttribute("data-add-cond"));
          if (!ref.node.conditions) ref.node.conditions = [];
          var f = columns[0].field;
          ref.node.conditions.push({ field: f, op: opsFor(f)[0], value: "", value2: "" });
          paint();
        };
      });
      backdrop.querySelectorAll("[data-add-group]").forEach(function (b) {
        b.onclick = function () {
          syncFromDom();
          var ref = resolvePath(b.getAttribute("data-add-group"));
          if (!ref.node.groups) ref.node.groups = [];
          ref.node.groups.push({ logic: "AND", conditions: [], groups: [] });
          paint();
        };
      });
      backdrop.querySelectorAll("[data-del-cond]").forEach(function (b) {
        b.onclick = function () {
          syncFromDom();
          var ref = resolvePath(b.getAttribute("data-del-cond"));
          if (ref.parent && ref.kind === "cond") {
            ref.parent.conditions.splice(ref.idx, 1);
            paint();
          }
        };
      });
      backdrop.querySelectorAll("[data-k]").forEach(function (el) {
        el.addEventListener("change", function () {
          syncFromDom();
          if (el.getAttribute("data-k") === "field" || el.getAttribute("data-k") === "op" || el.getAttribute("data-k") === "datePreset") paint();
        });
      });
    }

    document.body.appendChild(backdrop);
    paint();
    return backdrop;
  }

  global.JnFilterBuilder = { open: open, PRESETS: PRESETS };
})(window);
