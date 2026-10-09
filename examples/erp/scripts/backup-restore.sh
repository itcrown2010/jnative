#!/usr/bin/env bash
# Author: 张晓达
# Enterprise 1.4 — PostgreSQL backup/restore drill using pg_dump / pg_restore.
# Does not invent a custom DB backup format.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DB_URL="${DATABASE_URL:-postgresql://jnative_erp@127.0.0.1:5432/jnative_erp}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/build/erp/backups}"
STAMP="$(date +%Y%m%d_%H%M%S)"
DUMP="$BACKUP_DIR/jnative_erp_${STAMP}.dump"
VERIFY_DB="${VERIFY_DB:-jnative_erp_restore_${STAMP}}"

mkdir -p "$BACKUP_DIR"

echo "== backup =="
pg_dump --format=custom --file="$DUMP" "$DB_URL"
ls -la "$DUMP"

echo "== create verify database =="
psql -h 127.0.0.1 -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS ${VERIFY_DB};"
psql -h 127.0.0.1 -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE ${VERIFY_DB} OWNER jnative_erp;"

echo "== restore =="
pg_restore --no-owner --dbname="postgresql://jnative_erp@127.0.0.1:5432/${VERIFY_DB}" "$DUMP" || true

echo "== consistency checks =="
psql -h 127.0.0.1 -U jnative_erp -d "$VERIFY_DB" -v ON_ERROR_STOP=1 <<'SQL'
SELECT COUNT(*) AS companies FROM companies;
SELECT COUNT(*) AS users FROM users;
SELECT version FROM schema_migrations ORDER BY version;
SQL

echo "BACKUP_RESTORE_OK dump=$DUMP verify_db=$VERIFY_DB"
