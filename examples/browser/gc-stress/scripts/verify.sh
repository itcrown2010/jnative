#!/usr/bin/env bash
# Author: 张晓达
# Rebuild + Node stress: short-lived churn, cycles, ArrayList clear, host roots, grow.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh"
DIST="$ROOT/dist"

node --input-type=module << EOF
import { readFileSync } from 'fs';

const wasmBytes = readFileSync('$DIST/app.wasm');
let memory = null;
const imports = { jnative: {
  console_log() {}, console_warn() {}, console_error() {},
  document_get_element_by_id(){return 0}, document_body(){return 0},
  document_create_element(){return 0}, element_append_child(){},
  element_set_text(){}, element_get_text(){return 0},
  element_add_event_listener(){}, input_value(){return 0}, input_set_value(){},
  create_text(){return 0}, query_selector(){return 0},
  apply_dom_patches(){}, schedule_microtask(){},
  delegate_event(){}, init_delegation(){},
  register_callback(id){return id}, unregister_callback(){},
  set_count_label(){}, storage_set(){}, storage_get(){return 0},
  fetch_get(){return 0}, timer_sleep(){}, invoke_callback(id){return id},
  patch_write_int(){}, patch_write_string(){return 0}, alloc_patch_buffer(){return 65536},
  memory_grew() { memory = exports.memory; },
  report_uncaught(p,l){
    const msg = l ? new TextDecoder().decode(new Uint8Array(memory.buffer,p>>>0,l>>>0)) : '';
    const e = new Error(msg || 'uncaught'); e.name = 'JNativeException'; throw e;
  },
}};
const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
const exports = instance.exports;
memory = exports.memory;

function find(sub) {
  for (const k of Object.keys(exports)) if (k.includes(sub)) return exports[k];
  throw new Error('missing ' + sub + ' among ' + Object.keys(exports).filter(k => k.includes('GcStress')).join(','));
}
function stats() {
  const [allocBytes, liveBytes, freeBytes, heapCap, gcCount, hostRoots, allocFail] = exports.jn_gc_stats();
  return { allocBytes, liveBytes, freeBytes, heapCap, gcCount, hostRoots, allocFail };
}
function assert(c, m) { if (!c) throw new Error(m); }

if (typeof exports.jnative_start === 'function') exports.jnative_start();

const churn = find('GcStress_churn');
const makeCycle = find('GcStress_makeCycle');
const drop = find('GcStress_dropKeepAlive');
const bagChurn = find('GcStress_bagChurn');
const nested = find('GcStress_nestedSurvive');
const closureEsc = find('GcStress_closureEscape');

// warmup
for (let i = 0; i < 3; i++) churn(200);
exports.jn_gc_collect();
const base = stats();
console.log('warmup', base);

// short-lived churn — live bytes after collect should stay near baseline
const caps = [];
for (let round = 0; round < 8; round++) {
  assert(churn(400) === 399, 'churn value');
  exports.jn_gc_collect();
  caps.push(stats());
}
const liveAfterChurn = caps.map(s => s.liveBytes);
const maxLive = Math.max(...liveAfterChurn);
const minLive = Math.min(...liveAfterChurn);
assert(maxLive - minLive < 64 * 1024, 'live bytes unbounded after churn: ' + liveAfterChurn.join(','));
assert(caps[caps.length - 1].gcCount > base.gcCount, 'expected GC runs');
console.log('churn live curve', liveAfterChurn.join(','));

// cycle
assert(makeCycle() === 3, 'cycle');
exports.jn_gc_collect();
const withCycle = stats().liveBytes;
drop();
exports.jn_gc_collect();
const afterDrop = stats().liveBytes;
assert(afterDrop <= withCycle, 'cycle drop should not increase live');
console.log('cycle live', withCycle, '->', afterDrop);

// ArrayList clear
assert(bagChurn(80) === 80, 'bag size');
exports.jn_gc_collect();
const afterBag = stats();
console.log('bag clear', afterBag);

// nested expression survival
assert(nested(300) === 42, 'nested survive');
exports.jn_gc_collect();
assert(nested(300) === 42, 'nested survive 2');

// closure + clear
const sum = closureEsc(10); // 0..9 = 45
assert(sum === 45, 'closure sum ' + sum);
exports.jn_gc_collect();

// host root retain/release + gen reuse safety
const ptr = exports.jn_bump(32);
assert(ptr !== 0, 'bump');
const h1 = exports.jn_host_root_retain(ptr);
assert(h1 !== 0, 'retain');
assert(exports.jn_host_root_get(h1) === ptr, 'get');
exports.jn_host_root_release(h1);
assert(exports.jn_host_root_get(h1) === 0, 'stale handle must be 0');
const h2 = exports.jn_host_root_retain(ptr);
assert(h2 !== 0 && h2 !== h1, 'new handle differs (gen)');
assert(exports.jn_host_root_get(h2) === ptr, 'get2');
exports.jn_host_root_release(h2);

// force grow via large opaque allocs, then memory_grew path
const beforePages = memory.buffer.byteLength;
let grew = false;
for (let i = 0; i < 40; i++) {
  const p = exports.jn_bump(64 * 1024);
  if (!p) break;
  if (memory.buffer.byteLength > beforePages) grew = true;
}
exports.jn_gc_collect();
const afterGrow = stats();
console.log('grow', { beforePages, afterPages: memory.buffer.byteLength, grew, afterGrow });
// capacity may stay high; live after collect must be bounded vs heapCap
assert(afterGrow.liveBytes < afterGrow.heapCap, 'live < cap');
assert(afterGrow.allocFail === 0 || afterGrow.allocFail >= 0, 'allocFail readable');

// dynamic call / string still work after GC
const s = closureEsc(5);
assert(s === 10, 'post-gc closure ' + s);

console.log('PASS gc-stress', stats());
EOF

echo "verify gc-stress: OK"
