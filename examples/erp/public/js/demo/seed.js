// Author: 张晓达
/**
 * DemoSeedService - initial DEMO company data (UI 1.4).
 * Assumptions documented for golden-path finance: no tax/freight/discount unless set on docs.
 */
(function (global) {
  function pad(n, w) {
    var s = String(n);
    while (s.length < (w || 3)) s = "0" + s;
    return s;
  }

  function build() {
    var customers = [];
    var i;
    for (i = 1; i <= 20; i++) {
      customers.push({
        id: "C" + pad(i),
        code: "CUS-" + pad(i),
        name: i === 1 ? "Acme Industries" : ("Customer " + pad(i)),
        city: ["Shanghai", "Beijing", "Shenzhen", "Hangzhou", "Suzhou"][(i - 1) % 5],
        country: "CN",
        creditLimit: 50000 + i * 10000,
        status: "ACTIVE",
        phone: "+86-21-5550-" + pad(1000 + i, 4),
        email: "c" + pad(i) + "@demo.example"
      });
    }

    var suppliers = [];
    for (i = 1; i <= 10; i++) {
      suppliers.push({
        id: "S" + pad(i),
        code: "SUP-" + pad(i),
        name: i === 1 ? "SteelWorks Ltd" : ("Supplier " + pad(i)),
        city: ["Tianjin", "Suzhou", "Ningbo", "Guangzhou", "Wuxi"][(i - 1) % 5],
        country: "CN",
        paymentTerms: "Net 30",
        status: "ACTIVE",
        email: "s" + pad(i) + "@demo.example"
      });
    }

    var items = [];
    /* Golden-path item: cost 50, sell 80, opening qty 0 */
    items.push({
      id: "I-GOLD", sku: "FG-DEMO-A", name: "Demo Widget A", category: "Finished Goods",
      uom: "EA", price: 80, cost: 50, warehouse: "WH-MAIN", onHand: 0, status: "ACTIVE"
    });
    for (i = 1; i <= 99; i++) {
      var isRm = i <= 30;
      items.push({
        id: "I" + pad(i),
        sku: (isRm ? "RM-" : "FG-") + pad(i),
        name: (isRm ? "Raw Material " : "Finished Good ") + pad(i),
        category: isRm ? "Raw Material" : "Finished Goods",
        uom: isRm && i % 3 === 0 ? "KG" : "EA",
        price: isRm ? 10 + (i % 20) : 40 + (i % 60),
        cost: isRm ? 8 + (i % 15) : 25 + (i % 40),
        warehouse: isRm ? "WH-RM" : "WH-MAIN",
        onHand: isRm ? 200 + i * 10 : 50 + i,
        status: "ACTIVE"
      });
    }

    var warehouses = [
      { id: "W1", code: "WH-MAIN", name: "Main Warehouse", city: "Shanghai", status: "ACTIVE" },
      { id: "W2", code: "WH-RM", name: "Raw Materials WH", city: "Shanghai", status: "ACTIVE" },
      { id: "W3", code: "WH-FG2", name: "Finished Goods East", city: "Suzhou", status: "ACTIVE" }
    ];

    var users = [];
    for (i = 1; i <= 10; i++) {
      users.push({
        id: "U" + pad(i),
        email: "user" + i + "@jnative.dev",
        name: "Demo User " + i,
        role: i === 1 ? "Admin" : (i <= 4 ? "Sales" : "Buyer"),
        company: "Demo Co",
        status: "ACTIVE"
      });
    }

    var balances = items.filter(function (it) { return it.onHand > 0; }).map(function (it, idx) {
      return {
        id: "IB" + pad(idx + 1),
        warehouse: it.warehouse,
        sku: it.sku,
        name: it.name,
        qty: it.onHand,
        unitCost: it.cost,
        value: Math.round(it.onHand * it.cost * 100) / 100
      };
    });

    var state = {
      meta: {
        version: 14,
        company: { id: "CO1", code: "DEMO", name: "Demo Co", currency: "CNY" },
        assumptions: {
          tax: "none (DEMO)",
          freight: "none (DEMO)",
          discount: "none unless set on document (DEMO)",
          openingForGoldenSku: "FG-DEMO-A starts at qty 0"
        },
        persistence: "localStorage key jn_demo_state_v14 - survives refresh; cleared on Reset or clearing site data. NOT server persistence."
      },
      customers: customers,
      suppliers: suppliers,
      items: items,
      categories: [
        { id: "CAT1", code: "FG", name: "Finished Goods" },
        { id: "CAT2", code: "RM", name: "Raw Material" },
        { id: "CAT3", code: "PKG", name: "Packaging" }
      ],
      warehouses: warehouses,
      units: [
        { id: "U1", code: "EA", name: "Each" },
        { id: "U2", code: "KG", name: "Kilogram" },
        { id: "U3", code: "BOX", name: "Box" }
      ],
      priceLists: [
        { id: "PL1", code: "STD", name: "Standard", currency: "CNY", status: "ACTIVE" }
      ],
      currencies: [
        { id: "CUR1", code: "CNY", name: "Chinese Yuan", rate: 1 },
        { id: "CUR2", code: "USD", name: "US Dollar", rate: 7.2 },
        { id: "CUR3", code: "EUR", name: "Euro", rate: 7.8 }
      ],
      employees: [
        { id: "E1", code: "EMP-001", name: "Li Wei", department: "Sales", title: "Account Manager", status: "ACTIVE" },
        { id: "E2", code: "EMP-002", name: "Chen Fang", department: "Purchasing", title: "Buyer", status: "ACTIVE" },
        { id: "E3", code: "EMP-003", name: "Wang Jun", department: "Finance", title: "Controller", status: "ACTIVE" }
      ],
      departments: [
        { id: "D1", code: "SALES", name: "Sales" },
        { id: "D2", code: "PURCH", name: "Purchasing" },
        { id: "D3", code: "FIN", name: "Finance" },
        { id: "D4", code: "MFG", name: "Manufacturing" }
      ],
      salesQuotations: [],
      salesOrders: [],
      deliveries: [],
      salesReturns: [],
      arInvoices: [],
      purchaseRequests: [],
      purchaseQuotations: [],
      purchaseOrders: [],
      goodsReceipts: [],
      purchaseReturns: [],
      apInvoices: [],
      inventoryBalances: balances,
      inventoryTransactions: [],
      inventoryTransfers: [],
      inventoryCounting: [],
      inventoryAdjustments: [],
      batches: [],
      serials: [],
      inventoryAlerts: [
        { id: "IA1", sku: "FG-002", message: "Below reorder point (DEMO)", severity: "WARN" }
      ],
      accounts: [
        { id: "A1", code: "1001", name: "Cash/Bank", type: "Asset", balance: 100000 },
        { id: "A2", code: "1122", name: "Accounts Receivable", type: "Asset", balance: 0 },
        { id: "A3", code: "1403", name: "Inventory", type: "Asset", balance: 0 },
        { id: "A4", code: "2202", name: "Accounts Payable", type: "Liability", balance: 0 },
        { id: "A5", code: "6001", name: "Revenue", type: "Income", balance: 0 },
        { id: "A6", code: "6401", name: "COGS", type: "Expense", balance: 0 },
        { id: "A7", code: "1405", name: "GR/IR Clearing", type: "Asset", balance: 0 }
      ],
      journalEntries: [],
      glLines: [],
      trialBalance: [],
      incomingPayments: [],
      outgoingPayments: [],
      bankAccounts: [
        { id: "BA1", code: "BANK-CNY", name: "Demo Bank CNY", currency: "CNY", balance: 100000 }
      ],
      fiscalPeriods: [
        { id: "FP1", code: "2026-Q4", name: "2026 Q4", status: "OPEN" }
      ],
      boms: [
        {
          id: "BOM1", code: "BOM-DEMO-A", itemSku: "FG-DEMO-A", version: "1.0", status: "ACTIVE",
          lines: [
            { componentSku: "RM-001", qty: 2, uom: "EA" },
            { componentSku: "RM-002", qty: 1, uom: "EA" }
          ]
        }
      ],
      productionOrders: [],
      productionIssues: [],
      productionReturns: [],
      productionReceipts: [],
      mrpSuggestions: [
        { id: "MRP1", sku: "RM-001", qty: 100, action: "Buy", status: "OPEN" }
      ],
      leads: [
        { id: "LD1", name: "Lead Alpha", owner: "Li Wei", stage: "New", value: 12000, status: "OPEN" },
        { id: "LD2", name: "Lead Beta", owner: "Li Wei", stage: "Qualified", value: 8000, status: "OPEN" }
      ],
      opportunities: [
        { id: "OP1", name: "Opp Acme Expansion", customerName: "Acme Industries", stage: "Proposal", value: 50000, probability: 40 }
      ],
      activities: [
        { id: "ACT1", type: "Call", subject: "Follow up Acme", owner: "Li Wei", due: "2026-10-10", status: "OPEN" }
      ],
      visits: [
        { id: "VIS1", customerName: "Acme Industries", date: "2026-10-05", owner: "Li Wei", notes: "DEMO visit" }
      ],
      users: users,
      roles: [
        { id: "R1", code: "ADMIN", name: "Admin", permissions: 40 },
        { id: "R2", code: "SALES", name: "Sales", permissions: 12 },
        { id: "R3", code: "FINANCE", name: "Finance", permissions: 18 }
      ],
      permissions: [
        { id: "P1", code: "SALES.ORDER.READ", module: "Sales" },
        { id: "P2", code: "SALES.ORDER.POST", module: "Sales" },
        { id: "P3", code: "FIN.JOURNAL.POST", module: "Finance" }
      ],
      companies: [
        { id: "CO1", code: "DEMO", name: "Demo Co", currency: "CNY", status: "ACTIVE" }
      ],
      workflows: [
        { id: "WF1", code: "PO_APPROVAL", name: "Purchase Order Approval", status: "ACTIVE", steps: 2 },
        { id: "WF2", code: "SO_APPROVAL", name: "Sales Order Approval", status: "ACTIVE", steps: 1 }
      ],
      customFields: [
        { id: "CF1", entity: "Customer", name: "Industry", type: "Text", active: true }
      ],
      forms: [
        { id: "F1", code: "SO_HEADER", name: "Sales Order Header", fields: 12 }
      ],
      numberSeries: [
        { id: "NS1", docType: "SO", prefix: "SO-", next: 2001 },
        { id: "NS2", docType: "PO", prefix: "PO-", next: 5201 },
        { id: "NS3", docType: "DL", prefix: "DL-", next: 3001 },
        { id: "NS4", docType: "GR", prefix: "GR-", next: 5301 },
        { id: "NS5", docType: "AR", prefix: "AR-", next: 4001 },
        { id: "NS6", docType: "AP", prefix: "AP-", next: 5501 },
        { id: "NS7", docType: "JE", prefix: "JE-", next: 8001 },
        { id: "NS8", docType: "MO", prefix: "MO-", next: 9001 }
      ],
      settings: [
        { id: "SET1", key: "company.name", value: "Demo Co" },
        { id: "SET2", key: "ui.theme", value: "enterprise-light" },
        { id: "SET3", key: "demo.mode", value: "true" },
        { id: "SET4", key: "demo.persistence", value: "localStorage jn_demo_state_v14" }
      ],
      auditLogs: [],
      notifications: [
        { id: "N1", title: "DEMO MODE", body: "All data is demonstration only", time: "09:00" }
      ]
    };

    /* recompute inventory account balance from balances */
    var invVal = 0;
    state.inventoryBalances.forEach(function (b) { invVal += Number(b.value) || 0; });
    state.accounts.forEach(function (a) {
      if (a.code === "1403") a.balance = Math.round(invVal * 100) / 100;
    });
    refreshTrialBalance(state);
    return state;
  }

  function refreshTrialBalance(state) {
    state.trialBalance = (state.accounts || []).map(function (a, idx) {
      var debit = 0, credit = 0;
      var bal = Number(a.balance) || 0;
      if (a.type === "Asset" || a.type === "Expense") debit = bal >= 0 ? bal : 0;
      else credit = bal >= 0 ? bal : 0;
      if (bal < 0) {
        if (a.type === "Asset" || a.type === "Expense") credit = -bal;
        else debit = -bal;
      }
      return { id: "TB" + idx, account: a.code + " " + a.name, debit: debit, credit: credit };
    });
  }

  global.JnDemoSeed = {
    build: build,
    refreshTrialBalance: refreshTrialBalance,
    counts: function (state) {
      return {
        company: 1,
        customers: (state.customers || []).length,
        suppliers: (state.suppliers || []).length,
        items: (state.items || []).length,
        warehouses: (state.warehouses || []).length,
        users: (state.users || []).length
      };
    }
  };
})(window);
