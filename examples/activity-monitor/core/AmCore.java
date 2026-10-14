/**
 * Activity Monitor core: parse native snapshots, CPU deltas, identity, terminate.
 * UI-free so headless dump / verify can share the same logic.
 *
 * @author 张晓达
 */
public class AmCore {
    public static void main(String[] args) {
        runCli(args);
    }

    /** Shared by headless AmCore and ActivityMonitor CLI flags. */
    static void runCli(String[] args) {
        String mode = "--dump";
        if (args != null && args.length > 0) {
            mode = args[0];
        }
        if (mode.equals("--help") || mode.equals("-h")) {
            System.out.println("AmCore --dump [name] | --bench N | --self | --signal-self | --find name | --signal pid startSec startUsec term|kill | --check pid startSec startUsec");
            return;
        }
        if (mode.equals("--self")) {
            System.out.println("self_pid=" + String.valueOf(AmNative.selfPid()));
            return;
        }
        if (mode.equals("--signal-self")) {
            AmProcSample t = new AmProcSample();
            t.pid = AmNative.selfPid();
            t.startSec = 1;
            t.startUsec = 1;
            t.name = "self";
            int code = AmKill.terminate(t, AmKill.SIGTERM);
            System.out.println("signal_code=" + String.valueOf(code) + " " + AmKill.describeError(code));
            return;
        }
        if (mode.equals("--bench")) {
            int n = 5;
            if (args.length > 1) {
                n = AmParse.intOr(args[1], 5);
            }
            AmSampler.bench(n);
            return;
        }
        if (mode.equals("--find")) {
            String q = "";
            if (args.length > 1) {
                q = args[1];
            }
            AmCli.find(q);
            return;
        }
        if (mode.equals("--signal")) {
            if (args.length < 5) {
                System.out.println("ERROR usage --signal pid startSec startUsec term|kill");
                return;
            }
            AmCli.signal(AmParse.intOr(args[1], 0), AmParse.longOr(args[2], -1),
                    AmParse.intOr(args[3], -1), args[4]);
            return;
        }
        if (mode.equals("--check")) {
            if (args.length < 4) {
                System.out.println("ERROR usage --check pid startSec startUsec");
                return;
            }
            int r = AmNative.checkIdentity(AmParse.intOr(args[1], 0), AmParse.longOr(args[2], -1),
                    AmParse.intOr(args[3], -1));
            System.out.println("identity=" + String.valueOf(r));
            return;
        }
        String filter = "";
        if (args.length > 1) {
            filter = args[1];
        }
        AmCli.dump(filter);
    }
}

class AmCli {
    static AmViewState dualSample() {
        AmSnapshot a = AmSampler.take();
        if (a == null) {
            System.out.println("ERROR snapshot1");
            return null;
        }
        try {
            Thread.sleep(400);
        } catch (Exception e) {
        }
        AmSnapshot b = AmSampler.take();
        if (b == null) {
            System.out.println("ERROR snapshot2");
            return null;
        }
        AmViewState st = new AmViewState();
        st.applySnapshot(a);
        st.applySnapshot(b);
        return st;
    }

    static void dump(String filter) {
        AmViewState st = dualSample();
        if (st == null) {
            return;
        }
        System.out.println("mono_ns=" + AmParse.longToString(st.lastMonoNs));
        System.out.println("ncpu=" + String.valueOf(st.ncpu));
        System.out.println("processes=" + String.valueOf(st.rows.size()));
        System.out.println("mem_metric=RSS(pti_resident_size)+phys_footprint(ri_phys_footprint)");
        System.out.println("cpu_metric=100%=1 logical core (delta cpu_ns / delta mono_ns)");
        if (st.lastSysErr != 0) {
            System.out.println("sys_overview=partial_error");
        } else {
            System.out.println("sys_cpu=" + AmFormat.cpuPct(st.sysCpuMilli) + " (host ticks)");
            System.out.println("sys_mem_used=" + AmFormat.bytes(st.sysMemUsedBytes)
                    + " free_pages=" + AmFormat.bytes(st.sysMemFreeBytes));
        }
        ArrayList<AmProcRow> rows = st.filtered(filter);
        int i = 0;
        int limit = 12;
        if (filter != null && filter.length() > 0) {
            limit = 200;
        }
        while (i < rows.size() && i < limit) {
            System.out.println(AmFormat.rowLine(rows.get(i)));
            i = i + 1;
        }
    }

    static void find(String name) {
        AmSnapshot s = AmSampler.take();
        if (s == null) {
            System.out.println("ERROR snapshot");
            return;
        }
        int i = 0;
        int hits = 0;
        while (i < s.procs.size()) {
            AmProcSample p = s.procs.get(i);
            if (AmParse.containsIgnoreCase(p.name, name) != 0) {
                System.out.println("FOUND\t" + String.valueOf(p.pid) + "\t"
                        + AmParse.longToString(p.startSec) + "\t" + String.valueOf(p.startUsec)
                        + "\t" + AmParse.longToString(p.rss) + "\t" + p.name);
                hits = hits + 1;
            }
            i = i + 1;
        }
        System.out.println("hits=" + String.valueOf(hits));
    }

    static void signal(int pid, long startSec, int startUsec, String mode) {
        AmProcSample t = new AmProcSample();
        t.pid = pid;
        t.startSec = startSec;
        t.startUsec = startUsec;
        t.name = "cli-target";
        int sig = AmKill.SIGTERM;
        if (mode != null && mode.equals("kill")) {
            sig = AmKill.SIGKILL;
        }
        int code = AmKill.terminate(t, sig);
        System.out.println("signal_code=" + String.valueOf(code) + " " + AmKill.describeError(code));
        try {
            Thread.sleep(250);
        } catch (Exception e) {
        }
        int id = AmNative.checkIdentity(pid, startSec, startUsec);
        System.out.println("after_identity=" + String.valueOf(id));
    }
}

class AmNative {
    @Native(symbol = "jn_am_snapshot", library = "jn_am")
    static native NativePtr snapshot();

    @Native(symbol = "jn_am_free", library = "jn_am")
    static native void free(NativePtr p);

    @Native(symbol = "jn_am_mono_ns", library = "jn_am")
    static native long monoNs();

    @Native(symbol = "jn_am_self_pid", library = "jn_am")
    static native int selfPid();

    @Native(symbol = "jn_am_check_identity", library = "jn_am")
    static native int checkIdentity(int pid, long startSec, int startUsec);

    @Native(symbol = "jn_am_signal", library = "jn_am")
    static native int signal(int pid, int sig);

    @Native(symbol = "jn_am_alive", library = "jn_am")
    static native int alive(int pid);

    @Native(symbol = "jn_am_last_errno", library = "jn_am")
    static native int lastErrno();
}

class AmFlags {
    static int NAME = 1;
    static int USER = 2;
    static int CPU = 4;
    static int RSS = 8;
    static int FOOTPRINT = 16;
    static int NTHREADS = 32;
    static int BSD = 64;
    static int PATH = 128;
}

class AmSignals {
    static int TERM = 15;
    static int KILL = 9;
}

/** Immutable-ish process sample from one native snapshot line. */
class AmProcSample {
    int pid;
    int ppid;
    int uid;
    long startSec;
    int startUsec;
    long cpuNs;
    long rss;
    long footprint;
    int nthreads;
    int flags;
    String user;
    String name;
    String path;

    AmProcSample() {
        pid = 0;
        ppid = -1;
        uid = -1;
        startSec = -1;
        startUsec = -1;
        cpuNs = -1;
        rss = -1;
        footprint = -1;
        nthreads = -1;
        flags = 0;
        user = "";
        name = "";
        path = "";
    }

    String identityKey() {
        return String.valueOf(pid) + ":" + AmParse.longToString(startSec) + ":" + String.valueOf(startUsec);
    }
}

class AmSnapshot {
    long monoNs;
    int ncpu;
    long memTotal;
    long pageSize;
    long pagesFree;
    long pagesActive;
    long pagesInactive;
    long pagesWired;
    long pagesCompressible;
    long cpuTicksUser;
    long cpuTicksSystem;
    long cpuTicksIdle;
    long cpuTicksNice;
    int sysErr;
    ArrayList<AmProcSample> procs;
    int parseOk;

    AmSnapshot() {
        monoNs = 0;
        ncpu = 1;
        memTotal = 0;
        pageSize = 4096;
        pagesFree = 0;
        pagesActive = 0;
        pagesInactive = 0;
        pagesWired = 0;
        pagesCompressible = 0;
        cpuTicksUser = 0;
        cpuTicksSystem = 0;
        cpuTicksIdle = 0;
        cpuTicksNice = 0;
        sysErr = 0;
        procs = new ArrayList<AmProcSample>();
        parseOk = 0;
    }
}

class AmProcRow {
    AmProcSample sample;
    /** millipercent: 1234 => 12.34% of one logical core; -1 = waiting / unavailable */
    int cpuMilli;
    int gone;

    AmProcRow(AmProcSample s) {
        sample = s;
        cpuMilli = -1;
        gone = 0;
    }
}

/** Prev CPU sample keyed by identity for delta calculation. */
class AmCpuPrev {
    long cpuNs;
    long monoNs;

    AmCpuPrev(long c, long m) {
        cpuNs = c;
        monoNs = m;
    }
}

class AmViewState {
    ArrayList<AmProcRow> rows;
    ArrayList<AmCpuPrev> prevCpu; /* parallel sparse: use map via keys list */
    ArrayList<String> prevKeys;
    long prevSysUser;
    long prevSysSystem;
    long prevSysIdle;
    long prevSysNice;
    int hasPrevSys;
    int sysCpuMilli;
    long sysMemUsedBytes;
    long sysMemFreeBytes;
    long sysMemWiredBytes;
    long sysMemCompressedBytes;
    long memTotal;
    int ncpu;
    long lastMonoNs;
    int lastSysErr;
    String lastError;
    int sampleCount;

    AmViewState() {
        rows = new ArrayList<AmProcRow>();
        prevCpu = new ArrayList<AmCpuPrev>();
        prevKeys = new ArrayList<String>();
        prevSysUser = 0;
        prevSysSystem = 0;
        prevSysIdle = 0;
        prevSysNice = 0;
        hasPrevSys = 0;
        sysCpuMilli = -1;
        sysMemUsedBytes = -1;
        sysMemFreeBytes = -1;
        sysMemWiredBytes = -1;
        sysMemCompressedBytes = -1;
        memTotal = 0;
        ncpu = 1;
        lastMonoNs = 0;
        lastSysErr = 0;
        lastError = "";
        sampleCount = 0;
    }

    int findPrev(String key) {
        int i = 0;
        while (i < prevKeys.size()) {
            if (prevKeys.get(i).equals(key)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    void applySnapshot(AmSnapshot snap) {
        if (snap == null || snap.parseOk == 0) {
            lastError = "采样失败";
            return;
        }
        lastError = "";
        lastMonoNs = snap.monoNs;
        ncpu = snap.ncpu;
        memTotal = snap.memTotal;
        lastSysErr = snap.sysErr;
        sampleCount = sampleCount + 1;

        if (snap.sysErr == 0 && snap.pageSize > 0) {
            sysMemFreeBytes = snap.pagesFree * snap.pageSize;
            sysMemWiredBytes = snap.pagesWired * snap.pageSize;
            sysMemCompressedBytes = snap.pagesCompressible * snap.pageSize;
            /* used ≈ active + inactive + wired (Mach page accounting; not Activity Monitor identical) */
            sysMemUsedBytes = (snap.pagesActive + snap.pagesInactive + snap.pagesWired) * snap.pageSize;
        } else {
            sysMemFreeBytes = -1;
            sysMemUsedBytes = -1;
            sysMemWiredBytes = -1;
            sysMemCompressedBytes = -1;
        }

        if (hasPrevSys != 0 && snap.sysErr == 0) {
            long du = snap.cpuTicksUser - prevSysUser;
            long ds = snap.cpuTicksSystem - prevSysSystem;
            long di = snap.cpuTicksIdle - prevSysIdle;
            long dn = snap.cpuTicksNice - prevSysNice;
            long total = du + ds + di + dn;
            if (total > 0) {
                sysCpuMilli = (int)(((du + ds + dn) * 10000L) / total);
            } else {
                sysCpuMilli = -1;
            }
        } else {
            sysCpuMilli = -1;
        }
        if (snap.sysErr == 0) {
            prevSysUser = snap.cpuTicksUser;
            prevSysSystem = snap.cpuTicksSystem;
            prevSysIdle = snap.cpuTicksIdle;
            prevSysNice = snap.cpuTicksNice;
            hasPrevSys = 1;
        }

        ArrayList<String> nextKeys = new ArrayList<String>();
        ArrayList<AmCpuPrev> nextPrev = new ArrayList<AmCpuPrev>();
        ArrayList<AmProcRow> nextRows = new ArrayList<AmProcRow>();

        int i = 0;
        while (i < snap.procs.size()) {
            AmProcSample s = snap.procs.get(i);
            AmProcRow row = new AmProcRow(s);
            String key = s.identityKey();
            if ((s.flags & AmFlags.CPU) != 0) {
                int pi = findPrev(key);
                if (pi >= 0) {
                    AmCpuPrev p = prevCpu.get(pi);
                    long dCpu = s.cpuNs - p.cpuNs;
                    long dMono = snap.monoNs - p.monoNs;
                    if (dCpu < 0) {
                        dCpu = 0;
                    }
                    if (dMono > 0) {
                        /* 100% = one logical core */
                        row.cpuMilli = (int)((dCpu * 10000L) / dMono);
                    } else {
                        row.cpuMilli = -1;
                    }
                } else {
                    row.cpuMilli = -1; /* wait for second sample */
                }
                nextKeys.add(key);
                nextPrev.add(new AmCpuPrev(s.cpuNs, snap.monoNs));
            } else {
                row.cpuMilli = -2; /* unavailable */
            }
            nextRows.add(row);
            i = i + 1;
        }
        prevKeys = nextKeys;
        prevCpu = nextPrev;
        rows = nextRows;
    }

    AmProcRow findByIdentity(int pid, long startSec, int startUsec) {
        int i = 0;
        while (i < rows.size()) {
            AmProcRow r = rows.get(i);
            if (r.sample.pid == pid && r.sample.startSec == startSec && r.sample.startUsec == startUsec) {
                return r;
            }
            i = i + 1;
        }
        return null;
    }

    ArrayList<AmProcRow> filtered(String query) {
        ArrayList<AmProcRow> out = new ArrayList<AmProcRow>();
        String q = query;
        if (q == null) {
            q = "";
        }
        q = AmParse.trim(q);
        int i = 0;
        while (i < rows.size()) {
            AmProcRow r = rows.get(i);
            int match = 0;
            if (q.length() == 0) {
                match = 1;
            } else if (AmParse.containsIgnoreCase(r.sample.name, q) != 0) {
                match = 1;
            } else if (AmParse.containsIgnoreCase(r.sample.user, q) != 0) {
                match = 1;
            } else if (AmParse.containsIgnoreCase(String.valueOf(r.sample.pid), q) != 0) {
                match = 1;
            }
            if (match != 0) {
                out.add(r);
            }
            i = i + 1;
        }
        return out;
    }
}

class AmSampler {
    static int inFlight = 0;
    static int failLogLeft = 20;

    static AmSnapshot take() {
        if (inFlight != 0) {
            return null;
        }
        inFlight = 1;
        AmSnapshot snap = null;
        try {
            NativePtr p = AmNative.snapshot();
            if (p == NativePtr.NULL) {
                if (failLogLeft > 0) {
                    System.out.println("jn_am_snapshot failed errno=" + String.valueOf(AmNative.lastErrno()));
                    failLogLeft = failLogLeft - 1;
                }
                inFlight = 0;
                return null;
            }
            String text = NativeMemory.fromCString(p);
            AmNative.free(p);
            snap = AmParse.parseSnapshot(text);
        } catch (Exception e) {
            if (failLogLeft > 0) {
                System.out.println("sample exception");
                failLogLeft = failLogLeft - 1;
            }
            snap = null;
        }
        inFlight = 0;
        return snap;
    }

    static void bench(int n) {
        if (n < 1) {
            n = 1;
        }
        if (n > 50) {
            n = 50;
        }
        long sum = 0;
        int i = 0;
        while (i < n) {
            long t0 = AmNative.monoNs();
            AmSnapshot s = take();
            long t1 = AmNative.monoNs();
            long dt = t1 - t0;
            sum = sum + dt;
            int pc = 0;
            if (s != null) {
                pc = s.procs.size();
            }
            System.out.println("sample " + String.valueOf(i) + " procs=" + String.valueOf(pc)
                    + " ns=" + AmParse.longToString(dt));
            i = i + 1;
            try {
                Thread.sleep(200);
            } catch (Exception e) {
            }
        }
        System.out.println("avg_ns=" + AmParse.longToString(sum / (long)n));
    }
}

class AmKill {
    static int SIGTERM = 15;
    static int SIGKILL = 9;

    /** 0 ok signal sent; >0 errno; -1 refused (protected); -2 identity mismatch; -3 bad args */
    static int terminate(AmProcSample target, int sig) {
        if (target == null) {
            return -3;
        }
        if (AmProtect.isProtected(target) != 0) {
            return -1;
        }
        int id = AmNative.checkIdentity(target.pid, target.startSec, target.startUsec);
        if (id != 1) {
            return -2;
        }
        int err = AmNative.signal(target.pid, sig);
        return err;
    }

    static String describeError(int code) {
        if (code == 0) {
            return "已发送信号";
        }
        if (code == -1) {
            return "受保护进程，已禁用结束操作";
        }
        if (code == -2) {
            return "进程身份已变化（可能已退出或 PID 被复用），已取消";
        }
        if (code == -3) {
            return "无效目标";
        }
        if (code == 1) {
            return "权限不足（EPERM）";
        }
        if (code == 3) {
            return "进程不存在（ESRCH）";
        }
        /* Windows Win32 errors commonly returned by jn_am_signal */
        if (code == 5) {
            return "权限不足（ERROR_ACCESS_DENIED）";
        }
        if (code == 87) {
            return "参数无效（ERROR_INVALID_PARAMETER）";
        }
        if (code == 1168) {
            return "进程不存在（ERROR_NOT_FOUND）";
        }
        return "系统错误 code=" + String.valueOf(code);
    }
}

class AmProtect {
    /** Protect self, PID 0/1, and known critical system process names (macOS + Windows). */
    static int isProtected(AmProcSample s) {
        if (s == null) {
            return 1;
        }
        if (s.pid <= 1) {
            return 1;
        }
        if (s.pid == AmNative.selfPid()) {
            return 1;
        }
        if (s.name != null) {
            String n = s.name;
            if (n.equals("kernel_task") || n.equals("System") || n.equals("Idle")
                    || n.equals("smss.exe") || n.equals("csrss.exe") || n.equals("wininit.exe")
                    || n.equals("services.exe") || n.equals("lsass.exe") || n.equals("winlogon.exe")
                    || n.equals("Registry") || n.equals("Memory Compression")) {
                return 1;
            }
        }
        return 0;
    }

    static String reason(AmProcSample s) {
        if (s == null) {
            return "无效";
        }
        if (s.pid <= 1) {
            return "系统关键进程（PID≤1）";
        }
        if (s.pid == AmNative.selfPid()) {
            return "本监视器进程";
        }
        if (s.name != null && (s.name.equals("kernel_task") || s.name.equals("System"))) {
            return "系统内核/System 进程";
        }
        if (s.name != null && isProtected(s) != 0) {
            return "Windows 关键系统进程";
        }
        return "受保护";
    }
}

class AmParse {
    static String longToString(long n) {
        if (n == 0) {
            return "0";
        }
        int neg = 0;
        long v = n;
        if (v < 0) {
            neg = 1;
            v = -v;
        }
        String s = "";
        while (v > 0) {
            int digit = (int)(v % 10L);
            s = String.valueOf(digit) + s;
            v = v / 10L;
        }
        if (neg != 0) {
            s = "-" + s;
        }
        return s;
    }

    static AmSnapshot parseSnapshot(String text) {
        AmSnapshot snap = new AmSnapshot();
        if (text == null || text.length() == 0) {
            return snap;
        }
        ArrayList<String> lines = splitLines(text);
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.length() >= 2 && line.charAt(0) == 83 && line.charAt(1) == 9) {
                ArrayList<String> c = splitTab(line);
                if (c.size() >= 15) {
                    snap.monoNs = longOr(c.get(1), 0);
                    snap.ncpu = intOr(c.get(2), 1);
                    snap.memTotal = longOr(c.get(3), 0);
                    snap.pageSize = longOr(c.get(4), 4096);
                    snap.pagesFree = longOr(c.get(5), 0);
                    snap.pagesActive = longOr(c.get(6), 0);
                    snap.pagesInactive = longOr(c.get(7), 0);
                    snap.pagesWired = longOr(c.get(8), 0);
                    snap.pagesCompressible = longOr(c.get(9), 0);
                    snap.cpuTicksUser = longOr(c.get(10), 0);
                    snap.cpuTicksSystem = longOr(c.get(11), 0);
                    snap.cpuTicksIdle = longOr(c.get(12), 0);
                    snap.cpuTicksNice = longOr(c.get(13), 0);
                    snap.sysErr = intOr(c.get(14), 1);
                    snap.parseOk = 1;
                }
            } else if (line.length() >= 2 && line.charAt(0) == 80 && line.charAt(1) == 9) {
                ArrayList<String> c = splitTab(line);
                if (c.size() >= 13) {
                    AmProcSample s = new AmProcSample();
                    s.pid = intOr(c.get(1), 0);
                    s.ppid = intOr(c.get(2), -1);
                    s.uid = intOr(c.get(3), -1);
                    s.startSec = longOr(c.get(4), -1);
                    s.startUsec = intOr(c.get(5), -1);
                    s.cpuNs = longOr(c.get(6), -1);
                    s.rss = longOr(c.get(7), -1);
                    s.footprint = longOr(c.get(8), -1);
                    s.nthreads = intOr(c.get(9), -1);
                    s.flags = intOr(c.get(10), 0);
                    s.user = c.get(11);
                    s.name = c.get(12);
                    s.path = "";
                    if (c.size() >= 14) {
                        s.path = c.get(13);
                    }
                    snap.procs.add(s);
                    snap.parseOk = 1;
                }
            }
            i = i + 1;
        }
        return snap;
    }

    static ArrayList<String> splitLines(String s) {
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        int start = 0;
        while (i <= s.length()) {
            if (i == s.length() || s.charAt(i) == 10) {
                if (i > start) {
                    out.add(s.substring(start, i));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static ArrayList<String> splitTab(String s) {
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        int start = 0;
        while (i <= s.length()) {
            if (i == s.length() || s.charAt(i) == 9) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static int intOr(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        return parseIntInner(s);
    }

    static long longOr(String s, long def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        return parseLongInner(s);
    }

    static int parseIntInner(String s) {
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        int v = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                break;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v * sign;
    }

    static long parseLongInner(String s) {
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        long v = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                break;
            }
            v = v * 10L + (long)(c - 48);
            i = i + 1;
        }
        return v * (long)sign;
    }

    static String trim(String s) {
        if (s == null) {
            return "";
        }
        int a = 0;
        int b = s.length();
        while (a < b) {
            int c = s.charAt(a);
            if (c != 32 && c != 9) {
                break;
            }
            a = a + 1;
        }
        while (b > a) {
            int c = s.charAt(b - 1);
            if (c != 32 && c != 9) {
                break;
            }
            b = b - 1;
        }
        return s.substring(a, b);
    }

    static int containsIgnoreCase(String hay, String needle) {
        if (hay == null || needle == null) {
            return 0;
        }
        if (needle.length() == 0) {
            return 1;
        }
        String h = lower(hay);
        String n = lower(needle);
        int i = 0;
        while (i + n.length() <= h.length()) {
            int j = 0;
            int ok = 1;
            while (j < n.length()) {
                if (h.charAt(i + j) != n.charAt(j)) {
                    ok = 0;
                    break;
                }
                j = j + 1;
            }
            if (ok != 0) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    static String lower(String s) {
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c >= 65 && c <= 90) {
                c = c + 32;
            }
            out = out + String.valueOf((char)c);
            i = i + 1;
        }
        return out;
    }
}

class AmFormat {
    static String na() {
        return "不可用";
    }

    static String waiting() {
        return "采样中";
    }

    static String cpuPct(int milli) {
        if (milli == -1) {
            return waiting();
        }
        if (milli == -2 || milli < -2) {
            return na();
        }
        int whole = milli / 100;
        int frac = milli % 100;
        if (frac < 0) {
            frac = -frac;
        }
        String fs = String.valueOf(frac);
        if (frac < 10) {
            fs = "0" + fs;
        }
        return String.valueOf(whole) + "." + fs + "%";
    }

    /** Zero-padded for string sort (width ~8). */
    static String cpuSortKey(int milli) {
        if (milli < 0) {
            return "________";
        }
        String s = String.valueOf(milli);
        while (s.length() < 8) {
            s = "0" + s;
        }
        return s;
    }

    static String bytes(long n) {
        if (n < 0) {
            return na();
        }
        if (n < 1024L) {
            return AmParse.longToString(n) + " B";
        }
        if (n < 1024L * 1024L) {
            return AmParse.longToString(n / 1024L) + " KB";
        }
        if (n < 1024L * 1024L * 1024L) {
            long kb = n / 1024L;
            return AmParse.longToString(kb / 1024L) + "." + AmParse.longToString((kb % 1024L) / 102L) + " MB";
        }
        long mb = n / (1024L * 1024L);
        return AmParse.longToString(mb / 1024L) + "." + AmParse.longToString((mb % 1024L) / 102L) + " GB";
    }

    static String bytesSortKey(long n) {
        if (n < 0) {
            return "000000000000";
        }
        String s = AmParse.longToString(n);
        while (s.length() < 12) {
            s = "0" + s;
        }
        return s;
    }

    static String threads(AmProcSample s) {
        if (s == null || (s.flags & AmFlags.NTHREADS) == 0 || s.nthreads < 0) {
            return na();
        }
        return String.valueOf(s.nthreads);
    }

    static String rowLine(AmProcRow r) {
        return String.valueOf(r.sample.pid) + "\t" + r.sample.name + "\t" + r.sample.user
                + "\tCPU=" + cpuPct(r.cpuMilli)
                + "\tRSS=" + bytes(r.sample.rss)
                + "\tfootprint=" + bytes(r.sample.footprint)
                + "\tthr=" + threads(r.sample);
    }
}
