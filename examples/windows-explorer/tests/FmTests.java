/**
 * Headless tests for the JNative Windows Explorer logic. Runs on macOS / Linux against tests/jn_fm_posix.c,
 * which maps "C:\..." paths onto the directory $JNFM_ROOT (see tests/run.sh).
 *
 * @author 张晓达
 */
public class FmTests {
    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            pass = pass + 1;
        } else {
            fail = fail + 1;
            System.out.println("FAIL: " + name);
        }
    }

    static void eq(String name, String got, String want) {
        if (got.equals(want)) {
            pass = pass + 1;
        } else {
            fail = fail + 1;
            System.out.println("FAIL: " + name + " got [" + got + "] want [" + want + "]");
        }
    }

    static void eqi(String name, int got, int want) {
        if (got == want) {
            pass = pass + 1;
        } else {
            fail = fail + 1;
            System.out.println("FAIL: " + name + " got " + String.valueOf(got) + " want " + String.valueOf(want));
        }
    }

    static String names(FmFolder f) {
        String r = "";
        int i = 0;
        while (i < f.rows.size()) {
            if (i > 0) {
                r = r + ",";
            }
            r = r + f.rows.get(i).name;
            i = i + 1;
        }
        return r;
    }

    static void testFormat() {
        eq("sizeKb 0", FmFmt.sizeKb(0), "0 KB");
        eq("sizeKb 1", FmFmt.sizeKb(1), "1 KB");
        eq("sizeKb 1024", FmFmt.sizeKb(1024), "1 KB");
        eq("sizeKb 1025", FmFmt.sizeKb(1025), "2 KB");
        eq("sizeKb big", FmFmt.sizeKb(1234567890L), "1,205,633 KB");
        eq("human bytes", FmFmt.sizeHuman(512), "512 字节");
        eq("human 1.50 KB", FmFmt.sizeHuman(1536), "1.50 KB");
        eq("human 12.3 KB", FmFmt.sizeHuman(12600), "12.3 KB");
        eq("human 123 KB", FmFmt.sizeHuman(125952), "123 KB");
        eq("human MB", FmFmt.sizeHuman(5 * 1024 * 1024), "5.00 MB");
        eq("human GB", FmFmt.sizeHuman(3L * 1024 * 1024 * 1024 + 512L * 1024 * 1024), "3.50 GB");
        eq("comma", FmFmt.comma(1000), "1,000");
        eq("comma2", FmFmt.comma(123), "123");
        eq("comma3", FmFmt.comma(1234567), "1,234,567");
        eq("longStr", FmFmt.longStr(9876543210L), "9876543210");
        check("nat 2<10", FmFmt.natCmp("file2", "file10") < 0);
        check("nat 10>2", FmFmt.natCmp("file10", "file2") > 0);
        check("nat eq", FmFmt.natCmp("abc", "abc") == 0);
        check("nat a<b", FmFmt.natCmp("a1", "b1") < 0);
        check("nat prefix", FmFmt.natCmp("abc", "abcd") < 0);
        check("nat 01 vs 1", FmFmt.natCmp("a01", "a1") == 0);
        check("nat 007<8", FmFmt.natCmp("x007", "x8") < 0);
        eqi("toLong", (int) FmFmt.toLong("12345"), 12345);
        eqi("toLong neg", (int) FmFmt.toLong("-77"), -77);
        eqi("toLong junk", (int) FmFmt.toLong("12ab"), 12);
    }

    static void testPath() {
        eq("join", FmPath.join("C:\\a", "b"), "C:\\a\\b");
        eq("join root", FmPath.join("C:\\", "b"), "C:\\b");
        eq("parent", FmPath.parent("C:\\a\\b"), "C:\\a");
        eq("parent drive child", FmPath.parent("C:\\a"), "C:\\");
        eq("parent root", FmPath.parent("C:\\"), "::pc");
        eq("parent trailing", FmPath.parent("C:\\a\\b\\"), "C:\\a");
        eq("name", FmPath.nameOf("C:\\a\\b.txt"), "b.txt");
        eq("name root", FmPath.nameOf("C:\\"), "C:\\");
        eq("ext", FmPath.extOf("Photo.JPG"), "jpg");
        eq("ext none", FmPath.extOf("README"), "");
        eq("ext trailing dot", FmPath.extOf("a."), "");
        eq("ext dotfile", FmPath.extOf(".gitignore"), "gitignore");
        eq("stem", FmPath.stem("a.b.txt"), "a.b");
        eq("stem none", FmPath.stem("noext"), "noext");
        check("valid ok", FmPath.validName("a b.txt") == 1);
        check("valid star", FmPath.validName("a*b") == 0);
        check("valid empty", FmPath.validName("") == 0);
        check("valid spaces", FmPath.validName("   ") == 0);
        check("valid dot", FmPath.validName("..") == 0);
        check("valid trailing dot", FmPath.validName("a.") == 0);
        check("valid slash", FmPath.validName("a\\b") == 0);
        check("valid quote", FmPath.validName("a\"b") == 0);
        check("inside", FmPath.isInside("C:\\a\\b", "C:\\a") == 1);
        check("inside self", FmPath.isInside("C:\\a", "C:\\a") == 1);
        check("inside prefix trap", FmPath.isInside("C:\\ab", "C:\\a") == 0);
        check("inside case", FmPath.isInside("c:\\A\\B", "C:\\a") == 1);
        check("same path", FmPath.samePath("C:\\Users\\", "c:\\users") == 1);
        ArrayList<String> seg = FmPath.segmentNames("C:\\Users\\me\\Docs");
        eqi("seg n", seg.size(), 4);
        eq("seg0", seg.get(0), "C:");
        eq("seg3", seg.get(3), "Docs");
        eq("segPath0", FmPath.segmentPath("C:\\Users\\me\\Docs", 0), "C:\\");
        eq("segPath2", FmPath.segmentPath("C:\\Users\\me\\Docs", 2), "C:\\Users\\me");
        ArrayList<String> unc = FmPath.segmentNames("\\\\srv\\share\\x\\y");
        eqi("unc n", unc.size(), 3);
        eq("unc0", unc.get(0), "\\\\srv\\share");
    }

    static void testHistory() {
        FmHistory h = new FmHistory();
        check("empty back", !h.canBack());
        h.visit("a");
        h.visit("b");
        h.visit("c");
        check("can back", h.canBack());
        eq("back", h.back(), "b");
        eq("back2", h.back(), "a");
        check("no more back", !h.canBack());
        check("can fwd", h.canForward());
        eq("fwd", h.forward(), "b");
        h.visit("x");
        check("fwd truncated", !h.canForward());
        eq("cur", h.current(), "x");
        h.visit("x");
        eqi("dup ignored", h.items.size(), 3);
    }

    static String rec(String kind, String name, int attrs, long size, long mtime) {
        return kind + "\t" + name + "\t" + String.valueOf(attrs) + "\t" + FmFmt.longStr(size) + "\t"
                + FmFmt.longStr(mtime) + "\t0\t2025/1/1 10:00\n";
    }

    static void testFolderSort() {
        FmFolder f = new FmFolder();
        String tsv = rec("F", "b.txt", 0, 100, 3000) + rec("D", "Zdir", 16, -1, 1000) + rec("F", "a10.txt", 0, 5000, 1000)
                + rec("F", "a2.txt", 0, 50, 2000) + rec("D", "adir", 16, -1, 5000) + rec("F", ".hid", 2, 1, 1) + rec("F", "c.PNG", 0, 777, 9000);
        f.path = "C:\\t";
        eqi("parse err", f.parseListing(tsv, "C:\\t"), 0);
        eqi("all", f.all.size(), 7);
        f.showHidden = 0;
        f.rebuild();
        eq("name asc", names(f), "adir,Zdir,a2.txt,a10.txt,b.txt,c.PNG");
        f.sortBy(0);
        eq("name desc", names(f), "Zdir,adir,c.PNG,b.txt,a10.txt,a2.txt");
        f.sortBy(3);
        eq("size (desc first)", names(f), "adir,Zdir,a10.txt,c.PNG,b.txt,a2.txt");
        f.sortBy(3);
        eq("size asc", names(f), "adir,Zdir,a2.txt,b.txt,c.PNG,a10.txt");
        f.sortBy(1);
        eq("date desc", names(f), "adir,Zdir,c.PNG,b.txt,a2.txt,a10.txt");
        f.sortBy(1);
        eq("date asc", names(f), "Zdir,adir,a10.txt,a2.txt,b.txt,c.PNG");
        f.showHidden = 1;
        f.sortCol = 0;
        f.sortAsc = 1;
        f.rebuild();
        eqi("hidden shown", f.rows.size(), 7);
        eq("dotfile sorted first among files", f.rows.get(2).name, ".hid");
        check("hidden attr", f.rows.get(2).hidden() == 1);
        f.sortBy(2);
        check("type sort keeps dirs first", f.rows.get(0).isDir == 1 && f.rows.get(1).isDir == 1);
        /* selection helpers */
        f.showHidden = 0;
        f.sortCol = 0;
        f.sortAsc = 1;
        f.rebuild();
        f.selectOnly(2);
        eqi("sel1", f.selCount(), 1);
        f.selectRange(2, 4, 0);
        eqi("range", f.selCount(), 3);
        f.invertSel();
        eqi("invert", f.selCount(), 3);
        f.selectAllRows();
        eqi("all", f.selCount(), 6);
        check("selBytes", f.selBytes() == 100 + 5000 + 50 + 777);
        f.clearSel();
        eqi("clear", f.selCount(), 0);
        check("selBytes none", f.selBytes() < 0);
        /* focus survives a re-sort */
        f.selectOnly(2);
        FmEntry fe = f.focusEntry;
        f.sortBy(3);
        check("focus kept", f.rows.get(f.focus) == fe);
        /* error record */
        FmFolder g = new FmFolder();
        eqi("err parse", g.parseListing("!\t5\n", "C:\\x"), 5);
        eqi("err empty", g.all.size(), 0);
    }

    static void testSearchParse() {
        FmFolder f = new FmFolder();
        f.path = "::search";
        String s = rec("F", "x.txt", 0, 3, 1).substring(0, rec("F", "x.txt", 0, 3, 1).length() - 1) + "\tC:\\a\\b\n#done\n";
        int done = f.appendSearch(s);
        eqi("search done", done, 1);
        eqi("search n", f.all.size(), 1);
        eq("search dir", f.all.get(0).dir, "C:\\a\\b");
        eq("search full", f.all.get(0).full, "C:\\a\\b\\x.txt");
        eqi("not done", f.appendSearch(""), 0);
    }

    static void testHeadless() {
        FmApp.testMode = 1;
        Application app = FmApp.setup("C:\\Users\\me\\Documents");
        FmFolder f = FmApp.folder;
        FmList list = FmApp.list;
        eq("start path", f.path, "C:\\Users\\me\\Documents");
        eqi("history 1", FmApp.hist.items.size(), 1);
        eq("name sorted", names(f), "Archive,Notes,alpha.txt,beta.md,report 2.docx,report 10.docx");
        FmEntry notes = f.rows.get(1);
        check("Notes is dir", notes.isDir == 1);
        eq("type dir", notes.type, "文件夹");
        eq("type txt", f.rows.get(2).type, "TXT 文件");
        eq("title", FmApp.folderTitle(), "Documents");

        /* hidden files toggle */
        eqi("hidden not shown", f.rows.size(), 6);
        FmApp.doAction(36);
        eqi("hidden shown", f.rows.size(), 7);
        FmApp.doAction(36);
        eqi("hidden hidden again", f.rows.size(), 6);

        /* navigation: open folder, back, forward, up */
        FmApp.openEntry(notes);
        eq("into Notes", f.path, "C:\\Users\\me\\Documents\\Notes");
        eq("Notes content", names(f), "todo.txt");
        FmApp.goBack();
        eq("back", f.path, "C:\\Users\\me\\Documents");
        check("back selects origin", f.selCount() == 1 && f.selectedEntries().get(0).name.equals("Notes"));
        FmApp.goForward();
        eq("forward", f.path, "C:\\Users\\me\\Documents\\Notes");
        FmApp.goUp();
        eq("up", f.path, "C:\\Users\\me\\Documents");
        FmApp.navigateTo("C:\\", 1, "");
        eq("drive root", f.path, "C:\\");
        eq("parent of drive root", FmApp.parentOfCurrent(), "::pc");
        FmApp.goUp();
        eq("this pc", f.path, "::pc");
        check("pc has drives", f.rows.size() >= 8);
        check("pc drive row", f.rows.get(f.rows.size() - 1).drive == 1);
        eq("pc parent", FmApp.parentOfCurrent(), "");
        FmApp.navigateTo("::home", 1, "");
        eqi("home items", f.rows.size(), 6);
        FmApp.navigateTo("C:\\Users\\me\\Documents", 1, "");

        /* errors */
        String before = f.path;
        FmApp.navigateTo("C:\\Nope\\Missing", 1, "");
        eq("missing stays", f.path, before);
        check("missing reported", FmApp.lastError.indexOf("找不到") >= 0);

        /* new folder + inline rename */
        FmApp.newItem(1);
        check("new folder exists", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹") == 2);
        eqi("rename active", FmApp.renaming, 1);
        eqi("one selected", f.selCount(), 1);
        FmApp.ren.setText("Projects");
        FmApp.commitRename();
        eqi("rename done", FmApp.renaming, 0);
        check("renamed exists", FmNative.exists("C:\\Users\\me\\Documents\\Projects") == 2);
        check("old gone", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹") == 0);
        check("renamed selected", f.selCount() == 1 && f.selectedEntries().get(0).name.equals("Projects"));
        FmApp.newItem(1);
        check("second new folder unique", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹") == 2);
        FmApp.newItem(1);
        check("third new folder numbered", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹 (2)") == 2);
        FmApp.cancelRename();
        /* invalid + duplicate names */
        FmApp.lastError = "";
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\新建文件夹 (2)"));
        FmApp.startRename();
        FmApp.ren.setText("bad*name");
        FmApp.commitRename();
        check("invalid name rejected", FmApp.lastError.indexOf("不能包含") >= 0);
        check("still old name", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹 (2)") == 2);
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\新建文件夹 (2)"));
        FmApp.startRename();
        FmApp.lastError = "";
        FmApp.ren.setText("Projects");
        FmApp.commitRename();
        check("duplicate reported", FmApp.lastError.length() > 0);
        check("dup unchanged", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹 (2)") == 2);
        /* clean the helper folders (permanent delete) */
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\新建文件夹 (2)"));
        FmApp.doDelete(1);
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\新建文件夹"));
        FmApp.doDelete(1);
        check("perm delete", FmNative.exists("C:\\Users\\me\\Documents\\新建文件夹") == 0);

        /* new text document */
        FmApp.newItem(0);
        check("new txt", FmNative.exists("C:\\Users\\me\\Documents\\新建文本文档.txt") == 1);
        FmApp.cancelRename();
        FmApp.showExt = 0;
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\新建文本文档.txt"));
        FmApp.startRename();
        eq("editor shows stem when ext hidden", FmApp.ren.getText(), "新建文本文档");
        FmApp.ren.setText("memo");
        FmApp.commitRename();
        check("ext preserved on rename", FmNative.exists("C:\\Users\\me\\Documents\\memo.txt") == 1);
        eq("display w/o ext", FmApp.displayName(f.rows.get(f.indexOfPath("C:\\Users\\me\\Documents\\memo.txt"))), "memo");
        FmApp.showExt = 1;

        /* copy / paste into a sub folder, cut / paste (move) */
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\alpha.txt"));
        FmApp.clipSelection(0);
        eqi("clip copy state", FmApp.clipState, 1);
        FmApp.doPaste("C:\\Users\\me\\Documents\\Archive");
        check("copy exists", FmNative.exists("C:\\Users\\me\\Documents\\Archive\\alpha.txt") == 1);
        check("copy keeps source", FmNative.exists("C:\\Users\\me\\Documents\\alpha.txt") == 1);
        /* paste in the same folder renames */
        FmApp.doPaste("");
        check("same-folder paste makes a copy", FmNative.exists("C:\\Users\\me\\Documents\\alpha.txt - Copy") == 1);
        check("new copy selected", f.selCount() == 1 && f.selectedEntries().get(0).name.equals("alpha.txt - Copy"));
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\beta.md"));
        FmApp.clipSelection(1);
        eqi("clip cut state", FmApp.clipState, 2);
        check("cut marks entry", FmApp.isCut(f.rows.get(f.indexOfPath("C:\\Users\\me\\Documents\\beta.md"))) == 1);
        FmApp.doPaste("C:\\Users\\me\\Documents\\Archive");
        check("moved exists", FmNative.exists("C:\\Users\\me\\Documents\\Archive\\beta.md") == 1);
        check("moved gone", FmNative.exists("C:\\Users\\me\\Documents\\beta.md") == 0);
        eqi("clipboard cleared after move", FmNative.clipState(), 0);
        /* folder into itself is refused */
        ArrayList<String> self = new ArrayList<String>();
        self.add("C:\\Users\\me\\Documents\\Archive");
        FmApp.lastError = "";
        FmApp.transfer(self, "C:\\Users\\me\\Documents\\Archive\\sub", 0, 0);
        check("into itself refused", FmApp.lastError.indexOf("自身") >= 0);

        /* drag & drop move */
        f.selectOnly(f.indexOfPath("C:\\Users\\me\\Documents\\alpha.txt - Copy"));
        list.dropTarget = f.indexOfPath("C:\\Users\\me\\Documents\\Notes");
        FmChrome.navDrop = -1;
        FmApp.dropItems(0, 0);
        list.dropTarget = -1;
        check("drop moved", FmNative.exists("C:\\Users\\me\\Documents\\Notes\\alpha.txt - Copy") == 1);
        check("drop source gone", FmNative.exists("C:\\Users\\me\\Documents\\alpha.txt - Copy") == 0);

        /* delete to recycle bin keeps focus near the deleted item */
        int idx = f.indexOfPath("C:\\Users\\me\\Documents\\memo.txt");
        f.selectOnly(idx);
        FmApp.doDelete(0);
        check("recycled", FmNative.exists("C:\\Users\\me\\Documents\\memo.txt") == 0);
        eqi("selection after delete", f.selCount(), 1);

        /* sort via header + menu actions */
        FmApp.sortColumn(3);
        eqi("sort col", f.sortCol, 3);
        FmApp.doAction(30);
        eqi("sort by name action", f.sortCol, 0);
        FmApp.doAction(35);
        eqi("desc", f.sortAsc, 0);
        check("desc dirs first", f.rows.get(0).isDir == 1);
        FmApp.doAction(34);

        /* search */
        FmApp.navigateTo("C:\\Users\\me", 1, "");
        FmApp.sbox.setText("report");
        FmApp.onSearchChanged();
        eqi("search debounce armed", FmApp.searchDirty, 1);
        FmApp.runSearch();
        eqi("searching", FmApp.searching, 1);
        FmApp.pollSearch();
        eqi("search finished", FmApp.searching, 0);
        eqi("search hits", f.rows.size(), 2);
        eq("search path", f.path, "::search");
        check("search has folder col", list.cols.size() == 5);
        eq("title search", FmApp.folderTitle(), "搜索结果 - me");
        FmApp.sortColumn(4);
        eqi("sort by folder", f.sortCol, 4);
        FmApp.sortColumn(0);
        FmApp.leaveSearch();
        eq("left search", f.path, "C:\\Users\\me");
        eq("search box cleared", FmApp.sbox.getText(), "");
        FmApp.sbox.setText("zzzz-nothing");
        FmApp.runSearch();
        FmApp.pollSearch();
        eqi("no hits", f.rows.size(), 0);
        FmApp.leaveSearch();

        /* nav tree */
        FmNavTree nav = FmApp.nav;
        check("tree has home", nav.vis.get(0).path.equals("::home"));
        FmNode pc = nav.pc;
        check("pc expanded", pc.expanded == 1 && pc.kids.size() == 2);
        FmNode c = pc.kids.get(0);
        check("drive label", c.label.indexOf("C:") >= 0);
        nav.toggle(c);
        check("C: expanded", c.expanded == 1 && c.kids.size() >= 1);
        check("kids sorted", c.kids.get(0).label.equals("Users") || c.kids.get(0).label.equals("Program Files"));
        nav.toggle(c);
        check("collapsed", c.expanded == 0);
        FmNode docs = nav.findNode("C:\\Users\\me\\Documents");
        check("pinned documents found", docs != null);
        nav.toggle(docs);
        check("documents expanded", docs.expanded == 1);
        check("visible index", nav.visibleIndex("C:\\Users\\me\\Documents\\Archive") >= 0);
        nav.toggle(docs);
        FmNode empty = nav.findNode("C:\\Users\\me\\Pictures");
        nav.toggle(empty);
        check("empty folder has no kids", empty.expanded == 0 && empty.mayHaveKids == 0);

        /* crumbs */
        FmApp.navigateTo("C:\\Users\\me\\Documents", 1, "");
        ArrayList<String> lb = new ArrayList<String>();
        ArrayList<String> ps = new ArrayList<String>();
        FmApp.buildCrumbs(lb, ps);
        eqi("crumb count", lb.size(), 5);
        eq("crumb0", lb.get(0), "此电脑");
        eq("crumb1 label", lb.get(1), "本地磁盘 (C:)");
        eq("crumb1 path", ps.get(1), "C:\\");
        eq("crumb4", ps.get(4), "C:\\Users\\me\\Documents");

        /* list engine */
        list.x = 233;
        list.y = 94;
        list.w = 900;
        list.h = 500;
        list.configure();
        list.mode = 0;
        int n = f.rows.size();
        check("itemAt row 0", list.itemAt(300, 94 + Ft.HDR_H + 5) == 0);
        check("itemAt row 1", list.itemAt(300, 94 + Ft.HDR_H + Ft.ROW_H + 3) == 1);
        check("itemAt below", list.itemAt(300, 94 + Ft.HDR_H + n * Ft.ROW_H + 20) == -1);
        int r = list.onDown(300, 94 + Ft.HDR_H + 5, 1, 0, 0);
        eqi("click selects", f.selCount(), 1);
        eqi("click focus", f.focus, 0);
        list.onUp(300, 94 + Ft.HDR_H + 5, 1);
        r = list.onDown(300, 94 + Ft.HDR_H + 3 * Ft.ROW_H + 3, 1, 0, 1);
        eqi("shift range", f.selCount(), 4);
        list.onUp(300, 94 + Ft.HDR_H + 3 * Ft.ROW_H + 3, 1);
        r = list.onDown(300, 94 + Ft.HDR_H + 5 * Ft.ROW_H + 3, 1, 1, 0);
        eqi("ctrl adds", f.selCount(), 5);
        list.onUp(300, 94 + Ft.HDR_H + 5 * Ft.ROW_H + 3, 1);
        r = list.onDown(300, 94 + Ft.HDR_H + 5 * Ft.ROW_H + 3, 1, 1, 0);
        eqi("ctrl removes", f.selCount(), 4);
        list.onUp(300, 94 + Ft.HDR_H + 5 * Ft.ROW_H + 3, 1);
        /* press on selected item then release without moving collapses to it */
        r = list.onDown(300, 94 + Ft.HDR_H + 1 * Ft.ROW_H + 3, 1, 0, 0);
        eqi("press on selected keeps selection", f.selCount(), 4);
        r = list.onUp(300, 94 + Ft.HDR_H + 1 * Ft.ROW_H + 3, 1);
        eqi("release collapses", f.selCount(), 1);
        /* rubber band */
        int yEmpty = 94 + Ft.HDR_H + n * Ft.ROW_H + 40;
        r = list.onDown(700, yEmpty, 1, 0, 0);
        eqi("band starts", list.dragMode, 3);
        list.onMove(700, 94 + Ft.HDR_H + 2 * Ft.ROW_H + 3, 1);
        check("band selects rows", f.selCount() >= 2);
        list.onUp(700, 94 + Ft.HDR_H + 2 * Ft.ROW_H + 3, 1);
        /* right click on a non selected item selects it */
        f.clearSel();
        r = list.onDown(300, 94 + Ft.HDR_H + 2 * Ft.ROW_H + 3, 2, 0, 0);
        eqi("rclick item", r, 2);
        eqi("rclick selects", f.selCount(), 1);
        list.onUp(300, 94 + Ft.HDR_H + 2 * Ft.ROW_H + 3, 2);
        r = list.onDown(700, yEmpty, 2, 0, 0);
        eqi("rclick empty", r, 3);
        list.onUp(700, yEmpty, 2);
        /* header: divider resize + sort click */
        int dx = list.x + list.cols.get(0).w;
        int w0 = list.cols.get(0).w;
        list.onDown(dx, 100, 1, 0, 0);
        eqi("divider drag", list.dragMode, 4);
        list.onMove(dx + 40, 100, 1);
        eqi("col resized", list.cols.get(0).w, w0 + 40);
        list.onUp(dx + 40, 100, 1);
        r = list.onDown(list.x + list.cols.get(0).w + 30, 100, 1, 0, 0);
        r = list.onUp(list.x + list.cols.get(0).w + 30, 100, 1);
        eqi("header click -> sort", r, 4);
        eqi("sort column id", FmApp.pendingSort, 1);
        /* keyboard */
        f.sortCol = 0;
        f.sortAsc = 1;
        f.rebuild();
        f.selectOnly(0);
        list.onKey(40, 0);
        eqi("down", f.focus, 1);
        list.onKey(40, 1);
        eqi("shift+down selects 2", f.selCount(), 2);
        list.onKey(35, 0);
        eqi("end", f.focus, f.rows.size() - 1);
        list.onKey(36, 0);
        eqi("home", f.focus, 0);
        list.onKey(34, 0);
        check("pgdn moves", f.focus > 0);
        f.selectOnly(0);
        eqi("type r", list.typeAhead(114, 100), 1);
        eq("type-ahead finds report", f.rows.get(f.focus).name.substring(0, 6), "report");
        eqi("type e continues prefix", list.typeAhead(69, 101), 1);
        eq("type-ahead re", f.rows.get(f.focus).name.substring(0, 6), "report");
        eqi("type z no match", list.typeAhead(122, 102), 0);
        eqi("expired prefix restarts", list.typeAhead(97, 200), 1);
        eq("type-ahead a", f.rows.get(f.focus).key.substring(0, 1), "a");
        /* grid mode geometry */
        list.mode = 1;
        list.configure();
        check("grid cols", list.gridCols() == (900 - 16) / Ft.GRID_W);
        check("grid itemAt 0", list.itemAt(list.x + 20, list.y + 20) == 0);
        check("grid itemAt 1", list.itemAt(list.x + 8 + Ft.GRID_W + 10, list.y + 20) == 1);
        list.onKey(39, 0);
        list.mode = 0;
        list.configure();
        list.configure();

        /* more options / properties / copy path run through the shell layer */
        f.selectOnly(0);
        FmApp.copyPathText();
        FmApp.showProperties();
        FmApp.moreOptions(0);

        /* paint smoke test (stubs on this host: just must not crash / loop) */
        FmApp.W = 1180;
        FmApp.H = 740;
        FmApp.paint();
        FmApp.paintErrors = 0;
        list.mode = 1;
        list.configure();
        FmApp.paint();
        list.mode = 0;
        list.configure();
        FmApp.navigateTo("::pc", 1, "");
        FmApp.paint();

        /* tiles layout on This PC */
        eqi("pc uses tiles", list.emode, 2);
        int nf = list.nFolders();
        check("pc folder tiles", nf >= 6);
        check("folders first", f.rows.get(nf - 1).drive == 2 && f.rows.get(nf).drive == 1);
        int tc = list.tileCols();
        check("tile cols", tc >= 1 && tc == (list.w - 24) / (Ft.TILE_W + 8));
        eqi("tile 0 hit", list.itemAt(list.x + 20, list.y + 8 + 36 + 10), 0);
        eqi("tile 0 gap", list.itemAt(list.x + 12 + Ft.TILE_W + 2, list.y + 8 + 36 + 10), tc > 1 ? -1 : -1);
        eqi("tile header not hit", list.itemAt(list.x + 20, list.y + 10), -1);
        int di = nf;
        eqi("drive tile hit", list.itemAt(list.tileLeft(di) + 10, list.tileTop(di) + 10), di);
        eqi("tile top roundtrip", list.tileTop(di) >= list.y ? 1 : 0, 1);
        if (tc == 1) {
            eqi("tile down", list.vertTarget(0, 1), 1);
        } else {
            eqi("tile down", list.vertTarget(0, 1), tc);
        }
        eqi("tile up from first", list.vertTarget(0, -1), 0);
        check("tile content", list.contentH() > list.sec2Top());
        f.selectOnly(0);
        list.onKey(40, 0);
        check("tile key down", f.focus > 0);
        FmApp.paint();
        FmApp.navigateTo("::home", 1, "");
        eqi("home uses tiles", list.emode, 2);
        FmApp.paint();
        list.mode = 1;
        list.configure();
        eqi("pc grid when requested", list.emode, 1);
        list.mode = 0;
        list.configure();
        FmApp.navigateTo("C:\\Users\\me\\Documents", 1, "");
        eqi("details elsewhere", list.emode, 0);
        FmApp.onTick();
    }

    public static void main(String[] args) {
        testFormat();
        testPath();
        testHistory();
        testFolderSort();
        testSearchParse();
        testHeadless();
        System.out.println("passed " + String.valueOf(pass) + ", failed " + String.valueOf(fail));
        if (fail > 0) {
            System.out.println("FAILED");
        } else {
            System.out.println("ALL OK");
        }
    }
}
