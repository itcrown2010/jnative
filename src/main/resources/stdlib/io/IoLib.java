// JNative FsPath / Files / Process (0.21) — library + thin C ABI, no compiler JSON/FS intrinsics.

class FsNative {
    @Native(symbol = "jnative_fs_exists")
    static native int exists(String path);

    @Native(symbol = "jnative_fs_is_directory")
    static native int isDirectory(String path);

    @Native(symbol = "jnative_fs_is_file")
    static native int isFile(String path);

    @Native(symbol = "jnative_fs_create_directories")
    static native int createDirectories(String path);

    @Native(symbol = "jnative_fs_delete")
    static native int delete(String path);

    @Native(symbol = "jnative_fs_rename")
    static native int rename(String from, String to);

    @Native(symbol = "jnative_fs_list")
    static native NativePtr list(String path);

    @Native(symbol = "jnative_fs_free")
    static native void free(NativePtr p);

    @Native(symbol = "jnative_fs_config_dir")
    static native NativePtr configDir(String appName);
}

class ProcNative {
    @Native(symbol = "jnative_proc_spawn_s")
    static native NativePtr spawn(String file, String argvJoined, int argc, String cwd);

    @Native(symbol = "jnative_proc_wait")
    static native int waitPid(int pid);

    @Native(symbol = "jnative_proc_kill")
    static native int kill(int pid);

    @Native(symbol = "jnative_proc_read_string")
    static native NativePtr readString(int fd, int maxBytes);

    @Native(symbol = "jnative_proc_close_fd")
    static native void closeFd(int fd);

    @Native(symbol = "jnative_proc_free")
    static native void free(NativePtr p);
}

class FsPath {
    String text;

    FsPath(String t) {
        if (t == null) {
            text = "";
        } else {
            text = t;
        }
    }

    static FsPath of(String t) {
        return new FsPath(t);
    }

    String getName() {
        int i = text.length() - 1;
        while (i >= 0) {
            int c = text.charAt(i);
            if (c == 47 || c == 92) {
                return text.substring(i + 1, text.length());
            }
            i = i - 1;
        }
        return text;
    }

    FsPath getParent() {
        int i = text.length() - 1;
        while (i >= 0) {
            int c = text.charAt(i);
            if (c == 47 || c == 92) {
                if (i == 0) {
                    return new FsPath(text.substring(0, 1));
                }
                return new FsPath(text.substring(0, i));
            }
            i = i - 1;
        }
        return new FsPath(".");
    }

    FsPath resolve(String other) {
        if (other == null || other.length() == 0) {
            return this;
        }
        if (other.charAt(0) == 47 || (other.length() > 1 && other.charAt(1) == 58)) {
            return new FsPath(other);
        }
        if (text.length() == 0 || text.equals(".")) {
            return new FsPath(other);
        }
        int last = text.charAt(text.length() - 1);
        if (last == 47 || last == 92) {
            return new FsPath(text + other);
        }
        return new FsPath(text + "/" + other);
    }

    FsPath normalize() {
        return new FsPath(text);
    }

    boolean isAbsolute() {
        if (text.length() == 0) {
            return false;
        }
        if (text.charAt(0) == 47) {
            return true;
        }
        if (text.length() > 2 && text.charAt(1) == 58) {
            return true;
        }
        return false;
    }

    String toString() {
        return text;
    }

    boolean equalsFsPath(FsPath other) {
        if (other == null) {
            return false;
        }
        return text.equals(other.text);
    }
}

class Files {
    static boolean exists(FsPath path) {
        return FsNative.exists(path.toString()) != 0;
    }

    static boolean isDirectory(FsPath path) {
        return FsNative.isDirectory(path.toString()) != 0;
    }

    static boolean isFile(FsPath path) {
        return FsNative.isFile(path.toString()) != 0;
    }

    static String readString(FsPath path) {
        return File.readString(path.toString());
    }

    static void writeString(FsPath path, String text) {
        File.writeString(path.toString(), text);
    }

    static byte[] readBytes(FsPath path) {
        return File.readBytes(path.toString());
    }

    static void writeBytes(FsPath path, byte[] data) {
        File.writeBytes(path.toString(), data);
    }

    static ArrayList<String> list(FsPath path) {
        ArrayList<String> out = new ArrayList<String>();
        NativePtr p = FsNative.list(path.toString());
        if (p == NativePtr.NULL) {
            return out;
        }
        String blob = NativeMemory.fromCString(p);
        FsNative.free(p);
        if (blob == null) {
            return out;
        }
        int i = 0;
        int start = 0;
        while (i <= blob.length()) {
            if (i == blob.length() || blob.charAt(i) == 10) {
                if (i > start) {
                    out.add(blob.substring(start, i));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static boolean createDirectories(FsPath path) {
        return FsNative.createDirectories(path.toString()) != 0;
    }

    static boolean delete(FsPath path) {
        return FsNative.delete(path.toString()) != 0;
    }

    static boolean move(FsPath from, FsPath to) {
        return FsNative.rename(from.toString(), to.toString()) != 0;
    }

    static FsPath configDir(String appName) {
        NativePtr p = FsNative.configDir(appName);
        if (p == NativePtr.NULL) {
            return FsPath.of(".");
        }
        String s = NativeMemory.fromCString(p);
        FsNative.free(p);
        return FsPath.of(s);
    }
}

class ProcessBuilder {
    String file;
    ArrayList<String> args;
    String directory;
    HashMap<String, String> environment;

    ProcessBuilder(String executable) {
        file = executable;
        args = new ArrayList<String>();
        args.add(executable);
        directory = "";
        environment = new HashMap<String, String>();
    }

    ProcessBuilder argument(String a) {
        args.add(a);
        return this;
    }

    ProcessBuilder directory(String dir) {
        directory = dir;
        return this;
    }

    Process start() {
        String joined = "";
        int i = 0;
        while (i < args.size()) {
            if (i > 0) {
                joined = joined + "\n";
            }
            joined = joined + args.get(i);
            i = i + 1;
        }
        NativePtr desc = ProcNative.spawn(file, joined, args.size(), directory);
        if (desc == NativePtr.NULL) {
            return null;
        }
        String s = NativeMemory.fromCString(desc);
        ProcNative.free(desc);
        return Process.fromDescriptor(s);
    }
}

class Process {
    int pid;
    int stdoutFd;
    int stderrFd;
    int alive;

    Process(int p, int outFd, int errFd) {
        pid = p;
        stdoutFd = outFd;
        stderrFd = errFd;
        alive = 1;
    }

    static Process fromDescriptor(String s) {
        if (s == null) {
            return null;
        }
        ArrayList<String> parts = new ArrayList<String>();
        int i = 0;
        int start = 0;
        while (i <= s.length()) {
            if (i == s.length() || s.charAt(i) == 10) {
                if (i > start) {
                    parts.add(s.substring(start, i));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        if (parts.size() < 3) {
            return null;
        }
        int pid = IoUtil.parseInt(parts.get(0));
        int outFd = IoUtil.parseInt(parts.get(1));
        int errFd = IoUtil.parseInt(parts.get(2));
        return new Process(pid, outFd, errFd);
    }

    String readStdout(int maxBytes) {
        NativePtr p = ProcNative.readString(stdoutFd, maxBytes);
        if (p == NativePtr.NULL) {
            return "";
        }
        String s = NativeMemory.fromCString(p);
        ProcNative.free(p);
        if (s == null) {
            return "";
        }
        return s;
    }

    String readStderr(int maxBytes) {
        NativePtr p = ProcNative.readString(stderrFd, maxBytes);
        if (p == NativePtr.NULL) {
            return "";
        }
        String s = NativeMemory.fromCString(p);
        ProcNative.free(p);
        if (s == null) {
            return "";
        }
        return s;
    }

    int waitFor() {
        int code = ProcNative.waitPid(pid);
        alive = 0;
        ProcNative.closeFd(stdoutFd);
        ProcNative.closeFd(stderrFd);
        return code;
    }

    void destroy() {
        ProcNative.kill(pid);
    }

    boolean isAlive() {
        return alive != 0;
    }
}

class IoUtil {
    static int parseInt(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        int v = 0;
        int cont = 1;
        while (i < s.length() && cont != 0) {
            int c = s.charAt(i);
            if (c >= 48 && c <= 57) {
                v = v * 10 + (c - 48);
                i = i + 1;
            } else {
                cont = 0;
            }
        }
        return v * sign;
    }
}

class Desktop {
    static void openDirectory(String path) {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/open");
        pb.argument(path);
        Process p = pb.start();
        if (p != null) {
            p.waitFor();
        }
    }

    static void revealFile(String path) {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/open");
        pb.argument("-R");
        pb.argument(path);
        Process p = pb.start();
        if (p != null) {
            p.waitFor();
        }
    }

    static void openUrl(String url) {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/open");
        pb.argument(url);
        Process p = pb.start();
        if (p != null) {
            p.waitFor();
        }
    }
}

class EnvNative {
    @Native(symbol = "getenv")
    static native NativePtr getenv(String name);

    @Native(symbol = "jnative_cpu_count")
    static native int cpuCount();
}

/** Process environment + host CPU count (0.22). */
class Env {
    static String get(String name) {
        NativePtr p = EnvNative.getenv(name);
        if (p == NativePtr.NULL) {
            return null;
        }
        return NativeMemory.fromCString(p);
    }

    static String getOr(String name, String def) {
        String v = get(name);
        if (v == null) {
            return def;
        }
        return v;
    }

    static int getInt(String name, int def) {
        String v = get(name);
        if (v == null || v.length() == 0) {
            return def;
        }
        int n = 0;
        int i = 0;
        int sign = 1;
        if (v.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        int ok = 0;
        while (i < v.length()) {
            int c = v.charAt(i);
            if (c < 48 || c > 57) {
                return def;
            }
            n = n * 10 + (c - 48);
            ok = 1;
            i = i + 1;
        }
        if (ok == 0) {
            return def;
        }
        return n * sign;
    }
}

class CpuInfo {
    static int count() {
        int n = EnvNative.cpuCount();
        if (n < 1) {
            return 1;
        }
        return n;
    }
}

class Logger {
    static int DEBUG = 0;
    static int INFO = 1;
    static int WARN = 2;
    static int ERROR = 3;
    static int level = 1;
    static String logPath = "";

    static void setLogPath(String path) {
        logPath = path;
    }

    static void log(int lv, String msg) {
        if (lv < level) {
            return;
        }
        String line = msg + "\n";
        if (logPath != null && logPath.length() > 0) {
            String prev = "";
            if (File.exists(logPath)) {
                prev = File.readString(logPath);
            }
            File.writeString(logPath, prev + line);
        }
    }

    static void info(String msg) {
        log(INFO, msg);
    }

    static void warn(String msg) {
        log(WARN, msg);
    }

    static void error(String msg) {
        log(ERROR, msg);
    }
}

class Resources {
    static byte[] readBytes(String resourcePath) {
        return File.readBytes(resourcePath);
    }

    static String readString(String resourcePath) {
        return File.readString(resourcePath);
    }
}

class CancellationToken {
    int cancelled;

    CancellationToken() {
        cancelled = 0;
    }

    void cancel() {
        cancelled = 1;
    }

    boolean isCancelled() {
        return cancelled != 0;
    }

    void checkCancelled() {
        if (cancelled != 0) {
            throw new RuntimeException("cancelled");
        }
    }
}

/** Polling file-watcher foundation (0.21). Platform FSEvents/inotify reserved. */
class FileWatcher {
    String root;
    int generation;

    FileWatcher(String path) {
        root = path;
        generation = 0;
    }

    void poll() {
        generation = generation + 1;
    }

    int generation() {
        return generation;
    }
}
