#!/usr/bin/env bash
# Author: 张晓达
# Finder Quick Action launcher. Paths go into a length-prefixed job file.
# Never pass filenames through shell word-splitting or eval; never store passwords.
#
# Usage: jn-archive-launch.sh <action> <path> [<path> ...]
#    or: jn-archive-launch.sh <action>   with NUL-separated paths on stdin
set -euo pipefail

ACTION="${1:-}"
if [[ -z "$ACTION" ]]; then
  echo "usage: $0 <action> [paths...]" >&2
  exit 2
fi
shift

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_FROM_BUNDLE=""
# When embedded: .../JNativeArchive.app/Contents/Resources/shell/jn-archive-launch.sh
if [[ "$SCRIPT_DIR" == *"/Contents/Resources/shell" ]]; then
  APP_FROM_BUNDLE="$(cd "$SCRIPT_DIR/../../.." && pwd)"
fi

# Prefer the bundle that contains this launcher, then standard install locations.
# JNATIVE_ARCHIVE_APP overrides only when it still exists (dev/testing).
APP_CANDIDATES=(
  "$APP_FROM_BUNDLE"
  "$HOME/Applications/JNativeArchive.app"
  "/Applications/JNativeArchive.app"
  "${JNATIVE_ARCHIVE_APP:-}"
)
APP=""
for c in "${APP_CANDIDATES[@]}"; do
  if [[ -n "$c" && -x "$c/Contents/MacOS/JNativeArchive" ]]; then
    APP="$c"
    break
  fi
done
if [[ -z "$APP" ]]; then
  echo "JNativeArchive.app not found. Run integration/macos/install.sh first." >&2
  exit 1
fi

INBOX="${HOME}/Library/Application Support/JNativeArchive/shell-inbox"
mkdir -p "$INBOX"
chmod 700 "${HOME}/Library/Application Support/JNativeArchive" 2>/dev/null || true
chmod 700 "$INBOX" 2>/dev/null || true

JOB="${INBOX}/job-$(date +%s)-$$.job"
JOB_TMP="${JOB}.tmp"
PATHS_FILE="$(mktemp -t jnarch-paths)"
cleanup() { rm -f "$PATHS_FILE" "$JOB_TMP"; }
trap cleanup EXIT

if [[ $# -gt 0 ]]; then
  : >"$PATHS_FILE"
  for p in "$@"; do
    printf '%s\0' "$p" >>"$PATHS_FILE"
  done
else
  cat >"$PATHS_FILE"
fi

python3 - "$JOB_TMP" "$ACTION" "$PATHS_FILE" <<'PY'
import os, sys
out, action, paths_file = sys.argv[1], sys.argv[2], sys.argv[3]
allowed = {
    "compress", "compress-password",
    "extract-here", "extract-sibling", "extract-to", "open",
}
if action not in allowed:
    raise SystemExit("invalid action: " + action)
raw = open(paths_file, "rb").read()
parts = [p for p in raw.split(b"\0") if p]
paths = [os.path.abspath(os.fsdecode(b)) for b in parts]
# Password never written to disk.
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("JN1\n")
    f.write(action + "\n")
    f.write(str(len(paths)) + "\n")
    for ap in paths:
        f.write(str(len(ap)) + "\n")
        f.write(ap + "\n")
os.chmod(out, 0o600)
PY

mv -f "$JOB_TMP" "$JOB"
chmod 600 "$JOB" 2>/dev/null || true
trap - EXIT
rm -f "$PATHS_FILE"

# Launch / activate by bundle path (not argv paths). Prefer path form of open.
open "$APP"
echo "Queued action=$ACTION job=$JOB app=$APP"
