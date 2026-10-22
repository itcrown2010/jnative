import jnative.http.*;

/**
 * Whole-market scan for "stocks likely to jump tomorrow", with results streamed while the scan runs.
 *
 * What it is: one pooled logistic regression trained on ~300 stocks x ~4 years of daily bars, with label
 * "next-day return >= theta". It is validated on the most recent ~250 sessions that were held out of training, and
 * the validation table (hit rate per probability cut-off vs the base rate) is shown next to the results, so the user
 * can see how much the scores are worth. It is NOT a guarantee: most of the lift for big-move events comes from
 * volatility and limit-up momentum, and a limit-up stock usually cannot be bought at the open.
 *
 * How it runs: the server is single-threaded, so the job is a state machine advanced one small step (one HTTP fetch
 * or one fit) at a time from the serve loop; the browser polls /api/scan/status and appends new hits as they appear.
 * @author 张晓达
 */
class StockScan {
    static int NF2 = 16;
    static int TRAIN_STOCKS = 300;
    static int CAP = 300000;
    static int PHASE_IDLE = 0;
    static int PHASE_UNIVERSE = 1;
    static int PHASE_TRAIN = 2;
    static int PHASE_SCAN = 3;
    static int PHASE_DONE = 4;
    static int PHASE_ERROR = 5;

    static int phase = 0;
    static String msg = "";
    static int runId = 0;
    static long startedMs = 0L;
    static long finishedMs = 0L;
    static double theta = 0.05;
    static double pMin = 0.3;
    static int stopFlag = 0;
    static int excl = 0;
    static double lastBaseTest = 0.0;

    // universe (kept for the day)
    static ArrayList<String> uSym;
    static ArrayList<String> uName;
    static double[] uTrade;
    static int uN = 0;
    static int uPage = 1;
    static long uDay = 0L;

    // progress
    static int pos = 0;
    static int scanned = 0;
    static int failed = 0;
    static int skipped = 0;
    static int consecFail = 0;
    static ArrayList<String> hits;

    // training samples (kept in memory until restart)
    static double[] sx;
    static double[] sr;
    static double[] se;
    static int[] sl;
    static int hold = 5;
    static int hIdx = 1;
    /* market context: Shanghai Composite series and the regime of every sample */
    static int idxN = 0;
    static int[] idxD;
    static double[] idxR1;
    static double[] idxR5;
    static double[] idxDist;
    static int[] sm;
    static int mDate = 0;
    static int mState = -1;
    static double mR1 = 0.0;
    static double mR5 = 0.0;
    static double mDist = 0.0;
    static int mGate = 0;
    static int gated = 0;
    static double[] regN;
    static double[] regHit;
    static double[] regEnd;
    static double[] regHiN;
    static double[] regHiHit;
    static double[] regHiEnd;
    static double[] binEnd;
    static double[] binTp;
    static double[] binTs;
    static double baseTp = 0.0;
    static double baseTs = 0.0;
    static int tIdx = 2;
    static double[] ss;
    static int[] binLoss;
    static double baseEnd = 0.0;
    static int[] sd;
    static int sn = 0;
    static int trainTried = 0;
    static int trainLoaded = 0;
    static int trainStride = 1;
    static int cutDate = 0;
    static int standardized = 0;
    static double[] mu;
    static double[] sg;
    static double[] w;
    static double bias = 0.0;
    static String validation = "null";
    static double[] binCut;
    static double[] binPrec;
    static int[] binN;

    static void ensureInit() {
        if (hits == null) {
            hits = new ArrayList<String>();
            uSym = new ArrayList<String>();
            uName = new ArrayList<String>();
            uTrade = new double[9000];
            mu = new double[NF2];
            sg = new double[NF2];
            w = new double[NF2];
            binCut = new double[7];
            binCut[0] = 0.05;
            binCut[1] = 0.1;
            binCut[2] = 0.15;
            binCut[3] = 0.2;
            binCut[4] = 0.3;
            binCut[5] = 0.4;
            binCut[6] = 0.5;
            binPrec = new double[7];
            binN = new int[7];
            binEnd = new double[7];
            binTp = new double[7];
            binTs = new double[7];
            binLoss = new int[7];
        }
    }

    /** The four take-profit levels for which strategy returns are stored per sample. */
    static double thOf(int i) {
        if (i == 0) {
            return 0.03;
        }
        if (i == 1) {
            return 0.05;
        }
        if (i == 2) {
            return 0.08;
        }
        if (i == 3) {
            return 0.10;
        }
        return 0.15;
    }

    // ---------- control ----------
    static String start(double th, double pm, int ex, int hd) {
        ensureInit();
        if (phase >= PHASE_UNIVERSE && phase <= PHASE_SCAN) {
            return "a scan is already running";
        }
        tIdx = 0;
        double bestD = 9.0;
        int q0 = 0;
        while (q0 < 5) {
            double dd = th - thOf(q0);
            if (dd < 0.0) {
                dd = -dd;
            }
            if (dd < bestD) {
                bestD = dd;
                tIdx = q0;
            }
            q0 = q0 + 1;
        }
        th = thOf(tIdx);
        theta = th;
        pMin = pm;
        excl = ex;
        hold = hd;
        hIdx = 2;
        if (hd == 1) {
            hIdx = 0;
        }
        if (hd == 3) {
            hIdx = 1;
        }
        if (hd == 10) {
            hIdx = 3;
        }
        runId = runId + 1;
        stopFlag = 0;
        hits = new ArrayList<String>();
        pos = 0;
        scanned = 0;
        failed = 0;
        skipped = 0;
        gated = 0;
        consecFail = 0;
        startedMs = System.currentTimeMillis();
        finishedMs = 0L;
        trainTried = 0;
        msg = "";
        long today = StockFeed.nowCst() / 10000L;
        if (uN > 0 && uDay == today) {
            beginTrain();
        } else {
            uSym = new ArrayList<String>();
            uName = new ArrayList<String>();
            uN = 0;
            uPage = 1;
            phase = PHASE_UNIVERSE;
            msg = "正在获取全部A股列表";
        }
        return "";
    }

    static void stop() {
        if (phase >= PHASE_UNIVERSE && phase <= PHASE_SCAN) {
            phase = PHASE_DONE;
            msg = "已手动停止";
            finishedMs = System.currentTimeMillis();
        }
    }

    static int running() {
        if (phase >= PHASE_UNIVERSE && phase <= PHASE_SCAN) {
            return 1;
        }
        return 0;
    }

    /** Called from the serve loop; does at most one network request or one fit. */
    static void step() {
        if (phase < PHASE_UNIVERSE || phase > PHASE_SCAN) {
            return;
        }
        try {
            if (phase == PHASE_UNIVERSE) {
                stepUniverse();
            } else if (phase == PHASE_TRAIN) {
                stepTrain();
            } else {
                stepScan();
            }
        } catch (Exception ex) {
            phase = PHASE_ERROR;
            msg = "内部错误，扫描已中止";
            finishedMs = System.currentTimeMillis();
            System.out.println("STOCK_SCAN_ERROR");
        }
    }

    // ---------- helpers ----------
    static String get1(String url, String referer) {
        HttpRequest req = HttpRequest.create("GET", url);
        req.http1Only = 1;
        req.header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36");
        req.header("Referer", referer);
        HttpResponse r = HttpClient.send(req);
        if (r.statusCode != 200) {
            throw new IOException("HTTP " + r.statusCode);
        }
        return Bytes.toString(r.body);
    }

    static double numField(String obj, String key) {
        String pat = "\"" + key + "\":";
        int i = obj.indexOf(pat);
        if (i < 0) {
            return StockUtil.nan();
        }
        int st = i + pat.length();
        if (st < obj.length() && obj.charAt(st) == 34) {
            st = st + 1;
        }
        int j = st;
        while (j < obj.length()) {
            int c = obj.charAt(j);
            if (c == 44 || c == 125 || c == 34) {
                break;
            }
            j = j + 1;
        }
        return StockUtil.parseDouble(obj.substring(st, j), StockUtil.nan());
    }

    // ---------- phase 1: universe ----------
    static void stepUniverse() {
        String body = null;
        try {
            body = get1("http://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData?page=" + uPage
                    + "&num=100&sort=symbol&asc=1&node=hs_a", "https://finance.sina.com.cn/");
        } catch (Exception ex) {
            consecFail = consecFail + 1;
            if (consecFail >= 5) {
                phase = PHASE_ERROR;
                msg = "无法获取A股列表（数据源连续失败）";
                finishedMs = System.currentTimeMillis();
            }
            return;
        }
        consecFail = 0;
        ArrayList<String> rows = StockFeed.objects(body);
        if (rows.size() == 0 || uPage > 90) {
            if (uN < 500) {
                phase = PHASE_ERROR;
                msg = "A股列表过短（" + uN + "），已中止";
                finishedMs = System.currentTimeMillis();
                return;
            }
            uDay = StockFeed.nowCst() / 10000L;
            beginTrain();
            return;
        }
        int i = 0;
        while (i < rows.size() && uN < 8900) {
            String o = rows.get(i);
            i = i + 1;
            String sym = StockFeed.field(o, "symbol");
            if (sym.length() != 8) {
                continue;
            }
            if (sym.startsWith("bj920") || sym.startsWith("sh688")) {
                continue;
            }
            String nmU = StockFeed.unescapeU(StockFeed.field(o, "name"));
            if (nmU.indexOf("ST") >= 0) {
                continue;
            }
            double tr = numField(o, "trade");
            uSym.add(sym);
            uName.add(nmU);
            if (StockUtil.isNan(tr) != 0) {
                tr = 0.0;
            }
            uTrade[uN] = tr;
            uN = uN + 1;
        }
        msg = "正在获取A股列表：" + uN + " 只";
        uPage = uPage + 1;
    }

    // ---------- phase 2: training ----------
    static void beginTrain() {
        try {
            fetchIndex();
        } catch (Exception ex) {
            phase = PHASE_ERROR;
            msg = "无法获取大盘（上证指数）数据，已中止";
            finishedMs = System.currentTimeMillis();
            return;
        }
        if (sn > 0 && standardized == 1) {
            fitAndValidate();
            return;
        }
        sx = new double[CAP * NF2];
        sr = new double[4 * CAP];
        se = new double[4 * CAP];
        ss = new double[40 * CAP];
        sl = new int[4 * CAP];
        sd = new int[CAP];
        sm = new int[CAP];
        sn = 0;
        trainTried = 0;
        trainLoaded = 0;
        standardized = 0;
        trainStride = uN / TRAIN_STOCKS;
        if (trainStride < 1) {
            trainStride = 1;
        }
        phase = PHASE_TRAIN;
        msg = "正在加载训练样本";
    }

    static StockSeries fetchDaily(String sym, int datalen) {
        String body = get1("http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=" + sym
                + "&scale=240&ma=no&datalen=" + datalen, "https://finance.sina.com.cn/");
        ArrayList<String> objs = StockFeed.objects(body);
        if (objs.size() < 30) {
            return null;
        }
        StockSeries s = new StockSeries(sym, sym, "CN", "sina", objs.size());
        int n = 0;
        int k = 0;
        while (k < objs.size()) {
            String o = objs.get(k);
            k = k + 1;
            n = StockFeed.put(s, n, StockCsv.parseDate(StockFeed.field(o, "day")), StockUtil.parseDouble(StockFeed.field(o, "open"), 0.0),
                    StockUtil.parseDouble(StockFeed.field(o, "high"), 0.0), StockUtil.parseDouble(StockFeed.field(o, "low"), 0.0),
                    StockUtil.parseDouble(StockFeed.field(o, "close"), 0.0), StockUtil.parseDouble(StockFeed.field(o, "volume"), 0.0));
        }
        FeedBars fb = StockFeed.finish(new FeedBars(), s, n, "");
        adjustExRights(fb.series, sym);
        return fb.series;
    }

    /**
     * The scanner works on raw (unadjusted) bars, so a dividend/bonus-share ex-rights day looks like a crash. A one-day fall
     * beyond the board's daily limit is impossible for a normal move, so it is treated as ex-rights: every earlier bar is scaled
     * so that the previous close meets the new close (assumes a flat true return that day; the error is bounded by the limit).
     */
    static void adjustExRights(StockSeries s, String sym) {
        int n = s.n;
        if (n < 3) {
            return;
        }
        double[] fac = new double[n];
        double cum = 1.0;
        int changed = 0;
        int t = n - 1;
        while (t >= 1) {
            fac[t] = cum;
            double lim = limitOf(sym);
            if (lim < 0.15 && s.date[t] < 20200824 && (sym.startsWith("sz300") || sym.startsWith("sz301"))) {
                lim = 0.1;
            }
            if (s.c[t - 1] > 0.0 && s.c[t] > 0.0) {
                double r = s.c[t] / s.c[t - 1];
                if (r < 1.0 - lim - 0.004) {
                    cum = cum * r;
                    changed = 1;
                }
            }
            t = t - 1;
        }
        fac[0] = cum;
        if (changed == 0) {
            return;
        }
        t = 0;
        while (t < n) {
            double fv = fac[t];
            s.o[t] = s.o[t] * fv;
            s.h[t] = s.h[t] * fv;
            s.l[t] = s.l[t] * fv;
            s.c[t] = s.c[t] * fv;
            t = t + 1;
        }
    }

    static String stateName(int st) {
        if (st == 0) {
            return "强势";
        }
        if (st == 1) {
            return "回调";
        }
        if (st == 2) {
            return "反弹";
        }
        return "弱势";
    }

    /** Loads the Shanghai Composite daily series: 1-day and 5-day return and distance to the 20-day average on every day. */
    static void fetchIndex() {
        StockSeries s = fetchDaily("sh000001", 1000);
        if (s == null || s.n < 60) {
            throw new RuntimeException("index series unavailable");
        }
        int n = s.n;
        idxN = n;
        idxD = new int[n];
        idxR1 = new double[n];
        idxR5 = new double[n];
        idxDist = new double[n];
        int i = 0;
        while (i < n) {
            idxD[i] = s.date[i];
            idxR1[i] = 0.0;
            idxR5[i] = 0.0;
            idxDist[i] = 0.0;
            if (i >= 20 && s.c[i - 1] > 0.0 && s.c[i - 5] > 0.0) {
                double sum = 0.0;
                int k = 0;
                while (k < 20) {
                    sum = sum + s.c[i - k];
                    k = k + 1;
                }
                idxR1[i] = s.c[i] / s.c[i - 1] - 1.0;
                idxR5[i] = s.c[i] / s.c[i - 5] - 1.0;
                idxDist[i] = s.c[i] / (sum / 20.0) - 1.0;
            }
            i = i + 1;
        }
        int last = n - 1;
        mDate = idxD[last];
        mR1 = idxR1[last];
        mR5 = idxR5[last];
        mDist = idxDist[last];
        mState = regimeOf(mR5, mDist);
    }

    static int regimeOf(double r5, double dist) {
        if (dist >= 0.0 && r5 >= 0.0) {
            return 0;
        }
        if (dist >= 0.0) {
            return 1;
        }
        if (r5 >= 0.0) {
            return 2;
        }
        return 3;
    }

    /** Writes the 3 market features of the given date into out[off..] and returns the regime (0..3), or -1 when unknown. */
    static int marketAt(int date, double[] out, int off) {
        int lo = 0;
        int hi = idxN - 1;
        int at = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (idxD[mid] == date) {
                at = mid;
                lo = hi + 1;
            } else if (idxD[mid] < date) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (at < 20) {
            return -1;
        }
        out[off] = StockUtil.clamp(idxR1[at], -0.07, 0.07);
        out[off + 1] = StockUtil.clamp(idxR5[at], -0.15, 0.15);
        out[off + 2] = StockUtil.clamp(idxDist[at], -0.15, 0.15);
        return regimeOf(idxR5[at], idxDist[at]);
    }

    /** Writes the 13 stock features of bar t into out[off..]; returns 0 when any is unusable. */
    static int vec(StockFeatures f, int t, double[] out, int off) {
        int b = t * StockFeatures.NF;
        int k = 0;
        while (k < 11) {
            double v = f.x[b + k];
            if (StockUtil.isNan(v) != 0 || StockUtil.isBad(v) != 0) {
                return 0;
            }
            if (k <= 3) {
                v = StockUtil.clamp(v, -0.5, 1.0);
            } else if (k == 5) {
                v = StockUtil.clamp(v, -0.2, 0.2);
            } else if (k == 6) {
                v = StockUtil.clamp(v, 0.0, 0.2);
            } else if (k == 8) {
                v = StockUtil.clamp(v, -0.5, 1.0);
            }
            out[off + k] = v;
            k = k + 1;
        }
        double r1 = f.x[b];
        out[off + 11] = 0.0;
        out[off + 12] = 0.0;
        if (r1 >= 0.05) {
            out[off + 11] = 1.0;
        }
        if (r1 >= 0.095) {
            out[off + 12] = 1.0;
        }
        return 1;
    }

    /**
     * Label for a buyer who can only buy at the open of the day after the signal (T+1) and can sell from the day after that:
     * for each horizon H in {3, 5, 10} the best close within H days after the entry day, and the close H days after entry,
     * both relative to the entry open. Features are known at the close of day t.
     */
    /** Daily price limit of a board: 20% on ChiNext (300/301), 10% elsewhere (ST stocks are excluded from the universe). */
    static double limitOf(String sym) {
        if (sym.startsWith("sz300") || sym.startsWith("sz301")) {
            return 0.2;
        }
        if (sym.startsWith("bj")) {
            return 0.3;
        }
        return 0.1;
    }

    static void addSamples(StockSeries s, String sym) {
        double lim = limitOf(sym) - 0.005;
        StockFeatures f = StockFeatures.build(s);
        int n = s.n;
        int t = StockFeatures.WARMUP;
        while (t + 11 < n && sn < CAP) {
            double entry = s.o[t + 1];
            /* rows a buyer could not act on: signal day closed at the limit, or the entry day opens at the limit */
            int buyable = 1;
            if (s.c[t - 1] > 0.0 && s.c[t] >= s.c[t - 1] * (1.0 + lim)) {
                buyable = 0;
            }
            if (s.c[t] > 0.0 && entry >= s.c[t] * (1.0 + lim)) {
                buyable = 0;
            }
            int st = -1;
            if (buyable == 1 && entry > 0.0 && vec(f, t, sx, sn * NF2) == 1) {
                st = marketAt(s.date[t], sx, sn * NF2 + 13);
            }
            if (st >= 0) {
                int hi = 0;
                int ok = 1;
                while (hi < 4) {
                    int hh = 1;
                    if (hi == 1) {
                        hh = 3;
                    }
                    if (hi == 2) {
                        hh = 5;
                    }
                    if (hi == 3) {
                        hh = 10;
                    }
                    double mx = -1.0;
                    int kk = 2;
                    while (kk <= 1 + hh) {
                        double r = s.c[t + kk] / entry - 1.0;
                        if (r > mx) {
                            mx = r;
                        }
                        kk = kk + 1;
                    }
                    double end = s.c[t + 1 + hh] / entry - 1.0;
                    if (mx > 1.0 || end < -0.5) {
                        ok = 0;
                    }
                    int ti = 0;
                    while (ti < 5) {
                        double thr = thOf(ti);
                        double tp = end;
                        double ts = end;
                        int dTp = 0;
                        int dTs = 0;
                        int k2 = 2;
                        while (k2 <= 1 + hh) {
                            double r2 = s.c[t + k2] / entry - 1.0;
                            if (r2 >= thr) {
                                if (dTp == 0) {
                                    tp = thr;
                                    dTp = 1;
                                }
                                if (dTs == 0) {
                                    ts = thr;
                                    dTs = 1;
                                }
                            } else if (r2 <= -0.05 && dTs == 0) {
                                ts = r2;
                                dTs = 1;
                            }
                            k2 = k2 + 1;
                        }
                        ss[(hi * 5 + ti) * CAP + sn] = tp;
                        ss[((4 + hi) * 5 + ti) * CAP + sn] = ts;
                        ti = ti + 1;
                    }
                    sr[hi * CAP + sn] = mx;
                    se[hi * CAP + sn] = end;
                    sl[hi * CAP + sn] = s.date[t + 1 + hh];
                    hi = hi + 1;
                }
                if (ok == 1) {
                    sd[sn] = s.date[t];
                    sm[sn] = st;
                    sn = sn + 1;
                }
            }
            t = t + 1;
        }
    }

    static void stepTrain() {
        int idx = trainTried * trainStride;
        if (trainLoaded >= TRAIN_STOCKS || idx >= uN || sn >= CAP - 1100) {
            if (sn < 20000) {
                phase = PHASE_ERROR;
                msg = "训练样本不足（" + sn + "），请稍后重试";
                finishedMs = System.currentTimeMillis();
                return;
            }
            fitAndValidate();
            return;
        }
        trainTried = trainTried + 1;
        if (uTrade[idx] <= 0.0) {
            return;
        }
        try {
            StockSeries s = fetchDaily(uSym.get(idx), 1000);
            if (s != null && s.n > 300) {
                addSamples(s, uSym.get(idx));
                trainLoaded = trainLoaded + 1;
                if (cutDate == 0 && s.n > 600) {
                    cutDate = s.date[s.n - 251];
                }
            }
            consecFail = 0;
        } catch (Exception ex) {
            consecFail = consecFail + 1;
            if (consecFail >= 15) {
                phase = PHASE_ERROR;
                msg = "数据源连续失败，已暂停";
                finishedMs = System.currentTimeMillis();
            }
        }
        msg = "正在加载训练样本：" + trainLoaded + "/" + TRAIN_STOCKS + " 只，累计 " + sn + " 个样本";
    }

    // ---------- model ----------
    static double score(double[] x, int off) {
        double z = bias;
        int k = 0;
        while (k < NF2) {
            z = z + w[k] * (x[off + k] - mu[k]) / sg[k];
            k = k + 1;
        }
        if (z > 30.0) {
            z = 30.0;
        }
        if (z < -30.0) {
            z = -30.0;
        }
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /** With excl=1 rows whose own day already gained >= 5% are left out of training and validation. */
    static int inclRow(int i) {
        if (excl == 1 && sx[i * NF2] * sg[0] + mu[0] >= 0.05) {
            return 0;
        }
        return 1;
    }

    static void fitAndValidate() {
        if (cutDate == 0) {
            int mx = 0;
            int q = 0;
            while (q < sn) {
                if (sd[q] > mx) {
                    mx = sd[q];
                }
                q = q + 1;
            }
            cutDate = mx - 10000;
        }
        int ho = hIdx * CAP;
        int i = 0;
        int k = 0;
        int ntr = 0;
        if (standardized == 0) {
            double[] sum = new double[NF2];
            double[] sq = new double[NF2];
            i = 0;
            while (i < sn) {
                if (sd[i] < cutDate) {
                    k = 0;
                    while (k < NF2) {
                        double v = sx[i * NF2 + k];
                        sum[k] = sum[k] + v;
                        sq[k] = sq[k] + v * v;
                        k = k + 1;
                    }
                    ntr = ntr + 1;
                }
                i = i + 1;
            }
            k = 0;
            while (k < NF2) {
                mu[k] = sum[k] / (double) ntr;
                double vr = sq[k] / (double) ntr - mu[k] * mu[k];
                if (vr < 1.0e-12) {
                    vr = 1.0e-12;
                }
                sg[k] = Math.sqrt(vr);
                k = k + 1;
            }
            i = 0;
            while (i < sn) {
                k = 0;
                while (k < NF2) {
                    sx[i * NF2 + k] = (sx[i * NF2 + k] - mu[k]) / sg[k];
                    k = k + 1;
                }
                i = i + 1;
            }
            /* mu/sg keep the true statistics: rows are standardised in place for training, while score() standardises raw vectors on the fly */
            standardized = 1;
        }
        /* rows are standardised in place; gradient descent below works on them directly */
        ntr = 0;
        int ntrPos = 0;
        i = 0;
        while (i < sn) {
            if (sl[ho + i] < cutDate && inclRow(i) == 1) {
                ntr = ntr + 1;
                if (sr[ho + i] >= theta) {
                    ntrPos = ntrPos + 1;
                }
            }
            i = i + 1;
        }
        double base = (double) ntrPos / (double) ntr;
        if (base < 0.0005) {
            base = 0.0005;
        }
        k = 0;
        while (k < NF2) {
            w[k] = 0.0;
            k = k + 1;
        }
        bias = Math.log(base / (1.0 - base));
        double[] gw = new double[NF2];
        int it = 0;
        while (it < 100) {
            k = 0;
            while (k < NF2) {
                gw[k] = 0.0;
                k = k + 1;
            }
            double gb = 0.0;
            i = 0;
            while (i < sn) {
                if (sl[ho + i] < cutDate && inclRow(i) == 1) {
                    int o = i * NF2;
                    double z = bias;
                    k = 0;
                    while (k < NF2) {
                        z = z + w[k] * sx[o + k];
                        k = k + 1;
                    }
                    double p = 1.0 / (1.0 + Math.exp(-StockUtil.clamp(z, -30.0, 30.0)));
                    double y = 0.0;
                    if (sr[ho + i] >= theta) {
                        y = 1.0;
                    }
                    double e = p - y;
                    k = 0;
                    while (k < NF2) {
                        gw[k] = gw[k] + e * sx[o + k];
                        k = k + 1;
                    }
                    gb = gb + e;
                }
                i = i + 1;
            }
            k = 0;
            while (k < NF2) {
                w[k] = w[k] - 1.0 * (gw[k] / (double) ntr + 0.0002 * w[k]);
                k = k + 1;
            }
            bias = bias - 1.0 * gb / (double) ntr;
            it = it + 1;
        }
        /* validation on the held-out recent sessions */
        int nte = 0;
        int nteHit = 0;
        double brier = 0.0;
        int b = 0;
        double sumEnd = 0.0;
        double sumTp = 0.0;
        double sumTs = 0.0;
        int offTp = (hIdx * 5 + tIdx) * CAP;
        int offTs = ((4 + hIdx) * 5 + tIdx) * CAP;
        while (b < 7) {
            binN[b] = 0;
            binPrec[b] = 0.0;
            binEnd[b] = 0.0;
            binTp[b] = 0.0;
            binTs[b] = 0.0;
            binLoss[b] = 0;
            b = b + 1;
        }
        int[] binHit = new int[7];
        regN = new double[4];
        regHit = new double[4];
        regEnd = new double[4];
        regHiN = new double[4];
        regHiHit = new double[4];
        regHiEnd = new double[4];
        i = 0;
        while (i < sn) {
            if (sd[i] >= cutDate && inclRow(i) == 1) {
                int o = i * NF2;
                double z = bias;
                k = 0;
                while (k < NF2) {
                    z = z + w[k] * sx[o + k];
                    k = k + 1;
                }
                double p = 1.0 / (1.0 + Math.exp(-StockUtil.clamp(z, -30.0, 30.0)));
                double y = 0.0;
                if (sr[ho + i] >= theta) {
                    y = 1.0;
                    nteHit = nteHit + 1;
                }
                double endR = se[ho + i];
                sumEnd = sumEnd + endR;
                double rTp = ss[offTp + i];
                double rTs = ss[offTs + i];
                sumTp = sumTp + rTp;
                sumTs = sumTs + rTs;
                brier = brier + (p - y) * (p - y);
                int rg = sm[i];
                regN[rg] = regN[rg] + 1.0;
                regHit[rg] = regHit[rg] + y;
                regEnd[rg] = regEnd[rg] + endR;
                if (p >= pMin) {
                    regHiN[rg] = regHiN[rg] + 1.0;
                    regHiHit[rg] = regHiHit[rg] + y;
                    regHiEnd[rg] = regHiEnd[rg] + endR;
                }
                nte = nte + 1;
                b = 0;
                while (b < 7) {
                    if (p >= binCut[b]) {
                        binN[b] = binN[b] + 1;
                        binEnd[b] = binEnd[b] + endR;
                        binTp[b] = binTp[b] + rTp;
                        binTs[b] = binTs[b] + rTs;
                        if (endR <= -0.05) {
                            binLoss[b] = binLoss[b] + 1;
                        }
                        if (y > 0.5) {
                            binHit[b] = binHit[b] + 1;
                        }
                    }
                    b = b + 1;
                }
            }
            i = i + 1;
        }
        double baseTest = 0.0;
        double skill = 0.0;
        if (nte > 0) {
            baseTest = (double) nteHit / (double) nte;
            double bq = baseTest * (1.0 - baseTest);
            if (bq > 0.0) {
                skill = 1.0 - (brier / (double) nte) / bq;
            }
        }
        lastBaseTest = baseTest;
        baseEnd = 0.0;
        baseTp = 0.0;
        baseTs = 0.0;
        if (nte > 0) {
            baseEnd = sumEnd / (double) nte;
            baseTp = sumTp / (double) nte;
            baseTs = sumTs / (double) nte;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"hold\":" + hold + ",\"baseEnd\":" + StockUtil.num(baseEnd, 5) + ",\"baseTp\":" + StockUtil.num(baseTp, 5) + ",\"baseTs\":" + StockUtil.num(baseTs, 5) + ",\"excl\":" + excl + ",\"theta\":" + StockUtil.num(theta, 4) + ",\"trainN\":" + ntr + ",\"testN\":" + nte + ",\"stocks\":" + trainLoaded
                + ",\"cutDate\":" + cutDate + ",\"baseTrain\":" + StockUtil.num(base, 5) + ",\"baseTest\":" + StockUtil.num(baseTest, 5)
                + ",\"brierSkill\":" + StockUtil.num(skill, 4) + ",\"bins\":[");
        b = 0;
        while (b < 7) {
            if (binN[b] > 0) {
                binPrec[b] = (double) binHit[b] / (double) binN[b];
            }
            if (b > 0) {
                sb.append(",");
            }
            sb.append("{\"p\":" + StockUtil.num(binCut[b], 2) + ",\"n\":" + binN[b] + ",\"hit\":" + binHit[b] + ",\"precision\":" + StockUtil.num(binPrec[b], 4) + ",\"avgEnd\":" + StockUtil.num(avgEndOf(b), 5) + ",\"tp\":" + StockUtil.num(avgOf(binTp, b), 5) + ",\"tpsl\":" + StockUtil.num(avgOf(binTs, b), 5) + ",\"loss\":" + StockUtil.num(lossOf(b), 4) + "}");
            b = b + 1;
        }
        sb.append("],\"regimes\":[");
        int rg2 = 0;
        while (rg2 < 4) {
            if (rg2 > 0) {
                sb.append(",");
            }
            double rn = regN[rg2];
            double hn = regHiN[rg2];
            double baseR = 0.0;
            double endRg = 0.0;
            double hiPrec = 0.0;
            double hiEndAvg = 0.0;
            if (rn > 0.0) {
                baseR = regHit[rg2] / rn;
                endRg = regEnd[rg2] / rn;
            }
            if (hn > 0.0) {
                hiPrec = regHiHit[rg2] / hn;
                hiEndAvg = regHiEnd[rg2] / hn;
            }
            sb.append("{\"s\":" + rg2 + ",\"n\":" + (int) rn + ",\"base\":" + StockUtil.num(baseR, 4) + ",\"end\":" + StockUtil.num(endRg, 5) + ",\"hiN\":" + (int) hn
                    + ",\"hiPrec\":" + StockUtil.num(hiPrec, 4) + ",\"hiEnd\":" + StockUtil.num(hiEndAvg, 5) + "}");
            rg2 = rg2 + 1;
        }
        sb.append("]}");
        validation = sb.toString();
        pos = 0;
        phase = PHASE_SCAN;
        msg = "正在扫描";
    }

    static double avgEndOf(int b) {
        if (binN[b] == 0) {
            return 0.0;
        }
        return binEnd[b] / (double) binN[b];
    }

    static double avgOf(double[] a, int b) {
        if (binN[b] == 0) {
            return 0.0;
        }
        return a[b] / (double) binN[b];
    }

    static double lossOf(int b) {
        if (binN[b] == 0) {
            return 0.0;
        }
        return (double) binLoss[b] / (double) binN[b];
    }

    // ---------- phase 3: scan ----------
    static void stepScan() {
        if (pos >= uN) {
            phase = PHASE_DONE;
            msg = "扫描完成";
            finishedMs = System.currentTimeMillis();
            return;
        }
        int idx = pos;
        pos = pos + 1;
        if (uTrade[idx] <= 0.0 || uName.get(idx).indexOf("退") >= 0) {
            /* suspended, or in the delisting consolidation period (prices below 1 and extreme volatility distort the model) */
            skipped = skipped + 1;
            return;
        }
        StockSeries s = null;
        try {
            s = fetchDaily(uSym.get(idx), 130);
            consecFail = 0;
        } catch (Exception ex) {
            failed = failed + 1;
            consecFail = consecFail + 1;
            if (consecFail >= 20) {
                phase = PHASE_ERROR;
                msg = "数据源连续失败，已暂停（可重新开始）";
                finishedMs = System.currentTimeMillis();
            }
            return;
        }
        if (s == null || s.n < StockFeatures.WARMUP + 5) {
            skipped = skipped + 1;
            return;
        }
        StockFeatures f = StockFeatures.build(s);
        double[] x = new double[NF2];
        int t = s.n - 1;
        scanned = scanned + 1;
        if (vec(f, t, x, 0) == 0 || marketAt(s.date[t], x, 13) < 0) {
            skipped = skipped + 1;
            return;
        }
        /* skip stocks that have not traded on the last bar (flat volume = suspended) */
        if (s.v[t] <= 0.0) {
            skipped = skipped + 1;
            return;
        }
        if (excl == 1 && x[11] > 0.5) {
            skipped = skipped + 1;
            return;
        }
        /* signal day closed at the daily limit: tomorrow's open is almost never buyable */
        if (s.c[t - 1] > 0.0 && s.c[t] >= s.c[t - 1] * (1.0 + limitOf(uSym.get(idx)) - 0.005)) {
            skipped = skipped + 1;
            return;
        }
        double p = score(x, 0);
        if (p >= pMin && mGate == 1 && mState == 3) {
            gated = gated + 1;
            return;
        }
        if (p >= pMin) {
            StockTrack.add(s.date[t], uSym.get(idx), uName.get(idx), p, theta, excl, lastBaseTest, hold);
            double band = -1.0;
            double bandTp = 0.0;
            double bandTs = 0.0;
            int bn = 0;
            int b = 0;
            while (b < 7) {
                if (p >= binCut[b] && binN[b] >= 20) {
                    band = binPrec[b];
                    bandTp = avgOf(binTp, b);
                    bandTs = avgOf(binTs, b);
                    bn = binN[b];
                }
                b = b + 1;
            }
            String nm = uName.get(idx);
            String bandTxt = "null";
            if (band >= 0.0) {
                bandTxt = StockUtil.num(band, 4);
            }
            hits.add("{\"code\":" + StockUtil.js(uSym.get(idx)) + ",\"name\":" + StockUtil.js(nm) + ",\"asOf\":" + s.date[t] + ",\"price\":" + StockUtil.num(s.c[t], 2)
                    + ",\"chg\":" + StockUtil.num(f.x[t * StockFeatures.NF], 4) + ",\"p\":" + StockUtil.num(p, 4) + ",\"band\":" + bandTxt + ",\"bandN\":" + bn + ",\"bandTp\":" + StockUtil.num(bandTp, 5) + ",\"bandTs\":" + StockUtil.num(bandTs, 5)
                    + ",\"limitUp\":" + (int) x[12] + "}");
        }
    }

    /** Plain-text summary of a finished scan for push messages: the n most probable picks and how reliable that band was. */
    static String summaryText(int n) {
        StringBuilder sb = new StringBuilder();
        String asOf = "";
        int cnt = hits.size();
        if (cnt > 0) {
            asOf = "" + (int) numField(hits.get(0), "asOf");
        }
        sb.append("【JNative 收盘扫描】数据日期 " + asOf + "\n");
        sb.append("目标：T+1 开盘买入后 " + hold + " 个交易日内，任一天收盘较买入价涨幅 >= " + StockUtil.num(theta * 100.0, 0) + "%\n");
        if (mState >= 0) {
            sb.append("大盘（上证）" + mDate + "：" + stateName(mState) + "，当日 " + StockUtil.num(mR1 * 100.0, 2) + "%，5 日 " + StockUtil.num(mR5 * 100.0, 2) + "%，距 20 日线 " + StockUtil.num(mDist * 100.0, 2) + "%\n");
        }
        sb.append("检验期基准命中率 " + StockUtil.num(lastBaseTest * 100.0, 1) + "%，入选 " + cnt + " 只（概率 >= " + StockUtil.num(pMin * 100.0, 0) + "%）\n");
        int[] used = new int[cnt];
        int shown = 0;
        while (shown < n && shown < cnt) {
            int bi = -1;
            int z = 0;
            while (z < cnt) {
                if (used[z] == 0 && (bi < 0 || numField(hits.get(z), "p") > numField(hits.get(bi), "p"))) {
                    bi = z;
                }
                z = z + 1;
            }
            if (bi < 0) {
                shown = n;
                continue;
            }
            used[bi] = 1;
            String o = hits.get(bi);
            String bandTxt = "无足够检验样本";
            if (o.indexOf("\"band\":null") < 0) {
                bandTxt = "同档检验命中率 " + StockUtil.num(numField(o, "band") * 100.0, 1) + "%";
            }
            sb.append((shown + 1) + ". " + StockFeed.field(o, "code") + " " + StockFeed.field(o, "name") + " 概率 " + StockUtil.num(numField(o, "p") * 100.0, 1) + "%，" + bandTxt + "\n");
            shown = shown + 1;
        }
        sb.append("仅为研究工具的统计输出，不构成投资建议；回测不等于实盘，请先看页面里的历史战绩。");
        return sb.toString();
    }

    // ---------- status ----------
    static String marketJson() {
        if (mState < 0) {
            return "null";
        }
        return "{\"date\":" + mDate + ",\"state\":" + mState + ",\"r1\":" + StockUtil.num(mR1, 4) + ",\"r5\":" + StockUtil.num(mR5, 4) + ",\"dist\":" + StockUtil.num(mDist, 4) + "}";
    }

    static String statusJson(int since) {
        ensureInit();
        long now = System.currentTimeMillis();
        long end = now;
        if (finishedMs > 0L && running() == 0) {
            end = finishedMs;
        }
        long el = 0L;
        if (startedMs > 0L) {
            el = (end - startedMs) / 1000L;
        }
        int total = uN;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"phase\":" + phase + ",\"msg\":" + StockUtil.js(msg) + ",\"runId\":" + runId + ",\"universe\":" + total + ",\"pos\":" + pos
                + ",\"scanned\":" + scanned + ",\"failed\":" + failed + ",\"skipped\":" + skipped + ",\"theta\":" + StockUtil.num(theta, 4) + ",\"hold\":" + hold
                + ",\"pMin\":" + StockUtil.num(pMin, 3) + ",\"elapsed\":" + el + ",\"trainLoaded\":" + trainLoaded + ",\"trainTotal\":" + TRAIN_STOCKS
                + ",\"market\":" + marketJson() + ",\"gate\":" + mGate + ",\"gated\":" + gated + ",\"validation\":" + validation + ",\"hitsTotal\":" + hits.size() + ",\"marketOpen\":" + StockNotify.tradingNow() + ",\"hits\":[");
        int i = since;
        if (i < 0) {
            i = 0;
        }
        int cnt = 0;
        while (i < hits.size() && cnt < 200) {
            if (cnt > 0) {
                sb.append(",");
            }
            sb.append(hits.get(i));
            i = i + 1;
            cnt = cnt + 1;
        }
        sb.append("]}");
        return sb.toString();
    }
}
