#!/usr/bin/env bash
# First-start Setup Wizard (CLI) — Environment → Admin → Company → Completion
# WebUI wizard deferred; this is the productization v1 path.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PREFIX="${PREFIX:-$ROOT/build/jnative-one}"
LOCK="${ERP_SETUP_LOCK:-$PREFIX/var/setup.completed}"
DB_URL="${DATABASE_URL:-postgresql://jnative_erp@127.0.0.1:5432/jnative_erp}"

echo "== JNative One Setup Wizard =="

if [[ -f "$LOCK" ]]; then
  echo "REFUSED: setup already completed ($LOCK)"
  echo "Re-init of super-admin is forbidden after first install."
  exit 2
fi

# Concurrency: exclusive lock directory (portable; no flock dependency)
LOCKDIR="$PREFIX/var"
mkdir -p "$LOCKDIR"
if ! mkdir "$LOCKDIR/setup.lock.d" 2>/dev/null; then
  echo "FAIL: another setup is in progress"
  exit 3
fi
trap 'rmdir "$LOCKDIR/setup.lock.d" 2>/dev/null || true' EXIT

echo "1) Environment Check"
uname -s; uname -m
psql "$DB_URL" -c "SELECT version()" | head -3
psql "$DB_URL" -c "SELECT 1" >/dev/null

echo "2) Admin Account (hashed by server Password API on first seed)"
ADMIN_EMAIL="${SETUP_ADMIN_EMAIL:-admin@localhost}"
echo "admin_email=$ADMIN_EMAIL (password set via seed / change after login — not echoed)"

echo "3) Company Setup"
COMPANY_CODE="${SETUP_COMPANY_CODE:-DEMO}"
COMPANY_NAME="${SETUP_COMPANY_NAME:-Demo Trading Company}"
echo "company=$COMPANY_CODE / $COMPANY_NAME"

echo "4) Fiscal Year / Currency / Warehouse defaults"
echo "fiscal_year=${SETUP_FISCAL_YEAR:-$(date +%Y)} currency=${SETUP_CURRENCY:-USD} warehouse=WH1"

echo "5) User Roles — platform RBAC seeded by ErpPlatformAuth.ensureSeed"

echo "6) Data Initialization"
# Ensure migrations + start once to seed (demo company via ErpSeed when code JNAT or custom)
for f in 001_init.sql 002_finance.sql 003_manufacturing.sql 004_enterprise.sql; do
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f "$ROOT/examples/erp/migrations/$f" >/dev/null
done

if [[ "${SETUP_LOAD_DEMO:-1}" == "1" ]]; then
  export ERP_DEMO_MODE=1
  echo "Loading Demo Trading Company seed via server boot (ERP_DEMO_MODE=1)..."
  if [[ -x "$PREFIX/bin/jnative-one-server" ]]; then
    # short boot: seed on start then exit is not built-in — use seed by running --version after ensure
    # Start server briefly is heavy; call install path seed by running integrity after manual seed trigger:
    echo "HINT: start server once to run ErpSeed.ensure() for JNAT demo books"
  fi
fi

echo "7) Completion"
mkdir -p "$(dirname "$LOCK")"
{
  echo "completed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "company_code=$COMPANY_CODE"
  echo "admin_email=$ADMIN_EMAIL"
} > "$LOCK"
chmod 600 "$LOCK"

echo "SETUP_OK lock=$LOCK"
echo "Initialization interfaces must not re-create super-admin after this lock exists."
