import jnative.collections.*;

/**
 * Computer opponent. It plays through the same command interface as a human, using only what the
 * simulation exposes (it does not read the other player's orders), so its games replay perfectly.
 *
 * @author 张晓达
 */
public class ScAI {
    ScSim sim;
    int me;
    int level = 1;
    long nextThink;
    int attackWave;
    long lastAttack;

    ScAI(ScSim s, int player) {
        sim = s;
        me = player;
        s.players[player].isAI = 1;
    }

    void issue(ScCommand c) {
        sim.submit(c);
    }

    void think() {
        if (sim.tick < nextThink || sim.players[me].alive == 0) {
            return;
        }
        nextThink = sim.tick + 10;
        ScPlayer p = sim.players[me];
        ScEntity hub = sim.nearestOwned(me, ScDefs.HUB, 0, 0, 1);
        if (hub == null) {
            return;
        }
        int workers = 0;
        int idleWorkers = 0;
        int builders = 0;
        ArrayList<ScEntity> army = new ArrayList<ScEntity>();
        ArrayList<ScEntity> idleList = new ArrayList<ScEntity>();
        int i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.owner != me || e.isUnit() == 0) {
                continue;
            }
            if (ScDefs.worker[e.type] != 0) {
                workers = workers + 1;
                if (e.order == ScEntity.O_IDLE && e.hidden == 0) {
                    idleWorkers = idleWorkers + 1;
                    idleList.add(e);
                }
                if (e.order == ScEntity.O_BUILD || e.order == ScEntity.O_CONSTRUCT) {
                    builders = builders + 1;
                }
            } else {
                army.add(e);
            }
        }
        // idle workers back to the mineral line
        if (idleList.size() > 0) {
            ScEntity m = sim.nearestResource(ScDefs.MINERAL, hub.x, hub.y, 480, 1, -1);
            if (m == null) {
                m = sim.nearestResource(ScDefs.MINERAL, hub.x, hub.y, 480, 0, -1);
            }
            if (m != null) {
                ScCommand c = new ScCommand(0, me, ScCommand.GATHER);
                c.target = m.id;
                c.ids = new int[idleList.size()];
                int k = 0;
                while (k < idleList.size()) {
                    c.ids[k] = idleList.get(k).id;
                    k = k + 1;
                }
                issue(c);
            }
        }
        int depots = sim.countOwned(me, ScDefs.DEPOT, 0);
        int depotsDone = sim.countOwned(me, ScDefs.DEPOT, 1);
        int barracks = sim.countOwned(me, ScDefs.BARRACKS, 0);
        int barracksDone = sim.countOwned(me, ScDefs.BARRACKS, 1);
        int wantWorkers = 14;
        // worker production
        if (workers < wantWorkers && hub.qLen < 2 && p.minerals >= 50) {
            ScCommand c = new ScCommand(0, me, ScCommand.TRAIN);
            c.ids = new int[] { hub.id };
            c.arg = ScDefs.WORKER;
            issue(c);
            p.minerals = p.minerals;
        }
        int pendingDepots = depots - depotsDone;
        int margin = p.supplyCap - p.supplyUsed;
        int reserve = 0;
        if (margin <= 3 + barracksDone * 2 && pendingDepots == 0 && p.minerals >= 100 && builders == 0 && p.supplyCap < 100) {
            build(ScDefs.DEPOT, hub);
            reserve = 1;
        } else if (depotsDone > 0 && barracks < 1 + level + (sim.tick > 24 * 150 ? 1 : 0) && builders == 0 && p.minerals >= 150) {
            build(ScDefs.BARRACKS, hub);
        }
        // army production
        int b = 0;
        while (b < sim.ents.size()) {
            ScEntity e = sim.ents.get(b);
            b = b + 1;
            if (e.alive == 0 || e.owner != me || e.type != ScDefs.BARRACKS || e.done == 0) {
                continue;
            }
            if (e.rallyX < 0) {
                ScCommand rc = new ScCommand(0, me, ScCommand.RALLY);
                rc.ids = new int[] { e.id };
                rc.x = (int) hub.x + 90;
                rc.y = (int) hub.y + 140;
                issue(rc);
            }
            if (e.qLen < 2 && p.minerals >= 50 + reserve * 100 && p.supplyUsed < p.supplyCap) {
                ScCommand c = new ScCommand(0, me, ScCommand.TRAIN);
                c.ids = new int[] { e.id };
                c.arg = ScDefs.RIFLEMAN;
                issue(c);
            }
        }
        // attack waves
        int need = 8 + attackWave * 4;
        if (army.size() >= need && sim.tick - lastAttack > 24 * 20) {
            int enemy = -1;
            int q = 0;
            while (q < sim.playerCount) {
                if (q != me && sim.players[q].alive != 0) {
                    enemy = q;
                }
                q = q + 1;
            }
            if (enemy >= 0) {
                ScCommand c = new ScCommand(0, me, ScCommand.ATTACKMOVE);
                c.x = sim.map.startX[enemy];
                c.y = sim.map.startY[enemy];
                c.ids = new int[army.size()];
                int k = 0;
                while (k < army.size()) {
                    c.ids[k] = army.get(k).id;
                    k = k + 1;
                }
                issue(c);
                attackWave = attackWave + 1;
                lastAttack = sim.tick;
            }
        }
    }

    /** Pick a worker and a site next to the base but away from the mineral line. */
    void build(int type, ScEntity hub) {
        ScEntity m = sim.nearestResource(ScDefs.MINERAL, hub.x, hub.y, 480, 0, -1);
        double mx = m != null ? m.x : hub.x;
        double my = m != null ? m.y : hub.y;
        int bestX = -1;
        int bestY = -1;
        double bestScore = -1e18;
        int tw = ScDefs.tw[type];
        int th = ScDefs.th[type];
        int dy = -12;
        while (dy <= 14) {
            int dx = -12;
            while (dx <= 14) {
                int tx = hub.tx + dx;
                int ty = hub.ty + dy;
                dx = dx + 1;
                if (sim.canPlace(me, type, tx, ty) == 0) {
                    continue;
                }
                if (clearAround(tx, ty, tw, th) == 0) {
                    continue;
                }
                double cx = (tx + tw / 2.0) * 32;
                double cy = (ty + th / 2.0) * 32;
                double dMine = Math.sqrt((cx - mx) * (cx - mx) + (cy - my) * (cy - my));
                double dHub = Math.sqrt((cx - hub.x) * (cx - hub.x) + (cy - hub.y) * (cy - hub.y));
                if (dHub < 150) {
                    continue;
                }
                double score = dMine * 0.6 - dHub;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = tx;
                    bestY = ty;
                }
            }
            dy = dy + 1;
        }
        if (bestX < 0) {
            return;
        }
        ScEntity w = pickWorker(hub);
        if (w == null) {
            return;
        }
        ScCommand c = new ScCommand(0, me, ScCommand.BUILD);
        c.ids = new int[] { w.id };
        c.arg = type;
        c.x = bestX;
        c.y = bestY;
        issue(c);
    }

    int clearAround(int tx, int ty, int tw, int th) {
        int y = ty - 1;
        while (y <= ty + th) {
            int x = tx - 1;
            while (x <= tx + tw) {
                if (sim.blockedTile(x, y) != 0) {
                    return 0;
                }
                x = x + 1;
            }
            y = y + 1;
        }
        return 1;
    }

    ScEntity pickWorker(ScEntity hub) {
        ScEntity best = null;
        double bd = 1e18;
        int i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.owner != me || ScDefs.worker[e.type] == 0 || e.hidden != 0) {
                continue;
            }
            if (e.carryAmt > 0 || e.order == ScEntity.O_BUILD || e.order == ScEntity.O_CONSTRUCT) {
                continue;
            }
            double d = Math.abs(e.x - hub.x) + Math.abs(e.y - hub.y);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
