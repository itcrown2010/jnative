/**
 * JNative Windows Task Monitor - native bindings (jn_tm) and live data model.
 *
 * @author 张晓达
 */
class TmNative {
    @Native(symbol = "jn_tm_free", library = "jn_tm")
    static native void free(NativePtr p);

    @Native(symbol = "jn_tm_perf", library = "jn_tm")
    static native NativePtr perf();

    @Native(symbol = "jn_tm_procx", library = "jn_tm")
    static native NativePtr procx();

    @Native(symbol = "jn_tm_services", library = "jn_tm")
    static native NativePtr services();

    @Native(symbol = "jn_tm_service_control", library = "jn_tm")
    static native int serviceControl(String name, int op);

    @Native(symbol = "jn_tm_service_open_pid", library = "jn_tm")
    static native int serviceOpenPid(String name);

    @Native(symbol = "jn_tm_open_services_msc", library = "jn_tm")
    static native int openServicesMsc();

    @Native(symbol = "jn_tm_sessions", library = "jn_tm")
    static native NativePtr sessions();

    @Native(symbol = "jn_tm_session_action", library = "jn_tm")
    static native int sessionAction(int sessionId, int op);

    @Native(symbol = "jn_tm_startup", library = "jn_tm")
    static native NativePtr startup();

    @Native(symbol = "jn_tm_startup_set", library = "jn_tm")
    static native int startupSet(String id, int enable);

    @Native(symbol = "jn_tm_proc_kill_tree", library = "jn_tm")
    static native int killTree(int pid);

    @Native(symbol = "jn_tm_proc_set_priority", library = "jn_tm")
    static native int setPriority(int pid, int cls);

    @Native(symbol = "jn_tm_proc_suspend", library = "jn_tm")
    static native int suspend(int pid, int on);

    @Native(symbol = "jn_tm_proc_set_affinity", library = "jn_tm")
    static native int setAffinity(int pid, long mask);

    @Native(symbol = "jn_tm_proc_get_affinity", library = "jn_tm")
    static native long getAffinity(int pid);

    @Native(symbol = "jn_tm_proc_create_dump", library = "jn_tm")
    static native int createDump(int pid, String outPath);

    @Native(symbol = "jn_tm_proc_properties", library = "jn_tm")
    static native int properties(int pid);

    @Native(symbol = "jn_tm_proc_goto_services", library = "jn_tm")
    static native int gotoServices(int pid);

    @Native(symbol = "jn_tm_search_online", library = "jn_tm")
    static native int searchOnline(String query);

    @Native(symbol = "jn_tm_open_path", library = "jn_tm")
    static native int openPath(String path);

    @Native(symbol = "jn_tm_copy_text", library = "jn_tm")
    static native int copyText(String text);

    @Native(symbol = "jn_tm_open_resource_monitor", library = "jn_tm")
    static native int openResourceMonitor();

    @Native(symbol = "jn_tm_open_system_settings_apps", library = "jn_tm")
    static native int openSystemSettingsApps();

    @Native(symbol = "jn_tm_system_info", library = "jn_tm")
    static native NativePtr systemInfo();

    @Native(symbol = "jn_tm_is_elevated", library = "jn_tm")
    static native int isElevated();

    @Native(symbol = "jn_tm_relaunch_elevated", library = "jn_tm")
    static native int relaunchElevated();

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

class TmProc {
    int pid;
    int ppid;
    long startSec;
    int startUsec;
    String key;
    String name;
    String path;
    String user;
    String publisher;
    String desc;
    String cmd;
    String arch;
    long cpuNs;
    long prevCpuNs;
    long prevMono;
    int cpuX10;
    long ws;
    long priv;
    int threads;
    int winCount;
    int groupClass;
    int statusBits;
    int eco;
    long ioTotal;
    long prevIo;
    long diskBps;
    int handles;
    int gdi;
    int userObj;
    int session;
    int elevated;
    int prio;
    int suspended;
    int hasPrev;
    int hasX;
    long prevNt;
    long prevMono2;
    int seen;
    int group;
    int groupRoot;
    String disp;

    TmProc() {
        pid = 0;
        ppid = -1;
        startSec = -1;
        startUsec = -1;
        key = "";
        name = "";
        path = "";
        user = "";
        publisher = "";
        desc = "";
        cmd = "";
        arch = "";
        cpuNs = -1;
        prevCpuNs = -1;
        prevMono = 0;
        cpuX10 = 0;
        ws = -1;
        priv = -1;
        threads = -1;
        winCount = 0;
        groupClass = 0;
        statusBits = 0;
        eco = -1;
        ioTotal = -1;
        prevIo = -1;
        diskBps = 0;
        handles = -1;
        gdi = -1;
        userObj = -1;
        session = -1;
        elevated = -1;
        prio = 0;
        suspended = 0;
        hasPrev = 0;
        hasX = 0;
        prevNt = -1;
        prevMono2 = 0;
        seen = 0;
        group = 0;
        groupRoot = -1;
        disp = "";
    }

    String title() {
        if (disp.length() > 0) {
            return disp;
        }
        return Fmt.stripExe(name);
    }
}

class TmSvc {
    String name;
    String display;
    int pid;
    String state;
    String startType;
    String desc;
    String group;
    String image;
    String account;
}

class TmStartup {
    String id;
    String name;
    String publisher;
    String command;
    String source;
    int enabled;
    int impact;
    String path;
}

class TmSession {
    int id;
    String user;
    String domain;
    String state;
    String client;
    String station;
    long logon;
}

class TmDisk {
    int id;
    String label;
    String model;
    String media;
    long size;
    int actX10;
    long readBps;
    long writeBps;
    int respX10;
    String letters;
    long[] hAct;
    long[] hRead;
    long[] hWrite;

    TmDisk() {
        hAct = new long[60];
        hRead = new long[60];
        hWrite = new long[60];
    }
}

class TmNet {
    String id;
    String name;
    String desc;
    String kind;
    long link;
    long sendBps;
    long recvBps;
    String ipv4;
    String ipv6;
    long[] hSend;
    long[] hRecv;

    TmNet() {
        hSend = new long[60];
        hRecv = new long[60];
    }
}

class TmGpu {
    String id;
    String name;
    int utilX10;
    long vramUsed;
    long vramTotal;
    long sharedUsed;
    long sharedTotal;
    int temp;
    String driver;
    long[] hUtil;
    long[] hVram;
    long[] hShared;

    TmGpu() {
        hUtil = new long[60];
        hVram = new long[60];
        hShared = new long[60];
    }
}

class TmStore {
    static ArrayList<TmProc> procs = new ArrayList<TmProc>();
    static HashMap<String, Integer> byKey = new HashMap<String, Integer>();
    static HashMap<String, Integer> byPid = new HashMap<String, Integer>();
    static ArrayList<TmSvc> svcs = new ArrayList<TmSvc>();
    static ArrayList<TmStartup> startups = new ArrayList<TmStartup>();
    static ArrayList<TmSession> sessions = new ArrayList<TmSession>();
    static ArrayList<TmDisk> disks = new ArrayList<TmDisk>();
    static ArrayList<TmNet> nets = new ArrayList<TmNet>();
    static ArrayList<TmGpu> gpus = new ArrayList<TmGpu>();

    static int tickNo = 0;
    static int ncpu = 1;
    static long lastMono = 0;
    static int selfPid = 0;
    static int sampleOk = 0;
    static int perfOk = 0;
    static long lastSampleMs = 0;

    /* H line */
    static long uptimeSec = 0;
    static int procCount = 0;
    static int threadCount = 0;
    static int handleCount = 0;
    static long memTotal = 0;
    static long memAvail = 0;
    static long memInUse = 0;
    static long commitUsed = 0;
    static long commitLimit = 0;
    static long cached = 0;
    static long pagedPool = 0;
    static long nonPagedPool = 0;
    static long compressed = 0;
    static int memSpeed = -1;
    static int slotsUsed = -1;
    static int slotsTotal = -1;
    static String formFactor = "";

    /* T / I lines */
    static int cpuTotalX10 = 0;
    static int cpuKernelX10 = 0;
    static int cpuMhz = 0;
    static int cpuBaseMhz = 0;
    static String cpuName = "";
    static int sockets = -1;
    static int cores = -1;
    static int logical = -1;
    static int l1 = -1;
    static int l2 = -1;
    static int l3 = -1;
    static int virt = -1;
    static String arch = "";
    static int[] coreBusy = new int[256];
    static int[] coreMhz = new int[256];

    /* system info */
    static String hostName = "";
    static String osCaption = "";
    static String osBuild = "";
    static String userName = "";
    static int isAdmin = 0;

    /* histories (60 s) */
    static long[] hCpu = new long[60];
    static long[] hKernel = new long[60];
    static long[] hMem = new long[60];
    static long[] hCore = new long[256 * 60];

    static int svcTick = -100;
    static int startupLoaded = 0;
    static int sessionTick = -100;

    static void push(long[] a, long v) {
        int i = 0;
        while (i < 59) {
            a[i] = a[i + 1];
            i = i + 1;
        }
        a[59] = v;
    }

    static void pushAt(long[] a, int base, long v) {
        int i = 0;
        while (i < 59) {
            a[base + i] = a[base + i + 1];
            i = i + 1;
        }
        a[base + 59] = v;
    }

    static void init() {
        selfPid = AmNative.selfPid();
        String t = TmNative.take(TmNative.systemInfo());
        ArrayList<String> lines = AmParse.splitLines(t);
        int i = 0;
        while (i < lines.size()) {
            ArrayList<String> c = AmParse.splitTab(lines.get(i));
            if (c.size() >= 6 && c.get(0).equals("M")) {
                hostName = c.get(1);
                osCaption = c.get(2);
                osBuild = c.get(3);
                userName = c.get(4);
                isAdmin = AmParse.intOr(c.get(5), 0);
            }
            i = i + 1;
        }
    }

    /* ---------------------------------------------------------------- perf */
    static String field(ArrayList<String> c, int i) {
        if (i < c.size()) {
            return c.get(i);
        }
        return "";
    }

    static void samplePerf() {
        String t = TmNative.take(TmNative.perf());
        if (t.length() == 0) {
            return;
        }
        perfOk = 1;
        ArrayList<String> lines = AmParse.splitLines(t);
        ArrayList<TmDisk> nd = new ArrayList<TmDisk>();
        ArrayList<TmNet> nn = new ArrayList<TmNet>();
        ArrayList<TmGpu> ng = new ArrayList<TmGpu>();
        int i = 0;
        while (i < lines.size()) {
            ArrayList<String> c = AmParse.splitTab(lines.get(i));
            String k = c.get(0);
            if (k.equals("H") && c.size() >= 17) {
                ncpu = Math.max(1, AmParse.intOr(c.get(2), 1));
                uptimeSec = AmParse.longOr(c.get(3), 0);
                procCount = AmParse.intOr(c.get(4), 0);
                threadCount = AmParse.intOr(c.get(5), 0);
                handleCount = AmParse.intOr(c.get(6), 0);
                memTotal = AmParse.longOr(c.get(7), 0);
                memAvail = AmParse.longOr(c.get(8), 0);
                memInUse = AmParse.longOr(c.get(9), 0);
                commitUsed = AmParse.longOr(c.get(10), 0);
                commitLimit = AmParse.longOr(c.get(11), 0);
                cached = AmParse.longOr(c.get(12), 0);
                pagedPool = AmParse.longOr(c.get(13), 0);
                nonPagedPool = AmParse.longOr(c.get(14), 0);
                compressed = AmParse.longOr(c.get(15), 0);
                memSpeed = AmParse.intOr(c.get(16), -1);
                slotsUsed = AmParse.intOr(field(c, 17), -1);
                slotsTotal = AmParse.intOr(field(c, 18), -1);
                formFactor = field(c, 19);
            } else if (k.equals("C") && c.size() >= 4) {
                int idx = AmParse.intOr(c.get(1), 0);
                if (idx >= 0 && idx < 256) {
                    coreBusy[idx] = AmParse.intOr(c.get(2), 0);
                    coreMhz[idx] = AmParse.intOr(c.get(3), 0);
                    pushAt(hCore, idx * 60, (long) AmParse.intOr(c.get(2), 0));
                }
            } else if (k.equals("T") && c.size() >= 5) {
                cpuTotalX10 = AmParse.intOr(c.get(1), 0);
                cpuKernelX10 = AmParse.intOr(c.get(2), 0);
                cpuMhz = AmParse.intOr(c.get(3), 0);
                cpuBaseMhz = AmParse.intOr(c.get(4), 0);
            } else if (k.equals("I") && c.size() >= 10) {
                cpuName = c.get(1);
                if (AmParse.intOr(c.get(2), 0) > 0) {
                    cpuBaseMhz = AmParse.intOr(c.get(2), 0);
                }
                sockets = AmParse.intOr(c.get(3), -1);
                cores = AmParse.intOr(c.get(4), -1);
                logical = AmParse.intOr(c.get(5), -1);
                l1 = AmParse.intOr(c.get(6), -1);
                l2 = AmParse.intOr(c.get(7), -1);
                l3 = AmParse.intOr(c.get(8), -1);
                virt = AmParse.intOr(c.get(9), -1);
                arch = field(c, 10);
            } else if (k.equals("D") && c.size() >= 10) {
                int id = AmParse.intOr(c.get(1), 0);
                TmDisk d = findDisk(id);
                if (d == null) {
                    d = new TmDisk();
                }
                d.id = id;
                d.label = c.get(2);
                d.model = c.get(3);
                d.media = c.get(4);
                d.size = AmParse.longOr(c.get(5), -1);
                d.actX10 = AmParse.intOr(c.get(6), -1);
                d.readBps = AmParse.longOr(c.get(7), -1);
                d.writeBps = AmParse.longOr(c.get(8), -1);
                d.respX10 = AmParse.intOr(c.get(9), -1);
                d.letters = field(c, 10);
                push(d.hAct, (long) Math.max(0, d.actX10));
                push(d.hRead, Math.max(0L, d.readBps));
                push(d.hWrite, Math.max(0L, d.writeBps));
                nd.add(d);
            } else if (k.equals("N") && c.size() >= 8) {
                String id = c.get(1);
                TmNet n = findNet(id);
                if (n == null) {
                    n = new TmNet();
                }
                n.id = id;
                n.name = c.get(2);
                n.desc = c.get(3);
                n.kind = c.get(4);
                n.link = AmParse.longOr(c.get(5), -1);
                n.sendBps = AmParse.longOr(c.get(6), -1);
                n.recvBps = AmParse.longOr(c.get(7), -1);
                n.ipv4 = field(c, 8);
                n.ipv6 = field(c, 9);
                push(n.hSend, Math.max(0L, n.sendBps));
                push(n.hRecv, Math.max(0L, n.recvBps));
                nn.add(n);
            } else if (k.equals("G") && c.size() >= 8) {
                String id = c.get(1);
                TmGpu g = findGpu(id);
                if (g == null) {
                    g = new TmGpu();
                }
                g.id = id;
                g.name = c.get(2);
                g.utilX10 = AmParse.intOr(c.get(3), -1);
                g.vramUsed = AmParse.longOr(c.get(4), -1);
                g.vramTotal = AmParse.longOr(c.get(5), -1);
                g.sharedUsed = AmParse.longOr(c.get(6), -1);
                g.sharedTotal = AmParse.longOr(c.get(7), -1);
                g.temp = AmParse.intOr(field(c, 8), -1);
                g.driver = field(c, 9);
                push(g.hUtil, (long) Math.max(0, g.utilX10));
                push(g.hVram, Math.max(0L, g.vramUsed));
                push(g.hShared, Math.max(0L, g.sharedUsed));
                ng.add(g);
            }
            i = i + 1;
        }
        disks = nd;
        nets = nn;
        gpus = ng;
        push(hCpu, (long) cpuTotalX10);
        push(hKernel, (long) cpuKernelX10);
        long memPct = 0;
        if (memTotal > 0) {
            memPct = (memInUse * 1000) / memTotal;
        }
        push(hMem, memPct);
    }

    static TmDisk findDisk(int id) {
        int i = 0;
        while (i < disks.size()) {
            if (disks.get(i).id == id) {
                return disks.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static TmNet findNet(String id) {
        int i = 0;
        while (i < nets.size()) {
            if (nets.get(i).id.equals(id)) {
                return nets.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static TmGpu findGpu(String id) {
        int i = 0;
        while (i < gpus.size()) {
            if (gpus.get(i).id.equals(id)) {
                return gpus.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    /* ------------------------------------------------------------ processes */
    static TmProc byPidOf(int pid) {
        Integer ix = byPid.get(String.valueOf(pid));
        if (ix == null) {
            return null;
        }
        return procs.get(ix.intValue());
    }

    static void sampleProcs(int wantExtra) {
        AmSnapshot snap = AmSampler.take();
        if (snap == null || snap.parseOk == 0) {
            return;
        }
        sampleOk = 1;
        tickNo = tickNo + 1;
        long mono = snap.monoNs;
        int nc = Math.max(1, snap.ncpu);
        if (nc > ncpu) {
            ncpu = nc;
        }
        ArrayList<TmProc> next = new ArrayList<TmProc>();
        HashMap<String, Integer> nkey = new HashMap<String, Integer>();
        HashMap<String, Integer> npid = new HashMap<String, Integer>();
        int i = 0;
        while (i < snap.procs.size()) {
            AmProcSample s = snap.procs.get(i);
            String key = s.identityKey();
            TmProc p = null;
            Integer old = byKey.get(key);
            if (old != null) {
                p = procs.get(old.intValue());
            }
            if (p == null) {
                p = new TmProc();
                p.pid = s.pid;
                p.startSec = s.startSec;
                p.startUsec = s.startUsec;
                p.key = key;
            }
            p.ppid = s.ppid;
            p.name = s.name;
            p.path = s.path;
            p.user = s.user;
            p.publisher = s.publisher;
            p.threads = s.nthreads;
            p.winCount = s.winCount;
            p.groupClass = s.groupClass;
            p.statusBits = s.statusBits;
            p.eco = s.eco;
            p.ws = s.rss;
            p.priv = s.footprint;
            if (s.cpuNs >= 0 && p.prevCpuNs >= 0 && mono > p.prevMono) {
                long dc = s.cpuNs - p.prevCpuNs;
                long dm = mono - p.prevMono;
                if (dc < 0) {
                    dc = 0;
                }
                long x10 = (dc * 1000L) / (dm * (long) nc);
                if (x10 > 1000) {
                    x10 = 1000;
                }
                p.cpuX10 = (int) x10;
                p.hasPrev = 1;
            } else if (p.hasPrev == 0) {
                p.cpuX10 = 0;
            }
            p.cpuNs = s.cpuNs;
            p.prevCpuNs = s.cpuNs;
            p.prevMono = mono;
            p.seen = tickNo;
            nkey.put(key, Integer.valueOf(next.size()));
            npid.put(String.valueOf(s.pid), Integer.valueOf(next.size()));
            next.add(p);
            i = i + 1;
        }
        procs = next;
        byKey = nkey;
        byPid = npid;
        lastMono = mono;
        if (wantExtra != 0) {
            sampleProcX(mono);
        }
    }

    static void sampleProcX(long mono) {
        String t = TmNative.take(TmNative.procx());
        if (t.length() == 0) {
            return;
        }
        ArrayList<String> lines = AmParse.splitLines(t);
        int i = 0;
        while (i < lines.size()) {
            ArrayList<String> c = AmParse.splitTab(lines.get(i));
            if (c.size() >= 18 && c.get(0).equals("X")) {
                int pid = AmParse.intOr(c.get(1), -1);
                TmProc p = byPidOf(pid);
                if (p != null) {
                    p.session = AmParse.intOr(c.get(2), -1);
                    p.arch = c.get(3);
                    if (c.get(4).length() > 0) {
                        p.desc = c.get(4);
                    }
                    p.cmd = c.get(5);
                    long rd = AmParse.longOr(c.get(6), -1);
                    long wr = AmParse.longOr(c.get(7), -1);
                    long ot = AmParse.longOr(c.get(8), -1);
                    p.handles = AmParse.intOr(c.get(9), -1);
                    p.prio = AmParse.intOr(c.get(13), 0);
                    p.gdi = AmParse.intOr(c.get(14), -1);
                    p.userObj = AmParse.intOr(c.get(15), -1);
                    p.elevated = AmParse.intOr(c.get(16), -1);
                    p.suspended = AmParse.intOr(c.get(18), 0);
                    p.hasX = 1;
                    if (c.size() >= 22) {
                        long nws = AmParse.longOr(c.get(19), -1);
                        long nct = AmParse.longOr(c.get(20), -1);
                        if (p.ws <= 0 && nws > 0) {
                            p.ws = nws;
                        }
                        if (p.priv <= 0) {
                            long npv = AmParse.longOr(c.get(10), -1);
                            if (npv > 0) {
                                p.priv = npv;
                            }
                        }
                        if (p.cpuNs < 0 && nct >= 0) {
                            /* process could not be opened: derive CPU from the kernel's accounting */
                            long nns = nct * 100L;
                            if (p.prevNt >= 0 && mono > p.prevMono2 && nns >= p.prevNt) {
                                long dm2 = mono - p.prevMono2;
                                long x10 = ((nns - p.prevNt) * 1000L) / (dm2 * (long) TmStore.ncpu);
                                if (x10 > 1000) {
                                    x10 = 1000;
                                }
                                p.cpuX10 = (int) x10;
                            }
                            p.prevNt = nns;
                            p.prevMono2 = mono;
                            p.cpuNs = nns;
                        }
                        if ((p.user.length() == 0 || p.user.equals("N/A")) && p.session == 0) {
                            p.user = "SYSTEM";
                        }
                    }
                    if (rd >= 0 && wr >= 0) {
                        long tot = rd + wr;
                        long dtMs = 0;
                        if (p.prevIo >= 0 && tot >= p.prevIo && lastIoMono > 0 && mono > lastIoMono) {
                            dtMs = (mono - lastIoMono) / 1000000L;
                            if (dtMs > 0) {
                                p.diskBps = ((tot - p.prevIo) * 1000L) / dtMs;
                            }
                        } else {
                            p.diskBps = 0;
                        }
                        p.prevIo = tot;
                        p.ioTotal = tot;
                    }
                }
            }
            i = i + 1;
        }
        lastIoMono = mono;
    }

    static long lastIoMono = 0;

    /* -------------------------------------------------------------- services */
    static void sampleServices() {
        String t = TmNative.take(TmNative.services());
        ArrayList<String> lines = AmParse.splitLines(t);
        ArrayList<TmSvc> out = new ArrayList<TmSvc>();
        int i = 0;
        while (i < lines.size()) {
            ArrayList<String> c = AmParse.splitTab(lines.get(i));
            if (c.size() >= 7 && c.get(0).equals("V")) {
                TmSvc s = new TmSvc();
                s.name = c.get(1);
                s.display = c.get(2);
                s.pid = AmParse.intOr(c.get(3), 0);
                s.state = c.get(4);
                s.startType = c.get(5);
                s.desc = c.get(6);
                s.group = field(c, 7);
                s.image = field(c, 8);
                s.account = field(c, 9);
                out.add(s);
            }
            i = i + 1;
        }
        svcs = out;
    }

    static void sampleSessions() {
        String t = TmNative.take(TmNative.sessions());
        ArrayList<String> lines = AmParse.splitLines(t);
        ArrayList<TmSession> out = new ArrayList<TmSession>();
        int i = 0;
        while (i < lines.size()) {
            ArrayList<String> c = AmParse.splitTab(lines.get(i));
            if (c.size() >= 7 && c.get(0).equals("U")) {
                TmSession s = new TmSession();
                s.id = AmParse.intOr(c.get(1), -1);
                s.user = c.get(2);
                s.domain = c.get(3);
                s.state = c.get(4);
                s.client = c.get(5);
                s.station = c.get(6);
                s.logon = AmParse.longOr(field(c, 7), -1);
                out.add(s);
            }
            i = i + 1;
        }
        sessions = out;
    }

    static void loadStartup() {
        String t = TmNative.take(TmNative.startup());
        ArrayList<String> lines = AmParse.splitLines(t);
        ArrayList<TmStartup> out = new ArrayList<TmStartup>();
        int i = 0;
        while (i < lines.size()) {
            ArrayList<String> c = AmParse.splitTab(lines.get(i));
            if (c.size() >= 8 && c.get(0).equals("T")) {
                TmStartup s = new TmStartup();
                s.id = c.get(1);
                s.name = c.get(2);
                s.publisher = c.get(3);
                s.command = c.get(4);
                s.source = c.get(5);
                s.enabled = AmParse.intOr(c.get(6), 1);
                s.impact = AmParse.intOr(c.get(7), -1);
                s.path = field(c, 8);
                out.add(s);
            }
            i = i + 1;
        }
        startups = out;
        startupLoaded = 1;
    }

    static TmSvc svcByName(String name) {
        int i = 0;
        while (i < svcs.size()) {
            if (svcs.get(i).name.equals(name)) {
                return svcs.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static int svcCountForPid(int pid) {
        int n = 0;
        int i = 0;
        while (i < svcs.size()) {
            if (svcs.get(i).pid == pid && pid > 0) {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    /** Aggregate working set of all processes (for the Memory % header). */
    static long sumWs() {
        long s = 0;
        int i = 0;
        while (i < procs.size()) {
            if (procs.get(i).ws > 0) {
                s = s + procs.get(i).ws;
            }
            i = i + 1;
        }
        return s;
    }

    /** Case-insensitive substring test with a pre-lowered needle. */
    static int has(String hay, String lowNeedle) {
        if (lowNeedle.length() == 0) {
            return 1;
        }
        if (hay == null) {
            return 0;
        }
        if (hay.toLowerCase().contains(lowNeedle)) {
            return 1;
        }
        return 0;
    }
}
