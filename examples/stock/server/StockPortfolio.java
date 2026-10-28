/**
 * Portfolio backtest over a pool of stocks with one strategy (a built-in one or custom rules).
 *
 * One account, one calendar: on every day the engine (1) sells positions whose signal turned off, whose stop-loss /
 * take-profit / trailing stop was touched intraday, or that reached the maximum holding days, (2) buys the best-ranked
 * stocks whose signal is on, until maxPos positions are held, each sized at equity / maxPos in board lots of 100,
 * (3) marks everything to the close. Signals of day t are acted on at the OPEN of day t+1 and a position bought on day
 * t can first be sold on day t+1 (T+1). Opens at the daily limit are skipped (cannot buy a limit-up open, cannot sell a
 * limit-down open). Costs: commission (min 5 yuan) both sides, stamp duty on sells, slippage both sides.
 * @author 张晓达
 */
class StockPortfolio {
    static double MIN_FEE = 5.0;
    static int MAX_STOCKS = 120;

    static int[] sigOf(StockSeries s, String strategy, ParamBag p) {
        if (strategy.equals("rules")) {
            return StockRules.signals(s, p.gs("entry"), p.gs("exit"));
        }
        return StockBacktest.signals(s, strategy, p);
    }

    static String run(ArrayList<String> codes, String strategy, ParamBag p, int fromDate) {
        if (!(strategy.equals("buy_hold") || strategy.equals("ma_cross") || strategy.equals("rsi") || strategy.equals("macd")
                || strategy.equals("boll") || strategy.equals("rules"))) {
            return StockUtil.err("BAD_STRATEGY", "组合回测支持：buy_hold / ma_cross / rsi / macd / boll / rules");
        }
        ArrayList<StockSeries> ss = new ArrayList<StockSeries>();
        ArrayList<String> skipped = new ArrayList<String>();
        int i = 0;
        while (i < codes.size() && ss.size() < MAX_STOCKS) {
            StockSeries s = StockStore.find(codes.get(i));
            if (s == null) {
                skipped.add(codes.get(i) + "：本地没有数据");
            } else if (s.hm != null || s.n < 120) {
                skipped.add(codes.get(i) + "：日线不足 120 根");
            } else {
                ss.add(s);
            }
            i = i + 1;
        }
        int K = ss.size();
        if (K == 0) {
            return StockUtil.err("EMPTY_POOL", "股票池里没有可用的日线数据");
        }
        if (strategy.equals("rules")) {
            String ce = StockRules.check(ss.get(0), p.gs("entry"), 1);
            if (ce.length() == 0) {
                ce = StockRules.check(ss.get(0), p.gs("exit"), 0);
            }
            if (ce.length() > 0) {
                return StockUtil.err("BAD_RULE", ce);
            }
        }
        double capital = p.gd("capital", 1000000.0, 10000.0, 1.0e9);
        int maxPos = p.gi("maxPos", 5, 1, 50);
        if (maxPos > K) {
            maxPos = K;
        }
        double slPct = p.gd("sl", 0.0, 0.0, 0.5);
        double tpPct = p.gd("tp", 0.0, 0.0, 5.0);
        double trailPct = p.gd("trail", 0.0, 0.0, 0.5);
        int maxHold = p.gi("maxHold", 0, 0, 500);
        String rank = p.gs("rank");
        int rankMode = 0;
        if (rank.equals("mom")) {
            rankMode = 1;
        } else if (rank.equals("rev")) {
            rankMode = 2;
        }
        double comm = StockBacktest.COMMISSION;
        double stamp = StockBacktest.STAMP;
        double slip = StockBacktest.SLIPPAGE;

        /* signals */
        int[][] pos = new int[K][];
        double[] lim = new double[K];
        int sumN = 0;
        int firstSig = -1;
        int k = 0;
        while (k < K) {
            StockSeries s = ss.get(k);
            pos[k] = sigOf(s, strategy, p);
            lim[k] = StockScan.limitOf(s.code);
            sumN = sumN + s.n;
            k = k + 1;
        }

        /* shared calendar: sorted union of all dates */
        int[] ptr = new int[K];
        int[] cal = new int[sumN];
        int D = 0;
        int calDone = 0;
        while (calDone == 0) {
            int mn = 99999999;
            k = 0;
            while (k < K) {
                StockSeries s = ss.get(k);
                if (ptr[k] < s.n && s.date[ptr[k]] < mn) {
                    mn = s.date[ptr[k]];
                }
                k = k + 1;
            }
            if (mn == 99999999) {
                calDone = 1;
            } else {
                cal[D] = mn;
                D = D + 1;
                k = 0;
                while (k < K) {
                    StockSeries s = ss.get(k);
                    if (ptr[k] < s.n && s.date[ptr[k]] == mn) {
                        ptr[k] = ptr[k] + 1;
                    }
                    k = k + 1;
                }
            }
        }
        int[][] ix = new int[K][];
        int[] firstDay = new int[K];
        k = 0;
        while (k < K) {
            StockSeries s = ss.get(k);
            ix[k] = new int[D];
            int q = 0;
            int d = 0;
            while (d < D) {
                if (q < s.n && s.date[q] == cal[d]) {
                    ix[k][d] = q;
                    q = q + 1;
                } else {
                    ix[k][d] = -1;
                }
                d = d + 1;
            }
            int fs = StockBacktest.firstSignal(pos[k], s.n);
            firstDay[k] = D;
            if (fs >= 0) {
                d = 0;
                while (d < D) {
                    if (ix[k][d] == fs) {
                        firstDay[k] = d;
                        d = D;
                    }
                    d = d + 1;
                }
            }
            k = k + 1;
        }
        int start = D;
        k = 0;
        while (k < K) {
            if (firstDay[k] < start) {
                start = firstDay[k];
            }
            k = k + 1;
        }
        if (fromDate > 0) {
            int d2 = start;
            while (d2 < D && cal[d2] < fromDate) {
                d2 = d2 + 1;
            }
            if (D - d2 >= 60) {
                start = d2;
            }
        }
        if (start >= D || D - start < 60) {
            return StockUtil.err("INSUFFICIENT_DATA", "有信号的区间不足 60 个交易日");
        }

        /* state */
        double cash = capital;
        int[] held = new int[K];
        int[] shares = new int[K];
        double[] entryPx = new double[K];
        double[] cost = new double[K];
        double[] hi = new double[K];
        int[] entryDay = new int[K];
        double[] lastClose = new double[K];
        int[] soldDay = new int[K];
        int[] nTrades = new int[K];
        int[] nWins = new int[K];
        double[] pnlBy = new double[K];
        double[] eq = new double[D];
        double[] dr = new double[D];
        double[] expo = new double[D];
        int[] cnt = new int[D];
        ArrayList<String> rows = new ArrayList<String>();
        int trades = 0;
        int wins = 0;
        double sumWin = 0.0;
        double sumLoss = 0.0;
        double sumRetWin = 0.0;
        double sumRetLoss = 0.0;
        double holdSum = 0.0;
        double fees = 0.0;
        double traded = 0.0;
        int maxConc = 0;
        k = 0;
        while (k < K) {
            soldDay[k] = -1;
            lastClose[k] = 0.0;
            k = k + 1;
        }
        double prevEq = capital;
        int d = start;
        while (d < D) {
            int heldCount = 0;
            /* 1) exits */
            k = 0;
            while (k < K) {
                if (held[k] == 1) {
                    int b = ix[k][d];
                    if (b >= 1) {
                        StockSeries s = ss.get(k);
                        double op = s.o[b];
                        double pc = s.c[b - 1];
                        double px = 0.0;
                        String why = "";
                        int sell = 0;
                        if (pos[k][b - 1] == 0) {
                            sell = 1;
                            px = op;
                            why = "信号";
                        } else if (maxHold > 0 && d - entryDay[k] >= maxHold) {
                            sell = 1;
                            px = op;
                            why = "到期";
                        }
                        int locked = 0;
                        if (op <= pc * (1.0 - lim[k] + 0.002)) {
                            locked = 1;
                        }
                        if (locked == 1) {
                            sell = 0;
                        } else if (sell == 0) {
                            double slL = -1.0;
                            double trL = -1.0;
                            if (slPct > 0.0) {
                                slL = entryPx[k] * (1.0 - slPct);
                            }
                            if (trailPct > 0.0) {
                                trL = hi[k] * (1.0 - trailPct);
                            }
                            double stopL = slL;
                            String stopWhy = "止损";
                            if (trL > stopL) {
                                stopL = trL;
                                stopWhy = "移动止盈";
                            }
                            if (stopL > 0.0 && s.l[b] <= stopL) {
                                sell = 1;
                                px = Math.min(op, stopL);
                                why = stopWhy;
                            } else if (tpPct > 0.0 && s.h[b] >= entryPx[k] * (1.0 + tpPct)) {
                                sell = 1;
                                px = Math.max(op, entryPx[k] * (1.0 + tpPct));
                                why = "止盈";
                            }
                        }
                        if (sell == 1) {
                            double fill = px * (1.0 - slip);
                            double amt = fill * (double) shares[k];
                            double fee = Math.max(MIN_FEE, amt * comm) + amt * stamp;
                            double net = amt - fee;
                            double pnl = net - cost[k];
                            double ret = net / cost[k] - 1.0;
                            cash = cash + net;
                            fees = fees + fee;
                            traded = traded + amt;
                            trades = trades + 1;
                            nTrades[k] = nTrades[k] + 1;
                            pnlBy[k] = pnlBy[k] + pnl;
                            if (pnl > 0.0) {
                                wins = wins + 1;
                                nWins[k] = nWins[k] + 1;
                                sumWin = sumWin + pnl;
                                sumRetWin = sumRetWin + ret;
                            } else {
                                sumLoss = sumLoss + (-pnl);
                                sumRetLoss = sumRetLoss + ret;
                            }
                            holdSum = holdSum + (double) (d - entryDay[k]);
                            rows.add("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"in\":" + cal[entryDay[k]] + ",\"out\":" + cal[d]
                                    + ",\"inPx\":" + StockUtil.num(entryPx[k], 2) + ",\"outPx\":" + StockUtil.num(fill, 2) + ",\"ret\":" + StockUtil.num(ret, 4)
                                    + ",\"pnl\":" + StockUtil.num(pnl, 0) + ",\"why\":" + StockUtil.js(why) + ",\"days\":" + (d - entryDay[k]) + "}");
                            held[k] = 0;
                            shares[k] = 0;
                            soldDay[k] = d;
                        } else if (s.h[b] > hi[k]) {
                            hi[k] = s.h[b];
                        }
                    }
                }
                if (held[k] == 1) {
                    heldCount = heldCount + 1;
                }
                k = k + 1;
            }
            /* 2) entries */
            int slots = maxPos - heldCount;
            if (slots > 0) {
                int[] candK = new int[K];
                double[] candS = new double[K];
                int nc = 0;
                k = 0;
                while (k < K) {
                    int b = ix[k][d];
                    if (held[k] == 0 && soldDay[k] != d && b >= 1) {
                        if (pos[k][b - 1] == 1) {
                            StockSeries s = ss.get(k);
                            if (s.o[b] < s.c[b - 1] * (1.0 + lim[k] - 0.002) && s.o[b] > 0.0) {
                                double score = -(double) k;
                                if (rankMode > 0 && b - 1 >= 20) {
                                    double mom = s.c[b - 1] / s.c[b - 21] - 1.0;
                                    if (rankMode == 1) {
                                        score = mom;
                                    } else {
                                        score = -mom;
                                    }
                                }
                                int at = nc;
                                while (at > 0 && candS[at - 1] < score) {
                                    candS[at] = candS[at - 1];
                                    candK[at] = candK[at - 1];
                                    at = at - 1;
                                }
                                candS[at] = score;
                                candK[at] = k;
                                nc = nc + 1;
                            }
                        }
                    }
                    k = k + 1;
                }
                int ci = 0;
                while (ci < nc && slots > 0) {
                    k = candK[ci];
                    ci = ci + 1;
                    int b = ix[k][d];
                    StockSeries s = ss.get(k);
                    double buyPx = s.o[b] * (1.0 + slip);
                    double alloc = Math.min(prevEq / (double) maxPos, cash);
                    int lots = (int) Math.floor(alloc / (buyPx * (1.0 + comm)) / 100.0);
                    while (lots > 0 && (double) lots * 100.0 * buyPx + Math.max(MIN_FEE, (double) lots * 100.0 * buyPx * comm) > cash) {
                        lots = lots - 1;
                    }
                    if (lots <= 0) {
                        continue;
                    }
                    int sh = lots * 100;
                    double amt = buyPx * (double) sh;
                    double fee = Math.max(MIN_FEE, amt * comm);
                    cash = cash - amt - fee;
                    fees = fees + fee;
                    traded = traded + amt;
                    held[k] = 1;
                    shares[k] = sh;
                    entryPx[k] = buyPx;
                    cost[k] = amt + fee;
                    hi[k] = s.h[b];
                    entryDay[k] = d;
                    slots = slots - 1;
                    heldCount = heldCount + 1;
                }
            }
            /* 3) mark to the close */
            double mv = 0.0;
            k = 0;
            while (k < K) {
                int b = ix[k][d];
                if (b >= 0) {
                    lastClose[k] = ss.get(k).c[b];
                }
                if (held[k] == 1) {
                    mv = mv + lastClose[k] * (double) shares[k];
                }
                k = k + 1;
            }
            eq[d] = cash + mv;
            dr[d] = eq[d] / prevEq - 1.0;
            expo[d] = mv / eq[d];
            cnt[d] = heldCount;
            if (heldCount > maxConc) {
                maxConc = heldCount;
            }
            prevEq = eq[d];
            d = d + 1;
        }

        /* benchmarks: equal-weight pool (daily rebalanced) and the Shanghai index when stored */
        double[] bench = new double[D];
        d = start;
        while (d < D) {
            double sum = 0.0;
            int m = 0;
            k = 0;
            while (k < K) {
                int b = ix[k][d];
                if (b >= 1) {
                    StockSeries s = ss.get(k);
                    sum = sum + s.c[b] / s.c[b - 1] - 1.0;
                    m = m + 1;
                }
                k = k + 1;
            }
            if (m > 0) {
                bench[d] = sum / (double) m;
            }
            d = d + 1;
        }
        double[] idxR = new double[D];
        int haveIdx = 0;
        StockSeries ib = StockStore.find("sh000001");
        if (ib != null && ib.hm == null && ib.n > 60) {
            int q = 0;
            d = 0;
            while (d < D) {
                if (q < ib.n && ib.date[q] == cal[d]) {
                    if (q >= 1 && d >= start) {
                        idxR[d] = ib.c[q] / ib.c[q - 1] - 1.0;
                    }
                    q = q + 1;
                }
                d = d + 1;
            }
            haveIdx = 1;
        }

        int from = start;
        int split = from + (int) ((double) (D - from) * 0.7);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"strategy\":" + StockUtil.js(strategy) + ",\"pool\":" + K + ",\"startDate\":" + cal[start] + ",\"endDate\":" + cal[D - 1] + ",\"splitDate\":" + cal[split]
                + ",\"days\":" + (D - start) + ",\"capital\":" + StockUtil.num(capital, 0) + ",\"maxPos\":" + maxPos);
        sb.append(",\"costs\":{\"commission\":" + comm + ",\"minFee\":" + MIN_FEE + ",\"stamp\":" + stamp + ",\"slippage\":" + slip + "}");
        sb.append(",\"full\":");
        StockBacktest.metrics(sb, dr, from, D, bench);
        sb.append(",\"early\":");
        StockBacktest.metrics(sb, dr, from, split, bench);
        sb.append(",\"recent\":");
        StockBacktest.metrics(sb, dr, split, D, bench);
        double years = (double) (D - from) / StockBacktest.TRADING_DAYS;
        double avgEq = 0.0;
        double avgExpo = 0.0;
        double avgCnt = 0.0;
        d = from;
        while (d < D) {
            avgEq = avgEq + eq[d];
            avgExpo = avgExpo + expo[d];
            avgCnt = avgCnt + (double) cnt[d];
            d = d + 1;
        }
        double nd = (double) (D - from);
        avgEq = avgEq / nd;
        avgExpo = avgExpo / nd;
        avgCnt = avgCnt / nd;
        int losses = trades - wins;
        double pf = 0.0;
        if (sumLoss > 0.0) {
            pf = sumWin / sumLoss;
        }
        double maxDd = 0.0;
        double peak = capital;
        double[] ddv = new double[D];
        d = from;
        while (d < D) {
            if (eq[d] > peak) {
                peak = eq[d];
            }
            ddv[d] = eq[d] / peak - 1.0;
            if (ddv[d] < maxDd) {
                maxDd = ddv[d];
            }
            d = d + 1;
        }
        double totRet = eq[D - 1] / capital - 1.0;
        double cagr = Math.pow(Math.max(eq[D - 1] / capital, 1.0e-9), 1.0 / Math.max(years, 1.0e-9)) - 1.0;
        double calmar = 0.0;
        if (maxDd < -1.0e-9) {
            calmar = cagr / (-maxDd);
        }
        sb.append(",\"stats\":{\"trades\":" + trades + ",\"wins\":" + wins + ",\"losses\":" + losses + ",\"winRate\":" + StockUtil.num(trades > 0 ? (double) wins / (double) trades : 0.0, 3)
                + ",\"avgWin\":" + StockUtil.num(wins > 0 ? sumRetWin / (double) wins : 0.0, 4) + ",\"avgLoss\":" + StockUtil.num(losses > 0 ? sumRetLoss / (double) losses : 0.0, 4)
                + ",\"profitFactor\":" + StockUtil.num(pf, 2) + ",\"avgHold\":" + StockUtil.num(trades > 0 ? holdSum / (double) trades : 0.0, 1)
                + ",\"turnover\":" + StockUtil.num(traded / 2.0 / avgEq / Math.max(years, 1.0e-9), 2) + ",\"fees\":" + StockUtil.num(fees, 0)
                + ",\"avgExposure\":" + StockUtil.num(avgExpo, 3) + ",\"avgPositions\":" + StockUtil.num(avgCnt, 1) + ",\"maxPositions\":" + maxConc
                + ",\"calmar\":" + StockUtil.num(calmar, 2) + ",\"endEquity\":" + StockUtil.num(eq[D - 1], 0) + ",\"totalReturn\":" + StockUtil.num(totRet, 4) + ",\"maxDrawdown\":" + StockUtil.num(maxDd, 4) + "}");

        /* curves */
        int step = 1;
        while ((D - from) / step > 700) {
            step = step + 1;
        }
        StringBuilder cd = new StringBuilder();
        StringBuilder cs = new StringBuilder();
        StringBuilder cb = new StringBuilder();
        StringBuilder ci2 = new StringBuilder();
        StringBuilder cdd = new StringBuilder();
        double eb = 1.0;
        double ei = 1.0;
        int first = 1;
        d = from;
        while (d < D) {
            eb = eb * (1.0 + bench[d]);
            ei = ei * (1.0 + idxR[d]);
            if ((d - from) % step == 0 || d == D - 1) {
                if (first == 0) {
                    cd.append(",");
                    cs.append(",");
                    cb.append(",");
                    ci2.append(",");
                    cdd.append(",");
                }
                first = 0;
                cd.append("" + cal[d]);
                cs.append(StockUtil.num(eq[d] / capital, 4));
                cb.append(StockUtil.num(eb, 4));
                ci2.append(StockUtil.num(ei, 4));
                cdd.append(StockUtil.num(ddv[d], 4));
            }
            d = d + 1;
        }
        sb.append(",\"curve\":{\"d\":[" + cd.toString() + "],\"s\":[" + cs.toString() + "],\"b\":[" + cb.toString() + "],\"dd\":[" + cdd.toString() + "]");
        if (haveIdx == 1) {
            sb.append(",\"i\":[" + ci2.toString() + "]");
        }
        sb.append("}");

        /* monthly returns */
        sb.append(",\"monthly\":[");
        double prevM = capital;
        int curM = cal[from] / 100;
        int firstM = 1;
        d = from;
        while (d < D) {
            int ym = cal[d] / 100;
            boolean last = d == D - 1 || cal[d + 1] / 100 != ym;
            if (last) {
                if (firstM == 0) {
                    sb.append(",");
                }
                firstM = 0;
                sb.append("[" + ym + "," + StockUtil.num(eq[d] / prevM - 1.0, 4) + "]");
                prevM = eq[d];
            }
            d = d + 1;
        }
        sb.append("]");

        /* per stock + open positions */
        sb.append(",\"stocks\":[");
        StringBuilder open = new StringBuilder();
        int firstOpen = 1;
        k = 0;
        while (k < K) {
            StockSeries s = ss.get(k);
            if (k > 0) {
                sb.append(",");
            }
            double avgR = 0.0;
            sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"trades\":" + nTrades[k] + ",\"wins\":" + nWins[k] + ",\"pnl\":" + StockUtil.num(pnlBy[k], 0)
                    + ",\"open\":" + held[k] + "}");
            if (held[k] == 1) {
                if (firstOpen == 0) {
                    open.append(",");
                }
                firstOpen = 0;
                double mvk = lastClose[k] * (double) shares[k];
                open.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"in\":" + cal[entryDay[k]] + ",\"inPx\":" + StockUtil.num(entryPx[k], 2)
                        + ",\"px\":" + StockUtil.num(lastClose[k], 2) + ",\"shares\":" + shares[k] + ",\"ret\":" + StockUtil.num(mvk / cost[k] - 1.0, 4) + ",\"pnl\":" + StockUtil.num(mvk - cost[k], 0) + "}");
            }
            k = k + 1;
        }
        sb.append("],\"openPositions\":[" + open.toString() + "]");
        sb.append(",\"tradeList\":[");
        int fromRow = rows.size() - 100;
        if (fromRow < 0) {
            fromRow = 0;
        }
        int rr = fromRow;
        while (rr < rows.size()) {
            if (rr > fromRow) {
                sb.append(",");
            }
            sb.append(rows.get(rr));
            rr = rr + 1;
        }
        sb.append("],\"signalNow\":[");
        int firstSig2 = 1;
        /* the latest bar date most stocks share (a stock with a partial bar of today must not hide everyone else's signals) */
        int refDate = cal[D - 1];
        int bestCnt = 0;
        k = 0;
        while (k < K) {
            int dk = ss.get(k).date[ss.get(k).n - 1];
            int same = 0;
            int k2 = 0;
            while (k2 < K) {
                if (ss.get(k2).date[ss.get(k2).n - 1] == dk) {
                    same = same + 1;
                }
                k2 = k2 + 1;
            }
            if (same > bestCnt || (same == bestCnt && dk > refDate)) {
                bestCnt = same;
                refDate = dk;
            }
            k = k + 1;
        }
        k = 0;
        while (k < K) {
            StockSeries s = ss.get(k);
            int lb = s.n - 1;
            if (pos[k][lb] == 1 && s.date[lb] == refDate) {
                if (firstSig2 == 0) {
                    sb.append(",");
                }
                firstSig2 = 0;
                int fresh = 0;
                if (lb >= 1 && pos[k][lb - 1] != 1) {
                    fresh = 1;
                }
                sb.append("{\"code\":" + StockUtil.js(s.code) + ",\"name\":" + StockUtil.js(s.name) + ",\"price\":" + StockUtil.num(s.c[lb], 2) + ",\"asOf\":" + s.date[lb]
                        + ",\"fresh\":" + fresh + ",\"held\":" + held[k] + "}");
            }
            k = k + 1;
        }
        sb.append("],\"skipped\":[");
        int sk = 0;
        while (sk < skipped.size()) {
            if (sk > 0) {
                sb.append(",");
            }
            sb.append(StockUtil.js(skipped.get(sk)));
            sk = sk + 1;
        }
        sb.append("]}");
        return sb.toString();
    }
}
