#!/usr/bin/env bash
# Author: 张晓达
# Build release layout + checksums. Does NOT mark the package pilot-ready.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="${OUT:-$ROOT/build/jnative-one-release}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
VER="$(cat "$ROOT/examples/erp/VERSION")"

rm -rf "$OUT"
mkdir -p "$OUT"

echo "== build =="
(cd "$ROOT" && java -jar target/jnative.jar build --project examples/erp)

PREFIX="$OUT/jnative-one-${VER}"
export PREFIX
bash "$ROOT/examples/erp/scripts/jnative-one-install.sh"

# Relocate install tree into versioned folder if PREFIX default used
if [[ -d "$ROOT/build/jnative-one" && "$PREFIX" == "$ROOT/build/jnative-one" ]]; then
  mkdir -p "$OUT"
  rsync -a --delete "$ROOT/build/jnative-one/" "$PREFIX/"
fi

cp -f "$ROOT/docs/erp-product-status.md" "$PREFIX/docs/" 2>/dev/null || mkdir -p "$PREFIX/docs"
cp -f "$ROOT/docs/erp-release-blockers.md" "$PREFIX/docs/" || true
cp -f "$ROOT/docs/admin-guide.md" "$PREFIX/docs/" 2>/dev/null || true
cp -f "$ROOT/docs/user-guide.md" "$PREFIX/docs/" 2>/dev/null || true
cp -f "$ROOT/docs/implementation-guide.md" "$PREFIX/docs/" 2>/dev/null || true

echo "NOT_PILOT_READY" > "$PREFIX/RELEASE_STATUS"
echo "See docs/erp-release-blockers.md — Linux/P0 gates open" >> "$PREFIX/RELEASE_STATUS"

TAR="$OUT/jnative-one-${VER}-${STAMP}.tar.gz"
tar -C "$OUT" -czf "$TAR" "$(basename "$PREFIX")"
(
  cd "$OUT"
  if command -v shasum >/dev/null; then shasum -a 256 "$(basename "$TAR")" > "$(basename "$TAR").sha256"
  else sha256sum "$(basename "$TAR")" > "$(basename "$TAR").sha256"; fi
)

echo "PACKAGE_OK tar=$TAR"
cat "$TAR.sha256"
