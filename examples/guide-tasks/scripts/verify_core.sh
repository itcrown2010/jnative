#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="$REPO/build/guide-tasks"
MERGED="$OUT_DIR/merged-core-smoke.java"
BIN="$OUT_DIR/TaskCoreSmoke"
mkdir -p "$OUT_DIR"

python3 - "$ROOT" "$MERGED" <<'PY'
from pathlib import Path
import sys
root, out = Path(sys.argv[1]), Path(sys.argv[2])
core = root.joinpath("core/TaskCore.java").read_text().replace("public class TaskCore", "class TaskCore", 1)
lines = core.splitlines(True)
body = []
i = 0
while i < len(lines):
    if lines[i].startswith("    public static void main(String[] args)"):
        depth = 0
        started = False
        while i < len(lines):
            for ch in lines[i]:
                if ch == "{":
                    depth += 1
                    started = True
                elif ch == "}":
                    depth -= 1
            i += 1
            if started and depth == 0:
                break
        continue
    body.append(lines[i])
    i += 1
smoke = r'''
public class TaskCoreSmoke {
    public static void main(String[] args) {
        int ok = 1;
        TaskCore.clearFormDraft();
        TaskCore.appendTitleChar(66);
        TaskCore.appendTitleChar(117);
        TaskCore.appendTitleChar(121);
        TaskCore.setFormStatus(0);
        TaskCore.setFormDue(20261013);
        int id0 = TaskCore.addFromForm();
        if (id0 != 0 || TaskCore.getCount() != 1) { ok = 0; }
        if (TaskCore.getDueYmd(0) != 20261013) { ok = 0; }
        if (TaskCore.setFormDue(20260230) != 0) { ok = 0; }
        if (TaskCore.getLastError() != 6) { ok = 0; }
        TaskCore.clearFormDraft();
        TaskCore.appendTitleChar(87);
        TaskCore.setFormStatus(1);
        if (TaskCore.validateForm() != 0 || TaskCore.getLastError() != 7) { ok = 0; }
        TaskCore.appendNoteChar(110);
        TaskCore.appendNoteChar(111);
        TaskCore.appendNoteChar(116);
        TaskCore.appendNoteChar(101);
        int id1 = TaskCore.addFromForm();
        if (id1 != 1) { ok = 0; }
        TaskCore.removeById(id0);
        if (TaskCore.getCount() != 1 || TaskCore.getId(0) != 1) { ok = 0; }
        int n = TaskCore.prepareSave();
        String s = "";
        int i = 0;
        while (i < n) { s = s + TaskCore.unitToString(TaskCore.getSaveChar(i)); i = i + 1; }
        TaskCore.clearAll();
        if (TaskCore.loadSave(s) == 0) { ok = 0; }
        if (TaskCore.getCount() != 1 || TaskCore.getStatus(0) != 1) { ok = 0; }
        if (SyncCore.mergeDecision(1, 2, 1) != 2) { ok = 0; }
        if (SyncCore.mergeDecision(1, 3, 1) != 3) { ok = 0; }
        if (SyncCore.classifyHttpError(503, 0) != 1) { ok = 0; }
        if (ok == 0) {
            System.out.println("FAIL err=" + String.valueOf(TaskCore.getLastError()));
        } else {
            System.out.println("PASS");
        }
    }
}
'''
sync = root.joinpath("core/SyncCore.java").read_text().replace("public class SyncCore", "class SyncCore", 1)
slines = sync.splitlines(True)
sbody = []
i = 0
while i < len(slines):
    if slines[i].startswith("    public static void main(String[] args)"):
        depth = 0
        started = False
        while i < len(slines):
            for ch in slines[i]:
                if ch == "{":
                    depth += 1
                    started = True
                elif ch == "}":
                    depth -= 1
            i += 1
            if started and depth == 0:
                break
        continue
    sbody.append(slines[i])
    i += 1
out.write_text("".join(sbody) + "".join(body) + smoke)
PY

java -jar "$JAR" "$MERGED" -o "$BIN" -O0
out="$("$BIN")"
echo "$out"
[[ "$out" == "PASS" ]]
echo "GUIDE_TASKS_CORE_OK"
