/**
 * JNative Windows Explorer - application state, navigation, file commands, input and menus.
 *
 * @author 张晓达
 */
class FmApp {
    static Window win;
    static PaintView cv;
    static TextField addr;
    static TextField sbox;
    static TextField ren;
    static FmFolder folder;
    static FmList list;
    static FmNavTree nav;
    static FmHistory hist;
    static int W = 1180;
    static int H = 740;
    static int navW = 232;
    static int bodyY = 92;
    static int addrX = 158;
    static int addrW = 400;
    static int sbX = -1;
    static int sbY = -1;
    static int sbW = -1;
    static int tick = 0;
    /** 0 = follow the system, 1 = light, 2 = dark */
    static int themeMode = 0;
    /** view mode restored from the settings file (0 = details, 1 = icons) */
    static int startView = 0;
    static int showExt = 1;
    static int showHiddenOpt = 0;
    static int clipState = 0;
    static HashMap<String, String> cutPaths = new HashMap<String, String>();
    static int pendingSort = -1;
    /* inline rename */
    static int renaming = 0;
    static FmEntry renEntry = null;
    static int renX = -1;
    static int renY = -1;
    static int renW = -1;
    /* address edit */
    static int editingAddr = 0;
    static int renBlurTick = -1;
    static int addrBlurTick = -1;
    static int renStartTick = 0;
    static int addrBoundsSet = 0;
    /* search */
    static int searching = 0;
    static int searchHandle = 0;
    static int searchDirty = 0;
    static int searchDirtyTick = 0;
    static int muteSearch = 0;
    /* mouse bookkeeping */
    static int downArea = 0;
    static int downBtn = 0;
    static int splitDrag = 0;
    static int refreshAt = -1;
    static int ctxKind = 0;
    static int navDragIdx = -1;
    static int lastErr = 0;
    static int paintErrors = 0;

    static int testMode = 0;
    static String lastError = "";

    static void err(String m) {
        lastError = m;
        if (testMode == 0) {
            Dialogs.error(win, m);
        }
    }

    static void start(String startPath) {
        Application app = setup(startPath);
        cv.startTimer(50, () -> {
            FmApp.onTick();
        });
        app.run();
    }

    /** Builds the window and the models (no timer, no event loop) - also used by the headless tests. */
    static Application setup(String startPath) {
        Application.setDpiAware(true);
        Application app = new Application();
        win = new Window("文件资源管理器", 1180, 740);
        win.setMinSize(760, 440);
        Panel root = new Panel();
        cv = new PaintView();
        root.add(cv);
        addr = new TextField("");
        addr.setFlat();
        addr.setVisible(false);
        root.add(addr);
        sbox = new TextField("");
        sbox.setFlat();
        root.add(sbox);
        ren = new TextField("");
        ren.setFlat();
        ren.setVisible(false);
        root.add(ren);
        win.setContent(root);
        if (testMode == 0) {
            FmSettings.load();
        }
        folder = new FmFolder();
        list = new FmList(folder);
        if (startView == 1) {
            list.mode = 1;
            list.configure();
        }
        nav = new FmNavTree();
        nav.showHidden = showHiddenOpt;
        hist = new FmHistory();
        nav.build();
        applyTheme();
        cv.setOnPaint(() -> {
            FmApp.paint();
        });
        cv.onMouse((ev) -> {
            FmApp.onMouse(ev);
        });
        win.onKeyDown((ev) -> {
            FmApp.onKey(ev);
        });
        sbox.onChange((ev) -> {
            FmApp.onSearchChanged();
        });
        ren.onFocus((ev) -> {
            FmApp.onRenameFocus(ev);
        });
        addr.onFocus((ev) -> {
            FmApp.onAddrFocus(ev);
        });
        win.setResizeListener(() -> {
            FmApp.onResize();
        });
        win.show();
        /* setVisible(false) before the native window exists is not honoured: hide again now */
        addr.setVisible(false);
        ren.setVisible(false);
        onResize();
        String first = "::home";
        if (startPath.length() > 0) {
            String full = FmNative.take(FmNative.fullPath(stripQuotes(startPath)));
            if (FmNative.exists(full) == 2) {
                first = full;
            }
        }
        navigateTo(first, 1, "");
        return app;
    }

    static String stripQuotes(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    /* -------------------------------------------------------------------- theme */
    static int isDark() {
        if (themeMode == 2) {
            return 1;
        }
        if (themeMode == 1) {
            return 0;
        }
        if (UiNative.systemDark() == 1) {
            return 1;
        }
        return 0;
    }

    static void applyTheme() {
        int d = isDark();
        Ft.apply(d);
        if (d == 0) {
            win.setCaptionColors(243 * 65536 + 243 * 256 + 243, 27 * 65536 + 27 * 256 + 27, false);
        } else {
            win.setCaptionColors(32 * 65536 + 32 * 256 + 32, 255 * 65536 + 255 * 256 + 255, true);
        }
        sbox.setBackgroundRgba(Ft.editBg);
        sbox.setForegroundRgba(Ft.text);
        addr.setBackgroundRgba(Ft.editBg);
        addr.setForegroundRgba(Ft.text);
        ren.setBackgroundRgba(Ft.editBg);
        ren.setForegroundRgba(Ft.text);
    }

    static void onResize() {
        cv.setBounds(0, 0, win.width, win.height);
        cv.invalidate();
    }

    /* ------------------------------------------------------------------- helpers */
    static String displayName(FmEntry e) {
        if (showExt == 0 && e.isDir == 0 && e.ext.length() > 0) {
            String s = FmPath.stem(e.name);
            if (s.length() > 0) {
                return s;
            }
        }
        return e.name;
    }

    static int isCut(FmEntry e) {
        if (cutPaths.size() == 0) {
            return 0;
        }
        if (cutPaths.get(e.full.toLowerCase()) != null) {
            return 1;
        }
        return 0;
    }

    static String parentOfCurrent() {
        if (folder.isSearch() != 0) {
            return folder.searchRoot;
        }
        if (folder.isReal() == 0) {
            return "";
        }
        return FmPath.parent(folder.path);
    }

    static String locationIconKey() {
        if (folder.isReal() != 0) {
            return folder.path;
        }
        if (folder.isSearch() != 0) {
            return "*dir";
        }
        return "";
    }

    static int selectionIsFileSystem() {
        ArrayList<FmEntry> sel = folder.selectedEntries();
        if (sel.size() == 0) {
            return 0;
        }
        int i = 0;
        while (i < sel.size()) {
            if (sel.get(i).drive != 0) {
                return 0;
            }
            i = i + 1;
        }
        return 1;
    }

    static String driveLabel(String root) {
        int i = 0;
        FmNode pc = nav.pc;
        if (pc == null) {
            return root;
        }
        while (i < pc.kids.size()) {
            FmNode k = pc.kids.get(i);
            if (FmPath.samePath(k.path, root) != 0) {
                return k.label;
            }
            i = i + 1;
        }
        return root;
    }

    static String folderTitle() {
        if (folder.path.equals("::home")) {
            return "主页";
        }
        if (folder.path.equals("::pc")) {
            return "此电脑";
        }
        if (folder.isSearch() != 0) {
            return "搜索结果 - " + FmPath.nameOf(folder.searchRoot);
        }
        if (FmPath.isRoot(FmPath.trimSep(folder.path))) {
            return driveLabel(folder.path);
        }
        return FmPath.nameOf(folder.path);
    }

    /** Breadcrumb labels + the path each one navigates to. */
    static void buildCrumbs(ArrayList<String> labels, ArrayList<String> paths) {
        String p = folder.path;
        if (p.equals("::home")) {
            labels.add("主页");
            paths.add("::home");
            return;
        }
        if (p.equals("::pc")) {
            labels.add("此电脑");
            paths.add("::pc");
            return;
        }
        String real = p;
        if (folder.isSearch() != 0) {
            real = folder.searchRoot;
        }
        if (real.length() == 0) {
            return;
        }
        labels.add("此电脑");
        paths.add("::pc");
        ArrayList<String> names = FmPath.segmentNames(real);
        int i = 0;
        while (i < names.size()) {
            String sp = FmPath.segmentPath(real, i);
            String lb = names.get(i);
            if (i == 0 && FmPath.isRoot(sp)) {
                lb = driveLabel(sp);
            }
            labels.add(lb);
            paths.add(sp);
            i = i + 1;
        }
        if (folder.isSearch() != 0) {
            labels.add("搜索结果");
            paths.add(real);
        }
    }

    static int crumbHasKids(String p) {
        if (p.equals("::home")) {
            return 0;
        }
        return 1;
    }

    static String joinNl(ArrayList<String> items) {
        String r = "";
        int i = 0;
        while (i < items.size()) {
            if (i > 0) {
                r = r + "\n";
            }
            r = r + items.get(i);
            i = i + 1;
        }
        return r;
    }

    static String describeError(int code) {
        if (code == 5) {
            return "拒绝访问。你可能需要管理员权限。";
        }
        if (code == 2 || code == 3) {
            return "系统找不到指定的文件。";
        }
        if (code == 32) {
            return "该文件正被另一个程序使用。";
        }
        if (code == 80 || code == 183) {
            return "此位置已经包含同名的文件或文件夹。";
        }
        if (code == 206) {
            return "文件名或扩展名太长。";
        }
        if (code == 1223) {
            return "操作已取消。";
        }
        return "操作未能完成(错误 " + String.valueOf(code) + ")。";
    }

    /* ---------------------------------------------------------------- navigation */
    static void loadInto(String p, ArrayList<String> sel, String focusFull) {
        folder.showHidden = showHiddenOpt;
        if (p.equals("::home")) {
            folder.loadHome();
        } else if (p.equals("::pc")) {
            folder.loadPc();
        } else {
            folder.loadReal(p, sel, focusFull);
        }
        list.configure();
    }

    static void afterLoad() {
        win.setTitle(folderTitle());
        String cue = "搜索";
        if (folder.isReal() != 0) {
            cue = "在 " + folderTitle() + " 中搜索";
        } else if (folder.path.equals("::pc")) {
            cue = "搜索 此电脑";
        } else if (folder.path.equals("::home")) {
            cue = "搜索 主页";
        }
        sbox.setCueBanner(cue);
        if (folder.isReal() != 0) {
            FmNative.watchSet(folder.path);
        } else {
            FmNative.watchSet("");
        }
        refreshClip();
        cv.invalidate();
    }

    static void navigateTo(String path, int addHist, String selectPath) {
        endSearch();
        cancelRename();
        cancelAddr();
        String p = path;
        if (!p.startsWith("::")) {
            p = FmPath.trimSep(p);
            int k = FmNative.exists(p);
            if (k == 0) {
                err("Windows 找不到“" + p + "”。请检查拼写并重试。");
                return;
            }
            if (k == 1) {
                openPath(p, FmPath.parent(p));
                return;
            }
        }
        String prev = folder.path;
        if (prev.equals("::search")) {
            prev = folder.searchRoot;
        }
        loadInto(p, null, "");
        if (folder.err != 0 && folder.isReal() != 0) {
            int code = folder.err;
            if (prev.length() > 0) {
                loadInto(prev, null, "");
            }
            err("无法访问 " + p + "\n" + describeError(code));
            afterLoad();
            return;
        }
        if (addHist != 0) {
            hist.visit(p);
        }
        list.scroll = 0;
        list.hover = -1;
        if (selectPath.length() > 0) {
            int idx = folder.indexOfPath(selectPath);
            if (idx >= 0) {
                folder.selectOnly(idx);
                list.ensureVisible(idx);
            }
        }
        sbMute("");
        afterLoad();
    }

    static void sbMute(String text) {
        muteSearch = 1;
        sbox.setText(text);
        muteSearch = 0;
    }

    static void refresh() {
        if (folder.isSearch() != 0) {
            runSearch();
            return;
        }
        if (folder.path.length() == 0) {
            return;
        }
        String p = folder.path;
        if (folder.isReal() != 0 && FmNative.exists(p) != 2) {
            /* the folder vanished: climb to the nearest existing parent */
            String up = FmPath.parent(p);
            while (up.length() > 0 && !up.startsWith("::") && FmNative.exists(up) != 2) {
                up = FmPath.parent(up);
            }
            if (up.length() == 0) {
                up = "::pc";
            }
            navigateTo(up, 1, "");
            return;
        }
        ArrayList<String> sel = folder.selectedPaths();
        String fo = "";
        if (folder.focusEntry != null) {
            fo = folder.focusEntry.full;
        }
        int sc = list.scroll;
        loadInto(p, sel, fo);
        list.scroll = sc;
        list.clampScroll();
        afterLoad();
    }

    static void goBack() {
        if (!hist.canBack()) {
            return;
        }
        String from = folder.path;
        String p = hist.back();
        navigateTo(p, 0, from);
    }

    static void goForward() {
        if (!hist.canForward()) {
            return;
        }
        String p = hist.forward();
        navigateTo(p, 0, "");
    }

    static void goUp() {
        String up = parentOfCurrent();
        if (up.length() == 0) {
            return;
        }
        String from = folder.path;
        if (folder.isSearch() != 0) {
            from = "";
        }
        navigateTo(up, 1, from);
    }

    /* --------------------------------------------------------------------- open */
    static void openPath(String full, String dir) {
        int ec = FmNative.shellExec(full, "", "", dir);
        if (ec == 31 || ec == 1155) {
            ec = FmNative.shellExec(full, "openas", "", dir);
        }
        if (ec != 0 && ec != 1223) {
            err("无法打开“" + FmPath.nameOf(full) + "”。\n" + describeError(ec));
        }
    }

    static void openEntry(FmEntry e) {
        if (e.isDir != 0) {
            navigateTo(e.full, 1, "");
        } else {
            openPath(e.full, e.dir);
        }
    }

    static void openSelection() {
        ArrayList<FmEntry> sel = folder.selectedEntries();
        if (sel.size() == 0) {
            return;
        }
        if (sel.size() == 1) {
            openEntry(sel.get(0));
            return;
        }
        int files = 0;
        int i = 0;
        while (i < sel.size()) {
            FmEntry e = sel.get(i);
            if (e.isDir == 0) {
                openPath(e.full, e.dir);
                files = files + 1;
            }
            i = i + 1;
        }
        if (files == 0) {
            navigateTo(sel.get(0).full, 1, "");
        }
    }

    /* --------------------------------------------------------------- clipboard ops */
    static void refreshClip() {
        int st = FmNative.clipState();
        clipState = st;
        if (st != 2 && cutPaths.size() > 0) {
            cutPaths.clear();
        }
    }

    static void clipSelection(int cut) {
        if (selectionIsFileSystem() == 0) {
            return;
        }
        ArrayList<String> sel = folder.selectedPaths();
        int r = FmNative.clipSet(joinNl(sel), cut);
        if (r != 0) {
            err("无法访问剪贴板。\n" + describeError(r));
            return;
        }
        cutPaths.clear();
        if (cut != 0) {
            int i = 0;
            while (i < sel.size()) {
                cutPaths.put(sel.get(i).toLowerCase(), "1");
                i = i + 1;
            }
            clipState = 2;
        } else {
            clipState = 1;
        }
        cv.invalidate();
    }

    static void doPaste(String dstOverride) {
        String dst = dstOverride;
        if (dst.length() == 0) {
            dst = folder.workDir();
        }
        if (dst.length() == 0) {
            return;
        }
        String s = FmNative.take(FmNative.clipGet());
        if (s.length() == 0) {
            return;
        }
        String[] lines = s.split("\n");
        int move = 0;
        if (lines.length > 0 && lines[0].equals("X")) {
            move = 1;
        }
        ArrayList<String> srcs = new ArrayList<String>();
        int i = 1;
        while (i < lines.length) {
            if (lines[i].length() > 0) {
                srcs.add(lines[i]);
            }
            i = i + 1;
        }
        if (srcs.size() == 0) {
            return;
        }
        transfer(srcs, dst, move, 1);
    }

    /** Copies / moves items into dst through the shell (progress + conflict dialogs). */
    static void transfer(ArrayList<String> srcs, String dst, int move, int fromClipboard) {
        int i = 0;
        int allSameParent = 1;
        int anySameParent = 0;
        while (i < srcs.size()) {
            String sp = srcs.get(i);
            if (FmNative.exists(sp) == 2 && FmPath.isInside(dst, sp) != 0) {
                err("无法将文件夹“" + FmPath.nameOf(sp) + "”复制或移动到其自身或其子文件夹中。");
                return;
            }
            if (FmPath.samePath(FmPath.parent(sp), dst) != 0) {
                anySameParent = 1;
            } else {
                allSameParent = 0;
            }
            i = i + 1;
        }
        if (move != 0 && allSameParent != 0) {
            return;
        }
        int flags = 0;
        if (move == 0 && anySameParent != 0) {
            flags = 1;
        }
        HashMap<String, String> before = new HashMap<String, String>();
        i = 0;
        while (i < folder.all.size()) {
            before.put(folder.all.get(i).full.toLowerCase(), "1");
            i = i + 1;
        }
        int r = FmNative.fileOp(1 + move, joinNl(srcs), dst, flags);
        if (r != 0 && r != 1223) {
            err(describeError(r));
        }
        if (r == 0 && move != 0 && fromClipboard != 0) {
            FmNative.clipClear();
            cutPaths.clear();
            clipState = 0;
        }
        refresh();
        if (folder.isReal() != 0 && FmPath.samePath(folder.path, dst) != 0) {
            folder.clearSel();
            i = 0;
            int first = -1;
            while (i < folder.rows.size()) {
                FmEntry e = folder.rows.get(i);
                if (before.get(e.full.toLowerCase()) == null) {
                    e.selected = 1;
                    if (first < 0) {
                        first = i;
                    }
                }
                i = i + 1;
            }
            if (first >= 0) {
                folder.setFocus(first);
                folder.anchor = first;
                folder.anchorEntry = folder.rows.get(first);
                list.ensureVisible(first);
            }
        }
        cv.invalidate();
    }

    static void doDelete(int permanent) {
        if (selectionIsFileSystem() == 0) {
            return;
        }
        ArrayList<String> sel = folder.selectedPaths();
        int idx = folder.focus;
        int op = 4;
        if (permanent != 0) {
            op = 3;
        }
        int r = FmNative.fileOp(op, joinNl(sel), "", 0);
        if (r != 0 && r != 1223) {
            err(describeError(r));
        }
        refresh();
        int n = folder.rows.size();
        if (n > 0 && folder.selCount() == 0) {
            if (idx < 0) {
                idx = 0;
            }
            if (idx >= n) {
                idx = n - 1;
            }
            folder.selectOnly(idx);
            list.ensureVisible(idx);
        }
        cv.invalidate();
    }

    static void copyPathText() {
        ArrayList<String> sel = folder.selectedPaths();
        if (sel.size() == 0 && folder.isReal() != 0) {
            sel.add(folder.path);
        }
        if (sel.size() == 0) {
            return;
        }
        String t = "";
        int i = 0;
        while (i < sel.size()) {
            if (i > 0) {
                t = t + "\r\n";
            }
            t = t + "\"" + sel.get(i) + "\"";
            i = i + 1;
        }
        FmNative.clipText(t);
    }

    static void showProperties() {
        ArrayList<String> sel = folder.selectedPaths();
        if (sel.size() == 0 && folder.isReal() != 0) {
            sel.add(folder.path);
        }
        if (sel.size() == 0) {
            return;
        }
        int r = FmNative.shellVerb(joinNl(sel), "properties");
        if (r != 0) {
            err("无法显示属性。\n" + describeError(r));
        }
    }

    static void moreOptions(int shift) {
        ArrayList<String> sel = folder.selectedPaths();
        String paths;
        int flags = shift;
        if (sel.size() == 0) {
            if (folder.isReal() == 0) {
                return;
            }
            paths = folder.path;
        } else {
            paths = joinNl(sel);
            if (sel.size() == 1 && folder.selectedEntries().get(0).isDir != 0) {
                flags = flags + 2;
            }
        }
        int r = FmNative.shellMenu(paths, flags);
        if (r == 1) {
            startRename();
        } else if (r == 2) {
            navigateTo(sel.get(0), 1, "");
        } else {
            refresh();
        }
    }

    static void newWindow() {
        String p = "";
        if (folder.isReal() != 0) {
            p = folder.path;
        }
        int r = FmNative.spawnSelf(p);
        if (r != 0) {
            err("无法打开新窗口。\n" + describeError(r));
        }
    }

    /* ------------------------------------------------------------------ new items */
    static String uniqueName(String dir, String base, String ext) {
        String name = base + ext;
        int n = 2;
        while (FmNative.exists(FmPath.join(dir, name)) != 0 && n < 1000) {
            name = base + " (" + String.valueOf(n) + ")" + ext;
            n = n + 1;
        }
        return name;
    }

    static void newItem(int isDir) {
        String dir = folder.workDir();
        if (dir.length() == 0) {
            return;
        }
        String name;
        if (isDir != 0) {
            name = uniqueName(dir, "新建文件夹", "");
        } else {
            name = uniqueName(dir, "新建文本文档", ".txt");
        }
        String full = FmPath.join(dir, name);
        int r = FmNative.create(full, isDir);
        if (r != 0) {
            err("无法创建“" + name + "”。\n" + describeError(r));
            return;
        }
        refresh();
        int idx = folder.indexOfPath(full);
        if (idx >= 0) {
            folder.selectOnly(idx);
            list.ensureVisible(idx);
            cv.invalidate();
            startRename();
        }
    }

    /* --------------------------------------------------------------------- rename */
    static void startRename() {
        if (folder.selCount() != 1 || selectionIsFileSystem() == 0) {
            return;
        }
        FmEntry e = folder.selectedEntries().get(0);
        int idx = folder.indexOfPath(e.full);
        if (idx < 0) {
            return;
        }
        cancelAddr();
        list.ensureVisible(idx);
        renEntry = e;
        renaming = 1;
        renBlurTick = -1;
        renStartTick = tick;
        String nm = displayName(e);
        ren.setText(nm);
        list.nameRect(idx);
        renX = list.rx;
        renY = list.ry;
        renW = list.rw;
        ren.setBounds(list.rx + 2, list.ry + 3, list.rw - 4, list.rh - 6);
        ren.setVisible(true);
        ren.requestFocus();
        int selEnd = nm.length();
        if (e.isDir == 0 && showExt != 0 && e.ext.length() > 0) {
            int dot = nm.lastIndexOf(".");
            if (dot > 0) {
                selEnd = dot;
            }
        }
        UiNative.taSetSel(ren.nativeId, 0, selEnd);
        cv.invalidate();
    }

    static void cancelRename() {
        if (renaming == 0) {
            return;
        }
        renaming = 0;
        renEntry = null;
        ren.setVisible(false);
        cv.requestFocus();
        cv.invalidate();
    }

    static void commitRename() {
        if (renaming == 0 || renEntry == null) {
            return;
        }
        FmEntry e = renEntry;
        String nn = ren.getText().trim();
        renaming = 0;
        renEntry = null;
        ren.setVisible(false);
        cv.requestFocus();
        if (nn.length() == 0 || nn.equals(displayName(e))) {
            cv.invalidate();
            return;
        }
        if (showExt == 0 && e.isDir == 0 && e.ext.length() > 0) {
            nn = nn + "." + e.ext;
            if (e.name.lastIndexOf(".") >= 0) {
                nn = nn.substring(0, nn.length() - e.ext.length() - 1) + e.name.substringFrom(e.name.lastIndexOf("."));
            }
        }
        if (FmPath.validName(nn) == 0) {
            err("文件名不能包含下列任何字符:\n\\ / : * ? \" < > |");
            cv.invalidate();
            return;
        }
        if (nn.equals(e.name)) {
            cv.invalidate();
            return;
        }
        int r = FmNative.rename(e.full, nn);
        if (r != 0) {
            err("无法重命名“" + e.name + "”。\n" + describeError(r));
            cv.invalidate();
            return;
        }
        String newFull = FmPath.join(e.dir, nn);
        refresh();
        int idx = folder.indexOfPath(newFull);
        if (idx >= 0) {
            folder.selectOnly(idx);
            list.ensureVisible(idx);
        }
        cv.invalidate();
    }

    /* Focus loss is only a hint: it is re-checked on the next tick so a transient loss never commits. */
    static void onRenameFocus(FocusEvent ev) {
        if (!ev.isFocusGained() && renaming != 0) {
            renBlurTick = tick;
        }
    }

    /* --------------------------------------------------------------- address edit */
    static void startAddrEdit() {
        cancelRename();
        editingAddr = 1;
        addrBlurTick = -1;
        String t = folder.path;
        if (folder.isSearch() != 0) {
            t = folder.searchRoot;
        } else if (folder.path.equals("::pc")) {
            t = "此电脑";
        } else if (folder.path.equals("::home")) {
            t = "主页";
        }
        addr.setText(t);
        addr.setBounds(addrX + 34, 8 + 5, addrW - 44, 22);
        addr.setVisible(true);
        addr.requestFocus();
        addr.selectAll();
        cv.invalidate();
    }

    static void cancelAddr() {
        if (editingAddr == 0) {
            return;
        }
        editingAddr = 0;
        addr.setVisible(false);
        cv.requestFocus();
        cv.invalidate();
    }

    static void commitAddr() {
        if (editingAddr == 0) {
            return;
        }
        String t = stripQuotes(addr.getText());
        editingAddr = 0;
        addr.setVisible(false);
        cv.requestFocus();
        if (t.length() == 0) {
            cv.invalidate();
            return;
        }
        if (t.equals("此电脑") || t.equalsIgnoreCase("this pc")) {
            navigateTo("::pc", 1, "");
            return;
        }
        if (t.equals("主页") || t.equalsIgnoreCase("home")) {
            navigateTo("::home", 1, "");
            return;
        }
        String full = FmNative.take(FmNative.fullPath(t));
        int k = FmNative.exists(full);
        if (k == 2) {
            navigateTo(full, 1, "");
        } else if (k == 1) {
            openPath(full, FmPath.parent(full));
            cv.invalidate();
        } else {
            int ec = FmNative.shellExec(t, "", "", folder.workDir());
            if (ec != 0) {
                err("Windows 找不到“" + t + "”。请检查拼写并重试。");
            }
            cv.invalidate();
        }
    }

    static void onAddrFocus(FocusEvent ev) {
        if (!ev.isFocusGained() && editingAddr != 0) {
            addrBlurTick = tick;
        }
    }

    /* --------------------------------------------------------------------- search */
    static void onSearchChanged() {
        if (muteSearch != 0) {
            return;
        }
        searchDirty = 1;
        searchDirtyTick = tick;
    }

    static void endSearch() {
        if (searchHandle > 0) {
            FmNative.searchEnd(searchHandle);
        }
        searchHandle = 0;
        searching = 0;
        searchDirty = 0;
    }

    static void runSearch() {
        searchDirty = 0;
        String q = sbox.getText().trim();
        String root = "";
        if (folder.isSearch() != 0) {
            root = folder.searchRoot;
        } else if (folder.isReal() != 0) {
            root = folder.path;
        } else {
            root = FmNative.take(FmNative.knownFolder(0));
        }
        if (q.length() == 0) {
            if (folder.isSearch() != 0) {
                leaveSearch();
            }
            return;
        }
        if (root.length() == 0) {
            return;
        }
        if (searchHandle > 0) {
            FmNative.searchEnd(searchHandle);
            searchHandle = 0;
        }
        cancelRename();
        int h = FmNative.searchBegin(root, q);
        if (h <= 0) {
            return;
        }
        searchHandle = h;
        searching = 1;
        folder.showHidden = showHiddenOpt;
        folder.beginSearch(root, q);
        list.configure();
        list.scroll = 0;
        win.setTitle(folderTitle());
        FmNative.watchSet("");
        cv.invalidate();
    }

    static void pollSearch() {
        if (searchHandle <= 0) {
            return;
        }
        String s = FmNative.take(FmNative.searchNext(searchHandle, 30));
        int done = folder.appendSearch(s);
        if (s.length() > 0) {
            folder.rebuild();
        }
        if (done != 0) {
            FmNative.searchEnd(searchHandle);
            searchHandle = 0;
            searching = 0;
        }
        cv.invalidate();
    }

    static void leaveSearch() {
        String root = folder.searchRoot;
        endSearch();
        sbMute("");
        if (root.length() > 0) {
            navigateTo(root, 0, "");
        }
    }

    /* ----------------------------------------------------------------------- tick */
    static void onTick() {
        tick = tick + 1;
        if (renBlurTick >= 0 && tick > renBlurTick + 1) {
            renBlurTick = -1;
            if (renaming != 0 && tick - renStartTick >= 3 && !ren.hasFocus()) {
                commitRename();
            }
        }
        if (addrBlurTick >= 0 && tick > addrBlurTick + 1) {
            addrBlurTick = -1;
            if (editingAddr != 0 && !addr.hasFocus()) {
                cancelAddr();
            }
        }
        if (searchHandle > 0) {
            pollSearch();
        }
        if (searchDirty != 0 && tick - searchDirtyTick >= 8) {
            runSearch();
        }
        if (tick % 10 == 0) {
            if (folder.isReal() != 0 && FmNative.watchPoll() != 0) {
                refreshAt = tick + 4;
            }
            refreshClip();
        }
        if (refreshAt >= 0 && tick >= refreshAt) {
            if (renaming == 0 && list.dragMode == 0 && editingAddr == 0) {
                refreshAt = -1;
                refresh();
            } else {
                refreshAt = tick + 10;
            }
        }
        if (tick % 100 == 0 && themeMode == 0) {
            int d = isDark();
            if (d != Ft.dark) {
                applyTheme();
                cv.invalidate();
            }
        }
        if (tick % 200 == 0 && folder.path.equals("::pc")) {
            loadInto("::pc", null, "");
            cv.invalidate();
        }
    }

    /* ----------------------------------------------------------------------- paint */
    static void paint() {
        Ft.initFonts();
        FmHit.reset();
        W = win.width;
        H = win.height;
        Gfx.fill(0, 0, W, H, Ft.bg);
        FmChrome.paintNavRow(W);
        FmChrome.paintCommandBar(W);
        bodyY = Ft.NAV_H + Ft.CMD_H;
        /* body panel with rounded top corners (the other corners are off screen) */
        Gfx.roundStroke(-10, bodyY, W + 20, H - bodyY + 20, 8, Ft.panel, Ft.panelLine, 10);
        int bodyH = H - bodyY;
        int listX = navW + 1;
        int listW = W - listX;
        int listH = bodyH - Ft.STATUS_H;
        FmChrome.paintNavPane(0, bodyY + 2, navW, listH - 2);
        Gfx.fill(navW, bodyY + 1, 1, bodyH - 1, Ft.panelLine);
        FmHit.add(navW - 3, bodyY, 7, bodyH, FmId.SPLITTER, 0);
        list.x = listX;
        list.y = bodyY + 2;
        list.w = listW;
        list.h = listH - 2;
        list.paint();
        FmChrome.paintStatus(listX, H - Ft.STATUS_H, listW, Ft.STATUS_H);
        paintOverlays();
        if (list.dragMode == 2) {
            paintDragGhost();
        }
    }

    static void paintOverlays() {
        if (renaming != 0 && renEntry != null) {
            int idx = folder.indexOfPath(renEntry.full);
            if (idx >= 0) {
                list.nameRect(idx);
                if (list.rx != renX || list.ry != renY || list.rw != renW) {
                    renX = list.rx;
                    renY = list.ry;
                    renW = list.rw;
                    ren.setBounds(list.rx + 2, list.ry + 3, list.rw - 4, list.rh - 6);
                }
                Gfx.roundStroke(list.rx, list.ry, list.rw, list.rh, 3, Ft.editBg, Ft.accent, 15);
            }
        }
    }

    static void paintDragGhost() {
        int n = folder.selCount();
        String t = String.valueOf(n) + " 个项目";
        if (n == 1) {
            t = displayName(folder.selectedEntries().get(0));
        }
        String how = "移动";
        if (Gfx.keyDown(17)) {
            how = "复制";
        }
        int tw = Gfx.textWidth(Ft.fBody, t) + 20;
        int gx = list.mx + 14;
        int gy = list.my + 14;
        if (gx + tw > W) {
            gx = W - tw - 4;
        }
        Gfx.roundStroke(gx, gy, tw, 26, 4, Ft.editBg, Ft.accent, 10);
        Gfx.text(t, gx + 10, gy, tw - 12, 26, Ft.text, Ft.fBody, 4 + 8);
        String target = "";
        if (list.dropTarget >= 0 && list.dropTarget < folder.rows.size()) {
            target = displayName(folder.rows.get(list.dropTarget));
        } else if (FmChrome.navDrop >= 0 && FmChrome.navDrop < nav.vis.size()) {
            target = nav.vis.get(FmChrome.navDrop).label;
        }
        if (target.length() > 0) {
            String m = how + "到 " + target;
            int mw = Gfx.textWidth(Ft.fSmall, m) + 16;
            Gfx.roundStroke(gx, gy + 30, mw, 22, 4, Ft.editBg, Ft.line, 10);
            Gfx.text(m, gx + 8, gy + 30, mw - 8, 22, Ft.sub, Ft.fSmall, 4 + 8);
        }
    }

    /* ----------------------------------------------------------------------- mouse */
    static int inList(int x, int y) {
        if (x >= list.x && x < list.x + list.w && y >= list.y && y < list.y + list.h) {
            return 1;
        }
        return 0;
    }

    static int inNav(int x, int y) {
        if (x >= 0 && x < navW && y >= bodyY && y < H - Ft.STATUS_H) {
            return 1;
        }
        return 0;
    }

    static void onMouse(MouseEvent ev) {
        int t = ev.eventType;
        int x = ev.x;
        int y = ev.y;
        FmChrome.mx = x;
        FmChrome.my = y;
        if (t == 5) {
            if (splitDrag != 0) {
                int nw = x;
                if (nw < 140) {
                    nw = 140;
                }
                if (nw > W / 2) {
                    nw = W / 2;
                }
                navW = nw;
                Gfx.cursor(2);
                cv.invalidate();
                return;
            }
            if (list.dragMode != 0) {
                list.onMove(x, y, ev.button);
                if (list.dragMode == 2) {
                    FmChrome.navDrop = -1;
                    if (inNav(x, y) != 0) {
                        int ni = FmChrome.navIndexAt(x, y, 0, bodyY + 2, navW);
                        if (ni >= 0) {
                            FmNode nd = nav.vis.get(ni);
                            if (nd.path.length() > 0 && !nd.path.startsWith("::")) {
                                FmChrome.navDrop = ni;
                            }
                        }
                    }
                }
                cv.invalidate();
                return;
            }
            if (x >= navW - 3 && x <= navW + 3 && y >= bodyY && y < H - Ft.STATUS_H) {
                Gfx.cursor(2);
            }
            if (inList(x, y) != 0) {
                list.onMove(x, y, ev.button);
            } else {
                list.mx = x;
                list.my = y;
                list.hover = -1;
                list.hoverBar = 0;
            }
            cv.invalidate();
            return;
        }
        if (t == 7) {
            FmChrome.mx = -100;
            FmChrome.my = -100;
            list.onLeave();
            cv.invalidate();
            return;
        }
        if (t == 8) {
            if (inNav(x, y) != 0) {
                FmChrome.navScroll = FmChrome.navScroll - (ev.button * 56) / 120;
            } else if (Gfx.keyDown(17)) {
                if (ev.button > 0) {
                    setViewMode(1);
                } else {
                    setViewMode(0);
                }
            } else {
                list.onWheel(ev.button);
            }
            cv.invalidate();
            return;
        }
        if (t == 2) {
            if (splitDrag != 0) {
                splitDrag = 0;
                cv.invalidate();
                return;
            }
            if (list.dragMode != 0) {
                int dm = list.dragMode;
                int res = list.onUp(x, y, ev.button);
                if (res == 4) {
                    sortColumn(pendingSort);
                } else if (res == 6) {
                    dropItems(x, y);
                }
                FmChrome.navDrop = -1;
                list.dropTarget = -1;
                list.dragMode = 0;
                cv.invalidate();
                if (dm != 0 && ev.button == 2) {
                    return;
                }
            }
            if (ev.button == 2 && downBtn == 2) {
                downBtn = 0;
                if (ctxKind == 1) {
                    showItemMenu();
                } else if (ctxKind == 2) {
                    showBackgroundMenu();
                }
                ctxKind = 0;
                cv.invalidate();
            }
            return;
        }
        if (t != 1 && t != 4) {
            return;
        }
        if (renaming != 0) {
            commitRename();
        }
        int ctrl = 0;
        int shift = 0;
        if (Gfx.keyDown(17)) {
            ctrl = 1;
        }
        if (Gfx.keyDown(16)) {
            shift = 1;
        }
        if (editingAddr != 0) {
            int hi0 = FmHit.at(x, y);
            if (!(x >= addrX && x < addrX + addrW && y >= 8 && y < 40)) {
                cancelAddr();
            } else if (hi0 < 0) {
                return;
            }
        }
        downBtn = ev.button;
        cv.requestFocus();
        if (ev.button == 1 && x >= navW - 3 && x <= navW + 3 && y >= bodyY && y < H - Ft.STATUS_H) {
            splitDrag = 1;
            return;
        }
        int hi = FmHit.at(x, y);
        if (hi >= 0 && ev.button == 1) {
            int id = FmHit.hid[hi];
            int data = FmHit.hdata[hi];
            if (id == FmId.SPLITTER) {
                splitDrag = 1;
                return;
            }
            if (!(inList(x, y) != 0 && id == FmId.NAV_ITEM)) {
                /* a rapid second click arrives as a double-click event: treat it as another click */
                onHit(id, data, x, y);
                cv.invalidate();
                return;
            }
        }
        if (inList(x, y) != 0) {
            if (t == 4) {
                int i = list.itemAt(x, y);
                if (i >= 0 && ev.button == 1) {
                    list.cancelDrag();
                    openEntry(folder.rows.get(i));
                    cv.invalidate();
                }
                return;
            }
            int res = list.onDown(x, y, ev.button, ctrl, shift);
            if (res == 2) {
                ctxKind = 1;
            } else if (res == 3) {
                ctxKind = 2;
            } else {
                ctxKind = 0;
            }
            cv.invalidate();
            return;
        }
        if (inNav(x, y) != 0 && hi >= 0 && t == 4 && ev.button == 1) {
            return;
        }
    }

    static void onHit(int id, int data, int x, int y) {
        if (id == FmId.BACK) {
            goBack();
        } else if (id == FmId.FORWARD) {
            goForward();
        } else if (id == FmId.UP) {
            goUp();
        } else if (id == FmId.REFRESH) {
            nav.reload();
            refresh();
        } else if (id == FmId.ADDR_BG) {
            startAddrEdit();
        } else if (id == FmId.CRUMB) {
            if (data < FmChrome.crumbN) {
                String p = FmChrome.crumbPath[data];
                if (folder.isSearch() != 0 && FmPath.samePath(p, folder.searchRoot) != 0) {
                    leaveSearch();
                } else {
                    navigateTo(p, 1, "");
                }
            }
        } else if (id == FmId.CRUMB_ARROW) {
            if (data < FmChrome.crumbN) {
                showCrumbMenu(FmChrome.crumbPath[data]);
            }
        } else if (id == FmId.NAV_ITEM) {
            FmNode nd = nav.vis.get(data);
            if (nd.path.length() > 0) {
                navigateTo(nd.path, 1, "");
            }
        } else if (id == FmId.NAV_ARROW) {
            nav.toggle(nav.vis.get(data));
        } else if (id == FmId.VIEW_DETAILS) {
            setViewMode(0);
        } else if (id == FmId.VIEW_GRID) {
            setViewMode(1);
        } else if (id == FmId.CMD_NEW) {
            showNewMenu();
        } else if (id == FmId.CMD_CUT) {
            doAction(5);
        } else if (id == FmId.CMD_COPY) {
            doAction(6);
        } else if (id == FmId.CMD_PASTE) {
            doAction(7);
        } else if (id == FmId.CMD_RENAME) {
            doAction(10);
        } else if (id == FmId.CMD_DELETE) {
            doAction(11);
        } else if (id == FmId.CMD_SORT) {
            showSortMenu();
        } else if (id == FmId.CMD_VIEW) {
            showViewMenu();
        } else if (id == FmId.CMD_MORE) {
            showMoreMenu();
        }
    }

    static void setViewMode(int m) {
        list.mode = m;
        list.configure();
        list.scroll = 0;
        cv.invalidate();
        saveSettings();
    }

    static void saveSettings() {
        if (testMode == 0) {
            FmSettings.save();
        }
    }

    /** mode: 0 follow system, 1 light, 2 dark */
    static void setThemeMode(int mode) {
        themeMode = mode;
        applyTheme();
        saveSettings();
        cv.invalidate();
    }

    static void setAccent(int idx) {
        Ft.accentIdx = idx;
        applyTheme();
        saveSettings();
        cv.invalidate();
    }

    /** Ctrl+Shift+T: follow system -> light -> dark -> follow system */
    static void cycleTheme() {
        setThemeMode((themeMode + 1) % 3);
    }

    static void sortColumn(int colId) {
        if (colId < 0) {
            return;
        }
        if (folder.path.equals("::pc") || folder.path.equals("::home")) {
            return;
        }
        folder.sortBy(colId);
        list.ensureVisible(folder.focus);
        cv.invalidate();
    }

    static void dropItems(int x, int y) {
        String target = "";
        if (FmChrome.navDrop >= 0 && FmChrome.navDrop < nav.vis.size()) {
            target = nav.vis.get(FmChrome.navDrop).path;
        } else if (list.dropTarget >= 0 && list.dropTarget < folder.rows.size()) {
            target = folder.rows.get(list.dropTarget).full;
        }
        if (target.length() == 0 || selectionIsFileSystem() == 0) {
            return;
        }
        int move = 1;
        if (Gfx.keyDown(17)) {
            move = 0;
        }
        transfer(folder.selectedPaths(), target, move, 0);
    }

    /* ------------------------------------------------------------------------ keys */
    static int textFocus() {
        if (renaming != 0 || editingAddr != 0) {
            return 1;
        }
        if (sbox.hasFocus()) {
            return 1;
        }
        return 0;
    }

    static void onKey(KeyEvent ev) {
        if (ev == null) {
            return;
        }
        if (ev.rawMods >= 16) {
            return;
        }
        int vk = ev.keyCode;
        int mods = ev.rawMods;
        int shift = mods % 2;
        int ctrl = (mods / 2) % 2;
        int alt = (mods / 4) % 2;
        if (renaming != 0) {
            if (vk == 13) {
                commitRename();
            } else if (vk == 27) {
                cancelRename();
            } else if (ctrl != 0 && vk == 65) {
                ren.selectAll();
            }
            return;
        }
        if (editingAddr != 0) {
            if (vk == 13) {
                commitAddr();
            } else if (vk == 27) {
                cancelAddr();
            } else if (ctrl != 0 && vk == 65) {
                addr.selectAll();
            }
            return;
        }
        if (sbox.hasFocus()) {
            if (vk == 13) {
                runSearch();
            } else if (vk == 27) {
                if (sbox.getText().length() > 0) {
                    sbMute("");
                    if (folder.isSearch() != 0) {
                        leaveSearch();
                    }
                }
                cv.requestFocus();
            } else if (vk == 40) {
                cv.requestFocus();
            } else if (ctrl != 0 && vk == 65) {
                sbox.selectAll();
            } else if (ctrl != 0 && (vk == 76 || vk == 68)) {
                startAddrEdit();
            }
            return;
        }
        if (alt != 0) {
            if (vk == 37) {
                goBack();
            } else if (vk == 39) {
                goForward();
            } else if (vk == 38) {
                goUp();
            } else if (vk == 68) {
                startAddrEdit();
            } else if (vk == 13) {
                showProperties();
            }
            return;
        }
        if (ctrl != 0) {
            if (vk == 65) {
                folder.selectAllRows();
            } else if (vk == 67) {
                if (shift != 0) {
                    copyPathText();
                } else {
                    clipSelection(0);
                }
            } else if (vk == 88) {
                clipSelection(1);
            } else if (vk == 86) {
                doPaste("");
            } else if (vk == 76) {
                startAddrEdit();
            } else if (vk == 69 || vk == 70) {
                sbox.requestFocus();
            } else if (vk == 78) {
                if (shift != 0) {
                    newItem(1);
                } else {
                    newWindow();
                }
            } else if (vk == 87) {
                win.close();
            } else if (vk == 82) {
                nav.reload();
                refresh();
            } else if (vk == 54 && shift != 0) {
                setViewMode(0);
            } else if (vk == 50 && shift != 0) {
                setViewMode(1);
            } else if (vk == 84 && shift != 0) {
                cycleTheme();
            } else {
                list.onKey(vk, mods);
            }
            cv.invalidate();
            return;
        }
        if (vk == 116) {
            nav.reload();
            refresh();
        } else if (vk == 113) {
            startRename();
        } else if (vk == 115) {
            startAddrEdit();
        } else if (vk == 46) {
            doDelete(shift);
        } else if (vk == 13) {
            openSelection();
        } else if (vk == 8) {
            goBack();
        } else if (vk == 121 && shift != 0) {
            moreOptions(1);
        } else if (vk == 27) {
            if (folder.isSearch() != 0) {
                leaveSearch();
            } else {
                folder.clearSel();
            }
        } else if (list.onKey(vk, mods) != 0) {
            cv.invalidate();
            return;
        } else if (ev.typedChar >= 33 && ev.typedChar < 127) {
            list.typeAhead(ev.typedChar, tick);
            list.ensureVisible(folder.focus);
        } else if (ev.typedChar == 32 && folder.focus >= 0 && ctrl == 0) {
            /* space toggles nothing without ctrl in Explorer (it selects the focused item) */
            folder.selectOnly(folder.focus);
        }
        cv.invalidate();
    }

    /* ------------------------------------------------------------------------ menus */
    static MenuItem mi(Menu m, String label, int act, int enabled) {
        MenuItem it = new MenuItem(label, () -> {
            FmApp.doAction(act);
        });
        if (enabled == 0) {
            it.setEnabled(false);
        }
        m.add(it);
        return it;
    }

    static void doAction(int a) {
        if (a == 1) {
            openSelection();
        } else if (a == 2) {
            ArrayList<FmEntry> sel = folder.selectedEntries();
            if (sel.size() > 0) {
                int r = FmNative.spawnSelf(sel.get(0).full);
                if (r != 0) {
                    err("无法打开新窗口。\n" + describeError(r));
                }
            }
        } else if (a == 3) {
            ArrayList<FmEntry> sel = folder.selectedEntries();
            if (sel.size() > 0) {
                int r = FmNative.shellExec(sel.get(0).full, "runas", "", sel.get(0).dir);
                if (r != 0 && r != 1223) {
                    err(describeError(r));
                }
            }
        } else if (a == 4) {
            ArrayList<FmEntry> sel = folder.selectedEntries();
            if (sel.size() > 0) {
                FmNative.shellExec(sel.get(0).full, "openas", "", sel.get(0).dir);
            }
        } else if (a == 5) {
            clipSelection(1);
        } else if (a == 6) {
            clipSelection(0);
        } else if (a == 7) {
            doPaste("");
        } else if (a == 8) {
            ArrayList<FmEntry> sel = folder.selectedEntries();
            if (sel.size() == 1 && sel.get(0).isDir != 0) {
                doPaste(sel.get(0).full);
            }
        } else if (a == 9) {
            copyPathText();
        } else if (a == 10) {
            startRename();
        } else if (a == 11) {
            doDelete(0);
        } else if (a == 12) {
            showProperties();
        } else if (a == 13) {
            moreOptions(0);
        } else if (a == 14) {
            nav.reload();
            refresh();
        } else if (a == 15) {
            newItem(1);
        } else if (a == 16) {
            newItem(0);
        } else if (a == 17) {
            folder.selectAllRows();
        } else if (a == 18) {
            folder.clearSel();
        } else if (a == 19) {
            folder.invertSel();
        } else if (a == 20) {
            setViewMode(0);
        } else if (a == 21) {
            setViewMode(1);
        } else if (a >= 30 && a <= 33) {
            folder.sortCol = a - 30;
            folder.rebuild();
        } else if (a == 34) {
            folder.sortAsc = 1;
            folder.rebuild();
        } else if (a == 35) {
            folder.sortAsc = 0;
            folder.rebuild();
        } else if (a == 36) {
            showHiddenOpt = 1 - showHiddenOpt;
            nav.showHidden = showHiddenOpt;
            nav.reload();
            refresh();
            saveSettings();
        } else if (a == 37) {
            showExt = 1 - showExt;
            saveSettings();
        } else if (a == 38) {
            newWindow();
        } else if (a >= 40 && a <= 42) {
            setThemeMode(a - 40);
        } else if (a >= 50 && a < 50 + Ft.ACCENTS) {
            setAccent(a - 50);
        }
        cv.invalidate();
    }

    static void showItemMenu() {
        ArrayList<FmEntry> sel = folder.selectedEntries();
        if (sel.size() == 0) {
            return;
        }
        FmEntry first = sel.get(0);
        int fs = selectionIsFileSystem();
        ContextMenu m = new ContextMenu();
        mi(m, "打开", 1, 1);
        if (sel.size() == 1 && first.isDir != 0) {
            mi(m, "在新窗口中打开", 2, 1);
        }
        if (sel.size() == 1 && first.isDir == 0 && (first.ext.equals("exe") || first.ext.equals("bat") || first.ext.equals("cmd") || first.ext.equals("msi"))) {
            mi(m, "以管理员身份运行", 3, 1);
        }
        if (sel.size() == 1 && first.isDir == 0) {
            mi(m, "打开方式…", 4, 1);
        }
        m.addSeparator();
        mi(m, "剪切 (Ctrl+X)", 5, fs);
        mi(m, "复制 (Ctrl+C)", 6, fs);
        if (sel.size() == 1 && first.isDir != 0 && clipState > 0) {
            mi(m, "粘贴 (Ctrl+V)", 8, 1);
        }
        mi(m, "复制文件地址 (Ctrl+Shift+C)", 9, 1);
        if (sel.size() == 1) {
            mi(m, "重命名 (F2)", 10, fs);
        }
        mi(m, "删除 (Del)", 11, fs);
        m.addSeparator();
        mi(m, "属性 (Alt+Enter)", 12, 1);
        m.addSeparator();
        mi(m, "显示更多选项 (Shift+F10)", 13, 1);
        m.showAt(0, 0);
    }

    static void addSortMenu(Menu parent) {
        Menu sm = new Menu("排序方式");
        String[] names = new String[4];
        names[0] = "名称";
        names[1] = "修改日期";
        names[2] = "类型";
        names[3] = "大小";
        int i = 0;
        while (i < 4) {
            MenuItem it = mi(sm, names[i], 30 + i, 1);
            it.setChecked(folder.sortCol == i);
            i = i + 1;
        }
        sm.addSeparator();
        MenuItem up = mi(sm, "递增", 34, 1);
        up.setChecked(folder.sortAsc != 0);
        MenuItem dn = mi(sm, "递减", 35, 1);
        dn.setChecked(folder.sortAsc == 0);
        parent.addMenu(sm);
    }

    static void addViewMenu(Menu parent) {
        Menu vm = new Menu("查看");
        MenuItem d = mi(vm, "详细信息 (Ctrl+Shift+6)", 20, 1);
        d.setChecked(list.mode == 0);
        MenuItem g = mi(vm, "图标 (Ctrl+Shift+2)", 21, 1);
        g.setChecked(list.mode == 1);
        parent.addMenu(vm);
    }

    static void showBackgroundMenu() {
        ContextMenu m = new ContextMenu();
        addViewMenu(m);
        if (folder.path.length() > 0 && !folder.path.equals("::pc") && !folder.path.equals("::home")) {
            addSortMenu(m);
        }
        mi(m, "刷新", 14, 1);
        m.addSeparator();
        int real = folder.isReal();
        int canPaste = 0;
        if (real != 0 && clipState > 0) {
            canPaste = 1;
        }
        mi(m, "粘贴 (Ctrl+V)", 7, canPaste);
        m.addSeparator();
        if (real != 0) {
            Menu nm = new Menu("新建");
            mi(nm, "文件夹", 15, 1);
            mi(nm, "文本文档", 16, 1);
            m.addMenu(nm);
            m.addSeparator();
            mi(m, "全选 (Ctrl+A)", 17, 1);
            mi(m, "在新窗口中打开", 38, 1);
            mi(m, "属性", 12, 1);
        } else {
            mi(m, "全选 (Ctrl+A)", 17, 1);
        }
        m.showAt(0, 0);
    }

    static void showNewMenu() {
        ContextMenu m = new ContextMenu();
        mi(m, "文件夹", 15, 1);
        mi(m, "文本文档", 16, 1);
        m.showAt(0, 0);
    }

    static void showSortMenu() {
        ContextMenu m = new ContextMenu();
        String[] names = new String[4];
        names[0] = "名称";
        names[1] = "修改日期";
        names[2] = "类型";
        names[3] = "大小";
        int i = 0;
        while (i < 4) {
            MenuItem it = mi(m, names[i], 30 + i, 1);
            it.setChecked(folder.sortCol == i);
            i = i + 1;
        }
        m.addSeparator();
        MenuItem up = mi(m, "递增", 34, 1);
        up.setChecked(folder.sortAsc != 0);
        MenuItem dn = mi(m, "递减", 35, 1);
        dn.setChecked(folder.sortAsc == 0);
        m.showAt(0, 0);
    }

    static void showViewMenu() {
        ContextMenu m = new ContextMenu();
        MenuItem d = mi(m, "详细信息 (Ctrl+Shift+6)", 20, 1);
        d.setChecked(list.mode == 0);
        MenuItem g = mi(m, "图标 (Ctrl+Shift+2)", 21, 1);
        g.setChecked(list.mode == 1);
        m.addSeparator();
        MenuItem h = mi(m, "显示隐藏的项目", 36, 1);
        h.setChecked(showHiddenOpt != 0);
        MenuItem x = mi(m, "文件扩展名", 37, 1);
        x.setChecked(showExt != 0);
        m.showAt(0, 0);
    }

    static void showMoreMenu() {
        ContextMenu m = new ContextMenu();
        mi(m, "全选 (Ctrl+A)", 17, 1);
        mi(m, "全部不选", 18, 1);
        mi(m, "反向选择", 19, 1);
        m.addSeparator();
        mi(m, "刷新 (F5)", 14, 1);
        mi(m, "在新窗口中打开 (Ctrl+N)", 38, 1);
        m.addSeparator();
        MenuItem h = mi(m, "显示隐藏的项目", 36, 1);
        h.setChecked(showHiddenOpt != 0);
        MenuItem x = mi(m, "文件扩展名", 37, 1);
        x.setChecked(showExt != 0);
        m.addSeparator();
        addThemeMenu(m);
        m.showAt(0, 0);
    }

    /** "主题" (follow system / light / dark) and "强调色" sub menus. */
    static void addThemeMenu(Menu parent) {
        Menu tm = new Menu("主题 (Ctrl+Shift+T)");
        MenuItem t0 = mi(tm, "跟随系统", 40, 1);
        t0.setChecked(themeMode == 0);
        MenuItem t1 = mi(tm, "浅色", 41, 1);
        t1.setChecked(themeMode == 1);
        MenuItem t2 = mi(tm, "深色", 42, 1);
        t2.setChecked(themeMode == 2);
        parent.addMenu(tm);
        Menu am = new Menu("强调色");
        int i = 0;
        while (i < Ft.ACCENTS) {
            MenuItem ai = mi(am, Ft.accentName(i), 50 + i, 1);
            ai.setChecked(Ft.accentIdx == i);
            i = i + 1;
        }
        parent.addMenu(am);
    }

    static void showCrumbMenu(String p) {
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<String> paths = new ArrayList<String>();
        if (p.equals("::pc")) {
            int i = 0;
            while (i < nav.pc.kids.size()) {
                names.add(nav.pc.kids.get(i).label);
                paths.add(nav.pc.kids.get(i).path);
                i = i + 1;
            }
        } else if (!p.startsWith("::")) {
            String tsv = FmNative.take(FmNative.list(p));
            ArrayList<FmEntry> dirs = new ArrayList<FmEntry>();
            if (tsv.length() > 0 && !tsv.startsWith("!")) {
                String[] lines = tsv.split("\n");
                int i = 0;
                while (i < lines.length) {
                    String[] f = lines[i].split("\t");
                    i = i + 1;
                    if (f.length < 6 || !f[0].equals("D")) {
                        continue;
                    }
                    FmEntry e = new FmEntry();
                    e.isDir = 1;
                    e.name = f[1];
                    e.full = FmPath.join(p, e.name);
                    e.attrs = (int) FmFmt.toLong(f[2]);
                    e.key = e.name.toLowerCase();
                    if (showHiddenOpt == 0 && e.hidden() != 0) {
                        continue;
                    }
                    dirs.add(e);
                }
            }
            FmFolder.mergeSort(dirs, 0, 1);
            int i = 0;
            while (i < dirs.size() && i < 40) {
                names.add(dirs.get(i).name);
                paths.add(dirs.get(i).full);
                i = i + 1;
            }
        }
        if (names.size() == 0) {
            return;
        }
        ContextMenu m = new ContextMenu();
        int i = 0;
        while (i < names.size()) {
            String target = paths.get(i);
            MenuItem it = new MenuItem(names.get(i), () -> {
                FmApp.navigateTo(target, 1, "");
            });
            m.add(it);
            i = i + 1;
        }
        m.showAt(0, 0);
    }
}
