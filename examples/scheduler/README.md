# JNative Scheduler

作者：张晓达

Full-stack calendar demo validating JNative native server + shared domain + browser WASM/WebUI (no JVM/Node at runtime).

## Layout

```text
examples/scheduler/
├── shared/          # DTOs, validation, recurrence, layout, RPC contracts
├── server/          # Native HTTP/REST server + SQLite/PostgreSQL
├── browser/         # wasm32-browser WebUI shell
├── public/          # Calendar SPA (served by native server)
├── migrations/      # SQL schema
├── tests/           # Shared-logic unit tests
└── jnative.project
```

## Quick start

```bash
# from repo root
mvn -q -DskipTests package
java -jar target/jnative.jar build --project examples/scheduler
./examples/scheduler/.jnative/build/scheduler-server
```

Or: `./examples/scheduler/scripts/build-and-run.sh`

Demo login: `demo@jnative.dev` / `demo1234`  
UI: `http://127.0.0.1:18400` (native-served `public/`)

Default DB: `sqlite:./build/scheduler/scheduler.db`  
Override: `DATABASE_URL=postgresql://...`

Validation report: [docs/jnative-scheduler-validation-report.md](../../docs/jnative-scheduler-validation-report.md)

## Architecture

```text
Browser (public/app.js or WASM WebUI)
  → Typed REST mirrors of @Rpc contracts
  → Native SchedulerServer
  → Async EventLoop + Database pool
  → SQLite (dev) / PostgreSQL (DATABASE_URL)
```

## Build notes

- Multi-module `jnative.project` builds **server** + **browser** (dependsOn sources are materialised into one compile unit).
- Calendar UX: `public/app.js` (REST). WASM target mounts a WebUI shell (`browser/SchedulerApp.java`).
