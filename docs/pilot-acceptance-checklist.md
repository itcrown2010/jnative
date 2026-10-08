# JNative One — Pilot Acceptance Checklist

**Rule:** all boxes must be **PASS** with evidence links/logs before `PILOT_READY`.  
**Date audited:** 2026-10-08  

| # | Gate | Required evidence | Status |
|---|------|-------------------|--------|
| 1 | Install on **Linux x86_64** clean host | install log + running binary file type ELF x86_64 | **FAIL** — Linux runtime BLOCKED |
| 2 | Initialization / setup lock | setup.completed + cannot re-create super-admin | **PARTIAL** — CLI wizard on macOS only |
| 3 | Data import CSV (customers/items/WH) | import job OK + error report | **PARTIAL** — partners CSV tested; full matrix incomplete |
| 4 | Business closed loop native+PG+WASM | create customer→PO→GR→SO→DL→AR/AP→TB in browser | **FAIL** — headless tests ≠ browser E2E |
| 5 | Permission tests | cross-company / RBAC denials | **PASS** (enterprise-test cases, PG once) |
| 6 | Backup restore drill | pg_dump/restore + business check | **PASS** (macOS+local PG, 1.4/1.5) |
| 7 | Upgrade old→new | pre-upgrade dump + integrity | **NOT VERIFIED** |
| 8 | Performance scale recorded (no fiction) | P50/P95/P99 log | **NOT VERIFIED** |
| 9 | Security suite | SQLi/XSS/CSRF/authz/path | **NOT VERIFIED** |
| 10 | Concurrency suite | no double stock/payment/docno | **NOT VERIFIED** |
| 11 | 30 min soak | memory/error/GC log | **NOT VERIFIED** |
| 12 | **P0 = 0** | `docs/erp-release-blockers.md` | **FAIL** — P0 ≥ 6 |

**Pilot decision:** **REJECT** until gates 1, 4, 12 (and critical P0s) clear.
