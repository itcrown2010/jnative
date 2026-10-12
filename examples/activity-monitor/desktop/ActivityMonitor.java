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
        Application app = new Application();
        /* Apply after UiNative.init so Cocoa can set NSApp.appearance. */
        Theme.setAppearanceMode(appearance);
        AmWindow.show();
        app.run();
    }
}

class AmUi {
    static String forceAppearance;
    static Window window;
    static Window detailWin;
    static Label detailBody;
    static Label footCpu;
    static Label footMem;
    static Label footSpark;
    static Label footStatus;
    static SearchField search;
    static Button detailBtn;
    static Button termBtn;
    static Button cpuModeBtn;
    static Button memModeBtn;
    static Button moreBtn;
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
    static ArrayList<AmProcRow> viewRows;
    static long lastTableRebuildNs;
    static int tableDirty;
    static String lastSearchApplied;
    static ArrayList<int> cpuHistory;
    static ArrayList<String> iconCacheKeys;
    static ArrayList<String> iconCachePaths;
    static Label metricsNote;

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
        viewRows = new ArrayList<AmProcRow>();
        lastTableRebuildNs = 0;
        tableDirty = 0;
        lastSearchApplied = "";
        cpuHistory = new ArrayList<int>();
        iconCacheKeys = new ArrayList<String>();
        iconCachePaths = new ArrayList<String>();
    }
}

class AmWindow {
    static void show() {
        AmUi.ensure();
        AmUi.window = new Window(AmI18n.t("title"), 1100, 720);
        AmUi.window.setMinSize(720, 420);
        AmUi.window.onClose(() -> {
            AmUi.alive = 0;
            AmUi.sampleToken = AmUi.sampleToken + 1;
            AmUi.refreshGen = AmUi.refreshGen + 1;
            if (AmUi.detailWin != null) {
                AmUi.detailWin = null;
                AmUi.detailBody = null;
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
        toolbar.spacing = 4;
        toolbar.padding = 4;
        AmUi.termBtn = toolbar.addToolButton(AmI18n.t("term"), () -> {
            AmActions.confirmTerminate();
        });
        AmUi.detailBtn = toolbar.addToolButton(AmI18n.t("detail"), () -> {
            AmActions.showDetail();
        });
        toolbar.addSeparatorGap();
        AmUi.cpuModeBtn = toolbar.addToolButton(AmI18n.t("mode_cpu"), () -> {
            AmActions.setViewMode(0);
        });
        AmUi.memModeBtn = toolbar.addToolButton(AmI18n.t("mode_mem"), () -> {
            AmActions.setViewMode(1);
        });
        toolbar.addFlexibleSpace();
        AmUi.search = new SearchField();
        AmUi.search.setPlaceholder(AmI18n.t("search_ph"));
        AmUi.search.setOnQueryChanged(() -> {
            AmActions.onSearchChanged();
        });
        AmUi.search.prefWidth = 220;
        AmUi.search.width = 220;
        AmUi.search.prefHeight = 28;
        AmUi.search.height = 28;
        if (AmUi.search.field != null) {
            AmUi.search.field.setPrefSize(180, 24);
        }
        toolbar.add(AmUi.search);
        AmUi.moreBtn = toolbar.addToolButton(AmI18n.t("more"), () -> {
            AmActions.showMoreMenu();
        });
        toolbar.prefHeight = Theme.TOOLBAR_HEIGHT;
        toolbar.height = Theme.TOOLBAR_HEIGHT;

        AmUi.table = new TableView<AmProcRow>();
        AmUi.table.vgrow = 1;
        AmUi.table.prefHeight = 480;
        AmUi.table.height = 480;
        AmActions.rebuildColumns();
        AmUi.table.setIconPathProvider(new AmIconPaths());
        AmUi.table.onSelectionChanged((ev) -> {
            AmActions.onSelect(ev.selectedIndex);
        });
        AmUi.table.onAction((ev) -> {
            AmActions.showDetail();
        });
        AmUi.table.onHeaderClick((ev) -> {
            AmActions.sort(ev.selectedIndex);
        });

        HBox footer = new HBox();
        footer.spacing = Theme.SPACE_2;
        footer.padding = 4;
        footer.prefHeight = 40;
        footer.height = 40;
        AmUi.footCpu = new Label(AmI18n.t("foot_cpu_wait"));
        AmUi.footCpu.setPrefSize(180, 28);
        AmUi.footMem = new Label(AmI18n.t("foot_mem_wait"));
        AmUi.footMem.setPrefSize(300, 28);
        AmUi.footSpark = new Label("");
        AmUi.footSpark.setPrefSize(120, 28);
        AmUi.footStatus = new Label(AmI18n.t("status_ready"));
        AmUi.footStatus.setPrefSize(200, 28);
        footer.add(AmUi.footCpu);
        footer.add(AmUi.footMem);
        footer.add(AmUi.footSpark);
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.width = 0;
        footer.add(flex);
        footer.add(AmUi.footStatus);

        AmUi.metricsNote = new Label(AmI18n.t("metrics_hint"));
        AmUi.metricsNote.setPrefSize(1080, 18);

        root.add(toolbar);
        root.add(AmUi.table);
        root.add(footer);
        root.add(AmUi.metricsNote);

        AmUi.window.setContent(root);
        AmUi.window.show();
        AmActions.syncToolbarEnabled();
        AmActions.syncModeButtons();
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
        if (key.equals("col_mem_foot")) return "内存";
        if (key.equals("col_thr")) return "线程";
        if (key.equals("foot_cpu_wait")) return "CPU\n—";
        if (key.equals("foot_mem_wait")) return "内存\n—";
        if (key.equals("metrics_hint")) return "内存列 = 物理占用（ri_phys_footprint）；RSS 见详情。CPU 100% = 1 逻辑核心。";
        if (key.equals("no_sel")) return "请先选择一个进程";
        if (key.equals("detail_title")) return "进程详情";
        if (key.equals("gone")) return "所选进程已退出或身份失效";
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
        if (key.equals("foot_cpu_wait")) return "CPU\n—";
        if (key.equals("foot_mem_wait")) return "Memory\n—";
        if (key.equals("metrics_hint")) return "Memory column = phys footprint; RSS in Inspect. CPU 100% = 1 logical core.";
        if (key.equals("no_sel")) return "Select a process first";
        if (key.equals("detail_title")) return "Process";
        if (key.equals("gone")) return "Process exited or identity changed";
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
        AmUi.table.addColumn(new AmColName());
        AmUi.table.addColumn(new AmColPid());
        AmUi.table.addColumn(new AmColUser());
        AmUi.table.addColumn(new AmColCpu());
        AmUi.table.addColumn(new AmColMem());
        AmUi.table.addColumn(new AmColThr());
    }

    static void syncModeButtons() {
        /* Visual cue via label prefix — chrome is borderless toolbar. */
        if (AmUi.cpuModeBtn != null) {
            if (AmUi.viewMode == 0) {
                AmUi.cpuModeBtn.setText("● " + AmI18n.t("mode_cpu"));
            } else {
                AmUi.cpuModeBtn.setText(AmI18n.t("mode_cpu"));
            }
        }
        if (AmUi.memModeBtn != null) {
            if (AmUi.viewMode == 1) {
                AmUi.memModeBtn.setText("● " + AmI18n.t("mode_mem"));
            } else {
                AmUi.memModeBtn.setText(AmI18n.t("mode_mem"));
            }
        }
    }

    static void setViewMode(int mode) {
        AmUi.viewMode = mode;
        if (mode == 0) {
            AmUi.sortCol = 3;
            AmUi.sortAsc = 0;
        } else {
            AmUi.sortCol = 4;
            AmUi.sortAsc = 0;
        }
        syncModeButtons();
        AmUi.tableDirty = 1;
        rebuildColumns();
        maybeRebuildTable(1);
        updateFooter();
    }

    static void syncToolbarEnabled() {
        int on = 0;
        if (AmUi.selPid > 0) {
            AmProcRow r = selectedRow();
            if (r != null) {
                on = 1;
            }
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

    static void onSearchChanged() {
        if (AmUi.search == null) {
            return;
        }
        String q = AmUi.search.getQuery();
        if (q == null) {
            q = "";
        }
        AmUi.searchText = q;
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
    }

    static void finishSampleUi(int force) {
        updateFooter();
        if (AmUi.state.sampleCount >= 2) {
            if (force != 0 || AmUi.tableDirty != 0 || AmUi.lastTableRebuildNs == 0) {
                maybeRebuildTable(1);
            }
            if (ActivityMonitor.demoDetail != 0 && AmUi.detailWin == null
                    && AmUi.viewRows != null && AmUi.viewRows.size() > 0) {
                AmUi.table.select(0);
                AmActions.onSelect(0);
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
        if (AmUi.footCpu == null || AmUi.state == null) {
            return;
        }
        String cpu = AmFormat.cpuPct(AmUi.state.sysCpuMilli);
        AmUi.footCpu.setText("CPU  " + cpu + "\n" + String.valueOf(AmUi.state.ncpu) + " 逻辑核心");
        String used = AmFormat.bytes(AmUi.state.sysMemUsedBytes);
        String free = AmFormat.bytes(AmUi.state.sysMemFreeBytes);
        String total = AmFormat.bytes(AmUi.state.memTotal);
        AmUi.footMem.setText("内存  已用(页合计) " + used + "\n合计 " + total + " · 空闲页 " + free);
        AmUi.footSpark.setText("趋势\n" + sparkline());
    }

    static String sparkline() {
        if (AmUi.cpuHistory == null || AmUi.cpuHistory.size() < 2) {
            return "—";
        }
        /* ASCII sparkline from real sysCpuMilli samples (0–10000+). */
        String blocks = " .:-=+*#@";
        String out = "";
        int i = 0;
        while (i < AmUi.cpuHistory.size()) {
            int m = AmUi.cpuHistory.get(i);
            if (m < 0) {
                m = 0;
            }
            int idx = m / 1250;
            if (idx < 0) {
                idx = 0;
            }
            if (idx > 8) {
                idx = 8;
            }
            out = out + blocks.substring(idx, idx + 1);
            i = i + 1;
        }
        if (out.length() == 0) {
            return "—";
        }
        return out;
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
        AmUi.table.setItems(AmUi.viewRows);
        if (AmUi.selPid > 0) {
            int idx = 0;
            int found = -1;
            while (idx < AmUi.viewRows.size()) {
                AmProcRow r = AmUi.viewRows.get(idx);
                if (r.sample.pid == AmUi.selPid
                        && r.sample.startSec == AmUi.selStartSec
                        && r.sample.startUsec == AmUi.selStartUsec) {
                    found = idx;
                    break;
                }
                idx = idx + 1;
            }
            if (found >= 0) {
                AmUi.table.select(found);
            } else {
                AmUi.selPid = -1;
                if (AmUi.detailBody != null) {
                    AmUi.detailBody.setText(AmI18n.t("gone"));
                }
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
            syncToolbarEnabled();
            return;
        }
        AmProcRow r = AmUi.viewRows.get(viewIndex);
        AmUi.selPid = r.sample.pid;
        AmUi.selStartSec = r.sample.startSec;
        AmUi.selStartUsec = r.sample.startUsec;
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

    static void showDetail() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(AmUi.window, AmI18n.t("no_sel"));
            return;
        }
        if (AmUi.detailWin == null) {
            AmUi.detailWin = new Window(AmI18n.t("detail_title"), 420, 360);
            AmUi.detailWin.setMinSize(320, 240);
            AmUi.detailWin.onClose(() -> {
                AmUi.detailWin = null;
                AmUi.detailBody = null;
            });
            VBox box = new VBox();
            box.spacing = 8;
            box.padding = 16;
            AmUi.detailBody = new Label("");
            AmUi.detailBody.setPrefSize(380, 280);
            Button copyBtn = new Button("复制");
            copyBtn.onClick(() -> {
                if (AmUi.detailBody != null) {
                    /* Clipboard may be unavailable; still show text for manual copy. */
                    Dialogs.info(AmUi.detailWin, AmUi.detailBody.getText());
                }
            });
            box.add(AmUi.detailBody);
            box.add(copyBtn);
            AmUi.detailWin.setContent(box);
            AmUi.detailWin.show();
        }
        AmUi.detailBody.setText(formatDetail(r));
    }

    static void refreshDetailIfOpen() {
        if (AmUi.detailWin == null || AmUi.detailBody == null) {
            return;
        }
        AmProcRow r = selectedRow();
        if (r == null) {
            AmUi.detailBody.setText(AmI18n.t("gone"));
            return;
        }
        AmUi.detailBody.setText(formatDetail(r));
    }

    static String formatDetail(AmProcRow r) {
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
        String prot = "";
        if (AmProtect.isProtected(r.sample) != 0) {
            prot = "\n保护：" + AmProtect.reason(r.sample);
        }
        return "名称：" + r.sample.name + "\n"
                + "PID：" + String.valueOf(r.sample.pid) + "\n"
                + "PPID：" + String.valueOf(r.sample.ppid) + "\n"
                + "用户：" + r.sample.user + " (uid=" + String.valueOf(r.sample.uid) + ")\n"
                + "CPU：" + AmFormat.cpuPct(r.cpuMilli) + "（100%=1 逻辑核心）\n"
                + "物理占用：" + foot + "（ri_phys_footprint）\n"
                + "RSS：" + rss + "（pti_resident_size）\n"
                + "线程：" + AmFormat.threads(r.sample) + "\n"
                + "路径：" + path + "\n"
                + "启动：" + AmParse.longToString(r.sample.startSec) + "."
                + String.valueOf(r.sample.startUsec)
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

