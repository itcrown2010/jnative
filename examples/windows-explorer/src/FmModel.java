/**
 * JNative Windows Explorer - folder model (listing, sort, filter, selection) and the navigation tree.
 * No canvas calls in this file; everything is covered by the headless tests.
 *
 * @author 张晓达
 */
class FmCol {
    /* 0 name, 1 date modified, 2 type, 3 size, 4 folder (search), 5 total size, 6 free space */
    int id;
    String title;
    int w;
    int minW;
    int right;

    FmCol(int cid, String t, int width, int alignRight) {
        id = cid;
        title = t;
        w = width;
        minW = 48;
        right = alignRight;
    }
}

class FmFolder {
    /* "" nothing, "::home", "::pc", a real directory, or "::search" */
    String path;
    String searchRoot;
    String searchQuery;
    int err;
    ArrayList<FmEntry> all;
    ArrayList<FmEntry> rows;
    int sortCol;
    int sortAsc;
    int showHidden;
    int focus;
    int anchor;
    FmEntry focusEntry;
    FmEntry anchorEntry;
    static HashMap<String, String> typeCache = new HashMap<String, String>();

    FmFolder() {
        path = "";
        searchRoot = "";
        searchQuery = "";
        err = 0;
        all = new ArrayList<FmEntry>();
        rows = new ArrayList<FmEntry>();
        sortCol = 0;
        sortAsc = 1;
        showHidden = 0;
        focus = -1;
        anchor = -1;
        focusEntry = null;
        anchorEntry = null;
    }

    int isReal() {
        if (path.length() == 0 || path.startsWith("::")) {
            return 0;
        }
        return 1;
    }

    int isSearch() {
        if (path.equals("::search")) {
            return 1;
        }
        return 0;
    }

    /** Real directory where new items would go (search results have none). */
    String workDir() {
        if (isReal() != 0) {
            return path;
        }
        return "";
    }

    static String typeFor(String ext, int isDir) {
        String key = "d";
        if (isDir == 0) {
            key = "." + ext;
        }
        String t = typeCache.get(key);
        if (t != null) {
            return t;
        }
        if (isDir != 0) {
            t = FmNative.take(FmNative.typeName("folder", 1));
            if (t.length() == 0) {
                t = "文件夹";
            }
        } else if (ext.length() == 0) {
            t = FmNative.take(FmNative.typeName("noextension", 0));
            if (t.length() == 0) {
                t = "文件";
            }
        } else {
            t = FmNative.take(FmNative.typeName("x." + ext, 0));
            if (t.length() == 0) {
                t = ext.toUpperCase() + " 文件";
            }
        }
        typeCache.put(key, t);
        return t;
    }

    /** Builds an entry from one listing record (fields already split). */
    static FmEntry fromRecord(String[] f, String dir) {
        if (f.length < 6) {
            return null;
        }
        FmEntry e = new FmEntry();
        e.isDir = 0;
        if (f[0].equals("D")) {
            e.isDir = 1;
        }
        e.name = f[1];
        e.dir = dir;
        e.full = FmPath.join(dir, e.name);
        e.attrs = (int) FmFmt.toLong(f[2]);
        e.size = FmFmt.toLong(f[3]);
        e.mtime = FmFmt.toLong(f[4]);
        e.ctime = FmFmt.toLong(f[5]);
        if (f.length > 6) {
            e.mtimeText = f[6];
        }
        e.key = e.name.toLowerCase();
        if (e.isDir == 0) {
            e.ext = FmPath.extOf(e.name);
        }
        e.type = typeFor(e.ext, e.isDir);
        return e;
    }

    /** Parses a jn_fm_list result into `all`. Returns the error code (0 = ok). */
    int parseListing(String tsv, String dir) {
        all.clear();
        err = 0;
        if (tsv.length() == 0) {
            return 0;
        }
        String[] lines = tsv.split("\n");
        int i = 0;
        while (i < lines.length) {
            String ln = lines[i];
            i = i + 1;
            if (ln.length() == 0) {
                continue;
            }
            String[] f = ln.split("\t");
            if (f.length >= 2 && f[0].equals("!")) {
                err = (int) FmFmt.toLong(f[1]);
                return err;
            }
            FmEntry e = fromRecord(f, dir);
            if (e != null) {
                all.add(e);
            }
        }
        return 0;
    }

    /** Appends search records (list record + parent dir). Returns 1 once "#done" was seen. */
    int appendSearch(String tsv) {
        int done = 0;
        if (tsv.length() == 0) {
            return 0;
        }
        String[] lines = tsv.split("\n");
        int i = 0;
        while (i < lines.length) {
            String ln = lines[i];
            i = i + 1;
            if (ln.length() == 0) {
                continue;
            }
            if (ln.equals("#done")) {
                done = 1;
                continue;
            }
            String[] f = ln.split("\t");
            if (f.length < 8) {
                continue;
            }
            FmEntry e = fromRecord(f, f[7]);
            if (e != null) {
                all.add(e);
            }
        }
        return done;
    }

    void keepSelection(ArrayList<String> sel, String focusFull) {
        int i = 0;
        while (i < all.size()) {
            FmEntry e = all.get(i);
            e.selected = 0;
            i = i + 1;
        }
        if (sel == null) {
            return;
        }
        HashMap<String, String> set = new HashMap<String, String>();
        i = 0;
        while (i < sel.size()) {
            set.put(sel.get(i).toLowerCase(), "1");
            i = i + 1;
        }
        i = 0;
        while (i < all.size()) {
            FmEntry e = all.get(i);
            if (set.get(e.full.toLowerCase()) != null) {
                e.selected = 1;
            }
            if (focusFull.length() > 0 && e.full.equalsIgnoreCase(focusFull)) {
                focusEntry = e;
            }
            i = i + 1;
        }
    }

    ArrayList<String> selectedPaths() {
        ArrayList<String> r = new ArrayList<String>();
        int i = 0;
        while (i < rows.size()) {
            FmEntry e = rows.get(i);
            if (e.selected != 0) {
                r.add(e.full);
            }
            i = i + 1;
        }
        return r;
    }

    ArrayList<FmEntry> selectedEntries() {
        ArrayList<FmEntry> r = new ArrayList<FmEntry>();
        int i = 0;
        while (i < rows.size()) {
            FmEntry e = rows.get(i);
            if (e.selected != 0) {
                r.add(e);
            }
            i = i + 1;
        }
        return r;
    }

    int selCount() {
        int n = 0;
        int i = 0;
        while (i < rows.size()) {
            if (rows.get(i).selected != 0) {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    /** Total bytes of the selected files (-1 when the selection has no file). */
    long selBytes() {
        long t = 0;
        int files = 0;
        int i = 0;
        while (i < rows.size()) {
            FmEntry e = rows.get(i);
            if (e.selected != 0 && e.isDir == 0 && e.size >= 0) {
                t = t + e.size;
                files = files + 1;
            }
            i = i + 1;
        }
        if (files == 0) {
            return -1;
        }
        return t;
    }

    void clearSel() {
        int i = 0;
        while (i < rows.size()) {
            rows.get(i).selected = 0;
            i = i + 1;
        }
    }

    void selectOnly(int idx) {
        clearSel();
        if (idx >= 0 && idx < rows.size()) {
            rows.get(idx).selected = 1;
            focus = idx;
            anchor = idx;
            focusEntry = rows.get(idx);
            anchorEntry = focusEntry;
        }
    }

    void selectRange(int a, int b, int additive) {
        if (additive == 0) {
            clearSel();
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        if (lo < 0) {
            lo = 0;
        }
        if (hi >= rows.size()) {
            hi = rows.size() - 1;
        }
        int i = lo;
        while (i <= hi) {
            rows.get(i).selected = 1;
            i = i + 1;
        }
    }

    void selectAllRows() {
        int i = 0;
        while (i < rows.size()) {
            rows.get(i).selected = 1;
            i = i + 1;
        }
    }

    void invertSel() {
        int i = 0;
        while (i < rows.size()) {
            FmEntry e = rows.get(i);
            if (e.selected != 0) {
                e.selected = 0;
            } else {
                e.selected = 1;
            }
            i = i + 1;
        }
    }

    void setFocus(int idx) {
        focus = idx;
        if (idx >= 0 && idx < rows.size()) {
            focusEntry = rows.get(idx);
        } else {
            focusEntry = null;
        }
    }

    int indexOfPath(String full) {
        int i = 0;
        while (i < rows.size()) {
            if (rows.get(i).full.equalsIgnoreCase(full)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    /* ------------------------------------------------------------- sort/filter */
    static int cmp(FmEntry a, FmEntry b, int col, int asc) {
        if (a.isDir != b.isDir) {
            if (a.isDir != 0) {
                return -1;
            }
            return 1;
        }
        int r = 0;
        if (col == 1) {
            r = FmFmt.cmpLong(a.mtime, b.mtime);
        } else if (col == 2) {
            r = FmFmt.natCmp(a.type.toLowerCase(), b.type.toLowerCase());
        } else if (col == 3) {
            if (a.isDir == 0) {
                r = FmFmt.cmpLong(a.size, b.size);
            }
        } else if (col == 4) {
            r = FmFmt.natCmp(a.dir.toLowerCase(), b.dir.toLowerCase());
        } else if (col == 5) {
            r = FmFmt.cmpLong(a.total, b.total);
        } else if (col == 6) {
            r = FmFmt.cmpLong(a.free, b.free);
        }
        if (r == 0 && col != 0) {
            return FmFmt.natCmp(a.key, b.key);
        }
        if (col == 0) {
            r = FmFmt.natCmp(a.key, b.key);
            if (r == 0) {
                r = FmFmt.natCmp(a.dir.toLowerCase(), b.dir.toLowerCase());
            }
        }
        if (asc == 0) {
            return -r;
        }
        return r;
    }

    static void mergeSort(ArrayList<FmEntry> list, int col, int asc) {
        int n = list.size();
        if (n < 2) {
            return;
        }
        ArrayList<FmEntry> src = list;
        ArrayList<FmEntry> tmp = new ArrayList<FmEntry>();
        int i = 0;
        while (i < n) {
            tmp.add(list.get(i));
            i = i + 1;
        }
        int width = 1;
        ArrayList<FmEntry> a = src;
        ArrayList<FmEntry> b = tmp;
        while (width < n) {
            int lo = 0;
            while (lo < n) {
                int mid = Math.min(lo + width, n);
                int hi = Math.min(lo + 2 * width, n);
                int p = lo;
                int q = mid;
                int k = lo;
                while (p < mid && q < hi) {
                    if (cmp(a.get(q), a.get(p), col, asc) < 0) {
                        b.set(k, a.get(q));
                        q = q + 1;
                    } else {
                        b.set(k, a.get(p));
                        p = p + 1;
                    }
                    k = k + 1;
                }
                while (p < mid) {
                    b.set(k, a.get(p));
                    p = p + 1;
                    k = k + 1;
                }
                while (q < hi) {
                    b.set(k, a.get(q));
                    q = q + 1;
                    k = k + 1;
                }
                lo = lo + 2 * width;
            }
            ArrayList<FmEntry> t = a;
            a = b;
            b = t;
            width = width * 2;
        }
        if (a != list) {
            i = 0;
            while (i < n) {
                list.set(i, a.get(i));
                i = i + 1;
            }
        }
    }

    /** Re-applies filter + sort to `all`, keeping the focus / anchor entries. */
    void rebuild() {
        rows.clear();
        int i = 0;
        while (i < all.size()) {
            FmEntry e = all.get(i);
            if (showHidden != 0 || e.hidden() == 0) {
                rows.add(e);
            } else {
                e.selected = 0;
            }
            i = i + 1;
        }
        if (!path.equals("::pc") && !path.equals("::home")) {
            mergeSort(rows, sortCol, sortAsc);
        }
        focus = -1;
        anchor = -1;
        i = 0;
        while (i < rows.size()) {
            FmEntry e = rows.get(i);
            if (e == focusEntry) {
                focus = i;
            }
            if (e == anchorEntry) {
                anchor = i;
            }
            i = i + 1;
        }
        if (focus < 0) {
            focusEntry = null;
        }
        if (anchor < 0) {
            anchorEntry = null;
        }
    }

    void sortBy(int col) {
        if (col == sortCol) {
            if (sortAsc != 0) {
                sortAsc = 0;
            } else {
                sortAsc = 1;
            }
        } else {
            sortCol = col;
            sortAsc = 1;
            if (col == 1 || col == 3 || col == 6) {
                sortAsc = 0;
            }
        }
        rebuild();
    }

    /* ------------------------------------------------------------------ loading */
    /** Loads a directory. `sel` / `focusFull` (may be null / "") restore a previous selection. */
    int loadReal(String p, ArrayList<String> sel, String focusFull) {
        path = p;
        focusEntry = null;
        anchorEntry = null;
        String tsv = FmNative.take(FmNative.list(p));
        int e = parseListing(tsv, p);
        keepSelection(sel, focusFull);
        rebuild();
        return e;
    }

    void loadHome() {
        path = "::home";
        err = 0;
        all.clear();
        focusEntry = null;
        anchorEntry = null;
        addKnown(1, "桌面");
        addKnown(2, "下载");
        addKnown(3, "文档");
        addKnown(4, "图片");
        addKnown(5, "音乐");
        addKnown(6, "视频");
        rebuild();
    }

    void addKnown(int id, String fallback) {
        String p = FmNative.take(FmNative.knownFolder(id));
        if (p.length() == 0) {
            return;
        }
        FmEntry e = new FmEntry();
        e.isDir = 1;
        e.full = p;
        e.dir = FmPath.parent(p);
        e.name = FmPath.nameOf(p);
        if (e.name.length() == 0) {
            e.name = fallback;
        }
        e.key = e.name.toLowerCase();
        e.type = typeFor("", 1);
        e.drive = 2;
        all.add(e);
    }

    void loadPc() {
        path = "::pc";
        err = 0;
        all.clear();
        focusEntry = null;
        anchorEntry = null;
        addKnown(1, "桌面");
        addKnown(3, "文档");
        addKnown(2, "下载");
        addKnown(5, "音乐");
        addKnown(4, "图片");
        addKnown(6, "视频");
        String tsv = FmNative.take(FmNative.drives());
        parseDrives(tsv);
        rebuild();
    }

    void parseDrives(String tsv) {
        if (tsv.length() == 0) {
            return;
        }
        String[] lines = tsv.split("\n");
        int i = 0;
        while (i < lines.length) {
            String ln = lines[i];
            i = i + 1;
            if (ln.length() == 0) {
                continue;
            }
            String[] f = ln.split("\t");
            if (f.length < 5) {
                continue;
            }
            FmEntry e = new FmEntry();
            e.isDir = 1;
            e.drive = 1;
            e.full = f[0];
            e.dir = "";
            e.name = f[1];
            if (e.name.length() == 0) {
                e.name = f[0];
            }
            e.key = e.name.toLowerCase();
            e.driveType = (int) FmFmt.toLong(f[2]);
            e.total = FmFmt.toLong(f[3]);
            e.free = FmFmt.toLong(f[4]);
            e.type = driveTypeName(e.driveType);
            all.add(e);
        }
    }

    static String driveTypeName(int t) {
        if (t == 2) {
            return "可移动磁盘";
        }
        if (t == 4) {
            return "网络驱动器";
        }
        if (t == 5) {
            return "CD/DVD 驱动器";
        }
        if (t == 6) {
            return "RAM 磁盘";
        }
        return "本地磁盘";
    }

    void beginSearch(String root, String query) {
        path = "::search";
        searchRoot = root;
        searchQuery = query;
        err = 0;
        all.clear();
        focusEntry = null;
        anchorEntry = null;
        rebuild();
    }

    /** Folder column header title for the current location. */
    int hasFolderColumn() {
        return isSearch();
    }
}

/* ============================================================== navigation tree */
class FmNode {
    String label;
    String path;
    int depth;
    int expanded;
    int loaded;
    /* 1 = may have children (unknown), 0 = known to have none */
    int mayHaveKids;
    int separator;
    int glyph;
    /* real path used for the shell icon, "" = draw the glyph */
    String iconPath;
    ArrayList<FmNode> kids;
    FmNode parent;

    FmNode(String l, String p) {
        label = l;
        path = p;
        depth = 0;
        expanded = 0;
        loaded = 0;
        mayHaveKids = 1;
        separator = 0;
        glyph = 0;
        iconPath = "";
        kids = new ArrayList<FmNode>();
        parent = null;
    }
}

class FmNavTree {
    ArrayList<FmNode> roots;
    ArrayList<FmNode> vis;
    int showHidden;
    FmNode pc;

    FmNavTree() {
        roots = new ArrayList<FmNode>();
        vis = new ArrayList<FmNode>();
        showHidden = 0;
        pc = null;
    }

    void build() {
        roots.clear();
        FmNode home = new FmNode("主页", "::home");
        home.glyph = 59407;
        home.mayHaveKids = 0;
        roots.add(home);
        String od = FmNative.take(FmNative.knownFolder(9));
        if (od.length() > 0) {
            FmNode n = new FmNode(FmPath.nameOf(od), od);
            n.iconPath = od;
            roots.add(n);
        }
        FmNode sep = new FmNode("", "");
        sep.separator = 1;
        sep.mayHaveKids = 0;
        roots.add(sep);
        addPinned(1, "桌面");
        addPinned(2, "下载");
        addPinned(3, "文档");
        addPinned(4, "图片");
        addPinned(5, "音乐");
        addPinned(6, "视频");
        FmNode sep2 = new FmNode("", "");
        sep2.separator = 1;
        sep2.mayHaveKids = 0;
        roots.add(sep2);
        pc = new FmNode("此电脑", "::pc");
        pc.glyph = 59380;
        pc.loaded = 1;
        pc.expanded = 1;
        roots.add(pc);
        loadDrives();
        flatten();
    }

    void addPinned(int id, String fallback) {
        String p = FmNative.take(FmNative.knownFolder(id));
        if (p.length() == 0) {
            return;
        }
        String nm = FmPath.nameOf(p);
        if (nm.length() == 0) {
            nm = fallback;
        }
        FmNode n = new FmNode(nm, p);
        n.iconPath = p;
        roots.add(n);
    }

    void loadDrives() {
        pc.kids.clear();
        String tsv = FmNative.take(FmNative.drives());
        if (tsv.length() > 0) {
            String[] lines = tsv.split("\n");
            int i = 0;
            while (i < lines.length) {
                String[] f = lines[i].split("\t");
                i = i + 1;
                if (f.length < 5) {
                    continue;
                }
                String nm = f[1];
                if (nm.length() == 0) {
                    nm = f[0];
                }
                FmNode d = new FmNode(nm, f[0]);
                d.iconPath = f[0];
                d.parent = pc;
                pc.kids.add(d);
            }
        }
        if (pc.kids.size() == 0) {
            pc.mayHaveKids = 0;
        }
    }

    /** Lists the sub-folders of a real directory (names sorted naturally). */
    void loadKids(FmNode n) {
        if (n.loaded != 0) {
            return;
        }
        n.loaded = 1;
        n.kids.clear();
        if (n.path.length() == 0 || n.path.startsWith("::")) {
            n.mayHaveKids = 0;
            return;
        }
        String tsv = FmNative.take(FmNative.list(n.path));
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
                e.dir = n.path;
                e.full = FmPath.join(n.path, e.name);
                e.attrs = (int) FmFmt.toLong(f[2]);
                e.key = e.name.toLowerCase();
                if (showHidden == 0 && e.hidden() != 0) {
                    continue;
                }
                dirs.add(e);
            }
        }
        FmFolder.mergeSort(dirs, 0, 1);
        int i = 0;
        while (i < dirs.size()) {
            FmEntry e = dirs.get(i);
            FmNode k = new FmNode(e.name, e.full);
            k.parent = n;
            n.kids.add(k);
            i = i + 1;
        }
        if (n.kids.size() == 0) {
            n.mayHaveKids = 0;
        }
    }

    void toggle(FmNode n) {
        if (n.separator != 0) {
            return;
        }
        if (n.expanded != 0) {
            n.expanded = 0;
        } else {
            loadKids(n);
            if (n.kids.size() > 0) {
                n.expanded = 1;
            }
        }
        flatten();
    }

    void flatten() {
        vis.clear();
        int i = 0;
        while (i < roots.size()) {
            addVisible(roots.get(i), 0);
            i = i + 1;
        }
    }

    void addVisible(FmNode n, int depth) {
        n.depth = depth;
        vis.add(n);
        if (n.expanded != 0) {
            int i = 0;
            while (i < n.kids.size()) {
                addVisible(n.kids.get(i), depth + 1);
                i = i + 1;
            }
        }
    }

    /** Collapses every loaded node so folders created / removed meanwhile show up. */
    void reload() {
        ArrayList<String> open = new ArrayList<String>();
        collectOpen(roots, open);
        build();
        int i = 0;
        while (i < open.size()) {
            FmNode n = findNode(open.get(i));
            if (n != null) {
                loadKids(n);
                if (n.kids.size() > 0) {
                    n.expanded = 1;
                }
            }
            i = i + 1;
        }
        flatten();
    }

    void collectOpen(ArrayList<FmNode> list, ArrayList<String> out) {
        int i = 0;
        while (i < list.size()) {
            FmNode n = list.get(i);
            if (n.expanded != 0 && n.path.length() > 0 && !n.path.startsWith("::")) {
                out.add(n.path);
            }
            collectOpen(n.kids, out);
            i = i + 1;
        }
    }

    FmNode findNode(String p) {
        return findIn(roots, p);
    }

    FmNode findIn(ArrayList<FmNode> list, String p) {
        int i = 0;
        while (i < list.size()) {
            FmNode n = list.get(i);
            if (n.path.length() > 0 && FmPath.samePath(n.path, p) != 0) {
                return n;
            }
            FmNode r = findIn(n.kids, p);
            if (r != null) {
                return r;
            }
            i = i + 1;
        }
        return null;
    }

    /** Index in `vis` of the node for path p (only visible nodes), -1 when hidden. */
    int visibleIndex(String p) {
        int i = 0;
        while (i < vis.size()) {
            FmNode n = vis.get(i);
            if (n.path.length() > 0 && FmPath.samePath(n.path, p) != 0) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }
}
