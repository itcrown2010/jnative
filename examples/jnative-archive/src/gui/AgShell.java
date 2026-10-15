package org.jnative.archive.gui;

import java.util.zip.ZipEntry;
import org.jnative.archive.ArchiveCompressProfile;
import org.jnative.archive.ArchiveCreateOptions;
import org.jnative.archive.ArchiveExtractOptions;
import org.jnative.archive.ArchiveOps;
import org.jnative.archive.ArchiveOverwrite;
import org.jnative.archive.internal.ZipNative;

/**
 * Finder / Explorer shell integration — receives selected paths and drives ArchiveOps via UI.
 * Engine never runs inside the file manager. Passwords never appear on argv.
 *
 * Job file (UTF-8 text, length-prefixed paths — safe for Chinese / spaces / newlines / leading '-'):
 *   JN1\n
 *   &lt;action&gt;\n
 *   &lt;count&gt;\n
 *   for each path: &lt;charLength&gt;\n + path chars (exactly length) + \n
 *
 * @author 张晓达
 */
public class AgShell {
    static int pollerStarted;
    static int draining;
    static final int MAX_QUEUE = 32;
    static ArrayList<String> batchNotes;
    static int batchActive;

    static String pendingAction;
    static String pendingJobFile;
    static int drainOnStart;

    public static void parseArgs(String[] args) {
        pendingAction = null;
        pendingJobFile = null;
        drainOnStart = 1;
        if (args == null) {
            return;
        }
        int i = 0;
        while (i < args.length) {
            String a = args[i];
            if (a == null) {
                i = i + 1;
                continue;
            }
            if (a.equals("--action") && i + 1 < args.length) {
                pendingAction = args[i + 1];
                i = i + 1;
            } else if ((a.equals("--job-file") || a.equals("--paths-file")) && i + 1 < args.length) {
                pendingJobFile = args[i + 1];
                i = i + 1;
            } else if (a.equals("--open") && i + 1 < args.length) {
                pendingAction = "open";
                pendingJobFile = writeTransientOpenJob(args[i + 1]);
                i = i + 1;
            } else if (a.equals("--no-drain-inbox")) {
                drainOnStart = 0;
            }
            i = i + 1;
        }
    }

    public static void startAfterUiReady() {
        startInboxPoller();
        if (pendingJobFile != null && pendingJobFile.length() > 0) {
            ingestJobFile(pendingJobFile, pendingAction);
            pendingJobFile = null;
            pendingAction = null;
        }
        if (drainOnStart != 0) {
            Application.invokeLater(() -> {
                AgShell.drainInbox();
            });
        }
    }

    public static void onJobFinished() {
        Application.invokeLater(() -> {
            AgShell.drainInbox();
        });
    }

    static String writeTransientOpenJob(String path) {
        String inbox = inboxDir();
        Files.createDirectories(FsPath.of(inbox));
        String job = inbox + "/open-" + ("" + ZipNative.nowMs()) + ".job";
        ArrayList<String> list = new ArrayList<String>();
        list.add(path);
        writeJobFileList(job, "open", list);
        return job;
    }

    public static String inboxDir() {
        return Files.configDir("JNativeArchive").resolve("shell-inbox").text;
    }

    /**
     * Extra inboxes: Finder Sync is sandboxed and writes into its container
     * (and optionally /Users/Shared). Host is unsandboxed and drains all.
     */
    static ArrayList<String> allInboxDirs() {
        ArrayList<String> dirs = new ArrayList<String>();
        dirs.add(inboxDir());
        String home = Env.get("HOME");
        if (home != null && home.length() > 0) {
            dirs.add(home + "/Library/Containers/io.jnative.examples.archive.findersync"
                    + "/Data/Library/Application Support/JNativeArchive/shell-inbox");
        }
        dirs.add("/Users/Shared/JNativeArchive/shell-inbox");
        return dirs;
    }

    static void startInboxPoller() {
        if (pollerStarted != 0) {
            return;
        }
        pollerStarted = 1;
        Thread t = new Thread(() -> {
            AgShell.pollInboxLoop();
        });
        t.start();
    }

    static void pollInboxLoop() {
        while (AgUi.alive != 0) {
            try {
                Thread.sleep(400);
            } catch (Exception e) {
            }
            if (AgUi.alive == 0) {
                return;
            }
            if (draining != 0) {
                continue;
            }
            if (canRunNow() != 0) {
                Application.invokeLater(() -> {
                    AgShell.drainInbox();
                });
            } else if (countJobs() > 0 && AgUi.jobBusy == 0 && AgUi.conflictReady != 0
                    && AgUi.createDlg != null) {
                /* Queued shell request supersedes idle create dialog (no job running). */
                Application.invokeLater(() -> {
                    AgDialogs.dismissCreateForQueuedShell();
                    AgShell.drainInbox();
                });
            }
        }
    }

    static int canRunNow() {
        if (AgUi.alive == 0) {
            return 0;
        }
        if (AgUi.jobBusy != 0) {
            return 0;
        }
        if (AgUi.shellUiBusy != 0) {
            return 0;
        }
        if (AgUi.conflictReady == 0) {
            return 0;
        }
        return 1;
    }

    static void ingestJobFile(String jobFile, String actionOverride) {
        String inbox = inboxDir();
        Files.createDirectories(FsPath.of(inbox));
        String dest = jobFile;
        if (jobFile.indexOf("shell-inbox") < 0) {
            dest = inbox + "/job-" + ("" + ZipNative.nowMs()) + ".job";
            String text = File.readString(jobFile);
            if (text == null) {
                return;
            }
            if (actionOverride != null && actionOverride.length() > 0
                    && text.length() >= 4 && text.substring(0, 4).equals("JN1\n")) {
                /* rewrite action line if paths-file + --action form */
                ShellJob parsed = parseJobText(text);
                if (parsed != null) {
                    writeJobFileList(dest, actionOverride, parsed.paths);
                } else {
                    File.writeString(dest, text);
                }
            } else if (actionOverride != null && text.indexOf("JN1\n") != 0) {
                /* plain paths file: one path per line (no newlines inside paths) */
                ArrayList<String> paths = splitLines(text);
                writeJobFileList(dest, actionOverride, paths);
            } else {
                File.writeString(dest, text);
            }
            deletePath(FsPath.of(jobFile));
        }
        if (canRunNow() == 0 && AgUi.jobBusy == 0 && AgUi.conflictReady != 0
                && AgUi.createDlg != null) {
            AgDialogs.dismissCreateForQueuedShell();
        }
        if (canRunNow() == 0) {
            AgUi.jobLabel.setText("已排队（等待当前任务结束）");
            int n = countJobs();
            if (n > MAX_QUEUE) {
                deletePath(FsPath.of(dest));
                Dialogs.info(AgUi.window, "任务队列已满，请稍后再试");
            }
            return;
        }
        drainInbox();
    }

    static int countJobs() {
        int n = 0;
        ArrayList<String> dirs = allInboxDirs();
        int d = 0;
        while (d < dirs.size()) {
            ArrayList<String> names = Files.list(FsPath.of(dirs.get(d)));
            if (names != null) {
                int i = 0;
                while (i < names.size()) {
                    String name = names.get(i);
                    if (name != null && name.length() > 4
                            && name.substring(name.length() - 4, name.length()).equals(".job")) {
                        n = n + 1;
                    }
                    i = i + 1;
                }
            }
            d = d + 1;
        }
        return n;
    }

    static String pickOldestJob() {
        String pick = null;
        ArrayList<String> dirs = allInboxDirs();
        int d = 0;
        while (d < dirs.size()) {
            String dir = dirs.get(d);
            if (!Files.isDirectory(FsPath.of(dir))) {
                d = d + 1;
                continue;
            }
            ArrayList<String> names = Files.list(FsPath.of(dir));
            if (names != null) {
                int i = 0;
                while (i < names.size()) {
                    String n = names.get(i);
                    if (n != null && n.length() > 4
                            && n.substring(n.length() - 4, n.length()).equals(".job")) {
                        String full = dir + "/" + n;
                        if (pick == null || full.compareTo(pick) < 0) {
                            pick = full;
                        }
                    }
                    i = i + 1;
                }
            }
            d = d + 1;
        }
        return pick;
    }

    static void drainInbox() {
        if (draining != 0 || canRunNow() == 0) {
            return;
        }
        draining = 1;
        try {
            String pick = pickOldestJob();
            if (pick == null) {
                maybeShowBatchSummary();
                return;
            }
            String text = File.readString(pick);
            ShellJob job = parseJobText(text);
            if (job == null || validateJob(job) == 0) {
                deletePath(FsPath.of(pick));
                return;
            }
            /* Delete only after validation; requeue if we cannot start. */
            if (canRunNow() == 0) {
                return;
            }
            deletePath(FsPath.of(pick));
            if (job.action.equals("noop")) {
                return;
            }
            if (job.action.equals("extract-fixed") && job.paths.size() >= 2) {
                beginExtract(job.paths.get(0), job.paths.get(1));
                return;
            }
            dispatch(job.action, job.paths);
        } finally {
            draining = 0;
        }
    }

    static int validateJob(ShellJob job) {
        if (job == null || job.action == null || job.paths == null) {
            return 0;
        }
        if (job.action.indexOf("password") >= 0 && !job.action.equals("compress-password")) {
            return 0;
        }
        int i = 0;
        while (i < job.paths.size()) {
            String p = job.paths.get(i);
            if (p == null || p.length() == 0) {
                return 0;
            }
            if (p.charAt(0) != 47 && !(p.length() > 2 && p.charAt(1) == 58)) {
                /* require absolute path (POSIX / or Windows drive) */
                return 0;
            }
            i = i + 1;
        }
        return 1;
    }

    public static void noteBatchResult(String status, String a, String b) {
        if (batchNotes == null) {
            batchNotes = new ArrayList<String>();
        }
        String line = status + "\t" + a + "\t" + b;
        batchNotes.add(line);
        batchActive = 1;
    }

    public static void maybeShowBatchSummary() {
        if (batchActive == 0 || batchNotes == null || batchNotes.size() <= 1) {
            if (countJobs() == 0) {
                batchActive = 0;
                batchNotes = null;
            }
            return;
        }
        if (countJobs() != 0 || AgUi.jobBusy != 0 || AgUi.shellUiBusy != 0) {
            return;
        }
        if (AgUi.quietShell != 0) {
            /* Quiet Finder jobs: no blocking batch dialog. */
            batchActive = 0;
            batchNotes = null;
            return;
        }
        String msg = "批量结果 (" + ("" + batchNotes.size()) + "):\n";
        int i = 0;
        while (i < batchNotes.size() && i < 12) {
            msg = msg + batchNotes.get(i) + "\n";
            i = i + 1;
        }
        Dialogs.info(AgUi.window, msg);
        batchActive = 0;
        batchNotes = null;
    }

    static void dispatch(String action, ArrayList<String> paths) {
        if (action == null) {
            action = "open";
        }
        if (paths == null || paths.size() == 0) {
            AgQuiet.ensureUiForDialog();
            Dialogs.info(AgUi.window, "未收到有效路径");
            return;
        }
        if (action.equals("compress")) {
            runQuickCompress(paths, 0);
            return;
        }
        if (action.equals("compress-password")) {
            runQuickCompress(paths, 1);
            return;
        }
        if (action.equals("compress-options")) {
            AgQuiet.ensureUiForDialog();
            AgUi.enterInteractiveShell();
            AgDialogs.showCreatePrefill(paths, 0);
            return;
        }
        if (action.equals("open")) {
            AgQuiet.ensureUiForDialog();
            AgUi.enterInteractiveShell();
            String p = paths.get(0);
            if (!looksLikeZip(p)) {
                Dialogs.info(AgUi.window, "仅支持打开 ZIP 压缩包:\n" + p);
                return;
            }
            AgActions.loadArchive(p, null);
            return;
        }
        if (action.equals("extract-here")) {
            runExtractBatch(paths, 0);
            return;
        }
        if (action.equals("extract-sibling")) {
            runExtractBatch(paths, 1);
            return;
        }
        if (action.equals("extract-to")) {
            AgQuiet.ensureUiForDialog();
            AgUi.enterInteractiveShell();
            runExtractToChooser(paths);
            return;
        }
        AgQuiet.ensureUiForDialog();
        Dialogs.info(AgUi.window, "未知右键动作: " + action);
    }

    /** Quick compress: standard profile, no full form. encrypt=1 → password dialog only. */
    static void runQuickCompress(ArrayList<String> paths, int encrypt) {
        if (paths == null || paths.size() == 0) {
            return;
        }
        AgUi.beginQuietJob();
        String out = resolveQuickZipOut(paths);
        if (out == null || out.length() == 0) {
            AgShell.onJobFinished();
            return;
        }
        if (encrypt != 0) {
            AgDialogs.promptPasswordThenCompress(paths, out);
            return;
        }
        startQuickCompress(paths, out, null);
    }

    static void startQuickCompress(ArrayList<String> paths, String out, String password) {
        String[] srcArr = new String[paths.size()];
        int i = 0;
        while (i < paths.size()) {
            srcArr[i] = paths.get(i);
            i = i + 1;
        }
        ArchiveCreateOptions opt = new ArchiveCreateOptions();
        opt.profile = ArchiveCompressProfile.STANDARD;
        opt.reportProgress = 0;
        if (password != null && password.length() > 0) {
            opt.encrypt = 1;
            opt.password = password;
            opt.aesStrength = 3;
        }
        if (AgUi.quietShell == 0) {
            AgUi.beginQuietJob();
        }
        AgDialogs.runCreate(out, srcArr, opt);
    }

    /**
     * Output next to sources. Returns null if user cancelled save-location prompt
     * (mixed parents).
     */
    static String resolveQuickZipOut(ArrayList<String> sources) {
        String common = commonParentDir(sources);
        if (common == null) {
            /* Mixed parents: must ask for a single output folder. */
            AgQuiet.revealForError();
            AgUi.shellUiBusy = 1;
            String dir = DirectoryDialog.open(AgUi.window);
            AgUi.shellUiBusy = 0;
            if (dir == null || dir.length() == 0 || !Files.isDirectory(FsPath.of(dir))) {
                return null;
            }
            String name = "归档.zip";
            if (sources.size() == 1) {
                name = AgFormat.basename(defaultZipOut(sources));
            }
            return uniqueZipOut(FsPath.of(dir).resolve(name).text);
        }
        return uniqueZipOut(defaultZipOut(sources));
    }

    /** Same parent → path; different parents → null. */
    static String commonParentDir(ArrayList<String> sources) {
        if (sources == null || sources.size() == 0) {
            return null;
        }
        FsPath p0 = FsPath.of(sources.get(0)).getParent();
        if (p0 == null) {
            return null;
        }
        String parent = p0.text;
        int i = 1;
        while (i < sources.size()) {
            FsPath pi = FsPath.of(sources.get(i)).getParent();
            if (pi == null || !parent.equals(pi.text)) {
                return null;
            }
            i = i + 1;
        }
        return parent;
    }

    static void runExtractBatch(ArrayList<String> archives, int mode) {
        ArrayList<String> zips = filterZips(archives);
        if (zips.size() == 0) {
            AgQuiet.ensureUiForDialog();
            Dialogs.info(AgUi.window, "未选中可解压的 ZIP 文件");
            return;
        }
        String first = zips.get(0);
        String dest = null;
        if (mode != 0) {
            dest = uniqueSiblingDir(first);
        } else {
            dest = destFor(first, 0);
        }
        String queueAction = "extract-here";
        if (mode != 0) {
            queueAction = "extract-sibling";
        }
        int i = 1;
        while (i < zips.size()) {
            String inbox = inboxDir();
            Files.createDirectories(FsPath.of(inbox));
            String job = inbox + "/job-" + ("" + (ZipNative.nowMs() + i)) + ".job";
            ArrayList<String> one = new ArrayList<String>();
            one.add(zips.get(i));
            writeJobFileList(job, queueAction, one);
            i = i + 1;
        }
        AgUi.beginQuietJob();
        beginExtract(first, dest);
    }

    static void runExtractToChooser(ArrayList<String> archives) {
        ArrayList<String> zips = filterZips(archives);
        if (zips.size() == 0) {
            Dialogs.info(AgUi.window, "未选中可解压的 ZIP 文件");
            return;
        }
        String base = DirectoryDialog.open(AgUi.window);
        if (base == null || base.length() == 0) {
            return;
        }
        if (!Files.isDirectory(FsPath.of(base))) {
            Dialogs.info(AgUi.window, "目标目录不可用，请重新选择");
            base = DirectoryDialog.open(AgUi.window);
            if (base == null || base.length() == 0 || !Files.isDirectory(FsPath.of(base))) {
                return;
            }
        }
        String first = zips.get(0);
        String dest0 = FsPath.of(base).resolve(zipBaseName(first)).text;
        int i = 1;
        while (i < zips.size()) {
            String inbox = inboxDir();
            Files.createDirectories(FsPath.of(inbox));
            String job = inbox + "/job-" + ("" + (ZipNative.nowMs() + i)) + ".job";
            ArrayList<String> pair = new ArrayList<String>();
            pair.add(zips.get(i));
            pair.add(FsPath.of(base).resolve(zipBaseName(zips.get(i))).text);
            writeJobFileList(job, "extract-fixed", pair);
            i = i + 1;
        }
        beginExtract(first, dest0);
    }

    static void beginExtract(String archive, String dest) {
        if (archive == null || dest == null) {
            return;
        }
        if (!Files.exists(FsPath.of(archive))) {
            Dialogs.info(AgUi.window, "压缩包已不存在:\n" + archive);
            noteBatchResult("FAIL", archive, "missing");
            onJobFinished();
            return;
        }
        if (archiveNeedsPassword(archive)) {
            AgDialogs.promptPasswordThenExtract(archive, dest);
            return;
        }
        startExtractWithPassword(archive, dest, null);
    }

    public static void startExtractWithPassword(String archive, String dest, String password) {
        /* Create destination only when extract actually starts (cancel → no empty dir). */
        Files.createDirectories(FsPath.of(dest));
        ArchiveExtractOptions opt = new ArchiveExtractOptions();
        opt.conflictPolicy = ArchiveOverwrite.ASK;
        opt.conflictHandler = new AgGuiConflictHandler();
        opt.password = password;
        opt.reportProgress = 0;
        AgUi.conflictApplyAll = -1;
        /* Quiet extract: do not replace the archive currently open in the browser. */
        if (AgUi.quietShell == 0) {
            AgUi.archivePath = archive;
        }
        AgDialogs.runExtract(archive, dest, opt);
    }

    static boolean archiveNeedsPassword(String archive) {
        try {
            ArrayList<ZipEntry> es = ArchiveOps.listEntries(archive, null);
            int i = 0;
            while (i < es.size()) {
                if (es.get(i).isEncrypted()) {
                    return true;
                }
                i = i + 1;
            }
            return false;
        } catch (Exception ex) {
            String m = ex.getMessage();
            if (m != null && (m.indexOf("NEED_PASSWORD") >= 0 || m.indexOf("password") >= 0)) {
                return true;
            }
            return false;
        }
    }

    static String destFor(String archive, int sibling) {
        FsPath parent = FsPath.of(archive).getParent();
        if (sibling != 0) {
            return uniqueSiblingDir(archive);
        }
        return parent.text;
    }

    /** Sibling folder next to zip; if taken, use {@code name (2)}, {@code name (3)}, … */
    static String uniqueSiblingDir(String archive) {
        FsPath parent = FsPath.of(archive).getParent();
        String base = zipBaseName(archive);
        String cand = parent.resolve(base).text;
        if (!Files.exists(FsPath.of(cand))) {
            return cand;
        }
        int n = 2;
        while (n < 1000) {
            String next = parent.resolve(base + " (" + ("" + n) + ")").text;
            if (!Files.exists(FsPath.of(next))) {
                return next;
            }
            n = n + 1;
        }
        return parent.resolve(base + " (" + ("" + ZipNative.nowMs()) + ")").text;
    }

    static String zipBaseName(String archive) {
        String name = FsPath.of(archive).getName();
        if (name == null || name.length() == 0) {
            return "extracted";
        }
        String lower = name.toLowerCase();
        /* Strip compound archive suffixes before sanitizing (e.g. .tar.zip, .zip). */
        if (lower.length() >= 8 && lower.substring(lower.length() - 8, lower.length()).equals(".tar.zip")) {
            name = name.substring(0, name.length() - 8);
        } else if (lower.length() >= 4 && lower.substring(lower.length() - 4, lower.length()).equals(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        /* Sanitize path-illegal / control characters for folder name */
        String out = "";
        int i = 0;
        while (i < name.length()) {
            int c = name.charAt(i);
            if (c < 32 || c == 47 || c == 92 || c == 58 || c == 42 || c == 63
                    || c == 34 || c == 60 || c == 62 || c == 124) {
                out = out + "_";
            } else {
                out = out + name.substring(i, i + 1);
            }
            i = i + 1;
        }
        if (out.length() == 0 || out.equals(".") || out.equals("..")) {
            return "extracted";
        }
        return out;
    }

    public static String uniqueZipOut(String suggested) {
        if (suggested == null || suggested.length() == 0) {
            return suggested;
        }
        if (!Files.exists(FsPath.of(suggested))) {
            return suggested;
        }
        int n = 2;
        while (n < 1000) {
            String cand = ArchiveOps.numberedZipPath(suggested, n);
            if (!Files.exists(FsPath.of(cand))) {
                return cand;
            }
            n = n + 1;
        }
        String base = suggested;
        if (base.length() >= 4) {
            String low = base.toLowerCase();
            if (low.substring(low.length() - 4, low.length()).equals(".zip")) {
                base = suggested.substring(0, suggested.length() - 4);
            }
        }
        return base + " (" + ("" + ZipNative.nowMs()) + ").zip";
    }

    static ArrayList<String> filterZips(ArrayList<String> paths) {
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        while (i < paths.size()) {
            if (looksLikeZip(paths.get(i))) {
                out.add(paths.get(i));
            }
            i = i + 1;
        }
        return out;
    }

    static boolean looksLikeZip(String path) {
        if (path == null || path.length() < 4) {
            return false;
        }
        String n = path.toLowerCase();
        return n.substring(n.length() - 4, n.length()).equals(".zip");
    }

    public static String defaultZipOut(ArrayList<String> sources) {
        if (sources == null || sources.size() == 0) {
            return "";
        }
        if (sources.size() == 1) {
            FsPath p = FsPath.of(sources.get(0));
            FsPath parent = p.getParent();
            String base = p.getName();
            if (p.isDirectory()) {
                return parent.resolve(base + ".zip").text;
            }
            int dot = base.length() - 1;
            while (dot >= 0 && base.charAt(dot) != 46) {
                dot = dot - 1;
            }
            if (dot > 0) {
                base = base.substring(0, dot);
            }
            return parent.resolve(base + ".zip").text;
        }
        return FsPath.of(sources.get(0)).getParent().resolve("归档.zip").text;
    }

    /** True if launch should keep the main browser hidden (quick Finder job). */
    public static int shouldStartQuiet() {
        if (pendingAction != null && pendingAction.equals("open")) {
            return 0;
        }
        if (pendingAction != null && pendingAction.equals("compress-options")) {
            return 0;
        }
        if (pendingAction != null && pendingAction.equals("extract-to")) {
            return 0;
        }
        if (pendingJobFile != null && pendingJobFile.length() > 0) {
            String text = File.readString(pendingJobFile);
            ShellJob j = parseJobText(text);
            if (j != null && j.action != null) {
                if (j.action.equals("open") || j.action.equals("compress-options")
                        || j.action.equals("extract-to")) {
                    return 0;
                }
            }
            return 1;
        }
        if (countJobs() > 0) {
            return 1;
        }
        return 0;
    }

    public static void writeJobFileList(String jobPath, String action, ArrayList<String> paths) {
        String text = "JN1\n" + action + "\n" + ("" + paths.size()) + "\n";
        int i = 0;
        while (i < paths.size()) {
            String p = paths.get(i);
            if (p == null) {
                p = "";
            }
            text = text + ("" + p.length()) + "\n" + p + "\n";
            i = i + 1;
        }
        File.writeString(jobPath, text);
    }

    static ShellJob parseJobText(String text) {
        if (text == null || text.length() < 4 || !text.substring(0, 4).equals("JN1\n")) {
            return null;
        }
        int pos = 4;
        String action = readLineAt(text, pos);
        if (action == null) {
            return null;
        }
        pos = pos + action.length() + 1;
        String countStr = readLineAt(text, pos);
        if (countStr == null) {
            return null;
        }
        pos = pos + countStr.length() + 1;
        int count = parseIntSafe(countStr);
        ArrayList<String> paths = new ArrayList<String>();
        int i = 0;
        while (i < count && pos < text.length()) {
            String lenStr = readLineAt(text, pos);
            if (lenStr == null) {
                break;
            }
            pos = pos + lenStr.length() + 1;
            int len = parseIntSafe(lenStr);
            if (len < 0 || pos + len > text.length()) {
                break;
            }
            String path = text.substring(pos, pos + len);
            pos = pos + len;
            if (pos < text.length() && text.charAt(pos) == 10) {
                pos = pos + 1;
            }
            paths.add(path);
            i = i + 1;
        }
        ShellJob job = new ShellJob();
        job.action = action;
        job.paths = paths;
        return job;
    }

    static String readLineAt(String text, int pos) {
        if (pos >= text.length()) {
            return null;
        }
        int i = pos;
        while (i < text.length() && text.charAt(i) != 10) {
            i = i + 1;
        }
        return text.substring(pos, i);
    }

    static int parseIntSafe(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int v = 0;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return v;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v;
    }

    static ArrayList<String> splitLines(String text) {
        ArrayList<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        int i = 0;
        int start = 0;
        while (i <= text.length()) {
            if (i == text.length() || text.charAt(i) == 10) {
                String line = text.substring(start, i);
                if (line.length() > 0 && line.charAt(line.length() - 1) == 13) {
                    line = line.substring(0, line.length() - 1);
                }
                if (line.length() > 0) {
                    out.add(line);
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static void deletePath(FsPath path) {
        if (path == null) {
            return;
        }
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}

class ShellJob {
    String action;
    ArrayList<String> paths;
}
