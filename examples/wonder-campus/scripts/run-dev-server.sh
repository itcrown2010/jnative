#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BIN="$ROOT/examples/wonder-campus/.jnative/build/wonder-server"
mkdir -p "$ROOT/build/wonder-campus"
if [[ ! -x "$BIN" ]]; then
  (cd "$ROOT" && java -jar target/jnative.jar build --project examples/wonder-campus)
fi
export DATABASE_URL="${DATABASE_URL:-sqlite:$ROOT/build/wonder-campus/wonder.db}"
export WC_LISTEN_PORT="${WC_LISTEN_PORT:-18880}"
export WC_ALLOW_DEMO_LOGIN="${WC_ALLOW_DEMO_LOGIN:-1}"
export WC_ENV="${WC_ENV:-dev}"
export WC_MEDIA_DIR="${WC_MEDIA_DIR:-$ROOT/build/wonder-campus/media}"
echo "Starting wonder-server env=$WC_ENV port=$WC_LISTEN_PORT demoLogin=$WC_ALLOW_DEMO_LOGIN"
exec "$BIN"
