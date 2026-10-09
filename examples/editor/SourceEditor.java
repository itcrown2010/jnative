import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;

/**
 * JNative Source Editor — 0.20 Mini IDE surface.
 *
 * <pre>
 * File / Edit / Search / Build
 * Line numbers + syntax highlight + caret + selection
 * Clipboard / undo / find / open-save / background Build
 * </pre>
 * @author 张晓达
 */
public class SourceEditor {
    static CodeEditor editor;
    static Window window;
    static Label status;
    static String currentPath;
    static String compilerJar;
    static int building;
    static int lastBuildRc;
    static String lastBuildOut;

    public static void main(String[] args) {
        currentPath = "";
        compilerJar = "target/jnative.jar";
        building = 0;
        Application app = new Application();
        window = new Window("JNative Source Editor", 1000, 700);
        VBox root = new VBox();
        HBox toolbar = new HBox();
        Button openBtn = new Button("Open");
        Button saveBtn = new Button("Save");
        Button findBtn = new Button("Find");
        Button buildBtn = new Button("Build");
        status = new Label("Ready");
        toolbar.add(openBtn);
        toolbar.add(saveBtn);
        toolbar.add(findBtn);
        toolbar.add(buildBtn);
        toolbar.add(status);
        editor = new CodeEditor();
        editor.setTheme(EditorTheme.dark());
        editor.setText(
                "public class Main {\n"
                        + "    public static void main(String[] args) {\n"
                        + "        System.out.println(\"Hello JNative\");\n"
                        + "    }\n"
                        + "}\n");
        root.add(toolbar);
        root.add(editor);
        window.setContent(root);
        installMenus();
        openBtn.onClick(() -> {
            doOpen();
        });
        saveBtn.onClick(() -> {
            doSave();
        });
        findBtn.onClick(() -> {
            editor.findNext("public");
            status.setText("Find: public");
        });
        buildBtn.onClick(() -> {
            doBuild();
        });
        window.show();
        editor.repaint();
        app.run();
    }

    static void installMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("File");
        file.add(new MenuItem("Open", () -> {
            doOpen();
        }));
        file.add(new MenuItem("Save", () -> {
            doSave();
        }));
        file.add(new MenuItem("Save As", () -> {
            doSaveAs();
        }));
        Menu edit = new Menu("Edit");
        edit.add(new MenuItem("Undo", () -> {
            editor.executeCommand(EditorCommand.UNDO);
        }));
        edit.add(new MenuItem("Redo", () -> {
            editor.executeCommand(EditorCommand.REDO);
        }));
        edit.add(new MenuItem("Cut", () -> {
            editor.executeCommand(EditorCommand.CUT);
        }));
        edit.add(new MenuItem("Copy", () -> {
            editor.executeCommand(EditorCommand.COPY);
        }));
        edit.add(new MenuItem("Paste", () -> {
            editor.executeCommand(EditorCommand.PASTE);
        }));
        edit.add(new MenuItem("Select All", () -> {
            editor.executeCommand(EditorCommand.SELECT_ALL);
        }));
        Menu search = new Menu("Search");
        search.add(new MenuItem("Find public", () -> {
            editor.findNext("public");
            status.setText("Find: public");
        }));
        Menu build = new Menu("Build");
        build.add(new MenuItem("Build", () -> {
            doBuild();
        }));
        bar.add(file);
        bar.add(edit);
        bar.add(search);
        bar.add(build);
        window.setMenuBar(bar);
    }

    static void doOpen() {
        String path = FileDialog.open(window, "*.java");
        if (path == null || path.length() == 0) {
            return;
        }
        TextDocument doc = TextDocument.load(path);
        editor.setDocument(doc);
        currentPath = path;
        status.setText("Opened " + path);
    }

    static void doSave() {
        if (currentPath == null || currentPath.length() == 0) {
            doSaveAs();
            return;
        }
        editor.getDocument().saveAs(currentPath);
        status.setText("Saved " + currentPath);
    }

    static void doSaveAs() {
        String path = FileDialog.save(window, "*.java");
        if (path == null || path.length() == 0) {
            return;
        }
        currentPath = path;
        editor.getDocument().saveAs(path);
        status.setText("Saved " + path);
    }

    /**
     * Background Build: save buffer, spawn host compiler (java -jar jnative.jar),
     * map exit code into diagnostics via invokeLater.
     */
    static void doBuild() {
        if (building != 0) {
            status.setText("Build already running");
            return;
        }
        if (currentPath == null || currentPath.length() == 0) {
            currentPath = "Main.java";
        }
        editor.getDocument().saveAs(currentPath);
        editor.clearDiagnostics();
        status.setText("Building...");
        building = 1;
        lastBuildOut = "out-main";
        Thread t = new Thread(() -> {
            String cmd = "java -jar " + compilerJar + " " + currentPath + " -o " + lastBuildOut
                    + " --headless --ui-backend headless";
            lastBuildRc = ProcessRunner.run(cmd);
            Application.invokeLater(() -> {
                finishBuild();
            });
        });
        t.start();
    }

    static void finishBuild() {
        building = 0;
        if (lastBuildRc == 0) {
            editor.clearDiagnostics();
            status.setText("Build OK → " + lastBuildOut);
            Dialogs.info(window, "Native executable generated: " + lastBuildOut);
        } else {
            ArrayList<EditorDiagnostic> diags = new ArrayList<EditorDiagnostic>();
            diags.add(new EditorDiagnostic(0, 1, EditorDiagnostic.ERROR, "Build failed"));
            editor.setDiagnostics(diags);
            status.setText("Build failed");
            Dialogs.error(window, "Compilation failed. See diagnostics underline.");
        }
    }
}
