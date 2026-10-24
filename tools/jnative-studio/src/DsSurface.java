import jnative.ui.*;
import jnative.graphics.*;

interface DsSurfaceListener {
    void surfaceSelect(DsNode n);

    void surfaceEdited();

    void surfaceDefaultEvent(DsNode n);

    void surfaceStatus(String text);
}

/**
 * The design canvas. Real UiLib controls built from the document sit in the middle layer; a
 * transparent canvas above them draws selection / handles / drop feedback and captures the mouse,
 * so what you edit is exactly what runs.
 *
 * @author 张晓达
 */
public class DsSurface extends Panel {
    static int MARGIN = 36;
    static int TITLE = 30;

    DsDoc doc;
    DsBuild build;
    Canvas bg;
    Panel host;
    Canvas glass;
    DsSurfaceListener listener;

    int deviceW = 0;
    int deviceH = 0;
    int scrollX = 0;
    int scrollY = 0;
    boolean showGuides = true;
    boolean showGrid = true;

    DsNode hoverNode;

    // interaction state
    int mode = 0; // 0 idle, 1 pressed (maybe drag), 2 moving, 3 resizing, 4 external drag
    DsNode pressNode;
    int pressFx;
    int pressFy;
    int rectX0;
    int rectY0;
    int rectW0;
    int rectH0;
    int handleIdx = -1;
    int curFx;
    int curFy;
    int grabDx;
    int grabDy;

    String dragType = ""; // palette type being dragged (external) or armed tool
    String armedType = "";

    // drop feedback
    DsNode dropParent;
    int dropIndex;
    int dropX;
    int dropY;
    int dropCol;
    int dropRow;
    int indKind;
    int indX;
    int indY;
    int indW;
    int indH;
    int ghostX;
    int ghostY;
    int ghostW;
    int ghostH;
    ArrayList<Integer> guideX = new ArrayList<Integer>();
    ArrayList<Integer> guideY = new ArrayList<Integer>();

    DsSurface(DsDoc d) {
        doc = d;
        bg = new Canvas();
        bg.setHiDpi(true);
        bg.onPaint(g -> paintBg(g));
        host = new Panel();
        host.setClipChildren(true, false);
        setClipChildren(true, false);
        glass = new Canvas();
        glass.setHiDpi(true);
        glass.setTransparent(true);
        glass.onPaint(g -> paintGlass(g));
        glass.onMouseDown(e -> mouseDown(e));
        glass.onMouseMove(e -> mouseMove(e));
        glass.onMouseDrag(e -> mouseMove(e));
        glass.onMouseUp(e -> mouseUp(e));
        glass.onMouseWheel(e -> wheel(e));
        glass.onKeyDown(e -> keyDown(e));
        add(bg);
        add(host);
        add(glass);
        rebuild();
    }

    public void layout() {
        super.layout();
        layoutChildren();
    }

    void layoutChildren() {
        if (bg == null || glass == null || host == null) {
            return;
        }
        bg.setBounds(0, 0, width, height);
        glass.setBounds(0, 0, width, height);
        positionHost();
    }

    int formW() {
        if (deviceW > 0) {
            return deviceW;
        }
        return doc.root.getInt("width");
    }

    int formH() {
        if (deviceH > 0) {
            return deviceH;
        }
        return doc.root.getInt("height");
    }

    int hostX() {
        return MARGIN - scrollX;
    }

    int hostY() {
        return MARGIN + TITLE - scrollY;
    }

    void positionHost() {
        host.setBounds(hostX(), hostY(), formW(), formH());
    }

    void setDocument(DsDoc d) {
        doc = d;
        deviceW = 0;
        deviceH = 0;
        scrollX = 0;
        scrollY = 0;
        mode = 0;
        rebuild();
    }

    void setDevice(int w, int h) {
        deviceW = w;
        deviceH = h;
        scrollX = 0;
        scrollY = 0;
        rebuild();
    }

    /** Recreate the real component tree from the document. */
    void rebuild() {
        host.clear();
        build = new DsBuild(true);
        Component root = build.build(doc);
        positionHost();
        if (root != null) {
            build.place(root, host, formW(), formH());
        }
        bg.repaint();
        glass.repaint();
    }

    void redraw() {
        bg.repaint();
        glass.repaint();
    }

    // ---------------------------------------------------------------- geometry

    int[] rectOf(DsNode n) {
        if (n == doc.root) {
            return new int[] { 0, 0, formW(), formH() };
        }
        Component c = build.compOf(n);
        if (c == null) {
            return new int[] { 0, 0, 0, 0 };
        }
        return new int[] { build.absX(c), build.absY(c), c.width, c.height };
    }

    boolean parentIsAbsolute(DsNode n) {
        if (n.parent == null) {
            return false;
        }
        return DsMeta.find(n.parent.type).container == DsType.ABSOLUTE;
    }

    DsNode hit(int fx, int fy) {
        DsNode content = doc.content();
        if (content == null) {
            return null;
        }
        return build.hitTest(content, fx, fy);
    }

    boolean inForm(int fx, int fy) {
        return fx >= 0 && fy >= 0 && fx < formW() && fy < formH();
    }

    boolean inTitle(int fx, int fy) {
        return fx >= 0 && fx < formW() && fy >= -TITLE && fy < 0;
    }

    boolean resizable(DsNode n, int handle) {
        if (n == doc.root) {
            return false;
        }
        if (n == doc.content()) {
            return handle == 3 || handle == 4 || handle == 5;
        }
        return true;
    }

    /** Handle centre in form coordinates. */
    int handleX(int[] r, int h) {
        if (h == 0 || h == 6 || h == 7) {
            return r[0];
        }
        if (h == 1 || h == 5) {
            return r[0] + r[2] / 2;
        }
        return r[0] + r[2];
    }

    int handleY(int[] r, int h) {
        if (h == 0 || h == 1 || h == 2) {
            return r[1];
        }
        if (h == 3 || h == 7) {
            return r[1] + r[3] / 2;
        }
        return r[1] + r[3];
    }

    int hitHandle(DsNode n, int fx, int fy) {
        if (n == null || n == doc.root) {
            return -1;
        }
        int[] r = rectOf(n);
        int h = 0;
        while (h < 8) {
            if (resizable(n, h) && Math.abs(fx - handleX(r, h)) <= 5 && Math.abs(fy - handleY(r, h)) <= 5) {
                return h;
            }
            h = h + 1;
        }
        return -1;
    }

    int cursorForHandle(int h) {
        if (h == 0 || h == 4) {
            return 2;
        }
        if (h == 2 || h == 6) {
            return 2;
        }
        if (h == 1 || h == 5) {
            return 4;
        }
        return 2;
    }

    // ---------------------------------------------------------------- drop logic

    boolean accepts(DsNode t, DsNode dragged) {
        DsType pt = DsMeta.find(t.type);
        if (pt == null || !pt.isContainer() || t == doc.root) {
            return false;
        }
        if (dragged != null && (dragged == t || dragged.isAncestorOf(t))) {
            return false;
        }
        if (pt.container == DsType.SINGLE) {
            return t.children.size() < 1 || (dragged != null && dragged.parent == t);
        }
        if (pt.container == DsType.SPLIT) {
            return t.children.size() < 2 || (dragged != null && dragged.parent == t);
        }
        return true;
    }

    DsNode containerAt(DsNode n, int fx, int fy, DsNode excluded) {
        if (n == excluded) {
            return null;
        }
        Component c = build.compOf(n);
        if (c == null || c.visible == 0) {
            return null;
        }
        int[] r = rectOf(n);
        if (fx < r[0] || fy < r[1] || fx >= r[0] + r[2] || fy >= r[1] + r[3]) {
            return null;
        }
        int i = n.children.size() - 1;
        while (i >= 0) {
            DsNode deep = containerAt(n.children.get(i), fx, fy, excluded);
            if (deep != null) {
                return deep;
            }
            i = i - 1;
        }
        DsType t = DsMeta.find(n.type);
        if (t.isContainer()) {
            return n;
        }
        return null;
    }

    void clearDrop() {
        dropParent = null;
        indKind = 0;
        guideX.clear();
        guideY.clear();
    }

    /** Work out where the dragged / new control would land at (fx, fy). */
    void computeDrop(int fx, int fy, DsNode dragged, int gw, int gh) {
        clearDrop();
        DsNode t = containerAt(doc.content(), fx, fy, dragged);
        while (t != null && !accepts(t, dragged)) {
            t = t.parent;
            if (t == doc.root) {
                t = null;
            }
        }
        if (t == null) {
            return;
        }
        dropParent = t;
        DsType pt = DsMeta.find(t.type);
        int[] tr = rectOf(t);
        indKind = 2;
        indX = tr[0];
        indY = tr[1];
        indW = tr[2];
        indH = tr[3];
        dropIndex = t.children.size();
        if (pt.container == DsType.FLOW) {
            boolean vertical = t.type.equals("VBox");
            int idx = 0;
            int count = 0;
            int lineAt = -1;
            int i = 0;
            while (i < t.children.size()) {
                DsNode ch = t.children.get(i);
                if (ch == dragged) {
                    i = i + 1;
                    continue;
                }
                int[] cr = rectOf(ch);
                int center = vertical ? cr[1] + cr[3] / 2 : cr[0] + cr[2] / 2;
                int p = vertical ? fy : fx;
                if (p < center && lineAt < 0) {
                    lineAt = vertical ? cr[1] - 3 : cr[0] - 3;
                    idx = i;
                }
                count = i + 1;
                i = i + 1;
            }
            if (lineAt < 0) {
                idx = t.children.size();
                if (count > 0) {
                    DsNode last = t.children.get(count - 1);
                    int[] lr = rectOf(last);
                    lineAt = vertical ? lr[1] + lr[3] + 3 : lr[0] + lr[2] + 3;
                } else {
                    lineAt = vertical ? tr[1] + 6 : tr[0] + 6;
                }
            }
            dropIndex = idx;
            indKind = 1;
            if (vertical) {
                indX = tr[0] + 4;
                indY = lineAt;
                indW = tr[2] - 8;
                indH = 3;
            } else {
                indX = lineAt;
                indY = tr[1] + 4;
                indW = 3;
                indH = tr[3] - 8;
            }
        } else if (pt.container == DsType.ABSOLUTE) {
            dropX = snap4(fx - tr[0] - grabDx);
            dropY = snap4(fy - tr[1] - grabDy);
            ghostX = tr[0] + dropX;
            ghostY = tr[1] + dropY;
            ghostW = gw;
            ghostH = gh;
        } else if (pt.container == DsType.GRID) {
            Component gc = build.compOf(t);
            GridPane gp = (GridPane) gc;
            int cols = gp.gridCols;
            int useW = gp.cellW;
            if (cols > 0) {
                int avail = gp.width - gp.padding * 2 - (cols - 1) * gp.spacing;
                if (avail > cols * gp.cellW) {
                    useW = avail / cols;
                }
            }
            int col = (fx - tr[0] - gp.padding) / (useW + gp.spacing);
            int row = (fy - tr[1] - gp.padding) / (gp.cellH + gp.spacing);
            if (col < 0) {
                col = 0;
            }
            if (row < 0) {
                row = 0;
            }
            if (col > 40) {
                col = 40;
            }
            if (row > 40) {
                row = 40;
            }
            dropCol = col;
            dropRow = row;
            indKind = 3;
            indX = tr[0] + gp.padding + col * (useW + gp.spacing);
            indY = tr[1] + gp.padding + row * (gp.cellH + gp.spacing);
            indW = useW;
            indH = gp.cellH;
        }
    }

    int snap4(int v) {
        if (v < 0) {
            return 0;
        }
        return (v / 4) * 4;
    }

    void clearAttached(DsNode n) {
        n.unset("x");
        n.unset("y");
        n.unset("col");
        n.unset("row");
        n.unset("colSpan");
        n.unset("rowSpan");
        n.unset("tabTitle");
    }

    void setAttached(DsNode n, DsNode parent) {
        DsType pt = DsMeta.find(parent.type);
        if (pt.container == DsType.ABSOLUTE) {
            n.set("x", "" + dropX);
            n.set("y", "" + dropY);
        } else if (pt.container == DsType.GRID) {
            n.set("col", "" + dropCol);
            n.set("row", "" + dropRow);
        } else if (pt.container == DsType.TABS) {
            n.set("tabTitle", "Tab " + (parent.children.size() + 1));
        }
    }

    void applyMove(DsNode n) {
        if (dropParent == null) {
            return;
        }
        DsNode t = dropParent;
        doc.beginChange();
        DsNode old = n.parent;
        int oldIdx = old.children.indexOf(n);
        old.remove(n);
        int idx = dropIndex;
        if (old == t && oldIdx < idx) {
            idx = idx - 1;
        }
        clearAttached(n);
        t.insert(idx, n);
        setAttached(n, t);
        doc.changed();
        listener.surfaceEdited();
    }

    void applyNew(String type) {
        if (dropParent == null) {
            return;
        }
        DsNode t = dropParent;
        doc.beginChange();
        DsNode n = doc.create(type);
        t.insert(dropIndex, n);
        setAttached(n, t);
        doc.select(n);
        doc.changed();
        listener.surfaceSelect(n);
        listener.surfaceEdited();
    }

    // ---------------------------------------------------------------- external (palette) drag

    DsType armedOrDragType() {
        if (dragType.length() > 0) {
            return DsMeta.find(dragType);
        }
        return null;
    }

    /** Palette drag over the surface (fx, fy in form coordinates). */
    void externalDrag(String type, int sx, int sy) {
        dragType = type;
        mode = 4;
        grabDx = 0;
        grabDy = 0;
        curFx = sx - hostX();
        curFy = sy - hostY();
        DsType t = DsMeta.find(type);
        computeDrop(curFx, curFy, null, t.defW, t.defH);
        glass.repaint();
    }

    void externalDrop(String type, int sx, int sy) {
        int fx = sx - hostX();
        int fy = sy - hostY();
        DsType t = DsMeta.find(type);
        computeDrop(fx, fy, null, t.defW, t.defH);
        if (dropParent != null) {
            applyNew(type);
        }
        endDrag();
    }

    void externalCancel() {
        endDrag();
    }

    void endDrag() {
        mode = 0;
        dragType = "";
        handleIdx = -1;
        pressNode = null;
        clearDrop();
        glass.repaint();
    }

    void arm(String type) {
        armedType = type;
        glass.repaint();
    }

    /** Add a control of `type` into the selected container (or the form's root container). */
    void addToSelection(String type) {
        DsNode sel = doc.selected();
        DsNode t = sel;
        while (t != null && !accepts(t, null)) {
            t = t.parent;
            if (t == doc.root) {
                t = null;
            }
        }
        if (t == null) {
            t = doc.content();
        }
        if (t == null) {
            return;
        }
        dropParent = t;
        dropIndex = t.children.size();
        DsType pt = DsMeta.find(t.type);
        dropX = 8;
        dropY = 8;
        if (pt.container == DsType.ABSOLUTE) {
            dropX = 8 + (t.children.size() % 10) * 12;
            dropY = 8 + (t.children.size() % 10) * 12;
        }
        dropCol = 0;
        dropRow = 0;
        if (pt.container == DsType.GRID) {
            int col = 0;
            int row = 0;
            int guard = 0;
            while (doc.cellUsed(t, null, col, row) && guard < 200) {
                col = col + 1;
                if (col >= 3) {
                    col = 0;
                    row = row + 1;
                }
                guard = guard + 1;
            }
            dropCol = col;
            dropRow = row;
        }
        applyNew(type);
        clearDrop();
    }

    // ---------------------------------------------------------------- mouse

    void mouseDown(MouseEvent e) {
        glass.requestFocus();
        int fx = e.x - hostX();
        int fy = e.y - hostY();
        boolean dbl = Ide.doubleClick(glass, e);
        if (armedType.length() > 0 && e.button == 1) {
            DsType t = DsMeta.find(armedType);
            grabDx = 0;
            grabDy = 0;
            computeDrop(fx, fy, null, t.defW, t.defH);
            if (dropParent != null) {
                applyNew(armedType);
            }
            armedType = "";
            clearDrop();
            glass.repaint();
            return;
        }
        DsNode sel = doc.selected();
        int hh = e.button == 1 ? hitHandle(sel, fx, fy) : -1;
        if (hh >= 0) {
            mode = 3;
            handleIdx = hh;
            pressNode = sel;
            int[] r = rectOf(sel);
            rectX0 = r[0];
            rectY0 = r[1];
            rectW0 = r[2];
            rectH0 = r[3];
            pressFx = fx;
            pressFy = fy;
            curFx = fx;
            curFy = fy;
            ghostX = rectX0;
            ghostY = rectY0;
            ghostW = rectW0;
            ghostH = rectH0;
            return;
        }
        DsNode n = null;
        if (inForm(fx, fy)) {
            n = hit(fx, fy);
        } else if (inTitle(fx, fy)) {
            n = doc.root;
        }
        if (n == null) {
            n = doc.root;
        }
        doc.select(n);
        listener.surfaceSelect(n);
        glass.repaint();
        if (e.button == 3) {
            showContextMenu(n);
            return;
        }
        if (dbl && n != doc.root) {
            listener.surfaceDefaultEvent(n);
            return;
        }
        if (n != doc.root && n != doc.content() && e.button == 1) {
            mode = 1;
            pressNode = n;
            pressFx = fx;
            pressFy = fy;
            int[] r = rectOf(n);
            rectX0 = r[0];
            rectY0 = r[1];
            rectW0 = r[2];
            rectH0 = r[3];
            grabDx = fx - r[0];
            grabDy = fy - r[1];
        }
    }

    void mouseMove(MouseEvent e) {
        int fx = e.x - hostX();
        int fy = e.y - hostY();
        curFx = fx;
        curFy = fy;
        if (mode == 0) {
            DsNode h = null;
            if (inForm(fx, fy)) {
                h = hit(fx, fy);
            }
            if (h != hoverNode) {
                hoverNode = h;
                glass.repaint();
            }
            updateCursor(fx, fy);
            return;
        }
        if (mode == 1) {
            if (Math.abs(fx - pressFx) > 4 || Math.abs(fy - pressFy) > 4) {
                mode = 2;
            } else {
                return;
            }
        }
        if (mode == 2) {
            computeDrop(fx, fy, pressNode, rectW0, rectH0);
            if (dropParent != null && DsMeta.find(dropParent.type).container == DsType.ABSOLUTE
                    && dropParent == pressNode.parent) {
                snapMove(fx, fy);
            }
            Gfx.cursor(5);
            glass.repaint();
            return;
        }
        if (mode == 3) {
            computeResize(fx - pressFx, fy - pressFy);
            glass.repaint();
            return;
        }
    }

    /** Align the dragged absolute control to siblings / parent edges and record guide lines. */
    void snapMove(int fx, int fy) {
        guideX.clear();
        guideY.clear();
        int[] pr = rectOf(pressNode.parent);
        int nx = fx - grabDx;
        int ny = fy - grabDy;
        int bestDx = 6;
        int bestDy = 6;
        int sx = nx;
        int sy = ny;
        int gx = -1;
        int gy = -1;
        ArrayList<Integer> xs = new ArrayList<Integer>();
        ArrayList<Integer> ys = new ArrayList<Integer>();
        xs.add(pr[0]);
        xs.add(pr[0] + pr[2]);
        xs.add(pr[0] + pr[2] / 2);
        ys.add(pr[1]);
        ys.add(pr[1] + pr[3]);
        ys.add(pr[1] + pr[3] / 2);
        int i = 0;
        while (i < pressNode.parent.children.size()) {
            DsNode sib = pressNode.parent.children.get(i);
            if (sib != pressNode) {
                int[] r = rectOf(sib);
                xs.add(r[0]);
                xs.add(r[0] + r[2]);
                xs.add(r[0] + r[2] / 2);
                ys.add(r[1]);
                ys.add(r[1] + r[3]);
                ys.add(r[1] + r[3] / 2);
            }
            i = i + 1;
        }
        int[] mine = new int[] { 0, rectW0 / 2, rectW0 };
        int[] mineY = new int[] { 0, rectH0 / 2, rectH0 };
        int a = 0;
        while (a < 3) {
            int k = 0;
            while (k < xs.size()) {
                int d = xs.get(k) - (nx + mine[a]);
                if (Math.abs(d) < Math.abs(bestDx) || (Math.abs(d) == Math.abs(bestDx) && gx < 0 && Math.abs(d) < 6)) {
                    bestDx = d;
                    sx = nx + d;
                    gx = xs.get(k);
                }
                k = k + 1;
            }
            int m = 0;
            while (m < ys.size()) {
                int d = ys.get(m) - (ny + mineY[a]);
                if (Math.abs(d) < Math.abs(bestDy) || (Math.abs(d) == Math.abs(bestDy) && gy < 0 && Math.abs(d) < 6)) {
                    bestDy = d;
                    sy = ny + d;
                    gy = ys.get(m);
                }
                m = m + 1;
            }
            a = a + 1;
        }
        if (gx >= 0) {
            guideX.add(gx);
            nx = sx;
        }
        if (gy >= 0) {
            guideY.add(gy);
            ny = sy;
        }
        dropX = nx - pr[0];
        dropY = ny - pr[1];
        ghostX = nx;
        ghostY = ny;
        ghostW = rectW0;
        ghostH = rectH0;
    }

    void computeResize(int dx, int dy) {
        int nx = rectX0;
        int ny = rectY0;
        int nw = rectW0;
        int nh = rectH0;
        int h = handleIdx;
        if (h == 0 || h == 6 || h == 7) {
            nx = rectX0 + dx;
            nw = rectW0 - dx;
        }
        if (h == 2 || h == 3 || h == 4) {
            nw = rectW0 + dx;
        }
        if (h == 0 || h == 1 || h == 2) {
            ny = rectY0 + dy;
            nh = rectH0 - dy;
        }
        if (h == 4 || h == 5 || h == 6) {
            nh = rectH0 + dy;
        }
        if (nw < 8) {
            if (h == 0 || h == 6 || h == 7) {
                nx = rectX0 + rectW0 - 8;
            }
            nw = 8;
        }
        if (nh < 8) {
            if (h == 0 || h == 1 || h == 2) {
                ny = rectY0 + rectH0 - 8;
            }
            nh = 8;
        }
        guideX.clear();
        guideY.clear();
        ghostX = nx;
        ghostY = ny;
        ghostW = nw;
        ghostH = nh;
    }

    void mouseUp(MouseEvent e) {
        int fx = e.x - hostX();
        int fy = e.y - hostY();
        if (mode == 2 && pressNode != null) {
            DsNode n = pressNode;
            computeDrop(fx, fy, n, rectW0, rectH0);
            if (dropParent != null && DsMeta.find(dropParent.type).container == DsType.ABSOLUTE
                    && dropParent == n.parent) {
                snapMove(fx, fy);
            }
            if (dropParent != null) {
                if (dropParent == n.parent && DsMeta.find(dropParent.type).container == DsType.ABSOLUTE) {
                    doc.beginChange();
                    n.set("x", "" + dropX);
                    n.set("y", "" + dropY);
                    doc.changed();
                    listener.surfaceEdited();
                } else if (dropParent == n.parent && dropIndex == n.indexInParent()) {
                    // no change
                } else {
                    applyMove(n);
                }
            }
        } else if (mode == 3 && pressNode != null) {
            applyResize(pressNode);
        }
        endDrag();
    }

    void applyResize(DsNode n) {
        if (ghostW == rectW0 && ghostH == rectH0 && ghostX == rectX0 && ghostY == rectY0) {
            return;
        }
        doc.beginChange();
        if (n == doc.content()) {
            doc.root.set("width", "" + ghostW);
            doc.root.set("height", "" + ghostH);
            deviceW = 0;
            deviceH = 0;
        } else {
            if (parentIsAbsolute(n)) {
                int[] pr = rectOf(n.parent);
                n.set("x", "" + (ghostX - pr[0]));
                n.set("y", "" + (ghostY - pr[1]));
            }
            n.set("width", "" + ghostW);
            n.set("height", "" + ghostH);
        }
        doc.changed();
        listener.surfaceEdited();
    }

    void updateCursor(int fx, int fy) {
        DsNode sel = doc.selected();
        int h = hitHandle(sel, fx, fy);
        if (h >= 0) {
            Gfx.cursor(cursorForHandle(h));
        } else if (armedType.length() > 0) {
            Gfx.cursor(6);
        } else {
            Gfx.cursor(0);
        }
    }

    void wheel(MouseEvent e) {
        int fw = formW() + MARGIN * 2;
        int fh = formH() + MARGIN * 2 + TITLE;
        if (e.shiftDown != 0) {
            scrollX = clamp(scrollX - e.wheelDelta * 30, 0, Math.max(0, fw - width));
        } else {
            scrollY = clamp(scrollY - e.wheelDelta * 30, 0, Math.max(0, fh - height));
        }
        positionHost();
        redraw();
    }

    static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    void keyDown(KeyEvent e) {
        DsNode sel = doc.selected();
        if (e.keyCode == KeyCode.ESCAPE) {
            armedType = "";
            if (mode != 0) {
                endDrag();
            }
            glass.repaint();
            return;
        }
        if (sel == null || sel == doc.root) {
            return;
        }
        if (e.keyCode == KeyCode.DELETE || e.keyCode == KeyCode.BACKSPACE) {
            deleteSelected();
            return;
        }
        int step = e.shiftDown != 0 ? 8 : 1;
        int dx = 0;
        int dy = 0;
        if (e.keyCode == KeyCode.LEFT) {
            dx = -step;
        } else if (e.keyCode == KeyCode.RIGHT) {
            dx = step;
        } else if (e.keyCode == KeyCode.UP) {
            dy = -step;
        } else if (e.keyCode == KeyCode.DOWN) {
            dy = step;
        }
        if ((dx != 0 || dy != 0) && parentIsAbsolute(sel)) {
            doc.beginChange();
            sel.set("x", "" + (sel.getInt("x") + dx));
            sel.set("y", "" + (sel.getInt("y") + dy));
            doc.changed();
            listener.surfaceEdited();
        }
    }

    void deleteSelected() {
        DsNode sel = doc.selected();
        if (sel == null || sel == doc.root || sel == doc.content()) {
            return;
        }
        DsNode p = sel.parent;
        doc.deleteNode(sel);
        doc.select(p);
        listener.surfaceSelect(p);
        listener.surfaceEdited();
    }

    void showContextMenu(DsNode n) {
        ContextMenu cm = new ContextMenu();
        if (n != doc.root) {
            DsType t = DsMeta.find(n.type);
            if (t.defaultEvent.length() > 0) {
                cm.add(new MenuItem("编写 " + t.defaultEvent + " 事件处理…", () -> listener.surfaceDefaultEvent(n)));
                cm.addSeparator();
            }
            cm.add(new MenuItem("复制", () -> {
                doc.duplicate(n);
                listener.surfaceSelect(doc.selected());
                listener.surfaceEdited();
            }));
            cm.add(new MenuItem("上移一层", () -> moveBy(n, -1)));
            cm.add(new MenuItem("下移一层", () -> moveBy(n, 1)));
            if (n != doc.content()) {
                cm.addSeparator();
                cm.add(new MenuItem("删除", () -> deleteSelected()));
            }
        } else {
            cm.add(new MenuItem("窗体属性在右侧面板编辑", () -> {
            }));
        }
        cm.showAt(UiNative.cursorScreenX(), UiNative.cursorScreenY());
    }

    void moveBy(DsNode n, int delta) {
        if (n.parent == null || n.parent == doc.root) {
            return;
        }
        int idx = n.indexInParent() + delta;
        if (idx < 0 || idx >= n.parent.children.size()) {
            return;
        }
        doc.beginChange();
        DsNode p = n.parent;
        p.remove(n);
        p.insert(idx, n);
        doc.changed();
        listener.surfaceEdited();
    }

    // ---------------------------------------------------------------- painting

    void paintBg(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, 0xE4E7EC, 0, 0, width, height);
        if (showGrid) {
            Ide.rgba(g, 0x9AA1AD, 70);
            int gx = hostX() % 16;
            int gy = hostY() % 16;
            int y = gy - 16;
            while (y < height) {
                int x = gx - 16;
                while (x < width) {
                    g.fillRect(x, y, 1, 1);
                    x = x + 16;
                }
                y = y + 16;
            }
        }
        int hx = hostX();
        int hy = hostY();
        int fw = formW();
        int fh = formH();
        // shadow
        int s = 0;
        while (s < 10) {
            Ide.rgba(g, 0x000000, 5);
            g.fillRoundRect(hx - s, hy - TITLE - s + 4, fw + s * 2, fh + TITLE + s * 2, 10 + s);
            s = s + 1;
        }
        // title bar
        Ide.rfill(g, 0xEEEFF2, hx, hy - TITLE, fw, TITLE + 8, 9);
        Ide.fill(g, 0xFFFFFF, hx, hy, fw, fh);
        Ide.line(g, 0xD0D3D9, hx, hy - 0.5, hx + fw, hy - 0.5);
        Ide.rgb(g, 0xFF5F57);
        g.fillEllipse(hx + 12, hy - TITLE + 9, 12, 12);
        Ide.rgb(g, 0xFEBC2E);
        g.fillEllipse(hx + 32, hy - TITLE + 9, 12, 12);
        Ide.rgb(g, 0x28C840);
        g.fillEllipse(hx + 52, hy - TITLE + 9, 12, 12);
        String title = doc.root.get("title");
        Font f = Ide.ui();
        int tw = Ide.textWidth(g, title, f);
        Ide.textRow(g, title, hx + (fw - tw) / 2.0, hy - TITLE, TITLE, f, Ide.SUBTLE);
        Ide.rstroke(g, 0xC9CCD3, hx, hy - TITLE, fw, fh + TITLE, 8);
        // size badge
        String size = fw + " × " + fh + (deviceW > 0 ? "  (设备预览)" : "");
        Ide.textRow(g, size, hx, hy + fh + 4, 18, Ide.small(), Ide.FAINT);
    }

    void dashedRect(Graphics g, double x, double y, double w, double h) {
        int step = 6;
        double cx = x;
        while (cx < x + w) {
            double e = Math.min(cx + 3, x + w);
            g.drawLine(cx, y, e, y);
            g.drawLine(cx, y + h, e, y + h);
            cx = cx + step;
        }
        double cy = y;
        while (cy < y + h) {
            double e = Math.min(cy + 3, y + h);
            g.drawLine(x, cy, x, e);
            g.drawLine(x + w, cy, x + w, e);
            cy = cy + step;
        }
    }

    void paintContainerGuides(Graphics g, DsNode n) {
        int i = 0;
        while (i < n.children.size()) {
            DsNode c = n.children.get(i);
            DsType t = DsMeta.find(c.type);
            Component cc = build.compOf(c);
            if (cc != null && cc.visible != 0) {
                int[] r = rectOf(c);
                if (t.isContainer()) {
                    g.setLineWidth(1.0);
                    Ide.rgba(g, 0x5B6B82, 70);
                    dashedRect(g, hostX() + r[0] + 0.5, hostY() + r[1] + 0.5, r[2] - 1, r[3] - 1);
                    if (c.children.size() == 0 && t.container != DsType.TABS) {
                        String hint = "拖入控件";
                        Font f = Ide.small();
                        int tw = Ide.textWidth(g, hint, f);
                        Ide.textRow(g, hint, hostX() + r[0] + (r[2] - tw) / 2.0, hostY() + r[1] + r[3] / 2.0 - 9, 18, f,
                                Ide.FAINT);
                    }
                }
                paintContainerGuides(g, c);
            }
            i = i + 1;
        }
    }

    void paintGlass(Graphics g) {
        g.setAntialias(true);
        if (build == null) {
            return;
        }
        DsNode content = doc.content();
        if (showGuides && content != null) {
            DsType t0 = DsMeta.find(content.type);
            int[] r0 = rectOf(content);
            g.setLineWidth(1.0);
            Ide.rgba(g, 0x5B6B82, 70);
            dashedRect(g, hostX() + 0.5, hostY() + 0.5, r0[2] - 1, r0[3] - 1);
            if (content.children.size() == 0) {
                String hint = "从左侧工具箱拖入控件，或双击控件添加";
                Font f = Ide.ui();
                int tw = Ide.textWidth(g, hint, f);
                Ide.textRow(g, hint, hostX() + (r0[2] - tw) / 2.0, hostY() + r0[3] / 2.0 - 9, 18, f, Ide.FAINT);
            }
            paintContainerGuides(g, content);
        }
        DsNode sel = doc.selected();
        if (hoverNode != null && hoverNode != sel && mode == 0) {
            int[] r = rectOf(hoverNode);
            Ide.rgba(g, Ide.ACCENT, 28);
            g.fillRect(hostX() + r[0], hostY() + r[1], r[2], r[3]);
            Ide.rgba(g, Ide.ACCENT, 160);
            g.setLineWidth(1.0);
            g.drawRect(hostX() + r[0] + 0.5, hostY() + r[1] + 0.5, r[2] - 1, r[3] - 1);
        }
        if (sel != null && sel != doc.root && doc.findByName(sel.name) != null && build.compOf(sel) != null) {
            paintSelection(g, sel);
        } else if (sel == doc.root) {
            Ide.rgb(g, Ide.ACCENT);
            g.setLineWidth(2.0);
            g.drawRoundRect(hostX() - 1, hostY() - TITLE - 1, formW() + 2, formH() + TITLE + 2, 8);
        }
        // drop feedback
        if (mode == 2 || mode == 4) {
            paintDropFeedback(g);
        }
        if (mode == 3) {
            Ide.rgba(g, Ide.ACCENT, 40);
            g.fillRect(hostX() + ghostX, hostY() + ghostY, ghostW, ghostH);
            Ide.rgb(g, Ide.ACCENT);
            g.setLineWidth(1.0);
            g.drawRect(hostX() + ghostX + 0.5, hostY() + ghostY + 0.5, ghostW - 1, ghostH - 1);
            String sz = ghostW + " × " + ghostH;
            chip(g, sz, hostX() + ghostX + ghostW + 6, hostY() + ghostY + ghostH + 6);
        }
        if (armedType.length() > 0) {
            Ide.textRow(g, "单击画布放置 " + armedType, 12, height - 26, 18, Ide.ui(), Ide.ACCENT);
        }
    }

    void chip(Graphics g, String text, double x, double y) {
        Font f = Ide.small();
        int tw = Ide.textWidth(g, text, f);
        Ide.rfill(g, 0x2B3340, x, y, tw + 12, 18, 4);
        Ide.textRow(g, text, x + 6, y, 18, f, 0xFFFFFF);
    }

    void paintSelection(Graphics g, DsNode sel) {
        int[] r = rectOf(sel);
        double x = hostX() + r[0];
        double y = hostY() + r[1];
        Ide.rgba(g, Ide.ACCENT, 22);
        g.fillRect(x, y, r[2], r[3]);
        Ide.rgb(g, Ide.ACCENT);
        g.setLineWidth(2.0);
        g.drawRect(x + 1, y + 1, r[2] - 2, r[3] - 2);
        int h = 0;
        while (h < 8) {
            if (resizable(sel, h)) {
                double hx = hostX() + handleX(r, h);
                double hy = hostY() + handleY(r, h);
                Ide.rfill(g, 0xFFFFFF, hx - 4, hy - 4, 8, 8, 2);
                Ide.rstroke(g, Ide.ACCENT, hx - 4, hy - 4, 8, 8, 2);
            }
            h = h + 1;
        }
        String tag = sel.name + " : " + sel.type + "   " + r[2] + "×" + r[3];
        double ty = y - 22;
        if (ty < 2) {
            ty = y + r[3] + 4;
        }
        chip(g, tag, x, ty);
    }

    void paintDropFeedback(Graphics g) {
        if (dropParent == null) {
            return;
        }
        double ox = hostX();
        double oy = hostY();
        DsType pt = DsMeta.find(dropParent.type);
        if (indKind == 2) {
            Ide.rgba(g, Ide.ACCENT, 20);
            g.fillRect(ox + indX, oy + indY, indW, indH);
            Ide.rgb(g, Ide.ACCENT);
            g.setLineWidth(1.0);
            g.drawRect(ox + indX + 0.5, oy + indY + 0.5, indW - 1, indH - 1);
        } else if (indKind == 1) {
            Ide.rgb(g, Ide.ACCENT);
            g.fillRect(ox + indX, oy + indY, indW, indH);
        } else if (indKind == 3) {
            Ide.rgba(g, Ide.ACCENT, 40);
            g.fillRect(ox + indX, oy + indY, indW, indH);
            Ide.rgb(g, Ide.ACCENT);
            g.setLineWidth(1.0);
            g.drawRect(ox + indX + 0.5, oy + indY + 0.5, indW - 1, indH - 1);
        }
        if (pt.container == DsType.ABSOLUTE) {
            Ide.rgba(g, Ide.ACCENT, 50);
            g.fillRect(ox + ghostX, oy + ghostY, ghostW, ghostH);
            Ide.rgb(g, Ide.ACCENT);
            g.setLineWidth(1.0);
            g.drawRect(ox + ghostX + 0.5, oy + ghostY + 0.5, ghostW - 1, ghostH - 1);
            chip(g, "x=" + dropX + "  y=" + dropY, ox + ghostX, oy + ghostY - 22);
        }
        Ide.rgb(g, 0xE0338A);
        g.setLineWidth(1.0);
        int i = 0;
        while (i < guideX.size()) {
            g.drawLine(ox + guideX.get(i) + 0.5, 0, ox + guideX.get(i) + 0.5, height);
            i = i + 1;
        }
        i = 0;
        while (i < guideY.size()) {
            g.drawLine(0, oy + guideY.get(i) + 0.5, width, oy + guideY.get(i) + 0.5);
            i = i + 1;
        }
    }
}
