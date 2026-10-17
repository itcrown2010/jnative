/**
 * JNative Windows Task Monitor - process grouping and row builders for every list page.
 *
 * @author 张晓达
 */
class TmGroup {
    TmProc root;
    ArrayList<TmProc> members;
    int cat;
    long ws;
    long priv;
    int cpuX10;
    long disk;
    long io;
    long cpuNs;
    int threads;
    int handles;
    int anyWin;
    int ecoCount;
    int suspCount;
    int hungCount;
    String title;
    String key;
    int svcHost;
}

/** Column ids shared by Processes / Details / Users / App history. */
class Col {
    static int NAME = 0;
    static int STATUS = 1;
    static int CPU = 2;
    static int MEM = 3;
    static int DISK = 4;
    static int PUB = 5;
    static int PID = 6;
    static int USER = 7;
    static int THREADS = 8;
    static int HANDLES = 9;
    static int CMD = 10;
    static int TYPE = 11;
    static int ARCH = 12;
    static int DESC = 13;
    static int SESSION = 14;
    static int CPUTIME = 15;
    static int PRIO = 16;
    static int PATH = 17;
    static int IOTOTAL = 19;
}

class TmBuild {
    static ArrayList<TmGroup> groups = new ArrayList<TmGroup>();
    static String placeholderIcon = "C:\\Windows\\System32\\wininit.exe";

    /* --------------------------------------------------------------- helpers */
    static String userShort(String u) {
        if (u == null || u.equals("N/A")) {
            return "";
        }
        int i = u.lastIndexOf("\\");
        if (i >= 0 && i + 1 < u.length()) {
            return u.substring(i + 1, u.length());
        }
        return u;
    }

    static int isHung(TmProc p) {
        return (p.statusBits / 2) % 2;
    }

    static String statusText(int hung, int susp) {
        if (hung != 0) {
            return "未响应";
        }
        if (susp != 0) {
            return "已暂停";
        }
        return "";
    }

    static String prioText(int pc) {
        if (pc == 256) {
            return "实时";
        }
        if (pc == 128) {
            return "高";
        }
        if (pc == 32768) {
            return "高于正常";
        }
        if (pc == 32) {
            return "正常";
        }
        if (pc == 16384) {
            return "低于正常";
        }
        if (pc == 64) {
            return "低";
        }
        return "—";
    }

    static String typeText(int cat) {
        if (cat == 0) {
            return "应用";
        }
        if (cat == 1) {
            return "后台进程";
        }
        return "Windows 进程";
    }

    static String iconOf(TmProc p) {
        if (p.path != null && p.path.length() > 0) {
            return p.path;
        }
        return placeholderIcon;
    }

    static int cpuTier(int x10) {
        if (x10 < 10) {
            return 1;
        }
        if (x10 < 250) {
            return 2;
        }
        if (x10 < 500) {
            return 3;
        }
        if (x10 < 750) {
            return 4;
        }
        return 5;
    }

    static int memTier(long bytes) {
        if (TmStore.memTotal <= 0 || bytes < 0) {
            return 1;
        }
        long pm = (bytes * 1000L) / TmStore.memTotal;
        if (pm < 3) {
            return 1;
        }
        if (pm < 50) {
            return 2;
        }
        if (pm < 150) {
            return 3;
        }
        if (pm < 300) {
            return 4;
        }
        return 5;
    }

    static int diskTier(long bps) {
        if (bps <= 0) {
            return 0;
        }
        if (bps < 1048576L) {
            return 1;
        }
        if (bps < 5242880L) {
            return 2;
        }
        if (bps < 20971520L) {
            return 3;
        }
        if (bps < 52428800L) {
            return 4;
        }
        return 5;
    }

    static String friendlyName(TmProc p) {
        String ln = p.name.toLowerCase();
        if (p.pid == TmStore.selfPid || ln.equals("taskmgr.exe")) {
            return "任务管理器";
        }
        if (ln.equals("explorer.exe")) {
            return "Windows 资源管理器";
        }
        if (p.desc != null && p.desc.length() > 0) {
            return p.desc;
        }
        return Fmt.stripExe(p.name);
    }

    static int sameApp(TmProc par, TmProc p) {
        String pn = p.name.toLowerCase();
        if (pn.equals("conhost.exe") || pn.equals("openconsole.exe")) {
            return 1;
        }
        if (par.path.length() > 0 && p.path.length() > 0) {
            if (par.path.equalsIgnoreCase(p.path)) {
                return 1;
            }
            return 0;
        }
        if (par.path.length() == 0 && p.path.length() == 0 && par.name.equalsIgnoreCase(p.name)) {
            return 1;
        }
        return 0;
    }

    static int rootOf(ArrayList<TmProc> ps, int i) {
        int cur = i;
        int guard = 0;
        while (guard < 24) {
            TmProc p = ps.get(cur);
            Integer pi = TmStore.byPid.get(String.valueOf(p.ppid));
            if (pi == null) {
                break;
            }
            int pix = pi.intValue();
            if (pix == cur) {
                break;
            }
            TmProc par = ps.get(pix);
            if (par.pid <= 4) {
                break;
            }
            if (p.startSec >= 0 && par.startSec > p.startSec) {
                break;
            }
            if (sameApp(par, p) == 0) {
                break;
            }
            cur = pix;
            guard = guard + 1;
        }
        return cur;
    }

    static void group() {
        ArrayList<TmProc> ps = TmStore.procs;
        int n = ps.size();
        int[] slot = new int[n];
        int i = 0;
        while (i < n) {
            slot[i] = -1;
            i = i + 1;
        }
        ArrayList<TmGroup> out = new ArrayList<TmGroup>();
        i = 0;
        while (i < n) {
            TmProc p = ps.get(i);
            if (p.pid == 0) {
                i = i + 1;
                continue;
            }
            int r = rootOf(ps, i);
            TmGroup g = null;
            if (slot[r] >= 0) {
                g = out.get(slot[r]);
            } else {
                g = new TmGroup();
                g.root = ps.get(r);
                g.members = new ArrayList<TmProc>();
                slot[r] = out.size();
                out.add(g);
            }
            g.members.add(p);
            if (p.ws > 0) {
                g.ws = g.ws + p.ws;
            }
            if (p.priv > 0) {
                g.priv = g.priv + p.priv;
            }
            g.cpuX10 = g.cpuX10 + p.cpuX10;
            g.disk = g.disk + p.diskBps;
            if (p.cpuNs > 0) {
                g.cpuNs = g.cpuNs + p.cpuNs;
            }
            if (p.ioTotal > 0) {
                g.io = g.io + p.ioTotal;
            }
            if (p.threads > 0) {
                g.threads = g.threads + p.threads;
            }
            if (p.handles > 0) {
                g.handles = g.handles + p.handles;
            }
            if (p.winCount > 0) {
                g.anyWin = 1;
            }
            if (p.eco == 1) {
                g.ecoCount = g.ecoCount + 1;
            }
            if (p.suspended != 0) {
                g.suspCount = g.suspCount + 1;
            }
            if (isHung(p) != 0) {
                g.hungCount = g.hungCount + 1;
            }
            i = i + 1;
        }
        i = 0;
        while (i < out.size()) {
            TmGroup g = out.get(i);
            if (g.cpuX10 > 1000) {
                g.cpuX10 = 1000;
            }
            if (g.anyWin != 0) {
                g.cat = 0;
            } else if (g.root.groupClass == 2) {
                g.cat = 2;
            } else {
                g.cat = 1;
            }
            g.key = "g:" + g.root.key;
            g.title = friendlyName(g.root);
            g.svcHost = 0;
            if (g.root.name.equalsIgnoreCase("svchost.exe")) {
                int sc = TmStore.svcCountForPid(g.root.pid);
                if (sc > 0) {
                    g.svcHost = sc;
                    TmSvc first = firstSvc(g.root.pid);
                    String dn = g.root.desc;
                    if (first != null) {
                        dn = first.display;
                    }
                    g.title = "服务主机: " + dn;
                    if (sc > 1) {
                        g.title = g.title + " (" + String.valueOf(sc) + ")";
                    }
                }
            }
            i = i + 1;
        }
        groups = out;
    }

    static TmSvc firstSvc(int pid) {
        int i = 0;
        while (i < TmStore.svcs.size()) {
            if (TmStore.svcs.get(i).pid == pid) {
                return TmStore.svcs.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static int matchesProc(TmProc p, String q) {
        if (q.length() == 0) {
            return 1;
        }
        if (TmStore.has(p.name, q) != 0 || TmStore.has(p.desc, q) != 0 || TmStore.has(p.publisher, q) != 0) {
            return 1;
        }
        if (TmStore.has(String.valueOf(p.pid), q) != 0 || TmStore.has(p.path, q) != 0) {
            return 1;
        }
        return 0;
    }

    static int matchesGroup(TmGroup g, String q) {
        if (q.length() == 0) {
            return 1;
        }
        if (TmStore.has(g.title, q) != 0) {
            return 1;
        }
        int i = 0;
        while (i < g.members.size()) {
            if (matchesProc(g.members.get(i), q) != 0) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    /* --------------------------------------------------------- shared filling */
    /**
     * Fill cells/heat/sort keys of a process-like row. v* describe the entity.
     */
    static void fill(TmGrid g, TmRow r, String title, TmProc p, int cat, int count, int cpuX10, long ws, long disk,
            long cpuNs, long io, int threads, int handles, int hung, int susp, int showHeat) {
        int nv = 0;
        int k = 0;
        int total = g.layoutCols(Math.max(g.hw, 400));
        r.cells = new ArrayList<String>();
        while (k < total) {
            TmCol c = g.visCol(k);
            int id = c.id;
            String s = "";
            if (id == Col.NAME) {
                s = title;
            } else if (id == Col.STATUS) {
                s = statusText(hung, susp);
            } else if (id == Col.CPU) {
                s = Fmt.pct(cpuX10);
                if (showHeat != 0) {
                    r.heat[k] = cpuTier(cpuX10);
                }
            } else if (id == Col.MEM) {
                s = Fmt.memMB(ws);
                if (showHeat != 0) {
                    r.heat[k] = memTier(ws);
                }
            } else if (id == Col.DISK) {
                s = Fmt.diskRate(disk);
                if (showHeat != 0) {
                    r.heat[k] = diskTier(disk);
                }
            } else if (id == Col.PUB) {
                s = p.publisher;
            } else if (id == Col.PID) {
                if (count <= 1) {
                    s = String.valueOf(p.pid);
                }
            } else if (id == Col.USER) {
                s = userShort(p.user);
            } else if (id == Col.THREADS) {
                s = Fmt.nonNeg((long) threads);
            } else if (id == Col.HANDLES) {
                s = Fmt.nonNeg((long) handles);
            } else if (id == Col.CMD) {
                s = p.cmd;
            } else if (id == Col.TYPE) {
                s = typeText(cat);
            } else if (id == Col.ARCH) {
                s = p.arch;
            } else if (id == Col.DESC) {
                s = p.desc;
            } else if (id == Col.SESSION) {
                s = Fmt.nonNeg((long) p.session);
            } else if (id == Col.CPUTIME) {
                s = Fmt.cpuTime(cpuNs);
            } else if (id == Col.PRIO) {
                s = prioText(p.prio);
            } else if (id == Col.PATH) {
                s = p.path;
            } else if (id == Col.IOTOTAL) {
                s = Fmt.bytes(io);
            }
            r.cells.add(s);
            k = k + 1;
        }
        /* sort keys */
        int sc = g.sortCol;
        r.tie = title.toLowerCase();
        r.ss = r.tie;
        r.sn = 0;
        if (sc == Col.STATUS) {
            r.ss = statusText(hung, susp);
        } else if (sc == Col.CPU) {
            r.sn = (long) cpuX10;
        } else if (sc == Col.MEM) {
            r.sn = ws;
        } else if (sc == Col.DISK) {
            r.sn = disk;
        } else if (sc == Col.PUB) {
            r.ss = p.publisher.toLowerCase();
        } else if (sc == Col.PID) {
            r.sn = (long) p.pid;
        } else if (sc == Col.USER) {
            r.ss = userShort(p.user).toLowerCase();
        } else if (sc == Col.THREADS) {
            r.sn = (long) threads;
        } else if (sc == Col.HANDLES) {
            r.sn = (long) handles;
        } else if (sc == Col.CMD) {
            r.ss = p.cmd.toLowerCase();
        } else if (sc == Col.TYPE) {
            r.sn = (long) cat;
        } else if (sc == Col.ARCH) {
            r.ss = p.arch.toLowerCase();
        } else if (sc == Col.DESC) {
            r.ss = p.desc.toLowerCase();
        } else if (sc == Col.SESSION) {
            r.sn = (long) p.session;
        } else if (sc == Col.CPUTIME) {
            r.sn = cpuNs;
        } else if (sc == Col.PRIO) {
            r.sn = (long) p.prio;
        } else if (sc == Col.PATH) {
            r.ss = p.path.toLowerCase();
        } else if (sc == Col.IOTOTAL) {
            r.sn = io;
        }
        r.pid = p.pid;
        r.cpuX10 = cpuX10;
    }

    static int useStrSort(int sc) {
        if (sc == Col.NAME || sc == Col.STATUS || sc == Col.PUB || sc == Col.USER || sc == Col.CMD
                || sc == Col.ARCH || sc == Col.DESC || sc == Col.PATH) {
            return 1;
        }
        return 0;
    }

    static String pidList(ArrayList<TmProc> ms) {
        String s = "";
        int i = 0;
        while (i < ms.size()) {
            if (i > 0) {
                s = s + ",";
            }
            s = s + String.valueOf(ms.get(i).pid);
            i = i + 1;
        }
        return s;
    }

    /* ------------------------------------------------------------- Processes */
    static TmRow groupRow(TmGrid g, TmGroup gr) {
        TmRow r = new TmRow();
        int n = gr.members.size();
        String title = gr.title;
        if (n > 1 && gr.svcHost == 0) {
            title = title + " (" + String.valueOf(n) + ")";
        }
        fill(g, r, title, gr.root, gr.cat, n, gr.cpuX10, gr.ws, gr.disk, gr.cpuNs, gr.io, gr.threads, gr.handles,
                gr.hungCount, 0, 1);
        if (n == gr.ecoCount && n > 0) {
            r.status = 1;
        } else if (gr.suspCount == n && n > 0) {
            r.status = 2;
        }
        r.key = gr.key;
        r.icon = iconOf(gr.root);
        r.members = pidList(gr.members);
        r.count = n;
        r.cat = gr.cat;
        r.root = gr.root;
        r.ref = gr.root.name;
        if (n > 1 || gr.svcHost > 0) {
            r.expandable = 1;
            r.expanded = g.isExpanded(r.key);
        }
        if (gr.root.pid == TmStore.selfPid) {
            r.selfApp = 1;
        }
        return r;
    }

    static void addGroupChildren(TmGrid g, TmGroup gr, ArrayList<TmRow> out, String q) {
        ArrayList<TmRow> kids = new ArrayList<TmRow>();
        if (gr.svcHost > 0) {
            int i = 0;
            while (i < TmStore.svcs.size()) {
                TmSvc s = TmStore.svcs.get(i);
                if (s.pid == gr.root.pid) {
                    TmRow r = new TmRow();
                    TmProc rp = gr.root;
                    fill(g, r, s.display, rp, gr.cat, 2, 0, -1, 0, -1, -1, -1, -1, 0, 0, 0);
                    int k = 0;
                    int total = g.layoutCols(Math.max(g.hw, 400));
                    while (k < total) {
                        int id = g.visCol(k).id;
                        if (id != Col.NAME) {
                            r.cells.set(k, "");
                        }
                        k = k + 1;
                    }
                    r.key = "s:" + s.name;
                    r.depth = 1;
                    r.icon = iconOf(gr.root);
                    r.pid = gr.root.pid;
                    r.ctxKind = 3;
                    r.ref = s.name;
                    r.tie = s.display.toLowerCase();
                    r.ss = r.tie;
                    kids.add(r);
                }
                i = i + 1;
            }
        } else {
            int i = 0;
            while (i < gr.members.size()) {
                TmProc p = gr.members.get(i);
                TmRow r = new TmRow();
                String t = friendlyName(p);
                fill(g, r, t, p, gr.cat, 1, p.cpuX10, p.ws, p.diskBps, p.cpuNs, p.ioTotal, p.threads, p.handles,
                        isHung(p), p.suspended, 1);
                if (p.eco == 1) {
                    r.status = 1;
                } else if (p.suspended != 0) {
                    r.status = 2;
                }
                r.key = "p:" + p.key;
                r.depth = 1;
                r.icon = iconOf(p);
                r.members = String.valueOf(p.pid);
                r.root = p;
                r.ref = p.name;
                r.cat = gr.cat;
                kids.add(r);
                i = i + 1;
            }
        }
        TmSort.sort(kids, useStrSort(g.sortCol), g.sortAsc);
        int j = 0;
        while (j < kids.size()) {
            out.add(kids.get(j));
            j = j + 1;
        }
    }

    static void buildProcesses(TmGrid g) {
        group();
        String q = TmApp.filter;
        ArrayList<TmRow> a0 = new ArrayList<TmRow>();
        ArrayList<TmRow> a1 = new ArrayList<TmRow>();
        ArrayList<TmRow> a2 = new ArrayList<TmRow>();
        ArrayList<TmGroup> g0 = new ArrayList<TmGroup>();
        ArrayList<TmGroup> g1 = new ArrayList<TmGroup>();
        ArrayList<TmGroup> g2 = new ArrayList<TmGroup>();
        int i = 0;
        while (i < groups.size()) {
            TmGroup gr = groups.get(i);
            if (matchesGroup(gr, q) != 0) {
                TmRow r = groupRow(g, gr);
                if (gr.cat == 0) {
                    a0.add(r);
                    g0.add(gr);
                } else if (gr.cat == 1) {
                    a1.add(r);
                    g1.add(gr);
                } else {
                    a2.add(r);
                    g2.add(gr);
                }
            }
            i = i + 1;
        }
        int us = useStrSort(g.sortCol);
        /* remember group object by key for child expansion */
        ArrayList<TmRow> out = new ArrayList<TmRow>();
        appendSection(g, out, a0, "应用", us, q);
        appendSection(g, out, a1, "后台进程", us, q);
        appendSection(g, out, a2, "Windows 进程", us, q);
        g.rows = out;
        if (out.size() == 0) {
            if (q.length() > 0) {
                g.emptyText = "没有与“" + TmApp.filterRaw + "”匹配的进程";
            } else {
                g.emptyText = "正在读取进程…";
            }
        } else {
            g.emptyText = "";
        }
        g.recomputeContent();
    }

    static TmGroup groupByKey(String key) {
        int i = 0;
        while (i < groups.size()) {
            if (groups.get(i).key.equals(key)) {
                return groups.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static void appendSection(TmGrid g, ArrayList<TmRow> out, ArrayList<TmRow> list, String title, int us, String q) {
        if (list.size() == 0) {
            return;
        }
        TmSort.sort(list, us, g.sortAsc);
        TmRow h = new TmRow();
        h.kind = 1;
        h.key = "sec:" + title;
        h.cells.add(title + " (" + String.valueOf(list.size()) + ")");
        out.add(h);
        int i = 0;
        while (i < list.size()) {
            TmRow r = list.get(i);
            out.add(r);
            if (r.expandable != 0 && r.expanded != 0) {
                TmGroup gr = groupByKey(r.key);
                if (gr != null) {
                    addGroupChildren(g, gr, out, q);
                }
            }
            i = i + 1;
        }
    }

    /* --------------------------------------------------------------- Details */
    static void buildDetails(TmGrid g) {
        String q = TmApp.filter;
        ArrayList<TmRow> list = new ArrayList<TmRow>();
        int i = 0;
        while (i < TmStore.procs.size()) {
            TmProc p = TmStore.procs.get(i);
            if (matchesProc(p, q) != 0) {
                TmRow r = new TmRow();
                int cat = 1;
                if (p.winCount > 0) {
                    cat = 0;
                } else if (p.groupClass == 2) {
                    cat = 2;
                }
                fill(g, r, p.name, p, cat, 1, p.cpuX10, p.ws, p.diskBps, p.cpuNs, p.ioTotal, p.threads, p.handles,
                        isHung(p), p.suspended, 0);
                if (p.eco == 1) {
                    r.status = 1;
                    int k = 0;
                    int total = g.layoutCols(Math.max(g.hw, 400));
                    while (k < total) {
                        if (g.visCol(k).id == Col.STATUS) {
                            if (r.cells.get(k).length() == 0) {
                                r.cells.set(k, "效率模式");
                            }
                        }
                        k = k + 1;
                    }
                }
                if (r.cells.size() > 0) {
                    /* status column default text */
                    int k2 = 0;
                    int tot2 = g.layoutCols(Math.max(g.hw, 400));
                    while (k2 < tot2) {
                        if (g.visCol(k2).id == Col.STATUS && r.cells.get(k2).length() == 0) {
                            r.cells.set(k2, "正在运行");
                        }
                        k2 = k2 + 1;
                    }
                }
                r.status = 0;
                r.key = "d:" + p.key;
                r.icon = iconOf(p);
                r.members = String.valueOf(p.pid);
                r.root = p;
                r.ref = p.name;
                r.cat = cat;
                list.add(r);
            }
            i = i + 1;
        }
        TmSort.sort(list, useStrSort(g.sortCol), g.sortAsc);
        g.rows = list;
        if (list.size() == 0) {
            if (q.length() > 0) {
                g.emptyText = "没有与“" + TmApp.filterRaw + "”匹配的进程";
            } else {
                g.emptyText = "正在读取进程…";
            }
        } else {
            g.emptyText = "";
        }
        g.recomputeContent();
    }

    /* -------------------------------------------------------------- Services */
    static String svcState(String s) {
        if (s.equals("running")) {
            return "正在运行";
        }
        if (s.equals("stopped")) {
            return "已停止";
        }
        if (s.equals("paused")) {
            return "已暂停";
        }
        if (s.equals("starting")) {
            return "正在启动";
        }
        if (s.equals("stopping")) {
            return "正在停止";
        }
        return s;
    }

    static String startTypeText(String s) {
        if (s.equals("auto")) {
            return "自动";
        }
        if (s.equals("auto_delayed")) {
            return "自动(延迟启动)";
        }
        if (s.equals("manual")) {
            return "手动";
        }
        if (s.equals("disabled")) {
            return "已禁用";
        }
        if (s.equals("boot")) {
            return "引导";
        }
        if (s.equals("system")) {
            return "系统";
        }
        return s;
    }

    /* service col ids: 0 name, 1 pid, 2 desc, 3 status, 4 group, 5 display, 6 start type */
    static void buildServices(TmGrid g) {
        String q = TmApp.filter;
        ArrayList<TmRow> list = new ArrayList<TmRow>();
        int i = 0;
        while (i < TmStore.svcs.size()) {
            TmSvc s = TmStore.svcs.get(i);
            int ok = 1;
            if (q.length() > 0) {
                ok = 0;
                if (TmStore.has(s.name, q) != 0 || TmStore.has(s.display, q) != 0 || TmStore.has(s.desc, q) != 0) {
                    ok = 1;
                }
                if (s.pid > 0 && TmStore.has(String.valueOf(s.pid), q) != 0) {
                    ok = 1;
                }
            }
            if (ok != 0) {
                TmRow r = new TmRow();
                int total = g.layoutCols(Math.max(g.hw, 400));
                int k = 0;
                while (k < total) {
                    int id = g.visCol(k).id;
                    String c = "";
                    if (id == 0) {
                        c = s.name;
                    } else if (id == 1) {
                        if (s.pid > 0) {
                            c = String.valueOf(s.pid);
                        }
                    } else if (id == 2) {
                        c = s.display;
                    } else if (id == 3) {
                        c = svcState(s.state);
                    } else if (id == 4) {
                        c = s.group;
                    } else if (id == 5) {
                        c = s.desc;
                    } else if (id == 6) {
                        c = startTypeText(s.startType);
                    }
                    r.cells.add(c);
                    k = k + 1;
                }
                int sc = g.sortCol;
                r.tie = s.name.toLowerCase();
                r.ss = r.tie;
                r.sn = 0;
                if (sc == 1) {
                    r.sn = (long) s.pid;
                } else if (sc == 2) {
                    r.ss = s.display.toLowerCase();
                } else if (sc == 3) {
                    r.ss = s.state;
                } else if (sc == 4) {
                    r.ss = s.group.toLowerCase();
                } else if (sc == 5) {
                    r.ss = s.desc.toLowerCase();
                } else if (sc == 6) {
                    r.ss = s.startType;
                }
                r.key = "v:" + s.name;
                r.ref = s.name;
                r.pid = s.pid;
                r.iconGlyph = 59893;
                if (s.image.length() > 0) {
                    r.icon = "";
                }
                list.add(r);
            }
            i = i + 1;
        }
        int sc2 = g.sortCol;
        int us = 1;
        if (sc2 == 1) {
            us = 0;
        }
        TmSort.sort(list, us, g.sortAsc);
        g.rows = list;
        if (list.size() == 0) {
            g.emptyText = "没有匹配的服务";
        } else {
            g.emptyText = "";
        }
        g.recomputeContent();
    }

    /* --------------------------------------------------------------- Startup */
    /* col ids: 0 name, 1 publisher, 2 status, 3 impact, 4 source */
    static String sourceText(String s) {
        if (s.equals("HKCU_Run")) {
            return "注册表(当前用户)";
        }
        if (s.equals("HKLM_Run")) {
            return "注册表(所有用户)";
        }
        if (s.equals("HKLM_Run32")) {
            return "注册表(所有用户, 32 位)";
        }
        if (s.equals("StartupFolder")) {
            return "启动文件夹(当前用户)";
        }
        if (s.equals("CommonStartupFolder")) {
            return "启动文件夹(所有用户)";
        }
        return s;
    }

    static void buildStartup(TmGrid g) {
        String q = TmApp.filter;
        ArrayList<TmRow> list = new ArrayList<TmRow>();
        int i = 0;
        while (i < TmStore.startups.size()) {
            TmStartup s = TmStore.startups.get(i);
            int ok = 1;
            if (q.length() > 0) {
                ok = 0;
                if (TmStore.has(s.name, q) != 0 || TmStore.has(s.publisher, q) != 0 || TmStore.has(s.command, q) != 0) {
                    ok = 1;
                }
            }
            if (ok != 0) {
                TmRow r = new TmRow();
                int total = g.layoutCols(Math.max(g.hw, 400));
                int k = 0;
                String en = "已禁用";
                if (s.enabled != 0) {
                    en = "已启用";
                }
                while (k < total) {
                    int id = g.visCol(k).id;
                    String c = "";
                    if (id == 0) {
                        c = s.name;
                    } else if (id == 1) {
                        c = s.publisher;
                    } else if (id == 2) {
                        c = en;
                    } else if (id == 3) {
                        c = "未测量";
                    } else if (id == 4) {
                        c = sourceText(s.source);
                    } else if (id == 5) {
                        c = s.command;
                    }
                    r.cells.add(c);
                    k = k + 1;
                }
                int sc = g.sortCol;
                r.tie = s.name.toLowerCase();
                r.ss = r.tie;
                if (sc == 1) {
                    r.ss = s.publisher.toLowerCase();
                } else if (sc == 2) {
                    r.ss = en;
                } else if (sc == 4) {
                    r.ss = s.source;
                } else if (sc == 5) {
                    r.ss = s.command.toLowerCase();
                }
                r.key = "t:" + s.id;
                r.ref = s.id;
                if (s.path.length() > 0) {
                    r.icon = s.path;
                } else {
                    r.icon = placeholderIcon;
                }
                r.dim = 0;
                if (s.enabled == 0) {
                    r.dim = 1;
                }
                list.add(r);
            }
            i = i + 1;
        }
        TmSort.sort(list, 1, g.sortAsc);
        g.rows = list;
        if (list.size() == 0) {
            if (TmStore.startupLoaded == 0) {
                g.emptyText = "正在读取启动项…";
            } else {
                g.emptyText = "没有启动项";
            }
        } else {
            g.emptyText = "";
        }
        g.recomputeContent();
    }

    /* ----------------------------------------------------------------- Users */
    /* col ids reuse Col: 0 name, 1 status, 2 cpu, 3 mem, 4 disk */
    static void buildUsers(TmGrid g) {
        group();
        String q = TmApp.filter;
        ArrayList<TmRow> out = new ArrayList<TmRow>();
        int si = 0;
        while (si < TmStore.sessions.size()) {
            TmSession se = TmStore.sessions.get(si);
            String un = se.user.toLowerCase();
            ArrayList<TmGroup> mine = new ArrayList<TmGroup>();
            int cpu = 0;
            long ws = 0;
            long disk = 0;
            int gi = 0;
            while (gi < groups.size()) {
                TmGroup gr = groups.get(gi);
                String gu = userShort(gr.root.user).toLowerCase();
                if (gu.equals(un) && gr.root.session == se.id) {
                    if (matchesGroup(gr, q) != 0) {
                        mine.add(gr);
                    }
                    cpu = cpu + gr.cpuX10;
                    ws = ws + gr.ws;
                    disk = disk + gr.disk;
                }
                gi = gi + 1;
            }
            if (q.length() > 0 && mine.size() == 0) {
                si = si + 1;
                continue;
            }
            TmRow r = new TmRow();
            int total = g.layoutCols(Math.max(g.hw, 400));
            int k = 0;
            String title = se.user + " (" + String.valueOf(mine.size()) + ")";
            while (k < total) {
                int id = g.visCol(k).id;
                String c = "";
                if (id == Col.NAME) {
                    c = title;
                } else if (id == Col.STATUS) {
                    c = stateText(se.state);
                } else if (id == Col.CPU) {
                    c = Fmt.pct(cpu);
                    r.heat[k] = cpuTier(cpu);
                } else if (id == Col.MEM) {
                    c = Fmt.memMB(ws);
                    r.heat[k] = memTier(ws);
                } else if (id == Col.DISK) {
                    c = Fmt.diskRate(disk);
                    r.heat[k] = diskTier(disk);
                } else if (id == Col.SESSION) {
                    c = String.valueOf(se.id);
                }
                r.cells.add(c);
                k = k + 1;
            }
            r.key = "u:" + String.valueOf(se.id);
            r.iconGlyph = 59158;
            r.expandable = 1;
            r.expanded = g.isExpanded(r.key);
            r.ownerSession = se.id;
            r.ctxKind = 5;
            r.tie = un;
            r.ss = un;
            r.sn = (long) cpu;
            out.add(r);
            if (r.expanded != 0) {
                ArrayList<TmRow> kids = new ArrayList<TmRow>();
                int mi = 0;
                while (mi < mine.size()) {
                    TmRow cr = groupRow(g, mine.get(mi));
                    cr.key = "ug:" + String.valueOf(se.id) + ":" + cr.key;
                    cr.depth = 1;
                    cr.expandable = 0;
                    kids.add(cr);
                    mi = mi + 1;
                }
                TmSort.sort(kids, useStrSort(g.sortCol), g.sortAsc);
                int j = 0;
                while (j < kids.size()) {
                    out.add(kids.get(j));
                    j = j + 1;
                }
            }
            si = si + 1;
        }
        g.rows = out;
        if (out.size() == 0) {
            g.emptyText = "没有用户会话";
        } else {
            g.emptyText = "";
        }
        g.recomputeContent();
    }

    static String stateText(String s) {
        if (s.equals("active")) {
            return "活动";
        }
        if (s.equals("disconnected")) {
            return "已断开连接";
        }
        if (s.equals("idle")) {
            return "空闲";
        }
        return s;
    }

    /* ----------------------------------------------------------- App history */
    static void buildHistory(TmGrid g) {
        group();
        String q = TmApp.filter;
        ArrayList<TmRow> list = new ArrayList<TmRow>();
        int i = 0;
        while (i < groups.size()) {
            TmGroup gr = groups.get(i);
            if (gr.cat != 2 && matchesGroup(gr, q) != 0) {
                TmRow r = new TmRow();
                fill(g, r, gr.title, gr.root, gr.cat, gr.members.size(), gr.cpuX10, gr.ws, gr.disk, gr.cpuNs, gr.io,
                        gr.threads, gr.handles, 0, 0, 0);
                r.key = "h:" + gr.key;
                r.icon = iconOf(gr.root);
                r.members = pidList(gr.members);
                list.add(r);
            }
            i = i + 1;
        }
        TmSort.sort(list, useStrSort(g.sortCol), g.sortAsc);
        g.rows = list;
        if (list.size() == 0) {
            g.emptyText = "没有匹配的应用";
        } else {
            g.emptyText = "";
        }
        g.recomputeContent();
    }
}
