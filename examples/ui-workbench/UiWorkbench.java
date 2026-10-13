/**
 * Desktop UI workbench — layout, toolbar, table, trend chart, dialogs, background→UI.
 * macOS first; see docs/desktop-ui-platform-matrix.md for support status.
 *
 * @author 张晓达
 */
public class UiWorkbench {
    public static void main(String[] args) {
        int appearance = Theme.MODE_SYSTEM;
        if (args != null) {
            int i = 0;
            while (i < args.length) {
                if (args[i].equals("--appearance") && i + 1 < args.length) {
                    if (args[i + 1].equals("dark")) {
                        appearance = Theme.MODE_DARK;
                    } else if (args[i + 1].equals("light")) {
                        appearance = Theme.MODE_LIGHT;
                    }
                    i = i + 1;
                }
                i = i + 1;
            }
        }
        Application.setAppName("UI 工作台");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(appearance);
        Application.onQuit(() -> {
            WbUi.alive = 0;
            if (WbUi.clickSub != null) {
                WbUi.clickSub.cancel();
                WbUi.clickSub = null;
            }
        });
        WbUi.show();
        app.run();
    }
}

class WbRow {
    String id;
    String name;
    int value;

    WbRow(String i, String n, int v) {
        id = i;
        name = n;
        value = v;
    }
}

class WbRowIds implements TableRowIdProvider<WbRow> {
    String rowId(WbRow row) {
        if (row == null) {
            return "";
        }
        return row.id;
    }
}

class WbColName implements ColumnRenderer<WbRow> {
    String getTitle() {
        return "名称";
    }
    String renderRow(WbRow r) {
        if (r == null) {
            return "";
        }
        return r.name;
    }
}

class WbColVal implements ColumnRenderer<WbRow> {
    String getTitle() {
        return "数值";
    }
    String renderRow(WbRow r) {
        if (r == null) {
            return "";
        }
        return String.valueOf(r.value);
    }
}

class WbUi {
    static Window window;
    static Window auxWin;
    static Label status;
    static SearchField search;
    static TableView<WbRow> table;
    static TrendChart trend;
    static SegmentedControl mode;
    static ArrayList<WbRow> rows;
    static int tick;
    static int alive;
    static UiSubscription clickSub;
    static int dirtyNote;

    static void show() {
        alive = 1;
        tick = 0;
        dirtyNote = 0;
        rows = new ArrayList<WbRow>();
        int i = 0;
        while (i < 12) {
            rows.add(new WbRow("r" + String.valueOf(i), "项目 " + String.valueOf(i), i * 7));
            i = i + 1;
        }
        window = new Window("JNative UI 工作台", 920, 640);
        window.setMinSize(640, 420);
        window.onClose(() -> {
            WbActions.onClose();
        });
        WbActions.build();
        WbActions.installMenus();
        ShortcutManager.installOn(window);
        window.show();
        status.setText(UiPlatform.supportSummary());
        WbActions.armTick();
    }
}

class WbActions {
    static void onClose() {
        if (WbUi.dirtyNote != 0) {
            int c = Dialogs.askSave(WbUi.window, "工作台有未保存的演示标记。");
            if (c == 0) {
                WbUi.window.vetoClose();
                return;
            }
            if (c == 1) {
                WbUi.dirtyNote = 0;
                WbUi.status.setText("已保存演示标记");
            }
        }
        WbUi.alive = 0;
        if (WbUi.clickSub != null) {
            WbUi.clickSub.cancel();
            WbUi.clickSub = null;
        }
    }

    static void installMenus() {
        Command openCmd = new Command("wb.open", "打开文件…");
        openCmd.setShortcut(KeyCombination.of(79).withPrimary());
        openCmd.setExecute(() -> {
            String p = FileDialog.open(WbUi.window, "txt,json");
            if (p == null) {
                WbUi.status.setText("打开已取消");
                return;
            }
            WbUi.status.setText("已选: " + p);
            WbUi.dirtyNote = 1;
        });
        Command saveCmd = new Command("wb.save", "保存…");
        saveCmd.setShortcut(KeyCombination.of(83).withPrimary());
        saveCmd.setExecute(() -> {
            String p = FileDialog.save(WbUi.window, "txt");
            if (p == null) {
                WbUi.status.setText("保存已取消");
                return;
            }
            int ok = FileDialog.writeTextSafe(p, "workbench demo\n");
            if (ok != 0) {
                WbUi.dirtyNote = 0;
                WbUi.status.setText("已安全写入: " + p);
            } else {
                WbUi.status.setText("写入失败");
            }
        });
        Command auxCmd = new Command("wb.aux", "打开辅助窗口");
        auxCmd.setExecute(() -> {
            WbActions.openAux();
        });
        Command quitCmd = new Command("wb.quit", "退出");
        quitCmd.setExecute(() -> {
            if (WbUi.dirtyNote != 0) {
                int c = Dialogs.askSave(WbUi.window, "退出前保存演示标记？");
                if (c == 0) {
                    return;
                }
                if (c == 1) {
                    WbUi.dirtyNote = 0;
                }
            }
            Application.requestQuit();
        });
        Command aboutCmd = new Command("wb.about", "关于工作台");
        aboutCmd.setExecute(() -> {
            WbActions.onAbout();
        });
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        MenuItem miOpen = new MenuItem("打开文件…");
        openCmd.bindMenuItem(miOpen);
        file.add(miOpen);
        MenuItem miSave = new MenuItem("保存…");
        saveCmd.bindMenuItem(miSave);
        file.add(miSave);
        file.addSeparator();
        MenuItem miQuit = new MenuItem("退出");
        quitCmd.bindMenuItem(miQuit);
        file.add(miQuit);
        bar.add(file);
        bar.add(AppMenus.editMenu());
        Menu win = new Menu("窗口");
        MenuItem miAux = new MenuItem("打开辅助窗口");
        auxCmd.bindMenuItem(miAux);
        win.add(miAux);
        bar.add(win);
        bar.add(AppMenus.helpMenu(aboutCmd));
        WbUi.window.setMenuBar(bar);
    }

    static void openAux() {
        if (WbUi.auxWin != null && WbUi.auxWin.disposed == 0) {
            WbUi.auxWin.activate();
            return;
        }
        Window aw = new Window("辅助窗口", 400, 240);
        aw.setOwnerWindow(WbUi.window);
        VBox box = new VBox();
        box.setSpacing(8);
        box.setPadding(16);
        box.add(new Label("主窗口关闭时，此辅助窗口一并关闭。"));
        Button b = new Button("关闭");
        b.onClick(() -> {
            aw.close();
        });
        box.add(b);
        aw.setContent(box);
        aw.onClose(() -> {
            WbUi.auxWin = null;
        });
        WbUi.auxWin = aw;
        aw.show();
    }

    static void build() {
        VBox root = new VBox();
        root.setSpacing(0);
        root.setPadding(0);

        Toolbar tb = new Toolbar();
        tb.setSpacing(6);
        tb.setPadding(6);
        Button addBtn = tb.addPushToolButton("添加", "plus.circle", "追加一行", () -> {
            WbActions.onAdd();
        });
        WbUi.clickSub = addBtn.subscribeClick(() -> {
            WbActions.noop();
        });
        tb.addSeparatorGap();
        WbUi.mode = new SegmentedControl("布局\t表格\t图表");
        WbUi.mode.onSelectionChanged((ev) -> {
            WbActions.onSeg(ev.selectedIndex);
        });
        tb.addSegmented(WbUi.mode);
        tb.addFlexibleSpace();
        WbUi.search = new SearchField();
        WbUi.search.setPlaceholder("过滤…");
        WbUi.search.setOnQueryChanged(() -> {
            WbActions.onSearch();
        });
        tb.addSearch(WbUi.search);
        tb.addPushToolButton("信息", "info.circle", "信息对话框", () -> {
            WbActions.onInfo();
        });
        tb.addPushToolButton("确认", "questionmark.circle", "确认对话框", () -> {
            WbActions.onConfirm();
        });
        Menu more = new Menu("更多");
        more.add(new MenuItem("关于能力", () -> {
            WbActions.onAbout();
        }));
        if (UiPlatform.supports(UiPlatform.FEAT_OVERFLOW_MENU) != 0) {
            tb.addOverflowMenu("更多", more);
        }

        WbUi.table = new TableView<WbRow>();
        WbUi.table.vgrow = 1;
        WbUi.table.setRowIdProvider(new WbRowIds());
        WbUi.table.setEmptyMessage("无数据");
        WbUi.table.addColumnDef("名称", new WbColName(), 0, "L", 1);
        WbUi.table.addColumnDef("数值", new WbColVal(), 100, "R", 0);
        WbUi.table.setItems(WbUi.rows);
        WbUi.table.onSelectionChanged((ev) -> {
            WbActions.onSelect(ev.selectedIndex, ev.selectedItem);
        });
        WbUi.table.onDoubleClick((ev) -> {
            WbActions.onDbl();
        });
        WbUi.table.onHeaderClick((ev) -> {
            WbActions.onHeader(ev.selectedIndex);
        });

        HBox footer = new HBox();
        footer.setSpacing(12);
        footer.setPadding(8);
        footer.prefHeight = 56;
        footer.height = 56;
        Label title = new Label("真实采样趋势（后台推送）");
        Theme.applyStyle(title, Theme.STYLE_SECONDARY);
        title.setPrefSize(200, 40);
        WbUi.trend = new TrendChart();
        WbUi.trend.setCapacity(48);
        WbUi.trend.setYRange(0.0, 100.0);
        WbUi.trend.setPrefSize(220, 40);
        WbUi.status = new Label("就绪");
        Theme.applyStyle(WbUi.status, Theme.STYLE_BODY);
        WbUi.status.setPrefSize(360, 40);
        Label flex = new Label("");
        flex.hgrow = 1;
        flex.prefWidth = 0;
        footer.add(title);
        footer.add(WbUi.trend);
        footer.add(flex);
        footer.add(WbUi.status);

        root.add(tb);
        root.add(WbUi.table);
        root.add(footer);
        WbUi.window.setContent(root);
    }

    static void noop() {
    }

    static void onSeg(int idx) {
        WbUi.status.setText("分段: " + String.valueOf(idx));
    }

    static void onSearch() {
        WbUi.status.setText("搜索: " + WbUi.search.getQuery());
    }

    static void onInfo() {
        Dialogs.info(WbUi.window, "公共 Dialogs.info 示例。");
    }

    static void onConfirm() {
        boolean ok = Dialogs.confirm(WbUi.window, "继续演示？");
        if (ok) {
            WbUi.status.setText("确认结果=是");
        } else {
            WbUi.status.setText("确认结果=否");
        }
    }

    static void onAbout() {
        Dialogs.info(WbUi.window, UiPlatform.supportSummary());
    }

    static void onSelect(int index, WbRow r) {
        if (r != null) {
            WbUi.status.setText("选中 id=" + r.id + " 行号(勿作身份)=" + String.valueOf(index));
        }
    }

    static void onDbl() {
        Dialogs.info(WbUi.window, "双击 selectedRowId=" + WbUi.table.getSelectedRowId());
    }

    static void onHeader(int col) {
        WbUi.table.sortByColumn(col);
        WbUi.status.setText("排序列 " + String.valueOf(col));
    }

    static void onAdd() {
        int n = WbUi.rows.size();
        String id = "r" + String.valueOf(n) + "_" + String.valueOf(WbUi.tick);
        WbUi.rows.add(new WbRow(id, "新项目 " + String.valueOf(n), WbUi.tick));
        WbUi.table.setItems(WbUi.rows);
        WbUi.table.selectByRowId(id);
        WbUi.status.setText("已添加并选中 " + id);
    }

    static void armTick() {
        if (WbUi.alive == 0) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(800);
            } catch (Exception e) {
            }
            Application.invokeLater(() -> {
                WbActions.onTick();
            });
        });
        t.start();
    }

    static void onTick() {
        if (WbUi.alive == 0 || WbUi.window.disposed != 0) {
            return;
        }
        WbUi.tick = WbUi.tick + 1;
        double v = (double) ((WbUi.tick * 17) % 100);
        WbUi.trend.push(v);
        if (WbUi.rows.size() > 0) {
            WbRow r0 = WbUi.rows.get(0);
            r0.value = WbUi.tick;
            String keep = WbUi.table.getSelectedRowId();
            WbUi.table.setItems(WbUi.rows);
            if (keep != null) {
                WbUi.table.selectByRowId(keep);
            }
        }
        armTick();
    }
}
