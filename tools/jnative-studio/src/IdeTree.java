import jnative.ui.*;
import jnative.graphics.*;

/**
 * Tree / list node used by IdeTree.
 *
 * @author 张晓达
 */
class IdeItem {
    String text;
    String icon;
    String key;
    String hint = "";
    DsNode node;
    boolean expanded = false;
    int color = -1;
    ArrayList<IdeItem> kids = new ArrayList<IdeItem>();
    IdeItem parent;
    int tag = 0;

    IdeItem(String t, String ic, String k) {
        text = t;
        icon = ic;
        key = k;
    }

    IdeItem add(IdeItem c) {
        c.parent = this;
        kids.add(c);
        return c;
    }

    int depth() {
        int d = 0;
        IdeItem p = parent;
        while (p != null && p.parent != null) {
            d = d + 1;
            p = p.parent;
        }
        return d;
    }
}

interface IdeItemHandler {
    void handle(IdeItem it);
}

/**
 * Custom-drawn tree view (explorer, outline, problems list): crisp native text, icons, keyboard
 * navigation, wheel scrolling and context-menu hooks.
 *
 * @author 张晓达
 */
public class IdeTree extends Canvas {
    IdeItem root = new IdeItem("", "", "");
    IdeItem selected;
    IdeItem hover;
    ArrayList<IdeItem> rows = new ArrayList<IdeItem>();
    int scroll = 0;
    int rowH = 24;
    int indent = 14;
    int hasFocus = 0;
    String emptyText = "";
    boolean flat = false;
    Font customFont;
    IdeItemHandler onSelect;
    IdeItemHandler onActivate;
    IdeItemHandler onContext;

    IdeTree() {
        setHiDpi(true);
        onPaint(g -> paintTree(g));
        onMouseDown(e -> mouseDownTree(e));
        onMouseMove(e -> mouseMoveTree(e));
        onMouseExit(e -> mouseExitTree());
        onMouseWheel(e -> wheelTree(e));
        onKeyDown(e -> keyTree(e));
        onFocus(e -> focusTree(e));
    }

    void setRoot(IdeItem r) {
        root = r;
        rebuild();
    }

    void rebuild() {
        rows.clear();
        flatten(root);
        int max = maxScroll();
        if (scroll > max) {
            scroll = max;
        }
        if (selected != null && rows.indexOf(selected) < 0) {
            selected = null;
        }
        repaint();
    }

    void flatten(IdeItem n) {
        int i = 0;
        while (i < n.kids.size()) {
            IdeItem c = n.kids.get(i);
            rows.add(c);
            if (c.expanded && c.kids.size() > 0) {
                flatten(c);
            }
            i = i + 1;
        }
    }

    int maxScroll() {
        int total = rows.size() * rowH;
        int m = total - height + 4;
        if (m < 0) {
            return 0;
        }
        return m;
    }

    void scrollToEnd() {
        scroll = maxScroll();
        repaint();
    }

    boolean atEnd() {
        return scroll >= maxScroll() - rowH;
    }

    IdeItem findByKey(String key) {
        return findIn(root, key);
    }

    static IdeItem findIn(IdeItem n, String key) {
        if (n.key.equals(key)) {
            return n;
        }
        int i = 0;
        while (i < n.kids.size()) {
            IdeItem r = findIn(n.kids.get(i), key);
            if (r != null) {
                return r;
            }
            i = i + 1;
        }
        return null;
    }

    void expandTo(IdeItem it) {
        IdeItem p = it.parent;
        while (p != null) {
            p.expanded = true;
            p = p.parent;
        }
        rebuild();
    }

    void select(IdeItem it, boolean fire) {
        if (it != null) {
            expandTo(it);
        }
        selected = it;
        reveal(it);
        repaint();
        if (fire && it != null && onSelect != null) {
            onSelect.handle(it);
        }
    }

    void reveal(IdeItem it) {
        if (it == null) {
            return;
        }
        int idx = rows.indexOf(it);
        if (idx < 0) {
            return;
        }
        int top = idx * rowH;
        if (top < scroll) {
            scroll = top;
        } else if (top + rowH > scroll + height - 2) {
            scroll = top + rowH - height + 2;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    int rowAt(int y) {
        int idx = (y + scroll) / rowH;
        if (y < 0 || idx < 0 || idx >= rows.size()) {
            return -1;
        }
        return idx;
    }

    void paintTree(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        Font f = customFont != null ? customFont : Ide.ui();
        int first = scroll / rowH;
        int y0 = first * rowH - scroll;
        int i = first;
        g.setClip(0, 0, width, height);
        while (i < rows.size() && y0 < height) {
            IdeItem it = rows.get(i);
            int d = flat ? 0 : it.depth();
            if (it == selected) {
                int c = hasFocus != 0 ? Ide.SEL : Ide.SEL_IDLE;
                Ide.rfill(g, c, 3, y0 + 1, width - 6, rowH - 2, 4);
            } else if (it == hover) {
                Ide.rfill(g, Ide.HOVER, 3, y0 + 1, width - 6, rowH - 2, 4);
            }
            int x = 8 + d * indent;
            if (!flat) {
                if (it.kids.size() > 0) {
                    Ide.arrow(g, x + 5, y0 + rowH / 2.0, it.expanded, Ide.SUBTLE);
                }
                x = x + 14;
            }
            if (it.icon.length() > 0) {
                String ic = it.icon;
                if (ic.equals("folder") && it.expanded) {
                    ic = "folderOpen";
                }
                Ide.icon(g, ic, x, y0 + (rowH - 15) / 2.0, 15);
                x = x + 21;
            }
            int tc = it.color >= 0 ? it.color : Ide.TEXT;
            String label = it.text;
            int avail = width - x - 8;
            if (it.hint.length() > 0) {
                int hw = Ide.textWidth(g, it.hint, Ide.small());
                String lt = Ide.ellipsize(g, label, f, avail - hw - 10);
                Ide.textRow(g, lt, x, y0, rowH, f, tc);
                int lw = Ide.textWidth(g, lt, f);
                Ide.textRow(g, it.hint, x + lw + 8, y0, rowH, Ide.small(), Ide.FAINT);
            } else {
                Ide.textRow(g, Ide.ellipsize(g, label, f, avail), x, y0, rowH, f, tc);
            }
            y0 = y0 + rowH;
            i = i + 1;
        }
        g.clearClip();
        if (rows.size() == 0 && emptyText.length() > 0) {
            Ide.textRow(g, emptyText, 14, 8, rowH, f, Ide.FAINT);
        }
        int max = maxScroll();
        if (max > 0) {
            double th = Math.max(24.0, height * (double) height / (rows.size() * rowH));
            double ty = (height - th) * scroll / max;
            Ide.rfill(g, 0xC4C8D0, width - 6, ty + 2, 4, th - 4, 2);
        }
    }

    void focusTree(FocusEvent e) {
        hasFocus = e.gained;
        repaint();
    }

    void mouseDownTree(MouseEvent e) {
        requestFocus();
        boolean dbl = Ide.doubleClick(this, e);
        int idx = rowAt(e.y);
        if (idx < 0) {
            return;
        }
        IdeItem it = rows.get(idx);
        int x0 = 8 + (flat ? 0 : it.depth()) * indent;
        if (!flat && it.kids.size() > 0 && e.x >= x0 - 2 && e.x <= x0 + 14 && e.button == 1) {
            it.expanded = !it.expanded;
            selected = it;
            rebuild();
            return;
        }
        selected = it;
        repaint();
        if (e.button == 3) {
            if (onSelect != null) {
                onSelect.handle(it);
            }
            if (onContext != null) {
                onContext.handle(it);
            }
            return;
        }
        if (onSelect != null) {
            onSelect.handle(it);
        }
        if (dbl) {
            if (it.kids.size() > 0 && onActivate == null) {
                it.expanded = !it.expanded;
                rebuild();
            }
            if (onActivate != null) {
                onActivate.handle(it);
            }
        }
    }

    void mouseMoveTree(MouseEvent e) {
        int idx = rowAt(e.y);
        IdeItem h = idx < 0 ? null : rows.get(idx);
        if (h != hover) {
            hover = h;
            repaint();
        }
    }

    void mouseExitTree() {
        if (hover != null) {
            hover = null;
            repaint();
        }
    }

    void wheelTree(MouseEvent e) {
        int max = maxScroll();
        scroll = scroll - e.wheelDelta * 24;
        if (scroll < 0) {
            scroll = 0;
        }
        if (scroll > max) {
            scroll = max;
        }
        repaint();
    }

    void keyTree(KeyEvent e) {
        int idx = selected == null ? -1 : rows.indexOf(selected);
        if (e.keyCode == KeyCode.DOWN && idx + 1 < rows.size()) {
            select(rows.get(idx + 1), true);
        } else if (e.keyCode == KeyCode.UP && idx > 0) {
            select(rows.get(idx - 1), true);
        } else if (e.keyCode == KeyCode.RIGHT && selected != null && selected.kids.size() > 0) {
            if (!selected.expanded) {
                selected.expanded = true;
                rebuild();
            } else if (idx + 1 < rows.size()) {
                select(rows.get(idx + 1), true);
            }
        } else if (e.keyCode == KeyCode.LEFT && selected != null) {
            if (selected.expanded && selected.kids.size() > 0) {
                selected.expanded = false;
                rebuild();
            } else if (selected.parent != null && selected.parent.parent != null) {
                select(selected.parent, true);
            }
        } else if (e.keyCode == KeyCode.ENTER && selected != null) {
            if (onActivate != null) {
                onActivate.handle(selected);
            }
        }
    }
}
