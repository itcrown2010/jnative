import jnative.ui.*;
import jnative.graphics.*;

/**
 * Builds a live component tree from a design document, mirroring exactly what DsGen emits so the
 * designer canvas, the preview window and the generated application look the same.
 *
 * @author 张晓达
 */
public class DsBuild {
    ArrayList<DsNode> nodes = new ArrayList<DsNode>();
    ArrayList<Component> comps = new ArrayList<Component>();
    Component rootComp;
    boolean design; // design mode keeps disabled / hidden controls visible so they can be edited

    DsBuild(boolean designMode) {
        design = designMode;
    }

    Component compOf(DsNode n) {
        int i = 0;
        while (i < nodes.size()) {
            if (nodes.get(i) == n) {
                return comps.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    DsNode nodeOf(Component c) {
        int i = 0;
        while (i < comps.size()) {
            if (comps.get(i) == c) {
                return nodes.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    /** Build the content tree of a form; the result is sized to the form's client area. */
    Component build(DsDoc doc) {
        DsNode form = doc.root;
        if (form.children.size() == 0) {
            return null;
        }
        DsNode content = form.children.get(0);
        Component c = buildNode(content);
        rootComp = c;
        return c;
    }

    /** Put the built tree into `host` at the given client size and run layout. */
    void place(Component root, Container host, int w, int h) {
        host.add(root);
        root.setBounds(0, 0, w, h);
        root.layout();
    }

    static int colorInt(String v) {
        long c = DsUtil.parseColor(v);
        if (c < 0) {
            return 0;
        }
        int r = (int) ((c / 16777216L) % 256);
        int g = (int) ((c / 65536L) % 256);
        int b = (int) ((c / 256L) % 256);
        int a = (int) (c % 256);
        return Component.rgba(r, g, b, a);
    }

    static ArrayList<String> lines(String s) {
        return DsGen.splitLines(s);
    }

    Component create(DsNode n, DsType t) {
        String ty = n.type;
        if (ty.equals("VBox")) {
            return new VBox();
        }
        if (ty.equals("HBox")) {
            return new HBox();
        }
        if (ty.equals("GridPane")) {
            return new GridPane();
        }
        if (ty.equals("StackPane")) {
            return new StackPane();
        }
        if (ty.equals("Panel")) {
            return new Panel();
        }
        if (ty.equals("ScrollView")) {
            return new ScrollView();
        }
        if (ty.equals("SplitPane")) {
            int o = Orientation.HORIZONTAL;
            if (n.get("orientation").equals("Vertical")) {
                o = Orientation.VERTICAL;
            }
            return new SplitPane(o);
        }
        if (ty.equals("TabPane")) {
            return new TabPane();
        }
        if (ty.equals("Label")) {
            return new Label(n.get("text"));
        }
        if (ty.equals("Button")) {
            return new Button(n.get("text"));
        }
        if (ty.equals("TextField")) {
            return new TextField(n.get("text"));
        }
        if (ty.equals("TextArea")) {
            return new TextArea();
        }
        if (ty.equals("CheckBox")) {
            return new CheckBox(n.get("text"));
        }
        if (ty.equals("RadioButton")) {
            return new RadioButton(n.get("text"));
        }
        if (ty.equals("ComboBox")) {
            return new ComboBox<String>();
        }
        if (ty.equals("ListView")) {
            return new ListView<String>();
        }
        if (ty.equals("SegmentedControl")) {
            ArrayList<String> items = lines(n.get("items"));
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < items.size()) {
                if (i > 0) {
                    sb.append('\t');
                }
                sb.append(items.get(i));
                i = i + 1;
            }
            return new SegmentedControl(sb.toString());
        }
        if (ty.equals("ProgressBar")) {
            return new ProgressBar();
        }
        if (ty.equals("SearchField")) {
            return new SearchField();
        }
        if (ty.equals("Canvas")) {
            return new Canvas();
        }
        return new Panel();
    }

    void applyProps(Component c, DsNode n) {
        String ty = n.type;
        // type specific
        if (ty.equals("VBox") || ty.equals("HBox") || ty.equals("GridPane") || ty.equals("StackPane") || ty.equals("Panel")) {
            Container ct = (Container) c;
            if (n.has("spacing") && !ty.equals("StackPane") && !ty.equals("Panel")) {
                ct.setSpacing(n.getInt("spacing"));
            }
            if (n.has("padding")) {
                ct.setPadding(n.getInt("padding"));
            }
        }
        if (ty.equals("GridPane")) {
            GridPane g = (GridPane) c;
            if (n.has("cellWidth")) {
                g.cellW = n.getInt("cellWidth");
            }
            if (n.has("cellHeight")) {
                g.cellH = n.getInt("cellHeight");
            }
        }
        if (ty.equals("SplitPane") && n.has("divider")) {
            SplitPane sp = (SplitPane) c;
            sp.setDividerPosition(n.getDouble("divider"));
        }
        if (ty.equals("TextField")) {
            TextField tf = (TextField) c;
            if (n.has("cueBanner")) {
                tf.setCueBanner(n.get("cueBanner"));
            }
            if (n.has("password")) {
                tf.setPasswordMode(n.getBool("password"));
            }
        }
        if (ty.equals("TextArea")) {
            TextArea ta = (TextArea) c;
            if (n.has("text")) {
                ta.setText(n.get("text"));
            }
            if (n.has("editable")) {
                ta.setEditable(n.getBool("editable"));
            }
            if (n.has("wordWrap")) {
                ta.setWordWrap(n.getBool("wordWrap"));
            }
        }
        if (ty.equals("CheckBox") && n.has("selected")) {
            CheckBox cb = (CheckBox) c;
            cb.setSelected(n.getBool("selected"));
        }
        if (ty.equals("RadioButton") && n.has("selected")) {
            RadioButton rb = (RadioButton) c;
            rb.setSelected(n.getBool("selected"));
        }
        if (ty.equals("ComboBox")) {
            ComboBox<String> combo = (ComboBox<String>) c;
            ArrayList<String> items = lines(n.get("items"));
            int i = 0;
            while (i < items.size()) {
                combo.addItem(items.get(i));
                i = i + 1;
            }
            if (n.has("selectedIndex")) {
                combo.setSelectedIndex(n.getInt("selectedIndex"));
            }
        }
        if (ty.equals("ListView")) {
            ListView<String> lv = (ListView<String>) c;
            lv.setItems(lines(n.get("items")));
        }
        if (ty.equals("SegmentedControl") && n.has("selectedIndex")) {
            SegmentedControl sc = (SegmentedControl) c;
            sc.setSelectedIndex(n.getInt("selectedIndex"));
        }
        if (ty.equals("ProgressBar")) {
            ProgressBar pb = (ProgressBar) c;
            if (n.has("progress")) {
                pb.setProgress(n.getDouble("progress"));
            }
            if (n.has("indeterminate")) {
                pb.setIndeterminate(n.getBool("indeterminate"));
            }
        }
        if (ty.equals("SearchField") && n.has("placeholder")) {
            SearchField sf = (SearchField) c;
            sf.setPlaceholder(n.get("placeholder"));
        }
        // common
        if (!design) {
            if (n.has("enabled")) {
                c.setEnabled(n.getBool("enabled"));
            }
            if (n.has("visible")) {
                c.setVisible(n.getBool("visible"));
            }
        }
        if (n.has("toolTip")) {
            c.setToolTip(n.get("toolTip"));
        }
        if (n.has("hgrow") && n.getBool("hgrow")) {
            c.hgrow = 1;
        }
        if (n.has("vgrow") && n.getBool("vgrow")) {
            c.vgrow = 1;
        }
        if (n.has("background") && n.get("background").length() > 0) {
            c.setBackgroundRgba(colorInt(n.get("background")));
        }
        if (n.has("foreground") && n.get("foreground").length() > 0) {
            c.setForegroundRgba(colorInt(n.get("foreground")));
        }
        if (n.has("cornerRadius")) {
            c.setCornerRadius(n.getInt("cornerRadius"));
        }
        if (n.has("fontSize")) {
            c.setFontSize(n.getInt("fontSize"));
        }
        if (n.has("textAlign")) {
            DsProp p = DsMeta.prop("Label", "textAlign");
            c.setTextAlign(DsUtil.parseInt(p.codeOf(n.get("textAlign")), 0));
        }
    }

    int sizeOr(int v, int fallback) {
        if (v > 0) {
            return v;
        }
        return fallback;
    }

    Component buildNode(DsNode n) {
        DsType t = DsMeta.find(n.type);
        Component c = create(n, t);
        nodes.add(n);
        comps.add(c);
        applyProps(c, n);
        if (t.container != DsType.LEAF) {
            Container host = (Container) c;
            int i = 0;
            while (i < n.children.size()) {
                DsNode ch = n.children.get(i);
                Component cc = buildNode(ch);
                attach(host, t, n, ch, cc, i);
                i = i + 1;
            }
        }
        return c;
    }

    void attach(Container host, DsType pt, DsNode p, DsNode ch, Component cc, int index) {
        int w = ch.getInt("width");
        int h = ch.getInt("height");
        if (pt.container == DsType.ABSOLUTE) {
            host.add(cc);
            cc.setBounds(ch.getInt("x"), ch.getInt("y"), sizeOr(w, cc.prefWidth), sizeOr(h, cc.prefHeight));
            return;
        }
        if (pt.container == DsType.GRID) {
            GridPane g = (GridPane) host;
            g.addAtSpan(cc, ch.getInt("col"), ch.getInt("row"), ch.getInt("colSpan"), ch.getInt("rowSpan"));
            return;
        }
        if (pt.container == DsType.TABS) {
            TabPane tp = (TabPane) host;
            tp.addTab(ch.get("tabTitle"), cc);
            return;
        }
        if (w > 0 || h > 0) {
            cc.setPrefSize(sizeOr(w, cc.prefWidth), sizeOr(h, cc.prefHeight));
        }
        if (pt.container == DsType.SINGLE) {
            ScrollView sv = (ScrollView) host;
            sv.setContent(cc);
        } else if (pt.container == DsType.SPLIT) {
            SplitPane sp = (SplitPane) host;
            if (index == 0) {
                sp.setFirst(cc);
            } else {
                sp.setSecond(cc);
            }
        } else {
            host.add(cc);
        }
    }

    // ---- geometry helpers for the design surface ----

    /** X of the component relative to the build root. */
    int absX(Component c) {
        int sum = 0;
        Component cur = c;
        while (cur != null && cur != rootComp) {
            sum = sum + cur.x;
            cur = parentOf(cur);
        }
        return sum;
    }

    int absY(Component c) {
        int sum = 0;
        Component cur = c;
        while (cur != null && cur != rootComp) {
            sum = sum + cur.y;
            cur = parentOf(cur);
        }
        return sum;
    }

    Component parentOf(Component c) {
        DsNode n = nodeOf(c);
        if (n == null || n.parent == null) {
            return null;
        }
        return compOf(n.parent);
    }

    /** Deepest node under (px,py) in build-root coordinates, or null. */
    DsNode hitTest(DsNode n, int px, int py) {
        Component c = compOf(n);
        if (c == null) {
            return null;
        }
        int ax = absX(c);
        int ay = absY(c);
        if (px < ax || py < ay || px >= ax + c.width || py >= ay + c.height) {
            return null;
        }
        int i = n.children.size() - 1;
        while (i >= 0) {
            DsNode hit = null;
            Component cc = compOf(n.children.get(i));
            if (cc != null && cc.visible != 0) {
                hit = hitTest(n.children.get(i), px, py);
            }
            if (hit != null) {
                return hit;
            }
            i = i - 1;
        }
        return n;
    }
}
