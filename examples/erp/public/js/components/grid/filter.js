// Author: 张晓达
/**
 * Filter evaluation + date presets (timezone: Asia/Shanghai DEMO local calendar day).
 */
(function (global) {
  var OPS = JnGridModel.OPS;
  var DT = JnGridModel.DATA_TYPES;

  function pad(n) { return n < 10 ? "0" + n : String(n); }

  /** Local calendar date YYYY-MM-DD in browser local TZ (DEMO: document as local / Asia-Shanghai machine). */
  function ymd(d) {
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  }

  function startOfWeek(d) {
    var x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    var day = (x.getDay() + 6) % 7; /* Monday=0 */
    x.setDate(x.getDate() - day);
    return x;
  }

  function datePresetRange(preset) {
    var now = new Date();
    var today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var from, to;
    if (preset === "today") { from = today; to = today; }
    else if (preset === "yesterday") {
      from = new Date(today); from.setDate(from.getDate() - 1); to = from;
    } else if (preset === "thisWeek") {
      from = startOfWeek(today);
      to = new Date(from); to.setDate(to.getDate() + 6);
    } else if (preset === "thisMonth") {
      from = new Date(today.getFullYear(), today.getMonth(), 1);
      to = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    } else if (preset === "lastMonth") {
      from = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      to = new Date(today.getFullYear(), today.getMonth(), 0);
    } else if (preset === "thisQuarter") {
      var q = Math.floor(today.getMonth() / 3) * 3;
      from = new Date(today.getFullYear(), q, 1);
      to = new Date(today.getFullYear(), q + 3, 0);
    } else if (preset === "thisYear") {
      from = new Date(today.getFullYear(), 0, 1);
      to = new Date(today.getFullYear(), 11, 31);
    } else {
      return null;
    }
    return { from: ymd(from), to: ymd(to), tz: "local-calendar (DEMO; typically Asia/Shanghai machines)" };
  }

  function asString(v) { return v == null ? "" : String(v); }

  function compareMoney(a, b) {
    var ac = JnGridModel.moneyToCents(a);
    var bc = JnGridModel.moneyToCents(b);
    if (ac == null && bc == null) return 0;
    if (ac == null) return -1;
    if (bc == null) return 1;
    return ac === bc ? 0 : (ac < bc ? -1 : 1);
  }

  function compareVal(a, b, dataType) {
    if (dataType === DT.MONEY) return compareMoney(a, b);
    if (dataType === DT.INTEGER || dataType === DT.QUANTITY || dataType === DT.DECIMAL) {
      var an = Number(a), bn = Number(b);
      if (isNaN(an) && isNaN(bn)) return 0;
      if (isNaN(an)) return -1;
      if (isNaN(bn)) return 1;
      return an === bn ? 0 : (an < bn ? -1 : 1);
    }
    var as = asString(a), bs = asString(b);
    if (as === bs) return 0;
    return as < bs ? -1 : 1;
  }

  function matchCondition(row, cond, colMap) {
    if (!cond || !cond.field) return true;
    var col = colMap[cond.field];
    var dt = (col && col.dataType) || DT.STRING;
    var op = cond.op || OPS.EQ;
    var val = row[cond.field];
    if (op === OPS.IS_NULL) return val == null || val === "";
    if (op === OPS.IS_NOT_NULL) return !(val == null || val === "");

    if (cond.datePreset) {
      var range = datePresetRange(cond.datePreset);
      if (!range) return true;
      var ds = asString(val).slice(0, 10);
      return ds >= range.from && ds <= range.to;
    }

    if (op === OPS.CONTAINS) return asString(val).toLowerCase().indexOf(asString(cond.value).toLowerCase()) >= 0;
    if (op === OPS.STARTS_WITH) return asString(val).toLowerCase().indexOf(asString(cond.value).toLowerCase()) === 0;
    if (op === OPS.IN) {
      var list = Array.isArray(cond.value) ? cond.value : String(cond.value || "").split(",").map(function (x) { return x.trim(); });
      return list.indexOf(asString(val)) >= 0;
    }
    if (op === OPS.BETWEEN) {
      var a = cond.value, b = cond.value2;
      return compareVal(val, a, dt) >= 0 && compareVal(val, b, dt) <= 0;
    }
    var cmp = compareVal(val, cond.value, dt);
    if (op === OPS.EQ) return cmp === 0;
    if (op === OPS.NE) return cmp !== 0;
    if (op === OPS.GT) return cmp > 0;
    if (op === OPS.GTE) return cmp >= 0;
    if (op === OPS.LT) return cmp < 0;
    if (op === OPS.LTE) return cmp <= 0;
    return true;
  }

  function matchGroup(row, group, colMap, depth) {
    depth = depth || 0;
    if (depth > JnGridModel.MAX_FILTER_DEPTH) return true;
    if (!group) return true;
    var logic = (group.logic || "AND").toUpperCase();
    var parts = [];
    (group.conditions || []).forEach(function (c) { parts.push(matchCondition(row, c, colMap)); });
    (group.groups || []).forEach(function (g) { parts.push(matchGroup(row, g, colMap, depth + 1)); });
    if (!parts.length) return true;
    if (logic === "OR") {
      for (var i = 0; i < parts.length; i++) if (parts[i]) return true;
      return false;
    }
    for (var j = 0; j < parts.length; j++) if (!parts[j]) return false;
    return true;
  }

  function countConditions(group, depth) {
    depth = depth || 0;
    if (!group || depth > JnGridModel.MAX_FILTER_DEPTH) return 0;
    var n = (group.conditions || []).length;
    (group.groups || []).forEach(function (g) { n += countConditions(g, depth + 1); });
    return n;
  }

  function stableSort(rows, sorting, getId) {
    if (!sorting || !sorting.length) return rows;
    var indexed = rows.map(function (r, i) { return { r: r, i: i }; });
    indexed.sort(function (a, b) {
      for (var s = 0; s < sorting.length; s++) {
        var spec = sorting[s];
        var av = a.r[spec.field], bv = b.r[spec.field];
        var c;
        if (av == null && bv == null) c = 0;
        else if (av == null) c = 1;
        else if (bv == null) c = -1;
        else if (av === bv) c = 0;
        else c = av > bv ? 1 : -1;
        if (c !== 0) return spec.dir === "desc" ? -c : c;
      }
      return a.i - b.i; /* stable */
    });
    return indexed.map(function (x) { return x.r; });
  }

  function aggregate(rows, field, fn, dataType) {
    fn = (fn || "COUNT").toUpperCase();
    if (fn === "COUNT") return rows.length;
    var vals = [];
    rows.forEach(function (r) {
      var v = r[field];
      if (v == null || v === "") return;
      if (dataType === DT.MONEY) vals.push(JnGridModel.moneyToCents(v));
      else vals.push(Number(v));
    });
    if (!vals.length) return null;
    if (fn === "SUM") {
      var sum = 0;
      vals.forEach(function (v) { sum += v; });
      return dataType === DT.MONEY ? sum / 100 : sum;
    }
    if (fn === "AVG") {
      var s2 = 0;
      vals.forEach(function (v) { s2 += v; });
      var avg = s2 / vals.length;
      return dataType === DT.MONEY ? Math.round(avg) / 100 : avg;
    }
    if (fn === "MIN") {
      var mn = Math.min.apply(null, vals);
      return dataType === DT.MONEY ? mn / 100 : mn;
    }
    if (fn === "MAX") {
      var mx = Math.max.apply(null, vals);
      return dataType === DT.MONEY ? mx / 100 : mx;
    }
    return null;
  }

  global.JnGridFilter = {
    matchGroup: matchGroup,
    matchCondition: matchCondition,
    datePresetRange: datePresetRange,
    countConditions: countConditions,
    stableSort: stableSort,
    aggregate: aggregate,
    compareVal: compareVal
  };
})(window);
