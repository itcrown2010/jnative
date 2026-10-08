/**
 * PageRegistry - every menu item maps to a real page definition.
 */
(function (global) {
  function col(key, title, opts) {
    opts = opts || {};
    return { key: key, title: title, type: opts.type || "text", width: opts.width };
  }

  function page(def) {
    def.route = def.route || ("/" + def.pageId);
    def.permissions = def.permissions || ["demo.user"];
    def.interactive = true;
    /* PREVIEW | INTERACTIVE | VERIFIED */
    if (!def.status) {
      if (def.enterpriseGrid || def.editor === true || def.template === "dashboard" || def.template === "bomEditor" || def.template === "mrp" || def.template === "settings") {
        def.status = "INTERACTIVE";
      } else if (def.template === "report") {
        def.status = "PREVIEW";
      } else {
        def.status = "INTERACTIVE";
      }
    }
    return def;
  }

  var pages = [
    /* Workbench */
    page({ pageId: "dash.executive", module: "Workbench", title: "Executive Dashboard", route: "/dashboard", template: "dashboard", dashKind: "executive", icon: "*" }),
    page({ pageId: "dash.sales", module: "Workbench", title: "Sales Dashboard", route: "/dashboard/sales", template: "dashboard", dashKind: "sales", icon: "*" }),
    page({ pageId: "dash.purchasing", module: "Workbench", title: "Purchasing Dashboard", route: "/dashboard/purchasing", template: "dashboard", dashKind: "purchasing", icon: "*" }),
    page({ pageId: "dash.inventory", module: "Workbench", title: "Inventory Dashboard", route: "/dashboard/inventory", template: "dashboard", dashKind: "inventory", icon: "*" }),
    page({ pageId: "dash.finance", module: "Workbench", title: "Finance Dashboard", route: "/dashboard/finance", template: "dashboard", dashKind: "finance", icon: "*" }),
    page({ pageId: "dash.manufacturing", module: "Workbench", title: "Manufacturing Dashboard", route: "/dashboard/manufacturing", template: "dashboard", dashKind: "manufacturing", icon: "*" }),

    /* Master data */
    page({ pageId: "md.customers", module: "Master Data", title: "Customers", route: "/master/customers", template: "masterList", collection: "customers", enterpriseGrid: true, detailTemplate: true, formFields: ["code","name","city","country","creditLimit","phone","email","status"], columns: [col("code","Code"),col("name","Name"),col("city","City"),col("creditLimit","Credit Limit",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "md.suppliers", module: "Master Data", title: "Suppliers", route: "/master/suppliers", template: "masterList", collection: "suppliers", enterpriseGrid: true, formFields: ["code","name","city","country","paymentTerms","email","status"], columns: [col("code","Code"),col("name","Name"),col("city","City"),col("paymentTerms","Terms"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "md.items", module: "Master Data", title: "Items", route: "/master/items", template: "masterList", collection: "items", enterpriseGrid: true, formFields: ["sku","name","category","uom","price","cost","warehouse","onHand","status"], columns: [col("sku","SKU"),col("name","Name"),col("category","Category"),col("price","Price",{type:"money"}),col("onHand","On Hand",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "md.categories", module: "Master Data", title: "Item Categories", route: "/master/categories", template: "masterList", collection: "categories", formFields: ["code","name"], columns: [col("code","Code"),col("name","Name")], icon: "*" }),
    page({ pageId: "md.warehouses", module: "Master Data", title: "Warehouses", route: "/master/warehouses", template: "masterList", collection: "warehouses", formFields: ["code","name","city","status"], columns: [col("code","Code"),col("name","Name"),col("city","City"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "md.units", module: "Master Data", title: "Units", route: "/master/units", template: "masterList", collection: "units", formFields: ["code","name"], columns: [col("code","Code"),col("name","Name")], icon: "*" }),
    page({ pageId: "md.pricelists", module: "Master Data", title: "Price Lists", route: "/master/pricelists", template: "masterList", collection: "priceLists", formFields: ["code","name","currency","status"], columns: [col("code","Code"),col("name","Name"),col("currency","Currency"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "md.currencies", module: "Master Data", title: "Currencies", route: "/master/currencies", template: "masterList", collection: "currencies", formFields: ["code","name","rate"], columns: [col("code","Code"),col("name","Name"),col("rate","Rate",{type:"qty"})], icon: "*" }),
    page({ pageId: "md.employees", module: "Master Data", title: "Employees", route: "/master/employees", template: "masterList", collection: "employees", formFields: ["code","name","department","title","status"], columns: [col("code","Code"),col("name","Name"),col("department","Dept"),col("title","Title"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "md.departments", module: "Master Data", title: "Departments", route: "/master/departments", template: "masterList", collection: "departments", formFields: ["code","name"], columns: [col("code","Code"),col("name","Name")], icon: "*" }),

    /* Sales */
    page({ pageId: "sales.quotations", module: "Sales", title: "Sales Quotations", route: "/sales/quotations", template: "documentList", collection: "salesQuotations", columns: [col("docNo","Doc No"),col("customerName","Customer"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sales.orders", module: "Sales", title: "Sales Orders", route: "/sales/orders", template: "documentList", collection: "salesOrders", editor: true, enterpriseGrid: true, partnerField: "customerName", columns: [col("docNo","Doc No"),col("customerName","Customer"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sales.deliveries", module: "Sales", title: "Deliveries", route: "/sales/deliveries", template: "documentList", collection: "deliveries", editor: true, columns: [col("docNo","Doc No"),col("customerName","Customer"),col("date","Date"),col("qty","Qty",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sales.returns", module: "Sales", title: "Sales Returns", route: "/sales/returns", template: "documentList", collection: "salesReturns", columns: [col("docNo","Doc No"),col("customerName","Customer"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sales.ar", module: "Sales", title: "AR Invoices", route: "/sales/ar-invoices", template: "documentList", collection: "arInvoices", editor: true, columns: [col("docNo","Doc No"),col("customerName","Customer"),col("date","Date"),col("dueDate","Due"),col("total","Total",{type:"money"}),col("openAmount","Open",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sales.analysis", module: "Sales", title: "Sales Analysis", route: "/sales/analysis", template: "report", reportKind: "sales", icon: "*" }),

    /* Purchasing */
    page({ pageId: "pur.requests", module: "Purchasing", title: "Purchase Requests", route: "/purchasing/requests", template: "documentList", collection: "purchaseRequests", columns: [col("docNo","Doc No"),col("requester","Requester"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "pur.quotations", module: "Purchasing", title: "Purchase Quotations", route: "/purchasing/quotations", template: "documentList", collection: "purchaseQuotations", columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "pur.orders", module: "Purchasing", title: "Purchase Orders", route: "/purchasing/orders", template: "documentList", collection: "purchaseOrders", editor: true, enterpriseGrid: true, partnerField: "supplierName", columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "pur.gr", module: "Purchasing", title: "Goods Receipts", route: "/purchasing/goods-receipts", template: "documentList", collection: "goodsReceipts", editor: true, columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("date","Date"),col("qty","Qty",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "pur.returns", module: "Purchasing", title: "Purchase Returns", route: "/purchasing/returns", template: "documentList", collection: "purchaseReturns", columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("date","Date"),col("total","Total",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "pur.ap", module: "Purchasing", title: "AP Invoices", route: "/purchasing/ap-invoices", template: "documentList", collection: "apInvoices", editor: true, columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("date","Date"),col("dueDate","Due"),col("total","Total",{type:"money"}),col("openAmount","Open",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "pur.analysis", module: "Purchasing", title: "Purchasing Analysis", route: "/purchasing/analysis", template: "report", reportKind: "purchasing", icon: "*" }),

    /* Inventory */
    page({ pageId: "inv.balance", module: "Inventory", title: "Inventory Balance", route: "/inventory/balance", template: "masterList", collection: "inventoryBalances", enterpriseGrid: true, formFields: ["warehouse","sku","name","qty","value"], columns: [col("warehouse","Warehouse"),col("sku","SKU"),col("name","Name"),col("qty","Qty",{type:"qty"}),col("value","Value",{type:"money"})], readOnly: true, icon: "*" }),
    page({ pageId: "inv.txns", module: "Inventory", title: "Inventory Transactions", route: "/inventory/transactions", template: "masterList", collection: "inventoryTransactions", enterpriseGrid: true, formFields: ["date","type","sku","qty","ref","warehouse"], columns: [col("date","Date"),col("type","Type"),col("sku","SKU"),col("qty","Qty",{type:"qty"}),col("ref","Reference"),col("warehouse","WH")], readOnly: true, icon: "*" }),
    page({ pageId: "inv.transfers", module: "Inventory", title: "Inventory Transfers", route: "/inventory/transfers", template: "documentList", collection: "inventoryTransfers", columns: [col("docNo","Doc No"),col("fromWh","From"),col("toWh","To"),col("sku","SKU"),col("qty","Qty",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "inv.counting", module: "Inventory", title: "Inventory Counting", route: "/inventory/counting", template: "documentList", collection: "inventoryCounting", columns: [col("docNo","Doc No"),col("warehouse","Warehouse"),col("date","Date"),col("variance","Variance",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "inv.adjustments", module: "Inventory", title: "Inventory Adjustments", route: "/inventory/adjustments", template: "documentList", collection: "inventoryAdjustments", columns: [col("docNo","Doc No"),col("warehouse","Warehouse"),col("date","Date"),col("reason","Reason"),col("qty","Qty",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "inv.batches", module: "Inventory", title: "Batch Management", route: "/inventory/batches", template: "masterList", collection: "batches", formFields: ["batchNo","sku","qty","expiry","warehouse"], columns: [col("batchNo","Batch"),col("sku","SKU"),col("qty","Qty",{type:"qty"}),col("expiry","Expiry"),col("warehouse","WH")], icon: "*" }),
    page({ pageId: "inv.serials", module: "Inventory", title: "Serial Numbers", route: "/inventory/serials", template: "masterList", collection: "serials", formFields: ["serialNo","sku","status","warehouse"], columns: [col("serialNo","Serial"),col("sku","SKU"),col("status","Status",{type:"status"}),col("warehouse","WH")], icon: "*" }),
    page({ pageId: "inv.alerts", module: "Inventory", title: "Inventory Alerts", route: "/inventory/alerts", template: "masterList", collection: "inventoryAlerts", formFields: ["sku","message","severity"], columns: [col("sku","SKU"),col("message","Message"),col("severity","Severity")], readOnly: true, icon: "*" }),
    page({ pageId: "inv.valuation", module: "Inventory", title: "Inventory Valuation", route: "/inventory/valuation", template: "report", reportKind: "inventory", icon: "*" }),

    /* Finance */
    page({ pageId: "fin.coa", module: "Finance", title: "Chart of Accounts", route: "/finance/accounts", template: "masterList", collection: "accounts", formFields: ["code","name","type","balance"], columns: [col("code","Code"),col("name","Name"),col("type","Type"),col("balance","Balance",{type:"money"})], icon: "*" }),
    page({ pageId: "fin.journals", module: "Finance", title: "Journal Entries", route: "/finance/journals", template: "documentList", collection: "journalEntries", columns: [col("docNo","Doc No"),col("date","Date"),col("source","Source"),col("description","Description"),col("debit","Debit",{type:"money"}),col("credit","Credit",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "fin.gl", module: "Finance", title: "General Ledger", route: "/finance/gl", template: "masterList", collection: "glLines", formFields: ["date","account","debit","credit","journal"], columns: [col("date","Date"),col("account","Account"),col("debit","Debit",{type:"money"}),col("credit","Credit",{type:"money"}),col("journal","Journal")], readOnly: true, icon: "*" }),
    page({ pageId: "fin.tb", module: "Finance", title: "Trial Balance", route: "/finance/trial-balance", template: "masterList", collection: "trialBalance", formFields: ["account","debit","credit"], columns: [col("account","Account"),col("debit","Debit",{type:"money"}),col("credit","Credit",{type:"money"})], readOnly: true, icon: "*" }),
    page({ pageId: "fin.ar", module: "Finance", title: "Accounts Receivable", route: "/finance/ar", template: "documentList", collection: "arInvoices", enterpriseGrid: true, columns: [col("docNo","Doc No"),col("customerName","Customer"),col("total","Total",{type:"money"}),col("openAmount","Open",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "fin.ap", module: "Finance", title: "Accounts Payable", route: "/finance/ap", template: "documentList", collection: "apInvoices", enterpriseGrid: true, columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("total","Total",{type:"money"}),col("openAmount","Open",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "fin.ip", module: "Finance", title: "Incoming Payments", route: "/finance/incoming-payments", template: "documentList", collection: "incomingPayments", columns: [col("docNo","Doc No"),col("customerName","Customer"),col("date","Date"),col("amount","Amount",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "fin.op", module: "Finance", title: "Outgoing Payments", route: "/finance/outgoing-payments", template: "documentList", collection: "outgoingPayments", columns: [col("docNo","Doc No"),col("supplierName","Supplier"),col("date","Date"),col("amount","Amount",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "fin.banks", module: "Finance", title: "Bank Accounts", route: "/finance/banks", template: "masterList", collection: "bankAccounts", formFields: ["code","name","currency","balance"], columns: [col("code","Code"),col("name","Name"),col("currency","Currency"),col("balance","Balance",{type:"money"})], icon: "*" }),
    page({ pageId: "fin.periods", module: "Finance", title: "Fiscal Periods", route: "/finance/periods", template: "masterList", collection: "fiscalPeriods", formFields: ["code","name","status"], columns: [col("code","Code"),col("name","Name"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "fin.bs", module: "Finance", title: "Balance Sheet", route: "/finance/balance-sheet", template: "report", reportKind: "balanceSheet", icon: "*" }),
    page({ pageId: "fin.is", module: "Finance", title: "Income Statement", route: "/finance/income-statement", template: "report", reportKind: "income", icon: "*" }),
    page({ pageId: "fin.cf", module: "Finance", title: "Cash Flow Statement", route: "/finance/cash-flow", template: "report", reportKind: "cashflow", icon: "*" }),

    /* Manufacturing */
    page({ pageId: "mfg.bom", module: "Manufacturing", title: "BOM", route: "/mfg/bom", template: "documentList", collection: "boms", editor: "bom", columns: [col("code","Code"),col("itemSku","Item"),col("version","Version"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "mfg.bomEditor", module: "Manufacturing", title: "BOM Editor", route: "/mfg/bom-editor", template: "bomEditor", collection: "boms", icon: "*" }),
    page({ pageId: "mfg.orders", module: "Manufacturing", title: "Production Orders", route: "/mfg/orders", template: "documentList", collection: "productionOrders", enterpriseGrid: true, columns: [col("docNo","Doc No"),col("itemSku","Item"),col("qty","Qty",{type:"qty"}),col("completedQty","Completed",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "mfg.issues", module: "Manufacturing", title: "Production Issues", route: "/mfg/issues", template: "documentList", collection: "productionIssues", columns: [col("docNo","Doc No"),col("productionOrderId","MO"),col("date","Date"),col("value","Value",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "mfg.returns", module: "Manufacturing", title: "Production Returns", route: "/mfg/returns", template: "documentList", collection: "productionReturns", columns: [col("docNo","Doc No"),col("productionOrderId","MO"),col("date","Date"),col("qty","Qty",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "mfg.receipts", module: "Manufacturing", title: "Production Receipts", route: "/mfg/receipts", template: "documentList", collection: "productionReceipts", columns: [col("docNo","Doc No"),col("productionOrderId","MO"),col("date","Date"),col("qty","Qty",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "mfg.mrp", module: "Manufacturing", title: "MRP Workbench", route: "/mfg/mrp", template: "mrp", collection: "mrpSuggestions", icon: "*" }),
    page({ pageId: "mfg.cost", module: "Manufacturing", title: "Production Cost", route: "/mfg/cost", template: "report", reportKind: "mfgCost", icon: "*" }),
    page({ pageId: "mfg.progress", module: "Manufacturing", title: "Production Progress", route: "/mfg/progress", template: "report", reportKind: "mfgProgress", icon: "*" }),

    /* CRM */
    page({ pageId: "crm.leads", module: "CRM", title: "Leads", route: "/crm/leads", template: "masterList", collection: "leads", formFields: ["name","owner","stage","value","status"], columns: [col("name","Name"),col("owner","Owner"),col("stage","Stage"),col("value","Value",{type:"money"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "crm.opps", module: "CRM", title: "Opportunities", route: "/crm/opportunities", template: "masterList", collection: "opportunities", formFields: ["name","customerName","stage","value","probability"], columns: [col("name","Name"),col("customerName","Customer"),col("stage","Stage"),col("value","Value",{type:"money"}),col("probability","Prob %",{type:"qty"})], icon: "*" }),
    page({ pageId: "crm.activities", module: "CRM", title: "Activities", route: "/crm/activities", template: "masterList", collection: "activities", formFields: ["type","subject","owner","due","status"], columns: [col("type","Type"),col("subject","Subject"),col("owner","Owner"),col("due","Due"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "crm.visits", module: "CRM", title: "Customer Visits", route: "/crm/visits", template: "masterList", collection: "visits", formFields: ["customerName","date","owner","notes"], columns: [col("customerName","Customer"),col("date","Date"),col("owner","Owner"),col("notes","Notes")], icon: "*" }),
    page({ pageId: "crm.pipeline", module: "CRM", title: "Sales Pipeline", route: "/crm/pipeline", template: "report", reportKind: "pipeline", icon: "*" }),
    page({ pageId: "crm.forecast", module: "CRM", title: "Sales Forecast", route: "/crm/forecast", template: "report", reportKind: "forecast", icon: "*" }),

    /* Reports */
    page({ pageId: "rpt.sales", module: "Reports", title: "Sales Reports", route: "/reports/sales", template: "report", reportKind: "sales", icon: "*" }),
    page({ pageId: "rpt.purchasing", module: "Reports", title: "Purchasing Reports", route: "/reports/purchasing", template: "report", reportKind: "purchasing", icon: "*" }),
    page({ pageId: "rpt.inventory", module: "Reports", title: "Inventory Reports", route: "/reports/inventory", template: "report", reportKind: "inventory", icon: "*" }),
    page({ pageId: "rpt.finance", module: "Reports", title: "Finance Reports", route: "/reports/finance", template: "report", reportKind: "finance", icon: "*" }),
    page({ pageId: "rpt.mfg", module: "Reports", title: "Manufacturing Reports", route: "/reports/manufacturing", template: "report", reportKind: "mfg", icon: "*" }),
    page({ pageId: "rpt.customer", module: "Reports", title: "Customer Analytics", route: "/reports/customer", template: "report", reportKind: "customer", icon: "*" }),
    page({ pageId: "rpt.exec", module: "Reports", title: "Executive Analytics", route: "/reports/executive", template: "report", reportKind: "executive", icon: "*" }),

    /* System */
    page({ pageId: "sys.users", module: "System", title: "Users", route: "/system/users", template: "masterList", collection: "users", formFields: ["email","name","role","company","status"], columns: [col("email","Email"),col("name","Name"),col("role","Role"),col("company","Company"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sys.roles", module: "System", title: "Roles", route: "/system/roles", template: "masterList", collection: "roles", formFields: ["code","name","permissions"], columns: [col("code","Code"),col("name","Name"),col("permissions","Permissions",{type:"qty"})], icon: "*" }),
    page({ pageId: "sys.permissions", module: "System", title: "Permissions", route: "/system/permissions", template: "masterList", collection: "permissions", formFields: ["code","module"], columns: [col("code","Code"),col("module","Module")], readOnly: true, icon: "*" }),
    page({ pageId: "sys.companies", module: "System", title: "Companies", route: "/system/companies", template: "masterList", collection: "companies", formFields: ["code","name","currency","status"], columns: [col("code","Code"),col("name","Name"),col("currency","Currency"),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sys.departments", module: "System", title: "Departments", route: "/system/departments", template: "masterList", collection: "departments", formFields: ["code","name"], columns: [col("code","Code"),col("name","Name")], icon: "*" }),
    page({ pageId: "sys.workflows", module: "System", title: "Workflows", route: "/system/workflows", template: "masterList", collection: "workflows", formFields: ["code","name","steps","status"], columns: [col("code","Code"),col("name","Name"),col("steps","Steps",{type:"qty"}),col("status","Status",{type:"status"})], icon: "*" }),
    page({ pageId: "sys.customFields", module: "System", title: "Custom Fields", route: "/system/custom-fields", template: "masterList", collection: "customFields", formFields: ["entity","name","type"], columns: [col("entity","Entity"),col("name","Name"),col("type","Type")], icon: "*" }),
    page({ pageId: "sys.forms", module: "System", title: "Form Configuration", route: "/system/forms", template: "masterList", collection: "forms", formFields: ["code","name","fields"], columns: [col("code","Code"),col("name","Name"),col("fields","Fields",{type:"qty"})], icon: "*" }),
    page({ pageId: "sys.numbering", module: "System", title: "Numbering Series", route: "/system/numbering", template: "masterList", collection: "numberSeries", formFields: ["docType","prefix","next"], columns: [col("docType","Doc Type"),col("prefix","Prefix"),col("next","Next",{type:"qty"})], icon: "#" }),
    page({ pageId: "sys.settings", module: "System", title: "System Settings", route: "/system/settings", template: "settings", collection: "settings", icon: "*" }),
    page({ pageId: "sys.audit", module: "System", title: "Audit Logs", route: "/system/audit", template: "masterList", collection: "auditLogs", formFields: ["time","user","action","entity","ref"], columns: [col("time","Time"),col("user","User"),col("action","Action"),col("entity","Entity"),col("ref","Ref")], readOnly: true, icon: "*" }),
    page({ pageId: "sys.importExport", module: "System", title: "Import Export", route: "/system/import-export", template: "importExport", icon: "*" }),
    page({ pageId: "sys.wizard", module: "System", title: "Setup Wizard", route: "/system/wizard", template: "setupWizard", status: "INTERACTIVE", icon: "*" }),
    page({ pageId: "sys.help", module: "System", title: "Help Tutorials", route: "/system/help", template: "helpTutorials", status: "INTERACTIVE", icon: "*" }),
    page({ pageId: "inv.opening", module: "Inventory", title: "Opening Stock", route: "/inventory/opening", template: "openingStock", status: "INTERACTIVE", icon: "*" })
  ];

  var byId = {};
  var byRoute = {};
  pages.forEach(function (p) {
    byId[p.pageId] = p;
    byRoute[p.route] = p;
  });

  var menuGroups = [
    { title: "Workbench", titleKey: "nav.workbench", ids: ["dash.executive","dash.sales","dash.purchasing","dash.inventory","dash.finance","dash.manufacturing"] },
    { title: "Master Data", titleKey: "nav.master", ids: ["md.customers","md.suppliers","md.items","md.categories","md.warehouses","md.units","md.pricelists","md.currencies","md.employees","md.departments"] },
    { title: "Sales", titleKey: "nav.sales", ids: ["sales.quotations","sales.orders","sales.deliveries","sales.returns","sales.ar","sales.analysis"] },
    { title: "Purchasing", titleKey: "nav.purchasing", ids: ["pur.requests","pur.quotations","pur.orders","pur.gr","pur.returns","pur.ap","pur.analysis"] },
    { title: "Inventory", titleKey: "nav.inventory", ids: ["inv.balance","inv.opening","inv.txns","inv.transfers","inv.counting","inv.adjustments","inv.batches","inv.serials","inv.alerts","inv.valuation"] },
    { title: "Finance", titleKey: "nav.finance", ids: ["fin.coa","fin.journals","fin.gl","fin.tb","fin.ar","fin.ap","fin.ip","fin.op","fin.banks","fin.periods","fin.bs","fin.is","fin.cf"] },
    { title: "Manufacturing", titleKey: "nav.manufacturing", ids: ["mfg.bom","mfg.bomEditor","mfg.orders","mfg.issues","mfg.returns","mfg.receipts","mfg.mrp","mfg.cost","mfg.progress"] },
    { title: "CRM", titleKey: "nav.crm", ids: ["crm.leads","crm.opps","crm.activities","crm.visits","crm.pipeline","crm.forecast"] },
    { title: "Reports", titleKey: "nav.reports", ids: ["rpt.sales","rpt.purchasing","rpt.inventory","rpt.finance","rpt.mfg","rpt.customer","rpt.exec"] },
    { title: "Administration", titleKey: "nav.admin", ids: ["sys.wizard","sys.help","sys.users","sys.roles","sys.permissions","sys.companies","sys.departments","sys.workflows","sys.customFields","sys.forms","sys.numbering","sys.settings","sys.audit","sys.importExport"] }
  ];

  function localizePage(p) {
    if (!p) return null;
    var I = global.JnI18n;
    if (!I) return p;
    var cols = (p.columns || []).map(function (c) {
      return Object.assign({}, c, { title: I.colTitle(c) });
    });
    return Object.assign({}, p, {
      title: I.pageTitle(p),
      module: I.moduleTitle(p),
      columns: cols
    });
  }

  function localizeMenuGroups() {
    var I = global.JnI18n;
    return menuGroups.map(function (g) {
      var title = g.title;
      if (I) title = I.t(g.titleKey || ("nav." + g.title), null, g.title);
      return Object.assign({}, g, { title: title });
    });
  }

  function coverageMatrix() {
    return pages.map(function (p) {
      return {
        pageId: p.pageId,
        module: p.module,
        route: p.route,
        title: p.title,
        template: p.template,
        status: p.status,
        permissions: p.permissions,
        enterpriseGrid: !!p.enterpriseGrid,
        editor: p.editor || false
      };
    });
  }

  global.JnRegistry = {
    pages: pages,
    byId: byId,
    byRoute: byRoute,
    get menuGroups() { return localizeMenuGroups(); },
    get: function (id) { return localizePage(byId[id]); },
    getRaw: function (id) { return byId[id]; },
    getByRoute: function (route) { return localizePage(byRoute[route]); },
    count: pages.length,
    coverageMatrix: coverageMatrix,
    markVerified: function (ids) {
      (ids || []).forEach(function (id) {
        if (byId[id]) byId[id].status = "VERIFIED";
      });
    }
  };
})(window);
