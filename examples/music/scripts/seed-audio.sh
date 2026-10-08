#!/usr/bin/env bash
# Generate CC0/test WAV fixtures (sox/ffmpeg optional; falls back to MusicSeed at server boot).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/storage/audio"
mkdir -p "$OUT" "$ROOT/fixtures"
if command -v sox >/dev/null 2>&1; then
  for i in 1 2 3 4 5; do
    sox -n -r 22050 -c 1 "$OUT/tone${i}.wav" synth 2 sine $((400 + i * 40)) vol 0.2
  done
  echo "seed-audio: sox wrote 5 tones"
elif command -v ffmpeg >/dev/null 2>&1; then
  for i in 1 2 3 4 5; do
    ffmpeg -y -f lavfi -i "sine=frequency=$((400 + i * 40)):duration=2" -ar 22050 -ac 1 "$OUT/tone${i}.wav" </dev/null
  done
  echo "seed-audio: ffmpeg wrote 5 tones"
else
  echo "seed-audio: no sox/ffmpeg — MusicSeed.writeToneWav will create fixtures on server boot"
fi
