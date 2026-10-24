import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;

/**
 * Quick-pick palette (⌘P files, ⇧⌘O symbols): a one-line query and a filtered list drawn on a single canvas, so the
 * keyboard (Up / Down / Enter / Esc) and input methods work without native text fields.
 *
 * @author 张晓达
 */
public class IdeQuick extends Canvas {
    IdeApp app;
    String title = "";
    String query = "";
    String composing = "";
    ArrayList<IdeItem> all = new ArrayList<IdeItem>();
    ArrayList<IdeItem> shown = new ArrayList<IdeItem>();
    IdeItemHandler handler;
    int sel = 0;
    int top = 0;
    int hover = -1;
    int rowH = 26;
    int maxRows = 10;
    boolean isOpen = false;

    IdeQuick(IdeApp a) {
        app = a;
        setHiDpi(true);
        onPaint(g -> paintQuick(g));
        onMouseDown(e -> clickRow(e));
        onMouseMove(e -> moveRow(e));
        onMouseWheel(e -> wheelRow(e));
        onKeyDown(e -> keyQuick(e));
        onTextInput(e -> textQuick(e));
        onFocus(e -> focusQuick(e));
        UiNative.canvasSetTextInput(nativeId, 1);
        IdeApp.vis(this, false);
    }

    void open(String t, ArrayList<IdeItem> items, IdeItemHandler h) {
        title = t;
        all = items;
        handler = h;
        query = "";
        composing = "";
        sel = 0;
        top = 0;
        refilter();
        isOpen = true;
        app.remove(this);
        app.add(this);
        int w = Math.min(600, app.width - 80);
        int rows = Math.min(maxRows, Math.max(1, shown.size()));
        setBounds((app.width - w) / 2, IdeApp.TB_H + 8, w, 46 + rows * rowH + 8);
        IdeApp.vis(this, true);
        requestFocus();
        repaint();
    }

    void close() {
        if (!isOpen) {
            return;
        }
        isOpen = false;
        IdeApp.vis(this, false);
    }

    void refilter() {
        shown = new ArrayList<IdeItem>();
        String q = query.toLowerCase();
        ArrayList<String> words = new ArrayList<String>();
        int i = 0;
        String cur = "";
        while (i <= q.length()) {
            if (i == q.length() || q.charAt(i) == ' ') {
                if (cur.length() > 0) {
                    words.add(cur);
                }
                cur = "";
            } else {
                cur = cur + q.substring(i, i + 1);
            }
            i = i + 1;
        }
        i = 0;
        while (i < all.size() && shown.size() < 400) {
            IdeItem it = all.get(i);
            String hay = (it.text + " " + it.hint).toLowerCase();
            boolean ok = true;
            int w = 0;
            while (w < words.size()) {
                if (hay.indexOf(words.get(w)) < 0) {
                    ok = false;
                }
                w = w + 1;
            }
            if (ok) {
                shown.add(it);
            }
            i = i + 1;
        }
        if (words.size() > 0) {
            /* Names that start with the query first. */
            ArrayList<IdeItem> front = new ArrayList<IdeItem>();
            ArrayList<IdeItem> back = new ArrayList<IdeItem>();
            i = 0;
            while (i < shown.size()) {
                IdeItem it = shown.get(i);
                if (it.text.toLowerCase().startsWith(words.get(0))) {
                    front.add(it);
                } else {
                    back.add(it);
                }
                i = i + 1;
            }
            shown = front;
            i = 0;
            while (i < back.size()) {
                shown.add(back.get(i));
                i = i + 1;
            }
        }
        sel = 0;
        top = 0;
        int rows = Math.min(maxRows, Math.max(1, shown.size()));
        if (isOpen) {
            setBounds(x, y, width, 46 + rows * rowH + 8);
        }
    }

    void accept(int idx) {
        if (idx < 0 || idx >= shown.size()) {
            return;
        }
        IdeItem it = shown.get(idx);
        IdeItemHandler h = handler;
        close();
        if (h != null) {
            h.handle(it);
        }
    }

    void move(int d) {
        sel = sel + d;
        if (sel < 0) {
            sel = 0;
        }
        if (sel >= shown.size()) {
            sel = shown.size() - 1;
        }
        if (sel < 0) {
            sel = 0;
        }
        if (sel < top) {
            top = sel;
        } else if (sel >= top + maxRows) {
            top = sel - maxRows + 1;
        }
        repaint();
    }

    void keyQuick(KeyEvent e) {
        int code = e.keyCode;
        if (code == KeyCode.ESCAPE) {
            close();
            app.focusEditor();
        } else if (code == KeyCode.ENTER) {
            accept(sel);
        } else if (code == KeyCode.DOWN) {
            move(1);
        } else if (code == KeyCode.UP) {
            move(-1);
        } else if (code == KeyCode.PAGE_DOWN) {
            move(maxRows);
        } else if (code == KeyCode.PAGE_UP) {
            move(-maxRows);
        } else if (code == KeyCode.BACKSPACE) {
            if (query.length() > 0) {
                if (e.metaDown != 0) {
                    query = "";
                } else {
                    query = query.substring(0, query.length() - 1);
                }
                refilter();
                repaint();
            }
        } else if (e.metaDown == 0 && e.ctrlDown == 0) {
            int ch = e.typedChar;
            if (ch >= 32 && ch != 127) {
                query = query + TextUtil.charString(ch);
                refilter();
                repaint();
            }
        }
    }

    void textQuick(TextInputEvent e) {
        if (e.marked != 0) {
            composing = e.text;
            repaint();
            return;
        }
        composing = "";
        if (e.text.length() > 0) {
            query = query + e.text;
            refilter();
        }
        repaint();
    }

    void focusQuick(FocusEvent e) {
        if (e.gained == 0 && isOpen) {
            close();
        }
    }

    int rowAt(int y) {
        int r = (y - 46) / rowH;
        int idx = top + r;
        if (y < 46 || idx >= shown.size() || r >= maxRows) {
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

    void paintQuick(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        Ide.rstroke(g, Ide.BORDER, 0.5, 0.5, width - 1, height - 1, 8);
        Ide.fill(g, Ide.PANEL_ALT, 1, 1, width - 2, 44);
        Ide.line(g, Ide.BORDER, 0, 45.5, width, 45.5);
        String shownQuery = query + composing;
        if (shownQuery.length() == 0) {
            Ide.textRow(g, title, 16, 0, 46, Font.system(14), Ide.FAINT);
        } else {
            Ide.textRow(g, shownQuery, 16, 0, 46, Font.system(14), Ide.TEXT);
            int tw = Ide.textWidth(g, shownQuery, Font.system(14));
            Ide.fill(g, Ide.ACCENT, 16 + tw + 1, 13, 1.5, 20);
        }
        if (shown.size() == 0) {
            Ide.textRow(g, "没有匹配项", 16, 46, rowH, Ide.ui(), Ide.FAINT);
            return;
        }
        Font f = Ide.ui();
        int rows = Math.min(maxRows, shown.size() - top);
        int i = 0;
        g.setClip(0, 46, width, height - 46);
        while (i < rows) {
            IdeItem it = shown.get(top + i);
            int y = 50 + i * rowH;
            if (top + i == sel) {
                Ide.rfill(g, Ide.SEL, 6, y, width - 12, rowH, 5);
            } else if (top + i == hover) {
                Ide.rfill(g, Ide.HOVER, 6, y, width - 12, rowH, 5);
            }
            int x = 14;
            if (it.icon.length() > 0) {
                Ide.icon(g, it.icon, x, y + (rowH - 15) / 2.0, 15);
                x = x + 22;
            }
            int hw = 0;
            String ht = "";
            if (it.hint.length() > 0) {
                ht = Ide.ellipsize(g, it.hint, Ide.small(), width / 2 - 20);
                hw = Ide.textWidth(g, ht, Ide.small());
            }
            String lt = Ide.ellipsize(g, it.text, f, width - x - hw - 30);
            Ide.textRow(g, lt, x, y, rowH, f, Ide.TEXT);
            if (hw > 0) {
                Ide.textRow(g, ht, width - hw - 16, y, rowH, Ide.small(), Ide.FAINT);
            }
            i = i + 1;
        }
        g.clearClip();
    }
}
