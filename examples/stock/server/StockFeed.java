import jnative.http.*;

/**
 * Real A-share data feed.
 *
 * Raw daily bars come from Sina (HTTP) and are forward-adjusted here with Sina's multiplicative hfq factors (Tencent's
 * own "qfq" series is additively adjusted and goes negative for old Moutai bars, which would corrupt returns).
 * Names and symbol search use Tencent's smartbox endpoint (HTTPS). Today's bar is dropped while the market is still open so models
 * never train on or predict from an unfinished candle.
 * @author 张晓达
 */
class FeedSym {
    String code;
    String secid;
    String sina;
}

class FeedBars {
    StockSeries series;
    String error;
    String note;
}

class StockFeed {
    static String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    static int DEFAULT_BEG = 20150101;

    static void init() {
        HttpClient.connectTimeoutMs = 8000;
        HttpClient.requestTimeoutMs = 20000;
        HttpClient.tlsHandshakeTimeoutMs = 8000;
    }

    // ---------- symbol helpers ----------
    /** "600519" / "sh600519" / "SZ000001" -> canonical prefixed code; null if not a valid A-share style code. */
    static FeedSym resolve(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase();
        String pre = "";
        if (s.startsWith("sh") || s.startsWith("sz") || s.startsWith("bj")) {
            pre = s.substring(0, 2);
            s = s.substring(2, s.length());
        }
        if (s.length() != 6 || StockCsv.digitsOnly(s) == 0) {
            return null;
        }
        if (pre.length() == 0) {
            int d = s.charAt(0) - 48;
            if (d == 5 || d == 6 || d == 9) {
                pre = "sh";
            } else if (d == 4 || d == 8) {
                pre = "bj";
            } else {
                pre = "sz";
            }
        }
        FeedSym f = new FeedSym();
        f.code = pre + s;
        f.sina = pre + s;
        if (pre.equals("sh")) {
            f.secid = "1." + s;
        } else {
            f.secid = "0." + s;
        }
        return f;
    }

    static String urlEnc(String s) {
        byte[] b = Bytes.fromString(s);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < b.length) {
            int c = b[i] & 255;
            int plain = 0;
            if (c >= 48 && c <= 57) {
                plain = 1;
            }
            if (c >= 65 && c <= 90) {
                plain = 1;
            }
            if (c >= 97 && c <= 122) {
                plain = 1;
            }
            if (c == 45 || c == 46 || c == 95) {
                plain = 1;
            }
            if (plain == 1) {
                sb.append(String.valueOf((char) c));
            } else {
                String hex = "0123456789ABCDEF";
                sb.append("%" + hex.substring(c / 16, c / 16 + 1) + hex.substring(c % 16, c % 16 + 1));
            }
            i = i + 1;
        }
        return sb.toString();
    }

    // ---------- China time ----------
    /** Current China-standard-time as yyyymmddhhmm (long). */
    static long nowCst() {
        long secs = System.currentTimeMillis() / 1000L + 28800L;
        long days = secs / 86400L;
        long sod = secs % 86400L;
        long z = days + 719468L;
        long era = z / 146097L;
        long doe = z - era * 146097L;
        long yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L;
        long y = yoe + era * 400L;
        long doy = doe - (365L * yoe + yoe / 4L - yoe / 100L);
        long mp = (5L * doy + 2L) / 153L;
        long d = doy - (153L * mp + 2L) / 5L + 1L;
        long m = mp + 3L;
        if (mp >= 10L) {
            m = mp - 9L;
        }
        if (m <= 2L) {
            y = y + 1L;
        }
        long hh = sod / 3600L;
        long mm = (sod % 3600L) / 60L;
        return (y * 10000L + m * 100L + d) * 10000L + hh * 100L + mm;
    }

    /** Day of week in China: 0 = Monday ... 6 = Sunday. */
    static int nowDow() {
        long days = (System.currentTimeMillis() / 1000L + 28800L) / 86400L;
        return (int) ((days + 3L) % 7L);
    }

    /** True while today's daily bar may still change (before 15:05 CST on the bar's date). */
    static int barIsOpen(int barDate) {
        long now = nowCst();
        long today = now / 10000L;
        long hm = now % 10000L;
        if ((long) barDate == today && hm < 1505L) {
            return 1;
        }
        return 0;
    }

    // ---------- HTTP ----------
    static String httpGet(String url, String referer) {
        HttpRequest req = HttpRequest.create("GET", url);
        req.http1Only = 1;
        req.header("User-Agent", UA);
        req.header("Accept", "application/json,text/plain,*/*");
        if (referer != null) {
            req.header("Referer", referer);
        }
        int attempt = 0;
        while (attempt < 3) {
            attempt = attempt + 1;
            try {
                HttpResponse r = HttpClient.send(req);
                if (r.statusCode == 200) {
                    return Bytes.toString(r.body);
                }
                if (attempt >= 3) {
                    throw new IOException("HTTP " + r.statusCode);
                }
            } catch (Exception ex) {
                if (attempt >= 3) {
                    throw new IOException("request failed");
                }
            }
        }
        throw new IOException("request failed");
    }

    static String field(String obj, String key) {
        String pat = "\"" + key + "\":\"";
        int i = obj.indexOf(pat);
        if (i < 0) {
            return "";
        }
        int st = i + pat.length();
        int j = st;
        while (j < obj.length() && obj.charAt(j) != 34) {
            j = j + 1;
        }
        return obj.substring(st, j);
    }

    /** Splits a flat JSON array of objects into the text of each {...} (no nested objects/braces in values). */
    static ArrayList<String> objects(String body) {
        ArrayList<String> out = new ArrayList<String>();
        int depthStart = -1;
        int i = 0;
        while (i < body.length()) {
            int c = body.charAt(i);
            if (c == 123) {
                depthStart = i;
            } else if (c == 125 && depthStart >= 0) {
                out.add(body.substring(depthStart, i + 1));
                depthStart = -1;
            }
            i = i + 1;
        }
        return out;
    }

    // ---------- K-line fetchers ----------
    static FeedBars fetch(FeedSym f, int beg) {
        return sina(f, beg);
    }

    static int hexv(int c) {
        if (c >= 48 && c <= 57) {
            return c - 48;
        }
        if (c >= 97 && c <= 102) {
            return c - 87;
        }
        if (c >= 65 && c <= 70) {
            return c - 55;
        }
        return -1;
    }

    /** Decodes JSON-style \\uXXXX escapes. */
    static String unescapeU(String s) {
        if (s.indexOf("\\u") < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 92 && i + 5 < s.length() + 0 && s.charAt(i + 1) == 117) {
                int v = 0;
                int k = 0;
                int ok = 1;
                while (k < 4) {
                    int hv = hexv(s.charAt(i + 2 + k));
                    if (hv < 0) {
                        ok = 0;
                    } else {
                        v = v * 16 + hv;
                    }
                    k = k + 1;
                }
                if (ok == 1) {
                    sb.append(String.valueOf((char) v));
                    i = i + 6;
                } else {
                    sb.append("\\");
                    i = i + 1;
                }
            } else {
                sb.append(s.substring(i, i + 1));
                i = i + 1;
            }
        }
        return sb.toString();
    }

    static ArrayList<String> quoted(String row, int max) {
        ArrayList<String> out = new ArrayList<String>();
        int qs = -1;
        int i = 0;
        while (i < row.length() && out.size() < max) {
            if (row.charAt(i) == 34) {
                if (qs < 0) {
                    qs = i + 1;
                } else {
                    out.add(row.substring(qs, i));
                    qs = -1;
                }
            }
            i = i + 1;
        }
        return out;
    }

    /** Sina cumulative back-adjust (hfq) factors, newest first; empty list if unavailable. */
    static ArrayList<String> factorDates;
    static double[] factorVals;

    static int loadFactors(FeedSym f) {
        factorDates = new ArrayList<String>();
        factorVals = new double[0];
        String body = null;
        try {
            body = httpGet("http://finance.sina.com.cn/realstock/company/" + f.sina + "/hfq.js", "https://finance.sina.com.cn/");
        } catch (Exception ex) {
            return 0;
        }
        int bi = body.indexOf("\"data\":[");
        if (bi < 0) {
            return 0;
        }
        ArrayList<String> objs = objects(body.substringFrom(bi));
        double[] vals = new double[objs.size()];
        int n = 0;
        int i = 0;
        while (i < objs.size()) {
            String d = field(objs.get(i), "d");
            double v = StockUtil.parseDouble(field(objs.get(i), "f"), 0.0);
            if (d.length() == 10 && v > 0.0) {
                factorDates.add(d);
                vals[n] = v;
                n = n + 1;
            }
            i = i + 1;
        }
        factorVals = vals;
        return n;
    }

    /** Multiplier that turns a raw price on `ymd` into a forward-adjusted price (latest bar unchanged). */
    static double adjFactor(int ymd) {
        int n = factorDates.size();
        if (n == 0) {
            return 1.0;
        }
        String key = dashDate(ymd);
        int i = 0;
        while (i < n) {
            if (factorDates.get(i).compareTo(key) <= 0) {
                return factorVals[i] / factorVals[0];
            }
            i = i + 1;
        }
        return factorVals[n - 1] / factorVals[0];
    }

    static String dashDate(int ymd) {
        String m = "" + ((ymd / 100) % 100);
        String d = "" + (ymd % 100);
        if (m.length() < 2) {
            m = "0" + m;
        }
        if (d.length() < 2) {
            d = "0" + d;
        }
        return (ymd / 10000) + "-" + m + "-" + d;
    }

    static HashMap<String, String> nameCache;
    static HashMap<String, String> nameFailAt;

    /** Stock name for a code such as sz301205, remembered after the first lookup; returns the code itself when it cannot be found (retried after 5 minutes). */
    static String nameFor(String code) {
        if (nameCache == null) {
            nameCache = new HashMap<String, String>();
            nameFailAt = new HashMap<String, String>();
        }
        String hit = nameCache.get(code);
        if (hit != null) {
            return hit;
        }
        long now = System.currentTimeMillis();
        String fl = nameFailAt.get(code);
        if (fl != null && now - StockUtil.parseLong(fl, 0L) < 300000L) {
            return code;
        }
        FeedSym f = resolve(code);
        if (f == null) {
            return code;
        }
        String nm = lookupName(f);
        if (nm == null || nm.length() == 0 || nm.equals(code)) {
            nameFailAt.put(code, "" + now);
            return code;
        }
        nameCache.put(code, nm);
        return nm;
    }

    static String lookupName(FeedSym f) {
        try {
            String body = httpGet("https://smartbox.gtimg.cn/s3/?v=2&t=all&q=" + f.code.substring(2, 8), "https://gu.qq.com/");
            ArrayList<String> hits = StockFeed.hintItems(body);
            int i = 0;
            while (i < hits.size()) {
                ArrayList<String> p = splitTilde(hits.get(i));
                if (p.size() >= 3 && (p.get(0) + p.get(1)).equals(f.code)) {
                    return unescapeU(p.get(2));
                }
                i = i + 1;
            }
        } catch (Exception ex) {
            return f.code;
        }
        return f.code;
    }

    static ArrayList<String> splitTilde(String item) {
        ArrayList<String> p = new ArrayList<String>();
        int ps = 0;
        int j = 0;
        while (j <= item.length()) {
            if (j == item.length() || item.charAt(j) == 126) {
                p.add(item.substring(ps, j));
                ps = j + 1;
            }
            j = j + 1;
        }
        return p;
    }

    static ArrayList<String> hintItems(String body) {
        ArrayList<String> items = new ArrayList<String>();
        int qa = body.indexOf("\"");
        int qb = body.length() - 1;
        while (qb > 0 && body.charAt(qb) != 34) {
            qb = qb - 1;
        }
        if (qa < 0 || qb <= qa) {
            return items;
        }
        String list = body.substring(qa + 1, qb);
        int st = 0;
        int i = 0;
        while (i <= list.length()) {
            if (i == list.length() || list.charAt(i) == 94) {
                items.add(list.substring(st, i));
                st = i + 1;
            }
            i = i + 1;
        }
        return items;
    }

    /** Sina raw daily bars + Sina adjustment factors => multiplicatively forward-adjusted series. */
    static FeedBars sina(FeedSym f, int beg) {
        FeedBars out = new FeedBars();
        String body = null;
        try {
            String url = "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol="
                    + f.sina + "&scale=240&ma=no&datalen=3200";
            body = httpGet(url, "https://finance.sina.com.cn/");
        } catch (Exception ex) {
            out.error = "sina request failed";
            return out;
        }
        ArrayList<String> objs = objects(body);
        if (objs.size() < 30) {
            out.error = "sina: no data for " + f.code;
            return out;
        }
        int nf = loadFactors(f);
        StockSeries s = new StockSeries(f.code, lookupName(f), "CN", "sina", objs.size());
        int n = 0;
        int k = 0;
        while (k < objs.size()) {
            String o = objs.get(k);
            k = k + 1;
            int d = StockCsv.parseDate(field(o, "day"));
            if (d < beg) {
                continue;
            }
            double af = adjFactor(d);
            n = put(s, n, d, StockUtil.parseDouble(field(o, "open"), 0.0) * af, StockUtil.parseDouble(field(o, "high"), 0.0) * af,
                    StockUtil.parseDouble(field(o, "low"), 0.0) * af, StockUtil.parseDouble(field(o, "close"), 0.0) * af,
                    StockUtil.parseDouble(field(o, "volume"), 0.0));
        }
        String note = "已按新浪复权因子做前复权(" + nf + "个除权除息点)";
        if (nf == 0) {
            note = "未取到复权因子(指数或接口不可用),价格为原始价";
        }
        return finish(out, s, n, note);
    }

    static int put(StockSeries s, int n, int date, double o, double h, double l, double c, double v) {
        if (date < 0 || o <= 0.0 || c <= 0.0 || StockUtil.isBad(o) != 0 || StockUtil.isBad(c) != 0) {
            return n;
        }
        if (n > 0 && date <= s.date[n - 1]) {
            return n;
        }
        double hi = Math.max(h, Math.max(o, c));
        double lo = Math.min(l, Math.min(o, c));
        if (l <= 0.0) {
            lo = Math.min(o, c);
        }
        s.date[n] = date;
        s.o[n] = o;
        s.h[n] = hi;
        s.l[n] = lo;
        s.c[n] = c;
        s.v[n] = v;
        return n + 1;
    }

    /** Trim to n bars and drop an unfinished current-day bar. */
    static FeedBars finish(FeedBars out, StockSeries s, int n, String note) {
        out.note = note;
        if (n > 0 && barIsOpen(s.date[n - 1]) == 1) {
            n = n - 1;
            out.note = out.note + " 已忽略盘中未收盘的当日K线";
        }
        if (n < 30) {
            out.error = "too few bars (" + n + ")";
            return out;
        }
        StockSeries t = new StockSeries(s.code, s.name, s.market, s.source, n);
        int i = 0;
        while (i < n) {
            t.date[i] = s.date[i];
            t.o[i] = s.o[i];
            t.h[i] = s.h[i];
            t.l[i] = s.l[i];
            t.c[i] = s.c[i];
            t.v[i] = s.v[i];
            i = i + 1;
        }
        out.series = t;
        return out;
    }

    // ---------- search ----------
    static String searchJson(String q) {
        String body = null;
        FeedSym direct = resolve(q);
        if (direct != null) {
            q = direct.code.substring(2, 8);
        }
        try {
            body = httpGet("https://smartbox.gtimg.cn/s3/?v=2&t=all&q=" + urlEnc(q.trim()), "https://gu.qq.com/");
        } catch (Exception ex) {
            return StockUtil.err("FEED_UNAVAILABLE", "symbol search failed (network?)");
        }
        ArrayList<String> items = hintItems(body);
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int cnt = 0;
        int ii = 0;
        while (ii < items.size()) {
            ArrayList<String> p = splitTilde(items.get(ii));
            ii = ii + 1;
            if (p.size() >= 5) {
                String mk = p.get(0);
                String code = p.get(1);
                String ty = p.get(4);
                int okMarket = 0;
                if (mk.equals("sh") || mk.equals("sz") || mk.equals("bj")) {
                    okMarket = 1;
                }
                int okType = 0;
                if (ty.startsWith("GP") || ty.equals("ZS") || ty.equals("ETF") || ty.equals("LOF")) {
                    okType = 1;
                }
                if (okMarket == 1 && okType == 1 && code.length() == 6 && StockCsv.digitsOnly(code) == 1 && cnt < 12) {
                    int added = 0;
                    if (StockStore.find(mk + code) != null) {
                        added = 1;
                    }
                    if (cnt > 0) {
                        sb.append(",");
                    }
                    sb.append("{\"code\":" + StockUtil.js(mk + code) + ",\"name\":" + StockUtil.js(unescapeU(p.get(2)))
                            + ",\"kind\":" + StockUtil.js(ty) + ",\"added\":" + added + "}");
                    cnt = cnt + 1;
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ---------- add / refresh ----------
    static String addJson(String raw, int beg) {
        FeedSym f = resolve(raw);
        if (f == null) {
            return StockUtil.err("BAD_CODE", "expected a 6-digit A-share code like 600519 or sh600519");
        }
        FeedBars r = fetch(f, beg);
        if (r.series == null) {
            return StockUtil.err("FEED_FAILED", r.error);
        }
        StockSeries old = StockStore.find(f.code);
        if (old != null && old.name.length() > 0 && r.series.name.equals(r.series.code)) {
            r.series.name = old.name;
        }
        StockStore.put(r.series);
        StockStore.saveCsv(r.series);
        StockEval.trimCache();
        StockSeries cur = StockStore.find(f.code);
        return "{\"code\":" + StockUtil.js(f.code) + ",\"name\":" + StockUtil.js(cur.name) + ",\"bars\":" + cur.n + ",\"source\":"
                + StockUtil.js(cur.source) + ",\"first\":" + cur.date[0] + ",\"last\":" + cur.date[cur.n - 1] + ",\"note\":"
                + StockUtil.js(r.note) + "}";
    }

    static String refreshJson(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int cnt = 0;
        int idx = 0;
        ArrayList<String> codes = new ArrayList<String>();
        if (code != null && !code.equals("ALL")) {
            codes.add(code);
        } else {
            idx = 0;
            while (idx < StockStore.all.size()) {
                StockSeries s = StockStore.all.get(idx);
                if (s.source.equals("sina")) {
                    codes.add(s.code);
                }
                idx = idx + 1;
            }
        }
        int i = 0;
        while (i < codes.size()) {
            StockSeries s = StockStore.find(codes.get(i));
            i = i + 1;
            if (s == null || !(s.source.equals("sina"))) {
                continue;
            }
            int before = s.n;
            int lastBefore = s.date[s.n - 1];
            String res = addJson(s.code, DEFAULT_BEG);
            if (cnt > 0) {
                sb.append(",");
            }
            cnt = cnt + 1;
            if (res.startsWith("{\"error\"")) {
                sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"ok\":false,\"message\":" + StockUtil.js(res) + "}");
            } else {
                StockSeries now = StockStore.find(s.code);
                int changed = 0;
                if (now.date[now.n - 1] != lastBefore) {
                    changed = 1;
                }
                sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"ok\":true,\"newBars\":" + (now.n - before) + ",\"last\":"
                        + now.date[now.n - 1] + ",\"changed\":" + changed + "}");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
