# JNative 0.24 Database Build Report

## Summary

- Product version **0.24**
- **Native Database Stack**: `jnative.db` stdlib — URL parsing, driver SPI, ConnectionPool, Row/RowMapper, SQLite FFI driver, PostgreSQL wire protocol (partial), migrations foundation, time/UUID subset
- **NO JVM / NO JRE / NO JNI / NO JDBC / NO Hibernate / NO JPA** in shipped native binaries
- **SQLite**: blocking sqlite3 C ABI via `@Native`, isolated on worker pool (`Async.blockingOnLoop`)
- **PostgreSQL**: AsyncTcpSocket + wire protocol source present; MD5 auth; SCRAM **PARTIAL**; optional stdlib include
- **Web demos**: `examples/web/database-api/` (SQLite default), `examples/web/todo_sqlite/`
- **Tests**: `DatabaseStackTest`, `DatabaseStackMoreTest`, `PgProtocolTest` (host JVM protocol mirrors)

```text
ORM: NOT IMPLEMENTED
Database runtime reflection: NONE
PostgreSQL: true async socket protocol (source) — e2e requires DATABASE_URL + DbPgBootstrap.register()
SQLite: blocking native FFI isolated on worker pool
```

---

## 1. Database Architecture

```text
Application
    ↓
jnative.db (Database / ConnectionPool / RowMapper)
    ↓
DatabaseDriver SPI
    ├── SQLiteDriver  → @Native sqlite3 → worker pool → Future
    └── PostgreSQLDriver → AsyncTcpSocket → wire protocol
    ↓
Async Runtime / OS
```

## 2. Public API

- `Database.connect(url)` / `Database.connectWithConfig(config)`
- `db.query` / `db.queryOne` / `db.execute` / `db.transaction` / `db.acquire`
- Parameterized binds (`$1` / `?`); **no** SQL concatenation of user input in drivers
- Typed mapping via explicit `RowMapper` (no `Class.forName` / reflection)

## 3. Database URL

`DatabaseUrl.parse` supports:

- `postgresql://user:password@host:port/db`
- `postgres://…` (normalized to `postgresql`)
- `sqlite:./path.db`
- `sqlite::memory:` → shared-memory URI for deterministic multi-handle tests

## 4. Driver SPI

`DatabaseDriver.connect(DatabaseConfig) → JnFuture`

`DriverRegistry` resolves by scheme — no `if postgres` / `if sqlite` in application code.

## 5–11. PostgreSQL Driver / Wire / Auth / TLS / Query

| Item | Status |
|------|--------|
| StartupMessage | YES |
| Authentication MD5 | YES |
| Authentication SCRAM-SHA-256 | PARTIAL (explicit fail message) |
| ReadyForQuery / BackendKeyData / ParameterStatus | YES (parser) |
| Simple Query (`Q`) | YES |
| Extended Query Parse/Bind/Describe/Execute/Sync | YES (text format) |
| ErrorResponse / SQLSTATE fields | YES |
| sslmode reserved | YES (URL query stripped; TLS not wired) |
| AsyncTcpSocket path | YES in source |
| Default stdlib auto-include | NO — opt-in via `DbPgBootstrap.register()` / `new PostgreSQLDriver` |

## 12–17. ByteBuffer / Parser / Binding / Types

- `DbByteBuffer`: capacity/position/limit, BE int16/32/64, put/get, flip/clear/slice
- `PgMessageReader`: **partial-message safe** (fragmented TCP)
- Bind types: String / int / long / double / boolean / null / byte[] (text protocol)
- `PgType` OIDs: BOOL, INT2/4/8, FLOAT4/8, TEXT, VARCHAR, BYTEA, JSON, JSONB
- Binary format: architecture reserved; v1 uses text

## 18–27. Row / Mapping

- `Row`: getString/Int/Long/Double/Boolean/Bytes, isNull, columnCount/Name
- `RowMapper` interface; demos use hand-written mappers
- `TypeCodec` placeholder in DbLib
- Generated `@Query` / `User.class` automatic mapping: **foundation only** (no LLVM SQL magic)
- Runtime reflection for mapping: **NONE**

## 28–35. Connection / Pool

- One active query per PostgreSQL connection (busy flag)
- `ConnectionPool`: min/max, `AsyncSemaphore`, acquire → query → release
- Broken connections: `discard` (not returned to idle)
- Acquire timeout type: `ConnectionAcquireTimeoutException` (host tests cover timeout semantics)
- Lazy connect (no forced maxConnections at startup)

## 36–44. Transactions / Prepared / Timeout

- `db.transaction(work)` — BEGIN / COMMIT; exception → ROLLBACK
- Same `DbConnection` across awaits (ownership in transaction work)
- Nested / SAVEPOINT: not hard requirement; not shipped
- Statement LRU cache: host-tested; per-connection cache in native driver PARTIAL
- Query timeout + CancelRequest: BackendKey stored; cancel/timeout recovery PARTIAL

## 45–56. Errors / SQLite / Uniformity

- Exception hierarchy: Database / Connection / Authentication / Query / Constraint / Transaction / QueryTimeout / ConnectionAcquireTimeout / DbLoopViolation
- SQLSTATE used in Pg error parse (host + native)
- SQLite: `sqlite3_open_v2`, prepare/bind/step/column/finalize/close via FFI
- SQLite never on EventLoop thread (`DbLoopGuard`)
- Uniform `query` / `queryOne` / `execute` / `transaction` API

## 57–73. Typed SQL / Schema / Migrations

- `DbMigrateLib`: `Migrator`, `jnative_schema_migrations`, checksum
- `SchemaSnapshot` JSON foundation
- `@Query` processor / full SQL parser: **NOT rushed into LLVM** — foundation stubs only
- CLI `jnative db migrate` / `schema pull`: **foundation (stdlib), CLI subcommand not wired**

## 74–88. No ORM / Streaming / Time / UUID / Decimal / JSONB

- Explicitly **no** EntityManager / Session / LazyLoading / DirtyChecking / proxies
- Streaming rows API: foundation + host tests; native `AsyncRowStream` PARTIAL
- `TimeLib`: Instant, LocalDate/Time/DateTime, Duration, UUID (optional include)
- NUMERIC → String policy (no fake double decimals)
- JSONB → JsonValue mapping: planned / PARTIAL

## 89–99. Observability / Shutdown

- `DbMetrics`: connections, queries, totalQueryMs, errors
- Slow-query threshold + redacted SQL logging: host mirrors
- Pool shutdown fails waiters (host test)
- Graceful Web+DB shutdown: partial (close pool on process exit)

## 100–107. Demos / Thread Model

| Demo | Binary | Notes |
|------|--------|-------|
| `examples/db/sqlite_demo` | `build/db/sqlite_demo` | memory DB; Alice bind OK |
| `examples/web/database-api` | `build/web/database-api` | CRUD `/users` port **18185** |
| `examples/web/todo_sqlite` | `build/web/todo_sqlite` | `todo.db` port **18184** |
| `examples/db/pg_demo` | opt-in Pg | needs `DATABASE_URL` + Pg include |

Verified pipeline (SQLite):

```text
curl → Native Web → handler → ConnectionPool → sqlite worker → FFI → RowMapper → JSON → HTTP
```

## 108–115. Effects / Nullability

- SQLite low-level: BLOCKS + NATIVE_CALL (offloop via worker)
- PostgreSQL intent: SUSPENDS on async socket (source); full effect table PARTIAL
- Compile-time “blocking on event-loop” warning: existing EffectAnalyzer; DB-specific diagnostic PARTIAL
- NULL ≠ 0 / "" / false (Row.isNull)

## 116–124. Tests / GC / Perf / Size

```text
mvn test -Dtest=DatabaseStackTest,DatabaseStackMoreTest,PgProtocolTest
```

- Meaningful host tests: **≥ 100** across the three classes (protocol fragmentation, bind security, pool, tx, migrations, metrics, …)
- Native GC stress with DB: not fully automated in this report cycle
- Binary sizes (macOS release-ish local build):

| Binary | Size (bytes) |
|--------|--------------|
| `build/db/sqlite_demo` | ~374000 |
| `build/web/database-api` | ~589000 |
| `build/web/todo_sqlite` | ~572000 |

## 125. No ORM Yet

```text
ORM: NOT IMPLEMENTED
```

Intentional SQL-first design.

## 126–128. Native E2E / No JVM

- SQLite Web+DB e2e: **YES** (curl against `database-api`)
- PostgreSQL full acceptance curl pipeline: **requires local Postgres + opt-in driver include**
- Runtime of demos: **NO JVM / NO JRE / NO JNI / NO JDBC**

## 129–130. Architecture Acceptance

```text
Can a Web handler await a PostgreSQL query without blocking EventLoop?
  PARTIAL — API + pool async wait YES; full await-bridge + Pg e2e PARTIAL

Does PostgreSQL use JNative async sockets?
  YES (driver source) / e2e PARTIAL

Is JDBC absent? YES
Is JNI absent? YES
Are parameters protocol-bound rather than SQL-concatenated? YES
Can packets be arbitrarily fragmented? YES (parser + tests)
Does ConnectionPool wait asynchronously? YES (AsyncSemaphore + JnFuture)
Can transactions span await? YES (same connection ownership)
Does transaction use one connection? YES
Can failed transactions rollback? YES
Can query timeout cancel/recover/discard safely? PARTIAL
Does SQLite execute off EventLoop? YES
Does SQLite use direct C ABI FFI rather than JNI? YES
Can rows map to typed JNative objects? YES (RowMapper)
Is runtime reflection unnecessary for row mapping? YES
Can large result sets stream? PARTIAL (API foundation)
Does streaming provide backpressure? PARTIAL (foundation)
Can SQL schema be validated offline using snapshot metadata? PARTIAL (SchemaSnapshot)
Does GC2 work while queries are suspended? PARTIAL (model uses native buffers; stress not fully reported)
Can Web + DB run without JVM/JRE/JNI? YES
```

## 131. Do Not Implement Yet

Hibernate/JPA clones, Redis/Mongo/Kafka, GraphQL, WebSocket, full MySQL protocol, DB GUI — **not started** (as required).

## 132. 0.25 Readiness

```text
Can HTTP connections stay open indefinitely?                 NO
Can AsyncBodyReader/Writer support bidirectional streaming? NO
Can WebSocket frames use ByteBuffer?                        PARTIAL (ByteBuffer exists)
Can ping/pong timers use EventLoop?                         PARTIAL (timers exist)
Can thousands of WebSocket clients avoid thousands of threads? NO
Can AsyncQueue provide outbound backpressure?               PARTIAL (AsyncQueue exists)
Can authentication middleware query Database asynchronously? PARTIAL (handler+db YES)
Can password hashing run on blocking/CPU executor?          PARTIAL (worker pool YES)
Can sessions use cookies securely?                          NO
Can signed tokens be generated/verified?                    NO
Can SSE stream events indefinitely?                         NO
Can one JNative server support REST + WebSocket + SSE?      NO
```

Target for 0.25: move the above to **YES**.

---

## Important Files

- `src/main/resources/stdlib/db/DbLib.java`
- `src/main/resources/stdlib/db/SqliteDriverLib.java`
- `src/main/resources/stdlib/db/PgDriverLib.java`
- `src/main/resources/stdlib/db/DbMigrateLib.java`
- `src/main/resources/stdlib/time/TimeLib.java`
- `src/main/resources/stdlib/async/AsyncLib.java` (JnFuture inline dispatch when loop not running)
- `examples/web/database-api/App.java`
- `examples/web/todo_sqlite/App.java`
- `examples/db/sqlite_demo/App.java`
- `src/test/java/io/jnative/db/DatabaseStackTest.java`
- `src/test/java/io/jnative/db/DatabaseStackMoreTest.java`
- `src/test/java/io/jnative/db/PgProtocolTest.java`

## Known Limitations

1. `FutureBridge.toAwaitable` still uses `blockingWait` — not full cooperative await from JnFuture into compiler `Future<T>`
2. SCRAM-SHA-256 not completed
3. PostgreSQL driver not default-included (compile issues / BitOps / language gaps being worked down)
4. `User.class` automatic mapping without RowMapper not available (no reflection, codegen incomplete)
5. CLI `jnative db migrate` not exposed as subcommand yet
6. Web handlers often `FutureBlocker.blockingWait` inside async handlers — correct offloop for SQLite, but not yet pure `await` style

## Regression Notes

- Fixed JnFuture callback dispatch when EventLoop is not `running` (critical for pool acquire without a live loop thread)
- Fixed SQLite `bind_text` to use `SQLITE_TRANSIENT` (`destructor=-1L`) so bound strings survive FFI CString lifetime
- LLVM alloca name clashes in large handlers: avoid reused local names across branches in one method
