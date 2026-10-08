# JNative One — User Guide

**Product version:** 1.5.0-productization  
**Audience:** business users on a deployed demo/pilot instance  
**Note:** Feature freeze — describes what exists today; WASM UI is a navigation shell.

---

## Login

1. Open the application URL (default demo: `http://127.0.0.1:18700`).
2. Sign in with credentials provided by your administrator.
3. Demo seed (if enabled): `user1@jnative.dev` — password is set by admin; do not reuse in production.

Sessions use server-side cookies. Log out when finished on shared machines.

---

## Workbench / Dashboard

After login, use **Dashboard** APIs / menu entries for company overview (partner counts, open documents as implemented).

---

## Customers & Suppliers

- Browse business partners (`CUSTOMER` / `SUPPLIER`).
- Create/maintain via authorized APIs or admin import (CSV) when you have `ADMIN.IMPORT.RUN`.

---

## Materials (Items)

- SKU and name maintained per company.
- Prices/costs use **integer monetary units** (not floating point). Ask finance for unit meaning (e.g. cents).

---

## Purchasing

1. Create purchase order  
2. Approve (requires permission)  
3. Post goods receipt (idempotency key required on API)  
4. Inventory increases through the inventory engine only  

---

## Sales

1. Create sales order  
2. Approve  
3. Post delivery  
4. Stock decreases; over-delivery is rejected  

---

## Inventory

- Balances by warehouse × item  
- Movements are transactional documents — do not edit balances by hand  

---

## Finance

Where enabled for your company:

- Chart of accounts, journals, trial balance, AR/AP lists  
- Amounts are right-aligned integer units in the UI shell  

Manufacturing menus (BOM, production, MRP) appear when that module is licensed/deployed in the same binary.

---

## Administration (authorized roles)

Users, Roles, Permissions, Custom Fields, Forms, Workflows, Reports, Import/Export, Settings, Audit Logs.

**Important:** Hiding a field in the UI does not grant write access — the server re-validates.

---

## Reports

Run only **predefined** datasets (inventory, sales orders, purchase orders, customer balances). Arbitrary SQL reports are not available.

---

## FAQ

| Question | Answer |
|----------|--------|
| Why can't I see another company's data? | Multi-company isolation is enforced server-side. |
| Why was my approval rejected? | Missing role, duplicate act, or amount changed during approval. |
| Can I upload Excel? | CSV only in this version. |
| Password forgotten? | Contact administrator — self-service reset is not in v1 shell. |

---

## Known limitations

See `docs/erp-product-status.md` and `docs/erp-release-blockers.md`. This guide does not imply pilot readiness.
