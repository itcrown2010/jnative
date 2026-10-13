#!/usr/bin/env bash
# Repeatable desktop UI / a11y / focus smoke (macOS).
# Uses headless UiTest inject + optional live .app launch check.
# Author: 张晓达
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"

echo "==> package toolchain"
mvn -q -DskipTests package

echo "==> product-editor build + self-test (binding + UiTest focus/validation)"
examples/product-editor/scripts/build.sh
./build/product-editor/ProductEditor --self-test | tee build/product-editor/self-test.log
grep -q PE_SELF_TEST_OK build/product-editor/self-test.log
grep -q PE_UI_INTEGRATION_OK build/product-editor/self-test.log

echo "==> ui-workbench build"
examples/ui-workbench/scripts/build.sh

echo "==> activity-monitor verify (logic + binary)"
examples/activity-monitor/scripts/build_desktop.sh
examples/activity-monitor/scripts/run_verify.sh | tee build/activity-monitor/verify-run.log
grep -q "ALL VERIFY CHECKS PASSED" build/activity-monitor/verify-run.log

echo "==> live .app launch smoke (non-interactive, short)"
open "$REPO/build/product-editor/ProductEditor.app" --args --appearance light --data "$REPO/build/product-editor/product.json" || true
sleep 2
if pgrep -q ProductEditor; then
  echo "OK ProductEditor.app process running"
  pkill -9 ProductEditor || true
else
  echo "WARN ProductEditor.app did not stay up (check Gatekeeper / console)"
fi

echo "ALL DESKTOP A11Y/FOCUS SMOKE CHECKS PASSED"
echo "Manual VoiceOver: open ProductEditor.app, VO+A explore fields, Tab through form, Cmd+S."
echo "Manual DPI: move window across Retina / external display; check TrendChart sharpness."
