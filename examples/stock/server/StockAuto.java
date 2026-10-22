/**
 * Daily automatic scan. After the close (15:10 CST) on a weekday the scanner is started once with the saved settings and,
 * when it has finished, a short summary is pushed through the configured webhook. Settings live in data/_auto.txt:
 * on, hold days, theta, minimum probability, exclusion switch and the day of the last completed automatic scan.
 * @author 张晓达
 */
class StockAuto {
    static int on = 0;
    static int hold = 1;
    static double theta = 0.03;
    static double pMin = 0.3;
    static int excl = 0;
    static int mg = 0;
    static long lastDay = 0L;
    static int loaded = 0;
    static int watchRun = 0;
    static long lastCheck = 0L;
    static String lastMsg = "";

    static void init() {
        loaded = 1;
        try {
            String path = StockStore.dataDir + "/_auto.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path).trim();
            ArrayList<String> f = StockNotify.splitTabs(text);
            if (f.size() >= 6) {
                on = StockUtil.parseInt(f.get(0), 0);
                hold = StockUtil.parseInt(f.get(1), 1);
                theta = StockUtil.parseDouble(f.get(2), 0.03);
                pMin = StockUtil.parseDouble(f.get(3), 0.3);
                excl = StockUtil.parseInt(f.get(4), 0);
                lastDay = StockUtil.parseLong(f.get(5), 0L);
                if (f.size() >= 7) {
                    mg = StockUtil.parseInt(f.get(6), 0);
                }
            }
        } catch (Exception ex) {
            System.out.println("STOCK_AUTO_LOAD_WARN");
        }
    }

    static void save() {
        try {
            if (!Files.exists(FsPath.of(StockStore.dataDir))) {
                Files.createDirectories(FsPath.of(StockStore.dataDir));
            }
            File.writeString(StockStore.dataDir + "/_auto.txt", on + "\t" + hold + "\t" + StockUtil.num(theta, 4) + "\t" + StockUtil.num(pMin, 4) + "\t" + excl + "\t" + lastDay + "\t" + mg + "\n");
        } catch (Exception ex) {
            System.out.println("STOCK_AUTO_SAVE_WARN");
        }
    }

    static String set(int o, double th, double pm, int ex, int hd, int gate) {
        if (loaded == 0) {
            init();
        }
        on = o;
        theta = th;
        pMin = pm;
        excl = ex;
        hold = hd;
        mg = gate;
        save();
        return "";
    }

    static String json() {
        if (loaded == 0) {
            init();
        }
        return "{\"on\":" + on + ",\"hold\":" + hold + ",\"theta\":" + StockUtil.num(theta, 4) + ",\"pmin\":" + StockUtil.num(pMin, 4) + ",\"excl\":" + excl + ",\"mg\":" + mg
                + ",\"lastDay\":" + lastDay + ",\"running\":" + watchRun + ",\"msg\":" + StockUtil.js(lastMsg) + ",\"push\":" + pushable() + "}";
    }

    static int pushable() {
        if (StockNotify.url.length() > 0 && StockNotify.kind.length() > 0) {
            return 1;
        }
        return 0;
    }

    /** Called from the serve loop; does nothing most of the time. */
    static void tick() {
        if (loaded == 0) {
            init();
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck < 5000L) {
            return;
        }
        lastCheck = now;
        if (watchRun > 0) {
            finishCheck();
            return;
        }
        if (on == 0 || StockScan.running() == 1) {
            return;
        }
        long cst = StockFeed.nowCst();
        long today = cst / 10000L;
        long hm = cst % 10000L;
        if (StockFeed.nowDow() >= 5 || hm < 1510L || lastDay == today) {
            return;
        }
        StockScan.mGate = mg;
        String er = StockScan.start(theta, pMin, excl, hold);
        if (er.length() == 0) {
            watchRun = StockScan.runId;
            lastMsg = "已自动开始扫描";
        }
    }

    static void finishCheck() {
        if (StockScan.runId != watchRun) {
            /* somebody started another scan meanwhile; stop watching, try again next trading day */
            watchRun = 0;
            return;
        }
        if (StockScan.running() == 1) {
            return;
        }
        long today = StockFeed.nowCst() / 10000L;
        if (StockScan.phase == StockScan.PHASE_DONE && StockScan.msg.equals("扫描完成")) {
            lastDay = today;
            save();
            lastMsg = "自动扫描完成";
            push(StockScan.summaryText(5));
        } else if (StockScan.phase == StockScan.PHASE_ERROR) {
            lastMsg = "自动扫描失败：" + StockScan.msg;
            push("【JNative 收盘扫描】失败：" + StockScan.msg);
        } else {
            lastMsg = "自动扫描被手动停止";
        }
        watchRun = 0;
    }

    static void push(String text) {
        if (pushable() == 0) {
            return;
        }
        String r = StockNotify.send(text);
        StockNotify.addEvent("-", "收盘扫描", text, r);
    }
}
