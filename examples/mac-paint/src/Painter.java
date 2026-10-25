import jnative.graphics.*;

/**
 * 绘图工具：不依赖界面，坐标都是图像像素。MainForm 把鼠标事件换算成图像坐标后交给这里；
 * 形状类工具在手势期间“还原快照 → 重画”实现实时预览，松开鼠标后即成为一步撤销。
 */
public class Painter {
    static int SELECT = 0;
    static int ERASER = 1;
    static int FILL = 2;
    static int PICK = 3;
    static int ZOOM = 4;
    static int PENCIL = 5;
    static int BRUSH = 6;
    static int AIRBRUSH = 7;
    static int TEXT = 8;
    static int LINE = 9;
    static int CURVE = 10;
    static int RECT = 11;
    static int POLY = 12;
    static int ELLIPSE = 13;
    static int ROUNDRECT = 14;

    static int[] LINE_W = new int[] {1, 2, 3, 5, 8};
    static int[] BRUSH_W = new int[] {2, 4, 7, 11, 16};
    static int[] ERASER_W = new int[] {4, 8, 12, 20, 32};
    static int[] SPRAY_R = new int[] {5, 8, 12, 18, 26};
    static int[] TEXT_SZ = new int[] {12, 16, 24, 36, 56};

    static String toolName(int t) {
        if (t == SELECT) {
            return "选择";
        } else if (t == ERASER) {
            return "橡皮擦";
        } else if (t == FILL) {
            return "颜色填充";
        } else if (t == PICK) {
            return "取色器";
        } else if (t == ZOOM) {
            return "放大镜";
        } else if (t == PENCIL) {
            return "铅笔";
        } else if (t == BRUSH) {
            return "刷子";
        } else if (t == AIRBRUSH) {
            return "喷枪";
        } else if (t == TEXT) {
            return "文字";
        } else if (t == LINE) {
            return "直线";
        } else if (t == CURVE) {
            return "曲线";
        } else if (t == RECT) {
            return "矩形";
        } else if (t == POLY) {
            return "多边形";
        } else if (t == ELLIPSE) {
            return "椭圆";
        }
        return "圆角矩形";
    }

    static String toolHint(int t) {
        if (t == SELECT) {
            return "拖动框选；拖动选区移动（按住 ⌥ 复制）；Delete 清除；Esc 取消选择";
        } else if (t == ERASER) {
            return "拖动擦除为背景色";
        } else if (t == FILL) {
            return "单击填充（左键前景色，右键背景色）";
        } else if (t == PICK) {
            return "单击取色（左键前景色，右键背景色）";
        } else if (t == ZOOM) {
            return "单击放大，右键缩小";
        } else if (t == PENCIL) {
            return "拖动绘制 1 像素线条（右键使用背景色）";
        } else if (t == BRUSH) {
            return "拖动绘制（右键使用背景色）";
        } else if (t == AIRBRUSH) {
            return "按住喷洒";
        } else if (t == TEXT) {
            return "单击要放置文字的位置";
        } else if (t == LINE) {
            return "拖动绘制直线，按住 ⇧ 限制为 45° 倍数";
        } else if (t == CURVE) {
            return "拖出一条线，再依次拖动两次弯曲它";
        } else if (t == RECT || t == ELLIPSE || t == ROUNDRECT) {
            return "拖动绘制，按住 ⇧ 画正圆 / 正方形";
        }
        return "逐点单击；双击或点回起点闭合，Esc 取消";
    }

    Doc doc;
    int fg = Pix.BLACK;
    int bg = Pix.WHITE;
    int tool = 5;
    int prevTool = 5;
    int widthIdx = 0;
    int brushIdx = 1;
    int eraserIdx = 1;
    int sprayIdx = 1;
    int textIdx = 1;
    boolean textBold = false;
    int fillMode = 0;

    boolean modified = false;

    // 手势状态
    boolean active = false;
    int button = 1;
    int startX = 0;
    int startY = 0;
    int lastX = 0;
    int lastY = 0;

    // 曲线
    int curveStage = 0;
    int c1x = 0;
    int c1y = 0;
    int c2x = 0;
    int c2y = 0;
    int endX = 0;
    int endY = 0;

    // 多边形
    int[] px = new int[600];
    int[] py = new int[600];
    int pn = 0;

    // 选区
    boolean selActive = false;
    int selX = 0;
    int selY = 0;
    int selW = 0;
    int selH = 0;
    Image selImg = null;
    boolean selMoving = false;
    boolean selMaking = false;
    int grabX = 0;
    int grabY = 0;
    Image clip = null;

    // 给界面的回传
    boolean wantText = false;
    int textX = 0;
    int textY = 0;
    boolean picked = false;
    int pickedColor = 0;
    int pickedButton = 1;

    Random rnd = new Random(20260425);

    // 刷子形状缓存
    int brushSize = -1;
    int[] bl = new int[64];
    int[] br = new int[64];

    // span() 的输出
    int sl = 0;
    int sr = 0;

    Painter(Doc d) {
        doc = d;
    }

    // ------------------------------------------------------------ 状态

    int lineWidth() {
        return LINE_W[widthIdx];
    }

    int colorFor(int btn) {
        if (btn == 3) {
            return bg;
        }
        return fg;
    }

    int backFor(int btn) {
        if (btn == 3) {
            return fg;
        }
        return bg;
    }

    boolean inDoc(int x, int y) {
        return x >= 0 && y >= 0 && x < doc.w && y < doc.h;
    }

    void setTool(int t) {
        if (t == tool) {
            return;
        }
        endEdits();
        if (t == PICK) {
            prevTool = tool;
        }
        tool = t;
    }

    /** 结束所有进行中的编辑：多边形 / 曲线按当前样子落笔，选区落到图像上。 */
    void endEdits() {
        if (pn > 0) {
            if (pn >= 3) {
                finishPoly(button);
            } else {
                cancelGesture();
            }
        }
        if (curveStage > 0) {
            curveStage = 0;
            active = false;
            modified = true;
        }
        commitSelection();
    }

    /** Esc：放弃进行中的形状；没有的话取消选区。 */
    void cancelGesture() {
        if (pn > 0 || curveStage > 0 || (active && tool >= LINE && tool != POLY)) {
            if (doc.canUndo()) {
                doc.restoreFrom(doc.base());
                doc.dropLast();
            }
        }
        pn = 0;
        curveStage = 0;
        active = false;
        selMaking = false;
        selMoving = false;
    }

    // ------------------------------------------------------------ 鼠标手势

    void down(int x, int y, int btn, int shift, int alt, int clicks) {
        button = btn;
        int col = colorFor(btn);
        int t = tool;
        if (t != SELECT) {
            commitSelection();
        }
        if (t == PENCIL) {
            doc.push();
            Graphics g = doc.img.beginGraphics();
            setCol(g, col);
            g.fillRect(x, y, 1, 1);
            g.end();
            active = true;
            lastX = x;
            lastY = y;
            modified = true;
        } else if (t == BRUSH) {
            doc.push();
            active = true;
            lastX = x;
            lastY = y;
            brushSegment(x, y, x, y, col);
            modified = true;
        } else if (t == ERASER) {
            doc.push();
            active = true;
            lastX = x;
            lastY = y;
            eraseSegment(x, y, x, y);
            modified = true;
        } else if (t == AIRBRUSH) {
            doc.push();
            active = true;
            lastX = x;
            lastY = y;
            spray(x, y);
            modified = true;
        } else if (t == FILL) {
            if (inDoc(x, y)) {
                doc.push();
                int n = doc.img.floodFill(x, y, col, 0);
                if (n == 0) {
                    doc.dropLast();
                } else {
                    modified = true;
                }
            }
        } else if (t == PICK) {
            if (inDoc(x, y)) {
                picked = true;
                pickedColor = doc.img.getPixel(x, y) | (255 << 24);
                pickedButton = btn;
            }
        } else if (t == TEXT) {
            wantText = true;
            textX = x;
            textY = y;
        } else if (t == LINE || t == RECT || t == ELLIPSE || t == ROUNDRECT) {
            doc.push();
            active = true;
            startX = x;
            startY = y;
            lastX = x;
            lastY = y;
        } else if (t == CURVE) {
            if (curveStage == 0) {
                doc.push();
                active = true;
                startX = x;
                startY = y;
                endX = x;
                endY = y;
            } else {
                active = true;
            }
        } else if (t == POLY) {
            polyDown(x, y, btn, clicks);
        } else if (t == SELECT) {
            selectDown(x, y, alt);
        }
    }

    void move(int x, int y, int shift) {
        if (!active) {
            hover(x, y, shift);
            return;
        }
        int t = tool;
        int col = colorFor(button);
        if (t == PENCIL) {
            Graphics g = doc.img.beginGraphics();
            setCol(g, col);
            lineRaw(g, lastX, lastY, x, y, 1);
            g.end();
            lastX = x;
            lastY = y;
        } else if (t == BRUSH) {
            brushSegment(lastX, lastY, x, y, col);
            lastX = x;
            lastY = y;
        } else if (t == ERASER) {
            eraseSegment(lastX, lastY, x, y);
            lastX = x;
            lastY = y;
        } else if (t == AIRBRUSH) {
            lastX = x;
            lastY = y;
            spray(x, y);
        } else if (t == LINE || t == RECT || t == ELLIPSE || t == ROUNDRECT) {
            lastX = x;
            lastY = y;
            previewShape(x, y, shift);
        } else if (t == CURVE) {
            curveMove(x, y, shift);
        } else if (t == POLY) {
            lastX = x;
            lastY = y;
        } else if (t == SELECT) {
            selectMove(x, y);
        }
    }

    void up(int x, int y, int shift) {
        if (!active) {
            return;
        }
        int t = tool;
        if (t == PENCIL || t == BRUSH || t == ERASER || t == AIRBRUSH) {
            active = false;
        } else if (t == LINE || t == RECT || t == ELLIPSE || t == ROUNDRECT) {
            if (x == startX && y == startY) {
                // 单击不成形：不留下撤销步骤
                doc.restoreFrom(doc.base());
                doc.dropLast();
            } else {
                previewShape(x, y, shift);
                modified = true;
            }
            active = false;
        } else if (t == CURVE) {
            curveUp(x, y, shift);
        } else if (t == POLY) {
            active = false;
        } else if (t == SELECT) {
            selectUp(x, y);
        }
    }

    /** 没按键的移动：多边形橡皮筋预览。 */
    void hover(int x, int y, int shift) {
        if (tool == POLY && pn > 0) {
            lastX = x;
            lastY = y;
            previewPoly(x, y, false);
        }
    }

    /** 喷枪在原地按住时由定时器调用。 */
    void sprayTick() {
        if (active && tool == AIRBRUSH) {
            spray(lastX, lastY);
        }
    }

    // ------------------------------------------------------------ 基本绘制

    void setCol(Graphics g, int c) {
        g.setColorRgba(Pix.r(c), Pix.g(c), Pix.b(c), 255);
    }

    void ensureBrush(int size) {
        if (size == brushSize) {
            return;
        }
        brushSize = size;
        double c = (size - 1) / 2.0;
        double rad = size / 2.0;
        int j = 0;
        while (j < size) {
            double dy = j - c;
            int l = -1;
            int r = -2;
            int i = 0;
            while (i < size) {
                double dx = i - c;
                if (dx * dx + dy * dy <= rad * rad) {
                    if (l < 0) {
                        l = i;
                    }
                    r = i;
                }
                i = i + 1;
            }
            bl[j] = l;
            br[j] = r;
            j = j + 1;
        }
    }

    /** 以 (x, y) 为中心盖一个直径 size 的圆点（颜色由调用者设置）。 */
    void dot(Graphics g, int x, int y, int size) {
        if (size <= 1) {
            g.fillRect(x, y, 1, 1);
            return;
        }
        ensureBrush(size);
        int top = y - size / 2;
        int left = x - size / 2;
        int j = 0;
        while (j < size) {
            if (br[j] >= bl[j]) {
                g.fillRect(left + bl[j], top + j, br[j] - bl[j] + 1, 1);
            }
            j = j + 1;
        }
    }

    /** Bresenham 线，每个点盖一个 size 的圆点。 */
    void lineRaw(Graphics g, int x0, int y0, int x1, int y1, int size) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            dot(g, x, y, size);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err = err - dy;
                x = x + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                y = y + sy;
            }
        }
    }

    void brushSegment(int x0, int y0, int x1, int y1, int col) {
        int size = BRUSH_W[brushIdx];
        Graphics g = doc.img.beginGraphics();
        setCol(g, col);
        g.setAntialias(true);
        double r = size / 2.0;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        double step = Math.max(1.0, size / 5.0);
        int n = (int) (len / step);
        int i = 0;
        while (i <= n) {
            double t = n == 0 ? 0.0 : (double) i / n;
            double cx = x0 + dx * t + 0.5;
            double cy = y0 + dy * t + 0.5;
            g.fillEllipse(cx - r, cy - r, 2 * r, 2 * r);
            i = i + 1;
        }
        g.end();
    }

    void eraseSegment(int x0, int y0, int x1, int y1) {
        int size = ERASER_W[eraserIdx];
        Graphics g = doc.img.beginGraphics();
        setCol(g, bg);
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            g.fillRect(x - size / 2, y - size / 2, size, size);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err = err - dy;
                x = x + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                y = y + sy;
            }
        }
        g.end();
    }

    void spray(int x, int y) {
        int rad = SPRAY_R[sprayIdx];
        Graphics g = doc.img.beginGraphics();
        setCol(g, colorFor(button));
        int i = 0;
        while (i < 16) {
            double a = rnd.nextDouble() * 6.283185307179586;
            double r = Math.sqrt(rnd.nextDouble()) * rad;
            g.fillRect(x + (int) Math.floor(Math.cos(a) * r + 0.5), y + (int) Math.floor(Math.sin(a) * r + 0.5), 1, 1);
            i = i + 1;
        }
        g.end();
        modified = true;
    }

    // ------------------------------------------------------------ 形状

    /** 按 shift 约束终点：直线为 45° 倍数，矩形 / 椭圆为正方形。 */
    int constrainX(int sx, int sy, int x, int y, int shift, int kind) {
        if (shift == 0) {
            return x;
        }
        int dx = x - sx;
        int dy = y - sy;
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        if (kind == LINE) {
            if (ax > 2 * ay) {
                return x;
            } else if (ay > 2 * ax) {
                return sx;
            }
            return sx + (dx < 0 ? -1 : 1) * Math.max(ax, ay);
        }
        return sx + (dx < 0 ? -1 : 1) * Math.max(ax, ay);
    }

    int constrainY(int sx, int sy, int x, int y, int shift, int kind) {
        if (shift == 0) {
            return y;
        }
        int dx = x - sx;
        int dy = y - sy;
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        if (kind == LINE) {
            if (ax > 2 * ay) {
                return sy;
            } else if (ay > 2 * ax) {
                return y;
            }
            return sy + (dy < 0 ? -1 : 1) * Math.max(ax, ay);
        }
        return sy + (dy < 0 ? -1 : 1) * Math.max(ax, ay);
    }

    void previewShape(int x, int y, int shift) {
        doc.restoreFrom(doc.base());
        int ex = constrainX(startX, startY, x, y, shift, tool);
        int ey = constrainY(startX, startY, x, y, shift, tool);
        int col = colorFor(button);
        int back = backFor(button);
        Graphics g = doc.img.beginGraphics();
        if (tool == LINE) {
            setCol(g, col);
            lineRaw(g, startX, startY, ex, ey, lineWidth());
        } else {
            drawShape(g, tool, startX, startY, ex, ey, lineWidth(), col, back, fillMode);
        }
        g.end();
    }

    void fillRun(Graphics g, int xa, int xb, int y) {
        if (xb >= xa) {
            g.fillRect(xa, y, xb - xa + 1, 1);
        }
    }

    /** 某个形状在第 y 行的横向范围（结果在 sl / sr）。 */
    boolean span(int kind, int x0, int y0, int x1, int y1, int r, int y) {
        if (y < y0 || y > y1 || x1 < x0) {
            return false;
        }
        if (kind == RECT) {
            sl = x0;
            sr = x1;
            return true;
        }
        if (kind == ELLIPSE) {
            double a = (x1 - x0 + 1) / 2.0;
            double b = (y1 - y0 + 1) / 2.0;
            double cx = x0 + a;
            double cy = y0 + b;
            double dy = (y + 0.5 - cy) / b;
            double q = 1.0 - dy * dy;
            if (q < 0.0) {
                return false;
            }
            double half = a * Math.sqrt(q);
            sl = (int) Math.ceil(cx - half - 0.5);
            sr = (int) Math.floor(cx + half - 0.5);
            return sr >= sl;
        }
        double yc = y + 0.5;
        double inset = 0.0;
        if (yc < y0 + r) {
            double d = (y0 + r) - yc;
            inset = r - Math.sqrt(Math.max(0.0, (double) r * r - d * d));
        } else if (yc > y1 + 1 - r) {
            double d = yc - (y1 + 1 - r);
            inset = r - Math.sqrt(Math.max(0.0, (double) r * r - d * d));
        }
        int ins = (int) Math.floor(inset + 0.5);
        sl = x0 + ins;
        sr = x1 - ins;
        return sr >= sl;
    }

    /**
     * 画矩形 / 椭圆 / 圆角矩形。mode：0 只描边（outCol）；1 描边（outCol）并填充（fillCol）；2 只填充（outCol）。
     * 描边向内收，线宽 t，和经典“画图”一致，全部按像素行画出，不做抗锯齿。
     */
    void drawShape(Graphics g, int kind, int ax, int ay, int bx, int by, int t, int outCol, int fillCol, int mode) {
        int x0 = Math.min(ax, bx);
        int x1 = Math.max(ax, bx);
        int y0 = Math.min(ay, by);
        int y1 = Math.max(ay, by);
        int r = Math.max(2, Math.min(x1 - x0 + 1, y1 - y0 + 1) / 4);
        if (r > 30) {
            r = 30;
        }
        int ix0 = x0 + t;
        int iy0 = y0 + t;
        int ix1 = x1 - t;
        int iy1 = y1 - t;
        int ir = Math.max(0, r - t);
        int y = y0;
        while (y <= y1) {
            if (span(kind, x0, y0, x1, y1, r, y)) {
                int ol = sl;
                int or_ = sr;
                boolean hasIn = false;
                int il = 0;
                int ir2 = 0;
                if (mode != 2 && ix1 >= ix0 && iy1 >= iy0 && span(kind, ix0, iy0, ix1, iy1, ir, y)) {
                    hasIn = true;
                    il = sl;
                    ir2 = sr;
                }
                if (mode == 2) {
                    setCol(g, outCol);
                    fillRun(g, ol, or_, y);
                } else {
                    if (mode == 1 && hasIn) {
                        setCol(g, fillCol);
                        fillRun(g, il, ir2, y);
                    }
                    setCol(g, outCol);
                    if (!hasIn) {
                        fillRun(g, ol, or_, y);
                    } else {
                        fillRun(g, ol, il - 1, y);
                        fillRun(g, ir2 + 1, or_, y);
                    }
                }
            }
            y = y + 1;
        }
    }

    // ------------------------------------------------------------ 曲线

    void curvePoints(Graphics g, int x0, int y0, int ax, int ay, int bx, int by, int x3, int y3, int size) {
        int steps = 60 + (Math.abs(x3 - x0) + Math.abs(y3 - y0)) / 2;
        int px0 = x0;
        int py0 = y0;
        int i = 1;
        while (i <= steps) {
            double t = (double) i / steps;
            double u = 1.0 - t;
            double x = u * u * u * x0 + 3 * u * u * t * ax + 3 * u * t * t * bx + t * t * t * x3;
            double y = u * u * u * y0 + 3 * u * u * t * ay + 3 * u * t * t * by + t * t * t * y3;
            int ix = (int) Math.floor(x + 0.5);
            int iy = (int) Math.floor(y + 0.5);
            if (ix != px0 || iy != py0) {
                lineRaw(g, px0, py0, ix, iy, size);
                px0 = ix;
                py0 = iy;
            }
            i = i + 1;
        }
    }

    void drawCurve(int shift) {
        doc.restoreFrom(doc.base());
        Graphics g = doc.img.beginGraphics();
        setCol(g, colorFor(button));
        if (curveStage == 0) {
            lineRaw(g, startX, startY, endX, endY, lineWidth());
        } else {
            int bx = curveStage == 1 ? c1x : c2x;
            int by = curveStage == 1 ? c1y : c2y;
            curvePoints(g, startX, startY, c1x, c1y, bx, by, endX, endY, lineWidth());
        }
        g.end();
    }

    void curveMove(int x, int y, int shift) {
        if (curveStage == 0) {
            endX = constrainX(startX, startY, x, y, shift, LINE);
            endY = constrainY(startX, startY, x, y, shift, LINE);
        } else if (curveStage == 1) {
            c1x = x;
            c1y = y;
        } else {
            c2x = x;
            c2y = y;
        }
        drawCurve(shift);
    }

    void curveUp(int x, int y, int shift) {
        curveMove(x, y, shift);
        if (curveStage == 0) {
            if (endX == startX && endY == startY) {
                doc.restoreFrom(doc.base());
                doc.dropLast();
                active = false;
                return;
            }
            c1x = endX;
            c1y = endY;
            c2x = endX;
            c2y = endY;
            curveStage = 1;
            active = false;
        } else if (curveStage == 1) {
            c2x = c1x;
            c2y = c1y;
            curveStage = 2;
            active = false;
        } else {
            curveStage = 0;
            active = false;
            modified = true;
        }
    }

    // ------------------------------------------------------------ 多边形

    void polyDown(int x, int y, int btn, int clicks) {
        if (pn == 0) {
            doc.push();
            px[0] = x;
            py[0] = y;
            pn = 1;
            active = false;
            previewPoly(x, y, false);
            return;
        }
        boolean closeToStart = pn >= 3 && Math.abs(x - px[0]) <= 4 && Math.abs(y - py[0]) <= 4;
        if (clicks >= 2 || closeToStart) {
            finishPoly(btn);
            return;
        }
        if (pn < 599 && (px[pn - 1] != x || py[pn - 1] != y)) {
            px[pn] = x;
            py[pn] = y;
            pn = pn + 1;
        }
        previewPoly(x, y, false);
    }

    void finishPoly(int btn) {
        if (pn < 2) {
            cancelGesture();
            return;
        }
        previewPoly(px[pn - 1], py[pn - 1], true);
        pn = 0;
        active = false;
        modified = true;
    }

    void previewPoly(int x, int y, boolean close) {
        doc.restoreFrom(doc.base());
        Graphics g = doc.img.beginGraphics();
        int col = colorFor(button);
        int back = backFor(button);
        int n = pn;
        if (close) {
            if (fillMode != 0 && n >= 3) {
                fillPolygon(g, n, fillMode == 1 ? back : col);
            }
            if (fillMode != 2 || n < 3) {
                setCol(g, col);
                int i = 0;
                while (i < n - 1) {
                    lineRaw(g, px[i], py[i], px[i + 1], py[i + 1], lineWidth());
                    i = i + 1;
                }
                if (n >= 3) {
                    lineRaw(g, px[n - 1], py[n - 1], px[0], py[0], lineWidth());
                }
            }
        } else {
            setCol(g, col);
            int i = 0;
            while (i < n - 1) {
                lineRaw(g, px[i], py[i], px[i + 1], py[i + 1], lineWidth());
                i = i + 1;
            }
            lineRaw(g, px[n - 1], py[n - 1], x, y, lineWidth());
        }
        g.end();
    }

    /** 奇偶规则扫描线填充多边形（px / py 前 n 个点）。 */
    void fillPolygon(Graphics g, int n, int color) {
        setCol(g, color);
        int ymin = py[0];
        int ymax = py[0];
        int i = 1;
        while (i < n) {
            ymin = Math.min(ymin, py[i]);
            ymax = Math.max(ymax, py[i]);
            i = i + 1;
        }
        int[] xs = new int[n + 1];
        int y = ymin;
        while (y <= ymax) {
            double yc = y + 0.5;
            int cnt = 0;
            i = 0;
            while (i < n) {
                int j = (i + 1) % n;
                double ya = py[i];
                double yb = py[j];
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    double x = px[i] + (yc - ya) * (px[j] - px[i]) / (yb - ya);
                    xs[cnt] = (int) Math.floor(x + 0.5);
                    cnt = cnt + 1;
                }
                i = i + 1;
            }
            // 插入排序
            int a = 1;
            while (a < cnt) {
                int v = xs[a];
                int b = a - 1;
                while (b >= 0 && xs[b] > v) {
                    xs[b + 1] = xs[b];
                    b = b - 1;
                }
                xs[b + 1] = v;
                a = a + 1;
            }
            a = 0;
            while (a + 1 < cnt) {
                fillRun(g, xs[a], xs[a + 1] - 1, y);
                a = a + 2;
            }
            y = y + 1;
        }
    }

    // ------------------------------------------------------------ 文字

    /** 在 (x, y) 处按当前前景色绘制多行文字。 */
    void drawText(String text, int x, int y, int size, boolean bold) {
        if (text.length() == 0) {
            return;
        }
        doc.push();
        Graphics g = doc.img.beginGraphics();
        setCol(g, colorFor(1));
        g.setAntialias(true);
        Font f = bold ? Font.systemBold(size) : Font.system(size);
        g.setFont(f);
        int lh = g.getFontMetrics(f).getLineHeight();
        int pos = 0;
        int line = 0;
        while (pos <= text.length()) {
            int eol = text.indexOf("\n", pos);
            if (eol < 0) {
                eol = text.length();
            }
            String s = text.substring(pos, eol);
            if (s.length() > 0) {
                g.drawText(s, x, y + line * lh);
            }
            line = line + 1;
            pos = eol + 1;
        }
        g.end();
        modified = true;
    }

    // ------------------------------------------------------------ 选区

    boolean inSelection(int x, int y) {
        return selActive && x >= selX && y >= selY && x < selX + selW && y < selY + selH;
    }

    void selectDown(int x, int y, int alt) {
        if (inSelection(x, y)) {
            lift(alt != 0);
            selMoving = true;
            grabX = x - selX;
            grabY = y - selY;
            active = true;
            return;
        }
        commitSelection();
        selMaking = true;
        active = true;
        startX = Pix.clamp(x, 0, doc.w - 1);
        startY = Pix.clamp(y, 0, doc.h - 1);
        selX = startX;
        selY = startY;
        selW = 1;
        selH = 1;
        selActive = true;
    }

    void selectMove(int x, int y) {
        if (selMoving) {
            selX = x - grabX;
            selY = y - grabY;
        } else if (selMaking) {
            int cx = Pix.clamp(x, 0, doc.w - 1);
            int cy = Pix.clamp(y, 0, doc.h - 1);
            selX = Math.min(startX, cx);
            selY = Math.min(startY, cy);
            selW = Math.abs(cx - startX) + 1;
            selH = Math.abs(cy - startY) + 1;
        }
    }

    void selectUp(int x, int y) {
        if (selMaking) {
            selectMove(x, y);
            if (selW <= 1 && selH <= 1 && x == startX && y == startY) {
                selActive = false;
                selW = 0;
                selH = 0;
            }
        }
        selMaking = false;
        selMoving = false;
        active = false;
    }

    Image extract(int x, int y, int w, int h) {
        Image n = Image.create(w, h);
        Graphics g = n.beginGraphics();
        g.setColorRgba(Pix.r(bg), Pix.g(bg), Pix.b(bg), 255);
        g.fillRect(0, 0, w, h);
        g.drawImageRegion(doc.img, x, y, w, h, 0, 0, w, h);
        g.end();
        return n;
    }

    /** 把选区内容“拿起来”成为浮动图像；copy 为 true 时保留原处内容。 */
    void lift(boolean copy) {
        if (selImg != null || !selActive) {
            return;
        }
        doc.push();
        selImg = extract(selX, selY, selW, selH);
        if (!copy) {
            Graphics g = doc.img.beginGraphics();
            setCol(g, bg);
            g.fillRect(selX, selY, selW, selH);
            g.end();
        }
        modified = true;
    }

    /** 浮动图像落到文档上（选区仍保留）。 */
    void flattenFloating() {
        if (selImg != null) {
            Graphics g = doc.img.beginGraphics();
            g.drawImage(selImg, selX, selY);
            g.end();
            selImg.dispose();
            selImg = null;
            modified = true;
        }
    }

    void commitSelection() {
        flattenFloating();
        selActive = false;
        selMaking = false;
        selMoving = false;
        selW = 0;
        selH = 0;
    }

    void selectAll() {
        endEdits();
        tool = SELECT;
        selActive = true;
        selX = 0;
        selY = 0;
        selW = doc.w;
        selH = doc.h;
    }

    void copySelection() {
        if (!selActive) {
            return;
        }
        if (clip != null) {
            clip.dispose();
        }
        if (selImg != null) {
            clip = Doc.copyOf(selImg);
        } else {
            clip = extract(selX, selY, selW, selH);
        }
    }

    void deleteSelection() {
        if (!selActive) {
            return;
        }
        if (selImg != null) {
            selImg.dispose();
            selImg = null;
        } else {
            doc.push();
            Graphics g = doc.img.beginGraphics();
            setCol(g, bg);
            g.fillRect(selX, selY, selW, selH);
            g.end();
        }
        selActive = false;
        selW = 0;
        selH = 0;
        modified = true;
    }

    void cutSelection() {
        copySelection();
        deleteSelection();
    }

    boolean hasClip() {
        return clip != null;
    }

    /** 把剪贴板内容作为浮动选区贴到 (x, y)。 */
    void paste(int x, int y) {
        if (clip == null) {
            return;
        }
        endEdits();
        tool = SELECT;
        doc.push();
        selImg = Doc.copyOf(clip);
        selW = clip.getWidth();
        selH = clip.getHeight();
        selX = Math.max(0, Math.min(x, doc.w - 1));
        selY = Math.max(0, Math.min(y, doc.h - 1));
        selActive = true;
        modified = true;
    }

    void nudge(int dx, int dy) {
        if (!selActive) {
            return;
        }
        lift(false);
        selX = selX + dx;
        selY = selY + dy;
    }

    void cropToSelection() {
        if (!selActive) {
            return;
        }
        flattenFloating();
        int x = Pix.clamp(selX, 0, doc.w - 1);
        int y = Pix.clamp(selY, 0, doc.h - 1);
        int w = Math.min(selW, doc.w - x);
        int h = Math.min(selH, doc.h - y);
        selActive = false;
        selW = 0;
        selH = 0;
        if (w > 0 && h > 0) {
            doc.crop(x, y, w, h);
            modified = true;
        }
    }

    // ------------------------------------------------------------ 撤销 / 重做

    boolean undo() {
        endEdits();
        boolean ok = doc.undoStep();
        if (ok) {
            modified = true;
        }
        return ok;
    }

    boolean redo() {
        endEdits();
        boolean ok = doc.redoStep();
        if (ok) {
            modified = true;
        }
        return ok;
    }

    /** 载入 / 新建文档前丢弃所有进行中的状态。 */
    void reset() {
        pn = 0;
        curveStage = 0;
        active = false;
        selMaking = false;
        selMoving = false;
        selActive = false;
        selW = 0;
        selH = 0;
        if (selImg != null) {
            selImg.dispose();
            selImg = null;
        }
    }

    void swapColors() {
        int t = fg;
        fg = bg;
        bg = t;
    }
}
