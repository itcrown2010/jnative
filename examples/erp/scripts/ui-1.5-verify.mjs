// Author: 张晓达
/**
 * UI 1.5 — Commercial polish + Demo Center + visual regression (Chromium).
 */
import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';

const root = process.env.JN_ROOT || path.resolve(process.cwd());
const out = path.join(root, 'examples/erp/public/screenshots');
fs.mkdirSync(out, { recursive: true });
const base = process.env.JN_UI_URL || 'http://127.0.0.1:18700';

const results = {
  ok: [], fail: [], perf: {}, screenshots: [], a11y: [], demos: [],
  visual: {}, wasm: 'BLOCKED', stampHint: null
};

function ok(m) { results.ok.push(m); console.log('OK', m); }
function fail(m) { results.fail.push(m); console.error('FAIL', m); }

async function shot(page, name) {
  const p = path.join(out, name);
  await page.screenshot({ path: p, fullPage: false });
  results.screenshots.push(name);
  const st = fs.statSync(p);
  results.visual[name] = { bytes: st.size, ok: st.size > 8000 };
  if (st.size <= 8000) fail('screenshot too small: ' + name);
  console.log('shot', name, st.size);
}

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.on('pageerror', (e) => fail('pageerror: ' + e.message));
page.on('dialog', async (d) => { try { await d.accept(); } catch {} });

const t0 = Date.now();
await page.goto(base + '/', { waitUntil: 'networkidle' });
results.perf.firstLoadMs = Date.now() - t0;
await page.click('#btn-login');
await page.waitForSelector('#jn-workspace');
ok('login shell');

/* Design system / density */
try {
  const dens = await page.evaluate(() => {
    JnTheme.setDensity('compact');
    const c = getComputedStyle(document.documentElement).getPropertyValue('--jn-row-h').trim();
    JnTheme.setDensity('comfortable');
    const c2 = getComputedStyle(document.documentElement).getPropertyValue('--jn-row-h').trim();
    return { compact: c, comfortable: c2, theme: JnTheme.get().theme };
  });
  if (dens.compact === dens.comfortable) fail('density tokens unchanged');
  else ok('density compact=' + dens.compact + ' comfortable=' + dens.comfortable);
  if (dens.theme !== 'light') fail('default theme not light');
  else ok('light theme active');
} catch (e) {
  fail('theme: ' + e.message);
}

/* DEMO MODE */
try {
  const b = await page.locator('.jn-demo-banner').innerText();
  if (b.indexOf('DEMO MODE') < 0) fail('DEMO MODE missing');
  else ok('DEMO MODE banner');
} catch (e) { fail('banner: ' + e.message); }

/* Visual regression pages */
const pages = [
  { nav: 'dash.executive', name: 'ui15-executive-dashboard.png' },
  { nav: 'md.customers', name: 'ui15-customer-list.png' },
  { nav: 'md.customers', name: 'ui15-customer-detail.png', detail: true },
  { nav: 'sales.orders', name: 'ui15-sales-order-editor.png', editor: true },
  { nav: 'pur.orders', name: 'ui15-purchase-order-editor.png', editor: true },
  { nav: 'inv.balance', name: 'ui15-inventory-balance.png' },
  { nav: 'fin.journals', name: 'ui15-journal-entry.png' },
  { nav: 'mfg.bomEditor', name: 'ui15-bom-editor.png' },
  { nav: 'demo.center', name: 'ui15-demo-center.png' },
  { nav: 'sys.settings', name: 'ui15-settings.png' },
];

for (const s of pages) {
  try {
    const tNav = Date.now();
    await page.evaluate(() => document.querySelectorAll('.jn-modal-backdrop,.jn-tour-card,.jn-tour-spotlight').forEach((e) => e.remove()));
    await page.click(`[data-nav="${s.nav}"]`);
    await page.waitForTimeout(280);
    results.perf['nav_' + s.nav] = Date.now() - tNav;
    if (s.detail) {
      await page.locator('[data-open]').first().click();
      await page.waitForSelector('.jn-master-detail', { timeout: 4000 });
    }
    if (s.editor) {
      const btn = page.locator('[data-g="new"], [data-open], [data-action="new"]').first();
      await btn.click({ timeout: 8000 });
      await page.waitForSelector('.jn-doc-editor, .jn-modal', { timeout: 5000 });
    }
    await shot(page, s.name);
    if (s.editor) await page.evaluate(() => document.querySelectorAll('.jn-modal-backdrop').forEach((e) => e.remove()));
    ok('visual ' + s.name);
  } catch (e) {
    fail('visual ' + s.name + ': ' + e.message);
  }
}

/* Demo scenarios (tour start on first step) */
for (const id of ['executive', 'sales', 'purchasing', 'inventory', 'finance', 'manufacturing']) {
  try {
    await page.click('[data-nav="demo.center"]');
    await page.waitForSelector('.jn-demo-center');
    await page.click(`[data-start="${id}"]`);
    await page.waitForSelector('.jn-tour-card', { timeout: 4000 });
    const title = await page.locator('.jn-tour-card h3').innerText();
    await page.click('[data-tour="skip"]');
    await page.waitForTimeout(100);
    results.demos.push({ id, title });
    ok('demo scenario ' + id + ': ' + title);
  } catch (e) {
    fail('demo ' + id + ': ' + e.message);
  }
}

/* Keyboard Ctrl+S smoke on new SO */
try {
  await page.click('[data-nav="sales.orders"]');
  await page.waitForTimeout(200);
  await page.click('[data-g="new"]');
  await page.waitForSelector('.jn-doc-editor');
  await page.keyboard.press('Control+s');
  await page.waitForTimeout(300);
  ok('Ctrl+S invoked on document editor');
  await page.evaluate(() => document.querySelectorAll('.jn-modal-backdrop').forEach((e) => e.remove()));
} catch (e) {
  fail('keyboard save: ' + e.message);
}

/* Perf probes */
try {
  const perf = await page.evaluate(async () => {
    const out = {};
    const t0 = performance.now();
    JnApp.navigate('md.customers');
    await new Promise((r) => setTimeout(r, 50));
    out.switchMs = Math.round(performance.now() - t0);
    const scroll = document.querySelector('[data-grid-scroll]');
    if (scroll) {
      const t1 = performance.now();
      scroll.scrollTop = 200; scroll.scrollTop = 0;
      out.gridScrollMs = Math.round(performance.now() - t1);
    }
    const t2 = performance.now();
    JnFilterBuilder.open({
      columns: JnGridModel.fromPageColumns([{ key: 'name', title: 'Name' }]),
      filters: { logic: 'AND', conditions: [], groups: [] },
      onApply: function () {}
    });
    out.filterOpenMs = Math.round(performance.now() - t2);
    document.querySelector('[data-filter-builder]')?.remove();
    const t3 = performance.now();
    JnDocumentEditor.open({
      def: JnDocumentDefs.get('SalesOrder'),
      mode: 'CREATE',
      record: { status: 'DRAFT', lines: [], currency: 'CNY', date: '2026-10-08' }
    });
    out.docOpenMs = Math.round(performance.now() - t3);
    document.querySelector('.jn-doc-editor-backdrop')?.remove();
    return out;
  });
  results.perf = Object.assign(results.perf, perf);
  ok('perf measured: ' + JSON.stringify(perf));
} catch (e) {
  fail('perf: ' + e.message);
}

/* A11y spot checks */
try {
  const a11y = await page.evaluate(() => {
    const issues = [];
    if (!document.querySelector('.jn-demo-banner[role="status"]')) issues.push('banner role');
    const unlabeled = [...document.querySelectorAll('input.jn-input')].filter((i) => {
      const id = i.id;
      return id && !document.querySelector('label[for="' + id + '"]') && !i.getAttribute('aria-label');
    }).length;
    /* settings page has labels; global search may not — record only */
    const focusable = document.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])').length;
    const contrastSample = getComputedStyle(document.body).color;
    return { issues, unlabeledSample: unlabeled, focusable, contrastSample, density: document.documentElement.getAttribute('data-density') };
  });
  results.a11y = a11y;
  if (a11y.issues.length) fail('a11y: ' + a11y.issues.join(','));
  else ok('a11y banner role + focusable=' + a11y.focusable);
} catch (e) {
  fail('a11y: ' + e.message);
}

/* Viewports */
for (const vp of [
  { w: 1366, h: 768, name: 'ui15-viewport-1366.png' },
  { w: 1440, h: 900, name: 'ui15-viewport-1440.png' },
  { w: 1920, h: 1080, name: 'ui15-viewport-1920.png' }
]) {
  try {
    await page.setViewportSize({ width: vp.w, height: vp.h });
    await page.click('[data-nav="dash.executive"]');
    await page.waitForTimeout(200);
    await shot(page, vp.name);
    ok('viewport ' + vp.w);
  } catch (e) {
    fail('viewport ' + vp.w + ': ' + e.message);
  }
}

results.wasm = 'BLOCKED (public SPA acceptance; wasm32-browser not full ERP UI)';
await browser.close();

const visualOk = Object.values(results.visual).every((v) => v.ok);
const demosOk = results.demos.length >= 6;
results.stampHint = (results.fail.length === 0 && visualOk && demosOk)
  ? 'JNATIVE_ONE_UI_1_5_PASS'
  : 'JNATIVE_ONE_UI_1_5_PARTIAL';

fs.writeFileSync(path.join(out, 'ui15-verify.json'), JSON.stringify(results, null, 2));
console.log('STAMP_HINT', results.stampHint);
console.log('ok', results.ok.length, 'fail', results.fail.length);
process.exit(results.fail.length ? 1 : 0);
