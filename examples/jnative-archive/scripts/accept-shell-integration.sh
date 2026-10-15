#!/usr/bin/env bash
# Author: 张晓达 — macOS shell integration acceptance (Quick Actions path + GUI).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="${JNATIVE_ARCHIVE_APP:-$ROOT/build/JNativeArchive.app}"
BIN="$ROOT/build/jn-archive-gui"
CLI="$ROOT/build/jn-archive"
LAUNCH="$ROOT/integration/macos/jn-archive-launch.sh"
SHOT="$ROOT/build/shell-shots"
WORK="$ROOT/build/shell-accept"
SERVICES="${HOME}/Library/Services"
INBOX="${HOME}/Library/Application Support/JNativeArchive/shell-inbox"

if [[ ! -d "$APP" ]]; then
  echo "missing $APP — run scripts/build-gui.sh" >&2
  exit 1
fi
if [[ ! -x "$CLI" ]]; then
  echo "missing CLI" >&2
  exit 1
fi
chmod +x "$LAUNCH"

rm -rf "$SHOT" "$WORK"
mkdir -p "$SHOT" "$WORK/in" "$WORK/out"
# Special names: Chinese, spaces, leading dash, quotes-ish
printf 'a\n' > "$WORK/in/a.txt"
printf '中文\n' > "$WORK/in/中文 空格.txt"
printf 'dash\n' > "$WORK/in/--leading-dash.txt"
mkdir -p "$WORK/in/中文目录"
printf 'd\n' > "$WORK/in/中文目录/file.txt"
printf 'secret-pass' > "$WORK/pass.raw"
"$CLI" --password-fd 3 --encrypt create "$WORK/aes.zip" "$WORK/in/a.txt" 3<"$WORK/pass.raw"
"$CLI" create "$WORK/tree.zip" "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" "$WORK/in/--leading-dash.txt" "$WORK/in/中文目录"

echo "==> install Finder Quick Actions (user Services)"
export JNATIVE_ARCHIVE_APP="$APP"
export JN_ARCHIVE_INSTALL_YES=1
"$ROOT/integration/macos/install-finder-actions.sh"
ls -1 "$SERVICES"/JNative\ Archive*.workflow | tee "$SHOT/installed-workflows.txt"
test "$(ls -1 "$SERVICES"/JNative\ Archive*.workflow | wc -l | tr -d ' ')" -ge 6

echo "==> simulate right-click: open zip (app not running)"
killall JNativeArchive 2>/dev/null || true
killall jn-archive-gui 2>/dev/null || true
sleep 1
rm -rf "$INBOX"
"$LAUNCH" open "$WORK/tree.zip"
sleep 4
screencapture -x "$SHOT/01-open-via-shell.png" || true

echo "==> compress prefill (app already running)"
"$LAUNCH" compress "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" "$WORK/in/--leading-dash.txt"
sleep 3
screencapture -x "$SHOT/02-compress-dialog.png" || true

echo "==> compress-password dialog"
"$LAUNCH" compress-password "$WORK/in/中文目录"
sleep 2
screencapture -x "$SHOT/03-compress-password.png" || true

echo "==> extract-sibling queued while busy/idle"
"$LAUNCH" extract-sibling "$WORK/tree.zip"
sleep 2
screencapture -x "$SHOT/04-extract.png" || true

echo "==> inbox job file format (Chinese path length-prefix)"
python3 - <<PY
from pathlib import Path
inbox = Path.home() / "Library/Application Support/JNativeArchive/shell-inbox"
print("inbox exists", inbox.is_dir())
jobs = list(inbox.glob("*.job")) if inbox.is_dir() else []
print("pending jobs", len(jobs))
for j in jobs[:3]:
    print("---", j.name)
    print(j.read_text(encoding="utf-8")[:200])
PY

killall JNativeArchive 2>/dev/null || true
killall jn-archive-gui 2>/dev/null || true
sleep 1

echo "==> uninstall Quick Actions"
export JN_ARCHIVE_INSTALL_YES=1
"$ROOT/integration/macos/uninstall-finder-actions.sh"
left=$(find "$SERVICES" -maxdepth 1 -type d -name 'JNative Archive*.workflow' 2>/dev/null | wc -l | tr -d ' ')
echo "remaining workflows=$left"
test "$left" = "0"

echo "==> CLI security + core regressions"
"$ROOT/scripts/regress-security.sh"
"$ROOT/scripts/accept-core.sh" "$CLI"

echo "SHELL_INTEGRATION_ACCEPT_PASS"
echo "Screenshots: $SHOT"
ls -la "$SHOT"
echo "MENU_ENTRY: Finder → 右键 → 快速操作 → JNative Archive … (not a top-level Finder menu)"
echo "WINDOWS_CLASSIC_MENU: scripts present; WINDOWS_E2E_UNVERIFIED"
echo "WINDOWS_11_MODERN_MENU: FUTURE"
