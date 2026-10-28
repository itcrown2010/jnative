/**
 * Saved strategy configurations for the portfolio backtest. A configuration is the URL query string the page sent
 * (strategy, parameters, rules, risk and sizing options), stored as "name TAB config" lines in data/_strats.txt.
 * @author 张晓达
 */
class StockStrat {
    static ArrayList<String> names;
    static ArrayList<String> cfgs;
    static int loaded = 0;

    static void init() {
        names = new ArrayList<String>();
        cfgs = new ArrayList<String>();
        loaded = 1;
        try {
            String path = StockStore.dataDir + "/_strats.txt";
            if (!Files.exists(FsPath.of(path))) {
                return;
            }
            String text = File.readString(path);
            int st = 0;
            int i = 0;
            while (i <= text.length()) {
                if (i == text.length() || text.charAt(i) == 10) {
                    String ln = text.substring(st, i);
                    int tab = ln.indexOf("\t");
                    if (tab > 0) {
                        names.add(ln.substring(0, tab));
                        cfgs.add(ln.substring(tab + 1, ln.length()));
                    }
                    st = i + 1;
                }
                i = i + 1;
            }
        } catch (Exception ex) {
            System.out.println("STOCK_STRAT_LOAD_WARN");
        }
    }

    static void save() {
        try {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < names.size()) {
                sb.append(names.get(i) + "\t" + cfgs.get(i) + "\n");
                i = i + 1;
            }
            File.writeString(StockStore.dataDir + "/_strats.txt", sb.toString());
        } catch (Exception ex) {
            System.out.println("STOCK_STRAT_SAVE_WARN");
        }
    }

    static String clean(String s) {
        return s.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    static String list() {
        if (loaded == 0) {
            init();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < names.size()) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"name\":" + StockUtil.js(names.get(i)) + ",\"cfg\":" + StockUtil.js(cfgs.get(i)) + "}");
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    static String put(String name, String cfg) {
        if (loaded == 0) {
            init();
        }
        String nm = clean(name == null ? "" : name);
        if (nm.length() == 0 || nm.length() > 30) {
            return StockUtil.err("BAD_NAME", "策略名称需要 1–30 个字符");
        }
        String c = clean(cfg == null ? "" : cfg);
        if (c.length() == 0 || c.length() > 2000) {
            return StockUtil.err("BAD_CONFIG", "策略配置为空或过长");
        }
        int i = 0;
        while (i < names.size()) {
            if (names.get(i).equals(nm)) {
                cfgs.set(i, c);
                save();
                return list();
            }
            i = i + 1;
        }
        if (names.size() >= 50) {
            return StockUtil.err("TOO_MANY", "最多保存 50 个策略");
        }
        names.add(nm);
        cfgs.add(c);
        save();
        return list();
    }

    static String remove(String name) {
        if (loaded == 0) {
            init();
        }
        int i = 0;
        while (i < names.size()) {
            if (names.get(i).equals(name)) {
                names.remove(i);
                cfgs.remove(i);
                save();
                return list();
            }
            i = i + 1;
        }
        return list();
    }
}
