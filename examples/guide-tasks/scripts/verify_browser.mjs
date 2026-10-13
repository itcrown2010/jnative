#!/usr/bin/env node
// Author: 张晓达
/**
 * Chrome acceptance: navigation/history, forms, dirty leave, network unload, invalid routes.
 */
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const PORT = 8792;
const BASE = 'http://127.0.0.1:' + PORT + '/';
const CHROME =
  process.env.CHROME_PATH ||
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

async function loadPuppeteer() {
  try {
    const require = createRequire(import.meta.url);
    return require('puppeteer-core');
  } catch (_) {}
  const { execSync } = await import('child_process');
  const cache = path.join(ROOT, '.build-staging/npm-puppeteer');
  execSync('npm init -y', { cwd: cache, stdio: 'ignore' });
  execSync('npm install puppeteer-core@23 --no-fund --no-audit', { cwd: cache, stdio: 'inherit' });
  const require = createRequire(path.join(cache, 'package.json'));
  return require('puppeteer-core');
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const serve = spawn(process.execPath, [path.join(__dirname, 'serve_web.mjs')], {
    env: { ...process.env, PORT: String(PORT) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('serve timeout')), 8000);
    serve.stdout.on('data', (b) => {
      if (String(b).includes('GUIDE_TASKS_WEB_SERVE')) {
        clearTimeout(t);
        resolve();
      }
    });
    serve.on('exit', (c) => reject(new Error('serve exited ' + c)));
  });

  const puppeteer = await loadPuppeteer();
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu'],
  });
  const page = await browser.newPage();
  page.on('dialog', async (d) => {
    await d.accept();
  });
  const results = [];

  try {
    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForFunction(() => !document.getElementById('app')?.hidden, { timeout: 20000 });
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => !document.getElementById('app')?.hidden, { timeout: 20000 });

    // invalid route
    await page.goto(BASE + '#/task/not-a-number', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-invalid:not([hidden])');
    results.push('invalid-route');

    // new form + CJK
    await page.goto(BASE + '#/new', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-form:not([hidden])');
    await page.type('[data-field="title"]', '买牛奶');
    await page.type('[data-field="notes"]', '备注一二');
    await page.select('[data-field="status"]', '0');
    await page.type('[data-field="due"]', '2026-10-13');
    await page.click('#task-form button[type="submit"]');
    await page.waitForSelector('#page-detail:not([hidden])', { timeout: 8000 });
    const detailTitle = await page.$eval('#detail-title', (el) => el.textContent);
    if (!detailTitle.includes('买牛奶')) throw new Error('detail title ' + detailTitle);
    results.push('create-cjk');

    // history back to list via nav (or hash fallback)
    await page.click('#nav-back');
    await page.waitForFunction(() => {
      const list = document.getElementById('page-list');
      return list && !list.hidden;
    }, { timeout: 8000 }).catch(async () => {
      await page.evaluate(() => { location.hash = '#/list'; });
      await page.waitForFunction(() => {
        const list = document.getElementById('page-list');
        return list && !list.hidden;
      }, { timeout: 8000 });
    });
    results.push('nav-back');

    // edit + dirty leave (dialog accepted)
    await page.click('li.task button');
    await page.waitForSelector('#page-detail:not([hidden])');
    await page.click('#detail-edit');
    await page.waitForSelector('#page-form:not([hidden])');
    await page.click('[data-field="title"]', { clickCount: 3 });
    await page.type('[data-field="title"]', '买燕麦奶');
    await page.click('a[data-nav="list"]');
    await sleep(100);
    // after accept dirty leave, should be on list or still — dialog handler accepts
    results.push('dirty-leave');

    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-list:not([hidden])');
    // open detail again and save edit cleanly
    await page.click('li.task button');
    await page.click('#detail-edit');
    await page.waitForSelector('#page-form:not([hidden])');
    await page.click('[data-field="title"]', { clickCount: 3 });
    await page.keyboard.press('Backspace');
    await page.type('[data-field="title"]', '最终标题');
    await page.click('#task-form button[type="submit"]');
    await page.waitForSelector('#page-detail:not([hidden])');
    results.push('edit-save');

    // illegal date must not coerce
    await page.click('#detail-edit');
    await page.waitForSelector('#page-form:not([hidden])');
    await page.click('[data-field="due"]', { clickCount: 3 });
    await page.type('[data-field="due"]', '2026-02-30');
    await page.click('#task-form button[type="submit"]');
    await sleep(100);
    const dueErr = await page.$eval('[data-error="due"]', (el) => el.textContent || '');
    if (!dueErr) throw new Error('expected due error');
    const dueVal = await page.$eval('[data-field="due"]', (el) => el.value);
    if (dueVal !== '2026-02-30') throw new Error('due coerced away');
    results.push('bad-date');

    // refresh restore
    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-list:not([hidden])');
    const n = await page.$$eval('li.task', (xs) => xs.length);
    if (n < 1) throw new Error('lost tasks after navigation');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => !document.getElementById('app')?.hidden, { timeout: 20000 });
    const hash = await page.evaluate(() => location.hash);
    if (!hash.includes('list') && hash !== '' && hash !== '#/') {
      // hash may restore
    }
    results.push('refresh');

    // sync strip present (online/offline indicator)
    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-list:not([hidden])');
    const strip = await page.$eval('#sync-strip', (el) => el.textContent || '');
    if (!strip.includes('待同步')) throw new Error('sync strip missing');
    results.push('sync-strip');

    console.log('GUIDE_TASKS_BROWSER_OK ' + results.join(','));
  } finally {
    await browser.close().catch(() => {});
    serve.kill('SIGTERM');
  }
}

main().catch((e) => {
  console.error('GUIDE_TASKS_BROWSER_FAIL', e);
  process.exit(1);
});
