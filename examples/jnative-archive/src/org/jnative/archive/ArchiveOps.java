package org.jnative.archive;

import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.jnative.archive.internal.ZipNative;

/**
 * Shared archive operations for CLI and GUI. Wraps java.util.zip + jn_zip.
 * @author 张晓达
 */


/** Deflate presets — mapped to zlib levels; "max" is not claimed faster or always smaller. */
public class ArchiveCompressProfile {
    public static int FAST = 0;
    public static int STANDARD = 1;
    public static int MAX = 2;

    public static int deflateLevel(int profile) {
        if (profile == FAST) {
            return 1;
        }
        if (profile == MAX) {
            return 9;
        }
        return 6;
    }

    public static String label(int profile) {
        if (profile == FAST) {
            return "fast (deflate level 1)";
        }
        if (profile == MAX) {
            return "max (deflate level 9; may be slower, size not guaranteed smaller)";
        }
        return "standard (deflate level 6)";
    }
}

public class ArchiveConflictRequest {
    public String entryName;
    public String destPath;
}

/**
 * Per-conflict decision. applyToRest applies only within the current task.
 */
public class ArchiveConflictDecision {
    public int action;
    public int applyToRest;

    public ArchiveConflictDecision(int act, int all) {
        action = act;
        applyToRest = all;
    }
}

/**
 * GUI/CLI conflict resolver. Return FAIL/SKIP/REPLACE/RENAME (+ optional applyToRest).
 * Batch callers must not block forever — return FAIL when interaction is unavailable.
 */
public class ArchiveConflictHandler {
    public ArchiveConflictDecision onConflict(ArchiveConflictRequest req) {
        return new ArchiveConflictDecision(ArchiveOverwrite.FAIL, 0);
    }
}

public class ArchiveCreateOptions {
    public int profile;
    public int encrypt;
    public int aesStrength;
    public String password;
    public int confirmPasswordInteractive;
    public int followSymlinks;
    public long debugCancelAfterBytes;
    public int reportProgress;

    public ArchiveCreateOptions() {
        profile = ArchiveCompressProfile.STANDARD;
        encrypt = 0;
        aesStrength = 3;
        password = null;
        confirmPasswordInteractive = 0;
        followSymlinks = 0;
        debugCancelAfterBytes = 0L;
        reportProgress = 0;
    }
}

public class ArchiveExtractOptions {
    public int conflictPolicy;
    public ArchiveLimits limits;
    public String password;
    public ArrayList<String> selectedEntries;
    public int batch;
    public int reportProgress;
    public ArchiveConflictHandler conflictHandler;

    public ArchiveExtractOptions() {
        conflictPolicy = ArchiveOverwrite.FAIL;
        limits = new ArchiveLimits();
        password = null;
        selectedEntries = null;
        batch = 0;
        reportProgress = 0;
        conflictHandler = null;
    }
}

/** Final outcome after resource/temp cleanup. errorMessage never includes password material. */
public class ArchiveResult {
    public int state;
    public int errorCode;
    public String errorMessage;
    public String relatedFile;
    public String notes;
    public long entriesDone;
    public long bytesIn;
    public long bytesOut;
    public String outputPath;

    public ArchiveResult() {
        state = ArchiveTaskState.WAITING;
        errorCode = 0;
        errorMessage = "";
        relatedFile = "";
        notes = "";
        entriesDone = 0L;
        bytesIn = 0L;
        bytesOut = 0L;
        outputPath = "";
    }

    public int ok() {
        if (state == ArchiveTaskState.COMPLETED) {
            return 1;
        }
        return 0;
    }
}

public class ArchiveInputItem {
    public String absPath;
    public String entryName;
    public int isDir;

    public ArchiveInputItem(String abs, String entryNm, int dir) {
        absPath = abs;
        entryName = entryNm;
        isDir = dir;
    }
}

/**
 * Unified archive job API. One ArchiveTask per operation; progress/cancel via the task.
 */
public class ArchiveOps {
    public static ArchiveResult create(String outZip, String[] sources, ArchiveCreateOptions opt, ArchiveTask task) {
        ArchiveResult result = new ArchiveResult();
        if (task == null) {
            task = new ArchiveTask();
        }
        if (opt == null) {
            opt = new ArchiveCreateOptions();
        }
        task.begin("compress");
        task.markRunning();
        String tmp = null;
        ZipOutputStream zos = null;
        int published = 0;
        String notes = "symlink_policy=reject(default); format=ZIP";
        notes = notes + "; profile=" + ArchiveCompressProfile.label(opt.profile);
        try {
            if (outZip == null || sources == null || sources.length == 0) {
                throw failEx(ArchiveErrorCode.PARAM, "create requires output zip and at least one source", "");
            }
            String outAbs = absNorm(outZip);
            ArrayList<ArchiveInputItem> items = collectInputs(sources, outAbs, opt, task);
            task.entriesTotal = 0L + items.size();
            tmp = makeCreateTemp(outAbs);
            zos = new ZipOutputStream(tmp);
            task.writerHandle = zos.nativeHandle();
            zos.setLevel(ArchiveCompressProfile.deflateLevel(opt.profile));
            if (opt.encrypt != 0) {
                String password = opt.password;
                if (password == null && opt.confirmPasswordInteractive != 0) {
                    password = ArchivePassword.readInteractiveConfirmed();
                }
                if (password == null || password.length() == 0) {
                    throw failEx(ArchiveErrorCode.PARAM, "empty password rejected", outAbs);
                }
                if (opt.aesStrength == 1 || opt.aesStrength == 2 || opt.aesStrength == 3) {
                    ZipExtensions.setAesStrength(zos, opt.aesStrength);
                } else {
                    ZipExtensions.setAesStrength(zos, 3);
                }
                ZipExtensions.setOutputPassword(zos, password);
                notes = notes + "; encryption=WinZip-AES";
            } else {
                notes = notes + "; encryption=none";
            }
            int i = 0;
            while (i < items.size()) {
                checkCancel(task);
                ArchiveInputItem it = items.get(i);
                task.currentFile = it.entryName;
                if (it.isDir != 0) {
                    addDirEntry(zos, it.entryName);
                } else {
                    addFileStreaming(zos, task, it.absPath, it.entryName, opt);
                }
                i = i + 1;
            }
            checkCancel(task);
            zos.finish();
            /* Exclusive publish: never clobber an existing zip (race-safe). */
            String publishedPath = publishExclusive(tmp, outAbs);
            published = 1;
            tmp = null;
            task.writerHandle = NativePtr.NULL;
            zos.close();
            zos = null;
            task.complete();
            result.state = ArchiveTaskState.COMPLETED;
            result.outputPath = publishedPath;
            result.notes = notes;
            ArchiveProgress p = task.snapshot(1);
            if (p != null) {
                result.bytesIn = p.bytesIn;
                result.entriesDone = p.entriesDone;
            }
            return result;
        } catch (Exception ex) {
            return finishError(result, task, ex, tmp, zos, published, notes);
        }
    }

    public static ArrayList<ZipEntry> listEntries(String archive, String password) {
        ZipFile zf = openReader(archive, password);
        try {
            return zf.entries();
        } finally {
            zf.close();
        }
    }

    public static ArchiveResult test(String archive, String password, ArchiveTask task) {
        ArchiveResult result = new ArchiveResult();
        if (task == null) {
            task = new ArchiveTask();
        }
        task.begin("test");
        task.markRunning();
        ZipFile zf = null;
        try {
            zf = openReader(archive, password);
            task.readerHandle = zf.nativeHandle();
            ZipExtensions.test(zf);
            task.readerHandle = NativePtr.NULL;
            zf.close();
            zf = null;
            task.complete();
            result.state = ArchiveTaskState.COMPLETED;
            result.notes = "integrity=ok";
            result.relatedFile = archive;
            return result;
        } catch (Exception ex) {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Exception ignore) {
                }
                task.readerHandle = NativePtr.NULL;
            }
            return finishError(result, task, ex, null, null, 1, "integrity");
        }
    }

    public static ArchiveResult extract(String archive, String destDir, ArchiveExtractOptions opt, ArchiveTask task) {
        ArchiveResult result = new ArchiveResult();
        if (task == null) {
            task = new ArchiveTask();
        }
        if (opt == null) {
            opt = new ArchiveExtractOptions();
        }
        task.begin("extract");
        task.markRunning();
        ZipFile zf = null;
        int applyAll = -1;
        String notes = "path_safety=enforced; conflict_apply_all=task-local";
        try {
            if (opt.conflictPolicy == ArchiveOverwrite.ASK && opt.batch != 0 && opt.conflictHandler == null) {
                throw failEx(ArchiveErrorCode.PARAM,
                        "conflict policy ask requires a handler; batch mode cannot wait for interactive input", "");
            }
            zf = openReader(archive, opt.password);
            task.readerHandle = zf.nativeHandle();
            ArrayList<ZipEntry> all = zf.entries();
            ArrayList<ZipEntry> work = filterEntries(all, opt.selectedEntries);
            task.entriesTotal = 0L + work.size();
            long maxEntry = 0L;
            long maxTotal = 0L;
            long maxEnt = 0L;
            if (opt.limits != null) {
                maxEntry = opt.limits.maxEntryUncompressed;
                maxTotal = opt.limits.maxTotalUncompressed;
                maxEnt = opt.limits.maxEntries;
            }
            Files.createDirectories(FsPath.of(destDir));
            int i = 0;
            while (i < work.size()) {
                checkCancel(task);
                ZipEntry e = work.get(i);
                task.currentFile = e.name;
                if (e.isSymlink()) {
                    throw failEx(ArchiveErrorCode.LINK, "archive symlink entry rejected", e.name);
                }
                String destPath = joinPath(destDir, e.name);
                int ow = opt.conflictPolicy;
                if (ow == ArchiveOverwrite.ASK) {
                    ow = ArchiveOverwrite.FAIL;
                }
                if (!e.isDirectory() && File.exists(destPath) && Files.isFile(FsPath.of(destPath))) {
                    if (applyAll >= 0) {
                        ow = applyAll;
                    } else if (opt.conflictPolicy == ArchiveOverwrite.ASK) {
                        if (opt.batch != 0 && opt.conflictHandler == null) {
                            throw failEx(ArchiveErrorCode.EXISTS,
                                    "file exists; batch mode cannot ask for conflict decision", destPath);
                        }
                        ArchiveConflictHandler h = opt.conflictHandler;
                        if (h == null) {
                            throw failEx(ArchiveErrorCode.EXISTS,
                                    "file exists; no conflict handler (use fail|skip|replace|rename or provide handler)",
                                    destPath);
                        }
                        ArchiveConflictRequest req = new ArchiveConflictRequest();
                        req.entryName = e.name;
                        req.destPath = destPath;
                        ArchiveConflictDecision d = h.onConflict(req);
                        if (d == null) {
                            throw failEx(ArchiveErrorCode.EXISTS, "conflict handler returned null", destPath);
                        }
                        ow = d.action;
                        if (d.applyToRest != 0) {
                            applyAll = ow;
                        }
                    } else {
                        ow = opt.conflictPolicy;
                    }
                }
                ZipNative.check(ZipNative.readerLocate(zf.nativeHandle(), e.name, 0), "locate");
                int rc = ZipNative.readerExtractEntryEx(zf.nativeHandle(), destDir, ow, maxEntry, maxTotal, maxEnt);
                if (rc == ArchiveErrorCode.EXISTS && opt.conflictPolicy == ArchiveOverwrite.FAIL) {
                    throw failEx(rc, "target exists", destPath);
                }
                ZipNative.check(rc, "extract");
                if (opt.reportProgress != 0) {
                    ArchiveProgress p = task.snapshot(1);
                    if (p != null) {
                        System.out.println("PROGRESS\t" + e.name + "\tout=" + ("" + p.bytesOut)
                                + "\tentries=" + ("" + p.entriesDone));
                    }
                }
                i = i + 1;
            }
            task.readerHandle = NativePtr.NULL;
            zf.close();
            zf = null;
            task.complete();
            result.state = ArchiveTaskState.COMPLETED;
            result.relatedFile = destDir;
            result.notes = notes;
            ArchiveProgress p = task.snapshot(1);
            if (p != null) {
                result.bytesOut = p.bytesOut;
                result.entriesDone = p.entriesDone;
            }
            return result;
        } catch (Exception ex) {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Exception ignore) {
                }
                task.readerHandle = NativePtr.NULL;
            }
            return finishError(result, task, ex, null, null, 1, notes);
        }
    }

    public static ZipFile openReader(String archive, String password) {
        ZipFile zf = new ZipFile(archive);
        if (password != null && password.length() > 0) {
            ZipExtensions.setFilePassword(zf, password);
        }
        return zf;
    }

    public static ArrayList<ZipEntry> filterEntries(ArrayList<ZipEntry> all, ArrayList<String> selected) {
        if (selected == null || selected.size() == 0) {
            return all;
        }
        ArrayList<ZipEntry> out = new ArrayList<ZipEntry>();
        int i = 0;
        while (i < selected.size()) {
            String want = selected.get(i);
            int found = 0;
            int j = 0;
            while (j < all.size()) {
                if (all.get(j).name.equals(want)) {
                    out.add(all.get(j));
                    found = 1;
                    break;
                }
                j = j + 1;
            }
            if (found == 0) {
                throw failEx(ArchiveErrorCode.PATH, "entry not found in archive", want);
            }
            i = i + 1;
        }
        return out;
    }

    public static ArrayList<ArchiveInputItem> collectInputs(String[] sources, String outAbs,
            ArchiveCreateOptions opt, ArchiveTask task) {
        ArrayList<ArchiveInputItem> items = new ArrayList<ArchiveInputItem>();
        HashMap<String, String> entryToSource = new HashMap<String, String>();
        HashMap<String, String> visitedDirs = new HashMap<String, String>();
        int i = 0;
        while (i < sources.length) {
            checkCancel(task);
            String src = sources[i];
            if (src == null || src.length() == 0) {
                throw failEx(ArchiveErrorCode.PARAM, "empty source path", "");
            }
            String abs = absNorm(src);
            if (samePath(abs, outAbs) != 0) {
                throw failEx(ArchiveErrorCode.PARAM, "output archive cannot be an input source", abs);
            }
            if (Files.isSymlink(FsPath.of(abs))) {
                if (opt.followSymlinks != 0) {
                    throw failEx(ArchiveErrorCode.UNSUPPORTED,
                            "followSymlinks is not supported in this release; default is reject", abs);
                }
                throw failEx(ArchiveErrorCode.LINK,
                        "symlink rejected (default: do not follow; not silently skipped)", abs);
            }
            if (Files.isDirectory(FsPath.of(abs))) {
                String rootName = baseName(abs);
                if (rootName.length() == 0) {
                    rootName = "dir";
                }
                collectDir(abs, rootName, outAbs, opt, task, items, entryToSource, visitedDirs);
            } else if (Files.isFile(FsPath.of(abs)) || File.exists(abs)) {
                if (!Files.isFile(FsPath.of(abs))) {
                    throw failEx(ArchiveErrorCode.SOURCE, "not a regular file", abs);
                }
                String name = baseName(abs);
                addItem(items, entryToSource, new ArchiveInputItem(abs, name, 0), abs);
            } else {
                throw failEx(ArchiveErrorCode.SOURCE, "missing input", abs);
            }
            i = i + 1;
        }
        if (items.size() == 0) {
            throw failEx(ArchiveErrorCode.SOURCE, "no files collected", "");
        }
        return items;
    }

    public static void collectDir(String dirAbs, String entryPrefix, String outAbs, ArchiveCreateOptions opt,
            ArchiveTask task, ArrayList<ArchiveInputItem> items, HashMap<String, String> entryToSource,
            HashMap<String, String> visitedDirs) {
        checkCancel(task);
        String key = dirAbs;
        if (visitedDirs.containsKey(key)) {
            throw failEx(ArchiveErrorCode.LINK, "directory cycle detected", dirAbs);
        }
        visitedDirs.put(key, "1");
        String dirEntry = entryPrefix;
        if (endsWithSlash(dirEntry) == 0) {
            dirEntry = dirEntry + "/";
        }
        addItem(items, entryToSource, new ArchiveInputItem(dirAbs, dirEntry, 1), dirAbs);
        ArrayList<String> names = Files.list(FsPath.of(dirAbs));
        int i = 0;
        while (i < names.size()) {
            checkCancel(task);
            String name = names.get(i);
            if (name.equals(".") || name.equals("..")) {
                i = i + 1;
                continue;
            }
            String childAbs = joinPath(dirAbs, name);
            if (samePath(childAbs, outAbs) != 0 || isTempSiblingOf(childAbs, outAbs) != 0) {
                i = i + 1;
                continue;
            }
            if (Files.isSymlink(FsPath.of(childAbs))) {
                if (opt.followSymlinks != 0) {
                    throw failEx(ArchiveErrorCode.UNSUPPORTED,
                            "followSymlinks is not supported; symlink rejected", childAbs);
                }
                throw failEx(ArchiveErrorCode.LINK,
                        "symlink rejected (default: do not follow; not silently skipped)", childAbs);
            }
            String childEntry = entryPrefix + "/" + name;
            if (Files.isDirectory(FsPath.of(childAbs))) {
                collectDir(childAbs, childEntry, outAbs, opt, task, items, entryToSource, visitedDirs);
            } else if (Files.isFile(FsPath.of(childAbs))) {
                addItem(items, entryToSource, new ArchiveInputItem(childAbs, childEntry, 0), childAbs);
            } else {
                throw failEx(ArchiveErrorCode.SOURCE, "unreadable or special file", childAbs);
            }
            i = i + 1;
        }
    }

    public static void addItem(ArrayList<ArchiveInputItem> items, HashMap<String, String> entryToSource,
            ArchiveInputItem it, String sourceAbs) {
        if (entryToSource.containsKey(it.entryName)) {
            String other = entryToSource.get(it.entryName);
            throw failEx(ArchiveErrorCode.EXISTS,
                    "duplicate archive entry name from different sources",
                    it.entryName + " <= " + other + " and " + sourceAbs);
        }
        entryToSource.put(it.entryName, sourceAbs);
        items.add(it);
    }

    public static void addDirEntry(ZipOutputStream zos, String entryName) {
        String n = entryName;
        if (endsWithSlash(n) == 0) {
            n = n + "/";
        }
        ZipEntry e = new ZipEntry(n);
        e.directory = 1;
        e.setMethod(ZipEntry.STORED);
        zos.putNextEntry(e);
        zos.closeEntry();
    }

    public static void addFileStreaming(ZipOutputStream zos, ArchiveTask task, String path, String name,
            ArchiveCreateOptions opt) {
        if (!File.exists(path)) {
            throw failEx(ArchiveErrorCode.SOURCE, "file disappeared before read", path);
        }
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(e);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
        } catch (Exception openEx) {
            throw failEx(ArchiveErrorCode.SOURCE, "unreadable file", path);
        }
        byte[] buf = new byte[65536];
        long lastReport = ZipNative.nowMs();
        try {
            while (true) {
                checkCancel(task);
                int n = 0;
                try {
                    n = fis.read(buf, 0, buf.length);
                } catch (Exception readEx) {
                    throw failEx(ArchiveErrorCode.SOURCE, "read failed (file may have been deleted)", path);
                }
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                zos.write(buf, 0, n);
                if (opt.debugCancelAfterBytes > 0L) {
                    long bin = ZipNative.writerStatBytesIn(zos.nativeHandle());
                    if (bin >= opt.debugCancelAfterBytes) {
                        task.requestCancel();
                        throw failEx(ArchiveErrorCode.CANCELLED, "cancelled", name);
                    }
                }
                if (opt.reportProgress != 0) {
                    long now = ZipNative.nowMs();
                    if (now - lastReport >= 100L) {
                        lastReport = now;
                        ArchiveProgress p = task.snapshot(1);
                        if (p != null) {
                            System.out.println("PROGRESS\t" + name + "\tin=" + ("" + p.bytesIn)
                                    + "\tentries=" + ("" + p.entriesDone));
                        }
                    }
                }
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        zos.closeEntry();
    }

    public static String makeCreateTemp(String outAbs) {
        NativePtr p = ZipNative.makeUniqueTempPath(outAbs);
        if (p == NativePtr.NULL) {
            throw failEx(ArchiveErrorCode.IO, "temp path create failed", outAbs);
        }
        String tmp = NativeMemory.fromCString(p);
        ZipNative.freePtr(p);
        return tmp;
    }

    /**
     * Atomically publish tmp → final without overwriting. On name collision, tries
     * {@code name (2).zip}, {@code name (3).zip}, … Uses link(2) (fails if dest
     * exists); falls back to rename only when dest is still absent.
     */
    public static String publishExclusive(String tmp, String suggested) {
        if (tmp == null || suggested == null || suggested.length() == 0) {
            throw failEx(ArchiveErrorCode.PARAM, "publish paths required", "");
        }
        String dest = suggested;
        int attempt = 0;
        while (attempt < 1000) {
            if (attempt > 0) {
                dest = numberedZipPath(suggested, attempt + 1);
            }
            if (ArchiveFsNative.link(tmp, dest) == 0) {
                ArchiveFsNative.unlink(tmp);
                return absNorm(dest);
            }
            /* link failed: if dest missing, try rename (some volumes lack link). */
            if (!Files.exists(FsPath.of(dest))) {
                if (ArchiveFsNative.rename(tmp, dest) == 0) {
                    return absNorm(dest);
                }
                if (Files.move(FsPath.of(tmp), FsPath.of(dest))) {
                    return absNorm(dest);
                }
            }
            attempt = attempt + 1;
        }
        throw failEx(ArchiveErrorCode.TARGET, "could not publish zip without overwrite", suggested);
    }

    /** {@code /dir/name.zip} + n → {@code /dir/name (n).zip} (n ≥ 2). */
    public static String numberedZipPath(String suggested, int n) {
        if (suggested == null || suggested.length() == 0 || n < 2) {
            return suggested;
        }
        String base = suggested;
        String ext = ".zip";
        if (base.length() >= 4) {
            String low = base.toLowerCase();
            if (low.substring(low.length() - 4, low.length()).equals(".zip")) {
                base = suggested.substring(0, suggested.length() - 4);
                ext = suggested.substring(suggested.length() - 4, suggested.length());
            }
        }
        return base + " (" + ("" + n) + ")" + ext;
    }

    public static void checkCancel(ArchiveTask task) {
        if (task != null && task.cancelRequested != 0) {
            throw failEx(ArchiveErrorCode.CANCELLED, "cancelled", task.currentFile);
        }
    }

    public static ZipException failEx(int code, String msg, String related) {
        String m = msg;
        if (related != null && related.length() > 0) {
            m = msg + ": " + related;
        }
        return new ZipException(m);
    }

    public static ArchiveResult finishError(ArchiveResult result, ArchiveTask task, Exception ex,
            String tmp, ZipOutputStream zos, int published, String notes) {
        String msg = ex.getMessage();
        if (msg == null) {
            msg = "error";
        }
        msg = sanitizeError(msg);
        int code = ArchiveErrorCode.IO;
        if (msg.indexOf("cancelled") >= 0) {
            code = ArchiveErrorCode.CANCELLED;
        } else if (msg.indexOf("password") >= 0 || msg.indexOf("integrity") >= 0
                || msg.indexOf("NEED_PASSWORD") >= 0) {
            /* unify wrong password / auth — never include secret */
            if (msg.indexOf("NEED_PASSWORD") >= 0 || msg.indexOf("need password") >= 0) {
                code = ArchiveErrorCode.NEED_PASSWORD;
                msg = ZipNative.errMsg(ArchiveErrorCode.NEED_PASSWORD);
            } else {
                code = ArchiveErrorCode.PASSWORD;
                msg = ZipNative.errMsg(ArchiveErrorCode.PASSWORD);
            }
        } else if (msg.indexOf("CRC") >= 0) {
            code = ArchiveErrorCode.CRC;
        } else if (msg.indexOf("format") >= 0 || msg.indexOf("FORMAT") >= 0) {
            code = ArchiveErrorCode.FORMAT;
        } else if (msg.indexOf("symlink") >= 0 || msg.indexOf("reparse") >= 0) {
            code = ArchiveErrorCode.LINK;
        } else if (msg.indexOf("exists") >= 0) {
            code = ArchiveErrorCode.EXISTS;
        } else if (msg.indexOf("path") >= 0 || msg.indexOf("unsafe") >= 0) {
            code = ArchiveErrorCode.PATH;
        }
        if (zos != null) {
            try {
                zos.close();
            } catch (Exception ignore) {
            }
            task.writerHandle = NativePtr.NULL;
        }
        if (published == 0 && tmp != null && File.exists(tmp)) {
            File.delete(tmp);
        }
        if (code == ArchiveErrorCode.CANCELLED) {
            task.markCancelledAfterCleanup();
            result.state = ArchiveTaskState.CANCELLED;
        } else {
            task.fail(code, msg);
            result.state = ArchiveTaskState.FAILED;
        }
        result.errorCode = code;
        result.errorMessage = msg;
        result.notes = notes;
        result.relatedFile = task.currentFile;
        return result;
    }

    public static String sanitizeError(String msg) {
        if (msg == null) {
            return "error";
        }
        /* Strip mistaken --password= fragments if any slipped in. */
        if (msg.indexOf("--password") >= 0 || msg.indexOf("password=") >= 0) {
            return "operation failed (details redacted)";
        }
        return msg;
    }

    public static String absNorm(String path) {
        return FsPath.of(path).absolute().normalize().toString();
    }

    public static int samePath(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        if (a.equals(b)) {
            return 1;
        }
        return 0;
    }

    public static int isTempSiblingOf(String path, String outAbs) {
        /* mkstemp style: outAbs + random suffix as sibling file */
        if (path == null || outAbs == null) {
            return 0;
        }
        if (path.length() > outAbs.length() && path.indexOf(outAbs) == 0) {
            return 1;
        }
        return 0;
    }

    public static String joinPath(String dir, String name) {
        if (dir == null || dir.length() == 0) {
            return name;
        }
        int last = dir.charAt(dir.length() - 1);
        if (last == 47 || last == 92) {
            return dir + name;
        }
        return dir + "/" + name;
    }

    public static String baseName(String path) {
        if (path == null || path.length() == 0) {
            return "file";
        }
        int i = path.length() - 1;
        while (i >= 0) {
            int c = path.charAt(i);
            if (c == 47 || c == 92) {
                return path.substring(i + 1, path.length());
            }
            i = i - 1;
        }
        return path;
    }

    public static int endsWithSlash(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int c = s.charAt(s.length() - 1);
        if (c == 47 || c == 92) {
            return 1;
        }
        return 0;
    }
}

/** Minimal POSIX FS helpers for atomic publish when ArchiveFs is not linked yet. */
public class ArchiveFsNative {
    @Native(symbol = "rename")
    public static native int rename(String from, String to);

    @Native(symbol = "link")
    public static native int link(String from, String to);

    @Native(symbol = "unlink")
    public static native int unlink(String path);
}
