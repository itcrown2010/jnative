/* JNative wasm32-browser loader (0.26) — generated glue */
const handles = new Map();
let nextHandle = 1;
let memory = null;
let exports = null;

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

function writeUtf8ToHeap(str) {
  const enc = new TextEncoder().encode(str ?? '');
  // return via temporary import helper stored by runtime when needed
  return enc;
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
    element_get_text(handle) {
      const el = getHandle(handle);
      return 0; /* string return via heap — extended in fuller builds */
    },
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
      const type = readUtf8(typePtr, typeLen);
      el.addEventListener(type, (ev) => {
        if (exports && exports.jnative_callback) {
          exports.jnative_callback(callbackId | 0, retain(ev));
        }
      });
    },
    storage_set(h, kPtr, kLen, vPtr, vLen) {
      const store = getHandle(h) || localStorage;
      store.setItem(readUtf8(kPtr, kLen), readUtf8(vPtr, vLen));
    },
    storage_get(h, kPtr, kLen) { return 0; },
    fetch_get(ptr, len) { return 0; },
    timer_sleep(ms) { /* sync sleep not used; Async.sleep uses Promise path */ },
    invoke_callback(id) { return id | 0; },
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
export { jnativeStart, retain, release, getHandle };
