-- Manufacturing 1.3 (PostgreSQL). Quantities integer; money integer units; scrap_rate_bp = basis points (100=1%).

CREATE TABLE IF NOT EXISTS bills_of_material (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    parent_item_id BIGINT NOT NULL,
    bom_code TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    base_quantity INT NOT NULL DEFAULT 1,
    status TEXT NOT NULL,
    effective_from BIGINT NOT NULL DEFAULT 0,
    effective_to BIGINT NOT NULL DEFAULT 4102444800000,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, bom_code, version)
);

CREATE TABLE IF NOT EXISTS bom_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    bom_id BIGINT NOT NULL REFERENCES bills_of_material(id),
    component_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    uom TEXT NOT NULL DEFAULT 'PCS',
    scrap_rate_bp INT NOT NULL DEFAULT 0,
    warehouse_id BIGINT,
    sequence INT NOT NULL DEFAULT 10
);

CREATE TABLE IF NOT EXISTS production_orders (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    order_number TEXT NOT NULL,
    item_id BIGINT NOT NULL,
    bom_id BIGINT NOT NULL,
    bom_version INT NOT NULL,
    planned_quantity INT NOT NULL,
    completed_quantity INT NOT NULL DEFAULT 0,
    scrapped_quantity INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    planned_start_date BIGINT NOT NULL DEFAULT 0,
    planned_end_date BIGINT NOT NULL DEFAULT 0,
    actual_start_date BIGINT NOT NULL DEFAULT 0,
    actual_end_date BIGINT NOT NULL DEFAULT 0,
    over_issue_tolerance_bp INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, order_number)
);

CREATE TABLE IF NOT EXISTS production_order_components (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    production_order_id BIGINT NOT NULL REFERENCES production_orders(id),
    component_item_id BIGINT NOT NULL,
    required_quantity INT NOT NULL,
    issued_quantity INT NOT NULL DEFAULT 0,
    returned_quantity INT NOT NULL DEFAULT 0,
    warehouse_id BIGINT NOT NULL,
    unit_cost_amount BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_wip (
    production_order_id BIGINT PRIMARY KEY REFERENCES production_orders(id),
    company_id BIGINT NOT NULL,
    material_cost BIGINT NOT NULL DEFAULT 0,
    labor_cost BIGINT NOT NULL DEFAULT 0,
    overhead_cost BIGINT NOT NULL DEFAULT 0,
    transferred_cost BIGINT NOT NULL DEFAULT 0,
    remaining_cost BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_issues (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    production_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    posting_date BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, idempotency_key),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS production_issue_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL REFERENCES production_issues(id),
    component_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_cost_amount BIGINT NOT NULL DEFAULT 0,
    amount BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_returns (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    production_order_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    posting_date BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS production_return_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    return_id BIGINT NOT NULL REFERENCES production_returns(id),
    component_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_cost_amount BIGINT NOT NULL DEFAULT 0,
    amount BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production_receipts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    production_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_cost_amount BIGINT NOT NULL DEFAULT 0,
    total_cost BIGINT NOT NULL DEFAULT 0,
    posting_date BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, idempotency_key),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS production_scraps (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    production_order_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    cost_amount BIGINT NOT NULL DEFAULT 0,
    cost_policy TEXT NOT NULL DEFAULT 'ABSORB_TO_COMPLETED',
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS mrp_runs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    run_code TEXT NOT NULL,
    planning_start_date BIGINT NOT NULL,
    planning_end_date BIGINT NOT NULL,
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, run_code)
);

CREATE TABLE IF NOT EXISTS mrp_requirements (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    mrp_run_id BIGINT NOT NULL REFERENCES mrp_runs(id),
    item_id BIGINT NOT NULL,
    bucket_date BIGINT NOT NULL,
    gross_requirement INT NOT NULL DEFAULT 0,
    scheduled_receipts INT NOT NULL DEFAULT 0,
    projected_available INT NOT NULL DEFAULT 0,
    net_requirement INT NOT NULL DEFAULT 0,
    planned_order_receipt INT NOT NULL DEFAULT 0,
    planned_order_release INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS mrp_suggestions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    mrp_run_id BIGINT NOT NULL REFERENCES mrp_runs(id),
    suggestion_kind TEXT NOT NULL,
    item_id BIGINT NOT NULL,
    required_quantity INT NOT NULL,
    required_date BIGINT NOT NULL,
    suggested_order_date BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OPEN',
    converted_doc_id BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bom_parent ON bills_of_material(company_id, parent_item_id, status);
CREATE INDEX IF NOT EXISTS idx_po_mfg_status ON production_orders(company_id, status);
CREATE INDEX IF NOT EXISTS idx_mrp_req ON mrp_requirements(mrp_run_id, item_id, bucket_date);
