/**
 * Deterministic synthetic A-share style daily data. The five demo symbols have different, KNOWN dynamics
 * (pure random walk, drifting trend, mean reversion, ...), so the prediction lab can be checked against ground
 * truth: a model that "finds" structure in the random walk is overfitting.
 * @author 张晓达
 */
class StockGen {
    static void addDemoSymbols() {
        StockStore.put(gen("SIM001", "模拟-随机游走", 11L, 20.0, 0.016, 0.0001, 0.0, 0.0, 0.0));
        StockStore.put(gen("SIM002", "模拟-趋势动量", 22L, 35.0, 0.017, 0.0, 0.03, 0.0, 0.0018));
        StockStore.put(gen("SIM003", "模拟-均值回归", 33L, 12.0, 0.015, 0.0, 0.0, 0.06, 0.0));
        StockStore.put(gen("SIM004", "模拟-高波动成长", 44L, 48.0, 0.03, 0.0006, 0.02, 0.0, 0.0006));
        StockStore.put(gen("SIM005", "模拟-蓝筹低波动", 55L, 9.0, 0.009, 0.0003, 0.0, 0.01, 0.0));
    }

    static int isLeap(int y) {
        if (y % 400 == 0) {
            return 1;
        }
        if (y % 100 == 0) {
            return 0;
        }
        if (y % 4 == 0) {
            return 1;
        }
        return 0;
    }

    static int daysInMonth(int y, int m) {
        if (m == 2) {
            return 28 + isLeap(y);
        }
        if (m == 4 || m == 6 || m == 9 || m == 11) {
            return 30;
        }
        return 31;
    }

    /** Days since 1970-01-01 (civil-from-days algorithm, Howard Hinnant). */
    static long daysFromCivil(int y, int m, int d) {
        int yy = y;
        if (m <= 2) {
            yy = y - 1;
        }
        int era = yy / 400;
        int yoe = yy - era * 400;
        int mp = m + 9;
        if (m > 2) {
            mp = m - 3;
        }
        int doy = (153 * mp + 2) / 5 + d - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return (long) era * 146097L + (long) doe - 719468L;
    }

    /** 0 = Sunday ... 6 = Saturday. */
    static int dayOfWeek(int ymd) {
        long z = daysFromCivil(ymd / 10000, (ymd / 100) % 100, ymd % 100);
        long w = (z + 4L) % 7L;
        if (w < 0L) {
            w = w + 7L;
        }
        return (int) w;
    }

    static int nextDay(int ymd) {
        int y = ymd / 10000;
        int m = (ymd / 100) % 100;
        int d = ymd % 100;
        d = d + 1;
        if (d > daysInMonth(y, m)) {
            d = 1;
            m = m + 1;
            if (m > 12) {
                m = 1;
                y = y + 1;
            }
        }
        return y * 10000 + m * 100 + d;
    }

    static int isHoliday(int ymd) {
        int m = (ymd / 100) % 100;
        int d = ymd % 100;
        if (m == 1 && d == 1) {
            return 1;
        }
        if (m == 5 && d >= 1 && d <= 3) {
            return 1;
        }
        if (m == 10 && d >= 1 && d <= 7) {
            return 1;
        }
        return 0;
    }

    static StockSeries gen(String code, String name, long seed, double p0, double vol, double drift, double rho,
            double kappa, double driftSd) {
        int start = 20180102;
        int end = 20261016;
        int cap = 2400;
        int[] dts = new int[cap];
        int n = 0;
        int d = start;
        while (d <= end && n < cap) {
            int dow = dayOfWeek(d);
            if (dow != 0 && dow != 6 && isHoliday(d) == 0) {
                dts[n] = d;
                n = n + 1;
            }
            d = nextDay(d);
        }
        StockSeries s = new StockSeries(code, name, "SIM", "demo", n);
        StockRng rng = new StockRng(seed);
        double lp = Math.log(p0);
        double anchor = lp;
        double prevC = p0;
        double prevR = 0.0;
        double sig2 = vol * vol;
        double eps = 0.0;
        double latent = drift;
        double alpha = 0.08;
        double beta = 0.90;
        double w = vol * vol * (1.0 - alpha - beta);
        double regime = 1.0;
        int i = 0;
        while (i < n) {
            sig2 = w + alpha * eps * eps + beta * sig2;
            double sg = Math.sqrt(sig2);
            if (sg > vol * 3.0) {
                sg = vol * 3.0;
            }
            if (rng.next() < 0.004) {
                regime = 0.0 - regime;
            }
            if (driftSd > 0.0) {
                latent = 0.985 * latent + 0.015 * drift * regime + driftSd * 0.12 * rng.gauss();
            } else {
                latent = drift;
            }
            double z = rng.gauss();
            double r = latent + rho * prevR - kappa * (lp - anchor) + sg * z;
            if (rng.next() < 0.004) {
                r = r + 0.04 * rng.gauss();
            }
            r = StockUtil.clamp(r, -0.099, 0.099);
            eps = r - latent;
            double c = prevC * Math.exp(r);
            if (c < 0.5) {
                c = 0.5;
            }
            double gap = 0.3 * sg * rng.gauss();
            double o = prevC * Math.exp(0.25 * r + gap);
            double hi = Math.max(o, c) * (1.0 + Math.abs(rng.gauss()) * 0.45 * sg);
            double lo = Math.min(o, c) * (1.0 - Math.abs(rng.gauss()) * 0.45 * sg);
            s.date[i] = dts[i];
            s.o[i] = round2(o);
            s.c[i] = round2(c);
            s.h[i] = round2(Math.max(hi, Math.max(s.o[i], s.c[i])));
            s.l[i] = round2(Math.min(lo, Math.min(s.o[i], s.c[i])));
            double volu = 3.0e6 * Math.exp(0.35 * rng.gauss() + 6.0 * Math.abs(r));
            s.v[i] = (double) Math.round(volu / 100.0) * 100.0;
            lp = Math.log(s.c[i]);
            anchor = anchor + (lp - anchor) / 60.0;
            prevC = s.c[i];
            prevR = r;
            i = i + 1;
        }
        return s;
    }

    static double round2(double x) {
        return (double) Math.round(x * 100.0) / 100.0;
    }
}
