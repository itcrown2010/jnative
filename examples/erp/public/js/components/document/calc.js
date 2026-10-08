/**
 * DocumentCalculationService - integer minor currency units (cents).
 * Demo rounding: half-up to cent. No binary float as money source of truth.
 */
(function (global) {
  function toInt(n) {
    if (n == null || n === "") return 0;
    if (typeof n === "number") return n < 0 ? Math.ceil(n) : Math.floor(n);
    var s = String(n).trim();
    if (!s) return 0;
    var v = parseInt(s, 10);
    return isNaN(v) ? 0 : v;
  }

  /** half-up divide for non-negative integers */
  function divHalfUp(num, den) {
    if (den <= 0) return 0;
    return Math.floor((num + Math.floor(den / 2)) / den);
  }

  /**
   * Line calc:
   * gross = qty * unitPriceCents
   * discount = floor(gross * discountBps / 10000) with half-up
   * net = gross - discount
   * tax (exclusive) = half-up(net * taxBps / 10000)
   * lineAmount = net + tax  (exclusive mode)
   * inclusive: lineAmount = net; tax extracted
   */
  function calcLine(line, taxMode) {
    var qty = toInt(line.qty);
    var unit = toInt(line.unitPriceCents);
    var discBps = toInt(line.discountBps);
    var taxBps = toInt(line.taxBps);
    if (qty < 0) qty = 0;
    if (unit < 0) unit = 0;
    if (discBps < 0) discBps = 0;
    if (taxBps < 0) taxBps = 0;
    if (discBps > 10000) discBps = 10000;

    var gross = qty * unit;
    var discount = divHalfUp(gross * discBps, 10000);
    var net = gross - discount;
    if (net < 0) net = 0;

    var tax = 0;
    var lineAmount = net;
    if (taxMode === "inclusive") {
      /* net includes tax; extract tax portion */
      if (taxBps > 0) {
        tax = divHalfUp(net * taxBps, 10000 + taxBps);
        lineAmount = net;
      }
    } else {
      tax = divHalfUp(net * taxBps, 10000);
      lineAmount = net + tax;
    }

    return {
      qty: qty,
      unitPriceCents: unit,
      discountBps: discBps,
      taxBps: taxBps,
      discountCents: discount,
      netCents: net,
      taxCents: tax,
      lineAmountCents: lineAmount
    };
  }

  function calcDocument(doc, def) {
    var taxMode = (def && def.taxMode) || "exclusive";
    var lines = doc.lines || [];
    var subtotal = 0;
    var taxTotal = 0;
    var total = 0;
    var totalQty = 0;
    var i;
    for (i = 0; i < lines.length; i++) {
      var c = calcLine(lines[i], taxMode);
      lines[i].qty = c.qty;
      lines[i].unitPriceCents = c.unitPriceCents;
      lines[i].discountBps = c.discountBps;
      lines[i].taxBps = c.taxBps;
      lines[i].discountCents = c.discountCents;
      lines[i].netCents = c.netCents;
      lines[i].taxCents = c.taxCents;
      lines[i].lineAmountCents = c.lineAmountCents;
      lines[i].lineNo = i + 1;
      subtotal += c.netCents;
      taxTotal += c.taxCents;
      total += c.lineAmountCents;
      totalQty += c.qty;
    }
    doc.subtotalCents = subtotal;
    doc.taxCents = taxTotal;
    doc.totalCents = total;
    doc.totalQty = totalQty;
    /* legacy display fields for list compatibility */
    doc.total = total / 100;
    return doc;
  }

  function formatCents(cents) {
    var n = toInt(cents);
    var neg = n < 0;
    if (neg) n = -n;
    var yuan = Math.floor(n / 100);
    var fen = n % 100;
    var s = (neg ? "-" : "") + "CNY " + yuan.toLocaleString() + "." + (fen < 10 ? "0" : "") + fen;
    return s;
  }

  function parseMoneyToCents(text) {
    if (text == null || text === "") return 0;
    if (typeof text === "number") return Math.round(text * 100);
    var s = String(text).replace(/[^0-9.\-]/g, "");
    if (!s) return 0;
    var neg = s.charAt(0) === "-";
    if (neg) s = s.slice(1);
    var parts = s.split(".");
    var whole = parseInt(parts[0] || "0", 10) || 0;
    var frac = parts[1] || "0";
    if (frac.length === 1) frac = frac + "0";
    if (frac.length > 2) frac = frac.slice(0, 2);
    var cents = whole * 100 + (parseInt(frac, 10) || 0);
    return neg ? -cents : cents;
  }

  global.JnDocumentCalc = {
    calcLine: calcLine,
    calcDocument: calcDocument,
    formatCents: formatCents,
    parseMoneyToCents: parseMoneyToCents,
    toInt: toInt
  };
})(window);
