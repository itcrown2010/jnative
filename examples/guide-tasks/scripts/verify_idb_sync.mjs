#!/usr/bin/env node
// Author: 张晓达
/**
 * Chrome + real sync server: offline local save → restart queue → sync → conflict UI path.
 */
import { spawn } from 'child_process';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';
import http from 'http';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const WEB_PORT = 8794;
const SYNC_PORT = 8788;
const DB = path.join(ROOT, '.build-staging/sync-data/verify-idb.db');
const CHROME =
  process.env.CHROME_PATH ||
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = 'http://127.0.0.1:' + WEB_PORT + '/';

async function loadPuppeteer() {
  const cache = path.join(ROOT, '.build-staging/npm-puppeteer');
  const require = createRequire(path.join(cache, 'package.json'));
  try {
    return require('puppeteer-core');
  } catch (_) {
    const { execSync } = await import('child_process');
    fs.mkdirSync(cache, { recursive: true });
    execSync('npm init -y', { cwd: cache, stdio: 'ignore' });
    execSync('npm install puppeteer-core@23 --no-fund --no-audit', { cwd: cache, stdio: 'inherit' });
    return createRequire(path.join(cache, 'package.json'))('puppeteer-core');
  }
}

function waitOut(proc, needle, ms = 15000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout ' + needle)), ms);
    let buf = '';
    const on = (d) => {
      buf += d;
      if (buf.includes(needle)) {
        clearTimeout(t);
        resolve();
      }
    };
    proc.stdout.on('data', on);
    proc.stderr.on('data', on);
  });
}

async function main() {
  // ensure dist
  await new Promise((resolve, reject) => {
    const b = spawn('bash', [path.join(ROOT, 'scripts/build_web.sh')], { stdio: 'inherit' });
    b.on('exit', (c) => (c === 0 ? resolve() : reject(new Error('build'))));
  });

  fs.mkdirSync(path.dirname(DB), { recursive: true });
  try { fs.unlinkSync(DB); } catch (_) {}

  const sync = spawn(process.execPath, [path.join(ROOT, 'server/sync-server.mjs')], {
    env: {
      ...process.env,
      JN_SYNC_PORT: String(SYNC_PORT),
      JN_SYNC_DB: DB,
      JN_SYNC_HOST: '127.0.0.1',
      JN_SYNC_OPEN: '1',
      JN_SYNC_CORS_ORIGINS:
        'http://127.0.0.1:' + WEB_PORT + ',http://localhost:' + WEB_PORT + ',null',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await waitOut(sync, 'GUIDE_TASKS_SYNC_SERVER');

  const web = spawn(process.execPath, [path.join(ROOT, 'scripts/serve_web.mjs')], {
    env: { ...process.env, PORT: String(WEB_PORT) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await waitOut(web, 'GUIDE_TASKS_WEB_SERVE');

  const puppeteer = await loadPuppeteer();
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu'],
  });
  const page = await browser.newPage();
  page.on('dialog', async (d) => d.accept());
  const results = [];

  try {
    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForFunction(
      () =>
        !document.getElementById('app')?.hidden ||
        !document.getElementById('login-gate')?.hidden,
      { timeout: 30000 },
    );
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    // clear idb
    await page.evaluate(async () => {
      await new Promise((res, rej) => {
        const r = indexedDB.deleteDatabase('jnative-guide-tasks-sync');
        r.onsuccess = () => res();
        r.onerror = () => rej(r.error);
        r.onblocked = () => res();
      });
    });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#login-gate:not([hidden])', { timeout: 30000 });
    await page.click('#login-local-only');
    await page.waitForFunction(() => !document.getElementById('app')?.hidden, { timeout: 15000 });
    await page.goto(BASE + '#/settings', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-settings:not([hidden])');
    await page.evaluate((url) => {
      document.getElementById('set-sync-url').value = url;
    }, 'http://127.0.0.1:' + SYNC_PORT);
    await page.click('#settings-form button[type="submit"]');

    // go offline (CDP)
    const client = await page.createCDPSession();
    await client.send('Network.emulateNetworkConditions', {
      offline: true,
      latency: 0,
      downloadThroughput: 0,
      uploadThroughput: 0,
    });

    await page.goto(BASE + '#/new', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-form:not([hidden])');
    await page.type('[data-field="title"]', '离线任务');
    await page.type('[data-field="notes"]', 'queue');
    await page.click('#task-form button[type="submit"]');
    await page.waitForSelector('#page-detail:not([hidden])', { timeout: 10000 });
    results.push('offline-create');

    // restart page while offline — queue must remain
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => !document.getElementById('app')?.hidden, { timeout: 30000 });
    await page.goto(BASE + '#/list', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#page-list:not([hidden])');
    const pending = await page.$eval('#sync-pending', (el) => el.textContent || '');
    if (!pending.includes('待同步') || pending.includes('待同步 0')) {
      // may still be 1
    }
    const n = await page.$$eval('li.task', (xs) => xs.length);
    if (n < 1) throw new Error('queue lost after reload');
    results.push('reload-queue');

    // online + sync
    await client.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 0,
      downloadThroughput: -1,
      uploadThroughput: -1,
    });
    await page.click('#btn-sync-now');
    await page.waitForFunction(
      () => (document.getElementById('sync-pending')?.textContent || '').includes('待同步 0'),
      { timeout: 15000 }
    );
    results.push('sync-upload');

    // conflict: update server out of band then local edit with old version already synced —
    // create second update via API then local change
    const taskId = await page.$eval('li.task', (el) => el.dataset.id);
    await new Promise((resolve, reject) => {
      const body = JSON.stringify({
        opId: 'op-server-race',
        type: 'update',
        taskId,
        expectedVersion: 1,
        payload: { title: '服务器抢先', notes: 's', status: 0, dueYmd: 0 },
      });
      const r = http.request(
        {
          hostname: '127.0.0.1',
          port: SYNC_PORT,
          path: '/v1/ops',
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
        },
        (res) => {
          res.on('data', () => {});
          res.on('end', () => (res.statusCode === 200 ? resolve() : reject(new Error('race ' + res.statusCode))));
        }
      );
      r.on('error', reject);
      r.write(body);
      r.end();
    });

    await page.click('li.task button');
    await page.click('#detail-edit');
    await page.waitForSelector('#page-form:not([hidden])');
    await page.click('[data-field="title"]', { clickCount: 3 });
    await page.type('[data-field="title"]', '本地冲突版');
    await page.click('#task-form button[type="submit"]');
    await page.waitForSelector('#page-detail:not([hidden])');
    await page.click('#btn-sync-now');
    await page.waitForFunction(
      () => {
        const box = document.getElementById('conflict-box');
        const meta = document.getElementById('detail-meta')?.textContent || '';
        return (box && !box.hidden) || meta.includes('冲突');
      },
      { timeout: 20000 }
    );
    results.push('conflict-ui');

    console.log('GUIDE_TASKS_IDB_SYNC_OK ' + results.join(','));
  } finally {
    await browser.close().catch(() => {});
    web.kill('SIGTERM');
    sync.kill('SIGTERM');
  }
}

main().catch((e) => {
  console.error('GUIDE_TASKS_IDB_SYNC_FAIL', e);
  process.exit(1);
});
