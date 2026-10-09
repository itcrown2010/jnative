// Author: 张晓达
/** Page templates - list / form / document / dashboard / report / specialized */
(function (global) {
  var state = {
    query: "",
    status: "",
    sort: "",
    page: 1,
    selectedId: null,
    draft: null
  };

  function resetListState() {
    state.query = "";
    state.status = "";
    state.sort = "";
    state.page = 1;
    state.selectedId = null;
    state.draft = null;
  }

  function tt(key, params, fallback) {
    return window.JnI18n ? JnI18n.t(key, params, fallback) : (fallback != null ? fallback : key);
  }

  function toolbar(page, opts) {
    opts = opts || {};
    return (
      '<div class="jn-toolbar">' +
      "<h2 class=\"jn-card-title\" style=\"margin:0;flex:1\">" + JnUI.esc(page.title) + "</h2>" +
      (opts.hideNew ? "" : '<button type="button" class="jn-btn primary" data-action="new">' + JnUI.esc(tt("common.new")) + "</button>") +
      '<button type="button" class="jn-btn" data-action="refresh">' + JnUI.esc(tt("common.refresh")) + "</button>" +
      "</div>"
    );
  }

  function filterBar(page) {
    function opt(code) {
      var label = window.JnI18n ? JnI18n.statusLabel(code) : code;
      return '<option value="' + code + '">' + JnUI.esc(label) + "</option>";
    }
    return (
      '<div class="jn-filter-bar">' +
      '<div class="jn-field"><label>' + JnUI.esc(tt("common.search")) + '</label><input class="jn-input" data-role="search" value="' + JnUI.esc(state.query) + '" placeholder="' + JnUI.esc(tt("common.filterPlaceholder")) + '" /></div>' +
      '<div class="jn-field"><label>' + JnUI.esc(tt("common.status")) + '</label><select class="jn-select" data-role="status">' +
      '<option value="">' + JnUI.esc(tt("common.all")) + "</option>" +
      opt("ACTIVE") + opt("OPEN") + opt("DRAFT") + opt("APPROVED") + opt("POSTED") + opt("PARTIAL") +
      "</select></div>" +
      '<button type="button" class="jn-btn primary" data-action="apply-filter">' + JnUI.esc(tt("common.apply")) + "</button>" +
      "</div>"
    );
  }

  function tableHtml(page, result) {
    var cols = page.columns || [];
    var head = cols.map(function (c) {
      return "<th data-sort=\"" + JnUI.esc(c.key) + "\">" + JnUI.esc(c.title) + "</th>";
    }).join("") + "<th></th>";
    var body = (result.rows || []).map(function (row) {
      var cells = cols.map(function (c) { return JnUI.cell(c, row); }).join("");
      return '<tr data-id="' + JnUI.esc(row.id) + '">' + cells +
        '<td><button type="button" class="jn-btn" data-action="view" data-id="' + JnUI.esc(row.id) + '">' + JnUI.esc(tt("common.open")) + "</button></td></tr>";
    }).join("");
    if (!body) body = '<tr><td colspan="' + (cols.length + 1) + '"><div class="jn-empty">' + JnUI.esc(tt("common.empty")) + "</div></td></tr>";
    var from = (result.total === 0) ? 0 : ((result.page - 1) * result.pageSize + 1);
    var to = Math.min(result.page * result.pageSize, result.total);
    return (
      '<div class="jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>" +
      '<div class="jn-pagination"><span>' + JnUI.esc(tt("common.of", { from: from, to: to, total: result.total })) + "</span>" +
      '<span class="spacer" style="flex:1"></span>' +
      '<button type="button" class="jn-btn" data-action="prev" ' + (result.page <= 1 ? "disabled" : "") + ">" + JnUI.esc(tt("common.prev")) + "</button>" +
      '<button type="button" class="jn-btn" data-action="next" ' + (to >= result.total ? "disabled" : "") + ">" + JnUI.esc(tt("common.next")) + "</button></div>"
    );
  }

  function formFieldsHtml(fields, record) {
    record = record || {};
    return '<div class="jn-form-grid">' + fields.map(function (f) {
      var val = record[f] == null ? "" : record[f];
      var type = (f.indexOf("date") >= 0 || f === "due" || f === "expiry") ? "date" :
        (f === "qty" || f === "price" || f === "cost" || f === "amount" || f === "value" || f === "rate" || f === "creditLimit" || f === "balance" || f === "total" || f === "openAmount" || f === "probability" || f === "permissions" || f === "fields" || f === "steps" || f === "next" || f === "onHand") ? "number" : "text";
      return '<div class="jn-field"><label>' + JnUI.esc(f) + '</label><input class="jn-input" data-field="' + JnUI.esc(f) + '" type="' + type + '" value="' + JnUI.esc(val) + '" /></div>';
    }).join("") + "</div>";
  }

  function bindListActions(root, page, reload) {
    root.querySelectorAll("[data-action]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var action = btn.getAttribute("data-action");
        if (action === "refresh" || action === "apply-filter") {
          var s = root.querySelector('[data-role="search"]');
          var st = root.querySelector('[data-role="status"]');
          state.query = s ? s.value : "";
          state.status = st ? st.value : "";
          state.page = 1;
          reload();
        } else if (action === "prev") { state.page = Math.max(1, state.page - 1); reload(); }
        else if (action === "next") { state.page = state.page + 1; reload(); }
        else if (action === "new") { openEditor(page, null, reload); }
        else if (action === "view") { openEditor(page, btn.getAttribute("data-id"), reload); }
      });
    });
    root.querySelectorAll("th[data-sort]").forEach(function (th) {
      th.addEventListener("click", function () {
        var key = th.getAttribute("data-sort");
        state.sort = state.sort === key ? ("-" + key) : key;
        reload();
      });
    });
  }

  function openEditor(page, id, onSaved) {
    var isDoc = page.template === "documentList" && (page.editor === true || page.editor === "bom");
    var promise = id ? JnData.active.get(page.collection, id) : Promise.resolve({ status: "DRAFT", lines: page.editor ? [] : undefined });
    promise.then(function (rec) {
      if (!rec) { JnUI.toast("Record not found", "err"); return; }
      if (page.readOnly) {
        JnUI.modal({
          title: tt("common.viewSuffix", { title: page.title }),
          bodyHtml: "<pre style=\"white-space:pre-wrap;font-size:12px\">" + JnUI.esc(JSON.stringify(rec, null, 2)) + "</pre>",
          primaryLabel: null
        });
        return;
      }
      if (isDoc || JnDocumentDefs.getByPageId(page.pageId)) {
        var mode = page.readOnly ? "VIEW" : (id ? "EDIT" : "CREATE");
        if (JnDocumentDefs.getByPageId(page.pageId)) {
          JnDocumentEditor.openByPage(page, rec, mode, onSaved);
          return;
        }
        openDocumentEditor(page, rec, onSaved);
        return;
      }
      var fields = page.formFields || Object.keys(rec).filter(function (k) { return k !== "id" && k !== "lines"; });
      if (!id && page.collection === "items") {
        fields = ["sku", "name", "price"];
        rec = { sku: "", name: "", price: 0, status: "ACTIVE" };
      }
      var body = formFieldsHtml(fields, rec);
      JnUI.modal({
        title: id ? tt("common.editPrefix", { title: page.title }) : tt("common.newPrefix", { title: page.title }),
        bodyHtml: body,
        primaryLabel: tt("common.save"),
        onOk: function (backdrop) {
          var invalid = 0;
          fields.forEach(function (f) {
            var input = backdrop.querySelector('[data-field="' + f + '"]');
            if (!input) return;
            var field = input.closest(".jn-field");
            if (field) {
              var old = field.querySelector(".err-msg");
              if (old) old.remove();
              field.classList.remove("error");
            }
            var v = input.value;
            if ((f === "name" || f === "code" || f === "sku" || f === "email") && String(v).trim() === "") {
              invalid = 1;
              if (field) {
                field.classList.add("error");
                var em = document.createElement("div");
                em.className = "err-msg";
                em.textContent = tt("common.required");
                field.appendChild(em);
              }
            }
            if (input.type === "number") v = Number(v);
            rec[f] = v;
          });
          if (invalid) {
            JnUI.toast(tt("common.fixInvalid"), "err");
            return false;
          }
          JnData.active.save(page.collection, rec).then(function () {
            JnUI.toast(tt("common.saved"), "ok");
            if (onSaved) onSaved();
          }).catch(function (err) {
            JnUI.toast((err && err.message) || tt("common.saveFailed"), "err");
          });
          return true;
        }
      });
    }).catch(function (err) {
      JnUI.toast((err && err.message) || tt("common.loadFailed"), "err");
    });
  }

  function openDocumentEditor(page, rec, onSaved) {
    if (!rec.lines) rec.lines = [];
    function renderBody() {
      var partner = page.partnerField || "customerName";
      var lines = rec.lines.map(function (l, idx) {
        return "<tr>" +
          '<td><input class="jn-input" data-line="' + idx + '" data-k="sku" value="' + JnUI.esc(l.sku || l.componentSku || "") + '" /></td>' +
          '<td><input class="jn-input" data-line="' + idx + '" data-k="name" value="' + JnUI.esc(l.name || "") + '" /></td>' +
          '<td><input class="jn-input" data-line="' + idx + '" data-k="qty" type="number" value="' + JnUI.esc(l.qty) + '" /></td>' +
          '<td><input class="jn-input" data-line="' + idx + '" data-k="price" type="number" value="' + JnUI.esc(l.price == null ? "" : l.price) + '" /></td>' +
          '<td class="jn-money">' + JnUI.esc(JnUI.money(l.amount || 0)) + "</td>" +
          '<td><button type="button" class="jn-btn danger" data-del-line="' + idx + '">Del</button></td></tr>';
      }).join("");
      return (
        '<div class="jn-form-grid">' +
        '<div class="jn-field"><label>Document No</label><input class="jn-input" data-field="docNo" value="' + JnUI.esc(rec.docNo || "(new)") + '" /></div>' +
        '<div class="jn-field"><label>Partner</label><input class="jn-input" data-field="' + JnUI.esc(partner) + '" value="' + JnUI.esc(rec[partner] || "") + '" /></div>' +
        '<div class="jn-field"><label>Date</label><input class="jn-input" data-field="date" type="date" value="' + JnUI.esc(rec.date || "") + '" /></div>' +
        '<div class="jn-field"><label>Status</label><div>' + JnUI.statusHtml(rec.status || "DRAFT") + "</div></div>" +
        "</div>" +
        '<div class="jn-toolbar" style="margin-top:12px"><strong>Lines</strong><span class="spacer"></span>' +
        '<button type="button" class="jn-btn" data-add-line>Add line</button></div>' +
        '<div class="jn-lines-editor jn-table-wrap"><table class="jn-table"><thead><tr><th>SKU</th><th>Name</th><th>Qty</th><th>Price</th><th>Amount</th><th></th></tr></thead><tbody>' +
        (lines || '<tr><td colspan="6"><div class="jn-empty">No lines - add a line.</div></td></tr>') +
        "</tbody></table></div>" +
        '<div style="margin-top:12px;text-align:right;font-weight:700">Total: <span class="jn-money" data-total>' + JnUI.esc(JnUI.money(rec.total || 0)) + "</span></div>" +
        '<div class="jn-split" style="margin-top:16px">' +
        '<div class="jn-card"><div class="jn-panel-title">Document References</div><ul class="jn-timeline"><li>Document draft</li><li>Posting uses server APIs when available</li></ul></div>' +
        '<div class="jn-card"><div class="jn-panel-title">Approval Timeline</div><ul class="jn-timeline"><li>Draft saved locally</li><li>Awaiting demo approval</li></ul></div>' +
        "</div>" +
        '<div class="jn-demo-save-note">' + JnUI.esc(tt("common.demoSaveNote")) + "</div>"
      );
    }
    var backdrop = JnUI.modal({
      title: page.title + " | Editor",
      bodyHtml: renderBody(),
      primaryLabel: tt("common.save"),
      onOk: function () {
        syncFromDom(backdrop);
        rec.total = JnMockData.recalcLines(rec.lines);
        if (!rec.docNo || rec.docNo === "(new)") rec.docNo = "DRAFT-" + Date.now().toString().slice(-6);
        JnData.active.save(page.collection, rec).then(function () {
          JnUI.toast(tt("common.savedLocalOnly"), "ok");
          if (onSaved) onSaved();
        });
        return true;
      }
    });
    function syncFromDom(root) {
      ["docNo", "date", page.partnerField || "customerName"].forEach(function (f) {
        var input = root.querySelector('[data-field="' + f + '"]');
        if (input) rec[f] = input.value;
      });
      root.querySelectorAll("[data-line]").forEach(function (input) {
        var i = Number(input.getAttribute("data-line"));
        var k = input.getAttribute("data-k");
        if (!rec.lines[i]) return;
        var v = input.value;
        if (k === "qty" || k === "price") v = Number(v);
        rec.lines[i][k] = v;
        if (k === "sku") rec.lines[i].componentSku = v;
      });
      rec.total = JnMockData.recalcLines(rec.lines);
      var t = root.querySelector("[data-total]");
      if (t) t.textContent = JnUI.money(rec.total);
    }
    function rebind() {
      var body = backdrop.querySelector(".jn-modal-body");
      body.innerHTML = renderBody();
      body.querySelector("[data-add-line]").addEventListener("click", function () {
        syncFromDom(backdrop);
        rec.lines.push({ id: "NL" + Date.now(), sku: "", name: "", qty: 1, price: 0, amount: 0 });
        rebind();
      });
      body.querySelectorAll("[data-del-line]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          syncFromDom(backdrop);
          rec.lines.splice(Number(btn.getAttribute("data-del-line")), 1);
          rebind();
        });
      });
      body.querySelectorAll("[data-line]").forEach(function (input) {
        input.addEventListener("input", function () {
          syncFromDom(backdrop);
          var i = Number(input.getAttribute("data-line"));
          var row = body.querySelectorAll("tbody tr")[i];
          if (row) {
            var moneyCell = row.querySelector(".jn-money");
            if (moneyCell) moneyCell.textContent = JnUI.money(rec.lines[i].amount);
          }
        });
      });
    }
    rebind();
  }

  function renderEnterpriseList(host, page) {
    resetListState();
    var cols = JnGridModel.fromPageColumns(page.columns || []);
    /* pin first data col left; actions handled by grid */
    if (cols.length) cols[0].pinned = "left";
    JnEnterpriseGrid.mount(host, {
      page: page,
      columns: cols,
      onNew: function () { openEditor(page, null, function () { JnTemplates.render(host, page); }); },
      onOpen: function (id) {
        if (page.detailTemplate) renderMasterDetail(host, page, id);
        else openEditor(page, id, function () { JnTemplates.render(host, page); });
      }
    });
  }

  function renderMasterList(host, page) {
    if (page.enterpriseGrid) {
      renderEnterpriseList(host, page);
      return;
    }
    resetListState();
    function reload() {
      JnData.active.list(page.collection, { q: state.query, status: state.status, sort: state.sort, page: state.page, pageSize: 10 }).then(function (result) {
        host.innerHTML = '<div class="jn-card">' + toolbar(page, { hideNew: page.readOnly }) + filterBar(page) + tableHtml(page, result) + "</div>";
        var st = host.querySelector('[data-role="status"]');
        if (st) st.value = state.status;
        bindListActions(host, page, reload);
      }).catch(function (err) {
        host.innerHTML = '<div class="jn-error" role="alert">' + JnUI.esc((err && err.message) || tt("common.loadFailed")) + "</div>";
      });
    }
    reload();
  }

  function renderDocumentList(host, page) {
    renderMasterList(host, page);
  }

  function sparkSvg(points) {
    points = points || [];
    if (!points.length) return "";
    var w = 320, h = 120, pad = 8;
    var min = Math.min.apply(null, points), max = Math.max.apply(null, points);
    if (min === max) { min = min - 1; max = max + 1; }
    var coords = points.map(function (p, i) {
      var x = pad + (i / Math.max(1, points.length - 1)) * (w - pad * 2);
      var y = h - pad - ((p - min) / (max - min)) * (h - pad * 2);
      return x.toFixed(1) + "," + y.toFixed(1);
    }).join(" ");
    var area = pad + "," + (h - pad) + " " + coords + " " + (w - pad) + "," + (h - pad);
    return '<svg class="jn-spark" viewBox="0 0 ' + w + " " + h + '" preserveAspectRatio="none">' +
      '<polygon fill="var(--jn-color-primary-soft)" points="' + area + '"></polygon>' +
      '<polyline fill="none" stroke="var(--jn-color-chart)" stroke-width="2.5" points="' + coords + '"></polyline>' +
      "</svg>";
  }

  function renderDashboard(host, page) {
    var kpiLabel = {
      customers: tt("dash.kpi.customers"),
      suppliers: tt("dash.kpi.suppliers"),
      items: tt("dash.kpi.items"),
      openPo: tt("dash.kpi.openPo"),
      openSo: tt("dash.kpi.openSo")
    };
    JnData.active.dashboard(page.dashKind || "executive").then(function (data) {
      var kpis = (data.kpis || []).map(function (k) {
        var label = kpiLabel[k.k] || k.k;
        return '<div class="jn-kpi" role="group" aria-label="' + JnUI.esc(label) + '"><div class="k">' + JnUI.esc(label) +
          '</div><div class="v">' + JnUI.esc(k.v) + '</div><div class="d">' + JnUI.esc(k.d || "") + "</div></div>";
      }).join("");
      host.innerHTML =
        '<div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0">' + JnUI.esc(page.title) + '</h2>' +
        '<span class="spacer"></span>' +
        '<button type="button" class="jn-btn" data-go="md.items">' + JnUI.esc(tt("page.md.items")) + "</button>" +
        '<button type="button" class="jn-btn" data-go="md.customers">' + JnUI.esc(tt("page.md.customers")) + "</button></div>" +
        '<div class="jn-kpi-grid">' + kpis + "</div>" +
        '<div class="jn-card" style="margin-top:16px"><div class="jn-card-title">' + JnUI.esc(tt("dash.quickActions")) +
        '</div><div class="jn-toolbar">' +
        '<button type="button" class="jn-btn primary" data-go="md.items">' + JnUI.esc(tt("page.md.items")) + "</button>" +
        '<button type="button" class="jn-btn" data-go="pur.orders">' + JnUI.esc(tt("page.pur.orders")) + "</button>" +
        '<button type="button" class="jn-btn" data-go="sales.orders">' + JnUI.esc(tt("page.sales.orders")) + "</button>" +
        "</div></div>";
      host.querySelectorAll("[data-go]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          global.JnApp.navigate(btn.getAttribute("data-go"));
        });
      });
    }).catch(function (err) {
      host.innerHTML = '<div class="jn-error" role="alert">' + JnUI.esc((err && err.message) || tt("common.loadFailed")) + "</div>";
    });
  }

  function renderReport(host, page) {
    var kind = page.reportKind || "sales";
    var tables = {
      sales: { collection: "salesOrders", title: "Sales Orders", metric: "total" },
      purchasing: { collection: "purchaseOrders", title: "Purchase Orders", metric: "total" },
      inventory: { collection: "inventoryBalances", title: "Inventory Valuation", metric: "value" },
      finance: { collection: "journalEntries", title: "Journals", metric: "debit" },
      mfg: { collection: "productionOrders", title: "Production Orders", metric: "qty" },
      mfgCost: { collection: "productionIssues", title: "Issue Values", metric: "value" },
      mfgProgress: { collection: "productionOrders", title: "Progress", metric: "qty" },
      customer: { collection: "customers", title: "Customers", metric: "creditLimit" },
      executive: { collection: "salesOrders", title: "Executive slice", metric: "total" },
      balanceSheet: { collection: "accounts", title: "Accounts (BS proxy)", metric: "balance" },
      income: { collection: "trialBalance", title: "Trial Balance", metric: "debit" },
      cashflow: { collection: "incomingPayments", title: "Cash movements", metric: "amount" },
      pipeline: { collection: "opportunities", title: "Pipeline", metric: "value" },
      forecast: { collection: "opportunities", title: "Forecast source", metric: "value" }
    };
    var conf = tables[kind] || tables.sales;
    JnData.active.list(conf.collection, { page: 1, pageSize: 50, q: state.query }).then(function (result) {
      var sample = result.rows[0] || {};
      var cols = [
        { key: "docNo", title: "Doc / Code" }, { key: "code", title: "Code" }, { key: "name", title: "Name" },
        { key: "customerName", title: "Partner" }, { key: "supplierName", title: "Supplier" },
        { key: "total", title: "Total", type: "money" }, { key: "value", title: "Value", type: "money" },
        { key: "qty", title: "Qty", type: "qty" }, { key: "status", title: "Status", type: "status" },
        { key: "amount", title: "Amount", type: "money" }, { key: "debit", title: "Debit", type: "money" },
        { key: "credit", title: "Credit", type: "money" }, { key: "account", title: "Account" },
        { key: "sku", title: "SKU" }, { key: "warehouse", title: "WH" }, { key: "stage", title: "Stage" },
        { key: "balance", title: "Balance", type: "money" }, { key: "openAmount", title: "Open", type: "money" }
      ].filter(function (c) {
        return result.rows.some(function (r) { return r[c.key] != null; }) || sample[c.key] != null;
      });
      if (!cols.length) cols = Object.keys(sample).slice(0, 6).map(function (k) { return { key: k, title: k }; });
      var sum = 0;
      result.rows.forEach(function (r) { sum += Number(r[conf.metric]) || 0; });
      var pseudo = Object.assign({}, page, { collection: conf.collection, columns: cols, readOnly: true, title: page.title });
      host.innerHTML =
        '<div class="jn-card jn-report-page">' +
        '<div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0">' + JnUI.esc(page.title) + "</h2>" +
        '<span class="jn-status OPEN">ReportPageTemplate</span><span class="spacer"></span>' +
        '<button type="button" class="jn-btn" data-action="export-report">Export CSV</button>' +
        '<button type="button" class="jn-btn" data-action="refresh">Refresh</button></div>' +
        '<div class="jn-filter-bar"><div class="jn-field"><label for="rpt-q">Filter</label>' +
        '<input class="jn-input" id="rpt-q" data-role="search" value="' + JnUI.esc(state.query) + '" placeholder="Contains..." /></div>' +
        '<button type="button" class="jn-btn primary" data-action="apply-filter">Apply</button></div>' +
        '<div class="jn-report-summary">' +
        '<div class="jn-kpi"><div class="k">Rows</div><div class="v">' + result.total + '</div><div class="d">filtered</div></div>' +
        '<div class="jn-kpi"><div class="k">Sum(' + JnUI.esc(conf.metric) + ')</div><div class="v">' + JnUI.esc(JnUI.money(sum)) + '</div><div class="d">page slice</div></div>' +
        '<div class="jn-kpi"><div class="k">Metric</div><div class="v">-</div><div class="d"></div></div>' +
        "</div>" +
        '<div class="jn-demo-save-note">Export is browser CSV for the current page.</div>' +
        tableHtml(pseudo, result) + "</div>";
      bindListActions(host, pseudo, function () { renderReport(host, page); });
      var exp = host.querySelector('[data-action="export-report"]');
      if (exp) exp.onclick = function () {
        var lines = [cols.map(function (c) { return c.title; }).join(",")];
        result.rows.forEach(function (r) {
          lines.push(cols.map(function (c) {
            var v = r[c.key] == null ? "" : String(r[c.key]);
            if (/^[=+\-@]/.test(v)) v = "'" + v;
            if (/[",\n]/.test(v)) v = '"' + v.replace(/"/g, '""') + '"';
            return v;
          }).join(","));
        });
        var blob = new Blob(["\uFEFF" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
        var a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = page.pageId + "-report.csv";
        a.click();
        JnUI.toast("Exported report CSV", "ok");
      };
    }).catch(function (err) {
      host.innerHTML = '<div class="jn-error" role="alert">' + JnUI.esc((err && err.message) || tt("common.loadFailed")) + "</div>";
    });
  }

  function renderBomEditor(host, page) {
    JnData.active.list("boms", { page: 1, pageSize: 20 }).then(function (result) {
      var bom = result.rows[0] || { code: "BOM-NEW", itemSku: "", version: "1.0", status: "DRAFT", lines: [] };
      function paint() {
        var lines = (bom.lines || []).map(function (l, idx) {
          return "<tr>" +
            '<td><input class="jn-input" data-i="' + idx + '" data-k="componentSku" value="' + JnUI.esc(l.componentSku) + '" /></td>' +
            '<td><input class="jn-input" data-i="' + idx + '" data-k="qty" type="number" value="' + JnUI.esc(l.qty) + '" /></td>' +
            '<td><input class="jn-input" data-i="' + idx + '" data-k="uom" value="' + JnUI.esc(l.uom || "EA") + '" /></td>' +
            '<td><button type="button" class="jn-btn danger" data-del="' + idx + '">Del</button></td></tr>';
        }).join("");
        host.innerHTML =
          '<div class="jn-card"><div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0">BOM Editor</h2>' +
          '<button type="button" class="jn-btn" data-add>Add component</button>' +
          '<button type="button" class="jn-btn primary" data-save>Save BOM</button></div>' +
          '<div class="jn-form-grid">' +
          '<div class="jn-field"><label>Code</label><input class="jn-input" data-f="code" value="' + JnUI.esc(bom.code) + '" /></div>' +
          '<div class="jn-field"><label>Item SKU</label><input class="jn-input" data-f="itemSku" value="' + JnUI.esc(bom.itemSku) + '" /></div>' +
          '<div class="jn-field"><label>Version</label><input class="jn-input" data-f="version" value="' + JnUI.esc(bom.version) + '" /></div>' +
          '<div class="jn-field"><label>Status</label><div>' + JnUI.statusHtml(bom.status) + "</div></div></div>" +
          '<div class="jn-lines-editor jn-table-wrap" style="margin-top:12px"><table class="jn-table"><thead><tr><th>Component</th><th>Qty</th><th>UoM</th><th></th></tr></thead><tbody>' +
          (lines || '<tr><td colspan="4"><div class="jn-empty">No components</div></td></tr>') +
          "</tbody></table></div></div>";
        host.querySelector("[data-add]").onclick = function () {
          sync(); bom.lines.push({ componentSku: "", qty: 1, uom: "EA" }); paint();
        };
        host.querySelectorAll("[data-del]").forEach(function (b) {
          b.onclick = function () { sync(); bom.lines.splice(Number(b.getAttribute("data-del")), 1); paint(); };
        });
        host.querySelector("[data-save]").onclick = function () {
          sync();
          JnData.active.save("boms", bom).then(function () { JnUI.toast(tt("common.saved"), "ok"); });
        };
      }
      function sync() {
        host.querySelectorAll("[data-f]").forEach(function (inp) { bom[inp.getAttribute("data-f")] = inp.value; });
        host.querySelectorAll("[data-i]").forEach(function (inp) {
          var i = Number(inp.getAttribute("data-i"));
          var k = inp.getAttribute("data-k");
          var v = inp.value;
          if (k === "qty") v = Number(v);
          bom.lines[i][k] = v;
        });
      }
      paint();
    });
  }

  function renderMrp(host) {
    JnData.active.list("mrpSuggestions", { page: 1, pageSize: 50 }).then(function (result) {
      host.innerHTML =
        '<div class="jn-card"><div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0">MRP Workbench</h2>' +
        '<button type="button" class="jn-btn primary" data-run disabled>Run MRP</button></div>' +
        '<p style="color:var(--jn-color-text-muted)">MRP planning API is not available yet.</p>' +
        tableHtml({ columns: [
          { key: "sku", title: "SKU" },
          { key: "suggestedQty", title: "Suggested Qty", type: "qty" },
          { key: "reason", title: "Reason" },
          { key: "priority", title: "Priority", type: "status" }
        ], title: "MRP" }, result) + "</div>";
      host.querySelector("[data-run]").onclick = function () {
        JnUI.toast(tt("mrp.unavailableNote"), "err");
      };
    });
  }

  function renderSettings(host) {
    var theme = JnTheme.get();
    var cats = [
      { id: "appearance", title: tt("settings.appearance") },
      { id: "about", title: tt("settings.about") }
    ];
    var cat = host._settingsCat || "appearance";
    function paint() {
      var panel = "";
      if (cat === "appearance") {
        panel =
          '<div class="jn-field"><label>' + JnUI.esc(tt("settings.theme")) + '</label><div class="jn-toolbar">' +
          '<button type="button" class="jn-theme-chip' + (theme.theme === "light" ? " active" : "") + '" data-theme-set="light">' +
          JnUI.esc(tt("settings.themeLight")) + '</button></div></div>' +
          '<div class="jn-field" style="margin-top:12px"><label>' + JnUI.esc(tt("common.density")) + '</label><div class="jn-toolbar">' +
          '<button type="button" class="jn-density-toggle" data-den="comfortable">' + JnUI.esc(tt("common.comfortable")) + "</button>" +
          '<button type="button" class="jn-density-toggle" data-den="compact">' + JnUI.esc(tt("common.compact")) + "</button>" +
          '<span class="jn-muted">' + JnUI.esc(theme.density) + "</span></div></div>";
      } else {
        panel = "<p><strong>" + JnUI.esc(tt("app.brand", null, "JNative One")) + "</strong></p>" +
          '<p class="jn-muted">' + JnUI.esc(tt("settings.aboutBody")) + "</p>";
      }
      host.innerHTML =
        '<div class="jn-card jn-settings-page"><div class="jn-toolbar">' +
        '<h2 class="jn-card-title" style="margin:0">' + JnUI.esc(tt("page.sys.settings")) + "</h2>" +
        '<span class="spacer"></span></div>' +
        '<div class="jn-settings-layout"><nav class="jn-settings-nav" aria-label="Settings">' +
        cats.map(function (c) {
          return '<button type="button" data-cat="' + c.id + '" class="' + (cat === c.id ? "active" : "") + '">' + JnUI.esc(c.title) + "</button>";
        }).join("") +
        '</nav><div class="jn-settings-panel">' + panel + "</div></div></div>";
      host.querySelectorAll("[data-cat]").forEach(function (b) {
        b.onclick = function () { host._settingsCat = b.getAttribute("data-cat"); cat = host._settingsCat; theme = JnTheme.get(); paint(); };
      });
      host.querySelectorAll("[data-theme-set]").forEach(function (b) {
        b.onclick = function () { JnTheme.setTheme(b.getAttribute("data-theme-set")); theme = JnTheme.get(); paint(); };
      });
      host.querySelectorAll("[data-den]").forEach(function (b) {
        b.onclick = function () {
          JnTheme.setDensity(b.getAttribute("data-den"));
          theme = JnTheme.get();
          paint();
          JnUI.toast(tt("common.densityToast", { density: theme.density }), "ok");
        };
      });
    }
    paint();
  }

  function renderMasterDetail(host, page, id) {
    JnData.active.get(page.collection, id).then(function (rec) {
      if (!rec) {
        host.innerHTML = '<div class="jn-error">Record not found</div>';
        return;
      }
      var tab = host._detailTab || "basic";
      function paint() {
        var body = "";
        if (tab === "basic") {
          body = '<div class="jn-form-grid">' + (page.formFields || Object.keys(rec)).map(function (f) {
            return '<div class="jn-field"><label>' + JnUI.esc(f) + "</label>" +
              '<div class="jn-input" style="display:flex;align-items:center;background:var(--jn-color-surface-2)">' +
              JnUI.esc(rec[f] == null ? "" : rec[f]) + "</div></div>";
          }).join("") + "</div>";
        } else if (tab === "related") {
          var related = [];
          body = '<div class="jn-empty">' + JnUI.esc(tt("common.relatedUnavailable")) + "</div>";
        } else if (tab === "contacts") {
          body = '<div class="jn-empty">Contacts: email ' +
            JnUI.esc(rec.email || "(none)") + ", phone " + JnUI.esc(rec.phone || "(none)") + "</div>";
        } else if (tab === "attachments") {
          body = '<div class="jn-empty">No attachments</div>';
        } else {
          body = '<ul class="jn-timeline"><li>Opened record</li>' +
            "<li>Record id " + JnUI.esc(rec.id) + "</li></ul>";
        }
        host.innerHTML =
          '<div class="jn-card jn-master-detail">' +
          '<div class="jn-toolbar"><button type="button" class="jn-btn" data-back>Back to list</button>' +
          "<h2 class=\"jn-card-title\" style=\"margin:0\">" + JnUI.esc(page.title) + " | " + JnUI.esc(rec.name || rec.code || rec.id) + "</h2>" +
          '<span class="spacer"></span><button type="button" class="jn-btn primary" data-edit>Edit</button></div>' +
          '<div class="jn-detail-tabs">' +
          ["basic", "related", "contacts", "attachments", "audit"].map(function (t) {
            var label = { basic: "Basic info", related: "Related business", contacts: "Contacts", attachments: "Attachments", audit: "Activity" }[t];
            return '<button type="button" data-tab="' + t + '" class="' + (tab === t ? "active" : "") + '">' + label + "</button>";
          }).join("") +
          '</div><div class="jn-detail-layout"><div>' + body + "</div>" +
          '<div class="jn-card" style="box-shadow:none"><div class="jn-card-title">Status</div>' +
          JnUI.statusHtml(rec.status || "ACTIVE") +
          '</div></div></div>';
        host.querySelector("[data-back]").onclick = function () { renderMasterList(host, page); };
        host.querySelector("[data-edit]").onclick = function () { openEditor(page, id, function () { renderMasterDetail(host, page, id); }); };
        host.querySelectorAll("[data-tab]").forEach(function (b) {
          b.onclick = function () { host._detailTab = b.getAttribute("data-tab"); tab = host._detailTab; paint(); };
        });
      }
      paint();
    });
  }

  function renderImportExport(host) {
    host.innerHTML =
      '<div class="jn-card"><h2 class="jn-card-title">CSV \u5bfc\u5165 / \u5bfc\u51fa</h2>' +
      '<p style="color:var(--jn-color-text-muted)">\u771f\u5b9e\u670d\u52a1\u7aef UTF-8 \u89e3\u6790\uff1b\u9884\u89c8\u540e\u4e8b\u52a1\u5199\u5165\uff1b\u9ed8\u8ba4\u4e0d\u8986\u76d6\u5df2\u6709\u7f16\u7801\u3002</p>' +
      '<div class="jn-toolbar">' +
      '<button type="button" class="jn-btn" data-tpl-p>\u4f19\u4f34\u6a21\u677f</button>' +
      '<button type="button" class="jn-btn" data-tpl-i>\u5546\u54c1\u6a21\u677f</button>' +
      '<button type="button" class="jn-btn primary" data-prev-p>\u9884\u89c8\u4f19\u4f34</button>' +
      '<button type="button" class="jn-btn primary" data-imp-p>\u5bfc\u5165\u4f19\u4f34</button>' +
      '<button type="button" class="jn-btn primary" data-prev-i>\u9884\u89c8\u5546\u54c1</button>' +
      '<button type="button" class="jn-btn primary" data-imp-i>\u5bfc\u5165\u5546\u54c1</button></div>' +
      '<textarea class="jn-input" id="csv-body" rows="10" style="width:100%;font-family:monospace" placeholder="\u7c98\u8d34 CSV\u2026"></textarea>' +
      '<pre class="jn-empty" data-out style="white-space:pre-wrap;margin-top:12px">Ready.</pre></div>';
    function csrfFetch(path, body) {
      return fetch(path, {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": sessionStorage.getItem("jn_csrf") || ""
        },
        body: JSON.stringify(body || {})
      }).then(function (r) { return r.json().then(function (j) { if (!r.ok) throw new Error((j && j.message) || r.statusText); return j; }); });
    }
    host.querySelector("[data-tpl-p]").onclick = function () {
      fetch("/api/erp/import/templates/partners", { credentials: "same-origin" }).then(function (r) { return r.json(); })
        .then(function (j) { host.querySelector("#csv-body").value = j.csv || ""; JnUI.toast("\u5df2\u52a0\u8f7d\u4f19\u4f34\u6a21\u677f", "ok"); })
        .catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-tpl-i]").onclick = function () {
      fetch("/api/erp/import/templates/items", { credentials: "same-origin" }).then(function (r) { return r.json(); })
        .then(function (j) { host.querySelector("#csv-body").value = j.csv || ""; JnUI.toast("\u5df2\u52a0\u8f7d\u5546\u54c1\u6a21\u677f", "ok"); })
        .catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-prev-p]").onclick = function () {
      csrfFetch("/api/erp/import/partners/preview", { csv: host.querySelector("#csv-body").value })
        .then(function (j) { host.querySelector("[data-out]").textContent = JSON.stringify(j, null, 2); })
        .catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-imp-p]").onclick = function () {
      csrfFetch("/api/erp/import/partners", { csv: host.querySelector("#csv-body").value, jobCode: "UI-" + Date.now() })
        .then(function (j) { host.querySelector("[data-out]").textContent = JSON.stringify(j); JnUI.toast("\u5bfc\u5165\u5b8c\u6210", "ok"); })
        .catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-prev-i]").onclick = function () {
      csrfFetch("/api/erp/import/items/preview", { csv: host.querySelector("#csv-body").value })
        .then(function (j) { host.querySelector("[data-out]").textContent = JSON.stringify(j, null, 2); })
        .catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-imp-i]").onclick = function () {
      csrfFetch("/api/erp/import/items", { csv: host.querySelector("#csv-body").value, jobCode: "UI-" + Date.now() })
        .then(function (j) { host.querySelector("[data-out]").textContent = JSON.stringify(j); JnUI.toast("\u5bfc\u5165\u5b8c\u6210", "ok"); })
        .catch(function (e) { JnUI.toast(e.message, "err"); });
    };
  }

  function renderHelpTutorials(host) {
    host.innerHTML = '<div class="jn-card"><h2 class="jn-card-title">\u4e2d\u6587\u64cd\u4f5c\u6559\u7a0b</h2><p style="color:var(--jn-color-text-muted)">\u7ec3\u4e60\u6599\uff1aFG-PRACTICE-A / WH-PRACTICE\uff08\u4ec5\u6f14\u793a\u8d26\u5957\uff09</p><div data-list></div></div>';
    var list = (window.JnHelpZh && JnHelpZh.tutorials) || [];
    host.querySelector("[data-list]").innerHTML = list.map(function (t) {
      return '<div style="margin:12px 0;padding:12px 0;border-bottom:1px solid var(--jn-color-border,#e5e7eb)">' +
        "<h3>" + JnUI.esc(t.id + " \u00b7 " + t.title) + "</h3>" +
        "<p><strong>\u89d2\u8272</strong> " + JnUI.esc(t.role) + " \u00b7 <strong>\u83dc\u5355</strong> " + JnUI.esc(t.menu) + "</p>" +
        "<p><strong>\u8f93\u5165</strong> " + JnUI.esc(t.input) + "</p>" +
        "<p><strong>\u9884\u671f</strong> " + JnUI.esc(t.expect) + "</p>" +
        "<p style=\"color:#b45309\"><strong>\u5e38\u89c1\u9519\u8bef</strong> " + JnUI.esc(t.commonError) + "</p></div>";
    }).join("") || "<p>\u65e0\u6559\u7a0b</p>";
  }

  function renderSetupWizard(host) {
    host.innerHTML =
      '<div class="jn-card"><h2 class="jn-card-title">\u9996\u6b21\u4f7f\u7528\u5411\u5bfc\uff08\u6b63\u5f0f\u8d26\u5957\uff09</h2>' +
      '<p style="color:var(--jn-color-text-muted)">\u7a7a\u6b63\u5f0f\u8d26\u5957\uff1a\u516c\u53f8/\u65f6\u533a \u2192 \u5e01\u79cd \u2192 \u4ed3\u5e93 \u2192 \u7f16\u53f7 \u2192 \u7528\u6237 \u2192 \u4e3b\u6570\u636e \u2192 \u671f\u521d \u2192 \u6821\u9a8c\u3002</p>' +
      '<pre data-state style="white-space:pre-wrap">\u52a0\u8f7d\u4e2d\u2026</pre>' +
      '<div class="jn-toolbar" style="flex-wrap:wrap;gap:8px">' +
      '<button type="button" class="jn-btn" data-refresh>\u5237\u65b0\u72b6\u6001</button>' +
      '<button type="button" class="jn-btn" data-company>\u4fdd\u5b58\u516c\u53f8</button>' +
      '<button type="button" class="jn-btn" data-currency>\u4fdd\u5b58\u5e01\u79cd</button>' +
      '<button type="button" class="jn-btn" data-wh>\u521b\u5efa\u4ed3\u5e93</button>' +
      '<button type="button" class="jn-btn" data-num>\u7f16\u53f7\u89c4\u5219</button>' +
      '<button type="button" class="jn-btn" data-open>\u8fdb\u5165\u671f\u521d</button>' +
      '<button type="button" class="jn-btn" data-val>\u8fdb\u5165\u6821\u9a8c</button>' +
      '<button type="button" class="jn-btn primary" data-done>\u5b8c\u6210\u6821\u9a8c</button></div></div>';
    function hdr() {
      return { "Content-Type": "application/json", "X-CSRF-Token": sessionStorage.getItem("jn_csrf") || "" };
    }
    function refresh() {
      fetch("/api/erp/setup/wizard", { credentials: "same-origin" }).then(function (r) { return r.json(); })
        .then(function (j) { host.querySelector("[data-state]").textContent = JSON.stringify(j, null, 2); })
        .catch(function (e) { host.querySelector("[data-state]").textContent = e.message; });
    }
    function post(path, body) {
      return fetch(path, { method: "POST", credentials: "same-origin", headers: hdr(), body: JSON.stringify(body || {}) })
        .then(function (r) { return r.json().then(function (j) { if (!r.ok) throw new Error(j.message || r.statusText); return j; }); });
    }
    host.querySelector("[data-refresh]").onclick = refresh;
    host.querySelector("[data-company]").onclick = function () {
      post("/api/erp/setup/wizard/company", { name: "\u6b63\u5f0f\u516c\u53f8", timezone: "Asia/Shanghai" }).then(refresh).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-currency]").onclick = function () {
      post("/api/erp/setup/wizard/currency", { currency: "CNY", amountScale: 0 }).then(refresh).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-wh]").onclick = function () {
      post("/api/erp/setup/wizard/warehouse", { code: "WH1", name: "\u4e3b\u4ed3" }).then(refresh).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-num]").onclick = function () {
      post("/api/erp/setup/wizard/numbering", {}).then(refresh).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-open]").onclick = function () {
      post("/api/erp/setup/wizard/advance", { to: "OPENING" }).then(refresh).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-val]").onclick = function () {
      post("/api/erp/setup/wizard/advance", { to: "VALIDATE" }).then(refresh).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    host.querySelector("[data-done]").onclick = function () {
      post("/api/erp/setup/wizard/complete", {}).then(function (j) {
        host.querySelector("[data-state]").textContent = JSON.stringify(j, null, 2);
        JnUI.toast("\u5411\u5bfc\u5b8c\u6210", "ok");
      }).catch(function (e) { JnUI.toast(e.message, "err"); });
    };
    refresh();
  }

  function renderOpeningStock(host) {
    host.innerHTML =
      '<div class="jn-card"><h2 class="jn-card-title">\u671f\u521d\u5e93\u5b58\u5355\u636e</h2>' +
      '<p>\u72ec\u7acb\u671f\u521d\u5355\u636e\uff1a\u8349\u7a3f \u2192 \u8fc7\u8d26\uff1b\u9700 Idempotency-Key\u3002\u4f30\u503c\u8303\u56f4\u4e3a\u660e\u786e\u5355\u4ef7\uff0c\u4e0d\u58f0\u79f0\u5b8c\u6574\u603b\u8d26\u3002</p>' +
      '<div class="jn-field"><label>warehouseId</label><input class="jn-input" id="os-wh" value="1" /></div>' +
      '<div class="jn-field"><label>itemId</label><input class="jn-input" id="os-item" value="1" /></div>' +
      '<div class="jn-field"><label>quantity</label><input class="jn-input" id="os-qty" value="20" /></div>' +
      '<div class="jn-field"><label>unitCostAmount</label><input class="jn-input" id="os-cost" value="5000" /></div>' +
      '<button type="button" class="jn-btn primary" data-post>\u521b\u5efa\u5e76\u8fc7\u8d26</button>' +
      '<pre data-out style="margin-top:12px;white-space:pre-wrap"></pre></div>';
    host.querySelector("[data-post]").onclick = function () {
      var key = "ui-os-" + Date.now();
      var body = {
        warehouseId: Number(host.querySelector("#os-wh").value),
        businessDate: Date.now(),
        notes: "UI opening",
        idempotencyKey: key,
        lines: [{ itemId: Number(host.querySelector("#os-item").value), quantity: Number(host.querySelector("#os-qty").value), unitCostAmount: Number(host.querySelector("#os-cost").value) }]
      };
      fetch("/api/erp/opening/stock", {
        method: "POST", credentials: "same-origin",
        headers: { "Content-Type": "application/json", "Idempotency-Key": key, "X-CSRF-Token": sessionStorage.getItem("jn_csrf") || "" },
        body: JSON.stringify(body)
      }).then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
        .then(function (res) {
          if (!res.ok || (res.j && res.j.ok === 0)) {
            throw new Error((res.j && res.j.message) || "draft failed");
          }
          var doc = res.j.documentJson ? JSON.parse(res.j.documentJson) : res.j;
          var id = doc.id;
          var key2 = key + "-post";
          return fetch("/api/erp/opening/stock/" + id + "/post", {
            method: "POST", credentials: "same-origin",
            headers: { "Content-Type": "application/json", "Idempotency-Key": key2, "X-CSRF-Token": sessionStorage.getItem("jn_csrf") || "" },
            body: JSON.stringify({ idempotencyKey: key2 })
          }).then(function (r2) { return r2.json(); });
        }).then(function (j) {
          host.querySelector("[data-out]").textContent = JSON.stringify(j, null, 2);
          JnUI.toast("\u671f\u521d\u5df2\u8fc7\u8d26", "ok");
        }).catch(function (e) {
          host.querySelector("[data-out]").textContent = e.message;
          JnUI.toast(e.message, "err");
        });
    };
  }

  global.JnTemplates = {
    openEditor: openEditor,
    render: function (host, page) {
      host.innerHTML = '<div class="jn-loading" role="status">' + JnUI.esc(tt("common.loading")) + "</div>";
      try {
        if (page.template === "dashboard") return renderDashboard(host, page);
        if (page.template === "masterList") return renderMasterList(host, page);
        if (page.template === "documentList") return renderDocumentList(host, page);
        if (page.template === "report") return renderReport(host, page);
        if (page.template === "bomEditor") return renderBomEditor(host, page);
        if (page.template === "mrp") return renderMrp(host, page);
        if (page.template === "settings") return renderSettings(host, page);
        if (page.template === "importExport") return renderImportExport(host, page);
        if (page.template === "helpTutorials") return renderHelpTutorials(host, page);
        if (page.template === "setupWizard") return renderSetupWizard(host, page);
        if (page.template === "openingStock") return renderOpeningStock(host, page);
        host.innerHTML = '<div class="jn-error" role="alert">Unknown template: ' + JnUI.esc(page.template) + "</div>";
      } catch (e) {
        host.innerHTML = '<div class="jn-error" role="alert">Failed to render: ' + JnUI.esc(e.message) + "</div>";
      }
    }
  };
})(window);
