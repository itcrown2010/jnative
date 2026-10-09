#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build_web.sh"
DIST="$ROOT/web/dist"

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
}};
const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
instance.exports.jnative_start();
const e = instance.exports;
const find = (sub) => { for (const k of Object.keys(e)) if (k.includes(sub)) return e[k]; throw new Error('missing '+sub); };
const clearDraft = find('TaskCore_clearDraft');
const append = find('TaskCore_appendDraftChar_i32');
const add = find('TaskCore_addFromDraft');
const getCount = find('TaskCore_getCount');
const getDone = find('TaskCore_getDone_i32');
const toggle = find('TaskCore_toggleDone_i32');
const titleLen = find('TaskCore_getTitleLen_i32');
const titleChar = find('TaskCore_getTitleChar_i32_i32');
const removeAt = find('TaskCore_removeAt_i32');
clearDraft();
append(66); append(117); append(121); // Buy
const id = add();
if (id !== 0) throw new Error('id='+id);
if (getCount() !== 1) throw new Error('count');
if (titleLen(0) !== 3 || titleChar(0,0) !== 66) throw new Error('title');
toggle(0);
if (getDone(0) !== 1) throw new Error('done');
removeAt(0);
if (getCount() !== 0) throw new Error('remove');
console.log('GUIDE_TASKS_WASM_OK');
EOF
