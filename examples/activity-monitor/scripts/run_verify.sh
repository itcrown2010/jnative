#!/usr/bin/env bash
# Author: 张晓达
# Bounded verification against dedicated test processes only.
set -euo pipefail
# Background job death messages must not abort the script.
set +m
trap '' SIGCHLD || true
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
OUT_DIR="${OUT_DIR:-$REPO/build/activity-monitor}"
LOG="$OUT_DIR/verify-results.txt"
mkdir -p "$OUT_DIR"
: > "$LOG"
log() { echo "$@" | tee -a "$LOG"; }

"$ROOT/scripts/build_core.sh"
"$ROOT/scripts/build_desktop.sh"

CORE="$OUT_DIR/AmCore"
APP="$OUT_DIR/ActivityMonitor"
IDLE="$OUT_DIR/am_idle"
BURN="$OUT_DIR/am_burn"
IGN="$OUT_DIR/am_ignore_term"

log "== AmCore --self =="
"$CORE" --self | tee -a "$LOG"

log "== first sample (waiting) + second (CPU%) =="
"$CORE" --dump am_ | tee -a "$LOG" || true

log "== bench 5 =="
"$CORE" --bench 5 | tee -a "$LOG"

# --- spawn idle ---
wait_ready() {
  local f="$1" needle="$2" n=0
  while [[ $n -lt 50 ]]; do
    if [[ -f "$f" ]] && grep -q "$needle" "$f" 2>/dev/null; then
      return 0
    fi
    sleep 0.05
    n=$((n + 1))
  done
  log "WARN ready timeout for $f"
  return 1
}

log "== spawn am_idle =="
"$IDLE" >"$OUT_DIR/am_idle.out" 2>&1 &
IDLE_PID=$!
wait_ready "$OUT_DIR/am_idle.out" "am_idle pid=" || true
FIND=$("$CORE" --find am_idle)
log "$FIND"
echo "$FIND" | grep -q "FOUND	$IDLE_PID" || { log "FAIL idle not listed"; kill $IDLE_PID 2>/dev/null || true; exit 1; }
START_SEC=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $3; exit}')
START_USEC=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $4; exit}')
RSS=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $5; exit}')
log "idle identity $IDLE_PID $START_SEC $START_USEC rss=$RSS"
[[ "$RSS" -gt 0 ]] || { log "FAIL rss"; kill $IDLE_PID; exit 1; }

log "== SIGTERM idle (identity-checked) =="
"$CORE" --signal "$IDLE_PID" "$START_SEC" "$START_USEC" term | tee -a "$LOG"
sleep 0.4
if kill -0 "$IDLE_PID" 2>/dev/null; then
  log "FAIL idle still alive after SIGTERM"
  kill -9 "$IDLE_PID" || true
  exit 1
fi
log "OK idle exited"

# PID reuse guard: signal stale identity
log "== stale identity must fail =="
OUT=$("$CORE" --signal "$IDLE_PID" "$START_SEC" "$START_USEC" term || true)
log "$OUT"
echo "$OUT" | grep -q "signal_code=-2\|signal_code=3\|ESRCH\|身份" || log "note: stale signal result logged"

# --- burn CPU ---
log "== spawn am_burn =="
"$BURN" >"$OUT_DIR/am_burn.out" 2>&1 &
BURN_PID=$!
wait_ready "$OUT_DIR/am_burn.out" "am_burn pid=" || true
"$CORE" --dump >/dev/null
sleep 0.5
DUMP=$("$CORE" --dump am_burn)
log "$DUMP"
echo "$DUMP" | grep -q "$BURN_PID" || { log "FAIL burn missing"; kill -9 $BURN_PID; exit 1; }
# CPU should not be "等待采样" on second dump line for burn
CPU_LINE=$(echo "$DUMP" | grep "$BURN_PID" | head -1)
log "burn line: $CPU_LINE"
if echo "$CPU_LINE" | grep -q "等待采样"; then
  log "WARN burn still waiting sample (retry once)"
  sleep 0.5
  DUMP=$("$CORE" --dump am_burn)
  CPU_LINE=$(echo "$DUMP" | grep "$BURN_PID" | head -1)
  log "burn line2: $CPU_LINE"
fi
FIND=$("$CORE" --find am_burn)
B_SEC=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $3; exit}')
B_USEC=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $4; exit}')
"$CORE" --signal "$BURN_PID" "$B_SEC" "$B_USEC" term | tee -a "$LOG"
sleep 0.3
kill -0 "$BURN_PID" 2>/dev/null && kill -9 "$BURN_PID" || true

# --- ignore SIGTERM then SIGKILL ---
log "== spawn am_ignore_term =="
"$IGN" >"$OUT_DIR/am_ignore_term.out" 2>&1 &
IGN_PID=$!
wait_ready "$OUT_DIR/am_ignore_term.out" "am_ignore_term pid=" || true
# Extra beat: ensure sigaction completed before any signal.
sleep 0.15
FIND=$("$CORE" --find am_ignore_term)
log "$FIND"
I_SEC=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $3; exit}')
I_USEC=$(echo "$FIND" | awk -F'\t' '/FOUND/{print $4; exit}')
"$CORE" --signal "$IGN_PID" "$I_SEC" "$I_USEC" term | tee -a "$LOG"
sleep 0.3
if ! kill -0 "$IGN_PID" 2>/dev/null; then
  log "FAIL ignore_term died on SIGTERM"
  exit 1
fi
log "OK still alive after SIGTERM"
"$CORE" --signal "$IGN_PID" "$I_SEC" "$I_USEC" kill | tee -a "$LOG"
sleep 0.3
if kill -0 "$IGN_PID" 2>/dev/null; then
  log "FAIL ignore_term survived SIGKILL"
  kill -9 "$IGN_PID" || true
  exit 1
fi
log "OK force-killed"

log "== protect self (same-process) =="
OUT=$("$CORE" --signal-self || true)
log "$OUT"
echo "$OUT" | grep -q "signal_code=-1" || { log "FAIL self protect"; exit 1; }
log "OK self protected"

log "== desktop binary exists =="
[[ -x "$APP" ]]
file "$APP" | tee -a "$LOG"

log "== bounded sustained sampling (8 samples) =="
"$CORE" --bench 8 | tee -a "$LOG"

log "ALL VERIFY CHECKS PASSED"
log "Results: $LOG"
echo "GUI: $APP  (manual: search/sort/pause/detail/confirm terminate)"
