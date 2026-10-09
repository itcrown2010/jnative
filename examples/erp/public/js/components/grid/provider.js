// Author: 张晓达
/**
 * GridDataProvider - Rpc loads authoritative rows from server via JnData; client filters/pages.
 */
(function (global) {
  function colMap(columns) {
    var m = {};
    (columns || []).forEach(function (c) { m[c.field] = c; });
    return m;
  }

  function tt(key, params, fb) {
    return global.JnI18n ? JnI18n.t(key, params, fb) : (fb != null ? fb : key);
  }

  function MockGridDataProvider() {
    this.kind = "mock";
  }
  MockGridDataProvider.prototype.query = function () {
    return Promise.reject(new Error(tt("error.mockDisabled", null, "Mock grid disabled")));
  };
  MockGridDataProvider.prototype.count = function () {
    return Promise.reject(new Error(tt("error.mockDisabled", null, "Mock grid disabled")));
  };
  MockGridDataProvider.prototype.aggregate = function () {
    return Promise.reject(new Error(tt("error.mockDisabled", null, "Mock grid disabled")));
  };

  function RpcGridDataProvider(baseUrl) {
    this.kind = "rpc";
    this.baseUrl = baseUrl || "";
  }

  RpcGridDataProvider.prototype._rows = function (collection) {
    if (!global.JnData || !JnData.active || JnData.active.kind !== "rpc") {
      return Promise.reject(new Error(tt("error.rpcRequired", null, "Server data provider required")));
    }
    return JnData.active._fetchCollection(collection);
  };

  RpcGridDataProvider.prototype.query = function (collection, state, columns) {
    var self = this;
    return this._rows(collection).then(function (rows) {
      var t0 = performance.now();
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
        timings: { filterMs: filterMs, sortMs: sortMs, totalMs: Math.round(performance.now() - t0) }
      };
    });
  };

  RpcGridDataProvider.prototype.count = function (collection, state, columns) {
    return this.query(collection, Object.assign({}, state, { page: 1, pageSize: 1 }), columns).then(function (r) {
      return { total: r.total };
    });
  };

  RpcGridDataProvider.prototype.aggregate = function (collection, state, columns, specs) {
    var self = this;
    return this._rows(collection).then(function (rows) {
      var cmap = colMap(columns);
      var filtered = rows.filter(function (r) { return JnGridFilter.matchGroup(r, state.filters, cmap); });
      filtered = JnGridFilter.stableSort(filtered, state.sorting);
      var out = {};
      (specs || []).forEach(function (s) {
        var col = cmap[s.field];
        var allowed = JnGridModel.AGG_BY_TYPE[(col && col.dataType) || "STRING"] || ["COUNT"];
        var fn = (s.fn || "COUNT").toUpperCase();
        if (allowed.indexOf(fn) < 0) {
          out[s.field + ":" + fn] = { error: "Illegal aggregate for type" };
          return;
        }
        out[s.field + ":" + fn] = {
          value: JnGridFilter.aggregate(filtered, s.field, fn, col && col.dataType)
        };
      });
      return { aggregates: out, total: filtered.length };
    });
  };

  global.JnGridData = {
    MockGridDataProvider: MockGridDataProvider,
    RpcGridDataProvider: RpcGridDataProvider,
    active: null,
    useMock: function () { this.active = new MockGridDataProvider(); },
    useRpc: function (url) { this.active = new RpcGridDataProvider(url); }
  };
  global.JnGridData.useRpc("");
})(window);
