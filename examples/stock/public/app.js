/* JNative Stock Lab front-end (plain JS, canvas charts, no dependencies). */
(function () {
'use strict';

var UP = '#ef4444', DOWN = '#22c55e', ACC = '#4f8cff', MUT = '#8392a5', GRID = '#222b37', TXT = '#d8dee6', WARN = '#f59e0b';
var COLORS = ['#4f8cff', '#f59e0b', '#a855f7', '#14b8a6', '#ec4899', '#eab308'];

var state = { symbols: [], code: null, tab: 'chart', h: 5, pper: 'D', bars: null, pred: {}, loading: {} };

function $(id) { return document.getElementById(id); }
function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
function fx(x, d) { return (x == null || isNaN(x)) ? '-' : Number(x).toFixed(d); }
function pct(x, d) { return (x == null || isNaN(x)) ? '-' : (x * 100).toFixed(d == null ? 2 : d) + '%'; }
function sgn(x) { return x > 0 ? 'up' : (x < 0 ? 'down' : ''); }
function fdate(d) { var s = String(d); return s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6, 8); }

function api(path, opts) {
  return fetch(path, opts).then(function (r) {
    return r.text().then(function (t) {
      var j;
      try { j = JSON.parse(t); } catch (e) { throw new Error('\u670d\u52a1\u5668\u8fd4\u56de\u4e86\u65e0\u6cd5\u89e3\u6790\u7684\u5185\u5bb9'); }
      if (!r.ok || (j && j.error)) { throw new Error((j && j.message) || ('HTTP ' + r.status)); }
      return j;
    });
  });
}
function post(path) { return api(path, { method: 'POST' }); }

function setup(cv, h) {
  var dpr = window.devicePixelRatio || 1;
  cv.style.width = '100%';
  var w = cv.getBoundingClientRect().width || 600;
  cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); cv.style.height = h + 'px';
  var g = cv.getContext('2d');
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, w, h);
  g.font = '11px sans-serif';
  return { g: g, w: w, h: h };
}
function line(g, pts, color, lw) {
  g.beginPath(); var started = false;
  for (var i = 0; i < pts.length; i++) {
    if (pts[i] == null || isNaN(pts[i][1])) { started = false; continue; }
    if (!started) { g.moveTo(pts[i][0], pts[i][1]); started = true; } else { g.lineTo(pts[i][0], pts[i][1]); }
  }
  g.strokeStyle = color; g.lineWidth = lw || 1.2; g.stroke();
}
function niceTicks(lo, hi, n) {
  var span = hi - lo; if (span <= 0) { span = Math.abs(hi) * 0.1 + 1; }
  var step = Math.pow(10, Math.floor(Math.log(span / n) / Math.LN10)), err = span / n / step;
  if (err >= 7.5) step *= 10; else if (err >= 3.5) step *= 5; else if (err >= 1.5) step *= 2;
  var out = [], v = Math.ceil(lo / step) * step;
  for (; v <= hi + 1e-9; v += step) out.push(v);
  return out;
}
function yAxis(g, x0, x1, y0, y1, lo, hi, dp, grid) {
  var ticks = niceTicks(lo, hi, 4);
  g.fillStyle = MUT; g.textAlign = 'left'; g.textBaseline = 'middle';
  for (var i = 0; i < ticks.length; i++) {
    var y = y1 - (ticks[i] - lo) / (hi - lo) * (y1 - y0);
    if (grid !== false) { g.strokeStyle = GRID; g.lineWidth = 1; g.beginPath(); g.moveTo(x0, y); g.lineTo(x1, y); g.stroke(); }
    g.fillText(ticks[i].toFixed(dp), x1 + 4, y);
  }
}

/* ---------------- K-line chart ---------------- */
var K = { count: 120, end: 0, ind: 'MACD', boll: false, mx: -1, drag: null, per: 'D' };
function curBars() { return K.per === 'D' ? state.bars : state.mbars; }
function curSym() { return state.symbols.filter(function (x) { return x.code === state.code; })[0]; }
function pad2(n) { return (n < 10 ? '0' : '') + n; }
function dlabel(D, i) {
  if (!D.time) return fdate(D.date[i]);
  var t = D.time[i]; return fdate(D.date[i]).slice(5) + ' ' + pad2(Math.floor(t / 100)) + ':' + pad2(t % 100);
}
function drawChart() { if (K.per === 'T') drawT(); else drawK(); }

function drawK() {
  var D = curBars(), cv = $('kcv');
  if (!D || !cv) return;
  var N = D.close.length;
  K.count = Math.max(30, Math.min(N, K.count));
  K.end = Math.max(K.count, Math.min(N, K.end || N));
  var H = 520, S = setup(cv, H), g = S.g, W = S.w;
  var L = 6, R = 58, x0 = L, x1 = W - R;
  var pTop = 8, pBot = 300, vTop = 312, vBot = 372, iTop = 384, iBot = H - 8;
  var i0 = K.end - K.count, step = (x1 - x0) / K.count, bw = Math.max(1, step * 0.7);
  var lo = Infinity, hi = -Infinity, vmax = 0, i;
  for (i = i0; i < K.end; i++) {
    lo = Math.min(lo, D.low[i]); hi = Math.max(hi, D.high[i]); vmax = Math.max(vmax, D.volume[i]);
    if (K.boll && D.bollUp[i] != null) { hi = Math.max(hi, D.bollUp[i]); lo = Math.min(lo, D.bollLow[i]); }
  }
  var pad = (hi - lo) * 0.05; lo -= pad; hi += pad;
  var X = function (k) { return x0 + (k - i0 + 0.5) * step; };
  var Y = function (p) { return pBot - (p - lo) / (hi - lo) * (pBot - pTop); };
  yAxis(g, x0, x1, pTop, pBot, lo, hi, 2);
  for (i = i0; i < K.end; i++) {
    var up = D.close[i] >= D.open[i], col = up ? UP : DOWN, x = X(i);
    g.strokeStyle = col; g.fillStyle = col; g.lineWidth = 1;
    g.beginPath(); g.moveTo(x, Y(D.high[i])); g.lineTo(x, Y(D.low[i])); g.stroke();
    var yo = Y(D.open[i]), yc = Y(D.close[i]);
    g.fillRect(x - bw / 2, Math.min(yo, yc), bw, Math.max(1, Math.abs(yo - yc)));
    g.globalAlpha = 0.55; var vh = vmax > 0 ? D.volume[i] / vmax * (vBot - vTop) : 0;
    g.fillRect(x - bw / 2, vBot - vh, bw, vh); g.globalAlpha = 1;
  }
  var mas = [['ma5', '#f5d742'], ['ma10', '#4f8cff'], ['ma20', '#d946ef'], ['ma60', '#14b8a6']];
  mas.forEach(function (m) {
    var pts = []; for (var k = i0; k < K.end; k++) pts.push(D[m[0]][k] == null ? null : [X(k), Y(D[m[0]][k])]);
    line(g, pts, m[1], 1.1);
  });
  if (K.boll) {
    ['bollUp', 'bollMid', 'bollLow'].forEach(function (key) {
      var pts = []; for (var k = i0; k < K.end; k++) pts.push(D[key][k] == null ? null : [X(k), Y(D[key][k])]);
      line(g, pts, 'rgba(216,222,230,.55)', 1);
    });
  }
  g.fillStyle = MUT; g.textAlign = 'left'; g.textBaseline = 'top';
  g.fillText('\u6210\u4ea4\u91cf', x0 + 2, vTop - 1);
  /* indicator panel */
  g.strokeStyle = GRID; g.strokeRect(x0, iTop, x1 - x0, iBot - iTop);
  var series = [], ilo, ihi, dp = 2;
  if (K.ind === 'MACD') { series = [['dif', ACC], ['dea', WARN]]; ilo = Infinity; ihi = -Infinity; dp = 3;
    for (i = i0; i < K.end; i++) { [D.dif[i], D.dea[i], D.hist[i]].forEach(function (v) { if (v != null) { ilo = Math.min(ilo, v); ihi = Math.max(ihi, v); } }); }
    var ex = Math.max(Math.abs(ilo), Math.abs(ihi)); ilo = -ex; ihi = ex; }
  else if (K.ind === 'RSI') { series = [['rsi', ACC]]; ilo = 0; ihi = 100; dp = 0; }
  else { series = [['k', ACC], ['d', WARN], ['j', '#d946ef']]; ilo = -10; ihi = 110; dp = 0; }
  var IY = function (v) { return iBot - (v - ilo) / (ihi - ilo) * (iBot - iTop); };
  yAxis(g, x0, x1, iTop, iBot, ilo, ihi, dp, false);
  if (K.ind === 'RSI') { [30, 70].forEach(function (lv) { g.strokeStyle = GRID; g.beginPath(); g.moveTo(x0, IY(lv)); g.lineTo(x1, IY(lv)); g.stroke(); }); }
  if (K.ind === 'MACD') {
    for (i = i0; i < K.end; i++) { var hv = D.hist[i]; if (hv == null) continue; g.fillStyle = hv >= 0 ? UP : DOWN;
      g.fillRect(X(i) - bw / 2, Math.min(IY(0), IY(hv)), bw, Math.abs(IY(hv) - IY(0))); }
  }
  series.forEach(function (s) { var pts = []; for (var k = i0; k < K.end; k++) pts.push(D[s[0]][k] == null ? null : [X(k), IY(D[s[0]][k])]); line(g, pts, s[1], 1.2); });
  g.fillStyle = MUT; g.textBaseline = 'top'; g.textAlign = 'left'; g.fillText(K.ind, x0 + 4, iTop + 3);
  /* x labels */
  g.textAlign = 'center'; g.textBaseline = 'bottom';
  for (i = 0; i < 6; i++) { var k2 = i0 + Math.floor((K.count - 1) * i / 5); g.fillText(D.time ? dlabel(D, k2) : fdate(D.date[k2]).slice(2), Math.min(Math.max(X(k2), 40), x1 - 36), vBot + 11); }
  /* crosshair */
  var info = $('kinfo');
  if (K.mx >= x0 && K.mx <= x1) {
    var idx = Math.max(i0, Math.min(K.end - 1, i0 + Math.floor((K.mx - x0) / step)));
    var cx = X(idx);
    g.strokeStyle = 'rgba(216,222,230,.4)'; g.beginPath(); g.moveTo(cx, pTop); g.lineTo(cx, iBot); g.stroke();
    var prev = idx > 0 ? D.close[idx - 1] : D.close[idx], chg = D.close[idx] / prev - 1;
    info.innerHTML = '<b>' + dlabel(D, idx) + (D.live && idx === D.close.length - 1 ? ' <span class="warn">\u4eca\u65e5\u00b7\u5b9e\u65f6\u4ef7</span>' : '') + '</b>\u3000\u5f00 ' + fx(D.open[idx], 2) + '\u3000\u9ad8 ' + fx(D.high[idx], 2) + '\u3000\u4f4e ' + fx(D.low[idx], 2) +
      '\u3000\u6536 <b class="' + sgn(chg) + '">' + fx(D.close[idx], 2) + '</b>\u3000\u6da8\u8dcc <span class="' + sgn(chg) + '">' + pct(chg) + '</span>\u3000\u91cf ' + Math.round(D.volume[idx] / 100) + '\u624b' +
      '\u3000<span style="color:#f5d742">MA5 ' + fx(D.ma5[idx], 2) + '</span> <span style="color:#4f8cff">MA10 ' + fx(D.ma10[idx], 2) + '</span> <span style="color:#d946ef">MA20 ' + fx(D.ma20[idx], 2) + '</span> <span style="color:#14b8a6">MA60 ' + fx(D.ma60[idx], 2) + '</span>';
  } else {
    var j = K.end - 1, pc = j > 0 ? D.close[j - 1] : D.close[j];
    info.innerHTML = '<b>' + dlabel(D, j) + (D.live ? ' <span class="warn">\u4eca\u65e5\u00b7\u5b9e\u65f6\u4ef7</span>' : '') + '</b>\u3000\u6536 <b class="' + sgn(D.close[j] / pc - 1) + '">' + fx(D.close[j], 2) + '</b>\u3000' + (D.time ? '' : 'ATR(14) ' + fx(D.atr, 3) + '\u3000') + '<span class="muted">\u6eda\u8f6e\u7f29\u653e\uff0c\u62d6\u52a8\u5e73\u79fb</span>';
  }
}

function renderChart() {
  var root = $('tab-chart');
  var cs = curSym(), live = !!cs && cs.source === 'sina';
  if (!live) K.per = 'D';
  root.innerHTML = '<div class="card">' + (live ? '<div id="quoteBar"></div>' : '') + '<div class="toolbar">' +
    (live ? '<span class="muted">\u5468\u671f</span><span id="kPer"></span>' : '') +
    '<span class="muted">\u533a\u95f4</span><span id="kRange"></span>' +
    '<span class="muted">\u526f\u56fe</span><span id="kInd"></span>' +
    '<label class="muted"><input type="checkbox" id="kBoll"> \u5e03\u6797\u5e26</label></div>' +
    '<div id="kinfo" style="min-height:22px;font-size:13px;margin-bottom:4px"></div><canvas id="kcv"></canvas></div>';
  if (live) seg('kPer', [['D', '\u65e5\u7ebf'], ['T', '\u5206\u65f6'], ['5', '5\u5206'], ['15', '15\u5206'], ['30', '30\u5206'], ['60', '60\u5206']], K.per, function (v) { K.per = v; K.end = 0; if (v === 'D') { drawChart(); refreshLive(); } else { state.mbars = null; refreshLive(); } });
  seg('kRange', [['60', '60'], ['120', '120'], ['250', '250'], ['500', '500'], ['all', '\u5168\u90e8']], String(K.count >= 1500 ? 'all' : K.count), function (v) { K.count = v === 'all' ? 99999 : parseInt(v, 10); K.end = 0; drawChart(); });
  seg('kInd', [['MACD', 'MACD'], ['RSI', 'RSI'], ['KDJ', 'KDJ']], K.ind, function (v) { K.ind = v; drawChart(); });
  $('kBoll').checked = K.boll; $('kBoll').onchange = function () { K.boll = this.checked; drawChart(); };
  var cv = $('kcv');
  cv.onmousemove = function (e) {
    var r = cv.getBoundingClientRect(); K.mx = e.clientX - r.left;
    if (K.drag && K.per !== 'T') {
      var step = (r.width - 64) / K.count, dx = Math.round((K.mx - K.drag.x) / step);
      K.end = Math.max(K.count, Math.min(curBars().close.length, K.drag.end - dx));
    }
    drawChart();
  };
  cv.onmouseleave = function () { K.mx = -1; K.drag = null; drawChart(); };
  cv.onmousedown = function () { if (K.per !== 'T' && curBars()) K.drag = { x: K.mx, end: K.end || curBars().close.length }; };
  cv.onmouseup = function () { K.drag = null; };
  /* touch: one finger pans (and shows the crosshair), two fingers pinch to zoom; vertical swipes still scroll the page */
  var tdist = function (e) { var a = e.touches[0], b = e.touches[1]; return Math.sqrt((a.clientX - b.clientX) * (a.clientX - b.clientX) + (a.clientY - b.clientY) * (a.clientY - b.clientY)); };
  cv.addEventListener('touchstart', function (e) {
    var r = cv.getBoundingClientRect();
    if (e.touches.length === 2) { K.pinch = { d: tdist(e), count: K.count }; K.drag = null; return; }
    K.mx = e.touches[0].clientX - r.left;
    if (K.per !== 'T' && curBars()) K.drag = { x: K.mx, end: K.end || curBars().close.length };
    drawChart();
  }, { passive: true });
  cv.addEventListener('touchmove', function (e) {
    if (K.per === 'T' || !curBars()) { var r0 = cv.getBoundingClientRect(); K.mx = e.touches[0].clientX - r0.left; drawChart(); return; }
    var N = curBars().close.length;
    if (e.touches.length === 2 && K.pinch) {
      K.count = Math.max(30, Math.min(N, Math.round(K.pinch.count * K.pinch.d / tdist(e)))); K.end = Math.min(N, K.end || N); drawChart(); return;
    }
    var r = cv.getBoundingClientRect(); K.mx = e.touches[0].clientX - r.left;
    if (K.drag) {
      var step = (r.width - 64) / K.count, dx = Math.round((K.mx - K.drag.x) / step);
      K.end = Math.max(K.count, Math.min(N, K.drag.end - dx));
    }
    drawChart();
  }, { passive: true });
  cv.addEventListener('touchend', function () { K.drag = null; K.pinch = null; }, { passive: true });
  cv.onwheel = function (e) {
    if (K.per === 'T' || !curBars()) return;
    e.preventDefault(); var N = curBars().close.length;
    K.count = Math.max(30, Math.min(N, Math.round(K.count * (e.deltaY > 0 ? 1.15 : 0.87)))); K.end = Math.min(N, K.end || N); drawChart();
  };
  if (live) { drawQuote(); }
  if (K.per === 'D') { loadBars().then(drawChart); } else { $('kinfo').innerHTML = '<span class="muted">\u52a0\u8f7d\u4e2d\u2026</span>'; }
  refreshLive();
}

/* ---------------- Live quote, intraday, minute bars ---------------- */
function isTrading() {
  var n = new Date(Date.now() + (new Date().getTimezoneOffset() + 480) * 60000), dow = n.getDay(), hm = n.getHours() * 100 + n.getMinutes();
  if (dow === 0 || dow === 6) return false;
  return (hm >= 915 && hm <= 1131) || (hm >= 1300 && hm <= 1501);
}
function big(x) {
  if (x == null || isNaN(x) || x === 0) return '-';
  if (x >= 1e8) return (x / 1e8).toFixed(2) + '\u4ebf'; if (x >= 1e4) return (x / 1e4).toFixed(2) + '\u4e07'; return x.toFixed(0);
}
var STATUS = { pre: '\u76d8\u524d', auction: '\u96c6\u5408\u7ade\u4ef7', open: '\u4ea4\u6613\u4e2d', lunch: '\u5348\u95f4\u4f11\u5e02', closed: '\u5df2\u6536\u76d8', holiday: '\u4f11\u5e02' };
function drawQuote() {
  var el = $('quoteBar'); if (!el) return;
  var q = state.quote;
  if (!q || q.code.slice(2) !== state.code.replace(/^(sh|sz|bj)/, '')) { el.innerHTML = '<div class="muted" style="margin-bottom:8px">\u5b9e\u65f6\u62a5\u4ef7\u52a0\u8f7d\u4e2d\u2026</div>'; return; }
  var c = sgn(q.change), t = String(q.time), tm = t.length === 14 ? t.slice(8, 10) + ':' + t.slice(10, 12) + ':' + t.slice(12, 14) : '';
  function cell(k, v, cls) { return '<div><span>' + k + '</span><b' + (cls ? ' class="' + cls + '"' : '') + '>' + v + '</b></div>'; }
  var lvl = ['\u4e00', '\u4e8c', '\u4e09', '\u56db', '\u4e94'], book = '<table class="book">';
  for (var i = 4; i >= 0; i--) book += '<tr><td class="muted">\u5356' + lvl[i] + '</td><td class="down">' + (q.ask[i][0] == null ? '-' : fx(q.ask[i][0], 2)) + '</td><td>' + (q.ask[i][1] == null ? '' : q.ask[i][1]) + '</td></tr>';
  for (i = 0; i < 5; i++) book += '<tr><td class="muted">\u4e70' + lvl[i] + '</td><td class="up">' + (q.bid[i][0] == null ? '-' : fx(q.bid[i][0], 2)) + '</td><td>' + (q.bid[i][1] == null ? '' : q.bid[i][1]) + '</td></tr>';
  book += '</table>';
  el.innerHTML = '<div class="qbar"><div class="qmain"><div class="qpx ' + c + '">' + fx(q.price, 2) + '</div><div class="' + c + '">' + (q.change > 0 ? '+' : '') + fx(q.change, 2) + '\u3000' + (q.pct > 0 ? '+' : '') + pct(q.pct) + '</div>' +
    '<div class="muted" style="font-size:12px;margin-top:4px"><span class="st ' + q.status + '">' + (STATUS[q.status] || q.status) + '</span> ' + (tm ? tm + ' \u66f4\u65b0' : '') + '<br>\u6570\u636e\u6e90\u5ef6\u8fdf\u6570\u79d2\uff0c\u4ec5\u4f9b\u53c2\u8003</div></div>' +
    '<div class="kv qkv">' + cell('\u4eca\u5f00', fx(q.open, 2), sgn(q.open - q.prevClose)) + cell('\u6700\u9ad8', fx(q.high, 2), sgn(q.high - q.prevClose)) + cell('\u6700\u4f4e', fx(q.low, 2), sgn(q.low - q.prevClose)) + cell('\u6628\u6536', fx(q.prevClose, 2)) +
    cell('\u6210\u4ea4\u91cf', big(q.volume / 100) + '\u624b') + cell('\u6210\u4ea4\u989d', big(q.amount)) + cell('\u6362\u624b\u7387', pct(q.turnover)) + cell('\u91cf\u6bd4', fx(q.volRatio, 2)) +
    cell('\u5e02\u76c8\u7387(\u52a8)', fx(q.pe, 2)) + cell('\u5e02\u51c0\u7387', fx(q.pb, 2)) + cell('\u6da8\u505c\u4ef7', fx(q.limitUp, 2), 'up') + cell('\u8dcc\u505c\u4ef7', fx(q.limitDown, 2), 'down') + cell('\u603b\u5e02\u503c', big(q.mktCap)) + '</div>' + book + '</div>';
}
function refreshLive() {
  var s = curSym(); if (!s || s.source !== 'sina' || state.tab !== 'chart' || !$('kcv')) return Promise.resolve();
  var code = s.code, per = K.per, ps = [];
  ps.push(api('/api/quote?code=' + encodeURIComponent(code)).then(function (q) { if (state.code === code) { state.quote = q; drawQuote(); } })
    .catch(function (e) { if (state.code === code && $('quoteBar')) $('quoteBar').innerHTML = '<div class="warn" style="margin-bottom:8px">\u5b9e\u65f6\u62a5\u4ef7\u83b7\u53d6\u5931\u8d25\uff1a' + esc(e.message) + '</div>'; }));
 if (per === 'D' && state.bars && isTrading() && Date.now() - (state.barsAt || 0) > 14000) {
    ps.push(api('/api/bars?code=' + encodeURIComponent(code) + '&limit=3000&live=1').then(function (d) {
      if (state.code !== code || K.per !== 'D') return;
      var old = state.bars ? state.bars.close.length : 0; if (K.end >= old) K.end = 0;
      state.bars = d; state.barsAt = Date.now(); drawChart();
    }).catch(function () {}));
  }
  if (per === 'T') {
    ps.push(api('/api/intraday?code=' + encodeURIComponent(code)).then(function (d) { if (state.code === code && K.per === 'T') { state.intra = d; drawChart(); } })
      .catch(function (e) { if ($('kinfo')) $('kinfo').innerHTML = '<span class="warn">\u5206\u65f6\u6570\u636e\u83b7\u53d6\u5931\u8d25\uff1a' + esc(e.message) + '</span>'; }));
  } else if (per !== 'D') {
    ps.push(api('/api/minute?code=' + encodeURIComponent(code) + '&period=' + per + '&limit=1500').then(function (d) {
      if (state.code !== code || K.per !== per) return;
      var old = state.mbars ? state.mbars.close.length : 0; if (K.end >= old) K.end = 0;
      state.mbars = d; drawChart();
    }).catch(function (e) { if ($('kinfo')) $('kinfo').innerHTML = '<span class="warn">\u5206\u949f\u7ebf\u83b7\u53d6\u5931\u8d25\uff1a' + esc(e.message) + '</span>'; }));
  }
  return Promise.all(ps);
}
function refreshSidebar() {
  var codes = state.symbols.filter(function (x) { return x.source === 'sina' && (x.watch || x.code === state.code); }).map(function (x) { return x.code; });
  if (!codes.length) return;
  api('/api/quotes?codes=' + encodeURIComponent(codes.join(','))).then(function (list) {
    list.forEach(function (q) { state.symbols.forEach(function (x) { if (x.code === q.code) { x.close = q.price; x.chg = q.pct; } }); });
    drawSymbols();
  }).catch(function () {});
}
function mIdx(hm) { var t = Math.floor(hm / 100) * 60 + hm % 100; return t <= 690 ? t - 570 : t - 780 + 120; }
function drawT() {
  var D = state.intra, cv = $('kcv'), info = $('kinfo');
  if (!D || !cv) return;
  var H = 520, S = setup(cv, H), g = S.g, W = S.w, x0 = 6, x1 = W - 58, pTop = 10, pBot = 350, vTop = 372, vBot = H - 24, n = D.t.length, pc = D.prevClose, i;
  if (!n) { g.fillStyle = MUT; g.textAlign = 'center'; g.fillText('\u6682\u65e0\u5206\u65f6\u6570\u636e\uff08\u76d8\u524d\u6216\u975e\u4ea4\u6613\u65e5\uff09', W / 2, 120); info.innerHTML = ''; return; }
  var mx = pc * 0.005, vmax = 0;
  for (i = 0; i < n; i++) { mx = Math.max(mx, Math.abs(D.p[i] - pc), Math.abs(D.a[i] - pc)); vmax = Math.max(vmax, D.v[i]); }
  mx *= 1.08;
  var lo = pc - mx, hi = pc + mx, ww = x1 - x0;
  var X = function (k) { return x0 + mIdx(D.t[k]) / 240 * ww; }, Y = function (p) { return pBot - (p - lo) / (hi - lo) * (pBot - pTop); };
  [-1, -0.5, 0, 0.5, 1].forEach(function (f) {
    var p = pc + mx * f, y = Y(p);
    g.strokeStyle = f === 0 ? '#3a4655' : GRID; g.lineWidth = 1; g.setLineDash(f === 0 ? [4, 3] : []);
    g.beginPath(); g.moveTo(x0, y); g.lineTo(x1, y); g.stroke(); g.setLineDash([]);
    g.fillStyle = f > 0 ? UP : (f < 0 ? DOWN : MUT); g.textBaseline = 'middle'; g.textAlign = 'left'; g.fillText(fx(p, 2), x1 + 4, y);
    g.fillText(((p - pc) / pc * 100).toFixed(2) + '%', x0 + 3, Math.min(Math.max(y, pTop + 7), pBot - 7));
  });
  g.textAlign = 'center'; g.textBaseline = 'bottom';
  [[0, '9:30'], [60, '10:30'], [120, '11:30/13:00'], [180, '14:00'], [240, '15:00']].forEach(function (m) {
    var x = x0 + m[0] / 240 * ww; g.strokeStyle = GRID; g.beginPath(); g.moveTo(x, pTop); g.lineTo(x, vBot); g.stroke();
    g.fillStyle = MUT; g.fillText(m[1], Math.min(Math.max(x, x0 + 30), x1 - 30), vBot + 14);
  });
  var pp = [], aa = [];
  for (i = 0; i < n; i++) { pp.push([X(i), Y(D.p[i])]); aa.push([X(i), Y(D.a[i])]); }
  line(g, aa, WARN, 1.1); line(g, pp, ACC, 1.5);
  var bw = Math.max(1, ww / 240 * 0.8);
  for (i = 0; i < n; i++) {
    var prevP = i > 0 ? D.p[i - 1] : pc; g.fillStyle = D.p[i] >= prevP ? UP : DOWN;
    var vh = vmax > 0 ? D.v[i] / vmax * (vBot - vTop) : 0; g.fillRect(X(i) - bw / 2, vBot - vh, bw, vh);
  }
  g.fillStyle = MUT; g.textAlign = 'left'; g.textBaseline = 'top'; g.fillText('\u6210\u4ea4\u91cf(\u624b)', x0 + 2, vTop - 1);
  var k = n - 1;
  if (K.mx >= x0 && K.mx <= x1) {
    var slot = (K.mx - x0) / ww * 240; k = 0;
    for (i = 0; i < n; i++) { if (mIdx(D.t[i]) <= slot + 0.5) k = i; }
    var cx = X(k); g.strokeStyle = 'rgba(216,222,230,.4)'; g.beginPath(); g.moveTo(cx, pTop); g.lineTo(cx, vBot); g.stroke();
    g.beginPath(); g.moveTo(x0, Y(D.p[k])); g.lineTo(x1, Y(D.p[k])); g.stroke();
  }
  var ch = D.p[k] / pc - 1, tt = D.t[k];
  info.innerHTML = '<b>' + fdate(D.date) + ' ' + pad2(Math.floor(tt / 100)) + ':' + pad2(tt % 100) + '</b>\u3000\u4ef7 <b class="' + sgn(ch) + '">' + fx(D.p[k], 2) + '</b>\u3000\u6da8\u8dcc <span class="' + sgn(ch) + '">' + pct(ch) + '</span>\u3000<span style="color:' + WARN + '">\u5747\u4ef7 ' + fx(D.a[k], 3) + '</span>\u3000\u91cf ' + Math.round(D.v[k]) + '\u624b\u3000<span class="muted">\u6628\u6536 ' + fx(pc, 2) + '</span>';
}
function seg(id, items, cur, cb) {
  var el = $(id); el.innerHTML = '';
  items.forEach(function (it) {
    var b = document.createElement('button'); b.className = 'seg' + (it[0] === cur ? ' on' : ''); b.textContent = it[1];
    b.onclick = function () { Array.prototype.forEach.call(el.children, function (c) { c.className = 'seg'; }); b.className = 'seg on'; cb(it[0]); };
    el.appendChild(b);
  });
}
function loadBars() {
  if (state.bars && state.bars.code === state.code) return Promise.resolve();
  var cs = curSym(), lv = cs && cs.source === 'sina' ? '&live=1' : '';
  return api('/api/bars?code=' + encodeURIComponent(state.code) + '&limit=3000' + lv).then(function (d) { state.bars = d; state.barsAt = Date.now(); K.end = 0; });
}

/* ---------------- Prediction tab ---------------- */
var MODEL_NAME = { prior: '\u57fa\u51c6\uff1a\u5386\u53f2\u6da8\u8dcc\u7387', momentum: '\u52a8\u91cf\u89c4\u5219', logistic: '\u903b\u8f91\u56de\u5f52', mlp: '\u795e\u7ecf\u7f51\u7edc(MLP)', volnorm: '\u6ce2\u52a8\u7387\u6b63\u6001' };

function renderPredict() {
  var root = $('tab-predict');
  var cs = curSym(), live = !!cs && cs.source === 'sina';
  if (!live) state.pper = 'D';
  root.innerHTML = '<div class="toolbar">' + (live ? '<span class="muted">K\u7ebf\u5468\u671f</span><span id="pperSeg"></span>' : '') + '<span class="muted">\u9884\u6d4b\u8de8\u5ea6\uff08<span id="hUnit"></span>\uff09</span><span id="hSeg"></span>' +
    '<span class="muted" id="hNote"></span></div><div id="predBody"><div class="loading">\u6b63\u5728\u505a\u6eda\u52a8\u9a8c\u8bc1\u5e76\u62df\u5408\u6a21\u578b\uff08\u9996\u6b21\u7ea6\u6570\u79d2\uff09\u2026</div></div>';
  function unitTxt() { $('hUnit').textContent = state.pper === 'D' ? '\u4ea4\u6613\u65e5' : '\u6839' + state.pper + '\u5206\u949fK\u7ebf'; }
  if (live) seg('pperSeg', [['D', '\u65e5\u7ebf'], ['60', '60\u5206'], ['30', '30\u5206'], ['15', '15\u5206'], ['5', '5\u5206']], state.pper, function (v) { state.pper = v; unitTxt(); loadPredict(); });
  unitTxt();
  seg('hSeg', [['1', '1'], ['3', '3'], ['5', '5'], ['10', '10'], ['20', '20']], String(state.h), function (v) { state.h = parseInt(v, 10); loadPredict(); });
  loadPredict();
}
function loadPredict() {
  var code = state.code, h = state.h, per = state.pper, key = code + '|' + h + '|' + per, body = $('predBody');
  var cached = state.pred[key];
  if (cached && (per === 'D' || Date.now() - cached.at < 300000)) { drawPredict(cached.d); return; }
  body.innerHTML = '<div class="loading">\u6b63\u5728\u505a\u6eda\u52a8\u9a8c\u8bc1\u5e76\u62df\u5408\u6a21\u578b\uff08\u9996\u6b21\u7ea6\u6570\u79d2' + (per === 'D' ? '' : '\uff0c\u5206\u949f\u7ebf\u6837\u672c\u66f4\u591a\u3001\u7ea6 5\u201310 \u79d2') + '\uff09\u2026</div>';
  api('/api/predict?code=' + encodeURIComponent(code) + '&h=' + h + '&fan=20' + (per === 'D' ? '' : '&per=' + per)).then(function (d) {
    state.pred[key] = { d: d, at: Date.now() };
    if (state.code === code && state.h === h && state.pper === per && state.tab === 'predict') drawPredict(d);
  }).catch(function (e) { if ($('predBody')) $('predBody').innerHTML = '<div class="err">' + esc(e.message) + '</div>'; });
}
function drawPredict(d) {
  var body = $('predBody'); if (!body) return;
  if (d.insufficient) {
    body.innerHTML = '<div class="card"><b>\u5386\u53f2\u6570\u636e\u4e0d\u8db3</b><p class="muted">\u6eda\u52a8\u9a8c\u8bc1\u9700\u8981\u81f3\u5c11\u7ea6 ' + d.minBars + ' \u6839' + (d.period ? d.period + '\u5206\u949fK\u7ebf\u3002\u6570\u636e\u6e90\u5bf9\u5206\u949f\u7ebf\u7684\u5386\u53f2\u957f\u5ea6\u6709\u9650\uff0c\u8bf7\u6362\u66f4\u957f\u7684\u5468\u671f\uff08\u5982 60 \u5206\u949f\u6216\u65e5\u7ebf\uff09\u3002' : '\u65e5\u7ebf\u3002\u8bf7\u5bfc\u5165\u66f4\u957f\u7684\u5386\u53f2 CSV\u3002') + '</p></div>';
    return;
  }
  var best = null; d.models.forEach(function (m) { if (m.id === 'logistic' || m.id === 'mlp') { if (!best || m.z > best.z) best = m; } });
  var per = d.period, UN = per ? '\u6839 ' + per + ' \u5206\u949fK\u7ebf' : '\u4e2a\u4ea4\u6613\u65e5', UD = per ? '\u6bcf\u6839K\u7ebf' : '\u65e5';
  var vtxt = {
    none: '<b>\u672a\u53d1\u73b0\u53ef\u9760\u7684\u9884\u6d4b\u80fd\u529b</b><br>\u5728 ' + d.testBars + ' ' + (per ? '\u6839\u6837\u672c\u5916K\u7ebf' : '\u4e2a\u6837\u672c\u5916\u4ea4\u6613\u65e5') + '\uff08\u6709\u6548\u72ec\u7acb\u6837\u672c\u7ea6 ' + fx(best.nEff, 0) + '\uff09\u91cc\uff0c\u5b66\u4e60\u578b\u6a21\u578b\u7684\u65b9\u5411\u547d\u4e2d\u7387\u4e0e\u6982\u7387\u8d28\u91cf\u6ca1\u6709\u663e\u8457\u4f18\u4e8e\u201c\u53ea\u731c\u5386\u53f2\u6da8\u8dcc\u7387\u201d\u7684\u57fa\u51c6\u3002\u4e0b\u65b9\u7684\u6982\u7387\u5e94\u89c6\u4e3a\u63a5\u8fd1\u57fa\u51c6\uff0c\u4e0d\u5b9c\u636e\u6b64\u4ea4\u6613\u3002\u8fd9\u7b26\u5408\u5e02\u573a\u7684\u5e38\u8bc6\uff1a\u591a\u6570\u80a1\u7968\u7684\u6da8\u8dcc\u65b9\u5411\u51e0\u4e4e\u65e0\u6cd5\u7531\u5386\u53f2\u4ef7\u91cf\u9884\u6d4b\uff0c\u201c\u6ca1\u6709\u4fe1\u53f7\u201d\u672c\u8eab\u5c31\u662f\u6709\u4ef7\u503c\u7684\u7ed3\u8bba\u3002' + (per ? '<br><span class="muted">\u5206\u949f\u7ebf\u8bf4\u660e\uff1a\u6570\u636e\u6e90\u53ea\u63d0\u4f9b\u6700\u8fd1\u7ea6 ' + Math.round(d.testBars / (240 / per)) + ' \u4e2a\u4ea4\u6613\u65e5\u7684\u9a8c\u8bc1\u6837\u672c\uff0c\u6837\u672c\u504f\u77ed\u3001\u7ed3\u8bba\u7684\u4e0d\u786e\u5b9a\u6027\u66f4\u5927\uff1b\u5206\u949f\u7ea7\u4ef7\u683c\u8fd8\u53d7\u4e70\u5356\u4ef7\u5dee\u548c\u4ea4\u6613\u6210\u672c\u5f71\u54cd\uff0c\u5373\u4f7f\u6709\u5fae\u5f31\u4fe1\u53f7\u4e5f\u5f88\u96be\u8986\u76d6\u6210\u672c\u3002</span>' : ''),
    weak: '<b>\u51fa\u73b0\u5fae\u5f31\u4fe1\u53f7\uff08\u9700\u8c28\u614e\uff09</b><br>\u6700\u4f18\u5b66\u4e60\u578b\u6a21\u578b\u5728\u6837\u672c\u5916 z=' + fx(best.z, 2) + '\uff0cBrier \u6280\u80fd\u5206 ' + fx(best.brierSkill * 100, 1) + '%\u3002\u8be5\u6c34\u5e73\u5728\u591a\u53ea\u6807\u7684\u3001\u591a\u4e2a\u5468\u671f\u7684\u53cd\u590d\u68c0\u9a8c\u4e0b\u5f88\u5bb9\u6613\u662f\u5076\u7136\u3002\u5efa\u8bae\u6362\u5468\u671f/\u6362\u6807\u7684\u590d\u6838\uff0c\u5e76\u7528\u56de\u6d4b\u68c0\u9a8c\u6263\u9664\u6210\u672c\u540e\u662f\u5426\u4ecd\u6709\u4f18\u52bf\u3002',
    notable: '<b>\u6837\u672c\u5916\u9a8c\u8bc1\u663e\u793a\u663e\u8457\u7684\u65b9\u5411\u4fe1\u53f7</b><br>\u6700\u4f18\u5b66\u4e60\u578b\u6a21\u578b z=' + fx(best.z, 2) + '\uff0c\u547d\u4e2d\u7387 ' + pct(best.accuracy, 1) + '\uff08\u57fa\u51c6 ' + pct(best.majorityAccuracy, 1) + '\uff09\uff0cBrier \u6280\u80fd\u5206 ' + fx(best.brierSkill * 100, 1) + '%\u3002\u8fd9\u8bf4\u660e\u8be5\u5e8f\u5217\u5728\u5386\u53f2\u4e0a\u5b58\u5728\u53ef\u5b66\u4e60\u7684\u7ed3\u6784\uff0c\u4f46\u4e0d\u4fdd\u8bc1\u672a\u6765\u6301\u7eed\uff0c\u4e14\u9700\u8ba1\u5165\u4ea4\u6613\u6210\u672c\uff1b\u8bf7\u5230\u201c\u7b56\u7565\u56de\u6d4b\u201d\u9875\u9a8c\u8bc1\u53ef\u4ea4\u6613\u6027\u3002'
  }[d.verdict];
  var ens = d.ensembleProbUp, ensCls = ens > 0.5 ? 'up' : 'down', noSkill = d.verdict === 'none';
  var base = null; d.models.forEach(function (m) { if (m.id === 'prior') base = m.probUp; });
  var h = '<div class="verdict ' + d.verdict + '">' + vtxt + '</div>';
  h += '<div class="grid2"><div class="card"><h3>\u672a\u6765 ' + d.horizon + ' ' + UN + '\u4e0a\u6da8\u6982\u7387 <span class="muted" style="font-weight:400">\u622a\u81f3 ' + fdate(d.asOf) + (per ? ' ' + pad2(Math.floor(d.asOfTime / 100)) + ':' + pad2(d.asOfTime % 100) : '') + '\uff0c' + (per ? '\u6700\u65b0\u4ef7 ' : '\u6536\u76d8 ') + fx(d.lastClose, 2) + '</span></h3>' +
    '<div style="font-size:30px" class="' + (noSkill ? 'muted' : ensCls) + '">' + pct(ens, 1) + (noSkill ? ' <span class="warn" style="font-size:13px">\u6a21\u578b\u65e0\u663e\u8457\u9884\u6d4b\u529b\uff0c\u6b64\u6570\u5b57\u4e0d\u53ef\u7528\u4e8e\u4ea4\u6613\uff1b\u5386\u53f2\u57fa\u51c6\u4e0a\u6da8\u7387 ' + pct(base, 1) + '</span>' : '') + '</div>' +
    '<div class="meter' + (noSkill ? ' dim' : '') + '"><i style="left:' + (ens * 100).toFixed(1) + '%"></i></div>' +
    '<div class="legend"><span>0% \u770b\u8dcc</span><span style="flex:1;text-align:center">50%</span><span>100% \u770b\u6da8</span></div>' +
    '<div class="note">\u53d6\u903b\u8f91\u56de\u5f52\u4e0e\u795e\u7ecf\u7f51\u7edc\u7684\u5e73\u5747\u3002\u8d8a\u63a5\u8fd1 50%\uff0c\u8bf4\u660e\u201c\u6ca1\u6709\u4fe1\u606f\u201d\uff1b\u8be5\u6982\u7387\u5df2\u7528\u6eda\u52a8\u9a8c\u8bc1\u68c0\u9a8c\u5176\u6821\u51c6\u7a0b\u5ea6\uff08\u53f3\u4fa7\u53ef\u9760\u6027\u56fe\uff09\u3002</div>' +
    '<h3 style="margin-top:16px">\u6ce2\u52a8\u533a\u95f4\uff08EWMA \u6ce2\u52a8\u7387\uff09</h3><div class="kv">' +
    '<div><span>' + UD + '\u6ce2\u52a8\u7387</span><b>' + pct(d.volDaily, 2) + '</b></div><div><span>\u5e74\u5316\u6ce2\u52a8\u7387</span><b>' + pct(d.volAnnual, 1) + '</b></div>' +
    '<div><span>80%\u533a\u95f4\u6837\u672c\u5916\u8986\u76d6\u7387</span><b>' + pct(d.interval.coverage, 1) + '</b></div><div><span>\u5e73\u5747\u533a\u95f4\u5bbd\u5ea6</span><b>' + pct(d.interval.meanWidth, 1) + '</b></div></div>' +
    '<div class="note">\u533a\u95f4\u8986\u76d6\u7387\u5e94\u63a5\u8fd1\u540d\u4e49\u503c 80%\uff1b\u660e\u663e\u504f\u79bb\u8bf4\u660e\u6ce2\u52a8\u7387\u4f30\u8ba1\u5931\u51c6\u3002\u4ef7\u683c\u65b9\u5411\u51e0\u4e4e\u4e0d\u53ef\u9884\u6d4b\uff0c\u4f46\u6ce2\u52a8\u7387\u6709\u8f83\u5f3a\u7684\u53ef\u9884\u6d4b\u6027\u3002</div></div>' +
    '<div class="card"><h3>\u4ef7\u683c\u6247\u5f62\u56fe\uff08\u672a\u6765 20 ' + (per ? '\u6839K\u7ebf' : '\u65e5') + ' 80% / 95% \u533a\u95f4\uff09</h3><canvas id="fanCv"></canvas><div class="note">\u4e2d\u7ebf\u4ec5\u4e3a\u6536\u7f29\u540e\u7684\u5386\u53f2\u6f02\u79fb\uff0c\u533a\u95f4\u7531\u6ce2\u52a8\u7387\u51b3\u5b9a\uff1b\u4e0d\u662f\u4ef7\u683c\u76ee\u6807\u3002</div></div></div>';
  h += '<div class="card"><h3>\u591a\u6a21\u578b\u6837\u672c\u5916\u5bf9\u6bd4\uff08\u6eda\u52a8\u91cd\u8bad\uff0c' + fdate(d.testStart) + ' \u8d77' + (per ? '\uff0c' + per + ' \u5206\u949fK\u7ebf' : '') + '\uff09</h3><table><tr><th>\u6a21\u578b</th><th>\u5f53\u524dP(\u6da8)</th><th>\u547d\u4e2d\u7387</th><th>\u591a\u6570\u7c7b\u57fa\u51c6</th><th>Brier</th><th>Brier\u6280\u80fd\u5206</th><th>z\u503c</th><th>\u6709\u6548\u6837\u672c</th><th>\u9ad8\u4fe1\u5fc3(\u226555%)\u540e\u5747\u6536\u76ca</th><th>\u4f4e\u4fe1\u5fc3(\u226445%)\u540e\u5747\u6536\u76ca</th></tr>';
  d.models.forEach(function (m) {
    var skill = m.brierSkill;
    h += '<tr' + (best && m.id === best.id ? ' class="hl"' : '') + '><td>' + MODEL_NAME[m.id] + '</td><td class="' + (m.probUp > 0.5 ? 'up' : 'down') + '">' + pct(m.probUp, 1) + '</td><td>' + pct(m.accuracy, 1) + '</td><td>' + pct(m.majorityAccuracy, 1) +
      '</td><td>' + fx(m.brier, 4) + '</td><td class="' + (skill > 0 ? 'up' : '') + '">' + fx(skill * 100, 2) + '%</td><td>' + fx(m.z, 2) + '</td><td>' + fx(m.nEff, 0) +
      '</td><td>' + (m.nHigh ? pct(m.avgRetHigh, 2) + ' (' + m.nHigh + ')' : '-') + '</td><td>' + (m.nLow ? pct(m.avgRetLow, 2) + ' (' + m.nLow + ')' : '-') + '</td></tr>';
  });
  h += '</table><div class="note">z \u503c\u7528\u6709\u6548\u6837\u672c\u6570 n/\u5468\u671f \u6298\u7b97\uff08\u91cd\u53e0\u6807\u7b7e\u4e0d\u72ec\u7acb\uff09\u3002Brier \u6280\u80fd\u5206 &gt; 0 \u624d\u8868\u793a\u4f18\u4e8e\u57fa\u51c6\uff1bz \u2265 2.6 \u4e14\u6280\u80fd\u5206 &gt; 0 \u624d\u5224\u4e3a\u201c\u663e\u8457\u201d\uff0c\u4ee5\u62b5\u6d88\u540c\u65f6\u68c0\u9a8c\u591a\u4e2a\u6a21\u578b\u5e26\u6765\u7684\u5076\u7136\u6027\u3002</div></div>';
  h += '<div class="grid2"><div class="card"><h3>\u53ef\u9760\u6027\u56fe\uff08\u9884\u6d4b\u6982\u7387 vs \u5b9e\u9645\u4e0a\u6da8\u9891\u7387\uff09</h3><canvas id="calCv"></canvas><div class="legend" id="calLegend"></div><div class="note">\u70b9\u8d8a\u8d34\u8fd1\u5bf9\u89d2\u7ebf\uff0c\u6982\u7387\u8d8a\u53ef\u4fe1\uff1b\u70b9\u7684\u5927\u5c0f\u4ee3\u8868\u6837\u672c\u6570\u3002</div></div>' +
    '<div class="card"><h3>\u5f53\u524d\u7279\u5f81\u5feb\u7167</h3><div class="kv" id="featKv"></div></div></div>';
  body.innerHTML = h;
  var fk = $('featKv'), FN = { ret1: '1\u65e5\u6536\u76ca', ret5: '5\u65e5\u6536\u76ca', ret10: '10\u65e5\u6536\u76ca', ret20: '20\u65e5\u6536\u76ca', rsi14: 'RSI\u504f\u79bb', macdHist: 'MACD\u67f1/\u4ef7', vol20: '20\u65e5\u6ce2\u52a8', volRatio5v20: '\u77ed\u957f\u6ce2\u52a8\u6bd4', dist20: '\u8ddd20\u65e5\u5747\u7ebf', volumeRatio: '\u91cf\u6bd4\u504f\u79bb', range60: '60\u65e5\u533a\u95f4\u4f4d\u7f6e' };
  if (per) Object.keys(FN).forEach(function (k) { FN[k] = FN[k].replace(/\u65e5/g, '\u6839'); });
  fk.innerHTML = Object.keys(d.features).map(function (k) { return '<div><span>' + (FN[k] || k) + '</span><b>' + fx(d.features[k], 4) + '</b></div>'; }).join('');
  drawFan(d); drawCal(d);
}
function drawFan(d) {
  var cv = $('fanCv'); if (!cv || !d.hist) return;
  var S = setup(cv, 300), g = S.g, W = S.w, H = S.h, B = { close: d.hist }, N = B.close.length, hist = N;
  var lo = Infinity, hi = -Infinity, i;
  for (i = N - hist; i < N; i++) { lo = Math.min(lo, B.close[i]); hi = Math.max(hi, B.close[i]); }
  d.fan.forEach(function (f) { lo = Math.min(lo, f.lo95); hi = Math.max(hi, f.hi95); });
  var pad = (hi - lo) * 0.06; lo -= pad; hi += pad;
  var x0 = 6, x1 = W - 52, y0 = 8, y1 = H - 22, tot = hist + d.fan.length;
  var X = function (k) { return x0 + k / (tot - 1) * (x1 - x0); }, Y = function (p) { return y1 - (p - lo) / (hi - lo) * (y1 - y0); };
  yAxis(g, x0, x1, y0, y1, lo, hi, 2);
  function band(ka, kb, c) {
    g.beginPath(); g.moveTo(X(hist - 1), Y(d.lastClose));
    d.fan.forEach(function (f, k) { g.lineTo(X(hist + k), Y(f[ka])); });
    for (var k = d.fan.length - 1; k >= 0; k--) g.lineTo(X(hist + k), Y(d.fan[k][kb]));
    g.closePath(); g.fillStyle = c; g.fill();
  }
  band('hi95', 'lo95', 'rgba(79,140,255,.15)'); band('hi80', 'lo80', 'rgba(79,140,255,.28)');
  var pts = []; for (i = 0; i < hist; i++) pts.push([X(i), Y(B.close[N - hist + i])]);
  line(g, pts, TXT, 1.4);
  pts = [[X(hist - 1), Y(d.lastClose)]]; d.fan.forEach(function (f, k) { pts.push([X(hist + k), Y(f.mid)]); });
  line(g, pts, ACC, 1.4);
  g.strokeStyle = MUT; g.setLineDash([3, 3]); g.beginPath(); g.moveTo(X(hist - 1), y0); g.lineTo(X(hist - 1), y1); g.stroke(); g.setLineDash([]);
  g.fillStyle = MUT; g.textAlign = 'center'; g.fillText(d.period ? '\u73b0\u5728' : '\u4eca\u5929', X(hist - 1), H - 8); g.fillText('+' + d.fan.length + (d.period ? '\u6839' : '\u65e5'), X(tot - 1) - 10, H - 8);
}
function drawCal(d) {
  var cv = $('calCv'); if (!cv) return;
  var S = setup(cv, 300), g = S.g, W = S.w, H = S.h, x0 = 36, x1 = W - 14, y0 = 10, y1 = H - 28;
  var X = function (p) { return x0 + p * (x1 - x0); }, Y = function (p) { return y1 - p * (y1 - y0); };
  g.strokeStyle = GRID; g.fillStyle = MUT; g.textAlign = 'right'; g.textBaseline = 'middle';
  for (var t = 0; t <= 1.001; t += 0.25) { g.beginPath(); g.moveTo(x0, Y(t)); g.lineTo(x1, Y(t)); g.stroke(); g.fillText((t * 100).toFixed(0) + '%', x0 - 4, Y(t)); }
  g.textAlign = 'center'; g.textBaseline = 'top';
  for (t = 0; t <= 1.001; t += 0.25) g.fillText((t * 100).toFixed(0) + '%', X(t), y1 + 4);
  g.strokeStyle = MUT; g.setLineDash([4, 4]); g.beginPath(); g.moveTo(X(0), Y(0)); g.lineTo(X(1), Y(1)); g.stroke(); g.setLineDash([]);
  var show = ['logistic', 'mlp', 'volnorm'], cols = [ACC, WARN, '#a855f7'], lg = '';
  show.forEach(function (id, si) {
    var m = null; d.models.forEach(function (x) { if (x.id === id) m = x; }); if (!m) return;
    var pts = m.calibration.map(function (c) { return [X(c.p), Y(c.obs)]; });
    line(g, pts, cols[si], 1.2);
    m.calibration.forEach(function (c) { g.beginPath(); g.arc(X(c.p), Y(c.obs), Math.max(2.5, Math.min(7, Math.sqrt(c.n) / 3)), 0, 7); g.fillStyle = cols[si]; g.globalAlpha = 0.8; g.fill(); g.globalAlpha = 1; });
    lg += '<span><i style="background:' + cols[si] + '"></i>' + MODEL_NAME[id] + '</span>';
  });
  $('calLegend').innerHTML = lg;
}

/* ---------------- Backtest tab ---------------- */
var STRATS = [['buy_hold', '\u4e70\u5165\u6301\u6709'], ['ma_cross', '\u5747\u7ebf\u4ea4\u53c9(10/30)'], ['rsi', 'RSI\u53cd\u8f6c(30/70)'], ['macd', 'MACD\u91d1\u53c9'], ['boll', '\u5e03\u6797\u5e26\u56de\u5f52'], ['model', '\u6a21\u578b\u4fe1\u53f7(P\u226555%)']];
var SNAME = {}; STRATS.forEach(function (s) { SNAME[s[0]] = s[1]; });

function renderBacktest() {
  var root = $('tab-backtest');
  root.innerHTML = '<div class="card"><div class="toolbar"><span class="muted">\u7b56\u7565</span><select id="btStrat">' +
    STRATS.map(function (s) { return '<option value="' + s[0] + '">' + s[1] + '</option>'; }).join('') + '</select>' +
    '<span id="btParams"></span><button id="btRun">\u8fd0\u884c\u56de\u6d4b</button><button id="btAll" class="ghost">\u5bf9\u6bd4\u5168\u90e8\u7b56\u7565</button></div>' +
    '<div class="note">\u6210\u672c\uff1a\u4f63\u91d1 0.025%\uff08\u53cc\u8fb9\uff09\u3001\u5370\u82b1\u7a0e 0.05%\uff08\u5356\u51fa\uff09\u3001\u6ed1\u70b9 0.05%\uff08\u53cc\u8fb9\uff09\uff1b\u4fe1\u53f7\u6b21\u65e5\u5f00\u76d8\u4ef7\u6210\u4ea4\uff0c\u4ec5\u505a\u591a\uff0c\u4e0d\u4f7f\u7528\u672a\u6765\u6570\u636e\u3002\u6a21\u578b\u4fe1\u53f7\u7b56\u7565\u4f7f\u7528\u6eda\u52a8\u9a8c\u8bc1\u671f\u7684\u6837\u672c\u5916\u9884\u6d4b\u3002</div></div><div id="btBody"></div>';
  var sel = $('btStrat');
  function params() {
    var s = sel.value, h = '';
    if (s === 'ma_cross') h = '\u5feb <input id="p_fast" size="3" value="10"> \u6162 <input id="p_slow" size="3" value="30">';
    if (s === 'rsi') h = '\u5468\u671f <input id="p_period" size="3" value="14"> \u4e70\u5165\u7ebf <input id="p_lo" size="3" value="30"> \u5356\u51fa\u7ebf <input id="p_hi" size="3" value="70">';
    if (s === 'boll') h = '\u5468\u671f <input id="p_period" size="3" value="20"> \u500d\u6570 <input id="p_k" size="3" value="2">';
    if (s === 'model') h = '\u9884\u6d4b\u5468\u671f <input id="p_h" size="3" value="5"> \u9608\u503c <input id="p_threshold" size="4" value="0.55">';
    $('btParams').innerHTML = h;
  }
  sel.onchange = params; params();
  $('btRun').onclick = runBacktest; $('btAll').onclick = runCompare;
}
function paramQuery() {
  var q = '';
  ['fast', 'slow', 'period', 'lo', 'hi', 'k', 'h', 'threshold'].forEach(function (k) { var el = $('p_' + k); if (el && el.value !== '') q += '&' + k + '=' + encodeURIComponent(el.value); });
  return q;
}
function mrow(label, m) {
  return '<tr><td>' + label + '</td><td class="' + sgn(m.totalReturn) + '">' + pct(m.totalReturn, 1) + '</td><td>' + pct(m.cagr, 1) + '</td><td>' + fx(m.sharpe, 2) + '</td><td class="down">' + pct(m.maxDrawdown, 1) + '</td><td>' + pct(m.exposure, 0) +
    '</td><td class="muted">' + pct(m.benchTotalReturn, 1) + '</td><td class="muted">' + pct(m.benchMaxDrawdown, 1) + '</td></tr>';
}
function runBacktest() {
  var s = $('btStrat').value, body = $('btBody');
  body.innerHTML = '<div class="loading">\u56de\u6d4b\u4e2d\u2026\uff08\u6a21\u578b\u7b56\u7565\u9996\u6b21\u9700\u5148\u505a\u6eda\u52a8\u9a8c\u8bc1\uff09</div>';
  api('/api/backtest?code=' + encodeURIComponent(state.code) + '&strategy=' + s + paramQuery()).then(function (d) {
    var h = '<div class="card"><h3>' + SNAME[s] + ' vs \u4e70\u5165\u6301\u6709 <span class="muted" style="font-weight:400">\u81ea ' + fdate(d.startDate) + '\uff0c\u5171 ' + d.trades + ' \u7b14\u4ea4\u6613\uff0c\u80dc\u7387 ' + pct(d.winRate, 0) + '\uff0c\u5e73\u5747\u6301\u6709 ' + fx(d.avgHold, 1) + ' \u65e5</span></h3>' +
      '<canvas id="eqCv"></canvas><div class="legend"><span><i style="background:' + ACC + '"></i>\u7b56\u7565\u51c0\u503c</span><span><i style="background:' + MUT + '"></i>\u4e70\u5165\u6301\u6709</span><span>\u865a\u7ebf\uff1a\u524d 70% / \u540e 30% \u5206\u754c</span></div></div>' +
      '<div class="card"><h3>\u7ee9\u6548\uff08\u5206\u6bb5\uff09</h3><table><tr><th>\u533a\u95f4</th><th>\u7b56\u7565\u6536\u76ca</th><th>\u5e74\u5316</th><th>\u590f\u666e</th><th>\u6700\u5927\u56de\u64a4</th><th>\u6301\u4ed3\u5360\u6bd4</th><th>\u57fa\u51c6\u6536\u76ca</th><th>\u57fa\u51c6\u56de\u64a4</th></tr>' +
      mrow('\u5168\u533a\u95f4', d.full) + mrow('\u524d 70%\uff08' + fdate(d.startDate) + ' ~ ' + fdate(d.splitDate) + '\uff09', d.early) + mrow('\u540e 30%\uff08' + fdate(d.splitDate) + ' \u8d77\uff09', d.recent) + '</table>' +
      '<div class="note">\u82e5\u201c\u524d\u6bb5\u201d\u8fdc\u597d\u4e8e\u201c\u540e\u6bb5\u201d\uff0c\u5f80\u5f80\u8bf4\u660e\u7b56\u7565\u662f\u5bf9\u5386\u53f2\u8fc7\u62df\u5408\u6216\u89c4\u5f8b\u5df2\u5931\u6548\u3002\u5355\u4e00\u56de\u6d4b\u6709\u5e78\u5b58\u8005\u4e0e\u9009\u62e9\u504f\u5dee\uff0c\u4e0d\u7b49\u4e8e\u672a\u6765\u8868\u73b0\u3002</div></div>';
    h += '<div class="card"><h3>\u6700\u8fd1\u4ea4\u6613</h3><table><tr><th>\u4e70\u5165\u65e5</th><th>\u5356\u51fa\u65e5</th><th>\u4e70\u5165\u4ef7</th><th>\u5356\u51fa\u4ef7</th><th>\u6536\u76ca</th></tr>' +
      d.tradeList.slice().reverse().map(function (t) { return '<tr><td>' + fdate(t.in) + '</td><td>' + (t.open ? '\u6301\u4ed3\u4e2d' : fdate(t.out)) + '</td><td>' + fx(t.inPx, 2) + '</td><td>' + (t.open ? '-' : fx(t.outPx, 2)) + '</td><td class="' + sgn(t.ret) + '">' + pct(t.ret, 2) + '</td></tr>'; }).join('') + '</table></div>';
    body.innerHTML = h;
    drawEquity($('eqCv'), [{ d: d.curve.d, v: d.curve.s, c: ACC, n: SNAME[s] }, { d: d.curve.d, v: d.curve.b, c: MUT, n: '\u4e70\u5165\u6301\u6709' }], d.splitDate);
  }).catch(function (e) { body.innerHTML = '<div class="err">' + esc(e.message) + '</div>'; });
}
function runCompare() {
  var body = $('btBody'); body.innerHTML = '<div class="loading">\u9010\u4e2a\u8fd0\u884c\u5168\u90e8\u7b56\u7565\uff08\u5bf9\u9f50\u5230\u540c\u4e00\u8d77\u70b9\uff09\u2026</div>';
  var results = {}, from = 0;
  var chain = api('/api/backtest?code=' + encodeURIComponent(state.code) + '&strategy=model&h=5').then(function (d) { results.model = d; from = d.startDate; }).catch(function () { from = 0; });
  STRATS.forEach(function (s) {
    if (s[0] === 'model') return;
    chain = chain.then(function () { return api('/api/backtest?code=' + encodeURIComponent(state.code) + '&strategy=' + s[0] + (from ? '&from=' + from : '')).then(function (d) { results[s[0]] = d; }).catch(function () {}); });
  });
  chain.then(function () {
    var h = '<div class="card"><h3>\u5168\u90e8\u7b56\u7565\u51c0\u503c\u5bf9\u6bd4\uff08\u540c\u4e00\u8d77\u70b9 ' + (from ? fdate(from) : '\u5404\u81ea\u8d77\u70b9') + '\uff09</h3><canvas id="eqCv"></canvas><div class="legend" id="eqLg"></div></div>' +
      '<div class="card"><h3>\u6c47\u603b</h3><table><tr><th>\u7b56\u7565</th><th>\u5168\u533a\u95f4\u6536\u76ca</th><th>\u5e74\u5316</th><th>\u590f\u666e</th><th>\u6700\u5927\u56de\u64a4</th><th>\u540e30%\u6536\u76ca</th><th>\u4ea4\u6613\u6570</th></tr>';
    var series = [], lg = '', ci = 0;
    STRATS.forEach(function (s) {
      var d = results[s[0]]; if (!d) return;
      h += '<tr><td>' + s[1] + '</td><td class="' + sgn(d.full.totalReturn) + '">' + pct(d.full.totalReturn, 1) + '</td><td>' + pct(d.full.cagr, 1) + '</td><td>' + fx(d.full.sharpe, 2) + '</td><td class="down">' + pct(d.full.maxDrawdown, 1) + '</td><td class="' + sgn(d.recent.totalReturn) + '">' + pct(d.recent.totalReturn, 1) + '</td><td>' + d.trades + '</td></tr>';
      var col = COLORS[ci++ % COLORS.length]; series.push({ d: d.curve.d, v: d.curve.s, c: col, n: s[1] });
      lg += '<span><i style="background:' + col + '"></i>' + s[1] + '</span>';
    });
    body.innerHTML = h + '</table><div class="note">\u5728\u968f\u673a\u6e38\u8d70\u578b\u6807\u7684\u4e0a\uff0c\u4efb\u4f55\u7b56\u7565\u7684\u4f18\u52a3\u4e3b\u8981\u662f\u8fd0\u6c14\uff1b\u5728\u5b58\u5728\u5747\u503c\u56de\u5f52/\u52a8\u91cf\u7ed3\u6784\u7684\u6807\u7684\u4e0a\uff0c\u5339\u914d\u7ed3\u6784\u7684\u7b56\u7565\u624d\u4f1a\u7a33\u5b9a\u5360\u4f18\u3002</div></div>';
    $('eqLg').innerHTML = lg;
    if (series.length) drawEquity($('eqCv'), series, 0);
  });
}
function drawEquity(cv, series, splitDate) {
  var S = setup(cv, 320), g = S.g, W = S.w, H = S.h, x0 = 6, x1 = W - 56, y0 = 8, y1 = H - 22;
  var lo = Infinity, hi = -Infinity;
  series.forEach(function (s) { s.v.forEach(function (v) { if (v != null) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }); });
  var logS = hi / Math.max(lo, 1e-6) > 4;
  var T = function (v) { return logS ? Math.log(Math.max(v, 1e-6)) : v; };
  var tlo = T(lo), thi = T(hi), p = (thi - tlo) * 0.05; tlo -= p; thi += p;
  var Y = function (v) { return y1 - (T(v) - tlo) / (thi - tlo) * (y1 - y0); };
  var ticks = niceTicks(logS ? lo : lo, hi, 4);
  g.fillStyle = MUT; g.textAlign = 'left'; g.textBaseline = 'middle';
  ticks.forEach(function (t) { var y = Y(t); if (y < y0 || y > y1) return; g.strokeStyle = GRID; g.beginPath(); g.moveTo(x0, y); g.lineTo(x1, y); g.stroke(); g.fillText(t.toFixed(2), x1 + 4, y); });
  if (Y(1) >= y0 && Y(1) <= y1) { g.strokeStyle = MUT; g.setLineDash([3, 3]); g.beginPath(); g.moveTo(x0, Y(1)); g.lineTo(x1, Y(1)); g.stroke(); g.setLineDash([]); }
  var ref = series[0].d, nref = ref.length;
  series.forEach(function (s) {
    var pts = s.v.map(function (v, i) { return [x0 + i / Math.max(1, s.v.length - 1) * (x1 - x0), Y(v)]; });
    line(g, pts, s.c, 1.5);
  });
  if (splitDate) {
    var idx = 0; for (var i = 0; i < nref; i++) { if (ref[i] <= splitDate) idx = i; }
    var sx = x0 + idx / Math.max(1, nref - 1) * (x1 - x0);
    g.strokeStyle = MUT; g.setLineDash([4, 4]); g.beginPath(); g.moveTo(sx, y0); g.lineTo(sx, y1); g.stroke(); g.setLineDash([]);
  }
  g.fillStyle = MUT; g.textAlign = 'center'; g.textBaseline = 'bottom';
  for (var k = 0; k < 5; k++) { var j = Math.floor((nref - 1) * k / 4); g.fillText(fdate(ref[j]).slice(2), Math.min(Math.max(x0 + j / Math.max(1, nref - 1) * (x1 - x0), 34), x1 - 28), H - 4); }
}

/* ---------------- Watchlist & alerts ---------------- */
var ALERT_TYPES = [['price_above', '\u4ef7\u683c\u9ad8\u4e8e'], ['price_below', '\u4ef7\u683c\u4f4e\u4e8e'], ['prob_above', '5\u65e5\u4e0a\u6da8\u6982\u7387\u9ad8\u4e8e'], ['prob_below', '5\u65e5\u4e0a\u6da8\u6982\u7387\u4f4e\u4e8e'], ['rsi_above', 'RSI(14) \u9ad8\u4e8e'], ['rsi_below', 'RSI(14) \u4f4e\u4e8e']];
var ANAME = {}; ALERT_TYPES.forEach(function (a) { ANAME[a[0]] = a[1]; });

function renderWatch() {
  var root = $('tab-watch');
  root.innerHTML = '<div class="card"><h3>\u81ea\u9009\u80a1</h3><div id="wlBody" class="loading">\u52a0\u8f7d\u4e2d\u2026</div></div>' +
    '<div class="card"><h3>\u4ef7\u683c / \u6307\u6807 / \u9884\u6d4b\u6982\u7387\u63d0\u9192</h3><div class="toolbar"><select id="alCode">' +
    state.symbols.map(function (s) { return '<option value="' + esc(s.code) + '"' + (s.code === state.code ? ' selected' : '') + '>' + esc(s.code + ' ' + s.name) + '</option>'; }).join('') + '</select>' +
    '<select id="alType">' + ALERT_TYPES.map(function (a) { return '<option value="' + a[0] + '">' + a[1] + '</option>'; }).join('') + '</select>' +
    '<input id="alVal" size="8" placeholder="\u9608\u503c"><button id="alAdd">\u6dfb\u52a0\u63d0\u9192</button></div><div id="alBody" class="loading">\u52a0\u8f7d\u4e2d\u2026</div>' +
    '<div class="note">\u63d0\u9192\u7531\u670d\u52a1\u7aef\u5728\u540e\u53f0\u8bc4\u4f30\uff08\u4ea4\u6613\u65f6\u6bb5\u6bcf 30 \u79d2\uff0c\u5176\u4ed6\u65f6\u95f4\u6bcf 10 \u5206\u949f\uff09\uff0c\u4e0d\u9700\u8981\u6253\u5f00\u9875\u9762\uff1b\u6761\u4ef6\u4ece\u201c\u672a\u6ee1\u8db3\u201d\u53d8\u4e3a\u201c\u6ee1\u8db3\u201d\u7684\u90a3\u4e00\u523b\u63a8\u9001\u4e00\u6b21\uff0c\u56de\u843d\u540e\u81ea\u52a8\u91cd\u65b0\u6b66\u88c5\u3002\u4ef7\u683c\u63d0\u9192\u7528\u5b9e\u65f6\u4ef7\uff0c\u6982\u7387/RSI \u57fa\u4e8e\u6700\u8fd1\u4e00\u6839\u65e5\u7ebf\uff08\u6536\u76d8 15:10 \u540e\u81ea\u52a8\u66f4\u65b0\u81ea\u9009\u771f\u5b9e\u884c\u60c5\uff09\u3002</div></div>' +
    '<div class="card"><h3>\u63a8\u9001\u901a\u9053</h3><div id="ntBody" class="loading">\u52a0\u8f7d\u4e2d\u2026</div></div>' +
    '<div class="card"><h3>\u89e6\u53d1\u8bb0\u5f55</h3><div id="evBody" class="loading">\u52a0\u8f7d\u4e2d\u2026</div></div>';
  $('alAdd').onclick = function () {
    var v = $('alVal').value;
    if (v === '' || isNaN(Number(v))) { alert('\u8bf7\u8f93\u5165\u6570\u5b57\u9608\u503c'); return; }
    post('/api/alerts/add?code=' + encodeURIComponent($('alCode').value) + '&type=' + $('alType').value + '&value=' + encodeURIComponent(v)).then(function () { $('alVal').value = ''; refreshWatch(); }).catch(function (e) { alert(e.message); });
  };
  refreshWatch();
  renderNotify();
  loadEvents(true);
}
var NKIND = [['wecom', '\u4f01\u4e1a\u5fae\u4fe1\u7fa4\u673a\u5668\u4eba'], ['dingtalk', '\u9489\u9489\u7fa4\u673a\u5668\u4eba\uff08\u5173\u952e\u8bcd\u9700\u542b\u201c\u80a1\u7968\u63d0\u9192\u201d\uff09'], ['feishu', '\u98de\u4e66\u7fa4\u673a\u5668\u4eba'], ['webhook', '\u901a\u7528 Webhook\uff08JSON\uff09']];
function renderNotify() {
  var el = $('ntBody'); if (!el) return;
  api('/api/notify').then(function (c) {
    el.className = '';
    el.innerHTML = (c.configured ? '<div style="margin-bottom:8px"><b style="color:#22c55e">\u5df2\u914d\u7f6e</b>\u3000' + esc((NKIND.filter(function (k) { return k[0] === c.kind; })[0] || ['', c.kind])[1]) + '\u3000<span class="muted">' + esc(c.host) + '</span>' +
      '\u3000<button class="small ghost" id="ntTest">\u53d1\u9001\u6d4b\u8bd5</button> <button class="small ghost" id="ntClear">\u79fb\u9664</button></div>' : '<div class="muted" style="margin-bottom:8px">\u5c1a\u672a\u914d\u7f6e\uff1a\u63d0\u9192\u53ea\u4f1a\u51fa\u73b0\u5728\u4e0b\u65b9\u8bb0\u5f55\u548c\u6d4f\u89c8\u5668\u901a\u77e5\u91cc\u3002</div>') +
      '<div class="toolbar"><select id="ntKind">' + NKIND.map(function (k) { return '<option value="' + k[0] + '"' + (k[0] === c.kind ? ' selected' : '') + '>' + k[1] + '</option>'; }).join('') + '</select>' +
      '<input id="ntUrl" type="password" size="42" placeholder="\u673a\u5668\u4eba Webhook \u5730\u5740\uff08\u4fdd\u5b58\u540e\u4e0d\u518d\u663e\u793a\uff09" autocomplete="off"><button id="ntSave">\u4fdd\u5b58</button>' +
      '<button class="ghost" id="ntBrowser">\u5f00\u542f\u6d4f\u89c8\u5668\u901a\u77e5</button></div><div id="ntMsg" class="muted"></div>' +
      '<div class="note">Webhook \u5730\u5740\u76f8\u5f53\u4e8e\u5bc6\u7801\uff0c\u53ea\u4fdd\u5b58\u5728\u670d\u52a1\u5668 data/_notify.txt\uff0c\u4e0d\u4f1a\u518d\u901a\u8fc7\u63a5\u53e3\u8fd4\u56de\u3002</div>';
    $('ntSave').onclick = function () {
      var u = $('ntUrl').value.trim(); if (!u) { $('ntMsg').textContent = '\u8bf7\u586b\u5199 Webhook \u5730\u5740'; return; }
      api('/api/notify/set?kind=' + $('ntKind').value, { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: u }).then(function () { toast('\u63a8\u9001\u901a\u9053\u5df2\u4fdd\u5b58'); renderNotify(); }).catch(function (e) { $('ntMsg').innerHTML = '<span class="err">' + esc(e.message) + '</span>'; });
    };
    $('ntBrowser').onclick = function () {
      if (!window.Notification) { $('ntMsg').textContent = '\u6b64\u6d4f\u89c8\u5668\u4e0d\u652f\u6301\u901a\u77e5'; return; }
      Notification.requestPermission().then(function (p) { $('ntMsg').textContent = p === 'granted' ? '\u6d4f\u89c8\u5668\u901a\u77e5\u5df2\u5f00\u542f\uff08\u9875\u9762\u6253\u5f00\u65f6\u751f\u6548\uff09' : '\u672a\u83b7\u5f97\u901a\u77e5\u6743\u9650'; });
    };
    if ($('ntTest')) {
      $('ntTest').onclick = function () { $('ntMsg').textContent = '\u53d1\u9001\u4e2d\u2026'; post('/api/notify/test').then(function () { $('ntMsg').innerHTML = '<span style="color:#22c55e">\u6d4b\u8bd5\u6d88\u606f\u5df2\u53d1\u9001\uff0c\u8bf7\u5230\u7fa4\u91cc\u786e\u8ba4</span>'; }).catch(function (e) { $('ntMsg').innerHTML = '<span class="err">' + esc(e.message) + '</span>'; }); };
      $('ntClear').onclick = function () { if (confirm('\u79fb\u9664\u63a8\u9001\u901a\u9053\uff1f')) post('/api/notify/clear').then(renderNotify); };
    }
  }).catch(function (e) { el.innerHTML = '<span class="err">' + esc(e.message) + '</span>'; });
}
function fts(t) { var s = String(t); return s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6, 8) + ' ' + s.slice(8, 10) + ':' + s.slice(10, 12); }
var EV = { seen: -1 };
function loadEvents(render) {
  return api('/api/events').then(function (list) {
    var maxId = list.length ? list[0].id : 0;
    if (EV.seen >= 0) {
      list.filter(function (e) { return e.id > EV.seen; }).reverse().forEach(function (e) {
        toast(e.text);
        if (window.Notification && Notification.permission === 'granted') { try { new Notification('\u80a1\u7968\u63d0\u9192', { body: e.text }); } catch (x) { } }
      });
    }
    EV.seen = maxId;
    var el = $('evBody'); if (!render && !el) return; if (!el) return;
    el.className = '';
    el.innerHTML = list.length ? '<table><tr><th>\u65f6\u95f4</th><th style="text-align:left">\u5185\u5bb9</th><th>\u63a8\u9001</th></tr>' + list.map(function (e) {
      var st = e.status === 'ok' ? '<span style="color:#22c55e">\u5df2\u63a8\u9001</span>' : (e.status === 'none' ? '<span class="muted">\u672a\u914d\u7f6e\u901a\u9053</span>' : '<span class="err" style="padding:0" title="' + esc(e.status) + '">\u5931\u8d25</span>');
      return '<tr><td>' + fts(e.ts) + '</td><td style="text-align:left;white-space:normal">' + esc(e.text) + '</td><td>' + st + '</td></tr>';
    }).join('') + '</table>' : '<div class="muted">\u8fd8\u6ca1\u6709\u89e6\u53d1\u8fc7\u63d0\u9192\u3002</div>';
  }).catch(function () {});
}
function refreshWatch() {
  api('/api/watch').then(function (list) {
    var el = $('wlBody'); if (!el) return;
    el.className = '';
    el.innerHTML = list.length ? '<table><tr><th>\u4ee3\u7801</th><th>\u540d\u79f0</th><th>\u6700\u65b0\u4ef7</th><th>\u6da8\u8dcc\u5e45</th><th>RSI(14)</th><th>5\u65e5\u4e0a\u6da8\u6982\u7387</th><th>\u6570\u636e\u622a\u81f3</th><th></th></tr>' +
      list.map(function (w) {
        if (w.missing) return '<tr><td>' + esc(w.code) + '</td><td colspan="6" class="muted">\u6570\u636e\u5df2\u5220\u9664</td><td><button class="small ghost" data-rm="' + esc(w.code) + '">\u79fb\u9664</button></td></tr>';
        return '<tr><td><a href="#" data-go="' + esc(w.code) + '" style="color:' + ACC + '">' + esc(w.code) + '</a></td><td>' + esc(w.name) + '</td><td>' + fx(w.close, 2) + '</td><td class="' + sgn(w.chg) + '">' + pct(w.chg) + '</td><td>' + fx(w.rsi, 1) + '</td><td class="' + (w.probUp5 > 0.5 ? 'up' : 'down') + '">' + (w.probUp5 == null ? '\u6570\u636e\u4e0d\u8db3' : pct(w.probUp5, 1)) + '</td><td>' + fdate(w.asOf) + '</td><td><button class="small ghost" data-rm="' + esc(w.code) + '">\u79fb\u9664</button></td></tr>';
      }).join('') + '</table>' : '<div class="muted">\u8fd8\u6ca1\u6709\u81ea\u9009\u80a1\u3002\u5728\u5de6\u4fa7\u5217\u8868\u70b9 \u2606 \u5373\u53ef\u52a0\u5165\u3002</div>';
    Array.prototype.forEach.call(el.querySelectorAll('[data-rm]'), function (b) { b.onclick = function () { post('/api/watch/remove?code=' + encodeURIComponent(b.getAttribute('data-rm'))).then(function () { loadSymbols(); refreshWatch(); }); }; });
    Array.prototype.forEach.call(el.querySelectorAll('[data-go]'), function (a) { a.onclick = function (e) { e.preventDefault(); selectSymbol(a.getAttribute('data-go'), 'chart'); }; });
  });
  loadAlerts(true);
}
function loadAlerts(render) {
  return api('/api/alerts').then(function (list) {
    var n = list.filter(function (a) { return a.triggered; }).length, b = $('alertBadge');
    b.hidden = n === 0; b.textContent = n;
    if (n > 0 && !loadAlerts.notified) { loadAlerts.notified = true; }
    var el = $('alBody'); if (!render || !el) return;
    el.className = '';
    el.innerHTML = list.length ? '<table><tr><th>\u72b6\u6001</th><th>\u6807\u7684</th><th>\u6761\u4ef6</th><th>\u5f53\u524d\u503c</th><th></th></tr>' + list.map(function (a) {
      var isProb = a.type.indexOf('prob') === 0, val = isProb ? pct(a.value, 0) : fx(a.value, 2), cur = a.current == null ? '\u6570\u636e\u4e0d\u8db3' : (isProb ? pct(a.current, 1) : fx(a.current, 2));
      return '<tr' + (a.triggered ? ' class="hl"' : '') + '><td>' + (a.triggered ? '<b class="up">\u5df2\u89e6\u53d1</b>' : '<span class="muted">\u672a\u89e6\u53d1</span>') + '</td><td>' + esc(a.code + ' ' + a.name) + '</td><td>' + ANAME[a.type] + ' ' + val + '</td><td>' + cur + '</td><td><button class="small ghost" data-del="' + a.id + '">\u5220\u9664</button></td></tr>';
    }).join('') + '</table>' : '<div class="muted">\u6682\u65e0\u63d0\u9192\u3002</div>';
    Array.prototype.forEach.call(el.querySelectorAll('[data-del]'), function (b2) { b2.onclick = function () { post('/api/alerts/remove?id=' + b2.getAttribute('data-del')).then(function () { refreshWatch(); }); }; });
  }).catch(function () {});
}

/* ---------------- Shell: symbols, tabs, import ---------------- */
function loadSymbols() {
  return api('/api/symbols').then(function (list) { state.symbols = list; drawSymbols(); });
}
function drawSymbols() {
  var q = $('search').value.trim().toLowerCase(), el = $('symList');
  el.innerHTML = '';
  state.symbols.forEach(function (s) {
    if (q && (s.code + s.name).toLowerCase().indexOf(q) < 0) return;
    var d = document.createElement('div'); d.className = 'sym' + (s.code === state.code ? ' on' : '');
    d.innerHTML = '<span class="star' + (s.watch ? ' on' : '') + '" title="\u81ea\u9009">' + (s.watch ? '\u2605' : '\u2606') + '</span><div class="nm"><div>' + esc(s.name || s.code) + '</div><div class="cd">' + esc(s.code) + (s.source === 'demo' ? '<span class="src">\u6a21\u62df</span>' : (s.source === 'sina' ? '<span class="src live">\u884c\u60c5</span>' : '<span class="src">CSV</span>')) + '</div></div>' +
      '<div class="px"><div>' + fx(s.close, 2) + '</div><div class="' + sgn(s.chg) + '" style="font-size:12px">' + pct(s.chg) + '</div></div>';
    d.onclick = function (e) {
      if (e.target.className.indexOf('star') === 0) { post('/api/watch/' + (s.watch ? 'remove' : 'add') + '?code=' + encodeURIComponent(s.code)).then(function () { loadSymbols(); if (state.tab === 'watch') refreshWatch(); }); return; }
      selectSymbol(s.code);
    };
    el.appendChild(d);
  });
}
function ymdToDate(d) { return new Date(Math.floor(d / 10000), Math.floor(d / 100) % 100 - 1, d % 100); }
function staleHtml(s) {
  if (s.source !== 'sina') return '';
  var days = (Date.now() - ymdToDate(s.last).getTime()) / 86400000;
  return days > 4 ? '<span class="warn">\u6570\u636e\u5df2 ' + Math.floor(days) + ' \u5929\u672a\u66f4\u65b0\uff0c\u5efa\u8bae\u70b9\u51fb\u201c\u66f4\u65b0\u6570\u636e\u201d</span>' : '';
}
function toast(msg) {
  var t = document.createElement('div');
  t.style.cssText = 'position:fixed;right:20px;bottom:20px;background:#1e2631;border:1px solid #2a3441;border-radius:8px;padding:10px 16px;z-index:20;max-width:360px;font-size:13px';
  t.textContent = msg; document.body.appendChild(t); setTimeout(function () { t.remove(); }, 4500);
}
function refreshFeed(code) {
  var btn = $('btnRefresh'); btn.disabled = true; btn.textContent = '\u66f4\u65b0\u4e2d\u2026';
  toast('\u6b63\u5728\u62c9\u53d6\u6700\u65b0\u884c\u60c5\u2026');
  return post('/api/feed/refresh?code=' + encodeURIComponent(code || 'ALL')).then(function (r) {
    var ok = r.filter(function (x) { return x.ok; }), bad = r.filter(function (x) { return !x.ok; }), nb = 0;
    ok.forEach(function (x) { nb += x.newBars; });
    state.pred = {}; state.bars = null;
    toast(r.length ? ('\u5df2\u66f4\u65b0 ' + ok.length + ' \u53ea\uff0c\u65b0\u589e ' + nb + ' \u6839\u65e5\u7ebf' + (bad.length ? '\uff1b\u5931\u8d25 ' + bad.length + ' \u53ea' : '')) : '\u6ca1\u6709\u53ef\u66f4\u65b0\u7684\u771f\u5b9e\u884c\u60c5\u6807\u7684\uff08\u5148\u70b9\u201c\u6dfb\u52a0A\u80a1\u201d\uff09');
    return loadSymbols().then(function () { if (state.code) selectSymbol(state.code); });
  }).catch(function (e) { toast('\u66f4\u65b0\u5931\u8d25\uff1a' + e.message); }).then(function () { btn.disabled = false; btn.textContent = '\u66f4\u65b0\u884c\u60c5'; });
}

function initFeed() {
  var popular = [['sh600519', '\u8d35\u5dde\u8305\u53f0'], ['sh600036', '\u62db\u5546\u94f6\u884c'], ['sz000001', '\u5e73\u5b89\u94f6\u884c'], ['sz300750', '\u5b81\u5fb7\u65f6\u4ee3'], ['sh000001', '\u4e0a\u8bc1\u6307\u6570'], ['sh510300', '\u6caa\u6df1300ETF']];
  $('feedChips').innerHTML = popular.map(function (p) { return '<span class="chip" data-code="' + p[0] + '">' + p[1] + ' ' + p[0].slice(2) + '</span>'; }).join('');
  Array.prototype.forEach.call($('feedChips').children, function (c) { c.onclick = function () { addFeed(c.getAttribute('data-code')); }; });
  $('btnFeed').onclick = function () { $('feedModal').hidden = false; $('feedMsg').textContent = ''; $('feedQ').focus(); };
  $('feedClose').onclick = function () { $('feedModal').hidden = true; };
  $('btnRefresh').onclick = function () { refreshFeed('ALL'); };
  function search() {
    var q = $('feedQ').value.trim(); if (!q) return;
    $('feedMsg').textContent = '\u641c\u7d22\u4e2d\u2026';
    api('/api/feed/search?q=' + encodeURIComponent(q)).then(function (list) {
      $('feedMsg').textContent = list.length ? '' : '\u6ca1\u6709\u627e\u5230\u5339\u914d\u7684A\u80a1/\u6307\u6570/ETF\uff0c\u53ef\u76f4\u63a5\u8f93\u51656\u4f4d\u4ee3\u7801\u540e\u70b9\u201c\u641c\u7d22\u201d\u518d\u6dfb\u52a0\u3002';
      var el = $('feedRes'); el.innerHTML = list.map(function (x) {
        return '<div class="item"><span class="nm">' + esc(x.name) + ' <span class="muted">' + esc(x.code) + ' \u00b7 ' + esc(x.kind) + '</span></span><button class="small' + (x.added ? ' ghost' : '') + '" data-add="' + esc(x.code) + '">' + (x.added ? '\u91cd\u65b0\u62c9\u53d6' : '\u6dfb\u52a0') + '</button></div>';
      }).join('');
      Array.prototype.forEach.call(el.querySelectorAll('[data-add]'), function (b) { b.onclick = function () { addFeed(b.getAttribute('data-add')); }; });
      if (!list.length && /^(sh|sz|bj)?\d{6}$/i.test(q)) { el.innerHTML = '<div class="item"><span class="nm">' + esc(q.toLowerCase()) + '</span><button class="small" data-add="' + esc(q) + '">\u76f4\u63a5\u6dfb\u52a0</button></div>'; el.querySelector('[data-add]').onclick = function () { addFeed(q); }; }
    }).catch(function (e) { $('feedMsg').textContent = '\u641c\u7d22\u5931\u8d25\uff1a' + e.message; });
  }
  $('feedGo').onclick = search;
  $('feedQ').onkeydown = function (e) { if (e.key === 'Enter') search(); };
}
function addFeed(code) {
  $('feedMsg').textContent = '\u6b63\u5728\u62c9\u53d6 ' + code + ' \u7684\u5386\u53f2\u65e5\u7ebf\u4e0e\u590d\u6743\u56e0\u5b50\uff08\u7ea6\u51e0\u79d2\uff09\u2026';
  post('/api/feed/add?code=' + encodeURIComponent(code)).then(function (r) {
    state.pred = {};
    $('feedModal').hidden = true;
    toast('\u5df2\u6dfb\u52a0 ' + r.name + '\uff08' + r.bars + ' \u6839\u65e5\u7ebf\uff0c' + fdate(r.first) + ' ~ ' + fdate(r.last) + '\uff09\u3002' + (r.note || ''));
    return loadSymbols().then(function () { selectSymbol(r.code, 'chart'); });
  }).catch(function (e) { $('feedMsg').textContent = '\u6dfb\u52a0\u5931\u8d25\uff1a' + e.message; });
}

function selectSymbol(code, tab) {
  state.code = code; state.bars = null; state.mbars = null; state.intra = null; state.quote = null; K.end = 0;
  drawSymbols();
  var s = state.symbols.filter(function (x) { return x.code === code; })[0];
  if (s) {
    $('titleBar').innerHTML = '<h2>' + esc(s.name || s.code) + '</h2><span class="muted">' + esc(s.code) + '\u3000' + s.bars + ' \u6839\u65e5\u7ebf\u3000' + fdate(s.first) + ' ~ ' + fdate(s.last) + '</span>' +
      '<span class="' + sgn(s.chg) + '" style="font-size:18px">' + fx(s.close, 2) + '\u3000' + pct(s.chg) + '</span>' + staleHtml(s) + '<span class="spacer"></span>' + (s.source === 'sina' ? '<button class="small ghost" id="btnUpd">\u66f4\u65b0\u6570\u636e</button> ' : '') + '<button class="small ghost" id="btnDel">\u5220\u9664\u6570\u636e</button>';
    if ($('btnUpd')) { $('btnUpd').onclick = function () { refreshFeed(s.code); }; }
    $('btnDel').onclick = function () {
      if (!confirm('\u5220\u9664 ' + s.code + ' \u7684\u6570\u636e\u4e0e\u672c\u5730CSV\u7d22\u5f15\uff1f\uff08\u6a21\u62df\u6570\u636e\u91cd\u542f\u540e\u4f1a\u91cd\u65b0\u751f\u6210\uff09')) return;
      post('/api/symbol/delete?code=' + encodeURIComponent(s.code)).then(function () { return loadSymbols(); }).then(function () { if (state.symbols.length) selectSymbol(state.symbols[0].code); else { state.code = null; $('titleBar').innerHTML = ''; } });
    };
  }
  switchTab(tab || state.tab);
}
function switchTab(t) {
  state.tab = t;
  Array.prototype.forEach.call(document.querySelectorAll('#tabs button'), function (b) { b.className = b.getAttribute('data-tab') === t ? 'on' : ''; });
  ['chart', 'predict', 'backtest', 'watch', 'scan'].forEach(function (n) { $('tab-' + n).hidden = n !== t; });
  if (!state.code && t !== 'watch' && t !== 'scan') return;
  if (t === 'chart') renderChart(); else if (t === 'predict') renderPredict(); else if (t === 'backtest') renderBacktest(); else if (t === 'scan') renderScan(); else renderWatch();
}

/* ---------------- Whole-market scan ---------------- */
var SC = { since: 0, hits: [], runId: -1, timer: null, last: null, sortP: false, fresh: {} };
var PHASE_TXT = ['\u7a7a\u95f2', '\u83b7\u53d6\u80a1\u7968\u5217\u8868', '\u8bad\u7ec3\u6a21\u578b', '\u626b\u63cf\u4e2d', '\u5df2\u5b8c\u6210', '\u51fa\u9519'];
function renderScan() {
  var root = $('tab-scan');
  root.innerHTML = '<div class="card"><h3>\u5168\u5e02\u573a\u626b\u63cf\uff1a\u627e\u51fa\u201cT+1 \u5f00\u76d8\u4e70\u5165\u540e\u3001\u672a\u6765\u51e0\u5929\u53ef\u80fd\u5927\u6da8\u201d\u7684 A \u80a1</h3>' +
    '<div class="toolbar"><span class="muted">\u4e70\u5165\u540e</span><select id="scH"><option value="1">1</option><option value="3">3</option><option value="5" selected>5</option><option value="10">10</option></select><span class="muted">\u4e2a\u4ea4\u6613\u65e5\u5185\uff0c\u4efb\u4e00\u5929\u6536\u76d8\u4ef7\u8f83\u4e70\u5165\u4ef7\u6da8\u5e45 \u2265</span><select id="scTheta"><option value="0.03">3%</option><option value="0.05">5%</option><option value="0.08" selected>8%</option><option value="0.10">10%</option><option value="0.15">15%</option></select>' +
    '<span class="muted">\u53ea\u663e\u793a\u9884\u6d4b\u6982\u7387 \u2265</span><input id="scP" size="4" value="30"><span class="muted">%</span>' +
    '<button id="scGo">\u5f00\u59cb\u626b\u63cf</button><button id="scStop" class="ghost" hidden>\u505c\u6b62</button>' +
    '<label class="muted" title="\u53bb\u6389\u201c\u524d\u4e00\u5929\u5df2\u6da8\u22655%\u201d\u7684\u80a1\u7968\uff1a\u8fd9\u7c7b\u80a1\u7968\u7684\u89c4\u5f8b\u6700\u5f3a\u4e5f\u6700\u96be\u4e70\u5230\uff0c\u6392\u9664\u540e\u770b\u6a21\u578b\u662f\u5426\u8fd8\u6709\u9884\u6d4b\u529b"><input type="checkbox" id="scExcl"> \u6392\u9664\u524d\u4e00\u65e5\u6da8\u5e45\u22655%\u7684\u80a1\u7968</label>' +
    '<label class="muted" title="\u4e0a\u8bc1\u6307\u6570\u6536\u572820\u65e5\u5747\u7ebf\u4e0b\u65b9\u3001\u4e14\u8fd15\u65e5\u4e0b\u8dcc\uff08\u201c\u5f31\u52bf\u201d\uff09\u65f6\uff0c\u4e0d\u8f93\u51fa\u4efb\u4f55\u63a8\u8350\uff1a\u5927\u76d8\u5f31\u7684\u65f6\u5019\u4e2a\u80a1\u666e\u904d\u8ddf\u7740\u8dcc"><input type="checkbox" id="scGate"> \u5927\u76d8\u5f31\u52bf\u65f6\u4e0d\u63a8\u8350</label>' +
    '<label class="muted"><input type="checkbox" id="scSort"> \u6309\u6982\u7387\u6392\u5e8f</label></div><div id="scMkt"></div>' +
    '<div class="note">\u4e70\u5165\u4ef7\u6309\u201c\u4fe1\u53f7\u65e5\uff08\u6536\u76d8\u540e\u626b\u63cf\uff09\u7684\u4e0b\u4e00\u4e2a\u4ea4\u6613\u65e5\u5f00\u76d8\u4ef7\u201d\u8ba1\u7b97\uff08\u4f60\u53ea\u80fd T+1 \u4e70\u5165\uff09\uff0c\u6700\u65e9\u518d\u4e0b\u4e00\u4e2a\u4ea4\u6613\u65e5\u624d\u80fd\u5356\u51fa\uff08T+1 \u89c4\u5219\uff09\u3002\u7edf\u8ba1\u7684\u662f\u4e70\u5165\u540e H \u4e2a\u4ea4\u6613\u65e5\u91cc\u4efb\u610f\u4e00\u5929\u7684\u6700\u9ad8<b>\u6536\u76d8\u4ef7</b>\uff0c\u4f60\u4e0d\u53ef\u80fd\u6bcf\u6b21\u90fd\u5356\u5728\u6700\u9ad8\u70b9\uff0c\u6240\u4ee5\u8bf7\u540c\u65f6\u770b\u4e0b\u9762\u7684\u201c\u6301\u6709\u5230\u7b2c H \u5929\u6536\u76d8\u7684\u5e73\u5747\u6536\u76ca\u201d\u3002H=1 \u5373\u8d85\u77ed\u7ebf\uff1a\u4e70\u5165\u540e\u7b2c\u4e00\u4e2a\u53ef\u5356\u65e5\uff08\u6b21\u65e5\uff09\u6536\u76d8\u4ef7\u662f\u5426\u8fbe\u5230\u76ee\u6807\u3002\u5df2\u6392\u9664\u5317\u4ea4\u6240 920\u3001\u79d1\u521b\u677f 688 \u548c ST/*ST \u80a1\u7968\uff1b\u4fe1\u53f7\u65e5\u6536\u5728\u6da8\u505c\u3001\u6216\u4e70\u5165\u65e5\u5f00\u76d8\u5c31\u5728\u6da8\u505c\uff08\u4e70\u4e0d\u8fdb\uff09\u7684\u6837\u672c\u4e0d\u8ba1\u5165\u8bad\u7ec3\u3001\u68c0\u9a8c\u548c\u7ed3\u679c\u3002</div>' +
    '<div id="scProg"></div>' +
    '<div class="toolbar" style="margin-top:8px"><label title="\u670d\u52a1\u5f00\u7740\u65f6\uff0c\u6bcf\u4e2a\u4ea4\u6613\u65e5 15:10 \u4e4b\u540e\u81ea\u52a8\u7528\u4e0a\u9762\u7684\u53c2\u6570\u626b\u63cf\u4e00\u6b21\uff0c\u7ed3\u675f\u540e\u628a\u524d 5 \u53ea\u63a8\u9001\u5230\u201c\u81ea\u9009\u4e0e\u63d0\u9192\u201d\u91cc\u914d\u7f6e\u7684\u673a\u5668\u4eba\uff1b\u7ed3\u679c\u540c\u6837\u8bb0\u5165\u5386\u53f2\u6218\u7ee9"><input type="checkbox" id="scAuto"> \u6bcf\u4e2a\u4ea4\u6613\u65e5\u6536\u76d8\u540e\uff0815:10\uff09\u81ea\u52a8\u626b\u63cf\u5e76\u63a8\u9001</label><span id="scAutoSt" class="muted"></span></div></div>' +
    '<div class="card"><h3>\u626b\u63cf\u7ed3\u679c <span id="scCount" class="muted" style="font-weight:400"></span></h3><div id="scRes"><div class="muted">\u70b9\u51fb\u201c\u5f00\u59cb\u626b\u63cf\u201d\u3002\u53d1\u73b0\u4e00\u53ea\u5c31\u663e\u793a\u4e00\u53ea\uff0c\u4e0d\u5fc5\u7b49\u626b\u63cf\u7ed3\u675f\u3002</div></div></div>' +
    '<div class="card"><h3>\u8fd9\u4e9b\u6982\u7387\u9760\u4e0d\u9760\u8c31\uff1f\uff08\u6a21\u578b\u5728\u6ca1\u53c2\u4e0e\u8bad\u7ec3\u7684\u6700\u8fd1\u7ea6 250 \u4e2a\u4ea4\u6613\u65e5\u4e0a\u7684\u68c0\u9a8c\uff09</h3><div id="scVal"><div class="muted">\u9996\u6b21\u626b\u63cf\u4f1a\u5148\u8bad\u7ec3\u6a21\u578b\uff0c\u4e4b\u540e\u663e\u793a\u3002</div></div></div>' +
    '<div class="card"><h3>\u5386\u53f2\u6218\u7ee9\uff1a\u6bcf\u6b21\u626b\u63cf\u7684\u7ed3\u679c\uff0c\u9694\u5929\u81ea\u52a8\u6838\u5bf9\u771f\u5b9e\u6da8\u8dcc <button class="small ghost" id="trRef">\u5237\u65b0</button></h3><div id="scTrack"><div class="loading">\u52a0\u8f7d\u4e2d\u2026</div></div></div>' +
    '<div class="card"><h3>\u8bf7\u52a1\u5fc5\u770b\u6e05</h3><div class="note" style="font-size:13px;line-height:1.7">' +
    '\u2460 \u8fd9\u4e0d\u662f\u201c\u4fdd\u8bc1\u5927\u6da8\u201d\u7684\u540d\u5355\u3002\u6a21\u578b\u662f\u7528\u7ea6 300 \u53ea\u80a1\u7968\u3001\u8fd1 4 \u5e74\u65e5\u7ebf\u8bad\u7ec3\u7684\u7edf\u8ba1\u6a21\u578b\uff0c\u53ea\u7ed9\u51fa\u201c\u76f8\u5bf9\u57fa\u51c6\u66f4\u9ad8\u201d\u7684\u6982\u7387\uff1b\u4e0a\u65b9\u68c0\u9a8c\u8868\u662f\u5b83\u5728\u8bad\u7ec3\u5916\u65f6\u671f\u7684\u771f\u5b9e\u547d\u4e2d\u7387\uff0c\u8bf7\u4ee5\u6b64\u4e3a\u51c6\uff0c\u4e0d\u8981\u770b\u6982\u7387\u6570\u5b57\u672c\u8eab\u3002<br>' +
    '\u2461 \u6a21\u578b\u7684\u4f18\u52bf\u4e3b\u8981\u6765\u81ea\u201c\u8fd1\u671f\u6ce2\u52a8\u5927\u201d\u201c\u524d\u4e00\u5929\u5df2\u5927\u6da8\u201d\u7b49\u89c4\u5f8b\uff0c\u8fd9\u7c7b\u80a1\u7968\u6da8\u5f97\u591a\uff0c\u8dcc\u5f97\u4e5f\u591a\u2014\u2014\u8bf7\u770b\u68c0\u9a8c\u8868\u91cc\u201c\u6301\u6709\u671f\u672b\u4e8f\u635f\u22655% \u5360\u6bd4\u201d\u548c\u201c\u6301\u6709\u5230\u671f\u5e73\u5747\u6536\u76ca\u201d\u3002<br>' +
    '\u2462 \u4e70\u5165\u4ef7\u53d6\u7684\u662f\u5386\u53f2\u4e0a\u771f\u5b9e\u7684 T+1 \u5f00\u76d8\u4ef7\uff08\u5df2\u542b\u9ad8\u5f00\uff09\uff1b\u4f46\u5982\u679c T+1 \u5f00\u76d8\u5c31\u6da8\u505c\uff0c\u4f60\u5b9e\u9645\u4e70\u4e0d\u5230\uff1b\u4e5f\u6ca1\u6709\u8ba1\u5165\u4f63\u91d1\u3001\u5370\u82b1\u7a0e\u3001\u6ed1\u70b9\u3002\u201c\u4efb\u4e00\u5929\u6700\u9ad8\u6536\u76d8\u201d\u53ea\u662f\u4e0a\u9650\uff0c\u4f60\u9700\u8981\u81ea\u5df1\u51b3\u5b9a\u4f55\u65f6\u5356\uff0c\u6301\u6709\u5230\u671f\u7684\u6536\u76ca\u66f4\u63a5\u8fd1\u73b0\u5b9e\u3002<br>' +
    '\u2463 \u6570\u636e\u6765\u81ea\u65b0\u6d6a\u516c\u5f00\u63a5\u53e3\uff0c\u4ec5\u4f9b\u4e2a\u4eba\u7814\u7a76\u4e0e\u5b66\u4e60\uff0c\u4e0d\u6784\u6210\u4efb\u4f55\u6295\u8d44\u5efa\u8bae\u3002</div></div>';
  $('scSort').checked = SC.sortP;
  $('scSort').onchange = function () { SC.sortP = this.checked; drawScanRows(); };
  $('scGo').onclick = function () {
    var pm = parseFloat($('scP').value); if (isNaN(pm) || pm < 2 || pm > 95) { toast('\u6982\u7387\u9608\u503c\u8bf7\u586b 2\u201395 \u4e4b\u95f4\u7684\u6570\u5b57'); return; }
    SC.since = 0; SC.hits = []; SC.fresh = {}; drawScanRows();
    post('/api/scan/start?theta=' + $('scTheta').value + '&h=' + $('scH').value + '&pmin=' + (pm / 100) + ($('scExcl').checked ? '&excl=1' : '') + ($('scGate').checked ? '&mg=1' : '')).then(function (d) { SC.runId = d.runId; pollScan(); }).catch(function (e) { toast(e.message); });
  };
  $('trRef').onclick = loadTrack; loadTrack();
  var saveAuto = function () {
    var pm = parseFloat($('scP').value); if (isNaN(pm) || pm < 2 || pm > 95) { toast('\u6982\u7387\u9608\u503c\u8bf7\u586b 2\u201395 \u4e4b\u95f4\u7684\u6570\u5b57'); $('scAuto').checked = false; return; }
    post('/api/scan/auto/set?on=' + ($('scAuto').checked ? 1 : 0) + '&theta=' + $('scTheta').value + '&h=' + $('scH').value + '&pmin=' + (pm / 100) + ($('scExcl').checked ? '&excl=1' : '') + ($('scGate').checked ? '&mg=1' : '')).then(drawAuto).catch(function (e) { toast(e.message); });
  };
  $('scAuto').onchange = saveAuto;
  ['scH', 'scTheta', 'scP', 'scExcl', 'scGate'].forEach(function (id) { $(id).addEventListener('change', function () { if ($('scAuto').checked) saveAuto(); }); });
  api('/api/scan/auto').then(function (d) {
    $('scAuto').checked = !!d.on;
    if (d.on) { $('scH').value = d.hold; $('scTheta').value = d.theta.toFixed(2); $('scP').value = Math.round(d.pmin * 100); $('scExcl').checked = !!d.excl; $('scGate').checked = !!d.mg; }
    drawAuto(d);
  }).catch(function () {});
  $('scStop').onclick = function () { post('/api/scan/stop').then(function (d) { drawScanStatus(d); }); };
  SC.since = 0; SC.hits = [];
  pollScan();
}
function drawAuto(d) {
  var el = $('scAutoSt'); if (!el) return;
  if (!d.on) { el.textContent = ''; return; }
  el.textContent = '\u5df2\u5f00\u542f\uff1a\u4e70\u5165\u540e ' + d.hold + ' \u65e5\u5185 \u2265' + Math.round(d.theta * 100) + '%\u3001\u6982\u7387 \u2265' + Math.round(d.pmin * 100) + '%\uff1b' + (d.lastDay ? '\u4e0a\u6b21\u81ea\u52a8\u626b\u63cf ' + fdate(d.lastDay) + '\uff1b' : '\u5c1a\u672a\u81ea\u52a8\u626b\u63cf\u8fc7\uff1b') + (d.push ? '\u7ed3\u679c\u4f1a\u63a8\u9001\u5230\u4f60\u914d\u7f6e\u7684\u673a\u5668\u4eba' : '\u8fd8\u6ca1\u914d\u7f6e\u63a8\u9001\u673a\u5668\u4eba\uff08\u5728\u201c\u81ea\u9009\u4e0e\u63d0\u9192\u201d\u91cc\u8bbe\u7f6e\uff09\uff0c\u7ed3\u679c\u53ea\u8bb0\u5165\u5386\u53f2\u6218\u7ee9') + (d.running ? '\uff1b\u6b63\u5728\u626b\u63cf\u4e2d\u2026' : '');
}
function pollScan() {
  clearTimeout(SC.timer);
  api('/api/scan/status?since=' + SC.since).then(function (d) {
    if (SC.runId !== d.runId) { SC.runId = d.runId; SC.since = 0; SC.hits = []; SC.fresh = {}; if (d.hits.length === 0 && d.hitsTotal > 0) { pollScan(); return; } }
    d.hits.forEach(function (h) { SC.hits.push(h); SC.fresh[h.code] = Date.now(); });
    SC.since += d.hits.length; SC.last = d;
    if ($('scProg')) { drawScanStatus(d); if (d.hits.length) drawScanRows(); }
    if (d.phase >= 1 && d.phase <= 3) SC.timer = setTimeout(pollScan, 1000);
    else if (d.hitsTotal > SC.since) SC.timer = setTimeout(pollScan, 200);
  }).catch(function (e) { if ($('scProg')) $('scProg').innerHTML = '<span class="err">' + esc(e.message) + '</span>'; SC.timer = setTimeout(pollScan, 3000); });
}
var MK_NAME = ['\u5f3a\u52bf', '\u56de\u8c03', '\u53cd\u5f39', '\u5f31\u52bf'];
var MK_DESC = ['\u6307\u6570\u572820\u65e5\u7ebf\u4e0a\u65b9\u4e14\u8fd15\u65e5\u4e0a\u6da8', '\u6307\u6570\u572820\u65e5\u7ebf\u4e0a\u65b9\u4f46\u8fd15\u65e5\u4e0b\u8dcc', '\u6307\u6570\u572820\u65e5\u7ebf\u4e0b\u65b9\u4f46\u8fd15\u65e5\u4e0a\u6da8', '\u6307\u6570\u572820\u65e5\u7ebf\u4e0b\u65b9\u4e14\u8fd15\u65e5\u4e0b\u8dcc'];
function drawMarket(d) {
  var el = $('scMkt'); if (!el) return;
  var m = d.market; if (!m) { el.innerHTML = ''; return; }
  var cls = m.state === 0 ? 'up' : (m.state === 3 ? 'down' : '');
  var tail = '';
  if (d.gate && m.state === 3) tail = '\u3000<b class="warn">\u5df2\u6309\u8bbe\u7f6e\u6682\u505c\u63a8\u8350' + (d.gated ? '\uff08\u62e6\u4e0b ' + d.gated + ' \u53ea\u8fbe\u6807\u80a1\u7968\uff09' : '') + '</b>';
  else if (m.state === 3) tail = '\u3000<span class="warn">\u5927\u76d8\u504f\u5f31\uff0c\u4e2a\u80a1\u666e\u904d\u8ddf\u7740\u8dcc\uff0c\u4e0b\u9762\u7684\u63a8\u8350\u8bf7\u683c\u5916\u8c28\u614e\uff0c\u6216\u76f4\u63a5\u89c2\u671b\u3002</span>';
  else if (m.state === 1 || m.state === 2) tail = '\u3000<span class="muted">\u5927\u76d8\u4e0d\u660e\u6717\uff0c\u4ed3\u4f4d\u5b9c\u8f7b\u3002</span>';
  el.innerHTML = '<div class="note" style="margin-top:8px">\u5927\u76d8\uff08\u4e0a\u8bc1\u6307\u6570 ' + fdate(m.date) + '\uff09\uff1a<b class="' + cls + '">' + MK_NAME[m.state] + '</b>\uff08' + MK_DESC[m.state] + '\uff09\uff1b\u5f53\u65e5 <span class="' + sgn(m.r1) + '">' + pct(m.r1, 2) + '</span>\uff0c5\u65e5 <span class="' + sgn(m.r5) + '">' + pct(m.r5, 2) + '</span>\uff0c\u8ddd20\u65e5\u7ebf <span class="' + sgn(m.dist) + '">' + pct(m.dist, 2) + '</span>' + tail + '</div>';
}
function drawScanStatus(d) {
  var run = d.phase >= 1 && d.phase <= 3, pctDone = 0, line = '';
  if (d.phase === 2) pctDone = d.trainLoaded / d.trainTotal; else if (d.phase >= 3) pctDone = d.universe ? d.pos / d.universe : 0;
  else if (d.phase === 1) pctDone = Math.min(1, d.universe / 5600);
  var eta = '';
  if (d.phase === 3) {
    if (!SC.rate || SC.rate.run !== d.runId || d.pos < SC.rate.pos0) SC.rate = { run: d.runId, t0: Date.now(), pos0: d.pos };
    var dt = (Date.now() - SC.rate.t0) / 1000, dp = d.pos - SC.rate.pos0;
    if (dt > 8 && dp > 10) { var remS = dt / dp * (d.universe - d.pos); eta = '\uff0c\u9884\u8ba1\u8fd8\u9700\u7ea6 ' + Math.max(1, Math.round(remS / 60)) + ' \u5206\u949f'; }
  }
  line = '<b>' + PHASE_TXT[d.phase] + '</b>\u3000' + esc(d.msg) + (d.phase >= 3 ? '\u3000\u5df2\u5904\u7406 ' + d.pos + ' / ' + d.universe + '\uff08\u6709\u6548 ' + d.scanned + '\uff0c\u505c\u724c/\u6570\u636e\u4e0d\u8db3 ' + d.skipped + '\uff0c\u5931\u8d25 ' + d.failed + '\uff09' : '') + '\u3000\u7528\u65f6 ' + Math.floor(d.elapsed / 60) + ' \u5206 ' + (d.elapsed % 60) + ' \u79d2' + eta;
  $('scProg').innerHTML = '<div class="meter" style="height:10px;background:#2a3441;margin:6px 0"><span style="display:block;height:10px;border-radius:5px;background:' + ACC + ';width:' + (pctDone * 100).toFixed(1) + '%"></span></div><div style="font-size:13px">' + line + '</div>' +
    (d.marketOpen && d.phase >= 1 ? '<div class="warn" style="margin-top:4px">\u5f53\u524d\u5904\u4e8e\u4ea4\u6613\u65f6\u6bb5\uff1a\u672a\u6536\u76d8\u7684\u5f53\u65e5K\u7ebf\u5df2\u88ab\u5ffd\u7565\uff0c\u9884\u6d4b\u7684\u662f\u201c\u4eca\u5929\u201d\u800c\u4e0d\u662f\u201c\u660e\u5929\u201d\u3002\u5efa\u8bae\u6536\u76d8\u540e\uff0815:10 \u4ee5\u540e\uff09\u626b\u63cf\u3002</div>' : '');
  $('scGo').hidden = run; $('scStop').hidden = !run; $('scGo').textContent = d.phase === 0 ? '\u5f00\u59cb\u626b\u63cf' : '\u91cd\u65b0\u626b\u63cf';
  $('scTheta').disabled = run; $('scH').disabled = run; $('scP').disabled = run; $('scExcl').disabled = run; $('scGate').disabled = run;
  drawMarket(d);
  if (d.phase === 4 && SC.trackedRun !== d.runId) { SC.trackedRun = d.runId; loadTrack(); }
  if (d.validation) drawScanVal(d.validation);
}
function loadTrack() {
  api('/api/scan/track').then(function (t) {
    var el = $('scTrack'); if (!el) return;
    if (!t.total) { el.innerHTML = '<div class="muted">\u8fd8\u6ca1\u6709\u8bb0\u5f55\u3002\u6bcf\u6b21\u626b\u63cf\u53d1\u73b0\u7684\u80a1\u7968\u90fd\u4f1a\u81ea\u52a8\u8bb0\u4e0b\u6765\uff0c\u7b49 T+1 \u4e70\u5165\u540e\u7684\u8fd9\u51e0\u5929\u884c\u60c5\u51fa\u6765\uff0c\u7cfb\u7edf\u81ea\u52a8\u6838\u5bf9\u5b83\u4eec\u771f\u5b9e\u6da8\u4e86\u591a\u5c11\u3002\u8fd9\u91cc\u7d2f\u8ba1\u7684\u624d\u662f\u6a21\u578b\u7684\u201c\u5b9e\u76d8\u6218\u7ee9\u201d\uff0c\u6bd4\u4e0a\u9762\u7684\u5386\u53f2\u68c0\u9a8c\u66f4\u53ef\u4fe1\u3002</div>'; return; }
    var runs = t.runs.map(function (r) {
      return '<tr><td>' + fdate(r.asOf) + '</td><td>' + r.hold + ' \u65e5\u5185 \u2265' + pct(r.theta, 0) + (r.excl ? ' <span class="muted">(\u6392\u9664\u524d\u65e5\u5927\u6da8)</span>' : '') + '</td><td>' + r.n + '</td><td>' + r.verified + '</td><td>' + r.hit + '</td><td>' + (r.verified ? pct(r.hit / r.verified, 1) : '-') + '</td><td>' + pct(r.base, 1) + '</td><td class="' + sgn(r.avgMax) + '">' + (r.avgMax == null ? '-' : pct(r.avgMax, 2)) + '</td><td class="' + sgn(r.avgEnd) + '">' + (r.avgEnd == null ? '-' : pct(r.avgEnd, 2)) + '</td><td class="' + sgn(r.avgEnd == null ? 0 : r.avgEnd - t.cost) + '">' + (r.avgEnd == null ? '-' : pct(r.avgEnd - t.cost, 2)) + '</td><td class="' + sgn(r.topEnd == null ? 0 : r.topEnd - t.cost) + '">' + (r.topEnd == null ? '-' : pct(r.topEnd - t.cost, 2) + ' <span class="muted">(' + r.topV + '\u53ea)</span>') + '</td></tr>';
    }).join('');
    var sim = t.runs.filter(function (r) { return r.topEnd != null; }).slice().reverse();
    var eq = 1, wins = 0, sumNet = 0;
    sim.forEach(function (r) { var x = r.topEnd - t.cost; eq *= 1 + x; sumNet += x; if (x > 0) wins++; });
    var simHtml = sim.length ? '<div class="kv" style="margin-top:10px"><div><span>\u6a21\u62df\u76d8\uff1a\u6bcf\u6279\u4e70\u6982\u7387\u6700\u9ad8\u7684\u524d ' + t.topN + ' \u53ea\uff0c\u7b49\u6743</span><b>' + sim.length + ' \u6279\u5df2\u6838\u5bf9</b></div><div><span>\u6bcf\u6279\u5e73\u5747\u51c0\u6536\u76ca\uff08\u5df2\u6263 ' + pct(t.cost, 2) + ' \u6210\u672c\uff09</span><b class="' + sgn(sumNet) + '">' + pct(sumNet / sim.length, 2) + '</b></div><div><span>\u51c0\u6536\u76ca\u4e3a\u6b63\u7684\u6279\u6b21</span><b>' + wins + ' / ' + sim.length + '</b></div><div><span>\u5404\u6279\u590d\u5229\u7d2f\u8ba1\u51c0\u503c</span><b class="' + sgn(eq - 1) + '">' + eq.toFixed(4) + '</b></div></div><div class="note">\u6a21\u62df\u76d8\u51c0\u503c\u662f\u628a\u6bcf\u6279\u5f53\u4f5c\u72ec\u7acb\u7684\u4e00\u7b14\u4e70\u5165\uff0c\u5ffd\u7565\u6279\u6b21\u4e4b\u95f4\u8d44\u91d1\u5360\u7528\u91cd\u53e0\uff0c\u4ec5\u4f5c\u53c2\u8003\uff1b\u6279\u6b21\u5c11\u4e8e 20 \u4e2a\u65f6\u6ce2\u52a8\u4e3b\u8981\u6765\u81ea\u8fd0\u6c14\u3002</div>' : '<div class="note">\u6a21\u62df\u76d8\uff08\u6bcf\u6279\u4e70\u6982\u7387\u6700\u9ad8\u7684\u524d ' + t.topN + ' \u53ea\u3001\u6263\u9664 ' + pct(t.cost, 2) + ' \u5f80\u8fd4\u6210\u672c\uff09\u8981\u7b49\u81f3\u5c11\u4e00\u6279\u8d70\u5b8c\u4e70\u5165\u540e\u7684\u6301\u6709\u671f\u624d\u4f1a\u51fa\u73b0\u3002</div>';
    var items = t.items.map(function (i) {
      var res = i.status === 1 ? '\u6700\u9ad8 <span class="' + sgn(i.maxRet) + '">' + pct(i.maxRet, 2) + '</span>\u3000\u7b2c' + i.hold + '\u65e5\u6536\u76d8 <span class="' + sgn(i.endRet) + '">' + pct(i.endRet, 2) + '</span>' + (i.maxRet >= i.theta ? ' <b class="up">\u2713 \u8fbe\u6807</b>' : '') + ' <span class="muted">\u4e70\u5165 ' + fdate(i.entryDate).slice(5) + '</span>' : (i.status === 2 ? '<span class="muted">\u65e0\u6cd5\u6838\u5bf9</span>' : '<span class="muted">\u7b49\u5f85\u4e70\u5165\u540e ' + i.hold + ' \u4e2a\u4ea4\u6613\u65e5\u8d70\u5b8c</span>');
      return '<tr><td>' + fdate(i.asOf) + '</td><td>' + esc(i.code) + '</td><td>' + esc(i.name) + '</td><td>' + pct(i.p, 1) + '</td><td style="text-align:left">' + res + '</td></tr>';
    }).join('');
    el.innerHTML = '<div class="kv"><div><span>\u5df2\u8bb0\u5f55</span><b>' + t.total + ' \u53ea\u6b21</b></div><div><span>\u5df2\u6838\u5bf9</span><b>' + t.verified + '</b></div><div><span>\u5f85\u6838\u5bf9</span><b>' + t.pending + '</b></div>' +
      '<div><span>\u5b9e\u9645\u8fbe\u6807</span><b>' + t.hit + '</b></div><div><span>\u5b9e\u76d8\u547d\u4e2d\u7387</span><b>' + (t.precision == null ? '-' : pct(t.precision, 1)) + '</b></div><div><span>\u6301\u6709\u671f\u5185\u6700\u9ad8\u6da8\u5e45\u5747\u503c</span><b class="' + sgn(t.avgMax) + '">' + (t.avgMax == null ? '-' : pct(t.avgMax, 2)) + '</b></div><div><span>\u6301\u6709\u5230\u671f\u5e73\u5747\u6536\u76ca</span><b class="' + sgn(t.avgEnd) + '">' + (t.avgEnd == null ? '-' : pct(t.avgEnd, 2)) + '</b></div></div>' +
      simHtml + '<table style="margin-top:10px"><tr><th>\u6570\u636e\u65e5\u671f</th><th>\u76ee\u6807</th><th>\u5165\u9009</th><th>\u5df2\u6838\u5bf9</th><th>\u8fbe\u6807</th><th>\u547d\u4e2d\u7387</th><th>\u68c0\u9a8c\u671f\u57fa\u51c6</th><th>\u671f\u5185\u6700\u9ad8\u6da8\u5e45\u5747\u503c</th><th>\u6301\u6709\u5230\u671f\u5e73\u5747\u6536\u76ca</th><th>\u5168\u90e8\u5165\u9009\u51c0\u6536\u76ca</th><th>\u524d ' + t.topN + ' \u53ea\u51c0\u6536\u76ca</th></tr>' + runs + '</table>' +
      '<details style="margin-top:8px"><summary class="muted" style="cursor:pointer">\u9010\u53ea\u660e\u7ec6\uff08\u6700\u8fd1 80 \u6761\uff09</summary><table><tr><th>\u6570\u636e\u65e5\u671f</th><th>\u4ee3\u7801</th><th>\u540d\u79f0</th><th>\u9884\u6d4b\u6982\u7387</th><th style="text-align:left">\u7ed3\u679c</th></tr>' + items + '</table></details>' +
      '<div class="note">\u4e70\u5165\u4ef7 = \u4fe1\u53f7\u65e5\u4e0b\u4e00\u4e2a\u4ea4\u6613\u65e5\u7684\u5f00\u76d8\u4ef7\uff1b\u6da8\u5e45\u7528\u672a\u590d\u6743\u4ef7\u683c\u8ba1\u7b97\uff0c\u9047\u5230\u9664\u6743\u9664\u606f\u4f1a\u504f\u4f4e\u3002\u6837\u672c\u5c11\u4e8e\u51e0\u5341\u53ea\u65f6\uff0c\u547d\u4e2d\u7387\u53ea\u662f\u566a\u58f0\uff0c\u8bf7\u6301\u7eed\u7d2f\u8ba1\u3002</div>';
  }).catch(function () {});
}
function drawScanVal(v) {
  var el = $('scVal'); if (!el) return;
  var b3 = v.bins.filter(function (b) { return b.p >= 0.3 && b.n >= 20; })[0];
  var rows = v.bins.map(function (b) {
    var lift = v.baseTest > 0 && b.n > 0 ? b.precision / v.baseTest : null;
    return '<tr><td>\u2265 ' + pct(b.p, 0) + '</td><td>' + b.n + '</td><td>' + b.hit + '</td><td>' + (b.n ? pct(b.precision, 1) : '-') + '</td><td class="' + (lift != null && lift > 1.5 ? 'up' : '') + '">' + (lift != null ? lift.toFixed(2) + ' \u500d' : '-') + '</td><td class="' + sgn(b.avgEnd) + '">' + (b.n ? pct(b.avgEnd, 2) : '-') + '</td><td class="' + sgn(b.tp) + '">' + (b.n ? pct(b.tp, 2) : '-') + '</td><td class="' + sgn(b.tpsl) + '">' + (b.n ? pct(b.tpsl, 2) : '-') + '</td><td>' + (b.n ? pct(b.loss, 1) : '-') + '</td><td class="muted">' + (b.n < 50 ? '\u6837\u672c\u592a\u5c11\uff0c\u53c2\u8003\u4ef7\u503c\u4f4e' : '') + '</td></tr>';
  }).join('');
  el.innerHTML = '<div class="kv"><div><span>\u76ee\u6807</span><b>\u4e70\u5165\u540e ' + v.hold + ' \u65e5\u5185\u6700\u9ad8\u6536\u76d8 \u2265 +' + pct(v.theta, 0) + (v.excl ? '<br><small class="muted">\u5df2\u6392\u9664\u524d\u65e5\u6da8\u22655%</small>' : '') + '</b></div><div><span>\u8bad\u7ec3\u6837\u672c\uff08\u80a1\u7968-\u65e5\uff09</span><b>' + v.trainN + '</b></div><div><span>\u68c0\u9a8c\u6837\u672c\uff08' + fdate(v.cutDate) + ' \u8d77\uff09</span><b>' + v.testN + '</b></div>' +
    '<div><span>\u68c0\u9a8c\u671f\u57fa\u51c6\u53d1\u751f\u7387</span><b>' + pct(v.baseTest, 1) + '</b></div><div><span>\u57fa\u51c6\uff1a\u6301\u6709 ' + v.hold + ' \u65e5\u5e73\u5747\u6536\u76ca</span><b class="' + sgn(v.baseEnd) + '">' + pct(v.baseEnd, 2) + '</b></div><div><span>\u57fa\u51c6\uff1a\u6b62\u76c8\u7b56\u7565 / \u52a0\u6b62\u635f</span><b><span class="' + sgn(v.baseTp) + '">' + pct(v.baseTp, 2) + '</span> / <span class="' + sgn(v.baseTs) + '">' + pct(v.baseTs, 2) + '</span></b></div><div><span>Brier \u6280\u80fd\u5206</span><b class="' + (v.brierSkill > 0 ? 'up' : '') + '">' + fx(v.brierSkill * 100, 1) + '%</b></div></div>' +
    '<table style="margin-top:10px"><tr><th>\u9884\u6d4b\u6982\u7387</th><th>\u68c0\u9a8c\u6837\u672c\u6570</th><th>\u5b9e\u9645\u8fbe\u6807\u6b21\u6570</th><th>\u5b9e\u9645\u547d\u4e2d\u7387</th><th>\u76f8\u5bf9\u57fa\u51c6</th><th>\u6301\u6709 ' + v.hold + ' \u65e5\u540e\u5e73\u5747\u6536\u76ca</th><th>\u6da8\u5230 +' + pct(v.theta, 0) + ' \u5c31\u5356\uff0c\u5426\u5219\u6301\u6709\u5230\u671f</th><th>\u540c\u4e0a\uff0c\u518d\u52a0 \u22125% \u6b62\u635f</th><th>\u6301\u6709\u671f\u672b\u4e8f\u635f \u22655% \u5360\u6bd4</th><th></th></tr>' + rows + '</table>' +
    '<div class="note">\u6bcf\u4e00\u884c\u90fd\u662f\u201c\u6a21\u578b\u7ed9\u51fa\u8be5\u6863\u4ee5\u4e0a\u6982\u7387\u201d\u7684\u68c0\u9a8c\u671f\u6837\u672c\uff08\u4e0d\u5728\u8bad\u7ec3\u96c6\u5185\uff09\u3002\u201c\u76f8\u5bf9\u57fa\u51c6\u201d\u63a5\u8fd1 1 \u500d\u8bf4\u660e\u6ca1\u6709\u533a\u5206\u80fd\u529b\uff1b\u201c\u6301\u6709 ' + v.hold + ' \u65e5\u540e\u5e73\u5747\u6536\u76ca\u201d\u624d\u662f\u6309\u671f\u6301\u6709\u7684\u771f\u5b9e\u7ed3\u679c\uff0c\u201c\u4e8f\u635f\u5360\u6bd4\u201d\u63d0\u793a\u4e0b\u884c\u98ce\u9669\u3002\u201c\u6b62\u76c8\u7b56\u7565\u201d\u5047\u8bbe\u6536\u76d8\u4ef7\u89e6\u53ca\u76ee\u6807\u7684\u90a3\u5929\u6309\u76ee\u6807\u4ef7\u5356\u51fa\uff08\u7b49\u4ef7\u4e8e\u63d0\u524d\u6302\u597d\u7684\u9650\u4ef7\u5356\u5355\uff09\uff0c\u201c\u6b62\u635f\u201d\u6309\u6536\u76d8\u4ef7\u89e6\u53ca \u22125% \u5f53\u5929\u7684\u6536\u76d8\u4ef7\u5356\u51fa\uff0c\u90fd\u6ca1\u6709\u6263\u624b\u7eed\u8d39\u548c\u5370\u82b1\u7a0e\u3002\u540c\u4e00\u53ea\u80a1\u7968\u76f8\u90bb\u51e0\u5929\u7684\u6837\u672c\u9ad8\u5ea6\u91cd\u53e0\uff0c\u771f\u6b63\u72ec\u7acb\u7684\u6837\u672c\u6bd4\u8868\u91cc\u7684\u6570\u5b57\u5c11\u5f97\u591a\uff0c\u6837\u672c\u91cf\u5c0f\u7684\u884c\u8bf7\u5f53\u4f5c\u566a\u58f0\u3002' + (b3 ? '' : ' \u6ce8\u610f\uff1a\u6ca1\u6709\u4efb\u4f55\u4e00\u6863\u5728\u68c0\u9a8c\u671f\u91cc\u6709\u8db3\u591f\u6837\u672c\uff0c\u6700\u4f4e\u6982\u7387\u9608\u503c\u53ef\u80fd\u8bbe\u5f97\u8fc7\u9ad8\u3002') + '</div>';
  if (v.regimes) el.innerHTML += '<h4 style="margin:14px 0 6px">\u5206\u5927\u76d8\u72b6\u6001\u770b\uff08\u68c0\u9a8c\u671f\uff1b\u201c\u9ad8\u6982\u7387\u6863\u201d\u5373\u9884\u6d4b\u6982\u7387 \u2265 \u4f60\u8bbe\u7684\u9608\u503c\uff09</h4><table><tr><th>\u5927\u76d8\u72b6\u6001</th><th>\u6837\u672c\u6570</th><th>\u5168\u4f53\u57fa\u51c6\u547d\u4e2d\u7387</th><th>\u5168\u4f53\u6301\u6709 ' + v.hold + ' \u65e5\u5e73\u5747\u6536\u76ca</th><th>\u9ad8\u6982\u7387\u6863\u6837\u672c\u6570</th><th>\u9ad8\u6982\u7387\u6863\u547d\u4e2d\u7387</th><th>\u9ad8\u6982\u7387\u6863\u6301\u6709 ' + v.hold + ' \u65e5\u5e73\u5747\u6536\u76ca</th></tr>' + v.regimes.map(function (r) {
    return '<tr><td>' + MK_NAME[r.s] + ' <span class="muted">' + MK_DESC[r.s] + '</span></td><td>' + r.n + '</td><td>' + pct(r.base, 1) + '</td><td class="' + sgn(r.end) + '">' + pct(r.end, 2) + '</td><td>' + r.hiN + '</td><td>' + (r.hiN ? pct(r.hiPrec, 1) : '-') + '</td><td class="' + sgn(r.hiEnd) + '">' + (r.hiN ? pct(r.hiEnd, 2) : '-') + '</td></tr>';
  }).join('') + '</table><div class="note">\u201c\u5927\u76d8\u72b6\u6001\u201d\u6309\u4fe1\u53f7\u65e5\u6536\u76d8\u65f6\u7684\u4e0a\u8bc1\u6307\u6570\u5224\u5b9a\u3002\u82e5\u201c\u5f31\u52bf\u201d\u884c\u7684\u6536\u76ca\u660e\u663e\u66f4\u5dee\uff0c\u8bf4\u660e\u5927\u76d8\u4e0d\u597d\u65f6\u4e0d\u8be5\u51fa\u624b\uff1b\u82e5\u5404\u884c\u5dee\u4e0d\u591a\uff0c\u5219\u8bf4\u660e\u8fd9\u4e2a\u7b80\u5355\u7684\u5927\u76d8\u5224\u65ad\u6ca1\u6709\u5e2e\u52a9\u3002\u6ce8\u610f\uff1a\u8fd9\u53ea\u662f\u7528\u8fc7\u53bb\u6570\u636e\u62c6\u5206\uff0c\u5e76\u4e0d\u80fd\u9884\u6d4b\u660e\u5929\u5927\u76d8\u3002</div>';
}
function drawScanRows() {
  var el = $('scRes'); if (!el) return;
  $('scCount').textContent = SC.hits.length ? '\uff08\u5df2\u53d1\u73b0 ' + SC.hits.length + ' \u53ea\uff09' : '';
  if (!SC.hits.length) { el.innerHTML = '<div class="muted">' + (SC.last && SC.last.phase === 4 ? '\u6ca1\u6709\u80a1\u7968\u7684\u9884\u6d4b\u6982\u7387\u8fbe\u5230\u6240\u8bbe\u9608\u503c\u3002\u8fd9\u901a\u5e38\u662f\u6b63\u5e38\u7684\uff1a\u5927\u6da8\u662f\u5c0f\u6982\u7387\u4e8b\u4ef6\u3002\u53ef\u4ee5\u964d\u4f4e\u201c\u6700\u4f4e\u6982\u7387\u201d\u518d\u8bd5\u3002' : '\u6682\u65e0\u3002\u626b\u63cf\u5f00\u59cb\u540e\uff0c\u53d1\u73b0\u4e00\u53ea\u5c31\u663e\u793a\u4e00\u53ea\u3002') + '</div>'; return; }
  var list = SC.hits.slice(); if (SC.sortP) list.sort(function (a, b) { return b.p - a.p; });
  var th = SC.last ? SC.last.theta : 0.05, now = Date.now();
  el.innerHTML = '<table><tr><th>\u4ee3\u7801</th><th>\u540d\u79f0</th><th>\u6700\u65b0\u4ef7</th><th>\u5f53\u65e5\u6da8\u5e45</th><th>\u9884\u6d4b\u6982\u7387</th><th>\u68c0\u9a8c\u671f\u540c\u6863\u5b9e\u9645\u547d\u4e2d\u7387</th><th>\u540c\u6863\u6b62\u76c8\u7b56\u7565\u5e73\u5747\u6536\u76ca</th><th>\u6570\u636e\u65e5\u671f</th><th></th></tr>' + list.map(function (h) {
    var flag = h.limitUp ? ' <span class="warn">\u524d\u65e5\u6da8\u5e45\u22659.5%</span>' : (/ST/.test(h.name) ? ' <span class="warn">ST</span>' : '');
    return '<tr' + (now - (SC.fresh[h.code] || 0) < 4000 ? ' class="hl"' : '') + '><td><a href="#" data-open="' + esc(h.code) + '" style="color:' + ACC + '">' + esc(h.code) + '</a></td><td>' + esc(h.name) + flag + '</td><td>' + fx(h.price, 2) + '</td><td class="' + sgn(h.chg) + '">' + pct(h.chg) + '</td><td><b>' + pct(h.p, 1) + '</b></td><td>' + (h.band == null ? '<span class="muted">\u65e0\u8db3\u591f\u68c0\u9a8c\u6837\u672c</span>' : pct(h.band, 1) + ' <span class="muted">(n=' + h.bandN + ')</span>') + '</td><td class="' + sgn(h.bandTs) + '">' + (h.band == null ? '-' : pct(h.bandTp, 2) + ' <span class="muted">/ \u52a0\u6b62\u635f ' + pct(h.bandTs, 2) + '</span>') + '</td><td>' + fdate(h.asOf) + '</td><td><button class="small ghost" data-open="' + esc(h.code) + '">\u67e5\u770b</button></td></tr>';
  }).join('') + '</table><div class="note">\u201c\u9884\u6d4b\u6982\u7387\u201d= T+1 \u5f00\u76d8\u4e70\u5165\u540e ' + (SC.last ? SC.last.hold : '') + ' \u4e2a\u4ea4\u6613\u65e5\u5185\u3001\u4efb\u4e00\u5929\u6536\u76d8\u4ef7\u8f83\u4e70\u5165\u4ef7\u6da8\u5e45 \u2265 ' + pct(th, 0) + ' \u7684\u6982\u7387\u3002\u9ad8\u4eae\u884c\u4e3a\u521a\u53d1\u73b0\u7684\u3002</div>';
  Array.prototype.forEach.call(el.querySelectorAll('[data-open]'), function (a) { a.onclick = function (e) { e.preventDefault(); openScanned(a.getAttribute('data-open')); }; });
}
function openScanned(code) {
  var have = state.symbols.filter(function (x) { return x.code === code; })[0];
  if (have) { selectSymbol(code, 'chart'); return; }
  toast('\u6b63\u5728\u6dfb\u52a0 ' + code + ' \u7684\u5386\u53f2\u884c\u60c5\u2026');
  post('/api/feed/add?code=' + encodeURIComponent(code)).then(function (r) { state.pred = {}; return loadSymbols().then(function () { selectSymbol(r.code, 'chart'); }); }).catch(function (e) { toast('\u6dfb\u52a0\u5931\u8d25\uff1a' + e.message); });
}

function initImport() {
  $('btnImport').onclick = function () { $('modal').hidden = false; $('impMsg').textContent = ''; };
  $('impCancel').onclick = function () { $('modal').hidden = true; };
  $('impFile').onchange = function () {
    var f = this.files[0]; if (!f) return;
    var m = f.name.replace(/\.[^.]*$/, ''); if (!$('impCode').value) $('impCode').value = m.replace(/[^A-Za-z0-9._-]/g, '').slice(0, 16);
  };
  $('impGo').onclick = function () {
    var f = $('impFile').files[0], code = $('impCode').value.trim();
    if (!f || !code) { $('impMsg').textContent = '\u8bf7\u586b\u5199\u4ee3\u7801\u5e76\u9009\u62e9\u6587\u4ef6'; return; }
    $('impMsg').textContent = '\u8bfb\u53d6\u5e76\u5bfc\u5165\u4e2d\u2026';
    var fr = new FileReader();
    fr.onload = function () {
      var text; try { text = new TextDecoder($('impEnc').value).decode(fr.result); } catch (e) { $('impMsg').textContent = '\u89e3\u7801\u5931\u8d25\uff1a' + e.message; return; }
      api('/api/import?code=' + encodeURIComponent(code) + '&name=' + encodeURIComponent($('impName').value.trim()), { method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: text }).then(function (r) {
        $('modal').hidden = true; state.pred = {};
        return loadSymbols().then(function () { selectSymbol(r.code, 'chart'); });
      }).catch(function (e) { $('impMsg').textContent = '\u5bfc\u5165\u5931\u8d25\uff1a' + e.message; });
    };
    fr.readAsArrayBuffer(f);
  };
}

function init() {
  Array.prototype.forEach.call(document.querySelectorAll('#tabs button'), function (b) { b.onclick = function () { switchTab(b.getAttribute('data-tab')); }; });
  $('search').oninput = drawSymbols;
  window.addEventListener('resize', function () { var pc = state.pred[state.code + '|' + state.h + '|' + state.pper]; if (state.tab === 'chart') drawChart(); else if (state.tab === 'predict' && pc) drawPredict(pc.d); });
  initImport();
  initFeed();
  loadSymbols().then(function () {
    var hp = {}; location.hash.replace(/^#/, '').split('&').forEach(function (kv) { var a = kv.split('='); if (a[0]) hp[a[0]] = decodeURIComponent(a[1] || ''); });
    if (hp.h) state.h = parseInt(hp.h, 10) || 5;
    if (hp.n) K.count = parseInt(hp.n, 10) || K.count;
    if (hp.ind) K.ind = hp.ind;
    if (hp.per) K.per = hp.per;
    if (hp.pper) state.pper = hp.pper;
    if (state.symbols.length) selectSymbol(hp.code || state.symbols[0].code, hp.tab || 'chart');
    if (hp.run && state.tab === 'backtest') { if (hp.run === 'all') { runCompare(); } else { $('btStrat').value = hp.run; $('btStrat').onchange(); runBacktest(); } }
    loadAlerts(false);
    setInterval(function () { loadAlerts(state.tab === 'watch'); }, 120000);
    loadEvents(false);
    setInterval(function () { loadEvents(state.tab === 'watch'); }, 20000);
    setInterval(function () { if (isTrading() && !document.hidden && state.tab === 'chart') refreshLive(); }, 5000);
    setInterval(function () { if (isTrading() && !document.hidden) { if (state.tab === 'watch') refreshWatch(); else refreshSidebar(); } }, 15000);
  }).catch(function (e) { $('titleBar').innerHTML = '<span class="err">\u65e0\u6cd5\u8fde\u63a5\u670d\u52a1\u5668\uff1a' + esc(e.message) + '</span>'; });
}
init();
})();
