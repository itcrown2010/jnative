import jnative.ui.*;
import jnative.graphics.*;

interface DsGoto {
    void go(DsDoc doc, DsNode n, DsEvent ev);
}

/**
 * The UI designer for one .jnui document: toolbox, design surface, outline, property inspector,
 * device preview, live preview window and event-to-code navigation.
 *
 * @author 张晓达
 */
public class DsDesigner extends Panel implements DsSurfaceListener, DsPaletteListener, DsPropsListener {
    static int TOOLBAR_H = 40;
    static int LEFT_W = 200;
    static int RIGHT_W = 310;
    static int OUTLINE_H = 190;

    DsDoc doc;
    DsPreview preview;
    Runnable onChanged;
    DsGoto onGoto;

    Canvas chrome;
    Panel toolbar;
    TextField search;
    DsPalette palette;
    DsSurface surface;
    IdeTree outline;
    DsProps props;

    Button undoBtn;
    Button redoBtn;
    Button deleteBtn;
    Button dupBtn;
    ComboBox<String> deviceBox;
    CheckBox guidesBox;
    Button previewBtn;
    Button codeBtn;
    Label status;

    String[] deviceNames = new String[] { "桌面 · 窗体尺寸", "笔记本 1280×800", "平板 820×1180", "手机 390×844", "小窗 320×480" };
    int[] deviceW = new int[] { 0, 1280, 820, 390, 320 };
    int[] deviceH = new int[] { 0, 800, 1180, 844, 480 };
    int deviceIndex = 0;

    DsDesigner(DsDoc d) {
        doc = d;
        preview = new DsPreview();
        chrome = new Canvas();
        chrome.setHiDpi(true);
        chrome.onPaint(g -> paintChrome(g));
        add(chrome);

        toolbar = new Panel();
        add(toolbar);
        undoBtn = tbButton("撤销", () -> undo());
        redoBtn = tbButton("重做", () -> redo());
        deleteBtn = tbButton("删除", () -> surface.deleteSelected());
        dupBtn = tbButton("复制", () -> duplicateSelected());
        deviceBox = new ComboBox<String>();
        int i = 0;
        while (i < deviceNames.length) {
            deviceBox.addItem(deviceNames[i]);
            i = i + 1;
        }
        deviceBox.setSelectedIndex(0);
        deviceBox.onSelectionChanged(e -> setDeviceIndex(e.selectedIndex));
        toolbar.add(deviceBox);
        guidesBox = new CheckBox("辅助线");
        guidesBox.setSelected(true);
        guidesBox.onChangeValue(e -> {
            surface.showGuides = e.newValue == 1;
            surface.showGrid = e.newValue == 1;
            surface.redraw();
        });
        toolbar.add(guidesBox);
        previewBtn = tbButton("实时预览", () -> togglePreview());
        codeBtn = tbButton("转到代码", () -> gotoFormCode());
        status = new Label("");
        status.setForegroundRgba(Component.rgba(107, 114, 128, 255));
        toolbar.add(status);

        search = new TextField("");
        search.setCueBanner("搜索控件…");
        search.onChange(e -> palette.setFilter(search.getText()));
        add(search);
        palette = new DsPalette();
        palette.listener = this;
        add(palette);

        surface = new DsSurface(doc);
        surface.listener = this;
        add(surface);

        outline = new IdeTree();
        outline.onSelect = it -> outlineSelect(it);
        add(outline);
        props = new DsProps();
        props.listener = this;
        add(props);

        refreshOutline();
        selectNode(doc.selected() == null ? doc.root : doc.selected());
        updateButtons();
    }

    Button tbButton(String text, Runnable r) {
        Button b = new Button(text);
        b.onClick(r);
        toolbar.add(b);
        return b;
    }

    public void layout() {
        super.layout();
        if (chrome == null || props == null) {
            return;
        }
        chrome.setBounds(0, 0, width, height);
        toolbar.setBounds(0, 0, width, TOOLBAR_H);
        int x = 10;
        x = placeBtn(undoBtn, x, 56);
        x = placeBtn(redoBtn, x, 56);
        x = placeBtn(deleteBtn, x + 8, 56);
        x = placeBtn(dupBtn, x, 56);
        deviceBox.setBounds(x + 12, 6, 170, 28);
        x = x + 12 + 170 + 12;
        guidesBox.setBounds(x, 8, 80, 24);
        x = x + 88;
        x = placeBtn(previewBtn, x, 84);
        x = placeBtn(codeBtn, x, 84);
        status.setBounds(x + 12, 10, Math.max(60, width - x - 24), 20);
        int bodyH = height - TOOLBAR_H;
        search.setBounds(8, TOOLBAR_H + 8, LEFT_W - 16, 28);
        palette.setBounds(0, TOOLBAR_H + 44, LEFT_W, bodyH - 44);
        int sx = LEFT_W + 1;
        int sw = Math.max(100, width - LEFT_W - RIGHT_W - 2);
        surface.setBounds(sx, TOOLBAR_H, sw, bodyH);
        int rx = width - RIGHT_W;
        outline.setBounds(rx, TOOLBAR_H + 28, RIGHT_W, OUTLINE_H);
        props.setBounds(rx, TOOLBAR_H + 28 + OUTLINE_H + 30, RIGHT_W, Math.max(60, bodyH - 28 - OUTLINE_H - 30));
        chrome.repaint();
    }

    int placeBtn(Button b, int x, int w) {
        b.setBounds(x, 6, w, 28);
        return x + w + 6;
    }

    void paintChrome(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.PANEL, 0, 0, width, height);
        Ide.fill(g, Ide.TOOLBAR, 0, 0, width, TOOLBAR_H);
        Ide.line(g, Ide.BORDER, 0, TOOLBAR_H - 0.5, width, TOOLBAR_H - 0.5);
        Ide.line(g, Ide.BORDER, LEFT_W + 0.5, TOOLBAR_H, LEFT_W + 0.5, height);
        int rx = width - RIGHT_W;
        Ide.line(g, Ide.BORDER, rx - 0.5, TOOLBAR_H, rx - 0.5, height);
        Ide.fill(g, Ide.PANEL_ALT, rx, TOOLBAR_H, RIGHT_W, 28);
        Ide.textRow(g, "文档大纲", rx + 12, TOOLBAR_H, 28, Ide.bold(), Ide.SUBTLE);
        Ide.line(g, Ide.BORDER, rx, TOOLBAR_H + 28 + OUTLINE_H + 0.5, width, TOOLBAR_H + 28 + OUTLINE_H + 0.5);
        Ide.fill(g, Ide.PANEL_ALT, rx, TOOLBAR_H + 28 + OUTLINE_H + 1, RIGHT_W, 29);
        Ide.textRow(g, "属性 / 事件", rx + 12, TOOLBAR_H + 28 + OUTLINE_H + 1, 29, Ide.bold(), Ide.SUBTLE);
        Ide.line(g, Ide.BORDER, rx, TOOLBAR_H + 28 + 0.5, width, TOOLBAR_H + 28 + 0.5);
    }

    // ------------------------------------------------------------------ commands

    void setDocument(DsDoc d) {
        doc = d;
        surface.setDocument(d);
        if (preview.isOpen()) {
            preview.setDocument(d);
        }
        refreshOutline();
        selectNode(d.selected() == null ? d.root : d.selected());
        updateButtons();
    }

    void undo() {
        if (doc.undo()) {
            afterStructural();
        }
    }

    void redo() {
        if (doc.redo()) {
            afterStructural();
        }
    }

    void duplicateSelected() {
        DsNode n = doc.selected();
        if (n == null) {
            return;
        }
        DsNode c = doc.duplicate(n);
        if (c != null) {
            afterStructural();
        }
    }

    void setDeviceIndex(int idx) {
        deviceIndex = idx;
        surface.setDevice(deviceW[idx], deviceH[idx]);
        if (preview.isOpen()) {
            preview.setDevice(deviceW[idx], deviceH[idx]);
        }
        refreshStatus();
    }

    void togglePreview() {
        if (preview.isOpen()) {
            preview.close();
            previewBtn.setText("实时预览");
            return;
        }
        preview.onClosed = () -> previewBtn.setText("实时预览");
        preview.open(doc, deviceW[deviceIndex], deviceH[deviceIndex]);
        previewBtn.setText("关闭预览");
    }

    void gotoFormCode() {
        if (onGoto != null) {
            onGoto.go(doc, null, null);
        }
    }

    /** Document changed structurally (add/delete/move/undo): refresh everything. */
    void afterStructural() {
        surface.rebuild();
        refreshOutline();
        DsNode sel = doc.selected();
        if (sel == null) {
            sel = doc.root;
            doc.select(sel);
        }
        props.setNode(doc, sel);
        changed();
    }

    void changed() {
        if (preview.isOpen()) {
            preview.refresh();
        }
        updateButtons();
        refreshStatus();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    void updateButtons() {
        undoBtn.setEnabled(doc.canUndo());
        redoBtn.setEnabled(doc.canRedo());
        DsNode sel = doc.selected();
        boolean deletable = sel != null && sel != doc.root && sel != doc.content();
        deleteBtn.setEnabled(deletable);
        dupBtn.setEnabled(sel != null && sel != doc.root && sel != doc.content());
    }

    void refreshStatus() {
        DsNode sel = doc.selected();
        String s = doc.className + ".jnui";
        if (sel != null) {
            s = s + "   选中: " + sel.name + " (" + sel.type + ")";
        }
        status.setText(s);
    }

    void selectNode(DsNode n) {
        doc.select(n);
        IdeItem it = findItem(outline.root, n);
        outline.selected = it;
        outline.reveal(it);
        outline.repaint();
        props.setNode(doc, n);
        surface.redraw();
        updateButtons();
        refreshStatus();
    }

    // ------------------------------------------------------------------ outline

    void refreshOutline() {
        IdeItem root = new IdeItem("", "", "");
        IdeItem form = new IdeItem(doc.root.name, "form", doc.root.name);
        form.hint = "Form";
        form.node = doc.root;
        form.expanded = true;
        root.add(form);
        int i = 0;
        while (i < doc.root.children.size()) {
            addOutline(form, doc.root.children.get(i));
            i = i + 1;
        }
        outline.setRoot(root);
        DsNode sel = doc.selected();
        if (sel != null) {
            IdeItem it = findItem(root, sel);
            outline.selected = it;
            outline.reveal(it);
        }
        outline.repaint();
    }

    void addOutline(IdeItem parent, DsNode n) {
        IdeItem it = new IdeItem(n.name, "comp", n.name);
        it.hint = n.type;
        it.node = n;
        it.expanded = true;
        parent.add(it);
        int i = 0;
        while (i < n.children.size()) {
            addOutline(it, n.children.get(i));
            i = i + 1;
        }
    }

    IdeItem findItem(IdeItem n, DsNode target) {
        if (n.node == target) {
            return n;
        }
        int i = 0;
        while (i < n.kids.size()) {
            IdeItem r = findItem(n.kids.get(i), target);
            if (r != null) {
                return r;
            }
            i = i + 1;
        }
        return null;
    }

    void outlineSelect(IdeItem it) {
        if (it.node == null) {
            return;
        }
        doc.select(it.node);
        props.setNode(doc, it.node);
        surface.redraw();
        updateButtons();
        refreshStatus();
    }

    // ------------------------------------------------------------------ DsSurfaceListener

    public void surfaceSelect(DsNode n) {
        IdeItem it = findItem(outline.root, n);
        outline.selected = it;
        outline.reveal(it);
        outline.repaint();
        props.setNode(doc, n);
        updateButtons();
        refreshStatus();
    }

    public void surfaceEdited() {
        afterStructural();
    }

    public void surfaceDefaultEvent(DsNode n) {
        gotoEvent(n, null);
    }

    public void surfaceStatus(String text) {
        status.setText(text);
    }

    // ------------------------------------------------------------------ DsPaletteListener

    public void paletteArm(String type) {
        surface.arm(type);
    }

    public void paletteAdd(String type) {
        palette.clearArmed();
        surface.arm("");
        surface.addToSelection(type);
    }

    public void paletteDrag(String type, int wx, int wy) {
        palette.clearArmed();
        surface.armedType = "";
        surface.externalDrag(type, wx - Ide.windowX(surface.glass), wy - Ide.windowY(surface.glass));
    }

    public void paletteDrop(String type, int wx, int wy) {
        int sx = wx - Ide.windowX(surface.glass);
        int sy = wy - Ide.windowY(surface.glass);
        if (sx >= 0 && sy >= 0 && sx < surface.width && sy < surface.height) {
            surface.externalDrop(type, sx, sy);
        } else {
            surface.externalCancel();
        }
    }

    public void paletteCancel() {
        surface.externalCancel();
    }

    // ------------------------------------------------------------------ DsPropsListener

    public void propsEdited() {
        surface.rebuild();
        refreshOutline();
        changed();
    }

    public void propsGotoEvent(DsNode n, DsEvent ev) {
        gotoEvent(n, ev);
    }

    void gotoEvent(DsNode n, DsEvent ev) {
        DsType t = DsMeta.find(n.type);
        DsEvent e = ev;
        if (e == null) {
            if (t.defaultEvent.length() == 0) {
                return;
            }
            e = t.event(t.defaultEvent);
        }
        if (n.handler(e.name).length() == 0) {
            doc.beginChange();
            n.setHandler(e.name, DsGen.defaultHandler(n, e, doc));
            doc.changed();
            props.setNode(doc, n);
            changed();
        }
        if (onGoto != null) {
            onGoto.go(doc, n, e);
        }
    }

    public void dispose() {
        preview.close();
        super.dispose();
    }
}
