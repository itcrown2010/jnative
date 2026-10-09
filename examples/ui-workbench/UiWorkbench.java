import jnative.ui.*;

/**
 * Minimal IDE-shaped layout proving Desktop UI foundation is reusable.
 * @author 张晓达
 */
public class UiWorkbench {
    public static void main(String[] args) {
        Application app = new Application();
        WorkbenchWindow w = new WorkbenchWindow();
        w.show();
        app.run();
    }
}

class WorkbenchNav implements BreadcrumbNavigate {
    void go(String path) {
        WorkbenchActions.onCrumb(path);
    }
}

class WorkbenchActions {
    static WorkbenchWindow win;

    static void onCrumb(String path) {
        if (win != null) {
            win.status.setText("Navigate: " + path);
        }
    }

    static void cmdNew() {
        if (win != null) {
            win.status.setText("Command: new.file");
        }
    }

    static void cmdFind() {
        if (win != null) {
            win.search.focusField();
        }
    }

    static void onSearch() {
        if (win != null) {
            win.status.setText("Search: " + win.search.getQuery());
        }
    }

    static void onKey(KeyEvent ev) {
        ShortcutManager.dispatch(ev);
    }
}

class WorkbenchWindow {
    Window window;
    Label status;
    SearchField search;
    TreeView<String> tree;
    TableView<String> table;
    VirtualListMetrics virt;

    WorkbenchWindow() {
        WorkbenchActions.win = this;
        window = new Window("JNative UI Workbench", 960, 640);
        virt = new VirtualListMetrics();
        virt.setDataSize(1000000);
        virt.setViewportHeight(400);
        build();
    }

    void show() {
        window.show();
    }

    void build() {
        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        Command newCmd = new Command("new.file", "+");
        newCmd.setExecute(() -> {
            WorkbenchActions.cmdNew();
        });
        newCmd.setShortcut(KeyCombination.of(78).withPrimary());

        Command findCmd = new Command("edit.find", "F");
        findCmd.setExecute(() -> {
            WorkbenchActions.cmdFind();
        });
        findCmd.setShortcut(KeyCombination.of(70).withPrimary());

        Toolbar tb = new Toolbar();
        Button nb = tb.addToolButton("+", () -> {
        });
        newCmd.bindButton(nb);
        Button fb = tb.addToolButton("F", () -> {
        });
        findCmd.bindButton(fb);
        tb.addFlexibleSpace();
        search = new SearchField();
        search.setPlaceholder("Search");
        search.setOnQueryChanged(() -> {
            WorkbenchActions.onSearch();
        });
        tb.add(search);

        BreadcrumbBar crumbs = new BreadcrumbBar();
        crumbs.setOnNavigate(new WorkbenchNav());
        crumbs.setPath("/Project/src/main");

        tree = new TreeView<String>();
        table = new TableView<String>();
        SplitPane split = new SplitPane(Orientation.HORIZONTAL);
        split.setDividerPosition(0.22);
        split.setFirst(tree);
        split.setSecond(table);
        split.setPrefSize(940, 520);
        split.vgrow = 1;

        status = new Label("Ready pool=" + virt.poolSize
                + " data=" + virt.dataSize
                + " scale=" + Dpi.scalePercentForId(window.nativeId)
                + " dark=" + Theme.isDarkEffective());
        status.setPrefSize(900, 22);

        root.add(tb);
        root.add(crumbs);
        root.add(split);
        root.add(status);
        window.setContent(root);
        window.onKeyDown(ev -> {
            WorkbenchActions.onKey(ev);
        });
    }
}
