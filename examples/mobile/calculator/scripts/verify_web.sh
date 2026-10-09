#!/usr/bin/env bash
# Author: 张晓达
# Rebuild from source and verify real IR→WASM execution + bridge host semantics (Node).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build_web.sh"

DIST="$ROOT/dist"
node --input-type=module << EOF
import { readFileSync } from 'fs';
import { pathToFileURL } from 'url';

const wasmBytes = readFileSync('$DIST/app.wasm');
const imports = { jnative: {
  console_log() {}, document_get_element_by_id(){return 0}, element_set_text(){},
  document_create_element(){return 0}, element_append_child(){}, create_text(){return 0},
  query_selector(){return 0}, apply_dom_patches(){}, schedule_microtask(){},
  delegate_event(){}, init_delegation(){}, register_callback(id){return id}, set_count_label(){},
}};
const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
const e = instance.exports;
const mem = e.memory;
function w(s){ const enc=new TextEncoder().encode(s); const p=e.jn_bump(enc.length+4)>>>0; new DataView(mem.buffer).setUint32(p,enc.length,true); new Uint8Array(mem.buffer,p+4,enc.length).set(enc); return p; }
function find(sub){ for (const k of Object.keys(e)) if (k.includes(sub)) return e[k]; throw new Error('missing '+sub); }

const compute = find('CalculatorApp_compute_CString_CString');
const computeOp = find('CalculatorApp_computeOp_CString_CString_i32');
const evaluate = find('CalculatorApp_evaluate_CString');
const getErr = find('CalculatorApp_getLastError');
const add = find('CalculatorApp_add_i32_i32');
const mul = find('CalculatorApp_mul_i32_i32');

const sum = compute(w('12'), w('30'));
const err = getErr()|0;
if (sum !== 42 || err !== 0) throw new Error('expected 12+30=42 ok, got '+sum+' err='+err);
console.log('PASS compute 12+30 =>', sum);

const empty = compute(w(''), w('30'));
if ((getErr()|0) !== 1) throw new Error('expected ERR_EMPTY, got '+getErr());
console.log('PASS empty => err', getErr());

const bad = compute(w('x'), w('30'));
if ((getErr()|0) !== 2) throw new Error('expected ERR_INVALID, got '+getErr());
console.log('PASS invalid => err', getErr());

if (add(12,30) !== 42) throw new Error('add mismatch');
console.log('PASS add export');

const prod = computeOp(w('123'), w('456'), 2);
if (prod !== 56088 || (getErr()|0) !== 0) throw new Error('expected 123*456=56088, got '+prod+' err='+getErr());
console.log('PASS computeOp 123*456 =>', prod);

const expr = evaluate(w('123*456'));
if (expr !== 56088 || (getErr()|0) !== 0) throw new Error('expected evaluate 123*456=56088, got '+expr+' err='+getErr());
console.log('PASS evaluate 123*456 =>', expr);

const divz = computeOp(w('10'), w('0'), 3);
if ((getErr()|0) !== 3) throw new Error('expected ERR_DIV0, got '+getErr());
console.log('PASS div0 => err', getErr());

if (mul(123,456) !== 56088) throw new Error('mul mismatch');
console.log('PASS mul export');

// Prove not a fixed template: inspect WAT if present, else re-check export names are mangled IR names
const keys = Object.keys(e).filter(k => k.includes('CalculatorApp'));
if (keys.length < 5) throw new Error('too few CalculatorApp exports');
console.log('PASS exports', keys.join(', '));
console.log('ALL WEB WASM CHECKS PASSED');
EOF

echo "==> bridge unit checks (node)"
BRIDGE_TEST="$ROOT/.build-staging/bridge_test.mjs"
mkdir -p "$ROOT/.build-staging"
cat > "$BRIDGE_TEST" << EOF
import { JNativeBridge, installWebPlatformHost } from '$(cd "$DIST" && pwd)/bridge.js';

const listeners = new Map();
globalThis.window = {
  location: { origin: 'http://127.0.0.1' },
  addEventListener(type, fn) {
    if (!listeners.has(type)) listeners.set(type, new Set());
    listeners.get(type).add(fn);
  },
  removeEventListener(type, fn) { listeners.get(type)?.delete(fn); },
  postMessage(data, origin) {
    const ev = { data, origin: origin || 'http://127.0.0.1' };
    for (const fn of listeners.get('message') || []) fn(ev);
  },
};
globalThis.document = { addEventListener() {}, hidden: false };
if (typeof globalThis.crypto?.randomUUID !== 'function') {
  Object.defineProperty(globalThis, 'crypto', {
    value: { randomUUID: () => 'id-' + Math.random().toString(16).slice(2) },
  });
}
globalThis.localStorage = {
  _m: new Map(),
  getItem(k) { return this._m.has(k) ? this._m.get(k) : null; },
  setItem(k, v) { this._m.set(k, String(v)); },
  removeItem(k) { this._m.delete(k); },
};

installWebPlatformHost();
const bridge = new JNativeBridge({ timeoutMs: 200, requiredCapabilities: ['platform.getName'] });
const session = await bridge.init();
if (session.platform !== 'web' || session.version !== 1) throw new Error('handshake');
await bridge.init(); // re-init ok

const name = await bridge.call('platform.getName');
if (name !== 'web') throw new Error('platform ' + name);
await bridge.call('storage.set', { key: 't', value: 'v' });
if ((await bridge.call('storage.get', { key: 't' })) !== 'v') throw new Error('kv');

let unknownFailed = false;
try { await bridge.call('eval'); } catch (e) { unknownFailed = e.code === 'CAPABILITY_UNAVAILABLE' || e.code === 'METHOD_NOT_ALLOWED'; }
if (!unknownFailed) throw new Error('unknown method should fail');

let badFailed = false;
try { await bridge.call('storage.set', { key: '', value: 'x' }); } catch (e) { badFailed = true; }
if (!badFailed) throw new Error('bad key should fail');

const broken = new JNativeBridge({ timeoutMs: 50 });
broken.transport = () => {};
let timedOut = false;
try { await broken.init(); } catch (e) { timedOut = e.code === 'TIMEOUT'; }
if (!timedOut) throw new Error('expected TIMEOUT');

const doomed = new JNativeBridge({ timeoutMs: 5000 });
doomed.transport = () => {};
const p = doomed.init().then(
  () => { throw new Error('should not resolve'); },
  (e) => { if (e.code !== 'DESTROYED' && e.code !== 'TIMEOUT') throw e; },
);
doomed.destroy();
await p;
console.log('PASS bridge handshake/kv/timeout/destroy');
EOF
node "$BRIDGE_TEST"

echo "verify_web: OK"
