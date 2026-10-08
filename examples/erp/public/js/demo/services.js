/**
 * ApplicationService - all DEMO business mutations go through here.
 * Pages must not mutate other modules' collections directly.
 */
(function (global) {
  function clone(v) { return JSON.parse(JSON.stringify(v)); }
  function money(n) { return JnDemoState.money(n); }
  function today() {
    var d = new Date();
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return d.getFullYear() + "-" + (m < 10 ? "0" : "") + m + "-" + (day < 10 ? "0" : "") + day;
  }
  function nid(prefix) { return prefix + Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }

  function itemBySku(sku) {
    var rows = JnDemoState.collection("items");
    for (var i = 0; i < rows.length; i++) if (rows[i].sku === sku) return rows[i];
    return null;
  }

  function findBalance(warehouse, sku) {
    var rows = JnDemoState.collection("inventoryBalances");
    for (var i = 0; i < rows.length; i++) {
      if (rows[i].warehouse === warehouse && rows[i].sku === sku) return rows[i];
    }
    return null;
  }

  function applyInventoryDelta(opts) {
    var warehouse = opts.warehouse || "WH-MAIN";
    var sku = opts.sku;
    var qtyDelta = Number(opts.qtyDelta) || 0;
    var unitCost = opts.unitCost != null ? Number(opts.unitCost) : null;
    var name = opts.name || sku;
    var ref = opts.ref || "";
    var type = opts.type || "ADJUST";
    var it = itemBySku(sku);
    if (unitCost == null && it) unitCost = Number(it.cost) || 0;
    if (unitCost == null) unitCost = 0;

    var bal = findBalance(warehouse, sku);
    if (!bal) {
      bal = {
        id: nid("IB"),
        warehouse: warehouse,
        sku: sku,
        name: name || (it && it.name) || sku,
        qty: 0,
        unitCost: unitCost,
        value: 0
      };
      JnDemoState.collection("inventoryBalances").unshift(bal);
    }
    var newQty = money(Number(bal.qty) + qtyDelta);
    if (newQty < -0.0001) {
      throw new Error("Insufficient stock for " + sku + " at " + warehouse + " (have " + bal.qty + ", need " + (-qtyDelta) + ")");
    }
    /* moving average on receipt */
    if (qtyDelta > 0) {
      var oldVal = Number(bal.value) || 0;
      var addVal = money(qtyDelta * unitCost);
      bal.qty = newQty;
      bal.value = money(oldVal + addVal);
      bal.unitCost = bal.qty > 0 ? money(bal.value / bal.qty) : unitCost;
    } else {
      var costUsed = Number(bal.unitCost) || unitCost;
      bal.qty = newQty;
      bal.value = money(bal.qty * costUsed);
      unitCost = costUsed;
    }
    if (it) it.onHand = (Number(it.onHand) || 0) + qtyDelta;

    var txn = {
      id: nid("IT"),
      date: today(),
      type: type,
      sku: sku,
      qty: qtyDelta,
      unitCost: unitCost,
      value: money(Math.abs(qtyDelta) * unitCost) * (qtyDelta < 0 ? -1 : 1),
      ref: ref,
      warehouse: warehouse,
      sourceDocType: opts.sourceDocType || "",
      sourceDocId: opts.sourceDocId || "",
      demo: true
    };
    JnDemoState.collection("inventoryTransactions").unshift(txn);
    syncInventoryAccount();
    JnDemoState.persist();
    return { balance: clone(bal), txn: clone(txn), unitCost: unitCost };
  }

  function syncInventoryAccount() {
    var val = JnDemoState.computeKpis().inventoryValue;
    JnDemoState.collection("accounts").forEach(function (a) {
      if (a.code === "1403") a.balance = val;
    });
    JnDemoSeed.refreshTrialBalance(JnDemoState.getState());
  }

  function adjustAccount(code, delta) {
    JnDemoState.collection("accounts").forEach(function (a) {
      if (a.code === code) a.balance = money(Number(a.balance) + delta);
    });
  }

  function postJournal(opts) {
    var lines = opts.lines || [];
    var debit = 0, credit = 0;
    lines.forEach(function (l) {
      debit += Number(l.debit) || 0;
      credit += Number(l.credit) || 0;
    });
    debit = money(debit);
    credit = money(credit);
    if (debit !== credit) {
      throw new Error("Journal not balanced: debit=" + debit + " credit=" + credit);
    }
    var docNo = opts.docNo || JnDemoState.nextNumber("JE");
    var je = {
      id: nid("JE"),
      docNo: docNo,
      date: opts.date || today(),
      source: opts.source || "DEMO",
      description: opts.description || "",
      debit: debit,
      credit: credit,
      status: "POSTED",
      lines: lines,
      demo: true
    };
    JnDemoState.collection("journalEntries").unshift(je);
    lines.forEach(function (l) {
      var d = Number(l.debit) || 0;
      var c = Number(l.credit) || 0;
      /* Asset/Expense increase on debit; Liability/Income increase on credit */
      var acc = null;
      JnDemoState.collection("accounts").forEach(function (a) {
        if (a.code === l.account) acc = a;
      });
      if (!acc) return;
      if (acc.type === "Asset" || acc.type === "Expense") acc.balance = money(Number(acc.balance) + d - c);
      else acc.balance = money(Number(acc.balance) + c - d);
      JnDemoState.collection("glLines").unshift({
        id: nid("GL"),
        date: je.date,
        account: l.account + " " + (acc.name || ""),
        debit: d,
        credit: c,
        journal: je.docNo
      });
    });
    /* keep inventory account synced from balances as source of truth */
    syncInventoryAccount();
    JnDemoSeed.refreshTrialBalance(JnDemoState.getState());
    JnDemoState.persist();
    JnDemoEvents.emit("JOURNAL_POSTED", { docNo: je.docNo, debit: debit, credit: credit });
    return clone(je);
  }

  function lineTotals(lines) {
    var total = 0;
    (lines || []).forEach(function (l) {
      var qty = Number(l.qty) || 0;
      var price = Number(l.price != null ? l.price : (l.unitPriceCents != null ? l.unitPriceCents / 100 : 0)) || 0;
      l.price = price;
      l.amount = money(qty * price);
      total += l.amount;
    });
    return money(total);
  }

  /* ---------- Master ---------- */
  function createCustomer(data) {
    var rec = Object.assign({
      id: nid("C"),
      code: data.code || ("CUS-" + Date.now().toString().slice(-4)),
      status: "ACTIVE",
      country: "CN",
      creditLimit: 100000
    }, data);
    JnDemoState.upsert("customers", rec);
    JnDemoEvents.emit("CUSTOMER_CREATED", { id: rec.id, name: rec.name });
    return clone(rec);
  }

  function createSupplier(data) {
    var rec = Object.assign({
      id: nid("S"),
      code: data.code || ("SUP-" + Date.now().toString().slice(-4)),
      status: "ACTIVE",
      paymentTerms: "Net 30"
    }, data);
    JnDemoState.upsert("suppliers", rec);
    JnDemoEvents.emit("SUPPLIER_CREATED", { id: rec.id, name: rec.name });
    return clone(rec);
  }

  function createItem(data) {
    var rec = Object.assign({
      id: nid("I"),
      status: "ACTIVE",
      uom: "EA",
      onHand: 0,
      warehouse: "WH-MAIN",
      cost: 0,
      price: 0
    }, data);
    JnDemoState.upsert("items", rec);
    JnDemoEvents.emit("ITEM_CREATED", { id: rec.id, sku: rec.sku });
    return clone(rec);
  }

  function upsertMaster(collection, record) {
    if (!record.id) {
      if (collection === "customers") return createCustomer(record);
      if (collection === "suppliers") return createSupplier(record);
      if (collection === "items") return createItem(record);
      record.id = nid("M");
    }
    return JnDemoState.upsert(collection, record);
  }

  /* ---------- Purchasing ---------- */
  function savePurchaseOrder(rec) {
    if (!rec.id) rec.id = nid("PO");
    if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("PO");
    if (!rec.status) rec.status = "DRAFT";
    if (!rec.date) rec.date = today();
    rec.total = lineTotals(rec.lines);
    var created = !JnDemoState.find("purchaseOrders", rec.id);
    JnDemoState.upsert("purchaseOrders", rec);
    if (created) JnDemoEvents.emit("PURCHASE_ORDER_CREATED", { id: rec.id, docNo: rec.docNo });
    return clone(rec);
  }

  function submitDocument(collection, id) {
    var rec = JnDemoState.find(collection, id);
    if (!rec) throw new Error("Not found");
    rec.status = "SUBMITTED";
    JnDemoState.upsert(collection, rec);
    return clone(rec);
  }

  function approveDocument(collection, id) {
    var rec = JnDemoState.find(collection, id);
    if (!rec) throw new Error("Not found");
    rec.status = "APPROVED";
    JnDemoState.upsert(collection, rec);
    return clone(rec);
  }

  function postGoodsReceipt(input) {
    var po = null;
    var lines = input.lines || [];
    var warehouse = input.warehouseCode || input.warehouseId || "WH-MAIN";
    if (input.purchaseOrderId) po = JnDemoState.find("purchaseOrders", input.purchaseOrderId);
    if (po && (!lines || !lines.length)) lines = (po.lines || []).map(function (l) {
      return { sku: l.sku, name: l.name, qty: l.qty, price: l.price, itemId: l.itemId };
    });
    if (typeof warehouse === "string" && warehouse.indexOf("W") === 0 && warehouse.length <= 3) {
      var wh = JnDemoState.find("warehouses", warehouse);
      if (wh) warehouse = wh.code;
    }
    /* resolve warehouse id to code */
    var whRow = JnDemoState.collection("warehouses").filter(function (w) {
      return w.id === warehouse || w.code === warehouse;
    })[0];
    if (whRow) warehouse = whRow.code;

    var gr = {
      id: input.id || nid("GR"),
      docNo: input.docNo || JnDemoState.nextNumber("GR"),
      purchaseOrderId: po ? po.id : input.purchaseOrderId || "",
      supplierId: (po && po.supplierId) || input.supplierId || "",
      supplierName: (po && po.supplierName) || input.supplierName || "",
      sourceDocNo: po ? po.docNo : input.sourceDocNo || "",
      date: input.date || today(),
      status: "POSTED",
      warehouseId: whRow ? whRow.id : "W1",
      warehouseCode: warehouse,
      qty: 0,
      lines: [],
      references: { source: po ? po.docNo : "", targets: [] },
      demo: true
    };
    var totalValue = 0;
    lines.forEach(function (l) {
      var qty = Number(l.qty) || 0;
      var price = Number(l.price) || 0;
      var it = itemBySku(l.sku);
      var cost = price || (it && it.cost) || 0;
      var r = applyInventoryDelta({
        warehouse: warehouse,
        sku: l.sku,
        name: l.name || (it && it.name),
        qtyDelta: qty,
        unitCost: cost,
        ref: gr.docNo,
        type: "GOODS_RECEIPT",
        sourceDocType: "GoodsReceipt",
        sourceDocId: gr.id
      });
      gr.lines.push({
        id: nid("GRL"), itemId: l.itemId || (it && it.id), sku: l.sku, name: l.name || (it && it.name),
        qty: qty, price: cost, amount: money(qty * cost), uom: l.uom || "EA"
      });
      gr.qty += qty;
      totalValue = money(totalValue + qty * cost);
    });
    JnDemoState.upsert("goodsReceipts", gr);
    if (po) {
      po.status = "PARTIAL";
      if (!po.references) po.references = { source: "", targets: [] };
      po.references.targets = (po.references.targets || []).concat([gr.docNo]);
      JnDemoState.upsert("purchaseOrders", po);
    }
    /* Dr Inventory / Cr GR-IR */
    postJournal({
      source: gr.docNo,
      description: "DEMO GR post " + gr.docNo + " (no tax)",
      lines: [
        { account: "1403", debit: totalValue, credit: 0 },
        { account: "1405", debit: 0, credit: totalValue }
      ]
    });
    /* inventory account re-synced from balances inside postJournal/sync */
    JnDemoEvents.emit("GOODS_RECEIPT_POSTED", { id: gr.id, docNo: gr.docNo, qty: gr.qty, value: totalValue });
    return clone(gr);
  }

  function createAPInvoiceFromGR(grId) {
    var gr = JnDemoState.find("goodsReceipts", grId);
    if (!gr) throw new Error("GR not found");
    var total = 0;
    (gr.lines || []).forEach(function (l) { total += Number(l.amount) || 0; });
    total = money(total);
    var ap = {
      id: nid("AP"),
      docNo: JnDemoState.nextNumber("AP"),
      supplierId: gr.supplierId,
      supplierName: gr.supplierName,
      date: today(),
      dueDate: today(),
      status: "OPEN",
      currency: "CNY",
      total: total,
      openAmount: total,
      lines: (gr.lines || []).map(function (l) {
        return { id: nid("APL"), itemId: l.itemId, sku: l.sku, name: l.name, qty: l.qty, price: l.price, amount: l.amount };
      }),
      references: { source: gr.docNo, targets: [] },
      demo: true
    };
    JnDemoState.upsert("apInvoices", ap);
    gr.references = gr.references || { source: "", targets: [] };
    gr.references.targets = (gr.references.targets || []).concat([ap.docNo]);
    JnDemoState.upsert("goodsReceipts", gr);
    postJournal({
      source: ap.docNo,
      description: "DEMO AP invoice (no tax)",
      lines: [
        { account: "1405", debit: total, credit: 0 },
        { account: "2202", debit: 0, credit: total }
      ]
    });
    JnDemoEvents.emit("AP_INVOICE_CREATED", { id: ap.id, docNo: ap.docNo, total: total });
    return clone(ap);
  }

  function recordOutgoingPayment(apId, amount) {
    var ap = JnDemoState.find("apInvoices", apId);
    if (!ap) throw new Error("AP not found");
    amount = money(amount != null ? amount : ap.openAmount);
    var pay = {
      id: nid("OP"),
      docNo: "OP-" + Date.now().toString().slice(-6),
      supplierName: ap.supplierName,
      date: today(),
      amount: amount,
      status: "POSTED",
      apInvoiceId: ap.id,
      demo: true
    };
    JnDemoState.collection("outgoingPayments").unshift(pay);
    ap.openAmount = money(Number(ap.openAmount) - amount);
    if (ap.openAmount <= 0) { ap.openAmount = 0; ap.status = "PAID"; }
    JnDemoState.upsert("apInvoices", ap);
    var bank = JnDemoState.collection("bankAccounts")[0];
    if (bank) bank.balance = money(Number(bank.balance) - amount);
    postJournal({
      source: pay.docNo,
      description: "DEMO supplier payment",
      lines: [
        { account: "2202", debit: amount, credit: 0 },
        { account: "1001", debit: 0, credit: amount }
      ]
    });
    JnDemoEvents.emit("PAYMENT_RECORDED", { kind: "OUTGOING", id: pay.id, amount: amount, ap: ap.docNo });
    return clone(pay);
  }

  /* ---------- Sales ---------- */
  function saveSalesOrder(rec) {
    if (!rec.id) rec.id = nid("SO");
    if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("SO");
    if (!rec.status) rec.status = "DRAFT";
    if (!rec.date) rec.date = today();
    rec.total = lineTotals(rec.lines);
    var created = !JnDemoState.find("salesOrders", rec.id);
    JnDemoState.upsert("salesOrders", rec);
    if (created) JnDemoEvents.emit("SALES_ORDER_CREATED", { id: rec.id, docNo: rec.docNo });
    return clone(rec);
  }

  function postDelivery(input) {
    var so = null;
    if (input.salesOrderId) so = JnDemoState.find("salesOrders", input.salesOrderId);
    var lines = input.lines || (so ? so.lines : []) || [];
    var warehouse = input.warehouseCode || "WH-MAIN";
    var whRow = JnDemoState.collection("warehouses").filter(function (w) {
      return w.id === warehouse || w.code === warehouse;
    })[0];
    if (whRow) warehouse = whRow.code;

    var dl = {
      id: input.id || nid("DL"),
      docNo: input.docNo || JnDemoState.nextNumber("DL"),
      salesOrderId: so ? so.id : input.salesOrderId || "",
      customerId: (so && so.customerId) || input.customerId || "",
      customerName: (so && so.customerName) || input.customerName || "",
      sourceDocNo: so ? so.docNo : input.sourceDocNo || "",
      date: input.date || today(),
      status: "POSTED",
      warehouseId: whRow ? whRow.id : "W1",
      warehouseCode: warehouse,
      qty: 0,
      lines: [],
      references: { source: so ? so.docNo : "", targets: [] },
      demo: true
    };
    var cogs = 0;
    lines.forEach(function (l) {
      var qty = Number(l.qty) || 0;
      var it = itemBySku(l.sku);
      var r = applyInventoryDelta({
        warehouse: warehouse,
        sku: l.sku,
        name: l.name || (it && it.name),
        qtyDelta: -qty,
        ref: dl.docNo,
        type: "DELIVERY",
        sourceDocType: "Delivery",
        sourceDocId: dl.id
      });
      cogs = money(cogs + qty * r.unitCost);
      dl.lines.push({
        id: nid("DLL"), itemId: l.itemId || (it && it.id), sku: l.sku,
        name: l.name || (it && it.name), qty: qty, uom: l.uom || "EA",
        unitCost: r.unitCost, price: Number(l.price) || (it && it.price) || 0
      });
      dl.qty += qty;
    });
    JnDemoState.upsert("deliveries", dl);
    if (so) {
      so.status = "PARTIAL";
      if (!so.references) so.references = { source: "", targets: [] };
      so.references.targets = (so.references.targets || []).concat([dl.docNo]);
      JnDemoState.upsert("salesOrders", so);
    }
    postJournal({
      source: dl.docNo,
      description: "DEMO delivery COGS (no tax)",
      lines: [
        { account: "6401", debit: cogs, credit: 0 },
        { account: "1403", debit: 0, credit: cogs }
      ]
    });
    JnDemoEvents.emit("DELIVERY_POSTED", { id: dl.id, docNo: dl.docNo, qty: dl.qty, cogs: cogs });
    return clone(dl);
  }

  function createARInvoiceFromDelivery(dlId) {
    var dl = JnDemoState.find("deliveries", dlId);
    if (!dl) throw new Error("Delivery not found");
    var so = dl.salesOrderId ? JnDemoState.find("salesOrders", dl.salesOrderId) : null;
    var total = 0;
    var lines = (dl.lines || []).map(function (l) {
      var price = Number(l.price);
      if (!price && so) {
        var sl = (so.lines || []).filter(function (x) { return x.sku === l.sku; })[0];
        if (sl) price = Number(sl.price) || 0;
      }
      if (!price) {
        var it = itemBySku(l.sku);
        price = (it && it.price) || 0;
      }
      var amount = money(Number(l.qty) * price);
      total += amount;
      return { id: nid("ARL"), itemId: l.itemId, sku: l.sku, name: l.name, qty: l.qty, price: price, amount: amount };
    });
    total = money(total);
    var ar = {
      id: nid("AR"),
      docNo: JnDemoState.nextNumber("AR"),
      customerId: dl.customerId,
      customerName: dl.customerName,
      date: today(),
      dueDate: today(),
      status: "OPEN",
      currency: "CNY",
      total: total,
      openAmount: total,
      lines: lines,
      references: { source: dl.docNo, targets: [] },
      demo: true
    };
    JnDemoState.upsert("arInvoices", ar);
    dl.references = dl.references || { source: "", targets: [] };
    dl.references.targets = (dl.references.targets || []).concat([ar.docNo]);
    JnDemoState.upsert("deliveries", dl);
    postJournal({
      source: ar.docNo,
      description: "DEMO AR invoice revenue (no tax)",
      lines: [
        { account: "1122", debit: total, credit: 0 },
        { account: "6001", debit: 0, credit: total }
      ]
    });
    JnDemoEvents.emit("AR_INVOICE_CREATED", { id: ar.id, docNo: ar.docNo, total: total });
    return clone(ar);
  }

  function recordIncomingPayment(arId, amount) {
    var ar = JnDemoState.find("arInvoices", arId);
    if (!ar) throw new Error("AR not found");
    amount = money(amount != null ? amount : ar.openAmount);
    var pay = {
      id: nid("IP"),
      docNo: "IP-" + Date.now().toString().slice(-6),
      customerName: ar.customerName,
      date: today(),
      amount: amount,
      status: "POSTED",
      arInvoiceId: ar.id,
      demo: true
    };
    JnDemoState.collection("incomingPayments").unshift(pay);
    ar.openAmount = money(Number(ar.openAmount) - amount);
    if (ar.openAmount <= 0) { ar.openAmount = 0; ar.status = "PAID"; }
    JnDemoState.upsert("arInvoices", ar);
    var bank = JnDemoState.collection("bankAccounts")[0];
    if (bank) bank.balance = money(Number(bank.balance) + amount);
    postJournal({
      source: pay.docNo,
      description: "DEMO customer receipt",
      lines: [
        { account: "1001", debit: amount, credit: 0 },
        { account: "1122", debit: 0, credit: amount }
      ]
    });
    JnDemoEvents.emit("PAYMENT_RECORDED", { kind: "INCOMING", id: pay.id, amount: amount, ar: ar.docNo });
    return clone(pay);
  }

  /* ---------- Inventory ops ---------- */
  function transferInventory(fromWh, toWh, sku, qty) {
    qty = Number(qty) || 0;
    var out = applyInventoryDelta({ warehouse: fromWh, sku: sku, qtyDelta: -qty, type: "TRANSFER_OUT", ref: "TR-" + Date.now() });
    applyInventoryDelta({ warehouse: toWh, sku: sku, qtyDelta: qty, unitCost: out.unitCost, type: "TRANSFER_IN", ref: out.txn.ref });
    var tr = {
      id: nid("TR"), docNo: out.txn.ref, fromWh: fromWh, toWh: toWh, sku: sku, qty: qty, status: "POSTED", demo: true
    };
    JnDemoState.collection("inventoryTransfers").unshift(tr);
    JnDemoEvents.emit("INVENTORY_TRANSFERRED", tr);
    JnDemoState.persist();
    return clone(tr);
  }

  function adjustInventory(warehouse, sku, qtyDelta, reason) {
    var r = applyInventoryDelta({
      warehouse: warehouse, sku: sku, qtyDelta: qtyDelta, type: "ADJUSTMENT", ref: "ADJ-" + Date.now()
    });
    JnDemoState.collection("inventoryAdjustments").unshift({
      id: nid("ADJ"), docNo: r.txn.ref, warehouse: warehouse, date: today(),
      reason: reason || "DEMO adjust", qty: qtyDelta, status: "POSTED", demo: true
    });
    JnDemoEvents.emit("INVENTORY_ADJUSTED", { sku: sku, qtyDelta: qtyDelta });
    return r;
  }

  /* ---------- Manufacturing ---------- */
  function issueProduction(moId) {
    var mo = JnDemoState.find("productionOrders", moId);
    if (!mo) throw new Error("MO not found");
    var bom = JnDemoState.collection("boms").filter(function (b) { return b.itemSku === mo.itemSku; })[0];
    var value = 0;
    (bom ? bom.lines : []).forEach(function (l) {
      var qty = money(Number(l.qty) * Number(mo.qty));
      var r = applyInventoryDelta({
        warehouse: "WH-RM", sku: l.componentSku, qtyDelta: -qty,
        type: "PRODUCTION_ISSUE", ref: mo.docNo, sourceDocType: "ProductionOrder", sourceDocId: mo.id
      });
      value = money(value + qty * r.unitCost);
    });
    var issue = {
      id: nid("PI"), docNo: "PI-" + Date.now().toString().slice(-6),
      productionOrderId: mo.id, date: today(), value: value, status: "POSTED", demo: true
    };
    JnDemoState.collection("productionIssues").unshift(issue);
    mo.status = "ISSUED";
    JnDemoState.upsert("productionOrders", mo);
    JnDemoEvents.emit("PRODUCTION_ISSUED", { mo: mo.docNo, value: value });
    return clone(issue);
  }

  function receiveProduction(moId) {
    var mo = JnDemoState.find("productionOrders", moId);
    if (!mo) throw new Error("MO not found");
    var it = itemBySku(mo.itemSku);
    var cost = (it && it.cost) || 0;
    applyInventoryDelta({
      warehouse: "WH-MAIN", sku: mo.itemSku, name: it && it.name,
      qtyDelta: Number(mo.qty) || 0, unitCost: cost,
      type: "PRODUCTION_RECEIPT", ref: mo.docNo, sourceDocType: "ProductionOrder", sourceDocId: mo.id
    });
    var rcpt = {
      id: nid("PR"), docNo: "PR-" + Date.now().toString().slice(-6),
      productionOrderId: mo.id, date: today(), qty: mo.qty, status: "POSTED", demo: true
    };
    JnDemoState.collection("productionReceipts").unshift(rcpt);
    mo.completedQty = mo.qty;
    mo.status = "CLOSED";
    JnDemoState.upsert("productionOrders", mo);
    JnDemoEvents.emit("PRODUCTION_RECEIVED", { mo: mo.docNo, qty: mo.qty });
    return clone(rcpt);
  }

  function saveProductionOrder(rec) {
    if (!rec.id) rec.id = nid("MO");
    if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("MO");
    if (!rec.status) rec.status = "DRAFT";
    return JnDemoState.upsert("productionOrders", rec);
  }

  /* ---------- Document facade for editor ---------- */
  function saveDocument(documentType, rec) {
    if (documentType === "SalesOrder") return saveSalesOrder(rec);
    if (documentType === "PurchaseOrder") return savePurchaseOrder(rec);
    if (documentType === "Delivery") {
      if (!rec.id) rec.id = nid("DL");
      if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("DL");
      if (!rec.status) rec.status = "DRAFT";
      return JnDemoState.upsert("deliveries", rec);
    }
    if (documentType === "GoodsReceipt") {
      if (!rec.id) rec.id = nid("GR");
      if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("GR");
      if (!rec.status) rec.status = "DRAFT";
      return JnDemoState.upsert("goodsReceipts", rec);
    }
    if (documentType === "ARInvoice") {
      if (!rec.id) rec.id = nid("AR");
      if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("AR");
      rec.total = lineTotals(rec.lines);
      if (rec.openAmount == null) rec.openAmount = rec.total;
      return JnDemoState.upsert("arInvoices", rec);
    }
    if (documentType === "APInvoice") {
      if (!rec.id) rec.id = nid("AP");
      if (!rec.docNo) rec.docNo = JnDemoState.nextNumber("AP");
      rec.total = lineTotals(rec.lines);
      if (rec.openAmount == null) rec.openAmount = rec.total;
      return JnDemoState.upsert("apInvoices", rec);
    }
    return JnDemoState.upsert(
      ({ SalesOrder: "salesOrders", PurchaseOrder: "purchaseOrders" })[documentType] || "salesOrders",
      rec
    );
  }

  function approveAndPost(documentType, rec) {
    if (documentType === "Delivery") {
      return postDelivery(Object.assign({}, rec, {
        salesOrderId: rec.salesOrderId,
        lines: rec.lines,
        warehouseCode: rec.warehouseCode || rec.warehouseId || "WH-MAIN",
        customerName: rec.customerName || rec.partnerName,
        customerId: rec.customerId || rec.partnerId
      }));
    }
    if (documentType === "GoodsReceipt") {
      return postGoodsReceipt(Object.assign({}, rec, {
        purchaseOrderId: rec.purchaseOrderId,
        lines: rec.lines,
        warehouseCode: rec.warehouseCode || rec.warehouseId || "WH-MAIN",
        supplierName: rec.supplierName || rec.partnerName,
        supplierId: rec.supplierId || rec.partnerId
      }));
    }
    if (documentType === "SalesOrder" || documentType === "PurchaseOrder") {
      var coll = documentType === "SalesOrder" ? "salesOrders" : "purchaseOrders";
      saveDocument(documentType, rec);
      return approveDocument(coll, rec.id);
    }
    if (documentType === "ARInvoice") {
      rec.status = "OPEN";
      rec.total = lineTotals(rec.lines);
      rec.openAmount = rec.total;
      JnDemoState.upsert("arInvoices", rec);
      postJournal({
        source: rec.docNo,
        description: "DEMO AR approve",
        lines: [
          { account: "1122", debit: rec.total, credit: 0 },
          { account: "6001", debit: 0, credit: rec.total }
        ]
      });
      JnDemoEvents.emit("AR_INVOICE_CREATED", { id: rec.id, docNo: rec.docNo });
      return clone(rec);
    }
    if (documentType === "APInvoice") {
      rec.status = "OPEN";
      rec.total = lineTotals(rec.lines);
      rec.openAmount = rec.total;
      JnDemoState.upsert("apInvoices", rec);
      postJournal({
        source: rec.docNo,
        description: "DEMO AP approve",
        lines: [
          { account: "1405", debit: rec.total, credit: 0 },
          { account: "2202", debit: 0, credit: rec.total }
        ]
      });
      JnDemoEvents.emit("AP_INVOICE_CREATED", { id: rec.id, docNo: rec.docNo });
      return clone(rec);
    }
    return saveDocument(documentType, rec);
  }

  /** Golden-path helpers for automated verification */
  function runPurchaseGoldenPath() {
    var sup = createSupplier({ name: "Golden Supplier", code: "SUP-GOLD" });
    var po = savePurchaseOrder({
      supplierId: sup.id,
      supplierName: sup.name,
      status: "APPROVED",
      lines: [{ sku: "FG-DEMO-A", name: "Demo Widget A", qty: 100, price: 50 }]
    });
    var gr = postGoodsReceipt({ purchaseOrderId: po.id, warehouseCode: "WH-MAIN" });
    var ap = createAPInvoiceFromGR(gr.id);
    var pay = recordOutgoingPayment(ap.id, 5000);
    var bal = findBalance("WH-MAIN", "FG-DEMO-A");
    return {
      supplier: sup, po: po, gr: gr, ap: ap, pay: pay,
      stock: bal ? bal.qty : 0,
      stockValue: bal ? bal.value : 0
    };
  }

  function runSalesGoldenPath() {
    var cust = createCustomer({ name: "Golden Customer", code: "CUS-GOLD" });
    var so = saveSalesOrder({
      customerId: cust.id,
      customerName: cust.name,
      status: "APPROVED",
      lines: [{ sku: "FG-DEMO-A", name: "Demo Widget A", qty: 30, price: 80 }]
    });
    var dl = postDelivery({ salesOrderId: so.id, warehouseCode: "WH-MAIN" });
    var ar = createARInvoiceFromDelivery(dl.id);
    var pay = recordIncomingPayment(ar.id, 2400);
    var bal = findBalance("WH-MAIN", "FG-DEMO-A");
    var kpis = JnDemoState.computeKpis();
    return {
      customer: cust, so: so, dl: dl, ar: ar, pay: pay,
      stock: bal ? bal.qty : 0,
      stockValue: bal ? bal.value : 0,
      cogs: 1500,
      revenue: 2400,
      margin: 900,
      kpis: kpis
    };
  }

  function runInventoryGoldenCheck() {
    var bal = findBalance("WH-MAIN", "FG-DEMO-A");
    var txns = JnDemoState.collection("inventoryTransactions").filter(function (t) {
      return t.sku === "FG-DEMO-A";
    });
    return { qty: bal ? bal.qty : 0, value: bal ? bal.value : 0, txns: clone(txns) };
  }

  /** Inventory ops: GR already done elsewhere; transfer + adjust for UI 1.6 */
  function runInventoryOpsGoldenPath() {
    var beforeMain = findBalance("WH-MAIN", "FG-DEMO-A");
    var beforeQty = beforeMain ? Number(beforeMain.qty) : 0;
    var tr = transferInventory("WH-MAIN", "WH-FG2", "FG-DEMO-A", 5);
    var adj = adjustInventory("WH-MAIN", "FG-DEMO-A", -2, "UI16 demo adjust");
    var main = findBalance("WH-MAIN", "FG-DEMO-A");
    var fg2 = findBalance("WH-FG2", "FG-DEMO-A");
    return {
      transfer: tr,
      adjust: adj,
      mainQty: main ? main.qty : 0,
      fg2Qty: fg2 ? fg2.qty : 0,
      expectedMain: beforeQty - 5 - 2,
      expectedFg2: 5
    };
  }

  function runManufacturingGoldenPath() {
    var mo = saveProductionOrder({
      itemSku: "FG-DEMO-A",
      qty: 10,
      completedQty: 0,
      status: "RELEASED",
      warehouse: "WH-MAIN"
    });
    var issue = issueProduction(mo.id);
    var receipt = receiveProduction(mo.id);
    var rm1 = findBalance("WH-RM", "RM-001");
    var rm2 = findBalance("WH-RM", "RM-002");
    var fg = findBalance("WH-MAIN", "FG-DEMO-A");
    var moFinal = JnDemoState.find("productionOrders", mo.id);
    return {
      mo: moFinal,
      issue: issue,
      receipt: receipt,
      rm1Qty: rm1 ? rm1.qty : 0,
      rm2Qty: rm2 ? rm2.qty : 0,
      fgQty: fg ? fg.qty : 0,
      status: moFinal ? moFinal.status : null
    };
  }

  function resetDemo() {
    return JnDemoState.reset();
  }

  global.JnAppService = {
    createCustomer: createCustomer,
    createSupplier: createSupplier,
    createItem: createItem,
    upsertMaster: upsertMaster,
    savePurchaseOrder: savePurchaseOrder,
    saveSalesOrder: saveSalesOrder,
    submitDocument: submitDocument,
    approveDocument: approveDocument,
    postGoodsReceipt: postGoodsReceipt,
    postDelivery: postDelivery,
    createAPInvoiceFromGR: createAPInvoiceFromGR,
    createARInvoiceFromDelivery: createARInvoiceFromDelivery,
    recordOutgoingPayment: recordOutgoingPayment,
    recordIncomingPayment: recordIncomingPayment,
    transferInventory: transferInventory,
    adjustInventory: adjustInventory,
    issueProduction: issueProduction,
    receiveProduction: receiveProduction,
    saveProductionOrder: saveProductionOrder,
    saveDocument: saveDocument,
    approveAndPost: approveAndPost,
    postJournal: postJournal,
    runPurchaseGoldenPath: runPurchaseGoldenPath,
    runSalesGoldenPath: runSalesGoldenPath,
    runInventoryGoldenCheck: runInventoryGoldenCheck,
    runInventoryOpsGoldenPath: runInventoryOpsGoldenPath,
    runManufacturingGoldenPath: runManufacturingGoldenPath,
    resetDemo: resetDemo,
    getKpis: function () { return JnDemoState.computeKpis(); }
  };
})(window);
