// Author: 张晓达
/* JNative wasm32-browser loader (0.27) — generated glue */
const handles = new Map();
let nextHandle = 1;
let memory = null;
let exports = null;
let patchBuf = null;
let delegationRoot = null;
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

function readUtf8(ptr, len) {
  const bytes = new Uint8Array(memory.buffer, ptr >>> 0, len >>> 0);
  return new TextDecoder('utf-8').decode(bytes);
}

function readWasmString(ptr) {
  if (!ptr) return '';
  const view = new DataView(memory.buffer);
  const len = view.getUint32(ptr >>> 0, true);
  return readUtf8(ptr + 4, len);
}

function writePatchString(off, str) {
  const enc = new TextEncoder().encode(str ?? '');
  const view = new DataView(memory.buffer);
  view.setUint32(off >>> 0, enc.length, true);
  new Uint8Array(memory.buffer, off + 4, enc.length).set(enc);
  return off + 4 + enc.length;
}

function applyDomPatches(bufPtr, byteLen) {
  const view = new DataView(memory.buffer);
  let off = bufPtr >>> 0;
  const end = off + (byteLen >>> 0);
  while (off + 4 <= end) {
    const op = view.getInt32(off, true); off += 4;
    if (op === 1) { // SET_TEXT
      const node = view.getInt32(off, true); off += 4;
      const strPtr = view.getInt32(off, true); off += 4;
      const el = getHandle(node);
      if (el) el.textContent = readWasmString(strPtr);
    } else if (op === 2) { // SET_ATTR
      const node = view.getInt32(off, true); off += 4;
      const nPtr = view.getInt32(off, true); off += 4;
      const vPtr = view.getInt32(off, true); off += 4;
      const el = getHandle(node);
      if (el) el.setAttribute(readWasmString(nPtr), readWasmString(vPtr));
    } else if (op === 4) { // SET_PROP
      const node = view.getInt32(off, true); off += 4;
      const nPtr = view.getInt32(off, true); off += 4;
      const vPtr = view.getInt32(off, true); off += 4;
      const el = getHandle(node);
      const name = readWasmString(nPtr);
      const val = readWasmString(vPtr);
      if (el) {
        if (name === 'value') el.value = val;
        else if (name === 'checked') el.checked = val === 'true';
        else el[name] = val;
      }
    } else if (op === 5) { // APPEND
      const parent = view.getInt32(off, true); off += 4;
      const child = view.getInt32(off, true); off += 4;
      const p = getHandle(parent), c = getHandle(child);
      if (p && c) p.appendChild(c);
    } else {
      break;
    }
  }
}

function dispatchEvent(nodeId, type, ev) {
  const cbId = nodeId; // simplified: callback id stored on element dataset
  const el = getHandle(nodeId);
  const id = el?.dataset?.jnCb ? (el.dataset.jnCb | 0) : 0;
  if (exports?.jnative_callback && id) {
    exports.jnative_callback(id | 0, retain(ev));
  }
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
    document_get_element_by_id(ptr, len) {
      const id = readUtf8(ptr, len);
      const el = document.getElementById(id);
      return el ? retain(el) : 0;
    },
    element_set_text(handle, ptr, len) {
      const el = getHandle(handle);
      if (el) el.textContent = readUtf8(ptr, len);
    },
    element_get_text(handle) { return 0; },
    document_create_element(ptr, len) {
      return retain(document.createElement(readUtf8(ptr, len)));
    },
    element_append_child(parent, child) {
      const p = getHandle(parent), c = getHandle(child);
      if (p && c) p.appendChild(c);
    },
    element_add_event_listener(handle, typePtr, typeLen, callbackId) {
      const el = getHandle(handle);
      if (!el) return;
      el.dataset.jnCb = String(callbackId | 0);
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
    register_callback(id) { callbackRegistry.set(id | 0, id | 0); return id | 0; },
    delegate_event(node, typePtr, typeLen, cbId) {
      const el = getHandle(node);
      if (el) el.dataset.jnCb = String(cbId | 0);
    },
    init_delegation(root) { initDelegation(root); },
    set_count_label(handle, count) {
      const el = getHandle(handle);
      if (el) el.textContent = 'Count: ' + (count | 0);
    },
    patch_write_int(buf, off, v) {
      new DataView(memory.buffer).setInt32(off >>> 0, v | 0, true);
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

async function jnativeStart(wasmUrl = './app.wasm') {
  const resp = await fetch(wasmUrl);
  if (!resp.ok) throw new Error('failed to fetch ' + wasmUrl);
  const bytes = await resp.arrayBuffer();
  const { instance } = await WebAssembly.instantiate(bytes, imports);
  exports = instance.exports;
  memory = exports.memory;
  if (typeof exports.jnative_start === 'function') {
    try {
      exports.jnative_start();
    } catch (e) {
      console.error('JNative uncaught:', e);
      throw e;
    }
  }
  return { exports, handles, release };
}

jnativeStart().catch((e) => console.error(e));
export { jnativeStart, retain, release, getHandle, applyDomPatches };
