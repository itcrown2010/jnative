/**
 * Shared JNative Sudoku game core.
 *
 * Rules, board state, solver, hints, undo/redo, and save validation live here.
 * UI layers must not reimplement conflict / solve / completion logic.
 *
 * wasm32-browser constraints observed:
 *   - String: length / charAt only (no substring)
 *   - Prefer static state + int arrays
 *   - Keep exports as static int methods for JS/WASM shells
 *
 * Error codes (getLastError):
 *   0 OK
 *   1 invalid argument
 *   2 fixed cell
 *   3 paused / wrong state
 *   4 no hint available
 *   5 save invalid / incompatible
 *   6 save too large
 *   7 generate failed (stage-2)
 *   8 not unique / unsolvable (diagnostics)
 */
public class SudokuCore {
    public static final int SAVE_VERSION = 1;
    public static final int MAX_SAVE_CHARS = 8192;
    public static final int MAX_HISTORY = 128;
    public static final int GEN_MAX_ATTEMPTS = 40;
    public static final int GEN_MAX_STEPS = 200000;

    public static final int DIFF_EASY = 0;
    public static final int DIFF_MEDIUM = 1;
    public static final int DIFF_HARD = 2;

    public static final int ST_PLAYING = 0;
    public static final int ST_PAUSED = 1;
    public static final int ST_WON = 2;

    public static int lastError;

    /* Arrays are lazy-initialized: wasm clinit `static int[] a = new int[N]` is broken
     * (see docs/repro/05-wasm-static-array-init). */
    static int[] values;
    static int[] given;
    static int[] notes;
    static int[] conflict;
    static int[] solution;

    static int selected = 0;
    static int noteMode = 0;
    static int status = 0;
    static int puzzleId = 0;
    static int difficulty = 0;
    static int elapsedMs = 0;
    static int lastTickMs = 0;
    static int tickValid = 0;

    static int[] histCell;
    static int[] histPrevVal;
    static int[] histNewVal;
    static int[] histPrevNotes;
    static int[] histNewNotes;
    static int histLen = 0;
    static int histPos = 0;

    static int[] saveBuf;
    static int saveLen = 0;

    static int[] work;
    static int[] work2;
    static int solveSteps = 0;
    static int genSteps = 0;
    static int rngState = 1;
    /* Explicit backtrack stacks — wasm recursion depth is too shallow for sudoku DFS. */
    static int[] btIdx;
    static int[] btDigit;
    static int ready = 0;

    static void ensureInit() {
        if (ready != 0) {
            return;
        }
        values = new int[81];
        given = new int[81];
        notes = new int[81];
        conflict = new int[81];
        solution = new int[81];
        histCell = new int[MAX_HISTORY];
        histPrevVal = new int[MAX_HISTORY];
        histNewVal = new int[MAX_HISTORY];
        histPrevNotes = new int[MAX_HISTORY];
        histNewNotes = new int[MAX_HISTORY];
        saveBuf = new int[MAX_SAVE_CHARS];
        work = new int[81];
        work2 = new int[81];
        btIdx = new int[81];
        btDigit = new int[81];
        ready = 1;
    }

    /* Catalog blob: 9 puzzles × 81 digits (0 empty). Index bands:
     *   0..2 easy, 3..5 medium, 6..8 hard.
     * Each entry verified unique-solution by SudokuCore solver.
     * Difficulty labels are catalog bands (clue-target selection bands),
     * NOT a claim of human solving grade. See README.
     * Note: new String[]{...} array initializers are unsupported — use a blob. */
    /* Puzzle strings are returned as *local* literals — wasm static String fields
     * from clinit are unreliable (same family as static array init). */

    public static int getLastError() {
        ensureInit();
        return lastError;
    }

    public static int puzzleCount(int diff) {
        ensureInit();
        if (diff < 0 || diff > 2) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return 3;
    }

    public static int newGame(int diff, int index) {
        ensureInit();
        if (diff < 0 || diff > 2) {
            lastError = 1;
            return 0;
        }
        int idx = index;
        if (idx < 0) {
            idx = 0;
        }
        idx = idx % 3;
        int base = diff * 3 + idx;
        return loadPuzzleFromCatalog(base, diff, idx);
    }

    static String catalogPuzzle(int puzzleIndex) {
        if (puzzleIndex == 0) {
            return "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
        }
        if (puzzleIndex == 1) {
            return "000260701680070090190004500820100040004602900050003028009300074040050036703018000";
        }
        if (puzzleIndex == 2) {
            return "609200748450630090010000005394005007175309000006004000980470506067593000043000209";
        }
        if (puzzleIndex == 3) {
            return "000000907000420180000705026100904000050000040000507009920108000034059000507000000";
        }
        if (puzzleIndex == 4) {
            return "590120700080305000021000600005000460038061000210074580000000800040000200060090004";
        }
        if (puzzleIndex == 5) {
            return "080030002000720006001840300417306000800000100900000060000403020002907003600180004";
        }
        if (puzzleIndex == 6) {
            return "800000000003600000070090200050007000000045700000100030001000068008500010090000400";
        }
        if (puzzleIndex == 7) {
            return "005300000800000020070010500400005300010070006003200080060500009004000030000009700";
        }
        if (puzzleIndex == 8) {
            return "000003090000901800061057004000000180050006000600738500070000050000020900002000000";
        }
        return null;
    }

    static int loadPuzzleFromCatalog(int puzzleIndex, int diff, int id) {
        ensureInit();
        String cat = catalogPuzzle(puzzleIndex);
        if (cat == null || cat.length() != 81) {
            lastError = 1;
            return 0;
        }
        int i = 0;
        while (i < 81) {
            int c = cat.charAt(i);
            int d = 0;
            if (c >= 49 && c <= 57) {
                d = c - 48;
            } else if (c == 48 || c == 46) {
                d = 0;
            } else {
                lastError = 1;
                return 0;
            }
            values[i] = d;
            if (d != 0) {
                given[i] = 1;
            } else {
                given[i] = 0;
            }
            notes[i] = 0;
            conflict[i] = 0;
            i = i + 1;
        }
        copyBoard(values, work);
        int sols = countSolutionsBoard(work, 2);
        if (sols != 1) {
            lastError = 8;
            return 0;
        }
        copyBoard(values, work);
        if (solveBoard(work) == 0) {
            lastError = 8;
            return 0;
        }
        copyBoard(work, solution);
        difficulty = diff;
        puzzleId = id;
        selected = 0;
        noteMode = 0;
        status = ST_PLAYING;
        elapsedMs = 0;
        tickValid = 0;
        histLen = 0;
        histPos = 0;
        recomputeConflicts();
        lastError = 0;
        return 1;
    }

    public static int loadPuzzleString(String p, int diff, int id) {
        ensureInit();
        ensureInit();
        if (p == null || p.length() != 81) {
            lastError = 1;
            return 0;
        }
        int i = 0;
        while (i < 81) {
            int c = p.charAt(i);
            int d = 0;
            if (c >= 49 && c <= 57) {
                d = c - 48;
            } else if (c == 48 || c == 46) {
                d = 0;
            } else {
                lastError = 1;
                return 0;
            }
            values[i] = d;
            if (d != 0) {
                given[i] = 1;
            } else {
                given[i] = 0;
            }
            notes[i] = 0;
            conflict[i] = 0;
            i = i + 1;
        }
        copyBoard(values, work);
        int sols = countSolutionsBoard(work, 2);
        if (sols != 1) {
            lastError = 8;
            return 0;
        }
        copyBoard(values, work);
        if (solveBoard(work) == 0) {
            lastError = 8;
            return 0;
        }
        copyBoard(work, solution);
        difficulty = diff;
        puzzleId = id;
        selected = 0;
        noteMode = 0;
        status = ST_PLAYING;
        elapsedMs = 0;
        tickValid = 0;
        histLen = 0;
        histPos = 0;
        recomputeConflicts();
        lastError = 0;
        return 1;
    }

    public static int selectCell(int row, int col) {
        ensureInit();
        if (status == ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        if (row < 0 || row > 8 || col < 0 || col > 8) {
            lastError = 1;
            return 0;
        }
        selected = row * 9 + col;
        lastError = 0;
        return 1;
    }

    public static int moveSelection(int dRow, int dCol) {
        ensureInit();
        if (status == ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        int r = selected / 9;
        int c = selected % 9;
        r = r + dRow;
        c = c + dCol;
        if (r < 0) {
            r = 0;
        }
        if (r > 8) {
            r = 8;
        }
        if (c < 0) {
            c = 0;
        }
        if (c > 8) {
            c = 8;
        }
        selected = r * 9 + c;
        lastError = 0;
        return 1;
    }

    public static int toggleNoteMode() {
        ensureInit();
        if (status == ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        if (noteMode == 0) {
            noteMode = 1;
        } else {
            noteMode = 0;
        }
        lastError = 0;
        return noteMode;
    }

    public static int setNoteMode(int on) {
        ensureInit();
        if (status == ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        if (on != 0) {
            noteMode = 1;
        } else {
            noteMode = 0;
        }
        lastError = 0;
        return noteMode;
    }

    public static int inputDigit(int d) {
        ensureInit();
        if (status == ST_PAUSED || status == ST_WON) {
            lastError = 3;
            return 0;
        }
        if (d < 1 || d > 9) {
            lastError = 1;
            return 0;
        }
        int idx = selected;
        if (given[idx] != 0) {
            lastError = 2;
            return 0;
        }
        if (noteMode != 0) {
            return toggleNoteAt(idx, d);
        }
        int prevV = values[idx];
        int prevN = notes[idx];
        int newV = d;
        int newN = 0;
        if (prevV == newV && prevN == newN) {
            lastError = 0;
            return 1;
        }
        pushHistory(idx, prevV, newV, prevN, newN);
        values[idx] = newV;
        notes[idx] = newN;
        recomputeConflicts();
        checkWon();
        lastError = 0;
        return 1;
    }

    public static int clearCell() {
        ensureInit();
        if (status == ST_PAUSED || status == ST_WON) {
            lastError = 3;
            return 0;
        }
        int idx = selected;
        if (given[idx] != 0) {
            lastError = 2;
            return 0;
        }
        int prevV = values[idx];
        int prevN = notes[idx];
        if (prevV == 0 && prevN == 0) {
            lastError = 0;
            return 1;
        }
        pushHistory(idx, prevV, 0, prevN, 0);
        values[idx] = 0;
        notes[idx] = 0;
        recomputeConflicts();
        lastError = 0;
        return 1;
    }

    static int toggleNoteAt(int idx, int d) {
        int prevV = values[idx];
        int prevN = notes[idx];
        int bit = 1 << (d - 1);
        int newN = prevN;
        if ((newN & bit) != 0) {
            newN = newN - bit;
        } else {
            newN = newN + bit;
        }
        int newV = 0;
        if (prevV == newV && prevN == newN) {
            lastError = 0;
            return 1;
        }
        pushHistory(idx, prevV, newV, prevN, newN);
        values[idx] = newV;
        notes[idx] = newN;
        recomputeConflicts();
        lastError = 0;
        return 1;
    }

    static void pushHistory(int cell, int pv, int nv, int pn, int nn) {
        if (histPos < histLen) {
            histLen = histPos;
        }
        if (histLen >= MAX_HISTORY) {
            int i = 0;
            while (i < MAX_HISTORY - 1) {
                histCell[i] = histCell[i + 1];
                histPrevVal[i] = histPrevVal[i + 1];
                histNewVal[i] = histNewVal[i + 1];
                histPrevNotes[i] = histPrevNotes[i + 1];
                histNewNotes[i] = histNewNotes[i + 1];
                i = i + 1;
            }
            histLen = MAX_HISTORY - 1;
            histPos = histLen;
        }
        histCell[histLen] = cell;
        histPrevVal[histLen] = pv;
        histNewVal[histLen] = nv;
        histPrevNotes[histLen] = pn;
        histNewNotes[histLen] = nn;
        histLen = histLen + 1;
        histPos = histLen;
    }

    public static int undo() {
        ensureInit();
        if (status == ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        if (histPos <= 0) {
            lastError = 1;
            return 0;
        }
        histPos = histPos - 1;
        int cell = histCell[histPos];
        values[cell] = histPrevVal[histPos];
        notes[cell] = histPrevNotes[histPos];
        if (status == ST_WON) {
            status = ST_PLAYING;
        }
        recomputeConflicts();
        lastError = 0;
        return 1;
    }

    public static int redo() {
        ensureInit();
        if (status == ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        if (histPos >= histLen) {
            lastError = 1;
            return 0;
        }
        int cell = histCell[histPos];
        values[cell] = histNewVal[histPos];
        notes[cell] = histNewNotes[histPos];
        histPos = histPos + 1;
        recomputeConflicts();
        checkWon();
        lastError = 0;
        return 1;
    }

    public static int hint() {
        ensureInit();
        if (status == ST_PAUSED || status == ST_WON) {
            lastError = 3;
            return 0;
        }
        int i = 0;
        while (i < 81) {
            if (given[i] == 0 && values[i] != solution[i]) {
                selected = i;
                int prevV = values[i];
                int prevN = notes[i];
                int newV = solution[i];
                pushHistory(i, prevV, newV, prevN, 0);
                values[i] = newV;
                notes[i] = 0;
                recomputeConflicts();
                checkWon();
                lastError = 0;
                return 1;
            }
            i = i + 1;
        }
        lastError = 4;
        return 0;
    }

    public static int pause() {
        ensureInit();
        if (status != ST_PLAYING) {
            lastError = 3;
            return 0;
        }
        status = ST_PAUSED;
        tickValid = 0;
        lastError = 0;
        return 1;
    }

    public static int resume() {
        ensureInit();
        if (status != ST_PAUSED) {
            lastError = 3;
            return 0;
        }
        status = ST_PLAYING;
        tickValid = 0;
        lastError = 0;
        return 1;
    }

    /** Platform supplies monotonic milliseconds. Paused time is not accumulated. */
    public static int tick(int nowMs) {
        ensureInit();
        if (nowMs < 0) {
            lastError = 1;
            return elapsedMs;
        }
        if (status == ST_PLAYING && tickValid != 0) {
            int delta = nowMs - lastTickMs;
            if (delta > 0 && delta < 60000) {
                elapsedMs = elapsedMs + delta;
            }
        }
        lastTickMs = nowMs;
        tickValid = 1;
        lastError = 0;
        return elapsedMs;
    }

    public static int getElapsedMs() {
        ensureInit();
        return elapsedMs;
    }

    public static int getStatus() {
        ensureInit();
        return status;
    }

    public static int getSelected() {
        ensureInit();
        return selected;
    }

    public static int getNoteMode() {
        ensureInit();
        return noteMode;
    }

    public static int getPuzzleId() {
        ensureInit();
        return puzzleId;
    }

    public static int getDifficulty() {
        ensureInit();
        return difficulty;
    }

    public static int getValue(int idx) {
        ensureInit();
        if (idx < 0 || idx > 80) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return values[idx];
    }

    public static int getGiven(int idx) {
        ensureInit();
        if (idx < 0 || idx > 80) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return given[idx];
    }

    public static int getNotes(int idx) {
        ensureInit();
        if (idx < 0 || idx > 80) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return notes[idx];
    }

    public static int getConflict(int idx) {
        ensureInit();
        if (idx < 0 || idx > 80) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return conflict[idx];
    }

    public static int getSolutionDigit(int idx) {
        ensureInit();
        if (idx < 0 || idx > 80) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return solution[idx];
    }

    public static int canUndo() {
        ensureInit();
        if (histPos > 0) {
            return 1;
        }
        return 0;
    }

    public static int canRedo() {
        ensureInit();
        if (histPos < histLen) {
            return 1;
        }
        return 0;
    }

    public static int isComplete() {
        ensureInit();
        if (status == ST_WON) {
            return 1;
        }
        return 0;
    }

    /** Highlight helpers: same digit as selected (0 if empty). */
    public static int getHighlightDigit() {
        ensureInit();
        int v = values[selected];
        return v;
    }

    public static int sameRow(int idx) {
        ensureInit();
        if (selected / 9 == idx / 9) {
            return 1;
        }
        return 0;
    }

    public static int sameCol(int idx) {
        ensureInit();
        if (selected % 9 == idx % 9) {
            return 1;
        }
        return 0;
    }

    public static int sameBox(int idx) {
        ensureInit();
        int sr = selected / 9;
        int sc = selected % 9;
        int r = idx / 9;
        int c = idx % 9;
        if (sr / 3 == r / 3 && sc / 3 == c / 3) {
            return 1;
        }
        return 0;
    }

    static void checkWon() {
        int i = 0;
        while (i < 81) {
            if (values[i] == 0) {
                return;
            }
            if (conflict[i] != 0) {
                return;
            }
            i = i + 1;
        }
        if (boardValidComplete(values) != 0) {
            status = ST_WON;
            tickValid = 0;
        }
    }

    public static int boardValidComplete(int[] b) {
        ensureInit();
        int i = 0;
        while (i < 81) {
            if (b[i] < 1 || b[i] > 9) {
                return 0;
            }
            i = i + 1;
        }
        int n = 0;
        while (n < 9) {
            int rowMask = 0;
            int colMask = 0;
            int boxMask = 0;
            int k = 0;
            while (k < 9) {
                int rv = b[n * 9 + k];
                int cv = b[k * 9 + n];
                int br = (n / 3) * 3 + k / 3;
                int bc = (n % 3) * 3 + k % 3;
                int bv = b[br * 9 + bc];
                int rb = 1 << rv;
                int cb = 1 << cv;
                int bb = 1 << bv;
                if ((rowMask & rb) != 0 || (colMask & cb) != 0 || (boxMask & bb) != 0) {
                    return 0;
                }
                rowMask = rowMask + rb;
                colMask = colMask + cb;
                boxMask = boxMask + bb;
                k = k + 1;
            }
            n = n + 1;
        }
        return 1;
    }

    static void recomputeConflicts() {
        int i = 0;
        while (i < 81) {
            conflict[i] = 0;
            i = i + 1;
        }
        i = 0;
        while (i < 81) {
            int v = values[i];
            if (v == 0) {
                i = i + 1;
                continue;
            }
            int r = i / 9;
            int c = i % 9;
            int j = 0;
            while (j < 9) {
                int ri = r * 9 + j;
                if (ri != i && values[ri] == v) {
                    conflict[i] = 1;
                    conflict[ri] = 1;
                }
                int ci = j * 9 + c;
                if (ci != i && values[ci] == v) {
                    conflict[i] = 1;
                    conflict[ci] = 1;
                }
                j = j + 1;
            }
            int br = (r / 3) * 3;
            int bc = (c / 3) * 3;
            int dr = 0;
            while (dr < 3) {
                int dc = 0;
                while (dc < 3) {
                    int bi = (br + dr) * 9 + (bc + dc);
                    if (bi != i && values[bi] == v) {
                        conflict[i] = 1;
                        conflict[bi] = 1;
                    }
                    dc = dc + 1;
                }
                dr = dr + 1;
            }
            i = i + 1;
        }
    }

    static void copyBoard(int[] from, int[] to) {
        int i = 0;
        while (i < 81) {
            to[i] = from[i];
            i = i + 1;
        }
    }

    public static int countSolutionsPuzzle(String p) {
        ensureInit();
        if (p == null || p.length() != 81) {
            lastError = 1;
            return 0;
        }
        int i = 0;
        while (i < 81) {
            int c = p.charAt(i);
            if (c >= 49 && c <= 57) {
                work[i] = c - 48;
            } else if (c == 48 || c == 46) {
                work[i] = 0;
            } else {
                lastError = 1;
                return 0;
            }
            i = i + 1;
        }
        lastError = 0;
        return countSolutionsBoard(work, 2);
    }

    static int countSolutionsBoard(int[] board, int limit) {
        solveSteps = 0;
        int found = 0;
        int sp = 0;
        btIdx[0] = findEmpty(board);
        btDigit[0] = 1;
        if (btIdx[0] < 0) {
            return 1;
        }
        while (sp >= 0) {
            solveSteps = solveSteps + 1;
            if (solveSteps > 2000000 || found >= limit) {
                return found;
            }
            int idx = btIdx[sp];
            int d = btDigit[sp];
            board[idx] = 0;
            if (d > 9) {
                sp = sp - 1;
                continue;
            }
            if (canPlace(board, idx, d) == 0) {
                btDigit[sp] = d + 1;
                continue;
            }
            board[idx] = d;
            int next = findEmpty(board);
            if (next < 0) {
                found = found + 1;
                board[idx] = 0;
                btDigit[sp] = d + 1;
                continue;
            }
            btDigit[sp] = d + 1;
            sp = sp + 1;
            btIdx[sp] = next;
            btDigit[sp] = 1;
        }
        return found;
    }

    static int solveBoard(int[] board) {
        solveSteps = 0;
        int sp = 0;
        btIdx[0] = findEmpty(board);
        btDigit[0] = 1;
        if (btIdx[0] < 0) {
            return 1;
        }
        while (sp >= 0) {
            solveSteps = solveSteps + 1;
            if (solveSteps > 2000000) {
                return 0;
            }
            int idx = btIdx[sp];
            int d = btDigit[sp];
            board[idx] = 0;
            if (d > 9) {
                sp = sp - 1;
                continue;
            }
            if (canPlace(board, idx, d) == 0) {
                btDigit[sp] = d + 1;
                continue;
            }
            board[idx] = d;
            int next = findEmpty(board);
            if (next < 0) {
                return 1;
            }
            btDigit[sp] = d + 1;
            sp = sp + 1;
            if (sp >= 81) {
                return 0;
            }
            btIdx[sp] = next;
            btDigit[sp] = 1;
        }
        return 0;
    }

    static int findEmpty(int[] board) {
        int i = 0;
        while (i < 81) {
            if (board[i] == 0) {
                return i;
            }
            i = i + 1;
        }
        return 0 - 1;
    }

    static int canPlace(int[] board, int idx, int d) {
        int r = idx / 9;
        int c = idx % 9;
        int j = 0;
        while (j < 9) {
            if (board[r * 9 + j] == d) {
                return 0;
            }
            if (board[j * 9 + c] == d) {
                return 0;
            }
            j = j + 1;
        }
        int br = (r / 3) * 3;
        int bc = (c / 3) * 3;
        int dr = 0;
        while (dr < 3) {
            int dc = 0;
            while (dc < 3) {
                if (board[(br + dr) * 9 + (bc + dc)] == d) {
                    return 0;
                }
                dc = dc + 1;
            }
            dr = dr + 1;
        }
        return 1;
    }

    /* ---- Save / Load (ASCII, versioned) ---- */

    public static int prepareSave() {
        ensureInit();
        saveLen = 0;
        emitStr("JNSDK");
        emitInt(SAVE_VERSION);
        emitChar(10);
        emitInt(puzzleId);
        emitChar(10);
        emitInt(difficulty);
        emitChar(10);
        emitInt(status);
        emitChar(10);
        emitInt(elapsedMs);
        emitChar(10);
        emitInt(noteMode);
        emitChar(10);
        emitInt(selected);
        emitChar(10);
        emitInt(histPos);
        emitChar(10);
        emitInt(histLen);
        emitChar(10);
        int i = 0;
        while (i < 81) {
            emitChar(48 + given[i]);
            i = i + 1;
        }
        emitChar(10);
        i = 0;
        while (i < 81) {
            emitChar(48 + values[i]);
            i = i + 1;
        }
        emitChar(10);
        i = 0;
        while (i < 81) {
            emitInt(notes[i]);
            emitChar(44);
            i = i + 1;
        }
        emitChar(10);
        i = 0;
        while (i < histLen) {
            emitInt(histCell[i]);
            emitChar(44);
            emitInt(histPrevVal[i]);
            emitChar(44);
            emitInt(histNewVal[i]);
            emitChar(44);
            emitInt(histPrevNotes[i]);
            emitChar(44);
            emitInt(histNewNotes[i]);
            emitChar(59);
            i = i + 1;
        }
        emitChar(10);
        lastError = 0;
        return saveLen;
    }

    public static int getSaveLen() {
        ensureInit();
        return saveLen;
    }

    public static int getSaveChar(int i) {
        ensureInit();
        if (i < 0 || i >= saveLen) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return saveBuf[i];
    }

    static void emitChar(int c) {
        if (saveLen < MAX_SAVE_CHARS) {
            saveBuf[saveLen] = c;
            saveLen = saveLen + 1;
        }
    }

    static void emitStr(String s) {
        int i = 0;
        while (i < s.length()) {
            emitChar(s.charAt(i));
            i = i + 1;
        }
    }

    static void emitInt(int v) {
        if (v < 0) {
            emitChar(45);
            v = 0 - v;
        }
        if (v == 0) {
            emitChar(48);
            return;
        }
        int[] digs = new int[12];
        int n = 0;
        while (v > 0) {
            digs[n] = v % 10;
            v = v / 10;
            n = n + 1;
        }
        while (n > 0) {
            n = n - 1;
            emitChar(48 + digs[n]);
        }
    }

    public static int loadSave(String data) {
        ensureInit();
        if (data == null) {
            lastError = 5;
            return 0;
        }
        int n = data.length();
        if (n < 20 || n > MAX_SAVE_CHARS) {
            lastError = 6;
            return 0;
        }
        int[] pos = new int[1];
        pos[0] = 0;
        if (matchLiteral(data, pos, "JNSDK") == 0) {
            lastError = 5;
            return 0;
        }
        int ver = parseIntLine(data, pos);
        if (ver != SAVE_VERSION) {
            lastError = 5;
            return 0;
        }
        int pid = parseIntLine(data, pos);
        int diff = parseIntLine(data, pos);
        int st = parseIntLine(data, pos);
        int el = parseIntLine(data, pos);
        int nm = parseIntLine(data, pos);
        int sel = parseIntLine(data, pos);
        int hp = parseIntLine(data, pos);
        int hl = parseIntLine(data, pos);
        if (lastError != 0) {
            lastError = 5;
            return 0;
        }
        if (hl < 0 || hl > MAX_HISTORY || hp < 0 || hp > hl) {
            lastError = 5;
            return 0;
        }
        if (sel < 0 || sel > 80 || diff < 0 || diff > 2) {
            lastError = 5;
            return 0;
        }
        if (st != ST_PLAYING && st != ST_PAUSED && st != ST_WON) {
            lastError = 5;
            return 0;
        }
        if (el < 0) {
            lastError = 5;
            return 0;
        }

        int[] gTmp = new int[81];
        int[] vTmp = new int[81];
        int[] nTmp = new int[81];
        if (parseDigitLine(data, pos, gTmp) == 0) {
            lastError = 5;
            return 0;
        }
        if (parseDigitLine(data, pos, vTmp) == 0) {
            lastError = 5;
            return 0;
        }
        if (parseNotesLine(data, pos, nTmp) == 0) {
            lastError = 5;
            return 0;
        }

        int i = 0;
        while (i < 81) {
            if (gTmp[i] != 0 && gTmp[i] != 1) {
                lastError = 5;
                return 0;
            }
            if (vTmp[i] < 0 || vTmp[i] > 9) {
                lastError = 5;
                return 0;
            }
            if (nTmp[i] < 0 || nTmp[i] > 511) {
                lastError = 5;
                return 0;
            }
            if (gTmp[i] != 0 && vTmp[i] == 0) {
                lastError = 5;
                return 0;
            }
            i = i + 1;
        }

        int[] hc = new int[MAX_HISTORY];
        int[] hpv = new int[MAX_HISTORY];
        int[] hnv = new int[MAX_HISTORY];
        int[] hpn = new int[MAX_HISTORY];
        int[] hnn = new int[MAX_HISTORY];
        if (parseHistoryLine(data, pos, hl, hc, hpv, hnv, hpn, hnn) == 0) {
            lastError = 5;
            return 0;
        }

        /* Validate uniqueness of the implied initial puzzle (givens). */
        i = 0;
        while (i < 81) {
            if (gTmp[i] != 0) {
                work[i] = vTmp[i];
            } else {
                work[i] = 0;
            }
            i = i + 1;
        }
        int sols = countSolutionsBoard(work, 2);
        if (sols != 1) {
            lastError = 5;
            return 0;
        }
        copyBoard(work, work2);
        if (solveBoard(work2) == 0) {
            lastError = 5;
            return 0;
        }

        /* Commit only after full validation — do not clobber a good game on bad save. */
        i = 0;
        while (i < 81) {
            given[i] = gTmp[i];
            values[i] = vTmp[i];
            notes[i] = nTmp[i];
            solution[i] = work2[i];
            i = i + 1;
        }
        i = 0;
        while (i < hl) {
            histCell[i] = hc[i];
            histPrevVal[i] = hpv[i];
            histNewVal[i] = hnv[i];
            histPrevNotes[i] = hpn[i];
            histNewNotes[i] = hnn[i];
            i = i + 1;
        }
        histLen = hl;
        histPos = hp;
        puzzleId = pid;
        difficulty = diff;
        status = st;
        elapsedMs = el;
        noteMode = nm;
        selected = sel;
        tickValid = 0;
        recomputeConflicts();
        if (status != ST_WON) {
            checkWon();
        }
        lastError = 0;
        return 1;
    }

    static int matchLiteral(String data, int[] pos, String lit) {
        int i = 0;
        while (i < lit.length()) {
            if (pos[0] >= data.length() || data.charAt(pos[0]) != lit.charAt(i)) {
                return 0;
            }
            pos[0] = pos[0] + 1;
            i = i + 1;
        }
        return 1;
    }

    static int parseIntLine(String data, int[] pos) {
        lastError = 0;
        int sign = 1;
        if (pos[0] < data.length() && data.charAt(pos[0]) == 45) {
            sign = 0 - 1;
            pos[0] = pos[0] + 1;
        }
        int acc = 0;
        int digits = 0;
        while (pos[0] < data.length()) {
            int c = data.charAt(pos[0]);
            if (c == 10) {
                pos[0] = pos[0] + 1;
                break;
            }
            if (c < 48 || c > 57) {
                lastError = 5;
                return 0;
            }
            acc = acc * 10 + (c - 48);
            digits = digits + 1;
            pos[0] = pos[0] + 1;
        }
        if (digits == 0) {
            lastError = 5;
            return 0;
        }
        return acc * sign;
    }

    static int parseDigitLine(String data, int[] pos, int[] out) {
        int i = 0;
        while (i < 81) {
            if (pos[0] >= data.length()) {
                return 0;
            }
            int c = data.charAt(pos[0]);
            if (c < 48 || c > 57) {
                return 0;
            }
            out[i] = c - 48;
            pos[0] = pos[0] + 1;
            i = i + 1;
        }
        if (pos[0] < data.length() && data.charAt(pos[0]) == 10) {
            pos[0] = pos[0] + 1;
        }
        return 1;
    }

    static int parseNotesLine(String data, int[] pos, int[] out) {
        int i = 0;
        while (i < 81) {
            int acc = 0;
            int digits = 0;
            while (pos[0] < data.length()) {
                int c = data.charAt(pos[0]);
                if (c == 44 || c == 10) {
                    break;
                }
                if (c < 48 || c > 57) {
                    return 0;
                }
                acc = acc * 10 + (c - 48);
                digits = digits + 1;
                pos[0] = pos[0] + 1;
            }
            if (digits == 0) {
                return 0;
            }
            out[i] = acc;
            if (pos[0] < data.length() && data.charAt(pos[0]) == 44) {
                pos[0] = pos[0] + 1;
            }
            i = i + 1;
        }
        if (pos[0] < data.length() && data.charAt(pos[0]) == 10) {
            pos[0] = pos[0] + 1;
        }
        return 1;
    }

    static int parseHistoryLine(String data, int[] pos, int hl, int[] hc, int[] hpv, int[] hnv, int[] hpn, int[] hnn) {
        if (hl == 0) {
            if (pos[0] < data.length() && data.charAt(pos[0]) == 10) {
                pos[0] = pos[0] + 1;
            }
            return 1;
        }
        int i = 0;
        while (i < hl) {
            hc[i] = parseNum(data, pos, 44);
            if (lastError != 0) {
                return 0;
            }
            hpv[i] = parseNum(data, pos, 44);
            if (lastError != 0) {
                return 0;
            }
            hnv[i] = parseNum(data, pos, 44);
            if (lastError != 0) {
                return 0;
            }
            hpn[i] = parseNum(data, pos, 44);
            if (lastError != 0) {
                return 0;
            }
            hnn[i] = parseNum(data, pos, 59);
            if (lastError != 0) {
                return 0;
            }
            if (hc[i] < 0 || hc[i] > 80) {
                return 0;
            }
            i = i + 1;
        }
        if (pos[0] < data.length() && data.charAt(pos[0]) == 10) {
            pos[0] = pos[0] + 1;
        }
        return 1;
    }

    static int parseNum(String data, int[] pos, int stop) {
        lastError = 0;
        int acc = 0;
        int digits = 0;
        while (pos[0] < data.length()) {
            int c = data.charAt(pos[0]);
            if (c == stop || c == 10) {
                break;
            }
            if (c < 48 || c > 57) {
                lastError = 5;
                return 0;
            }
            acc = acc * 10 + (c - 48);
            digits = digits + 1;
            pos[0] = pos[0] + 1;
        }
        if (digits == 0) {
            lastError = 5;
            return 0;
        }
        if (pos[0] < data.length() && data.charAt(pos[0]) == stop) {
            pos[0] = pos[0] + 1;
        }
        return acc;
    }

    /* ---- Stage-2 reproducible generation (bounded; may fail) ---- */

    public static int generate(int seed, int diff) {
        ensureInit();
        if (diff < 0 || diff > 2) {
            lastError = 1;
            return 0;
        }
        rngState = seed;
        if (rngState == 0) {
            rngState = 1;
        }
        genSteps = 0;
        int attempt = 0;
        int targetGivens = 36;
        if (diff == DIFF_MEDIUM) {
            targetGivens = 30;
        }
        if (diff == DIFF_HARD) {
            targetGivens = 24;
        }
        while (attempt < GEN_MAX_ATTEMPTS) {
            if (genSteps > GEN_MAX_STEPS) {
                lastError = 7;
                return 0;
            }
            if (fillRandomComplete(work) == 0) {
                attempt = attempt + 1;
                continue;
            }
            copyBoard(work, work2);
            digHoles(work2, targetGivens);
            copyBoard(work2, work);
            int sols = countSolutionsBoard(work, 2);
            if (sols == 1) {
                int i = 0;
                while (i < 81) {
                    values[i] = work2[i];
                    if (work2[i] != 0) {
                        given[i] = 1;
                    } else {
                        given[i] = 0;
                    }
                    notes[i] = 0;
                    i = i + 1;
                }
                copyBoard(work2, work);
                solveBoard(work);
                copyBoard(work, solution);
                difficulty = diff;
                puzzleId = 1000 + (seed & 65535);
                selected = 0;
                noteMode = 0;
                status = ST_PLAYING;
                elapsedMs = 0;
                tickValid = 0;
                histLen = 0;
                histPos = 0;
                recomputeConflicts();
                lastError = 0;
                return 1;
            }
            attempt = attempt + 1;
        }
        lastError = 7;
        return 0;
    }

    static int rngNext() {
        /* LCG: numerical recipes */
        rngState = rngState * 1664525 + 1013904223;
        if (rngState < 0) {
            rngState = 0 - rngState;
        }
        return rngState;
    }

    static int fillRandomComplete(int[] board) {
        int i = 0;
        while (i < 81) {
            board[i] = 0;
            i = i + 1;
        }
        return fillRandomRec(board);
    }

    static int fillRandomRec(int[] board) {
        genSteps = genSteps + 1;
        if (genSteps > GEN_MAX_STEPS) {
            return 0;
        }
        int idx = findEmpty(board);
        if (idx < 0) {
            return 1;
        }
        int[] order = new int[9];
        int k = 0;
        while (k < 9) {
            order[k] = k + 1;
            k = k + 1;
        }
        k = 8;
        while (k > 0) {
            int j = rngNext() % (k + 1);
            int tmp = order[k];
            order[k] = order[j];
            order[j] = tmp;
            k = k - 1;
        }
        k = 0;
        while (k < 9) {
            int d = order[k];
            if (canPlace(board, idx, d) != 0) {
                board[idx] = d;
                if (fillRandomRec(board) != 0) {
                    return 1;
                }
                board[idx] = 0;
            }
            k = k + 1;
        }
        return 0;
    }

    static void digHoles(int[] board, int targetGivens) {
        int[] order = new int[81];
        int i = 0;
        while (i < 81) {
            order[i] = i;
            i = i + 1;
        }
        i = 80;
        while (i > 0) {
            int j = rngNext() % (i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
            i = i - 1;
        }
        int filled = 81;
        i = 0;
        while (i < 81 && filled > targetGivens) {
            int idx = order[i];
            int bak = board[idx];
            if (bak != 0) {
                board[idx] = 0;
                copyBoard(board, work);
                int sols = countSolutionsBoard(work, 2);
                if (sols != 1) {
                    board[idx] = bak;
                } else {
                    filled = filled - 1;
                }
            }
            i = i + 1;
        }
    }

    /** Snapshot board digits as 81-char codes into saveBuf without full save header. */
    public static int exportBoardDigits() {
        ensureInit();
        saveLen = 0;
        int i = 0;
        while (i < 81) {
            emitChar(48 + values[i]);
            i = i + 1;
        }
        lastError = 0;
        return saveLen;
    }

    public static void main(String[] args) {
        /* Retain exports for wasm32-browser DCE. */
        boolean keep = false;
        if (keep) {
            lastError = newGame(0, 0);
            lastError = selectCell(0, 0);
            lastError = moveSelection(0, 1);
            lastError = inputDigit(1);
            lastError = clearCell();
            lastError = toggleNoteMode();
            lastError = setNoteMode(0);
            lastError = undo();
            lastError = redo();
            lastError = hint();
            lastError = pause();
            lastError = resume();
            lastError = tick(0);
            lastError = getElapsedMs();
            lastError = getStatus();
            lastError = getSelected();
            lastError = getNoteMode();
            lastError = getPuzzleId();
            lastError = getDifficulty();
            lastError = getValue(0);
            lastError = getGiven(0);
            lastError = getNotes(0);
            lastError = getConflict(0);
            lastError = getSolutionDigit(0);
            lastError = canUndo();
            lastError = canRedo();
            lastError = isComplete();
            lastError = getHighlightDigit();
            lastError = sameRow(0);
            lastError = sameCol(0);
            lastError = sameBox(0);
            lastError = prepareSave();
            lastError = getSaveLen();
            lastError = getSaveChar(0);
            lastError = loadSave("x");
            lastError = countSolutionsPuzzle("0");
            lastError = generate(1, 0);
            lastError = puzzleCount(0);
            lastError = exportBoardDigits();
            lastError = getLastError();
            lastError = boardValidComplete(values);
            lastError = loadPuzzleString("0", 0, 0);
        }
    }
}
