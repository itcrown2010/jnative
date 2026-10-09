#!/usr/bin/env bash
# Author: 张晓达
# Phase-1 documentation verification (no public deploy).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

JAR="$ROOT/target/jnative.jar"
JN=(java -jar "$JAR")
if [[ ! -f "$JAR" ]]; then
  echo "error: missing $JAR — run: mvn -DskipTests package" >&2
  exit 1
fi

echo "== version =="
"${JN[@]}" --version

echo "== doctor =="
if ! "${JN[@]}" doctor; then
  echo "error: doctor failed" >&2
  exit 1
fi

echo "== guide-hello build/run =="
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp -R "$ROOT/examples/guide-hello/." "$TMP/guide-hello/"
(
  cd "$TMP/guide-hello"
  "${JN[@]}" build
  out=$(./build/guide-hello)
  echo "output: $out"
  [[ "$out" == "Hello, JNative!" ]] || {
    echo "error: unexpected output: $out" >&2
    exit 1
  }
)

echo "== API extract =="
bash "$ROOT/scripts/docs/extract-api.sh"

echo "== docs site build =="
if ! command -v npm >/dev/null 2>&1; then
  echo "warn: npm not found; skip vitepress build" >&2
else
  (
    cd "$ROOT/website"
    if [[ ! -d node_modules ]]; then
      npm install
    fi
    npm run build
  )
fi

echo "PHASE1_VERIFY_OK"
