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
      try { j = JSON.parse(t); } catch (e) { throw new Error('服务器返回了无法解析的内容'); }
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
  g.fillText('成交量', x0 + 2, vTop - 1);
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
    info.innerHTML = '<b>' + dlabel(D, idx) + (D.live && idx === D.close.length - 1 ? ' <span class="warn">今日·实时价</span>' : '') + '</b>　开 ' + fx(D.open[idx], 2) + '　高 ' + fx(D.high[idx], 2) + '　低 ' + fx(D.low[idx], 2) +
      '　收 <b class="' + sgn(chg) + '">' + fx(D.close[idx], 2) + '</b>　涨跌 <span class="' + sgn(chg) + '">' + pct(chg) + '</span>　量 ' + Math.round(D.volume[idx] / 100) + '手' +
      '　<span style="color:#f5d742">MA5 ' + fx(D.ma5[idx], 2) + '</span> <span style="color:#4f8cff">MA10 ' + fx(D.ma10[idx], 2) + '</span> <span style="color:#d946ef">MA20 ' + fx(D.ma20[idx], 2) + '</span> <span style="color:#14b8a6">MA60 ' + fx(D.ma60[idx], 2) + '</span>';
  } else {
    var j = K.end - 1, pc = j > 0 ? D.close[j - 1] : D.close[j];
    info.innerHTML = '<b>' + dlabel(D, j) + (D.live ? ' <span class="warn">今日·实时价</span>' : '') + '</b>　收 <b class="' + sgn(D.close[j] / pc - 1) + '">' + fx(D.close[j], 2) + '</b>　' + (D.time ? '' : 'ATR(14) ' + fx(D.atr, 3) + '　') + '<span class="muted">滚轮缩放，拖动平移</span>';
  }
}

function renderChart() {
  var root = $('tab-chart');
  var cs = curSym(), live = !!cs && cs.source === 'sina';
  if (!live) K.per = 'D';
  root.innerHTML = '<div class="card">' + (live ? '<div id="quoteBar"></div>' : '') + '<div class="toolbar">' +
    (live ? '<span class="muted">周期</span><span id="kPer"></span>' : '') +
    '<span class="muted">区间</span><span id="kRange"></span>' +
    '<span class="muted">副图</span><span id="kInd"></span>' +
    '<label class="muted"><input type="checkbox" id="kBoll"> 布林带</label></div>' +
    '<div id="kinfo" style="min-height:22px;font-size:13px;margin-bottom:4px"></div><canvas id="kcv"></canvas></div>';
  if (live) seg('kPer', [['D', '日线'], ['T', '分时'], ['5', '5分'], ['15', '15分'], ['30', '30分'], ['60', '60分']], K.per, function (v) { K.per = v; K.end = 0; if (v === 'D') { drawChart(); refreshLive(); } else { state.mbars = null; refreshLive(); } });
  seg('kRange', [['60', '60'], ['120', '120'], ['250', '250'], ['500', '500'], ['all', '全部']], String(K.count >= 1500 ? 'all' : K.count), function (v) { K.count = v === 'all' ? 99999 : parseInt(v, 10); K.end = 0; drawChart(); });
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
  if (K.per === 'D') { loadBars().then(drawChart); } else { $('kinfo').innerHTML = '<span class="muted">加载中…</span>'; }
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
  if (x >= 1e8) return (x / 1e8).toFixed(2) + '亿'; if (x >= 1e4) return (x / 1e4).toFixed(2) + '万'; return x.toFixed(0);
}
var STATUS = { pre: '盘前', auction: '集合竞价', open: '交易中', lunch: '午间休市', closed: '已收盘', holiday: '休市' };
function drawQuote() {
  var el = $('quoteBar'); if (!el) return;
  var q = state.quote;
  if (!q || q.code.slice(2) !== state.code.replace(/^(sh|sz|bj)/, '')) { el.innerHTML = '<div class="muted" style="margin-bottom:8px">实时报价加载中…</div>'; return; }
  var c = sgn(q.change), t = String(q.time), tm = t.length === 14 ? t.slice(8, 10) + ':' + t.slice(10, 12) + ':' + t.slice(12, 14) : '';
  function cell(k, v, cls) { return '<div><span>' + k + '</span><b' + (cls ? ' class="' + cls + '"' : '') + '>' + v + '</b></div>'; }
  var lvl = ['一', '二', '三', '四', '五'], book = '<table class="book">';
  for (var i = 4; i >= 0; i--) book += '<tr><td class="muted">卖' + lvl[i] + '</td><td class="down">' + (q.ask[i][0] == null ? '-' : fx(q.ask[i][0], 2)) + '</td><td>' + (q.ask[i][1] == null ? '' : q.ask[i][1]) + '</td></tr>';
  for (i = 0; i < 5; i++) book += '<tr><td class="muted">买' + lvl[i] + '</td><td class="up">' + (q.bid[i][0] == null ? '-' : fx(q.bid[i][0], 2)) + '</td><td>' + (q.bid[i][1] == null ? '' : q.bid[i][1]) + '</td></tr>';
  book += '</table>';
  el.innerHTML = '<div class="qbar"><div class="qmain"><div class="qpx ' + c + '">' + fx(q.price, 2) + '</div><div class="' + c + '">' + (q.change > 0 ? '+' : '') + fx(q.change, 2) + '　' + (q.pct > 0 ? '+' : '') + pct(q.pct) + '</div>' +
    '<div class="muted" style="font-size:12px;margin-top:4px"><span class="st ' + q.status + '">' + (STATUS[q.status] || q.status) + '</span> ' + (tm ? tm + ' 更新' : '') + '<br>数据源延迟数秒，仅供参考</div></div>' +
    '<div class="kv qkv">' + cell('今开', fx(q.open, 2), sgn(q.open - q.prevClose)) + cell('最高', fx(q.high, 2), sgn(q.high - q.prevClose)) + cell('最低', fx(q.low, 2), sgn(q.low - q.prevClose)) + cell('昨收', fx(q.prevClose, 2)) +
    cell('成交量', big(q.volume / 100) + '手') + cell('成交额', big(q.amount)) + cell('换手率', pct(q.turnover)) + cell('量比', fx(q.volRatio, 2)) +
    cell('市盈率(动)', fx(q.pe, 2)) + cell('市净率', fx(q.pb, 2)) + cell('涨停价', fx(q.limitUp, 2), 'up') + cell('跌停价', fx(q.limitDown, 2), 'down') + cell('总市值', big(q.mktCap)) + '</div>' + book + '</div>';
}
function refreshLive() {
  var s = curSym(); if (!s || s.source !== 'sina' || state.tab !== 'chart' || !$('kcv')) return Promise.resolve();
  var code = s.code, per = K.per, ps = [];
  ps.push(api('/api/quote?code=' + encodeURIComponent(code)).then(function (q) { if (state.code === code) { state.quote = q; drawQuote(); } })
    .catch(function (e) { if (state.code === code && $('quoteBar')) $('quoteBar').innerHTML = '<div class="warn" style="margin-bottom:8px">实时报价获取失败：' + esc(e.message) + '</div>'; }));
 if (per === 'D' && state.bars && isTrading() && Date.now() - (state.barsAt || 0) > 14000) {
    ps.push(api('/api/bars?code=' + encodeURIComponent(code) + '&limit=3000&live=1').then(function (d) {
      if (state.code !== code || K.per !== 'D') return;
      var old = state.bars ? state.bars.close.length : 0; if (K.end >= old) K.end = 0;
      state.bars = d; state.barsAt = Date.now(); drawChart();
    }).catch(function () {}));
  }
  if (per === 'T') {
    ps.push(api('/api/intraday?code=' + encodeURIComponent(code)).then(function (d) { if (state.code === code && K.per === 'T') { state.intra = d; drawChart(); } })
      .catch(function (e) { if ($('kinfo')) $('kinfo').innerHTML = '<span class="warn">分时数据获取失败：' + esc(e.message) + '</span>'; }));
  } else if (per !== 'D') {
    ps.push(api('/api/minute?code=' + encodeURIComponent(code) + '&period=' + per + '&limit=1500').then(function (d) {
      if (state.code !== code || K.per !== per) return;
      var old = state.mbars ? state.mbars.close.length : 0; if (K.end >= old) K.end = 0;
      state.mbars = d; drawChart();
    }).catch(function (e) { if ($('kinfo')) $('kinfo').innerHTML = '<span class="warn">分钟线获取失败：' + esc(e.message) + '</span>'; }));
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
  if (!n) { g.fillStyle = MUT; g.textAlign = 'center'; g.fillText('暂无分时数据（盘前或非交易日）', W / 2, 120); info.innerHTML = ''; return; }
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
  g.fillStyle = MUT; g.textAlign = 'left'; g.textBaseline = 'top'; g.fillText('成交量(手)', x0 + 2, vTop - 1);
  var k = n - 1;
  if (K.mx >= x0 && K.mx <= x1) {
    var slot = (K.mx - x0) / ww * 240; k = 0;
    for (i = 0; i < n; i++) { if (mIdx(D.t[i]) <= slot + 0.5) k = i; }
    var cx = X(k); g.strokeStyle = 'rgba(216,222,230,.4)'; g.beginPath(); g.moveTo(cx, pTop); g.lineTo(cx, vBot); g.stroke();
    g.beginPath(); g.moveTo(x0, Y(D.p[k])); g.lineTo(x1, Y(D.p[k])); g.stroke();
  }
  var ch = D.p[k] / pc - 1, tt = D.t[k];
  info.innerHTML = '<b>' + fdate(D.date) + ' ' + pad2(Math.floor(tt / 100)) + ':' + pad2(tt % 100) + '</b>　价 <b class="' + sgn(ch) + '">' + fx(D.p[k], 2) + '</b>　涨跌 <span class="' + sgn(ch) + '">' + pct(ch) + '</span>　<span style="color:' + WARN + '">均价 ' + fx(D.a[k], 3) + '</span>　量 ' + Math.round(D.v[k]) + '手　<span class="muted">昨收 ' + fx(pc, 2) + '</span>';
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
var MODEL_NAME = { prior: '基准：历史涨跌率', momentum: '动量规则', logistic: '逻辑回归', mlp: '神经网络(MLP)', volnorm: '波动率正态' };

function renderPredict() {
  var root = $('tab-predict');
  var cs = curSym(), live = !!cs && cs.source === 'sina';
  if (!live) state.pper = 'D';
  root.innerHTML = '<div class="toolbar">' + (live ? '<span class="muted">K线周期</span><span id="pperSeg"></span>' : '') + '<span class="muted">预测跨度（<span id="hUnit"></span>）</span><span id="hSeg"></span>' +
    '<span class="muted" id="hNote"></span></div><div id="predBody"><div class="loading">正在做滚动验证并拟合模型（首次约数秒）…</div></div>';
  function unitTxt() { $('hUnit').textContent = state.pper === 'D' ? '交易日' : '根' + state.pper + '分钟K线'; }
  if (live) seg('pperSeg', [['D', '日线'], ['60', '60分'], ['30', '30分'], ['15', '15分'], ['5', '5分']], state.pper, function (v) { state.pper = v; unitTxt(); loadPredict(); });
  unitTxt();
  seg('hSeg', [['1', '1'], ['3', '3'], ['5', '5'], ['10', '10'], ['20', '20']], String(state.h), function (v) { state.h = parseInt(v, 10); loadPredict(); });
  loadPredict();
}
function loadPredict() {
  var code = state.code, h = state.h, per = state.pper, key = code + '|' + h + '|' + per, body = $('predBody');
  var cached = state.pred[key];
  if (cached && (per === 'D' || Date.now() - cached.at < 300000)) { drawPredict(cached.d); return; }
  body.innerHTML = '<div class="loading">正在做滚动验证并拟合模型（首次约数秒' + (per === 'D' ? '' : '，分钟线样本更多、约 5–10 秒') + '）…</div>';
  api('/api/predict?code=' + encodeURIComponent(code) + '&h=' + h + '&fan=20' + (per === 'D' ? '' : '&per=' + per)).then(function (d) {
    state.pred[key] = { d: d, at: Date.now() };
    if (state.code === code && state.h === h && state.pper === per && state.tab === 'predict') drawPredict(d);
  }).catch(function (e) { if ($('predBody')) $('predBody').innerHTML = '<div class="err">' + esc(e.message) + '</div>'; });
}
function drawPredict(d) {
  var body = $('predBody'); if (!body) return;
  if (d.insufficient) {
    body.innerHTML = '<div class="card"><b>历史数据不足</b><p class="muted">滚动验证需要至少约 ' + d.minBars + ' 根' + (d.period ? d.period + '分钟K线。数据源对分钟线的历史长度有限，请换更长的周期（如 60 分钟或日线）。' : '日线。请导入更长的历史 CSV。') + '</p></div>';
    return;
  }
  var best = null; d.models.forEach(function (m) { if (m.id === 'logistic' || m.id === 'mlp') { if (!best || m.z > best.z) best = m; } });
  var per = d.period, UN = per ? '根 ' + per + ' 分钟K线' : '个交易日', UD = per ? '每根K线' : '日';
  var vtxt = {
    none: '<b>未发现可靠的预测能力</b><br>在 ' + d.testBars + ' ' + (per ? '根样本外K线' : '个样本外交易日') + '（有效独立样本约 ' + fx(best.nEff, 0) + '）里，学习型模型的方向命中率与概率质量没有显著优于“只猜历史涨跌率”的基准。下方的概率应视为接近基准，不宜据此交易。这符合市场的常识：多数股票的涨跌方向几乎无法由历史价量预测，“没有信号”本身就是有价值的结论。' + (per ? '<br><span class="muted">分钟线说明：数据源只提供最近约 ' + Math.round(d.testBars / (240 / per)) + ' 个交易日的验证样本，样本偏短、结论的不确定性更大；分钟级价格还受买卖价差和交易成本影响，即使有微弱信号也很难覆盖成本。</span>' : ''),
    weak: '<b>出现微弱信号（需谨慎）</b><br>最优学习型模型在样本外 z=' + fx(best.z, 2) + '，Brier 技能分 ' + fx(best.brierSkill * 100, 1) + '%。该水平在多只标的、多个周期的反复检验下很容易是偶然。建议换周期/换标的复核，并用回测检验扣除成本后是否仍有优势。',
    notable: '<b>样本外验证显示显著的方向信号</b><br>最优学习型模型 z=' + fx(best.z, 2) + '，命中率 ' + pct(best.accuracy, 1) + '（基准 ' + pct(best.majorityAccuracy, 1) + '），Brier 技能分 ' + fx(best.brierSkill * 100, 1) + '%。这说明该序列在历史上存在可学习的结构，但不保证未来持续，且需计入交易成本；请到“策略回测”页验证可交易性。'
  }[d.verdict];
  var ens = d.ensembleProbUp, ensCls = ens > 0.5 ? 'up' : 'down', noSkill = d.verdict === 'none';
  var base = null; d.models.forEach(function (m) { if (m.id === 'prior') base = m.probUp; });
  var h = '<div class="verdict ' + d.verdict + '">' + vtxt + '</div>';
  h += '<div class="grid2"><div class="card"><h3>未来 ' + d.horizon + ' ' + UN + '上涨概率 <span class="muted" style="font-weight:400">截至 ' + fdate(d.asOf) + (per ? ' ' + pad2(Math.floor(d.asOfTime / 100)) + ':' + pad2(d.asOfTime % 100) : '') + '，' + (per ? '最新价 ' : '收盘 ') + fx(d.lastClose, 2) + '</span></h3>' +
    '<div style="font-size:30px" class="' + (noSkill ? 'muted' : ensCls) + '">' + pct(ens, 1) + (noSkill ? ' <span class="warn" style="font-size:13px">模型无显著预测力，此数字不可用于交易；历史基准上涨率 ' + pct(base, 1) + '</span>' : '') + '</div>' +
    '<div class="meter' + (noSkill ? ' dim' : '') + '"><i style="left:' + (ens * 100).toFixed(1) + '%"></i></div>' +
    '<div class="legend"><span>0% 看跌</span><span style="flex:1;text-align:center">50%</span><span>100% 看涨</span></div>' +
    '<div class="note">取逻辑回归与神经网络的平均。越接近 50%，说明“没有信息”；该概率已用滚动验证检验其校准程度（右侧可靠性图）。</div>' +
    '<h3 style="margin-top:16px">波动区间（EWMA 波动率）</h3><div class="kv">' +
    '<div><span>' + UD + '波动率</span><b>' + pct(d.volDaily, 2) + '</b></div><div><span>年化波动率</span><b>' + pct(d.volAnnual, 1) + '</b></div>' +
    '<div><span>80%区间样本外覆盖率</span><b>' + pct(d.interval.coverage, 1) + '</b></div><div><span>平均区间宽度</span><b>' + pct(d.interval.meanWidth, 1) + '</b></div></div>' +
    '<div class="note">区间覆盖率应接近名义值 80%；明显偏离说明波动率估计失准。价格方向几乎不可预测，但波动率有较强的可预测性。</div></div>' +
    '<div class="card"><h3>价格扇形图（未来 20 ' + (per ? '根K线' : '日') + ' 80% / 95% 区间）</h3><canvas id="fanCv"></canvas><div class="note">中线仅为收缩后的历史漂移，区间由波动率决定；不是价格目标。</div></div></div>';
  h += '<div class="card"><h3>多模型样本外对比（滚动重训，' + fdate(d.testStart) + ' 起' + (per ? '，' + per + ' 分钟K线' : '') + '）</h3><table><tr><th>模型</th><th>当前P(涨)</th><th>命中率</th><th>多数类基准</th><th>Brier</th><th>Brier技能分</th><th>z值</th><th>有效样本</th><th>高信心(≥55%)后均收益</th><th>低信心(≤45%)后均收益</th></tr>';
  d.models.forEach(function (m) {
    var skill = m.brierSkill;
    h += '<tr' + (best && m.id === best.id ? ' class="hl"' : '') + '><td>' + MODEL_NAME[m.id] + '</td><td class="' + (m.probUp > 0.5 ? 'up' : 'down') + '">' + pct(m.probUp, 1) + '</td><td>' + pct(m.accuracy, 1) + '</td><td>' + pct(m.majorityAccuracy, 1) +
      '</td><td>' + fx(m.brier, 4) + '</td><td class="' + (skill > 0 ? 'up' : '') + '">' + fx(skill * 100, 2) + '%</td><td>' + fx(m.z, 2) + '</td><td>' + fx(m.nEff, 0) +
      '</td><td>' + (m.nHigh ? pct(m.avgRetHigh, 2) + ' (' + m.nHigh + ')' : '-') + '</td><td>' + (m.nLow ? pct(m.avgRetLow, 2) + ' (' + m.nLow + ')' : '-') + '</td></tr>';
  });
  h += '</table><div class="note">z 值用有效样本数 n/周期 折算（重叠标签不独立）。Brier 技能分 &gt; 0 才表示优于基准；z ≥ 2.6 且技能分 &gt; 0 才判为“显著”，以抵消同时检验多个模型带来的偶然性。</div></div>';
  h += '<div class="grid2"><div class="card"><h3>可靠性图（预测概率 vs 实际上涨频率）</h3><canvas id="calCv"></canvas><div class="legend" id="calLegend"></div><div class="note">点越贴近对角线，概率越可信；点的大小代表样本数。</div></div>' +
    '<div class="card"><h3>当前特征快照</h3><div class="kv" id="featKv"></div></div></div>';
  body.innerHTML = h;
  var fk = $('featKv'), FN = { ret1: '1日收益', ret5: '5日收益', ret10: '10日收益', ret20: '20日收益', rsi14: 'RSI偏离', macdHist: 'MACD柱/价', vol20: '20日波动', volRatio5v20: '短长波动比', dist20: '距20日均线', volumeRatio: '量比偏离', range60: '60日区间位置' };
  if (per) Object.keys(FN).forEach(function (k) { FN[k] = FN[k].replace(/日/g, '根'); });
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
  g.fillStyle = MUT; g.textAlign = 'center'; g.fillText(d.period ? '现在' : '今天', X(hist - 1), H - 8); g.fillText('+' + d.fan.length + (d.period ? '根' : '日'), X(tot - 1) - 10, H - 8);
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
var STRATS = [['buy_hold', '买入持有'], ['ma_cross', '均线交叉(10/30)'], ['rsi', 'RSI反转(30/70)'], ['macd', 'MACD金叉'], ['boll', '布林带回归'], ['model', '模型信号(P≥55%)']];
var SNAME = {}; STRATS.forEach(function (s) { SNAME[s[0]] = s[1]; });

function renderBacktest() {
  var root = $('tab-backtest');
  root.innerHTML = '<div class="card"><div class="toolbar"><span class="muted">策略</span><select id="btStrat">' +
    STRATS.map(function (s) { return '<option value="' + s[0] + '">' + s[1] + '</option>'; }).join('') + '</select>' +
    '<span id="btParams"></span><button id="btRun">运行回测</button><button id="btAll" class="ghost">对比全部策略</button></div>' +
    '<div class="note">成本：佣金 0.025%（双边）、印花税 0.05%（卖出）、滑点 0.05%（双边）；信号次日开盘价成交，仅做多，不使用未来数据。模型信号策略使用滚动验证期的样本外预测。</div></div><div id="btBody"></div>';
  var sel = $('btStrat');
  function params() {
    var s = sel.value, h = '';
    if (s === 'ma_cross') h = '快 <input id="p_fast" size="3" value="10"> 慢 <input id="p_slow" size="3" value="30">';
    if (s === 'rsi') h = '周期 <input id="p_period" size="3" value="14"> 买入线 <input id="p_lo" size="3" value="30"> 卖出线 <input id="p_hi" size="3" value="70">';
    if (s === 'boll') h = '周期 <input id="p_period" size="3" value="20"> 倍数 <input id="p_k" size="3" value="2">';
    if (s === 'model') h = '预测周期 <input id="p_h" size="3" value="5"> 阈值 <input id="p_threshold" size="4" value="0.55">';
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
  body.innerHTML = '<div class="loading">回测中…（模型策略首次需先做滚动验证）</div>';
  api('/api/backtest?code=' + encodeURIComponent(state.code) + '&strategy=' + s + paramQuery()).then(function (d) {
    var h = '<div class="card"><h3>' + SNAME[s] + ' vs 买入持有 <span class="muted" style="font-weight:400">自 ' + fdate(d.startDate) + '，共 ' + d.trades + ' 笔交易，胜率 ' + pct(d.winRate, 0) + '，平均持有 ' + fx(d.avgHold, 1) + ' 日</span></h3>' +
      '<canvas id="eqCv"></canvas><div class="legend"><span><i style="background:' + ACC + '"></i>策略净值</span><span><i style="background:' + MUT + '"></i>买入持有</span><span>虚线：前 70% / 后 30% 分界</span></div></div>' +
      '<div class="card"><h3>绩效（分段）</h3><table><tr><th>区间</th><th>策略收益</th><th>年化</th><th>夏普</th><th>最大回撤</th><th>持仓占比</th><th>基准收益</th><th>基准回撤</th></tr>' +
      mrow('全区间', d.full) + mrow('前 70%（' + fdate(d.startDate) + ' ~ ' + fdate(d.splitDate) + '）', d.early) + mrow('后 30%（' + fdate(d.splitDate) + ' 起）', d.recent) + '</table>' +
      '<div class="note">若“前段”远好于“后段”，往往说明策略是对历史过拟合或规律已失效。单一回测有幸存者与选择偏差，不等于未来表现。</div></div>';
    h += '<div class="card"><h3>最近交易</h3><table><tr><th>买入日</th><th>卖出日</th><th>买入价</th><th>卖出价</th><th>收益</th></tr>' +
      d.tradeList.slice().reverse().map(function (t) { return '<tr><td>' + fdate(t.in) + '</td><td>' + (t.open ? '持仓中' : fdate(t.out)) + '</td><td>' + fx(t.inPx, 2) + '</td><td>' + (t.open ? '-' : fx(t.outPx, 2)) + '</td><td class="' + sgn(t.ret) + '">' + pct(t.ret, 2) + '</td></tr>'; }).join('') + '</table></div>';
    body.innerHTML = h;
    drawEquity($('eqCv'), [{ d: d.curve.d, v: d.curve.s, c: ACC, n: SNAME[s] }, { d: d.curve.d, v: d.curve.b, c: MUT, n: '买入持有' }], d.splitDate);
  }).catch(function (e) { body.innerHTML = '<div class="err">' + esc(e.message) + '</div>'; });
}
function runCompare() {
  var body = $('btBody'); body.innerHTML = '<div class="loading">逐个运行全部策略（对齐到同一起点）…</div>';
  var results = {}, from = 0;
  var chain = api('/api/backtest?code=' + encodeURIComponent(state.code) + '&strategy=model&h=5').then(function (d) { results.model = d; from = d.startDate; }).catch(function () { from = 0; });
  STRATS.forEach(function (s) {
    if (s[0] === 'model') return;
    chain = chain.then(function () { return api('/api/backtest?code=' + encodeURIComponent(state.code) + '&strategy=' + s[0] + (from ? '&from=' + from : '')).then(function (d) { results[s[0]] = d; }).catch(function () {}); });
  });
  chain.then(function () {
    var h = '<div class="card"><h3>全部策略净值对比（同一起点 ' + (from ? fdate(from) : '各自起点') + '）</h3><canvas id="eqCv"></canvas><div class="legend" id="eqLg"></div></div>' +
      '<div class="card"><h3>汇总</h3><table><tr><th>策略</th><th>全区间收益</th><th>年化</th><th>夏普</th><th>最大回撤</th><th>后30%收益</th><th>交易数</th></tr>';
    var series = [], lg = '', ci = 0;
    STRATS.forEach(function (s) {
      var d = results[s[0]]; if (!d) return;
      h += '<tr><td>' + s[1] + '</td><td class="' + sgn(d.full.totalReturn) + '">' + pct(d.full.totalReturn, 1) + '</td><td>' + pct(d.full.cagr, 1) + '</td><td>' + fx(d.full.sharpe, 2) + '</td><td class="down">' + pct(d.full.maxDrawdown, 1) + '</td><td class="' + sgn(d.recent.totalReturn) + '">' + pct(d.recent.totalReturn, 1) + '</td><td>' + d.trades + '</td></tr>';
      var col = COLORS[ci++ % COLORS.length]; series.push({ d: d.curve.d, v: d.curve.s, c: col, n: s[1] });
      lg += '<span><i style="background:' + col + '"></i>' + s[1] + '</span>';
    });
    body.innerHTML = h + '</table><div class="note">在随机游走型标的上，任何策略的优劣主要是运气；在存在均值回归/动量结构的标的上，匹配结构的策略才会稳定占优。</div></div>';
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
var ALERT_TYPES = [['price_above', '价格高于'], ['price_below', '价格低于'], ['prob_above', '5日上涨概率高于'], ['prob_below', '5日上涨概率低于'], ['rsi_above', 'RSI(14) 高于'], ['rsi_below', 'RSI(14) 低于']];
var ANAME = {}; ALERT_TYPES.forEach(function (a) { ANAME[a[0]] = a[1]; });

function renderWatch() {
  var root = $('tab-watch');
  root.innerHTML = '<div class="card"><h3>自选股</h3><div id="wlBody" class="loading">加载中…</div></div>' +
    '<div class="card"><h3>价格 / 指标 / 预测概率提醒</h3><div class="toolbar"><select id="alCode">' +
    state.symbols.map(function (s) { return '<option value="' + esc(s.code) + '"' + (s.code === state.code ? ' selected' : '') + '>' + esc(s.code + ' ' + s.name) + '</option>'; }).join('') + '</select>' +
    '<select id="alType">' + ALERT_TYPES.map(function (a) { return '<option value="' + a[0] + '">' + a[1] + '</option>'; }).join('') + '</select>' +
    '<input id="alVal" size="8" placeholder="阈值"><button id="alAdd">添加提醒</button></div><div id="alBody" class="loading">加载中…</div>' +
    '<div class="note">提醒由服务端在后台评估（交易时段每 30 秒，其他时间每 10 分钟），不需要打开页面；条件从“未满足”变为“满足”的那一刻推送一次，回落后自动重新武装。价格提醒用实时价，概率/RSI 基于最近一根日线（收盘 15:10 后自动更新自选真实行情）。</div></div>' +
    '<div class="card"><h3>推送通道</h3><div id="ntBody" class="loading">加载中…</div></div>' +
    '<div class="card"><h3>触发记录</h3><div id="evBody" class="loading">加载中…</div></div>';
  $('alAdd').onclick = function () {
    var v = $('alVal').value;
    if (v === '' || isNaN(Number(v))) { alert('请输入数字阈值'); return; }
    post('/api/alerts/add?code=' + encodeURIComponent($('alCode').value) + '&type=' + $('alType').value + '&value=' + encodeURIComponent(v)).then(function () { $('alVal').value = ''; refreshWatch(); }).catch(function (e) { alert(e.message); });
  };
  refreshWatch();
  renderNotify();
  loadEvents(true);
}
var NKIND = [['wecom', '企业微信群机器人'], ['dingtalk', '钉钉群机器人（关键词需含“股票提醒”）'], ['feishu', '飞书群机器人'], ['webhook', '通用 Webhook（JSON）']];
function renderNotify() {
  var el = $('ntBody'); if (!el) return;
  api('/api/notify').then(function (c) {
    el.className = '';
    el.innerHTML = (c.configured ? '<div style="margin-bottom:8px"><b style="color:#22c55e">已配置</b>　' + esc((NKIND.filter(function (k) { return k[0] === c.kind; })[0] || ['', c.kind])[1]) + '　<span class="muted">' + esc(c.host) + '</span>' +
      '　<button class="small ghost" id="ntTest">发送测试</button> <button class="small ghost" id="ntClear">移除</button></div>' : '<div class="muted" style="margin-bottom:8px">尚未配置：提醒只会出现在下方记录和浏览器通知里。</div>') +
      '<div class="toolbar"><select id="ntKind">' + NKIND.map(function (k) { return '<option value="' + k[0] + '"' + (k[0] === c.kind ? ' selected' : '') + '>' + k[1] + '</option>'; }).join('') + '</select>' +
      '<input id="ntUrl" type="password" size="42" placeholder="机器人 Webhook 地址（保存后不再显示）" autocomplete="off"><button id="ntSave">保存</button>' +
      '<button class="ghost" id="ntBrowser">开启浏览器通知</button></div><div id="ntMsg" class="muted"></div>' +
      '<div class="note">Webhook 地址相当于密码，只保存在服务器 data/_notify.txt，不会再通过接口返回。</div>';
    $('ntSave').onclick = function () {
      var u = $('ntUrl').value.trim(); if (!u) { $('ntMsg').textContent = '请填写 Webhook 地址'; return; }
      api('/api/notify/set?kind=' + $('ntKind').value, { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: u }).then(function () { toast('推送通道已保存'); renderNotify(); }).catch(function (e) { $('ntMsg').innerHTML = '<span class="err">' + esc(e.message) + '</span>'; });
    };
    $('ntBrowser').onclick = function () {
      if (!window.Notification) { $('ntMsg').textContent = '此浏览器不支持通知'; return; }
      Notification.requestPermission().then(function (p) { $('ntMsg').textContent = p === 'granted' ? '浏览器通知已开启（页面打开时生效）' : '未获得通知权限'; });
    };
    if ($('ntTest')) {
      $('ntTest').onclick = function () { $('ntMsg').textContent = '发送中…'; post('/api/notify/test').then(function () { $('ntMsg').innerHTML = '<span style="color:#22c55e">测试消息已发送，请到群里确认</span>'; }).catch(function (e) { $('ntMsg').innerHTML = '<span class="err">' + esc(e.message) + '</span>'; }); };
      $('ntClear').onclick = function () { if (confirm('移除推送通道？')) post('/api/notify/clear').then(renderNotify); };
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
        if (window.Notification && Notification.permission === 'granted') { try { new Notification('股票提醒', { body: e.text }); } catch (x) { } }
      });
    }
    EV.seen = maxId;
    var el = $('evBody'); if (!render && !el) return; if (!el) return;
    el.className = '';
    el.innerHTML = list.length ? '<table><tr><th>时间</th><th style="text-align:left">内容</th><th>推送</th></tr>' + list.map(function (e) {
      var st = e.status === 'ok' ? '<span style="color:#22c55e">已推送</span>' : (e.status === 'none' ? '<span class="muted">未配置通道</span>' : '<span class="err" style="padding:0" title="' + esc(e.status) + '">失败</span>');
      return '<tr><td>' + fts(e.ts) + '</td><td style="text-align:left;white-space:normal">' + esc(e.text) + '</td><td>' + st + '</td></tr>';
    }).join('') + '</table>' : '<div class="muted">还没有触发过提醒。</div>';
  }).catch(function () {});
}
function refreshWatch() {
  api('/api/watch').then(function (list) {
    var el = $('wlBody'); if (!el) return;
    el.className = '';
    el.innerHTML = list.length ? '<table><tr><th>代码</th><th>名称</th><th>最新价</th><th>涨跌幅</th><th>RSI(14)</th><th>5日上涨概率</th><th>数据截至</th><th></th></tr>' +
      list.map(function (w) {
        if (w.missing) return '<tr><td>' + esc(w.code) + '</td><td colspan="6" class="muted">数据已删除</td><td><button class="small ghost" data-rm="' + esc(w.code) + '">移除</button></td></tr>';
        return '<tr><td><a href="#" data-go="' + esc(w.code) + '" style="color:' + ACC + '">' + esc(w.code) + '</a></td><td>' + esc(w.name) + '</td><td>' + fx(w.close, 2) + '</td><td class="' + sgn(w.chg) + '">' + pct(w.chg) + '</td><td>' + fx(w.rsi, 1) + '</td><td class="' + (w.probUp5 > 0.5 ? 'up' : 'down') + '">' + (w.probUp5 == null ? '数据不足' : pct(w.probUp5, 1)) + '</td><td>' + fdate(w.asOf) + '</td><td><button class="small ghost" data-rm="' + esc(w.code) + '">移除</button></td></tr>';
      }).join('') + '</table>' : '<div class="muted">还没有自选股。在左侧列表点 ☆ 即可加入。</div>';
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
    el.innerHTML = list.length ? '<table><tr><th>状态</th><th>标的</th><th>条件</th><th>当前值</th><th></th></tr>' + list.map(function (a) {
      var isProb = a.type.indexOf('prob') === 0, val = isProb ? pct(a.value, 0) : fx(a.value, 2), cur = a.current == null ? '数据不足' : (isProb ? pct(a.current, 1) : fx(a.current, 2));
      return '<tr' + (a.triggered ? ' class="hl"' : '') + '><td>' + (a.triggered ? '<b class="up">已触发</b>' : '<span class="muted">未触发</span>') + '</td><td>' + esc(a.code + ' ' + a.name) + '</td><td>' + ANAME[a.type] + ' ' + val + '</td><td>' + cur + '</td><td><button class="small ghost" data-del="' + a.id + '">删除</button></td></tr>';
    }).join('') + '</table>' : '<div class="muted">暂无提醒。</div>';
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
    d.innerHTML = '<span class="star' + (s.watch ? ' on' : '') + '" title="自选">' + (s.watch ? '★' : '☆') + '</span><div class="nm"><div>' + esc(s.name || s.code) + '</div><div class="cd">' + esc(s.code) + (s.source === 'demo' ? '<span class="src">模拟</span>' : (s.source === 'sina' ? '<span class="src live">行情</span>' : '<span class="src">CSV</span>')) + '</div></div>' +
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
  return days > 4 ? '<span class="warn">数据已 ' + Math.floor(days) + ' 天未更新，建议点击“更新数据”</span>' : '';
}
function toast(msg) {
  var t = document.createElement('div');
  t.style.cssText = 'position:fixed;right:20px;bottom:20px;background:#1e2631;border:1px solid #2a3441;border-radius:8px;padding:10px 16px;z-index:20;max-width:360px;font-size:13px';
  t.textContent = msg; document.body.appendChild(t); setTimeout(function () { t.remove(); }, 4500);
}
function refreshFeed(code) {
  var btn = $('btnRefresh'); btn.disabled = true; btn.textContent = '更新中…';
  toast('正在拉取最新行情…');
  return post('/api/feed/refresh?code=' + encodeURIComponent(code || 'ALL')).then(function (r) {
    var ok = r.filter(function (x) { return x.ok; }), bad = r.filter(function (x) { return !x.ok; }), nb = 0;
    ok.forEach(function (x) { nb += x.newBars; });
    state.pred = {}; state.bars = null;
    toast(r.length ? ('已更新 ' + ok.length + ' 只，新增 ' + nb + ' 根日线' + (bad.length ? '；失败 ' + bad.length + ' 只' : '')) : '没有可更新的真实行情标的（先点“添加A股”）');
    return loadSymbols().then(function () { if (state.code) selectSymbol(state.code); });
  }).catch(function (e) { toast('更新失败：' + e.message); }).then(function () { btn.disabled = false; btn.textContent = '更新行情'; });
}

function initFeed() {
  var popular = [['sh600519', '贵州茅台'], ['sh600036', '招商银行'], ['sz000001', '平安银行'], ['sz300750', '宁德时代'], ['sh000001', '上证指数'], ['sh510300', '沪深300ETF']];
  $('feedChips').innerHTML = popular.map(function (p) { return '<span class="chip" data-code="' + p[0] + '">' + p[1] + ' ' + p[0].slice(2) + '</span>'; }).join('');
  Array.prototype.forEach.call($('feedChips').children, function (c) { c.onclick = function () { addFeed(c.getAttribute('data-code')); }; });
  $('btnFeed').onclick = function () { $('feedModal').hidden = false; $('feedMsg').textContent = ''; $('feedQ').focus(); };
  $('feedClose').onclick = function () { $('feedModal').hidden = true; };
  $('btnRefresh').onclick = function () { refreshFeed('ALL'); };
  function search() {
    var q = $('feedQ').value.trim(); if (!q) return;
    $('feedMsg').textContent = '搜索中…';
    api('/api/feed/search?q=' + encodeURIComponent(q)).then(function (list) {
      $('feedMsg').textContent = list.length ? '' : '没有找到匹配的A股/指数/ETF，可直接输入6位代码后点“搜索”再添加。';
      var el = $('feedRes'); el.innerHTML = list.map(function (x) {
        return '<div class="item"><span class="nm">' + esc(x.name) + ' <span class="muted">' + esc(x.code) + ' · ' + esc(x.kind) + '</span></span><button class="small' + (x.added ? ' ghost' : '') + '" data-add="' + esc(x.code) + '">' + (x.added ? '重新拉取' : '添加') + '</button></div>';
      }).join('');
      Array.prototype.forEach.call(el.querySelectorAll('[data-add]'), function (b) { b.onclick = function () { addFeed(b.getAttribute('data-add')); }; });
      if (!list.length && /^(sh|sz|bj)?\d{6}$/i.test(q)) { el.innerHTML = '<div class="item"><span class="nm">' + esc(q.toLowerCase()) + '</span><button class="small" data-add="' + esc(q) + '">直接添加</button></div>'; el.querySelector('[data-add]').onclick = function () { addFeed(q); }; }
    }).catch(function (e) { $('feedMsg').textContent = '搜索失败：' + e.message; });
  }
  $('feedGo').onclick = search;
  $('feedQ').onkeydown = function (e) { if (e.key === 'Enter') search(); };
}
function addFeed(code) {
  $('feedMsg').textContent = '正在拉取 ' + code + ' 的历史日线与复权因子（约几秒）…';
  post('/api/feed/add?code=' + encodeURIComponent(code)).then(function (r) {
    state.pred = {};
    $('feedModal').hidden = true;
    toast('已添加 ' + r.name + '（' + r.bars + ' 根日线，' + fdate(r.first) + ' ~ ' + fdate(r.last) + '）。' + (r.note || ''));
    return loadSymbols().then(function () { selectSymbol(r.code, 'chart'); });
  }).catch(function (e) { $('feedMsg').textContent = '添加失败：' + e.message; });
}

function selectSymbol(code, tab) {
  state.code = code; state.bars = null; state.mbars = null; state.intra = null; state.quote = null; K.end = 0;
  drawSymbols();
  var s = state.symbols.filter(function (x) { return x.code === code; })[0];
  if (s) {
    $('titleBar').innerHTML = '<h2>' + esc(s.name || s.code) + '</h2><span class="muted">' + esc(s.code) + '　' + s.bars + ' 根日线　' + fdate(s.first) + ' ~ ' + fdate(s.last) + '</span>' +
      '<span class="' + sgn(s.chg) + '" style="font-size:18px">' + fx(s.close, 2) + '　' + pct(s.chg) + '</span>' + staleHtml(s) + '<span class="spacer"></span>' + (s.source === 'sina' ? '<button class="small ghost" id="btnUpd">更新数据</button> ' : '') + '<button class="small ghost" id="btnDel">删除数据</button>';
    if ($('btnUpd')) { $('btnUpd').onclick = function () { refreshFeed(s.code); }; }
    $('btnDel').onclick = function () {
      if (!confirm('删除 ' + s.code + ' 的数据与本地CSV索引？（模拟数据重启后会重新生成）')) return;
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
var PHASE_TXT = ['空闲', '获取股票列表', '训练模型', '扫描中', '已完成', '出错'];
function renderScan() {
  var root = $('tab-scan');
  root.innerHTML = '<div class="card"><h3>全市场扫描：找出“T+1 开盘买入后、未来几天可能大涨”的 A 股</h3>' +
    '<div class="toolbar"><span class="muted">买入后</span><select id="scH"><option value="1">1</option><option value="3">3</option><option value="5" selected>5</option><option value="10">10</option></select><span class="muted">个交易日内，任一天收盘价较买入价涨幅 ≥</span><select id="scTheta"><option value="0.03">3%</option><option value="0.05">5%</option><option value="0.08" selected>8%</option><option value="0.10">10%</option><option value="0.15">15%</option></select>' +
    '<span class="muted">只显示预测概率 ≥</span><input id="scP" size="4" value="30"><span class="muted">%</span>' +
    '<button id="scGo">开始扫描</button><button id="scStop" class="ghost" hidden>停止</button>' +
    '<label class="muted" title="去掉“前一天已涨≥5%”的股票：这类股票的规律最强也最难买到，排除后看模型是否还有预测力"><input type="checkbox" id="scExcl"> 排除前一日涨幅≥5%的股票</label>' +
    '<label class="muted" title="上证指数收在20日均线下方、且近5日下跌（“弱势”）时，不输出任何推荐：大盘弱的时候个股普遍跟着跌"><input type="checkbox" id="scGate"> 大盘弱势时不推荐</label>' +
    '<label class="muted"><input type="checkbox" id="scSort"> 按概率排序</label></div><div id="scMkt"></div>' +
    '<div class="note">买入价按“信号日（收盘后扫描）的下一个交易日开盘价”计算（你只能 T+1 买入），最早再下一个交易日才能卖出（T+1 规则）。统计的是买入后 H 个交易日里任意一天的最高<b>收盘价</b>，你不可能每次都卖在最高点，所以请同时看下面的“持有到第 H 天收盘的平均收益”。H=1 即超短线：买入后第一个可卖日（次日）收盘价是否达到目标。已排除北交所 920、科创板 688 和 ST/*ST 股票；信号日收在涨停、或买入日开盘就在涨停（买不进）的样本不计入训练、检验和结果。</div>' +
    '<div id="scProg"></div>' +
    '<div class="toolbar" style="margin-top:8px"><label title="服务开着时，每个交易日 15:10 之后自动用上面的参数扫描一次，结束后把前 5 只推送到“自选与提醒”里配置的机器人；结果同样记入历史战绩"><input type="checkbox" id="scAuto"> 每个交易日收盘后（15:10）自动扫描并推送</label><span id="scAutoSt" class="muted"></span></div></div>' +
    '<div class="card"><h3>扫描结果 <span id="scCount" class="muted" style="font-weight:400"></span></h3><div id="scRes"><div class="muted">点击“开始扫描”。发现一只就显示一只，不必等扫描结束。</div></div></div>' +
    '<div class="card"><h3>这些概率靠不靠谱？（模型在没参与训练的最近约 250 个交易日上的检验）</h3><div id="scVal"><div class="muted">首次扫描会先训练模型，之后显示。</div></div></div>' +
    '<div class="card"><h3>历史战绩：每次扫描的结果，隔天自动核对真实涨跌 <button class="small ghost" id="trRef">刷新</button></h3><div id="scTrack"><div class="loading">加载中…</div></div></div>' +
    '<div class="card"><h3>请务必看清</h3><div class="note" style="font-size:13px;line-height:1.7">' +
    '① 这不是“保证大涨”的名单。模型是用约 300 只股票、近 4 年日线训练的统计模型，只给出“相对基准更高”的概率；上方检验表是它在训练外时期的真实命中率，请以此为准，不要看概率数字本身。<br>' +
    '② 模型的优势主要来自“近期波动大”“前一天已大涨”等规律，这类股票涨得多，跌得也多——请看检验表里“持有期末亏损≥5% 占比”和“持有到期平均收益”。<br>' +
    '③ 买入价取的是历史上真实的 T+1 开盘价（已含高开）；但如果 T+1 开盘就涨停，你实际买不到；也没有计入佣金、印花税、滑点。“任一天最高收盘”只是上限，你需要自己决定何时卖，持有到期的收益更接近现实。<br>' +
    '④ 数据来自新浪公开接口，仅供个人研究与学习，不构成任何投资建议。</div></div>';
  $('scSort').checked = SC.sortP;
  $('scSort').onchange = function () { SC.sortP = this.checked; drawScanRows(); };
  $('scGo').onclick = function () {
    var pm = parseFloat($('scP').value); if (isNaN(pm) || pm < 2 || pm > 95) { toast('概率阈值请填 2–95 之间的数字'); return; }
    SC.since = 0; SC.hits = []; SC.fresh = {}; drawScanRows();
    post('/api/scan/start?theta=' + $('scTheta').value + '&h=' + $('scH').value + '&pmin=' + (pm / 100) + ($('scExcl').checked ? '&excl=1' : '') + ($('scGate').checked ? '&mg=1' : '')).then(function (d) { SC.runId = d.runId; pollScan(); }).catch(function (e) { toast(e.message); });
  };
  $('trRef').onclick = loadTrack; loadTrack();
  var saveAuto = function () {
    var pm = parseFloat($('scP').value); if (isNaN(pm) || pm < 2 || pm > 95) { toast('概率阈值请填 2–95 之间的数字'); $('scAuto').checked = false; return; }
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
  el.textContent = '已开启：买入后 ' + d.hold + ' 日内 ≥' + Math.round(d.theta * 100) + '%、概率 ≥' + Math.round(d.pmin * 100) + '%；' + (d.lastDay ? '上次自动扫描 ' + fdate(d.lastDay) + '；' : '尚未自动扫描过；') + (d.push ? '结果会推送到你配置的机器人' : '还没配置推送机器人（在“自选与提醒”里设置），结果只记入历史战绩') + (d.running ? '；正在扫描中…' : '');
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
var MK_NAME = ['强势', '回调', '反弹', '弱势'];
var MK_DESC = ['指数在20日线上方且近5日上涨', '指数在20日线上方但近5日下跌', '指数在20日线下方但近5日上涨', '指数在20日线下方且近5日下跌'];
function drawMarket(d) {
  var el = $('scMkt'); if (!el) return;
  var m = d.market; if (!m) { el.innerHTML = ''; return; }
  var cls = m.state === 0 ? 'up' : (m.state === 3 ? 'down' : '');
  var tail = '';
  if (d.gate && m.state === 3) tail = '　<b class="warn">已按设置暂停推荐' + (d.gated ? '（拦下 ' + d.gated + ' 只达标股票）' : '') + '</b>';
  else if (m.state === 3) tail = '　<span class="warn">大盘偏弱，个股普遍跟着跌，下面的推荐请格外谨慎，或直接观望。</span>';
  else if (m.state === 1 || m.state === 2) tail = '　<span class="muted">大盘不明朗，仓位宜轻。</span>';
  el.innerHTML = '<div class="note" style="margin-top:8px">大盘（上证指数 ' + fdate(m.date) + '）：<b class="' + cls + '">' + MK_NAME[m.state] + '</b>（' + MK_DESC[m.state] + '）；当日 <span class="' + sgn(m.r1) + '">' + pct(m.r1, 2) + '</span>，5日 <span class="' + sgn(m.r5) + '">' + pct(m.r5, 2) + '</span>，距20日线 <span class="' + sgn(m.dist) + '">' + pct(m.dist, 2) + '</span>' + tail + '</div>';
}
function drawScanStatus(d) {
  var run = d.phase >= 1 && d.phase <= 3, pctDone = 0, line = '';
  if (d.phase === 2) pctDone = d.trainLoaded / d.trainTotal; else if (d.phase >= 3) pctDone = d.universe ? d.pos / d.universe : 0;
  else if (d.phase === 1) pctDone = Math.min(1, d.universe / 5600);
  var eta = '';
  if (d.phase === 3) {
    if (!SC.rate || SC.rate.run !== d.runId || d.pos < SC.rate.pos0) SC.rate = { run: d.runId, t0: Date.now(), pos0: d.pos };
    var dt = (Date.now() - SC.rate.t0) / 1000, dp = d.pos - SC.rate.pos0;
    if (dt > 8 && dp > 10) { var remS = dt / dp * (d.universe - d.pos); eta = '，预计还需约 ' + Math.max(1, Math.round(remS / 60)) + ' 分钟'; }
  }
  line = '<b>' + PHASE_TXT[d.phase] + '</b>　' + esc(d.msg) + (d.phase >= 3 ? '　已处理 ' + d.pos + ' / ' + d.universe + '（有效 ' + d.scanned + '，停牌/数据不足 ' + d.skipped + '，失败 ' + d.failed + '）' : '') + '　用时 ' + Math.floor(d.elapsed / 60) + ' 分 ' + (d.elapsed % 60) + ' 秒' + eta;
  $('scProg').innerHTML = '<div class="meter" style="height:10px;background:#2a3441;margin:6px 0"><span style="display:block;height:10px;border-radius:5px;background:' + ACC + ';width:' + (pctDone * 100).toFixed(1) + '%"></span></div><div style="font-size:13px">' + line + '</div>' +
    (d.marketOpen && d.phase >= 1 ? '<div class="warn" style="margin-top:4px">当前处于交易时段：未收盘的当日K线已被忽略，预测的是“今天”而不是“明天”。建议收盘后（15:10 以后）扫描。</div>' : '');
  $('scGo').hidden = run; $('scStop').hidden = !run; $('scGo').textContent = d.phase === 0 ? '开始扫描' : '重新扫描';
  $('scTheta').disabled = run; $('scH').disabled = run; $('scP').disabled = run; $('scExcl').disabled = run; $('scGate').disabled = run;
  drawMarket(d);
  if (d.phase === 4 && SC.trackedRun !== d.runId) { SC.trackedRun = d.runId; loadTrack(); }
  if (d.validation) drawScanVal(d.validation);
}
function loadTrack() {
  api('/api/scan/track').then(function (t) {
    var el = $('scTrack'); if (!el) return;
    if (!t.total) { el.innerHTML = '<div class="muted">还没有记录。每次扫描发现的股票都会自动记下来，等 T+1 买入后的这几天行情出来，系统自动核对它们真实涨了多少。这里累计的才是模型的“实盘战绩”，比上面的历史检验更可信。</div>'; return; }
    var runs = t.runs.map(function (r) {
      return '<tr><td>' + fdate(r.asOf) + '</td><td>' + r.hold + ' 日内 ≥' + pct(r.theta, 0) + (r.excl ? ' <span class="muted">(排除前日大涨)</span>' : '') + '</td><td>' + r.n + '</td><td>' + r.verified + '</td><td>' + r.hit + '</td><td>' + (r.verified ? pct(r.hit / r.verified, 1) : '-') + '</td><td>' + pct(r.base, 1) + '</td><td class="' + sgn(r.avgMax) + '">' + (r.avgMax == null ? '-' : pct(r.avgMax, 2)) + '</td><td class="' + sgn(r.avgEnd) + '">' + (r.avgEnd == null ? '-' : pct(r.avgEnd, 2)) + '</td><td class="' + sgn(r.avgEnd == null ? 0 : r.avgEnd - t.cost) + '">' + (r.avgEnd == null ? '-' : pct(r.avgEnd - t.cost, 2)) + '</td><td class="' + sgn(r.topEnd == null ? 0 : r.topEnd - t.cost) + '">' + (r.topEnd == null ? '-' : pct(r.topEnd - t.cost, 2) + ' <span class="muted">(' + r.topV + '只)</span>') + '</td></tr>';
    }).join('');
    var sim = t.runs.filter(function (r) { return r.topEnd != null; }).slice().reverse();
    var eq = 1, wins = 0, sumNet = 0;
    sim.forEach(function (r) { var x = r.topEnd - t.cost; eq *= 1 + x; sumNet += x; if (x > 0) wins++; });
    var simHtml = sim.length ? '<div class="kv" style="margin-top:10px"><div><span>模拟盘：每批买概率最高的前 ' + t.topN + ' 只，等权</span><b>' + sim.length + ' 批已核对</b></div><div><span>每批平均净收益（已扣 ' + pct(t.cost, 2) + ' 成本）</span><b class="' + sgn(sumNet) + '">' + pct(sumNet / sim.length, 2) + '</b></div><div><span>净收益为正的批次</span><b>' + wins + ' / ' + sim.length + '</b></div><div><span>各批复利累计净值</span><b class="' + sgn(eq - 1) + '">' + eq.toFixed(4) + '</b></div></div><div class="note">模拟盘净值是把每批当作独立的一笔买入，忽略批次之间资金占用重叠，仅作参考；批次少于 20 个时波动主要来自运气。</div>' : '<div class="note">模拟盘（每批买概率最高的前 ' + t.topN + ' 只、扣除 ' + pct(t.cost, 2) + ' 往返成本）要等至少一批走完买入后的持有期才会出现。</div>';
    var items = t.items.map(function (i) {
      var res = i.status === 1 ? '最高 <span class="' + sgn(i.maxRet) + '">' + pct(i.maxRet, 2) + '</span>　第' + i.hold + '日收盘 <span class="' + sgn(i.endRet) + '">' + pct(i.endRet, 2) + '</span>' + (i.maxRet >= i.theta ? ' <b class="up">✓ 达标</b>' : '') + ' <span class="muted">买入 ' + fdate(i.entryDate).slice(5) + '</span>' : (i.status === 2 ? '<span class="muted">无法核对</span>' : '<span class="muted">等待买入后 ' + i.hold + ' 个交易日走完</span>');
      return '<tr><td>' + fdate(i.asOf) + '</td><td>' + esc(i.code) + '</td><td>' + esc(i.name) + '</td><td>' + pct(i.p, 1) + '</td><td style="text-align:left">' + res + '</td></tr>';
    }).join('');
    el.innerHTML = '<div class="kv"><div><span>已记录</span><b>' + t.total + ' 只次</b></div><div><span>已核对</span><b>' + t.verified + '</b></div><div><span>待核对</span><b>' + t.pending + '</b></div>' +
      '<div><span>实际达标</span><b>' + t.hit + '</b></div><div><span>实盘命中率</span><b>' + (t.precision == null ? '-' : pct(t.precision, 1)) + '</b></div><div><span>持有期内最高涨幅均值</span><b class="' + sgn(t.avgMax) + '">' + (t.avgMax == null ? '-' : pct(t.avgMax, 2)) + '</b></div><div><span>持有到期平均收益</span><b class="' + sgn(t.avgEnd) + '">' + (t.avgEnd == null ? '-' : pct(t.avgEnd, 2)) + '</b></div></div>' +
      simHtml + '<table style="margin-top:10px"><tr><th>数据日期</th><th>目标</th><th>入选</th><th>已核对</th><th>达标</th><th>命中率</th><th>检验期基准</th><th>期内最高涨幅均值</th><th>持有到期平均收益</th><th>全部入选净收益</th><th>前 ' + t.topN + ' 只净收益</th></tr>' + runs + '</table>' +
      '<details style="margin-top:8px"><summary class="muted" style="cursor:pointer">逐只明细（最近 80 条）</summary><table><tr><th>数据日期</th><th>代码</th><th>名称</th><th>预测概率</th><th style="text-align:left">结果</th></tr>' + items + '</table></details>' +
      '<div class="note">买入价 = 信号日下一个交易日的开盘价；涨幅用未复权价格计算，遇到除权除息会偏低。样本少于几十只时，命中率只是噪声，请持续累计。</div>';
  }).catch(function () {});
}
function drawScanVal(v) {
  var el = $('scVal'); if (!el) return;
  var b3 = v.bins.filter(function (b) { return b.p >= 0.3 && b.n >= 20; })[0];
  var rows = v.bins.map(function (b) {
    var lift = v.baseTest > 0 && b.n > 0 ? b.precision / v.baseTest : null;
    return '<tr><td>≥ ' + pct(b.p, 0) + '</td><td>' + b.n + '</td><td>' + b.hit + '</td><td>' + (b.n ? pct(b.precision, 1) : '-') + '</td><td class="' + (lift != null && lift > 1.5 ? 'up' : '') + '">' + (lift != null ? lift.toFixed(2) + ' 倍' : '-') + '</td><td class="' + sgn(b.avgEnd) + '">' + (b.n ? pct(b.avgEnd, 2) : '-') + '</td><td class="' + sgn(b.tp) + '">' + (b.n ? pct(b.tp, 2) : '-') + '</td><td class="' + sgn(b.tpsl) + '">' + (b.n ? pct(b.tpsl, 2) : '-') + '</td><td>' + (b.n ? pct(b.loss, 1) : '-') + '</td><td class="muted">' + (b.n < 50 ? '样本太少，参考价值低' : '') + '</td></tr>';
  }).join('');
  el.innerHTML = '<div class="kv"><div><span>目标</span><b>买入后 ' + v.hold + ' 日内最高收盘 ≥ +' + pct(v.theta, 0) + (v.excl ? '<br><small class="muted">已排除前日涨≥5%</small>' : '') + '</b></div><div><span>训练样本（股票-日）</span><b>' + v.trainN + '</b></div><div><span>检验样本（' + fdate(v.cutDate) + ' 起）</span><b>' + v.testN + '</b></div>' +
    '<div><span>检验期基准发生率</span><b>' + pct(v.baseTest, 1) + '</b></div><div><span>基准：持有 ' + v.hold + ' 日平均收益</span><b class="' + sgn(v.baseEnd) + '">' + pct(v.baseEnd, 2) + '</b></div><div><span>基准：止盈策略 / 加止损</span><b><span class="' + sgn(v.baseTp) + '">' + pct(v.baseTp, 2) + '</span> / <span class="' + sgn(v.baseTs) + '">' + pct(v.baseTs, 2) + '</span></b></div><div><span>Brier 技能分</span><b class="' + (v.brierSkill > 0 ? 'up' : '') + '">' + fx(v.brierSkill * 100, 1) + '%</b></div></div>' +
    '<table style="margin-top:10px"><tr><th>预测概率</th><th>检验样本数</th><th>实际达标次数</th><th>实际命中率</th><th>相对基准</th><th>持有 ' + v.hold + ' 日后平均收益</th><th>涨到 +' + pct(v.theta, 0) + ' 就卖，否则持有到期</th><th>同上，再加 −5% 止损</th><th>持有期末亏损 ≥5% 占比</th><th></th></tr>' + rows + '</table>' +
    '<div class="note">每一行都是“模型给出该档以上概率”的检验期样本（不在训练集内）。“相对基准”接近 1 倍说明没有区分能力；“持有 ' + v.hold + ' 日后平均收益”才是按期持有的真实结果，“亏损占比”提示下行风险。“止盈策略”假设收盘价触及目标的那天按目标价卖出（等价于提前挂好的限价卖单），“止损”按收盘价触及 −5% 当天的收盘价卖出，都没有扣手续费和印花税。同一只股票相邻几天的样本高度重叠，真正独立的样本比表里的数字少得多，样本量小的行请当作噪声。' + (b3 ? '' : ' 注意：没有任何一档在检验期里有足够样本，最低概率阈值可能设得过高。') + '</div>';
  if (v.regimes) el.innerHTML += '<h4 style="margin:14px 0 6px">分大盘状态看（检验期；“高概率档”即预测概率 ≥ 你设的阈值）</h4><table><tr><th>大盘状态</th><th>样本数</th><th>全体基准命中率</th><th>全体持有 ' + v.hold + ' 日平均收益</th><th>高概率档样本数</th><th>高概率档命中率</th><th>高概率档持有 ' + v.hold + ' 日平均收益</th></tr>' + v.regimes.map(function (r) {
    return '<tr><td>' + MK_NAME[r.s] + ' <span class="muted">' + MK_DESC[r.s] + '</span></td><td>' + r.n + '</td><td>' + pct(r.base, 1) + '</td><td class="' + sgn(r.end) + '">' + pct(r.end, 2) + '</td><td>' + r.hiN + '</td><td>' + (r.hiN ? pct(r.hiPrec, 1) : '-') + '</td><td class="' + sgn(r.hiEnd) + '">' + (r.hiN ? pct(r.hiEnd, 2) : '-') + '</td></tr>';
  }).join('') + '</table><div class="note">“大盘状态”按信号日收盘时的上证指数判定。若“弱势”行的收益明显更差，说明大盘不好时不该出手；若各行差不多，则说明这个简单的大盘判断没有帮助。注意：这只是用过去数据拆分，并不能预测明天大盘。</div>';
}
function drawScanRows() {
  var el = $('scRes'); if (!el) return;
  $('scCount').textContent = SC.hits.length ? '（已发现 ' + SC.hits.length + ' 只）' : '';
  if (!SC.hits.length) { el.innerHTML = '<div class="muted">' + (SC.last && SC.last.phase === 4 ? '没有股票的预测概率达到所设阈值。这通常是正常的：大涨是小概率事件。可以降低“最低概率”再试。' : '暂无。扫描开始后，发现一只就显示一只。') + '</div>'; return; }
  var list = SC.hits.slice(); if (SC.sortP) list.sort(function (a, b) { return b.p - a.p; });
  var th = SC.last ? SC.last.theta : 0.05, now = Date.now();
  el.innerHTML = '<table><tr><th>代码</th><th>名称</th><th>最新价</th><th>当日涨幅</th><th>预测概率</th><th>检验期同档实际命中率</th><th>同档止盈策略平均收益</th><th>数据日期</th><th></th></tr>' + list.map(function (h) {
    var flag = h.limitUp ? ' <span class="warn">前日涨幅≥9.5%</span>' : (/ST/.test(h.name) ? ' <span class="warn">ST</span>' : '');
    return '<tr' + (now - (SC.fresh[h.code] || 0) < 4000 ? ' class="hl"' : '') + '><td><a href="#" data-open="' + esc(h.code) + '" style="color:' + ACC + '">' + esc(h.code) + '</a></td><td>' + esc(h.name) + flag + '</td><td>' + fx(h.price, 2) + '</td><td class="' + sgn(h.chg) + '">' + pct(h.chg) + '</td><td><b>' + pct(h.p, 1) + '</b></td><td>' + (h.band == null ? '<span class="muted">无足够检验样本</span>' : pct(h.band, 1) + ' <span class="muted">(n=' + h.bandN + ')</span>') + '</td><td class="' + sgn(h.bandTs) + '">' + (h.band == null ? '-' : pct(h.bandTp, 2) + ' <span class="muted">/ 加止损 ' + pct(h.bandTs, 2) + '</span>') + '</td><td>' + fdate(h.asOf) + '</td><td><button class="small ghost" data-open="' + esc(h.code) + '">查看</button></td></tr>';
  }).join('') + '</table><div class="note">“预测概率”= T+1 开盘买入后 ' + (SC.last ? SC.last.hold : '') + ' 个交易日内、任一天收盘价较买入价涨幅 ≥ ' + pct(th, 0) + ' 的概率。高亮行为刚发现的。</div>';
  Array.prototype.forEach.call(el.querySelectorAll('[data-open]'), function (a) { a.onclick = function (e) { e.preventDefault(); openScanned(a.getAttribute('data-open')); }; });
}
function openScanned(code) {
  var have = state.symbols.filter(function (x) { return x.code === code; })[0];
  if (have) { selectSymbol(code, 'chart'); return; }
  toast('正在添加 ' + code + ' 的历史行情…');
  post('/api/feed/add?code=' + encodeURIComponent(code)).then(function (r) { state.pred = {}; return loadSymbols().then(function () { selectSymbol(r.code, 'chart'); }); }).catch(function (e) { toast('添加失败：' + e.message); });
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
    if (!f || !code) { $('impMsg').textContent = '请填写代码并选择文件'; return; }
    $('impMsg').textContent = '读取并导入中…';
    var fr = new FileReader();
    fr.onload = function () {
      var text; try { text = new TextDecoder($('impEnc').value).decode(fr.result); } catch (e) { $('impMsg').textContent = '解码失败：' + e.message; return; }
      api('/api/import?code=' + encodeURIComponent(code) + '&name=' + encodeURIComponent($('impName').value.trim()), { method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: text }).then(function (r) {
        $('modal').hidden = true; state.pred = {};
        return loadSymbols().then(function () { selectSymbol(r.code, 'chart'); });
      }).catch(function (e) { $('impMsg').textContent = '导入失败：' + e.message; });
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
  }).catch(function (e) { $('titleBar').innerHTML = '<span class="err">无法连接服务器：' + esc(e.message) + '</span>'; });
}
init();
})();
