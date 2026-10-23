/**
 * Axis-aligned moving body with tile collision. Positions are the top-left corner in
 * pixels (level space); speeds are pixels per 1/60 s frame.
 *
 * @author 张晓达
 */
public class SmBody {
    double x;
    double y;
    double vx;
    double vy;
    int w;
    int h;
    int onGround;
    /** -1 / 1 when the last horizontal move was stopped by a wall, else 0. */
    int hitWall;
    /** Tile hit by the head while moving up (valid when bumpedHead != 0). */
    int bumpedHead;
    int bumpTx;
    int bumpTy;

    static int tileOf(double p) {
        return (int) Math.floor(p / 16.0);
    }

    double centerX() {
        return x + w / 2.0;
    }

    double bottom() {
        return y + h;
    }

    void step(SmLevel lv, int collide) {
        hitWall = 0;
        bumpedHead = 0;
        x = x + vx;
        if (collide != 0) {
            int ty0 = tileOf(y + 0.01);
            int ty1 = tileOf(y + h - 0.01);
            if (vx > 0) {
                int tx = tileOf(x + w - 0.01);
                int ty = ty0;
                while (ty <= ty1) {
                    if (lv.solidAt(tx, ty) != 0) {
                        x = tx * 16 - w;
                        vx = 0;
                        hitWall = 1;
                    }
                    ty = ty + 1;
                }
            } else if (vx < 0) {
                int tx = tileOf(x);
                int ty = ty0;
                while (ty <= ty1) {
                    if (lv.solidAt(tx, ty) != 0) {
                        x = (tx + 1) * 16;
                        vx = 0;
                        hitWall = -1;
                    }
                    ty = ty + 1;
                }
            }
        }
        y = y + vy;
        onGround = 0;
        if (collide != 0) {
            int tx0 = tileOf(x + 0.01);
            int tx1 = tileOf(x + w - 0.01);
            if (vy > 0) {
                int ty = tileOf(y + h - 0.01);
                int tx = tx0;
                while (tx <= tx1) {
                    if (lv.solidAt(tx, ty) != 0) {
                        y = ty * 16 - h;
                        vy = 0;
                        onGround = 1;
                    }
                    tx = tx + 1;
                }
            } else if (vy < 0) {
                int ty = tileOf(y);
                int best = -1;
                double bestDist = 1000.0;
                int tx = tx0;
                while (tx <= tx1) {
                    if (lv.solidAt(tx, ty) != 0) {
                        double d = Math.abs((tx * 16 + 8) - (x + w / 2.0));
                        if (d < bestDist) {
                            bestDist = d;
                            best = tx;
                        }
                    }
                    tx = tx + 1;
                }
                if (best >= 0) {
                    y = (ty + 1) * 16;
                    vy = 0;
                    bumpedHead = 1;
                    bumpTx = best;
                    bumpTy = ty;
                }
            }
        }
    }

    int overlaps(SmBody o) {
        if (x + w <= o.x || o.x + o.w <= x) {
            return 0;
        }
        if (y + h <= o.y || o.y + o.h <= y) {
            return 0;
        }
        return 1;
    }
}
