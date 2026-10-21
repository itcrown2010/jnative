/**
 * Small numeric / JSON helpers shared by the stock analytics code.
 * @author 张晓达
 */
class StockUtil {
    static double nan() {
        double z = 0.0;
        return z / z;
    }

    static int isNan(double x) {
        if (x != x) {
            return 1;
        }
        return 0;
    }

    static int isBad(double x) {
        if (x != x || x > 1.0e15 || x < -1.0e15) {
            return 1;
        }
        return 0;
    }

    static double sqr(double x) {
        return x * x;
    }

    static double clamp(double x, double lo, double hi) {
        if (x < lo) {
            return lo;
        }
        if (x > hi) {
            return hi;
        }
        return x;
    }

    static double pow10(int dp) {
        double sc = 1.0;
        int i = 0;
        while (i < dp) {
            sc = sc * 10.0;
            i = i + 1;
        }
        return sc;
    }

    /** JSON number with dp decimals, "null" for NaN / infinity. */
    static String num(double x, int dp) {
        if (isBad(x) != 0) {
            return "null";
        }
        double sc = pow10(dp);
        double ax = x;
        if (ax < 0.0) {
            ax = 0.0 - ax;
        }
        if (ax * sc > 4.0e15) {
            return "" + x;
        }
        long q = Math.round(ax * sc);
        if (q == 0L) {
            return "0";
        }
        long unit = (long) sc;
        long ip = q / unit;
        long fp = q % unit;
        String sign = "";
        if (x < 0.0) {
            sign = "-";
        }
        if (fp == 0L) {
            return sign + ip;
        }
        String fs = "" + fp;
        while (fs.length() < dp) {
            fs = "0" + fs;
        }
        int e = fs.length();
        while (e > 0 && fs.charAt(e - 1) == 48) {
            e = e - 1;
        }
        return sign + ip + "." + fs.substring(0, e);
    }

    static void arr(StringBuilder sb, double[] a, int from, int to, int dp) {
        sb.append("[");
        int i = from;
        while (i < to) {
            if (i > from) {
                sb.append(",");
            }
            sb.append(num(a[i], dp));
            i = i + 1;
        }
        sb.append("]");
    }

    static void arrInt(StringBuilder sb, int[] a, int from, int to) {
        sb.append("[");
        int i = from;
        while (i < to) {
            if (i > from) {
                sb.append(",");
            }
            sb.append("" + a[i]);
            i = i + 1;
        }
        sb.append("]");
    }

    static String js(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34) {
                sb.append("\\\"");
            } else if (c == 92) {
                sb.append("\\\\");
            } else if (c == 10) {
                sb.append("\\n");
            } else if (c == 13) {
                sb.append("\\r");
            } else if (c == 9) {
                sb.append("\\t");
            } else if (c >= 0 && c < 32) {
                sb.append(" ");
            } else {
                sb.append(s.substring(i, i + 1));
            }
            i = i + 1;
        }
        sb.append("\"");
        return sb.toString();
    }

    static String err(String code, String msg) {
        return "{\"error\":" + js(code) + ",\"message\":" + js(msg) + "}";
    }

    static int parseInt(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        int sign = 1;
        int i = 0;
        long v = 0L;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        int digits = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return def;
            }
            v = v * 10L + (long) (c - 48);
            if (v > 2000000000L) {
                return def;
            }
            digits = digits + 1;
            i = i + 1;
        }
        if (digits == 0) {
            return def;
        }
        return sign * (int) v;
    }

    static long parseLong(String s, long def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        long v = 0L;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return def;
            }
            v = v * 10L + (long) (c - 48);
            i = i + 1;
        }
        return v;
    }

    static double parseDouble(String s, double def) {
        if (s == null) {
            return def;
        }
        String t = s.trim();
        if (t.length() == 0) {
            return def;
        }
        int i = 0;
        int seen = 0;
        while (i < t.length()) {
            int c = t.charAt(i);
            if (c >= 48 && c <= 57) {
                seen = 1;
            } else if (c != 45 && c != 43 && c != 46 && c != 101 && c != 69) {
                return def;
            }
            i = i + 1;
        }
        if (seen == 0) {
            return def;
        }
        return Double.parseDouble(t);
    }

    /** Standard normal CDF (Abramowitz-Stegun 26.2.17, error < 7.5e-8). */
    static double normCdf(double x) {
        double k = 1.0 / (1.0 + 0.2316419 * Math.abs(x));
        double poly = k * (0.319381530 + k * (-0.356563782 + k * (1.781477937 + k * (-1.821255978 + k * 1.330274429))));
        double pdf = Math.exp(-0.5 * x * x) / 2.5066282746310002;
        double cdf = 1.0 - pdf * poly;
        if (x < 0.0) {
            return 1.0 - cdf;
        }
        return cdf;
    }

    static double sigmoid(double z) {
        if (z > 30.0) {
            return 1.0 / (1.0 + Math.exp(-30.0));
        }
        if (z < -30.0) {
            return 1.0 / (1.0 + Math.exp(30.0));
        }
        return 1.0 / (1.0 + Math.exp(-z));
    }

    static double tanh(double z) {
        if (z > 20.0) {
            return 1.0;
        }
        if (z < -20.0) {
            return -1.0;
        }
        double e = Math.exp(2.0 * z);
        return (e - 1.0) / (e + 1.0);
    }
}

/** Deterministic pseudo random generator (64-bit LCG), so demo data and model fits are reproducible. */
class StockRng {
    long s;
    int haveSpare;
    double spare;

    StockRng(long seed) {
        s = seed * 2862933555777941757L + 3037000493L;
        haveSpare = 0;
        spare = 0.0;
        next();
        next();
    }

    double next() {
        s = s * 6364136223846793005L + 1442695040888963407L;
        long hi = s >>> 11;
        return (double) hi * (1.0 / 9007199254740992.0);
    }

    double gauss() {
        if (haveSpare != 0) {
            haveSpare = 0;
            return spare;
        }
        double u = next();
        if (u < 1.0e-12) {
            u = 1.0e-12;
        }
        double v = next();
        double r = Math.sqrt(-2.0 * Math.log(u));
        double a = 6.283185307179586 * v;
        spare = r * Math.sin(a);
        haveSpare = 1;
        return r * Math.cos(a);
    }
}
