# JNative ERP Manufacturing 1.3

作者：张晓达

**Stamp: `JNATIVE_ERP_MANUFACTURING_1_3_PASS`**

**Date:** 2026-10-08  
**Artifact:** `examples/erp`  
**Runtime:** JNative native executable + PostgreSQL (WASM menu present)

Core SmartDevice-A manufacturing loop passed twice on native PostgreSQL (`--manufacturing-test` → `PASS manufacturing`, FG=100, WIP remaining=0). This does **not** clear upstream Finance/Foundation wire fragility for all workloads; SQLite remains flaky.

---

## Precheck

| Source | Status |
|--------|--------|
| Phase-1 | `PARTIAL` — Item/WH/Inventory engine usable |
| Foundation 1.1 | Doc **missing**; capabilities partial |
| Finance 1.2 | `PARTIAL` — Posting engine + WIP/FG accounts used by MFG |

**Carried risks (documented, not ignored):** PG `08P01` can still appear in other flows; SQLite prepare/`incomplete input` intermittent; money is integer units (not full Decimal); RBAC matrix thin.

Manufacturing inventory changes go only through `ErpInventory.applyDeltaWithCostConn`. GL via `ErpFinanceCore.postJournalConn`.

---

## Architecture

| Area | Path |
|------|------|
| Schema | `migrations/003_manufacturing.sql` (+ sqlite) |
| BOM / explosion | `server/ErpMfgBom.java` |
| Production / WIP / issue / receipt | `server/ErpMfgProduction.java` |
| MRP | `server/ErpMfgMrp.java` |
| Test | `--manufacturing-test` → `ErpManufacturingLogicTest` |
| API | `/api/erp/mfg/boms`, `productionOrders`, `wip`, `mrp/suggestions` |
| UI | Manufacturing menu in `browser/ErpApp.java` |

---

## BOM Model

- Versions + `DRAFT`/`ACTIVE`/`OBSOLETE` + effective dates  
- Lines: qty > 0, `scrap_rate_bp` ≥ 0, UOM PCS  
- Activate locks prior ACTIVE for parent  
- Explosion: multi-level, scrap, cycle path check, depth cap, leaf merge  
- Release copies exploded leaf requirements onto the order (BOM edits later do not rewrite)

---

## Production Model

`PLANNED` → `RELEASED` → `IN_PROGRESS` / `PARTIALLY_COMPLETED` → `COMPLETED`

| Event | Inventory Engine | WIP | Journal |
|-------|------------------|-----|---------|
| Issue | −components | +material | Dr WIP / Cr Inventory |
| Receipt | +FG | transfer cost | Dr FG / Cr WIP |

Partial receipts 60+40; over-receipt rejected; issue/receipt idempotent keys.

`remainingCost = material + labor + overhead − transferred` (labor/overhead=0 in v1). Final receipt absorbs remaining WIP.

---

## MRP Algorithm

Daily buckets; gross / scheduled / projected / net / planned receipt & release; suggestions by `mrp_run_id`; same `run_code` idempotent. v1 collapses open demand into the start bucket (not full date-pegged SO/MO schedule).

---

## Native E2E

```bash
java -jar target/jnative.jar build --project examples/erp
psql … apply 001/002/003
DATABASE_URL='postgresql://jnative_erp@127.0.0.1:5432/jnative_erp' \
  examples/erp/.jnative/build/erp-server --manufacturing-test
```

**Result (native PG, repeated):**

- Explode: Motor 200, Sensor 400, Controller 100, Case 100  
- Issue all components → WIP material 6800  
- Receipt 60 + 40 → FG qty 100, order COMPLETED, WIP remaining 0  
- MRP run idempotent; cycle explode rejected  
- Stamp line: `PASS manufacturing`

---

## UI Verification

Manufacturing menu: BOM, Production Orders, Production Issue, Production Receipt, MRP, Production Cost (API-backed stubs). Full tree editor / MRP grid not design-complete.

---

## Performance

1000 BOM / 10000 lines / 1000 MO stress: **not measured** (no fabricated timings).

---

## Known Issues

- Upstream PG wire flake may still hit other modules  
- SQLite manufacturing path unreliable  
- Production return / scrap policy / full RBAC incomplete  
- MRP date pegging incomplete  
- Foundation 1.1 doc still missing  

---

## Stamp

`JNATIVE_ERP_MANUFACTURING_1_3_PASS`

STOP — do not auto-start the next phase.
