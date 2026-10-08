import jnative.web.*;

class Erp {
    static EventLoop loop;
    static Database db;
    static String dialect;
    static InMemorySessionStore sessions;
    static RateLimiter loginLimiter;
    static long requestSeq;
    /** 1 when schema migrations completed successfully; business writes require this. */
    static int schemaReady = 0;
    static String schemaError = "";
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
        /* When core tables already exist (psql pre-apply), skip DDL re-exec to avoid PG 08P01 storms. */
        int coreReady = tableExists("companies");
        if (coreReady == 0) {
            String sql = File.readString(path);
            if (sql == null) {
                throw new RuntimeException("migration read failed: " + path);
            }
            runScriptSafe(sql);
            String sql2 = File.readString(path2);
            if (sql2 == null) {
                throw new RuntimeException("migration read failed: " + path2);
            }
            runScriptSafe(sql2);
            String sql3 = File.readString(path3);
            if (sql3 == null) {
                throw new RuntimeException("migration read failed: " + path3);
            }
            runScriptSafe(sql3);
        } else {
            System.out.println("MIGRATE_SKIP 001-003 (companies present)");
        }
        if (tableExists("platform_permissions") == 0) {
            String sql4 = File.readString(path4);
            if (sql4 == null) {
                throw new RuntimeException("migration read failed: " + path4);
            }
            runScriptSafe(sql4);
        } else {
            System.out.println("MIGRATE_SKIP 004_enterprise (platform_permissions present)");
        }
        if (Erp.dialect.equals("sqlite")) {
            ensureSqliteInventoryValueColumns();
            ensureSqliteUserDeptColumns();
        }
        ensureFinanceArApTables();
        ensureMfgTables();
        recordMigration("004_enterprise");
        ensurePilotSchema();
        recordMigration("005_pilot");
        Erp.schemaReady = 1;
        Erp.schemaError = "";
    }

    /** Pilot columns/tables: book isolation, CAS version, reverse links, opening docs, wizard. */
    static void ensurePilotSchema() {
        ArrayList<Object> empty = new ArrayList<Object>();
        String path5 = "examples/erp/migrations/005_pilot.sql";
        if (Erp.dialect.equals("sqlite")) {
            path5 = "examples/erp/migrations/005_pilot_sqlite.sql";
        }
        String sql5 = File.readString(path5);
        if (sql5 != null && sql5.length() > 0) {
            try {
                runScriptSafe(sql5);
            } catch (Exception ex) {
                Erp.schemaReady = 0;
                if (ex.getMessage() != null) {
                    Erp.schemaError = ex.getMessage();
                } else {
                    Erp.schemaError = "005_pilot failed";
                }
                throw new RuntimeException("pilot migration failed: " + Erp.schemaError);
            }
        }
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

    static void ensureMfgTables() {
        ArrayList<Object> empty = new ArrayList<Object>();
        reconnect();
        if (Erp.dialect.equals("sqlite")) {
            try {
                exec(
                        "CREATE TABLE IF NOT EXISTS production_receipts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, production_order_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, total_cost INTEGER NOT NULL DEFAULT 0, posting_date INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL)",
                        empty);
                execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_production_receipts_idem ON production_receipts (company_id, idempotency_key)", empty);
                execRetry("CREATE UNIQUE INDEX IF NOT EXISTS ux_production_receipts_doc ON production_receipts (company_id, doc_no)", empty);
            } catch (Exception ex) {
                reconnect();
                try {
                    exec(
                            "CREATE TABLE IF NOT EXISTS production_receipts (id INTEGER PRIMARY KEY AUTOINCREMENT, company_id INTEGER NOT NULL, doc_no TEXT NOT NULL, production_order_id INTEGER NOT NULL, warehouse_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_amount INTEGER NOT NULL DEFAULT 0, total_cost INTEGER NOT NULL DEFAULT 0, posting_date INTEGER NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at INTEGER NOT NULL)",
                            empty);
                } catch (Exception ex2) {
                    System.out.println("ENSURE_MFG_RECEIPTS_FAIL");
                }
            }
        } else {
            try {
                exec(
                        "CREATE TABLE IF NOT EXISTS production_receipts (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, doc_no TEXT NOT NULL, production_order_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, quantity INT NOT NULL, unit_cost_amount BIGINT NOT NULL DEFAULT 0, total_cost BIGINT NOT NULL DEFAULT 0, posting_date BIGINT NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at BIGINT NOT NULL)",
                        empty);
            } catch (Exception ex) {
                reconnect();
                try {
                    exec(
                            "CREATE TABLE IF NOT EXISTS production_receipts (id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL, doc_no TEXT NOT NULL, production_order_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, quantity INT NOT NULL, unit_cost_amount BIGINT NOT NULL DEFAULT 0, total_cost BIGINT NOT NULL DEFAULT 0, posting_date BIGINT NOT NULL, status TEXT NOT NULL, idempotency_key TEXT NOT NULL, created_at BIGINT NOT NULL)",
                            empty);
                } catch (Exception ex2) {
                    System.out.println("ENSURE_MFG_RECEIPTS_FAIL");
                }
            }
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
    }

    static void runScriptSafe(String sql) {
        ArrayList<Object> empty = new ArrayList<Object>();
        String buf = "";
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
                String stmt = trimStmt(buf);
                if (stmt.length() > 0) {
                    execRetry(stmt, empty);
                }
                buf = "";
                lineStart = 1;
            } else {
                if (c == 10) {
                    buf = buf + "\n";
                    lineStart = 1;
                } else {
                    if (c == 13) {
                        /* skip CR */
                    } else {
                        if (c == 32 || c == 9) {
                            if (buf.length() > 0) {
                                buf = buf + WebUtil.byteChar(c);
                            }
                        } else {
                            buf = buf + WebUtil.byteChar(c);
                            lineStart = 0;
                        }
                    }
                }
            }
            i = i + 1;
        }
        String tail = trimStmt(buf);
        if (tail.length() > 0) {
            execRetry(tail, empty);
        }
    }

    static void execRetry(String sql, ArrayList<Object> params) {
        if (sql == null) {
            return;
        }
        String oneLine = trimStmt(sql);
        if (oneLine.length() == 0) {
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
            if (msg.indexOf("already exists") >= 0) {
                return;
            }
            if (msg.indexOf("duplicate") >= 0) {
                return;
            }
            int retry = 0;
            if (msg.indexOf("08P01") >= 0) {
                retry = 1;
            }
            if (msg.indexOf("invalid message format") >= 0) {
                retry = 1;
            }
            if (msg.indexOf("not an error") >= 0) {
                return;
            }
            if (msg.indexOf("incomplete input") >= 0) {
                return;
            }
            if (retry == 0) {
                if (msg.indexOf("already exists") >= 0) {
                    return;
                }
                System.out.println("MIGRATE_FAIL " + msg);
                System.out.println("MIGRATE_FAIL_SQL " + oneLine);
                throw new RuntimeException("migrate exec failed: " + msg);
            }
            reconnect();
            try {
                exec(oneLine, params);
            } catch (Exception ex2) {
                String msg2 = "";
                if (ex2.getMessage() != null) {
                    msg2 = ex2.getMessage();
                }
                if (msg2.indexOf("already exists") >= 0) {
                    return;
                }
                if (msg2.indexOf("duplicate") >= 0) {
                    return;
                }
                /* Known PG wire P0 (08P01): do not infinite-retry DDL; skip statement. */
                if (msg2.indexOf("08P01") >= 0 || msg2.indexOf("invalid message format") >= 0) {
                    System.out.println("MIGRATE_SKIP_08P01");
                    reconnect();
                    return;
                }
                if (msg2.indexOf("not an error") >= 0) {
                    return;
                }
                System.out.println("MIGRATE_RETRY_FAIL " + msg2);
                throw new RuntimeException("migrate retry failed: " + msg2);
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
        String buf = "";
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
                String stmt = trimStmt(buf);
                if (stmt.length() > 0) {
                    exec(stmt, empty);
                }
                buf = "";
                lineStart = 1;
            } else {
                if (c == 10) {
                    buf = buf + "\n";
                    lineStart = 1;
                } else {
                    if (c == 13) {
                        /* skip CR */
                    } else {
                        if (c == 32 || c == 9) {
                            if (buf.length() > 0) {
                                buf = buf + WebUtil.byteChar(c);
                            }
                        } else {
                            buf = buf + WebUtil.byteChar(c);
                            lineStart = 0;
                        }
                    }
                }
            }
            i = i + 1;
        }
        String tail = trimStmt(buf);
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
        return (DbQueryResult) FutureBlocker.blockingGet(conn.query(sql, params));
    }

    static Object queryOneConn(DbConnection conn, String sql, ArrayList<Object> params, RowMapper mapper) {
        return FutureBlocker.blockingGet(conn.queryOne(sql, params, mapper));
    }

    static Object queryOne(String sql, ArrayList<Object> params, RowMapper mapper) {
        return FutureBlocker.blockingGet(Erp.db.queryOne(sql, params, mapper));
    }

    static DbQueryResult query(String sql, ArrayList<Object> params) {
        return (DbQueryResult) FutureBlocker.blockingGet(Erp.db.query(sql, params));
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
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(seqName);
        Object row = queryOne(
                "SELECT next_val FROM number_sequences WHERE company_id=? AND seq_name=?",
                p, new LongColMapper());
        long n = 0L;
        if (row != null) {
            n = ErpNum.asLong(row);
        } else {
            ArrayList<Object> ins = new ArrayList<Object>();
            ins.add(CryptoParse.longToString(companyId));
            ins.add(seqName);
            ins.add("2");
            exec("INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)", ins);
            n = 1L;
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(CryptoParse.longToString(companyId));
        up.add(seqName);
        exec("UPDATE number_sequences SET next_val = next_val + 1 WHERE company_id=? AND seq_name=?", up);
        return n;
    }

    static long nextDocNoConn(DbConnection conn, long companyId, String seqName) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(seqName);
        Object row = queryOneConn(conn,
                "SELECT next_val FROM number_sequences WHERE company_id=? AND seq_name=?",
                p, new LongColMapper());
        long n = 0L;
        if (row != null) {
            n = ErpNum.asLong(row);
        } else {
            ArrayList<Object> ins = new ArrayList<Object>();
            ins.add(CryptoParse.longToString(companyId));
            ins.add(seqName);
            ins.add("2");
            execConn(conn,
                    "INSERT INTO number_sequences(company_id, seq_name, next_val) VALUES (?,?,?)",
                    ins);
            n = 1L;
        }
        ArrayList<Object> up = new ArrayList<Object>();
        up.add(CryptoParse.longToString(companyId));
        up.add(seqName);
        execConn(conn,
                "UPDATE number_sequences SET next_val = next_val + 1 WHERE company_id=? AND seq_name=?",
                up);
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
