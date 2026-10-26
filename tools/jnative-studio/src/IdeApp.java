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
    IdeFindBar findBar;
    IdeSuggest suggest;
    IdeHint hint;
    IdeLsp lsp = new IdeLsp();
    int lspRestarts = 0;
    IdeQuick quick;
    IdeWelcome welcome;
    Canvas bottomHead;
    IdeTree output;
    IdeTree problems;
    IdeTree search;
    IdeTree debugTree;
    IdeDebug dbg;
    ArrayList<String> bpPaths = new ArrayList<String>();
    ArrayList<Integer> bpLines = new ArrayList<Integer>();
    boolean debugAfterBuild = false;
    IdeProc checkProc;
    UiTimer checkTimer;
    boolean checkPending = false;
    long lastEditMs = 0;
    ArrayList<IdeDiag> checkDiags = new ArrayList<IdeDiag>();
    int checkSeq = 0;
    int tickCount = 0;
    boolean skipAutoOpen = false;
    boolean restoring = false;
    ArrayList<int> bhEdges = new ArrayList<int>();
    String searchQuery = "";
    Canvas status;
    IdeSplitter splitV;
    IdeSplitter splitH;

    Button btnNew;
    Button btnOpen;
    Button btnSave;
    Button btnBuild;
    Button btnRun;
    Button btnDebug;
    Button btnCont;
    Button btnStep;
    Button btnInto;
    Button btnOut;
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
        btnDebug = tb("调试", () -> debug());
        btnCont = tb("继续", () -> dbg.resume());
        btnStep = tb("逐行", () -> dbg.stepOver());
        btnInto = tb("步入", () -> dbg.stepIn());
        btnOut = tb("步出", () -> dbg.stepOut());
        btnStop = tb("■ 停止", () -> stopAll());
        dbg = new IdeDebug(this);
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
        findBar = new IdeFindBar(this);
        suggest = new IdeSuggest(this);
        host.add(suggest);
        hint = new IdeHint(this);
        host.add(hint);
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
        search = new IdeTree();
        search.flat = true;
        search.rowH = 22;
        search.emptyText = "在「编辑 → 在项目中查找」中搜索（⇧⌘F）";
        search.onActivate = it -> searchActivate(it);
        add(search);
        vis(search, false);
        debugTree = new IdeTree();
        debugTree.rowH = 22;
        debugTree.emptyText = "点击行号左侧设断点，再按「调试」";
        debugTree.onActivate = it -> debugActivate(it);
        debugTree.onExpand = it -> dbg.expand(it);
        add(debugTree);
        vis(debugTree, false);

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

        quick = new IdeQuick(this);
        add(quick);
        checkTimer = new UiTimer(500, () -> checkTick());
        window.onKeyDown(e -> windowKey(e));
        Application.setQuitGuard(() -> confirmQuit());
        buildMenus();
        refreshState();
    }

    /** Escape from a native text field (the find bar) arrives here, not at the editor. */
    void windowKey(KeyEvent e) {
        if (e.keyCode == KeyCode.ESCAPE) {
            if (quick.isOpen) {
                quick.close();
            } else if (findBar.shown) {
                findBar.close();
            } else if (hint.isOpen) {
                hint.hide();
            } else {
                suggest.hide();
            }
        }
        if (e.metaDown == 0 && e.ctrlDown == 0 && e.altDown == 0 && e.keyCode == 116) {
            debug();
        }
    }

    /** Key pre-filter of every code editor: completion list first, then Esc closes the find bar. */
    void editorKey(KeyEvent e) {
        if (e.keyCode == KeyCode.ESCAPE && !suggest.isOpen && hint.isOpen) {
            e.consume();
            hint.hide();
            return;
        }
        if (e.keyCode == KeyCode.ESCAPE && !suggest.isOpen && findBar.shown) {
            e.consume();
            findBar.close();
            return;
        }
        if (e.metaDown == 0 && e.ctrlDown == 0 && e.altDown == 0) {
            if (e.keyCode == 116) {
                e.consume();
                debug();
                return;
            }
            if (e.keyCode == 120) {
                e.consume();
                toggleBpAtCaret();
                return;
            }
            if (e.keyCode == 121) {
                e.consume();
                dbg.stepOver();
                return;
            }
            if (e.keyCode == 122) {
                e.consume();
                dbg.stepIn();
                return;
            }
            if (e.keyCode == 119) {
                e.consume();
                dbg.stepOut();
                return;
            }
        }
        if (e.altDown != 0 && e.metaDown == 0 && e.ctrlDown == 0 && e.keyCode == 118) {
            e.consume();
            findUsages();
            return;
        }
        if (e.altDown != 0 && e.metaDown == 0 && e.ctrlDown == 0 && e.keyCode == KeyCode.ENTER) {
            e.consume();
            quickFix();
            return;
        }
        if (e.ctrlDown != 0 && e.metaDown == 0 && e.altDown == 0) {
            if (e.keyCode == 80) {
                e.consume();
                paramInfo();
                return;
            }
            if (e.keyCode == 81) {
                e.consume();
                quickDoc();
                return;
            }
        }
        suggest.keyPre(e);
    }

    // ---------------------------------------------------------------- language service

    /** Starts (or restarts, when the project changed or the process died) the language service. */
    boolean ensureLsp() {
        if (project == null) {
            return false;
        }
        if (lsp.running() && lsp.root.equals(project.dir)) {
            return true;
        }
        if (lsp.dead && lsp.root.equals(project.dir) && lspRestarts >= 3) {
            return false;
        }
        if (lsp.dead && lsp.root.equals(project.dir)) {
            lspRestarts = lspRestarts + 1;
        } else {
            lspRestarts = 0;
        }
        return lsp.start(project.dir);
    }

    /** ⌃P: parameter info for the call around the caret. */
    void paramInfo() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        hint.triggerParams(d.editor, d.path);
    }

    /** ⌃Q: quick documentation (signature, return type, owner) of the symbol at the caret. */
    void quickDoc() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        suggest.hide();
        hint.quickDoc(d.editor, d.path);
    }

    void typedHook(int code) {
        suggest.typed(code);
        hintTyped(code);
    }

    // ---------------------------------------------------------------- mouse hover documentation

    CodeEditor hoverEd;
    int hoverWA = -1;
    int hoverWB = -1;
    int hoverOff = 0;
    long hoverMs = 0;
    boolean hoverAsked = false;
    UiTimer hoverTimer;

    /** Tracks the identifier under the pointer; the documentation popup follows after a short rest. */
    void hoverMoved(CodeEditor ed, MouseEvent e) {
        if (project == null || ed.mouseDownFlag != 0) {
            return;
        }
        int off = ed.hitTest(e.x, e.y);
        TextPosition p = ed.document.offsetToPosition(off);
        String line = ed.document.getLine(p.line);
        int col = p.column;
        int a = -1;
        int b = -1;
        if (col < line.length() && IdeSuggest.identChar(line.charAt(col))) {
            a = col;
            b = col;
            while (a > 0 && IdeSuggest.identChar(line.charAt(a - 1))) {
                a = a - 1;
            }
            while (b < line.length() && IdeSuggest.identChar(line.charAt(b))) {
                b = b + 1;
            }
        }
        int wa = a < 0 ? -1 : off - col + a;
        int wb = a < 0 ? -1 : off - col + b;
        if (hint.isOpen && hint.hovering && (hoverEd != ed || wa != hoverWA)) {
            hint.hide();
        }
        if (hoverEd == ed && wa == hoverWA && wa >= 0) {
            return;
        }
        hoverEd = ed;
        hoverWA = wa;
        hoverWB = wb;
        hoverOff = off;
        hoverMs = System.currentTimeMillis();
        hoverAsked = wa < 0;
        if (hoverTimer == null) {
            hoverTimer = new UiTimer(150, () -> hoverTick());
        }
    }

    void hoverTick() {
        if (hoverAsked || hoverEd == null || hoverWA < 0) {
            return;
        }
        if (System.currentTimeMillis() - hoverMs < 450) {
            return;
        }
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0 || d.editor != hoverEd || d.path.length() == 0
                || suggest.isOpen || quick.isOpen || (hint.isOpen && !hint.hovering)) {
            return;
        }
        if (!(d.path.endsWith(".java") || d.path.endsWith(".jn"))) {
            return;
        }
        hoverAsked = true;
        String w = d.editor.document.substring(hoverWA, hoverWB);
        if (DsUtil.isKeyword(w) || w.charAt(0) >= '0' && w.charAt(0) <= '9') {
            return;
        }
        hint.hoverDoc(d.editor, d.path, hoverOff, hoverWA, hoverWB);
    }

    /** ⌥F7: lists every use of the symbol under the caret (compiler-resolved, across the project). */
    void findUsages() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0 || d.path.length() == 0) {
            setStatus("请先打开一个代码文件");
            return;
        }
        if (!ensureLsp()) {
            setStatus("语言服务未启动，无法查找用法");
            return;
        }
        CodeEditor ed = d.editor;
        lsp.sync(d.path, ed.getText());
        TextPosition p = ed.document.offsetToPosition(ed.caret.offset);
        int col = p.column;
        String line = ed.document.getLine(p.line);
        if ((col >= line.length() || !IdeSuggest.identChar(line.charAt(col))) && col > 0) {
            col = col - 1;
        }
        setStatus("正在查找用法…");
        lsp.requestAt("textDocument/references", d.path, p.line, col, ",\"context\":{\"includeDeclaration\":true}",
                r -> gotUsages(r));
    }

    void gotUsages(JsonValue r) {
        if (r == null || !r.isArray() || r.asArray().size() == 0) {
            setStatus("没有找到用法");
            return;
        }
        ArrayList<IdeItem> items = new ArrayList<IdeItem>();
        JsonArray arr = r.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject loc = arr.get(i).asObject();
            String uri = loc.getString("uri", "");
            String path = uri.startsWith("file://") ? uri.substring(7) : uri;
            path = path.replace("%20", " ");
            JsonObject st = loc.get("range").asObject().get("start").asObject();
            int ln = st.getInt("line", 0);
            int cl = st.getInt("character", 0);
            String text = "";
            int open = tabs.find(path);
            if (open >= 0 && tabs.docs.get(open).kind == 0) {
                text = tabs.docs.get(open).editor.document.getLine(ln);
            } else if (Files.exists(FsPath.of(path))) {
                ArrayList<String> ls = DsGen.splitLines(Files.readString(FsPath.of(path)));
                if (ln < ls.size()) {
                    text = ls.get(ln);
                }
            }
            IdeItem it = new IdeItem(Ide.baseName(path) + ":" + (ln + 1) + "   " + text.trim(), "cm", path);
            it.tag = ln;
            it.edits.add("" + cl);
            items.add(it);
            i = i + 1;
        }
        setStatus("找到 " + items.size() + " 处用法");
        quick.open("查找用法（" + items.size() + "）", items, it -> {
            navRecord();
            openFile(it.key);
            IdeDoc cur = activeDoc();
            if (cur != null && cur.kind == 0) {
                cur.editor.goTo(it.tag, DsUtil.parseInt(it.edits.get(0), 0));
                cur.editor.requestFocus();
            }
        });
    }

    /** ⌥Enter: quick fixes for the problems at the caret line (remove / rewrite / suppress / add import). */
    void quickFix() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0 || d.path.length() == 0) {
            return;
        }
        if (!ensureLsp()) {
            setStatus("语言服务未启动，无法提供快速修复");
            return;
        }
        CodeEditor ed = d.editor;
        lsp.sync(d.path, ed.getText());
        TextPosition p = ed.document.offsetToPosition(ed.caret.offset);
        int line = p.line;
        String path = d.path;
        String range = ",\"range\":{\"start\":{\"line\":" + line + ",\"character\":" + p.column
                + "},\"end\":{\"line\":" + line + ",\"character\":" + p.column + "}},\"context\":{\"diagnostics\":[]}";
        lsp.requestAt("textDocument/codeAction", path, line, p.column, range, r -> gotQuickFixes(d, r));
    }

    void gotQuickFixes(IdeDoc d, JsonValue r) {
        if (r == null || !r.isArray() || r.asArray().size() == 0) {
            setStatus("此处没有可用的快速修复");
            return;
        }
        ArrayList<IdeItem> items = new ArrayList<IdeItem>();
        JsonArray arr = r.asArray();
        int i = 0;
        while (i < arr.size()) {
            JsonObject a = arr.get(i).asObject();
            IdeItem it = new IdeItem(a.getString("title", ""), "cm", "");
            JsonValue ev = a.get("edit");
            if (ev != null && ev.isObject()) {
                JsonValue cv = ev.asObject().get("changes");
                if (cv != null && cv.isObject() && cv.asObject().keys.size() > 0) {
                    JsonValue list = cv.asObject().get(cv.asObject().keys.get(0));
                    if (list != null && list.isArray()) {
                        int k = 0;
                        while (k < list.asArray().size()) {
                            JsonObject te = list.asArray().get(k).asObject();
                            JsonObject rg = te.get("range").asObject();
                            JsonObject st = rg.get("start").asObject();
                            JsonObject en = rg.get("end").asObject();
                            it.edits.add("" + st.getInt("line", 0));
                            it.edits.add("" + st.getInt("character", 0));
                            it.edits.add("" + en.getInt("line", 0));
                            it.edits.add("" + en.getInt("character", 0));
                            it.edits.add(te.getString("newText", ""));
                            k = k + 1;
                        }
                    }
                }
            }
            if (it.edits.size() > 0) {
                items.add(it);
            }
            i = i + 1;
        }
        if (items.size() == 0) {
            setStatus("此处没有可用的快速修复");
            return;
        }
        quick.open("快速修复（Enter 应用）", items, it -> applyQuickFix(d, it));
    }

    void applyQuickFix(IdeDoc d, IdeItem it) {
        CodeEditor ed = d.editor;
        int n = it.edits.size() / 5;
        ArrayList<Integer> starts = new ArrayList<Integer>();
        ArrayList<Integer> lens = new ArrayList<Integer>();
        ArrayList<String> news = new ArrayList<String>();
        int k = 0;
        while (k < n) {
            int a = ed.document.positionToOffset(DsUtil.parseInt(it.edits.get(k * 5), 0), DsUtil.parseInt(it.edits.get(k * 5 + 1), 0));
            int b = ed.document.positionToOffset(DsUtil.parseInt(it.edits.get(k * 5 + 2), 0), DsUtil.parseInt(it.edits.get(k * 5 + 3), 0));
            starts.add(a);
            lens.add(b - a);
            news.add(it.edits.get(k * 5 + 4));
            k = k + 1;
        }
        ed.undo.closeGroup();
        int done = 0;
        while (done < n) {
            int best = -1;
            int j = 0;
            while (j < n) {
                if (starts.get(j) >= 0 && (best < 0 || starts.get(j) > starts.get(best))) {
                    best = j;
                }
                j = j + 1;
            }
            ed.applyEdit(starts.get(best), lens.get(best), news.get(best));
            starts.set(best, -1);
            done = done + 1;
        }
        ed.undo.closeGroup();
        markEdited();
        setStatus("已应用：" + it.text);
    }

    /** After a typed character: "(" and "," open parameter info; while it is open it follows the caret. */
    void hintTyped(int code) {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0 || d.path.length() == 0 || !(d.path.endsWith(".java") || d.path.endsWith(".jn"))) {
            return;
        }
        CodeEditor ed = d.editor;
        if (code == 40 || code == 44) {
            if (ed.inStringOrComment(ed.caret.offset) == 0) {
                hint.triggerParams(ed, d.path);
            }
        } else if (hint.isOpen && hint.mode == 0) {
            hint.triggerParams(ed, d.path);
        }
    }

    void focusEditor() {
        IdeDoc d = activeDoc();
        if (d != null && d.kind == 0) {
            d.editor.requestFocus();
        }
    }

    int findBarHeight() {
        return findBar.shown ? findBar.height() : 0;
    }

    /** Positions the find bar and gives the code editors the space below it. */
    void layoutDocs() {
        int fh = findBarHeight();
        findBar.layoutBar(host.width);
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc d = tabs.docs.get(i);
            Component v = d.view();
            if (v != null) {
                if (d.kind == 0) {
                    v.setBounds(0, fh, host.width, Math.max(0, host.height - fh));
                } else {
                    v.setBounds(0, 0, host.width, host.height);
                }
            }
            i = i + 1;
        }
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
        item(edit, "替换…", 70, 2, () -> findBar.open(true));
        item(edit, "查找下一个", 71, 0, () -> findAgain());
        item(edit, "查找上一个", 71, 1, () -> findPrevious());
        edit.addSeparator();
        item(edit, "在项目中查找…", 70, 1, () -> findInFiles());
        item(edit, "重新缩进代码", 73, 1, () -> formatDocument());
        item(edit, "重命名符号…", 82, 1, () -> renameSymbol());
        item(edit, "转到行…", 76, 0, () -> gotoLineDialog());
        item(edit, "转到文件…", 79, 1, () -> quickOpenFile());
        item(edit, "转到符号…", 84, 0, () -> quickSymbol());
        edit.addSeparator();
        item(edit, "代码补全", 75, 0, () -> completeDialog());
        item(edit, "转到定义", 68, 0, () -> gotoDefinition());
        item(edit, "查找用法（⌥F7）", 0, 0, () -> findUsages());
        item(edit, "快速修复（⌥Enter）", 0, 0, () -> quickFix());
        item(edit, "参数信息（⌃P）", 0, 0, () -> paramInfo());
        item(edit, "快速文档（⌃Q）", 0, 0, () -> quickDoc());
        item(edit, "后退", 37, 2, () -> navBack());
        item(edit, "前进", 39, 2, () -> navForward());
        bar.add(edit);
        Menu view = new Menu("视图");
        item(view, "设计器 / 代码 切换", 69, 0, () -> toggleDesignerCode());
        item(view, "实时预览", 80, 0, () -> designerPreview());
        item(view, "显示/隐藏输出面板", 74, 0, () -> toggleBottom());
        item(view, "开关实时错误检查", 69, 1, () -> toggleLiveCheck());
        view.addSeparator();
        item(view, "放大编辑器字号", 187, 0, () -> zoomEditors(1));
        item(view, "缩小编辑器字号", 189, 0, () -> zoomEditors(-1));
        item(view, "重置编辑器字号", 48, 0, () -> zoomEditors(0));
        view.addSeparator();
        item(view, "下一个标签页", 221, 2, () -> cycleTab(1));
        item(view, "上一个标签页", 219, 2, () -> cycleTab(-1));
        bar.add(view);
        Menu bld = new Menu("生成");
        item(bld, "生成项目", 66, 0, () -> build(false));
        item(bld, "运行", 82, 0, () -> build(true));
        item(bld, "停止", 190, 0, () -> stopAll());
        bar.add(bld);
        Menu debugMenu = new Menu("调试");
        item(debugMenu, "开始调试（F5）", 0, 0, () -> debug());
        item(debugMenu, "重新生成并调试", 0, 0, () -> debugRebuild());
        item(debugMenu, "继续", 0, 0, () -> dbg.resume());
        item(debugMenu, "单步跳过（F10）", 0, 0, () -> dbg.stepOver());
        item(debugMenu, "单步进入（F11）", 0, 0, () -> dbg.stepIn());
        item(debugMenu, "单步跳出（F8）", 0, 0, () -> dbg.stepOut());
        item(debugMenu, "暂停", 0, 0, () -> dbg.pause());
        item(debugMenu, "停止调试", 0, 0, () -> dbg.stop());
        debugMenu.addSeparator();
        item(debugMenu, "切换断点（F9）", 0, 0, () -> toggleBpAtCaret());
        bar.add(debugMenu);
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
        x = place(btnRun, x, 64);
        x = place(btnDebug, x, 48);
        x = place(btnCont, x, 48);
        x = place(btnStep, x, 48);
        x = place(btnInto, x, 48);
        x = place(btnOut, x, 48);
        x = place(btnStop, x, 56);
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
        layoutDocs();
        vis(bottomHead, bottomVisible);
        vis(output, bottomVisible && bottomTab == 0);
        vis(problems, bottomVisible && bottomTab == 1);
        vis(search, bottomVisible && bottomTab == 2);
        vis(debugTree, bottomVisible && bottomTab == 3);
        if (bottomVisible) {
            int by = top + centerH;
            bottomHead.setBounds(cx, by, cw, HEAD_H);
            output.setBounds(cx, by + HEAD_H, cw, bh - HEAD_H);
            problems.setBounds(cx, by + HEAD_H, cw, bh - HEAD_H);
            search.setBounds(cx, by + HEAD_H, cw, bh - HEAD_H);
            debugTree.setBounds(cx, by + HEAD_H, cw, bh - HEAD_H);
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
        String[] titles = new String[4];
        titles[0] = "输出";
        titles[1] = "问题" + (diagCount + warnCount > 0 ? " (" + (diagCount + warnCount) + ")" : "");
        titles[2] = "搜索" + (search.root.kids.size() > 0 ? " (" + search.root.kids.size() + ")" : "");
        titles[3] = "调试" + (dbg != null && dbg.active ? (dbg.stopped ? " (暂停)" : " (运行)") : "");
        bhEdges.clear();
        int x = 12;
        int t = 0;
        while (t < 4) {
            int w = Ide.textWidth(g, titles[t], Ide.bold()) + 20;
            bhEdges.add(x);
            if (bottomTab == t) {
                Ide.fill(g, Ide.ACCENT, x, height - 2, w, 2);
            }
            int col = bottomTab == t ? Ide.TEXT : Ide.SUBTLE;
            if (t == 1 && bottomTab == 1 && diagCount > 0) {
                col = Ide.ERROR;
            }
            Ide.textRow(g, titles[t], x + 10, 0, height, Ide.bold(), col);
            x = x + w;
            t = t + 1;
        }
        bhEdges.add(x);
        Ide.text(g, "✕", width - 22, 7, Ide.ui(), Ide.SUBTLE);
        Ide.text(g, "清除", width - 70, 8, Ide.small(), Ide.SUBTLE);
        if (bottomTab == 3) {
            String hint = dbg != null && dbg.stopped ? "F5 继续 · F10 逐行 · F11 步入 · F8 步出" : "点工具栏「继续 / 逐行 / 步入 / 步出」";
            int hw = Ide.textWidth(g, hint, Ide.small());
            Ide.text(g, hint, width - 90 - hw, 8, Ide.small(), Ide.FAINT);
        }
    }

    void bottomHeadDown(MouseEvent e) {
        if (e.x > bottomHead.width - 32) {
            toggleBottom();
            return;
        }
        if (e.x > bottomHead.width - 80) {
            IdeTree t = bottomTab == 0 ? output : (bottomTab == 1 ? problems : (bottomTab == 2 ? search : debugTree));
            if (bottomTab == 1 || bottomTab == 3) {
                return;
            }
            t.rows.clear();
            t.root = new IdeItem("", "", "");
            t.rebuild();
            bottomHead.repaint();
            return;
        }
        int t = 0;
        while (t < 4 && t + 1 < bhEdges.size()) {
            if (e.x >= bhEdges.get(t) && e.x < bhEdges.get(t + 1)) {
                showBottomTab(t);
                return;
            }
            t = t + 1;
        }
    }

    void paintStatus(Graphics g) {
        g.setAntialias(true);
        int width = status.width;
        int height = status.height;
        boolean busy = buildProc != null && buildProc.running() || runProc != null && runProc.running()
                || dbg != null && dbg.active;
        Ide.fill(g, busy ? Ide.ACCENT : 0x2B2F36, 0, 0, width, height);
        Ide.textRow(g, statusText, 12, 0, height, Ide.small(), 0xFFFFFF);
        int rightEdge = width - 14;
        if (caretText.length() > 0) {
            int tw = Ide.textWidth(g, caretText, Ide.small());
            Ide.textRow(g, caretText, rightEdge - tw, 0, height, Ide.small(), 0xFFFFFF);
            rightEdge = rightEdge - tw - 18;
        }
        if (project != null) {
            String counts = "✖ " + diagCount + "   ⚠ " + warnCount;
            int cw = Ide.textWidth(g, counts, Ide.small());
            Ide.textRow(g, counts, rightEdge - cw, 0, height, Ide.small(), diagCount > 0 ? 0xFFB4B4 : 0xFFFFFF);
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
        if (IdeDialog.template == 2) {
            openFile(p.dir + "/src/Main.java");
        } else {
            openFile(p.dir + "/src/MainForm.jnui");
        }
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
        if (mainForm.length() > 0 && !skipAutoOpen) {
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
        boolean debugging = dbg != null && dbg.active;
        boolean canStep = debugging && dbg.stopped;
        btnBuild.setEnabled(has && !building && !debugging);
        btnRun.setEnabled(has && !building && !running && !debugging);
        btnDebug.setEnabled(has && !building && (!debugging || dbg.stopped));
        btnCont.setEnabled(canStep);
        btnStep.setEnabled(canStep);
        btnInto.setEnabled(canStep);
        btnOut.setEnabled(canStep);
        btnStop.setEnabled(running || building || debugging);
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
        cm.add(new MenuItem("新建文件…", () -> newFile(it)));
        cm.add(new MenuItem("新建文件夹…", () -> newFolder(it)));
        cm.addSeparator();
        if (!it.icon.equals("project")) {
            cm.add(new MenuItem("重命名…", () -> renameItem(it)));
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
        boolean dir = Files.isDirectory(FsPath.of(it.key));
        String what = dir ? "文件夹 " + it.text + " 及其全部内容" : it.text;
        if (!Dialogs.confirm(window, "确定删除 " + what + " ？将移到废纸篓。")) {
            return;
        }
        closeDocsUnder(it.key);
        if (!Desktop.moveToTrash(FsPath.of(it.key))) {
            if (dir) {
                Dialogs.error(window, "无法删除文件夹，请在 Finder 中操作。");
                return;
            }
            Files.delete(FsPath.of(it.key));
        }
        refreshExplorer();
        setStatus("已删除 " + it.text);
    }

    /** Closes (without asking) every open document at or below a path that is about to disappear. */
    void closeDocsUnder(String path) {
        int i = tabs.docs.size() - 1;
        while (i >= 0) {
            String dp = tabs.docs.get(i).path;
            if (dp.equals(path) || dp.startsWith(path + "/")) {
                forceClose(i);
            }
            i = i - 1;
        }
    }

    boolean validName(String n) {
        if (n.length() == 0 || n.indexOf("/") >= 0 || n.startsWith(".") || n.indexOf("\\") >= 0) {
            Dialogs.error(window, "名称无效：" + n);
            return false;
        }
        return true;
    }

    void newFile(IdeItem at) {
        if (project == null) {
            return;
        }
        String dir = dirOf(at);
        IdeDialog.input(window, "新建文件", "文件名（例如 notes.md）", "untitled.txt", n -> {
            if (!validName(n)) {
                return;
            }
            String path = dir + "/" + n;
            if (Files.exists(FsPath.of(path))) {
                Dialogs.error(window, "已存在：" + n);
                return;
            }
            Files.writeString(FsPath.of(path), "");
            refreshExplorer();
            openFile(path);
        });
    }

    void newFolder(IdeItem at) {
        if (project == null) {
            return;
        }
        String dir = dirOf(at);
        IdeDialog.input(window, "新建文件夹", "文件夹名", "folder", n -> {
            if (!validName(n)) {
                return;
            }
            String path = dir + "/" + n;
            if (Files.exists(FsPath.of(path))) {
                Dialogs.error(window, "已存在：" + n);
                return;
            }
            Files.createDirectories(FsPath.of(path));
            refreshExplorer();
            setStatus("已创建文件夹 " + n);
        });
    }

    void renameItem(IdeItem it) {
        if (project == null || it.icon.equals("project")) {
            return;
        }
        IdeDialog.input(window, "重命名", "新名称", it.text, n -> renameTo(it, n));
    }

    void renameTo(IdeItem it, String n) {
        {
            if (n.equals(it.text) || !validName(n)) {
                return;
            }
            String from = it.key;
            String to = Ide.dirName(from) + "/" + n;
            if (Files.exists(FsPath.of(to))) {
                Dialogs.error(window, "已存在：" + n);
                return;
            }
            int i = 0;
            while (i < tabs.docs.size()) {
                IdeDoc od = tabs.docs.get(i);
                if ((od.path.equals(from) || od.path.startsWith(from + "/")) && od.isDirty()) {
                    saveDoc(od);
                }
                i = i + 1;
            }
            if (!Files.move(FsPath.of(from), FsPath.of(to))) {
                Dialogs.error(window, "重命名失败：" + it.text);
                return;
            }
            i = 0;
            while (i < tabs.docs.size()) {
                IdeDoc rd = tabs.docs.get(i);
                if (rd.path.equals(from) || rd.path.startsWith(from + "/")) {
                    rd.path = to + rd.path.substring(from.length());
                    rd.title = Ide.baseName(rd.path);
                }
                i = i + 1;
            }
            IdeComplete.touch();
            refreshExplorer();
            tabs.repaint();
            setStatus("已重命名为 " + n);
        }
    }

    // ---------------------------------------------------------------- search in project

    void findInFiles() {
        if (project == null) {
            setStatus("请先打开项目");
            return;
        }
        String init = lastFind;
        IdeDoc d = activeDoc();
        if (d != null && d.kind == 0 && d.editor.selection.toRange().length() > 0
                && d.editor.selection.toRange().length() < 80) {
            TextRange sr = d.editor.selection.toRange();
            init = d.editor.document.substring(sr.start, sr.end);
        }
        IdeDialog.input(window, "在项目中查找", "查找内容（不区分大小写）：", init, q -> runSearch(q));
    }

    void runSearch(String q) {
        if (project == null || q.length() == 0) {
            return;
        }
        lastFind = q;
        searchQuery = q;
        String lq = q.toLowerCase();
        ArrayList<IdeItem> files = new ArrayList<IdeItem>();
        collectFiles(project.dir, files, 0);
        IdeItem root = new IdeItem("", "", "");
        int total = 0;
        int f = 0;
        while (f < files.size() && total < 500) {
            IdeItem fi = files.get(f);
            f = f + 1;
            String text;
            int open = tabs.find(fi.key);
            if (open >= 0 && tabs.docs.get(open).kind == 0) {
                text = tabs.docs.get(open).editor.getText();
            } else {
                if (Files.size(FsPath.of(fi.key)) > 2000000) {
                    continue;
                }
                text = Files.readString(FsPath.of(fi.key));
            }
            if (text.toLowerCase().indexOf(lq) < 0) {
                continue;
            }
            int pos = 0;
            int lineNo = 0;
            int n = text.length();
            while (pos <= n && total < 500) {
                int eol = text.indexOf("\n", pos);
                if (eol < 0) {
                    eol = n;
                }
                String line = text.substring(pos, eol);
                int col = line.toLowerCase().indexOf(lq);
                if (col >= 0) {
                    String shown = line.trim();
                    if (shown.length() > 200) {
                        shown = shown.substring(0, 200);
                    }
                    IdeItem it = new IdeItem(shown, Ide.iconForFile(fi.text), fi.key);
                    it.hint = fi.hint + ":" + (lineNo + 1);
                    it.tag = lineNo * 4096 + Math.min(col, 4095);
                    root.add(it);
                    total = total + 1;
                }
                pos = eol + 1;
                lineNo = lineNo + 1;
            }
        }
        search.setRoot(root);
        search.scroll = 0;
        search.emptyText = "没有找到「" + q + "」";
        showBottomTab(2);
        setStatus(total == 0 ? "没有找到「" + q + "」" : "找到 " + total + (total >= 500 ? "+" : "") + " 处「" + q + "」");
    }

    void searchActivate(IdeItem it) {
        navRecord();
        openFile(it.key);
        IdeDoc d = activeDoc();
        if (d != null && d.kind == 0) {
            int line = it.tag / 4096;
            int col = it.tag % 4096;
            d.editor.goTo(line, col);
            d.editor.selectRange(d.editor.caret.offset, d.editor.caret.offset + searchQuery.length());
            d.editor.requestFocus();
        }
    }

    // ---------------------------------------------------------------- formatting

    /** Replaces the editor text with {@code neu} as one undoable edit that only touches the changed middle part. */
    void applyTextDiff(CodeEditor e, String neu) {
        String old = e.getText();
        int pre = 0;
        int max = Math.min(old.length(), neu.length());
        while (pre < max && old.charAt(pre) == neu.charAt(pre)) {
            pre = pre + 1;
        }
        int suf = 0;
        while (suf < max - pre && old.charAt(old.length() - 1 - suf) == neu.charAt(neu.length() - 1 - suf)) {
            suf = suf + 1;
        }
        e.undo.closeGroup();
        e.applyEdit(pre, old.length() - pre - suf, neu.substring(pre, neu.length() - suf));
        e.undo.closeGroup();
    }

    // ---------------------------------------------------------------- rename symbol

    /** ⇧⌘R: renames the identifier under the caret in all project sources (and the designer files that name it). */
    void renameSymbol() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0 || project == null) {
            return;
        }
        String text = d.editor.getText();
        int c = d.editor.caret.offset;
        int a = c;
        while (a > 0 && IdeRename.identChar(text.charAt(a - 1))) {
            a = a - 1;
        }
        int b = c;
        while (b < text.length() && IdeRename.identChar(text.charAt(b))) {
            b = b + 1;
        }
        String word = text.substring(a, b);
        if (word.length() == 0 || !DsUtil.isIdent(word) || DsUtil.isKeyword(word)) {
            setStatus("请把光标放在要重命名的标识符上");
            return;
        }
        IdeDialog.input(window, "重命名符号", "将「" + word + "」重命名为：", word, n -> renameSymbolTo(word, n));
    }

    /** Computes the new text of every project file that mentions the identifier; returns the replacement count. */
    int planRename(String word, String n, ArrayList<String> paths, ArrayList<String> texts) {
        ArrayList<IdeItem> files = new ArrayList<IdeItem>();
        collectFiles(project.dir, files, 0);
        int total = 0;
        int f = 0;
        while (f < files.size()) {
            String path = files.get(f).key;
            f = f + 1;
            boolean isJava = path.endsWith(".java");
            if (!isJava && !path.endsWith(".jnui")) {
                continue;
            }
            int open = tabs.find(path);
            String text = "";
            if (open >= 0 && tabs.docs.get(open).kind == 0) {
                text = tabs.docs.get(open).editor.getText();
            } else if (open >= 0 && tabs.docs.get(open).kind == 1) {
                saveDesigner(tabs.docs.get(open));
                text = Files.readString(FsPath.of(path));
            } else {
                text = Files.readString(FsPath.of(path));
            }
            int[] cnt = new int[1];
            String neu = isJava ? IdeRename.replaceIdent(text, word, n, cnt) : IdeRename.replaceQuoted(text, word, n, cnt);
            if (cnt[0] > 0) {
                paths.add(path);
                texts.add(neu);
                total = total + cnt[0];
            }
        }
        return total;
    }

    void renameSymbolTo(String word, String n) {
        if (n.equals(word)) {
            return;
        }
        if (!DsUtil.isIdent(n) || DsUtil.isKeyword(n)) {
            Dialogs.error(window, "标识符无效：" + n);
            return;
        }
        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<String> texts = new ArrayList<String>();
        int total = planRename(word, n, paths, texts);
        if (total == 0) {
            setStatus("没有找到可重命名的「" + word + "」");
            return;
        }
        if (!Dialogs.confirm(window, "将「" + word + "」重命名为「" + n + "」：共 " + total + " 处，" + paths.size()
                + " 个文件。继续？\n（已打开的文件可用 ⌘Z 撤销，其它文件会直接写入磁盘。）")) {
            return;
        }
        applyRename(paths, texts);
        IdeComplete.touch();
        setStatus("已重命名 " + word + " → " + n + "：" + total + " 处，" + paths.size() + " 个文件");
    }

    void applyRename(ArrayList<String> paths, ArrayList<String> texts) {
        ArrayList<String> reopen = new ArrayList<String>();
        int i = 0;
        while (i < paths.size()) {
            String path = paths.get(i);
            int open = tabs.find(path);
            if (open >= 0 && tabs.docs.get(open).kind == 0) {
                applyTextDiff(tabs.docs.get(open).editor, texts.get(i));
            } else {
                Files.writeString(FsPath.of(path), texts.get(i));
                if (open >= 0) {
                    reopen.add(path);
                }
            }
            i = i + 1;
        }
        i = 0;
        while (i < reopen.size()) {
            int idx = tabs.find(reopen.get(i));
            if (idx >= 0) {
                forceClose(idx);
            }
            openFile(reopen.get(i));
            i = i + 1;
        }
    }

    /** ⇧⌘I: re-indents the current Java file by bracket depth (one undoable edit that only touches changed text). */
    void formatDocument() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        CodeEditor e = d.editor;
        String old = e.getText();
        String neu = IdeFormat.reindent(old);
        if (neu.equals(old)) {
            setStatus("缩进已经是整齐的");
            return;
        }
        TextPosition cp = e.document.offsetToPosition(e.caret.offset);
        String oldLine = e.document.getLine(cp.line);
        int oldIndent = 0;
        while (oldIndent < oldLine.length() && oldLine.charAt(oldIndent) == ' ') {
            oldIndent = oldIndent + 1;
        }
        applyTextDiff(e, neu);
        String newLine = e.document.getLine(Math.min(cp.line, e.document.getLineCount() - 1));
        int newIndent = 0;
        while (newIndent < newLine.length() && newLine.charAt(newIndent) == ' ') {
            newIndent = newIndent + 1;
        }
        int col = cp.column >= oldIndent ? cp.column + newIndent - oldIndent : newIndent;
        e.goTo(Math.min(cp.line, e.document.getLineCount() - 1), Math.max(0, Math.min(col, newLine.length())));
        setStatus("已重新缩进");
    }

    // ---------------------------------------------------------------- live error check

    /** Reloads open files that changed on disk (unless they have unsaved edits, then only warns). */
    void checkExternalChanges() {
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc d = tabs.docs.get(i);
            i = i + 1;
            if (d.kind != 0 || !Files.exists(FsPath.of(d.path))) {
                continue;
            }
            long m = Files.lastModified(FsPath.of(d.path));
            if (m == d.mtime) {
                continue;
            }
            d.mtime = m;
            if (d.isDirty()) {
                setStatus("注意：" + d.title + " 已在外部被修改，保存将覆盖外部更改");
                continue;
            }
            String t = Files.readString(FsPath.of(d.path));
            if (!t.equals(d.editor.getText())) {
                applyTextDiff(d.editor, t);
                d.editor.document.markSaved();
                setStatus("已重新载入外部修改：" + d.title);
                markEdited();
                tabs.repaint();
            }
        }
    }

    // ---------------------------------------------------------------- session

    void saveSession() {
        if (project == null) {
            return;
        }
        IdeSettings.lastProject = project.dir;
        IdeSettings.session = new ArrayList<String>();
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeSettings.session.add(tabs.docs.get(i).path);
            i = i + 1;
        }
        IdeDoc a = activeDoc();
        IdeSettings.sessionActive = a == null ? "" : a.path;
        IdeSettings.save();
    }

    /** Reopens the project and tabs of the previous run (called at startup when no project was given). */
    void restoreSession() {
        IdeSettings.load();
        String dir = IdeSettings.lastProject;
        if (dir.length() == 0 || !Files.exists(FsPath.of(dir + "/jnative.project"))) {
            return;
        }
        ArrayList<String> docs = new ArrayList<String>();
        int i = 0;
        while (i < IdeSettings.session.size()) {
            docs.add(IdeSettings.session.get(i));
            i = i + 1;
        }
        String active = IdeSettings.sessionActive;
        skipAutoOpen = docs.size() > 0;
        restoring = true;
        openProject(dir);
        skipAutoOpen = false;
        i = 0;
        while (i < docs.size()) {
            if (Files.exists(FsPath.of(docs.get(i)))) {
                openFile(docs.get(i));
            }
            i = i + 1;
        }
        restoring = false;
        int idx = tabs.find(active);
        if (idx >= 0) {
            showDoc(idx);
        } else {
            saveSession();
        }
    }

    void markEdited() {
        checkPending = true;
        lastEditMs = System.currentTimeMillis();
    }

    void editorChanged(IdeDoc d, DocumentChange ch) {
        markEdited();
        int oldLines = d.lines;
        int newLines = d.editor.document.getLineCount();
        d.lines = newLines;
        int delta = newLines - oldLines;
        if (delta != 0 && oldLines > 0) {
            int line = d.editor.document.offsetToPosition(ch.offset).line;
            shiftBreaks(d.path, line, delta);
            applyBreaks(d);
            if (dbg.active) {
                dbg.sendBreakpoints(d.path);
            }
        }
    }

    void toggleLiveCheck() {
        IdeSettings.liveCheck = !IdeSettings.liveCheck;
        IdeSettings.save();
        if (IdeSettings.liveCheck) {
            markEdited();
            setStatus("实时错误检查：开");
        } else {
            setStatus("实时错误检查：关");
        }
    }

    /** Runs every half second: when the user paused typing, starts a frontend-only check of the project. */
    void checkTick() {
        tickCount = tickCount + 1;
        if (dbg != null && dbg.active && dbg.launchAt > 0 && !dbg.stopped && !dbg.sawInitialized) {
            if (System.currentTimeMillis() - dbg.launchAt > 10000) {
                appendOutput("调试器启动超时（10 秒内 lldb-dap 没有就绪）。已停止。", Ide.ERROR);
                showBottomTab(0);
                dbg.stop();
            }
        }
        if (tickCount % 4 == 0) {
            checkExternalChanges();
        }
        if (!checkPending || !IdeSettings.liveCheck || project == null || !IdeSettings.hasCompiler()) {
            return;
        }
        if (buildProc != null && buildProc.running()) {
            return;
        }
        if (checkProc != null && checkProc.running()) {
            return;
        }
        if (System.currentTimeMillis() - lastEditMs < 900) {
            return;
        }
        startCheck();
    }

    void startCheck() {
        checkPending = false;
        checkSeq = checkSeq + 1;
        String overlayArgs = "";
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc d = tabs.docs.get(i);
            if (d.kind == 0 && d.isDirty() && d.path.endsWith(".java")) {
                String tmp = "/tmp/jnative-studio-overlay-" + i + ".txt";
                Files.writeString(FsPath.of(tmp), d.editor.getText());
                overlayArgs = overlayArgs + " --overlay '" + d.path + "=" + tmp + "'";
            }
            i = i + 1;
        }
        checkDiags = new ArrayList<IdeDiag>();
        IdeProc p = new IdeProc();
        p.onLine = ln -> checkLine(ln);
        p.onExit = code -> checkDone(code);
        checkProc = p;
        if (!p.start(project.dir, IdeSettings.analyzeCommand(overlayArgs))) {
            checkProc = null;
        }
    }

    void checkLine(String ln) {
        IdeDiag d = IdeDiag.parse(ln);
        if (d != null && project != null && d.path.startsWith(project.dir)) {
            checkDiags.add(d);
        }
    }

    void checkDone(int code) {
        checkProc = null;
        if (buildProc != null && buildProc.running()) {
            return;
        }
        if (project == null) {
            return;
        }
        /* Replace the problem list with the fresh result (a build repopulates it when it runs). */
        diags = checkDiags;
        diagCount = 0;
        warnCount = 0;
        int i = 0;
        while (i < diags.size()) {
            if (diags.get(i).severity == 1) {
                diagCount = diagCount + 1;
            } else {
                warnCount = warnCount + 1;
            }
            i = i + 1;
        }
        refreshProblems();
        applyDiagnostics();
    }

    // ---------------------------------------------------------------- view helpers

    void zoomEditors(int delta) {
        int n = delta == 0 ? 13 : IdeSettings.fontSize + delta;
        n = Math.max(9, Math.min(32, n));
        IdeSettings.fontSize = n;
        IdeSettings.save();
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc d = tabs.docs.get(i);
            if (d.kind == 0) {
                d.editor.font = Font.mono(n);
                d.editor.repaint();
            }
            i = i + 1;
        }
        setStatus("编辑器字号 " + n);
    }

    void cycleTab(int dir) {
        int n = tabs.docs.size();
        if (n < 2) {
            return;
        }
        showDoc((tabs.active + dir + n) % n);
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
            ed.font = Font.mono(IdeSettings.fontSize);
            ed.setTheme(EditorTheme.light());
            ed.setText(text);
            ed.document.markSaved();
            ed.onCaretChanged(pos -> caretMoved(pos));
            d.mtime = Files.lastModified(FsPath.of(path));
            ed.document.onChange(ch -> editorChanged(d, ch));
            ed.keyPre = e -> editorKey(e);
            ed.typedListener = c -> typedHook(c);
            ed.commandClick = e -> gotoDefinition();
            ed.gutterListener = ln -> gutterClick(d, ln);
            d.lines = ed.document.getLineCount();
            ed.onMouse(e -> {
                if (e.eventType == 1) {
                    suggest.hide();
                    hint.hide();
                } else if (e.eventType == 5) {
                    hoverMoved(ed, e);
                }
            });
            d.editor = ed;
            applyBreaks(d);
            if (project != null && (path.endsWith(".java") || path.endsWith(".jn")) && ensureLsp()) {
                lsp.sync(path, text);
            }
        }
        tabs.docs.add(d);
        host.add(d.view());
        showDoc(tabs.docs.size() - 1);
        applyDiagnostics();
        markEdited();
    }

    void docChanged() {
        tabs.repaint();
        refreshState();
    }

    void caretMoved(TextPosition p) {
        if (hint.isOpen) {
            if (hint.mode == 1) {
                hint.hide();
            } else {
                IdeDoc hd = activeDoc();
                if (hd != null && hd.kind == 0 && hd.editor == hint.editor && hd.path.length() > 0) {
                    hint.triggerParams(hd.editor, hd.path);
                }
            }
        }
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
        suggest.hide();
        hint.hide();
        IdeDoc d = activeDoc();
        findBar.sync();
        layoutDocs();
        if (d != null) {
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
        if (!restoring) {
            saveSession();
        }
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
        if (d.kind == 0 && d.path.length() > 0) {
            lsp.closeDoc(d.path);
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
            d.mtime = Files.lastModified(FsPath.of(d.path));
            IdeComplete.touch();
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

    void indexContext(IdeDoc d) {
        IdeComplete.projectDirs.clear();
        if (project != null) {
            int k = 0;
            while (k < project.srcDirs.size()) {
                IdeComplete.projectDirs.add(project.dir + "/" + project.srcDirs.get(k));
                k = k + 1;
            }
        }
        IdeComplete.currentFile = d.path;
    }

    /** Jumps to where the identifier under the caret is declared (project sources open in a tab). */
    void gotoDefinition() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        indexContext(d);
        String r = IdeComplete.definition(d.editor.getText(), d.editor.caret.offset);
        int colon = r.lastIndexOf(":");
        if (r.length() == 0 || colon < 0) {
            setStatus("找不到定义");
            return;
        }
        String path = r.substring(0, colon);
        int line = DsUtil.parseInt(r.substring(colon + 1), 1);
        if (path.length() == 0) {
            setStatus("定义在编译器内置类型（String / Object）中");
            return;
        }
        if (project == null || !path.startsWith(project.dir)) {
            setStatus("定义在标准库：" + Ide.baseName(path) + " 第 " + line + " 行");
            return;
        }
        navRecord();
        openFile(path);
        IdeDoc nd = activeDoc();
        if (nd != null && nd.kind == 0) {
            nd.editor.goTo(line - 1, 0);
            nd.editor.requestFocus();
        }
    }

    /** ⌘K: opens the inline completion list at the caret. */
    void completeDialog() {
        completeInline(true);
    }

    /** Shows the completion list for the caret; automatic triggers stay silent when nothing matches. */
    void completeInline(boolean manual) {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        CodeEditor e = d.editor;
        String[] pre = new String[1];
        indexContext(d);
        ArrayList<IdeItem> all = IdeComplete.candidates(e.getText(), e.caret.offset, pre);
        if (all.size() == 0) {
            suggest.hide();
            if (manual) {
                setStatus("没有可用的补全");
            }
            return;
        }
        hint.hide();
        suggest.open(e, all, pre[0]);
        if (!suggest.isOpen) {
            suggest.hide();
            if (manual) {
                setStatus("没有可用的补全");
            }
        }
    }

    void findDialog() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        findBar.open(false);
    }

    void findAgain() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        if (findBar.shown) {
            findBar.step(1);
            return;
        }
        if (lastFind.length() == 0) {
            findBar.open(false);
            return;
        }
        d.editor.findNext(lastFind);
    }

    void findPrevious() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        if (findBar.shown) {
            findBar.step(-1);
            return;
        }
        if (lastFind.length() == 0) {
            findBar.open(false);
            return;
        }
        d.editor.setFind(lastFind, d.editor.findCase, d.editor.findWhole);
        d.editor.findStep(-1, false);
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
                navRecord();
                cur.editor.goTo(Math.min(n, cur.editor.document.getLineCount()) - 1, 0);
                cur.editor.requestFocus();
            }
        });
    }

    // ---------------------------------------------------------------- navigation history

    ArrayList<String> navBack = new ArrayList<String>();
    ArrayList<String> navFwd = new ArrayList<String>();

    String navHere() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return "";
        }
        TextPosition p = d.editor.document.offsetToPosition(d.editor.caret.offset);
        return d.path + "\n" + p.line + "\n" + p.column;
    }

    /** Remembers the current caret position before a jump so ⌥⌘← can return to it. */
    void navRecord() {
        String here = navHere();
        if (here.length() == 0) {
            return;
        }
        if (navBack.size() > 0 && navBack.get(navBack.size() - 1).equals(here)) {
            navFwd.clear();
            return;
        }
        navBack.add(here);
        navFwd.clear();
    }

    void navJump(String loc) {
        int a = loc.indexOf("\n");
        int b = loc.lastIndexOf("\n");
        String path = loc.substring(0, a);
        int line = DsUtil.parseInt(loc.substring(a + 1, b), 0);
        int col = DsUtil.parseInt(loc.substring(b + 1), 0);
        openFile(path);
        IdeDoc nd = activeDoc();
        if (nd != null && nd.kind == 0) {
            nd.editor.goTo(line, col);
            nd.editor.requestFocus();
        }
    }

    void navBack() {
        if (navBack.size() == 0) {
            setStatus("没有更早的位置");
            return;
        }
        String here = navHere();
        String loc = navBack.get(navBack.size() - 1);
        navBack.removeAt(navBack.size() - 1);
        if (here.length() > 0) {
            navFwd.add(here);
        }
        navJump(loc);
    }

    void navForward() {
        if (navFwd.size() == 0) {
            setStatus("没有更新的位置");
            return;
        }
        String here = navHere();
        String loc = navFwd.get(navFwd.size() - 1);
        navFwd.removeAt(navFwd.size() - 1);
        if (here.length() > 0) {
            navBack.add(here);
        }
        navJump(loc);
    }

    // ---------------------------------------------------------------- quick pick

    void collectFiles(String dir, ArrayList<IdeItem> out, int depth) {
        ArrayList<String> kids = Files.list(FsPath.of(dir));
        int i = 0;
        while (i < kids.size() && out.size() < 3000) {
            String n = kids.get(i);
            String p = dir + "/" + n;
            if (n.startsWith(".")) {
                i = i + 1;
                continue;
            }
            if (Files.isDirectory(FsPath.of(p))) {
                if (depth < 8 && !n.endsWith(".app") && !n.equals("build") && !n.equals("node_modules")) {
                    collectFiles(p, out, depth + 1);
                }
            } else {
                String ext = Ide.fileExt(n);
                if (ext.equals("java") || ext.equals("jnui") || ext.equals("md") || ext.equals("txt")
                        || ext.equals("sh") || ext.equals("json") || ext.equals("project") || ext.equals("properties")) {
                    IdeItem it = new IdeItem(n, Ide.iconForFile(n), p);
                    it.hint = p.substring(project.dir.length() + 1);
                    out.add(it);
                }
            }
            i = i + 1;
        }
    }

    void quickOpenFile() {
        if (project == null) {
            setStatus("请先打开项目");
            return;
        }
        ArrayList<IdeItem> items = new ArrayList<IdeItem>();
        collectFiles(project.dir, items, 0);
        quick.open("转到文件…（输入文件名）", items, it -> {
            navRecord();
            openFile(it.key);
        });
    }

    void quickSymbol() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            setStatus("请先打开一个代码文件");
            return;
        }
        IdeTable t = new IdeTable();
        IdeComplete.indexText(d.editor.getText(), t, d.path);
        ArrayList<IdeItem> items = new ArrayList<IdeItem>();
        int i = 0;
        while (i < t.names.size()) {
            IdeCls c = t.get(t.names.get(i));
            IdeItem ci = new IdeItem(c.name, "cc", d.path);
            ci.tag = c.line;
            ci.hint = "类 · 第 " + c.line + " 行";
            items.add(ci);
            int m = 0;
            while (m < c.members.size()) {
                IdeMem mem = c.members.get(m);
                IdeItem mi = new IdeItem(mem.label(), mem.method ? "cm" : "cf", d.path);
                mi.tag = mem.line;
                mi.hint = c.name + " · 第 " + mem.line + " 行";
                items.add(mi);
                m = m + 1;
            }
            i = i + 1;
        }
        quick.open("转到符号…（类 / 方法 / 字段）", items, it -> {
            IdeDoc cur = activeDoc();
            if (cur != null && cur.kind == 0) {
                navRecord();
                cur.editor.goTo(it.tag - 1, 0);
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
        debugAfterBuild = false;
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
            if (debugAfterBuild) {
                debugAfterBuild = false;
                startDebug();
            } else if (runAfterBuild) {
                runProgram();
            }
        } else {
            debugAfterBuild = false;
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
        if (dbg.active) {
            dbg.stop();
        }
    }

    // ---------------------------------------------------------------- debugger

    /** F5: continue if paused; otherwise launch the current binary (rebuild only when it is missing). */
    void debug() {
        if (dbg.active) {
            if (dbg.stopped) {
                dbg.resume();
            }
            return;
        }
        if (project == null) {
            Dialogs.info(window, "请先打开或新建一个项目。");
            return;
        }
        if (IdeDap.findAdapter().length() == 0) {
            showBottomTab(0);
            appendOutput("找不到 lldb-dap：请安装 Xcode 或 Command Line Tools（xcode-select --install）", Ide.ERROR);
            return;
        }
        saveAll();
        if (Files.exists(FsPath.of(project.outputPath()))) {
            startDebug();
            return;
        }
        debugRebuild();
    }

    /** Rebuilds with --debug -O0, then launches. Use when the binary is missing or stale. */
    void debugRebuild() {
        if (project == null || (buildProc != null && buildProc.running()) || dbg.active) {
            return;
        }
        if (!IdeSettings.hasCompiler()) {
            IdeDialog.settings(window, () -> {
                status.repaint();
                welcome.repaint();
            });
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
        appendOutput("—— 调试生成 " + project.name + "（--debug -O0）——", Ide.ACCENT);
        runAfterBuild = false;
        debugAfterBuild = true;
        IdeProc p = new IdeProc();
        p.onLine = ln -> buildLine(ln);
        p.onExit = code -> buildDone(code);
        buildProc = p;
        if (!p.start(project.dir, IdeSettings.buildDebugCommand())) {
            appendOutput("无法启动编译器进程", Ide.ERROR);
            buildProc = null;
            debugAfterBuild = false;
            return;
        }
        setStatus("正在以调试信息重新生成…");
        refreshState();
    }

    void startDebug() {
        String exe = project.outputPath();
        if (!Files.exists(FsPath.of(exe))) {
            appendOutput("找不到可执行文件：" + exe, Ide.ERROR);
            return;
        }
        showBottomTab(3);
        if (!dbg.launch(exe, project.dir)) {
            return;
        }
        setStatus("正在调试 " + project.name);
        refreshState();
    }

    void debugActivate(IdeItem it) {
        if (it.parent != null && it.parent.text.equals("调用栈")) {
            dbg.selectFrame(DsUtil.parseInt(it.key, 0));
        }
    }

    void toggleBpAtCaret() {
        IdeDoc d = activeDoc();
        if (d == null || d.kind != 0) {
            return;
        }
        gutterClick(d, d.editor.document.offsetToPosition(d.editor.caret.offset).line);
    }

    void gutterClick(IdeDoc d, int line) {
        toggleBreakpoint(d.path, line);
        applyBreaks(d);
        if (dbg.active) {
            dbg.sendBreakpoints(d.path);
        }
    }

    void toggleBreakpoint(String path, int line) {
        int i = 0;
        while (i < bpPaths.size()) {
            if (bpPaths.get(i).equals(path) && bpLines.get(i) == line) {
                bpPaths.remove(i);
                bpLines.remove(i);
                return;
            }
            i = i + 1;
        }
        bpPaths.add(path);
        bpLines.add(line);
    }

    void shiftBreaks(String path, int atLine, int delta) {
        int i = 0;
        while (i < bpPaths.size()) {
            if (bpPaths.get(i).equals(path) && bpLines.get(i) > atLine) {
                int n = bpLines.get(i) + delta;
                if (n < 0) {
                    bpPaths.remove(i);
                    bpLines.remove(i);
                    continue;
                }
                bpLines.set(i, n);
            }
            i = i + 1;
        }
    }

    void applyBreaks(IdeDoc d) {
        if (d.kind != 0 || d.editor == null) {
            return;
        }
        d.editor.breakLines.clear();
        int i = 0;
        while (i < bpPaths.size()) {
            if (bpPaths.get(i).equals(d.path)) {
                d.editor.breakLines.add(bpLines.get(i));
            }
            i = i + 1;
        }
        d.editor.repaint();
    }

    void showExecLine(String path, int line) {
        clearExecLine();
        openFile(path);
        int idx = tabs.find(path);
        if (idx >= 0 && tabs.docs.get(idx).kind == 0) {
            CodeEditor ed = tabs.docs.get(idx).editor;
            ed.execLine = line;
            ed.goTo(line, 0);
            ed.requestFocus();
            ed.repaint();
        }
    }

    void clearExecLine() {
        int i = 0;
        while (i < tabs.docs.size()) {
            IdeDoc d = tabs.docs.get(i);
            if (d.kind == 0 && d.editor != null && d.editor.execLine >= 0) {
                d.editor.execLine = -1;
                d.editor.repaint();
            }
            i = i + 1;
        }
    }

    String formattersPath() {
        String c = IdeSettings.compiler;
        String root = "";
        if (c.endsWith("/target/classes")) {
            root = c.substring(0, c.length() - 15);
        } else if (c.endsWith("/target/jnative.jar")) {
            root = c.substring(0, c.length() - 19);
        }
        if (root.length() > 0) {
            String p = root + "/tools/lldb/jnative_formatters.py";
            if (Files.exists(FsPath.of(p))) {
                return p;
            }
        }
        return "";
    }

    void appendProgramOutput(String text, boolean err) {
        ArrayList<String> lines = DsGen.splitLines(text);
        int i = 0;
        while (i < lines.size()) {
            if (lines.get(i).length() > 0 || i + 1 < lines.size()) {
                appendOutput(lines.get(i), err ? Ide.ERROR : Ide.TEXT);
            }
            i = i + 1;
        }
    }

    void debugEnded(boolean was) {
        clearExecLine();
        debugTree.emptyText = "点击行号左侧设断点，再按「调试」。暂停后可用「继续 / 逐行 / 步入 / 步出」";
        debugTree.setRoot(new IdeItem("", "", ""));
        if (was) {
            setStatus("就绪");
        }
        refreshState();
        if (bottomHead != null) {
            bottomHead.repaint();
        }
    }

    void debugStopped(String reason) {
        showBottomTab(3);
        setStatus(reason.length() > 0 ? "已暂停（" + reason + "）" : "已暂停");
        refreshState();
    }

    void debugRunning() {
        setStatus("正在调试…");
        refreshState();
        if (bottomHead != null) {
            bottomHead.repaint();
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

    /** ⌘Q: asks about unsaved documents; false cancels the quit. */
    boolean confirmQuit() {
        int i = 0;
        while (i < tabs.docs.size()) {
            if (!confirmDoc(tabs.docs.get(i))) {
                return false;
            }
            i = i + 1;
        }
        lsp.stop();
        return true;
    }

    /** Called when the window is asked to close: returns false to veto. */
    boolean confirmExit() {
        saveSession();
        int i = 0;
        while (i < tabs.docs.size()) {
            if (!confirmDoc(tabs.docs.get(i))) {
                return false;
            }
            i = i + 1;
        }
        stopAll();
        lsp.stop();
        return true;
    }
}

