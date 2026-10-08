#!/usr/bin/env bash
# Debug the real purchase chain on an isolated SQLite practice DB (macOS arm64).
# Flow: createDraft → approve → postGoodsReceipt (idempotent keys in ErpLogicTest).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
ERP="$ROOT/examples/erp"
BIN="$ERP/.jnative/build/erp-server"
JAR="$ROOT/target/jnative.jar"
FMT="$ROOT/tools/lldb/jnative_formatters.py"
DB="$ROOT/build/erp/debug-practice.db"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi
command -v lldb >/dev/null || { echo "SKIP: lldb missing"; exit 2; }

mkdir -p "$ROOT/build/erp"
pkill -f "$BIN" 2>/dev/null || true

echo "== debug build ERP server (browser target may fail; server is enough) =="
set +e
(cd "$ROOT" && java -jar "$JAR" build --project examples/erp --debug) \
  >"$ROOT/build/erp/debug-build.log" 2>&1
set -e
if [[ ! -x "$BIN" ]]; then
  echo "FAIL: erp-server missing after debug build"
  tail -40 "$ROOT/build/erp/debug-build.log" || true
  exit 1
fi
[[ -d "${BIN}.dSYM" ]] || { echo "FAIL: missing ${BIN}.dSYM"; exit 1; }
[[ -f "${BIN}.jn-debug.json" ]] || {
  echo "WARN: publishing catalog from staging"
  if ls "$ERP/.jnative/build/"erp-server*.jn-debug.json >/dev/null 2>&1; then
    cp -f "$ERP/.jnative/build/"erp-server*.jn-debug.json "${BIN}.jn-debug.json" || true
  fi
}

# Prefer original-file breakpoints; also set symbol BPs as fallback.
LINE_CREATE="$(grep -n 'static PurchaseOrderDto createDraft' "$ERP/server/ErpPurchasing.java" | head -1 | cut -d: -f1)"
LINE_APPROVE="$(grep -n 'static PurchaseOrderDto approve' "$ERP/server/ErpPurchasing.java" | head -1 | cut -d: -f1)"
LINE_GR="$(grep -n 'static PostResultDto postGoodsReceipt' "$ERP/server/ErpPosting.java" | head -1 | cut -d: -f1)"

rm -f "$DB"
export DATABASE_URL="sqlite:$DB"
export ERP_LISTEN_PORT=18799
echo "== practice DB: $DATABASE_URL =="

LOG="$(mktemp)"
set +e
lldb -b \
  -o "settings set target.process.stop-on-sharedlibrary-events false" \
  -o "command script import $FMT" \
  -o "settings set -- target.run-args \"--logic-test\"" \
  -o "breakpoint set --file ErpPurchasing.java --line $LINE_CREATE" \
  -o "breakpoint set --file ErpPurchasing.java --line $LINE_APPROVE" \
  -o "breakpoint set --file ErpPosting.java --line $LINE_GR" \
  -o "breakpoint set --name _JN_ErpPurchasing_createDraft_i64_i64_PCArrayList_CObject" \
  -o "breakpoint set --name _JN_ErpPurchasing_approve_i64_i64" \
  -o "breakpoint set --name _JN_ErpPosting_postGoodsReceipt_i64_CPostGoodsReceiptRequest" \
  -o "run" \
  -o "thread step-over" \
  -o "frame variable companyId supplierId" \
  -o "bt 5" \
  -o "continue" \
  -o "thread step-over" \
  -o "frame variable companyId poId" \
  -o "bt 5" \
  -o "continue" \
  -o "thread step-over" \
  -o "frame variable companyId" \
  -o "bt 5" \
  -o "breakpoint delete 3" \
  -o "breakpoint delete 6" \
  -o "continue" \
  -- "$BIN" >"$LOG" 2>&1
rc=$?
set -e
tail -n 220 "$LOG"
echo "(full log: $LOG)"

if grep -qiE 'Operation not permitted|cannot attach|developer mode' "$LOG"; then
  echo "SKIP: debugger attach blocked"; exit 2
fi

# Prefer original-file stop locations when DI mapping works
if grep -E "ErpPurchasing\.java:$LINE_CREATE" "$LOG" >/dev/null; then
  echo "OK: createDraft stopped in ErpPurchasing.java:$LINE_CREATE"
elif grep -E '_JN_ErpPurchasing_createDraft' "$LOG" >/dev/null; then
  echo "WARN: createDraft hit via symbol (DI file mapping incomplete)"
else
  echo "FAIL: createDraft not hit"; exit 1
fi

if grep -E "ErpPurchasing\.java:($LINE_APPROVE|$((LINE_APPROVE+1)))" "$LOG" >/dev/null; then
  echo "OK: approve stopped in ErpPurchasing.java"
elif grep -E '_JN_ErpPurchasing_approve' "$LOG" >/dev/null; then
  echo "WARN: approve hit via symbol"
else
  echo "FAIL: approve not hit"; exit 1
fi

if grep -E "ErpPosting\.java:$LINE_GR" "$LOG" >/dev/null; then
  echo "OK: postGoodsReceipt stopped in ErpPosting.java"
elif grep -E '_JN_ErpPosting_postGoodsReceipt' "$LOG" >/dev/null; then
  echo "WARN: postGoodsReceipt hit via symbol"
else
  echo "FAIL: postGoodsReceipt not hit"; exit 1
fi

grep -E '\(long\) poId = 1' "$LOG" >/dev/null \
  || { echo "FAIL: poId not observed as 1 after step"; exit 1; }

grep -E '(^PASS$|PASS[[:space:]]*$)' "$LOG" >/dev/null \
  || { echo "FAIL: logic-test did not print PASS after continue"; exit 1; }

[[ -f "$DB" ]] || { echo "FAIL: practice DB missing"; exit 1; }
if command -v sqlite3 >/dev/null 2>&1; then
  po_n=$(sqlite3 "$DB" "SELECT COUNT(*) FROM purchase_orders;")
  echo "DB purchase_orders=$po_n"
  [[ "$po_n" -ge 1 ]] || { echo "FAIL: no purchase_orders"; exit 1; }
fi

echo "PASS: ERP purchase chain debug on practice SQLite verified"
echo "NOTE: HTTP pause may time out clients; this verify uses in-process --logic-test"
echo "NOTE: ErpLogicTest idempotency keys avoid duplicate posting on resume"
exit 0
