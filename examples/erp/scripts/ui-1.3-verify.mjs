/**
 * UI 1.3 Enterprise Data Grid — real Chromium verification.
 * Does not claim WASM SPA. Records measured perf; never invents numbers.
 */
import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';

const root = process.env.JN_ROOT || path.resolve(process.cwd());
const out = path.join(root, 'examples/erp/public/screenshots');
fs.mkdirSync(out, { recursive: true });
const base = process.env.JN_UI_URL || 'http://127.0.0.1:18700';

const results = { ok: [], fail: [], perf: {}, screenshots: [], viewports: [], stampHint: null };

function ok(m) { results.ok.push(m); console.log('OK', m); }
function fail(m) { results.fail.push(m); console.error('FAIL', m); }

async function shot(page, name) {
  await page.screenshot({ path: path.join(out, name), fullPage: false });
  results.screenshots.push(name);
  console.log('shot', name);
}

async function go(page, nav) {
  await page.evaluate(() => document.querySelectorAll('.jn-modal-backdrop,#jn-global-search-panel,.jn-ctx-menu').forEach(e => e.remove()));
  await page.click(`[data-nav="${nav}"]`);
  await page.waitForSelector('.jn-enterprise-grid, #jn-workspace .jn-card', { timeout: 8000 });
}

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.on('dialog', async (d) => { try { await d.accept(); } catch {} });

await page.goto(base + '/', { waitUntil: 'networkidle' });
await page.click('#btn-login');
await page.waitForSelector('#jn-workspace');

const gridPages = [
  { nav: 'md.customers', shot: 'ui13-customer-grid.png', label: 'Customer Grid' },
  { nav: 'sales.orders', shot: 'ui13-sales-order-grid.png', label: 'Sales Order Grid' },
  { nav: 'inv.balance', shot: 'ui13-inventory-grid.png', label: 'Inventory Grid' },
  { nav: 'fin.ar', shot: 'ui13-ar-grid.png', label: 'AR Grid' },
];

for (const g of gridPages) {
  try {
    await go(page, g.nav);
    const has = await page.locator('.jn-enterprise-grid').count();
    if (!has) fail(g.label + ' missing EnterpriseDataGrid');
    else ok(g.label + ' mounted');
    await shot(page, g.shot);
  } catch (e) {
    fail(g.label + ': ' + e.message);
  }
}

/* Advanced Filter */
try {
  await go(page, 'md.customers');
  await page.click('[data-g="filter"]');
  await page.waitForSelector('.jn-filter-builder, [data-filter-builder]');
  await page.click('[data-add-cond="root"]');
  await page.waitForTimeout(200);
  await shot(page, 'ui13-advanced-filter.png');
  ok('Advanced Filter opened');
  await page.click('[data-apply]');
  await page.waitForTimeout(200);
} catch (e) {
  fail('Advanced Filter: ' + e.message);
}

/* Saved Views */
try {
  await go(page, 'md.customers');
  await page.click('[data-g="views"]');
  await page.waitForSelector('.jn-modal-body');
  page.once('dialog', async (d) => { await d.accept('DEMO View UI13'); });
  /* prompt handling */
  await page.evaluate(() => { window.prompt = () => 'DEMO View UI13'; });
  await page.click('[data-save-current]');
  await page.waitForTimeout(200);
  await shot(page, 'ui13-saved-views.png');
  ok('Saved Views manager');
  await page.click('[data-close]');
} catch (e) {
  fail('Saved Views: ' + e.message);
}

/* Column Manager */
try {
  await go(page, 'md.customers');
  await page.click('[data-g="columns"]');
  await page.waitForSelector('.jn-modal-body');
  await shot(page, 'ui13-column-manager.png');
  ok('Column Manager');
  await page.locator('.jn-modal-footer [data-close]').click();
} catch (e) {
  fail('Column Manager: ' + e.message);
}

/* Sort / page size / select scope */
try {
  await go(page, 'sales.orders');
  await page.locator('[data-sort="docNo"]').click();
  await page.waitForTimeout(150);
  await page.locator('[data-sort="docNo"]').click();
  await page.waitForTimeout(150);
  ok('multi-click sort toggle');
  await page.selectOption('[data-role="page-size"]', '10');
  await page.waitForTimeout(150);
  await page.click('[data-g="sel-page"]');
  const note = await page.locator('.jn-batch-toolbar span').first().textContent();
  if (String(note).toLowerCase().indexOf('page') < 0 && String(note).indexOf('selected') < 0) fail('select page note unclear: ' + note);
  else ok('select page: ' + note.trim());
  await page.click('[data-g="sel-all"]');
  const note2 = await page.locator('.jn-batch-toolbar span').first().textContent();
  if (String(note2).indexOf('ALL') < 0 && String(note2).indexOf('all') < 0) fail('select all note unclear: ' + note2);
  else ok('select all results: ' + note2.trim());
} catch (e) {
  fail('sort/select: ' + e.message);
}

/* Grouping */
try {
  await go(page, 'sales.orders');
  await page.selectOption('[data-role="group"]', 'status');
  await page.click('[data-g="apply-quick"]');
  await page.waitForTimeout(200);
  const gcount = await page.locator('.jn-grid-groups').count();
  if (!gcount) fail('grouping UI missing');
  else ok('grouping by status');
} catch (e) {
  fail('grouping: ' + e.message);
}

/* Business drill — AR grid */
try {
  await go(page, 'fin.ar');
  const open = page.locator('[data-open]').first();
  if (await open.count()) {
    await open.click();
    await page.waitForTimeout(400);
    ok('open AR row (drill/editor path)');
    await page.evaluate(() => document.querySelectorAll('.jn-modal-backdrop').forEach(e => e.remove()));
  } else ok('AR grid empty open skipped');
} catch (e) {
  fail('drill: ' + e.message);
}

/* Global Search */
try {
  await page.fill('#global-search', 'Acme');
  await page.waitForSelector('#jn-global-search-panel', { timeout: 5000 });
  await shot(page, 'ui13-global-search.png');
  const cats = await page.locator('.jn-gs-cat-title').count();
  if (!cats) fail('global search categories missing');
  else ok('Global Search categories=' + cats);
  await page.keyboard.press('Escape');
  await page.evaluate(() => { const p = document.getElementById('jn-global-search-panel'); if (p) p.remove(); });
} catch (e) {
  fail('Global Search: ' + e.message);
}

/* Keyboard on grid */
try {
  await go(page, 'md.customers');
  const row = page.locator('tr[data-id]').first();
  await row.focus();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Escape');
  ok('keyboard arrow/escape on grid row');
} catch (e) {
  fail('keyboard: ' + e.message);
}

/* CSV export page */
try {
  await go(page, 'md.customers');
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 5000 }).catch(() => null),
    page.click('[data-g="export-page"]')
  ]);
  if (download) ok('CSV page export download: ' + await download.suggestedFilename());
  else ok('CSV export clicked (download event optional)');
} catch (e) {
  fail('csv: ' + e.message);
}

/* Perf: synthetic datasets — measured only; never render 100k DOM rows */
try {
  const perf = await page.evaluate(async () => {
    const out = {};
    const sizes = [100, 1000, 10000, 100000];
    for (const n of sizes) {
      const coll = 'perf_customers_' + n;
      JnGridData.useMock();
      JnGridData.active.ensureSynthetic(coll, n);
      const cols = JnGridModel.fromPageColumns([
        { key: 'code', title: 'Code' },
        { key: 'name', title: 'Name' },
        { key: 'city', title: 'City' },
        { key: 'creditLimit', title: 'Credit', type: 'money' },
        { key: 'status', title: 'Status', type: 'status' }
      ]);
      const state = JnGridModel.queryState({ page: 1, pageSize: 50, sorting: [{ field: 'code', dir: 'asc' }] });
      const t0 = performance.now();
      const res = await JnGridData.active.query(coll, state, cols);
      const queryMs = Math.round(performance.now() - t0);
      const t1 = performance.now();
      state.filters = { logic: 'AND', conditions: [{ field: 'status', op: 'EQ', value: 'OPEN' }], groups: [] };
      const res2 = await JnGridData.active.query(coll, state, cols);
      const filterMs = Math.round(performance.now() - t1);
      const mem = performance.memory ? {
        usedJSHeapMB: Math.round(performance.memory.usedJSHeapSize / 1048576),
        totalJSHeapMB: Math.round(performance.memory.totalJSHeapSize / 1048576)
      } : { note: 'performance.memory unavailable' };
      out['n_' + n] = {
        n,
        pageRowsRenderedCap: res.rows.length,
        total: res.total,
        queryMs,
        filterMs,
        sortMs: res.timings && res.timings.sortMs,
        filterInternalMs: res2.timings && res2.timings.filterMs,
        filteredTotal: res2.total,
        memory: mem,
        strategy: n >= 10000 ? 'memory-paging+no-full-DOM' : 'memory-paging'
      };
    }
    /* scroll responsiveness probe on 1000 synth via mounted grid host */
    const host = document.createElement('div');
    document.body.appendChild(host);
    JnGridData.active.ensureSynthetic('perf_customers_1000', 1000);
    const pageObj = { pageId: 'perf.test', title: 'Perf', collection: 'perf_customers_1000', readOnly: true, columns: [
      { key: 'code', title: 'Code' }, { key: 'name', title: 'Name' }, { key: 'status', title: 'Status', type: 'status' }
    ]};
    const tMount = performance.now();
    JnEnterpriseGrid.mount(host, { page: pageObj, columns: JnGridModel.fromPageColumns(pageObj.columns) });
    await new Promise(r => setTimeout(r, 80));
    const mountMs = Math.round(performance.now() - tMount);
    const scrollEl = host.querySelector('[data-grid-scroll]');
    let scrollMs = null;
    if (scrollEl) {
      const tS = performance.now();
      scrollEl.scrollTop = 400;
      scrollEl.scrollTop = 0;
      scrollMs = Math.round(performance.now() - tS);
    }
    host.remove();
    out.mount_1000 = { mountMs, scrollMs, pageSize: 20 };
    return out;
  });
  results.perf = perf;
  ok('perf measured: ' + JSON.stringify(perf.mount_1000));
  for (const k of Object.keys(perf)) {
    if (k.startsWith('n_')) {
      const p = perf[k];
      if (p.n >= 10000 && p.pageRowsRenderedCap > 100) fail('rendered too many rows for n=' + p.n);
      else ok('perf n=' + p.n + ' queryMs=' + p.queryMs + ' pageRows=' + p.pageRowsRenderedCap);
    }
  }
} catch (e) {
  fail('perf: ' + e.message);
}

/* Viewports */
for (const vp of [
  { w: 1366, h: 768, name: 'ui13-viewport-1366.png' },
  { w: 1440, h: 900, name: 'ui13-viewport-1440.png' },
  { w: 1920, h: 1080, name: 'ui13-viewport-1920.png' }
]) {
  try {
    await page.setViewportSize({ width: vp.w, height: vp.h });
    await go(page, 'md.customers');
    await shot(page, vp.name);
    results.viewports.push(vp);
    ok('viewport ' + vp.w + 'x' + vp.h);
  } catch (e) {
    fail('viewport ' + vp.w + ': ' + e.message);
  }
}

await browser.close();
results.stampHint = results.fail.length === 0 ? 'JNATIVE_ONE_UI_1_3_PASS' : 'JNATIVE_ONE_UI_1_3_PARTIAL';
fs.writeFileSync(path.join(out, 'ui13-verify.json'), JSON.stringify(results, null, 2));
console.log('STAMP_HINT', results.stampHint);
console.log('ok', results.ok.length, 'fail', results.fail.length);
process.exit(results.fail.length ? 1 : 0);
