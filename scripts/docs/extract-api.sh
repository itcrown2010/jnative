#!/usr/bin/env bash
# Author: 张晓达
# Extract public type/method declarations from stdlib for API docs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STDLIB="$ROOT/src/main/resources/stdlib"
OUT="$ROOT/website/api/_generated"
mkdir -p "$OUT"

extract_module() {
  local name="$1"
  local dir="$STDLIB/$name"
  local dest="$OUT/${name}.txt"
  if [[ ! -d "$dir" ]]; then
    echo "(missing module dir: $name)" >"$dest"
    return
  fi
  {
    echo "# Generated from stdlib/$name — $(date -u +%Y-%m-%dT%H:%MZ)"
    echo "# Signatures only. Behavior notes are maintained in website/api/*.md"
    echo
    # Types and method-ish lines (heuristic; not a full parser)
    rg -n --no-heading \
      -e '^[[:space:]]*(public[[:space:]]+)?(abstract[[:space:]]+)?(final[[:space:]]+)?(class|interface|enum)[[:space:]]+[A-Za-z_]' \
      -e '^[[:space:]]*(public|protected)[[:space:]]+.*\([^;]*\)[[:space:]]*(\{|throws|;|$)' \
      "$dir" \
      || true
  } >"$dest"
  echo "wrote $dest ($(wc -l <"$dest") lines)"
}

MODULES=(
  async browser collections crypto db editor graphics http io json math net rpc studio time tls ui web webui
)

for m in "${MODULES[@]}"; do
  extract_module "$m"
done

# Index
{
  echo "# API extract index"
  echo "generated=$(date -u +%Y-%m-%dT%H:%MZ)"
  echo "stdlib=$STDLIB"
  for m in "${MODULES[@]}"; do
    f="$OUT/${m}.txt"
    lines=0
    [[ -f "$f" ]] && lines=$(wc -l <"$f")
    echo "$m lines=$lines"
  done
} >"$OUT/INDEX.txt"

echo "OK: $OUT/INDEX.txt"
