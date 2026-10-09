
/**
 * JNative Explorer — native desktop file manager.
 *
 * <pre>
 * java -cp target/classes io.jnative.Main \
 *   examples/explorer/JNativeExplorer.java -o build/explorer/JNativeExplorer --app-type gui
 * ./build/explorer/JNativeExplorer
 * </pre>
 *
 * No JVM / Swing / Electron at runtime. Explorer-specific LLVM intrinsics: NONE.
 * @author 张晓达
 */
public class JNativeExplorer {
    public static void main(String[] args) {
        Application app = new Application();
        ExplorerWindow win = new ExplorerWindow();
        win.show();
        app.run();
    }
}

/* ---------- Models ---------- */

class NavNode {
    String label;
    FsPath path;
    int kind; /* 0=group 1=folder 2=drive */
    ArrayList<NavNode> children;

    NavNode(String lab, FsPath p, int k) {
        label = lab;
        path = p;
        kind = k;
        children = new ArrayList<NavNode>();
    }
}

class NavTreeModel implements TreeModel<NavNode> {
    NavNode r;

    NavTreeModel(NavNode root) {
        r = root;
    }

    NavNode root() {
        return r;
    }

    int childCount(NavNode node) {
        return node.children.size();
    }

    NavNode child(NavNode node, int index) {
        return node.children.get(index);
    }
}

class NavigationHistory {
    ArrayList<FsPath> stack;
    int index;

    NavigationHistory() {
        stack = new ArrayList<FsPath>();
        index = -1;
    }

    void visit(FsPath path) {
        ArrayList<FsPath> next = new ArrayList<FsPath>();
        int i = 0;
        while (i <= index && i < stack.size()) {
            next.add(stack.get(i));
            i = i + 1;
        }
        next.add(path);
        stack = next;
        index = stack.size() - 1;
    }

    FsPath back() {
        if (index <= 0) {
            return null;
        }
        index = index - 1;
        return stack.get(index);
    }

    FsPath forward() {
        if (index < 0 || index >= stack.size() - 1) {
            return null;
        }
        index = index + 1;
        return stack.get(index);
    }

    int canBack() {
        if (index > 0) {
            return 1;
        }
        return 0;
    }

    int canForward() {
        if (index >= 0 && index < stack.size() - 1) {
            return 1;
        }
        return 0;
    }
}

class NameCol implements ColumnRenderer<FileInfo> {
    String getTitle() {
        return "Name";
    }

    String renderRow(FileInfo f) {
        return f.name;
    }
}

class ModifiedCol implements ColumnRenderer<FileInfo> {
    String getTitle() {
        return "Date Modified";
    }

    String renderRow(FileInfo f) {
        return ExplorerFormat.time(f.modifiedTime);
    }
}

class SizeCol implements ColumnRenderer<FileInfo> {
    String getTitle() {
        return "Size";
    }

    String renderRow(FileInfo f) {
        if (f.isDirectory != 0) {
            return "—";
        }
        return FileSize.format(f.size);
    }
}

class TypeCol implements ColumnRenderer<FileInfo> {
    String getTitle() {
        return "Kind";
    }

    String renderRow(FileInfo f) {
        if (f.isDirectory != 0) {
            return "Folder";
        }
        if (f.type == null || f.type.length() == 0) {
            return "Document";
        }
        return f.type;
    }
}

class FileIconPath implements TableIconPathProvider<FileInfo> {
    String iconPath(FileInfo f) {
        if (f == null || f.path == null) {
            return "";
        }
        return f.path.toString();
    }
}

class ExplorerBreadcrumbNav implements BreadcrumbNavigate {
    void go(String path) {
        ExplorerActions.breadcrumbGo(path);
    }
}

class ExplorerSearchDebounce {
    static int gen;
    static int finishGen;

    static void run() {
        Thread.sleep(300);
        finishGen = gen;
        Application.invokeLater(() -> {
            ExplorerSearchDebounce.finish();
        });
    }

    static void finish() {
        int g = finishGen;
        if (ExplorerActions.win != null && g == ExplorerActions.win.searchDebounceGen) {
            ExplorerActions.runSearch();
        }
    }
}

class NavRenderer implements CellRenderer<NavNode> {
    String render(NavNode n) {
        return n.label;
    }
}

class ExplorerActions {
    static ExplorerWindow win;

    static void goBack() {
        Application.invokeLater(() -> {
            ExplorerActions.win.goBack();
        });
    }

    static void goForward() {
        Application.invokeLater(() -> {
            ExplorerActions.win.goForward();
        });
    }

    static void goUp() {
        Application.invokeLater(() -> {
            ExplorerActions.win.goUp();
        });
    }

    static void refresh() {
        Application.invokeLater(() -> {
            ExplorerActions.win.refresh(1);
        });
    }

    static void navigateAddress() {
        Application.invokeLater(() -> {
            ExplorerActions.win.navigateAddress();
        });
    }

    static void runSearchDeferred() {
        Application.invokeLater(() -> {
            ExplorerActions.win.runSearch();
        });
    }

    static void runSearch() {
        win.runSearch();
    }

    static void updateStatus() {
        win.updateStatus();
    }

    static void onTreeSelect() {
        NavNode n = win.tree.getSelectionModel().getSelectedItem();
        if (n != null && n.path != null && n.kind != 0) {
            pendingNav = n;
            /* Always enqueue: invokeLater runs sync on the UI thread, and
               SELECT used to fire nested inside AppKit selection (crash). */
            UiTaskQueue.enqueue(() -> {
                ExplorerActions.finishTreeOpen();
            });
        }
    }

    static void onAddressKey(KeyEvent ev) {
        if (ev != null && ev.keyCode == 13) {
            win.navigateAddress();
        }
    }

    static void onSearchKey(KeyEvent ev) {
        if (ev != null && ev.keyCode == 13) {
            win.runSearch();
        }
    }

    static void onWindowKey(KeyEvent ev) {
        win.handleKey(ev);
    }

    static void newFolder() {
        win.newFolder();
    }

    static void openSelected() {
        win.openSelected();
    }

    static FileInfo pendingOpen;
    static NavNode pendingNav;

    static void activateTree() {
        if (win == null || win.tree == null) {
            return;
        }
        pendingNav = win.tree.getSelectionModel().getSelectedItem();
        UiTaskQueue.enqueue(() -> {
            ExplorerActions.finishTreeOpen();
        });
    }

    static void finishOpen() {
        FileInfo f = pendingOpen;
        pendingOpen = null;
        if (f == null || win == null) {
            return;
        }
        if (f.isDirectory != 0) {
            win.navigateTo(f.path, 1);
        } else {
            if (!Desktop.openPath(f.path)) {
                Dialogs.error(win.window, "Could not open:\n" + f.path.toString());
            }
        }
    }

    static void finishTreeOpen() {
        NavNode n = pendingNav;
        pendingNav = null;
        if (n == null || win == null) {
            return;
        }
        if (n.path != null && n.kind != 0) {
            win.navigateTo(n.path, 1);
        }
    }

    static void showProperties() {
        win.showProperties();
    }

    static void cutSelected() {
        win.cutSelected();
    }

    static void copySelected() {
        win.copySelected();
    }

    static void pasteClipboard() {
        win.pasteClipboard();
    }

    static void renameSelected() {
        win.renameSelected();
    }

    static void deleteSelected() {
        win.deleteSelected();
    }

    static void selectAll() {
        win.selectAll();
    }

    static void toggleHidden() {
        win.toggleHidden();
    }

    static void sortBy(int col) {
        win.sortBy(col);
    }

    static void sortName() {
        win.sortBy(0);
    }

    static void sortModified() {
        win.sortBy(1);
    }

    static void sortSize() {
        win.sortBy(2);
    }

    static void sortType() {
        win.sortBy(3);
    }

    static void breadcrumbGo(String path) {
        Application.invokeLater(() -> {
            ExplorerActions.win.navigatePathString(path);
        });
    }

    static void focusAddress() {
        Application.invokeLater(() -> {
            ExplorerActions.win.focusAddressBar();
        });
    }

    static void syncNavButtons() {
        win.syncNavButtons();
    }

    static void scheduleSearch() {
        win.scheduleSearchDebounce();
    }

    static void about() {
        Dialogs.info(win.window, "JNative Explorer\nNative desktop file manager.\nNo JVM. No Electron.\nExplorer LLVM intrinsics: NONE");
    }

    static void quit() {
        Application.quit();
    }
}

class ExplorerModel {
    FsPath current;
    ArrayList<FileInfo> entries;
    ArrayList<FileInfo> visible;
    int showHidden;
    int navigationGeneration;
    int sortColumn;
    int sortAsc;
    String searchQuery;
    int searchGeneration;
    SelectionModel<FileInfo> selection;
    NavigationHistory history;
    WatchService watcher;
    ClipboardFiles clipboard;
    int loading;

    ExplorerModel() {
        current = SystemPaths.home();
        entries = new ArrayList<FileInfo>();
        visible = new ArrayList<FileInfo>();
        showHidden = 0;
        navigationGeneration = 0;
        sortColumn = 0;
        sortAsc = 1;
        searchQuery = "";
        searchGeneration = 0;
        selection = new SelectionModel<FileInfo>();
        history = new NavigationHistory();
        watcher = null;
        clipboard = new ClipboardFiles();
        loading = 0;
    }
}

class ClipboardFiles {
    ArrayList<FsPath> paths;
    int moveSemantic;

    ClipboardFiles() {
        paths = new ArrayList<FsPath>();
        moveSemantic = 0;
    }

    void set(ArrayList<FsPath> p, int move) {
        paths = p;
        moveSemantic = move;
    }
}

class FileOpProgress {
    int filesTotal;
    int filesCompleted;
    long bytesTotal;
    long bytesCompleted;
    String currentFile;
    int cancelled;
    int done;
    String error;

    FileOpProgress() {
        filesTotal = 0;
        filesCompleted = 0;
        bytesTotal = 0;
        bytesCompleted = 0;
        currentFile = "";
        cancelled = 0;
        done = 0;
        error = "";
    }
}

/* ---------- Window ---------- */

class ExplorerWindow {
    Window window;
    ExplorerModel model;
    BreadcrumbBar breadcrumb;
    SearchField search;
    Button backBtn;
    Button fwdBtn;
    Button upBtn;
    Button refreshBtn;
    TreeView<NavNode> tree;
    TableView<FileInfo> table;
    Label status;
    Label statusSize;
    int watchRunning;
    int searchDebounceGen;

    ExplorerWindow() {
        model = new ExplorerModel();
        watchRunning = 0;
        searchDebounceGen = 0;
        ExplorerActions.win = this;
        window = new Window("JNative Explorer", 1120, 740);
        buildUi();
        installMenus();
        navigateTo(model.current, 1);
        startWatcherLoop();
    }

    void show() {
        window.show();
        if (breadcrumb != null && model != null && model.current != null) {
            breadcrumb.setPath(model.current.toString());
        }
        if (window.content != null) {
            window.content.setBounds(0, 0, window.width, window.height);
            window.content.layout();
        }
    }

    void buildUi() {
        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        Toolbar toolbar = new Toolbar();
        backBtn = toolbar.addToolButton("←", () -> {
        });
        fwdBtn = toolbar.addToolButton("→", () -> {
        });
        upBtn = toolbar.addToolButton("↑", () -> {
        });
        refreshBtn = toolbar.addToolButton("↻", () -> {
        });
        Command backCmd = new Command("nav.back", "←");
        backCmd.setExecute(() -> {
            ExplorerActions.goBack();
        });
        backCmd.bindButton(backBtn);
        Command fwdCmd = new Command("nav.forward", "→");
        fwdCmd.setExecute(() -> {
            ExplorerActions.goForward();
        });
        fwdCmd.bindButton(fwdBtn);
        Command upCmd = new Command("nav.up", "↑");
        upCmd.setExecute(() -> {
            ExplorerActions.goUp();
        });
        upCmd.bindButton(upBtn);
        Command refreshCmd = new Command("nav.refresh", "↻");
        refreshCmd.setExecute(() -> {
            ExplorerActions.refresh();
        });
        refreshCmd.bindButton(refreshBtn);
        toolbar.addSeparatorGap();
        toolbar.addToolButton("＋", () -> {
            ExplorerActions.newFolder();
        });
        Button copyBtn = toolbar.addToolButton("⎘", () -> {
        });
        Button cutBtn = toolbar.addToolButton("✂", () -> {
        });
        Button pasteBtn = toolbar.addToolButton("❐", () -> {
        });
        pasteBtn.setChromeStyle(1);
        Button renameBtn = toolbar.addToolButton("✎", () -> {
        });
        Button deleteBtn = toolbar.addToolButton("⌫", () -> {
        });
        Command copyCmd = new Command("edit.copy", "Copy");
        copyCmd.setShortcut(KeyCombination.of(67).withPrimary());
        copyCmd.setExecute(() -> {
            ExplorerActions.copySelected();
        });
        copyCmd.bindButton(copyBtn);
        Command cutCmd = new Command("edit.cut", "Cut");
        cutCmd.setShortcut(KeyCombination.of(88).withPrimary());
        cutCmd.setExecute(() -> {
            ExplorerActions.cutSelected();
        });
        cutCmd.bindButton(cutBtn);
        Command pasteCmd = new Command("edit.paste", "Paste");
        pasteCmd.setShortcut(KeyCombination.of(86).withPrimary());
        pasteCmd.setExecute(() -> {
            ExplorerActions.pasteClipboard();
        });
        pasteCmd.bindButton(pasteBtn);
        Command renameCmd = new Command("edit.rename", "Rename");
        renameCmd.setExecute(() -> {
            ExplorerActions.renameSelected();
        });
        renameCmd.bindButton(renameBtn);
        Command deleteCmd = new Command("edit.delete", "Delete");
        deleteCmd.setExecute(() -> {
            ExplorerActions.deleteSelected();
        });
        deleteCmd.bindButton(deleteBtn);
        toolbar.addSeparatorGap();
        /* Path sits in the toolbar row (Finder-style) — avoids a second chrome
           strip and the Y-gap that appeared between toolbar and breadcrumb. */
        breadcrumb = new BreadcrumbBar();
        breadcrumb.setOnNavigate(new ExplorerBreadcrumbNav());
        breadcrumb.prefHeight = 26;
        breadcrumb.height = 26;
        breadcrumb.setPrefSize(420, 26);
        toolbar.add(breadcrumb);
        toolbar.addFlexibleSpace();
        search = new SearchField();
        search.setPlaceholder("Search");
        search.setOnQueryChanged(() -> {
            ExplorerActions.scheduleSearch();
        });
        toolbar.add(search);
        Command findCmd = new Command("edit.find", "Find");
        findCmd.setShortcut(KeyCombination.of(70).withPrimary());
        findCmd.setExecute(() -> {
            if (ExplorerActions.win != null && ExplorerActions.win.search != null) {
                ExplorerActions.win.search.focusField();
            }
        });
        CommandRegistry.remember(findCmd);
        Command addrCmd = new Command("nav.address", "Address");
        addrCmd.setShortcut(KeyCombination.of(76).withPrimary());
        addrCmd.setExecute(() -> {
            ExplorerActions.navigateAddress();
        });
        CommandRegistry.remember(addrCmd);

        tree = new TreeView<NavNode>();
        tree.setRenderer(new NavRenderer());
        tree.setModel(new NavTreeModel(buildNavRoot()));
        tree.expand(0);
        tree.expand(1);
        tree.expand(2);
        tree.onSelectionChanged(ev -> {
            ExplorerActions.onTreeSelect();
        });
        tree.onAction(ev -> {
            ExplorerActions.activateTree();
        });

        table = new TableView<FileInfo>();
        table.addColumn(new NameCol());
        table.addColumn(new ModifiedCol());
        table.addColumn(new SizeCol());
        table.addColumn(new TypeCol());
        table.setIconPathProvider(new FileIconPath());
        table.onSelectionChanged(ev -> {
            ExplorerActions.updateStatus();
        });
        table.onAction(ev -> {
            ExplorerActions.openSelected();
        });

        SplitPane split = new SplitPane(Orientation.HORIZONTAL);
        split.setPrefSize(800, 480);
        split.vgrow = 1;
        split.prefHeight = 480;
        split.setDividerPosition(0.22);
        split.setFirst(tree);
        split.setSecond(table);
        tree.setPrefSize(200, 480);
        table.setPrefSize(600, 480);

        HBox statusBar = new HBox();
        statusBar.spacing = Theme.SPACE_3;
        statusBar.padding = Theme.SPACE_2;
        statusBar.prefHeight = Theme.STATUS_HEIGHT;
        statusBar.height = Theme.STATUS_HEIGHT;
        status = new Label("Ready");
        status.setPrefSize(520, 20);
        statusSize = new Label("");
        statusSize.setPrefSize(160, 20);
        statusBar.add(status);
        Label statusFlex = new Label("");
        statusFlex.prefWidth = 0;
        statusFlex.width = 0;
        statusFlex.prefHeight = 1;
        statusFlex.height = 1;
        statusBar.add(statusFlex);
        statusBar.add(statusSize);

        root.add(toolbar);
        root.add(split);
        root.add(statusBar);
        /* Incremental add() may temporarily stretch chrome; reset prefs. */
        toolbar.prefHeight = Theme.TOOLBAR_HEIGHT;
        toolbar.height = Theme.TOOLBAR_HEIGHT;
        statusBar.prefHeight = Theme.STATUS_HEIGHT;
        statusBar.height = Theme.STATUS_HEIGHT;
        split.prefHeight = 480;
        window.setContent(root);

        window.onKeyDown(ev -> {
            ExplorerActions.onWindowKey(ev);
        });
        syncNavButtons();
    }

    void scheduleSearchDebounce() {
        searchDebounceGen = searchDebounceGen + 1;
        int gen = searchDebounceGen;
        ExplorerSearchDebounce.gen = gen;
        Thread t = new Thread(() -> {
            ExplorerSearchDebounce.run();
        });
        t.start();
    }

    NavNode buildNavRoot() {
        NavNode root = new NavNode("Navigation", null, 0);
        int mac = HostPlatform.isMac();
        if (mac != 0) {
            NavNode fav = new NavNode("Favorites", null, 0);
            fav.children.add(new NavNode("Applications", new FsPath("/Applications"), 1));
            fav.children.add(new NavNode("Desktop", SystemPaths.desktop(), 1));
            fav.children.add(new NavNode("Documents", SystemPaths.documents(), 1));
            fav.children.add(new NavNode("Downloads", SystemPaths.downloads(), 1));
            fav.children.add(new NavNode("Home", SystemPaths.home(), 1));
            root.children.add(fav);
            NavNode loc = new NavNode("Locations", null, 0);
            loc.children.add(new NavNode("Computer", new FsPath("/"), 1));
            ArrayList<DriveInfo> drives = FileSystem.drives();
            int i = 0;
            while (i < drives.size()) {
                DriveInfo d = drives.get(i);
                loc.children.add(new NavNode(d.name, d.root, 2));
                i = i + 1;
            }
            root.children.add(loc);
        } else if (HostPlatform.current() == HostPlatform.WINDOWS) {
            NavNode qa = new NavNode("Quick Access", null, 0);
            qa.children.add(new NavNode("Desktop", SystemPaths.desktop(), 1));
            qa.children.add(new NavNode("Documents", SystemPaths.documents(), 1));
            qa.children.add(new NavNode("Downloads", SystemPaths.downloads(), 1));
            qa.children.add(new NavNode("Pictures", SystemPaths.pictures(), 1));
            root.children.add(qa);
            NavNode pc = new NavNode("This PC", null, 0);
            ArrayList<DriveInfo> drives2 = FileSystem.drives();
            int j = 0;
            while (j < drives2.size()) {
                DriveInfo d2 = drives2.get(j);
                pc.children.add(new NavNode(d2.name, d2.root, 2));
                j = j + 1;
            }
            root.children.add(pc);
        } else {
            NavNode fav = new NavNode("Places", null, 0);
            fav.children.add(new NavNode("Home", SystemPaths.home(), 1));
            fav.children.add(new NavNode("Desktop", SystemPaths.desktop(), 1));
            fav.children.add(new NavNode("Documents", SystemPaths.documents(), 1));
            fav.children.add(new NavNode("Downloads", SystemPaths.downloads(), 1));
            root.children.add(fav);
            NavNode loc = new NavNode("Devices", null, 0);
            loc.children.add(new NavNode("File System", new FsPath("/"), 1));
            root.children.add(loc);
        }
        return root;
    }

    void syncNavButtons() {
        if (backBtn != null) {
            if (model.history.canBack() != 0) {
                backBtn.setEnabled(true);
            } else {
                backBtn.setEnabled(false);
            }
        }
        if (fwdBtn != null) {
            if (model.history.canForward() != 0) {
                fwdBtn.setEnabled(true);
            } else {
                fwdBtn.setEnabled(false);
            }
        }
    }

    void focusAddressBar() {
        if (breadcrumb != null && model.current != null) {
            breadcrumb.beginEdit(model.current.toString());
        }
    }

    void navigatePathString(String t) {
        if (t == null || t.length() == 0) {
            return;
        }
        FsPath p = FsPath.of(t).normalize();
        if (!Files.exists(p)) {
            Dialogs.error(window, "Path not found:\n" + t);
            return;
        }
        if (!Files.isDirectory(p)) {
            Desktop.openPath(p);
            return;
        }
        navigateTo(p, 1);
    }

    void installMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("File");
        file.add(new MenuItem("New Folder", () -> {
            ExplorerActions.newFolder();
        }));
        file.add(new MenuItem("Open", () -> {
            ExplorerActions.openSelected();
        }));
        file.addSeparator();
        file.add(new MenuItem("Properties", () -> {
            ExplorerActions.showProperties();
        }));
        file.addSeparator();
        file.add(new MenuItem("Quit", () -> {
            ExplorerActions.quit();
        }));
        Menu edit = new Menu("Edit");
        edit.add(new MenuItem("Cut", () -> {
            ExplorerActions.cutSelected();
        }));
        edit.add(new MenuItem("Copy", () -> {
            ExplorerActions.copySelected();
        }));
        edit.add(new MenuItem("Paste", () -> {
            ExplorerActions.pasteClipboard();
        }));
        edit.add(new MenuItem("Rename", () -> {
            ExplorerActions.renameSelected();
        }));
        edit.add(new MenuItem("Delete", () -> {
            ExplorerActions.deleteSelected();
        }));
        edit.add(new MenuItem("Select All", () -> {
            ExplorerActions.selectAll();
        }));
        Menu view = new Menu("View");
        view.add(new MenuItem("Refresh", () -> {
            ExplorerActions.refresh();
        }));
        view.add(new MenuItem("Show Hidden", () -> {
            ExplorerActions.toggleHidden();
        }));
        view.add(new MenuItem("Sort by Name", () -> {
            ExplorerActions.sortName();
        }));
        view.add(new MenuItem("Sort by Type", () -> {
            ExplorerActions.sortType();
        }));
        view.add(new MenuItem("Sort by Size", () -> {
            ExplorerActions.sortSize();
        }));
        view.add(new MenuItem("Sort by Modified", () -> {
            ExplorerActions.sortModified();
        }));
        Menu help = new Menu("Help");
        help.add(new MenuItem("About JNative Explorer", () -> {
            ExplorerActions.about();
        }));
        bar.add(file);
        bar.add(edit);
        bar.add(view);
        bar.add(help);
        window.setMenuBar(bar);
    }

    void handleKey(KeyEvent ev) {
        if (ev == null) {
            return;
        }
        if (ShortcutManager.dispatch(ev) != 0) {
            return;
        }
        if (ev.keyCode == 13) {
            openSelected();
        } else if (ev.keyCode == 8) {
            goUp();
        } else if (ev.keyCode == 116) {
            refresh(1);
        } else if (ev.keyCode == 46) {
            deleteSelected();
        } else if (ev.keyCode == 113) {
            renameSelected();
        } else if (ev.metaDown != 0 || ev.ctrlDown != 0) {
            if (ev.keyCode == 65 || ev.keyCode == 97) {
                selectAll();
            }
        }
    }

    void navigateAddress() {
        if (breadcrumb != null && breadcrumb.isEditing() != 0) {
            breadcrumb.endEdit(1);
            return;
        }
        focusAddressBar();
    }

    void navigateTo(FsPath path, int recordHistory) {
        if (path == null) {
            return;
        }
        if (!Files.exists(path)) {
            Dialogs.error(window, "Path not found:\n" + path.toString());
            return;
        }
        if (!Files.isDirectory(path)) {
            Dialogs.error(window, "Not a directory:\n" + path.toString());
            return;
        }
        model.navigationGeneration = model.navigationGeneration + 1;
        int gen = model.navigationGeneration;
        model.current = path.normalize();
        model.searchQuery = "";
        if (search != null) {
            search.setQuery("");
        }
        if (breadcrumb != null) {
            breadcrumb.setPath(model.current.toString());
        }
        if (recordHistory != 0) {
            model.history.visit(model.current);
        }
        model.loading = 1;
        status.setText("Loading…");
        syncNavButtons();
        reopenWatcher();
        ExplorerListing.lastTarget = model.current;
        ExplorerListing.lastGen = gen;
        ExplorerListing.lastWindow = this;
        Thread t = new Thread(() -> {
            ExplorerListing.runAsync();
        });
        t.start();
    }

    void applyListing(int gen, ArrayList<FileInfo> listed, String err) {
        if (gen != model.navigationGeneration) {
            return;
        }
        model.loading = 0;
        model.entries = listed;
        if (err != null && err.length() > 0) {
            status.setText(err);
        }
        rebuildVisible();
    }

    void rebuildVisible() {
        ArrayList<FileInfo> v = new ArrayList<FileInfo>();
        int i = 0;
        while (i < model.entries.size()) {
            FileInfo f = model.entries.get(i);
            int keep = 1;
            if (model.showHidden == 0 && f.isHidden != 0) {
                keep = 0;
            }
            if (keep != 0 && model.searchQuery.length() > 0) {
                String q = model.searchQuery;
                if (f.name.indexOf(q) < 0) {
                    keep = 0;
                }
            }
            if (keep != 0) {
                v.add(f);
            }
            i = i + 1;
        }
        ExplorerSort.sort(v, model.sortColumn, model.sortAsc);
        model.visible = v;
        table.setItems(v);
        updateStatus();
    }

    void updateStatus() {
        int n = model.visible.size();
        FileInfo sel = table.getSelectionModel().getSelectedItem();
        if (model.loading != 0) {
            status.setText("Loading…");
            statusSize.setText("");
            return;
        }
        if (sel != null) {
            status.setText("1 item selected");
            if (sel.isDirectory != 0) {
                statusSize.setText("");
            } else {
                statusSize.setText(FileSize.format(sel.size));
            }
            return;
        }
        long sum = 0;
        int i = 0;
        while (i < model.visible.size()) {
            FileInfo f = model.visible.get(i);
            if (f.isDirectory == 0) {
                sum = sum + f.size;
            }
            i = i + 1;
        }
        if (n == 1) {
            status.setText("1 item");
        } else {
            status.setText(("" + n) + " items");
        }
        statusSize.setText(FileSize.format(sum));
    }

    void goBack() {
        FsPath p = model.history.back();
        if (p != null) {
            navigateTo(p, 0);
        }
    }

    void goForward() {
        FsPath p = model.history.forward();
        if (p != null) {
            navigateTo(p, 0);
        }
    }

    void goUp() {
        FsPath parent = model.current.parent();
        if (parent != null && !parent.toString().equals(model.current.toString())) {
            navigateTo(parent, 1);
        }
    }

    void refresh(int force) {
        navigateTo(model.current, 0);
    }

    void sortBy(int col) {
        if (model.sortColumn == col) {
            if (model.sortAsc != 0) {
                model.sortAsc = 0;
            } else {
                model.sortAsc = 1;
            }
        } else {
            model.sortColumn = col;
            model.sortAsc = 1;
        }
        table.sortByColumn(col);
        rebuildVisible();
    }

    void toggleHidden() {
        if (model.showHidden != 0) {
            model.showHidden = 0;
        } else {
            model.showHidden = 1;
        }
        rebuildVisible();
    }

    void openSelected() {
        FileInfo f = table.getSelectionModel().getSelectedItem();
        if (f == null) {
            return;
        }
        /* Defer reload/open off the native double-click stack. */
        ExplorerActions.pendingOpen = f;
        Application.invokeLater(() -> {
            ExplorerActions.finishOpen();
        });
    }

    void selectAll() {
        /* Single-selection TableView foundation — select first as smoke. */
        if (model.visible.size() > 0) {
            table.select(0);
        }
    }

    ArrayList<FsPath> selectedPaths() {
        ArrayList<FsPath> out = new ArrayList<FsPath>();
        FileInfo f = table.getSelectionModel().getSelectedItem();
        if (f != null) {
            out.add(f.path);
        }
        return out;
    }

    void copySelected() {
        ArrayList<FsPath> p = selectedPaths();
        if (p.size() == 0) {
            return;
        }
        model.clipboard.set(p, 0);
        status.setText("Copied " + p.size() + " item(s)");
    }

    void cutSelected() {
        ArrayList<FsPath> p = selectedPaths();
        if (p.size() == 0) {
            return;
        }
        model.clipboard.set(p, 1);
        status.setText("Cut " + p.size() + " item(s)");
    }

    void pasteClipboard() {
        if (model.clipboard.paths.size() == 0) {
            return;
        }
        FsPath destDir = model.current;
        ArrayList<FsPath> srcs = model.clipboard.paths;
        int move = model.clipboard.moveSemantic;
        FileOpProgress prog = new FileOpProgress();
        prog.filesTotal = srcs.size();
        status.setText("Copying...");
        ExplorerFileOps.lastSrcs = srcs;
        ExplorerFileOps.lastDest = destDir;
        ExplorerFileOps.lastMove = move;
        ExplorerFileOps.lastProg = prog;
        ExplorerFileOps.lastWindow = this;
        Thread t = new Thread(() -> {
            ExplorerFileOps.runAsync();
        });
        t.start();
    }

    void newFolder() {
        String name = "New Folder";
        FsPath dest = model.current.resolve(name);
        int n = 1;
        while (Files.exists(dest)) {
            dest = model.current.resolve("New Folder " + n);
            n = n + 1;
        }
        if (!Files.createDirectory(dest)) {
            Dialogs.error(window, "Could not create folder (permission denied?)");
            return;
        }
        refresh(1);
    }

    void renameSelected() {
        FileInfo f = table.getSelectionModel().getSelectedItem();
        if (f == null) {
            return;
        }
        String neu = Dialogs.prompt(window, "Rename to:", f.name + "-renamed");
        if (neu == null || neu.length() == 0 || neu.equals(f.name)) {
            return;
        }
        if (ExplorerFormat.hasInvalidName(neu) != 0) {
            Dialogs.error(window, "Invalid characters in name");
            return;
        }
        FsPath dest = model.current.resolve(neu);
        if (Files.exists(dest)) {
            Dialogs.error(window, "File already exists");
            return;
        }
        if (!Files.move(f.path, dest)) {
            Dialogs.error(window, "Rename failed (permission denied or file in use)");
            return;
        }
        refresh(1);
    }

    void deleteSelected() {
        FileInfo f = table.getSelectionModel().getSelectedItem();
        if (f == null) {
            return;
        }
        if (!Dialogs.confirm(window, "Move to Trash?\n" + f.name)) {
            return;
        }
        if (!Desktop.moveToTrash(f.path)) {
            Dialogs.error(window, "Could not move to Trash");
            return;
        }
        refresh(1);
    }

    void showProperties() {
        FileInfo f = table.getSelectionModel().getSelectedItem();
        if (f == null) {
            f = new FileInfo();
            f.name = model.current.name();
            f.path = model.current;
            f.isDirectory = 1;
            f.type = "Folder";
            f.modifiedTime = Files.lastModified(model.current);
        }
        String msg = f.name + "\n"
                + "Path: " + f.path.toString() + "\n"
                + "Type: " + f.type + "\n"
                + "Size: " + FileSize.format(f.size) + "\n"
                + "Modified: " + ExplorerFormat.time(f.modifiedTime);
        Dialogs.info(window, msg);
    }

    void runSearch() {
        String q = "";
        if (search != null) {
            q = search.getQuery();
        }
        if (q == null) {
            q = "";
        }
        model.searchQuery = q;
        model.searchGeneration = model.searchGeneration + 1;
        int gen = model.searchGeneration;
        if (q.length() == 0) {
            rebuildVisible();
            return;
        }
        status.setText("Searching...");
        ExplorerSearch.lastRoot = model.current;
        ExplorerSearch.lastQuery = q;
        ExplorerSearch.lastGen = gen;
        ExplorerSearch.lastWindow = this;
        Thread t = new Thread(() -> {
            ExplorerSearch.runAsync();
        });
        t.start();
    }

    void reopenWatcher() {
        /* Drop reference first so the background poll loop cannot use a closing handle. */
        WatchService old = model.watcher;
        model.watcher = null;
        if (old != null) {
            old.close();
        }
        model.watcher = WatchService.open(model.current);
    }

    void startWatcherLoop() {
        if (watchRunning != 0) {
            return;
        }
        watchRunning = 1;
        ExplorerWatch.lastWindow = this;
        Thread t = new Thread(() -> {
            ExplorerWatch.runLoop();
        });
        t.start();
    }
}

class ExplorerWatch {
    static ExplorerWindow lastWindow;

    static void runLoop() {
        while (lastWindow != null && lastWindow.watchRunning != 0) {
            WatchService w = lastWindow.model.watcher;
            if (w != null && w.poll()) {
                ExplorerWatch.lastGen = lastWindow.model.navigationGeneration;
                Application.invokeLater(() -> {
                    ExplorerWatch.onChanged();
                });
            }
            Thread.sleep(400);
        }
    }

    static int lastGen;

    static void onChanged() {
        if (lastWindow == null) {
            return;
        }
        if (lastGen == lastWindow.model.navigationGeneration) {
            lastWindow.refresh(0);
        }
    }
}

class ExplorerFormat {
    static String time(long ms) {
        if (ms <= 0) {
            return "";
        }
        /* UTC YYYY-MM-DD HH:MM from epoch millis (no TimeZone API required). */
        long sec = ms / 1000;
        long mins = sec / 60;
        long hours = mins / 60;
        long days = hours / 24;
        long minute = mins - hours * 60;
        long hour = hours - days * 24;
        long y = 1970;
        int moreYears = 1;
        while (moreYears != 0) {
            long diy = 365;
            if (isLeap(y) != 0) {
                diy = 366;
            }
            if (days < diy) {
                moreYears = 0;
            } else {
                days = days - diy;
                y = y + 1;
            }
        }
        long m = 1;
        int moreMonths = 1;
        while (moreMonths != 0 && m <= 12) {
            long dim = daysInMonth(y, m);
            if (days < dim) {
                moreMonths = 0;
            } else {
                days = days - dim;
                m = m + 1;
            }
        }
        long d = days + 1;
        return pad4(y) + "-" + pad2(m) + "-" + pad2(d) + " " + pad2(hour) + ":" + pad2(minute);
    }

    static int isLeap(long y) {
        if ((y % 400) == 0) {
            return 1;
        }
        if ((y % 100) == 0) {
            return 0;
        }
        if ((y % 4) == 0) {
            return 1;
        }
        return 0;
    }

    static long daysInMonth(long y, long m) {
        if (m == 1 || m == 3 || m == 5 || m == 7 || m == 8 || m == 10 || m == 12) {
            return 31;
        }
        if (m == 4 || m == 6 || m == 9 || m == 11) {
            return 30;
        }
        if (isLeap(y) != 0) {
            return 29;
        }
        return 28;
    }

    static String pad2(long n) {
        if (n < 10) {
            return "0" + n;
        }
        return "" + n;
    }

    static String pad4(long n) {
        if (n < 10) {
            return "000" + n;
        }
        if (n < 100) {
            return "00" + n;
        }
        if (n < 1000) {
            return "0" + n;
        }
        return "" + n;
    }

    static int hasInvalidName(String name) {
        int i = 0;
        while (i < name.length()) {
            int c = name.charAt(i);
            if (c == 47 || c == 92 || c == 58 || c == 42 || c == 63 || c == 34 || c == 60 || c == 62 || c == 124) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }
}

class ExplorerSort {
    static void sort(ArrayList<FileInfo> list, int col, int asc) {
        int n = list.size();
        int i = 1;
        while (i < n) {
            FileInfo key = list.get(i);
            int j = i - 1;
            int moving = 1;
            while (j >= 0 && moving != 0) {
                int cmp = compare(key, list.get(j), col);
                int out = 0;
                if (asc != 0 && cmp < 0) {
                    out = 1;
                }
                if (asc == 0 && cmp > 0) {
                    out = 1;
                }
                if (out == 0) {
                    moving = 0;
                } else {
                    list.set(j + 1, list.get(j));
                    j = j - 1;
                }
            }
            list.set(j + 1, key);
            i = i + 1;
        }
    }

    static int compare(FileInfo a, FileInfo b, int col) {
        if (a.isDirectory != b.isDirectory) {
            if (a.isDirectory != 0) {
                return -1;
            }
            return 1;
        }
        /* Columns: 0 Name, 1 Date Modified, 2 Size, 3 Kind */
        if (col == 1) {
            if (a.modifiedTime < b.modifiedTime) {
                return -1;
            }
            if (a.modifiedTime > b.modifiedTime) {
                return 1;
            }
            return 0;
        }
        if (col == 2) {
            if (a.size < b.size) {
                return -1;
            }
            if (a.size > b.size) {
                return 1;
            }
            return 0;
        }
        if (col == 3) {
            return UiStrings.compare(a.type, b.type);
        }
        return UiStrings.compare(a.name, b.name);
    }
}

class ExplorerListing {
    static FsPath lastTarget;
    static int lastGen;
    static ExplorerWindow lastWindow;
    static ArrayList<FileInfo> lastResult;
    static String lastError;

    static void runAsync() {
        lastError = "";
        lastResult = new ArrayList<FileInfo>();
        try {
            lastResult = Files.listInfo(lastTarget);
        } catch (RuntimeException ex) {
            lastError = "Access denied or I/O error";
        }
        Application.invokeLater(() -> {
            ExplorerListing.finish();
        });
    }

    static void finish() {
        if (lastWindow != null) {
            lastWindow.applyListing(lastGen, lastResult, lastError);
        }
    }
}

class ExplorerFileOps {
    static ArrayList<FsPath> lastSrcs;
    static FsPath lastDest;
    static int lastMove;
    static FileOpProgress lastProg;
    static ExplorerWindow lastWindow;

    static void runAsync() {
        runCopyMove(lastSrcs, lastDest, lastMove, lastProg);
        Application.invokeLater(() -> {
            ExplorerFileOps.finish();
        });
    }

    static void finish() {
        if (lastProg.error.length() > 0) {
            Dialogs.error(lastWindow.window, lastProg.error);
        }
        if (lastMove != 0) {
            lastWindow.model.clipboard.paths = new ArrayList<FsPath>();
        }
        lastWindow.refresh(1);
    }

    static void runCopyMove(ArrayList<FsPath> srcs, FsPath destDir, int move, FileOpProgress prog) {
        int i = 0;
        while (i < srcs.size() && prog.cancelled == 0) {
            FsPath src = srcs.get(i);
            FsPath dest = destDir.resolve(src.name());
            prog.currentFile = src.name();
            int success = copyOne(src, dest, move);
            if (success == 0) {
                prog.error = "Failed: " + src.toString();
            }
            prog.filesCompleted = prog.filesCompleted + 1;
            i = i + 1;
        }
        prog.done = 1;
    }

    static int copyOne(FsPath src, FsPath dest, int move) {
        if (move != 0) {
            if (Files.move(src, dest)) {
                return 1;
            }
            if (Files.copy(src, dest)) {
                Files.delete(src);
                return 1;
            }
            return 0;
        }
        if (Files.copy(src, dest)) {
            return 1;
        }
        return 0;
    }
}

class ExplorerSearch {
    static FsPath lastRoot;
    static String lastQuery;
    static int lastGen;
    static ExplorerWindow lastWindow;
    static ArrayList<FileInfo> lastHits;

    static void runAsync() {
        lastHits = new ArrayList<FileInfo>();
        scan(lastRoot, lastQuery, lastHits, 0, 500);
        Application.invokeLater(() -> {
            ExplorerSearch.finish();
        });
    }

    static void finish() {
        if (lastWindow == null) {
            return;
        }
        if (lastGen != lastWindow.model.searchGeneration) {
            return;
        }
        lastWindow.model.entries = lastHits;
        lastWindow.model.visible = lastHits;
        lastWindow.table.setItems(lastHits);
        lastWindow.status.setText(("" + lastHits.size()) + " search results");
    }

    static void scan(FsPath dir, String query, ArrayList<FileInfo> out, int depth, int limit) {
        if (depth > 8 || out.size() >= limit) {
            return;
        }
        ArrayList<FileInfo> kids = Files.listInfo(dir);
        int i = 0;
        while (i < kids.size() && out.size() < limit) {
            FileInfo f = kids.get(i);
            if (f.name.indexOf(query) >= 0) {
                out.add(f);
            }
            if (f.isDirectory != 0) {
                scan(f.path, query, out, depth + 1, limit);
            }
            i = i + 1;
        }
    }
}
