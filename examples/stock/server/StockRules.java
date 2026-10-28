/**
 * A tiny rule language for custom strategies. A strategy is an "entry" rule list (all must hold) and an "exit" rule list
 * (any one is enough), each rule being a comparison of two operands, rules separated by ";" or a new line:
 *
 *   close>hh(20)        breakout: close above the highest high of the previous 20 days
 *   ma(10)>>ma(30)      ">>" is "crosses above", "<<" is "crosses below"; ">", "<", ">=", "<=" are plain comparisons
 *   rsi(14)<30          roc(20)>0.1  (20-day return above 10%)   volr(20)>1.5  (volume vs the 20-day average)
 *
 * Operands: a number, close/open/high/low/volume/chg, ma(n) ema(n) vma(n) rsi(n) roc(n) volr(n) hh(n) ll(n) atrp(n)
 * bbu(n) bbm(n) bbl(n) (Bollinger 2 sigma) macd dea hist k(n) d(n) j(n). All of them only use data up to the day
 * itself, so a signal on day t is acted on at the open of day t+1 (same as the built-in strategies).
 * @author 张晓达
 */
class StockRules {
    static String lastError = "";

    static double nan() {
        double[] x = StockIndicators.filled(1);
        return x[0];
    }

    static ArrayList<String> splitConds(String text) {
        ArrayList<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        int st = 0;
        int i = 0;
        while (i <= text.length()) {
            if (i == text.length() || text.charAt(i) == 59 || text.charAt(i) == 10 || text.charAt(i) == 13) {
                String c = text.substring(st, i).trim().replace(" ", "").toLowerCase();
                if (c.length() > 0) {
                    out.add(c);
                }
                st = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static double[] constant(int n, double v) {
        double[] a = new double[n];
        int i = 0;
        while (i < n) {
            a[i] = v;
            i = i + 1;
        }
        return a;
    }

    /** Series for one operand, or null (with lastError set) when it cannot be parsed. */
    static double[] operand(StockSeries s, String tok) {
        int n = s.n;
        if (tok.length() == 0) {
            lastError = "规则缺少操作数";
            return null;
        }
        char c0 = tok.charAt(0);
        if ((c0 >= 48 && c0 <= 57) || c0 == 45 || c0 == 46) {
            double v = StockUtil.parseDouble(tok, nan());
            if (StockUtil.isNan(v) == 1) {
                lastError = "数字格式错误：" + tok;
                return null;
            }
            return constant(n, v);
        }
        String name = tok;
        int p = -1;
        int lp = tok.indexOf("(");
        if (lp > 0) {
            if (!tok.endsWith(")")) {
                lastError = "括号不匹配：" + tok;
                return null;
            }
            name = tok.substring(0, lp);
            p = StockUtil.parseInt(tok.substring(lp + 1, tok.length() - 1), -1);
            if (p < 1 || p > 500) {
                lastError = "周期必须是 1–500 的整数：" + tok;
                return null;
            }
        }
        if (name.equals("close")) {
            return s.c;
        }
        if (name.equals("open")) {
            return s.o;
        }
        if (name.equals("high")) {
            return s.h;
        }
        if (name.equals("low")) {
            return s.l;
        }
        if (name.equals("volume")) {
            return s.v;
        }
        if (name.equals("chg")) {
            return StockIndicators.returns(s.c, n);
        }
        if (name.equals("macd") || name.equals("dea") || name.equals("hist")) {
            double[][] m = StockIndicators.macd(s.c, n);
            if (name.equals("macd")) {
                return m[0];
            }
            if (name.equals("dea")) {
                return m[1];
            }
            return m[2];
        }
        if (p < 0) {
            lastError = "未知的指标或缺少周期：" + tok + "（例如 ma(20)）";
            return null;
        }
        if (name.equals("ma")) {
            return StockIndicators.sma(s.c, n, p);
        }
        if (name.equals("ema")) {
            return StockIndicators.ema(s.c, n, p);
        }
        if (name.equals("vma")) {
            return StockIndicators.sma(s.v, n, p);
        }
        if (name.equals("rsi")) {
            return StockIndicators.rsi(s.c, n, p);
        }
        double[] out = StockIndicators.filled(n);
        int i = 0;
        if (name.equals("roc")) {
            i = p;
            while (i < n) {
                out[i] = s.c[i] / s.c[i - p] - 1.0;
                i = i + 1;
            }
            return out;
        }
        if (name.equals("volr")) {
            double[] vm = StockIndicators.sma(s.v, n, p);
            i = p + 1;
            while (i < n) {
                if (StockUtil.isNan(vm[i - 1]) == 0 && vm[i - 1] > 0.0) {
                    out[i] = s.v[i] / vm[i - 1];
                }
                i = i + 1;
            }
            return out;
        }
        if (name.equals("hh") || name.equals("ll")) {
            i = p;
            while (i < n) {
                if (name.equals("hh")) {
                    out[i] = StockIndicators.maxOver(s.h, i - p, i - 1);
                } else {
                    out[i] = StockIndicators.minOver(s.l, i - p, i - 1);
                }
                i = i + 1;
            }
            return out;
        }
        if (name.equals("atrp")) {
            double[] a = StockIndicators.atr(s.h, s.l, s.c, n, p);
            i = 0;
            while (i < n) {
                if (StockUtil.isNan(a[i]) == 0 && s.c[i] > 0.0) {
                    out[i] = a[i] / s.c[i];
                }
                i = i + 1;
            }
            return out;
        }
        if (name.equals("bbu") || name.equals("bbm") || name.equals("bbl")) {
            double[][] b = StockIndicators.boll(s.c, n, p, 2.0);
            if (name.equals("bbu")) {
                return b[1];
            }
            if (name.equals("bbm")) {
                return b[0];
            }
            return b[2];
        }
        if (name.equals("k") || name.equals("d") || name.equals("j")) {
            double[][] kd = StockIndicators.kdj(s.h, s.l, s.c, n, p);
            if (name.equals("k")) {
                return kd[0];
            }
            if (name.equals("d")) {
                return kd[1];
            }
            return kd[2];
        }
        lastError = "未知的指标：" + name;
        return null;
    }

    /** Per-day truth of one rule: 1 true, 0 false, -1 not available yet (an operand is still warming up). Null on a syntax error. */
    static int[] evalCond(StockSeries s, String cond) {
        int n = s.n;
        int op = -1;
        int k = 0;
        while (k < cond.length()) {
            char ch = cond.charAt(k);
            if (ch == 62 || ch == 60) {
                op = k;
                k = cond.length();
            }
            k = k + 1;
        }
        if (op <= 0) {
            lastError = "规则需要比较符（> < >= <= >> <<）：" + cond;
            return null;
        }
        char c1 = cond.charAt(op);
        int kind = 0;
        int rhsAt = op + 1;
        if (op + 1 < cond.length() && cond.charAt(op + 1) == c1) {
            kind = 3;
            rhsAt = op + 2;
        } else if (op + 1 < cond.length() && cond.charAt(op + 1) == 61) {
            kind = 2;
            rhsAt = op + 2;
        } else {
            kind = 1;
        }
        boolean up = c1 == 62;
        if (rhsAt >= cond.length()) {
            lastError = "规则缺少右侧：" + cond;
            return null;
        }
        double[] a = operand(s, cond.substring(0, op));
        if (a == null) {
            return null;
        }
        double[] b = operand(s, cond.substring(rhsAt, cond.length()));
        if (b == null) {
            return null;
        }
        int[] r = new int[n];
        int t = 0;
        while (t < n) {
            if (StockUtil.isNan(a[t]) == 1 || StockUtil.isNan(b[t]) == 1) {
                r[t] = -1;
            } else if (kind == 3) {
                if (t == 0 || StockUtil.isNan(a[t - 1]) == 1 || StockUtil.isNan(b[t - 1]) == 1) {
                    r[t] = -1;
                } else if (up) {
                    r[t] = 0;
                    if (a[t] > b[t] && a[t - 1] <= b[t - 1]) {
                        r[t] = 1;
                    }
                } else {
                    r[t] = 0;
                    if (a[t] < b[t] && a[t - 1] >= b[t - 1]) {
                        r[t] = 1;
                    }
                }
            } else {
                r[t] = 0;
                if (up) {
                    if (kind == 2) {
                        if (a[t] >= b[t]) {
                            r[t] = 1;
                        }
                    } else if (a[t] > b[t]) {
                        r[t] = 1;
                    }
                } else if (kind == 2) {
                    if (a[t] <= b[t]) {
                        r[t] = 1;
                    }
                } else if (a[t] < b[t]) {
                    r[t] = 1;
                }
            }
            t = t + 1;
        }
        return r;
    }

    /** Validates a rule text against a series; returns "" when fine, else the message. */
    static String check(StockSeries probe, String text, int required) {
        ArrayList<String> cs = splitConds(text);
        if (required == 1 && cs.size() == 0) {
            return "至少需要一条入场规则";
        }
        if (cs.size() > 12) {
            return "规则最多 12 条";
        }
        int i = 0;
        while (i < cs.size()) {
            lastError = "";
            if (evalCond(probe, cs.get(i)) == null) {
                return lastError;
            }
            i = i + 1;
        }
        return "";
    }

    /** Position signal per day (1 hold, 0 flat, -1 not available), same convention as StockBacktest.signals. */
    static int[] signals(StockSeries s, String entryText, String exitText) {
        int n = s.n;
        int[] pos = new int[n];
        ArrayList<String> en = splitConds(entryText);
        ArrayList<String> ex = splitConds(exitText);
        int t = 0;
        while (t < n) {
            pos[t] = -1;
            t = t + 1;
        }
        if (en.size() == 0) {
            return pos;
        }
        int[][] ev = new int[en.size()][];
        int i = 0;
        while (i < en.size()) {
            ev[i] = evalCond(s, en.get(i));
            if (ev[i] == null) {
                return pos;
            }
            i = i + 1;
        }
        int[][] xv = new int[ex.size()][];
        i = 0;
        while (i < ex.size()) {
            xv[i] = evalCond(s, ex.get(i));
            if (xv[i] == null) {
                return pos;
            }
            i = i + 1;
        }
        int hold = 0;
        t = 0;
        while (t < n) {
            int ready = 1;
            int allIn = 1;
            i = 0;
            while (i < ev.length) {
                if (ev[i][t] < 0) {
                    ready = 0;
                }
                if (ev[i][t] != 1) {
                    allIn = 0;
                }
                i = i + 1;
            }
            if (ready == 1) {
                if (hold == 0 && allIn == 1) {
                    hold = 1;
                } else if (hold == 1) {
                    int anyOut = 0;
                    i = 0;
                    while (i < xv.length) {
                        if (xv[i][t] == 1) {
                            anyOut = 1;
                        }
                        i = i + 1;
                    }
                    if (anyOut == 1) {
                        hold = 0;
                    }
                }
                pos[t] = hold;
            }
            t = t + 1;
        }
        return pos;
    }
}
