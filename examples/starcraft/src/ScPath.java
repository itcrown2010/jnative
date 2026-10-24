/**
 * A* over the tile grid (8 directions, no corner cutting). The goal can be an exact tile, the ring
 * around a footprint, or any tile within a weapon range of a footprint.
 *
 * @author 张晓达
 */
public class ScPath {
    int w;
    int h;
    int[] g;
    int[] parent;
    int[] stamp;
    byte[] closed;
    int cur;
    int[] heap;
    int[] heapF;
    int heapSize;
    int expanded;

    ScPath(int width, int height) {
        w = width;
        h = height;
        int n = w * h;
        g = new int[n];
        parent = new int[n];
        stamp = new int[n];
        closed = new byte[n];
        heap = new int[n * 8];
        heapF = new int[n * 8];
    }

    void push(int node, int f) {
        int i = heapSize;
        heapSize = heapSize + 1;
        while (i > 0) {
            int p = (i - 1) / 2;
            if (heapF[p] <= f) {
                break;
            }
            heap[i] = heap[p];
            heapF[i] = heapF[p];
            i = p;
        }
        heap[i] = node;
        heapF[i] = f;
    }

    int pop() {
        int top = heap[0];
        heapSize = heapSize - 1;
        if (heapSize > 0) {
            int node = heap[heapSize];
            int f = heapF[heapSize];
            int i = 0;
            while (true) {
                int c = i * 2 + 1;
                if (c >= heapSize) {
                    break;
                }
                if (c + 1 < heapSize && heapF[c + 1] < heapF[c]) {
                    c = c + 1;
                }
                if (heapF[c] >= f) {
                    break;
                }
                heap[i] = heap[c];
                heapF[i] = heapF[c];
                i = c;
            }
            heap[i] = node;
            heapF[i] = f;
        }
        return top;
    }

    static int octile(int dx, int dy) {
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int mn = Math.min(ax, ay);
        int mx = Math.max(ax, ay);
        return mn * 14 + (mx - mn) * 10;
    }

    int isGoal(int x, int y, int mode, int gx, int gy, ScEntity t, int rangePx) {
        if (mode == 0) {
            if (x == gx && y == gy) {
                return 1;
            }
            return 0;
        }
        if (mode == 1) {
            if (x >= t.tx - 1 && x <= t.tx + t.tw && y >= t.ty - 1 && y <= t.ty + t.th) {
                return 1;
            }
            return 0;
        }
        double d = t.edgeDistance(x * 32 + 16, y * 32 + 16);
        if (d <= rangePx) {
            return 1;
        }
        return 0;
    }

    /**
     * Returns the tiles to walk (excluding the start tile) as tile indices, or null when already at
     * the goal. When the goal is unreachable the path leads to the closest reachable tile.
     */
    int[] find(ScSim sim, int sx, int sy, int mode, int gx, int gy, ScEntity t, int rangePx) {
        cur = cur + 1;
        heapSize = 0;
        expanded = 0;
        int start = sy * w + sx;
        if (isGoal(sx, sy, mode, gx, gy, t, rangePx) != 0) {
            return null;
        }
        int hx = gx;
        int hy = gy;
        if (mode != 0) {
            hx = t.tx + t.tw / 2;
            hy = t.ty + t.th / 2;
        }
        stamp[start] = cur;
        g[start] = 0;
        parent[start] = -1;
        closed[start] = 0;
        int bestNode = start;
        int bestH = octile(sx - hx, sy - hy);
        push(start, bestH);
        int found = -1;
        while (heapSize > 0) {
            int node = pop();
            if (closed[node] == 1 && stamp[node] == cur) {
                continue;
            }
            closed[node] = 1;
            expanded = expanded + 1;
            int cx = node % w;
            int cy = node / w;
            if (isGoal(cx, cy, mode, gx, gy, t, rangePx) != 0) {
                found = node;
                break;
            }
            int hh = octile(cx - hx, cy - hy);
            if (hh < bestH) {
                bestH = hh;
                bestNode = node;
            }
            int d = 0;
            while (d < 8) {
                int dx = 0;
                int dy = 0;
                if (d == 0) {
                    dx = 1;
                } else if (d == 1) {
                    dx = -1;
                } else if (d == 2) {
                    dy = 1;
                } else if (d == 3) {
                    dy = -1;
                } else if (d == 4) {
                    dx = 1;
                    dy = 1;
                } else if (d == 5) {
                    dx = 1;
                    dy = -1;
                } else if (d == 6) {
                    dx = -1;
                    dy = 1;
                } else {
                    dx = -1;
                    dy = -1;
                }
                d = d + 1;
                int nx = cx + dx;
                int ny = cy + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                if (sim.blockedTile(nx, ny) != 0) {
                    continue;
                }
                int cost = 10;
                if (dx != 0 && dy != 0) {
                    if (sim.blockedTile(cx + dx, cy) != 0 || sim.blockedTile(cx, cy + dy) != 0) {
                        continue;
                    }
                    cost = 14;
                }
                int ni = ny * w + nx;
                int ng = g[node] + cost;
                if (stamp[ni] != cur) {
                    stamp[ni] = cur;
                    closed[ni] = 0;
                    g[ni] = ng;
                    parent[ni] = node;
                    push(ni, ng + octile(nx - hx, ny - hy));
                } else if (ng < g[ni] && closed[ni] == 0) {
                    g[ni] = ng;
                    parent[ni] = node;
                    push(ni, ng + octile(nx - hx, ny - hy));
                }
            }
        }
        int endNode = found;
        if (endNode < 0) {
            endNode = bestNode;
        }
        if (endNode == start) {
            return null;
        }
        int len = 0;
        int n = endNode;
        while (n != start && n >= 0) {
            len = len + 1;
            n = parent[n];
        }
        int[] out = new int[len];
        n = endNode;
        int i = len - 1;
        while (n != start && n >= 0) {
            out[i] = n;
            i = i - 1;
            n = parent[n];
        }
        return out;
    }
}
