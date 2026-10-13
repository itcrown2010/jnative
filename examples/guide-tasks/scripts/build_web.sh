#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
if [[ -n "${JNATIVE_JAR:-}" && -f "${JNATIVE_JAR}" ]]; then
  JAR="$JNATIVE_JAR"
elif [[ -f "$REPO/target/jnative.jar" ]]; then
  JAR="$REPO/target/jnative.jar"
elif [[ -f "$ROOT/../../target/jnative.jar" ]]; then
  JAR="$ROOT/../../target/jnative.jar"
else
  JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
fi
DIST="$ROOT/web/dist"
STAGE="$ROOT/.build-staging/web"
MERGED="$STAGE/merged-core.java"

export PATH="/opt/homebrew/opt/wabt/bin:/usr/local/opt/wabt/bin:$PATH"

rm -rf "$STAGE"
mkdir -p "$STAGE"

python3 - "$ROOT/core/TaskCore.java" "$ROOT/core/SyncCore.java" "$MERGED" <<'PY'
from pathlib import Path
import sys
task, sync, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
t = task.read_text().replace("public class TaskCore", "class TaskCore", 1)
s = sync.read_text().replace("public class SyncCore", "class SyncCore", 1)
# Drop SyncCore.main; keep TaskCore.main and extend keep-block refs via appended stub main in SyncCore removed
lines = s.splitlines(True)
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
# Inject SyncCore keep refs into TaskCore.main keep block (must stay reachable for wasm exports)
marker = "            lastError = getLastError();\n            lastError = clearAll();\n"
inject = (
    "            lastError = getLastError();\n"
    "            lastError = clearAll();\n"
    "            lastError = SyncCore.mergeDecision(0, 1, 1);\n"
    "            lastError = SyncCore.mergeDecision(1, 2, 1);\n"
    "            lastError = SyncCore.mergeDecision(1, 3, 1);\n"
    "            lastError = SyncCore.stateAfterLocalMutation();\n"
    "            lastError = SyncCore.stateBeginSync(2);\n"
    "            lastError = SyncCore.stateAfterAck();\n"
    "            lastError = SyncCore.stateAfterConflict();\n"
    "            lastError = SyncCore.stateAfterTempFail();\n"
    "            lastError = SyncCore.classifyHttpError(503, 0);\n"
    "            lastError = SyncCore.backoffMs(2);\n"
    "            lastError = SyncCore.maxAttempts();\n"
    "            lastError = SyncCore.isDeleteEditConflict(3, 1, 2, 0);\n"
    "            lastError = SyncCore.getLastError();\n"
)
if marker not in t:
    raise SystemExit("TaskCore keep-block marker missing; cannot inject SyncCore refs")
t = t.replace(marker, inject)
out.write_text("".join(body) + "\n" + t)
PY

echo "==> compiling merged TaskCore+SyncCore → wasm32-browser"
java -jar "$JAR" build --target wasm32-browser --keep-temps "$MERGED" -o "$STAGE"
for f in app.wasm app.js; do
  [[ -f "$STAGE/$f" ]] || { echo "missing $f"; exit 1; }
done

cp "$ROOT/web/index.html" "$STAGE/index.html"
cp "$ROOT/web/app-shell.js" "$STAGE/app-shell.js"
cp "$ROOT/web/tasks.css" "$STAGE/tasks.css"
mkdir -p "$STAGE/lib" "$STAGE/api"
cp "$ROOT/web/lib/"*.js "$STAGE/lib/"
cp "$ROOT/web/api/hint.json" "$STAGE/api/hint.json"
if [[ -f "$ROOT/web/sw.js" ]]; then
  cp "$ROOT/web/sw.js" "$STAGE/sw.js"
fi

rm -rf "$DIST"
mkdir -p "$DIST"
cp -R "$STAGE"/. "$DIST"/
rm -f "$DIST/merged-core.java" "$DIST/app.wat"
rm -rf "$DIST/.jnative"
echo "==> web dist ready: $DIST"
ls -la "$DIST" "$DIST/lib"
