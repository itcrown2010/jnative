#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SCH="$ROOT/examples/scheduler"
OUT="$ROOT/build/scheduler"
JAR="$ROOT/target/jnative.jar"
mkdir -p "$OUT" "$ROOT/tmp/sch-concat"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi

cat \
  "$SCH/shared/Enums.java" \
  "$SCH/shared/Dtos.java" \
  "$SCH/shared/Validation.java" \
  "$SCH/shared/Recurrence.java" \
  "$SCH/shared/EventLayout.java" \
  "$SCH/server/SchJson.java" \
  "$SCH/server/SchedulerServer.java" \
  > "$ROOT/tmp/sch-concat/App.java"
python3 - <<'PY'
from pathlib import Path
p = Path("/Users/zhangxiaoda/jnative/tmp/sch-concat/App.java")
# path may differ — fix below
PY
python3 -c "
from pathlib import Path
p = Path(r'$ROOT/tmp/sch-concat/App.java')
p.write_text(p.read_text().replace('public class SchedulerServer', 'public class App'))
"

java -jar "$JAR" "$ROOT/tmp/sch-concat/App.java" -o "$OUT/scheduler-server"

# unit tests
cat \
  "$SCH/shared/Enums.java" \
  "$SCH/shared/Dtos.java" \
  "$SCH/shared/Validation.java" \
  "$SCH/shared/Recurrence.java" \
  "$SCH/shared/EventLayout.java" \
  "$SCH/tests/SchedulerLogicTest.java" \
  > "$ROOT/tmp/sch-concat/TestApp.java"
# TestApp already has public class SchedulerLogicTest
java -jar "$JAR" "$ROOT/tmp/sch-concat/TestApp.java" -o "$OUT/scheduler-tests"
"$OUT/scheduler-tests"

# browser wasm (best-effort)
java -jar "$JAR" build --project "$SCH" || true
if [[ -f "$SCH/.jnative/build/browser/app.wasm" ]]; then
  cp -f "$SCH/.jnative/build/browser/app.wasm" "$OUT/" || true
  cp -f "$SCH/.jnative/build/browser/app.js" "$OUT/" || true
elif [[ -f "$SCH/.jnative/build/browser.tmp.1/app.wasm" ]]; then
  cp -f "$SCH/.jnative/build/browser.tmp.1/"* "$OUT/" 2>/dev/null || true
fi

echo "Starting scheduler-server on :18400"
exec "$OUT/scheduler-server"
