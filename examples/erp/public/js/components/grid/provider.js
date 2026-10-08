/**
 * GridDataProvider - Mock in-memory query/count/aggregate; Rpc stub boundary.
 */
(function (global) {
  function colMap(columns) {
    var m = {};
    (columns || []).forEach(function (c) { m[c.field] = c; });
    return m;
  }

  function MockGridDataProvider() {
    this.kind = "mock";
    this._synth = {}; /* collection -> generated rows for large demos */
  }

  MockGridDataProvider.prototype._allRows = function (collection) {
    if (this._synth[collection]) return this._synth[collection].slice();
    if (global.JnDemoState) {
      return JSON.parse(JSON.stringify(JnDemoState.collection(collection) || []));
    }
    var res = JnMockData.list(collection, { page: 1, pageSize: 100000 });
    return (res && res.rows) ? res.rows : [];
  };

  /** Generate synthetic dataset for perf tests - not formal ERP data. */
  MockGridDataProvider.prototype.ensureSynthetic = function (collection, n) {
    if (this._synth[collection] && this._synth[collection].length >= n) return;
    var rows = [];
    var statuses = ["DRAFT", "OPEN", "APPROVED", "POSTED", "PARTIAL"];
    var cities = ["Shanghai", "Beijing", "Shenzhen", "Hangzhou", "Suzhou"];
    for (var i = 0; i < n; i++) {
      rows.push({
        id: "SYN-" + i,
        code: "C-" + (10000 + i),
        name: "Synth Customer " + i,
        city: cities[i % cities.length],
        creditLimit: ((i % 50) + 1) * 1000,
        status: statuses[i % statuses.length],
        date: "2026-" + pad(((i % 12) + 1)) + "-" + pad((i % 28) + 1),
        total: ((i % 200) + 1) * 10.5,
        customerName: "Partner " + (i % 40),
        docNo: "DOC-" + (2000 + i),
        sku: "SKU-" + (i % 500),
        qty: (i % 100) + 1,
        value: ((i % 80) + 1) * 25,
        warehouse: i % 2 ? "WH-MAIN" : "WH-RM",
        openAmount: ((i % 30) + 1) * 100,
        demo: true
      });
    }
    this._synth[collection] = rows;
  };

  function pad(n) { return n < 10 ? "0" + n : String(n); }

  MockGridDataProvider.prototype.query = function (collection, state, columns) {
    var self = this;
    return Promise.resolve().then(function () {
      var t0 = performance.now();
      var rows = self._allRows(collection);
      var cmap = colMap(columns);
      var filtered = rows.filter(function (r) {
        return JnGridFilter.matchGroup(r, state.filters, cmap);
      });
      var filterMs = Math.round(performance.now() - t0);
      var t1 = performance.now();
      var sorted = JnGridFilter.stableSort(filtered, state.sorting);
      var sortMs = Math.round(performance.now() - t1);
      var page = state.page || 1;
      var pageSize = state.pageSize || 20;
      var start = (page - 1) * pageSize;
      var pageRows = sorted.slice(start, start + pageSize);
      var groups = null;
      if (state.grouping && state.grouping.field) {
        var gf = state.grouping.field;
        var map = {};
        sorted.forEach(function (r) {
          var k = r[gf] == null ? "(blank)" : String(r[gf]);
          if (!map[k]) map[k] = { key: k, count: 0, rows: [] };
          map[k].count++;
        });
        groups = Object.keys(map).map(function (k) { return map[k]; });
      }
      return {
        rows: pageRows,
        total: sorted.length,
        page: page,
        pageSize: pageSize,
        groups: groups,
        demo: true,
        timings: { filterMs: filterMs, sortMs: sortMs, totalMs: Math.round(performance.now() - t0) }
      };
    });
  };

  MockGridDataProvider.prototype.count = function (collection, state, columns) {
    return this.query(collection, Object.assign({}, state, { page: 1, pageSize: 1 }), columns).then(function (r) {
      return { total: r.total, demo: true };
    });
  };

  MockGridDataProvider.prototype.aggregate = function (collection, state, columns, specs) {
    var self = this;
    return this.query(collection, Object.assign({}, state, { page: 1, pageSize: 1000000 }), columns).then(function (res) {
      /* Re-filter full set for aggregate - query already filtered but page capped.
         For mock, re-run on all filtered via internal. */
      var rows = self._allRows(collection);
      var cmap = colMap(columns);
      var filtered = rows.filter(function (r) { return JnGridFilter.matchGroup(r, state.filters, cmap); });
      filtered = JnGridFilter.stableSort(filtered, state.sorting);
      var out = {};
      (specs || []).forEach(function (s) {
        var col = cmap[s.field];
        var allowed = JnGridModel.AGG_BY_TYPE[(col && col.dataType) || "STRING"] || ["COUNT"];
        var fn = (s.fn || "COUNT").toUpperCase();
        if (allowed.indexOf(fn) < 0) {
          out[s.field + ":" + fn] = { error: "Illegal aggregate for type", demo: true };
          return;
        }
        out[s.field + ":" + fn] = {
          value: JnGridFilter.aggregate(filtered, s.field, fn, col && col.dataType),
          demo: true
        };
      });
      return { aggregates: out, total: filtered.length, demo: true };
    });
  };

  function RpcGridDataProvider(baseUrl) {
    this.kind = "rpc";
    this.baseUrl = baseUrl || "";
  }
  RpcGridDataProvider.prototype.query = function () {
    return Promise.reject(new Error("RpcGridDataProvider not connected - DEMO mode only (no forged server query)"));
  };
  RpcGridDataProvider.prototype.count = function () {
    return Promise.reject(new Error("RpcGridDataProvider not connected - DEMO mode only"));
  };
  RpcGridDataProvider.prototype.aggregate = function () {
    return Promise.reject(new Error("RpcGridDataProvider not connected - DEMO mode only"));
  };

  global.JnGridData = {
    MockGridDataProvider: MockGridDataProvider,
    RpcGridDataProvider: RpcGridDataProvider,
    active: new MockGridDataProvider(),
    useMock: function () { this.active = new MockGridDataProvider(); },
    useRpc: function (url) { this.active = new RpcGridDataProvider(url); }
  };
})(window);
