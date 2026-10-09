// Author: 张晓达
/**
 * DemoStateStore - single source of DEMO truth.
 * Persistence: localStorage (survives refresh). Cleared by Reset or clearing site data.
 * NOT server / formal DB persistence.
 */
(function (global) {
  var KEY = "jn_demo_state_v14";
  var state = null;
  var persistEnabled = true;

  function clone(v) { return JSON.parse(JSON.stringify(v)); }

  function load() {
    if (!persistEnabled) {
      state = JnDemoSeed.build();
      return state;
    }
    try {
      var raw = localStorage.getItem(KEY);
      if (raw) {
        state = JSON.parse(raw);
        if (!state.meta || state.meta.version !== 14) {
          state = JnDemoSeed.build();
          persist();
        }
        return state;
      }
    } catch (e) {}
    state = JnDemoSeed.build();
    persist();
    return state;
  }

  function persist() {
    if (!persistEnabled || !state) return;
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch (e) {
      console.warn("DemoStateStore persist failed", e);
    }
  }

  function reset() {
    state = JnDemoSeed.build();
    persist();
    JnDemoEvents.clearHistory();
    JnDemoEvents.emit("DEMO_RESET", { counts: JnDemoSeed.counts(state) });
    JnDemoEvents.emit("STATE_CHANGED", { reason: "reset" });
    return state;
  }

  function getState() {
    if (!state) load();
    return state;
  }

  function collection(name) {
    var s = getState();
    if (!s[name]) s[name] = [];
    return s[name];
  }

  function upsert(name, record) {
    var rows = collection(name);
    var found = false;
    for (var i = 0; i < rows.length; i++) {
      if (rows[i].id === record.id) {
        rows[i] = clone(record);
        found = true;
        break;
      }
    }
    if (!found) rows.unshift(clone(record));
    persist();
    JnDemoEvents.emit("STATE_CHANGED", { collection: name, id: record.id });
    return clone(record);
  }

  function remove(name, id) {
    var s = getState();
    s[name] = (s[name] || []).filter(function (r) { return r.id !== id; });
    persist();
    JnDemoEvents.emit("STATE_CHANGED", { collection: name, id: id, removed: true });
    return true;
  }

  function find(name, id) {
    var rows = collection(name);
    for (var i = 0; i < rows.length; i++) if (rows[i].id === id) return clone(rows[i]);
    return null;
  }

  function findBy(name, pred) {
    return collection(name).filter(pred).map(clone);
  }

  function nextNumber(docType) {
    var rows = collection("numberSeries");
    for (var i = 0; i < rows.length; i++) {
      if (rows[i].docType === docType) {
        var n = rows[i].next;
        rows[i].next = n + 1;
        persist();
        return rows[i].prefix + n;
      }
    }
    return docType + "-" + Date.now();
  }

  function money(n) {
    return Math.round(Number(n) * 100) / 100;
  }

  function fmtMoney(n) {
    return "CNY " + money(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function inventoryValue() {
    var t = 0;
    collection("inventoryBalances").forEach(function (b) { t += Number(b.value) || 0; });
    return money(t);
  }

  function sumOpen(collectionName, field) {
    field = field || "openAmount";
    var t = 0;
    collection(collectionName).forEach(function (r) {
      if (r.status === "CANCELLED" || r.status === "PAID") return;
      t += Number(r[field] != null ? r[field] : r.total) || 0;
    });
    return money(t);
  }

  function sumDocTotals(collectionName, statuses) {
    var t = 0;
    collection(collectionName).forEach(function (r) {
      if (statuses && statuses.indexOf(r.status) < 0) return;
      t += Number(r.total) || 0;
    });
    return money(t);
  }

  function computeKpis() {
    var soOpen = collection("salesOrders").filter(function (r) {
      return r.status === "APPROVED" || r.status === "PARTIAL" || r.status === "SUBMITTED";
    });
    var pendingApproval = collection("salesOrders").filter(function (r) { return r.status === "SUBMITTED"; }).length +
      collection("purchaseOrders").filter(function (r) { return r.status === "SUBMITTED"; }).length;
    var pendingDelivery = soOpen.filter(function (r) {
      return r.status === "APPROVED" || r.status === "PARTIAL";
    }).length;
    return {
      salesAmount: sumDocTotals("salesOrders", null),
      purchaseAmount: sumDocTotals("purchaseOrders", null),
      inventoryValue: inventoryValue(),
      arOpen: sumOpen("arInvoices", "openAmount"),
      apOpen: sumOpen("apInvoices", "openAmount"),
      pendingApproval: pendingApproval,
      pendingDelivery: pendingDelivery,
      openSalesOrders: soOpen.length,
      openPurchaseOrders: collection("purchaseOrders").filter(function (r) {
        return r.status !== "CANCELLED" && r.status !== "CLOSED";
      }).length
    };
  }

  function computeDashboard(kind) {
    var k = computeKpis();
    var kpisBy = {
      executive: [
        { k: "Sales Amount", v: fmtMoney(k.salesAmount), d: "from DemoStateStore" },
        { k: "Purchase Amount", v: fmtMoney(k.purchaseAmount), d: "from DemoStateStore" },
        { k: "Inventory Value", v: fmtMoney(k.inventoryValue), d: "from DemoStateStore" },
        { k: "AR Open", v: fmtMoney(k.arOpen), d: "from DemoStateStore" },
        { k: "AP Open", v: fmtMoney(k.apOpen), d: "from DemoStateStore" },
        { k: "Pending Approval", v: String(k.pendingApproval), d: "SO+PO SUBMITTED" },
        { k: "Pending Delivery", v: String(k.pendingDelivery), d: "APPROVED/PARTIAL SO" }
      ],
      sales: [
        { k: "Orders", v: String(collection("salesOrders").length), d: "DemoStateStore" },
        { k: "Sales Amount", v: fmtMoney(k.salesAmount), d: "DemoStateStore" },
        { k: "Deliveries", v: String(collection("deliveries").length), d: "DemoStateStore" },
        { k: "AR Open", v: fmtMoney(k.arOpen), d: "DemoStateStore" }
      ],
      purchasing: [
        { k: "POs", v: String(collection("purchaseOrders").length), d: "DemoStateStore" },
        { k: "Purchase Amount", v: fmtMoney(k.purchaseAmount), d: "DemoStateStore" },
        { k: "GRs", v: String(collection("goodsReceipts").length), d: "DemoStateStore" },
        { k: "AP Open", v: fmtMoney(k.apOpen), d: "DemoStateStore" }
      ],
      inventory: [
        { k: "SKUs on hand", v: String(collection("inventoryBalances").length), d: "DemoStateStore" },
        { k: "Value", v: fmtMoney(k.inventoryValue), d: "DemoStateStore" },
        { k: "Transactions", v: String(collection("inventoryTransactions").length), d: "DemoStateStore" },
        { k: "Alerts", v: String(collection("inventoryAlerts").length), d: "DemoStateStore" }
      ],
      finance: [
        { k: "Journals", v: String(collection("journalEntries").length), d: "DemoStateStore" },
        { k: "AR Open", v: fmtMoney(k.arOpen), d: "DemoStateStore" },
        { k: "AP Open", v: fmtMoney(k.apOpen), d: "DemoStateStore" },
        { k: "Bank", v: fmtMoney((collection("bankAccounts")[0] || {}).balance || 0), d: "DemoStateStore" }
      ],
      manufacturing: [
        { k: "Open MOs", v: String(collection("productionOrders").filter(function (r) { return r.status !== "CLOSED"; }).length), d: "DemoStateStore" },
        { k: "BOMs", v: String(collection("boms").length), d: "DemoStateStore" },
        { k: "MRP Suggestions", v: String(collection("mrpSuggestions").length), d: "DemoStateStore" },
        { k: "Issues", v: String(collection("productionIssues").length), d: "DemoStateStore" }
      ]
    };
    var trendPts = [k.salesAmount * 0.2, k.salesAmount * 0.35, k.salesAmount * 0.5, k.salesAmount * 0.7, k.salesAmount || 1];
    return {
      kpis: kpisBy[kind] || kpisBy.executive,
      trend: { label: "Activity trend (derived DEMO)", points: trendPts },
      todos: [
        { text: "Pending approvals: " + k.pendingApproval, go: "sales.orders" },
        { text: "Pending deliveries: " + k.pendingDelivery, go: "sales.deliveries" },
        { text: "AR open " + fmtMoney(k.arOpen), go: "fin.ar" }
      ],
      recent: collection("salesOrders").slice(0, 4).map(function (r) {
        return { docNo: r.docNo, partner: r.customerName, status: r.status, go: "sales.orders" };
      }),
      quickActions: [
        { label: "New Sales Order", go: "sales.orders" },
        { label: "New Purchase Order", go: "pur.orders" },
        { label: "Inventory Balance", go: "inv.balance" },
        { label: "Reset Demo Data", go: "sys.settings" }
      ],
      demo: true,
      fromStore: true
    };
  }

  global.JnDemoState = {
    KEY: KEY,
    load: load,
    persist: persist,
    reset: reset,
    getState: getState,
    state: null, /* filled after load */
    collection: collection,
    upsert: upsert,
    remove: remove,
    find: find,
    findBy: findBy,
    nextNumber: nextNumber,
    money: money,
    fmtMoney: fmtMoney,
    computeKpis: computeKpis,
    computeDashboard: computeDashboard,
    persistenceNote: function () {
      return (getState().meta && getState().meta.persistence) || KEY;
    }
  };

  /* bind live state property */
  Object.defineProperty(global.JnDemoState, "state", {
    get: function () { return getState(); }
  });

  load();
})(window);
