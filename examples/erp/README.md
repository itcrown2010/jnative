# JNative ERP (Phase-1)

Purchase → goods receipt → inventory → sales → delivery closed loop on PostgreSQL (SQLite fallback). No GL/MRP/CRM.

## UI language (i18n)

The product shell in `public/` supports **简体中文 (`zh-CN`)** and **English (`en`)**.

- Default on first visit: `zh-CN`
- Preference key: `localStorage.jn_erp_locale`
- Switch: **中文 / English** on the login page and in the top bar (no full page reload)
- Runtime catalog: `public/js/i18n/catalogs.js` (ASCII `\uXXXX` escapes). Source dictionaries: `en.js` / `zh-CN.js`.
- **Note:** current `erp-server` static file serving corrupts raw UTF-8 bytes in `.js`; keep shipped catalogs ASCII-only.
- API: `JnI18n.t(key, params)`. Add keys to both locale sources, then regenerate `catalogs.js`.
- Business codes, API fields, and routes stay English; only display labels are translated

## Build

From repo root (requires `target/jnative.jar`):

```bash
mkdir -p build/erp
java -jar target/jnative.jar build --project examples/erp
```

Binaries:

| Target | Output |
|--------|--------|
| server | `examples/erp/.jnative/build/erp-server` |
| logic test | `examples/erp/.jnative/build/erp-server --logic-test` |
| browser WASM | `examples/erp/.jnative/build/browser/` |

## Run (PostgreSQL)

```bash
docker compose -f examples/erp/docker-compose.yml up -d
export DATABASE_URL='postgresql://jnative_erp:jnative_erp@127.0.0.1:5432/jnative_erp'
examples/erp/.jnative/build/erp-server
# http://127.0.0.1:18700 — user1@jnative.dev / erp1234
```

Default when `DATABASE_URL` unset: `postgresql://jnative_erp@127.0.0.1:5432/jnative_erp` (local trust).

## Run (SQLite)

```bash
export DATABASE_URL='sqlite:./build/erp/erp.db'
examples/erp/.jnative/build/erp-server
```

## Headless acceptance

```bash
export DATABASE_URL='sqlite:./build/erp/logic-test.db'
examples/erp/.jnative/build/erp-server --logic-test
# prints PASS or FAIL
```

Scenario: PO 100 → GR 60+40 → stock 100 → SO 30 → DL 10+20 → stock 70; idempotent GR replay; over-receive / over-delivery rejected.

### Pilot 12-step acceptance (formal book)

```bash
examples/erp/scripts/pilot-accept.sh
# or
DATABASE_URL='sqlite:./build/erp/pilot-accept.db' examples/erp/.jnative/build/erp-server --pilot-test
# prints PILOT_ACCEPT_PASS
```

Covers wizard → CSV → opening 20 → GR 10 → DL 3 (=27) → reverse DL (=30) → second reverse rejected → AR/AP opening → reconnect → migration 005 → backup scripts → demo reset isolation.

See `docs/erp-pilot-baseline-report.md` and `docs/erp-pilot-delivery.md`.

## PG e2e script

```bash
chmod +x examples/erp/scripts/pg-e2e.sh
examples/erp/scripts/pg-e2e.sh
```

## Layout

| Path | Role |
|------|------|
| `shared/` | DTOs, enums, API contracts |
| `server/` | HTTP server, posting, seed |
| `browser/` | WASM shell |
| `migrations/` | PostgreSQL + SQLite DDL |
| `tests/` | `ErpLogicTest` |

## API (session cookie after `POST /api/auth/login`)

- `GET /api/erp/dashboard`
- `GET /api/erp/partners?kind=CUSTOMER|SUPPLIER`
- `POST /api/erp/purchaseOrders` · `POST .../:id/approve` · `POST /api/erp/goodsReceipts` (Idempotency-Key)
- `POST /api/erp/salesOrders` · `POST .../:id/approve` · `POST /api/erp/deliveries` (Idempotency-Key)
- `GET /api/erp/inventory?warehouseId=`

Money: integer cents on order lines. Quantities: integer pieces.
