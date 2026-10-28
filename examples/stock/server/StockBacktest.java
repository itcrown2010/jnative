/**
 * Long-only daily backtester with A-share style costs.
 *
 * A signal computed from the close of day t is executed at the OPEN of day t+1 (no look-ahead). Costs: commission
 * both sides, stamp duty on sells, slippage both sides. Reports the full-period result and the last 30% separately so
 * the decay between "in-sample looking" and "recent" performance is visible.
 * @author 张晓达
 */
class StockBacktest {
    static double COMMISSION = 0.00025;
    static double STAMP = 0.0005;
    static double SLIPPAGE = 0.0005;
    static double TRADING_DAYS = 244.0;

    /** Position signal per day (1 = want to hold after the close of day t). -1 = signal not available yet. */
    static int[] signals(StockSeries s, String strategy, ParamBag p) {
        int n = s.n;
        int[] pos = new int[n];
        int i = 0;
        while (i < n) {
            pos[i] = -1;
            i = i + 1;
        }
        if (strategy.equals("ma_cross")) {
            int fast = p.gi("fast", 10, 2, 120);
            int slow = p.gi("slow", 30, 3, 250);
            if (fast >= slow) {
                slow = fast + 1;
            }
            double[] a = StockIndicators.sma(s.c, n, fast);
            double[] b = StockIndicators.sma(s.c, n, slow);
            i = 0;
            while (i < n) {
                if (StockUtil.isNan(b[i]) == 0) {
                    if (a[i] > b[i]) {
                        pos[i] = 1;
                    } else {
                        pos[i] = 0;
                    }
                }
                i = i + 1;
            }
        } else if (strategy.equals("rsi")) {
            int period = p.gi("period", 14, 2, 100);
            double lo = p.gd("lo", 30.0, 1.0, 50.0);
            double hi = p.gd("hi", 70.0, 50.0, 99.0);
            double[] r = StockIndicators.rsi(s.c, n, period);
            int hold = 0;
            i = 0;
            while (i < n) {
                if (StockUtil.isNan(r[i]) == 0) {
                    if (hold == 0 && r[i] < lo) {
                        hold = 1;
                    } else if (hold == 1 && r[i] > hi) {
                        hold = 0;
                    }
                    pos[i] = hold;
                }
                i = i + 1;
            }
        } else if (strategy.equals("macd")) {
            double[][] m = StockIndicators.macd(s.c, n);
            i = 0;
            while (i < n) {
                if (StockUtil.isNan(m[1][i]) == 0) {
                    if (m[0][i] > m[1][i]) {
                        pos[i] = 1;
                    } else {
                        pos[i] = 0;
                    }
                }
                i = i + 1;
            }
        } else if (strategy.equals("boll")) {
            int period2 = p.gi("period", 20, 5, 120);
            double k = p.gd("k", 2.0, 0.5, 5.0);
            double[][] bb = StockIndicators.boll(s.c, n, period2, k);
            int hold2 = 0;
            i = 0;
            while (i < n) {
                if (StockUtil.isNan(bb[0][i]) == 0) {
                    if (hold2 == 0 && s.c[i] < bb[2][i]) {
                        hold2 = 1;
                    } else if (hold2 == 1 && s.c[i] > bb[0][i]) {
                        hold2 = 0;
                    }
                    pos[i] = hold2;
                }
                i = i + 1;
            }
        } else if (strategy.equals("rules")) {
            return StockRules.signals(s, p.gs("entry"), p.gs("exit"));
        } else if (strategy.equals("model")) {
            int H = p.gi("horizon", 5, 1, 20);
            double thr = p.gd("threshold", 0.55, 0.5, 0.9);
            StockWf w = StockEval.walkForward(s, H);
            double[] e = StockEval.ensemble(w);
            int hold3 = 0;
            i = 0;
            while (i < n) {
                if (StockUtil.isNan(e[i]) == 0) {
                    if (e[i] >= thr) {
                        hold3 = 1;
                    } else if (e[i] <= 1.0 - thr || e[i] < 0.5) {
                        hold3 = 0;
                    }
                    pos[i] = hold3;
                }
                i = i + 1;
            }
        } else {
            /* buy_hold */
            i = 0;
            while (i < n) {
                pos[i] = 1;
                i = i + 1;
            }
        }
        return pos;
    }

    static int firstSignal(int[] pos, int n) {
        int i = 0;
        while (i < n) {
            if (pos[i] >= 0) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    /**
     * Simulates holding pos[t-1] during day t. Returns daily net returns (index = day), and fills trade info.
     * tradeOut: [entries, exits] counts; trades appended as JSON by the caller via lists.
     */
    static double[] simulate(StockSeries s, int[] pos, int start, TradeLog log) {
        int n = s.n;
        double[] dr = new double[n];
        int held = 0;
        double entryPx = 0.0;
        int entryDay = -1;
        double entryEq = 1.0;
        double eq = 1.0;
        int t = start + 1;
        while (t < n) {
            int want = pos[t - 1];
            if (want < 0) {
                want = 0;
            }
            double r = 0.0;
            if (held == 0 && want == 1) {
                double buyPx = s.o[t] * (1.0 + SLIPPAGE);
                r = s.c[t] / buyPx - 1.0 - COMMISSION;
                held = 1;
                entryPx = buyPx;
                entryDay = t;
                entryEq = eq;
            } else if (held == 1 && want == 0) {
                double sellPx = s.o[t] * (1.0 - SLIPPAGE);
                r = sellPx / s.c[t - 1] - 1.0 - COMMISSION - STAMP;
                log.add(s, entryDay, t, entryPx, sellPx, eq * (1.0 + r) / entryEq - 1.0);
                held = 0;
            } else if (held == 1) {
                r = s.c[t] / s.c[t - 1] - 1.0;
            }
            dr[t] = r;
            eq = eq * (1.0 + r);
            t = t + 1;
        }
        if (held == 1) {
            log.addOpen(s, entryDay, entryPx, eq / entryEq - 1.0);
        }
        return dr;
    }

    static void metrics(StringBuilder sb, double[] dr, int from, int to, double[] bench) {
        /* dr, bench: daily returns, evaluated over days [from, to) */
        int days = to - from;
        double eq = 1.0;
        double peak = 1.0;
        double maxDd = 0.0;
        double sum = 0.0;
        double sumSq = 0.0;
        int invested = 0;
        int t = from;
        while (t < to) {
            eq = eq * (1.0 + dr[t]);
            if (eq > peak) {
                peak = eq;
            }
            double dd = eq / peak - 1.0;
            if (dd < maxDd) {
                maxDd = dd;
            }
            sum = sum + dr[t];
            sumSq = sumSq + dr[t] * dr[t];
            if (dr[t] != 0.0) {
                invested = invested + 1;
            }
            t = t + 1;
        }
        double mean = sum / (double) days;
        double var = sumSq / (double) days - mean * mean;
        double sd = Math.sqrt(Math.max(var, 0.0));
        double years = (double) days / TRADING_DAYS;
        double cagr = Math.pow(Math.max(eq, 1.0e-9), 1.0 / Math.max(years, 1.0e-9)) - 1.0;
        double sharpe = 0.0;
        if (sd > 1.0e-12) {
            sharpe = mean / sd * Math.sqrt(TRADING_DAYS);
        }
        double beq = 1.0;
        double bpeak = 1.0;
        double bdd = 0.0;
        t = from;
        while (t < to) {
            beq = beq * (1.0 + bench[t]);
            if (beq > bpeak) {
                bpeak = beq;
            }
            if (beq / bpeak - 1.0 < bdd) {
                bdd = beq / bpeak - 1.0;
            }
            t = t + 1;
        }
        double bcagr = Math.pow(Math.max(beq, 1.0e-9), 1.0 / Math.max(years, 1.0e-9)) - 1.0;
        sb.append("{\"days\":" + days + ",\"totalReturn\":" + StockUtil.num(eq - 1.0, 4) + ",\"cagr\":" + StockUtil.num(cagr, 4)
                + ",\"annVol\":" + StockUtil.num(sd * Math.sqrt(TRADING_DAYS), 4) + ",\"sharpe\":" + StockUtil.num(sharpe, 2)
                + ",\"maxDrawdown\":" + StockUtil.num(maxDd, 4) + ",\"exposure\":" + StockUtil.num((double) invested / (double) days, 3)
                + ",\"benchTotalReturn\":" + StockUtil.num(beq - 1.0, 4) + ",\"benchCagr\":" + StockUtil.num(bcagr, 4)
                + ",\"benchMaxDrawdown\":" + StockUtil.num(bdd, 4) + "}");
    }

    static String run(StockSeries s, String strategy, ParamBag p, int fromDate) {
        int n = s.n;
        if (!(strategy.equals("buy_hold") || strategy.equals("ma_cross") || strategy.equals("rsi") || strategy.equals("macd")
                || strategy.equals("boll") || strategy.equals("model") || strategy.equals("rules"))) {
            return StockUtil.err("BAD_STRATEGY", "unknown strategy: " + strategy);
        }
        if (strategy.equals("rules")) {
            String ce = StockRules.check(s, p.gs("entry"), 1);
            if (ce.length() == 0) {
                ce = StockRules.check(s, p.gs("exit"), 0);
            }
            if (ce.length() > 0) {
                return StockUtil.err("BAD_RULE", ce);
            }
        }
        int[] pos = signals(s, strategy, p);
        int start = firstSignal(pos, n);
        if (start < 0 || n - start < 60) {
            return StockUtil.err("INSUFFICIENT_DATA", "not enough bars for this strategy");
        }
        if (fromDate > 0) {
            int i = start;
            while (i < n && s.date[i] < fromDate) {
                i = i + 1;
            }
            if (i < start) {
                i = start;
            }
            if (n - i >= 60) {
                start = i;
            }
        }
        TradeLog log = new TradeLog();
        double[] dr = simulate(s, pos, start, log);
        int[] allOn = new int[n];
        int q = 0;
        while (q < n) {
            allOn[q] = 1;
            q = q + 1;
        }
        TradeLog dummy = new TradeLog();
        double[] bench = simulate(s, allOn, start, dummy);
        int from = start + 1;
        int split = from + (int) ((double) (n - from) * 0.7);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"strategy\":" + StockUtil.js(strategy));
        sb.append(",\"costs\":{\"commission\":" + COMMISSION + ",\"stamp\":" + STAMP + ",\"slippage\":" + SLIPPAGE + "}");
        sb.append(",\"startDate\":" + s.date[start] + ",\"splitDate\":" + s.date[split]);
        sb.append(",\"full\":");
        metrics(sb, dr, from, n, bench);
        sb.append(",\"early\":");
        metrics(sb, dr, from, split, bench);
        sb.append(",\"recent\":");
        metrics(sb, dr, split, n, bench);
        sb.append(",\"trades\":" + log.count + ",\"winRate\":" + StockUtil.num(log.winRate(), 3) + ",\"avgHold\":" + StockUtil.num(log.avgHold(), 1));
        /* equity curves (downsampled to <= 700 points) */
        int step = 1;
        while ((n - from) / step > 700) {
            step = step + 1;
        }
        sb.append(",\"curve\":{\"d\":[");
        double[] eqS = new double[n];
        double[] eqB = new double[n];
        double e1 = 1.0;
        double e2 = 1.0;
        int t = from;
        while (t < n) {
            e1 = e1 * (1.0 + dr[t]);
            e2 = e2 * (1.0 + bench[t]);
            eqS[t] = e1;
            eqB[t] = e2;
            t = t + 1;
        }
        int first = 1;
        t = from;
        while (t < n) {
            if ((t - from) % step == 0 || t == n - 1) {
                if (first == 0) {
                    sb.append(",");
                }
                first = 0;
                sb.append("" + s.date[t]);
            }
            t = t + 1;
        }
        sb.append("],\"s\":[");
        first = 1;
        t = from;
        while (t < n) {
            if ((t - from) % step == 0 || t == n - 1) {
                if (first == 0) {
                    sb.append(",");
                }
                first = 0;
                sb.append(StockUtil.num(eqS[t], 4));
            }
            t = t + 1;
        }
        sb.append("],\"b\":[");
        first = 1;
        t = from;
        while (t < n) {
            if ((t - from) % step == 0 || t == n - 1) {
                if (first == 0) {
                    sb.append(",");
                }
                first = 0;
                sb.append(StockUtil.num(eqB[t], 4));
            }
            t = t + 1;
        }
        sb.append("]}");
        sb.append(",\"tradeList\":");
        sb.append(log.json(30));
        sb.append("}");
        return sb.toString();
    }
}

class TradeLog {
    int count;
    int wins;
    double holdSum;
    ArrayList<String> rows;

    TradeLog() {
        rows = new ArrayList<String>();
        count = 0;
        wins = 0;
        holdSum = 0.0;
    }

    void add(StockSeries s, int entryDay, int exitDay, double entryPx, double exitPx, double ret) {
        count = count + 1;
        if (ret > 0.0) {
            wins = wins + 1;
        }
        holdSum = holdSum + (double) (exitDay - entryDay);
        rows.add("{\"in\":" + s.date[entryDay] + ",\"out\":" + s.date[exitDay] + ",\"inPx\":" + StockUtil.num(entryPx, 2)
                + ",\"outPx\":" + StockUtil.num(exitPx, 2) + ",\"ret\":" + StockUtil.num(ret, 4) + ",\"open\":false}");
    }

    void addOpen(StockSeries s, int entryDay, double entryPx, double ret) {
        rows.add("{\"in\":" + s.date[entryDay] + ",\"out\":null,\"inPx\":" + StockUtil.num(entryPx, 2)
                + ",\"outPx\":null,\"ret\":" + StockUtil.num(ret, 4) + ",\"open\":true}");
    }

    double winRate() {
        if (count == 0) {
            return 0.0;
        }
        return (double) wins / (double) count;
    }

    double avgHold() {
        if (count == 0) {
            return 0.0;
        }
        return holdSum / (double) count;
    }

    /** Most recent `max` trades. */
    String json(int max) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int from = rows.size() - max;
        if (from < 0) {
            from = 0;
        }
        int i = from;
        while (i < rows.size()) {
            if (i > from) {
                sb.append(",");
            }
            sb.append(rows.get(i));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
}

/** Query / body parameters as simple name -> value strings. */
class ParamBag {
    HashMap<String, String> m;

    ParamBag() {
        m = new HashMap<String, String>();
    }

    void put(String k, String v) {
        if (v != null && v.length() > 0) {
            m.put(k, v);
        }
    }

    int geti(String k, int def) {
        String v = m.get(k);
        if (v == null) {
            return def;
        }
        return StockUtil.parseInt(v, def);
    }

    int gi(String k, int def, int lo, int hi) {
        int v = geti(k, def);
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    double gd(String k, double def, double lo, double hi) {
        double v = getd(k, def);
        if (StockUtil.isNan(v) != 0) {
            return def;
        }
        return StockUtil.clamp(v, lo, hi);
    }

    String gs(String k) {
        String v = m.get(k);
        if (v == null) {
            return "";
        }
        return v;
    }

    double getd(String k, double def) {
        String v = m.get(k);
        if (v == null) {
            return def;
        }
        return StockUtil.parseDouble(v, def);
    }
}
