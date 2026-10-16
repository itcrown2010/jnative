#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$(cd "$(dirname "$0")/../../www" && pwd)"
DST="$ROOT/app/src/main/assets/www"
mkdir -p "$DST"
rm -rf "$DST/js"
mkdir -p "$DST/js"
cp -f "$SRC/index.html" "$SRC/app.css" "$SRC/app.js" "$SRC/config.js" "$DST/"
cp -f "$SRC"/js/*.js "$DST/js/"
if [[ -f "$SRC/config.prod.js" ]]; then cp -f "$SRC/config.prod.js" "$DST/"; fi
echo "Synced www → $DST"
