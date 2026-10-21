import jnative.http.*;

/**
 * Server-side alert monitor and push notifications.
 *
 * The server is single-threaded, so the monitor is not a thread: StockHttp.serve calls tick() after every accepted
 * connection and after every accept timeout (JNATIVE_ACCEPT_TIMEOUT_MS, set by run.sh). Alerts are edge-triggered:
 * a notification is sent when a condition turns from false to true, and re-armed once it turns false again.
 * Channels are incoming-webhook robots (WeCom, DingTalk, Feishu or a generic JSON webhook). The webhook URL is a
 * secret: it lives in data/_notify.txt and is never returned by the API.
 * @author 张晓达
 */
class StockNotify {
    static String kind = "";
    static String url = "";
    static ArrayList<String> events;
    static int nextEvent = 1;
    static long lastTick = 0L;
    static long lastEvalCst = 0L;
    static long lastRefreshDay = 0L;
    static long lastRefreshCount = 0L;

    static void init() {
        events = new ArrayList<String>();
        loadConfig();
        loadEvents();
    }

    // ---------- config ----------
    static void loadConfig() {
        try {
            String path = StockStore.dataDir + "/_notify.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path);
            int nl = text.indexOf("\n");
            if (nl < 0) {
                return;
            }
            kind = text.substring(0, nl).trim();
            url = text.substring(nl + 1, text.length()).trim();
        } catch (Exception ex) {
            System.out.println("STOCK_NOTIFY_LOAD_WARN");
        }
    }

    static void saveConfig() {
        try {
            if (!Files.exists(FsPath.of(StockStore.dataDir))) {
                Files.createDirectories(FsPath.of(StockStore.dataDir));
            }
            File.writeString(StockStore.dataDir + "/_notify.txt", kind + "\n" + url + "\n");
        } catch (Exception ex) {
            System.out.println("STOCK_NOTIFY_SAVE_WARN");
        }
    }

    static int validKind(String k) {
        if (k == null) {
            return 0;
        }
        if (k.equals("wecom") || k.equals("dingtalk") || k.equals("feishu") || k.equals("webhook")) {
            return 1;
        }
        return 0;
    }

    static String hostOf(String u) {
        int i = u.indexOf("://");
        if (i < 0) {
            return "";
        }
        String rest = u.substringFrom(i + 3);
        int j = rest.indexOf("/");
        if (j >= 0) {
            rest = rest.substring(0, j);
        }
        return rest;
    }

    static String configJson() {
        int on = 0;
        if (url.length() > 0 && kind.length() > 0) {
            on = 1;
        }
        return "{\"configured\":" + on + ",\"kind\":" + StockUtil.js(kind) + ",\"host\":" + StockUtil.js(hostOf(url))
                + ",\"lastEval\":" + lastEvalCst + ",\"lastRefreshDay\":" + lastRefreshDay + "}";
    }

    /** Returns an error message, or "" on success. */
    static String setConfig(String k, String u) {
        if (validKind(k) == 0) {
            return "kind must be wecom, dingtalk, feishu or webhook";
        }
        String t = u.trim();
        if (!(t.startsWith("https://") || t.startsWith("http://")) || t.length() > 600 || hostOf(t).length() < 3) {
            return "url must be a full http(s) webhook address";
        }
        int i = 0;
        while (i < t.length()) {
            int c = t.charAt(i);
            if (c <= 32 || c > 126) {
                return "url contains illegal characters";
            }
            i = i + 1;
        }
        kind = k;
        url = t;
        saveConfig();
        return "";
    }

    static void clearConfig() {
        kind = "";
        url = "";
        saveConfig();
    }

    // ---------- sending ----------
    /** Sends one text message through the configured channel. Returns "ok" or "fail: reason". */
    static String send(String text) {
        if (url.length() == 0 || kind.length() == 0) {
            return "none";
        }
        String body = "";
        String t = StockUtil.js(text);
        if (kind.equals("feishu")) {
            body = "{\"msg_type\":\"text\",\"content\":{\"text\":" + t + "}}";
        } else if (kind.equals("webhook")) {
            body = "{\"text\":" + t + ",\"msgtype\":\"text\",\"content\":" + t + "}";
        } else {
            body = "{\"msgtype\":\"text\",\"text\":{\"content\":" + t + "}}";
        }
        try {
            HttpRequest req = HttpRequest.create("POST", url);
            req.http1Only = 1;
            req.header("Content-Type", "application/json; charset=utf-8");
            req.header("User-Agent", "JNative-Stock/1.0");
            req.bodyString(body);
            HttpResponse r = HttpClient.send(req);
            String rb = Bytes.toString(r.body);
            if (r.statusCode < 200 || r.statusCode >= 300) {
                return "fail: HTTP " + r.statusCode;
            }
            if (kind.equals("wecom") || kind.equals("dingtalk")) {
                if (rb.indexOf("\"errcode\":0") >= 0) {
                    return "ok";
                }
                return "fail: " + clip(rb);
            }
            if (kind.equals("feishu")) {
                if (rb.indexOf("\"code\":0") >= 0 || rb.indexOf("\"StatusCode\":0") >= 0) {
                    return "ok";
                }
                return "fail: " + clip(rb);
            }
            return "ok";
        } catch (Exception ex) {
            return "fail: request error";
        }
    }

    static String clip(String s) {
        String t = s.replace("\n", " ").replace("\t", " ");
        if (t.length() > 100) {
            t = t.substring(0, 100);
        }
        return t;
    }

    // ---------- events ----------
    static void loadEvents() {
        try {
            String path = StockStore.dataDir + "/_events.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path);
            int st = 0;
            int i = 0;
            while (i <= text.length()) {
                if (i == text.length() || text.charAt(i) == 10) {
                    String ln = text.substring(st, i);
                    st = i + 1;
                    int tab = ln.indexOf("\t");
                    if (tab > 0) {
                        int id = StockUtil.parseInt(ln.substring(0, tab), 0);
                        if (id > 0) {
                            events.add(ln);
                            if (id >= nextEvent) {
                                nextEvent = id + 1;
                            }
                        }
                    }
                }
                i = i + 1;
            }
        } catch (Exception ex) {
            System.out.println("STOCK_EVENTS_LOAD_WARN");
        }
    }

    static void saveEvents() {
        try {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < events.size()) {
                sb.append(events.get(i) + "\n");
                i = i + 1;
            }
            File.writeString(StockStore.dataDir + "/_events.txt", sb.toString());
        } catch (Exception ex) {
            System.out.println("STOCK_EVENTS_SAVE_WARN");
        }
    }

    static void addEvent(String code, String name, String text, String status) {
        String clean = text.replace("\t", " ").replace("\n", " ");
        events.add(nextEvent + "\t" + StockFeed.nowCst() + "\t" + code + "\t" + name.replace("\t", " ") + "\t" + clean + "\t" + status);
        nextEvent = nextEvent + 1;
        while (events.size() > 200) {
            events.removeAt(0);
        }
        saveEvents();
    }

    /** Newest first, at most 50. */
    static String eventsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int cnt = 0;
        int i = events.size() - 1;
        while (i >= 0 && cnt < 50) {
            String ln = events.get(i);
            i = i - 1;
            ArrayList<String> p = splitTabs(ln);
            if (p.size() < 6) {
                continue;
            }
            if (cnt > 0) {
                sb.append(",");
            }
            sb.append("{\"id\":" + p.get(0) + ",\"ts\":" + p.get(1) + ",\"code\":" + StockUtil.js(p.get(2)) + ",\"name\":" + StockUtil.js(p.get(3))
                    + ",\"text\":" + StockUtil.js(p.get(4)) + ",\"status\":" + StockUtil.js(p.get(5)) + "}");
            cnt = cnt + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    static ArrayList<String> splitTabs(String ln) {
        ArrayList<String> out = new ArrayList<String>();
        int st = 0;
        int i = 0;
        while (i <= ln.length()) {
            if (i == ln.length() || ln.charAt(i) == 9) {
                out.add(ln.substring(st, i));
                st = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    // ---------- monitor ----------
    static int tradingNow() {
        if (StockFeed.nowDow() >= 5) {
            return 0;
        }
        long hm = StockFeed.nowCst() % 10000L;
        if (hm >= 915L && hm <= 1131L) {
            return 1;
        }
        if (hm >= 1300L && hm <= 1501L) {
            return 1;
        }
        return 0;
    }

    /** Called by the serve loop; cheap when it is not yet time. */
    static void tick() {
        long now = System.currentTimeMillis();
        long gap = 600000L;
        if (tradingNow() == 1) {
            gap = 30000L;
        }
        if (lastTick != 0L && now - lastTick < gap) {
            return;
        }
        lastTick = now;
        lastEvalCst = StockFeed.nowCst();
        try {
            refreshAfterClose();
            evaluate();
        } catch (Exception ex) {
            System.out.println("STOCK_MONITOR_WARN");
        }
    }

    /** Once per trading day after 15:10, pull the final daily bar for every watched real-data symbol. */
    static void refreshAfterClose() {
        long cst = StockFeed.nowCst();
        long today = cst / 10000L;
        long hm = cst % 10000L;
        if (StockFeed.nowDow() >= 5 || hm < 1510L || lastRefreshDay == today) {
            return;
        }
        lastRefreshDay = today;
        int i = 0;
        while (i < StockApi.watch.size()) {
            StockSeries s = StockStore.find(StockApi.watch.get(i));
            i = i + 1;
            if (s != null && s.source.equals("sina")) {
                try {
                    StockFeed.refreshJson(s.code);
                } catch (Exception ex) {
                    System.out.println("STOCK_REFRESH_WARN");
                }
            }
        }
    }

    static void evaluate() {
        int changed = 0;
        int i = 0;
        while (i < StockApi.alerts.size()) {
            StockAlert a = StockApi.alerts.get(i);
            i = i + 1;
            if (a.enabled == 0) {
                continue;
            }
            StockSeries s = StockStore.find(a.code);
            if (s == null) {
                continue;
            }
            double cur = StockApi.alertCurrent(a, s);
            if (StockUtil.isNan(cur) != 0) {
                continue;
            }
            int hit = StockApi.alertHit(a, cur);
            if (a.fired < 0) {
                a.fired = hit;
                changed = 1;
            } else if (hit == 1 && a.fired == 0) {
                a.fired = 1;
                changed = 1;
                fire(a, s, cur);
            } else if (hit == 0 && a.fired == 1) {
                a.fired = 0;
                changed = 1;
            }
        }
        if (changed == 1) {
            StockApi.saveState();
        }
    }

    static String valueText(StockAlert a, double v) {
        if (a.type.startsWith("prob")) {
            return StockUtil.num(v * 100.0, 1) + "%";
        }
        if (a.type.startsWith("rsi")) {
            return StockUtil.num(v, 1);
        }
        return StockUtil.num(v, 2);
    }

    static String describe(StockAlert a, StockSeries s, double cur) {
        return "【股票提醒】" + s.name + "（" + s.code + "） " + StockApi.alertTypeName(a.type) + " " + valueText(a, a.value)
                + " 已触发，当前 " + valueText(a, cur);
    }

    static void fire(StockAlert a, StockSeries s, double cur) {
        String text = describe(a, s, cur);
        String st = send(text);
        addEvent(s.code, s.name, text, st);
        System.out.println("STOCK_ALERT_FIRED " + a.id + " " + st);
    }
}
