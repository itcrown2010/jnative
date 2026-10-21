/**
 * Feature engineering and probabilistic direction models.
 *
 * Every model outputs P(close[t+H] > close[t]) from information available at the close of day t. Models:
 *   0 prior       always the training-window base rate (the honest baseline)
 *   1 momentum    fixed-confidence rule on the 20-day return
 *   2 logistic    L2-regularised logistic regression on standardised features
 *   3 mlp         one hidden layer (tanh) network trained by full-batch gradient descent
 *   4 volnorm     normal model: shrunk trailing drift over EWMA volatility
 * @author 张晓达
 */
class StockFeatures {
    static int NF = 11;
    static int WARMUP = 70;

    int n;
    double[] x;
    double[] vol;
    double[] c;

    static StockFeatures build(StockSeries s) {
        int n = s.n;
        StockFeatures f = new StockFeatures();
        f.n = n;
        f.c = s.c;
        f.x = new double[n * NF];
        double[] close = s.c;
        double[] ret = StockIndicators.returns(close, n);
        double[][] macd = StockIndicators.macd(close, n);
        double[] rsi = StockIndicators.rsi(close, n, 14);
        double[] sma20 = StockIndicators.sma(close, n, 20);
        double[] vsma = StockIndicators.sma(s.v, n, 20);
        double[] sd20 = StockIndicators.stdev(ret, n, 20);
        double[] sd5 = StockIndicators.stdev(ret, n, 5);
        f.vol = StockIndicators.ewmaVol(close, n, 0.94);
        int t = WARMUP;
        while (t < n) {
            int b = t * NF;
            f.x[b] = close[t] / close[t - 1] - 1.0;
            f.x[b + 1] = close[t] / close[t - 5] - 1.0;
            f.x[b + 2] = close[t] / close[t - 10] - 1.0;
            f.x[b + 3] = close[t] / close[t - 20] - 1.0;
            f.x[b + 4] = (rsi[t] - 50.0) / 50.0;
            f.x[b + 5] = macd[2][t] / close[t];
            f.x[b + 6] = sd20[t];
            f.x[b + 7] = StockUtil.clamp(sd5[t] / Math.max(sd20[t], 1.0e-6) - 1.0, -1.0, 2.0);
            f.x[b + 8] = (close[t] - sma20[t]) / sma20[t];
            double vr = 0.0;
            if (vsma[t] > 0.0) {
                vr = s.v[t] / vsma[t] - 1.0;
            }
            f.x[b + 9] = StockUtil.clamp(vr, -1.0, 3.0);
            double hi = StockIndicators.maxOver(close, t - 59, t);
            double lo = StockIndicators.minOver(close, t - 59, t);
            double pos = 0.5;
            if (hi - lo > 1.0e-9) {
                pos = (close[t] - lo) / (hi - lo);
            }
            f.x[b + 10] = pos;
            t = t + 1;
        }
        return f;
    }

    static String featureName(int k) {
        if (k == 0) {
            return "ret1";
        }
        if (k == 1) {
            return "ret5";
        }
        if (k == 2) {
            return "ret10";
        }
        if (k == 3) {
            return "ret20";
        }
        if (k == 4) {
            return "rsi14";
        }
        if (k == 5) {
            return "macdHist";
        }
        if (k == 6) {
            return "vol20";
        }
        if (k == 7) {
            return "volRatio5v20";
        }
        if (k == 8) {
            return "dist20";
        }
        if (k == 9) {
            return "volumeRatio";
        }
        return "range60";
    }
}

class StockModel {
    static int PRIOR = 0;
    static int MOMENTUM = 1;
    static int LOGISTIC = 2;
    static int MLP = 3;
    static int VOLNORM = 4;
    static int COUNT = 5;

    static int HIDDEN = 6;

    int type;
    int horizon;
    double prior;
    double drift;
    double[] mean;
    double[] sd;
    double[] w;
    double bias;
    double[] w1;
    double[] b1;
    double[] w2;
    double b2;

    static String idOf(int type) {
        if (type == PRIOR) {
            return "prior";
        }
        if (type == MOMENTUM) {
            return "momentum";
        }
        if (type == LOGISTIC) {
            return "logistic";
        }
        if (type == MLP) {
            return "mlp";
        }
        return "volnorm";
    }

    /**
     * Fit on rows t in [a, b] (inclusive). Row t is only usable if its label close[t+H] is known, so callers pass
     * b <= lastKnown - H. Standardisation statistics are computed on the training rows only.
     */
    static StockModel fit(int type, StockFeatures f, int a, int b, int horizon, int seed) {
        StockModel m = new StockModel();
        m.type = type;
        m.horizon = horizon;
        int rows = b - a + 1;
        int nf = StockFeatures.NF;
        double ups = 0.0;
        double sumR = 0.0;
        int t = a;
        while (t <= b) {
            double r = f.c[t + horizon] / f.c[t] - 1.0;
            if (r > 0.0) {
                ups = ups + 1.0;
            }
            sumR = sumR + Math.log(f.c[t + horizon] / f.c[t]);
            t = t + 1;
        }
        m.prior = ups / (double) rows;
        m.drift = sumR / (double) rows / (double) horizon;
        if (type == PRIOR || type == MOMENTUM || type == VOLNORM) {
            return m;
        }
        m.mean = new double[nf];
        m.sd = new double[nf];
        int k = 0;
        while (k < nf) {
            double s1 = 0.0;
            t = a;
            while (t <= b) {
                s1 = s1 + f.x[t * nf + k];
                t = t + 1;
            }
            double mu = s1 / (double) rows;
            double s2 = 0.0;
            t = a;
            while (t <= b) {
                double d = f.x[t * nf + k] - mu;
                s2 = s2 + d * d;
                t = t + 1;
            }
            m.mean[k] = mu;
            m.sd[k] = Math.max(Math.sqrt(s2 / (double) rows), 1.0e-9);
            k = k + 1;
        }
        double[] z = new double[rows * nf];
        double[] y = new double[rows];
        t = a;
        while (t <= b) {
            int r0 = (t - a) * nf;
            k = 0;
            while (k < nf) {
                z[r0 + k] = StockUtil.clamp((f.x[t * nf + k] - m.mean[k]) / m.sd[k], -5.0, 5.0);
                k = k + 1;
            }
            if (f.c[t + horizon] > f.c[t]) {
                y[t - a] = 1.0;
            } else {
                y[t - a] = 0.0;
            }
            t = t + 1;
        }
        if (type == LOGISTIC) {
            fitLogistic(m, z, y, rows, nf);
        } else {
            fitMlp(m, z, y, rows, nf, seed);
        }
        return m;
    }

    static void fitLogistic(StockModel m, double[] z, double[] y, int rows, int nf) {
        m.w = new double[nf];
        m.bias = Math.log(Math.max(m.prior, 0.02) / Math.max(1.0 - m.prior, 0.02));
        double lambda = 0.05;
        double lr = 0.3;
        double[] g = new double[nf];
        int ep = 0;
        while (ep < 150) {
            int k = 0;
            while (k < nf) {
                g[k] = 0.0;
                k = k + 1;
            }
            double gb = 0.0;
            int i = 0;
            while (i < rows) {
                int r0 = i * nf;
                double zz = m.bias;
                k = 0;
                while (k < nf) {
                    zz = zz + m.w[k] * z[r0 + k];
                    k = k + 1;
                }
                double e = StockUtil.sigmoid(zz) - y[i];
                gb = gb + e;
                k = 0;
                while (k < nf) {
                    g[k] = g[k] + e * z[r0 + k];
                    k = k + 1;
                }
                i = i + 1;
            }
            k = 0;
            while (k < nf) {
                m.w[k] = m.w[k] - lr * (g[k] / (double) rows + lambda * m.w[k]);
                k = k + 1;
            }
            m.bias = m.bias - lr * gb / (double) rows;
            ep = ep + 1;
        }
    }

    static void fitMlp(StockModel m, double[] z, double[] y, int rows, int nf, int seed) {
        int hd = HIDDEN;
        StockRng rng = new StockRng((long) seed * 7919L + 17L);
        m.w1 = new double[hd * nf];
        m.b1 = new double[hd];
        m.w2 = new double[hd];
        m.b2 = Math.log(Math.max(m.prior, 0.02) / Math.max(1.0 - m.prior, 0.02));
        int i = 0;
        while (i < hd * nf) {
            m.w1[i] = rng.gauss() * 0.3;
            i = i + 1;
        }
        i = 0;
        while (i < hd) {
            m.w2[i] = rng.gauss() * 0.3;
            i = i + 1;
        }
        double lambda = 0.02;
        double lr = 0.25;
        double[] w1 = m.w1;
        double[] b1 = m.b1;
        double[] w2 = m.w2;
        double b2 = m.b2;
        double[] gw1 = new double[hd * nf];
        double[] gb1 = new double[hd];
        double[] gw2 = new double[hd];
        double[] h = new double[hd];
        int ep = 0;
        while (ep < 70) {
            i = 0;
            while (i < hd * nf) {
                gw1[i] = 0.0;
                i = i + 1;
            }
            i = 0;
            while (i < hd) {
                gb1[i] = 0.0;
                gw2[i] = 0.0;
                i = i + 1;
            }
            double gb2 = 0.0;
            int r = 0;
            while (r < rows) {
                int r0 = r * nf;
                int u = 0;
                double out = b2;
                while (u < hd) {
                    double a1 = b1[u];
                    int k = 0;
                    int wb = u * nf;
                    while (k < nf) {
                        a1 = a1 + w1[wb + k] * z[r0 + k];
                        k = k + 1;
                    }
                    h[u] = StockUtil.tanh(a1);
                    out = out + w2[u] * h[u];
                    u = u + 1;
                }
                double e = StockUtil.sigmoid(out) - y[r];
                gb2 = gb2 + e;
                u = 0;
                while (u < hd) {
                    gw2[u] = gw2[u] + e * h[u];
                    double dh = e * w2[u] * (1.0 - h[u] * h[u]);
                    gb1[u] = gb1[u] + dh;
                    int k2 = 0;
                    int wb2 = u * nf;
                    while (k2 < nf) {
                        gw1[wb2 + k2] = gw1[wb2 + k2] + dh * z[r0 + k2];
                        k2 = k2 + 1;
                    }
                    u = u + 1;
                }
                r = r + 1;
            }
            double inv = 1.0 / (double) rows;
            i = 0;
            while (i < hd * nf) {
                w1[i] = w1[i] - lr * (gw1[i] * inv + lambda * w1[i]);
                i = i + 1;
            }
            i = 0;
            while (i < hd) {
                b1[i] = b1[i] - lr * gb1[i] * inv;
                w2[i] = w2[i] - lr * (gw2[i] * inv + lambda * w2[i]);
                i = i + 1;
            }
            b2 = b2 - lr * gb2 * inv;
            ep = ep + 1;
        }
        m.b2 = b2;
    }

    /** Probability of an up move over the horizon given the feature row at day t. */
    double predict(StockFeatures f, int t) {
        int nf = StockFeatures.NF;
        int b = t * nf;
        if (type == PRIOR) {
            return StockUtil.clamp(prior, 0.02, 0.98);
        }
        if (type == MOMENTUM) {
            if (f.x[b + 3] > 0.0) {
                return 0.54;
            }
            return 0.46;
        }
        if (type == VOLNORM) {
            double sig = f.vol[t];
            if (StockUtil.isNan(sig) != 0 || sig < 1.0e-6) {
                return 0.5;
            }
            double mu = 0.25 * drift;
            double zscore = mu * (double) horizon / (sig * Math.sqrt((double) horizon));
            return StockUtil.clamp(StockUtil.normCdf(zscore), 0.02, 0.98);
        }
        double[] z = new double[nf];
        int k = 0;
        while (k < nf) {
            z[k] = StockUtil.clamp((f.x[b + k] - mean[k]) / sd[k], -5.0, 5.0);
            k = k + 1;
        }
        double p;
        if (type == LOGISTIC) {
            double zz = bias;
            k = 0;
            while (k < nf) {
                zz = zz + w[k] * z[k];
                k = k + 1;
            }
            p = StockUtil.sigmoid(zz);
        } else {
            double out = b2;
            int u = 0;
            while (u < HIDDEN) {
                double a1 = b1[u];
                k = 0;
                while (k < nf) {
                    a1 = a1 + w1[u * nf + k] * z[k];
                    k = k + 1;
                }
                out = out + w2[u] * StockUtil.tanh(a1);
                u = u + 1;
            }
            p = StockUtil.sigmoid(out);
        }
        return StockUtil.clamp(p, 0.02, 0.98);
    }
}
