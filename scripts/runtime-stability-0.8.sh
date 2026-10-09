#!/usr/bin/env bash
# Author: 张晓达
# JNative 0.8 — Runtime Stability runner
# Usage:
#   ./scripts/runtime-stability-0.8.sh              # short suite
#   JNATIVE_STABILITY_SOAK=10m ./scripts/...        # 10-minute loop (optional)
#   JNATIVE_STABILITY_SOAK=30m|2h                   # longer soaks
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== JNative Runtime Stability 0.8 ==="
echo "host=$(uname -s)-$(uname -m) date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"

MVN=(mvn -q -DskipTests=false)
echo "--- compile ---"
"${MVN[@]}" -DskipTests compile test-compile

echo "--- short RuntimeStabilitySuite ---"
"${MVN[@]}" -Dtest=RuntimeStabilitySuiteTest,GcRelocationEndToEndTest,GcNativeValidationEndToEndTest,ThreadEndToEndTest,FfiEndToEndTest,NativeCallbackEndToEndTest,ExceptionEndToEndTest test

SOAK="${JNATIVE_STABILITY_SOAK:-}"
if [[ -z "$SOAK" ]]; then
  echo "SOAK_10M=NOT_RUN"
  echo "SOAK_30M=NOT_RUN"
  echo "SOAK_2H=NOT_RUN"
  echo "Set JNATIVE_STABILITY_SOAK=10m|30m|2h to enable continuous stress."
  exit 0
fi

secs=0
case "$SOAK" in
  10m) secs=600 ;;
  30m) secs=1800 ;;
  2h) secs=7200 ;;
  *) echo "Unknown soak=$SOAK"; exit 2 ;;
esac

echo "--- soak $SOAK (${secs}s) looping GC+Thread stress ---"
end=$(( $(date +%s) + secs ))
ops=0
fails=0
while [[ $(date +%s) -lt $end ]]; do
  if ! "${MVN[@]}" -Dtest=RuntimeStabilitySuiteTest#gcStressDebugAndRelease,RuntimeStabilitySuiteTest#threadStress2_4_8_16 test; then
    fails=$((fails + 1))
    echo "SOAK_FAIL at ops=$ops fails=$fails"
    exit 1
  fi
  ops=$((ops + 1))
  echo "soak progress ops=$ops remaining=$(( end - $(date +%s) ))s"
done
echo "SOAK_${SOAK}=PASS ops=$ops fails=$fails"
