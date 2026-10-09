/**
 * UI shell: wires HTML controls to WASM CalculatorApp + platform bridge.
 * Calculation is never reimplemented here.
 */
import { jnativeStart } from './app.js';
import { JNativeBridge, installWebPlatformHost } from './bridge.js';

const KEY_LEFT = 'calc.lastLeft';
const KEY_RIGHT = 'calc.lastRight';
const KEY_OP = 'calc.lastOp';
const KEY_EXPR = 'calc.lastExpr';

const errMessages = {
  1: '输入为空',
  2: '不是合法整数或表达式（请用两个整数框，或写 123*456）',
  3: '除数不能为 0',
  4: '整数溢出',
  5: '未知运算符',
};

function $(id) {
  return document.getElementById(id);
}

function setStatus(text, isError) {
  const el = $('status');
  el.textContent = text || '';
  el.className = isError ? 'status error' : 'status';
}

window.addEventListener('error', (e) => {
  console.error('JS error', e.error || e.message);
  setStatus('JS error: ' + (e.message || 'unknown'), true);
});
window.addEventListener('unhandledrejection', (e) => {
  console.error('Unhandled rejection', e.reason);
  setStatus('Promise error: ' + (e.reason?.message || e.reason || 'unknown'), true);
});

async function main() {
  const isNativeShell =
    !!(window.JNativeHost || window.webkit?.messageHandlers?.jnativeBridge);
  if (!isNativeShell) {
    installWebPlatformHost();
  }

  const bridge = new JNativeBridge({
    requiredCapabilities: ['platform.getName', 'storage.get', 'storage.set'],
  });
  window.__jnativeBridge = bridge;

  let writeWasmString;
  let findExport;
  try {
    ({ writeWasmString, findExport } = await jnativeStart('./app.wasm', {
      autoStart: true,
    }));
  } catch (e) {
    setStatus('WASM load failed: ' + e.message, true);
    throw e;
  }

  const computeOp = findExport('CalculatorApp_computeOp_CString_CString_i32');
  const evaluate = findExport('CalculatorApp_evaluate_CString');
  const compute = findExport('CalculatorApp_compute_CString_CString');
  const getLastError = findExport('CalculatorApp_getLastError');
  if (typeof getLastError !== 'function') {
    setStatus('WASM exports missing (rebuild from source)', true);
    return;
  }
  if (typeof computeOp !== 'function' && typeof evaluate !== 'function' && typeof compute !== 'function') {
    setStatus('WASM compute exports missing (rebuild from source)', true);
    return;
  }

  let session;
  try {
    session = await bridge.init();
  } catch (e) {
    setStatus('Bridge init failed: ' + (e.code || '') + ' ' + e.message, true);
    throw e;
  }
  $('platform').textContent =
    'platform: ' + session.platform + ' (bridge v' + session.version + ')';

  bridge.onLifecycle((name) => {
    $('lifecycle').textContent = 'lifecycle: ' + name;
  });

  let currentOp = 0;
  const opButtons = Array.from(document.querySelectorAll('.ops [data-op]'));
  function setOp(op) {
    currentOp = Number(op) || 0;
    opButtons.forEach((b) => {
      b.classList.toggle('active', Number(b.getAttribute('data-op')) === currentOp);
    });
  }
  opButtons.forEach((b) => {
    b.addEventListener('click', () => setOp(b.getAttribute('data-op')));
  });

  try {
    const left = await bridge.call('storage.get', { key: KEY_LEFT });
    const right = await bridge.call('storage.get', { key: KEY_RIGHT });
    const op = await bridge.call('storage.get', { key: KEY_OP });
    const expr = await bridge.call('storage.get', { key: KEY_EXPR });
    if (typeof left === 'string') $('left').value = left;
    if (typeof right === 'string') $('right').value = right;
    if (typeof expr === 'string') $('expr').value = expr;
    if (typeof op === 'string' && op.length) setOp(op);
  } catch (e) {
    console.warn('restore failed', e.code || e.message);
  }

  $('go').addEventListener('click', async () => {
    try {
      const expr = $('expr').value.trim();
      const left = $('left').value.trim();
      const right = $('right').value.trim();
      let result = 0;
      let code = 0;

      if (expr.length > 0) {
        if (typeof evaluate !== 'function') {
          setStatus('当前 WASM 无表达式接口，请重新 build_web', true);
          return;
        }
        result = evaluate(writeWasmString(expr));
        code = getLastError() | 0;
      } else if (typeof computeOp === 'function') {
        result = computeOp(writeWasmString(left), writeWasmString(right), currentOp);
        code = getLastError() | 0;
      } else {
        /* fallback add-only build */
        result = compute(writeWasmString(left), writeWasmString(right));
        code = getLastError() | 0;
        if (code === 0 && currentOp !== 0) {
          setStatus('当前包仅支持加法，请重新 scripts/build_web.sh', true);
          return;
        }
      }

      if (code !== 0) {
        $('result').textContent = '—';
        setStatus(errMessages[code] || ('错误码 ' + code), true);
        return;
      }
      $('result').textContent = String(result);
      setStatus('OK', false);
      await bridge.call('storage.set', { key: KEY_LEFT, value: left });
      await bridge.call('storage.set', { key: KEY_RIGHT, value: right });
      await bridge.call('storage.set', { key: KEY_OP, value: String(currentOp) });
      await bridge.call('storage.set', { key: KEY_EXPR, value: expr });
    } catch (e) {
      setStatus('Bridge/WASM error: ' + (e.code || '') + ' ' + (e.message || e), true);
    }
  });

  window.addEventListener('pagehide', () => bridge.destroy());
  window.addEventListener('beforeunload', () => bridge.destroy());
}

main().catch((e) => {
  console.error(e);
  setStatus(String(e), true);
});
