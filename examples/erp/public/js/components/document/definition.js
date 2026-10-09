// Author: 张晓达
/**
 * DocumentUiDefinition - strong typed configs for document editors (UI 1.2).
 * No eval / arbitrary code execution.
 */
(function (global) {
  function field(key, label, type, opts) {
    opts = opts || {};
    return {
      key: key,
      label: label,
      type: type, /* text|date|money|select|partner|warehouse|employee|currency|contact */
      required: !!opts.required,
      readOnly: !!opts.readOnly,
      defaultValue: opts.defaultValue != null ? opts.defaultValue : "",
      options: opts.options || null,
      partnerKind: opts.partnerKind || null /* CUSTOMER|SUPPLIER */
    };
  }

  function col(key, label, type, opts) {
    opts = opts || {};
    return {
      key: key,
      label: label,
      type: type, /* text|qty|money|percent|item|uom */
      required: !!opts.required,
      width: opts.width || 100,
      editable: opts.editable !== false
    };
  }

  function def(cfg) {
    return {
      documentType: cfg.documentType,
      title: cfg.title,
      collection: cfg.collection,
      pageId: cfg.pageId,
      partnerKind: cfg.partnerKind,
      headerFields: cfg.headerFields,
      lineColumns: cfg.lineColumns,
      footerFields: cfg.footerFields,
      availableActions: cfg.availableActions,
      validationRules: cfg.validationRules || [],
      taxMode: cfg.taxMode || "exclusive", /* exclusive|inclusive */
      rounding: cfg.rounding || "half-up-cent"
    };
  }

  var ACTIONS_FULL = ["save", "submit", "approve", "reject", "copy", "cancel", "close"];

  var DEFS = {};

  DEFS.SalesOrder = def({
    documentType: "SalesOrder",
    title: "Sales Order",
    collection: "salesOrders",
    pageId: "sales.orders",
    partnerKind: "CUSTOMER",
    taxMode: "exclusive",
    headerFields: [
      field("docNo", "Document No", "text", { readOnly: true }),
      field("partnerId", "Customer", "partner", { required: true, partnerKind: "CUSTOMER" }),
      field("contactName", "Contact", "contact", {}),
      field("date", "Order Date", "date", { required: true }),
      field("currency", "Currency", "currency", { required: true, defaultValue: "CNY" }),
      field("warehouseId", "Warehouse", "warehouse", {}),
      field("employeeId", "Sales Rep", "employee", {}),
      field("status", "Status", "text", { readOnly: true, defaultValue: "DRAFT" })
    ],
    lineColumns: [
      col("lineNo", "#", "text", { editable: false, width: 40 }),
      col("itemId", "Item", "item", { required: true, width: 160 }),
      col("sku", "SKU", "text", { editable: false, width: 110 }),
      col("name", "Name", "text", { width: 160 }),
      col("uom", "UoM", "uom", { width: 60 }),
      col("qty", "Qty", "qty", { required: true, width: 80 }),
      col("unitPriceCents", "Unit Price", "money", { required: true, width: 100 }),
      col("discountBps", "Disc %", "percent", { width: 70 }),
      col("taxBps", "Tax %", "percent", { width: 70 }),
      col("lineAmountCents", "Amount", "money", { editable: false, width: 110 })
    ],
    footerFields: [
      field("subtotalCents", "Subtotal", "money", { readOnly: true }),
      field("taxCents", "Tax", "money", { readOnly: true }),
      field("totalCents", "Total", "money", { readOnly: true })
    ],
    availableActions: ACTIONS_FULL,
    validationRules: [
      { scope: "header", field: "partnerId", rule: "required" },
      { scope: "header", field: "date", rule: "required" },
      { scope: "lines", field: "itemId", rule: "required" },
      { scope: "lines", field: "qty", rule: "positive" }
    ]
  });

  DEFS.PurchaseOrder = def({
    documentType: "PurchaseOrder",
    title: "Purchase Order",
    collection: "purchaseOrders",
    pageId: "pur.orders",
    partnerKind: "SUPPLIER",
    headerFields: [
      field("docNo", "Document No", "text", { readOnly: true }),
      field("partnerId", "Supplier", "partner", { required: true, partnerKind: "SUPPLIER" }),
      field("contactName", "Contact", "contact", {}),
      field("date", "Order Date", "date", { required: true }),
      field("currency", "Currency", "currency", { required: true, defaultValue: "CNY" }),
      field("warehouseId", "Warehouse", "warehouse", {}),
      field("employeeId", "Buyer", "employee", {}),
      field("status", "Status", "text", { readOnly: true, defaultValue: "DRAFT" })
    ],
    lineColumns: DEFS.SalesOrder.lineColumns.slice(),
    footerFields: DEFS.SalesOrder.footerFields.slice(),
    availableActions: ACTIONS_FULL,
    validationRules: DEFS.SalesOrder.validationRules.slice()
  });

  DEFS.Delivery = def({
    documentType: "Delivery",
    title: "Delivery",
    collection: "deliveries",
    pageId: "sales.deliveries",
    partnerKind: "CUSTOMER",
    headerFields: [
      field("docNo", "Document No", "text", { readOnly: true }),
      field("partnerId", "Customer", "partner", { required: true, partnerKind: "CUSTOMER" }),
      field("sourceDocNo", "Sales Order", "text", {}),
      field("date", "Delivery Date", "date", { required: true }),
      field("warehouseId", "Warehouse", "warehouse", { required: true }),
      field("status", "Status", "text", { readOnly: true, defaultValue: "DRAFT" })
    ],
    lineColumns: [
      col("lineNo", "#", "text", { editable: false, width: 40 }),
      col("itemId", "Item", "item", { required: true, width: 160 }),
      col("sku", "SKU", "text", { editable: false, width: 110 }),
      col("name", "Name", "text", { width: 160 }),
      col("uom", "UoM", "uom", { width: 60 }),
      col("qty", "Qty", "qty", { required: true, width: 80 })
    ],
    footerFields: [
      field("totalQty", "Total Qty", "text", { readOnly: true })
    ],
    availableActions: ["save", "submit", "approve", "copy", "cancel", "close"],
    validationRules: [
      { scope: "header", field: "partnerId", rule: "required" },
      { scope: "lines", field: "qty", rule: "positive" }
    ]
  });

  DEFS.GoodsReceipt = def({
    documentType: "GoodsReceipt",
    title: "Goods Receipt",
    collection: "goodsReceipts",
    pageId: "pur.gr",
    partnerKind: "SUPPLIER",
    headerFields: [
      field("docNo", "Document No", "text", { readOnly: true }),
      field("partnerId", "Supplier", "partner", { required: true, partnerKind: "SUPPLIER" }),
      field("sourceDocNo", "Purchase Order", "text", {}),
      field("date", "Receipt Date", "date", { required: true }),
      field("warehouseId", "Warehouse", "warehouse", { required: true }),
      field("status", "Status", "text", { readOnly: true, defaultValue: "DRAFT" })
    ],
    lineColumns: DEFS.Delivery.lineColumns.slice(),
    footerFields: DEFS.Delivery.footerFields.slice(),
    availableActions: ["save", "submit", "approve", "copy", "cancel", "close"],
    validationRules: DEFS.Delivery.validationRules.slice()
  });

  DEFS.ARInvoice = def({
    documentType: "ARInvoice",
    title: "AR Invoice",
    collection: "arInvoices",
    pageId: "sales.ar",
    partnerKind: "CUSTOMER",
    taxMode: "exclusive",
    headerFields: [
      field("docNo", "Document No", "text", { readOnly: true }),
      field("partnerId", "Customer", "partner", { required: true, partnerKind: "CUSTOMER" }),
      field("date", "Invoice Date", "date", { required: true }),
      field("dueDate", "Due Date", "date", { required: true }),
      field("currency", "Currency", "currency", { required: true, defaultValue: "CNY" }),
      field("status", "Status", "text", { readOnly: true, defaultValue: "DRAFT" })
    ],
    lineColumns: DEFS.SalesOrder.lineColumns.slice(),
    footerFields: DEFS.SalesOrder.footerFields.slice(),
    availableActions: ACTIONS_FULL,
    validationRules: DEFS.SalesOrder.validationRules.slice()
  });

  DEFS.APInvoice = def({
    documentType: "APInvoice",
    title: "AP Invoice",
    collection: "apInvoices",
    pageId: "pur.ap",
    partnerKind: "SUPPLIER",
    taxMode: "exclusive",
    headerFields: [
      field("docNo", "Document No", "text", { readOnly: true }),
      field("partnerId", "Supplier", "partner", { required: true, partnerKind: "SUPPLIER" }),
      field("date", "Invoice Date", "date", { required: true }),
      field("dueDate", "Due Date", "date", { required: true }),
      field("currency", "Currency", "currency", { required: true, defaultValue: "CNY" }),
      field("status", "Status", "text", { readOnly: true, defaultValue: "DRAFT" })
    ],
    lineColumns: DEFS.SalesOrder.lineColumns.slice(),
    footerFields: DEFS.SalesOrder.footerFields.slice(),
    availableActions: ACTIONS_FULL,
    validationRules: DEFS.SalesOrder.validationRules.slice()
  });

  var BY_PAGE = {};
  Object.keys(DEFS).forEach(function (k) {
    BY_PAGE[DEFS[k].pageId] = DEFS[k];
  });

  global.JnDocumentDefs = {
    all: DEFS,
    get: function (type) { return DEFS[type] || null; },
    getByPageId: function (pageId) { return BY_PAGE[pageId] || null; },
    types: Object.keys(DEFS)
  };
})(window);
