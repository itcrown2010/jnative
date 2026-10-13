#!/usr/bin/env node
// Author: 张晓达
/** Smoke: dev server builds, exposes status, shuts down and releases port. */
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import http from 'http';
import net from 'net';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 8793;

function get(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let b = '';
      res.on('data', (d) => (b += d));
      res.on('end', () => resolve({ status: res.statusCode, body: b }));
    }).on('error', reject);
  });
}

function portFree(port) {
  return new Promise((resolve) => {
    const s = net.createServer();
    s.once('error', () => resolve(false));
    s.once('listening', () => s.close(() => resolve(true)));
    s.listen(port, '127.0.0.1');
  });
}

async function main() {
  const child = spawn(process.execPath, [path.join(__dirname, 'dev_web.mjs')], {
    env: { ...process.env, PORT: String(PORT) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let boot = '';
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('dev boot timeout')), 60000);
    child.stdout.on('data', (d) => {
      boot += d;
      if (boot.includes('GUIDE_TASKS_DEV')) {
        clearTimeout(t);
        resolve();
      }
    });
    child.stderr.on('data', (d) => { boot += d; });
    child.on('exit', (c) => reject(new Error('dev exited early ' + c)));
  });

  let status = null;
  for (let i = 0; i < 60; i++) {
    try {
      const r = await get('http://127.0.0.1:' + PORT + '/__build_status');
      status = JSON.parse(r.body);
      if (status.status === 'ok' || status.status === 'failed') break;
    } catch (_) {}
    await new Promise((r) => setTimeout(r, 500));
  }
  if (!status || status.status !== 'ok') {
    child.kill('SIGTERM');
    throw new Error('expected build ok, got ' + JSON.stringify(status));
  }
  const idx = await get('http://127.0.0.1:' + PORT + '/');
  if (idx.status !== 200 || !idx.body.includes('任务')) throw new Error('index missing');

  child.kill('SIGTERM');
  await new Promise((r) => setTimeout(r, 800));
  const free = await portFree(PORT);
  if (!free) throw new Error('port not released');
  console.log('GUIDE_TASKS_DEV_OK');
}

main().catch((e) => {
  console.error('GUIDE_TASKS_DEV_FAIL', e);
  process.exit(1);
});
