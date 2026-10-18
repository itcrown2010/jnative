/**
 * JNative Windows Explorer - data model, formatting helpers and the native shell bindings.
 * Pure logic: nothing in this file touches the canvas, so it is unit-tested headlessly.
 *
 * @author 张晓达
 */
class FmNative {
    @Native(symbol = "jn_fm_free", library = "jn_fm")
    static native void free(NativePtr p);

    @Native(symbol = "jn_fm_list", library = "jn_fm")
    static native NativePtr list(String path);

    @Native(symbol = "jn_fm_drives", library = "jn_fm")
    static native NativePtr drives();

    @Native(symbol = "jn_fm_known_folder", library = "jn_fm")
    static native NativePtr knownFolder(int id);

    @Native(symbol = "jn_fm_type_name", library = "jn_fm")
    static native NativePtr typeName(String name, int isDir);

    @Native(symbol = "jn_fm_exists", library = "jn_fm")
    static native int exists(String path);

    @Native(symbol = "jn_fm_full_path", library = "jn_fm")
    static native NativePtr fullPath(String path);

    @Native(symbol = "jn_fm_file_op", library = "jn_fm")
    static native int fileOp(int op, String srcs, String dst, int flags);

    @Native(symbol = "jn_fm_clip_set", library = "jn_fm")
    static native int clipSet(String paths, int cut);

    @Native(symbol = "jn_fm_clip_get", library = "jn_fm")
    static native NativePtr clipGet();

    @Native(symbol = "jn_fm_clip_state", library = "jn_fm")
    static native int clipState();

    @Native(symbol = "jn_fm_clip_clear", library = "jn_fm")
    static native void clipClear();

    @Native(symbol = "jn_fm_clip_text", library = "jn_fm")
    static native int clipText(String text);

    @Native(symbol = "jn_fm_rename", library = "jn_fm")
    static native int rename(String path, String newName);

    @Native(symbol = "jn_fm_create", library = "jn_fm")
    static native int create(String path, int isDir);

    @Native(symbol = "jn_fm_shell_exec", library = "jn_fm")
    static native int shellExec(String path, String verb, String params, String cwd);

    @Native(symbol = "jn_fm_shell_menu", library = "jn_fm")
    static native int shellMenu(String paths, int flags);

    @Native(symbol = "jn_fm_shell_verb", library = "jn_fm")
    static native int shellVerb(String paths, String verb);

    @Native(symbol = "jn_fm_search_begin", library = "jn_fm")
    static native int searchBegin(String root, String query);

    @Native(symbol = "jn_fm_search_next", library = "jn_fm")
    static native NativePtr searchNext(int handle, int budgetMs);

    @Native(symbol = "jn_fm_search_end", library = "jn_fm")
    static native void searchEnd(int handle);

    @Native(symbol = "jn_fm_watch_set", library = "jn_fm")
    static native int watchSet(String path);

    @Native(symbol = "jn_fm_watch_poll", library = "jn_fm")
    static native int watchPoll();

    @Native(symbol = "jn_fm_spawn_self", library = "jn_fm")
    static native int spawnSelf(String path);

    /** Reads and releases a malloc'd UTF-8 text result. Never returns null. */
    static String take(NativePtr p) {
        if (p == NativePtr.NULL) {
            return "";
        }
        String t = NativeMemory.fromCString(p);
        free(p);
        if (t == null) {
            return "";
        }
        return t;
    }
}

/** One row of a folder / drive / search listing. */
class FmEntry {
    String name;
    /* parent directory (without trailing separator unless a drive root); "" for virtual rows */
    String dir;
    /* absolute path of the item itself */
    String full;
    int isDir;
    int attrs;
    long size;
    long mtime;
    long ctime;
    String mtimeText;
    String type;
    String ext;
    String key;
    int drive;
    long total;
    long free;
    int selected;
    int driveType;

    FmEntry() {
        name = "";
        dir = "";
        full = "";
        isDir = 0;
        attrs = 0;
        size = -1;
        mtime = 0;
        ctime = 0;
        mtimeText = "";
        type = "";
        ext = "";
        key = "";
        drive = 0;
        total = -1;
        free = -1;
        selected = 0;
        driveType = 0;
    }

    int hidden() {
        if ((attrs % 4) >= 2) {
            return 1;
        }
        if ((attrs / 4) % 2 == 1) {
            return 1;
        }
        return 0;
    }
}

class FmPath {
    static String SEP = "\\";

    static boolean isVirtual(String p) {
        return p.startsWith("::");
    }

    /** True for "C:\" and "\\server\share". */
    static boolean isRoot(String p) {
        if (p.length() == 3 && p.charAt(1) == 58 && p.charAt(2) == 92) {
            return true;
        }
        return false;
    }

    static String trimSep(String p) {
        String r = p;
        while (r.length() > 3 && (r.endsWith("\\") || r.endsWith("/"))) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    static String join(String dir, String name) {
        if (dir.length() == 0) {
            return name;
        }
        if (dir.endsWith("\\")) {
            return dir + name;
        }
        return dir + "\\" + name;
    }

    /** Parent folder of a real path; "::pc" when the path is a drive root, "" when none. */
    static String parent(String p) {
        String t = trimSep(p);
        if (isVirtual(t)) {
            return "";
        }
        if (isRoot(t)) {
            return "::pc";
        }
        int i = t.lastIndexOf("\\");
        if (i < 0) {
            return "::pc";
        }
        if (i <= 2) {
            return t.substring(0, 3);
        }
        return t.substring(0, i);
    }

    static String nameOf(String p) {
        String t = trimSep(p);
        if (isRoot(t)) {
            return t;
        }
        int i = t.lastIndexOf("\\");
        if (i < 0) {
            return t;
        }
        return t.substringFrom(i + 1);
    }

    static String extOf(String name) {
        int i = name.lastIndexOf(".");
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substringFrom(i + 1).toLowerCase();
    }

    /** Name without the extension (dot-prefixed names keep everything before the last dot, may be empty). */
    static String stem(String name) {
        int i = name.lastIndexOf(".");
        if (i <= 0) {
            return name;
        }
        return name.substring(0, i);
    }

    static int samePath(String a, String b) {
        String x = trimSep(a).toLowerCase();
        String y = trimSep(b).toLowerCase();
        if (x.equals(y)) {
            return 1;
        }
        return 0;
    }

    /** Is path `p` equal to `dir` or inside it? */
    static int isInside(String p, String dir) {
        String x = trimSep(p).toLowerCase();
        String d = trimSep(dir).toLowerCase();
        if (x.equals(d)) {
            return 1;
        }
        if (!d.endsWith("\\")) {
            d = d + "\\";
        }
        if (x.startsWith(d)) {
            return 1;
        }
        return 0;
    }

    /** Characters Windows forbids in a file name: \ / : * ? " < > | */
    static int validName(String n) {
        if (n.length() == 0) {
            return 0;
        }
        int i = 0;
        while (i < n.length()) {
            int c = n.charAt(i);
            if (c < 32 || c == 92 || c == 47 || c == 58 || c == 42 || c == 63 || c == 34 || c == 60 || c == 62 || c == 124) {
                return 0;
            }
            i = i + 1;
        }
        String t = n.trim();
        if (t.length() == 0) {
            return 0;
        }
        if (t.equals(".") || t.equals("..")) {
            return 0;
        }
        if (n.endsWith(".") || n.endsWith(" ")) {
            return 0;
        }
        return 1;
    }

    /** Breadcrumb segments of a real path: C:\Users\me -> "C:\", "Users", "me" with their full paths. */
    static ArrayList<String> segmentNames(String p) {
        ArrayList<String> out = new ArrayList<String>();
        String t = trimSep(p);
        if (t.startsWith("\\\\")) {
            /* UNC: \\server\share\a\b */
            String rest = t.substringFrom(2);
            int i = rest.indexOf("\\");
            if (i < 0) {
                out.add("\\\\" + rest);
                return out;
            }
            int j = rest.indexOfFrom("\\", i + 1);
            if (j < 0) {
                out.add("\\\\" + rest);
                return out;
            }
            out.add("\\\\" + rest.substring(0, j));
            String tail = rest.substringFrom(j + 1);
            addParts(out, tail);
            return out;
        }
        if (t.length() >= 2 && t.charAt(1) == 58) {
            out.add(t.substring(0, 2));
            if (t.length() > 3) {
                addParts(out, t.substringFrom(3));
            }
            return out;
        }
        addParts(out, t);
        return out;
    }

    static void addParts(ArrayList<String> out, String tail) {
        int s = 0;
        while (s <= tail.length()) {
            int e = tail.indexOfFrom("\\", s);
            if (e < 0) {
                e = tail.length();
            }
            if (e > s) {
                out.add(tail.substring(s, e));
            }
            s = e + 1;
        }
    }

    /** Full path of the k-th breadcrumb segment (0 based). */
    static String segmentPath(String p, int k) {
        String t = trimSep(p);
        ArrayList<String> names = segmentNames(t);
        String r = "";
        int i = 0;
        while (i <= k && i < names.size()) {
            if (i == 0) {
                r = names.get(0);
                if (r.length() == 2 && r.charAt(1) == 58) {
                    r = r + "\\";
                }
            } else {
                r = join(r, names.get(i));
            }
            i = i + 1;
        }
        return r;
    }
}

class FmFmt {
    static String two(int n) {
        if (n < 10) {
            return "0" + String.valueOf(n);
        }
        return String.valueOf(n);
    }

    static String longStr(long n) {
        if (n == 0) {
            return "0";
        }
        if (n < 0) {
            return "-" + longStr(-n);
        }
        String r = "";
        long v = n;
        while (v > 0) {
            int d = (int) (v % 10);
            r = String.valueOf(d) + r;
            v = v / 10;
        }
        return r;
    }

    static String comma(long n) {
        String s = longStr(n);
        if (n < 1000) {
            return s;
        }
        String r = "";
        int cnt = 0;
        int i = s.length() - 1;
        while (i >= 0) {
            r = s.substring(i, i + 1) + r;
            cnt = cnt + 1;
            if (cnt % 3 == 0 && i > 0) {
                r = "," + r;
            }
            i = i - 1;
        }
        return r;
    }

    /** Details-view size column: whole kilobytes rounded up ("1 KB" for 1 byte), like Explorer. */
    static String sizeKb(long bytes) {
        if (bytes < 0) {
            return "";
        }
        long kb = (bytes + 1023) / 1024;
        return comma(kb) + " KB";
    }

    /** Three significant digits: 12.3 KB, 1.50 GB, 123 MB, "512 字节". */
    static String sizeHuman(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return longStr(bytes) + " 字节";
        }
        String[] u = new String[5];
        u[0] = "KB";
        u[1] = "MB";
        u[2] = "GB";
        u[3] = "TB";
        u[4] = "PB";
        long scaled = bytes * 100;
        int idx = -1;
        long v = scaled;
        while (v >= 102400 && idx < 4) {
            v = v / 1024;
            idx = idx + 1;
        }
        /* v is value * 100 now, express with 3 significant digits */
        long whole = v / 100;
        long frac = v % 100;
        String num;
        if (whole >= 100) {
            num = longStr(whole);
        } else if (whole >= 10) {
            num = longStr(whole) + "." + longStr(frac / 10);
        } else {
            num = longStr(whole) + "." + two((int) frac);
        }
        if (idx < 0) {
            idx = 0;
        }
        return num + " " + u[idx];
    }

    /** Parses a base-10 integer prefix; 0 when there is none. */
    static long toLong(String s) {
        long r = 0;
        int i = 0;
        int neg = 0;
        if (s.length() > 0 && s.charAt(0) == 45) {
            neg = 1;
            i = 1;
        }
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                break;
            }
            r = r * 10 + (long) (c - 48);
            i = i + 1;
        }
        if (neg != 0) {
            return -r;
        }
        return r;
    }

    static int isDigit(int c) {
        if (c >= 48 && c <= 57) {
            return 1;
        }
        return 0;
    }

    /** Natural, case-insensitive comparison on already lower-cased keys (StrCmpLogical-like). */
    static int natCmp(String a, String b) {
        int i = 0;
        int j = 0;
        int na = a.length();
        int nb = b.length();
        while (i < na && j < nb) {
            int ca = a.charAt(i);
            int cb = b.charAt(j);
            if (isDigit(ca) != 0 && isDigit(cb) != 0) {
                while (i < na && a.charAt(i) == 48) {
                    i = i + 1;
                }
                while (j < nb && b.charAt(j) == 48) {
                    j = j + 1;
                }
                int ei = i;
                while (ei < na && isDigit(a.charAt(ei)) != 0) {
                    ei = ei + 1;
                }
                int ej = j;
                while (ej < nb && isDigit(b.charAt(ej)) != 0) {
                    ej = ej + 1;
                }
                int la = ei - i;
                int lb = ej - j;
                if (la != lb) {
                    if (la < lb) {
                        return -1;
                    }
                    return 1;
                }
                while (i < ei) {
                    int x = a.charAt(i);
                    int y = b.charAt(j);
                    if (x != y) {
                        if (x < y) {
                            return -1;
                        }
                        return 1;
                    }
                    i = i + 1;
                    j = j + 1;
                }
            } else {
                if (ca != cb) {
                    if (ca < cb) {
                        return -1;
                    }
                    return 1;
                }
                i = i + 1;
                j = j + 1;
            }
        }
        if (na - i == nb - j) {
            return 0;
        }
        if (na - i < nb - j) {
            return -1;
        }
        return 1;
    }

    static int cmpLong(long a, long b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        return 0;
    }

    static String plural(int n) {
        return String.valueOf(n) + " 个项目";
    }
}

class FmHistory {
    ArrayList<String> items;
    int pos;

    FmHistory() {
        items = new ArrayList<String>();
        pos = -1;
    }

    void visit(String p) {
        if (pos >= 0 && pos < items.size() && items.get(pos).equals(p)) {
            return;
        }
        while (items.size() > pos + 1) {
            items.removeAt(items.size() - 1);
        }
        items.add(p);
        pos = items.size() - 1;
        if (items.size() > 60) {
            items.removeAt(0);
            pos = pos - 1;
        }
    }

    boolean canBack() {
        return pos > 0;
    }

    boolean canForward() {
        return pos >= 0 && pos < items.size() - 1;
    }

    String back() {
        if (!canBack()) {
            return "";
        }
        pos = pos - 1;
        return items.get(pos);
    }

    String forward() {
        if (!canForward()) {
            return "";
        }
        pos = pos + 1;
        return items.get(pos);
    }

    String current() {
        if (pos < 0 || pos >= items.size()) {
            return "";
        }
        return items.get(pos);
    }
}
