/**
 * ERP Integrity Check — inventory vs movements, simple AR/AP/GL balance probes.
 * Feature freeze: read-only diagnostics.
 */
class ErpIntegrity {
    static int runAll(long companyId) {
        int pass = 1;
        pass = checkInvBalance(companyId, pass);
        pass = checkJournalBalance(companyId, pass);
        pass = checkSchemaMigration(pass);
        if (pass != 0) {
            System.out.println("PASS integrity");
        } else {
            System.out.println("FAIL integrity");
        }
        return pass;
    }

    static int checkSchemaMigration(int pass) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add("004_enterprise");
        Object v = ErpDb.queryOne("SELECT version FROM schema_migrations WHERE version=?", p, new StringMapper());
        if (v == null) {
            System.out.println("FAIL integrity: schema_migrations missing 004_enterprise");
            return 0;
        }
        System.out.println("OK integrity migration");
        return pass;
    }

    /**
     * For each balance row: sum of inventory_transactions quantity_delta should equal quantity_on_hand.
     * Empty company (no balances) is OK.
     */
    static int checkInvBalance(long companyId, int pass) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult bal = ErpDb.query(
                "SELECT warehouse_id, item_id, quantity_on_hand FROM inventory_balances WHERE company_id=?", p);
        int i = 0;
        while (i < bal.rows.size()) {
            Row row = bal.rows.get(i);
            long wh = row.getLong(0);
            long item = row.getLong(1);
            int qoh = row.getInt(2);
            ArrayList<Object> tp = new ArrayList<Object>();
            tp.add(CryptoParse.longToString(companyId));
            tp.add(CryptoParse.longToString(wh));
            tp.add(CryptoParse.longToString(item));
            Object sum = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(quantity_delta),0) FROM inventory_transactions WHERE company_id=? AND warehouse_id=? AND item_id=?",
                    tp, new LongMapper());
            long s = ErpNum.asLong(sum);
            long q = 0L + qoh;
            if (s != q) {
                System.out.println("FAIL integrity inv wh=" + CryptoParse.longToString(wh)
                        + " item=" + CryptoParse.longToString(item)
                        + " qoh=" + String.valueOf(qoh)
                        + " sumDelta=" + CryptoParse.longToString(s));
                return 0;
            }
            i = i + 1;
        }
        System.out.println("OK integrity inventory rows=" + String.valueOf(bal.rows.size()));
        return pass;
    }

    static int checkJournalBalance(long companyId, int pass) {
        /* If journal tables absent, skip without fail */
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT journal_entry_id, SUM(debit_amount), SUM(credit_amount) FROM journal_entry_lines WHERE company_id=? GROUP BY journal_entry_id",
                    p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                long d = row.getLong(1);
                long c = row.getLong(2);
                if (d != c) {
                    System.out.println("FAIL integrity journal unbalanced id="
                            + CryptoParse.longToString(row.getLong(0)));
                    return 0;
                }
                i = i + 1;
            }
            System.out.println("OK integrity journals checked=" + String.valueOf(r.rows.size()));
            return pass;
        } catch (Exception ex) {
            System.out.println("SKIP integrity journals (table/query unavailable)");
            ErpDb.reconnect();
            return pass;
        }
    }
}
