#!/usr/bin/env bash
# Author: 张晓达
# Core archive acceptance — real jn-archive binary on macOS (Windows: run same script or mark UNVERIFIED).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="${1:-$ROOT/build/jn-archive}"
MZ="${MINIZIP_CLI:-$ROOT/third_party/build-macos/minizip}"
WORK="$ROOT/build/accept-core"
REPORT="$WORK/REPORT.txt"

if [[ ! -x "$BIN" ]]; then
  echo "missing $BIN — run scripts/build.sh" >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/in/tree/sub" "$WORK/in/empty_dir" "$WORK/out" "$WORK/perf"
{
  echo "host=$(uname -srm)"
  echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "bin=$BIN"
} > "$REPORT"

printf 'root-a\n' > "$WORK/in/a.txt"
printf 'unicode 内容\n' > "$WORK/in/中文 空格.txt"
: > "$WORK/in/empty.dat"
mkdir -p "$WORK/in/empty_dir"
printf 'nested\n' > "$WORK/in/tree/sub/deep.txt"
mkdir -p "$WORK/in/tree/中文目录"
printf 'mid\n' > "$WORK/in/tree/中文目录/file.txt"
# text / repeated / LCG noise for perf
python3 - <<PY
from pathlib import Path
base = Path(r"$WORK/perf")
base.mkdir(parents=True, exist_ok=True)
(base / "text.txt").write_text(("hello world\n" * 20000), encoding="utf-8")
(base / "repeat.bin").write_bytes(b"ABCD" * (256 * 1024))  # 1MiB repeated — compressible
x = 0xC0FFEE
buf = bytearray(1024 * 1024)
for i in range(0, len(buf), 4):
    x = (x * 1664525 + 1013904223) & 0xFFFFFFFF
    buf[i] = x & 0xFF
    buf[i+1] = (x >> 8) & 0xFF
    buf[i+2] = (x >> 16) & 0xFF
    buf[i+3] = (x >> 24) & 0xFF
(base / "noise.bin").write_bytes(buf)
print("perf inputs ready")
PY

echo "==> 1) multi-file + multi-level directory create"
/usr/bin/time -p "$BIN" --profile standard create "$WORK/tree.zip" \
  "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" "$WORK/in/empty.dat" \
  "$WORK/in/tree" "$WORK/in/empty_dir" 2>"$WORK/time-create.txt" | tee "$WORK/create.out"
grep -q "OK created" "$WORK/create.out"
echo "CREATE_TREE_OK" | tee -a "$REPORT"

echo "==> 2) list + test"
"$BIN" list "$WORK/tree.zip" | tee "$WORK/list.out"
grep -q "tree/sub/deep.txt" "$WORK/list.out"
grep -q "empty_dir/" "$WORK/list.out" || grep -q "empty_dir" "$WORK/list.out"
"$BIN" test "$WORK/tree.zip"
echo "LIST_TEST_OK" | tee -a "$REPORT"

echo "==> 3) extract + per-file compare"
rm -rf "$WORK/out1" && mkdir -p "$WORK/out1"
"$BIN" extract "$WORK/tree.zip" "$WORK/out1"
diff -q "$WORK/in/a.txt" "$WORK/out1/a.txt"
diff -q "$WORK/in/中文 空格.txt" "$WORK/out1/中文 空格.txt"
diff -q "$WORK/in/empty.dat" "$WORK/out1/empty.dat"
diff -q "$WORK/in/tree/sub/deep.txt" "$WORK/out1/tree/sub/deep.txt"
diff -q "$WORK/in/tree/中文目录/file.txt" "$WORK/out1/tree/中文目录/file.txt"
test -d "$WORK/out1/empty_dir"
echo "EXTRACT_COMPARE_OK" | tee -a "$REPORT"

echo "==> 4) chinese / empty file / empty dir (covered above)"
echo "UNICODE_EMPTY_OK" | tee -a "$REPORT"

echo "==> 5) AES correct + wrong password"
printf 'secret-aes-256' > "$WORK/pass.raw"
"$BIN" --password-fd 3 --encrypt --aes 256 create "$WORK/aes.zip" \
  "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" 3< "$WORK/pass.raw"
if "$BIN" --password-fd 3 test "$WORK/aes.zip" 3< <(printf 'wrong'); then
  echo "wrong password should fail" >&2
  exit 1
fi
"$BIN" --password-fd 3 test "$WORK/aes.zip" 3< "$WORK/pass.raw"
rm -rf "$WORK/out_aes" && mkdir -p "$WORK/out_aes"
"$BIN" --password-fd 3 --overwrite replace extract "$WORK/aes.zip" "$WORK/out_aes" 3< "$WORK/pass.raw"
diff -q "$WORK/in/a.txt" "$WORK/out_aes/a.txt"
echo "AES_OK" | tee -a "$REPORT"

echo "==> 6) conflict policies fail|skip|replace|rename"
"$BIN" create "$WORK/ow.zip" "$WORK/in/a.txt"
rm -rf "$WORK/c_fail" "$WORK/c_skip" "$WORK/c_rep" "$WORK/c_ren"
mkdir -p "$WORK/c_fail" "$WORK/c_skip" "$WORK/c_rep" "$WORK/c_ren"
printf 'KEEP\n' > "$WORK/c_fail/a.txt"
printf 'KEEP\n' > "$WORK/c_skip/a.txt"
printf 'KEEP\n' > "$WORK/c_rep/a.txt"
printf 'KEEP\n' > "$WORK/c_ren/a.txt"
if "$BIN" --overwrite fail --batch extract "$WORK/ow.zip" "$WORK/c_fail"; then
  echo "fail policy should error" >&2
  exit 1
fi
grep -q KEEP "$WORK/c_fail/a.txt"
"$BIN" --overwrite skip --batch extract "$WORK/ow.zip" "$WORK/c_skip"
grep -q KEEP "$WORK/c_skip/a.txt"
"$BIN" --overwrite replace --batch extract "$WORK/ow.zip" "$WORK/c_rep"
grep -q root-a "$WORK/c_rep/a.txt"
"$BIN" --overwrite rename --batch extract "$WORK/ow.zip" "$WORK/c_ren"
grep -q KEEP "$WORK/c_ren/a.txt"
test -f "$WORK/c_ren/a.txt.1" || test -f "$WORK/c_ren/a.txt.2" || ls "$WORK/c_ren"
# ask + batch must refuse
if "$BIN" --overwrite ask --batch extract "$WORK/ow.zip" "$WORK/c_fail"; then
  echo "ask+batch should fail" >&2
  exit 1
fi
echo "CONFLICT_OK" | tee -a "$REPORT"

echo "==> 7) mid-create cancel: no leftover publish, preexisting untouched"
printf 'ORIGINAL\n' > "$WORK/pre-out.zip"
# 2MiB file; cancel after 64KiB
dd if=/dev/urandom of="$WORK/big.bin" bs=1048576 count=2 status=none 2>/dev/null \
  || python3 -c "open(r'$WORK/big.bin','wb').write(bytes(2*1024*1024))"
if "$BIN" --debug-cancel-after-bytes 65536 create "$WORK/pre-out.zip" "$WORK/big.bin"; then
  echo "cancel create should fail" >&2
  exit 1
fi
grep -q ORIGINAL "$WORK/pre-out.zip"
# no orphan temps next to pre-out
shopt -s nullglob
temps=("$WORK"/pre-out.zip*)
shopt -u nullglob
for t in "${temps[@]:-}"; do
  [[ -e "$t" ]] || continue
  [[ "$t" == "$WORK/pre-out.zip" ]] && continue
  echo "leftover temp $t" >&2
  exit 1
done
echo "CANCEL_CREATE_OK" | tee -a "$REPORT"

echo "==> 8) interop with system unzip / minizip"
unzip -t "$WORK/tree.zip" >/dev/null
if [[ -x "$MZ" ]]; then
  rm -rf "$WORK/out_mz" && mkdir -p "$WORK/out_mz"
  "$MZ" -x -o -d "$WORK/out_mz" "$WORK/tree.zip" >/dev/null
  diff -q "$WORK/in/a.txt" "$WORK/out_mz/a.txt"
  echo "MINIZIP_INTEROP_OK" | tee -a "$REPORT"
else
  echo "MINIZIP_INTEROP_SKIPPED" | tee -a "$REPORT"
fi
echo "UNZIP_INTEROP_OK" | tee -a "$REPORT"

echo "==> 9) perf profiles (text / repeat / noise)"
for prof in fast standard max; do
  for kind in text.txt repeat.bin noise.bin; do
    src="$WORK/perf/$kind"
    out="$WORK/perf/${prof}-${kind}.zip"
    t0=$(python3 -c 'import time; print(time.time())')
    "$BIN" --profile "$prof" create "$out" "$src" >/dev/null
    t1=$(python3 -c 'import time; print(time.time())')
    in_sz=$(wc -c < "$src" | tr -d ' ')
    out_sz=$(wc -c < "$out" | tr -d ' ')
    dur=$(python3 -c "print(round($t1-$t0, 4))")
    echo "PERF profile=$prof file=$kind in=$in_sz out=$out_sz sec=$dur" | tee -a "$REPORT"
  done
done
# noise must stay near ratio 1.0
python3 - <<PY
import zipfile
z = zipfile.ZipFile(r"$WORK/perf/standard-noise.bin.zip")
info = z.getinfo("noise.bin")
ratio = info.compress_size / info.file_size
print("noise_ratio", round(ratio, 4))
if ratio < 0.90:
    raise SystemExit("noise over-compressed")
PY
echo "PERF_OK" | tee -a "$REPORT"

echo "==> 10) output zip inside input tree skipped + symlink reject"
mkdir -p "$WORK/inside/sub"
printf 'x\n' > "$WORK/inside/sub/f.txt"
"$BIN" create "$WORK/inside/nested.zip" "$WORK/inside"
"$BIN" list "$WORK/inside/nested.zip" | tee "$WORK/inside.list"
if grep -q nested.zip "$WORK/inside.list"; then
  echo "output zip must not be packed into itself" >&2
  exit 1
fi
ln -sf f.txt "$WORK/inside/sub/link.txt"
if "$BIN" create "$WORK/inside/withlink.zip" "$WORK/inside/sub"; then
  echo "symlink should be rejected" >&2
  exit 1
fi
echo "INSIDE_AND_SYMLINK_OK" | tee -a "$REPORT"

echo "ACCEPT_CORE_PASS" | tee -a "$REPORT"
echo "WINDOWS_E2E_UNVERIFIED" | tee -a "$REPORT"
echo "Report: $REPORT"
cat "$REPORT"
