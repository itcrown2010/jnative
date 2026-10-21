#!/bin/sh
# Start the stock server. JNATIVE_ACCEPT_TIMEOUT_MS lets the single-threaded server wake up every 50ms
# to run the alert monitor even when nobody is browsing.
cd "$(dirname "$0")" || exit 1
export JNATIVE_ACCEPT_TIMEOUT_MS=50
exec ./.jnative/build/stock-server "${1:-18900}" data public
