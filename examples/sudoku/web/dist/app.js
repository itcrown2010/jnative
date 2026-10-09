/* JNative wasm32-browser loader — generated glue */
const handles = new Map();
let nextHandle = 1;
let memory = null;
let exports = null;
let patchBuf = null;
let delegationRoot = null;
/** cbId → { el, type, handler } for host-side unsubscribe */
const callbackRegistry = new Map();

function retain(obj) {
  const id = nextHandle++;
  handles.set(id, obj);
  return id;
}
function release(id) {
  handles.delete(id | 0);
}
function getHandle(id) {
  return handles.get(id | 0);
}

/** Always use fresh memory.buffer — required after memory.grow. */
function u8(ptr, len) {
  return new Uint8Array(memory.buffer, ptr >>> 0, len >>> 0);
}
function view() {
  return new DataView(memory.buffer);
}

function readUtf8(ptr, len) {
  return new TextDecoder('utf-8').decode(u8(ptr, len));
}

function readWasmString(ptr) {
  if (!ptr) return '';
  const len = view().getUint32(ptr >>> 0, true);
  return readUtf8(ptr + 4, len);
}

function writePatchString(off, str) {
  const enc = new TextEncoder().encode(str ?? '');
  view().setUint32(off >>> 0, enc.length, true);
  u8(off + 4, enc.length).set(enc);
  return off + 4 + enc.length;
}

/**
 * Managed WASM object roots (separate from DOM host handles).
 * Handle format: ((gen & 0xFFFF) << 16) | (slot+1); 0 = null/invalid.
 */
function retainManaged(ptr) {
  if (!ptr || !exports?.jn_host_root_retain) return 0;
  return exports.jn_host_root_retain(ptr | 0) | 0;
}
function releaseManaged(handle) {
  if (!handle || !exports?.jn_host_root_release) return;
  exports.jn_host_root_release(handle | 0);
}
function getManaged(handle) {
  if (!handle || !exports?.jn_host_root_get) return 0;
  return exports.jn_host_root_get(handle | 0) | 0;
}
function gcStats() {
  if (!exports?.jn_gc_stats) return null;
  const [allocBytes, liveBytes, freeBytes, heapCap, gcCount, hostRoots, allocFail] =
    exports.jn_gc_stats();
  return { allocBytes, liveBytes, freeBytes, heapCap, gcCount, hostRoots, allocFail };
}
function gcCollect() {
  if (exports?.jn_gc_collect) exports.jn_gc_collect();
}

/**
 * Temporary host string into managed heap. Caller must retainManaged
 * for long-lived use, or releaseManaged after a short call window.
 * Returns raw ptr (not a handle) — only safe until next GC without a root.
 */
function writeWasmString(str) {
  if (!exports?.jn_bump || !memory) throw new Error('wasm not ready');
  const enc = new TextEncoder().encode(str ?? '');
  const ptr = exports.jn_bump(enc.length + 4) >>> 0;
  if (!ptr) throw new Error('jn_bump alloc failed');
  view().setUint32(ptr, enc.length, true);
  u8(ptr + 4, enc.length).set(enc);
  return ptr;
}
/** Alloc + root for long-lived JS→WASM strings. */
function writeWasmStringRooted(str) {
  const ptr = writeWasmString(str);
  return { ptr, handle: retainManaged(ptr) };
}

function applyDomPatches(bufPtr, byteLen) {
  const v = view();
  let off = bufPtr >>> 0;
  const end = off + (byteLen >>> 0);
  while (off + 4 <= end) {
    const op = v.getInt32(off, true); off += 4;
    if (op === 1) {
      const node = v.getInt32(off, true); off += 4;
      const strPtr = v.getInt32(off, true); off += 4;
      const el = getHandle(node);
      if (el) el.textContent = readWasmString(strPtr);
    } else if (op === 2) {
      const node = v.getInt32(off, true); off += 4;
      const nPtr = v.getInt32(off, true); off += 4;
      const vPtr = v.getInt32(off, true); off += 4;
      const el = getHandle(node);
      if (el) el.setAttribute(readWasmString(nPtr), readWasmString(vPtr));
    } else if (op === 4) {
      const node = v.getInt32(off, true); off += 4;
      const nPtr = v.getInt32(off, true); off += 4;
      const vPtr = v.getInt32(off, true); off += 4;
      const el = getHandle(node);
      const name = readWasmString(nPtr);
      const val = readWasmString(vPtr);
      if (el) {
        if (name === 'value') el.value = val;
        else if (name === 'checked') el.checked = val === 'true';
        else el[name] = val;
      }
    } else if (op === 5) {
      const parent = v.getInt32(off, true); off += 4;
      const child = v.getInt32(off, true); off += 4;
      const p = getHandle(parent), c = getHandle(child);
      if (p && c) p.appendChild(c);
    } else {
      break;
    }
  }
}

/** Last uncaught JNative exception reported to the host (not a WASM trap). */
let lastUncaught = null;

function raiseHostError(kind, detail) {
  // Convert host failures into a pending JNative exception when runtime is ready.
  // Do not embed full argument payloads (avoid leaking secrets into messages).
  try {
    if (exports?.jn_raise_host_error && typeof writeWasmString === 'function') {
      const msg = writeWasmString(String(kind) + (detail ? (': ' + detail) : ''));
      exports.jn_raise_host_error(msg);
      return;
    }
  } catch (_) { /* fall through */ }
  console.error('JNative host error:', kind, detail || '');
}

function bindCallback(el, type, cbId) {
  el.dataset.jnCb = String(cbId | 0);
  const handler = (ev) => {
    if (exports?.jnative_callback) {
      try {
        exports.jnative_callback(cbId | 0, retain(ev));
      } catch (e) {
        // Distinguishes: JNativeException (language) vs WebAssembly.RuntimeError (trap).
        console.error('JNative callback failure:', e && e.name, e && e.message);
        if (e && e.name !== 'JNativeException') {
          // Trap: do not assume the runtime remains usable.
          console.error('JNative: WASM trap in callback — runtime may be unsafe to continue');
        }
      }
    }
  };
  el.addEventListener(type, handler);
  callbackRegistry.set(cbId | 0, { el, type, handler });
}

function initDelegation(rootHandle) {
  delegationRoot = getHandle(rootHandle);
  if (!delegationRoot) return;
  const handler = (ev) => {
    let t = ev.target;
    while (t && t !== delegationRoot) {
      const cb = t.dataset?.jnCb;
      if (cb && (ev.type === 'click' || ev.type === 'input' || ev.type === 'submit')) {
        if (exports?.jnative_callback) {
          exports.jnative_callback(cb | 0, retain(ev));
        }
        if (ev.type === 'submit') ev.preventDefault();
        return;
      }
      t = t.parentElement;
    }
  };
  ['click', 'input', 'change', 'submit'].forEach((type) => {
    delegationRoot.addEventListener(type, handler, type === 'submit');
  });
}

const imports = {
  jnative: {
    console_log(ptr, len) { console.log(readUtf8(ptr, len)); },
    console_warn(ptr, len) { console.warn(readUtf8(ptr, len)); },
    console_error(ptr, len) { console.error(readUtf8(ptr, len)); },
    report_uncaught(ptr, len) {
      const msg = (len >>> 0) ? readUtf8(ptr, len) : 'uncaught exception';
      const err = new Error(msg);
      err.name = 'JNativeException';
      lastUncaught = err;
      console.error('JNative uncaught:', err.name, msg);
      // Re-throw so the WASM→JS call (jnative_start / callback) fails observably.
      throw err;
    },
    document_get_element_by_id(ptr, len) {
      try {
        const id = readUtf8(ptr, len);
        const el = document.getElementById(id);
        return el ? retain(el) : 0;
      } catch (e) {
        raiseHostError('DOM.getElementById', e && e.message);
        return 0;
      }
    },
    document_body() {
      try {
        return retain(document.body);
      } catch (e) {
        raiseHostError('DOM.body', e && e.message);
        return 0;
      }
    },
    element_set_text(handle, ptr, len) {
      try {
        const el = getHandle(handle);
        if (el) el.textContent = readUtf8(ptr, len);
      } catch (e) {
        raiseHostError('DOM.setText', e && e.message);
      }
    },
    element_get_text(handle) { return 0; },
    document_create_element(ptr, len) {
      try {
        return retain(document.createElement(readUtf8(ptr, len)));
      } catch (e) {
        raiseHostError('DOM.createElement', e && e.message);
        return 0;
      }
    },
    element_append_child(parent, child) {
      try {
        const p = getHandle(parent), c = getHandle(child);
        if (p && c) p.appendChild(c);
      } catch (e) {
        raiseHostError('DOM.appendChild', e && e.message);
      }
    },
    element_add_event_listener(handle, typePtr, typeLen, callbackId) {
      const el = getHandle(handle);
      if (!el) return;
      bindCallback(el, readUtf8(typePtr, typeLen), callbackId | 0);
    },
    input_value(handle) {
      const el = getHandle(handle);
      return writeWasmString(el && 'value' in el ? String(el.value) : '');
    },
    input_set_value(handle, ptr, len) {
      const el = getHandle(handle);
      if (el && 'value' in el) el.value = readUtf8(ptr, len);
    },
    storage_set(h, kPtr, kLen, vPtr, vLen) {
      const store = getHandle(h) || localStorage;
      store.setItem(readUtf8(kPtr, kLen), readUtf8(vPtr, vLen));
    },
    storage_get(h, kPtr, kLen) { return 0; },
    fetch_get(ptr, len) { return 0; },
    timer_sleep(ms) {},
    invoke_callback(id) { return id | 0; },
    schedule_microtask() {
      queueMicrotask(() => { if (exports?.jnative_flush) exports.jnative_flush(); });
    },
    apply_dom_patches(buf, len) { applyDomPatches(buf, len); },
    query_selector(ptr, len) {
      const sel = readUtf8(ptr, len);
      const el = document.querySelector(sel);
      return el ? retain(el) : 0;
    },
    create_text(ptr, len) {
      return retain(document.createTextNode(readUtf8(ptr, len)));
    },
    register_callback(id) { return id | 0; },
    unregister_callback(id) {
      const rec = callbackRegistry.get(id | 0);
      if (rec) {
        rec.el.removeEventListener(rec.type, rec.handler);
        callbackRegistry.delete(id | 0);
      }
    },
    delegate_event(node, typePtr, typeLen, cbId) {
      const el = getHandle(node);
      if (!el) return;
      bindCallback(el, readUtf8(typePtr, typeLen), cbId | 0);
    },
    init_delegation(root) { initDelegation(root); },
    set_count_label(handle, count) {
      const el = getHandle(handle);
      if (el) el.textContent = 'Count: ' + (count | 0);
    },
    memory_grew() {
      // Invalidate any cached views; u8()/view() always re-bind memory.buffer.
      memory = exports.memory;
    },
    patch_write_int(buf, off, v) {
      view().setInt32(off >>> 0, v | 0, true);
    },
    patch_write_string(buf, off, ptr, len) {
      return writePatchString(off, readUtf8(ptr, len));
    },
    alloc_patch_buffer(size) {
      patchBuf = (patchBuf ?? 0) + 1;
      return 65536;
    },
  }
};

function findExport(substr) {
  if (!exports) return null;
  for (const k of Object.keys(exports)) {
    if (k.includes(substr)) return exports[k];
  }
  return null;
}

async function jnativeStart(wasmSource = './app.wasm', { autoStart = true } = {}) {
  let bytes;
  if (wasmSource instanceof ArrayBuffer || ArrayBuffer.isView(wasmSource)) {
    bytes = wasmSource instanceof ArrayBuffer ? wasmSource : wasmSource.buffer;
  } else {
    const resp = await fetch(wasmSource);
    if (!resp.ok) throw new Error('failed to fetch ' + wasmSource);
    bytes = await resp.arrayBuffer();
  }
  const { instance } = await WebAssembly.instantiate(bytes, imports);
  exports = instance.exports;
  memory = exports.memory;
  lastUncaught = null;
  if (autoStart && typeof exports.jnative_start === 'function') {
    try {
      exports.jnative_start();
    } catch (e) {
      if (e && e.name === 'JNativeException') {
        console.error('JNative uncaught exception:', e.message);
      } else {
        console.error('JNative WASM trap or host error (runtime may be unsafe):', e);
      }
      throw e;
    }
  }
  return {
    exports, memory, handles, release, writeWasmString, writeWasmStringRooted,
    findExport, readWasmString, retainManaged, releaseManaged, getManaged,
    gcStats, gcCollect, lastUncaught: () => lastUncaught,
    /**
     * Call a WASM export and surface pending JNative exceptions to JS.
     * WASM traps still throw WebAssembly.RuntimeError separately.
     */
    callChecked(fn, ...args) {
      const r = fn(...args);
      if (exports?.jn_exception_pending && exports.jn_exception_pending()) {
        if (exports.jn_exception_uncaught) exports.jn_exception_uncaught();
      }
      return r;
    },
    releaseCallback(id) {
      if (exports?.jn_release_callback) exports.jn_release_callback(id | 0);
      imports.jnative.unregister_callback(id | 0);
    },
    /** Release all DOM handles + managed roots known to this session (page tear-down). */
    dispose() {
      for (const id of [...callbackRegistry.keys()]) {
        imports.jnative.unregister_callback(id);
        if (exports?.jn_release_callback) exports.jn_release_callback(id);
      }
      handles.clear();
      if (exports?.jn_gc_collect) exports.jn_gc_collect();
    }
  };
}

export {
  jnativeStart, retain, release, getHandle, applyDomPatches,
  writeWasmString, writeWasmStringRooted, findExport, readWasmString,
  retainManaged, releaseManaged, getManaged, gcStats, gcCollect
};
