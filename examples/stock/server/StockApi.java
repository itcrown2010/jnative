/**
 * Chart/indicator payloads, watchlist + alert state and the symbol summaries behind the HTTP routes.
 * @author 张晓达
 */
class StockAlert {
    int id;
    String code;
    String type;
    double value;
    int enabled;
    int fired = -1;
}

class StockApi {
    static ArrayList<String> watch;
    static ArrayList<StockAlert> alerts;
    static int nextId;
    static HashMap<String, String> probCache;

    static void init() {
        watch = new ArrayList<String>();
        alerts = new ArrayList<StockAlert>();
        probCache = new HashMap<String, String>();
        nextId = 1;
        loadState();
        if (watch.size() == 0 && alerts.size() == 0) {
            watch.add("SIM002");
            watch.add("SIM003");
            addAlert("SIM002", "rsi_above", 70.0);
            addAlert("SIM003", "prob_above", 0.6);
        }
    }

    static int validCode(String code) {
        if (code == null || code.length() == 0 || code.length() > 16) {
            return 0;
        }
        int i = 0;
        while (i < code.length()) {
            int c = code.charAt(i);
            int ok = 0;
            if (c >= 48 && c <= 57) {
                ok = 1;
            }
            if (c >= 65 && c <= 90) {
                ok = 1;
            }
            if (c >= 97 && c <= 122) {
                ok = 1;
            }
            if (c == 46 || c == 45 || c == 95) {
                ok = 1;
            }
            if (ok == 0) {
                return 0;
            }
            i = i + 1;
        }
        return 1;
    }

    // ---------- state persistence ----------
    static void saveState() {
        try {
            if (!Files.exists(FsPath.of(StockStore.dataDir))) {
                Files.createDirectories(FsPath.of(StockStore.dataDir));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("N " + nextId + "\n");
            int i = 0;
            while (i < watch.size()) {
                sb.append("W " + watch.get(i) + "\n");
                i = i + 1;
            }
            i = 0;
            while (i < alerts.size()) {
                StockAlert a = alerts.get(i);
                sb.append("A " + a.id + " " + a.code + " " + a.type + " " + a.value + " " + a.enabled + " " + a.fired + "\n");
                i = i + 1;
            }
            File.writeString(StockStore.dataDir + "/_state.txt", sb.toString());
        } catch (Exception ex) {
            System.out.println("STOCK_STATE_SAVE_WARN");
        }
    }

    static void loadState() {
        try {
            String path = StockStore.dataDir + "/_state.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path);
            int st = 0;
            int i = 0;
            while (i <= text.length()) {
                if (i == text.length() || text.charAt(i) == 10) {
                    String ln = text.substring(st, i).trim();
                    st = i + 1;
                    parseStateLine(ln);
                }
                i = i + 1;
            }
        } catch (Exception ex) {
            System.out.println("STOCK_STATE_LOAD_WARN");
        }
    }

    static void parseStateLine(String ln) {
        if (ln.length() < 3) {
            return;
        }
        ArrayList<String> p = new ArrayList<String>();
        int st = 0;
        int i = 0;
        while (i <= ln.length()) {
            if (i == ln.length() || ln.charAt(i) == 32) {
                if (i > st) {
                    p.add(ln.substring(st, i));
                }
                st = i + 1;
            }
            i = i + 1;
        }
        String k = p.get(0);
        if (k.equals("N") && p.size() >= 2) {
            nextId = StockUtil.parseInt(p.get(1), 1);
        } else if (k.equals("W") && p.size() >= 2) {
            watch.add(p.get(1));
        } else if (k.equals("A") && p.size() >= 6) {
            StockAlert a = new StockAlert();
            a.id = StockUtil.parseInt(p.get(1), 0);
            a.code = p.get(2);
            a.type = p.get(3);
            a.value = StockUtil.parseDouble(p.get(4), 0.0);
            a.enabled = StockUtil.parseInt(p.get(5), 1);
            if (p.size() >= 7) {
                a.fired = StockUtil.parseInt(p.get(6), -1);
            }
            alerts.add(a);
        }
    }

    static int inWatch(String code) {
        int i = 0;
        while (i < watch.size()) {
            if (watch.get(i).equals(code)) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    static void watchAdd(String code) {
        if (inWatch(code) == 0) {
            watch.add(code);
            saveState();
        }
    }

    static void watchRemove(String code) {
        int i = 0;
        while (i < watch.size()) {
            if (watch.get(i).equals(code)) {
                watch.removeAt(i);
                saveState();
                return;
            }
            i = i + 1;
        }
    }

    static int addAlert(String code, String type, double value) {
        StockAlert a = new StockAlert();
        a.id = nextId;
        nextId = nextId + 1;
        a.code = code;
        a.type = type;
        a.value = value;
        a.enabled = 1;
        StockSeries ser = StockStore.find(code);
        if (ser != null) {
            /* baseline: only a later false -> true crossing notifies */
            a.fired = alertHit(a, alertCurrent(a, ser));
        }
        alerts.add(a);
        saveState();
        return a.id;
    }

    static int removeAlert(int id) {
        int i = 0;
        while (i < alerts.size()) {
            if (alerts.get(i).id == id) {
                alerts.removeAt(i);
                saveState();
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    static int validAlertType(String t) {
        if (t == null) {
            return 0;
        }
        if (t.equals("price_above") || t.equals("price_below") || t.equals("prob_above") || t.equals("prob_below")
                || t.equals("rsi_above") || t.equals("rsi_below")) {
            return 1;
        }
        return 0;
    }

    // ---------- derived values ----------
    /** Ensemble P(up in 5 days) for the latest bar, or NaN if there is not enough history. Cached per series version. */
    static double latestProb(StockSeries s) {
        String key = s.code + "|" + s.version;
        String hit = probCache.get(key);
        if (hit != null) {
            return StockUtil.parseDouble(hit, StockUtil.nan());
        }
        double res = StockUtil.nan();
        int H = 5;
        int n = s.n;
        if (n >= StockFeatures.WARMUP + StockEval.MIN_TRAIN + H + 30) {
            StockFeatures f = StockFeatures.build(s);
            int b = n - 1 - H;
            int a = b - StockEval.MAX_TRAIN + 1;
            if (a < StockFeatures.WARMUP) {
                a = StockFeatures.WARMUP;
            }
            StockModel m1 = StockModel.fit(StockModel.LOGISTIC, f, a, b, H, 999);
            StockModel m2 = StockModel.fit(StockModel.MLP, f, a, b, H, 999);
            res = 0.5 * (m1.predict(f, n - 1) + m2.predict(f, n - 1));
        }
        probCache.put(key, StockUtil.num(res, 8));
        return res;
    }

    static String symbolsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < StockStore.all.size()) {
            StockSeries s = StockStore.all.get(i);
            if (i > 0) {
                sb.append(",");
            }
            double chg = 0.0;
            if (s.n >= 2) {
                chg = s.c[s.n - 1] / s.c[s.n - 2] - 1.0;
            }
            sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"market\":" + StockUtil.js(s.market)
                    + ",\"source\":" + StockUtil.js(s.source) + ",\"bars\":" + s.n + ",\"first\":" + s.date[0] + ",\"last\":" + s.date[s.n - 1]
                    + ",\"close\":" + StockUtil.num(s.c[s.n - 1], 2) + ",\"chg\":" + StockUtil.num(chg, 4)
                    + ",\"watch\":" + inWatch(s.code) + "}");
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    static String barsJson(StockSeries s, int limit) {
        int n = s.n;
        int from = n - limit;
        if (from < 0) {
            from = 0;
        }
        double[] ma5 = StockIndicators.sma(s.c, n, 5);
        double[] ma10 = StockIndicators.sma(s.c, n, 10);
        double[] ma20 = StockIndicators.sma(s.c, n, 20);
        double[] ma60 = StockIndicators.sma(s.c, n, 60);
        double[][] bb = StockIndicators.boll(s.c, n, 20, 2.0);
        double[][] mc = StockIndicators.macd(s.c, n);
        double[] rs = StockIndicators.rsi(s.c, n, 14);
        double[][] kd = StockIndicators.kdj(s.h, s.l, s.c, n, 9);
        double[] at = StockIndicators.atr(s.h, s.l, s.c, n, 14);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"source\":" + StockUtil.js(s.source)
                + ",\"total\":" + n + ",\"from\":" + from);
        if (s.hm != null) {
            sb.append(",\"time\":");
            StockUtil.arrInt(sb, s.hm, from, n);
        }
        sb.append(",\"date\":");
        StockUtil.arrInt(sb, s.date, from, n);
        sb.append(",\"open\":");
        StockUtil.arr(sb, s.o, from, n, 2);
        sb.append(",\"high\":");
        StockUtil.arr(sb, s.h, from, n, 2);
        sb.append(",\"low\":");
        StockUtil.arr(sb, s.l, from, n, 2);
        sb.append(",\"close\":");
        StockUtil.arr(sb, s.c, from, n, 2);
        sb.append(",\"volume\":");
        StockUtil.arr(sb, s.v, from, n, 0);
        sb.append(",\"ma5\":");
        StockUtil.arr(sb, ma5, from, n, 3);
        sb.append(",\"ma10\":");
        StockUtil.arr(sb, ma10, from, n, 3);
        sb.append(",\"ma20\":");
        StockUtil.arr(sb, ma20, from, n, 3);
        sb.append(",\"ma60\":");
        StockUtil.arr(sb, ma60, from, n, 3);
        sb.append(",\"bollMid\":");
        StockUtil.arr(sb, bb[0], from, n, 3);
        sb.append(",\"bollUp\":");
        StockUtil.arr(sb, bb[1], from, n, 3);
        sb.append(",\"bollLow\":");
        StockUtil.arr(sb, bb[2], from, n, 3);
        sb.append(",\"dif\":");
        StockUtil.arr(sb, mc[0], from, n, 4);
        sb.append(",\"dea\":");
        StockUtil.arr(sb, mc[1], from, n, 4);
        sb.append(",\"hist\":");
        StockUtil.arr(sb, mc[2], from, n, 4);
        sb.append(",\"rsi\":");
        StockUtil.arr(sb, rs, from, n, 2);
        sb.append(",\"k\":");
        StockUtil.arr(sb, kd[0], from, n, 2);
        sb.append(",\"d\":");
        StockUtil.arr(sb, kd[1], from, n, 2);
        sb.append(",\"j\":");
        StockUtil.arr(sb, kd[2], from, n, 2);
        sb.append(",\"atr\":" + StockUtil.num(at[n - 1], 3));
        sb.append("}");
        return sb.toString();
    }

    static double alertCurrent(StockAlert a, StockSeries s) {
        double cur = StockUtil.nan();
        if (a.type.startsWith("price")) {
            cur = s.c[s.n - 1];
            if (s.source.equals("sina")) {
                LiveSnap ls = StockLive.snap(a.code);
                if (ls != null && ls.price > 0.0) {
                    cur = ls.price;
                }
            }
        } else if (a.type.startsWith("rsi")) {
            double[] rs = StockIndicators.rsi(s.c, s.n, 14);
            cur = rs[s.n - 1];
        } else {
            cur = latestProb(s);
        }
        return cur;
    }

    static int alertHit(StockAlert a, double cur) {
        if (StockUtil.isNan(cur) != 0) {
            return 0;
        }
        if (a.type.endsWith("_above") && cur > a.value) {
            return 1;
        }
        if (a.type.endsWith("_below") && cur < a.value) {
            return 1;
        }
        return 0;
    }

    static String alertTypeName(String t) {
        if (t.equals("price_above")) {
            return "价格高于";
        }
        if (t.equals("price_below")) {
            return "价格低于";
        }
        if (t.equals("prob_above")) {
            return "5日上涨概率高于";
        }
        if (t.equals("prob_below")) {
            return "5日上涨概率低于";
        }
        if (t.equals("rsi_above")) {
            return "RSI(14)高于";
        }
        return "RSI(14)低于";
    }

    static String describeAlert(StockAlert a, StockSeries s) {
        double cur = StockUtil.nan();
        int hit = 0;
        if (s != null) {
            cur = alertCurrent(a, s);
            hit = alertHit(a, cur);
        }
        String nm = "";
        if (s != null) {
            nm = s.name;
        }
        return "{\"id\":" + a.id + ",\"code\":" + StockUtil.js(a.code) + ",\"name\":" + StockUtil.js(nm) + ",\"type\":"
                + StockUtil.js(a.type) + ",\"value\":" + StockUtil.num(a.value, 4) + ",\"enabled\":" + a.enabled
                + ",\"current\":" + StockUtil.num(cur, 4) + ",\"triggered\":" + (hit * a.enabled) + "}";
    }

    static String alertsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < alerts.size()) {
            StockAlert a = alerts.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append(describeAlert(a, StockStore.find(a.code)));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    static String watchJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < watch.size()) {
            StockSeries s = StockStore.find(watch.get(i));
            if (i > 0) {
                sb.append(",");
            }
            if (s == null) {
                sb.append("{\"code\":" + StockUtil.js(watch.get(i)) + ",\"missing\":true}");
            } else {
                double chg = 0.0;
                if (s.n >= 2) {
                    chg = s.c[s.n - 1] / s.c[s.n - 2] - 1.0;
                }
 double[] rs = StockIndicators.rsi(s.c, s.n, 14);
                double shown = s.c[s.n - 1];
                if (s.source.equals("sina")) {
                    LiveSnap ls = StockLive.snap(s.code);
                    if (ls != null && ls.price > 0.0) {
                        shown = ls.price;
                        chg = ls.pct;
                    }
                }
                sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"close\":" + StockUtil.num(shown, 2)
                        + ",\"chg\":" + StockUtil.num(chg, 4) + ",\"rsi\":" + StockUtil.num(rs[s.n - 1], 1)
                        + ",\"probUp5\":" + StockUtil.num(latestProb(s), 3) + ",\"asOf\":" + s.date[s.n - 1] + "}");
            }
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
}
