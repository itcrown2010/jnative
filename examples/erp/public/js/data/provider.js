// Author: 张晓达
/**
 * DataProvider - Rpc talks to erp-server; mock kept only for offline tooling (not default).
 */
(function (global) {
  function MockDataProvider() {
    this.kind = "mock";
  }
  MockDataProvider.prototype.list = function (collection, query) {
    return Promise.resolve(JnMockData.list(collection, query));
  };
  MockDataProvider.prototype.get = function (collection, id) {
    return Promise.resolve(JnMockData.get(collection, id));
  };
  MockDataProvider.prototype.save = function (collection, record) {
    return Promise.resolve(JnMockData.save(collection, record));
  };
  MockDataProvider.prototype.remove = function (collection, id) {
    return Promise.resolve(JnMockData.remove(collection, id));
  };
  MockDataProvider.prototype.dashboard = function (kind) {
    return Promise.resolve(JnMockData.dashboard(kind));
  };

  function apiError(res, body) {
    var msg = "Request failed";
    var code = "HTTP_" + res.status;
    try {
      var j = typeof body === "string" ? JSON.parse(body) : body;
      if (j && j.message) msg = j.message;
      if (j && j.code) code = j.code;
    } catch (e) {
      if (body) msg = String(body).slice(0, 200);
    }
    var err = new Error(msg);
    err.code = code;
    err.status = res.status;
    return err;
  }

  function RpcDataProvider(baseUrl) {
    this.kind = "rpc";
    this.baseUrl = baseUrl || "";
    this._cache = {};
    this._csrf = "";
    this.user = null;
    this.book = null;
  }

  RpcDataProvider.prototype._url = function (path) {
    return this.baseUrl + path;
  };

  RpcDataProvider.prototype._fetch = function (path, opts) {
    opts = opts || {};
    var headers = Object.assign({ "Accept": "application/json" }, opts.headers || {});
    if (opts.method && opts.method !== "GET" && this._csrf) {
      headers["X-CSRF-Token"] = this._csrf;
    }
    if (opts.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    return fetch(this._url(path), {
      method: opts.method || "GET",
      credentials: "same-origin",
      headers: headers,
      body: opts.body || undefined
    }).then(function (res) {
      return res.text().then(function (text) {
        if (!res.ok) throw apiError(res, text);
        if (!text) return null;
        try { return JSON.parse(text); } catch (e) { return text; }
      });
    });
  };

  RpcDataProvider.prototype.login = function (email, password) {
    var self = this;
    return this._fetch("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email: email, password: password })
    }).then(function (data) {
      self.user = data.user || null;
      self._csrf = data.csrfToken || "";
      try {
        sessionStorage.setItem("jn_auth", "1");
        sessionStorage.setItem("jn_rpc_auth", "1");
        sessionStorage.setItem("jn_user", JSON.stringify(self.user || {}));
        sessionStorage.setItem("jn_csrf", self._csrf);
      } catch (e) {}
      return data;
    });
  };

  RpcDataProvider.prototype.logout = function () {
    var self = this;
    return this._fetch("/api/auth/logout", { method: "POST" }).catch(function () {
      return null;
    }).then(function () {
      self.user = null;
      self._csrf = "";
      self._cache = {};
      try {
        sessionStorage.removeItem("jn_auth");
        sessionStorage.removeItem("jn_rpc_auth");
        sessionStorage.removeItem("jn_user");
        sessionStorage.removeItem("jn_csrf");
        sessionStorage.removeItem("jn_demo_auth");
      } catch (e) {}
    });
  };

  RpcDataProvider.prototype.loadSession = function () {
    var self = this;
    try {
      self._csrf = sessionStorage.getItem("jn_csrf") || "";
      var u = sessionStorage.getItem("jn_user");
      if (u) self.user = JSON.parse(u);
    } catch (e) {}
    return this._fetch("/api/auth/currentUser").then(function (user) {
      self.user = user;
      try { sessionStorage.setItem("jn_user", JSON.stringify(user || {})); } catch (e) {}
      return user;
    });
  };

  RpcDataProvider.prototype.loadBook = function () {
    var self = this;
    return this._fetch("/api/erp/book").then(function (b) {
      self.book = b;
      return b;
    }).catch(function () {
      self.book = null;
      return null;
    });
  };

  RpcDataProvider.prototype.invalidate = function (collection) {
    if (collection) delete this._cache[collection];
    else this._cache = {};
  };

  function mapItem(it) {
    var cents = Number(it.unitPriceCents || 0);
    return {
      id: it.id,
      sku: it.sku,
      name: it.name,
      category: it.category || "",
      uom: it.uom || "EA",
      price: cents / 100,
      cost: cents / 100,
      unitPriceCents: cents,
      warehouse: it.warehouse || "",
      onHand: it.onHand != null ? it.onHand : "",
      status: it.status || "ACTIVE"
    };
  }

  function mapPartner(p, kind) {
    return {
      id: p.id,
      code: p.code,
      name: p.name,
      kind: p.kind || kind,
      city: p.city || "",
      country: p.country || "",
      email: p.email || "",
      status: p.status || "ACTIVE",
      creditLimit: p.creditLimit,
      paymentTerms: p.paymentTerms || ""
    };
  }

  function mapWarehouse(w) {
    return {
      id: w.id,
      code: w.code,
      name: w.name,
      city: w.city || "",
      status: w.status || "ACTIVE"
    };
  }

  RpcDataProvider.prototype._fetchCollection = function (collection) {
    var self = this;
    if (this._cache[collection]) {
      return Promise.resolve(this._cache[collection]);
    }
    var path = null;
    var map = null;
    if (collection === "items") {
      path = "/api/erp/items";
      map = function (arr) { return (arr || []).map(mapItem); };
    } else if (collection === "customers") {
      path = "/api/erp/partners?kind=CUSTOMER";
      map = function (arr) { return (arr || []).map(function (p) { return mapPartner(p, "CUSTOMER"); }); };
    } else if (collection === "suppliers") {
      path = "/api/erp/partners?kind=SUPPLIER";
      map = function (arr) { return (arr || []).map(function (p) { return mapPartner(p, "SUPPLIER"); }); };
    } else if (collection === "warehouses") {
      path = "/api/erp/warehouses";
      map = function (arr) { return (arr || []).map(mapWarehouse); };
    } else if (collection === "inventoryBalances") {
      path = "/api/erp/inventory";
      map = function (arr) {
        return (arr || []).map(function (b) {
          return {
            id: String(b.warehouseId) + "-" + String(b.itemId),
            warehouse: b.warehouseId,
            sku: b.itemId,
            name: "",
            qty: b.quantityOnHand,
            value: ""
          };
        });
      };
    }
    if (!path) {
      return Promise.reject(new Error("Collection not available on server: " + collection));
    }
    return this._fetch(path).then(function (data) {
      var rows = map(Array.isArray(data) ? data : []);
      self._cache[collection] = rows;
      return rows;
    });
  };

  RpcDataProvider.prototype.list = function (collection, query) {
    query = query || {};
    return this._fetchCollection(collection).then(function (rows) {
      var q = (query.query || query.q || "").toLowerCase();
      var status = query.status || "";
      var filtered = rows.filter(function (r) {
        if (status && String(r.status || "") !== status) return false;
        if (!q) return true;
        return JSON.stringify(r).toLowerCase().indexOf(q) >= 0;
      });
      var page = query.page || 1;
      var pageSize = query.pageSize || 20;
      var start = (page - 1) * pageSize;
      return {
        rows: filtered.slice(start, start + pageSize),
        total: filtered.length,
        page: page,
        pageSize: pageSize
      };
    });
  };

  RpcDataProvider.prototype.get = function (collection, id) {
    return this._fetchCollection(collection).then(function (rows) {
      var found = null;
      rows.forEach(function (r) {
        if (String(r.id) === String(id)) found = r;
      });
      if (!found) throw new Error("Not found");
      return JSON.parse(JSON.stringify(found));
    });
  };

  RpcDataProvider.prototype.save = function (collection, record) {
    var self = this;
    if (collection === "items") {
      if (record.id && String(record.id).indexOf("NEW") < 0) {
        return Promise.reject(new Error("Item update API not available yet"));
      }
      var cents = record.unitPriceCents;
      if (cents == null && record.price != null) {
        cents = Math.round(Number(record.price) * 100);
      }
      return this._fetch("/api/erp/items", {
        method: "POST",
        body: JSON.stringify({
          sku: record.sku,
          name: record.name,
          unitPriceCents: cents || 0
        })
      }).then(function (created) {
        self.invalidate("items");
        return mapItem(created);
      });
    }
    return Promise.reject(new Error("Save not available for " + collection));
  };

  RpcDataProvider.prototype.remove = function () {
    return Promise.reject(new Error("Delete not available"));
  };

  RpcDataProvider.prototype.dashboard = function () {
    return this._fetch("/api/erp/dashboard").then(function (d) {
      return {
        kpis: [
          { k: "customers", v: d.customerCount, d: "" },
          { k: "suppliers", v: d.supplierCount, d: "" },
          { k: "items", v: d.itemCount, d: "" },
          { k: "openPo", v: d.openPoCount, d: "" },
          { k: "openSo", v: d.openSoCount, d: "" }
        ],
        trend: { label: "", points: [] },
        alerts: []
      };
    });
  };

  global.JnData = {
    MockDataProvider: MockDataProvider,
    RpcDataProvider: RpcDataProvider,
    active: null,
    useMock: function () { this.active = new MockDataProvider(); },
    useRpc: function (url) { this.active = new RpcDataProvider(url); }
  };
  global.JnData.useRpc("");
})(window);
