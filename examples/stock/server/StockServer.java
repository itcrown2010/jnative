import jnative.web.*;
import jnative.json.*;

/**
 * JNative Stock Lab: A-share daily-bar analysis, honest probabilistic prediction with walk-forward validation,
 * cost-aware backtests, watchlist and alerts.
 *
 * Usage: stock-server [port] [dataDir] [publicDir]   (defaults: 18900 data public, relative to the working directory)
 * @author 张晓达
 */
public class StockServer {
    public static void main(String[] args) {
        int port = 18900;
        String dataDir = "data";
        String publicDir = "public";
        if (args.length > 0) {
            port = StockUtil.parseInt(args[0], 18900);
        }
        if (args.length > 1) {
            dataDir = args[1];
        }
        if (args.length > 2) {
            publicDir = args[2];
        }
        StockFeed.init();
        StockLive.init();
        StockStore.init(dataDir);
        StockApi.init();
        StockNotify.init();
        StockTrack.init();
        WebApp app = new WebApp();
        app.config.keepAliveMax = 1;
        app.config.readTimeoutMs = 0;
        app.config.maxBodyBytes = 16 * 1024 * 1024;
        app.get("/api/ping", new StockRoute(0));
        app.get("/api/symbols", new StockRoute(1));
        app.get("/api/bars", new StockRoute(2));
        app.get("/api/predict", new StockRoute(3));
        app.get("/api/backtest", new StockRoute(4));
        app.post("/api/import", new StockRoute(5));
        app.post("/api/symbol/delete", new StockRoute(6));
        app.get("/api/watch", new StockRoute(7));
        app.post("/api/watch/add", new StockRoute(8));
        app.post("/api/watch/remove", new StockRoute(9));
        app.get("/api/alerts", new StockRoute(10));
        app.post("/api/alerts/add", new StockRoute(11));
        app.post("/api/alerts/remove", new StockRoute(12));
        app.get("/api/feed/search", new StockRoute(13));
        app.post("/api/feed/add", new StockRoute(14));
        app.post("/api/feed/refresh", new StockRoute(15));
        app.get("/api/notify", new StockRoute(20));
        app.post("/api/notify/set", new StockRoute(21));
        app.post("/api/notify/test", new StockRoute(22));
        app.post("/api/notify/clear", new StockRoute(23));
        app.get("/api/events", new StockRoute(24));
        app.post("/api/scan/start", new StockRoute(25));
        app.post("/api/scan/stop", new StockRoute(26));
        app.get("/api/scan/status", new StockRoute(27));
        app.get("/api/scan/track", new StockRoute(28));
        app.get("/api/quote", new StockRoute(16));
        app.get("/api/intraday", new StockRoute(17));
        app.get("/api/quotes", new StockRoute(18));
        app.get("/api/minute", new StockRoute(19));
        app.staticFiles("/", publicDir);
        System.out.println("STOCK symbols=" + StockStore.all.size() + " data=" + dataDir + " public=" + publicDir);
        StockHttp.serve(app, port);
    }
}

/** Single-threaded accept/serve loop (no worker pool): the runtime GC is only reliable with one mutator thread. */
class StockHttp {
    static void serve(WebApp app, int port) {
        WebServer ws = new WebServer(app);
        app.server = ws;
        ws.http = HttpServer.listen("0.0.0.0", port);
        System.out.println("JNative Stock on http://localhost:" + port);
        int alive = 1;
        while (alive != 0) {
            HttpConnection conn = null;
            int ok = 1;
            try {
                conn = ws.http.accept();
            } catch (Exception ex) {
                ok = 0;
            }
            if (ok != 0 && conn != null) {
                ws.activeConns = 1;
                ws.serveConnection(conn);
            }
            StockNotify.tick();
            long scanT0 = System.currentTimeMillis();
            while (StockScan.running() == 1 && System.currentTimeMillis() - scanT0 < 400L) {
                StockScan.step();
            }
            if (StockScan.running() == 0) {
                StockTrack.verifyStep();
            }
        }
    }
}

class StockRoute implements WebHandler {
    int kind;

    StockRoute(int kind) {
        this.kind = kind;
    }

    void reply(WebContext ctx, String json) {
        if (json.startsWith("{\"error\"")) {
            ctx.status(400);
        }
        ctx.jsonString(json);
    }

    public void handle(WebContext ctx) {
        ctx.setHeader("Cache-Control", "no-store");
        if (kind == 0) {
            ctx.jsonString("{\"ok\":true,\"symbols\":" + StockStore.all.size() + ",\"cst\":" + StockFeed.nowCst() + "}");
            return;
        }
        if (kind == 1) {
            ctx.jsonString(StockApi.symbolsJson());
            return;
        }
        if (kind == 7) {
            ctx.jsonString(StockApi.watchJson());
            return;
        }
        if (kind == 10) {
            ctx.jsonString(StockApi.alertsJson());
            return;
        }
        if (kind == 5) {
            doImport(ctx);
            return;
        }
        if (kind == 13) {
            String q = ctx.query("q");
            if (q == null || q.trim().length() == 0) {
                reply(ctx, StockUtil.err("BAD_QUERY", "q is required"));
                return;
            }
            reply(ctx, StockFeed.searchJson(q));
            return;
        }
        if (kind == 14) {
            reply(ctx, StockFeed.addJson(ctx.query("code"), StockFeed.DEFAULT_BEG));
            return;
        }
        if (kind == 15) {
            String c = ctx.query("code");
            if (c == null) {
                c = "ALL";
            }
            ctx.jsonString(StockFeed.refreshJson(c));
            return;
        }
        if (kind == 25) {
            double th = StockUtil.parseDouble(ctx.query("theta"), 0.05);
            double pm = StockUtil.parseDouble(ctx.query("pmin"), 0.3);
            if (th < 0.02 || th > 0.2 || pm < 0.02 || pm > 0.95) {
                reply(ctx, StockUtil.err("BAD_SCAN", "theta must be within 2%-20% and pmin within 2%-95%"));
                return;
            }
            int ex = 0;
            if (StockUtil.parseInt(ctx.query("excl"), 0) == 1) {
                ex = 1;
            }
            int hd = StockUtil.parseInt(ctx.query("h"), 5);
            if (hd != 1 && hd != 3 && hd != 5 && hd != 10) {
                reply(ctx, StockUtil.err("BAD_SCAN", "h must be 1, 3, 5 or 10"));
                return;
            }
            String er = StockScan.start(th, pm, ex, hd);
            if (er.length() > 0) {
                reply(ctx, StockUtil.err("BUSY", er));
                return;
            }
            ctx.jsonString(StockScan.statusJson(0));
            return;
        }
        if (kind == 28) {
            ctx.jsonString(StockTrack.trackJson());
            return;
        }
        if (kind == 26) {
            StockScan.stop();
            ctx.jsonString(StockScan.statusJson(0));
            return;
        }
        if (kind == 27) {
            ctx.jsonString(StockScan.statusJson(StockUtil.parseInt(ctx.query("since"), 0)));
            return;
        }
        if (kind == 20) {
            ctx.jsonString(StockNotify.configJson());
            return;
        }
        if (kind == 21) {
            String err = StockNotify.setConfig(ctx.query("kind"), ctx.bodyString());
            if (err.length() > 0) {
                reply(ctx, StockUtil.err("BAD_NOTIFY", err));
                return;
            }
            ctx.jsonString(StockNotify.configJson());
            return;
        }
        if (kind == 22) {
            String st = StockNotify.send("【股票提醒】这是一条测试消息，说明推送通道配置成功。");
            if (st.equals("ok")) {
                ctx.jsonString("{\"ok\":true}");
            } else if (st.equals("none")) {
                reply(ctx, StockUtil.err("NOT_CONFIGURED", "no notification channel configured"));
            } else {
                reply(ctx, StockUtil.err("SEND_FAILED", st));
            }
            return;
        }
        if (kind == 23) {
            StockNotify.clearConfig();
            ctx.jsonString(StockNotify.configJson());
            return;
        }
        if (kind == 24) {
            ctx.jsonString(StockNotify.eventsJson());
            return;
        }
        if (kind == 18) {
            String cs = ctx.query("codes");
            if (cs == null) {
                cs = "";
            }
            ctx.jsonString(StockLive.quotesJson(cs));
            return;
        }
        if (kind == 16 || kind == 17) {
            LiveSnap n = StockLive.snap(ctx.query("code"));
            if (n == null) {
                reply(ctx, StockUtil.err("NO_QUOTE", StockLive.lastError));
                return;
            }
            if (kind == 16) {
                ctx.jsonString(StockLive.quoteJson(n));
            } else {
                ctx.jsonString(StockLive.intradayJson(n));
            }
            return;
        }
        if (kind == 19) {
            FeedSym fs = StockFeed.resolve(ctx.query("code"));
            int period = StockUtil.parseInt(ctx.query("period"), 5);
            if (fs == null || StockLive.validPeriod(period) == 0) {
                reply(ctx, StockUtil.err("BAD_REQUEST", "code must be an A-share code and period one of 5,15,30,60"));
                return;
            }
            StockSeries known = StockStore.find(fs.code);
            String nm = fs.code;
            if (known != null) {
                nm = known.name;
            }
            StockSeries ms = StockLive.minuteSeries(fs, period, nm);
            if (ms == null) {
                reply(ctx, StockUtil.err("NO_MINUTE_DATA", StockLive.lastError));
                return;
            }
            int lim = StockUtil.parseInt(ctx.query("limit"), 1200);
            if (lim < 30) {
                lim = 30;
            }
            ctx.jsonString(StockApi.barsJson(ms, lim));
            return;
        }
        if (kind == 11) {
            doAlertAdd(ctx);
            return;
        }
        if (kind == 12) {
            int id = StockUtil.parseInt(ctx.query("id"), -1);
            ctx.jsonString("{\"removed\":" + StockApi.removeAlert(id) + "}");
            return;
        }
        String code = ctx.query("code");
        StockSeries s = StockStore.find(code);
        if (s == null) {
            reply(ctx, StockUtil.err("NOT_FOUND", "unknown symbol"));
            return;
        }
        if (kind == 3 && ctx.query("per") != null && ctx.query("per").length() > 0) {
            int per = StockUtil.parseInt(ctx.query("per"), 0);
            FeedSym fs2 = StockFeed.resolve(s.code);
            if (fs2 == null || !s.source.equals("sina") || StockLive.validPeriod(per) == 0) {
                reply(ctx, StockUtil.err("BAD_REQUEST", "intraday prediction needs a real-data A-share and period 5|15|30|60"));
                return;
            }
            StockSeries ms2 = StockLive.minuteSeries(fs2, per, s.name);
            if (ms2 == null || ms2.n < 200) {
                reply(ctx, StockUtil.err("NO_MINUTE_DATA", StockLive.lastError));
                return;
            }
            int h3 = StockUtil.parseInt(ctx.query("h"), 5);
            if (h3 < 1) {
                h3 = 1;
            }
            if (h3 > 20) {
                h3 = 20;
            }
            ctx.jsonString(StockEval.predictJson(StockLive.predictSeries(ms2), h3, 20));
            return;
        }
        if (kind == 2) {
            int limit = StockUtil.parseInt(ctx.query("limit"), 500);
            if (limit < 30) {
                limit = 30;
            }
            StockSeries lv = null;
            if ("1".equals(ctx.query("live"))) {
                lv = StockLive.withLiveBar(s);
            }
            if (lv != null) {
                String bj = StockApi.barsJson(lv, limit);
                ctx.jsonString(bj.substring(0, bj.length() - 1) + ",\"live\":1}");
            } else {
                ctx.jsonString(StockApi.barsJson(s, limit));
            }
        } else if (kind == 3) {
            int h = StockUtil.parseInt(ctx.query("h"), 5);
            if (h < 1) {
                h = 1;
            }
            if (h > 20) {
                h = 20;
            }
            int fan = StockUtil.parseInt(ctx.query("fan"), 20);
            if (fan < 1) {
                fan = 1;
            }
            if (fan > 60) {
                fan = 60;
            }
            ctx.jsonString(StockEval.predictJson(s, h, fan));
        } else if (kind == 4) {
            String strategy = ctx.query("strategy");
            if (strategy == null) {
                strategy = "buy_hold";
            }
            ParamBag p = new ParamBag();
            p.put("fast", ctx.query("fast"));
            p.put("slow", ctx.query("slow"));
            p.put("period", ctx.query("period"));
            p.put("lo", ctx.query("lo"));
            p.put("hi", ctx.query("hi"));
            p.put("k", ctx.query("k"));
            p.put("horizon", ctx.query("h"));
            p.put("threshold", ctx.query("threshold"));
            int from = StockUtil.parseInt(ctx.query("from"), 0);
            reply(ctx, StockBacktest.run(s, strategy, p, from));
        } else if (kind == 6) {
            StockStore.remove(s.code);
            StockApi.watchRemove(s.code);
            ctx.jsonString("{\"removed\":1}");
        } else if (kind == 8) {
            StockApi.watchAdd(s.code);
            ctx.jsonString("{\"ok\":true}");
        } else if (kind == 9) {
            StockApi.watchRemove(s.code);
            ctx.jsonString("{\"ok\":true}");
        }
    }

    void doImport(WebContext ctx) {
        String code = ctx.query("code");
        String name = ctx.query("name");
        if (StockApi.validCode(code) == 0) {
            reply(ctx, StockUtil.err("BAD_CODE", "code must be 1-16 chars of letters, digits, . - _"));
            return;
        }
        String text = null;
        try {
            text = ctx.bodyString();
        } catch (Exception ex) {
            reply(ctx, StockUtil.err("BAD_ENCODING", "the CSV must be UTF-8 text (convert GBK files first, or use the web importer)"));
            return;
        }
        if (text == null || text.length() < 20) {
            reply(ctx, StockUtil.err("EMPTY_BODY", "POST the CSV text as the request body"));
            return;
        }
        StockCsvResult r = StockCsv.parse(text, code, name);
        if (r.series == null) {
            reply(ctx, StockUtil.err("PARSE_FAILED", r.note));
            return;
        }
        if (r.series.n < 30) {
            reply(ctx, StockUtil.err("TOO_FEW_BARS", "need at least 30 valid bars, got " + r.series.n));
            return;
        }
        r.series.source = "csv";
        StockStore.put(r.series);
        StockStore.saveCsv(r.series);
        StockSeries cur = StockStore.find(code);
        ctx.jsonString("{\"code\":" + StockUtil.js(code) + ",\"bars\":" + cur.n + ",\"total\":" + r.total + ",\"accepted\":" + r.accepted
                + ",\"rejected\":" + r.rejected + ",\"note\":" + StockUtil.js(r.note) + "}");
    }

    void doAlertAdd(WebContext ctx) {
        String code = ctx.query("code");
        String type = ctx.query("type");
        String val = ctx.query("value");
        if (StockStore.find(code) == null) {
            reply(ctx, StockUtil.err("NOT_FOUND", "unknown symbol"));
            return;
        }
        if (StockApi.validAlertType(type) == 0 || val == null) {
            reply(ctx, StockUtil.err("BAD_ALERT", "type must be price|prob|rsi _above|_below and value is required"));
            return;
        }
        double v = StockUtil.parseDouble(val, StockUtil.nan());
        if (StockUtil.isNan(v) != 0) {
            reply(ctx, StockUtil.err("BAD_ALERT", "value is not a number"));
            return;
        }
        int id = StockApi.addAlert(code, type, v);
        ctx.jsonString("{\"id\":" + id + "}");
    }
}
