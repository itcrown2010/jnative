#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build_web.sh"
DIST="$ROOT/web/dist"
[[ -f "$DIST/lib/router.js" ]] || { echo "missing lib/"; exit 1; }

node --input-type=module << EOF
import { readFileSync } from 'fs';
const wasmBytes = readFileSync('$DIST/app.wasm');
const imports = { jnative: {
  console_log() {}, document_get_element_by_id(){return 0}, document_body(){return 0},
  document_create_element(){return 0}, element_append_child(){}, element_set_text(){},
  create_text(){return 0}, query_selector(){return 0}, apply_dom_patches(){},
  schedule_microtask(){}, delegate_event(){}, init_delegation(){},
  register_callback(id){return id}, unregister_callback(){}, set_count_label(){},
  input_value(){return 0}, input_set_value(){}, memory_grew(){}, report_uncaught(){},
  storage_set(){}, storage_get(){return 0}, fetch_get(){return 0},
}};
const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
instance.exports.jnative_start();
const e = instance.exports;
const find = (sub) => { for (const k of Object.keys(e)) if (k.includes(sub)) return e[k]; throw new Error('missing '+sub); };
const clear = find('TaskCore_clearFormDraft');
const appendT = find('TaskCore_appendTitleChar_i32');
const appendN = find('TaskCore_appendNoteChar_i32');
const setSt = find('TaskCore_setFormStatus_i32');
const setDue = find('TaskCore_setFormDue_i32');
const add = find('TaskCore_addFromForm');
const getCount = find('TaskCore_getCount');
const getId = find('TaskCore_getId_i32');
const getStatus = find('TaskCore_getStatus_i32');
const getDue = find('TaskCore_getDueYmd_i32');
const removeById = find('TaskCore_removeById_i32');
const prepareSave = find('TaskCore_prepareSave');
const getSaveLen = find('TaskCore_getSaveLen');
const getSaveChar = find('TaskCore_getSaveChar_i32');
const loadSave = find('TaskCore_loadSave_CString');
clear();
appendT(66); appendT(117); appendT(121);
setSt(0); setDue(20261013);
const id0 = add();
if (id0 !== 0) throw new Error('id0');
clear();
appendT(88); appendN(110); setSt(1);
const id1 = add();
if (id1 !== 1 || getCount() !== 2) throw new Error('add2');
if (getStatus(1) !== 1 || getDue(0) !== 20261013) throw new Error('fields');
removeById(0);
if (getCount() !== 1 || getId(0) !== 1) throw new Error('stable');
prepareSave();
let s = '';
const len = getSaveLen()|0;
for (let i=0;i<len;i++) s += String.fromCharCode(getSaveChar(i)|0);
if (!s.startsWith('V3')) throw new Error('ver '+s.slice(0,4));
const enc = new TextEncoder().encode(s);
const ptr = e.jn_bump(enc.length + 4) >>> 0;
new DataView(e.memory.buffer).setUint32(ptr, enc.length, true);
new Uint8Array(e.memory.buffer, ptr+4, enc.length).set(enc);
if (!(loadSave(ptr)|0)) throw new Error('load');
const merge = find('SyncCore_mergeDecision_i32_i32_i32');
if ((merge(1,2,1)|0) !== 2) throw new Error('merge');
if ((merge(1,3,1)|0) !== 3) throw new Error('drop-both');
console.log('GUIDE_TASKS_WASM_OK');
EOF
