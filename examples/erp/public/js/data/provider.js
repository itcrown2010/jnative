// Author: 张晓达
/**
 * DataProvider — business data via erp-server RPC only (no localStorage mock fallback).
 */
(function (global) {
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

  function centsToMoney(cents) {
    return (Number(cents) || 0) / 100;
  }

  function pageFilter(rows, query) {
    query = query || {};
    var q = (query.query || query.q || "").toLowerCase();
    var status = query.status || "";
    var filtered = (rows || []).filter(function (r) {
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
    var headers = Object.assign({ Accept: "application/json" }, opts.headers || {});
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
        try {
          return JSON.parse(text);
        } catch (e) {
          return text;
        }
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
        sessionStorage.removeItem("jn_demo_auth");
      } catch (e) {}
      return data;
    });
  };

  RpcDataProvider.prototype.enterDemo = function () {
    var self = this;
    return this._fetch("/api/auth/demo-enter", { method: "POST", body: "{}" }).then(function (data) {
      self.user = data.user || null;
      self._csrf = data.csrfToken || "";
      try {
        sessionStorage.setItem("jn_auth", "1");
        sessionStorage.setItem("jn_rpc_auth", "1");
        sessionStorage.setItem("jn_user", JSON.stringify(self.user || {}));
        sessionStorage.setItem("jn_csrf", self._csrf);
        sessionStorage.removeItem("jn_demo_auth");
      } catch (e) {}
      return data;
    });
  };

  RpcDataProvider.prototype.logout = function () {
    var self = this;
    return this._fetch("/api/auth/logout", { method: "POST" })
      .catch(function () {
        return null;
      })
      .then(function () {
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
      try {
        sessionStorage.setItem("jn_user", JSON.stringify(user || {}));
        sessionStorage.setItem("jn_auth", "1");
        sessionStorage.setItem("jn_rpc_auth", "1");
      } catch (e2) {}
      return user;
    });
  };

  RpcDataProvider.prototype.loadBook = function () {
    var self = this;
    return this._fetch("/api/erp/book")
      .then(function (book) {
        self.book = book;
        return book;
      })
      .catch(function () {
        self.book = null;
        return null;
      });
  };

  RpcDataProvider.prototype.demoAvailable = function () {
    return this._fetch("/api/auth/demo-available").then(function (d) {
      return !!(d && d.available);
    }).catch(function () {
      return false;
    });
  };

  RpcDataProvider.prototype.invalidate = function (collection) {
    if (collection) delete this._cache[collection];
    else this._cache = {};
  };

  function mapItem(it) {
    var cents = it.unitPriceCents != null ? it.unitPriceCents : 0;
    return {
      id: it.id,
      sku: it.sku,
      name: it.name,
      category: it.category || it.categoryCode || "",
      uom: it.uom || "EA",
      price: centsToMoney(cents),
      cost: centsToMoney(it.unitCostAmount != null ? it.unitCostAmount : cents),
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

  function mapDoc(d, partnerField) {
    var total = d.total != null ? d.total : centsToMoney(d.totalAmount || d.totalCents || 0);
    var row = {
      id: d.id,
      docNo: d.docNo || d.invoiceNumber || d.code || "",
      date: d.date || d.invoiceDate || d.createdAt || "",
      dueDate: d.dueDate || "",
      status: d.status || "",
      total: total,
      openAmount: d.openAmount != null ? centsToMoney(d.openAmount) : "",
      qty: d.qty != null ? d.qty : d.quantity,
      lines: d.lines || []
    };
    if (partnerField === "customerName") {
      row.customerName = d.customerName || d.partnerName || "";
      row.customerId = d.customerId;
    } else if (partnerField === "supplierName") {
      row.supplierName = d.supplierName || d.partnerName || "";
      row.supplierId = d.supplierId;
      row.requester = d.requester || d.requesterName || "";
    }
    return row;
  }

  /** Collection → API path and row mapper. Only implemented backends. */
  var COLLECTION_API = {
    items: { path: "/api/erp/items", map: function (arr) { return (arr || []).map(mapItem); } },
    customers: {
      path: "/api/erp/partners?kind=CUSTOMER",
      map: function (arr) { return (arr || []).map(function (p) { return mapPartner(p, "CUSTOMER"); }); }
    },
    suppliers: {
      path: "/api/erp/partners?kind=SUPPLIER",
      map: function (arr) { return (arr || []).map(function (p) { return mapPartner(p, "SUPPLIER"); }); }
    },
    warehouses: { path: "/api/erp/warehouses", map: function (arr) { return (arr || []).map(mapWarehouse); } },
    categories: {
      path: "/api/erp/categories",
      map: function (arr) {
        return (arr || []).map(function (c) {
          return { id: c.id, code: c.code, name: c.name };
        });
      }
    },
    units: {
      path: "/api/erp/units",
      map: function (arr) {
        return (arr || []).map(function (u) {
          return { id: u.id, code: u.code, name: u.name };
        });
      }
    },
    employees: {
      path: "/api/erp/employees",
      map: function (arr) {
        return (arr || []).map(function (e) {
          return {
            id: e.id,
            code: e.code,
            name: e.name,
            department: e.department || "",
            title: e.title || "",
            status: e.status || "ACTIVE"
          };
        });
      }
    },
    departments: {
      path: "/api/erp/departments",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return { id: d.id, code: d.code, name: d.name };
        });
      }
    },
    inventoryBalances: {
      path: "/api/erp/inventory",
      map: function (arr) {
        return (arr || []).map(function (b) {
          return {
            id: String(b.warehouseId) + "-" + String(b.itemId),
            warehouse: b.warehouseCode || b.warehouseId,
            sku: b.itemSku || b.itemId,
            name: b.itemName || "",
            qty: b.quantityOnHand,
            value: b.value != null ? centsToMoney(b.value) : ""
          };
        });
      }
    },
    inventoryTransactions: {
      path: "/api/erp/inventory/transactions",
      map: function (arr) {
        return (arr || []).map(function (t) {
          return {
            id: t.id,
            date: t.createdAt || t.date || "",
            type: t.txnType || t.type || "",
            sku: t.itemSku || t.itemId || "",
            qty: t.quantity,
            ref: t.refDocNo || t.reference || "",
            warehouse: t.warehouseCode || t.warehouseId || ""
          };
        });
      }
    },
    purchaseRequests: {
      path: "/api/erp/purchaseRequests",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return mapDoc(d, "supplierName");
        });
      }
    },
    purchaseOrders: {
      path: "/api/erp/purchaseOrders",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return mapDoc(d, "supplierName");
        });
      }
    },
    goodsReceipts: {
      path: "/api/erp/goodsReceipts",
      map: function (arr) {
        return (arr || []).map(function (d) {
          var row = mapDoc(d, "supplierName");
          row.qty = d.qty != null ? d.qty : d.totalQty;
          return row;
        });
      }
    },
    salesOrders: {
      path: "/api/erp/salesOrders",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return mapDoc(d, "customerName");
        });
      }
    },
    deliveries: {
      path: "/api/erp/deliveries",
      map: function (arr) {
        return (arr || []).map(function (d) {
          var row = mapDoc(d, "customerName");
          row.qty = d.qty != null ? d.qty : d.totalQty;
          return row;
        });
      }
    },
    arInvoices: {
      path: "/api/erp/finance/ar",
      map: function (arr) {
        return (arr || []).map(function (d) {
          var row = mapDoc(d, "customerName");
          row.docNo = d.invoiceNumber || row.docNo;
          row.total = centsToMoney(d.totalAmount);
          row.openAmount = centsToMoney(d.openAmount);
          row.customerName = d.customerName || "";
          return row;
        });
      }
    },
    apInvoices: {
      path: "/api/erp/finance/ap",
      map: function (arr) {
        return (arr || []).map(function (d) {
          var row = mapDoc(d, "supplierName");
          row.docNo = d.invoiceNumber || row.docNo;
          row.total = centsToMoney(d.totalAmount);
          row.openAmount = centsToMoney(d.openAmount);
          row.supplierName = d.supplierName || "";
          return row;
        });
      }
    },
    incomingPayments: {
      path: "/api/erp/finance/incoming-payments",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return {
            id: d.id,
            docNo: d.docNo || d.paymentNumber || "",
            customerName: d.customerName || "",
            date: d.paymentDate || d.date || "",
            amount: centsToMoney(d.amount),
            status: d.status || "POSTED"
          };
        });
      }
    },
    outgoingPayments: {
      path: "/api/erp/finance/outgoing-payments",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return {
            id: d.id,
            docNo: d.docNo || d.paymentNumber || "",
            supplierName: d.supplierName || "",
            date: d.paymentDate || d.date || "",
            amount: centsToMoney(d.amount),
            status: d.status || "POSTED"
          };
        });
      }
    },
    accounts: {
      path: "/api/erp/finance/accounts",
      map: function (arr) {
        return (arr || []).map(function (a) {
          return {
            id: a.id,
            code: a.accountCode || a.code,
            name: a.accountName || a.name,
            type: a.accountType || a.type || "",
            balance: a.balance != null ? centsToMoney(a.balance) : ""
          };
        });
      }
    },
    journalEntries: {
      path: "/api/erp/finance/journals",
      map: function (arr) {
        return (arr || []).map(function (j) {
          return {
            id: j.id,
            docNo: j.journalNumber || "",
            date: j.postingDate || "",
            source: j.sourceType || "",
            description: j.description || "",
            debit: centsToMoney(j.totalDebit),
            credit: centsToMoney(j.totalCredit),
            status: j.status || ""
          };
        });
      }
    },
    glLines: {
      path: "/api/erp/finance/gl",
      map: function (arr) {
        return (arr || []).map(function (g, idx) {
          return {
            id: g.id || idx,
            date: g.postingDate || g.date || "",
            account: g.accountCode || g.account || "",
            debit: centsToMoney(g.debitAmount || g.debit),
            credit: centsToMoney(g.creditAmount || g.credit),
            journal: g.journalNumber || g.journal || ""
          };
        });
      }
    },
    trialBalance: {
      path: "/api/erp/finance/trialBalance",
      map: function (arr) {
        return (arr || []).map(function (t, idx) {
          return {
            id: t.id || idx,
            account: t.accountCode || t.account || "",
            debit: centsToMoney(t.debitAmount || t.debit),
            credit: centsToMoney(t.creditAmount || t.credit)
          };
        });
      }
    },
    bankAccounts: {
      path: "/api/erp/finance/banks",
      map: function (arr) {
        return (arr || []).map(function (b) {
          return {
            id: b.id,
            code: b.code || b.accountCode || "",
            name: b.name || b.accountName || "",
            currency: b.currency || "CNY",
            balance: b.balance != null ? centsToMoney(b.balance) : ""
          };
        });
      }
    },
    fiscalPeriods: {
      path: "/api/erp/finance/periods",
      map: function (arr) {
        return (arr || []).map(function (p) {
          return {
            id: p.id,
            code: p.code || (String(p.fiscalYear) + "-" + String(p.periodNumber)),
            name: p.name || ("FY" + p.fiscalYear + " P" + p.periodNumber),
            status: p.status || ""
          };
        });
      }
    },
    users: {
      path: "/api/erp/admin/users",
      map: function (arr) {
        return (arr || []).map(function (u) {
          return {
            id: u.id,
            email: u.email,
            name: u.displayName || u.name,
            role: u.role || "",
            status: u.status || "ACTIVE"
          };
        });
      }
    },
    roles: {
      path: "/api/erp/admin/roles",
      map: function (arr) {
        return (arr || []).map(function (r) {
          return { id: r.id, code: r.code, name: r.name };
        });
      }
    },
    auditLogs: {
      path: "/api/erp/admin/audit",
      map: function (arr) {
        return (arr || []).map(function (a) {
          return {
            id: a.id,
            action: a.action,
            entity: a.entityType || "",
            detail: a.detail || "",
            at: a.createdAt || ""
          };
        });
      }
    },
    settings: {
      path: "/api/erp/admin/settings",
      map: function (arr) {
        return (arr || []).map(function (s) {
          return { id: s.id || s.key, key: s.key, value: s.value };
        });
      }
    }
  };

  RpcDataProvider.prototype._fetchCollection = function (collection) {
    var self = this;
    if (this._cache[collection]) {
      return Promise.resolve(this._cache[collection]);
    }
    var conf = COLLECTION_API[collection];
    if (!conf) {
      return Promise.reject(new Error("Collection not available on server: " + collection));
    }
    return this._fetch(conf.path).then(function (data) {
      var rows = conf.map(Array.isArray(data) ? data : []);
      self._cache[collection] = rows;
      return rows;
    });
  };

  RpcDataProvider.prototype.list = function (collection, query) {
    return this._fetchCollection(collection).then(function (rows) {
      return pageFilter(rows, query);
    });
  };

  RpcDataProvider.prototype.get = function (collection, id) {
    var conf = COLLECTION_API[collection];
    if (conf && (collection === "purchaseRequests" || collection === "purchaseOrders" ||
        collection === "salesOrders" || collection === "goodsReceipts" || collection === "deliveries")) {
      return this._fetch(conf.path + "/" + encodeURIComponent(id)).then(function (d) {
        if (collection === "purchaseRequests" || collection === "purchaseOrders") {
          return mapDoc(d, "supplierName");
        }
        if (collection === "salesOrders" || collection === "deliveries") {
          return mapDoc(d, "customerName");
        }
        return mapDoc(d, "supplierName");
      });
    }
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
    if (collection === "purchaseRequests") {
      return this._fetch("/api/erp/purchaseRequests", {
        method: "POST",
        body: JSON.stringify(record)
      }).then(function (created) {
        self.invalidate("purchaseRequests");
        return mapDoc(created, "supplierName");
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
        alerts: [],
        todos: [],
        recent: [],
        quickActions: []
      };
    });
  };

  RpcDataProvider.prototype.resetDemo = function () {
    var self = this;
    return this._fetch("/api/erp/admin/demo/reset", { method: "POST", body: "{}" }).then(function (r) {
      self._cache = {};
      return r;
    });
  };

  global.JnData = {
    RpcDataProvider: RpcDataProvider,
    active: null,
    mode: "rpc",
    useRpc: function (url) {
      this.mode = "rpc";
      this.active = new RpcDataProvider(url);
    }
  };
  global.JnData.useRpc("");
})(window);
