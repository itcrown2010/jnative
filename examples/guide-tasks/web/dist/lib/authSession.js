// Author: 张晓达
/**
 * Token session helper (Bearer).
 * Stores token in sessionStorage — ordinary web storage, NOT a secure vault.
 * Prefer sessionStorage so closing the tab drops the token; document that clearly.
 */
import { httpRequest } from './httpClient.js';
import { diag, newEventId } from './diag.js';

const KEY = 'jnative.guide-tasks.auth.v1';

export function loadAuth() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const o = JSON.parse(raw);
    if (!o || typeof o.token !== 'string') return null;
    return {
      token: o.token,
      userId: o.userId || '',
      username: o.username || '',
      expiresAt: o.expiresAt | 0,
    };
  } catch (_) {
    return null;
  }
}

export function saveAuth(session) {
  if (!session || !session.token) {
    sessionStorage.removeItem(KEY);
    return;
  }
  sessionStorage.setItem(
    KEY,
    JSON.stringify({
      token: session.token,
      userId: session.userId,
      username: session.username,
      expiresAt: session.expiresAt | 0,
    }),
  );
}

export function clearAuth() {
  sessionStorage.removeItem(KEY);
}

export function authExpired(session, now = Date.now()) {
  if (!session) return true;
  if (!session.expiresAt) return false;
  return session.expiresAt <= now + 5_000;
}

export function authHeaders(session) {
  if (!session || !session.token) return {};
  return { Authorization: 'Bearer ' + session.token };
}

/**
 * @param {string} baseUrl
 * @param {{username:string,password:string}} creds
 */
export async function login(baseUrl, creds) {
  const requestId = newEventId('login');
  const result = await httpRequest(joinUrl(baseUrl, '/v1/auth/login'), {
    method: 'POST',
    body: { username: creds.username, password: creds.password },
    requestId,
    timeoutMs: 15_000,
  });
  if (result.kind === 'ok' && result.json && result.json.token) {
    const session = {
      token: result.json.token,
      userId: result.json.userId,
      username: result.json.username,
      expiresAt: result.json.expiresAt | 0,
    };
    saveAuth(session);
    diag('network', 'login ok', { eventId: requestId, detail: { username: session.username } });
    return { ok: true, session, result };
  }
  diag('network', 'login failed', {
    eventId: requestId,
    detail: { kind: result.kind, status: result.status, code: result.code },
  });
  return {
    ok: false,
    result,
    error:
      result.kind === 'http_error'
        ? result.message || '登录失败'
        : result.kind === 'network'
          ? '网络连接失败'
          : result.kind === 'timeout'
            ? '登录超时'
            : result.message || '登录失败',
  };
}

export async function logout(baseUrl, session) {
  if (!session || !session.token) {
    clearAuth();
    return { ok: true };
  }
  const result = await httpRequest(joinUrl(baseUrl, '/v1/auth/logout'), {
    method: 'POST',
    headers: authHeaders(session),
    body: {},
    timeoutMs: 10_000,
  });
  clearAuth();
  return { ok: true, result };
}

export async function restoreSession(baseUrl, session) {
  if (!session) return { ok: false, reason: 'none' };
  if (authExpired(session)) {
    clearAuth();
    return { ok: false, reason: 'expired' };
  }
  const result = await httpRequest(joinUrl(baseUrl, '/v1/auth/me'), {
    method: 'GET',
    headers: authHeaders(session),
    timeoutMs: 10_000,
  });
  if (result.kind === 'ok' && result.json && result.json.ok) {
    return {
      ok: true,
      session: {
        ...session,
        userId: result.json.userId || session.userId,
        username: result.json.username || session.username,
      },
    };
  }
  if (result.kind === 'http_error' && (result.status === 401 || result.code === 'auth_expired')) {
    clearAuth();
    return { ok: false, reason: result.code === 'auth_expired' ? 'expired' : 'invalid', result };
  }
  return { ok: false, reason: 'transport', result };
}

function joinUrl(base, path) {
  return String(base || '').replace(/\/$/, '') + path;
}
