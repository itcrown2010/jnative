// Author: 张晓达
/**
 * Touch shell: DOM + hash route + persistence (bridge storage or localStorage) + fetch.
 * Business rules remain in TaskCore (WASM).
 */
import { jnativeStart } from './app.js';
import { JNativeBridge } from './bridge.js';

const STORAGE_KEY = 'jnative.guide-tasks.mobile.v2';
const HINT_URL = './api/hint.json';

const els = {
  status: document.getElementById('status'),
  life: document.getElementById('lifecycle'),
  listView: document.getElementById('view-list'),
  editView: document.getElementById('view-edit'),
  failView: document.getElementById('view-fail'),
  failDetail: document.getElementById('fail-detail'),
  list: document.getElementById('list'),
  listState: document.getElementById('list-state'),
  formAdd: document.getElementById('form-add'),
  title: document.getElementById('title'),
  formError: document.getElementById('form-error'),
  search: document.getElementById('search'),
  sync: document.getElementById('sync'),
  back: document.getElementById('back'),
  editHeading: document.getElementById('edit-heading'),
  formEdit: document.getElementById('form-edit'),
  editTitle: document.getElementById('edit-title'),
  editError: document.getElementById('edit-error'),
  editCancel: document.getElementById('edit-cancel'),
  dirtyHint: document.getElementById('dirty-hint'),
};

let api = null;
let writeWasmString = null;
let alive = true;
let syncAbort = null;
let syncToken = 0;
let route = { name: 'list', id: -1 };
let editBaseline = '';
let bridge = null;
let useBridgeStorage = false;

function setStatus(msg, isError) {
  els.status.textContent = msg;
  els.status.classList.toggle('err', !!isError);
}

function setLife(msg) {
  if (els.life) els.life.textContent = msg || '';
}

function showFieldError(el, msg) {
  if (!msg) { el.hidden = true; el.textContent = ''; return; }
  el.hidden = false;
  el.textContent = msg;
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
    getId: need('TaskCore_getId_i32'),
    indexOfId: need('TaskCore_indexOfId_i32'),
    clearDraft: need('TaskCore_clearDraft'),
    appendDraftChar: need('TaskCore_appendDraftChar_i32'),
    addFromDraft: need('TaskCore_addFromDraft'),
    getTitleLen: need('TaskCore_getTitleLen_i32'),
    getTitleChar: need('TaskCore_getTitleChar_i32_i32'),
    getDone: need('TaskCore_getDone_i32'),
    toggleDoneById: need('TaskCore_toggleDoneById_i32'),
    removeById: need('TaskCore_removeById_i32'),
    setTitleFromDraftById: need('TaskCore_setTitleFromDraftById_i32'),
    clearQuery: need('TaskCore_clearQuery'),
    appendQueryChar: need('TaskCore_appendQueryChar_i32'),
    matchesQuery: need('TaskCore_matchesQuery_i32'),
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

function errMessage(code) {
  if (code === 2) return '标题不能为空';
  if (code === 3) return '标题过长';
  if (code === 4) return '任务已满';
  if (code === 5) return '存档损坏或版本不受支持';
  if (code === 1) return '无效参数';
  return '失败 code=' + code;
}

function addTask(title) {
  api.clearDraft();
  for (let i = 0; i < title.length; i++) {
    if (!(api.appendDraftChar(title.charCodeAt(i) | 0) | 0)) return -1;
  }
  return api.addFromDraft() | 0;
}

function setTitleById(id, title) {
  api.clearDraft();
  for (let i = 0; i < title.length; i++) {
    if (!(api.appendDraftChar(title.charCodeAt(i) | 0) | 0)) return 0;
  }
  return api.setTitleFromDraftById(id) | 0;
}

function setQuery(q) {
  api.clearQuery();
  for (let i = 0; i < q.length; i++) {
    if (!(api.appendQueryChar(q.charCodeAt(i) | 0) | 0)) break;
  }
}

async function storageGet() {
  if (useBridgeStorage && bridge) {
    try {
      const v = await bridge.call('storage.get', { key: STORAGE_KEY });
      return v == null ? null : String(v);
    } catch (_) { /* fall through */ }
  }
  return localStorage.getItem(STORAGE_KEY);
}

async function storageSet(value) {
  if (useBridgeStorage && bridge) {
    try {
      await bridge.call('storage.set', { key: STORAGE_KEY, value });
      return;
    } catch (_) { /* fall through */ }
  }
  localStorage.setItem(STORAGE_KEY, value);
}

async function storageRemove() {
  if (useBridgeStorage && bridge) {
    try {
      await bridge.call('storage.remove', { key: STORAGE_KEY });
      return;
    } catch (_) { /* fall through */ }
  }
  localStorage.removeItem(STORAGE_KEY);
}

async function persist() {
  if (!api) return;
  api.prepareSave();
  const len = api.getSaveLen() | 0;
  let s = '';
  for (let i = 0; i < len; i++) s += String.fromCharCode(api.getSaveChar(i) | 0);
  await storageSet(s);
}

async function restore() {
  const s = await storageGet();
  if (!s) return { ok: true, empty: true };
  const ok = api.loadSave(writeWasmString(s)) | 0;
  if (!ok) return { ok: false, code: api.getLastError() | 0 };
  return { ok: true, empty: false, count: api.getCount() | 0 };
}

function parseHash() {
  const raw = (location.hash || '#/').replace(/^#/, '');
  const parts = raw.split('/').filter(Boolean);
  if (parts[0] === 'edit' && parts[1] != null) {
    const id = Number(parts[1]);
    if (Number.isInteger(id) && id >= 0) return { name: 'edit', id };
  }
  return { name: 'list', id: -1 };
}

function navigate(hash, replace) {
  const next = hash.startsWith('#') ? hash : '#' + hash;
  if (replace) history.replaceState(null, '', next);
  else if (location.hash !== next) location.hash = next;
  else applyRoute();
}

function isDirty() {
  return route.name === 'edit' && els.editTitle.value !== editBaseline;
}

function applyRoute() {
  if (!api) return;
  const next = parseHash();
  if (route.name === 'edit' && next.name === 'list' && isDirty()) {
    if (!confirm('有未保存修改，确定离开？')) {
      navigate('#/edit/' + route.id, true);
      return;
    }
  }
  route = next;
  els.listView.hidden = route.name !== 'list';
  els.editView.hidden = route.name !== 'edit';
  els.failView.hidden = true;
  if (route.name === 'list') refreshList();
  else openEdit(route.id);
}

function openEdit(id) {
  const idx = api.indexOfId(id) | 0;
  if (idx < 0) {
    setStatus('任务不存在', true);
    navigate('#/', true);
    return;
  }
  const title = titleAt(idx);
  els.editHeading.textContent = '编辑 #' + id;
  els.editTitle.value = title;
  editBaseline = title;
  els.dirtyHint.hidden = true;
  showFieldError(els.editError, '');
}

function refreshList() {
  if (!alive || !api) return;
  setQuery((els.search.value || '').trim());
  const n = api.getCount() | 0;
  els.list.innerHTML = '';
  if (n === 0) {
    els.listState.hidden = false;
    els.listState.textContent = '暂无任务';
    return;
  }
  let shown = 0;
  for (let i = 0; i < n; i++) {
    if (!(api.matchesQuery(i) | 0)) continue;
    shown += 1;
    const id = api.getId(i) | 0;
    const done = api.getDone(i) | 0;
    const li = document.createElement('li');
    li.className = 'task' + (done ? ' done' : '');
    const span = document.createElement('span');
    span.textContent = (done ? '[x] ' : '[ ] ') + titleAt(i);
    const actions = document.createElement('div');
    actions.className = 'actions';
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.textContent = done ? '重开' : '完成';
    toggle.onclick = async () => {
      if (!alive) return;
      api.toggleDoneById(id);
      await persist();
      refreshList();
    };
    const edit = document.createElement('button');
    edit.type = 'button';
    edit.textContent = '编辑';
    edit.onclick = () => alive && navigate('#/edit/' + id);
    const del = document.createElement('button');
    del.type = 'button';
    del.textContent = '删';
    del.onclick = async () => {
      if (!alive) return;
      if (!confirm('删除 #' + id + '？')) return;
      api.removeById(id);
      await persist();
      refreshList();
    };
    actions.append(toggle, edit, del);
    li.append(span, actions);
    els.list.appendChild(li);
  }
  if (shown === 0) {
    els.listState.hidden = false;
    els.listState.textContent = '无匹配结果';
  } else {
    els.listState.hidden = true;
  }
}

els.formAdd.addEventListener('submit', async (e) => {
  e.preventDefault();
  if (!alive || !api) return;
  const id = addTask(els.title.value.trim());
  if (id < 0) {
    showFieldError(els.formError, errMessage(api.getLastError() | 0));
    return;
  }
  showFieldError(els.formError, '');
  els.title.value = '';
  els.title.blur();
  await persist();
  refreshList();
  setStatus('已添加 #' + id, false);
});

els.search.addEventListener('input', () => alive && api && refreshList());

els.formEdit.addEventListener('submit', async (e) => {
  e.preventDefault();
  if (!alive || !api || route.name !== 'edit') return;
  const t = els.editTitle.value.trim();
  if (!(setTitleById(route.id, t) | 0)) {
    showFieldError(els.editError, errMessage(api.getLastError() | 0));
    return;
  }
  editBaseline = t;
  els.dirtyHint.hidden = true;
  els.editTitle.blur();
  await persist();
  navigate('#/');
});

els.editTitle.addEventListener('input', () => {
  els.dirtyHint.hidden = !isDirty();
});

els.editCancel.addEventListener('click', () => {
  if (isDirty() && !confirm('放弃未保存修改？')) return;
  editBaseline = els.editTitle.value;
  navigate('#/');
});

els.back.addEventListener('click', () => {
  if (isDirty() && !confirm('有未保存修改，确定返回？')) return;
  editBaseline = els.editTitle.value;
  navigate('#/');
});

els.sync.addEventListener('click', async () => {
  if (!alive) return;
  if (syncAbort) { syncAbort.abort(); syncAbort = null; }
  syncToken += 1;
  const token = syncToken;
  const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
  syncAbort = ctrl;
  setStatus('网络请求中…', false);
  const timer = setTimeout(() => ctrl && ctrl.abort(), 4000);
  try {
    const resp = await fetch(HINT_URL, { signal: ctrl ? ctrl.signal : undefined, cache: 'no-store' });
    clearTimeout(timer);
    if (!alive || token !== syncToken) return;
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    const data = await resp.json();
    if (!alive || token !== syncToken) return;
    setStatus('网络成功: ' + (data.message || 'ok'), false);
  } catch (e) {
    clearTimeout(timer);
    if (!alive || token !== syncToken) return;
    if (e && e.name === 'AbortError') setStatus('网络已取消或超时', true);
    else setStatus('网络失败: ' + (e.message || e), true);
  } finally {
    if (syncAbort === ctrl) syncAbort = null;
  }
});

window.addEventListener('hashchange', () => applyRoute());

function onBackground() {
  setLife('lifecycle: background');
  syncToken += 1;
  if (syncAbort) { syncAbort.abort(); syncAbort = null; }
  if (api) persist().catch(() => {});
}

function onForeground() {
  setLife('lifecycle: foreground');
  alive = true;
  if (api) applyRoute();
}

window.addEventListener('pagehide', () => { alive = false; onBackground(); if (bridge) bridge.destroy(); });
window.addEventListener('pageshow', () => onForeground());
document.addEventListener('visibilitychange', () => {
  if (document.hidden) onBackground();
  else onForeground();
});

if (window.visualViewport) {
  const vv = window.visualViewport;
  const syncKb = () => {
    document.body.classList.toggle('keyboard-open', window.innerHeight - vv.height > 120);
  };
  vv.addEventListener('resize', syncKb);
  vv.addEventListener('scroll', syncKb);
}

async function bootBridge() {
  const hasHost = !!(window.JNativeHost || window.webkit?.messageHandlers?.jnativeBridge);
  if (!hasHost) return;
  bridge = new JNativeBridge({
    timeoutMs: 5000,
    requiredCapabilities: ['storage.get', 'storage.set', 'storage.remove'],
  });
  bridge.onLifecycle((name) => {
    if (name === 'lifecycle.background') onBackground();
    if (name === 'lifecycle.foreground') onForeground();
  });
  try {
    const session = await bridge.init();
    useBridgeStorage = true;
    setLife('bridge: ' + (session.platform || 'ok'));
  } catch (e) {
    useBridgeStorage = false;
    setLife('bridge fallback localStorage');
    bridge.destroy();
    bridge = null;
  }
}

jnativeStart('./app.wasm', { autoStart: true })
  .then(async (mod) => {
    writeWasmString = mod.writeWasmString;
    api = findApi(mod.findExport);
    await bootBridge();
    const r = await restore();
    if (!r.ok) {
      setStatus(errMessage(r.code) + '（已忽略损坏存档）', true);
      await storageRemove();
    } else if (r.empty) {
      setStatus('就绪（触屏壳 · WASM）', false);
    } else {
      setStatus('已恢复 ' + r.count + ' 条', false);
    }
    applyRoute();
    setLife(els.life.textContent || 'lifecycle: foreground');
  })
  .catch((e) => {
    console.error(e);
    els.listView.hidden = true;
    els.editView.hidden = true;
    els.failView.hidden = false;
    els.failDetail.textContent = String(e && e.stack ? e.stack : e);
    setStatus('WASM 启动失败', true);
  });
