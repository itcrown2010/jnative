// Author: 张晓达
/**
 * Unified HTTP client for browser / WebView.
 * Classifies results; never treats HTTP error status as connection failure.
 * Credentials are never logged.
 */
import { diag, newEventId } from './diag.js';

export const HTTP_MAX_BODY = 1_048_576; // 1 MiB response bound
export const HTTP_DEFAULT_TIMEOUT_MS = 15_000;

/** @typedef {'ok'|'http_error'|'network'|'timeout'|'cancelled'|'parse_error'} HttpKind */

/**
 * @typedef {object} HttpResult
 * @property {HttpKind} kind
 * @property {number} [status]
 * @property {string} [statusText]
 * @property {*} [json]
 * @property {string} [text]
 * @property {string} [contentType]
 * @property {boolean} [abortedUnderlying] true when AbortController aborted the fetch
 * @property {string} [message] safe message (no credentials)
 * @property {string} [code] server code when present
 * @property {string} [requestId]
 */

const active = new Set(); // AbortControllers for page teardown

export function abortAllHttp() {
  for (const c of active) {
    try { c.abort(); } catch (_) {}
  }
  active.clear();
}

function safeMessage(status, json, fallback) {
  if (json && typeof json.message === 'string') {
    const m = json.message.slice(0, 200);
    if (!/password|token|secret|authorization/i.test(m)) return m;
  }
  if (json && typeof json.code === 'string') return json.code;
  return fallback || ('HTTP ' + status);
}

/**
 * @param {string} url
 * @param {object} [opts]
 * @returns {Promise<HttpResult>}
 */
export async function httpRequest(url, opts = {}) {
  const method = (opts.method || 'GET').toUpperCase();
  const timeoutMs = opts.timeoutMs != null ? opts.timeoutMs : HTTP_DEFAULT_TIMEOUT_MS;
  const maxBody = opts.maxBody != null ? opts.maxBody : HTTP_MAX_BODY;
  const requestId = opts.requestId || newEventId('http');
  const headers = { Accept: 'application/json', ...(opts.headers || {}) };
  // Strip accidental credential logging helpers
  const expectJson = opts.expectJson !== false;

  const controller = opts.signal ? null : new AbortController();
  const signal = opts.signal || controller.signal;
  if (controller) active.add(controller);

  let timer = null;
  let timedOut = false;
  if (timeoutMs > 0 && controller) {
    timer = setTimeout(() => {
      timedOut = true;
      try { controller.abort(); } catch (_) {}
    }, timeoutMs);
  }

  const init = {
    method,
    headers,
    cache: 'no-store',
    credentials: opts.credentials || 'omit',
    signal,
  };
  if (opts.body != null) {
    if (typeof opts.body === 'string') {
      init.body = opts.body;
      if (!headers['Content-Type'] && !headers['content-type']) {
        headers['Content-Type'] = 'application/json; charset=utf-8';
      }
    } else {
      init.body = JSON.stringify(opts.body);
      if (!headers['Content-Type'] && !headers['content-type']) {
        headers['Content-Type'] = 'application/json; charset=utf-8';
      }
    }
  }

  try {
    let resp;
    try {
      resp = await fetch(url, init);
    } catch (e) {
      if (timedOut || (e && e.name === 'AbortError')) {
        const kind = timedOut ? 'timeout' : 'cancelled';
        diag('network', kind + ' ' + method, {
          eventId: requestId,
          detail: { url: redactUrl(url), abortedUnderlying: true },
        });
        return {
          kind,
          abortedUnderlying: true,
          message: timedOut
            ? '请求超时（已中止浏览器侧请求；服务器是否完成未知）'
            : '请求已取消（已中止浏览器侧请求；服务器是否完成未知）',
          requestId,
        };
      }
      diag('network', 'network failure', {
        eventId: requestId,
        detail: { url: redactUrl(url), name: e && e.name },
      });
      return {
        kind: 'network',
        message: '网络连接失败',
        requestId,
      };
    }

    const status = resp.status | 0;
    const statusText = resp.statusText || '';
    const contentType = (resp.headers.get('content-type') || '').split(';')[0].trim();

    // Bound read
    const buf = await readBounded(resp, maxBody);
    if (buf.overflow) {
      return {
        kind: 'parse_error',
        status,
        statusText,
        contentType,
        message: '响应超过大小限制',
        requestId,
      };
    }
    const text = buf.text;

    let json = null;
    if (text && expectJson) {
      if (contentType && contentType !== 'application/json' && !contentType.endsWith('+json')) {
        // Still try parse; if fail → parse_error with note
      }
      try {
        json = JSON.parse(text);
      } catch (_) {
        if (status >= 200 && status < 300 && !text) {
          // empty success body — caller may treat as lost response
          json = null;
        } else if (expectJson && text) {
          return {
            kind: 'parse_error',
            status,
            statusText,
            contentType,
            text: text.slice(0, 200),
            message: '响应不是合法 JSON',
            requestId,
          };
        }
      }
    }

    if (status >= 200 && status < 300) {
      return {
        kind: 'ok',
        status,
        statusText,
        contentType,
        json,
        text,
        requestId,
      };
    }

    // Non-success HTTP — NOT network failure
    diag('network', 'http_error ' + status, {
      eventId: requestId,
      detail: { url: redactUrl(url), code: json && json.code },
    });
    return {
      kind: 'http_error',
      status,
      statusText,
      contentType,
      json,
      text,
      code: json && json.code,
      message: safeMessage(status, json, 'HTTP ' + status),
      requestId,
    };
  } finally {
    if (timer) clearTimeout(timer);
    if (controller) active.delete(controller);
  }
}

async function readBounded(resp, maxBody) {
  const reader = resp.body && resp.body.getReader ? resp.body.getReader() : null;
  if (!reader) {
    const text = await resp.text();
    if (text.length > maxBody) return { overflow: true, text: '' };
    return { overflow: false, text };
  }
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBody) {
      try { reader.cancel(); } catch (_) {}
      return { overflow: true, text: '' };
    }
    chunks.push(value);
  }
  const merged = new Uint8Array(total);
  let off = 0;
  for (const c of chunks) {
    merged.set(c, off);
    off += c.byteLength;
  }
  return { overflow: false, text: new TextDecoder('utf-8').decode(merged) };
}

function redactUrl(url) {
  try {
    const u = new URL(url, typeof location !== 'undefined' ? location.href : 'http://127.0.0.1');
    u.searchParams.delete('token');
    return u.origin + u.pathname;
  } catch (_) {
    return '(url)';
  }
}

/** Map HttpResult to sync-engine style kinds where useful. */
export function httpToSyncKind(result, syncApi) {
  if (result.kind === 'ok') {
    if (result.status === 200 && result.json == null && !result.text) {
      return { kind: 'temp', httpStatus: 200 };
    }
    return { kind: 'ok', task: result.json && result.json.task, json: result.json };
  }
  if (result.kind === 'timeout' || result.kind === 'network') {
    return { kind: 'temp', httpStatus: 0, transport: result.kind };
  }
  if (result.kind === 'cancelled') {
    return { kind: 'cancelled', httpStatus: 0 };
  }
  if (result.kind === 'parse_error') {
    return { kind: 'temp', httpStatus: result.status || 0, transport: 'parse_error' };
  }
  const status = result.status | 0;
  const bodyCode = result.code === 'validation' ? 2 : 0;
  if (status === 409 || result.code === 'conflict') {
    return { kind: 'conflict', httpStatus: status, server: result.json && result.json.server };
  }
  if (syncApi) {
    const cls = syncApi.classifyHttpError(status, bodyCode) | 0;
    if (cls === syncApi.ERR_AUTH) return { kind: 'auth', httpStatus: status };
    if (cls === syncApi.ERR_VALIDATION) {
      return {
        kind: 'validation',
        httpStatus: status,
        fields: result.json && result.json.fields,
        message: result.message,
      };
    }
    if (cls === syncApi.ERR_TEMP) return { kind: 'temp', httpStatus: status };
    if (cls === syncApi.ERR_CONFLICT) {
      return { kind: 'conflict', httpStatus: status, server: result.json && result.json.server };
    }
  }
  if (status === 401 || status === 403) return { kind: 'auth', httpStatus: status };
  if (status >= 500) return { kind: 'temp', httpStatus: status };
  return { kind: 'perm', httpStatus: status, message: result.message };
}
