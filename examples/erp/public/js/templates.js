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

  function toolbar(page, opts) {
    opts = opts || {};
    return (
      '<div class="jn-toolbar">' +
      "<h2 class=\"jn-card-title\" style=\"margin:0;flex:1\">" + JnUI.esc(page.title) + "</h2>" +
      (opts.hideNew ? "" : '<button type="button" class="jn-btn primary" data-action="new">New</button>') +
      '<button type="button" class="jn-btn" data-action="refresh">Refresh</button>' +
      "</div>"
    );
  }

  function filterBar(page) {
    return (
      '<div class="jn-filter-bar">' +
      '<div class="jn-field"><label>Search</label><input class="jn-input" data-role="search" value="' + JnUI.esc(state.query) + '" placeholder="Filter rows..." /></div>' +
      '<div class="jn-field"><label>Status</label><select class="jn-select" data-role="status">' +
      '<option value="">All</option><option value="ACTIVE">ACTIVE</option><option value="OPEN">OPEN</option><option value="DRAFT">DRAFT</option><option value="APPROVED">APPROVED</option><option value="POSTED">POSTED</option><option value="PARTIAL">PARTIAL</option>' +
      "</select></div>" +
      '<button type="button" class="jn-btn primary" data-action="apply-filter">Apply</button>' +
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
        '<td><button type="button" class="jn-btn" data-action="view" data-id="' + JnUI.esc(row.id) + '">Open</button></td></tr>';
    }).join("");
    if (!body) body = '<tr><td colspan="' + (cols.length + 1) + '"><div class="jn-empty">No records match the filter.</div></td></tr>';
    var from = (result.total === 0) ? 0 : ((result.page - 1) * result.pageSize + 1);
    var to = Math.min(result.page * result.pageSize, result.total);
    return (
      '<div class="jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>" +
      '<div class="jn-pagination"><span>' + from + "-" + to + " of " + result.total + " | DEMO DATA</span>" +
      '<span class="spacer" style="flex:1"></span>' +
      '<button type="button" class="jn-btn" data-action="prev" ' + (result.page <= 1 ? "disabled" : "") + ">Prev</button>" +
      '<button type="button" class="jn-btn" data-action="next" ' + (to >= result.total ? "disabled" : "") + ">Next</button></div>"
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
          title: page.title + " | View",
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
      var body = formFieldsHtml(fields, rec);
      body = body + '<div class="jn-demo-save-note">Save updates DEMO mock state only - not the formal business database.</div>' +
        '<div class="jn-checkbox-row" style="margin-top:8px"><input type="checkbox" id="jn-confirm-demo" checked disabled /> <label for="jn-confirm-demo">I understand this is DEMO DATA</label></div>';
      JnUI.modal({
        title: (id ? "Edit " : "New ") + page.title,
        bodyHtml: body,
        primaryLabel: "Save DEMO",
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
                em.textContent = "Required";
                field.appendChild(em);
              }
            }
            if (input.type === "number") v = Number(v);
            rec[f] = v;
          });
          if (invalid) {
            JnUI.toast("Fix validation errors", "err");
            return false;
          }
          JnData.active.save(page.collection, rec).then(function () {
            JnUI.toast("DEMO state updated (not persisted to formal DB)", "ok");
            if (onSaved) onSaved();
          });
          return true;
        }
      });
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
        '<div class="jn-card"><div class="jn-panel-title">Document References</div><ul class="jn-timeline"><li>Created in DEMO mode</li><li>Linked inventory / GL not posted to live DB</li></ul></div>' +
        '<div class="jn-card"><div class="jn-panel-title">Approval Timeline</div><ul class="jn-timeline"><li>Draft saved locally</li><li>Awaiting demo approval</li></ul></div>' +
        "</div>" +
        '<div class="jn-demo-save-note">Save updates DEMO mock state only - not the formal business database.</div>'
      );
    }
    var backdrop = JnUI.modal({
      title: page.title + " | Editor",
      bodyHtml: renderBody(),
      primaryLabel: "Save DEMO Draft",
      onOk: function () {
        syncFromDom(backdrop);
        rec.total = JnMockData.recalcLines(rec.lines);
        if (!rec.docNo || rec.docNo === "(new)") rec.docNo = "DEMO-" + Date.now().toString().slice(-6);
        JnData.active.save(page.collection, rec).then(function () {
          JnUI.toast("DEMO draft updated (not formal DB)", "ok");
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
    JnData.active.dashboard(page.dashKind || "executive").then(function (data) {
      var kpis = (data.kpis || []).map(function (k) {
        return '<div class="jn-kpi" role="group" aria-label="' + JnUI.esc(k.k) + '"><div class="k">' + JnUI.esc(k.k) + '</div><div class="v">' + JnUI.esc(k.v) + '</div><div class="d">' + JnUI.esc(k.d) + "</div></div>";
      }).join("");
      var trend = data.trend || { label: "Trend (DEMO)", points: [] };
      var todos = (data.todos || []).map(function (t) {
        return '<div class="jn-todo-item"><span>' + JnUI.esc(t.text) + '</span><button type="button" data-go="' + JnUI.esc(t.go) + '">Open</button></div>';
      }).join("") || '<div class="jn-empty">No open tasks</div>';
      var recent = (data.recent || []).map(function (r) {
        return '<div class="jn-recent-item"><span><strong>' + JnUI.esc(r.docNo) + "</strong> " + JnUI.esc(r.partner) +
          " " + JnUI.statusHtml(r.status) + '</span><button type="button" data-go="' + JnUI.esc(r.go) + '">View</button></div>';
      }).join("") || '<div class="jn-empty">No recent documents</div>';
      var quick = (data.quickActions || []).map(function (q, i) {
        return '<button type="button" class="jn-btn' + (i === 0 ? " primary" : "") + '" data-go="' + JnUI.esc(q.go) + '">' + JnUI.esc(q.label) + "</button>";
      }).join("");
      host.innerHTML =
        '<div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0">' + JnUI.esc(page.title) + '</h2>' +
        '<span class="jn-status OPEN">DEMO MODE</span><span class="spacer"></span>' +
        '<button type="button" class="jn-btn" data-go="demo.center">Demo Center</button></div>' +
        '<div class="jn-kpi-grid">' + kpis + "</div>" +
        '<div class="jn-dash-grid">' +
        '<div class="jn-card"><div class="jn-card-title">Chart | ' + JnUI.esc(trend.label) + "</div>" + sparkSvg(trend.points) +
        '<div class="jn-demo-save-note">Derived from DemoStateStore - not live analytics warehouse.</div></div>' +
        '<div class="jn-card"><div class="jn-card-title">Task List</div>' + todos + "</div>" +
        '<div class="jn-card"><div class="jn-card-title">Recent Documents</div>' + recent + "</div>" +
        "</div>" +
        '<div class="jn-card" style="margin-top:16px"><div class="jn-card-title">Quick Actions</div><div class="jn-toolbar">' + quick + "</div></div>";
      host.querySelectorAll("[data-go]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          global.JnApp.navigate(btn.getAttribute("data-go"));
        });
      });
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
    var kpis = JnDemoState.computeKpis();
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
        '<div class="jn-kpi"><div class="k">Rows</div><div class="v">' + result.total + '</div><div class="d">filtered DEMO</div></div>' +
        '<div class="jn-kpi"><div class="k">Sum(' + JnUI.esc(conf.metric) + ')</div><div class="v">' + JnUI.esc(JnUI.money(sum)) + '</div><div class="d">page slice</div></div>' +
        '<div class="jn-kpi"><div class="k">AR Open</div><div class="v">' + JnUI.esc(JnDemoState.fmtMoney(kpis.arOpen)) + '</div><div class="d">DemoStateStore</div></div>' +
        '<div class="jn-kpi"><div class="k">Inventory</div><div class="v">' + JnUI.esc(JnDemoState.fmtMoney(kpis.inventoryValue)) + '</div><div class="d">DemoStateStore</div></div>' +
        "</div>" +
        '<div class="jn-demo-save-note">ReportPageTemplate | DEMO DATA | export is browser CSV only.</div>' +
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
        a.download = page.pageId + "-report-DEMO.csv";
        a.click();
        JnUI.toast("Exported DEMO report CSV", "ok");
      };
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
          JnData.active.save("boms", bom).then(function () { JnUI.toast("BOM saved (DEMO)", "ok"); });
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
        '<button type="button" class="jn-btn primary" data-run>Run MRP (DEMO)</button></div>' +
        '<p style="color:var(--jn-color-text-muted)">Suggestions are mock planning output - DEMO DATA.</p>' +
        tableHtml({ columns: [
          { key: "sku", title: "SKU" },
          { key: "suggestedQty", title: "Suggested Qty", type: "qty" },
          { key: "reason", title: "Reason" },
          { key: "priority", title: "Priority", type: "status" }
        ], title: "MRP" }, result) + "</div>";
      host.querySelector("[data-run]").onclick = function () {
        JnUI.toast("MRP run complete (DEMO - no DB writes)", "ok");
      };
    });
  }

  function renderSettings(host) {
    JnData.active.list("settings", { page: 1, pageSize: 50 }).then(function (result) {
      var counts = JnDemoSeed.counts(JnDemoState.getState());
      var theme = JnTheme.get();
      var cats = [
        { id: "general", title: "General" },
        { id: "appearance", title: "Appearance" },
        { id: "demo", title: "Demo Data" },
        { id: "about", title: "About" }
      ];
      var cat = host._settingsCat || "general";
      function paint() {
        var panel = "";
        if (cat === "general") {
          panel = '<div class="jn-form-grid">' + result.rows.map(function (r, idx) {
            return '<div class="jn-field"><label for="set-' + idx + '">' + JnUI.esc(r.key) + "</label>" +
              '<input class="jn-input" id="set-' + idx + '" data-idx="' + idx + '" value="' + JnUI.esc(r.value) + '" /></div>';
          }).join("") + "</div>";
        } else if (cat === "appearance") {
          panel =
            '<div class="jn-field"><label>Theme</label><div class="jn-toolbar">' +
            '<button type="button" class="jn-theme-chip' + (theme.theme === "light" ? " active" : "") + '" data-theme-set="light">Light</button>' +
            '<button type="button" class="jn-theme-chip" data-theme-set="dark" title="Reserved">Dark (preview tokens)</button></div>' +
            '<div class="jn-muted">Dark theme tokens are reserved; Light is the commercial default.</div></div>' +
            '<div class="jn-field" style="margin-top:12px"><label>Density</label><div class="jn-toolbar">' +
            '<button type="button" class="jn-density-toggle" data-den="comfortable">Comfortable</button>' +
            '<button type="button" class="jn-density-toggle" data-den="compact">Compact</button>' +
            '<span class="jn-muted">Current: ' + JnUI.esc(theme.density) + "</span></div></div>";
        } else if (cat === "demo") {
          panel =
            '<div class="jn-demo-save-note">Seed: ' + counts.customers + " customers, " + counts.suppliers +
            " suppliers, " + counts.items + " items, " + counts.warehouses + " warehouses, " + counts.users +
            " users.</div>" +
            "<p>Persistence: " + JnUI.esc(JnDemoState.persistenceNote()) + "</p>" +
            '<p class="jn-muted">Refresh keeps data. Reset / clearing site data restores seed. Never server DB.</p>' +
            '<button type="button" class="jn-btn danger" data-reset>Reset Demo Data</button>';
        } else {
          panel = "<p><strong>JNative One</strong> UI 1.5 Commercial Polish</p>" +
            '<p class="jn-muted">Product shell: examples/erp/public | WASM SPA: BLOCKED</p>' +
            '<button type="button" class="jn-btn" data-go-demo>Open Demo Center</button>';
        }
        host.innerHTML =
          '<div class="jn-card jn-settings-page"><div class="jn-toolbar">' +
          '<h2 class="jn-card-title" style="margin:0">System Settings</h2>' +
          '<span class="jn-status OPEN">SettingsPageTemplate</span><span class="spacer"></span>' +
          (cat === "general" ? '<button type="button" class="jn-btn primary" data-save>Save</button>' : "") +
          "</div>" +
          '<div class="jn-settings-layout"><nav class="jn-settings-nav" aria-label="Settings categories">' +
          cats.map(function (c) {
            return '<button type="button" data-cat="' + c.id + '" class="' + (cat === c.id ? "active" : "") + '">' + JnUI.esc(c.title) + "</button>";
          }).join("") +
          '</nav><div class="jn-settings-panel">' + panel +
          '<div class="jn-field error" id="set-err" style="display:none"><div class="err-msg">Validation error</div></div>' +
          "</div></div></div>";
        host.querySelectorAll("[data-cat]").forEach(function (b) {
          b.onclick = function () { host._settingsCat = b.getAttribute("data-cat"); cat = host._settingsCat; theme = JnTheme.get(); paint(); };
        });
        var save = host.querySelector("[data-save]");
        if (save) save.onclick = function () {
          var invalid = false;
          host.querySelectorAll("[data-idx]").forEach(function (inp) {
            var i = Number(inp.getAttribute("data-idx"));
            if (String(inp.value).trim() === "") invalid = true;
            result.rows[i].value = inp.value;
            JnDemoState.upsert("settings", result.rows[i]);
          });
          var err = host.querySelector("#set-err");
          if (invalid) {
            err.style.display = "block";
            err.querySelector(".err-msg").textContent = "Values required - empty settings rejected";
            JnUI.toast("Fix validation errors", "err");
            return;
          }
          err.style.display = "none";
          JnUI.toast("Settings saved (DemoStateStore / localStorage)", "ok");
        };
        host.querySelectorAll("[data-theme-set]").forEach(function (b) {
          b.onclick = function () { JnTheme.setTheme(b.getAttribute("data-theme-set")); theme = JnTheme.get(); paint(); JnUI.toast("Theme: " + theme.theme + " (DEMO)", "ok"); };
        });
        host.querySelectorAll("[data-den]").forEach(function (b) {
          b.onclick = function () { JnTheme.setDensity(b.getAttribute("data-den")); theme = JnTheme.get(); paint(); JnUI.toast("Density: " + theme.density, "ok"); };
        });
        var reset = host.querySelector("[data-reset]");
        if (reset) reset.onclick = function () {
          if (!window.confirm("Reset all DEMO data to seed state?")) return;
          JnAppService.resetDemo();
          JnUI.toast("DEMO data reset", "ok");
          renderSettings(host);
        };
        var go = host.querySelector("[data-go-demo]");
        if (go) go.onclick = function () { JnApp.navigate("demo.center"); };
      }
      paint();
    });
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
          if (page.collection === "customers") {
            related = JnDemoState.collection("salesOrders").filter(function (r) {
              return r.customerId === rec.id || r.customerName === rec.name;
            }).slice(0, 8);
          }
          body = related.length
            ? '<ul class="jn-timeline">' + related.map(function (r) {
              return "<li><strong>" + JnUI.esc(r.docNo) + "</strong> " + JnUI.statusHtml(r.status) + " " + JnUI.esc(JnUI.money(r.total)) + "</li>";
            }).join("") + "</ul>"
            : '<div class="jn-empty">No related DEMO documents</div>';
        } else if (tab === "contacts") {
          body = '<div class="jn-empty">Contact roles are DEMO placeholders - email ' +
            JnUI.esc(rec.email || "(none)") + ", phone " + JnUI.esc(rec.phone || "(none)") + "</div>";
        } else if (tab === "attachments") {
          body = '<div class="jn-empty">No server attachments in DEMO MODE</div>';
        } else {
          body = '<ul class="jn-timeline"><li>Opened in MasterDataDetailTemplate (DEMO)</li>' +
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
          '<div class="jn-demo-save-note" style="margin-top:12px">MasterDataDetailTemplate | DEMO MODE</div></div></div></div>';
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
      '<div class="jn-card"><h2 class="jn-card-title">Import / Export</h2>' +
      '<p style="color:var(--jn-color-text-muted)">DEMO mode - files stay in the browser; no server upload.</p>' +
      '<div class="jn-toolbar">' +
      '<button type="button" class="jn-btn primary" data-exp>Export Customers CSV</button>' +
      '<button type="button" class="jn-btn" data-imp>Simulate Import</button></div>' +
      '<div class="jn-empty" data-out>Ready.</div></div>';
    host.querySelector("[data-exp]").onclick = function () {
      JnData.active.list("customers", { pageSize: 100 }).then(function (r) {
        var csv = "code,name,city\n" + r.rows.map(function (c) { return c.code + "," + c.name + "," + c.city; }).join("\n");
        host.querySelector("[data-out]").textContent = csv;
        JnUI.toast("Exported " + r.rows.length + " customers (DEMO)", "ok");
      });
    };
    host.querySelector("[data-imp]").onclick = function () {
      JnUI.toast("Import accepted into mock store (DEMO)", "ok");
      host.querySelector("[data-out]").textContent = "Import simulation complete.";
    };
  }

  global.JnTemplates = {
    openEditor: openEditor,
    render: function (host, page) {
      host.innerHTML = '<div class="jn-loading" role="status">Loading...</div>';
      try {
        if (page.template === "dashboard") return renderDashboard(host, page);
        if (page.template === "masterList") return renderMasterList(host, page);
        if (page.template === "documentList") return renderDocumentList(host, page);
        if (page.template === "report") return renderReport(host, page);
        if (page.template === "bomEditor") return renderBomEditor(host, page);
        if (page.template === "mrp") return renderMrp(host, page);
        if (page.template === "settings") return renderSettings(host, page);
        if (page.template === "importExport") return renderImportExport(host, page);
        if (page.template === "demoCenter") return JnDemoCenter.render(host);
        host.innerHTML = '<div class="jn-error" role="alert">Unknown template: ' + JnUI.esc(page.template) + "</div>";
      } catch (e) {
        host.innerHTML = '<div class="jn-error" role="alert">Failed to render: ' + JnUI.esc(e.message) + "</div>";
      }
    }
  };
})(window);
