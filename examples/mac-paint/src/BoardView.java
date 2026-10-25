import jnative.ui.*;
import jnative.graphics.*;

/** 画布视图：缩放、滚动、图像坐标换算，以及画布（图像 + 选区 + 网格 + 滚动条）的绘制。 */
public class BoardView {
    static int MARGIN = 12;
    static int[] LEVELS = new int[] {25, 50, 100, 200, 300, 400, 600, 800, 1200, 1600};

    Canvas canvas;
    Painter p;
    int zoom = 100;
    int scrollX = 0;
    int scrollY = 0;
    boolean grid = true;
    boolean hovering = false;
    int hoverX = 0;
    int hoverY = 0;

    // 滚动条拖动
    int barDrag = 0;
    int barGrab = 0;

    BoardView(Canvas c, Painter painter) {
        canvas = c;
        p = painter;
        c.setHiDpi(true);
        c.onPaint(g -> paint(g));
    }

    int viewW() {
        return canvas.width;
    }

    int viewH() {
        return canvas.height;
    }

    int docX(int mx) {
        return (int) Math.floor((mx - (MARGIN - scrollX)) * 100.0 / zoom);
    }

    int docY(int my) {
        return (int) Math.floor((my - (MARGIN - scrollY)) * 100.0 / zoom);
    }

    double screenX(double dx) {
        return MARGIN - scrollX + dx * zoom / 100.0;
    }

    double screenY(double dy) {
        return MARGIN - scrollY + dy * zoom / 100.0;
    }

    int contentW() {
        return (int) Math.ceil(p.doc.w * zoom / 100.0) + 2 * MARGIN;
    }

    int contentH() {
        return (int) Math.ceil(p.doc.h * zoom / 100.0) + 2 * MARGIN;
    }

    void clampScroll() {
        int maxX = Math.max(0, contentW() - viewW());
        int maxY = Math.max(0, contentH() - viewH());
        scrollX = Pix.clamp(scrollX, 0, maxX);
        scrollY = Pix.clamp(scrollY, 0, maxY);
    }

    void repaint() {
        canvas.repaint();
    }

    void setZoom(int z, int anchorX, int anchorY) {
        z = Pix.clamp(z, 25, 1600);
        if (z == zoom) {
            return;
        }
        double ax = (anchorX - (MARGIN - scrollX)) * 100.0 / zoom;
        double ay = (anchorY - (MARGIN - scrollY)) * 100.0 / zoom;
        zoom = z;
        scrollX = (int) Math.floor(MARGIN + ax * zoom / 100.0 - anchorX);
        scrollY = (int) Math.floor(MARGIN + ay * zoom / 100.0 - anchorY);
        clampScroll();
        repaint();
    }

    int nextLevel(int dir) {
        int i = 0;
        if (dir > 0) {
            while (i < LEVELS.length) {
                if (LEVELS[i] > zoom) {
                    return LEVELS[i];
                }
                i = i + 1;
            }
            return LEVELS[LEVELS.length - 1];
        }
        i = LEVELS.length - 1;
        while (i >= 0) {
            if (LEVELS[i] < zoom) {
                return LEVELS[i];
            }
            i = i - 1;
        }
        return LEVELS[0];
    }

    void zoomStep(int dir, int anchorX, int anchorY) {
        setZoom(nextLevel(dir), anchorX, anchorY);
    }

    void zoomCenter(int dir) {
        zoomStep(dir, viewW() / 2, viewH() / 2);
    }

    /** 让文档缩放后居中显示在视图里（打开 / 新建后用）。 */
    void reset() {
        scrollX = 0;
        scrollY = 0;
        clampScroll();
        repaint();
    }

    void wheel(MouseEvent e) {
        if (e.metaDown != 0 || e.ctrlDown != 0) {
            if (e.wheelDelta > 0 || e.pixelDelta > 0) {
                zoomStep(1, e.x, e.y);
            } else if (e.wheelDelta < 0 || e.pixelDelta < 0) {
                zoomStep(-1, e.x, e.y);
            }
            return;
        }
        int dx = 0;
        int dy = 0;
        if (e.pixelDelta != 0) {
            if (e.horizontal != 0) {
                dx = e.pixelDelta;
            } else {
                dy = e.pixelDelta;
            }
        } else if (e.horizontal != 0) {
            dx = e.wheelDelta * 40;
        } else {
            dy = e.wheelDelta * 60;
        }
        if (e.shiftDown != 0 && dx == 0) {
            dx = dy;
            dy = 0;
        }
        scrollX = scrollX - dx;
        scrollY = scrollY - dy;
        clampScroll();
        repaint();
    }

    // ------------------------------------------------------------ 滚动条

    boolean needH() {
        return contentW() > viewW();
    }

    boolean needV() {
        return contentH() > viewH();
    }

    int thumbLen(int content, int view) {
        return Math.max(28, view * view / content);
    }

    int thumbPos(int scroll, int content, int view) {
        int len = thumbLen(content, view);
        int range = content - view;
        if (range <= 0) {
            return 0;
        }
        return (int) ((long) scroll * (view - len) / range);
    }

    /** 点在滚动条滑块上则开始拖动并返回 true。 */
    boolean barDown(int mx, int my) {
        if (needV() && mx >= viewW() - 14) {
            int len = thumbLen(contentH(), viewH());
            int pos = thumbPos(scrollY, contentH(), viewH());
            if (my >= pos && my < pos + len) {
                barDrag = 2;
                barGrab = my - pos;
            } else {
                scrollY = scrollY + (my < pos ? -viewH() : viewH()) * 9 / 10;
                clampScroll();
                repaint();
                barDrag = 0;
            }
            return true;
        }
        if (needH() && my >= viewH() - 14) {
            int len = thumbLen(contentW(), viewW());
            int pos = thumbPos(scrollX, contentW(), viewW());
            if (mx >= pos && mx < pos + len) {
                barDrag = 1;
                barGrab = mx - pos;
            } else {
                scrollX = scrollX + (mx < pos ? -viewW() : viewW()) * 9 / 10;
                clampScroll();
                repaint();
                barDrag = 0;
            }
            return true;
        }
        return false;
    }

    boolean barMove(int mx, int my) {
        if (barDrag == 0) {
            return false;
        }
        if (barDrag == 2) {
            int len = thumbLen(contentH(), viewH());
            int span = viewH() - len;
            if (span > 0) {
                scrollY = (int) ((long) (my - barGrab) * (contentH() - viewH()) / span);
            }
        } else {
            int len = thumbLen(contentW(), viewW());
            int span = viewW() - len;
            if (span > 0) {
                scrollX = (int) ((long) (mx - barGrab) * (contentW() - viewW()) / span);
            }
        }
        clampScroll();
        repaint();
        return true;
    }

    boolean barUp() {
        boolean was = barDrag != 0;
        barDrag = 0;
        return was;
    }

    // ------------------------------------------------------------ 绘制

    void paint(Graphics g) {
        int vw = viewW();
        int vh = viewH();
        clampScroll();
        g.setColorRgba(0xCF, 0xD6, 0xDF, 255);
        g.fillRect(0, 0, vw, vh);
        Doc doc = p.doc;
        double ox = screenX(0);
        double oy = screenY(0);
        double dw = doc.w * zoom / 100.0;
        double dh = doc.h * zoom / 100.0;
        g.setColorRgba(0, 0, 0, 38);
        g.fillRect(ox + 3, oy + 3, dw, dh);
        g.setColorRgba(255, 255, 255, 255);
        g.fillRect(ox, oy, dw, dh);
        blit(g, doc.img, 0, 0, doc.w, doc.h, 0, 0);
        if (p.selImg != null) {
            blit(g, p.selImg, 0, 0, p.selImg.getWidth(), p.selImg.getHeight(), p.selX, p.selY);
        }
        if (grid && zoom >= 400) {
            paintGrid(g);
        }
        g.setColorRgba(0x6B, 0x72, 0x80, 255);
        g.drawRect(ox - 0.5, oy - 0.5, dw + 1, dh + 1);
        if (p.selActive) {
            double sx = screenX(p.selX);
            double sy = screenY(p.selY);
            marquee(g, sx, sy, p.selW * zoom / 100.0, p.selH * zoom / 100.0);
        }
        if (hovering && !p.active) {
            paintBrushCursor(g);
        }
        paintBars(g);
    }

    /** 把 img 的 (sx, sy, sw, sh) 区域画到文档坐标 (dx, dy)，只处理可见部分。 */
    void blit(Graphics g, Image img, int sx, int sy, int sw, int sh, int dx, int dy) {
        double z = zoom / 100.0;
        int vw = viewW();
        int vh = viewH();
        // 可见的源像素范围
        int x0 = (int) Math.floor((0 - screenX(dx)) / z);
        int y0 = (int) Math.floor((0 - screenY(dy)) / z);
        int x1 = (int) Math.ceil((vw - screenX(dx)) / z);
        int y1 = (int) Math.ceil((vh - screenY(dy)) / z);
        x0 = Pix.clamp(x0, 0, sw);
        y0 = Pix.clamp(y0, 0, sh);
        x1 = Pix.clamp(x1, 0, sw);
        y1 = Pix.clamp(y1, 0, sh);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        g.drawImageRegion(img, sx + x0, sy + y0, x1 - x0, y1 - y0, screenX(dx + x0), screenY(dy + y0),
                (x1 - x0) * z, (y1 - y0) * z);
    }

    void paintGrid(Graphics g) {
        double z = zoom / 100.0;
        Doc doc = p.doc;
        int vw = viewW();
        int vh = viewH();
        int x0 = Math.max(0, (int) Math.floor((0 - screenX(0)) / z));
        int x1 = Math.min(doc.w, (int) Math.ceil((vw - screenX(0)) / z));
        int y0 = Math.max(0, (int) Math.floor((0 - screenY(0)) / z));
        int y1 = Math.min(doc.h, (int) Math.ceil((vh - screenY(0)) / z));
        g.setColorRgba(0, 0, 0, 36);
        double top = screenY(y0);
        double bottom = screenY(y1);
        double left = screenX(x0);
        double right = screenX(x1);
        int x = x0;
        while (x <= x1) {
            double sx = Math.floor(screenX(x));
            g.fillRect(sx, top, 1, bottom - top);
            x = x + 1;
        }
        int y = y0;
        while (y <= y1) {
            double sy = Math.floor(screenY(y));
            g.fillRect(left, sy, right - left, 1);
            y = y + 1;
        }
    }

    /** 选区虚线框：白底黑虚线，任何底色上都看得清。 */
    void marquee(Graphics g, double x, double y, double w, double h) {
        g.setColorRgba(255, 255, 255, 255);
        g.fillRect(x, y, w, 1);
        g.fillRect(x, y + h - 1, w, 1);
        g.fillRect(x, y, 1, h);
        g.fillRect(x + w - 1, y, 1, h);
        g.setColorRgba(0, 0, 0, 255);
        double t = 0;
        while (t < w) {
            double seg = Math.min(4.0, w - t);
            g.fillRect(x + t, y, seg, 1);
            g.fillRect(x + t, y + h - 1, seg, 1);
            t = t + 8;
        }
        t = 0;
        while (t < h) {
            double seg = Math.min(4.0, h - t);
            g.fillRect(x, y + t, 1, seg);
            g.fillRect(x + w - 1, y + t, 1, seg);
            t = t + 8;
        }
    }

    /** 橡皮擦 / 刷子 / 喷枪在光标处的大小提示。 */
    void paintBrushCursor(Graphics g) {
        int size = 0;
        boolean round = true;
        if (p.tool == Painter.ERASER) {
            size = Painter.ERASER_W[p.eraserIdx];
            round = false;
        } else if (p.tool == Painter.BRUSH) {
            size = Painter.BRUSH_W[p.brushIdx];
        } else if (p.tool == Painter.AIRBRUSH) {
            size = Painter.SPRAY_R[p.sprayIdx] * 2;
        } else {
            return;
        }
        double z = zoom / 100.0;
        double w = size * z;
        double cx = screenX(hoverX + 0.5);
        double cy = screenY(hoverY + 0.5);
        double x = cx - w / 2;
        double y = cy - w / 2;
        g.setAntialias(true);
        g.setLineWidth(1);
        g.setColorRgba(255, 255, 255, 220);
        if (round) {
            g.drawEllipse(x - 1, y - 1, w + 2, w + 2);
        } else {
            g.drawRect(x - 1, y - 1, w + 2, w + 2);
        }
        g.setColorRgba(0, 0, 0, 220);
        if (round) {
            g.drawEllipse(x, y, w, w);
        } else {
            g.drawRect(x, y, w, w);
        }
        g.setAntialias(false);
    }

    void paintBars(Graphics g) {
        int vw = viewW();
        int vh = viewH();
        g.setAntialias(true);
        if (needV()) {
            int len = thumbLen(contentH(), vh);
            int pos = thumbPos(scrollY, contentH(), vh);
            g.setColorRgba(0, 0, 0, barDrag == 2 ? 110 : 70);
            g.fillRoundRect(vw - 10, pos + 2, 6, len - 4, 3);
        }
        if (needH()) {
            int len = thumbLen(contentW(), vw);
            int pos = thumbPos(scrollX, contentW(), vw);
            g.setColorRgba(0, 0, 0, barDrag == 1 ? 110 : 70);
            g.fillRoundRect(pos + 2, vh - 10, len - 4, 6, 3);
        }
        g.setAntialias(false);
    }
}
