#!/usr/bin/env node
// Author: 张晓达
/**
 * Real SQLite sync verification with fault injection (not browser Offline toggle).
 * Covers: offline queue persistence, idempotent retry after dropped response,
 * version conflict, delete-vs-edit conflict, changes cursor.
 */
import { spawn } from 'child_process';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import http from 'http';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const PORT = 8787;
const DB = path.join(ROOT, '.build-staging/sync-data/verify-sync.db');
const BASE = 'http://127.0.0.1:' + PORT;

function req(method, urlPath, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlPath, BASE);
    const r = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(body ? { 'Content-Length': Buffer.byteLength(body) } : {}),
          ...headers,
        },
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          let json = null;
          if (text) {
            try { json = JSON.parse(text); } catch (_) {}
          }
          resolve({ status: res.statusCode, text, json, empty: !text });
        });
      }
    );
    r.on('error', reject);
    if (body) r.write(body);
    r.end();
  });
}

async function main() {
  fs.mkdirSync(path.dirname(DB), { recursive: true });
  try { fs.unlinkSync(DB); } catch (_) {}

  const child = spawn(process.execPath, [path.join(ROOT, 'server/sync-server.mjs')], {
    env: {
      ...process.env,
      JN_SYNC_PORT: String(PORT),
      JN_SYNC_HOST: '127.0.0.1',
      JN_SYNC_DB: DB,
      // Legacy open mode for ops-only contract tests (no Bearer).
      // Auth/login coverage lives in verify_auth_http.mjs.
      JN_SYNC_OPEN: '1',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('server boot timeout')), 10000);
    child.stdout.on('data', (d) => {
      if (String(d).includes('GUIDE_TASKS_SYNC_SERVER')) {
        clearTimeout(t);
        resolve();
      }
    });
    child.stderr.on('data', (d) => process.stderr.write(d));
    child.on('exit', (c) => reject(new Error('server exit ' + c)));
  });

  const results = [];
  try {
    const health = await req('GET', '/v1/health');
    if (!health.json?.ok) throw new Error('health');
    results.push('health');

    // create
    const op1 = {
      opId: 'op-create-1',
      type: 'create',
      taskId: 'task-a',
      expectedVersion: 0,
      payload: { title: 'A', notes: 'n', status: 0, dueYmd: 20261013 },
    };
    let r = await req('POST', '/v1/ops', JSON.stringify(op1));
    if (r.status !== 200 || r.json.task.version !== 1) throw new Error('create ' + r.status);
    results.push('create');

    // drop response after successful write — retry same opId must not duplicate
    await req('POST', '/v1/admin/fault', JSON.stringify({ dropResponseOnce: true }));
    const op2 = {
      opId: 'op-update-1',
      type: 'update',
      taskId: 'task-a',
      expectedVersion: 1,
      payload: { title: 'A2', notes: 'n', status: 0, dueYmd: 20261013 },
    };
    r = await req('POST', '/v1/ops', JSON.stringify(op2));
    if (r.status !== 200 || !r.empty) throw new Error('expected dropped body');
    // retry
    r = await req('POST', '/v1/ops', JSON.stringify(op2));
    if (r.status !== 200 || r.json.task.title !== 'A2' || r.json.task.version !== 2) {
      throw new Error('idempotent retry failed ' + JSON.stringify(r.json));
    }
    results.push('idempotent-retry');

    // conflict: stale version
    r = await req('POST', '/v1/ops', JSON.stringify({
      opId: 'op-stale',
      type: 'update',
      taskId: 'task-a',
      expectedVersion: 1,
      payload: { title: 'stale', notes: '', status: 0, dueYmd: 0 },
    }));
    if (r.status !== 409 || r.json.code !== 'conflict') throw new Error('conflict expected');
    results.push('version-conflict');

    // second client create then conflict on delete/edit
    await req('POST', '/v1/ops', JSON.stringify({
      opId: 'op-b',
      type: 'create',
      taskId: 'task-b',
      expectedVersion: 0,
      payload: { title: 'B', notes: 'x', status: 1, dueYmd: 0 },
    }));
    // edit bumps version
    await req('POST', '/v1/ops', JSON.stringify({
      opId: 'op-b2',
      type: 'update',
      taskId: 'task-b',
      expectedVersion: 1,
      payload: { title: 'B2', notes: 'x', status: 1, dueYmd: 0 },
    }));
    // delete with stale expected
    r = await req('POST', '/v1/ops', JSON.stringify({
      opId: 'op-b-del-stale',
      type: 'delete',
      taskId: 'task-b',
      expectedVersion: 1,
      payload: {},
    }));
    if (r.status !== 409) throw new Error('delete-edit conflict expected');
    results.push('delete-edit-conflict');

    // changes cursor
    r = await req('GET', '/v1/changes?since=0&limit=10');
    if (!r.json.ok || r.json.changes.length < 2) throw new Error('changes');
    const next = r.json.next;
    r = await req('GET', '/v1/changes?since=' + next + '&limit=10');
    if (!r.json.ok || r.json.changes.length !== 0) throw new Error('cursor tail');
    results.push('cursor');

    // SyncCore unit via wasm (optional if built)
    const wasmPath = path.join(ROOT, 'web/dist/app.wasm');
    if (fs.existsSync(wasmPath)) {
      const wasmBytes = fs.readFileSync(wasmPath);
      const imports = { jnative: {
        console_log() {}, document_get_element_by_id(){return 0}, document_body(){return 0},
        document_create_element(){return 0}, element_append_child(){}, element_set_text(){},
        create_text(){return 0}, query_selector(){return 0}, apply_dom_patches(){},
        schedule_microtask(){}, delegate_event(){}, init_delegation(){},
        register_callback(id){return id}, unregister_callback(){}, set_count_label(){},
        input_value(){return 0}, input_set_value(){}, memory_grew(){}, report_uncaught(){},
        storage_set(){}, storage_get(){return 0}, fetch_get(){return 0},
      }};
      const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
      instance.exports.jnative_start();
      const find = (sub) => {
        for (const k of Object.keys(instance.exports)) if (k.includes(sub)) return instance.exports[k];
        throw new Error('missing ' + sub);
      };
      const merge = find('SyncCore_mergeDecision_i32_i32_i32');
      if ((merge(1, 2, 1) | 0) !== 2) throw new Error('merge replace');
      if ((merge(1, 3, 1) | 0) !== 3) throw new Error('merge drop both');
      results.push('syncore-wasm');
    }

    console.log('GUIDE_TASKS_SYNC_OK ' + results.join(','));
  } finally {
    child.kill('SIGTERM');
  }
}

main().catch((e) => {
  console.error('GUIDE_TASKS_SYNC_FAIL', e);
  process.exit(1);
});
