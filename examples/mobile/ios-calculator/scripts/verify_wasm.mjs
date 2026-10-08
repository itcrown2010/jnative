/**
 * Drive CalcEngine WASM exports — no mirrored calculator logic.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const wasmPath = path.join(root, 'dist', 'app.wasm');

const stubNames = [
  'console_log', 'document_get_element_by_id', 'element_set_text',
  'document_create_element', 'element_append_child', 'create_text',
  'query_selector', 'apply_dom_patches', 'schedule_microtask',
  'delegate_event', 'init_delegation', 'register_callback', 'set_count_label',
];

function findExport(exports, substr) {
  for (const k of Object.keys(exports)) {
    if (k.includes(substr)) return exports[k];
  }
  return null;
}

function formatScaled(v, entering, hasDot, fracDigits) {
  const SCALE = 1000000n;
  const neg = v < 0n;
  let x = neg ? -v : v;
  const intPart = x / SCALE;
  const frac = x % SCALE;
  let text;
  if (entering && hasDot) {
    text = intPart.toString() + '.' + frac.toString().padStart(6, '0').slice(0, fracDigits);
  } else if (frac === 0n) {
    text = intPart.toString();
  } else {
    text = intPart.toString() + '.' + frac.toString().padStart(6, '0').replace(/0+$/, '');
  }
  return neg ? '-' + text : text;
}

async function load() {
  const wasm = fs.readFileSync(wasmPath);
  const jnative = Object.fromEntries(stubNames.map((n) => [n, () => 0]));
  const { instance } = await WebAssembly.instantiate(wasm, { jnative });
  const e = instance.exports;
  const api = {
    digit: findExport(e, 'CalcEngine_pressDigit'),
    dot: findExport(e, 'CalcEngine_pressDot'),
    op: findExport(e, 'CalcEngine_pressOp'),
    eq: findExport(e, 'CalcEngine_pressEquals'),
    clear: findExport(e, 'CalcEngine_pressClear'),
    back: findExport(e, 'CalcEngine_pressBackspace'),
    negate: findExport(e, 'CalcEngine_pressNegate'),
    getValue: findExport(e, 'CalcEngine_getValue'),
    getError: findExport(e, 'CalcEngine_getError'),
    entering: findExport(e, 'CalcEngine_getEntering'),
    hasDot: findExport(e, 'CalcEngine_getHasDot'),
    frac: findExport(e, 'CalcEngine_getFracDigits'),
  };
  for (const [k, v] of Object.entries(api)) {
    if (typeof v !== 'function') throw new Error('missing export ' + k);
  }
  return api;
}

function value(api) {
  const v = api.getValue();
  if (typeof v === 'bigint') return v;
  if (typeof v === 'number') return BigInt(Math.trunc(v));
  throw new Error('getValue must return BigInt');
}

function display(api) {
  const err = api.getError() | 0;
  if (err) return { err, text: 'Error' };
  const text = formatScaled(
    value(api),
    (api.entering() | 0) !== 0,
    (api.hasDot() | 0) !== 0,
    api.frac() | 0,
  );
  return { err: 0, text };
}

function tap(api, seq) {
  for (const s of seq) {
    if (s === '.') api.dot();
    else if (s === '+') api.op(1);
    else if (s === '-') api.op(2);
    else if (s === '*') api.op(3);
    else if (s === '/') api.op(4);
    else if (s === '=') api.eq();
    else if (s === 'C') api.clear();
    else if (s === 'B') api.back();
    else if (s === 'N') api.negate();
    else if (s >= '0' && s <= '9') api.digit(Number(s));
    else throw new Error('bad token ' + s);
  }
}

function expect(api, seq, want, label) {
  api.clear();
  tap(api, seq);
  const d = display(api);
  if (want === 'Error') {
    if (d.err === 0) throw new Error(`${label}: expected Error, got ${d.text}`);
  } else if (d.text !== want) {
    throw new Error(`${label}: expected ${want}, got ${d.text} (err=${d.err})`);
  }
  console.log('  OK', label, '→', d.text);
}

const api = await load();
console.log('==> CalcEngine WASM verification');

expect(api, ['1', '2', '+', '3', '0', '='], '42', '12+30');
expect(api, ['7', '-', '1', '0', '='], '-3', '7-10');
expect(api, ['6', '*', '8', '='], '48', '6*8');
expect(api, ['9', '/', '4', '='], '2.25', '9/4');
expect(api, ['0', '.', '1', '+', '0', '.', '2', '='], '0.3', '0.1+0.2');
expect(api, ['1', '/', '0', '='], 'Error', '1/0');

// Immediate eval: 2+3*4 = 20
expect(api, ['2', '+', '3', '*', '4', '='], '20', '2+3*4 immediate');

// Op replace
expect(api, ['5', '+', '-', '2', '='], '3', 'op replace +→-');

// Equals without RHS keeps result
api.clear();
tap(api, ['8', '+', '=']);
if (display(api).text !== '8') throw new Error('equals without RHS');
console.log('  OK equals without RHS → 8');

// Repeat equals does not re-apply
api.clear();
tap(api, ['5', '+', '2', '=', '=']);
if (display(api).text !== '7') throw new Error('repeat equals');
console.log('  OK repeat equals → 7');

// After result, digit starts new calc
api.clear();
tap(api, ['1', '+', '1', '=', '9']);
if (display(api).text !== '9') throw new Error('new calc after result');
console.log('  OK digit after result → 9');

// Dot leading
expect(api, ['.', '5', '=',], '0.5', 'leading dot');

// Backspace
api.clear();
tap(api, ['1', '2', '3', 'B']);
if (display(api).text !== '12') throw new Error('backspace');
console.log('  OK backspace → 12');

// Negate
expect(api, ['5', 'N', '+', '2', '='], '-3', 'negate');

// Six decimal places + rounding (1/3 ≈ 0.333333)
expect(api, ['1', '/', '3', '='], '0.333333', '1/3 six places');

// Rounding half away: 1/6 = 0.1666666... → 0.166667
expect(api, ['1', '/', '6', '='], '0.166667', '1/6 round');

// Overflow: 9999999999 * 9999999999
api.clear();
tap(api, ['9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '*', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '=']);
if ((api.getError() | 0) === 0) throw new Error('expected overflow');
console.log('  OK overflow → Error');

// Error recovery via clear
api.clear();
if ((api.getError() | 0) !== 0 || display(api).text !== '0') throw new Error('clear recovery');
console.log('  OK clear recovery');

// Error recovery via digit
api.clear();
tap(api, ['1', '/', '0', '=', '5']);
if (display(api).text !== '5' || (api.getError() | 0) !== 0) throw new Error('digit recovery');
console.log('  OK digit recovery → 5');

console.log('ALL_WASM_TESTS_PASSED');
