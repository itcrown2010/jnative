#!/usr/bin/env bash
# Author: 张晓达
# Capability-integration verify for examples/order-processing.
# Usage: from repo root: bash examples/order-processing/scripts/verify.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
APP="$ROOT/examples/order-processing/app"
REG="file://$ROOT/examples/order-processing/registry"
JAR="$ROOT/target/jnative.jar"
OUT_DIR="$APP/build"
BIN="$OUT_DIR/order-app"
ARTIFACT_DIR="$OUT_DIR/verify-artifacts"
TIMEOUT_SEC="${VERIFY_TIMEOUT:-60}"

die() { echo "FAIL: $*" >&2; exit 1; }
log() { echo "[verify] $*"; }

need() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }

run_timed() {
  # Avoid blocking on full stdout before timeout: use perl alarm + line drain.
  local label="$1"; shift
  local out="$ARTIFACT_DIR/${label}.out"
  local err="$ARTIFACT_DIR/${label}.err"
  mkdir -p "$ARTIFACT_DIR"
  : >"$out"; : >"$err"
  local start end elapsed
  start=$(date +%s)
  set +e
  (
    cd "$APP"
    perl -e '
      use strict; use warnings;
      my $timeout = shift @ARGV;
      my $out = shift @ARGV;
      my $err = shift @ARGV;
      my $pid;
      $SIG{ALRM} = sub {
        print STDERR "TIMEOUT\n";
        kill "TERM", $pid if defined $pid;
        exit 124;
      };
      alarm($timeout);
      $pid = open(my $fh, "-|", @ARGV) or die $!;
      open my $o, ">", $out or die $!;
      while (my $line = <$fh>) {
        print $o $line;
      }
      close $fh;
      my $code = $? >> 8;
      alarm(0);
      exit $code;
    ' "$TIMEOUT_SEC" "$out" "$err" "$@"
  )
  local code=$?
  set -e
  end=$(date +%s)
  elapsed=$((end - start))
  echo "$elapsed" >"$ARTIFACT_DIR/${label}.time_sec"
  echo "$code" >"$ARTIFACT_DIR/${label}.exit"
  if [[ $code -eq 124 ]]; then
    die "$label timed out after ${TIMEOUT_SEC}s (see $out)"
  fi
  return $code
}

need java
need perl
[[ -f "$JAR" ]] || die "build compiler first: mvn -DskipTests package (missing $JAR)"

log "build O0"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR" "$ARTIFACT_DIR"
# Project builds write jnative.project "output"; copy for O0 vs opt compare.
JNATIVE_REGISTRY="$REG" java -jar "$JAR" build --project "$APP" -O0 --registry "$ROOT/examples/order-processing/registry" \
  >"$ARTIFACT_DIR/build-O0.log" 2>&1 || die "O0 build failed; see $ARTIFACT_DIR/build-O0.log"
[[ -x "$BIN" ]] || die "missing binary after O0 build: $BIN"
cp "$BIN" "$BIN-O0"
log "build default opt"
JNATIVE_REGISTRY="$REG" java -jar "$JAR" build --project "$APP" --registry "$ROOT/examples/order-processing/registry" \
  >"$ARTIFACT_DIR/build-opt.log" 2>&1 || die "opt build failed; see $ARTIFACT_DIR/build-opt.log"
[[ -x "$BIN" ]] || die "missing binary after opt build: $BIN"

expect_ok=$'1\n3500\n200\n1\n1\n7\n34\n1\n10\n42\n'
expect_bad=$'0\n1\n42\n'

log "run ok (opt)"
run_timed ok "$BIN" || die "ok path exit $?"
printf '%s' "$expect_ok" >"$ARTIFACT_DIR/ok.expected"
diff -u "$ARTIFACT_DIR/ok.expected" "$ARTIFACT_DIR/ok.out" >/dev/null \
  || die "ok path output mismatch (artifacts in $ARTIFACT_DIR)"

log "run bad (opt)"
run_timed bad "$BIN" bad || die "bad path exit $?"
printf '%s' "$expect_bad" >"$ARTIFACT_DIR/bad.expected"
diff -u "$ARTIFACT_DIR/bad.expected" "$ARTIFACT_DIR/bad.out" >/dev/null \
  || die "bad path output mismatch"

log "run ok (O0) vs opt"
run_timed ok-O0 "$BIN-O0" || die "O0 ok exit $?"
diff -u "$ARTIFACT_DIR/ok.out" "$ARTIFACT_DIR/ok-O0.out" >/dev/null \
  || die "O0 vs opt ok mismatch"

log "run bad (O0) vs opt"
run_timed bad-O0 "$BIN-O0" bad || die "O0 bad exit $?"
diff -u "$ARTIFACT_DIR/bad.out" "$ARTIFACT_DIR/bad-O0.out" >/dev/null \
  || die "O0 vs opt bad mismatch"

log "Unicode summary file"
[[ -f "$APP/build/order-summary.txt" ]] || die "missing summary"
# UTF-8 apple emoji F0 9F 8D 8E
hex=$(od -An -tx1 "$APP/build/order-summary.txt" | tr -d ' \n')
echo "$hex" | grep -q 'f09f8d8e' || die "summary missing UTF-8 emoji bytes (got hex prefix: ${hex:0:64})"

log "stress N=200 (bounded)"
run_timed stress200 "$BIN" stress || die "stress exit $?"
# first line checksum, second line store size after removals
lines=$(wc -l <"$ARTIFACT_DIR/stress200.out" | tr -d ' ')
[[ "$lines" -ge 2 ]] || die "stress expected 2 lines"
size=$(sed -n '2p' "$ARTIFACT_DIR/stress200.out" | tr -d '[:space:]')
[[ "$size" =~ ^[0-9]+$ ]] || die "stress size not numeric: $size"
# With remove every 5th, size should stay well below N
[[ "$size" -lt 200 ]] || die "stress size suspiciously large: $size (possible leak/growth)"

# Second stress run — size/checksum should be deterministic for fixed seed
run_timed stress200b "$BIN" stress || die "stress2 exit $?"
diff -u "$ARTIFACT_DIR/stress200.out" "$ARTIFACT_DIR/stress200b.out" >/dev/null \
  || die "stress not deterministic for fixed seed"

# Record RSS if available (macOS)
if command -v /usr/bin/time >/dev/null 2>&1; then
  /usr/bin/time -l "$BIN" stress50 >"$ARTIFACT_DIR/stress50.out" 2>"$ARTIFACT_DIR/stress50.time" || true
fi

{
  echo "verify_ok=1"
  echo "timeout_sec=$TIMEOUT_SEC"
  echo "ok_time_sec=$(cat "$ARTIFACT_DIR/ok.time_sec")"
  echo "stress200_time_sec=$(cat "$ARTIFACT_DIR/stress200.time_sec")"
  echo "stress200_size=$size"
  echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >"$ARTIFACT_DIR/SUMMARY.txt"

log "PASS — artifacts in $ARTIFACT_DIR"
cat "$ARTIFACT_DIR/SUMMARY.txt"
