/**
 * Component catalog: describes every component type the designer knows about (palette entry,
 * properties, events, container behaviour). Drives the palette, property inspector, builder and
 * code generator so that adding a component is a single registration here.
 *
 * @author 张晓达
 */
class DsProp {
    static int STR = 0;
    static int INT = 1;
    static int BOOL = 2;
    static int COLOR = 3;
    static int ENUM = 4;
    static int DOUBLE = 5;
    static int TEXT = 6; // multi-line text, one item per line

    String name;
    String label;
    String group;
    int kind;
    String def;
    String opts = ""; // ENUM: "Label=code;Label2=code2"
    String gen = ""; // statement template, $ = variable, {v} = formatted value
    int min = 0;
    int max = 100000;

    DsProp(String n, String l, String g, int k, String d, String genTemplate) {
        name = n;
        label = l;
        group = g;
        kind = k;
        def = d;
        gen = genTemplate;
    }

    DsProp withOpts(String o) {
        opts = o;
        return this;
    }

    DsProp range(int lo, int hi) {
        min = lo;
        max = hi;
        return this;
    }

    int optCount() {
        if (opts.length() == 0) {
            return 0;
        }
        int n = 1;
        int i = 0;
        while (i < opts.length()) {
            if (opts.charAt(i) == ';') {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    String optPair(int idx) {
        int start = 0;
        int k = 0;
        int i = 0;
        while (i <= opts.length()) {
            if (i == opts.length() || opts.charAt(i) == ';') {
                if (k == idx) {
                    return opts.substring(start, i);
                }
                k = k + 1;
                start = i + 1;
            }
            i = i + 1;
        }
        return "";
    }

    String optLabel(int idx) {
        String p = optPair(idx);
        int eq = p.indexOf('=');
        if (eq < 0) {
            return p;
        }
        return p.substring(0, eq);
    }

    String optCode(int idx) {
        String p = optPair(idx);
        int eq = p.indexOf('=');
        if (eq < 0) {
            return p;
        }
        return p.substring(eq + 1);
    }

    /** Stored value of an enum is the label; find its code expression. */
    String codeOf(String label) {
        int i = 0;
        while (i < optCount()) {
            if (optLabel(i).equals(label)) {
                return optCode(i);
            }
            i = i + 1;
        }
        return optCode(0);
    }
}

class DsEvent {
    String name;
    String reg; // registration method, e.g. onClick
    String param; // handler parameter type, "" for none
    String label;

    DsEvent(String n, String l, String r, String p) {
        name = n;
        label = l;
        reg = r;
        param = p;
    }
}

class DsType {
    static int LEAF = 0;
    static int FLOW = 1; // VBox / HBox: ordered children
    static int GRID = 2;
    static int STACK = 3;
    static int ABSOLUTE = 4; // Panel
    static int SINGLE = 5; // ScrollView: one content child
    static int SPLIT = 6; // SplitPane: two children
    static int TABS = 7; // TabPane: children carry tabTitle

    String name;
    String cls;
    String category;
    String glyph;
    int container;
    String ctor; // property used as constructor text argument, "" if none
    String generic = ""; // e.g. "<String>"
    int defW;
    int defH;
    String defaultEvent = "";
    ArrayList<DsProp> props = new ArrayList<DsProp>();
    ArrayList<DsEvent> events = new ArrayList<DsEvent>();

    DsType(String n, String c, String cat, String g, int cont, int w, int h) {
        name = n;
        cls = c;
        category = cat;
        glyph = g;
        container = cont;
        defW = w;
        defH = h;
        ctor = "";
    }

    boolean isContainer() {
        return container != LEAF;
    }

    String declType() {
        return cls + generic;
    }

    DsEvent event(String n) {
        int i = 0;
        while (i < events.size()) {
            if (events.get(i).name.equals(n)) {
                return events.get(i);
            }
            i = i + 1;
        }
        return null;
    }
}

public class DsMeta {
    static ArrayList<DsType> types;
    static ArrayList<DsProp> formProps;
    static ArrayList<DsProp> commonProps;
    static ArrayList<DsProp> attachedGrid;
    static ArrayList<DsProp> attachedAbs;
    static ArrayList<DsProp> attachedTab;
    static DsType formType;

    static String ALIGN = "Left=0;Center=1;Right=2";

    static DsType find(String name) {
        init();
        if (name.equals("Form")) {
            return formType;
        }
        int i = 0;
        while (i < types.size()) {
            if (types.get(i).name.equals(name)) {
                return types.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    static ArrayList<DsProp> propsFor(String type) {
        init();
        DsType t = find(type);
        ArrayList<DsProp> all = new ArrayList<DsProp>();
        if (t == null) {
            return all;
        }
        if (type.equals("Form")) {
            int k = 0;
            while (k < formProps.size()) {
                all.add(formProps.get(k));
                k = k + 1;
            }
            return all;
        }
        int i = 0;
        while (i < t.props.size()) {
            all.add(t.props.get(i));
            i = i + 1;
        }
        i = 0;
        while (i < commonProps.size()) {
            all.add(commonProps.get(i));
            i = i + 1;
        }
        return all;
    }

    static DsProp prop(String type, String name) {
        init();
        DsType t = find(type);
        if (t == null) {
            return null;
        }
        if (type.equals("Form")) {
            return findIn(formProps, name);
        }
        DsProp p = findIn(t.props, name);
        if (p != null) {
            return p;
        }
        p = findIn(commonProps, name);
        if (p != null) {
            return p;
        }
        p = findIn(attachedGrid, name);
        if (p != null) {
            return p;
        }
        p = findIn(attachedAbs, name);
        if (p != null) {
            return p;
        }
        return findIn(attachedTab, name);
    }

    static DsProp findIn(ArrayList<DsProp> list, String name) {
        int i = 0;
        while (i < list.size()) {
            if (list.get(i).name.equals(name)) {
                return list.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    /** Properties contributed by the parent container (grid cell, absolute position, tab title). */
    static ArrayList<DsProp> attachedProps(DsNode n) {
        init();
        if (n.parent == null) {
            return new ArrayList<DsProp>();
        }
        DsType pt = find(n.parent.type);
        if (pt.container == DsType.GRID) {
            return attachedGrid;
        }
        if (pt.container == DsType.ABSOLUTE) {
            return attachedAbs;
        }
        if (pt.container == DsType.TABS) {
            return attachedTab;
        }
        return new ArrayList<DsProp>();
    }

    static String formatValue(DsProp p, String v) {
        if (p.kind == DsProp.STR || p.kind == DsProp.TEXT) {
            return DsUtil.javaString(v);
        }
        if (p.kind == DsProp.COLOR) {
            long c = DsUtil.parseColor(v);
            if (c < 0) {
                return "0";
            }
            int r = (int) ((c / 16777216L) % 256);
            int g = (int) ((c / 65536L) % 256);
            int b = (int) ((c / 256L) % 256);
            int a = (int) (c % 256);
            return "Component.rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
        }
        if (p.kind == DsProp.ENUM) {
            return p.codeOf(v);
        }
        if (p.kind == DsProp.DOUBLE) {
            return DsUtil.fmtDouble(DsUtil.parseDouble(v, 0));
        }
        if (p.kind == DsProp.BOOL) {
            return v.equals("true") ? "true" : "false";
        }
        return "" + DsUtil.parseInt(v, 0);
    }

    /** Statement for one property, empty when the template is blank. */
    static String emit(DsProp p, String var, String value) {
        if (p.gen.length() == 0) {
            return "";
        }
        String t = p.gen;
        String fv = formatValue(p, value);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '$') {
                sb.append(var);
            } else if (c == '{' && DsUtil.startsAt(t, "{v}", i)) {
                sb.append(fv);
                i = i + 2;
            } else {
                sb.append(c);
            }
            i = i + 1;
        }
        return sb.toString();
    }

    static DsType add(DsType t) {
        types.add(t);
        return t;
    }

    static DsProp text(String def) {
        return new DsProp("text", "文本", "外观", DsProp.STR, def, "");
    }

    static void init() {
        if (types != null) {
            return;
        }
        types = new ArrayList<DsType>();
        formProps = new ArrayList<DsProp>();
        commonProps = new ArrayList<DsProp>();
        attachedGrid = new ArrayList<DsProp>();
        attachedAbs = new ArrayList<DsProp>();
        attachedTab = new ArrayList<DsProp>();

        formType = new DsType("Form", "Window", "", "▭", DsType.SINGLE, 640, 480);
        formProps.add(new DsProp("title", "标题", "窗口", DsProp.STR, "Form", ""));
        formProps.add(new DsProp("width", "宽度", "窗口", DsProp.INT, "640", "").range(120, 4000));
        formProps.add(new DsProp("height", "高度", "窗口", DsProp.INT, "480", "").range(80, 4000));
        formType.events.add(new DsEvent("close", "关闭", "onClose", ""));
        formType.defaultEvent = "close";

        commonProps.add(new DsProp("enabled", "启用", "行为", DsProp.BOOL, "true", "$.setEnabled({v});"));
        commonProps.add(new DsProp("visible", "可见", "行为", DsProp.BOOL, "true", "$.setVisible({v});"));
        commonProps.add(new DsProp("toolTip", "提示文字", "行为", DsProp.STR, "", "$.setToolTip({v});"));
        commonProps.add(new DsProp("width", "宽度", "布局", DsProp.INT, "0", "").range(0, 4000));
        commonProps.add(new DsProp("height", "高度", "布局", DsProp.INT, "0", "").range(0, 4000));
        commonProps.add(new DsProp("hgrow", "水平拉伸", "布局", DsProp.BOOL, "false", "$.hgrow = 1;"));
        commonProps.add(new DsProp("vgrow", "垂直拉伸", "布局", DsProp.BOOL, "false", "$.vgrow = 1;"));
        commonProps.add(new DsProp("background", "背景色", "外观", DsProp.COLOR, "", "$.setBackgroundRgba({v});"));
        commonProps.add(new DsProp("foreground", "前景色", "外观", DsProp.COLOR, "", "$.setForegroundRgba({v});"));
        commonProps.add(new DsProp("cornerRadius", "圆角", "外观", DsProp.INT, "0", "$.setCornerRadius({v});").range(0, 100));
        commonProps.add(new DsProp("fontSize", "字号", "外观", DsProp.INT, "0", "$.setFontSize({v});").range(0, 96));
        commonProps.add(new DsProp("textAlign", "对齐", "外观", DsProp.ENUM, "Left", "$.setTextAlign({v});").withOpts(ALIGN));

        attachedGrid.add(new DsProp("col", "列", "网格", DsProp.INT, "0", "").range(0, 64));
        attachedGrid.add(new DsProp("row", "行", "网格", DsProp.INT, "0", "").range(0, 64));
        attachedGrid.add(new DsProp("colSpan", "跨列", "网格", DsProp.INT, "1", "").range(1, 64));
        attachedGrid.add(new DsProp("rowSpan", "跨行", "网格", DsProp.INT, "1", "").range(1, 64));
        attachedAbs.add(new DsProp("x", "X", "位置", DsProp.INT, "0", "").range(-4000, 4000));
        attachedAbs.add(new DsProp("y", "Y", "位置", DsProp.INT, "0", "").range(-4000, 4000));
        attachedTab.add(new DsProp("tabTitle", "页签标题", "页签", DsProp.STR, "Tab", ""));

        // ---- Containers ----
        DsType vbox = add(new DsType("VBox", "VBox", "布局", "☰", DsType.FLOW, 240, 160));
        vbox.props.add(new DsProp("spacing", "间距", "布局", DsProp.INT, "8", "$.setSpacing({v});").range(0, 200));
        vbox.props.add(new DsProp("padding", "内边距", "布局", DsProp.INT, "12", "$.setPadding({v});").range(0, 200));

        DsType hbox = add(new DsType("HBox", "HBox", "布局", "⫴", DsType.FLOW, 240, 60));
        hbox.props.add(new DsProp("spacing", "间距", "布局", DsProp.INT, "8", "$.setSpacing({v});").range(0, 200));
        hbox.props.add(new DsProp("padding", "内边距", "布局", DsProp.INT, "12", "$.setPadding({v});").range(0, 200));

        DsType grid = add(new DsType("GridPane", "GridPane", "布局", "▦", DsType.GRID, 300, 160));
        grid.props.add(new DsProp("spacing", "间距", "布局", DsProp.INT, "8", "$.setSpacing({v});").range(0, 200));
        grid.props.add(new DsProp("padding", "内边距", "布局", DsProp.INT, "12", "$.setPadding({v});").range(0, 200));
        grid.props.add(new DsProp("cellWidth", "单元宽", "布局", DsProp.INT, "120", "$.cellW = {v};").range(10, 1000));
        grid.props.add(new DsProp("cellHeight", "单元高", "布局", DsProp.INT, "32", "$.cellH = {v};").range(10, 1000));

        DsType stack = add(new DsType("StackPane", "StackPane", "布局", "❏", DsType.STACK, 240, 160));
        stack.props.add(new DsProp("padding", "内边距", "布局", DsProp.INT, "12", "$.setPadding({v});").range(0, 200));

        DsType panel = add(new DsType("Panel", "Panel", "布局", "□", DsType.ABSOLUTE, 300, 200));
        panel.props.add(new DsProp("padding", "内边距", "布局", DsProp.INT, "12", "$.setPadding({v});").range(0, 200));

        add(new DsType("ScrollView", "ScrollView", "布局", "⇕", DsType.SINGLE, 300, 200));

        DsType split = add(new DsType("SplitPane", "SplitPane", "布局", "◫", DsType.SPLIT, 400, 240));
        split.props.add(new DsProp("orientation", "方向", "布局", DsProp.ENUM, "Horizontal", "")
                .withOpts("Horizontal=Orientation.HORIZONTAL;Vertical=Orientation.VERTICAL"));
        split.props.add(new DsProp("divider", "分隔位置", "布局", DsProp.DOUBLE, "0.5", "$.setDividerPosition({v});"));

        add(new DsType("TabPane", "TabPane", "布局", "⊟", DsType.TABS, 360, 240));

        // ---- Basic controls ----
        DsType label = add(new DsType("Label", "Label", "基本控件", "A", DsType.LEAF, 200, 24));
        label.ctor = "text";
        label.props.add(text("Label"));
        label.events.add(new DsEvent("click", "单击", "onClick", ""));
        label.defaultEvent = "click";

        DsType button = add(new DsType("Button", "Button", "基本控件", "▢", DsType.LEAF, 96, 28));
        button.ctor = "text";
        button.props.add(text("Button"));
        button.events.add(new DsEvent("click", "单击", "onClick", ""));
        button.events.add(new DsEvent("keyDown", "按键", "onKeyDown", "KeyEvent"));
        button.defaultEvent = "click";

        DsType tf = add(new DsType("TextField", "TextField", "基本控件", "I", DsType.LEAF, 180, 28));
        tf.ctor = "text";
        tf.props.add(text(""));
        tf.props.add(new DsProp("cueBanner", "占位提示", "外观", DsProp.STR, "", "$.setCueBanner({v});"));
        tf.props.add(new DsProp("password", "密码模式", "行为", DsProp.BOOL, "false", "$.setPasswordMode({v});"));
        tf.events.add(new DsEvent("change", "内容变化", "onChange", "UiEvent"));
        tf.events.add(new DsEvent("keyDown", "按键", "onKeyDown", "KeyEvent"));
        tf.defaultEvent = "change";

        DsType ta = add(new DsType("TextArea", "TextArea", "基本控件", "¶", DsType.LEAF, 240, 100));
        ta.props.add(new DsProp("text", "文本", "外观", DsProp.TEXT, "", "$.setText({v});"));
        ta.props.add(new DsProp("editable", "可编辑", "行为", DsProp.BOOL, "true", "$.setEditable({v});"));
        ta.props.add(new DsProp("wordWrap", "自动换行", "行为", DsProp.BOOL, "false", "$.setWordWrap({v});"));
        ta.events.add(new DsEvent("change", "内容变化", "onChange", "UiEvent"));
        ta.defaultEvent = "change";

        DsType cb = add(new DsType("CheckBox", "CheckBox", "基本控件", "☑", DsType.LEAF, 160, 28));
        cb.ctor = "text";
        cb.props.add(text("CheckBox"));
        cb.props.add(new DsProp("selected", "选中", "行为", DsProp.BOOL, "false", "$.setSelected({v});"));
        cb.events.add(new DsEvent("toggle", "选中变化", "onChangeValue", "ValueChangeEvent<int>"));
        cb.events.add(new DsEvent("click", "单击", "onClick", ""));
        cb.defaultEvent = "toggle";

        DsType rb = add(new DsType("RadioButton", "RadioButton", "基本控件", "◉", DsType.LEAF, 160, 28));
        rb.ctor = "text";
        rb.props.add(text("RadioButton"));
        rb.props.add(new DsProp("selected", "选中", "行为", DsProp.BOOL, "false", "$.setSelected({v});"));
        rb.events.add(new DsEvent("toggle", "选中变化", "onChangeValue", "ValueChangeEvent<int>"));
        rb.events.add(new DsEvent("click", "单击", "onClick", ""));
        rb.defaultEvent = "toggle";

        DsType combo = add(new DsType("ComboBox", "ComboBox", "基本控件", "▾", DsType.LEAF, 180, 28));
        combo.generic = "<String>";
        combo.props.add(new DsProp("items", "选项", "数据", DsProp.TEXT, "Item 1\nItem 2\nItem 3", ""));
        combo.props.add(new DsProp("selectedIndex", "选中项", "数据", DsProp.INT, "0", "$.setSelectedIndex({v});").range(-1, 1000));
        combo.events.add(new DsEvent("select", "选择变化", "onSelectionChanged", "SelectionEvent<String>"));
        combo.defaultEvent = "select";

        DsType list = add(new DsType("ListView", "ListView", "基本控件", "≣", DsType.LEAF, 200, 160));
        list.generic = "<String>";
        list.props.add(new DsProp("items", "选项", "数据", DsProp.TEXT, "Item 1\nItem 2\nItem 3", ""));
        list.events.add(new DsEvent("select", "选择变化", "onSelectionChanged", "SelectionEvent<String>"));
        list.defaultEvent = "select";

        DsType seg = add(new DsType("SegmentedControl", "SegmentedControl", "基本控件", "◧", DsType.LEAF, 160, 28));
        seg.props.add(new DsProp("items", "分段", "数据", DsProp.TEXT, "A\nB", ""));
        seg.props.add(new DsProp("selectedIndex", "选中项", "数据", DsProp.INT, "0", "$.setSelectedIndex({v});").range(0, 20));
        seg.events.add(new DsEvent("select", "选择变化", "onSelectionChanged", "SelectionEvent<int>"));
        seg.defaultEvent = "select";

        DsType pb = add(new DsType("ProgressBar", "ProgressBar", "基本控件", "▰", DsType.LEAF, 200, 20));
        pb.props.add(new DsProp("progress", "进度(0~1)", "数据", DsProp.DOUBLE, "0.0", "$.setProgress({v});"));
        pb.props.add(new DsProp("indeterminate", "不确定", "数据", DsProp.BOOL, "false", "$.setIndeterminate({v});"));

        DsType sf = add(new DsType("SearchField", "SearchField", "基本控件", "⌕", DsType.LEAF, 220, 28));
        sf.props.add(new DsProp("placeholder", "占位提示", "外观", DsProp.STR, "", "$.setPlaceholder({v});"));

        DsType cv = add(new DsType("Canvas", "Canvas", "基本控件", "◩", DsType.LEAF, 300, 200));
        cv.events.add(new DsEvent("paint", "绘制", "onPaint", "Graphics"));
        cv.events.add(new DsEvent("mouseDown", "鼠标按下", "onMouseDown", "MouseEvent"));
        cv.events.add(new DsEvent("mouseUp", "鼠标松开", "onMouseUp", "MouseEvent"));
        cv.events.add(new DsEvent("mouseMove", "鼠标移动", "onMouseMove", "MouseEvent"));
        cv.events.add(new DsEvent("keyDown", "按键", "onKeyDown", "KeyEvent"));
        cv.defaultEvent = "paint";
    }
}
