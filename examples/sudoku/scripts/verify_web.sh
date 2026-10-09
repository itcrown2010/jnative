#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build_web.sh"
DIST="$ROOT/web/dist"
export PATH="/opt/homebrew/opt/wabt/bin:/usr/local/opt/wabt/bin:$PATH"

node --input-type=module << EOF
import { readFileSync } from 'fs';
const wasmBytes = readFileSync('$DIST/app.wasm');
const imports = { jnative: {
  console_log() {}, document_get_element_by_id(){return 0}, element_set_text(){},
  document_create_element(){return 0}, element_append_child(){}, create_text(){return 0},
  query_selector(){return 0}, apply_dom_patches(){}, schedule_microtask(){},
  delegate_event(){}, init_delegation(){}, register_callback(id){return id}, set_count_label(){},
}};
const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
instance.exports.jnative_start();
const e = instance.exports;
const find = (sub) => { for (const k of Object.keys(e)) if (k.includes(sub)) return e[k]; throw new Error('missing '+sub); };
const newGame = find('SudokuCore_newGame_i32_i32');
const getValue = find('SudokuCore_getValue_i32');
const getGiven = find('SudokuCore_getGiven_i32');
const selectCell = find('SudokuCore_selectCell_i32_i32');
const inputDigit = find('SudokuCore_inputDigit_i32');
const getConflict = find('SudokuCore_getConflict_i32');
const clearCell = find('SudokuCore_clearCell');
const undo = find('SudokuCore_undo');
const isComplete = find('SudokuCore_isComplete');
const getSol = find('SudokuCore_getSolutionDigit_i32');
const err = find('SudokuCore_getLastError');

if (newGame(0,0) !== 1) throw new Error('newGame failed err='+err());
if (getValue(0) !== 5) throw new Error('expected given 5 at 0, got '+getValue(0));
// Fixed cell reject
selectCell(0, 0);
if (inputDigit(9) !== 0 || (err()|0) !== 2) throw new Error('fixed cell should reject');
// Fill to complete (unique solution path)
for (let i = 0; i < 81; i++) {
  if (getGiven(i) === 0) {
    selectCell((i/9)|0, i%9);
    const r = inputDigit(getSol(i));
    if (r !== 1) throw new Error('input failed at '+i+' err='+err());
  }
}
if (isComplete() !== 1) throw new Error('expected complete');
// Fresh game + conflict via same-row peer
if (newGame(0,0) !== 1) throw new Error('newGame2 failed');
let empty = 0;
for (; empty < 81; empty++) if (getGiven(empty) === 0) break;
const row = (empty / 9) | 0;
let peerDigit = 0;
for (let c = 0; c < 9; c++) {
  const v = getValue(row * 9 + c) | 0;
  if (v !== 0) { peerDigit = v; break; }
}
selectCell(row, empty % 9);
const put = inputDigit(peerDigit);
const conf = getConflict(empty)|0;
if (put !== 1) throw new Error('conflict input failed err='+err());
if (conf !== 1) throw new Error('expected conflict got '+conf+' peer='+peerDigit+' empty='+empty);
clearCell();
undo();
console.log('PASS wasm sudoku core sequence');
console.log('ALL WEB WASM CHECKS PASSED');
EOF
