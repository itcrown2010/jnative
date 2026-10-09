package io.jnative.backend.wasm;

/**
 * Generated JS loader — WASM instantiate, handle table, DOM glue.
 * No Emscripten. CSP-safe (no eval).
 * After {@code memory.grow}, always re-read {@code memory.buffer} (views are refreshed per call).
 */
public final class JsGlueGenerator {
    private JsGlueGenerator() {
    }

    public static String generate() {
        return """
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

                function writeWasmString(str) {
                  if (!exports?.jn_bump || !memory) throw new Error('wasm not ready');
                  const enc = new TextEncoder().encode(str ?? '');
                  const ptr = exports.jn_bump(enc.length + 4) >>> 0;
                  view().setUint32(ptr, enc.length, true);
                  u8(ptr + 4, enc.length).set(enc);
                  return ptr;
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

                function bindCallback(el, type, cbId) {
                  el.dataset.jnCb = String(cbId | 0);
                  const handler = (ev) => {
                    if (exports?.jnative_callback) {
                      try {
                        exports.jnative_callback(cbId | 0, retain(ev));
                      } catch (e) {
                        console.error('JNative callback trap/error:', e);
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
                    document_get_element_by_id(ptr, len) {
                      const id = readUtf8(ptr, len);
                      const el = document.getElementById(id);
                      return el ? retain(el) : 0;
                    },
                    document_body() {
                      return retain(document.body);
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
                      // TypedArray views must not be cached across grow — we re-read buffer each call.
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

                async function jnativeStart(wasmUrl = './app.wasm', { autoStart = true } = {}) {
                  const resp = await fetch(wasmUrl);
                  if (!resp.ok) throw new Error('failed to fetch ' + wasmUrl);
                  const bytes = await resp.arrayBuffer();
                  const { instance } = await WebAssembly.instantiate(bytes, imports);
                  exports = instance.exports;
                  memory = exports.memory;
                  if (autoStart && typeof exports.jnative_start === 'function') {
                    try {
                      exports.jnative_start();
                    } catch (e) {
                      console.error('JNative uncaught (WASM trap or JS):', e);
                      throw e;
                    }
                  }
                  return {
                    exports, memory, handles, release, writeWasmString, findExport, readWasmString,
                    releaseCallback(id) {
                      if (exports?.jn_release_callback) exports.jn_release_callback(id | 0);
                      imports.jnative.unregister_callback(id | 0);
                    }
                  };
                }

                export { jnativeStart, retain, release, getHandle, applyDomPatches, writeWasmString, findExport, readWasmString };
                """;
    }
}
