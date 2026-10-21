/**
 * Daily bar series, the in-memory symbol store, CSV import and the deterministic demo-data generator.
 * @author 张晓达
 */
class StockSeries {
    String code;
    String name;
    String market;
    String source;
    int n;
    int[] date;
    int[] hm;
    int period;
    double[] o;
    double[] h;
    double[] l;
    double[] c;
    double[] v;
    int version;

    StockSeries(String code, String name, String market, String source, int n) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.source = source;
        this.n = n;
        this.date = new int[n];
        this.o = new double[n];
        this.h = new double[n];
        this.l = new double[n];
        this.c = new double[n];
        this.v = new double[n];
        this.version = 1;
    }
}

class StockStore {
    static ArrayList<StockSeries> all;
    static String dataDir;

    static void init(String dir) {
        all = new ArrayList<StockSeries>();
        dataDir = dir;
        StockGen.addDemoSymbols();
        loadDir();
    }

    static StockSeries find(String code) {
        if (code == null) {
            return null;
        }
        int i = 0;
        while (i < all.size()) {
            StockSeries s = all.get(i);
            if (s.code.equals(code)) {
                return s;
            }
            i = i + 1;
        }
        return null;
    }

    static void put(StockSeries s) {
        int i = 0;
        while (i < all.size()) {
            StockSeries old = all.get(i);
            if (old.code.equals(s.code)) {
                s.version = old.version + 1;
                all.set(i, s);
                return;
            }
            i = i + 1;
        }
        all.add(s);
    }

    static int remove(String code) {
        int i = 0;
        while (i < all.size()) {
            if (all.get(i).code.equals(code)) {
                all.removeAt(i);
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    /** Load every data/*.csv; the file name (without .csv) is the symbol code. */
    static void loadDir() {
        try {
            if (!Files.exists(FsPath.of(dataDir))) {
                return;
            }
            ArrayList<String> names = Files.list(FsPath.of(dataDir));
            int i = 0;
            while (i < names.size()) {
                String fn = names.get(i);
                if (fn.endsWith(".csv")) {
                    String code = fn.substring(0, fn.length() - 4);
                    try {
                        String text = File.readString(dataDir + "/" + fn);
                        StockCsvResult r = StockCsv.parse(text, code, "");
                        if (r.series != null && r.series.n >= 30) {
                            r.series.source = "csv";
                            int sp = text.indexOf("# source=");
                            if (sp >= 0) {
                                String rest = text.substring(sp + 9, Math.min(text.length(), sp + 30));
                                int nl = rest.indexOf("\n");
                                if (nl > 0) {
                                    String srcName = rest.substring(0, nl).trim();
                                    if (srcName.equals("sina")) {
                                        r.series.source = srcName;
                                        r.series.market = "CN";
                                    }
                                }
                            }
                            put(r.series);
                        }
                    } catch (Exception ex) {
                        System.out.println("STOCK_LOAD_WARN " + fn);
                    }
                }
                i = i + 1;
            }
        } catch (Exception ex2) {
            System.out.println("STOCK_LOAD_DIR_WARN");
        }
    }

    static void saveCsv(StockSeries s) {
        try {
            if (!Files.exists(FsPath.of(dataDir))) {
                Files.createDirectories(FsPath.of(dataDir));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# name=" + s.name + "\n");
            sb.append("# source=" + s.source + "\n");
            sb.append("date,open,high,low,close,volume\n");
            int i = 0;
            while (i < s.n) {
                sb.append(StockCsv.dateStr(s.date[i]) + "," + s.o[i] + "," + s.h[i] + "," + s.l[i] + "," + s.c[i] + ","
                        + (long) s.v[i] + "\n");
                i = i + 1;
            }
            File.writeString(dataDir + "/" + s.code + ".csv", sb.toString());
        } catch (Exception ex) {
            System.out.println("STOCK_SAVE_WARN " + s.code);
        }
    }
}

class StockCsvResult {
    StockSeries series;
    int total;
    int accepted;
    int rejected;
    String note;
}

class StockCsv {
    static int digitsOnly(String s) {
        if (s.length() == 0) {
            return 0;
        }
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return 0;
            }
            i = i + 1;
        }
        return 1;
    }

    /** yyyy-mm-dd / yyyy/mm/dd / yyyymmdd -> yyyymmdd, or -1. */
    static int parseDate(String raw) {
        String s = raw.trim();
        if (s.length() == 8 && digitsOnly(s) != 0) {
            return StockUtil.parseInt(s, -1);
        }
        if (s.length() >= 10) {
            String y = s.substring(0, 4);
            String m = s.substring(5, 7);
            String d = s.substring(8, 10);
            int c4 = s.charAt(4);
            int c7 = s.charAt(7);
            if ((c4 == 45 || c4 == 47 || c4 == 46) && (c7 == 45 || c7 == 47 || c7 == 46)) {
                if (digitsOnly(y) != 0 && digitsOnly(m) != 0 && digitsOnly(d) != 0) {
                    int yy = StockUtil.parseInt(y, 0);
                    int mm = StockUtil.parseInt(m, 0);
                    int dd = StockUtil.parseInt(d, 0);
                    if (yy >= 1990 && yy <= 2100 && mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31) {
                        return yy * 10000 + mm * 100 + dd;
                    }
                }
            }
        }
        return -1;
    }

    static String two(int v) {
        if (v < 10) {
            return "0" + v;
        }
        return "" + v;
    }

    static String dateStr(int d) {
        int y = d / 10000;
        int m = (d / 100) % 100;
        int dd = d % 100;
        return y + "-" + two(m) + "-" + two(dd);
    }

    static ArrayList<String> splitSpaces(String line) {
        ArrayList<String> out = new ArrayList<String>();
        int start = -1;
        int i = 0;
        while (i <= line.length()) {
            if (i == line.length() || line.charAt(i) == 32) {
                if (start >= 0) {
                    out.add(line.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
            i = i + 1;
        }
        return out;
    }

    static ArrayList<String> splitLine(String line) {
        ArrayList<String> out = new ArrayList<String>();
        int sep = 44;
        if (line.indexOf(",") < 0) {
            if (line.indexOf("\t") >= 0) {
                sep = 9;
            } else if (line.indexOf(";") >= 0) {
                sep = 59;
            }
        }
        int start = 0;
        int i = 0;
        while (i <= line.length()) {
            if (i == line.length() || line.charAt(i) == sep) {
                out.add(line.substring(start, i).trim());
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static int colIndex(ArrayList<String> hdr, String a, String b, String c, String d, int def) {
        int i = 0;
        while (i < hdr.size()) {
            String h = hdr.get(i).toLowerCase();
            if (h.equals(a) || h.equals(b) || h.equals(c) || h.equals(d)) {
                return i;
            }
            i = i + 1;
        }
        return def;
    }

    static StockCsvResult parse(String text, String code, String name) {
        StockCsvResult res = new StockCsvResult();
        res.note = "";
        String nm = name;
        ArrayList<String> lines = new ArrayList<String>();
        int st = 0;
        int i = 0;
        while (i <= text.length()) {
            if (i == text.length() || text.charAt(i) == 10) {
                String ln = text.substring(st, i);
                if (ln.endsWith("\r")) {
                    ln = ln.substring(0, ln.length() - 1);
                }
                if (ln.trim().length() > 0) {
                    lines.add(ln.trim());
                }
                st = i + 1;
            }
            i = i + 1;
        }
        int cDate = 0;
        int cOpen = 1;
        int cHigh = 2;
        int cLow = 3;
        int cClose = 4;
        int cVol = 5;
        int firstData = 0;
        int ln2 = 0;
        int headerDone = 0;
        while (ln2 < lines.size() && headerDone == 0) {
            String line = lines.get(ln2);
            if (line.startsWith("#")) {
                int p = line.indexOf("name=");
                if (p >= 0 && (nm == null || nm.length() == 0)) {
                    nm = line.substring(p + 5, line.length()).trim();
                }
                ln2 = ln2 + 1;
            } else {
                ArrayList<String> cells = splitLine(line);
                if (cells.size() > 0 && parseDate(cells.get(0)) < 0) {
                    cDate = colIndex(cells, "date", "datetime", "time", "trade_date", 0);
                    cOpen = colIndex(cells, "open", "o", "open_price", "open_price", 1);
                    cHigh = colIndex(cells, "high", "h", "high_price", "high_price", 2);
                    cLow = colIndex(cells, "low", "l", "low_price", "low_price", 3);
                    cClose = colIndex(cells, "close", "c", "close_price", "price", 4);
                    cVol = colIndex(cells, "volume", "vol", "v", "volume_total", 5);
                    ln2 = ln2 + 1;
                }
                headerDone = 1;
            }
        }
        firstData = ln2;
        int maxCol = cDate;
        if (cOpen > maxCol) {
            maxCol = cOpen;
        }
        if (cHigh > maxCol) {
            maxCol = cHigh;
        }
        if (cLow > maxCol) {
            maxCol = cLow;
        }
        if (cClose > maxCol) {
            maxCol = cClose;
        }
        int cap = lines.size() - firstData;
        if (cap < 0) {
            cap = 0;
        }
        int[] dts = new int[cap];
        double[] oo = new double[cap];
        double[] hh = new double[cap];
        double[] ll = new double[cap];
        double[] cc = new double[cap];
        double[] vv = new double[cap];
        int k = 0;
        int j = firstData;
        while (j < lines.size()) {
            res.total = res.total + 1;
            ArrayList<String> cells = splitLine(lines.get(j));
            int ok = 1;
            if (cells.size() <= maxCol) {
                ok = 0;
            }
            int d = -1;
            double po = 0.0;
            double ph = 0.0;
            double pl = 0.0;
            double pc = 0.0;
            double pv = 0.0;
            if (ok != 0) {
                d = parseDate(cells.get(cDate));
                po = StockUtil.parseDouble(cells.get(cOpen), -1.0);
                ph = StockUtil.parseDouble(cells.get(cHigh), -1.0);
                pl = StockUtil.parseDouble(cells.get(cLow), -1.0);
                pc = StockUtil.parseDouble(cells.get(cClose), -1.0);
                if (cVol < cells.size()) {
                    pv = StockUtil.parseDouble(cells.get(cVol), 0.0);
                }
                if (d < 0 || po <= 0.0 || ph <= 0.0 || pl <= 0.0 || pc <= 0.0) {
                    ok = 0;
                }
            }
            if (ok != 0) {
                /* repair obviously inconsistent highs/lows instead of dropping the bar */
                double hi = Math.max(Math.max(po, pc), ph);
                double lo = Math.min(Math.min(po, pc), pl);
                dts[k] = d;
                oo[k] = po;
                hh[k] = hi;
                ll[k] = lo;
                cc[k] = pc;
                if (pv < 0.0) {
                    pv = 0.0;
                }
                vv[k] = pv;
                k = k + 1;
            } else {
                res.rejected = res.rejected + 1;
            }
            j = j + 1;
        }
        /* order ascending (some vendors export newest first), then drop duplicate dates */
        int[] idx = new int[k];
        int a = 0;
        while (a < k) {
            idx[a] = a;
            a = a + 1;
        }
        sortIdx(idx, dts, k);
        int keep = 0;
        int lastD = -1;
        a = 0;
        while (a < k) {
            if (dts[idx[a]] != lastD) {
                keep = keep + 1;
                lastD = dts[idx[a]];
            }
            a = a + 1;
        }
        StockSeries s = new StockSeries(code, nm, "CN", "csv", keep);
        int w = 0;
        lastD = -1;
        a = 0;
        while (a < k) {
            int q = idx[a];
            if (dts[q] != lastD) {
                s.date[w] = dts[q];
                s.o[w] = oo[q];
                s.h[w] = hh[q];
                s.l[w] = ll[q];
                s.c[w] = cc[q];
                s.v[w] = vv[q];
                lastD = dts[q];
                w = w + 1;
            }
            a = a + 1;
        }
        if (keep < k) {
            res.note = "duplicates removed: " + (k - keep);
        }
        res.accepted = keep;
        res.series = s;
        return res;
    }

    /** Insertion-free merge sort of index array by date value. */
    static void sortIdx(int[] idx, int[] key, int n) {
        if (n < 2) {
            return;
        }
        int[] tmp = new int[n];
        int width = 1;
        while (width < n) {
            int lo = 0;
            while (lo < n) {
                int mid = lo + width;
                if (mid > n) {
                    mid = n;
                }
                int hi = lo + 2 * width;
                if (hi > n) {
                    hi = n;
                }
                int p = lo;
                int q = mid;
                int t = lo;
                while (p < mid && q < hi) {
                    if (key[idx[p]] <= key[idx[q]]) {
                        tmp[t] = idx[p];
                        p = p + 1;
                    } else {
                        tmp[t] = idx[q];
                        q = q + 1;
                    }
                    t = t + 1;
                }
                while (p < mid) {
                    tmp[t] = idx[p];
                    p = p + 1;
                    t = t + 1;
                }
                while (q < hi) {
                    tmp[t] = idx[q];
                    q = q + 1;
                    t = t + 1;
                }
                lo = lo + 2 * width;
            }
            int z = 0;
            while (z < n) {
                idx[z] = tmp[z];
                z = z + 1;
            }
            width = width * 2;
        }
    }

}
