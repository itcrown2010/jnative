/**
 * MockData facade over DemoStateStore (UI 1.4).
 * All mutations for master/docs should prefer JnAppService.
 * list/get remain read adapters for grids/templates.
 */
(function (global) {
  function clone(v) { return JSON.parse(JSON.stringify(v)); }

  function store() {
    return JnDemoState.getState();
  }

  function list(collection, query) {
    var rows = clone(store()[collection] || []);
    query = query || {};
    if (query.q) {
      var q = String(query.q).toLowerCase();
      rows = rows.filter(function (r) {
        return JSON.stringify(r).toLowerCase().indexOf(q) >= 0;
      });
    }
    if (query.status) {
      rows = rows.filter(function (r) { return r.status === query.status; });
    }
    var sort = query.sort || "";
    if (sort) {
      var desc = sort.charAt(0) === "-";
      var key = desc ? sort.slice(1) : sort;
      rows.sort(function (a, b) {
        var av = a[key], bv = b[key];
        if (av === bv) return 0;
        if (av == null) return 1;
        if (bv == null) return -1;
        if (av > bv) return desc ? -1 : 1;
        return desc ? 1 : -1;
      });
    }
    var page = query.page || 1;
    var pageSize = query.pageSize || 20;
    var start = (page - 1) * pageSize;
    return {
      rows: rows.slice(start, start + pageSize),
      total: rows.length,
      page: page,
      pageSize: pageSize,
      demo: true
    };
  }

  function get(collection, id) {
    return JnDemoState.find(collection, id);
  }

  function save(collection, record) {
    /* Route known masters/docs through ApplicationService when available */
    if (global.JnAppService) {
      if (collection === "customers" || collection === "suppliers" || collection === "items") {
        return JnAppService.upsertMaster(collection, record);
      }
      if (collection === "salesOrders") return JnAppService.saveSalesOrder(record);
      if (collection === "purchaseOrders") return JnAppService.savePurchaseOrder(record);
      if (collection === "productionOrders") return JnAppService.saveProductionOrder(record);
    }
    if (!record.id) record.id = "M" + Math.random().toString(36).slice(2, 9);
    return JnDemoState.upsert(collection, record);
  }

  function remove(collection, idVal) {
    return JnDemoState.remove(collection, idVal);
  }

  function dashboard(kind) {
    return JnDemoState.computeDashboard(kind || "executive");
  }

  function recalcLines(lines) {
    var total = 0;
    (lines || []).forEach(function (l) {
      l.qty = Number(l.qty) || 0;
      l.price = Number(l.price) || 0;
      l.amount = Math.round(l.qty * l.price * 100) / 100;
      total += l.amount;
    });
    return Math.round(total * 100) / 100;
  }

  global.JnMockData = {
    get store() { return store(); },
    list: list,
    get: get,
    save: save,
    remove: remove,
    dashboard: dashboard,
    recalcLines: recalcLines,
    notifications: function () { return clone(store().notifications || []); },
    mode: "DEMO",
    persistenceNote: function () { return JnDemoState.persistenceNote(); }
  };
})(window);
