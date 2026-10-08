#!/usr/bin/env bash
# jnative-one install — Productization 1.5 (FEATURE FREEZE)
# Installs application files + runs migrations. Does NOT claim Linux runtime VERIFIED.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PREFIX="${PREFIX:-$ROOT/build/jnative-one}"
CONF_DIR="${CONF_DIR:-$PREFIX/etc}"
DATA_DIR="${DATA_DIR:-$PREFIX/var}"
ERP_SRC="$ROOT/examples/erp"

echo "== JNative One install =="
echo "ROOT=$ROOT PREFIX=$PREFIX"

OS="$(uname -s)"
ARCH="$(uname -m)"
echo "OS=$OS ARCH=$ARCH"

case "$OS" in
  Linux)
    echo "WARN: Linux is the intended server target but native epoll/runtime is NOT VERIFIED (see docs/erp-release-blockers.md ERP-P0-01)."
    if [[ "$ARCH" != "x86_64" && "$ARCH" != "amd64" ]]; then
      echo "FAIL: expected x86_64 on Linux first release; got $ARCH"
      exit 2
    fi
    ;;
  Darwin)
    echo "INFO: macOS is development/demo only — not the first commercial server commitment."
    ;;
  *)
    echo "FAIL: unsupported OS $OS"
    exit 2
    ;;
esac

command -v psql >/dev/null || { echo "FAIL: psql not found"; exit 3; }
command -v pg_dump >/dev/null || { echo "FAIL: pg_dump not found"; exit 3; }

# Disk space (require ≥500MB free on PREFIX parent)
AVAIL_KB=$(df -k "$ROOT" | awk 'NR==2{print $4}')
if [[ "${AVAIL_KB:-0}" -lt 512000 ]]; then
  echo "FAIL: insufficient disk space (need ~500MB+)"
  exit 4
fi

PORT="${ERP_LISTEN_PORT:-18700}"
if command -v lsof >/dev/null 2>&1; then
  if lsof -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "FAIL: port $PORT already in use"
    exit 5
  fi
fi

mkdir -p "$PREFIX/bin" "$PREFIX/wasm" "$PREFIX/public" "$PREFIX/migrations" "$PREFIX/docs" \
  "$CONF_DIR" "$DATA_DIR/storage" "$DATA_DIR/backups" "$DATA_DIR/logs"

if [[ ! -x "$ERP_SRC/.jnative/build/erp-server" ]]; then
  echo "== build erp-server =="
  (cd "$ROOT" && java -jar target/jnative.jar build --project examples/erp)
fi

cp -f "$ERP_SRC/.jnative/build/erp-server" "$PREFIX/bin/jnative-one-server"
chmod +x "$PREFIX/bin/jnative-one-server"
if [[ -d "$ERP_SRC/.jnative/build/browser" ]]; then
  cp -R "$ERP_SRC/.jnative/build/browser/"* "$PREFIX/wasm/" || true
fi
if [[ -d "$ERP_SRC/public" ]]; then
  cp -R "$ERP_SRC/public/"* "$PREFIX/public/" || true
fi
cp -f "$ERP_SRC/migrations/"*.sql "$PREFIX/migrations/"
cp -f "$ERP_SRC/VERSION" "$PREFIX/VERSION"
cp -f "$ERP_SRC/deploy/jnative-one.conf.example" "$CONF_DIR/jnative-one.conf.example"
cp -f "$ERP_SRC/deploy/env.example" "$CONF_DIR/env.example"

BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
TARGET="${JNATIVE_ONE_TARGET:-$OS-$ARCH}"
{
  echo "version=$(cat "$PREFIX/VERSION")"
  echo "build_time=$BUILD_TIME"
  echo "target=$TARGET"
  echo "host_os=$OS"
  echo "host_arch=$ARCH"
} > "$PREFIX/BUILD_METADATA"

(
  cd "$PREFIX"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 bin/jnative-one-server > CHECKSUMS.sha256
    [[ -f wasm/app.wasm ]] && shasum -a 256 wasm/app.wasm >> CHECKSUMS.sha256
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum bin/jnative-one-server > CHECKSUMS.sha256
    [[ -f wasm/app.wasm ]] && sha256sum wasm/app.wasm >> CHECKSUMS.sha256
  fi
)

DB_URL="${DATABASE_URL:-postgresql://jnative_erp@127.0.0.1:5432/jnative_erp}"
echo "== database check =="
if ! psql "$DB_URL" -c "SELECT 1" >/dev/null 2>&1; then
  echo "FAIL: cannot connect with DATABASE_URL (credentials redacted)"
  echo "HINT: create role/db and export DATABASE_URL, then re-run"
  exit 6
fi

echo "== migrations (psql) =="
for f in 001_init.sql 002_finance.sql 003_manufacturing.sql 004_enterprise.sql; do
  echo " apply $f"
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f "$PREFIX/migrations/$f" >/dev/null
done

cat > "$PREFIX/bin/jnative-one" <<EOF
#!/usr/bin/env bash
set -euo pipefail
PREFIX="$PREFIX"
export ERP_PUBLIC="\$PREFIX/public"
export ERP_STATIC_WASM="\$PREFIX/wasm"
export ERP_STORAGE_PATH="\${ERP_STORAGE_PATH:-\$PREFIX/var/storage}"
export ERP_BACKUP_PATH="\${ERP_BACKUP_PATH:-\$PREFIX/var/backups}"
export JNATIVE_ONE_VERSION="\$(cat "\$PREFIX/VERSION")"
export JNATIVE_ONE_BUILD_TIME="$BUILD_TIME"
export JNATIVE_ONE_TARGET="$TARGET"
exec "\$PREFIX/bin/jnative-one-server" "\$@"
EOF
chmod +x "$PREFIX/bin/jnative-one"

echo "INSTALL_OK prefix=$PREFIX"
echo "Next: $PREFIX/bin/jnative-one --version"
echo "      docs/admin-guide.md"
