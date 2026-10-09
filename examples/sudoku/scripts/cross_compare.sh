#!/usr/bin/env bash
# Same action sequence on native core tests binary path vs fresh WASM — compare boards.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
"$ROOT/scripts/build_core_tests.sh" >/tmp/sudoku-native-tests.log
"$ROOT/scripts/verify_web.sh" >/tmp/sudoku-wasm-verify.log

# Emit board fingerprints via a tiny harness compiled from core
OUT="$REPO/build/sudoku"
python3 - "$ROOT" "$OUT/fingerprint.java" <<'PY'
from pathlib import Path
import sys
root, out = Path(sys.argv[1]), Path(sys.argv[2])
core = root.joinpath('core/SudokuCore.java').read_text().replace('public class SudokuCore','class SudokuCore',1)
# strip main
lines=core.splitlines(True); body=[]; i=0
while i<len(lines):
    if lines[i].startswith('    public static void main(String[] args)'):
        depth=0; started=False
        while i<len(lines):
            for ch in lines[i]:
                if ch=='{': depth+=1; started=True
                elif ch=='}': depth-=1
            i+=1
            if started and depth==0: break
        continue
    body.append(lines[i]); i+=1
harness='''
public class fingerprint {
  static void dump() {
    SudokuCore.exportBoardDigits();
    int n = SudokuCore.getSaveLen();
    int i = 0;
    // Prefer digit codes as decimal tokens so wasm/native compare without char print.
    String s = "";
    while (i < n) {
      if (i != 0) {
        s = s + ",";
      }
      s = s + SudokuCore.getSaveChar(i);
      i = i + 1;
    }
    System.out.println(s);
  }
  public static void main(String[] args) {
    SudokuCore.newGame(0, 0);
    int i = 0;
    while (i < 81) {
      if (SudokuCore.getGiven(i) == 0) {
        SudokuCore.selectCell(i / 9, i % 9);
        SudokuCore.inputDigit(SudokuCore.getSolutionDigit(i));
        break;
      }
      i = i + 1;
    }
    SudokuCore.undo();
    SudokuCore.redo();
    dump();
    System.out.println("conflict=" + SudokuCore.getConflict(SudokuCore.getSelected()));
    System.out.println("notesMode=" + SudokuCore.getNoteMode());
  }
}
'''
out.write_text('// AUTO\n'+''.join(body)+harness)
PY
java -jar "${JNATIVE_JAR:-$REPO/target/jnative.jar}" "$OUT/fingerprint.java" -o "$OUT/fingerprint" -O0
"$OUT/fingerprint" | tee "$OUT/native-fingerprint.txt"

node --input-type=module << EOF | tee "$OUT/wasm-fingerprint.txt"
import { readFileSync } from 'fs';
const wasm = readFileSync('$ROOT/web/dist/app.wasm');
const imports = { jnative: {
  console_log(){}, document_get_element_by_id(){return 0}, element_set_text(){},
  document_create_element(){return 0}, element_append_child(){}, create_text(){return 0},
  query_selector(){return 0}, apply_dom_patches(){}, schedule_microtask(){},
  delegate_event(){}, init_delegation(){}, register_callback(id){return id}, set_count_label(){},
}};
const { instance } = await WebAssembly.instantiate(wasm, imports);
instance.exports.jnative_start();
const e = instance.exports;
const find = (s)=>{ for (const k of Object.keys(e)) if (k.includes(s)) return e[k]; };
find('SudokuCore_newGame_i32_i32')(0,0);
let i=0; for(;i<81;i++) if(find('SudokuCore_getGiven_i32')(i)===0) break;
find('SudokuCore_selectCell_i32_i32')((i/9)|0,i%9);
find('SudokuCore_inputDigit_i32')(find('SudokuCore_getSolutionDigit_i32')(i));
find('SudokuCore_undo')();
find('SudokuCore_redo')();
find('SudokuCore_exportBoardDigits')();
let parts=[]; const n=find('SudokuCore_getSaveLen')()|0;
for (let k=0;k<n;k++) parts.push(String(find('SudokuCore_getSaveChar_i32')(k)|0));
console.log(parts.join(','));
console.log('conflict='+find('SudokuCore_getConflict_i32')(find('SudokuCore_getSelected')()));
console.log('notesMode='+find('SudokuCore_getNoteMode')());
EOF

# Compare board line
N1=$(head -1 "$OUT/native-fingerprint.txt")
W1=$(head -1 "$OUT/wasm-fingerprint.txt")
if [[ "$N1" != "$W1" ]]; then
  echo "BOARD MISMATCH native vs wasm" >&2
  echo "native=$N1" >&2
  echo "wasm  =$W1" >&2
  exit 1
fi
echo "CROSS-PLATFORM BOARD MATCH: $N1"
echo "See also -O0 fingerprint binary vs default WASM (timing excluded)."
