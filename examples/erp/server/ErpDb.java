import jnative.web.*;

class Erp {
    static EventLoop loop;
    static Database db;
    static String dialect;
    static InMemorySessionStore sessions;
    static RateLimiter loginLimiter;
    static long requestSeq;
    /** 1 when schema migrations completed successfully; business writes require this.
 * @author 张晓达
 */
    static int schemaReady = 0;
    static String schemaError = "";
    /** Accumulated fatal migrate diagnostics (non-empty ⇒ readiness must fail). */
    static String migrateFatal = "";
}

class ErpDb {
    static void setup(EventLoop loop) {
        Erp.loop = loop;
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "postgresql://jnative_erp@127.0.0.1:5432/jnative_erp";
            Erp.dialect = "postgresql";
        } else {
            Erp.dialect = DatabaseUrl.parse(url).scheme;
        }
        if (Erp.dialect.equals("sqlite")) {
            /* ok */
        } else {
            if (Erp.dialect.equals("postgresql") == false) {
                Erp.dialect = "postgresql";
            }
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;
        JnFuture dbF = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbF);
        if (dbF.getError() != null) {
            throw new ConnectionException("db connect failed");
        }
        Erp.db = (Database) dbF.getValue();
        applySqlitePragmas();
        try {
            migrate();
        } catch (Exception migEx) {
            Erp.schemaReady = 0;
            if (migEx.getMessage() != null) {
                Erp.schemaError = migEx.getMessage();
            } else {
                Erp.schemaError = "migrate failed";
            }
            System.out.println("SCHEMA_NOT_READY " + Erp.schemaError);
            throw migEx;
        }
    }

    static void migrate() {
        Erp.migrateFatal = "";
        Erp.schemaReady = 0;
        String path = "examples/erp/migrations/001_init.sql";
        String path2 = "examples/erp/migrations/002_finance.sql";
        String path3 = "examples/erp/migrations/003_manufacturing.sql";
        String path4 = "examples/erp/migrations/004_enterprise.sql";
        if (Erp.dialect.equals("sqlite")) {
            path = "examples/erp/migrations/001_init_sqlite.sql";
            path2 = "examples/erp/migrations/002_finance_sqlite.sql";
            path3 = "examples/erp/migrations/003_manufacturing_sqlite.sql";
            path4 = "examples/erp/migrations/004_enterprise_sqlite.sql";
        }
        /* Skip full 001-003 only when business docs already exist (avoid PG 08P01 storms).
           companies alone is not enough — ensureCoreTables may create companies without PO/SO. */
        int coreReady = 0;
        if (tableExists("companies") != 0 && tableExists("purchase_orders") != 0
                && tableExists("sales_orders") != 0 && tableExists("gl_accounts") != 0) {
            coreReady = 1;
        }
        if (coreReady == 0) {
            runScriptFileLabeled(path, "001");
            runScriptFileLabeled(path2, "002");
            runScriptFileLabeled(path3, "003");
        } else {
            System.out.println("MIGRATE_SKIP 001-003 (business schema present)");
        }
        if (tableExists("platform_permissions") == 0) {
            runScriptFileLabeled(path4, "004");
        } else {
            System.out.println("MIGRATE_SKIP 004_enterprise (platform_permissions present)");
        }
        /* Explicit core DDL — script splitter / sqlite FFI can skip or corrupt mid-file. */
        ensureCoreTables();
        ensureBusinessDocTables();
        if (Erp.dialect.equals("sqlite")) {
            ensureSqliteInventoryValueColumns();
            ensureSqliteUserDeptColumns();
        }
        ensureFinanceArApTables();
        ensureMfgTables();
        try {
            recordMigration("004_enterprise");
        } catch (Exception ignored) {
            reconnect();
        }
        ensurePilotSchema();
        try {
            recordMigration("005_pilot");
        } catch (Exception ignored) {
            reconnect();
        }
        ensureDemoOpsSchema();
        try {
            recordMigration("006_demo_ops");
        } catch (Exception ignored) {
            reconnect();
        }
        ensurePurchaseOpsSchema();
        try {
            recordMigration("007_purchase_ops");
        } catch (Exception ignored) {
            reconnect();
        }
        ensureSalesOpsSchema();
        try {
            recordMigration("008_sales_ops");
        } catch (Exception ignored) {
            reconnect();
        }
        ensureFinanceSettlementSchema();
        try {
            recordMigration("009_finance_settlement");
        } catch (Exception ignored) {
            reconnect();
        }
        ensureInventoryCostingSchema();
        try {
            recordMigration("010_inventory_costing");
        } catch (Exception ignored) {
            reconnect();
        }
        ensureGlLedgerSchema();
        try {
            recordMigration("011_gl_ledger");
        } catch (Exception ignored) {
            reconnect();
        }
        if (tableExists("companies") == 0) {
            throw new RuntimeException("migrate failed: companies table missing after ensure");
        }
        if (Erp.dialect.equals("sqlite")) {
            requireTable("purchase_orders");
            requireTable("sales_orders");
            requireTable("gl_accounts");
            requireTable("production_issues");
            requireTable("bank_cash_movements");
        }
        if (Erp.migrateFatal.length() > 0) {
            /* Necessary migration failures always block readiness — never clear fatals silently. */
            Erp.schemaReady = 0;
            Erp.schemaError = Erp.migrateFatal;
            throw new RuntimeException("migrate failed: " + Erp.migrateFatal);
        }
        Erp.schemaReady = 1;
        Erp.schemaError = "";
    }

    static void requireTable(String name) {
        if (tableExists(name) == 0) {
            Erp.schemaReady = 0;
            Erp.schemaError = "required table missing: " + name;
            throw new RuntimeException("migrate failed: required table missing: " + name);
        }
    }

    /** Management GL: company policy, JE lifecycle cols, gl_posting_events. */
    static void ensureGlLedgerSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path11 = "examples/erp/migrations/011_gl_ledger.sql";
        if (Erp.dialect.equals("sqlite")) {
            path11 = "examples/erp/migrations/011_gl_ledger_sqlite.sql";
        }
        runScriptFileLabeled(path11, "011");
        tryAlter("ALTER TABLE companies ADD COLUMN gl_policy TEXT NOT NULL DEFAULT 'PROVISIONAL_CLEARING'");
        tryAlter("ALTER TABLE companies ADD COLUMN functional_currency TEXT NOT NULL DEFAULT 'CNY'");
        tryAlter("ALTER TABLE gl_accounts ADD COLUMN normal_balance TEXT NOT NULL DEFAULT 'DEBIT'");
        tryAlter("ALTER TABLE gl_accounts ADD COLUMN is_control INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE gl_accounts ADD COLUMN require_partner INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE gl_accounts ADD COLUMN require_warehouse INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN approved_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN approved_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN rejected_reason TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN event_type TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE journal_entries ADD COLUMN fiscal_period_id INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE journal_entry_lines ADD COLUMN warehouse_id INTEGER NOT NULL DEFAULT 0");
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS gl_posting_events (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, event_type TEXT NOT NULL, source_type TEXT NOT NULL, source_id INTEGER NOT NULL, source_line_id INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, journal_entry_id INTEGER NOT NULL DEFAULT 0, amount INTEGER NOT NULL DEFAULT 0, partner_id INTEGER NOT NULL DEFAULT 0, warehouse_id INTEGER NOT NULL DEFAULT 0, biz_date INTEGER NOT NULL, payload_json TEXT NOT NULL DEFAULT '', error_code TEXT NOT NULL DEFAULT '', error_message TEXT NOT NULL DEFAULT '', retry_count INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, posted_at INTEGER NOT NULL DEFAULT 0, UNIQUE (company_id, event_type, source_type, source_id, source_line_id))",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS gl_posting_events (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, event_type TEXT NOT NULL, source_type TEXT NOT NULL, source_id BIGINT NOT NULL, source_line_id BIGINT NOT NULL DEFAULT 0, status TEXT NOT NULL, journal_entry_id BIGINT NOT NULL DEFAULT 0, amount BIGINT NOT NULL DEFAULT 0, partner_id BIGINT NOT NULL DEFAULT 0, warehouse_id BIGINT NOT NULL DEFAULT 0, biz_date BIGINT NOT NULL, payload_json TEXT NOT NULL DEFAULT '', error_code TEXT NOT NULL DEFAULT '', error_message TEXT NOT NULL DEFAULT '', retry_count INT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, posted_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, event_type, source_type, source_id, source_line_id))",
                    empty);
        }
        execRetry("CREATE INDEX IF NOT EXISTS idx_gl_events_status ON gl_posting_events(company_id, status)", empty);
        execRetry("CREATE INDEX IF NOT EXISTS idx_gl_events_source ON gl_posting_events(company_id, source_type, source_id)", empty);
    }

    /** Moving-average costing: DL/GR cost cols, inventory_cost_events. */
    static void ensureInventoryCostingSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path10 = "examples/erp/migrations/010_inventory_costing.sql";
        if (Erp.dialect.equals("sqlite")) {
            path10 = "examples/erp/migrations/010_inventory_costing_sqlite.sql";
        }
        runScriptFileLabeled(path10, "010");
        tryAlter("ALTER TABLE delivery_lines ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE delivery_lines ADD COLUMN cogs_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE delivery_lines ADD COLUMN cogs_returned_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE goods_receipt_lines ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE goods_receipt_lines ADD COLUMN line_value_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE inventory_transactions ADD COLUMN biz_date INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE inventory_transactions ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE inventory_transactions ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_cost_events (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, item_id INTEGER NOT NULL, event_type TEXT NOT NULL, quantity_delta INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, value_delta_amount INTEGER NOT NULL, qty_before INTEGER NOT NULL DEFAULT 0, value_before INTEGER NOT NULL DEFAULT 0, qty_after INTEGER NOT NULL DEFAULT 0, value_after INTEGER NOT NULL DEFAULT 0, avg_cost_after INTEGER NOT NULL DEFAULT 0, source_type TEXT NOT NULL, source_id INTEGER NOT NULL, source_line_id INTEGER NOT NULL DEFAULT 0, related_event_id INTEGER NOT NULL DEFAULT 0, biz_date INTEGER NOT NULL, posted_at INTEGER NOT NULL, posted_by INTEGER NOT NULL DEFAULT 0, reason TEXT NOT NULL DEFAULT '', idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE (company_id, idempotency_key))",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_cost_events (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, item_id BIGINT NOT NULL, event_type TEXT NOT NULL, quantity_delta INT NOT NULL, unit_cost_amount BIGINT NOT NULL DEFAULT 0, value_delta_amount BIGINT NOT NULL, qty_before INT NOT NULL DEFAULT 0, value_before BIGINT NOT NULL DEFAULT 0, qty_after INT NOT NULL DEFAULT 0, value_after BIGINT NOT NULL DEFAULT 0, avg_cost_after BIGINT NOT NULL DEFAULT 0, source_type TEXT NOT NULL, source_id BIGINT NOT NULL, source_line_id BIGINT NOT NULL DEFAULT 0, related_event_id BIGINT NOT NULL DEFAULT 0, biz_date BIGINT NOT NULL, posted_at BIGINT NOT NULL, posted_by BIGINT NOT NULL DEFAULT 0, reason TEXT NOT NULL DEFAULT '', idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE (company_id, idempotency_key))",
                    empty);
        }
    }

    /** Bank cash, AR/AP settlement columns, alloc reverse, source-event uniqueness. */
    static void ensureFinanceSettlementSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path9 = "examples/erp/migrations/009_finance_settlement.sql";
        if (Erp.dialect.equals("sqlite")) {
            path9 = "examples/erp/migrations/009_finance_settlement_sqlite.sql";
        }
        runScriptFileLabeled(path9, "009");
        tryAlter("ALTER TABLE bank_accounts ADD COLUMN balance INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE bank_accounts ADD COLUMN allow_overdraft INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ar_invoices ADD COLUMN adjusted_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ar_invoices ADD COLUMN settlement_status TEXT NOT NULL DEFAULT 'OPEN'");
        tryAlter("ALTER TABLE ar_invoices ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'CNY'");
        tryAlter("ALTER TABLE ap_invoices ADD COLUMN adjusted_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ap_invoices ADD COLUMN settlement_status TEXT NOT NULL DEFAULT 'OPEN'");
        tryAlter("ALTER TABLE ap_invoices ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'CNY'");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN unallocated_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN payment_method TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN external_ref TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN doc_no TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN created_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN unallocated_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN payment_method TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN external_ref TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN doc_no TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN created_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN voided_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN voided_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE incoming_payments ADD COLUMN void_reason TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN voided_at INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN voided_by INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE outgoing_payments ADD COLUMN void_reason TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE bank_cash_movements ADD COLUMN reverse_of_id INTEGER NOT NULL DEFAULT 0");
        String path12 = "examples/erp/migrations/012_payment_void.sql";
        if (Erp.dialect.equals("sqlite")) {
            path12 = "examples/erp/migrations/012_payment_void_sqlite.sql";
        }
        runScriptFileLabeled(path12, "012");
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS bank_cash_movements (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, bank_account_id INTEGER NOT NULL, movement_type TEXT NOT NULL, amount INTEGER NOT NULL, balance_after INTEGER NOT NULL, biz_date INTEGER NOT NULL, source_type TEXT NOT NULL, source_id INTEGER NOT NULL, party_id INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, idempotency_key TEXT NOT NULL, reverse_of_id INTEGER NOT NULL DEFAULT 0, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ar_ap_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, side TEXT NOT NULL, invoice_id INTEGER NOT NULL, adjust_amount INTEGER NOT NULL, reason TEXT NOT NULL DEFAULT '', source_type TEXT NOT NULL, source_id INTEGER NOT NULL, created_by INTEGER NOT NULL DEFAULT 0, biz_time INTEGER NOT NULL, created_at INTEGER NOT NULL, idempotency_key TEXT NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ar_settlement_allocs (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, payment_id INTEGER NOT NULL, invoice_id INTEGER NOT NULL, allocated_amount INTEGER NOT NULL, status TEXT NOT NULL, reverse_of_id INTEGER NOT NULL DEFAULT 0, reversed_by_id INTEGER NOT NULL DEFAULT 0, reason TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ap_settlement_allocs (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, payment_id INTEGER NOT NULL, invoice_id INTEGER NOT NULL, allocated_amount INTEGER NOT NULL, status TEXT NOT NULL, reverse_of_id INTEGER NOT NULL DEFAULT 0, reversed_by_id INTEGER NOT NULL DEFAULT 0, reason TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS finance_source_events (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, source_type TEXT NOT NULL, source_id INTEGER NOT NULL, event_type TEXT NOT NULL, ref_id INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, UNIQUE (company_id, source_type, source_id, event_type))",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS bank_cash_movements (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, bank_account_id BIGINT NOT NULL, movement_type TEXT NOT NULL, amount BIGINT NOT NULL, balance_after BIGINT NOT NULL, biz_date BIGINT NOT NULL, source_type TEXT NOT NULL, source_id BIGINT NOT NULL, party_id BIGINT NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '', created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, idempotency_key TEXT NOT NULL, reverse_of_id BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ar_ap_adjustments (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, side TEXT NOT NULL, invoice_id BIGINT NOT NULL, adjust_amount BIGINT NOT NULL, reason TEXT NOT NULL DEFAULT '', source_type TEXT NOT NULL, source_id BIGINT NOT NULL, created_by BIGINT NOT NULL DEFAULT 0, biz_time BIGINT NOT NULL, created_at BIGINT NOT NULL, idempotency_key TEXT NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ar_settlement_allocs (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, payment_id BIGINT NOT NULL, invoice_id BIGINT NOT NULL, allocated_amount BIGINT NOT NULL, status TEXT NOT NULL, reverse_of_id BIGINT NOT NULL DEFAULT 0, reversed_by_id BIGINT NOT NULL DEFAULT 0, reason TEXT NOT NULL DEFAULT '', created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ap_settlement_allocs (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, payment_id BIGINT NOT NULL, invoice_id BIGINT NOT NULL, allocated_amount BIGINT NOT NULL, status TEXT NOT NULL, reverse_of_id BIGINT NOT NULL DEFAULT 0, reversed_by_id BIGINT NOT NULL DEFAULT 0, reason TEXT NOT NULL DEFAULT '', created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, idempotency_key TEXT NOT NULL DEFAULT '', UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS finance_source_events (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, source_type TEXT NOT NULL, source_id BIGINT NOT NULL, event_type TEXT NOT NULL, ref_id BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, UNIQUE (company_id, source_type, source_id, event_type))",
                    empty);
        }
    }

    /** SO approval/fulfillment columns, reservations, sales returns, AR provisional cols. */
    static void ensureSalesOpsSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path8 = "examples/erp/migrations/008_sales_ops.sql";
        if (Erp.dialect.equals("sqlite")) {
            path8 = "examples/erp/migrations/008_sales_ops_sqlite.sql";
        }
        runScriptFileLabeled(path8, "008");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN warehouse_id INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN order_date INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN delivery_date INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY'");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN approval_status TEXT NOT NULL DEFAULT 'DRAFT'");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN fulfillment_status TEXT NOT NULL DEFAULT 'NONE'");
        tryAlter("ALTER TABLE sales_order_lines ADD COLUMN returned_qty INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE sales_order_lines ADD COLUMN discount_bps INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE sales_order_lines ADD COLUMN tax_bps INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE purchase_order_lines ADD COLUMN discount_bps INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE purchase_order_lines ADD COLUMN tax_bps INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE sales_orders ADD COLUMN contact_name TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE purchase_orders ADD COLUMN contact_name TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE companies ADD COLUMN default_tax_bps INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE delivery_lines ADD COLUMN returned_qty INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ar_invoices ADD COLUMN is_provisional INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ar_invoices ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_reservations (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, sales_order_id INTEGER NOT NULL, sales_order_line_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, quantity_consumed INTEGER NOT NULL DEFAULT 0, quantity_released INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS sales_returns (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, sales_order_id INTEGER NOT NULL, delivery_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, status TEXT NOT NULL, resellable INTEGER NOT NULL DEFAULT 1, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS sales_return_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, sales_return_id INTEGER NOT NULL, delivery_line_id INTEGER NOT NULL, sales_order_line_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL)",
                    empty);
            execRetry(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_source_dl ON ar_invoices (company_id, source_delivery_id) WHERE source_delivery_id > 0",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_reservations (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, sales_order_id BIGINT NOT NULL, sales_order_line_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, item_id BIGINT NOT NULL, quantity INT NOT NULL, quantity_consumed INT NOT NULL DEFAULT 0, quantity_released INT NOT NULL DEFAULT 0, status TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS sales_returns (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, doc_no TEXT NOT NULL, sales_order_id BIGINT NOT NULL, delivery_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, status TEXT NOT NULL, resellable INT NOT NULL DEFAULT 1, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_at BIGINT NOT NULL, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS sales_return_lines (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, sales_return_id BIGINT NOT NULL, delivery_line_id BIGINT NOT NULL, sales_order_line_id BIGINT NOT NULL, item_id BIGINT NOT NULL, quantity INT NOT NULL)",
                    empty);
        }
        /* Backfill approval/fulfillment from legacy status when still DRAFT defaults. */
        try {
            execRetry(
                    "UPDATE sales_orders SET approval_status=status WHERE (approval_status='DRAFT' OR approval_status='' OR approval_status IS NULL) AND status IN ('DRAFT','SUBMITTED','APPROVED','CANCELLED')",
                    empty);
        } catch (Exception ignored) {
        }
    }

    /** PO header fields, provisional AP columns, inventory adjust/count, item min_qty. */
    static void ensurePurchaseOpsSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path7 = "examples/erp/migrations/007_purchase_ops.sql";
        if (Erp.dialect.equals("sqlite")) {
            path7 = "examples/erp/migrations/007_purchase_ops_sqlite.sql";
        }
        runScriptFileLabeled(path7, "007");
        tryAlter("ALTER TABLE purchase_orders ADD COLUMN warehouse_id INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE purchase_orders ADD COLUMN order_date INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE purchase_orders ADD COLUMN expected_date INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE purchase_orders ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE purchase_orders ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY'");
        tryAlter("ALTER TABLE items ADD COLUMN min_qty INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ap_invoices ADD COLUMN is_provisional INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE ap_invoices ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, warehouse_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity_delta INTEGER NOT NULL, reason TEXT NOT NULL DEFAULT '', status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_counts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, warehouse_id INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, posted_at INTEGER NOT NULL DEFAULT 0, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_count_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, inventory_count_id INTEGER NOT NULL, item_id INTEGER NOT NULL, system_qty INTEGER NOT NULL, counted_qty INTEGER NOT NULL, variance_qty INTEGER NOT NULL)",
                    empty);
            execRetry(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_ap_source_gr ON ap_invoices (company_id, source_gr_id) WHERE source_gr_id > 0",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_adjustments (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, doc_no TEXT NOT NULL, warehouse_id BIGINT NOT NULL, item_id BIGINT NOT NULL, quantity_delta INT NOT NULL, reason TEXT NOT NULL DEFAULT '', status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_counts (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, doc_no TEXT NOT NULL, warehouse_id BIGINT NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, posted_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS inventory_count_lines (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, inventory_count_id BIGINT NOT NULL, item_id BIGINT NOT NULL, system_qty INT NOT NULL, counted_qty INT NOT NULL, variance_qty INT NOT NULL)",
                    empty);
        }
    }

    /** PO/SO/GR/DL/inventory tables when 001 script splitter skips CREATE. */
    static void ensureBusinessDocTables() {
        ArrayList<Object> empty = new ArrayList<Object>();
        if (Erp.dialect.equals("sqlite") == false) {
            return;
        }
        execRetry(
                "CREATE TABLE IF NOT EXISTS purchase_orders (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, supplier_id INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, warehouse_id INTEGER NOT NULL DEFAULT 0, order_date INTEGER NOT NULL DEFAULT 0, expected_date INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '', currency TEXT NOT NULL DEFAULT 'CNY', UNIQUE (company_id, doc_no))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS purchase_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, purchase_order_id INTEGER NOT NULL, line_no INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, received_qty INTEGER NOT NULL DEFAULT 0, unit_price_cents INTEGER NOT NULL, UNIQUE (purchase_order_id, line_no))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS sales_orders (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, customer_id INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE (company_id, doc_no))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS sales_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, sales_order_id INTEGER NOT NULL, line_no INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, delivered_qty INTEGER NOT NULL DEFAULT 0, unit_price_cents INTEGER NOT NULL, UNIQUE (sales_order_id, line_no))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS goods_receipts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, purchase_order_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, doc_no))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS goods_receipt_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, goods_receipt_id INTEGER NOT NULL, purchase_order_line_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL)",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, sales_order_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, doc_no))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS delivery_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, delivery_id INTEGER NOT NULL, sales_order_line_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL)",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS inventory_balances (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity_on_hand INTEGER NOT NULL DEFAULT 0, value_amount INTEGER NOT NULL DEFAULT 0, row_version INTEGER NOT NULL DEFAULT 0, UNIQUE (company_id, warehouse_id, item_id))",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS inventory_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, item_id INTEGER NOT NULL, txn_type TEXT NOT NULL, quantity_delta INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, value_delta_amount INTEGER NOT NULL DEFAULT 0, ref_doc_type TEXT NOT NULL, ref_doc_id INTEGER NOT NULL, created_at INTEGER NOT NULL)",
                empty);
        execRetry(
                "CREATE TABLE IF NOT EXISTS document_references (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, ref_kind TEXT NOT NULL, from_doc_type TEXT NOT NULL, from_doc_id INTEGER NOT NULL, to_doc_type TEXT NOT NULL, to_doc_id INTEGER NOT NULL, created_at INTEGER NOT NULL)",
                empty);
        tryAlter("ALTER TABLE items ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE items ADD COLUMN min_qty INTEGER NOT NULL DEFAULT 0");
    }

    /** Purchase requests, light masters, demo_seed_log. */
    static void ensureDemoOpsSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path6 = "examples/erp/migrations/006_demo_ops.sql";
        if (Erp.dialect.equals("sqlite")) {
            path6 = "examples/erp/migrations/006_demo_ops_sqlite.sql";
        }
        runScriptFileLabeled(path6, "006");
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS item_categories (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS units_of_measure (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS employees (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, department TEXT NOT NULL DEFAULT '', title TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'ACTIVE', created_at INTEGER NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS departments (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS purchase_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, requester_name TEXT NOT NULL DEFAULT '', supplier_id INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', seed_key TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE (company_id, doc_no))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS purchase_request_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, purchase_request_id INTEGER NOT NULL, line_no INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_price_cents INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS demo_seed_log (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, seed_version INTEGER NOT NULL, seed_key TEXT NOT NULL, status TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', applied_at INTEGER NOT NULL)",
                    empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_demo_seed_log_key ON demo_seed_log (company_id, seed_key)",
                    empty);
        }
        tryAlter("ALTER TABLE items ADD COLUMN category_code TEXT NOT NULL DEFAULT ''");
        tryAlter("ALTER TABLE items ADD COLUMN uom TEXT NOT NULL DEFAULT 'EA'");
    }

    /** Minimal schema required for formal boot / login (idempotent). */
    static void ensureCoreTables() {
        ArrayList<Object> empty = new ArrayList<Object>();
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS companies (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, created_at INTEGER NOT NULL)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, email TEXT NOT NULL, display_name TEXT NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'user', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE (company_id, email))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS roles (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS permissions (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS user_roles (user_id INTEGER NOT NULL, role_id INTEGER NOT NULL, PRIMARY KEY (user_id, role_id))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS role_permissions (role_id INTEGER NOT NULL, permission_id INTEGER NOT NULL, PRIMARY KEY (role_id, permission_id))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS business_partners (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, sku TEXT NOT NULL, name TEXT NOT NULL, unit_price_cents INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, UNIQUE (company_id, sku))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS warehouses (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS number_sequences (company_id INTEGER NOT NULL, seq_name TEXT NOT NULL, next_val INTEGER NOT NULL DEFAULT 1, PRIMARY KEY (company_id, seq_name))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (version TEXT PRIMARY KEY, applied_at INTEGER NOT NULL, checksum TEXT NOT NULL DEFAULT '')",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS idempotency_keys (company_id INTEGER NOT NULL, idempotency_key TEXT NOT NULL, operation TEXT NOT NULL, response_json TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (company_id, idempotency_key))",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (version TEXT PRIMARY KEY, applied_at BIGINT NOT NULL, checksum TEXT NOT NULL DEFAULT '')",
                    empty);
        }
    }

    /** Pilot columns/tables: book isolation, CAS version, reverse links, opening docs, wizard. */
    static void ensurePilotSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path5 = "examples/erp/migrations/005_pilot.sql";
        if (Erp.dialect.equals("sqlite")) {
            path5 = "examples/erp/migrations/005_pilot_sqlite.sql";
        }
        /* 005 best-effort: INDEX/TABLE order noise; ensure* DDL below is authoritative. */
        String fatalBefore005 = Erp.migrateFatal;
        try {
            runScriptFileLabeled(path5, "005");
        } catch (Exception ex) {
            System.out.println("005_pilot_script_partial");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            reconnect();
        }
        Erp.migrateFatal = fatalBefore005;
        tryAlter("ALTER TABLE companies ADD COLUMN book_kind TEXT NOT NULL DEFAULT 'FORMAL'");
        tryAlter("ALTER TABLE companies ADD COLUMN timezone TEXT NOT NULL DEFAULT 'UTC'");
        tryAlter("ALTER TABLE companies ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'CNY'");
        tryAlter("ALTER TABLE companies ADD COLUMN amount_scale INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE companies ADD COLUMN setup_completed INTEGER NOT NULL DEFAULT 0");
        if (Erp.dialect.equals("sqlite")) {
            tryAlter("ALTER TABLE inventory_balances ADD COLUMN row_version INTEGER NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE goods_receipts ADD COLUMN reverses_id INTEGER NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE goods_receipts ADD COLUMN reversed_by_id INTEGER NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE goods_receipts ADD COLUMN reverse_reason TEXT NOT NULL DEFAULT ''");
            tryAlter("ALTER TABLE deliveries ADD COLUMN reverses_id INTEGER NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE deliveries ADD COLUMN reversed_by_id INTEGER NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE deliveries ADD COLUMN reverse_reason TEXT NOT NULL DEFAULT ''");
        } else {
            tryAlter("ALTER TABLE inventory_balances ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE goods_receipts ADD COLUMN IF NOT EXISTS reverses_id BIGINT NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE goods_receipts ADD COLUMN IF NOT EXISTS reversed_by_id BIGINT NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE goods_receipts ADD COLUMN IF NOT EXISTS reverse_reason TEXT NOT NULL DEFAULT ''");
            tryAlter("ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS reverses_id BIGINT NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS reversed_by_id BIGINT NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS reverse_reason TEXT NOT NULL DEFAULT ''");
            tryAlter("ALTER TABLE companies ADD COLUMN IF NOT EXISTS book_kind TEXT NOT NULL DEFAULT 'FORMAL'");
            tryAlter("ALTER TABLE companies ADD COLUMN IF NOT EXISTS timezone TEXT NOT NULL DEFAULT 'UTC'");
            tryAlter("ALTER TABLE companies ADD COLUMN IF NOT EXISTS currency_code TEXT NOT NULL DEFAULT 'CNY'");
            tryAlter("ALTER TABLE companies ADD COLUMN IF NOT EXISTS amount_scale INT NOT NULL DEFAULT 0");
            tryAlter("ALTER TABLE companies ADD COLUMN IF NOT EXISTS setup_completed INT NOT NULL DEFAULT 0");
        }
        execRetry("UPDATE inventory_balances SET row_version=1 WHERE row_version=0 OR row_version IS NULL", empty);
        /* Explicit ensures — runScriptSafe can skip mid-script statements on some dialects */
        if (Erp.dialect.equals("sqlite")) {
            /* Dual table-level UNIQUE breaks jnative SQLite prepare — indexes instead. */
            execRetry(
                    "CREATE TABLE IF NOT EXISTS opening_ar_docs (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, customer_id INTEGER NOT NULL, business_date INTEGER NOT NULL, amount INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, posted_at INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ar_docs_doc_no ON opening_ar_docs (company_id, doc_no)", empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ar_docs_idem ON opening_ar_docs (company_id, idempotency_key)", empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS opening_ap_docs (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, supplier_id INTEGER NOT NULL, business_date INTEGER NOT NULL, amount INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, posted_at INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ap_docs_doc_no ON opening_ap_docs (company_id, doc_no)", empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ap_docs_idem ON opening_ap_docs (company_id, idempotency_key)", empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS opening_stock_docs (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, warehouse_id INTEGER NOT NULL, business_date INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, posted_at INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_stock_docs_doc_no ON opening_stock_docs (company_id, doc_no)", empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_stock_docs_idem ON opening_stock_docs (company_id, idempotency_key)", empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS opening_stock_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_id INTEGER NOT NULL, item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS import_batches (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, batch_code TEXT NOT NULL, entity_type TEXT NOT NULL, status TEXT NOT NULL, total_rows INTEGER NOT NULL DEFAULT 0, ok_rows INTEGER NOT NULL DEFAULT 0, error_rows INTEGER NOT NULL DEFAULT 0, error_report TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)",
                    empty);
            execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_import_batches_code ON import_batches (company_id, batch_code)", empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS setup_wizard_state (company_id INTEGER NOT NULL PRIMARY KEY, step_code TEXT NOT NULL DEFAULT 'COMPANY', payload_json TEXT NOT NULL DEFAULT '{}', completed INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, updated_by INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS demo_seed_meta (company_id INTEGER NOT NULL PRIMARY KEY, seed_version INTEGER NOT NULL, applied_at INTEGER NOT NULL)",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS opening_ar_docs (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), doc_no TEXT NOT NULL, customer_id BIGINT NOT NULL, business_date BIGINT NOT NULL, amount BIGINT NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, posted_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS opening_ap_docs (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), doc_no TEXT NOT NULL, supplier_id BIGINT NOT NULL, business_date BIGINT NOT NULL, amount BIGINT NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', created_by BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, posted_at BIGINT NOT NULL DEFAULT 0, UNIQUE (company_id, doc_no), UNIQUE (company_id, idempotency_key))",
                    empty);
        }
    }

    static void tryAlter(String sql) {
        try {
            exec(sql, new ArrayList<Object>());
        } catch (Exception ignored) {
        }
    }

    static int tableExists(String name) {
        try {
            if (Erp.dialect.equals("sqlite")) {
                ArrayList<Object> p = new ArrayList<Object>();
                p.add(name);
                Object v = queryOne(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", p, new LongMapper());
                if (ErpNum.asLong(v) > 0L) {
                    return 1;
                }
                return 0;
            }
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(name);
            Object v = queryOne(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    p, new LongMapper());
            if (ErpNum.asLong(v) > 0L) {
                return 1;
            }
            return 0;
        } catch (Exception ex) {
            reconnect();
            return 0;
        }
    }

    static void ensureSqliteUserDeptColumns() {
        ArrayList<Object> empty = new ArrayList<Object>();
        try {
            exec("ALTER TABLE users ADD COLUMN department_id INTEGER NOT NULL DEFAULT 0", empty);
        } catch (Exception ignored) {
        }
        try {
            exec("ALTER TABLE users ADD COLUMN owner_user_id INTEGER NOT NULL DEFAULT 0", empty);
        } catch (Exception ignored) {
        }
    }

    static void recordMigration(String version) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(version);
        Object existing = queryOne("SELECT version FROM schema_migrations WHERE version=?", p, new StringMapper());
        if (existing != null) {
            return;
        }
        long now = AsyncClockNative.wallMillis();
        exec(
                "INSERT INTO schema_migrations(version, applied_at, checksum) VALUES ('"
                        + version + "'," + CryptoParse.longToString(now) + ",'')",
                new ArrayList<Object>());
    }

    /** Repair manufacturing tables if 003 script was incomplete (short literals only). */
    static void ensureMfgTables() {
        ArrayList<Object> empty = new ArrayList<Object>();
        reconnect();
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS bills_of_material (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, parent_item_id INTEGER NOT NULL, bom_code TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, base_quantity INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL, effective_from INTEGER NOT NULL DEFAULT 0, effective_to INTEGER NOT NULL DEFAULT 4102444800000, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE (company_id, bom_code, version))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS bom_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, bom_id INTEGER NOT NULL, component_item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, uom TEXT NOT NULL DEFAULT 'PCS', scrap_rate_bp INTEGER NOT NULL DEFAULT 0, warehouse_id INTEGER, sequence INTEGER NOT NULL DEFAULT 10)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_orders (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, order_number TEXT NOT NULL, item_id INTEGER NOT NULL, bom_id INTEGER NOT NULL, bom_version INTEGER NOT NULL, planned_quantity INTEGER NOT NULL, completed_quantity INTEGER NOT NULL DEFAULT 0, scrapped_quantity INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, warehouse_id INTEGER NOT NULL, planned_start_date INTEGER NOT NULL DEFAULT 0, planned_end_date INTEGER NOT NULL DEFAULT 0, actual_start_date INTEGER NOT NULL DEFAULT 0, actual_end_date INTEGER NOT NULL DEFAULT 0, over_issue_tolerance_bp INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE (company_id, order_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_order_components (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, production_order_id INTEGER NOT NULL, component_item_id INTEGER NOT NULL, required_quantity INTEGER NOT NULL, issued_quantity INTEGER NOT NULL DEFAULT 0, returned_quantity INTEGER NOT NULL DEFAULT 0, warehouse_id INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_wip (production_order_id INTEGER PRIMARY KEY, company_id INTEGER NOT NULL, material_cost INTEGER NOT NULL DEFAULT 0, labor_cost INTEGER NOT NULL DEFAULT 0, overhead_cost INTEGER NOT NULL DEFAULT 0, transferred_cost INTEGER NOT NULL DEFAULT 0, remaining_cost INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_issues (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, production_order_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, posting_date INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, idempotency_key), UNIQUE (company_id, doc_no))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_issue_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, issue_id INTEGER NOT NULL, component_item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, amount INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_returns (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, production_order_id INTEGER NOT NULL, issue_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, posting_date INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_return_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, return_id INTEGER NOT NULL, component_item_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, amount INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_receipts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, production_order_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, total_cost INTEGER NOT NULL DEFAULT 0, posting_date INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, idempotency_key), UNIQUE (company_id, doc_no))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_scraps (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, production_order_id INTEGER NOT NULL, quantity INTEGER NOT NULL, reason TEXT NOT NULL DEFAULT '', cost_amount INTEGER NOT NULL DEFAULT 0, cost_policy TEXT NOT NULL DEFAULT 'ABSORB_TO_COMPLETED', created_at INTEGER NOT NULL)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS mrp_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, run_code TEXT NOT NULL, planning_start_date INTEGER NOT NULL, planning_end_date INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, run_code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS mrp_requirements (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, mrp_run_id INTEGER NOT NULL, item_id INTEGER NOT NULL, bucket_date INTEGER NOT NULL, gross_requirement INTEGER NOT NULL DEFAULT 0, scheduled_receipts INTEGER NOT NULL DEFAULT 0, projected_available INTEGER NOT NULL DEFAULT 0, net_requirement INTEGER NOT NULL DEFAULT 0, planned_order_receipt INTEGER NOT NULL DEFAULT 0, planned_order_release INTEGER NOT NULL DEFAULT 0)",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS mrp_suggestions (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, mrp_run_id INTEGER NOT NULL, suggestion_kind TEXT NOT NULL, item_id INTEGER NOT NULL, required_quantity INTEGER NOT NULL, required_date INTEGER NOT NULL, suggested_order_date INTEGER NOT NULL, supplier_id INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'OPEN', converted_doc_id INTEGER NOT NULL DEFAULT 0)",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS production_receipts (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, doc_no TEXT NOT NULL, production_order_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, quantity INT NOT NULL, unit_cost_amount BIGINT NOT NULL DEFAULT 0, total_cost BIGINT NOT NULL DEFAULT 0, posting_date BIGINT NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at BIGINT NOT NULL)",
                    empty);
        }
    }

    static void reconnect() {
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            url = "postgresql://jnative_erp@127.0.0.1:5432/jnative_erp";
        }
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = Erp.loop;
        JnFuture dbF = Database.connectWithConfig(cfg);
        FutureBlocker.blockingWait(dbF);
        if (dbF.getError() != null) {
            throw new ConnectionException("db reconnect failed");
        }
        Erp.db = (Database) dbF.getValue();
        applySqlitePragmas();
    }

    /** Concurrent HTTP writes need a busy wait; WAL reduces reader/writer blocking. */
    static void applySqlitePragmas() {
        if (Erp.dialect.equals("sqlite") == false) {
            return;
        }
        ArrayList<Object> empty = new ArrayList<Object>();
        try {
            exec("PRAGMA busy_timeout=8000", empty);
        } catch (Exception ignored) {
        }
        try {
            exec("PRAGMA journal_mode=WAL", empty);
        } catch (Exception ignored) {
        }
    }

    /**
     * Load migration file as bytes and execute statement-by-statement.
     * Avoid File.readString + String concat loops — those produced mid-CREATE fragments
     * (e.g. production_issues split) under JNative string/GC pressure.
     */
    static void runScriptFileLabeled(String path, String label) {
        byte[] raw = File.readBytes(path);
        if (raw == null) {
            throw new RuntimeException("migration read failed: " + path);
        }
        runScriptBytesLabeled(raw, label, path);
    }

    static void runScriptSafe(String sql) {
        if (sql == null || sql.length() == 0) {
            return;
        }
        ByteBuf buf = new ByteBuf();
        appendSqlBytes(buf, sql);
        runScriptBytesLabeled(buf.toArray(), "script", "inline");
    }

    static void runScriptSafeLabeled(String sql, String label) {
        if (sql == null || sql.length() == 0) {
            return;
        }
        ByteBuf buf = new ByteBuf();
        appendSqlBytes(buf, sql);
        runScriptBytesLabeled(buf.toArray(), label, "inline");
    }

    static void runScriptBytesLabeled(byte[] raw, String label, String path) {
        ArrayList<Object> empty = new ArrayList<Object>();
        if (raw == null || raw.length == 0) {
            return;
        }
        ByteBuf stmt = new ByteBuf();
        int i = 0;
        int inQuote = 0;
        int lineStart = 1;
        int stmtIdx = 0;
        while (i < raw.length) {
            int c = raw[i];
            if (lineStart != 0 && c == 45 && i + 1 < raw.length && raw[i + 1] == 45) {
                while (i < raw.length && raw[i] != 10) {
                    i = i + 1;
                }
                if (i < raw.length && raw[i] == 10) {
                    i = i + 1;
                }
                lineStart = 1;
            } else {
                if (c == 39) {
                    stmt.appendByte(c);
                    if (inQuote != 0 && i + 1 < raw.length && raw[i + 1] == 39) {
                        stmt.appendByte(39);
                        i = i + 2;
                        lineStart = 0;
                    } else {
                        if (inQuote != 0) {
                            inQuote = 0;
                        } else {
                            inQuote = 1;
                        }
                        lineStart = 0;
                        i = i + 1;
                    }
                } else {
                    if (inQuote == 0 && c == 59) {
                        String one = collapseSql(stmt.toStringUtf8());
                        stmt = new ByteBuf();
                        if (one.length() > 0) {
                            stmtIdx = stmtIdx + 1;
                            execRetryLabeled(one, empty, label, stmtIdx);
                        }
                        lineStart = 1;
                        i = i + 1;
                    } else {
                        stmt.appendByte(c);
                        if (c == 10) {
                            lineStart = 1;
                        } else {
                            if (c != 13 && c != 32 && c != 9) {
                                lineStart = 0;
                            }
                        }
                        i = i + 1;
                    }
                }
            }
        }
        String tail = collapseSql(stmt.toStringUtf8());
        if (tail.length() > 0) {
            stmtIdx = stmtIdx + 1;
            execRetryLabeled(tail, empty, label, stmtIdx);
        }
        if (Erp.migrateFatal.length() > 0) {
            /* Keep going so ensure* can repair; migrate() final check decides readiness. */
            System.out.println("MIGRATE_SCRIPT_HAS_FATAL " + path + " " + label);
        }
    }

    static void appendSqlBytes(ByteBuf buf, String s) {
        if (s == null || s.length() == 0) {
            return;
        }
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c >= 0 && c <= 255) {
                buf.appendByte(c);
            }
            i = i + 1;
        }
    }

    /** Kept for callers/tests that still strip whole scripts. */
    static String stripSqlLineComments(String sql) {
        if (sql == null || sql.length() == 0) {
            return "";
        }
        /* Prefer statement runner; this helper only strips full-line comments lightly. */
        String out = "";
        int start = 0;
        int alive = 1;
        int parts = 0;
        while (alive != 0) {
            String rest = sql.substring(start, sql.length());
            int rel = rest.indexOf("\n");
            String line = "";
            if (rel < 0) {
                line = rest;
                alive = 0;
            } else {
                line = sql.substring(start, start + rel);
                start = start + rel + 1;
            }
            String t = trimStmt(line);
            if (t.length() >= 2 && t.charAt(0) == 45 && t.charAt(1) == 45) {
                /* skip */
            } else {
                if (parts == 0) {
                    out = stripInlineSqlComment(line);
                } else {
                    out = out + "\n" + stripInlineSqlComment(line);
                }
                parts = parts + 1;
            }
        }
        return out;
    }

    /** Remove " -- ..." tail when -- is outside single quotes. */
    static String stripInlineSqlComment(String line) {
        if (line == null || line.length() < 2) {
            return line == null ? "" : line;
        }
        int i = 0;
        int inQuote = 0;
        while (i < line.length()) {
            int c = line.charAt(i);
            if (c == 39) {
                /* SQL single quote; '' escape */
                if (inQuote != 0 && i + 1 < line.length() && line.charAt(i + 1) == 39) {
                    i = i + 2;
                } else {
                    if (inQuote != 0) {
                        inQuote = 0;
                    } else {
                        inQuote = 1;
                    }
                    i = i + 1;
                }
            } else {
                if (inQuote == 0 && c == 45 && i + 1 < line.length() && line.charAt(i + 1) == 45) {
                    return line.substring(0, i);
                }
                i = i + 1;
            }
        }
        return line;
    }

    /**
     * Trim leading comment lines. Keep newlines — sqlite3_prepare accepts them.
     * Former char-by-char flatten truncated long CREATE TABLE text (DEFAULT→DEFAUL).
     */
    static String collapseSql(String s) {
        String t = trimStmt(s);
        if (t.length() == 0) {
            return "";
        }
        while (t.length() >= 2 && t.charAt(0) == 45 && t.charAt(1) == 45) {
            int nl = t.indexOf("\n");
            if (nl < 0) {
                return "";
            }
            t = trimStmt(t.substring(nl + 1, t.length()));
        }
        return t;
    }

    static void execRetry(String sql, ArrayList<Object> params) {
        execRetryLabeled(sql, params, "ddl", 0);
    }

    static int migrateIgnorable(String msg) {
        if (msg == null) {
            return 0;
        }
        /* Truncated / incomplete SQL must never be treated as ignorable. */
        if (msg.indexOf("incomplete input") >= 0) {
            return 0;
        }
        if (msg.indexOf("syntax error") >= 0) {
            return 0;
        }
        if (msg.indexOf("already exists") >= 0) {
            return 1;
        }
        if (msg.indexOf("duplicate column") >= 0) {
            return 1;
        }
        if (msg.indexOf("duplicate") >= 0 && msg.indexOf("syntax") < 0) {
            return 1;
        }
        /* SQLite ALTER ADD COLUMN when column present */
        if (msg.indexOf("duplicate column name") >= 0) {
            return 1;
        }
        return 0;
    }

    /** incomplete input / syntax on DDL is structural — retry will not heal truncated SQL. */
    static int migrateNonRetryable(String msg) {
        if (msg == null) {
            return 0;
        }
        if (msg.indexOf("incomplete input") >= 0) {
            return 1;
        }
        if (msg.indexOf("syntax error") >= 0) {
            return 1;
        }
        return 0;
    }

    static int migrateLooksTruncated(String sql) {
        if (sql == null || sql.length() == 0) {
            return 0;
        }
        String t = trimStmt(sql);
        if (t.length() < 8) {
            return 0;
        }
        /* Valid DDL/DML starts with known keywords (ASCII case variants). */
        if (t.indexOf("CREATE ") == 0 || t.indexOf("create ") == 0 || t.indexOf("Create ") == 0) {
            return 0;
        }
        if (t.indexOf("ALTER ") == 0 || t.indexOf("alter ") == 0) {
            return 0;
        }
        if (t.indexOf("INSERT ") == 0 || t.indexOf("insert ") == 0) {
            return 0;
        }
        if (t.indexOf("UPDATE ") == 0 || t.indexOf("update ") == 0) {
            return 0;
        }
        if (t.indexOf("DELETE ") == 0 || t.indexOf("delete ") == 0) {
            return 0;
        }
        if (t.indexOf("DROP ") == 0 || t.indexOf("drop ") == 0) {
            return 0;
        }
        if (t.indexOf("PRAGMA ") == 0 || t.indexOf("pragma ") == 0) {
            return 0;
        }
        if (t.indexOf("SELECT ") == 0 || t.indexOf("select ") == 0) {
            return 0;
        }
        /* Fragments like "company_id INTEGER NOT NULL, ..." without a verb. */
        if (t.indexOf("INTEGER") >= 0 || t.indexOf("TEXT") >= 0 || t.indexOf("BIGINT") >= 0) {
            return 1;
        }
        return 0;
    }

    static void execRetryLabeled(String sql, ArrayList<Object> params, String label, int stmtIdx) {
        if (sql == null) {
            return;
        }
        String oneLine = trimStmt(sql);
        if (oneLine.length() == 0) {
            return;
        }
        if (migrateLooksTruncated(oneLine) != 0) {
            String detail = "truncated SQL fragment in " + label + " stmt#" + String.valueOf(stmtIdx)
                    + ": " + oneLine;
            System.out.println("MIGRATE_FATAL " + detail);
            if (Erp.migrateFatal.length() == 0) {
                Erp.migrateFatal = detail;
            } else {
                Erp.migrateFatal = Erp.migrateFatal + "; " + detail;
            }
            return;
        }
        try {
            exec(oneLine, params);
            return;
        } catch (Exception ex) {
            String msg = "";
            if (ex.getMessage() != null) {
                msg = ex.getMessage();
            }
            if (migrateIgnorable(msg) != 0) {
                return;
            }
            if (migrateNonRetryable(msg) != 0) {
                String detail = label + " stmt#" + String.valueOf(stmtIdx) + " " + msg + " SQL=" + oneLine;
                System.out.println("MIGRATE_FATAL " + detail);
                if (Erp.migrateFatal.length() == 0) {
                    Erp.migrateFatal = detail;
                } else {
                    Erp.migrateFatal = Erp.migrateFatal + "; " + detail;
                }
                return;
            }
            /* Transient prepare/bind: reconnect and retry once. */
            System.out.println("MIGRATE_RETRY " + label + "#" + String.valueOf(stmtIdx) + " " + msg);
            reconnect();
            try {
                exec(oneLine, params);
                return;
            } catch (Exception ex2) {
                String msg2 = "";
                if (ex2.getMessage() != null) {
                    msg2 = ex2.getMessage();
                }
                if (migrateIgnorable(msg2) != 0) {
                    return;
                }
                String detail = label + " stmt#" + String.valueOf(stmtIdx) + " " + msg2 + " SQL=" + oneLine;
                System.out.println("MIGRATE_FATAL " + detail);
                if (Erp.migrateFatal.length() == 0) {
                    Erp.migrateFatal = detail;
                } else {
                    Erp.migrateFatal = Erp.migrateFatal + "; " + detail;
                }
                reconnect();
                return;
            }
        }
    }

    static void ensureFinanceArApTables() {
        ArrayList<Object> empty = new ArrayList<Object>();
        if (Erp.dialect.equals("sqlite")) {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS gl_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, account_code TEXT NOT NULL, account_name TEXT NOT NULL, account_type TEXT NOT NULL, parent_account_id INTEGER, posting_allowed INTEGER NOT NULL DEFAULT 1, active INTEGER NOT NULL DEFAULT 1, UNIQUE (company_id, account_code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS account_mappings (company_id INTEGER NOT NULL, mapping_key TEXT NOT NULL, account_id INTEGER NOT NULL, PRIMARY KEY (company_id, mapping_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS fiscal_periods (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, fiscal_year INTEGER NOT NULL, period_number INTEGER NOT NULL, start_date INTEGER NOT NULL, end_date INTEGER NOT NULL, status TEXT NOT NULL, UNIQUE (company_id, fiscal_year, period_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS journal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, journal_number TEXT NOT NULL, posting_date INTEGER NOT NULL, document_date INTEGER NOT NULL, source_type TEXT NOT NULL, source_id INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', created_by INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, reversed_journal_id INTEGER, UNIQUE (company_id, journal_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS journal_entry_lines (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, journal_entry_id INTEGER NOT NULL, line_no INTEGER NOT NULL, account_id INTEGER NOT NULL, debit_amount INTEGER NOT NULL DEFAULT 0, credit_amount INTEGER NOT NULL DEFAULT 0, business_partner_id INTEGER, description TEXT NOT NULL DEFAULT '')",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS bank_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, account_name TEXT NOT NULL, currency TEXT NOT NULL DEFAULT 'CNY', ledger_account_id INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1, UNIQUE (company_id, account_name))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ar_invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, customer_id INTEGER NOT NULL, invoice_number TEXT NOT NULL, invoice_date INTEGER NOT NULL, due_date INTEGER NOT NULL, currency TEXT NOT NULL DEFAULT 'USD', total_amount INTEGER NOT NULL, paid_amount INTEGER NOT NULL DEFAULT 0, open_amount INTEGER NOT NULL, status TEXT NOT NULL, source_delivery_id INTEGER, journal_entry_id INTEGER, created_at INTEGER NOT NULL, UNIQUE (company_id, invoice_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS incoming_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, customer_id INTEGER NOT NULL, payment_date INTEGER NOT NULL, amount INTEGER NOT NULL, allocated_amount INTEGER NOT NULL DEFAULT 0, bank_account_id INTEGER NOT NULL, status TEXT NOT NULL, journal_entry_id INTEGER, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS incoming_payment_allocations (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, payment_id INTEGER NOT NULL, invoice_id INTEGER NOT NULL, allocated_amount INTEGER NOT NULL, UNIQUE (payment_id, invoice_id))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ap_invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, supplier_id INTEGER NOT NULL, invoice_number TEXT NOT NULL, invoice_date INTEGER NOT NULL, due_date INTEGER NOT NULL, currency TEXT NOT NULL DEFAULT 'USD', total_amount INTEGER NOT NULL, paid_amount INTEGER NOT NULL DEFAULT 0, open_amount INTEGER NOT NULL, status TEXT NOT NULL, source_gr_id INTEGER, journal_entry_id INTEGER, created_at INTEGER NOT NULL, UNIQUE (company_id, invoice_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS outgoing_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, supplier_id INTEGER NOT NULL, payment_date INTEGER NOT NULL, amount INTEGER NOT NULL, allocated_amount INTEGER NOT NULL DEFAULT 0, bank_account_id INTEGER NOT NULL, status TEXT NOT NULL, journal_entry_id INTEGER, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS outgoing_payment_allocations (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, payment_id INTEGER NOT NULL, invoice_id INTEGER NOT NULL, allocated_amount INTEGER NOT NULL, UNIQUE (payment_id, invoice_id))",
                    empty);
        } else {
            execRetry(
                    "CREATE TABLE IF NOT EXISTS gl_accounts (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, account_code TEXT NOT NULL, account_name TEXT NOT NULL, account_type TEXT NOT NULL, parent_account_id BIGINT, posting_allowed INT NOT NULL DEFAULT 1, active INT NOT NULL DEFAULT 1, UNIQUE (company_id, account_code))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ar_invoices (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, invoice_number TEXT NOT NULL, invoice_date BIGINT NOT NULL, due_date BIGINT NOT NULL, currency TEXT NOT NULL DEFAULT 'USD', total_amount BIGINT NOT NULL, paid_amount BIGINT NOT NULL DEFAULT 0, open_amount BIGINT NOT NULL, status TEXT NOT NULL, source_delivery_id BIGINT, journal_entry_id BIGINT, created_at BIGINT NOT NULL, UNIQUE (company_id, invoice_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS incoming_payments (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, payment_date BIGINT NOT NULL, amount BIGINT NOT NULL, allocated_amount BIGINT NOT NULL DEFAULT 0, bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id), status TEXT NOT NULL, journal_entry_id BIGINT, idempotency_key TEXT NOT NULL, created_at BIGINT NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS incoming_payment_allocations (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, payment_id BIGINT NOT NULL REFERENCES incoming_payments(id), invoice_id BIGINT NOT NULL REFERENCES ar_invoices(id), allocated_amount BIGINT NOT NULL, UNIQUE (payment_id, invoice_id))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS ap_invoices (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL, invoice_number TEXT NOT NULL, invoice_date BIGINT NOT NULL, due_date BIGINT NOT NULL, currency TEXT NOT NULL DEFAULT 'USD', total_amount BIGINT NOT NULL, paid_amount BIGINT NOT NULL DEFAULT 0, open_amount BIGINT NOT NULL, status TEXT NOT NULL, source_gr_id BIGINT, journal_entry_id BIGINT, created_at BIGINT NOT NULL, UNIQUE (company_id, invoice_number))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS outgoing_payments (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL, payment_date BIGINT NOT NULL, amount BIGINT NOT NULL, allocated_amount BIGINT NOT NULL DEFAULT 0, bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id), status TEXT NOT NULL, journal_entry_id BIGINT, idempotency_key TEXT NOT NULL, created_at BIGINT NOT NULL, UNIQUE (company_id, idempotency_key))",
                    empty);
            execRetry(
                    "CREATE TABLE IF NOT EXISTS outgoing_payment_allocations (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, payment_id BIGINT NOT NULL REFERENCES outgoing_payments(id), invoice_id BIGINT NOT NULL REFERENCES ap_invoices(id), allocated_amount BIGINT NOT NULL, UNIQUE (payment_id, invoice_id))",
                    empty);
        }
    }

    static void ensureSqliteInventoryValueColumns() {
        ArrayList<Object> empty = new ArrayList<Object>();
        try {
            exec("ALTER TABLE items ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0", empty);
        } catch (Exception ignored) {
        }
        try {
            exec("ALTER TABLE inventory_balances ADD COLUMN value_amount INTEGER NOT NULL DEFAULT 0", empty);
        } catch (Exception ignored) {
        }
        try {
            exec("ALTER TABLE inventory_transactions ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0", empty);
        } catch (Exception ignored) {
        }
        try {
            exec("ALTER TABLE inventory_transactions ADD COLUMN value_delta_amount INTEGER NOT NULL DEFAULT 0", empty);
        } catch (Exception ignored) {
        }
    }

    static void runScript(String sql) {
        ArrayList<Object> empty = new ArrayList<Object>();
        int start = 0;
        int i = 0;
        int lineStart = 1;
        while (i < sql.length()) {
            int c = sql.charAt(i);
            if (lineStart != 0 && c == 45 && i + 1 < sql.length() && sql.charAt(i + 1) == 45) {
                while (i < sql.length() && sql.charAt(i) != 10) {
                    i = i + 1;
                }
                continue;
            }
            if (c == 59) {
                String stmt = trimStmt(sql.substring(start, i));
                if (stmt.length() > 0) {
                    exec(stmt, empty);
                }
                start = i + 1;
                lineStart = 1;
            } else {
                if (c == 10) {
                    lineStart = 1;
                } else {
                    if (c == 13) {
                        /* skip CR */
                    } else {
                        if (c == 32 || c == 9) {
                            /* whitespace */
                        } else {
                            lineStart = 0;
                        }
                    }
                }
            }
            i = i + 1;
        }
        String tail = trimStmt(sql.substring(start, sql.length()));
        if (tail.length() > 0) {
            exec(tail, empty);
        }
    }

    static String trimStmt(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }

    static void exec(String sql, ArrayList<Object> params) {
        FutureBlocker.blockingGet(Erp.db.execute(sql, params));
    }

    static void execConn(DbConnection conn, String sql, ArrayList<Object> params) {
        FutureBlocker.blockingGet(conn.execute(sql, params));
    }

    static DbQueryResult queryConn(DbConnection conn, String sql, ArrayList<Object> params) {
        DbQueryResult r = (DbQueryResult) FutureBlocker.blockingGet(conn.query(sql, params));
        if (r == null) {
            r = new DbQueryResult();
        }
        if (r.rows == null) {
            r.rows = new ArrayList<Row>();
        }
        return r;
    }

    static Object queryOneConn(DbConnection conn, String sql, ArrayList<Object> params, RowMapper mapper) {
        return FutureBlocker.blockingGet(conn.queryOne(sql, params, mapper));
    }

    static Object queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        int attempt = 0;
        while (attempt < 3) {
            try {
                return FutureBlocker.blockingGet(Erp.db.queryOne(sql, params, mapper));
            } catch (Exception ex) {
                String em = "";
                if (ex.getMessage() != null) {
                    em = ex.getMessage();
                }
                if (attempt < 2 && (em.length() == 0 || em.indexOf("prepare returned null") >= 0
                        || em.indexOf("not an error") >= 0 || em.indexOf("database is locked") >= 0)) {
                    reconnect();
                    attempt = attempt + 1;
                } else {
                    throw ex;
                }
            }
        }
        return null;
    }

    static DbQueryResult query(String sql, ArrayList<Object> params) {
        int attempt = 0;
        while (attempt < 3) {
            try {
                DbQueryResult r = (DbQueryResult) FutureBlocker.blockingGet(Erp.db.query(sql, params));
                if (r == null) {
                    r = new DbQueryResult();
                    r.rows = new ArrayList<Row>();
                }
                if (r.rows == null) {
                    r.rows = new ArrayList<Row>();
                }
                return r;
            } catch (Exception ex) {
                String em = "";
                if (ex.getMessage() != null) {
                    em = ex.getMessage();
                }
                if (attempt < 2 && (em.length() == 0 || em.indexOf("prepare returned null") >= 0
                        || em.indexOf("not an error") >= 0 || em.indexOf("database is locked") >= 0)) {
                    reconnect();
                    attempt = attempt + 1;
                } else {
                    throw ex;
                }
            }
        }
        DbQueryResult empty = new DbQueryResult();
        empty.rows = new ArrayList<Row>();
        return empty;
    }

    static long insertReturningIdConn(DbConnection conn, String sql, ArrayList<Object> params) {
        if (Erp.dialect.equals("sqlite")) {
            execConn(conn, sql, params);
            Object idObj = queryOneConn(conn, "SELECT last_insert_rowid()", new ArrayList<Object>(),
                    new LongMapper());
            return ErpNum.asLong(idObj);
        }
        String pgSql = sql + " RETURNING id";
        DbQueryResult r2 = queryConn(conn, pgSql, params);
        if (r2.rows.size() == 0) {
            return 0L;
        }
        return r2.rows.get(0).getLong(0);
    }

    static long nextDocNo(long companyId, String seqName) {
        /* Prefer conn path inside transactions; this standalone path still uses atomic bump. */
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(seqName);
        Object exists = queryOne(
                "SELECT next_val FROM number_sequences WHERE company_id=? AND seq_name=?",
                p, new LongColMapper());
        if (exists == null) {
            try {
                ArrayList<Object> ins = new ArrayList<Object>();
                ins.add(CryptoParse.longToString(companyId));
                ins.add(seqName);
                ins.add("1");
                exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)",
                        ins);
            } catch (Exception ignored) {
            }
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(CryptoParse.longToString(companyId));
        up.add(seqName);
        exec("UPDATE number_sequences SET next_val = next_val + 1 WHERE company_id=? AND seq_name=?", up);
        Object row = queryOne(
                "SELECT next_val - 1 FROM number_sequences WHERE company_id=? AND seq_name=?",
                p, new LongColMapper());
        return ErpNum.asLong(row);
    }

    /**
     * Allocate next document sequence value under the caller's transaction.
     * UPDATE increments first (row lock), then read next_val-1 — avoids SELECT/UPDATE races
     * that issued duplicate doc numbers under concurrent HTTP creates.
     */
    static long nextDocNoConn(DbConnection conn, long companyId, String seqName) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(seqName);
        Object exists = queryOneConn(conn,
                "SELECT next_val FROM number_sequences WHERE company_id=? AND seq_name=?",
                p, new LongColMapper());
        if (exists == null) {
            try {
                ArrayList<Object> ins = new ArrayList<Object>();
                ins.add(CryptoParse.longToString(companyId));
                ins.add(seqName);
                ins.add("1");
                execConn(conn,
                        "INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)",
                        ins);
            } catch (Exception ignored) {
                /* peer txn inserted the same sequence row */
            }
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(CryptoParse.longToString(companyId));
        up.add(seqName);
        execConn(conn,
                "UPDATE number_sequences SET next_val = next_val + 1 WHERE company_id=? AND seq_name=?",
                up);
        Object row = queryOneConn(conn,
                "SELECT next_val - 1 FROM number_sequences WHERE company_id=? AND seq_name=?",
                p, new LongColMapper());
        long n = ErpNum.asLong(row);
        if (n <= 0L) {
            throw new TransactionException("number sequence failed: " + seqName);
        }
        return n;
    }
}

class ErpNum {
    static long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0L;
        }
        return WebUtil.parseLongOr(s, 0L);
    }

    static long asLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof String) {
            return parseLong((String) v);
        }
        return 0L;
    }

    static int parseInt(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        return WebUtil.parseIntOr(s, 0);
    }
}

class LongColMapper implements RowMapper {
    public Object map(Row row) {
        return CryptoParse.longToString(row.getLong(0));
    }
}

class LongMapper implements RowMapper {
    public Object map(Row row) {
        return CryptoParse.longToString(row.getLong(0));
    }
}

class IntMapper implements RowMapper {
    public Object map(Row row) {
        return String.valueOf(row.getInt(0));
    }
}

class StringMapper implements RowMapper {
    public Object map(Row row) {
        return row.getString(0);
    }
}
