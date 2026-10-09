#!/bin/bash
set -euo pipefail
LABEL=dev.jnative.erp-demo
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"
if [[ ! -f "$PLIST" ]]; then
  echo "missing LaunchAgent $PLIST — re-run agent setup" >&2
  exit 1
fi
launchctl kickstart -k "gui/$(id -u)/$LABEL"
for i in $(seq 1 40); do
  if curl -sf -m 1 http://127.0.0.1:18700/ready; then
    echo
    echo "OK http://127.0.0.1:18700"
    exit 0
  fi
  sleep 0.5
done
echo "timeout" >&2
exit 1
