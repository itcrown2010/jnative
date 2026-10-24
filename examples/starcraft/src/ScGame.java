import jnative.collections.*;

/**
 * Game session: menu, live match, replay playback, selection, orders and the command card. All
 * orders go through ScSim.submit so every live match is recorded.
 *
 * @author 张晓达
 */
public class ScGame {
    static int W = 1120;
    static int H = 800;
    static int HUD_Y = 624;
    static int VW = 1120;
    static int VH = 624;
    static int MM_X = 14;
    static int MM_Y = 638;
    static int MM_S = 148;
    static int CARD_X = 926;
    static int CARD_Y = 634;
    static int CARD_W = 56;
    static int CARD_H = 50;
    static int CARD_GAP = 6;

    static int MENU = 0;
    static int PLAY = 1;
    static int REPLAY = 2;

    static int M_NORMAL = 0;
    static int M_ATTACK = 1;
    static int M_MOVE = 2;
    static int M_BUILD = 3;
    static int M_RALLY = 4;

    // card actions
    static int A_NONE = 0;
    static int A_MOVE = 1;
    static int A_STOP = 2;
    static int A_HOLD = 3;
    static int A_ATTACK = 4;
    static int A_GATHER = 5;
    static int A_BUILDMENU = 6;
    static int A_BUILD = 7;
    static int A_TRAIN = 8;
    static int A_RALLY = 9;
    static int A_CANCEL = 10;
    static int A_BACK = 11;
    static int A_RETURN = 12;

    static double[] SPEEDS = new double[] { 0.5, 1, 2, 3, 4, 8, 16 };

    int mode;
    ScSim sim;
    ScAI[] ai = new ScAI[8];
    int me;
    ScReplay rec;
    ScReplay rep;
    int viewPlayer;
    long frame;
    long lastNs;
    double tickAcc;
    int paused;
    int speedIdx = 1;
    long seekTarget = -1;
    int replayDone;

    double camX;
    double camY;
    int[] sel = new int[12];
    int selCount;
    int[][] grp = new int[10][12];
    int[] grpCount = new int[10];
    long grpTapFrame = -100;
    int grpTapIdx = -1;
    int cmdMode;
    int cmdType;
    int buildPage;
    int mx;
    int my;
    int mouseIn;
    int boxing;
    int bx0;
    int by0;
    int draggingMap;
    long lastClickFrame = -100;
    int lastClickX;
    int lastClickY;
    int lastClickId = -1;
    int[] keys = new int[512];

    String notice = "";
    long noticeFrame = -1000;
    long alertTick = -10000;
    double alertX;
    double alertY;
    long lastAlertSeen = -1;
    int lastShots;
    int lastBooms;
    int lastBuilt;
    int lastUnits;
    int overSounded;
    ArrayList<double[]> markers = new ArrayList<double[]>();

    // card layout
    int[] cardAct = new int[9];
    int[] cardArg = new int[9];
    int[] cardOn = new int[9];
    String[] cardLabel = new String[9];
    char[] cardKey = new char[9];

    // menu
    int menuPage;
    int difficulty = 1;
    int mapSize = 64;
    ArrayList<String> replayFiles = new ArrayList<String>();
    int replaySel;
    String lastSaved = "";
    int saved;

    // view helpers
    ScView view;

    ScGame() {
        ScDefs.init();
        ScArt.init();
        view = new ScView(this);
    }

    // ------------------------------------------------------------------ session control

    void newGame() {
        long seed = (System.nanoTime() / 1000) % 900000 + 100;
        int sz = mapSize;
        sim = new ScSim(seed, sz, 2);
        ai = new ScAI[8];
        ai[1] = new ScAI(sim, 1);
        ai[1].level = difficulty;
        rec = new ScReplay(seed, sz, 2);
        rec.level = difficulty;
        rec.stamp = System.currentTimeMillis();
        sim.recorder = rec;
        rep = null;
        me = 0;
        viewPlayer = 0;
        mode = PLAY;
        beginSession();
    }

    void startReplay(ScReplay r) {
        rep = r;
        rec = null;
        sim = r.start();
        ai = new ScAI[8];
        mode = REPLAY;
        viewPlayer = 0;
        me = 0;
        beginSession();
        replayDone = 0;
    }

    void beginSession() {
        paused = 0;
        speedIdx = 1;
        seekTarget = -1;
        selCount = 0;
        cmdMode = M_NORMAL;
        buildPage = 0;
        saved = 0;
        lastSaved = "";
        replayDone = 0;
        tickAcc = 0;
        lastNs = 0;
        lastShots = sim.shotCount;
        lastBooms = sim.boomCount;
        lastBuilt = 0;
        lastUnits = 0;
        overSounded = 0;
        notice = "";
        markers.clear();
        int g = 0;
        while (g < 10) {
            grpCount[g] = 0;
            g = g + 1;
        }
        view.reset();
        int p = viewPlayer < 0 ? 0 : viewPlayer;
        ScEntity hub = sim.nearestOwned(p, ScDefs.HUB, 0, 0, 0);
        if (hub != null) {
            centerOn(hub.x, hub.y);
        }
    }

    void toMenu() {
        mode = MENU;
        menuPage = 0;
        sim = null;
    }

    void scanReplays() {
        replayFiles.clear();
        FsPath dir = FsPath.of("replays");
        if (!Files.isDirectory(dir)) {
            return;
        }
        ArrayList<String> all = Files.list(dir);
        int i = 0;
        while (i < all.size()) {
            String n = all.get(i);
            i = i + 1;
            int slash = n.lastIndexOf("/");
            if (slash >= 0) {
                n = n.substring(slash + 1);
            }
            if (n.endsWith(".jnrep")) {
                replayFiles.add(n);
            }
        }
        // newest first (names start with a sortable stamp)
        int a = 0;
        while (a < replayFiles.size()) {
            int b = a + 1;
            while (b < replayFiles.size()) {
                if (replayFiles.get(b).compareTo(replayFiles.get(a)) > 0) {
                    String t = replayFiles.get(a);
                    replayFiles.set(a, replayFiles.get(b));
                    replayFiles.set(b, t);
                }
                b = b + 1;
            }
            a = a + 1;
        }
        replaySel = 0;
    }

    ScReplay loadReplay(String name) {
        String text = Files.readString(FsPath.of("replays/" + name));
        return ScReplay.parse(text);
    }

    void saveReplay() {
        if (rec == null || saved != 0) {
            return;
        }
        saved = 1;
        rec.winner = sim.winner;
        rec.length = sim.tick;
        rec.checkpoint(sim.tick, sim.checksum());
        FsPath dir = FsPath.of("replays");
        Files.createDirectories(dir);
        String name = "g" + pad(rec.stamp / 1000) + "-" + rec.seed + ".jnrep";
        Files.writeString(FsPath.of("replays/" + name), rec.serialize());
        lastSaved = name;
    }

    static char lower(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        return c;
    }

    static String pad(long v) {
        return "" + v;
    }

    // ------------------------------------------------------------------ main loop

    void update(long nowNs) {
        frame = frame + 1;
        if (mode == MENU || sim == null) {
            return;
        }
        long dtNs = lastNs == 0 ? 16000000 : nowNs - lastNs;
        lastNs = nowNs;
        if (dtNs > 100000000) {
            dtNs = 100000000;
        }
        scrollCamera();
        double speed = SPEEDS[speedIdx];
        if (mode == PLAY) {
            if (sim.gameOver != 0) {
                saveReplay();
            } else if (paused == 0) {
                tickAcc = tickAcc + dtNs / 1000000000.0 * 24.0 * speed;
                int n = 0;
                while (tickAcc >= 1 && n < 40) {
                    stepLive();
                    tickAcc = tickAcc - 1;
                    n = n + 1;
                }
                if (tickAcc > 4) {
                    tickAcc = 4;
                }
            }
        } else {
            if (seekTarget >= 0) {
                long t0 = System.nanoTime();
                while (sim.tick < seekTarget && sim.gameOver == 0 && System.nanoTime() - t0 < 12000000) {
                    stepReplay();
                }
                if (sim.tick >= seekTarget || sim.gameOver != 0) {
                    seekTarget = -1;
                }
            } else if (paused == 0 && replayDone == 0) {
                tickAcc = tickAcc + dtNs / 1000000000.0 * 24.0 * speed;
                int n = 0;
                while (tickAcc >= 1 && n < 80) {
                    stepReplay();
                    tickAcc = tickAcc - 1;
                    n = n + 1;
                }
                if (tickAcc > 6) {
                    tickAcc = 6;
                }
            }
        }
        view.tickEffects();
        audioEvents();
        checkAlerts();
        purgeSelection();
    }

    int onScreen(double wx, double wy) {
        double sx = wx - camX;
        double sy = wy - camY;
        return (sx > -40 && sy > -40 && sx < VW + 40 && sy < VH + 40) ? 1 : 0;
    }

    void audioEvents() {
        if (sim.shotCount != lastShots) {
            lastShots = sim.shotCount;
            if (onScreen(sim.shotX, sim.shotY) != 0) {
                ScAudio.play(ScAudio.SHOT, 110);
            }
        }
        if (sim.boomCount != lastBooms) {
            lastBooms = sim.boomCount;
            if (onScreen(sim.boomX, sim.boomY) != 0) {
                ScAudio.play(ScAudio.BOOM, 180);
            }
        }
        if (mode == PLAY) {
            ScPlayer p = sim.players[me];
            if (p.buildingsBuilt > lastBuilt) {
                ScAudio.play(ScAudio.BUILT, 300);
            }
            lastBuilt = p.buildingsBuilt;
            if (p.unitsBuilt > lastUnits) {
                ScAudio.play(ScAudio.READY, 200);
            }
            lastUnits = p.unitsBuilt;
            if (sim.gameOver != 0 && overSounded == 0) {
                overSounded = 1;
                ScAudio.play(sim.winner == me ? ScAudio.WIN : ScAudio.LOSE, 0);
            }
        }
    }

    void stepLive() {
        int i = 0;
        while (i < ai.length) {
            if (ai[i] != null) {
                ai[i].think();
            }
            i = i + 1;
        }
        sim.step();
        if (sim.tick % 240 == 0) {
            rec.checkpoint(sim.tick, sim.checksum());
        }
    }

    void stepReplay() {
        rep.feed(sim);
        sim.step();
        if (sim.tick >= rep.length || sim.gameOver != 0) {
            if (sim.tick >= rep.length) {
                replayDone = 1;
            }
        }
    }

    void restartReplay() {
        ScReplay r = rep;
        int vp = viewPlayer;
        int sp = speedIdx;
        startReplay(r);
        viewPlayer = vp;
        speedIdx = sp;
    }

    void seekReplay(long target) {
        if (target < 0) {
            target = 0;
        }
        if (target > rep.length) {
            target = rep.length;
        }
        if (target < sim.tick) {
            restartReplay();
        }
        replayDone = 0;
        seekTarget = target;
    }

    void checkAlerts() {
        if (mode != PLAY) {
            return;
        }
        if (sim.lastAttackOwner == me && sim.lastAttackTick != lastAlertSeen) {
            lastAlertSeen = sim.lastAttackTick;
            if (sim.tick - alertTick > 24 * 8) {
                alertTick = sim.tick;
                alertX = sim.lastAttackX;
                alertY = sim.lastAttackY;
                note("Your forces are under attack!");
                ScAudio.play(ScAudio.ALERT, 2000);
            }
        }
    }

    void note(String s) {
        notice = s;
        noticeFrame = frame;
        if (s.startsWith("Cannot") || s.startsWith("Not enough") || s.startsWith("Additional") || s.startsWith("Requires")) {
            ScAudio.play(ScAudio.ERROR, 400);
        }
    }

    // ------------------------------------------------------------------ camera

    void centerOn(double wx, double wy) {
        camX = wx - VW / 2.0;
        camY = wy - VH / 2.0;
        clampCamera();
    }

    void clampCamera() {
        double maxX = sim.w * 32 - VW;
        double maxY = sim.h * 32 - VH;
        if (camX > maxX) {
            camX = maxX;
        }
        if (camY > maxY) {
            camY = maxY;
        }
        if (camX < 0) {
            camX = 0;
        }
        if (camY < 0) {
            camY = 0;
        }
    }

    void scrollCamera() {
        double v = 18;
        double dx = 0;
        double dy = 0;
        if (keys[37] != 0) {
            dx = -v;
        }
        if (keys[39] != 0) {
            dx = v;
        }
        if (keys[38] != 0) {
            dy = -v;
        }
        if (keys[40] != 0) {
            dy = v;
        }
        if (mouseIn != 0 && draggingMap == 0 && boxing == 0) {
            if (mx <= 2) {
                dx = -v;
            } else if (mx >= W - 3) {
                dx = v;
            }
            if (my <= 2) {
                dy = -v;
            }
            if (my >= VH - 3 && my < VH + 2 && mx > 190 && mx < CARD_X - 10) {
                dy = v;
            }
        }
        if (dx != 0 || dy != 0) {
            camX = camX + dx;
            camY = camY + dy;
            clampCamera();
        }
    }

    // ------------------------------------------------------------------ selection

    void purgeSelection() {
        int k = 0;
        int i = 0;
        while (i < selCount) {
            ScEntity e = sim.get(sel[i]);
            if (e != null && (e.hidden == 0 || e.isUnit() == 0)) {
                sel[k] = sel[i];
                k = k + 1;
            }
            i = i + 1;
        }
        if (k != selCount) {
            selCount = k;
            buildPage = 0;
        }
    }

    int canSee(ScEntity e) {
        if (viewPlayer < 0) {
            return 1;
        }
        if (e.owner == viewPlayer) {
            return 1;
        }
        if (e.isResource() != 0) {
            return sim.seen[viewPlayer][clampTile(e.ty) * sim.w + clampTile(e.tx)] != 0 ? 1 : 0;
        }
        int tx = (int) Math.floor(e.x / 32);
        int ty = (int) Math.floor(e.y / 32);
        return sim.visibleTo(viewPlayer, tx, ty);
    }

    int clampTile(int v) {
        return Math.max(0, Math.min(sim.w - 1, v));
    }

    ScEntity entityAt(double wx, double wy) {
        ScEntity best = null;
        double bd = 1e18;
        int i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.hidden != 0 || canSee(e) == 0) {
                continue;
            }
            if (e.isUnit() != 0) {
                double dx = e.x - wx;
                double dy = e.y - wy;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= ScDefs.radius[e.type] + 6 && d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        if (best != null) {
            return best;
        }
        i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.isUnit() != 0 || canSee(e) == 0) {
                continue;
            }
            if (wx >= e.tx * 32 && wx < (e.tx + e.tw) * 32 && wy >= e.ty * 32 && wy < (e.ty + e.th) * 32) {
                return e;
            }
        }
        return null;
    }

    void selectOnly(ScEntity e) {
        sel[0] = e.id;
        selCount = 1;
        buildPage = 0;
        cmdMode = M_NORMAL;
    }

    int isSelected(int id) {
        int i = 0;
        while (i < selCount) {
            if (sel[i] == id) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    void addToSelection(ScEntity e) {
        if (isSelected(e.id) != 0) {
            int k = 0;
            int i = 0;
            while (i < selCount) {
                if (sel[i] != e.id) {
                    sel[k] = sel[i];
                    k = k + 1;
                }
                i = i + 1;
            }
            selCount = k;
            return;
        }
        if (selCount < 12 && e.isUnit() != 0) {
            // cannot mix buildings and units
            if (selCount > 0) {
                ScEntity f = sim.get(sel[0]);
                if (f == null || f.isUnit() == 0 || f.owner != e.owner) {
                    return;
                }
            }
            sel[selCount] = e.id;
            selCount = selCount + 1;
        }
    }

    void boxSelect(double x0, double y0, double x1, double y1, int add) {
        double lx = Math.min(x0, x1);
        double hx = Math.max(x0, x1);
        double ly = Math.min(y0, y1);
        double hy = Math.max(y0, y1);
        int[] found = new int[12];
        int n = 0;
        int enemy = -1;
        int i = 0;
        while (i < sim.ents.size()) {
            ScEntity e = sim.ents.get(i);
            i = i + 1;
            if (e.alive == 0 || e.hidden != 0 || e.isUnit() == 0 || canSee(e) == 0) {
                continue;
            }
            if (e.x + 6 >= lx && e.x - 6 <= hx && e.y + 6 >= ly && e.y - 6 <= hy) {
                if (viewPlayer >= 0 && e.owner != viewPlayer) {
                    if (enemy < 0) {
                        enemy = e.id;
                    }
                    continue;
                }
                if (n < 12) {
                    found[n] = e.id;
                    n = n + 1;
                }
            }
        }
        if (n == 0 && enemy >= 0 && add == 0) {
            found[0] = enemy;
            n = 1;
        }
        if (n == 0) {
            if (add == 0) {
                selCount = 0;
            }
            return;
        }
        if (add == 0) {
            selCount = 0;
        } else if (selCount > 0) {
            ScEntity f = sim.get(sel[0]);
            if (f == null || f.isUnit() == 0) {
                selCount = 0;
            }
        }
        i = 0;
        while (i < n) {
            if (isSelected(found[i]) == 0 && selCount < 12) {
                sel[selCount] = found[i];
                selCount = selCount + 1;
            }
            i = i + 1;
        }
        buildPage = 0;
    }

    void selectSameType(ScEntity e) {
        selCount = 0;
        int i = 0;
        while (i < sim.ents.size() && selCount < 12) {
            ScEntity o = sim.ents.get(i);
            i = i + 1;
            if (o.alive == 0 || o.hidden != 0 || o.type != e.type || o.owner != e.owner || canSee(o) == 0) {
                continue;
            }
            double sx = o.x - camX;
            double sy = o.y - camY;
            if (sx >= 0 && sx < VW && sy >= 0 && sy < VH) {
                sel[selCount] = o.id;
                selCount = selCount + 1;
            }
        }
        buildPage = 0;
    }

    ScEntity firstSel() {
        if (selCount == 0) {
            return null;
        }
        return sim.get(sel[0]);
    }

    int ownSelection() {
        ScEntity e = firstSel();
        if (e == null || mode != PLAY) {
            return 0;
        }
        return e.owner == me ? 1 : 0;
    }

    // ------------------------------------------------------------------ orders

    ScCommand newCmd(int type) {
        return new ScCommand(sim.tick, me, type);
    }

    int[] selectedUnitIds() {
        int n = 0;
        int i = 0;
        while (i < selCount) {
            ScEntity e = sim.get(sel[i]);
            if (e != null && e.isUnit() != 0 && e.owner == me) {
                n = n + 1;
            }
            i = i + 1;
        }
        int[] r = new int[n];
        n = 0;
        i = 0;
        while (i < selCount) {
            ScEntity e = sim.get(sel[i]);
            if (e != null && e.isUnit() != 0 && e.owner == me) {
                r[n] = e.id;
                n = n + 1;
            }
            i = i + 1;
        }
        return r;
    }

    void issue(ScCommand c) {
        if (mode == PLAY) {
            sim.submit(c);
        }
    }

    void orderMove(double wx, double wy, int attackMove) {
        int[] ids = selectedUnitIds();
        if (ids.length == 0) {
            return;
        }
        ScCommand c = newCmd(attackMove != 0 ? ScCommand.ATTACKMOVE : ScCommand.MOVE);
        c.ids = ids;
        c.x = (int) wx;
        c.y = (int) wy;
        issue(c);
        addMarker(wx, wy, attackMove != 0 ? 1 : 0);
    }

    void orderAttack(ScEntity t) {
        int[] ids = selectedUnitIds();
        if (ids.length == 0) {
            return;
        }
        ScCommand c = newCmd(ScCommand.ATTACK);
        c.ids = ids;
        c.target = t.id;
        issue(c);
        addMarker(t.x, t.y, 2);
    }

    void orderSimple(int type) {
        int[] ids = selectedUnitIds();
        if (ids.length == 0) {
            return;
        }
        ScCommand c = newCmd(type);
        c.ids = ids;
        issue(c);
    }

    void orderGather(ScEntity r) {
        int[] ids = selectedUnitIds();
        if (ids.length == 0) {
            return;
        }
        ScCommand c = newCmd(ScCommand.GATHER);
        c.ids = ids;
        c.target = r.id;
        issue(c);
        addMarker(r.x, r.y, 3);
    }

    void addMarker(double x, double y, int kind) {
        ScAudio.play(ScAudio.ACK, 60);
        markers.add(new double[] { x, y, kind, frame });
    }

    void rightClickWorld(double wx, double wy) {
        if (mode != PLAY) {
            return;
        }
        if (ownSelection() == 0) {
            return;
        }
        ScEntity first = firstSel();
        ScEntity t = entityAt(wx, wy);
        if (first.isBuilding() != 0) {
            if (ScDefs.producer[0] == first.type || ScDefs.producer[1] == first.type) {
                ScCommand c = newCmd(ScCommand.RALLY);
                c.ids = new int[] { first.id };
                c.x = (int) wx;
                c.y = (int) wy;
                c.target = (t != null && t.isResource() != 0) ? t.id : -1;
                issue(c);
                addMarker(wx, wy, 4);
            }
            return;
        }
        if (t != null && t.owner != me && t.owner >= 0) {
            orderAttack(t);
        } else if (t != null && t.isResource() != 0 && hasWorkerSelected() != 0 && (t.type == ScDefs.MINERAL || (t.type == ScDefs.REFINERY && t.owner == me))) {
            orderGather(t);
        } else if (t != null && t.type == ScDefs.REFINERY && t.owner == me && t.done != 0 && hasWorkerSelected() != 0) {
            orderGather(t);
        } else {
            orderMove(wx, wy, 0);
        }
    }

    int hasWorkerSelected() {
        int i = 0;
        while (i < selCount) {
            ScEntity e = sim.get(sel[i]);
            if (e != null && ScDefs.worker[e.type] != 0) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    int placeTileX(double wx, int type) {
        return (int) Math.floor(wx / 32.0 - ScDefs.tw[type] / 2.0 + 0.5);
    }

    int placeTileY(double wy, int type) {
        return (int) Math.floor(wy / 32.0 - ScDefs.th[type] / 2.0 + 0.5);
    }

    /** Snap the placement of a refinery to the geyser under the cursor; returns encoded tile or -1. */
    int placementValid(int type, int tx, int ty) {
        if (sim.canPlace(me, type, tx, ty) == 0) {
            return 0;
        }
        int cx = tx + ScDefs.tw[type] / 2;
        int cy = ty + ScDefs.th[type] / 2;
        if (sim.seen[me][clampTile(cy) * sim.w + clampTile(cx)] == 0) {
            return 0;
        }
        return 1;
    }

    void tryBuildAt(double wx, double wy) {
        int type = cmdType;
        int tx = placeTileX(wx, type);
        int ty = placeTileY(wy, type);
        if (type == ScDefs.REFINERY) {
            ScEntity g = entityAt(wx, wy);
            if (g != null && g.type == ScDefs.GEYSER) {
                tx = g.tx;
                ty = g.ty;
            }
        }
        if (sim.players[me].minerals < ScDefs.cost[type]) {
            note("Not enough minerals");
            return;
        }
        if (placementValid(type, tx, ty) == 0) {
            note("Cannot build there");
            return;
        }
        // nearest selected worker builds
        ScEntity best = null;
        double bd = 1e18;
        int i = 0;
        while (i < selCount) {
            ScEntity e = sim.get(sel[i]);
            i = i + 1;
            if (e == null || ScDefs.worker[e.type] == 0 || e.owner != me) {
                continue;
            }
            double d = Math.abs(e.x - wx) + Math.abs(e.y - wy);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            return;
        }
        ScCommand c = newCmd(ScCommand.BUILD);
        c.ids = new int[] { best.id };
        c.arg = type;
        c.x = tx;
        c.y = ty;
        issue(c);
        cmdMode = M_NORMAL;
        buildPage = 0;
    }

    // ------------------------------------------------------------------ command card

    void clearCard() {
        int i = 0;
        while (i < 9) {
            cardAct[i] = A_NONE;
            cardArg[i] = 0;
            cardOn[i] = 1;
            cardLabel[i] = "";
            cardKey[i] = ' ';
            i = i + 1;
        }
    }

    void setCard(int slot, int act, int arg, String label, char key, int on) {
        cardAct[slot] = act;
        cardArg[slot] = arg;
        cardLabel[slot] = label;
        cardKey[slot] = key;
        cardOn[slot] = on;
    }

    void buildCard() {
        clearCard();
        if (mode != PLAY || sim == null) {
            return;
        }
        ScEntity e = firstSel();
        if (e == null || e.owner != me) {
            return;
        }
        ScPlayer p = sim.players[me];
        if (cmdMode != M_NORMAL) {
            setCard(8, A_BACK, 0, "Cancel", (char) 27, 1);
            return;
        }
        if (e.isUnit() != 0) {
            setCard(0, A_MOVE, 0, "Move", 'm', 1);
            setCard(1, A_STOP, 0, "Stop", 's', 1);
            setCard(2, A_HOLD, 0, "Hold", 'h', 1);
            setCard(3, A_ATTACK, 0, "Attack", 'a', 1);
            if (hasWorkerSelected() != 0) {
                if (buildPage == 1) {
                    clearCard();
                    int t = 0;
                    int slot = 0;
                    while (t < ScDefs.TYPES) {
                        if (ScDefs.building[t] == 1 && slot < 8) {
                            int ok = p.minerals >= ScDefs.cost[t] ? 1 : 0;
                            if (ScDefs.requires[t] >= 0 && sim.hasBuilding(me, ScDefs.requires[t]) == 0) {
                                ok = 0;
                            }
                            setCard(slot, A_BUILD, t, ScDefs.name[t], lower(ScDefs.hotkey[t]), ok);
                            slot = slot + 1;
                        }
                        t = t + 1;
                    }
                    setCard(8, A_BACK, 0, "Back", (char) 27, 1);
                    return;
                }
                setCard(4, A_GATHER, 0, "Gather", 'g', 1);
                setCard(5, A_RETURN, 0, "Return", 'r', 1);
                setCard(6, A_BUILDMENU, 0, "Build", 'b', 1);
            }
            return;
        }
        if (e.isBuilding() != 0 && e.done == 0) {
            setCard(8, A_CANCEL, 1, "Cancel", 'x', 1);
            return;
        }
        if (e.isBuilding() != 0) {
            int slot = 0;
            int t = 0;
            while (t < ScDefs.TYPES) {
                if (ScDefs.building[t] == 0 && ScDefs.producer[t] == e.type && slot < 6) {
                    int ok = p.minerals >= ScDefs.cost[t] ? 1 : 0;
                    if (p.supplyUsed + ScDefs.supply[t] > p.supplyCap || e.qLen >= 5) {
                        ok = 0;
                    }
                    setCard(slot, A_TRAIN, t, ScDefs.name[t], lower(ScDefs.hotkey[t]), ok);
                    slot = slot + 1;
                }
                t = t + 1;
            }
            if (slot > 0) {
                setCard(6, A_RALLY, 0, "Rally", 'y', 1);
            }
            if (e.qLen > 0) {
                setCard(8, A_CANCEL, 0, "Cancel", 'x', 1);
            }
        }
    }

    void doCard(int slot) {
        if (slot < 0 || slot >= 9 || cardAct[slot] == A_NONE) {
            return;
        }
        int act = cardAct[slot];
        if (cardOn[slot] == 0 && act != A_BACK) {
            if (act == A_BUILD) {
                if (sim.players[me].minerals < ScDefs.cost[cardArg[slot]]) {
                    note("Not enough minerals");
                } else {
                    note("Requires " + ScDefs.name[ScDefs.requires[cardArg[slot]]]);
                }
            } else if (act == A_TRAIN) {
                ScPlayer p = sim.players[me];
                if (p.minerals < ScDefs.cost[cardArg[slot]]) {
                    note("Not enough minerals");
                } else if (p.supplyUsed + ScDefs.supply[cardArg[slot]] > p.supplyCap) {
                    note("Additional supply required - build a Supply Depot");
                }
            }
            return;
        }
        ScEntity e = firstSel();
        if (act == A_MOVE) {
            cmdMode = M_MOVE;
        } else if (act == A_ATTACK) {
            cmdMode = M_ATTACK;
        } else if (act == A_STOP) {
            orderSimple(ScCommand.STOP);
        } else if (act == A_HOLD) {
            orderSimple(ScCommand.HOLD);
        } else if (act == A_RETURN) {
            orderSimple(ScCommand.RETURN);
        } else if (act == A_GATHER) {
            ScEntity hub = sim.nearestOwned(me, ScDefs.HUB, e.x, e.y, 0);
            double hx = hub != null ? hub.x : e.x;
            double hy = hub != null ? hub.y : e.y;
            ScEntity m = sim.nearestResource(ScDefs.MINERAL, hx, hy, 600, 1, -1);
            if (m == null) {
                m = sim.nearestResource(ScDefs.MINERAL, hx, hy, 600, 0, -1);
            }
            if (m != null) {
                orderGather(m);
            }
        } else if (act == A_BUILDMENU) {
            buildPage = 1;
        } else if (act == A_BUILD) {
            cmdMode = M_BUILD;
            cmdType = cardArg[slot];
        } else if (act == A_TRAIN) {
            ScCommand c = newCmd(ScCommand.TRAIN);
            c.ids = new int[] { e.id };
            c.arg = cardArg[slot];
            issue(c);
        } else if (act == A_RALLY) {
            cmdMode = M_RALLY;
        } else if (act == A_CANCEL) {
            if (e != null && e.isBuilding() != 0 && e.done == 0) {
                ScCommand c = newCmd(ScCommand.CANCEL);
                c.ids = new int[] { e.id };
                c.arg = -1;
                issue(c);
            } else if (e != null && e.qLen > 0) {
                ScCommand c = newCmd(ScCommand.CANCEL);
                c.ids = new int[] { e.id };
                c.arg = e.qLen - 1;
                issue(c);
            }
        } else if (act == A_BACK) {
            if (cmdMode != M_NORMAL) {
                cmdMode = M_NORMAL;
            } else {
                buildPage = 0;
            }
        }
    }

    int cardAt(int px, int py) {
        int col = (px - CARD_X) / (CARD_W + CARD_GAP);
        int row = (py - CARD_Y) / (CARD_H + CARD_GAP);
        if (px < CARD_X || py < CARD_Y || col > 2 || row > 2) {
            return -1;
        }
        int lx = (px - CARD_X) % (CARD_W + CARD_GAP);
        int ly = (py - CARD_Y) % (CARD_H + CARD_GAP);
        if (lx >= CARD_W || ly >= CARD_H) {
            return -1;
        }
        return row * 3 + col;
    }

    // ------------------------------------------------------------------ input events

    void onKey(int code, int mods, int down) {
        if (code >= 0 && code < 512) {
            keys[code] = down;
        }
        if (down == 0) {
            return;
        }
        if (mode == MENU) {
            menuKey(code);
            return;
        }
        if (sim == null) {
            return;
        }
        int shift = mods % 2;
        int ctrl = (mods / 2) % 2 + (mods / 8) % 2;
        if (code == '=' || code == '+') {
            if (speedIdx < (mode == REPLAY ? SPEEDS.length - 1 : 4)) {
                speedIdx = speedIdx + 1;
            }
            return;
        }
        if (code == '-') {
            if (speedIdx > 0) {
                speedIdx = speedIdx - 1;
            }
            return;
        }
        if (mode == REPLAY) {
            replayKey(code);
            return;
        }
        if (sim.gameOver != 0) {
            if (code == 13) {
                toMenu();
            } else if (code == 'w' && rec != null) {
                ScReplay r = ScReplay.parse(rec.serialize());
                startReplay(r);
            }
            return;
        }
        if (code == 27) {
            if (cmdMode != M_NORMAL) {
                cmdMode = M_NORMAL;
            } else if (buildPage != 0) {
                buildPage = 0;
            } else if (selCount > 0) {
                ScEntity e = firstSel();
                if (e != null && e.isBuilding() != 0 && e.owner == me && e.qLen > 0) {
                    doCardKey('x');
                }
            }
            return;
        }
        if (code == 'p') {
            paused = 1 - paused;
            return;
        }
        if (code == 32) {
            if (sim.tick - alertTick < 24 * 60) {
                centerOn(alertX, alertY);
            } else {
                ScEntity hub = sim.nearestOwned(me, ScDefs.HUB, camX + VW / 2.0, camY + VH / 2.0, 1);
                if (hub != null) {
                    centerOn(hub.x, hub.y);
                }
            }
            return;
        }
        if (code >= '0' && code <= '9') {
            int g = code - '0';
            if (ctrl != 0) {
                purgeSelection();
                grpCount[g] = selCount;
                int i = 0;
                while (i < selCount) {
                    grp[g][i] = sel[i];
                    i = i + 1;
                }
                note("Group " + g + " assigned");
            } else if (grpCount[g] > 0) {
                selCount = 0;
                int i = 0;
                while (i < grpCount[g]) {
                    if (sim.get(grp[g][i]) != null) {
                        sel[selCount] = grp[g][i];
                        selCount = selCount + 1;
                    }
                    i = i + 1;
                }
                if (frame - grpTapFrame < 24 && grpTapIdx == g && selCount > 0) {
                    ScEntity f = sim.get(sel[0]);
                    if (f != null) {
                        centerOn(f.x, f.y);
                    }
                }
                grpTapFrame = frame;
                grpTapIdx = g;
                buildPage = 0;
                cmdMode = M_NORMAL;
            }
            return;
        }
        if (code >= 'a' && code <= 'z') {
            doCardKey((char) code);
        }
    }

    void replayKey(int code) {
        if (code == 27) {
            toMenu();
        } else if (code == 32) {
            paused = 1 - paused;
        } else if (code == '1') {
            viewPlayer = 0;
        } else if (code == '2') {
            viewPlayer = 1;
        } else if (code == '3' || code == 'v') {
            viewPlayer = -1;
        } else if (code == 'r') {
            restartReplay();
        } else if (code == ']') {
            seekReplay(sim.tick + 24 * 10);
        } else if (code == '[') {
            seekReplay(sim.tick - 24 * 10);
        }
    }

    void doCardKey(char key) {
        buildCard();
        int i = 0;
        while (i < 9) {
            if (cardAct[i] != A_NONE && cardKey[i] == key) {
                doCard(i);
                return;
            }
            i = i + 1;
        }
    }

    void menuKey(int code) {
        if (menuPage == 0) {
            if (code == 'n' || code == 13) {
                newGame();
            } else if (code == 'd') {
                difficulty = (difficulty + 1) % 3;
            } else if (code == 'm') {
                mapSize = mapSize == 64 ? 96 : (mapSize == 96 ? 128 : 64);
            } else if (code == 'r') {
                scanReplays();
                menuPage = 1;
            } else if (code == 'q' || code == 27) {
                Application.quit();
            }
        } else {
            if (code == 27) {
                menuPage = 0;
            } else if (code == 38 && replaySel > 0) {
                replaySel = replaySel - 1;
            } else if (code == 40 && replaySel < replayFiles.size() - 1) {
                replaySel = replaySel + 1;
            } else if (code == 13 && replayFiles.size() > 0) {
                ScReplay r = loadReplay(replayFiles.get(replaySel));
                startReplay(r);
            } else if (code == 8 || code == 46) {
                if (replayFiles.size() > 0) {
                    Files.delete(FsPath.of("replays/" + replayFiles.get(replaySel)));
                    scanReplays();
                }
            }
        }
    }

    void onMouseMove(int x, int y, int button) {
        mx = x;
        my = y;
        mouseIn = 1;
        if (mode == MENU || sim == null) {
            return;
        }
        if (button == 1) {
            if (draggingMap != 0) {
                minimapTo(x, y);
            }
        }
    }

    void minimapTo(int x, int y) {
        double fx = (x - MM_X) / (double) MM_S;
        double fy = (y - MM_Y) / (double) MM_S;
        fx = Math.max(0, Math.min(1, fx));
        fy = Math.max(0, Math.min(1, fy));
        centerOn(fx * sim.w * 32, fy * sim.h * 32);
    }

    int inMinimap(int x, int y) {
        return (x >= MM_X && y >= MM_Y && x < MM_X + MM_S && y < MM_Y + MM_S) ? 1 : 0;
    }

    void onMouseDown(int x, int y, int button, int mods) {
        mx = x;
        my = y;
        if (mode == MENU || sim == null) {
            return;
        }
        int shift = mods % 2;
        if (mode == REPLAY) {
            if (button == 1) {
                if (inMinimap(x, y) != 0) {
                    draggingMap = 1;
                    minimapTo(x, y);
                    return;
                }
                if (view.inProgressBar(x, y) != 0) {
                    seekReplay((long) (view.progressFrac(x) * rep.length));
                    return;
                }
                if (y < VH) {
                    bx0 = x;
                    by0 = y;
                    boxing = 1;
                }
            }
            return;
        }
        if (sim.gameOver != 0) {
            return;
        }
        if (button == 3) {
            if (cmdMode != M_NORMAL) {
                cmdMode = M_NORMAL;
                return;
            }
            if (inMinimap(x, y) != 0) {
                double fx = (x - MM_X) / (double) MM_S * sim.w * 32;
                double fy = (y - MM_Y) / (double) MM_S * sim.h * 32;
                if (ownSelection() != 0) {
                    ScEntity f = firstSel();
                    if (f.isUnit() != 0) {
                        orderMove(fx, fy, 0);
                    }
                }
                return;
            }
            if (y < VH) {
                rightClickWorld(x + camX, y + camY);
            }
            return;
        }
        if (button != 1) {
            return;
        }
        if (y >= VH) {
            if (inMinimap(x, y) != 0) {
                if (cmdMode == M_ATTACK || cmdMode == M_MOVE) {
                    double fx = (x - MM_X) / (double) MM_S * sim.w * 32;
                    double fy = (y - MM_Y) / (double) MM_S * sim.h * 32;
                    orderMove(fx, fy, cmdMode == M_ATTACK ? 1 : 0);
                    cmdMode = M_NORMAL;
                    return;
                }
                draggingMap = 1;
                minimapTo(x, y);
                return;
            }
            buildCard();
            int slot = cardAt(x, y);
            if (slot >= 0) {
                doCard(slot);
                return;
            }
            // click on a unit icon in the info panel
            int idx = view.selIconAt(x, y);
            if (idx >= 0 && idx < selCount) {
                ScEntity e = sim.get(sel[idx]);
                if (e != null) {
                    if (shift != 0) {
                        addToSelection(e);
                    } else {
                        selectOnly(e);
                    }
                }
            }
            return;
        }
        double wx = x + camX;
        double wy = y + camY;
        if (cmdMode == M_ATTACK) {
            ScEntity t = entityAt(wx, wy);
            if (t != null && t.owner != me && t.owner >= 0) {
                orderAttack(t);
            } else {
                orderMove(wx, wy, 1);
            }
            cmdMode = M_NORMAL;
            return;
        }
        if (cmdMode == M_MOVE) {
            orderMove(wx, wy, 0);
            cmdMode = M_NORMAL;
            return;
        }
        if (cmdMode == M_BUILD) {
            tryBuildAt(wx, wy);
            return;
        }
        if (cmdMode == M_RALLY) {
            ScEntity f = firstSel();
            if (f != null) {
                ScCommand c = newCmd(ScCommand.RALLY);
                c.ids = new int[] { f.id };
                c.x = (int) wx;
                c.y = (int) wy;
                ScEntity t = entityAt(wx, wy);
                c.target = (t != null && t.isResource() != 0) ? t.id : -1;
                issue(c);
                addMarker(wx, wy, 4);
            }
            cmdMode = M_NORMAL;
            return;
        }
        bx0 = x;
        by0 = y;
        boxing = 1;
    }

    void onMouseUp(int x, int y, int button, int mods) {
        mx = x;
        my = y;
        if (mode == MENU || sim == null) {
            return;
        }
        if (button != 1) {
            return;
        }
        if (draggingMap != 0) {
            draggingMap = 0;
            return;
        }
        if (boxing == 0) {
            return;
        }
        boxing = 0;
        int shift = mods % 2;
        int dx = Math.abs(x - bx0);
        int dy = Math.abs(y - by0);
        if (dx < 5 && dy < 5) {
            ScEntity e = entityAt(x + camX, y + camY);
            boolean dbl = frame - lastClickFrame < 22 && Math.abs(x - lastClickX) < 6 && Math.abs(y - lastClickY) < 6;
            lastClickFrame = frame;
            lastClickX = x;
            lastClickY = y;
            if (e == null) {
                if (shift == 0) {
                    selCount = 0;
                }
                return;
            }
            if (dbl && e.isUnit() != 0 && e.id == lastClickId) {
                selectSameType(e);
                return;
            }
            lastClickId = e.id;
            if (shift != 0 && e.isUnit() != 0) {
                addToSelection(e);
            } else {
                selectOnly(e);
            }
            return;
        }
        boxSelect(bx0 + camX, by0 + camY, x + camX, y + camY, shift);
    }

    void onWheel(int delta) {
        // wheel scrolls the map vertically
        if (sim == null) {
            return;
        }
        camY = camY - delta * 24;
        clampCamera();
    }
}
