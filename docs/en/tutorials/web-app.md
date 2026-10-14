# Tutorial: Web task-style application

**Status:** Experimental end-to-end sample

The richest maintained sample is **`examples/guide-tasks`**: WASM/Web UI + optional native sync server + mobile WebView hosts.

## What it demonstrates

- Multi-page web shell
- Forms + validation
- IndexedDB offline queue
- HTTP sync with Token auth (local server)
- Desktop/mobile packaging scripts

## Do not invent

guide-tasks is **not** a generic Spring clone. Persistence may be IDB + SQLite sync server rather than a full ORM.

## Steps

1. Read `examples/guide-tasks/README.md`
2. `node examples/guide-tasks/server/sync-server.mjs` (local)
3. `node examples/guide-tasks/scripts/dev_web.mjs`
4. Login with seeded local users (see HTTP auth docs)—**local only**

For a minimal native-only HTTP+JSON path, start from `examples/web/hello/` instead.
