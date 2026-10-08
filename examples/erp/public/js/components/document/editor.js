/**
 * DocumentEditor - Enterprise document editor (UI 1.2).
 * Modes: CREATE | EDIT | VIEW
 */
(function (global) {
  var CURRENCIES = ["CNY", "USD", "EUR"];
  var sessionDraftKey = "jn_doc_drafts_v12";

  function loadSessionDrafts() {
    try { return JSON.parse(sessionStorage.getItem(sessionDraftKey) || "{}"); } catch (e) { return {}; }
  }
  function saveSessionDrafts(map) {
    try { sessionStorage.setItem(sessionDraftKey, JSON.stringify(map)); } catch (e) {}
  }

  function today() {
    var d = new Date();
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return d.getFullYear() + "-" + (m < 10 ? "0" : "") + m + "-" + (day < 10 ? "0" : "") + day;
  }

  function normalizeRecord(def, rec) {
    rec = rec ? JSON.parse(JSON.stringify(rec)) : {};
    if (!rec.id) rec.id = "NEW-" + Date.now();
    if (!rec.status) rec.status = "DRAFT";
    if (!rec.date) rec.date = today();
    if (!rec.currency) rec.currency = "CNY";
    if (!rec.lines) rec.lines = [];
    if (!rec.attachments) rec.attachments = [];
    if (!rec.approvals) {
      rec.approvals = [
        { who: "Demo User", when: today() + " 09:00", result: "CREATED", note: "DEMO" }
      ];
    }
    if (!rec.audit) {
      rec.audit = [
        { when: today() + " 09:00", user: "Demo User", action: "CREATE", detail: "DEMO mock" }
      ];
    }
    if (!rec.references) {
      rec.references = { source: rec.sourceDocNo || "", targets: [] };
    }
    /* migrate legacy total/price fields to cents */
    (rec.lines || []).forEach(function (l, i) {
      if (l.unitPriceCents == null && l.price != null) l.unitPriceCents = Math.round(Number(l.price) * 100);
      if (l.lineAmountCents == null && l.amount != null) l.lineAmountCents = Math.round(Number(l.amount) * 100);
      if (l.discountBps == null) l.discountBps = 0;
      if (l.taxBps == null) l.taxBps = 1300; /* DEMO 13% */
      if (!l.uom) l.uom = "EA";
      if (!l.id) l.id = "L" + i + "-" + Date.now();
    });
    if (rec.totalCents == null && rec.total != null) rec.totalCents = Math.round(Number(rec.total) * 100);
    if (rec.partnerId == null) {
      if (rec.customerId) rec.partnerId = rec.customerId;
      if (rec.supplierId) rec.partnerId = rec.supplierId;
    }
    if (!rec.partnerName) {
      rec.partnerName = rec.customerName || rec.supplierName || "";
    }
    JnDocumentCalc.calcDocument(rec, def);
    return rec;
  }

  function canEdit(mode, status) {
    if (mode === "VIEW") return false;
    if (mode === "CREATE") return true;
    return status === "DRAFT" || status === "SUBMITTED";
  }

  function actionEnabled(action, mode, status, dirty) {
    if (action === "close") return true;
    if (mode === "VIEW") return action === "copy" || action === "close";
    if (action === "save") return canEdit(mode, status);
    if (action === "submit") return canEdit(mode, status) && status === "DRAFT";
    if (action === "approve") return status === "SUBMITTED";
    if (action === "reject") return status === "SUBMITTED";
    if (action === "cancel") return status !== "CANCELLED" && status !== "APPROVED";
    if (action === "copy") return true;
    return false;
  }

  function open(opts) {
    var def = opts.def;
    var mode = opts.mode || "EDIT";
    var rec = normalizeRecord(def, opts.record);
    if (mode === "EDIT" && !canEdit("EDIT", rec.status)) mode = "VIEW";
    var dirty = false;
    var baseline = JSON.stringify(rec);
    var editable = canEdit(mode, rec.status);
    var focusCell = { r: 0, c: 0 };
    var startMs = performance.now();

    var backdrop = document.createElement("div");
    backdrop.className = "jn-modal-backdrop jn-doc-editor-backdrop";
    backdrop.innerHTML = '<div class="jn-modal jn-doc-editor" role="dialog" style="width:min(1100px,98vw);max-height:94vh"></div>';
    var shell = backdrop.querySelector(".jn-doc-editor");

    function markDirty() {
      dirty = JSON.stringify(rec) !== baseline;
      var el = shell.querySelector("[data-dirty]");
      if (el) el.style.display = dirty ? "inline-flex" : "none";
    }

    function close(force) {
      if (!force && dirty) {
        var leave = window.confirm("Unsaved changes will be lost. Leave without saving DEMO draft?");
        if (!leave) return;
      }
      backdrop.remove();
      document.removeEventListener("keydown", onGlobalKey, true);
      if (opts.onClose) opts.onClose();
    }

    function validate() {
      var errors = [];
      (def.validationRules || []).forEach(function (rule) {
        if (rule.scope === "header" && rule.rule === "required") {
          if (!rec[rule.field]) errors.push({ scope: "header", field: rule.field, message: "Required" });
        }
      });
      if (!rec.lines || !rec.lines.length) errors.push({ scope: "document", message: "At least one line required" });
      (rec.lines || []).forEach(function (l, i) {
        (def.validationRules || []).forEach(function (rule) {
          if (rule.scope !== "lines") return;
          if (rule.rule === "required" && !l[rule.field]) {
            errors.push({ scope: "line", row: i, field: rule.field, message: "Required" });
          }
          if (rule.rule === "positive" && !(Number(l[rule.field]) > 0)) {
            errors.push({ scope: "line", row: i, field: rule.field, message: "Must be > 0" });
          }
        });
      });
      return errors;
    }

    function headerHtml() {
      return '<div class="jn-doc-header jn-form-grid">' + def.headerFields.map(function (f) {
        var val = rec[f.key];
        if (f.key === "partnerId") val = rec.partnerName || "";
        if (f.key === "warehouseId") val = rec.warehouseCode || rec.warehouseId || "";
        if (f.key === "employeeId") val = rec.employeeName || rec.employeeId || "";
        var ro = !editable || f.readOnly || mode === "VIEW";
        var ctrl = "";
        if (f.type === "partner" || f.type === "warehouse" || f.type === "employee" || f.type === "contact") {
          ctrl = '<div class="jn-selector-field"><input class="jn-input" data-h="' + f.key + '" value="' + JnUI.esc(val || "") + '" ' + (ro ? "readonly" : "readonly") + " />" +
            (ro ? "" : '<button type="button" class="jn-btn" data-pick="' + f.key + '">...</button>') +
            (ro ? "" : '<button type="button" class="jn-btn" data-clear-h="' + f.key + '" title="Clear">x</button>') + "</div>";
        } else if (f.type === "currency") {
          ctrl = '<select class="jn-select" data-h="' + f.key + '" ' + (ro ? "disabled" : "") + ">" +
            CURRENCIES.map(function (c) {
              return '<option value="' + c + '"' + (val === c ? " selected" : "") + ">" + c + "</option>";
            }).join("") + "</select>";
        } else if (f.type === "date") {
          ctrl = '<input class="jn-input" type="date" data-h="' + f.key + '" value="' + JnUI.esc(val || "") + '" ' + (ro ? "readonly" : "") + " />";
        } else if (f.key === "status") {
          ctrl = JnUI.statusHtml(rec.status);
        } else {
          ctrl = '<input class="jn-input" data-h="' + f.key + '" value="' + JnUI.esc(val == null ? "" : val) + '" ' + (ro ? "readonly" : "") + " />";
        }
        return '<div class="jn-field" data-hf="' + f.key + '"><label>' + JnUI.esc(f.label) +
          (f.required ? " *" : "") + "</label>" + ctrl + "</div>";
      }).join("") + "</div>";
    }

    function lineErrorsMap() {
      var map = {};
      validate().forEach(function (e) {
        if (e.scope === "line") {
          map[e.row + ":" + e.field] = e.message;
        }
      });
      return map;
    }

    function linesHtml() {
      var errMap = lineErrorsMap();
      var cols = def.lineColumns;
      var head = cols.map(function (c, ci) {
        return '<th data-ci="' + ci + '" style="min-width:' + c.width + 'px">' + JnUI.esc(c.label) + "</th>";
      }).join("") + (editable ? "<th></th>" : "");
      var body = (rec.lines || []).map(function (l, ri) {
        var cells = cols.map(function (c, ci) {
          var err = errMap[ri + ":" + c.key];
          var v = l[c.key];
          var display = v;
          if (c.type === "money") display = (Number(v || 0) / 100).toFixed(2);
          if (c.type === "percent") display = ((Number(v || 0) / 100).toFixed(2));
          var ro = !editable || c.editable === false || mode === "VIEW";
          if (c.type === "item" && !ro) {
            return '<td class="' + (err ? "jn-cell-err" : "") + '"><div class="jn-selector-field">' +
              '<input class="jn-input jn-cell" data-r="' + ri + '" data-c="' + ci + '" data-k="' + c.key + '" value="' + JnUI.esc(l.sku || l.itemId || "") + '" readonly />' +
              '<button type="button" class="jn-btn" data-pick-item="' + ri + '">...</button></div>' +
              (err ? '<div class="err-msg">' + JnUI.esc(err) + "</div>" : "") + "</td>";
          }
          return '<td class="' + (err ? "jn-cell-err" : "") + '">' +
            (ro
              ? '<span class="jn-cell-ro">' + JnUI.esc(display == null ? "" : display) + "</span>"
              : '<input class="jn-input jn-cell" data-r="' + ri + '" data-c="' + ci + '" data-k="' + c.key + '" value="' + JnUI.esc(display == null ? "" : display) + '" />') +
            (err ? '<div class="err-msg">' + JnUI.esc(err) + "</div>" : "") + "</td>";
        }).join("");
        return "<tr>" + cells + (editable
          ? '<td style="white-space:nowrap">' +
            '<button type="button" class="jn-btn" data-up-row="' + ri + '" title="Move up">^</button> ' +
            '<button type="button" class="jn-btn" data-down-row="' + ri + '" title="Move down">v</button> ' +
            '<button type="button" class="jn-btn" data-copy-row="' + ri + '">Copy</button> ' +
            '<button type="button" class="jn-btn danger" data-del-row="' + ri + '">Del</button></td>'
          : "") + "</tr>";
      }).join("");
      if (!body) body = '<tr><td colspan="' + (cols.length + 1) + '"><div class="jn-empty">No lines - add or paste TSV</div></td></tr>';
      return '<div class="jn-lines-toolbar jn-toolbar">' +
        (editable ? '<button type="button" class="jn-btn primary" data-add-line>Add line</button>' +
          '<button type="button" class="jn-btn" data-paste>Paste TSV</button>' : "") +
        '<span class="spacer"></span><span style="font-size:12px;color:var(--jn-color-text-muted)">Tab / Enter / Arrows navigate cells (IME safe)</span></div>' +
        '<div class="jn-lines-editor jn-table-wrap" data-lines-wrap><table class="jn-table" data-lines-table><thead><tr>' + head +
        "</tr></thead><tbody>" + body + "</tbody></table></div>";
    }

    function footerHtml() {
      JnDocumentCalc.calcDocument(rec, def);
      return '<div class="jn-doc-footer">' + (def.footerFields || []).map(function (f) {
        var v = rec[f.key];
        if (f.type === "money") v = JnDocumentCalc.formatCents(v);
        return '<div class="jn-footer-item"><span>' + JnUI.esc(f.label) + '</span><strong>' + JnUI.esc(v) + "</strong></div>";
      }).join("") + "</div>";
    }

    function panelsHtml() {
      var refs = rec.references || { source: "", targets: [] };
      var src = refs.source ? '<button type="button" class="jn-btn" data-ref-go="source">' + JnUI.esc(refs.source) + "</button>" : "<span>(none)</span>";
      var tg = (refs.targets || []).map(function (t) {
        return '<button type="button" class="jn-btn" data-ref-go="' + JnUI.esc(t) + '">' + JnUI.esc(t) + "</button>";
      }).join(" ") || "<span>(none)</span>";
      var atts = (rec.attachments || []).map(function (a, i) {
        return '<div class="jn-todo-item"><span>' + JnUI.esc(a.name) + ' <em style="color:var(--jn-color-text-muted)">(DEMO local only)</em></span>' +
          (editable ? '<button type="button" class="jn-btn danger" data-del-att="' + i + '">Del</button>' : "") + "</div>";
      }).join("") || '<div class="jn-empty">No attachments</div>';
      var appr = (rec.approvals || []).map(function (a) {
        return "<li><strong>" + JnUI.esc(a.result) + "</strong> " + JnUI.esc(a.who) + " @ " + JnUI.esc(a.when) + " - " + JnUI.esc(a.note || "") + " <em>(DEMO)</em></li>";
      }).join("");
      var aud = (rec.audit || []).map(function (a) {
        return "<li>" + JnUI.esc(a.when) + " | " + JnUI.esc(a.user) + " | " + JnUI.esc(a.action) + " | " + JnUI.esc(a.detail) + "</li>";
      }).join("");
      return '<div class="jn-doc-panels jn-dash-grid" style="grid-template-columns:1fr 1fr 1fr">' +
        '<div class="jn-card"><div class="jn-panel-title">Document References</div><div>Source: ' + src + "</div><div style=\"margin-top:8px\">Targets: " + tg + "</div></div>" +
        '<div class="jn-card"><div class="jn-panel-title">Attachments</div>' + atts +
        (editable ? '<div class="jn-toolbar" style="margin-top:8px"><input type="file" data-file multiple /><button type="button" class="jn-btn" data-add-att>Add DEMO file</button></div>' : "") +
        '<div class="jn-demo-save-note">Files are not uploaded to server.</div></div>' +
        '<div class="jn-card"><div class="jn-panel-title">Approval Timeline</div><ul class="jn-timeline">' + appr + "</ul>" +
        '<div class="jn-panel-title" style="margin-top:12px">Audit</div><ul class="jn-timeline">' + aud + "</ul></div></div>";
    }

    function toolbarHtml() {
      var actions = def.availableActions || [];
      var btns = actions.map(function (a) {
        var en = actionEnabled(a, mode, rec.status, dirty);
        var label = a.charAt(0).toUpperCase() + a.slice(1);
        if (a === "save") label = "Save Draft";
        return '<button type="button" class="jn-btn' + (a === "save" || a === "submit" ? " primary" : "") +
          (a === "cancel" || a === "reject" ? " danger" : "") + '" data-act="' + a + '" ' + (en ? "" : "disabled") + ">" + label + "</button>";
      }).join("");
      return '<div class="jn-doc-toolbar jn-toolbar">' +
        "<strong>" + JnUI.esc(def.title) + " | " + JnUI.esc(mode) + "</strong> " +
        JnUI.statusHtml(rec.status) +
        ' <span class="jn-status OPEN" data-dirty style="display:' + (dirty ? "inline-flex" : "none") + '">UNSAVED</span>' +
        '<span class="spacer"></span>' + btns + "</div>";
    }

    function paint() {
      JnDocumentCalc.calcDocument(rec, def);
      shell.innerHTML =
        toolbarHtml() +
        '<div class="jn-modal-body" style="overflow:auto">' +
        '<div class="jn-demo-save-note">DEMO editor - status changes are UI mock only, not formal posting. Drafts: sessionStorage (cleared when browser tab closes).</div>' +
        headerHtml() + linesHtml() + footerHtml() + panelsHtml() +
        "</div>";
      bind();
      markDirty();
    }

    function syncHeader() {
      shell.querySelectorAll("[data-h]").forEach(function (inp) {
        var k = inp.getAttribute("data-h");
        if (k === "partnerId" || k === "warehouseId" || k === "employeeId" || k === "contactName") return;
        if (inp.tagName === "SELECT" || !inp.readOnly) rec[k] = inp.value;
      });
    }

    function syncLinesFromDom() {
      shell.querySelectorAll("input.jn-cell").forEach(function (inp) {
        var r = Number(inp.getAttribute("data-r"));
        var k = inp.getAttribute("data-k");
        if (!rec.lines[r]) return;
        var v = inp.value;
        if (k === "qty") rec.lines[r].qty = JnDocumentCalc.toInt(v);
        else if (k === "unitPriceCents") rec.lines[r].unitPriceCents = JnDocumentCalc.parseMoneyToCents(v);
        else if (k === "discountBps" || k === "taxBps") rec.lines[r][k] = Math.round(Number(v) * 100) || 0;
        else if (k !== "itemId") rec.lines[r][k] = v;
      });
      JnDocumentCalc.calcDocument(rec, def);
    }

    function addLine(prefill) {
      syncLinesFromDom();
      var line = Object.assign({
        id: "L" + Date.now(),
        itemId: "",
        sku: "",
        name: "",
        uom: "EA",
        qty: 1,
        unitPriceCents: 0,
        discountBps: 0,
        taxBps: 1300,
        lineAmountCents: 0
      }, prefill || {});
      rec.lines.push(line);
      paint();
    }

    function applyPasteTsv(text) {
      var rows = String(text || "").replace(/\r/g, "").split("\n").filter(function (x) { return x.trim().length; });
      if (!rows.length) return;
      var mapped = [];
      var errs = [];
      rows.forEach(function (row, i) {
        var parts = row.split("\t");
        /* expected: sku, name, qty, price, discount%, tax% */
        var sku = (parts[0] || "").trim();
        var name = (parts[1] || "").trim();
        var qty = JnDocumentCalc.toInt(parts[2]);
        var priceCents = JnDocumentCalc.parseMoneyToCents(parts[3]);
        var disc = parts[4] != null && parts[4] !== "" ? Math.round(Number(parts[4]) * 100) : 0;
        var tax = parts[5] != null && parts[5] !== "" ? Math.round(Number(parts[5]) * 100) : 1300;
        if (!sku) { errs.push("Row " + (i + 1) + ": SKU required"); return; }
        if (!(qty > 0)) { errs.push("Row " + (i + 1) + ": qty must be > 0"); return; }
        if (priceCents < 0) { errs.push("Row " + (i + 1) + ": price invalid"); return; }
        mapped.push({ sku: sku, name: name || sku, qty: qty, unitPriceCents: priceCents, discountBps: disc, taxBps: tax, uom: "EA", itemId: sku });
      });
      if (errs.length) {
        JnUI.toast("Paste rejected: " + errs[0], "err");
        return;
      }
      var ok = window.confirm("Paste " + mapped.length + " lines into document? (DEMO)");
      if (!ok) return;
      syncLinesFromDom();
      mapped.forEach(function (m) { rec.lines.push(Object.assign({ id: "P" + Date.now() + Math.random() }, m)); });
      rec.audit.push({ when: today() + " " + new Date().toTimeString().slice(0, 5), user: "Demo User", action: "EDIT", detail: "Paste " + mapped.length + " lines (DEMO)" });
      paint();
      JnUI.toast("Pasted " + mapped.length + " lines (DEMO)", "ok");
    }

    function bind() {
      shell.querySelectorAll("[data-act]").forEach(function (btn) {
        btn.onclick = function () { runAction(btn.getAttribute("data-act")); };
      });
      shell.querySelectorAll("[data-pick]").forEach(function (btn) {
        btn.onclick = function () {
          var k = btn.getAttribute("data-pick");
          if (k === "partnerId") {
            JnSelectors.openPartnerSelector(def.partnerKind, function (p) {
              rec.partnerId = p.id;
              rec.partnerName = p.name;
              if (def.partnerKind === "CUSTOMER") { rec.customerId = p.id; rec.customerName = p.name; }
              if (def.partnerKind === "SUPPLIER") { rec.supplierId = p.id; rec.supplierName = p.name; }
              paint();
            });
          } else if (k === "warehouseId") {
            JnSelectors.openWarehouseSelector(function (w) {
              rec.warehouseId = w.id;
              rec.warehouseCode = w.code;
              paint();
            });
          } else if (k === "employeeId") {
            JnSelectors.openEmployeeSelector(function (e) {
              rec.employeeId = e.id;
              rec.employeeName = e.name;
              paint();
            });
          } else if (k === "contactName") {
            JnSelectors.openPartnerSelector(def.partnerKind, function (p) {
              rec.contactName = p.name;
              paint();
            });
          }
        };
      });
      shell.querySelectorAll("[data-clear-h]").forEach(function (btn) {
        btn.onclick = function () {
          var k = btn.getAttribute("data-clear-h");
          rec[k] = "";
          if (k === "partnerId") { rec.partnerName = ""; rec.customerName = ""; rec.supplierName = ""; }
          paint();
        };
      });
      shell.querySelectorAll("[data-h]").forEach(function (inp) {
        inp.addEventListener("change", function () {
          syncHeader();
          markDirty();
        });
      });
      var add = shell.querySelector("[data-add-line]");
      if (add) add.onclick = function () { addLine(); };
      var pasteBtn = shell.querySelector("[data-paste]");
      if (pasteBtn) pasteBtn.onclick = function () {
        var t = window.prompt("Paste TSV rows: sku\\tname\\tqty\\tprice\\tdisc%\\ttax%");
        if (t != null) applyPasteTsv(t);
      };
      shell.querySelectorAll("[data-del-row]").forEach(function (btn) {
        btn.onclick = function () {
          syncLinesFromDom();
          rec.lines.splice(Number(btn.getAttribute("data-del-row")), 1);
          paint();
        };
      });
      shell.querySelectorAll("[data-copy-row]").forEach(function (btn) {
        btn.onclick = function () {
          syncLinesFromDom();
          var i = Number(btn.getAttribute("data-copy-row"));
          var copy = JSON.parse(JSON.stringify(rec.lines[i]));
          copy.id = "C" + Date.now();
          rec.lines.splice(i + 1, 0, copy);
          paint();
        };
      });
      shell.querySelectorAll("[data-up-row]").forEach(function (btn) {
        btn.onclick = function () {
          syncLinesFromDom();
          var i = Number(btn.getAttribute("data-up-row"));
          if (i <= 0) return;
          var tmp = rec.lines[i - 1];
          rec.lines[i - 1] = rec.lines[i];
          rec.lines[i] = tmp;
          paint();
        };
      });
      shell.querySelectorAll("[data-down-row]").forEach(function (btn) {
        btn.onclick = function () {
          syncLinesFromDom();
          var i = Number(btn.getAttribute("data-down-row"));
          if (i >= rec.lines.length - 1) return;
          var tmp = rec.lines[i + 1];
          rec.lines[i + 1] = rec.lines[i];
          rec.lines[i] = tmp;
          paint();
        };
      });
      shell.querySelectorAll("[data-pick-item]").forEach(function (btn) {
        btn.onclick = function () {
          var ri = Number(btn.getAttribute("data-pick-item"));
          JnSelectors.openItemSelector(function (it) {
            syncLinesFromDom();
            var l = rec.lines[ri];
            l.itemId = it.id;
            l.sku = it.sku;
            l.name = it.name;
            l.uom = it.uom || "EA";
            /* ItemSelector normalizes price to integer cents */
            l.unitPriceCents = JnDocumentCalc.toInt(it.price);
            paint();
          });
        };
      });
      shell.querySelectorAll("input.jn-cell").forEach(function (inp) {
        inp.addEventListener("input", function () {
          syncLinesFromDom();
          /* live footer update without full repaint */
          var foot = shell.querySelector(".jn-doc-footer");
          if (foot) {
            JnDocumentCalc.calcDocument(rec, def);
            foot.outerHTML = footerHtml();
          }
          markDirty();
        });
        inp.addEventListener("keydown", onCellKey);
      });
      var addAtt = shell.querySelector("[data-add-att]");
      if (addAtt) addAtt.onclick = function () {
        var fileInput = shell.querySelector("[data-file]");
        var files = fileInput && fileInput.files;
        if (!files || !files.length) {
          JnUI.toast("Choose a file first (DEMO local name only)", "err");
          return;
        }
        for (var i = 0; i < files.length; i++) {
          rec.attachments.push({ name: files[i].name, size: files[i].size, demo: true });
        }
        paint();
        JnUI.toast("DEMO attachment metadata added (not uploaded)", "ok");
      };
      shell.querySelectorAll("[data-del-att]").forEach(function (btn) {
        btn.onclick = function () {
          rec.attachments.splice(Number(btn.getAttribute("data-del-att")), 1);
          paint();
        };
      });
    }

    function editableCols() {
      return def.lineColumns.map(function (c, i) { return { c: c, i: i }; }).filter(function (x) {
        return x.c.editable !== false && x.c.type !== "item";
      });
    }

    function onCellKey(e) {
      /* do not intercept when composing IME */
      if (e.isComposing || e.keyCode === 229) return;
      var r = Number(e.target.getAttribute("data-r"));
      var c = Number(e.target.getAttribute("data-c"));
      var cols = def.lineColumns;
      if (e.key === "Escape") {
        e.target.blur();
        return;
      }
      if (e.key === "Delete" && e.target.value === "" && e.ctrlKey) {
        e.preventDefault();
        syncLinesFromDom();
        rec.lines.splice(r, 1);
        paint();
        return;
      }
      var nextR = r, nextC = c;
      if (e.key === "Enter" || e.key === "ArrowDown") {
        e.preventDefault();
        nextR = Math.min(rec.lines.length - 1, r + 1);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        nextR = Math.max(0, r - 1);
      } else if (e.key === "Tab" && !e.shiftKey) {
        e.preventDefault();
        nextC = c + 1;
        while (nextC < cols.length && (cols[nextC].editable === false)) nextC++;
        if (nextC >= cols.length) { nextC = 0; nextR = r + 1; if (nextR >= rec.lines.length) addLine(); return; }
      } else if (e.key === "Tab" && e.shiftKey) {
        e.preventDefault();
        nextC = c - 1;
        while (nextC >= 0 && cols[nextC].editable === false) nextC--;
        if (nextC < 0) { nextR = Math.max(0, r - 1); nextC = cols.length - 1; }
      } else if (e.key === "ArrowRight" && e.target.selectionStart === e.target.value.length) {
        e.preventDefault();
        nextC = c + 1;
      } else if (e.key === "ArrowLeft" && e.target.selectionStart === 0) {
        e.preventDefault();
        nextC = Math.max(0, c - 1);
      } else {
        return;
      }
      var sel = shell.querySelector('input.jn-cell[data-r="' + nextR + '"][data-c="' + nextC + '"]');
      if (sel) { sel.focus(); sel.select(); }
    }

    function onGlobalKey(e) {
      if (!document.body.contains(backdrop)) {
        document.removeEventListener("keydown", onGlobalKey, true);
        document.removeEventListener("keydown", onSaveKey, true);
        return;
      }
      var t = e.target;
      if (e.key === "Escape" && t && typeof t.closest === "function" && !t.closest(".jn-modal-backdrop[data-picker]")) {
        /* don't steal from nested pickers */
      }
    }

    function persistDraft() {
      var map = loadSessionDrafts();
      map[def.documentType + ":" + rec.id] = rec;
      saveSessionDrafts(map);
    }

    function runAction(act) {
      syncHeader();
      syncLinesFromDom();
      if (act === "close") { close(false); return; }
      if (act === "save") {
        var errs = validate();
        if (errs.length) {
          JnUI.toast("Validation: " + (errs[0].message || "error") + (errs[0].row != null ? " (line " + (errs[0].row + 1) + ")" : ""), "err");
          paint();
          return;
        }
        JnDocumentCalc.calcDocument(rec, def);
        if (def.partnerKind === "CUSTOMER") rec.customerName = rec.partnerName;
        if (def.partnerKind === "SUPPLIER") rec.supplierName = rec.partnerName;
        rec.total = rec.totalCents / 100;
        /* sync line price yuan for ApplicationService */
        (rec.lines || []).forEach(function (l) {
          if (l.unitPriceCents != null) l.price = l.unitPriceCents / 100;
          if (l.lineAmountCents != null) l.amount = l.lineAmountCents / 100;
        });
        rec.audit.push({ when: today() + " " + new Date().toTimeString().slice(0, 5), user: "Demo User", action: "EDIT", detail: "Save DEMO draft via ApplicationService" });
        persistDraft();
        try {
          var saved = JnAppService.saveDocument(def.documentType, rec);
          Object.assign(rec, saved);
          baseline = JSON.stringify(rec);
          dirty = false;
          JnUI.toast("DEMO draft saved via ApplicationService -> DemoStateStore (localStorage, not formal DB)", "ok");
          paint();
          if (opts.onSaved) opts.onSaved(rec);
        } catch (e) {
          JnUI.toast(e.message || String(e), "err");
        }
        return;
      }
      if (act === "submit") {
        if (validate().length) { JnUI.toast("Fix errors before submit", "err"); paint(); return; }
        try {
          JnAppService.saveDocument(def.documentType, rec);
          var sub = JnAppService.submitDocument(def.collection, rec.id);
          Object.assign(rec, sub);
          rec.approvals.push({ who: "Demo User", when: today(), result: "SUBMITTED", note: "DEMO ApplicationService" });
          persistDraft();
          baseline = JSON.stringify(rec);
          JnUI.toast("DEMO status -> SUBMITTED (ApplicationService, not formal posting)", "ok");
          paint();
        } catch (e) {
          JnUI.toast(e.message || String(e), "err");
        }
        return;
      }
      if (act === "approve") {
        try {
          (rec.lines || []).forEach(function (l) {
            if (l.unitPriceCents != null) l.price = l.unitPriceCents / 100;
          });
          var posted = JnAppService.approveAndPost(def.documentType, rec);
          Object.assign(rec, posted);
          rec.approvals = rec.approvals || [];
          rec.approvals.push({ who: "Demo Approver", when: today(), result: "APPROVED/POSTED", note: "DEMO ApplicationService" });
          persistDraft();
          baseline = JSON.stringify(rec);
          mode = canEdit("EDIT", rec.status) ? mode : "VIEW";
          editable = canEdit(mode, rec.status);
          JnUI.toast("DEMO approve/post via ApplicationService (not formal DB posting)", "ok");
          paint();
          if (opts.onSaved) opts.onSaved(rec);
        } catch (e) {
          JnUI.toast(e.message || String(e), "err");
          paint();
        }
        return;
      }
      if (act === "reject") {
        rec.status = "DRAFT";
        rec.approvals.push({ who: "Demo Approver", when: today(), result: "REJECTED", note: "DEMO" });
        persistDraft();
        JnData.active.save(def.collection, rec);
        baseline = JSON.stringify(rec);
        paint();
        return;
      }
      if (act === "cancel") {
        rec.status = "CANCELLED";
        rec.audit.push({ when: today(), user: "Demo User", action: "CANCEL", detail: "DEMO" });
        persistDraft();
        JnData.active.save(def.collection, rec);
        baseline = JSON.stringify(rec);
        paint();
        return;
      }
      if (act === "copy") {
        var copy = JSON.parse(JSON.stringify(rec));
        copy.id = "NEW-" + Date.now();
        copy.docNo = "";
        copy.status = "DRAFT";
        open({ def: def, mode: "CREATE", record: copy, onSaved: opts.onSaved });
        close(true);
      }
    }

    function onSaveKey(e) {
      if (!document.body.contains(backdrop)) {
        document.removeEventListener("keydown", onGlobalKey, true);
        document.removeEventListener("keydown", onSaveKey, true);
        return;
      }
      if (e.isComposing || e.keyCode === 229) return;
      var mod = e.ctrlKey || e.metaKey;
      if (mod && (e.key === "s" || e.key === "S")) {
        e.preventDefault();
        if (actionEnabled("save", mode, rec.status, dirty)) runAction("save");
      }
    }

    document.body.appendChild(backdrop);
    document.addEventListener("keydown", onGlobalKey, true);
    document.addEventListener("keydown", onSaveKey, true);
    var _close = close;
    close = function (force) {
      document.removeEventListener("keydown", onSaveKey, true);
      _close(force);
    };
    paint();
    var renderMs = Math.round(performance.now() - startMs);
    shell.setAttribute("data-render-ms", String(renderMs));
    return { el: shell, renderMs: renderMs, getRecord: function () { return rec; } };
  }

  function openByPage(page, record, mode, onSaved) {
    var def = JnDocumentDefs.getByPageId(page.pageId);
    if (!def) {
      JnUI.toast("No DocumentUiDefinition for " + page.pageId, "err");
      return null;
    }
    return open({ def: def, record: record, mode: mode || (record && record.id ? "EDIT" : "CREATE"), onSaved: onSaved });
  }

  /** Force-dismiss any open editor DOM + leaked key handlers (acceptance / Escape hardening). */
  function dismissAll() {
    document.querySelectorAll(".jn-doc-editor-backdrop").forEach(function (bd) {
      try {
        var esc = new KeyboardEvent("keydown", { key: "Escape", bubbles: true });
        bd.dispatchEvent(esc);
      } catch (e) {}
      bd.remove();
    });
  }

  global.JnDocumentEditor = {
    open: open,
    openByPage: openByPage,
    canEdit: canEdit,
    loadSessionDrafts: loadSessionDrafts,
    dismissAll: dismissAll
  };
})(window);
