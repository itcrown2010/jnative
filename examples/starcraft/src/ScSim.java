import jnative.collections.*;

/**
 * The deterministic simulation: entities, economy, orders, combat and fog of war. One call to
 * {@link #step()} advances the world by 1/24 s. The same seed and the same command stream always
 * produce the same match.
 *
 * @author 张晓达
 */
public class ScSim {
    static int TICKS_PER_SEC = 24;

    int w;
    int h;
    long seed;
    ScMap map;
    ScPath path;
    ScRand rand;
    long tick;
    ArrayList<ScEntity> ents = new ArrayList<ScEntity>();
    ScEntity[] byId = new ScEntity[4096];
    int nextId = 1;
    int[] occ;
    ScPlayer[] players;
    int playerCount;
    byte[][] vis;
    byte[][] seen;
    ArrayList<ScEffect> effects = new ArrayList<ScEffect>();
    int winner = -1;
    int gameOver;
    ScEntity probe = new ScEntity(0, 3, 0);
    ArrayList<ScCommand> pending = new ArrayList<ScCommand>();
    ScReplay recorder;

    ScSim(long s, int size, int count) {
        seed = s;
        w = size;
        h = size;
        rand = new ScRand(s + 7);
        map = ScMap.generate(s, size);
        path = new ScPath(w, h);
        occ = new int[w * h];
        playerCount = count;
        players = new ScPlayer[count];
        vis = new byte[count][];
        seen = new byte[count][];
        int p = 0;
        while (p < count) {
            players[p] = new ScPlayer(p, "Player " + (p + 1));
            vis[p] = new byte[w * h];
            seen[p] = new byte[w * h];
            p = p + 1;
        }
        // resources
        int i = 0;
        while (i < map.resCount) {
            ScEntity r = spawnStructure(map.resType[i], -1, map.resX[i], map.resY[i], 1);
            r.amount = map.resAmount[i];
            i = i + 1;
        }
        // starting forces
        p = 0;
        while (p < count && p < map.startCount) {
            int sx = map.startX[p];
            int sy = map.startY[p];
            ScEntity hub = spawnStructure(ScDefs.HUB, p, sx - 2, sy - 1, 1);
            int k = 0;
            while (k < 4) {
                double ux = hub.x - 48 + k * 32;
                double uy = (hub.ty + hub.th) * 32 + 20;
                spawnUnit(ScDefs.WORKER, p, ux, uy);
                k = k + 1;
            }
            p = p + 1;
        }
        updateSupplyCaps();
        updateFog();
    }

    // ------------------------------------------------------------------ entities

    ScEntity get(int id) {
        if (id < 0 || id >= byId.length) {
            return null;
        }
        ScEntity e = byId[id];
        if (e == null || e.alive == 0) {
            return null;
        }
        return e;
    }

    void register(ScEntity e) {
        if (e.id >= byId.length) {
            ScEntity[] bigger = new ScEntity[byId.length * 2];
            int i = 0;
            while (i < byId.length) {
                bigger[i] = byId[i];
                i = i + 1;
            }
            byId = bigger;
        }
        byId[e.id] = e;
        ents.add(e);
    }

    ScEntity spawnUnit(int type, int owner, double x, double y) {
        ScEntity e = new ScEntity(nextId, type, owner);
        nextId = nextId + 1;
        e.x = x;
        e.y = y;
        e.facing = 1.5;
        register(e);
        if (owner >= 0) {
            players[owner].supplyUsed = players[owner].supplyUsed + ScDefs.supply[type];
        }
        return e;
    }

    ScEntity spawnStructure(int type, int owner, int tx, int ty, int done) {
        ScEntity e = new ScEntity(nextId, type, owner);
        nextId = nextId + 1;
        e.tx = tx;
        e.ty = ty;
        e.tw = ScDefs.tw[type];
        e.th = ScDefs.th[type];
        e.x = (tx + e.tw / 2.0) * 32;
        e.y = (ty + e.th / 2.0) * 32;
        e.done = done;
        if (done == 0) {
            e.hp = Math.max(1, ScDefs.hp[type] / 10);
        }
        register(e);
        markOcc(e, e.id);
        return e;
    }

    void markOcc(ScEntity e, int value) {
        int y = e.ty;
        while (y < e.ty + e.th) {
            int x = e.tx;
            while (x < e.tx + e.tw) {
                if (x >= 0 && y >= 0 && x < w && y < h) {
                    occ[y * w + x] = value;
                }
                x = x + 1;
            }
            y = y + 1;
        }
    }

    int blockedTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) {
            return 1;
        }
        if (map.walkable(tx, ty) == 0) {
            return 1;
        }
        if (occ[ty * w + tx] != 0) {
            return 1;
        }
        return 0;
    }

    void say(int player, String msg) {
        if (player >= 0 && player < playerCount) {
            players[player].message = msg;
            players[player].messageTick = tick;
        }
    }

    // ------------------------------------------------------------------ queries

    int countOwned(int owner, int type, int doneOnly) {
        int n = 0;
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            if (e.alive != 0 && e.owner == owner && e.type == type && (doneOnly == 0 || e.done != 0)) {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    int hasBuilding(int owner, int type) {
        return countOwned(owner, type, 1) > 0 ? 1 : 0;
    }

    ScEntity nearestOwned(int owner, int type, double x, double y, int doneOnly) {
        ScEntity best = null;
        double bd = 1e18;
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.owner != owner || e.type != type) {
                continue;
            }
            if (doneOnly != 0 && e.done == 0) {
                continue;
            }
            double d = Math.abs(e.x - x) + Math.abs(e.y - y);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    ScEntity nearestDropoff(int owner, double x, double y) {
        ScEntity best = null;
        double bd = 1e18;
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.owner != owner || e.done == 0 || ScDefs.dropoff[e.type] == 0) {
                continue;
            }
            double d = Math.abs(e.x - x) + Math.abs(e.y - y);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    ScEntity nearestResource(int type, double x, double y, double maxDist, int freeOnly, int ownerOfRefinery) {
        ScEntity best = null;
        double bd = maxDist;
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.type != type || e.amount <= 0) {
                continue;
            }
            if (freeOnly != 0 && e.miner >= 0 && get(e.miner) != null) {
                continue;
            }
            double d = Math.sqrt((e.x - x) * (e.x - x) + (e.y - y) * (e.y - y));
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    int visibleTo(int player, int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) {
            return 0;
        }
        return vis[player][ty * w + tx] > 0 ? 1 : 0;
    }

    // ------------------------------------------------------------------ commands

    void queueCommand(ScCommand c) {
        pending.add(c);
    }

    /** Issue an order for the current tick: it is executed on the next step and recorded for replays. */
    void submit(ScCommand c) {
        c.tick = tick;
        pending.add(c);
        if (recorder != null) {
            recorder.add(c);
        }
    }

    void applyCommand(ScCommand c) {
        if (c.player < 0 || c.player >= playerCount || players[c.player].alive == 0) {
            return;
        }
        int t = c.type;
        if (t == ScCommand.SURRENDER) {
            defeat(c.player);
            return;
        }
        if (t == ScCommand.TRAIN) {
            ScEntity b = ownedEntity(c.player, c.ids.length > 0 ? c.ids[0] : -1);
            if (b != null) {
                trainUnit(b, c.arg);
            }
            return;
        }
        if (t == ScCommand.CANCEL) {
            ScEntity b = ownedEntity(c.player, c.ids.length > 0 ? c.ids[0] : -1);
            if (b != null) {
                cancelQueue(b, c.arg);
            }
            return;
        }
        if (t == ScCommand.RALLY) {
            int i = 0;
            while (i < c.ids.length) {
                ScEntity b = ownedEntity(c.player, c.ids[i]);
                if (b != null && b.isBuilding() != 0) {
                    b.rallyX = c.x;
                    b.rallyY = c.y;
                    b.rallyTarget = c.target;
                }
                i = i + 1;
            }
            return;
        }
        int n = c.ids.length;
        int k = 0;
        int i = 0;
        while (i < n) {
            ScEntity e = ownedEntity(c.player, c.ids[i]);
            i = i + 1;
            if (e == null || e.isUnit() == 0 || e.hidden != 0) {
                continue;
            }
            if (e.order == ScEntity.O_CONSTRUCT && t != ScCommand.STOP) {
                releaseBuilder(e);
            }
            if (t == ScCommand.MOVE || t == ScCommand.ATTACKMOVE) {
                double dx = 0;
                double dy = 0;
                if (n > 1) {
                    double ang = k * 2.39996;
                    double rad = 13.0 * Math.sqrt(k + 0.5);
                    dx = Math.cos(ang) * rad;
                    dy = Math.sin(ang) * rad;
                }
                k = k + 1;
                double gx = Math.max(8, Math.min(w * 32 - 8, c.x + dx));
                double gy = Math.max(8, Math.min(h * 32 - 8, c.y + dy));
                if (t == ScCommand.MOVE) {
                    orderMove(e, gx, gy, ScEntity.O_MOVE);
                } else {
                    orderMove(e, gx, gy, ScEntity.O_ATTACKMOVE);
                }
            } else if (t == ScCommand.ATTACK) {
                ScEntity target = get(c.target);
                if (target != null && target.owner != c.player && ScDefs.damage[e.type] > 0) {
                    e.order = ScEntity.O_ATTACK;
                    e.target = target.id;
                    e.autoAcquired = 0;
                    e.path = null;
                    e.sub = 0;
                }
            } else if (t == ScCommand.STOP) {
                e.order = ScEntity.O_IDLE;
                e.target = -1;
                e.path = null;
            } else if (t == ScCommand.HOLD) {
                e.order = ScEntity.O_HOLD;
                e.target = -1;
                e.path = null;
            } else if (t == ScCommand.GATHER) {
                ScEntity r = get(c.target);
                if (r != null && ScDefs.worker[e.type] != 0 && (r.type == ScDefs.MINERAL
                        || (r.type == ScDefs.REFINERY && r.owner == c.player && r.done != 0))) {
                    startGather(e, r);
                }
            } else if (t == ScCommand.RETURN) {
                if (ScDefs.worker[e.type] != 0 && e.carryAmt > 0) {
                    e.order = ScEntity.O_RETURN;
                    e.path = null;
                }
            } else if (t == ScCommand.BUILD) {
                if (ScDefs.worker[e.type] != 0) {
                    e.order = ScEntity.O_BUILD;
                    e.buildType = c.arg;
                    e.buildX = c.x;
                    e.buildY = c.y;
                    e.path = null;
                    e.sub = 0;
                    break;
                }
            }
        }
    }

    int firstIndex(ScCommand c, int id) {
        int i = 0;
        while (i < c.ids.length) {
            if (c.ids[i] == id) {
                return i;
            }
            i = i + 1;
        }
        return 0;
    }

    ScEntity ownedEntity(int player, int id) {
        ScEntity e = get(id);
        if (e == null || e.owner != player) {
            return null;
        }
        return e;
    }

    void startGather(ScEntity e, ScEntity r) {
        e.target = r.id;
        e.lastRes = r.id;
        e.sub = 0;
        e.path = null;
        if (e.carryAmt > 0) {
            e.order = ScEntity.O_RETURN;
        } else {
            e.order = ScEntity.O_GATHER;
        }
    }

    void releaseBuilder(ScEntity e) {
        e.order = ScEntity.O_IDLE;
        ScEntity b = get(e.target);
        if (b != null && b.builder == e.id) {
            b.builder = -1;
        }
        e.target = -1;
    }

    void orderMove(ScEntity e, double x, double y, int order) {
        e.order = order;
        e.ox = x;
        e.oy = y;
        e.target = -1;
        e.autoAcquired = 0;
        e.sub = 0;
        e.path = null;
        e.pathLen = 0;
        e.pathPos = 0;
        e.repath = 0;
    }

    // ------------------------------------------------------------------ production

    int trainUnit(ScEntity b, int utype) {
        ScPlayer p = players[b.owner];
        if (b.isBuilding() == 0 || b.done == 0) {
            return 0;
        }
        if (utype < 0 || utype >= ScDefs.TYPES || ScDefs.producer[utype] != b.type || ScDefs.building[utype] != 0) {
            return 0;
        }
        if (b.qLen >= 5) {
            say(b.owner, "Production queue is full");
            return 0;
        }
        if (p.minerals < ScDefs.cost[utype]) {
            say(b.owner, "Not enough minerals");
            return 0;
        }
        if (p.supplyUsed + ScDefs.supply[utype] > p.supplyCap) {
            say(b.owner, "Additional supply required");
            return 0;
        }
        p.minerals = p.minerals - ScDefs.cost[utype];
        p.supplyUsed = p.supplyUsed + ScDefs.supply[utype];
        b.queue[b.qLen] = utype;
        b.qLen = b.qLen + 1;
        if (b.qLen == 1) {
            b.qProg = 0;
        }
        return 1;
    }

    void cancelQueue(ScEntity b, int index) {
        if (b.isBuilding() == 0) {
            return;
        }
        if (b.done == 0) {
            cancelConstruction(b);
            return;
        }
        if (index < 0 || index >= b.qLen) {
            return;
        }
        int utype = b.queue[index];
        ScPlayer p = players[b.owner];
        p.minerals = p.minerals + ScDefs.cost[utype];
        p.supplyUsed = p.supplyUsed - ScDefs.supply[utype];
        int i = index;
        while (i < b.qLen - 1) {
            b.queue[i] = b.queue[i + 1];
            i = i + 1;
        }
        b.qLen = b.qLen - 1;
        if (index == 0) {
            b.qProg = 0;
        }
    }

    void cancelConstruction(ScEntity b) {
        ScPlayer p = players[b.owner];
        p.minerals = p.minerals + ScDefs.cost[b.type] * 3 / 4;
        ScEntity wk = get(b.builder);
        if (wk != null) {
            wk.order = ScEntity.O_IDLE;
            wk.target = -1;
        }
        if (b.type == ScDefs.REFINERY) {
            // the geyser comes back
            ScEntity g = spawnStructure(ScDefs.GEYSER, -1, b.tx, b.ty, 1);
            g.amount = b.amount;
        }
        removeEntity(b);
    }

    void finishTraining(ScEntity b) {
        int utype = b.queue[0];
        int i = 0;
        while (i < b.qLen - 1) {
            b.queue[i] = b.queue[i + 1];
            i = i + 1;
        }
        b.qLen = b.qLen - 1;
        b.qProg = 0;
        // spawn on a free tile around the building, nearest to the rally point
        double rx = b.rallyX;
        double ry = b.rallyY;
        if (rx < 0) {
            rx = b.x;
            ry = (b.ty + b.th + 2) * 32;
        }
        int best = -1;
        double bd = 1e18;
        int x = b.tx - 1;
        while (x <= b.tx + b.tw) {
            int y = b.ty - 1;
            while (y <= b.ty + b.th) {
                boolean ring = x == b.tx - 1 || x == b.tx + b.tw || y == b.ty - 1 || y == b.ty + b.th;
                if (ring && blockedTile(x, y) == 0) {
                    double d = Math.abs(x * 32 + 16 - rx) + Math.abs(y * 32 + 16 - ry);
                    if (d < bd) {
                        bd = d;
                        best = y * w + x;
                    }
                }
                y = y + 1;
            }
            x = x + 1;
        }
        if (best < 0) {
            best = (b.ty + b.th) * w + b.tx;
        }
        // supply was reserved when queued, so spawn without counting it twice
        ScEntity u = spawnUnit(utype, b.owner, (best % w) * 32 + 16, (best / w) * 32 + 16);
        players[b.owner].supplyUsed = players[b.owner].supplyUsed - ScDefs.supply[utype];
        players[b.owner].unitsBuilt = players[b.owner].unitsBuilt + 1;
        if (b.rallyTarget >= 0 && ScDefs.worker[utype] != 0) {
            ScEntity r = get(b.rallyTarget);
            if (r != null) {
                startGather(u, r);
                return;
            }
        }
        if (b.rallyX >= 0) {
            orderMove(u, b.rallyX, b.rallyY, ScEntity.O_MOVE);
        }
    }

    // ------------------------------------------------------------------ building placement

    int canPlace(int player, int type, int tx, int ty) {
        int tw = ScDefs.tw[type];
        int th = ScDefs.th[type];
        if (tx < 1 || ty < 1 || tx + tw > w - 1 || ty + th > h - 1) {
            return 0;
        }
        int req = ScDefs.requires[type];
        if (req >= 0 && hasBuilding(player, req) == 0) {
            return 0;
        }
        if (type == ScDefs.REFINERY) {
            int id = occ[ty * w + tx];
            ScEntity g = get(id);
            if (g == null || g.type != ScDefs.GEYSER || g.tx != tx || g.ty != ty) {
                return 0;
            }
            return 1;
        }
        int y = ty;
        while (y < ty + th) {
            int x = tx;
            while (x < tx + tw) {
                if (blockedTile(x, y) != 0) {
                    return 0;
                }
                if (map.get(x, y) > 1) {
                    return 0;
                }
                x = x + 1;
            }
            y = y + 1;
        }
        if (type == ScDefs.HUB) {
            int i = 0;
            while (i < ents.size()) {
                ScEntity r = ents.get(i);
                i = i + 1;
                if (r.alive == 0 || r.isResource() == 0) {
                    continue;
                }
                if (r.tx < tx + tw + 3 && r.tx + r.tw > tx - 3 && r.ty < ty + th + 3 && r.ty + r.th > ty - 3) {
                    return 0;
                }
            }
        }
        return 1;
    }

    /** Called by the worker once it stands next to the site. Returns the new structure or null. */
    ScEntity placeBuilding(ScEntity worker) {
        int type = worker.buildType;
        ScPlayer p = players[worker.owner];
        if (canPlace(worker.owner, type, worker.buildX, worker.buildY) == 0) {
            say(worker.owner, "Cannot build there");
            return null;
        }
        if (p.minerals < ScDefs.cost[type]) {
            say(worker.owner, "Not enough minerals");
            return null;
        }
        if (type == ScDefs.REFINERY) {
            ScEntity g = get(occ[worker.buildY * w + worker.buildX]);
            int amount = g.amount;
            removeEntity(g);
            p.minerals = p.minerals - ScDefs.cost[type];
            ScEntity r = spawnStructure(type, worker.owner, worker.buildX, worker.buildY, 0);
            r.amount = amount;
            r.builder = worker.id;
            return r;
        }
        p.minerals = p.minerals - ScDefs.cost[type];
        ScEntity b = spawnStructure(type, worker.owner, worker.buildX, worker.buildY, 0);
        b.builder = worker.id;
        evictUnits(b);
        return b;
    }

    void evictUnits(ScEntity b) {
        int i = 0;
        while (i < ents.size()) {
            ScEntity u = ents.get(i);
            i = i + 1;
            if (u.alive == 0 || u.isUnit() == 0) {
                continue;
            }
            int ux = (int) Math.floor(u.x / 32);
            int uy = (int) Math.floor(u.y / 32);
            if (ux >= b.tx && ux < b.tx + b.tw && uy >= b.ty && uy < b.ty + b.th) {
                int free = findFreeNear(ux, uy, 8);
                if (free >= 0) {
                    u.x = (free % w) * 32 + 16;
                    u.y = (free / w) * 32 + 16;
                    u.path = null;
                }
            }
        }
    }

    int findFreeNear(int tx, int ty, int maxR) {
        if (blockedTile(tx, ty) == 0) {
            return ty * w + tx;
        }
        int r = 1;
        while (r <= maxR) {
            int best = -1;
            int bd = 1000000;
            int y = ty - r;
            while (y <= ty + r) {
                int x = tx - r;
                while (x <= tx + r) {
                    if ((Math.abs(x - tx) == r || Math.abs(y - ty) == r) && blockedTile(x, y) == 0) {
                        int d = (x - tx) * (x - tx) + (y - ty) * (y - ty);
                        if (d < bd) {
                            bd = d;
                            best = y * w + x;
                        }
                    }
                    x = x + 1;
                }
                y = y + 1;
            }
            if (best >= 0) {
                return best;
            }
            r = r + 1;
        }
        return -1;
    }

    // ------------------------------------------------------------------ main loop

    void step() {
        // commands scheduled for this tick
        int ci = 0;
        while (ci < pending.size()) {
            ScCommand c = pending.get(ci);
            if (c.tick <= tick) {
                applyCommand(c);
                pending.remove(ci);
            } else {
                ci = ci + 1;
            }
        }
        int i = 0;
        int n = ents.size();
        while (i < n) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive == 0) {
                continue;
            }
            if (e.isUnit() != 0) {
                updateUnit(e);
            } else if (e.isBuilding() != 0) {
                updateBuilding(e);
            }
        }
        separate();
        sweepDead();
        updateSupplyCaps();
        if (tick % 4 == 0) {
            updateFog();
        }
        checkVictory();
        // effects
        int ei = 0;
        while (ei < effects.size()) {
            ScEffect fx = effects.get(ei);
            fx.ttl = fx.ttl - 1;
            if (fx.ttl <= 0) {
                effects.remove(ei);
            } else {
                ei = ei + 1;
            }
        }
        tick = tick + 1;
    }

    void sweepDead() {
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            if (e.alive == 0) {
                ents.remove(i);
            } else {
                i = i + 1;
            }
        }
    }

    void updateSupplyCaps() {
        int p = 0;
        while (p < playerCount) {
            players[p].supplyCap = 0;
            p = p + 1;
        }
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive != 0 && e.owner >= 0 && e.done != 0 && ScDefs.provides[e.type] > 0) {
                players[e.owner].supplyCap = players[e.owner].supplyCap + ScDefs.provides[e.type];
            }
        }
        p = 0;
        while (p < playerCount) {
            if (players[p].supplyCap > 200) {
                players[p].supplyCap = 200;
            }
            p = p + 1;
        }
    }

    void checkVictory() {
        int p = 0;
        while (p < playerCount) {
            if (players[p].alive != 0) {
                int has = 0;
                int i = 0;
                while (i < ents.size()) {
                    ScEntity e = ents.get(i);
                    i = i + 1;
                    if (e.alive != 0 && e.owner == p && e.isBuilding() != 0) {
                        has = 1;
                        break;
                    }
                }
                if (has == 0) {
                    defeat(p);
                }
            }
            p = p + 1;
        }
        if (gameOver == 0) {
            int alive = 0;
            int last = -1;
            p = 0;
            while (p < playerCount) {
                if (players[p].alive != 0) {
                    alive = alive + 1;
                    last = p;
                }
                p = p + 1;
            }
            if (alive <= 1) {
                gameOver = 1;
                winner = last;
            }
        }
    }

    void defeat(int p) {
        players[p].alive = 0;
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive != 0 && e.owner == p && e.isUnit() != 0) {
                e.alive = 0;
            }
        }
    }

    // ------------------------------------------------------------------ fog of war

    void updateFog() {
        int p = 0;
        while (p < playerCount) {
            byte[] v = vis[p];
            int i = 0;
            while (i < v.length) {
                v[i] = 0;
                i = i + 1;
            }
            p = p + 1;
        }
        int k = 0;
        while (k < ents.size()) {
            ScEntity e = ents.get(k);
            k = k + 1;
            if (e.alive == 0 || e.owner < 0) {
                continue;
            }
            int r = ScDefs.sight[e.type];
            int cx = (int) Math.floor(e.x / 32);
            int cy = (int) Math.floor(e.y / 32);
            byte[] v = vis[e.owner];
            byte[] sn = seen[e.owner];
            int y = cy - r;
            while (y <= cy + r) {
                if (y >= 0 && y < h) {
                    int x = cx - r;
                    while (x <= cx + r) {
                        if (x >= 0 && x < w) {
                            int dx = x - cx;
                            int dy = y - cy;
                            if (dx * dx + dy * dy <= r * r) {
                                v[y * w + x] = 1;
                                sn[y * w + x] = 1;
                            }
                        }
                        x = x + 1;
                    }
                }
                y = y + 1;
            }
        }
    }

    // ------------------------------------------------------------------ units

    void faceTo(ScEntity e, double px, double py) {
        e.facing = Math.atan2(py - e.y, px - e.x);
    }

    /** Re-plans the route of a unit according to its order. */
    void replan(ScEntity e) {
        int sx = (int) Math.floor(e.x / 32);
        int sy = (int) Math.floor(e.y / 32);
        sx = Math.max(0, Math.min(w - 1, sx));
        sy = Math.max(0, Math.min(h - 1, sy));
        int[] route = null;
        int o = e.order;
        if (o == ScEntity.O_MOVE || o == ScEntity.O_ATTACKMOVE) {
            int gx = (int) Math.floor(e.ox / 32);
            int gy = (int) Math.floor(e.oy / 32);
            int free = findFreeNear(gx, gy, 10);
            if (free < 0) {
                e.path = null;
                return;
            }
            gx = free % w;
            gy = free / w;
            e.pathGoalX = gx;
            e.pathGoalY = gy;
            route = path.find(this, sx, sy, 0, gx, gy, null, 0);
        } else if (o == ScEntity.O_ATTACK) {
            ScEntity t = get(e.target);
            if (t == null) {
                e.path = null;
                return;
            }
            int rng = Math.max(0, ScDefs.range[e.type] - 6);
            route = path.find(this, sx, sy, 2, 0, 0, t, rng);
        } else if (o == ScEntity.O_GATHER) {
            ScEntity t = get(e.target);
            if (t == null) {
                e.path = null;
                return;
            }
            route = path.find(this, sx, sy, 1, 0, 0, t, 0);
        } else if (o == ScEntity.O_RETURN) {
            ScEntity hub = nearestDropoff(e.owner, e.x, e.y);
            if (hub == null) {
                e.path = null;
                return;
            }
            e.target = hub.id;
            route = path.find(this, sx, sy, 1, 0, 0, hub, 0);
        } else if (o == ScEntity.O_BUILD) {
            probe.type = e.buildType;
            probe.tx = e.buildX;
            probe.ty = e.buildY;
            probe.tw = ScDefs.tw[e.buildType];
            probe.th = ScDefs.th[e.buildType];
            probe.x = (probe.tx + probe.tw / 2.0) * 32;
            probe.y = (probe.ty + probe.th / 2.0) * 32;
            route = path.find(this, sx, sy, 1, 0, 0, probe, 0);
        }
        e.path = route;
        e.pathPos = 0;
        if (route == null) {
            e.pathLen = 0;
        } else {
            e.pathLen = route.length;
        }
        e.repath = 0;
    }

    /** Walks along the current route. Returns 1 when the route is finished. */
    int follow(ScEntity e) {
        if (e.path == null || e.pathPos >= e.pathLen) {
            return 1;
        }
        int node = e.path[e.pathPos];
        double wx = (node % w) * 32 + 16;
        double wy = (node / w) * 32 + 16;
        if ((e.order == ScEntity.O_MOVE || e.order == ScEntity.O_ATTACKMOVE) && e.pathPos == e.pathLen - 1
                && (node % w) == (int) Math.floor(e.ox / 32) && (node / w) == (int) Math.floor(e.oy / 32)) {
            wx = e.ox;
            wy = e.oy;
        }
        double dx = wx - e.x;
        double dy = wy - e.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double sp = ScDefs.speed[e.type];
        e.facing = Math.atan2(dy, dx);
        if (dist <= sp) {
            e.x = wx;
            e.y = wy;
            e.pathPos = e.pathPos + 1;
        } else {
            double nx = e.x + dx / dist * sp;
            double ny = e.y + dy / dist * sp;
            int ntx = (int) Math.floor(nx / 32);
            int nty = (int) Math.floor(ny / 32);
            if (blockedTile(ntx, nty) != 0) {
                int ctx = (int) Math.floor(e.x / 32);
                int cty = (int) Math.floor(e.y / 32);
                if (ntx != ctx || nty != cty) {
                    e.path = null;
                    return 0;
                }
            }
            e.x = nx;
            e.y = ny;
        }
        if (e.pathPos >= e.pathLen) {
            return 1;
        }
        return 0;
    }

    /** Moves toward the current goal, replanning when needed. Returns 1 when the route ended. */
    int travel(ScEntity e) {
        if (e.path == null) {
            if (e.repath > 0) {
                e.repath = e.repath - 1;
                return 0;
            }
            replan(e);
            if (e.path == null) {
                e.repath = 12;
                return 1;
            }
        }
        int r = follow(e);
        if (e.path == null) {
            e.repath = 3;
            return 0;
        }
        return r;
    }

    void updateUnit(ScEntity e) {
        if (e.cooldown > 0) {
            e.cooldown = e.cooldown - 1;
        }
        int o = e.order;
        if (e.hidden != 0) {
            updateGather(e);
            return;
        }
        if (o == ScEntity.O_IDLE || o == ScEntity.O_HOLD) {
            if (ScDefs.damage[e.type] > 0 && ScDefs.worker[e.type] == 0 && (tick + e.id) % 4 == 0) {
                int radius = ScDefs.range[e.type];
                if (o == ScEntity.O_IDLE) {
                    radius = ScDefs.sight[e.type] * 32 - 32;
                }
                ScEntity t = findEnemy(e, radius);
                if (t != null) {
                    if (o == ScEntity.O_HOLD) {
                        e.target = t.id;
                        faceTo(e, t.x, t.y);
                        if (e.cooldown <= 0) {
                            shoot(e, t);
                        }
                    } else {
                        e.order = ScEntity.O_ATTACK;
                        e.target = t.id;
                        e.autoAcquired = 1;
                        e.path = null;
                    }
                }
            } else if (o == ScEntity.O_HOLD && e.target >= 0) {
                ScEntity t = get(e.target);
                if (t != null && t.edgeDistance(e.x, e.y) <= ScDefs.range[e.type]) {
                    faceTo(e, t.x, t.y);
                    if (e.cooldown <= 0) {
                        shoot(e, t);
                    }
                } else {
                    e.target = -1;
                }
            }
            return;
        }
        if (o == ScEntity.O_MOVE) {
            if (travel(e) != 0) {
                e.order = ScEntity.O_IDLE;
                e.path = null;
            }
            return;
        }
        if (o == ScEntity.O_ATTACKMOVE) {
            if ((tick + e.id) % 4 == 0 && ScDefs.damage[e.type] > 0) {
                ScEntity t = findEnemy(e, ScDefs.sight[e.type] * 32 - 16);
                if (t != null) {
                    // fight, then resume the march
                    e.sub = 1;
                    e.target = t.id;
                }
            }
            if (e.sub == 1) {
                ScEntity t = get(e.target);
                if (t == null) {
                    e.sub = 0;
                    e.target = -1;
                    e.path = null;
                } else {
                    attackStep(e, t);
                    return;
                }
            }
            if (travel(e) != 0) {
                e.order = ScEntity.O_IDLE;
                e.path = null;
            }
            return;
        }
        if (o == ScEntity.O_ATTACK) {
            ScEntity t = get(e.target);
            if (t == null) {
                e.target = -1;
                e.order = ScEntity.O_IDLE;
                e.path = null;
                return;
            }
            attackStep(e, t);
            return;
        }
        if (o == ScEntity.O_GATHER || o == ScEntity.O_RETURN) {
            updateGather(e);
            return;
        }
        if (o == ScEntity.O_BUILD) {
            updateBuildOrder(e);
            return;
        }
        if (o == ScEntity.O_CONSTRUCT) {
            ScEntity b = get(e.target);
            if (b == null || b.done != 0) {
                e.order = ScEntity.O_IDLE;
                e.target = -1;
            }
            return;
        }
    }

    void attackStep(ScEntity e, ScEntity t) {
        double d = t.edgeDistance(e.x, e.y);
        if (d <= ScDefs.range[e.type] + 1) {
            faceTo(e, t.x, t.y);
            e.path = null;
            if (e.cooldown <= 0) {
                shoot(e, t);
            }
        } else {
            if (e.path != null && e.pathPos >= e.pathLen) {
                e.path = null;
            }
            if (e.path != null && (tick + e.id) % 16 == 0) {
                e.path = null;
            }
            travel(e);
        }
    }

    ScEntity findEnemy(ScEntity e, int radius) {
        ScEntity best = null;
        double bd = 1e18;
        int bestPrio = 9;
        int i = 0;
        while (i < ents.size()) {
            ScEntity t = ents.get(i);
            i = i + 1;
            if (t.alive == 0 || t.owner < 0 || t.owner == e.owner || t.hidden != 0) {
                continue;
            }
            double d = t.edgeDistance(e.x, e.y);
            if (d > radius) {
                continue;
            }
            if (visibleTo(e.owner, (int) Math.floor(t.x / 32), (int) Math.floor(t.y / 32)) == 0) {
                continue;
            }
            int prio = 2;
            if (ScDefs.damage[t.type] > 0 && t.isUnit() != 0) {
                prio = 0;
            } else if (t.isUnit() != 0) {
                prio = 1;
            }
            if (prio < bestPrio || (prio == bestPrio && d < bd)) {
                bestPrio = prio;
                bd = d;
                best = t;
            }
        }
        return best;
    }

    void shoot(ScEntity a, ScEntity t) {
        int dmg = Math.max(1, ScDefs.damage[a.type] - ScDefs.armor[t.type]);
        a.cooldown = ScDefs.cooldown[a.type];
        t.hp = t.hp - dmg;
        int kind = 0;
        if (ScDefs.range[a.type] <= 32) {
            kind = 2;
        }
        effects.add(new ScEffect(kind, a.x, a.y, t.x, t.y, 4, a.owner));
        shotCount = shotCount + 1;
        shotX = a.x;
        shotY = a.y;
        if (t.isUnit() != 0 && t.alive != 0 && ScDefs.damage[t.type] > 0 && ScDefs.worker[t.type] == 0
                && (t.order == ScEntity.O_IDLE)) {
            t.order = ScEntity.O_ATTACK;
            t.target = a.id;
            t.autoAcquired = 1;
            t.path = null;
        }
        if (t.owner >= 0 && t.hp > 0 && tick - players[t.owner].messageTick > 120 && t.owner != a.owner) {
            // "under attack" is raised by the view; remember the time for it
            lastAttackTick = tick;
            lastAttackOwner = t.owner;
            lastAttackX = t.x;
            lastAttackY = t.y;
        }
        if (t.hp <= 0) {
            if (a.owner >= 0) {
                players[a.owner].kills = players[a.owner].kills + 1;
            }
            kill(t);
        }
    }

    int shotCount;
    double shotX;
    double shotY;
    int boomCount;
    double boomX;
    double boomY;
    long lastAttackTick = -1000;
    int lastAttackOwner = -1;
    double lastAttackX;
    double lastAttackY;

    void kill(ScEntity t) {
        if (t.alive == 0) {
            return;
        }
        t.alive = 0;
        int size = 10;
        if (t.isBuilding() != 0) {
            size = 40;
        }
        effects.add(new ScEffect(1, t.x, t.y, 0, 0, 14, t.owner));
        boomCount = boomCount + 1;
        boomX = t.x;
        boomY = t.y;
        if (t.owner >= 0) {
            players[t.owner].unitsLost = players[t.owner].unitsLost + 1;
        }
        if (t.isUnit() != 0) {
            if (t.owner >= 0) {
                players[t.owner].supplyUsed = players[t.owner].supplyUsed - ScDefs.supply[t.type];
            }
            if (t.order == ScEntity.O_CONSTRUCT) {
                ScEntity b = get(t.target);
                if (b != null && b.builder == t.id) {
                    b.builder = -1;
                }
            }
            if (t.sub == 1 && t.order == ScEntity.O_GATHER) {
                ScEntity r = get(t.target);
                if (r != null && r.miner == t.id) {
                    r.miner = -1;
                }
            }
        } else if (t.isBuilding() != 0) {
            markOcc(t, 0);
            // queued units give their supply back
            int q = 0;
            while (q < t.qLen) {
                players[t.owner].supplyUsed = players[t.owner].supplyUsed - ScDefs.supply[t.queue[q]];
                q = q + 1;
            }
            ScEntity wk = get(t.builder);
            if (wk != null) {
                wk.order = ScEntity.O_IDLE;
                wk.target = -1;
            }
        } else {
            markOcc(t, 0);
        }
    }

    void removeEntity(ScEntity t) {
        if (t.alive == 0) {
            return;
        }
        t.alive = 0;
        if (t.isUnit() == 0) {
            markOcc(t, 0);
        }
    }

    // ------------------------------------------------------------------ economy

    void updateGather(ScEntity e) {
        if (e.hidden != 0) {
            // inside a refinery
            e.timer = e.timer - 1;
            if (e.timer <= 0) {
                ScEntity r = get(e.target);
                e.hidden = 0;
                if (r != null) {
                    int amt = Math.min(4, r.amount);
                    r.amount = r.amount - amt;
                    r.miner = -1;
                    e.carryType = 2;
                    e.carryAmt = amt;
                    e.lastRes = r.id;
                }
                e.order = ScEntity.O_RETURN;
                e.path = null;
                e.sub = 0;
            }
            return;
        }
        if (e.order == ScEntity.O_RETURN) {
            ScEntity hub = nearestDropoff(e.owner, e.x, e.y);
            if (hub == null) {
                e.order = ScEntity.O_IDLE;
                return;
            }
            if (hub.edgeDistance(e.x, e.y) <= 24) {
                if (e.carryType == 1) {
                    players[e.owner].minerals = players[e.owner].minerals + e.carryAmt;
                    players[e.owner].mined = players[e.owner].mined + e.carryAmt;
                } else if (e.carryType == 2) {
                    players[e.owner].gas = players[e.owner].gas + e.carryAmt;
                    players[e.owner].gasMined = players[e.owner].gasMined + e.carryAmt;
                }
                e.carryAmt = 0;
                e.carryType = 0;
                ScEntity r = get(e.lastRes);
                if (r == null || r.amount <= 0) {
                    r = nearestResource(ScDefs.MINERAL, e.x, e.y, 480, 1, e.owner);
                }
                if (r != null) {
                    startGather(e, r);
                } else {
                    e.order = ScEntity.O_IDLE;
                }
                return;
            }
            if (travel(e) != 0 && hub.edgeDistance(e.x, e.y) > 24) {
                e.path = null;
            }
            return;
        }
        // O_GATHER
        ScEntity r = get(e.target);
        if (r == null || r.amount <= 0) {
            // the patch is gone: look for another of the same kind nearby
            int type = ScDefs.MINERAL;
            ScEntity next = nearestResource(type, e.x, e.y, 480, 0, e.owner);
            if (next != null) {
                startGather(e, next);
            } else {
                e.order = ScEntity.O_IDLE;
            }
            return;
        }
        if (e.sub == 1) {
            // mining
            e.timer = e.timer - 1;
            if (e.timer <= 0) {
                int amt = Math.min(5, r.amount);
                r.amount = r.amount - amt;
                r.miner = -1;
                e.carryType = 1;
                e.carryAmt = amt;
                e.lastRes = r.id;
                e.order = ScEntity.O_RETURN;
                e.path = null;
                e.sub = 0;
                if (r.amount <= 0) {
                    removeEntity(r);
                }
            }
            return;
        }
        if (r.edgeDistance(e.x, e.y) <= 24) {
            boolean taken = r.miner >= 0 && r.miner != e.id && get(r.miner) != null;
            if (!taken) {
                r.miner = e.id;
                e.sub = 1;
                e.path = null;
                if (r.type == ScDefs.REFINERY) {
                    e.hidden = 1;
                    e.timer = 30;
                } else {
                    e.timer = 45;
                }
                faceTo(e, r.x, r.y);
            } else {
                // somebody else is mining here: try a free patch close by
                e.timer = e.timer + 1;
                if (e.timer > 8 && r.type == ScDefs.MINERAL) {
                    ScEntity alt = nearestResource(ScDefs.MINERAL, e.x, e.y, 200, 1, e.owner);
                    if (alt != null) {
                        startGather(e, alt);
                        e.timer = 0;
                    }
                }
            }
            return;
        }
        e.timer = 0;
        if (travel(e) != 0 && r.edgeDistance(e.x, e.y) > 24) {
            e.path = null;
            e.repath = 10;
        }
    }

    void updateBuildOrder(ScEntity e) {
        probe.type = e.buildType;
        probe.tx = e.buildX;
        probe.ty = e.buildY;
        probe.tw = ScDefs.tw[e.buildType];
        probe.th = ScDefs.th[e.buildType];
        if (probe.edgeDistance(e.x, e.y) <= 24) {
            ScEntity b = placeBuilding(e);
            if (b == null) {
                e.order = ScEntity.O_IDLE;
                return;
            }
            e.order = ScEntity.O_CONSTRUCT;
            e.target = b.id;
            e.path = null;
            return;
        }
        if (travel(e) != 0 && probe.edgeDistance(e.x, e.y) > 24) {
            e.order = ScEntity.O_IDLE;
            say(e.owner, "Cannot reach the build site");
        }
    }

    // ------------------------------------------------------------------ buildings

    void updateBuilding(ScEntity b) {
        if (b.done == 0) {
            ScEntity wk = get(b.builder);
            if (wk != null && wk.order == ScEntity.O_CONSTRUCT && wk.target == b.id) {
                b.progress = b.progress + 1;
                int total = ScDefs.buildTime[b.type];
                b.hp = Math.min(b.maxHp, Math.max(1, b.maxHp * b.progress / total));
                if (b.progress >= total) {
                    b.done = 1;
                    b.hp = b.maxHp;
                    players[b.owner].buildingsBuilt = players[b.owner].buildingsBuilt + 1;
                    wk.order = ScEntity.O_IDLE;
                    wk.target = -1;
                    b.builder = -1;
                    if (b.type == ScDefs.REFINERY) {
                        startGather(wk, b);
                    }
                }
            }
            return;
        }
        if (b.qLen > 0) {
            b.qProg = b.qProg + 1;
            if (b.qProg >= ScDefs.buildTime[b.queue[0]]) {
                finishTraining(b);
            }
        }
        if (b.cooldown > 0) {
            b.cooldown = b.cooldown - 1;
        }
        if (ScDefs.damage[b.type] > 0 && ScDefs.range[b.type] > 0 && (tick + b.id) % 4 == 0 && b.cooldown <= 0) {
            ScEntity t = findEnemy(b, ScDefs.range[b.type]);
            if (t != null) {
                shoot(b, t);
            }
        }
    }

    // ------------------------------------------------------------------ collisions

    void separate() {
        int n = ents.size();
        int i = 0;
        while (i < n) {
            ScEntity a = ents.get(i);
            i = i + 1;
            if (a.alive == 0 || a.isUnit() == 0 || a.hidden != 0 || a.sub == 1 && a.order == ScEntity.O_GATHER) {
                continue;
            }
            int j = i;
            while (j < n) {
                ScEntity b = ents.get(j);
                j = j + 1;
                if (b.alive == 0 || b.isUnit() == 0 || b.hidden != 0 || b.sub == 1 && b.order == ScEntity.O_GATHER) {
                    continue;
                }
                double dx = b.x - a.x;
                if (dx > 16 || dx < -16) {
                    continue;
                }
                double dy = b.y - a.y;
                if (dy > 16 || dy < -16) {
                    continue;
                }
                double rr = ScDefs.radius[a.type] + ScDefs.radius[b.type];
                double d2 = dx * dx + dy * dy;
                if (d2 >= rr * rr) {
                    continue;
                }
                double d = Math.sqrt(d2);
                double nx;
                double ny;
                if (d < 0.01) {
                    nx = ((a.id + b.id) % 2 == 0) ? 1 : -1;
                    ny = ((a.id * 3 + b.id) % 2 == 0) ? 1 : -1;
                    d = 0.01;
                } else {
                    nx = dx / d;
                    ny = dy / d;
                }
                double push = Math.min(1.5, (rr - d) / 2 + 0.05);
                int aMoving = (a.order == ScEntity.O_IDLE || a.order == ScEntity.O_HOLD || a.order == ScEntity.O_CONSTRUCT) ? 0 : 1;
                int bMoving = (b.order == ScEntity.O_IDLE || b.order == ScEntity.O_HOLD || b.order == ScEntity.O_CONSTRUCT) ? 0 : 1;
                double wa = 1.0;
                double wb = 1.0;
                if (aMoving == 1 && bMoving == 0) {
                    wa = 0.25;
                    wb = 1.75;
                } else if (aMoving == 0 && bMoving == 1) {
                    wa = 1.75;
                    wb = 0.25;
                }
                nudge(a, -nx * push * wa, -ny * push * wa);
                nudge(b, nx * push * wb, ny * push * wb);
            }
        }
    }

    void nudge(ScEntity e, double dx, double dy) {
        double nx = e.x + dx;
        double ny = e.y + dy;
        if (blockedTile((int) Math.floor(nx / 32), (int) Math.floor(ny / 32)) == 0) {
            e.x = nx;
            e.y = ny;
        }
    }

    // ------------------------------------------------------------------ determinism check

    long checksum() {
        long hsh = 1469598103934665603L;
        int i = 0;
        while (i < ents.size()) {
            ScEntity e = ents.get(i);
            i = i + 1;
            if (e.alive == 0) {
                continue;
            }
            hsh = (hsh ^ e.id) * 1099511628211L;
            hsh = (hsh ^ e.type) * 1099511628211L;
            hsh = (hsh ^ (long) (e.x * 16)) * 1099511628211L;
            hsh = (hsh ^ (long) (e.y * 16)) * 1099511628211L;
            hsh = (hsh ^ e.hp) * 1099511628211L;
            hsh = (hsh ^ e.order) * 1099511628211L;
            hsh = (hsh ^ e.amount) * 1099511628211L;
        }
        int p = 0;
        while (p < playerCount) {
            hsh = (hsh ^ players[p].minerals) * 1099511628211L;
            hsh = (hsh ^ players[p].gas) * 1099511628211L;
            hsh = (hsh ^ players[p].supplyUsed) * 1099511628211L;
            p = p + 1;
        }
        return hsh;
    }
}
