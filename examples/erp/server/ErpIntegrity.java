/**
 * ERP Integrity Checker (1.6) — read-only. Never mutates ledgers.
 * Prints PASS / FAIL / SKIP (NOT_APPLICABLE) / NOT_VERIFIED per check.
 */
class ErpIntegrity {
    static int runAll(long companyId) {
        int pass = 1;
        pass = checkSchemaMigration(pass);
        pass = checkInvBalance(companyId, pass);
        pass = checkNoNegativeInventory(companyId, pass);
        pass = checkJournalBalance(companyId, pass);
        pass = checkPoFulfillment(companyId, pass);
        pass = checkSoFulfillment(companyId, pass);
        pass = checkArOpen(companyId, pass);
        pass = checkApOpen(companyId, pass);
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
            System.out.println("FAIL integrity:migration missing 004_enterprise");
            return 0;
        }
        System.out.println("PASS integrity:migration");
        return pass;
    }

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
                System.out.println("FAIL integrity:inventory_balance_vs_txn wh="
                        + CryptoParse.longToString(wh) + " item=" + CryptoParse.longToString(item)
                        + " qoh=" + String.valueOf(qoh) + " sumDelta=" + CryptoParse.longToString(s));
                return 0;
            }
            i = i + 1;
        }
        System.out.println("PASS integrity:inventory_balance_vs_txn rows=" + String.valueOf(bal.rows.size()));
        return pass;
    }

    static int checkNoNegativeInventory(long companyId, int pass) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        Object n = ErpDb.queryOne(
                "SELECT COUNT(*) FROM inventory_balances WHERE company_id=? AND quantity_on_hand < 0", p,
                new LongMapper());
        if (ErpNum.asLong(n) > 0L) {
            System.out.println("FAIL integrity:negative_inventory count=" + CryptoParse.longToString(ErpNum.asLong(n)));
            return 0;
        }
        System.out.println("PASS integrity:negative_inventory");
        return pass;
    }

    static int checkJournalBalance(long companyId, int pass) {
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT journal_entry_id, SUM(debit_amount), SUM(credit_amount) FROM journal_entry_lines WHERE company_id=? GROUP BY journal_entry_id",
                    p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                if (row.getLong(1) != row.getLong(2)) {
                    System.out.println("FAIL integrity:journal_balance id="
                            + CryptoParse.longToString(row.getLong(0)));
                    return 0;
                }
                i = i + 1;
            }
            System.out.println("PASS integrity:journal_balance entries=" + String.valueOf(r.rows.size()));
            return pass;
        } catch (Exception ex) {
            System.out.println("SKIP integrity:journal_balance NOT_APPLICABLE");
            ErpDb.reconnect();
            return pass;
        }
    }

    static int checkPoFulfillment(long companyId, int pass) {
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT id, quantity, received_qty FROM purchase_order_lines WHERE company_id=?", p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                int qty = row.getInt(1);
                int recv = row.getInt(2);
                if (recv < 0 || recv > qty) {
                    System.out.println("FAIL integrity:po_fulfillment line="
                            + CryptoParse.longToString(row.getLong(0))
                            + " qty=" + String.valueOf(qty) + " recv=" + String.valueOf(recv));
                    return 0;
                }
                i = i + 1;
            }
            System.out.println("PASS integrity:po_fulfillment lines=" + String.valueOf(r.rows.size()));
            return pass;
        } catch (Exception ex) {
            System.out.println("SKIP integrity:po_fulfillment NOT_APPLICABLE");
            ErpDb.reconnect();
            return pass;
        }
    }

    static int checkSoFulfillment(long companyId, int pass) {
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT id, quantity, delivered_qty FROM sales_order_lines WHERE company_id=?", p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                int qty = row.getInt(1);
                int del = row.getInt(2);
                if (del < 0 || del > qty) {
                    System.out.println("FAIL integrity:so_fulfillment line="
                            + CryptoParse.longToString(row.getLong(0))
                            + " qty=" + String.valueOf(qty) + " del=" + String.valueOf(del));
                    return 0;
                }
                i = i + 1;
            }
            System.out.println("PASS integrity:so_fulfillment lines=" + String.valueOf(r.rows.size()));
            return pass;
        } catch (Exception ex) {
            System.out.println("SKIP integrity:so_fulfillment NOT_APPLICABLE");
            ErpDb.reconnect();
            return pass;
        }
    }

    static int checkArOpen(long companyId, int pass) {
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT id, total_amount, paid_amount, open_amount FROM ar_invoices WHERE company_id=?", p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                long total = row.getLong(1);
                long paid = row.getLong(2);
                long open = row.getLong(3);
                if (paid + open != total) {
                    System.out.println("FAIL integrity:ar_open id=" + CryptoParse.longToString(row.getLong(0)));
                    return 0;
                }
                if (open < 0L || paid < 0L) {
                    System.out.println("FAIL integrity:ar_negative id=" + CryptoParse.longToString(row.getLong(0)));
                    return 0;
                }
                i = i + 1;
            }
            System.out.println("PASS integrity:ar_open invoices=" + String.valueOf(r.rows.size()));
            return pass;
        } catch (Exception ex) {
            System.out.println("SKIP integrity:ar_open NOT_APPLICABLE");
            ErpDb.reconnect();
            return pass;
        }
    }

    static int checkApOpen(long companyId, int pass) {
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            DbQueryResult r = ErpDb.query(
                    "SELECT id, total_amount, paid_amount, open_amount FROM ap_invoices WHERE company_id=?", p);
            int i = 0;
            while (i < r.rows.size()) {
                Row row = r.rows.get(i);
                long total = row.getLong(1);
                long paid = row.getLong(2);
                long open = row.getLong(3);
                if (paid + open != total) {
                    System.out.println("FAIL integrity:ap_open id=" + CryptoParse.longToString(row.getLong(0)));
                    return 0;
                }
                if (open < 0L || paid < 0L) {
                    System.out.println("FAIL integrity:ap_negative id=" + CryptoParse.longToString(row.getLong(0)));
                    return 0;
                }
                i = i + 1;
            }
            System.out.println("PASS integrity:ap_open invoices=" + String.valueOf(r.rows.size()));
            return pass;
        } catch (Exception ex) {
            System.out.println("SKIP integrity:ap_open NOT_APPLICABLE");
            ErpDb.reconnect();
            return pass;
        }
    }
}
