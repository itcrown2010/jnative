import jnative.ui.*;
import jnative.graphics.*;
import jnative.io.*;

/** 两个整数的回调（对话框确定时使用）。 */
interface IntPair {
    void apply(int a, int b);
}

/**
 * macOS 画图：界面骨架（工具箱 / 画布 / 调色板 / 状态栏）由 MainForm.jnui 在 JNative Studio 设计器里设计，
 * 生成到 MainFormBase；这里接上绘图逻辑、菜单、文件读写和各种对话框。
 */
public class MainForm extends MainFormBase {
    static String APP = "画图";

    Doc doc;
    Painter painter;
    BoardView view;
    Toolbox tools;
    Palette pal;
    UiTimer ticker;

    String path = "";
    int hoverDX = -1;
    int hoverDY = -1;
    MenuItem miGrid;

    MainForm() {
        buildUi();
        doc = new Doc(800, 600);
        painter = new Painter(doc);
        view = new BoardView(board, painter);
        tools = new Toolbox(toolbox, painter);
        pal = new Palette(palette, painter);
        loadSettings();
        window.setMinSize(820, 560);
        board.onMouseDown(e -> boardDown(e));
        board.onMouseMove(e -> boardMove(e));
        board.onMouseUp(e -> boardUp(e));
        board.onMouseWheel(e -> boardWheel(e));
        board.onMouseExit(e -> boardExit());
        tools.onChange = () -> refresh();
        tools.onHint = () -> showHint();
        pal.onChange = () -> refreshColors();
        pal.onEdit = () -> editColorRequest();
        buildMenus();
        window.onClose(() -> {
            if (!confirmDiscard()) {
                window.vetoClose();
            }
        });
        Application.setQuitGuard(() -> confirmDiscard());
        window.onKeyDown(e -> onKey(e));
        ticker = new UiTimer(60, () -> tick());
        updateTitle();
        refresh();
    }

    // ------------------------------------------------------------ 画布事件

    void boardDown(MouseEvent e) {
        if (view.barDown(e.x, e.y)) {
            return;
        }
        if (e.button == 2) {
            return;
        }
        int dx = view.docX(e.x);
        int dy = view.docY(e.y);
        if (painter.tool == Painter.ZOOM) {
            view.zoomStep(e.button == 3 ? -1 : 1, e.x, e.y);
            refresh();
            return;
        }
        painter.down(dx, dy, e.button, e.shiftDown, e.altDown, e.clickCount);
        afterGesture();
    }

    void boardMove(MouseEvent e) {
        if (view.barMove(e.x, e.y)) {
            return;
        }
        int dx = view.docX(e.x);
        int dy = view.docY(e.y);
        hoverDX = dx;
        hoverDY = dy;
        view.hovering = true;
        view.hoverX = dx;
        view.hoverY = dy;
        painter.move(dx, dy, e.shiftDown);
        board.repaint();
        showPos();
        if (painter.active) {
            refreshStatus();
        }
    }

    void boardUp(MouseEvent e) {
        if (view.barUp()) {
            return;
        }
        if (e.button == 2) {
            return;
        }
        painter.up(view.docX(e.x), view.docY(e.y), e.shiftDown);
        afterGesture();
    }

    void boardWheel(MouseEvent e) {
        view.wheel(e);
        refreshStatus();
    }

    void boardExit() {
        view.hovering = false;
        hoverDX = -1;
        board.repaint();
        showPos();
    }

    /** 手势处理完的善后：取色结果、文字请求、标题、状态栏。 */
    void afterGesture() {
        if (painter.picked) {
            painter.picked = false;
            if (painter.pickedButton == 3) {
                painter.bg = painter.pickedColor;
            } else {
                painter.fg = painter.pickedColor;
            }
            painter.tool = painter.prevTool;
            tools.canvas.repaint();
            pal.canvas.repaint();
        }
        if (painter.wantText) {
            painter.wantText = false;
            textDialog(painter.textX, painter.textY);
        }
        board.repaint();
        updateTitle();
        refreshStatus();
    }

    void tick() {
        if (painter.active && painter.tool == Painter.AIRBRUSH) {
            painter.sprayTick();
            board.repaint();
        }
    }

    void onKey(KeyEvent e) {
        int k = e.keyCode;
        if (k == KeyCode.ESCAPE) {
            if (painter.pn > 0 || painter.curveStage > 0 || painter.active) {
                painter.cancelGesture();
            } else {
                painter.commitSelection();
            }
            board.repaint();
            refreshStatus();
            e.consume();
        } else if (k == KeyCode.ENTER) {
            if (painter.pn > 0 || painter.curveStage > 0) {
                painter.endEdits();
                afterGesture();
                e.consume();
            }
        } else if ((k == KeyCode.DELETE || k == KeyCode.BACKSPACE) && painter.selActive) {
            painter.deleteSelection();
            afterGesture();
            e.consume();
        } else if (painter.selActive && (k == KeyCode.LEFT || k == KeyCode.RIGHT || k == KeyCode.UP || k == KeyCode.DOWN)) {
            int step = e.shiftDown != 0 ? 10 : 1;
            int dx = k == KeyCode.LEFT ? -step : (k == KeyCode.RIGHT ? step : 0);
            int dy = k == KeyCode.UP ? -step : (k == KeyCode.DOWN ? step : 0);
            painter.nudge(dx, dy);
            afterGesture();
            e.consume();
        }
    }

    // ------------------------------------------------------------ 状态

    void showPos() {
        if (hoverDX >= 0 && hoverDY >= 0 && hoverDX < doc.w && hoverDY < doc.h) {
            lblPos.setText(hoverDX + ", " + hoverDY + " 像素");
        } else {
            lblPos.setText("");
        }
    }

    void showHint() {
        if (tools.hintText.length() > 0) {
            lblHint.setText(tools.hintText);
        } else {
            lblHint.setText(Painter.toolName(painter.tool) + "：" + Painter.toolHint(painter.tool));
        }
    }

    void refreshColors() {
        palette.repaint();
        if (pal.hoverValid) {
            lblHint.setText("颜色 " + Pix.hex(pal.hoverColor) + "（左键设为前景色，右键设为背景色，双击自定义格编辑）");
        } else {
            showHint();
        }
    }

    void refreshStatus() {
        lblSize.setText(doc.w + " × " + doc.h + " 像素");
        if (painter.selActive) {
            lblSel.setText("选区 " + painter.selW + " × " + painter.selH);
        } else {
            lblSel.setText("");
        }
        lblZoom.setText(view.zoom + "%");
        showHint();
    }

    void refresh() {
        view.clampScroll();
        board.repaint();
        toolbox.repaint();
        palette.repaint();
        refreshStatus();
        updateTitle();
    }

    String displayName() {
        if (path.length() == 0) {
            return "未命名";
        }
        return baseName(path);
    }

    static String baseName(String p) {
        int i = p.lastIndexOf("/");
        if (i < 0) {
            return p;
        }
        return p.substring(i + 1);
    }

    String titleText = "";

    void updateTitle() {
        titleText = displayName() + (painter.modified ? " — 已编辑" : "") + " — " + APP;
        window.setTitle(titleText);
    }

    // ------------------------------------------------------------ 菜单

    MenuItem item(Menu m, String text, int key, int mods, Runnable r) {
        MenuItem mi = new MenuItem(text, r);
        if (key > 0) {
            KeyCombination kc = KeyCombination.of(key).withPrimary();
            if (mods == 1) {
                kc = kc.withShift();
            } else if (mods == 2) {
                kc = kc.withAlt();
            }
            mi.setShortcut(kc);
        }
        m.add(mi);
        return mi;
    }

    void buildMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        item(file, "新建…", 78, 0, () -> newDialog());
        item(file, "打开…", 79, 0, () -> doOpen());
        file.addSeparator();
        item(file, "存储", 83, 0, () -> doSave());
        item(file, "存储为…", 83, 1, () -> doSaveAs());
        file.addSeparator();
        item(file, "在 Finder 中显示", 0, 0, () -> revealInFinder());
        bar.add(file);

        Menu edit = new Menu("编辑");
        item(edit, "撤销", 90, 0, () -> doUndo());
        item(edit, "重做", 90, 1, () -> doRedo());
        edit.addSeparator();
        item(edit, "剪切", 88, 0, () -> doCut());
        item(edit, "拷贝", 67, 0, () -> doCopy());
        item(edit, "粘贴", 86, 0, () -> doPaste());
        item(edit, "清除选区", 0, 0, () -> doDelete());
        item(edit, "全选", 65, 0, () -> doSelectAll());
        edit.addSeparator();
        item(edit, "交换前景色和背景色", 88, 2, () -> swapColors());
        item(edit, "编辑前景色…", 69, 0, () -> editColor(1, 0));
        bar.add(edit);

        Menu image = new Menu("图像");
        item(image, "裁剪到选区", 0, 0, () -> doCrop());
        item(image, "画布大小…", 69, 2, () -> canvasSizeDialog());
        item(image, "缩放图像…", 82, 2, () -> scaleDialog());
        image.addSeparator();
        item(image, "水平翻转", 0, 0, () -> imageOp(0));
        item(image, "垂直翻转", 0, 0, () -> imageOp(1));
        item(image, "顺时针旋转 90°", 82, 0, () -> imageOp(2));
        item(image, "逆时针旋转 90°", 82, 1, () -> imageOp(3));
        item(image, "旋转 180°", 0, 0, () -> imageOp(4));
        image.addSeparator();
        item(image, "反色", 73, 0, () -> imageOp(5));
        item(image, "清除图像（用背景色）", 0, 0, () -> imageOp(6));
        bar.add(image);

        Menu tool = new Menu("工具");
        int t = 0;
        while (t < 15) {
            int tt = t;
            tool.add(new MenuItem(Painter.toolName(tt), () -> pickTool(tt)));
            t = t + 1;
        }
        bar.add(tool);

        Menu v = new Menu("查看");
        item(v, "放大", 187, 0, () -> zoomIn());
        item(v, "缩小", 189, 0, () -> zoomOut());
        item(v, "实际大小", 48, 0, () -> zoomTo(100));
        item(v, "适应窗口", 57, 0, () -> zoomFit());
        v.addSeparator();
        miGrid = item(v, "显示像素网格（≥400%）", 0, 0, () -> toggleGrid());
        miGrid.setChecked(view.grid);
        bar.add(v);

        Menu help = new Menu("帮助");
        help.add(new MenuItem("关于画图", () -> showAbout()));
        bar.add(help);
        window.setMenuBar(bar);
    }

    void pickTool(int t) {
        painter.setTool(t);
        refresh();
    }

    // ------------------------------------------------------------ 编辑 / 图像操作

    void afterDocChange() {
        view.clampScroll();
        board.repaint();
        updateTitle();
        refreshStatus();
    }

    void doUndo() {
        painter.undo();
        afterDocChange();
    }

    void doRedo() {
        painter.redo();
        afterDocChange();
    }

    void doCut() {
        painter.cutSelection();
        afterDocChange();
    }

    void doCopy() {
        painter.copySelection();
        refreshStatus();
    }

    void doPaste() {
        if (!painter.hasClip()) {
            return;
        }
        int x = Math.max(0, view.docX(0));
        int y = Math.max(0, view.docY(0));
        painter.paste(x, y);
        tools.canvas.repaint();
        afterDocChange();
    }

    void doDelete() {
        painter.deleteSelection();
        afterDocChange();
    }

    void doSelectAll() {
        painter.selectAll();
        tools.canvas.repaint();
        afterDocChange();
    }

    void doCrop() {
        painter.cropToSelection();
        view.reset();
        afterDocChange();
    }

    /** 0 水平翻转 1 垂直翻转 2 顺时针 3 逆时针 4 180° 5 反色 6 清除。 */
    void imageOp(int op) {
        painter.endEdits();
        if (op == 0) {
            doc.flipHorizontal();
        } else if (op == 1) {
            doc.flipVertical();
        } else if (op == 2) {
            doc.rotate(1);
        } else if (op == 3) {
            doc.rotate(3);
        } else if (op == 4) {
            doc.rotate(2);
        } else if (op == 5) {
            doc.invert();
        } else if (op == 6) {
            doc.push();
            doc.fill(painter.bg);
        }
        painter.modified = true;
        if (op >= 2 && op <= 4) {
            view.reset();
        }
        afterDocChange();
    }

    void swapColors() {
        painter.swapColors();
        palette.repaint();
    }

    // ------------------------------------------------------------ 缩放

    void zoomIn() {
        view.zoomCenter(1);
        refreshStatus();
    }

    void zoomOut() {
        view.zoomCenter(-1);
        refreshStatus();
    }

    void zoomTo(int z) {
        view.setZoom(z, view.viewW() / 2, view.viewH() / 2);
        refreshStatus();
    }

    void zoomFit() {
        int best = 25;
        int i = 0;
        while (i < BoardView.LEVELS.length) {
            int z = BoardView.LEVELS[i];
            if (doc.w * z / 100 + 2 * BoardView.MARGIN <= view.viewW() && doc.h * z / 100 + 2 * BoardView.MARGIN <= view.viewH()) {
                best = z;
            }
            i = i + 1;
        }
        view.setZoom(best, 0, 0);
        view.reset();
        refreshStatus();
    }

    void toggleGrid() {
        view.grid = !view.grid;
        miGrid.setChecked(view.grid);
        saveSettings();
        board.repaint();
    }

    // ------------------------------------------------------------ 文件

    boolean confirmDiscard() {
        if (!painter.modified) {
            return true;
        }
        painter.endEdits();
        int r = Dialogs.askSave(window, "是否保存对“" + displayName() + "”的更改？");
        if (r == 0) {
            return false;
        }
        if (r == 1) {
            return doSave();
        }
        return true;
    }

    /** 新建空白画布。 */
    void newCanvas(int w, int h) {
        painter.reset();
        doc.load(doc.blank(w, h, Pix.WHITE));
        painter.bg = Pix.WHITE;
        path = "";
        painter.modified = false;
        view.setZoom(100, 0, 0);
        view.reset();
        refresh();
    }

    void newDialog() {
        if (!confirmDiscard()) {
            return;
        }
        sizeDialog("新建画布", "宽度（像素）：", "高度（像素）：", doc.w, doc.h, false, "新建", (w, h) -> newCanvas(w, h));
    }

    void doOpen() {
        if (!confirmDiscard()) {
            return;
        }
        String p = FileDialogEx.show(window, 0, "图片 (*.png;*.bmp)|*.png;*.bmp|所有文件 (*.*)|*.*", "", "", 0);
        if (p == null || p.length() == 0) {
            return;
        }
        openPath(p);
    }

    boolean openPath(String p) {
        if (!Files.exists(FsPath.of(p))) {
            Dialogs.error(window, "文件不存在：" + p);
            return false;
        }
        byte[] head = Files.readBytes(FsPath.of(p));
        boolean png = head.length > 8 && head[0] == (byte) 137 && head[1] == 80 && head[2] == 78 && head[3] == 71;
        boolean bmp = head.length > 2 && head[0] == 66 && head[1] == 77;
        if (!png && !bmp) {
            Dialogs.error(window, "无法打开“" + baseName(p) + "”：目前只支持 PNG 和 BMP 图片。");
            return false;
        }
        Image loaded = Image.load(p);
        int w = loaded.getWidth();
        int h = loaded.getHeight();
        Image flat = doc.blank(w, h, Pix.WHITE);
        Graphics g = flat.beginGraphics();
        g.drawImage(loaded, 0, 0);
        g.end();
        loaded.dispose();
        painter.reset();
        doc.load(flat);
        path = p;
        painter.modified = false;
        view.setZoom(100, 0, 0);
        view.reset();
        refresh();
        return true;
    }

    boolean doSave() {
        String lower = path.toLowerCase();
        if (path.length() == 0 || !lower.endsWith(".png")) {
            return doSaveAs();
        }
        return writePng(path);
    }

    boolean doSaveAs() {
        String name = path.length() == 0 ? "未命名.png" : stem(baseName(path)) + ".png";
        String p = FileDialogEx.show(window, 1, "PNG 图像 (*.png)|*.png", name, "", 0);
        if (p == null || p.length() == 0) {
            return false;
        }
        if (!p.toLowerCase().endsWith(".png")) {
            p = p + ".png";
        }
        return writePng(p);
    }

    static String stem(String name) {
        int i = name.lastIndexOf(".");
        if (i <= 0) {
            return name;
        }
        return name.substring(0, i);
    }

    boolean writePng(String p) {
        painter.endEdits();
        doc.img.save(p);
        if (!Files.exists(FsPath.of(p))) {
            Dialogs.error(window, "无法写入文件：" + p);
            return false;
        }
        path = p;
        painter.modified = false;
        updateTitle();
        lblHint.setText("已存储 " + baseName(p));
        return true;
    }

    void revealInFinder() {
        if (path.length() == 0) {
            Dialogs.info(window, "请先存储图片。");
            return;
        }
        Desktop.revealFile(path);
    }

    // ------------------------------------------------------------ 对话框

    /** 宽 × 高对话框；ratio 为 true 时显示“保持纵横比”。 */
    void sizeDialog(String title, String lw, String lh, int w0, int h0, boolean ratio, String okText, IntPair done) {
        Window w = new Window(title, 360, ratio ? 214 : 186);
        w.setDialogStyle(window);
        Panel p = new Panel();
        Label l1 = new Label(lw);
        p.add(l1);
        l1.setBounds(20, 20, 140, 22);
        TextField tw = new TextField(w0 + "");
        p.add(tw);
        tw.setBounds(170, 16, 160, 28);
        Label l2 = new Label(lh);
        p.add(l2);
        l2.setBounds(20, 58, 140, 22);
        TextField th = new TextField(h0 + "");
        p.add(th);
        th.setBounds(170, 54, 160, 28);
        CheckBox keep = new CheckBox("保持纵横比");
        int btnY = 100;
        if (ratio) {
            keep.setSelected(true);
            p.add(keep);
            keep.setBounds(20, 92, 200, 24);
            btnY = 128;
            tw.onChange(e -> {
                if (keep.isSelected()) {
                    int a = Pix.parseInt(tw.getText(), -1);
                    if (a > 0) {
                        th.setText(Math.max(1, a * h0 / w0) + "");
                    }
                }
            });
        }
        Button ok = new Button(okText);
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(160, btnY, 80, 32);
        cancel.setBounds(250, btnY, 80, 32);
        ok.onClick(() -> {
            int a = Pix.parseInt(tw.getText(), -1);
            int b = Pix.parseInt(th.getText(), -1);
            if (a < 1 || b < 1 || a > 4096 || b > 4096) {
                Dialogs.error(w, "宽度和高度必须是 1 到 4096 之间的整数。");
                return;
            }
            w.close();
            done.apply(a, b);
        });
        cancel.onClick(() -> w.close());
        w.setDefaultButton(ok);
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        w.show();
        tw.requestFocus();
    }

    void canvasSizeDialog() {
        sizeDialog("画布大小", "宽度（像素）：", "高度（像素）：", doc.w, doc.h, false, "确定", (w, h) -> {
            painter.endEdits();
            doc.resizeCanvas(w, h, painter.bg);
            painter.modified = true;
            view.reset();
            afterDocChange();
        });
    }

    void scaleDialog() {
        sizeDialog("缩放图像", "新宽度（像素）：", "新高度（像素）：", doc.w, doc.h, true, "确定", (w, h) -> {
            painter.endEdits();
            doc.scaleTo(w, h);
            painter.modified = true;
            view.reset();
            afterDocChange();
        });
    }

    void textDialog(int x, int y) {
        Window w = new Window("输入文字", 380, 240);
        w.setDialogStyle(window);
        Panel p = new Panel();
        int size = Painter.TEXT_SZ[painter.textIdx];
        Label l = new Label("字号 " + size + (painter.textBold ? "（加粗）" : "") + "，颜色 " + Pix.hex(painter.fg) + "：");
        p.add(l);
        l.setBounds(20, 14, 340, 22);
        TextArea ta = new TextArea();
        p.add(ta);
        ta.setBounds(20, 42, 340, 112);
        Button ok = new Button("放置");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(180, 172, 80, 32);
        cancel.setBounds(270, 172, 80, 32);
        ok.onClick(() -> {
            String t = ta.getText();
            w.close();
            if (t.length() > 0) {
                painter.drawText(t, x, y, size, painter.textBold);
                afterGesture();
            }
        });
        cancel.onClick(() -> w.close());
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        w.show();
        ta.requestFocus();
    }

    void editColorRequest() {
        editColor(pal.editTarget, pal.editSlot);
    }

    /** target：1 前景色 2 背景色 3 自定义色格 slot。 */
    void editColor(int target, int slot) {
        int cur = painter.fg;
        if (target == 2) {
            cur = painter.bg;
        } else if (target == 3) {
            cur = pal.custom[slot];
        }
        Window w = new Window(target == 2 ? "编辑背景色" : (target == 3 ? "编辑自定义颜色" : "编辑前景色"), 360, 250);
        w.setDialogStyle(window);
        Panel p = new Panel();
        Label lr = new Label("红 (0–255)：");
        Label lg = new Label("绿 (0–255)：");
        Label lb = new Label("蓝 (0–255)：");
        Label lh = new Label("十六进制：");
        p.add(lr);
        p.add(lg);
        p.add(lb);
        p.add(lh);
        lr.setBounds(20, 22, 100, 22);
        lg.setBounds(20, 58, 100, 22);
        lb.setBounds(20, 94, 100, 22);
        lh.setBounds(20, 130, 100, 22);
        TextField tr = new TextField(Pix.r(cur) + "");
        TextField tg = new TextField(Pix.g(cur) + "");
        TextField tb = new TextField(Pix.b(cur) + "");
        TextField th = new TextField(Pix.hex(cur));
        p.add(tr);
        p.add(tg);
        p.add(tb);
        p.add(th);
        tr.setBounds(120, 18, 80, 28);
        tg.setBounds(120, 54, 80, 28);
        tb.setBounds(120, 90, 80, 28);
        th.setBounds(120, 126, 110, 28);
        Canvas sw = new Canvas();
        p.add(sw);
        sw.setBounds(240, 18, 100, 100);
        int[] col = new int[1];
        col[0] = cur;
        sw.onPaint(g -> {
            g.setColorRgba(0xC4, 0xC8, 0xD0, 255);
            g.fillRect(0, 0, 100, 100);
            g.setColorRgba(Pix.r(col[0]), Pix.g(col[0]), Pix.b(col[0]), 255);
            g.fillRect(1, 1, 98, 98);
        });
        boolean[] busy = new boolean[1];
        tr.onChange(e -> rgbChanged(busy, col, tr, tg, tb, th, sw));
        tg.onChange(e -> rgbChanged(busy, col, tr, tg, tb, th, sw));
        tb.onChange(e -> rgbChanged(busy, col, tr, tg, tb, th, sw));
        th.onChange(e -> {
            if (busy[0]) {
                return;
            }
            int c = Pix.parseHex(th.getText(), -1);
            if (c != -1) {
                busy[0] = true;
                col[0] = c;
                tr.setText(Pix.r(c) + "");
                tg.setText(Pix.g(c) + "");
                tb.setText(Pix.b(c) + "");
                busy[0] = false;
                sw.repaint();
            }
        });
        Button ok = new Button("确定");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(170, 180, 80, 32);
        cancel.setBounds(260, 180, 80, 32);
        ok.onClick(() -> {
            w.close();
            applyColor(target, slot, col[0]);
        });
        cancel.onClick(() -> w.close());
        w.setDefaultButton(ok);
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        w.show();
        tr.requestFocus();
    }

    void rgbChanged(boolean[] busy, int[] col, TextField tr, TextField tg, TextField tb, TextField th, Canvas sw) {
        if (busy[0]) {
            return;
        }
        int r = Pix.parseInt(tr.getText(), -1);
        int g = Pix.parseInt(tg.getText(), -1);
        int b = Pix.parseInt(tb.getText(), -1);
        if (r < 0 || g < 0 || b < 0 || r > 255 || g > 255 || b > 255) {
            return;
        }
        col[0] = Pix.rgb(r, g, b);
        busy[0] = true;
        th.setText(Pix.hex(col[0]));
        busy[0] = false;
        sw.repaint();
    }

    void applyColor(int target, int slot, int c) {
        if (target == 2) {
            painter.bg = c;
        } else if (target == 3) {
            pal.custom[slot] = c;
            painter.fg = c;
            saveSettings();
        } else {
            painter.fg = c;
            // 同时记入第一个空闲 / 最旧的自定义格，方便再用
            rememberCustom(c);
        }
        palette.repaint();
    }

    void rememberCustom(int c) {
        int i = 0;
        while (i < 10) {
            if (pal.custom[i] == c) {
                return;
            }
            i = i + 1;
        }
        i = 9;
        while (i > 0) {
            pal.custom[i] = pal.custom[i - 1];
            i = i - 1;
        }
        pal.custom[0] = c;
        saveSettings();
    }

    void showAbout() {
        Dialogs.info(window, "画图 1.0\n\n用 JNative Studio 设计界面、JNative 编译为原生 macOS 程序。\n"
                + "支持铅笔、刷子、喷枪、橡皮、填充、取色、文字、直线、曲线、矩形、多边形、椭圆、圆角矩形、选区移动 / 复制，"
                + "撤销 / 重做，缩放与像素网格，PNG / BMP 打开与 PNG 存储。");
    }

    // ------------------------------------------------------------ 设置

    FsPath settingsFile() {
        FsPath dir = Files.configDir("jnative-paint");
        Files.createDirectories(dir);
        return dir.resolve("settings.txt");
    }

    void loadSettings() {
        FsPath f = settingsFile();
        if (!Files.exists(f)) {
            return;
        }
        String text = Files.readString(f);
        int pos = 0;
        while (pos < text.length()) {
            int eol = text.indexOf("\n", pos);
            if (eol < 0) {
                eol = text.length();
            }
            String ln = text.substring(pos, eol);
            pos = eol + 1;
            int eq = ln.indexOf("=");
            if (eq <= 0) {
                continue;
            }
            String k = ln.substring(0, eq);
            String v = ln.substring(eq + 1);
            if (k.equals("grid")) {
                view.grid = v.equals("1");
            } else if (k.startsWith("custom")) {
                int idx = Pix.parseInt(k.substring(6), -1);
                if (idx >= 0 && idx < 10) {
                    pal.custom[idx] = Pix.parseHex(v, Pix.WHITE);
                }
            }
        }
    }

    void saveSettings() {
        String s = "grid=" + (view.grid ? "1" : "0") + "\n";
        int i = 0;
        while (i < 10) {
            s = s + "custom" + i + "=" + Pix.hex(pal.custom[i]) + "\n";
            i = i + 1;
        }
        Files.writeString(settingsFile(), s);
    }
}
