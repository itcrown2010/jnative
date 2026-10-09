#!/usr/bin/env bash
# Author: 张晓达
# Phase-3: mobile assets + android APK + ios sim build + docs site
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

bash "$ROOT/scripts/docs/verify-phase2.sh"

echo "== mobile doctor (informational) =="
java -jar "$ROOT/target/jnative.jar" doctor --mobile || true

echo "== guide-tasks mobile assets =="
bash "$ROOT/examples/guide-tasks/scripts/verify_mobile_assets.sh"

echo "== guide-tasks android APK =="
bash "$ROOT/examples/guide-tasks/scripts/build_android.sh"

echo "== guide-tasks ios simulator build =="
bash "$ROOT/examples/guide-tasks/scripts/build_ios.sh"

echo "PHASE3_VERIFY_OK"
echo "NOTE: device install / store publish not claimed by this script."
