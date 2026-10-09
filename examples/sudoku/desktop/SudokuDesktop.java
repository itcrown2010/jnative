/**
 * Native desktop Sudoku UI (jnative.ui). Game rules live in SudokuCore only.
 * Types come from flattened stdlib (no jnative.ui imports — FQN imports currently
 * resolve to missing class names; wildcard jnative.* is restored in Parser).
 *
 * Build (concatenated with core by scripts/build_desktop.sh):
 *   ./examples/sudoku/scripts/build_desktop.sh
 * @author 张晓达
 */
public class SudokuDesktop {
    public static void main(String[] args) {
        Theme.setAppearanceMode(Theme.MODE_LIGHT);
        Application app = new Application();
        SudokuWindow.show();
        app.run();
    }
}

/** Warm paper theme — signed ARGB ints (same packing as calculator). */
class SudokuTheme {
    static int BG = -724760;
    static int PANEL = -1251880;
    static int INK = -14935529;
    static int CELL = -520;
    static int SEL = -2562318;
    static int PEER = -1317674;
    static int SAME = -793936;
    static int CONFLICT = -998208;
    static int USER = -16032597;
    static int ACCENT = -15766964;
    static int BTN = -520;
    static int MUTED = -10725812;
    static int WHITE = -1;
    static int GRID = -14014944;
    static int NOTES = -9804452;
    static int CELL_SIZE = 52;
    static int DIGIT_SIZE = 44;
    static int RADIUS = 8;
}

class SudokuUi {
    static Window window;
    static Label title;
    static Label status;
    static Label timer;
    static Label meta;
    static Button[] cells;
    static Button notesBtn;
    static Button[] digitBtns;
    static Button clearBtn;
    static int lang;
    static String savePath;
    static int tickBase;

    static void ensure() {
        if (cells == null) {
            cells = new Button[81];
        }
        if (digitBtns == null) {
            digitBtns = new Button[9];
        }
        if (savePath == null) {
            savePath = "build/sudoku/sudoku-save.jnsdk";
        }
    }
}

class SudokuWindow {
    static void show() {
        SudokuUi.ensure();
        SudokuUi.window = new Window(t("title"), 640, 860);
        SudokuUi.window.setBackgroundRgba(SudokuTheme.BG);

        VBox root = new VBox();
        root.spacing = 10;
        root.padding = 16;
        root.setBackgroundRgba(SudokuTheme.BG);

        SudokuUi.title = new Label(t("title"));
        SudokuUi.title.setDisplayStyle(22, 0, SudokuTheme.INK);
        SudokuUi.title.setPrefSize(600, 32);

        SudokuUi.meta = new Label(t("subtitle"));
        SudokuUi.meta.setDisplayStyle(12, 0, SudokuTheme.MUTED);
        SudokuUi.meta.setPrefSize(600, 20);

        /* Difficulty row */
        HBox diffBar = new HBox();
        diffBar.spacing = 8;
        diffBar.padding = 8;
        diffBar.setBackgroundRgba(SudokuTheme.PANEL);
        Button newEasy = toolBtn(t("easy"), 1);
        Button newMed = toolBtn(t("medium"), 1);
        Button newHard = toolBtn(t("hard"), 1);
        newEasy.onClick(() -> { SudokuCore.newGame(0, 0); SudokuActions.refresh(); });
        newMed.onClick(() -> { SudokuCore.newGame(1, 0); SudokuActions.refresh(); });
        newHard.onClick(() -> { SudokuCore.newGame(2, 0); SudokuActions.refresh(); });
        diffBar.add(newEasy);
        diffBar.add(newMed);
        diffBar.add(newHard);

        /* Action row */
        HBox toolBar = new HBox();
        toolBar.spacing = 6;
        toolBar.padding = 8;
        toolBar.setBackgroundRgba(SudokuTheme.PANEL);
        Button undo = toolBtn(t("undo"), 0);
        Button redo = toolBtn(t("redo"), 0);
        Button hint = toolBtn(t("hint"), 0);
        SudokuUi.notesBtn = toolBtn(t("notes"), 0);
        Button pause = toolBtn(t("pause"), 0);
        Button save = toolBtn(t("save"), 0);
        Button load = toolBtn(t("load"), 0);
        Button lang = toolBtn("中/EN", 0);
        undo.onClick(() -> { SudokuCore.undo(); SudokuActions.refresh(); });
        redo.onClick(() -> { SudokuCore.redo(); SudokuActions.refresh(); });
        hint.onClick(() -> {
            int ok = SudokuCore.hint();
            if (ok == 0) {
                SudokuActions.setStatus(t("no_hint"), 1);
            }
            SudokuActions.refresh();
        });
        SudokuUi.notesBtn.onClick(() -> { SudokuCore.toggleNoteMode(); SudokuActions.refresh(); });
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
        toolBar.add(undo);
        toolBar.add(redo);
        toolBar.add(hint);
        toolBar.add(SudokuUi.notesBtn);
        toolBar.add(pause);
        toolBar.add(save);
        toolBar.add(load);
        toolBar.add(lang);

        /* Board: 3×3 boxes with thicker gap for 宫 boundaries */
        GridPane boxes = new GridPane();
        boxes.spacing = 4;
        boxes.padding = 6;
        boxes.cellW = SudokuTheme.CELL_SIZE * 3 + 2;
        boxes.cellH = SudokuTheme.CELL_SIZE * 3 + 2;
        boxes.setBackgroundRgba(SudokuTheme.GRID);

        int br = 0;
        while (br < 3) {
            int bc = 0;
            while (bc < 3) {
                GridPane mini = new GridPane();
                mini.spacing = 1;
                mini.padding = 0;
                mini.cellW = SudokuTheme.CELL_SIZE;
                mini.cellH = SudokuTheme.CELL_SIZE;
                mini.setBackgroundRgba(SudokuTheme.GRID);

                int r0 = br * 3;
                int c0 = bc * 3;
                int lr = 0;
                while (lr < 3) {
                    int lc = 0;
                    while (lc < 3) {
                        int row = r0 + lr;
                        int col = c0 + lc;
                        int idx = row * 9 + col;
                        Button b = new Button(" ");
                        b.setPrefSize(SudokuTheme.CELL_SIZE, SudokuTheme.CELL_SIZE);
                        b.setFilledStyle(SudokuTheme.CELL, SudokuTheme.INK, 4, 18);
                        int capture = idx;
                        b.onClick(() -> {
                            SudokuCore.selectCell(capture / 9, capture % 9);
                            SudokuActions.refresh();
                        });
                        SudokuUi.cells[idx] = b;
                        mini.addAt(b, lc, lr);
                        lc = lc + 1;
                    }
                    lr = lr + 1;
                }
                boxes.addAt(mini, bc, br);
                bc = bc + 1;
            }
            br = br + 1;
        }

        /* Digit pad */
        HBox digits = new HBox();
        digits.spacing = 6;
        digits.padding = 4;
        digits.setBackgroundRgba(SudokuTheme.BG);
        int d = 1;
        while (d <= 9) {
            int digit = d;
            Button db = new Button("" + digit);
            db.setPrefSize(SudokuTheme.DIGIT_SIZE, SudokuTheme.DIGIT_SIZE);
            db.setFilledStyle(SudokuTheme.ACCENT, SudokuTheme.WHITE, SudokuTheme.RADIUS, 18);
            db.onClick(() -> {
                SudokuCore.inputDigit(digit);
                SudokuActions.refresh();
            });
            SudokuUi.digitBtns[d - 1] = db;
            digits.add(db);
            d = d + 1;
        }
        SudokuUi.clearBtn = new Button(t("clear"));
        SudokuUi.clearBtn.setPrefSize(72, SudokuTheme.DIGIT_SIZE);
        SudokuUi.clearBtn.setFilledStyle(SudokuTheme.MUTED, SudokuTheme.WHITE, SudokuTheme.RADIUS, 14);
        SudokuUi.clearBtn.onClick(() -> { SudokuCore.clearCell(); SudokuActions.refresh(); });
        digits.add(SudokuUi.clearBtn);

        HBox footer = new HBox();
        footer.spacing = 16;
        footer.padding = 4;
        footer.setBackgroundRgba(SudokuTheme.BG);
        SudokuUi.timer = new Label("0:00");
        SudokuUi.timer.setDisplayStyle(16, 0, SudokuTheme.INK);
        SudokuUi.timer.setPrefSize(80, 24);
        SudokuUi.status = new Label(t("ready"));
        SudokuUi.status.setDisplayStyle(14, 0, SudokuTheme.MUTED);
        SudokuUi.status.setPrefSize(480, 24);
        footer.add(SudokuUi.timer);
        footer.add(SudokuUi.status);

        root.add(SudokuUi.title);
        root.add(SudokuUi.meta);
        root.add(diffBar);
        root.add(toolBar);
        root.add(boxes);
        root.add(digits);
        root.add(footer);

        SudokuUi.window.setContent(root);
        SudokuUi.window.onKeyDown(ev -> { SudokuActions.onKey(ev); });

        SudokuCore.newGame(0, 0);
        SudokuActions.refresh();
        SudokuUi.window.show();
        SudokuUi.tickBase = 0;
    }

    static Button toolBtn(String label, int accent) {
        Button b = new Button(label);
        if (accent != 0) {
            b.setFilledStyle(SudokuTheme.ACCENT, SudokuTheme.WHITE, SudokuTheme.RADIUS, 13);
        } else {
            b.setFilledStyle(SudokuTheme.BTN, SudokuTheme.INK, SudokuTheme.RADIUS, 13);
        }
        b.setPrefSize(Button.buttonPrefWidth(label), 34);
        return b;
    }

    static String t(String key) {
        if (SudokuUi.lang == 0) {
            if (key.equals("title")) { return "JNative 数独"; }
            if (key.equals("subtitle")) { return "原生桌面 · 共享核心 · 不依赖网页容器"; }
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
            if (key.equals("conflict")) { return "冲突（不只靠颜色）"; }
            return key;
        }
        if (key.equals("title")) { return "JNative Sudoku"; }
        if (key.equals("subtitle")) { return "Native desktop · shared core · not a web shell"; }
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
        if (key.equals("conflict")) { return "Conflict (not color-only)"; }
        return key;
    }
}

class SudokuActions {
    static void setStatus(String msg, int err) {
        if (err != 0) {
            SudokuUi.status.setDisplayStyle(14, 0, SudokuTheme.CONFLICT);
        } else {
            SudokuUi.status.setDisplayStyle(14, 0, SudokuTheme.MUTED);
        }
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
        SudokuUi.title.setText(SudokuWindow.t("title"));
        SudokuUi.meta.setText(SudokuWindow.t("subtitle"));

        int sel = SudokuCore.getSelected();
        int hiDigit = SudokuCore.getHighlightDigit();

        int i = 0;
        while (i < 81) {
            int v = SudokuCore.getValue(i);
            int notes = SudokuCore.getNotes(i);
            int given = SudokuCore.getGiven(i);
            int conflict = SudokuCore.getConflict(i);
            String text = " ";
            int fontPt = 20;
            int fg = SudokuTheme.USER;
            if (v != 0) {
                text = "" + v;
                if (given != 0) {
                    fg = SudokuTheme.INK;
                    fontPt = 20;
                } else {
                    fg = SudokuTheme.USER;
                    fontPt = 20;
                }
            } else if (notes != 0) {
                text = notesLabel(notes);
                fg = SudokuTheme.NOTES;
                fontPt = 10;
            }

            int bg = SudokuTheme.CELL;
            if (conflict != 0) {
                bg = SudokuTheme.CONFLICT;
            } else if (i == sel) {
                bg = SudokuTheme.SEL;
            } else if (SudokuCore.sameRow(i) != 0 || SudokuCore.sameCol(i) != 0
                    || SudokuCore.sameBox(i) != 0) {
                bg = SudokuTheme.PEER;
            } else if (hiDigit != 0 && v == hiDigit) {
                bg = SudokuTheme.SAME;
            }

            Button b = SudokuUi.cells[i];
            b.setText(text);
            b.setPrefSize(SudokuTheme.CELL_SIZE, SudokuTheme.CELL_SIZE);
            b.setFilledStyle(bg, fg, 4, fontPt);
            i = i + 1;
        }

        if (SudokuUi.notesBtn != null) {
            if (SudokuCore.getNoteMode() != 0) {
                SudokuUi.notesBtn.setFilledStyle(SudokuTheme.ACCENT, SudokuTheme.WHITE,
                        SudokuTheme.RADIUS, 13);
            } else {
                SudokuUi.notesBtn.setFilledStyle(SudokuTheme.BTN, SudokuTheme.INK,
                        SudokuTheme.RADIUS, 13);
            }
        }

        if (SudokuCore.isComplete() != 0) {
            setStatus(SudokuWindow.t("won"), 0);
            SudokuUi.status.setDisplayStyle(14, 0, SudokuTheme.ACCENT);
        } else if (SudokuCore.getStatus() == SudokuCore.ST_PAUSED) {
            setStatus(SudokuWindow.t("paused"), 0);
        } else if (SudokuCore.getNoteMode() != 0) {
            setStatus(SudokuWindow.t("notes_on"), 0);
        } else {
            String msg = SudokuWindow.t("ready");
            if (SudokuCore.getConflict(sel) != 0) {
                msg = SudokuWindow.t("conflict");
                setStatus(msg, 1);
            } else {
                setStatus(msg, 0);
            }
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
