// Author: 张晓达
/**
 * Grid column / query model (UI 1.3). Strong typed; no eval.
 */
(function (global) {
  var DATA_TYPES = {
    STRING: "STRING",
    INTEGER: "INTEGER",
    DECIMAL: "DECIMAL",
    DATE: "DATE",
    DATETIME: "DATETIME",
    BOOLEAN: "BOOLEAN",
    ENUM: "ENUM",
    MONEY: "MONEY",
    QUANTITY: "QUANTITY"
  };

  var OPS = {
    EQ: "EQ", NE: "NE", GT: "GT", GTE: "GTE", LT: "LT", LTE: "LTE",
    BETWEEN: "BETWEEN", CONTAINS: "CONTAINS", STARTS_WITH: "STARTS_WITH",
    IN: "IN", IS_NULL: "IS_NULL", IS_NOT_NULL: "IS_NOT_NULL"
  };

  var OPS_BY_TYPE = {};
  OPS_BY_TYPE[DATA_TYPES.STRING] = [OPS.EQ, OPS.NE, OPS.CONTAINS, OPS.STARTS_WITH, OPS.IN, OPS.IS_NULL, OPS.IS_NOT_NULL];
  OPS_BY_TYPE[DATA_TYPES.ENUM] = [OPS.EQ, OPS.NE, OPS.IN, OPS.IS_NULL, OPS.IS_NOT_NULL];
  OPS_BY_TYPE[DATA_TYPES.BOOLEAN] = [OPS.EQ, OPS.NE, OPS.IS_NULL, OPS.IS_NOT_NULL];
  OPS_BY_TYPE[DATA_TYPES.INTEGER] = [OPS.EQ, OPS.NE, OPS.GT, OPS.GTE, OPS.LT, OPS.LTE, OPS.BETWEEN, OPS.IN, OPS.IS_NULL, OPS.IS_NOT_NULL];
  OPS_BY_TYPE[DATA_TYPES.DECIMAL] = OPS_BY_TYPE[DATA_TYPES.INTEGER];
  OPS_BY_TYPE[DATA_TYPES.MONEY] = [OPS.EQ, OPS.NE, OPS.GT, OPS.GTE, OPS.LT, OPS.LTE, OPS.BETWEEN, OPS.IS_NULL, OPS.IS_NOT_NULL];
  OPS_BY_TYPE[DATA_TYPES.QUANTITY] = OPS_BY_TYPE[DATA_TYPES.INTEGER];
  OPS_BY_TYPE[DATA_TYPES.DATE] = [OPS.EQ, OPS.NE, OPS.GT, OPS.GTE, OPS.LT, OPS.LTE, OPS.BETWEEN, OPS.IS_NULL, OPS.IS_NOT_NULL];
  OPS_BY_TYPE[DATA_TYPES.DATETIME] = OPS_BY_TYPE[DATA_TYPES.DATE];

  var AGG_BY_TYPE = {};
  AGG_BY_TYPE[DATA_TYPES.STRING] = ["COUNT"];
  AGG_BY_TYPE[DATA_TYPES.ENUM] = ["COUNT"];
  AGG_BY_TYPE[DATA_TYPES.BOOLEAN] = ["COUNT"];
  AGG_BY_TYPE[DATA_TYPES.DATE] = ["COUNT", "MIN", "MAX"];
  AGG_BY_TYPE[DATA_TYPES.DATETIME] = ["COUNT", "MIN", "MAX"];
  AGG_BY_TYPE[DATA_TYPES.INTEGER] = ["COUNT", "SUM", "AVG", "MIN", "MAX"];
  AGG_BY_TYPE[DATA_TYPES.DECIMAL] = AGG_BY_TYPE[DATA_TYPES.INTEGER];
  AGG_BY_TYPE[DATA_TYPES.MONEY] = ["COUNT", "SUM", "AVG", "MIN", "MAX"];
  AGG_BY_TYPE[DATA_TYPES.QUANTITY] = AGG_BY_TYPE[DATA_TYPES.INTEGER];

  function mapLegacyType(t) {
    if (!t || t === "text") return DATA_TYPES.STRING;
    if (t === "money") return DATA_TYPES.MONEY;
    if (t === "qty") return DATA_TYPES.QUANTITY;
    if (t === "status") return DATA_TYPES.ENUM;
    if (t === "date") return DATA_TYPES.DATE;
    return DATA_TYPES.STRING;
  }

  function column(cfg) {
    cfg = cfg || {};
    return {
      field: cfg.field || cfg.key,
      title: cfg.title || cfg.field || cfg.key,
      dataType: cfg.dataType || mapLegacyType(cfg.type),
      width: cfg.width || 120,
      minWidth: cfg.minWidth || 60,
      maxWidth: cfg.maxWidth || 480,
      sortable: cfg.sortable !== false,
      filterable: cfg.filterable !== false,
      visible: cfg.visible !== false,
      pinned: cfg.pinned || null, /* left|right|null */
      formatter: cfg.formatter || null, /* named formatter id only */
      alignment: cfg.alignment || (cfg.dataType === DATA_TYPES.MONEY || cfg.dataType === DATA_TYPES.QUANTITY || cfg.dataType === DATA_TYPES.INTEGER || cfg.dataType === DATA_TYPES.DECIMAL ? "right" : "left")
    };
  }

  function fromPageColumns(cols) {
    return (cols || []).map(function (c, i) {
      var dt = null;
      if (/date|due|expiry/i.test(c.key) && (!c.type || c.type === "text")) dt = DATA_TYPES.DATE;
      var col = column({
        field: c.key,
        title: c.title,
        type: c.type,
        dataType: dt || undefined,
        width: c.width || (c.type === "money" ? 110 : 120),
        pinned: i === 0 ? "left" : null
      });
      if (dt) col.dataType = dt;
      return col;
    });
  }

  function queryState(init) {
    init = init || {};
    return {
      filters: init.filters || { logic: "AND", conditions: [], groups: [] },
      sorting: init.sorting || [], /* [{field, dir:'asc'|'desc'}] */
      page: init.page || 1,
      pageSize: init.pageSize || 20,
      grouping: init.grouping || null, /* {field} single-level */
      columns: init.columns || null,
      selectionMode: init.selectionMode || "multi", /* single|multi */
      selectScope: "page" /* page|all - never confuse */
    };
  }

  function moneyToCents(v) {
    if (v == null || v === "") return null;
    if (typeof v === "number") return Math.round(v * 100);
    var s = String(v).replace(/[^0-9.\-]/g, "");
    if (!s) return null;
    var neg = s.charAt(0) === "-";
    if (neg) s = s.slice(1);
    var parts = s.split(".");
    var whole = parseInt(parts[0] || "0", 10) || 0;
    var frac = (parts[1] || "0") + "00";
    frac = frac.slice(0, 2);
    var cents = whole * 100 + (parseInt(frac, 10) || 0);
    return neg ? -cents : cents;
  }

  global.JnGridModel = {
    DATA_TYPES: DATA_TYPES,
    OPS: OPS,
    OPS_BY_TYPE: OPS_BY_TYPE,
    AGG_BY_TYPE: AGG_BY_TYPE,
    column: column,
    fromPageColumns: fromPageColumns,
    queryState: queryState,
    moneyToCents: moneyToCents,
    MAX_FILTER_DEPTH: 3,
    MAX_FILTER_CONDITIONS: 20
  };
})(window);
