# JNative One — Implementation Guide

**Purpose:** how an implementation partner would roll out a single-enterprise instance.  
**Status:** process guide for productization 1.5 — **not** a claim that the product is pilot-ready.

---

## 1. Requirements workshop

Capture: companies/books, warehouses, fiscal calendar, currency, roles, document approval needs, integration constraints. Confirm Linux server target readiness with engineering (today: **BLOCKED**).

---

## 2. Master data preparation

Templates (CSV):

- Customers / suppliers (code, name, kind)  
- Items (sku, name, unit price units)  
- Warehouses  
- Opening inventory (item, warehouse, qty, unit cost, date) — **controlled posting required**; do not UPDATE balances directly  

Finance opening AR/AP/GL: enable only after finance acceptance for that customer.

---

## 3. Environment build

1. Provision PostgreSQL (prefer non-SCRAM or driver-compatible auth until SCRAM lands)  
2. Run `jnative-one-install.sh`  
3. Run `setup-wizard.sh`  
4. Configure TLS terminator in front of `ERP_LISTEN`  
5. Verify `/health` `/ready`  

---

## 4. Data import

Pipeline: Upload → Parse → Validate → Preview → Confirm → Commit → Result.  
Use admin import APIs/jobs; retain batch error reports. Duplicate codes must fail or follow agreed policy.

---

## 5. Opening balances

- Inventory openings through inventory engine / opening document (when available)  
- Money as integer units (Decimal type **MISSING** — agree unit scale in writing)  
- Reconcile integrity check before go-live  

---

## 6. Training

Roles: sales, purchase, warehouse, finance, admin. Use Demo Trading Company on a **separate** database with `ERP_DEMO_MODE=1`.

---

## 7. Parallel run

Run legacy + JNative One for an agreed period. Compare stock, AR/AP, trial balance samples daily.

---

## 8. Cutover

1. Freeze legacy entries  
2. Final import / delta  
3. Backup  
4. Switch DNS/users  
5. Monitor `/ready`, error logs, first postings  

---

## 9. Acceptance

Complete `docs/pilot-acceptance-checklist.md`. **Do not sign pilot** while P0 > 0 or Linux runtime unverified.
