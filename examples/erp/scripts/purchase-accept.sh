#!/usr/bin/env bash
# Author: 张晓达
# Isolated DB acceptance: PO 10 → GR 4+6 + provisional AP + idempotency.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BIN="$ROOT/examples/erp/.jnative/build/erp-server"
DB="$ROOT/build/erp/purchase-accept.db"
mkdir -p "$ROOT/build/erp"
if [[ ! -x "$BIN" ]]; then
  (cd "$ROOT" && cp examples/erp/jnative.project.server-only examples/erp/jnative.project \
    && java -jar target/jnative.jar build --project examples/erp \
    && git -C "$ROOT" checkout -- examples/erp/jnative.project 2>/dev/null \
       || cp examples/erp/jnative.project.fullbak examples/erp/jnative.project 2>/dev/null || true)
fi
rm -f "$DB"
echo "== logic-test on $DB =="
DATABASE_URL="sqlite:$DB" "$BIN" --logic-test
echo "== DB spot-check =="
sqlite3 "$DB" <<'SQL'
SELECT 'PO', doc_no, status FROM purchase_orders ORDER BY id LIMIT 5;
SELECT 'GR', doc_no, status FROM goods_receipts ORDER BY id LIMIT 5;
SELECT 'AP', invoice_number, status, total_amount, source_gr_id FROM ap_invoices WHERE status='PROVISIONAL' ORDER BY id LIMIT 5;
SELECT 'TXN', txn_type, quantity_delta, ref_doc_type || '-' || ref_doc_id FROM inventory_transactions ORDER BY id LIMIT 8;
SELECT 'BAL', quantity_on_hand FROM inventory_balances LIMIT 3;
SQL
echo "OK purchase-accept"
