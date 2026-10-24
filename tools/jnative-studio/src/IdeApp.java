import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;
import jnative.io.*;

/**
 * Welcome page shown while no document is open: project actions and recent projects.
 *
 * @author 张晓达
 */
class IdeWelcome extends Canvas {
    IdeApp app;
    int hover = -1;

    IdeWelcome(IdeApp a) {
        app = a;
        setHiDpi(true);
        onPaint(g -> paintWelcome(g));
        onMouseMove(e -> {
            int h = hit(e.y, e.x);
            if (h != hover) {
                hover = h;
                repaint();
            }
        });
        onMouseDown(e -> {
            int h = hit(e.y, e.x);
            if (h >= 0) {
                app.welcomeAction(h);
            }
        });
    }

    int leftX() {
        return Math.max(40, (width - 520) / 2);
    }

    int hit(int y, int x) {
        int lx = leftX();
        if (x < lx || x > lx + 520) {
            return -1;
        }
        int base = 190;
        if (y >= base && y < base + 30) {
            return 0;
        }
        if (y >= base + 34 && y < base + 64) {
            return 1;
        }
        int ry = base + 140;
        int i = 0;
        while (i < IdeSettings.recent.size()) {
            if (y >= ry + i * 28 && y < ry + i * 28 + 26) {
                return 10 + i;
            }
            i = i + 1;
        }
        return -1;
    }

    void paintWelcome(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        int lx = leftX();
        Ide.rfill(g, Ide.ACCENT, lx, 56, 56, 56, 14);
        Ide.text(g, "J", lx + 19, 62, Font.systemBold(38), 0xFFFFFF);
        Ide.text(g, "JNative Studio", lx + 72, 56, Font.systemBold(28), Ide.TEXT);
        Ide.text(g, "用 JNative 快速创建、设计、开发和运行原生应用", lx + 74, 94, Ide.ui(), Ide.SUBTLE);
        Ide.text(g, "开始", lx, 150, Ide.bold(), Ide.SUBTLE);
        paintLink(g, "新建项目…", "⌘N", lx, 190, hover == 0);
        paintLink(g, "打开项目…", "⌘O", lx, 224, hover == 1);
        Ide.text(g, "最近使用", lx, 280, Ide.bold(), Ide.SUBTLE);
        int ry = 330;
        int i = 0;
        if (IdeSettings.recent.size() == 0) {
            Ide.text(g, "还没有最近打开的项目", lx, ry, Ide.ui(), Ide.FAINT);
        }
        while (i < IdeSettings.recent.size()) {
            String p = IdeSettings.recent.get(i);
            if (hover == 10 + i) {
                Ide.rfill(g, Ide.HOVER, lx - 8, ry + i * 28, 536, 26, 5);
            }
            Ide.icon(g, "project", lx, ry + i * 28 + 5, 16);
            Ide.text(g, Ide.baseName(p), lx + 26, ry + i * 28 + 5, Ide.bold(), Ide.TEXT);
            Ide.text(g, Ide.ellipsize(g, p, Ide.small(), 300), lx + 180, ry + i * 28 + 6, Ide.small(), Ide.FAINT);
            i = i + 1;
        }
        if (!IdeSettings.hasCompiler()) {
            Ide.text(g, "未找到 JNative 编译器：请在 设置 中指定 jnative.jar（或设置环境变量 JNATIVE_COMPILER）", lx, height - 40, Ide.ui(), Ide.WARN);
        }
    }

    void paintLink(Graphics g, String text, String key, int x, int y, boolean hot) {
        if (hot) {
            Ide.rfill(g, Ide.HOVER, x - 8, y - 6, 536, 30, 5);
        }
        Ide.text(g, text, x, y, Font.system(14), Ide.ACCENT);
        Ide.text(g, key, x + 480, y + 1, Ide.small(), Ide.FAINT);
    }
}

/**
 * Splitter handle (thin transparent canvas on top of the layout).
 */
class IdeSplitter extends Canvas {
    void dragTo(MouseEvent e) {
        if (dragging && onDrag != null) {
            int pos = vertical ? Ide.windowX(this) + e.x : Ide.windowY(this) + e.y;
            onDrag.handle(pos);
        }
    }

    boolean vertical;
    IdeIntHandler onDrag;
    boolean dragging = false;

    IdeSplitter(boolean v) {
        vertical = v;
        setTransparent(true);
        onPaint(g -> {
            g.setColor(new Color(0, 0, 0, 0));
        });
        onMouseMove(e -> {
            Gfx.cursor(vertical ? 2 : 4);
        });
        onMouseExit(e -> {
            if (!dragging) {
                Gfx.cursor(0);
            }
        });
        onMouseDown(e -> {
            dragging = true;
        });
        onMouseDrag(e -> dragTo(e));
        onMouseUp(e -> {
            dragging = false;
            Gfx.cursor(0);
        });
    }
}

/**
 * JNative Studio main window: explorer, document tabs (form designer / code editor), build output,
 * problems, run and navigation between designer and code.
 *
 * @author 张晓达
 */
public class IdeApp extends Panel {
    static int TB_H = 46;
    static int ST_H = 26;
    static int TABS_H = 34;
    static int HEAD_H = 30;

    Window window;
    IdeProject project;
    int explorerW = 270;
    int bottomH = 240;
    boolean bottomVisible = false;
    int bottomTab = 0;

    Canvas chrome;
    Panel toolbar;
    IdeTree explorer;
    IdeTabs tabs;
    Panel host;
    IdeWelcome welcome;
    Canvas bottomHead;
    IdeTree output;
    IdeTree problems;
    Canvas status;
    IdeSplitter splitV;
    IdeSplitter splitH;

    Button btnNew;
    Button btnOpen;
    Button btnSave;
    Button btnBuild;
    Button btnRun;
    Button btnStop;
    Button btnSwitch;
    Button btnSettings;

    IdeProc buildProc;
    IdeProc runProc;
    boolean runAfterBuild = false;
    int diagCount = 0;
    int warnCount = 0;
    ArrayList<IdeDiag> diags = new ArrayList<IdeDiag>();
    String statusText = "就绪";
    String caretText = "";

    IdeApp(Window w) {
        window = w;
        IdeSettings.load();
        chrome = new Canvas();
        chrome.setHiDpi(true);
        chrome.onPaint(g -> paintChrome(g));
        add(chrome);
        toolbar = new Panel();
        add(toolbar);
        btnNew = tb("新建项目", () -> newProject());
        btnOpen = tb("打开项目", () -> openProjectDialog());
        btnSave = tb("保存", () -> saveActive());
        btnBuild = tb("生成", () -> build(false));
        btnRun = tb("▶ 运行", () -> build(true));
        btnStop = tb("■ 停止", () -> stopAll());
        btnSwitch = tb("设计器 ⇄ 代码", () -> toggleDesignerCode());
        btnSettings = tb("设置", () -> IdeDialog.settings(window, () -> {
            welcome.repaint();
            status.repaint();
        }));

        explorer = new IdeTree();
        explorer.emptyText = "未打开项目";
        explorer.onActivate = it -> explorerActivate(it);
        explorer.onContext = it -> explorerContext(it);
        add(explorer);

        tabs = new IdeTabs();
        tabs.onSelect = i -> showDoc(i);
        tabs.onClose = i -> closeDoc(i);
        add(tabs);

        host = new Panel();
        host.setClipChildren(true, false);
        add(host);
        welcome = new IdeWelcome(this);
        add(welcome);

        bottomHead = new Canvas();
        bottomHead.setHiDpi(true);
        bottomHead.onPaint(g -> paintBottomHead(g));
        bottomHead.onMouseDown(e -> bottomHeadDown(e));
        add(bottomHead);
        output = new IdeTree();
        output.flat = true;
        output.rowH = 20;
        output.customFont = Font.mono(12);
        output.emptyText = "";
        add(output);
        problems = new IdeTree();
        problems.flat = true;
        problems.rowH = 24;
        problems.emptyText = "没有问题";
        problems.onActivate = it -> problemActivate(it);
        add(problems);
        vis(problems, false);

        status = new Canvas();
        status.setHiDpi(true);
        status.onPaint(g -> paintStatus(g));
        add(status);

        splitV = new IdeSplitter(true);
        splitV.onDrag = x -> {
            explorerW = Math.max(160, Math.min(520, x));
            layout();
        };
        add(splitV);
        splitH = new IdeSplitter(false);
        splitH.onDrag = y -> {
            int by = height - ST_H - y;
            bottomH = Math.max(90, Math.min(height - 260, by));
            layout();
        };
        add(splitH);

        buildMenus();
        refreshState();
    }

    Button tb(String text, Runnable r) {
        Button b = new Button(text);
        b.onClick(r);
        toolbar.add(b);
        return b;
    }

    MenuItem item(Menu m, String text, int key, int mods, Runnable r) {
        MenuItem mi = new MenuItem(text, r);
        if (key > 0) {
            KeyCombination kc = KeyCombination.of(key).withPrimary();
            if (mods == 1) {
                kc = kc.withShift();
            } else if (mods == 2) {
                kc = kc.withAlt();
            }
            mi.setShortcut(kc);
        }
        m.add(mi);
        return mi;
    }

    void buildMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        item(file, "新建项目…", 78, 0, () -> newProject());
        item(file, "新建窗体…", 78, 1, () -> newForm(null));
        item(file, "打开项目…", 79, 0, () -> openProjectDialog());
        file.addSeparator();
        item(file, "保存", 83, 0, () -> saveActive());
        item(file, "全部保存", 83, 2, () -> saveAll());
        item(file, "关闭标签页", 87, 0, () -> closeActive());
        bar.add(file);
        Menu edit = new Menu("编辑");
        item(edit, "撤销", 90, 0, () -> designerUndo());
        item(edit, "重做", 90, 1, () -> designerRedo());
        edit.addSeparator();
        item(edit, "查找…", 70, 0, () -> findDialog());
        item(edit, "查找下一个", 71, 0, () -> findAgain());
        item(edit, "转到行…", 76, 0, () -> gotoLineDialog());
        edit.addSeparator();
        item(edit, "代码补全", 75, 0, () -> completeDialog());
        bar.add(edit);
        Menu view = new Menu("视图");
        item(view, "设计器 / 代码 切换", 69, 0, () -> toggleDesignerCode());
        item(view, "实时预览", 80, 0, () -> designerPreview());
        item(view, "显示/隐藏输出面板", 74, 0, () -> toggleBottom());
        bar.add(view);
        Menu bld = new Menu("生成");
        item(bld, "生成项目", 66, 0, () -> build(false));
        item(bld, "运行", 82, 0, () -> build(true));
        item(bld, "停止", 190, 0, () -> stopAll());
        bar.add(bld);
        Menu help = new Menu("帮助");
        help.add(new MenuItem("设置…", () -> IdeDialog.settings(window, () -> welcome.repaint())));
        bar.add(help);
        window.setMenuBar(bar);
    }

    // ---------------------------------------------------------------- layout

    public void layout() {
        super.layout();
        if (chrome == null || splitH == null) {
            return;
        }
        chrome.setBounds(0, 0, width, height);
        toolbar.setBounds(0, 0, width, TB_H);
        int x = 12;
        x = place(btnNew, x, 84);
        x = place(btnOpen, x, 84);
        x = place(btnSave, x, 60);
        x = x + 14;
        x = place(btnBuild, x, 64);
        x = place(btnRun, x, 84);
        x = place(btnStop, x, 84);
        x = x + 14;
        x = place(btnSwitch, x, 120);
        x = place(btnSettings, x, 60);
        int top = TB_H;
        int bodyH = height - TB_H - ST_H;
        explorer.setBounds(0, top + HEAD_H, explorerW, bodyH - HEAD_H);
        int cx = explorerW + 1;
        int cw = width - cx;
        int centerH = bodyH;
        int bh = bottomVisible ? bottomH : 0;
        centerH = bodyH - bh;
        tabs.setBounds(cx, top, cw, TABS_H);
        host.setBounds(cx, top + TABS_H, cw, centerH - TABS_H);
        welcome.setBounds(cx, top + TABS_H, cw, centerH - TABS_H);
        int i = 0;
        while (i < tabs.docs.size()) {
            Component v = tabs.docs.get(i).view();
            if (v != null) {
                v.setBounds(0, 0, cw, centerH - TABS_H);
            }
            i = i + 1;
        }
        vis(bottomHead, bottomVisible);
        vis(output, bottomVisible && bottomTab == 0);
        vis(problems, bottomVisible && bottomTab == 1);
        if (bottomVisible) {
            int by = top + centerH;
            bottomHead.setBounds(cx, by, cw, HEAD_H);
            output.setBounds(cx, by + HEAD_H, cw, bh - HEAD_H);
            problems.setBounds(cx, by + HEAD_H, cw, bh - HEAD_H);
        }
        status.setBounds(0, height - ST_H, width, ST_H);
        splitV.setBounds(explorerW - 2, top, 5, bodyH);
        vis(splitH, bottomVisible);
        if (bottomVisible) {
            splitH.setBounds(cx, top + centerH - 2, cw, 5);
        }
        chrome.repaint();
    }

    /** Visibility without Component.setVisible's re-layout of the whole window (we are inside layout). */
    static void vis(Component c, boolean v) {
        c.visible = v ? 1 : 0;
        UiNative.setVisible(c.nativeId, c.visible);
    }

    int place(Button b, int x, int w) {
        b.setBounds(x, 8, w, 30);
        return x + w + 6;
    }

    void paintChrome(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        Ide.fill(g, Ide.TOOLBAR, 0, 0, width, TB_H);
        Ide.line(g, Ide.BORDER, 0, TB_H - 0.5, width, TB_H - 0.5);
        Ide.fill(g, Ide.PANEL_ALT, 0, TB_H, explorerW, HEAD_H);
        Ide.textRow(g, "资源管理器", 14, TB_H, HEAD_H, Ide.bold(), Ide.SUBTLE);
        if (project != null) {
            Ide.textRow(g, project.name, 14 + 80, TB_H, HEAD_H, Ide.small(), Ide.FAINT);
        }
        Ide.line(g, Ide.BORDER, explorerW + 0.5, TB_H, explorerW + 0.5, height - ST_H);
        Ide.line(g, Ide.BORDER, 0, TB_H + HEAD_H - 0.5, explorerW, TB_H + HEAD_H - 0.5);
        if (project != null) {
            String t = project.name + "  ·  " + project.mainClass;
            int tw = Ide.textWidth(g, t, Ide.small());
            Ide.text(g, t, width - tw - 16, 15, Ide.small(), Ide.SUBTLE);
        }
    }

    void paintBottomHead(Graphics g) {
        g.setAntialias(true);
        int width = bottomHead.width;
        int height = bottomHead.height;
        Ide.fill(g, Ide.PANEL_ALT, 0, 0, width, height);
        Ide.line(g, Ide.BORDER, 0, 0.5, width, 0.5);
        Ide.line(g, Ide.BORDER, 0, height - 0.5, width, height - 0.5);
        String t0 = "输出";
        String t1 = "问题" + (diagCount + warnCount > 0 ? " (" + (diagCount + warnCount) + ")" : "");
        int x = 12;
        int w0 = Ide.textWidth(g, t0, Ide.bold()) + 20;
        int w1 = Ide.textWidth(g, t1, Ide.bold()) + 20;
        if (bottomTab == 0) {
            Ide.fill(g, Ide.ACCENT, x, height - 2, w0, 2);
        } else {
            Ide.fill(g, Ide.ACCENT, x + w0, height - 2, w1, 2);
        }
        Ide.textRow(g, t0, x + 10, 0, height, Ide.bold(), bottomTab == 0 ? Ide.TEXT : Ide.SUBTLE);
        Ide.textRow(g, t1, x + w0 + 10, 0, height, Ide.bold(), bottomTab == 1 ? (diagCount > 0 ? Ide.ERROR : Ide.TEXT) : Ide.SUBTLE);
        Ide.text(g, "✕", width - 22, 7, Ide.ui(), Ide.SUBTLE);
        Ide.text(g, "清除", width - 70, 8, Ide.small(), Ide.SUBTLE);
    }

    void bottomHeadDown(MouseEvent e) {
        if (e.x > bottomHead.width - 32) {
            toggleBottom();
            return;
        }
        if (e.x > bottomHead.width - 80) {
            output.rows.clear();
            output.root = new IdeItem("", "", "");
            output.rebuild();
            return;
        }
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        int w0 = Ide.textWidth(g, "输出", Ide.bold()) + 20;
        g.end();
        tmp.dispose();
        if (e.x < 12 + w0) {
            showBottomTab(0);
        } else {
            showBottomTab(1);
        }
    }

    void paintStatus(Graphics g) {
        g.setAntialias(true);
        int width = status.width;
        int height = status.height;
        boolean busy = buildProc != null && buildProc.running() || runProc != null && runProc.running();
        Ide.fill(g, busy ? Ide.ACCENT : 0x2B2F36, 0, 0, width, height);
        Ide.textRow(g, statusText, 12, 0, height, Ide.small(), 0xFFFFFF);
        if (caretText.length() > 0) {
            int tw = Ide.textWidth(g, caretText, Ide.small());
            Ide.textRow(g, caretText, width - tw - 14, 0, height, Ide.small(), 0xFFFFFF);
        }
        if (!IdeSettings.hasCompiler()) {
            String w = "⚠ 未找到编译器";
            int tw = Ide.textWidth(g, w, Ide.small());
            Ide.textRow(g, w, width - tw - 150, 0, height, Ide.small(), 0xFFD27A);
        }
    }

    void setStatus(String s) {
        statusText = s;
        status.repaint();
    }

    void toggleBottom() {
        bottomVisible = !bottomVisible;
        layout();
    }

    void showBottomTab(int t) {
        bottomTab = t;
        if (!bottomVisible) {
            bottomVisible = true;
        }
        layout();
        bottomHead.repaint();
    }

    // ---------------------------------------------------------------- project

    void welcomeAction(int a) {
        if (a == 0) {
            newProject();
        } else if (a == 1) {
            openProjectDialog();
        } else if (a >= 10 && a - 10 < IdeSettings.recent.size()) {
            openProject(IdeSettings.recent.get(a - 10));
        }
    }

    void newProject() {
        String parent = Env.get("HOME");
        if (parent == null || parent.length() == 0) {
            parent = "/tmp";
        }
        IdeDialog.newProject(window, parent + "/JNativeProjects", (loc, name) -> createProject(loc, name));
    }

    void createProject(String loc, String name) {
        if (!DsUtil.isIdent(name)) {
            Dialogs.error(window, "项目名称只能包含字母、数字和下划线，且不能以数字开头。");
            return;
        }
        Files.createDirectories(FsPath.of(loc));
        IdeProject p = IdeProject.create(loc, name);
        if (p == null) {
            Dialogs.error(window, "无法创建项目：目录已存在 " + loc + "/" + name);
            return;
        }
        openProject(p.dir);
        openFile(p.dir + "/src/MainForm.jnui");
    }

    void openProjectDialog() {
        String d = FileDialogEx.show(window, 2, "", "", "", 0);
        if (d == null || d.length() == 0) {
            return;
        }
        openProject(d);
    }

    void openProject(String dir) {
        IdeProject p = IdeProject.open(dir);
        if (p == null) {
            Dialogs.error(window, "所选文件夹不是 JNative 项目（缺少 jnative.project）：\n" + dir);
            return;
        }
        if (!closeAllDocs()) {
            return;
        }
        project = p;
        IdeSettings.addRecent(dir);
        refreshExplorer();
        diags.clear();
        diagCount = 0;
        warnCount = 0;
        refreshProblems();
        output.root = new IdeItem("", "", "");
        output.rebuild();
        window.setTitle("JNative Studio - " + p.name);
        setStatus("已打开项目 " + p.name);
        String mainForm = findFirstForm(p.srcRoot());
        refreshState();
        layout();
        if (mainForm.length() > 0) {
            openFile(mainForm);
        }
    }

    String findFirstForm(String dir) {
        ArrayList<String> names = Files.list(FsPath.of(dir));
        int i = 0;
        while (i < names.size()) {
            if (names.get(i).endsWith(".jnui")) {
                return dir + "/" + names.get(i);
            }
            i = i + 1;
        }
        return "";
    }

    void refreshExplorer() {
        if (project == null) {
            explorer.setRoot(new IdeItem("", "", ""));
            return;
        }
        ArrayList<String> open = new ArrayList<String>();
        collectExpanded(explorer.root, open);
        IdeItem r = project.buildTree();
        restoreExpanded(r, open);
        explorer.setRoot(r);
    }

    void collectExpanded(IdeItem n, ArrayList<String> out) {
        int i = 0;
        while (i < n.kids.size()) {
            IdeItem c = n.kids.get(i);
            if (c.expanded) {
                out.add(c.key);
            }
            collectExpanded(c, out);
            i = i + 1;
        }
    }

    void restoreExpanded(IdeItem n, ArrayList<String> open) {
        int i = 0;
        while (i < n.kids.size()) {
            IdeItem c = n.kids.get(i);
            if (open.size() > 0) {
                c.expanded = open.indexOf(c.key) >= 0;
            }
            restoreExpanded(c, open);
            i = i + 1;
        }
    }

    void refreshState() {
        boolean has = project != null;
        boolean running = runProc != null && runProc.running();
        boolean building = buildProc != null && buildProc.running();
        btnBuild.setEnabled(has && !building);
        btnRun.setEnabled(has && !building && !running);
        btnStop.setEnabled(running || building);
        btnSave.setEnabled(tabs != null && tabs.active >= 0);
        btnSwitch.setEnabled(tabs != null && tabs.active >= 0);
        status.repaint();
        chrome.repaint();
    }

    // ---------------------------------------------------------------- explorer

    void explorerActivate(IdeItem it) {
        if (it.icon.equals("folder") || it.icon.equals("project")) {
            return;
        }
        openFile(it.key);
    }

    String dirOf(IdeItem it) {
        if (it == null) {
            return project != null ? project.srcRoot() : "";
        }
        if (it.icon.equals("folder") || it.icon.equals("project")) {
            return it.key;
        }
        return Ide.dirName(it.key);
    }

    void explorerContext(IdeItem it) {
        if (project == null) {
            return;
        }
        ContextMenu cm = new ContextMenu();
        cm.add(new MenuItem("新建窗体…", () -> newForm(it)));
        cm.add(new MenuItem("新建 Java 类…", () -> newClass(it)));
        cm.addSeparator();
        if (!it.icon.equals("project")) {
            cm.add(new MenuItem("删除…", () -> deleteItem(it)));
        }
        cm.add(new MenuItem("在 Finder 中显示", () -> Desktop.revealFile(it.key)));
        cm.add(new MenuItem("刷新", () -> refreshExplorer()));
        cm.showAt(UiNative.cursorScreenX(), UiNative.cursorScreenY());
    }

    void newForm(IdeItem at) {
        if (project == null) {
            Dialogs.info(window, "请先打开或新建一个项目。");
            return;
        }
        String dir = dirOf(at);
        IdeDialog.input(window, "新建窗体", "窗体类名（例如 LoginForm）", "Form1", n -> createForm(dir, n));
    }

    void createForm(String dir, String n) {
        String name = DsUtil.capitalize(n);
        if (!DsUtil.isIdent(name) || DsUtil.isKeyword(name)) {
            Dialogs.error(window, "类名无效：" + name);
            return;
        }
        if (Files.exists(FsPath.of(dir + "/" + name + ".jnui")) || Files.exists(FsPath.of(dir + "/" + name + ".java"))) {
            Dialogs.error(window, "已存在同名文件：" + name);
            return;
        }
        DsDoc d = DsDoc.blank(name);
        d.root.set("title", name);
        d.dirty = false;
        IdeProject.writeForm(dir, d, true);
        refreshExplorer();
        openFile(dir + "/" + name + ".jnui");
        setStatus("已创建窗体 " + name);
    }

    void newClass(IdeItem at) {
        if (project == null) {
            return;
        }
        String dir = dirOf(at);
        IdeDialog.input(window, "新建 Java 类", "类名", "MyClass", n -> createClass(dir, n));
    }

    void createClass(String dir, String n) {
        String name = DsUtil.capitalize(n);
        if (!DsUtil.isIdent(name) || DsUtil.isKeyword(name)) {
            Dialogs.error(window, "类名无效：" + name);
            return;
        }
        String path = dir + "/" + name + ".java";
        if (Files.exists(FsPath.of(path))) {
            Dialogs.error(window, "已存在同名文件：" + name + ".java");
            return;
        }
        Files.writeString(FsPath.of(path), "public class " + name + " {\n    " + name + "() {\n    }\n}\n");
        refreshExplorer();
        openFile(path);
    }

    void deleteItem(IdeItem it) {
        if (Files.isDirectory(FsPath.of(it.key))) {
            Dialogs.info(window, "暂不支持删除文件夹，请在 Finder 中操作。");
            return;
        }
        if (!Dialogs.confirm(window, "确定删除 " + it.text + " ？此操作不可撤销。")) {
            return;
        }
        int idx = tabs.find(it.key);
        if (idx >= 0) {
            forceClose(idx);
        }
        Files.delete(FsPath.of(it.key));
        refreshExplorer();
    }

    // ---------------------------------------------------------------- documents

    IdeDoc activeDoc() {
        if (tabs.active < 0 || tabs.active >= tabs.docs.size()) {
            return null;
        }
        return tabs.docs.get(tabs.active);
    }

    void openFile(String path) {
        int idx = tabs.find(path);
        if (idx >= 0) {
            showDoc(idx);
            return;
        }
        if (!Files.exists(FsPath.of(path))) {
            Dialogs.error(window, "文件不存在：" + path);
            return;
        }
        String text = Files.readString(FsPath.of(path));
        IdeDoc d = new IdeDoc();
        d.path = path;
        d.title = Ide.baseName(path);
        if (path.endsWith(".jnui")) {
            String cls = d.title.substring(0, d.title.length() - 5);
            DsDoc ds = DsDoc.fromFileText(text, cls);
            if (ds == null) {
                Dialogs.error(window, "无法解析设计文件：" + path);
                return;
            }
            ds.path = path;
            ds.clearHistory();
            d.kind = 1;
            d.ds = ds;
            DsDesigner dz = new DsDesigner(ds);
            dz.onChanged = () -> docChanged();
            dz.onGoto = (doc, n, ev) -> gotoCode(doc, n, ev);
            d.designer = dz;
        } else {
            d.kind = 0;
            CodeEditor ed = new CodeEditor();
            ed.setHiDpi(true);
            ed.font = Font.mono(13);
            ed.setTheme(EditorTheme.light());
            ed.setText(text);
            ed.document.markSaved();
            ed.onCaretChanged(pos -> caretMoved(pos));
            d.editor = ed;
        }
        tabs.docs.add(d);
        host.add(d.view());
        d.view().setBounds(0, 0, host.width, host.height);
        showDoc(tabs.docs.size() - 1);
        applyDiagnostics();
    }

    void docChanged() {
        tabs.repaint();
        refreshState();
    }

    void caretMoved(TextPosition p) {
        caretText = "行 " + (p.line + 1) + "，列 " + (p.column + 1);
        status.repaint();
        tabs.repaint();
    }

    void showDoc(int idx) {
        tabs.active = idx;
        int i = 0;
        while (i < tabs.docs.size()) {
            Component v = tabs.docs.get(i).view();
            vis(v, i == idx);
            i = i + 1;
        }
        vis(welcome, tabs.docs.size() == 0);
        IdeDoc d = activeDoc();
        if (d != null) {
            d.view().setBounds(0, 0, host.width, host.height);
            if (d.kind == 0) {
                d.editor.requestFocus();
                caretMoved(d.editor.document.offsetToPosition(d.editor.caret.offset));
            } else {
                caretText = "";
                d.designer.refreshStatus();
            }
            if (project != null) {
                IdeItem it = explorer.findByKey(d.path);
                if (it != null) {
                    explorer.selected = it;
                    explorer.expandTo(it);
                    explorer.reveal(it);
                    explorer.repaint();
                }
            }
        } else {
            caretText = "";
        }
        tabs.repaint();
        refreshState();
    }

    void closeActive() {
        if (tabs.active >= 0) {
            closeDoc(tabs.active);
        }
    }

    boolean confirmDoc(IdeDoc d) {
        if (!d.isDirty()) {
            return true;
        }
        int r = Dialogs.askSave(window, "是否保存对 " + d.title + " 的更改？");
        if (r == 0) {
            return false;
        }
        if (r == 1) {
            saveDoc(d);
        }
        return true;
    }

    void closeDoc(int idx) {
        IdeDoc d = tabs.docs.get(idx);
        if (!confirmDoc(d)) {
            return;
        }
        forceClose(idx);
    }

    void forceClose(int idx) {
        IdeDoc d = tabs.docs.get(idx);
        Component v = d.view();
        if (d.designer != null) {
            d.designer.dispose();
        }
        tabs.docs.remove(idx);
        host.remove(v);
        v.dispose();
        int na = tabs.docs.size() == 0 ? -1 : Math.min(idx, tabs.docs.size() - 1);
        showDoc(na);
    }

    boolean closeAllDocs() {
        int i = 0;
        while (i < tabs.docs.size()) {
            if (!confirmDoc(tabs.docs.get(i))) {
                return false;
            }
            i = i + 1;
        }
        while (tabs.docs.size() > 0) {
            forceClose(tabs.docs.size() - 1);
        }
        return true;
    }

    void saveActive() {
        IdeDoc d = activeDoc();
        if (d != null) {
            saveDoc(d);
            setStatus("已保存 " + d.title);
        }
    }

    void saveAll() {
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc d = tabs.docs.get(i);
            if (d.isDirty()) {
                saveDoc(d);
            }
            i = i + 1;
        }
        setStatus("已全部保存");
    }

    void saveDoc(IdeDoc d) {
        if (d.kind == 0) {
            Files.writeString(FsPath.of(d.path), d.editor.getText());
            d.editor.document.markSaved();
        } else {
            saveDesigner(d);
        }
        tabs.repaint();
    }

    void saveDesigner(IdeDoc d) {
        DsDoc ds = d.ds;
        String dir = Ide.dirName(d.path);
        ArrayList<String> problemsList = ds.validate();
        int i = 0;
        while (i < problemsList.size()) {
            appendOutput("[设计器] " + problemsList.get(i), Ide.WARN);
            i = i + 1;
        }
        Files.writeString(FsPath.of(d.path), ds.toFileText());
        writeSynced(dir + "/" + ds.className + "Base.java", DsGen.generateBase(ds));
        FsPath user = FsPath.of(dir + "/" + ds.className + ".java");
        if (!Files.exists(user)) {
            Files.writeString(user, DsGen.userStub(ds));
            refreshExplorer();
        }
        ds.dirty = false;
    }

    /** Writes a generated file and refreshes an open editor tab showing it. */
    void writeSynced(String path, String text) {
        boolean existed = Files.exists(FsPath.of(path));
        Files.writeString(FsPath.of(path), text);
        int idx = tabs.find(path);
        if (idx >= 0) {
            IdeDoc d = tabs.docs.get(idx);
            if (d.kind == 0 && !d.editor.getText().equals(text)) {
                d.editor.setText(text);
                d.editor.document.markSaved();
            }
        }
        if (!existed) {
            refreshExplorer();
        }
    }

    // ---------------------------------------------------------------- designer <-> code

    void designerUndo() {
        IdeDoc d = activeDoc();
        if (d != null && d.kind == 1) {
            d.designer.undo();
        } else if (d != null && d.kind == 0) {
            d.editor.executeCommand(EditorCommand.UNDO);
        }
    }

    void designerRedo() {
        IdeDoc d = activeDoc();
        if (d != null && d.kind == 1) {
            d.designer.redo();
        } else if (d != null && d.kind == 0) {
            d.editor.executeCommand(EditorCommand.REDO);
        }
    }

    String lastFind = "";

    /** Code completion popup (⌘K): candidates for the caret, filtered by a live prefix box. */
    void completeDialog() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        CodeEditor e = d.editor;
        String[] pre = new String[1];
        IdeComplete.projectDirs.clear();
        if (project != null) {
            int k = 0;
            while (k < project.srcDirs.size()) {
                IdeComplete.projectDirs.add(project.dir + "/" + project.srcDirs.get(k));
                k = k + 1;
            }
        }
        ArrayList<IdeItem> all = IdeComplete.candidates(e.getText(), e.caret.offset, pre);
        if (all.size() == 0) {
            setStatus("没有可用的补全");
            return;
        }
        IdeCompletePopup.show(window, all, pre[0], e);
    }

    void findDialog() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        IdeDialog.input(window, "查找", "在当前文件中查找：", lastFind, q -> {
            lastFind = q;
            IdeDoc cur = activeDoc();
            if (cur != null && cur.kind == 0) {
                cur.editor.findNext(q);
                cur.editor.requestFocus();
            }
        });
    }

    void findAgain() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        if (lastFind.length() == 0) {
            findDialog();
            return;
        }
        d.editor.findNext(lastFind);
    }

    void gotoLineDialog() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        IdeDialog.input(window, "转到行", "行号（1 - " + d.editor.document.getLineCount() + "）：", "", t -> {
            IdeDoc cur = activeDoc();
            if (cur == null || cur.kind != 0) {
                return;
            }
            int n = DsUtil.parseInt(t, -1);
            if (n >= 1) {
                cur.editor.goTo(Math.min(n, cur.editor.document.getLineCount()) - 1, 0);
                cur.editor.requestFocus();
            }
        });
    }

    void designerPreview() {
        IdeDoc d = activeDoc();
        if (d != null && d.kind == 1) {
            d.designer.togglePreview();
        }
    }

    void toggleDesignerCode() {
        IdeDoc d = activeDoc();
        if (d == null) {
            return;
        }
        String dir = Ide.dirName(d.path);
        String name = d.title;
        if (d.kind == 1) {
            gotoCode(d.ds, null, null);
            return;
        }
        if (name.endsWith("Base.java")) {
            name = name.substring(0, name.length() - 9);
        } else if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }
        String jnui = dir + "/" + name + ".jnui";
        if (Files.exists(FsPath.of(jnui))) {
            openFile(jnui);
        } else {
            setStatus("当前文件没有对应的设计器（" + name + ".jnui）");
        }
    }

    void gotoCode(DsDoc ds, DsNode n, DsEvent ev) {
        int di = findDesignerTab(ds);
        if (di >= 0) {
            saveDesigner(tabs.docs.get(di));
            tabs.repaint();
        }
        String dir = Ide.dirName(ds.path);
        String userPath = dir + "/" + ds.className + ".java";
        if (!Files.exists(FsPath.of(userPath))) {
            Files.writeString(FsPath.of(userPath), DsGen.userStub(ds));
            refreshExplorer();
        }
        openFile(userPath);
        int idx = tabs.find(userPath);
        if (idx < 0) {
            return;
        }
        IdeDoc cd = tabs.docs.get(idx);
        if (cd.kind != 0) {
            return;
        }
        if (ev != null && n != null) {
            String h = n.handler(ev.name);
            String src = cd.editor.getText();
            if (!DsGen.hasMethod(src, h)) {
                cd.editor.setText(DsGen.insertMethod(src, DsGen.handlerStub(h, ev)));
                saveDoc(cd);
            }
            int line = DsGen.lineOfMethod(cd.editor.getText(), h);
            cd.editor.goTo(line, 8);
            setStatus("已转到 " + h);
        }
        cd.editor.requestFocus();
    }

    int findDesignerTab(DsDoc ds) {
        int i = 0;
        while (i < tabs.docs.size()) {
            if (tabs.docs.get(i).ds == ds) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    // ---------------------------------------------------------------- build & run

    void appendOutput(String text, int color) {
        boolean stick = output.atEnd();
        IdeItem it = new IdeItem(text, "", "");
        it.color = color;
        output.root.add(it);
        output.rebuild();
        if (stick) {
            output.scrollToEnd();
        }
    }

    void build(boolean run) {
        if (project == null) {
            Dialogs.info(window, "请先打开或新建一个项目。");
            return;
        }
        if (!IdeSettings.hasCompiler()) {
            IdeDialog.settings(window, () -> {
                status.repaint();
                welcome.repaint();
            });
            return;
        }
        if (buildProc != null && buildProc.running()) {
            return;
        }
        saveAll();
        output.root = new IdeItem("", "", "");
        output.rebuild();
        diags.clear();
        diagCount = 0;
        warnCount = 0;
        refreshProblems();
        applyDiagnostics();
        showBottomTab(0);
        appendOutput("—— 生成 " + project.name + " ——", Ide.ACCENT);
        runAfterBuild = run;
        IdeProc p = new IdeProc();
        p.onLine = ln -> buildLine(ln);
        p.onExit = code -> buildDone(code);
        buildProc = p;
        if (!p.start(project.dir, IdeSettings.buildCommand())) {
            appendOutput("无法启动编译器进程", Ide.ERROR);
            buildProc = null;
            return;
        }
        setStatus("正在生成…");
        refreshState();
    }

    void buildLine(String ln) {
        IdeDiag d = IdeDiag.parse(ln);
        if (d != null) {
            if (!hasDiag(d)) {
                diags.add(d);
                if (d.severity == 1) {
                    diagCount = diagCount + 1;
                } else {
                    warnCount = warnCount + 1;
                }
                refreshProblems();
            }
            appendOutput(ln, d.severity == 1 ? Ide.ERROR : Ide.WARN);
            return;
        }
        appendOutput(ln, Ide.TEXT);
    }

    boolean hasDiag(IdeDiag d) {
        int i = 0;
        while (i < diags.size()) {
            IdeDiag o = diags.get(i);
            if (o.line == d.line && o.col == d.col && o.path.equals(d.path) && o.message.equals(d.message)) {
                return true;
            }
            i = i + 1;
        }
        return false;
    }

    void buildDone(int code) {
        boolean stoppedByUser = buildProc != null && buildProc.stopped;
        buildProc = null;
        if (stoppedByUser) {
            appendOutput("—— 生成已取消 ——", Ide.WARN);
            setStatus("生成已取消");
        } else if (code == 0 && diagCount == 0) {
            appendOutput("—— 生成成功 ——", Ide.OK);
            setStatus("生成成功");
            if (runAfterBuild) {
                runProgram();
            }
        } else {
            appendOutput("—— 生成失败（" + diagCount + " 个错误）——", Ide.ERROR);
            setStatus("生成失败：" + diagCount + " 个错误");
            if (diagCount > 0) {
                showBottomTab(1);
            }
        }
        applyDiagnostics();
        refreshProblems();
        refreshState();
    }

    void runProgram() {
        String exe = project.outputPath();
        if (!Files.exists(FsPath.of(exe))) {
            appendOutput("找不到可执行文件：" + exe, Ide.ERROR);
            return;
        }
        appendOutput("—— 运行 " + project.name + " ——", Ide.ACCENT);
        IdeProc p = new IdeProc();
        p.onLine = ln -> appendOutput(ln, Ide.TEXT);
        p.onExit = code -> runDone(code);
        runProc = p;
        if (!p.start(project.dir, "JNATIVE_LINEBUF=1 exec '" + exe + "' 2>&1")) {
            appendOutput("无法启动程序", Ide.ERROR);
            runProc = null;
            return;
        }
        setStatus("正在运行 " + project.name);
        refreshState();
    }

    void runDone(int code) {
        boolean byUser = runProc != null && runProc.stopped;
        runProc = null;
        if (byUser) {
            appendOutput("—— 程序已停止 ——", Ide.WARN);
        } else {
            appendOutput("—— 程序已退出（代码 " + code + "）——", code == 0 ? Ide.OK : Ide.ERROR);
        }
        setStatus("就绪");
        refreshState();
    }

    void stopAll() {
        if (buildProc != null) {
            buildProc.stop();
        }
        if (runProc != null) {
            runProc.stop();
        }
    }

    // ---------------------------------------------------------------- problems

    void refreshProblems() {
        IdeItem r = new IdeItem("", "", "");
        int i = 0;
        while (i < diags.size()) {
            IdeDiag d = diags.get(i);
            String where = Ide.baseName(d.path) + ":" + d.line;
            IdeItem it = new IdeItem(d.message, d.severity == 1 ? "error" : "warning", "" + i);
            it.hint = where;
            r.add(it);
            i = i + 1;
        }
        problems.setRoot(r);
        bottomHead.repaint();
    }

    void problemActivate(IdeItem it) {
        int i = DsUtil.parseInt(it.key, -1);
        if (i < 0 || i >= diags.size()) {
            return;
        }
        IdeDiag d = diags.get(i);
        openFile(d.path);
        int idx = tabs.find(d.path);
        if (idx >= 0 && tabs.docs.get(idx).kind == 0) {
            CodeEditor ed = tabs.docs.get(idx).editor;
            ed.goTo(d.line - 1, d.col - 1);
            ed.requestFocus();
        }
    }

    void applyDiagnostics() {
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc doc = tabs.docs.get(i);
            if (doc.kind == 0) {
                ArrayList<EditorDiagnostic> list = new ArrayList<EditorDiagnostic>();
                int j = 0;
                while (j < diags.size()) {
                    IdeDiag d = diags.get(j);
                    if (d.path.equals(doc.path)) {
                        list.add(EditorDiagnostic.fromCompiler(doc.editor.document, d.line, d.col, d.severity, d.message));
                    }
                    j = j + 1;
                }
                doc.editor.setDiagnostics(list);
            }
            i = i + 1;
        }
    }

    /** Called when the window is asked to close: returns false to veto. */
    boolean confirmExit() {
        int i = 0;
        while (i < tabs.docs.size()) {
            if (!confirmDoc(tabs.docs.get(i))) {
                return false;
            }
            i = i + 1;
        }
        stopAll();
        return true;
    }
}
