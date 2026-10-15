#!/usr/bin/env bash
# Author: 张晓达 — extract staging symlink / publish / unique create-temp regress
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="${1:-$ROOT/build/jn-archive}"
WORK="$ROOT/build/regress-security"
rm -rf "$WORK"
mkdir -p "$WORK/dest" "$WORK/victim_dir" "$WORK/in"
printf 'hello-secure\n' > "$WORK/in/a.txt"
printf 'ORIGINAL\n' > "$WORK/victim_dir/victim.txt"

"$BIN" create "$WORK/a.zip" "$WORK/in/a.txt"

echo "==> symlink staging hijack must not rewrite victim"
# Pre-create symlink where old code used fixed .jn-extract-tmp
ln -s "$WORK/victim_dir" "$WORK/dest/.jn-extract-tmp"
# Also plant a predictable temp name (old pattern) as symlink to victim
mkdir -p "$WORK/dest/.jn-ex-fake" 2>/dev/null || true
# Extract into dest — must succeed via mkdtemp, not follow .jn-extract-tmp
"$BIN" --overwrite replace extract "$WORK/a.zip" "$WORK/dest"
# Victim must remain ORIGINAL
grep -q ORIGINAL "$WORK/victim_dir/victim.txt"
# Published file must be a regular file, not a symlink
[[ -f "$WORK/dest/a.txt" ]]
[[ ! -L "$WORK/dest/a.txt" ]]
diff -u "$WORK/in/a.txt" "$WORK/dest/a.txt"
echo SYMLINK_STAGING_OK

echo "==> overwrite fail keeps preexisting"
printf 'keep\n' > "$WORK/dest/a.txt"
if "$BIN" --overwrite fail extract "$WORK/a.zip" "$WORK/dest"; then
  echo "expected exists error" >&2
  exit 1
fi
grep -q keep "$WORK/dest/a.txt"
echo OVERWRITE_KEEP_OK

echo "==> unique create temps (concurrent-ish sequential)"
"$BIN" create "$WORK/out1.zip" "$WORK/in/a.txt" &
P1=$!
"$BIN" create "$WORK/out2.zip" "$WORK/in/a.txt" &
P2=$!
wait $P1
wait $P2
"$BIN" test "$WORK/out1.zip"
"$BIN" test "$WORK/out2.zip"
echo UNIQUE_CREATE_OK

echo "==> password option must not echo secret"
set +e
OUT=$("$BIN" --password=review-test-only create "$WORK/x.zip" "$WORK/in/a.txt" 2>&1)
RC=$?
set -e
if [[ $RC -eq 0 ]]; then
  echo "expected failure for --password=" >&2
  exit 1
fi
if echo "$OUT" | grep -q 'review-test-only'; then
  echo "PASSWORD LEAKED IN OUTPUT: $OUT" >&2
  exit 1
fi
echo "$OUT" | grep -q redacted
echo PASSWORD_REDACT_OK

echo "==> empty password fd cleans temp"
: > "$WORK/empty.pass"
set +e
"$BIN" --password-fd 3 --encrypt create "$WORK/empty-out.zip" "$WORK/in/a.txt" 3< "$WORK/empty.pass"
RC=$?
set -e
[[ $RC -ne 0 ]]
# no leftover unique temps next to empty-out.zip (mkstemp siblings)
shopt -s nullglob
LEFTOVERS=("$WORK"/empty-out.zip*)
shopt -u nullglob
if ((${#LEFTOVERS[@]} > 0)); then
  for f in "${LEFTOVERS[@]}"; do
    [[ -e "$f" ]] || continue
    [[ "$f" == "$WORK/empty-out.zip" ]] && continue
    echo "leftover temp: $f" >&2
    exit 1
  done
fi
echo EMPTY_PASS_CLEAN_OK

echo "REGRESS_SECURITY_PASS"
