import jnative.ui.*;
import jnative.graphics.*;

interface DsPaletteListener {
    void paletteArm(String type);

    void paletteAdd(String type);

    void paletteDrag(String type, int wx, int wy);

    void paletteDrop(String type, int wx, int wy);

    void paletteCancel();
}

/**
 * Component toolbox: grouped, filterable list. Drag a component onto the design surface, click to
 * arm it for placement, or double-click to add it to the selected container.
 *
 * @author 张晓达
 */
public class DsPalette extends Canvas {
    static int ROW = 28;
    static int HEAD = 26;

    // flattened rows: type == "" means a category header
    ArrayList<String> rowType = new ArrayList<String>();
    ArrayList<String> rowText = new ArrayList<String>();
    ArrayList<DsType> rowMeta = new ArrayList<DsType>();
    String filter = "";
    int scroll = 0;
    int hoverRow = -1;
    String armed = "";
    DsPaletteListener listener;

    // drag state
    int pressRow = -1;
    int pressX;
    int pressY;
    boolean dragging = false;

    ArrayList<String> collapsed = new ArrayList<String>();

    DsPalette() {
        setHiDpi(true);
        onPaint(g -> paintPalette(g));
        onMouseDown(e -> down(e));
        onMouseMove(e -> move(e));
        onMouseDrag(e -> move(e));
        onMouseUp(e -> up(e));
        onMouseExit(e -> {
            hoverRow = -1;
            repaint();
        });
        onMouseWheel(e -> {
            scroll = scroll - e.wheelDelta * 24;
            clampScroll();
            repaint();
        });
        rebuildRows();
    }

    void setFilter(String f) {
        filter = f.toLowerCase();
        rebuildRows();
        repaint();
    }

    void rebuildRows() {
        DsMeta.init();
        rowType.clear();
        rowText.clear();
        rowMeta.clear();
        String[] cats = new String[] { "基本控件", "布局" };
        int c = 0;
        while (c < cats.length) {
            ArrayList<DsType> items = new ArrayList<DsType>();
            int i = 0;
            while (i < DsMeta.types.size()) {
                DsType t = DsMeta.types.get(i);
                if (t.category.equals(cats[c]) && matches(t)) {
                    items.add(t);
                }
                i = i + 1;
            }
            if (items.size() > 0) {
                rowType.add("");
                rowText.add(cats[c]);
                rowMeta.add(null);
                if (!isCollapsed(cats[c]) || filter.length() > 0) {
                    int k = 0;
                    while (k < items.size()) {
                        rowType.add(items.get(k).name);
                        rowText.add(items.get(k).name);
                        rowMeta.add(items.get(k));
                        k = k + 1;
                    }
                }
            }
            c = c + 1;
        }
        clampScroll();
    }

    boolean matches(DsType t) {
        if (filter.length() == 0) {
            return true;
        }
        return t.name.toLowerCase().indexOf(filter) >= 0;
    }

    boolean isCollapsed(String cat) {
        return collapsed.indexOf(cat) >= 0;
    }

    int rowHeight(int i) {
        return rowType.get(i).length() == 0 ? HEAD : ROW;
    }

    int contentHeight() {
        int h = 4;
        int i = 0;
        while (i < rowType.size()) {
            h = h + rowHeight(i);
            i = i + 1;
        }
        return h;
    }

    void clampScroll() {
        int max = Math.max(0, contentHeight() - height);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    int rowAt(int y) {
        int cy = 4 - scroll;
        int i = 0;
        while (i < rowType.size()) {
            int h = rowHeight(i);
            if (y >= cy && y < cy + h) {
                return i;
            }
            cy = cy + h;
            i = i + 1;
        }
        return -1;
    }

    void paintPalette(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        g.setClip(0, 0, width, height);
        int cy = 4 - scroll;
        int i = 0;
        while (i < rowType.size()) {
            int h = rowHeight(i);
            if (cy + h > 0 && cy < height) {
                String type = rowType.get(i);
                if (type.length() == 0) {
                    Ide.arrow(g, 14, cy + h / 2.0, !isCollapsed(rowText.get(i)), Ide.SUBTLE);
                    Ide.textRow(g, rowText.get(i), 26, cy, h, Ide.bold(), Ide.SUBTLE);
                } else {
                    boolean isArmed = type.equals(armed);
                    if (isArmed) {
                        Ide.rfill(g, Ide.SEL, 4, cy + 1, width - 8, h - 2, 5);
                    } else if (i == hoverRow) {
                        Ide.rfill(g, Ide.HOVER, 4, cy + 1, width - 8, h - 2, 5);
                    }
                    DsType t = rowMeta.get(i);
                    Ide.rfill(g, 0xE9ECF2, 14, cy + 4, 20, 20, 5);
                    Ide.rstroke(g, 0xCFD4DC, 14, cy + 4, 20, 20, 5);
                    Font gf = Ide.ui();
                    int gw = Ide.textWidth(g, t.glyph, gf);
                    Ide.textRow(g, t.glyph, 14 + (20 - gw) / 2.0, cy + 4, 20, gf, Ide.ACCENT);
                    Ide.textRow(g, type, 44, cy, h, Ide.ui(), Ide.TEXT);
                }
            }
            cy = cy + h;
            i = i + 1;
        }
        g.clearClip();
    }

    void down(MouseEvent e) {
        int r = rowAt(e.y);
        if (r < 0) {
            return;
        }
        String type = rowType.get(r);
        if (type.length() == 0) {
            String cat = rowText.get(r);
            int idx = collapsed.indexOf(cat);
            if (idx >= 0) {
                collapsed.remove(idx);
            } else {
                collapsed.add(cat);
            }
            rebuildRows();
            repaint();
            return;
        }
        boolean dbl = Ide.doubleClick(this, e);
        if (dbl && listener != null) {
            listener.paletteAdd(type);
            return;
        }
        pressRow = r;
        pressX = e.x;
        pressY = e.y;
        dragging = false;
    }

    void move(MouseEvent e) {
        if (pressRow < 0) {
            int r = rowAt(e.y);
            if (r != hoverRow) {
                hoverRow = r;
                repaint();
            }
            return;
        }
        String type = rowType.get(pressRow);
        if (!dragging && (Math.abs(e.x - pressX) > 4 || Math.abs(e.y - pressY) > 4)) {
            dragging = true;
        }
        if (dragging && listener != null) {
            listener.paletteDrag(type, Ide.windowX(this) + e.x, Ide.windowY(this) + e.y);
        }
    }

    void up(MouseEvent e) {
        if (pressRow < 0) {
            return;
        }
        String type = rowType.get(pressRow);
        if (dragging) {
            if (listener != null) {
                listener.paletteDrop(type, Ide.windowX(this) + e.x, Ide.windowY(this) + e.y);
            }
        } else {
            if (armed.equals(type)) {
                armed = "";
            } else {
                armed = type;
            }
            if (listener != null) {
                listener.paletteArm(armed);
            }
        }
        pressRow = -1;
        dragging = false;
        repaint();
    }

    void clearArmed() {
        if (armed.length() > 0) {
            armed = "";
            repaint();
        }
    }
}
