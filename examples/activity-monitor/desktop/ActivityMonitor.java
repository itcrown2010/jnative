/**
 * macOS Activity Monitor — native desktop UI (Toolbar + TableView + status).
 * Sampling / terminate logic lives in AmCore.java (merged at build).
 *
 * @author 张晓达
 */
public class ActivityMonitor {
    static int demoDetail;

    public static void main(String[] args) {
        demoDetail = 0;
        if (args != null && args.length > 0) {
            String a0 = args[0];
            if (a0.equals("--dump") || a0.equals("--bench") || a0.equals("--self")
                    || a0.equals("--signal-self") || a0.equals("--help")
                    || a0.equals("--find") || a0.equals("--signal") || a0.equals("--check")) {
                AmCore.runCli(args);
                return;
            }
            int ai = 0;
            while (ai < args.length) {
                if (args[ai].equals("--demo-detail")) {
                    demoDetail = 1;
                }
                if (args[ai].equals("--appearance") && ai + 1 < args.length) {
                    AmUi.forceAppearance = args[ai + 1];
                    ai = ai + 1;
                }
                if (args[ai].equals("--view-mode") && ai + 1 < args.length) {
                    AmUi.forceViewMode = args[ai + 1];
                    ai = ai + 1;
                }
                if (args[ai].equals("--window-size") && ai + 2 < args.length) {
                    AmUi.forceWinW = AmParse.intOr(args[ai + 1], 1100);
                    AmUi.forceWinH = AmParse.intOr(args[ai + 2], 720);
                    ai = ai + 2;
                }
                ai = ai + 1;
            }
        }
        int appearance = Theme.MODE_SYSTEM;
        String demo = AmUi.forceAppearance;
        if (demo == null) {
            demo = Env.get("JN_AM_APPEARANCE");
        }
        if (demo != null && demo.equals("dark")) {
            appearance = Theme.MODE_DARK;
        } else if (demo != null && demo.equals("light")) {
            appearance = Theme.MODE_LIGHT;
        }
        Application.setAppName("活动监视器");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        /* Apply after UiNative.init so Cocoa can set NSApp.appearance. */
        Theme.setAppearanceMode(appearance);
        AmWindow.show();
        app.run();
    }
}

class AmUi {
    static String forceAppearance;
    static String forceViewMode;
    static int forceWinW;
    static int forceWinH;
    static Window window;
    static Window detailWin;
    static Label detailTitle;
    static Label detailMeta;
    static Label detailStatus;
    static Label detailValName;
    static Label detailValPid;
    static Label detailValPpid;
    static Label detailValUser;
    static Label detailValCpu;
    static Label detailValMem;
    static Label detailValRss;
    static Label detailValThr;
    static Label detailValPath;
    static Label detailValStart;
    static String detailCopyText;
    static int detailPid;
    static long detailStartSec;
    static int detailStartUsec;
    static Label footPrimary;
    static TrendChart footTrend;
    static Label footStatus;
    static SearchField search;
    static Button detailBtn;
    static Button termBtn;
    static Button moreBtn;
    static Button infoBtn;
    static SegmentedControl modeSeg;
    static TableView<AmProcRow> table;
    static AmViewState state;
    static int alive;
    static int paused;
    static int refreshMs;
    static int sampleToken;
    static int samplerBusy;
    static int refreshGen;
    static int pendingWarmup;
    static AmSnapshot pendingSnapA;
    static AmSnapshot pendingSnapB;
    static int pendingEpoch;
    static int selPid;
    static long selStartSec;
    static int selStartUsec;
    static String searchText;
    /** 0=name 1=pid 2=user 3=cpu 4=mem 5=threads */
    static int sortCol;
    static int sortAsc;
    /** 0=CPU page, 1=Memory page */
    static int viewMode;
    /** 1 = user clicked a column; mode switch still resets to page default. */
    static int sortUserTouched;
    static ArrayList<AmProcRow> viewRows;
    static long lastTableRebuildNs;
    static int tableDirty;
    static String lastSearchApplied;
    static ArrayList<int> cpuHistory;
    static ArrayList<int> memHistory;
    static ArrayList<String> iconCacheKeys;
    static ArrayList<String> iconCachePaths;
    /** Lightweight UI state (not the high-frequency snapshot graph). */
    static BindingScope scope;
    static ObservableInt viewModeObs;
    static ObservableValue<String> searchObs;
    static ObservableValue<String> selectedIdObs;
    static ObservableInt pausedObs;
    static ObservableInt toolEnableObs;

    static void ensure() {
        if (state == null) {
            state = new AmViewState();
        }
        if (refreshMs <= 0) {
            refreshMs = 1500;
        }
        alive = 1;
        paused = 0;
        sampleToken = 0;
        selPid = -1;
        selStartSec = -1;
        selStartUsec = -1;
        searchText = "";
        sortCol = 3;
        sortAsc = 0;
        viewMode = 0;
        sortUserTouched = 0;
        viewRows = new ArrayList<AmProcRow>();
        lastTableRebuildNs = 0;
        tableDirty = 0;
        lastSearchApplied = "";
        cpuHistory = new ArrayList<int>();
        memHistory = new ArrayList<int>();
        iconCacheKeys = new ArrayList<String>();
        iconCachePaths = new ArrayList<String>();
        scope = new BindingScope();
        viewModeObs = new ObservableInt(0);
        searchObs = ObservableValue.ofString("");
        selectedIdObs = ObservableValue.ofString("");
        pausedObs = new ObservableInt(0);
        toolEnableObs = new ObservableInt(0);
    }
}

class AmWindow {
    static void show() {
        AmUi.ensure();
        int winW = 1100;
        int winH = 720;
        if (AmUi.forceWinW >= 720) {
            winW = AmUi.forceWinW;
        }
        if (AmUi.forceWinH >= 420) {
            winH = AmUi.forceWinH;
        }
        AmUi.window = new Window(AmI18n.t("title"), winW, winH);
        /* Routes to Cocoa contentMinSize (logical points). */
        AmUi.window.setMinSize(720, 420);
        AmUi.window.onClose(() -> {
            AmUi.alive = 0;
            AmUi.sampleToken = AmUi.sampleToken + 1;
            AmUi.refreshGen = AmUi.refreshGen + 1;
            if (AmUi.scope != null) {
                AmUi.scope.dispose();
                AmUi.scope = null;
            }
            /* Owned auxiliary windows close via Window.fireClose child pass. */
            AmActions.clearDetailUi();
        });
        Application.onQuit(() -> {
            AmUi.alive = 0;
            AmUi.sampleToken = AmUi.sampleToken + 1;
            AmUi.refreshGen = AmUi.refreshGen + 1;
            if (AmUi.scope != null) {
                AmUi.scope.dispose();
                AmUi.scope = null;
            }
        });

        MenuBar mb = new MenuBar();
        Menu viewMenu = new Menu(AmI18n.t("menu_view"));
        MenuItem pauseItem = new MenuItem(AmI18n.t("pause"), () -> { AmActions.togglePause(); });
        MenuItem refreshItem = new MenuItem(AmI18n.t("refresh_now"), () -> {
            AmUi.tableDirty = 1;
            AmActions.requestSample(1);
        });
        MenuItem iv1 = new MenuItem(AmI18n.t("interval_1s"), () -> {
            AmUi.refreshMs = 1000;
            AmActions.setFootStatus();
        });
        MenuItem iv2 = new MenuItem(AmI18n.t("interval_2s"), () -> {
            AmUi.refreshMs = 2000;
            AmActions.setFootStatus();
        });
        MenuItem iv5 = new MenuItem(AmI18n.t("interval_5s"), () -> {
            AmUi.refreshMs = 5000;
            AmActions.setFootStatus();
        });
        viewMenu.add(pauseItem);
        viewMenu.add(refreshItem);
        viewMenu.add(iv1);
        viewMenu.add(iv2);
        viewMenu.add(iv5);
        mb.add(viewMenu);
        AmUi.window.setMenuBar(mb);

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        Toolbar toolbar = new Toolbar();
        toolbar.spacing = 6;
        toolbar.padding = 6;
        AmUi.termBtn = toolbar.addPushToolButton(AmI18n.t("term"), "xmark.circle",
                AmI18n.t("tip_term"), () -> {
            AmActions.confirmTerminate();
        });
        AmUi.detailBtn = toolbar.addPushToolButton(AmI18n.t("detail"), "info.circle",
                AmI18n.t("tip_detail"), () -> {
            AmActions.showDetail();
        });
        Bindings.bindEnabled(AmUi.detailBtn, AmUi.toolEnableObs, AmUi.scope);
        toolbar.addSeparatorGap();
        int bootMode = 0;
        if (AmUi.forceViewMode != null) {
            if (AmUi.forceViewMode.equals("mem") || AmUi.forceViewMode.equals("memory")
                    || AmUi.forceViewMode.equals("1")) {
                bootMode = 1;
            }
        }
        AmUi.viewMode = bootMode;
        if (bootMode == 0) {
            AmUi.sortCol = 3;
            AmUi.sortAsc = 0;
        } else {
            AmUi.sortCol = 4;
            AmUi.sortAsc = 0;
        }
        AmUi.modeSeg = new SegmentedControl(AmI18n.t("mode_cpu") + "\t" + AmI18n.t("mode_mem"));
        AmUi.viewModeObs.set(bootMode);
        Bindings.bindSegmented(AmUi.modeSeg, AmUi.viewModeObs, AmUi.scope);
        AmUi.scope.addObs(AmUi.viewModeObs.subscribe((ev) -> {
            AmActions.setViewMode(ev.newValue);
        }));
        toolbar.add(AmUi.modeSeg);
        toolbar.addFlexibleSpace();
        AmUi.search = new SearchField();
        AmUi.search.setPlaceholder(AmI18n.t("search_ph"));
        AmUi.search.setOnQueryChanged(() -> {
            AmActions.onSearchChanged();
        });
        AmUi.search.prefWidth = 200;
        AmUi.search.width = 200;
        toolbar.add(AmUi.search);
        AmUi.infoBtn = toolbar.addPushToolButton(AmI18n.t("metrics_btn"), "questionmark.circle",
                AmI18n.t("tip_metrics"), () -> {
            AmActions.showMetricsInfo();
        });
        Menu moreMenu = new Menu(AmI18n.t("more"));
        moreMenu.add(new MenuItem(AmI18n.t("pause"), () -> { AmActions.togglePause(); }));
        moreMenu.add(new MenuItem(AmI18n.t("refresh_now"), () -> {
            AmUi.tableDirty = 1;
            AmActions.requestSample(1);
        }));
        moreMenu.add(new MenuItem(AmI18n.t("interval_1s"), () -> {
            AmUi.refreshMs = 1000;
            AmActions.setFootStatus();
        }));
        moreMenu.add(new MenuItem(AmI18n.t("interval_2s"), () -> {
            AmUi.refreshMs = 2000;
            AmActions.setFootStatus();
        }));
        moreMenu.add(new MenuItem(AmI18n.t("interval_5s"), () -> {
            AmUi.refreshMs = 5000;
            AmActions.setFootStatus();
        }));
        if (UiPlatform.supports(UiPlatform.FEAT_OVERFLOW_MENU) != 0) {
            AmUi.moreBtn = toolbar.addOverflowMenu(AmI18n.t("tip_more"), moreMenu);
        } else {
            AmUi.moreBtn = toolbar.addPushToolButton(AmI18n.t("more"), "ellipsis.circle",
                    AmI18n.t("tip_more"), () -> {
                AmActions.showMoreMenu();
            });
        }
        toolbar.prefHeight = 40;
        toolbar.height = 40;

        AmUi.table = new TableView<AmProcRow>();
        AmUi.table.vgrow = 1;
        AmUi.table.prefHeight = 480;
        AmUi.table.height = 480;
        AmUi.table.setRowIdProvider(new AmRowIds());
        AmUi.table.setEmptyMessage(AmI18n.t("status_sampling"));
        AmActions.rebuildColumns();
        AmUi.table.setIconPathProvider(new AmIconPaths());
        AmUi.table.onSelectionChanged((ev) -> {
            AmActions.onSelect(ev.selectedIndex);
        });
        AmUi.table.onDoubleClick((ev) -> {
            AmActions.showDetail();
        });
        AmUi.table.onHeaderClick((ev) -> {
            AmActions.sort(ev.selectedIndex);
        });

        HBox footer = new HBox();
        footer.spacing = 12;
        footer.padding = 8;
        footer.prefHeight = 52;
        footer.height = 52;
        AmUi.footPrimary = new Label(AmI18n.t("foot_cpu_wait"));
        AmUi.footPrimary.setPrefSize(280, 36);
        Theme.applyStyle(AmUi.footPrimary, Theme.STYLE_BODY);
        AmUi.footTrend = new TrendChart();
        AmUi.footTrend.setCapacity(32);
        AmUi.footTrend.setYRange(0.0, 100.0);
        AmUi.footTrend.setPrefSize(168, 36);
        AmUi.footStatus = new Label(AmI18n.t("status_ready"));
        AmUi.footStatus.setPrefSize(200, 36);
        Theme.applyStyle(AmUi.footStatus, Theme.STYLE_SECONDARY);
        footer.add(AmUi.footPrimary);
        footer.add(AmUi.footTrend);
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.width = 0;
        flex.hgrow = 1;
        footer.add(flex);
        footer.add(AmUi.footStatus);

        root.add(toolbar);
        root.add(AmUi.table);
        root.add(footer);

        AmUi.window.setContent(root);
        AmUi.window.show();
        AmActions.syncToolbarEnabled();
        AmActions.syncModeSeg();
        AmActions.setFootStatus();

        AmActions.requestSample(1);
        AmActions.armRefresh();
    }
}

class AmIconPaths implements TableIconPathProvider<AmProcRow> {
    String iconPath(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "";
        }
        return AmActions.cachedIconPath(row.sample);
    }
}

/** Stable process identity — never use view row index as business id. */
class AmRowIds implements TableRowIdProvider<AmProcRow> {
    String rowId(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "";
        }
        return String.valueOf(row.sample.pid) + ":"
                + AmParse.longToString(row.sample.startSec) + ":"
                + String.valueOf(row.sample.startUsec);
    }
}

class AmColName implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return AmActions.colTitle(0, AmI18n.t("col_name"));
    }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null || r.sample.name == null) {
            return AmFormat.na();
        }
        if ((r.sample.flags & AmFlags.NAME) == 0) {
            return AmFormat.na();
        }
        return r.sample.name;
    }
}

class AmColPid implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return AmActions.colTitle(1, AmI18n.t("col_pid"));
    }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        return String.valueOf(r.sample.pid);
    }
}

class AmColUser implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return AmActions.colTitle(2, AmI18n.t("col_user"));
    }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null || (r.sample.flags & AmFlags.USER) == 0) {
            return AmFormat.na();
        }
        if (r.sample.user == null) {
            return AmFormat.na();
        }
        return r.sample.user;
    }
}

class AmColCpu implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return AmActions.colTitle(3, AmI18n.t("col_cpu"));
    }
    String renderRow(AmProcRow r) {
        if (r == null) {
            return AmFormat.na();
        }
        return AmFormat.cpuPct(r.cpuMilli);
    }
}

class AmColMem implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        String base = AmI18n.t("col_mem_foot");
        if (AmUi.viewMode == 1) {
            base = AmI18n.t("col_mem_foot");
        }
        return AmActions.colTitle(4, base);
    }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        /* Memory page & CPU page: primary display = physical footprint (documented). */
        if ((r.sample.flags & AmFlags.FOOTPRINT) == 0) {
            return AmFormat.na();
        }
        return AmFormat.bytes(r.sample.footprint);
    }
}

class AmColThr implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return AmActions.colTitle(5, AmI18n.t("col_thr"));
    }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        return AmFormat.threads(r.sample);
    }
}

class AmI18n {
    static String lang;

    static String t(String key) {
        if (lang == null) {
            lang = "zh";
            String env = Env.get("LANG");
            if (env != null && env.length() >= 2) {
                if (env.substring(0, 2).equals("en")) {
                    lang = "en";
                }
            }
        }
        if (lang.equals("en")) {
            return en(key);
        }
        return zh(key);
    }

    static String zh(String key) {
        if (key.equals("title")) return "活动监视器";
        if (key.equals("menu_view")) return "显示";
        if (key.equals("pause")) return "暂停刷新";
        if (key.equals("resume")) return "恢复刷新";
        if (key.equals("refresh_now")) return "立即刷新";
        if (key.equals("interval_1s")) return "刷新：1 秒";
        if (key.equals("interval_2s")) return "刷新：2 秒";
        if (key.equals("interval_5s")) return "刷新：5 秒";
        if (key.equals("detail")) return "详情";
        if (key.equals("term")) return "结束进程";
        if (key.equals("more")) return "⋯";
        if (key.equals("mode_cpu")) return "CPU";
        if (key.equals("mode_mem")) return "内存";
        if (key.equals("search_ph")) return "搜索";
        if (key.equals("status_ready")) return "就绪";
        if (key.equals("status_sampling")) return "正在采样";
        if (key.equals("status_paused")) return "已暂停";
        if (key.equals("col_name")) return "进程名称";
        if (key.equals("col_pid")) return "PID";
        if (key.equals("col_user")) return "用户";
        if (key.equals("col_cpu")) return "CPU %";
        if (key.equals("col_mem_foot")) return "内存占用";
        if (key.equals("col_thr")) return "线程";
        if (key.equals("foot_cpu_wait")) return "CPU\n等待采样";
        if (key.equals("foot_mem_wait")) return "内存\n等待采样";
        if (key.equals("metrics_btn")) return "说明";
        if (key.equals("metrics_hint")) return "内存占用 = 物理占用（ri_phys_footprint）；RSS 见进程详情。CPU 100% = 1 逻辑核心。";
        if (key.equals("tip_term")) return "结束所选进程";
        if (key.equals("tip_detail")) return "查看进程详情";
        if (key.equals("tip_more")) return "暂停、刷新与间隔";
        if (key.equals("tip_metrics")) return "指标口径说明";
        if (key.equals("spark_wait")) return "等待采样";
        if (key.equals("no_sel")) return "请先选择一个进程";
        if (key.equals("detail_title")) return "进程详情";
        if (key.equals("gone")) return "所选进程已退出或身份失效";
        if (key.equals("d_name")) return "名称";
        if (key.equals("d_pid")) return "PID";
        if (key.equals("d_ppid")) return "PPID";
        if (key.equals("d_user")) return "用户";
        if (key.equals("d_cpu")) return "CPU";
        if (key.equals("d_mem")) return "内存";
        if (key.equals("d_rss")) return "RSS";
        if (key.equals("d_thr")) return "线程";
        if (key.equals("d_path")) return "路径";
        if (key.equals("d_start")) return "启动";
        if (key.equals("d_copy")) return "复制";
        if (key.equals("d_copy_tip")) return "复制详情到剪贴板";
        if (key.equals("d_copied")) return "已复制到剪贴板";
        if (key.equals("d_protected")) return "受保护：";
        return key;
    }

    static String en(String key) {
        if (key.equals("title")) return "Activity Monitor";
        if (key.equals("menu_view")) return "View";
        if (key.equals("pause")) return "Pause";
        if (key.equals("resume")) return "Resume";
        if (key.equals("refresh_now")) return "Refresh Now";
        if (key.equals("interval_1s")) return "Interval: 1s";
        if (key.equals("interval_2s")) return "Interval: 2s";
        if (key.equals("interval_5s")) return "Interval: 5s";
        if (key.equals("detail")) return "Inspect";
        if (key.equals("term")) return "Quit";
        if (key.equals("more")) return "⋯";
        if (key.equals("mode_cpu")) return "CPU";
        if (key.equals("mode_mem")) return "Memory";
        if (key.equals("search_ph")) return "Search";
        if (key.equals("status_ready")) return "Ready";
        if (key.equals("status_sampling")) return "Sampling";
        if (key.equals("status_paused")) return "Paused";
        if (key.equals("col_name")) return "Process Name";
        if (key.equals("col_pid")) return "PID";
        if (key.equals("col_user")) return "User";
        if (key.equals("col_cpu")) return "CPU %";
        if (key.equals("col_mem_foot")) return "Memory";
        if (key.equals("col_thr")) return "Threads";
        if (key.equals("foot_cpu_wait")) return "CPU\nWaiting";
        if (key.equals("foot_mem_wait")) return "Memory\nWaiting";
        if (key.equals("metrics_btn")) return "Info";
        if (key.equals("metrics_hint")) return "Memory = phys footprint (ri_phys_footprint); RSS in Inspect. CPU 100% = 1 logical core.";
        if (key.equals("tip_term")) return "Quit selected process";
        if (key.equals("tip_detail")) return "Show process details";
        if (key.equals("tip_more")) return "Pause, refresh, interval";
        if (key.equals("tip_metrics")) return "Metric definitions";
        if (key.equals("spark_wait")) return "Waiting";
        if (key.equals("no_sel")) return "Select a process first";
        if (key.equals("detail_title")) return "Process";
        if (key.equals("gone")) return "Process exited or identity changed";
        if (key.equals("d_name")) return "Name";
        if (key.equals("d_pid")) return "PID";
        if (key.equals("d_ppid")) return "PPID";
        if (key.equals("d_user")) return "User";
        if (key.equals("d_cpu")) return "CPU";
        if (key.equals("d_mem")) return "Memory";
        if (key.equals("d_rss")) return "RSS";
        if (key.equals("d_thr")) return "Threads";
        if (key.equals("d_path")) return "Path";
        if (key.equals("d_start")) return "Started";
        if (key.equals("d_copy")) return "Copy";
        if (key.equals("d_copy_tip")) return "Copy details to clipboard";
        if (key.equals("d_copied")) return "Copied to clipboard";
        if (key.equals("d_protected")) return "Protected: ";
        return key;
    }
}

class AmActions {
    static String colTitle(int col, String base) {
        if (AmUi.sortCol != col) {
            return base;
        }
        if (AmUi.sortAsc != 0) {
            return base + " ▲";
        }
        return base + " ▼";
    }

    static void rebuildColumns() {
        if (AmUi.table == null) {
            return;
        }
        AmUi.table.columns.clear();
        AmUi.table.addColumnDef(AmActions.colTitle(0, AmI18n.t("col_name")),
                new AmColName(), 0, "L", 1);
        AmUi.table.addColumnDef(AmActions.colTitle(1, AmI18n.t("col_pid")),
                new AmColPid(), 88, "R", 0);
        AmUi.table.addColumnDef(AmActions.colTitle(2, AmI18n.t("col_user")),
                new AmColUser(), 128, "L", 0);
        AmUi.table.addColumnDef(AmActions.colTitle(3, AmI18n.t("col_cpu")),
                new AmColCpu(), 84, "R", 0);
        AmUi.table.addColumnDef(AmActions.colTitle(4, AmI18n.t("col_mem_foot")),
                new AmColMem(), 100, "R", 0);
        AmUi.table.addColumnDef(AmActions.colTitle(5, AmI18n.t("col_thr")),
                new AmColThr(), 64, "R", 0);
    }

    static void syncModeSeg() {
        if (AmUi.modeSeg != null) {
            AmUi.modeSeg.setSelectedIndex(AmUi.viewMode);
        }
    }

    static void setViewMode(int mode) {
        if (mode != 0 && mode != 1) {
            return;
        }
        if (AmUi.viewMode == mode) {
            return;
        }
        AmUi.viewMode = mode;
        if (AmUi.viewModeObs != null && AmUi.viewModeObs.get() != mode) {
            AmUi.viewModeObs.set(mode);
        }
        /* Switching page applies that page's default sort (does not stick across pages). */
        AmUi.sortUserTouched = 0;
        if (mode == 0) {
            AmUi.sortCol = 3;
            AmUi.sortAsc = 0;
        } else {
            AmUi.sortCol = 4;
            AmUi.sortAsc = 0;
        }
        syncModeSeg();
        AmUi.tableDirty = 1;
        rebuildColumns();
        maybeRebuildTable(1);
        updateFooter();
        setFootStatus();
    }

    static void syncToolbarEnabled() {
        int on = 0;
        if (AmUi.selPid > 0) {
            AmProcRow r = selectedRow();
            if (r != null) {
                on = 1;
            }
        }
        if (AmUi.toolEnableObs != null) {
            AmUi.toolEnableObs.set(on);
        }
        if (AmUi.detailBtn != null) {
            if (on != 0) {
                AmUi.detailBtn.setEnabled(true);
            } else {
                AmUi.detailBtn.setEnabled(false);
            }
        }
        if (AmUi.termBtn != null) {
            int allow = 0;
            if (on != 0) {
                AmProcRow r = selectedRow();
                if (r != null && AmProtect.isProtected(r.sample) == 0) {
                    allow = 1;
                }
            }
            if (allow != 0) {
                AmUi.termBtn.setEnabled(true);
            } else {
                AmUi.termBtn.setEnabled(false);
            }
        }
    }

    static void setFootStatus() {
        if (AmUi.footStatus == null) {
            return;
        }
        String st = AmI18n.t("status_ready");
        if (AmUi.paused != 0) {
            st = AmI18n.t("status_paused");
        } else if (AmUi.state != null && AmUi.state.sampleCount < 2) {
            st = AmI18n.t("status_sampling");
        }
        int n = 0;
        if (AmUi.state != null) {
            n = AmUi.state.rows.size();
        }
        AmUi.footStatus.setText(st + "\n" + String.valueOf(n) + " 进程 · "
                + String.valueOf(AmUi.refreshMs) + " ms");
    }

    static void togglePause() {
        if (AmUi.paused != 0) {
            AmUi.paused = 0;
        } else {
            AmUi.paused = 1;
        }
        if (AmUi.pausedObs != null) {
            AmUi.pausedObs.set(AmUi.paused);
        }
        setFootStatus();
        if (AmUi.paused == 0) {
            requestSample(0);
        }
    }

    static void showMoreMenu() {
        String msg = AmI18n.t("pause") + " / " + AmI18n.t("resume") + "\n"
                + AmI18n.t("refresh_now") + "\n"
                + AmI18n.t("interval_1s") + " / " + AmI18n.t("interval_2s") + " / " + AmI18n.t("interval_5s")
                + "\n\n（完整项见菜单「显示」）";
        if (Dialogs.confirm(AmUi.window, msg + "\n\n切换暂停？")) {
            togglePause();
        }
    }

    static void showMetricsInfo() {
        Dialogs.info(AmUi.window, AmI18n.t("metrics_hint"));
    }

    static void onSearchChanged() {
        if (AmUi.search == null) {
            return;
        }
        String q = AmUi.search.getQuery();
        if (q == null) {
            q = "";
        }
        AmUi.searchText = q;
        if (AmUi.searchObs != null) {
            AmUi.searchObs.set(q);
        }
        AmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    /**
     * One-shot sleep thread that re-arms itself from the UI thread after each tick.
     * A single long-lived while/sleep loop was exiting after the first cycle under Cocoa.
     */
    static void armRefresh() {
        if (AmUi.alive == 0) {
            return;
        }
        AmUi.refreshGen = AmUi.refreshGen + 1;
        Thread t = new Thread(() -> {
            int gen = AmUi.refreshGen;
            try {
                Thread.sleep(AmUi.refreshMs);
            } catch (Exception e) {
            }
            Application.invokeLater(() -> {
                if (AmUi.alive == 0 || gen != AmUi.refreshGen) {
                    return;
                }
                if (AmUi.paused == 0) {
                    requestSample(0);
                }
                armRefresh();
            });
        });
        t.start();
    }

    static void applyOneSnapshot(AmSnapshot snap) {
        if (snap == null) {
            return;
        }
        AmUi.state.applySnapshot(snap);
        if (AmUi.state.sysCpuMilli >= 0) {
            AmUi.cpuHistory.add(AmUi.state.sysCpuMilli);
            while (AmUi.cpuHistory.size() > 32) {
                AmUi.cpuHistory.removeAt(0);
            }
        }
        if (AmUi.state.sysMemUsedBytes >= 0 && AmUi.state.memTotal > 0) {
            int memMilli = (int)((AmUi.state.sysMemUsedBytes * 10000L) / AmUi.state.memTotal);
            AmUi.memHistory.add(memMilli);
            while (AmUi.memHistory.size() > 32) {
                AmUi.memHistory.removeAt(0);
            }
        }
    }

    static void finishSampleUi(int force) {
        updateFooter();
        if (AmUi.state.sampleCount >= 2) {
            if (force != 0 || AmUi.tableDirty != 0 || AmUi.lastTableRebuildNs == 0) {
                maybeRebuildTable(1);
            }
            if (ActivityMonitor.demoDetail != 0 && AmUi.detailWin == null
                    && AmUi.viewRows != null && AmUi.viewRows.size() > 0) {
                int di = 0;
                int pick = 0;
                while (di < AmUi.viewRows.size()) {
                    AmProcRow rr = AmUi.viewRows.get(di);
                    if (rr != null && rr.sample != null && rr.sample.name != null
                            && rr.sample.name.equals("ActivityMonitor")) {
                        pick = di;
                        break;
                    }
                    di = di + 1;
                }
                AmUi.table.select(pick);
                AmActions.onSelect(pick);
                AmActions.showDetail();
                ActivityMonitor.demoDetail = 0;
            }
        }
        setFootStatus();
        syncToolbarEnabled();
        refreshDetailIfOpen();
    }

    static void requestSample(int force) {
        if (AmUi.alive == 0) {
            return;
        }
        if (AmUi.samplerBusy != 0 && force == 0) {
            return;
        }
        if (AmSampler.inFlight != 0 && force == 0) {
            return;
        }
        AmUi.pendingEpoch = AmUi.sampleToken;
        AmUi.pendingWarmup = 0;
        if (AmUi.state == null || AmUi.state.sampleCount < 2) {
            AmUi.pendingWarmup = 1;
            setFootStatus();
        }
        if (AmUi.search != null) {
            String q = AmUi.search.getQuery();
            if (q == null) {
                q = "";
            }
            AmUi.searchText = q;
        }
        AmUi.samplerBusy = 1;
        Thread t = new Thread(() -> {
            AmUi.pendingSnapA = AmSampler.take();
            AmUi.pendingSnapB = null;
            if (AmUi.pendingWarmup != 0 && AmUi.pendingSnapA != null) {
                try {
                    Thread.sleep(250);
                } catch (Exception e) {
                }
                AmUi.pendingSnapB = AmSampler.take();
            }
            Application.invokeLater(() -> {
                AmUi.samplerBusy = 0;
                if (AmUi.alive == 0 || AmUi.pendingEpoch != AmUi.sampleToken) {
                    return;
                }
                int warm = AmUi.pendingWarmup;
                applyOneSnapshot(AmUi.pendingSnapA);
                applyOneSnapshot(AmUi.pendingSnapB);
                AmUi.pendingSnapA = null;
                AmUi.pendingSnapB = null;
                /* Full table rebuild on warmup / dirty only — periodic samples
                   update footer to avoid Cocoa setItems crash on every tick. */
                int forceTable = 0;
                if (warm != 0 || AmUi.lastTableRebuildNs == 0 || AmUi.tableDirty != 0) {
                    forceTable = 1;
                }
                finishSampleUi(forceTable);
            });
        });
        t.start();
    }

    static void maybeRebuildTable(int force) {
        if (AmUi.search != null) {
            String q = AmUi.search.getQuery();
            if (q == null) {
                q = "";
            }
            if (!q.equals(AmUi.lastSearchApplied)) {
                AmUi.searchText = q;
                AmUi.tableDirty = 1;
            }
        }
        int due = 0;
        if (force != 0 || AmUi.tableDirty != 0) {
            due = 1;
        }
        if (AmUi.lastTableRebuildNs == 0) {
            due = 1;
        }
        if (due == 0) {
            return;
        }
        applyToTable();
        AmUi.lastTableRebuildNs = AmNative.monoNs();
        AmUi.tableDirty = 0;
        AmUi.lastSearchApplied = AmUi.searchText;
    }

    static void updateFooter() {
        if (AmUi.footPrimary == null || AmUi.state == null) {
            return;
        }
        if (AmUi.viewMode == 0) {
            String cpu = AmFormat.cpuPct(AmUi.state.sysCpuMilli);
            AmUi.footPrimary.setText("CPU  " + cpu + "\n"
                    + String.valueOf(AmUi.state.ncpu) + " 逻辑核心");
            if (AmUi.footTrend != null) {
                AmUi.footTrend.setYRange(0.0, 100.0);
                AmUi.footTrend.setSamplesFromMilli(AmUi.cpuHistory, 0.01);
            }
        } else {
            String used = AmFormat.bytes(AmUi.state.sysMemUsedBytes);
            String total = AmFormat.bytes(AmUi.state.memTotal);
            String free = AmFormat.bytes(AmUi.state.sysMemFreeBytes);
            AmUi.footPrimary.setText("内存占用  " + used + " / " + total + "\n"
                    + "空闲页 " + free);
            if (AmUi.footTrend != null) {
                AmUi.footTrend.setYRange(0.0, 100.0);
                AmUi.footTrend.setSamplesFromMilli(AmUi.memHistory, 0.01);
            }
        }
    }

    static void applyToTable() {
        if (AmUi.table == null || AmUi.state == null) {
            return;
        }
        ArrayList<AmProcRow> filtered = AmUi.state.filtered(AmUi.searchText);
        ArrayList<AmProcRow> sorted = sortRows(filtered, AmUi.sortCol, AmUi.sortAsc);
        ArrayList<AmProcRow> shown = new ArrayList<AmProcRow>();
        int maxShow = 400;
        int si = 0;
        while (si < sorted.size() && si < maxShow) {
            AmProcRow rr = sorted.get(si);
            if (rr != null && rr.sample != null) {
                if (rr.sample.name == null) {
                    rr.sample.name = "";
                }
                if (rr.sample.user == null) {
                    rr.sample.user = "";
                }
                shown.add(rr);
            }
            si = si + 1;
        }
        AmUi.viewRows = shown;
        /* TableView retains selection by stable row id across setItems. */
        AmUi.table.setItems(AmUi.viewRows);
        if (AmUi.selPid > 0) {
            String want = String.valueOf(AmUi.selPid) + ":"
                    + AmParse.longToString(AmUi.selStartSec) + ":"
                    + String.valueOf(AmUi.selStartUsec);
            AmUi.table.selectByRowId(want);
            if (AmUi.table.getSelectedRowId() == null
                    || !want.equals(AmUi.table.getSelectedRowId())) {
                AmUi.selPid = -1;
            }
        }
        syncToolbarEnabled();
    }

    static ArrayList<AmProcRow> sortRows(ArrayList<AmProcRow> src, int col, int asc) {
        ArrayList<AmProcRow> out = new ArrayList<AmProcRow>();
        int i = 0;
        while (i < src.size()) {
            out.add(src.get(i));
            i = i + 1;
        }
        int n = out.size();
        int a = 1;
        while (a < n) {
            AmProcRow key = out.get(a);
            String keyS = sortKey(key, col);
            int j = a - 1;
            int moving = 1;
            while (j >= 0 && moving != 0) {
                String js = sortKey(out.get(j), col);
                int cmp = UiStrings.compare(keyS, js);
                int outOfOrder = 0;
                if (asc != 0 && cmp < 0) {
                    outOfOrder = 1;
                }
                if (asc == 0 && cmp > 0) {
                    outOfOrder = 1;
                }
                if (outOfOrder == 0) {
                    moving = 0;
                } else {
                    out.set(j + 1, out.get(j));
                    j = j - 1;
                }
            }
            out.set(j + 1, key);
            a = a + 1;
        }
        return out;
    }

    static String sortKey(AmProcRow r, int col) {
        if (col == 0) {
            return r.sample.name;
        }
        if (col == 1) {
            String s = String.valueOf(r.sample.pid);
            while (s.length() < 8) {
                s = "0" + s;
            }
            return s;
        }
        if (col == 2) {
            return r.sample.user;
        }
        if (col == 3) {
            return AmFormat.cpuSortKey(r.cpuMilli);
        }
        if (col == 4) {
            return AmFormat.bytesSortKey(r.sample.footprint);
        }
        if (col == 5) {
            String s = String.valueOf(r.sample.nthreads);
            while (s.length() < 6) {
                s = "0" + s;
            }
            return s;
        }
        return "";
    }

    static void sort(int col) {
        if (col < 0 || col > 5) {
            return;
        }
        AmUi.sortUserTouched = 1;
        if (AmUi.sortCol == col) {
            if (AmUi.sortAsc != 0) {
                AmUi.sortAsc = 0;
            } else {
                AmUi.sortAsc = 1;
            }
        } else {
            AmUi.sortCol = col;
            AmUi.sortAsc = 0;
        }
        rebuildColumns();
        AmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static void onSelect(int viewIndex) {
        if (viewIndex < 0 || AmUi.viewRows == null || viewIndex >= AmUi.viewRows.size()) {
            AmUi.selPid = -1;
            if (AmUi.selectedIdObs != null) {
                AmUi.selectedIdObs.set("");
            }
            syncToolbarEnabled();
            return;
        }
        AmProcRow r = AmUi.viewRows.get(viewIndex);
        AmUi.selPid = r.sample.pid;
        AmUi.selStartSec = r.sample.startSec;
        AmUi.selStartUsec = r.sample.startUsec;
        if (AmUi.selectedIdObs != null) {
            AmUi.selectedIdObs.set(String.valueOf(r.sample.pid) + ":"
                    + AmParse.longToString(r.sample.startSec) + ":"
                    + String.valueOf(r.sample.startUsec));
        }
        syncToolbarEnabled();
        refreshDetailIfOpen();
    }

    static AmProcRow selectedRow() {
        if (AmUi.selPid <= 0 || AmUi.state == null) {
            return null;
        }
        return AmUi.state.findByIdentity(AmUi.selPid, AmUi.selStartSec, AmUi.selStartUsec);
    }

    static String cachedIconPath(AmProcSample s) {
        if (s == null) {
            return "";
        }
        String key = s.identityKey();
        int i = 0;
        while (i < AmUi.iconCacheKeys.size()) {
            if (AmUi.iconCacheKeys.get(i).equals(key)) {
                return AmUi.iconCachePaths.get(i);
            }
            i = i + 1;
        }
        String p = "";
        if ((s.flags & AmFlags.PATH) != 0 && s.path != null) {
            p = s.path;
            /* Prefer .app bundle root when path is inside Contents/MacOS. */
            int ix = AmActions.findAppBundle(p);
            if (ix > 0) {
                p = p.substring(0, ix + 4);
            }
        }
        AmUi.iconCacheKeys.add(key);
        AmUi.iconCachePaths.add(p);
        while (AmUi.iconCacheKeys.size() > 800) {
            AmUi.iconCacheKeys.removeAt(0);
            AmUi.iconCachePaths.removeAt(0);
        }
        return p;
    }

    static int findAppBundle(String p) {
        if (p == null) {
            return -1;
        }
        int i = 0;
        while (i + 5 <= p.length()) {
            if (p.charAt(i) == 46 && p.charAt(i + 1) == 97 && p.charAt(i + 2) == 112
                    && p.charAt(i + 3) == 112 && p.charAt(i + 4) == 47) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    static void clearDetailUi() {
        AmUi.detailTitle = null;
        AmUi.detailMeta = null;
        AmUi.detailStatus = null;
        AmUi.detailValName = null;
        AmUi.detailValPid = null;
        AmUi.detailValPpid = null;
        AmUi.detailValUser = null;
        AmUi.detailValCpu = null;
        AmUi.detailValMem = null;
        AmUi.detailValRss = null;
        AmUi.detailValThr = null;
        AmUi.detailValPath = null;
        AmUi.detailValStart = null;
        AmUi.detailCopyText = "";
        AmUi.detailPid = -1;
        AmUi.detailStartSec = 0;
        AmUi.detailStartUsec = 0;
    }

    static Label detailKeyLabel(String text) {
        Label k = new Label(text);
        k.setPrefSize(72, 22);
        Theme.applyStyle(k, Theme.STYLE_SECONDARY);
        return k;
    }

    static Label detailValLabel() {
        Label v = new Label("");
        v.setPrefSize(320, 22);
        Theme.applyStyle(v, Theme.STYLE_BODY);
        return v;
    }

    static HBox detailRow(Label key, Label val) {
        HBox row = new HBox();
        row.spacing = 12;
        row.padding = 0;
        row.prefHeight = 24;
        row.height = 24;
        row.add(key);
        row.add(val);
        return row;
    }

    static void buildDetailWindow() {
        AmUi.detailWin = new Window(AmI18n.t("detail_title"), 460, 520);
        AmUi.detailWin.setMinSize(380, 420);
        AmUi.detailWin.setOwnerWindow(AmUi.window);
        AmUi.detailWin.onClose(() -> {
            AmUi.detailWin = null;
            clearDetailUi();
        });

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        VBox header = new VBox();
        header.spacing = 2;
        header.padding = 16;
        header.prefHeight = 64;
        header.height = 64;
        AmUi.detailTitle = new Label("");
        AmUi.detailTitle.setPrefSize(420, 26);
        Theme.applyStyle(AmUi.detailTitle, Theme.STYLE_TITLE);
        AmUi.detailMeta = new Label("");
        AmUi.detailMeta.setPrefSize(420, 18);
        Theme.applyStyle(AmUi.detailMeta, Theme.STYLE_SECONDARY);
        AmUi.detailStatus = new Label("");
        AmUi.detailStatus.setPrefSize(420, 16);
        Theme.applyStyle(AmUi.detailStatus, Theme.STYLE_SECONDARY);
        header.add(AmUi.detailTitle);
        header.add(AmUi.detailMeta);
        header.add(AmUi.detailStatus);

        VBox fields = new VBox();
        fields.spacing = 4;
        fields.padding = 16;
        fields.vgrow = 1;
        AmUi.detailValName = detailValLabel();
        AmUi.detailValPid = detailValLabel();
        AmUi.detailValPpid = detailValLabel();
        AmUi.detailValUser = detailValLabel();
        AmUi.detailValCpu = detailValLabel();
        AmUi.detailValMem = detailValLabel();
        AmUi.detailValRss = detailValLabel();
        AmUi.detailValThr = detailValLabel();
        AmUi.detailValPath = detailValLabel();
        AmUi.detailValPath.setPrefSize(340, 36);
        AmUi.detailValStart = detailValLabel();
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_name")), AmUi.detailValName));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_pid")), AmUi.detailValPid));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_ppid")), AmUi.detailValPpid));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_user")), AmUi.detailValUser));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_cpu")), AmUi.detailValCpu));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_mem")), AmUi.detailValMem));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_rss")), AmUi.detailValRss));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_thr")), AmUi.detailValThr));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_path")), AmUi.detailValPath));
        fields.add(detailRow(detailKeyLabel(AmI18n.t("d_start")), AmUi.detailValStart));

        HBox footer = new HBox();
        footer.spacing = 8;
        footer.padding = 14;
        footer.prefHeight = 52;
        footer.height = 52;
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.width = 0;
        flex.hgrow = 1;
        footer.add(flex);
        Button copyBtn = new Button(AmI18n.t("d_copy"));
        copyBtn.setChromeStyle(3);
        copyBtn.setToolTip(AmI18n.t("d_copy_tip"));
        copyBtn.onClick(() -> {
            String t = AmUi.detailCopyText;
            if (t == null) {
                t = "";
            }
            Clipboard.setText(t);
            Dialogs.info(AmUi.detailWin, AmI18n.t("d_copied"));
        });
        footer.add(copyBtn);

        root.add(header);
        root.add(fields);
        root.add(footer);
        AmUi.detailWin.setContent(root);
        AmUi.detailWin.show();
    }

    static void showDetail() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(AmUi.window, AmI18n.t("no_sel"));
            return;
        }
        if (AmUi.detailWin == null) {
            buildDetailWindow();
        } else {
            AmUi.detailWin.activate();
        }
        fillDetail(r);
    }

    static void markDetailGone() {
        if (AmUi.detailWin == null || AmUi.detailTitle == null) {
            return;
        }
        AmUi.detailStatus.setText(AmI18n.t("gone"));
        Theme.applyStyle(AmUi.detailStatus, Theme.STYLE_ERROR);
    }

    static AmProcRow detailTrackedRow() {
        if (AmUi.detailPid <= 0 || AmUi.state == null) {
            return null;
        }
        return AmUi.state.findByIdentity(AmUi.detailPid, AmUi.detailStartSec, AmUi.detailStartUsec);
    }

    static void refreshDetailIfOpen() {
        if (AmUi.detailWin == null || AmUi.detailTitle == null) {
            return;
        }
        AmProcRow r = detailTrackedRow();
        if (r == null) {
            markDetailGone();
            return;
        }
        fillDetail(r);
    }

    static String formatStartTime(long startSec, int startUsec) {
        if (startSec <= 0) {
            return AmFormat.na();
        }
        Instant inst = Instant.ofEpochSecond(startSec);
        ZoneId zone = ZoneId.UTC();
        if (AmI18n.lang == null || AmI18n.lang.equals("zh")) {
            zone = ZoneId.of("+08:00");
        }
        ZonedDateTime zdt = ZonedDateTime.ofInstant(inst, zone);
        String s = zdt.toLocalDateTime().toString();
        int t = s.indexOf("T");
        if (t > 0) {
            s = s.substring(0, t) + " " + s.substring(t + 1, s.length());
        }
        return s;
    }

    static void fillDetail(AmProcRow r) {
        if (r == null || r.sample == null || AmUi.detailTitle == null) {
            return;
        }
        AmUi.detailPid = r.sample.pid;
        AmUi.detailStartSec = r.sample.startSec;
        AmUi.detailStartUsec = r.sample.startUsec;
        String path = r.sample.path;
        if (path == null || path.length() == 0 || (r.sample.flags & AmFlags.PATH) == 0) {
            path = AmFormat.na();
        }
        String rss = AmFormat.na();
        if ((r.sample.flags & AmFlags.RSS) != 0) {
            rss = AmFormat.bytes(r.sample.rss);
        }
        String foot = AmFormat.na();
        if ((r.sample.flags & AmFlags.FOOTPRINT) != 0) {
            foot = AmFormat.bytes(r.sample.footprint);
        }
        String name = r.sample.name;
        if (name == null || name.length() == 0) {
            name = AmFormat.na();
        }
        AmUi.detailTitle.setText(name);
        AmUi.detailMeta.setText("PID " + String.valueOf(r.sample.pid) + "  ·  "
                + r.sample.user + "  ·  uid " + String.valueOf(r.sample.uid));
        if (AmProtect.isProtected(r.sample) != 0) {
            AmUi.detailStatus.setText(AmI18n.t("d_protected") + AmProtect.reason(r.sample));
            Theme.applyStyle(AmUi.detailStatus, Theme.STYLE_ERROR);
        } else {
            AmUi.detailStatus.setText("");
            Theme.applyStyle(AmUi.detailStatus, Theme.STYLE_SECONDARY);
        }
        AmUi.detailValName.setText(name);
        AmUi.detailValPid.setText(String.valueOf(r.sample.pid));
        AmUi.detailValPpid.setText(String.valueOf(r.sample.ppid));
        AmUi.detailValUser.setText(r.sample.user);
        AmUi.detailValCpu.setText(AmFormat.cpuPct(r.cpuMilli));
        AmUi.detailValMem.setText(foot);
        AmUi.detailValRss.setText(rss);
        AmUi.detailValThr.setText(AmFormat.threads(r.sample));
        AmUi.detailValPath.setText(path);
        AmUi.detailValStart.setText(formatStartTime(r.sample.startSec, r.sample.startUsec));
        AmUi.detailCopyText = formatDetailPlain(r, name, path, foot, rss);
    }

    static String formatDetailPlain(AmProcRow r, String name, String path, String foot, String rss) {
        String prot = "";
        if (AmProtect.isProtected(r.sample) != 0) {
            prot = "\n" + AmI18n.t("d_protected") + AmProtect.reason(r.sample);
        }
        return AmI18n.t("d_name") + "\t" + name + "\n"
                + AmI18n.t("d_pid") + "\t" + String.valueOf(r.sample.pid) + "\n"
                + AmI18n.t("d_ppid") + "\t" + String.valueOf(r.sample.ppid) + "\n"
                + AmI18n.t("d_user") + "\t" + r.sample.user + "\n"
                + AmI18n.t("d_cpu") + "\t" + AmFormat.cpuPct(r.cpuMilli) + "\n"
                + AmI18n.t("d_mem") + "\t" + foot + "\n"
                + AmI18n.t("d_rss") + "\t" + rss + "\n"
                + AmI18n.t("d_thr") + "\t" + AmFormat.threads(r.sample) + "\n"
                + AmI18n.t("d_path") + "\t" + path + "\n"
                + AmI18n.t("d_start") + "\t"
                + formatStartTime(r.sample.startSec, r.sample.startUsec)
                + prot;
    }

    static void confirmTerminate() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(AmUi.window, AmI18n.t("no_sel"));
            return;
        }
        if (AmProtect.isProtected(r.sample) != 0) {
            Dialogs.error(AmUi.window, "无法结束：" + AmProtect.reason(r.sample));
            return;
        }
        String msg = "名称：" + r.sample.name + "\n"
                + "PID：" + String.valueOf(r.sample.pid) + "\n\n"
                + "将校验启动时间以防 PID 复用。\n"
                + "「结束进程」= SIGTERM；「强制结束」= SIGKILL。\n"
                + "信号发送成功不等于进程已退出。";
        int choice = Dialogs.terminateChoose(AmUi.window, AmI18n.t("term"), msg);
        if (choice == 0) {
            return;
        }
        int sig = AmKill.SIGTERM;
        if (choice == 2) {
            sig = AmKill.SIGKILL;
        }
        AmProcSample target = r.sample;
        int code = AmKill.terminate(target, sig);
        if (code != 0) {
            Dialogs.error(AmUi.window, AmKill.describeError(code));
            return;
        }
        int pid = target.pid;
        long ss = target.startSec;
        int su = target.startUsec;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (Exception e) {
            }
            int still = AmNative.checkIdentity(pid, ss, su);
            Application.invokeLater(() -> {
                if (AmUi.alive == 0) {
                    return;
                }
                if (still == 1) {
                    Dialogs.info(AmUi.window, "信号已发送，进程仍在运行");
                } else {
                    AmUi.selPid = -1;
                }
                AmUi.tableDirty = 1;
                requestSample(1);
            });
        });
        t.start();
    }
}

