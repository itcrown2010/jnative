/**
 * In-app Chinese tutorials for pilot flows (content UTF-8 via JSON unicode escapes).
 * Practice materials: FG-PRACTICE-A / WH-PRACTICE.
 */
class ErpHelp {
    static String tutorialsJson() {
        return "{\"tutorials\":["
                + tutorial("T01", "setup formal book", "admin", "System / Wizard",
                        "Create FORMAL company with code/name/timezone.",
                        "companies.book_kind=FORMAL; no demo business rows.",
                        "Do not treat DEMO book as formal.")
                + ","
                + tutorial("T02", "setup wizard", "admin", "Wizard steps",
                        "currency -> warehouse -> numbering -> user -> master -> opening -> validate.",
                        "setup_wizard_state.completed=1; companies.setup_completed=1.",
                        "Foundation locked after business docs exist.")
                + ","
                + tutorial("T03", "CSV import", "admin", "Import Export",
                        "Download template; UTF-8 CSV preview; transactional commit.",
                        "import_batches audited; duplicate codes skipped.",
                        "Do not re-import formula-injected cells.")
                + ","
                + tutorial("T04", "opening stock", "admin/finance", "Inventory / Opening",
                        "Draft opening stock then post. Never edit balances directly.",
                        "inventory_balances and OS transactions updated.",
                        "Idempotency-Key required.")
                + ","
                + tutorial("T05", "first purchase/sales", "purchase/sales", "PO/GR and SO/DL",
                        "GR 10 then DL 3 on real API (no DemoStateStore).",
                        "stock 20 -> 30 -> 27.",
                        "Unapproved orders cannot post.")
                + ","
                + tutorial("T06", "reverse docs", "admin", "GR/DL reverse",
                        "Reverse posted DL with reason; second reverse rejected.",
                        "status=REVERSED; stock restored; audit REVERSE.",
                        "Downstream AR/AP must be cleared first.")
                + ","
                + tutorial("T07", "backup restore", "admin", "ops scripts",
                        "Run backup-restore.sh; upgrade runs pg_dump first.",
                        "Independent DB restore counts match.",
                        "Do not drop-and-recreate as upgrade.")
                + ","
                + tutorial("T08", "demo practice", "any", "DEMO book",
                        "Use FG-PRACTICE-A / WH-PRACTICE; demo reset allowed only in DEMO.",
                        "FORMAL reset returns 403.",
                        "Demo seed upgrade must not wipe user docs.")
                + "]}";
    }

    static String tutorial(String id, String title, String role, String menu, String input, String expect,
            String commonError) {
        return "{\"id\":" + ErpJson.str(id)
                + ",\"title\":" + ErpJson.str(title)
                + ",\"role\":" + ErpJson.str(role)
                + ",\"menu\":" + ErpJson.str(menu)
                + ",\"input\":" + ErpJson.str(input)
                + ",\"expect\":" + ErpJson.str(expect)
                + ",\"commonError\":" + ErpJson.str(commonError) + "}";
    }
}
