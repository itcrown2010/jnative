#!/usr/bin/env node
// Author: 张晓达
/**
 * Real-service acceptance: Token auth, HTTP result kinds, form validation,
 * user isolation, idempotent retry after dropped response, timeout/fault.
 * Uses an independent SQLite DB. Fault injection only via localhost admin.
 */
import { spawn } from 'child_process';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import http from 'http';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const PORT = 8788;
const DB = path.join(ROOT, '.build-staging/sync-data/verify-auth.db');
const BASE = 'http://127.0.0.1:' + PORT;

function req(method, urlPath, body, headers = {}, opts = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlPath, BASE);
    const r = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method,
        headers: {
          Accept: 'application/json',
          ...(body
            ? {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(body),
              }
            : {}),
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
            try {
              json = JSON.parse(text);
            } catch (_) {}
          }
          resolve({
            status: res.statusCode,
            text,
            json,
            empty: !text,
            contentType: res.headers['content-type'] || '',
          });
        });
      },
    );
    r.on('error', reject);
    if (opts.timeoutMs) {
      r.setTimeout(opts.timeoutMs, () => {
        r.destroy(new Error('timeout'));
      });
    }
    if (body) r.write(body);
    r.end();
  });
}

function classifyNode(res, err) {
  if (err) {
    if (String(err.message || err).includes('timeout')) return 'timeout';
    return 'network';
  }
  if (res.status >= 200 && res.status < 300) {
    if (res.empty) return 'ok_empty';
    if (res.text && res.json == null) return 'parse_error';
    return 'ok';
  }
  return 'http_error'; // never report as network
}

async function login(username, password) {
  const r = await req(
    'POST',
    '/v1/auth/login',
    JSON.stringify({ username, password }),
  );
  return r;
}

async function main() {
  fs.mkdirSync(path.dirname(DB), { recursive: true });
  try {
    fs.unlinkSync(DB);
  } catch (_) {}

  const child = spawn(process.execPath, [path.join(ROOT, 'server/sync-server.mjs')], {
    env: {
      ...process.env,
      JN_SYNC_PORT: String(PORT),
      JN_SYNC_HOST: '127.0.0.1',
      JN_SYNC_DB: DB,
      JN_SYNC_SESSION_TTL_MS: String(60 * 60 * 1000),
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
    // 1. login success / failure
    let r = await login('alice', 'wrong');
    if (classifyNode(r) !== 'http_error' || r.status !== 401) {
      throw new Error('bad login should be http_error 401 not network');
    }
    results.push('login-fail');

    r = await login('alice', 'password-alice');
    if (r.status !== 200 || !r.json?.token) throw new Error('login alice');
    const alice = r.json;
    results.push('login-ok');

    // 2. session restore + me
    r = await req('GET', '/v1/auth/me', null, {
      Authorization: 'Bearer ' + alice.token,
    });
    if (r.status !== 200 || r.json.username !== 'alice') throw new Error('me');
    results.push('session-me');

    // 3. create/update/delete persist
    const taskId = 'auth-task-1';
    r = await req(
      'POST',
      '/v1/ops',
      JSON.stringify({
        opId: 'auth-op-c1',
        type: 'create',
        taskId,
        expectedVersion: 0,
        payload: { title: 'Hello', notes: '', status: 0, dueYmd: 20261013 },
      }),
      { Authorization: 'Bearer ' + alice.token },
    );
    if (r.status !== 200 || r.json.task.version !== 1) throw new Error('create');
    r = await req(
      'POST',
      '/v1/ops',
      JSON.stringify({
        opId: 'auth-op-u1',
        type: 'update',
        taskId,
        expectedVersion: 1,
        payload: { title: 'Hello2', notes: 'n', status: 0, dueYmd: 20261013 },
      }),
      { Authorization: 'Bearer ' + alice.token },
    );
    if (r.status !== 200 || r.json.task.title !== 'Hello2') throw new Error('update');
    results.push('crud-write');

    // 4. validation + permission
    r = await req(
      'POST',
      '/v1/ops',
      JSON.stringify({
        opId: 'auth-op-bad',
        type: 'create',
        taskId: 'auth-bad',
        expectedVersion: 0,
        payload: { title: '', notes: '', status: 1, dueYmd: 0 },
      }),
      { Authorization: 'Bearer ' + alice.token },
    );
    if (r.status !== 400 || r.json.code !== 'validation' || !r.json.fields?.title) {
      throw new Error('validation fields');
    }
    if (classifyNode(r) !== 'http_error') throw new Error('validation must be http_error');
    results.push('validation');

    // 5. network-ish: delay + client timeout classification
    await req('POST', '/v1/admin/fault', JSON.stringify({ delayMs: 3000 }));
    try {
      await req('GET', '/v1/health', null, {}, { timeoutMs: 200 });
      throw new Error('expected timeout');
    } catch (e) {
      if (classifyNode(null, e) !== 'timeout') throw e;
    }
    await req('POST', '/v1/admin/fault', JSON.stringify({ delayMs: 0 }));
    results.push('timeout');

    // illegal / empty success after write
    await req('POST', '/v1/admin/fault', JSON.stringify({ dropResponseOnce: true }));
    const opLost = {
      opId: 'auth-op-lost',
      type: 'update',
      taskId,
      expectedVersion: 2,
      payload: { title: 'AfterLost', notes: 'n', status: 0, dueYmd: 20261013 },
    };
    r = await req('POST', '/v1/ops', JSON.stringify(opLost), {
      Authorization: 'Bearer ' + alice.token,
    });
    if (!r.empty || r.status !== 200) throw new Error('drop response');
    if (classifyNode(r) !== 'ok_empty') throw new Error('empty body kind');
    // 6+7 double submit / lost response — same opId
    r = await req('POST', '/v1/ops', JSON.stringify(opLost), {
      Authorization: 'Bearer ' + alice.token,
    });
    if (r.status !== 200 || r.json.task.title !== 'AfterLost' || r.json.task.version !== 3) {
      throw new Error('idempotent after lost');
    }
    r = await req('POST', '/v1/ops', JSON.stringify(opLost), {
      Authorization: 'Bearer ' + alice.token,
    });
    if (r.status !== 200 || r.json.task.version !== 3) throw new Error('double submit same opId');
    results.push('idempotent-lost');

    // 8. logout revokes
    r = await req('POST', '/v1/auth/logout', '{}', {
      Authorization: 'Bearer ' + alice.token,
    });
    if (r.status !== 200) throw new Error('logout');
    r = await req('GET', '/v1/auth/me', null, {
      Authorization: 'Bearer ' + alice.token,
    });
    if (r.status !== 401 || r.json.code !== 'auth') throw new Error('revoked token');
    results.push('logout-revoke');

    // 9. user isolation
    const alice2 = (await login('alice', 'password-alice')).json;
    const bob = (await login('bob', 'password-bob')).json;
    r = await req(
      'POST',
      '/v1/ops',
      JSON.stringify({
        opId: 'bob-steal',
        type: 'update',
        taskId,
        expectedVersion: 3,
        payload: { title: 'stolen', notes: '', status: 0, dueYmd: 0 },
      }),
      { Authorization: 'Bearer ' + bob.token },
    );
    // bob cannot see alice's task → conflict/missing, not overwrite
    if (r.status === 200 && r.json.task && r.json.task.title === 'stolen') {
      throw new Error('cross-user write');
    }
    r = await req('GET', '/v1/changes?since=0&limit=50', null, {
      Authorization: 'Bearer ' + bob.token,
    });
    const bobSeesAlice = (r.json.changes || []).some((c) => c.id === taskId);
    if (bobSeesAlice) throw new Error('bob saw alice task');
    r = await req('GET', '/v1/changes?since=0&limit=50', null, {
      Authorization: 'Bearer ' + alice2.token,
    });
    if (!(r.json.changes || []).some((c) => c.id === taskId && c.title === 'AfterLost')) {
      throw new Error('alice missing own task');
    }
    results.push('isolation');

    // delete
    r = await req(
      'POST',
      '/v1/ops',
      JSON.stringify({
        opId: 'auth-op-del',
        type: 'delete',
        taskId,
        expectedVersion: 3,
        payload: {},
      }),
      { Authorization: 'Bearer ' + alice2.token },
    );
    if (r.status !== 200 || !r.json.task.deleted) throw new Error('delete');
    results.push('delete');

    // expire: short TTL server would need restart — simulate with bad token
    r = await req('GET', '/v1/auth/me', null, { Authorization: 'Bearer deadbeef' });
    if (r.status !== 401 || classifyNode(r) !== 'http_error') throw new Error('bad token');
    results.push('bad-token-http_error');

    console.log('GUIDE_TASKS_AUTH_HTTP_OK ' + results.join(','));
  } finally {
    child.kill('SIGTERM');
  }
}

main().catch((e) => {
  console.error('GUIDE_TASKS_AUTH_HTTP_FAIL', e);
  process.exit(1);
});
