#!/usr/bin/env bash
# Author: 张晓达
# Wonder Campus E2E A/B + negatives on isolated sqlite.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BIN="$ROOT/examples/wonder-campus/.jnative/build/wonder-server"
DB_DIR="$ROOT/build/wonder-campus"
DB="$DB_DIR/e2e.db"
mkdir -p "$DB_DIR"
if [[ ! -x "$BIN" ]]; then
  (cd "$ROOT" && java -jar target/jnative.jar build --project examples/wonder-campus)
fi
rm -f "$DB" "${DB}-wal" "${DB}-shm"
echo "== wonder-campus e2e on $DB =="
DATABASE_URL="sqlite:$DB" WC_ALLOW_DEMO_LOGIN=1 WC_ENV=dev "$BIN" --e2e-test
echo "OK wonder-campus e2e-accept"
