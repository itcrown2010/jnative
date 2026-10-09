/**
 * Browser Sudoku shell — mouse/keyboard layout.
 * All rules/solve/conflict logic come from WASM SudokuCore exports.
 */
import { jnativeStart } from './app.js';
import { JNativeBridge, installWebPlatformHost } from './bridge.js';

const SAVE_KEY = 'jnative.sudoku.save.v1';
const I18N = {
  zh: {
    title: 'JNative 数独',
    ready: '选择格子并输入数字',
    won: '完成！',
    paused: '已暂停',
    notes: '笔记模式',
    conflict: '冲突（不只靠颜色：单元格标记为冲突）',
    loadFail: '存档无效，当前对局未改动',
    saved: '已保存',
    loaded: '已加载',
    wasmFail: 'WASM 加载失败',
  },
  en: {
    title: 'JNative Sudoku',
    ready: 'Select a cell and enter a digit',
    won: 'Solved!',
    paused: 'Paused',
    notes: 'Notes mode',
    conflict: 'Conflict (cells marked conflict, not color-only)',
    loadFail: 'Bad save; current game kept',
    saved: 'Saved',
    loaded: 'Loaded',
    wasmFail: 'WASM load failed',
  },
};

function $(id) { return document.getElementById(id); }
function t(lang, k) { return (I18N[lang] || I18N.zh)[k] || k; }

function findApi(findExport) {
  const need = (sub) => {
    const fn = findExport(sub);
    if (typeof fn !== 'function') throw new Error('missing export ' + sub);
    return fn;
  };
  return {
    newGame: need('SudokuCore_newGame_i32_i32'),
    selectCell: need('SudokuCore_selectCell_i32_i32'),
    moveSelection: need('SudokuCore_moveSelection_i32_i32'),
    inputDigit: need('SudokuCore_inputDigit_i32'),
    clearCell: need('SudokuCore_clearCell'),
    toggleNoteMode: need('SudokuCore_toggleNoteMode'),
    undo: need('SudokuCore_undo'),
    redo: need('SudokuCore_redo'),
    hint: need('SudokuCore_hint'),
    pause: need('SudokuCore_pause'),
    resume: need('SudokuCore_resume'),
    tick: need('SudokuCore_tick_i32'),
    getElapsedMs: need('SudokuCore_getElapsedMs'),
    getStatus: need('SudokuCore_getStatus'),
    getSelected: need('SudokuCore_getSelected'),
    getNoteMode: need('SudokuCore_getNoteMode'),
    getValue: need('SudokuCore_getValue_i32'),
    getGiven: need('SudokuCore_getGiven_i32'),
    getNotes: need('SudokuCore_getNotes_i32'),
    getConflict: need('SudokuCore_getConflict_i32'),
    getHighlightDigit: need('SudokuCore_getHighlightDigit'),
    sameRow: need('SudokuCore_sameRow_i32'),
    sameCol: need('SudokuCore_sameCol_i32'),
    sameBox: need('SudokuCore_sameBox_i32'),
    isComplete: need('SudokuCore_isComplete'),
    prepareSave: need('SudokuCore_prepareSave'),
    getSaveLen: need('SudokuCore_getSaveLen'),
    getSaveChar: need('SudokuCore_getSaveChar_i32'),
    loadSave: need('SudokuCore_loadSave_CString'),
    getLastError: need('SudokuCore_getLastError'),
  };
}

function notesText(mask) {
  let s = '';
  for (let d = 1; d <= 9; d++) if (mask & (1 << (d - 1))) s += d;
  return s || ' ';
}

function formatTime(ms) {
  const sec = Math.floor(ms / 1000);
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return m + ':' + String(s).padStart(2, '0');
}

async function main() {
  const touch = document.body.classList.contains('touch');
  const lang = { value: 'zh' };
  const status = (msg, err) => {
    const el = $('status');
    el.textContent = msg || '';
    el.className = err ? 'status error' : 'status';
  };

  const isNativeShell = !!(window.JNativeHost || window.webkit?.messageHandlers?.jnativeBridge);
  if (!isNativeShell) installWebPlatformHost();

  const bridge = new JNativeBridge({
    requiredCapabilities: ['platform.getName', 'storage.get', 'storage.set'],
  });
  window.__jnativeBridge = bridge;

  let writeWasmString, findExport, api;
  try {
    ({ writeWasmString, findExport } = await jnativeStart('./app.wasm', { autoStart: true }));
    api = findApi(findExport);
  } catch (e) {
    status(t(lang.value, 'wasmFail') + ': ' + e.message, true);
    throw e;
  }

  let session;
  try {
    session = await bridge.init();
  } catch (e) {
    status('Bridge: ' + e.message, true);
    throw e;
  }
  $('platform').textContent = 'platform: ' + session.platform;

  const boardEl = $('board');
  const cells = [];
  for (let i = 0; i < 81; i++) {
    const div = document.createElement('div');
    div.className = 'cell';
    div.setAttribute('role', 'gridcell');
    div.tabIndex = 0;
    div.addEventListener(touch ? 'pointerup' : 'click', () => {
      api.selectCell((i / 9) | 0, i % 9);
      render();
    });
    boardEl.appendChild(div);
    cells.push(div);
  }

  function readSave() {
    api.prepareSave();
    const n = api.getSaveLen() | 0;
    let s = '';
    for (let i = 0; i < n; i++) s += String.fromCharCode(api.getSaveChar(i) | 0);
    return s;
  }

  async function persist() {
    const data = readSave();
    try {
      await bridge.request('storage.set', { key: SAVE_KEY, value: data });
      status(t(lang.value, 'saved'));
    } catch (e) {
      status(String(e.message || e), true);
    }
  }

  async function restore() {
    try {
      const data = await bridge.request('storage.get', { key: SAVE_KEY });
      if (!data) return;
      const ok = api.loadSave(writeWasmString(String(data)));
      if (!ok) status(t(lang.value, 'loadFail'), true);
      else status(t(lang.value, 'loaded'));
    } catch (e) {
      status(t(lang.value, 'loadFail'), true);
    }
  }

  function render() {
    const now = performance.now() | 0;
    api.tick(now);
    $('timer').textContent = formatTime(api.getElapsedMs() | 0);
    const sel = api.getSelected() | 0;
    const hi = api.getHighlightDigit() | 0;
    for (let i = 0; i < 81; i++) {
      const v = api.getValue(i) | 0;
      const given = api.getGiven(i) | 0;
      const notes = api.getNotes(i) | 0;
      const conflict = api.getConflict(i) | 0;
      const el = cells[i];
      el.className = 'cell';
      if (given) el.classList.add('given');
      else if (v) el.classList.add('user');
      if (notes && !v) el.classList.add('notes');
      if (i === sel) el.classList.add('selected');
      else if ((api.sameRow(i) | api.sameCol(i) | api.sameBox(i)) !== 0) el.classList.add('peer');
      if (hi && v === hi) el.classList.add('same');
      if (conflict) el.classList.add('conflict');
      el.textContent = v ? String(v) : notes ? notesText(notes) : '';
      const label = [];
      label.push('r' + (((i / 9) | 0) + 1) + 'c' + ((i % 9) + 1));
      if (given) label.push('fixed');
      else if (v) label.push('user ' + v);
      else if (notes) label.push('notes ' + notesText(notes));
      else label.push('empty');
      if (conflict) label.push('conflict');
      if (i === sel) label.push('selected');
      el.setAttribute('aria-label', label.join(', '));
    }
    $('title').textContent = t(lang.value, 'title');
    if ((api.isComplete() | 0) !== 0) status(t(lang.value, 'won'));
    else if ((api.getStatus() | 0) === 1) status(t(lang.value, 'paused'));
    else if ((api.getNoteMode() | 0) !== 0) status(t(lang.value, 'notes'));
    else if ((api.getConflict(sel) | 0) !== 0) status(t(lang.value, 'conflict'));
    else status(t(lang.value, 'ready'));
    $('notesBtn').classList.toggle('active', (api.getNoteMode() | 0) !== 0);
  }

  $('btnEasy').onclick = () => { api.newGame(0, 0); render(); };
  $('btnMedium').onclick = () => { api.newGame(1, 0); render(); };
  $('btnHard').onclick = () => { api.newGame(2, 0); render(); };
  $('btnUndo').onclick = () => { api.undo(); render(); };
  $('btnRedo').onclick = () => { api.redo(); render(); };
  $('btnHint').onclick = () => { api.hint(); render(); };
  $('notesBtn').onclick = () => { api.toggleNoteMode(); render(); };
  $('btnPause').onclick = () => {
    if ((api.getStatus() | 0) === 1) api.resume();
    else api.pause();
    render();
  };
  $('btnSave').onclick = () => { persist().then(render); };
  $('btnLoad').onclick = () => { restore().then(render); };
  $('btnLang').onclick = () => {
    lang.value = lang.value === 'zh' ? 'en' : 'zh';
    render();
  };
  for (let d = 1; d <= 9; d++) {
    $('d' + d).onclick = () => { api.inputDigit(d); render(); };
  }
  $('btnClear').onclick = () => { api.clearCell(); render(); };

  if (!touch) {
    window.addEventListener('keydown', (ev) => {
      if (ev.key === 'ArrowLeft') api.moveSelection(0, -1);
      else if (ev.key === 'ArrowRight') api.moveSelection(0, 1);
      else if (ev.key === 'ArrowUp') api.moveSelection(-1, 0);
      else if (ev.key === 'ArrowDown') api.moveSelection(1, 0);
      else if (ev.key === 'Backspace' || ev.key === 'Delete' || ev.key === '0') api.clearCell();
      else if (ev.key >= '1' && ev.key <= '9') api.inputDigit(Number(ev.key));
      else return;
      ev.preventDefault();
      render();
    });
  }

  bridge.onLifecycle(async (name) => {
    $('lifecycle').textContent = 'lifecycle: ' + name;
    if (name === 'lifecycle.background') {
      api.pause();
      await persist();
      render();
    } else if (name === 'lifecycle.foreground') {
      api.resume();
      render();
    }
  });

  api.newGame(0, 0);
  await restore();
  render();
  setInterval(render, 500);
}

main().catch((e) => console.error(e));
