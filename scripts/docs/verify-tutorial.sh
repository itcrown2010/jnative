#!/usr/bin/env bash
# Author: 张晓达
# Compile/run tutorial examples and compare expected outputs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
JAR="$ROOT/target/jnative.jar"
OUT="$ROOT/build/tutorial-verify"
EXP="$ROOT/examples/tutorial/_expected"
mkdir -p "$OUT" "$ROOT/build"

[[ -f "$JAR" ]] || { echo "missing $JAR"; exit 1; }

run_check() {
  local name="$1"
  local bin="$2"
  shift 2
  local got exp
  got=$("$bin" "$@" 2>&1 | tr -d '\r')
  exp=$(cat "$EXP/${name}.txt" | tr -d '\r')
  if [[ "$got" != "$exp" ]]; then
    echo "FAIL output: $name"
    echo "--- got ---"; echo "$got"
    echo "--- exp ---"; echo "$exp"
    return 1
  fi
  echo "OK $name"
}

fail=0

# Single-file chapters
for name in 01-hello 06-vars 07-numbers 08-strings 09-ops 10-control 11-methods \
            12-arrays 13-objects 14-access 15-inherit 16-enum-final \
            19-generics 20-collections 21-lambda 22-exceptions 23-files; do
  src="$ROOT/examples/tutorial/${name}/Main.jn"
  if ! java -jar "$JAR" "$src" -o "$OUT/$name" -O0 >/tmp/tut-c.err 2>&1; then
    echo "FAIL compile $name"; tail -5 /tmp/tut-c.err; fail=1; continue
  fi
  run_check "$name" "$OUT/$name" || fail=1
done

# 04 project
if ! (cd "$ROOT/examples/tutorial/04-project" && java -jar "$JAR" build >/tmp/tut-p.err 2>&1); then
  echo "FAIL 04-project build"; tail -5 /tmp/tut-p.err; fail=1
else
  run_check "04-project" "$ROOT/examples/tutorial/04-project/build/tutorial-04" || fail=1
fi

# 17 multifile
if ! (cd "$ROOT/examples/tutorial/17-multifile" && java -jar "$JAR" build >/tmp/tut-m.err 2>&1); then
  echo "FAIL 17-multifile"; tail -5 /tmp/tut-m.err; fail=1
else
  run_check "17-multifile" "$ROOT/examples/tutorial/17-multifile/build/tutorial-multifile" || fail=1
fi

# 05 negative
if java -jar "$JAR" "$ROOT/examples/tutorial/05-errors/Bad.jn" -o "$OUT/Bad" -O0 >/tmp/tut-bad.out 2>&1; then
  echo "FAIL 05-errors: expected compile failure"; fail=1
else
  if rg -q "incompatible types: String cannot be converted to int" /tmp/tut-bad.out; then
    echo "OK 05-errors (diagnostic)"
  else
    echo "FAIL 05-errors: unexpected diagnostic"; cat /tmp/tut-bad.out | head -10; fail=1
  fi
fi

# task manager demos
for s in stage1 stage2 stage3 stage4; do
  if ! java -jar "$JAR" "$ROOT/examples/tutorial/task-manager/$s/Main.jn" -o "$OUT/tm-$s" -O0 >/tmp/tut-tm.err 2>&1; then
    echo "FAIL compile tm-$s"; tail -5 /tmp/tut-tm.err; fail=1; continue
  fi
  run_check "tm-$s-demo" "$OUT/tm-$s" demo || fail=1
done

# sync docs → website
mkdir -p "$ROOT/website/tutorial"
rsync -a --delete "$ROOT/docs/tutorial/" "$ROOT/website/tutorial/"

if [[ -d "$ROOT/website/node_modules" ]]; then
  (cd "$ROOT/website" && npm run build >/tmp/tut-site.err 2>&1) || {
    echo "FAIL website build"; tail -20 /tmp/tut-site.err; fail=1
  }
fi

[[ "$fail" == "0" ]] || exit 1
echo "TUTORIAL_VERIFY_OK"
