package org.jnative.archive.gui;

import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import org.jnative.archive.ArchiveCompressProfile;
import org.jnative.archive.ArchiveConflictDecision;
import org.jnative.archive.ArchiveConflictHandler;
import org.jnative.archive.ArchiveConflictRequest;
import org.jnative.archive.ArchiveCreateOptions;
import org.jnative.archive.ArchiveErrorCode;
import org.jnative.archive.ArchiveExtractOptions;
import org.jnative.archive.ArchiveOps;
import org.jnative.archive.ArchiveOverwrite;
import org.jnative.archive.ArchivePassword;
import org.jnative.archive.ArchiveProgress;
import org.jnative.archive.ArchiveResult;
import org.jnative.archive.ArchiveTask;
import org.jnative.archive.ArchiveTaskState;
import org.jnative.archive.ZipExtensions;
import org.jnative.archive.internal.ZipNative;

/**
 * JNative Archive desktop GUI — uses ArchiveOps / ArchiveTask only for business work.
 * No compression/extract/list on the UI thread.
 *
 * @author 张晓达
 */
public class ArchiveApp {
    public static void main(String[] args) {
        Application.setAppName("JNative Archive");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(Theme.MODE_SYSTEM);
        Application.onQuit(() -> {
            AgUi.teardown();
        });
        AgShell.parseArgs(args);
        int quiet = AgShell.shouldStartQuiet();
        if (quiet != 0) {
            Application.setQuitOnLastWindowClose(false);
        }
        AgUi.open(quiet);
        AgShell.startAfterUiReady();
        app.run();
    }
}

/** Row shown in the central table (archive-internal path browsing). */
class AgRow {
    String name;
    String fullName;
    long size;
    long compressed;
    String kind;
    String modified;
    int isDir;
    int encrypted;
    int aes;
}

class AgNameCol implements ColumnRenderer<AgRow> {
    String getTitle() {
        return "名称";
    }

    String renderRow(AgRow r) {
        if (r == null) {
            return "";
        }
        if (r.isDir != 0) {
            return r.name + "/";
        }
        if (r.encrypted != 0) {
            return "* " + r.name;
        }
        return r.name;
    }
}

class AgRowIds implements TableRowIdProvider<AgRow> {
    String rowId(AgRow row) {
        if (row == null) {
            return "";
        }
        return row.fullName;
    }
}

class AgSizeCol implements ColumnRenderer<AgRow> {
    String getTitle() {
        return "原始大小";
    }

    String renderRow(AgRow r) {
        if (r == null || r.isDir != 0) {
            return "";
        }
        return AgFormat.bytes(r.size);
    }
}

class AgCompCol implements ColumnRenderer<AgRow> {
    String getTitle() {
        return "压缩后";
    }

    String renderRow(AgRow r) {
        if (r == null || r.isDir != 0) {
            return "";
        }
        return AgFormat.bytes(r.compressed);
    }
}

class AgTypeCol implements ColumnRenderer<AgRow> {
    String getTitle() {
        return "类型";
    }

    String renderRow(AgRow r) {
        if (r == null) {
            return "";
        }
        return r.kind;
    }
}

class AgTimeCol implements ColumnRenderer<AgRow> {
    String getTitle() {
        return "修改时间";
    }

    String renderRow(AgRow r) {
        if (r == null) {
            return "";
        }
        return r.modified;
    }
}

class AgFormat {
    static String bytes(long n) {
        if (n < 0L) {
            return "—";
        }
        if (n < 1024L) {
            return ("" + n) + " B";
        }
        if (n < 1048576L) {
            return ("" + (n / 1024L)) + " KB";
        }
        if (n < 1073741824L) {
            return ("" + (n / 1048576L)) + " MB";
        }
        return ("" + (n / 1073741824L)) + " GB";
    }

    static String basename(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        return FsPath.of(path).getName();
    }

    static String parentDir(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        FsPath p = FsPath.of(path).getParent();
        if (p == null) {
            return "";
        }
        return p.text;
    }

    /** Middle-ellipsis for long paths (keeps head and tail). */
    static String middleEllipsis(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (maxChars < 8 || s.length() <= maxChars) {
            return s;
        }
        int keep = (maxChars - 1) / 2;
        int head = keep;
        int tail = maxChars - 1 - head;
        if (tail < 4) {
            tail = 4;
            head = maxChars - 1 - tail;
        }
        return s.substring(0, head) + "…" + s.substring(s.length() - tail, s.length());
    }

    static String archiveTitle(String path) {
        String n = basename(path);
        if (n == null || n.length() == 0) {
            return "JNative Archive";
        }
        return n + " — JNative Archive";
    }

    static String errTitle(int code) {
        if (code == ArchiveErrorCode.NEED_PASSWORD) {
            return "需要密码";
        }
        if (code == ArchiveErrorCode.PASSWORD) {
            return "密码错误或内容校验失败";
        }
        if (code == ArchiveErrorCode.CRC) {
            return "文件损坏（CRC）";
        }
        if (code == ArchiveErrorCode.FORMAT) {
            return "不支持或损坏的格式";
        }
        if (code == ArchiveErrorCode.SPACE) {
            return "磁盘空间不足";
        }
        if (code == ArchiveErrorCode.EXISTS) {
            return "目标冲突";
        }
        if (code == ArchiveErrorCode.CANCELLED) {
            return "已取消";
        }
        if (code == ArchiveErrorCode.SOURCE || code == ArchiveErrorCode.TARGET) {
            return "权限或路径错误";
        }
        if (code == ArchiveErrorCode.PATH || code == ArchiveErrorCode.LINK) {
            return "路径不安全";
        }
        if (code == ArchiveErrorCode.UNSUPPORTED) {
            return "不支持的操作";
        }
        return "操作失败";
    }

    static String sanitizeDetail(String msg) {
        return ArchiveOps.sanitizeError(msg);
    }
}

class AgPathRenderer implements CellRenderer<String> {
    public String render(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String name = AgFormat.basename(value);
        if (name == null || name.length() == 0) {
            name = value;
        }
        return name + "  —  " + AgFormat.middleEllipsis(value, 64);
    }
}

class AgUi {
    static Window window;
    static TableView<AgRow> table;
    static Label addrLabel;
    static Label statusLeft;
    static Label statusRight;
    static Label jobLabel;
    static ProgressBar progress;
    static Button btnNew;
    static Button btnOpen;
    static Button btnExtract;
    static Button btnTest;
    static Button btnUp;
    static Button btnCancel;
    static Button btnCopyPath;
    static Button btnCopyErr;
    static Label emptyHint;
    static int alive;
    /** Monotonic job identity. Only the matching epoch may update job UI. */
    static int jobGen;
    static int jobBusy;
    /** Modal shell UI (password / create / conflict) — blocks inbox drain. */
    static int shellUiBusy;
    static ArchiveTask jobTask;

    /** True if epoch is still the accepted current job. */
    static int isCurrentJob(int epoch) {
        if (alive == 0) {
            return 0;
        }
        if (epoch != jobGen) {
            return 0;
        }
        return 1;
    }

    /**
     * Begin a new job epoch. Cancels any prior task so its completion cannot
     * own UI updates. Keeps jobBusy set through finish UI (including modals).
     */
    static int beginJob() {
        if (jobTask != null) {
            jobTask.requestCancel();
        }
        jobGen = jobGen + 1;
        jobBusy = 1;
        return jobGen;
    }
    static String lastOutputPath;
    static String lastJobPhase;
    static String archivePath;
    static String cwd;
    static ArrayList<ZipEntry> allEntries;
    static ArrayList<AgRow> viewRows;
    static String lastErrorDetail;
    static int encryptedMeta;
    /* Create dialog retained non-sensitive fields */
    static String createOutPath;
    static int createProfileIdx;
    static int createEncrypt;
    static ArrayList<String> createSources;
    static TextField createOutField;
    static TextField createDirField;
    static TextField createPass1;
    static TextField createPass2;
    static CheckBox createEncBox;
    static ComboBox<String> createProfileBox;
    static Label createErrLabel;
    static Label createSrcErr;
    static Label createOutErr;
    static Label createSrcCount;
    static ListView<String> createSrcList;
    static Label createPassLab;
    static Label createPass2Lab;
    static Button createOkBtn;
    static int createForceEncrypt;
    static Window createDlg;
    static TextField extractDestField;
    static TextField extractPassField;
    static ComboBox<String> extractConflictBox;
    static CheckBox extractOnlySel;
    static Label extractErrLabel;
    static Window extractDlg;
    static AgRow extractSelRow;
    static TextField testPassField;
    static Label testPassErr;
    static Window testPassDlg;
    /* Conflict bridge (worker ↔ UI) */
    static ArchiveConflictRequest conflictReq;
    static ArchiveConflictDecision conflictDecision;
    static int conflictReady;
    static int conflictApplyAll;
    static int conflictApplyAction;
    /* Password bridge */
    static String passwordResult;
    static int passwordReady;
    static int passwordCancelled;
    /** Finder quick job: keep main browser hidden; use progress float / toasts. */
    static int quietShell;
    static int mainShown;
    static long quietJobStartMs;
    static Window progressFloat;
    static Label progressFloatLabel;
    static ProgressBar progressFloatBar;
    static Button progressFloatCancel;
    static Window toastWindow;

    static void open() {
        open(0);
    }

    static void open(int quiet) {
        alive = 1;
        quietShell = quiet;
        mainShown = 0;
        jobGen = 0;
        jobBusy = 0;
        shellUiBusy = 0;
        jobTask = null;
        archivePath = "";
        cwd = "";
        allEntries = new ArrayList<ZipEntry>();
        viewRows = new ArrayList<AgRow>();
        lastErrorDetail = "";
        lastOutputPath = "";
        lastJobPhase = "";
        encryptedMeta = 0;
        createOutPath = "";
        createProfileIdx = 1;
        createEncrypt = 0;
        conflictReady = 1;
        passwordReady = 1;
        conflictApplyAll = -1;

        window = new Window("JNative Archive", 1000, 680);
        window.setMinSize(780, 480);
        window.onClose(() -> {
            AgUi.onClose();
        });

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        Toolbar toolbar = new Toolbar();
        toolbar.spacing = Theme.SPACE_2;
        toolbar.padding = Theme.SPACE_2;
        btnNew = toolbar.addPushToolButton("新建", "plus.rectangle", "创建压缩包", () -> {
            AgDialogs.showCreate();
        });
        btnOpen = toolbar.addPushToolButton("打开", "folder", "打开压缩包", () -> {
            AgActions.openArchive();
        });
        btnExtract = toolbar.addPushToolButton("解压", "arrow.down.doc", "解压到…", () -> {
            AgDialogs.showExtract();
        });
        btnTest = toolbar.addPushToolButton("校验", "checkmark.seal", "完整性校验", () -> {
            AgActions.testArchive();
        });
        toolbar.addFlexibleSpace();
        btnCancel = toolbar.addPushToolButton("取消", "xmark", "取消当前任务", () -> {
            AgActions.cancelJob();
        });
        btnCancel.setEnabled(false);
        toolbar.prefHeight = Theme.TOOLBAR_HEIGHT;
        toolbar.height = Theme.TOOLBAR_HEIGHT;

        HBox addrRow = new HBox();
        addrRow.spacing = Theme.SPACE_2;
        addrRow.padding = Theme.SPACE_2;
        addrRow.prefHeight = 32;
        addrRow.height = 32;
        btnUp = new Button("上级");
        btnUp.setChromeStyle(1);
        btnUp.setToolTip("返回上级目录");
        btnUp.onClick(() -> {
            AgActions.goUp();
        });
        addrLabel = new Label("打开压缩包，或从菜单新建");
        addrLabel.setPrefSize(720, 22);
        addrLabel.hgrow = 1;
        addrLabel.prefWidth = 0;
        Theme.applyStyle(addrLabel, Theme.STYLE_SECONDARY);
        btnCopyPath = new Button("复制路径");
        btnCopyPath.setChromeStyle(1);
        btnCopyPath.setEnabled(false);
        btnCopyPath.onClick(() -> {
            AgActions.copyArchivePath();
        });
        Label addrSep = new Label(" ");
        addrSep.setPrefSize(8, 20);
        addrRow.add(btnUp);
        addrRow.add(addrSep);
        addrRow.add(addrLabel);
        addrRow.add(btnCopyPath);

        table = new TableView<AgRow>();
        /* Name flexes; numeric/meta columns stay compact (max enforced in Cocoa). */
        table.addColumnDef("名称", new AgNameCol(), 280, "L", 1, 0);
        table.addColumnDef("原始大小", new AgSizeCol(), 96, "R", 0, 120);
        table.addColumnDef("压缩后", new AgCompCol(), 96, "R", 0, 120);
        table.addColumnDef("类型", new AgTypeCol(), 96, "L", 0, 120);
        table.addColumnDef("修改时间", new AgTimeCol(), 140, "L", 0, 180);
        table.setEmptyMessage("打开压缩包，或从「新建」开始");
        table.setRowIdProvider(new AgRowIds());
        table.getSelectionModel().setMultiple(true);
        table.onSelectionChanged((ev) -> {
            AgUi.refreshStatus();
        });
        table.onDoubleClick((ev) -> {
            AgActions.activateRow();
        });
        table.onHeaderClick((ev) -> {
            if (ev != null) {
                table.sortByColumn(ev.selectedIndex);
            }
        });
        table.onAction((ev) -> {
            AgActions.activateRow();
        });
        table.setPrefSize(960, 480);
        table.vgrow = 1;

        HBox statusBar = new HBox();
        statusBar.spacing = Theme.SPACE_3;
        statusBar.padding = Theme.SPACE_2;
        statusBar.prefHeight = 28;
        statusBar.height = 28;
        statusLeft = new Label("条目 0");
        statusLeft.setPrefSize(220, 20);
        Theme.applyStyle(statusLeft, Theme.STYLE_SECONDARY);
        progress = new ProgressBar();
        progress.setPrefSize(140, 12);
        progress.setVisible(false);
        jobLabel = new Label("就绪");
        jobLabel.setPrefSize(420, 20);
        jobLabel.hgrow = 1;
        jobLabel.prefWidth = 0;
        Theme.applyStyle(jobLabel, Theme.STYLE_SECONDARY);
        btnCopyErr = new Button("复制错误详情");
        btnCopyErr.setChromeStyle(1);
        btnCopyErr.setEnabled(false);
        btnCopyErr.setVisible(false);
        btnCopyErr.onClick(() -> {
            AgActions.copyError();
        });
        statusRight = new Label("");
        statusRight.setPrefSize(8, 20);
        statusBar.add(statusLeft);
        statusBar.add(progress);
        statusBar.add(jobLabel);
        statusBar.add(btnCopyErr);
        statusBar.add(statusRight);

        root.add(toolbar);
        root.add(addrRow);
        root.add(table);
        root.add(statusBar);
        window.setContent(root);

        MenuBar mb = new MenuBar();
        Menu file = new Menu("文件");
        MenuItem miNew = new MenuItem("新建压缩包...", () -> {
            AgDialogs.showCreate();
        });
        MenuItem miOpen = new MenuItem("打开...", () -> {
            AgActions.openArchive();
        });
        MenuItem miExtract = new MenuItem("解压...", () -> {
            AgDialogs.showExtract();
        });
        MenuItem miTest = new MenuItem("校验", () -> {
            AgActions.testArchive();
        });
        MenuItem miUp = new MenuItem("返回上级目录", () -> {
            AgActions.goUp();
        });
        MenuItem miCopyPath = new MenuItem("复制压缩包路径", () -> {
            AgActions.copyArchivePath();
        });
        MenuItem miQuit = new MenuItem("退出", () -> {
            AgUi.confirmQuit();
        });
        file.add(miNew);
        file.add(miOpen);
        file.add(miExtract);
        file.add(miTest);
        file.add(miUp);
        file.add(miCopyPath);
        file.add(miQuit);
        mb.add(file);
        mb.add(AppMenus.helpMenu(null));
        window.setMenuBar(mb);

        refreshChrome();
        jobLabel.setText("就绪");
        if (quietShell == 0) {
            showMainWindow();
            Application.invokeLater(() -> {
                AgUi.maybeGuideFinderExtension();
            });
        }
    }

    static void showMainWindow() {
        if (window == null || window.disposed != 0) {
            return;
        }
        window.show();
        mainShown = 1;
        quietShell = 0;
        Application.setQuitOnLastWindowClose(true);
    }

    /** Switch from quiet Finder job to interactive UI (open / options / extract-to). */
    static void enterInteractiveShell() {
        quietShell = 0;
        showMainWindow();
        if (window != null) {
            window.activate();
        }
    }

    static void beginQuietJob() {
        quietShell = 1;
        quietJobStartMs = ZipNative.nowMs();
        Application.setQuitOnLastWindowClose(false);
        if (mainShown == 0 && window != null && window.disposed == 0) {
            window.hide();
        }
    }

    /** One-shot guidance to enable Finder Sync (no repeated popups). */
    static void maybeGuideFinderExtension() {
        String marker = Files.configDir("JNativeArchive").resolve("finder-extension-guide-shown").text;
        if (Files.exists(FsPath.of(marker))) {
            return;
        }
        String msg = "若 Finder 右键看不到「JNative」菜单：\n\n"
            + "系统设置 → 通用 → 登录项与扩展 → 文件提供程序\n"
            + "勾选「JNative」后，在文件上右键即可使用。\n\n"
            + "也可在终端执行：\n"
            + "pluginkit -e use -i io.jnative.examples.archive.findersync\n\n"
            + "此提示只显示一次。";
        Dialogs.info(window, msg);
        try {
            File.writeString(marker, "1\n");
        } catch (Exception e) {
        }
    }

    static void onClose() {
        if (Application.quitting != 0) {
            teardown();
            return;
        }
        if (jobBusy != 0) {
            if (!Dialogs.confirm(window, "有任务正在运行。确定取消任务并退出？")) {
                window.vetoClose();
                return;
            }
            if (jobTask != null) {
                jobTask.requestCancel();
            }
            /* Wait briefly for cleanup; do not destroy handles while worker uses them. */
            int i = 0;
            while (jobBusy != 0 && i < 200) {
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                }
                i = i + 1;
            }
        }
        teardown();
    }

    static void confirmQuit() {
        if (jobBusy != 0) {
            if (!Dialogs.confirm(window, "有任务正在运行。取消并退出？")) {
                return;
            }
            if (jobTask != null) {
                jobTask.requestCancel();
            }
        }
        Application.requestQuit();
    }

    static void teardown() {
        alive = 0;
        jobGen = jobGen + 1;
        if (jobTask != null) {
            jobTask.requestCancel();
        }
        clearPasswordBridge();
        conflictReady = 1;
    }

    static void clearPasswordBridge() {
        passwordResult = null;
        passwordReady = 1;
        passwordCancelled = 0;
    }

    static void refreshChrome() {
        int has = 0;
        if (archivePath != null && archivePath.length() > 0) {
            has = 1;
        }
        btnExtract.setEnabled(has != 0 && jobBusy == 0);
        btnTest.setEnabled(has != 0 && jobBusy == 0);
        btnNew.setEnabled(jobBusy == 0);
        btnOpen.setEnabled(jobBusy == 0);
        btnUp.setEnabled(has != 0 && cwd != null && cwd.length() > 0 && jobBusy == 0);
        btnCancel.setEnabled(jobBusy != 0);
        btnCopyPath.setEnabled(has != 0);
        if (has == 0) {
            window.setTitle("JNative Archive");
            addrLabel.setText("打开压缩包，或从菜单新建");
            addrLabel.setToolTip("");
            table.setEmptyMessage("打开压缩包，或从「新建」开始");
        } else {
            window.setTitle(AgFormat.archiveTitle(archivePath));
            String inside = "/";
            if (cwd != null && cwd.length() > 0) {
                inside = cwd;
                if (inside.charAt(inside.length() - 1) == 47) {
                    inside = inside.substring(0, inside.length() - 1);
                }
            }
            String crumb = AgFormat.basename(archivePath);
            if (inside.length() > 0 && !inside.equals("/")) {
                crumb = crumb + " › " + inside;
            } else {
                crumb = crumb + " › /";
            }
            if (encryptedMeta != 0) {
                crumb = crumb + "  ·  含加密";
            }
            String fullCrumb = crumb;
            addrLabel.setText(AgFormat.middleEllipsis(crumb, 56));
            addrLabel.setToolTip(archivePath + "\n包内: /" + (cwd == null ? "" : cwd)
                    + "\n" + fullCrumb);
            table.setEmptyMessage("此目录为空");
        }
        refreshStatus();
    }

    static void refreshStatus() {
        int n = 0;
        if (viewRows != null) {
            n = viewRows.size();
        }
        int sel = 0;
        if (table != null && table.getSelectionModel() != null
                && table.getSelectionModel().getSelectedIndex() >= 0) {
            sel = 1;
            if (table.getSelectionModel().selectedIndices != null) {
                int m = table.getSelectionModel().selectedIndices.size();
                if (m > sel) {
                    sel = m;
                }
            }
        }
        statusLeft.setText("条目 " + String.valueOf(n) + " · 选中 " + String.valueOf(sel));
    }

    static void setJobUi(String text, int indeterminate, double pct) {
        if (alive == 0) {
            return;
        }
        if (text == null) {
            text = "";
        }
        if (window != null && window.disposed == 0 && mainShown != 0) {
            jobLabel.setText(AgFormat.middleEllipsis(text, 72));
            jobLabel.setToolTip(text);
            if (indeterminate != 0) {
                progress.setVisible(true);
                progress.setIndeterminate(true);
            } else if (pct >= 0.0) {
                progress.setVisible(true);
                progress.setIndeterminate(false);
                progress.setProgress(pct);
            } else {
                progress.setVisible(false);
            }
        }
        AgQuiet.onJobUi(text, indeterminate, pct);
    }

    static void showError(int code, String detail, String related) {
        AgQuiet.revealForError();
        lastErrorDetail = AgFormat.errTitle(code) + "\n" + AgFormat.sanitizeDetail(detail);
        if (related != null && related.length() > 0) {
            lastErrorDetail = lastErrorDetail + "\n文件: " + related;
        }
        btnCopyErr.setVisible(true);
        btnCopyErr.setEnabled(true);
        jobLabel.setText(AgFormat.errTitle(code));
        Dialogs.error(window, AgFormat.errTitle(code) + "\n\n" + AgFormat.sanitizeDetail(detail));
    }
}

class AgActions {
    static void openArchive() {
        String path = FileDialog.open(AgUi.window, "zip");
        if (path == null || path.length() == 0) {
            return;
        }
        /* beginJob supersedes any in-flight/cancelling task (epoch gate). */
        loadArchive(path, null);
    }

    static void loadArchive(String path, String password) {
        int gen = AgUi.beginJob();
        AgUi.refreshChrome();
        AgUi.table.setLoading(true, "正在读取目录...");
        AgUi.setJobUi("加载中...", 1, -1.0);
        AgJobCtx.loadPath = path;
        AgJobCtx.loadPassword = password;
        AgJobCtx.loadGen = gen;
        Thread t = new Thread(() -> {
            AgJobCtx.runLoadWorker();
        });
        t.start();
    }

    static void activateRow() {
        AgRow r = AgUi.table.getSelectionModel().getSelectedItem();
        if (r == null) {
            return;
        }
        if (r.isDir != 0) {
            AgUi.cwd = r.fullName;
            if (AgUi.cwd.length() > 0) {
                int last = AgUi.cwd.charAt(AgUi.cwd.length() - 1);
                if (last != 47) {
                    AgUi.cwd = AgUi.cwd + "/";
                }
            }
            AgNav.rebuildView();
            AgUi.refreshChrome();
        } else {
            AgUi.jobLabel.setText(AgFormat.middleEllipsis(r.fullName, 64));
            AgUi.jobLabel.setToolTip(r.fullName);
        }
    }

    static void goUp() {
        if (AgUi.cwd == null || AgUi.cwd.length() == 0) {
            return;
        }
        String c = AgUi.cwd;
        if (c.length() > 0 && c.charAt(c.length() - 1) == 47) {
            c = c.substring(0, c.length() - 1);
        }
        int slash = -1;
        int i = 0;
        while (i < c.length()) {
            if (c.charAt(i) == 47) {
                slash = i;
            }
            i = i + 1;
        }
        if (slash < 0) {
            AgUi.cwd = "";
        } else {
            AgUi.cwd = c.substring(0, slash + 1);
        }
        AgNav.rebuildView();
        AgUi.refreshChrome();
    }

    static void testArchive() {
        if (AgUi.archivePath == null || AgUi.archivePath.length() == 0 || AgUi.jobBusy != 0) {
            return;
        }
        if (AgUi.encryptedMeta != 0) {
            AgDialogs.showTestPassword();
            return;
        }
        runTest(null);
    }

    static void runTest(String pass) {
        AgJobCtx.kind = 1;
        AgJobCtx.phase = "校验";
        AgJobCtx.archivePath = AgUi.archivePath;
        AgJobCtx.password = pass;
        startJob();
    }

    static void cancelJob() {
        if (AgUi.jobTask != null) {
            AgUi.jobTask.requestCancel();
            AgUi.setJobUi("正在取消...", 1, -1.0);
        }
    }

    static void copyError() {
        if (AgUi.lastErrorDetail == null || AgUi.lastErrorDetail.length() == 0) {
            return;
        }
        Clipboard.setText(AgUi.lastErrorDetail);
        Dialogs.info(AgUi.window, "已复制错误详情到剪贴板。");
    }

    static void copyArchivePath() {
        if (AgUi.archivePath == null || AgUi.archivePath.length() == 0) {
            return;
        }
        Clipboard.setText(AgUi.archivePath);
        AgUi.jobLabel.setText("已复制路径");
    }

    static void startJob() {
        if (AgUi.jobBusy != 0) {
            Dialogs.info(AgUi.window, "已有任务运行，请等待完成或取消。");
            return;
        }
        int gen = AgUi.beginJob();
        ArchiveTask task = new ArchiveTask();
        AgUi.jobTask = task;
        AgUi.refreshChrome();
        AgUi.setJobUi(AgJobCtx.phase + "...", 1, -1.0);
        AgJobCtx.workerGen = gen;
        AgJobCtx.workerTask = task;
        Thread t = new Thread(() -> {
            AgJobCtx.runJobWorker();
        });
        Thread tick = new Thread(() -> {
            AgJobCtx.runProgressTicker();
        });
        t.start();
        tick.start();
    }
}

/** Job parameters — avoids typed lambdas (unsupported). kind: 1=test 2=create 3=extract */
class AgJobCtx {
    static int kind;
    static String phase;
    static String archivePath;
    static String password;
    static String outPath;
    static String destPath;
    static String[] sources;
    static ArchiveCreateOptions createOpt;
    static ArchiveExtractOptions extractOpt;
    static ArchiveResult pendingResult;
    static ArchiveProgress pendingProgress;
    static int workerGen;
    static ArchiveTask workerTask;
    static int pendingProgressEpoch;
    static String loadPath;
    static String loadPassword;
    static int loadGen;
    static ArrayList<ZipEntry> loadList;
    static String loadErr;
    static int loadCode;

    static ArchiveResult execute(ArchiveTask task) {
        if (kind == 1) {
            return ArchiveOps.test(archivePath, password, task);
        }
        if (kind == 2) {
            return ArchiveOps.create(outPath, sources, createOpt, task);
        }
        if (kind == 3) {
            return ArchiveOps.extract(archivePath, destPath, extractOpt, task);
        }
        ArchiveResult r = new ArchiveResult();
        r.state = ArchiveTaskState.FAILED;
        r.errorCode = ArchiveErrorCode.PARAM;
        r.errorMessage = "unknown job";
        return r;
    }

    static void runJobWorker() {
        ArchiveResult result = null;
        String msg = null;
        try {
            result = execute(workerTask);
        } catch (Exception ex) {
            msg = ex.getMessage();
        }
        if (result == null) {
            result = new ArchiveResult();
            result.state = ArchiveTaskState.FAILED;
            result.errorCode = ArchiveErrorCode.IO;
            result.errorMessage = AgFormat.sanitizeDetail(msg);
        }
        pendingResult = result;
        Application.invokeLater(() -> {
            AgJobCtx.finishJobUi();
        });
    }

    static void finishJobUi() {
        int epoch = workerGen;
        if (AgUi.isCurrentJob(epoch) == 0) {
            return;
        }
        ArchiveResult fr = pendingResult;
        ArchiveTask finished = workerTask;
        if (AgUi.jobTask == finished) {
            AgUi.jobTask = null;
        }
        clearSecrets();
        /* Keep jobBusy until finish UI (modals) returns so nested runloop
           cannot drain a new inbox job that races with this completion. */
        AgUi.setJobUi("", 0, -1.0);
        if (fr != null && fr.state == ArchiveTaskState.CANCELLED) {
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
            AgUi.jobLabel.setText("已取消（非失败）");
        }
        finishOnUi(fr, epoch);
        if (AgUi.isCurrentJob(epoch) == 0) {
            /* Superseded (e.g. create success → loadArchive). New epoch owns busy. */
            return;
        }
        AgUi.jobBusy = 0;
        AgUi.refreshChrome();
        AgUi.refreshStatus();
        AgUi.shellUiBusy = 0;
        AgShell.onJobFinished();
    }

    static void runProgressTicker() {
        int epoch = workerGen;
        while (true) {
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
            if (AgUi.jobBusy == 0) {
                return;
            }
            ArchiveTask jt = AgUi.jobTask;
            if (jt != null && jt == workerTask) {
                ArchiveProgress p = jt.snapshot(0);
                if (p != null) {
                    pendingProgress = p;
                    pendingProgressEpoch = epoch;
                    Application.invokeLater(() -> {
                        AgJobCtx.applyPendingProgress();
                    });
                }
            }
            try {
                Thread.sleep(120);
            } catch (Exception e) {
            }
        }
    }

    static void applyPendingProgress() {
        if (AgUi.isCurrentJob(pendingProgressEpoch) == 0) {
            return;
        }
        AgJobUi.applyProgress(pendingProgress);
    }

    static void runLoadWorker() {
        loadList = null;
        loadErr = null;
        loadCode = 0;
        try {
            loadList = ArchiveOps.listEntries(loadPath, loadPassword);
        } catch (Exception ex) {
            loadErr = ex.getMessage();
            loadCode = ArchiveErrorCode.FORMAT;
            if (loadErr != null && loadErr.indexOf("NEED_PASSWORD") >= 0) {
                loadCode = ArchiveErrorCode.NEED_PASSWORD;
            } else if (loadErr != null && (loadErr.indexOf("password") >= 0 || loadErr.indexOf("integrity") >= 0)) {
                loadCode = ArchiveErrorCode.PASSWORD;
            }
        }
        Application.invokeLater(() -> {
            AgJobCtx.finishLoadUi();
        });
    }

    static void finishLoadUi() {
        int epoch = loadGen;
        if (AgUi.isCurrentJob(epoch) == 0) {
            return;
        }
        AgUi.setJobUi("", 0, -1.0);
        AgUi.table.setLoading(false, "");
        if (loadList == null) {
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
            AgUi.showError(loadCode, loadErr, loadPath);
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
            AgUi.jobBusy = 0;
            AgUi.refreshChrome();
            AgUi.shellUiBusy = 0;
            AgShell.onJobFinished();
            return;
        }
        if (AgUi.isCurrentJob(epoch) == 0) {
            return;
        }
        AgUi.archivePath = loadPath;
        AgUi.cwd = "";
        AgUi.allEntries = loadList;
        AgUi.encryptedMeta = 0;
        int i = 0;
        while (i < loadList.size()) {
            if (loadList.get(i).isEncrypted()) {
                AgUi.encryptedMeta = 1;
                break;
            }
            i = i + 1;
        }
        AgNav.rebuildView();
        if (AgUi.isCurrentJob(epoch) == 0) {
            return;
        }
        AgUi.jobLabel.setText("已打开");
        AgUi.jobBusy = 0;
        AgUi.refreshChrome();
        AgUi.shellUiBusy = 0;
        AgShell.onJobFinished();
    }

    static void clearSecrets() {
        password = null;
        loadPassword = null;
        if (createOpt != null) {
            createOpt.password = null;
        }
        if (extractOpt != null) {
            extractOpt.password = null;
        }
    }

    static void finishOnUi(ArchiveResult r, int epoch) {
        if (r == null) {
            return;
        }
        if (AgUi.isCurrentJob(epoch) == 0) {
            return;
        }
        AgUi.btnCopyErr.setVisible(false);
        AgUi.btnCopyErr.setEnabled(false);
        if (r.state == ArchiveTaskState.CANCELLED) {
            AgUi.jobLabel.setText("已取消");
            AgQuiet.hideProgress();
            /* Quiet / queued: no blocking cancel dialog. */
            if (AgUi.quietShell == 0 && AgShell.countJobs() == 0) {
                Dialogs.info(AgUi.window, "操作已取消。临时文件已清理，原有文件未改动。");
            }
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
            AgShell.noteBatchResult("CANCEL", archivePath, destPath);
            return;
        }
        if (kind == 1) {
            if (r.ok() != 0) {
                AgUi.jobLabel.setText("校验通过");
                Dialogs.info(AgUi.window, "完整性校验通过");
            } else {
                AgUi.showError(r.errorCode, r.errorMessage, r.relatedFile);
            }
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
        } else if (kind == 2) {
            if (r.ok() != 0) {
                AgUi.jobLabel.setText("创建完成");
                AgUi.lastOutputPath = r.outputPath;
                AgShell.noteBatchResult("OK", "compress", r.outputPath);
                if (AgUi.isCurrentJob(epoch) == 0) {
                    return;
                }
                AgQuiet.hideProgress();
                if (AgUi.quietShell != 0) {
                    if (AgShell.countJobs() == 0) {
                        AgQuiet.showSuccessToast("已创建 " + AgFormat.basename(r.outputPath), r.outputPath);
                    }
                    return;
                }
                /* Interactive: optional reveal, then open in browser. */
                if (AgShell.countJobs() == 0) {
                    if (Dialogs.confirm(AgUi.window,
                            "已创建：\n" + AgFormat.basename(r.outputPath) + "\n\n在 Finder 中显示？")) {
                        if (AgUi.isCurrentJob(epoch) != 0) {
                            Desktop.revealFile(r.outputPath);
                        }
                    }
                }
                if (AgUi.isCurrentJob(epoch) == 0) {
                    return;
                }
                AgActions.loadArchive(r.outputPath, null);
                return;
            } else {
                AgQuiet.hideProgress();
                AgShell.noteBatchResult("FAIL", "compress", r.errorMessage);
                AgUi.showError(r.errorCode, r.errorMessage, r.relatedFile);
            }
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
        } else if (kind == 3) {
            if (r.ok() != 0) {
                AgUi.jobLabel.setText("解压完成");
                AgUi.lastOutputPath = destPath;
                AgShell.noteBatchResult("OK", archivePath, destPath);
                if (AgUi.isCurrentJob(epoch) == 0) {
                    return;
                }
                AgQuiet.hideProgress();
                if (AgUi.quietShell != 0) {
                    if (AgShell.countJobs() == 0) {
                        AgQuiet.showSuccessToast("已解压到 " + AgFormat.basename(destPath), destPath);
                    }
                    return;
                }
                if (AgShell.countJobs() == 0) {
                    if (Dialogs.confirm(AgUi.window,
                            "已解压到：\n" + AgFormat.middleEllipsis(destPath, 64) + "\n\n在 Finder 中显示？")) {
                        if (AgUi.isCurrentJob(epoch) != 0) {
                            Desktop.revealFile(destPath);
                        }
                    }
                }
            } else if (r.errorCode == ArchiveErrorCode.PASSWORD
                    || r.errorCode == ArchiveErrorCode.NEED_PASSWORD) {
                AgQuiet.hideProgress();
                AgShell.noteBatchResult("FAIL", archivePath, "password");
                if (AgUi.isCurrentJob(epoch) == 0) {
                    return;
                }
                AgQuiet.ensureUiForDialog();
                if (Dialogs.confirm(AgUi.window, "密码错误。是否重新输入？")) {
                    if (AgUi.isCurrentJob(epoch) != 0) {
                        AgDialogs.promptPasswordThenExtract(archivePath, destPath);
                    }
                }
            } else {
                AgQuiet.hideProgress();
                AgShell.noteBatchResult("FAIL", archivePath, r.errorMessage);
                AgUi.showError(r.errorCode, r.errorMessage, r.relatedFile);
            }
            if (AgUi.isCurrentJob(epoch) == 0) {
                return;
            }
        }
        if (AgUi.isCurrentJob(epoch) != 0) {
            AgShell.maybeShowBatchSummary();
        }
    }
}

class AgJobUi {
    static long jobStartMs;

    static void applyProgress(ArchiveProgress p) {
        if (p == null) {
            return;
        }
        if (p.state == ArchiveTaskState.CANCELLING || p.cancelRequested != 0) {
            AgUi.setJobUi("正在取消… " + AgFormat.basename(p.currentFile), 1, -1.0);
            return;
        }
        String file = AgFormat.basename(p.currentFile);
        if (file == null || file.length() == 0) {
            file = p.currentFile;
        }
        long done = p.bytesIn;
        if (p.bytesOut > p.bytesIn) {
            done = p.bytesOut;
        }
        String msg = p.phase + " · " + file + " · " + AgFormat.bytes(done);
        if (p.percent >= 0) {
            AgUi.setJobUi(msg, 0, (0.0 + p.percent) / 100.0);
        } else {
            AgUi.setJobUi(msg, 1, -1.0);
        }
    }
}

class AgNav {
    static void rebuildView() {
        ArrayList<AgRow> rows = new ArrayList<AgRow>();
        HashMap<String, String> dirs = new HashMap<String, String>();
        String prefix = AgUi.cwd;
        if (prefix == null) {
            prefix = "";
        }
        int i = 0;
        while (i < AgUi.allEntries.size()) {
            ZipEntry e = AgUi.allEntries.get(i);
            String name = e.name;
            if (name == null) {
                i = i + 1;
                continue;
            }
            if (prefix.length() > 0) {
                if (name.length() < prefix.length() || !name.substring(0, prefix.length()).equals(prefix)) {
                    i = i + 1;
                    continue;
                }
            }
            String rest = name.substring(prefix.length(), name.length());
            if (rest.length() == 0) {
                i = i + 1;
                continue;
            }
            int slash = -1;
            int k = 0;
            while (k < rest.length()) {
                if (rest.charAt(k) == 47) {
                    slash = k;
                    break;
                }
                k = k + 1;
            }
            if (slash >= 0) {
                String dirName = rest.substring(0, slash);
                if (!dirs.containsKey(dirName)) {
                    dirs.put(dirName, "1");
                    AgRow row = new AgRow();
                    row.name = dirName;
                    row.fullName = prefix + dirName + "/";
                    row.isDir = 1;
                    row.kind = "文件夹";
                    row.size = -1L;
                    row.compressed = -1L;
                    row.modified = "";
                    row.encrypted = 0;
                    rows.add(row);
                }
            } else {
                if (e.isDirectory()) {
                    i = i + 1;
                    continue;
                }
                AgRow row = new AgRow();
                row.name = rest;
                row.fullName = name;
                row.isDir = 0;
                row.size = e.getSize();
                row.compressed = e.getCompressedSize();
                row.kind = "文件";
                if (e.isEncrypted()) {
                    row.encrypted = 1;
                    row.kind = "加密文件";
                }
                row.modified = "";
                if (e.time > 0L) {
                    row.modified = "" + e.time;
                }
                rows.add(row);
            }
            i = i + 1;
        }
        /* Folders first */
        ArrayList<AgRow> sorted = new ArrayList<AgRow>();
        int a = 0;
        while (a < rows.size()) {
            if (rows.get(a).isDir != 0) {
                sorted.add(rows.get(a));
            }
            a = a + 1;
        }
        a = 0;
        while (a < rows.size()) {
            if (rows.get(a).isDir == 0) {
                sorted.add(rows.get(a));
            }
            a = a + 1;
        }
        AgUi.viewRows = sorted;
        if (sorted.size() == 0) {
            if (AgUi.archivePath == null || AgUi.archivePath.length() == 0) {
                AgUi.table.setEmptyMessage("打开压缩包，或从「新建」开始");
            } else {
                AgUi.table.setEmptyMessage("此目录为空");
            }
        }
        AgUi.table.setItems(sorted);
        AgUi.refreshStatus();
    }
}

class AgDialogs {
    static void showCreate() {
        showCreatePrefill(new ArrayList<String>(), 0);
    }

    /**
     * Prefill sources from Finder/Explorer. forceEncrypt=1 requires password (右键「压缩并设置密码」).
     */
    static void showCreatePrefill(ArrayList<String> prefill, int forceEncrypt) {
        if (AgUi.jobBusy != 0 || AgUi.shellUiBusy != 0) {
            /* Re-queue: caller may have already removed the inbox job. */
            String act = "compress";
            if (forceEncrypt != 0) {
                act = "compress-password";
            }
            String inbox = AgShell.inboxDir();
            Files.createDirectories(FsPath.of(inbox));
            String job = inbox + "/job-" + ("" + ZipNative.nowMs()) + ".job";
            ArrayList<String> paths = prefill;
            if (paths == null) {
                paths = new ArrayList<String>();
            }
            AgShell.writeJobFileList(job, act, paths);
            AgUi.statusLeft.setText("右键任务已排队（等待当前对话框/任务结束）");
            AgUi.jobLabel.setText("已排队");
            return;
        }
        AgUi.shellUiBusy = 1;
        Window dlg = new Window("新建压缩包", 560, 620);
        dlg.setMinSize(480, 480);
        dlg.setOwnerWindow(AgUi.window);

        ArrayList<String> sources = new ArrayList<String>();
        int si = 0;
        while (prefill != null && si < prefill.size()) {
            sources.add(prefill.get(si));
            si = si + 1;
        }

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        VBox form = new VBox();
        form.setSpacing(Theme.SPACE_2);
        form.setPadding(Theme.SPACE_4);

        Label secIn = new Label("输入文件");
        Theme.applyStyle(secIn, Theme.STYLE_TITLE);
        ListView<String> srcList = new ListView<String>();
        srcList.setRenderer(new AgPathRenderer());
        srcList.visibleRows = 4;
        srcList.prefHeight = 96;
        srcList.height = 96;
        srcList.setPrefSize(520, 96);
        srcList.setItems(sources);
        Label srcCount = new Label("尚未添加文件或目录");
        Theme.applyStyle(srcCount, Theme.STYLE_SECONDARY);
        Label srcErr = new Label("");
        Theme.applyStyle(srcErr, Theme.STYLE_ERROR);

        Button addFiles = new Button("添加文件…");
        Button addDir = new Button("添加目录…");
        Button removeSel = new Button("移除所选");
        addFiles.onClick(() -> {
            String p = FileDialog.open(dlg, "");
            if (p != null && p.length() > 0) {
                sources.add(p);
                srcList.setItems(sources);
                AgDialogs.refreshCreateForm();
            }
        });
        addDir.onClick(() -> {
            String p = DirectoryDialog.open(dlg);
            if (p != null && p.length() > 0) {
                sources.add(p);
                srcList.setItems(sources);
                AgDialogs.refreshCreateForm();
            }
        });
        removeSel.onClick(() -> {
            int idx = srcList.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < sources.size()) {
                sources.removeAt(idx);
                srcList.setItems(sources);
                AgDialogs.refreshCreateForm();
            }
        });
        HBox addRow = new HBox();
        addRow.setSpacing(Theme.SPACE_2);
        addRow.add(addFiles);
        addRow.add(addDir);
        addRow.add(removeSel);

        Label secOut = new Label("保存位置");
        Theme.applyStyle(secOut, Theme.STYLE_TITLE);
        String suggested = AgShell.uniqueZipOut(AgShell.defaultZipOut(sources));
        if (suggested == null || suggested.length() == 0) {
            suggested = AgUi.createOutPath;
        }
        TextField nameField = new TextField(AgFormat.basename(suggested));
        nameField.setPrefSize(280, 28);
        nameField.setAccessibleName("文件名");
        TextField dirField = new TextField(AgFormat.parentDir(suggested));
        dirField.setPrefSize(360, 28);
        dirField.setAccessibleName("保存目录");
        Button browseDir = new Button("选择…");
        browseDir.onClick(() -> {
            String p = DirectoryDialog.open(dlg);
            if (p != null && p.length() > 0) {
                dirField.setText(p);
                AgDialogs.refreshCreateForm();
            }
        });
        HBox dirRow = new HBox();
        dirRow.setSpacing(Theme.SPACE_2);
        dirRow.add(dirField);
        dirRow.add(browseDir);
        Label outErr = new Label("");
        Theme.applyStyle(outErr, Theme.STYLE_ERROR);
        Label outHint = new Label("同名压缩包默认另存为新名称，不会覆盖。");
        Theme.applyStyle(outHint, Theme.STYLE_SECONDARY);

        Label secOpt = new Label("压缩选项");
        Theme.applyStyle(secOpt, Theme.STYLE_TITLE);
        ComboBox<String> profile = new ComboBox<String>();
        profile.addItem("快速");
        profile.addItem("标准");
        profile.addItem("高压缩");
        profile.setSelectedIndex(AgUi.createProfileIdx);
        profile.setToolTip("快速优先速度；标准均衡；高压缩更慢，体积不一定更小");
        CheckBox enc = new CheckBox("使用密码加密");
        if (AgUi.createEncrypt != 0 || forceEncrypt != 0) {
            enc.setSelected(true);
        }
        if (forceEncrypt != 0) {
            enc.setEnabled(false);
        }
        Label passLab = new Label("密码");
        Label pass2Lab = new Label("确认密码");
        TextField pass1 = new TextField("");
        TextField pass2 = new TextField("");
        pass1.setPasswordMode(true);
        pass2.setPasswordMode(true);
        pass1.setPrefSize(280, 28);
        pass2.setPrefSize(280, 28);
        pass1.setAccessibleName("密码");
        pass2.setAccessibleName("确认密码");
        Label passErr = new Label("");
        Theme.applyStyle(passErr, Theme.STYLE_ERROR);
        enc.onClick(() -> {
            AgDialogs.syncCreateEncryptVisible();
            AgDialogs.refreshCreateForm();
        });

        form.add(secIn);
        form.add(addRow);
        form.add(srcCount);
        form.add(srcList);
        form.add(srcErr);
        form.add(secOut);
        form.add(new Label("文件名"));
        form.add(nameField);
        form.add(new Label("目录"));
        form.add(dirRow);
        form.add(outHint);
        form.add(outErr);
        form.add(secOpt);
        form.add(new Label("压缩档位"));
        form.add(profile);
        form.add(enc);
        form.add(passLab);
        form.add(pass1);
        form.add(pass2Lab);
        form.add(pass2);
        form.add(passErr);

        Button ok = new Button("创建");
        Button cancel = new Button("取消");
        HBox footer = new HBox();
        footer.setSpacing(Theme.SPACE_2);
        footer.padding = Theme.SPACE_3;
        footer.prefHeight = 52;
        footer.height = 52;
        footer.vgrow = 0;
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.hgrow = 1;
        footer.add(flex);
        footer.add(cancel);
        footer.add(ok);

        /* Scroll form; pin footer so Cancel/Create stay visible at any height. */
        ScrollView scroll = new ScrollView();
        scroll.vgrow = 1;
        scroll.setContent(form);
        root.add(scroll);
        root.add(footer);

        AgUi.createSources = sources;
        AgUi.createOutField = nameField;
        AgUi.createDirField = dirField;
        AgUi.createPass1 = pass1;
        AgUi.createPass2 = pass2;
        AgUi.createEncBox = enc;
        AgUi.createProfileBox = profile;
        AgUi.createErrLabel = passErr;
        AgUi.createSrcErr = srcErr;
        AgUi.createOutErr = outErr;
        AgUi.createSrcCount = srcCount;
        AgUi.createSrcList = srcList;
        AgUi.createPassLab = passLab;
        AgUi.createPass2Lab = pass2Lab;
        AgUi.createOkBtn = ok;
        AgUi.createDlg = dlg;
        AgUi.createForceEncrypt = forceEncrypt;

        ok.onClick(() -> {
            AgDialogs.submitCreate();
        });
        cancel.onClick(() -> {
            pass1.clearSensitive();
            pass2.clearSensitive();
            AgUi.createDlg = null;
            AgUi.shellUiBusy = 0;
            dlg.close();
            AgShell.onJobFinished();
        });
        dlg.onClose(() -> {
            pass1.clearSensitive();
            pass2.clearSensitive();
            if (AgUi.createDlg == dlg) {
                AgUi.createDlg = null;
                AgUi.shellUiBusy = 0;
                AgShell.onJobFinished();
            }
        });

        dlg.setContent(root);
        dlg.setDefaultButton(ok);
        dlg.setCancelButton(cancel);
        dlg.show();
        dlg.centerOnOwner();
        AgDialogs.syncCreateEncryptVisible();
        AgDialogs.refreshCreateForm();
    }

    /** Close idle create dialog so a queued shell job can run (epoch/jobBusy unchanged). */
    static void dismissCreateForQueuedShell() {
        if (AgUi.jobBusy != 0) {
            return;
        }
        Window dlg = AgUi.createDlg;
        if (dlg == null) {
            return;
        }
        AgUi.createDlg = null;
        AgUi.shellUiBusy = 0;
        if (AgUi.createPass1 != null) {
            AgUi.createPass1.clearSensitive();
        }
        if (AgUi.createPass2 != null) {
            AgUi.createPass2.clearSensitive();
        }
        dlg.close();
    }

    static void syncCreateEncryptVisible() {
        int show = 0;
        if (AgUi.createEncBox != null && AgUi.createEncBox.isSelected()) {
            show = 1;
        }
        if (AgUi.createPass1 != null) {
            AgUi.createPass1.setVisible(show != 0);
            AgUi.createPass2.setVisible(show != 0);
        }
        if (AgUi.createPassLab != null) {
            AgUi.createPassLab.setVisible(show != 0);
            AgUi.createPass2Lab.setVisible(show != 0);
        }
        if (show == 0 && AgUi.createPass1 != null) {
            AgUi.createPass1.setText("");
            AgUi.createPass2.setText("");
        }
    }

    static void refreshCreateForm() {
        ArrayList<String> sources = AgUi.createSources;
        if (AgUi.createSrcCount != null) {
            if (sources == null || sources.size() == 0) {
                AgUi.createSrcCount.setText("尚未添加文件或目录");
            } else {
                AgUi.createSrcCount.setText("已选 " + String.valueOf(sources.size()) + " 项");
            }
        }
        if (AgUi.createSrcErr != null) {
            AgUi.createSrcErr.setText("");
        }
        if (AgUi.createOutErr != null) {
            AgUi.createOutErr.setText("");
        }
        if (AgUi.createErrLabel != null) {
            AgUi.createErrLabel.setText("");
        }
        String reason = "";
        if (sources == null || sources.size() == 0) {
            reason = "请添加至少一个文件或目录";
        }
        String name = "";
        String dir = "";
        if (AgUi.createOutField != null) {
            AgUi.createOutField.syncTextFromNative();
            name = AgUi.createOutField.getText();
        }
        if (AgUi.createDirField != null) {
            AgUi.createDirField.syncTextFromNative();
            dir = AgUi.createDirField.getText();
        }
        if (reason.length() == 0 && (name == null || name.length() == 0)) {
            reason = "请填写文件名";
        }
        if (reason.length() == 0 && (dir == null || dir.length() == 0)) {
            reason = "请选择保存目录";
        }
        if (reason.length() == 0 && AgUi.createEncBox != null && AgUi.createEncBox.isSelected()) {
            if (AgUi.createPass1 != null) {
                AgUi.createPass1.syncTextFromNative();
                AgUi.createPass2.syncTextFromNative();
                String a = AgUi.createPass1.getText();
                String b = AgUi.createPass2.getText();
                if (a == null || a.length() == 0) {
                    reason = "请输入密码";
                } else if (b == null || !a.equals(b)) {
                    reason = "两次密码不一致";
                }
            }
        }
        if (AgUi.createOkBtn != null) {
            AgUi.createOkBtn.setEnabled(reason.length() == 0);
            if (reason.length() > 0) {
                AgUi.createOkBtn.setToolTip(reason);
            } else {
                AgUi.createOkBtn.setToolTip("创建压缩包");
            }
        }
        if (reason.length() > 0 && AgUi.createErrLabel != null
                && AgUi.createEncBox != null && AgUi.createEncBox.isSelected()
                && reason.indexOf("密码") >= 0) {
            AgUi.createErrLabel.setText(reason);
        } else if (reason.length() > 0 && AgUi.createSrcErr != null
                && reason.indexOf("文件") >= 0) {
            AgUi.createSrcErr.setText(reason);
        } else if (reason.length() > 0 && AgUi.createOutErr != null) {
            AgUi.createOutErr.setText(reason);
        }
    }

    static String shellExtractArchive;
    static String shellExtractDest;

    /** Compact password + confirm for Finder「加密压缩」— no full create form. */
    static void promptPasswordThenCompress(ArrayList<String> paths, String out) {
        AgQuiet.ensureUiForDialog();
        AgUi.shellUiBusy = 1;
        /* Tall enough for title + 2 fields + error + pinned footer (buttons were clipped at 260). */
        Window dlg = new Window("加密压缩", 440, 360);
        dlg.setMinSize(400, 320);
        dlg.setOwnerWindow(AgUi.window);

        VBox form = new VBox();
        form.setSpacing(Theme.SPACE_3);
        form.setPadding(Theme.SPACE_4);
        Label title = new Label("设置压缩包密码");
        Theme.applyStyle(title, Theme.STYLE_TITLE);
        Label destLab = new Label(AgFormat.basename(out));
        Theme.applyStyle(destLab, Theme.STYLE_SECONDARY);
        TextField pass1 = new TextField("");
        TextField pass2 = new TextField("");
        pass1.setPasswordMode(true);
        pass2.setPasswordMode(true);
        pass1.setPrefSize(360, 28);
        pass2.setPrefSize(360, 28);
        pass1.setAccessibleName("密码");
        pass2.setAccessibleName("确认密码");
        Label err = new Label(" ");
        Theme.applyStyle(err, Theme.STYLE_ERROR);
        form.add(title);
        form.add(destLab);
        form.add(new Label("密码"));
        form.add(pass1);
        form.add(new Label("确认密码"));
        form.add(pass2);
        form.add(err);

        Button ok = new Button("开始压缩");
        Button cancel = new Button("取消");
        ok.onClick(() -> {
            pass1.syncTextFromNative();
            pass2.syncTextFromNative();
            String a = pass1.getText();
            String b = pass2.getText();
            if (a == null || a.length() == 0) {
                err.setText("请输入密码");
                return;
            }
            if (b == null || !a.equals(b)) {
                err.setText("两次密码不一致");
                return;
            }
            pass1.clearSensitive();
            pass2.clearSensitive();
            AgUi.shellUiBusy = 0;
            dlg.close();
            AgShell.startQuickCompress(paths, out, a);
        });
        cancel.onClick(() -> {
            pass1.clearSensitive();
            pass2.clearSensitive();
            AgUi.shellUiBusy = 0;
            dlg.close();
            AgShell.onJobFinished();
        });
        dlg.onClose(() -> {
            pass1.clearSensitive();
            pass2.clearSensitive();
            if (AgUi.shellUiBusy != 0) {
                AgUi.shellUiBusy = 0;
                AgShell.onJobFinished();
            }
        });

        HBox footer = new HBox();
        footer.setSpacing(Theme.SPACE_2);
        footer.padding = Theme.SPACE_3;
        footer.prefHeight = 52;
        footer.height = 52;
        footer.vgrow = 0;
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.hgrow = 1;
        footer.add(flex);
        footer.add(cancel);
        footer.add(ok);

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;
        form.vgrow = 1;
        root.add(form);
        root.add(footer);
        dlg.setContent(root);
        dlg.setDefaultButton(ok);
        dlg.setCancelButton(cancel);
        dlg.show();
        dlg.centerOnOwner();
    }

    /** Async password then extract (UI thread). */
    static void promptPasswordThenExtract(String archive, String dest) {
        shellExtractArchive = archive;
        shellExtractDest = dest;
        AgQuiet.ensureUiForDialog();
        AgUi.shellUiBusy = 1;
        Window dlg = new Window("需要密码", 440, 240);
        dlg.setMinSize(400, 220);
        dlg.setOwnerWindow(AgUi.window);
        VBox root = new VBox();
        root.setSpacing(Theme.SPACE_3);
        root.setPadding(Theme.SPACE_4);
        Label title = new Label(AgFormat.basename(archive));
        Theme.applyStyle(title, Theme.STYLE_TITLE);
        Label pathLab = new Label(AgFormat.middleEllipsis(archive, 56));
        Theme.applyStyle(pathLab, Theme.STYLE_SECONDARY);
        pathLab.setToolTip(archive);
        TextField pass = new TextField("");
        pass.setPasswordMode(true);
        pass.setPrefSize(360, 28);
        pass.setAccessibleName("密码");
        Label err = new Label("");
        Theme.applyStyle(err, Theme.STYLE_ERROR);
        Button ok = new Button("解压");
        Button cancel = new Button("取消");
        ok.onClick(() -> {
            pass.syncTextFromNative();
            String pw = pass.getText();
            if (pw == null || pw.length() == 0) {
                err.setText("请输入密码");
                return;
            }
            String a = shellExtractArchive;
            String d = shellExtractDest;
            pass.clearSensitive();
            shellExtractArchive = null;
            dlg.close();
            AgUi.shellUiBusy = 0;
            AgShell.startExtractWithPassword(a, d, pw);
        });
        cancel.onClick(() -> {
            pass.clearSensitive();
            shellExtractArchive = null;
            AgUi.shellUiBusy = 0;
            AgUi.jobLabel.setText("已取消");
            dlg.close();
            AgShell.noteBatchResult("CANCEL", archive, "password-cancel");
            AgShell.onJobFinished();
        });
        dlg.onClose(() -> {
            pass.clearSensitive();
            if (shellExtractArchive != null) {
                shellExtractArchive = null;
                AgUi.shellUiBusy = 0;
                AgUi.jobLabel.setText("已取消");
                AgShell.onJobFinished();
            }
        });
        HBox row = new HBox();
        row.setSpacing(Theme.SPACE_2);
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.hgrow = 1;
        row.add(flex);
        row.add(cancel);
        row.add(ok);
        root.add(title);
        root.add(pathLab);
        root.add(new Label("密码"));
        root.add(pass);
        root.add(err);
        root.add(row);
        dlg.setContent(root);
        dlg.setDefaultButton(ok);
        dlg.setCancelButton(cancel);
        dlg.show();
        dlg.centerOnOwner();
    }

    static void submitCreate() {
        AgDialogs.refreshCreateForm();
        TextField nameField = AgUi.createOutField;
        TextField dirField = AgUi.createDirField;
        TextField pass1 = AgUi.createPass1;
        TextField pass2 = AgUi.createPass2;
        CheckBox enc = AgUi.createEncBox;
        ComboBox<String> profile = AgUi.createProfileBox;
        ArrayList<String> sources = AgUi.createSources;
        Window dlg = AgUi.createDlg;
        nameField.syncTextFromNative();
        dirField.syncTextFromNative();
        pass1.syncTextFromNative();
        pass2.syncTextFromNative();
        String name = nameField.getText();
        String dir = dirField.getText();
        if (name == null || name.length() == 0 || dir == null || dir.length() == 0) {
            return;
        }
        if (!name.toLowerCase().endsWith(".zip")) {
            name = name + ".zip";
        }
        String out = FsPath.of(dir).resolve(name).text;
        if (sources.size() == 0) {
            return;
        }
        int si = 0;
        while (si < sources.size()) {
            if (!Files.exists(FsPath.of(sources.get(si)))) {
                if (AgUi.createSrcErr != null) {
                    AgUi.createSrcErr.setText("输入已不存在: " + AgFormat.basename(sources.get(si)));
                }
                return;
            }
            si = si + 1;
        }
        if (Files.exists(FsPath.of(out))) {
            out = AgShell.uniqueZipOut(out);
            nameField.setText(AgFormat.basename(out));
            dirField.setText(AgFormat.parentDir(out));
            if (AgUi.createOutErr != null) {
                AgUi.createOutErr.setText("目标已存在，已改为 " + AgFormat.basename(out));
            }
            return;
        }
        String password = null;
        if (enc.isSelected()) {
            String a = pass1.getText();
            String b = pass2.getText();
            if (a == null || a.length() == 0 || b == null || !a.equals(b)) {
                return;
            }
            password = a;
        }
        AgUi.createOutPath = out;
        AgUi.createProfileIdx = profile.getSelectedIndex();
        if (enc.isSelected()) {
            AgUi.createEncrypt = 1;
        } else {
            AgUi.createEncrypt = 0;
        }
        String[] srcArr = new String[sources.size()];
        int i = 0;
        while (i < sources.size()) {
            srcArr[i] = sources.get(i);
            i = i + 1;
        }
        ArchiveCreateOptions opt = new ArchiveCreateOptions();
        int pi = profile.getSelectedIndex();
        if (pi == 0) {
            opt.profile = ArchiveCompressProfile.FAST;
        } else if (pi == 2) {
            opt.profile = ArchiveCompressProfile.MAX;
        } else {
            opt.profile = ArchiveCompressProfile.STANDARD;
        }
        if (password != null) {
            opt.encrypt = 1;
            opt.password = password;
            opt.aesStrength = 3;
        }
        pass1.clearSensitive();
        pass2.clearSensitive();
        AgUi.createDlg = null;
        dlg.close();
        runCreate(out, srcArr, opt);
        AgUi.shellUiBusy = 0;
    }

    static void runCreate(String out, String[] sources, ArchiveCreateOptions opt) {
        AgJobCtx.kind = 2;
        AgJobCtx.phase = "压缩";
        AgJobCtx.outPath = out;
        AgJobCtx.sources = sources;
        AgJobCtx.createOpt = opt;
        AgActions.startJob();
    }

    static void showExtract() {
        if (AgUi.archivePath == null || AgUi.archivePath.length() == 0) {
            Dialogs.info(AgUi.window, "请先打开压缩包");
            return;
        }
        if (AgUi.jobBusy != 0) {
            return;
        }
        Window dlg = new Window("解压", 500, 380);
        dlg.setMinSize(440, 320);
        dlg.setOwnerWindow(AgUi.window);
        VBox root = new VBox();
        root.setSpacing(Theme.SPACE_3);
        root.setPadding(Theme.SPACE_4);
        Label title = new Label("解压到…");
        Theme.applyStyle(title, Theme.STYLE_TITLE);

        TextField destField = new TextField("");
        destField.setPrefSize(340, 28);
        Button browse = new Button("选择…");
        browse.onClick(() -> {
            String p = DirectoryDialog.open(dlg);
            if (p != null) {
                destField.setText(p);
            }
        });
        HBox destRow = new HBox();
        destRow.setSpacing(Theme.SPACE_2);
        destRow.add(destField);
        destRow.add(browse);

        CheckBox onlySel = new CheckBox("仅解压选中条目");
        AgRow sel = AgUi.table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            onlySel.setEnabled(false);
        }

        ComboBox<String> conflict = new ComboBox<String>();
        conflict.addItem("询问");
        conflict.addItem("替换");
        conflict.addItem("跳过");
        conflict.addItem("失败");
        conflict.addItem("保留两者");
        conflict.setSelectedIndex(0);

        TextField pass = new TextField("");
        pass.setPasswordMode(true);
        pass.setPrefSize(280, 28);
        if (AgUi.encryptedMeta == 0) {
            pass.setEnabled(false);
        }

        Label err = new Label("");
        Theme.applyStyle(err, Theme.STYLE_ERROR);
        Button ok = new Button("解压");
        Button cancel = new Button("取消");
        AgUi.extractDestField = destField;
        AgUi.extractPassField = pass;
        AgUi.extractConflictBox = conflict;
        AgUi.extractOnlySel = onlySel;
        AgUi.extractErrLabel = err;
        AgUi.extractDlg = dlg;
        AgUi.extractSelRow = sel;
        ok.onClick(() -> {
            AgDialogs.submitExtract();
        });
        cancel.onClick(() -> {
            pass.clearSensitive();
            dlg.close();
        });
        dlg.onClose(() -> {
            pass.clearSensitive();
        });

        HBox btnRow = new HBox();
        btnRow.setSpacing(Theme.SPACE_2);
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.hgrow = 1;
        btnRow.add(flex);
        btnRow.add(cancel);
        btnRow.add(ok);
        root.add(title);
        root.add(new Label("目标目录"));
        root.add(destRow);
        root.add(onlySel);
        root.add(new Label("同名文件"));
        root.add(conflict);
        root.add(new Label("密码"));
        root.add(pass);
        root.add(err);
        root.add(btnRow);
        dlg.setContent(root);
        dlg.setDefaultButton(ok);
        dlg.setCancelButton(cancel);
        dlg.show();
        dlg.centerOnOwner();
    }

    static ArrayList<String> collectSelectedEntries(AgRow sel) {
        ArrayList<String> list = new ArrayList<String>();
        if (sel == null) {
            return list;
        }
        if (sel.isDir == 0) {
            list.add(sel.fullName);
            return list;
        }
        String pref = sel.fullName;
        int i = 0;
        while (i < AgUi.allEntries.size()) {
            String n = AgUi.allEntries.get(i).name;
            if (n != null && n.length() >= pref.length()
                    && n.substring(0, pref.length()).equals(pref)) {
                list.add(n);
            }
            i = i + 1;
        }
        return list;
    }

    static void runExtract(String dest, ArchiveExtractOptions opt) {
        runExtract(AgUi.archivePath, dest, opt);
    }

    static void runExtract(String archive, String dest, ArchiveExtractOptions opt) {
        AgJobCtx.kind = 3;
        AgJobCtx.phase = "解压";
        AgJobCtx.archivePath = archive;
        AgJobCtx.destPath = dest;
        AgJobCtx.extractOpt = opt;
        AgActions.startJob();
    }

    static void submitExtract() {
        TextField destField = AgUi.extractDestField;
        TextField pass = AgUi.extractPassField;
        ComboBox<String> conflict = AgUi.extractConflictBox;
        CheckBox onlySel = AgUi.extractOnlySel;
        Label err = AgUi.extractErrLabel;
        Window dlg = AgUi.extractDlg;
        AgRow sel = AgUi.extractSelRow;
        destField.syncTextFromNative();
        pass.syncTextFromNative();
        String dest = destField.getText();
        if (dest == null || dest.length() == 0) {
            err.setText("请选择目标目录");
            return;
        }
        ArchiveExtractOptions opt = new ArchiveExtractOptions();
        int ci = conflict.getSelectedIndex();
        if (ci == 0) {
            opt.conflictPolicy = ArchiveOverwrite.ASK;
            opt.conflictHandler = new AgGuiConflictHandler();
        } else if (ci == 1) {
            opt.conflictPolicy = ArchiveOverwrite.REPLACE;
        } else if (ci == 2) {
            opt.conflictPolicy = ArchiveOverwrite.SKIP;
        } else if (ci == 3) {
            opt.conflictPolicy = ArchiveOverwrite.FAIL;
        } else {
            opt.conflictPolicy = ArchiveOverwrite.RENAME;
        }
        if (AgUi.encryptedMeta != 0) {
            if (pass.getText() == null || pass.getText().length() == 0) {
                err.setText("加密包需要密码");
                return;
            }
            opt.password = pass.getText();
        }
        if (onlySel.isSelected() && sel != null) {
            opt.selectedEntries = collectSelectedEntries(sel);
        }
        AgUi.conflictApplyAll = -1;
        pass.clearSensitive();
        dlg.close();
        runExtract(dest, opt);
    }

    static void submitTestPassword() {
        TextField pass = AgUi.testPassField;
        Label err = AgUi.testPassErr;
        Window dlg = AgUi.testPassDlg;
        pass.syncTextFromNative();
        String pw = pass.getText();
        if (pw == null || pw.length() == 0) {
            err.setText("请输入密码");
            return;
        }
        pass.clearSensitive();
        dlg.close();
        AgActions.runTest(pw);
    }

    static void showTestPassword() {
        Window dlg = new Window("校验加密压缩包", 360, 180);
        VBox root = new VBox();
        root.setSpacing(8);
        root.setPadding(16);
        TextField pass = new TextField("");
        pass.setPasswordMode(true);
        pass.setPrefSize(280, 28);
        Label err = new Label("");
        Button ok = new Button("校验");
        Button cancel = new Button("取消");
        AgUi.testPassField = pass;
        AgUi.testPassErr = err;
        AgUi.testPassDlg = dlg;
        ok.onClick(() -> {
            AgDialogs.submitTestPassword();
        });
        cancel.onClick(() -> {
            pass.clearSensitive();
            dlg.close();
        });
        dlg.onClose(() -> {
            pass.clearSensitive();
        });
        HBox row = new HBox();
        row.setSpacing(8);
        row.add(ok);
        row.add(cancel);
        root.add(new Label("读取内容前需要密码（浏览元数据不需要）"));
        root.add(pass);
        root.add(err);
        root.add(row);
        dlg.setContent(root);
        dlg.setDefaultButton(ok);
        dlg.setCancelButton(cancel);
        dlg.show();
        dlg.centerOnOwner();
    }

    static void showConflictUi() {
        ArchiveConflictRequest req = AgUi.conflictReq;
        if (req == null) {
            AgUi.conflictDecision = new ArchiveConflictDecision(ArchiveOverwrite.FAIL, 0);
            AgUi.conflictReady = 1;
            return;
        }
        AgQuiet.ensureUiForDialog();
        AgUi.shellUiBusy = 1;
        Window dlg = new Window("已存在同名文件", 520, 320);
        dlg.setMinSize(460, 280);
        dlg.setOwnerWindow(AgUi.window);
        VBox root = new VBox();
        root.setSpacing(Theme.SPACE_3);
        root.setPadding(Theme.SPACE_4);

        Label title = new Label(req.entryName);
        Theme.applyStyle(title, Theme.STYLE_TITLE);
        Label existingLab = new Label("已有文件");
        Theme.applyStyle(existingLab, Theme.STYLE_SECONDARY);
        Label existingPath = new Label(AgFormat.middleEllipsis(req.destPath, 58));
        existingPath.setToolTip(req.destPath);
        Label incomingLab = new Label("待解压条目");
        Theme.applyStyle(incomingLab, Theme.STYLE_SECONDARY);
        Label incomingName = new Label(req.entryName);
        Button copyPath = new Button("复制完整路径");
        copyPath.setChromeStyle(1);
        copyPath.onClick(() -> {
            Clipboard.setText(req.destPath);
        });
        CheckBox applyAll = new CheckBox("应用于其余冲突（仅本任务）");

        Button keepBoth = new Button("保留两者");
        Button skip = new Button("跳过");
        Button replace = new Button("替换");
        Button cancel = new Button("取消本次解压");
        keepBoth.setToolTip("重命名后解压，保留已有文件");
        skip.setToolTip("跳过此文件，继续解压其余条目");
        replace.setToolTip("用压缩包中的文件覆盖已有文件");
        keepBoth.onClick(() -> {
            finishConflict(ArchiveOverwrite.RENAME, applyAll.isSelected());
            dlg.close();
        });
        skip.onClick(() -> {
            finishConflict(ArchiveOverwrite.SKIP, applyAll.isSelected());
            dlg.close();
        });
        replace.onClick(() -> {
            finishConflict(ArchiveOverwrite.REPLACE, applyAll.isSelected());
            dlg.close();
        });
        cancel.onClick(() -> {
            finishConflict(ArchiveOverwrite.FAIL, false);
            if (AgUi.jobTask != null) {
                AgUi.jobTask.requestCancel();
            }
            dlg.close();
        });
        dlg.onClose(() -> {
            if (AgUi.conflictReady == 0) {
                finishConflict(ArchiveOverwrite.FAIL, false);
            }
        });

        HBox row = new HBox();
        row.setSpacing(Theme.SPACE_2);
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.hgrow = 1;
        row.add(flex);
        row.add(keepBoth);
        row.add(skip);
        row.add(replace);
        row.add(cancel);

        root.add(title);
        root.add(existingLab);
        root.add(existingPath);
        root.add(incomingLab);
        root.add(incomingName);
        root.add(copyPath);
        root.add(applyAll);
        root.add(row);
        dlg.setContent(root);
        /* Safe default: Skip — not Replace. */
        dlg.setDefaultButton(skip);
        dlg.setCancelButton(cancel);
        dlg.show();
        dlg.centerOnOwner();
    }

    static void finishConflict(int action, boolean all) {
        AgUi.conflictDecision = new ArchiveConflictDecision(action, 0);
        if (all) {
            AgUi.conflictApplyAll = action;
            AgUi.conflictDecision.applyToRest = 1;
        }
        AgUi.conflictReady = 1;
        AgUi.shellUiBusy = 0;
    }
}

class AgGuiConflictHandler extends ArchiveConflictHandler {
    public ArchiveConflictDecision onConflict(ArchiveConflictRequest req) {
        if (AgUi.conflictApplyAll >= 0) {
            return new ArchiveConflictDecision(AgUi.conflictApplyAll, 0);
        }
        AgUi.conflictReq = req;
        AgUi.conflictReady = 0;
        AgUi.conflictDecision = new ArchiveConflictDecision(ArchiveOverwrite.FAIL, 0);
        Application.invokeLater(() -> {
            AgDialogs.showConflictUi();
        });
        int spins = 0;
        while (AgUi.conflictReady == 0 && AgUi.alive != 0 && spins < 6000) {
            try {
                Thread.sleep(50);
            } catch (Exception e) {
            }
            spins = spins + 1;
            if (AgUi.jobTask != null && AgUi.jobTask.cancelRequested != 0) {
                return new ArchiveConflictDecision(ArchiveOverwrite.FAIL, 0);
            }
        }
        if (AgUi.conflictReady == 0) {
            return new ArchiveConflictDecision(ArchiveOverwrite.FAIL, 0);
        }
        return AgUi.conflictDecision;
    }
}

/**
 * Quiet Finder UX: progress float after ~1s, non-blocking success toast,
 * show main window only when a dialog is required.
 */
class AgQuiet {
    /** Password / conflict dialogs are their own windows — do not flash the browser. */
    static void ensureUiForDialog() {
    }

    /** Failures must be visible: show main window without replacing the open archive. */
    static void revealForError() {
        if (AgUi.window == null || AgUi.window.disposed != 0) {
            return;
        }
        AgUi.window.show();
        AgUi.mainShown = 1;
        AgUi.window.activate();
    }

    static void onJobUi(String text, int indeterminate, double pct) {
        if (AgUi.quietShell == 0 || AgUi.jobBusy == 0) {
            return;
        }
        long now = ZipNative.nowMs();
        if (AgUi.quietJobStartMs <= 0L) {
            AgUi.quietJobStartMs = now;
        }
        if (now - AgUi.quietJobStartMs < 1000L) {
            return;
        }
        ensureProgressFloat();
        if (AgUi.progressFloatLabel != null) {
            AgUi.progressFloatLabel.setText(AgFormat.middleEllipsis(text, 48));
        }
        if (AgUi.progressFloatBar != null) {
            if (indeterminate != 0) {
                AgUi.progressFloatBar.setIndeterminate(true);
            } else if (pct >= 0.0) {
                AgUi.progressFloatBar.setIndeterminate(false);
                AgUi.progressFloatBar.setProgress(pct);
            }
        }
    }

    static void ensureProgressFloat() {
        if (AgUi.progressFloat != null && AgUi.progressFloat.disposed == 0) {
            AgUi.progressFloat.show();
            return;
        }
        Window w = new Window("JNative Archive", 360, 120);
        w.setMinSize(320, 100);
        VBox root = new VBox();
        root.setSpacing(Theme.SPACE_2);
        root.setPadding(Theme.SPACE_3);
        Label lab = new Label("处理中…");
        ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        bar.setPrefSize(320, 12);
        Button cancel = new Button("取消");
        cancel.onClick(() -> {
            if (AgUi.jobTask != null) {
                AgUi.jobTask.requestCancel();
            }
        });
        HBox row = new HBox();
        row.setSpacing(Theme.SPACE_2);
        Label flex = new Label("");
        flex.hgrow = 1;
        row.add(flex);
        row.add(cancel);
        root.add(lab);
        root.add(bar);
        root.add(row);
        w.setContent(root);
        w.setCancelButton(cancel);
        AgUi.progressFloat = w;
        AgUi.progressFloatLabel = lab;
        AgUi.progressFloatBar = bar;
        AgUi.progressFloatCancel = cancel;
        w.show();
    }

    static void hideProgress() {
        Window w = AgUi.progressFloat;
        if (w != null && w.disposed == 0) {
            w.close();
        }
        AgUi.progressFloat = null;
        AgUi.progressFloatLabel = null;
        AgUi.progressFloatBar = null;
        AgUi.progressFloatCancel = null;
    }

    static void showSuccessToast(String message, String revealPath) {
        hideProgress();
        if (AgUi.toastWindow != null && AgUi.toastWindow.disposed == 0) {
            AgUi.toastWindow.close();
        }
        Window w = new Window("JNative Archive", 380, 110);
        w.setMinSize(340, 100);
        VBox root = new VBox();
        root.setSpacing(Theme.SPACE_2);
        root.setPadding(Theme.SPACE_3);
        Label lab = new Label(message);
        Button show = new Button("显示文件");
        Button close = new Button("关闭");
        show.onClick(() -> {
            if (revealPath != null && revealPath.length() > 0) {
                Desktop.revealFile(revealPath);
            }
            w.close();
        });
        close.onClick(() -> {
            w.close();
        });
        HBox row = new HBox();
        row.setSpacing(Theme.SPACE_2);
        Label flex = new Label("");
        flex.hgrow = 1;
        row.add(flex);
        row.add(show);
        row.add(close);
        root.add(lab);
        root.add(row);
        w.setContent(root);
        w.setDefaultButton(show);
        w.setCancelButton(close);
        AgUi.toastWindow = w;
        w.show();
        /* Auto-dismiss after a few seconds if still open. */
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(4500);
            } catch (Exception e) {
            }
            Application.invokeLater(() -> {
                if (AgUi.toastWindow == w && w.disposed == 0) {
                    w.close();
                    AgUi.toastWindow = null;
                }
            });
        });
        t.start();
    }
}
