import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;
import jnative.io.*;

/**
 * JNative Notepad — native desktop notepad demo.
 *
 * <pre>
 * java -cp target/classes io.jnative.Main \
 *   examples/notepad/JNativeNotepad.java -o build/notepad/JNativeNotepad --app-type gui
 * ./build/notepad/JNativeNotepad
 * </pre>
 */
public class JNativeNotepad {
    public static void main(String[] args) {
        Application app = new Application();
        NotepadWindow notepad = new NotepadWindow();
        notepad.show();
        if (args != null && args.length > 0) {
            if (args[0] != null && args[0].length() > 0) {
                NotepadActions.openPath(args[0]);
            }
        }
        app.run();
    }
}

class NotepadWindow {
    Window window;
    CodeEditor editor;
    Label statusLn;
    Label statusEnc;
    Label statusEol;
    HBox findBar;
    TextField findField;
    String currentPath;
    int findBarVisible;

    NotepadWindow() {
        currentPath = "";
        findBarVisible = 0;
        window = new Window("Untitled - JNative Notepad", 900, 650);
        VBox root = new VBox();

        HBox toolbar = new HBox();
        toolbar.spacing = 8;
        toolbar.padding = 8;
        Button newBtn = new Button("New");
        Button openBtn = new Button("Open");
        Button saveBtn = new Button("Save");
        Button undoBtn = new Button("Undo");
        Button findBtn = new Button("Find");
        toolbar.add(newBtn);
        toolbar.add(openBtn);
        toolbar.add(saveBtn);
        toolbar.add(undoBtn);
        toolbar.add(findBtn);

        findBar = new HBox();
        findField = new TextField("");
        Button findNextBtn = new Button("Find Next");
        Button findCloseBtn = new Button("Close");
        findBar.add(new Label("Find:"));
        findBar.add(findField);
        findBar.add(findNextBtn);
        findBar.add(findCloseBtn);
        findBar.setPrefSize(800, 40);
        findBar.setVisible(false);

        editor = new CodeEditor();
        editor.setTheme(EditorTheme.light());
        editor.setLineNumbersVisible(false);
        editor.setSyntaxHighlighting(false);
        editor.setPrefSize(860, 520);
        editor.setText("");
        editor.getDocument().markSaved();

        HBox status = new HBox();
        statusLn = new Label("Ln 1, Col 1");
        statusEnc = new Label("UTF-8");
        statusEol = new Label("LF");
        status.add(statusLn);
        status.add(statusEnc);
        status.add(statusEol);
        status.setPrefSize(860, 28);

        root.add(toolbar);
        root.add(findBar);
        root.add(editor);
        root.add(status);
        window.setContent(root);
        installMenus();

        newBtn.onClick(() -> { NotepadActions.doNew(); });
        openBtn.onClick(() -> { NotepadActions.doOpen(); });
        saveBtn.onClick(() -> { NotepadActions.doSave(); });
        undoBtn.onClick(() -> { NotepadActions.doUndo(); });
        findBtn.onClick(() -> { NotepadActions.showFind(); });
        findNextBtn.onClick(() -> { NotepadActions.doFindNext(); });
        findCloseBtn.onClick(() -> { NotepadActions.hideFind(); });

        editor.onCaretChanged(pos -> {
            NotepadActions.onCaret(pos);
        });
        editor.onKeyDown(event -> {
            NotepadActions.onEditorKey(event);
        });
        editor.onMouseDown(event -> {
            NotepadHolder.win.editor.requestFocus();
        });
        window.onClose(() -> {
            NotepadActions.onWindowClose();
        });

        NotepadHolder.win = this;
        refreshTitle();
        updateStatus();
    }

    void show() {
        window.show();
        editor.requestFocus();
        editor.ensureBlinkTimer();
        editor.repaint();
    }

    void installMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("File");
        file.add(new MenuItem("New", () -> { NotepadActions.doNew(); }));
        file.add(new MenuItem("Open...", () -> { NotepadActions.doOpen(); }));
        file.add(new MenuItem("Save", () -> { NotepadActions.doSave(); }));
        file.add(new MenuItem("Save As...", () -> { NotepadActions.doSaveAs(); }));
        file.addSeparator();
        file.add(new MenuItem("Exit", () -> { NotepadActions.doExit(); }));
        Menu edit = new Menu("Edit");
        edit.add(new MenuItem("Undo", () -> { NotepadActions.doUndo(); }));
        edit.add(new MenuItem("Redo", () -> { NotepadActions.doRedo(); }));
        edit.addSeparator();
        edit.add(new MenuItem("Cut", () -> { NotepadActions.doCut(); }));
        edit.add(new MenuItem("Copy", () -> { NotepadActions.doCopy(); }));
        edit.add(new MenuItem("Paste", () -> { NotepadActions.doPaste(); }));
        edit.add(new MenuItem("Delete", () -> { NotepadActions.doDelete(); }));
        edit.addSeparator();
        edit.add(new MenuItem("Select All", () -> { NotepadActions.doSelectAll(); }));
        Menu search = new Menu("Search");
        search.add(new MenuItem("Find...", () -> { NotepadActions.showFind(); }));
        search.add(new MenuItem("Find Next", () -> { NotepadActions.doFindNext(); }));
        Menu help = new Menu("Help");
        help.add(new MenuItem("About JNative Notepad", () -> { NotepadActions.doAbout(); }));
        bar.add(file);
        bar.add(edit);
        bar.add(search);
        bar.add(help);
        window.setMenuBar(bar);
    }

    boolean isDirty() {
        return editor.getDocument().isDirty();
    }

    String displayName() {
        if (currentPath == null || currentPath.length() == 0) {
            return "Untitled";
        }
        return FsPath.of(currentPath).getName();
    }

    void refreshTitle() {
        String name = displayName();
        if (isDirty()) {
            window.setTitle("*" + name + " - JNative Notepad");
        } else {
            window.setTitle(name + " - JNative Notepad");
        }
    }

    void updateStatus() {
        TextPosition p = editor.getDocument().offsetToPosition(editor.getCaretOffset());
        statusLn.setText("Ln " + (p.line + 1) + ", Col " + (p.column + 1));
        statusEnc.setText("UTF-8");
        statusEol.setText("LF");
        refreshTitle();
    }
}

class NotepadHolder {
    static NotepadWindow win;
}

class NotepadActions {
    static int confirmDiscardOrSave() {
        NotepadWindow w = NotepadHolder.win;
        if (!w.isDirty()) {
            return 2;
        }
        return Dialogs.askSave(w.window, "Save changes to " + w.displayName() + "?");
    }

    static void doNew() {
        NotepadWindow w = NotepadHolder.win;
        int r = confirmDiscardOrSave();
        if (r == 0) {
            return;
        }
        if (r == 1) {
            if (!saveInternal(0)) {
                return;
            }
        }
        w.editor.setText("");
        w.currentPath = "";
        w.editor.getDocument().markSaved();
        w.refreshTitle();
        w.updateStatus();
    }

    static void doOpen() {
        NotepadWindow w = NotepadHolder.win;
        int r = confirmDiscardOrSave();
        if (r == 0) {
            return;
        }
        if (r == 1) {
            if (!saveInternal(0)) {
                return;
            }
        }
        String path = FileDialog.open(w.window, "*.txt;*.java;*.jnative");
        if (path == null || path.length() == 0) {
            return;
        }
        openPath(path);
    }

    static void openPath(String path) {
        NotepadWindow w = NotepadHolder.win;
        TextDocument doc = TextDocument.load(path);
        if (doc == null) {
            Dialogs.error(w.window, "Unable to open file.\n" + path);
            return;
        }
        w.editor.setDocument(doc);
        w.editor.setLineNumbersVisible(false);
        w.editor.setSyntaxHighlighting(false);
        w.currentPath = path;
        w.refreshTitle();
        w.updateStatus();
        w.editor.repaint();
    }

    static void doSave() {
        saveInternal(0);
    }

    static void doSaveAs() {
        saveInternal(1);
    }

    static boolean saveInternal(int forceAs) {
        NotepadWindow w = NotepadHolder.win;
        String path = w.currentPath;
        if (forceAs != 0 || path == null || path.length() == 0) {
            path = FileDialog.save(w.window, "*.txt");
            if (path == null || path.length() == 0) {
                return false;
            }
        }
        TextDocument doc = w.editor.getDocument();
        doc.saveAs(path);
        if (doc.isDirty()) {
            Dialogs.error(w.window, "Unable to save file.\n" + path);
            return false;
        }
        w.currentPath = path;
        w.refreshTitle();
        w.updateStatus();
        return true;
    }

    static void doExit() {
        NotepadWindow w = NotepadHolder.win;
        w.window.close();
    }

    static void onWindowClose() {
        NotepadWindow w = NotepadHolder.win;
        int r = confirmDiscardOrSave();
        if (r == 0) {
            w.window.vetoClose();
            return;
        }
        if (r == 1) {
            if (!saveInternal(0)) {
                w.window.vetoClose();
            }
        }
    }

    static void doUndo() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.UNDO);
        NotepadHolder.win.updateStatus();
    }

    static void doRedo() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.REDO);
        NotepadHolder.win.updateStatus();
    }

    static void doCut() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.CUT);
        NotepadHolder.win.updateStatus();
    }

    static void doCopy() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.COPY);
    }

    static void doPaste() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.PASTE);
        NotepadHolder.win.updateStatus();
    }

    static void doDelete() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.DELETE_FORWARD);
        NotepadHolder.win.updateStatus();
    }

    static void doSelectAll() {
        NotepadHolder.win.editor.executeCommand(EditorCommand.SELECT_ALL);
    }

    static void showFind() {
        NotepadWindow w = NotepadHolder.win;
        w.findBarVisible = 1;
        w.findBar.setVisible(true);
    }

    static void hideFind() {
        NotepadWindow w = NotepadHolder.win;
        w.findBarVisible = 0;
        w.findBar.setVisible(false);
    }

    static void doFindNext() {
        NotepadWindow w = NotepadHolder.win;
        String q = w.findField.getText();
        if (q == null || q.length() == 0) {
            showFind();
            return;
        }
        w.editor.findNext(q);
        w.updateStatus();
    }

    static void doAbout() {
        Dialogs.info(NotepadHolder.win.window,
                "JNative Notepad\n\nBuilt with JNative\n\nNative desktop application\nNo JVM required");
    }

    static void onCaret(TextPosition pos) {
        NotepadHolder.win.updateStatus();
    }

    static void onEditorKey(KeyEvent event) {
        int code = event.keyCode;
        int ctrl = event.ctrlDown;
        if (event.metaDown != 0) {
            ctrl = 1;
        }
        if (ctrl == 0) {
            NotepadHolder.win.updateStatus();
            return;
        }
        if (code == 78 || code == 110) {
            doNew();
        } else if (code == 79 || code == 111) {
            doOpen();
        } else if (code == 83 || code == 115) {
            doSave();
        } else if (code == 70 || code == 102) {
            showFind();
        }
        NotepadHolder.win.updateStatus();
    }
}
