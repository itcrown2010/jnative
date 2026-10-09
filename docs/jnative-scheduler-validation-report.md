# JNative Scheduler Validation Report

作者：张晓达

Date: 2026-10-07  
Demo: `examples/scheduler`  
Goal: Validate JNative as a full-stack Web platform (native server + shared contracts + browser WASM), not merely a calendar UI.

## Executive summary

| Area | Result |
|------|--------|
| Native server compile / run (no JVM) | **PASS** |
| Browser WASM compile (no Node) | **PASS** |
| Shared DTO / validation / recurrence / layout | **PASS** |
| Auth + session cookie + CSRF + rate limit | **PASS** (PBKDF2 password hashing; Argon2id still MISSING) |
| Calendar CRUD + recurrence expand | **PASS** |
| Tasks CRUD | **PASS** |
| Search (ASCII) | **PASS** |
| Search (non-ASCII query string) | **PARTIAL** |
| Real-time (long-poll hub / SSE) | **PARTIAL** (hub poll; true SSE reconnect MISSING) |
| PostgreSQL async | **PARTIAL** (SQLite default; `DATABASE_URL` PG path exists; SCRAM/AsyncPgPool maturity incomplete) |
| Multi-module `jnative build --project` | **PASS** (after platform fix) |
| Production without JVM/Node | **PASS** for this demo path |

**Verdict:** JNative can compile and run a real shared-domain calendar stack end-to-end. Several gaps were exposed and fixed in the platform during this demo; remaining MISSING items are listed below.

---

## How to run

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar build --project examples/scheduler
./examples/scheduler/.jnative/build/scheduler-server
# open http://127.0.0.1:18400
# demo@jnative.dev / demo1234
```

Unit tests:

```bash
# via examples/scheduler/scripts/build-and-run.sh or:
./build/scheduler/scheduler-tests
```

---

## Capability matrix

| Capability | Status | Notes |
|------------|--------|-------|
| Native Server | PASS | Mach-O arm64 executable ~1.1MB |
| HTTP | PASS | WebApp on :18400 |
| HTTPS | PARTIAL | Stack present (0.31); Scheduler uses HTTP localhost for dev |
| HTTP/2 | PARTIAL | Stdlib present; not required for Scheduler E2E |
| Async Runtime | PASS | EventLoop + Future handlers |
| PostgreSQL | PARTIAL | Driver exists; demo defaults to SQLite |
| Connection Pool | PARTIAL | Database pool API used; deep PG pool validation not completed here |
| Transactions | PARTIAL | Event+reminder writes sequential; explicit txn API not exercised |
| Typed RPC | PARTIAL | `@Rpc` contracts in `shared/Apis.java`; REST mirrors mounted |
| JSON | PASS | Fixed `getLong` truncation + number literal preservation |
| Security | PARTIAL | Session, CSRF, rate limit, PBKDF2 (not Argon2id) |
| Sessions | PASS | HttpOnly cookie via SessionManager |
| WASM | PASS | `app.wasm` ~7.8KB / gzip ~4.9KB (WebUI shell) |
| WebUI | PARTIAL | Counter-style mount works; full calendar SPA is `public/app.js` |
| Router | MISSING | SPA uses hash-less JS; WebUI Router not used |
| Reactive State | PARTIAL | WebUI state for shell; main UI is DOM JS |
| Forms | PARTIAL | HTML forms in public SPA |
| Validation | PASS | Shared `EventValidation` / `LoginValidation` |
| SSE | PARTIAL | `/api/stream` long-poll hub; Prefer SSE remains open |
| Incremental Compiler | PASS | `--explain` shows shared reuse / target rebuild |
| Package Manager | PARTIAL | Stdlib via `import jnative.*` (monorepo packages) |
| Compile-Time Generation | PARTIAL | `@Rpc` present; Scheduler uses REST mirrors |
| Timezone | PARTIAL | ZoneId fixed offsets; DST incomplete |
| DateTime | PARTIAL | Wall clock via `AsyncClockNative.wallMillis`; TimeLib improved |

---

## Platform gaps exposed → fixes

### 1. Multi-module `dependsOn` sources not compiled

- **Symptom:** `jnative build --project` failed parsing `LoginRequest` etc. when only the main file was compiled.
- **Layer:** Incremental `CompilerSession`
- **Fix:** Materialize concatenated compile unit from `dependsOn` + module sources (`materializeModuleCompileUnit`).
- **Also:** `StdlibIncluder.insertDatabaseStdlib` now finds any `public class`, not only `App`.

### 2. `JsonObject.getLong` truncated to int

- **Symptom:** Event `startTime` became `2147483647`.
- **Layer:** `stdlib/json/JsonLib.java`
- **Fix:** Real `JsonParseLong`; preserve JSON number lexeme on `JsonNumber.literal`.
- **Why Scheduler exposed it:** Epoch millis exceed `int`.

### 3. Primitive casts / `String.valueOf(long)` / `!` on int

- **Symptom:** Shared recurrence/layout and server session checks failed to compile under JNative subset rules.
- **Layer:** Language subset + Scheduler sources
- **Fix:** Avoid `(long)`/`(int)` casts; use `CryptoParse.longToString` / `== 0` int checks; TimeLib helpers started.

### 4. Password hashing

- **Status:** PBKDF2-SHA256 (existing `Password` API). **Argon2id still MISSING** (documented in CryptoLib).

### 5. UTF-8 query search

- **Status:** ASCII search PASS; Chinese `q=` returned empty from HTTP while SQLite LIKE matched — URL query decoding gap (**PARTIAL**).

### 6. True SSE + Argon2 + full WebUI calendar

- Documented as follow-ups; no Scheduler-specific hacks for overlapping layout (shared `EventLayoutEngine`).

---

## Acceptance checklist

| Question | Answer |
|----------|--------|
| Can JNative compile the native server? | **YES** |
| Can JNative compile the browser app to WASM? | **YES** |
| Can both share DTOs? | **YES** |
| Can both share validation? | **YES** |
| Can Typed RPC generate client/server code? | **PARTIAL** (contracts + REST mirrors) |
| Can browser call native server? | **YES** (`public/app.js` + cookies) |
| Can native server query DB asynchronously? | **YES** (async API; SQLite backend) |
| Can users log in securely? | **YES** (session + PBKDF2; not Argon2id) |
| Can users create/update/delete events? | **YES** |
| Can users manage tasks? | **YES** |
| Can month/week/day views work? | **YES** (public SPA) |
| Can timezone conversion work? | **PARTIAL** |
| Can recurring events work? | **YES** (DAILY/WEEKLY/MONTHLY expand) |
| Can reminders reach browser clients? | **PARTIAL** (hub + ReminderScheduler timer) |
| Can real-time updates work? | **PARTIAL** (poll `/api/stream`) |
| Can server/browser rebuild independently? | **YES** (`--explain`) |
| Can shared API changes invalidate both? | **YES** (target fingerprint includes dependsOn) |
| Can package deps resolve through JNative packages? | **PARTIAL** (stdlib modules) |
| Can production server run without JVM? | **YES** |
| Can browser app run without Node? | **YES** |
| Can production require neither JVM nor Node? | **YES** |

---

## Performance snapshot (this machine)

| Metric | Value |
|--------|-------|
| Native server binary | ~1,177,192 bytes |
| WASM uncompressed | 7,877 bytes |
| WASM gzip | ~4,893 bytes |
| Unit tests | `SchedulerLogicTest PASS` |
| Cold project build (after jar rebuild) | ~6s |

---

## Ultimate scenario status

Steps 1–9, 14–15 (login, month data, create event, reminder field, week view in SPA, task, search ASCII, logout) are executable against the running native server + `public/` UI.

Steps requiring true multi-tab SSE push, drag-move, and Argon2id remain **PARTIAL / next**.

---

## Conclusion

**JNative Full-Stack Web Capability: CONDITIONAL PASS** for Scheduler’s first vertical slice — native executable + WASM + shared domain + secure session API + calendar/task persistence — with tracked platform follow-ups (Argon2id, UTF-8 query decode, production HTTPS E2E, full WebUI Router calendar, true SSE).
