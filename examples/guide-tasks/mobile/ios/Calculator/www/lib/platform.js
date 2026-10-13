// Author: 张晓达
/**
 * Unified platform capability API — discover by capability name, never by OS string.
 * Web: browser File / Share APIs with explicit fallbacks.
 * Android/iOS WebView: bridge whitelist methods (content URI / security-scoped URLs).
 */
import { JNativeBridge, BRIDGE_VERSION } from './bridge.js';
import {
  CAPABILITY_NAMES,
  FILE_IO_TIMEOUT_MS,
  FILE_MAX_BYTES,
  FILE_MAX_MIME,
  FILE_MAX_NAME,
  FILE_PICK_TIMEOUT_MS,
  platformError,
  validateFileName,
} from './platformLimits.js';
import { diag, newEventId } from './diag.js';

function hasNativeHost() {
  return !!(
    (typeof window !== 'undefined' && window.JNativeHost && typeof window.JNativeHost.postMessage === 'function') ||
    (typeof window !== 'undefined' && window.webkit?.messageHandlers?.jnativeBridge)
  );
}

function bytesToBase64(bytes) {
  let bin = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(bin);
}

function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Web-only handle table (File / Blob). */
function createWebFileStore() {
  const map = new Map();
  let seq = 0;
  return {
    put(file) {
      const id = 'wh-' + (++seq) + '-' + Date.now().toString(36);
      map.set(id, file);
      return id;
    },
    get(id) {
      return map.get(id) || null;
    },
    release(id) {
      return map.delete(id);
    },
    clear() {
      map.clear();
    },
  };
}

async function webQueryCapabilities(denied) {
  const shareOk = typeof navigator !== 'undefined' && typeof navigator.share === 'function';
  const fileOk = typeof document !== 'undefined';
  return CAPABILITY_NAMES.map((name) => {
    if (denied.has(name)) return { name, status: 'denied' };
    if (name.startsWith('file.') || name === 'share.system') {
      if (name === 'share.system') {
        return { name, status: shareOk || fileOk ? 'available' : 'unsupported' };
      }
      return { name, status: fileOk ? 'available' : 'unsupported' };
    }
    if (name === 'lifecycle' || name.startsWith('platform.') || name.startsWith('storage.')) {
      return { name, status: 'available' };
    }
    return { name, status: 'unsupported' };
  });
}

function webPickFile(store, opts) {
  const accept = Array.isArray(opts.accept) ? opts.accept.join(',') : (opts.accept || '.json,application/json,.jnbackup');
  const maxBytes = Math.min(opts.maxBytes | 0 || FILE_MAX_BYTES, FILE_MAX_BYTES);
  return new Promise((resolve, reject) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.style.display = 'none';
    let settled = false;
    const finish = (fn, arg) => {
      if (settled) return;
      settled = true;
      input.remove();
      window.removeEventListener('focus', onFocus);
      fn(arg);
    };
    const onFocus = () => {
      // User cancelled: change never fires; detect after focus returns.
      setTimeout(() => {
        if (!settled && !input.files?.length) {
          finish(reject, platformError('USER_CANCELLED', 'user cancelled file pick'));
        }
      }, 400);
    };
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (!file) {
        finish(reject, platformError('USER_CANCELLED', 'user cancelled file pick'));
        return;
      }
      if (file.size > maxBytes) {
        finish(reject, platformError('SIZE_LIMIT', 'file exceeds ' + maxBytes + ' bytes', { size: file.size, maxBytes }));
        return;
      }
      const nameErr = validateFileName(file.name.length > FILE_MAX_NAME ? file.name.slice(0, FILE_MAX_NAME) : file.name);
      const safeName = (file.name || 'file').slice(0, FILE_MAX_NAME);
      if (nameErr && !file.name) {
        finish(reject, nameErr);
        return;
      }
      const handleId = store.put(file);
      finish(resolve, {
        handleId,
        name: safeName,
        mimeType: (file.type || 'application/octet-stream').slice(0, FILE_MAX_MIME),
        size: file.size | 0,
      });
    });
    document.body.appendChild(input);
    window.addEventListener('focus', onFocus);
    input.click();
  });
}

async function webReadFile(store, handleId, opts) {
  const file = store.get(handleId);
  if (!file) throw platformError('BAD_PARAMS', 'unknown or released file handle');
  const maxBytes = Math.min(opts.maxBytes | 0 || FILE_MAX_BYTES, FILE_MAX_BYTES);
  if (file.size > maxBytes) {
    throw platformError('SIZE_LIMIT', 'file exceeds ' + maxBytes + ' bytes', { size: file.size, maxBytes });
  }
  const as = opts.as === 'bytesBase64' ? 'bytesBase64' : 'text';
  if (as === 'text') {
    let text;
    try {
      text = await file.text();
    } catch (e) {
      throw platformError('IO_ERROR', 'failed to read file as text');
    }
    // UTF-8 decode already; reject obvious binary NUL spam for backup path
    return { text, bytesRead: file.size | 0, encoding: 'utf-8' };
  }
  const buf = await file.arrayBuffer();
  const bytes = new Uint8Array(buf);
  return { bytesBase64: bytesToBase64(bytes), bytesRead: bytes.length, encoding: 'base64' };
}

async function webExportFile(opts) {
  const nameErr = validateFileName(opts.name);
  if (nameErr) throw nameErr;
  const mime = (opts.mimeType || 'application/octet-stream').slice(0, FILE_MAX_MIME);
  let blob;
  if (typeof opts.text === 'string') {
    const enc = new TextEncoder();
    const bytes = enc.encode(opts.text);
    if (bytes.length > FILE_MAX_BYTES) {
      throw platformError('SIZE_LIMIT', 'export exceeds ' + FILE_MAX_BYTES + ' bytes');
    }
    blob = new Blob([bytes], { type: mime });
  } else if (typeof opts.bytesBase64 === 'string') {
    const bytes = base64ToBytes(opts.bytesBase64);
    if (bytes.length > FILE_MAX_BYTES) {
      throw platformError('SIZE_LIMIT', 'export exceeds ' + FILE_MAX_BYTES + ' bytes');
    }
    blob = new Blob([bytes], { type: mime });
  } else {
    throw platformError('BAD_PARAMS', 'text or bytesBase64 required');
  }

  if (typeof window.showSaveFilePicker === 'function') {
    try {
      const handle = await window.showSaveFilePicker({
        suggestedName: opts.name,
        types: [{ description: mime, accept: { [mime]: ['.' + (opts.name.split('.').pop() || 'bin')] } }],
      });
      const writable = await handle.createWritable();
      await writable.write(blob);
      await writable.close();
      return { exported: true, method: 'saveFilePicker' };
    } catch (e) {
      if (e && (e.name === 'AbortError' || e.code === 'USER_CANCELLED')) {
        throw platformError('USER_CANCELLED', 'user cancelled export');
      }
      // fall through to download
    }
  }
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = url;
    a.download = opts.name;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
    return { exported: true, method: 'download' };
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }
}

async function webShare(opts) {
  const title = typeof opts.title === 'string' ? opts.title.slice(0, 120) : '';
  const text = typeof opts.text === 'string' ? opts.text : '';
  if (text && new TextEncoder().encode(text).length > FILE_MAX_BYTES) {
    throw platformError('SIZE_LIMIT', 'share payload exceeds limit');
  }
  const files = [];
  if (opts.name && (opts.text != null || opts.bytesBase64)) {
    const mime = (opts.mimeType || 'application/json').slice(0, FILE_MAX_MIME);
    let blob;
    if (typeof opts.text === 'string') blob = new Blob([opts.text], { type: mime });
    else blob = new Blob([base64ToBytes(opts.bytesBase64)], { type: mime });
    files.push(new File([blob], opts.name.slice(0, FILE_MAX_NAME), { type: mime }));
  }
  if (typeof navigator.share === 'function') {
    try {
      const data = { title };
      if (text) data.text = text;
      if (files.length && navigator.canShare && navigator.canShare({ files })) {
        data.files = files;
      } else if (files.length && !text) {
        data.text = await files[0].text();
      }
      await navigator.share(data);
      // Browsers often resolve without proving delivery
      return { status: 'unknown', note: 'share sheet dismissed; delivery not confirmed' };
    } catch (e) {
      if (e && e.name === 'AbortError') {
        throw platformError('USER_CANCELLED', 'user cancelled share');
      }
      throw platformError('IO_ERROR', e && e.message ? e.message : 'share failed');
    }
  }
  // Fallback: trigger download of file payload
  if (files.length || text) {
    await webExportFile({
      name: opts.name || 'share.txt',
      mimeType: opts.mimeType || 'text/plain',
      text: text || (files[0] ? await files[0].text() : ''),
    });
    return { status: 'fallback-download', note: 'navigator.share unsupported; exported as download' };
  }
  throw platformError('UNSUPPORTED', 'share.system not available');
}

/**
 * @returns {Promise<{
 *   init: Function,
 *   queryCapabilities: Function,
 *   pickFile: Function,
 *   readFile: Function,
 *   releaseFile: Function,
 *   exportFile: Function,
 *   share: Function,
 *   destroy: Function,
 *   mode: string,
 * }>}
 */
export async function createPlatform() {
  const denied = new Set();
  const eventId = newEventId('plat');

  if (hasNativeHost()) {
    const bridge = new JNativeBridge({
      timeoutMs: FILE_IO_TIMEOUT_MS,
      requiredCapabilities: [],
    });
    await bridge.init();
    diag('bridge', 'native platform session', {
      eventId,
      detail: { platform: bridge.session?.platform, caps: [...(bridge.session?.capabilities || [])] },
    });

    const call = (method, params, timeoutMs) =>
      bridge.call(method, params || {}, { timeoutMs: timeoutMs || FILE_IO_TIMEOUT_MS }).catch((e) => {
        if (e && e.code === 'PERMISSION_DENIED') denied.add(method.split('.')[0] === 'share' ? 'share.system' : method);
        throw e;
      });

    return {
      mode: 'native-bridge',
      bridgeVersion: BRIDGE_VERSION,
      async init() {
        return bridge.session;
      },
      async queryCapabilities() {
        try {
          const r = await call('platform.queryCapabilities', {});
          if (Array.isArray(r)) return r;
        } catch (_) { /* fall through */ }
        const have = bridge.session?.capabilities || new Set();
        return CAPABILITY_NAMES.map((name) => ({
          name,
          status: denied.has(name) ? 'denied' : have.has(name) ? 'available' : 'unsupported',
        }));
      },
      pickFile(opts = {}) {
        return call('file.pick', {
          accept: opts.accept || ['.json', '.jnbackup', 'application/json'],
          maxBytes: Math.min(opts.maxBytes || FILE_MAX_BYTES, FILE_MAX_BYTES),
          multiple: false,
        }, FILE_PICK_TIMEOUT_MS);
      },
      readFile(handleId, opts = {}) {
        return call('file.read', {
          handleId,
          as: opts.as === 'bytesBase64' ? 'bytesBase64' : 'text',
          maxBytes: Math.min(opts.maxBytes || FILE_MAX_BYTES, FILE_MAX_BYTES),
        });
      },
      releaseFile(handleId) {
        return call('file.release', { handleId });
      },
      exportFile(opts = {}) {
        return call('file.export', {
          name: opts.name,
          mimeType: opts.mimeType || 'application/json',
          text: opts.text,
          bytesBase64: opts.bytesBase64,
          maxBytes: FILE_MAX_BYTES,
        }, FILE_PICK_TIMEOUT_MS);
      },
      share(opts = {}) {
        return call('share.system', {
          title: opts.title || '',
          text: opts.text,
          name: opts.name,
          mimeType: opts.mimeType,
          bytesBase64: opts.bytesBase64,
        }, FILE_PICK_TIMEOUT_MS);
      },
      destroy() {
        bridge.destroy();
      },
    };
  }

  // Pure Web adapters — capability discovery without guessing OS names.
  const store = createWebFileStore();
  diag('javascript', 'web platform adapters active', { eventId });

  return {
    mode: 'web',
    bridgeVersion: BRIDGE_VERSION,
    async init() {
      return { platform: 'web', capabilities: new Set(CAPABILITY_NAMES) };
    },
    queryCapabilities: () => webQueryCapabilities(denied),
    pickFile: (opts) => webPickFile(store, opts || {}),
    readFile: (handleId, opts) => webReadFile(store, handleId, opts || {}),
    async releaseFile(handleId) {
      store.release(handleId);
      return true;
    },
    exportFile: (opts) => webExportFile(opts || {}),
    share: (opts) => webShare(opts || {}),
    destroy() {
      store.clear();
    },
  };
}

export { FILE_MAX_BYTES, CAPABILITY_NAMES, platformError };
