#!/usr/bin/env node
// Author: 张晓达
/**
 * Dev preview: watch sources → rebuild → atomic dist replace → reload signal.
 * Failed builds keep last good dist and expose /__build_status as failed
 * (clients should not treat failed builds as success). This is reload, not HMR.
 */
import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const REPO = path.resolve(ROOT, '../..');
const DIST = path.join(ROOT, 'web/dist');
const STAGE = path.join(ROOT, '.build-staging/dev-dist');
const PORT = Number(process.env.PORT || 8767);
const DEBOUNCE_MS = Number(process.env.DEV_DEBOUNCE_MS || 400);

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.wasm': 'application/wasm',
  '.json': 'application/json; charset=utf-8',
};

let buildState = {
  status: 'idle',
  ok: false,
  message: 'not built yet',
  at: null,
  generation: 0,
};
let building = false;
let pending = false;
let timer = null;
const clients = new Set();
const watchers = [];

function setState(partial) {
  buildState = { ...buildState, ...partial, at: new Date().toISOString() };
  broadcast({ type: 'build', ...buildState });
}

function broadcast(msg) {
  const data = `data: ${JSON.stringify(msg)}\n\n`;
  for (const res of clients) {
    try { res.write(data); } catch (_) {}
  }
}

function runBuild() {
  if (building) {
    pending = true;
    return;
  }
  building = true;
  pending = false;
  setState({ status: 'building', message: 'compiling…' });
  const script = path.join(ROOT, 'scripts/build_web.sh');
  const child = spawn('bash', [script], {
    cwd: ROOT,
    env: { ...process.env, JNATIVE_JAR: process.env.JNATIVE_JAR || path.join(REPO, 'target/jnative.jar') },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  child.stdout.on('data', (b) => { out += b; process.stdout.write(b); });
  child.stderr.on('data', (b) => { out += b; process.stderr.write(b); });
  child.on('close', (code) => {
    building = false;
    if (code === 0 && fs.existsSync(path.join(DIST, 'app.wasm'))) {
      // Atomic stamp: write success marker only after dist is ready
      const stamp = path.join(DIST, '.build-ok');
      const gen = buildState.generation + 1;
      fs.writeFileSync(stamp, String(gen), 'utf8');
      injectReloadClient();
      setState({ status: 'ok', ok: true, message: 'build ok', generation: gen });
      broadcast({ type: 'reload', generation: gen });
    } else {
      setState({
        status: 'failed',
        ok: false,
        message: summarizeFail(out) || ('build exit ' + code),
      });
      // Do not rewrite dist; leave last good artifacts + failed status for UI
    }
    if (pending) scheduleBuild('coalesced');
  });
}

function summarizeFail(out) {
  const lines = String(out).split(/\r?\n/).filter(Boolean);
  const err = lines.filter((l) => /error|FAIL|Exception|missing/i.test(l)).slice(-6);
  return err.join(' | ').slice(0, 500);
}

function injectReloadClient() {
  const index = path.join(DIST, 'index.html');
  if (!fs.existsSync(index)) return;
  let html = fs.readFileSync(index, 'utf8');
  if (html.includes('__jn_dev_reload')) return;
  const snip = `<script id="__jn_dev_reload">
(() => {
  const es = new EventSource('/__events');
  let last = null;
  es.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data);
      if (m.type === 'build' && m.status === 'failed') {
        const bar = document.getElementById('__jn_build_bar') || document.createElement('div');
        bar.id = '__jn_build_bar';
        bar.style.cssText = 'position:fixed;left:0;right:0;bottom:0;background:#a12622;color:#fff;padding:8px 12px;z-index:99999;font:14px sans-serif';
        bar.textContent = '构建失败（仍显示上一成功产物）: ' + (m.message || '');
        document.body.appendChild(bar);
      }
      if (m.type === 'reload') {
        const bar = document.getElementById('__jn_build_bar');
        if (bar) bar.remove();
        if (last != null && m.generation !== last) location.reload();
        last = m.generation;
      }
      if (m.type === 'build' && m.status === 'ok') last = m.generation;
    } catch (_) {}
  };
})();
</script>`;
  html = html.replace('</body>', snip + '\n</body>');
  fs.writeFileSync(index, html);
}

function scheduleBuild(reason) {
  console.log('[dev] schedule build:', reason);
  clearTimeout(timer);
  timer = setTimeout(runBuild, DEBOUNCE_MS);
}

function watchPath(p) {
  if (!fs.existsSync(p)) return;
  const w = fs.watch(p, { recursive: true }, (_e, file) => {
    if (!file) return;
    if (/\.(java|jn|js|css|html|json)$/.test(file) && !file.includes('node_modules') && !file.includes('dist/')) {
      scheduleBuild(file);
    }
  });
  watchers.push(w);
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url || '/', 'http://127.0.0.1');
  if (u.pathname === '/__events') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    res.write('data: ' + JSON.stringify({ type: 'build', ...buildState }) + '\n\n');
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }
  if (u.pathname === '/__build_status') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(buildState));
    return;
  }
  if (u.pathname === '/api/hint.json' && u.searchParams.get('fail') === '1') {
    res.writeHead(503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: false, message: 'simulated failure' }));
    return;
  }
  let rel = decodeURIComponent(u.pathname);
  if (rel === '/') rel = '/index.html';
  const file = path.normalize(path.join(DIST, rel));
  if (!file.startsWith(DIST) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404);
    res.end('not found');
    return;
  }
  // Refuse to claim success if never built ok and requesting wasm
  if (rel.endsWith('.wasm') && !buildState.ok && !fs.existsSync(path.join(DIST, '.build-ok'))) {
    res.writeHead(503, { 'Content-Type': 'text/plain' });
    res.end('build not ready');
    return;
  }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

function shutdown() {
  console.log('[dev] shutting down');
  for (const w of watchers) {
    try { w.close(); } catch (_) {}
  }
  for (const res of clients) {
    try { res.end(); } catch (_) {}
  }
  clients.clear();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1500).unref();
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

watchPath(path.join(ROOT, 'core'));
watchPath(path.join(ROOT, 'web'));

server.listen(PORT, '127.0.0.1', () => {
  console.log('GUIDE_TASKS_DEV http://127.0.0.1:' + PORT + '/');
  scheduleBuild('startup');
});
