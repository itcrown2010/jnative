/**
 * Terrain grid. Generated from a seed with point symmetry so both sides get the same map.
 * Terrain: 0 grass, 1 dirt, 2 water (blocked), 3 rock (blocked).
 *
 * @author 张晓达
 */
public class ScMap {
    static int TILE = 32;
    int w;
    int h;
    byte[] terr;
    int[] startX = new int[8];
    int[] startY = new int[8];
    int startCount;
    // resource placements: type, tile x, tile y, amount
    int[] resType = new int[128];
    int[] resX = new int[128];
    int[] resY = new int[128];
    int[] resAmount = new int[128];
    int resCount;
    // expansion sites (centre tile)
    int[] expX = new int[8];
    int[] expY = new int[8];
    int expCount;

    ScMap(int width, int height) {
        w = width;
        h = height;
        terr = new byte[w * h];
    }

    int get(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) {
            return 3;
        }
        return terr[ty * w + tx];
    }

    int walkable(int tx, int ty) {
        int t = get(tx, ty);
        if (t == 0 || t == 1) {
            return 1;
        }
        return 0;
    }

    void set(int tx, int ty, int t) {
        if (tx >= 0 && ty >= 0 && tx < w && ty < h) {
            terr[ty * w + tx] = (byte) t;
        }
    }

    void clearRect(int x0, int y0, int ww, int hh) {
        int y = y0;
        while (y < y0 + hh) {
            int x = x0;
            while (x < x0 + ww) {
                if (get(x, y) >= 2) {
                    set(x, y, 0);
                }
                x = x + 1;
            }
            y = y + 1;
        }
    }

    void clearDisc(int cx, int cy, int r) {
        int y = cy - r;
        while (y <= cy + r) {
            int x = cx - r;
            while (x <= cx + r) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= r * r && get(x, y) >= 2 && x > 0 && y > 0 && x < w - 1 && y < h - 1) {
                    set(x, y, 0);
                }
                x = x + 1;
            }
            y = y + 1;
        }
    }

    void addResource(int type, int tx, int ty, int amount) {
        if (resCount < 128) {
            resType[resCount] = type;
            resX[resCount] = tx;
            resY[resCount] = ty;
            resAmount[resCount] = amount;
            resCount = resCount + 1;
        }
    }

    /** Smooth value noise in 0..1000. */
    static int noise(int[] grid, int gw, int x, int y, int cell) {
        int gx = x / cell;
        int gy = y / cell;
        int fx = x % cell;
        int fy = y % cell;
        int a = grid[gy * gw + gx];
        int b = grid[gy * gw + gx + 1];
        int c = grid[(gy + 1) * gw + gx];
        int d = grid[(gy + 1) * gw + gx + 1];
        int top = a * (cell - fx) + b * fx;
        int bot = c * (cell - fx) + d * fx;
        return (top * (cell - fy) + bot * fy) / (cell * cell);
    }

    /** Builds a symmetric 1v1 map. Players start in opposite corners. */
    static ScMap generate(long seed, int size) {
        ScRand rnd = new ScRand(seed);
        ScMap m = new ScMap(size, size);
        int cell = 8;
        int gw = size / cell + 2;
        int[] g1 = new int[gw * gw];
        int[] g2 = new int[gw * gw];
        int i = 0;
        while (i < g1.length) {
            g1[i] = rnd.next(1000);
            g2[i] = rnd.next(1000);
            i = i + 1;
        }
        int y = 0;
        while (y < size) {
            int x = 0;
            while (x < size) {
                int h1 = noise(g1, gw, x, y, cell);
                int h2 = noise(g2, gw, x, y, cell);
                int t = 0;
                if (h1 < 250) {
                    t = 2;
                } else if (h1 > 770) {
                    t = 3;
                } else if (h2 > 560) {
                    t = 1;
                }
                m.set(x, y, t);
                x = x + 1;
            }
            y = y + 1;
        }
        // point symmetry: copy the first half onto the second
        int total = size * size;
        i = 0;
        while (i < total / 2) {
            m.terr[total - 1 - i] = m.terr[i];
            i = i + 1;
        }
        // main bases
        int s0x = 16;
        int s0y = 16;
        int s1x = size - 1 - 16;
        int s1y = size - 1 - 16;
        m.startX[0] = s0x;
        m.startY[0] = s0y;
        m.startX[1] = s1x;
        m.startY[1] = s1y;
        m.startCount = 2;
        m.clearDisc(s0x, s0y, 11);
        m.clearDisc(s1x, s1y, 11);
        // expansions: two sites plus their mirror images
        m.addExpansion(40, 18, 0);
        m.addExpansion(18, 44, 0);
        m.addExpansion(40, 18, 1);
        m.addExpansion(18, 44, 1);
        // base resources: laid out for player 0 and mirrored for player 1
        m.baseResources(s0x, s0y, 0);
        m.baseResources(s0x, s0y, 1);
        m.ensureConnected();
        return m;
    }

    void addExpansion(int cx, int cy, int mirror) {
        int ex = cx;
        int ey = cy;
        if (mirror != 0) {
            ex = w - 1 - cx;
            ey = h - 1 - cy;
        }
        expX[expCount] = ex;
        expY[expCount] = ey;
        expCount = expCount + 1;
        clearDisc(ex, ey, 9);
        int[] mx = new int[]{-8, -5, -2, 1, 4, -8};
        int[] my = new int[]{-6, -7, -7, -6, -5, -3};
        int i = 0;
        while (i < 6) {
            addMirrored(7, cx + mx[i], cy + my[i], 1200, mirror);
            i = i + 1;
        }
        addMirrored(8, cx + 5, cy + 5, 5000, mirror);
    }

    /** Resources of a main base (offsets from the hub centre); mirror=1 flips to the opposite corner. */
    void baseResources(int cx, int cy, int mirror) {
        int[] mx = new int[]{-9, -6, -3, 0, 3, -9, -9};
        int[] my = new int[]{-7, -8, -8, -8, -7, -4, -1};
        int i = 0;
        while (i < 7) {
            addMirrored(7, cx + mx[i], cy + my[i], 1500, mirror);
            i = i + 1;
        }
        addMirrored(8, cx + 5, cy + 6, 5000, mirror);
    }

    void addMirrored(int type, int tx, int ty, int amount, int mirror) {
        int tw = ScDefs.tw[type];
        int th = ScDefs.th[type];
        int px = tx;
        int py = ty;
        if (mirror != 0) {
            px = w - 1 - tx - (tw - 1);
            py = h - 1 - ty - (th - 1);
        }
        clearRect(px - 1, py - 1, tw + 2, th + 2);
        addResource(type, px, py, amount);
    }

    /** Guarantees a walkable corridor between the two bases. */
    void ensureConnected() {
        if (connected(startX[0], startY[0], startX[1], startY[1]) != 0) {
            return;
        }
        // carve an L-shaped corridor
        int x = startX[0];
        int y = startY[0];
        while (x != startX[1]) {
            clearRect(x, y - 2, 1, 5);
            if (x < startX[1]) {
                x = x + 1;
            } else {
                x = x - 1;
            }
        }
        while (y != startY[1]) {
            clearRect(x - 2, y, 5, 1);
            if (y < startY[1]) {
                y = y + 1;
            } else {
                y = y - 1;
            }
        }
    }

    int connected(int ax, int ay, int bx, int by) {
        int[] queue = new int[w * h];
        byte[] seen = new byte[w * h];
        int head = 0;
        int tail = 0;
        queue[tail] = ay * w + ax;
        tail = tail + 1;
        seen[ay * w + ax] = 1;
        while (head < tail) {
            int cur = queue[head];
            head = head + 1;
            int cx = cur % w;
            int cy = cur / w;
            if (cx == bx && cy == by) {
                return 1;
            }
            int d = 0;
            while (d < 4) {
                int nx = cx;
                int ny = cy;
                if (d == 0) {
                    nx = nx + 1;
                } else if (d == 1) {
                    nx = nx - 1;
                } else if (d == 2) {
                    ny = ny + 1;
                } else {
                    ny = ny - 1;
                }
                if (walkable(nx, ny) != 0 && seen[ny * w + nx] == 0) {
                    seen[ny * w + nx] = 1;
                    queue[tail] = ny * w + nx;
                    tail = tail + 1;
                }
                d = d + 1;
            }
        }
        return 0;
    }
}
