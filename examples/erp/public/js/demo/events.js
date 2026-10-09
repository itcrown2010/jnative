// Author: 张晓达
/**
 * DemoBusinessEvent bus - UI subscriptions only.
 * NOT a substitute for real database transactions.
 */
(function (global) {
  var listeners = [];
  var history = [];

  var TYPES = [
    "CUSTOMER_CREATED", "ITEM_CREATED", "SUPPLIER_CREATED",
    "PURCHASE_ORDER_CREATED", "GOODS_RECEIPT_POSTED",
    "SALES_ORDER_CREATED", "DELIVERY_POSTED",
    "AR_INVOICE_CREATED", "AP_INVOICE_CREATED", "PAYMENT_RECORDED",
    "PRODUCTION_ISSUED", "PRODUCTION_RECEIVED",
    "INVENTORY_ADJUSTED", "INVENTORY_TRANSFERRED",
    "JOURNAL_POSTED", "DEMO_RESET", "STATE_CHANGED"
  ];

  function emit(type, payload) {
    var evt = {
      type: type,
      payload: payload || {},
      at: new Date().toISOString(),
      demo: true,
      note: "DEMO event - not a formal DB transaction"
    };
    history.push(evt);
    if (history.length > 200) history.shift();
    listeners.slice().forEach(function (fn) {
      try { fn(evt); } catch (e) { console.error("DemoEvent listener", e); }
    });
    return evt;
  }

  function subscribe(fn) {
    listeners.push(fn);
    return function () {
      listeners = listeners.filter(function (x) { return x !== fn; });
    };
  }

  global.JnDemoEvents = {
    TYPES: TYPES,
    emit: emit,
    subscribe: subscribe,
    history: function () { return history.slice(); },
    clearHistory: function () { history = []; }
  };
})(window);
