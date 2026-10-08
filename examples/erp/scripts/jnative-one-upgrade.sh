#!/usr/bin/env bash
# jnative-one upgrade — backup → migrate → replace binary → health hint
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PREFIX="${PREFIX:-$ROOT/build/jnative-one}"
DB_URL="${DATABASE_URL:-postgresql://jnative_erp@127.0.0.1:5432/jnative_erp}"
BACKUP_DIR="${ERP_BACKUP_PATH:-$PREFIX/var/backups}"
STAMP="$(date +%Y%m%d_%H%M%S)"

echo "== JNative One upgrade =="
if [[ ! -d "$PREFIX" ]]; then
  echo "FAIL: PREFIX missing ($PREFIX) — run install first"
  exit 2
fi

CUR="unknown"
[[ -f "$PREFIX/VERSION" ]] && CUR="$(cat "$PREFIX/VERSION")"
TARGET="$(cat "$ROOT/examples/erp/VERSION")"
echo "current=$CUR target=$TARGET"

if [[ "$CUR" == "$TARGET" ]]; then
  echo "INFO: versions identical — will still refresh binary and re-check migrations"
fi

mkdir -p "$BACKUP_DIR"
DUMP="$BACKUP_DIR/pre_upgrade_${STAMP}.dump"
echo "== backup database =="
pg_dump --format=custom --file="$DUMP" "$DB_URL"
echo "backup=$DUMP"

echo "== apply migrations (idempotent DDL) =="
for f in 001_init.sql 002_finance.sql 003_manufacturing.sql 004_enterprise.sql; do
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f "$ROOT/examples/erp/migrations/$f" >/dev/null || {
    echo "FAIL: migration $f — restore from $DUMP"
    echo "  pg_restore --clean --if-exists --dbname=\"\$DATABASE_URL\" $DUMP"
    exit 3
  }
done

echo "== rebuild & replace binary =="
(cd "$ROOT" && java -jar target/jnative.jar build --project examples/erp)
cp -f "$ROOT/examples/erp/.jnative/build/erp-server" "$PREFIX/bin/jnative-one-server"
cp -f "$ROOT/examples/erp/VERSION" "$PREFIX/VERSION"
if [[ -d "$ROOT/examples/erp/.jnative/build/browser" ]]; then
  cp -R "$ROOT/examples/erp/.jnative/build/browser/"* "$PREFIX/wasm/" || true
fi

echo "== post-upgrade integrity (optional) =="
DATABASE_URL="$DB_URL" "$PREFIX/bin/jnative-one-server" --integrity-check || {
  echo "WARN: integrity-check failed — investigate before serving traffic"
  echo "Restore: pg_restore --clean --if-exists --dbname=\"\$DATABASE_URL\" $DUMP"
  exit 4
}

echo "UPGRADE_OK current_was=$CUR now=$TARGET backup=$DUMP"
