// Author: 张晓达
/**
 * Touch shell adapter: DOM + localStorage + lifecycle hooks.
 * Business rules remain in TaskCore (WASM). Same exports as desktop/web guide.
 */
import { jnativeStart } from './app.js';

const statusEl = document.getElementById('status');
const lifeEl = document.getElementById('lifecycle');
const listEl = document.getElementById('list');
const form = document.getElementById('form');
const titleInput = document.getElementById('title');
const STORAGE_KEY = 'jnative.guide-tasks.mobile.v1';

let api = null;
let writeWasmString = null;
let alive = true;
let bgToken = 0;

function setStatus(msg, isError) {
  statusEl.textContent = msg;
  statusEl.classList.toggle('err', !!isError);
}

function setLife(msg) {
  if (lifeEl) lifeEl.textContent = msg || '';
}

function findApi(findExport) {
  const need = (sub) => {
    const fn = findExport(sub);
    if (typeof fn !== 'function') throw new Error('missing export ' + sub);
    return fn;
  };
  return {
    getCount: need('TaskCore_getCount'),
    getLastError: need('TaskCore_getLastError'),
    clearDraft: need('TaskCore_clearDraft'),
    appendDraftChar: need('TaskCore_appendDraftChar_i32'),
    addFromDraft: need('TaskCore_addFromDraft'),
    getTitleLen: need('TaskCore_getTitleLen_i32'),
    getTitleChar: need('TaskCore_getTitleChar_i32_i32'),
    getDone: need('TaskCore_getDone_i32'),
    toggleDone: need('TaskCore_toggleDone_i32'),
    removeAt: need('TaskCore_removeAt_i32'),
    prepareSave: need('TaskCore_prepareSave'),
    getSaveLen: need('TaskCore_getSaveLen'),
    getSaveChar: need('TaskCore_getSaveChar_i32'),
    loadSave: need('TaskCore_loadSave_CString'),
  };
}

function titleAt(index) {
  const n = api.getTitleLen(index) | 0;
  let s = '';
  for (let i = 0; i < n; i++) s += String.fromCharCode(api.getTitleChar(index, i) | 0);
  return s;
}

function addTask(title) {
  api.clearDraft();
  for (let i = 0; i < title.length; i++) {
    if (!(api.appendDraftChar(title.charCodeAt(i) | 0) | 0)) return -1;
  }
  return api.addFromDraft() | 0;
}

function refresh() {
  const n = api.getCount() | 0;
  listEl.innerHTML = '';
  if (n === 0) {
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = '（暂无任务）';
    listEl.appendChild(li);
    return;
  }
  for (let i = 0; i < n; i++) {
    const idx = i;
    const li = document.createElement('li');
    if (api.getDone(idx) | 0) li.classList.add('done');
    const span = document.createElement('span');
    span.textContent = ((api.getDone(idx) | 0) ? '[x] ' : '[ ] ') + titleAt(idx);
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.textContent = '完成';
    toggle.onclick = () => { if (!alive) return; api.toggleDone(idx); refresh(); };
    const del = document.createElement('button');
    del.type = 'button';
    del.textContent = '删';
    del.onclick = () => {
      if (!alive) return;
      api.removeAt(idx);
      refresh();
      setStatus('已删除', false);
    };
    li.append(span, toggle, del);
    listEl.appendChild(li);
  }
}

function saveLocal() {
  api.prepareSave();
  const len = api.getSaveLen() | 0;
  let s = '';
  for (let i = 0; i < len; i++) s += String.fromCharCode(api.getSaveChar(i) | 0);
  localStorage.setItem(STORAGE_KEY, s);
  setStatus('已保存（' + len + '）', false);
}

function loadLocal() {
  const s = localStorage.getItem(STORAGE_KEY);
  if (!s) { setStatus('无本地存档', true); return; }
  const ok = api.loadSave(writeWasmString(s)) | 0;
  if (!ok) { setStatus('加载失败 code=' + (api.getLastError() | 0), true); return; }
  refresh();
  setStatus('已加载 ' + (api.getCount() | 0) + ' 条', false);
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  if (!alive) return;
  const t = titleInput.value.trim();
  const id = addTask(t);
  if (id < 0) {
    const err = api.getLastError() | 0;
    if (err === 2) setStatus('标题不能为空', true);
    else if (err === 3) setStatus('标题过长', true);
    else setStatus('添加失败 code=' + err, true);
    return;
  }
  titleInput.value = '';
  titleInput.blur();
  refresh();
  setStatus('已添加 #' + id, false);
});

document.getElementById('save').onclick = () => alive && saveLocal();
document.getElementById('load').onclick = () => alive && loadLocal();
document.getElementById('bg').onclick = () => {
  if (!alive) return;
  bgToken += 1;
  const token = bgToken;
  setStatus('异步运行中…', false);
  setTimeout(() => {
    if (!alive || token !== bgToken) return;
    setStatus('异步完成', false);
  }, 800);
};

function onBackground() {
  setLife('lifecycle: background');
  bgToken += 1;
}

function onForeground() {
  setLife('lifecycle: foreground');
  if (api) {
    try { refresh(); } catch (_) {}
  }
}

window.addEventListener('pagehide', () => { alive = false; onBackground(); });
window.addEventListener('pageshow', () => { alive = true; onForeground(); });
document.addEventListener('visibilitychange', () => {
  if (document.hidden) onBackground();
  else { alive = true; onForeground(); }
});

/* Soft keyboard heuristic via visualViewport */
if (window.visualViewport) {
  const vv = window.visualViewport;
  const syncKb = () => {
    const shrink = window.innerHeight - vv.height > 120;
    document.body.classList.toggle('keyboard-open', shrink);
  };
  vv.addEventListener('resize', syncKb);
  vv.addEventListener('scroll', syncKb);
}

/* Optional host bridge lifecycle events (sudoku-style containers) */
window.addEventListener('message', (ev) => {
  const d = ev.data;
  if (!d || d.type !== 'event') return;
  if (d.name === 'lifecycle.background') onBackground();
  if (d.name === 'lifecycle.foreground') { alive = true; onForeground(); }
});

jnativeStart('./app.wasm', { autoStart: true })
  .then((mod) => {
    writeWasmString = mod.writeWasmString;
    api = findApi(mod.findExport);
    refresh();
    setStatus('就绪（触屏壳 · WASM）', false);
    setLife('lifecycle: foreground');
  })
  .catch((e) => {
    console.error(e);
    setStatus('WASM 启动失败: ' + e.message, true);
  });
