#!/usr/bin/env bash
# HTTP smoke test for /api/erp/b1/* against a running demo-mode server.
# Usage: b1-smoke.sh [host:port]   (server must run with ERP_DEMO_MODE=1)
set -u
HP="${1:-localhost:18791}"
B="http://$HP/api/erp/b1"
CJ="$(mktemp)"
FAIL=0
T=$(curl -s -m 8 -c "$CJ" -X POST "http://$HP/api/auth/demo-enter" -H 'content-type: application/json' -d '{}' | sed 's/.*csrfToken":"\([^"]*\)".*/\1/')
[ -n "$T" ] || { echo "FAIL demo-enter"; exit 1; }
g() { curl -s -m 10 -b "$CJ" "$B$1"; }
p() { curl -s -m 10 -b "$CJ" -X POST "$B$1" -H 'content-type: application/json' -H "X-CSRF-Token: $T" -d "$2"; }
ok() { # name, haystack, needle
  case "$2" in *"$3"*) echo "OK   $1";; *) echo "FAIL $1 :: $2" | head -c 400; echo; FAIL=1;; esac
}
ok "tax list"    "$(g /m/taxCodes)" '"code":"VAT'
ok "currencies"  "$(g /m/currencies)" '"code"'
ok "partners"    "$(g '/m/partners?limit=2')" '"code"'
ok "items"       "$(g '/m/items?limit=2')" '"sku"'
R=$(p /m/taxCodes '{"code":"SMK5","name":"Smoke 5","rateBps":500}')
case "$R" in *SMK5*|*DUPLICATE_KEY*) echo "OK   tax create";; *) echo "FAIL tax create :: $R"; FAIL=1;; esac
ok "pricing"     "$(g '/pricing?partnerId=1&itemId=1&qty=1')" 'unitPriceCents'
ok "credit"      "$(g /credit/1)" 'creditLimit'
ok "doc list"    "$(g /docs/SQ)" '['
CUST=$(g '/m/partners?kind=CUSTOMER&limit=1' | sed 's/^\[{"id":\([0-9]*\).*/\1/')
SQ=$(p /docs/SQ "{\"partnerId\":$CUST,\"lines\":[{\"itemId\":1,\"quantity\":2,\"unitPriceCents\":1000}]}")
ok "SQ create"   "$SQ" '"docNo"'
SQID=$(echo "$SQ" | sed 's/^{"id":\([0-9]*\).*/\1/')
ok "SQ submit"   "$(p /docs/SQ/$SQID/submit '{}')" '"status":"OPEN"'
CB="{\"fromType\":\"SQ\",\"fromId\":$SQID}"
ok "SQ->SO copy" "$(p /copy/SO "$CB")" 'targetId'
echo "smoke done fail=$FAIL"
rm -f "$CJ"
exit $FAIL
