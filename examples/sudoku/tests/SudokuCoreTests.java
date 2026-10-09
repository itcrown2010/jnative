/**
 * Headless Sudoku core tests. Built by concatenating with SudokuCore.java.
 */
public class SudokuCoreTests {
    static int passed = 0;
    static int failed = 0;

    static void ok(String name, boolean cond) {
        if (cond) {
            passed = passed + 1;
            System.out.println("PASS " + name);
        } else {
            failed = failed + 1;
            System.out.println("FAIL " + name);
        }
    }

    static String boardString() {
        SudokuCore.exportBoardDigits();
        int n = SudokuCore.getSaveLen();
        String s = "";
        int i = 0;
        while (i < n) {
            int c = SudokuCore.getSaveChar(i);
            s = s + fromCode(c);
            i = i + 1;
        }
        return s;
    }

    static String saveString() {
        SudokuCore.prepareSave();
        int n = SudokuCore.getSaveLen();
        String s = "";
        int i = 0;
        while (i < n) {
            int c = SudokuCore.getSaveChar(i);
            s = s + fromCode(c);
            i = i + 1;
        }
        return s;
    }

    /** Map ASCII code to 1-char string (no char literals — unsupported). */
    static String fromCode(int c) {
        if (c == 10) {
            return "\n";
        }
        if (c >= 48 && c <= 57) {
            return digitStr(c - 48);
        }
        if (c == 74) {
            return "J";
        }
        if (c == 78) {
            return "N";
        }
        if (c == 83) {
            return "S";
        }
        if (c == 68) {
            return "D";
        }
        if (c == 75) {
            return "K";
        }
        if (c == 44) {
            return ",";
        }
        if (c == 59) {
            return ";";
        }
        if (c == 45) {
            return "-";
        }
        return "?";
    }

    static String digitStr(int d) {
        if (d == 0) { return "0"; }
        if (d == 1) { return "1"; }
        if (d == 2) { return "2"; }
        if (d == 3) { return "3"; }
        if (d == 4) { return "4"; }
        if (d == 5) { return "5"; }
        if (d == 6) { return "6"; }
        if (d == 7) { return "7"; }
        if (d == 8) { return "8"; }
        if (d == 9) { return "9"; }
        return "0";
    }

    static boolean strEq(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int i = 0;
        while (i < a.length()) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("SudokuCoreTests");

        /* Catalog uniqueness */
        int d = 0;
        while (d <= 2) {
            int count = SudokuCore.puzzleCount(d);
            ok("catalog-count-" + d, count >= 2);
            int i = 0;
            while (i < count) {
                int r = SudokuCore.newGame(d, i);
                ok("newGame-" + d + "-" + i, r == 1 && SudokuCore.getLastError() == 0);
                i = i + 1;
            }
            d = d + 1;
        }

        ok("newGame-easy0", SudokuCore.newGame(0, 0) == 1);

        /* Fixed cell rejected */
        int fi = 0;
        while (fi < 81) {
            if (SudokuCore.getGiven(fi) == 1) {
                break;
            }
            fi = fi + 1;
        }
        SudokuCore.selectCell(fi / 9, fi % 9);
        ok("fixed-reject", SudokuCore.inputDigit(9) == 0 && SudokuCore.getLastError() == 2);

        /* Find empty and create row conflict */
        ok("newGame-easy0b", SudokuCore.newGame(0, 0) == 1);
        int empty = 0;
        while (empty < 81) {
            if (SudokuCore.getGiven(empty) == 0) {
                break;
            }
            empty = empty + 1;
        }
        int row = empty / 9;
        int digit = 0;
        int c = 0;
        while (c < 9) {
            int v = SudokuCore.getValue(row * 9 + c);
            if (v != 0) {
                digit = v;
                break;
            }
            c = c + 1;
        }
        SudokuCore.selectCell(row, empty % 9);
        ok("input-ok", SudokuCore.inputDigit(digit) == 1);
        ok("row-conflict", SudokuCore.getConflict(empty) == 1);

        /* Clear + undo/redo */
        ok("clear", SudokuCore.clearCell() == 1);
        ok("conflict-cleared", SudokuCore.getConflict(empty) == 0);
        ok("undo", SudokuCore.undo() == 1);
        ok("undo-restored-conflict", SudokuCore.getConflict(empty) == 1);
        ok("redo", SudokuCore.redo() == 1);
        ok("redo-cleared", SudokuCore.getValue(empty) == 0);

        /* Notes do not complete */
        ok("newGame-notes", SudokuCore.newGame(0, 0) == 1);
        SudokuCore.setNoteMode(1);
        int e2 = 0;
        while (e2 < 81) {
            if (SudokuCore.getGiven(e2) == 0) {
                break;
            }
            e2 = e2 + 1;
        }
        SudokuCore.selectCell(e2 / 9, e2 % 9);
        SudokuCore.inputDigit(1);
        ok("note-set", SudokuCore.getNotes(e2) != 0 && SudokuCore.getValue(e2) == 0);
        ok("not-won-with-notes", SudokuCore.isComplete() == 0);

        /* Hint fills a correct digit */
        SudokuCore.setNoteMode(0);
        ok("hint", SudokuCore.hint() == 1);
        int hi = SudokuCore.getSelected();
        ok("hint-correct", SudokuCore.getValue(hi) == SudokuCore.getSolutionDigit(hi));

        /* Complete by filling solution (skip givens) */
        ok("newGame-complete", SudokuCore.newGame(0, 0) == 1);
        int i = 0;
        while (i < 81) {
            if (SudokuCore.getGiven(i) == 0) {
                SudokuCore.selectCell(i / 9, i % 9);
                SudokuCore.inputDigit(SudokuCore.getSolutionDigit(i));
            }
            i = i + 1;
        }
        ok("won", SudokuCore.isComplete() == 1 && SudokuCore.getStatus() == SudokuCore.ST_WON);

        /* Incomplete filled-looking board cannot win: leave one wrong */
        ok("newGame-wrong", SudokuCore.newGame(0, 0) == 1);
        i = 0;
        int lastEmpty = 0 - 1;
        while (i < 81) {
            if (SudokuCore.getGiven(i) == 0) {
                lastEmpty = i;
                SudokuCore.selectCell(i / 9, i % 9);
                int sol = SudokuCore.getSolutionDigit(i);
                int wrong = sol + 1;
                if (wrong > 9) {
                    wrong = 1;
                }
                SudokuCore.inputDigit(wrong);
            }
            i = i + 1;
        }
        /* Fix all but keep conflicts / wrong — should not be WON */
        ok("not-won-wrong", SudokuCore.isComplete() == 0);

        /* Solver diagnostics */
        ok("unsolvable", SudokuCore.countSolutionsPuzzle(
            "111111111000000000000000000000000000000000000000000000000000000000000000000000000") == 0);
        ok("multi-or-zero-empty", SudokuCore.countSolutionsPuzzle(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000") != 1);

        /* Save round-trip */
        ok("newGame-save", SudokuCore.newGame(1, 0) == 1);
        SudokuCore.selectCell(0, 0);
        /* move to empty */
        i = 0;
        while (i < 81) {
            if (SudokuCore.getGiven(i) == 0) {
                SudokuCore.selectCell(i / 9, i % 9);
                SudokuCore.inputDigit(SudokuCore.getSolutionDigit(i));
                break;
            }
            i = i + 1;
        }
        SudokuCore.tick(1000);
        SudokuCore.tick(2500);
        int el1 = SudokuCore.getElapsedMs();
        String snap = boardString();
        String save = saveString();
        ok("save-header", save.charAt(0) == 74 && save.charAt(4) == 75);

        /* Corrupt save must not clobber */
        ok("newGame-protect", SudokuCore.newGame(0, 1) == 1);
        String before = boardString();
        /* Short / wrong-version payloads must fail without clobbering state. */
        int bad = SudokuCore.loadSave("JNSDK9\nbad-save-too-short!!!!!");
        ok("bad-save", bad == 0 && (SudokuCore.getLastError() == 5 || SudokuCore.getLastError() == 6));
        ok("board-unchanged", strEq(boardString(), before));

        ok("good-load", SudokuCore.loadSave(save) == 1);
        ok("load-board", strEq(boardString(), snap));
        ok("load-elapsed", SudokuCore.getElapsedMs() == el1);

        /* Pause timing */
        ok("newGame-pause", SudokuCore.newGame(0, 0) == 1);
        SudokuCore.tick(100);
        SudokuCore.tick(200);
        ok("pause", SudokuCore.pause() == 1);
        SudokuCore.tick(500);
        SudokuCore.tick(900);
        int elPause = SudokuCore.getElapsedMs();
        ok("paused-no-accumulate", elPause == 100);
        SudokuCore.resume();
        SudokuCore.tick(1000);
        SudokuCore.tick(1100);
        ok("resume-accumulate", SudokuCore.getElapsedMs() == 200);

        /* New action clears redo */
        ok("newGame-redo-clear", SudokuCore.newGame(0, 0) == 1);
        i = 0;
        while (i < 81) {
            if (SudokuCore.getGiven(i) == 0) {
                SudokuCore.selectCell(i / 9, i % 9);
                break;
            }
            i = i + 1;
        }
        SudokuCore.inputDigit(1);
        SudokuCore.undo();
        ok("can-redo", SudokuCore.canRedo() == 1);
        SudokuCore.inputDigit(2);
        ok("redo-cleared", SudokuCore.canRedo() == 0);

        /* GC stress: many new games */
        int g = 0;
        while (g < 50) {
            SudokuCore.newGame(g % 3, g);
            g = g + 1;
        }
        ok("gc-survived", SudokuCore.newGame(0, 0) == 1);

        /* Stage-2 generate: bounded; OK if fails, but seed path must be deterministic when succeeds */
        int g1 = SudokuCore.generate(42, 0);
        if (g1 == 1) {
            String a = boardString();
            int g2 = SudokuCore.generate(42, 0);
            ok("generate-deterministic", g2 == 1 && strEq(boardString(), a));
        } else {
            ok("generate-fail-explicit", SudokuCore.getLastError() == 7);
        }

        System.out.println("RESULT passed=" + passed + " failed=" + failed);
        if (failed != 0) {
            System.out.println("FAIL_EXIT");
        }
    }
}
