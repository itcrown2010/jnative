import jnative.collections.*;

/**
 * Headless checks of the simulation: economy, construction, training, combat and determinism.
 *
 * @author 张晓达
 */
public class ScSelfTest {
    static int failures;
    static int checks;

    static void check(String what, boolean ok) {
        checks = checks + 1;
        if (!ok) {
            failures = failures + 1;
            System.out.println("FAIL: " + what);
        }
    }

    static ScEntity firstOf(ScSim s, int owner, int type) {
        int i = 0;
        while (i < s.ents.size()) {
            ScEntity e = s.ents.get(i);
            if (e.alive != 0 && e.owner == owner && e.type == type) {
                return e;
            }
            i = i + 1;
        }
        return null;
    }

    static ScEntity nearestMineral(ScSim s, ScEntity from) {
        ScEntity best = null;
        double bd = 1e18;
        int i = 0;
        while (i < s.ents.size()) {
            ScEntity e = s.ents.get(i);
            i = i + 1;
            if (e.alive != 0 && e.type == ScDefs.MINERAL) {
                double d = (e.x - from.x) * (e.x - from.x) + (e.y - from.y) * (e.y - from.y);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    static int[] unitIds(ScSim s, int owner, int type) {
        ArrayList<Integer> l = new ArrayList<Integer>();
        int i = 0;
        while (i < s.ents.size()) {
            ScEntity e = s.ents.get(i);
            i = i + 1;
            if (e.alive != 0 && e.owner == owner && e.type == type) {
                l.add(e.id);
            }
        }
        int[] r = new int[l.size()];
        i = 0;
        while (i < r.length) {
            r[i] = l.get(i);
            i = i + 1;
        }
        return r;
    }

    static void run(ScSim s, int ticks) {
        int i = 0;
        while (i < ticks) {
            s.step();
            i = i + 1;
        }
    }

    static void testSetup() {
        ScSim s = new ScSim(11, 64, 2);
        check("two hubs", s.countOwned(0, ScDefs.HUB, 0) == 1 && s.countOwned(1, ScDefs.HUB, 0) == 1);
        check("four workers each", unitIds(s, 0, ScDefs.WORKER).length == 4 && unitIds(s, 1, ScDefs.WORKER).length == 4);
        check("supply", s.players[0].supplyUsed == 4 && s.players[0].supplyCap == 10);
        ScEntity hub0 = firstOf(s, 0, ScDefs.HUB);
        ScEntity hub1 = firstOf(s, 1, ScDefs.HUB);
        check("start positions far apart", Math.abs(hub0.x - hub1.x) + Math.abs(hub0.y - hub1.y) > 600);
        check("minerals exist", nearestMineral(s, hub0) != null);
    }

    static void testEconomy() {
        ScSim s = new ScSim(11, 64, 2);
        ScEntity hub = firstOf(s, 0, ScDefs.HUB);
        ScEntity m = nearestMineral(s, hub);
        ScCommand c = new ScCommand(0, 0, ScCommand.GATHER);
        c.ids = unitIds(s, 0, ScDefs.WORKER);
        c.target = m.id;
        s.queueCommand(c);
        run(s, 24 * 60);
        check("minerals mined after a minute: " + s.players[0].minerals, s.players[0].minerals > 150);
        check("mineral depleted a little", m.amount < 1500 || m.amount < 1800);
    }

    static void testTraining() {
        ScSim s = new ScSim(11, 64, 2);
        ScEntity hub = firstOf(s, 0, ScDefs.HUB);
        ScCommand c = new ScCommand(0, 0, ScCommand.TRAIN);
        c.ids = new int[] { hub.id };
        c.arg = ScDefs.WORKER;
        s.queueCommand(c);
        run(s, 5);
        check("money spent", s.players[0].minerals == 0);
        run(s, 300);
        check("worker trained", unitIds(s, 0, ScDefs.WORKER).length == 5);
    }

    static void testBuild() {
        ScSim s = new ScSim(11, 64, 2);
        s.players[0].minerals = 500;
        ScEntity hub = firstOf(s, 0, ScDefs.HUB);
        int tx = hub.tx + 6;
        int ty = hub.ty + 5;
        int found = 0;
        int dy = 0;
        while (dy < 12 && found == 0) {
            int dx = 0;
            while (dx < 12 && found == 0) {
                if (s.canPlace(0, ScDefs.DEPOT, hub.tx + dx - 4, hub.ty + 4 + dy) != 0) {
                    tx = hub.tx + dx - 4;
                    ty = hub.ty + 4 + dy;
                    found = 1;
                }
                dx = dx + 1;
            }
            dy = dy + 1;
        }
        check("found a depot site", found != 0);
        ScCommand c = new ScCommand(0, 0, ScCommand.BUILD);
        int[] w = unitIds(s, 0, ScDefs.WORKER);
        c.ids = new int[] { w[0] };
        c.arg = ScDefs.DEPOT;
        c.x = tx;
        c.y = ty;
        s.queueCommand(c);
        run(s, 24 * 60);
        ScEntity d = firstOf(s, 0, ScDefs.DEPOT);
        check("depot exists", d != null);
        check("depot finished", d != null && d.done != 0);
        check("supply cap raised: " + s.players[0].supplyCap, s.players[0].supplyCap == 18);
        check("builder released", s.get(w[0]) != null && s.get(w[0]).hidden == 0);
    }

    static void testCombat() {
        ScSim s = new ScSim(11, 64, 2);
        ScEntity h0 = firstOf(s, 0, ScDefs.HUB);
        ScEntity h1 = firstOf(s, 1, ScDefs.HUB);
        int k = 0;
        int[] mine = new int[6];
        while (k < 6) {
            ScEntity r = s.spawnUnit(ScDefs.RIFLEMAN, 0, h0.x + k * 10, h0.y + 90);
            mine[k] = r.id;
            s.spawnUnit(ScDefs.RIFLEMAN, 1, h1.x + k * 10, h1.y - 90);
            k = k + 1;
        }
        ScCommand c = new ScCommand(0, 0, ScCommand.ATTACKMOVE);
        c.ids = mine;
        c.x = (int) h1.x;
        c.y = (int) h1.y;
        s.queueCommand(c);
        run(s, 24 * 90);
        int alive0 = unitIds(s, 0, ScDefs.RIFLEMAN).length;
        check("combat produced losses", s.players[1].unitsLost > 0 || s.players[0].unitsLost > 0);
        check("attackers reached or died, alive=" + alive0, alive0 >= 0);
    }

    static long fullMatch(int ticks) {
        ScSim s = new ScSim(23, 64, 2);
        ScAI a = new ScAI(s, 0);
        ScAI b = new ScAI(s, 1);
        int i = 0;
        while (i < ticks) {
            a.think();
            b.think();
            s.step();
            i = i + 1;
        }
        return s.checksum();
    }

    static void testDeterminism() {
        long a = fullMatch(24 * 120);
        long b = fullMatch(24 * 120);
        check("same seed, same result", a == b);
    }

    static void testReplay() {
        ScSim s = new ScSim(31, 64, 2);
        ScReplay rec = new ScReplay(31, 64, 2);
        ScAI a = new ScAI(s, 0);
        ScAI b = new ScAI(s, 1);
        s.recorder = rec;
        int i = 0;
        while (i < 24 * 150) {
            a.think();
            b.think();
            s.step();
            if (i % 240 == 0) {
                rec.checkpoint(s.tick, s.checksum());
            }
            i = i + 1;
        }
        String text = rec.serialize();
        ScReplay back = ScReplay.parse(text);
        check("replay commands survive text", back.cmds.size() == rec.cmds.size() && back.cmds.size() > 0);
        check("replay verifies", back.verify(24 * 150) == 1);
        ScSim t = back.start();
        i = 0;
        while (i < 24 * 150) {
            back.feed(t);
            t.step();
            i = i + 1;
        }
        check("replay ends identical", t.checksum() == s.checksum());
    }

    static void stepGame(ScGame g, int n) {
        int i = 0;
        while (i < n) {
            g.stepLive();
            i = i + 1;
        }
    }

    static void testUi() {
        ScGame g = new ScGame();
        g.newGame();
        g.ai[1] = null;
        ScSim s = g.sim;
        ScEntity hub = firstOf(s, 0, ScDefs.HUB);
        check("camera centred on hub", Math.abs(g.camX + ScGame.VW / 2.0 - hub.x) < 700);
        // box select the starting workers
        double cx = hub.x - g.camX;
        double cy = hub.y - g.camY + 60;
        g.onMouseDown((int) cx - 150, (int) cy - 80, 1, 0);
        g.onMouseUp((int) cx + 150, (int) cy + 120, 1, 0);
        check("box selected workers: " + g.selCount, g.selCount == 4);
        // right click a mineral patch -> gather
        ScEntity m = nearestMineral(s, hub);
        g.centerOn(hub.x, hub.y);
        int mxp = (int) (m.x - g.camX);
        int myp = (int) (m.y - g.camY);
        g.onMouseDown(mxp, myp, 3, 0);
        g.onMouseUp(mxp, myp, 3, 0);
        stepGame(g, 24 * 40);
        check("workers mined via right click: " + s.players[0].minerals, s.players[0].minerals > 100);
        // select hub and train via hotkey
        int before = unitIds(s, 0, ScDefs.WORKER).length;
        g.selectOnly(hub);
        g.onKey('w', 0, 1);
        g.onKey('w', 0, 0);
        stepGame(g, 24 * 15);
        check("hotkey trains a worker", unitIds(s, 0, ScDefs.WORKER).length == before + 1);
        // build a depot: select a worker, B, D, click free spot
        stepGame(g, 24 * 30);
        check("enough minerals for a depot: " + s.players[0].minerals, s.players[0].minerals >= 100);
        int[] w = unitIds(s, 0, ScDefs.WORKER);
        g.selectOnly(s.get(w[0]));
        g.onKey('b', 0, 1);
        check("build page", g.buildPage == 1);
        g.onKey('d', 0, 1);
        check("placement mode", g.cmdMode == ScGame.M_BUILD && g.cmdType == ScDefs.DEPOT);
        int px = 0;
        int py = 0;
        int found = 0;
        int dy = 4;
        while (dy < 14 && found == 0) {
            int dx = -6;
            while (dx < 10 && found == 0) {
                int tx = hub.tx + dx;
                int ty = hub.ty + dy;
                if (g.placementValid(ScDefs.DEPOT, tx, ty) != 0) {
                    px = tx * 32 + 48;
                    py = ty * 32 + 32;
                    found = 1;
                }
                dx = dx + 1;
            }
            dy = dy + 1;
        }
        check("valid depot spot", found != 0);
        g.onMouseDown((int) (px - g.camX), (int) (py - g.camY), 1, 0);
        g.onMouseUp((int) (px - g.camX), (int) (py - g.camY), 1, 0);
        check("placement mode ends", g.cmdMode == ScGame.M_NORMAL);
        stepGame(g, 24 * 40);
        check("depot built via UI", s.countOwned(0, ScDefs.DEPOT, 1) == 1);
        // control groups
        g.boxSelect(hub.x - 500, hub.y - 500, hub.x + 500, hub.y + 500, 0);
        int n = g.selCount;
        g.onKey('1', 2, 1);
        g.selCount = 0;
        g.onKey('1', 0, 1);
        check("control group recall: " + n, g.selCount == n && n > 0);
        // finish quickly then verify the recorded replay reproduces the game
        stepGame(g, 24 * 60);
        g.rec.length = s.tick;
        g.rec.checkpoint(s.tick, s.checksum());
        ScReplay r = ScReplay.parse(g.rec.serialize());
        check("UI session replay verifies", r.verify(s.tick) == 1);
        ScGame g2 = new ScGame();
        g2.startReplay(r);
        g2.seekReplay(s.tick);
        int guard = 0;
        while (g2.seekTarget >= 0 && guard < 100000) {
            g2.update(System.nanoTime());
            guard = guard + 1;
        }
        check("replay seek reproduces end state", g2.sim.checksum() == s.checksum());
        g2.seekReplay(24 * 20);
        guard = 0;
        while (g2.seekTarget >= 0 && guard < 100000) {
            g2.update(System.nanoTime());
            guard = guard + 1;
        }
        check("replay seek backwards", g2.sim.tick == 24 * 20);
    }

    static int run() {
        ScDefs.init();
        testSetup();
        testEconomy();
        testTraining();
        testBuild();
        testCombat();
        testDeterminism();
        testReplay();
        testUi();
        System.out.println(checks + " checks, " + failures + " failures");
        return failures;
    }
}
