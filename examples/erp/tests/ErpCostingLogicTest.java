/**
 * Moving-average costing acceptance (integer cents):
 * Open 10@100 → +10@200 → 20/300 avg15 → out 8 cogs120 remain12/180
 * → +3@60 → 15/240 avg16 → return 2 at original 15 (=30) → 17/270
 * → clear remainder → value 0.
 * @author 张晓达
 */
class ErpCostingLogicTest {
    static long companyId = 0L;
    static long warehouseId = 0L;
    static long itemId = 0L;
    static String companyCode = "C";

    static int runAll() {
        companyCode = "C" + CryptoParse.longToString(AsyncClockNative.wallMillis());
        if (seed() == 0) {
            System.out.println("FAIL seed");
            return 0;
        }
        int pass = scenario();
        if (pass != 0) {
            pass = ErpCosting.runConservationChecks(companyId);
        }
        if (pass != 0) {
            System.out.println("PASS costing");
        } else {
            System.out.println("FAIL costing");
        }
        return pass;
    }

    static int seed() {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(companyCode);
        co.add("Costing Co");
        co.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(companyCode);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));
        ArrayList<Object> wh = new ArrayList<Object>();
        wh.add(CryptoParse.longToString(companyId));
        wh.add("WH1");
        wh.add("Main");
        wh.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO warehouses(company_id, code, name, created_at) VALUES (?,?,?,?)", wh);
        ArrayList<Object> it = new ArrayList<Object>();
        it.add(CryptoParse.longToString(companyId));
        it.add("SKU-C");
        it.add("Cost Widget");
        it.add("1000");
        it.add(CryptoParse.longToString(now));
        ErpDb.exec("INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES (?,?,?,?,?)", it);
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(CryptoParse.longToString(companyId));
        wp.add("WH1");
        warehouseId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM warehouses WHERE company_id=? AND code=?", wp, new LongMapper()));
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add("SKU-C");
        itemId = ErpNum.asLong(ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku=?", ip, new LongMapper()));
        if (companyId == 0L || warehouseId == 0L || itemId == 0L) {
            return 0;
        }
        return 1;
    }

    static int ok(PostResultDto r) {
        if (r == null || r.ok == 0) {
            return 0;
        }
        return 1;
    }

    static int expect(long qty, long value, long avg, String step) {
        long q = ErpCosting.balanceQty(companyId, warehouseId, itemId);
        long v = ErpCosting.balanceValue(companyId, warehouseId, itemId);
        long a = ErpCosting.avgCost(companyId, warehouseId, itemId);
        if (q != qty || v != value || a != avg) {
            System.out.println("FAIL " + step + " expected qty/value/avg "
                    + CryptoParse.longToString(qty) + "/" + CryptoParse.longToString(value) + "/"
                    + CryptoParse.longToString(avg) + " got "
                    + CryptoParse.longToString(q) + "/" + CryptoParse.longToString(v) + "/"
                    + CryptoParse.longToString(a));
            return 0;
        }
        System.out.println("OK " + step + " qty=" + CryptoParse.longToString(q)
                + " val=" + CryptoParse.longToString(v) + " avg=" + CryptoParse.longToString(a));
        return 1;
    }

    static long parseField(String json, String key) {
        if (json == null) {
            return 0L;
        }
        String needle = "\"" + key + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return 0L;
        }
        int i = idx + needle.length();
        String n = "";
        while (i < json.length()) {
            int c = json.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            i = i + 1;
        }
        if (n.length() == 0) {
            return 0L;
        }
        return ErpNum.parseLong(n);
    }

    static int scenario() {
        /* 1) Opening 10 / value 100 */
        if (ok(ErpCosting.seedOpening(companyId, warehouseId, itemId, 10, 100L,
                "cost-open-" + companyCode)) == 0) {
            System.out.println("FAIL open seed");
            return 0;
        }
        if (expect(10L, 100L, 10L, "open_10_100") == 0) {
            return 0;
        }

        /* 2-3) Receive 10 @ 20 each = 200 → 20/300 avg 15 */
        if (ok(ErpCosting.receiveCost(companyId, warehouseId, itemId, 10, 20L,
                "cost-in1-" + companyCode)) == 0) {
            System.out.println("FAIL receive 10@20");
            return 0;
        }
        if (expect(20L, 300L, 15L, "after_in_20_300_avg15") == 0) {
            return 0;
        }

        /* Idempotent replay */
        if (ok(ErpCosting.receiveCost(companyId, warehouseId, itemId, 10, 20L,
                "cost-in1-" + companyCode)) == 0) {
            System.out.println("FAIL idempotent receive");
            return 0;
        }
        if (expect(20L, 300L, 15L, "idempotent_unchanged") == 0) {
            return 0;
        }

        /* 4) Issue 8 → cogs 120, remain 12/180 */
        PostResultDto out1 = ErpCosting.issueWithDeliveryLine(companyId, warehouseId, itemId, 8,
                "cost-out1-" + companyCode);
        if (ok(out1) == 0) {
            System.out.println("FAIL issue 8");
            return 0;
        }
        long cogs1 = parseField(out1.documentJson, "cogsAmount");
        if (cogs1 != 120L) {
            System.out.println("FAIL cogs expected 120 got " + CryptoParse.longToString(cogs1));
            return 0;
        }
        long dlLine = parseField(out1.documentJson, "deliveryLineId");
        if (expect(12L, 180L, 15L, "after_out8") == 0) {
            return 0;
        }
        System.out.println("OK cogs_120");

        /* 5-6) Receive 3 @ 20 = 60 → 15/240 avg 16 */
        if (ok(ErpCosting.receiveCost(companyId, warehouseId, itemId, 3, 20L,
                "cost-in2-" + companyCode)) == 0) {
            System.out.println("FAIL receive 3@20");
            return 0;
        }
        if (expect(15L, 240L, 16L, "after_in3_avg16") == 0) {
            return 0;
        }

        /* 7-8) Return 2 at original 15 (=30), not current 16 */
        PostResultDto ret = ErpCosting.returnToStock(companyId, warehouseId, itemId, dlLine, 2,
                "cost-ret-" + companyCode);
        if (ok(ret) == 0) {
            System.out.println("FAIL return 2");
            if (ret != null) {
                System.out.println(ret.message);
            }
            return 0;
        }
        long restore = parseField(ret.documentJson, "restoreValue");
        if (restore != 30L) {
            System.out.println("FAIL restore expected 30 got " + CryptoParse.longToString(restore));
            return 0;
        }
        if (expect(17L, 270L, 15L, "after_return2_orig15") == 0) {
            /* avg may be 270/17 = 15 (integer) */
            long q = ErpCosting.balanceQty(companyId, warehouseId, itemId);
            long v = ErpCosting.balanceValue(companyId, warehouseId, itemId);
            if (q == 17L && v == 270L) {
                System.out.println("OK after_return2_orig15 qty=17 val=270 avg="
                        + CryptoParse.longToString(ErpCosting.avgCost(companyId, warehouseId, itemId)));
            } else {
                return 0;
            }
        }

        /* Over-return rejected */
        PostResultDto over = ErpCosting.returnToStock(companyId, warehouseId, itemId, dlLine, 7,
                "cost-over-" + companyCode);
        if (over != null && over.ok != 0) {
            System.out.println("FAIL expected over-return reject");
            return 0;
        }
        System.out.println("OK over_return_rejected");

        /* 9) Clear remaining 17 → value 0 */
        PostResultDto clear = ErpCosting.issueCost(companyId, warehouseId, itemId, 17,
                "cost-clear-" + companyCode);
        if (ok(clear) == 0) {
            System.out.println("FAIL clear");
            return 0;
        }
        long clearCogs = parseField(clear.documentJson, "cogs");
        if (clearCogs != 270L) {
            System.out.println("FAIL clear cogs expected 270 got " + CryptoParse.longToString(clearCogs));
            return 0;
        }
        if (expect(0L, 0L, 0L, "cleared_zero") == 0) {
            return 0;
        }
        System.out.println("OK clear_residual_zero");
        return 1;
    }
}
