#!/usr/bin/env bash
# Compare reference lexer dumps vs JNative JnLexMain dumps.
# Author: Zhang Xiaoda
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/target/jnative.jar"
CORPUS="$ROOT/tests/selfhost/lexer/corpus"
OUT="$ROOT/.build-staging/selfhost-lex"
mkdir -p "$OUT"

[[ -f "$JAR" ]] || { echo "missing $JAR"; exit 1; }

echo "==> build JnLexMain"
java -jar "$JAR" build --project "$ROOT/compiler-selfhost/lexer"
JNLEX="$ROOT/compiler-selfhost/lexer/build/JnLexMain"
[[ -x "$JNLEX" ]] || { echo "missing $JNLEX"; exit 1; }

fail=0
for src in "$CORPUS"/*.jn; do
  base=$(basename "$src" .jn)
  echo "==> $base"
  java -cp "$JAR" io.jnative.selfhost.RefTokenDump "$src" >"$OUT/$base.ref.txt"
  "$JNLEX" "$src" >"$OUT/$base.jn.txt"
  if ! diff -u "$OUT/$base.ref.txt" "$OUT/$base.jn.txt"; then
    echo "FAIL $base"
    fail=1
  else
    echo "OK $base"
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "SELFHOST_LEX_FAIL"
  exit 1
fi
echo "SELFHOST_LEX_OK"
