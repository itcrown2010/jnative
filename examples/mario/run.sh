#!/bin/sh
# Build (if needed) and start the game.  Usage: ./run.sh [--selftest]
cd "$(dirname "$0")" || exit 1
JN="${JNATIVE:-java -cp ../../target/classes io.jnative.Main}"
if [ ! -x .jnative/build/mario ] || [ -n "$(find src -newer .jnative/build/mario -name '*.java' 2>/dev/null)" ]; then
  $JN build --project . || exit 1
fi
exec ./.jnative/build/mario "$@"
