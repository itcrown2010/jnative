#!/usr/bin/env bash
# Author: 张晓达
# Race regression: run → cancel → immediately open/compress again.
# Covers success / failure / cancel end-paths without fixed-sleep workarounds
# beyond waiting for UI readiness (poll AX / inbox).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="${1:-$ROOT/build/JNativeArchive.app}"
BIN="$APP/Contents/MacOS/JNativeArchive"
CLI="$ROOT/build/jn-archive"
LAUNCH="$APP/Contents/Resources/shell/jn-archive-launch.sh"
WORK="$ROOT/build/cancel-reopen"
SHOT="$ROOT/build/cancel-reopen-shots"
INBOX="${HOME}/Library/Application Support/JNativeArchive/shell-inbox"
REPORT="$SHOT/RESULTS.txt"

pass() { echo "PASS  $*" | tee -a "$REPORT"; }
fail() { echo "FAIL  $*" | tee -a "$REPORT"; FAILS=$((FAILS + 1)); }
FAILS=0

if [[ ! -x "$BIN" ]]; then
  echo "missing $BIN — run scripts/build-gui.sh" >&2
  exit 1
fi
if [[ ! -x "$CLI" ]]; then
  echo "missing $CLI" >&2
  exit 1
fi
if [[ ! -x "$LAUNCH" ]]; then
  LAUNCH="$ROOT/integration/macos/jn-archive-launch.sh"
fi

rm -rf "$WORK" "$SHOT"
mkdir -p "$WORK/in" "$WORK/out" "$SHOT"
: >"$REPORT"

printf 'race-a\n' >"$WORK/in/a.txt"
printf 'race-b\n' >"$WORK/in/b file.txt"
printf '中文\n' >"$WORK/in/中文名.txt"
# Larger payload so cancel has a real window (≈48MiB)
dd if=/dev/urandom of="$WORK/in/big.bin" bs=1048576 count=48 status=none 2>/dev/null || \
  head -c 50331648 /dev/urandom >"$WORK/in/big.bin"
"$CLI" create "$WORK/plain.zip" "$WORK/in/a.txt" "$WORK/in/b file.txt" "$WORK/in/中文名.txt"
"$CLI" create "$WORK/plain2.zip" "$WORK/in/a.txt"

killall JNativeArchive 2>/dev/null || true
killall jn-archive-gui 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"
chmod 700 "$INBOX"

ax_has() {
  local needle="$1"
  osascript -e '
    tell application "System Events"
      if not (exists process "JNativeArchive") then return "NO_PROC"
      tell process "JNativeArchive"
        set texts to value of every static text of every window
        return texts as text
      end tell
    end tell
  ' 2>/dev/null | tr '\n' ' ' | grep -F "$needle" >/dev/null
}

wait_ax() {
  local needle="$1"
  local seconds="${2:-20}"
  local i=0
  while [[ $i -lt $seconds ]]; do
    if ax_has "$needle"; then
      return 0
    fi
    sleep 0.5
    i=$((i + 1))
  done
  return 1
}

dismiss_alerts() {
  osascript -e '
    tell application "System Events"
      if not (exists process "JNativeArchive") then return
      tell process "JNativeArchive"
        repeat with w in windows
          try
            if exists button "好" of w then click button "好" of w
            if exists button "确定" of w then click button "确定" of w
            if exists button "OK" of w then click button "OK" of w
            if exists button "No" of w then click button "No" of w
            if exists button "否" of w then click button "否" of w
          end try
        end repeat
      end tell
    end tell
  ' 2>/dev/null || true
}

click_cancel_toolbar() {
  osascript -e '
    tell application "System Events"
      tell process "JNativeArchive"
        set frontmost to true
        try
          click button "取消" of toolbar 1 of window 1
        on error
          try
            click menu item "取消" of menu "文件" of menu bar 1
          end try
        end try
      end tell
    end tell
  ' 2>/dev/null || true
}

click_create_start() {
  osascript -e '
    tell application "System Events"
      tell process "JNativeArchive"
        set frontmost to true
        try
          click button "创建" of window "新建压缩包"
        end try
      end tell
    end tell
  ' 2>/dev/null || true
}

start_compress_job() {
  local log="$1"
  shift
  printf '%s\0' "$@" | "$LAUNCH" compress >"$log" 2>&1 || true
  # Wait for create dialog, then start the actual job
  local i=0
  while [[ $i -lt 40 ]]; do
    if ax_has "新建压缩包" || ax_has "压缩档位"; then
      break
    fi
    sleep 0.25
    i=$((i + 1))
  done
  click_create_start
  # Wait until toolbar Cancel is enabled / progress visible
  i=0
  while [[ $i -lt 40 ]]; do
    if ax_has "正在" || ax_has "压缩" || ax_has "%"; then
      break
    fi
    # Also treat disabled New button as busy via AX role dump
    sleep 0.15
    i=$((i + 1))
  done
}

cancel_then_open() {
  local zip="$1"
  local openlog="$2"
  # Queue open while job still busy (inbox), then cancel — no fixed long sleep.
  printf '%s\0' "$zip" | "$LAUNCH" open >"$openlog" 2>&1 || true
  local i=0
  while [[ $i -lt 10 ]]; do
    click_cancel_toolbar
    sleep 0.05
    i=$((i + 1))
  done
  i=0
  while [[ $i -lt 40 ]]; do
    dismiss_alerts
    if ax_has "plain.zip" || ax_has "中文名" || ax_has "a.txt"; then
      break
    fi
    sleep 0.25
    i=$((i + 1))
  done
}

echo "==> R1 cancel during compress → immediate open" | tee -a "$REPORT"
open -a "$APP"
sleep 2
start_compress_job "$SHOT/r1-compress.log" \
  "$WORK/in/a.txt" "$WORK/in/b file.txt" "$WORK/in/中文名.txt" "$WORK/in/big.bin"
cancel_then_open "$WORK/plain.zip" "$SHOT/r1-open.log"
if wait_ax "plain.zip" 40 || wait_ax "中文名" 40 || wait_ax "a.txt" 40; then
  pass "R1 cancel→immediate open shows archive"
else
  fail "R1 cancel→immediate open did not show archive"
  screencapture -x "$SHOT/r1-fail.png" || true
fi
screencapture -x "$SHOT/r1-after-open.png" || true

echo "==> R2 cancel during compress → immediate compress again" | tee -a "$REPORT"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"
open -a "$APP"
sleep 2
start_compress_job "$SHOT/r2-compress1.log" "$WORK/in/big.bin" "$WORK/in/a.txt"
# Cancel then immediately re-compress
i=0
while [[ $i -lt 8 ]]; do
  click_cancel_toolbar
  sleep 0.05
  i=$((i + 1))
done
printf '%s\0' "$WORK/in/a.txt" "$WORK/in/中文名.txt" \
  | "$LAUNCH" compress >"$SHOT/r2-compress2.log" 2>&1 || true
sleep 2
dismiss_alerts
ax_create="$(osascript -e '
  tell application "System Events"
    if not (exists process "JNativeArchive") then return "NO"
    tell process "JNativeArchive"
      set titles to name of every window
      return titles as text
    end tell
  end tell
' 2>/dev/null || true)"
echo "windows=$ax_create" | tee -a "$REPORT"
if echo "$ax_create" | grep -q "新建压缩包"; then
  pass "R2 cancel→immediate compress shows create dialog"
elif wait_ax "已选" 20 || wait_ax "创建" 20 || wait_ax "中文名" 20; then
  pass "R2 cancel→immediate compress UI visible"
else
  fail "R2 cancel→immediate compress UI missing"
  screencapture -x "$SHOT/r2-fail.png" || true
fi
screencapture -x "$SHOT/r2-create.png" || true
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      try
        click button "取消" of window "新建压缩包"
      end try
    end tell
  end tell
' 2>/dev/null || true

echo "==> R3 success path then open (no stale overwrite)" | tee -a "$REPORT"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"
"$CLI" create "$WORK/out/ok.zip" "$WORK/in/a.txt"
open -a "$APP" --args --open "$WORK/out/ok.zip"
if wait_ax "ok.zip" 30 || wait_ax "a.txt" 30; then
  pass "R3 success open path"
else
  fail "R3 success open path"
fi
screencapture -x "$SHOT/r3-success.png" || true

echo "==> R4 failure path (missing source) then immediate open" | tee -a "$REPORT"
MISS="$WORK/in/will-miss.txt"
printf 'x\n' >"$MISS"
printf '%s\0' "$MISS" | "$LAUNCH" compress >"$SHOT/r4-compress.log" 2>&1 || true
# Create dialog appears; delete source then submit → failure path
sleep 1
rm -f "$MISS"
click_create_start
sleep 2
dismiss_alerts
printf '%s\0' "$WORK/plain2.zip" | "$LAUNCH" open >"$SHOT/r4-open.log" 2>&1 || true
dismiss_alerts
if wait_ax "plain2.zip" 40 || wait_ax "a.txt" 40; then
  pass "R4 fail→immediate open"
else
  fail "R4 fail→immediate open"
  screencapture -x "$SHOT/r4-fail.png" || true
fi
screencapture -x "$SHOT/r4-after.png" || true

echo "==> R5 cancel end-path status is not sticky after reopen" | tee -a "$REPORT"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"
open -a "$APP"
sleep 2
start_compress_job "$SHOT/r5-c.log" "$WORK/in/big.bin"
cancel_then_open "$WORK/plain.zip" "$SHOT/r5-o.log"
ui_text="$(osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      set texts to value of every static text of every window
      return texts as text
    end tell
  end tell
' 2>/dev/null || true)"
echo "ui_snippet=${ui_text:0:240}" | tee -a "$REPORT"
if echo "$ui_text" | grep -qE "plain\.zip|中文名|a\.txt"; then
  pass "R5 reopen after cancel not stuck on cancel-only UI"
else
  fail "R5 reopen after cancel missing archive UI"
fi
screencapture -x "$SHOT/r5-final.png" || true

killall JNativeArchive 2>/dev/null || true

echo "" | tee -a "$REPORT"
echo "FAILS=$FAILS" | tee -a "$REPORT"
echo "WINDOWS_RACE: UNVERIFIED" | tee -a "$REPORT"
ls -la "$SHOT" | tee -a "$REPORT"

if [[ "$FAILS" -gt 0 ]]; then
  echo "CANCEL_REOPEN_REGRESS_FAIL fails=$FAILS"
  exit 1
fi
echo "CANCEL_REOPEN_REGRESS_PASS"
echo "Report: $REPORT"
