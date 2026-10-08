#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
mkdir -p build/erp

echo "== ERP PG e2e =="
docker compose -f examples/erp/docker-compose.yml up -d --wait 2>/dev/null || docker compose -f examples/erp/docker-compose.yml up -d
sleep 2

export DATABASE_URL="${DATABASE_URL:-postgresql://jnative_erp:jnative_erp@127.0.0.1:5432/jnative_erp}"

java -jar target/jnative.jar build --project examples/erp --release 2>&1 | tail -20

echo "== logic test (PG) =="
DATABASE_URL="$DATABASE_URL" examples/erp/.jnative/build/erp-server --logic-test
echo "== server smoke =="
DATABASE_URL="$DATABASE_URL" timeout 3 examples/erp/.jnative/build/erp-server || true

echo "PG_E2E_DONE"
