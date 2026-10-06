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

/** Piece table: original buffer + add chunks; no full-document copy on edit. */
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
        int i = 0;
        while (i < length) {
            if (charAt(i) == 10) {
                lineStarts.add(i + 1);
            }
            i = i + 1;
        }
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
        int chunkIndex = addChunks.size();
        addChunks.add(text);
        if (pieces.size() == 0) {
            pieces.add(new Piece(chunkIndex, 0, text.length()));
            length = text.length();
            rebuildLineStarts();
            return;
        }
        int[] idx = new int[1];
        int[] local = new int[1];
        findPiece(offset, idx, local);
        int pi = idx[0];
        int loc = local[0];
        if (pi >= pieces.size()) {
            pieces.add(new Piece(chunkIndex, 0, text.length()));
        } else {
            Piece cur = pieces.get(pi);
            if (loc == 0) {
                pieces.addAt(pi, new Piece(chunkIndex, 0, text.length()));
            } else if (loc == cur.length) {
                pieces.addAt(pi + 1, new Piece(chunkIndex, 0, text.length()));
            } else {
                Piece left = new Piece(cur.chunk, cur.start, loc);
                Piece right = new Piece(cur.chunk, cur.start + loc, cur.length - loc);
                pieces.set(pi, left);
                pieces.addAt(pi + 1, new Piece(chunkIndex, 0, text.length()));
                pieces.addAt(pi + 2, right);
            }
        }
        int oldLen = length;
        length = length + text.length();
        if (offset == oldLen) {
            appendLineStarts(offset, text);
        } else {
            rebuildLineStarts();
        }
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
        length = length - delLen;
        rebuildLineStarts();
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
        int i = 0;
        int hasCr = 0;
        while (i < n) {
            if (text.charAt(i) == 13) {
                hasCr = 1;
                i = n;
            } else {
                i = i + 1;
            }
        }
        if (hasCr == 0) {
            return text;
        }
        String out = "";
        i = 0;
        while (i < n) {
            int c = text.charAt(i);
            if (c == 13) {
                if (i + 1 < n && text.charAt(i + 1) == 10) {
                    i = i + 1;
                }
                out = out + "\n";
            } else {
                out = out + text.substring(i, i + 1);
            }
            i = i + 1;
        }
        return out;
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
        if (c == 95) {
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

    UndoGroup() {
        edits = new ArrayList<TextEdit>();
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

    UndoManager(TextDocument doc) {
        document = doc;
        undoStack = new ArrayList<UndoGroup>();
        redoStack = new ArrayList<UndoGroup>();
        compound = null;
        compounding = 0;
        memoryBytes = 0;
        memoryLimit = 8 * 1024 * 1024;
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
        UndoGroup g = new UndoGroup();
        g.add(edit);
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
        int i = g.edits.size() - 1;
        while (i >= 0) {
            TextEdit e = g.edits.get(i);
            document.replace(e.offset, e.insertedText.length(), e.removedText);
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
            i = i + 1;
        }
        undoStack.add(g);
    }

    int getMemoryBytes() {
        return memoryBytes;
    }
}

class TextSearch {
    static TextRange find(TextDocument doc, String query, int fromOffset, int caseSensitive, int wholeWord) {
        if (query == null || query.length() == 0) {
            return null;
        }
        String text = doc.getText();
        int n = text.length();
        int qn = query.length();
        int i = fromOffset;
        if (i < 0) {
            i = 0;
        }
        while (i + qn <= n) {
            int match = 1;
            int j = 0;
            while (j < qn) {
                int a = text.charAt(i + j);
                int b = query.charAt(j);
                if (caseSensitive == 0) {
                    if (a >= 65 && a <= 90) {
                        a = a + 32;
                    }
                    if (b >= 65 && b <= 90) {
                        b = b + 32;
                    }
                }
                if (a != b) {
                    match = 0;
                    j = qn;
                } else {
                    j = j + 1;
                }
            }
            if (match != 0) {
                if (wholeWord != 0) {
                    int ok = 1;
                    if (i > 0 && TextUtil.isWordChar(text.charAt(i - 1)) != 0) {
                        ok = 0;
                    }
                    if (i + qn < n && TextUtil.isWordChar(text.charAt(i + qn)) != 0) {
                        ok = 0;
                    }
                    if (ok != 0) {
                        return new TextRange(i, i + qn);
                    }
                } else {
                    return new TextRange(i, i + qn);
                }
            }
            i = i + 1;
        }
        return null;
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
            return keyword;
        }
        return foreground;
    }
}

class JNativeSyntaxTokenizer {
    static int linesRetokenized;
    static int tokensGenerated;

    static int isKeyword(String w) {
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
                || w.equals("continue")) {
            return 1;
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
                } else if (c == 34) {
                    j = i + 1;
                    done = 0;
                    while (j < n && done == 0) {
                        if (line.charAt(j) == 34 && line.charAt(j - 1) != 92) {
                            j = j + 1;
                            done = 1;
                        } else {
                            j = j + 1;
                        }
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
                        if ((d >= 48 && d <= 57) || d == 46) {
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

class CodeEditor extends Canvas {
    TextDocument document;
    UndoManager undo;
    Caret caret;
    TextSelection selection;
    EditorViewport viewport;
    EditorTheme theme;
    ArrayList<StyledSpan> spans;
    ArrayList<int> lineStates;
    ArrayList<EditorDiagnostic> diagnostics;
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
    int compositionActive;
    String compositionText;

    CodeEditor() {
        document = new TextDocument("");
        undo = new UndoManager(document);
        caret = new Caret();
        selection = new TextSelection();
        viewport = new EditorViewport();
        theme = EditorTheme.dark();
        spans = new ArrayList<StyledSpan>();
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
        compositionActive = 0;
        compositionText = "";
        setPrefSize(800, 600);
        width = 800;
        height = 600;
        viewport.width = 800;
        viewport.height = 600;
        installHandlers();
        rehighlightAll();
    }

    void installHandlers() {
        CodeEditor self = this;
        onPaint(g -> {
            self.paintEditor(g);
        });
        onMouseDown(event -> {
            self.handleMouseDown(event);
        });
        onMouseDrag(event -> {
            self.handleMouseDrag(event);
        });
        onKeyDown(event -> {
            self.handleKey(event);
        });
        /* Blink timer started lazily via ensureBlinkTimer() to avoid headless hang. */
        blinkTimer = null;
    }

    void ensureBlinkTimer() {
        if (blinkTimer != null) {
            return;
        }
        CodeEditor self = this;
        blinkTimer = new UiTimer(500, () -> {
            self.caret.blinkOn = 1 - self.caret.blinkOn;
            self.repaint();
        });
    }

    void setDocument(TextDocument doc) {
        document = doc;
        undo = new UndoManager(document);
        caret.offset = 0;
        selection.anchor = 0;
        selection.active = 0;
        viewport.firstLine = 0;
        rehighlightAll();
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
        undo = new UndoManager(document);
        rehighlightAll();
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
            rehighlightAll();
        } else {
            showSyntax = 0;
            spans = new ArrayList<StyledSpan>();
        }
        repaint();
    }

    void setTheme(EditorTheme t) {
        theme = t;
        repaint();
    }

    void setDiagnostics(ArrayList<EditorDiagnostic> list) {
        diagnostics = list;
        if (diagnostics == null) {
            diagnostics = new ArrayList<EditorDiagnostic>();
        }
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

    void rehighlightAll() {
        if (showSyntax == 0) {
            spans = new ArrayList<StyledSpan>();
            return;
        }
        spans = JNativeSyntaxTokenizer.highlightDocument(document);
        lineStates = new ArrayList<int>();
        lineStates.add(0);
        int line = 0;
        int state = 0;
        while (line < document.getLineCount()) {
            ArrayList<Token> tokens = new ArrayList<Token>();
            state = JNativeSyntaxTokenizer.tokenizeLine(state, document.getLine(line), tokens, document.lineToOffset(line));
            lineStates.add(state);
            line = line + 1;
        }
    }

    void rehighlightAround(int offset) {
        int line = document.offsetToLine(offset);
        int from = line - 1;
        if (from < 0) {
            from = 0;
        }
        int to = line + 8;
        if (to >= document.getLineCount()) {
            to = document.getLineCount() - 1;
        }
        ArrayList<StyledSpan> neu = JNativeSyntaxTokenizer.highlightLineRange(document, from, to, lineStates);
        ArrayList<StyledSpan> kept = new ArrayList<StyledSpan>();
        int a = document.lineToOffset(from);
        int b = document.getLength();
        if (to + 1 < document.getLineCount()) {
            b = document.lineToOffset(to + 1);
        }
        int i = 0;
        while (i < spans.size()) {
            StyledSpan s = spans.get(i);
            if (s.end <= a || s.start >= b) {
                kept.add(s);
            }
            i = i + 1;
        }
        i = 0;
        while (i < neu.size()) {
            kept.add(neu.get(i));
            i = i + 1;
        }
        spans = kept;
    }

    int gutterWidthFor() {
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
        int w = digits * 10 + 16;
        if (w < 36) {
            w = 36;
        }
        viewport.gutterWidth = w;
        return w;
    }

    int hitTest(int x, int y) {
        int gw = gutterWidthFor();
        int contentX = x - gw + viewport.xOffset;
        if (contentX < 0) {
            contentX = 0;
        }
        int line = viewport.firstLine + y / viewport.lineHeight;
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
        int col = 0;
        int best = 0;
        int n = text.length();
        while (col <= n) {
            String prefix = text.substring(0, col);
            Size sz = measureLineWidth(prefix);
            if (sz.width <= contentX) {
                best = col;
            } else {
                return document.positionToOffset(line, best);
            }
            col = col + 1;
        }
        return document.positionToOffset(line, best);
    }

    Size measureLineWidth(String s) {
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        g.setFont(font);
        Size sz = g.measureText(s, font);
        g.end();
        tmp.dispose();
        return sz;
    }

    void ensureCaretVisible() {
        TextPosition p = document.offsetToPosition(caret.offset);
        int vis = viewport.visibleLineCount();
        if (p.line < viewport.firstLine) {
            viewport.firstLine = p.line;
        }
        if (p.line >= viewport.firstLine + vis - 1) {
            viewport.firstLine = p.line - vis + 2;
            if (viewport.firstLine < 0) {
                viewport.firstLine = 0;
            }
        }
    }

    void goTo(int line, int column) {
        int off = document.positionToOffset(line, column);
        caret.offset = off;
        selection.anchor = off;
        selection.active = off;
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
        fireSelection();
        repaint();
    }

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
        rehighlightAround(offset);
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

    void moveCaret(int newOffset, int extend) {
        if (newOffset < 0) {
            newOffset = 0;
        }
        if (newOffset > document.getLength()) {
            newOffset = document.getLength();
        }
        caret.offset = newOffset;
        if (extend != 0) {
            selection.active = newOffset;
        } else {
            selection.anchor = newOffset;
            selection.active = newOffset;
        }
        TextPosition p = document.offsetToPosition(caret.offset);
        caret.preferredColumn = p.column;
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

    void executeCommand(int cmd) {
        TextRange r = selection.toRange();
        TextPosition p = document.offsetToPosition(caret.offset);
        String line = "";
        String indent = "";
        int i = 0;
        int cont = 0;
        int c = 0;
        int lineNo = 0;
        if (cmd == EditorCommand.COPY) {
            Clipboard.setText(document.substring(r.start, r.end));
        } else if (cmd == EditorCommand.CUT) {
            if (readOnly == 0) {
                Clipboard.setText(document.substring(r.start, r.end));
                replaceSelection("");
            }
        } else if (cmd == EditorCommand.PASTE) {
            if (readOnly == 0) {
                insertText(TextUtil.normalizeNewlines(Clipboard.getText()));
            }
        } else if (cmd == EditorCommand.UNDO) {
            if (readOnly == 0) {
                undo.undo();
                rehighlightAll();
                repaint();
            }
        } else if (cmd == EditorCommand.REDO) {
            if (readOnly == 0) {
                undo.redo();
                rehighlightAll();
                repaint();
            }
        } else if (cmd == EditorCommand.SELECT_ALL) {
            selection.anchor = 0;
            selection.active = document.getLength();
            caret.offset = selection.active;
            fireSelection();
            repaint();
        } else if (cmd == EditorCommand.MOVE_LEFT) {
            moveCaret(caret.offset - 1, shiftSelecting);
        } else if (cmd == EditorCommand.MOVE_RIGHT) {
            moveCaret(caret.offset + 1, shiftSelecting);
        } else if (cmd == EditorCommand.MOVE_UP) {
            lineNo = p.line - 1;
            if (lineNo < 0) {
                lineNo = 0;
            }
            moveCaret(document.positionToOffset(lineNo, caret.preferredColumn), shiftSelecting);
        } else if (cmd == EditorCommand.MOVE_DOWN) {
            lineNo = p.line + 1;
            if (lineNo >= document.getLineCount()) {
                lineNo = document.getLineCount() - 1;
            }
            moveCaret(document.positionToOffset(lineNo, caret.preferredColumn), shiftSelecting);
        } else if (cmd == EditorCommand.DELETE_BACKWARD) {
            deleteSelectionOr(-1);
        } else if (cmd == EditorCommand.DELETE_FORWARD) {
            deleteSelectionOr(1);
        } else if (cmd == EditorCommand.NEWLINE) {
            if (readOnly == 0) {
                line = document.getLine(p.line);
                indent = "";
                i = 0;
                cont = 1;
                while (i < line.length() && cont != 0) {
                    c = line.charAt(i);
                    if (c == 32 || c == 9) {
                        indent = indent + TextUtil.fromCode(c);
                        i = i + 1;
                    } else {
                        cont = 0;
                    }
                }
                insertText("\n" + indent);
            }
        } else if (cmd == EditorCommand.HOME) {
            moveCaret(document.lineToOffset(p.line), shiftSelecting);
        } else if (cmd == EditorCommand.END) {
            line = document.getLine(p.line);
            moveCaret(document.lineToOffset(p.line) + line.length(), shiftSelecting);
        }
    }

    void handleMouseDown(MouseEvent event) {
        int off = hitTest(event.getX(), event.getY());
        if (event.clickCount >= 2) {
            int a = wordBoundary(off, -1);
            int b = wordBoundary(off, 1);
            selection.anchor = a;
            selection.active = b;
            caret.offset = b;
        } else {
            moveCaret(off, 0);
        }
        caret.blinkOn = 1;
        ensureBlinkTimer();
        fireSelection();
        repaint();
    }

    void handleMouseDrag(MouseEvent event) {
        int off = hitTest(event.getX(), event.getY());
        moveCaret(off, 1);
    }

    void handleKey(KeyEvent event) {
        int code = event.keyCode;
        shiftSelecting = event.shiftDown;
        int ctrl = event.ctrlDown;
        if (event.metaDown != 0) {
            ctrl = 1;
        }
        if (ctrl != 0) {
            if (code == 67 || code == 99) {
                executeCommand(EditorCommand.COPY);
                return;
            }
            if (code == 88 || code == 120) {
                executeCommand(EditorCommand.CUT);
                return;
            }
            if (code == 86 || code == 118) {
                executeCommand(EditorCommand.PASTE);
                return;
            }
            if (code == 90 || code == 122) {
                executeCommand(EditorCommand.UNDO);
                return;
            }
            if (code == 89 || code == 121) {
                executeCommand(EditorCommand.REDO);
                return;
            }
            if (code == 65 || code == 97) {
                executeCommand(EditorCommand.SELECT_ALL);
                return;
            }
            if (code == 37) {
                moveCaret(wordBoundary(caret.offset, -1), shiftSelecting);
                return;
            }
            if (code == 39) {
                moveCaret(wordBoundary(caret.offset, 1), shiftSelecting);
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
        } else if (code == 9) {
            if (insertSpaces != 0) {
                String sp = "";
                int i = 0;
                while (i < tabSize) {
                    sp = sp + " ";
                    i = i + 1;
                }
                insertText(sp);
            } else {
                insertText("\t");
            }
        } else if (code >= 32 && code < 127) {
            insertText(TextUtil.fromCode(code));
        }
        caret.blinkOn = 1;
    }

    void scrollLines(int delta) {
        viewport.firstLine = viewport.firstLine + delta;
        if (viewport.firstLine < 0) {
            viewport.firstLine = 0;
        }
        int max = document.getLineCount() - 1;
        if (max < 0) {
            max = 0;
        }
        if (viewport.firstLine > max) {
            viewport.firstLine = max;
        }
        repaint();
    }

    void findNext(String query) {
        findQuery = query;
        TextRange r = TextSearch.find(document, query, caret.offset + 1, 1, 0);
        if (r == null) {
            r = TextSearch.find(document, query, 0, 1, 0);
        }
        if (r != null) {
            reveal(r);
        }
    }

    void paintEditor(Graphics g) {
        viewport.width = width;
        viewport.height = height;
        int gw = gutterWidthFor();
        g.setColor(theme.background);
        g.fillRect(0, 0, width, height);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        viewport.lineHeight = fm.getLineHeight();
        if (viewport.lineHeight < 14) {
            viewport.lineHeight = 18;
        }
        int vis = viewport.visibleLineCount();
        int line = viewport.firstLine;
        int last = line + vis;
        if (last > document.getLineCount()) {
            last = document.getLineCount();
        }
        TextPosition caretPos = document.offsetToPosition(caret.offset);
        EditorMetrics.visibleLinesPainted = last - line;
        int y = 0;
        String text = "";
        int lineOff = 0;
        int sel0 = 0;
        int sel1 = 0;
        int lineEnd = 0;
        int a = 0;
        int b = 0;
        Size sa = null;
        Size sb = null;
        int di = 0;
        EditorDiagnostic d = null;
        String lineText = "";
        Size prefix = null;
        double cx = 0.0;
        double cy = 0.0;
        while (line < last) {
            y = (line - viewport.firstLine) * viewport.lineHeight;
            if (line == caretPos.line) {
                g.setColor(theme.currentLine);
                g.fillRect(0, y, width, viewport.lineHeight);
            }
            if (showLineNumbers != 0) {
                g.setColor(theme.lineNumber);
                g.drawText("" + (line + 1), 4, y);
            }
            g.setColor(theme.background);
            text = document.getLine(line);
            lineOff = document.lineToOffset(line);
            sel0 = selection.start();
            sel1 = selection.end();
            lineEnd = lineOff + text.length();
            if (sel0 < lineEnd && sel1 > lineOff) {
                a = sel0;
                if (a < lineOff) {
                    a = lineOff;
                }
                b = sel1;
                if (b > lineEnd) {
                    b = lineEnd;
                }
                sa = g.measureText(text.substring(0, a - lineOff), font);
                sb = g.measureText(text.substring(0, b - lineOff), font);
                g.setColor(theme.selection);
                g.fillRect(gw + sa.width - viewport.xOffset, y, sb.width - sa.width, viewport.lineHeight);
            }
            paintStyledLine(g, text, lineOff, gw, y);
            di = 0;
            while (di < diagnostics.size()) {
                d = diagnostics.get(di);
                if (d.start < lineEnd && d.end > lineOff) {
                    a = d.start;
                    if (a < lineOff) {
                        a = lineOff;
                    }
                    b = d.end;
                    if (b > lineEnd) {
                        b = lineEnd;
                    }
                    sa = g.measureText(text.substring(0, a - lineOff), font);
                    sb = g.measureText(text.substring(0, b - lineOff), font);
                    g.setColor(theme.diagnosticError);
                    g.drawLine(gw + sa.width, y + viewport.lineHeight - 2, gw + sb.width, y + viewport.lineHeight - 2);
                }
                di = di + 1;
            }
            line = line + 1;
        }
        if (showLineNumbers != 0 && gw > 0) {
            g.setColor(new Color(60, 60, 60));
            g.drawLine(gw - 2, 0, gw - 2, height);
        }
        if (caret.visible != 0 && caret.blinkOn != 0) {
            if (caretPos.line >= viewport.firstLine && caretPos.line < last) {
                lineText = document.getLine(caretPos.line);
                prefix = g.measureText(lineText.substring(0, caretPos.column), font);
                cx = gw + prefix.width - viewport.xOffset;
                cy = (caretPos.line - viewport.firstLine) * viewport.lineHeight;
                g.setColor(theme.caret);
                g.fillRect(cx, cy, 2, viewport.lineHeight);
            }
        }
        EditorMetrics.layoutsCreated = EditorMetrics.layoutsCreated + EditorMetrics.visibleLinesPainted;
    }

    void paintStyledLine(Graphics g, String text, int lineOff, int gw, int y) {
        if (text.length() == 0) {
            return;
        }
        int i = 0;
        int n = text.length();
        int abs = 0;
        int ty = 0;
        int j = 0;
        int k = 0;
        int run = 0;
        int abs2 = 0;
        int ty2 = 0;
        StyledSpan s = null;
        String part = "";
        Size before = null;
        while (i < n) {
            abs = lineOff + i;
            ty = TokenType.NORMAL;
            j = 0;
            while (j < spans.size()) {
                s = spans.get(j);
                if (abs >= s.start && abs < s.end) {
                    ty = s.tokenType;
                    j = spans.size();
                } else {
                    j = j + 1;
                }
            }
            k = i + 1;
            run = 1;
            while (k < n && run != 0) {
                abs2 = lineOff + k;
                ty2 = TokenType.NORMAL;
                j = 0;
                while (j < spans.size()) {
                    s = spans.get(j);
                    if (abs2 >= s.start && abs2 < s.end) {
                        ty2 = s.tokenType;
                        j = spans.size();
                    } else {
                        j = j + 1;
                    }
                }
                if (ty2 != ty) {
                    run = 0;
                } else {
                    k = k + 1;
                }
            }
            part = text.substring(i, k);
            before = g.measureText(text.substring(0, i), font);
            g.setColor(theme.colorForToken(ty));
            g.drawText(part, gw + before.width - viewport.xOffset, y);
            i = k;
        }
    }

    /** IME composition foundation (commit path). */
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
