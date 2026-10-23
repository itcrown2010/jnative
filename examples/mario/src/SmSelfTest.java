/** Deterministic gameplay checks run with {@code --selftest}; prints PASS/FAIL per rule. */
public class SmSelfTest {
    static int failures = 0;
    static int passes = 0;

    static void check(String name, int ok, String detail) {
        if (ok != 0) {
            passes = passes + 1;
            System.out.println("PASS " + name);
        } else {
            failures = failures + 1;
            System.out.println("FAIL " + name + " : " + detail);
        }
    }

    static SmGame fresh() {
        SmGame g = new SmGame();
        g.newGame();
        // clear the enemies so each scenario is isolated
        g.enemies.clear();
        g.introLen = 0;
        g.state = SmGame.PLAYING;
        return g;
    }

    static void hold(SmGame g, int code, int down) {
        g.setKey(code, down);
    }

    static void steps(SmGame g, int n) {
        int i = 0;
        while (i < n) {
            g.update();
            i = i + 1;
        }
    }

    static void place(SmGame g, double tx, double ty) {
        g.player.x = tx * 16 + 2;
        g.player.y = ty * 16;
        g.player.vx = 0;
        g.player.vy = 0;
        g.camX = (int) (tx * 16 - 112);
        if (g.camX < 0) {
            g.camX = 0;
        }
    }

    /** Peak height (px above the start) of a jump held for holdFrames, with a run-up speed. */
    static double jumpPeak(double runSpeedKey, int holdFrames, int running) {
        SmGame g = fresh();
        place(g, 3, 12);
        steps(g, 5);
        double y0 = g.player.y;
        if (running != 0) {
            g.player.vx = 2.5625;
            hold(g, 39, 1);
            hold(g, 88, 1);
        }
        y0 = g.player.y;
        double minY = y0;
        hold(g, 90, 1);
        int f = 0;
        while (f < 120) {
            if (f >= holdFrames) {
                hold(g, 90, 0);
            }
            g.update();
            if (g.player.y < minY) {
                minY = g.player.y;
            }
            f = f + 1;
        }
        return y0 - minY;
    }

    public static void run() {
        SmSprites.init();
        double stand = jumpPeak(0, 100, 0);
        check("standing jump reaches ~4 tiles", stand > 58 && stand < 72 ? 1 : 0, "peak=" + stand);
        double runJ = jumpPeak(0, 100, 1);
        check("running jump is higher than standing", runJ > stand + 8 ? 1 : 0, "peak=" + runJ);
        double shortJ = jumpPeak(0, 6, 0);
        check("tapping jump gives a short hop", shortJ < stand - 20 ? 1 : 0, "peak=" + shortJ);

        // walking speed caps
        SmGame g = fresh();
        place(g, 3, 12);
        hold(g, 39, 1);
        steps(g, 55);
        check("walk speed caps at 1.5625", Math.abs(g.player.vx - 1.5625) < 0.001 ? 1 : 0, "vx=" + g.player.vx);
        hold(g, 88, 1);
        steps(g, 40);
        check("run speed caps at 2.5625", Math.abs(g.player.vx - 2.5625) < 0.001 ? 1 : 0, "vx=" + g.player.vx);
        hold(g, 39, 0);
        hold(g, 88, 0);
        hold(g, 37, 1);
        steps(g, 8);
        check("pressing back while running skids", g.player.skidding != 0 ? 1 : 0, "skid=" + g.player.skidding);
        hold(g, 37, 0);

        // ? block gives a coin once
        g = fresh();
        place(g, 16, 12);
        g.player.y = 10 * 16 + 1;
        g.player.vy = -3;
        steps(g, 6);
        check("question block turns into a used block", g.level.get(16, 9) == 'u' ? 1 : 0, "tile=" + g.level.get(16, 9));
        check("block coin adds a coin and score", g.coins == 1 && g.score == 200 ? 1 : 0, "coins=" + g.coins + " score=" + g.score);

        // ? block with mushroom, then grow
        g = fresh();
        place(g, 21, 12);
        g.player.y = 10 * 16 + 1;
        g.player.vy = -3;
        steps(g, 6);
        check("power block spawns a mushroom", g.items.size() >= 1 && g.items.get(0).kind == 0 ? 1 : 0, "items=" + g.items.size());
        steps(g, 40);
        place(g, 21, 8);
        g.player.y = 8 * 16 - 16;
        // walk into the mushroom wherever it is
        int k = 0;
        while (k < g.items.size()) {
            SmItem it = g.items.get(k);
            if (it.kind == 0) {
                g.player.x = it.x;
                g.player.y = it.y - 2;
            }
            k = k + 1;
        }
        steps(g, 3);
        check("mushroom makes the hero big", g.player.big == 1 ? 1 : 0, "big=" + g.player.big + " items=" + g.items.size());
        steps(g, 60);

        // brick: small bumps, big breaks
        g = fresh();
        place(g, 20, 12);
        g.player.y = 10 * 16 + 1;
        g.player.vy = -3;
        steps(g, 6);
        check("small hero cannot break a brick", g.level.get(20, 9) == 'B' ? 1 : 0, "tile=" + g.level.get(20, 9));
        g = fresh();
        g.player.setBig(1);
        place(g, 20, 11);
        g.player.y = 162;
        g.player.vy = -3;
        steps(g, 8);
        check("big hero breaks a brick", g.level.get(20, 9) == '.' ? 1 : 0, "tile=" + g.level.get(20, 9));

        // stomp a goomba
        g = fresh();
        SmEnemy e = new SmEnemy(0, 10 * 16, 12 * 16);
        e.active = 1;
        g.enemies.add(e);
        place(g, 10, 9);
        g.player.vy = 1;
        steps(g, 20);
        check("stomping a goomba squashes it and scores", e.squash > 0 || e.removed != 0 ? (g.score >= 100 ? 1 : 0) : 0,
                "squash=" + e.squash + " score=" + g.score);

        // koopa -> shell -> kick
        g = fresh();
        SmEnemy kp = new SmEnemy(1, 10 * 16, 12 * 16);
        kp.active = 1;
        g.enemies.add(kp);
        place(g, 10, 9);
        g.player.vy = 1;
        steps(g, 20);
        check("stomping a koopa leaves a shell", kp.kind == 2 ? 1 : 0, "kind=" + kp.kind);
        steps(g, 30);
        g.player.x = kp.x - 14;
        g.player.y = kp.y;
        g.player.vy = 0;
        hold(g, 39, 1);
        steps(g, 6);
        hold(g, 39, 0);
        check("touching a resting shell kicks it", kp.kind == 3 ? 1 : 0, "kind=" + kp.kind);

        // damage: big -> small, small -> death
        g = fresh();
        g.player.setBig(1);
        SmEnemy gb = new SmEnemy(0, 12 * 16, 12 * 16);
        gb.active = 1;
        g.enemies.add(gb);
        place(g, 11, 11);
        g.player.y = 176;
        g.player.x = gb.x - 8;
        steps(g, 4);
        check("enemy hit shrinks a big hero", g.player.big == 0 ? 1 : 0, "big=" + g.player.big);
        steps(g, 200);
        g = fresh();
        SmEnemy gs = new SmEnemy(0, 12 * 16, 12 * 16);
        gs.active = 1;
        g.enemies.add(gs);
        place(g, 12, 12);
        g.player.x = gs.x - 4;
        steps(g, 3);
        check("enemy hit kills a small hero", g.player.state == 3 ? 1 : 0, "state=" + g.player.state);
        steps(g, 260);
        check("death costs a life and restarts the level", g.lives == 2 && g.player.state == 0 ? 1 : 0,
                "lives=" + g.lives + " state=" + g.player.state);

        // pit
        g = fresh();
        place(g, 69, 10);
        steps(g, 160);
        check("falling into a pit kills", g.lives == 2 || g.player.state == 3 ? 1 : 0, "lives=" + g.lives + " state=" + g.player.state);

        // flag pole to course clear
        g = fresh();
        place(g, 201, 7);
        hold(g, 39, 1);
        steps(g, 600);
        check("grabbing the flag clears the course", g.state == SmGame.CLEAR ? 1 : 0, "state=" + g.state + " px=" + g.player.x);
        check("flag awards points", g.score >= 100 ? 1 : 0, "score=" + g.score);

        // fire flower from a power block when big
        g = fresh();
        g.player.setBig(1);
        place(g, 21, 11);
        g.player.y = 162;
        g.player.vy = -3;
        steps(g, 8);
        check("power block gives a flower to a big hero", g.items.size() >= 1 && g.items.get(0).kind == 4 ? 1 : 0, "items=" + g.items.size());
        steps(g, 30);
        int fi = 0;
        while (fi < g.items.size()) {
            SmItem it = g.items.get(fi);
            if (it.kind == 4) {
                g.player.x = it.x;
                g.player.y = it.y - 20;
            }
            fi = fi + 1;
        }
        steps(g, 6);
        check("flower grants fire power", g.player.fire == 1 && g.player.big == 1 ? 1 : 0, "fire=" + g.player.fire);

        // fireballs
        g = fresh();
        g.player.setBig(1);
        g.player.fire = 1;
        place(g, 10, 11);
        SmEnemy fe = new SmEnemy(0, 14 * 16, 12 * 16);
        fe.active = 1;
        g.enemies.add(fe);
        steps(g, 3);
        hold(g, 88, 1);
        steps(g, 1);
        check("fire button throws a fireball", g.fireballs.size() == 1 ? 1 : 0, "fireballs=" + g.fireballs.size());
        steps(g, 40);
        check("fireball kills a goomba", fe.dead != 0 ? 1 : 0, "dead=" + fe.dead);
        hold(g, 88, 0);
        steps(g, 2);
        hold(g, 88, 1);
        steps(g, 1);
        hold(g, 88, 0);
        steps(g, 2);
        hold(g, 88, 1);
        steps(g, 1);
        hold(g, 88, 0);
        steps(g, 2);
        hold(g, 88, 1);
        steps(g, 1);
        check("at most two fireballs at once", g.fireballs.size() <= 2 ? 1 : 0, "fireballs=" + g.fireballs.size());
        hold(g, 88, 0);

        // starman
        g = fresh();
        g.player.setBig(1);
        place(g, 122, 11);
        g.player.y = 98;
        g.player.vy = -3;
        g.level.set(122, 5, 'S');
        steps(g, 10);
        check("star block releases a starman", g.items.size() >= 1 && g.items.get(0).kind == 6 ? 1 : 0, "items=" + g.items.size() + " tile=" + g.level.get(122, 5));
        g = fresh();
        SmItem st = new SmItem(6, 10 * 16, 12 * 16);
        g.items.add(st);
        place(g, 10, 11);
        steps(g, 3);
        check("starman gives temporary invincibility", g.player.star > 500 ? 1 : 0, "star=" + g.player.star);
        SmEnemy se = new SmEnemy(0, 11 * 16, 12 * 16);
        se.active = 1;
        g.enemies.add(se);
        g.player.x = se.x - 4;
        steps(g, 4);
        check("starman kills what it touches", se.dead != 0 && g.player.state == 0 ? 1 : 0, "dead=" + se.dead + " state=" + g.player.state);

        // warp pipe into the bonus room and back out
        g = fresh();
        place(g, 38, 10);
        g.player.x = 38 * 16 + 16 - g.player.w / 2.0;
        g.player.y = 10 * 16 - 16;
        steps(g, 3);
        hold(g, 40, 1);
        steps(g, 2);
        check("down on a pipe starts a warp", g.player.state == 7 ? 1 : 0, "state=" + g.player.state);
        steps(g, 40);
        hold(g, 40, 0);
        check("warp lands in the bonus room", g.player.state == 0 && g.player.x >= 220 * 16 ? 1 : 0, "state=" + g.player.state + " x=" + g.player.x);
        g.player.x = 232 * 16 + 16 - g.player.w / 2.0;
        g.player.y = 11 * 16 - 16;
        steps(g, 3);
        hold(g, 40, 1);
        steps(g, 2);
        hold(g, 40, 0);
        steps(g, 40);
        check("exit pipe returns to the level", g.player.state == 0 && g.player.x < 70 * 16 && g.player.x > 50 * 16 ? 1 : 0, "state=" + g.player.state + " x=" + g.player.x);

        // key bindings: K jumps, J runs
        g = fresh();
        place(g, 3, 12);
        steps(g, 5);
        hold(g, 75, 1);
        steps(g, 3);
        check("K key jumps", g.player.vy < 0 || g.player.onGround == 0 ? 1 : 0, "vy=" + g.player.vy);
        hold(g, 75, 0);
        g = fresh();
        place(g, 3, 12);
        hold(g, 39, 1);
        hold(g, 74, 1);
        steps(g, 90);
        check("J key runs", g.player.vx > 2.0 ? 1 : 0, "vx=" + g.player.vx);
        hold(g, 74, 0);
        hold(g, 39, 0);
        g = fresh();
        place(g, 3, 12);
        hold(g, 39, 1);
        hold(g, 75, 1);
        steps(g, 90);
        check("K key does not run", g.player.vx < 1.7 ? 1 : 0, "vx=" + g.player.vx);
        hold(g, 75, 0);
        hold(g, 39, 0);

        // coyote time and jump buffer
        g = fresh();
        place(g, 3, 8);
        g.player.vy = 0.1;
        g.coyote = 3;
        hold(g, 75, 1);
        steps(g, 1);
        check("coyote time allows a late jump", g.player.vy < 0 ? 1 : 0, "vy=" + g.player.vy);
        hold(g, 75, 0);
        g = fresh();
        place(g, 3, 8);
        g.player.y = 184;
        g.player.vy = 0;
        steps(g, 1);
        hold(g, 75, 1);
        steps(g, 12);
        check("buffered jump fires on landing", g.player.y < 185 ? 1 : 0, "y=" + g.player.y);
        hold(g, 75, 0);

        // level intro card
        g = new SmGame();
        g.newGame();
        check("new game shows the world card first", g.state == SmGame.INTRO ? 1 : 0, "state=" + g.state);
        steps(g, 130);
        check("world card hands over to play", g.state == SmGame.PLAYING ? 1 : 0, "state=" + g.state);

        // damage removes fire and size
        g = fresh();
        g.player.setBig(1);
        g.player.fire = 1;
        g.hurt();
        check("hit removes fire power", g.player.fire == 0 && g.player.big == 0 ? 1 : 0, "fire=" + g.player.fire + " big=" + g.player.big);

        // level 2 and progression
        g = fresh();
        g.levelIndex = 1;
        g.loadLevel();
        check("level 2 is underground", g.level.theme == 1 && g.stageNo == 2 ? 1 : 0, "theme=" + g.level.theme);
        g = fresh();
        g.player.setBig(1);
        g.player.fire = 1;
        place(g, 201, 7);
        hold(g, 39, 1);
        steps(g, 600);
        hold(g, 39, 0);
        steps(g, 400);
        hold(g, 13, 1);
        steps(g, 2);
        hold(g, 13, 0);
        check("clearing 1-1 starts 1-2 with power kept", g.state == SmGame.PLAYING && g.levelIndex == 1 && g.player.fire == 1 ? 1 : 0,
                "state=" + g.state + " level=" + g.levelIndex + " fire=" + g.player.fire);
        g = fresh();
        g.levelIndex = 1;
        g.loadLevel();
        place(g, g.level.flagX - 1, 7);
        hold(g, 39, 1);
        steps(g, 600);
        hold(g, 39, 0);
        steps(g, 400);
        hold(g, 13, 1);
        steps(g, 2);
        hold(g, 13, 0);
        check("clearing the last course shows the win screen", g.state == SmGame.WIN ? 1 : 0, "state=" + g.state);

        // clock
        g = fresh();
        steps(g, 24 * 5);
        check("clock ticks every 24 frames", g.time == 395 ? 1 : 0, "time=" + g.time);

        System.out.println("selftest: " + passes + " passed, " + failures + " failed");
    }
}
