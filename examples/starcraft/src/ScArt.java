import jnative.graphics.*;

/**
 * Procedural art: terrain baking and the shapes of every unit and structure. Nothing is loaded from
 * disk, so the game is a single self contained binary.
 *
 * @author 张晓达
 */
public class ScArt {
    static Color[][] team = new Color[8][3];
    static Color shadow;
    static Color white;
    static Color black;
    static Color glass;
    static Color metal;
    static Color metalDark;
    static Color metalLight;
    static Color crystal;
    static Color crystalLight;
    static Color crystalDark;
    static Color gas;
    static Color gasLight;
    static Color[] stroke = new Color[1];
    static Stroke thin;
    static Stroke mid;
    static Stroke thick;
    static int ready;

    static void init() {
        if (ready != 0) {
            return;
        }
        ready = 1;
        setTeam(0, 70, 140, 255);
        setTeam(1, 240, 70, 60);
        setTeam(2, 240, 210, 60);
        setTeam(3, 80, 220, 110);
        setTeam(4, 230, 120, 230);
        setTeam(5, 240, 150, 50);
        setTeam(6, 90, 220, 230);
        setTeam(7, 200, 200, 210);
        shadow = new Color(0, 0, 0, 70);
        white = new Color(255, 255, 255);
        black = new Color(0, 0, 0);
        glass = new Color(150, 220, 255);
        metal = new Color(122, 128, 140);
        metalDark = new Color(68, 72, 84);
        metalLight = new Color(178, 184, 196);
        crystal = new Color(70, 150, 255);
        crystalLight = new Color(170, 225, 255);
        crystalDark = new Color(30, 80, 190);
        gas = new Color(80, 230, 110, 200);
        gasLight = new Color(190, 255, 200, 200);
        thin = new Stroke(1);
        mid = new Stroke(2);
        thick = new Stroke(3);
    }

    static void setTeam(int i, int r, int g, int b) {
        team[i][0] = new Color(r, g, b);
        team[i][1] = new Color(Math.min(255, r + 70), Math.min(255, g + 70), Math.min(255, b + 70));
        team[i][2] = new Color(r * 6 / 10, g * 6 / 10, b * 6 / 10);
    }

    static Color teamColor(int owner, int shade) {
        if (owner < 0) {
            return metal;
        }
        return team[owner % 8][shade];
    }

    static int hash(int x, int y, int s) {
        int h = x * 73856093 ^ y * 19349663 ^ s * 83492791;
        h = h ^ (h >>> 13);
        h = h * 1274126177;
        h = h ^ (h >>> 16);
        return h & 2147483647;
    }

    // ------------------------------------------------------------------ terrain

    static Image bakeTerrain(ScMap m) {
        Image img = Image.create(m.w * 32, m.h * 32);
        Graphics g = img.beginGraphics();
        int ty = 0;
        while (ty < m.h) {
            int tx = 0;
            while (tx < m.w) {
                drawTile(g, m, tx, ty);
                tx = tx + 1;
            }
            ty = ty + 1;
        }
        g.end();
        return img;
    }

    /** Bake an 8x8 tile block (256x256 px) whose top-left tile is (tx0, ty0). */
    static Image bakeChunk(ScMap m, int tx0, int ty0) {
        Image img = Image.create(256, 256);
        Graphics g = img.beginGraphics();
        g.translate(-tx0 * 32, -ty0 * 32);
        int ty = ty0;
        while (ty < ty0 + 8 && ty < m.h) {
            int tx = tx0;
            while (tx < tx0 + 8 && tx < m.w) {
                drawTile(g, m, tx, ty);
                tx = tx + 1;
            }
            ty = ty + 1;
        }
        g.end();
        return img;
    }

    static void drawTile(Graphics g, ScMap m, int tx, int ty) {
        int t = m.get(tx, ty);
        int x = tx * 32;
        int y = ty * 32;
        int hs = hash(tx, ty, 1);
        int v = hs % 5;
        if (t == 0) {
            g.setColor(new Color(54 + v * 3, 100 + v * 3, 46 + v * 2));
            g.fillRect(x, y, 32, 32);
            int k = 0;
            while (k < 14) {
                int hh = hash(tx, ty, 10 + k);
                int px = x + hh % 30;
                int py = y + (hh / 31) % 30;
                int s = hh % 3;
                if (s == 0) {
                    g.setColor(new Color(76, 128, 62, 120));
                } else if (s == 1) {
                    g.setColor(new Color(40, 82, 38, 120));
                } else {
                    g.setColor(new Color(96, 148, 70, 90));
                }
                g.fillRect(px, py, 2 + hh % 3, 2);
                k = k + 1;
            }
            // little blades
            g.setStroke(thin);
            k = 0;
            while (k < 3) {
                int hh = hash(tx, ty, 40 + k);
                int px = x + 3 + hh % 26;
                int py = y + 6 + (hh / 31) % 22;
                g.setColor(new Color(110, 164, 80, 150));
                g.drawLine(px, py, px - 1, py - 4);
                g.drawLine(px + 2, py, px + 3, py - 3);
                k = k + 1;
            }
        } else if (t == 1) {
            g.setColor(new Color(122 + v * 3, 98 + v * 2, 66 + v * 2));
            g.fillRect(x, y, 32, 32);
            int k = 0;
            while (k < 10) {
                int hh = hash(tx, ty, 70 + k);
                int px = x + hh % 28;
                int py = y + (hh / 29) % 28;
                if (hh % 2 == 0) {
                    g.setColor(new Color(150, 124, 90, 130));
                } else {
                    g.setColor(new Color(92, 70, 46, 140));
                }
                g.fillEllipse(px, py, 3 + hh % 4, 2 + hh % 3);
                k = k + 1;
            }
        } else if (t == 2) {
            g.setColor(new Color(26 + v * 2, 64 + v * 3, 126 + v * 3));
            g.fillRect(x, y, 32, 32);
            g.setStroke(thin);
            int k = 0;
            while (k < 4) {
                int hh = hash(tx, ty, 90 + k);
                int px = x + hh % 20;
                int py = y + 4 + k * 8;
                g.setColor(new Color(90, 150, 210, 90));
                g.drawLine(px, py, px + 8 + hh % 6, py);
                k = k + 1;
            }
            // shore
            drawShore(g, m, tx, ty, x, y);
        } else {
            drawRock(g, m, tx, ty, x, y, v);
        }
        // cliff shadow on ground below rock
        if (t < 2 && m.get(tx, ty - 1) == 3) {
            g.setColor(new Color(0, 0, 0, 70));
            g.fillRect(x, y, 32, 7);
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRect(x, y + 7, 32, 5);
        }
    }

    static void drawShore(Graphics g, ScMap m, int tx, int ty, int x, int y) {
        g.setColor(new Color(190, 225, 240, 120));
        if (m.get(tx, ty - 1) < 2) {
            g.fillRect(x, y, 32, 3);
        }
        if (m.get(tx, ty + 1) < 2) {
            g.fillRect(x, y + 29, 32, 3);
        }
        if (m.get(tx - 1, ty) < 2) {
            g.fillRect(x, y, 3, 32);
        }
        if (m.get(tx + 1, ty) < 2) {
            g.fillRect(x + 29, y, 3, 32);
        }
    }

    static void drawRock(Graphics g, ScMap m, int tx, int ty, int x, int y, int v) {
        int below = m.get(tx, ty + 1);
        // top surface
        g.setColor(new Color(100 + v * 3, 98 + v * 3, 108 + v * 3));
        g.fillRect(x, y, 32, 32);
        g.setColor(new Color(128, 126, 138, 110));
        g.fillRect(x + 2, y + 2, 28, 10);
        int k = 0;
        while (k < 5) {
            int hh = hash(tx, ty, 120 + k);
            int px = x + hh % 24;
            int py = y + (hh / 25) % 22;
            g.setColor(new Color(70, 68, 80, 110));
            g.fillRect(px, py, 5 + hh % 6, 3 + hh % 5);
            g.setColor(new Color(150, 148, 160, 90));
            g.fillRect(px + 1, py - 1, 4 + hh % 4, 2);
            k = k + 1;
        }
        if (below < 2) {
            // sheer face
            g.setColor(new Color(58, 56, 68));
            g.fillRect(x, y + 20, 32, 12);
            g.setColor(new Color(40, 38, 48));
            g.fillRect(x, y + 28, 32, 4);
            g.setStroke(thin);
            g.setColor(new Color(82, 80, 94));
            int s = 0;
            while (s < 4) {
                int hh = hash(tx, ty, 150 + s);
                int px = x + 3 + s * 8 + hh % 4;
                g.drawLine(px, y + 20, px - 2, y + 31);
                s = s + 1;
            }
        }
        if (m.get(tx, ty - 1) < 2) {
            g.setColor(new Color(170, 168, 180, 140));
            g.fillRect(x, y, 32, 2);
        }
    }

    static Image bakeMinimap(ScMap m) {
        Image img = Image.create(m.w, m.h);
        int y = 0;
        while (y < m.h) {
            int x = 0;
            while (x < m.w) {
                int t = m.get(x, y);
                int r = 60;
                int gg = 110;
                int b = 50;
                if (t == 1) {
                    r = 120;
                    gg = 98;
                    b = 66;
                } else if (t == 2) {
                    r = 30;
                    gg = 70;
                    b = 130;
                } else if (t == 3) {
                    r = 100;
                    gg = 98;
                    b = 108;
                }
                GfxNative.setPixel(img.surfaceId, x, y, GfxNative.packRgba(r, gg, b, 255));
                x = x + 1;
            }
            y = y + 1;
        }
        return img;
    }

    // ------------------------------------------------------------------ units

    static void unitShadow(Graphics g, double cx, double cy, double r) {
        g.setColor(shadow);
        g.fillEllipse(cx - r, cy + r * 0.35, r * 2, r * 1.1);
    }

    static void drawWorker(Graphics g, double cx, double cy, double facing, int owner, int carryType, int working, long frame) {
        unitShadow(g, cx, cy, 8);
        double fx = Math.cos(facing);
        double fy = Math.sin(facing);
        // backpack
        g.setColor(metalDark);
        g.fillEllipse(cx - fx * 5 - 4, cy - fy * 5 - 4, 8, 8);
        // body
        g.setColor(teamColor(owner, 2));
        g.fillEllipse(cx - 7, cy - 7, 14, 14);
        g.setColor(teamColor(owner, 0));
        g.fillEllipse(cx - 6, cy - 7, 12, 11);
        // helmet visor
        g.setColor(glass);
        g.fillEllipse(cx + fx * 3 - 3, cy + fy * 3 - 4, 6, 5);
        g.setColor(white);
        g.fillRect(cx + fx * 3 - 1, cy + fy * 3 - 3, 2, 1);
        // tool arm
        g.setStroke(mid);
        g.setColor(metalLight);
        double tx = cx + fx * 11;
        double ty = cy + fy * 11;
        g.drawLine(cx + fx * 5, cy + fy * 5, tx, ty);
        if (working != 0 && frame % 4 < 2) {
            g.setColor(new Color(255, 240, 150));
            g.fillEllipse(tx - 3, ty - 3, 6, 6);
            g.setColor(white);
            g.fillEllipse(tx - 1.5, ty - 1.5, 3, 3);
        }
        if (carryType == 1) {
            g.setColor(crystalDark);
            g.fillRoundRect(cx - 4, cy - 15, 8, 8, 2);
            g.setColor(crystalLight);
            g.fillRect(cx - 3, cy - 14, 3, 3);
        } else if (carryType == 2) {
            g.setColor(new Color(40, 170, 80));
            g.fillRoundRect(cx - 4, cy - 15, 8, 8, 3);
            g.setColor(gasLight);
            g.fillRect(cx - 3, cy - 14, 3, 3);
        }
    }

    static void drawRifleman(Graphics g, double cx, double cy, double facing, int owner, int firing, long frame) {
        unitShadow(g, cx, cy, 8);
        double fx = Math.cos(facing);
        double fy = Math.sin(facing);
        // legs / base
        g.setColor(metalDark);
        g.fillEllipse(cx - 6, cy - 5, 12, 12);
        // torso armour
        g.setColor(teamColor(owner, 2));
        g.fillEllipse(cx - 8, cy - 9, 16, 15);
        g.setColor(teamColor(owner, 0));
        g.fillEllipse(cx - 7, cy - 9, 14, 11);
        g.setColor(teamColor(owner, 1));
        g.fillRect(cx - 4, cy - 7, 8, 2);
        // helmet
        g.setColor(metal);
        g.fillEllipse(cx - 5 + fx * 2, cy - 11 + fy * 2, 10, 9);
        g.setColor(glass);
        g.fillRect(cx - 3 + fx * 3, cy - 8 + fy * 3, 6, 3);
        // rifle
        g.setStroke(thick);
        g.setColor(metalDark);
        double gx = cx + fx * 15;
        double gy = cy + fy * 15 - 2;
        g.drawLine(cx + fx * 3, cy + fy * 3 - 2, gx, gy);
        g.setStroke(thin);
        g.setColor(metalLight);
        g.drawLine(cx + fx * 5, cy + fy * 5 - 3, gx - fx * 2, gy - fy * 2 - 1);
        if (firing != 0) {
            g.setColor(new Color(255, 230, 120));
            g.fillEllipse(gx - 4, gy - 4, 8, 8);
            g.setColor(white);
            g.fillEllipse(gx - 2, gy - 2, 4, 4);
        }
    }

    // ------------------------------------------------------------------ structures

    /** Draw a structure with its top-left corner at (sx, sy) in screen pixels. */
    static void drawStructure(Graphics g, int type, int sx, int sy, int owner, double frac, long frame, double facing, int firing) {
        int w = ScDefs.tw[type] * 32;
        int h = ScDefs.th[type] * 32;
        if (frac < 1.0) {
            g.save();
            double shown = Math.max(0.12, frac);
            g.setClip(sx - 2, sy + h * (1 - shown), w + 4, h * shown + 4);
        }
        if (type == ScDefs.HUB) {
            drawHub(g, sx, sy, w, h, owner, frame);
        } else if (type == ScDefs.DEPOT) {
            drawDepot(g, sx, sy, w, h, owner);
        } else if (type == ScDefs.BARRACKS) {
            drawBarracks(g, sx, sy, w, h, owner, frame);
        } else if (type == ScDefs.REFINERY) {
            drawRefinery(g, sx, sy, w, h, owner, frame);
        } else if (type == ScDefs.TURRET) {
            drawTurret(g, sx, sy, w, h, owner, facing, firing);
        }
        if (frac < 1.0) {
            g.restore();
            // scaffold
            g.setStroke(thin);
            g.setColor(new Color(240, 210, 80, 200));
            int i = 0;
            while (i <= w) {
                g.drawLine(sx + i, sy + h * (1 - Math.max(0.12, frac)), sx + i, sy + h);
                i = i + 16;
            }
            g.drawLine(sx, sy + h * (1 - Math.max(0.12, frac)), sx + w, sy + h * (1 - Math.max(0.12, frac)));
            g.setColor(new Color(255, 255, 255, 190));
            if (frame % 6 < 3) {
                g.fillEllipse(sx + w * 0.3, sy + h * (1 - frac) - 3, 6, 6);
            }
        }
    }

    static void drawHub(Graphics g, int x, int y, int w, int h, int owner, long frame) {
        g.setColor(shadow);
        g.fillRoundRect(x + 6, y + h - 14, w - 4, 18, 10);
        // base plate
        g.setColor(metalDark);
        g.fillRoundRect(x + 2, y + 6, w - 4, h - 8, 12);
        g.setColor(metal);
        g.fillRoundRect(x + 5, y + 8, w - 10, h - 18, 10);
        g.setColor(metalLight);
        g.fillRect(x + 12, y + 12, w - 24, 3);
        // team trim
        g.setColor(teamColor(owner, 0));
        g.fillRect(x + 8, y + h - 20, w - 16, 5);
        g.setColor(teamColor(owner, 1));
        g.fillRect(x + 8, y + h - 20, w - 16, 1);
        // side pods
        g.setColor(metalDark);
        g.fillRoundRect(x + 6, y + 22, 22, h - 44, 6);
        g.fillRoundRect(x + w - 28, y + 22, 22, h - 44, 6);
        g.setColor(teamColor(owner, 2));
        g.fillRoundRect(x + 9, y + 25, 16, h - 50, 5);
        g.fillRoundRect(x + w - 25, y + 25, 16, h - 50, 5);
        // dome
        double dx = x + w / 2.0;
        double dy = y + h / 2.0 - 4;
        g.setColor(metalDark);
        g.fillEllipse(dx - 34, dy - 28, 68, 58);
        g.setColor(new Color(90, 100, 118));
        g.fillEllipse(dx - 31, dy - 31, 62, 56);
        g.setColor(new Color(128, 142, 164));
        g.fillEllipse(dx - 26, dy - 28, 40, 28);
        g.setColor(new Color(190, 205, 225, 200));
        g.fillEllipse(dx - 20, dy - 25, 22, 12);
        // beacon
        g.setColor(frame % 24 < 12 ? new Color(255, 90, 70) : new Color(120, 40, 30));
        g.fillEllipse(dx + 20, dy - 24, 6, 6);
        // landing lights
        int k = 0;
        while (k < 5) {
            g.setColor(frame / 6 % 5 == k ? new Color(255, 240, 160) : new Color(150, 130, 70));
            g.fillRect(x + 22 + k * ((w - 50) / 4), y + h - 12, 4, 3);
            k = k + 1;
        }
        g.setColor(black);
        g.fillRoundRect(dx - 9, y + h - 22, 18, 12, 3);
    }

    static void drawDepot(Graphics g, int x, int y, int w, int h, int owner) {
        g.setColor(shadow);
        g.fillRoundRect(x + 4, y + h - 10, w - 2, 12, 8);
        int i = 0;
        while (i < 3) {
            int bx = x + 4 + i * 30;
            g.setColor(metalDark);
            g.fillRoundRect(bx, y + 8, 28, h - 14, 5);
            g.setColor(i == 1 ? teamColor(owner, 0) : metal);
            g.fillRoundRect(bx + 2, y + 10, 24, h - 20, 4);
            g.setColor(i == 1 ? teamColor(owner, 1) : metalLight);
            g.fillRect(bx + 4, y + 12, 20, 3);
            g.setColor(new Color(0, 0, 0, 90));
            int r = 0;
            while (r < 3) {
                g.fillRect(bx + 6, y + 20 + r * 8, 16, 2);
                r = r + 1;
            }
            i = i + 1;
        }
    }

    static void drawBarracks(Graphics g, int x, int y, int w, int h, int owner, long frame) {
        g.setColor(shadow);
        g.fillRoundRect(x + 6, y + h - 14, w - 4, 18, 10);
        g.setColor(metalDark);
        g.fillRoundRect(x + 2, y + 4, w - 4, h - 6, 8);
        g.setColor(metal);
        g.fillRoundRect(x + 5, y + 6, w - 10, h - 12, 6);
        // roof panels
        g.setColor(metalLight);
        g.fillRect(x + 10, y + 10, w - 20, 4);
        g.setColor(new Color(100, 108, 124));
        g.fillRect(x + 10, y + 16, (w - 24) / 2, 22);
        g.fillRect(x + 14 + (w - 24) / 2, y + 16, (w - 24) / 2, 22);
        g.setColor(teamColor(owner, 0));
        g.fillRect(x + 10, y + 40, w - 20, 5);
        // door
        g.setColor(black);
        g.fillRoundRect(x + w / 2.0 - 16, y + h - 34, 32, 30, 4);
        g.setColor(new Color(40, 44, 54));
        g.fillRect(x + w / 2.0 - 14, y + h - 32, 28, 6);
        g.setColor(teamColor(owner, 1));
        g.fillRect(x + w / 2.0 - 16, y + h - 36, 32, 2);
        // antenna
        g.setStroke(thin);
        g.setColor(metalLight);
        g.drawLine(x + w - 18, y + 10, x + w - 18, y - 6);
        g.setColor(frame % 30 < 15 ? new Color(255, 80, 60) : new Color(110, 30, 20));
        g.fillEllipse(x + w - 21, y - 9, 6, 6);
    }

    static void drawRefinery(Graphics g, int x, int y, int w, int h, int owner, long frame) {
        g.setColor(shadow);
        g.fillRoundRect(x + 4, y + h - 10, w - 2, 12, 8);
        g.setColor(metalDark);
        g.fillRoundRect(x + 3, y + 8, w - 6, h - 12, 8);
        g.setColor(metal);
        g.fillRoundRect(x + 6, y + 10, w - 12, h - 18, 6);
        // tanks
        int i = 0;
        while (i < 2) {
            double tx = x + 14 + i * 40;
            g.setColor(metalDark);
            g.fillEllipse(tx, y + 10, 30, 36);
            g.setColor(teamColor(owner, 2));
            g.fillEllipse(tx + 2, y + 8, 26, 30);
            g.setColor(teamColor(owner, 0));
            g.fillEllipse(tx + 4, y + 8, 22, 20);
            g.setColor(new Color(255, 255, 255, 90));
            g.fillEllipse(tx + 7, y + 11, 8, 6);
            i = i + 1;
        }
        g.setStroke(thick);
        g.setColor(metalLight);
        g.drawLine(x + 44, y + 36, x + 54, y + 36);
        double pulse = 0.5 + 0.5 * Math.sin(frame * 0.2);
        g.setColor(new Color(90, 240, 120, (int) (120 + pulse * 100)));
        g.fillEllipse(x + w / 2.0 - 5, y + h - 20, 10, 8);
    }

    static void drawTurret(Graphics g, int x, int y, int w, int h, int owner, double facing, int firing) {
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        g.setColor(shadow);
        g.fillEllipse(cx - 24, cy - 12, 48, 38);
        g.setColor(metalDark);
        g.fillEllipse(cx - 24, cy - 24, 48, 48);
        g.setColor(metal);
        g.fillEllipse(cx - 20, cy - 22, 40, 40);
        g.setColor(teamColor(owner, 0));
        g.fillEllipse(cx - 12, cy - 16, 24, 24);
        g.setColor(teamColor(owner, 1));
        g.fillEllipse(cx - 8, cy - 14, 12, 8);
        double fx = Math.cos(facing);
        double fy = Math.sin(facing);
        g.setStroke(new Stroke(6));
        g.setColor(metalDark);
        g.drawLine(cx, cy - 4, cx + fx * 28, cy - 4 + fy * 28);
        g.setStroke(mid);
        g.setColor(metalLight);
        g.drawLine(cx, cy - 5, cx + fx * 26, cy - 5 + fy * 26);
        if (firing != 0) {
            g.setColor(new Color(255, 230, 120));
            g.fillEllipse(cx + fx * 30 - 5, cy - 4 + fy * 30 - 5, 10, 10);
        }
    }

    // ------------------------------------------------------------------ resources

    static void drawMineral(Graphics g, int x, int y, double frac, int variant) {
        // x,y: top-left of a 64x32 patch
        g.setColor(shadow);
        g.fillEllipse(x + 4, y + 16, 58, 14);
        double s = 0.55 + 0.45 * Math.max(0, Math.min(1, frac));
        crystalSpike(g, x + 12, y + 28, 16 * s, 26 * s);
        crystalSpike(g, x + 42, y + 28, 15 * s, 22 * s);
        crystalSpike(g, x + 27, y + 30, 18 * s, 34 * s);
        crystalSpike(g, x + 52, y + 29, 9 * s, 14 * s);
    }

    static void crystalSpike(Graphics g, double bx, double by, double w, double h) {
        Path p = new Path();
        p.moveTo(bx - w / 2, by);
        p.lineTo(bx - w * 0.3, by - h * 0.7);
        p.lineTo(bx, by - h);
        p.lineTo(bx + w * 0.35, by - h * 0.65);
        p.lineTo(bx + w / 2, by);
        p.close();
        g.setColor(crystalDark);
        g.fillPath(p);
        p.dispose();
        Path q = new Path();
        q.moveTo(bx - w / 2 + 2, by - 1);
        q.lineTo(bx - w * 0.28, by - h * 0.68);
        q.lineTo(bx, by - h + 1);
        q.lineTo(bx + 1, by - 1);
        q.close();
        g.setColor(crystal);
        g.fillPath(q);
        q.dispose();
        g.setColor(crystalLight);
        g.fillRect(bx - w * 0.2, by - h * 0.72, 2, h * 0.4);
    }

    static void drawGeyser(Graphics g, int x, int y, long frame) {
        // 96x64
        g.setColor(new Color(40, 36, 30));
        g.fillEllipse(x + 6, y + 8, 84, 52);
        g.setColor(new Color(70, 62, 50));
        g.fillEllipse(x + 12, y + 12, 72, 42);
        g.setColor(new Color(20, 40, 24));
        g.fillEllipse(x + 22, y + 20, 52, 26);
        int k = 0;
        while (k < 4) {
            double ph = (frame * 0.05 + k * 0.25) % 1.0;
            g.setColor(new Color(110, 240, 140, (int) (180 * (1 - ph))));
            g.fillEllipse(x + 30 + k * 9 - ph * 4, y + 38 - ph * 34, 10 + ph * 8, 8 + ph * 6);
            k = k + 1;
        }
        g.setColor(gas);
        g.fillEllipse(x + 34, y + 28, 28, 12);
    }
}
