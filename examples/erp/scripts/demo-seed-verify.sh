#!/bin/bash
# Verify demo book seed via HTTP lists. Requires ERP_DEMO_MODE=1 server on :18700.
set -euo pipefail
BASE="${1:-http://127.0.0.1:18700}"
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

echo "== ready =="
curl -sf "$BASE/ready" | tee /tmp/demo-ready.json
echo

echo "== demo-available =="
curl -sf "$BASE/api/auth/demo-available"
echo

echo "== demo-enter =="
curl -sf -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/api/auth/demo-enter" \
  -H 'Content-Type: application/json' -d '{}' | tee /tmp/demo-enter.json
echo
CSRF=$(python3 -c "import json; print(json.load(open('/tmp/demo-enter.json')).get('csrfToken',''))")

auth() {
  curl -sf -c "$COOKIE" -b "$COOKIE" -H "X-CSRF-Token: $CSRF" "$@"
}

check_list() {
  local path="$1"
  local min="$2"
  local label="$3"
  local body
  body=$(auth "$BASE$path")
  local n
  n=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(len(d) if isinstance(d,list) else -1)" <<<"$body")
  if [[ "$n" -lt "$min" ]]; then
    echo "FAIL $label $path count=$n min=$min"
    echo "$body" | head -c 400
    echo
    exit 1
  fi
  echo "OK $label count=$n"
}

echo "== book =="
auth "$BASE/api/erp/book"
echo

check_list /api/erp/partners?kind=CUSTOMER 10 customers
check_list /api/erp/partners?kind=SUPPLIER 8 suppliers
check_list /api/erp/items 30 items
check_list /api/erp/categories 3 categories
check_list /api/erp/units 3 units
check_list /api/erp/warehouses 3 warehouses
check_list /api/erp/employees 3 employees
check_list /api/erp/purchaseRequests 3 purchaseRequests
check_list /api/erp/purchaseOrders 1 purchaseOrders
check_list /api/erp/goodsReceipts 2 goodsReceipts
check_list /api/erp/salesOrders 1 salesOrders
check_list /api/erp/deliveries 2 deliveries
check_list /api/erp/finance/ap 1 ap
check_list /api/erp/finance/ar 1 ar
check_list /api/erp/finance/outgoing-payments 1 outgoingPayments
check_list /api/erp/finance/incoming-payments 1 incomingPayments
check_list /api/erp/inventory 1 inventory
check_list /api/erp/inventory/transactions 1 inventoryTxns

echo "== dashboard =="
auth "$BASE/api/erp/dashboard"
echo
echo "ALL CHECKS PASSED"
