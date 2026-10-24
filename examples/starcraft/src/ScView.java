import jnative.collections.*;
import jnative.graphics.*;

/**
 * Everything on screen: world, fog of war, minimap, HUD, menu and overlays.
 *
 * @author 张晓达
 */
public class ScView {
    ScGame game;
    Image[] chunks;
    int chunkCols;
    int chunkRows;
    Image mmTerrain;
    Image mmFog;
    int[] ghostType = new int[4096];
    int[] ghostOwner = new int[4096];
    int[] ghostTx = new int[4096];
    int[] ghostTy = new int[4096];
    int[] ghostHp = new int[4096];
    int[] ghostDone = new int[4096];
    int[] ghostMax = new int[4096];
    int mmDirty;
    long[] prof = new long[8];
    int[] order = new int[2048];
    long[] sortKey = new long[2048];

    Font fSmall = new Font("Menlo", 12, FontStyle.BOLD);
    Font fHud = new Font("Menlo", 15, FontStyle.BOLD);
    Font fMid = new Font("Menlo", 18, FontStyle.BOLD);
    Font fBig = new Font("Menlo", 30, FontStyle.BOLD);
    Font fTitle = new Font("Menlo", 64, FontStyle.BOLD);
    Color panel = new Color(24, 28, 38);
    Color panelLight = new Color(52, 60, 78);
    Color panelEdge = new Color(110, 124, 150);
    Color textColor = new Color(214, 226, 244);
    Color dim = new Color(130, 146, 172);
    Color gold = new Color(255, 214, 96);

    ScView(ScGame g) {
        game = g;
    }

    void reset() {
        ScSim sim = game.sim;
        if (chunks != null) {
            int ci = 0;
            while (ci < chunks.length) {
                chunks[ci].dispose();
                ci = ci + 1;
            }
            mmTerrain.dispose();
            mmFog.dispose();
            chunks = null;
        }
        if (sim == null) {
            return;
        }
        chunkCols = (sim.w + 7) / 8;
        chunkRows = (sim.h + 7) / 8;
        chunks = new Image[chunkCols * chunkRows];
        int cyi = 0;
        while (cyi < chunkRows) {
            int cxi = 0;
            while (cxi < chunkCols) {
                chunks[cyi * chunkCols + cxi] = ScArt.bakeChunk(sim.map, cxi * 8, cyi * 8);
                cxi = cxi + 1;
            }
            cyi = cyi + 1;
        }
        mmTerrain = ScArt.bakeMinimap(sim.map);
        mmFog = Image.create(sim.w, sim.h);
        int i = 0;
        while (i < ghostType.length) {
            ghostType[i] = -1;
            i = i + 1;
        }
        mmDirty = 0;
    }

    void tickEffects() {
        ScSim sim = game.sim;
        int i = 0;
        while (i < game.markers.size()) {
            if (game.frame - (long) game.markers.get(i)[3] > 30) {
                game.markers.remove(i);
            } else {
                i = i + 1;
            }
        }
        if (game.frame % 6 == 0) {
            updateGhosts();
        }
        if (game.frame % 4 == 0) {
            updateMinimapFog();
        }
    }

    void updateGhosts() {
        ScSim sim = game.sim;
        int vp = game.viewPlayer;
        if (vp < 0) {
            return;
        }
        if (sim.nextId >= ghostType.length) {
            int n = ghostType.length * 2;
            ghostType = grow(ghostType, n, -1);
            ghostOwner = grow(ghostOwner, n, 0);
            ghostTx = grow(ghostTx, n, 0);
            ghostTy = grow(ghostTy, n, 0);
            ghostHp = grow(ghostHp, n, 0);
            ghostDone = grow(ghostDone, n, 0);
            ghostMax = grow(ghostMax, n, 0);
        }
        int i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.isBuilding() == 0 || e.owner == vp || e.owner < 0) {
                continue;
            }
            if (game.canSee(e) != 0) {
                ghostType[e.id] = e.type;
                ghostOwner[e.id] = e.owner;
                ghostTx[e.id] = e.tx;
                ghostTy[e.id] = e.ty;
                ghostHp[e.id] = e.hp;
                ghostMax[e.id] = e.maxHp;
                ghostDone[e.id] = e.done;
            }
        }
        int id = 1;
        while (id < sim.nextId && id < ghostType.length) {
            if (ghostType[id] >= 0) {
                ScEntity e = sim.get(id);
                int cx = ghostTx[id] + ScDefs.tw[ghostType[id]] / 2;
                int cy = ghostTy[id] + ScDefs.th[ghostType[id]] / 2;
                if (e == null && sim.visibleTo(vp, cx, cy) != 0) {
                    ghostType[id] = -1;
                }
            }
            id = id + 1;
        }
    }

    int[] grow(int[] a, int n, int fill) {
        int[] b = new int[n];
        int i = 0;
        while (i < n) {
            b[i] = i < a.length ? a[i] : fill;
            i = i + 1;
        }
        return b;
    }

    void updateMinimapFog() {
        ScSim sim = game.sim;
        if (sim == null || mmFog == null) {
            return;
        }
        int vp = game.viewPlayer;
        int sid = mmFog.surfaceId;
        int clear = GfxNative.packRgba(0, 0, 0, 0);
        int explored = GfxNative.packRgba(0, 0, 0, 120);
        int unseen = GfxNative.packRgba(0, 0, 0, 255);
        int y = 0;
        while (y < sim.h) {
            int x = 0;
            while (x < sim.w) {
                int c = clear;
                if (vp >= 0) {
                    int k = y * sim.w + x;
                    if (sim.vis[vp][k] == 0) {
                        c = sim.seen[vp][k] != 0 ? explored : unseen;
                    }
                }
                GfxNative.setPixel(sid, x, y, c);
                x = x + 1;
            }
            y = y + 1;
        }
    }

    // ------------------------------------------------------------------ helpers

    void text(Graphics g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        double top = y - f.getSize() * 0.74;
        g.setColor(black(150));
        g.drawText(s, x + 1, top + 1);
        g.setColor(c);
        g.drawText(s, x, top);
    }

    Color black(int a) {
        return new Color(0, 0, 0, a);
    }

    void textRight(Graphics g, String s, double xr, double y, Font f, Color c) {
        double w = g.measureText(s, f).width;
        text(g, s, xr - w, y, f, c);
    }

    void textCenter(Graphics g, String s, double xc, double y, Font f, Color c) {
        double w = g.measureText(s, f).width;
        text(g, s, xc - w / 2, y, f, c);
    }

    String clock(long tick) {
        long s = tick / 24;
        long m = s / 60;
        long r = s % 60;
        return m + ":" + (r < 10 ? "0" : "") + r;
    }

    void bevel(Graphics g, double x, double y, double w, double h, Color fill) {
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(panelEdge);
        g.fillRect(x, y, w, 1);
        g.fillRect(x, y, 1, h);
        g.setColor(black(160));
        g.fillRect(x, y + h - 1, w, 1);
        g.fillRect(x + w - 1, y, 1, h);
    }

    // ------------------------------------------------------------------ paint

    void paint(Graphics g) {
        if (game.mode == ScGame.MENU || game.sim == null) {
            paintMenu(g);
            return;
        }
        paintWorld(g);
        long p0 = System.nanoTime();
        paintHud(g);
        prof[5] = prof[5] + System.nanoTime() - p0;
        if (game.mode == ScGame.PLAY && game.sim.gameOver != 0) {
            paintGameOver(g);
        }
        if (game.paused != 0 && game.sim.gameOver == 0) {
            g.setColor(black(110));
            g.fillRect(0, 0, ScGame.VW, ScGame.VH);
            textCenter(g, "PAUSED", ScGame.VW / 2.0, 280, fBig, textColor);
        }
    }

    void paintWorld(Graphics g) {
        ScSim sim = game.sim;
        int vw = ScGame.VW;
        int vh = ScGame.VH;
        int cx = (int) game.camX;
        int cy = (int) game.camY;
        g.setClip(0, 0, vw, vh);
        g.setColor(new Color(0, 0, 0));
        g.fillRect(0, 0, vw, vh);
        long p0 = System.nanoTime();
        int c0 = Math.max(0, cx / 256);
        int c1 = Math.min(chunkCols - 1, (cx + vw) / 256);
        int r0 = Math.max(0, cy / 256);
        int r1 = Math.min(chunkRows - 1, (cy + vh) / 256);
        int rr = r0;
        while (rr <= r1) {
            int cc = c0;
            while (cc <= c1) {
                g.drawImage(chunks[rr * chunkCols + cc], cc * 256 - cx, rr * 256 - cy);
                cc = cc + 1;
            }
            rr = rr + 1;
        }
        prof[0] = prof[0] + System.nanoTime() - p0;
        p0 = System.nanoTime();
        // water shimmer
        int tx0 = Math.max(0, cx / 32);
        int ty0 = Math.max(0, cy / 32);
        int tx1 = Math.min(sim.w - 1, (cx + vw) / 32);
        int ty1 = Math.min(sim.h - 1, (cy + vh) / 32);
        g.setStroke(ScArt.thin);
        int ty = ty0;
        while (ty <= ty1) {
            int tx = tx0;
            while (tx <= tx1) {
                if (sim.map.get(tx, ty) == 2) {
                    int hh = ScArt.hash(tx, ty, 5);
                    double ph = ((game.frame + hh % 90) % 90) / 90.0;
                    int a = (int) (110 * Math.sin(ph * 3.14159));
                    g.setColor(new Color(200, 235, 255, a));
                    int lx = tx * 32 - cx + 4 + (hh % 12);
                    int ly = ty * 32 - cy + 6 + (hh / 7) % 20;
                    g.drawLine(lx, ly, lx + 10, ly);
                }
                tx = tx + 1;
            }
            ty = ty + 1;
        }
        prof[1] = prof[1] + System.nanoTime() - p0;
        p0 = System.nanoTime();
        paintEntities(g, cx, cy);
        paintEffects(g, cx, cy);
        prof[2] = prof[2] + System.nanoTime() - p0;
        p0 = System.nanoTime();
        paintFog(g, cx, cy, tx0, ty0, tx1, ty1);
        prof[3] = prof[3] + System.nanoTime() - p0;
        p0 = System.nanoTime();
        paintOverlays(g, cx, cy);
        prof[4] = prof[4] + System.nanoTime() - p0;
        g.clearClip();
    }

    void paintEntities(Graphics g, int cx, int cy) {
        ScSim sim = game.sim;
        int vw = ScGame.VW;
        int vh = ScGame.VH;
        long frame = game.frame;
        // pass 1: resources, buildings and ghosts (sorted by bottom edge)
        int n = 0;
        int i = 0;
        while (i < sim.ents.size() && n < 2000) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.isUnit() != 0) {
                continue;
            }
            double sx = e.tx * 32 - cx;
            double sy = e.ty * 32 - cy;
            if (sx > vw || sy > vh || sx + e.tw * 32 < 0 || sy + e.th * 32 < 0) {
                continue;
            }
            if (game.canSee(e) == 0) {
                continue;
            }
            order[n] = e.id;
            sortKey[n] = (e.ty + e.th) * 1000L + e.tx;
            n = n + 1;
        }
        // ghosts as negative ids
        int vp = game.viewPlayer;
        if (vp >= 0) {
            int id = 1;
            while (id < sim.nextId && id < ghostType.length && n < 2000) {
                if (ghostType[id] >= 0) {
                    ScEntity ge = sim.get(id);
                    boolean show = ge == null || game.canSee(ge) == 0;
                    double sx = ghostTx[id] * 32 - cx;
                    double sy = ghostTy[id] * 32 - cy;
                    int t = ghostType[id];
                    if (show && sx < vw && sy < vh && sx + ScDefs.tw[t] * 32 > 0 && sy + ScDefs.th[t] * 32 > 0) {
                        order[n] = -id;
                        sortKey[n] = (ghostTy[id] + ScDefs.th[t]) * 1000L + ghostTx[id];
                        n = n + 1;
                    }
                }
                id = id + 1;
            }
        }
        sortOrder(n);
        i = 0;
        while (i < n) {
            int id = order[i];
            i = i + 1;
            if (id < 0) {
                int gid = -id;
                int t = ghostType[gid];
                double fr = ghostDone[gid] != 0 ? 1.0 : 0.6;
                ScArt.drawStructure(g, t, ghostTx[gid] * 32 - cx, ghostTy[gid] * 32 - cy, ghostOwner[gid], fr, frame, 1.5, 0);
                continue;
            }
            ScEntity e = sim.get(id);
            if (e == null) {
                continue;
            }
            int sx = e.tx * 32 - cx;
            int sy = e.ty * 32 - cy;
            if (e.type == ScDefs.MINERAL) {
                ScArt.drawMineral(g, sx, sy, e.amount / 1500.0, 0);
            } else if (e.type == ScDefs.GEYSER) {
                ScArt.drawGeyser(g, sx, sy, frame);
            } else {
                double frac = 1.0;
                if (e.done == 0) {
                    frac = e.progress / (double) ScDefs.buildTime[e.type];
                }
                int firing = e.cooldown > ScDefs.cooldown[e.type] - 3 ? 1 : 0;
                ScArt.drawStructure(g, e.type, sx, sy, e.owner, frac, frame, e.facing, firing);
                // training indicator light
                if (e.done != 0 && e.qLen > 0 && frame % 20 < 10) {
                    g.setColor(new Color(120, 255, 160));
                    g.fillRect(sx + e.tw * 16 - 2, sy + e.th * 32 - 6, 4, 3);
                }
            }
        }
        // pass 2: units sorted by y
        n = 0;
        i = 0;
        while (i < sim.ents.size() && n < 2000) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.isUnit() == 0 || e.hidden != 0) {
                continue;
            }
            double sx = e.x - cx;
            double sy = e.y - cy;
            if (sx < -20 || sy < -30 || sx > vw + 20 || sy > vh + 20) {
                continue;
            }
            if (game.canSee(e) == 0) {
                continue;
            }
            order[n] = e.id;
            sortKey[n] = (long) (e.y * 10);
            n = n + 1;
        }
        sortOrder(n);
        i = 0;
        while (i < n) {
            ScEntity e = sim.get(order[i]);
            i = i + 1;
            if (e == null) {
                continue;
            }
            double sx = e.x - cx;
            double sy = e.y - cy;
            if (game.isSelected(e.id) != 0) {
                selectionRing(g, e, sx, sy);
            }
            g.save();
            g.translate(sx, sy);
            g.scale(1.3, 1.3);
            if (e.type == ScDefs.WORKER) {
                int working = (e.order == ScEntity.O_CONSTRUCT || e.cooldown > 18) ? 1 : 0;
                ScArt.drawWorker(g, 0, 0, e.facing, e.owner, e.carryAmt > 0 ? e.carryType : 0, working, frame);
            } else {
                int firing = e.cooldown > ScDefs.cooldown[e.type] - 2 ? 1 : 0;
                ScArt.drawRifleman(g, 0, 0, e.facing, e.owner, firing, frame);
            }
            g.restore();
        }
        // selection rings and bars for buildings / health bars
        i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.hidden != 0 || game.canSee(e) == 0) {
                continue;
            }
            int sel = game.isSelected(e.id);
            if (e.isBuilding() != 0) {
                double sx = e.tx * 32 - cx;
                double sy = e.ty * 32 - cy;
                if (sx > vw || sy > vh || sx + e.tw * 32 < 0 || sy + e.th * 32 < 0) {
                    continue;
                }
                if (sel != 0) {
                    g.setStroke(ScArt.mid);
                    g.setColor(e.owner == game.viewPlayer || e.owner == game.me && game.viewPlayer >= 0 ? new Color(90, 255, 120) : new Color(255, 80, 70));
                    g.drawRoundRect(sx - 2, sy - 2, e.tw * 32 + 4, e.th * 32 + 4, 8);
                }
                if (sel != 0 || e.hp < e.maxHp || e.done == 0) {
                    healthBar(g, sx + 4, sy - 9, e.tw * 32 - 8, 5, e.hp, e.maxHp, e.done == 0);
                }
            } else if (e.isUnit() != 0) {
                double sx = e.x - cx;
                double sy = e.y - cy;
                if (sx < -20 || sy < -30 || sx > vw + 20 || sy > vh + 20) {
                    continue;
                }
                if (sel != 0 || e.hp < e.maxHp) {
                    healthBar(g, sx - 12, sy - 22, 24, 4, e.hp, e.maxHp, false);
                }
            } else if (sel != 0) {
                double sx = e.tx * 32 - cx;
                double sy = e.ty * 32 - cy;
                g.setStroke(ScArt.mid);
                g.setColor(new Color(255, 230, 90));
                g.drawRoundRect(sx - 2, sy - 2, e.tw * 32 + 4, e.th * 32 + 4, 8);
            }
        }
    }

    void sortOrder(int n) {
        int i = 1;
        while (i < n) {
            int id = order[i];
            long k = sortKey[i];
            int j = i - 1;
            while (j >= 0 && sortKey[j] > k) {
                order[j + 1] = order[j];
                sortKey[j + 1] = sortKey[j];
                j = j - 1;
            }
            order[j + 1] = id;
            sortKey[j + 1] = k;
            i = i + 1;
        }
    }

    void selectionRing(Graphics g, ScEntity e, double sx, double sy) {
        g.setStroke(ScArt.mid);
        int mine = (game.viewPlayer >= 0 && e.owner == game.viewPlayer) ? 1 : 0;
        if (game.viewPlayer < 0) {
            mine = 1;
        }
        g.setColor(mine != 0 ? new Color(90, 255, 120) : new Color(255, 80, 70));
        g.drawEllipse(sx - 14, sy - 5, 28, 18);
    }

    void healthBar(Graphics g, double x, double y, double w, double h, int hp, int max, boolean building) {
        double f = Math.max(0, Math.min(1, hp / (double) max));
        g.setColor(black(190));
        g.fillRect(x - 1, y - 1, w + 2, h + 2);
        Color c = new Color(70, 220, 90);
        if (f < 0.66) {
            c = new Color(235, 210, 60);
        }
        if (f < 0.33) {
            c = new Color(235, 60, 50);
        }
        if (building) {
            c = new Color(90, 170, 255);
        }
        g.setColor(c);
        g.fillRect(x, y, w * f, h);
    }

    void paintEffects(Graphics g, int cx, int cy) {
        ScSim sim = game.sim;
        int i = 0;
        while (i < sim.effects.size()) {
            ScEffect fx = sim.effects.get(i);
            i = i + 1;
            double x0 = fx.x0 - cx;
            double y0 = fx.y0 - cy;
            double f = fx.ttl / (double) fx.maxTtl;
            if (fx.kind == 0) {
                g.setStroke(ScArt.mid);
                g.setColor(new Color(255, 236, 140, (int) (80 + 175 * f)));
                g.drawLine(x0, y0 - 3, fx.x1 - cx, fx.y1 - cy - 3);
                g.setColor(new Color(255, 255, 255, (int) (255 * f)));
                g.fillEllipse(fx.x1 - cx - 3, fx.y1 - cy - 6, 6, 6);
            } else if (fx.kind == 2) {
                g.setColor(new Color(255, 255, 200, (int) (200 * f)));
                g.fillEllipse(fx.x1 - cx - 4, fx.y1 - cy - 4, 8, 8);
            } else {
                double p = 1 - f;
                double r = 8 + 26 * p;
                g.setColor(new Color(255, 140, 40, (int) (200 * f)));
                g.fillEllipse(x0 - r, y0 - r, r * 2, r * 2);
                g.setColor(new Color(255, 230, 120, (int) (220 * f)));
                g.fillEllipse(x0 - r * 0.6, y0 - r * 0.6, r * 1.2, r * 1.2);
                g.setColor(new Color(70, 60, 56, (int) (150 * f)));
                g.fillEllipse(x0 - r * 0.4, y0 - r * 1.3, r * 0.8, r * 0.8);
            }
        }
    }

    int darkness(int vp, int tx, int ty) {
        ScSim sim = game.sim;
        if (tx < 0 || ty < 0 || tx >= sim.w || ty >= sim.h) {
            return 255;
        }
        int k = ty * sim.w + tx;
        if (sim.vis[vp][k] != 0) {
            return 0;
        }
        if (sim.seen[vp][k] != 0) {
            return 120;
        }
        return 255;
    }

    int[] dark = new int[80 * 50];
    int[] rowV = new int[160];

    void paintFog(Graphics g, int cx, int cy, int tx0, int ty0, int tx1, int ty1) {
        int vp = game.viewPlayer;
        if (vp < 0) {
            return;
        }
        int cols = tx1 - tx0 + 3;
        int rows = ty1 - ty0 + 3;
        if (cols > 80 || rows > 50) {
            return;
        }
        // darkness of tiles tx0-1 .. tx1+1
        int r = 0;
        while (r < rows) {
            int c = 0;
            while (c < cols) {
                dark[r * 80 + c] = darkness(vp, tx0 - 1 + c, ty0 - 1 + r);
                c = c + 1;
            }
            r = r + 1;
        }
        int ty = ty0;
        while (ty <= ty1) {
            int qy = 0;
            while (qy < 2) {
                // compute the values of this cell row
                int n = 0;
                int tx = tx0;
                while (tx <= tx1) {
                    int lc = tx - tx0 + 1;
                    int lr = ty - ty0 + 1;
                    int a = dark[lr * 80 + lc];
                    int dyy = qy == 0 ? -1 : 1;
                    int qx = 0;
                    while (qx < 2) {
                        int dxx = qx == 0 ? -1 : 1;
                        int b = dark[lr * 80 + lc + dxx];
                        int c2 = dark[(lr + dyy) * 80 + lc];
                        int d = dark[(lr + dyy) * 80 + lc + dxx];
                        rowV[n] = (a * 4 + b * 2 + c2 * 2 + d) / 9;
                        n = n + 1;
                        qx = qx + 1;
                    }
                    tx = tx + 1;
                }
                // draw runs
                int i = 0;
                double y = ty * 32 - cy + qy * 16;
                while (i < n) {
                    int v = rowV[i];
                    int j = i + 1;
                    while (j < n && rowV[j] == v) {
                        j = j + 1;
                    }
                    if (v > 0) {
                        g.setColor(new Color(0, 0, 0, v));
                        g.fillRect(tx0 * 32 - cx + i * 16, y, (j - i) * 16, 16);
                    }
                    i = j;
                }
                qy = qy + 1;
            }
            ty = ty + 1;
        }
    }

    void paintOverlays(Graphics g, int cx, int cy) {
        ScSim sim = game.sim;
        // order markers
        int i = 0;
        while (i < game.markers.size()) {
            double[] m = game.markers.get(i);
            i = i + 1;
            double age = (game.frame - (long) m[3]) / 30.0;
            int kind = (int) m[2];
            double r = 6 + 18 * age;
            int a = (int) (255 * (1 - age));
            if (a < 0) {
                a = 0;
            }
            g.setStroke(ScArt.mid);
            if (kind == 1 || kind == 2) {
                g.setColor(new Color(255, 80, 70, a));
            } else if (kind == 3) {
                g.setColor(new Color(110, 200, 255, a));
            } else if (kind == 4) {
                g.setColor(new Color(255, 230, 90, a));
            } else {
                g.setColor(new Color(90, 255, 120, a));
            }
            g.drawEllipse(m[0] - cx - r, m[1] - cy - r * 0.6, r * 2, r * 1.2);
        }
        // alert ping
        if (game.mode == ScGame.PLAY && sim.tick - game.alertTick < 24 * 4) {
            double age = (sim.tick - game.alertTick) / 96.0;
            g.setStroke(ScArt.thick);
            g.setColor(new Color(255, 60, 50, (int) (230 * (1 - age))));
            double r = 20 + 60 * age;
            g.drawEllipse(game.alertX - cx - r, game.alertY - cy - r, r * 2, r * 2);
        }
        // build placement ghost
        if (game.mode == ScGame.PLAY && game.cmdMode == ScGame.M_BUILD && game.my < ScGame.VH) {
            int t = game.cmdType;
            double wx = game.mx + game.camX;
            double wy = game.my + game.camY;
            int tx = game.placeTileX(wx, t);
            int ty = game.placeTileY(wy, t);
            if (t == ScDefs.REFINERY) {
                ScEntity ge = game.entityAt(wx, wy);
                if (ge != null && ge.type == ScDefs.GEYSER) {
                    tx = ge.tx;
                    ty = ge.ty;
                }
            }
            int ok = game.placementValid(t, tx, ty);
            ScArt.drawStructure(g, t, tx * 32 - cx, ty * 32 - cy, game.me, 1.0, game.frame, 1.5, 0);
            int yy = 0;
            while (yy < ScDefs.th[t]) {
                int xx = 0;
                while (xx < ScDefs.tw[t]) {
                    int bad = 0;
                    if (t == ScDefs.REFINERY) {
                        bad = ok == 0 ? 1 : 0;
                    } else {
                        bad = (sim.blockedTile(tx + xx, ty + yy) != 0 || sim.map.get(tx + xx, ty + yy) > 1 || ok == 0) ? 1 : 0;
                    }
                    g.setColor(bad != 0 ? new Color(255, 40, 30, 110) : new Color(40, 255, 70, 90));
                    g.fillRect((tx + xx) * 32 - cx, (ty + yy) * 32 - cy, 32, 32);
                    xx = xx + 1;
                }
                yy = yy + 1;
            }
        }
        // targeting cursor
        if (game.mode == ScGame.PLAY && (game.cmdMode == ScGame.M_ATTACK || game.cmdMode == ScGame.M_MOVE || game.cmdMode == ScGame.M_RALLY) && game.my < ScGame.VH) {
            g.setStroke(ScArt.mid);
            g.setColor(game.cmdMode == ScGame.M_ATTACK ? new Color(255, 70, 60) : new Color(90, 255, 120));
            double x = game.mx;
            double y = game.my;
            g.drawEllipse(x - 10, y - 10, 20, 20);
            g.drawLine(x - 16, y, x - 5, y);
            g.drawLine(x + 5, y, x + 16, y);
            g.drawLine(x, y - 16, x, y - 5);
            g.drawLine(x, y + 5, x, y + 16);
        }
        // selection box
        if (game.boxing != 0 && game.my >= 0) {
            double x0 = Math.min(game.bx0, game.mx);
            double y0 = Math.min(game.by0, Math.min(game.my, ScGame.VH));
            double x1 = Math.max(game.bx0, game.mx);
            double y1 = Math.max(game.by0, Math.min(game.my, ScGame.VH));
            g.setStroke(ScArt.thin);
            g.setColor(new Color(90, 255, 120, 40));
            g.fillRect(x0, y0, x1 - x0, y1 - y0);
            g.setColor(new Color(90, 255, 120));
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
        }
    }

    // ------------------------------------------------------------------ HUD

    int inProgressBar(int x, int y) {
        return (x >= 200 && x < 900 && y >= 756 && y < 776) ? 1 : 0;
    }

    double progressFrac(int x) {
        return Math.max(0, Math.min(1, (x - 200) / 700.0));
    }

    int selIconAt(int x, int y) {
        if (game.selCount < 2) {
            return -1;
        }
        int col = (x - 200) / 52;
        int row = (y - 640) / 52;
        if (x < 200 || y < 640 || col > 11 || row > 2) {
            return -1;
        }
        int idx = row * 6 + col;
        if (col >= 6) {
            return -1;
        }
        return idx;
    }

    void paintHud(Graphics g) {
        ScSim sim = game.sim;
        int hy = ScGame.HUD_Y;
        g.setColor(panel);
        g.fillRect(0, hy, ScGame.W, ScGame.H - hy);
        g.setColor(panelEdge);
        g.fillRect(0, hy, ScGame.W, 2);
        g.setColor(new Color(12, 14, 20));
        g.fillRect(0, hy + 2, ScGame.W, 2);
        paintMinimap(g);
        paintInfo(g);
        paintCard(g);
        paintTopBar(g);
    }

    void paintTopBar(Graphics g) {
        ScSim sim = game.sim;
        g.setColor(black(150));
        g.fillRect(0, 0, ScGame.W, 26);
        int vp = game.viewPlayer < 0 ? 0 : game.viewPlayer;
        if (game.mode == ScGame.REPLAY) {
            int p = 0;
            double x = 14;
            while (p < sim.playerCount) {
                ScPlayer pl = sim.players[p];
                g.setColor(ScArt.teamColor(p, 0));
                g.fillRect(x, 6, 12, 14);
                String s = " " + pl.minerals + "m  " + pl.gas + "g  " + pl.supplyUsed + "/" + pl.supplyCap + (pl.alive == 0 ? "  (out)" : "");
                text(g, s, x + 14, 19, fHud, textColor);
                x = x + 300;
                p = p + 1;
            }
        } else {
            ScPlayer pl = sim.players[game.me];
            g.setColor(ScArt.crystal);
            g.fillRect(ScGame.W - 410, 6, 11, 14);
            g.setColor(ScArt.crystalLight);
            g.fillRect(ScGame.W - 408, 8, 3, 5);
            text(g, "" + pl.minerals, ScGame.W - 392, 19, fHud, textColor);
            g.setColor(new Color(60, 200, 90));
            g.fillEllipse(ScGame.W - 330, 6, 13, 14);
            text(g, "" + pl.gas, ScGame.W - 312, 19, fHud, textColor);
            boolean blocked = pl.supplyUsed >= pl.supplyCap;
            g.setColor(blocked && game.frame % 30 < 15 ? new Color(255, 70, 60) : new Color(220, 220, 230));
            g.fillRoundRect(ScGame.W - 250, 6, 14, 14, 3);
            text(g, pl.supplyUsed + "/" + pl.supplyCap, ScGame.W - 230, 19, fHud, blocked ? new Color(255, 120, 110) : textColor);
        }
        textRight(g, clock(sim.tick), ScGame.W - 14, 19, fHud, gold);
        String sp = "" + ScGame.SPEEDS[game.speedIdx];
        text(g, sp + "x", ScGame.W - 150, 19, fSmall, dim);
        // message line
        String msg = "";
        if (game.frame - game.noticeFrame < 150) {
            msg = game.notice;
        } else if (game.mode == ScGame.PLAY && sim.tick - sim.players[game.me].messageTick < 72) {
            msg = sim.players[game.me].message;
        }
        if (msg.length() > 0) {
            textCenter(g, msg, ScGame.VW / 2.0, 52, fMid, new Color(255, 200, 120));
        }
    }

    void paintMinimap(Graphics g) {
        ScSim sim = game.sim;
        int x = ScGame.MM_X;
        int y = ScGame.MM_Y;
        int s = ScGame.MM_S;
        bevel(g, x - 5, y - 5, s + 10, s + 10, new Color(14, 16, 22));
        g.setClip(x, y, s, s);
        g.drawImageScaled(mmTerrain, x, y, s, s);
        double sc = s / (double) sim.w;
        // resources
        int vp = game.viewPlayer;
        int i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.hidden != 0) {
                continue;
            }
            if (e.isResource() != 0) {
                if (vp >= 0 && sim.seen[vp][game.clampTile(e.ty) * sim.w + game.clampTile(e.tx)] == 0) {
                    continue;
                }
                g.setColor(e.type == ScDefs.MINERAL ? new Color(110, 190, 255) : new Color(80, 230, 110));
                g.fillRect(x + e.tx * sc, y + e.ty * sc, Math.max(2, e.tw * sc), Math.max(2, e.th * sc));
            }
        }
        i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.hidden != 0 || e.owner < 0 || game.canSee(e) == 0) {
                continue;
            }
            g.setColor(ScArt.teamColor(e.owner, 0));
            if (e.isBuilding() != 0) {
                g.fillRect(x + e.tx * sc, y + e.ty * sc, Math.max(3, e.tw * sc), Math.max(3, e.th * sc));
            } else {
                g.fillRect(x + e.x / 32 * sc - 1, y + e.y / 32 * sc - 1, 3, 3);
            }
        }
        g.drawImageScaled(mmFog, x, y, s, s);
        // camera
        g.setStroke(ScArt.thin);
        g.setColor(new Color(255, 255, 255, 230));
        g.drawRect(x + game.camX / 32 * sc, y + game.camY / 32 * sc, ScGame.VW / 32.0 * sc, ScGame.VH / 32.0 * sc);
        if (game.mode == ScGame.PLAY && sim.tick - game.alertTick < 24 * 6) {
            double r = 4 + ((game.frame / 3) % 8);
            g.setColor(new Color(255, 60, 50));
            g.drawEllipse(x + game.alertX / 32 * sc - r, y + game.alertY / 32 * sc - r, r * 2, r * 2);
        }
        g.clearClip();
    }

    void unitIcon(Graphics g, ScEntity e, double x, double y, double w, double h) {
        g.save();
        g.setClip(x, y, w, h);
        g.translate(x + w / 2, y + h / 2 + 2);
        double s = Math.min(w, h) / 22.0;
        if (e.isBuilding() != 0) {
            s = Math.min(w, h) / (Math.max(e.tw, e.th) * 34.0);
            g.scale(s, s);
            ScArt.drawStructure(g, e.type, -e.tw * 16, -e.th * 16, e.owner, 1.0, game.frame, 1.5, 0);
        } else if (e.type == ScDefs.WORKER) {
            g.scale(s, s);
            ScArt.drawWorker(g, 0, 0, 0.6, e.owner, 0, 0, game.frame);
        } else {
            g.scale(s, s);
            ScArt.drawRifleman(g, 0, 0, 0.6, e.owner, 0, game.frame);
        }
        g.restore();
    }

    void typeIcon(Graphics g, int type, int owner, double x, double y, double w, double h) {
        g.save();
        g.setClip(x, y, w, h);
        g.translate(x + w / 2, y + h / 2 + 2);
        if (ScDefs.building[type] == 1) {
            double s = Math.min(w, h) / (Math.max(ScDefs.tw[type], ScDefs.th[type]) * 34.0);
            g.scale(s, s);
            ScArt.drawStructure(g, type, -ScDefs.tw[type] * 16, -ScDefs.th[type] * 16, owner, 1.0, game.frame, 1.5, 0);
        } else if (type == ScDefs.WORKER) {
            double s = Math.min(w, h) / 22.0;
            g.scale(s, s);
            ScArt.drawWorker(g, 0, 0, 0.6, owner, 0, 0, game.frame);
        } else {
            double s = Math.min(w, h) / 22.0;
            g.scale(s, s);
            ScArt.drawRifleman(g, 0, 0, 0.6, owner, 0, game.frame);
        }
        g.restore();
    }

    String orderName(ScEntity e) {
        int o = e.order;
        if (e.hidden != 0) {
            return "Harvesting";
        }
        if (o == ScEntity.O_MOVE) {
            return "Moving";
        }
        if (o == ScEntity.O_ATTACKMOVE) {
            return "Attack-moving";
        }
        if (o == ScEntity.O_ATTACK) {
            return "Attacking";
        }
        if (o == ScEntity.O_GATHER) {
            return "Gathering";
        }
        if (o == ScEntity.O_RETURN) {
            return "Returning cargo";
        }
        if (o == ScEntity.O_BUILD) {
            return "Moving to build site";
        }
        if (o == ScEntity.O_CONSTRUCT) {
            return "Constructing";
        }
        if (o == ScEntity.O_HOLD) {
            return "Holding position";
        }
        return "Idle";
    }

    void paintInfo(Graphics g) {
        ScSim sim = game.sim;
        int x0 = 190;
        int y0 = 634;
        bevel(g, x0 - 4, y0 - 2, 740, 160, new Color(18, 21, 29));
        if (game.mode == ScGame.REPLAY) {
            paintReplayPanel(g);
            return;
        }
        if (game.selCount == 0) {
            text(g, "COLONISTS", x0 + 10, y0 + 24, fMid, gold);
            text(g, "Left-drag select  Right-click order  A attack  M move  S stop  H hold", x0 + 10, y0 + 52, fSmall, dim);
            text(g, "B build (workers)  W/R train  Y rally  Ctrl+1..9 groups  Space jump to alert", x0 + 10, y0 + 70, fSmall, dim);
            text(g, "P pause   +/- speed   arrows or screen edge scroll", x0 + 10, y0 + 88, fSmall, dim);
            ScPlayer pl = sim.players[game.me];
            text(g, "Kills " + pl.kills + "   Lost " + pl.unitsLost + "   Mined " + pl.mined, x0 + 10, y0 + 128, fHud, textColor);
            return;
        }
        if (game.selCount == 1) {
            ScEntity e = game.firstSel();
            if (e == null) {
                return;
            }
            bevel(g, x0 + 4, y0 + 4, 92, 92, new Color(8, 10, 14));
            unitIcon(g, e, x0 + 6, y0 + 6, 88, 88);
            String nm = ScDefs.name[e.type];
            text(g, nm, x0 + 112, y0 + 22, fMid, e.owner >= 0 ? ScArt.teamColor(e.owner, 1) : textColor);
            if (e.isResource() != 0) {
                text(g, "Remaining: " + e.amount, x0 + 112, y0 + 46, fHud, textColor);
                return;
            }
            text(g, e.hp + " / " + e.maxHp, x0 + 112, y0 + 44, fHud, textColor);
            healthBar(g, x0 + 112, y0 + 50, 180, 8, e.hp, e.maxHp, e.done == 0);
            if (e.isUnit() != 0) {
                text(g, "Damage " + ScDefs.damage[e.type] + "   Armor " + ScDefs.armor[e.type] + "   Range " + ScDefs.range[e.type] / 32.0, x0 + 112, y0 + 76, fSmall, dim);
                text(g, orderName(e), x0 + 112, y0 + 96, fSmall, textColor);
                if (e.carryAmt > 0) {
                    text(g, "Carrying " + e.carryAmt + (e.carryType == 2 ? " gas" : " minerals"), x0 + 112, y0 + 114, fSmall, textColor);
                }
            } else {
                if (e.done == 0) {
                    text(g, "Under construction", x0 + 112, y0 + 76, fHud, textColor);
                    double f = e.progress / (double) ScDefs.buildTime[e.type];
                    g.setColor(black(200));
                    g.fillRect(x0 + 112, y0 + 84, 300, 12);
                    g.setColor(new Color(90, 170, 255));
                    g.fillRect(x0 + 113, y0 + 85, 298 * f, 10);
                } else {
                    if (ScDefs.damage[e.type] > 0) {
                        text(g, "Damage " + ScDefs.damage[e.type] + "   Range " + ScDefs.range[e.type] / 32.0, x0 + 112, y0 + 76, fSmall, dim);
                    }
                    if (ScDefs.provides[e.type] > 0) {
                        text(g, "Supply +" + ScDefs.provides[e.type], x0 + 112, y0 + 76, fSmall, dim);
                    }
                    paintQueue(g, e, x0 + 112, y0 + 92);
                }
            }
            return;
        }
        // multiple
        int i = 0;
        while (i < game.selCount && i < 12) {
            ScEntity e = sim.get(game.sel[i]);
            if (e != null) {
                int col = i % 6;
                int row = i / 6;
                double ix = x0 + 10 + col * 52;
                double iy = y0 + 6 + row * 52;
                bevel(g, ix, iy, 48, 44, new Color(8, 10, 14));
                unitIcon(g, e, ix + 1, iy + 1, 46, 36);
                healthBar(g, ix + 4, iy + 37, 40, 4, e.hp, e.maxHp, false);
            }
            i = i + 1;
        }
        text(g, "" + game.selCount + " units selected", x0 + 10, y0 + 122, fHud, textColor);
        // worker/army summary
        ScEntity f = game.firstSel();
        if (f != null) {
            text(g, "Orders: " + orderName(f), x0 + 340, y0 + 122, fSmall, dim);
        }
    }

    void paintQueue(Graphics g, ScEntity e, double x, double y) {
        int i = 0;
        while (i < 5) {
            double ix = x + i * 46;
            bevel(g, ix, y, 40, 34, new Color(8, 10, 14));
            if (i < e.qLen) {
                typeIcon(g, e.queue[i], e.owner, ix + 1, y + 1, 38, 28);
                if (i == 0) {
                    double f = e.qProg / (double) ScDefs.buildTime[e.queue[0]];
                    g.setColor(black(200));
                    g.fillRect(ix + 2, y + 28, 36, 4);
                    g.setColor(new Color(90, 255, 140));
                    g.fillRect(ix + 2, y + 28, 36 * f, 4);
                }
            }
            i = i + 1;
        }
        if (e.rallyX >= 0) {
            text(g, "Rally set", x + 240, y + 22, fSmall, dim);
        }
    }

    void paintReplayPanel(Graphics g) {
        ScSim sim = game.sim;
        ScReplay r = game.rep;
        int x0 = 190;
        int y0 = 634;
        text(g, "REPLAY", x0 + 10, y0 + 24, fMid, gold);
        String who = game.viewPlayer < 0 ? "Viewing: all players (no fog)" : "Viewing: Player " + (game.viewPlayer + 1);
        text(g, who, x0 + 120, y0 + 24, fHud, textColor);
        text(g, "Space pause  +/- speed  [ ] jump 10s  R restart  1/2/3 perspective  Esc menu", x0 + 10, y0 + 50, fSmall, dim);
        String res = r.winner >= 0 ? "Winner: Player " + (r.winner + 1) : "Unfinished";
        text(g, res + "   Length " + clock(r.length) + "   AI level " + r.level, x0 + 10, y0 + 72, fSmall, dim);
        // players table
        int p = 0;
        while (p < sim.playerCount) {
            ScPlayer pl = sim.players[p];
            double y = y0 + 98 + p * 18;
            g.setColor(ScArt.teamColor(p, 0));
            g.fillRect(x0 + 10, y - 11, 12, 12);
            text(g, "Player " + (p + 1) + "   units lost " + pl.unitsLost + "   kills " + pl.kills + "   buildings " + pl.buildingsBuilt + "   mined " + pl.mined, x0 + 30, y, fSmall, textColor);
            p = p + 1;
        }
        // progress bar
        g.setColor(black(220));
        g.fillRect(200, 756, 700, 14);
        double f = r.length > 0 ? Math.min(1.0, sim.tick / (double) r.length) : 0;
        g.setColor(new Color(90, 170, 255));
        g.fillRect(201, 757, 698 * f, 12);
        g.setColor(white());
        g.fillRect(200 + 698 * f, 753, 3, 20);
        text(g, clock(sim.tick) + " / " + clock(r.length), 200, 788, fSmall, dim);
        if (game.paused != 0) {
            textRight(g, "paused", 900, 788, fSmall, gold);
        } else if (game.replayDone != 0) {
            textRight(g, "end of replay - R to restart", 900, 788, fSmall, gold);
        }
    }

    Color white() {
        return new Color(255, 255, 255);
    }

    void paintCard(Graphics g) {
        game.buildCard();
        int i = 0;
        while (i < 9) {
            int col = i % 3;
            int row = i / 3;
            double x = ScGame.CARD_X + col * (ScGame.CARD_W + ScGame.CARD_GAP);
            double y = ScGame.CARD_Y + row * (ScGame.CARD_H + ScGame.CARD_GAP);
            if (game.cardAct[i] == ScGame.A_NONE) {
                g.setColor(new Color(16, 19, 26));
                g.fillRect(x, y, ScGame.CARD_W, ScGame.CARD_H);
                i = i + 1;
                continue;
            }
            int on = game.cardOn[i];
            bevel(g, x, y, ScGame.CARD_W, ScGame.CARD_H, on != 0 ? panelLight : new Color(34, 38, 48));
            int act = game.cardAct[i];
            if (act == ScGame.A_BUILD || act == ScGame.A_TRAIN) {
                typeIcon(g, game.cardArg[i], game.me, x + 3, y + 2, ScGame.CARD_W - 6, ScGame.CARD_H - 16);
                text(g, "" + ScDefs.cost[game.cardArg[i]], x + 4, y + ScGame.CARD_H - 4, fSmall, on != 0 ? new Color(150, 210, 255) : new Color(150, 90, 90));
            } else {
                drawGlyph(g, act, x, y, on);
            }
            text(g, "" + game.cardKey[i], x + ScGame.CARD_W - 12, y + 12, fSmall, gold);
            if (act == ScGame.A_BACK) {
                text(g, "esc", x + ScGame.CARD_W - 28, y + 12, fSmall, gold);
            }
            if (on == 0) {
                g.setColor(black(110));
                g.fillRect(x, y, ScGame.CARD_W, ScGame.CARD_H);
            }
            // tooltip
            if (game.mx >= x && game.mx < x + ScGame.CARD_W && game.my >= y && game.my < y + ScGame.CARD_H) {
                String tip = game.cardLabel[i];
                if (act == ScGame.A_BUILD || act == ScGame.A_TRAIN) {
                    tip = tip + "  " + ScDefs.cost[game.cardArg[i]] + " minerals";
                    if (ScDefs.supply[game.cardArg[i]] > 0) {
                        tip = tip + "  supply " + ScDefs.supply[game.cardArg[i]];
                    }
                }
                double tw = g.measureText(tip, fHud).width + 16;
                bevel(g, ScGame.CARD_X + 180 - tw, ScGame.CARD_Y - 34, tw, 26, new Color(10, 12, 18));
                text(g, tip, ScGame.CARD_X + 188 - tw, ScGame.CARD_Y - 16, fHud, textColor);
            }
            i = i + 1;
        }
    }

    void drawGlyph(Graphics g, int act, double x, double y, int on) {
        double cx = x + ScGame.CARD_W / 2.0;
        double cy = y + ScGame.CARD_H / 2.0 + 2;
        g.setStroke(ScArt.thick);
        Color c = new Color(190, 235, 255);
        g.setColor(c);
        if (act == ScGame.A_MOVE) {
            g.drawLine(cx - 12, cy, cx + 10, cy);
            g.drawLine(cx + 10, cy, cx + 3, cy - 7);
            g.drawLine(cx + 10, cy, cx + 3, cy + 7);
        } else if (act == ScGame.A_STOP) {
            g.setColor(new Color(255, 120, 110));
            g.fillRect(cx - 9, cy - 9, 18, 18);
        } else if (act == ScGame.A_HOLD) {
            g.drawRoundRect(cx - 10, cy - 11, 20, 22, 6);
            g.drawLine(cx - 6, cy - 3, cx + 6, cy - 3);
        } else if (act == ScGame.A_ATTACK) {
            g.setColor(new Color(255, 110, 100));
            g.drawLine(cx - 11, cy + 11, cx + 9, cy - 9);
            g.drawLine(cx + 9, cy - 9, cx + 9, cy - 1);
            g.drawLine(cx + 9, cy - 9, cx + 1, cy - 9);
            g.drawLine(cx - 4, cy + 4, cx - 10, cy - 2);
        } else if (act == ScGame.A_GATHER) {
            g.setColor(ScArt.crystal);
            g.fillRect(cx - 7, cy - 8, 14, 16);
            g.setColor(ScArt.crystalLight);
            g.fillRect(cx - 5, cy - 6, 4, 6);
        } else if (act == ScGame.A_RETURN) {
            g.drawLine(cx + 10, cy, cx - 10, cy);
            g.drawLine(cx - 10, cy, cx - 3, cy - 7);
            g.drawLine(cx - 10, cy, cx - 3, cy + 7);
        } else if (act == ScGame.A_BUILDMENU) {
            g.setColor(new Color(240, 210, 90));
            g.drawRect(cx - 12, cy - 4, 24, 14);
            g.drawLine(cx - 12, cy - 4, cx, cy - 12);
            g.drawLine(cx, cy - 12, cx + 12, cy - 4);
        } else if (act == ScGame.A_RALLY) {
            g.setColor(new Color(255, 230, 90));
            g.drawLine(cx - 6, cy + 12, cx - 6, cy - 12);
            g.fillRect(cx - 5, cy - 12, 14, 9);
        } else if (act == ScGame.A_CANCEL || act == ScGame.A_BACK) {
            g.setColor(new Color(255, 120, 110));
            g.drawLine(cx - 9, cy - 9, cx + 9, cy + 9);
            g.drawLine(cx - 9, cy + 9, cx + 9, cy - 9);
        }
    }

    // ------------------------------------------------------------------ menus

    void stars(Graphics g, long frame) {
        int i = 0;
        while (i < 160) {
            int h = ScArt.hash(i, 3, 99);
            double x = (h % ScGame.W + frame * (0.15 + (h % 7) * 0.05)) % ScGame.W;
            double y = (h / 7) % ScGame.H;
            int a = 80 + (h % 150);
            if ((frame / 20 + i) % 17 == 0) {
                a = 255;
            }
            g.setColor(new Color(200, 220, 255, a));
            double sz = 1 + (h % 3 == 0 ? 1 : 0);
            g.fillRect(x, y, sz, sz);
            i = i + 1;
        }
    }

    void paintMenu(Graphics g) {
        long f = game.frame;
        int w = ScGame.W;
        int h = ScGame.H;
        int band = 0;
        while (band < 16) {
            g.setColor(new Color(6 + band, 8 + band * 2, 20 + band * 3));
            g.fillRect(0, band * h / 16.0, w, h / 16.0 + 1);
            band = band + 1;
        }
        stars(g, f);
        // planet
        g.setColor(new Color(30, 60, 90));
        g.fillEllipse(w - 520, h - 330, 900, 900);
        g.setColor(new Color(46, 96, 130));
        g.fillEllipse(w - 500, h - 320, 860, 860);
        g.setColor(new Color(70, 140, 160, 120));
        g.fillEllipse(w - 440, h - 300, 500, 200);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillEllipse(w - 330, h - 220, 700, 700);
        // showcase units
        g.save();
        g.translate(140, 560);
        g.scale(2.4, 2.4);
        ScArt.drawStructure(g, ScDefs.HUB, 0, 0, 0, 1.0, f, 1.5, 0);
        g.restore();
        g.save();
        g.translate(520, 640);
        g.scale(3.0, 3.0);
        ScArt.drawRifleman(g, 0, 0, 0.3, 0, (f / 18) % 3 == 0 ? 1 : 0, f);
        ScArt.drawRifleman(g, 22, 8, 0.1, 0, 0, f);
        ScArt.drawWorker(g, -22, 6, 0.4, 0, 1, 0, f);
        g.restore();
        g.save();
        g.translate(820, 650);
        g.scale(3.0, 3.0);
        ScArt.drawRifleman(g, 0, 0, 3.3, 1, (f / 18) % 3 == 1 ? 1 : 0, f);
        ScArt.drawRifleman(g, 24, -6, 3.0, 1, 0, f);
        g.restore();
        textCenter(g, "COLONISTS", w / 2.0, 190, fTitle, new Color(130, 200, 255));
        textCenter(g, "a real-time strategy game written in JNative", w / 2.0, 232, fMid, textColor);
        if (game.menuPage == 0) {
            String[] items = new String[] { "[N]  New game vs computer", "[D]  Difficulty:  " + diffName(game.difficulty), "[M]  Map size:  " + game.mapSize + " x " + game.mapSize, "[R]  Watch a replay", "[Q]  Quit" };
            int i = 0;
            while (i < items.length) {
                textCenter(g, items[i], w / 2.0, 330 + i * 44, fBig, i == 0 ? gold : textColor);
                i = i + 1;
            }
            textCenter(g, "Every match is recorded automatically; replays are saved to ./replays", w / 2.0, 330 + 5 * 44 + 20, fSmall, dim);
        } else {
            textCenter(g, "REPLAYS", w / 2.0, 300, fBig, gold);
            if (game.replayFiles.size() == 0) {
                textCenter(g, "No replays yet - finish a game first", w / 2.0, 360, fHud, dim);
            }
            int i = 0;
            while (i < game.replayFiles.size() && i < 12) {
                int idx = i;
                String nm = game.replayFiles.get(idx);
                boolean on = idx == game.replaySel;
                if (on) {
                    g.setColor(new Color(60, 100, 160, 140));
                    g.fillRect(w / 2.0 - 300, 330 + i * 28 - 4, 600, 26);
                }
                text(g, nm, w / 2.0 - 280, 350 + i * 28, fHud, on ? gold : textColor);
                i = i + 1;
            }
            textCenter(g, "Up/Down choose   Enter play   Delete remove   Esc back", w / 2.0, 330 + 12 * 28 + 24, fSmall, dim);
        }
    }

    String diffName(int d) {
        if (d == 0) {
            return "Easy";
        }
        if (d == 1) {
            return "Normal";
        }
        return "Hard";
    }

    void paintGameOver(Graphics g) {
        ScSim sim = game.sim;
        g.setColor(black(150));
        g.fillRect(0, 0, ScGame.VW, ScGame.VH);
        boolean won = sim.winner == game.me;
        textCenter(g, won ? "VICTORY" : "DEFEAT", ScGame.VW / 2.0, 220, fTitle, won ? gold : new Color(255, 90, 80));
        ScPlayer pl = sim.players[game.me];
        textCenter(g, "Time " + clock(sim.tick) + "    Units lost " + pl.unitsLost + "    Kills " + pl.kills + "    Buildings " + pl.buildingsBuilt + "    Mined " + pl.mined, ScGame.VW / 2.0, 280, fHud, textColor);
        if (game.lastSaved.length() > 0) {
            textCenter(g, "Replay saved: replays/" + game.lastSaved, ScGame.VW / 2.0, 320, fSmall, dim);
        }
        textCenter(g, "[W] Watch this game as a replay      [Enter] Main menu", ScGame.VW / 2.0, 380, fMid, textColor);
    }
}
