-- Manufacturing 1.3 (SQLite mirror).

CREATE TABLE IF NOT EXISTS bills_of_material (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    parent_item_id INTEGER NOT NULL,
    bom_code TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    base_quantity INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL,
    effective_from INTEGER NOT NULL DEFAULT 0,
    effective_to INTEGER NOT NULL DEFAULT 4102444800000,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE (company_id, bom_code, version)
);

CREATE TABLE IF NOT EXISTS bom_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    bom_id INTEGER NOT NULL,
    component_item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    uom TEXT NOT NULL DEFAULT 'PCS',
    scrap_rate_bp INTEGER NOT NULL DEFAULT 0,
    warehouse_id INTEGER,
    sequence INTEGER NOT NULL DEFAULT 10
);

CREATE TABLE IF NOT EXISTS production_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    order_number TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    bom_id INTEGER NOT NULL,
    bom_version INTEGER NOT NULL,
    planned_quantity INTEGER NOT NULL,
    completed_quantity INTEGER NOT NULL DEFAULT 0,
    scrapped_quantity INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    warehouse_id INTEGER NOT NULL,
    planned_start_date INTEGER NOT NULL DEFAULT 0,
    planned_end_date INTEGER NOT NULL DEFAULT 0,
    actual_start_date INTEGER NOT NULL DEFAULT 0,
    actual_end_date INTEGER NOT NULL DEFAULT 0,
    over_issue_tolerance_bp INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE (company_id, order_number)
);

CREATE TABLE IF NOT EXISTS production_order_components (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    production_order_id INTEGER NOT NULL,
    component_item_id INTEGER NOT NULL,
    required_quantity INTEGER NOT NULL,
    issued_quantity INTEGER NOT NULL DEFAULT 0,
    returned_quantity INTEGER NOT NULL DEFAULT 0,
    warehouse_id INTEGER NOT NULL,
    unit_cost_amount INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_wip (
    production_order_id INTEGER PRIMARY KEY,
    company_id INTEGER NOT NULL,
    material_cost INTEGER NOT NULL DEFAULT 0,
    labor_cost INTEGER NOT NULL DEFAULT 0,
    overhead_cost INTEGER NOT NULL DEFAULT 0,
    transferred_cost INTEGER NOT NULL DEFAULT 0,
    remaining_cost INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_issues (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    production_order_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    posting_date INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, idempotency_key),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS production_issue_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    issue_id INTEGER NOT NULL,
    component_item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost_amount INTEGER NOT NULL DEFAULT 0,
    amount INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_returns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    production_order_id INTEGER NOT NULL,
    issue_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    posting_date INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS production_return_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    return_id INTEGER NOT NULL,
    component_item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost_amount INTEGER NOT NULL DEFAULT 0,
    amount INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_receipts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    production_order_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost_amount INTEGER NOT NULL DEFAULT 0,
    total_cost INTEGER NOT NULL DEFAULT 0,
    posting_date INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, idempotency_key),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS production_scraps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    production_order_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    cost_amount INTEGER NOT NULL DEFAULT 0,
    cost_policy TEXT NOT NULL DEFAULT 'ABSORB_TO_COMPLETED',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS mrp_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    run_code TEXT NOT NULL,
    planning_start_date INTEGER NOT NULL,
    planning_end_date INTEGER NOT NULL,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, run_code)
);

CREATE TABLE IF NOT EXISTS mrp_requirements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    mrp_run_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    bucket_date INTEGER NOT NULL,
    gross_requirement INTEGER NOT NULL DEFAULT 0,
    scheduled_receipts INTEGER NOT NULL DEFAULT 0,
    projected_available INTEGER NOT NULL DEFAULT 0,
    net_requirement INTEGER NOT NULL DEFAULT 0,
    planned_order_receipt INTEGER NOT NULL DEFAULT 0,
    planned_order_release INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS mrp_suggestions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    mrp_run_id INTEGER NOT NULL,
    suggestion_kind TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    required_quantity INTEGER NOT NULL,
    required_date INTEGER NOT NULL,
    suggested_order_date INTEGER NOT NULL,
    supplier_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OPEN',
    converted_doc_id INTEGER NOT NULL DEFAULT 0
);
