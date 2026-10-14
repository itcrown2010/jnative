/**
 * JNative Task Monitor — Windows 11 Task Manager–aligned process page (light theme).
 * Sampling via jn_am; UI via UiLib + Win32. Remaining pages: nav shells only.
 * Visual E2E: WINDOWS_E2E_UNVERIFIED until Parallel overlay QA.
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
        Theme.setAppearanceMode(Theme.MODE_LIGHT);
        TmWindow.show();
        app.run();
    }
}

class TmFormat {
    static String na() {
        return "—";
    }

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

    static String statusOf(AmProcSample s) {
        if (s == null) {
            return "";
        }
        String out = "";
        if ((s.statusBits & AmStatusBits.NOT_RESPONDING) != 0) {
            out = "无响应";
        }
        if ((s.statusBits & AmStatusBits.EFFICIENCY) != 0 || s.eco == 1) {
            if (out.length() > 0) {
                out = out + " · ";
            }
            out = out + "效率模式";
        }
        return out;
    }
}

class TmColumn extends VBox {
    public int preferredHeight() {
        return 20000;
    }
}

class TmPage extends VBox {
    public int preferredHeight() {
        return 120;
    }
}

class TmUi {
    static Window window;
    static TableView<TmViewRow> table;
    static SearchField searchField;
    static Label footPrimary;
    static Label footStatus;
    static Label pageHeading;
    static Label dualCpu;
    static Label dualMem;
    static Label dualDisk;
    static Label dualNet;
    static Label dualPad;
    static Button pauseBtn;
    static Button refreshBtn;
    static Button runTaskBtn;
    static Button endTaskBtn;
    static Button ecoBtn;
    static Button moreBtn;
    static ArrayList<Button> navBtns;
    static ArrayList<Panel> navInds;
    static ArrayList<HBox> navRows;
    static VBox navBox;
    static VBox navTop;
    static VBox contentPanel;
    static VBox pageProc;
    static VBox pagePerf;
    static VBox pageStub;
    static Label stubBody;
    static Label perfCpuLabel;
    static Label perfMemLabel;
    static Label perfMetaLabel;
    static Label perfGap;
    static TrendChart cpuChart;
    static TrendChart memChart;
    static BindingScope scope;
    static AmViewState state;
    static ArrayList<TmViewRow> viewRows;
    static HashMap<String, Integer> expanded;
    static ArrayList<int> cpuHistory;
    static ArrayList<int> memHistory;
    static String searchText;
    static String selRowId;
    static int sortCol;
    static int sortAsc;
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
    static int page;
    static int termStyle;
    static int ecoStyle;

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
    static int sectionBg;

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
            viewRows = new ArrayList<TmViewRow>();
        }
        if (expanded == null) {
            expanded = new HashMap<String, Integer>();
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
        if (selRowId == null) {
            selRowId = "";
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
            sortCol = 2;
            sortAsc = 0;
        }
        if (refreshMs == 0) {
            refreshMs = 1000;
        }
        appBg = Component.rgba(243, 243, 243, 255);
        contentBg = Component.rgba(255, 255, 255, 255);
        textC = Component.rgba(32, 33, 36, 255);
        mutedC = Component.rgba(96, 101, 108, 255);
        sepC = Component.rgba(232, 232, 232, 255);
        accentC = Component.rgba(0, 103, 192, 255);
        hoverC = Component.rgba(242, 246, 250, 255);
        selectedC = Component.rgba(229, 241, 252, 255);
        dangerBg = Component.rgba(252, 232, 230, 255);
        dangerFg = Component.rgba(197, 34, 31, 255);
        disabledFg = Component.rgba(160, 168, 181, 255);
        toolBg = Component.rgba(255, 255, 255, 255);
        sectionBg = Component.rgba(248, 248, 248, 255);
        alive = 1;
        paused = 0;
        samplerBusy = 0;
        pendingRefresh = 0;
        lastSampleOk = 0;
        lastOkMonoNs = 0;
        page = TmTokens.PAGE_PROC;
        termStyle = 0;
        ecoStyle = 0;
        detailPid = -1;
    }

    static Label mkLabel(String t, int w, int h, int pt, int fg, int bg) {
        Label l = new Label(t);
        l.setPrefSize(w, h);
        l.setVisual(bg, fg, 0, pt, 0);
        return l;
    }

    static void styleNavBtn(Button b, int selected) {
        if (b == null) {
            return;
        }
        if (selected != 0) {
            b.setFilledStyle(selectedC, textC, 6, TmTokens.BODY_PT);
        } else {
            b.setFilledStyle(appBg, mutedC, 6, TmTokens.BODY_PT);
        }
        b.setTextAlign(0);
    }

    static void styleToolBtn(Button b) {
        if (b == null) {
            return;
        }
        b.setPrefSize(b.prefWidth, TmTokens.CMD_BTN_H);
        b.setFilledStyle(toolBg, textC, 4, TmTokens.BODY_PT);
    }

    static void styleToolBtnState(Button b, int enabled) {
        if (b == null) {
            return;
        }
        b.setPrefSize(b.prefWidth, TmTokens.CMD_BTN_H);
        if (enabled != 0) {
            b.setFilledStyle(toolBg, textC, 4, TmTokens.BODY_PT);
        } else {
            b.setFilledStyle(appBg, disabledFg, 4, TmTokens.BODY_PT);
        }
    }

    static void styleDangerBtn(Button b, int enabled) {
        if (b == null) {
            return;
        }
        b.setPrefSize(b.prefWidth, TmTokens.CMD_BTN_H);
        if (enabled != 0) {
            b.setFilledStyle(dangerBg, dangerFg, 4, TmTokens.BODY_PT);
        } else {
            b.setFilledStyle(appBg, disabledFg, 4, TmTokens.BODY_PT);
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

        TmChrome.buildNav(root);

        TmColumn main = new TmColumn();
        main.spacing = 0;
        main.padding = 0;
        main.hgrow = 1;
        main.setBackgroundRgba(TmUi.appBg);

        /* Top strip: centered search */
        HBox top = new HBox();
        top.spacing = 0;
        top.padding = 8;
        top.setPrefSize(400, TmTokens.TITLE_H);
        top.setBackgroundRgba(TmUi.appBg);
        Label padL = TmUi.mkLabel("", 40, TmTokens.SEARCH_H, 12, TmUi.mutedC, TmUi.appBg);
        padL.hgrow = 1;
        TmUi.searchField = new SearchField();
        TmUi.searchField.setPrefSize(TmTokens.SEARCH_W, TmTokens.SEARCH_H);
        TmUi.searchField.width = TmTokens.SEARCH_W;
        TmUi.searchField.height = TmTokens.SEARCH_H;
        TmUi.searchField.setPlaceholder("搜索");
        TmUi.searchField.setOnQueryChanged(() -> { TmActions.onSearchChanged(); });
        Label padR = TmUi.mkLabel("", 40, TmTokens.SEARCH_H, 12, TmUi.mutedC, TmUi.appBg);
        padR.hgrow = 1;
        top.add(padL);
        top.add(TmUi.searchField);
        top.add(padR);

        /* White content panel */
        TmUi.contentPanel = new VBox();
        TmUi.contentPanel.spacing = 0;
        TmUi.contentPanel.padding = 12;
        TmUi.contentPanel.vgrow = 1;
        TmUi.contentPanel.setBackgroundRgba(TmUi.contentBg);
        TmUi.contentPanel.setCornerRadius(TmTokens.CONTENT_RADIUS);

        TmChrome.buildProcessPage(TmUi.contentPanel);
        TmChrome.buildPerfPage(TmUi.contentPanel);
        TmChrome.buildStubPage(TmUi.contentPanel);

        HBox footer = new HBox();
        footer.spacing = 12;
        footer.padding = 8;
        footer.setPrefSize(400, 28);
        footer.setBackgroundRgba(TmUi.appBg);
        TmUi.footPrimary = TmUi.mkLabel("进程 —", 200, 22, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.appBg);
        TmUi.footPrimary.hgrow = 1;
        TmUi.footStatus = TmUi.mkLabel("就绪", 360, 22, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.appBg);
        footer.add(TmUi.footPrimary);
        footer.add(TmUi.footStatus);

        main.add(top);
        main.add(TmUi.contentPanel);
        main.add(footer);
        root.add(main);

        TmUi.window.setContent(root);
        TmUi.window.show();
        TmActions.showPage(TmTokens.PAGE_PROC);
        TmActions.syncCmdEnabled();
        TmActions.warmupAndArm();
    }
}

class TmChrome {
    static String navLabel(int idx) {
        if (idx == 0) { return "  进程"; }
        if (idx == 1) { return "  性能"; }
        if (idx == 2) { return "  应用历史记录"; }
        if (idx == 3) { return "  启动应用"; }
        if (idx == 4) { return "  用户"; }
        if (idx == 5) { return "  详细信息"; }
        if (idx == 6) { return "  服务"; }
        return "  设置";
    }

    static void buildNav(HBox root) {
        TmColumn nav = new TmColumn();
        nav.spacing = 2;
        nav.padding = 8;
        nav.prefWidth = TmTokens.NAV_W;
        nav.width = TmTokens.NAV_W;
        nav.setBackgroundRgba(TmUi.appBg);
        TmUi.navBox = nav;

        TmUi.navTop = new VBox();
        TmUi.navTop.spacing = 2;
        TmUi.navTop.padding = 0;
        TmUi.navTop.vgrow = 1;
        TmUi.navTop.setBackgroundRgba(TmUi.appBg);

        int i = 0;
        while (i < 7) {
            addNavItem(TmUi.navTop, i);
            i = i + 1;
        }
        nav.add(TmUi.navTop);

        /* Settings pinned at bottom */
        addNavItem(nav, TmTokens.PAGE_SETTINGS);
        /* Fix last button label */
        if (TmUi.navBtns.size() > TmTokens.PAGE_SETTINGS) {
            TmUi.navBtns.get(TmTokens.PAGE_SETTINGS).setText("  设置");
        }

        root.add(nav);
    }

    static void addNavItem(VBox parent, int idx) {
        HBox row = new HBox();
        row.spacing = 0;
        row.padding = 0;
        row.setPrefSize(TmTokens.NAV_W - 16, TmTokens.NAV_ITEM_H);
        row.setBackgroundRgba(TmUi.appBg);

        Panel ind = new Panel();
        ind.setPrefSize(3, 20);
        ind.setBackgroundRgba(TmUi.appBg);

        Button b = new Button(navLabel(idx));
        b.setPrefSize(TmTokens.NAV_W - 24, TmTokens.NAV_ITEM_H);
        TmUi.styleNavBtn(b, 0);
        int pageIdx = idx;
        b.onClick(() -> { TmActions.showPage(pageIdx); });

        row.add(ind);
        row.add(b);
        parent.add(row);

        TmUi.navRows.add(row);
        TmUi.navInds.add(ind);
        TmUi.navBtns.add(b);
    }

    static void buildProcessPage(VBox content) {
        TmPage page = new TmPage();
        page.spacing = 6;
        page.padding = 4;
        page.vgrow = 1;
        page.setBackgroundRgba(TmUi.contentBg);
        TmUi.pageProc = page;

        /* Command bar: 进程 | actions */
        HBox cmd = new HBox();
        cmd.spacing = 8;
        cmd.padding = 0;
        cmd.setPrefSize(400, TmTokens.CMD_H);
        cmd.setBackgroundRgba(TmUi.contentBg);
        TmUi.pageHeading = TmUi.mkLabel("进程", 120, 28, TmTokens.PAGE_TITLE_PT, TmUi.textC, TmUi.contentBg);
        TmUi.pageHeading.hgrow = 1;

        TmUi.runTaskBtn = new Button("运行新任务");
        TmUi.runTaskBtn.setPrefSize(96, TmTokens.CMD_BTN_H);
        TmUi.styleToolBtn(TmUi.runTaskBtn);
        TmUi.runTaskBtn.onClick(() -> { TmCommands.runNewTask(); });

        TmUi.endTaskBtn = new Button("结束任务");
        TmUi.endTaskBtn.setPrefSize(80, TmTokens.CMD_BTN_H);
        TmUi.styleDangerBtn(TmUi.endTaskBtn, 0);
        TmUi.endTaskBtn.setEnabled(false);
        TmUi.endTaskBtn.onClick(() -> { TmCommands.confirmEndTask(); });

        TmUi.ecoBtn = new Button("效率模式");
        TmUi.ecoBtn.setPrefSize(80, TmTokens.CMD_BTN_H);
        TmUi.styleToolBtnState(TmUi.ecoBtn, 0);
        TmUi.ecoBtn.setEnabled(false);
        TmUi.ecoBtn.onClick(() -> { TmCommands.toggleEfficiency(); });

        TmUi.moreBtn = new Button("更多");
        TmUi.moreBtn.setPrefSize(56, TmTokens.CMD_BTN_H);
        TmUi.styleToolBtn(TmUi.moreBtn);
        TmUi.moreBtn.onClick(() -> { TmCommands.showMoreMenu(0, 0); });

        TmUi.pauseBtn = new Button("暂停");
        TmUi.pauseBtn.setPrefSize(56, TmTokens.CMD_BTN_H);
        TmUi.styleToolBtn(TmUi.pauseBtn);
        TmUi.pauseBtn.onClick(() -> { TmActions.togglePause(); });

        TmUi.refreshBtn = new Button("刷新");
        TmUi.refreshBtn.setPrefSize(56, TmTokens.CMD_BTN_H);
        TmUi.styleToolBtn(TmUi.refreshBtn);
        TmUi.refreshBtn.onClick(() -> { TmActions.requestSample(1); });

        cmd.add(TmUi.pageHeading);
        cmd.add(TmUi.runTaskBtn);
        cmd.add(TmUi.endTaskBtn);
        cmd.add(TmUi.ecoBtn);
        cmd.add(TmUi.moreBtn);
        cmd.add(TmUi.pauseBtn);
        cmd.add(TmUi.refreshBtn);

        /* Pseudo dual header: pad + resource summaries aligned to columns */
        HBox dual = new HBox();
        dual.spacing = 0;
        dual.padding = 0;
        dual.setPrefSize(400, TmTokens.DUAL_H);
        dual.setBackgroundRgba(TmUi.contentBg);
        TmUi.dualPad = TmUi.mkLabel("", TmTokens.NAME_W + TmTokens.STATUS_W, 22,
                TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
        TmUi.dualCpu = TmUi.mkLabel("—", TmTokens.CPU_W, 22, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
        TmUi.dualMem = TmUi.mkLabel("—", TmTokens.MEM_W, 22, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
        TmUi.dualDisk = TmUi.mkLabel("—", TmTokens.DISK_W, 22, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
        TmUi.dualNet = TmUi.mkLabel("—", TmTokens.NET_W, 22, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
        dual.add(TmUi.dualPad);
        dual.add(TmUi.dualCpu);
        dual.add(TmUi.dualMem);
        dual.add(TmUi.dualDisk);
        dual.add(TmUi.dualNet);

        TmUi.table = new TableView<TmViewRow>();
        TmUi.table.vgrow = 1;
        TmUi.table.prefHeight = 200;
        TmUi.table.height = 200;
        TmUi.table.setBackgroundRgba(TmUi.contentBg);
        TmUi.table.setRowIdProvider(new TmRowIds());
        TmUi.table.setIconPathProvider(new TmRowIcons());
        TmUi.table.setRowHeight(TmTokens.ROW_H);
        TmUi.table.setEmptyMessage("正在采样…");
        TmActions.rebuildColumns();
        TmUi.table.onSelectionChanged((ev) -> {
            if (ev != null && ev.selectedItem != null) {
                TmActions.onSelectView(ev.selectedItem);
            }
        });
        TmUi.table.onDoubleClick((ev) -> { TmActions.onActivateRow(); });
        TmUi.table.onHeaderClick((ev) -> { TmActions.sort(ev.selectedIndex); });
        TmUi.table.onMouse((ev) -> {
            if (ev != null && ev.button == 2) {
                TmCommands.showRowContextMenu(ev.x, ev.y);
            }
        });

        page.add(cmd);
        page.add(dual);
        page.add(TmUi.table);
        content.add(page);
    }

    static void buildPerfPage(VBox content) {
        TmPage page = new TmPage();
        page.spacing = Theme.SPACE_3;
        page.padding = Theme.SPACE_4;
        page.vgrow = 1;
        page.setBackgroundRgba(TmUi.contentBg);
        TmUi.pagePerf = page;
        page.setVisible(false);
        TmUi.perfGap = TmUi.mkLabel(
                "部分实现：仅整机 CPU/内存曲线。磁盘/网络/GPU 分栏与设备信息未实现（见对照清单）。",
                400, 40, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
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
        TmUi.perfMetaLabel = TmUi.mkLabel("采样口径见 docs/windows-task-monitor-w11-parity.md",
                400, 24, TmTokens.MUTED_PT, TmUi.mutedC, TmUi.contentBg);
        page.add(TmUi.perfGap);
        page.add(TmUi.perfCpuLabel);
        page.add(TmUi.cpuChart);
        page.add(TmUi.perfMemLabel);
        page.add(TmUi.memChart);
        page.add(TmUi.perfMetaLabel);
        content.add(page);
    }

    static void buildStubPage(VBox content) {
        TmPage page = new TmPage();
        page.spacing = Theme.SPACE_2;
        page.padding = Theme.SPACE_4;
        page.vgrow = 1;
        page.setBackgroundRgba(TmUi.contentBg);
        TmUi.pageStub = page;
        page.setVisible(false);
        TmUi.stubBody = TmUi.mkLabel("未实现", 400, 200, 14, TmUi.textC, TmUi.contentBg);
        page.add(TmUi.stubBody);
        content.add(page);
    }
}

class TmRowIds implements TableRowIdProvider<TmViewRow> {
    public String rowId(TmViewRow row) {
        if (row == null) {
            return "";
        }
        return row.rowId();
    }
}

class TmRowIcons implements TableIconPathProvider<TmViewRow> {
    public String iconPath(TmViewRow row) {
        if (row == null) {
            return "jn:kind:section";
        }
        if (row.kind == TmViewRow.KIND_SECTION) {
            return "jn:kind:section";
        }
        if (row.kind == TmViewRow.KIND_GROUP) {
            if (row.expanded != 0) {
                return "jn:icon:chevron-down";
            }
            return "jn:icon:chevron-right";
        }
        if (row.proc != null && row.proc.sample != null
                && row.proc.sample.path != null && row.proc.sample.path.length() > 0) {
            return row.proc.sample.path;
        }
        return "";
    }
}

class TmColName implements ColumnRenderer<TmViewRow> {
    public String getTitle() {
        return TmActions.colTitle(0, "名称");
    }
    public String renderRow(TmViewRow row) {
        if (row == null) {
            return "";
        }
        if (row.kind == TmViewRow.KIND_SECTION) {
            return row.title;
        }
        if (row.kind == TmViewRow.KIND_GROUP) {
            return row.title + " (" + String.valueOf(row.childCount) + ")";
        }
        String indent = "";
        if (row.groupKey != null && row.groupKey.length() > 0) {
            indent = "    ";
        }
        String n = row.title;
        if (row.proc != null && row.proc.sample != null && row.proc.sample.name != null) {
            n = row.proc.sample.name;
        }
        return indent + n;
    }
}

class TmColStatus implements ColumnRenderer<TmViewRow> {
    public String getTitle() {
        return TmActions.colTitle(1, "状态");
    }
    public String renderRow(TmViewRow row) {
        if (row == null || row.kind == TmViewRow.KIND_SECTION) {
            return "";
        }
        AmProcSample s = null;
        if (row.kind == TmViewRow.KIND_GROUP && row.proc != null) {
            s = row.proc.sample;
        } else if (row.proc != null) {
            s = row.proc.sample;
        }
        return TmFormat.statusOf(s);
    }
}

class TmColCpu implements ColumnRenderer<TmViewRow> {
    public String getTitle() {
        return TmActions.colTitle(2, "CPU");
    }
    public String renderRow(TmViewRow row) {
        if (row == null || row.kind == TmViewRow.KIND_SECTION) {
            return "";
        }
        return TmFormat.cpuPct1(row.cpuMilli);
    }
}

class TmColMem implements ColumnRenderer<TmViewRow> {
    public String getTitle() {
        return TmActions.colTitle(3, "内存");
    }
    public String renderRow(TmViewRow row) {
        if (row == null || row.kind == TmViewRow.KIND_SECTION) {
            return "";
        }
        return TmFormat.bytes(row.memBytes);
    }
}

class TmColDisk implements ColumnRenderer<TmViewRow> {
    public String getTitle() {
        return TmActions.colTitle(4, "磁盘");
    }
    public String renderRow(TmViewRow row) {
        if (row == null || row.kind == TmViewRow.KIND_SECTION) {
            return "";
        }
        /* Honest gap: no per-process disk throughput this round. */
        return TmFormat.na();
    }
}

class TmColNet implements ColumnRenderer<TmViewRow> {
    public String getTitle() {
        return TmActions.colTitle(5, "网络");
    }
    public String renderRow(TmViewRow row) {
        if (row == null || row.kind == TmViewRow.KIND_SECTION) {
            return "";
        }
        return TmFormat.na();
    }
}
