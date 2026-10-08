# JNative One — Administrator Guide

**Version:** 1.5.0-productization  
**Deploy model:** single enterprise instance, multi-company books, Native Server + PostgreSQL + WASM assets.

---

## Installation

```bash
# From repository (developer host) — Linux package target not VERIFIED
export DATABASE_URL='postgresql://jnative_erp@127.0.0.1:5432/jnative_erp'
chmod +x examples/erp/scripts/jnative-one-install.sh
examples/erp/scripts/jnative-one-install.sh
# PREFIX default: build/jnative-one
```

Checks performed: OS/arch warning, `psql`/`pg_dump`, disk space, port, copy binaries/migrations, apply SQL migrations, write `BUILD_METADATA` + checksums.

**Linux x86_64:** intended production target — currently **BLOCKED** (epoll stub / NOT VERIFIED). macOS arm64 is demo/dev only.

Config templates: `examples/erp/deploy/jnative-one.conf.example`, `env.example`.

---

## Initialization

```bash
export PREFIX=build/jnative-one
chmod +x examples/erp/scripts/setup-wizard.sh
examples/erp/scripts/setup-wizard.sh
```

- Creates `var/setup.completed` lock — **blocks re-init**.
- Concurrent setup prevented via lock directory.
- First server start runs `ErpSeed.ensure()` for demo company `JNAT` when empty.

Admin passwords use PBKDF2 via `Password.hashBlocking` (see release blockers for iteration/Argon2 status).

---

## Users & permissions

Platform RBAC: `MODULE.RESOURCE.ACTION` + data scope OWN/DEPARTMENT/COMPANY.  
Assign via `platform_user_roles` / seed. Admin APIs under `/api/erp/admin/*` re-check permissions.

---

## System configuration

Prefer environment variables / conf file (never commit secrets):

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | PostgreSQL |
| `ERP_LISTEN` | `host:port` |
| `ERP_STORAGE_PATH` | uploads |
| `ERP_BACKUP_PATH` | backups |
| `ERP_DEMO_MODE` | `1` only for demo resets |
| `ERP_LOG_LEVEL` | logging |

Sensitive settings in DB: display masked (`***`).

---

## Backup

```bash
export DATABASE_URL=...
examples/erp/scripts/backup-restore.sh
```

Uses `pg_dump --format=custom` + `pg_restore`. Also back up `ERP_STORAGE_PATH` and config separately.

---

## Restore

Follow script output / admin runbook:

1. Stop application  
2. Restore DB with `pg_restore`  
3. Restore file storage  
4. Start app  
5. Hit `/ready` and `--integrity-check`  

Never invent a proprietary DB backup format.

---

## Upgrade

```bash
examples/erp/scripts/jnative-one-upgrade.sh
```

Flow: version print → `pg_dump` → migrations → replace binary/WASM → `--integrity-check`. On failure, restore the pre-upgrade dump.

---

## Logs

Structured JSON lines from boot/`ErpProductConfig.logInfo` include timestamp, level, requestId, companyId, module, operation, duration, errorCode.  
**Do not** log passwords, tokens, or `DATABASE_URL`.

---

## Health

- `GET /health` — process up + version (no secrets)  
- `GET /ready` — DB ping + migration marker  
- `GET /version` — version metadata  
- CLI: `jnative-one-server --version` · `--integrity-check`

---

## Troubleshooting

| Symptom | Action |
|---------|--------|
| `08P01 invalid message format` | Platform P0 — reconnect/retry; do not ignore for pilot |
| Port in use | Change `ERP_LISTEN_PORT` |
| Setup refused | Lock exists — intentional |
| `--reset-demo` refused | Set `ERP_DEMO_MODE=1` only on disposable instances |

---

## Demo mode

`ERP_DEMO_MODE=1` allows demo-oriented operations. Production must keep it off. Demo data (`JNAT`) must not be mixed into customer production databases — use a separate database.
