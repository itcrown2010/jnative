#!/usr/bin/env bash
# Bootstrap Stage 1 demonstration (tools + lexer compatibility).
# Does NOT claim full self-hosted compiler.
# Author: Zhang Xiaoda
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "JNative Bootstrap Stage 1"
echo "========================="
echo

if [[ ! -f target/jnative.jar ]]; then
  echo "Building Stage 0 compiler (Maven)..."
  mvn -q -DskipTests package
fi
echo "Stage 0 compiler: OK ($("$ROOT/target/jnative.jar" --version 2>/dev/null || java -jar target/jnative.jar --version))"
echo

echo "Building Stage 1 JNative tools..."
java -jar target/jnative.jar build --project examples/jn-tools/jn-project
java -jar target/jnative.jar build --project compiler-selfhost/lexer
echo "OK"
echo

echo "Verifying jn-project tool..."
out=$(examples/jn-tools/jn-project/build/JnProjectMain examples/hello-app)
echo "$out" | grep -q 'name=hello-app'
echo "OK"
echo

echo "Lexer golden compare (reference vs JnLex)..."
bash scripts/selfhost-lex-compare.sh
echo

echo "Parser subset AST compare (reference vs JnAst)..."
bash scripts/selfhost-ast-compare.sh
echo

echo "Compiling bootstrap sample with Stage 0..."
mkdir -p .build-staging
java -jar target/jnative.jar examples/hello-app/src/Main.jn -o .build-staging/bootstrap-hello -O0
out=$(.build-staging/bootstrap-hello)
test "$out" = "Hello from JNative Studio!"
echo "OK"
echo

echo "Self-hosting verification: STAGE1_PASS"
echo "(Levels 1-2 + lexer + parser subset; NOT a self-hosted compiler)"
