/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative FsPath / Files / Process (0.21) — library + thin C ABI, no compiler JSON/FS intrinsics.

class FsNative {
    @Native(symbol = "jnative_fs_exists")
    static native int exists(String path);

    @Native(symbol = "jnative_fs_is_directory")
    static native int isDirectory(String path);

    @Native(symbol = "jnative_fs_is_file")
    static native int isFile(String path);

    @Native(symbol = "jnative_fs_is_hidden")
    static native int isHidden(String path);

    @Native(symbol = "jnative_fs_create_directories")
    static native int createDirectories(String path);

    @Native(symbol = "jnative_fs_delete")
    static native int delete(String path);

    @Native(symbol = "jnative_fs_rename")
    static native int rename(String from, String to);

    @Native(symbol = "jnative_fs_copy_file")
    static native int copyFile(String from, String to);

    @Native(symbol = "jnative_fs_file_size")
    static native long fileSize(String path);

    @Native(symbol = "jnative_fs_mtime_ms")
    static native long mtimeMs(String path);

    @Native(symbol = "jnative_fs_list")
    static native NativePtr list(String path);

    @Native(symbol = "jnative_fs_list_info")
    static native NativePtr listInfo(String path);

    @Native(symbol = "jnative_fs_roots")
    static native NativePtr roots();

    @Native(symbol = "jnative_fs_known_folder")
    static native NativePtr knownFolder(String id);

    @Native(symbol = "jnative_fs_open")
    static native int open(String path);

    @Native(symbol = "jnative_fs_reveal")
    static native int reveal(String path);

    @Native(symbol = "jnative_fs_trash")
    static native int trash(String path);

    @Native(symbol = "jnative_fs_free")
    static native void free(NativePtr p);

    @Native(symbol = "jnative_fs_config_dir")
    static native NativePtr configDir(String appName);

    @Native(symbol = "jnative_fs_watch_open")
    static native long watchOpen(String path);

    @Native(symbol = "jnative_fs_watch_poll")
    static native int watchPoll(long handle);

    @Native(symbol = "jnative_fs_watch_close")
    static native void watchClose(long handle);

    @Native(symbol = "jnative_fs_watch_poll_ex")
    static native int watchPollEx(long handle);

    @Native(symbol = "jnative_fs_is_symlink")
    static native int isSymlink(String path);

    @Native(symbol = "jnative_fs_readlink")
    static native NativePtr readlink(String path);
}

/** Standard open options for Files streams / write.
 * @author 张晓达
 */
class StandardOpenOption {
    static int READ = 1;
    static int WRITE = 2;
    static int APPEND = 4;
    static int CREATE = 8;
    static int CREATE_NEW = 16;
    static int TRUNCATE_EXISTING = 32;
}

class ProcNative {
    @Native(symbol = "jnative_proc_spawn_s")
    static native NativePtr spawn(String file, String argvJoined, int argc, String cwd);

    @Native(symbol = "jnative_proc_spawn_pipe_s")
    static native NativePtr spawnPipe(String file, String argvJoined, int argc, String cwd);

    @Native(symbol = "jnative_proc_write_s")
    static native int writeFd(int fd, String data);

    @Native(symbol = "jnative_proc_wait")
    static native int waitPid(int pid);

    @Native(symbol = "jnative_proc_kill")
    static native int kill(int pid);

    @Native(symbol = "jnative_proc_read_string")
    static native NativePtr readString(int fd, int maxBytes);

    @Native(symbol = "jnative_proc_read_nb")
    static native NativePtr readNb(int fd, int maxBytes, int timeoutMs);

    @Native(symbol = "jnative_proc_try_wait")
    static native int tryWait(int pid);

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

    /** Spec alias for Explorer / Path.of API. */
    String name() {
        return getName();
    }

    FsPath parent() {
        return getParent();
    }

    FsPath absolute() {
        if (isAbsolute()) {
            return this;
        }
        String cwd = Env.getOr("PWD", ".");
        return FsPath.of(cwd).resolve(text).normalize();
    }

    boolean exists() {
        return Files.exists(this);
    }

    boolean isDirectory() {
        return Files.isDirectory(this);
    }

    boolean isFile() {
        return Files.isFile(this);
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
        if (text == null || text.length() == 0) {
            return new FsPath(".");
        }
        // Collapse // and trailing separator (keep root / or C:\).
        String s = text;
        int changed = 1;
        while (changed != 0) {
            changed = 0;
            int i = 0;
            while (i + 1 < s.length()) {
                int a = s.charAt(i);
                int b = s.charAt(i + 1);
                if ((a == 47 && b == 47) || (a == 92 && b == 92)) {
                    s = s.substring(0, i) + s.substring(i + 1, s.length());
                    changed = 1;
                } else {
                    i = i + 1;
                }
            }
        }
        if (s.length() > 1) {
            int last = s.charAt(s.length() - 1);
            if (last == 47 || last == 92) {
                if (!(s.length() == 3 && s.charAt(1) == 58)) {
                    s = s.substring(0, s.length() - 1);
                }
            }
        }
        return new FsPath(s);
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
    static int atomicSeq;

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

    static boolean createDirectory(FsPath path) {
        return createDirectories(path);
    }

    static long size(FsPath path) {
        return FsNative.fileSize(path.toString());
    }

    static long lastModified(FsPath path) {
        return FsNative.mtimeMs(path.toString());
    }

    static boolean isHidden(FsPath path) {
        return FsNative.isHidden(path.toString()) != 0;
    }

    /** Structured directory listing (Explorer). */
    static ArrayList<FileInfo> listInfo(FsPath path) {
        ArrayList<FileInfo> out = new ArrayList<FileInfo>();
        NativePtr p = FsNative.listInfo(path.toString());
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
                    FileInfo fi = FileInfo.parseLine(path, blob.substring(start, i));
                    if (fi != null) {
                        out.add(fi);
                    }
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static boolean copy(FsPath from, FsPath to) {
        if (isDirectory(from)) {
            return copyDirectoryRecursive(from, to, new HashMap<String, String>());
        }
        return FsNative.copyFile(from.toString(), to.toString()) != 0;
    }

    static boolean isSymlink(FsPath path) {
        return FsNative.isSymlink(path.toString()) != 0;
    }

    static String readSymbolicLink(FsPath path) {
        NativePtr p = FsNative.readlink(path.toString());
        if (p == NativePtr.NULL) {
            throw new IOException("readlink failed: " + path.toString());
        }
        String s = NativeMemory.fromCString(p);
        FsNative.free(p);
        return s;
    }

    /** Temp write + fsync + rename. Partial temp removed on failure. */
    static void writeAtomic(FsPath path, byte[] data) {
        if (path == null) {
            throw new NullPointerException("path");
        }
        if (data == null) {
            throw new NullPointerException("data");
        }
        Files.atomicSeq = Files.atomicSeq + 1;
        FsPath parent = path.getParent();
        String tmpName = path.getName() + ".jn-tmp-" + String.valueOf(Files.atomicSeq);
        FsPath tmp = parent.resolve(tmpName);
        FileOutputStream out = new FileOutputStream(tmp.toString(), 0);
        out.write(data);
        out.flush();
        out.close();
        if (!move(tmp, path)) {
            delete(tmp);
            throw new IOException("atomic rename failed: " + path.toString());
        }
    }

    static void writeAtomicString(FsPath path, String text) {
        writeAtomic(path, Bytes.fromString(text));
    }

    static FileInputStream newInputStream(FsPath path) {
        return new FileInputStream(path);
    }

    static FileOutputStream newOutputStream(FsPath path) {
        return new FileOutputStream(path);
    }

    /**
     * Depth-first walk. Symlink directories are not followed (cycle-safe).
     * visited keys are normalized absolute-ish path strings.
     */
    static ArrayList<FsPath> walk(FsPath root) {
        ArrayList<FsPath> out = new ArrayList<FsPath>();
        HashMap<String, String> seen = new HashMap<String, String>();
        walkInto(root, out, seen, 0);
        return out;
    }

    static void walkInto(FsPath root, ArrayList<FsPath> out, HashMap<String, String> seen, int depth) {
        if (root == null || depth > 256) {
            return;
        }
        String key = root.normalize().toString();
        if (seen.containsKey(key)) {
            return;
        }
        seen.put(key, "1");
        out.add(root);
        if (!isDirectory(root)) {
            return;
        }
        if (isSymlink(root)) {
            return;
        }
        ArrayList<FileInfo> kids = listInfo(root);
        int i = 0;
        while (i < kids.size()) {
            FileInfo fi = kids.get(i);
            walkInto(root.resolve(fi.name), out, seen, depth + 1);
            i = i + 1;
        }
    }

    static boolean copyDirectoryRecursive(FsPath from, FsPath to, HashMap<String, String> seen) {
        String key = from.normalize().toString();
        if (seen.containsKey(key)) {
            return true;
        }
        seen.put(key, "1");
        if (!createDirectories(to)) {
            if (!exists(to)) {
                return false;
            }
        }
        ArrayList<FileInfo> kids = listInfo(from);
        int i = 0;
        while (i < kids.size()) {
            FileInfo fi = kids.get(i);
            FsPath childFrom = from.resolve(fi.name);
            FsPath childTo = to.resolve(fi.name);
            if (fi.isDirectory != 0) {
                if (!copyDirectoryRecursive(childFrom, childTo, seen)) {
                    return false;
                }
            } else {
                if (FsNative.copyFile(childFrom.toString(), childTo.toString()) == 0) {
                    return false;
                }
            }
            i = i + 1;
        }
        return true;
    }
}

/** File metadata for Explorer / Files.listInfo. */
class FileInfo {
    String name;
    FsPath path;
    String type;
    long size;
    long createdTime;
    long modifiedTime;
    int attributes;
    int isDirectory;
    int isHidden;
    int isReadOnly;

    FileInfo() {
        name = "";
        path = FsPath.of(".");
        type = "File";
        size = 0;
        createdTime = 0;
        modifiedTime = 0;
        attributes = 0;
        isDirectory = 0;
        isHidden = 0;
        isReadOnly = 0;
    }

    static FileInfo parseLine(FsPath parent, String line) {
        if (line == null || line.length() == 0) {
            return null;
        }
        ArrayList<String> cols = IoUtil.splitTab(line);
        if (cols.size() < 5) {
            return null;
        }
        FileInfo fi = new FileInfo();
        fi.name = cols.get(0);
        fi.path = parent.resolve(fi.name);
        fi.isDirectory = IoUtil.parseInt(cols.get(1));
        fi.size = IoUtil.parseLong(cols.get(2));
        fi.modifiedTime = IoUtil.parseLong(cols.get(3));
        fi.createdTime = fi.modifiedTime;
        fi.isHidden = IoUtil.parseInt(cols.get(4));
        if (fi.isDirectory != 0) {
            fi.type = "Folder";
            fi.size = 0;
        } else {
            fi.type = FileTypes.ofName(fi.name);
        }
        return fi;
    }
}

class FileTypes {
    static String ofName(String name) {
        int dot = -1;
        int i = 0;
        while (i < name.length()) {
            if (name.charAt(i) == 46) {
                dot = i;
            }
            i = i + 1;
        }
        if (dot < 0 || dot == name.length() - 1) {
            return "File";
        }
        String ext = name.substring(dot + 1, name.length());
        if (ext.equals("txt")) { return "Text"; }
        if (ext.equals("pdf")) { return "PDF"; }
        if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif")) {
            return "Image";
        }
        if (ext.equals("java")) { return "Java"; }
        if (ext.equals("exe") || ext.equals("dll")) { return "Application"; }
        return ext + " File";
    }
}

class FileSize {
    static String format(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return ("" + bytes) + " B";
        }
        if (bytes < 1024 * 1024) {
            long kb10 = (bytes * 10) / 1024;
            return ("" + (kb10 / 10)) + "." + ("" + (kb10 % 10)) + " KB";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            long mb10 = (bytes * 10) / (1024 * 1024);
            return ("" + (mb10 / 10)) + "." + ("" + (mb10 % 10)) + " MB";
        }
        long gb10 = (bytes * 10) / (1024L * 1024L * 1024L);
        return ("" + (gb10 / 10)) + "." + ("" + (gb10 % 10)) + " GB";
    }
}

class DriveInfo {
    String name;
    FsPath root;
    String type;
    long totalSpace;
    long freeSpace;

    DriveInfo(String n, FsPath r) {
        name = n;
        root = r;
        type = "Local Disk";
        totalSpace = 0;
        freeSpace = 0;
    }
}

class FileSystem {
    static ArrayList<FsPath> roots() {
        ArrayList<FsPath> out = new ArrayList<FsPath>();
        NativePtr p = FsNative.roots();
        if (p == NativePtr.NULL) {
            out.add(FsPath.of("/"));
            return out;
        }
        String blob = NativeMemory.fromCString(p);
        FsNative.free(p);
        if (blob == null) {
            out.add(FsPath.of("/"));
            return out;
        }
        int i = 0;
        int start = 0;
        while (i <= blob.length()) {
            if (i == blob.length() || blob.charAt(i) == 10) {
                if (i > start) {
                    out.add(FsPath.of(blob.substring(start, i)));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static ArrayList<DriveInfo> drives() {
        ArrayList<DriveInfo> out = new ArrayList<DriveInfo>();
        ArrayList<FsPath> rs = roots();
        int i = 0;
        while (i < rs.size()) {
            FsPath r = rs.get(i);
            String label = r.toString();
            out.add(new DriveInfo("Local Disk (" + label + ")", r));
            i = i + 1;
        }
        return out;
    }
}

class SystemPaths {
    static FsPath known(String id) {
        NativePtr p = FsNative.knownFolder(id);
        if (p == NativePtr.NULL) {
            return FsPath.of(".");
        }
        String s = NativeMemory.fromCString(p);
        FsNative.free(p);
        return FsPath.of(s);
    }

    static FsPath home() {
        return known("home");
    }

    static FsPath desktop() {
        return known("desktop");
    }

    static FsPath documents() {
        return known("documents");
    }

    static FsPath downloads() {
        return known("downloads");
    }

    static FsPath pictures() {
        return known("pictures");
    }
}

/** Watch event codes from pollEx. */
class WatchKind {
    static int NONE = 0;
    static int CHANGED = 1;
    /** OS queue overflow — client must rescan. */
    static int OVERFLOW = 2;
}

/** WatchService — platform backend (kqueue/inotify/ReadDirectoryChangesW) + poll. */
class WatchService implements AutoCloseable {
    long handle;
    FsPath path;
    int closed;

    WatchService(FsPath dir) {
        path = dir;
        handle = FsNative.watchOpen(dir.toString());
        closed = 0;
        ResourceTracker.trackOpen("file");
    }

    static WatchService open(FsPath dir) {
        return new WatchService(dir);
    }

    boolean poll() {
        return pollEx() == WatchKind.CHANGED;
    }

    /** 0=none, 1=changed, 2=OVERFLOW/RESCAN_REQUIRED. */
    int pollEx() {
        if (closed != 0 || handle == 0) {
            return WatchKind.NONE;
        }
        return FsNative.watchPollEx(handle);
    }

    void close() {
        if (closed == 0 && handle != 0) {
            FsNative.watchClose(handle);
            handle = 0;
            closed = 1;
            ResourceTracker.trackClose("file");
        }
    }
}

class IoUtil {
    static ArrayList<String> splitTab(String line) {
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        int start = 0;
        while (i <= line.length()) {
            if (i == line.length() || line.charAt(i) == 9) {
                out.add(line.substring(start, i));
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        long v = 0;
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return v * sign;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v * sign;
    }

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

class ProcessBuilder {
    String file;
    ArrayList<String> args;
    String directory;
    HashMap<String, String> environment;
    int stdinPipe;

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

    /** Gives the child a stdin pipe (see Process.writeStdin) instead of inheriting this process's stdin. */
    ProcessBuilder pipeStdin() {
        stdinPipe = 1;
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
        NativePtr desc = stdinPipe != 0
                ? ProcNative.spawnPipe(file, joined, args.size(), directory)
                : ProcNative.spawn(file, joined, args.size(), directory);
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
    int stdinFd = -1;
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
        Process proc = new Process(pid, outFd, errFd);
        if (parts.size() >= 4) {
            proc.stdinFd = IoUtil.parseInt(parts.get(3));
        }
        return proc;
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

    /** Writes text (ASCII/UTF-8) to the child's stdin; only for processes started with pipeStdin(). False on error. */
    boolean writeStdin(String text) {
        if (stdinFd < 0) {
            return false;
        }
        return ProcNative.writeFd(stdinFd, text) >= 0;
    }

    /** Closes the child's stdin (signals EOF to it). */
    void closeStdin() {
        if (stdinFd >= 0) {
            ProcNative.closeFd(stdinFd);
            stdinFd = -1;
        }
    }

    /**
     * Wait up to timeoutMs for output. Returns "" when nothing arrived yet and null once the stream
     * has ended (EOF).
     */
    String readStdoutNb(int maxBytes, int timeoutMs) {
        return readNb(stdoutFd, maxBytes, timeoutMs);
    }

    String readStderrNb(int maxBytes, int timeoutMs) {
        return readNb(stderrFd, maxBytes, timeoutMs);
    }

    static String readNb(int fd, int maxBytes, int timeoutMs) {
        NativePtr p = ProcNative.readNb(fd, maxBytes, timeoutMs);
        if (p == NativePtr.NULL) {
            return null;
        }
        String s = NativeMemory.fromCString(p);
        ProcNative.free(p);
        if (s == null) {
            return "";
        }
        return s;
    }

    /** Exit code when finished, -1000 while still running. Reaps the child on completion. */
    int pollExit() {
        if (alive == 0) {
            return 0;
        }
        int code = ProcNative.tryWait(pid);
        if (code != -1000) {
            alive = 0;
            ProcNative.closeFd(stdoutFd);
            ProcNative.closeFd(stderrFd);
            closeStdin();
        }
        return code;
    }

    int waitFor() {
        int code = ProcNative.waitPid(pid);
        alive = 0;
        ProcNative.closeFd(stdoutFd);
        ProcNative.closeFd(stderrFd);
        closeStdin();
        return code;
    }

    void destroy() {
        ProcNative.kill(pid);
    }

    boolean isAlive() {
        return alive != 0;
    }
}

class Desktop {
    /** Open file or directory with the OS default application. */
    static boolean open(String path) {
        if (path == null) {
            return false;
        }
        return FsNative.open(path) != 0;
    }

    static boolean openPath(FsPath path) {
        if (path == null) {
            return false;
        }
        return open(path.toString());
    }

    /** Move to Recycle Bin / Trash (not permanent delete). */
    static boolean moveToTrash(FsPath path) {
        if (path == null) {
            return false;
        }
        return FsNative.trash(path.toString()) != 0;
    }

    static void openDirectory(String path) {
        open(path);
    }

    static void revealFile(String path) {
        FsNative.reveal(path);
    }

    static void openUrl(String url) {
        open(url);
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

/** Host OS hint for platform-adaptive chrome (not a substitute for target triples). */
class HostPlatform {
    static int MAC = 1;
    static int WINDOWS = 2;
    static int LINUX = 3;

    static int current() {
        String os = Env.get("OS");
        if (os != null) {
            if (os.indexOf("Windows") >= 0 || os.indexOf("windows") >= 0) {
                return WINDOWS;
            }
        }
        FsPath sysLib = new FsPath("/System/Library");
        if (Files.exists(sysLib)) {
            return MAC;
        }
        return LINUX;
    }

    static int isMac() {
        if (current() == MAC) {
            return 1;
        }
        return 0;
    }
}

/**
 * Simple user preferences store under {@link Files#configDir}.
 * Format: key=value lines (UTF-8). Parallel key/value lists (no HashMap iteration).
 */
class UserPreferencesStore {
    String appName;
    ArrayList<String> keys;
    ArrayList<String> values;
    int loaded;

    UserPreferencesStore(String app) {
        appName = app;
        keys = new ArrayList<String>();
        values = new ArrayList<String>();
        loaded = 0;
    }

    FsPath filePath() {
        FsPath dir = Files.configDir(appName);
        Files.createDirectories(dir);
        return dir.resolve("preferences.txt");
    }

    void ensureLoaded() {
        if (loaded != 0) {
            return;
        }
        loaded = 1;
        FsPath f = filePath();
        if (!Files.exists(f)) {
            return;
        }
        String text = Files.readString(f);
        if (text == null) {
            return;
        }
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOfFrom("\n", start);
            int end = nl;
            if (end < 0) {
                end = text.length();
            }
            String line = text.substring(start, end);
            int eq = line.indexOf("=");
            if (eq > 0) {
                keys.add(line.substring(0, eq));
                values.add(line.substringFrom(eq + 1));
            }
            if (nl < 0) {
                start = text.length();
            } else {
                start = nl + 1;
            }
        }
    }

    int indexOfKey(String key) {
        int i = 0;
        while (i < keys.size()) {
            if (key.equals(keys.get(i))) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    String get(String key, String def) {
        ensureLoaded();
        int i = indexOfKey(key);
        if (i < 0) {
            return def;
        }
        return values.get(i);
    }

    int getInt(String key, int def) {
        String v = get(key, "");
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

    void put(String key, String value) {
        ensureLoaded();
        int i = indexOfKey(key);
        if (i < 0) {
            keys.add(key);
            values.add(value);
        } else {
            values.set(i, value);
        }
    }

    void putInt(String key, int value) {
        put(key, "" + value);
    }

    void save() {
        ensureLoaded();
        String out = "";
        int i = 0;
        while (i < keys.size()) {
            out = out + keys.get(i) + "=" + values.get(i) + "\n";
            i = i + 1;
        }
        Files.writeString(filePath(), out);
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
