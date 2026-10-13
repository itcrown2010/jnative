// Author: 张晓达
/** Thin WASM TaskCore binding — business rules stay in JNative. */

export function bindTaskApi(findExport) {
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
    clearFormDraft: need('TaskCore_clearFormDraft'),
    appendTitleChar: need('TaskCore_appendTitleChar_i32'),
    appendNoteChar: need('TaskCore_appendNoteChar_i32'),
    setFormStatus: need('TaskCore_setFormStatus_i32'),
    setFormDue: need('TaskCore_setFormDue_i32'),
    validateForm: need('TaskCore_validateForm'),
    addFromForm: need('TaskCore_addFromForm'),
    updateFromFormById: need('TaskCore_updateFromFormById_i32'),
    loadFormFromId: need('TaskCore_loadFormFromId_i32'),
    getTitleLen: need('TaskCore_getTitleLen_i32'),
    getTitleChar: need('TaskCore_getTitleChar_i32_i32'),
    getNoteLen: need('TaskCore_getNoteLen_i32'),
    getNoteChar: need('TaskCore_getNoteChar_i32_i32'),
    getStatus: need('TaskCore_getStatus_i32'),
    getDueYmd: need('TaskCore_getDueYmd_i32'),
    getDone: need('TaskCore_getDone_i32'),
    toggleDoneById: need('TaskCore_toggleDoneById_i32'),
    setStatusById: need('TaskCore_setStatusById_i32_i32'),
    removeById: need('TaskCore_removeById_i32'),
    clearQuery: need('TaskCore_clearQuery'),
    appendQueryChar: need('TaskCore_appendQueryChar_i32'),
    matchesQuery: need('TaskCore_matchesQuery_i32'),
    prepareSave: need('TaskCore_prepareSave'),
    getSaveLen: need('TaskCore_getSaveLen'),
    getSaveChar: need('TaskCore_getSaveChar_i32'),
    loadSave: need('TaskCore_loadSave_CString'),
    clearAll: need('TaskCore_clearAll'),
  };
}

export function readField(api, lenFn, charFn, index) {
  const n = lenFn(index) | 0;
  let s = '';
  for (let i = 0; i < n; i++) s += String.fromCharCode(charFn(index, i) | 0);
  return s;
}

export function statusLabel(s) {
  if (s === 1) return '进行中';
  if (s === 2) return '已完成';
  return '待办';
}

export function errMessage(code) {
  if (code === 2) return '标题不能为空';
  if (code === 3) return '标题或备注过长';
  if (code === 4) return '任务数量已满';
  if (code === 5) return '存档损坏或版本不受支持';
  if (code === 6) return '日期无效（请用合法的年-月-日，不会自动改写）';
  if (code === 7) return '进行中的任务需要填写备注';
  if (code === 1) return '无效参数';
  return '操作失败 code=' + code;
}

/** Parse YYYY-MM-DD → YYYYMMDD int. Empty → 0. Invalid → { ok:false }. Never defaults. */
export function parseDueInput(text) {
  const t = (text || '').trim();
  if (!t) return { ok: true, ymd: 0 };
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(t);
  if (!m) return { ok: false, error: '日期格式须为 YYYY-MM-DD' };
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  const ymd = y * 10000 + mo * 100 + d;
  return { ok: true, ymd, display: t };
}

export function formatDue(ymd) {
  if (!ymd) return '';
  const y = Math.floor(ymd / 10000);
  const m = Math.floor((ymd / 100) % 100);
  const d = ymd % 100;
  const pad = (n) => (n < 10 ? '0' + n : String(n));
  return y + '-' + pad(m) + '-' + pad(d);
}

export function feedChars(fn, text) {
  for (let i = 0; i < text.length; i++) {
    if (!(fn(text.charCodeAt(i) | 0) | 0)) return false;
  }
  return true;
}
