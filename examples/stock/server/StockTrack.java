import jnative.http.*;

/**
 * Track record of the market scanner: every hit is logged with the data date it was based on, and once the next
 * trading day's bar exists the real next-day return is fetched and stored. This is the only honest measure of whether
 * the scan keeps working after it has been shown to the user (the validation table in the scan is a backtest).
 *
 * Record (tab separated): 0 asOf, 1 code, 2 name, 3 prob, 4 theta, 5 excl, 6 baseTest, 7 status(0 pending, 1 verified, 2 unverifiable),
 * 8 entryDate (the day after asOf), 9 maxRet, 10 scanDay, 11 hold days H, 12 endRet.
 * A buyer enters at the open of the day after asOf (T+1) and can sell from the following day: maxRet is the best close within H days
 * after the entry day relative to the entry open, endRet the close H days after entry. Prices are unadjusted, so an ex-dividend day biases returns downwards.
 * @author 张晓达
 */
class StockTrack {
    static ArrayList<String> recs;
    static HashMap<String, String> retryAt;
    static long nextVerify = 0L;

    static void init() {
        recs = new ArrayList<String>();
        retryAt = new HashMap<String, String>();
        try {
            String path = StockStore.dataDir + "/_scanlog.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path);
            int st = 0;
            int i = 0;
            while (i <= text.length()) {
                if (i == text.length() || text.charAt(i) == 10) {
                    String ln = text.substring(st, i);
                    st = i + 1;
                    if (StockNotify.splitTabs(ln).size() >= 13) {
                        recs.add(ln);
                    }
                }
                i = i + 1;
            }
        } catch (Exception ex) {
            System.out.println("STOCK_TRACK_LOAD_WARN");
        }
    }

    static void save() {
        try {
            if (!Files.exists(FsPath.of(StockStore.dataDir))) {
                Files.createDirectories(FsPath.of(StockStore.dataDir));
            }
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < recs.size()) {
                sb.append(recs.get(i) + "\n");
                i = i + 1;
            }
            File.writeString(StockStore.dataDir + "/_scanlog.txt", sb.toString());
        } catch (Exception ex) {
            System.out.println("STOCK_TRACK_SAVE_WARN");
        }
    }

    static void add(int asOf, String code, String name, double p, double theta, int excl, double base, int hold) {
        if (recs == null) {
            init();
        }
        String th = StockUtil.num(theta, 4);
        int i = 0;
        while (i < recs.size()) {
            ArrayList<String> f = StockNotify.splitTabs(recs.get(i));
            i = i + 1;
            if (f.get(0).equals("" + asOf) && f.get(1).equals(code) && f.get(4).equals(th) && f.get(5).equals("" + excl) && f.get(11).equals("" + hold)) {
                return;
            }
        }
        recs.add(asOf + "\t" + code + "\t" + name.replace("\t", " ") + "\t" + StockUtil.num(p, 4) + "\t" + th + "\t" + excl + "\t" + StockUtil.num(base, 5)
                + "\t0\t0\t0\t" + (StockFeed.nowCst() / 10000L) + "\t" + hold + "\t0");
        save();
    }

    static String rebuild(ArrayList<String> f, int status, int entryDate, double maxRet, double endRet) {
        return f.get(0) + "\t" + f.get(1) + "\t" + f.get(2) + "\t" + f.get(3) + "\t" + f.get(4) + "\t" + f.get(5) + "\t" + f.get(6) + "\t" + status + "\t"
                + entryDate + "\t" + StockUtil.num(maxRet, 5) + "\t" + f.get(10) + "\t" + f.get(11) + "\t" + StockUtil.num(endRet, 5);
    }

    /** One small unit of work per call: verify at most one pending record. */
    static void verifyStep() {
        if (recs == null) {
            init();
        }
        long now = System.currentTimeMillis();
        if (recs.size() == 0 || now < nextVerify) {
            return;
        }
        nextVerify = now + 700L;
        int idx = -1;
        int i = 0;
        while (i < recs.size() && idx < 0) {
            ArrayList<String> f = StockNotify.splitTabs(recs.get(i));
            if (f.get(7).equals("0")) {
                String key = f.get(0) + "|" + f.get(1) + "|" + f.get(4) + "|" + f.get(11);
                String ra = retryAt.get(key);
                if (ra == null || StockUtil.parseLong(ra, 0L) <= now) {
                    idx = i;
                }
            }
            i = i + 1;
        }
        if (idx < 0) {
            nextVerify = now + 60000L;
            return;
        }
        ArrayList<String> f = StockNotify.splitTabs(recs.get(idx));
        String key = f.get(0) + "|" + f.get(1) + "|" + f.get(4) + "|" + f.get(11);
        int asOf = StockUtil.parseInt(f.get(0), 0);
        int hd = StockUtil.parseInt(f.get(11), 5);
        try {
            StockSeries s = StockScan.fetchDaily(f.get(1), 50);
            if (s == null) {
                retryAt.put(key, "" + (now + 1800000L));
                return;
            }
            int j = -1;
            int k = 0;
            while (k < s.n) {
                if (s.date[k] == asOf) {
                    j = k;
                }
                k = k + 1;
            }
            if (j < 0) {
                if (s.date[0] > asOf) {
                    recs.set(idx, rebuild(f, 2, 0, 0.0, 0.0));
                    save();
                } else {
                    retryAt.put(key, "" + (now + 1800000L));
                }
                return;
            }
            if (j + 1 + hd < s.n) {
                double entry = s.o[j + 1];
                double mx = -1.0;
                int q = 2;
                while (q <= 1 + hd) {
                    double r = s.c[j + q] / entry - 1.0;
                    if (r > mx) {
                        mx = r;
                    }
                    q = q + 1;
                }
                recs.set(idx, rebuild(f, 1, s.date[j + 1], mx, s.c[j + 1 + hd] / entry - 1.0));
                save();
            } else {
                retryAt.put(key, "" + (now + 1800000L));
            }
        } catch (Exception ex) {
            retryAt.put(key, "" + (now + 600000L));
        }
    }

    /** Round-trip trading cost assumed in net figures: commission both ways, stamp duty on the sell and slippage. */
    static double COST = 0.003;
    static int TOPN = 5;

    static String trackJson() {
        if (recs == null) {
            init();
        }
        int total = recs.size();
        int verified = 0;
        int pending = 0;
        int hit = 0;
        double sumRet = 0.0;
        double sumEnd = 0.0;
        ArrayList<String> keys = new ArrayList<String>();
        int i = 0;
        while (i < total) {
            ArrayList<String> f = StockNotify.splitTabs(recs.get(i));
            i = i + 1;
            String rk = f.get(0) + "|" + f.get(4) + "|" + f.get(5) + "|" + f.get(11);
            int have = 0;
            int q = 0;
            while (q < keys.size()) {
                if (keys.get(q).equals(rk)) {
                    have = 1;
                }
                q = q + 1;
            }
            if (have == 0) {
                keys.add(rk);
            }
            if (f.get(7).equals("1")) {
                verified = verified + 1;
                double r = StockUtil.parseDouble(f.get(9), 0.0);
                sumRet = sumRet + r;
                sumEnd = sumEnd + StockUtil.parseDouble(f.get(12), 0.0);
                if (r >= StockUtil.parseDouble(f.get(4), 0.05)) {
                    hit = hit + 1;
                }
            } else if (f.get(7).equals("0")) {
                pending = pending + 1;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"cost\":" + StockUtil.num(COST, 4) + ",\"topN\":" + TOPN + ",\"total\":" + total + ",\"verified\":" + verified + ",\"pending\":" + pending + ",\"hit\":" + hit);
        if (verified > 0) {
            sb.append(",\"precision\":" + StockUtil.num((double) hit / (double) verified, 4) + ",\"avgMax\":" + StockUtil.num(sumRet / (double) verified, 5) + ",\"avgEnd\":" + StockUtil.num(sumEnd / (double) verified, 5));
        } else {
            sb.append(",\"precision\":null,\"avgMax\":null,\"avgEnd\":null");
        }
        sb.append(",\"runs\":[");
        int kc = 0;
        int ki = keys.size() - 1;
        while (ki >= 0 && kc < 40) {
            String rk = keys.get(ki);
            ki = ki - 1;
            int n = 0;
            int v = 0;
            int h = 0;
            double sr = 0.0;
            double se2 = 0.0;
            double base = 0.0;
            String hs = "";
            String asOf = "";
            String th = "";
            String ex = "";
            double[] rp = new double[total];
            double[] re = new double[total];
            int[] rv = new int[total];
            int rn = 0;
            int j = 0;
            while (j < total) {
                ArrayList<String> f = StockNotify.splitTabs(recs.get(j));
                j = j + 1;
                if ((f.get(0) + "|" + f.get(4) + "|" + f.get(5) + "|" + f.get(11)).equals(rk)) {
                    hs = f.get(11);
                    n = n + 1;
                    asOf = f.get(0);
                    th = f.get(4);
                    ex = f.get(5);
                    base = StockUtil.parseDouble(f.get(6), 0.0);
                    rp[rn] = StockUtil.parseDouble(f.get(3), 0.0);
                    re[rn] = StockUtil.parseDouble(f.get(12), 0.0);
                    rv[rn] = 0;
                    if (f.get(7).equals("1")) {
                        rv[rn] = 1;
                        v = v + 1;
                        double r = StockUtil.parseDouble(f.get(9), 0.0);
                        sr = sr + r;
                        se2 = se2 + StockUtil.parseDouble(f.get(12), 0.0);
                        if (r >= StockUtil.parseDouble(th, 0.05)) {
                            h = h + 1;
                        }
                    }
                    rn = rn + 1;
                }
            }
            /* the five most probable picks of the run: what a person with limited capital would actually have bought */
            int topV = 0;
            double topSum = 0.0;
            int tk = 0;
            while (tk < TOPN && tk < rn) {
                int bi = -1;
                int z = 0;
                while (z < rn) {
                    if (rp[z] >= 0.0 && (bi < 0 || rp[z] > rp[bi])) {
                        bi = z;
                    }
                    z = z + 1;
                }
                if (bi < 0) {
                    tk = TOPN;
                } else {
                    if (rv[bi] == 1) {
                        topV = topV + 1;
                        topSum = topSum + re[bi];
                    }
                    rp[bi] = -1.0;
                    tk = tk + 1;
                }
            }
            String topTxt = "null";
            if (topV > 0) {
                topTxt = StockUtil.num(topSum / (double) topV, 5);
            }
            if (kc > 0) {
                sb.append(",");
            }
            sb.append("{\"topV\":" + topV + ",\"topEnd\":" + topTxt + ",\"asOf\":" + asOf + ",\"hold\":" + hs + ",\"theta\":" + th + ",\"excl\":" + ex + ",\"n\":" + n + ",\"verified\":" + v + ",\"hit\":" + h + ",\"base\":" + StockUtil.num(base, 5));
            if (v > 0) {
                sb.append(",\"avgMax\":" + StockUtil.num(sr / (double) v, 5) + ",\"avgEnd\":" + StockUtil.num(se2 / (double) v, 5));
            } else {
                sb.append(",\"avgMax\":null,\"avgEnd\":null");
            }
            sb.append("}");
            kc = kc + 1;
        }
        sb.append("],\"items\":[");
        int ic = 0;
        i = total - 1;
        while (i >= 0 && ic < 80) {
            ArrayList<String> f = StockNotify.splitTabs(recs.get(i));
            i = i - 1;
            if (ic > 0) {
                sb.append(",");
            }
            sb.append("{\"asOf\":" + f.get(0) + ",\"code\":" + StockUtil.js(f.get(1)) + ",\"name\":" + StockUtil.js(f.get(2)) + ",\"p\":" + f.get(3) + ",\"theta\":" + f.get(4)
                    + ",\"status\":" + f.get(7) + ",\"hold\":" + f.get(11) + ",\"entryDate\":" + f.get(8) + ",\"maxRet\":" + f.get(9) + ",\"endRet\":" + f.get(12) + "}");
            ic = ic + 1;
        }
        sb.append("]}");
        return sb.toString();
    }
}
