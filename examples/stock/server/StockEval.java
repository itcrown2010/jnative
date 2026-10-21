/**
 * Walk-forward (expanding-origin, rolling-window) evaluation of the direction models plus the prediction report.
 *
 * The evaluation is deliberately strict:
 *  - a model refit at day T only sees labels that were already known at T (row t needs close[t+H], so t <= T-H);
 *  - standardisation uses training rows only;
 *  - horizon-H labels overlap, so significance uses n/H effective samples;
 *  - results are compared with a base-rate baseline, and the verdict needs both skill and significance.
 * @author 张晓达
 */
class StockWf {
    int n;
    int horizon;
    int firstTest;
    double[] pred;
    StockFeatures f;
}

class StockEval {
    static int MIN_TRAIN = 500;
    static int MAX_TRAIN = 750;
    static int STEP = 126;
    static HashMap<String, StockWf> cache;

    static void trimCache() {
        if (cache != null && cache.size() > 40) {
            cache = new HashMap<String, StockWf>();
        }
    }

    static StockWf walkForward(StockSeries s, int horizon) {
        if (cache == null) {
            cache = new HashMap<String, StockWf>();
        }
        String key = s.code + "|" + s.period + "|" + horizon + "|" + s.version;
        StockWf hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        StockWf w = new StockWf();
        w.n = s.n;
        w.horizon = horizon;
        w.firstTest = -1;
        w.f = StockFeatures.build(s);
        int n = s.n;
        w.pred = StockIndicators.filled(n * StockModel.COUNT);
        int t0 = StockFeatures.WARMUP + MIN_TRAIN + horizon;
        if (n >= t0 + 30) {
            w.firstTest = t0;
            int T = t0;
            int fitNo = 0;
            while (T < n) {
                int b = T - horizon;
                int a = b - MAX_TRAIN + 1;
                if (a < StockFeatures.WARMUP) {
                    a = StockFeatures.WARMUP;
                }
                int end = T + STEP;
                if (end > n) {
                    end = n;
                }
                int type = 0;
                while (type < StockModel.COUNT) {
                    StockModel m = StockModel.fit(type, w.f, a, b, horizon, fitNo * 10 + type);
                    int t = T;
                    while (t < end) {
                        w.pred[type * n + t] = m.predict(w.f, t);
                        t = t + 1;
                    }
                    type = type + 1;
                }
                fitNo = fitNo + 1;
                T = T + STEP;
            }
        }
        trimCache();
        cache.put(key, w);
        return w;
    }

    static double[] ensemble(StockWf w) {
        int n = w.n;
        double[] e = StockIndicators.filled(n);
        int t = 0;
        while (t < n) {
            double a = w.pred[StockModel.LOGISTIC * n + t];
            double b = w.pred[StockModel.MLP * n + t];
            if (StockUtil.isNan(a) == 0 && StockUtil.isNan(b) == 0) {
                e[t] = 0.5 * (a + b);
            }
            t = t + 1;
        }
        return e;
    }

    /** Appends the metrics object of one model; returns skill/z through out[0]=brierSkill, out[1]=z. */
    static void modelMetrics(StringBuilder sb, StockSeries s, StockWf w, int type, double[] out) {
        int n = w.n;
        int H = w.horizon;
        int cnt = 0;
        int hit = 0;
        int upCnt = 0;
        double brier = 0.0;
        double brierPrior = 0.0;
        double logloss = 0.0;
        double[] binP = new double[10];
        double[] binY = new double[10];
        int[] binN = new int[10];
        double sumHigh = 0.0;
        int nHigh = 0;
        double sumLow = 0.0;
        int nLow = 0;
        int t = w.firstTest;
        while (t >= 0 && t + H < n) {
            double p = w.pred[type * n + t];
            double pp = w.pred[StockModel.PRIOR * n + t];
            if (StockUtil.isNan(p) == 0) {
                double y = 0.0;
                double r = s.c[t + H] / s.c[t] - 1.0;
                if (r > 0.0) {
                    y = 1.0;
                    upCnt = upCnt + 1;
                }
                cnt = cnt + 1;
                if ((p > 0.5 && y > 0.5) || (p <= 0.5 && y < 0.5)) {
                    hit = hit + 1;
                }
                brier = brier + (p - y) * (p - y);
                brierPrior = brierPrior + (pp - y) * (pp - y);
                logloss = logloss - (y * Math.log(p) + (1.0 - y) * Math.log(1.0 - p));
                int bi = (int) (p * 10.0);
                if (bi > 9) {
                    bi = 9;
                }
                if (bi < 0) {
                    bi = 0;
                }
                binP[bi] = binP[bi] + p;
                binY[bi] = binY[bi] + y;
                binN[bi] = binN[bi] + 1;
                if (p >= 0.55) {
                    sumHigh = sumHigh + r;
                    nHigh = nHigh + 1;
                }
                if (p <= 0.45) {
                    sumLow = sumLow + r;
                    nLow = nLow + 1;
                }
            }
            t = t + 1;
        }
        sb.append("{\"id\":\"" + StockModel.idOf(type) + "\",\"n\":" + cnt);
        if (cnt == 0) {
            sb.append("}");
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        double acc = (double) hit / (double) cnt;
        double upRate = (double) upCnt / (double) cnt;
        double nEff = (double) cnt / (double) H;
        double z = (acc - 0.5) / Math.sqrt(0.25 / nEff);
        double skill = 1.0 - brier / Math.max(brierPrior, 1.0e-9);
        sb.append(",\"nEff\":" + StockUtil.num(nEff, 1));
        sb.append(",\"accuracy\":" + StockUtil.num(acc, 4));
        sb.append(",\"baseUpRate\":" + StockUtil.num(upRate, 4));
        sb.append(",\"majorityAccuracy\":" + StockUtil.num(Math.max(upRate, 1.0 - upRate), 4));
        sb.append(",\"brier\":" + StockUtil.num(brier / (double) cnt, 5));
        sb.append(",\"brierPrior\":" + StockUtil.num(brierPrior / (double) cnt, 5));
        sb.append(",\"brierSkill\":" + StockUtil.num(skill, 4));
        sb.append(",\"logLoss\":" + StockUtil.num(logloss / (double) cnt, 4));
        sb.append(",\"z\":" + StockUtil.num(z, 2));
        sb.append(",\"avgRetHigh\":" + StockUtil.num(avg(sumHigh, nHigh), 5));
        sb.append(",\"nHigh\":" + nHigh);
        sb.append(",\"avgRetLow\":" + StockUtil.num(avg(sumLow, nLow), 5));
        sb.append(",\"nLow\":" + nLow);
        sb.append(",\"calibration\":[");
        int b = 0;
        int first = 1;
        while (b < 10) {
            if (binN[b] > 0) {
                if (first == 0) {
                    sb.append(",");
                }
                first = 0;
                sb.append("{\"p\":" + StockUtil.num(binP[b] / (double) binN[b], 3) + ",\"obs\":"
                        + StockUtil.num(binY[b] / (double) binN[b], 3) + ",\"n\":" + binN[b] + "}");
            }
            b = b + 1;
        }
        sb.append("]}");
        out[0] = skill;
        out[1] = z;
    }

    static double avg(double sum, int n) {
        if (n == 0) {
            return StockUtil.nan();
        }
        return sum / (double) n;
    }

    /** Shrunk daily log drift from the last up-to-250 bars. */
    static double trailingDrift(StockSeries s, int upto) {
        int a = upto - 250;
        if (a < 0) {
            a = 0;
        }
        if (upto - a < 20) {
            return 0.0;
        }
        return 0.25 * Math.log(s.c[upto] / s.c[a]) / (double) (upto - a);
    }

    static String verdict(double[] skillZ) {
        double bestZ = StockUtil.nan();
        double bestSkill = 0.0;
        int k = 0;
        while (k < 2) {
            double sk = skillZ[k * 2];
            double zz = skillZ[k * 2 + 1];
            if (StockUtil.isNan(bestZ) != 0 || zz > bestZ) {
                bestZ = zz;
                bestSkill = sk;
            }
            k = k + 1;
        }
        if (bestZ >= 2.6 && bestSkill > 0.0) {
            return "notable";
        }
        if (bestZ >= 1.65 && bestSkill > 0.0) {
            return "weak";
        }
        return "none";
    }

    static String predictJson(StockSeries s, int H, int fanDays) {
        int n = s.n;
        StockWf w = walkForward(s, H);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"horizon\":" + H);
        sb.append(",\"asOf\":" + s.date[n - 1] + ",\"lastClose\":" + StockUtil.num(s.c[n - 1], 2) + ",\"period\":" + s.period);
        if (s.hm != null) {
            sb.append(",\"asOfTime\":" + s.hm[n - 1]);
        }
        sb.append(",\"hist\":[");
        int hs = n - 60;
        if (hs < 0) {
            hs = 0;
        }
        int hi2 = hs;
        while (hi2 < n) {
            if (hi2 > hs) {
                sb.append(",");
            }
            sb.append(StockUtil.num(s.c[hi2], 3));
            hi2 = hi2 + 1;
        }
        sb.append("]");
        if (w.firstTest < 0) {
            sb.append(",\"insufficient\":true,\"minBars\":" + (StockFeatures.WARMUP + MIN_TRAIN + H + 30) + "}");
            return sb.toString();
        }
        sb.append(",\"insufficient\":false,\"testStart\":" + s.date[w.firstTest] + ",\"testBars\":" + (n - H - w.firstTest));
        /* latest probabilities: refit on all labelled rows */
        int b = n - 1 - H;
        int a = b - MAX_TRAIN + 1;
        if (a < StockFeatures.WARMUP) {
            a = StockFeatures.WARMUP;
        }
        sb.append(",\"models\":[");
        double[] skillZ = new double[4];
        double[] out = new double[2];
        double pLog = 0.5;
        double pMlp = 0.5;
        int type = 0;
        while (type < StockModel.COUNT) {
            StockModel m = StockModel.fit(type, w.f, a, b, H, 999);
            double p = m.predict(w.f, n - 1);
            if (type == StockModel.LOGISTIC) {
                pLog = p;
            }
            if (type == StockModel.MLP) {
                pMlp = p;
            }
            if (type > 0) {
                sb.append(",");
            }
            StringBuilder mb = new StringBuilder();
            modelMetrics(mb, s, w, type, out);
            if (type == StockModel.LOGISTIC) {
                skillZ[0] = out[0];
                skillZ[1] = out[1];
            }
            if (type == StockModel.MLP) {
                skillZ[2] = out[0];
                skillZ[3] = out[1];
            }
            String body = mb.toString();
            sb.append(body.substring(0, body.length() - 1));
            sb.append(",\"probUp\":" + StockUtil.num(p, 4) + "}");
            type = type + 1;
        }
        sb.append("]");
        sb.append(",\"ensembleProbUp\":" + StockUtil.num(0.5 * (pLog + pMlp), 4));
        sb.append(",\"verdict\":" + StockUtil.js(verdict(skillZ)));
        /* interval forecast from EWMA volatility + its out-of-sample coverage */
        double[] sig = w.f.vol;
        double sigNow = sig[n - 1];
        double perDay = 1.0;
        if (s.period > 0) {
            perDay = 240.0 / (double) s.period;
        }
        double mu = trailingDrift(s, n - 1);
        sb.append(",\"volDaily\":" + StockUtil.num(sigNow, 5) + ",\"volAnnual\":" + StockUtil.num(sigNow * Math.sqrt(244.0 * perDay), 4));
        sb.append(",\"driftDaily\":" + StockUtil.num(mu, 6));
        sb.append(",\"fan\":[");
        int d = 1;
        while (d <= fanDays) {
            if (d > 1) {
                sb.append(",");
            }
            double sd = sigNow * Math.sqrt((double) d);
            double c0 = s.c[n - 1];
            sb.append("{\"d\":" + d + ",\"mid\":" + StockUtil.num(c0 * Math.exp(mu * (double) d), 2)
                    + ",\"lo80\":" + StockUtil.num(c0 * Math.exp(mu * (double) d - 1.2816 * sd), 2)
                    + ",\"hi80\":" + StockUtil.num(c0 * Math.exp(mu * (double) d + 1.2816 * sd), 2)
                    + ",\"lo95\":" + StockUtil.num(c0 * Math.exp(mu * (double) d - 1.96 * sd), 2)
                    + ",\"hi95\":" + StockUtil.num(c0 * Math.exp(mu * (double) d + 1.96 * sd), 2) + "}");
            d = d + 1;
        }
        sb.append("]");
        int inside = 0;
        int cnt = 0;
        double widthSum = 0.0;
        int t = w.firstTest;
        while (t + H < n) {
            double sg = sig[t];
            if (StockUtil.isNan(sg) == 0) {
                double m2 = trailingDrift(s, t);
                double lr = Math.log(s.c[t + H] / s.c[t]);
                double half = 1.2816 * sg * Math.sqrt((double) H);
                if (lr >= m2 * (double) H - half && lr <= m2 * (double) H + half) {
                    inside = inside + 1;
                }
                widthSum = widthSum + 2.0 * half;
                cnt = cnt + 1;
            }
            t = t + 1;
        }
        sb.append(",\"interval\":{\"nominal\":0.8,\"coverage\":" + StockUtil.num(avg((double) inside, cnt), 4)
                + ",\"meanWidth\":" + StockUtil.num(avg(widthSum, cnt), 4) + ",\"n\":" + cnt + "}");
        /* feature snapshot for display */
        sb.append(",\"features\":{");
        int k = 0;
        while (k < StockFeatures.NF) {
            if (k > 0) {
                sb.append(",");
            }
            sb.append("\"" + StockFeatures.featureName(k) + "\":" + StockUtil.num(w.f.x[(n - 1) * StockFeatures.NF + k], 5));
            k = k + 1;
        }
        sb.append("}}");
        return sb.toString();
    }
}
