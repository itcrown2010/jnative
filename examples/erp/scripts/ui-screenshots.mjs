import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';

const root = process.env.JN_ROOT || path.resolve(process.cwd());
const out = path.join(root, 'examples/erp/public/screenshots');
fs.mkdirSync(out, { recursive: true });
const base = process.env.JN_UI_URL || 'http://127.0.0.1:18700';

const shots = [
  { name: '01-dashboard.png', after: async () => {} },
  { name: '02-customer-list.png', after: async (page) => { await page.click('[data-nav="md.customers"]'); await page.waitForTimeout(400); } },
  { name: '03-customer-editor.png', after: async (page) => {
      await page.click('[data-nav="md.customers"]');
      await page.waitForTimeout(300);
      await page.click('[data-action="new"]');
      await page.waitForTimeout(300);
    }},
  { name: '04-sales-order-list.png', after: async (page) => {
      const close = page.locator('.jn-modal-backdrop [data-close]').first();
      if (await close.count()) { try { await close.click({ timeout: 500 }); } catch {} }
      await page.click('[data-nav="sales.orders"]'); await page.waitForTimeout(400);
    }},
  { name: '05-sales-order-editor.png', after: async (page) => {
      await page.click('[data-nav="sales.orders"]');
      await page.waitForTimeout(300);
      await page.locator('[data-action="view"]').first().click();
      await page.waitForTimeout(400);
    }},
  { name: '06-purchase-order-editor.png', after: async (page) => {
      const close = page.locator('.jn-modal-backdrop [data-close]').first();
      if (await close.count()) { try { await close.click({ timeout: 500 }); } catch {} }
      await page.click('[data-nav="pur.orders"]');
      await page.waitForTimeout(300);
      await page.locator('[data-action="view"]').first().click();
      await page.waitForTimeout(400);
    }},
  { name: '07-inventory-balance.png', after: async (page) => {
      const close = page.locator('.jn-modal-backdrop [data-close]').first();
      if (await close.count()) { try { await close.click({ timeout: 500 }); } catch {} }
      await page.click('[data-nav="inv.balance"]'); await page.waitForTimeout(400);
    }},
  { name: '08-journal-entry.png', after: async (page) => { await page.click('[data-nav="fin.journals"]'); await page.waitForTimeout(400); } },
  { name: '09-bom-editor.png', after: async (page) => { await page.click('[data-nav="mfg.bomEditor"]'); await page.waitForTimeout(400); } },
  { name: '10-mrp-workbench.png', after: async (page) => { await page.click('[data-nav="mfg.mrp"]'); await page.waitForTimeout(400); } },
  { name: '11-system-settings.png', after: async (page) => { await page.click('[data-nav="sys.settings"]'); await page.waitForTimeout(400); } },
];

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
await page.goto(base + '/', { waitUntil: 'networkidle' });
await page.click('#btn-login');
await page.waitForSelector('#jn-workspace');
await page.waitForTimeout(500);

for (const s of shots) {
  await s.after(page);
  await page.screenshot({ path: path.join(out, s.name), fullPage: false });
  console.log('shot', s.name);
}

await page.click('[data-nav="dash.executive"]');
await page.setViewportSize({ width: 1920, height: 1080 });
await page.waitForTimeout(200);
await page.screenshot({ path: path.join(out, '12-dashboard-1920.png') });
await page.setViewportSize({ width: 1366, height: 768 });
await page.waitForTimeout(200);
await page.screenshot({ path: path.join(out, '13-dashboard-1366.png') });
console.log('viewports ok');

const navs = await page.$$eval('[data-nav]', els => els.map(e => e.getAttribute('data-nav')));
let ok = 0, fail = [];
for (const id of navs) {
  try {
    const close = page.locator('.jn-modal-backdrop [data-close]').first();
    if (await close.count()) { try { await close.click({ timeout: 300 }); } catch {} }
    await page.click(`[data-nav="${id}"]`, { timeout: 2000 });
    await page.waitForSelector('#jn-workspace .jn-card, #jn-workspace .jn-kpi, #jn-workspace .jn-empty, #jn-workspace .jn-loading', { timeout: 3000 });
    const err = await page.$('.jn-error');
    if (err) fail.push(id + ':error');
    else ok++;
  } catch (e) {
    fail.push(id + ':' + e.message.split('\n')[0]);
  }
}
console.log('menu_ok', ok, 'menu_fail', fail.length);
if (fail.length) console.log(fail.slice(0, 30));
fs.writeFileSync(path.join(out, 'menu-smoke.json'), JSON.stringify({ ok, fail, total: navs.length }, null, 2));

await browser.close();
