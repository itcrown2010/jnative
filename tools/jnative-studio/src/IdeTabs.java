import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;

interface IdeIntHandler {
    void handle(int i);
}

/**
 * An open document (code editor or form designer).
 *
 * @author 张晓达
 */
class IdeDoc {
    String path = "";
    String title = "";
    int kind = 0; // 0 code, 1 designer
    CodeEditor editor;
    DsDesigner designer;
    DsDoc ds;
    boolean dirtyFlag = false;

    boolean isDirty() {
        if (kind == 1) {
            return ds != null && ds.dirty;
        }
        if (editor != null) {
            return editor.document.version != editor.document.savedVersion;
        }
        return dirtyFlag;
    }

    Component view() {
        if (kind == 1) {
            return designer;
        }
        return editor;
    }
}

/**
 * Custom document tab strip with close buttons and a modified marker.
 *
 * @author 张晓达
 */
public class IdeTabs extends Canvas {
    ArrayList<IdeDoc> docs = new ArrayList<IdeDoc>();
    int active = -1;
    int hover = -1;
    int hoverClose = -1;
    IdeIntHandler onSelect;
    IdeIntHandler onClose;
    int scrollX = 0;

    IdeTabs() {
        setHiDpi(true);
        onPaint(g -> paintTabs(g));
        onMouseDown(e -> downTabs(e));
        onMouseMove(e -> moveTabs(e));
        onMouseExit(e -> {
            hover = -1;
            hoverClose = -1;
            repaint();
        });
    }

    int tabWidth(Graphics g, IdeDoc d) {
        int w = Ide.textWidth(g, d.title, Ide.ui()) + 62;
        if (w < 90) {
            w = 90;
        }
        if (w > 220) {
            w = 220;
        }
        return w;
    }

    int indexAt(int mx) {
        Image tmp = Image.create(4, 4);
        Graphics g = tmp.beginGraphics();
        int x = 0;
        int found = -1;
        int i = 0;
        while (i < docs.size()) {
            int w = tabWidth(g, docs.get(i));
            if (mx >= x && mx < x + w) {
                found = i;
                hoverX = x;
                hoverW = w;
                break;
            }
            x = x + w;
            i = i + 1;
        }
        g.end();
        tmp.dispose();
        return found;
    }

    int hoverX = 0;
    int hoverW = 0;

    void moveTabs(MouseEvent e) {
        int idx = indexAt(e.x);
        int hc = -1;
        if (idx >= 0 && e.x >= hoverX + hoverW - 26) {
            hc = idx;
        }
        if (idx != hover || hc != hoverClose) {
            hover = idx;
            hoverClose = hc;
            repaint();
        }
    }

    void downTabs(MouseEvent e) {
        int idx = indexAt(e.x);
        if (idx < 0) {
            return;
        }
        if (e.button == 2 || (e.button == 1 && e.x >= hoverX + hoverW - 26)) {
            if (onClose != null) {
                onClose.handle(idx);
            }
            return;
        }
        if (e.button == 1 && onSelect != null) {
            onSelect.handle(idx);
        }
    }

    void paintTabs(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.BG, 0, 0, width, height);
        int x = 0;
        int i = 0;
        while (i < docs.size()) {
            IdeDoc d = docs.get(i);
            int w = tabWidth(g, d);
            boolean act = i == active;
            if (act) {
                Ide.fill(g, Ide.PANEL, x, 0, w, height);
                Ide.fill(g, Ide.ACCENT, x, 0, w, 2);
            } else if (i == hover) {
                Ide.fill(g, Ide.HOVER, x, 0, w, height);
            }
            Ide.line(g, Ide.BORDER, x + w - 0.5, 0, x + w - 0.5, height);
            String ic = d.kind == 1 ? "form" : Ide.iconForFile(Ide.baseName(d.path));
            Ide.icon(g, ic, x + 10, (height - 15) / 2.0, 15);
            String t = Ide.ellipsize(g, d.title, Ide.ui(), w - 56);
            Ide.textRow(g, t, x + 30, 0, height, Ide.ui(), act ? Ide.TEXT : Ide.SUBTLE);
            double cx = x + w - 15;
            double cy = height / 2.0;
            if (i == hoverClose) {
                Ide.rfill(g, 0xDADDE3, cx - 8, cy - 8, 16, 16, 4);
            }
            if (d.isDirty() && i != hoverClose) {
                Ide.rfill(g, Ide.TEXT, cx - 3.5, cy - 3.5, 7, 7, 3.5);
            } else if (act || i == hover) {
                Ide.line(g, Ide.SUBTLE, cx - 3, cy - 3, cx + 3, cy + 3);
                Ide.line(g, Ide.SUBTLE, cx - 3, cy + 3, cx + 3, cy - 3);
            }
            x = x + w;
            i = i + 1;
        }
        Ide.line(g, Ide.BORDER, x, height - 0.5, width, height - 0.5);
        if (active >= 0) {
            // keep the active tab's bottom edge open into the content
            int ax = 0;
            int j = 0;
            while (j < active) {
                ax = ax + tabWidth(g, docs.get(j));
                j = j + 1;
            }
            Ide.fill(g, Ide.PANEL, ax + 1, height - 1, tabWidth(g, docs.get(active)) - 2, 1);
        }
    }

    int find(String path) {
        int i = 0;
        while (i < docs.size()) {
            if (docs.get(i).path.equals(path)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }
}
