/**
 * Book isolation: DEMO vs FORMAL. Demo seed never writes into FORMAL books.
 * @author 张晓达
 */
class BookKind {
    static String DEMO = "DEMO";
    static String FORMAL = "FORMAL";
}

class ErpBooks {
    static int DEMO_SEED_VERSION = 1;

    static String bookKind(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT book_kind FROM companies WHERE id=?", p, new StringMapper());
        if (v == null) {
            return BookKind.FORMAL;
        }
        String k = (String) v;
        if (k == null || k.length() == 0) {
            return BookKind.FORMAL;
        }
        return k;
    }

    static int isDemoBook(long companyId) {
        if (bookKind(companyId).equals(BookKind.DEMO)) {
            return 1;
        }
        return 0;
    }

    static int isFormalBook(long companyId) {
        if (isDemoBook(companyId) != 0) {
            return 0;
        }
        return 1;
    }

    static void requireDemoBookOrThrow(long companyId, String action) {
        ErpDemoGuard.requireDemoModeOrThrow(action);
        if (isDemoBook(companyId) == 0) {
            throw new TransactionException("FORBIDDEN:FORMAL_BOOK:" + action);
        }
    }

    static void setBookKind(long companyId, String kind) {
        ErpDb.exec(
                "UPDATE companies SET book_kind='" + kind + "' WHERE id="
                        + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
    }

    static int demoSeedVersion(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT seed_version FROM demo_seed_meta WHERE company_id=?", p, new LongMapper());
        return ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(v)));
    }

    static void recordDemoSeed(long companyId, int version) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object existing = ErpDb.queryOne("SELECT company_id FROM demo_seed_meta WHERE company_id=?", p,
                new LongMapper());
        if (existing == null) {
            ErpDb.exec(
                    "INSERT INTO demo_seed_meta(company_id, seed_version, applied_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(version) + ","
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        } else {
            ErpDb.exec(
                    "UPDATE demo_seed_meta SET seed_version=" + CryptoParse.longToString(version)
                            + ", applied_at=" + CryptoParse.longToString(now)
                            + " WHERE company_id=" + CryptoParse.longToString(companyId),
                    new ArrayList<Object>());
        }
    }

    /**
     * Upgrade demo seed content without wiping user-added business docs.
     * Only fills missing practice SKU/warehouse if absent.
     */
    static void upgradeDemoSeedIfNeeded(long companyId) {
        if (isDemoBook(companyId) == 0) {
            return;
        }
        int cur = demoSeedVersion(companyId);
        if (cur >= DEMO_SEED_VERSION) {
            return;
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("FG-PRACTICE-A");
        Object item = ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper());
        if (item == null) {
            ErpDb.exec(
                    "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES ("
                            + CryptoParse.longToString(companyId)
                            + ",'FG-PRACTICE-A','练习商品 A',8000,5000,"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        }
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH-PRACTICE");
        Object wh = ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper());
        if (wh == null) {
            ErpDb.exec(
                    "INSERT INTO warehouses(company_id, code, name, created_at) VALUES ("
                            + CryptoParse.longToString(companyId)
                            + ",'WH-PRACTICE','练习仓',"
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        }
        recordDemoSeed(companyId, DEMO_SEED_VERSION);
    }

    /** Wipe demo transactional data and re-seed practice masters. FORMAL -> throw. */
    static void resetDemoBook(long companyId) {
        requireDemoBookOrThrow(companyId, "RESET_DEMO");
        ArrayList<Object> empty = new ArrayList<Object>();
        String cid = CryptoParse.longToString(companyId);
        ErpDb.exec("DELETE FROM idempotency_keys WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM document_references WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM inventory_transactions WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM inventory_balances WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM delivery_lines WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM deliveries WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM goods_receipt_lines WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM goods_receipts WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM sales_order_lines WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM sales_orders WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM purchase_order_lines WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM purchase_orders WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM opening_stock_lines WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM opening_stock_docs WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM opening_ar_docs WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM opening_ap_docs WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM business_partners WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM items WHERE company_id=" + cid, empty);
        ErpDb.exec("DELETE FROM warehouses WHERE company_id=" + cid, empty);
        long now = AsyncClockNative.wallMillis();
        ErpSeed.seedDemoBusiness(companyId, now);
        recordDemoSeed(companyId, DEMO_SEED_VERSION);
        ErpDb.exec(
                "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                        + cid + ",0,'DEMO_RESET','COMPANY'," + cid + ",'demo book reset',"
                        + CryptoParse.longToString(now) + ")",
                empty);
    }
}
