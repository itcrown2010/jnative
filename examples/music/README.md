# JNative Music (validation app)

作者：张晓达

Full-stack Music validation for JNative: native server, PostgreSQL/SQLite metadata, HTTP Range streaming, WASM PlayerStore + thin audio/DOM bridge.

## Quick start (SQLite local)

```bash
# from repo root
./jn build --project examples/music
# or: java -jar ... / mvn -q exec depending on your install

mkdir -p build/music
./.jnative/build/music-server
# open http://127.0.0.1:18500
# demo: music@jnative.dev / music1234
```

Default DB when `DATABASE_URL` unset: `sqlite:./build/music/music.db`.

## PostgreSQL (validation target)

SCRAM auth is **not** implemented yet (RH-1). Use md5/trust:

```bash
docker compose -f examples/music/docker-compose.yml up -d
export DATABASE_URL='postgresql://music:music@127.0.0.1:5433/music'
./.jnative/build/music-server
```

## Layout

| Path | Role |
|------|------|
| `shared/` | DTOs + `@Rpc` contracts |
| `server/` | `MusicServer`, media Range handlers, seed |
| `browser/` | WASM `MusicApp` PlayerStore shell |
| `public/` | `index.html` + `audio-bridge.js` only (WASM at `/wasm/*` from build) |
| Report | [`docs/jnative-music-validation.md`](../../docs/jnative-music-validation.md) |
| `migrations/` | SQL schema |
| `storage/` | runtime audio/covers (seeded) |

## Gates

- **A** — Play/seek via real Range 206 streaming
- **B** — Search / playlist / album / artist / my music / auth / charts
- **C** — Stress/soak/security + `docs/jnative-music-validation.md`

Stamp: `JNATIVE_MUSIC_VALIDATION_PASS` only if A+B+C core gates hold.

## Env

| Var | Meaning |
|-----|---------|
| `DATABASE_URL` | `sqlite:...` or `postgresql://...` |
| (optional) storage under `examples/music/storage` |

Admin multipart upload is **PARTIAL** if HTTP multipart is immature — use seed path.
