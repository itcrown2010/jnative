// Author: 张晓达
/**
 * Web shell: multi-page UI + TaskCore validation + SyncCore rules + IndexedDB queue.
 */
import { jnativeStart } from './app.js';
import { createRouter, pathFor } from './lib/router.js';
import { createController, createPageHost } from './lib/lifecycle.js';
import { bindTaskApi, errMessage, formatDue, feedChars, parseDueInput } from './lib/taskApi.js';
import { attachTaskForm } from './lib/formController.js';
import { loadSettings, saveSettings } from './lib/storage.js';
import { bindSyncApi, createSyncEngine, syncStateLabel } from './lib/syncEngine.js';
import { idbClearAllBusiness, idbListTasks } from './lib/idbStore.js';
import { diag, installGlobalHandlers, newEventId } from './lib/diag.js';
import { createPlatform } from './lib/platform.js';
import {
  BACKUP_MIME,
  buildBackup,
  planImport,
  previewBackup,
} from './lib/backup.js';

installGlobalHandlers();

const boot = document.getElementById('boot');
const bootMsg = document.getElementById('boot-msg');
const bootDetail = document.getElementById('boot-detail');
const bootRetry = document.getElementById('boot-retry');
const appEl = document.getElementById('app');
const statusEl = document.getElementById('status');
const pageTitle = document.getElementById('page-title');
const navBack = document.getElementById('nav-back');
const syncStrip = document.getElementById('sync-strip');
const syncOnline = document.getElementById('sync-online');
const syncPending = document.getElementById('sync-pending');
const syncPhase = document.getElementById('sync-phase');

const pages = {
  list: document.getElementById('page-list'),
  detail: document.getElementById('page-detail'),
  form: document.getElementById('page-form'),
  settings: document.getElementById('page-settings'),
  invalid: document.getElementById('page-invalid'),
};

let api = null;
let syncApi = null;
let engine = null;
let alive = true;
let settings = loadSettings();
let formCtl = null;
let formMode = 'new';
let formTaskId = '';
let host = createPageHost();
let router = null;
let tasksCache = [];
let platform = null;
let importPlan = null;
let importHandleId = null;

function setBoot(msg, detail, retryable) {
  boot.hidden = false;
  appEl.hidden = true;
  bootMsg.textContent = msg;
  bootDetail.hidden = !detail;
  bootDetail.textContent = detail || '';
  bootRetry.hidden = !retryable;
}

function setReady() {
  boot.hidden = true;
  appEl.hidden = false;
}

function setStatus(msg, isError) {
  statusEl.textContent = msg || '';
  statusEl.classList.toggle('err', !!isError);
}

function syncUrl() {
  return settings.syncUrl || 'http://127.0.0.1:8780';
}

function showPage(name) {
  Object.keys(pages).forEach((k) => { pages[k].hidden = k !== name; });
  let tab = '';
  if (name === 'list') tab = 'list';
  else if (name === 'settings') tab = 'settings';
  else if (name === 'form' && formMode === 'new') tab = 'new';
  document.querySelectorAll('.tabs a').forEach((a) => {
    a.classList.toggle('active', a.dataset.nav === tab);
  });
}

function applySettingsUi() {
  document.body.classList.toggle('dense', !!settings.denserList);
  document.getElementById('set-dense').checked = !!settings.denserList;
  document.getElementById('set-confirm').checked = settings.confirmDelete !== false;
  document.getElementById('set-sync-url').value = settings.syncUrl || 'http://127.0.0.1:8780';
  document.getElementById('set-sync-token').value = settings.syncToken || '';
}

async function refreshSyncStrip() {
  if (!engine) return;
  const st = engine.getStatusSnapshot();
  const n = await engine.pendingCount();
  syncOnline.textContent = st.online ? '在线' : '离线';
  syncStrip.classList.toggle('offline', !st.online);
  syncPending.textContent = '待同步 ' + n;
  syncPhase.textContent = st.syncing ? '同步中…' : (n ? '本地已保存，等待上传' : '队列空闲');
}

function statusLabel(s) {
  if (s === 1) return '进行中';
  if (s === 2) return '已完成';
  return '待办';
}

async function reloadTasks() {
  tasksCache = await engine.listTasks();
}

function validateWithCore(payload) {
  api.clearFormDraft();
  if (!feedChars(api.appendTitleChar, payload.title)) return { ok: false, error: errMessage(api.getLastError() | 0) };
  if (!feedChars(api.appendNoteChar, payload.notes || '')) return { ok: false, error: errMessage(api.getLastError() | 0) };
  if (!(api.setFormStatus(payload.status | 0) | 0)) return { ok: false, error: errMessage(api.getLastError() | 0) };
  if (!(api.setFormDue(payload.dueYmd | 0) | 0)) return { ok: false, error: errMessage(api.getLastError() | 0) };
  if (!(api.validateForm() | 0)) return { ok: false, error: errMessage(api.getLastError() | 0) };
  return { ok: true };
}

function ensureForm(mode, id) {
  formMode = mode;
  formTaskId = id;
  if (formCtl) { formCtl.dispose(); formCtl = null; }
  formCtl = attachTaskForm(pages.form, {
    api,
    mode,
    taskId: id,
    onValidated: async (payload) => {
      const v = validateWithCore(payload);
      if (!v.ok) return v;
      if (mode === 'new') {
        const taskId = engine.newTaskId();
        const r = await engine.applyLocalChange({
          type: 'create',
          taskId,
          payload,
          expectedVersion: 0,
        });
        if (!r.ok) return { ok: false, error: r.error || '本地保存失败' };
        setStatus('已在本地保存（待同步）', false);
        engine.scheduleSync();
        return { ok: true, id: taskId };
      }
      const cur = await engine.getTask(id);
      if (!cur) return { ok: false, error: '任务不存在' };
      const r = await engine.applyLocalChange({
        type: 'update',
        taskId: id,
        payload,
        expectedVersion: cur.serverVersion | 0,
      });
      if (!r.ok) return { ok: false, error: r.error || '本地保存失败' };
      setStatus('已在本地保存（待同步）', false);
      engine.scheduleSync();
      return { ok: true, id };
    },
    onSubmitOk: (savedId) => {
      router.navigate(pathFor('detail', { id: savedId }), { replace: true });
    },
    onCancel: () => router.back(),
  });
  if (mode === 'edit') {
    engine.getTask(id).then((t) => {
      if (!t || t.deleted) {
        router.navigate('#/not-found', { replace: true });
        return;
      }
      formCtl.fillDom({
        title: t.title,
        notes: t.notes,
        status: t.status,
        due: formatDue(t.dueYmd | 0),
      });
    });
  } else {
    formCtl.fillDom({ title: '', notes: '', status: 0, due: '' });
  }
}

function confirmLeave(from, to) {
  if (!formCtl) return true;
  if (from.name !== 'new' && from.name !== 'edit') return true;
  if (to.name === from.name && (to.params?.id === from.params?.id)) return true;
  if (!formCtl.isDirty()) return true;
  return confirm('有未保存修改，确定离开？');
}

async function paintList() {
  await reloadTasks();
  const list = document.getElementById('list');
  const state = document.getElementById('list-state');
  const q = (document.getElementById('search').value || '').trim().toLowerCase();
  list.innerHTML = '';
  let rows = tasksCache.slice();
  if (q) {
    rows = rows.filter((t) =>
      (t.title || '').toLowerCase().includes(q) || (t.notes || '').toLowerCase().includes(q)
    );
  }
  if (tasksCache.length === 0) {
    state.hidden = false;
    state.textContent = '暂无任务。点「新建」添加（可离线）。';
    return;
  }
  if (rows.length === 0) {
    state.hidden = false;
    state.textContent = '无匹配结果';
    return;
  }
  state.hidden = true;
  for (const t of rows) {
    const li = document.createElement('li');
    li.className = 'task' + (t.status === 2 ? ' done' : '');
    li.dataset.id = t.id;
    const title = document.createElement('div');
    title.className = 'title';
    title.textContent = t.title;
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent =
      syncStateLabel(t.syncState) + ' · v' + (t.serverVersion | 0) + ' · ' + statusLabel(t.status) +
      (t.dueYmd ? ' · 截止 ' + formatDue(t.dueYmd) : '');
    const open = document.createElement('button');
    open.type = 'button';
    open.textContent = '详情';
    open.onclick = () => router.navigate(pathFor('detail', { id: t.id }));
    li.append(title, meta, open);
    list.appendChild(li);
  }
}

async function paintDetail(id) {
  const t = await engine.getTask(id);
  if (!t || t.deleted) {
    router.navigate('#/not-found', { replace: true });
    return;
  }
  document.getElementById('detail-title').textContent = t.title;
  document.getElementById('detail-meta').textContent =
    syncStateLabel(t.syncState) + ' · 服务器版本 v' + (t.serverVersion | 0) +
    ' · ' + statusLabel(t.status) + (t.dueYmd ? ' · 截止 ' + formatDue(t.dueYmd) : '');
  document.getElementById('detail-notes').textContent = t.notes || '（无备注）';
  document.getElementById('detail-toggle').textContent = t.status === 2 ? '标为待办' : '标为完成';
  const box = document.getElementById('conflict-box');
  if (t.syncState === syncApi.ST_CONFLICT && t.conflictServer) {
    box.hidden = false;
    const s = t.conflictServer;
    document.getElementById('conflict-diff').textContent =
      '本地: ' + t.title + ' / st=' + t.status + ' / due=' + formatDue(t.dueYmd) + '\n' +
      '服务器: ' + (s.title || '') + ' / st=' + (s.status | 0) + ' / due=' + formatDue(s.dueYmd | 0) +
      ' / v' + (s.version | 0) + (s.deleted ? ' / 已删除' : '');
  } else {
    box.hidden = true;
  }
}

async function onRoute(route) {
  if (!engine || !alive) return;
  navBack.hidden = route.name === 'list';
  await refreshSyncStrip();
  if (route.name === 'list') {
    pageTitle.textContent = '任务列表';
    showPage('list');
    await paintList();
  } else if (route.name === 'detail') {
    pageTitle.textContent = '任务详情';
    showPage('detail');
    await paintDetail(route.params.id);
  } else if (route.name === 'new') {
    pageTitle.textContent = '新建任务';
    showPage('form');
    if (formMode !== 'new' || !formCtl) {
      ensureForm('new', '');
      formCtl.focusTitle();
    }
  } else if (route.name === 'edit') {
    pageTitle.textContent = '编辑任务';
    showPage('form');
    if (formMode !== 'edit' || formTaskId !== route.params.id || !formCtl) {
      ensureForm('edit', route.params.id);
      formCtl.focusTitle();
    }
  } else if (route.name === 'settings') {
    pageTitle.textContent = '设置';
    showPage('settings');
    applySettingsUi();
  } else {
    pageTitle.textContent = '未找到';
    showPage('invalid');
    document.getElementById('invalid-reason').textContent =
      '原因: ' + (route.params.reason || 'unknown') + ' · ' + route.path;
  }
}

function wireStaticHandlers() {
  document.getElementById('search').addEventListener('input', () => {
    if (router.current().name === 'list') paintList();
  });
  document.getElementById('btn-sync-now').addEventListener('click', () => {
    setStatus('开始同步…', false);
    engine.scheduleSync();
  });
  document.getElementById('detail-edit').addEventListener('click', () => {
    router.navigate(pathFor('edit', { id: router.current().params.id }));
  });
  document.getElementById('detail-toggle').addEventListener('click', async () => {
    const id = router.current().params.id;
    const t = await engine.getTask(id);
    if (!t) return;
    const next = t.status === 2 ? 0 : 2;
    const r = await engine.applyLocalChange({
      type: 'update',
      taskId: id,
      expectedVersion: t.serverVersion | 0,
      payload: { title: t.title, notes: t.notes, status: next, dueYmd: t.dueYmd | 0 },
    });
    if (!r.ok) { setStatus(r.error || '失败', true); return; }
    setStatus('已在本地保存', false);
    engine.scheduleSync();
    await paintDetail(id);
  });
  document.getElementById('detail-delete').addEventListener('click', async () => {
    const id = router.current().params.id;
    if (settings.confirmDelete && !confirm('删除任务？将进入待同步队列。')) return;
    const t = await engine.getTask(id);
    const r = await engine.applyLocalChange({
      type: 'delete',
      taskId: id,
      expectedVersion: t ? (t.serverVersion | 0) : 0,
      payload: {},
    });
    if (!r.ok) { setStatus(r.error || '删除失败', true); return; }
    setStatus('已在本地标记删除（待同步）', false);
    engine.scheduleSync();
    router.navigate('#/list', { replace: true });
  });
  document.getElementById('conflict-take-server').addEventListener('click', async () => {
    const id = router.current().params.id;
    await engine.resolveConflictTakeServer(id);
    setStatus('已采用服务器版本', false);
    await paintDetail(id);
  });
  document.getElementById('conflict-resubmit').addEventListener('click', async () => {
    const id = router.current().params.id;
    await engine.resolveConflictResubmitLocal(id);
    setStatus('已重新排队本地版本（仍检查服务器版本）', false);
    await paintDetail(id);
  });
  document.getElementById('form-cancel').addEventListener('click', () => router.back());
  document.getElementById('settings-form').addEventListener('submit', (e) => {
    e.preventDefault();
    settings = {
      denserList: document.getElementById('set-dense').checked,
      confirmDelete: document.getElementById('set-confirm').checked,
      syncUrl: document.getElementById('set-sync-url').value.trim() || 'http://127.0.0.1:8780',
      syncToken: document.getElementById('set-sync-token').value,
    };
    saveSettings(settings);
    applySettingsUi();
    setStatus('设置已保存', false);
  });
  document.getElementById('set-clear').addEventListener('click', async () => {
    if (!confirm('清除本地任务与待同步队列？未上传数据将丢失。')) return;
    await idbClearAllBusiness();
    setStatus('本地数据已清除', false);
    await paintList();
    refreshSyncStrip();
  });
  document.getElementById('btn-export-backup').addEventListener('click', () => exportBackup(false));
  document.getElementById('btn-share-backup').addEventListener('click', () => exportBackup(true));
  document.getElementById('btn-import-backup').addEventListener('click', () => importBackupPick());
  document.getElementById('btn-import-confirm').addEventListener('click', () => importBackupConfirm());
  document.getElementById('btn-import-cancel').addEventListener('click', () => importBackupCancel());
  navBack.addEventListener('click', () => {
    if (!router.back()) setStatus('已在根页面', false);
  });
}

async function refreshCapsUi() {
  const el = document.getElementById('caps-status');
  if (!el || !platform) return;
  try {
    const caps = await platform.queryCapabilities();
    const file = caps.find((c) => c.name === 'file.pick');
    const share = caps.find((c) => c.name === 'share.system');
    el.textContent =
      '能力: file.pick=' + (file?.status || '?') +
      ' · share.system=' + (share?.status || '?') +
      ' · 模式=' + platform.mode;
  } catch (e) {
    el.textContent = '能力查询失败: ' + (e && e.message ? e.message : e);
  }
}

async function exportBackup(doShare) {
  if (!platform || !engine) return;
  try {
    const tasks = await idbListTasks();
    const { text, fileName } = buildBackup(tasks, { appVersion: '0.1.0' });
    if (doShare) {
      const r = await platform.share({
        title: '任务备份',
        name: fileName,
        mimeType: BACKUP_MIME,
        text,
      });
      setStatus('分享面板已结束（' + (r.status || 'unknown') + '；不保证送达）', false);
      diag('bridge', 'share finished', { detail: r });
    } else {
      await platform.exportFile({ name: fileName, mimeType: BACKUP_MIME, text });
      setStatus('已导出 ' + fileName, false);
    }
  } catch (e) {
    const code = e && e.code ? e.code : 'ERROR';
    if (code === 'USER_CANCELLED') setStatus('已取消', false);
    else if (code === 'UNSUPPORTED') setStatus('当前环境不支持该能力', true);
    else if (code === 'PERMISSION_DENIED') setStatus('权限被拒绝，请在系统设置中检查（不会反复弹窗）', true);
    else setStatus((e && e.message) || '导出失败', true);
    diag('bridge', 'export/share failed', { detail: { code, message: e && e.message } });
  }
}

async function importBackupPick() {
  if (!platform) return;
  try {
    importBackupCancel();
    const picked = await platform.pickFile({
      accept: ['.jnbackup', '.json', 'application/json'],
      maxBytes: 1048576,
    });
    importHandleId = picked.handleId;
    const read = await platform.readFile(picked.handleId, { as: 'text' });
    const parsed = previewBackup(read.text);
    if (!parsed.ok) {
      setStatus(parsed.error, true);
      await platform.releaseFile(picked.handleId);
      importHandleId = null;
      return;
    }
    const existing = (await idbListTasks()).map((t) => t.id);
    const strategy = 'append';
    importPlan = { preview: parsed.preview, plan: planImport(parsed.preview, existing, strategy) };
    const box = document.getElementById('import-preview');
    box.hidden = false;
    document.getElementById('import-summary').textContent =
      '文件「' + picked.name + '」· 有效 ' + parsed.preview.validCount +
      ' / 无效 ' + parsed.preview.invalidCount +
      (parsed.preview.duplicateInFile.length ? ' / 文件内重复 ' + parsed.preview.duplicateInFile.length : '') +
      ' · 示例: ' + (parsed.preview.sampleTitles.join('、') || '（无）');
    setStatus('请确认导入策略', false);
  } catch (e) {
    const code = e && e.code ? e.code : 'ERROR';
    if (code === 'USER_CANCELLED') setStatus('已取消选择', false);
    else setStatus((e && e.message) || '导入预览失败', true);
  }
}

async function importBackupConfirm() {
  if (!platform || !engine || !importPlan) return;
  const mode = (document.querySelector('input[name="import-mode"]:checked') || {}).value || 'append';
  const existing = (await idbListTasks()).map((t) => t.id);
  const plan = planImport(importPlan.preview, existing, mode);
  try {
    if (plan.willClearAll) {
      if (!confirm('替换将清除本地任务与待同步队列（不含同步服务地址设置）。继续？')) return;
      await idbClearAllBusiness();
    }
    let added = 0;
    for (const rec of plan.toAdd) {
      if (rec.deleted) continue;
      const r = await engine.applyLocalChange({
        type: 'create',
        taskId: rec.id,
        expectedVersion: 0,
        payload: {
          title: rec.title,
          notes: rec.notes,
          status: rec.status | 0,
          dueYmd: rec.dueYmd | 0,
        },
      });
      if (r && r.ok) added += 1;
    }
    setStatus(
      '导入完成：新增 ' + added +
        (plan.skippedExisting.length ? '，跳过已有 ' + plan.skippedExisting.length : '') +
        '（导入项进入本地待同步，不含旧 serverVersion）',
      false,
    );
    importBackupCancel();
    await paintList();
    refreshSyncStrip();
  } catch (e) {
    setStatus((e && e.message) || '导入失败（事务未部分提交为业务成功）', true);
  }
}

async function importBackupCancel() {
  document.getElementById('import-preview').hidden = true;
  importPlan = null;
  if (importHandleId && platform) {
    try { await platform.releaseFile(importHandleId); } catch (_) {}
  }
  importHandleId = null;
}

async function startWasm() {
  setBoot('正在初始化 WASM…', '', false);
  const mod = await jnativeStart('./app.wasm', { autoStart: true });
  api = bindTaskApi(mod.findExport);
  syncApi = bindSyncApi(mod.findExport);
  settings = {
    denserList: !!settings.denserList,
    confirmDelete: settings.confirmDelete !== false,
    syncUrl: settings.syncUrl || 'http://127.0.0.1:8780',
    syncToken: settings.syncToken || '',
  };
  engine = createSyncEngine({
    syncApi,
    getBaseUrl: () => syncUrl(),
    getToken: () => settings.syncToken || '',
    onChange: () => {
      refreshSyncStrip();
      const r = router && router.current();
      if (!r) return;
      if (r.name === 'detail') paintDetail(r.params.id);
      if (r.name === 'list') paintList();
    },
  });
  setBoot('正在加载本地 IndexedDB…', '', false);
  await reloadTasks();
  try {
    platform = await createPlatform();
    await platform.init();
    await refreshCapsUi();
  } catch (e) {
    diag('bridge', 'platform init degraded', { detail: e && e.message });
    platform = null;
  }
  applySettingsUi();
  setReady();
  setStatus('就绪（IndexedDB + 同步队列）', false);
  wireStaticHandlers();
  router = createRouter({
    onChange: (route) => {
      const sid = route.name + ':' + JSON.stringify(route.params || {});
      const ctl = createController('page:' + route.name, {
        onUpdate() { onRoute(route); },
      }, sid);
      host.show(ctl, route);
    },
    confirmLeave,
  });
  router.bind();
  engine.scheduleSync();
  await refreshSyncStrip();
}

bootRetry.addEventListener('click', () => {
  bootRetry.hidden = true;
  startWasm().catch(onFatal);
});

function onFatal(e) {
  const msg = String(e && e.message ? e.message : e);
  const layer = /wasm|WebAssembly|export/i.test(msg) ? 'wasm' : 'javascript';
  diag(layer, '应用启动失败', { eventId: newEventId('boot'), detail: msg });
  setBoot('应用启动失败', msg, true);
}

window.addEventListener('pagehide', () => {
  alive = false;
  if (engine) engine.dispose();
  host.dispose();
  if (formCtl) formCtl.dispose();
  if (platform) {
    try { platform.destroy(); } catch (_) {}
    platform = null;
  }
  importPlan = null;
  importHandleId = null;
});

startWasm().catch(onFatal);
