#!/usr/bin/env bash
# Dogfood: Studio Core builds hello-app and runs it.
# Author: Zhang Xiaoda
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
JAR="$ROOT/target/jnative.jar"
[[ -f "$JAR" ]] || { echo "missing $JAR — run mvn -DskipTests package"; exit 1; }

echo "==> build studio"
java -jar "$JAR" build --project "$ROOT/examples/jnative-studio"
STUDIO="$ROOT/examples/jnative-studio/build/StudioMain"
[[ -x "$STUDIO" ]] || { echo "missing $STUDIO"; exit 1; }

WORKDIR="$(mktemp -d)"
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT
cd "$WORKDIR"

echo "==> open / build / run hello-app"
"$STUDIO" --jar "$JAR" open "$ROOT/examples/hello-app"
"$STUDIO" --jar "$JAR" build
out=$("$STUDIO" --jar "$JAR" run)
echo "$out" | grep -q 'Hello from JNative Studio!'
echo "$out" | grep -q 'Process exited with code 0'
echo "STUDIO_CORE_OK"
