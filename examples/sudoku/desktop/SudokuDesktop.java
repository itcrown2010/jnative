/**
 * Native desktop Sudoku UI (jnative.ui). Game rules live in SudokuCore only.
 * Types come from flattened stdlib (no jnative.ui imports — FQN imports currently
 * resolve to missing class names; wildcard jnative.* is restored in Parser).
 *
 * Build (concatenated with core by scripts/build_desktop.sh):
 *   ./examples/sudoku/scripts/build_desktop.sh
 */
public class SudokuDesktop {
    public static void main(String[] args) {
        Application app = new Application();
        SudokuWindow.show();
        app.run();
    }
}

class SudokuUi {
    static Window window;
    static Label status;
    static Label timer;
    static Label langLabel;
    static Button[] cells;
    static int lang;
    static String savePath;
    static int tickBase;

    static void ensure() {
        if (cells == null) {
            cells = new Button[81];
        }
        if (savePath == null) {
            savePath = "build/sudoku/sudoku-save.jnsdk";
        }
    }
}

class SudokuWindow {
    static void show() {
        SudokuUi.ensure();
        SudokuUi.window = new Window(t("title"), 720, 820);
        VBox root = new VBox();

        HBox toolbar = new HBox();
        Button newEasy = new Button(t("easy"));
        Button newMed = new Button(t("medium"));
        Button newHard = new Button(t("hard"));
        Button undo = new Button(t("undo"));
        Button redo = new Button(t("redo"));
        Button hint = new Button(t("hint"));
        Button notes = new Button(t("notes"));
        Button pause = new Button(t("pause"));
        Button save = new Button(t("save"));
        Button load = new Button(t("load"));
        Button lang = new Button("中/EN");

        newEasy.onClick(() -> { SudokuCore.newGame(0, 0); SudokuActions.refresh(); });
        newMed.onClick(() -> { SudokuCore.newGame(1, 0); SudokuActions.refresh(); });
        newHard.onClick(() -> { SudokuCore.newGame(2, 0); SudokuActions.refresh(); });
        undo.onClick(() -> { SudokuCore.undo(); SudokuActions.refresh(); });
        redo.onClick(() -> { SudokuCore.redo(); SudokuActions.refresh(); });
        hint.onClick(() -> {
            int ok = SudokuCore.hint();
            if (ok == 0) {
                SudokuActions.setStatus(t("no_hint"), 1);
            }
            SudokuActions.refresh();
        });
        notes.onClick(() -> { SudokuCore.toggleNoteMode(); SudokuActions.refresh(); });
        pause.onClick(() -> {
            if (SudokuCore.getStatus() == SudokuCore.ST_PAUSED) {
                SudokuCore.resume();
            } else {
                SudokuCore.pause();
            }
            SudokuActions.refresh();
        });
        save.onClick(() -> { SudokuActions.doSave(); });
        load.onClick(() -> { SudokuActions.doLoad(); });
        lang.onClick(() -> {
            if (SudokuUi.lang == 0) {
                SudokuUi.lang = 1;
            } else {
                SudokuUi.lang = 0;
            }
            SudokuActions.setStatus(t("lang_switched"), 0);
            SudokuActions.refresh();
        });

        toolbar.add(newEasy);
        toolbar.add(newMed);
        toolbar.add(newHard);
        toolbar.add(undo);
        toolbar.add(redo);
        toolbar.add(hint);
        toolbar.add(notes);
        toolbar.add(pause);
        toolbar.add(save);
        toolbar.add(load);
        toolbar.add(lang);

        GridPane board = new GridPane();
        int i = 0;
        while (i < 81) {
            int idx = i;
            Button b = new Button(" ");
            b.onClick(() -> {
                SudokuCore.selectCell(idx / 9, idx % 9);
                SudokuActions.refresh();
            });
            SudokuUi.cells[i] = b;
            board.addAt(b, i % 9, i / 9);
            i = i + 1;
        }

        HBox digits = new HBox();
        int d = 1;
        while (d <= 9) {
            int digit = d;
            Button db = new Button("" + digit);
            db.onClick(() -> {
                SudokuCore.inputDigit(digit);
                SudokuActions.refresh();
            });
            digits.add(db);
            d = d + 1;
        }
        Button clear = new Button(t("clear"));
        clear.onClick(() -> { SudokuCore.clearCell(); SudokuActions.refresh(); });
        digits.add(clear);

        SudokuUi.status = new Label(t("ready"));
        SudokuUi.timer = new Label("0:00");
        SudokuUi.langLabel = new Label("");

        root.add(toolbar);
        root.add(board);
        root.add(digits);
        root.add(SudokuUi.timer);
        root.add(SudokuUi.status);

        SudokuUi.window.setContent(root);
        SudokuUi.window.onKeyDown(ev -> { SudokuActions.onKey(ev); });

        SudokuCore.newGame(0, 0);
        SudokuActions.refresh();
        SudokuUi.window.show();
        SudokuUi.tickBase = 0;
    }

    static String t(String key) {
        if (SudokuUi.lang == 0) {
            if (key.equals("title")) { return "JNative 数独"; }
            if (key.equals("easy")) { return "简单"; }
            if (key.equals("medium")) { return "中等"; }
            if (key.equals("hard")) { return "困难"; }
            if (key.equals("undo")) { return "撤销"; }
            if (key.equals("redo")) { return "重做"; }
            if (key.equals("hint")) { return "提示"; }
            if (key.equals("notes")) { return "笔记"; }
            if (key.equals("pause")) { return "暂停"; }
            if (key.equals("save")) { return "存档"; }
            if (key.equals("load")) { return "读档"; }
            if (key.equals("clear")) { return "清除"; }
            if (key.equals("ready")) { return "选择格子并输入数字"; }
            if (key.equals("no_hint")) { return "没有可提示的空格"; }
            if (key.equals("won")) { return "完成！"; }
            if (key.equals("paused")) { return "已暂停"; }
            if (key.equals("saved")) { return "已保存"; }
            if (key.equals("loaded")) { return "已加载"; }
            if (key.equals("load_fail")) { return "存档无效，当前对局未改动"; }
            if (key.equals("lang_switched")) { return "已切换语言"; }
            if (key.equals("notes_on")) { return "笔记模式：开"; }
            if (key.equals("conflict")) { return "冲突"; }
            return key;
        }
        if (key.equals("title")) { return "JNative Sudoku"; }
        if (key.equals("easy")) { return "Easy"; }
        if (key.equals("medium")) { return "Medium"; }
        if (key.equals("hard")) { return "Hard"; }
        if (key.equals("undo")) { return "Undo"; }
        if (key.equals("redo")) { return "Redo"; }
        if (key.equals("hint")) { return "Hint"; }
        if (key.equals("notes")) { return "Notes"; }
        if (key.equals("pause")) { return "Pause"; }
        if (key.equals("save")) { return "Save"; }
        if (key.equals("load")) { return "Load"; }
        if (key.equals("clear")) { return "Clear"; }
        if (key.equals("ready")) { return "Select a cell and enter a digit"; }
        if (key.equals("no_hint")) { return "No hint available"; }
        if (key.equals("won")) { return "Solved!"; }
        if (key.equals("paused")) { return "Paused"; }
        if (key.equals("saved")) { return "Saved"; }
        if (key.equals("loaded")) { return "Loaded"; }
        if (key.equals("load_fail")) { return "Bad save; current game kept"; }
        if (key.equals("lang_switched")) { return "Language switched"; }
        if (key.equals("notes_on")) { return "Notes: ON"; }
        if (key.equals("conflict")) { return "Conflict"; }
        return key;
    }
}

class SudokuActions {
    static void setStatus(String msg, int err) {
        SudokuUi.status.setText(msg);
    }

    static void refresh() {
        SudokuUi.tickBase = SudokuUi.tickBase + 16;
        int elapsed = SudokuCore.tick(SudokuUi.tickBase);
        int sec = elapsed / 1000;
        int m = sec / 60;
        int s = sec % 60;
        String ts = "" + m + ":";
        if (s < 10) {
            ts = ts + "0" + s;
        } else {
            ts = ts + s;
        }
        SudokuUi.timer.setText(ts);

        int i = 0;
        while (i < 81) {
            int v = SudokuCore.getValue(i);
            int notes = SudokuCore.getNotes(i);
            String text = " ";
            if (v != 0) {
                text = "" + v;
            } else if (notes != 0) {
                text = notesLabel(notes);
            }
            Button b = SudokuUi.cells[i];
            b.setText(text);
            /* Accessibility name via tooltip-like status on select; buttons keep digit text. */
            i = i + 1;
        }

        if (SudokuCore.isComplete() != 0) {
            setStatus(SudokuWindow.t("won"), 0);
        } else if (SudokuCore.getStatus() == SudokuCore.ST_PAUSED) {
            setStatus(SudokuWindow.t("paused"), 0);
        } else if (SudokuCore.getNoteMode() != 0) {
            setStatus(SudokuWindow.t("notes_on"), 0);
        } else {
            int sel = SudokuCore.getSelected();
            String msg = SudokuWindow.t("ready");
            if (SudokuCore.getConflict(sel) != 0) {
                msg = SudokuWindow.t("conflict");
            }
            setStatus(msg, 0);
        }
    }

    static String notesLabel(int mask) {
        String s = "";
        int d = 1;
        while (d <= 9) {
            if ((mask & (1 << (d - 1))) != 0) {
                s = s + d;
            }
            d = d + 1;
        }
        return s;
    }

    static void onKey(KeyEvent ev) {
        int code = ev.keyCode;
        if (code == KeyCode.LEFT) { SudokuCore.moveSelection(0, 0 - 1); }
        else if (code == KeyCode.RIGHT) { SudokuCore.moveSelection(0, 1); }
        else if (code == KeyCode.UP) { SudokuCore.moveSelection(0 - 1, 0); }
        else if (code == KeyCode.DOWN) { SudokuCore.moveSelection(1, 0); }
        else if (code == KeyCode.BACKSPACE || code == KeyCode.DELETE || code == KeyCode.DIGIT0) {
            SudokuCore.clearCell();
        } else if (code >= KeyCode.DIGIT0 + 1 && code <= KeyCode.DIGIT9) {
            SudokuCore.inputDigit(code - KeyCode.DIGIT0);
        }
        refresh();
    }

    static void doSave() {
        SudokuCore.prepareSave();
        int n = SudokuCore.getSaveLen();
        String s = "";
        int i = 0;
        while (i < n) {
            int c = SudokuCore.getSaveChar(i);
            if (c == 10) {
                s = s + "\n";
            } else {
                s = s + codeToStr(c);
            }
            i = i + 1;
        }
        File.writeString(SudokuUi.savePath, s);
        setStatus(SudokuWindow.t("saved"), 0);
    }

    static void doLoad() {
        if (File.exists(SudokuUi.savePath) == false) {
            setStatus(SudokuWindow.t("load_fail"), 1);
            return;
        }
        String data = File.readString(SudokuUi.savePath);
        int ok = SudokuCore.loadSave(data);
        if (ok == 0) {
            setStatus(SudokuWindow.t("load_fail"), 1);
        } else {
            setStatus(SudokuWindow.t("loaded"), 0);
        }
        refresh();
    }

    static String codeToStr(int c) {
        if (c >= 48 && c <= 57) {
            return "" + (c - 48);
        }
        if (c == 74) { return "J"; }
        if (c == 78) { return "N"; }
        if (c == 83) { return "S"; }
        if (c == 68) { return "D"; }
        if (c == 75) { return "K"; }
        if (c == 44) { return ","; }
        if (c == 59) { return ";"; }
        if (c == 45) { return "-"; }
        return "";
    }
}
