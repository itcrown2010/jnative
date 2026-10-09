#!/usr/bin/env bash
# Author: 张晓达
# Pilot 12-step acceptance (SQLite by default; set DATABASE_URL for PG).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

mkdir -p build/erp
DB="${DATABASE_URL:-sqlite:./build/erp/pilot-accept.db}"
rm -f build/erp/pilot-accept.db

echo "== build erp-server =="
java -jar target/jnative.jar build --project examples/erp

echo "== pilot-test DATABASE_URL=$DB =="
DATABASE_URL="$DB" examples/erp/.jnative/build/erp-server --pilot-test

echo "== baseline logic-test (smoke) =="
DATABASE_URL="sqlite:./build/erp/pilot-logic.db" examples/erp/.jnative/build/erp-server --logic-test

echo "PILOT_ACCEPT_SCRIPT_OK"
