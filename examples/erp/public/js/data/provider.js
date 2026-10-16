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
    if (res.status === 401 || code === "AUTH_REQUIRED") {
      err.authRequired = true;
      try {
        sessionStorage.removeItem("jn_auth");
        sessionStorage.removeItem("jn_rpc_auth");
      } catch (e2) {}
      try {
        document.dispatchEvent(new CustomEvent("jn:auth-required", { detail: { message: msg } }));
      } catch (e3) {
        try {
          var ev = document.createEvent("Event");
          ev.initEvent("jn:auth-required", true, true);
          document.dispatchEvent(ev);
        } catch (e4) {}
      }
    }
    return err;
  }

  function dateToMillis(v) {
    if (v == null || v === "" || v === 0 || v === "0") return 0;
    var n = Number(v);
    if (isFinite(n) && n > 1e11) return Math.floor(n);
    if (isFinite(n) && n > 1e8 && n < 1e11) return Math.floor(n * 1000);
    if (isFinite(n) && n > 0 && n < 100000) {
      throw new Error("无效业务日期");
    }
    var s = String(v);
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s.slice(0, 10));
    if (!m) return 0;
    var y = Number(m[1]);
    var mo = Number(m[2]);
    var d = Number(m[3]);
    if (y < 1970 || y > 2100 || mo < 1 || mo > 12 || d < 1 || d > 31) {
      throw new Error("无效业务日期: " + s.slice(0, 10));
    }
    var utc = Date.UTC(y, mo - 1, d, 12, 0, 0);
    var check = new Date(utc);
    if (check.getUTCFullYear() !== y || check.getUTCMonth() !== mo - 1 || check.getUTCDate() !== d) {
      throw new Error("无效业务日期: " + s.slice(0, 10));
    }
    return utc;
  }

  function linePayload(l) {
    return {
      itemId: l.itemId,
      quantity: l.qty != null ? l.qty : l.quantity,
      qty: l.qty != null ? l.qty : l.quantity,
      unitPriceCents: l.unitPriceCents != null ? l.unitPriceCents : Math.round(Number(l.price || 0) * 100),
      discountBps: l.discountBps != null ? l.discountBps : 0,
      taxBps: l.taxBps != null ? l.taxBps : 0
    };
  }

  function centsToMoney(cents) {
    return (Number(cents) || 0) / 100;
  }

  /** Format biz date from epoch-millis / numeric / ISO; 0 and missing → empty (not "0"). */
  function formatBizDate(v) {
    if (v == null || v === "") return "";
    var n = Number(v);
    if (v === 0 || v === "0" || n === 0) return "";
    /* Do not render year-only corrupt values like 2026 as a date. */
    if (isFinite(n) && n > 0 && n < 100000) return "";
    if (isFinite(n) && n > 1e11) {
      var d = new Date(n);
      if (isNaN(d.getTime())) return "";
      var y = d.getFullYear();
      var mo = d.getMonth() + 1;
      var day = d.getDate();
      return y + "-" + (mo < 10 ? "0" : "") + mo + "-" + (day < 10 ? "0" : "") + day;
    }
    var s = String(v);
    if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
    return "";
  }

  function warehouseLabel(b) {
    var code = b.warehouseCode || "";
    var name = b.warehouseName || "";
    if (code && name) return code + " · " + name;
    if (code) return code;
    if (name) return name;
    return "";
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
        if (!res.ok) {
          var httpErr = apiError(res, text);
          httpErr.retryable = httpErr.retryable || res.status >= 500 || res.status === 429;
          throw httpErr;
        }
        if (!text || !String(text).trim()) {
          if (res.status === 204) return null;
          var emptyErr = new Error("响应格式错误：空响应（HTTP " + res.status + "）");
          emptyErr.code = "EMPTY_RESPONSE";
          emptyErr.status = res.status;
          emptyErr.retryable = true;
          emptyErr.path = path;
          throw emptyErr;
        }
        try {
          return JSON.parse(text);
        } catch (e) {
          /* Never return the raw string — callers must not treat garbage as data. */
          var parseErr = new Error("响应格式错误：不是合法 JSON");
          parseErr.code = "INVALID_JSON";
          parseErr.status = res.status;
          parseErr.retryable = true;
          parseErr.path = path;
          parseErr.preview = String(text).slice(0, 180);
          throw parseErr;
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

  RpcDataProvider.prototype._hasCache = function (collection) {
    return Object.prototype.hasOwnProperty.call(this._cache, collection);
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
    /* Server money fields are integer minor units (cents); never treat raw total as major units. */
    var total = centsToMoney(
      d.totalCents != null ? d.totalCents : (d.totalAmount != null ? d.totalAmount : (d.total != null ? d.total : 0))
    );
    var lines = (d.lines || []).map(function (l, i) {
      var qty = l.qty != null ? l.qty : (l.quantity != null ? l.quantity : 0);
      var rcv = l.receivedQty != null ? l.receivedQty : (l.fulfilledQty != null ? l.fulfilledQty : 0);
      return {
        id: l.id || ("L" + i),
        itemId: l.itemId,
        sku: l.sku || l.itemSku || "",
        name: l.name || l.itemName || "",
        uom: l.uom || "EA",
        qty: qty,
        quantity: qty,
        receivedQty: rcv,
        fulfilledQty: rcv,
        openQty: l.openQty != null ? l.openQty : Math.max(0, qty - rcv),
        unitPriceCents: l.unitPriceCents != null ? l.unitPriceCents : 0,
        lineAmountCents: l.lineAmountCents != null ? l.lineAmountCents : ((l.unitPriceCents || 0) * qty),
        discountBps: l.discountBps != null ? l.discountBps : 0,
        taxBps: l.taxBps != null ? l.taxBps : 0
      };
    });
    var rawDate = (d.orderDate != null && d.orderDate !== 0 && d.orderDate !== "0")
      ? d.orderDate
      : ((d.date != null && d.date !== 0 && d.date !== "0")
        ? d.date
        : (d.invoiceDate != null ? d.invoiceDate : d.createdAt));
    var row = {
      id: d.id,
      docNo: d.docNo || d.invoiceNumber || d.code || "",
      date: formatBizDate(rawDate),
      orderDate: rawDate || 0,
      dueDate: formatBizDate(d.dueDate),
      expectedDate: formatBizDate(d.expectedDate),
      warehouseId: d.warehouseId || "",
      currency: d.currency || "CNY",
      notes: d.notes || "",
      status: d.status || "",
      approvalStatus: d.approvalStatus || "",
      fulfillmentStatus: d.fulfillmentStatus || "",
      total: total,
      totalCents: d.totalCents != null ? d.totalCents : Math.round(Number(total) * 100),
      openAmount: d.openAmount != null ? centsToMoney(d.openAmount) : "",
      qty: d.qty != null ? d.qty : d.quantity,
      contactName: d.contactName || "",
      createdAt: d.createdAt || 0,
      lines: lines
    };
    if (partnerField === "customerName") {
      row.customerName = d.customerName || d.partnerName || "";
      row.customerId = d.customerId;
      row.partnerId = d.customerId || d.partnerId;
      row.partnerName = row.customerName;
    } else if (partnerField === "supplierName") {
      row.supplierName = d.supplierName || d.partnerName || "";
      row.supplierId = d.supplierId;
      row.partnerId = d.supplierId || d.partnerId;
      row.partnerName = row.supplierName;
      row.requester = d.requester || d.requesterName || "";
    }
    return row;
  }

  function poPayload(record) {
    var key = record.idempotencyKey || ("po-ui-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8));
    return {
      supplierId: record.supplierId || record.partnerId,
      partnerId: record.partnerId || record.supplierId,
      warehouseId: record.warehouseId || 0,
      orderDate: dateToMillis(record.orderDate || record.date),
      expectedDate: dateToMillis(record.expectedDate),
      notes: record.notes || "",
      currency: record.currency || "CNY",
      contactName: record.contactName || "",
      idempotencyKey: key,
      lines: (record.lines || []).map(linePayload)
    };
  }

  function soPayload(record) {
    var key = record.idempotencyKey || ("so-ui-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8));
    if (!record._idempotencyKey) record._idempotencyKey = key;
    return {
      customerId: record.customerId || record.partnerId,
      partnerId: record.partnerId || record.customerId,
      warehouseId: record.warehouseId || 0,
      orderDate: dateToMillis(record.orderDate || record.date),
      deliveryDate: dateToMillis(record.deliveryDate),
      notes: record.notes || "",
      currency: record.currency || "CNY",
      contactName: record.contactName || "",
      idempotencyKey: record._idempotencyKey,
      lines: (record.lines || []).map(linePayload)
    };
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
          var wh = warehouseLabel(b);
          var sku = b.itemSku || "";
          var name = b.itemName || "";
          var costState = b.costState || "";
          if (!costState && b.quantityOnHand > 0 && !(b.valueAmount || b.value)) {
            costState = "PENDING_COST";
          }
          var valueDisp = "";
          if (costState === "PENDING_COST") {
            valueDisp = "(pending cost)";
          } else if (b.value != null || b.valueAmount != null) {
            valueDisp = centsToMoney(b.value != null ? b.value : b.valueAmount);
          }
          return {
            id: String(b.warehouseId) + "-" + String(b.itemId),
            warehouse: wh || ("#" + String(b.warehouseId || "")),
            warehouseCode: b.warehouseCode || "",
            sku: sku || ("#" + String(b.itemId || "")),
            name: name || (sku ? "" : "(no item name)"),
            qty: b.quantityOnHand,
            onHand: b.quantityOnHand,
            reserved: b.reservedSupported ? b.quantityReserved : "(n/a)",
            available: b.quantityAvailable != null ? b.quantityAvailable : b.quantityOnHand,
            value: valueDisp,
            costState: costState || "OK",
            minQty: b.minQty != null ? b.minQty : 0
          };
        });
      }
    },
    inventoryAlerts: {
      path: "/api/erp/inventory/alerts",
      map: function (arr) {
        return (arr || []).map(function (a) {
          return {
            id: a.id,
            sku: a.sku || "",
            message: a.message || "",
            severity: a.severity || "WARN",
            warehouse: a.warehouse || "",
            qty: a.qty
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
            date: formatBizDate(t.createdAt || t.date),
            type: t.txnType || t.type || "",
            sku: t.itemSku || t.itemId || "",
            qty: t.quantity,
            ref: t.refDocNo || t.reference || "",
            warehouse: t.warehouseCode || t.warehouseId || ""
          };
        });
      }
    },
    inventoryCosting: {
      path: "/api/erp/inventory/costing",
      map: function (arr) {
        return (arr || []).map(function (b) {
          return {
            id: String(b.warehouseId) + "-" + String(b.itemId),
            warehouseCode: b.warehouseCode || "",
            itemSku: b.itemSku || "",
            quantityOnHand: b.quantityOnHand,
            valueAmount: centsToMoney(b.valueAmount),
            avgUnitCost: centsToMoney(b.avgUnitCost),
            costState: b.costState || "OK"
          };
        });
      }
    },
    costEvents: {
      path: "/api/erp/inventory/cost-events",
      map: function (arr) {
        return (arr || []).map(function (e) {
          return {
            id: e.id,
            eventType: e.eventType || "",
            quantityDelta: e.quantityDelta,
            valueDeltaAmount: centsToMoney(e.valueDeltaAmount),
            avgCostAfter: centsToMoney(e.avgCostAfter),
            sourceType: e.sourceType || "",
            sourceId: e.sourceId
          };
        });
      }
    },
    businessMargin: {
      path: "/api/erp/inventory/margin",
      map: function (arr) {
        return (arr || []).map(function (m) {
          return {
            id: m.deliveryLineId,
            salesOrderDocNo: m.salesOrderDocNo || "",
            deliveryDocNo: m.deliveryDocNo || "",
            customerName: m.customerName || "",
            itemSku: m.itemSku || "",
            salesNetAmount: m.costState === "PENDING_COST" ? "(待核算)" : centsToMoney(m.salesNetAmount),
            cogsNetAmount: m.costState === "PENDING_COST" ? "(待核算)" : centsToMoney(m.cogsNetAmount),
            grossMarginAmount: m.costState === "PENDING_COST" ? "(待核算)" : centsToMoney(m.grossMarginAmount),
            costState: m.costState || "OK"
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
          row.adjustedAmount = centsToMoney(d.adjustedAmount || 0);
          row.paidAmount = centsToMoney(d.paidAmount || 0);
          row.openAmount = centsToMoney(d.openAmount);
          row.settlementStatus = d.settlementStatus || "";
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
          row.adjustedAmount = centsToMoney(d.adjustedAmount || 0);
          row.paidAmount = centsToMoney(d.paidAmount || 0);
          row.openAmount = centsToMoney(d.openAmount);
          row.settlementStatus = d.settlementStatus || "";
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
            date: formatBizDate(d.paymentDate || d.date),
            amount: centsToMoney(d.amount),
            unallocated: centsToMoney(d.unallocatedAmount != null ? d.unallocatedAmount : d.unallocated || 0),
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
            date: formatBizDate(d.paymentDate || d.date),
            amount: centsToMoney(d.amount),
            unallocated: centsToMoney(d.unallocatedAmount != null ? d.unallocatedAmount : d.unallocated || 0),
            status: d.status || "POSTED"
          };
        });
      }
    },
    cashMovements: {
      path: "/api/erp/finance/cash-movements",
      map: function (arr) {
        return (arr || []).map(function (d) {
          return {
            id: d.id,
            movementType: d.movementType || "",
            amount: centsToMoney(d.amount),
            balanceAfter: centsToMoney(d.balanceAfter),
            sourceType: d.sourceType || "",
            sourceId: d.sourceId
          };
        });
      }
    },
    arAgeing: {
      path: "/api/erp/finance/arAgeing",
      map: function (arr) {
        return (arr || []).map(function (b) {
          return {
            partnerName: b.partnerName || "",
            currentAmount: centsToMoney(b.currentAmount || 0),
            d1to30: centsToMoney(b.d1to30 || 0),
            d31to60: centsToMoney(b.d31to60 || 0),
            d61to90: centsToMoney(b.d61to90 || 0),
            d90plus: centsToMoney(b.d90plus || 0),
            totalOpen: centsToMoney(b.totalOpen || 0)
          };
        });
      }
    },
    apAgeing: {
      path: "/api/erp/finance/apAgeing",
      map: function (arr) {
        return (arr || []).map(function (b) {
          return {
            partnerName: b.partnerName || "",
            currentAmount: centsToMoney(b.currentAmount || 0),
            d1to30: centsToMoney(b.d1to30 || 0),
            d31to60: centsToMoney(b.d31to60 || 0),
            d61to90: centsToMoney(b.d61to90 || 0),
            d90plus: centsToMoney(b.d90plus || 0),
            totalOpen: centsToMoney(b.totalOpen || 0)
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
            date: formatBizDate(j.postingDate),
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
            date: formatBizDate(g.postingDate || g.date),
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
            debit: centsToMoney(t.closingDebit != null ? t.closingDebit : (t.debitAmount || t.debit || 0)),
            credit: centsToMoney(t.closingCredit != null ? t.closingCredit : (t.creditAmount || t.credit || 0))
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
    glEvents: {
      path: "/api/erp/finance/gl-events",
      map: function (arr) {
        return (arr || []).map(function (e) {
          return {
            id: e.id,
            eventType: e.eventType || "",
            sourceType: e.sourceType || "",
            sourceId: e.sourceId || 0,
            amount: centsToMoney(e.amount || 0),
            status: e.status || "",
            journalEntryId: e.journalEntryId || 0,
            errorMessage: e.errorMessage || ""
          };
        });
      }
    },
    glRecon: {
      path: "/api/erp/finance/gl-recon",
      map: function (arr) {
        return (arr || []).map(function (c, idx) {
          return {
            id: idx,
            checkName: c.checkName || "",
            ok: c.ok ? "OK" : "DIFF",
            expectedAmount: centsToMoney(c.expectedAmount || 0),
            actualAmount: centsToMoney(c.actualAmount || 0),
            diffAmount: centsToMoney(c.diffAmount || 0),
            detail: c.detail || ""
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

  RpcDataProvider.prototype._fetchCollection = function (collection, opts) {
    opts = opts || {};
    var self = this;
    var conf = COLLECTION_API[collection];
    if (!conf) {
      return Promise.reject(new Error("集合不可用: " + collection));
    }
    if (opts.forceRefresh) {
      delete this._cache[collection];
    } else if (this._hasCache(collection)) {
      /* Note: [] is a valid cached empty result (hasOwnProperty). Failures never write cache. */
      return Promise.resolve(this._cache[collection]);
    }
    return this._fetch(conf.path).then(function (data) {
      if (!Array.isArray(data)) {
        var shapeErr = new Error("集合响应格式错误（期望数组）: " + collection);
        shapeErr.code = "INVALID_COLLECTION";
        shapeErr.retryable = true;
        shapeErr.path = conf.path;
        /* Do not cache — next list()/forceRefresh must hit the network again. */
        throw shapeErr;
      }
      var rows = conf.map(data);
      if (!Array.isArray(rows)) {
        var mapErr = new Error("集合映射结果不是数组: " + collection);
        mapErr.code = "INVALID_COLLECTION";
        mapErr.retryable = true;
        throw mapErr;
      }
      /* Cache only successful, structurally valid data (including real []). */
      self._cache[collection] = rows;
      return rows;
    }, function (err) {
      /* Ensure failed fetches leave no cache entry. */
      delete self._cache[collection];
      throw err;
    });
  };

  RpcDataProvider.prototype.list = function (collection, query) {
    query = query || {};
    var force = !!(query.forceRefresh || query.bypassCache || query.refresh);
    return this._fetchCollection(collection, { forceRefresh: force }).then(function (rows) {
      return pageFilter(rows, query);
    });
  };

  RpcDataProvider.prototype.get = function (collection, id) {
    var conf = COLLECTION_API[collection];
    if (collection === "items") {
      return this._fetch("/api/erp/items/" + encodeURIComponent(id)).then(mapItem);
    }
    if (conf && (collection === "purchaseRequests" || collection === "purchaseOrders" ||
        collection === "salesOrders" || collection === "goodsReceipts" || collection === "deliveries")) {
      return this._fetch(conf.path + "/" + encodeURIComponent(id)).then(function (d) {
        if (collection === "purchaseRequests" || collection === "purchaseOrders") {
          return mapDoc(d, "supplierName");
        }
        if (collection === "salesOrders") {
          return mapDoc(d, "customerName");
        }
        if (collection === "deliveries") {
          var dl = mapDoc(d, "customerName");
          dl.salesOrderId = d.salesOrderId;
          dl.sourceDocNo = d.sourceDocNo || d.soDocNo || "";
          dl.soDocNo = d.soDocNo || dl.sourceDocNo;
          dl.qty = d.qty != null ? d.qty : d.totalQty;
          dl.totalQty = d.totalQty != null ? d.totalQty : dl.qty;
          return dl;
        }
        if (collection === "goodsReceipts") {
          var gr = mapDoc(d, "supplierName");
          gr.purchaseOrderId = d.purchaseOrderId;
          gr.sourceDocNo = d.sourceDocNo || d.poDocNo || "";
          gr.poDocNo = d.poDocNo || gr.sourceDocNo;
          gr.qty = d.qty != null ? d.qty : d.totalQty;
          gr.totalQty = d.totalQty != null ? d.totalQty : gr.qty;
          return gr;
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
      var cents = record.unitPriceCents;
      if (cents == null && record.price != null) {
        cents = Math.round(Number(record.price) * 100);
      }
      if (cents == null || isNaN(cents)) cents = 0;
      var isUpdate = record.id != null && String(record.id) !== "" && String(record.id).indexOf("NEW") < 0;
      if (isUpdate) {
        return this._fetch("/api/erp/items/" + encodeURIComponent(record.id), {
          method: "POST",
          body: JSON.stringify({
            name: record.name,
            unitPriceCents: cents,
            price: record.price
          })
        }).then(function (updated) {
          self.invalidate("items");
          return mapItem(updated);
        });
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
    if (collection === "purchaseOrders") {
      var isUpdate = record.id != null && String(record.id) !== "" && String(record.id).indexOf("NEW") < 0;
      var path = isUpdate
        ? "/api/erp/purchaseOrders/" + encodeURIComponent(record.id)
        : "/api/erp/purchaseOrders";
      var poBody = poPayload(record);
      var poHeaders = {};
      if (!isUpdate && poBody.idempotencyKey) {
        poHeaders["Idempotency-Key"] = poBody.idempotencyKey;
        if (!record._idempotencyKey) record._idempotencyKey = poBody.idempotencyKey;
        else poBody.idempotencyKey = record._idempotencyKey;
        poHeaders["Idempotency-Key"] = record._idempotencyKey || poBody.idempotencyKey;
      }
      return this._fetch(path, {
        method: "POST",
        headers: poHeaders,
        body: JSON.stringify(poBody)
      }).then(function (saved) {
        self.invalidate("purchaseOrders");
        return mapDoc(saved, "supplierName");
      });
    }
    if (collection === "salesOrders") {
      var soUpdate = record.id != null && String(record.id) !== "" && String(record.id).indexOf("NEW") < 0;
      var soPath = soUpdate
        ? "/api/erp/salesOrders/" + encodeURIComponent(record.id)
        : "/api/erp/salesOrders";
      var soBody = soPayload(record);
      var soHeaders = {};
      if (!soUpdate) {
        soHeaders["Idempotency-Key"] = soBody.idempotencyKey;
      }
      return this._fetch(soPath, {
        method: "POST",
        headers: soHeaders,
        body: JSON.stringify(soBody)
      }).then(function (saved) {
        self.invalidate("salesOrders");
        self.invalidate("inventoryBalances");
        return mapDoc(saved, "customerName");
      });
    }
    if (collection === "deliveries") {
      var dlKey = record.idempotencyKey || ("dl-ui-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8));
      return this._fetch("/api/erp/deliveries", {
        method: "POST",
        headers: { "Idempotency-Key": dlKey },
        body: JSON.stringify({
          salesOrderId: record.salesOrderId || record.sourceSoId,
          warehouseId: record.warehouseId,
          idempotencyKey: dlKey,
          lines: (record.lines || []).map(function (l) {
            return {
              orderLineId: l.orderLineId || l.soLineId || l.id,
              quantity: l.qty != null ? l.qty : l.quantity
            };
          })
        })
      }).then(function (res) {
        self.invalidate("deliveries");
        self.invalidate("salesOrders");
        self.invalidate("inventoryBalances");
        self.invalidate("inventoryTransactions");
        self.invalidate("arInvoices");
        if (res && res.ok === 0) throw new Error(res.message || res.code || "DL post failed");
        var doc = (res && res.document) || res || {};
        return { id: doc.id, docNo: doc.docNo || "", status: "POSTED", provisionalArId: doc.provisionalArId, lines: record.lines || [] };
      });
    }
    if (collection === "goodsReceipts") {
      var key = record.idempotencyKey || ("gr-ui-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8));
      var grBody = {
        purchaseOrderId: record.purchaseOrderId || record.sourcePoId,
        warehouseId: record.warehouseId,
        idempotencyKey: key,
        lines: (record.lines || []).map(function (l) {
          return {
            orderLineId: l.orderLineId || l.poLineId || l.id,
            quantity: l.qty != null ? l.qty : l.quantity
          };
        })
      };
      return this._fetch("/api/erp/goodsReceipts", {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body: JSON.stringify(grBody)
      }).then(function (res) {
        self.invalidate("goodsReceipts");
        self.invalidate("purchaseOrders");
        self.invalidate("inventoryBalances");
        self.invalidate("inventoryTransactions");
        self.invalidate("apInvoices");
        if (res && res.ok === 0) {
          throw new Error(res.message || res.code || "GR post failed");
        }
        var doc = (res && res.document) || res || {};
        return {
          id: doc.id,
          docNo: doc.docNo || "",
          status: "POSTED",
          provisionalApId: doc.provisionalApId,
          lines: record.lines || []
        };
      });
    }
    return Promise.reject(new Error("Save not available for " + collection));
  };

  RpcDataProvider.prototype.submitPurchaseOrder = function (id) {
    var self = this;
    return this._fetch("/api/erp/purchaseOrders/" + encodeURIComponent(id) + "/submit", {
      method: "POST",
      body: "{}"
    }).then(function (po) {
      self.invalidate("purchaseOrders");
      return mapDoc(po, "supplierName");
    });
  };

  RpcDataProvider.prototype.approvePurchaseOrder = function (id) {
    var self = this;
    return this._fetch("/api/erp/purchaseOrders/" + encodeURIComponent(id) + "/approve", {
      method: "POST",
      body: "{}"
    }).then(function (po) {
      self.invalidate("purchaseOrders");
      return mapDoc(po, "supplierName");
    });
  };

  RpcDataProvider.prototype.closePurchaseOrder = function (id) {
    var self = this;
    return this._fetch("/api/erp/purchaseOrders/" + encodeURIComponent(id) + "/close", {
      method: "POST",
      body: "{}"
    }).then(function (po) {
      self.invalidate("purchaseOrders");
      return mapDoc(po, "supplierName");
    });
  };

  RpcDataProvider.prototype.submitSalesOrder = function (id) {
    var self = this;
    return this._fetch("/api/erp/salesOrders/" + encodeURIComponent(id) + "/submit", {
      method: "POST",
      body: "{}"
    }).then(function (so) {
      self.invalidate("salesOrders");
      self.invalidate("inventoryBalances");
      return mapDoc(so, "customerName");
    });
  };

  RpcDataProvider.prototype.approveSalesOrder = function (id) {
    var self = this;
    return this._fetch("/api/erp/salesOrders/" + encodeURIComponent(id) + "/approve", {
      method: "POST",
      body: "{}"
    }).then(function (so) {
      self.invalidate("salesOrders");
      self.invalidate("inventoryBalances");
      return mapDoc(so, "customerName");
    });
  };

  RpcDataProvider.prototype.remove = function () {
    return Promise.reject(new Error("Delete not available"));
  };

  RpcDataProvider.prototype.dashboard = function (kind) {
    kind = kind || "executive";
    return this._fetch("/api/erp/dashboard/summary?kind=" + encodeURIComponent(kind)).then(function (d) {
      var cur = d.currency || "CNY";
      var period = formatBizDate(d.periodStart) + " ~ " + formatBizDate(d.periodEnd);
      if (!d.periodStart) period = "as-of " + formatBizDate(d.asOf);
      var kpis = [];
      var drill = d.drill || {};
      function add(k, v, desc, money) {
        if (v === -1 || v === "-1") {
          kpis.push({ k: k, v: "—", d: desc + " (unavailable)", money: 0, go: drill[k] || "" });
          return;
        }
        if (v == null) {
          kpis.push({ k: k, v: "—", d: desc, money: 0, go: drill[k] || "" });
          return;
        }
        kpis.push({ k: k, v: money ? centsToMoney(v) : v, d: desc, money: money ? 1 : 0, go: drill[k] || "" });
      }
      if (kind === "sales") {
        add("periodOrderAmount", d.periodOrderAmount, cur + " period SO amount", 1);
        add("openDeliveryQty", d.openDeliveryQty, "open delivery qty", 0);
        add("openDeliveryAmount", d.openDeliveryAmount, cur + " open delivery amount", 1);
        add("overdueDeliveryCount", d.overdueDeliveryCount, "overdue deliveries", 0);
      } else if (kind === "purchasing") {
        add("periodOrderAmount", d.periodOrderAmount, cur + " period PO amount", 1);
        add("openReceiptQty", d.openReceiptQty, "open receipt qty", 0);
        add("openReceiptAmount", d.openReceiptAmount, cur + " open receipt amount", 1);
        add("overdueReceiptCount", d.overdueReceiptCount, "overdue receipts", 0);
      } else if (kind === "inventory") {
        add("onHandQty", d.onHandQty, "on-hand qty", 0);
        add("valuedAmount", d.valuedAmount, cur + " costed stock value", 1);
        add("zeroOrNegStockCount", d.zeroOrNegStockCount, "zero/neg stock rows", 0);
        add("pendingCostCount", d.pendingCostCount, "qty>0 value=0", 0);
      } else if (kind === "finance") {
        add("arOpen", d.arOpenAmount, cur + " open AR", 1);
        add("apOpen", d.apOpenAmount, cur + " open AP", 1);
        add("periodReceipts", d.periodReceipts, cur + " posted receipts", 1);
        add("periodPayments", d.periodPayments, cur + " posted payments", 1);
        add("pendingGlEvents", d.pendingGlEvents, "PENDING/FAILED events", 0);
      } else {
        add("draftSoCount", d.draftSoCount, "draft/submitted SO", 0);
        add("draftPoCount", d.draftPoCount, "draft/submitted PO", 0);
        add("arOpen", d.arOpenAmount, cur + " open AR", 1);
        add("apOpen", d.apOpenAmount, cur + " open AP", 1);
        add("pendingGlEvents", d.pendingGlEvents, "PENDING/FAILED GL events", 0);
      }
      var qa = {
        sales: ["sales.orders", "sales.deliveries", "sales.ar"],
        purchasing: ["pur.orders", "pur.gr", "pur.ap"],
        inventory: ["inv.balance", "inv.valuation", "inv.txns"],
        finance: ["fin.ar", "fin.ap", "fin.glEvents", "fin.glRecon"],
        executive: ["sales.orders", "pur.orders", "fin.ar", "fin.glEvents"]
      };
      return {
        kind: kind,
        currency: cur,
        periodLabel: period,
        basis: d.basis || "",
        kpis: kpis,
        trend: { label: "", points: [] },
        alerts: [],
        todos: [],
        recent: [],
        quickActions: qa[kind] || qa.executive
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
