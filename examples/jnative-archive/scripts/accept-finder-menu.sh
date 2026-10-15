#!/usr/bin/env bash
# Author: 张晓达
# Separates Automator workflow execution from real Finder menu entry.
# On Chinese macOS: 访达 → 服务 → JNative Archive … (same workflows as 右键→快速操作).
# Runs FINDER_MENU first (clean Finder), then AUTOMATOR regression.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLI="$ROOT/build/jn-archive"
SHOT="$ROOT/build/finder-menu-shots"
WORK="$ROOT/build/finder-menu"
SERVICES="${HOME}/Library/Services"
INBOX="${HOME}/Library/Application Support/JNativeArchive/shell-inbox"
REPORT="$SHOT/RESULTS.txt"
DEST_APP="${HOME}/Applications/JNativeArchive.app"

pass() { echo "PASS  $*" | tee -a "$REPORT"; }
fail() { echo "FAIL  $*" | tee -a "$REPORT"; FAILS=$((FAILS + 1)); }
skip() { echo "UNVERIFIED  $*" | tee -a "$REPORT"; }
FAILS=0
AUTOMATOR_OK=0
FINDER_MENU_OK=0

rm -rf "$SHOT" "$WORK"
mkdir -p "$SHOT" "$WORK/in" "$WORK/out"
: >"$REPORT"

echo "==> ensure app + Quick Actions installed" | tee -a "$REPORT"
chmod +x "$ROOT/scripts/dist-macos.sh" "$ROOT/integration/macos/install.sh" \
  "$ROOT/integration/macos/jn-archive-launch.sh" 2>/dev/null || true
if [[ "${SKIP_BUILD:-}" != "1" ]]; then
  "$ROOT/scripts/build-gui.sh"
  "$ROOT/scripts/dist-macos.sh"
fi
export JN_ARCHIVE_INSTALL_YES=1
"$ROOT/integration/macos/install.sh"

if [[ ! -x "$DEST_APP/Contents/MacOS/JNativeArchive" ]]; then
  fail "app missing at $DEST_APP"
  exit 1
fi
pass "app installed"

WF_OPEN="JNative Archive Open ZIP"
WF_COMPRESS="JNative Archive Compress ZIP"
if [[ ! -d "$SERVICES/${WF_OPEN}.workflow" ]]; then
  fail "Open workflow missing"
  exit 1
fi
pass "Quick Action workflows present"

printf 'm-a\n' >"$WORK/in/a.txt"
printf 'm-b\n' >"$WORK/in/b file.txt"
printf '中文\n' >"$WORK/in/中文路径.txt"
mkdir -p "$WORK/in/中文目录"
printf 'inner\n' >"$WORK/in/中文目录/inner.txt"
"$CLI" create "$WORK/plain.zip" "$WORK/in/a.txt" "$WORK/in/中文路径.txt"
WORK_ABS="$(cd "$WORK" && pwd)"

wait_app_or_job() {
  local seconds="${1:-15}"
  local i=0
  while [[ $i -lt $seconds ]]; do
    if pgrep -x JNativeArchive >/dev/null; then
      return 0
    fi
    if find "$INBOX" -name '*.job' 2>/dev/null | grep -q .; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

finder_service_click() {
  local item="$1"
  local logfile="$2"
  shift 2
  local attempt=1
  while [[ $attempt -le 6 ]]; do
    osascript - "$item" "$@" <<'APPLESCRIPT' >"$logfile" 2>&1 || true
on run argv
  set itemName to item 1 of argv
  set pathList to {}
  set i to 2
  repeat while i ≤ (count of argv)
    set end of pathList to POSIX file (item i of argv)
    set i to i + 1
  end repeat
  tell application "System Events"
    key code 53
    delay 0.15
    key code 53
  end tell
  tell application "Finder"
    activate
    delay 0.6
    if (count of pathList) = 1 then
      set f to item 1 of pathList
      reveal f
      delay 0.5
      select f
    else
      set aliases to {}
      repeat with p in pathList
        set end of aliases to (p as alias)
      end repeat
      reveal item 1 of aliases
      delay 0.5
      select aliases
    end if
    delay 1.2
  end tell
  tell application "System Events"
    tell process "Finder"
      set frontmost to true
      delay 0.5
      try
        click menu bar item "访达" of menu bar 1
        delay 0.4
        set svc to menu item "服务" of menu "访达" of menu bar 1
        click svc
        delay 0.6
        set names to name of every menu item of menu 1 of svc
        set found to false
        repeat with n in names
          if (n as text) is equal to itemName then set found to true
        end repeat
        if found is false then
          return "MENU_NOT_LISTED names=" & (names as text)
        end if
        click menu item itemName of menu 1 of svc
        delay 0.3
        return "MENU_CLICK_OK"
      on error errMsg number errNum
        return "MENU_ERR:" & errNum & ":" & errMsg
      end try
    end tell
  end tell
end run
APPLESCRIPT
    if grep -q "MENU_CLICK_OK" "$logfile" 2>/dev/null; then
      return 0
    fi
    if grep -q "MENU_NOT_LISTED" "$logfile" 2>/dev/null; then
      return 1
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  return 1
}

# ---------------------------------------------------------------------------
# FINDER_MENU first (clean Finder — Automator leaves Finder "busy")
# ---------------------------------------------------------------------------
echo "==> FINDER_MENU: cold open via 访达→服务" | tee -a "$REPORT"
killall JNativeArchive 2>/dev/null || true
killall Automator 2>/dev/null || true
killall WorkflowServiceRunner 2>/dev/null || true
sleep 2
rm -rf "$INBOX"
mkdir -p "$INBOX"
osascript -e 'tell application "Finder" to activate' 2>/dev/null || true
sleep 1

finder_service_click "JNative Archive Open ZIP" "$SHOT/finder-menu-open.log" "$WORK_ABS/plain.zip"
MENU_LOG="$(cat "$SHOT/finder-menu-open.log" 2>/dev/null || true)"
echo "finder_menu_log=$MENU_LOG" | tee -a "$REPORT"
screencapture -x "$SHOT/finder-menu-open.png" || true

if echo "$MENU_LOG" | grep -q "MENU_CLICK_OK"; then
  pass "FINDER_MENU item discoverable and clicked"
  if wait_app_or_job 18; then
    pass "FINDER_MENU cold launch / job after menu click"
    FINDER_MENU_OK=1
  else
    fail "FINDER_MENU click OK but app/job missing"
  fi
else
  fail "FINDER_MENU could not click service item ($MENU_LOG)"
fi

echo "==> FINDER_MENU multi-select + Chinese paths" | tee -a "$REPORT"
killall JNativeArchive 2>/dev/null || true
sleep 2
rm -rf "$INBOX"
mkdir -p "$INBOX"
finder_service_click "JNative Archive Compress ZIP" "$SHOT/finder-menu-multi.log" \
  "$WORK_ABS/in/a.txt" "$WORK_ABS/in/中文路径.txt"
MULTI_LOG="$(cat "$SHOT/finder-menu-multi.log" 2>/dev/null || true)"
echo "finder_multi=$MULTI_LOG" | tee -a "$REPORT"
sleep 2
screencapture -x "$SHOT/finder-menu-multi.png" || true
if echo "$MULTI_LOG" | grep -q "MENU_CLICK_OK"; then
  pass "FINDER_MENU multi-select + Chinese path menu click"
  FINDER_MENU_OK=1
else
  fail "FINDER_MENU multi-select menu click missed"
fi

echo "==> FINDER_MENU cancel then re-trigger (warm)" | tee -a "$REPORT"
osascript -e '
  tell application "System Events"
    if exists process "JNativeArchive" then
      tell process "JNativeArchive"
        set frontmost to true
        try
          click button "取消" of window "新建压缩包"
        end try
        try
          click button "取消" of toolbar 1 of window 1
        end try
        try
          click button "No" of window 1
        end try
      end tell
    end if
  end tell
' 2>/dev/null || true
sleep 2
if ! pgrep -x JNativeArchive >/dev/null; then
  open -a "$DEST_APP"
  sleep 2
fi
finder_service_click "JNative Archive Open ZIP" "$SHOT/finder-menu-retrigger.log" "$WORK_ABS/plain.zip"
sleep 2
screencapture -x "$SHOT/finder-menu-retrigger.png" || true
if grep -q "MENU_CLICK_OK" "$SHOT/finder-menu-retrigger.log" 2>/dev/null; then
  pass "FINDER_MENU cancel then re-trigger"
  if pgrep -x JNativeArchive >/dev/null; then
    pass "FINDER_MENU warm path (app already running)"
    FINDER_MENU_OK=1
  fi
else
  fail "FINDER_MENU re-trigger missed"
fi

# ---------------------------------------------------------------------------
# AUTOMATOR regression (separate from Finder menu entry)
# ---------------------------------------------------------------------------
echo "==> AUTOMATOR: workflow execution (regression)" | tee -a "$REPORT"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"
automator -i "$WORK/plain.zip" "$SERVICES/${WF_OPEN}.workflow" \
  >"$SHOT/automator-open.log" 2>&1 || true
sleep 4
if pgrep -x JNativeArchive >/dev/null || grep -qiE 'queued|launch|open' "$SHOT/automator-open.log"; then
  pass "AUTOMATOR open workflow executed"
  AUTOMATOR_OK=1
else
  if find "$INBOX" -name '*.job' 2>/dev/null | grep -q . || pgrep -x JNativeArchive >/dev/null; then
    pass "AUTOMATOR open workflow delivered job"
    AUTOMATOR_OK=1
  else
    fail "AUTOMATOR open workflow no evidence"
  fi
fi
screencapture -x "$SHOT/automator-open.png" || true

killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"
printf '%s\0' "$WORK/in/a.txt" "$WORK/in/b file.txt" "$WORK/in/中文路径.txt" \
  | "$DEST_APP/Contents/Resources/shell/jn-archive-launch.sh" compress \
  >"$SHOT/launch-compress-multi.log" 2>&1 || true
sleep 3
if pgrep -x JNativeArchive >/dev/null; then
  pass "AUTOMATOR/launch multi-select compress started"
  AUTOMATOR_OK=1
else
  fail "AUTOMATOR multi compress did not start app"
fi
screencapture -x "$SHOT/automator-compress.png" || true

echo "" | tee -a "$REPORT"
if [[ "$AUTOMATOR_OK" -eq 1 ]]; then
  echo "AUTOMATOR_WORKFLOW: PASS" | tee -a "$REPORT"
else
  echo "AUTOMATOR_WORKFLOW: FAIL" | tee -a "$REPORT"
fi
if [[ "$FINDER_MENU_OK" -eq 1 ]]; then
  echo "FINDER_MENU_ENTRY: PASS" | tee -a "$REPORT"
else
  echo "FINDER_MENU_ENTRY: FAIL" | tee -a "$REPORT"
fi
echo "FINDER_MENU_PATH: 访达 → 服务 → JNative Archive … (右键快速操作同工作流)" | tee -a "$REPORT"
echo "WINDOWS_EXPLORER: UNVERIFIED" | tee -a "$REPORT"
echo "FAILS=$FAILS" | tee -a "$REPORT"
ls -la "$SHOT" | tee -a "$REPORT"

if [[ "$FAILS" -gt 0 ]]; then
  echo "FINDER_MENU_ACCEPT_FAIL fails=$FAILS"
  exit 1
fi
echo "FINDER_MENU_ACCEPT_PASS"
echo "Report: $REPORT"
