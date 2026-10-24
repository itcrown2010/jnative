import jnative.ui.*;
import jnative.graphics.*;

interface DsPropsListener {
    /** A property changed in the document: refresh the surface (and outline names). */
    void propsEdited();

    void propsGotoEvent(DsNode n, DsEvent ev);
}

/** Small colour swatch that opens a preset-colour menu. */
class DsSwatch extends Canvas {
    int rgb = -1;
    Runnable onPick;
    String picked = "";

    DsSwatch() {
        setHiDpi(true);
        onPaint(g -> paintSwatch(g));
        onMouseDown(e -> {
            if (onPick != null) {
                onPick.run();
            }
        });
    }

    void paintSwatch(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        if (rgb >= 0) {
            Ide.rfill(g, rgb, 1, 1, width - 2, height - 2, 4);
        } else {
            Ide.rfill(g, 0xFFFFFF, 1, 1, width - 2, height - 2, 4);
            Ide.line(g, Ide.ERROR, 4, height - 4, width - 4, 4);
        }
        Ide.rstroke(g, 0xB9BEC8, 1, 1, width - 2, height - 2, 4);
    }
}

/**
 * Property inspector for the selected component: grouped editors generated from the DsMeta catalog
 * plus the event list with handler binding.
 *
 * @author 张晓达
 */
public class DsProps extends Panel {
    static int LABEL_X = 12;
    static int EDIT_X = 108;

    DsDoc doc;
    DsNode node;
    DsPropsListener listener;
    Panel clip;
    Canvas bar;
    int scrollY = 0;
    int totalH = 0;
    boolean dragBar = false;
    int dragStartY = 0;
    int dragStartScroll = 0;
    Panel body;
    int builtWidth = -1;
    int builtHeight = -1;
    int contentH = 0;

    DsProps() {
        clip = new Panel();
        clip.setClipChildren(true, false);
        body = new Panel();
        body.setClipChildren(false, true);
        body.onMouseWheel(e -> scrollBy(-e.wheelDelta * 24));
        clip.add(body);
        add(clip);
        bar = new Canvas();
        bar.setHiDpi(true);
        bar.setTransparent(true);
        bar.onPaint(g -> paintBar(g));
        bar.onMouseDown(e -> barDown(e.y));
        bar.onMouseDrag(e -> barDrag(e.y));
        bar.onMouseUp(e -> {
            dragBar = false;
        });
        bar.onMouseWheel(e -> scrollBy(-e.wheelDelta * 24));
        add(bar);
    }

    public void layout() {
        super.layout();
        if (clip == null || bar == null) {
            return;
        }
        clip.setBounds(0, 0, width, height);
        bar.setBounds(width - 10, 0, 10, height);
        if ((width != builtWidth || height != builtHeight) && width > 40) {
            rebuild();
        }
    }

    void setNode(DsDoc d, DsNode n) {
        doc = d;
        node = n;
        if (doc != null) {
            doc.endCoalesce();
        }
        rebuild();
    }

    Component put(Component c, int x, int y, int w, int h) {
        body.add(c);
        c.setBounds(x, y, w, h);
        return c;
    }

    Label label(String text, int x, int y, int w, boolean bold) {
        Label l = new Label(text);
        l.setForegroundRgba(Component.rgba(bold ? 90 : 107, bold ? 98 : 114, bold ? 112 : 128, 255));
        if (bold) {
            l.setFontSize(11);
        } else {
            l.setFontSize(12);
        }
        put(l, x, y, w, 20);
        return l;
    }

    void rebuild() {
        builtWidth = width;
        builtHeight = height;
        body.clear();
        int w = Math.max(width, 120);
        int y = 8;
        if (doc == null || node == null) {
            Label l = new Label("未选择组件");
            l.setForegroundRgba(Component.rgba(154, 161, 173, 255));
            put(l, 14, 12, w - 28, 20);
            finish(w, 40);
            return;
        }
        DsType type = DsMeta.find(node.type);
        Label head = new Label(node.type.equals("Form") ? "窗体 " + node.name : node.name + " : " + node.type);
        head.setFontSize(13);
        put(head, 12, y, w - 24, 22);
        y = y + 28;
        int ew = w - EDIT_X - 12;
        if (!node.type.equals("Form")) {
            label("名称", LABEL_X, y + 4, 90, false);
            TextField nameField = new TextField(node.name);
            nameField.onChange(e -> renameNode(nameField));
            put(nameField, EDIT_X, y, ew, 28);
            y = y + 34;
        }
        ArrayList<DsProp> ps = DsMeta.propsFor(node.type);
        ArrayList<DsProp> att = DsMeta.attachedProps(node);
        int ai = 0;
        while (ai < att.size()) {
            ps.add(att.get(ai));
            ai = ai + 1;
        }
        ArrayList<String> groups = new ArrayList<String>();
        int i = 0;
        while (i < ps.size()) {
            if (groups.indexOf(ps.get(i).group) < 0) {
                groups.add(ps.get(i).group);
            }
            i = i + 1;
        }
        int g = 0;
        while (g < groups.size()) {
            String grp = groups.get(g);
            boolean any = false;
            int k = 0;
            while (k < ps.size()) {
                DsProp p = ps.get(k);
                if (p.group.equals(grp) && visibleProp(p)) {
                    if (!any) {
                        y = y + 6;
                        label(grp, LABEL_X, y, w - 24, true);
                        y = y + 22;
                        any = true;
                    }
                    y = addRow(p, y, w);
                }
                k = k + 1;
            }
            g = g + 1;
        }
        // events
        if (type.events.size() > 0) {
            y = y + 6;
            label("事件", LABEL_X, y, w - 24, true);
            y = y + 22;
            int e = 0;
            while (e < type.events.size()) {
                y = addEventRow(type.events.get(e), y, w);
                e = e + 1;
            }
        }
        finish(w, y + 16);
    }

    boolean visibleProp(DsProp p) {
        // hide width/height of the form's content root (they mirror the window size)
        if (node.parent != null && node.parent.type.equals("Form")) {
            if (p.name.equals("width") || p.name.equals("height") || p.name.equals("hgrow") || p.name.equals("vgrow")) {
                return false;
            }
        }
        return true;
    }

    void finish(int w, int h) {
        totalH = h;
        contentH = Math.max(h, height);
        int maxScroll = Math.max(0, totalH - height);
        if (scrollY > maxScroll) {
            scrollY = maxScroll;
        }
        body.setPrefSize(w, contentH);
        body.setBounds(0, -scrollY, w, contentH);
        body.layout();
        bar.repaint();
    }

    void scrollBy(int dy) {
        int maxScroll = Math.max(0, totalH - height);
        int ns = Math.max(0, Math.min(maxScroll, scrollY + dy));
        if (ns == scrollY) {
            return;
        }
        scrollY = ns;
        body.setBounds(0, -scrollY, body.width, body.height);
        bar.repaint();
    }

    void paintBar(Graphics g) {
        g.setAntialias(true);
        int maxScroll = totalH - height;
        if (maxScroll <= 0 || height <= 0) {
            return;
        }
        int th = Math.max(30, height * height / totalH);
        int ty = (height - th) * scrollY / maxScroll;
        Ide.rfill(g, dragBar ? 0x7A808C : 0xB9BEC8, 3, ty + 2, 5, th - 4, 3);
    }

    void barDown(int y) {
        int maxScroll = totalH - height;
        if (maxScroll <= 0) {
            return;
        }
        int th = Math.max(30, height * height / totalH);
        int ty = (height - th) * scrollY / maxScroll;
        if (y >= ty && y < ty + th) {
            dragBar = true;
            dragStartY = y;
            dragStartScroll = scrollY;
        } else {
            scrollBy(y < ty ? -height + 40 : height - 40);
        }
    }

    void barDrag(int y) {
        if (!dragBar) {
            return;
        }
        int maxScroll = totalH - height;
        int th = Math.max(30, height * height / totalH);
        int room = Math.max(1, height - th);
        int target = dragStartScroll + (y - dragStartY) * maxScroll / room;
        scrollBy(target - scrollY);
    }

    int addRow(DsProp p, int y, int w) {
        int ew = w - EDIT_X - 12;
        label(p.label, LABEL_X, y + 4, 92, false);
        String val = node.get(p.name);
        if (p.kind == DsProp.BOOL) {
            CheckBox cb = new CheckBox("");
            cb.setSelected(val.equals("true"));
            cb.onChangeValue(e -> commit(p, e.newValue == 1 ? "true" : "false"));
            put(cb, EDIT_X, y + 2, 30, 24);
            return y + 30;
        }
        if (p.kind == DsProp.ENUM) {
            ComboBox<String> combo = new ComboBox<String>();
            int i = 0;
            int sel = 0;
            while (i < p.optCount()) {
                combo.addItem(p.optLabel(i));
                if (p.optLabel(i).equals(val)) {
                    sel = i;
                }
                i = i + 1;
            }
            combo.setSelectedIndex(sel);
            combo.onSelectionChanged(e -> commit(p, e.selectedItem));
            put(combo, EDIT_X, y, ew, 28);
            return y + 34;
        }
        if (p.kind == DsProp.TEXT) {
            TextArea ta = new TextArea();
            ta.setText(val);
            ta.onChange(e -> commitLive(p, ta.getText()));
            put(ta, EDIT_X, y, ew, 68);
            return y + 74;
        }
        if (p.kind == DsProp.COLOR) {
            TextField tf = new TextField(val);
            tf.setCueBanner("#RRGGBB");
            DsSwatch sw = new DsSwatch();
            sw.rgb = swatchColor(val);
            tf.onChange(e -> {
                String t = tf.getText();
                if (t.length() == 0 || DsUtil.parseColor(t) >= 0) {
                    sw.rgb = swatchColor(t);
                    sw.repaint();
                    commitLive(p, t);
                }
            });
            sw.onPick = () -> pickColor(p, tf, sw);
            put(tf, EDIT_X, y, ew - 30, 28);
            put(sw, EDIT_X + ew - 24, y + 3, 24, 22);
            return y + 34;
        }
        TextField tf = new TextField(val);
        tf.onChange(e -> textEdited(p, tf.getText()));
        put(tf, EDIT_X, y, ew, 28);
        return y + 34;
    }

    void textEdited(DsProp p, String t) {
        if (p.kind == DsProp.INT) {
            if (t.length() == 0) {
                commitLive(p, p.def);
                return;
            }
            int iv = DsUtil.parseInt(t, -999999);
            if (iv == -999999) {
                return;
            }
            commitLive(p, "" + Math.min(p.max, Math.max(p.min, iv)));
        } else if (p.kind == DsProp.DOUBLE) {
            if (t.length() == 0) {
                commitLive(p, p.def);
                return;
            }
            double d = DsUtil.parseDouble(t, -999999.0);
            if (d == -999999.0) {
                return;
            }
            commitLive(p, DsUtil.fmtDouble(d));
        } else {
            commitLive(p, t);
        }
    }

    int swatchColor(String v) {
        long c = DsUtil.parseColor(v);
        if (c < 0) {
            return -1;
        }
        return (int) ((c / 256L) % 16777216L);
    }

    void pickColor(DsProp p, TextField tf, DsSwatch sw) {
        ContextMenu cm = new ContextMenu();
        String[] names = new String[] { "无", "白", "浅灰", "灰", "深灰", "黑", "红", "橙", "黄", "绿", "青", "蓝", "紫", "粉" };
        String[] vals = new String[] { "", "#FFFFFF", "#F1F2F4", "#9AA1AD", "#4B5563", "#111827", "#D13438", "#F59E0B",
                "#EAB308", "#2E9D4F", "#06B6D4", "#0A64D8", "#8E4EC6", "#EC4899" };
        int i = 0;
        while (i < names.length) {
            String v = vals[i];
            cm.add(new MenuItem(names[i] + (v.length() > 0 ? "  " + v : ""), () -> {
                tf.setText(v);
                sw.rgb = swatchColor(v);
                sw.repaint();
                commit(p, v);
            }));
            i = i + 1;
        }
        cm.showAt(UiNative.cursorScreenX(), UiNative.cursorScreenY());
    }

    int addEventRow(DsEvent ev, int y, int w) {
        int ew = w - EDIT_X - 12;
        label(ev.label, LABEL_X, y + 4, 92, false);
        TextField tf = new TextField(node.handler(ev.name));
        tf.setCueBanner(DsGen.defaultHandler(node, ev, doc));
        tf.onChange(e -> {
            String t = tf.getText();
            if (t.length() == 0 || DsUtil.isIdent(t)) {
                doc.beginChange();
                node.setHandler(ev.name, t);
                doc.changed();
                listener.propsEdited();
            }
        });
        put(tf, EDIT_X, y, ew - 32, 28);
        Button go = new Button("↵");
        go.setToolTip("转到事件处理方法（不存在则创建）");
        go.onClick(() -> {
            String h = node.handler(ev.name);
            if (h.length() == 0) {
                h = DsGen.defaultHandler(node, ev, doc);
                doc.beginChange();
                node.setHandler(ev.name, h);
                doc.changed();
                tf.setText(h);
            }
            listener.propsGotoEvent(node, ev);
        });
        put(go, EDIT_X + ew - 28, y, 28, 28);
        return y + 34;
    }

    void commit(DsProp p, String value) {
        doc.endCoalesce();
        doc.setProp(node, p.name, value);
        listener.propsEdited();
    }

    void commitLive(DsProp p, String value) {
        doc.setPropLive(node, p.name, value);
        listener.propsEdited();
    }

    void renameNode(TextField tf) {
        String t = tf.getText();
        if (t.equals(node.name)) {
            return;
        }
        if (doc.rename(node, t)) {
            tf.setForegroundRgba(Component.rgba(31, 35, 40, 255));
            listener.propsEdited();
        } else {
            tf.setForegroundRgba(Component.rgba(209, 52, 56, 255));
        }
    }
}
