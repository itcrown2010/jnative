#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BIN="$ROOT/examples/erp/.jnative/build/erp-server"
DB="$ROOT/build/erp/sales-accept.db"
mkdir -p "$ROOT/build/erp"
if [[ ! -x "$BIN" ]]; then
  (cd "$ROOT" && cp examples/erp/jnative.project.server-only examples/erp/jnative.project \
    && java -jar target/jnative.jar build --project examples/erp \
    && git checkout -- examples/erp/jnative.project)
fi
rm -f "$DB"
echo "== purchase dependency recheck =="
DATABASE_URL="sqlite:$ROOT/build/erp/logic-recheck.db" rm -f "$ROOT/build/erp/logic-recheck.db"
DATABASE_URL="sqlite:$ROOT/build/erp/logic-recheck.db" "$BIN" --logic-test
echo "== sales-test on $DB =="
DATABASE_URL="sqlite:$DB" "$BIN" --sales-test
echo "OK sales-accept"
