#!/usr/bin/env bash
# Author: 张晓达
# Phase B evidence: AES interop, password fd, path safety, cancel, limits, ZIP64-ish streaming.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="${1:-$ROOT/build/jn-archive}"
MZ="${MINIZIP_CLI:-$ROOT/third_party/build-macos/minizip}"
WORK="$ROOT/build/phase-b-smoke"
PASS_FILE="$WORK/pass.txt"

if [[ ! -x "$BIN" ]]; then
  echo "missing $BIN — run scripts/build.sh" >&2
  exit 1
fi
if [[ ! -x "$MZ" ]]; then
  echo "missing minizip CLI at $MZ (build minizip-ng with CLI)" >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/in" "$WORK/out" "$WORK/out_aes" "$WORK/out_ext" "$WORK/evil" "$WORK/preexist"

printf 'phase-b plain\n' > "$WORK/in/a.txt"
printf 'unicode 内容\n' > "$WORK/in/中文 空格.txt"
: > "$WORK/in/empty.dat"
# high-compressibility
python3 - <<'PY' > "$WORK/in/zeros.bin"
import sys
sys.stdout.buffer.write(b"\0" * (2 * 1024 * 1024))
PY
# low-compressibility: full 32-bit LCG stream as little-endian words (not low-8 / 256-byte period)
python3 - <<PY
path = r"$WORK/in/noise.bin"
x = 0xC0FFEE
with open(path, "wb") as f:
    buf = bytearray(4 * 512 * 1024)  # 2MiB
    for i in range(0, len(buf), 4):
        x = (x * 1664525 + 1013904223) & 0xFFFFFFFF
        buf[i] = x & 0xFF
        buf[i+1] = (x >> 8) & 0xFF
        buf[i+2] = (x >> 16) & 0xFF
        buf[i+3] = (x >> 24) & 0xFF
    f.write(buf)
print("noise", path, "bytes", len(buf))
PY

printf '正确密码-Unicode-密码\n' > "$PASS_FILE"
# password file must not end with issues — use printf without newline for fd test
printf 'secret-aes-256' > "$WORK/pass.raw"

echo "==> version / crypto"
"$BIN" version

echo "==> plain create/list/test/extract + sha256"
"$BIN" create "$WORK/plain.zip" "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" "$WORK/in/empty.dat"
"$BIN" list "$WORK/plain.zip"
"$BIN" test "$WORK/plain.zip"
"$BIN" extract "$WORK/plain.zip" "$WORK/out"
(cd "$WORK/in" && shasum -a 256 a.txt "中文 空格.txt" empty.dat) > "$WORK/h1"
(cd "$WORK/out" && shasum -a 256 a.txt "中文 空格.txt" empty.dat) > "$WORK/h2"
diff -u "$WORK/h1" "$WORK/h2"
echo HASH_PLAIN_OK

echo "==> AES create via --password-fd (no argv password)"
"$BIN" --password-fd 3 --encrypt --aes 256 create "$WORK/aes.zip" \
  "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" 3< "$WORK/pass.raw"
"$BIN" list "$WORK/aes.zip" | tee "$WORK/aes.list"
grep ENC "$WORK/aes.list"
grep AES-256 "$WORK/aes.list"

echo "==> AES wrong password must fail (no success files)"
rm -rf "$WORK/out_bad" && mkdir -p "$WORK/out_bad"
printf 'wrong' > "$WORK/pass.wrong"
if "$BIN" --password-fd 3 test "$WORK/aes.zip" 3< "$WORK/pass.wrong"; then
  echo "wrong password test should fail" >&2
  exit 1
fi
if "$BIN" --password-fd 3 extract "$WORK/aes.zip" "$WORK/out_bad" 3< "$WORK/pass.wrong"; then
  echo "wrong password extract should fail" >&2
  exit 1
fi
# no published content
if [[ -f "$WORK/out_bad/a.txt" ]]; then
  echo "wrong password left published file" >&2
  exit 1
fi
echo WRONG_PASSWORD_OK

echo "==> AES correct password extract + hash"
"$BIN" --password-fd 3 test "$WORK/aes.zip" 3< "$WORK/pass.raw"
"$BIN" --password-fd 3 --overwrite replace extract "$WORK/aes.zip" "$WORK/out_aes" 3< "$WORK/pass.raw"
(cd "$WORK/out_aes" && shasum -a 256 a.txt "中文 空格.txt") > "$WORK/h_aes"
(cd "$WORK/in" && shasum -a 256 a.txt "中文 空格.txt") > "$WORK/h_in2"
diff -u "$WORK/h_in2" "$WORK/h_aes"
echo HASH_AES_OK

echo "==> external minizip AES create → our test/extract"
(cd "$WORK/in" && "$MZ" -0 -s -p 'secret-aes-256' "$WORK/ext-aes.zip" a.txt)
"$BIN" --password-fd 3 test "$WORK/ext-aes.zip" 3< "$WORK/pass.raw"
rm -rf "$WORK/out_ext" && mkdir -p "$WORK/out_ext"
"$BIN" --password-fd 3 --overwrite replace extract "$WORK/ext-aes.zip" "$WORK/out_ext" 3< "$WORK/pass.raw"
diff -u "$WORK/in/a.txt" "$WORK/out_ext/a.txt"
echo EXT_AES_TO_OURS_OK

echo "==> our AES → external minizip extract"
rm -rf "$WORK/out_mz" && mkdir -p "$WORK/out_mz"
"$MZ" -x -o -p 'secret-aes-256' -d "$WORK/out_mz" "$WORK/aes.zip"
diff -u "$WORK/in/a.txt" "$WORK/out_mz/a.txt"
echo OURS_AES_TO_EXT_OK

echo "==> empty password rejected"
: > "$WORK/pass.empty"
if "$BIN" --password-fd 3 --encrypt create "$WORK/empty-pass.zip" "$WORK/in/a.txt" 3< "$WORK/pass.empty"; then
  echo "empty password should fail" >&2
  exit 1
fi
echo EMPTY_PASSWORD_REJECT_OK

echo "==> path escape rejected"
# craft zip with ../ via Python struct (store method, no compress)
python3 - <<'PY'
import struct, zlib, time, os
work = os.environ.get("WORK") or ""
# use fixed path
base = "examples/jnative-archive/build/phase-b-smoke"
name = b"../evil.txt"
data = b"pwn\n"
crc = zlib.crc32(data) & 0xffffffff
# local header
lh = struct.pack("<IHHHHHIIIHH", 0x04034b50, 20, 0, 0, 0, 0, crc, len(data), len(data), len(name), 0)
# central
ch = struct.pack("<IHHHHHHIIIHHHHHII", 0x02014b50, 20, 20, 0, 0, 0, 0, crc, len(data), len(data),
                 len(name), 0, 0, 0, 0, 0, 0)
# end
cd_offset = len(lh) + len(name) + len(data)
cd_size = len(ch) + len(name)
eh = struct.pack("<IHHHHIIH", 0x06054b50, 0, 0, 1, 1, cd_size, cd_offset, 0)
path = base + "/evil-path.zip"
with open(path, "wb") as f:
    f.write(lh); f.write(name); f.write(data); f.write(ch); f.write(name); f.write(eh)
print("wrote", path)
PY
mkdir -p "$WORK/safe"
if "$BIN" extract "$WORK/evil-path.zip" "$WORK/safe"; then
  echo "evil path should fail" >&2
  exit 1
fi
if [[ -f "$WORK/evil.txt" ]]; then
  echo "path escape wrote outside dest" >&2
  exit 1
fi
echo PATH_ESCAPE_OK

echo "==> overwrite fail preserves preexisting"
printf 'keep-me\n' > "$WORK/preexist/a.txt"
"$BIN" create "$WORK/ow.zip" "$WORK/in/a.txt"
if "$BIN" --overwrite fail extract "$WORK/ow.zip" "$WORK/preexist"; then
  echo "overwrite fail should error" >&2
  exit 1
fi
grep -q keep-me "$WORK/preexist/a.txt"
echo OVERWRITE_FAIL_OK

echo "==> streaming large-ish files (2MiB zeros + 2MiB noise) ZIP64-ready path"
"$BIN" --progress create "$WORK/big.zip" "$WORK/in/zeros.bin" "$WORK/in/noise.bin"
"$BIN" test "$WORK/big.zip"
rm -rf "$WORK/out_big" && mkdir -p "$WORK/out_big"
"$BIN" extract "$WORK/big.zip" "$WORK/out_big"
diff -q "$WORK/in/zeros.bin" "$WORK/out_big/zeros.bin"
diff -q "$WORK/in/noise.bin" "$WORK/out_big/noise.bin"
# noise must not collapse like a 256-byte-period sample (deflate ratio near 1.0)
python3 - <<PY
import zipfile, os
z = zipfile.ZipFile(r"$WORK/big.zip")
info = z.getinfo("noise.bin")
u, c = info.file_size, info.compress_size
ratio = c / u if u else 0
print("noise compress ratio", round(ratio, 4), "uncomp", u, "comp", c)
if ratio < 0.90:
    raise SystemExit("noise over-compressed (ratio %s); sample likely periodic" % ratio)
PY
echo STREAM_LARGE_OK

echo "==> many entries (ZIP64 entry count path; default 70000, override MANY_ENTRIES=)"
MANY="${MANY_ENTRIES:-70000}"
python3 - <<PY
import os, struct
base = r"$WORK"
n = int("$MANY")
path = os.path.join(base, "many.zip")
central = []
with open(path, "wb") as f:
    for i in range(n):
        name = ("e%05d.dat" % i).encode()
        crc = 0
        lh = struct.pack("<IHHHHHIIIHH", 0x04034b50, 20, 0, 0, 0, 0, crc, 0, 0, len(name), 0)
        local_off = f.tell()
        f.write(lh); f.write(name)
        ch = struct.pack("<IHHHHHHIIIHHHHHII", 0x02014b50, 20, 20, 0, 0, 0, 0, crc, 0, 0,
                         len(name), 0, 0, 0, 0, 0, local_off)
        central.append(ch + name)
    cd_start = f.tell()
    for c in central:
        f.write(c)
    cd_size = f.tell() - cd_start
    if n >= 65535:
        z64_offset = f.tell()
        f.write(struct.pack("<IQHHIIQQQQ", 0x06064b50, 44, 45, 45, 0, 0, n, n, cd_size, cd_start))
        f.write(struct.pack("<IIQI", 0x07064b50, 0, z64_offset, 1))
        f.write(struct.pack("<IHHHHIIH", 0x06054b50, 0, 0, 0xFFFF, 0xFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0))
    else:
        f.write(struct.pack("<IHHHHIIH", 0x06054b50, 0, 0, n, n, cd_size, cd_start, 0))
print("many entries", n, "->", path)
PY
/usr/bin/time -l "$BIN" test "$WORK/many.zip" 2>"$WORK/many.time" | tee "$WORK/many.out"
grep -q "OK test passed" "$WORK/many.out"
echo "MANY_ENTRIES_OK n=$MANY (see $WORK/many.time for peak RSS)"

if [[ "${ZIP64_LARGE:-0}" == "1" ]]; then
  echo "==> optional >4GiB file (ZIP64_LARGE=1)"
  # 4100 MiB low-compressibility stream (32-bit LCG LE words) — long running
  python3 - <<PY
import os
path = r"$WORK/in/huge.bin"
x = 0x12345678
chunk = bytearray(1024 * 1024)
with open(path, "wb") as f:
    for _ in range(4100):
        for i in range(0, len(chunk), 4):
            x = (x * 1664525 + 1013904223) & 0xFFFFFFFF
            chunk[i] = x & 0xFF
            chunk[i+1] = (x >> 8) & 0xFF
            chunk[i+2] = (x >> 16) & 0xFF
            chunk[i+3] = (x >> 24) & 0xFF
        f.write(chunk)
print("wrote", path, os.path.getsize(path))
PY
  "$BIN" create "$WORK/huge.zip" "$WORK/in/huge.bin"
  "$BIN" test "$WORK/huge.zip"
  echo ZIP64_4GIB_OK
else
  echo "ZIP64_4GIB_SKIPPED (set ZIP64_LARGE=1 to run; disk/time heavy)"
fi

echo "==> truncated archive fails"
head -c 40 "$WORK/plain.zip" > "$WORK/trunc.zip"
if "$BIN" test "$WORK/trunc.zip"; then
  echo "truncated should fail" >&2
  exit 1
fi
echo TRUNC_OK

echo "PHASE_B_SMOKE_PASS"
echo "Artifacts: $WORK"
echo "WINDOWS_E2E_UNVERIFIED"
