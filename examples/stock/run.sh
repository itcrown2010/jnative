#!/bin/sh
# Start the stock server.
#  JNATIVE_ACCEPT_TIMEOUT_MS      wake the single-threaded server every 50 ms to run the alert monitor, auto scan and
#                                 outcome checks even when nobody is browsing.
#  JNATIVE_ACCEPT_FIRST_BYTE_MS   park new connections until they send something (idle browser/phone pre-connections
#                                 must not block the only worker); parked connections are dropped after 30 s.
#  JNATIVE_TCP_RECV_TIMEOUT_MS    longest stall while reading a request that was started but not finished.
#  JNATIVE_MAJOR_MIN_MB           old-generation headroom before the runtime runs a major collection (memory growth guard).
cd "$(dirname "$0")" || exit 1
export JNATIVE_ACCEPT_TIMEOUT_MS=50
export JNATIVE_ACCEPT_FIRST_BYTE_MS=30000
export JNATIVE_TCP_RECV_TIMEOUT_MS=5000
export JNATIVE_MAJOR_MIN_MB=64
exec ./.jnative/build/stock-server "${1:-18900}" data public
