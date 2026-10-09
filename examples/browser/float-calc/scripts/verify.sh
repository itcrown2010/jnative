#!/usr/bin/env bash
# Rebuild from source and execute float/double checks in Node's WASM engine.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh"
DIST="$ROOT/dist"

echo "==> tool versions"
node -v
wat2wasm --version 2>/dev/null || true

node --input-type=module << EOF
import { readFileSync } from 'fs';

const wasmBytes = readFileSync('$DIST/app.wasm');
const imports = { jnative: {
  console_log() {}, document_get_element_by_id(){return 0}, element_set_text(){},
  document_create_element(){return 0}, element_append_child(){}, create_text(){return 0},
  query_selector(){return 0}, apply_dom_patches(){}, schedule_microtask(){},
  delegate_event(){}, init_delegation(){}, register_callback(id){return id},
  unregister_callback(){}, set_count_label(){}, document_body(){return 0},
  input_value(){return 0}, input_set_value(){}, memory_grew(){},
}};
const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
const e = instance.exports;
function find(sub){
  for (const k of Object.keys(e)) if (k.includes(sub)) return e[k];
  throw new Error('missing '+sub+' in '+Object.keys(e).filter(k=>k.includes('FloatCalc')).join(','));
}

const add = find('FloatCalc_add_f64_f64');
const sub = find('FloatCalc_sub_f64_f64');
const mul = find('FloatCalc_mul_f64_f64');
const div = find('FloatCalc_div_f64_f64');
const compute = find('FloatCalc_compute_f64_f64_i32');
const addf = find('FloatCalc_addf_f32_f32');
const mulf = find('FloatCalc_mulf_f32_f32');
const negate = find('FloatCalc_negate_f64');
const cmpEq = find('FloatCalc_cmpEq_f64_f64');
const cmpLt = find('FloatCalc_cmpLt_f64_f64');
const toInt = find('FloatCalc_toInt_f64');
const toLong = find('FloatCalc_toLong_f64');
const fromInt = find('FloatCalc_fromInt_i32');
const promoteDemote = find('FloatCalc_promoteDemote_f32');
const arrayRoundTrip = find('FloatCalc_arrayRoundTrip_f64_f64');
const floatPrecision = find('FloatCalc_floatPrecision');
const selectPos = find('FloatCalc_selectPos_f64_f64');

function approx(a,b,eps=1e-9){ if (Object.is(a,b)) return true; if (Number.isNaN(a)&&Number.isNaN(b)) return true; return Math.abs(a-b)<=eps; }
function assert(cond, msg){ if (!cond) throw new Error(msg); }

assert(approx(add(123.5, 456.25), 579.75), 'add');
assert(approx(sub(10, 3.5), 6.5), 'sub');
assert(approx(mul(2.5, 4), 10), 'mul');
assert(approx(div(9, 4), 2.25), 'div');
assert(approx(compute(123.5, 456.25, 0), 579.75), 'compute+');
assert(approx(compute(10, 2, 2), 20), 'compute*');

assert(approx(addf(1.5, 2.25), 3.75), 'addf');
assert(approx(mulf(2, 3.5), 7), 'mulf');

const nz = negate(0);
assert(Object.is(nz, -0) || (nz === 0 && 1/nz < 0), 'negate +0 -> -0, got '+nz+' 1/x='+(1/nz));

assert(cmpEq(1, 1) === 1, 'eq');
assert(cmpEq(NaN, NaN) === 0, 'NaN != NaN');
assert(cmpLt(1, 2) === 1, 'lt');
assert(cmpLt(NaN, 1) === 0, 'NaN lt');

assert(toInt(3.9) === 3, 'trunc toward 0');
assert(toInt(-3.9) === -3, 'trunc neg');
assert(toInt(NaN) === 0, 'NaN->0');
assert(toInt(1e100) === 2147483647, 'sat +inf range');
assert(toInt(-1e100) === -2147483648, 'sat -inf range');
assert(toLong(42.7) === 42n || Number(toLong(42.7)) === 42, 'toLong');
assert(approx(fromInt(7), 7), 'fromInt');
assert(approx(promoteDemote(1.5), 1.5), 'promoteDemote');

assert(approx(arrayRoundTrip(1.25, 2.5), 3.75), 'array');
assert(floatPrecision() === 16777216, 'float precision 16777216f+1f');
assert(approx(selectPos(-1, 9), 9), 'ternary');
assert(approx(selectPos(4, 9), 4), 'ternary2');

assert(div(1, 0) === Infinity, '+inf');
assert(div(-1, 0) === -Infinity, '-inf');
assert(Number.isNaN(div(0, 0)), '0/0 nan');

console.log('PASS float/double wasm execution checks');
console.log('exports', Object.keys(e).filter(k => k.includes('FloatCalc')).join(', '));
EOF

echo "==> default-opt path already exercised above (rebuild+execute)"
echo "note: -O0 may still hit unrelated unsupported IR (e.g. InvokeCallable); float ops themselves are covered"

echo "verify float-calc: OK"
