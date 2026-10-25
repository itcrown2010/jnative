import jnative.graphics.*;

/**
 * 画布文档：一张不透明的位图，加上撤销 / 重做栈。绘图操作先调用 {@link #push()} 留一份快照，
 * 改变尺寸的操作（翻转、旋转、缩放、裁剪）直接把旧图像放进撤销栈、换上新图像。
 */
public class Doc {
    static int MAX_UNDO = 30;

    Image img;
    int w;
    int h;
    ArrayList<Image> undo = new ArrayList<Image>();
    ArrayList<Image> redo = new ArrayList<Image>();

    Doc(int width, int height) {
        w = width;
        h = height;
        img = Image.create(width, height);
        fill(Pix.WHITE);
    }

    void fill(int color) {
        Graphics g = img.beginGraphics();
        g.setColorRgba(Pix.r(color), Pix.g(color), Pix.b(color), 255);
        g.fillRect(0, 0, w, h);
        g.end();
    }

    static Image copyOf(Image src) {
        Image c = Image.create(src.getWidth(), src.getHeight());
        Graphics g = c.beginGraphics();
        g.drawImage(src, 0, 0);
        g.end();
        return c;
    }

    /** 把当前图像放回 dst（用于形状预览：先还原再重画）。 */
    void restoreFrom(Image base) {
        Graphics g = img.beginGraphics();
        g.drawImage(base, 0, 0);
        g.end();
    }

    void clearRedo() {
        int i = 0;
        while (i < redo.size()) {
            redo.get(i).dispose();
            i = i + 1;
        }
        redo.clear();
    }

    void trimUndo() {
        while (undo.size() > MAX_UNDO) {
            undo.get(0).dispose();
            undo.remove(0);
        }
    }

    /** 记录修改前的快照（原地绘图前调用）。 */
    void push() {
        undo.add(copyOf(img));
        clearRedo();
        trimUndo();
    }

    /** 丢弃最近一次 push（手势没有产生任何改动时）。 */
    void dropLast() {
        if (undo.size() > 0) {
            undo.get(undo.size() - 1).dispose();
            undo.remove(undo.size() - 1);
        }
    }

    Image base() {
        return undo.get(undo.size() - 1);
    }

    boolean canUndo() {
        return undo.size() > 0;
    }

    boolean canRedo() {
        return redo.size() > 0;
    }

    boolean undoStep() {
        if (undo.size() == 0) {
            return false;
        }
        Image prev = undo.remove(undo.size() - 1);
        redo.add(img);
        img = prev;
        w = img.getWidth();
        h = img.getHeight();
        return true;
    }

    boolean redoStep() {
        if (redo.size() == 0) {
            return false;
        }
        Image next = redo.remove(redo.size() - 1);
        undo.add(img);
        img = next;
        w = img.getWidth();
        h = img.getHeight();
        return true;
    }

    /** 用 ni 替换当前图像（旧图像进入撤销栈）。 */
    void replaceWith(Image ni) {
        undo.add(img);
        clearRedo();
        trimUndo();
        img = ni;
        w = ni.getWidth();
        h = ni.getHeight();
    }

    Image blank(int nw, int nh, int color) {
        Image n = Image.create(nw, nh);
        Graphics g = n.beginGraphics();
        g.setColorRgba(Pix.r(color), Pix.g(color), Pix.b(color), 255);
        g.fillRect(0, 0, nw, nh);
        g.end();
        return n;
    }

    void flipHorizontal() {
        Image n = Image.create(w, h);
        int y = 0;
        while (y < h) {
            int x = 0;
            while (x < w) {
                n.setPixel(w - 1 - x, y, img.getPixel(x, y));
                x = x + 1;
            }
            y = y + 1;
        }
        replaceWith(n);
    }

    void flipVertical() {
        Image n = Image.create(w, h);
        int y = 0;
        while (y < h) {
            int x = 0;
            while (x < w) {
                n.setPixel(x, h - 1 - y, img.getPixel(x, y));
                x = x + 1;
            }
            y = y + 1;
        }
        replaceWith(n);
    }

    /** quarter: 1 = 顺时针 90°，3 = 逆时针 90°，2 = 180°。 */
    void rotate(int quarter) {
        int nw = w;
        int nh = h;
        if (quarter == 1 || quarter == 3) {
            nw = h;
            nh = w;
        }
        Image n = Image.create(nw, nh);
        int y = 0;
        while (y < h) {
            int x = 0;
            while (x < w) {
                int c = img.getPixel(x, y);
                if (quarter == 1) {
                    n.setPixel(h - 1 - y, x, c);
                } else if (quarter == 3) {
                    n.setPixel(y, w - 1 - x, c);
                } else {
                    n.setPixel(w - 1 - x, h - 1 - y, c);
                }
                x = x + 1;
            }
            y = y + 1;
        }
        replaceWith(n);
    }

    void invert() {
        push();
        int y = 0;
        while (y < h) {
            int x = 0;
            while (x < w) {
                int c = img.getPixel(x, y);
                img.setPixel(x, y, Pix.rgb(255 - Pix.r(c), 255 - Pix.g(c), 255 - Pix.b(c)));
                x = x + 1;
            }
            y = y + 1;
        }
    }

    /** 画布大小：保留左上角内容，新增区域用 color 填充。 */
    void resizeCanvas(int nw, int nh, int color) {
        Image n = blank(nw, nh, color);
        Graphics g = n.beginGraphics();
        g.drawImageRegion(img, 0, 0, Math.min(w, nw), Math.min(h, nh), 0, 0, Math.min(w, nw), Math.min(h, nh));
        g.end();
        replaceWith(n);
    }

    /** 缩放图像（最近邻）。 */
    void scaleTo(int nw, int nh) {
        Image n = Image.create(nw, nh);
        Graphics g = n.beginGraphics();
        g.drawImageRegion(img, 0, 0, w, h, 0, 0, nw, nh);
        g.end();
        replaceWith(n);
    }

    void crop(int x, int y, int cw, int ch) {
        Image n = Image.create(cw, ch);
        Graphics g = n.beginGraphics();
        g.drawImageRegion(img, x, y, cw, ch, 0, 0, cw, ch);
        g.end();
        replaceWith(n);
    }

    /** 载入新图像（不进撤销栈，清空历史）。 */
    void load(Image ni) {
        int i = 0;
        while (i < undo.size()) {
            undo.get(i).dispose();
            i = i + 1;
        }
        undo.clear();
        clearRedo();
        img.dispose();
        img = ni;
        w = ni.getWidth();
        h = ni.getHeight();
    }
}
