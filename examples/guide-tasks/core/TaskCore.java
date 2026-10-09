/**
 * Shared task-list core for guide-tasks (desktop / web / later mobile).
 *
 * wasm32-browser constraints (same family as SudokuCore):
 *   - Prefer static int state + lazy arrays
 *   - String: length / charAt for load paths; titles stored as char codes
 *   - No lambdas in this file (WASM callable rejection)
 *
 * Error codes (getLastError):
 *   0 OK
 *   1 invalid argument / index
 *   2 empty title
 *   3 title too long
 *   4 capacity full
 *   5 save invalid
 * @author 张晓达
 */
public class TaskCore {
    public static final int MAX_TASKS = 64;
    public static final int TITLE_MAX = 48;
    public static final int SAVE_VERSION = 1;
    public static final int MAX_SAVE_CHARS = 8192;

    public static int lastError;

    static int[] titleChars;
    static int[] titleLen;
    static int[] done;
    static String[] titleStrCache;
    static int count;
    static int ready;

    static int[] draft;
    static int draftLen;

    static int[] saveBuf;
    static int saveLen;

    static void ensureInit() {
        if (ready != 0) {
            return;
        }
        titleChars = new int[MAX_TASKS * TITLE_MAX];
        titleLen = new int[MAX_TASKS];
        done = new int[MAX_TASKS];
        titleStrCache = new String[MAX_TASKS];
        draft = new int[TITLE_MAX];
        saveBuf = new int[MAX_SAVE_CHARS];
        count = 0;
        draftLen = 0;
        saveLen = 0;
        lastError = 0;
        ready = 1;
    }

    /** Map code unit to 1-char String for printable ASCII; else "?". */
    public static String unitToString(int c) {
        if (c == 10) {
            return "\n";
        }
        if (c == 34) {
            return "\"";
        }
        if (c == 39) {
            return "'";
        }
        if (c == 92) {
            return "\\";
        }
        if (c >= 32 && c <= 126) {
            String base = " !#$%&()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~";
            int i = 0;
            int cur = 32;
            while (cur <= 126) {
                if (cur != 34 && cur != 39 && cur != 92) {
                    if (cur == c) {
                        return base.substring(i, i + 1);
                    }
                    i = i + 1;
                }
                cur = cur + 1;
            }
        }
        return "?";
    }

    public static int getLastError() {
        return lastError;
    }

    public static int getCount() {
        ensureInit();
        lastError = 0;
        return count;
    }

    public static int clearDraft() {
        ensureInit();
        draftLen = 0;
        lastError = 0;
        return 0;
    }

    public static int appendDraftChar(int ch) {
        ensureInit();
        /* Accept BMP code units used by String.charAt. */
        if (ch <= 0 || ch > 65535) {
            lastError = 1;
            return 0;
        }
        if (draftLen >= TITLE_MAX) {
            lastError = 3;
            return 0;
        }
        draft[draftLen] = ch;
        draftLen = draftLen + 1;
        lastError = 0;
        return 1;
    }

    public static int getDraftLen() {
        ensureInit();
        return draftLen;
    }

    public static int validateDraft() {
        ensureInit();
        if (draftLen <= 0) {
            lastError = 2;
            return 0;
        }
        if (draftLen > TITLE_MAX) {
            lastError = 3;
            return 0;
        }
        lastError = 0;
        return 1;
    }

    public static int addFromDraft() {
        ensureInit();
        if (validateDraft() == 0) {
            return -1;
        }
        if (count >= MAX_TASKS) {
            lastError = 4;
            return -1;
        }
        int id = count;
        int base = id * TITLE_MAX;
        int i = 0;
        while (i < draftLen) {
            titleChars[base + i] = draft[i];
            i = i + 1;
        }
        titleLen[id] = draftLen;
        done[id] = 0;
        titleStrCache[id] = null;
        count = count + 1;
        draftLen = 0;
        lastError = 0;
        return id;
    }

    /** Desktop helper path: copy UTF-16 code units from a String. */
    public static int addTaskString(String title) {
        ensureInit();
        clearDraft();
        if (title == null) {
            lastError = 2;
            return -1;
        }
        int n = title.length();
        if (n <= 0) {
            lastError = 2;
            return -1;
        }
        int i = 0;
        while (i < n) {
            if (appendDraftChar(title.charAt(i)) == 0) {
                return -1;
            }
            i = i + 1;
        }
        int id = addFromDraft();
        if (id >= 0) {
            titleStrCache[id] = title;
        }
        return id;
    }

    public static int getTitleLen(int index) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return titleLen[index];
    }

    public static int getTitleChar(int index, int pos) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        if (pos < 0 || pos >= titleLen[index]) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return titleChars[index * TITLE_MAX + pos];
    }

    public static int getDone(int index) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return done[index];
    }

    public static int setDone(int index, int value) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        if (value != 0) {
            done[index] = 1;
        } else {
            done[index] = 0;
        }
        lastError = 0;
        return 1;
    }

    public static int toggleDone(int index) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        if (done[index] != 0) {
            done[index] = 0;
        } else {
            done[index] = 1;
        }
        lastError = 0;
        return 1;
    }

    public static int removeAt(int index) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        int i = index;
        while (i < count - 1) {
            copyTask(i + 1, i);
            i = i + 1;
        }
        count = count - 1;
        titleLen[count] = 0;
        done[count] = 0;
        lastError = 0;
        return 1;
    }

    public static int setTitleString(int index, String title) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return 0;
        }
        if (title == null || title.length() <= 0) {
            lastError = 2;
            return 0;
        }
        int n = title.length();
        if (n > TITLE_MAX) {
            lastError = 3;
            return 0;
        }
        int base = index * TITLE_MAX;
        int i = 0;
        while (i < n) {
            titleChars[base + i] = title.charAt(i);
            i = i + 1;
        }
        titleLen[index] = n;
        titleStrCache[index] = title;
        lastError = 0;
        return 1;
    }

    static void copyTask(int from, int to) {
        int fb = from * TITLE_MAX;
        int tb = to * TITLE_MAX;
        int n = titleLen[from];
        int i = 0;
        while (i < n) {
            titleChars[tb + i] = titleChars[fb + i];
            i = i + 1;
        }
        titleLen[to] = n;
        done[to] = done[from];
        titleStrCache[to] = titleStrCache[from];
    }

    /** Native UI display string (uses cache when set via addTaskString). */
    public static String titleString(int index) {
        ensureInit();
        if (index < 0 || index >= count) {
            lastError = 1;
            return "";
        }
        if (titleStrCache[index] != null) {
            lastError = 0;
            return titleStrCache[index];
        }
        int n = titleLen[index];
        int base = index * TITLE_MAX;
        String s = "";
        int i = 0;
        while (i < n) {
            s = s + unitToString(titleChars[base + i]);
            i = i + 1;
        }
        lastError = 0;
        return s;
    }

    public static int clearAll() {
        ensureInit();
        count = 0;
        draftLen = 0;
        int i = 0;
        while (i < MAX_TASKS) {
            titleStrCache[i] = null;
            i = i + 1;
        }
        lastError = 0;
        return 0;
    }

    static void emitChar(int ch) {
        if (saveLen < MAX_SAVE_CHARS) {
            saveBuf[saveLen] = ch;
            saveLen = saveLen + 1;
        }
    }

    static void emitInt(int v) {
        if (v < 0) {
            emitChar(45);
            v = 0 - v;
        }
        int[] dig = new int[12];
        int n = 0;
        if (v == 0) {
            dig[0] = 48;
            n = 1;
        } else {
            while (v > 0) {
                dig[n] = 48 + (v % 10);
                v = v / 10;
                n = n + 1;
            }
        }
        int i = n - 1;
        while (i >= 0) {
            emitChar(dig[i]);
            i = i - 1;
        }
    }

    public static int prepareSave() {
        ensureInit();
        saveLen = 0;
        emitChar(86);
        emitInt(SAVE_VERSION);
        emitChar(10);
        emitChar(78);
        emitInt(count);
        emitChar(10);
        int i = 0;
        while (i < count) {
            emitChar(68);
            emitInt(done[i]);
            emitChar(10);
            emitChar(84);
            emitInt(titleLen[i]);
            emitChar(10);
            /* Title as decimal code units + ';' so save text stays ASCII-safe. */
            int j = 0;
            int base = i * TITLE_MAX;
            while (j < titleLen[i]) {
                emitInt(titleChars[base + j]);
                emitChar(59);
                j = j + 1;
            }
            emitChar(10);
            i = i + 1;
        }
        lastError = 0;
        return saveLen;
    }

    public static int getSaveLen() {
        return saveLen;
    }

    public static int getSaveChar(int i) {
        if (i < 0 || i >= saveLen) {
            lastError = 1;
            return 0;
        }
        lastError = 0;
        return saveBuf[i];
    }

    public static int loadSave(String data) {
        ensureInit();
        if (data == null) {
            lastError = 5;
            return 0;
        }
        int len = data.length();
        if (len <= 0 || len > MAX_SAVE_CHARS) {
            lastError = 5;
            return 0;
        }
        int pos = 0;
        if (pos >= len || data.charAt(pos) != 86) {
            lastError = 5;
            return 0;
        }
        pos = pos + 1;
        int ver = 0;
        int[] r = parseIntAt(data, pos);
        ver = r[0];
        pos = r[1];
        if (ver != SAVE_VERSION) {
            lastError = 5;
            return 0;
        }
        if (pos >= len || data.charAt(pos) != 10) {
            lastError = 5;
            return 0;
        }
        pos = pos + 1;
        if (pos >= len || data.charAt(pos) != 78) {
            lastError = 5;
            return 0;
        }
        pos = pos + 1;
        r = parseIntAt(data, pos);
        int n = r[0];
        pos = r[1];
        if (n < 0 || n > MAX_TASKS) {
            lastError = 5;
            return 0;
        }
        if (pos >= len || data.charAt(pos) != 10) {
            lastError = 5;
            return 0;
        }
        pos = pos + 1;
        count = 0;
        int i = 0;
        while (i < n) {
            if (pos >= len || data.charAt(pos) != 68) {
                lastError = 5;
                return 0;
            }
            pos = pos + 1;
            r = parseIntAt(data, pos);
            int d = r[0];
            pos = r[1];
            if (pos >= len || data.charAt(pos) != 10) {
                lastError = 5;
                return 0;
            }
            pos = pos + 1;
            if (pos >= len || data.charAt(pos) != 84) {
                lastError = 5;
                return 0;
            }
            pos = pos + 1;
            r = parseIntAt(data, pos);
            int tl = r[0];
            pos = r[1];
            if (tl < 0 || tl > TITLE_MAX) {
                lastError = 5;
                return 0;
            }
            if (pos >= len || data.charAt(pos) != 10) {
                lastError = 5;
                return 0;
            }
            pos = pos + 1;
            int base = count * TITLE_MAX;
            int j = 0;
            while (j < tl) {
                r = parseIntAt(data, pos);
                titleChars[base + j] = r[0];
                pos = r[1];
                if (pos >= len || data.charAt(pos) != 59) {
                    lastError = 5;
                    return 0;
                }
                pos = pos + 1;
                j = j + 1;
            }
            titleLen[count] = tl;
            if (d != 0) {
                done[count] = 1;
            } else {
                done[count] = 0;
            }
            /* Rebuild ASCII display cache; non-ASCII shown as '?' until re-edited. */
            String rebuilt = "";
            j = 0;
            while (j < tl) {
                rebuilt = rebuilt + unitToString(titleChars[base + j]);
                j = j + 1;
            }
            titleStrCache[count] = rebuilt;
            count = count + 1;
            if (pos >= len || data.charAt(pos) != 10) {
                lastError = 5;
                return 0;
            }
            pos = pos + 1;
            i = i + 1;
        }
        lastError = 0;
        return 1;
    }

    /** parse decimal at pos; returns {value, newPos} in a tiny array. */
    static int[] parseIntAt(String data, int pos) {
        int[] out = new int[2];
        int sign = 1;
        int len = data.length();
        if (pos < len && data.charAt(pos) == 45) {
            sign = -1;
            pos = pos + 1;
        }
        int v = 0;
        int any = 0;
        while (pos < len) {
            int c = data.charAt(pos);
            if (c < 48 || c > 57) {
                break;
            }
            v = v * 10 + (c - 48);
            pos = pos + 1;
            any = 1;
        }
        if (any == 0) {
            out[0] = 0;
            out[1] = pos;
            return out;
        }
        out[0] = v * sign;
        out[1] = pos;
        return out;
    }

    public static void main(String[] args) {
        boolean keep = false;
        if (keep) {
            lastError = clearDraft();
            lastError = appendDraftChar(65);
            lastError = addFromDraft();
            lastError = getCount();
            lastError = getTitleLen(0);
            lastError = getTitleChar(0, 0);
            lastError = getDone(0);
            lastError = setDone(0, 1);
            lastError = toggleDone(0);
            lastError = prepareSave();
            lastError = getSaveLen();
            lastError = getSaveChar(0);
            lastError = loadSave("x");
            lastError = removeAt(0);
            lastError = addTaskString("x");
            lastError = setTitleString(0, "y");
            String s = titleString(0);
            if (s != null) {
                lastError = s.length();
            }
            lastError = getLastError();
            lastError = clearAll();
        }
    }
}
