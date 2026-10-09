#!/usr/bin/env bash
# Build order-list and exercise add/remove/total via a minimal DOM mock in Node.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh"
DIST="$ROOT/dist"

node --input-type=module << EOF
import { pathToFileURL } from 'url';
const dist = '$DIST';
const elements = new Map();
function el(id, tag = 'div') {
  const o = {
    id, tagName: tag.toUpperCase(), value: '', textContent: '',
    dataset: {}, children: [],
    addEventListener(type, handler) {
      this._handlers = this._handlers || {};
      this._handlers[type] = handler;
    },
    removeEventListener() {},
    appendChild(c) { this.children.push(c); },
    setAttribute() {},
    click() { this._handlers?.click?.({ type: 'click', target: this }); },
  };
  if (id === 'itemName') o.value = '龙井茶叶';
  if (id === 'itemQty') o.value = '2';
  if (id === 'itemPrice') o.value = '12.5';
  elements.set(id, o);
  return o;
}
['addBtn','clearBtn','itemName','itemQty','itemPrice','orderList','totalQty','totalPrice'].forEach((id) => el(id));
globalThis.document = {
  getElementById(id) { return elements.get(id) || null; },
  createElement(tag) { return el('anon', tag); },
  createTextNode(t) { return { textContent: t }; },
  querySelector() { return null; },
  body: el('body'),
};
const mod = await import(pathToFileURL(dist + '/app.js').href);
await mod.jnativeStart(pathToFileURL(dist + '/app.wasm').href, { autoStart: true });
elements.get('addBtn').click();
const list1 = elements.get('orderList').textContent;
if (!list1.includes('龙井') && !list1.includes('茶叶')) throw new Error('chinese name missing: ' + list1);
if (elements.get('totalQty').textContent !== '2') throw new Error('qty ' + elements.get('totalQty').textContent);
if (!String(elements.get('totalPrice').textContent).includes('25')) {
  throw new Error('price ' + elements.get('totalPrice').textContent);
}
elements.get('itemName').value = '碧螺春';
elements.get('itemQty').value = '3';
elements.get('itemPrice').value = '10';
elements.get('addBtn').click();
if (elements.get('totalQty').textContent !== '5') throw new Error('qty2');
elements.get('clearBtn').click();
if (elements.get('totalQty').textContent !== '2') throw new Error('qty3');
console.log('PASS order-list wasm logic (Node DOM mock)');
console.log('list=', list1.replace(/\\n/g, ' | '));
EOF

echo "verify order-list: OK"
