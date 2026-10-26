// JNative ERP One (b1 module)
/**
 * JnB1 core - SAP Business One style master data editors (multi-tab) on top of /api/erp/b1/*.
 * Source files must stay ASCII (erp-server static serving corrupts raw UTF-8); i18n via b1-i18n.js.
 */
(function (global) {
  var B1 = global.JnB1 || {};
  global.JnB1 = B1;

  function tt(key, fallback, params) {
    return global.JnI18n ? global.JnI18n.t(key, params, fallback) : (fallback != null ? fallback : key);
  }
  function esc(s) { return global.JnUI ? global.JnUI.esc(s) : String(s == null ? "" : s); }

  /* ---------------------------------------------------------------- api */
  function api(path, method, body, headers) {
    var opts = { method: method || "GET", headers: headers || {} };
    if (body != null) opts.body = JSON.stringify(body);
    return global.JnData.active._fetch(path, opts);
  }
  function qs(params) {
    var parts = [];
    Object.keys(params || {}).forEach(function (k) {
      var v = params[k];
      if (v === undefined || v === null || v === "") return;
      parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(v));
    });
    return parts.length ? "?" + parts.join("&") : "";
  }

  /* ------------------------------------------------------------- lookups */
  var lookupCache = {};
  var LOOKUP_PATH = {
    taxCodes: "/api/erp/b1/m/taxCodes?limit=1000",
    paymentTerms: "/api/erp/b1/m/paymentTerms?limit=1000",
    priceLists: "/api/erp/b1/m/priceLists?limit=1000",
    bpGroups: "/api/erp/b1/m/bpGroups?limit=1000",
    itemGroups: "/api/erp/b1/m/itemGroups?limit=1000",
    uomGroups: "/api/erp/b1/m/uomGroups?limit=1000",
    currencies: "/api/erp/b1/m/currencies?limit=1000",
    customers: "/api/erp/b1/m/partners?kind=CUSTOMER&limit=2000",
    suppliers: "/api/erp/b1/m/partners?kind=SUPPLIER&limit=2000",
    partners: "/api/erp/b1/m/partners?limit=2000",
    items: "/api/erp/b1/m/items?limit=2000",
    warehouses: "/api/erp/warehouses",
    employees: "/api/erp/employees"
  };
  function lookup(name, force) {
    if (!force && lookupCache[name]) return Promise.resolve(lookupCache[name]);
    var path = LOOKUP_PATH[name];
    if (!path) return Promise.resolve([]);
    return api(path).then(function (rows) {
      lookupCache[name] = Array.isArray(rows) ? rows : [];
      return lookupCache[name];
    });
  }
  function invalidate(name) {
    if (name) delete lookupCache[name];
    else lookupCache = {};
    if (name === "partners") { delete lookupCache.customers; delete lookupCache.suppliers; }
    if (name === "customers" || name === "suppliers") delete lookupCache.partners;
  }
  function lookupAll(names) {
    return Promise.all(names.map(function (n) { return lookup(n); })).then(function (arr) {
      var out = {};
      names.forEach(function (n, i) { out[n] = arr[i]; });
      return out;
    });
  }
  function labelOf(row, name) {
    if (!row) return "";
    if (name === "items") return (row.sku || "") + " - " + (row.name || "");
    if (name === "taxCodes") return (row.code || "") + " (" + (row.rateBps / 100) + "%)";
    if (row.code && row.name) return row.code + " - " + row.name;
    return row.name || row.code || String(row.id);
  }
  function optionsHtml(rows, name, selected, allowEmpty) {
    var h = allowEmpty === false ? "" : '<option value="0">-</option>';
    (rows || []).forEach(function (r) {
      h += '<option value="' + esc(r.id) + '"' + (String(r.id) === String(selected) ? " selected" : "") + ">" + esc(labelOf(r, name)) + "</option>";
    });
    return h;
  }
  function findById(rows, id) {
    var f = null;
    (rows || []).forEach(function (r) { if (String(r.id) === String(id)) f = r; });
    return f;
  }

  /* ------------------------------------------------------------ formatting */
  function centsToMajor(c) { return (Number(c) || 0) / 100; }
  function majorToCents(v) {
    var n = Number(v);
    if (!isFinite(n)) return 0;
    return Math.round(n * 100);
  }
  function fmtMoney(cents, cur) {
    var v = (Number(cents) || 0) / 100;
    var s = v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return cur ? (cur + " " + s) : s;
  }
  function msToDateInput(ms) {
    var n = Number(ms);
    if (!n) return "";
    var d = new Date(n);
    if (isNaN(d.getTime())) return "";
    var m = d.getMonth() + 1, day = d.getDate();
    return d.getFullYear() + "-" + (m < 10 ? "0" : "") + m + "-" + (day < 10 ? "0" : "") + day;
  }
  function dateInputToMs(s) {
    if (!s) return 0;
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
    if (!m) return 0;
    return Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12, 0, 0);
  }
  function errMsg(e) {
    var m = (e && e.message) || "error";
    var map = {
      DUPLICATE_KEY: tt("b1.err.duplicate", "Duplicate key: code already exists"),
      NO_EXCHANGE_RATE: tt("b1.err.noRate", "No exchange rate for this currency/date")
    };
    return map[m] || map[e && e.code] || m;
  }
  function toastErr(e) { if (global.JnUI) global.JnUI.toast(errMsg(e), "err"); }
  function toastOk(m) { if (global.JnUI) global.JnUI.toast(m, "ok"); }

  /* --------------------------------------------------------- field helpers */
  /* field = { key, label, type, tab, lookup, options, readOnlyEdit, span, hint, def } */
  function fieldHtml(f, rec, ctx) {
    var v = rec[f.key];
    var label = f.labelKey ? tt(f.labelKey, f.label) : f.label;
    var attrs = ' data-f="' + esc(f.key) + '" data-t="' + esc(f.type) + '"';
    var ro = (ctx && ctx.readOnly) || (f.readOnlyEdit && rec.id) || f.readOnly;
    var dis = ro ? " disabled" : "";
    var h = "";
    if (f.type === "textarea") {
      h = '<textarea class="jn-input" rows="3"' + attrs + dis + ">" + esc(v == null ? "" : v) + "</textarea>";
    } else if (f.type === "select") {
      h = '<select class="jn-select"' + attrs + dis + ">" + (f.options || []).map(function (o) {
        var val = typeof o === "string" ? o : o.v;
        var lab = typeof o === "string" ? o : (o.lk ? tt(o.lk, o.l) : o.l);
        return '<option value="' + esc(val) + '"' + (String(v == null ? (f.def != null ? f.def : "") : v) === String(val) ? " selected" : "") + ">" + esc(lab) + "</option>";
      }).join("") + "</select>";
    } else if (f.type === "fk") {
      var rows = (ctx && ctx.lookups && ctx.lookups[f.lookup]) || [];
      h = '<select class="jn-select"' + attrs + dis + ">" + optionsHtml(rows, f.lookup, v == null ? 0 : v, f.required ? false : true) + "</select>";
    } else if (f.type === "bool") {
      var on = Number(v == null ? (f.def != null ? f.def : 0) : v) ? 1 : 0;
      h = '<select class="jn-select"' + attrs + dis + '><option value="1"' + (on ? " selected" : "") + ">" + esc(tt("b1.yes", "Yes")) + '</option><option value="0"' + (on ? "" : " selected") + ">" + esc(tt("b1.no", "No")) + "</option></select>";
    } else if (f.type === "money") {
      h = '<input class="jn-input" type="number" step="0.01" min="0"' + attrs + ' value="' + esc(v == null ? (f.def != null ? f.def : 0) : centsToMajor(v)) + '"' + dis + " />";
    } else if (f.type === "bps") {
      h = '<input class="jn-input" type="number" step="0.01" min="0" max="100"' + attrs + ' value="' + esc(v == null ? (f.def != null ? f.def : 0) : (Number(v) / 100)) + '"' + dis + " />";
    } else if (f.type === "rate") {
      h = '<input class="jn-input" type="number" step="0.000001" min="0"' + attrs + ' value="' + esc(v == null ? "" : (Number(v) / 1000000)) + '"' + dis + " />";
    } else if (f.type === "int") {
      h = '<input class="jn-input" type="number" step="1"' + attrs + ' value="' + esc(v == null ? (f.def != null ? f.def : 0) : v) + '"' + dis + " />";
    } else if (f.type === "date") {
      h = '<input class="jn-input" type="date"' + attrs + ' value="' + esc(msToDateInput(v)) + '"' + dis + " />";
    } else {
      h = '<input class="jn-input" type="text"' + attrs + ' value="' + esc(v == null ? (f.def != null ? f.def : "") : v) + '"' + dis + " />";
    }
    if (ctx && ctx.bare) return h;
    return '<div class="jn-field' + (f.span ? " span-2" : "") + '"><label>' + esc(label) + (f.required ? " *" : "") + "</label>" + h +
      (f.hint ? '<div class="hint">' + esc(tt(f.hint, "")) + "</div>" : "") + "</div>";
  }
  function fieldsGrid(fields, rec, ctx) {
    return '<div class="jn-form-grid">' + fields.map(function (f) { return fieldHtml(f, rec, ctx); }).join("") + "</div>";
  }
  function readFields(root, fields, rec) {
    var out = {};
    fields.forEach(function (f) {
      var el = root.querySelector('[data-f="' + f.key + '"]');
      if (!el || el.disabled) return;
      var v = el.value;
      if (f.type === "int" || f.type === "fk" || f.type === "bool") out[f.key] = Math.round(Number(v) || 0);
      else if (f.type === "money") out[f.key] = majorToCents(v);
      else if (f.type === "bps") out[f.key] = Math.round((Number(v) || 0) * 100);
      else if (f.type === "rate") out[f.key] = Math.round((Number(v) || 0) * 1000000);
      else if (f.type === "date") out[f.key] = dateInputToMs(v);
      else out[f.key] = String(v == null ? "" : v).trim();
    });
    if (rec) {
      Object.keys(out).forEach(function (k) { rec[k] = out[k]; });
    }
    return out;
  }

  /* ------------------------------------------------------ entity definitions */
  var PARTNER_KINDS = [{ v: "CUSTOMER", l: "Customer", lk: "b1.kind.customer" }, { v: "SUPPLIER", l: "Supplier", lk: "b1.kind.supplier" }, { v: "BOTH", l: "Customer & Supplier", lk: "b1.kind.both" }];
  var PARTNER_STATUS = [{ v: "ACTIVE", l: "Active", lk: "b1.st.active" }, { v: "FROZEN", l: "Frozen", lk: "b1.st.frozen" }, { v: "INACTIVE", l: "Inactive", lk: "b1.st.inactive" }];
  var ITEM_TYPES = [{ v: "ITEM", l: "Item", lk: "b1.itype.item" }, { v: "SERVICE", l: "Service", lk: "b1.itype.service" }, { v: "LABOR", l: "Labor", lk: "b1.itype.labor" }, { v: "TRAVEL", l: "Travel", lk: "b1.itype.travel" }];
  var ITEM_STATUS = [{ v: "ACTIVE", l: "Active", lk: "b1.st.active" }, { v: "INACTIVE", l: "Inactive", lk: "b1.st.inactive" }];
  var MANAGE = [{ v: "N", l: "None", lk: "b1.manage.n" }, { v: "B", l: "Batch", lk: "b1.manage.b" }, { v: "S", l: "Serial", lk: "b1.manage.s" }];
  var VALUATION = [{ v: "MOVING_AVG", l: "Moving average", lk: "b1.val.ma" }, { v: "STANDARD", l: "Standard", lk: "b1.val.std" }, { v: "FIFO", l: "FIFO", lk: "b1.val.fifo" }];
  var TAX_DIR = [{ v: "OUT", l: "Output (sales)", lk: "b1.dir.out" }, { v: "IN", l: "Input (purchase)", lk: "b1.dir.in" }, { v: "BOTH", l: "Both", lk: "b1.dir.both" }];
  var BPG_KIND = [{ v: "CUSTOMER", l: "Customer", lk: "b1.kind.customer" }, { v: "SUPPLIER", l: "Supplier", lk: "b1.kind.supplier" }, { v: "LEAD", l: "Lead", lk: "b1.kind.lead" }];
  var ADDR_TYPE = [{ v: "BILL_TO", l: "Bill-to", lk: "b1.addr.bill" }, { v: "SHIP_TO", l: "Ship-to", lk: "b1.addr.ship" }];

  function F(key, label, type, extra) {
    var o = { key: key, label: label, labelKey: "b1.f." + key, type: type || "text" };
    if (extra) Object.keys(extra).forEach(function (k) { o[k] = extra[k]; });
    return o;
  }

  var ENTITIES = {
    partners: {
      api: "partners",
      titleKey: "b1.ent.partners", title: "Business Partner",
      lookups: ["bpGroups", "paymentTerms", "priceLists", "taxCodes", "currencies"],
      listCols: [
        { key: "code", title: "Code" }, { key: "name", title: "Name" },
        { key: "kind", title: "Type", fmt: "kind" },
        { key: "groupId", title: "Group", fk: "bpGroups" },
        { key: "paymentTermsId", title: "Payment Terms", fk: "paymentTerms" },
        { key: "currency", title: "Currency" },
        { key: "creditLimitCents", title: "Credit Limit", fmt: "money" },
        { key: "status", title: "Status", fmt: "status" }
      ],
      filters: ["status"],
      tabs: ["general", "terms", "contacts", "addresses", "credit", "remarks"],
      fields: [
        F("code", "Code", "text", { tab: "general", required: true, readOnlyEdit: true }),
        F("name", "Name", "text", { tab: "general", required: true }),
        F("kind", "Type", "select", { tab: "general", options: PARTNER_KINDS, def: "CUSTOMER" }),
        F("groupId", "Group", "fk", { tab: "general", lookup: "bpGroups" }),
        F("status", "Status", "select", { tab: "general", options: PARTNER_STATUS, def: "ACTIVE" }),
        F("taxId", "Tax ID", "text", { tab: "general" }),
        F("phone", "Phone", "text", { tab: "general" }),
        F("email", "Email", "text", { tab: "general" }),
        F("website", "Website", "text", { tab: "general" }),
        F("industry", "Industry", "text", { tab: "general" }),
        F("paymentTermsId", "Payment Terms", "fk", { tab: "terms", lookup: "paymentTerms" }),
        F("priceListId", "Price List", "fk", { tab: "terms", lookup: "priceLists" }),
        F("currency", "Currency", "text", { tab: "terms", def: "CNY", hint: "b1.hint.currency" }),
        F("discountBps", "Default Discount %", "bps", { tab: "terms" }),
        F("defaultTaxCodeId", "Default Tax Code", "fk", { tab: "terms", lookup: "taxCodes" }),
        F("creditLimitCents", "Credit Limit", "money", { tab: "terms", hint: "b1.hint.credit0" }),
        F("commitmentLimitCents", "Commitment Limit", "money", { tab: "terms" }),
        F("bankName", "Bank", "text", { tab: "terms" }),
        F("bankAccount", "Bank Account", "text", { tab: "terms" }),
        F("remarks", "Remarks", "textarea", { tab: "remarks", span: 1 })
      ]
    },
    items: {
      api: "items",
      titleKey: "b1.ent.items", title: "Item",
      lookups: ["itemGroups", "uomGroups", "taxCodes", "warehouses", "suppliers"],
      listCols: [
        { key: "sku", title: "Item No." }, { key: "name", title: "Description" },
        { key: "itemType", title: "Type", fmt: "itype" },
        { key: "itemGroupId", title: "Group", fk: "itemGroups" },
        { key: "uom", title: "UoM" },
        { key: "unitPriceCents", title: "Base Price", fmt: "money" },
        { key: "status", title: "Status", fmt: "status" }
      ],
      filters: ["itemType", "status"],
      tabs: ["general", "sales", "purchasing", "inventory", "prices", "remarks"],
      fields: [
        F("sku", "Item No.", "text", { tab: "general", required: true, readOnlyEdit: true }),
        F("name", "Description", "text", { tab: "general", required: true }),
        F("itemType", "Item Type", "select", { tab: "general", options: ITEM_TYPES, def: "ITEM" }),
        F("itemGroupId", "Item Group", "fk", { tab: "general", lookup: "itemGroups" }),
        F("barcode", "Barcode", "text", { tab: "general" }),
        F("categoryCode", "Category", "text", { tab: "general" }),
        F("uom", "Inventory UoM", "text", { tab: "general", def: "EA" }),
        F("uomGroupId", "UoM Group", "fk", { tab: "general", lookup: "uomGroups" }),
        F("status", "Status", "select", { tab: "general", options: ITEM_STATUS, def: "ACTIVE" }),
        F("isSales", "Sales Item", "bool", { tab: "sales", def: 1 }),
        F("unitPriceCents", "Base Price", "money", { tab: "sales" }),
        F("salesUom", "Sales UoM", "text", { tab: "sales", def: "EA" }),
        F("salesTaxCodeId", "Sales Tax Code", "fk", { tab: "sales", lookup: "taxCodes" }),
        F("isPurchase", "Purchase Item", "bool", { tab: "purchasing", def: 1 }),
        F("purchaseUom", "Purchase UoM", "text", { tab: "purchasing", def: "EA" }),
        F("purchaseTaxCodeId", "Purchase Tax Code", "fk", { tab: "purchasing", lookup: "taxCodes" }),
        F("preferredVendorId", "Preferred Vendor", "fk", { tab: "purchasing", lookup: "suppliers" }),
        F("leadTimeDays", "Lead Time (days)", "int", { tab: "purchasing" }),
        F("isInventory", "Inventory Item", "bool", { tab: "inventory", def: 1 }),
        F("manageMethod", "Manage By", "select", { tab: "inventory", options: MANAGE, def: "N" }),
        F("valuationMethod", "Valuation Method", "select", { tab: "inventory", options: VALUATION, def: "MOVING_AVG" }),
        F("defaultWarehouseId", "Default Warehouse", "fk", { tab: "inventory", lookup: "warehouses" }),
        F("minQty", "Min Stock", "int", { tab: "inventory" }),
        F("maxQty", "Max Stock", "int", { tab: "inventory" }),
        F("reorderQty", "Reorder Qty", "int", { tab: "inventory" }),
        F("weightG", "Weight (g)", "int", { tab: "inventory" }),
        F("remarks", "Remarks", "textarea", { tab: "remarks", span: 1 })
      ]
    },
    bpGroups: simpleEntity("bpGroups", "b1.ent.bpGroups", "BP Groups", [
      F("code", "Code", "text", { required: true, readOnlyEdit: true }),
      F("name", "Name", "text", { required: true }),
      F("kind", "Type", "select", { options: BPG_KIND, def: "CUSTOMER" }),
      F("active", "Active", "bool", { def: 1 })
    ], [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "kind", title: "Type", fmt: "kind" }, { key: "active", title: "Active", fmt: "bool" }]),
    paymentTerms: simpleEntity("paymentTerms", "b1.ent.paymentTerms", "Payment Terms", [
      F("code", "Code", "text", { required: true, readOnlyEdit: true }),
      F("name", "Name", "text", { required: true }),
      F("netDays", "Net Days", "int", { def: 30 }),
      F("discountDays", "Discount Days", "int"),
      F("discountBps", "Cash Discount %", "bps"),
      F("active", "Active", "bool", { def: 1 })
    ], [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "netDays", title: "Net Days" }, { key: "discountDays", title: "Discount Days" }, { key: "discountBps", title: "Cash Discount %", fmt: "bps" }, { key: "active", title: "Active", fmt: "bool" }]),
    taxCodes: simpleEntity("taxCodes", "b1.ent.taxCodes", "Tax Codes", [
      F("code", "Code", "text", { required: true, readOnlyEdit: true }),
      F("name", "Name", "text", { required: true }),
      F("rateBps", "Rate %", "bps"),
      F("direction", "Direction", "select", { options: TAX_DIR, def: "BOTH" }),
      F("active", "Active", "bool", { def: 1 })
    ], [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "rateBps", title: "Rate %", fmt: "bps" }, { key: "direction", title: "Direction", fmt: "dir" }, { key: "active", title: "Active", fmt: "bool" }]),
    currencies: simpleEntity("currencies", "b1.ent.currencies", "Currencies", [
      F("code", "Code", "text", { required: true, readOnlyEdit: true }),
      F("name", "Name", "text", { required: true }),
      F("symbol", "Symbol", "text"),
      F("decimals", "Decimals", "int", { def: 2 }),
      F("active", "Active", "bool", { def: 1 })
    ], [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "symbol", title: "Symbol" }, { key: "decimals", title: "Decimals" }, { key: "isLocal", title: "Local", fmt: "bool" }, { key: "active", title: "Active", fmt: "bool" }]),
    exchangeRates: simpleEntity("exchangeRates", "b1.ent.exchangeRates", "Exchange Rates", [
      F("currencyCode", "Currency", "text", { required: true, readOnlyEdit: true }),
      F("rateDate", "Rate Date", "date", { required: true }),
      F("rateMicro", "Rate (local per 1 foreign)", "rate", { required: true })
    ], [{ key: "currencyCode", title: "Currency" }, { key: "rateDate", title: "Rate Date", fmt: "date" }, { key: "rateMicro", title: "Rate", fmt: "rate" }]),
    priceLists: {
      api: "priceLists", titleKey: "b1.ent.priceLists", title: "Price Lists",
      lookups: ["priceLists", "items"],
      listCols: [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "currencyCode", title: "Currency" }, { key: "baseListId", title: "Base List", fk: "priceLists" }, { key: "factorBps", title: "Factor %", fmt: "bps" }, { key: "isDefault", title: "Default", fmt: "bool" }, { key: "active", title: "Active", fmt: "bool" }],
      filters: [],
      tabs: ["general", "prices"],
      fields: [
        F("code", "Code", "text", { tab: "general", required: true, readOnlyEdit: true }),
        F("name", "Name", "text", { tab: "general", required: true }),
        F("currencyCode", "Currency", "text", { tab: "general", def: "CNY" }),
        F("baseListId", "Base List", "fk", { tab: "general", lookup: "priceLists" }),
        F("factorBps", "Factor %", "bps", { tab: "general", def: 100, hint: "b1.hint.factor" }),
        F("isDefault", "Default List", "bool", { tab: "general" }),
        F("active", "Active", "bool", { tab: "general", def: 1 })
      ]
    },
    itemGroups: simpleEntity("itemGroups", "b1.ent.itemGroups", "Item Groups", [
      F("code", "Code", "text", { required: true, readOnlyEdit: true }),
      F("name", "Name", "text", { required: true }),
      F("valuationMethod", "Valuation Method", "select", { options: VALUATION, def: "MOVING_AVG" }),
      F("active", "Active", "bool", { def: 1 })
    ], [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "valuationMethod", title: "Valuation", fmt: "val" }, { key: "active", title: "Active", fmt: "bool" }]),
    uomGroups: {
      api: "uomGroups", titleKey: "b1.ent.uomGroups", title: "UoM Groups",
      lookups: [],
      listCols: [{ key: "code", title: "Code" }, { key: "name", title: "Name" }, { key: "baseUom", title: "Base UoM" }, { key: "active", title: "Active", fmt: "bool" }],
      filters: [],
      tabs: ["general", "conversions"],
      fields: [
        F("code", "Code", "text", { tab: "general", required: true, readOnlyEdit: true }),
        F("name", "Name", "text", { tab: "general", required: true }),
        F("baseUom", "Base UoM", "text", { tab: "general", def: "EA" }),
        F("active", "Active", "bool", { tab: "general", def: 1 })
      ]
    }
  };

  function simpleEntity(api, titleKey, title, fields, listCols) {
    fields.forEach(function (f) { f.tab = "general"; });
    return { api: api, titleKey: titleKey, title: title, lookups: [], listCols: listCols, filters: [], tabs: ["general"], fields: fields };
  }

  var CHILD = {
    bpContacts: {
      api: "bpContacts", parentKey: "partnerId",
      cols: [F("name", "Name", "text", { required: true }), F("title", "Title", "text"), F("phone", "Phone", "text"), F("mobile", "Mobile", "text"), F("email", "Email", "text"), F("isDefault", "Default", "bool")]
    },
    bpAddresses: {
      api: "bpAddresses", parentKey: "partnerId",
      cols: [F("addrType", "Address Type", "select", { options: ADDR_TYPE, def: "BILL_TO" }), F("addrName", "Address Name", "text"), F("street", "Street", "text"), F("city", "City", "text"), F("state", "State/Province", "text"), F("zip", "Postal Code", "text"), F("country", "Country", "text"), F("isDefault", "Default", "bool")]
    },
    uomGroupItems: {
      api: "uomGroupItems", parentKey: "uomGroupId",
      cols: [F("uomCode", "UoM", "text", { required: true }), F("baseQty", "Base Qty per UoM", "int", { def: 1 })]
    }
  };

  /* ------------------------------------------------------------- formatting */
  function fmtCell(col, row, lk) {
    var v = row[col.key];
    if (col.fk) {
      var rec = findById((lk || {})[col.fk], v);
      return rec ? esc(labelOf(rec, col.fk)) : (Number(v) ? "#" + esc(v) : "");
    }
    switch (col.fmt) {
      case "money": return '<span class="jn-money">' + esc(fmtMoney(v)) + "</span>";
      case "bps": return esc((Number(v) || 0) / 100) + "%";
      case "rate": return esc((Number(v) || 0) / 1000000);
      case "date": return esc(msToDateInput(v));
      case "bool": return esc(Number(v) ? tt("b1.yes", "Yes") : tt("b1.no", "No"));
      case "status": return global.JnUI.statusHtml(v);
      case "kind": return esc(optionLabel(PARTNER_KINDS.concat(BPG_KIND), v));
      case "itype": return esc(optionLabel(ITEM_TYPES, v));
      case "dir": return esc(optionLabel(TAX_DIR, v));
      case "val": return esc(optionLabel(VALUATION, v));
      default: return esc(v == null ? "" : v);
    }
  }
  function optionLabel(opts, v) {
    var out = v == null ? "" : String(v);
    opts.forEach(function (o) { if (o.v === v) out = o.lk ? tt(o.lk, o.l) : o.l; });
    return out;
  }
  function colTitle(col) { return tt("b1.f." + col.key, col.title); }

  /* ----------------------------------------------------------- master list */
  var PAGE_SIZE = 20;

  function renderMaster(host, page) {
    var cfg = page.b1 || {};
    var ent = ENTITIES[cfg.entity];
    if (!ent) { host.innerHTML = '<div class="jn-error">Unknown entity</div>'; return; }
    var st = { q: "", status: "", itemType: "", page: 1 };
    function load() {
      host.innerHTML = '<div class="jn-loading" role="status">' + esc(tt("common.loading", "Loading...")) + "</div>";
      var params = { limit: PAGE_SIZE + 1, offset: (st.page - 1) * PAGE_SIZE, q: st.q };
      if (cfg.kind) params.kind = cfg.kind;
      if (st.status) params.status = st.status;
      if (st.itemType) params.itemType = st.itemType;
      Promise.all([api("/api/erp/b1/m/" + ent.api + qs(params)), lookupAll(ent.lookups.filter(function (n) { return n !== "suppliers"; }))]).then(function (res) {
        var rows = res[0], lk = res[1];
        var hasNext = rows.length > PAGE_SIZE;
        if (hasNext) rows = rows.slice(0, PAGE_SIZE);
        paint(rows, lk, hasNext);
      }).catch(function (e) {
        host.innerHTML = '<div class="jn-error" role="alert">' + esc(errMsg(e)) + "</div>";
      });
    }
    function paint(rows, lk, hasNext) {
      var title = page.title || tt(ent.titleKey, ent.title);
      var head = ent.listCols.map(function (c) { return "<th>" + esc(colTitle(c)) + "</th>"; }).join("") + "<th></th>";
      var body = rows.map(function (r) {
        return '<tr data-id="' + esc(r.id) + '">' + ent.listCols.map(function (c) { return "<td>" + fmtCell(c, r, lk) + "</td>"; }).join("") +
          '<td><button type="button" class="jn-btn" data-open="' + esc(r.id) + '">' + esc(tt("common.open", "Open")) + "</button></td></tr>";
      }).join("") || '<tr><td colspan="' + (ent.listCols.length + 1) + '"><div class="jn-empty">' + esc(tt("common.empty", "No records")) + "</div></td></tr>";
      var filt = '<div class="jn-filter-bar"><div class="jn-field"><label>' + esc(tt("common.search", "Search")) + '</label><input class="jn-input" data-role="q" value="' + esc(st.q) + '" /></div>';
      if (ent.filters.indexOf("status") >= 0) {
        var stOpts = cfg.entity === "items" ? ITEM_STATUS : PARTNER_STATUS;
        filt += '<div class="jn-field"><label>' + esc(tt("common.status", "Status")) + '</label><select class="jn-select" data-role="status"><option value="">' + esc(tt("common.all", "All")) + "</option>" +
          stOpts.map(function (o) { return '<option value="' + o.v + '"' + (st.status === o.v ? " selected" : "") + ">" + esc(tt(o.lk, o.l)) + "</option>"; }).join("") + "</select></div>";
      }
      if (ent.filters.indexOf("itemType") >= 0) {
        filt += '<div class="jn-field"><label>' + esc(tt("b1.f.itemType", "Item Type")) + '</label><select class="jn-select" data-role="itemType"><option value="">' + esc(tt("common.all", "All")) + "</option>" +
          ITEM_TYPES.map(function (o) { return '<option value="' + o.v + '"' + (st.itemType === o.v ? " selected" : "") + ">" + esc(tt(o.lk, o.l)) + "</option>"; }).join("") + "</select></div>";
      }
      filt += '<button type="button" class="jn-btn primary" data-act="apply">' + esc(tt("common.apply", "Apply")) + "</button></div>";
      host.innerHTML = '<div class="jn-card"><div class="jn-toolbar"><h2 class="jn-card-title" style="margin:0;flex:1">' + esc(title) + "</h2>" +
        '<button type="button" class="jn-btn primary" data-act="new">' + esc(tt("common.new", "New")) + "</button>" +
        '<button type="button" class="jn-btn" data-act="refresh">' + esc(tt("common.refresh", "Refresh")) + "</button></div>" + filt +
        '<div class="jn-table-wrap"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>" +
        '<div class="jn-pagination"><span>' + esc(tt("b1.page", "Page {n}", { n: st.page })) + '</span><span style="flex:1"></span>' +
        '<button type="button" class="jn-btn" data-act="prev"' + (st.page <= 1 ? " disabled" : "") + ">" + esc(tt("common.prev", "Prev")) + "</button>" +
        '<button type="button" class="jn-btn" data-act="next"' + (hasNext ? "" : " disabled") + ">" + esc(tt("common.next", "Next")) + "</button></div></div>";
      function readFilters() {
        var q = host.querySelector('[data-role="q"]'); st.q = q ? q.value : "";
        var s = host.querySelector('[data-role="status"]'); if (s) st.status = s.value;
        var t = host.querySelector('[data-role="itemType"]'); if (t) st.itemType = t.value;
      }
      host.querySelectorAll("[data-act]").forEach(function (b) {
        b.onclick = function () {
          var a = b.getAttribute("data-act");
          if (a === "apply" || a === "refresh") { readFilters(); st.page = 1; invalidate(); load(); }
          else if (a === "prev") { readFilters(); st.page = Math.max(1, st.page - 1); load(); }
          else if (a === "next") { readFilters(); st.page = st.page + 1; load(); }
          else if (a === "new") openMasterEditor(cfg.entity, null, { kind: cfg.kind }, load);
        };
      });
      var qi = host.querySelector('[data-role="q"]');
      if (qi) qi.onkeydown = function (e) { if (e.key === "Enter") { readFilters(); st.page = 1; load(); } };
      host.querySelectorAll("[data-open]").forEach(function (b) {
        b.onclick = function () { openMasterEditor(cfg.entity, b.getAttribute("data-open"), { kind: cfg.kind }, load); };
      });
    }
    load();
  }

  /* --------------------------------------------------------- master editor */
  function tabLabel(t) { return tt("b1.tab." + t, t); }

  function openMasterEditor(entityName, id, defaults, onSaved) {
    var ent = ENTITIES[entityName];
    var rec = Object.assign({}, defaults || {});
    var lookups = {};
    var current = "general";
    var backdrop = null;
    var creditInfo = null;
    var childCache = {};
    var priceRows = null;

    function boot(r) {
      rec = r;
      return lookupAll(ent.lookups).then(function (lk) { lookups = lk; });
    }
    var start = id ? api("/api/erp/b1/m/" + ent.api + "/" + encodeURIComponent(id)).then(boot) : boot(Object.assign({}, defaults || {}));
    start.then(function () {
      if (!id && entityName === "partners" && !rec.kind) rec.kind = (defaults && defaults.kind) || "CUSTOMER";
      backdrop = global.JnUI.modal({ title: titleOf(), bodyHtml: "", primaryLabel: null });
      var modal = backdrop.querySelector(".jn-modal");
      modal.classList.add("jn-b1-wide");
      modal.querySelector(".jn-modal-footer").innerHTML = "";
      paintShell();
    }).catch(toastErr);

    function titleOf() {
      var base = tt(ent.titleKey, ent.title);
      if (rec.id) return base + " | " + (rec.code || rec.sku || rec.currencyCode || "") + " " + (rec.name || "");
      return tt("common.new", "New") + " " + base;
    }

    function fieldsOf(tab) { return ent.fields.filter(function (f) { return f.tab === tab; }); }

    function paintShell() {
      var body = backdrop.querySelector(".jn-modal-body");
      backdrop.querySelector(".jn-modal-header strong").textContent = titleOf();
      var tabs = ent.tabs.map(function (t) {
        return '<button type="button" data-tab="' + t + '" class="' + (t === current ? "active" : "") + '">' + esc(tabLabel(t)) + "</button>";
      }).join("");
      body.innerHTML = '<div class="jn-detail-tabs jn-b1-tabs">' + tabs + '</div><div data-pane></div>' +
        '<div class="jn-b1-footer"><span data-msg class="jn-muted"></span><span style="flex:1"></span>' +
        '<button type="button" class="jn-btn" data-close>' + esc(tt("common.cancel", "Close")) + "</button>" +
        '<button type="button" class="jn-btn primary" data-save>' + esc(tt("common.save", "Save")) + "</button></div>";
      body.querySelectorAll("[data-tab]").forEach(function (b) {
        b.onclick = function () { syncPane(); current = b.getAttribute("data-tab"); paintShell(); };
      });
      body.querySelector("[data-close]").onclick = function () { backdrop.remove(); };
      body.querySelector("[data-save]").onclick = save;
      paintPane();
    }

    function syncPane() {
      var pane = backdrop.querySelector("[data-pane]");
      if (!pane) return;
      var fields = fieldsOf(current);
      if (fields.length) readFields(pane, fields, rec);
    }

    function paintPane() {
      var pane = backdrop.querySelector("[data-pane]");
      var fields = fieldsOf(current);
      var html = "";
      if (fields.length) html += fieldsGrid(fields, rec, { lookups: lookups });
      pane.innerHTML = html;
      if (current === "contacts") childGrid(pane, "bpContacts", { partnerId: rec.id });
      else if (current === "addresses") childGrid(pane, "bpAddresses", { partnerId: rec.id });
      else if (current === "conversions") childGrid(pane, "uomGroupItems", { uomGroupId: rec.id });
      else if (current === "credit") creditPane(pane);
      else if (current === "prices") pricesPane(pane);
    }

    function needSave(pane) {
      pane.innerHTML += '<div class="jn-empty">' + esc(tt("b1.saveFirst", "Save the record first to edit this tab.")) + "</div>";
    }

    /* generic inline-editable child grid */
    function childGrid(pane, childName, parentFilter) {
      var ch = CHILD[childName];
      if (!rec.id) { needSave(pane); return; }
      var box = document.createElement("div");
      pane.appendChild(box);
      function loadRows() {
        box.innerHTML = '<div class="jn-loading">' + esc(tt("common.loading", "Loading...")) + "</div>";
        api("/api/erp/b1/m/" + ch.api + qs(Object.assign({ limit: 500 }, parentFilter))).then(function (rows) {
          childCache[childName] = rows;
          paintRows(rows);
        }).catch(function (e) { box.innerHTML = '<div class="jn-error">' + esc(errMsg(e)) + "</div>"; });
      }
      function rowInputs(r, idx) {
        return ch.cols.map(function (f) {
          var ctrl = fieldHtml(f, r || {}, { bare: true });
          return "<td>" + ctrl.replace(/data-f="/, 'data-r="' + idx + '" data-f="') + "</td>";
        }).join("");
      }
      function paintRows(rows) {
        var head = ch.cols.map(function (f) { return "<th>" + esc(tt(f.labelKey, f.label)) + "</th>"; }).join("") + "<th></th>";
        var body = rows.map(function (r, i) {
          return "<tr>" + rowInputs(r, i) + '<td style="white-space:nowrap"><button type="button" class="jn-btn" data-rs="' + i + '">' + esc(tt("common.save", "Save")) +
            '</button> <button type="button" class="jn-btn danger" data-rd="' + i + '">' + esc(tt("b1.delete", "Delete")) + "</button></td></tr>";
        }).join("");
        body += "<tr class='jn-b1-newrow'>" + rowInputs({}, "new") + '<td><button type="button" class="jn-btn primary" data-rs="new">' + esc(tt("b1.add", "Add")) + "</button></td></tr>";
        box.innerHTML = '<div class="jn-table-wrap jn-lines-editor"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>";
        box.querySelectorAll("[data-rs]").forEach(function (b) {
          b.onclick = function () {
            var key = b.getAttribute("data-rs");
            var tr = b.closest("tr");
            var payload = readFields(tr, ch.cols, null);
            Object.keys(parentFilter).forEach(function (k) { payload[k] = parentFilter[k]; });
            var path = "/api/erp/b1/m/" + ch.api + (key === "new" ? "" : "/" + rows[Number(key)].id);
            api(path, "POST", payload).then(function () { toastOk(tt("common.saved", "Saved")); invalidate("partners"); loadRows(); }).catch(toastErr);
          };
        });
        box.querySelectorAll("[data-rd]").forEach(function (b) {
          b.onclick = function () {
            var r = rows[Number(b.getAttribute("data-rd"))];
            api("/api/erp/b1/m/" + ch.api + "/" + r.id + "/delete", "POST", {}).then(function () { loadRows(); }).catch(toastErr);
          };
        });
      }
      loadRows();
    }

    function creditPane(pane) {
      if (!rec.id) { needSave(pane); return; }
      pane.innerHTML = '<div class="jn-loading">' + esc(tt("common.loading", "Loading...")) + "</div>";
      api("/api/erp/b1/credit/" + rec.id).then(function (c) {
        creditInfo = c;
        function row(k, v, money) { return '<div class="jn-kpi"><div class="k">' + esc(k) + '</div><div class="v">' + esc(money ? fmtMoney(v) : v) + "</div></div>"; }
        pane.innerHTML = '<div class="jn-kpi-grid">' +
          row(tt("b1.credit.limit", "Credit limit"), c.creditLimitCents, true) +
          row(tt("b1.credit.balance", "Account balance (AR/AP)"), c.balanceCents, true) +
          row(tt("b1.credit.orders", "Open orders"), c.openOrdersCents, true) +
          row(tt("b1.credit.exposure", "Total exposure"), c.exposureCents, true) +
          row(tt("b1.credit.blocked", "Blocked"), c.blocked ? tt("b1.yes", "Yes") : tt("b1.no", "No")) +
          "</div>" + (c.reason ? '<div class="jn-demo-save-note">' + esc(c.reason) + "</div>" : "") +
          '<p class="jn-muted">' + esc(tt("b1.credit.note", "A credit limit of 0 means unlimited. Orders and documents are blocked when exposure exceeds the limit.")) + "</p>";
      }).catch(function (e) { pane.innerHTML = '<div class="jn-error">' + esc(errMsg(e)) + "</div>"; });
    }

    /* item: price per price list / price list: item prices */
    function pricesPane(pane) {
      if (!rec.id) { needSave(pane); return; }
      var isItem = entityName === "items";
      var box = document.createElement("div");
      pane.innerHTML = "";
      pane.appendChild(box);
      var filter = isItem ? { itemId: rec.id } : { priceListId: rec.id };
      function loadRows() {
        Promise.all([api("/api/erp/b1/m/priceListItems" + qs(Object.assign({ limit: 1000 }, filter))), lookup(isItem ? "priceLists" : "items", true)]).then(function (res) {
          paint(res[0], res[1]);
        }).catch(function (e) { box.innerHTML = '<div class="jn-error">' + esc(errMsg(e)) + "</div>"; });
      }
      function paint(rows, others) {
        var otherKey = isItem ? "priceListId" : "itemId";
        var head = "<th>" + esc(isItem ? tt("b1.f.priceListId", "Price List") : tt("b1.f.itemId", "Item")) + "</th><th>" + esc(tt("b1.f.priceCents", "Price")) + "</th><th></th>";
        var body = rows.map(function (r, i) {
          var o = findById(others, r[otherKey]);
          return "<tr><td>" + esc(o ? labelOf(o, isItem ? "priceLists" : "items") : "#" + r[otherKey]) + '</td><td><input class="jn-input" type="number" step="0.01" min="0" data-pr="' + i + '" value="' + esc(centsToMajor(r.priceCents)) + '" /></td>' +
            '<td style="white-space:nowrap"><button type="button" class="jn-btn" data-ps="' + i + '">' + esc(tt("common.save", "Save")) + '</button> <button type="button" class="jn-btn danger" data-pd="' + i + '">' + esc(tt("b1.delete", "Delete")) + "</button></td></tr>";
        }).join("");
        body += '<tr><td><select class="jn-select" data-pnew-key>' + optionsHtml(others, isItem ? "priceLists" : "items", 0, true) + '</select></td><td><input class="jn-input" type="number" step="0.01" min="0" data-pnew-price value="0" /></td>' +
          '<td><button type="button" class="jn-btn primary" data-padd>' + esc(tt("b1.add", "Add")) + "</button></td></tr>";
        box.innerHTML = '<div class="jn-table-wrap jn-lines-editor"><table class="jn-table"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>";
        function put(otherId, cents) {
          var payload = { priceListId: isItem ? Number(otherId) : rec.id, itemId: isItem ? rec.id : Number(otherId), priceCents: cents };
          return api("/api/erp/b1/m/priceListItems", "POST", payload);
        }
        box.querySelectorAll("[data-ps]").forEach(function (b) {
          b.onclick = function () {
            var i = Number(b.getAttribute("data-ps"));
            var v = box.querySelector('[data-pr="' + i + '"]').value;
            put(rows[i][otherKey], majorToCents(v)).then(function () { toastOk(tt("common.saved", "Saved")); loadRows(); }).catch(toastErr);
          };
        });
        box.querySelectorAll("[data-pd]").forEach(function (b) {
          b.onclick = function () {
            var r = rows[Number(b.getAttribute("data-pd"))];
            api("/api/erp/b1/m/priceListItems/" + r.id + "/delete", "POST", {}).then(loadRows).catch(toastErr);
          };
        });
        box.querySelector("[data-padd]").onclick = function () {
          var k = box.querySelector("[data-pnew-key]").value;
          if (!Number(k)) { toastErr({ message: tt("b1.pickOne", "Select a value first") }); return; }
          put(k, majorToCents(box.querySelector("[data-pnew-price]").value)).then(function () { toastOk(tt("common.saved", "Saved")); loadRows(); }).catch(toastErr);
        };
      }
      loadRows();
    }

    function save() {
      syncPane();
      /* required check across all tabs */
      var missing = ent.fields.filter(function (f) { return f.required && (rec[f.key] == null || rec[f.key] === "" || rec[f.key] === 0); });
      if (missing.length) {
        toastErr({ message: tt("common.required", "Required") + ": " + missing.map(function (f) { return tt(f.labelKey, f.label); }).join(", ") });
        return;
      }
      var payload = {};
      ent.fields.forEach(function (f) {
        if (rec[f.key] !== undefined) payload[f.key] = rec[f.key];
      });
      if (rec.id) { delete payload[ent.fields[0].key]; }
      var path = "/api/erp/b1/m/" + ent.api + (rec.id ? "/" + rec.id : "");
      api(path, "POST", payload).then(function (saved) {
        invalidate();
        toastOk(tt("common.saved", "Saved"));
        if (onSaved) onSaved();
        if (!rec.id && saved && saved.id) {
          rec = saved;
          paintShell();
          return;
        }
        if (saved && saved.id) rec = saved;
        paintShell();
      }).catch(toastErr);
    }
  }

  B1.api = api; B1.qs = qs; B1.lookup = lookup; B1.lookupAll = lookupAll; B1.invalidate = invalidate;
  B1.labelOf = labelOf; B1.optionsHtml = optionsHtml; B1.findById = findById;
  B1.fmtMoney = fmtMoney; B1.centsToMajor = centsToMajor; B1.majorToCents = majorToCents;
  B1.msToDateInput = msToDateInput; B1.dateInputToMs = dateInputToMs;
  B1.tt = tt; B1.esc = esc; B1.errMsg = errMsg; B1.toastErr = toastErr; B1.toastOk = toastOk;
  B1.fieldHtml = fieldHtml; B1.fieldsGrid = fieldsGrid; B1.readFields = readFields;
  B1.renderMaster = renderMaster; B1.openMasterEditor = openMasterEditor;
  B1.entities = ENTITIES;
})(window);
