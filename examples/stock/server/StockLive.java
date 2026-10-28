import jnative.http.*;

/**
 * Real-time quotes, intraday ("分时") curves and minute K-lines.
 *
 * Quote + intraday come from one Tencent request (web.ifzq.gtimg.cn minute/query, UTF-8 JSON, a few seconds delayed).
 * Minute bars (5/15/30/60) come from Sina's K-line endpoint (unadjusted). Everything is cached for a few seconds so
 * UI polling cannot hammer the upstream, and a failure never takes the server down (callers get an error JSON).
 * @author 张晓达
 */
class LiveSnap {
    String code;
    String name;
    double price;
    double prev;
    double open;
    double high;
    double low;
    double change;
    double pct;
    double volume;
    double amount;
    double turnover;
    double pe;
    double pb;
    double limitUp;
    double limitDown;
    double volRatio;
    double mktCap;
    long timeStamp;
    int date;
    ArrayList<String> bid;
    ArrayList<String> ask;
    ArrayList<String> rows;
    long fetchedAt;
}

class StockLive {
    static HashMap<String, LiveSnap> snaps;
    static HashMap<String, StockSeries> minSeries;
    static HashMap<String, String> minAt;
    static int SNAP_TTL_MS = 4000;

    static void init() {
        snaps = new HashMap<String, LiveSnap>();
        minSeries = new HashMap<String, StockSeries>();
        minAt = new HashMap<String, String>();
    }

    static double d(ArrayList<String> q, int i) {
        if (i >= q.size()) {
            return 0.0;
        }
        return StockUtil.parseDouble(q.get(i), 0.0);
    }

    static String s(ArrayList<String> q, int i) {
        if (i >= q.size()) {
            return "";
        }
        return q.get(i);
    }

    /** Cached or freshly fetched snapshot; null on failure (reason in lastError). */
    static String lastError = "";

    static LiveSnap snap(String rawCode) {
        FeedSym f = StockFeed.resolve(rawCode);
        if (f == null) {
            lastError = "not an A-share code";
            return null;
        }
        long now = System.currentTimeMillis();
        LiveSnap hit = snaps.get(f.code);
        if (hit != null && now - hit.fetchedAt < (long) SNAP_TTL_MS) {
            return hit;
        }
        if (now < tencentDownUntil) {
            return snapFallback(f, hit, now);
        }
        String body = null;
        try {
            body = StockFeed.httpGet("https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=" + f.sina, "https://gu.qq.com/");
        } catch (Exception ex) {
            /* blocked or down (e.g. the upstream WAF answers 501): stop asking for a minute and use the Sina quote instead */
            tencentDownUntil = now + 60000L;
            lastError = "quote request failed";
            return snapFallback(f, hit, now);
        }
        int qi = body.indexOf("\"" + f.sina + "\":[\"");
        if (qi < 0) {
            lastError = "no quote data for " + f.code;
            return snapFallback(f, hit, now);
        }
        ArrayList<String> qq = StockFeed.quoted(body.substringFrom(qi), 70);
        /* qq[0] is the symbol key, the quote fields start at qq[1] */
        ArrayList<String> q = new ArrayList<String>();
        int i = 1;
        while (i < qq.size()) {
            q.add(qq.get(i));
            i = i + 1;
        }
        if (q.size() < 35 || d(q, 3) <= 0.0) {
            lastError = "empty quote for " + f.code;
            return snapFallback(f, hit, now);
        }
        LiveSnap n = new LiveSnap();
        n.code = f.code;
        n.name = StockFeed.unescapeU(s(q, 1));
        n.price = d(q, 3);
        n.prev = d(q, 4);
        n.open = d(q, 5);
        n.volume = d(q, 6) * 100.0;
        n.change = d(q, 31);
        n.pct = d(q, 32) / 100.0;
        n.high = d(q, 33);
        n.low = d(q, 34);
        n.amount = d(q, 37) * 10000.0;
        n.turnover = d(q, 38) / 100.0;
        n.pe = d(q, 39);
        n.mktCap = d(q, 45) * 100000000.0;
        n.pb = d(q, 46);
        n.limitUp = d(q, 47);
        n.limitDown = d(q, 48);
        n.volRatio = d(q, 49);
        String ts = s(q, 30);
        n.timeStamp = 0L;
        n.date = 0;
        if (ts.length() >= 8 && StockCsv.digitsOnly(ts) == 1) {
            n.date = StockUtil.parseInt(ts.substring(0, 8), 0);
            if (ts.length() == 14) {
                n.timeStamp = (long) n.date * 1000000L + (long) StockUtil.parseInt(ts.substring(8, 14), 0);
            }
        }
        n.bid = new ArrayList<String>();
        n.ask = new ArrayList<String>();
        int k = 0;
        while (k < 5) {
            n.bid.add(s(q, 9 + k * 2) + "," + s(q, 10 + k * 2));
            n.ask.add(s(q, 19 + k * 2) + "," + s(q, 20 + k * 2));
            k = k + 1;
        }
        n.rows = new ArrayList<String>();
        int di = body.indexOf("\"data\":[\"");
        if (di >= 0) {
            String rest = body.substringFrom(di + 8);
            int e = rest.indexOf("]");
            if (e > 0) {
                n.rows = StockFeed.quoted(rest.substring(0, e), 400);
            }
        }
        n.fetchedAt = now;
        snaps.put(f.code, n);
        return n;
    }

    static long tencentDownUntil = 0L;
    /** A quote older than this is never served as "current" when the upstream keeps failing. */
    static long STALE_MS = 30000L;

    /** Tencent failed: take the quote from Sina (price, bid/ask, volume; no intraday ticks, PE/PB or turnover), else a recent cached one. */
    static LiveSnap snapFallback(FeedSym f, LiveSnap hit, long now) {
        LiveSnap n = null;
        try {
            n = fetchSina(f, hit, now);
        } catch (Exception ex) {
            lastError = "quote request failed";
            System.out.println("STOCK_SINA_QUOTE_WARN " + ex.getMessage());
        }
        if (n != null) {
            snaps.put(f.code, n);
            return n;
        }
        if (hit != null && now - hit.fetchedAt < STALE_MS) {
            return hit;
        }
        return null;
    }

    static ArrayList<String> splitComma(String t) {
        ArrayList<String> out = new ArrayList<String>();
        int st = 0;
        int i = 0;
        while (i <= t.length()) {
            if (i == t.length() || t.charAt(i) == 44) {
                out.add(t.substring(st, i));
                st = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static double limitRate(String code, String name) {
        if (name != null && name.indexOf("ST") >= 0) {
            return 0.05;
        }
        if (code.startsWith("sh688") || code.startsWith("sh689") || code.startsWith("sz300") || code.startsWith("sz301")) {
            return 0.2;
        }
        if (code.startsWith("bj")) {
            return 0.3;
        }
        return 0.1;
    }

    static LiveSnap fetchSina(FeedSym f, LiveSnap old, long now) {
        HttpRequest req = HttpRequest.create("GET", "https://hq.sinajs.cn/list=" + f.sina);
        req.http1Only = 1;
        req.header("User-Agent", StockFeed.UA);
        req.header("Referer", "https://finance.sina.com.cn/");
        HttpResponse r = HttpClient.send(req);
        if (r.statusCode != 200) {
            lastError = "quote request failed";
            return null;
        }
        /* the body is GBK: replace every non-ASCII byte so that it decodes cleanly (only the name field is affected) */
        byte[] raw = r.body;
        byte[] cp = new byte[raw.length];
        int bi = 0;
        while (bi < raw.length) {
            if (raw[bi] < 0) {
                cp[bi] = 63;
            } else {
                cp[bi] = raw[bi];
            }
            bi = bi + 1;
        }
        String body = Bytes.toString(cp);
        int a = body.indexOf("=\"");
        if (a < 0) {
            return null;
        }
        int b = body.indexOf("\";", a);
        if (b < 0) {
            return null;
        }
        ArrayList<String> q = splitComma(body.substring(a + 2, b));
        if (q.size() < 32) {
            lastError = "empty quote for " + f.code;
            return null;
        }
        double price = d(q, 3);
        double prev = d(q, 2);
        if (price <= 0.0 && d(q, 6) > 0.0) {
            price = d(q, 6);
        }
        if (price <= 0.0 || prev <= 0.0) {
            lastError = "empty quote for " + f.code;
            return null;
        }
        LiveSnap n = new LiveSnap();
        n.code = f.code;
        /* the Sina name field is GBK and is not decoded here: reuse a known name */
        n.name = f.code;
        if (old != null && old.name != null && old.name.length() > 0) {
            n.name = old.name;
        } else {
            StockSeries ss = StockStore.find(f.code);
            if (ss != null && ss.name != null && ss.name.length() > 0) {
                n.name = ss.name;
            }
        }
        if (n.name.equals(f.code)) {
            n.name = StockFeed.nameFor(f.code);
        }
        n.price = price;
        n.prev = prev;
        n.open = d(q, 1);
        n.high = d(q, 4);
        n.low = d(q, 5);
        n.volume = d(q, 8);
        n.amount = d(q, 9);
        n.change = price - prev;
        n.pct = (price - prev) / prev;
        double rate = limitRate(f.code, n.name);
        n.limitUp = Math.round(prev * (1.0 + rate) * 100.0) / 100.0;
        n.limitDown = Math.round(prev * (1.0 - rate) * 100.0) / 100.0;
        n.volRatio = 0.0;
        n.turnover = 0.0;
        n.pe = 0.0;
        n.pb = 0.0;
        n.mktCap = 0.0;
        String ds = q.get(30);
        String tm = q.get(31);
        n.date = 0;
        n.timeStamp = 0L;
        if (ds.length() == 10) {
            n.date = StockUtil.parseInt(ds.substring(0, 4) + ds.substring(5, 7) + ds.substring(8, 10), 0);
        }
        if (n.date > 0 && tm.length() == 8) {
            n.timeStamp = (long) n.date * 1000000L + (long) StockUtil.parseInt(tm.substring(0, 2) + tm.substring(3, 5) + tm.substring(6, 8), 0);
        }
        n.bid = new ArrayList<String>();
        n.ask = new ArrayList<String>();
        int k = 0;
        while (k < 5) {
            n.bid.add(q.get(11 + k * 2) + "," + StockUtil.num(d(q, 10 + k * 2) / 100.0, 0));
            n.ask.add(q.get(21 + k * 2) + "," + StockUtil.num(d(q, 20 + k * 2) / 100.0, 0));
            k = k + 1;
        }
        n.rows = new ArrayList<String>();
        n.fetchedAt = now;
        return n;
    }

    /** Session state from the quote's own timestamp: pre | auction | open | lunch | closed | holiday. */
    static String status(LiveSnap n) {
        long now = StockFeed.nowCst();
        long today = now / 10000L;
        long hm = now % 10000L;
        if (n != null && n.date != 0 && (long) n.date != today) {
            return "holiday";
        }
        if (StockFeed.nowDow() >= 5) {
            return "holiday";
        }
        if (hm < 915L) {
            return "pre";
        }
        if (hm < 930L) {
            return "auction";
        }
        if (hm < 1130L) {
            return "open";
        }
        if (hm < 1300L) {
            return "lunch";
        }
        if (hm < 1500L) {
            return "open";
        }
        return "closed";
    }

    static String quoteJson(LiveSnap n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"code\":" + StockUtil.js(n.code) + ",\"name\":" + StockUtil.js(n.name) + ",\"price\":" + StockUtil.num(n.price, 3)
                + ",\"prevClose\":" + StockUtil.num(n.prev, 3) + ",\"open\":" + StockUtil.num(n.open, 3) + ",\"high\":" + StockUtil.num(n.high, 3)
                + ",\"low\":" + StockUtil.num(n.low, 3) + ",\"change\":" + StockUtil.num(n.change, 3) + ",\"pct\":" + StockUtil.num(n.pct, 5)
                + ",\"volume\":" + StockUtil.num(n.volume, 0) + ",\"amount\":" + StockUtil.num(n.amount, 0) + ",\"turnover\":" + StockUtil.num(n.turnover, 5)
                + ",\"pe\":" + StockUtil.num(n.pe, 2) + ",\"pb\":" + StockUtil.num(n.pb, 2) + ",\"mktCap\":" + StockUtil.num(n.mktCap, 0)
                + ",\"limitUp\":" + StockUtil.num(n.limitUp, 2) + ",\"limitDown\":" + StockUtil.num(n.limitDown, 2) + ",\"volRatio\":" + StockUtil.num(n.volRatio, 2)
                + ",\"date\":" + n.date + ",\"time\":" + n.timeStamp + ",\"status\":" + StockUtil.js(status(n)));
        sb.append(",\"bid\":[");
        int i = 0;
        while (i < n.bid.size()) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(pairJson(n.bid.get(i)));
            i = i + 1;
        }
        sb.append("],\"ask\":[");
        i = 0;
        while (i < n.ask.size()) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(pairJson(n.ask.get(i)));
            i = i + 1;
        }
        sb.append("]}");
        return sb.toString();
    }

    static String pairJson(String pv) {
        int c = pv.indexOf(",");
        String p = pv.substring(0, c);
        String v = pv.substring(c + 1, pv.length());
        if (p.length() == 0 || StockUtil.parseDouble(p, 0.0) <= 0.0) {
            return "[null,null]";
        }
        return "[" + StockUtil.num(StockUtil.parseDouble(p, 0.0), 3) + "," + StockUtil.num(StockUtil.parseDouble(v, 0.0), 0) + "]";
    }

    /** Intraday curve: minute price, per-minute volume (lots), running average price. Times as hhmm. */
    static String intradayJson(LiveSnap n) {
        StringBuilder t = new StringBuilder();
        StringBuilder p = new StringBuilder();
        StringBuilder v = new StringBuilder();
        StringBuilder a = new StringBuilder();
        double prevCum = 0.0;
        int cnt = 0;
        int i = 0;
        while (i < n.rows.size()) {
            String row = n.rows.get(i);
            i = i + 1;
            ArrayList<String> c = StockCsv.splitSpaces(row);
            if (c.size() < 4) {
                continue;
            }
            int hm = StockUtil.parseInt(c.get(0), -1);
            if (hm < 930 || hm > 1500) {
                continue;
            }
            double px = StockUtil.parseDouble(c.get(1), 0.0);
            double cumVol = StockUtil.parseDouble(c.get(2), 0.0);
            double cumAmt = StockUtil.parseDouble(c.get(3), 0.0);
            double vol = cumVol - prevCum;
            if (vol < 0.0) {
                vol = 0.0;
            }
            prevCum = cumVol;
            double avg = px;
            if (cumVol > 0.0) {
                avg = cumAmt / (cumVol * 100.0);
            }
            if (cnt > 0) {
                t.append(",");
                p.append(",");
                v.append(",");
                a.append(",");
            }
            t.append("" + hm);
            p.append(StockUtil.num(px, 3));
            v.append(StockUtil.num(vol, 0));
            a.append(StockUtil.num(avg, 3));
            cnt = cnt + 1;
        }
        return "{\"code\":" + StockUtil.js(n.code) + ",\"name\":" + StockUtil.js(n.name) + ",\"date\":" + n.date + ",\"prevClose\":"
                + StockUtil.num(n.prev, 3) + ",\"limitUp\":" + StockUtil.num(n.limitUp, 2) + ",\"limitDown\":" + StockUtil.num(n.limitDown, 2)
                + ",\"status\":" + StockUtil.js(status(n)) + ",\"t\":[" + t.toString() + "],\"p\":[" + p.toString() + "],\"v\":["
                + v.toString() + "],\"a\":[" + a.toString() + "]}";
    }

    static String quotesJson(String codes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int cnt = 0;
        int st = 0;
        int i = 0;
        while (i <= codes.length()) {
            if (i == codes.length() || codes.charAt(i) == 44) {
                String c = codes.substring(st, i).trim();
                st = i + 1;
                if (c.length() > 0 && cnt < 24) {
                    StockSeries ser = StockStore.find(c);
                    if ((ser != null && ser.source.equals("sina")) || (ser == null && StockFeed.resolve(c) != null)) {
                        LiveSnap n = snap(c);
                        if (n != null) {
                            if (cnt > 0) {
                                sb.append(",");
                            }
                            sb.append("{\"code\":" + StockUtil.js(c) + ",\"price\":" + StockUtil.num(n.price, 3) + ",\"pct\":" + StockUtil.num(n.pct, 5)
                                    + ",\"prev\":" + StockUtil.num(n.prev, 3) + ",\"limitUp\":" + StockUtil.num(n.limitUp, 2) + ",\"limitDown\":" + StockUtil.num(n.limitDown, 2)
                                    + ",\"open\":" + StockUtil.num(n.open, 3) + ",\"time\":" + n.timeStamp + ",\"status\":" + StockUtil.js(status(n)) + "}");
                            cnt = cnt + 1;
                        }
                    }
                }
            }
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Daily series plus today's in-progress bar built from the live quote (null when there is nothing newer than the last daily bar).
     * The daily series is forward-adjusted so its latest prices are raw prices, which is the scale of the live quote.
     */
    static StockSeries withLiveBar(StockSeries s) {
        if (!s.source.equals("sina") || s.hm != null) {
            return null;
        }
        LiveSnap q = snap(s.code);
        if (q == null || q.price <= 0.0 || q.open <= 0.0 || q.date <= s.date[s.n - 1]) {
            return null;
        }
        int n = s.n;
        StockSeries t = new StockSeries(s.code, s.name, s.market, s.source, n + 1);
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
        t.date[n] = q.date;
        t.o[n] = q.open;
        t.h[n] = Math.max(q.high, Math.max(q.open, q.price));
        t.l[n] = Math.min(q.low, Math.min(q.open, q.price));
        if (q.low <= 0.0) {
            t.l[n] = Math.min(q.open, q.price);
        }
        t.c[n] = q.price;
        t.v[n] = q.volume;
        t.version = s.version;
        return t;
    }

    // ---------- minute K-lines ----------
    static HashMap<String, String> predSig;
    static HashMap<String, String> predVer;
    static int verCounter = 1;

    /**
     * Series used for prediction: only completed bars (a bar stamped with its end time is complete once that time has
     * passed), with a version that changes only when a new bar arrives, so the walk-forward cache is not recomputed on every poll.
     */
    static StockSeries predictSeries(StockSeries ms) {
        if (predSig == null) {
            predSig = new HashMap<String, String>();
            predVer = new HashMap<String, String>();
        }
        long cst = StockFeed.nowCst();
        int today = (int) (cst / 10000L);
        int hm = (int) (cst % 10000L);
        int n = ms.n;
        while (n > 0 && ms.date[n - 1] == today && ms.hm[n - 1] > hm) {
            n = n - 1;
        }
        StockSeries t = new StockSeries(ms.code, ms.name, ms.market, ms.source, n);
        t.period = ms.period;
        t.hm = new int[n];
        int i = 0;
        while (i < n) {
            t.date[i] = ms.date[i];
            t.hm[i] = ms.hm[i];
            t.o[i] = ms.o[i];
            t.h[i] = ms.h[i];
            t.l[i] = ms.l[i];
            t.c[i] = ms.c[i];
            t.v[i] = ms.v[i];
            i = i + 1;
        }
        String key = ms.code + "|" + ms.period;
        String sig = n + "|" + t.date[n - 1] + "|" + t.hm[n - 1] + "|" + t.date[0] + "|" + t.hm[0];
        String old = predSig.get(key);
        if (old == null || !old.equals(sig)) {
            verCounter = verCounter + 1;
            predSig.put(key, sig);
            predVer.put(key, "" + verCounter);
        }
        t.version = StockUtil.parseInt(predVer.get(key), 1);
        return t;
    }

    static int validPeriod(int p) {
        if (p == 5 || p == 15 || p == 30 || p == 60) {
            return 1;
        }
        return 0;
    }

    static StockSeries minuteSeries(FeedSym f, int period, String name) {
        String key = f.code + "|" + period;
        long now = System.currentTimeMillis();
        StockSeries hit = minSeries.get(key);
        String at = minAt.get(key);
        long ttl = 300000L;
        long cst = StockFeed.nowCst();
        long hm = cst % 10000L;
        if (StockFeed.nowDow() < 5 && hm >= 925L && hm <= 1510L) {
            ttl = 8000L;
        }
        if (hit != null && at != null && now - StockUtil.parseLong(at, 0L) < ttl) {
            return hit;
        }
        String body = null;
        try {
            body = StockFeed.httpGet("http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol="
                    + f.sina + "&scale=" + period + "&ma=no&datalen=3000", "https://finance.sina.com.cn/");
        } catch (Exception ex) {
            lastError = "minute request failed";
            return hit;
        }
        ArrayList<String> objs = StockFeed.objects(body);
        if (objs.size() < 10) {
            lastError = "no minute data";
            return hit;
        }
        StockSeries s = new StockSeries(f.code, name, "CN", "sina", objs.size());
        s.period = period;
        s.hm = new int[objs.size()];
        int n = 0;
        int k = 0;
        while (k < objs.size()) {
            String o = objs.get(k);
            k = k + 1;
            String day = StockFeed.field(o, "day");
            if (day.length() < 16) {
                continue;
            }
            int dt = StockCsv.parseDate(day.substring(0, 10));
            int tm = StockUtil.parseInt(day.substring(11, 13), 0) * 100 + StockUtil.parseInt(day.substring(14, 16), 0);
            double op = StockUtil.parseDouble(StockFeed.field(o, "open"), 0.0);
            double cl = StockUtil.parseDouble(StockFeed.field(o, "close"), 0.0);
            double h = StockUtil.parseDouble(StockFeed.field(o, "high"), 0.0);
            double l = StockUtil.parseDouble(StockFeed.field(o, "low"), 0.0);
            if (dt < 0 || op <= 0.0 || cl <= 0.0) {
                continue;
            }
            if (n > 0 && (dt < s.date[n - 1] || (dt == s.date[n - 1] && tm <= s.hm[n - 1]))) {
                continue;
            }
            s.date[n] = dt;
            s.hm[n] = tm;
            s.o[n] = op;
            s.c[n] = cl;
            s.h[n] = Math.max(h, Math.max(op, cl));
            s.l[n] = Math.min(l, Math.min(op, cl));
            if (l <= 0.0) {
                s.l[n] = Math.min(op, cl);
            }
            s.v[n] = StockUtil.parseDouble(StockFeed.field(o, "volume"), 0.0);
            n = n + 1;
        }
        if (n < 10) {
            lastError = "no minute data";
            return hit;
        }
        StockSeries t = new StockSeries(f.code, name, "CN", "sina", n);
        t.period = period;
        t.hm = new int[n];
        int i = 0;
        while (i < n) {
            t.date[i] = s.date[i];
            t.hm[i] = s.hm[i];
            t.o[i] = s.o[i];
            t.h[i] = s.h[i];
            t.l[i] = s.l[i];
            t.c[i] = s.c[i];
            t.v[i] = s.v[i];
            i = i + 1;
        }
        minSeries.put(key, t);
        minAt.put(key, "" + now);
        return t;
    }
}
