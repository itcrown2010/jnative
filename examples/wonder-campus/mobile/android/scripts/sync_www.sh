#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$(cd "$(dirname "$0")/../../www" && pwd)"
DST="$ROOT/app/src/main/assets/www"
mkdir -p "$DST"
cp -f "$SRC/index.html" "$SRC/app.css" "$SRC/app.js" "$SRC/config.js" "$DST/"
echo "Synced www → $DST"
