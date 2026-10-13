// Author: 张晓达
/** Ordinary browser storage adapter — not a secure credential store. */

export const STORAGE_KEY = 'jnative.guide-tasks.v3';
export const SETTINGS_KEY = 'jnative.guide-tasks.settings.v1';

export function loadSettings() {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) {
      return {
        denserList: false,
        confirmDelete: true,
        syncUrl: 'http://127.0.0.1:8780',
        syncToken: '',
      };
    }
    const o = JSON.parse(raw);
    return {
      denserList: !!o.denserList,
      confirmDelete: o.confirmDelete !== false,
      syncUrl: o.syncUrl || 'http://127.0.0.1:8780',
      syncToken: o.syncToken || '',
    };
  } catch (_) {
    return {
      denserList: false,
      confirmDelete: true,
      syncUrl: 'http://127.0.0.1:8780',
      syncToken: '',
    };
  }
}

export function saveSettings(s) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(s));
}

export function persistTasks(api) {
  api.prepareSave();
  const len = api.getSaveLen() | 0;
  let s = '';
  for (let i = 0; i < len; i++) s += String.fromCharCode(api.getSaveChar(i) | 0);
  try {
    localStorage.setItem(STORAGE_KEY, s);
    return { ok: true, len };
  } catch (e) {
    return { ok: false, error: '存储失败（可能已满或被禁用）' };
  }
}

export function restoreTasks(api, writeWasmString) {
  let s;
  try {
    s = localStorage.getItem(STORAGE_KEY);
  } catch (e) {
    return { ok: false, error: '无法读取本地存储', code: -1 };
  }
  if (!s) return { ok: true, empty: true };
  const ok = api.loadSave(writeWasmString(s)) | 0;
  if (!ok) return { ok: false, code: api.getLastError() | 0, error: '存档无效' };
  return { ok: true, empty: false, count: api.getCount() | 0 };
}

export function clearTasksStorage() {
  try {
    localStorage.removeItem(STORAGE_KEY);
    return true;
  } catch (_) {
    return false;
  }
}
