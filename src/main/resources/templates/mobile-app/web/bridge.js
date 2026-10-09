// Author: 张晓达
/**
 * JNative Mobile bridge client v1 — handshake, whitelist, timeout, destroy.
 * Request:  { id, v, method, params }
 * Response: { id, ok, result } | { id, ok:false, error:{ code, message } }
 * Events:   { type:"event", name, params }
 */

export const BRIDGE_VERSION = 1;
const DEFAULT_TIMEOUT_MS = 5000;

const CORE_METHODS = new Set(['bridge.hello']);

export class JNativeBridge {
  constructor({ timeoutMs = DEFAULT_TIMEOUT_MS, requiredCapabilities = [] } = {}) {
    this.timeoutMs = timeoutMs;
    this.requiredCapabilities = requiredCapabilities;
    this.pending = new Map();
    this.lifecycleListeners = new Set();
    this.destroyed = false;
    this.ready = null;
    this.session = null; // { version, platform, capabilities:Set }
    this._onMessage = (ev) => this._handleHostMessage(ev);
    this._install();
  }

  _install() {
    if (typeof window === 'undefined') return;
    window.addEventListener('message', this._onMessage);
    if (window.JNativeHost && typeof window.JNativeHost.postMessage === 'function') {
      this.transport = (payload) => window.JNativeHost.postMessage(JSON.stringify(payload));
    } else if (window.webkit?.messageHandlers?.jnativeBridge) {
      this.transport = (payload) =>
        window.webkit.messageHandlers.jnativeBridge.postMessage(payload);
    } else {
      this.transport = (payload) =>
        window.postMessage({ __jnativeBridge: true, ...payload }, window.location.origin);
    }
  }

  destroy() {
    this.destroyed = true;
    this.ready = null;
    this.session = null;
    if (typeof window !== 'undefined') {
      window.removeEventListener('message', this._onMessage);
    }
    for (const [, p] of this.pending) {
      clearTimeout(p.timer);
      p.reject(Object.assign(new Error('bridge destroyed'), { code: 'DESTROYED' }));
    }
    this.pending.clear();
    this.lifecycleListeners.clear();
  }

  onLifecycle(fn) {
    this.lifecycleListeners.add(fn);
    return () => this.lifecycleListeners.delete(fn);
  }

  /** Handshake; safe to call again after reload/rebuild. */
  async init() {
    if (this.destroyed) {
      throw Object.assign(new Error('bridge destroyed'), { code: 'DESTROYED' });
    }
    if (this.ready) return this.ready;
    this.ready = (async () => {
      const result = await this._rawCall('bridge.hello', { clientVersion: BRIDGE_VERSION });
      const version = result?.version | 0;
      if (version !== BRIDGE_VERSION) {
        this.session = null;
        throw Object.assign(
          new Error('bridge protocol mismatch: host=' + version + ' client=' + BRIDGE_VERSION),
          { code: 'PROTOCOL_MISMATCH', hostVersion: version, clientVersion: BRIDGE_VERSION },
        );
      }
      const caps = new Set(Array.isArray(result.capabilities) ? result.capabilities : []);
      for (const need of this.requiredCapabilities) {
        if (!caps.has(need)) {
          throw Object.assign(new Error('capability not available: ' + need), {
            code: 'CAPABILITY_UNAVAILABLE',
            capability: need,
          });
        }
      }
      this.session = {
        version,
        platform: result.platform || 'unknown',
        capabilities: caps,
      };
      return this.session;
    })();
    try {
      return await this.ready;
    } catch (e) {
      this.ready = null;
      throw e;
    }
  }

  async call(method, params = {}, opts = {}) {
    if (!this.session) {
      await this.init();
    }
    if (method !== 'bridge.hello' && !this.session.capabilities.has(method)) {
      throw Object.assign(new Error('capability not available: ' + method), {
        code: 'CAPABILITY_UNAVAILABLE',
        capability: method,
      });
    }
    return this._rawCall(method, params, opts);
  }

  _rawCall(method, params = {}, { timeoutMs } = {}) {
    if (this.destroyed) {
      return Promise.reject(Object.assign(new Error('bridge destroyed'), { code: 'DESTROYED' }));
    }
    // Before handshake completes, only bridge.hello is permitted.
    if (method !== 'bridge.hello' && !this.session) {
      return Promise.reject(Object.assign(new Error('bridge not initialized'), {
        code: 'NOT_INITIALIZED',
      }));
    }
    const id = crypto.randomUUID();
    const req = { id, v: BRIDGE_VERSION, method, params };
    const ms = timeoutMs ?? this.timeoutMs;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(Object.assign(new Error('bridge timeout'), { code: 'TIMEOUT', id, method }));
      }, ms);
      this.pending.set(id, { resolve, reject, timer, method });
      try {
        this.transport(req);
      } catch (e) {
        clearTimeout(timer);
        this.pending.delete(id);
        reject(Object.assign(e instanceof Error ? e : new Error(String(e)), { code: 'TRANSPORT' }));
      }
    });
  }

  _handle(msg) {
    const p = this.pending.get(msg.id);
    if (!p) return;
    clearTimeout(p.timer);
    this.pending.delete(msg.id);
    if (msg.ok) {
      p.resolve(msg.result);
    } else {
      const err = new Error(msg.error?.message || 'bridge error');
      err.code = msg.error?.code || 'ERROR';
      p.reject(err);
    }
  }

  _handleEvent(msg) {
    for (const fn of this.lifecycleListeners) {
      try { fn(msg.name, msg.params || {}); } catch (e) { console.error('lifecycle listener error', e); }
    }
  }

  _handleHostMessage(ev) {
    if (this.destroyed) return;
    const data = ev?.data;
    if (!data || typeof data !== 'object') return;
    if (ev.origin && ev.origin !== window.location.origin && ev.origin !== 'null') return;
    if (data.type === 'event' && data.name) {
      this._handleEvent(data);
      return;
    }
    if (data.id && typeof data.ok === 'boolean') {
      this._handle(data);
    }
  }
}

const HOST_CAPS = [
  'platform.getName',
  'storage.get',
  'storage.set',
  'storage.remove',
  'lifecycle',
];

/** Web platform host — ordinary localStorage (not secure storage). */
export function installWebPlatformHost({ capabilities = HOST_CAPS } = {}) {
  const MAX_KEY = 128;
  const MAX_VAL = 4096;
  const caps = Array.from(new Set(['bridge.hello', ...capabilities]));

  function reply(req, ok, result, error) {
    const msg = ok ? { id: req.id, ok: true, result } : { id: req.id, ok: false, error };
    window.postMessage(msg, window.location.origin);
  }

  function validateKv(params, needValue) {
    if (!params || typeof params !== 'object') {
      return { code: 'BAD_PARAMS', message: 'params must be object' };
    }
    if (typeof params.key !== 'string' || params.key.length === 0 || params.key.length > MAX_KEY) {
      return { code: 'BAD_KEY', message: 'key must be string 1..' + MAX_KEY };
    }
    if (needValue) {
      if (typeof params.value !== 'string' || params.value.length > MAX_VAL) {
        return { code: 'BAD_VALUE', message: 'value must be string length 0..' + MAX_VAL };
      }
    }
    return null;
  }

  window.addEventListener('message', (ev) => {
    const data = ev.data;
    if (!data || !data.__jnativeBridge || !data.method) return;
    if (ev.origin !== window.location.origin) return;
    const req = data;
    if (req.v !== BRIDGE_VERSION) {
      reply(req, false, null, {
        code: 'PROTOCOL_MISMATCH',
        message: 'unsupported bridge version ' + req.v + ' (host=' + BRIDGE_VERSION + ')',
      });
      return;
    }
    try {
      switch (req.method) {
        case 'bridge.hello':
          reply(req, true, {
            version: BRIDGE_VERSION,
            platform: 'web',
            capabilities: caps.filter((c) => c !== 'bridge.hello'),
          });
          break;
        case 'platform.getName':
          if (!caps.includes('platform.getName')) {
            reply(req, false, null, { code: 'CAPABILITY_UNAVAILABLE', message: 'platform.getName' });
            break;
          }
          reply(req, true, 'web');
          break;
        case 'storage.get': {
          if (!caps.includes('storage.get')) {
            reply(req, false, null, { code: 'CAPABILITY_UNAVAILABLE', message: 'storage.get' });
            break;
          }
          const err = validateKv(req.params, false);
          if (err) { reply(req, false, null, err); break; }
          reply(req, true, localStorage.getItem(req.params.key));
          break;
        }
        case 'storage.set': {
          if (!caps.includes('storage.set')) {
            reply(req, false, null, { code: 'CAPABILITY_UNAVAILABLE', message: 'storage.set' });
            break;
          }
          const err = validateKv(req.params, true);
          if (err) { reply(req, false, null, err); break; }
          localStorage.setItem(req.params.key, req.params.value);
          reply(req, true, true);
          break;
        }
        case 'storage.remove': {
          if (!caps.includes('storage.remove')) {
            reply(req, false, null, { code: 'CAPABILITY_UNAVAILABLE', message: 'storage.remove' });
            break;
          }
          const err = validateKv(req.params, false);
          if (err) { reply(req, false, null, err); break; }
          localStorage.removeItem(req.params.key);
          reply(req, true, true);
          break;
        }
        default:
          reply(req, false, null, { code: 'METHOD_NOT_ALLOWED', message: 'unknown method' });
      }
    } catch (e) {
      reply(req, false, null, { code: 'HOST_ERROR', message: String(e && e.message ? e.message : e) });
    }
  });

  document.addEventListener('visibilitychange', () => {
    const name = document.hidden ? 'lifecycle.background' : 'lifecycle.foreground';
    window.postMessage({ type: 'event', name, params: {} }, window.location.origin);
  });
}
