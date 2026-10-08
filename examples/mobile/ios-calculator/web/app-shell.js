/**
 * iPhone calculator shell — wires keys to WASM CalcEngine only.
 * Display formatting uses BigInt/i32 halves; no arithmetic reimplementation.
 */
import { jnativeStart } from './app.js';

const SCALE = 1000000n;
const ERR = { 1: 'Error', 2: 'Error' };

function $(id) {
  return document.getElementById(id);
}

function formatScaled(value, entering, hasDot, fracDigits) {
  const neg = value < 0n;
  let v = neg ? -value : value;
  const intPart = v / SCALE;
  let frac = v % SCALE;
  let text;
  if (entering && hasDot) {
    let fracStr = frac.toString().padStart(6, '0').slice(0, fracDigits);
    text = intPart.toString() + '.' + fracStr;
  } else if (frac === 0n) {
    text = intPart.toString();
  } else {
    let fracStr = frac.toString().padStart(6, '0').replace(/0+$/, '');
    text = intPart.toString() + '.' + fracStr;
  }
  if (neg && text !== '0' && text !== '0.') {
    text = '-' + text;
  } else if (neg && (text === '0' || text === '0.')) {
    text = '-' + text;
  }
  return text;
}

async function main() {
  const display = $('display');
  const status = $('status');

  let findExport;
  try {
    ({ findExport } = await jnativeStart('./app.wasm', { autoStart: true }));
  } catch (e) {
    display.textContent = 'Error';
    display.classList.add('error');
    const detail = (e && (e.message || String(e))) || 'unknown';
    status.textContent = 'WASM: ' + detail;
    console.error('WASM load failed', e);
    throw e;
  }

  const api = {
    digit: findExport('CalcEngine_pressDigit_i32'),
    dot: findExport('CalcEngine_pressDot'),
    op: findExport('CalcEngine_pressOp_i32'),
    eq: findExport('CalcEngine_pressEquals'),
    clear: findExport('CalcEngine_pressClear'),
    back: findExport('CalcEngine_pressBackspace'),
    negate: findExport('CalcEngine_pressNegate'),
    getValue: findExport('CalcEngine_getValue'),
    getError: findExport('CalcEngine_getError'),
    getEntering: findExport('CalcEngine_getEntering'),
    getHasDot: findExport('CalcEngine_getHasDot'),
    getFracDigits: findExport('CalcEngine_getFracDigits'),
  };

  for (const [k, fn] of Object.entries(api)) {
    if (typeof fn !== 'function') {
      display.textContent = 'Error';
      display.classList.add('error');
      status.textContent = 'Missing export: ' + k;
      return;
    }
  }

  function readValue() {
    const v = api.getValue();
    if (typeof v === 'bigint') return v;
    if (typeof v === 'number') return BigInt(Math.trunc(v));
    throw new Error('getValue must return BigInt (i64)');
  }

  function refresh() {
    const err = api.getError() | 0;
    if (err !== 0) {
      display.textContent = ERR[err] || 'Error';
      display.classList.add('error');
      display.classList.remove('long');
      status.textContent = err === 1 ? 'Division by zero' : 'Out of range';
      return;
    }
    display.classList.remove('error');
    const text = formatScaled(
      readValue(),
      (api.getEntering() | 0) !== 0,
      (api.getHasDot() | 0) !== 0,
      api.getFracDigits() | 0,
    );
    display.textContent = text;
    if (text.length > 10) display.classList.add('long');
    else display.classList.remove('long');
    status.textContent = '';
  }

  function handle(btn) {
    const act = btn.dataset.act;
    if (act === 'digit') api.digit(Number(btn.dataset.d) | 0);
    else if (act === 'dot') api.dot();
    else if (act === 'op') api.op(Number(btn.dataset.op) | 0);
    else if (act === 'eq') api.eq();
    else if (act === 'clear') api.clear();
    else if (act === 'back') api.back();
    else if (act === 'negate') api.negate();
    refresh();
  }

  document.querySelectorAll('button.key').forEach((btn) => {
    btn.addEventListener('pointerdown', () => btn.classList.add('pressed'));
    btn.addEventListener('pointerup', () => btn.classList.remove('pressed'));
    btn.addEventListener('pointerleave', () => btn.classList.remove('pressed'));
    btn.addEventListener('click', () => handle(btn));
  });

  // Block soft keyboard / focus on non-buttons
  document.addEventListener('focusin', (e) => {
    if (e.target && e.target !== document.body) {
      e.target.blur?.();
    }
  });

  refresh();
  status.textContent = 'WASM ready';
  setTimeout(() => {
    if (status.textContent === 'WASM ready') status.textContent = '';
  }, 1200);

  // Optional simulator demo: ?autodemo=1 or #autodemo runs real button clicks.
  if (/[?&#]autodemo=1\b/.test(location.href) || location.hash === '#autodemo') {
    const steps = ['1', '2', '+', '3', '0', '='];
    let i = 0;
    const tick = () => {
      if (i >= steps.length) return;
      const token = steps[i++];
      let btn = null;
      if (token >= '0' && token <= '9') {
        btn = document.querySelector(`button.key[data-act="digit"][data-d="${token}"]`);
      } else if (token === '+') {
        btn = document.querySelector('button.key[data-act="op"][data-op="1"]');
      } else if (token === '=') {
        btn = document.querySelector('button.key[data-act="eq"]');
      }
      if (btn) btn.click();
      setTimeout(tick, 280);
    };
    setTimeout(tick, 500);
  }
}

main().catch((e) => {
  console.error(e);
  const d = document.getElementById('display');
  if (d) {
    d.textContent = 'Error';
    d.classList.add('error');
  }
});
