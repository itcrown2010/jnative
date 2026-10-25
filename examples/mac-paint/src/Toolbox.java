import jnative.ui.*;
import jnative.graphics.*;

/** 左侧工具箱：两列工具图标（矢量绘制）+ 随工具变化的选项区（线宽 / 笔刷大小 / 填充方式 / 字号）。 */
public class Toolbox {
    static int CELL_W = 40;
    static int CELL_H = 36;
    static int LEFT = 6;
    static int TOP = 8;

    // 工具在 2 × 8 网格里的顺序
    static int[] ORDER = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, -1};

    Canvas canvas;
    Painter p;
    int hover = -1;
    Runnable onChange;
    Runnable onHint;
    String hintText = "";

    Toolbox(Canvas c, Painter painter) {
        canvas = c;
        p = painter;
        c.setHiDpi(true);
        c.onPaint(g -> paint(g));
        c.onMouseDown(e -> down(e));
        c.onMouseMove(e -> moved(e));
        c.onMouseExit(e -> exited());
    }

    int optionsTop() {
        return TOP + 8 * CELL_H + 12;
    }

    int toolAt(int mx, int my) {
        int col = (mx - LEFT) / CELL_W;
        int row = (my - TOP) / CELL_H;
        if (mx < LEFT || my < TOP || col < 0 || col > 1 || row < 0 || row > 7) {
            return -1;
        }
        return ORDER[row * 2 + col];
    }

    void changed() {
        canvas.repaint();
        if (onChange != null) {
            onChange.run();
        }
    }

    void moved(MouseEvent e) {
        int t = toolAt(e.x, e.y);
        if (t != hover) {
            hover = t;
            canvas.repaint();
        }
        if (onHint != null) {
            hintText = t >= 0 ? Painter.toolName(t) + "：" + Painter.toolHint(t) : "";
            onHint.run();
        }
    }

    void exited() {
        hover = -1;
        canvas.repaint();
        hintText = "";
        if (onHint != null) {
            onHint.run();
        }
    }

    // ------------------------------------------------------------ 选项区

    /** 选项种类：0 无，1 橡皮，2 刷子，3 喷枪，4 线宽，5 线宽+填充，6 文字。 */
    int optionKind() {
        int t = p.tool;
        if (t == Painter.ERASER) {
            return 1;
        } else if (t == Painter.BRUSH) {
            return 2;
        } else if (t == Painter.AIRBRUSH) {
            return 3;
        } else if (t == Painter.LINE || t == Painter.CURVE) {
            return 4;
        } else if (t == Painter.RECT || t == Painter.ELLIPSE || t == Painter.ROUNDRECT || t == Painter.POLY) {
            return 5;
        } else if (t == Painter.TEXT) {
            return 6;
        }
        return 0;
    }

    int rowH() {
        return 26;
    }

    void down(MouseEvent e) {
        int t = toolAt(e.x, e.y);
        if (t >= 0) {
            p.setTool(t);
            changed();
            return;
        }
        int oy = optionsTop();
        int kind = optionKind();
        if (kind == 0 || e.y < oy) {
            return;
        }
        int row = (e.y - oy - 22) / rowH();
        if (e.y < oy + 22 || row < 0) {
            return;
        }
        if (kind == 1 && row < 5) {
            p.eraserIdx = row;
        } else if (kind == 2 && row < 5) {
            p.brushIdx = row;
        } else if (kind == 3 && row < 5) {
            p.sprayIdx = row;
        } else if (kind == 4 && row < 5) {
            p.widthIdx = row;
        } else if (kind == 5) {
            if (row < 3) {
                p.fillMode = row;
            } else if (row >= 3 && row < 8) {
                p.widthIdx = row - 3;
            }
        } else if (kind == 6) {
            if (row < 5) {
                p.textIdx = row;
            } else if (row == 5) {
                p.textBold = !p.textBold;
            }
        }
        changed();
    }

    // ------------------------------------------------------------ 绘制

    void paint(Graphics g) {
        int w = canvas.width;
        int h = canvas.height;
        g.setColorRgba(0xF2, 0xF2, 0xF7, 255);
        g.fillRect(0, 0, w, h);
        g.setColorRgba(0xD9, 0xDB, 0xE2, 255);
        g.fillRect(w - 1, 0, 1, h);
        g.setAntialias(true);
        int i = 0;
        while (i < 16) {
            int t = ORDER[i];
            if (t >= 0) {
                int x = LEFT + (i % 2) * CELL_W;
                int y = TOP + (i / 2) * CELL_H;
                boolean sel = t == p.tool;
                if (sel) {
                    g.setColorRgba(0x2F, 0x6F, 0xED, 255);
                    g.fillRoundRect(x, y, CELL_W - 4, CELL_H - 4, 6);
                    g.setColorRgba(255, 255, 255, 255);
                    g.fillRoundRect(x + 1, y + 1, CELL_W - 6, CELL_H - 6, 5);
                    g.setColorRgba(0xDD, 0xE8, 0xFD, 255);
                    g.fillRoundRect(x + 1, y + 1, CELL_W - 6, CELL_H - 6, 5);
                } else if (t == hover) {
                    g.setColorRgba(0xE4, 0xE7, 0xEE, 255);
                    g.fillRoundRect(x, y, CELL_W - 4, CELL_H - 4, 6);
                }
                icon(g, t, x + (CELL_W - 4) / 2.0 - 11, y + (CELL_H - 4) / 2.0 - 11, sel);
            }
            i = i + 1;
        }
        paintOptions(g);
        g.setAntialias(false);
    }

    void ink(Graphics g, boolean sel) {
        if (sel) {
            g.setColorRgba(0x1B, 0x3F, 0x8F, 255);
        } else {
            g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
        }
    }

    /** 22×22 的矢量图标，左上角在 (x, y)。 */
    void icon(Graphics g, int t, double x, double y, boolean sel) {
        g.setLineWidth(1.4);
        ink(g, sel);
        if (t == Painter.SELECT) {
            double s = 2.0;
            double k = 0;
            while (k < 18) {
                g.drawLine(x + 2 + k, y + 3, x + 2 + Math.min(k + 3, 18), y + 3);
                g.drawLine(x + 2 + k, y + 19, x + 2 + Math.min(k + 3, 18), y + 19);
                g.drawLine(x + 2, y + 3 + k * 16 / 18, x + 2, y + 3 + Math.min(k + 3, 18) * 16 / 18);
                g.drawLine(x + 20, y + 3 + k * 16 / 18, x + 20, y + 3 + Math.min(k + 3, 18) * 16 / 18);
                k = k + 6;
            }
        } else if (t == Painter.ERASER) {
            g.setLineWidth(9);
            g.setColorRgba(0xF0, 0x8A, 0xA6, 255);
            g.drawLine(x + 6, y + 15, x + 14, y + 7);
            g.setColorRgba(0xC9, 0xCE, 0xD8, 255);
            g.drawLine(x + 5, y + 16, x + 8, y + 13);
            g.setLineWidth(1.2);
            ink(g, sel);
            g.drawLine(x + 3, y + 20, x + 19, y + 20);
        } else if (t == Painter.FILL) {
            g.setLineWidth(1.6);
            g.drawLine(x + 4, y + 11, x + 11, y + 4);
            g.drawLine(x + 11, y + 4, x + 18, y + 11);
            g.drawLine(x + 18, y + 11, x + 11, y + 18);
            g.drawLine(x + 11, y + 18, x + 4, y + 11);
            g.setColorRgba(0x2F, 0x6F, 0xED, 255);
            g.fillEllipse(x + 17, y + 14, 5, 6);
            g.drawLine(x + 7, y + 8, x + 15, y + 12);
        } else if (t == Painter.PICK) {
            g.setLineWidth(3);
            g.setColorRgba(0x6B, 0x72, 0x80, 255);
            g.drawLine(x + 6, y + 16, x + 14, y + 8);
            g.setLineWidth(6);
            g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
            g.drawLine(x + 14, y + 8, x + 17, y + 5);
            g.setLineWidth(1.4);
            g.drawLine(x + 5, y + 17, x + 3, y + 20);
        } else if (t == Painter.ZOOM) {
            g.setLineWidth(1.8);
            g.drawEllipse(x + 3, y + 2, 12, 12);
            g.setLineWidth(3);
            g.drawLine(x + 13, y + 12, x + 19, y + 18);
            g.setLineWidth(1.2);
            g.drawLine(x + 6.5, y + 8, x + 11.5, y + 8);
            g.drawLine(x + 9, y + 5.5, x + 9, y + 10.5);
        } else if (t == Painter.PENCIL) {
            g.setLineWidth(5);
            g.setColorRgba(0xF2, 0xB7, 0x34, 255);
            g.drawLine(x + 8, y + 14, x + 17, y + 5);
            g.setLineWidth(5);
            g.setColorRgba(0xE8, 0x8A, 0x8A, 255);
            g.drawLine(x + 17, y + 5, x + 19, y + 3);
            g.setLineWidth(1.4);
            ink(g, sel);
            g.drawLine(x + 3, y + 19, x + 6, y + 16);
            g.drawLine(x + 3, y + 19, x + 3, y + 19);
        } else if (t == Painter.BRUSH) {
            g.setLineWidth(3);
            g.setColorRgba(0xA8, 0x6B, 0x3A, 255);
            g.drawLine(x + 10, y + 12, x + 19, y + 3);
            g.setLineWidth(1.2);
            g.setColorRgba(0xC0, 0xC4, 0xCC, 255);
            g.drawLine(x + 9, y + 13, x + 12, y + 10);
            g.setColorRgba(0x2F, 0x6F, 0xED, 255);
            g.fillEllipse(x + 2, y + 11, 9, 9);
        } else if (t == Painter.AIRBRUSH) {
            g.setLineWidth(1.4);
            ink(g, sel);
            g.drawRect(x + 11, y + 10, 8, 9);
            g.drawLine(x + 13, y + 10, x + 13, y + 5);
            g.drawLine(x + 17, y + 10, x + 17, y + 5);
            g.setColorRgba(0x2F, 0x6F, 0xED, 255);
            g.fillEllipse(x + 3, y + 4, 2, 2);
            g.fillEllipse(x + 6, y + 8, 2, 2);
            g.fillEllipse(x + 2, y + 9, 2, 2);
            g.fillEllipse(x + 6, y + 13, 2, 2);
            g.fillEllipse(x + 3, y + 15, 2, 2);
        } else if (t == Painter.TEXT) {
            g.setFont(Font.systemBold(20));
            g.drawText("A", x + 4, y - 1);
        } else if (t == Painter.LINE) {
            g.setLineWidth(1.8);
            g.drawLine(x + 3, y + 19, x + 19, y + 3);
        } else if (t == Painter.CURVE) {
            g.setLineWidth(1.8);
            double px0 = x + 3;
            double py0 = y + 18;
            int i = 1;
            while (i <= 16) {
                double u = i / 16.0;
                double v = 1 - u;
                double bx = v * v * v * (x + 3) + 3 * v * v * u * (x + 3) + 3 * v * u * u * (x + 19) + u * u * u * (x + 19);
                double by = v * v * v * (y + 18) + 3 * v * v * u * (y - 4) + 3 * v * u * u * (y + 26) + u * u * u * (y + 4);
                g.drawLine(px0, py0, bx, by);
                px0 = bx;
                py0 = by;
                i = i + 1;
            }
        } else if (t == Painter.RECT) {
            g.setLineWidth(1.8);
            g.drawRect(x + 3, y + 5, 16, 12);
        } else if (t == Painter.POLY) {
            g.setLineWidth(1.8);
            g.drawLine(x + 3, y + 17, x + 6, y + 5);
            g.drawLine(x + 6, y + 5, x + 14, y + 3);
            g.drawLine(x + 14, y + 3, x + 19, y + 11);
            g.drawLine(x + 19, y + 11, x + 11, y + 19);
            g.drawLine(x + 11, y + 19, x + 3, y + 17);
        } else if (t == Painter.ELLIPSE) {
            g.setLineWidth(1.8);
            g.drawEllipse(x + 2, y + 4, 18, 14);
        } else if (t == Painter.ROUNDRECT) {
            g.setLineWidth(1.8);
            g.drawRoundRect(x + 3, y + 5, 16, 12, 5);
        }
    }

    void paintOptions(Graphics g) {
        int kind = optionKind();
        int oy = optionsTop();
        int w = canvas.width;
        g.setColorRgba(0xD9, 0xDB, 0xE2, 255);
        g.fillRect(8, oy - 6, w - 18, 1);
        if (kind == 0) {
            return;
        }
        g.setFont(Font.system(11));
        g.setColorRgba(0x6B, 0x72, 0x80, 255);
        String title = "大小";
        if (kind == 4) {
            title = "线宽";
        } else if (kind == 5) {
            title = "填充方式 / 线宽";
        } else if (kind == 6) {
            title = "字号";
        }
        g.drawText(title, 10, oy);
        int y = oy + 22;
        if (kind == 5) {
            int m = 0;
            while (m < 3) {
                option(g, y + m * rowH(), p.fillMode == m);
                fillSample(g, m, y + m * rowH());
                m = m + 1;
            }
            y = y + 3 * rowH();
            int k = 0;
            while (k < 5) {
                option(g, y + k * rowH(), p.widthIdx == k);
                widthSample(g, Painter.LINE_W[k], y + k * rowH());
                k = k + 1;
            }
            return;
        }
        int k = 0;
        while (k < 5) {
            int ry = y + k * rowH();
            if (kind == 1) {
                option(g, ry, p.eraserIdx == k);
                int s = Math.min(18, Painter.ERASER_W[k] / 2 + 3);
                g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
                g.drawRect(w / 2 - s / 2, ry + 12 - s / 2, s, s);
            } else if (kind == 2) {
                option(g, ry, p.brushIdx == k);
                double d = Math.min(18.0, Painter.BRUSH_W[k] + 2.0);
                g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
                g.fillEllipse(w / 2 - d / 2, ry + 12 - d / 2, d, d);
            } else if (kind == 3) {
                option(g, ry, p.sprayIdx == k);
                g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
                int n = 0;
                while (n < 9 + k * 3) {
                    double a = n * 2.399963;
                    double r = Math.sqrt((n + 1.0) / (9 + k * 3)) * (4 + k * 2);
                    g.fillRect(w / 2 + Math.cos(a) * r, ry + 12 + Math.sin(a) * r, 1.5, 1.5);
                    n = n + 1;
                }
            } else if (kind == 4) {
                option(g, ry, p.widthIdx == k);
                widthSample(g, Painter.LINE_W[k], ry);
            } else if (kind == 6) {
                option(g, ry, p.textIdx == k);
                g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
                g.setFont(Font.system(Math.min(18, 7 + k * 2 + 2)));
                g.drawText(Painter.TEXT_SZ[k] + "", 14, ry + 4);
            }
            k = k + 1;
        }
        if (kind == 6) {
            int ry = y + 5 * rowH();
            option(g, ry, p.textBold);
            g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
            g.setFont(Font.systemBold(13));
            g.drawText("加粗", 14, ry + 4);
        }
    }

    void option(Graphics g, int y, boolean on) {
        int w = canvas.width - 18;
        if (on) {
            g.setColorRgba(0xDD, 0xE8, 0xFD, 255);
            g.fillRoundRect(8, y, w, rowH() - 3, 5);
            g.setColorRgba(0x2F, 0x6F, 0xED, 255);
            g.drawRoundRect(8, y, w, rowH() - 3, 5);
        }
    }

    void widthSample(Graphics g, int lw, int y) {
        g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
        g.fillRect(16, y + 11 - lw / 2.0, canvas.width - 34, lw);
    }

    void fillSample(Graphics g, int mode, int y) {
        double x = 22;
        double w = canvas.width - 46;
        double yy = y + 5;
        if (mode == 1) {
            g.setColorRgba(0xB8, 0xC9, 0xF4, 255);
            g.fillRect(x, yy, w, 13);
        }
        g.setColorRgba(0x3A, 0x3F, 0x4B, 255);
        if (mode == 2) {
            g.fillRect(x, yy, w, 13);
        } else {
            g.drawRect(x + 0.5, yy + 0.5, w - 1, 12);
        }
    }
}
