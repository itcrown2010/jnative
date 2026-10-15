#!/usr/bin/env bash
# Author: 张晓达 — capture real-app delivery screenshots for UI redesign.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/build/JNativeArchive.app"
BIN="$APP/Contents/MacOS/JNativeArchive"
CLI="$ROOT/build/jn-archive"
LAUNCH="$APP/Contents/Resources/shell/jn-archive-launch.sh"
SHOT="$ROOT/build/ui-delivery-shots"
WORK="$ROOT/build/ui-delivery"
INBOX="${HOME}/Library/Application Support/JNativeArchive/shell-inbox"

rm -rf "$SHOT" "$WORK"
mkdir -p "$SHOT" "$WORK/in" "$WORK/out" "$WORK/conflict/plain"
printf 'a\n' >"$WORK/in/a.txt"
printf 'b\n' >"$WORK/in/b file.txt"
printf '中文\n' >"$WORK/in/中文名.txt"
mkdir -p "$WORK/in/folder"
printf 'inner\n' >"$WORK/in/folder/inner.txt"
dd if=/dev/urandom of="$WORK/in/big.bin" bs=1024 count=1024 status=none 2>/dev/null || true
"$CLI" create "$WORK/plain.zip" "$WORK/in/a.txt" "$WORK/in/b file.txt" "$WORK/in/中文名.txt" "$WORK/in/folder"
printf 'secret' >"$WORK/pass.raw"
"$CLI" --password-fd 3 --encrypt create "$WORK/aes.zip" "$WORK/in/a.txt" 3<"$WORK/pass.raw"
cp "$WORK/plain.zip" "$WORK/conflict/plain.zip"
printf 'OLD\n' >"$WORK/conflict/plain/a.txt"

killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
mkdir -p "$INBOX"

dismiss() {
  osascript -e '
    tell application "System Events"
      if not (exists process "JNativeArchive") then return
      tell process "JNativeArchive"
        repeat with w in (every window)
          try
            if exists button "好" of w then click button "好" of w
            if exists button "确定" of w then click button "确定" of w
          end try
        end repeat
      end tell
    end tell
  ' 2>/dev/null || true
}

echo "==> A1 empty main"
open -a "$APP"
sleep 2
screencapture -x "$SHOT/A1-main-empty.png"

echo "==> A2 open zip"
printf '%s\0' "$WORK/plain.zip" | "$LAUNCH" open >/dev/null 2>&1 || true
sleep 3
dismiss
screencapture -x "$SHOT/A2-main-open.png"

echo "==> A3 create encrypt dialog"
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      set frontmost to true
      try
        click button "新建" of toolbar 1 of window 1
      end try
    end tell
  end tell
' 2>/dev/null || true
sleep 1
# Prefer shell compress-password for prefilled sources
printf '%s\0' "$WORK/in/a.txt" "$WORK/in/中文名.txt" \
  | "$LAUNCH" compress-password >/dev/null 2>&1 || true
sleep 2
screencapture -x "$SHOT/A3-create-encrypt.png"
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      try
        click button "取消" of window "新建压缩包"
      end try
    end tell
  end tell
' 2>/dev/null || true

echo "==> A4 conflict"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"; mkdir -p "$INBOX"
# Sibling folder already has a.txt → conflict UI
printf '%s\0' "$WORK/conflict/plain.zip" | "$LAUNCH" extract-sibling >/dev/null 2>&1 || true
sleep 4
screencapture -x "$SHOT/A4-conflict.png"
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      try
        click button "跳过" of window 1
      end try
      try
        click button "取消本次解压" of window 1
      end try
    end tell
  end tell
' 2>/dev/null || true

echo "==> A5 progress"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"; mkdir -p "$INBOX"
open -a "$APP"
sleep 1
printf '%s\0' "$WORK/in/big.bin" "$WORK/in/a.txt" "$WORK/in/folder" \
  | "$LAUNCH" compress >/dev/null 2>&1 || true
sleep 1
screencapture -x "$SHOT/A5-progress.png"
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      try
        click button "取消" of window "新建压缩包"
      end try
      try
        click button "取消" of toolbar 1 of window 1
      end try
    end tell
  end tell
' 2>/dev/null || true
sleep 1
# Force a running job for progress if create dialog was cancelled without start
printf '%s\0' "$WORK/in/big.bin" | "$LAUNCH" compress >/dev/null 2>&1 || true
sleep 1
# Click 创建 if dialog open
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      try
        click button "创建" of window "新建压缩包"
      end try
    end tell
  end tell
' 2>/dev/null || true
sleep 0.8
screencapture -x "$SHOT/A5b-progress-running.png"
osascript -e '
  tell application "System Events"
    tell process "JNativeArchive"
      try
        click button "取消" of toolbar 1 of window 1
      end try
    end tell
  end tell
' 2>/dev/null || true

echo "==> A6 failure / password"
killall JNativeArchive 2>/dev/null || true
sleep 1
rm -rf "$INBOX"; mkdir -p "$INBOX"
printf '%s\0' "$WORK/aes.zip" | "$LAUNCH" extract-sibling >/dev/null 2>&1 || true
sleep 3
screencapture -x "$SHOT/A6-password-or-error.png"
dismiss

killall JNativeArchive 2>/dev/null || true
ls -la "$SHOT"
echo "UI_DELIVERY_SHOTS=$SHOT"
