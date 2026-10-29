/**
 * Paper-trading engine ("模拟交易") for A-shares: one account with cash, positions, orders and fills.
 *
 * Rules that mirror the real market: T+1 (shares bought today cannot be sold today), board lots of 100 (STAR market: at
 * least 200, then any size; a sell may clear an odd lot), price limits from the live quote (a buy cannot fill at the
 * limit-up price and a sell cannot fill at the limit-down price), commission with a minimum, stamp duty on sells and the
 * transfer fee, slippage on market orders. Orders are day orders matched against the live quote while the market is open;
 * unfilled orders expire at the close. Stop-loss / take-profit / trailing-stop rules are checked by the serve loop and turn
 * into market sell orders.
 *
 * Persistence: data/_trade.txt, one tab separated record per line:
 *   C key value            account setting
 *   P code name qty sellable cost sl tp trail high lastBuyDay [exitDay]    open position (cost = cash paid for the remaining shares, fees included)
 *   O id ts day code name side type price qty status fillPrice fee reserve source note slPct tpPct trailPct [holdDays]    order
 *   F id orderId ts code name side price qty amount fee pnl source    fill
 *   E day equity cash marketValue    end-of-day equity point
 * @author 张晓达
 */
class TradeOrder {
    int id;
    long ts;
    int day;
    String code;
    String name;
    /** 1 buy, 2 sell */
    int side;
    /** 0 limit, 1 market */
    int type;
    double price;
    int qty;
    /** 0 pending, 1 filled, 2 cancelled, 3 rejected, 4 expired */
    int status;
    double fillPrice;
    double fee;
    double reserve;
    String source;
    String note;
    double slPct;
    double tpPct;
    double trailPct;
    /** Buy orders only: sell the shares at the close of the N-th trading day after the buy day (0 = no time exit). */
    int holdDays;
}

class TradePos {
    String code;
    String name;
    int qty;
    int sellable;
    double cost;
    double sl;
    double tp;
    double trail;
    double high;
    int lastBuyDay;
    /** Trading day (yyyymmdd) at whose close the position is sold; 0 = none. */
    int exitDay;
}

class TradeFill {
    int id;
    int orderId;
    long ts;
    String code;
    String name;
    int side;
    double price;
    int qty;
    double amount;
    double fee;
    double pnl;
    String source;
}

class StockTrade {
    /** Day value of an order queued while the market was closed: it becomes a normal day order at the next open. */
    static int QUEUED_DAY = 99999999;
    static double cash = 1000000.0;
    static double initial = 1000000.0;
    static double feeRate = 0.00025;
    static double minFee = 5.0;
    static double stampRate = 0.0005;
    static double transferRate = 0.00001;
    static double slipBps = 5.0;
    static double maxPosPct = 0.3;
    static int anytime = 0;
    static int nextOrder = 1;
    static int nextFill = 1;
    static int lastDay = 0;
    static int lastEqDay = 0;
    static double lastEq = 0.0;
    static long resetTs = 0L;
    static ArrayList<TradePos> positions;
    static ArrayList<TradeOrder> orders;
    static ArrayList<TradeFill> fills;
    static ArrayList<String> equity;
    static long lastTick = 0L;
    static int loaded = 0;
    static String lastMsg = "";
    /** Set by the HTTP route right before place(): hold days for the buy order being placed. */
    static int pendingHold = 0;

    static void init() {
        positions = new ArrayList<TradePos>();
        orders = new ArrayList<TradeOrder>();
        fills = new ArrayList<TradeFill>();
        equity = new ArrayList<String>();
        loaded = 1;
        load();
        if (lastDay == 0) {
            lastDay = (int) (StockFeed.nowCst() / 10000L);
        }
        if (resetTs == 0L) {
            resetTs = StockFeed.nowCst();
        }
    }

    // ------------------------------------------------------------------ persistence

    static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    static void save() {
        try {
            if (!Files.exists(FsPath.of(StockStore.dataDir))) {
                Files.createDirectories(FsPath.of(StockStore.dataDir));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("C\tcash\t" + StockUtil.num(cash, 4) + "\n");
            sb.append("C\tinitial\t" + StockUtil.num(initial, 2) + "\n");
            sb.append("C\tfeeRate\t" + StockUtil.num(feeRate, 7) + "\n");
            sb.append("C\tminFee\t" + StockUtil.num(minFee, 2) + "\n");
            sb.append("C\tstampRate\t" + StockUtil.num(stampRate, 7) + "\n");
            sb.append("C\ttransferRate\t" + StockUtil.num(transferRate, 7) + "\n");
            sb.append("C\tslipBps\t" + StockUtil.num(slipBps, 2) + "\n");
            sb.append("C\tmaxPosPct\t" + StockUtil.num(maxPosPct, 4) + "\n");
            sb.append("C\tanytime\t" + anytime + "\n");
            sb.append("C\tnextOrder\t" + nextOrder + "\n");
            sb.append("C\tnextFill\t" + nextFill + "\n");
            sb.append("C\tlastDay\t" + lastDay + "\n");
            sb.append("C\tlastEqDay\t" + lastEqDay + "\n");
            sb.append("C\tlastEq\t" + StockUtil.num(lastEq, 2) + "\n");
            sb.append("C\tresetTs\t" + resetTs + "\n");
            int i = 0;
            while (i < positions.size()) {
                TradePos p = positions.get(i);
                sb.append("P\t" + p.code + "\t" + clean(p.name) + "\t" + p.qty + "\t" + p.sellable + "\t" + StockUtil.num(p.cost, 4) + "\t"
                        + StockUtil.num(p.sl, 3) + "\t" + StockUtil.num(p.tp, 3) + "\t" + StockUtil.num(p.trail, 4) + "\t" + StockUtil.num(p.high, 3)
                        + "\t" + p.lastBuyDay + "\t" + p.exitDay + "\n");
                i = i + 1;
            }
            i = 0;
            while (i < orders.size()) {
                TradeOrder o = orders.get(i);
                sb.append("O\t" + o.id + "\t" + o.ts + "\t" + o.day + "\t" + o.code + "\t" + clean(o.name) + "\t" + o.side + "\t" + o.type + "\t"
                        + StockUtil.num(o.price, 3) + "\t" + o.qty + "\t" + o.status + "\t" + StockUtil.num(o.fillPrice, 3) + "\t" + StockUtil.num(o.fee, 2)
                        + "\t" + StockUtil.num(o.reserve, 2) + "\t" + clean(o.source) + "\t" + clean(o.note) + "\t" + StockUtil.num(o.slPct, 4) + "\t"
                        + StockUtil.num(o.tpPct, 4) + "\t" + StockUtil.num(o.trailPct, 4) + "\t" + o.holdDays + "\n");
                i = i + 1;
            }
            i = 0;
            while (i < fills.size()) {
                TradeFill f = fills.get(i);
                sb.append("F\t" + f.id + "\t" + f.orderId + "\t" + f.ts + "\t" + f.code + "\t" + clean(f.name) + "\t" + f.side + "\t" + StockUtil.num(f.price, 3)
                        + "\t" + f.qty + "\t" + StockUtil.num(f.amount, 2) + "\t" + StockUtil.num(f.fee, 2) + "\t" + StockUtil.num(f.pnl, 2) + "\t"
                        + clean(f.source) + "\n");
                i = i + 1;
            }
            i = 0;
            while (i < equity.size()) {
                sb.append("E\t" + equity.get(i) + "\n");
                i = i + 1;
            }
            File.writeString(StockStore.dataDir + "/_trade.txt", sb.toString());
        } catch (Exception ex) {
            System.out.println("STOCK_TRADE_SAVE_WARN");
        }
    }

    static void load() {
        try {
            String path = StockStore.dataDir + "/_trade.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path);
            int st = 0;
            int i = 0;
            while (i <= text.length()) {
                if (i == text.length() || text.charAt(i) == 10) {
                    parseLine(text.substring(st, i));
                    st = i + 1;
                }
                i = i + 1;
            }
        } catch (Exception ex) {
            System.out.println("STOCK_TRADE_LOAD_WARN");
        }
    }

    static void parseLine(String ln) {
        if (ln.length() < 3) {
            return;
        }
        ArrayList<String> f = StockNotify.splitTabs(ln);
        String t = f.get(0);
        if (t.equals("C") && f.size() >= 3) {
            String k = f.get(1);
            String v = f.get(2);
            if (k.equals("cash")) {
                cash = StockUtil.parseDouble(v, cash);
            } else if (k.equals("initial")) {
                initial = StockUtil.parseDouble(v, initial);
            } else if (k.equals("feeRate")) {
                feeRate = StockUtil.parseDouble(v, feeRate);
            } else if (k.equals("minFee")) {
                minFee = StockUtil.parseDouble(v, minFee);
            } else if (k.equals("stampRate")) {
                stampRate = StockUtil.parseDouble(v, stampRate);
            } else if (k.equals("transferRate")) {
                transferRate = StockUtil.parseDouble(v, transferRate);
            } else if (k.equals("slipBps")) {
                slipBps = StockUtil.parseDouble(v, slipBps);
            } else if (k.equals("maxPosPct")) {
                maxPosPct = StockUtil.parseDouble(v, maxPosPct);
            } else if (k.equals("anytime")) {
                anytime = StockUtil.parseInt(v, 0);
            } else if (k.equals("nextOrder")) {
                nextOrder = StockUtil.parseInt(v, 1);
            } else if (k.equals("nextFill")) {
                nextFill = StockUtil.parseInt(v, 1);
            } else if (k.equals("lastDay")) {
                lastDay = StockUtil.parseInt(v, 0);
            } else if (k.equals("lastEqDay")) {
                lastEqDay = StockUtil.parseInt(v, 0);
            } else if (k.equals("lastEq")) {
                lastEq = StockUtil.parseDouble(v, 0.0);
            } else if (k.equals("resetTs")) {
                resetTs = StockUtil.parseLong(v, 0L);
            }
        } else if (t.equals("P") && f.size() >= 11) {
            TradePos p = new TradePos();
            p.code = f.get(1);
            p.name = f.get(2);
            p.qty = StockUtil.parseInt(f.get(3), 0);
            p.sellable = StockUtil.parseInt(f.get(4), 0);
            p.cost = StockUtil.parseDouble(f.get(5), 0.0);
            p.sl = StockUtil.parseDouble(f.get(6), 0.0);
            p.tp = StockUtil.parseDouble(f.get(7), 0.0);
            p.trail = StockUtil.parseDouble(f.get(8), 0.0);
            p.high = StockUtil.parseDouble(f.get(9), 0.0);
            p.lastBuyDay = StockUtil.parseInt(f.get(10), 0);
            if (f.size() > 11) {
                p.exitDay = StockUtil.parseInt(f.get(11), 0);
            }
            if (p.qty > 0) {
                positions.add(p);
            }
        } else if (t.equals("O") && f.size() >= 19) {
            TradeOrder o = new TradeOrder();
            o.id = StockUtil.parseInt(f.get(1), 0);
            o.ts = StockUtil.parseLong(f.get(2), 0L);
            o.day = StockUtil.parseInt(f.get(3), 0);
            o.code = f.get(4);
            o.name = f.get(5);
            o.side = StockUtil.parseInt(f.get(6), 1);
            o.type = StockUtil.parseInt(f.get(7), 0);
            o.price = StockUtil.parseDouble(f.get(8), 0.0);
            o.qty = StockUtil.parseInt(f.get(9), 0);
            o.status = StockUtil.parseInt(f.get(10), 0);
            o.fillPrice = StockUtil.parseDouble(f.get(11), 0.0);
            o.fee = StockUtil.parseDouble(f.get(12), 0.0);
            o.reserve = StockUtil.parseDouble(f.get(13), 0.0);
            o.source = f.get(14);
            o.note = f.get(15);
            o.slPct = StockUtil.parseDouble(f.get(16), 0.0);
            o.tpPct = StockUtil.parseDouble(f.get(17), 0.0);
            o.trailPct = StockUtil.parseDouble(f.get(18), 0.0);
            if (f.size() > 19) {
                o.holdDays = StockUtil.parseInt(f.get(19), 0);
            }
            if (o.id > 0) {
                orders.add(o);
            }
        } else if (t.equals("F") && f.size() >= 13) {
            TradeFill x = new TradeFill();
            x.id = StockUtil.parseInt(f.get(1), 0);
            x.orderId = StockUtil.parseInt(f.get(2), 0);
            x.ts = StockUtil.parseLong(f.get(3), 0L);
            x.code = f.get(4);
            x.name = f.get(5);
            x.side = StockUtil.parseInt(f.get(6), 1);
            x.price = StockUtil.parseDouble(f.get(7), 0.0);
            x.qty = StockUtil.parseInt(f.get(8), 0);
            x.amount = StockUtil.parseDouble(f.get(9), 0.0);
            x.fee = StockUtil.parseDouble(f.get(10), 0.0);
            x.pnl = StockUtil.parseDouble(f.get(11), 0.0);
            x.source = f.get(12);
            if (x.id > 0) {
                fills.add(x);
            }
        } else if (t.equals("E") && f.size() >= 5) {
            equity.add(f.get(1) + "\t" + f.get(2) + "\t" + f.get(3) + "\t" + f.get(4));
        }
    }

    // ------------------------------------------------------------------ rules

    static double r2(double x) {
        return (double) Math.round(x * 100.0) / 100.0;
    }

    /** Total transaction cost of one fill: commission (with minimum) + transfer fee (+ stamp duty when selling). */
    static double feeOf(int side, double amount) {
        double comm = amount * feeRate;
        if (comm < minFee) {
            comm = minFee;
        }
        double f = comm + amount * transferRate;
        if (side == 2) {
            f = f + amount * stampRate;
        }
        return r2(f);
    }

    /** Stocks only: Shanghai 60x / 68x, Shenzhen 00x / 30x, Beijing 4x / 8x / 9x. */
    static int tradable(String code) {
        if (code.length() != 8) {
            return 0;
        }
        String pre = code.substring(0, 2);
        String d = code.substring(2, 4);
        if (pre.equals("sh") && (d.equals("60") || d.equals("68"))) {
            return 1;
        }
        if (pre.equals("sz") && (d.equals("00") || d.equals("30"))) {
            return 1;
        }
        if (pre.equals("bj")) {
            return 1;
        }
        return 0;
    }

    static int isStar(String code) {
        if (code.startsWith("sh688") || code.startsWith("sh689")) {
            return 1;
        }
        return 0;
    }

    /** Empty when the quantity is a legal board lot for this side, otherwise the reason. */
    static String lotError(String code, int side, int qty, int sellableAll) {
        if (qty <= 0) {
            return "数量必须大于 0";
        }
        if (side == 1) {
            if (isStar(code) == 1) {
                if (qty < 200) {
                    return "科创板买入数量不得少于 200 股";
                }
                return "";
            }
            if (qty % 100 != 0) {
                return "买入数量必须是 100 股的整数倍";
            }
            return "";
        }
        if (qty % 100 != 0 && qty != sellableAll) {
            return "卖出数量必须是 100 股的整数倍（零股只能一次全部卖出）";
        }
        return "";
    }

    /**
     * Price at which an order fills against a quote, or 0 when it cannot fill now. Buys cannot fill at the limit-up price and
     * sells cannot fill at the limit-down price (the board is locked); market orders pay slippage.
     */
    static double matchPrice(int side, int type, double limit, double last, double up, double down, double slip) {
        if (last <= 0.0) {
            return 0.0;
        }
        if (side == 1) {
            if (up > 0.0 && last >= up - 0.0001) {
                return 0.0;
            }
            if (type == 0) {
                if (last <= limit + 0.00001) {
                    return last;
                }
                return 0.0;
            }
            double p = r2(last * (1.0 + slip));
            if (up > 0.0 && p > up) {
                p = up;
            }
            return p;
        }
        if (down > 0.0 && last <= down + 0.0001) {
            return 0.0;
        }
        if (type == 0) {
            if (last >= limit - 0.00001) {
                return last;
            }
            return 0.0;
        }
        double q = r2(last * (1.0 - slip));
        if (down > 0.0 && q < down) {
            q = down;
        }
        return q;
    }

    // ------------------------------------------------------------------ lookups

    static TradePos findPos(String code) {
        int i = 0;
        while (i < positions.size()) {
            if (positions.get(i).code.equals(code)) {
                return positions.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static void removePos(TradePos p) {
        int i = 0;
        while (i < positions.size()) {
            if (positions.get(i) == p) {
                positions.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    static TradeOrder findOrder(int id) {
        int i = 0;
        while (i < orders.size()) {
            if (orders.get(i).id == id) {
                return orders.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static double frozen() {
        double s = 0.0;
        int i = 0;
        while (i < orders.size()) {
            TradeOrder o = orders.get(i);
            if (o.status == 0 && o.side == 1) {
                s = s + o.reserve;
            }
            i = i + 1;
        }
        return s;
    }

    static int pendingSell(String code) {
        int s = 0;
        int i = 0;
        while (i < orders.size()) {
            TradeOrder o = orders.get(i);
            if (o.status == 0 && o.side == 2 && o.code.equals(code)) {
                s = s + o.qty;
            }
            i = i + 1;
        }
        return s;
    }

    static int pendingCount() {
        int s = 0;
        int i = 0;
        while (i < orders.size()) {
            if (orders.get(i).status == 0) {
                s = s + 1;
            }
            i = i + 1;
        }
        return s;
    }

    /** Last known price: live quote, else the last daily close, else the average cost. */
    static double priceOf(TradePos p) {
        LiveSnap n = StockLive.snap(p.code);
        if (n != null && n.price > 0.0) {
            return n.price;
        }
        StockSeries s = StockStore.find(p.code);
        if (s != null && s.n > 0) {
            return s.c[s.n - 1];
        }
        if (p.qty > 0) {
            return p.cost / (double) p.qty;
        }
        return 0.0;
    }

    static double marketValue() {
        double mv = 0.0;
        int i = 0;
        while (i < positions.size()) {
            TradePos p = positions.get(i);
            mv = mv + priceOf(p) * (double) p.qty;
            i = i + 1;
        }
        return mv;
    }

    static int marketOpen(LiveSnap n) {
        if (anytime == 1) {
            return 1;
        }
        String st = StockLive.status(n);
        if (st.equals("open")) {
            return 1;
        }
        return 0;
    }

    static String nameOf(String code, LiveSnap n) {
        if (n != null && n.name != null && n.name.length() > 0) {
            return n.name;
        }
        StockSeries s = StockStore.find(code);
        if (s != null) {
            return s.name;
        }
        return StockFeed.nameFor(code);
    }

    static long lastWatchSync = 0L;

    /** Every held stock is kept on the watchlist: missing history is fetched (at most 2 stocks per call, retried after a minute) and the code is added. */
    static void syncWatch(long now) {
        if (lastWatchSync != 0L && now - lastWatchSync < 60000L) {
            return;
        }
        int fetched = 0;
        int missing = 0;
        int i = 0;
        while (i < positions.size()) {
            String code = positions.get(i).code;
            i = i + 1;
            if (StockApi.inWatch(code) == 1) {
                continue;
            }
            if (StockStore.find(code) == null && fetched < 2) {
                fetched = fetched + 1;
                StockFeed.addJson(code, StockFeed.DEFAULT_BEG);
            }
            if (StockStore.find(code) != null) {
                StockApi.watchAdd(code);
            } else {
                missing = missing + 1;
            }
        }
        lastWatchSync = now;
        if (missing > 0 && fetched >= 2) {
            lastWatchSync = now - 50000L;
        }
    }

    /** Positions, orders and fills that were recorded with the bare code as name (the quote source gave no name) get their real name. */
    static int healNames() {
        int changed = 0;
        int i = 0;
        while (i < positions.size()) {
            TradePos p = positions.get(i);
            if (p.name.equals(p.code)) {
                String nm = StockFeed.nameFor(p.code);
                if (!nm.equals(p.code)) {
                    p.name = nm;
                    changed = changed + 1;
                }
            }
            i = i + 1;
        }
        i = 0;
        while (i < orders.size()) {
            TradeOrder o = orders.get(i);
            if (o.name.equals(o.code)) {
                String nm = StockFeed.nameFor(o.code);
                if (!nm.equals(o.code)) {
                    o.name = nm;
                    changed = changed + 1;
                }
            }
            i = i + 1;
        }
        i = 0;
        while (i < fills.size()) {
            TradeFill f = fills.get(i);
            if (f.name.equals(f.code)) {
                String nm = StockFeed.nameFor(f.code);
                if (!nm.equals(f.code)) {
                    f.name = nm;
                    changed = changed + 1;
                }
            }
            i = i + 1;
        }
        return changed;
    }

    // ------------------------------------------------------------------ placing orders

    static String sideName(int side) {
        if (side == 1) {
            return "买入";
        }
        return "卖出";
    }

    /**
     * Validates and queues an order; market orders (and limit orders already marketable) fill immediately while the market is open.
     * Returns the order JSON or an error JSON.
     */
    static String place(String rawCode, int side, int type, double price, int qty, double slPct, double tpPct, double trailPct, String source, String note, int queue) {
        int holdD = pendingHold;
        pendingHold = 0;
        FeedSym f = StockFeed.resolve(rawCode);
        if (f == null || tradable(f.code) == 0) {
            return StockUtil.err("BAD_CODE", "仅支持沪深北交所 A 股股票（指数、基金、债券不可交易）");
        }
        if (side != 1 && side != 2) {
            return StockUtil.err("BAD_SIDE", "side 必须是 buy 或 sell");
        }
        LiveSnap n = StockLive.snap(f.code);
        if (n == null) {
            return StockUtil.err("NO_QUOTE", "获取行情失败：" + StockLive.lastError);
        }
        String st = StockLive.status(n);
        int queued = 0;
        if (anytime == 0 && !st.equals("open") && !st.equals("auction")) {
            if (queue == 0) {
                return StockUtil.err("MARKET_CLOSED", "当前不在交易时段（9:30-11:30、13:00-15:00）。可勾选“预约下一个开盘”，或在设置里开启“休市也成交”做演示");
            }
            queued = 1;
        }
        TradePos pos = findPos(f.code);
        int sellableAll = 0;
        if (pos != null) {
            sellableAll = pos.sellable - pendingSell(f.code);
        }
        String le = lotError(f.code, side, qty, sellableAll);
        if (le.length() > 0) {
            return StockUtil.err("BAD_QTY", le);
        }
        double limit = 0.0;
        if (type == 0) {
            limit = r2(price);
            if (limit <= 0.0) {
                return StockUtil.err("BAD_PRICE", "限价单必须填写价格");
            }
            if (queued == 0 && n.limitUp > 0.0 && limit > n.limitUp + 0.0001) {
                return StockUtil.err("BAD_PRICE", "委托价高于涨停价 " + StockUtil.num(n.limitUp, 2));
            }
            if (queued == 0 && n.limitDown > 0.0 && limit < n.limitDown - 0.0001) {
                return StockUtil.err("BAD_PRICE", "委托价低于跌停价 " + StockUtil.num(n.limitDown, 2));
            }
        }
        double ref = limit;
        if (type == 1) {
            ref = n.price * (1.0 + slipBps / 10000.0);
            if (n.limitUp > 0.0 && ref > n.limitUp) {
                ref = n.limitUp;
            }
        }
        double reserve = 0.0;
        if (side == 1) {
            double amount = ref * (double) qty;
            double need = r2(amount + feeOf(1, amount));
            double avail = cash - frozen();
            if (need > avail + 0.005) {
                return StockUtil.err("NO_FUNDS", "可用资金不足：需要 " + StockUtil.num(need, 2) + "，可用 " + StockUtil.num(avail, 2));
            }
            double eq = lastEquityEstimate();
            double posCost = 0.0;
            if (pos != null) {
                posCost = pos.cost;
            }
            if (maxPosPct > 0.0 && maxPosPct < 1.0 && eq > 0.0 && (posCost + amount) > eq * maxPosPct + 1.0) {
                return StockUtil.err("RISK_LIMIT", "超过单只股票仓位上限 " + StockUtil.num(maxPosPct * 100.0, 1) + "%（总资产 " + StockUtil.num(eq, 0) + "）");
            }
            reserve = need;
        } else {
            if (pos == null || sellableAll <= 0) {
                if (pos != null && pos.qty > 0) {
                    return StockUtil.err("T_PLUS_1", "T+1：当日买入的股票次日才能卖出");
                }
                return StockUtil.err("NO_POSITION", "没有该股票的持仓");
            }
            if (qty > sellableAll) {
                return StockUtil.err("T_PLUS_1", "可卖数量不足：可卖 " + sellableAll + " 股（T+1，当日买入不可卖，已挂单的数量也不可重复卖）");
            }
        }
        TradeOrder o = new TradeOrder();
        o.id = nextOrder;
        nextOrder = nextOrder + 1;
        o.ts = StockFeed.nowCst();
        o.day = (int) (o.ts / 10000L);
        if (queued == 1) {
            o.day = QUEUED_DAY;
            note = "预约·" + note;
        }
        o.code = f.code;
        o.name = nameOf(f.code, n);
        o.side = side;
        o.type = type;
        o.price = limit;
        o.qty = qty;
        o.status = 0;
        o.reserve = reserve;
        o.source = source;
        o.note = clean(note);
        o.slPct = slPct;
        o.tpPct = tpPct;
        o.trailPct = trailPct;
        if (side == 1) {
            o.holdDays = holdD;
        }
        orders.add(o);
        trimOrders();
        if (queued == 0 && marketOpen(n) == 1) {
            tryFill(o, n);
        }
        save();
        return "{\"order\":" + orderJson(o) + ",\"filled\":" + (o.status == 1 ? 1 : 0) + "}";
    }

    static void trimOrders() {
        int done = 0;
        int i = orders.size() - 1;
        while (i >= 0) {
            if (orders.get(i).status != 0) {
                done = done + 1;
                if (done > 600) {
                    orders.removeAt(i);
                }
            }
            i = i - 1;
        }
        while (fills.size() > 3000) {
            fills.removeAt(0);
        }
    }

    static String cancel(int id) {
        TradeOrder o = findOrder(id);
        if (o == null) {
            return StockUtil.err("NOT_FOUND", "没有这笔委托");
        }
        if (o.status != 0) {
            return StockUtil.err("NOT_PENDING", "这笔委托已经结束，无法撤销");
        }
        o.status = 2;
        o.reserve = 0.0;
        save();
        return "{\"order\":" + orderJson(o) + "}";
    }

    // ------------------------------------------------------------------ matching

    /** Tries to fill a pending order against the quote. Returns 1 when it was filled (or rejected) so the caller saves. */
    static int tryFill(TradeOrder o, LiveSnap n) {
        if (o.status != 0 || n == null) {
            return 0;
        }
        double px = matchPrice(o.side, o.type, o.price, n.price, n.limitUp, n.limitDown, slipBps / 10000.0);
        if (px <= 0.0) {
            return 0;
        }
        double amount = r2(px * (double) o.qty);
        double fee = feeOf(o.side, amount);
        long ts = StockFeed.nowCst();
        int today = (int) (ts / 10000L);
        TradePos pos = findPos(o.code);
        if (o.side == 1) {
            double total = amount + fee;
            o.reserve = 0.0;
            if (total > cash - frozen() + 0.005) {
                o.status = 3;
                o.note = clean(o.note + " 成交时资金不足");
                return 1;
            }
            cash = cash - total;
            if (pos == null) {
                pos = new TradePos();
                pos.code = o.code;
                pos.name = o.name;
                pos.qty = 0;
                pos.sellable = 0;
                pos.cost = 0.0;
                pos.sl = 0.0;
                pos.tp = 0.0;
                pos.trail = 0.0;
                pos.high = 0.0;
                positions.add(pos);
            }
            pos.qty = pos.qty + o.qty;
            pos.cost = pos.cost + total;
            pos.lastBuyDay = today;
            if (px > pos.high) {
                pos.high = px;
            }
            double avg = pos.cost / (double) pos.qty;
            if (o.slPct > 0.0) {
                pos.sl = r2(avg * (1.0 - o.slPct));
            }
            if (o.tpPct > 0.0) {
                pos.tp = r2(avg * (1.0 + o.tpPct));
            }
            if (o.trailPct > 0.0) {
                pos.trail = o.trailPct;
            }
            if (o.holdDays > 0) {
                int ed = addTradingDays(today, o.holdDays);
                if (ed > pos.exitDay) {
                    pos.exitDay = ed;
                }
            }
            recordFill(o, px, amount, fee, 0.0, ts);
        } else {
            if (pos == null || pos.qty < o.qty) {
                o.status = 3;
                o.note = clean(o.note + " 成交时持仓不足");
                return 1;
            }
            double costPart = pos.cost * (double) o.qty / (double) pos.qty;
            double proceeds = amount - fee;
            double pnl = r2(proceeds - costPart);
            cash = cash + proceeds;
            pos.cost = pos.cost - costPart;
            pos.qty = pos.qty - o.qty;
            pos.sellable = pos.sellable - o.qty;
            if (pos.sellable < 0) {
                pos.sellable = 0;
            }
            recordFill(o, px, amount, fee, pnl, ts);
            if (pos.qty <= 0) {
                removePos(pos);
            }
        }
        o.status = 1;
        o.fillPrice = px;
        o.fee = fee;
        announce(o, px, fee);
        return 1;
    }

    static void recordFill(TradeOrder o, double px, double amount, double fee, double pnl, long ts) {
        TradeFill x = new TradeFill();
        x.id = nextFill;
        nextFill = nextFill + 1;
        x.orderId = o.id;
        x.ts = ts;
        x.code = o.code;
        x.name = o.name;
        x.side = o.side;
        x.price = px;
        x.qty = o.qty;
        x.amount = amount;
        x.fee = fee;
        x.pnl = pnl;
        x.source = o.source;
        fills.add(x);
    }

    /** Pushes automatic trades (stop-loss / take-profit / signal buys) through the notification channel; manual ones stay quiet. */
    static void announce(TradeOrder o, double px, double fee) {
        lastMsg = o.name + " " + sideName(o.side) + " " + o.qty + " 股 @" + StockUtil.num(px, 2);
        if (o.source.equals("手动") || (o.source.equals("扫描") && o.note.indexOf("预约") < 0)) {
            return;
        }
        String text = "【模拟交易】" + o.source + "：" + sideName(o.side) + " " + o.name + "（" + o.code + "） " + o.qty + " 股，成交价 " + StockUtil.num(px, 2)
                + "，费用 " + StockUtil.num(fee, 2);
        String st = "none";
        if (StockAuto.pushable() == 1) {
            st = StockNotify.send(text);
        }
        StockNotify.addEvent(o.code, o.name, text, st);
    }

    // ------------------------------------------------------------------ serve-loop hook

    /** Called by the serve loop: day rollover, order matching, risk rules and the end-of-day equity point. */
    static void tick() {
        if (loaded == 0) {
            init();
        }
        long now = System.currentTimeMillis();
        long gap = 30000L;
        if (anytime == 1 && pendingCount() > 0) {
            gap = 3000L;
        } else if (StockNotify.tradingNow() == 1 && (pendingCount() > 0 || hasRiskRules() == 1)) {
            gap = 3000L;
        } else if (StockNotify.tradingNow() == 1) {
            gap = 10000L;
        }
        if (lastTick != 0L && now - lastTick < gap) {
            return;
        }
        lastTick = now;
        try {
            rollover();
            int changed = healNames();
            syncWatch(now);
            int i = 0;
            while (i < orders.size()) {
                TradeOrder o = orders.get(i);
                i = i + 1;
                if (o.status != 0) {
                    continue;
                }
                LiveSnap n = StockLive.snap(o.code);
                if (n != null && marketOpen(n) == 1) {
                    if (o.day == QUEUED_DAY) {
                        o.day = (int) (StockFeed.nowCst() / 10000L);
                        changed = changed + 1;
                    }
                    changed = changed + tryFill(o, n);
                }
            }
            changed = changed + checkRisk();
            snapshotEquity();
            if (changed > 0) {
                save();
            }
        } catch (Exception ex) {
            System.out.println("STOCK_TRADE_TICK_WARN");
        }
    }

    /** The trading day that is n trading days after ymd (weekends and fixed-date holidays skipped; moving holidays are not known). */
    static int addTradingDays(int ymd, int n) {
        int d = ymd;
        int left = n;
        while (left > 0) {
            d = StockGen.nextDay(d);
            int w = StockGen.dayOfWeek(d);
            if (w != 0 && w != 6 && StockGen.isHoliday(d) == 0) {
                left = left - 1;
            }
        }
        return d;
    }

    /** A time exit is due at 14:50 of the exit day (or at once if that day has passed). */
    static int exitDue(TradePos p, int today, long hhmm) {
        if (p.exitDay <= 0) {
            return 0;
        }
        if (today > p.exitDay) {
            return 1;
        }
        if (today == p.exitDay && (anytime == 1 || hhmm >= 1450L)) {
            return 1;
        }
        return 0;
    }

    static int hasRiskRules() {
        int i = 0;
        while (i < positions.size()) {
            TradePos p = positions.get(i);
            if (p.sl > 0.0 || p.tp > 0.0 || p.trail > 0.0 || p.exitDay > 0) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    /** New trading day: yesterday's purchases become sellable, unfilled day orders expire. */
    static void rollover() {
        int today = (int) (StockFeed.nowCst() / 10000L);
        if (today == lastDay) {
            return;
        }
        int i = 0;
        while (i < positions.size()) {
            TradePos p = positions.get(i);
            if (p.lastBuyDay < today) {
                p.sellable = p.qty;
            }
            i = i + 1;
        }
        i = 0;
        while (i < orders.size()) {
            TradeOrder o = orders.get(i);
            if (o.status == 0 && o.day < today) {
                o.status = 4;
                o.reserve = 0.0;
            }
            i = i + 1;
        }
        lastDay = today;
        save();
    }

    /** Orders that are still open after the close expire (also covers a server that was down at 15:00). */
    static void expireAfterClose() {
        long cst = StockFeed.nowCst();
        if (anytime == 1 || cst % 10000L < 1505L || StockFeed.nowDow() >= 5) {
            return;
        }
        int i = 0;
        int n = 0;
        while (i < orders.size()) {
            TradeOrder o = orders.get(i);
            if (o.status == 0 && o.day != QUEUED_DAY) {
                o.status = 4;
                o.reserve = 0.0;
                n = n + 1;
            }
            i = i + 1;
        }
        if (n > 0) {
            save();
        }
    }

    /** Stop-loss, take-profit and trailing-stop rules turn into market sell orders for the sellable shares. */
    static int checkRisk() {
        int changed = 0;
        int i = 0;
        while (i < positions.size()) {
            TradePos p = positions.get(i);
            i = i + 1;
            if (p.sl <= 0.0 && p.tp <= 0.0 && p.trail <= 0.0 && p.exitDay <= 0) {
                continue;
            }
            LiveSnap n = StockLive.snap(p.code);
            if (n == null || marketOpen(n) == 0) {
                continue;
            }
            if (n.price > p.high) {
                p.high = n.price;
                changed = 1;
            }
            int free = p.sellable - pendingSell(p.code);
            if (free <= 0) {
                continue;
            }
            String why = "";
            if (p.sl > 0.0 && n.price <= p.sl) {
                why = "止损";
            } else if (p.tp > 0.0 && n.price >= p.tp) {
                why = "止盈";
            } else if (p.trail > 0.0 && p.high > 0.0 && n.price <= p.high * (1.0 - p.trail)) {
                why = "移动止盈";
            } else if (exitDue(p, (int) (StockFeed.nowCst() / 10000L), StockFeed.nowCst() % 10000L) == 1) {
                why = "到期卖出";
            }
            if (why.length() == 0) {
                continue;
            }
            TradeOrder o = new TradeOrder();
            o.id = nextOrder;
            nextOrder = nextOrder + 1;
            o.ts = StockFeed.nowCst();
            o.day = (int) (o.ts / 10000L);
            o.code = p.code;
            o.name = p.name;
            o.side = 2;
            o.type = 1;
            o.price = 0.0;
            o.qty = free;
            o.status = 0;
            o.reserve = 0.0;
            o.source = why;
            o.note = "价格 " + StockUtil.num(n.price, 2) + " 触发 " + why;
            o.slPct = 0.0;
            o.tpPct = 0.0;
            o.trailPct = 0.0;
            orders.add(o);
            changed = 1;
            tryFill(o, n);
            i = 0;
        }
        return changed;
    }

    /** Records the end-of-day equity (once per trading day, after 15:05, or for the previous day on the next start). */
    static void snapshotEquity() {
        long cst = StockFeed.nowCst();
        int today = (int) (cst / 10000L);
        long hm = cst % 10000L;
        if (lastEqDay != 0 && lastEqDay != today && hasEquityDay(lastEqDay) == 0 && lastEq > 0.0) {
            equity.add(lastEqDay + "\t" + StockUtil.num(lastEq, 2) + "\t" + StockUtil.num(cash, 2) + "\t" + StockUtil.num(lastEq - cash, 2));
            save();
        }
        double eq = equityNow();
        lastEq = eq;
        lastEqDay = today;
        if (anytime == 0 && StockFeed.nowDow() < 5 && hm >= 1505L && hasEquityDay(today) == 0) {
            LiveSnap probe = null;
            if (positions.size() > 0) {
                probe = StockLive.snap(positions.get(0).code);
            } else {
                probe = StockLive.snap("sh000001");
            }
            if (probe != null && probe.date == today) {
                equity.add(today + "\t" + StockUtil.num(eq, 2) + "\t" + StockUtil.num(cash, 2) + "\t" + StockUtil.num(eq - cash, 2));
                expireAfterClose();
                save();
            }
        }
    }

    static int hasEquityDay(int day) {
        int i = equity.size() - 1;
        while (i >= 0) {
            ArrayList<String> p = StockNotify.splitTabs(equity.get(i));
            if (p.size() > 0 && StockUtil.parseInt(p.get(0), 0) == day) {
                return 1;
            }
            if (p.size() > 0 && StockUtil.parseInt(p.get(0), 0) < day) {
                return 0;
            }
            i = i - 1;
        }
        return 0;
    }

    /** Cash (including frozen) + market value of all positions. */
    static double equityNow() {
        return cash + marketValue();
    }

    /** Cheap estimate for risk checks: the last computed equity, or cash + cost when nothing was valued yet. */
    static double lastEquityEstimate() {
        if (lastEq > 0.0) {
            return lastEq;
        }
        double c = cash;
        int i = 0;
        while (i < positions.size()) {
            c = c + positions.get(i).cost;
            i = i + 1;
        }
        return c;
    }

    // ------------------------------------------------------------------ settings

    static String reset(double newCash) {
        if (newCash < 10000.0 || newCash > 1.0e9) {
            return StockUtil.err("BAD_CASH", "初始资金必须在 1 万到 10 亿之间");
        }
        positions = new ArrayList<TradePos>();
        orders = new ArrayList<TradeOrder>();
        fills = new ArrayList<TradeFill>();
        equity = new ArrayList<String>();
        cash = r2(newCash);
        initial = cash;
        nextOrder = 1;
        nextFill = 1;
        lastEq = cash;
        lastEqDay = (int) (StockFeed.nowCst() / 10000L);
        lastDay = lastEqDay;
        resetTs = StockFeed.nowCst();
        save();
        return accountJson();
    }

    static String configure(String fee, String minF, String stamp, String slip, String maxPos, String any) {
        double v = StockUtil.parseDouble(fee, feeRate);
        if (v >= 0.0 && v <= 0.01) {
            feeRate = v;
        }
        v = StockUtil.parseDouble(minF, minFee);
        if (v >= 0.0 && v <= 100.0) {
            minFee = v;
        }
        v = StockUtil.parseDouble(stamp, stampRate);
        if (v >= 0.0 && v <= 0.01) {
            stampRate = v;
        }
        v = StockUtil.parseDouble(slip, slipBps);
        if (v >= 0.0 && v <= 500.0) {
            slipBps = v;
        }
        v = StockUtil.parseDouble(maxPos, maxPosPct);
        if (v >= 0.0 && v <= 1.0) {
            maxPosPct = v;
        }
        if (any != null && any.length() > 0) {
            anytime = StockUtil.parseInt(any, anytime) == 1 ? 1 : 0;
        }
        save();
        return accountJson();
    }

    /** Sets absolute stop-loss / take-profit prices and a trailing percentage on an open position (0 clears). */
    static String setRisk(String rawCode, double sl, double tp, double trail) {
        FeedSym f = StockFeed.resolve(rawCode);
        TradePos p = null;
        if (f != null) {
            p = findPos(f.code);
        }
        if (p == null) {
            return StockUtil.err("NO_POSITION", "没有该股票的持仓");
        }
        if (sl < 0.0 || tp < 0.0 || trail < 0.0 || trail >= 1.0) {
            return StockUtil.err("BAD_RISK", "止损/止盈价不能为负，移动止盈比例必须在 0 到 1 之间");
        }
        double px = priceOf(p);
        if (sl > 0.0 && px > 0.0 && sl >= px) {
            return StockUtil.err("BAD_RISK", "止损价必须低于当前价 " + StockUtil.num(px, 2));
        }
        if (tp > 0.0 && px > 0.0 && tp <= px) {
            return StockUtil.err("BAD_RISK", "止盈价必须高于当前价 " + StockUtil.num(px, 2));
        }
        p.sl = r2(sl);
        p.tp = r2(tp);
        p.trail = trail;
        if (px > p.high) {
            p.high = px;
        }
        save();
        return accountJson();
    }

    // ------------------------------------------------------------------ JSON

    static String orderJson(TradeOrder o) {
        return "{\"id\":" + o.id + ",\"ts\":" + o.ts + ",\"code\":" + StockUtil.js(o.code) + ",\"name\":" + StockUtil.js(o.name) + ",\"side\":" + o.side
                + ",\"type\":" + o.type + ",\"price\":" + StockUtil.num(o.price, 3) + ",\"qty\":" + o.qty + ",\"status\":" + o.status + ",\"fillPrice\":"
                + StockUtil.num(o.fillPrice, 3) + ",\"fee\":" + StockUtil.num(o.fee, 2) + ",\"source\":" + StockUtil.js(o.source) + ",\"note\":"
                + StockUtil.js(o.note) + ",\"queued\":" + (o.day == QUEUED_DAY ? 1 : 0) + "}";
    }

    static String fillJson(TradeFill f) {
        return "{\"id\":" + f.id + ",\"orderId\":" + f.orderId + ",\"ts\":" + f.ts + ",\"code\":" + StockUtil.js(f.code) + ",\"name\":" + StockUtil.js(f.name)
                + ",\"side\":" + f.side + ",\"price\":" + StockUtil.num(f.price, 3) + ",\"qty\":" + f.qty + ",\"amount\":" + StockUtil.num(f.amount, 2)
                + ",\"fee\":" + StockUtil.num(f.fee, 2) + ",\"pnl\":" + (f.side == 2 ? StockUtil.num(f.pnl, 2) : "null") + ",\"source\":" + StockUtil.js(f.source) + "}";
    }

    static String ordersJson(int openOnly, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int cnt = 0;
        int i = orders.size() - 1;
        while (i >= 0 && cnt < limit) {
            TradeOrder o = orders.get(i);
            i = i - 1;
            if (openOnly == 1 && o.status != 0) {
                continue;
            }
            if (cnt > 0) {
                sb.append(",");
            }
            sb.append(orderJson(o));
            cnt = cnt + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    static String fillsJson(int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int cnt = 0;
        int i = fills.size() - 1;
        while (i >= 0 && cnt < limit) {
            if (cnt > 0) {
                sb.append(",");
            }
            sb.append(fillJson(fills.get(i)));
            cnt = cnt + 1;
            i = i - 1;
        }
        sb.append("]");
        return sb.toString();
    }

    /** Shares of the code bought today (all buy fills of the current day) and what they cost including fees: [qty, cost]. */
    static double[] boughtToday(String code) {
        double[] r = new double[2];
        long today = StockFeed.nowCst() / 10000L;
        int i = fills.size() - 1;
        while (i >= 0) {
            TradeFill x = fills.get(i);
            if (x.ts / 10000L != today) {
                if (x.ts / 10000L < today) {
                    return r;
                }
            } else if (x.side == 1 && x.code.equals(code)) {
                r[0] = r[0] + (double) x.qty;
                r[1] = r[1] + x.amount + x.fee;
            }
            i = i - 1;
        }
        return r;
    }

    static String accountJson() {
        StringBuilder sb = new StringBuilder();
        double mv = 0.0;
        double dayPnl = 0.0;
        StringBuilder ps = new StringBuilder();
        ps.append("[");
        int i = 0;
        while (i < positions.size()) {
            TradePos p = positions.get(i);
            LiveSnap n = StockLive.snap(p.code);
            double px = priceOf(p);
            double prev = 0.0;
            double up = 0.0;
            double down = 0.0;
            if (n != null) {
                prev = n.prev;
                up = n.limitUp;
                down = n.limitDown;
            }
            double v = px * (double) p.qty;
            mv = mv + v;
            /* shares bought today have no yesterday close: their float is measured from what they cost; older shares from the previous close */
            double[] tb = boughtToday(p.code);
            double tq = tb[0];
            if (tq > (double) p.qty) {
                tq = (double) p.qty;
            }
            double tcost = 0.0;
            if (tb[0] > 0.0) {
                tcost = tb[1] * tq / tb[0];
            }
            dayPnl = dayPnl + (px * tq - tcost);
            if (prev > 0.0) {
                dayPnl = dayPnl + (px - prev) * ((double) p.qty - tq);
            }
            double avg = 0.0;
            if (p.qty > 0) {
                avg = p.cost / (double) p.qty;
            }
            if (i > 0) {
                ps.append(",");
            }
            ps.append("{\"code\":" + StockUtil.js(p.code) + ",\"name\":" + StockUtil.js(p.name) + ",\"qty\":" + p.qty + ",\"sellable\":" + p.sellable
                    + ",\"free\":" + (p.sellable - pendingSell(p.code)) + ",\"avgCost\":" + StockUtil.num(avg, 3) + ",\"cost\":" + StockUtil.num(p.cost, 2)
                    + ",\"price\":" + StockUtil.num(px, 3) + ",\"prev\":" + StockUtil.num(prev, 3) + ",\"mv\":" + StockUtil.num(v, 2) + ",\"pnl\":"
                    + StockUtil.num(v - p.cost, 2) + ",\"pnlPct\":" + StockUtil.num(p.cost > 0.0 ? (v - p.cost) / p.cost : 0.0, 5) + ",\"sl\":"
                    + StockUtil.num(p.sl, 3) + ",\"tp\":" + StockUtil.num(p.tp, 3) + ",\"trail\":" + StockUtil.num(p.trail, 4) + ",\"high\":"
                    + StockUtil.num(p.high, 3) + ",\"limitUp\":" + StockUtil.num(up, 2) + ",\"limitDown\":" + StockUtil.num(down, 2) + ",\"buyDay\":"
                    + p.lastBuyDay + ",\"exitDay\":" + p.exitDay + "}");
            i = i + 1;
        }
        ps.append("]");
        double frz = frozen();
        double eq = cash + mv;
        lastEq = eq;
        sb.append("{\"cash\":" + StockUtil.num(cash, 2) + ",\"frozen\":" + StockUtil.num(frz, 2) + ",\"available\":" + StockUtil.num(cash - frz, 2)
                + ",\"mv\":" + StockUtil.num(mv, 2) + ",\"equity\":" + StockUtil.num(eq, 2) + ",\"initial\":" + StockUtil.num(initial, 2) + ",\"pnl\":"
                + StockUtil.num(eq - initial, 2) + ",\"ret\":" + StockUtil.num(initial > 0.0 ? (eq - initial) / initial : 0.0, 5) + ",\"dayPnl\":"
                + StockUtil.num(dayPnl, 2) + ",\"pending\":" + pendingCount() + ",\"positions\":" + ps.toString() + ",\"cfg\":{\"feeRate\":"
                + StockUtil.num(feeRate, 7) + ",\"minFee\":" + StockUtil.num(minFee, 2) + ",\"stampRate\":" + StockUtil.num(stampRate, 7) + ",\"slipBps\":"
                + StockUtil.num(slipBps, 2) + ",\"maxPosPct\":" + StockUtil.num(maxPosPct, 4) + ",\"anytime\":" + anytime + "},\"since\":" + resetTs
                + ",\"last\":" + StockUtil.js(lastMsg) + ",\"cst\":" + StockFeed.nowCst() + "}");
        return sb.toString();
    }

    // ------------------------------------------------------------------ performance

    /** Equity curve (end-of-day points plus the live value) and trading statistics computed from the fills. */
    static String equityJson() {
        StringBuilder sb = new StringBuilder();
        double eqNow = equityNow();
        int today = (int) (StockFeed.nowCst() / 10000L);
        double[] vals = new double[equity.size() + 2];
        int nv = 0;
        sb.append("{\"initial\":" + StockUtil.num(initial, 2) + ",\"points\":[");
        int cnt = 0;
        int i = 0;
        while (i < equity.size()) {
            ArrayList<String> p = StockNotify.splitTabs(equity.get(i));
            i = i + 1;
            if (p.size() < 4) {
                continue;
            }
            if (StockUtil.parseInt(p.get(0), 0) == today) {
                continue;
            }
            if (cnt > 0) {
                sb.append(",");
            }
            sb.append("[" + p.get(0) + "," + p.get(1) + "," + p.get(2) + "," + p.get(3) + "]");
            vals[nv] = StockUtil.parseDouble(p.get(1), 0.0);
            nv = nv + 1;
            cnt = cnt + 1;
        }
        if (cnt > 0) {
            sb.append(",");
        }
        sb.append("[" + today + "," + StockUtil.num(eqNow, 2) + "," + StockUtil.num(cash, 2) + "," + StockUtil.num(eqNow - cash, 2) + "]");
        vals[nv] = eqNow;
        nv = nv + 1;
        sb.append("],\"stats\":");
        sb.append(statsJson(vals, nv, eqNow));
        sb.append("}");
        return sb.toString();
    }

    static String statsJson(double[] vals, int nv, double eqNow) {
        int wins = 0;
        int losses = 0;
        double grossWin = 0.0;
        double grossLoss = 0.0;
        double fees = 0.0;
        double turnover = 0.0;
        double realized = 0.0;
        int buys = 0;
        int sells = 0;
        int i = 0;
        while (i < fills.size()) {
            TradeFill f = fills.get(i);
            i = i + 1;
            fees = fees + f.fee;
            turnover = turnover + f.amount;
            if (f.side == 1) {
                buys = buys + 1;
            } else {
                sells = sells + 1;
                realized = realized + f.pnl;
                if (f.pnl > 0.0) {
                    wins = wins + 1;
                    grossWin = grossWin + f.pnl;
                } else if (f.pnl < 0.0) {
                    losses = losses + 1;
                    grossLoss = grossLoss - f.pnl;
                }
            }
        }
        double peak = initial;
        double maxDd = 0.0;
        double prev = initial;
        double sum = 0.0;
        double sum2 = 0.0;
        int nr = 0;
        i = 0;
        while (i < nv) {
            double v = vals[i];
            if (v > peak) {
                peak = v;
            }
            if (peak > 0.0) {
                double dd = (peak - v) / peak;
                if (dd > maxDd) {
                    maxDd = dd;
                }
            }
            if (prev > 0.0) {
                double r = v / prev - 1.0;
                sum = sum + r;
                sum2 = sum2 + r * r;
                nr = nr + 1;
            }
            prev = v;
            i = i + 1;
        }
        double sharpe = StockUtil.nan();
        if (nr >= 10) {
            double mean = sum / (double) nr;
            double var = sum2 / (double) nr - mean * mean;
            if (var > 1.0e-12) {
                sharpe = mean / Math.sqrt(var) * Math.sqrt(244.0);
            }
        }
        double winRate = StockUtil.nan();
        if (wins + losses > 0) {
            winRate = (double) wins / (double) (wins + losses);
        }
        double avgWin = StockUtil.nan();
        if (wins > 0) {
            avgWin = grossWin / (double) wins;
        }
        double avgLoss = StockUtil.nan();
        if (losses > 0) {
            avgLoss = grossLoss / (double) losses;
        }
        double pf = StockUtil.nan();
        if (grossLoss > 0.0) {
            pf = grossWin / grossLoss;
        }
        return "{\"ret\":" + StockUtil.num(initial > 0.0 ? (eqNow - initial) / initial : 0.0, 5) + ",\"maxDd\":" + StockUtil.num(maxDd, 5) + ",\"sharpe\":"
                + StockUtil.num(sharpe, 3) + ",\"days\":" + nv + ",\"buys\":" + buys + ",\"sells\":" + sells + ",\"wins\":" + wins + ",\"losses\":"
                + losses + ",\"winRate\":" + StockUtil.num(winRate, 4) + ",\"avgWin\":" + StockUtil.num(avgWin, 2) + ",\"avgLoss\":" + StockUtil.num(avgLoss, 2)
                + ",\"profitFactor\":" + StockUtil.num(pf, 3) + ",\"realized\":" + StockUtil.num(realized, 2) + ",\"fees\":" + StockUtil.num(fees, 2)
                + ",\"turnover\":" + StockUtil.num(turnover, 2) + "}";
    }
}
