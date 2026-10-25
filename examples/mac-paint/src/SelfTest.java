import jnative.ui.*;
import jnative.graphics.*;
import jnative.io.*;

/** 无鼠标自测：直接驱动绘图引擎和 MainForm 的事件处理，检查像素、撤销、选区、文件往返，并可截图。 */
public class SelfTest {
    static int failed = 0;

    static void check(String name, String got, String want) {
        if (got.equals(want)) {
            System.out.println("ok   " + name);
        } else {
            failed = failed + 1;
            System.out.println("FAIL " + name + ": got [" + got + "] want [" + want + "]");
        }
    }

    static String px(Doc d, int x, int y) {
        return Pix.hex(d.img.getPixel(x, y));
    }

    static void stroke(Painter p, int x0, int y0, int x1, int y1, int btn) {
        p.down(x0, y0, btn, 0, 0, 1);
        p.move(x1, y1, 0);
        p.up(x1, y1, 0);
    }

    static void click(Painter p, int x, int y) {
        p.down(x, y, 1, 0, 0, 1);
        p.up(x, y, 0);
    }

    static Painter fresh(int w, int h) {
        Doc d = new Doc(w, h);
        return new Painter(d);
    }

    static int countNon(Doc d, int color, int x0, int y0, int x1, int y1) {
        int n = 0;
        int y = y0;
        while (y < y1) {
            int x = x0;
            while (x < x1) {
                if (d.img.getPixel(x, y) != color) {
                    n = n + 1;
                }
                x = x + 1;
            }
            y = y + 1;
        }
        return n;
    }

    static int run() {
        engine();
        selection();
        shapes();
        docOps();
        files();
        form();
        return failed;
    }

    // ------------------------------------------------------------ 基本工具

    static void engine() {
        Painter p = fresh(64, 48);
        Doc d = p.doc;
        check("初始为白色", px(d, 3, 3), "#FFFFFF");

        p.setTool(Painter.PENCIL);
        stroke(p, 5, 5, 20, 5, 1);
        check("铅笔起点", px(d, 5, 5), "#000000");
        check("铅笔中点", px(d, 12, 5), "#000000");
        check("铅笔终点", px(d, 20, 5), "#000000");
        check("铅笔不外溢", px(d, 12, 6), "#FFFFFF");
        check("铅笔标记已修改", "" + p.modified, "true");
        p.undo();
        check("撤销铅笔", px(d, 12, 5), "#FFFFFF");
        p.redo();
        check("重做铅笔", px(d, 12, 5), "#000000");

        p.fg = Pix.rgb(255, 0, 0);
        p.bg = Pix.rgb(0, 0, 255);
        stroke(p, 5, 10, 15, 10, 3);
        check("右键用背景色", px(d, 10, 10), "#0000FF");

        p.setTool(Painter.LINE);
        p.fg = Pix.BLACK;
        p.widthIdx = 2;
        stroke(p, 10, 20, 30, 20, 1);
        check("粗线中心", px(d, 20, 20), "#000000");
        check("粗线上沿", px(d, 20, 19), "#000000");
        check("粗线下沿", px(d, 20, 21), "#000000");
        check("粗线外侧", px(d, 20, 22), "#FFFFFF");
        p.widthIdx = 0;
        p.setTool(Painter.LINE);
        p.down(40, 30, 1, 0, 0, 1);
        p.move(60, 33, 1);
        p.up(60, 33, 1);
        check("⇧ 约束为水平线", px(d, 55, 30), "#000000");
        check("⇧ 约束后无斜线", px(d, 55, 32), "#FFFFFF");
        stroke(p, 3, 3, 3, 3, 1);
        check("单击直线不留痕迹", px(d, 3, 3), "#FFFFFF");

        p.setTool(Painter.ERASER);
        p.eraserIdx = 1;
        stroke(p, 18, 20, 22, 20, 1);
        check("橡皮擦成背景色", px(d, 20, 20), "#0000FF");
        check("橡皮擦范围外不动", px(d, 30, 20), "#000000");

        p.setTool(Painter.PICK);
        click(p, 20, 20);
        check("取色器取到背景", Pix.hex(p.pickedColor) + p.picked, "#0000FFtrue");
        p.picked = false;

        // 刷子（抗锯齿，中心必为实色）
        p.setTool(Painter.BRUSH);
        p.fg = Pix.rgb(0, 160, 0);
        p.brushIdx = 3;
        stroke(p, 10, 40, 40, 40, 1);
        check("刷子中心", px(d, 25, 40), "#00A000");
        // 喷枪
        p.setTool(Painter.AIRBRUSH);
        p.sprayIdx = 1;
        p.down(50, 10, 1, 0, 0, 1);
        p.sprayTick();
        p.sprayTick();
        p.up(50, 10, 0);
        check("喷枪撒出点", "" + (countNon(d, Pix.WHITE, 40, 2, 62, 20) > 5), "true");
    }

    // ------------------------------------------------------------ 形状与填充

    static void shapes() {
        Painter p = fresh(80, 60);
        Doc d = p.doc;
        p.fg = Pix.BLACK;
        p.bg = Pix.rgb(255, 0, 0);

        p.setTool(Painter.RECT);
        stroke(p, 10, 10, 40, 30, 1);
        check("矩形角", px(d, 10, 10), "#000000");
        check("矩形右边", px(d, 40, 20), "#000000");
        check("矩形内部为空", px(d, 25, 20), "#FFFFFF");
        p.fillMode = 1;
        stroke(p, 50, 10, 70, 30, 1);
        check("描边加填充：边", px(d, 50, 20), "#000000");
        check("描边加填充：内部为背景色", px(d, 60, 20), "#FF0000");
        p.fillMode = 2;
        stroke(p, 50, 35, 70, 55, 1);
        check("纯填充使用前景色", px(d, 60, 45), "#000000");

        // 填充：把空矩形内部涂成红色，外面不动
        p.setTool(Painter.FILL);
        p.down(25, 20, 3, 0, 0, 1);
        p.up(25, 20, 0);
        check("填充内部（右键用背景色）", px(d, 25, 20), "#FF0000");
        check("填充不越过边框", px(d, 5, 5), "#FFFFFF");
        int undoBefore = d.undo.size();
        p.down(25, 20, 3, 0, 0, 1);
        p.up(25, 20, 0);
        check("相同颜色重复填充不产生撤销步骤", "" + (d.undo.size() == undoBefore), "true");
        p.undo();
        check("撤销填充", px(d, 25, 20), "#FFFFFF");

        p.fillMode = 0;
        p.setTool(Painter.ELLIPSE);
        stroke(p, 5, 35, 35, 58, 1);
        check("椭圆最左点", px(d, 5, 46), "#000000");
        check("椭圆中心为空", px(d, 20, 46), "#FFFFFF");
        check("椭圆外角为空", px(d, 5, 35), "#FFFFFF");
        p.setTool(Painter.ROUNDRECT);
        stroke(p, 40, 35, 49, 58, 1);
        check("圆角矩形角被削圆", px(d, 40, 35), "#FFFFFF");
        check("圆角矩形边", px(d, 44, 35), "#000000");

        // 多边形
        Painter q = fresh(40, 40);
        q.setTool(Painter.POLY);
        q.fillMode = 2;
        click(q, 5, 5);
        click(q, 30, 5);
        click(q, 17, 30);
        q.down(17, 30, 1, 0, 0, 2);
        q.up(17, 30, 0);
        check("多边形填充内部", px(q.doc, 17, 12), "#000000");
        check("多边形外部不填", px(q.doc, 3, 20), "#FFFFFF");
        check("多边形完成后不再进行", "" + q.pn, "0");

        // 曲线：先拖直线，再拖两次弯曲
        Painter c = fresh(50, 50);
        c.setTool(Painter.CURVE);
        stroke(c, 5, 40, 45, 40, 1);
        check("曲线第一步", "" + c.curveStage, "1");
        stroke(c, 25, 10, 25, 10, 1);
        check("曲线第二步", "" + c.curveStage, "2");
        stroke(c, 25, 10, 25, 10, 1);
        check("曲线完成", "" + c.curveStage, "0");
        int top = -1;
        int y = 0;
        while (y < 50 && top < 0) {
            if (c.doc.img.getPixel(25, y) != Pix.WHITE) {
                top = y;
            }
            y = y + 1;
        }
        check("曲线向上弯曲", "" + (top >= 15 && top <= 30), "true");
        check("曲线两端", px(c.doc, 5, 40) + px(c.doc, 45, 40), "#000000#000000");

        // 文字
        Painter t = fresh(120, 40);
        t.fg = Pix.BLACK;
        t.drawText("Hi 文字", 4, 4, 20, false);
        check("文字画出了像素", "" + (countNon(t.doc, Pix.WHITE, 0, 0, 120, 40) > 30), "true");
        t.undo();
        check("撤销文字", "" + countNon(t.doc, Pix.WHITE, 0, 0, 120, 40), "0");
    }

    // ------------------------------------------------------------ 选区

    static void selection() {
        Painter p = fresh(60, 60);
        Doc d = p.doc;
        p.setTool(Painter.PENCIL);
        click(p, 3, 3);
        p.setTool(Painter.SELECT);
        stroke(p, 2, 2, 9, 9, 1);
        check("框选尺寸", p.selW + "x" + p.selH, "8x8");
        p.down(4, 4, 1, 0, 0, 1);
        p.move(14, 14, 0);
        p.up(14, 14, 0);
        check("移动后选区位置", p.selX + "," + p.selY, "12,12");
        p.commitSelection();
        check("移动后原处清为背景色", px(d, 3, 3), "#FFFFFF");
        check("移动后新位置", px(d, 13, 13), "#000000");
        p.undo();
        check("撤销移动：原处恢复", px(d, 3, 3), "#000000");
        check("撤销移动：新位置清空", px(d, 13, 13), "#FFFFFF");

        // 拷贝 / 粘贴
        stroke(p, 2, 2, 9, 9, 1);
        p.copySelection();
        p.commitSelection();
        p.paste(30, 30);
        check("粘贴后为浮动选区", p.selActive + "/" + (p.selImg != null), "true/true");
        p.commitSelection();
        check("粘贴内容落位", px(d, 31, 31), "#000000");
        p.undo();
        check("撤销粘贴", px(d, 31, 31), "#FFFFFF");

        // 删除选区
        stroke(p, 0, 0, 9, 9, 1);
        p.deleteSelection();
        check("删除选区清为背景色", px(d, 3, 3), "#FFFFFF");
        p.undo();
        check("撤销删除选区", px(d, 3, 3), "#000000");

        // 复制式移动（⌥）
        stroke(p, 2, 2, 9, 9, 1);
        p.down(4, 4, 1, 0, 1, 1);
        p.move(24, 4, 0);
        p.up(24, 4, 0);
        p.commitSelection();
        check("⌥ 移动保留原件", px(d, 3, 3) + px(d, 23, 3), "#000000#000000");

        // 裁剪到选区
        Painter c = fresh(40, 40);
        c.setTool(Painter.PENCIL);
        click(c, 11, 11);
        c.setTool(Painter.SELECT);
        stroke(c, 10, 10, 19, 14, 1);
        c.cropToSelection();
        check("裁剪尺寸", c.doc.w + "x" + c.doc.h, "10x5");
        check("裁剪内容", px(c.doc, 1, 1), "#000000");
        c.undo();
        check("撤销裁剪", c.doc.w + "x" + c.doc.h, "40x40");

        // 方向键微调
        Painter n = fresh(30, 30);
        n.setTool(Painter.SELECT);
        stroke(n, 5, 5, 10, 10, 1);
        n.nudge(3, 2);
        check("微调选区", n.selX + "," + n.selY, "8,7");
    }

    // ------------------------------------------------------------ 整图操作

    static void docOps() {
        Doc d = new Doc(30, 20);
        d.img.setPixel(0, 0, Pix.BLACK);
        d.flipHorizontal();
        check("水平翻转", px(d, 29, 0) + px(d, 0, 0), "#000000#FFFFFF");
        d.flipVertical();
        check("垂直翻转", px(d, 29, 19), "#000000");
        d.undoStep();
        d.undoStep();
        check("撤销两次翻转", px(d, 0, 0), "#000000");
        d.rotate(1);
        check("顺时针旋转尺寸", d.w + "x" + d.h, "20x30");
        check("顺时针旋转像素", px(d, 19, 0), "#000000");
        d.rotate(3);
        check("逆时针转回", px(d, 0, 0) + d.w + "x" + d.h, "#00000030x20");
        d.rotate(2);
        check("旋转 180°", px(d, 29, 19), "#000000");
        d.invert();
        check("反色：黑变白", px(d, 29, 19), "#FFFFFF");
        check("反色：白变黑", px(d, 0, 0), "#000000");
        d.resizeCanvas(40, 25, Pix.rgb(255, 0, 0));
        check("画布扩大后新增区域为背景色", px(d, 35, 22), "#FF0000");
        check("画布扩大保留原内容", px(d, 0, 0), "#000000");
        d.resizeCanvas(10, 10, Pix.WHITE);
        check("画布缩小", d.w + "x" + d.h, "10x10");
        d.scaleTo(20, 20);
        check("缩放 2 倍", px(d, 0, 0) + px(d, 1, 1) + px(d, 2, 2), "#000000#000000#000000");
        check("撤销历史上限", "" + (d.undo.size() <= Doc.MAX_UNDO), "true");
    }

    // ------------------------------------------------------------ 文件往返

    static void files() {
        String dir = "/tmp/macpaint-selftest";
        Files.createDirectories(FsPath.of(dir));
        Doc d = new Doc(40, 30);
        d.img.setPixel(3, 4, Pix.rgb(12, 34, 56));
        d.img.setPixel(39, 29, Pix.rgb(250, 1, 2));
        String p = dir + "/a.png";
        d.img.save(p);
        Image back = Image.load(p);
        check("PNG 尺寸往返", back.getWidth() + "x" + back.getHeight(), "40x30");
        check("PNG 像素往返", Pix.hex(back.getPixel(3, 4)) + Pix.hex(back.getPixel(39, 29)), "#0C2238#FA0102");
        back.dispose();
        check("颜色十六进制解析", Pix.hex(Pix.parseHex("#1A2b3C", 0)), "#1A2B3C");
        check("非法十六进制回退", "" + Pix.parseHex("zz", 7), "7");
    }

    // ------------------------------------------------------------ 窗体层

    static MouseEvent me(MainForm f, int type, int docX, int docY, int btn) {
        int sx = (int) Math.floor(f.view.screenX(docX + 0.5));
        int sy = (int) Math.floor(f.view.screenY(docY + 0.5));
        return new MouseEvent(f.board, type, sx, sy, btn);
    }

    static void form() {
        MainForm f = new MainForm();
        f.show();
        f.newCanvas(100, 80);
        check("新建标题", f.titleText, "未命名 — 画图");
        check("状态栏尺寸", f.lblSize.getText(), "100 × 80 像素");

        f.pickTool(Painter.PENCIL);
        f.boardDown(me(f, 1, 10, 10, 1));
        f.boardMove(me(f, 5, 30, 10, 1));
        f.boardUp(me(f, 2, 30, 10, 1));
        check("事件链：铅笔画线", px(f.doc, 20, 10), "#000000");
        check("画完标题带已编辑", f.titleText, "未命名 — 已编辑 — 画图");

        f.zoomTo(400);
        check("缩放状态栏", f.lblZoom.getText(), "400%");
        check("缩放后坐标换算", "" + f.view.docX((int) Math.floor(f.view.screenX(12.5))), "12");
        f.zoomTo(100);

        f.pal.down(new MouseEvent(f.palette, 1, Palette.X0 + 2 * Palette.PITCH + 5, Palette.Y0 + 5, 1));
        check("调色板左键设前景色", Pix.hex(f.painter.fg), "#800000");
        f.pal.down(new MouseEvent(f.palette, 1, Palette.X0 + 3, Palette.Y0 + Palette.PITCH + 5, 3));
        check("调色板右键设背景色", Pix.hex(f.painter.bg), "#FFFFFF");
        f.pal.down(new MouseEvent(f.palette, 1, Palette.X0 + 16 * 0 + 5 * Palette.PITCH + 3, Palette.Y0 + 3, 1));
        check("再选一种前景色", Pix.hex(f.painter.fg), "#008080");

        f.tools.down(new MouseEvent(f.toolbox, 1, Toolbox.LEFT + 3, Toolbox.TOP + 3, 1));
        check("工具箱选中“选择”", "" + f.painter.tool, "" + Painter.SELECT);
        f.tools.down(new MouseEvent(f.toolbox, 1, Toolbox.LEFT + Toolbox.CELL_W + 3, Toolbox.TOP + 3 + 6 * Toolbox.CELL_H, 1));
        check("工具箱选中“椭圆”", "" + f.painter.tool, "" + Painter.ELLIPSE);
        f.tools.down(new MouseEvent(f.toolbox, 1, 20, f.tools.optionsTop() + 22 + 1, 1));
        check("选项区：填充方式", "" + f.painter.fillMode, "0");
        f.tools.down(new MouseEvent(f.toolbox, 1, 20, f.tools.optionsTop() + 22 + 26 + 1, 1));
        check("选项区：描边加填充", "" + f.painter.fillMode, "1");

        f.imageOp(5);
        check("反色菜单操作", px(f.doc, 90, 70), "#000000");
        f.doUndo();
        check("撤销反色", px(f.doc, 90, 70), "#FFFFFF");

        String out = "/tmp/macpaint-selftest/form.png";
        check("存储成功", f.writePng(out) ? "yes" : "no", "yes");
        check("存储后清除标记", f.titleText, "form.png — 画图");
        f.newCanvas(10, 10);
        check("重新新建后尺寸", f.doc.w + "x" + f.doc.h, "10x10");
        check("打开成功", f.openPath(out) ? "yes" : "no", "yes");
        check("打开后尺寸", f.doc.w + "x" + f.doc.h, "100x80");
        check("打开后像素", px(f.doc, 20, 10), "#000000");
        check("打开后标题", f.titleText, "form.png — 画图");
    }

    // ------------------------------------------------------------ 截图

    static int snapStep = 0;
    static UiTimer snapTimer;

    /** 画一幅示例图并截图（file 非空则先打开该图片）。 */
    static void snapshot(String file, String png) {
        MainForm f = new MainForm();
        f.show();
        snapTimer = new UiTimer(500, () -> {
            snapStep = snapStep + 1;
            if (snapStep == 1) {
                if (file.length() > 0) {
                    f.openPath(file);
                } else {
                    demoScene(f);
                }
            } else if (snapStep == 3) {
                f.window.snapshot(png.length() > 0 ? png : "/tmp/macpaint.png");
                System.out.println("snapshot done");
                System.exit(0);
            }
        });
    }

    static void demoScene(MainForm f) {
        Painter p = f.painter;
        p.fg = Pix.rgb(0x2F, 0x6F, 0xED);
        p.bg = Pix.rgb(0xFF, 0xE0, 0x8A);
        p.setTool(Painter.ROUNDRECT);
        p.fillMode = 1;
        p.widthIdx = 2;
        stroke(p, 40, 40, 300, 200, 1);
        p.fg = Pix.rgb(0xE0, 0x3E, 0x3E);
        p.bg = Pix.rgb(0xCF, 0xF2, 0xD0);
        p.setTool(Painter.ELLIPSE);
        stroke(p, 200, 120, 460, 330, 1);
        p.fg = Pix.rgb(0x22, 0x22, 0x22);
        p.setTool(Painter.BRUSH);
        p.brushIdx = 3;
        stroke(p, 60, 300, 180, 380, 1);
        stroke(p, 180, 380, 320, 300, 1);
        p.setTool(Painter.PENCIL);
        stroke(p, 500, 60, 640, 90, 1);
        p.fg = Pix.rgb(0x8E, 0x44, 0xAD);
        p.setTool(Painter.POLY);
        p.fillMode = 2;
        click(p, 520, 200);
        click(p, 640, 230);
        click(p, 600, 330);
        p.down(560, 350, 1, 0, 0, 2);
        p.up(560, 350, 0);
        p.fg = Pix.rgb(0x1B, 0x7F, 0x4B);
        p.fillMode = 0;
        p.widthIdx = 1;
        p.setTool(Painter.CURVE);
        stroke(p, 360, 380, 560, 380, 1);
        stroke(p, 420, 300, 420, 300, 1);
        stroke(p, 500, 460, 500, 460, 1);
        p.fg = Pix.BLACK;
        p.drawText("JNative 画图", 60, 440, 36, true);
        p.setTool(Painter.RECT);
        p.fillMode = 0;
        p.widthIdx = 0;
        f.painter.tool = Painter.SELECT;
        stroke(p, 320, 40, 420, 110, 1);
        p.setTool(Painter.RECT);
        f.refresh();
    }
}
