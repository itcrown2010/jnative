#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$(cd "$(dirname "$0")/../../www" && pwd)"
DST="$ROOT/WonderCampus/www"
mkdir -p "$DST/js"
cp -f "$SRC/index.html" "$SRC/app.css" "$SRC/app.js" "$SRC/config.js" "$DST/"
if [[ -f "$SRC/config.prod.js" ]]; then cp -f "$SRC/config.prod.js" "$DST/"; fi
cp -f "$SRC"/js/*.js "$DST/js/"
echo "Synced www → $DST (incl. js modules)"
