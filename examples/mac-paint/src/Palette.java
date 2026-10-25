import jnative.ui.*;
import jnative.graphics.*;

/** 底部调色板：前景 / 背景色、28 种标准色、10 个自定义色、“编辑颜色…”按钮。左键设前景色，右键设背景色。 */
public class Palette {
    static int SW = 24;
    static int PITCH = 26;
    static int X0 = 96;
    static int Y0 = 8;
    static int CUSTOM_X = 480;
    static int EDIT_X = 640;

    Canvas canvas;
    Painter p;
    int[] std = new int[28];
    int[] custom = new int[10];
    Runnable onChange;
    Runnable onEdit;
    /** 1 前景色，2 背景色，3 自定义色格（editSlot）。 */
    int editTarget = 0;
    int editSlot = 0;
    int hoverColor = -1;
    boolean hoverValid = false;

    Palette(Canvas c, Painter painter) {
        canvas = c;
        p = painter;
        c.setHiDpi(true);
        c.onPaint(g -> paint(g));
        c.onMouseDown(e -> down(e));
        c.onMouseMove(e -> moved(e));
        c.onMouseExit(e -> exited());
        fillStd();
        int i = 0;
        while (i < 10) {
            custom[i] = Pix.WHITE;
            i = i + 1;
        }
    }

    void set(int i, int r, int g, int b) {
        std[i] = Pix.rgb(r, g, b);
    }

    void fillStd() {
        set(0, 0, 0, 0);
        set(1, 128, 128, 128);
        set(2, 128, 0, 0);
        set(3, 128, 128, 0);
        set(4, 0, 128, 0);
        set(5, 0, 128, 128);
        set(6, 0, 0, 128);
        set(7, 128, 0, 128);
        set(8, 128, 128, 64);
        set(9, 0, 64, 64);
        set(10, 0, 128, 255);
        set(11, 0, 64, 128);
        set(12, 64, 0, 255);
        set(13, 128, 64, 0);
        set(14, 255, 255, 255);
        set(15, 192, 192, 192);
        set(16, 255, 0, 0);
        set(17, 255, 255, 0);
        set(18, 0, 255, 0);
        set(19, 0, 255, 255);
        set(20, 0, 0, 255);
        set(21, 255, 0, 255);
        set(22, 255, 255, 128);
        set(23, 0, 255, 128);
        set(24, 128, 255, 255);
        set(25, 128, 128, 255);
        set(26, 255, 0, 128);
        set(27, 255, 128, 64);
    }

    /** 命中的色格：0..27 标准色，100..109 自定义色，-1 无。 */
    int swatchAt(int mx, int my) {
        int col = (mx - X0) / PITCH;
        int row = (my - Y0) / PITCH;
        if (mx >= X0 && my >= Y0 && col >= 0 && col < 14 && row >= 0 && row < 2 && (mx - X0) % PITCH < SW
                && (my - Y0) % PITCH < SW) {
            return row * 14 + col;
        }
        int cc = (mx - CUSTOM_X) / PITCH;
        if (mx >= CUSTOM_X && my >= Y0 && cc >= 0 && cc < 5 && row >= 0 && row < 2 && (mx - CUSTOM_X) % PITCH < SW
                && (my - Y0) % PITCH < SW) {
            return 100 + row * 5 + cc;
        }
        return -1;
    }

    int colorOf(int idx) {
        if (idx >= 100) {
            return custom[idx - 100];
        }
        return std[idx];
    }

    void down(MouseEvent e) {
        int s = swatchAt(e.x, e.y);
        if (s >= 0) {
            if (s >= 100 && e.clickCount >= 2) {
                request(3, s - 100);
                return;
            }
            int c = colorOf(s);
            if (e.button == 3) {
                p.bg = c;
            } else {
                p.fg = c;
            }
            changed();
            return;
        }
        if (e.x >= EDIT_X && e.x < EDIT_X + 96 && e.y >= Y0 && e.y < Y0 + 2 * PITCH - 2) {
            request(1, 0);
            return;
        }
        if (e.x >= 32 && e.x < 68 && e.y >= 24 && e.y < 54) {
            request(2, 0);
            return;
        }
        if (e.x >= 12 && e.x < 48 && e.y >= 8 && e.y < 38) {
            request(1, 0);
        }
    }

    void request(int target, int slot) {
        editTarget = target;
        editSlot = slot;
        if (onEdit != null) {
            onEdit.run();
        }
    }

    void moved(MouseEvent e) {
        int s = swatchAt(e.x, e.y);
        if (s >= 0) {
            hoverColor = colorOf(s);
            hoverValid = true;
        } else {
            hoverValid = false;
        }
        canvas.repaint();
        if (onChange != null) {
            onChange.run();
        }
    }

    void exited() {
        hoverValid = false;
        canvas.repaint();
        if (onChange != null) {
            onChange.run();
        }
    }

    void changed() {
        canvas.repaint();
        if (onChange != null) {
            onChange.run();
        }
    }

    void swatch(Graphics g, int x, int y, int color) {
        g.setColorRgba(0x8A, 0x90, 0x9C, 255);
        g.fillRect(x, y, SW, SW);
        g.setColorRgba(Pix.r(color), Pix.g(color), Pix.b(color), 255);
        g.fillRect(x + 1, y + 1, SW - 2, SW - 2);
    }

    void paint(Graphics g) {
        int w = canvas.width;
        int h = canvas.height;
        g.setColorRgba(0xF2, 0xF2, 0xF7, 255);
        g.fillRect(0, 0, w, h);
        g.setColorRgba(0xD9, 0xDB, 0xE2, 255);
        g.fillRect(0, 0, w, 1);
        // 背景色在后，前景色在前
        swatch2(g, 32, 24, p.bg);
        swatch2(g, 12, 8, p.fg);
        int i = 0;
        while (i < 28) {
            swatch(g, X0 + (i % 14) * PITCH, Y0 + (i / 14) * PITCH, std[i]);
            i = i + 1;
        }
        g.setFont(Font.system(11));
        g.setColorRgba(0x6B, 0x72, 0x80, 255);
        g.drawText("自定义", CUSTOM_X, Y0 + 2 * PITCH + 1);
        i = 0;
        while (i < 10) {
            swatch(g, CUSTOM_X + (i % 5) * PITCH, Y0 + (i / 5) * PITCH, custom[i]);
            i = i + 1;
        }
        // 编辑颜色按钮：彩虹条 + 文字
        g.setAntialias(true);
        g.setColorRgba(0xFF, 0xFF, 0xFF, 255);
        g.fillRoundRect(EDIT_X, Y0, 96, 2 * PITCH - 2, 6);
        g.setColorRgba(0xC4, 0xC8, 0xD0, 255);
        g.drawRoundRect(EDIT_X, Y0, 96, 2 * PITCH - 2, 6);
        int k = 0;
        while (k < 6) {
            int c = rainbow(k);
            g.setColorRgba(Pix.r(c), Pix.g(c), Pix.b(c), 255);
            g.fillRect(EDIT_X + 12 + k * 12, Y0 + 7, 12, 10);
            k = k + 1;
        }
        g.setAntialias(false);
        g.setFont(Font.system(12));
        g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
        g.drawText("编辑颜色…", EDIT_X + 16, Y0 + 24);
        if (hoverValid) {
            g.setFont(Font.system(11));
            g.setColorRgba(0x6B, 0x72, 0x80, 255);
            g.drawText(Pix.hex(hoverColor), X0, Y0 + 2 * PITCH + 1);
        }
    }

    int rainbow(int k) {
        if (k == 0) {
            return Pix.rgb(235, 64, 52);
        } else if (k == 1) {
            return Pix.rgb(244, 160, 40);
        } else if (k == 2) {
            return Pix.rgb(240, 217, 60);
        } else if (k == 3) {
            return Pix.rgb(76, 175, 80);
        } else if (k == 4) {
            return Pix.rgb(47, 111, 237);
        }
        return Pix.rgb(142, 68, 173);
    }

    void swatch2(Graphics g, int x, int y, int color) {
        g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
        g.fillRect(x, y, 36, 30);
        g.setColorRgba(255, 255, 255, 255);
        g.fillRect(x + 1, y + 1, 34, 28);
        g.setColorRgba(Pix.r(color), Pix.g(color), Pix.b(color), 255);
        g.fillRect(x + 3, y + 3, 30, 24);
    }
}
