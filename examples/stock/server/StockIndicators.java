/**
 * Technical indicators over double[] series. Undefined leading values are NaN.
 * @author 张晓达
 */
class StockIndicators {
    static double[] filled(int n) {
        double[] a = new double[n];
        double nan = StockUtil.nan();
        int i = 0;
        while (i < n) {
            a[i] = nan;
            i = i + 1;
        }
        return a;
    }

    static double[] sma(double[] x, int n, int p) {
        double[] out = filled(n);
        double sum = 0.0;
        int i = 0;
        while (i < n) {
            sum = sum + x[i];
            if (i >= p) {
                sum = sum - x[i - p];
            }
            if (i >= p - 1) {
                out[i] = sum / (double) p;
            }
            i = i + 1;
        }
        return out;
    }

    static double[] ema(double[] x, int n, int p) {
        double[] out = filled(n);
        if (n < p) {
            return out;
        }
        double k = 2.0 / ((double) p + 1.0);
        double seed = 0.0;
        int i = 0;
        while (i < p) {
            seed = seed + x[i];
            i = i + 1;
        }
        seed = seed / (double) p;
        out[p - 1] = seed;
        i = p;
        while (i < n) {
            out[i] = x[i] * k + out[i - 1] * (1.0 - k);
            i = i + 1;
        }
        return out;
    }

    /** EMA of a series that starts with NaNs (e.g. the MACD line). */
    static double[] emaSkipNan(double[] x, int n, int p) {
        double[] out = filled(n);
        int first = 0;
        while (first < n && StockUtil.isNan(x[first]) != 0) {
            first = first + 1;
        }
        if (n - first < p) {
            return out;
        }
        double k = 2.0 / ((double) p + 1.0);
        double seed = 0.0;
        int i = first;
        while (i < first + p) {
            seed = seed + x[i];
            i = i + 1;
        }
        out[first + p - 1] = seed / (double) p;
        i = first + p;
        while (i < n) {
            out[i] = x[i] * k + out[i - 1] * (1.0 - k);
            i = i + 1;
        }
        return out;
    }

    /** MACD(12,26,9). Returns [dif, dea, hist]. */
    static double[][] macd(double[] c, int n) {
        double[] e12 = ema(c, n, 12);
        double[] e26 = ema(c, n, 26);
        double[] dif = filled(n);
        int i = 0;
        while (i < n) {
            if (StockUtil.isNan(e12[i]) == 0 && StockUtil.isNan(e26[i]) == 0) {
                dif[i] = e12[i] - e26[i];
            }
            i = i + 1;
        }
        double[] dea = emaSkipNan(dif, n, 9);
        double[] hist = filled(n);
        i = 0;
        while (i < n) {
            if (StockUtil.isNan(dif[i]) == 0 && StockUtil.isNan(dea[i]) == 0) {
                hist[i] = 2.0 * (dif[i] - dea[i]);
            }
            i = i + 1;
        }
        double[][] out = new double[3][];
        out[0] = dif;
        out[1] = dea;
        out[2] = hist;
        return out;
    }

    /** Wilder RSI. */
    static double[] rsi(double[] c, int n, int p) {
        double[] out = filled(n);
        if (n <= p) {
            return out;
        }
        double gain = 0.0;
        double loss = 0.0;
        int i = 1;
        while (i <= p) {
            double ch = c[i] - c[i - 1];
            if (ch > 0.0) {
                gain = gain + ch;
            } else {
                loss = loss - ch;
            }
            i = i + 1;
        }
        gain = gain / (double) p;
        loss = loss / (double) p;
        out[p] = rsiVal(gain, loss);
        i = p + 1;
        while (i < n) {
            double ch2 = c[i] - c[i - 1];
            double g = 0.0;
            double ls = 0.0;
            if (ch2 > 0.0) {
                g = ch2;
            } else {
                ls = 0.0 - ch2;
            }
            gain = (gain * (double) (p - 1) + g) / (double) p;
            loss = (loss * (double) (p - 1) + ls) / (double) p;
            out[i] = rsiVal(gain, loss);
            i = i + 1;
        }
        return out;
    }

    static double rsiVal(double gain, double loss) {
        if (loss <= 1.0e-12) {
            if (gain <= 1.0e-12) {
                return 50.0;
            }
            return 100.0;
        }
        double rs = gain / loss;
        return 100.0 - 100.0 / (1.0 + rs);
    }

    static double[] stdev(double[] x, int n, int p) {
        double[] out = filled(n);
        int i = p - 1;
        while (i < n) {
            double m = 0.0;
            int j = i - p + 1;
            while (j <= i) {
                m = m + x[j];
                j = j + 1;
            }
            m = m / (double) p;
            double v = 0.0;
            j = i - p + 1;
            while (j <= i) {
                v = v + (x[j] - m) * (x[j] - m);
                j = j + 1;
            }
            out[i] = Math.sqrt(v / (double) (p - 1));
            i = i + 1;
        }
        return out;
    }

    /** Bollinger bands. Returns [mid, upper, lower]. */
    static double[][] boll(double[] c, int n, int p, double k) {
        double[] mid = sma(c, n, p);
        double[] sd = stdev(c, n, p);
        double[] up = filled(n);
        double[] lo = filled(n);
        int i = 0;
        while (i < n) {
            if (StockUtil.isNan(mid[i]) == 0 && StockUtil.isNan(sd[i]) == 0) {
                up[i] = mid[i] + k * sd[i];
                lo[i] = mid[i] - k * sd[i];
            }
            i = i + 1;
        }
        double[][] out = new double[3][];
        out[0] = mid;
        out[1] = up;
        out[2] = lo;
        return out;
    }

    static double[] atr(double[] h, double[] l, double[] c, int n, int p) {
        double[] out = filled(n);
        if (n <= p) {
            return out;
        }
        double[] tr = new double[n];
        tr[0] = h[0] - l[0];
        int i = 1;
        while (i < n) {
            double a = h[i] - l[i];
            double b = Math.abs(h[i] - c[i - 1]);
            double d = Math.abs(l[i] - c[i - 1]);
            tr[i] = Math.max(a, Math.max(b, d));
            i = i + 1;
        }
        double sum = 0.0;
        i = 1;
        while (i <= p) {
            sum = sum + tr[i];
            i = i + 1;
        }
        out[p] = sum / (double) p;
        i = p + 1;
        while (i < n) {
            out[i] = (out[i - 1] * (double) (p - 1) + tr[i]) / (double) p;
            i = i + 1;
        }
        return out;
    }

    /** KDJ(9,3,3). Returns [k, d, j]. */
    static double[][] kdj(double[] h, double[] l, double[] c, int n, int p) {
        double[] k = filled(n);
        double[] d = filled(n);
        double[] j = filled(n);
        double kk = 50.0;
        double dd = 50.0;
        int i = p - 1;
        while (i < n) {
            double hh = h[i];
            double ll = l[i];
            int q = i - p + 1;
            while (q <= i) {
                if (h[q] > hh) {
                    hh = h[q];
                }
                if (l[q] < ll) {
                    ll = l[q];
                }
                q = q + 1;
            }
            double rsv = 50.0;
            if (hh - ll > 1.0e-12) {
                rsv = (c[i] - ll) / (hh - ll) * 100.0;
            }
            kk = kk * 2.0 / 3.0 + rsv / 3.0;
            dd = dd * 2.0 / 3.0 + kk / 3.0;
            k[i] = kk;
            d[i] = dd;
            j[i] = 3.0 * kk - 2.0 * dd;
            i = i + 1;
        }
        double[][] out = new double[3][];
        out[0] = k;
        out[1] = d;
        out[2] = j;
        return out;
    }

    /** Simple daily returns r[t] = c[t]/c[t-1]-1 (r[0] = NaN). */
    static double[] returns(double[] c, int n) {
        double[] r = filled(n);
        int i = 1;
        while (i < n) {
            r[i] = c[i] / c[i - 1] - 1.0;
            i = i + 1;
        }
        return r;
    }

    /** EWMA (RiskMetrics) daily volatility of log returns, sigma[t] uses returns up to t. */
    static double[] ewmaVol(double[] c, int n, double lambda) {
        double[] out = filled(n);
        if (n < 25) {
            return out;
        }
        double v = 0.0;
        int i = 1;
        while (i <= 20) {
            double lr = Math.log(c[i] / c[i - 1]);
            v = v + lr * lr;
            i = i + 1;
        }
        v = v / 20.0;
        out[20] = Math.sqrt(v);
        i = 21;
        while (i < n) {
            double lr2 = Math.log(c[i] / c[i - 1]);
            v = lambda * v + (1.0 - lambda) * lr2 * lr2;
            out[i] = Math.sqrt(v);
            i = i + 1;
        }
        return out;
    }

    static double maxOver(double[] x, int from, int to) {
        double m = x[from];
        int i = from + 1;
        while (i <= to) {
            if (x[i] > m) {
                m = x[i];
            }
            i = i + 1;
        }
        return m;
    }

    static double minOver(double[] x, int from, int to) {
        double m = x[from];
        int i = from + 1;
        while (i <= to) {
            if (x[i] < m) {
                m = x[i];
            }
            i = i + 1;
        }
        return m;
    }
}
