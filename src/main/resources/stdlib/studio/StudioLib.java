/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Studio application library (0.21) — ordinary JNative app code.
// Depends on: collections, ui, graphics, editor, json, io (StdlibIncluder).

/**
 * @author 张晓达
 */
class ProjectNodeKind {
    static int PROJECT = 1;
    static int DIRECTORY = 2;
    static int SOURCE_FILE = 3;
    static int OTHER_FILE = 4;
}

class ProjectNode {
    int kind;
    String name;
    String path;
    ArrayList<ProjectNode> children;
    int childrenLoaded;

    ProjectNode(int k, String n, String p) {
        kind = k;
        name = n;
        path = p;
        children = new ArrayList<ProjectNode>();
        childrenLoaded = 0;
    }

    void addChild(ProjectNode c) {
        children.add(c);
    }
}

class Project {
    String name;
    String rootDirectory;
    ArrayList<String> sourceRoots;
    String outputDirectory;
    String mainClass;
    int optLevel;

    Project() {
        name = "Untitled";
        rootDirectory = ".";
        sourceRoots = new ArrayList<String>();
        sourceRoots.add("src");
        outputDirectory = "build";
        mainClass = "Main";
        optLevel = 0;
    }

    static Project load(String projectFileOrDir) {
        Project p = new Project();
        String root = projectFileOrDir;
        String file = projectFileOrDir;
        if (FsNative.isDirectory(projectFileOrDir) != 0) {
            root = projectFileOrDir;
            file = root + "/jnative.project";
        } else {
            FsPath fp = FsPath.of(projectFileOrDir);
            root = fp.getParent().toString();
            file = projectFileOrDir;
        }
        p.rootDirectory = root;
        if (File.exists(file)) {
            String text = File.readString(file);
            JsonValue v = JsonParser.parse(text);
            if (v.isObject()) {
                JsonObject o = v.asObject();
                p.name = o.getString("name", p.name);
                p.outputDirectory = o.getString("output", p.outputDirectory);
                p.mainClass = o.getString("main", p.mainClass);
                JsonValue src = o.get("source");
                if (src.isArray()) {
                    p.sourceRoots = new ArrayList<String>();
                    JsonArray a = src.asArray();
                    int i = 0;
                    while (i < a.size()) {
                        if (a.get(i).isString()) {
                            p.sourceRoots.add(a.get(i).asString());
                        }
                        i = i + 1;
                    }
                }
            }
        }
        return p;
    }

    void save() {
        JsonObject o = new JsonObject();
        o.putString("name", name);
        o.putString("output", outputDirectory);
        o.putString("main", mainClass);
        JsonArray src = new JsonArray();
        int i = 0;
        while (i < sourceRoots.size()) {
            src.add(new JsonString(sourceRoots.get(i)));
            i = i + 1;
        }
        o.put("source", src);
        String path = rootDirectory + "/jnative.project";
        File.writeString(path, JsonWriter.write(o));
    }

    String outputBinaryPath() {
        return rootDirectory + "/" + outputDirectory + "/" + mainClass;
    }
}

class EditorTab {
    String path;
    TextDocument document;
    CodeEditor editor;
    String title;

    EditorTab(String p, TextDocument doc, CodeEditor ed) {
        path = p;
        document = doc;
        editor = ed;
        title = FsPath.of(p).getName();
    }

    boolean isDirty() {
        return document.isDirty();
    }

    String displayTitle() {
        if (isDirty()) {
            return title + " *";
        }
        return title;
    }

    void save() {
        document.saveAs(path);
    }
}

class ConsoleBuffer {
    String text;
    int maxBytes;

    ConsoleBuffer(int max) {
        text = "";
        maxBytes = max;
    }

    void append(String s) {
        if (s == null) {
            return;
        }
        text = text + s;
        if (text.utf8Length() > maxBytes) {
            // Approximate trim by UTF-16 units when over UTF-8 byte budget.
            while (text.utf8Length() > maxBytes && text.length() > 0) {
                text = text.substring(1, text.length());
            }
        }
    }

    void clear() {
        text = "";
    }

    String getText() {
        return text;
    }
}

class StudioDiagnostic {
    int severity;
    String message;
    String file;
    int line;
    int column;

    StudioDiagnostic(int sev, String msg, String f, int ln, int col) {
        severity = sev;
        message = msg;
        file = f;
        line = ln;
        column = col;
    }
}

class BuildState {
    static int IDLE = 0;
    static int BUILDING = 1;
    static int SUCCESS = 2;
    static int FAILED = 3;
    static int CANCELLED = 4;
}

class RunConfiguration {
    String name;
    String mainClass;
    String arguments;
    String workingDirectory;

    RunConfiguration() {
        name = "Default";
        mainClass = "Main";
        arguments = "";
        workingDirectory = "";
    }
}

class SearchMatch {
    String path;
    int line;
    int column;
    String preview;

    SearchMatch(String p, int ln, int col, String prev) {
        path = p;
        line = ln;
        column = col;
        preview = prev;
    }
}

class CompletionItem {
    String label;
    String insertText;
    int kind;
    String detail;

    static int KEYWORD = 1;
    static int CLASS = 2;
    static int METHOD = 3;
    static int FIELD = 4;
    static int VARIABLE = 5;

    CompletionItem(String lab, String ins, int k, String det) {
        label = lab;
        insertText = ins;
        kind = k;
        detail = det;
    }
}

class StudioCommand {
    String id;
    String title;

    StudioCommand(String i, String t) {
        id = i;
        title = t;
    }
}

class StudioCommandRegistry {
    ArrayList<StudioCommand> commands;

    StudioCommandRegistry() {
        commands = new ArrayList<StudioCommand>();
        commands.add(new StudioCommand("File.OpenProject", "Open Project"));
        commands.add(new StudioCommand("File.Save", "Save"));
        commands.add(new StudioCommand("File.SaveAll", "Save All"));
        commands.add(new StudioCommand("Build.Project", "Build"));
        commands.add(new StudioCommand("Run.Project", "Run"));
        commands.add(new StudioCommand("Search.Files", "Find in Files"));
        commands.add(new StudioCommand("Navigate.Definition", "Go to Definition"));
    }
}

class StudioSettings {
    ArrayList<String> recentProjects;
    int windowWidth;
    int windowHeight;
    String theme;
    String compilerJar;

    StudioSettings() {
        recentProjects = new ArrayList<String>();
        windowWidth = 1200;
        windowHeight = 800;
        theme = "dark";
        compilerJar = "target/jnative.jar";
    }

    static StudioSettings load() {
        StudioSettings s = new StudioSettings();
        FsPath dir = Files.configDir("JNativeStudio");
        FsPath file = dir.resolve("settings.json");
        if (Files.exists(file)) {
            JsonValue v = JsonParser.parse(Files.readString(file));
            if (v.isObject()) {
                JsonObject o = v.asObject();
                s.theme = o.getString("theme", s.theme);
                s.compilerJar = o.getString("compilerJar", s.compilerJar);
            }
        }
        return s;
    }

    void save() {
        FsPath dir = Files.configDir("JNativeStudio");
        Files.createDirectories(dir);
        JsonObject o = new JsonObject();
        o.putString("theme", theme);
        o.putString("compilerJar", compilerJar);
        Files.writeString(dir.resolve("settings.json"), JsonWriter.write(o));
    }
}

class StudioSymbol {
    String name;
    String kind;
    String file;
    int line;
    int column;

    StudioSymbol(String n, String k, String f, int ln, int col) {
        name = n;
        kind = k;
        file = f;
        line = ln;
        column = col;
    }
}

class CompletionService {
    WorkspaceIndex index;

    CompletionService(WorkspaceIndex idx) {
        index = idx;
    }

    ArrayList<CompletionItem> complete(String prefix) {
        ArrayList<CompletionItem> items = new ArrayList<CompletionItem>();
        items.add(new CompletionItem("class", "class ", CompletionItem.KEYWORD, "keyword"));
        items.add(new CompletionItem("interface", "interface ", CompletionItem.KEYWORD, "keyword"));
        items.add(new CompletionItem("public", "public ", CompletionItem.KEYWORD, "keyword"));
        if (index != null) {
            ArrayList<StudioSymbol> hits = index.findByName(prefix);
            int i = 0;
            while (i < hits.size()) {
                StudioSymbol s = hits.get(i);
                int kind = CompletionItem.CLASS;
                if (s.kind.equals("method")) {
                    kind = CompletionItem.METHOD;
                } else if (s.kind.equals("field")) {
                    kind = CompletionItem.FIELD;
                }
                items.add(new CompletionItem(s.name, s.name, kind, s.kind));
                i = i + 1;
            }
        }
        return items;
    }
}

class WorkspaceIndex {
    ArrayList<StudioSymbol> symbols;
    int version;

    WorkspaceIndex() {
        symbols = new ArrayList<StudioSymbol>();
        version = 0;
    }

    void clear() {
        symbols = new ArrayList<StudioSymbol>();
    }

    void add(StudioSymbol s) {
        symbols.add(s);
    }

    ArrayList<StudioSymbol> findByName(String q) {
        ArrayList<StudioSymbol> out = new ArrayList<StudioSymbol>();
        int i = 0;
        while (i < symbols.size()) {
            StudioSymbol s = symbols.get(i);
            if (s.name.indexOf(q) >= 0) {
                out.add(s);
            }
            i = i + 1;
        }
        return out;
    }
}

class StudioApplication {
    Window window;
    SplitPane rootSplit;
    SplitPane centerSplit;
    TreeView<ProjectNode> explorer;
    TabPane editorTabs;
    TabPane bottomTabs;
    Label status;
    Label problemsLabel;
    Label consoleLabel;
    Project project;
    ArrayList<EditorTab> tabs;
    ConsoleBuffer console;
    ArrayList<StudioDiagnostic> problems;
    int buildState;
    StudioSettings settings;
    StudioCommandRegistry commands;
    WorkspaceIndex index;
    RunConfiguration runConfig;
    Process runningProcess;
    String compilerJar;
    CancellationToken buildToken;
    ProjectNode treeRoot;
    int analysisVersion;

    StudioApplication() {
        tabs = new ArrayList<EditorTab>();
        console = new ConsoleBuffer(10 * 1024 * 1024);
        problems = new ArrayList<StudioDiagnostic>();
        buildState = BuildState.IDLE;
        settings = StudioSettings.load();
        commands = new StudioCommandRegistry();
        index = new WorkspaceIndex();
        runConfig = new RunConfiguration();
        runningProcess = null;
        compilerJar = settings.compilerJar;
        buildToken = new CancellationToken();
        analysisVersion = 0;
        project = null;
        treeRoot = null;
    }

    void start() {
        FsPath logDir = Files.configDir("JNativeStudio");
        Logger.setLogPath(logDir.resolve("jnative-studio.log").toString());
        Logger.info("JNative Studio starting");
        window = new Window("JNative Studio", settings.windowWidth, settings.windowHeight);
        VBox root = new VBox();
        HBox toolbar = new HBox();
        Button openBtn = new Button("Open Project");
        Button saveBtn = new Button("Save");
        Button buildBtn = new Button("Build");
        Button runBtn = new Button("Run");
        Button stopBtn = new Button("Stop");
        Button searchBtn = new Button("Search");
        status = new Label("Ready");
        toolbar.add(openBtn);
        toolbar.add(saveBtn);
        toolbar.add(buildBtn);
        toolbar.add(runBtn);
        toolbar.add(stopBtn);
        toolbar.add(searchBtn);
        toolbar.add(status);

        explorer = new TreeView<ProjectNode>();
        editorTabs = new TabPane();
        problemsLabel = new Label("Problems");
        consoleLabel = new Label("Console");
        bottomTabs = new TabPane();
        bottomTabs.addTab("Problems", problemsLabel);
        bottomTabs.addTab("Console", consoleLabel);

        centerSplit = new SplitPane(Orientation.VERTICAL);
        centerSplit.setFirst(editorTabs);
        centerSplit.setSecond(bottomTabs);
        centerSplit.setDividerPosition(0.75);

        rootSplit = new SplitPane(Orientation.HORIZONTAL);
        rootSplit.setFirst(explorer);
        rootSplit.setSecond(centerSplit);
        rootSplit.setDividerPosition(0.22);

        root.add(toolbar);
        root.add(rootSplit);
        window.setContent(root);
        installMenus();

        openBtn.onClick(() -> { StudioActions.openProject(); });
        saveBtn.onClick(() -> { StudioActions.saveActive(); });
        buildBtn.onClick(() -> { StudioActions.build(); });
        runBtn.onClick(() -> { StudioActions.run(); });
        stopBtn.onClick(() -> { StudioActions.stop(); });
        searchBtn.onClick(() -> { StudioActions.searchInFiles("public"); });

        window.show();
        StudioHolder.app = this;
        status.setText("JNative Studio " + StudioVersion.VERSION);
    }

    void installMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("File");
        file.add(new MenuItem("New Project", () -> { StudioActions.newProject(); }));
        file.add(new MenuItem("Open Project", () -> { StudioActions.openProject(); }));
        file.add(new MenuItem("Save", () -> { StudioActions.saveActive(); }));
        file.add(new MenuItem("Save All", () -> { StudioActions.saveAll(); }));
        Menu build = new Menu("Build");
        build.add(new MenuItem("Build", () -> { StudioActions.build(); }));
        Menu run = new Menu("Run");
        run.add(new MenuItem("Run", () -> { StudioActions.run(); }));
        run.add(new MenuItem("Stop", () -> { StudioActions.stop(); }));
        Menu nav = new Menu("Navigate");
        nav.add(new MenuItem("Go to Definition", () -> { StudioActions.goToDefinition(); }));
        Menu help = new Menu("Help");
        help.add(new MenuItem("About", () -> { StudioActions.showAbout(); }));
        help.add(new MenuItem("Runtime Diagnostics", () -> { StudioActions.showRuntimeDiagnostics(); }));
        bar.add(file);
        bar.add(build);
        bar.add(run);
        bar.add(nav);
        bar.add(help);
        window.setMenuBar(bar);
    }

    EditorTab findTab(String path) {
        int i = 0;
        while (i < tabs.size()) {
            if (tabs.get(i).path.equals(path)) {
                return tabs.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    EditorTab activeTab() {
        int idx = editorTabs.getSelectedIndex();
        if (idx < 0 || idx >= tabs.size()) {
            return null;
        }
        return tabs.get(idx);
    }

    void openFile(String path) {
        EditorTab existing = findTab(path);
        if (existing != null) {
            int i = 0;
            while (i < tabs.size()) {
                if (tabs.get(i) == existing) {
                    editorTabs.setSelectedIndex(i);
                    return;
                }
                i = i + 1;
            }
        }
        TextDocument doc = TextDocument.load(path);
        CodeEditor ed = new CodeEditor();
        ed.setDocument(doc);
        if (settings.theme.equals("light")) {
            ed.setTheme(EditorTheme.light());
        } else {
            ed.setTheme(EditorTheme.dark());
        }
        EditorTab tab = new EditorTab(path, doc, ed);
        tabs.add(tab);
        editorTabs.addTab(tab.displayTitle(), ed);
        editorTabs.setSelectedIndex(tabs.size() - 1);
        indexFile(path, doc.getText());
        status.setText("Opened " + path);
    }

    void indexFile(String path, String text) {
        int line = 0;
        int i = 0;
        int lineStart = 0;
        while (i <= text.length()) {
            if (i == text.length() || text.charAt(i) == 10) {
                String ln = text.substring(lineStart, i);
                if (ln.indexOf("class ") >= 0 || ln.indexOf("interface ") >= 0) {
                    index.add(new StudioSymbol(ln, "type", path, line + 1, 1));
                }
                line = line + 1;
                lineStart = i + 1;
            }
            i = i + 1;
        }
        index.version = index.version + 1;
    }

    void setProject(Project p) {
        project = p;
        runConfig.mainClass = p.mainClass;
        runConfig.workingDirectory = p.rootDirectory;
        treeRoot = new ProjectNode(ProjectNodeKind.PROJECT, p.name, p.rootDirectory);
        loadNodeChildren(treeRoot);
        explorer.setModel(new ProjectTreeModel(treeRoot));
        explorer.setRenderer(new ProjectNodeRenderer());
        explorer.expand(0);
        window.setTitle("JNative Studio — " + p.name);
        status.setText("Project: " + p.rootDirectory);
        settings.recentProjects.add(p.rootDirectory);
        settings.save();
    }

    void loadNodeChildren(ProjectNode node) {
        if (node.childrenLoaded != 0) {
            return;
        }
        node.childrenLoaded = 1;
        if (node.kind != ProjectNodeKind.PROJECT && node.kind != ProjectNodeKind.DIRECTORY) {
            return;
        }
        ArrayList<String> names = Files.list(FsPath.of(node.path));
        int i = 0;
        while (i < names.size()) {
            String name = names.get(i);
            if (StudioUtil.shouldIgnore(name) == 0) {
                String childPath = node.path + "/" + name;
                int kind = ProjectNodeKind.OTHER_FILE;
                if (FsNative.isDirectory(childPath) != 0) {
                    kind = ProjectNodeKind.DIRECTORY;
                } else if (name.endsWith(".java") || name.endsWith(".jnative")) {
                    kind = ProjectNodeKind.SOURCE_FILE;
                }
                node.addChild(new ProjectNode(kind, name, childPath));
            }
            i = i + 1;
        }
    }

    void refreshProblemsView() {
        String t = "Problems (" + problems.size() + ")\n";
        int i = 0;
        while (i < problems.size()) {
            StudioDiagnostic d = problems.get(i);
            t = t + d.severity + " " + d.file + ":" + d.line + " " + d.message + "\n";
            i = i + 1;
        }
        problemsLabel.setText(t);
    }

    void refreshConsoleView() {
        consoleLabel.setText(console.getText());
    }

    void applyEditorDiagnostics() {
        EditorTab tab = activeTab();
        if (tab == null) {
            return;
        }
        ArrayList<EditorDiagnostic> list = new ArrayList<EditorDiagnostic>();
        int i = 0;
        while (i < problems.size()) {
            StudioDiagnostic d = problems.get(i);
            if (d.file.equals(tab.path) || tab.path.endsWith(d.file)) {
                list.add(EditorDiagnostic.fromCompiler(tab.document, d.line, d.column, d.severity, d.message));
            }
            i = i + 1;
        }
        tab.editor.setDiagnostics(list);
    }
}

class StudioHolder {
    static StudioApplication app;
}

class ProjectTreeModel implements TreeModel<ProjectNode> {
    ProjectNode rootNode;

    ProjectTreeModel(ProjectNode r) {
        rootNode = r;
    }

    ProjectNode root() {
        return rootNode;
    }

    int childCount(ProjectNode node) {
        StudioHolder.app.loadNodeChildren(node);
        return node.children.size();
    }

    ProjectNode child(ProjectNode node, int index) {
        return node.children.get(index);
    }
}

class ProjectNodeRenderer implements CellRenderer<ProjectNode> {
    String render(ProjectNode node) {
        return node.name;
    }
}

class StudioVersion {
    static String VERSION = "0.21";
}

class StudioUtil {
    static int shouldIgnore(String name) {
        if (name.equals("build") || name.equals("target") || name.equals(".git") || name.equals(".idea") || name.equals("out")) {
            return 1;
        }
        return 0;
    }
}

class StudioActions {
    static void openProject() {
        StudioApplication app = StudioHolder.app;
        String path = DirectoryDialog.open(app.window);
        if (path == null || path.length() == 0) {
            return;
        }
        Project p = Project.load(path);
        app.setProject(p);
    }

    static void newProject() {
        StudioApplication app = StudioHolder.app;
        String path = DirectoryDialog.open(app.window);
        if (path == null || path.length() == 0) {
            return;
        }
        FsPath root = FsPath.of(path).resolve("HelloStudio");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("src"));
        Project p = new Project();
        p.name = "HelloStudio";
        p.rootDirectory = root.toString();
        p.mainClass = "Main";
        p.save();
        String main = "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello JNative\");\n"
                + "    }\n"
                + "}\n";
        Files.writeString(root.resolve("src").resolve("Main.java"), main);
        app.setProject(p);
        app.openFile(root.resolve("src").resolve("Main.java").toString());
    }

    static void saveActive() {
        StudioApplication app = StudioHolder.app;
        EditorTab tab = app.activeTab();
        if (tab != null) {
            tab.save();
            app.status.setText("Saved " + tab.path);
        }
    }

    static void saveAll() {
        StudioApplication app = StudioHolder.app;
        int i = 0;
        while (i < app.tabs.size()) {
            app.tabs.get(i).save();
            i = i + 1;
        }
        app.status.setText("Saved all");
    }

    static void onTreeSelect() {
        StudioApplication app = StudioHolder.app;
        /* Expand lazy + open source on selection when possible. */
        if (app.treeRoot != null) {
            app.loadNodeChildren(app.treeRoot);
        }
    }

    static void openPath(String path) {
        StudioHolder.app.openFile(path);
    }

    static void build() {
        StudioApplication app = StudioHolder.app;
        if (app.project == null) {
            Dialogs.error(app.window, "No project open");
            return;
        }
        if (app.buildState == BuildState.BUILDING) {
            return;
        }
        app.buildState = BuildState.BUILDING;
        app.buildToken = new CancellationToken();
        app.status.setText("Building...");
        app.console.append("Build started\n");
        app.refreshConsoleView();
        EditorTab tab = app.activeTab();
        if (tab != null && tab.isDirty()) {
            tab.save();
        }
        StudioBuildWorker.lastSrc = app.project.rootDirectory + "/src/" + app.project.mainClass + ".java";
        StudioBuildWorker.lastOut = app.project.outputBinaryPath();
        StudioBuildWorker.lastJar = app.compilerJar;
        BuildThread t = new BuildThread();
        t.start();
    }

    static void run() {
        StudioApplication app = StudioHolder.app;
        if (app.project == null) {
            return;
        }
        StudioRunWorker.lastBin = app.project.outputBinaryPath();
        if (FsNative.exists(StudioRunWorker.lastBin) == 0) {
            build();
            return;
        }
        StudioRunWorker.lastCwd = app.project.rootDirectory;
        app.console.append("Running " + StudioRunWorker.lastBin + "\n");
        app.refreshConsoleView();
        RunThread t = new RunThread();
        t.start();
    }

    static void stop() {
        StudioApplication app = StudioHolder.app;
        if (app.runningProcess != null) {
            app.runningProcess.destroy();
            app.console.append("Process terminated\n");
            app.refreshConsoleView();
        }
        app.buildToken.cancel();
    }

    static void searchInFiles(String query) {
        StudioApplication app = StudioHolder.app;
        if (app.project == null) {
            return;
        }
        StudioSearchWorker.lastRoot = app.project.rootDirectory;
        StudioSearchWorker.lastQuery = query;
        SearchThread t = new SearchThread();
        t.start();
    }

    static void showAbout() {
        Dialogs.info(StudioHolder.app.window, "JNative Studio " + StudioVersion.VERSION + " / GC generational");
    }

    static void onBuildDone() {
        StudioActions.completeBuild(StudioBuildWorker.lastRc, StudioBuildWorker.lastOut, StudioBuildWorker.lastLog);
    }

    static void onRunDone() {
        StudioActions.appendConsole(StudioRunWorker.lastMsg);
        StudioHolder.app.runningProcess = null;
    }

    static void onSearchDone() {
        StudioActions.appendConsole(StudioSearchWorker.lastSummary);
    }

    static void goToDefinition() {
        StudioApplication app = StudioHolder.app;
        EditorTab tab = app.activeTab();
        if (tab == null) {
            return;
        }
        ArrayList<StudioSymbol> hits = app.index.findByName("Main");
        if (hits.size() > 0) {
            StudioSymbol s = hits.get(0);
            app.openFile(s.file);
            EditorTab t2 = app.findTab(s.file);
            if (t2 != null) {
                t2.editor.goTo(s.line - 1, s.column - 1);
            }
        }
    }

    static void showRuntimeDiagnostics() {
        StudioApplication app = StudioHolder.app;
        String msg = "tabs=" + app.tabs.size()
                + " problems=" + app.problems.size()
                + " index=" + app.index.symbols.size()
                + " buildState=" + app.buildState;
        Dialogs.info(app.window, msg);
    }

    static void completeBuild(int rc, String outPath, String log) {
        StudioApplication app = StudioHolder.app;
        app.console.append(log);
        if (rc == 0) {
            app.buildState = BuildState.SUCCESS;
            app.problems = new ArrayList<StudioDiagnostic>();
            app.status.setText("Build OK → " + outPath);
            app.console.append("Build finished successfully\n");
        } else {
            app.buildState = BuildState.FAILED;
            app.problems = new ArrayList<StudioDiagnostic>();
            app.problems.add(new StudioDiagnostic(1, "Build failed", outPath, 1, 1));
            app.status.setText("Build failed");
            app.console.append("Build failed (exit " + rc + ")\n");
        }
        app.refreshProblemsView();
        app.refreshConsoleView();
        app.applyEditorDiagnostics();
    }

    static void appendConsole(String s) {
        StudioApplication app = StudioHolder.app;
        app.console.append(s);
        app.refreshConsoleView();
    }
}

class StudioBuildWorker {
    static String lastSrc;
    static String lastOut;
    static String lastJar;
    static String lastLog;
    static int lastRc;

    static void run() {
        String javaBin = "/usr/bin/java";
        ProcessBuilder pb = new ProcessBuilder(javaBin);
        pb.argument("-jar");
        pb.argument(lastJar);
        pb.argument(lastSrc);
        pb.argument("-o");
        pb.argument(lastOut);
        pb.argument("--headless");
        pb.argument("--ui-backend");
        pb.argument("headless");
        Process p = pb.start();
        lastLog = "";
        lastRc = 1;
        if (p != null) {
            lastLog = p.readStdout(1024 * 1024) + p.readStderr(1024 * 1024);
            lastRc = p.waitFor();
        } else {
            lastLog = "failed to spawn compiler process\n";
        }
        Application.invokeLater(() -> { StudioActions.onBuildDone(); });
    }
}

class StudioRunWorker {
    static String lastMsg;
    static String lastBin;
    static String lastCwd;

    static void run() {
        ProcessBuilder pb = new ProcessBuilder(lastBin);
        pb.directory(lastCwd);
        Process p = pb.start();
        StudioHolder.app.runningProcess = p;
        lastMsg = "";
        int rc = 1;
        if (p != null) {
            lastMsg = p.readStdout(1024 * 1024);
            String err = p.readStderr(1024 * 1024);
            rc = p.waitFor();
            lastMsg = lastMsg + err + "\nProcess exited with code " + rc + "\n";
        } else {
            lastMsg = "failed to start process\n";
        }
        Application.invokeLater(() -> { StudioActions.onRunDone(); });
    }
}

class StudioSearchWorker {
    static String lastSummary;
    static String lastRoot;
    static String lastQuery;

    static void run() {
        ArrayList<SearchMatch> matches = new ArrayList<SearchMatch>();
        StudioSearchWorker.scanDir(lastRoot, lastQuery, matches, 0);
        lastSummary = "Search '" + lastQuery + "': " + matches.size() + " hits\n";
        int i = 0;
        while (i < matches.size() && i < 50) {
            SearchMatch m = matches.get(i);
            lastSummary = lastSummary + m.path + ":" + m.line + " " + m.preview + "\n";
            i = i + 1;
        }
        Application.invokeLater(() -> { StudioActions.onSearchDone(); });
    }

    static void scanDir(String dir, String query, ArrayList<SearchMatch> matches, int depth) {
        if (depth > 8 || matches.size() > 500) {
            return;
        }
        ArrayList<String> names = Files.list(FsPath.of(dir));
        int i = 0;
        while (i < names.size()) {
            String name = names.get(i);
            if (StudioUtil.shouldIgnore(name) == 0) {
                String path = dir + "/" + name;
                if (FsNative.isDirectory(path) != 0) {
                    scanDir(path, query, matches, depth + 1);
                } else if (name.endsWith(".java")) {
                    scanFile(path, query, matches);
                }
            }
            i = i + 1;
        }
    }

    static void scanFile(String path, String query, ArrayList<SearchMatch> matches) {
        String text = File.readString(path);
        int line = 0;
        int i = 0;
        int lineStart = 0;
        while (i <= text.length()) {
            if (i == text.length() || text.charAt(i) == 10) {
                String ln = text.substring(lineStart, i);
                if (ln.indexOf(query) >= 0) {
                    matches.add(new SearchMatch(path, line + 1, 1, ln));
                }
                line = line + 1;
                lineStart = i + 1;
            }
            i = i + 1;
        }
    }
}

class BuildThread extends Thread {
    public void run() {
        StudioBuildWorker.run();
    }
}

class RunThread extends Thread {
    public void run() {
        StudioRunWorker.run();
    }
}

class SearchThread extends Thread {
    public void run() {
        StudioSearchWorker.run();
    }
}
