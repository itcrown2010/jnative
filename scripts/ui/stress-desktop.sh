#!/usr/bin/env bash
# Desktop UI stress: ProductEditor headless cycles + Activity Monitor sampling soak.
# Does NOT terminate user processes; uses isolated JSON under build/.
# Author: 张晓达
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"
OUT="$REPO/build/ui-stress"
mkdir -p "$OUT"
DUR="${STRESS_SECONDS:-45}"
LOG="$OUT/stress-$(date +%Y%m%d-%H%M%S).log"

exec > >(tee "$LOG") 2>&1
echo "stress start $(date -Iseconds) dur=${DUR}s"
echo "host=$(uname -a)"

mvn -q -DskipTests package
examples/product-editor/scripts/build.sh
examples/activity-monitor/scripts/build_desktop.sh

echo "==> desktop-sample build + self-test"
examples/desktop-sample/scripts/build.sh
./build/desktop-sample/DesktopSample --self-test | tee "$OUT/ds-self.txt"
grep -q DS_SELF_TEST_OK "$OUT/ds-self.txt"

echo "==> PE self-test x5 (edit/validate/dispose cycles)"
for i in 1 2 3 4 5; do
  ./build/product-editor/ProductEditor --self-test | tee "$OUT/pe-self-$i.txt"
  grep -q PE_SELF_TEST_OK "$OUT/pe-self-$i.txt"
done

DATA="$OUT/stress-product.json"
cat > "$DATA" <<'EOF'
{"id":"s1","name":"压力测试品","categoryId":"general","price":"1.00","qty":"1","enabled":1}
EOF
echo "PE_ISOLATED_DATA=$DATA"

echo "==> AM verify"
examples/activity-monitor/scripts/run_verify.sh | tee "$OUT/am-verify.txt"
grep -q "ALL VERIFY CHECKS PASSED" "$OUT/am-verify.txt"

echo "==> AM GUI soak ${DUR}s (isolated process; signals only this PID)"
AM_BIN="$REPO/build/activity-monitor/ActivityMonitor"
"$AM_BIN" --appearance light >/dev/null 2>&1 &
AMPID=$!
sleep 1
if ! kill -0 "$AMPID" 2>/dev/null; then
  echo "FAIL: ActivityMonitor did not start"
  exit 1
fi
SAMPLES=0
RSS_MAX=0
RSS_MIN=999999999
CPU_SUM=0
AM_DIED=0
while [[ $SAMPLES -lt $DUR ]]; do
  if ! kill -0 "$AMPID" 2>/dev/null; then
    wait "$AMPID" 2>/dev/null || true
    echo "FAIL: ActivityMonitor died during soak at t=${SAMPLES}s (crash is P0)"
    AM_DIED=1
    break
  fi
  RSS=$(ps -o rss= -p "$AMPID" 2>/dev/null | tr -d ' ' || echo 0)
  CPU=$(ps -o %cpu= -p "$AMPID" 2>/dev/null | tr -d ' ' || echo 0)
  if [[ -n "$RSS" && "$RSS" -gt 0 ]]; then
    if [[ "$RSS" -gt "$RSS_MAX" ]]; then RSS_MAX=$RSS; fi
    if [[ "$RSS" -lt "$RSS_MIN" ]]; then RSS_MIN=$RSS; fi
  fi
  if [[ -n "$CPU" ]]; then
    CPU_SUM=$(awk -v a="$CPU_SUM" -v b="$CPU" 'BEGIN { printf "%.2f", a + b }')
  fi
  SAMPLES=$((SAMPLES + 1))
  sleep 1
done
if [[ "$AM_DIED" -eq 0 ]]; then
  kill -TERM "$AMPID" 2>/dev/null || true
  sleep 1
  if kill -0 "$AMPID" 2>/dev/null; then
    kill -KILL "$AMPID" 2>/dev/null || true
  fi
  wait "$AMPID" 2>/dev/null || true
fi
sleep 0.5
if pgrep -f "$AM_BIN" >/dev/null 2>&1; then
  echo "WARN: ActivityMonitor binary still listed in pgrep (check manually)"
else
  echo "AM_NO_LEFTOVER_PROCESS=1"
fi

CPU_AVG=$(awk -v s="$CPU_SUM" -v n="$SAMPLES" 'BEGIN { if (n>0) printf "%.2f", s/n; else print 0 }')
echo "AM_RSS_MIN_KB=$RSS_MIN"
echo "AM_RSS_MAX_KB=$RSS_MAX"
echo "AM_CPU_AVG_PCT=$CPU_AVG"
echo "AM_SOAK_SECONDS=$SAMPLES"
if [[ "$RSS_MAX" -gt 2000000 ]]; then
  echo "WARN: RSS max unusually high: $RSS_MAX KB"
fi
if [[ "$AM_DIED" -ne 0 ]]; then
  exit 1
fi

echo "==> PE leak probe"
./build/product-editor/ProductEditor --self-test | tee "$OUT/pe-leak-probe.txt"
grep -q PE_UI_INTEGRATION_OK "$OUT/pe-leak-probe.txt"

echo "STRESS_OK log=$LOG"
echo "Manual GUI still required: AM search/sort/detail/pause/resize/reopen; PE multi-window save-fail."
