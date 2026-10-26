/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Text Engine + CodeEditor (0.20) — library/runtime, NOT compiler intrinsics.
// Depends on: collections, ui, graphics (prepended by StdlibIncluder).

interface DocumentChangeListener {
    void onChange(DocumentChange change);
}

interface CaretListener {
    void onCaretChanged(TextPosition pos);
}

interface SelectionListener {
    void onSelectionChanged(TextSelection sel);
}

class TextPosition {
    int line;
    int column;

    TextPosition(int l, int c) {
        line = l;
        column = c;
    }

    int getLine() {
        return line;
    }

    int getColumn() {
        return column;
    }
}

class TextRange {
    int start;
    int end;

    TextRange(int s, int e) {
        if (s <= e) {
            start = s;
            end = e;
        } else {
            start = e;
            end = s;
        }
    }

    int getStart() {
        return start;
    }

    int getEnd() {
        return end;
    }

    int length() {
        return end - start;
    }

    boolean isEmpty() {
        return start == end;
    }
}

class DocumentChange {
    int offset;
    int removedLength;
    int insertedLength;
    String insertedText;
    int beforeVersion;
    int afterVersion;

    DocumentChange(int off, int rem, int insLen, String ins, int before, int after) {
        offset = off;
        removedLength = rem;
        insertedLength = insLen;
        insertedText = ins;
        beforeVersion = before;
        afterVersion = after;
    }
}

class Piece {
    int chunk;
    int start;
    int length;

    Piece(int ch, int st, int len) {
        chunk = ch;
        start = st;
        length = len;
    }
}

/** Piece table: original buffer + add chunks; no full-document copy on edit.
 * @author 张晓达
 */
class PieceTable {
    String original;
    ArrayList<String> addChunks;
    ArrayList<Piece> pieces;
    ArrayList<int> lineStarts;
    int length;

    PieceTable(String text) {
        if (text == null) {
            text = "";
        }
        text = TextUtil.normalizeNewlines(text);
        original = text;
        addChunks = new ArrayList<String>();
        pieces = new ArrayList<Piece>();
        if (text.length() > 0) {
            pieces.add(new Piece(-1, 0, text.length()));
        }
        length = text.length();
        lineStarts = new ArrayList<int>();
        rebuildLineStarts();
    }

    int getLength() {
        return length;
    }

    int getPieceCount() {
        return pieces.size();
    }

    int getLineCount() {
        return lineStarts.size();
    }

    String chunkText(int chunk) {
        if (chunk < 0) {
            return original;
        }
        return addChunks.get(chunk);
    }

    int charAt(int offset) {
        if (offset < 0 || offset >= length) {
            return 0;
        }
        int i = 0;
        int at = 0;
        while (i < pieces.size()) {
            Piece p = pieces.get(i);
            if (offset < at + p.length) {
                String s = chunkText(p.chunk);
                return s.charAt(p.start + (offset - at));
            }
            at = at + p.length;
            i = i + 1;
        }
        return 0;
    }

    String substring(int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end > length) {
            end = length;
        }
        if (start >= end) {
            return "";
        }
        String out = "";
        int i = 0;
        int at = 0;
        while (i < pieces.size()) {
            Piece p = pieces.get(i);
            int p0 = at;
            int p1 = at + p.length;
            int a = start;
            if (a < p0) {
                a = p0;
            }
            int b = end;
            if (b > p1) {
                b = p1;
            }
            if (a < b) {
                String s = chunkText(p.chunk);
                int local0 = p.start + (a - p0);
                int local1 = p.start + (b - p0);
                out = out + s.substring(local0, local1);
            }
            at = p1;
            if (at >= end) {
                return out;
            }
            i = i + 1;
        }
        return out;
    }

    String getText() {
        return substring(0, length);
    }

    void rebuildLineStarts() {
        lineStarts.clear();
        lineStarts.add(0);
        int pi = 0;
        int at = 0;
        while (pi < pieces.size()) {
            Piece p = pieces.get(pi);
            String s = chunkText(p.chunk);
            int j = p.start;
            int e = p.start + p.length;
            while (j < e) {
                if (s.charAt(j) == 10) {
                    lineStarts.add(at + (j - p.start) + 1);
                }
                j = j + 1;
            }
            at = at + p.length;
            pi = pi + 1;
        }
    }

    /** Length of a line in characters, without building the line string. */
    int lineLength(int line) {
        if (line < 0 || line >= lineStarts.size()) {
            return 0;
        }
        int a = lineStarts.get(line);
        if (line + 1 < lineStarts.size()) {
            return lineStarts.get(line + 1) - a - 1;
        }
        return length - a;
    }

    int offsetToLine(int offset) {
        if (offset <= 0) {
            return 0;
        }
        if (offset >= length) {
            int n = lineStarts.size() - 1;
            if (n < 0) {
                return 0;
            }
            return n;
        }
        int lo = 0;
        int hi = lineStarts.size() - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int v = lineStarts.get(mid);
            if (v == offset) {
                return mid;
            }
            if (v < offset) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return hi;
    }

    int lineToOffset(int line) {
        if (line < 0) {
            return 0;
        }
        if (line >= lineStarts.size()) {
            return length;
        }
        return lineStarts.get(line);
    }

    TextPosition offsetToPosition(int offset) {
        if (offset < 0) {
            offset = 0;
        }
        if (offset > length) {
            offset = length;
        }
        int line = offsetToLine(offset);
        int col = offset - lineToOffset(line);
        return new TextPosition(line, col);
    }

    int positionToOffset(int line, int column) {
        int base = lineToOffset(line);
        int next = length;
        if (line + 1 < lineStarts.size()) {
            next = lineStarts.get(line + 1) - 1;
            if (next < base) {
                next = base;
            }
        }
        int maxCol = next - base;
        if (column < 0) {
            column = 0;
        }
        if (column > maxCol) {
            column = maxCol;
        }
        return base + column;
    }

    String getLine(int line) {
        if (line < 0 || line >= lineStarts.size()) {
            return "";
        }
        int a = lineStarts.get(line);
        int b = length;
        if (line + 1 < lineStarts.size()) {
            b = lineStarts.get(line + 1);
            if (b > a && charAt(b - 1) == 10) {
                b = b - 1;
            }
        }
        return substring(a, b);
    }

    void findPiece(int offset, int[] outIndex, int[] outLocal) {
        int i = 0;
        int at = 0;
        while (i < pieces.size()) {
            Piece p = pieces.get(i);
            if (offset <= at + p.length) {
                outIndex[0] = i;
                outLocal[0] = offset - at;
                return;
            }
            at = at + p.length;
            i = i + 1;
        }
        outIndex[0] = pieces.size();
        outLocal[0] = 0;
    }

    void insert(int offset, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        text = TextUtil.normalizeNewlines(text);
        if (offset < 0) {
            offset = 0;
        }
        if (offset > length) {
            offset = length;
        }
        int tl = text.length();
        if (pieces.size() == 0) {
            addChunks.add(text);
            pieces.add(new Piece(addChunks.size() - 1, 0, tl));
            length = tl;
            rebuildLineStarts();
            return;
        }
        int[] idx = new int[1];
        int[] local = new int[1];
        findPiece(offset, idx, local);
        int pi = idx[0];
        int loc = local[0];
        int merged = 0;
        if (pi < pieces.size() && loc > 0) {
            Piece cur = pieces.get(pi);
            /* Typing continues the newest add chunk: grow it instead of adding a piece per keystroke. */
            if (loc == cur.length && cur.chunk >= 0 && cur.chunk == addChunks.size() - 1) {
                String chunk = addChunks.get(cur.chunk);
                if (cur.start + cur.length == chunk.length()) {
                    addChunks.set(cur.chunk, chunk + text);
                    pieces.set(pi, new Piece(cur.chunk, cur.start, cur.length + tl));
                    merged = 1;
                }
            }
        }
        if (merged == 0) {
            int chunkIndex = addChunks.size();
            addChunks.add(text);
            if (pi >= pieces.size()) {
                pieces.add(new Piece(chunkIndex, 0, tl));
            } else {
                Piece cur = pieces.get(pi);
                if (loc == 0) {
                    pieces.addAt(pi, new Piece(chunkIndex, 0, tl));
                } else if (loc == cur.length) {
                    pieces.addAt(pi + 1, new Piece(chunkIndex, 0, tl));
                } else {
                    Piece left = new Piece(cur.chunk, cur.start, loc);
                    Piece right = new Piece(cur.chunk, cur.start + loc, cur.length - loc);
                    pieces.set(pi, left);
                    pieces.addAt(pi + 1, new Piece(chunkIndex, 0, tl));
                    pieces.addAt(pi + 2, right);
                }
            }
        }
        /* Incremental line index: shift later starts, add the starts of newlines in the inserted text. */
        int line = offsetToLine(offset);
        length = length + tl;
        ArrayList<int> ls = new ArrayList<int>();
        int k = 0;
        while (k <= line) {
            ls.add(lineStarts.get(k));
            k = k + 1;
        }
        k = 0;
        while (k < tl) {
            if (text.charAt(k) == 10) {
                ls.add(offset + k + 1);
            }
            k = k + 1;
        }
        k = line + 1;
        int total = lineStarts.size();
        while (k < total) {
            ls.add(lineStarts.get(k) + tl);
            k = k + 1;
        }
        lineStarts = ls;
    }

    void appendLineStarts(int baseOffset, String text) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (text.charAt(i) == 10) {
                lineStarts.add(baseOffset + i + 1);
            }
            i = i + 1;
        }
    }

    void delete(int offset, int delLen) {
        if (delLen <= 0 || length == 0) {
            return;
        }
        if (offset < 0) {
            offset = 0;
        }
        if (offset >= length) {
            return;
        }
        if (offset + delLen > length) {
            delLen = length - offset;
        }
        int end = offset + delLen;
        ArrayList<Piece> neu = new ArrayList<Piece>();
        int i = 0;
        int at = 0;
        while (i < pieces.size()) {
            Piece p = pieces.get(i);
            int p0 = at;
            int p1 = at + p.length;
            if (p1 <= offset || p0 >= end) {
                neu.add(p);
            } else {
                if (p0 < offset) {
                    neu.add(new Piece(p.chunk, p.start, offset - p0));
                }
                if (p1 > end) {
                    int skip = end - p0;
                    neu.add(new Piece(p.chunk, p.start + skip, p1 - end));
                }
            }
            at = p1;
            i = i + 1;
        }
        pieces = neu;
        ArrayList<int> ls = new ArrayList<int>();
        int k = 0;
        int total = lineStarts.size();
        while (k < total) {
            int v = lineStarts.get(k);
            if (v <= offset) {
                ls.add(v);
            } else if (v > end) {
                ls.add(v - delLen);
            }
            k = k + 1;
        }
        lineStarts = ls;
        length = length - delLen;
    }

    void replace(int offset, int delLen, String text) {
        delete(offset, delLen);
        insert(offset, text);
    }

    /** Iterate piece chunks without building one giant String (save/streaming). */
    void iterateChunks(ChunkConsumer consumer) {
        if (consumer == null) {
            return;
        }
        int i = 0;
        while (i < pieces.size()) {
            Piece p = pieces.get(i);
            String s = chunkText(p.chunk);
            consumer.accept(s.substring(p.start, p.start + p.length));
            i = i + 1;
        }
    }
}

interface ChunkConsumer {
    void accept(String chunk);
}

class TextUtil {
    static String fromCode(int code) {
        if (code == 34) {
            return "\"";
        }
        if (code == 39) {
            return "'";
        }
        if (code == 92) {
            return "\\";
        }
        if (code >= 32 && code <= 126) {
            String base = " !#$%&()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~";
            /* base omits " ' \ which are handled above; map remaining codes */
            int i = 0;
            int c = 32;
            while (c <= 126) {
                if (c != 34 && c != 39 && c != 92) {
                    if (c == code) {
                        return base.substring(i, i + 1);
                    }
                    i = i + 1;
                }
                c = c + 1;
            }
        }
        return "";
    }

    static String normalizeNewlines(String text) {
        if (text == null) {
            return "";
        }
        int n = text.length();
        int first = text.indexOf("\r");
        if (first < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int segStart = 0;
        int i = first;
        while (i < n) {
            int c = text.charAt(i);
            if (c == 13) {
                out.append(text.substring(segStart, i));
                out.append("\n");
                if (i + 1 < n && text.charAt(i + 1) == 10) {
                    i = i + 1;
                }
                segStart = i + 1;
            }
            i = i + 1;
        }
        if (segStart < n) {
            out.append(text.substring(segStart, n));
        }
        return out.toString();
    }

    /** Any Unicode code point as a one-character string. */
    static String charString(int code) {
        char ch = (char) code;
        return "" + ch;
    }

    static int isWordChar(int c) {
        if (c >= 97 && c <= 122) {
            return 1;
        }
        if (c >= 65 && c <= 90) {
            return 1;
        }
        if (c >= 48 && c <= 57) {
            return 1;
        }
        if (c == 95 || c == 36) {
            return 1;
        }
        if (c >= 128) {
            return 1;
        }
        return 0;
    }

    static int isWhitespace(int c) {
        if (c == 32 || c == 9) {
            return 1;
        }
        return 0;
    }
}

class TextSnapshot {
    int version;
    String text;
    int lineCount;

    TextSnapshot(int ver, String t, int lines) {
        version = ver;
        text = t;
        lineCount = lines;
    }

    int getVersion() {
        return version;
    }

    String getText() {
        return text;
    }

    int getLineCount() {
        return lineCount;
    }
}

class TextDocument {
    PieceTable buffer;
    int version;
    int savedVersion;
    int dirty;
    String path;
    ArrayList<DocumentChangeListener> listeners;
    static int metricsDocumentBytes;
    static int metricsDocumentLines;
    static int metricsPieceCount;

    TextDocument() {
        buffer = new PieceTable("");
        version = 0;
        savedVersion = 0;
        dirty = 0;
        path = "";
        listeners = new ArrayList<DocumentChangeListener>();
        refreshMetrics();
    }

    TextDocument(String text) {
        buffer = new PieceTable(text);
        version = 0;
        savedVersion = 0;
        dirty = 0;
        path = "";
        listeners = new ArrayList<DocumentChangeListener>();
        refreshMetrics();
    }

    static TextDocument load(String filePath) {
        String text = File.readString(filePath);
        TextDocument doc = new TextDocument(text);
        doc.path = filePath;
        doc.savedVersion = doc.version;
        doc.dirty = 0;
        return doc;
    }

    void save() {
        saveAs(path);
    }

    void saveAs(String filePath) {
        if (filePath == null || filePath.length() == 0) {
            throw new RuntimeException("TextDocument.saveAs: empty path");
        }
        /* Chunk iteration is available for streaming; File API currently takes String. */
        File.writeString(filePath, buffer.getText());
        path = filePath;
        savedVersion = version;
        dirty = 0;
    }

    void iterateChunks(ChunkConsumer consumer) {
        buffer.iterateChunks(consumer);
    }

    void markSaved() {
        savedVersion = version;
        dirty = 0;
    }

    boolean isDirty() {
        return dirty != 0;
    }

    String getPath() {
        return path;
    }

    void refreshMetrics() {
        metricsDocumentBytes = buffer.getLength();
        metricsDocumentLines = buffer.getLineCount();
        metricsPieceCount = buffer.getPieceCount();
    }

    void onChange(DocumentChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    void fireChange(DocumentChange change) {
        int i = 0;
        while (i < listeners.size()) {
            listeners.get(i).onChange(change);
            i = i + 1;
        }
    }

    int getVersion() {
        return version;
    }

    int getLength() {
        return buffer.getLength();
    }

    int getLineCount() {
        return buffer.getLineCount();
    }

    int lineLength(int line) {
        return buffer.lineLength(line);
    }

    String getText() {
        return buffer.getText();
    }

    String getLine(int line) {
        return buffer.getLine(line);
    }

    int charAt(int offset) {
        return buffer.charAt(offset);
    }

    String substring(int start, int end) {
        return buffer.substring(start, end);
    }

    TextPosition offsetToPosition(int offset) {
        return buffer.offsetToPosition(offset);
    }

    int positionToOffset(int line, int column) {
        return buffer.positionToOffset(line, column);
    }

    int offsetToLine(int offset) {
        return buffer.offsetToLine(offset);
    }

    int lineToOffset(int line) {
        return buffer.lineToOffset(line);
    }

    void insert(int offset, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        int before = version;
        buffer.insert(offset, text);
        version = version + 1;
        dirty = 1;
        refreshMetrics();
        fireChange(new DocumentChange(offset, 0, text.length(), text, before, version));
    }

    void delete(int offset, int len) {
        if (len <= 0) {
            return;
        }
        int before = version;
        String removed = buffer.substring(offset, offset + len);
        buffer.delete(offset, len);
        version = version + 1;
        dirty = 1;
        refreshMetrics();
        fireChange(new DocumentChange(offset, removed.length(), 0, "", before, version));
    }

    void replace(int offset, int len, String text) {
        if (text == null) {
            text = "";
        }
        int before = version;
        String removed = buffer.substring(offset, offset + len);
        buffer.replace(offset, len, text);
        version = version + 1;
        dirty = 1;
        refreshMetrics();
        fireChange(new DocumentChange(offset, removed.length(), text.length(), text, before, version));
    }

    void setText(String text) {
        int before = version;
        int oldLen = buffer.getLength();
        buffer = new PieceTable(text);
        version = version + 1;
        dirty = 1;
        refreshMetrics();
        fireChange(new DocumentChange(0, oldLen, buffer.getLength(), buffer.getText(), before, version));
    }

    TextSnapshot snapshot() {
        return new TextSnapshot(version, buffer.getText(), buffer.getLineCount());
    }

    int getPieceCount() {
        return buffer.getPieceCount();
    }
}

class TextEdit {
    int offset;
    int removedLength;
    String removedText;
    String insertedText;

    TextEdit(int off, String removed, String inserted) {
        offset = off;
        removedText = removed;
        if (removed == null) {
            removedText = "";
        }
        removedLength = removedText.length();
        insertedText = inserted;
        if (insertedText == null) {
            insertedText = "";
        }
    }
}

class UndoGroup {
    ArrayList<TextEdit> edits;
    /** True while consecutive typing may still be merged into this group. */
    int open;

    UndoGroup() {
        edits = new ArrayList<TextEdit>();
        open = 0;
    }

    void add(TextEdit e) {
        edits.add(e);
    }

    int memorySize() {
        int m = 0;
        int i = 0;
        while (i < edits.size()) {
            TextEdit e = edits.get(i);
            m = m + e.removedText.length() + e.insertedText.length();
            i = i + 1;
        }
        return m;
    }
}

class UndoManager {
    ArrayList<UndoGroup> undoStack;
    ArrayList<UndoGroup> redoStack;
    UndoGroup compound;
    int compounding;
    int memoryBytes;
    int memoryLimit;
    TextDocument document;
    /** When set, a new edit joins the previous group if that group is still open (typing runs). */
    int coalescing;
    /** Caret offset suggested by the last undo()/redo(). */
    int lastCaret;

    UndoManager(TextDocument doc) {
        document = doc;
        undoStack = new ArrayList<UndoGroup>();
        redoStack = new ArrayList<UndoGroup>();
        compound = null;
        compounding = 0;
        memoryBytes = 0;
        memoryLimit = 8 * 1024 * 1024;
        coalescing = 0;
        lastCaret = 0;
    }

    /** Ends the current typing run: the next edit starts a new undo step. */
    void closeGroup() {
        if (undoStack.size() > 0) {
            undoStack.get(undoStack.size() - 1).open = 0;
        }
    }

    void beginCompoundEdit() {
        compounding = compounding + 1;
        if (compounding == 1) {
            compound = new UndoGroup();
        }
    }

    void endCompoundEdit() {
        if (compounding <= 0) {
            return;
        }
        compounding = compounding - 1;
        if (compounding == 0) {
            if (compound != null && compound.edits.size() > 0) {
                pushGroup(compound);
            }
            compound = null;
        }
    }

    void pushGroup(UndoGroup group) {
        undoStack.add(group);
        redoStack.clear();
        memoryBytes = memoryBytes + group.memorySize();
        trimMemory();
    }

    void pushEdit(TextEdit edit) {
        if (compounding > 0 && compound != null) {
            compound.add(edit);
            return;
        }
        if (coalescing != 0 && undoStack.size() > 0) {
            UndoGroup last = undoStack.get(undoStack.size() - 1);
            if (last.open != 0) {
                last.add(edit);
                memoryBytes = memoryBytes + edit.removedText.length() + edit.insertedText.length();
                redoStack.clear();
                return;
            }
        }
        UndoGroup g = new UndoGroup();
        g.add(edit);
        if (coalescing != 0) {
            g.open = 1;
        }
        pushGroup(g);
    }

    void trimMemory() {
        while (memoryBytes > memoryLimit && undoStack.size() > 0) {
            UndoGroup g = undoStack.removeAt(0);
            memoryBytes = memoryBytes - g.memorySize();
        }
    }

    void recordReplace(int offset, String removed, String inserted) {
        pushEdit(new TextEdit(offset, removed, inserted));
    }

    boolean canUndo() {
        return undoStack.size() > 0;
    }

    boolean canRedo() {
        return redoStack.size() > 0;
    }

    void undo() {
        if (undoStack.size() == 0) {
            return;
        }
        UndoGroup g = undoStack.removeAt(undoStack.size() - 1);
        g.open = 0;
        int i = g.edits.size() - 1;
        while (i >= 0) {
            TextEdit e = g.edits.get(i);
            document.replace(e.offset, e.insertedText.length(), e.removedText);
            lastCaret = e.offset + e.removedText.length();
            i = i - 1;
        }
        redoStack.add(g);
    }

    void redo() {
        if (redoStack.size() == 0) {
            return;
        }
        UndoGroup g = redoStack.removeAt(redoStack.size() - 1);
        int i = 0;
        while (i < g.edits.size()) {
            TextEdit e = g.edits.get(i);
            document.replace(e.offset, e.removedText.length(), e.insertedText);
            lastCaret = e.offset + e.insertedText.length();
            i = i + 1;
        }
        undoStack.add(g);
    }

    int getMemoryBytes() {
        return memoryBytes;
    }
}

class TextSearch {
    static int lower(int c) {
        if (c >= 65 && c <= 90) {
            return c + 32;
        }
        return c;
    }

    static int matchAt(String text, int i, String query, int caseSensitive) {
        int qn = query.length();
        int j = 0;
        while (j < qn) {
            int a = text.charAt(i + j);
            int b = query.charAt(j);
            if (a != b) {
                if (caseSensitive != 0) {
                    return 0;
                }
                if (lower(a) != lower(b)) {
                    return 0;
                }
            }
            j = j + 1;
        }
        return 1;
    }

    static int wholeWordOk(String text, int i, int qn) {
        int n = text.length();
        if (i > 0 && TextUtil.isWordChar(text.charAt(i - 1)) != 0) {
            return 0;
        }
        if (i + qn < n && TextUtil.isWordChar(text.charAt(i + qn)) != 0) {
            return 0;
        }
        return 1;
    }

    /** First match at or after fromOffset in text. */
    static TextRange findIn(String text, String query, int fromOffset, int caseSensitive, int wholeWord) {
        if (query == null || query.length() == 0) {
            return null;
        }
        int n = text.length();
        int qn = query.length();
        int i = fromOffset;
        if (i < 0) {
            i = 0;
        }
        int q0 = query.charAt(0);
        while (i + qn <= n) {
            int c0 = text.charAt(i);
            if (c0 == q0 || (caseSensitive == 0 && lower(c0) == lower(q0))) {
                if (matchAt(text, i, query, caseSensitive) != 0) {
                    if (wholeWord == 0 || wholeWordOk(text, i, qn) != 0) {
                        return new TextRange(i, i + qn);
                    }
                }
            }
            i = i + 1;
        }
        return null;
    }

    /** Last match that starts before beforeOffset. */
    static TextRange findBackIn(String text, String query, int beforeOffset, int caseSensitive, int wholeWord) {
        if (query == null || query.length() == 0) {
            return null;
        }
        int n = text.length();
        int qn = query.length();
        int i = beforeOffset - 1;
        if (i + qn > n) {
            i = n - qn;
        }
        while (i >= 0) {
            if (matchAt(text, i, query, caseSensitive) != 0) {
                if (wholeWord == 0 || wholeWordOk(text, i, qn) != 0) {
                    return new TextRange(i, i + qn);
                }
            }
            i = i - 1;
        }
        return null;
    }

    static TextRange find(TextDocument doc, String query, int fromOffset, int caseSensitive, int wholeWord) {
        if (query == null || query.length() == 0) {
            return null;
        }
        return findIn(doc.getText(), query, fromOffset, caseSensitive, wholeWord);
    }

    static int replaceAll(TextDocument doc, UndoManager undo, String query, String replacement, int caseSensitive) {
        if (query == null || query.length() == 0) {
            return 0;
        }
        if (replacement == null) {
            replacement = "";
        }
        undo.beginCompoundEdit();
        int count = 0;
        int from = 0;
        TextRange r = TextSearch.find(doc, query, from, caseSensitive, 0);
        while (r != null) {
            String removed = doc.substring(r.start, r.end);
            undo.recordReplace(r.start, removed, replacement);
            doc.replace(r.start, r.length(), replacement);
            count = count + 1;
            from = r.start + replacement.length();
            r = TextSearch.find(doc, query, from, caseSensitive, 0);
        }
        undo.endCompoundEdit();
        return count;
    }
}

class TokenType {
    static int NORMAL = 0;
    static int KEYWORD = 1;
    static int IDENTIFIER = 2;
    static int STRING = 3;
    static int NUMBER = 4;
    static int COMMENT = 5;
    static int OPERATOR = 6;
    static int TYPE = 7;
    static int ANNOTATION = 8;
}

class Token {
    int start;
    int length;
    int type;

    Token(int s, int len, int t) {
        start = s;
        length = len;
        type = t;
    }
}

class TextStyle {
    Color foreground;
    Color background;
    int bold;
    int underline;

    TextStyle(Color fg, Color bg, int b, int u) {
        foreground = fg;
        background = bg;
        bold = b;
        underline = u;
    }
}

class StyledSpan {
    int start;
    int end;
    int tokenType;

    StyledSpan(int s, int e, int t) {
        start = s;
        end = e;
        tokenType = t;
    }
}

class EditorTheme {
    Color background;
    Color foreground;
    Color selection;
    Color caret;
    Color lineNumber;
    Color currentLine;
    Color keyword;
    Color stringColor;
    Color comment;
    Color number;
    Color diagnosticError;
    Color typeColor;
    Color annotationColor;
    Color bracketMatch;
    Color findMatch;
    Color findCurrent;
    Color wordHighlight;
    Color gutterBackground;
    Color gutterCurrent;
    Color scrollThumb;
    Color indentGuide;

    static EditorTheme dark() {
        EditorTheme t = new EditorTheme();
        t.background = new Color(30, 30, 30);
        t.foreground = new Color(220, 220, 220);
        t.selection = new Color(60, 80, 140);
        t.caret = new Color(255, 255, 255);
        t.lineNumber = new Color(120, 120, 120);
        t.currentLine = new Color(40, 40, 48);
        t.keyword = new Color(120, 160, 255);
        t.stringColor = new Color(220, 180, 120);
        t.comment = new Color(120, 180, 120);
        t.number = new Color(200, 140, 220);
        t.diagnosticError = new Color(255, 80, 80);
        t.typeColor = new Color(78, 201, 176);
        t.annotationColor = new Color(220, 220, 130);
        t.bracketMatch = new Color(90, 90, 110);
        t.findMatch = new Color(110, 90, 30);
        t.findCurrent = new Color(160, 120, 20);
        t.wordHighlight = new Color(55, 60, 75);
        t.gutterBackground = new Color(30, 30, 30);
        t.gutterCurrent = new Color(200, 200, 200);
        t.scrollThumb = new Color(120, 120, 120, 120);
        t.indentGuide = new Color(55, 55, 55);
        return t;
    }

    static EditorTheme light() {
        EditorTheme t = new EditorTheme();
        t.background = new Color(255, 255, 255);
        t.foreground = new Color(20, 20, 20);
        t.selection = new Color(180, 200, 255);
        t.caret = new Color(0, 0, 0);
        t.lineNumber = new Color(140, 140, 140);
        t.currentLine = new Color(245, 245, 250);
        t.keyword = new Color(0, 0, 200);
        t.stringColor = new Color(160, 80, 0);
        t.comment = new Color(0, 128, 0);
        t.number = new Color(128, 0, 128);
        t.diagnosticError = new Color(200, 0, 0);
        t.typeColor = new Color(0, 120, 130);
        t.annotationColor = new Color(130, 100, 0);
        t.bracketMatch = new Color(205, 220, 245);
        t.findMatch = new Color(255, 232, 150);
        t.findCurrent = new Color(255, 190, 80);
        t.wordHighlight = new Color(228, 236, 248);
        t.gutterBackground = new Color(250, 250, 250);
        t.gutterCurrent = new Color(60, 60, 60);
        t.scrollThumb = new Color(90, 90, 90, 110);
        t.indentGuide = new Color(232, 232, 232);
        return t;
    }

    Color colorForToken(int type) {
        if (type == TokenType.KEYWORD) {
            return keyword;
        }
        if (type == TokenType.STRING) {
            return stringColor;
        }
        if (type == TokenType.COMMENT) {
            return comment;
        }
        if (type == TokenType.NUMBER) {
            return number;
        }
        if (type == TokenType.ANNOTATION) {
            return annotationColor;
        }
        if (type == TokenType.TYPE) {
            return typeColor;
        }
        return foreground;
    }
}

class JNativeSyntaxTokenizer {
    static int linesRetokenized;
    static int tokensGenerated;

    static int isKeyword(String w) {
        int n = w.length();
        if (n < 2 || n > 12) {
            return 0;
        }
        int c0 = w.charAt(0);
        if (c0 < 97 || c0 > 122) {
            return 0;
        }
        if (w.equals("class") || w.equals("interface") || w.equals("public")
                || w.equals("private") || w.equals("protected") || w.equals("static")
                || w.equals("void") || w.equals("int") || w.equals("long")
                || w.equals("double") || w.equals("boolean") || w.equals("char")
                || w.equals("if") || w.equals("else") || w.equals("for")
                || w.equals("while") || w.equals("return") || w.equals("new")
                || w.equals("extends") || w.equals("implements") || w.equals("try")
                || w.equals("catch") || w.equals("throw") || w.equals("throws")
                || w.equals("import") || w.equals("package") || w.equals("final")
                || w.equals("true") || w.equals("false") || w.equals("null")
                || w.equals("this") || w.equals("super") || w.equals("break")
                || w.equals("continue") || w.equals("abstract") || w.equals("switch")
                || w.equals("case") || w.equals("default") || w.equals("do")
                || w.equals("enum") || w.equals("float") || w.equals("short")
                || w.equals("byte") || w.equals("instanceof") || w.equals("native")
                || w.equals("synchronized") || w.equals("volatile") || w.equals("transient")
                || w.equals("finally") || w.equals("var") || w.equals("record")) {
            return 1;
        }
        return 0;
    }

    /** CamelCase identifier (class / interface / enum name): first letter upper case and some lower case. */
    static int isTypeName(String w) {
        int n = w.length();
        if (n < 2) {
            return 0;
        }
        int c0 = w.charAt(0);
        if (c0 < 65 || c0 > 90) {
            return 0;
        }
        int i = 1;
        while (i < n) {
            int c = w.charAt(i);
            if (c >= 97 && c <= 122) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    /** Returns end state: 0 normal, 1 in block comment. */
    static int tokenizeLine(int prevState, String line, ArrayList<Token> out, int lineOffset) {
        linesRetokenized = linesRetokenized + 1;
        int i = 0;
        int j = 0;
        int n = line.length();
        int state = prevState;
        int endPos = 0;
        int done = 0;
        int cont = 0;
        int d = 0;
        int c = 0;
        int ty = 0;
        String w = "";
        while (i < n) {
            if (state == 1) {
                endPos = line.indexOfFrom("*/", i);
                if (endPos < 0) {
                    out.add(new Token(lineOffset + i, n - i, TokenType.COMMENT));
                    tokensGenerated = tokensGenerated + 1;
                    return 1;
                }
                out.add(new Token(lineOffset + i, endPos + 2 - i, TokenType.COMMENT));
                tokensGenerated = tokensGenerated + 1;
                i = endPos + 2;
                state = 0;
            } else {
                c = line.charAt(i);
                if (c == 47 && i + 1 < n && line.charAt(i + 1) == 47) {
                    out.add(new Token(lineOffset + i, n - i, TokenType.COMMENT));
                    tokensGenerated = tokensGenerated + 1;
                    return 0;
                }
                if (c == 47 && i + 1 < n && line.charAt(i + 1) == 42) {
                    endPos = line.indexOfFrom("*/", i + 2);
                    if (endPos < 0) {
                        out.add(new Token(lineOffset + i, n - i, TokenType.COMMENT));
                        tokensGenerated = tokensGenerated + 1;
                        return 1;
                    }
                    out.add(new Token(lineOffset + i, endPos + 2 - i, TokenType.COMMENT));
                    tokensGenerated = tokensGenerated + 1;
                    i = endPos + 2;
                } else if (c == 34 || c == 39) {
                    j = i + 1;
                    done = 0;
                    while (j < n && done == 0) {
                        d = line.charAt(j);
                        if (d == 92) {
                            j = j + 2;
                        } else if (d == c) {
                            j = j + 1;
                            done = 1;
                        } else {
                            j = j + 1;
                        }
                    }
                    if (j > n) {
                        j = n;
                    }
                    out.add(new Token(lineOffset + i, j - i, TokenType.STRING));
                    tokensGenerated = tokensGenerated + 1;
                    i = j;
                } else if (c == 64) {
                    j = i + 1;
                    while (j < n && TextUtil.isWordChar(line.charAt(j)) != 0) {
                        j = j + 1;
                    }
                    out.add(new Token(lineOffset + i, j - i, TokenType.ANNOTATION));
                    tokensGenerated = tokensGenerated + 1;
                    i = j;
                } else if (c >= 48 && c <= 57) {
                    j = i + 1;
                    cont = 1;
                    while (j < n && cont != 0) {
                        d = line.charAt(j);
                        if ((d >= 48 && d <= 57) || d == 46 || d == 95 || d == 76 || d == 102
                                || (c == 48 && j == i + 1 && d == 120)
                                || (c == 48 && i + 1 < n && line.charAt(i + 1) == 120
                                    && ((d >= 97 && d <= 102) || (d >= 65 && d <= 70)))) {
                            j = j + 1;
                        } else {
                            cont = 0;
                        }
                    }
                    out.add(new Token(lineOffset + i, j - i, TokenType.NUMBER));
                    tokensGenerated = tokensGenerated + 1;
                    i = j;
                } else if (TextUtil.isWordChar(c) != 0) {
                    j = i + 1;
                    while (j < n && TextUtil.isWordChar(line.charAt(j)) != 0) {
                        j = j + 1;
                    }
                    w = line.substring(i, j);
                    ty = TokenType.IDENTIFIER;
                    if (isKeyword(w) != 0) {
                        ty = TokenType.KEYWORD;
                    } else if (isTypeName(w) != 0) {
                        ty = TokenType.TYPE;
                    }
                    out.add(new Token(lineOffset + i, j - i, ty));
                    tokensGenerated = tokensGenerated + 1;
                    i = j;
                } else {
                    out.add(new Token(lineOffset + i, 1, TokenType.OPERATOR));
                    tokensGenerated = tokensGenerated + 1;
                    i = i + 1;
                }
            }
        }
        return state;
    }

    static ArrayList<StyledSpan> highlightDocument(TextDocument doc) {
        ArrayList<StyledSpan> spans = new ArrayList<StyledSpan>();
        ArrayList<int> states = new ArrayList<int>();
        states.add(0);
        int line = 0;
        int n = doc.getLineCount();
        while (line < n) {
            ArrayList<Token> tokens = new ArrayList<Token>();
            int off = doc.lineToOffset(line);
            int st = states.get(line);
            int next = tokenizeLine(st, doc.getLine(line), tokens, off);
            states.add(next);
            int ti = 0;
            while (ti < tokens.size()) {
                Token t = tokens.get(ti);
                spans.add(new StyledSpan(t.start, t.start + t.length, t.type));
                ti = ti + 1;
            }
            line = line + 1;
        }
        return spans;
    }

    static ArrayList<StyledSpan> highlightLineRange(TextDocument doc, int fromLine, int toLine, ArrayList<int> lineStates) {
        ArrayList<StyledSpan> spans = new ArrayList<StyledSpan>();
        if (lineStates.size() == 0) {
            lineStates.add(0);
        }
        while (lineStates.size() <= fromLine) {
            lineStates.add(0);
        }
        int line = fromLine;
        if (line < 0) {
            line = 0;
        }
        int n = doc.getLineCount();
        if (toLine >= n) {
            toLine = n - 1;
        }
        while (line <= toLine) {
            while (lineStates.size() <= line) {
                lineStates.add(0);
            }
            ArrayList<Token> tokens = new ArrayList<Token>();
            int off = doc.lineToOffset(line);
            int st = lineStates.get(line);
            int next = tokenizeLine(st, doc.getLine(line), tokens, off);
            if (line + 1 < lineStates.size()) {
                lineStates.set(line + 1, next);
            } else {
                lineStates.add(next);
            }
            int ti = 0;
            while (ti < tokens.size()) {
                Token t = tokens.get(ti);
                spans.add(new StyledSpan(t.start, t.start + t.length, t.type));
                ti = ti + 1;
            }
            line = line + 1;
        }
        return spans;
    }
}

class EditorDiagnostic {
    int start;
    int end;
    int severity;
    String message;

    static int ERROR = 1;
    static int WARNING = 2;
    static int INFO = 3;
    static int HINT = 4;

    EditorDiagnostic(int s, int e, int sev, String msg) {
        start = s;
        end = e;
        severity = sev;
        message = msg;
    }

    /**
     * Map compiler diagnostic (1-based line/column) onto document offsets.
     */
    static EditorDiagnostic fromCompiler(TextDocument doc, int line1, int column1, int severity, String message) {
        int line = line1 - 1;
        int col = column1 - 1;
        if (line < 0) {
            line = 0;
        }
        if (col < 0) {
            col = 0;
        }
        int start = doc.positionToOffset(line, col);
        int end = start + 1;
        if (end > doc.getLength()) {
            end = doc.getLength();
        }
        if (end <= start && start < doc.getLength()) {
            end = start + 1;
        }
        return new EditorDiagnostic(start, end, severity, message);
    }
}

class TextSelection {
    int anchor;
    int active;

    TextSelection() {
        anchor = 0;
        active = 0;
    }

    int start() {
        if (anchor <= active) {
            return anchor;
        }
        return active;
    }

    int end() {
        if (anchor <= active) {
            return active;
        }
        return anchor;
    }

    boolean isEmpty() {
        return anchor == active;
    }

    TextRange toRange() {
        return new TextRange(start(), end());
    }
}

class Caret {
    int offset;
    int preferredColumn;
    int visible;
    int blinkOn;

    Caret() {
        offset = 0;
        preferredColumn = 0;
        visible = 1;
        blinkOn = 1;
    }
}

class EditorViewport {
    int firstLine;
    int yOffset;
    int xOffset;
    int width;
    int height;
    int lineHeight;
    int gutterWidth;

    EditorViewport() {
        firstLine = 0;
        yOffset = 0;
        xOffset = 0;
        width = 800;
        height = 600;
        lineHeight = 18;
        gutterWidth = 48;
    }

    int visibleLineCount() {
        if (lineHeight <= 0) {
            return 1;
        }
        return height / lineHeight + 2;
    }
}

class ClipboardNative {
    @Native(symbol = "jnative_clipboard_set_text")
    static native int setText(String text);

    @Native(symbol = "jnative_clipboard_get_text")
    static native NativePtr getTextPtr();

    @Native(symbol = "jnative_clipboard_free")
    static native void freeText(NativePtr p);
}

class ProcessNative {
    @Native(symbol = "jnative_process_run")
    static native int run(String command);

    @Native(symbol = "jnative_now_ms")
    static native int nowMs();
}

/** Shell command helper for IDE Build (off UI thread). */
class ProcessRunner {
    static int run(String command) {
        if (command == null) {
            return -1;
        }
        return ProcessNative.run(command);
    }
}

class EditorClock {
    static int nowMs() {
        return ProcessNative.nowMs();
    }
}

class Clipboard {
    static void setText(String text) {
        if (text == null) {
            text = "";
        }
        ClipboardNative.setText(text);
    }

    static String getText() {
        NativePtr p = ClipboardNative.getTextPtr();
        if (p == NativePtr.NULL) {
            return "";
        }
        String s = NativeMemory.fromCString(p);
        ClipboardNative.freeText(p);
        if (s == null) {
            return "";
        }
        return s;
    }
}

class EditorMetrics {
    static int layoutsCreated;
    static int layoutCacheHits;
    static int layoutCacheMisses;
    static int visibleLinesPainted;
    static int managedAllocHint;
}

class EditorCommand {
    static int COPY = 1;
    static int CUT = 2;
    static int PASTE = 3;
    static int UNDO = 4;
    static int REDO = 5;
    static int SELECT_ALL = 6;
    static int MOVE_LEFT = 7;
    static int MOVE_RIGHT = 8;
    static int MOVE_UP = 9;
    static int MOVE_DOWN = 10;
    static int DELETE_BACKWARD = 11;
    static int DELETE_FORWARD = 12;
    static int NEWLINE = 13;
    static int HOME = 14;
    static int END = 15;
    static int FIND = 16;
    static int REPLACE = 17;
    static int PAGE_UP = 18;
    static int PAGE_DOWN = 19;
    static int DOC_START = 20;
    static int DOC_END = 21;
    static int WORD_LEFT = 22;
    static int WORD_RIGHT = 23;
    static int TOGGLE_COMMENT = 24;
    static int INDENT = 25;
    static int OUTDENT = 26;
    static int DELETE_LINE = 27;
    static int DUPLICATE_LINE = 28;
    static int COPY_LINE_UP = 29;
    static int MOVE_LINE_UP = 30;
    static int MOVE_LINE_DOWN = 31;
    static int SELECT_LINE = 32;
    static int INSERT_LINE_BELOW = 33;
    static int INSERT_LINE_ABOVE = 34;
    static int DELETE_WORD_BACK = 35;
    static int DELETE_WORD_FORWARD = 36;
    static int DELETE_TO_LINE_START = 37;
}

/** Word boundary helpers for code-aware caret motion. */
class WordBoundary {
    static int prev(TextDocument doc, int offset) {
        if (doc == null || offset <= 0) {
            return 0;
        }
        int i = offset;
        if (i > doc.getLength()) {
            i = doc.getLength();
        }
        while (i > 0 && TextUtil.isWordChar(doc.charAt(i - 1)) == 0) {
            i = i - 1;
        }
        while (i > 0 && TextUtil.isWordChar(doc.charAt(i - 1)) != 0) {
            i = i - 1;
        }
        return i;
    }

    static int next(TextDocument doc, int offset) {
        if (doc == null) {
            return 0;
        }
        int n = doc.getLength();
        int i = offset;
        if (i < 0) {
            i = 0;
        }
        while (i < n && TextUtil.isWordChar(doc.charAt(i)) == 0) {
            i = i + 1;
        }
        while (i < n && TextUtil.isWordChar(doc.charAt(i)) != 0) {
            i = i + 1;
        }
        return i;
    }
}

/** Fold region model (UI folding deferred; data ready for 0.21). */
class FoldRegion {
    int startLine;
    int endLine;
    int collapsed;

    FoldRegion(int start, int end) {
        startLine = start;
        endLine = end;
        collapsed = 0;
    }
}

/** Key binding entry (keymap policies can remap commands). */
class KeyBinding {
    int keyCode;
    int ctrl;
    int shift;
    int meta;
    int commandId;

    KeyBinding(int key, int c, int s, int m, int cmd) {
        keyCode = key;
        ctrl = c;
        shift = s;
        meta = m;
        commandId = cmd;
    }
}

/**
 * TextLayout: measure / caret-x / hit-test foundation.
 * Glyph shaping remains native (DirectWrite / CoreText / Pango / software Font).
 */
class TextLayout {
    String text;
    Font font;
    int documentVersion;
    int lineNumber;
    Size measured;

    TextLayout(String t, Font f, int ver, int line) {
        text = t;
        if (text == null) {
            text = "";
        }
        font = f;
        documentVersion = ver;
        lineNumber = line;
        measured = null;
    }

    Size measure() {
        if (measured != null) {
            EditorMetrics.layoutCacheHits = EditorMetrics.layoutCacheHits + 1;
            return measured;
        }
        EditorMetrics.layoutCacheMisses = EditorMetrics.layoutCacheMisses + 1;
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        g.setFont(font);
        measured = g.measureText(text, font);
        g.end();
        tmp.dispose();
        EditorMetrics.layoutsCreated = EditorMetrics.layoutsCreated + 1;
        return measured;
    }

    int hitTest(int x) {
        int col = 0;
        int best = 0;
        int n = text.length();
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        g.setFont(font);
        while (col <= n) {
            Size sz = g.measureText(text.substring(0, col), font);
            if (sz.width <= x) {
                best = col;
            } else {
                g.end();
                tmp.dispose();
                return best;
            }
            col = col + 1;
        }
        g.end();
        tmp.dispose();
        return best;
    }

    double caretX(int column) {
        if (column < 0) {
            column = 0;
        }
        if (column > text.length()) {
            column = text.length();
        }
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        g.setFont(font);
        Size sz = g.measureText(text.substring(0, column), font);
        g.end();
        tmp.dispose();
        return sz.width;
    }
}

class TextLineLayout {
    int line;
    int version;
    TextLayout layout;
    ArrayList<StyledSpan> spans;

    TextLineLayout(int ln, int ver, TextLayout lay) {
        line = ln;
        version = ver;
        layout = lay;
        spans = new ArrayList<StyledSpan>();
    }
}

/** Tokens of one line as full-coverage runs [start, end, type] (line relative). */
class LineTok {
    int[] data;
    int count;

    LineTok() {
        data = new int[12];
        count = 0;
    }

    void add(int s, int e, int t) {
        if (e <= s) {
            return;
        }
        if (count >= 3 && data[count - 1] == t && data[count - 2] == s) {
            data[count - 2] = e;
            return;
        }
        if (count + 3 > data.length) {
            int[] nd = new int[data.length * 2];
            int i = 0;
            while (i < count) {
                nd[i] = data[i];
                i = i + 1;
            }
            data = nd;
        }
        data[count] = s;
        data[count + 1] = e;
        data[count + 2] = t;
        count = count + 3;
    }

    int typeAt(int col) {
        int i = 0;
        while (i < count) {
            if (col >= data[i] && col < data[i + 1]) {
                return data[i + 2];
            }
            i = i + 3;
        }
        return TokenType.NORMAL;
    }
}

interface GutterListener {
    /** The breakpoint strip at the left edge of the gutter was clicked on a (0-based) line. */
    void clicked(int line);
}

interface TypedListener {
    /** A character was typed (code point), or 8 for backspace / 127 for delete. */
    void typed(int code);
}

class CodeEditor extends Canvas {
    TextDocument document;
    UndoManager undo;
    Caret caret;
    TextSelection selection;
    EditorViewport viewport;
    EditorTheme theme;
    ArrayList<LineTok> lineToks;
    ArrayList<int> lineStates;
    ArrayList<EditorDiagnostic> diagnostics;
    String hoverTip = "";
    Font font;
    int tabSize;
    int insertSpaces;
    int readOnly;
    int wordWrap;
    int showLineNumbers;
    int showSyntax;
    UiTimer blinkTimer;
    int shiftSelecting;
    ArrayList<CaretListener> caretListeners;
    ArrayList<SelectionListener> selectionListeners;
    String findQuery;
    int findCase;
    int findWhole;
    int compositionActive;
    String compositionText;
    /** Auto-close brackets and quotes, auto-indent. */
    int smartEdit;
    /** Runs before the editor handles a key; consume() the event to take it over (popups). */
    EventHandler<KeyEvent> keyPre;
    TypedListener typedListener;
    /** Breakpoint markers (0-based lines) drawn in the gutter. */
    ArrayList<Integer> breakLines = new ArrayList<Integer>();
    /** Bookmarked lines (0-based): a blue tab at the left edge of the gutter. */
    ArrayList<Integer> bookmarkLines = new ArrayList<Integer>();
    /**
     * Per-line change marks drawn as a thin bar at the right edge of the gutter (version-control style):
     * 1 added, 2 modified, 3 lines were deleted just above. Shorter than the document is fine (missing = 0).
     */
    ArrayList<int> lineMarks = new ArrayList<int>();
    /** Line the debugger is stopped at (highlighted with an arrow), or -1. */
    int execLine = -1;
    /** Code folding is off by default (Studio turns it on): regions come from { } pairs and multi-line block comments. */
    int foldingEnabled = 0;
    ArrayList<int> foldStarts = new ArrayList<int>();
    ArrayList<int> foldEnds = new ArrayList<int>();
    /** Start lines the user collapsed. */
    ArrayList<int> foldedStarts = new ArrayList<int>();
    /** Merged hidden line ranges [hideFrom, hideTo], ascending. */
    ArrayList<int> hideFrom = new ArrayList<int>();
    ArrayList<int> hideTo = new ArrayList<int>();
    int hiddenTotal = 0;
    int foldsDirty = 1;
    int foldPrevLines = 0;
    GutterListener gutterListener;
    /** Called for Cmd/Ctrl + click with the clicked offset (go to definition). */
    EventHandler<MouseEvent> commandClick;

    double charW;
    int lineH;
    Font metricsFont;
    String hlWord;
    int bracketA;
    int bracketB;
    int dragMode;
    int dragStart;
    int dragEnd;
    int dragBar;
    int dragBarGrab;
    int maxCols;
    int maxColsDirty;
    int mouseDownFlag;
    int lastImeX;
    int lastImeY;
    ArrayList<int> wideCodes;
    ArrayList<int> wideWidths;
    ArrayList<Token> scratchTokens;

    CodeEditor() {
        document = new TextDocument("");
        undo = new UndoManager(document);
        caret = new Caret();
        selection = new TextSelection();
        viewport = new EditorViewport();
        theme = EditorTheme.dark();
        lineToks = new ArrayList<LineTok>();
        lineStates = new ArrayList<int>();
        lineStates.add(0);
        diagnostics = new ArrayList<EditorDiagnostic>();
        font = Font.monospace(14);
        tabSize = 4;
        insertSpaces = 1;
        readOnly = 0;
        wordWrap = 0;
        showLineNumbers = 1;
        showSyntax = 1;
        shiftSelecting = 0;
        caretListeners = new ArrayList<CaretListener>();
        selectionListeners = new ArrayList<SelectionListener>();
        findQuery = "";
        findCase = 0;
        findWhole = 0;
        compositionActive = 0;
        compositionText = "";
        smartEdit = 1;
        charW = 0.0;
        lineH = 19;
        metricsFont = null;
        hlWord = "";
        bracketA = -1;
        bracketB = -1;
        dragMode = 0;
        maxCols = 0;
        maxColsDirty = 1;
        wideCodes = new ArrayList<int>();
        wideWidths = new ArrayList<int>();
        scratchTokens = new ArrayList<Token>();
        lastImeX = -1;
        lastImeY = -1;
        setPrefSize(800, 600);
        width = 800;
        height = 600;
        viewport.width = 800;
        viewport.height = 600;
        installHandlers();
        watchDocument();
        rehighlightAll();
    }

    void installHandlers() {
        CodeEditor self = this;
        onPaint(g -> {
            self.paintEditor(g);
        });
        onMouse(event -> {
            self.handleMouse(event);
        });
        onKeyDown(event -> {
            self.handleKey(event);
        });
        onTextInput(event -> {
            self.handleTextInput(event);
        });
        if (nativeId > 0) {
            UiNative.canvasSetTextInput(nativeId, 1);
            UiNative.canvasSetIbeam(nativeId, 1);
        }
        /* Blink timer started lazily via ensureBlinkTimer() to avoid headless hang. */
        blinkTimer = null;
    }

    void watchDocument() {
        CodeEditor self = this;
        document.onChange(ch -> {
            self.documentChanged(ch);
            self.foldAfterChange(ch);
        });
    }

    void ensureBlinkTimer() {
        if (blinkTimer != null) {
            return;
        }
        CodeEditor self = this;
        blinkTimer = new UiTimer(530, () -> {
            if (self.visible != 0 && self.compositionActive == 0) {
                self.caret.blinkOn = 1 - self.caret.blinkOn;
                self.repaint();
            }
        });
    }

    void setDocument(TextDocument doc) {
        document = doc;
        undo = new UndoManager(document);
        caret.offset = 0;
        selection.anchor = 0;
        selection.active = 0;
        viewport.firstLine = 0;
        viewport.yOffset = 0;
        viewport.xOffset = 0;
        foldedStarts = new ArrayList<int>();
        hideFrom = new ArrayList<int>();
        hideTo = new ArrayList<int>();
        hiddenTotal = 0;
        foldsDirty = 1;
        foldPrevLines = document.getLineCount();
        watchDocument();
        rehighlightAll();
        refreshCaretInfo();
        repaint();
    }

    TextDocument getDocument() {
        return document;
    }

    int getCaretOffset() {
        return caret.offset;
    }

    void setText(String text) {
        document.setText(text);
        caret.offset = 0;
        selection.anchor = 0;
        selection.active = 0;
        viewport.firstLine = 0;
        viewport.yOffset = 0;
        viewport.xOffset = 0;
        undo = new UndoManager(document);
        rehighlightAll();
        refreshCaretInfo();
        repaint();
    }

    String getText() {
        return document.getText();
    }

    void setReadOnly(boolean v) {
        if (v) {
            readOnly = 1;
        } else {
            readOnly = 0;
        }
    }

    void setLineNumbersVisible(boolean v) {
        if (v) {
            showLineNumbers = 1;
        } else {
            showLineNumbers = 0;
        }
        repaint();
    }

    void setSyntaxHighlighting(boolean v) {
        if (v) {
            showSyntax = 1;
        } else {
            showSyntax = 0;
        }
        rehighlightAll();
        repaint();
    }

    void setTheme(EditorTheme t) {
        theme = t;
        repaint();
    }

    void setDiagnostics(ArrayList<EditorDiagnostic> list) {
        diagnostics = list;
        repaint();
    }

    void clearDiagnostics() {
        diagnostics = new ArrayList<EditorDiagnostic>();
        repaint();
    }

    void onCaretChanged(CaretListener l) {
        if (l != null) {
            caretListeners.add(l);
        }
    }

    void onSelectionChanged(SelectionListener l) {
        if (l != null) {
            selectionListeners.add(l);
        }
    }

    void fireCaret() {
        refreshCaretInfo();
        TextPosition p = document.offsetToPosition(caret.offset);
        int i = 0;
        while (i < caretListeners.size()) {
            caretListeners.get(i).onCaretChanged(p);
            i = i + 1;
        }
    }

    void fireSelection() {
        int i = 0;
        while (i < selectionListeners.size()) {
            selectionListeners.get(i).onSelectionChanged(selection);
            i = i + 1;
        }
    }

    // ------------------------------------------------------------------ tokens

    void rehighlightAll() {
        lineToks = new ArrayList<LineTok>();
        lineStates = new ArrayList<int>();
        lineStates.add(0);
        int n = document.getLineCount();
        int state = 0;
        int line = 0;
        while (line < n) {
            if (showSyntax != 0) {
                String text = document.getLine(line);
                lineToks.add(tokenizeInto(state, text));
                lineStates.add(scratchState);
                state = scratchState;
            } else {
                lineToks.add(null);
                lineStates.add(0);
            }
            line = line + 1;
        }
        maxColsDirty = 1;
    }

    int scratchState;

    LineTok tokenizeInto(int state, String text) {
        scratchTokens.clear();
        scratchState = JNativeSyntaxTokenizer.tokenizeLine(state, text, scratchTokens, 0);
        LineTok lt = new LineTok();
        int n = text.length();
        int cur = 0;
        int i = 0;
        while (i < scratchTokens.size()) {
            Token t = scratchTokens.get(i);
            int ty = t.type;
            if (ty == TokenType.IDENTIFIER || ty == TokenType.OPERATOR) {
                ty = TokenType.NORMAL;
            }
            if (t.start > cur) {
                lt.add(cur, t.start, TokenType.NORMAL);
            }
            lt.add(t.start, t.start + t.length, ty);
            cur = t.start + t.length;
            i = i + 1;
        }
        if (cur < n) {
            lt.add(cur, n, TokenType.NORMAL);
        }
        return lt;
    }

    /** Keeps the per-line token cache in step with a document edit and re-tokenizes what changed. */
    void documentChanged(DocumentChange ch) {
        maxColsDirty = 1;
        bracketA = -1;
        bracketB = -1;
        int newCount = document.getLineCount();
        int oldCount = lineToks.size();
        int inserted = 0;
        String txt = ch.insertedText;
        if (txt != null) {
            int k = 0;
            int tn = txt.length();
            while (k < tn) {
                if (txt.charAt(k) == 10) {
                    inserted = inserted + 1;
                }
                k = k + 1;
            }
        }
        int removed = oldCount - newCount + inserted;
        if (removed < 0 || inserted + removed > 200 || oldCount == 0) {
            rehighlightAll();
            return;
        }
        int line = document.offsetToLine(ch.offset);
        int r = 0;
        while (r < removed) {
            if (line + 1 < lineToks.size()) {
                lineToks.removeAt(line + 1);
            }
            if (line + 1 < lineStates.size()) {
                lineStates.removeAt(line + 1);
            }
            r = r + 1;
        }
        r = 0;
        while (r < inserted) {
            lineToks.addAt(line + 1, null);
            lineStates.addAt(line + 1, 0);
            r = r + 1;
        }
        while (lineToks.size() < newCount) {
            lineToks.add(null);
        }
        while (lineStates.size() < newCount + 1) {
            lineStates.add(0);
        }
        if (showSyntax == 0) {
            return;
        }
        int i = line;
        int state = lineStates.get(i);
        int guard = 0;
        while (i < newCount) {
            String text = document.getLine(i);
            lineToks.set(i, tokenizeInto(state, text));
            int next = scratchState;
            int old = lineStates.get(i + 1);
            lineStates.set(i + 1, next);
            state = next;
            i = i + 1;
            if (i > line + inserted && next == old) {
                return;
            }
            guard = guard + 1;
        }
    }

    LineTok tokensForLine(int line) {
        if (showSyntax == 0 || line < 0 || line >= lineToks.size()) {
            return null;
        }
        LineTok t = lineToks.get(line);
        if (t == null) {
            int st = 0;
            if (line < lineStates.size()) {
                st = lineStates.get(line);
            }
            t = tokenizeInto(st, document.getLine(line));
            lineToks.set(line, t);
        }
        return t;
    }

    int tokenTypeAt(int line, int col) {
        LineTok t = tokensForLine(line);
        if (t == null) {
            return TokenType.NORMAL;
        }
        return t.typeAt(col);
    }

    // ------------------------------------------------------------------ metrics

    void ensureMetrics() {
        if (metricsFont == font && charW > 0.0) {
            return;
        }
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        String probe = "";
        int i = 0;
        while (i < 8) {
            probe = probe + "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM";
            i = i + 1;
        }
        Size sz = g.measureText(probe, font);
        charW = sz.width / (probe.length() * 1.0);
        int lh = fm.getLineHeight();
        g.end();
        tmp.dispose();
        if (charW < 3.0) {
            charW = 8.0;
        }
        lineH = lh + 4;
        if (lineH < 16) {
            lineH = 18;
        }
        viewport.lineHeight = lineH;
        metricsFont = font;
        wideCodes = new ArrayList<int>();
        wideWidths = new ArrayList<int>();
    }

    double wideWidth(int c) {
        int i = 0;
        while (i < wideCodes.size()) {
            if (wideCodes.get(i) == c) {
                return wideWidths.get(i) * 1.0;
            }
            i = i + 1;
        }
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        Size sz = g.measureText(TextUtil.charString(c), font);
        g.end();
        tmp.dispose();
        int w = sz.width;
        if (w <= 0) {
            w = (int) (charW * 2.0);
        }
        wideCodes.add(c);
        wideWidths.add(w);
        return w * 1.0;
    }

    double advance(int c, double cur) {
        if (c == 9) {
            double tabPx = tabSize * charW;
            double next = ((int) (cur / tabPx) + 1) * tabPx;
            return next - cur;
        }
        if (c < 128) {
            return charW;
        }
        return wideWidth(c);
    }

    /** Pixel offset of a column inside a line (tabs and wide characters aware). */
    double colToX(String line, int col) {
        int n = line.length();
        if (col > n) {
            col = n;
        }
        double x = 0.0;
        int i = 0;
        while (i < col) {
            x = x + advance(line.charAt(i), x);
            i = i + 1;
        }
        return x;
    }

    int xToCol(String line, double x) {
        int n = line.length();
        double cur = 0.0;
        int i = 0;
        while (i < n) {
            double w = advance(line.charAt(i), cur);
            if (x < cur + w / 2.0) {
                return i;
            }
            cur = cur + w;
            i = i + 1;
        }
        return n;
    }

    int gutterWidthFor() {
        ensureMetrics();
        if (showLineNumbers == 0) {
            viewport.gutterWidth = 0;
            return 0;
        }
        int lines = document.getLineCount();
        int digits = 1;
        int v = lines;
        while (v >= 10) {
            digits = digits + 1;
            v = v / 10;
        }
        if (digits < 3) {
            digits = 3;
        }
        int w = (int) (digits * charW) + 26;
        viewport.gutterWidth = w;
        return w;
    }

    int textLeft() {
        return gutterWidthFor() + 8;
    }

    // ------------------------------------------------------------------ code folding

    /** Display row of a line; a hidden line shares the row of its fold's first line. */
    int rowOf(int line) {
        if (hiddenTotal == 0) {
            return line;
        }
        int hidden = 0;
        int i = 0;
        while (i < hideFrom.size()) {
            int a = hideFrom.get(i);
            if (a > line) {
                break;
            }
            int b = hideTo.get(i);
            if (line > b) {
                hidden = hidden + (b - a + 1);
            } else {
                hidden = hidden + (line - a + 1);
            }
            i = i + 1;
        }
        return line - hidden;
    }

    int lineOfRow(int row) {
        if (hiddenTotal == 0) {
            return row;
        }
        int line = row;
        int i = 0;
        while (i < hideFrom.size()) {
            if (line >= hideFrom.get(i)) {
                line = line + (hideTo.get(i) - hideFrom.get(i) + 1);
            } else {
                break;
            }
            i = i + 1;
        }
        return line;
    }

    int visibleRowCount() {
        return document.getLineCount() - hiddenTotal;
    }

    /** Index of the hidden range containing the line, or -1. */
    int hiddenIndex(int line) {
        if (hiddenTotal == 0) {
            return -1;
        }
        int i = 0;
        while (i < hideFrom.size()) {
            if (line >= hideFrom.get(i) && line <= hideTo.get(i)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    /** End line of the fold region starting at the line, or -1. */
    int foldEndAt(int line) {
        if (foldingEnabled == 0) {
            return -1;
        }
        ensureFolds();
        int i = 0;
        while (i < foldStarts.size()) {
            int s = foldStarts.get(i);
            if (s == line) {
                return foldEnds.get(i);
            }
            if (s > line) {
                break;
            }
            i = i + 1;
        }
        return -1;
    }

    int isFoldedAt(int line) {
        if (foldedStarts.indexOf(line) >= 0 && hiddenTotal > 0) {
            return 1;
        }
        return 0;
    }

    void addFoldRegion(ArrayList<int> best, int s, int e) {
        if (e > s && s < best.size() && best.get(s) < e) {
            best.set(s, e);
        }
    }

    /** Recomputes the fold regions when the text changed. */
    void ensureFolds() {
        if (foldingEnabled == 0 || foldsDirty == 0) {
            return;
        }
        foldsDirty = 0;
        int n = document.getLineCount();
        foldPrevLines = n;
        ArrayList<int> best = new ArrayList<int>();
        int k = 0;
        while (k < n) {
            best.add(-1);
            k = k + 1;
        }
        ArrayList<int> stack = new ArrayList<int>();
        int inBlock = 0;
        int blockStart = 0;
        int line = 0;
        while (line < n) {
            String t = document.getLine(line);
            int len = t.length();
            int i = 0;
            while (i < len) {
                int c = t.charAt(i);
                if (inBlock != 0) {
                    if (c == 42 && i + 1 < len && t.charAt(i + 1) == 47) {
                        inBlock = 0;
                        i = i + 1;
                        addFoldRegion(best, blockStart, line);
                    }
                } else if (c == 47 && i + 1 < len && t.charAt(i + 1) == 47) {
                    break;
                } else if (c == 47 && i + 1 < len && t.charAt(i + 1) == 42) {
                    inBlock = 1;
                    blockStart = line;
                    i = i + 1;
                } else if (c == 34 || c == 39) {
                    int q = c;
                    i = i + 1;
                    while (i < len && t.charAt(i) != q) {
                        if (t.charAt(i) == 92) {
                            i = i + 1;
                        }
                        i = i + 1;
                    }
                } else if (c == 123) {
                    stack.add(line);
                } else if (c == 125) {
                    if (stack.size() > 0) {
                        int s0 = stack.get(stack.size() - 1);
                        stack.removeAt(stack.size() - 1);
                        addFoldRegion(best, s0, line);
                    }
                }
                i = i + 1;
            }
            line = line + 1;
        }
        foldStarts = new ArrayList<int>();
        foldEnds = new ArrayList<int>();
        line = 0;
        while (line < n) {
            if (best.get(line) >= 0) {
                foldStarts.add(line);
                foldEnds.add(best.get(line));
            }
            line = line + 1;
        }
        ArrayList<int> keep = new ArrayList<int>();
        int f = 0;
        while (f < foldedStarts.size()) {
            int fs = foldedStarts.get(f);
            if (fs < n && best.get(fs) >= 0) {
                keep.add(fs);
            }
            f = f + 1;
        }
        foldedStarts = keep;
        rebuildHidden();
    }

    void rebuildHidden() {
        hideFrom = new ArrayList<int>();
        hideTo = new ArrayList<int>();
        hiddenTotal = 0;
        int lastEnd = -1;
        int i = 0;
        while (i < foldedStarts.size()) {
            int s = foldedStarts.get(i);
            int e = foldEndAtRaw(s);
            i = i + 1;
            if (e < 0 || s <= lastEnd) {
                continue;
            }
            hideFrom.add(s + 1);
            hideTo.add(e);
            hiddenTotal = hiddenTotal + (e - s);
            lastEnd = e;
        }
    }

    int foldEndAtRaw(int line) {
        int i = 0;
        while (i < foldStarts.size()) {
            if (foldStarts.get(i) == line) {
                return foldEnds.get(i);
            }
            i = i + 1;
        }
        return -1;
    }

    void addFolded(int s) {
        if (foldedStarts.indexOf(s) >= 0) {
            return;
        }
        int at = 0;
        while (at < foldedStarts.size() && foldedStarts.get(at) < s) {
            at = at + 1;
        }
        foldedStarts.addAt(at, s);
    }

    /** Collapses or expands the region that starts at the line. */
    void toggleFold(int line) {
        if (foldingEnabled == 0) {
            return;
        }
        ensureFolds();
        int idx = foldedStarts.indexOf(line);
        if (idx >= 0) {
            foldedStarts.removeAt(idx);
        } else if (foldEndAtRaw(line) >= 0) {
            addFolded(line);
        } else {
            return;
        }
        rebuildHidden();
        TextPosition p = document.offsetToPosition(caret.offset);
        int hi = hiddenIndex(p.line);
        if (hi >= 0) {
            int sl = hideFrom.get(hi) - 1;
            int off = document.lineToOffset(sl) + document.lineLength(sl);
            caret.offset = off;
            selection.anchor = off;
            selection.active = off;
            fireCaret();
            fireSelection();
        }
        setScrollPx(scrollPx());
        repaint();
    }

    /** Folds / unfolds the innermost region around the caret line. */
    void toggleFoldAtCaret() {
        if (foldingEnabled == 0) {
            return;
        }
        ensureFolds();
        TextPosition p = document.offsetToPosition(caret.offset);
        int line = p.line;
        if (isFoldedAt(line) != 0) {
            toggleFold(line);
            return;
        }
        int best = -1;
        int i = 0;
        while (i < foldStarts.size()) {
            int s = foldStarts.get(i);
            if (s <= line && foldEnds.get(i) >= line && foldEnds.get(i) > s) {
                best = s;
            }
            if (s > line) {
                break;
            }
            i = i + 1;
        }
        if (best >= 0) {
            toggleFold(best);
        }
    }

    /** Collapses every region nested inside another one (the outermost class body stays open). */
    void foldAll() {
        if (foldingEnabled == 0) {
            return;
        }
        ensureFolds();
        foldedStarts = new ArrayList<int>();
        int top = -1;
        int i = 0;
        while (i < foldStarts.size()) {
            int s = foldStarts.get(i);
            if (s > top) {
                top = foldEnds.get(i);
            } else {
                foldedStarts.add(s);
            }
            i = i + 1;
        }
        rebuildHidden();
        revealFoldsFor(document.offsetToLine(caret.offset));
        setScrollPx(scrollPx());
        repaint();
    }

    void unfoldAll() {
        foldedStarts = new ArrayList<int>();
        rebuildHidden();
        repaint();
    }

    /** Expands every collapsed region that hides the line (navigation, find, errors). */
    void revealFoldsFor(int line) {
        if (hiddenTotal == 0) {
            return;
        }
        int guard = 0;
        while (hiddenIndex(line) >= 0 && guard < 50) {
            int hi = hiddenIndex(line);
            int s = hideFrom.get(hi) - 1;
            int idx = foldedStarts.indexOf(s);
            if (idx < 0) {
                break;
            }
            foldedStarts.removeAt(idx);
            rebuildHidden();
            guard = guard + 1;
        }
    }

    /** Called after every document change: keeps collapsed regions attached to their lines or expands them. */
    void foldAfterChange(DocumentChange ch) {
        if (foldingEnabled == 0) {
            return;
        }
        int newCount = document.getLineCount();
        int inserted = 0;
        String txt = ch.insertedText;
        if (txt != null) {
            int k = 0;
            while (k < txt.length()) {
                if (txt.charAt(k) == 10) {
                    inserted = inserted + 1;
                }
                k = k + 1;
            }
        }
        int removed = foldPrevLines - newCount + inserted;
        foldPrevLines = newCount;
        foldsDirty = 1;
        if (foldedStarts.size() == 0) {
            return;
        }
        if (removed < 0) {
            foldedStarts = new ArrayList<int>();
            rebuildHidden();
            return;
        }
        int l0 = document.offsetToLine(ch.offset);
        ArrayList<int> next = new ArrayList<int>();
        int i = 0;
        while (i < foldedStarts.size()) {
            int s = foldedStarts.get(i);
            int e = foldEndAtRaw(s);
            i = i + 1;
            if (e < 0) {
                continue;
            }
            if (l0 + removed < s) {
                next.add(s + inserted - removed);
            } else if (l0 > e) {
                next.add(s);
            } else if (l0 == s && removed == 0 && inserted == 0) {
                next.add(s);
            }
        }
        foldedStarts = next;
        ensureFoldsNow();
    }

    void ensureFoldsNow() {
        foldsDirty = 1;
        ensureFolds();
    }

    int scrollPx() {
        return viewport.firstLine * lineH + viewport.yOffset;
    }

    int contentHeight() {
        return visibleRowCount() * lineH;
    }

    int maxScrollY() {
        int m = contentHeight() - height + height / 2;
        if (m < 0) {
            m = 0;
        }
        return m;
    }

    void setScrollPx(int px) {
        int mx = maxScrollY();
        if (px > mx) {
            px = mx;
        }
        if (px < 0) {
            px = 0;
        }
        viewport.firstLine = px / lineH;
        viewport.yOffset = px % lineH;
    }

    void refreshMaxCols() {
        if (maxColsDirty == 0) {
            return;
        }
        maxColsDirty = 0;
        int n = document.getLineCount();
        int m = 0;
        int i = 0;
        while (i < n) {
            int l = document.lineLength(i);
            if (l > m) {
                m = l;
            }
            i = i + 1;
        }
        maxCols = m;
    }

    int maxScrollX() {
        refreshMaxCols();
        int content = (int) (maxCols * charW) + 40;
        int avail = width - textLeft() - 12;
        int m = content - avail;
        if (m < 0) {
            m = 0;
        }
        return m;
    }

    void setScrollX(int px) {
        int mx = maxScrollX();
        if (px > mx) {
            px = mx;
        }
        if (px < 0) {
            px = 0;
        }
        viewport.xOffset = px;
    }

    void scrollLines(int delta) {
        ensureMetrics();
        setScrollPx(scrollPx() + delta * lineH);
        repaint();
    }

    int visibleLines() {
        ensureMetrics();
        int v = height / lineH;
        if (v < 1) {
            v = 1;
        }
        return v;
    }

    int hitTest(int x, int y) {
        ensureMetrics();
        int line = lineOfRow((y + scrollPx()) / lineH);
        if (y + scrollPx() < 0) {
            line = 0;
        }
        if (line < 0) {
            line = 0;
        }
        if (line >= document.getLineCount()) {
            line = document.getLineCount() - 1;
            if (line < 0) {
                return 0;
            }
        }
        String text = document.getLine(line);
        double cx = x - textLeft() + viewport.xOffset;
        if (cx < 0.0) {
            cx = 0.0;
        }
        int col = xToCol(text, cx);
        return document.positionToOffset(line, col);
    }

    Size measureLineWidth(String s) {
        ensureMetrics();
        return new Size((int) colToX(s, s.length()), lineH);
    }

    void ensureCaretVisible() {
        ensureMetrics();
        if (height <= 0) {
            return;
        }
        TextPosition p = document.offsetToPosition(caret.offset);
        revealFoldsFor(p.line);
        int y = rowOf(p.line) * lineH;
        int sp = scrollPx();
        if (y < sp) {
            setScrollPx(y);
        } else if (y + lineH > sp + height) {
            setScrollPx(y + lineH - height);
        }
        String text = document.getLine(p.line);
        int x = (int) colToX(text, p.column);
        int avail = width - textLeft() - 12;
        if (avail < 40) {
            return;
        }
        if (x - viewport.xOffset < 8) {
            setScrollX(x - 60);
        } else if (x - viewport.xOffset > avail - 24) {
            setScrollX(x - avail + 80);
        }
    }

    /** Caret position in editor pixels: x of the caret, y of the top of its line. */
    int caretPixelX() {
        ensureMetrics();
        TextPosition p = document.offsetToPosition(caret.offset);
        return (int) (textLeft() - viewport.xOffset + colToX(document.getLine(p.line), p.column));
    }

    int caretPixelY() {
        ensureMetrics();
        TextPosition p = document.offsetToPosition(caret.offset);
        return rowOf(p.line) * lineH - scrollPx();
    }

    /** Pixel y of the top of the line containing an arbitrary offset (same coordinate space as caretPixelY). */
    int offsetPixelY(int offset) {
        ensureMetrics();
        TextPosition p = document.offsetToPosition(offset);
        return rowOf(p.line) * lineH - scrollPx();
    }

    /** Pixel x of an arbitrary offset (same coordinate space as caretPixelX). */
    int offsetPixelX(int offset) {
        ensureMetrics();
        TextPosition p = document.offsetToPosition(offset);
        return (int) (textLeft() - viewport.xOffset + colToX(document.getLine(p.line), p.column));
    }

    int lineHeightPx() {
        ensureMetrics();
        return lineH;
    }

    /** Identifier text immediately before the caret. */
    String wordBeforeCaret() {
        return document.substring(wordStart(caret.offset), caret.offset);
    }

    /** Scrolls so that the line is visible, centring it when it is far away. */
    void revealLine(int line) {
        ensureMetrics();
        revealFoldsFor(line);
        int y = rowOf(line) * lineH;
        int sp = scrollPx();
        if (y < sp || y + lineH > sp + height) {
            setScrollPx(y - height / 2 + lineH);
        }
    }

    void goTo(int line, int column) {
        int off = document.positionToOffset(line, column);
        undo.closeGroup();
        caret.offset = off;
        selection.anchor = off;
        selection.active = off;
        TextPosition p = document.offsetToPosition(off);
        revealLine(p.line);
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void reveal(TextRange range) {
        TextPosition p = document.offsetToPosition(range.start);
        goTo(p.line, p.column);
        selection.anchor = range.start;
        selection.active = range.end;
        caret.offset = range.end;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void selectRange(int a, int b) {
        undo.closeGroup();
        selection.anchor = a;
        selection.active = b;
        caret.offset = b;
        TextPosition p = document.offsetToPosition(b);
        revealLine(p.line);
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    // ------------------------------------------------------------------ editing primitives

    void applyEdit(int offset, int delLen, String insert) {
        if (readOnly != 0) {
            return;
        }
        if (insert == null) {
            insert = "";
        }
        String removed = document.substring(offset, offset + delLen);
        undo.recordReplace(offset, removed, insert);
        document.replace(offset, delLen, insert);
        caret.offset = offset + insert.length();
        selection.anchor = caret.offset;
        selection.active = caret.offset;
        caret.blinkOn = 1;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void replaceSelection(String text) {
        TextRange r = selection.toRange();
        applyEdit(r.start, r.length(), text);
    }

    void insertText(String text) {
        if (!selection.isEmpty()) {
            replaceSelection(text);
        } else {
            applyEdit(caret.offset, 0, text);
        }
    }

    /** Inserts text as one undo step (paste, IME commit, completion). */
    void insertBlock(String text) {
        if (readOnly != 0) {
            return;
        }
        undo.closeGroup();
        undo.coalescing = 0;
        insertText(text);
        undo.closeGroup();
        notifyTyped(0);
    }

    void deleteSelectionOr(int dir) {
        if (!selection.isEmpty()) {
            replaceSelection("");
            return;
        }
        if (dir < 0) {
            if (caret.offset > 0) {
                applyEdit(caret.offset - 1, 1, "");
            }
        } else {
            if (caret.offset < document.getLength()) {
                applyEdit(caret.offset, 1, "");
            }
        }
    }

    void notifyTyped(int code) {
        if (typedListener != null) {
            typedListener.typed(code);
        }
    }

    void moveCaret(int newOffset, int extend) {
        if (newOffset < 0) {
            newOffset = 0;
        }
        if (newOffset > document.getLength()) {
            newOffset = document.getLength();
        }
        undo.closeGroup();
        caret.offset = newOffset;
        if (extend != 0) {
            selection.active = newOffset;
        } else {
            selection.anchor = newOffset;
            selection.active = newOffset;
        }
        TextPosition p = document.offsetToPosition(caret.offset);
        caret.preferredColumn = p.column;
        caret.blinkOn = 1;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    int wordBoundary(int offset, int dir) {
        if (dir < 0) {
            return WordBoundary.prev(document, offset);
        }
        return WordBoundary.next(document, offset);
    }

    /** Offset of the first non-blank character of a line (line end for blank lines). */
    int firstNonBlank(int line) {
        String t = document.getLine(line);
        int i = 0;
        while (i < t.length() && TextUtil.isWhitespace(t.charAt(i)) != 0) {
            i = i + 1;
        }
        return document.lineToOffset(line) + i;
    }

    String indentUnit() {
        if (insertSpaces != 0) {
            String sp = "";
            int i = 0;
            while (i < tabSize) {
                sp = sp + " ";
                i = i + 1;
            }
            return sp;
        }
        return "\t";
    }

    String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && TextUtil.isWhitespace(line.charAt(i)) != 0) {
            i = i + 1;
        }
        return line.substring(0, i);
    }

    int lastNonBlankChar(String s) {
        int i = s.length() - 1;
        while (i >= 0 && TextUtil.isWhitespace(s.charAt(i)) != 0) {
            i = i - 1;
        }
        if (i < 0) {
            return 0;
        }
        return s.charAt(i);
    }

    // ------------------------------------------------------------------ typing

    int closerFor(int c) {
        if (c == 40) {
            return 41;
        }
        if (c == 91) {
            return 93;
        }
        if (c == 123) {
            return 125;
        }
        if (c == 34) {
            return 34;
        }
        if (c == 39) {
            return 39;
        }
        return 0;
    }

    int isCloser(int c) {
        if (c == 41 || c == 93 || c == 125 || c == 34 || c == 39) {
            return 1;
        }
        return 0;
    }

    int charAtOffset(int off) {
        if (off < 0 || off >= document.getLength()) {
            return 0;
        }
        return document.charAt(off);
    }

    /** True when the caret sits inside a string or comment token (no auto pairing there). */
    int inStringOrComment(int offset) {
        TextPosition p = document.offsetToPosition(offset);
        if (p.column == 0) {
            return 0;
        }
        int ty = tokenTypeAt(p.line, p.column - 1);
        if (ty == TokenType.COMMENT) {
            return 1;
        }
        if (ty == TokenType.STRING) {
            return 1;
        }
        return 0;
    }

    void typeChar(int ch) {
        if (readOnly != 0) {
            return;
        }
        String s = TextUtil.charString(ch);
        int nextCh = charAtOffset(caret.offset);
        int prevCh = charAtOffset(caret.offset - 1);
        if (smartEdit != 0 && selection.isEmpty() && isCloser(ch) != 0 && nextCh == ch) {
            /* Typing over an auto-inserted closer. */
            if (ch == 34 || ch == 39) {
                if (inStringOrComment(caret.offset) != 0 || TextUtil.isWordChar(prevCh) == 0) {
                    moveCaret(caret.offset + 1, 0);
                    notifyTyped(ch);
                    return;
                }
            } else {
                moveCaret(caret.offset + 1, 0);
                notifyTyped(ch);
                return;
            }
        }
        if (smartEdit != 0 && closerFor(ch) != 0) {
            int closer = closerFor(ch);
            if (!selection.isEmpty()) {
                /* Surround the selection. */
                TextRange r = selection.toRange();
                String inner = document.substring(r.start, r.end);
                undo.closeGroup();
                undo.beginCompoundEdit();
                applyEdit(r.start, r.length(), s + inner + TextUtil.charString(closer));
                undo.endCompoundEdit();
                selection.anchor = r.start + 1;
                selection.active = r.start + 1 + inner.length();
                caret.offset = selection.active;
                fireCaret();
                fireSelection();
                repaint();
                undo.closeGroup();
                notifyTyped(ch);
                return;
            }
            int ok = 1;
            if (ch == 34 || ch == 39) {
                if (TextUtil.isWordChar(prevCh) != 0 || prevCh == ch || inStringOrComment(caret.offset) != 0) {
                    ok = 0;
                }
            }
            if (ok != 0 && nextCh != 0 && nextCh != 10 && nextCh != 32 && nextCh != 9 && isCloser(nextCh) == 0
                    && nextCh != 59 && nextCh != 44 && nextCh != 46) {
                ok = 0;
            }
            if (ok != 0 && inStringOrComment(caret.offset) != 0 && ch != 34 && ch != 39) {
                ok = 0;
            }
            if (ok != 0) {
                undo.closeGroup();
                undo.beginCompoundEdit();
                applyEdit(caret.offset, 0, s + TextUtil.charString(closer));
                undo.endCompoundEdit();
                moveCaret(caret.offset - 1, 0);
                notifyTyped(ch);
                return;
            }
        }
        if (smartEdit != 0 && ch == 125 && selection.isEmpty()) {
            /* A closing brace on a blank line steps back one indent level. */
            TextPosition p = document.offsetToPosition(caret.offset);
            String line = document.getLine(p.line);
            String before = line.substring(0, p.column);
            if (before.length() > 0 && leadingWhitespace(before).length() == before.length()) {
                int remove = 0;
                if (before.endsWith("\t")) {
                    remove = 1;
                } else if (before.length() >= tabSize) {
                    remove = tabSize;
                } else {
                    remove = before.length();
                }
                undo.closeGroup();
                undo.beginCompoundEdit();
                applyEdit(caret.offset - remove, remove, "");
                applyEdit(caret.offset, 0, "}");
                undo.endCompoundEdit();
                undo.closeGroup();
                notifyTyped(ch);
                return;
            }
        }
        undo.coalescing = 1;
        insertText(s);
        undo.coalescing = 0;
        notifyTyped(ch);
    }

    void typeNewline(int above, int below) {
        if (readOnly != 0) {
            return;
        }
        TextPosition p = document.offsetToPosition(caret.offset);
        String line = document.getLine(p.line);
        String indent = leadingWhitespace(line);
        undo.closeGroup();
        undo.beginCompoundEdit();
        if (below != 0) {
            int endOff = document.lineToOffset(p.line) + line.length();
            String extra = "";
            if (smartEdit != 0 && lastNonBlankChar(line) == 123) {
                extra = indentUnit();
            }
            selection.anchor = endOff;
            selection.active = endOff;
            caret.offset = endOff;
            applyEdit(endOff, 0, "\n" + indent + extra);
        } else if (above != 0) {
            int startOff = document.lineToOffset(p.line);
            applyEdit(startOff, 0, indent + "\n");
            moveCaret(startOff + indent.length(), 0);
        } else {
            TextRange r = selection.toRange();
            String beforeCaret = document.substring(document.lineToOffset(p.line), r.start);
            int afterCh = charAtOffset(r.end);
            int beforeLast = lastNonBlankChar(beforeCaret);
            if (smartEdit != 0 && beforeLast == 123) {
                String unit = indentUnit();
                if (afterCh == 125) {
                    applyEdit(r.start, r.length(), "\n" + indent + unit + "\n" + indent);
                    int back = indent.length() + 1;
                    moveCaret(caret.offset - back, 0);
                } else {
                    applyEdit(r.start, r.length(), "\n" + indent + unit);
                }
            } else {
                applyEdit(r.start, r.length(), "\n" + indent);
            }
        }
        undo.endCompoundEdit();
        undo.closeGroup();
        notifyTyped(10);
    }

    // ------------------------------------------------------------------ line / block commands

    int blockFirstLine() {
        TextRange r = selection.toRange();
        return document.offsetToLine(r.start);
    }

    int blockLastLine() {
        TextRange r = selection.toRange();
        int l = document.offsetToLine(r.end);
        if (r.end > r.start && r.end == document.lineToOffset(l) && l > document.offsetToLine(r.start)) {
            l = l - 1;
        }
        return l;
    }

    void indentLines(int outdent) {
        if (readOnly != 0) {
            return;
        }
        int l0 = blockFirstLine();
        int l1 = blockLastLine();
        int multi = 0;
        if (!selection.isEmpty() && l1 > l0) {
            multi = 1;
        }
        TextRange sel = selection.toRange();
        int startLineOff = document.lineToOffset(l0);
        undo.closeGroup();
        undo.beginCompoundEdit();
        String unit = indentUnit();
        int deltaFirst = 0;
        int deltaTotal = 0;
        int line = l1;
        while (line >= l0) {
            String t = document.getLine(line);
            int off = document.lineToOffset(line);
            int d = 0;
            if (outdent == 0) {
                if (t.length() > 0 || l0 == l1) {
                    undo.recordReplace(off, "", unit);
                    document.replace(off, 0, unit);
                    d = unit.length();
                }
            } else {
                int rm = 0;
                if (t.startsWith("\t")) {
                    rm = 1;
                } else {
                    while (rm < tabSize && rm < t.length() && t.charAt(rm) == 32) {
                        rm = rm + 1;
                    }
                }
                if (rm > 0) {
                    undo.recordReplace(off, t.substring(0, rm), "");
                    document.replace(off, rm, "");
                    d = 0 - rm;
                }
            }
            deltaTotal = deltaTotal + d;
            if (line == l0) {
                deltaFirst = d;
            }
            line = line - 1;
        }
        undo.endCompoundEdit();
        undo.closeGroup();
        if (multi != 0 || !selection.isEmpty()) {
            int a = startLineOff;
            int b = sel.end + deltaTotal;
            if (sel.start > startLineOff) {
                a = sel.start + deltaFirst;
                if (a < startLineOff) {
                    a = startLineOff;
                }
            }
            selection.anchor = a;
            selection.active = b;
            caret.offset = b;
        } else {
            int c = caret.offset + deltaTotal;
            if (c < startLineOff) {
                c = startLineOff;
            }
            selection.anchor = c;
            selection.active = c;
            caret.offset = c;
        }
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void toggleComment() {
        if (readOnly != 0) {
            return;
        }
        int l0 = blockFirstLine();
        int l1 = blockLastLine();
        int allCommented = 1;
        int minIndent = 100000;
        int any = 0;
        int line = l0;
        while (line <= l1) {
            String t = document.getLine(line);
            String ws = leadingWhitespace(t);
            if (t.length() > ws.length()) {
                any = 1;
                if (ws.length() < minIndent) {
                    minIndent = ws.length();
                }
                if (!t.substring(ws.length()).startsWith("//")) {
                    allCommented = 0;
                }
            }
            line = line + 1;
        }
        if (any == 0) {
            allCommented = 0;
            minIndent = 0;
        }
        TextRange sel = selection.toRange();
        int multi = 0;
        if (!selection.isEmpty()) {
            multi = 1;
        }
        int delta = 0;
        int deltaFirst = 0;
        undo.closeGroup();
        undo.beginCompoundEdit();
        line = l1;
        while (line >= l0) {
            String t = document.getLine(line);
            String ws = leadingWhitespace(t);
            int off = document.lineToOffset(line);
            int d = 0;
            if (allCommented != 0) {
                if (t.length() > ws.length()) {
                    int rm = 2;
                    if (t.length() > ws.length() + 2 && t.charAt(ws.length() + 2) == 32) {
                        rm = 3;
                    }
                    undo.recordReplace(off + ws.length(), t.substring(ws.length(), ws.length() + rm), "");
                    document.replace(off + ws.length(), rm, "");
                    d = 0 - rm;
                }
            } else {
                if (t.length() > ws.length() || l0 == l1) {
                    undo.recordReplace(off + minIndent, "", "// ");
                    document.replace(off + minIndent, 0, "// ");
                    d = 3;
                }
            }
            delta = delta + d;
            if (line == l0) {
                deltaFirst = d;
            }
            line = line - 1;
        }
        undo.endCompoundEdit();
        undo.closeGroup();
        if (multi != 0) {
            int a = sel.start + deltaFirst;
            int lo = document.lineToOffset(l0);
            if (a < lo) {
                a = lo;
            }
            int b = sel.end + delta;
            selection.anchor = a;
            selection.active = b;
            caret.offset = b;
        } else {
            int c = caret.offset + delta;
            int lo = document.lineToOffset(l0);
            if (c < lo) {
                c = lo;
            }
            selection.anchor = c;
            selection.active = c;
            caret.offset = c;
        }
        fireCaret();
        fireSelection();
        repaint();
    }

    void deleteLines() {
        if (readOnly != 0) {
            return;
        }
        int l0 = blockFirstLine();
        int l1 = blockLastLine();
        int a = document.lineToOffset(l0);
        int b = document.lineToOffset(l1) + document.lineLength(l1);
        if (l1 + 1 < document.getLineCount()) {
            b = b + 1;
        } else if (l0 > 0) {
            a = a - 1;
        }
        undo.closeGroup();
        applyEdit(a, b - a, "");
        undo.closeGroup();
        int target = l0;
        if (target >= document.getLineCount()) {
            target = document.getLineCount() - 1;
        }
        moveCaret(firstNonBlank(target), 0);
    }

    void duplicateLines(int down) {
        if (readOnly != 0) {
            return;
        }
        int l0 = blockFirstLine();
        int l1 = blockLastLine();
        int a = document.lineToOffset(l0);
        int b = document.lineToOffset(l1) + document.lineLength(l1);
        String block = document.substring(a, b);
        int anchor0 = selection.anchor;
        int active0 = selection.active;
        undo.closeGroup();
        if (down != 0) {
            applyEdit(b, 0, "\n" + block);
            int shift = b - a + 1;
            anchor0 = anchor0 + shift;
            active0 = active0 + shift;
        } else {
            applyEdit(a, 0, block + "\n");
        }
        undo.closeGroup();
        selection.anchor = anchor0;
        selection.active = active0;
        caret.offset = active0;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void moveLines(int dir) {
        if (readOnly != 0) {
            return;
        }
        int l0 = blockFirstLine();
        int l1 = blockLastLine();
        int n = document.getLineCount();
        if (dir < 0 && l0 == 0) {
            return;
        }
        if (dir > 0 && l1 >= n - 1) {
            return;
        }
        int a = document.lineToOffset(l0);
        int b = document.lineToOffset(l1) + document.lineLength(l1);
        String block = document.substring(a, b);
        int anchor = selection.anchor;
        int active = selection.active;
        undo.closeGroup();
        if (dir < 0) {
            int pa = document.lineToOffset(l0 - 1);
            String prev = document.getLine(l0 - 1);
            applyEdit(pa, b - pa, block + "\n" + prev);
            int shift = 0 - (prev.length() + 1);
            anchor = anchor + shift;
            active = active + shift;
        } else {
            String next = document.getLine(l1 + 1);
            int nb = b + 1 + next.length();
            applyEdit(a, nb - a, next + "\n" + block);
            int shift = next.length() + 1;
            anchor = anchor + shift;
            active = active + shift;
        }
        undo.closeGroup();
        selection.anchor = anchor;
        selection.active = active;
        caret.offset = active;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void selectLine() {
        int l = document.offsetToLine(caret.offset);
        int a = document.lineToOffset(l);
        int b = a + document.lineLength(l);
        if (l + 1 < document.getLineCount()) {
            b = b + 1;
        }
        selectRange(a, b);
    }

    void deleteToLineStart() {
        if (readOnly != 0) {
            return;
        }
        if (!selection.isEmpty()) {
            replaceSelection("");
            return;
        }
        int l = document.offsetToLine(caret.offset);
        int a = document.lineToOffset(l);
        if (caret.offset > a) {
            undo.closeGroup();
            applyEdit(a, caret.offset - a, "");
            undo.closeGroup();
        } else if (a > 0) {
            applyEdit(a - 1, 1, "");
        }
    }

    void deleteWord(int dir) {
        if (readOnly != 0) {
            return;
        }
        if (!selection.isEmpty()) {
            replaceSelection("");
            return;
        }
        int target = wordBoundary(caret.offset, dir);
        if (dir < 0 && target == caret.offset && caret.offset > 0) {
            target = caret.offset - 1;
        }
        int a = target;
        int b = caret.offset;
        if (dir > 0) {
            a = caret.offset;
            b = target;
        }
        if (b > a) {
            undo.closeGroup();
            applyEdit(a, b - a, "");
            undo.closeGroup();
        }
    }

    void smartBackspace() {
        if (readOnly != 0) {
            return;
        }
        if (!selection.isEmpty()) {
            undo.closeGroup();
            replaceSelection("");
            undo.closeGroup();
            notifyTyped(8);
            return;
        }
        int off = caret.offset;
        if (off == 0) {
            return;
        }
        int prev = charAtOffset(off - 1);
        int next = charAtOffset(off);
        undo.coalescing = 1;
        if (smartEdit != 0 && closerFor(prev) != 0 && next == closerFor(prev)) {
            undo.coalescing = 0;
            undo.closeGroup();
            applyEdit(off - 1, 2, "");
            undo.closeGroup();
            notifyTyped(8);
            return;
        }
        /* Backspace inside leading indentation removes a whole indent unit. */
        TextPosition p = document.offsetToPosition(off);
        String line = document.getLine(p.line);
        String before = line.substring(0, p.column);
        if (smartEdit != 0 && insertSpaces != 0 && before.length() > 0
                && leadingWhitespace(before).length() == before.length() && before.endsWith(" ")) {
            int rm = before.length() % tabSize;
            if (rm == 0) {
                rm = tabSize;
            }
            if (rm > before.length()) {
                rm = before.length();
            }
            applyEdit(off - rm, rm, "");
            undo.coalescing = 0;
            notifyTyped(8);
            return;
        }
        applyEdit(off - 1, 1, "");
        undo.coalescing = 0;
        notifyTyped(8);
    }

    // ------------------------------------------------------------------ commands

    void copySelection(int cut) {
        TextRange r = selection.toRange();
        if (r.isEmpty()) {
            int l = document.offsetToLine(caret.offset);
            int a = document.lineToOffset(l);
            int b = a + document.lineLength(l);
            Clipboard.setText(document.substring(a, b) + "\n");
            if (cut != 0 && readOnly == 0) {
                deleteLines();
            }
            return;
        }
        Clipboard.setText(document.substring(r.start, r.end));
        if (cut != 0 && readOnly == 0) {
            undo.closeGroup();
            replaceSelection("");
            undo.closeGroup();
        }
    }

    void afterUndoRedo() {
        caret.offset = undo.lastCaret;
        if (caret.offset > document.getLength()) {
            caret.offset = document.getLength();
        }
        selection.anchor = caret.offset;
        selection.active = caret.offset;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
    }

    void executeCommand(int cmd) {
        TextRange r = selection.toRange();
        TextPosition p = document.offsetToPosition(caret.offset);
        int lineNo = 0;
        if (cmd == EditorCommand.COPY) {
            copySelection(0);
        } else if (cmd == EditorCommand.CUT) {
            copySelection(1);
        } else if (cmd == EditorCommand.PASTE) {
            if (readOnly == 0) {
                insertBlock(TextUtil.normalizeNewlines(Clipboard.getText()));
            }
        } else if (cmd == EditorCommand.UNDO) {
            if (readOnly == 0) {
                undo.closeGroup();
                undo.undo();
                afterUndoRedo();
            }
        } else if (cmd == EditorCommand.REDO) {
            if (readOnly == 0) {
                undo.redo();
                afterUndoRedo();
            }
        } else if (cmd == EditorCommand.SELECT_ALL) {
            undo.closeGroup();
            selection.anchor = 0;
            selection.active = document.getLength();
            caret.offset = selection.active;
            fireCaret();
            fireSelection();
            repaint();
        } else if (cmd == EditorCommand.MOVE_LEFT) {
            if (!r.isEmpty() && shiftSelecting == 0) {
                moveCaret(r.start, 0);
            } else {
                moveCaret(caret.offset - 1, shiftSelecting);
            }
        } else if (cmd == EditorCommand.MOVE_RIGHT) {
            if (!r.isEmpty() && shiftSelecting == 0) {
                moveCaret(r.end, 0);
            } else {
                moveCaret(caret.offset + 1, shiftSelecting);
            }
        } else if (cmd == EditorCommand.MOVE_UP || cmd == EditorCommand.MOVE_DOWN) {
            int keep = caret.preferredColumn;
            lineNo = p.line - 1;
            if (cmd == EditorCommand.MOVE_DOWN) {
                lineNo = p.line + 1;
            }
            if (hiddenTotal > 0 && lineNo >= 0 && lineNo < document.getLineCount()) {
                int hi = hiddenIndex(lineNo);
                if (hi >= 0) {
                    if (cmd == EditorCommand.MOVE_DOWN) {
                        lineNo = hideTo.get(hi) + 1;
                    } else {
                        lineNo = hideFrom.get(hi) - 1;
                    }
                }
            }
            if (lineNo < 0) {
                moveCaret(0, shiftSelecting);
            } else if (lineNo >= document.getLineCount()) {
                moveCaret(document.getLength(), shiftSelecting);
            } else {
                moveCaret(document.positionToOffset(lineNo, keep), shiftSelecting);
            }
            caret.preferredColumn = keep;
        } else if (cmd == EditorCommand.PAGE_UP || cmd == EditorCommand.PAGE_DOWN) {
            int keep = caret.preferredColumn;
            int vis = visibleLines() - 1;
            int r0 = rowOf(p.line);
            int r1 = r0 - vis;
            if (cmd == EditorCommand.PAGE_DOWN) {
                r1 = r0 + vis;
            }
            if (r1 < 0) {
                r1 = 0;
            }
            if (r1 >= visibleRowCount()) {
                r1 = visibleRowCount() - 1;
            }
            lineNo = lineOfRow(r1);
            setScrollPx(scrollPx() + (r1 - r0) * lineH);
            moveCaret(document.positionToOffset(lineNo, keep), shiftSelecting);
            caret.preferredColumn = keep;
        } else if (cmd == EditorCommand.DELETE_BACKWARD) {
            smartBackspace();
        } else if (cmd == EditorCommand.DELETE_FORWARD) {
            deleteSelectionOr(1);
            notifyTyped(127);
        } else if (cmd == EditorCommand.NEWLINE) {
            typeNewline(0, 0);
        } else if (cmd == EditorCommand.HOME) {
            int fnb = firstNonBlank(p.line);
            int ls = document.lineToOffset(p.line);
            if (caret.offset == fnb) {
                moveCaret(ls, shiftSelecting);
            } else {
                moveCaret(fnb, shiftSelecting);
            }
        } else if (cmd == EditorCommand.END) {
            moveCaret(document.lineToOffset(p.line) + document.lineLength(p.line), shiftSelecting);
        } else if (cmd == EditorCommand.DOC_START) {
            moveCaret(0, shiftSelecting);
        } else if (cmd == EditorCommand.DOC_END) {
            moveCaret(document.getLength(), shiftSelecting);
        } else if (cmd == EditorCommand.WORD_LEFT) {
            moveCaret(wordBoundary(caret.offset, -1), shiftSelecting);
        } else if (cmd == EditorCommand.WORD_RIGHT) {
            moveCaret(wordBoundary(caret.offset, 1), shiftSelecting);
        } else if (cmd == EditorCommand.TOGGLE_COMMENT) {
            toggleComment();
        } else if (cmd == EditorCommand.INDENT) {
            indentLines(0);
        } else if (cmd == EditorCommand.OUTDENT) {
            indentLines(1);
        } else if (cmd == EditorCommand.DELETE_LINE) {
            deleteLines();
        } else if (cmd == EditorCommand.DUPLICATE_LINE) {
            duplicateLines(1);
        } else if (cmd == EditorCommand.COPY_LINE_UP) {
            duplicateLines(0);
        } else if (cmd == EditorCommand.MOVE_LINE_UP) {
            moveLines(-1);
        } else if (cmd == EditorCommand.MOVE_LINE_DOWN) {
            moveLines(1);
        } else if (cmd == EditorCommand.SELECT_LINE) {
            selectLine();
        } else if (cmd == EditorCommand.INSERT_LINE_BELOW) {
            typeNewline(0, 1);
        } else if (cmd == EditorCommand.INSERT_LINE_ABOVE) {
            typeNewline(1, 0);
        } else if (cmd == EditorCommand.DELETE_WORD_BACK) {
            deleteWord(-1);
        } else if (cmd == EditorCommand.DELETE_WORD_FORWARD) {
            deleteWord(1);
        } else if (cmd == EditorCommand.DELETE_TO_LINE_START) {
            deleteToLineStart();
        }
    }

    // ------------------------------------------------------------------ mouse

    int inVBar(int x, int y) {
        if (contentHeight() > height && x >= width - 12) {
            return 1;
        }
        return 0;
    }

    int inHBar(int x, int y) {
        if (maxScrollX() > 0 && y >= height - 12) {
            return 1;
        }
        return 0;
    }

    void dragVBar(int y) {
        int total = contentHeight();
        int thumbH = height * height / total;
        if (thumbH < 28) {
            thumbH = 28;
        }
        int track = height - thumbH;
        if (track < 1) {
            return;
        }
        int mx = maxScrollY();
        int top = y - dragBarGrab;
        setScrollPx(top * mx / track);
        repaint();
    }

    void dragHBar(int x) {
        int avail = width - textLeft();
        int content = avail + maxScrollX();
        int thumbW = avail * avail / content;
        if (thumbW < 28) {
            thumbW = 28;
        }
        int track = avail - thumbW;
        if (track < 1) {
            return;
        }
        int left = x - textLeft() - dragBarGrab;
        setScrollX(left * maxScrollX() / track);
        repaint();
    }

    void handleMouse(MouseEvent event) {
        int type = event.eventType;
        if (type == 8 || type == 9) {
            handleWheel(event);
            return;
        }
        if (type == 1) {
            handleMouseDown(event);
        } else if (type == 5) {
            if (mouseDownFlag != 0) {
                handleMouseDrag(event);
            } else {
                updateHoverTip(event);
            }
        } else if (type == 2) {
            mouseDownFlag = 0;
            dragMode = 0;
            dragBar = 0;
        }
    }

    /** Shows the message of the diagnostic under the mouse pointer as a native tooltip. */
    void updateHoverTip(MouseEvent event) {
        String tip = "";
        if (diagnostics.size() > 0) {
            ensureMetrics();
            int docY = event.y + scrollPx();
            if (docY >= 0 && docY / lineH < visibleRowCount()) {
                int off = hitTest(event.x, event.y);
                int i = 0;
                while (i < diagnostics.size()) {
                    EditorDiagnostic d = diagnostics.get(i);
                    if (off >= d.start && off <= Math.max(d.end, d.start + 1)) {
                        if (tip.length() > 0) {
                            tip = tip + "\n";
                        }
                        tip = tip + d.message;
                    }
                    i = i + 1;
                }
            }
        }
        if (!tip.equals(hoverTip)) {
            hoverTip = tip;
            setToolTip(tip);
        }
    }

    void handleWheel(MouseEvent event) {
        ensureMetrics();
        int dx = 0;
        int dy = 0;
        if (event.pixelDelta != 0) {
            if (event.horizontal != 0) {
                dx = event.pixelDelta;
            } else {
                dy = event.pixelDelta;
            }
        } else {
            if (event.horizontal != 0) {
                dx = event.wheelDelta * 40;
            } else {
                dy = event.wheelDelta * lineH * 3;
            }
        }
        if (event.shiftDown != 0 && dx == 0) {
            dx = dy;
            dy = 0;
        }
        if (dy != 0) {
            setScrollPx(scrollPx() - dy);
        }
        if (dx != 0) {
            setScrollX(viewport.xOffset - dx);
        }
        repaint();
    }

    void handleMouseDown(MouseEvent event) {
        ensureMetrics();
        ensureBlinkTimer();
        requestFocus();
        mouseDownFlag = 1;
        int mx = event.getX();
        int my = event.getY();
        undo.closeGroup();
        if (event.button == 3) {
            int off = hitTest(mx, my);
            TextRange sr = selection.toRange();
            if (off < sr.start || off > sr.end) {
                moveCaret(off, 0);
            }
            mouseDownFlag = 0;
            return;
        }
        if (inVBar(mx, my) != 0) {
            int total = contentHeight();
            int thumbH = height * height / total;
            if (thumbH < 28) {
                thumbH = 28;
            }
            int mxs = maxScrollY();
            int track = height - thumbH;
            int thumbTop = 0;
            if (mxs > 0 && track > 0) {
                thumbTop = scrollPx() * track / mxs;
            }
            if (my >= thumbTop && my <= thumbTop + thumbH) {
                dragBarGrab = my - thumbTop;
            } else {
                dragBarGrab = thumbH / 2;
                dragVBar(my);
            }
            dragBar = 1;
            return;
        }
        if (inHBar(mx, my) != 0) {
            int avail = width - textLeft();
            int content = avail + maxScrollX();
            int thumbW = avail * avail / content;
            if (thumbW < 28) {
                thumbW = 28;
            }
            int track = avail - thumbW;
            int thumbLeft = 0;
            if (maxScrollX() > 0 && track > 0) {
                thumbLeft = textLeft() + viewport.xOffset * track / maxScrollX();
            }
            if (mx >= thumbLeft && mx <= thumbLeft + thumbW) {
                dragBarGrab = mx - thumbLeft;
            } else {
                dragBarGrab = thumbW / 2;
                dragHBar(mx);
            }
            dragBar = 2;
            return;
        }
        int off = hitTest(mx, my);
        int line = document.offsetToLine(off);
        if (foldingEnabled != 0 && showLineNumbers != 0 && mx >= gutterWidthFor() - 16 && mx < gutterWidthFor()
                && my + scrollPx() < visibleRowCount() * lineH) {
            if (foldEndAt(line) >= 0) {
                mouseDownFlag = 0;
                toggleFold(line);
                return;
            }
        }
        if (foldingEnabled != 0 && mx >= textLeft() && isFoldedAt(line) != 0 && my + scrollPx() < visibleRowCount() * lineH) {
            String ft = document.getLine(line);
            double fx = textLeft() - viewport.xOffset + colToX(ft, ft.length()) + 8;
            if (mx >= fx && mx <= fx + foldPlaceholderWidth()) {
                mouseDownFlag = 0;
                toggleFold(line);
                return;
            }
        }
        if (gutterListener != null && mx < 18 && showLineNumbers != 0 && my + scrollPx() < visibleRowCount() * lineH) {
            mouseDownFlag = 0;
            gutterListener.clicked(line);
            return;
        }
        if (mx < gutterWidthFor() && showLineNumbers != 0) {
            int a = document.lineToOffset(line);
            int b = a + document.lineLength(line);
            if (line + 1 < document.getLineCount()) {
                b = b + 1;
            }
            selection.anchor = a;
            selection.active = b;
            caret.offset = b;
            dragMode = 3;
            dragStart = a;
            dragEnd = b;
            fireCaret();
            fireSelection();
            repaint();
            return;
        }
        if (event.clickCount >= 3) {
            int a = document.lineToOffset(line);
            int b = a + document.lineLength(line);
            if (line + 1 < document.getLineCount()) {
                b = b + 1;
            }
            selection.anchor = a;
            selection.active = b;
            caret.offset = b;
            dragMode = 3;
            dragStart = a;
            dragEnd = b;
        } else if (event.clickCount == 2) {
            int a = wordStart(off);
            int b = wordEnd(off);
            selection.anchor = a;
            selection.active = b;
            caret.offset = b;
            dragMode = 2;
            dragStart = a;
            dragEnd = b;
        } else if (event.shiftDown != 0) {
            moveCaret(off, 1);
            dragMode = 1;
            return;
        } else {
            moveCaret(off, 0);
            dragMode = 1;
            if (event.metaDown != 0 && commandClick != null) {
                commandClick.handle(event);
            }
            return;
        }
        caret.blinkOn = 1;
        fireCaret();
        fireSelection();
        repaint();
    }

    int wordStart(int off) {
        int i = off;
        while (i > 0 && TextUtil.isWordChar(charAtOffset(i - 1)) != 0) {
            i = i - 1;
        }
        return i;
    }

    int wordEnd(int off) {
        int n = document.getLength();
        int i = off;
        while (i < n && TextUtil.isWordChar(charAtOffset(i)) != 0) {
            i = i + 1;
        }
        return i;
    }

    void handleMouseDrag(MouseEvent event) {
        ensureMetrics();
        int my = event.getY();
        int mx = event.getX();
        if (dragBar == 1) {
            dragVBar(my);
            return;
        }
        if (dragBar == 2) {
            dragHBar(mx);
            return;
        }
        if (my < 0) {
            setScrollPx(scrollPx() + my / 2 - 4);
        } else if (my > height) {
            setScrollPx(scrollPx() + (my - height) / 2 + 4);
        }
        if (mx < textLeft()) {
            setScrollX(viewport.xOffset - 12);
        } else if (mx > width - 20) {
            setScrollX(viewport.xOffset + 12);
        }
        int off = hitTest(mx, my);
        if (dragMode == 2) {
            int a = wordStart(off);
            int b = wordEnd(off);
            if (off < dragStart) {
                selection.anchor = dragEnd;
                selection.active = a;
            } else {
                selection.anchor = dragStart;
                selection.active = b;
            }
            caret.offset = selection.active;
            fireCaret();
            fireSelection();
            repaint();
        } else if (dragMode == 3) {
            int line = document.offsetToLine(off);
            int a = document.lineToOffset(line);
            int b = a + document.lineLength(line);
            if (line + 1 < document.getLineCount()) {
                b = b + 1;
            }
            if (off < dragStart) {
                selection.anchor = dragEnd;
                selection.active = a;
            } else {
                selection.anchor = dragStart;
                selection.active = b;
            }
            caret.offset = selection.active;
            fireCaret();
            fireSelection();
            repaint();
        } else {
            moveCaret(off, 1);
        }
    }

    // ------------------------------------------------------------------ keyboard

    void handleKey(KeyEvent event) {
        if (keyPre != null) {
            keyPre.handle(event);
            if (event.isConsumed()) {
                return;
            }
        }
        int code = event.keyCode;
        shiftSelecting = event.shiftDown;
        int shift = event.shiftDown;
        int meta = event.metaDown;
        int ctrl = event.ctrlDown;
        int alt = event.altDown;
        int k = code;
        if (k >= 97 && k <= 122) {
            k = k - 32;
        }
        caret.blinkOn = 1;
        ensureBlinkTimer();
        int cmdKey = 0;
        if (meta != 0 || ctrl != 0) {
            cmdKey = 1;
        }
        if (cmdKey != 0 && alt == 0) {
            if (k == 67) {
                executeCommand(EditorCommand.COPY);
                return;
            }
            if (k == 88) {
                executeCommand(EditorCommand.CUT);
                return;
            }
            if (k == 86) {
                executeCommand(EditorCommand.PASTE);
                return;
            }
            if (k == 90) {
                if (shift != 0) {
                    executeCommand(EditorCommand.REDO);
                } else {
                    executeCommand(EditorCommand.UNDO);
                }
                return;
            }
            if (k == 89) {
                executeCommand(EditorCommand.REDO);
                return;
            }
            if (k == 65 && meta != 0) {
                executeCommand(EditorCommand.SELECT_ALL);
                return;
            }
            if (k == 47) {
                executeCommand(EditorCommand.TOGGLE_COMMENT);
                return;
            }
            if (k == 93) {
                executeCommand(EditorCommand.INDENT);
                return;
            }
            if (k == 91) {
                executeCommand(EditorCommand.OUTDENT);
                return;
            }
            if (k == 75 && shift != 0) {
                executeCommand(EditorCommand.DELETE_LINE);
                return;
            }
            if (k == 68 && shift != 0) {
                executeCommand(EditorCommand.DUPLICATE_LINE);
                return;
            }
            if (k == 76 && ctrl != 0 && meta == 0) {
                executeCommand(EditorCommand.SELECT_LINE);
                return;
            }
            if (code == 13) {
                if (shift != 0) {
                    executeCommand(EditorCommand.INSERT_LINE_ABOVE);
                } else {
                    executeCommand(EditorCommand.INSERT_LINE_BELOW);
                }
                return;
            }
            if (meta != 0) {
                if (code == 37) {
                    executeCommand(EditorCommand.HOME);
                    return;
                }
                if (code == 39) {
                    executeCommand(EditorCommand.END);
                    return;
                }
                if (code == 38) {
                    executeCommand(EditorCommand.DOC_START);
                    return;
                }
                if (code == 40) {
                    executeCommand(EditorCommand.DOC_END);
                    return;
                }
                if (code == 8) {
                    executeCommand(EditorCommand.DELETE_TO_LINE_START);
                    return;
                }
            } else {
                if (code == 37) {
                    executeCommand(EditorCommand.WORD_LEFT);
                    return;
                }
                if (code == 39) {
                    executeCommand(EditorCommand.WORD_RIGHT);
                    return;
                }
            }
        }
        if (alt != 0 && cmdKey == 0) {
            if (code == 37) {
                executeCommand(EditorCommand.WORD_LEFT);
                return;
            }
            if (code == 39) {
                executeCommand(EditorCommand.WORD_RIGHT);
                return;
            }
            if (code == 38) {
                if (shift != 0) {
                    executeCommand(EditorCommand.COPY_LINE_UP);
                } else {
                    executeCommand(EditorCommand.MOVE_LINE_UP);
                }
                return;
            }
            if (code == 40) {
                if (shift != 0) {
                    executeCommand(EditorCommand.DUPLICATE_LINE);
                } else {
                    executeCommand(EditorCommand.MOVE_LINE_DOWN);
                }
                return;
            }
            if (code == 8) {
                executeCommand(EditorCommand.DELETE_WORD_BACK);
                return;
            }
            if (code == 46) {
                executeCommand(EditorCommand.DELETE_WORD_FORWARD);
                return;
            }
        }
        if (ctrl != 0 && meta == 0 && alt == 0) {
            if (k == 65) {
                executeCommand(EditorCommand.HOME);
                return;
            }
            if (k == 69) {
                executeCommand(EditorCommand.END);
                return;
            }
        }
        if (code == 37) {
            executeCommand(EditorCommand.MOVE_LEFT);
        } else if (code == 39) {
            executeCommand(EditorCommand.MOVE_RIGHT);
        } else if (code == 38) {
            executeCommand(EditorCommand.MOVE_UP);
        } else if (code == 40) {
            executeCommand(EditorCommand.MOVE_DOWN);
        } else if (code == 33) {
            executeCommand(EditorCommand.PAGE_UP);
        } else if (code == 34) {
            executeCommand(EditorCommand.PAGE_DOWN);
        } else if (code == 8) {
            executeCommand(EditorCommand.DELETE_BACKWARD);
        } else if (code == 46) {
            executeCommand(EditorCommand.DELETE_FORWARD);
        } else if (code == 13) {
            executeCommand(EditorCommand.NEWLINE);
        } else if (code == 36) {
            executeCommand(EditorCommand.HOME);
        } else if (code == 35) {
            executeCommand(EditorCommand.END);
        } else if (code == 27) {
            if (!selection.isEmpty()) {
                moveCaret(caret.offset, 0);
            }
        } else if (code == 9) {
            if (shift != 0) {
                indentLines(1);
            } else if (!selection.isEmpty() && blockLastLine() > blockFirstLine()) {
                indentLines(0);
            } else {
                insertBlock(indentUnit());
            }
        } else if (cmdKey == 0 && alt == 0) {
            int ch = event.typedChar;
            if (ch <= 0) {
                ch = code;
            }
            if (ch >= 32 && ch < 127) {
                typeChar(ch);
            }
        }
    }

    void handleTextInput(TextInputEvent ev) {
        if (ev.marked != 0) {
            compositionText = ev.text;
            if (ev.text.length() > 0) {
                compositionActive = 1;
            } else {
                compositionActive = 0;
            }
            repaint();
            return;
        }
        compositionActive = 0;
        compositionText = "";
        String t = ev.text;
        if (t == null || t.length() == 0 || readOnly != 0) {
            repaint();
            return;
        }
        caret.blinkOn = 1;
        ensureBlinkTimer();
        if (t.length() == 1) {
            int c = t.charAt(0);
            if (c == 10 || c == 13) {
                typeNewline(0, 0);
            } else if (c == 9) {
                insertBlock(indentUnit());
            } else if (c >= 32) {
                typeChar(c);
            }
            return;
        }
        insertBlock(TextUtil.normalizeNewlines(t));
    }

    void beginComposition() {
        compositionActive = 1;
        compositionText = "";
    }

    void updateComposition(String text) {
        compositionText = text;
        repaint();
    }

    void commitComposition() {
        if (compositionActive != 0 && compositionText.length() > 0) {
            insertText(compositionText);
        }
        compositionActive = 0;
        compositionText = "";
    }

    // ------------------------------------------------------------------ find / replace

    void setFind(String query, int caseSensitive, int wholeWord) {
        if (query == null) {
            query = "";
        }
        findQuery = query;
        findCase = caseSensitive;
        findWhole = wholeWord;
        repaint();
    }

    void clearFind() {
        findQuery = "";
        repaint();
    }

    /** Selects the next (dir > 0) or previous match of the current find query; returns whether one was found. */
    boolean findStep(int dir, boolean keepCurrent) {
        if (findQuery.length() == 0) {
            return false;
        }
        String text = document.getText();
        TextRange r = null;
        if (dir > 0) {
            int from = selection.end();
            if (keepCurrent) {
                from = selection.start();
            }
            r = TextSearch.findIn(text, findQuery, from, findCase, findWhole);
            if (r == null) {
                r = TextSearch.findIn(text, findQuery, 0, findCase, findWhole);
            }
        } else {
            r = TextSearch.findBackIn(text, findQuery, selection.start(), findCase, findWhole);
            if (r == null) {
                r = TextSearch.findBackIn(text, findQuery, text.length(), findCase, findWhole);
            }
        }
        if (r == null) {
            return false;
        }
        selectRange(r.start, r.end);
        return true;
    }

    void findNext(String query) {
        findQuery = query;
        findStep(1, false);
    }

    /** Returns {1-based index of the current match (0 if none), total matches}. */
    int[] findStats() {
        int[] out = new int[2];
        out[0] = 0;
        out[1] = 0;
        if (findQuery.length() == 0) {
            return out;
        }
        String text = document.getText();
        int from = 0;
        int total = 0;
        int cur = selection.start();
        TextRange r = TextSearch.findIn(text, findQuery, from, findCase, findWhole);
        while (r != null && total < 100000) {
            total = total + 1;
            if (r.start == cur && selection.end() == r.end) {
                out[0] = total;
            }
            from = r.end;
            if (r.end <= r.start) {
                from = r.start + 1;
            }
            r = TextSearch.findIn(text, findQuery, from, findCase, findWhole);
        }
        out[1] = total;
        return out;
    }

    int selectionIsMatch() {
        if (findQuery.length() == 0 || selection.isEmpty()) {
            return 0;
        }
        TextRange sr = selection.toRange();
        String s = document.substring(sr.start, sr.end);
        if (s.length() != findQuery.length()) {
            return 0;
        }
        TextRange m = TextSearch.findIn(s, findQuery, 0, findCase, 0);
        if (m != null && m.start == 0 && m.end == s.length()) {
            return 1;
        }
        return 0;
    }

    boolean replaceCurrent(String replacement) {
        if (readOnly != 0 || findQuery.length() == 0) {
            return false;
        }
        if (selectionIsMatch() != 0) {
            undo.closeGroup();
            replaceSelection(replacement);
            undo.closeGroup();
        }
        return findStep(1, false);
    }

    int replaceAllMatches(String replacement) {
        if (readOnly != 0 || findQuery.length() == 0) {
            return 0;
        }
        String text = document.getText();
        ArrayList<int> starts = new ArrayList<int>();
        int from = 0;
        TextRange r = TextSearch.findIn(text, findQuery, from, findCase, findWhole);
        while (r != null) {
            starts.add(r.start);
            from = r.end;
            if (r.end <= r.start) {
                from = r.start + 1;
            }
            r = TextSearch.findIn(text, findQuery, from, findCase, findWhole);
        }
        int n = starts.size();
        if (n == 0) {
            return 0;
        }
        int qlen = findQuery.length();
        undo.closeGroup();
        undo.beginCompoundEdit();
        int i = n - 1;
        while (i >= 0) {
            int off = starts.get(i);
            undo.recordReplace(off, text.substring(off, off + qlen), replacement);
            document.replace(off, qlen, replacement);
            i = i - 1;
        }
        undo.endCompoundEdit();
        undo.closeGroup();
        int first = starts.get(0);
        selection.anchor = first;
        selection.active = first;
        caret.offset = first;
        ensureCaretVisible();
        fireCaret();
        fireSelection();
        repaint();
        return n;
    }

    // ------------------------------------------------------------------ caret info (brackets, word highlight)

    void refreshCaretInfo() {
        bracketA = -1;
        bracketB = -1;
        hlWord = "";
        if (document.getLength() > 0) {
            computeBracketMatch();
            computeWordHighlight();
        }
    }

    void computeWordHighlight() {
        TextRange r = selection.toRange();
        int a = r.start;
        int b = r.end;
        if (r.isEmpty()) {
            a = wordStart(caret.offset);
            b = wordEnd(caret.offset);
        } else {
            if (document.offsetToLine(a) != document.offsetToLine(b)) {
                return;
            }
            if (wordStart(a) != a || wordEnd(a) != b) {
                return;
            }
        }
        if (b - a < 2 || b - a > 60) {
            return;
        }
        String w = document.substring(a, b);
        int c0 = w.charAt(0);
        if (c0 >= 48 && c0 <= 57) {
            return;
        }
        hlWord = w;
    }

    int bracketPartner(int c) {
        if (c == 40) {
            return 41;
        }
        if (c == 41) {
            return 40;
        }
        if (c == 91) {
            return 93;
        }
        if (c == 93) {
            return 91;
        }
        if (c == 123) {
            return 125;
        }
        if (c == 125) {
            return 123;
        }
        return 0;
    }

    void computeBracketMatch() {
        int at = -1;
        int c = charAtOffset(caret.offset);
        if (bracketPartner(c) != 0) {
            at = caret.offset;
        } else {
            c = charAtOffset(caret.offset - 1);
            if (bracketPartner(c) != 0) {
                at = caret.offset - 1;
            }
        }
        if (at < 0) {
            return;
        }
        TextPosition p = document.offsetToPosition(at);
        int ty = tokenTypeAt(p.line, p.column);
        if (ty == TokenType.COMMENT || ty == TokenType.STRING) {
            return;
        }
        int partner = bracketPartner(c);
        int forward = 0;
        if (c == 40 || c == 91 || c == 123) {
            forward = 1;
        }
        int depth = 0;
        int line = p.line;
        int col = p.column;
        int steps = 0;
        int n = document.getLineCount();
        if (forward != 0) {
            while (line < n && steps < 400) {
                String t = document.getLine(line);
                int i = col;
                while (i < t.length()) {
                    int ch = t.charAt(i);
                    if (ch == c || ch == partner) {
                        int tt = tokenTypeAt(line, i);
                        if (tt != TokenType.COMMENT && tt != TokenType.STRING) {
                            if (ch == c) {
                                depth = depth + 1;
                            } else {
                                depth = depth - 1;
                                if (depth == 0) {
                                    bracketA = at;
                                    bracketB = document.lineToOffset(line) + i;
                                    return;
                                }
                            }
                        }
                    }
                    i = i + 1;
                }
                line = line + 1;
                col = 0;
                steps = steps + 1;
            }
        } else {
            while (line >= 0 && steps < 400) {
                String t = document.getLine(line);
                int i = t.length() - 1;
                if (line == p.line) {
                    i = col;
                }
                while (i >= 0) {
                    int ch = t.charAt(i);
                    if (ch == c || ch == partner) {
                        int tt = tokenTypeAt(line, i);
                        if (tt != TokenType.COMMENT && tt != TokenType.STRING) {
                            if (ch == c) {
                                depth = depth + 1;
                            } else {
                                depth = depth - 1;
                                if (depth == 0) {
                                    bracketA = at;
                                    bracketB = document.lineToOffset(line) + i;
                                    return;
                                }
                            }
                        }
                    }
                    i = i - 1;
                }
                line = line - 1;
                steps = steps + 1;
            }
        }
    }

    // ------------------------------------------------------------------ painting

    /** Fills the pixel span of [fromCol, toCol) of a line. */
    void fillCols(Graphics g, String text, int fromCol, int toCol, int y, int extra) {
        double x0 = textLeft() - viewport.xOffset + colToX(text, fromCol);
        double x1 = textLeft() - viewport.xOffset + colToX(text, toCol) + extra;
        g.fillRect(x0, y, x1 - x0, lineH);
    }

    void paintMatches(Graphics g, String text, int y, String needle, int cs, int whole, Color color) {
        if (needle.length() == 0) {
            return;
        }
        int from = 0;
        int guard = 0;
        TextRange m = TextSearch.findIn(text, needle, from, cs, whole);
        g.setColor(color);
        while (m != null && guard < 200) {
            fillCols(g, text, m.start, m.end, y, 0);
            from = m.end;
            if (m.end <= m.start) {
                from = m.start + 1;
            }
            m = TextSearch.findIn(text, needle, from, cs, whole);
            guard = guard + 1;
        }
    }

    void drawRun(Graphics g, String text, int a, int b, int ty, int y, int splitCol, double splitPx) {
        int i = a;
        int segStart = a;
        int tl = textLeft();
        while (i <= b) {
            int brk = 0;
            if (i == b) {
                brk = 1;
            } else if (text.charAt(i) == 9 || i == splitCol) {
                brk = 1;
            }
            if (brk != 0) {
                int segEnd = i;
                if (i < b && text.charAt(i) == 9) {
                    segEnd = i;
                }
                if (segEnd > segStart) {
                    double x = tl - viewport.xOffset + colToX(text, segStart);
                    if (segStart >= splitCol) {
                        x = x + splitPx;
                    }
                    if (x < width && x + (segEnd - segStart) * charW * 2.0 > tl) {
                        g.drawText(text.substring(segStart, segEnd), x, y + 2);
                    }
                }
                if (i < b && text.charAt(i) == 9) {
                    segStart = i + 1;
                } else {
                    segStart = i;
                }
            }
            i = i + 1;
        }
    }

    void paintLineText(Graphics g, String text, int line, int y, int splitCol, double splitPx) {
        int n = text.length();
        if (n == 0) {
            return;
        }
        LineTok lt = tokensForLine(line);
        if (lt == null) {
            g.setColor(theme.foreground);
            drawRun(g, text, 0, n, TokenType.NORMAL, y, splitCol, splitPx);
            return;
        }
        int i = 0;
        while (i < lt.count) {
            int a = lt.data[i];
            int b = lt.data[i + 1];
            int ty = lt.data[i + 2];
            if (b > n) {
                b = n;
            }
            if (a < b) {
                g.setColor(theme.colorForToken(ty));
                drawRun(g, text, a, b, ty, y, splitCol, splitPx);
            }
            i = i + 3;
        }
    }

    void paintEditor(Graphics g) {
        ensureMetrics();
        viewport.width = width;
        viewport.height = height;
        int gw = gutterWidthFor();
        refreshMaxCols();
        ensureFolds();
        setScrollPx(scrollPx());
        setScrollX(viewport.xOffset);
        g.setColor(theme.background);
        g.fillRect(0, 0, width, height);
        g.setFont(font);
        int first = viewport.firstLine;
        int yOff = viewport.yOffset;
        int lines = visibleRowCount();
        int last = first + (height + yOff) / lineH;
        if (last >= lines) {
            last = lines - 1;
        }
        TextPosition caretPos = document.offsetToPosition(caret.offset);
        EditorMetrics.visibleLinesPainted = last - first + 1;
        int sel0 = selection.start();
        int sel1 = selection.end();
        int tl = textLeft();
        double compPx = 0.0;
        if (compositionActive != 0) {
            compPx = colToX(compositionText, compositionText.length());
        }
        g.setClip(gw, 0, width - gw, height);
        int row = first;
        while (row <= last) {
            int line = lineOfRow(row);
            int y = (row - first) * lineH - yOff;
            String text = document.getLine(line);
            int lineOff = document.lineToOffset(line);
            int lineEnd = lineOff + text.length();
            if (line == caretPos.line && sel0 == sel1) {
                g.setColor(theme.currentLine);
                g.fillRect(gw, y, width - gw, lineH);
            }
            if (line == execLine) {
                g.setColor(new Color(255, 214, 90, 110));
                g.fillRect(gw, y, width - gw, lineH);
            }
            if (findQuery.length() > 0) {
                paintMatches(g, text, y, findQuery, findCase, findWhole, theme.findMatch);
            }
            if (hlWord.length() > 0) {
                paintMatches(g, text, y, hlWord, 1, 1, theme.wordHighlight);
            }
            if (bracketA >= 0 && bracketB >= 0) {
                g.setColor(theme.bracketMatch);
                if (bracketA >= lineOff && bracketA < lineEnd) {
                    fillCols(g, text, bracketA - lineOff, bracketA - lineOff + 1, y, 0);
                }
                if (bracketB >= lineOff && bracketB < lineEnd) {
                    fillCols(g, text, bracketB - lineOff, bracketB - lineOff + 1, y, 0);
                }
            }
            if (sel0 < sel1 && sel0 <= lineEnd && sel1 > lineOff) {
                int a = sel0;
                if (a < lineOff) {
                    a = lineOff;
                }
                int b = sel1;
                if (b > lineEnd) {
                    b = lineEnd;
                }
                int extra = 0;
                if (sel1 > lineEnd) {
                    extra = (int) charW;
                }
                g.setColor(theme.selection);
                fillCols(g, text, a - lineOff, b - lineOff, y, extra);
            }
            int splitCol = 1000000;
            if (compositionActive != 0 && line == caretPos.line) {
                splitCol = caretPos.column;
            }
            paintLineText(g, text, line, y, splitCol, compPx);
            if (foldingEnabled != 0 && foldedStarts.size() > 0 && isFoldedAt(line) != 0) {
                double fx = tl - viewport.xOffset + colToX(text, text.length()) + 8;
                g.setColor(theme.selection);
                g.fillRect(fx, y + 2, foldPlaceholderWidth(), lineH - 4);
                g.setColor(theme.lineNumber);
                g.drawText("...", fx + 4, y + 2);
            }
            if (compositionActive != 0 && line == caretPos.line) {
                double cx0 = tl - viewport.xOffset + colToX(text, caretPos.column);
                g.setColor(theme.background);
                g.fillRect(cx0, y, compPx, lineH);
                g.setColor(theme.foreground);
                g.drawText(compositionText, cx0, y + 2);
                g.drawLine(cx0, y + lineH - 3, cx0 + compPx, y + lineH - 3);
            }
            int di = 0;
            while (di < diagnostics.size()) {
                EditorDiagnostic d = diagnostics.get(di);
                if (d.start < lineEnd + 1 && d.end > lineOff) {
                    int a = d.start;
                    if (a < lineOff) {
                        a = lineOff;
                    }
                    int b = d.end;
                    if (b > lineEnd) {
                        b = lineEnd;
                    }
                    if (b <= a) {
                        b = a + 1;
                    }
                    double sx = tl - viewport.xOffset + colToX(text, a - lineOff);
                    double ex = tl - viewport.xOffset + colToX(text, b - lineOff);
                    g.setColor(theme.diagnosticError);
                    double px = sx;
                    int zig = 0;
                    int yy = y + lineH - 3;
                    while (px < ex) {
                        double nx = px + 3.0;
                        if (nx > ex) {
                            nx = ex;
                        }
                        if (zig == 0) {
                            g.drawLine(px, yy + 2, nx, yy);
                        } else {
                            g.drawLine(px, yy, nx, yy + 2);
                        }
                        zig = 1 - zig;
                        px = nx;
                    }
                }
                di = di + 1;
            }
            row = row + 1;
        }
        if (caret.visible != 0 && caret.blinkOn != 0) {
            int cy = rowOf(caretPos.line) * lineH - scrollPx();
            if (cy > 0 - lineH && cy < height) {
                String lineText = document.getLine(caretPos.line);
                double cx = tl - viewport.xOffset + colToX(lineText, caretPos.column);
                if (compositionActive != 0) {
                    cx = cx + compPx;
                }
                g.setColor(theme.caret);
                g.fillRect(cx, cy + 1, 2, lineH - 2);
            }
        }
        g.clearClip();
        /* Gutter */
        if (showLineNumbers != 0 && gw > 0) {
            g.setColor(theme.gutterBackground);
            g.fillRect(0, 0, gw, height);
            row = first;
            while (row <= last) {
                int line = lineOfRow(row);
                int y = (row - first) * lineH - yOff;
                String num = "" + (line + 1);
                if (line == caretPos.line) {
                    g.setColor(theme.gutterCurrent);
                } else {
                    g.setColor(theme.lineNumber);
                }
                double nw = num.length() * charW;
                g.drawText(num, gw - 14 - nw, y + 2);
                if (breakLines.size() > 0 && breakLines.indexOf(line) >= 0) {
                    g.setColor(new Color(224, 62, 62));
                    g.fillEllipse(5, y + (lineH - 11) / 2.0, 11, 11);
                }
                if (bookmarkLines.size() > 0 && bookmarkLines.indexOf(line) >= 0) {
                    g.setColor(new Color(59, 130, 246));
                    g.fillRoundRect(0, y + 2, 4, lineH - 4, 2);
                }
                if (line == execLine) {
                    g.setColor(new Color(37, 99, 235));
                    g.drawText(">", 4, y + 2);
                }
                if (line < lineMarks.size() && lineMarks.get(line) != 0) {
                    int mk = lineMarks.get(line);
                    if (mk == 1) {
                        g.setColor(new Color(46, 160, 67));
                        g.fillRect(gw - 5, y, 3, lineH);
                    } else if (mk == 2) {
                        g.setColor(new Color(59, 130, 246));
                        g.fillRect(gw - 5, y, 3, lineH);
                    } else {
                        g.setColor(new Color(224, 62, 62));
                        g.fillRect(gw - 9, y - 1, 8, 2);
                    }
                }
                if (foldingEnabled != 0 && foldEndAt(line) >= 0) {
                    paintFoldMarker(g, gw - 9, y + lineH / 2, isFoldedAt(line));
                }
                row = row + 1;
            }
            g.setColor(theme.indentGuide);
            g.drawLine(gw - 1, 0, gw - 1, height);
        }
        paintScrollbars(g);
        int imeX = (int) (tl - viewport.xOffset + colToX(document.getLine(caretPos.line), caretPos.column));
        int imeY = rowOf(caretPos.line) * lineH - scrollPx();
        if ((imeX != lastImeX || imeY != lastImeY) && nativeId > 0) {
            lastImeX = imeX;
            lastImeY = imeY;
            UiNative.canvasSetImeRect(nativeId, imeX, imeY, lineH);
        }
        EditorMetrics.layoutsCreated = EditorMetrics.layoutsCreated + EditorMetrics.visibleLinesPainted;
    }

    double foldPlaceholderWidth() {
        return charW * 3 + 8;
    }

    /** Small chevron in the gutter: pointing right when the region is collapsed, down when open. */
    void paintFoldMarker(Graphics g, int cx, int cy, int folded) {
        g.setColor(theme.lineNumber);
        if (folded != 0) {
            g.drawLine(cx - 2, cy - 4, cx + 2, cy);
            g.drawLine(cx + 2, cy, cx - 2, cy + 4);
        } else {
            g.drawLine(cx - 4, cy - 2, cx, cy + 2);
            g.drawLine(cx, cy + 2, cx + 4, cy - 2);
        }
    }

    void paintScrollbars(Graphics g) {
        int total = contentHeight();
        if (total > height) {
            int thumbH = height * height / total;
            if (thumbH < 28) {
                thumbH = 28;
            }
            int mx = maxScrollY();
            int track = height - thumbH;
            int top = 0;
            if (mx > 0) {
                top = scrollPx() * track / mx;
            }
            g.setColor(theme.scrollThumb);
            g.fillRoundRect(width - 9, top + 1, 7, thumbH - 2, 3);
        }
        int mxx = maxScrollX();
        if (mxx > 0) {
            int avail = width - textLeft();
            int content = avail + mxx;
            int thumbW = avail * avail / content;
            if (thumbW < 28) {
                thumbW = 28;
            }
            int track = avail - thumbW;
            int left = textLeft() + viewport.xOffset * track / mxx;
            g.setColor(theme.scrollThumb);
            g.fillRoundRect(left, height - 9, thumbW, 7, 3);
        }
    }

    void disposeEditor() {
        if (blinkTimer != null) {
            blinkTimer.stop();
            blinkTimer.dispose();
            blinkTimer = null;
        }
        dispose();
    }
}

/** Headless/native bridge helpers for tests. */
class EditorTest {
    static void typeChar(CodeEditor editor, int code) {
        UiTest.keyPress(editor, code);
    }

    static void paint(CodeEditor editor) {
        UiTest.paint(editor);
    }
}
