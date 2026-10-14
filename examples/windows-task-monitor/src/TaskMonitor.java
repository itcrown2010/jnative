/**
 * JNative Task Monitor — Windows desktop process monitor.
 * Sampling via jn_am (Toolhelp32 / GetProcessTimes / GetProcessMemoryInfo).
 * UI via JNative UiLib + Win32 backend.
 *
 * @author 张晓达
 */
public class TaskMonitor {
    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            String a0 = args[0];
            if (a0.equals("--dump") || a0.equals("--bench") || a0.equals("--self")
                    || a0.equals("--signal-self") || a0.equals("--help")
                    || a0.equals("--find") || a0.equals("--signal") || a0.equals("--check")) {
                AmCore.runCli(args);
                return;
            }
        }
        Application.setAppName("JNative Task Monitor");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(Theme.MODE_SYSTEM);
        TmWindow.show();
        app.run();
    }
}

/**
 * Display formatting helpers (presentation only).
 */
class TmFormat {
    static String na() {
        return "—";
    }

    /** CPU percent with one decimal. milli = percent * 100. -1 waiting, -2 n/a. */
    static String cpuPct1(int milli) {
        if (milli == -1) {
            return "采样中";
        }
        if (milli < 0) {
            return na();
        }
        int tenths = (milli + 5) / 10;
        int whole = tenths / 10;
        int frac = tenths % 10;
        return String.valueOf(whole) + "." + String.valueOf(frac) + "%";
    }

    static String bytes(long n) {
        if (n < 0) {
            return na();
        }
        return AmFormat.bytes(n);
    }
}

/**
 * Column layout that always reports a very large preferred height so a parent
 * HBox hands it the full available height (VBox/HBox layout never grows a
 * child beyond its preferred height on its own).
 */
class TmColumn extends VBox {
    public int preferredHeight() {
        return 20000;
    }
}

/**
 * Page container: tiny preferred height so the parent VBox stretch logic
 * always sizes it to the leftover space (and lets it shrink with the window).
 */
class TmPage extends VBox {
    public int preferredHeight() {
        return 120;
    }
}

class TmUi {
    static Window window;
    static TableView<AmProcRow> table;
    static TextField searchField;
    static Label footPrimary;
    static Label footStatus;
    static Label pageTitle;
    static Label toolStatus;
    static Label procSummary;
    static Button pauseBtn;
    static Button refreshBtn;
    static Button detailBtn;
    static Button termBtn;
    static Button navToggle;
    static ArrayList<Button> navBtns;
    static ArrayList<Panel> navInds;
    static ArrayList<HBox> navRows;
    static VBox navBox;
    static VBox contentMain;
    static VBox pageProc;
    static VBox pagePerf;
    static VBox pageDetail;
    static Label perfCpuLabel;
    static Label perfMemLabel;
    static Label perfMetaLabel;
    static TrendChart cpuChart;
    static TrendChart memChart;
    static Label detailPageBody;
    static BindingScope scope;
    static AmViewState state;
    static ArrayList<AmProcRow> viewRows;
    static ArrayList<int> cpuHistory;
    static ArrayList<int> memHistory;
    static String searchText;
    static int sortCol;
    static int sortAsc;
    static int selPid;
    static long selStartSec;
    static int selStartUsec;
    static int alive;
    static int paused;
    static int refreshMs;
    static int refreshGen;
    static int sampleToken;
    static int samplerBusy;
    static int pendingRefresh;
    static int tableDirty;
    static long lastTableRebuildNs;
    static long lastOkMonoNs;
    static int lastSampleOk;
    static int page; /* 0=proc 1=perf 2=detail */
    static int navCollapsed;
    static int termStyle; /* 0=unset 1=disabled 2=enabled */
    static int detailStyle; /* 0=unset 1=disabled 2=enabled */

    /* Palette (Component.rgba ints). */
    static int appBg;
    static int contentBg;
    static int textC;
    static int mutedC;
    static int sepC;
    static int accentC;
    static int hoverC;
    static int selectedC;
    static int dangerBg;
    static int dangerFg;
    static int disabledFg;
    static int toolBg;

    static Window detailWin;
    static Label detailBody;
    static String detailCopyText;
    static int detailPid;
    static long detailStartSec;
    static int detailStartUsec;

    static void ensure() {
        if (state == null) {
            state = new AmViewState();
        }
        if (viewRows == null) {
            viewRows = new ArrayList<AmProcRow>();
        }
        if (cpuHistory == null) {
            cpuHistory = new ArrayList<int>();
        }
        if (memHistory == null) {
            memHistory = new ArrayList<int>();
        }
        if (searchText == null) {
            searchText = "";
        }
        if (scope == null) {
            scope = new BindingScope();
        }
        if (navBtns == null) {
            navBtns = new ArrayList<Button>();
        }
        if (navInds == null) {
            navInds = new ArrayList<Panel>();
        }
        if (navRows == null) {
            navRows = new ArrayList<HBox>();
        }
        if (sortCol == 0 && sortAsc == 0) {
            sortCol = 1;
            sortAsc = 0;
        }
        if (refreshMs == 0) {
            refreshMs = 1000;
        }
        appBg = Component.rgba(243, 245, 247, 255);
        contentBg = Component.rgba(255, 255, 255, 255);
        textC = Component.rgba(32, 33, 36, 255);
        mutedC = Component.rgba(102, 112, 133, 255);
        sepC = Component.rgba(232, 235, 239, 255);
        accentC = Component.rgba(0, 103, 192, 255);
        hoverC = Component.rgba(242, 246, 250, 255);
        selectedC = Component.rgba(229, 241, 252, 255);
        dangerBg = Component.rgba(252, 232, 230, 255);
        dangerFg = Component.rgba(197, 34, 31, 255);
        disabledFg = Component.rgba(160, 168, 181, 255);
        toolBg = Component.rgba(255, 255, 255, 255);
        alive = 1;
        paused = 0;
        samplerBusy = 0;
        pendingRefresh = 0;
        lastSampleOk = 0;
        lastOkMonoNs = 0;
        page = 0;
        navCollapsed = 0;
        termStyle = 0;
        detailStyle = 0;
        selPid = -1;
        detailPid = -1;
    }

    static Label mkLabel(String t, int w, int h, int pt, int fg, int bg) {
        Label l = new Label(t);
        l.setPrefSize(w, h);
        l.setVisual(bg, fg, 0, pt, 0);
        return l;
    }

    /** Left navigation item. selected: light-blue pill; else transparent/appBg. */
    static void styleNavBtn(Button b, int selected, int collapsed) {
        if (b == null) {
            return;
        }
        if (selected != 0) {
            b.setFilledStyle(selectedC, textC, 6, 13);
        } else {
            b.setFilledStyle(appBg, mutedC, 6, 13);
        }
        if (collapsed != 0) {
            b.setTextAlign(1);
        } else {
            b.setTextAlign(0);
        }
    }

    /** Toolbar push button: white fill, dark text. */
    static void styleToolBtn(Button b) {
        if (b == null) {
            return;
        }
        b.setPrefSize(b.prefWidth, 32);
        b.setFilledStyle(toolBg, textC, 5, 13);
    }

    static void styleToolBtnState(Button b, int enabled) {
        if (b == null) {
            return;
        }
        b.setPrefSize(b.prefWidth, 32);
        if (enabled != 0) {
            b.setFilledStyle(toolBg, textC, 5, 13);
        } else {
            b.setFilledStyle(appBg, disabledFg, 5, 13);
        }
    }

    static void styleDangerBtn(Button b, int enabled) {
        if (b == null) {
            return;
        }
        b.setPrefSize(b.prefWidth, 32);
        if (enabled != 0) {
            b.setFilledStyle(dangerBg, dangerFg, 5, 13);
        } else {
            b.setFilledStyle(appBg, disabledFg, 5, 13);
        }
    }
}

class TmWindow {
    static void show() {
        TmUi.ensure();
        TmUi.window = new Window("JNative Task Monitor", 1180, 760);
        TmUi.window.setMinSize(900, 600);
        TmUi.window.onClose(() -> {
            TmUi.alive = 0;
            TmUi.sampleToken = TmUi.sampleToken + 1;
            TmUi.refreshGen = TmUi.refreshGen + 1;
            if (TmUi.cpuHistory != null) {
                TmUi.cpuHistory.clear();
            }
            if (TmUi.memHistory != null) {
                TmUi.memHistory.clear();
            }
            if (TmUi.scope != null) {
                TmUi.scope.dispose();
                TmUi.scope = null;
            }
        });

        HBox root = new HBox();
        root.spacing = 0;
        root.padding = 0;
        root.setBackgroundRgba(TmUi.appBg);

        /* ---- Left navigation ---- */
        TmColumn nav = new TmColumn();
        nav.spacing = 4;
        nav.padding = 8;
        nav.prefWidth = 184;
        nav.width = 184;
        nav.setBackgroundRgba(TmUi.appBg);
        TmUi.navBox = nav;

        TmUi.navToggle = new Button("«");
        TmUi.navToggle.setPrefSize(168, 32);
        TmUi.navToggle.setFilledStyle(TmUi.appBg, TmUi.mutedC, 6, 13);
        TmUi.navToggle.onClick(() -> { TmActions.toggleNav(); });
        nav.add(TmUi.navToggle);

        addNavItem(nav, 0);
        addNavItem(nav, 1);
        addNavItem(nav, 2);

        /* ---- Main column ---- */
        TmColumn main = new TmColumn();
        main.spacing = 12;
        main.padding = 20;
        main.hgrow = 1;
        main.setBackgroundRgba(TmUi.appBg);
        TmUi.contentMain = main;

        /* Top row: page title | search */
        HBox top = new HBox();
        top.spacing = 12;
        top.padding = 0;
        top.setPrefSize(400, 40);
        top.setBackgroundRgba(TmUi.appBg);
        TmUi.pageTitle = TmUi.mkLabel("进程", 200, 32, 20, TmUi.textC, TmUi.appBg);
        TmUi.pageTitle.hgrow = 1;
        TmUi.searchField = new TextField("");
        TmUi.searchField.setPrefSize(300, 36);
        TmUi.searchField.width = 300;
        TmUi.searchField.height = 36;
        TmUi.searchField.hgrow = 1;
        TmUi.searchField.setBackgroundRgba(Component.rgba(255, 255, 255, 255));
        TmUi.searchField.setForegroundRgba(TmUi.textC);
        TmUi.searchField.setCornerRadius(6);
        TmUi.searchField.setFontSize(13);
        TmUi.searchField.onChange((ev) -> { TmActions.onSearchChanged(); });
        top.add(TmUi.pageTitle);
        top.add(TmUi.searchField);

        /* Toolbar: status | actions */
        HBox toolbar = new HBox();
        toolbar.spacing = 8;
        toolbar.padding = 0;
        toolbar.setPrefSize(400, 40);
        toolbar.setBackgroundRgba(TmUi.appBg);
        TmUi.toolStatus = TmUi.mkLabel("自动刷新 · 1000 ms", 200, 24, 12, TmUi.mutedC, TmUi.appBg);
        TmUi.toolStatus.hgrow = 1;

        TmUi.pauseBtn = new Button("暂停");
        TmUi.pauseBtn.setPrefSize(72, 32);
        TmUi.styleToolBtn(TmUi.pauseBtn);
        TmUi.pauseBtn.onClick(() -> { TmActions.togglePause(); });
        TmUi.refreshBtn = new Button("刷新");
        TmUi.refreshBtn.setPrefSize(72, 32);
        TmUi.styleToolBtn(TmUi.refreshBtn);
        TmUi.refreshBtn.onClick(() -> { TmActions.requestSample(1); });
        TmUi.detailBtn = new Button("打开详情");
        TmUi.detailBtn.setPrefSize(96, 32);
        TmUi.styleToolBtnState(TmUi.detailBtn, 0);
        TmUi.detailBtn.onClick(() -> { TmActions.showDetail(); });
        TmUi.termBtn = new Button("结束进程");
        TmUi.termBtn.setPrefSize(96, 32);
        TmUi.styleDangerBtn(TmUi.termBtn, 0);
        TmUi.termBtn.onClick(() -> { TmActions.confirmTerminate(); });
        TmUi.termBtn.setEnabled(false);
        TmUi.detailBtn.setEnabled(false);
        TmUi.termStyle = 1;
        TmUi.detailStyle = 1;

        toolbar.add(TmUi.toolStatus);
        toolbar.add(TmUi.pauseBtn);
        toolbar.add(TmUi.refreshBtn);
        toolbar.add(TmUi.detailBtn);
        toolbar.add(TmUi.termBtn);

        /* ---- Process page ---- */
        TmPage pageProc = new TmPage();
        pageProc.spacing = 8;
        pageProc.padding = 12;
        pageProc.vgrow = 1;
        pageProc.setBackgroundRgba(TmUi.contentBg);
        TmUi.pageProc = pageProc;
        TmUi.procSummary = TmUi.mkLabel("", 400, 24, 12, TmUi.mutedC, TmUi.contentBg);
        TmUi.table = new TableView<AmProcRow>();
        TmUi.table.vgrow = 1;
        TmUi.table.prefHeight = 200;
        TmUi.table.height = 200;
        TmUi.table.setBackgroundRgba(TmUi.contentBg);
        TmUi.table.setRowIdProvider(new TmRowIds());
        TmUi.table.setIconPathProvider(new TmRowIcons());
        TmUi.table.setRowHeight(34);
        TmUi.table.setEmptyMessage("正在采样…");
        TmActions.rebuildColumns();
        TmUi.table.onSelectionChanged((ev) -> {
            if (ev != null && ev.selectedItem != null) {
                TmActions.onSelectRow(ev.selectedItem);
            } else if (ev != null) {
                TmActions.onSelect(ev.selectedIndex);
            }
        });
        TmUi.table.onDoubleClick((ev) -> { TmActions.showDetail(); });
        TmUi.table.onHeaderClick((ev) -> { TmActions.sort(ev.selectedIndex); });
        pageProc.add(TmUi.procSummary);
        pageProc.add(TmUi.table);

        /* ---- Performance page ---- */
        TmPage pagePerf = new TmPage();
        pagePerf.spacing = Theme.SPACE_3;
        pagePerf.padding = Theme.SPACE_4;
        pagePerf.vgrow = 1;
        pagePerf.setBackgroundRgba(TmUi.contentBg);
        TmUi.pagePerf = pagePerf;
        pagePerf.setVisible(false);
        TmUi.perfCpuLabel = TmUi.mkLabel("CPU —", 400, 28, 14, TmUi.textC, TmUi.contentBg);
        TmUi.cpuChart = new TrendChart();
        TmUi.cpuChart.setCapacity(60);
        TmUi.cpuChart.setYRange(0.0, 100.0);
        TmUi.cpuChart.setPrefSize(400, 120);
        TmUi.perfMemLabel = TmUi.mkLabel("内存 —", 400, 28, 14, TmUi.textC, TmUi.contentBg);
        TmUi.memChart = new TrendChart();
        TmUi.memChart.setCapacity(60);
        TmUi.memChart.setYRange(0.0, 100.0);
        TmUi.memChart.setPrefSize(400, 120);
        TmUi.perfMetaLabel = TmUi.mkLabel(
                "最近约 60 秒 · 间隔 1000 ms · CPU% 按整机逻辑处理器归一化 · 内存为系统已用估计",
                400, 24, 12, TmUi.mutedC, TmUi.contentBg);
        pagePerf.add(TmUi.perfCpuLabel);
        pagePerf.add(TmUi.cpuChart);
        pagePerf.add(TmUi.perfMemLabel);
        pagePerf.add(TmUi.memChart);
        pagePerf.add(TmUi.perfMetaLabel);

        /* ---- Detail page ---- */
        TmPage pageDetail = new TmPage();
        pageDetail.spacing = Theme.SPACE_2;
        pageDetail.padding = Theme.SPACE_4;
        pageDetail.vgrow = 1;
        pageDetail.setBackgroundRgba(TmUi.contentBg);
        TmUi.pageDetail = pageDetail;
        pageDetail.setVisible(false);
        TmUi.detailPageBody = TmUi.mkLabel("在进程页选择一个进程以查看详情。",
                400, 360, 13, TmUi.textC, TmUi.contentBg);
        pageDetail.add(TmUi.detailPageBody);

        /* ---- Footer ---- */
        HBox footer = new HBox();
        footer.spacing = 12;
        footer.padding = 0;
        footer.setPrefSize(400, 32);
        footer.setBackgroundRgba(TmUi.appBg);
        TmUi.footPrimary = TmUi.mkLabel("进程 — · 线程 —", 200, 24, 12, TmUi.mutedC, TmUi.appBg);
        TmUi.footPrimary.hgrow = 1;
        TmUi.footStatus = TmUi.mkLabel("就绪", 320, 24, 12, TmUi.mutedC, TmUi.appBg);
        footer.add(TmUi.footPrimary);
        footer.add(TmUi.footStatus);

        main.add(top);
        main.add(toolbar);
        main.add(pageProc);
        main.add(pagePerf);
        main.add(pageDetail);
        main.add(footer);

        root.add(nav);
        root.add(main);

        TmUi.window.setContent(root);
        TmUi.window.show();
        TmActions.showPage(0);
        TmActions.syncToolbarEnabled();
        TmActions.warmupAndArm();
    }

    static void addNavItem(VBox nav, int idx) {
        HBox row = new HBox();
        row.spacing = 0;
        row.padding = 0;
        row.setPrefSize(168, 36);
        row.setBackgroundRgba(TmUi.appBg);

        Panel ind = new Panel();
        ind.setPrefSize(3, 20);
        ind.setBackgroundRgba(TmUi.appBg);

        Button b = new Button(TmActions.navLabel(idx, 0));
        b.setPrefSize(165, 36);
        TmUi.styleNavBtn(b, 0, 0);
        b.onClick(() -> { TmActions.showPage(idx); });

        row.add(ind);
        row.add(b);
        nav.add(row);

        TmUi.navRows.add(row);
        TmUi.navInds.add(ind);
        TmUi.navBtns.add(b);
    }
}

class TmRowIds implements TableRowIdProvider<AmProcRow> {
    public String rowId(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "";
        }
        return row.sample.identityKey();
    }
}

class TmRowIcons implements TableIconPathProvider<AmProcRow> {
    public String iconPath(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "";
        }
        if ((row.sample.flags & AmFlags.PATH) != 0 && row.sample.path != null) {
            return row.sample.path;
        }
        return "";
    }
}

class TmColName implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(0, "名称");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null || row.sample.name == null
                || row.sample.name.length() == 0) {
            return TmFormat.na();
        }
        return row.sample.name;
    }
}

class TmColCpu implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(1, "CPU");
    }
    String renderRow(AmProcRow row) {
        if (row == null) {
            return TmFormat.na();
        }
        return TmFormat.cpuPct1(row.cpuMilli);
    }
}

class TmColMem implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(2, "工作集");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return TmFormat.na();
        }
        if ((row.sample.flags & AmFlags.FOOTPRINT) != 0) {
            return TmFormat.bytes(row.sample.footprint);
        }
        if ((row.sample.flags & AmFlags.RSS) != 0) {
            return TmFormat.bytes(row.sample.rss);
        }
        return TmFormat.na();
    }
}

class TmColPid implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(3, "PID");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return TmFormat.na();
        }
        return String.valueOf(row.sample.pid);
    }
}

class TmColUser implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(4, "用户");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null || (row.sample.flags & AmFlags.USER) == 0
                || row.sample.user == null || row.sample.user.length() == 0) {
            return TmFormat.na();
        }
        return row.sample.user;
    }
}

class TmColThr implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(5, "线程");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null || (row.sample.flags & AmFlags.NTHREADS) == 0
                || row.sample.nthreads < 0) {
            return TmFormat.na();
        }
        return String.valueOf(row.sample.nthreads);
    }
}

class TmActions {
    static String colTitle(int col, String base) {
        if (TmUi.sortCol != col) {
            return base;
        }
        if (TmUi.sortAsc != 0) {
            return base + " ↑";
        }
        return base + " ↓";
    }

    static void rebuildColumns() {
        if (TmUi.table == null) {
            return;
        }
        TmUi.table.columns.clear();
        /* Order: Name | CPU | Memory | PID | User | Threads */
        /* Prefer: name/cpu/mem/pid stay visible; user/threads may scroll when narrow. */
        TmUi.table.addColumnDef(colTitle(0, "名称"), new TmColName(), 240, "L", 1, 360);
        TmUi.table.addColumnDef(colTitle(1, "CPU"), new TmColCpu(), 90, "R", 0, 0);
        TmUi.table.addColumnDef(colTitle(2, "工作集"), new TmColMem(), 120, "R", 0, 0);
        TmUi.table.addColumnDef(colTitle(3, "PID"), new TmColPid(), 80, "R", 0, 0);
        TmUi.table.addColumnDef(colTitle(4, "用户"), new TmColUser(), 120, "L", 1, 180);
        TmUi.table.addColumnDef(colTitle(5, "线程"), new TmColThr(), 70, "R", 0, 0);
    }

    static String navLabel(int idx, int collapsed) {
        if (collapsed != 0) {
            if (idx == 0) {
                return "进";
            }
            if (idx == 1) {
                return "性";
            }
            return "详";
        }
        if (idx == 0) {
            return "  进程";
        }
        if (idx == 1) {
            return "  性能";
        }
        return "  详情";
    }

    static String pageName(int p) {
        if (p == 0) {
            return "进程";
        }
        if (p == 1) {
            return "性能";
        }
        return "详情";
    }

    static void toggleNav() {
        if (TmUi.navCollapsed != 0) {
            TmUi.navCollapsed = 0;
        } else {
            TmUi.navCollapsed = 1;
        }
        syncNav();
    }

    /** Restyle nav buttons / indicators / widths for current page + collapsed state. */
    static void syncNav() {
        if (TmUi.navBox == null || TmUi.navBtns == null) {
            return;
        }
        int collapsed = TmUi.navCollapsed;
        int navW = 184;
        if (collapsed != 0) {
            navW = 56;
        }
        int innerW = navW - 16;
        int btnW = innerW - 3;
        TmUi.navBox.prefWidth = navW;
        TmUi.navBox.width = navW;
        if (TmUi.navToggle != null) {
            TmUi.navToggle.setPrefSize(innerW, 32);
            if (collapsed != 0) {
                TmUi.navToggle.setText("»");
            } else {
                TmUi.navToggle.setText("«");
            }
        }
        int i = 0;
        while (i < TmUi.navBtns.size()) {
            Button b = TmUi.navBtns.get(i);
            Panel ind = TmUi.navInds.get(i);
            HBox row = TmUi.navRows.get(i);
            int sel = 0;
            if (i == TmUi.page) {
                sel = 1;
            }
            row.setPrefSize(innerW, 36);
            b.setPrefSize(btnW, 36);
            b.setText(navLabel(i, collapsed));
            TmUi.styleNavBtn(b, sel, collapsed);
            if (sel != 0) {
                ind.setBackgroundRgba(TmUi.accentC);
            } else {
                ind.setBackgroundRgba(TmUi.appBg);
            }
            row.layout();
            i = i + 1;
        }
        TmUi.navBox.layout();
        if (TmUi.window != null && TmUi.window.content != null) {
            TmUi.window.content.layout();
        }
    }

    static void showPage(int p) {
        if (p < 0 || p > 2) {
            return;
        }
        TmUi.page = p;
        if (TmUi.pageProc != null) {
            TmUi.pageProc.setVisible(p == 0);
        }
        if (TmUi.pagePerf != null) {
            TmUi.pagePerf.setVisible(p == 1);
        }
        if (TmUi.pageDetail != null) {
            TmUi.pageDetail.setVisible(p == 2);
        }
        if (TmUi.searchField != null) {
            TmUi.searchField.setVisible(p == 0);
        }
        if (TmUi.pageTitle != null) {
            TmUi.pageTitle.setText(pageName(p));
        }
        if (p == 1) {
            updatePerfPage();
        }
        if (p == 2) {
            refreshDetailPage();
        }
        syncNav();
        if (TmUi.window != null && TmUi.window.content != null) {
            TmUi.window.content.layout();
        }
    }

    static void togglePause() {
        if (TmUi.paused != 0) {
            TmUi.paused = 0;
            if (TmUi.pauseBtn != null) {
                TmUi.pauseBtn.setText("暂停");
                TmUi.pauseBtn.setPrefSize(72, 32);
            }
            requestSample(0);
        } else {
            TmUi.paused = 1;
            if (TmUi.pauseBtn != null) {
                TmUi.pauseBtn.setText("恢复");
                TmUi.pauseBtn.setPrefSize(72, 32);
            }
        }
        updateStatusBar();
    }

    static void onSearchChanged() {
        String q = "";
        if (TmUi.searchField != null) {
            q = TmUi.searchField.getText();
        }
        if (q == null) {
            q = "";
        }
        TmUi.searchText = q;
        TmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static void onSelectRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            TmUi.selPid = -1;
            syncToolbarEnabled();
            refreshDetailPage();
            return;
        }
        TmUi.selPid = r.sample.pid;
        TmUi.selStartSec = r.sample.startSec;
        TmUi.selStartUsec = r.sample.startUsec;
        syncToolbarEnabled();
        refreshDetailIfOpen();
        refreshDetailPage();
    }

    static void onSelect(int index) {
        if (TmUi.viewRows == null || index < 0 || index >= TmUi.viewRows.size()) {
            TmUi.selPid = -1;
            syncToolbarEnabled();
            refreshDetailPage();
            return;
        }
        onSelectRow(TmUi.viewRows.get(index));
    }

    static void syncToolbarEnabled() {
        AmProcRow r = selectedRow();
        int ok = 0;
        if (r != null && r.sample != null) {
            ok = 1;
        }
        if (TmUi.detailBtn != null) {
            TmUi.detailBtn.setEnabled(ok != 0);
            int want = 1;
            if (ok != 0) {
                want = 2;
            }
            if (TmUi.detailStyle != want) {
                TmUi.detailStyle = want;
                TmUi.styleToolBtnState(TmUi.detailBtn, ok);
            }
        }
        int canTerm = 0;
        if (ok != 0 && AmProtect.isProtected(r.sample) == 0) {
            canTerm = 1;
        }
        if (TmUi.termBtn != null) {
            TmUi.termBtn.setEnabled(canTerm != 0);
            int wantT = 1;
            if (canTerm != 0) {
                wantT = 2;
            }
            if (TmUi.termStyle != wantT) {
                TmUi.termStyle = wantT;
                TmUi.styleDangerBtn(TmUi.termBtn, canTerm);
            }
        }
    }

    static void sort(int col) {
        if (col < 0 || col > 5) {
            return;
        }
        if (TmUi.sortCol == col) {
            TmUi.sortAsc = TmUi.sortAsc != 0 ? 0 : 1;
        } else {
            TmUi.sortCol = col;
            TmUi.sortAsc = 0;
        }
        rebuildColumns();
        TmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static AmProcRow selectedRow() {
        if (TmUi.selPid <= 0 || TmUi.state == null) {
            return null;
        }
        return TmUi.state.findByIdentity(TmUi.selPid, TmUi.selStartSec, TmUi.selStartUsec);
    }

    static void warmupAndArm() {
        TmUi.samplerBusy = 1;
        int token = TmUi.sampleToken;
        Thread t = new Thread(() -> {
            AmSnapshot a = AmSampler.take();
            try {
                Thread.sleep(250);
            } catch (Exception e) {
            }
            AmSnapshot b = AmSampler.take();
            Application.invokeLater(() -> {
                TmUi.samplerBusy = 0;
                if (TmUi.alive == 0 || token != TmUi.sampleToken) {
                    return;
                }
                int ok = 0;
                if (a != null) {
                    applyOneSnapshot(a);
                    ok = 1;
                }
                if (b != null) {
                    applyOneSnapshot(b);
                    ok = 1;
                }
                if (ok != 0) {
                    TmUi.lastSampleOk = 1;
                    TmUi.lastOkMonoNs = AmNative.monoNs();
                } else {
                    TmUi.lastSampleOk = 0;
                    if (TmUi.state != null) {
                        TmUi.state.lastError = "采样失败";
                    }
                }
                finishSampleUi(1);
                armRefresh();
                drainPending();
            });
        });
        t.start();
    }

    static void drainPending() {
        if (TmUi.alive == 0) {
            return;
        }
        if (TmUi.pendingRefresh != 0 && TmUi.samplerBusy == 0) {
            TmUi.pendingRefresh = 0;
            requestSample(0);
        }
    }

    static void applyOneSnapshot(AmSnapshot snap) {
        if (snap == null) {
            return;
        }
        TmUi.state.applySnapshot(snap);
        if (TmUi.paused != 0) {
            return;
        }
        if (TmUi.state.sysCpuMilli >= 0) {
            TmUi.cpuHistory.add(TmUi.state.sysCpuMilli);
            while (TmUi.cpuHistory.size() > 60) {
                TmUi.cpuHistory.removeAt(0);
            }
        }
        if (TmUi.state.sysMemUsedBytes >= 0 && TmUi.state.memTotal > 0) {
            int memMilli = (int)((TmUi.state.sysMemUsedBytes * 10000L) / TmUi.state.memTotal);
            TmUi.memHistory.add(memMilli);
            while (TmUi.memHistory.size() > 60) {
                TmUi.memHistory.removeAt(0);
            }
        }
    }

    static void finishSampleUi(int forceTable) {
        updateFooter();
        updateStatusBar();
        updatePerfPage();
        if (TmUi.state != null && TmUi.state.sampleCount >= 2) {
            if (forceTable != 0 || TmUi.tableDirty != 0 || TmUi.lastTableRebuildNs == 0) {
                maybeRebuildTable(1);
            }
        }
        syncSelectionFromNative();
        syncToolbarEnabled();
        refreshDetailIfOpen();
        if (TmUi.page == 2) {
            refreshDetailPage();
        }
    }

    /**
     * Win32 ListView selection sometimes paints without delivering SELECT to
     * Java; poll the native selected index so toolbar enablement stays correct.
     */
    static void syncSelectionFromNative() {
        if (TmUi.table == null || TmUi.table.nativeId <= 0 || TmUi.viewRows == null) {
            return;
        }
        int idx = UiNative.getSelectedIndex(TmUi.table.nativeId);
        if (idx < 0 || idx >= TmUi.viewRows.size()) {
            return;
        }
        AmProcRow r = TmUi.viewRows.get(idx);
        if (r == null || r.sample == null) {
            return;
        }
        if (r.sample.pid != TmUi.selPid
                || r.sample.startSec != TmUi.selStartSec
                || r.sample.startUsec != TmUi.selStartUsec) {
            onSelectRow(r);
        }
    }

    static void requestSample(int force) {
        if (TmUi.alive == 0) {
            return;
        }
        if (TmUi.samplerBusy != 0) {
            if (force != 0) {
                TmUi.pendingRefresh = 1;
            } else {
                TmUi.pendingRefresh = 1;
            }
            return;
        }
        if (AmSampler.inFlight != 0 && force == 0) {
            TmUi.pendingRefresh = 1;
            return;
        }
        int token = TmUi.sampleToken;
        TmUi.samplerBusy = 1;
        Thread t = new Thread(() -> {
            AmSnapshot snap = AmSampler.take();
            Application.invokeLater(() -> {
                TmUi.samplerBusy = 0;
                if (TmUi.alive == 0 || token != TmUi.sampleToken) {
                    return;
                }
                if (snap != null) {
                    applyOneSnapshot(snap);
                    TmUi.lastSampleOk = 1;
                    TmUi.lastOkMonoNs = AmNative.monoNs();
                    if (TmUi.state != null) {
                        TmUi.state.lastError = "";
                    }
                } else {
                    TmUi.lastSampleOk = 0;
                    if (TmUi.state != null) {
                        TmUi.state.lastError = "采样失败";
                    }
                }
                /* Always rebuild the process table so CPU/mem keep updating.
                 * TableView.preserveScroll + rowId selection keep UX stable. */
                finishSampleUi(1);
                drainPending();
            });
        });
        t.start();
    }

    static void armRefresh() {
        if (TmUi.alive == 0) {
            return;
        }
        TmUi.refreshGen = TmUi.refreshGen + 1;
        int gen = TmUi.refreshGen;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(TmUi.refreshMs);
            } catch (Exception e) {
            }
            Application.invokeLater(() -> {
                if (TmUi.alive == 0 || gen != TmUi.refreshGen) {
                    return;
                }
                if (TmUi.paused == 0) {
                    requestSample(0);
                }
                armRefresh();
            });
        });
        t.start();
    }

    static void updateFooter() {
        if (TmUi.state == null) {
            return;
        }
        int nproc = 0;
        int nthr = 0;
        if (TmUi.state.rows != null) {
            nproc = TmUi.state.rows.size();
            int i = 0;
            while (i < TmUi.state.rows.size()) {
                AmProcRow r = TmUi.state.rows.get(i);
                if (r != null && r.sample != null && (r.sample.flags & AmFlags.NTHREADS) != 0) {
                    nthr = nthr + r.sample.nthreads;
                }
                i = i + 1;
            }
        }
        String cpuPart = "";
        if (TmUi.state.sysCpuMilli >= 0) {
            cpuPart = TmFormat.cpuPct1(TmUi.state.sysCpuMilli);
        }
        if (TmUi.footPrimary != null) {
            String ft = "进程 " + String.valueOf(nproc) + " · 线程 " + String.valueOf(nthr);
            if (cpuPart.length() > 0) {
                ft = ft + " · CPU " + cpuPart;
            }
            TmUi.footPrimary.setText(ft);
        }
        if (TmUi.procSummary != null) {
            String sum = "";
            if (cpuPart.length() > 0) {
                sum = "CPU " + cpuPart;
            }
            if (TmUi.state.sysMemUsedBytes >= 0) {
                if (sum.length() > 0) {
                    sum = sum + " · ";
                }
                sum = sum + "内存 " + AmFormat.bytes(TmUi.state.sysMemUsedBytes);
                if (TmUi.state.memTotal > 0) {
                    sum = sum + " / " + AmFormat.bytes(TmUi.state.memTotal);
                }
            }
            if (sum.length() == 0 && TmUi.state.sampleCount < 2) {
                sum = "正在采样…";
            }
            TmUi.procSummary.setText(sum);
        }
    }

    static void updateStatusBar() {
        String ts = "";
        if (TmUi.paused != 0) {
            ts = "已暂停";
        } else if (TmUi.samplerBusy != 0) {
            ts = "采样中…";
        } else {
            ts = "自动刷新 · " + String.valueOf(TmUi.refreshMs) + " ms";
        }
        if (TmUi.toolStatus != null) {
            TmUi.toolStatus.setText(ts);
        }
        if (TmUi.footStatus == null) {
            return;
        }
        String st = "";
        if (TmUi.paused != 0) {
            st = "已暂停";
        } else if (TmUi.samplerBusy != 0) {
            st = "采样中…";
        } else {
            st = "刷新 " + String.valueOf(TmUi.refreshMs) + " ms";
        }
        if (TmUi.lastSampleOk != 0 && TmUi.lastOkMonoNs > 0) {
            long ageMs = (AmNative.monoNs() - TmUi.lastOkMonoNs) / 1000000L;
            if (ageMs < 0) {
                ageMs = 0;
            }
            st = st + " · 上次成功 " + AmParse.longToString(ageMs) + " ms 前";
        } else if (TmUi.state != null && TmUi.state.lastError != null
                && TmUi.state.lastError.length() > 0) {
            st = st + " · 数据已过期（" + TmUi.state.lastError + "）";
        }
        TmUi.footStatus.setText(st);
    }

    static void updatePerfPage() {
        if (TmUi.perfCpuLabel == null) {
            return;
        }
        String cpu = TmFormat.na();
        if (TmUi.state != null && TmUi.state.sysCpuMilli >= 0) {
            cpu = TmFormat.cpuPct1(TmUi.state.sysCpuMilli);
        } else if (TmUi.state != null && TmUi.state.sampleCount < 2) {
            cpu = "采样中";
        }
        int ncpu = 0;
        if (TmUi.state != null) {
            ncpu = TmUi.state.ncpu;
        }
        TmUi.perfCpuLabel.setText("CPU 利用率 " + cpu
                + (ncpu > 0 ? (" · 逻辑处理器 " + String.valueOf(ncpu)) : ""));
        if (TmUi.perfMemLabel != null && TmUi.state != null) {
            String mu = TmFormat.na();
            String mt = TmFormat.na();
            if (TmUi.state.sysMemUsedBytes >= 0) {
                mu = AmFormat.bytes(TmUi.state.sysMemUsedBytes);
            }
            if (TmUi.state.memTotal > 0) {
                mt = AmFormat.bytes(TmUi.state.memTotal);
            }
            TmUi.perfMemLabel.setText("内存已用 " + mu + " / 总量 " + mt + "（系统页计数估计）");
        }
        if (TmUi.cpuChart != null && TmUi.cpuHistory != null) {
            TmUi.cpuChart.setSamplesFromMilli(TmUi.cpuHistory, 0.01);
        }
        if (TmUi.memChart != null && TmUi.memHistory != null) {
            TmUi.memChart.setSamplesFromMilli(TmUi.memHistory, 0.01);
        }
        if (TmUi.perfMetaLabel != null) {
            String pauseNote = "";
            if (TmUi.paused != 0) {
                pauseNote = " · 已暂停（曲线不再推进）";
            }
            TmUi.perfMetaLabel.setText("最近约 "
                    + String.valueOf(TmUi.cpuHistory != null ? TmUi.cpuHistory.size() : 0)
                    + " 秒 · 间隔 " + String.valueOf(TmUi.refreshMs)
                    + " ms · CPU% 按整机逻辑处理器归一化 · 内存为系统已用估计"
                    + pauseNote);
        }
    }

    static void maybeRebuildTable(int force) {
        if (TmUi.table == null || TmUi.state == null) {
            return;
        }
        if (force == 0 && TmUi.tableDirty == 0) {
            return;
        }
        ArrayList<AmProcRow> filtered = TmUi.state.filtered(TmUi.searchText);
        ArrayList<AmProcRow> sorted = sortRows(filtered, TmUi.sortCol, TmUi.sortAsc);
        ArrayList<AmProcRow> shown = new ArrayList<AmProcRow>();
        int maxShow = 800;
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
        TmUi.viewRows = shown;
        /* Seed row-id so setItems restores selection without EnsureVisible jump. */
        if (TmUi.selPid > 0) {
            TmUi.table.selectedRowId = String.valueOf(TmUi.selPid) + ":"
                    + AmParse.longToString(TmUi.selStartSec) + ":"
                    + String.valueOf(TmUi.selStartUsec);
        }
        int keepTop = -1;
        if (TmUi.table.nativeId > 0) {
            keepTop = UiNative.tableGetScrollY(TmUi.table.nativeId);
        }
        TmUi.table.setItems(TmUi.viewRows);
        if (keepTop >= 0 && TmUi.table.nativeId > 0) {
            UiNative.tableSetScrollY(TmUi.table.nativeId, keepTop);
        }
        if (TmUi.selPid > 0) {
            String want = String.valueOf(TmUi.selPid) + ":"
                    + AmParse.longToString(TmUi.selStartSec) + ":"
                    + String.valueOf(TmUi.selStartUsec);
            if (TmUi.table.getSelectedRowId() == null
                    || !want.equals(TmUi.table.getSelectedRowId())) {
                TmUi.selPid = -1;
                TmUi.table.selectedRowId = null;
            }
        }
        TmUi.tableDirty = 0;
        TmUi.lastTableRebuildNs = AmNative.monoNs();
        syncToolbarEnabled();
    }

    static ArrayList<AmProcRow> sortRows(ArrayList<AmProcRow> src, int col, int asc) {
        ArrayList<AmProcRow> out = new ArrayList<AmProcRow>();
        int i = 0;
        while (i < src.size()) {
            out.add(src.get(i));
            i = i + 1;
        }
        int a = 0;
        while (a < out.size()) {
            int b = a + 1;
            while (b < out.size()) {
                if (cmp(out.get(a), out.get(b), col, asc) > 0) {
                    AmProcRow tmp = out.get(a);
                    out.set(a, out.get(b));
                    out.set(b, tmp);
                }
                b = b + 1;
            }
            a = a + 1;
        }
        return out;
    }

    /* Columns: 0 Name | 1 CPU | 2 Memory | 3 PID | 4 User | 5 Threads */
    static int cmp(AmProcRow x, AmProcRow y, int col, int asc) {
        if (x == null || y == null || x.sample == null || y.sample == null) {
            return 0;
        }
        int c = 0;
        if (col == 0) {
            String nx = x.sample.name != null ? x.sample.name : "";
            String ny = y.sample.name != null ? y.sample.name : "";
            c = nx.compareTo(ny);
        } else if (col == 1) {
            c = x.cpuMilli - y.cpuMilli;
        } else if (col == 2) {
            long mx = -1;
            long my = -1;
            if ((x.sample.flags & AmFlags.FOOTPRINT) != 0) {
                mx = x.sample.footprint;
            } else if ((x.sample.flags & AmFlags.RSS) != 0) {
                mx = x.sample.rss;
            }
            if ((y.sample.flags & AmFlags.FOOTPRINT) != 0) {
                my = y.sample.footprint;
            } else if ((y.sample.flags & AmFlags.RSS) != 0) {
                my = y.sample.rss;
            }
            if (mx < my) {
                c = -1;
            } else if (mx > my) {
                c = 1;
            }
        } else if (col == 3) {
            c = x.sample.pid - y.sample.pid;
        } else if (col == 4) {
            String ux = x.sample.user != null ? x.sample.user : "";
            String uy = y.sample.user != null ? y.sample.user : "";
            c = ux.compareTo(uy);
        } else if (col == 5) {
            c = x.sample.nthreads - y.sample.nthreads;
        }
        if (asc == 0) {
            c = 0 - c;
        }
        return c;
    }

    static String detailText(AmProcRow r) {
        if (r == null || r.sample == null) {
            return "在进程页选择一个进程以查看详情。";
        }
        String path = r.sample.path;
        if (path == null || path.length() == 0 || (r.sample.flags & AmFlags.PATH) == 0) {
            path = TmFormat.na();
        }
        String mem = TmFormat.na();
        if ((r.sample.flags & AmFlags.FOOTPRINT) != 0) {
            mem = TmFormat.bytes(r.sample.footprint) + "（工作集）";
        } else if ((r.sample.flags & AmFlags.RSS) != 0) {
            mem = TmFormat.bytes(r.sample.rss) + "（工作集）";
        }
        String user = TmFormat.na();
        if ((r.sample.flags & AmFlags.USER) != 0 && r.sample.user != null
                && r.sample.user.length() > 0) {
            user = r.sample.user;
        }
        String thr = TmFormat.na();
        if ((r.sample.flags & AmFlags.NTHREADS) != 0 && r.sample.nthreads >= 0) {
            thr = String.valueOf(r.sample.nthreads);
        }
        String prot = "";
        if (AmProtect.isProtected(r.sample) != 0) {
            prot = "\n受保护：" + AmProtect.reason(r.sample);
        }
        return "名称\t" + r.sample.name + "\n"
                + "PID\t" + String.valueOf(r.sample.pid) + "\n"
                + "PPID\t" + String.valueOf(r.sample.ppid) + "\n"
                + "用户\t" + user + "\n"
                + "CPU\t" + TmFormat.cpuPct1(r.cpuMilli) + "\n"
                + "内存\t" + mem + "\n"
                + "线程\t" + thr + "\n"
                + "路径\t" + path + "\n"
                + "启动\t" + AmParse.longToString(r.sample.startSec) + "."
                + String.valueOf(r.sample.startUsec)
                + prot;
    }

    static void refreshDetailPage() {
        if (TmUi.detailPageBody == null) {
            return;
        }
        AmProcRow r = selectedRow();
        if (r == null) {
            TmUi.detailPageBody.setText("在进程页选择一个进程以查看详情。");
            return;
        }
        TmUi.detailPageBody.setText(detailText(r));
    }

    static void showDetail() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        showPage(2);
        if (TmUi.detailWin == null) {
            TmUi.detailWin = new Window("进程详情", 480, 420);
            TmUi.detailWin.setOwnerWindow(TmUi.window);
            TmUi.detailWin.onClose(() -> {
                TmUi.detailWin = null;
                TmUi.detailBody = null;
                TmUi.detailPid = -1;
            });
            VBox box = new VBox();
            box.spacing = 8;
            box.padding = 16;
            box.setBackgroundRgba(TmUi.contentBg);
            TmUi.detailBody = TmUi.mkLabel("", 440, 300, 13, TmUi.textC, TmUi.contentBg);
            Button copy = new Button("复制");
            copy.setPrefSize(72, 32);
            TmUi.styleToolBtn(copy);
            copy.onClick(() -> {
                String t = TmUi.detailCopyText;
                if (t == null) {
                    t = "";
                }
                Clipboard.setText(t);
                Dialogs.info(TmUi.detailWin, "已复制到剪贴板");
            });
            box.add(TmUi.detailBody);
            box.add(copy);
            TmUi.detailWin.setContent(box);
            TmUi.detailWin.show();
        } else {
            TmUi.detailWin.activate();
        }
        fillDetail(r);
    }

    static void fillDetail(AmProcRow r) {
        if (r == null || r.sample == null || TmUi.detailBody == null) {
            return;
        }
        TmUi.detailPid = r.sample.pid;
        TmUi.detailStartSec = r.sample.startSec;
        TmUi.detailStartUsec = r.sample.startUsec;
        String text = detailText(r);
        TmUi.detailBody.setText(text);
        TmUi.detailCopyText = text;
    }

    static void refreshDetailIfOpen() {
        if (TmUi.detailWin == null || TmUi.detailBody == null || TmUi.detailPid <= 0) {
            return;
        }
        AmProcRow r = TmUi.state.findByIdentity(TmUi.detailPid, TmUi.detailStartSec, TmUi.detailStartUsec);
        if (r == null) {
            TmUi.detailBody.setText("所选进程已退出或身份失效");
            return;
        }
        fillDetail(r);
    }

    static void confirmTerminate() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        if (AmProtect.isProtected(r.sample) != 0) {
            Dialogs.error(TmUi.window, "无法结束：" + AmProtect.reason(r.sample));
            return;
        }
        String msg = "是否结束进程 " + r.sample.name + "（PID "
                + String.valueOf(r.sample.pid) + "）？\n将校验启动时间以防 PID 复用。";
        if (!Dialogs.confirm(TmUi.window, msg)) {
            return;
        }
        AmProcSample target = r.sample;
        int idOk = AmNative.checkIdentity(target.pid, target.startSec, target.startUsec);
        if (idOk == 0) {
            Dialogs.error(TmUi.window, "目标已退出或 PID 已被复用，未执行结束。");
            TmUi.tableDirty = 1;
            requestSample(1);
            return;
        }
        if (idOk < 0) {
            Dialogs.error(TmUi.window, "无法校验进程身份（权限不足或 API 失败）。");
            return;
        }
        int code = AmKill.terminate(target, AmKill.SIGKILL);
        if (code != 0) {
            Dialogs.error(TmUi.window, AmKill.describeError(code));
            return;
        }
        TmUi.tableDirty = 1;
        requestSample(1);
    }
}
