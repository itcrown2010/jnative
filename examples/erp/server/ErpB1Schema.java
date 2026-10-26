/**
 * Migration 012 — SAP Business One style master data and marketing-document layer.
 * Single DDL source for PostgreSQL and SQLite: BIGINT has INTEGER affinity in SQLite, only the
 * primary key clause differs ({ID}). ALTERs are idempotent via ErpDb.tryAlter.
 * @author 张晓达
 */
class ErpB1Schema {
    static String pk() {
        if (Erp.dialect.equals("sqlite")) {
            return "INTEGER PRIMARY KEY AUTOINCREMENT";
        }
        return "BIGSERIAL PRIMARY KEY";
    }

    static void ct(String name, String cols) {
        ErpDb.execRetry("CREATE TABLE IF NOT EXISTS " + name + " (id " + pk()
                + ", company_id BIGINT NOT NULL, " + cols + ")", new ArrayList<Object>());
    }

    static void idx(String sql) {
        try {
            ErpDb.execRetry(sql, new ArrayList<Object>());
        } catch (Exception ex) {
            System.out.println("B1_IDX_SKIP");
        }
    }

    static void ensure() {
        ct("bp_groups", "code TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'CUSTOMER', "
                + "active BIGINT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("payment_terms", "code TEXT NOT NULL, name TEXT NOT NULL, net_days BIGINT NOT NULL DEFAULT 30, "
                + "discount_days BIGINT NOT NULL DEFAULT 0, discount_bps BIGINT NOT NULL DEFAULT 0, "
                + "active BIGINT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("tax_codes", "code TEXT NOT NULL, name TEXT NOT NULL, rate_bps BIGINT NOT NULL DEFAULT 0, "
                + "direction TEXT NOT NULL DEFAULT 'BOTH', active BIGINT NOT NULL DEFAULT 1, "
                + "created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("currencies", "code TEXT NOT NULL, name TEXT NOT NULL, symbol TEXT NOT NULL DEFAULT '', "
                + "decimals BIGINT NOT NULL DEFAULT 2, is_local BIGINT NOT NULL DEFAULT 0, "
                + "active BIGINT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("exchange_rates", "currency_code TEXT NOT NULL, rate_date BIGINT NOT NULL, rate_micro BIGINT NOT NULL, "
                + "created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, currency_code, rate_date)");
        ct("price_lists", "code TEXT NOT NULL, name TEXT NOT NULL, currency_code TEXT NOT NULL DEFAULT 'CNY', "
                + "base_list_id BIGINT NOT NULL DEFAULT 0, factor_bps BIGINT NOT NULL DEFAULT 10000, "
                + "is_default BIGINT NOT NULL DEFAULT 0, active BIGINT NOT NULL DEFAULT 1, "
                + "created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("price_list_items", "price_list_id BIGINT NOT NULL, item_id BIGINT NOT NULL, "
                + "price_cents BIGINT NOT NULL DEFAULT 0, UNIQUE (price_list_id, item_id)");
        ct("item_groups", "code TEXT NOT NULL, name TEXT NOT NULL, valuation_method TEXT NOT NULL DEFAULT 'MOVING_AVG', "
                + "active BIGINT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("uom_groups", "code TEXT NOT NULL, name TEXT NOT NULL, base_uom TEXT NOT NULL DEFAULT 'EA', "
                + "active BIGINT NOT NULL DEFAULT 1, created_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, code)");
        ct("uom_group_items", "uom_group_id BIGINT NOT NULL, uom_code TEXT NOT NULL, "
                + "base_qty BIGINT NOT NULL DEFAULT 1, UNIQUE (uom_group_id, uom_code)");
        ct("bp_addresses", "partner_id BIGINT NOT NULL, addr_type TEXT NOT NULL DEFAULT 'SHIP', "
                + "addr_name TEXT NOT NULL DEFAULT '', street TEXT NOT NULL DEFAULT '', city TEXT NOT NULL DEFAULT '', "
                + "state TEXT NOT NULL DEFAULT '', zip TEXT NOT NULL DEFAULT '', country TEXT NOT NULL DEFAULT 'CN', "
                + "is_default BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL DEFAULT 0");
        ct("bp_contact_persons", "partner_id BIGINT NOT NULL, name TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', "
                + "phone TEXT NOT NULL DEFAULT '', mobile TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', "
                + "is_default BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL DEFAULT 0");
        ct("mk_docs", "doc_type TEXT NOT NULL, doc_no TEXT NOT NULL, partner_id BIGINT NOT NULL DEFAULT 0, "
                + "partner_name TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'DRAFT', "
                + "doc_date BIGINT NOT NULL DEFAULT 0, due_date BIGINT NOT NULL DEFAULT 0, "
                + "valid_until BIGINT NOT NULL DEFAULT 0, "
                + "currency TEXT NOT NULL DEFAULT 'CNY', rate_micro BIGINT NOT NULL DEFAULT 1000000, "
                + "payment_terms_id BIGINT NOT NULL DEFAULT 0, price_list_id BIGINT NOT NULL DEFAULT 0, "
                + "warehouse_id BIGINT NOT NULL DEFAULT 0, ship_to TEXT NOT NULL DEFAULT '', "
                + "bill_to TEXT NOT NULL DEFAULT '', ref_no TEXT NOT NULL DEFAULT '', "
                + "discount_bps BIGINT NOT NULL DEFAULT 0, "
                + "subtotal_cents BIGINT NOT NULL DEFAULT 0, tax_cents BIGINT NOT NULL DEFAULT 0, "
                + "total_cents BIGINT NOT NULL DEFAULT 0, paid_cents BIGINT NOT NULL DEFAULT 0, "
                + "total_local_cents BIGINT NOT NULL DEFAULT 0, "
                + "base_type TEXT NOT NULL DEFAULT '', base_id BIGINT NOT NULL DEFAULT 0, "
                + "target_type TEXT NOT NULL DEFAULT '', target_id BIGINT NOT NULL DEFAULT 0, "
                + "journal_id BIGINT NOT NULL DEFAULT 0, subledger_id BIGINT NOT NULL DEFAULT 0, "
                + "notes TEXT NOT NULL DEFAULT '', idempotency_key TEXT NOT NULL DEFAULT '', "
                + "created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL DEFAULT 0, "
                + "updated_at BIGINT NOT NULL DEFAULT 0, posted_at BIGINT NOT NULL DEFAULT 0, "
                + "UNIQUE (company_id, doc_type, doc_no)");
        ct("mk_doc_lines", "doc_id BIGINT NOT NULL, line_no BIGINT NOT NULL, item_id BIGINT NOT NULL DEFAULT 0, "
                + "description TEXT NOT NULL DEFAULT '', warehouse_id BIGINT NOT NULL DEFAULT 0, "
                + "quantity BIGINT NOT NULL DEFAULT 0, uom_code TEXT NOT NULL DEFAULT '', "
                + "unit_price_cents BIGINT NOT NULL DEFAULT 0, discount_bps BIGINT NOT NULL DEFAULT 0, "
                + "tax_code_id BIGINT NOT NULL DEFAULT 0, tax_bps BIGINT NOT NULL DEFAULT 0, "
                + "net_cents BIGINT NOT NULL DEFAULT 0, tax_cents BIGINT NOT NULL DEFAULT 0, "
                + "total_cents BIGINT NOT NULL DEFAULT 0, is_inventory BIGINT NOT NULL DEFAULT 0, "
                + "base_type TEXT NOT NULL DEFAULT '', base_id BIGINT NOT NULL DEFAULT 0, "
                + "base_line_id BIGINT NOT NULL DEFAULT 0, clearing_cents BIGINT NOT NULL DEFAULT 0, "
                + "UNIQUE (doc_id, line_no)");
        idx("CREATE INDEX IF NOT EXISTS ix_mk_docs_type ON mk_docs (company_id, doc_type, status)");
        idx("CREATE INDEX IF NOT EXISTS ix_mk_docs_partner ON mk_docs (company_id, partner_id)");
        idx("CREATE INDEX IF NOT EXISTS ix_mk_lines_base ON mk_doc_lines (company_id, base_type, base_line_id)");
        idx("CREATE UNIQUE INDEX IF NOT EXISTS ux_mk_docs_idem ON mk_docs (company_id, idempotency_key) "
                + "WHERE idempotency_key <> ''");

        /* Business partner master (SAP B1 OCRD) */
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN group_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN payment_terms_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN price_list_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY'");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN tax_id TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN phone TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN email TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN website TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN credit_limit_cents BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN commitment_limit_cents BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN discount_bps BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN sales_employee_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN default_tax_code_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN bank_name TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN bank_account TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN industry TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE business_partners ADD COLUMN remarks TEXT NOT NULL DEFAULT ''");

        /* Item master (SAP B1 OITM) */
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN item_group_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN item_type TEXT NOT NULL DEFAULT 'ITEM'");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN is_inventory BIGINT NOT NULL DEFAULT 1");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN is_sales BIGINT NOT NULL DEFAULT 1");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN is_purchase BIGINT NOT NULL DEFAULT 1");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN barcode TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN manage_method TEXT NOT NULL DEFAULT 'N'");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN uom_group_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN sales_uom TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN purchase_uom TEXT NOT NULL DEFAULT ''");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN sales_tax_code_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN purchase_tax_code_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN valuation_method TEXT NOT NULL DEFAULT 'MOVING_AVG'");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN default_warehouse_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN preferred_vendor_id BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN max_qty BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN reorder_qty BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN lead_time_days BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN weight_g BIGINT NOT NULL DEFAULT 0");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
        ErpDb.tryAlter("ALTER TABLE items ADD COLUMN remarks TEXT NOT NULL DEFAULT ''");
    }

    /** Upgrade old data: defaults per company, backfill BP/items links. Idempotent. */
    static void upgradeAll() {
        DbQueryResult r = ErpDb.query("SELECT id FROM companies ORDER BY id", new ArrayList<Object>());
        int i = 0;
        while (i < r.rows.size()) {
            long cid = r.rows.get(i).getLong(0);
            try {
                ErpB1Master.ensureDefaults(cid);
            } catch (Exception ex) {
                System.out.println("B1_DEFAULTS_FAIL company=" + CryptoParse.longToString(cid));
                ErpPrint.ex(ex);
                ErpDb.reconnect();
            }
            i = i + 1;
        }
    }
}

class ErpPrint {
    static void ex(Exception ex) {
        if (ex == null) {
            System.out.println("EX_NULL");
            return;
        }
        String m = ex.getMessage();
        if (m == null || m.length() == 0) {
            System.out.println("EX_EMPTY_MESSAGE");
        } else {
            System.out.println(m);
        }
    }
}
