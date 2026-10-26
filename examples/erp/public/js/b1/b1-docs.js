// JNative ERP One (b1 module) - unified marketing document editor
/**
 * Document types: SQ (sales quotation), ARINV, ARCM, PQ (purchase quotation), APINV, APCM.
 * Backed by /api/erp/b1/docs/:type, /api/erp/b1/copy/:type, /api/erp/b1/pricing, /credit, /rate.
 */
(function (global) {
  var B1 = global.JnB1;
  var tt = B1.tt, esc = B1.esc, api = B1.api, qs = B1.qs;

  var TYPES = {
    SQ: { sales: true, posting: false, titleKey: "b1.doc.SQ", title: "Sales Quotation", partner: "customers", dateKey: "b1.f.validUntil", copyTo: [{ to: "SO", labelKey: "b1.copy.toSO", label: "Copy to Sales Order" }], bases: [] },
    ARINV: { sales: true, posting: true, titleKey: "b1.doc.ARINV", title: "A/R Invoice", partner: "customers", copyTo: [{ to: "ARCM", labelKey: "b1.copy.toARCM", label: "Copy to A/R Credit Memo" }], bases: ["DL", "SO"] },
    ARCM: { sales: true, posting: true, titleKey: "b1.doc.ARCM", title: "A/R Credit Memo", partner: "customers", copyTo: [], bases: ["ARINV"] },
    PQ: { sales: false, posting: false, titleKey: "b1.doc.PQ", title: "Purchase Quotation", partner: "suppliers", dateKey: "b1.f.validUntil", copyTo: [{ to: "PO", labelKey: "b1.copy.toPO", label: "Copy to Purchase Order" }], bases: [] },
    APINV: { sales: false, posting: true, titleKey: "b1.doc.APINV", title: "A/P Invoice", partner: "suppliers", copyTo: [{ to: "APCM", labelKey: "b1.copy.toAPCM", label: "Copy to A/P Credit Memo" }], bases: ["GR", "PO"] },
    APCM: { sales: false, posting: true, titleKey: "b1.doc.APCM", title: "A/P Credit Memo", partner: "suppliers", copyTo: [], bases: ["APINV"] }
  };
  var BASE_LABEL = { DL: ["b1.base.DL", "Delivery"], SO: ["b1.base.SO", "Sales Order"], GR: ["b1.base.GR", "Goods Receipt"], PO: ["b1.base.PO", "Purchase Order"], ARINV: ["b1.doc.ARINV", "A/R Invoice"], APINV: ["b1.doc.APINV", "A/P Invoice"] };
  var BASE_COLLECTION = { DL: "deliveries", SO: "salesOrders", GR: "goodsReceipts", PO: "purchaseOrders" };

  function docTitle(type) { return tt(TYPES[type].titleKey, TYPES[type].title); }
  function round(n) { return Math.round(n); }
  function uuid() { return "ui-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10); }

  /* ------------------------------------------------------------------ list */
  var PAGE = 20;

  function renderDocs(host, page) {
    var type = (page.b1 || {}).docType;
    var T = TYPES[type];
    if (!T) { host.innerHTML = '<div class="jn-error">Unknown document type</div>'; return; }
    var st = { q: "", status: "", page: 1 };
    function load() {
      host.innerHTML = '<div class="jn-loading" role="status">' + esc(tt("common.loading", "Loading...")) + "</div>";
      api("/api/erp/b1/docs/" + type + qs({ q: st.q, status: st.status, limit: 500 })).then(function (rows) {
        paint(rows);
      }).catch(function (e) { host.innerHTML = '<div class="jn-error" role="alert">' + esc(B1.errMsg(e)) + "</div>"; });
    }
    function paint(all) {
      var total = all.length;
      var rows = all.slice((st.page - 1) * PAGE, st.page * PAGE);
      var cols = [["docNo", "b1.f.docNo", "Doc No"], ["partnerName", T.sales ? "b1.f.customer" : "b1.f.supplier", T.sales ? "Customer" : "Supplier"], ["docDate", "b1.f.docDate", "Date"],
        [T.posting ? "dueDate" : "validUntil", T.posting ? "b1.f.dueDate" : "b1.f.validUntil", T.posting ? "Due Date" : "Valid Until"], ["currency", "b1.f.currency", "Currency"],
        ["totalCents", "b1.f.total", "Total"], ["totalLocalCents", "b1.f.totalLocal", "Total (local)"], ["refNo", "b1.f.refNo", "Reference"], ["status", "common.status", "Status"]];
      var head = cols.map(function (c) { return "<th>" + esc(tt(c[1], c[2])) + "</th>"; }).join("") + "<th></th>";
      var body = rows.map(function (r) {
        var cells = cols.map(function (c) {
          var v = r[c[0]];
          if (c[0] === "docDate" || c[0] === "dueDate" || c[0] === "validUntil") return "<td>" + esc(B1.msToDateInput(v)) + "</td>";
          if (c[0] === "totalCents") return '<td class="jn-money">' + esc(B1.fmtMoney(v, r.currency)) + "</td>";
          if (c[0] === "totalLocalCents") return '<td class="jn-money">' + esc(B1.fmtMoney(v)) + "</td>";
          if (c[0] === "status") return "<td>" + global.JnUI.statusHtml(v) + "</td>";
          return "<td>" + esc(v == null ? "" : v) + "</td>";
        }).join("");
        return '<tr data-id="' + esc(r.id) + '">' + cells + '<td><button type="button" class="jn-btn" data-open="' + esc(r.id) + '">' + esc(tt("common.open", "Open")) + "</button></td></tr>";
      }).join("") || '<tr><td colspan="' + (cols.length + 1) + '"><div class="jn-empty">' + esc(tt("common.empty", "No records")) + "</div></td></tr>";
      var statuses = T.posting ? ["DRAFT", "POSTED", "REVERSED", "CANCELLED"] : ["DRAFT", "OPEN", "CLOSED", "CANCELLED"];
      var filt = '<div class="jn-filter-bar"><div class="jn-field"><label>' + esc(tt("common.search", "Search")) + '</label><input class="jn-input" data-role="q" value="' + esc(st.q) + '" /></div>' +
        '<div class="jn-field"><label>' + esc(tt("common.status", "Status")) + '</label><select class="jn-select" data-role="status"><option value="">' + esc(tt("common.all", "All")) + "</option>" +
        statuses.map(function (s) { return '<option value="' + s + '"' + (st.status === s ? " selected" : "") + ">" + esc(global.JnI18n ? global.JnI18n.statusLabel(s) : s) + "</option>"; }).join("") + "</select></div>" +
        '<button type="button" class="jn-btn primary" data-act="apply">' + esc(tt("common.apply", "Apply")) + "</button></div>";
      var from = total === 0 ? 0 : (st.page - 1) * PAGE + 1;
      var to = Math.min(st.page * PAGE, total);
      host.innerHTML = '<div class="jn-card"><div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0;flex:1">' + esc(page.title || docTitle(type)) + "</h2>" +
        '<button type="button" class="jn-btn primary" data-act="new">' + esc(tt("common.new", "New")) + "</button>" +
        (T.bases.length ? '<button type="button" class="jn-btn" data-act="copyfrom">' + esc(tt("b1.copyFrom", "Copy from...")) + "</button>" : "") +
        '<button type="button" class="jn-btn" data-act="refresh">' + esc(tt("common.refresh", "Refresh")) + "</button></div>" + filt +
        '<div class="jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>" +
        '<div class="jn-pagination"><span>' + esc(tt("common.of", "{from}-{to} of {total}", { from: from, to: to, total: total })) + '</span><span style="flex:1"></span>' +
        '<button type="button" class="jn-btn" data-act="prev"' + (st.page <= 1 ? " disabled" : "") + ">" + esc(tt("common.prev", "Prev")) + "</button>" +
        '<button type="button" class="jn-btn" data-act="next"' + (to >= total ? " disabled" : "") + ">" + esc(tt("common.next", "Next")) + "</button></div></div>";
      function readFilters() {
        var q = host.querySelector('[data-role="q"]'); st.q = q ? q.value : "";
        var s = host.querySelector('[data-role="status"]'); st.status = s ? s.value : "";
      }
      host.querySelectorAll("[data-act]").forEach(function (b) {
        b.onclick = function () {
          var a = b.getAttribute("data-act");
          if (a === "apply" || a === "refresh") { readFilters(); st.page = 1; B1.invalidate(); load(); }
          else if (a === "prev") { st.page = Math.max(1, st.page - 1); paint(all); }
          else if (a === "next") { st.page = st.page + 1; paint(all); }
          else if (a === "new") openDocEditor(type, null, load);
          else if (a === "copyfrom") openCopyFrom(type, load);
        };
      });
      var qi = host.querySelector('[data-role="q"]');
      if (qi) qi.onkeydown = function (e) { if (e.key === "Enter") { readFilters(); st.page = 1; load(); } };
      host.querySelectorAll("[data-open]").forEach(function (b) {
        b.onclick = function () { openDocEditor(type, b.getAttribute("data-open"), load); };
      });
    }
    load();
  }

  /* ---------------------------------------------------------- doc editor */
  function newDoc(type) {
    return { id: 0, docType: type, docNo: "", status: "DRAFT", partnerId: 0, docDate: Date.UTC(new Date().getFullYear(), new Date().getMonth(), new Date().getDate(), 12), dueDate: 0, validUntil: 0,
      currency: "", rateMicro: 0, paymentTermsId: 0, priceListId: 0, warehouseId: 0, shipTo: "", billTo: "", refNo: "", discountBps: 0, notes: "", lines: [], baseType: "", baseId: 0 };
  }
  function blankLine() {
    return { itemId: 0, description: "", quantity: 1, uomCode: "", unitPriceCents: 0, discountBps: 0, taxCodeId: 0, taxBps: 0, isInventory: 1, auto: true, baseType: "", baseId: 0, baseLineId: 0 };
  }

  function calcDoc(doc) {
    var sub = 0, tax = 0;
    doc.lines.forEach(function (l) {
      var gross = (Number(l.quantity) || 0) * (Number(l.unitPriceCents) || 0);
      var net = gross - round(gross * (Number(l.discountBps) || 0) / 10000);
      if (net < 0) net = 0;
      if ((Number(doc.discountBps) || 0) > 0) net = net - round(net * Number(doc.discountBps) / 10000);
      l.netCents = net;
      l.taxCents = round(net * (Number(l.taxBps) || 0) / 10000);
      l.totalCents = net + l.taxCents;
      sub += net; tax += l.taxCents;
    });
    doc.subtotalCents = sub; doc.taxCents = tax; doc.totalCents = sub + tax;
  }

  function openDocEditor(type, id, onChange, preset) {
    var T = TYPES[type];
    var doc = null;
    var lk = {};
    var backdrop = null;
    var idemKey = uuid();
    var itemByLabel = {};
    var credit = null;

    var need = [T.partner, "paymentTerms", "priceLists", "taxCodes", "currencies", "warehouses", "items"];
    Promise.all([
      B1.lookupAll(need),
      id ? api("/api/erp/b1/docs/" + type + "/" + encodeURIComponent(id)) : Promise.resolve(preset || newDoc(type))
    ]).then(function (res) {
      lk = res[0];
      doc = res[1];
      doc.lines = (doc.lines || []).map(function (l) { return Object.assign({ auto: false }, l); });
      if (!doc.currency) {
        (lk.currencies || []).forEach(function (c) { if (c.isLocal && !doc.currency) doc.currency = c.code; });
        if (!doc.currency && lk.currencies && lk.currencies.length) doc.currency = lk.currencies[0].code;
        doc.rateMicro = 1000000;
      }
      indexItems();
      backdrop = global.JnUI.modal({ title: docTitle(type), bodyHtml: "", primaryLabel: null });
      var m = backdrop.querySelector(".jn-modal");
      m.classList.add("jn-b1-wide");
      m.classList.add("jn-b1-doc");
      m.querySelector(".jn-modal-footer").innerHTML = "";
      refreshCredit().then(paint);
    }).catch(B1.toastErr);

    function indexItems() {
      itemByLabel = {};
      (lk.items || []).forEach(function (it) { itemByLabel[B1.labelOf(it, "items")] = it; });
    }
    function editable() { return doc.status === "DRAFT"; }
    function refreshCredit() {
      if (!T.sales || !doc.partnerId) { credit = null; return Promise.resolve(); }
      return api("/api/erp/b1/credit/" + doc.partnerId + qs({ extra: doc.totalLocalCents || doc.totalCents || 0 })).then(function (c) { credit = c; }).catch(function () { credit = null; });
    }

    function headerFields() {
      var ro = editable() ? "" : " disabled";
      function sel(key, label, rows, name, allowEmpty) {
        return '<div class="jn-field"><label>' + esc(label) + '</label><select class="jn-select" data-h="' + key + '"' + ro + ">" + B1.optionsHtml(rows, name, doc[key], allowEmpty) + "</select></div>";
      }
      function inp(key, label, type, val, extra) {
        return '<div class="jn-field"><label>' + esc(label) + '</label><input class="jn-input" type="' + type + '" data-h="' + key + '" value="' + esc(val == null ? "" : val) + '"' + ro + (extra || "") + " /></div>";
      }
      var curRows = (lk.currencies || []).map(function (c) { return { id: c.code, code: c.code, name: c.name }; });
      var curSel = '<div class="jn-field"><label>' + esc(tt("b1.f.currency", "Currency")) + '</label><select class="jn-select" data-h="currency"' + ro + ">" +
        curRows.map(function (c) { return '<option value="' + esc(c.code) + '"' + (c.code === doc.currency ? " selected" : "") + ">" + esc(c.code + " - " + c.name) + "</option>"; }).join("") + "</select>" +
        '<div class="hint" data-rate-hint>' + (doc.rateMicro ? esc(tt("b1.f.rate", "Rate") + ": " + (doc.rateMicro / 1000000)) : "") + "</div></div>";
      var partnerSel = '<div class="jn-field"><label>' + esc(T.sales ? tt("b1.f.customer", "Customer") : tt("b1.f.supplier", "Supplier")) + ' *</label><select class="jn-select" data-h="partnerId"' + (editable() && !doc.baseType ? "" : " disabled") + ">" +
        B1.optionsHtml(lk[T.partner], "partners", doc.partnerId, true) + "</select></div>";
      var dueKey = T.posting ? "dueDate" : "validUntil";
      var creditHtml = "";
      if (credit) {
        creditHtml = '<div class="jn-b1-credit' + (credit.blocked ? " blocked" : "") + '">' + esc(tt("b1.credit.exposure", "Exposure")) + ": " + esc(B1.fmtMoney(credit.exposureCents)) + " / " +
          esc(tt("b1.credit.limit", "Limit")) + ": " + (Number(credit.creditLimitCents) ? esc(B1.fmtMoney(credit.creditLimitCents)) : esc(tt("b1.unlimited", "unlimited"))) + (credit.blocked ? " - " + esc(tt("b1.credit.blocked", "Blocked")) : "") + "</div>";
      }
      return '<div class="jn-form-grid jn-b1-header">' + partnerSel +
        '<div class="jn-field"><label>' + esc(tt("b1.f.docNo", "Doc No")) + '</label><input class="jn-input" value="' + esc(doc.docNo || tt("b1.autoNo", "(auto)")) + '" disabled /></div>' +
        '<div class="jn-field"><label>' + esc(tt("common.status", "Status")) + '</label><div style="padding-top:4px">' + global.JnUI.statusHtml(doc.status) + "</div></div>" +
        inp("docDate", tt("b1.f.docDate", "Posting Date"), "date", B1.msToDateInput(doc.docDate)) +
        inp(dueKey, tt(T.posting ? "b1.f.dueDate" : "b1.f.validUntil", T.posting ? "Due Date" : "Valid Until"), "date", B1.msToDateInput(doc[dueKey])) +
        curSel +
        sel("paymentTermsId", tt("b1.f.paymentTermsId", "Payment Terms"), lk.paymentTerms, "paymentTerms", true) +
        sel("priceListId", tt("b1.f.priceListId", "Price List"), lk.priceLists, "priceLists", true) +
        sel("warehouseId", tt("b1.f.warehouseId", "Warehouse"), lk.warehouses, "warehouses", true) +
        inp("refNo", tt("b1.f.refNo", "Reference"), "text", doc.refNo) +
        inp("discountBps", tt("b1.f.headerDiscount", "Header Discount %"), "number", (Number(doc.discountBps) || 0) / 100, ' step="0.01" min="0" max="100"') +
        '<div class="jn-field span-2"><label>' + esc(tt("b1.f.shipTo", "Ship-to / Bill-to")) + '</label><input class="jn-input" data-h="shipTo" value="' + esc(doc.shipTo || "") + '"' + ro + " /></div>" +
        "</div>" + creditHtml;
    }

    function taxSel(l, i) {
      var ro = editable() ? "" : " disabled";
      return '<select class="jn-select" data-l="' + i + '" data-k="taxCodeId"' + ro + ">" + '<option value="0">-</option>' + (lk.taxCodes || []).map(function (t) {
        return '<option value="' + t.id + '"' + (String(t.id) === String(l.taxCodeId) ? " selected" : "") + ">" + esc(t.code + " " + (t.rateBps / 100) + "%") + "</option>";
      }).join("") + "</select>";
    }

    function linesHtml() {
      var ro = editable() ? "" : " disabled";
      var rows = doc.lines.map(function (l, i) {
        var it = B1.findById(lk.items, l.itemId);
        var label = it ? B1.labelOf(it, "items") : (l.itemSku ? l.itemSku : "");
        var itemDis = (!editable() || l.baseLineId > 0) ? " disabled" : "";
        return "<tr>" +
          "<td>" + (i + 1) + "</td>" +
          '<td><input class="jn-input" list="b1-items-dl" data-l="' + i + '" data-k="item" value="' + esc(label) + '"' + itemDis + " /></td>" +
          '<td><input class="jn-input" data-l="' + i + '" data-k="description" value="' + esc(l.description || "") + '"' + ro + " /></td>" +
          '<td><input class="jn-input" type="number" min="1" step="1" data-l="' + i + '" data-k="quantity" value="' + esc(l.quantity) + '"' + ro + ' style="width:72px" /></td>' +
          "<td>" + esc(l.uomCode || "") + "</td>" +
          '<td><input class="jn-input" type="number" min="0" step="0.01" data-l="' + i + '" data-k="unitPriceCents" value="' + esc(B1.centsToMajor(l.unitPriceCents)) + '"' + ro + ' style="width:96px" /></td>' +
          '<td><input class="jn-input" type="number" min="0" max="100" step="0.01" data-l="' + i + '" data-k="discountBps" value="' + esc((Number(l.discountBps) || 0) / 100) + '"' + ro + ' style="width:72px" /></td>' +
          "<td>" + taxSel(l, i) + "</td>" +
          '<td class="jn-money" data-net="' + i + '">' + esc(B1.fmtMoney(l.netCents)) + "</td>" +
          '<td class="jn-money" data-tax="' + i + '">' + esc(B1.fmtMoney(l.taxCents)) + "</td>" +
          '<td class="jn-money" data-tot="' + i + '">' + esc(B1.fmtMoney(l.totalCents)) + "</td>" +
          "<td>" + (l.baseType ? '<span class="jn-muted">' + esc(l.baseType) + "</span>" : "") + "</td>" +
          "<td>" + (editable() ? '<button type="button" class="jn-btn danger" data-del="' + i + '">x</button>' : "") + "</td></tr>";
      }).join("");
      var head = ["#", tt("b1.f.item", "Item"), tt("b1.f.description", "Description"), tt("b1.f.quantity", "Qty"), tt("b1.f.uom", "UoM"), tt("b1.f.unitPrice", "Unit Price"),
        tt("b1.f.discount", "Disc %"), tt("b1.f.taxCode", "Tax Code"), tt("b1.f.net", "Net"), tt("b1.f.tax", "Tax"), tt("b1.f.total", "Total"), tt("b1.f.base", "Base"), ""]
        .map(function (h) { return "<th>" + esc(h) + "</th>"; }).join("");
      var dl = '<datalist id="b1-items-dl">' + (lk.items || []).map(function (it) { return '<option value="' + esc(B1.labelOf(it, "items")) + '"></option>'; }).join("") + "</datalist>";
      return dl + '<div class="jn-lines-editor jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" +
        (rows || '<tr><td colspan="13"><div class="jn-empty">' + esc(tt("b1.noLines", "No lines. Add a line.")) + "</div></td></tr>") + "</tbody></table></div>" +
        (editable() && !doc.baseType ? '<div class="jn-toolbar" style="margin-top:8px"><button type="button" class="jn-btn" data-addline>' + esc(tt("b1.addLine", "Add line")) + "</button></div>" : "");
    }

    function totalsHtml() {
      var cur = doc.currency || "";
      return '<div class="jn-b1-totals"><div><span>' + esc(tt("b1.f.subtotal", "Subtotal")) + '</span><b data-sum="sub">' + esc(B1.fmtMoney(doc.subtotalCents, cur)) + "</b></div>" +
        "<div><span>" + esc(tt("b1.f.tax", "Tax")) + '</span><b data-sum="tax">' + esc(B1.fmtMoney(doc.taxCents, cur)) + "</b></div>" +
        '<div class="grand"><span>' + esc(tt("b1.f.total", "Total")) + '</span><b data-sum="tot">' + esc(B1.fmtMoney(doc.totalCents, cur)) + "</b></div>" +
        (doc.id && doc.currency && doc.rateMicro && doc.rateMicro !== 1000000 ? "<div><span>" + esc(tt("b1.f.totalLocal", "Total (local)")) + "</span><b>" + esc(B1.fmtMoney(doc.totalLocalCents)) + "</b></div>" : "") + "</div>";
    }

    function actionsHtml() {
      var b = "";
      function btn(act, label, cls) { return '<button type="button" class="jn-btn' + (cls ? " " + cls : "") + '" data-act="' + act + '">' + esc(label) + "</button>"; }
      if (editable()) {
        b += btn("save", tt("common.save", "Save"), "primary");
        if (T.posting) b += btn("post", tt("b1.post", "Post"), "primary");
        else b += btn("submit", tt("b1.submit", "Submit / Release"));
        if (doc.id) b += btn("cancel", tt("b1.cancelDoc", "Cancel document"), "danger");
      } else if (doc.status === "OPEN") {
        if (T.copyTo.length) T.copyTo.forEach(function (c) { b += btn("copyto:" + c.to, tt(c.labelKey, c.label), "primary"); });
        b += btn("cancel", tt("b1.cancelDoc", "Cancel document"), "danger");
      } else if (doc.status === "POSTED") {
        T.copyTo.forEach(function (c) { b += btn("copyto:" + c.to, tt(c.labelKey, c.label)); });
        b += btn("reverse", tt("b1.reverse", "Reverse"), "danger");
      }
      b += btn("close", tt("b1.close", "Close"));
      return b;
    }

    function refsHtml() {
      if (!doc.id) return "";
      var h = "";
      if (doc.baseType) h += "<li>" + esc(tt("b1.f.base", "Base") + ": " + (BASE_LABEL[doc.baseType] ? tt(BASE_LABEL[doc.baseType][0], BASE_LABEL[doc.baseType][1]) : doc.baseType) + " #" + doc.baseId) + "</li>";
      if (doc.targetType) h += "<li>" + esc(tt("b1.target", "Target") + ": " + doc.targetType + " #" + doc.targetId) + "</li>";
      if (doc.journalId) h += "<li>" + esc(tt("b1.journal", "Journal entry") + " #" + doc.journalId) + "</li>";
      if (doc.subledgerId) h += "<li>" + esc(tt("b1.subledger", "Sub-ledger") + " #" + doc.subledgerId) + "</li>";
      return h ? '<ul class="jn-timeline jn-b1-refs">' + h + "</ul>" : "";
    }

    function paint() {
      calcDoc(doc);
      var body = backdrop.querySelector(".jn-modal-body");
      backdrop.querySelector(".jn-modal-header strong").textContent = docTitle(type) + (doc.docNo ? " | " + doc.docNo : " | " + tt("common.new", "New"));
      body.innerHTML = '<div class="jn-toolbar jn-b1-actions">' + actionsHtml() + "</div>" + headerFields() +
        '<div class="jn-b1-sect">' + esc(tt("b1.lines", "Lines")) + "</div>" + linesHtml() + totalsHtml() +
        '<div class="jn-field" style="margin-top:12px"><label>' + esc(tt("b1.f.notes", "Remarks")) + '</label><textarea class="jn-input" rows="2" data-h="notes"' + (editable() ? "" : " disabled") + ">" + esc(doc.notes || "") + "</textarea></div>" + refsHtml();
      bind(body);
    }

    function syncHeader(body) {
      body.querySelectorAll("[data-h]").forEach(function (el) {
        if (el.disabled) return;
        var k = el.getAttribute("data-h");
        var v = el.value;
        if (k === "docDate" || k === "dueDate" || k === "validUntil") doc[k] = B1.dateInputToMs(v);
        else if (k === "discountBps") doc[k] = Math.round((Number(v) || 0) * 100);
        else if (k === "partnerId" || k === "paymentTermsId" || k === "priceListId" || k === "warehouseId") doc[k] = Math.round(Number(v) || 0);
        else doc[k] = v;
      });
    }

    function updateTotalsDom(body) {
      calcDoc(doc);
      doc.lines.forEach(function (l, i) {
        var n = body.querySelector('[data-net="' + i + '"]'), x = body.querySelector('[data-tax="' + i + '"]'), t = body.querySelector('[data-tot="' + i + '"]');
        if (n) n.textContent = B1.fmtMoney(l.netCents);
        if (x) x.textContent = B1.fmtMoney(l.taxCents);
        if (t) t.textContent = B1.fmtMoney(l.totalCents);
      });
      var cur = doc.currency || "";
      var set = function (k, v) { var e = body.querySelector('[data-sum="' + k + '"]'); if (e) e.textContent = B1.fmtMoney(v, cur); };
      set("sub", doc.subtotalCents); set("tax", doc.taxCents); set("tot", doc.totalCents);
    }

    function applyPricing(i, body) {
      var l = doc.lines[i];
      if (!l.itemId || !doc.partnerId) { return Promise.resolve(); }
      return api("/api/erp/b1/pricing" + qs({ partnerId: doc.partnerId, itemId: l.itemId, side: T.sales ? "sales" : "purchase" })).then(function (p) {
        l.unitPriceCents = p.unitPriceCents || 0;
        l.discountBps = p.discountBps || 0;
        l.taxCodeId = p.taxCodeId || 0;
        l.taxBps = p.taxBps || 0;
        l.uomCode = p.uom || l.uomCode;
        l.isInventory = p.isInventory;
        l.auto = true;
      }).catch(function () {});
    }

    function bind(body) {
      body.querySelectorAll("[data-act]").forEach(function (b) {
        b.onclick = function () { onAction(b.getAttribute("data-act"), body); };
      });
      body.querySelectorAll("[data-h]").forEach(function (el) {
        el.onchange = function () {
          var k = el.getAttribute("data-h");
          syncHeader(body);
          if (k === "partnerId") {
            var p = B1.findById(lk[T.partner], doc.partnerId);
            if (p) {
              if (p.paymentTermsId) doc.paymentTermsId = p.paymentTermsId;
              if (p.priceListId) doc.priceListId = p.priceListId;
              if (p.currency && !doc.id) doc.currency = p.currency;
              if (p.discountBps != null && !doc.id) doc.discountBps = p.discountBps;
            }
            var jobs = doc.lines.map(function (l, i) { return l.itemId && l.baseLineId <= 0 ? applyPricing(i, body) : Promise.resolve(); });
            Promise.all(jobs).then(refreshCredit).then(function () { loadRate().then(paint); });
          } else if (k === "currency" || k === "docDate") {
            loadRate().then(paint);
          } else {
            updateTotalsDom(body);
          }
        };
      });
      body.querySelectorAll("[data-l]").forEach(function (el) {
        var i = Number(el.getAttribute("data-l"));
        var k = el.getAttribute("data-k");
        el.onchange = function () {
          var l = doc.lines[i];
          if (k === "item") {
            var it = itemByLabel[el.value];
            if (!it) {
              /* allow typing a bare SKU */
              (lk.items || []).forEach(function (x) { if (x.sku === el.value.trim()) it = x; });
            }
            if (!it) { l.itemId = 0; return; }
            l.itemId = it.id;
            l.description = l.description || "";
            if (!doc.partnerId) { l.uomCode = it.uom || ""; l.unitPriceCents = it.unitPriceCents || 0; l.isInventory = it.isInventory; paint(); return; }
            applyPricing(i, body).then(function () { paint(); });
            return;
          }
          if (k === "quantity") l.quantity = Math.max(0, Math.round(Number(el.value) || 0));
          else if (k === "unitPriceCents") { l.unitPriceCents = B1.majorToCents(el.value); l.auto = false; }
          else if (k === "discountBps") { l.discountBps = Math.round((Number(el.value) || 0) * 100); l.auto = false; }
          else if (k === "taxCodeId") {
            l.taxCodeId = Math.round(Number(el.value) || 0);
            var tc = B1.findById(lk.taxCodes, l.taxCodeId);
            l.taxBps = tc ? tc.rateBps : 0;
            l.auto = false;
          } else if (k === "description") l.description = el.value;
          updateTotalsDom(body);
        };
      });
      body.querySelectorAll("[data-del]").forEach(function (b) {
        b.onclick = function () { syncHeader(body); doc.lines.splice(Number(b.getAttribute("data-del")), 1); paint(); };
      });
      var add = body.querySelector("[data-addline]");
      if (add) add.onclick = function () { syncHeader(body); doc.lines.push(blankLine()); paint(); };
    }

    function loadRate() {
      if (!doc.currency) return Promise.resolve();
      return api("/api/erp/b1/rate" + qs({ currency: doc.currency, date: doc.docDate })).then(function (r) {
        doc.rateMicro = r.rateMicro || 0;
      }).catch(function () { doc.rateMicro = 0; });
    }

    function payload() {
      var p = {
        partnerId: doc.partnerId, docDate: doc.docDate, dueDate: doc.dueDate, validUntil: doc.validUntil, currency: doc.currency || undefined,
        paymentTermsId: doc.paymentTermsId, priceListId: doc.priceListId, warehouseId: doc.warehouseId, shipTo: doc.shipTo, billTo: doc.billTo,
        refNo: doc.refNo, discountBps: doc.discountBps, notes: doc.notes, baseType: doc.baseType || "", baseId: doc.baseId || 0
      };
      if (!p.currency) delete p.currency;
      p.lines = doc.lines.filter(function (l) { return l.itemId > 0 || (l.description && l.description.length); }).map(function (l) {
        var o = { itemId: l.itemId, description: l.description || "", quantity: l.quantity, uomCode: l.uomCode || "", baseType: l.baseType || "", baseId: l.baseId || 0, baseLineId: l.baseLineId || 0, warehouseId: l.warehouseId || 0 };
        if (!l.auto) {
          o.unitPriceCents = l.unitPriceCents; o.discountBps = l.discountBps; o.taxCodeId = l.taxCodeId; o.taxBps = l.taxBps;
        }
        return o;
      });
      return p;
    }

    function saveDoc(body) {
      syncHeader(body);
      if (!doc.partnerId) return Promise.reject({ message: tt("b1.needPartner", "Select a business partner") });
      var p = payload();
      if (!p.lines.length) return Promise.reject({ message: tt("b1.needLines", "Add at least one line") });
      var headers = {};
      if (!doc.id) headers["Idempotency-Key"] = idemKey;
      return api("/api/erp/b1/docs/" + type + (doc.id ? "/" + doc.id : ""), "POST", p, headers).then(function (saved) {
        saved.lines = (saved.lines || []).map(function (l) { return Object.assign({ auto: false }, l); });
        doc = saved;
        return refreshCredit();
      });
    }

    function afterChange() {
      B1.invalidate();
      if (global.JnData && global.JnData.active) global.JnData.active.invalidate();
      if (onChange) onChange();
    }

    function onAction(act, body) {
      if (act === "close") { backdrop.remove(); return; }
      var busy = body.querySelectorAll("[data-act]");
      busy.forEach(function (x) { x.disabled = true; });
      var run;
      if (act === "save") {
        run = saveDoc(body).then(function () { B1.toastOk(tt("common.saved", "Saved")); });
      } else if (act === "submit") {
        run = saveDoc(body).then(function () { return api("/api/erp/b1/docs/" + type + "/" + doc.id + "/submit", "POST", {}); }).then(function (d) { applyDoc(d); B1.toastOk(tt("b1.submitted", "Submitted")); });
      } else if (act === "post") {
        run = saveDoc(body).then(function () { return api("/api/erp/b1/docs/" + type + "/" + doc.id + "/post", "POST", {}); }).then(function (d) { applyDoc(d); B1.toastOk(tt("b1.posted", "Posted")); });
      } else if (act === "cancel") {
        run = api("/api/erp/b1/docs/" + type + "/" + doc.id + "/cancel", "POST", {}).then(function (d) { applyDoc(d); B1.toastOk(tt("b1.cancelled", "Cancelled")); });
      } else if (act === "reverse") {
        if (!global.confirm(tt("b1.reverseConfirm", "Reverse this posted document? Journal and sub-ledger effects will be reversed."))) {
          busy.forEach(function (x) { x.disabled = false; });
          return;
        }
        run = api("/api/erp/b1/docs/" + type + "/" + doc.id + "/reverse", "POST", {}).then(function (d) { applyDoc(d); B1.toastOk(tt("b1.reversed", "Reversed")); });
      } else if (act.indexOf("copyto:") === 0) {
        var to = act.slice(7);
        run = api("/api/erp/b1/copy/" + to, "POST", { fromType: type, fromId: doc.id }).then(function (r) {
          afterChange();
          if (r && r.targetId && !r.id) {
            B1.toastOk(tt("b1.copied", "Created {type} {no}", { type: r.targetType, no: r.targetDocNo }));
            doc.status = "CLOSED"; doc.targetType = r.targetType; doc.targetId = r.targetId;
            paint();
            return null;
          }
          /* credit memo draft */
          backdrop.remove();
          openDocEditor(to, r.id, onChange);
          return null;
        });
      } else { run = Promise.resolve(); }
      run.then(function () { if (backdrop.isConnected) paint(); afterChange(); }).catch(function (e) { B1.toastErr(e); if (backdrop.isConnected) paint(); });
    }

    function applyDoc(d) {
      d.lines = (d.lines || []).map(function (l) { return Object.assign({ auto: false }, l); });
      doc = d;
    }
  }

  /* -------------------------------------------------------- copy-from dialog */
  function openCopyFrom(toType, onDone) {
    var T = TYPES[toType];
    var baseType = T.bases[0];
    var bases = [];
    var lines = [];
    var itemsLk = [];
    var backdrop = global.JnUI.modal({ title: tt("b1.copyFrom", "Copy from...") + " - " + docTitle(toType), bodyHtml: "", primaryLabel: null });
    backdrop.querySelector(".jn-modal").classList.add("jn-b1-wide");
    backdrop.querySelector(".jn-modal-footer").innerHTML = "";
    var body = backdrop.querySelector(".jn-modal-body");
    B1.lookup("items").then(function (it) { itemsLk = it; loadBases(); });

    function loadBases() {
      body.innerHTML = '<div class="jn-loading">' + esc(tt("common.loading", "Loading...")) + "</div>";
      var p;
      if (BASE_COLLECTION[baseType]) {
        p = global.JnData.active.list(BASE_COLLECTION[baseType], { page: 1, pageSize: 200, forceRefresh: true }).then(function (r) { return r.rows; });
      } else {
        p = api("/api/erp/b1/docs/" + baseType + qs({ status: "POSTED", limit: 200 }));
      }
      p.then(function (rows) { bases = rows; lines = []; paint(0); }).catch(function (e) { body.innerHTML = '<div class="jn-error">' + esc(B1.errMsg(e)) + "</div>"; });
    }
    function baseLabel(r) {
      var who = r.customerName || r.supplierName || r.partnerName || "";
      return (r.docNo || ("#" + r.id)) + " | " + who + " | " + (r.status || "");
    }
    function paint(selId) {
      var typeSel = '<div class="jn-field"><label>' + esc(tt("b1.f.base", "Base document")) + '</label><select class="jn-select" data-bt>' + T.bases.map(function (b) {
        return '<option value="' + b + '"' + (b === baseType ? " selected" : "") + ">" + esc(tt(BASE_LABEL[b][0], BASE_LABEL[b][1])) + "</option>";
      }).join("") + "</select></div>";
      var docSel = '<div class="jn-field"><label>' + esc(tt("b1.f.docNo", "Doc No")) + '</label><select class="jn-select" data-bd><option value="0">-</option>' + bases.map(function (r) {
        return '<option value="' + esc(r.id) + '"' + (String(r.id) === String(selId) ? " selected" : "") + ">" + esc(baseLabel(r)) + "</option>";
      }).join("") + "</select></div>";
      var lineHtml = "";
      if (selId) {
        lineHtml = '<div class="jn-table-wrap jn-lines-editor"><table class="jn-table"><thead><tr><th></th><th>' + esc(tt("b1.f.item", "Item")) + "</th><th>" + esc(tt("b1.f.quantity", "Qty")) + "</th><th>" + esc(tt("b1.f.used", "Already copied")) + "</th><th>" + esc(tt("b1.f.open", "Open")) + "</th><th>" + esc(tt("b1.f.copyQty", "Copy Qty")) + "</th></tr></thead><tbody>" +
          lines.map(function (l, i) {
            var it = B1.findById(itemsLk, l.itemId);
            return "<tr><td><input type=\"checkbox\" data-ck=\"" + i + "\" " + (l.openQuantity > 0 ? "checked" : "disabled") + " /></td><td>" + esc(it ? B1.labelOf(it, "items") : "#" + l.itemId) + "</td><td>" + l.quantity + "</td><td>" + l.usedQuantity + "</td><td>" + l.openQuantity +
              '</td><td><input class="jn-input" type="number" min="0" max="' + l.openQuantity + '" step="1" data-cq="' + i + '" value="' + l.openQuantity + '" style="width:80px"' + (l.openQuantity > 0 ? "" : " disabled") + " /></td></tr>";
          }).join("") + "</tbody></table></div>";
      }
      body.innerHTML = '<div class="jn-form-grid">' + typeSel + docSel + "</div>" + lineHtml +
        '<div class="jn-b1-footer"><span style="flex:1"></span><button type="button" class="jn-btn" data-close>' + esc(tt("b1.close", "Close")) + '</button><button type="button" class="jn-btn primary" data-go' + (selId ? "" : " disabled") + ">" + esc(tt("b1.createDraft", "Create draft")) + "</button></div>";
      body.querySelector("[data-close]").onclick = function () { backdrop.remove(); };
      body.querySelector("[data-bt]").onchange = function (e) { baseType = e.target.value; loadBases(); };
      body.querySelector("[data-bd]").onchange = function (e) {
        var id = Number(e.target.value);
        if (!id) { lines = []; paint(0); return; }
        api("/api/erp/b1/open-lines" + qs({ baseType: baseType, baseId: id })).then(function (ls) { lines = ls; paint(id); }).catch(B1.toastErr);
      };
      var go = body.querySelector("[data-go]");
      if (go) go.onclick = function () {
        var sel = [];
        lines.forEach(function (l, i) {
          var ck = body.querySelector('[data-ck="' + i + '"]');
          var q = Number((body.querySelector('[data-cq="' + i + '"]') || {}).value) || 0;
          if (ck && ck.checked && q > 0) sel.push({ baseLineId: l.baseLineId, quantity: q });
        });
        if (!sel.length) { B1.toastErr({ message: tt("b1.pickLines", "Select at least one line") }); return; }
        go.disabled = true;
        api("/api/erp/b1/copy/" + toType, "POST", { fromType: baseType, fromId: Number(body.querySelector("[data-bd]").value), lines: sel }).then(function (d) {
          backdrop.remove();
          if (onDone) onDone();
          openDocEditor(toType, d.id, onDone);
        }).catch(function (e) { go.disabled = false; B1.toastErr(e); });
      };
    }
  }

  B1.renderDocs = renderDocs;
  B1.openDocEditor = openDocEditor;
  B1.openCopyFrom = openCopyFrom;
  B1.docTypes = TYPES;
})(window);
