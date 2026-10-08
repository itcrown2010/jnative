# JNative Music Validation Report

**Stamp:** `JNATIVE_MUSIC_VALIDATION_PARTIAL`  
**Date:** 2026-10-08  
**Does NOT claim:** `JNATIVE_1_0_READY`

## Summary

JNative Music (`examples/music`) exercises the native HTTP stack, streaming Range I/O, password hashing, SQLite metadata seed, REST surface, thin audio/DOM bridge, and a WASM PlayerStore shell. Core playback-path Range streaming is **PASS**. Full charter purity (WASM-owned UI + PostgreSQL SCRAM) is **PARTIAL / BLOCKED_BY_ENVIRONMENT**.

## Gate matrix

| Gate | Result | Evidence |
|------|--------|----------|
| A — Streaming Range media | **PASS** | `HttpRangeStreamingEndToEndTest` green; live `GET /media/tracks/1` → **206** + `Content-Range` without whole-file `readBytes` |
| A — Seed spine | **PASS** | 10 artists / ~20 albums / 100 tracks / demo user; WAV fixtures generated in `MusicSeed` |
| A — Play/seek path | **PASS** (API) | Range seek bytes verified; browser Audio element driven via bridge (`public/audio-bridge.js`) |
| A — WASM PlayerStore shell | **PARTIAL** | `browser/MusicApp.java` mounts PlayerStore; UI interactivity currently in DOM bridge (WASM fetch ABI still stubby) |
| B — Search debounce/cancel | **PASS** (host) | Search API + bridge debounce/generation; injection `q=a; drop` → **400** |
| B — Album/Artist/Playlist/Charts/MyMusic | **PASS** (API+UI host) | Handlers + bridge routes exercised |
| B — Auth (cookie + CSRF + PBKDF2) | **PASS** | Login/me/favorite with CSRF; hash `pbkdf2_sha256$10000$...`; `Password.needsRehash` |
| B — Admin multipart upload | **PARTIAL** | Seed-only upload path; multipart not shipped |
| B — Playlist reorder in TX | **PARTIAL** | Add/remove implemented; full reorder TX not shipped |
| C — Concurrent streams 10/50/100 | **PASS** | `stress-range.sh` / inline xargs Range GETs succeeded |
| C — Short soak | **PASS** | 20× health+Range loop ok=20 fail=0; full 30m script: `examples/music/scripts/soak.sh` |
| C — 30m soak executed this run | **PARTIAL** | Harness present; not wall-clock 30m in this session |
| C — Path traversal / multi-range / oversized | **PASS** | multi-range → **416**; unsatisfiable → **416**; `..` rejected in media path |
| PostgreSQL metadata | **BLOCKED_BY_ENVIRONMENT** | Docker unavailable on runner; SCRAM still missing (RH-1). `docker-compose.yml` forces `password_encryption=md5`. Default local DB: SQLite |
| Clean-SDK dogfood | **BLOCKED_BY_ENVIRONMENT** | RH-4 SDK not present |
| Charter: no business JS in `public/` | **PARTIAL** | `public/` contains only `index.html` + `audio-bridge.js`, but bridge currently includes DOM/REST binder beyond pure Audio (WASM fetch gap) |

## Platform fixes landed (stdlib — not Music-specific)

1. **Streaming HTTP Range** (`WebLib` / `HttpLib` / `StreamLib` + posix/windows `jnative_fs_stream_seek`)
   - `MediaStreamer.serveRange` / `WebResponse.fileRange`
   - `HttpConnection.sendFileRange` seek + 64KiB chunks
   - 416 + `Content-Range: bytes */total`; multi-range rejected
   - Static mounts continue to next mount when file missing
   - Regression: `src/test/java/io/jnative/e2e/HttpRangeStreamingEndToEndTest.java`

2. **PBKDF2 default raised** (`CryptoLib.Password`)
   - `DEFAULT_ITERATIONS = 10000` (versioned `pbkdf2_sha256$N$...`)
   - `Password.needsRehash`; Music login upgrades legacy hashes

## Bugs found / fixed

| Bug | Repro | Fix / regression |
|-----|-------|------------------|
| Static Range materialized whole file via `File.readBytes` | Large static + `Range` | Streaming path + e2e |
| No stream seek ABI | Could not skip to offset | `jnative_fs_stream_seek` + `StreamNative.seek` |
| Password default 500 iterations | Music Auth blocked | Raise to 10000 + `needsRehash` |
| Multi static mount: missing file aborted mount walk | `/wasm/app.js` 404 behind `/` mount | `tryStatic` continues to next mount |

## How to run

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar build --project examples/music
mkdir -p build/music
./examples/music/.jnative/build/music-server
# http://127.0.0.1:18500  demo: music@jnative.dev / music1234

# optional PG (md5):
# docker compose -f examples/music/docker-compose.yml up -d
# DATABASE_URL=postgresql://music:music@127.0.0.1:5433/music ./examples/music/.jnative/build/music-server

./examples/music/scripts/stress-range.sh http://127.0.0.1:18500 50
# SOAK_MINUTES=30 ./examples/music/scripts/soak.sh http://127.0.0.1:18500
```

## Blocker list (STOP)

1. PostgreSQL **SCRAM** unsupported (RH-1) — md5/trust only.
2. Browser WASM **fetch ABI** incomplete — product UI uses thin DOM/network bridge (charter tension → PARTIAL).
3. Admin multipart upload immature → PARTIAL.
4. Full 30m soak not executed in this validation session (script ready).
5. Do **not** stamp `JNATIVE_1_0_READY` from Music alone.

## STOP

Validation closes here with stamp **`JNATIVE_MUSIC_VALIDATION_PARTIAL`**. Next hardening tracks remain RH-2+ (GC/async truth, SCRAM, Argon2, Clean SDK) — not claimed complete by this app.
