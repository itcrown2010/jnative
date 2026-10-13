#!/usr/bin/env node
// Author: 张晓达
/**
 * Real SQLite sync + Token auth API (localhost only by default).
 * Auth: Bearer session tokens from POST /v1/auth/login (not Cookie/CSRF).
 * Open mode: JN_SYNC_OPEN=1 (legacy tests). Optional static JN_SYNC_TOKEN as service token.
 * Fault injection: /v1/admin/fault (localhost only) — not a public business API.
 */
import http from 'http';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';
import { DatabaseSync } from 'node:sqlite';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const HOST = process.env.JN_SYNC_HOST || '127.0.0.1';
const PORT = Number(process.env.JN_SYNC_PORT || 8780);
const STATIC_TOKEN = process.env.JN_SYNC_TOKEN || '';
const OPEN = process.env.JN_SYNC_OPEN === '1';
const DB_PATH = process.env.JN_SYNC_DB || path.join(ROOT, '.build-staging/sync-data/tasks.db');
const ALLOW_PUBLIC = process.env.JN_SYNC_ALLOW_PUBLIC === '1';
const SESSION_TTL_MS = Number(process.env.JN_SYNC_SESSION_TTL_MS || 2 * 60 * 60 * 1000);
const CORS_ORIGINS = (process.env.JN_SYNC_CORS_ORIGINS ||
  'http://127.0.0.1:8767,http://localhost:8767,http://127.0.0.1:8794,http://localhost:8794,http://127.0.0.1:8799,http://localhost:8799,null')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

const TITLE_MAX = 200;
const NOTES_MAX = 4000;

if (HOST !== '127.0.0.1' && HOST !== 'localhost' && !ALLOW_PUBLIC) {
  console.error('Refusing non-localhost bind without JN_SYNC_ALLOW_PUBLIC=1');
  process.exit(1);
}

fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
const db = new DatabaseSync(DB_PATH);
db.exec(fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8'));
migrate();
seedUsers();

const fault = {
  dropResponseOnce: false,
  failNext: 0,
  delayMs: 0,
};

function migrate() {
  const cols = db.prepare("PRAGMA table_info('tasks')").all().map((c) => c.name);
  if (!cols.includes('user_id')) {
    db.exec("ALTER TABLE tasks ADD COLUMN user_id TEXT NOT NULL DEFAULT ''");
  }
  const icols = db.prepare("PRAGMA table_info('idempotency')").all().map((c) => c.name);
  if (!icols.includes('user_id')) {
    db.exec("ALTER TABLE idempotency ADD COLUMN user_id TEXT NOT NULL DEFAULT ''");
  }
  metaSet('schema_version', '2');
}

function hashPassword(password, salt) {
  return crypto.scryptSync(String(password), salt, 32).toString('hex');
}

function seedUsers() {
  const count = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
  if (count > 0) return;
  const now = Date.now();
  for (const [id, username, password] of [
    ['user-alice', 'alice', 'password-alice'],
    ['user-bob', 'bob', 'password-bob'],
  ]) {
    const salt = crypto.randomBytes(16).toString('hex');
    const password_hash = salt + ':' + hashPassword(password, salt);
    db.prepare(
      'INSERT INTO users(id, username, password_hash, created_at) VALUES(?,?,?,?)',
    ).run(id, username, password_hash, now);
  }
  console.log('Seeded local-dev users: alice / bob (passwords in docs; not for public)');
}

function metaGet(key) {
  const row = db.prepare('SELECT value FROM meta WHERE key = ?').get(key);
  return row ? row.value : null;
}
function metaSet(key, value) {
  db.prepare(
    'INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
  ).run(key, String(value));
}
function nextSeq() {
  const cur = Number(metaGet('change_seq') || '0');
  const n = cur + 1;
  metaSet('change_seq', n);
  return n;
}

function taskRow(userId, id) {
  return db.prepare('SELECT * FROM tasks WHERE id = ? AND user_id = ?').get(id, userId) || null;
}

function publicTask(row) {
  if (!row) return null;
  return {
    id: row.id,
    version: row.version,
    title: row.title,
    notes: row.notes,
    status: row.status,
    dueYmd: row.due_ymd,
    deleted: !!row.deleted,
    updatedSeq: row.updated_seq,
  };
}

function corsHeaders(req) {
  const origin = req.headers.origin || '';
  const allow =
    !origin || CORS_ORIGINS.includes(origin) || CORS_ORIGINS.includes('*')
      ? origin || CORS_ORIGINS[0]
      : '';
  const h = {
    'Content-Type': 'application/json; charset=utf-8',
    Vary: 'Origin',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-JNative-Token',
    'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS',
  };
  if (allow) h['Access-Control-Allow-Origin'] = allow;
  return h;
}

function authorize(req) {
  if (OPEN) return { ok: true, userId: 'open', username: 'open', open: true };
  const h = req.headers.authorization || '';
  const bearer = h.startsWith('Bearer ') ? h.slice(7).trim() : '';
  const headerTok = req.headers['x-jnative-token'];
  const token = bearer || (typeof headerTok === 'string' ? headerTok : '');
  if (!token) return { ok: false, reason: 'missing' };
  if (STATIC_TOKEN && token === STATIC_TOKEN) {
    return { ok: true, userId: 'service', username: 'service', service: true };
  }
  const row = db.prepare(
    'SELECT s.*, u.username FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?',
  ).get(token);
  if (!row || row.revoked) return { ok: false, reason: 'invalid' };
  if (row.expires_at < Date.now()) return { ok: false, reason: 'expired' };
  return { ok: true, userId: row.user_id, username: row.username, token };
}

function verifyPassword(username, password) {
  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(String(username || ''));
  if (!user) return null;
  const [salt, hash] = String(user.password_hash).split(':');
  if (!salt || !hash) return null;
  const next = hashPassword(password, salt);
  const a = Buffer.from(hash, 'hex');
  const b = Buffer.from(next, 'hex');
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  return user;
}

function readBody(req, max = 1_048_576) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (c) => {
      total += c.length;
      if (total > max) {
        reject(Object.assign(new Error('body too large'), { code: 'SIZE_LIMIT' }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (e) {
        reject(Object.assign(e, { code: 'PARSE' }));
      }
    });
    req.on('error', reject);
  });
}

function send(req, res, code, obj, { dropBody = false } = {}) {
  const headers = corsHeaders(req);
  if (dropBody) {
    res.writeHead(code, headers);
    res.end();
    return;
  }
  const body = JSON.stringify(obj);
  res.writeHead(code, headers);
  res.end(body);
}

function validatePayload(payload, type) {
  const fields = {};
  if (type === 'create' || type === 'update') {
    const title = String(payload.title || '');
    const notes = String(payload.notes || '');
    const status = Number(payload.status);
    const dueYmd = Number(payload.dueYmd || 0);
    if (!title.trim()) fields.title = '标题不能为空';
    if (title.length > TITLE_MAX) fields.title = '标题过长';
    if (notes.length > NOTES_MAX) fields.notes = '备注过长';
    if (![0, 1, 2, 3].includes(status)) fields.status = '状态非法';
    if (status === 1 && !notes.trim()) fields.notes = '进行中的任务需要填写备注';
    if (dueYmd < 0 || dueYmd > 99991231) fields.dueYmd = '日期非法';
  }
  return Object.keys(fields).length ? fields : null;
}

function applyOp(userId, body) {
  const opId = String(body.opId || '');
  const type = String(body.type || '');
  const taskId = String(body.taskId || '');
  const expectedVersion = Number(body.expectedVersion);
  const payload = body.payload || {};

  if (!opId || !taskId || !['create', 'update', 'delete'].includes(type)) {
    return { status: 400, json: { ok: false, code: 'validation', message: 'bad op' } };
  }
  if (opId.length > 80 || taskId.length > 64) {
    return { status: 400, json: { ok: false, code: 'validation', message: 'id too long' } };
  }

  const fieldErr = validatePayload(payload, type);
  if (fieldErr) {
    return {
      status: 400,
      json: { ok: false, code: 'validation', message: '字段校验失败', fields: fieldErr },
    };
  }

  const existing = db.prepare(
    'SELECT * FROM idempotency WHERE op_id = ? AND user_id = ?',
  ).get(opId, userId);
  if (existing) {
    return { status: existing.status_code, json: JSON.parse(existing.response_json) };
  }

  db.exec('BEGIN IMMEDIATE');
  try {
    const again = db.prepare(
      'SELECT * FROM idempotency WHERE op_id = ? AND user_id = ?',
    ).get(opId, userId);
    if (again) {
      db.exec('COMMIT');
      return { status: again.status_code, json: JSON.parse(again.response_json) };
    }

    let result;
    const now = Date.now();
    if (type === 'create') {
      const cur = taskRow(userId, taskId);
      if (cur && !cur.deleted) {
        result = { status: 409, json: { ok: false, code: 'conflict', server: publicTask(cur) } };
      } else {
        const seq = nextSeq();
        const version = 1;
        db.prepare(
          `INSERT INTO tasks(id,user_id,version,title,notes,status,due_ymd,deleted,updated_seq)
           VALUES(?,?,?,?,?,?,?,0,?)
           ON CONFLICT(id) DO UPDATE SET
             user_id=excluded.user_id,
             version=excluded.version, title=excluded.title, notes=excluded.notes,
             status=excluded.status, due_ymd=excluded.due_ymd, deleted=0, updated_seq=excluded.updated_seq
           WHERE tasks.user_id = excluded.user_id OR tasks.user_id = ''`,
        ).run(
          taskId,
          userId,
          version,
          String(payload.title || ''),
          String(payload.notes || ''),
          Number(payload.status || 0),
          Number(payload.dueYmd || 0),
          seq,
        );
        const owned = taskRow(userId, taskId);
        if (!owned) {
          result = { status: 403, json: { ok: false, code: 'forbidden', message: '任务不属于当前用户' } };
        } else {
          result = { status: 200, json: { ok: true, task: publicTask(owned) } };
        }
      }
    } else if (type === 'update') {
      const cur = taskRow(userId, taskId);
      if (!cur || cur.deleted) {
        result = {
          status: 409,
          json: {
            ok: false,
            code: 'conflict',
            server: publicTask(cur) || { id: taskId, deleted: true, version: cur ? cur.version : 0 },
          },
        };
      } else if (cur.version !== expectedVersion) {
        result = { status: 409, json: { ok: false, code: 'conflict', server: publicTask(cur) } };
      } else {
        const seq = nextSeq();
        const version = cur.version + 1;
        db.prepare(
          `UPDATE tasks SET version=?, title=?, notes=?, status=?, due_ymd=?, updated_seq=?
           WHERE id=? AND user_id=?`,
        ).run(
          version,
          String(payload.title || ''),
          String(payload.notes || ''),
          Number(payload.status || 0),
          Number(payload.dueYmd || 0),
          seq,
          taskId,
          userId,
        );
        result = { status: 200, json: { ok: true, task: publicTask(taskRow(userId, taskId)) } };
      }
    } else {
      const cur = taskRow(userId, taskId);
      if (!cur) {
        const seq = nextSeq();
        db.prepare(
          `INSERT INTO tasks(id,user_id,version,title,notes,status,due_ymd,deleted,updated_seq)
           VALUES(?,?,?, '', '', 0, 0, 1, ?)`,
        ).run(taskId, userId, 1, seq);
        result = { status: 200, json: { ok: true, task: publicTask(taskRow(userId, taskId)) } };
      } else if (cur.deleted && cur.version === expectedVersion) {
        result = { status: 200, json: { ok: true, task: publicTask(cur) } };
      } else if (cur.version !== expectedVersion) {
        result = { status: 409, json: { ok: false, code: 'conflict', server: publicTask(cur) } };
      } else {
        const seq = nextSeq();
        const version = cur.version + 1;
        db.prepare(
          `UPDATE tasks SET version=?, deleted=1, updated_seq=? WHERE id=? AND user_id=?`,
        ).run(version, seq, taskId, userId);
        result = { status: 200, json: { ok: true, task: publicTask(taskRow(userId, taskId)) } };
      }
    }

    db.prepare(
      `INSERT INTO idempotency(op_id, user_id, task_id, status_code, response_json, created_at)
       VALUES(?,?,?,?,?,?)`,
    ).run(opId, userId, taskId, result.status, JSON.stringify(result.json), now);
    db.exec('COMMIT');
    return result;
  } catch (e) {
    try {
      db.exec('ROLLBACK');
    } catch (_) {}
    throw e;
  }
}

const PUBLIC_PATHS = new Set(['/v1/health', '/v1/auth/login']);

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    send(req, res, 204, {});
    return;
  }
  const u = new URL(req.url || '/', 'http://' + HOST);
  const auth = authorize(req);
  const needsAuth = !PUBLIC_PATHS.has(u.pathname) && !u.pathname.startsWith('/v1/admin/');

  try {
    if (fault.delayMs > 0) await new Promise((r) => setTimeout(r, fault.delayMs));

    if (u.pathname === '/v1/health' && req.method === 'GET') {
      send(req, res, 200, {
        ok: true,
        db: path.basename(DB_PATH),
        schema: metaGet('schema_version'),
        auth: OPEN ? 'open' : 'token',
      });
      return;
    }

    if (u.pathname === '/v1/auth/login' && req.method === 'POST') {
      let body;
      try {
        body = await readBody(req);
      } catch (e) {
        send(req, res, e.code === 'SIZE_LIMIT' ? 413 : 400, {
          ok: false,
          code: 'validation',
          message: e.code === 'PARSE' ? 'JSON 无效' : '请求体过大',
        });
        return;
      }
      const user = verifyPassword(body.username, body.password);
      if (!user) {
        // constant-ish delay
        await new Promise((r) => setTimeout(r, 80));
        send(req, res, 401, { ok: false, code: 'auth', message: '用户名或密码错误' });
        return;
      }
      const token = crypto.randomBytes(32).toString('hex');
      const expiresAt = Date.now() + SESSION_TTL_MS;
      db.prepare(
        'INSERT INTO sessions(token, user_id, expires_at, revoked, created_at) VALUES(?,?,?,0,?)',
      ).run(token, user.id, expiresAt, Date.now());
      send(req, res, 200, {
        ok: true,
        token,
        userId: user.id,
        username: user.username,
        expiresAt,
        storageNote: 'client must not treat ordinary browser storage as a secure vault',
      });
      return;
    }

    if (needsAuth && !auth.ok) {
      send(req, res, 401, {
        ok: false,
        code: auth.reason === 'expired' ? 'auth_expired' : 'auth',
        message: auth.reason === 'expired' ? '登录已过期' : '未登录或令牌无效',
      });
      return;
    }

    if (u.pathname === '/v1/auth/logout' && req.method === 'POST') {
      if (auth.token) {
        db.prepare('UPDATE sessions SET revoked=1 WHERE token=?').run(auth.token);
      }
      send(req, res, 200, { ok: true });
      return;
    }

    if (u.pathname === '/v1/auth/me' && req.method === 'GET') {
      send(req, res, 200, {
        ok: true,
        userId: auth.userId,
        username: auth.username,
        open: !!auth.open,
      });
      return;
    }

    if (u.pathname === '/v1/admin/fault' && req.method === 'POST') {
      if (HOST !== '127.0.0.1' && HOST !== 'localhost') {
        send(req, res, 403, { ok: false, message: 'fault only on localhost' });
        return;
      }
      const body = await readBody(req);
      if (body.dropResponseOnce != null) fault.dropResponseOnce = !!body.dropResponseOnce;
      if (body.failNext != null) fault.failNext = Number(body.failNext) || 0;
      if (body.delayMs != null) fault.delayMs = Number(body.delayMs) || 0;
      send(req, res, 200, { ok: true, fault });
      return;
    }

    if (u.pathname === '/v1/ops' && req.method === 'POST') {
      if (fault.failNext) {
        const code = fault.failNext;
        fault.failNext = 0;
        send(req, res, code, { ok: false, code: 'injected', message: 'fault' });
        return;
      }
      let body;
      try {
        body = await readBody(req);
      } catch (e) {
        send(req, res, e.code === 'SIZE_LIMIT' ? 413 : 400, {
          ok: false,
          code: 'validation',
          message: 'bad body',
        });
        return;
      }
      const result = applyOp(auth.userId, body);
      const drop = fault.dropResponseOnce;
      if (drop) fault.dropResponseOnce = false;
      send(req, res, result.status, result.json, { dropBody: drop && result.status === 200 });
      return;
    }

    if (u.pathname === '/v1/changes' && req.method === 'GET') {
      const since = Number(u.searchParams.get('since') || '0');
      const limit = Math.min(100, Math.max(1, Number(u.searchParams.get('limit') || '50')));
      const rows = db
        .prepare(
          'SELECT * FROM tasks WHERE user_id = ? AND updated_seq > ? ORDER BY updated_seq ASC LIMIT ?',
        )
        .all(auth.userId, since, limit);
      const next = rows.length ? rows[rows.length - 1].updated_seq : since;
      send(req, res, 200, {
        ok: true,
        since,
        next,
        changes: rows.map(publicTask),
      });
      return;
    }

    if (u.pathname.startsWith('/v1/tasks/') && req.method === 'GET') {
      const id = decodeURIComponent(u.pathname.slice('/v1/tasks/'.length));
      const row = taskRow(auth.userId, id);
      if (!row) {
        send(req, res, 404, { ok: false, code: 'not_found' });
        return;
      }
      send(req, res, 200, { ok: true, task: publicTask(row) });
      return;
    }

    send(req, res, 404, { ok: false, message: 'not found' });
  } catch (e) {
    console.error(e && e.message ? e.message : e);
    send(req, res, 500, { ok: false, code: 'server', message: 'internal error' });
  }
});

server.listen(PORT, HOST, () => {
  console.log(
    'GUIDE_TASKS_SYNC_SERVER http://' +
      HOST +
      ':' +
      PORT +
      '/  db=' +
      DB_PATH +
      (OPEN ? ' auth=open' : STATIC_TOKEN ? ' auth=static+users' : ' auth=users'),
  );
});

function shutdown() {
  try {
    db.close();
  } catch (_) {}
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
