import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;

/**
 * Inline code completion: a list that floats under the caret while the editor keeps keyboard focus. It opens
 * automatically after "." or after the first identifier character (and on demand with Ctrl+Space / ⌘K), narrows as
 * you type, and Up / Down / Enter / Tab / Esc drive it from the keyboard.
 *
 * @author 张晓达
 */
public class IdeSuggest extends Canvas {
    IdeApp app;
    CodeEditor editor;
    ArrayList<IdeItem> all = new ArrayList<IdeItem>();
    ArrayList<IdeItem> shown = new ArrayList<IdeItem>();
    int sel = 0;
    int top = 0;
    int hover = -1;
    int rowH = 24;
    int maxRows = 9;
    int startOffset = 0;
    boolean isOpen = false;
    String prefix = "";

    IdeSuggest(IdeApp a) {
        app = a;
        setHiDpi(true);
        onPaint(g -> paintList(g));
        onMouseDown(e -> clickRow(e));
        onMouseMove(e -> moveRow(e));
        onMouseWheel(e -> wheelRow(e));
        IdeApp.vis(this, false);
    }

    static boolean identChar(int c) {
        return (c >= 97 && c <= 122) || (c >= 65 && c <= 90) || (c >= 48 && c <= 57) || c == 95 || c == 36;
    }

    // ---------------------------------------------------------------- opening / filtering

    void open(CodeEditor ed, ArrayList<IdeItem> items, String pre) {
        editor = ed;
        all = items;
        prefix = pre;
        startOffset = ed.caret.offset - pre.length();
        sel = 0;
        top = 0;
        refilter();
        if (shown.size() == 0) {
            hide();
            return;
        }
        isOpen = true;
        app.host.remove(this);
        app.host.add(this);
        place();
        IdeApp.vis(this, true);
        repaint();
    }

    void hide() {
        if (!isOpen && visible == 0) {
            return;
        }
        isOpen = false;
        IdeApp.vis(this, false);
    }

    void refilter() {
        shown = new ArrayList<IdeItem>();
        String q = prefix.toLowerCase();
        int i = 0;
        while (i < all.size() && shown.size() < 300) {
            IdeItem it = all.get(i);
            if (q.length() == 0 || it.key.toLowerCase().startsWith(q) || it.text.toLowerCase().startsWith(q)) {
                shown.add(it);
            }
            i = i + 1;
        }
        if (q.length() > 0 && shown.size() < 300) {
            i = 0;
            while (i < all.size() && shown.size() < 300) {
                IdeItem it = all.get(i);
                if (!(it.key.toLowerCase().startsWith(q) || it.text.toLowerCase().startsWith(q))
                        && (it.text.toLowerCase().indexOf(q) >= 0)) {
                    shown.add(it);
                }
                i = i + 1;
            }
        }
        if (sel >= shown.size()) {
            sel = shown.size() - 1;
        }
        if (sel < 0) {
            sel = 0;
        }
        top = 0;
        revealSel();
    }

    /** Positions the list under the word start (or above the caret line when there is no room). */
    void place() {
        int rows = Math.min(maxRows, shown.size());
        int h = rows * rowH + 8;
        int w = 440;
        int ex = editor.offsetPixelX(startOffset);
        int ey = editor.caretPixelY();
        int lh = editor.lineHeightPx();
        int x = ex;
        int hostW = app.host.width;
        int hostH = app.host.height;
        if (x + w > hostW - 12) {
            x = hostW - w - 12;
        }
        if (x < 4) {
            x = 4;
            w = Math.min(w, hostW - 8);
        }
        int y = ey + lh + 2 + app.findBarHeight();
        if (y + h > hostH - 4) {
            y = ey + app.findBarHeight() - h - 2;
            if (y < 0) {
                y = 0;
            }
        }
        setBounds(x, y, w, h);
    }

    void revealSel() {
        if (sel < top) {
            top = sel;
        } else if (sel >= top + maxRows) {
            top = sel - maxRows + 1;
        }
        if (top < 0) {
            top = 0;
        }
    }

    // ---------------------------------------------------------------- editor hooks

    /** Called for every key the editor receives; returns true (and consumes the event) when the list used it. */
    void keyPre(KeyEvent e) {
        int code = e.keyCode;
        if (!isOpen) {
            if (code == 32 && e.ctrlDown != 0) {
                e.consume();
                app.completeInline(true);
            }
            return;
        }
        if (code == 32 && e.ctrlDown != 0) {
            e.consume();
            return;
        }
        if (e.metaDown != 0 || e.ctrlDown != 0) {
            hide();
            return;
        }
        if (code == KeyCode.DOWN && e.altDown == 0) {
            e.consume();
            move(1);
        } else if (code == KeyCode.UP && e.altDown == 0) {
            e.consume();
            move(-1);
        } else if (code == KeyCode.PAGE_DOWN) {
            e.consume();
            move(maxRows);
        } else if (code == KeyCode.PAGE_UP) {
            e.consume();
            move(-maxRows);
        } else if (code == KeyCode.ENTER || (code == KeyCode.TAB && e.shiftDown == 0)) {
            e.consume();
            accept(sel);
        } else if (code == KeyCode.ESCAPE) {
            e.consume();
            hide();
        } else if (code == KeyCode.LEFT || code == KeyCode.RIGHT || code == KeyCode.UP || code == KeyCode.DOWN
                || code == KeyCode.HOME || code == KeyCode.END || code == KeyCode.TAB) {
            hide();
        }
    }

    /** Called after the editor inserted / deleted a character. */
    void typed(int code) {
        IdeDoc cur = app.activeDoc();
        if (cur == null || cur.kind != 0) {
            return;
        }
        if (editor != cur.editor) {
            hide();
            editor = cur.editor;
        }
        if (isOpen) {
            if (code == 8 || code == 127 || identChar(code)) {
                int caret = editor.caret.offset;
                if (caret < startOffset) {
                    hide();
                    return;
                }
                String w = editor.document.substring(startOffset, caret);
                int k = 0;
                while (k < w.length()) {
                    if (!identChar(w.charAt(k))) {
                        hide();
                        return;
                    }
                    k = k + 1;
                }
                prefix = w;
                refilter();
                if (shown.size() == 0) {
                    hide();
                    return;
                }
                place();
                repaint();
                return;
            }
            hide();
        }
        if (code == 46) {
            app.completeInline(false);
        } else if (identChar(code) && !(code >= 48 && code <= 57)) {
            String w = editor.wordBeforeCaret();
            if (w.length() >= 1 && editor.inStringOrComment(editor.caret.offset) == 0) {
                int c0 = w.charAt(0);
                if (!(c0 >= 48 && c0 <= 57)) {
                    app.completeInline(false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- selection / accept

    void move(int d) {
        sel = sel + d;
        if (sel < 0) {
            sel = 0;
        }
        if (sel >= shown.size()) {
            sel = shown.size() - 1;
        }
        revealSel();
        repaint();
    }

    void accept(int idx) {
        if (idx < 0 || idx >= shown.size()) {
            hide();
            return;
        }
        IdeItem it = shown.get(idx);
        CodeEditor ed = editor;
        hide();
        int caret = ed.caret.offset;
        int start = startOffset;
        if (start > caret || start < 0) {
            start = caret;
        }
        String ins = it.key;
        ed.undo.closeGroup();
        int back = 0;
        if (ins.endsWith("(")) {
            ins = ins + ")";
            back = 1;
        }
        ed.applyEdit(start, caret - start, ins);
        ed.undo.closeGroup();
        if (back > 0) {
            ed.moveCaret(ed.caret.offset - back, 0);
        }
        ed.requestFocus();
    }

    // ---------------------------------------------------------------- mouse / paint

    int rowAt(int y) {
        int r = (y - 4) / rowH;
        int idx = top + r;
        if (y < 4 || idx >= shown.size() || r >= maxRows) {
            return -1;
        }
        return idx;
    }

    void clickRow(MouseEvent e) {
        int idx = rowAt(e.y);
        if (idx >= 0) {
            accept(idx);
        }
    }

    void moveRow(MouseEvent e) {
        int idx = rowAt(e.y);
        if (idx != hover) {
            hover = idx;
            repaint();
        }
    }

    void wheelRow(MouseEvent e) {
        int max = Math.max(0, shown.size() - maxRows);
        top = Math.max(0, Math.min(max, top - e.wheelDelta));
        repaint();
    }

    void paintList(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        Ide.rstroke(g, Ide.BORDER, 0.5, 0.5, width - 1, height - 1, 6);
        Font f = Ide.mono();
        int rows = Math.min(maxRows, shown.size() - top);
        int i = 0;
        g.setClip(0, 0, width, height);
        while (i < rows) {
            IdeItem it = shown.get(top + i);
            int y = 4 + i * rowH;
            if (top + i == sel) {
                Ide.rfill(g, Ide.SEL, 4, y, width - 8, rowH, 4);
            } else if (top + i == hover) {
                Ide.rfill(g, Ide.HOVER, 4, y, width - 8, rowH, 4);
            }
            int x = 10;
            if (it.icon.length() > 0) {
                Ide.icon(g, it.icon, x, y + (rowH - 15) / 2.0, 15);
                x = x + 21;
            }
            String label = it.text;
            int hw = 0;
            if (it.hint.length() > 0) {
                hw = Math.min(170, Ide.textWidth(g, it.hint, Ide.small()));
            }
            String lt = Ide.ellipsize(g, label, f, width - x - hw - 24);
            Ide.textRow(g, lt, x, y, rowH, f, Ide.TEXT);
            if (it.hint.length() > 0) {
                String ht = Ide.ellipsize(g, it.hint, Ide.small(), 170);
                int w2 = Ide.textWidth(g, ht, Ide.small());
                Ide.textRow(g, ht, width - w2 - 12, y, rowH, Ide.small(), Ide.FAINT);
            }
            i = i + 1;
        }
        g.clearClip();
        if (shown.size() > maxRows) {
            int total = shown.size();
            double th = Math.max(20.0, height * (double) maxRows / total);
            double ty = (height - th) * top / (total - maxRows);
            Ide.rfill(g, 0xC4C8D0, width - 6, ty + 2, 3, th - 4, 1.5);
        }
    }
}
