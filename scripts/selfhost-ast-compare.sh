#!/usr/bin/env bash
# Compare reference AstPrinter dumps vs JNative JnAstMain dumps.
# Author: Zhang Xiaoda
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/target/jnative.jar"
CORPUS="$ROOT/tests/selfhost/parser/corpus"
OUT="$ROOT/.build-staging/selfhost-ast"
mkdir -p "$OUT"

[[ -f "$JAR" ]] || { echo "missing $JAR"; exit 1; }

echo "==> build JnAstMain"
java -jar "$JAR" build --project "$ROOT/compiler-selfhost/parser"
JNAST="$ROOT/compiler-selfhost/parser/build/JnAstMain"
[[ -x "$JNAST" ]] || { echo "missing $JNAST"; exit 1; }

norm() {
  python3 -c 'import pathlib,sys; p=pathlib.Path(sys.argv[1]); t=p.read_text().rstrip("\n")+"\n"; pathlib.Path(sys.argv[2]).write_text(t)' "$1" "$2"
}

fail=0
for src in "$CORPUS"/*.jn; do
  base=$(basename "$src" .jn)
  echo "==> $base"
  java -cp "$JAR" io.jnative.selfhost.RefAstDump "$src" >"$OUT/$base.ref.raw"
  "$JNAST" "$src" >"$OUT/$base.jn.raw"
  norm "$OUT/$base.ref.raw" "$OUT/$base.ref.txt"
  norm "$OUT/$base.jn.raw" "$OUT/$base.jn.txt"
  if ! diff -u "$OUT/$base.ref.txt" "$OUT/$base.jn.txt"; then
    echo "FAIL $base"
    fail=1
  else
    echo "OK $base"
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "SELFHOST_AST_FAIL"
  exit 1
fi
echo "SELFHOST_AST_OK"
