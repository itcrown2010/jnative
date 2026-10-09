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
        int id = TaskCore.addTaskString("Buy milk");
        int ok = 1;
        if (id != 0 || TaskCore.getCount() != 1 || TaskCore.getDone(0) != 0) { ok = 0; }
        TaskCore.toggleDone(0);
        if (TaskCore.getDone(0) != 1) { ok = 0; }
        int n = TaskCore.prepareSave();
        String s = "";
        int i = 0;
        while (i < n) { s = s + TaskCore.unitToString(TaskCore.getSaveChar(i)); i = i + 1; }
        TaskCore.clearAll();
        if (TaskCore.loadSave(s) == 0) { ok = 0; }
        if (TaskCore.getCount() != 1) { ok = 0; }
        if (ok == 0) {
            System.out.println("FAIL err=" + String.valueOf(TaskCore.getLastError()));
        } else {
            System.out.println("PASS");
        }
    }
}
'''
out.write_text("".join(body) + smoke)
PY

java -jar "$JAR" "$MERGED" -o "$BIN" -O0
out="$("$BIN")"
echo "$out"
[[ "$out" == "PASS" ]]
echo "GUIDE_TASKS_CORE_OK"
