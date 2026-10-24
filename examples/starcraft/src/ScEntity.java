/**
 * One thing on the map: a unit, a building or a resource patch.
 *
 * @author 张晓达
 */
public class ScEntity {
    // orders
    static int O_IDLE = 0;
    static int O_MOVE = 1;
    static int O_ATTACKMOVE = 2;
    static int O_ATTACK = 3;
    static int O_GATHER = 4;
    static int O_RETURN = 5;
    static int O_BUILD = 6;
    static int O_HOLD = 7;
    static int O_CONSTRUCT = 8;

    int id;
    int type;
    int owner;
    double x;
    double y;
    double facing;
    int hp;
    int maxHp;
    int alive = 1;

    // footprint (buildings and resources): top-left tile and size in tiles
    int tx;
    int ty;
    int tw;
    int th;

    // orders
    int order;
    double ox;
    double oy;
    int target = -1;
    int autoAcquired;
    int[] path;
    int pathLen;
    int pathPos;
    int pathGoalX;
    int pathGoalY;
    int repath;
    int cooldown;
    int sub;
    int timer;

    // worker state
    int carryType;
    int carryAmt;
    int lastRes = -1;
    int buildType = -1;
    int buildX;
    int buildY;
    int hidden;

    // building state
    int done = 1;
    int progress;
    int builder = -1;
    int[] queue = new int[5];
    int qLen;
    int qProg;
    double rallyX = -1;
    double rallyY = -1;
    int rallyTarget = -1;

    // resource state
    int amount;
    int miner = -1;

    ScEntity(int i, int t, int o) {
        id = i;
        type = t;
        owner = o;
        hp = ScDefs.hp[t];
        maxHp = ScDefs.hp[t];
    }

    int isBuilding() {
        if (ScDefs.building[type] == 1) {
            return 1;
        }
        return 0;
    }

    int isResource() {
        if (ScDefs.building[type] == 2) {
            return 1;
        }
        return 0;
    }

    int isUnit() {
        if (ScDefs.building[type] == 0) {
            return 1;
        }
        return 0;
    }

    /** Distance from a point to the edge of this entity's footprint (0 when inside). */
    double edgeDistance(double px, double py) {
        if (ScDefs.building[type] == 0) {
            double dx = px - x;
            double dy = py - y;
            return Math.max(0, Math.sqrt(dx * dx + dy * dy) - ScDefs.radius[type]);
        }
        double x0 = tx * 32;
        double y0 = ty * 32;
        double x1 = x0 + tw * 32;
        double y1 = y0 + th * 32;
        double dx = 0;
        if (px < x0) {
            dx = x0 - px;
        } else if (px > x1) {
            dx = px - x1;
        }
        double dy = 0;
        if (py < y0) {
            dy = y0 - py;
        } else if (py > y1) {
            dy = py - y1;
        }
        return Math.sqrt(dx * dx + dy * dy);
    }
}
